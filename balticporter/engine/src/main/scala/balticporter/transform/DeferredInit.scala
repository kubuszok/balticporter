package balticporter.transform

import balticporter.tir.*

/** Turns a static whose class initialiser reads the threaded holder into a `def` over a `$set`/
  * `$value` cache pair, taking the context clause — per site, never by default, since deferring
  * init to first read changes java's first-ACTIVE-USE trigger (recorded as
  * `Decision.Kind.DeferredInit`, counted `deferred-init`). Does NOT reproduce the JVM's class-init
  * lock; reads are unchanged since the field's symbol is reused as a parameterless `def`. */
final class DeferredInit(
    program: Program,
    holder: ContextHolder,
    mint: GlobalsToImplicitsTransform.Minter,
    ctxRef: TypeRepr,
    val deferrals: List[ContextNeed.Deferral],
) extends Phase:

  def name = "globals->implicits/deferred-init"

  private val byField  = deferrals.map(d => d.field -> d).toMap
  /** Only deferrals out of a class initialiser have one to strip — a field carrying its own
    * initialiser is replaced whole by `deferField` and its `clinit` is `SymId.None` (ENGINE-LIMITS
    * CT6). */
  private val byClinit = deferrals.filter(_.clinit != SymId.None).groupBy(_.clinit)
  private val o        = Origin.synthetic

  // minted rather than looked up: a name string test is the §4.56 hazard the transform lint forbids
  private lazy val boolSym = mint.tpe("Boolean", "scala.Boolean")
  private lazy val unitSym = mint.tpe("Unit", "scala.Unit")
  private lazy val boolT   = TypeRepr.TypeRef(TypeRepr.NoPrefix, boolSym)
  private lazy val unitT   = TypeRepr.TypeRef(TypeRepr.NoPrefix, unitSym)
  /** `scala.<op>#unary_!` is the convention the frontend's own operator lowering uses for the emitter
    * to render a call as an operator. */
  private lazy val notSym  = mint.member("unary_!", "scala.<op>#unary_!", SymId.None, boolT, Flags())

  def apply(u: Tree.ClassDef)(using Program): Tree.ClassDef =
    if deferrals.isEmpty then u else StandardTraversal.mapClassDef(this, u)

  override def transformClassDef(t: Tree.ClassDef)(using Program): Tree.ClassDef =
    if deferrals.isEmpty then t else t.copy(body = t.body.flatMap(rewrite))

  private def rewrite(s: Statement)(using Program): List[Statement] = s match
    case v: Tree.ValDef if byField.contains(v.symbol) => deferField(v, byField(v.symbol))
    case d: Tree.DefDef if byClinit.contains(d.symbol) => stripClinit(d, byClinit(d.symbol))
    case other => List(other)

  /** the field becomes a cache pair plus a `def` taking the clause. */
  private def deferField(v: Tree.ValDef, d: ContextNeed.Deferral)(using Program): List[Statement] =
    val sym    = summon[Program].symbolOf(v.symbol)
    val nm     = sym.map(_.name).getOrElse("f")
    val owner  = sym.map(_.owner).getOrElse(SymId.None)
    val full   = sym.map(_.fullName).getOrElse(nm)
    val flags  = Flags(isStatic = true, isMutable = true, isPrivate = true)
    val setSym = mint.member(s"$nm$$set", s"$full$$set", owner, boolT, flags)
    val valSym = mint.member(s"$nm$$value", s"$full$$value", owner, v.tpt.tpe, flags)

    val at    = v.origin
    val cond  = Tree.Apply(Tree.Select(Tree.Ident(setSym, boolT, at), notSym, TypeRepr.NoType, at),
                           Nil, notSym, boolT, at)
    val thenp = Tree.Block(
      List(Tree.Assign(Tree.Ident(setSym, boolT, at), Tree.Literal(Constant.BoolC(true), boolT, at), unitT, at),
           Tree.Assign(Tree.Ident(valSym, v.tpt.tpe, at), d.rhs, unitT, at)),
      Tree.Literal(Constant.UnitC, unitT, at), unitT, at)
    val body  = Tree.Block(List(Tree.If(cond, thenp, Tree.Literal(Constant.UnitC, unitT, at), unitT, at)),
                           Tree.Ident(valSym, v.tpt.tpe, at), v.tpt.tpe, at)

    List(
      Tree.ValDef(setSym, TypeTree(boolT, at), Some(Tree.Literal(Constant.BoolC(false), boolT, at)), at),
      Tree.ValDef(valSym, TypeTree(v.tpt.tpe, at), scala.None, at),
      // the field's OWN symbol carries the `def`, so no call site changes — only its `paramss` moves
      Tree.DefDef(v.symbol, List(List(mint.usingParam(v.symbol, holder.context.fqn, ctxRef, at))),
                  TypeTree(v.tpt.tpe, at), Some(body), at, leading = v.leading),
    )

  /** The class initialiser keeps everything the deferral did not move; dropped rather than emitted
    * empty if nothing remains. */
  private def stripClinit(d: Tree.DefDef, ds: List[ContextNeed.Deferral])(using Program): List[Statement] =
    val moved = ds.map(_.field).toSet
    def isMoved(t: Statement): Boolean = t match
      case x: Term => Tree.uncomment(x) match
        case Tree.Assign(lhs, _, _, _, _) => lhsSym(lhs).exists(moved.contains)
        case _                         => false
      case _ => false
    d.rhs.map(Tree.uncomment) match
      case Some(b: Tree.Block) =>
        val stats = (b.stats :+ b.expr).filterNot(isMoved)
        val (init, expr) = stats.lastOption match
          case Some(t: Term) => (stats.dropRight(1), t)
          case _             => (stats, Tree.Literal(Constant.UnitC, unitT, d.origin))
        if init.isEmpty && isUnitLiteral(expr) then Nil
        else List(d.copy(rhs = Some(b.copy(stats = init, expr = expr))))
      case Some(t) if isMoved(t) => Nil
      case _                     => List(d)

  private def isUnitLiteral(t: Term): Boolean = Tree.uncomment(t) match
    case Tree.Literal(Constant.UnitC, _, _) => true
    case _                                  => false

  private def lhsSym(t: Term): Option[SymId] = Tree.uncomment(t) match
    case Tree.Ident(s, _, _)     => Some(s)
    case Tree.Select(_, s, _, _) => Some(s)
    case _                       => scala.None

  /** The static flags are reused verbatim (the symbol stays a static, landing in the companion where
    * java put it), which is what keeps every read in the program naming the same thing. */
  def unchangedSymbols: Set[SymId] = byField.keySet
