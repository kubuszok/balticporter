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
    extends Phase, PolicySource, SurfacePolicy, PolicyBound:
  def name = s"primitive->opaque:${spec.fqn}"

  /** This phase RETYPES declarations under a [[RuleScope]], so two modules configuring it
    * differently emit signatures that each compile alone and cannot compile together — CLAUDE.md
    * §1's standing obligation, and it was unmet: with no `SurfacePolicy`,
    * [[balticporter.core.PortManifest.fingerprint]] compared two instances by NAME, and the name is
    * `primitive->opaque:<fqn>`, so a base and a dependent seeding the same opaque type from
    * different declarations compared EQUAL.
    *
    * Everything DECLARATIVE in the spec is rendered, sorted. '''[[OpaqueSpec.hints]] is not, and
    * cannot be''' — it is a `Symbol => Boolean`, and a lambda has no stable rendering; two specs
    * differing only in their predicate therefore still compare equal. That residue is named in
    * `ENGINE-LIMITS.md` §13 rather than hidden, and it is strictly smaller than the hole it
    * replaces: the fence, the definition site, the primitive and every agent-supplied
    * `extraHints` entry — which is where a predicate's misses are corrected — are all now compared.
    */
  def surfaceFingerprint: String =
    val extras = if spec.extraHints.isEmpty then "" else s";extra=${spec.extraHints.toList.sorted.mkString(",")}"
    val fence  = spec.scope.fingerprint
    s"${spec.fqn}:${spec.underlyingFqn}$extras${if fence.isEmpty then "" else s";$fence"}"

  /** Hints the mechanism CANNOT REACH — see [[reportUnreachable]]. Empty policy in, empty report
    * out, and cleared at the head of every run so a reused instance never reports the previous
    * translation's findings. */
  private val unreachable = collection.mutable.ListBuffer.empty[PolicyFinding]

  def policyReport: PolicyReport = PolicyReport(unreachable.toList)

  private var objSym, opaqueSym, applySym, unwrapSym, primSym: SymId = SymId.None
  private var seeds: Set[SymId]   = Set.empty
  private var opaqueRef: TypeRepr = TypeRepr.NoType
  private var primRef: TypeRepr   = TypeRepr.NoType
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
    * reusable trace (also what a semantic-diff of this phase would report). */
  def typeMapping: Map[SymId, TypeRepr] = seeds.iterator.map(_ -> opaqueRef).toMap

  override def run(program: Program): Program =
    unreachable.clear()
    primSym = program.symbols.all.find(_.fullName == spec.underlyingFqn).map(_.id).getOrElse(SymId.None)
    if primSym == SymId.None then return program
    primRef = TypeRepr.TypeRef(TypeRepr.NoType, primSym)

    // The SCOPE fences seeding as well as propagation: a fence a named entry could step over is not
    // a fence, and a pure-move chain crosses type boundaries freely enough that one careless hint
    // pulls half a library in. `RuleScope.Everywhere()` — the default — fences nothing, so this
    // filter is the identity and the phase behaves exactly as it did before it took a scope.
    def fenced(s: Symbol): Boolean = spec.scope.includes(program, s)
    // A hint may also name something a SIBLING spec has already claimed — admitted here on purpose
    // so `refuseOverlap` can see it. Filtered out silently (which is what "it is no longer of my
    // primitive" would do) the second instance would simply find nothing and return.
    val named = program.symbols.all.filter(s => spec.hints(s) || spec.extraHints(s.fullName))
    val hints = named
      .filter(s => fenced(s) && (taggablePrim(s.info) || foreignOpaque(program, s.info).isDefined))
      .map(_.id).toSet
    // …and the ones this MECHANISM cannot reach, before any early return: a spec that names one is
    // the shape that used to look exactly like a typo (see [[reportUnreachable]]).
    reportUnreachable(program, named.filter(fenced))
    if hints.isEmpty then return program
    seeds = propagate(program, hints) // grow the seed set along pure-move flows
    refuseOverlap(program)
    if seeds.isEmpty then return program

    var next = program.symbols.all.map(_.id.raw).maxOption.getOrElse(-1) + 1
    def mint(name: String, full: String, flags: Flags, owner: SymId = SymId.None, info: TypeRepr = TypeRepr.NoType): SymId =
      val id = SymId(next); next += 1
      minted += Symbol(id, name, full, flags, owner, info)
      id
    // The object is a TOP-LEVEL unit whose `fullName` is the spec's FQN, and that FQN is the whole
    // of "where is it defined": the emitter derives the `package` clause from the name's prefix and
    // the file from the package. An FQN with no `.` is the default package, which is what the
    // Int-only predecessor did with its bare `typeName`.
    objSym    = mint(spec.objectName, spec.fqn, Flags(isModule = true))
    // owner = the object, so the emitter renders references as the path-dependent `Name.T`
    // (an object's type member; a `Name#T` projection would need `Name` to be a type).
    opaqueSym = mint("T", spec.typeFqn, Flags(isOpaque = true), objSym)
    opaqueRef = TypeRepr.TypeRef(TypeRepr.NoType, opaqueSym)
    val vWrap   = mint("v", "v", Flags(isParam = true), info = primRef)
    val vUnwrap = mint("v", "v", Flags(isParam = true), info = opaqueRef)
    applySym  = mint("apply",  s"${spec.fqn}.apply",  Flags(), objSym, TypeRepr.MethodType(List("v" -> primRef), opaqueRef))
    unwrapSym = mint("unwrap", s"${spec.fqn}.unwrap", Flags(), objSym, TypeRepr.MethodType(List("v" -> opaqueRef), primRef))

    val o = Origin.synthetic
    val typeDef  = Tree.TypeDef(opaqueSym, TypeTree(primRef, o), o)
    val applyDef = Tree.DefDef(applySym, List(List(Tree.ValDef(vWrap, TypeTree(primRef, o), None, o))),
      TypeTree(opaqueRef, o), Some(Tree.Ident(vWrap, primRef, o)), o)
    val unwrapDef = Tree.DefDef(unwrapSym, List(List(Tree.ValDef(vUnwrap, TypeTree(opaqueRef, o), None, o))),
      TypeTree(primRef, o), Some(Tree.Ident(vUnwrap, opaqueRef, o)), o)
    val synthUnit = Tree.ClassDef(objSym, Nil, None, List(typeDef, applyDef, unwrapDef), o)

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
      * consumer reads whichever it reads. */
    def methodType(id: SymId, mt: TypeRepr.MethodType): TypeRepr.MethodType =
      val slots = seedParamSlots.getOrElse(id, Set.empty)
      TypeRepr.MethodType(
        mt.params.zipWithIndex.map((nt, i) => if slots(i) then nt._1 -> opaqueRef else nt),
        if seeds(id) && isPrim(mt.result) then opaqueRef else mt.result,
        mt.isImplicit)

    // retype seed symbol infos primitive → opaque (value seeds) / return → opaque (method seeds)
    // / the parameter slots of ANY method — seed or not — one of whose parameters is a seed.
    val retyped = program.symbols.all.map { s =>
      s.info match
        case r if seeds(s.id) && isPrim(r)                   => s.copy(info = opaqueRef)
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
    val walked = program.units.map(u => StandardTraversal.mapClassDef(this, u))
    val units  = if mintsHere(program, hints) then walked :+ synthUnit else walked
    program.rebuilt(units, symbols)

  /** Does THIS module own the declarations the spec named? Read through [[RunScope.emits]], which is
    * the run's own emitted-unit set and the same predicate every other module-ownership question in
    * the engine now goes through.
    *
    * `true` whenever there is no run scope (a base port, a single-module port, a spec, `DebugEmit`),
    * by [[RunScope.whole]] — so this is the identity everywhere the pre-fix code was already right,
    * and there is no second code path for a port to be silently on. */
  private def mintsHere(p: Program, hints: Set[SymId]): Boolean =
    hints.exists(id => runScope.emits(unitOf(p, id)))

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
          if spec.extraHints(s.fullName) then s"OpaqueSpec(${spec.fqn}).extraHints"
          else s"OpaqueSpec(${spec.fqn}).hints"
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
    if seeds(v.symbol) then v.copy(tpt = TypeTree(opaqueRef, v.origin), rhs = v.rhs.map(wrap))
    else v.copy(rhs = v.rhs.map(unwrapIfOpaque))

  override def transformDefDef(d: Tree.DefDef)(using Program): Tree.DefDef =
    if seeds(d.symbol) then d.copy(returnTpt = TypeTree(opaqueRef, d.origin), rhs = d.rhs.map(wrapReturns))
    else d.copy(rhs = d.rhs.map(unwrapReturns))

  // retype seed REFERENCES so boundary detection reads a consistent `tpe` (the populator left
  // a seed reference's node `tpe` as `Int`; only the declaration was retyped above).
  override def transformIdent(t: Tree.Ident)(using Program): Term =
    if seeds(t.sym) then t.copy(tpe = opaqueRef) else t
  override def transformSelect(t: Tree.Select)(using Program): Term =
    if seeds(t.sym) then t.copy(tpe = opaqueRef) else t

  override def transformApply(t0: Tree.Apply)(using Program): Term =
    val t = if isSeedMethod(t0.method) then t0.copy(tpe = opaqueRef) else t0 // seed getter returns opaque
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
      if carriesOpaque(a.lhs) then a.copy(rhs = wrap(a.rhs)) else a.copy(rhs = unwrapIfOpaque(a.rhs))
    case x: Tree.ArrayAccess => x.copy(index = unwrapIfOpaque(x.index))
    case other => other

  private def isSeedMethod(m: SymId)(using p: Program): Boolean =
    seeds(m) && p.symbolOf(m).exists(_.info.isInstanceOf[TypeRepr.MethodType])

  private def coerceArgs(t: Tree.Apply)(using Program): Term =
    summon[Program].definitionOf(t.method) match
      case Some(d: Tree.DefDef) =>
        val params = d.paramss.flatten
        if params.length != t.args.length then t
        else t.copy(args = t.args.zip(params).map { (arg, p) =>
          if seeds(p.symbol) then wrap(arg) else unwrapIfOpaque(arg)
        })
      case _ => t

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
    case Tree.Match(_, cases, _, _)   => cases.exists(c => carriesOpaque(c.body))
    case Tree.Ident(s, _, _)          => seeds(s) || isOpaque(e)
    case Tree.Select(_, s, _, _)      => seeds(s) || isOpaque(e)
    case Tree.Apply(_, _, m, _, _)    => isSeedMethod(m) || isOpaque(e)
    case other                        => isOpaque(other)

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

  private def wrapCall(e: Term): Term =
    if isPrim(e.tpe) then Tree.Apply(Tree.Ident(objSym, TypeRepr.NoType, e.origin), List(e), applySym, opaqueRef, e.origin)
    else e

  private def unwrapCall(e: Term): Term =
    Tree.Apply(Tree.Select(Tree.Ident(objSym, TypeRepr.NoType, e.origin), unwrapSym, TypeRepr.NoType, e.origin),
      List(e), unwrapSym, primRef, e.origin)

  /** COERCE OUT of it — a no-op unless the value really is a seed's, which is what makes this safe
    * to ask at every boundary rather than only at the ones a node type made visible. */
  private def unwrapIfOpaque(e: Term)(using Program): Term =
    if !carriesOpaque(e) then e
    else coerce(e, primRef, l => if carriesOpaque(l) then unwrapCall(l) else l)

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
  private def isPrim(t: TypeRepr): Boolean = headSym(t).contains(primSym)
  private def taggablePrim(info: TypeRepr): Boolean = info match
    case r if isPrim(r)                 => true
    case TypeRepr.MethodType(_, ret, _) => isPrim(ret)
    case _                              => false

  private def headSym(t: TypeRepr): Option[SymId] = t match
    case TypeRepr.TypeRef(_, s)      => Some(s)
    case TypeRepr.AppliedType(tc, _) => headSym(tc)
    case _                           => None
