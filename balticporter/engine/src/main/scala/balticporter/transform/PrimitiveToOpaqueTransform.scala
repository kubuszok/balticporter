package balticporter.transform

import balticporter.core.{PolicyFinding, PolicyIssue, PolicyReport, PolicySource, SurfacePolicy}
import balticporter.tir.*

/** Retype a semantically-tagged PRIMITIVE to an `opaque type` (+companion) everywhere it flows,
  * wrapping its construction sites and unwrapping its consumption sites. The sge/ssg case:
  * GL layer ids, key/button codes — an `int` that is really a distinct domain value, given a
  * real type so the compiler stops it being mixed with plain arithmetic ints.
  *
  * This is symbol-identity- and flow-driven, not textual:
  *
  *   1. HINTS — a small set of tagged symbols of the spec's primitive (the `hints` predicate, or
  *      `extraHints` fully-qualified names an agent supplies after a failed compile — see below).
  *      These are the [[SymTag]] seeds.
  *   2. DETECT (propagation) — the seed set is GROWN from the hints by tracing the
  *      whole-program reference graph: any symbol of the same primitive connected to a seed by a
  *      *pure-move* flow (assignment `a = b`, `val x = ref`, `return ref`, passing `ref`
  *      to a parameter) is itself a seed. The flow edges are read from the TIR, whose every
  *      reference was resolved to a `SymId` by Spoon — so this is "use the Java compiler's
  *      resolution to map old references to new". Arithmetic (`layer + 1`) is NOT a
  *      pure-move — it yields a plain value, correctly breaking the chain (and getting an
  *      unwrap). The mechanism is [[FlowPropagation]], shared with every other retyping rule;
  *      the detected mapping `symbol → opaque` is exposed as [[typeMapping]], a reusable trace.
  *   3. RETYPE + COERCE — each seed symbol's `info` becomes the opaque type; its references
  *      retype with it (found by SymId, via the xref — not by node `tpe`, which the
  *      populator left as the primitive); and coercions are inserted at the seed/primitive boundary
  *      (`wrap` a plain value flowing into a seed, `unwrap` a seed consumed as a plain one).
  *
  * Agent-in-the-loop: when the emitted Scala fails to compile, an agent reads the errors and
  * can pass the fully-qualified name of a missed declaration (one that should have been opaque but
  * wasn't reachable from the hints) as an `extraHints` entry; the next run re-propagates with
  * it. That closes the loop the design calls for.
  *
  * ==Everything a PORT says is in [[OpaqueSpec]]==
  * Which primitive, what the type is called, where it is minted, which declarations seed it and how
  * far propagation may reach. The phase holds the mechanism and nothing else, which is what makes
  * "turn a primitive into an opaque type" a §1(b) mechanism carrying a §1(c) policy rather than one
  * library's rule wearing an engine's clothes. It was `IntToOpaqueTransform(typeName, hint,
  * extraHints)` — an `Int`-only rule whose definition site was implicit — and every one of those
  * three limits was a decision nobody had made on purpose.
  *
  * ==Two instances in one pipeline COMPOSE, or the run fails==
  * A port with several opaque types runs several of these, and one symbol cannot be two opaque
  * types. Whichever instance runs second would silently decline the overlap — the first has already
  * retyped those symbols away from the primitive, so they are no longer eligible — and the port
  * would compile with half a domain type missing and nothing said. So the overlap is DETECTED (the
  * eligibility test admits a sibling's opaque type precisely so that reaching one is visible) and
  * the run is FAILED, naming the symbol and both specs. See [[refuseOverlap]].
  *
  * ==A COERCION reads the boundary through the DECLARATION, never the term node's `tpe`==
  * CLAUDE.md §1's rule for a scoped retyping phase, and this one was measured getting it wrong
  * (`ENGINE-LIMITS.md` §13 O1, 3 scalac errors). Every coercion here asks [[carriesOpaque]], which
  * reads the SEED TABLE through the declaration a value flows from and descends the compound
  * expressions that CARRY a value without being one — `if`, a block's tail, a `match` arm, a comment
  * wrapper. Nothing retypes a composite node from its branches, so a node-`tpe` test is exact for a
  * bare reference and blind to `x == null ? 0 : x.handle()`, which is the shape the corpus has.
  * The coercion is then PUSHED INTO EACH BRANCH rather than wrapped around the whole
  * ([[coerce]]), which is what the reference hand port writes and what leaves a declaration that
  * kept the primitive reading as java wrote it.
  *
  * ==A retyped PARAMETER moves its METHOD's signature too==
  * `ENGINE-LIMITS.md` §13 O2, 3 more errors. The TIR stores a parameter's type TWICE — on the
  * parameter symbol and in the enclosing method's `MethodType` — and which one a consumer reads is
  * its own business: the emitter renders the `ValDef`, the constructor funnel reads the signature
  * (deliberately, since an argument's type may be narrower than the formal), a published contract
  * row reads the signature. So the retype loop moves BOTH, by POSITION, in one motion.
  *
  * ==The MINT belongs to ONE module — the one that owns the declarations it was minted FOR==
  * `ENGINE-LIMITS.md` §13 O5, 24 errors over six dependent lanes with six suites stopped, and the
  * one gap here that is not about translation at all. This phase adds a TOP-LEVEL UNIT to the
  * program, and `PortRun.converted` classifies a unit by its recorded `Origin` — under `sourceRoot`
  * is owned, under a `resolutionRoot` is not, and a unit with NO usable origin is converted, because
  * refusing to emit on a missing origin would be a silent omission. That rule is right for a parsed
  * unit and wrong for a minted one: a dependent's `Program` CONTAINS its base's units (that is what
  * `resolutionRoots` is), so an inherited instance of this phase seeds there too, mints there too,
  * and every module in the chain writes its own copy of the same FQN. A minted opaque type cannot be
  * duplicated even harmlessly — opacity is per-DEFINITION, so inside the copy `T` binds to the FIRST
  * definition's abstract type and the copy's own `apply`/`unwrap` stop type-checking against it.
  *
  * So the mint is fenced by [[RunScope.emits]], the run's own answer to *does this module emit that
  * declaration at all* — CLAUDE.md §1.5's rule for a phase that SYNTHESISES a declaration, which is
  * the same one-module answer `inject` already owes. A dependent still RETYPES every reference and
  * COERCES at every boundary: it holds the minted symbols, `Program.owns` reports them external
  * exactly as it does a JDK symbol (they hang off no unit of this run), and the emitted
  * fully-qualified `Name.T` / `Name(…)` resolve against the object the OWNING module emitted and put
  * on the classpath — which is what a dependent lane already compiles against.
  *
  * '''Read off the HINTS and never off the grown seed set.''' The seed set grows along pure-move
  * flows, and a flow reaches a DEPENDENT's own declarations the moment that dependent so much as
  * assigns the base's tagged getter to a local — gdx-gltf's `SharedTextureTest` is exactly that. A
  * grown-set test would therefore hand the mint back to a module that merely USES the family, which
  * is the defect wearing a fence. The hints are what the SPEC NAMED, so the module declaring them is
  * the family's home, and a run owning none of them owns no part of the family.
  *
  * The fence is deliberately not a finding: withholding the mint is not a refusal, it is the phase
  * doing in a dependent exactly what it should. `PortRun` carries the loud half, in the direction
  * that can still go wrong — a synthesised unit at an FQN a base's published port map already claims
  * FAILS THE RUN, which is what catches the next phase that mints without asking.
  */
