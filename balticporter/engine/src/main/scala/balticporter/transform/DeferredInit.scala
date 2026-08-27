package balticporter.transform

import balticporter.tir.*

/** EAGER → LAZY, per site and never by default: a static whose class initialiser reads the holder
  * becomes a `def` over a cache, taking the context clause.
  *
  * ==Why this is a per-site opt-in and not a mode==
  * A class initialiser has no signature, so it cannot be threaded, and it runs before anything could
  * have constructed a context. The only faithful-ish answer is to move the initialisation to the
  * first READ of the field — and that is a SEMANTIC CHANGE: Java runs a class initialiser at the
  * first ACTIVE USE of the class (any static read, any instantiation), while this runs at the first
  * read of THIS field. The two coincide when nothing else in the class is touched first, and the
  * mechanism cannot know whether that holds. So it is asked for per site, recorded as a
  * `Decision.Kind.DeferredInit` with a porter note beside the code, and counted as a
  * `deferred-init` seam. The corpus demand for it is exactly ONE site in nine libraries, which is
  * itself the argument for per-site policy over a mode.
  *
  * ==The emitted shape==
  * {{{
  * // was:  static X f;  static { f = <expr reading the holder>; }
  * private var f$set: Boolean = false
  * private var f$value: X     = null
  * def f(using T): X = { if (!f$set) { f$set = true; f$value = <expr, now threaded> }; f$value }
  * }}}
  *
  * A pair of cache fields rather than a Scala `lazy val`, for two reasons that are not style: a
  * `lazy val` initialiser has no parameter list, so there would be nowhere for the context to arrive
  * — that is the whole problem being solved — and the `set` flag makes the rewrite work for a
  * PRIMITIVE-typed static, where a null test would fire on a legitimately-zero value forever.
  *
  * What it does NOT reproduce is the JVM's class-initialisation LOCK: `<clinit>` runs once even
  * under concurrent first use, and this does not. A static a port defers is one whose first read is
  * on a known thread; the decision row says so, and a port that needs the guarantee should inject a
  * hand-written holder instead.
  *
  * ==Reads are UNCHANGED at the call site==
  * A Scala parameterless `def` is read exactly as a field was, so every `T.f` in the program stays
  * the text it was and the context arrives from the `using` in scope. That is why the field's symbol
  * is seeded into the closure as a METHOD: its readers are what must now be able to supply it.
  */
final class DeferredInit(
    program: Program,
    holder: ContextHolder,
    mint: GlobalsToImplicitsTransform.Minter,
    ctxRef: TypeRepr,
    val deferrals: List[ContextNeed.Deferral],
) extends Phase:

  def name = "globals->implicits/deferred-init"

  private val byField  = deferrals.map(d => d.field -> d).toMap
  /** only the deferrals that came OUT OF a class initialiser have one to strip: a static field
    * carrying its own initialiser is replaced whole by [[deferField]], and its `clinit` is
    * `SymId.None` (`ENGINE-LIMITS.md` CT6). */
  private val byClinit = deferrals.filter(_.clinit != SymId.None).groupBy(_.clinit)
  private val o        = Origin.synthetic

  // The two types the cache is written in. MINTED rather than looked up: searching the symbol table
  // for `fullName == "scala.Boolean"` is the §4.56 string test the transform-package lint forbids,
  // and there is nothing to gain by winning it — an external symbol is rendered from its `fullName`,
  // so a second one interned under the same name emits the same text and owns nothing.
  private lazy val boolSym = mint.tpe("Boolean", "scala.Boolean")
  private lazy val unitSym = mint.tpe("Unit", "scala.Unit")
  private lazy val boolT   = TypeRepr.TypeRef(TypeRepr.NoPrefix, boolSym)
  private lazy val unitT   = TypeRepr.TypeRef(TypeRepr.NoPrefix, unitSym)
  /** the emitter renders a call on a symbol whose `fullName` starts with `scala.<op>#` as an
    * operator — the convention the frontend's own operator lowering uses, read back rather than
    * re-invented. */
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
      // the field's OWN symbol carries the `def`, so every read in the program keeps naming it and
      // no call site changes; only its `paramss` says the context now arrives here.
      Tree.DefDef(v.symbol, List(List(mint.usingParam(v.symbol, holder.context.fqn, ctxRef, at))),
                  TypeTree(v.tpt.tpe, at), Some(body), at, leading = v.leading),
    )

  /** the class initialiser keeps everything the deferral did not move; an initialiser left with no
    * statements at all is dropped rather than emitted as an empty block. */
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

  /** the field flags a deferral needs on the emitted `def`: the static stays a static, so it lands
    * in the companion where Java put it. Nothing here changes them — the symbol is reused verbatim,
    * which is what keeps every read in the program naming the same thing. */
  def unchangedSymbols: Set[SymId] = byField.keySet
