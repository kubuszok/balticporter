package balticporter.transform

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
  */
final class PrimitiveToOpaqueTransform(val spec: OpaqueSpec) extends Phase:
  def name = s"primitive->opaque:${spec.fqn}"

  private var objSym, opaqueSym, applySym, unwrapSym, primSym: SymId = SymId.None
  private var seeds: Set[SymId]   = Set.empty
  private var opaqueRef: TypeRepr = TypeRepr.NoType
  private var primRef: TypeRepr   = TypeRepr.NoType
  private val minted = collection.mutable.ListBuffer[Symbol]()

  /** the detected old→new type mapping: every primitive symbol retyped to the opaque type. A
    * reusable trace (also what a semantic-diff of this phase would report). */
  def typeMapping: Map[SymId, TypeRepr] = seeds.iterator.map(_ -> opaqueRef).toMap

  override def run(program: Program): Program =
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
    val hints = program.symbols.all
      .filter(s => (spec.hints(s) || spec.extraHints(s.fullName)) && fenced(s) &&
        (taggablePrim(s.info) || foreignOpaque(program, s.info).isDefined))
      .map(_.id).toSet
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

    // retype seed symbol infos primitive → opaque (value seeds) / return → opaque (method seeds).
    val retyped = program.symbols.all.map { s =>
      if !seeds(s.id) then s
      else s.info match
        case r if isPrim(r) => s.copy(info = opaqueRef)
        case TypeRepr.MethodType(ps, ret, im) if isPrim(ret) => s.copy(info = TypeRepr.MethodType(ps, opaqueRef, im))
        case _ => s
    }
    val symbols = SymbolTable(retyped ++ minted)
    given Program = new Program(program.units, symbols, program.xref)

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

    val units = program.units.map(u => StandardTraversal.mapClassDef(this, u)) :+ synthUnit
    new Program(units, symbols, program.xref)

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
  override def transformValDef(v: Tree.ValDef)(using Program): Tree.ValDef =
    if !seeds(v.symbol) then v
    else v.copy(tpt = TypeTree(opaqueRef, v.origin), rhs = v.rhs.map(wrap))

  override def transformDefDef(d: Tree.DefDef)(using Program): Tree.DefDef =
    if !seeds(d.symbol) then d
    else d.copy(returnTpt = TypeTree(opaqueRef, d.origin), rhs = d.rhs.map(wrapReturns))

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
    case a: Tree.Assign =>
      if isOpaque(a.lhs) && isPrim(a.rhs.tpe) then a.copy(rhs = wrap(a.rhs))
      else if isPrim(a.lhs.tpe) && isOpaque(a.rhs) then a.copy(rhs = unwrapCall(a.rhs))
      else a
    case x: Tree.ArrayAccess if isOpaque(x.index) => x.copy(index = unwrapCall(x.index))
    case other => other

  private def isSeedMethod(m: SymId)(using p: Program): Boolean =
    seeds(m) && p.symbolOf(m).exists(_.info.isInstanceOf[TypeRepr.MethodType])

  private def coerceArgs(t: Tree.Apply)(using Program): Term =
    summon[Program].definitionOf(t.method) match
      case Some(d: Tree.DefDef) =>
        val params = d.paramss.flatten
        if params.length != t.args.length then t
        else t.copy(args = t.args.zip(params).map { (arg, p) =>
          val pOpaque = seeds(p.symbol)
          if pOpaque && isPrim(arg.tpe) then wrap(arg)
          else if !pOpaque && isOpaque(arg) then unwrapCall(arg)
          else arg
        })
      case _ => t

  private def wrap(e: Term): Term =
    if isPrim(e.tpe) then Tree.Apply(Tree.Ident(objSym, TypeRepr.NoType, e.origin), List(e), applySym, opaqueRef, e.origin)
    else e

  private def unwrapCall(e: Term): Term =
    Tree.Apply(Tree.Select(Tree.Ident(objSym, TypeRepr.NoType, e.origin), unwrapSym, TypeRepr.NoType, e.origin),
      List(e), unwrapSym, primRef, e.origin)

  private def unwrapIfOpaque(e: Term): Term = if isOpaque(e) then unwrapCall(e) else e

  private def wrapReturns(body: Term): Term = body match
    case Tree.Return(Some(e), tp, o) if isPrim(e.tpe) => Tree.Return(Some(wrap(e)), tp, o)
    case b: Tree.Block  => b.copy(stats = b.stats.map { case s: Term => wrapReturns(s); case s => s }, expr = wrapReturns(b.expr))
    case i: Tree.If     => i.copy(thenp = wrapReturns(i.thenp), elsep = wrapReturns(i.elsep))
    case e if isPrim(e.tpe) => wrap(e)
    case other          => other

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