final class PrimitiveToOpaqueTransform(val spec: OpaqueSpec)
    extends Phase, Rewrite, PolicySource, SurfacePolicy, PolicyBound:
  def name = s"primitive->opaque:${spec.fqn}"

  /** The check lane that counts every seam this phase's retyping opened and could not close.
    *
    * [[OpaqueBoundaryCheck]] is this phase's own lane, following the pattern `CollectionsTransform`
    * -> `CollectionBoundaryCheck`, `NullabilityTransform` -> `NullabilityBoundaryCheck`. It counts
    * the three populations CLAUDE.md §1 requires: external callees whose class-file formals this
    * phase cannot read, scope boundaries where the opaque type meets the primitive, and
    * boxed-primitive slots the inline coercion could not reach.
    *
    * Named as a symbol rather than as a string so a renamed lane is a compile error and not a
    * silently unwired claim. */
  def accountedBy: Set[String] = Set(OpaqueBoundaryCheck.Name)

  /** This phase RETYPES declarations under a [[RuleScope]], so two modules configuring it
    * differently emit signatures that each compile alone and cannot compile together — CLAUDE.md
    * §1's standing obligation, and it was unmet: with no `SurfacePolicy`,
    * [[balticporter.core.PortManifest.fingerprint]] compared two instances by NAME, and the name is
    * `primitive->opaque:<fqn>`, so a base and a dependent seeding the same opaque type from
    * different declarations compared EQUAL.
    *
    * Everything in the spec is rendered, sorted. `hints` is now a `Set[String]` of exact FQNs
    * (O4 CLOSED — the predecessor was a `Symbol => Boolean` with no stable rendering, so two specs
    * differing only in their predicate compared equal). The fence, the definition site, the
    * primitive, every hint and every agent-supplied `extraHints` entry are all compared.
    *
    * O6 CLOSED: when the target is `Existing`, the TARGET FQN is rendered instead of (or beside)
    * the mint FQN. Two modules disagreeing about which existing type a family retypes to is §1.5's
    * two-ports-that-cannot-compile-together. The segment is empty when `target = Mint` and renders
    * unconditionally when `target = Existing`, so §1(b)'s fingerprint no-op rule holds.
    */
  def surfaceFingerprint: String =
    val seeds  = if spec.hints.isEmpty then "" else s";hints=${spec.hints.toList.sorted.mkString(",")}"
    val extras = if spec.extraHints.isEmpty then "" else s";extra=${spec.extraHints.toList.sorted.mkString(",")}"
    val fence  = spec.scope.fingerprint
    val tgt = spec.target match
      case OpaqueSpec.Target.Mint => ""
      case OpaqueSpec.Target.Existing(t, w, u) => s";target=$t;wrap=$w;unwrap=$u"
    s"${spec.fqn}:${spec.underlyingFqn}$seeds$extras${if fence.isEmpty then "" else s";$fence"}$tgt"

  /** Hints the mechanism CANNOT REACH — see [[reportUnreachable]]. Empty policy in, empty report
    * out, and cleared at the head of every run so a reused instance never reports the previous
    * translation's findings. */
  private val unreachable = collection.mutable.ListBuffer.empty[PolicyFinding]

  /** Boundary findings accumulated during [[run]] — the seams this phase opened and could not
    * close. Cleared at the head of every run so a reused instance never reports the previous
    * translation's findings. */
  private val boundaryIssues = collection.mutable.ListBuffer.empty[OpaqueBoundaryCheck.Finding]

  /** Every boundary site this phase opened and could not close, restricted to the units this run
    * actually EMITS — `ENGINE-LIMITS.md` D2. A base port passes `program.units`; a dependent
    * passes `checkedUnits`. */
  def boundary(units: List[Tree.ClassDef]): List[OpaqueBoundaryCheck.Finding] =
    val emitted = units.map(_.symbol).toSet
    boundaryIssues.toList.filter(f => emitted(f.unit))
      .sortBy(f => (f.origin.javaPath, f.origin.line, f.subject, f.issue.toString))

  def policyReport: PolicyReport = PolicyReport(unreachable.toList)

  private var objSym, opaqueSym, applySym, unwrapSym, wrapArraySym, unwrapArraySym, primSym, boxedPrimSym, arraySym: SymId = SymId.None
  private var seeds: Set[SymId]   = Set.empty
  private var opaqueRef: TypeRepr = TypeRepr.NoType
  private var primRef: TypeRepr   = TypeRepr.NoType
  /** `Array[Opaque.T]` — what an `int[]` seed becomes after retyping. */
  private var opaqueArrayRef: TypeRepr = TypeRepr.NoType
  /** `Array[Int]` — the JVM-level array type the coercion wraps/unwraps. */
  private var primArrayRef: TypeRepr   = TypeRepr.NoType
  private val minted = collection.mutable.ListBuffer[Symbol]()

  /** what the RUN knows about ITSELF — which top-level units it EMITS. Not derivable from the
    * `Program` a phase is handed (a dependent's contains its base's units); see [[RunScope]]. The
    * default is the base-port answer, which is also every spec's and `DebugEmit`'s, so a consumer
    * cannot take a different code path under test than it does in a port. */
  private var runScope: RunScope = RunScope.whole

  /** Nothing to BIND: this phase's policy is a predicate and an FQN set, neither of which is a key
    * the binder resolves. What it is here for is [[runScope]] — `PolicyBinder` is already the one
    * object the run hands every phase before the pipeline starts, and a second channel would be a
    * second thing a caller must remember. */
  def bindPolicy(binder: PolicyBinder): Unit = runScope = binder.run

  /** the detected old→new type mapping: every primitive symbol retyped to the opaque type. A
    * reusable trace (also what a semantic-diff of this phase would report).
    * O3: array seeds map to `Array[Opaque.T]` rather than `Opaque.T`. */
  def typeMapping: Map[SymId, TypeRepr] = seeds.iterator.map(id => id -> opaqueRef).toMap

  override def run(program: Program): Program =
    unreachable.clear()
    boundaryIssues.clear()
    primSym = program.symbols.all.find(_.fullName == spec.underlyingFqn).map(_.id).getOrElse(SymId.None)
    if primSym == SymId.None then return program
    primRef = TypeRepr.TypeRef(TypeRepr.NoType, primSym)
    boxedPrimSym = program.symbols.all.find(_.fullName == spec.underlying.boxedFqn).map(_.id).getOrElse(SymId.None)
    arraySym = program.symbols.all.find(_.fullName == "scala.Array").map(_.id).getOrElse(SymId.None)

    // The SCOPE fences seeding as well as propagation: a fence a named entry could step over is not
    // a fence, and a pure-move chain crosses type boundaries freely enough that one careless hint
    // pulls half a library in. `RuleScope.Everywhere()` — the default — fences nothing, so this
    // filter is the identity and the phase behaves exactly as it did before it took a scope.
    def fenced(s: Symbol): Boolean = spec.scope.includes(program, s)
    // A hint may also name something a SIBLING spec has already claimed — admitted here on purpose
    // so `refuseOverlap` can see it. Filtered out silently (which is what "it is no longer of my
    // primitive" would do) the second instance would simply find nothing and return.
    val named = program.symbols.all.filter(s => spec.hints(s.fullName) || spec.extraHints(s.fullName))
    val hints = named
      .filter(s => fenced(s) && (taggablePrim(s.info) || foreignOpaque(program, s.info).isDefined))
      .map(_.id).toSet
    // …and the ones this MECHANISM cannot reach, before any early return: a spec that names one is
    // the shape that used to look exactly like a typo (see [[reportUnreachable]]).
    reportUnreachable(program, named.filter(fenced))
    if hints.isEmpty then return program
    // The spanning-hints check and mint-ownership logic only apply when MINTING — an Existing target
    // has no unit to collide on, because the definition is supplied by `Substitutions` (drop+inject).
    if spec.isMint then refuseSpanningHints(program, hints)
    seeds = propagate(program, hints) // grow the seed set along pure-move flows
    refuseOverlap(program)
    if seeds.isEmpty then return program

    var next = program.symbols.all.map(_.id.raw).maxOption.getOrElse(-1) + 1
    def mint(name: String, full: String, flags: Flags, owner: SymId = SymId.None, info: TypeRepr = TypeRepr.NoType): SymId =
      val id = SymId(next); next += 1
      minted += Symbol(id, name, full, flags, owner, info)
      id

    // The SYNTHESIS of the opaque type's symbols depends on the target form. Both paths mint the
    // same PHANTOM SYMBOLS (objSym, opaqueSym, applySym, unwrapSym) so the coercion code below is
    // one path. The difference is that `Mint` creates a full ClassDef unit and `Existing` does not.
    val synthUnit: Option[Tree.ClassDef] = spec.target match
      case OpaqueSpec.Target.Mint =>
        // The object is a TOP-LEVEL unit whose `fullName` is the spec's FQN, and that FQN is the
        // whole of "where is it defined": the emitter derives the `package` clause from the name's
        // prefix and the file from the package.
        objSym    = mint(spec.objectName, spec.fqn, Flags(isModule = true))
        // owner = the object, so the emitter renders references as the path-dependent `Name.T`
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

        // O3: array wrap/unwrap
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
        // O6 CLOSED: the opaque type ALREADY EXISTS (an injected replacement). Mint PHANTOM symbols
        // for the existing type's companion, type, and coercion methods — the same shape
        // `NullabilityTransform.Target.Named` uses for its five members. No ClassDef is synthesised.
        //
        // The symbols are phantom: they carry the FQN so the emitter renders fully-qualified
        // references, but no unit is created — the definition is supplied by `Substitutions`
        // (drop + inject), and the injected file is what scalac compiles against.
        val companionFqn = typeFqn // opaque companion shares the type's path
        objSym    = mint(spec.objectName, companionFqn, Flags(isModule = true))
        // For an Existing target, the type IS the FQN (e.g. `sge.utils.Align`), not a member `T`.
        // The opaque symbol must NOT be owned by the companion, because the emitter renders
        // `owner.name` for a module member — which would produce `Align.Align` instead of `Align`.
        // With no owner, the emitter falls through to `nestedPath`, which renders the fullName
        // directly (and for a name without `$`, that is `escPath(fullName)` = the FQN as-is).
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

        // O3: array coercions for the Existing form too — the same erasure identity applies.
        if arraySym != SymId.None then
          wrapArraySym   = mint("wrapArray",   s"$companionFqn.wrapArray",   Flags(), objSym,
            TypeRepr.MethodType(List("v" -> primArrayRef), opaqueArrayRef))
          unwrapArraySym = mint("unwrapArray", s"$companionFqn.unwrapArray", Flags(), objSym,
            TypeRepr.MethodType(List("v" -> opaqueArrayRef), primArrayRef))

        None // no unit minted — the definition is the injected file

    // A retyped PARAMETER's own METHOD, by POSITION — `ENGINE-LIMITS.md` §13 O2.
    //
    // BY POSITION and never by name: a `MethodType`'s parameter list and its `DefDef`'s are
    // parallel by construction, while the NAMES are not — an earlier phase may rewrite a parameter
    // SLOT without touching the method's `info` (the reassigned-parameter transform mints
    // `x$arg` for exactly that), and read by name the signature silently would not move. The same
    // correction `NullabilityTransform` records, for the same reason and in the same shape.
    //
    // A method whose enclosing DEFINITION the program does not have contributes nothing: its
    // parameters are not this program's declarations either, so there is no disagreement to close.
    val seedParamSlots: Map[SymId, Set[Int]] =
      program.symbols.all.iterator.filter(s => seeds(s.id) && s.flags.isParam).toList
        .groupBy(_.owner).flatMap { (owner, ps) =>
          program.definitionOf(owner).collect { case d: Tree.DefDef =>
            val at = d.paramss.flatten.map(_.symbol).zipWithIndex.toMap
            owner -> ps.flatMap(p => at.get(p.id)).toSet
          }
        }.toMap

    /** ONE method's signature: every seeded parameter slot, plus the result when the method itself
      * is a seed. Both halves in one place, because they are two faces of one declaration and a
      * consumer reads whichever it reads. O3: an array parameter/result is retyped too. */
    def methodType(id: SymId, mt: TypeRepr.MethodType): TypeRepr.MethodType =
      val slots = seedParamSlots.getOrElse(id, Set.empty)
      def retypeSlot(t: TypeRepr): TypeRepr =
        if isPrim(t) then opaqueRef else if isArrayOfPrim(t) then opaqueArrayRef else t
      TypeRepr.MethodType(
        mt.params.zipWithIndex.map((nt, i) => if slots(i) then nt._1 -> retypeSlot(nt._2) else nt),
        if seeds(id) then retypeSlot(mt.result) else mt.result,
        mt.isImplicit)

    // retype seed symbol infos primitive → opaque (value seeds) / return → opaque (method seeds)
    // / the parameter slots of ANY method — seed or not — one of whose parameters is a seed.
    // O3: array seeds are retyped Array[Prim] → Array[Opaque.T].
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

    // DECISION PROVENANCE: one row per DECLARATION whose signature became the opaque type.
    //
    // `Reason.LibraryRule` — CLAUDE.md §1's canonical (c). The MECHANISM is shared (seed, propagate
    // along pure-move flows, retype, coerce at the boundary) but WHICH primitives are really a
    // domain value is knowledge about one library and nothing else, so an agent reading this row has to
    // look in that library's own rule, not in a manifest key and not in the engine. Parameters and
    // method-locals are seeds too and are deliberately not rows: their method's signature already
    // moved with them (`Decision.isDeclaration`), and the coercions inserted at every boundary are
    // site-level and visible in the emitted diff.
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

    // THE MINT IS ONE MODULE'S — `ENGINE-LIMITS.md` §13 O5, and see the class doc for why the test
    // is on the HINTS rather than on the grown seed set. Everything above this line happens in every
    // module that inherits the instance: the symbols are minted, the declarations are retyped and
    // every boundary is coerced. What a module that owns none of the tagged declarations must NOT do
    // is WRITE the object, because `PortRun.converted` emits an origin-less unit and would then
    // write one copy per module of an FQN that cannot be duplicated even harmlessly.
    //
    // For the Existing form (O6), there is no unit to mint at all — the definition is the injected
    // file. The walked tree is the entire result.
    val walked = program.units.map(u => StandardTraversal.mapClassDef(this, u))
    val units = synthUnit match
      case Some(su) if mintsHere(program, hints) => walked :+ su
      case _ => walked
    program.rebuilt(units, symbols)

  /** Does THIS module own the declarations the spec named? Read through [[RunScope.emits]], which is
    * the run's own emitted-unit set and the same predicate every other module-ownership question in
    * the engine now goes through.
    *
    * `true` whenever there is no run scope (a base port, a single-module port, a spec, `DebugEmit`),
    * by [[RunScope.whole]] — so this is the identity everywhere the pre-fix code was already right,
    * and there is no second code path for a port to be silently on.
    *
    * `exists` is safe BECAUSE of [[refuseSpanningHints]], which has already run: the bound hints are
    * all this module's or none of them are, so `exists` and `forall` agree and the question has one
    * answer. Read on its own it is the more DANGEROUS of the two — see that method. */
  private def mintsHere(p: Program, hints: Set[SymId]): Boolean =
    hints.exists(id => runScope.emits(unitOf(p, id)))

  /** FAIL THE RUN when the spec's BOUND HINTS land in more than one module — O5's fence, which
    * admitted exactly the shape it exists to prevent.
    *
    * [[mintsHere]] asks `hints.exists(owned)`, and `exists` is the WRONG quantifier for a fence whose
    * answer decides who WRITES a file. A spec's `hints` is a predicate over `Symbol` — libGDX's is an
    * exact FQN, but the type invites `_.name == "handle"`, which is the form that reads naturally and
    * matches whatever a dependent happens to have called a field. One such match inside a dependent's
    * own units makes `exists` true THERE, and the base's own hints make it true in the BASE, so both
    * modules mint the same FQN: precisely the 24-error, six-suites-stopped failure O5 measured, with
    * the fence in place and answering.
    *
    * The `claimedSynthetic` belt behind it does not close this, and its own doc says why it cannot be
    * relied on to: `PortRun.claimedSynthetic(_, _, Nil)` is `Nil`, so a base with NO published map —
    * or one proven stale, which shares the path — ADMITS the second copy. Note the direction that
    * asymmetry runs in against `DESIGN.md` §8.13's `governs` screen, which REFUSES when it has no map
    * to read. Both are deliberate and neither is a defect (see the belt's own note at
    * `PortRun.claimedSynthetic`), but a fence that leans on a belt which admits by default is a fence
    * with no floor. So the fence answers for itself.
    *
    * The phase can answer it: it holds the `RunScope` and it already resolves a symbol to its
    * top-level unit ([[unitOf]]) for [[mintsHere]]. A hint set that straddles the two is a spec whose
    * intent the engine cannot recover — mint here, mint there, or mint in neither is a choice about
    * a library's shared surface — so it refuses and names both sides.
    *
    * §1(c) LIBRARY RULE, and the fix is in the port: `hints` names declarations of ONE module. A
    * dependent that genuinely wants its own domain type declares its own spec, with its own FQN.
    *
    * A throw and not a finding, for [[refuseOverlap]]'s reason: there is no honest program to emit. */
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

  /** the TOP-LEVEL unit a symbol belongs to — how a symbol is held to the module that emits it.
    * Fuel-bounded, so a corrupt owner chain cannot hang the phase. */
  private def unitOf(p: Program, id: SymId, fuel: Int = 64): SymId =
    p.symbolOf(id) match
      case Some(s) if s.owner != SymId.None && fuel > 0 => unitOf(p, s.owner, fuel - 1)
      case _                                            => id

  /** Seed detection — [[FlowPropagation]] over the symbols of this spec's primitive, with the
    * hints as roots.
    *
    * The mechanism is SHARED rather than owned here: "a rewritten declaration carries every
    * reference and call site with it" is the second half of every retyping rule, not a fact about
    * opaque types (`CollectionsTransform` grows a `RuleScope.Only` the same way). What stays here
    * is the eligibility test, which is this phase's own record of what it retypes — and it admits
    * one thing beyond its own primitive on purpose: a SIBLING'S OPAQUE TYPE. See [[refuseOverlap]];
    * an overlap the propagation refused to walk into is an overlap nobody can see. */
  private def propagate(p: Program, hints: Set[SymId]): Set[SymId] =
    FlowPropagation.grow(p, hints, id => p.symbolOf(id).exists(s =>
      (taggablePrim(s.info) || foreignOpaque(p, s.info).isDefined) && spec.scope.includes(p, s)))

  /** A HINT THE MECHANISM CANNOT REACH — `ENGINE-LIMITS.md` §13 O3, made loud.
    *
    * [[taggablePrim]] tests a symbol's OWN info against the spec's primitive, so a declaration
    * whose domain value sits INSIDE a container — `int[] locations`, which a reference hand port
    * types `Array[AttributeLocation]` — is invisible to seeding, and to propagation as well, since
    * `FlowPropagation`'s edges run between SYMBOLS and an array's element has none.
    *
    * The failure without this is quiet in exactly the way that matters: the hint does not throw and
    * does not refuse, it simply matches nothing — which reads identically to a typo, and sends its
    * author looking for a misspelling that is not there. So the phase reports the one case it can
    * TELL APART: a hint that named a real declaration of this program, inside the fence, whose type
    * MENTIONS the spec's primitive somewhere the mechanism cannot seed. That is not a guess about
    * intent; it is the observation that the author wrote a name whose type contains the very
    * primitive the spec is about.
    *
    * `Malformed` and not `NeverMatched`, deliberately: the key named something, so "your key matches
    * nothing" is the wrong sentence, and `PolicyReport`'s three answers already contain the right
    * one — *it could never have named anything the phase can act on*. The detail says which of §1's
    * three kinds the fix is, because the honest answer here is (a) ENGINE and not the §1(b) the
    * finding's own render assumes: a spec has no vocabulary for "the element of", so the exits are
    * to drop the hint or to widen the mechanism.
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

  /** the type a declaration's VALUE has — a method's RESULT, anything else's own info. The same
    * shape [[taggablePrim]] tests, so the report's domain is exactly the seeding rule's domain: a
    * hint naming a method whose PARAMETER is the primitive is a different mistake with a policy
    * exit (name the parameter), and reporting it here would send its author to the engine. */
  private def valueTypeOf(info: TypeRepr): TypeRepr = info match
    case TypeRepr.MethodType(_, ret, _)                       => ret
    case TypeRepr.PolyType(_, TypeRepr.MethodType(_, ret, _)) => ret
    case other                                                => other

  /** does this type mention the spec's primitive ANYWHERE — as an array element, a type argument, a
    * parameter? Walked with `StandardTraversal.mapType` and not a private recursion, so a `TypeRepr`
    * shape added later is reached without this remembering to enumerate it (CLAUDE.md §3). */
  private def mentionsPrim(t: TypeRepr)(using Program): Boolean =
    var found = false
    val scan = new Phase:
      def name = "primitive->opaque/mentions"
      override def transformType(x: TypeRepr)(using Program): TypeRepr =
        if isPrim(x) then found = true
        x
    StandardTraversal.mapType(scan, t)
    found

  /** the OTHER opaque object a symbol's declared type belongs to, if any — read from the
    * `isOpaque` flag, which only this phase ever sets, and reported by its OWNER's name because
    * that is the sibling spec's `fqn`. `None` for this phase's own (nothing is minted yet when
    * this runs). */
  private def foreignOpaque(p: Program, info: TypeRepr): Option[String] =
    val head = info match
      case TypeRepr.MethodType(_, ret, _) => headSym(ret)
      case other                          => headSym(other)
    head.filter(_ != opaqueSym).flatMap(p.symbolOf).filter(_.flags.isOpaque)
      .flatMap(t => p.symbolOf(t.owner).map(_.fullName).orElse(Some(t.fullName)))

  /** FAIL THE RUN when this spec's seeds overlap another `PrimitiveToOpaqueTransform`'s.
    *
    * A port with several opaque types runs several instances of this phase, and one symbol cannot
    * be two opaque types. The failure mode without this is entirely silent and order-dependent:
    * whichever instance runs second finds those symbols already retyped away from the primitive,
    * declines them as ineligible, and emits a port with half a domain type missing — a green
    * compile, no count moved, and a `decisions.tsv` whose only evidence is a row that is not there.
    * Precisely the shape CLAUDE.md §3 is about.
    *
    * So the propagation is allowed to WALK INTO a sibling's opaque type (see [[propagate]]) and the
    * overlap is refused here, naming the symbol and both specs. Exactly one instance detects each
    * overlap — the one that runs after the other — which is what makes the message deterministic
    * for a given pipeline order.
    *
    * A throw and not a `CheckReport` finding: a check reports on emitted code, and there is no
    * honest program to emit. `Pipeline.order` throws on a phase cycle for the same reason. */
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
  // A DECLARATION is the boundary, on BOTH sides of the `if`: a seed declaration wraps whatever
  // arrives, and a declaration that KEPT the primitive unwraps whatever seed arrives — which is the
  // face `ENGINE-LIMITS.md` §13 O1 measured (`int h1 = texture == null ? 0 : texture.getHandle()`
  // is a boundary precisely BECAUSE `h1` is correctly not a seed: an `if` is not a pure move, so
  // `FlowPropagation` builds no edge to it and the local rightly keeps `int`).
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
        else t.copy(args = t.args.zip(params).map { (arg, param) =>
          if seeds(param.symbol) then wrapFor(arg, p.symbolOf(param.symbol).map(_.info).getOrElse(TypeRepr.NoType))
          else unwrapIfOpaque(arg)
        })
      case _ =>
        // EXTERNAL CALLEE — no definition in the program. If any argument carries the opaque type,
        // the class-file formal still expects the primitive, and `coerceArgs` cannot read that
        // formal. The SCOPE FENCE is the configured defence: with the fenced types' declarations
        // outside the seed set, the arguments reaching this call are still primitives and no
        // coercion is needed. Where an argument IS opaque and no coercion was possible, record
        // a boundary finding.
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
  // THE BOUNDARY, read through the DECLARATION — `ENGINE-LIMITS.md` §13 O1.
  // -------------------------------------------------------------------------

  /** Does the VALUE this term yields belong to the opaque family?
    *
    * '''Read through the DECLARATION, and through the compound expressions that CARRY a value.'''
    * A node's own `tpe` is exact for a bare reference — `transformIdent`/`transformSelect`/
    * `transformApply` retype those as they pass — and blind to every term that merely carries one,
    * because nothing retypes a composite node from its branches. `x == null ? 0 : x.getHandle()` is
    * an `If` whose `tpe` is still the primitive and whose value is a seed's on one branch, and that
    * is the shape the corpus has (3 measured scalac errors, all of it).
    *
    * The CARRIERS are enumerated, and an unenumerated one is a MISSED coercion — the same failure
    * direction [[FlowPropagation]] argues for and for the same reason: a missing coercion is a
    * compile error at the site, loud and attributable, while a spurious one would silently unwrap a
    * value nothing asked about. `Match` is here because a switch EXPRESSION carries its arms'
    * values; a `Try` and a `Lambda` are not, and each is a missed edge rather than a wrong one.
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

  /** Insert `f` WHERE THE VALUE IS — at each leaf of a carrying expression, never around the whole.
    *
    * `ENGINE-LIMITS.md` §13 O1 left two candidates and this is the one the reference hand port
    * writes: `if (t == null) 0 else Handle.unwrap(t.getHandle())`, so the declaration that kept the
    * primitive reads as java wrote it. Wrapping the whole is not merely uglier — it is WRONG for a
    * mixed carrier, since an `if` with one branch of each type has no type a single coercion could
    * take (an opaque type's upper bound outside its own object is `Any`).
    */
  private def coerce(e: Term, tpe: TypeRepr, f: Term => Term)(using Program): Term = e match
    case Tree.Commented(l, inner)  => Tree.Commented(l, coerce(inner, tpe, f))
    case i: Tree.If                => i.copy(thenp = coerce(i.thenp, tpe, f), elsep = coerce(i.elsep, tpe, f), tpe = tpe)
    case b: Tree.Block             => b.copy(expr = coerce(b.expr, tpe, f), tpe = tpe)
    case m: Tree.Match             => m.copy(cases = m.cases.map(c => c.copy(body = coerce(c.body, tpe, f))), tpe = tpe)
    case leaf                      => f(leaf)

  /** COERCE INTO the opaque type. A leaf that already carries it is left alone, so a MIXED carrier
    * wraps only the branches that are still plain. */
  private def wrap(e: Term)(using Program): Term =
    if carriesOpaque(e) then coerce(e, opaqueRef, l => if carriesOpaque(l) then l else wrapCall(l))
    else wrapCall(e)

  /** Emit the wrap: `OpaqueCompanion(value)`. Handles the BOXED form of the primitive too: Java's
    * auto-unbox from `Integer` to `int` is implicit in the TIR, so a `wrapCall` that only handles
    * the unboxed form leaves a boxed slot (`Cell.align: Integer`) unreachable — the argument sits
    * between two opaque coercions and no explicit unbox node exists. Because an opaque type IS
    * the primitive at the JVM level, `Align(integerValue)` auto-unboxes the same way java's
    * `int x = integerValue` does, so the wrap is the same call. */
  private def wrapCall(e: Term): Term =
    if isPrim(e.tpe) || isBoxedPrim(e.tpe) then
      Tree.Apply(Tree.Ident(objSym, TypeRepr.NoType, e.origin), List(e), applySym, opaqueRef, e.origin)
    else e

  private def unwrapCall(e: Term): Term =
    Tree.Apply(Tree.Select(Tree.Ident(objSym, TypeRepr.NoType, e.origin), unwrapSym, TypeRepr.NoType, e.origin),
      List(e), unwrapSym, primRef, e.origin)

  /** COERCE OUT of it — a no-op unless the value really is a seed's, which is what makes this safe
    * to ask at every boundary rather than only at the ones a node type made visible.
    * O3: an array-of-opaque value is unwrapped with `unwrapArray`. */
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

  /** The dual, for a method that KEPT the primitive and returns a value carrying a seed — O1 one
    * node up from a `val`. Only a `return` expression and the body's TAIL are coerced: an ordinary
    * statement is not a value the method yields, and rewriting one would coerce an expression
    * nothing consumes. */
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
  /** Is this the BOXED form of the spec's primitive — `java.lang.Integer` for `Int`, etc.? A value
    * of this type auto-unboxes to the primitive, so `Align(integerValue)` is valid. */
  private def isBoxedPrim(t: TypeRepr): Boolean = boxedPrimSym != SymId.None && headSym(t).contains(boxedPrimSym)

  /** The retyped type for a seed: `Prim` -> `Opaque.T`, `Array[Prim]` -> `Array[Opaque.T]`. */
  private def seedTypeRef(origType: TypeRepr): TypeRepr =
    if isArrayOfPrim(origType) then opaqueArrayRef else opaqueRef

  /** The return type of a seed method, retyped. */
  private def seedMethodRetType(m: SymId)(using p: Program): TypeRepr =
    p.symbolOf(m).map(_.info) match
      case Some(TypeRepr.MethodType(_, ret, _)) =>
        if isArrayOfPrim(ret) then opaqueArrayRef else opaqueRef
      case _ => opaqueRef

  /** Wrap a value for assignment to a seed. Dispatches scalar vs array coercion (O3).
    * O8: when the declared type is already `Array[Opaque]` (read from the retyped symbol table at
    * an Assign LHS), array coercion applies — the same shape as `Array[Prim]`, read after the
    * retype rather than before it. */
  private def wrapFor(e: Term, origDeclType: TypeRepr)(using Program): Term =
    if isArrayOfPrim(origDeclType) || isArrayOfOpaque(origDeclType) then wrapArrayCall(e) else wrap(e)

  /** The declared type of the LHS of an assignment — read from the DECLARATION, not the node.
    * O8: an `ArrayAccess` LHS reads the ELEMENT type of the array's declaration. */
  private def lhsDeclType(lhs: Term)(using p: Program): TypeRepr = lhs match
    case Tree.Ident(s, _, _)     => p.symbolOf(s).map(_.info).getOrElse(TypeRepr.NoType)
    case Tree.Select(_, s, _, _) => p.symbolOf(s).map(_.info).getOrElse(TypeRepr.NoType)
    case Tree.ArrayAccess(arr, _, _, _) =>
      lhsDeclType(arr) match
        case TypeRepr.AppliedType(_, List(elem)) => elem
        case other => other
    case _                       => TypeRepr.NoType

  // O3: array coercion — wrapArray/unwrapArray calls.
  private def wrapArrayCall(e: Term): Term =
    Tree.Apply(Tree.Select(Tree.Ident(objSym, TypeRepr.NoType, e.origin), wrapArraySym, TypeRepr.NoType, e.origin),
      List(e), wrapArraySym, opaqueArrayRef, e.origin)

  private def unwrapArrayCall(e: Term): Term =
    Tree.Apply(Tree.Select(Tree.Ident(objSym, TypeRepr.NoType, e.origin), unwrapArraySym, TypeRepr.NoType, e.origin),
      List(e), unwrapArraySym, primArrayRef, e.origin)


  /** Is this type `Array[Prim]` — an array whose element is the spec's primitive? */
  private def isArrayOfPrim(t: TypeRepr): Boolean = t match
    case TypeRepr.AppliedType(TypeRepr.TypeRef(_, s), List(elem)) =>
      s == arraySym && isPrim(elem)
    case _ => false

  /** Is this type `Array[Opaque]` — an array whose element is the opaque type? O8: the retyped
    * symbol table reads `Array[Opaque]` where the original had `Array[Prim]`, and `wrapFor` at an
    * Assign LHS needs to dispatch array coercion on the retyped shape. */
  private def isArrayOfOpaque(t: TypeRepr): Boolean = t match
    case TypeRepr.AppliedType(TypeRepr.TypeRef(_, s), List(TypeRepr.TypeRef(_, e))) =>
      s == arraySym && e == opaqueSym
    case _ => false

  /** Is this a taggable type — either the primitive itself or `Array[Prim]` (O3)?
    * A method's RESULT is what decides; a parameter is not independently taggable. */
  private def taggablePrim(info: TypeRepr): Boolean = info match
    case r if isPrim(r)                              => true
    case r if isArrayOfPrim(r)                       => true
    case TypeRepr.MethodType(_, ret, _)              => isPrim(ret) || isArrayOfPrim(ret)
    case _                                           => false

  private def headSym(t: TypeRepr): Option[SymId] = t match
    case TypeRepr.TypeRef(_, s)      => Some(s)
    case TypeRepr.AppliedType(tc, _) => headSym(tc)
    case _                           => None
