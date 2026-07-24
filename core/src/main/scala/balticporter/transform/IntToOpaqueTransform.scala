package balticporter.transform

import balticporter.tir.*

/** Retype a semantically-tagged `Int` to an `opaque type` (+companion) everywhere it flows,
  * wrapping its construction sites and unwrapping its consumption sites. The sge/ssg case:
  * GL layer ids, key/button codes — an `int` that is really a distinct domain value, given a
  * real type so the compiler stops it being mixed with plain arithmetic ints.
  *
  * This is symbol-identity- and flow-driven, not textual:
  *
  *   1. HINTS — a small set of tagged `int` symbols (the `hint` predicate, or `extraHints`
  *      fully-qualified names an agent supplies after a failed compile — see below). These
  *      are the [[SymTag]] seeds.
  *   2. DETECT (propagation) — the seed set is GROWN from the hints by tracing the
  *      whole-program reference graph: any `int` symbol connected to a seed by a
  *      *pure-move* flow (assignment `a = b`, `val x = ref`, `return ref`, passing `ref`
  *      to a parameter) is itself a seed. The flow edges are read from the TIR, whose every
  *      reference was resolved to a `SymId` by Spoon — so this is "use the Java compiler's
  *      resolution to map old references to new". Arithmetic (`layer + 1`) is NOT a
  *      pure-move — it yields a plain int, correctly breaking the chain (and getting an
  *      unwrap). The detected mapping `int symbol → opaque` is exposed as [[typeMapping]],
  *      a reusable type-mapping trace.
  *   3. RETYPE + COERCE — each seed symbol's `info` becomes the opaque type; its references
  *      retype with it (found by SymId, via the xref — not by node `tpe`, which the
  *      populator left as `Int`); and coercions are inserted at the seed/plain-int boundary
  *      (`wrap` a plain int flowing into a seed, `unwrap` a seed consumed as a plain int).
  *
  * Agent-in-the-loop: when the emitted Scala fails to compile, an agent reads the errors and
  * can pass the fully-qualified name of a missed `int` (one that should have been opaque but
  * wasn't reachable from the hints) as an `extraHints` entry; the next run re-propagates with
  * it. That closes the loop the design calls for.
  */
