package balticporter.transform

import balticporter.core.{MergeablePolicy, PolicyFinding, PolicyIssue, PolicyReport, PolicySource, SurfacePolicy}
import balticporter.tir.*

/** Retypes a semantically-tagged primitive to an `opaque type` (+companion) everywhere it flows,
  * wrapping construction sites and unwrapping consumption sites (sge/ssg case: GL layer ids,
  * key/button codes). Symbol-identity- and flow-driven, not textual:
  *   1. hints — a small tagged-symbol seed set (`spec.hints`, or an agent-supplied `extraHints`
  *      FQN after a failed compile, closing the loop).
  *   2. detect — [[FlowPropagation]] grows the seeds along pure-move flows (assignment, `val`
  *      init, `return`, argument passing); arithmetic breaks the chain and gets an unwrap instead.
  *   3. retype + coerce — each seed's `info` becomes the opaque type, references retype via the
  *      xref, coercions are inserted at every seed/primitive boundary.
  *
  * Everything a port says is in [[OpaqueSpec]] — CLAUDE.md §1(b) mechanism, §1(c) policy. Two
  * instances in one pipeline compose (union of seeds) or the run fails on overlap
  * ([[refuseOverlap]]) — one symbol cannot be two opaque types. Coercion reads the boundary through
  * the DECLARATION, never a term node's `tpe`, descending carrier expressions (`if`, block tail,
  * `match` arm) so a mixed-branch value is not missed. ENGINE-LIMITS §13 O1
  * A retyped parameter moves its enclosing method's `MethodType` too, by position. ENGINE-LIMITS §13 O2
  *
  * The mint belongs to the ONE module that owns the declarations it was minted for — fenced by
  * [[RunScope.emits]] (CLAUDE.md §1.5, synthesised declarations), read off the HINTS and never the
  * grown seed set, since propagation can reach a dependent's own declarations. A dependent still
  * retypes and coerces everywhere; only the object/type/apply/unwrap symbols stay phantom
  * (`Program.owns` reports them external) and resolve against the owning module's emitted class.
  * ENGINE-LIMITS §13 O5
  */