final class IntToOpaqueTransform(
    typeName: String,
    hint: Symbol => Boolean,
    extraHints: Set[String] = Set.empty,
) extends Phase:
  def name = s"int->opaque:$typeName"

  private var objSym, opaqueSym, applySym, unwrapSym, intSym: SymId = SymId.None
  private var seeds: Set[SymId]   = Set.empty
  private var opaqueRef: TypeRepr = TypeRepr.NoType
  private var intRef: TypeRepr    = TypeRepr.NoType
  private val minted = collection.mutable.ListBuffer[Symbol]()

  /** the detected old→new type mapping: every `int` symbol retyped to the opaque type. A
    * reusable trace (also what a semantic-diff of this phase would report). */
  def typeMapping: Map[SymId, TypeRepr] = seeds.iterator.map(_ -> opaqueRef).toMap

  override def run(program: Program): Program =
    intSym = program.symbols.all.find(_.fullName == "scala.Int").map(_.id).getOrElse(SymId.None)
    if intSym == SymId.None then return program
    intRef = TypeRepr.TypeRef(TypeRepr.NoType, intSym)

    val hints = program.symbols.all
      .filter(s => (hint(s) || extraHints(s.fullName)) && taggableInt(s.info)).map(_.id).toSet
    if hints.isEmpty then return program
    seeds = propagate(program, hints) // grow the seed set along pure-move flows
    if seeds.isEmpty then return program

    var next = program.symbols.all.map(_.id.raw).maxOption.getOrElse(-1) + 1
    def mint(name: String, full: String, flags: Flags, owner: SymId = SymId.None, info: TypeRepr = TypeRepr.NoType): SymId =
      val id = SymId(next); next += 1
      minted += Symbol(id, name, full, flags, owner, info)
      id
    objSym    = mint(typeName, typeName, Flags(isModule = true))
    // owner = the object, so the emitter renders references as the path-dependent `Name.T`
    // (an object's type member; a `Name#T` projection would need `Name` to be a type).
    opaqueSym = mint("T", s"$typeName.T", Flags(isOpaque = true), objSym)
    opaqueRef = TypeRepr.TypeRef(TypeRepr.NoType, opaqueSym)
    val vWrap   = mint("v", "v", Flags(isParam = true), info = intRef)
    val vUnwrap = mint("v", "v", Flags(isParam = true), info = opaqueRef)
    applySym  = mint("apply",  s"$typeName.apply",  Flags(), objSym, TypeRepr.MethodType(List("v" -> intRef), opaqueRef))
    unwrapSym = mint("unwrap", s"$typeName.unwrap", Flags(), objSym, TypeRepr.MethodType(List("v" -> opaqueRef), intRef))

    val o = Origin.synthetic
    val typeDef  = Tree.TypeDef(opaqueSym, TypeTree(intRef, o), o)
    val applyDef = Tree.DefDef(applySym, List(List(Tree.ValDef(vWrap, TypeTree(intRef, o), None, o))),
      TypeTree(opaqueRef, o), Some(Tree.Ident(vWrap, intRef, o)), o)
    val unwrapDef = Tree.DefDef(unwrapSym, List(List(Tree.ValDef(vUnwrap, TypeTree(opaqueRef, o), None, o))),
      TypeTree(intRef, o), Some(Tree.Ident(vUnwrap, opaqueRef, o)), o)
    val synthUnit = Tree.ClassDef(objSym, Nil, None, List(typeDef, applyDef, unwrapDef), o)

    // retype seed symbol infos Int → opaque (value seeds) / return Int → opaque (method seeds).
    val retyped = program.symbols.all.map { s =>
      if !seeds(s.id) then s
      else s.info match
        case r if isInt(r) => s.copy(info = opaqueRef)
        case TypeRepr.MethodType(ps, ret, im) if isInt(ret) => s.copy(info = TypeRepr.MethodType(ps, opaqueRef, im))
        case _ => s
    }
    val symbols = SymbolTable(retyped ++ minted)
    given Program = new Program(program.units, symbols, program.xref)
    val units = program.units.map(u => StandardTraversal.mapClassDef(this, u)) :+ synthUnit
    new Program(units, symbols, program.xref)

  // -------------------------------------------------------------------------
  // Seed detection: union-find over Int symbols connected by pure-move flows.
  // -------------------------------------------------------------------------
  private def propagate(p: Program, hints: Set[SymId]): Set[SymId] =
    val parent = collection.mutable.Map[SymId, SymId]()
    def find(x: SymId): SymId =
      var r = x
      while parent.getOrElse(r, r) != r do r = parent(r)
      parent(x) = r; r
    def union(a: SymId, b: SymId): Unit = parent(find(a)) = find(b)
    def isIntSym(id: SymId): Boolean = p.symbolOf(id).exists(s => taggableInt(s.info))

    val edges = collectFlows(p).filter((a, b) => isIntSym(a) && isIntSym(b))
    edges.foreach((a, b) => union(a, b))
    val hintRoots = hints.filter(isIntSym).map(find)
    val universe  = (edges.flatMap((a, b) => List(a, b)) ++ hints).toSet.filter(isIntSym)
    universe.filter(s => hintRoots.contains(find(s)))

  /** Pure-move flow edges over the whole program: `(a, b)` means an int value moves between
    * `a` and `b` without arithmetic, so they must share a type. Read from the Spoon-resolved
    * TIR (every reference already carries its resolved `SymId`). */
  private def collectFlows(p: Program): List[(SymId, SymId)] =
    val edges = collection.mutable.ListBuffer[(SymId, SymId)]()
    def refSym(t: Term): Option[SymId] = t match
      case Tree.Ident(s, _, _)          => Some(s)
      case Tree.Select(_, s, _, _)      => Some(s)
      case Tree.Apply(_, Nil, m, _, _)  => Some(m) // nullary getter call: `x = obj.get()`
      case _                            => None
    def walkTerm(t: Term, encl: SymId): Unit = t match
      case Tree.Block(stats, e, _, _) => stats.foreach(walkStat(_, encl)); walkTerm(e, encl)
      case Tree.Assign(l, r, _, _) =>
        for a <- refSym(l); b <- refSym(r) do edges += ((a, b)); walkTerm(l, encl); walkTerm(r, encl)
      case Tree.Return(Some(e), _, _) =>
        if encl != SymId.None then refSym(e).foreach(s => edges += ((encl, s)))
        walkTerm(e, encl)
      case Tree.Apply(fun, args, m, _, _) =>
        p.definitionOf(m) match
          case Some(d: Tree.DefDef) =>
            args.zip(d.paramss.flatten).foreach((arg, pd) => refSym(arg).foreach(s => edges += ((s, pd.symbol))))
          case _ => ()
        walkTerm(fun, encl); args.foreach(walkTerm(_, encl))
      case Tree.If(c, a, b, _, _) => walkTerm(c, encl); walkTerm(a, encl); walkTerm(b, encl)
      case Tree.While(c, b, _, _) => walkTerm(c, encl); walkTerm(b, encl)
      case Tree.Select(q, _, _, _) => walkTerm(q, encl)
      case _ => ()
    def walkStat(s: Statement, encl: SymId): Unit = s match
      case c: Tree.ClassDef => c.body.foreach(walkStat(_, c.symbol))
      case d: Tree.DefDef   => d.rhs.foreach(r => { tailRefs(r).foreach(s => edges += ((d.symbol, s))); walkTerm(r, d.symbol) })
      case v: Tree.ValDef   => v.rhs.foreach(r => { refSym(r).foreach(s => edges += ((v.symbol, s))); walkTerm(r, encl) })
      case t: Term          => walkTerm(t, encl)
      case _                => ()
    /** references returnable from a method body's tail (a bare-expression return or the last
      * expr of a block), which flow to the method's own (return) symbol. */
    def tailRefs(t: Term): List[SymId] = t match
      case Tree.Block(_, e, _, _) => tailRefs(e)
      case Tree.If(_, a, b, _, _) => tailRefs(a) ++ tailRefs(b)
      case other                  => refSym(other).toList
    p.units.foreach(walkStat(_, SymId.None))
    edges.toList

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
      if isOpaque(a.lhs) && isInt(a.rhs.tpe) then a.copy(rhs = wrap(a.rhs))
      else if isInt(a.lhs.tpe) && isOpaque(a.rhs) then a.copy(rhs = unwrapCall(a.rhs))
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
          if pOpaque && isInt(arg.tpe) then wrap(arg)
          else if !pOpaque && isOpaque(arg) then unwrapCall(arg)
          else arg
        })
      case _ => t

  private def wrap(e: Term): Term =
    if isInt(e.tpe) then Tree.Apply(Tree.Ident(objSym, TypeRepr.NoType, e.origin), List(e), applySym, opaqueRef, e.origin)
    else e

  private def unwrapCall(e: Term): Term =
    Tree.Apply(Tree.Select(Tree.Ident(objSym, TypeRepr.NoType, e.origin), unwrapSym, TypeRepr.NoType, e.origin),
      List(e), unwrapSym, intRef, e.origin)

  private def unwrapIfOpaque(e: Term): Term = if isOpaque(e) then unwrapCall(e) else e

  private def wrapReturns(body: Term): Term = body match
    case Tree.Return(Some(e), tp, o) if isInt(e.tpe) => Tree.Return(Some(wrap(e)), tp, o)
    case b: Tree.Block  => b.copy(stats = b.stats.map { case s: Term => wrapReturns(s); case s => s }, expr = wrapReturns(b.expr))
    case i: Tree.If     => i.copy(thenp = wrapReturns(i.thenp), elsep = wrapReturns(i.elsep))
    case e if isInt(e.tpe) => wrap(e)
    case other          => other

  private def isOpaque(t: Term): Boolean  = headSym(t.tpe).contains(opaqueSym)
  private def isInt(t: TypeRepr): Boolean = headSym(t).contains(intSym)
  private def taggableInt(info: TypeRepr): Boolean = info match
    case r if isInt(r)                  => true
    case TypeRepr.MethodType(_, ret, _) => isInt(ret)
    case _                              => false

  private def headSym(t: TypeRepr): Option[SymId] = t match
    case TypeRepr.TypeRef(_, s)      => Some(s)
    case TypeRepr.AppliedType(tc, _) => headSym(tc)
    case _                           => None