final class PrimitiveToOpaqueTransform(val spec: OpaqueSpec)
    extends Phase, Rewrite, PolicySource, MergeablePolicy, PolicyBound:
  def name = s"primitive->opaque:${spec.fqn}"

  /** [[OpaqueBoundaryCheck]] counts external callees whose class-file formals this phase cannot
    * read, scope boundaries where opaque meets primitive, and unreachable boxed-primitive slots.
    * Named as a symbol so a renamed lane is a compile error, not a silently unwired claim. */
  def accountedBy: Set[String] = Set(OpaqueBoundaryCheck.Name)

  /** This phase retypes under a [[RuleScope]], so two modules configuring it differently emit
    * signatures that cannot compile together. Everything in the spec is rendered, sorted —
    * `hints` (exact FQNs), the fence, the definition site, the primitive, `extraHints`, and the
    * target FQN when `target = Existing` (empty segment when `target = Mint`, so §1(b)'s
    * fingerprint no-op rule holds).
    */
  def surfaceFingerprint: String =
    val seeds  = if spec.hints.isEmpty then "" else s";hints=${spec.hints.toList.sorted.mkString(",")}"
    val extras = if spec.extraHints.isEmpty then "" else s";extra=${spec.extraHints.toList.sorted.mkString(",")}"
    val fence  = spec.scope.fingerprint
    val tgt = spec.target match
      case OpaqueSpec.Target.Mint => ""
      case OpaqueSpec.Target.Existing(t, w, u) => s";target=$t;wrap=$w;unwrap=$u"
    s"${spec.fqn}:${spec.underlyingFqn}$seeds$extras${if fence.isEmpty then "" else s";$fence"}$tgt"

  /** every shared-surface subject this instance's policy is keyed on — the leading type FQN of
    * each hint and each scope entry, through [[MergeablePolicy.subjectOf]]. Over-approximate:
    * an omitted subject is a hole exactly where the §1.5 screen exists. */
  def subjects: Set[String] =
    (spec.hints ++ spec.extraHints ++ spec.scope.entries).map(MergeablePolicy.subjectOf)

  /** The merge contract for same-FQN opaque specs across a base/dependent chain: `fqn`, `target`,
    * `underlying` must agree (the family's identity) or the merge refuses; `hints`/`extraHints`
    * union; `scope` composes as `NullabilityTransform`'s (`Everywhere`/`Only` union, mixed
    * refuses). `added` is the subject side of what the later instance contributes.
    */
  def mergedWith(later: Phase): Either[String, MergeablePolicy.Merged] = later match
    case o: PrimitiveToOpaqueTransform =>
      // identity: fqn already guaranteed equal by name-matching in the fold; target/underlying must agree too.
      val targetCheck: Option[String] = (spec.target, o.spec.target) match
        case (a, b) if a == b => None
        case (a, b)           => Some(
          s"""both modules state a different `target` for `${spec.fqn}` — the retyping's """ +
            "destination type is one emitted signature per member, so two answers is a choice and " +
            "not a composition")
      val underlyingCheck: Option[String] =
        if spec.underlying == o.spec.underlying then None
        else Some(
          s"""both modules state a different `underlying` for `${spec.fqn}` — """ +
            s""""${spec.underlying}" and "${o.spec.underlying}" — the opaque type wraps one """ +
            "primitive, not two")
      val scopeMerged: Either[String, RuleScope] = (spec.scope, o.spec.scope) match
        case (RuleScope.Everywhere(a), RuleScope.Everywhere(b)) => Right(RuleScope.Everywhere(a ++ b))
        case (RuleScope.Only(a), RuleScope.Only(b))             => Right(RuleScope.Only(a ++ b))
        case (mine, theirs)                                     => Left(
          s"""both modules scope `$name`, one `${mine.productPrefix}` and one """ +
            s"""`${theirs.productPrefix}` — the two point in OPPOSITE directions (an entry EXCLUDES """ +
            "under one and INCLUDES under the other), so no entry set preserves both")
      val refusals = targetCheck.toList ++ underlyingCheck.toList ++ scopeMerged.left.toOption.toList
      refusals match
        case Nil =>
          val mergedScope = scopeMerged.getOrElse(spec.scope) // safe: Nil means Right
          val merged = new PrimitiveToOpaqueTransform(OpaqueSpec(
            fqn        = spec.fqn,
            hints      = spec.hints ++ o.spec.hints,
            underlying = spec.underlying,
            extraHints = spec.extraHints ++ o.spec.extraHints,
            scope      = mergedScope,
            target     = spec.target,
          ))
          Right(MergeablePolicy.Merged(merged, o.subjects -- subjects))
        case whys => Left(whys.mkString("; "))
    case other =>
      Left(s"`${other.name}` is not a `PrimitiveToOpaqueTransform`, so there is no policy to compose")

  /** hints the mechanism cannot reach; see [[reportUnreachable]]. Cleared at the head of every run. */
  private val unreachable = collection.mutable.ListBuffer.empty[PolicyFinding]

  /** boundary findings accumulated during [[run]]; cleared at the head of every run. */
  private val boundaryIssues = collection.mutable.ListBuffer.empty[OpaqueBoundaryCheck.Finding]

  /** every boundary site this phase opened and could not close, restricted to units this run
    * actually emits. ENGINE-LIMITS D2 */
  def boundary(units: List[Tree.ClassDef]): List[OpaqueBoundaryCheck.Finding] =
    val emitted = units.map(_.symbol).toSet
    boundaryIssues.toList.filter(f => emitted(f.unit))
      .sortBy(f => (f.origin.javaPath, f.origin.line, f.subject, f.issue.toString))

  def policyReport: PolicyReport = PolicyReport(unreachable.toList)

  private var objSym, opaqueSym, applySym, unwrapSym, wrapArraySym, unwrapArraySym, primSym, boxedPrimSym, arraySym: SymId = SymId.None
  private var seeds: Set[SymId]   = Set.empty
  /** method fullNames whose base port map upstream descriptor mentions this spec's opaque FQN —
    * a direct read of what the base published, not a re-derivation. CLAUDE.md §4.55 */
  private var baseRetypedMethods: Set[String] = Set.empty
  private var opaqueRef: TypeRepr = TypeRepr.NoType
  private var primRef: TypeRepr   = TypeRepr.NoType
  /** `Array[Opaque.T]` — what an `int[]` seed becomes after retyping. */
  private var opaqueArrayRef: TypeRepr = TypeRepr.NoType
  /** `Array[Int]` — the JVM-level array type the coercion wraps/unwraps. */
  private var primArrayRef: TypeRepr   = TypeRepr.NoType
  private val minted = collection.mutable.ListBuffer[Symbol]()

  /** which top-level units this run emits; not derivable from the `Program` a phase is handed
    * (a dependent's contains its base's units). Defaults to the base-port answer. */
  private var runScope: RunScope = RunScope.whole

  /** nothing to bind — this phase's policy is a predicate and an FQN set. Used only for [[runScope]]. */
  def bindPolicy(binder: PolicyBinder): Unit =
    runScope = binder.run
    // method fullNames whose base port-map upstream descriptor mentions this spec's opaque FQN —
    // such a callee had its parameter retyped by the base, so coerceArgs must not unwrap.
    // the simple-name check is intentional: the port map descriptor uses the simple name.
    val opaqueSimple = spec.target match
      case OpaqueSpec.Target.Existing(fqn, _, _) => fqn.split('.').last
      case OpaqueSpec.Target.Mint                => spec.fqn.split('.').last
    baseRetypedMethods = binder.run.baseMemberUpstream
      .filter(u => u.contains(s"($opaqueSimple") || u.contains(s",$opaqueSimple"))
      .map(u => u.takeWhile(_ != '('))
      .toSet

  /** the detected old→new type mapping: every primitive symbol retyped to the opaque type.
    * Array seeds map to `Array[Opaque.T]` rather than `Opaque.T`. */
  def typeMapping: Map[SymId, TypeRepr] = seeds.iterator.map(id => id -> opaqueRef).toMap

  override def run(program: Program): Program =
    unreachable.clear()
    boundaryIssues.clear()
    // lowest SymId among symbols with this fullName: a retarget may later mint a second symbol
    // with the same fullName, and `find` on that would be non-deterministic — the ORIGINAL id is
    // what existing symbols' `info` still references, so prefer the lowest (the frontend's).
    primSym = program.symbols.all.filter(_.fullName == spec.underlyingFqn).minByOption(_.id.raw).map(_.id).getOrElse(SymId.None)
    if primSym == SymId.None then return program
    primRef = TypeRepr.TypeRef(TypeRepr.NoType, primSym)
    boxedPrimSym = program.symbols.all.filter(_.fullName == spec.underlying.boxedFqn).minByOption(_.id.raw).map(_.id).getOrElse(SymId.None)
    arraySym = program.symbols.all.filter(_.fullName == "scala.Array").minByOption(_.id.raw).map(_.id).getOrElse(SymId.None)

    // the scope fences seeding as well as propagation. RuleScope.Everywhere() (default) fences
    // nothing, so this filter is then the identity.
    def fenced(s: Symbol): Boolean = spec.scope.includes(program, s)
    // a hint may also name something a SIBLING spec already claimed — admitted here on purpose so
    // refuseOverlap can see it, rather than silently finding nothing.
    val named = program.symbols.all.filter(s => spec.hints(s.fullName) || spec.extraHints(s.fullName))
    val hints = named
      .filter(s => fenced(s) && (taggablePrim(s.info) || foreignOpaque(program, s.info).isDefined))
      .map(_.id).toSet
    // the ones this mechanism cannot reach, before any early return — see reportUnreachable.
    reportUnreachable(program, named.filter(fenced))
    if hints.isEmpty then return program
    // spanning-hints/mint-ownership only apply when MINTING; an Existing target has no unit to collide on.
    if spec.isMint then refuseSpanningHints(program, hints)
    seeds = FlowPropagation.grow(program, hints, id => program.symbolOf(id).exists(s =>
      (taggablePrim(s.info) || foreignOpaque(program, s.info).isDefined) && spec.scope.includes(program, s)))
    refuseOverlap(program)
    if seeds.isEmpty then return program

    var next = program.symbols.all.map(_.id.raw).maxOption.getOrElse(-1) + 1
    def mint(name: String, full: String, flags: Flags, owner: SymId = SymId.None, info: TypeRepr = TypeRepr.NoType): SymId =
      val id = SymId(next); next += 1
      minted += Symbol(id, name, full, flags, owner, info)
      id

    // synthesis depends on the target form; both mint the same phantom symbols (objSym, opaqueSym,
    // applySym, unwrapSym) so coercion is one code path. Only Mint creates a ClassDef unit.
    val synthUnit: Option[Tree.ClassDef] = spec.target match
      case OpaqueSpec.Target.Mint =>
        // the object is a top-level unit whose fullName is the spec's FQN; the emitter derives
        // the package clause and file from it.
        objSym    = mint(spec.objectName, spec.fqn, Flags(isModule = true))
        // owner = the object, so the emitter renders references as the path-dependent Name.T
        opaqueSym = mint("T", spec.typeFqn, Flags(isOpaque = true), objSym)
        opaqueRef = TypeRepr.TypeRef(TypeRepr.NoType, opaqueSym)
        val arrayTC = TypeRepr.TypeRef(TypeRepr.NoType, arraySym)
        opaqueArrayRef = if arraySym == SymId.None then TypeRepr.NoType
                         else TypeRepr.AppliedType(arrayTC, List(opaqueRef))
        primArrayRef   = if arraySym == SymId.None then TypeRepr.NoType
                         else TypeRepr.AppliedType(arrayTC, List(primRef))
        val vWrap   = mint("v", "v", Flags(isParam = true), info = primRef)
        val vUnwrap = mint("v", "v", Flags(isParam = true), info = opaqueRef)
        applySym  = mint("apply",  s"${spec.fqn}.apply",  Flags(), objSym, TypeRepr.MethodType(List("v" -> primRef), opaqueRef))
        unwrapSym = mint("unwrap", s"${spec.fqn}.unwrap", Flags(), objSym, TypeRepr.MethodType(List("v" -> opaqueRef), primRef))

        // array wrap/unwrap
        val arrayMembers = if arraySym == SymId.None then Nil else
          val vaw = mint("v", "v", Flags(isParam = true), info = primArrayRef)
          val vau = mint("v", "v", Flags(isParam = true), info = opaqueArrayRef)
          wrapArraySym   = mint("wrapArray",   s"${spec.fqn}.wrapArray",   Flags(), objSym,
            TypeRepr.MethodType(List("v" -> primArrayRef), opaqueArrayRef))
          unwrapArraySym = mint("unwrapArray", s"${spec.fqn}.unwrapArray", Flags(), objSym,
            TypeRepr.MethodType(List("v" -> opaqueArrayRef), primArrayRef))
          List(
            Tree.DefDef(wrapArraySym, List(List(Tree.ValDef(vaw, TypeTree(primArrayRef, Origin.synthetic), None, Origin.synthetic))),
              TypeTree(opaqueArrayRef, Origin.synthetic), Some(Tree.Ident(vaw, primArrayRef, Origin.synthetic)), Origin.synthetic),
            Tree.DefDef(unwrapArraySym, List(List(Tree.ValDef(vau, TypeTree(opaqueArrayRef, Origin.synthetic), None, Origin.synthetic))),
              TypeTree(primArrayRef, Origin.synthetic), Some(Tree.Ident(vau, opaqueArrayRef, Origin.synthetic)), Origin.synthetic),
          )

        val o = Origin.synthetic
        val typeDef  = Tree.TypeDef(opaqueSym, TypeTree(primRef, o), o)
        val applyDef = Tree.DefDef(applySym, List(List(Tree.ValDef(vWrap, TypeTree(primRef, o), None, o))),
          TypeTree(opaqueRef, o), Some(Tree.Ident(vWrap, primRef, o)), o)
        val unwrapDef = Tree.DefDef(unwrapSym, List(List(Tree.ValDef(vUnwrap, TypeTree(opaqueRef, o), None, o))),
          TypeTree(primRef, o), Some(Tree.Ident(vUnwrap, opaqueRef, o)), o)
        Some(Tree.ClassDef(objSym, Nil, None, List(typeDef, applyDef, unwrapDef) ++ arrayMembers, o))

      case OpaqueSpec.Target.Existing(typeFqn, wrapName, unwrapName) =>
        // the opaque type already exists (an injected replacement): mint phantom symbols for its
        // companion, type and coercion methods, the shape NullabilityTransform.Target.Named uses.
        // No ClassDef is synthesised — the definition is supplied by Substitutions (drop+inject).
        val companionFqn = typeFqn // opaque companion shares the type's path
        objSym    = mint(spec.objectName, companionFqn, Flags(isModule = true))
        // for an Existing target the type IS the FQN, not a member T; no owner so the emitter
        // renders the fullName directly instead of owner.name (which would give Align.Align).
        opaqueSym = mint(spec.objectName, typeFqn, Flags(isOpaque = true))
        opaqueRef = TypeRepr.TypeRef(TypeRepr.NoType, opaqueSym)
        val arrayTC = TypeRepr.TypeRef(TypeRepr.NoType, arraySym)
        opaqueArrayRef = if arraySym == SymId.None then TypeRepr.NoType
                         else TypeRepr.AppliedType(arrayTC, List(opaqueRef))
        primArrayRef   = if arraySym == SymId.None then TypeRepr.NoType
                         else TypeRepr.AppliedType(arrayTC, List(primRef))
        applySym  = mint(wrapName,   s"$companionFqn.$wrapName",   Flags(), objSym,
          TypeRepr.MethodType(List("v" -> primRef), opaqueRef))
        unwrapSym = mint(unwrapName, s"$companionFqn.$unwrapName", Flags(), objSym,
          TypeRepr.MethodType(List("v" -> opaqueRef), primRef))

        // array coercions for the Existing form too — the same erasure identity applies.
        if arraySym != SymId.None then
          wrapArraySym   = mint("wrapArray",   s"$companionFqn.wrapArray",   Flags(), objSym,
            TypeRepr.MethodType(List("v" -> primArrayRef), opaqueArrayRef))
          unwrapArraySym = mint("unwrapArray", s"$companionFqn.unwrapArray", Flags(), objSym,
            TypeRepr.MethodType(List("v" -> opaqueArrayRef), primArrayRef))

        None // no unit minted — the definition is the injected file

    // a retyped parameter's own method, by POSITION and never by name: a MethodType's parameter
    // list and its DefDef's are parallel by construction, but names are not (an earlier phase may
    // rewrite a parameter slot without touching the method's info). ENGINE-LIMITS §13 O2
    val seedParamSlots: Map[SymId, Set[Int]] =
      program.symbols.all.iterator.filter(s => seeds(s.id) && s.flags.isParam).toList
        .groupBy(_.owner).flatMap { (owner, ps) =>
          program.definitionOf(owner).collect { case d: Tree.DefDef =>
            val at = d.paramss.flatten.map(_.symbol).zipWithIndex.toMap
            owner -> ps.flatMap(p => at.get(p.id)).toSet
          }
        }.toMap

    /** one method's signature: every seeded parameter slot, plus the result when the method
      * itself is a seed. An array parameter/result is retyped too. */
    def methodType(id: SymId, mt: TypeRepr.MethodType): TypeRepr.MethodType =
      val slots = seedParamSlots.getOrElse(id, Set.empty)
      def retypeSlot(t: TypeRepr): TypeRepr =
        if isPrim(t) then opaqueRef else if isArrayOfPrim(t) then opaqueArrayRef else t
      TypeRepr.MethodType(
        mt.params.zipWithIndex.map((nt, i) => if slots(i) then nt._1 -> retypeSlot(nt._2) else nt),
        if seeds(id) then retypeSlot(mt.result) else mt.result,
        mt.isImplicit)

    // retype seed symbol infos primitive -> opaque (value seeds) / return -> opaque (method
    // seeds) / the parameter slots of any method one of whose parameters is a seed. Array seeds
    // retype Array[Prim] -> Array[Opaque.T].
    val retyped = program.symbols.all.map { s =>
      s.info match
        case r if seeds(s.id) && isPrim(r)                   => s.copy(info = opaqueRef)
        case r if seeds(s.id) && isArrayOfPrim(r)            => s.copy(info = opaqueArrayRef)
        case mt: TypeRepr.MethodType                         =>
          val next = methodType(s.id, mt); if next == mt then s else s.copy(info = next)
        case TypeRepr.PolyType(tps, mt: TypeRepr.MethodType) =>
          val next = methodType(s.id, mt)
          if next == mt then s else s.copy(info = TypeRepr.PolyType(tps, next))
        case _                                               => s
    }
    val symbols = SymbolTable(retyped ++ minted)
    given Program = program.rebuilt(symbols = symbols)

    // one decision row per declaration whose signature became the opaque type. Reason.LibraryRule
    // (§1(c)): which primitives are a domain value is one library's knowledge. Parameters and
    // method-locals are seeds too but deliberately not rows — their method's signature already
    // moved with them.
    program.symbols.all.foreach { s =>
      if seeds(s.id) && Decision.isDeclaration(summon[Program], s) then
        summon[Program].symbolOf(s.id).foreach { now =>
          if now.info != s.info then
            record(Decision(
              kind       = Decision.Kind.RetypedSignature,
              subject    = s.id,
              subjectFqn = s.fullName,
              detail = Map(
                "from" -> TirPrinter.tpe(s.info, TirPrinter.Style.canonical),
                "to"   -> TirPrinter.tpe(now.info, TirPrinter.Style.canonical),
                "key"  -> spec.fqn,
                "why"  -> (s"this `${spec.underlyingFqn}` reaches a tagged seed by a pure-move flow, " +
                  "so it carries the same domain value and gets the same opaque type"),
              ),
              reason = Reason.LibraryRule(name),
              origin = Decision.originOf(program, s.id),
            ))
        }
    }

    // the mint is one module's (ENGINE-LIMITS §13 O5; class doc explains why the test is on the
    // hints, not the grown seed set). Everything above this line runs in every inheriting module;
    // only the object write is fenced. For the Existing form there is no unit to mint at all.
    val walked = program.units.map(u => StandardTraversal.mapClassDef(this, u))
    val units = synthUnit match
      case Some(su) if mintsHere(program, hints) => walked :+ su
      case _ => walked
    program.rebuilt(units, symbols)

  /** Does this module own the declarations the spec named? Read through [[RunScope.emits]]; `true`
    * whenever there is no run scope ([[RunScope.whole]]). `exists` is safe only because
    * [[refuseSpanningHints]] has already run and guaranteed the bound hints are all this module's
    * or none of them are — read on its own, `exists` is the more dangerous quantifier. */
  private def mintsHere(p: Program, hints: Set[SymId]): Boolean =
    hints.exists(id => runScope.emits(unitOf(p, id)))

  /** Fails the run when the spec's bound hints land in more than one module — a spec's `hints`
    * predicate can match a field in both a base and a dependent, making both modules mint the same
    * FQN. `PortRun.claimedSynthetic` cannot be relied on to catch this (it admits by default when
    * no base map is published), so this phase refuses for itself, naming both sides. §1(c): the
    * fix is in the port — `hints` names declarations of ONE module. Throws rather than finding,
    * for [[refuseOverlap]]'s reason: there is no honest program to emit. ENGINE-LIMITS §13 O5
    */
  private def refuseSpanningHints(p: Program, hints: Set[SymId]): Unit =
    def named(id: SymId): String = p.symbolOf(id).map(_.fullName).getOrElse(id.toString)
    val (here, elsewhere) = hints.toList.partition(id => runScope.emits(unitOf(p, id)))
    if here.nonEmpty && elsewhere.nonEmpty then
      def show(ids: List[SymId]) =
        ids.map(id => s"      ${named(id)}   (in ${named(unitOf(p, id))})").sorted.take(10).mkString("\n")
      throw new IllegalStateException(
        s"[balticporter] §1(c) LIBRARY RULE: `${spec.fqn}`'s hints bind declarations in MORE THAN " +
          "ONE module, so no module can be said to own the minted type.\n" +
          s"    this module emits ${here.size} of them:\n${show(here)}\n" +
          s"    and does NOT emit ${elsewhere.size}:\n${show(elsewhere)}\n" +
          s"  The minted `${spec.fqn}` is a TOP-LEVEL unit and belongs to the module that owns the " +
          "declarations it was minted FOR (`ENGINE-LIMITS.md` §13 O5). With hints on both sides of " +
          "that line, every module in the chain mints its own copy of one FQN — and an opaque type " +
          "cannot be duplicated even harmlessly, since opacity is per-DEFINITION.\n" +
          "  Fix in the PORT: narrow `hints`/`extraHints` to declarations of ONE module — an exact " +
          "FQN rather than a name pattern is the reliable form — or fence the spec with a " +
          "`RuleScope`. A dependent that wants a domain type of its own declares its OWN spec, at " +
          "its own FQN.")

  /** the top-level unit a symbol belongs to. Fuel-bounded so a corrupt owner chain cannot hang
    * the phase. */
  private def unitOf(p: Program, id: SymId, fuel: Int = 64): SymId =
    p.symbolOf(id) match
      case Some(s) if s.owner != SymId.None && fuel > 0 => unitOf(p, s.owner, fuel - 1)
      case _                                            => id

  /** A hint the mechanism cannot reach, made loud. [[taggablePrim]] tests a symbol's own info, so
    * a domain value sitting INSIDE a container (`int[] locations`) is invisible to seeding and
    * propagation alike — an array element has no symbol. Reports the one case it can tell apart
    * from a typo: a hint naming a real declaration whose type MENTIONS the primitive somewhere
    * the mechanism cannot seed. `Malformed`, not `NeverMatched` — the key named something real.
    * §1(a) ENGINE: a spec has no vocabulary for "the element of". ENGINE-LIMITS §13 O3
    */
  private def reportUnreachable(program: Program, named: Iterable[Symbol]): Unit =
    given Program = program
    named.foreach { s =>
      val value = valueTypeOf(s.info)
      if !taggablePrim(s.info) && foreignOpaque(program, s.info).isEmpty && mentionsPrim(value) then
        val setting =
          if spec.extraHints(s.fullName) then s"OpaqueSpec(${spec.fqn}).extraHints(${s.fullName})"
          else s"OpaqueSpec(${spec.fqn}).hints(${s.fullName})"
        unreachable += PolicyFinding(name, setting, s.fullName, PolicyIssue.Malformed,
          s"this declaration's value type is `${TirPrinter.tpe(value, TirPrinter.Style.canonical)}`, " +
            s"which MENTIONS `${spec.underlyingFqn}` without BEING it — the domain value sits inside a " +
            "container. This mechanism seeds a symbol whose OWN type is the primitive and grows the " +
            "set along flows between SYMBOLS, and a container's element has no symbol of its own, so " +
            "neither a hint nor a pure-move edge can reach it. The hint is therefore NOT a typo and " +
            "NOT something a respelling fixes. [§1(a) ENGINE, `ENGINE-LIMITS.md` §13 O3: an " +
            "`OpaqueSpec` has no vocabulary for \"the element of\". Until it does, the exits are to " +
            "drop this hint or to widen the mechanism]")
    }

  /** the type a declaration's value has — a method's result, anything else's own info. Matches
    * [[taggablePrim]]'s domain: a hint naming a parameter is a different mistake with a policy
    * exit (name the parameter directly). */
  private def valueTypeOf(info: TypeRepr): TypeRepr = info match
    case TypeRepr.MethodType(_, ret, _)                       => ret
    case TypeRepr.PolyType(_, TypeRepr.MethodType(_, ret, _)) => ret
    case other                                                => other

  /** does this type mention the spec's primitive anywhere? `StandardTraversal.mapType`, not a
    * private recursion, so a new `TypeRepr` shape is reached without enumerating it. CLAUDE.md §3 */
  private def mentionsPrim(t: TypeRepr)(using Program): Boolean =
    var found = false
    val scan = new Phase:
      def name = "primitive->opaque/mentions"
      override def transformType(x: TypeRepr)(using Program): TypeRepr =
        if isPrim(x) then found = true
        x
    StandardTraversal.mapType(scan, t)
    found

  /** the other opaque object a symbol's declared type belongs to, if any — read from the
    * `isOpaque` flag. `None` for this phase's own (nothing is minted yet when this runs). */
  private def foreignOpaque(p: Program, info: TypeRepr): Option[String] =
    val head = info match
      case TypeRepr.MethodType(_, ret, _) => headSym(ret)
      case other                          => headSym(other)
    head.filter(_ != opaqueSym).flatMap(p.symbolOf).filter(_.flags.isOpaque)
      .flatMap(t => p.symbolOf(t.owner).map(_.fullName).orElse(Some(t.fullName)))

  /** Fails the run when this spec's seeds overlap another `PrimitiveToOpaqueTransform`'s. Without
    * this, whichever instance runs second finds those symbols already retyped, declines them
    * silently, and emits a port with half a domain type missing — no compile error, no count
    * moved. Propagation is allowed to walk into a sibling's opaque type so the overlap is visible
    * here, naming the symbol and both specs. Throws rather than a finding: there is no honest
    * program to emit. CLAUDE.md §3
    */
  private def refuseOverlap(p: Program): Unit =
    val clashes = seeds.toList.flatMap { id =>
      p.symbolOf(id).flatMap(s => foreignOpaque(p, s.info).map(other => (s.fullName, other)))
    }.sorted
    if clashes.nonEmpty then
      val lines = clashes.map((sym, other) => s"  $sym is already `$other`, and `${spec.fqn}` claims it too")
      throw new IllegalStateException(
        s"[balticporter] §1(c) LIBRARY RULE: two opaque-type specs claim the same declaration(s). " +
          s"One symbol cannot be two opaque types, and the instance that ran second would otherwise " +
          s"decline them in silence.\n${lines.mkString("\n")}\n" +
          s"  Fix in the PORT: narrow one spec's `hints`/`extraHints`, or fence it with a " +
          s"`RuleScope` so its propagation cannot reach the other's declarations.")

  // -------------------------------------------------------------------------
  // Retype seed positions in the tree + insert coercions.
  // -------------------------------------------------------------------------
  // a declaration is the boundary on both sides of the if: a seed declaration wraps whatever
  // arrives, and a declaration that kept the primitive unwraps whatever seed arrives. ENGINE-LIMITS §13 O1
  override def transformValDef(v: Tree.ValDef)(using Program): Tree.ValDef =
    if seeds(v.symbol) then
      val ref = seedTypeRef(v.tpt.tpe)
      v.copy(tpt = TypeTree(ref, v.origin), rhs = v.rhs.map(e => wrapFor(e, v.tpt.tpe)))
    else v.copy(rhs = v.rhs.map(unwrapIfOpaque))

  override def transformDefDef(d: Tree.DefDef)(using Program): Tree.DefDef =
    if seeds(d.symbol) then
      val ref = seedTypeRef(d.returnTpt.tpe)
      d.copy(returnTpt = TypeTree(ref, d.origin), rhs = d.rhs.map(wrapReturns))
    else d.copy(rhs = d.rhs.map(unwrapReturns))

  // retype seed REFERENCES so boundary detection reads a consistent `tpe` (the populator left
  // a seed reference's node `tpe` as `Int`; only the declaration was retyped above).
  override def transformIdent(t: Tree.Ident)(using Program): Term =
    if seeds(t.sym) then t.copy(tpe = seedTypeRef(t.tpe)) else t
  override def transformSelect(t: Tree.Select)(using Program): Term =
    if seeds(t.sym) then t.copy(tpe = seedTypeRef(t.tpe)) else t

  override def transformApply(t0: Tree.Apply)(using Program): Term =
    val t = if isSeedMethod(t0.method) then t0.copy(tpe = seedMethodRetType(t0.method)) else t0
    t.fun match
      // operator operands consumed as Int (`layer + 1`, `layer < other`) → unwrap the seed sides.
      case Tree.Select(recv, m, st, so) if summon[Program].symbolOf(m).exists(_.fullName.startsWith("scala.<op>#")) =>
        Tree.Apply(Tree.Select(unwrapIfOpaque(recv), m, st, so), t.args.map(unwrapIfOpaque), t.method, t.tpe, t.origin)
      case _ =>
        // a plain-Int callee parameter fed a seed value → unwrap; a seed parameter fed a plain
        // Int → wrap. Read the callee's (retyped) param symbols from its definition.
        coerceArgs(t)

  override def transformTerm(t: Term)(using Program): Term = t match
    // The DECLARATION on the left decides, not the node type on the right: `layer = c ? 0 : x` has
    // an `Assign` whose rhs node is still `Int` and whose value is half a seed's.
    case a: Tree.Assign =>
      if carriesOpaque(a.lhs) then a.copy(rhs = wrapFor(a.rhs, lhsDeclType(a.lhs)))
      else a.copy(rhs = unwrapIfOpaque(a.rhs))
    case x: Tree.ArrayAccess => x.copy(index = unwrapIfOpaque(x.index))
    case other => other

  private def isSeedMethod(m: SymId)(using p: Program): Boolean =
    seeds(m) && p.symbolOf(m).exists(_.info.isInstanceOf[TypeRepr.MethodType])

  private def coerceArgs(t: Tree.Apply)(using p: Program): Term =
    p.definitionOf(t.method) match
      case Some(d: Tree.DefDef) =>
        val params = d.paramss.flatten
        if params.length != t.args.length then t
        else
          // a formal that stays java is read literally (CLAUDE.md §1(b)). Propagation may grow the
          // seed set into a base declaration this run does not emit: if the base's port map says
          // it retyped that method (baseRetypedMethods), wrap the argument (direct read of the
          // base's published answer, §4.55); otherwise unwrap to the primitive.
          val calleeEmitted = runScope.emits(unitOf(p, t.method))
          val calleeFqn     = p.symbolOf(t.method).map(_.fullName).getOrElse("")
          val calleeOwner   = calleeFqn.indexOf('#') match { case -1 => calleeFqn; case i => calleeFqn.take(i) }
          val baseRetyped   = baseRetypedMethods.exists(m =>
            m.startsWith(calleeOwner) && m.contains('#') && {
              val mName = m.drop(m.indexOf('#') + 1)
              val cName = calleeFqn.drop(calleeFqn.indexOf('#') + 1)
              mName == cName
            })
          t.copy(args = t.args.zip(params).map { (arg, param) =>
            if seeds(param.symbol) && (calleeEmitted || baseRetyped) then
              wrapFor(arg, p.symbolOf(param.symbol).map(_.info).getOrElse(TypeRepr.NoType))
            else unwrapIfOpaque(arg)
          })
      case _ =>
        // external callee — no definition to read the formal from. The scope fence is the
        // defence; where an argument IS opaque and no coercion was possible, record a boundary finding.
        val calleeFqn = p.symbolOf(t.method).map(_.fullName).getOrElse("?")
        t.args.foreach { arg =>
          if carriesOpaque(arg) then
            val encl = p.symbolOf(t.method).map(_.owner).getOrElse(SymId.None)
            boundaryIssues += OpaqueBoundaryCheck.Finding(
              issue   = OpaqueBoundaryCheck.Issue.ExternalCallee,
              subject = calleeFqn,
              detail  = s"an opaque-typed argument reaches an external callee whose formal this " +
                s"program does not have — the scope fence is the defence (spec: ${spec.fqn})",
              origin  = t.origin,
              unit    = unitOf(p, encl),
            )
        }
        t

  // -------------------------------------------------------------------------
  // the boundary, read through the declaration. ENGINE-LIMITS §13 O1
  // -------------------------------------------------------------------------

  /** Does the value this term yields belong to the opaque family? Read through the declaration
    * and through compound expressions that CARRY a value (`if`, block, `match`) rather than the
    * node's own `tpe`, which is blind to `x == null ? 0 : x.getHandle()`. Carriers are enumerated;
    * an unenumerated one is a missed coercion — a loud compile error, not a spurious unwrap.
    */
  private def carriesOpaque(e: Term)(using Program): Boolean = e match
    case Tree.Commented(_, inner)     => carriesOpaque(inner)
    case Tree.If(_, a, b, _, _)       => carriesOpaque(a) || carriesOpaque(b)
    case Tree.Block(_, x, _, _, _)    => carriesOpaque(x)
    case Tree.Match(_, cases, _, _, _, _) => cases.exists(c => carriesOpaque(c.body))
    case Tree.ArrayAccess(arr, _, _, _) => isOpaqueArray(arr) || carriesOpaque(arr)
    case Tree.Ident(s, _, _)          => seeds(s) || isOpaque(e) || isOpaqueArray(e)
    case Tree.Select(_, s, _, _)      => seeds(s) || isOpaque(e) || isOpaqueArray(e)
    case Tree.Apply(_, _, m, _, _)    => isSeedMethod(m) || isOpaque(e) || isOpaqueArray(e)
    case other                        => isOpaque(other) || isOpaqueArray(other)

  /** Inserts `f` where the value is — at each leaf of a carrying expression, never around the
    * whole. Wrapping the whole is wrong for a mixed carrier: an `if` with one branch of each type
    * has no type a single coercion could take.
    */
  private def coerce(e: Term, tpe: TypeRepr, f: Term => Term)(using Program): Term = e match
    case Tree.Commented(l, inner)  => Tree.Commented(l, coerce(inner, tpe, f))
    case i: Tree.If                => i.copy(thenp = coerce(i.thenp, tpe, f), elsep = coerce(i.elsep, tpe, f), tpe = tpe)
    case b: Tree.Block             => b.copy(expr = coerce(b.expr, tpe, f), tpe = tpe)
    case m: Tree.Match             => m.copy(cases = m.cases.map(c => c.copy(body = coerce(c.body, tpe, f))), tpe = tpe)
    case leaf                      => f(leaf)

  /** coerce into the opaque type. A leaf that already carries it is left alone, so a mixed carrier
    * wraps only the branches that are still plain. */
  private def wrap(e: Term)(using Program): Term =
    if carriesOpaque(e) then coerce(e, opaqueRef, l => if carriesOpaque(l) then l else wrapCall(l))
    else wrapCall(e)

  /** Emits the wrap: `OpaqueCompanion(value)`. Also handles the boxed form of the primitive
    * (Java's auto-unbox is implicit in the TIR) — an opaque type IS the primitive at the JVM
    * level, so `Align(integerValue)` auto-unboxes the same way java's `int x = integerValue` does. */
  private def wrapCall(e: Term): Term =
    if isPrim(e.tpe) || isBoxedPrim(e.tpe) then
      Tree.Apply(Tree.Ident(objSym, TypeRepr.NoType, e.origin), List(e), applySym, opaqueRef, e.origin)
    else e

  private def unwrapCall(e: Term): Term =
    Tree.Apply(Tree.Select(Tree.Ident(objSym, TypeRepr.NoType, e.origin), unwrapSym, TypeRepr.NoType, e.origin),
      List(e), unwrapSym, primRef, e.origin)

  /** coerce out of it — a no-op unless the value really is a seed's, safe to ask at every
    * boundary. An array-of-opaque value is unwrapped with `unwrapArray`. */
  private def unwrapIfOpaque(e: Term)(using Program): Term =
    if !carriesOpaque(e) then e
    else if isOpaqueArray(e) then unwrapArrayCall(e)
    else coerce(e, primRef, l =>
      if isOpaqueArray(l) then unwrapArrayCall(l)
      else if carriesOpaque(l) then unwrapCall(l)
      else l)

  private def wrapReturns(body: Term)(using Program): Term = body match
    case Tree.Return(Some(e), tp, o) if isPrim(e.tpe) => Tree.Return(Some(wrap(e)), tp, o)
    case b: Tree.Block  => b.copy(stats = b.stats.map { case s: Term => wrapReturns(s); case s => s }, expr = wrapReturns(b.expr))
    case i: Tree.If     => i.copy(thenp = wrapReturns(i.thenp), elsep = wrapReturns(i.elsep))
    case e if isPrim(e.tpe) => wrap(e)
    case other          => other

  /** the dual, for a method that kept the primitive and returns a value carrying a seed. Only a
    * `return` and the body's tail are coerced — an ordinary statement is not a value the method
    * yields. */
  private def unwrapReturns(body: Term)(using Program): Term =
    def walk(t: Term, tail: Boolean): Term = t match
      case Tree.Return(Some(e), tp, o) => Tree.Return(Some(unwrapIfOpaque(e)), tp, o)
      case b: Tree.Block => b.copy(stats = b.stats.map { case s: Term => walk(s, false); case s => s },
                                   expr = walk(b.expr, tail))
      case i: Tree.If    => i.copy(thenp = walk(i.thenp, tail), elsep = walk(i.elsep, tail))
      case other if tail => unwrapIfOpaque(other)
      case other         => other
    walk(body, true)

  private def isOpaque(t: Term): Boolean   = headSym(t.tpe).contains(opaqueSym)
  private def isOpaqueArray(t: Term): Boolean = t.tpe match
    case TypeRepr.AppliedType(TypeRepr.TypeRef(_, s), List(TypeRepr.TypeRef(_, e))) =>
      s == arraySym && e == opaqueSym
    case _ => false
  private def isPrim(t: TypeRepr): Boolean = headSym(t).contains(primSym)
  /** is this the boxed form of the spec's primitive (`java.lang.Integer` for `Int`)? Auto-unboxes
    * to the primitive, so `Align(integerValue)` is valid. */
  private def isBoxedPrim(t: TypeRepr): Boolean = boxedPrimSym != SymId.None && headSym(t).contains(boxedPrimSym)

  /** the retyped type for a seed: `Prim` -> `Opaque.T`, `Array[Prim]` -> `Array[Opaque.T]`. */
  private def seedTypeRef(origType: TypeRepr): TypeRepr =
    if isArrayOfPrim(origType) then opaqueArrayRef else opaqueRef

  /** the return type of a seed method, retyped. */
  private def seedMethodRetType(m: SymId)(using p: Program): TypeRepr =
    p.symbolOf(m).map(_.info) match
      case Some(TypeRepr.MethodType(_, ret, _)) =>
        if isArrayOfPrim(ret) then opaqueArrayRef else opaqueRef
      case _ => opaqueRef

  /** wraps a value for assignment to a seed, dispatching scalar vs array coercion — including
    * when the declared type is already `Array[Opaque]` (read after the retype). */
  private def wrapFor(e: Term, origDeclType: TypeRepr)(using Program): Term =
    if isArrayOfPrim(origDeclType) || isArrayOfOpaque(origDeclType) then wrapArrayCall(e) else wrap(e)

  /** the declared type of the LHS of an assignment, read from the declaration, not the node — an
    * `ArrayAccess` LHS reads the element type of the array's declaration. */
  private def lhsDeclType(lhs: Term)(using p: Program): TypeRepr = lhs match
    case Tree.Ident(s, _, _)     => p.symbolOf(s).map(_.info).getOrElse(TypeRepr.NoType)
    case Tree.Select(_, s, _, _) => p.symbolOf(s).map(_.info).getOrElse(TypeRepr.NoType)
    case Tree.ArrayAccess(arr, _, _, _) =>
      lhsDeclType(arr) match
        case TypeRepr.AppliedType(_, List(elem)) => elem
        case other => other
    case _                       => TypeRepr.NoType

  // array coercion — wrapArray/unwrapArray calls.
  private def wrapArrayCall(e: Term): Term =
    Tree.Apply(Tree.Select(Tree.Ident(objSym, TypeRepr.NoType, e.origin), wrapArraySym, TypeRepr.NoType, e.origin),
      List(e), wrapArraySym, opaqueArrayRef, e.origin)

  private def unwrapArrayCall(e: Term): Term =
    Tree.Apply(Tree.Select(Tree.Ident(objSym, TypeRepr.NoType, e.origin), unwrapArraySym, TypeRepr.NoType, e.origin),
      List(e), unwrapArraySym, primArrayRef, e.origin)


  /** is this type `Array[Prim]` — an array whose element is the spec's primitive? */
  private def isArrayOfPrim(t: TypeRepr): Boolean = t match
    case TypeRepr.AppliedType(TypeRepr.TypeRef(_, s), List(elem)) =>
      s == arraySym && isPrim(elem)
    case _ => false

  /** is this type `Array[Opaque]`? Needed because the retyped symbol table reads `Array[Opaque]`
    * where the original had `Array[Prim]`, and `wrapFor` at an Assign LHS reads it after retyping. */
  private def isArrayOfOpaque(t: TypeRepr): Boolean = t match
    case TypeRepr.AppliedType(TypeRepr.TypeRef(_, s), List(TypeRepr.TypeRef(_, e))) =>
      s == arraySym && e == opaqueSym
    case _ => false

  /** is this a taggable type — the primitive itself or `Array[Prim]`? A method's result decides;
    * a parameter is not independently taggable. */
  private def taggablePrim(info: TypeRepr): Boolean = info match
    case r if isPrim(r)                              => true
    case r if isArrayOfPrim(r)                       => true
    case TypeRepr.MethodType(_, ret, _)              => isPrim(ret) || isArrayOfPrim(ret)
    case _                                           => false

  private def headSym(t: TypeRepr): Option[SymId] = t match
    case TypeRepr.TypeRef(_, s)      => Some(s)
    case TypeRepr.AppliedType(tc, _) => headSym(tc)
    case _                           => None
