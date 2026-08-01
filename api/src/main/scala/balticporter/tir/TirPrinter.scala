package balticporter.tir

/** A readable, STABLE rendering of the TIR — the thing you need to compare two phases' output.
  *
  * Case-class `toString` is not a substitute and was the only option before this existed: a
  * `Tree.ClassDef` prints its whole `TypeRepr` graph inline, renders every symbol as an opaque
  * `SymId` integer, wraps at no column and repeats an applied type in full at every occurrence.
  * Diffing two of those tells you nothing.
  *
  * Two properties this printer has that `toString` does not:
  *
  *   - **Symbols read as names.** `TypeRef(NoPrefix, SymId(4132))` is unreadable and, worse, the
  *     integer is INTERNING-ORDER dependent (`Tir.scala` — `SymId` is an opaque `Int` handed out
  *     as symbols are discovered), so it changes when an unrelated file is added. [[Style.debug]]
  *     prints `fullName#id` because the id is what a `SymId`-keyed log line carries;
  *     [[Style.canonical]] prints the name ALONE, which is what a persisted artifact or a
  *     run-over-run digest must contain.
  *   - **Types are printed once, in surface syntax.** `AppliedType(TypeRef(_, LIST), TypeBounds(
  *     NoType, TypeRef(_, W)))` reads `List[? <: Widget]`.
  *
  * The rendering is total over `Tree` and `TypeRepr` — a node kind added to the IR without a case
  * here is a compiler exhaustivity warning, not a silently unprinted subtree. That matters for the
  * same reason CLAUDE.md §3 insists on `StandardTraversal`: two of this project's four silent
  * correctness defects were traversals that stopped one node short.
  *
  * `ParamRef` deliberately does NOT recurse into its binder. Expanding it would re-print the
  * whole enclosing signature at every parameter occurrence — unreadable and quadratic — and does
  * not terminate at all for a binder that transitively contains the `ParamRef`. It renders as the
  * binder-relative parameter NAME, which is also what makes the canonical form independent of
  * binder identity.
  */
object TirPrinter:

  /** `showIds`: append `#<SymId>` to every symbol. Interning-order dependent — debugging only,
    * never in a persisted artifact. `showOrigins`: append the Java source location; line numbers
    * move when upstream whitespace does, so this too is off in the canonical form.
    * `showTrivia`: render the original Java comments each node carries — see [[Style.canonical]]. */
  final case class Style(showIds: Boolean = true, showOrigins: Boolean = false, showTypes: Boolean = true,
                         showTrivia: Boolean = true)
  object Style:
    /** what you read on a terminal while diagnosing one phase. */
    val debug: Style = Style(showIds = true, showOrigins = true)
    /** what you DIFF: no ids, no line numbers, no clock — and no trivia.
      *
      * Trivia is elided here for the same reason origins are. A dump exists to answer "what did
      * this phase do to the tree", and libGDX's `AssetManager` carries 400 lines of Javadoc that
      * would bury the twelve nodes a phase actually moved. The comments are content, not
      * structure, and no phase reads them.
      *
      * Which is exactly why [[digest]] does NOT use this style — see there. */
    val canonical: Style = Style(showIds = false, showOrigins = false, showTrivia = false)
    /** canonical PLUS trivia: everything that reaches the emitted file and nothing that does not.
      * The identity a content digest must be taken over. */
    val identity: Style = canonical.copy(showTrivia = true)

  // ---------------------------------------------------------------------------
  // entry points
  // ---------------------------------------------------------------------------

  def render(t: Tree, style: Style = Style.debug)(using Program): String =
    val sb = new StringBuilder
    tree(sb, t, 0, style)
    sb.result()

  /** the deterministic form: names, no ids, no origins. The unit of a run-over-run semantic diff
    * (DESIGN.md §2.6). */
  def canonical(t: Tree)(using Program): String = render(t, Style.canonical)

  /** sha-256 of the unit's IDENTITY form, hex. Stable across runs of the same input; changes
    * exactly when anything that reaches the emitted file changes.
    *
    * NOT `sha256(canonical(t))`, which it was until trivia existed. `balticporter.core.TirCacheKey`
    * keys the action cache on this, and the cache stores EMITTED TEXT — so anything the emitter
    * writes has to be inside the digest or a source edit that only touched a comment gets a cache
    * HIT and re-serves the previous file, with the previous comment. That failure is silent, it
    * survives a `clean`, and no count moves; `Style.identity` closes it by construction rather
    * than by remembering to add each new field. */
  def digest(t: Tree)(using Program): String = sha256(render(t, Style.identity))

  def sha256(s: String): String =
    java.security.MessageDigest.getInstance("SHA-256")
      .digest(s.getBytes(java.nio.charset.StandardCharsets.UTF_8))
      .map(b => f"${b & 0xff}%02x").mkString

  /** the whole program, units in `fullName` order — order must not depend on the frontend's file
    * walk or every diff is noise. */
  def program(style: Style = Style.canonical)(using p: Program): String =
    p.units.map(u => (nameOf(u.symbol), u)).sortBy(_._1).map((_, u) => render(u, style)).mkString("\n")

  /** one unit by full name, `scala.None` when the program has no such unit. */
  def unit(fullName: String, style: Style = Style.debug)(using p: Program): Option[String] =
    p.units.find(u => nameOf(u.symbol) == fullName).map(render(_, style))

  // ---------------------------------------------------------------------------
  // symbols
  // ---------------------------------------------------------------------------

  private def nameOf(s: SymId)(using p: Program): String =
    if s == SymId.None then "<none>" else p.symbolOf(s).map(_.fullName).getOrElse(s"<unknown:${s.raw}>")

  private def sym(s: SymId, style: Style)(using Program): String =
    if style.showIds && s != SymId.None then s"${nameOf(s)}#${s.raw}" else nameOf(s)

  private def origin(o: Origin, style: Style): String =
    if !style.showOrigins || o == Origin.synthetic then "" else s"  @${o.javaPath}:${o.line}"

  private def ofType(t: TypeRepr, style: Style)(using Program): String =
    if style.showTypes then s" : ${tpe(t, style)}" else ""

  // ---------------------------------------------------------------------------
  // types — surface syntax, printed once
  // ---------------------------------------------------------------------------

  def tpe(t: TypeRepr, style: Style = Style.debug)(using Program): String = t match
    case TypeRepr.NoPrefix              => "<noprefix>"
    case TypeRepr.NoType                => "<notype>"
    case TypeRepr.ConstantType(c)       => s"${const(c, style)}.type"
    case TypeRepr.TypeRef(TypeRepr.NoPrefix, s) => sym(s, style)
    case TypeRepr.TypeRef(p, s)         => s"${tpe(p, style)}::${sym(s, style)}"
    case TypeRepr.TermRef(TypeRepr.NoPrefix, s) => s"${sym(s, style)}.type"
    case TypeRepr.TermRef(p, s)         => s"${tpe(p, style)}::${sym(s, style)}.type"
    case TypeRepr.ThisType(c)           => s"${sym(c, style)}.this"
    case TypeRepr.SuperType(a, b)       => s"${tpe(a, style)}.super[${tpe(b, style)}]"
    case TypeRepr.AppliedType(tc, as)   => s"${tpe(tc, style)}[${as.map(tpe(_, style)).mkString(", ")}]"
    case TypeRepr.AndType(l, r)         => s"(${tpe(l, style)} & ${tpe(r, style)})"
    case TypeRepr.OrType(l, r)          => s"(${tpe(l, style)} | ${tpe(r, style)})"
    case TypeRepr.ByNameType(u)         => s"=> ${tpe(u, style)}"
    case b: TypeRepr.TypeBounds         => s"?${bounds(b, style)}"
    case TypeRepr.Refinement(p, n, i)   => s"${tpe(p, style)} { $n: ${tpe(i, style)} }"
    case TypeRepr.MethodType(ps, r, im) =>
      val using_ = if im then "using " else ""
      s"($using_${ps.map((n, pt) => s"$n: ${tpe(pt, style)}").mkString(", ")}): ${tpe(r, style)}"
    case TypeRepr.PolyType(ps, r)   => s"[${ps.map((n, b) => s"$n${bounds(b, style)}").mkString(", ")}]${tpe(r, style)}"
    case TypeRepr.TypeLambda(ps, b) => s"[${ps.map((n, bd) => s"$n${bounds(bd, style)}").mkString(", ")}] =>> ${tpe(b, style)}"
    // NEVER recurse into the binder: it contains this node.
    case TypeRepr.ParamRef(binder, idx) => paramName(binder, idx)

  private def paramName(binder: TypeRepr, idx: Int): String = binder match
    case TypeRepr.MethodType(ps, _, _) => ps.lift(idx).map(_._1).getOrElse(s"_$$$idx")
    case TypeRepr.PolyType(ps, _)      => ps.lift(idx).map(_._1).getOrElse(s"_$$$idx")
    case TypeRepr.TypeLambda(ps, _)    => ps.lift(idx).map(_._1).getOrElse(s"_$$$idx")
    case _                             => s"_$$$idx"

  private def bounds(b: TypeRepr.TypeBounds, style: Style)(using Program): String =
    val lo = if b.low == TypeRepr.NoType then "" else s" >: ${tpe(b.low, style)}"
    val hi = if b.hi == TypeRepr.NoType then "" else s" <: ${tpe(b.hi, style)}"
    lo + hi

  def const(c: Constant, style: Style = Style.debug)(using Program): String = c match
    case Constant.BoolC(v)   => v.toString
    case Constant.ByteC(v)   => s"${v}b"
    case Constant.ShortC(v)  => s"${v}s"
    case Constant.CharC(v)   => s"'${escape(v.toString)}'"
    case Constant.IntC(v)    => v.toString
    case Constant.LongC(v)   => s"${v}L"
    case Constant.FloatC(v)  => s"${v}f"
    case Constant.DoubleC(v) => s"${v}d"
    case Constant.StringC(v) => s""""${escape(v)}""""
    case Constant.NullC      => "null"
    case Constant.UnitC      => "()"
    case Constant.ClassOfC(t) => s"classOf[${tpe(t, style)}]"

  private def escape(s: String): String =
    s.flatMap {
      case '\n' => "\\n"
      case '\r' => "\\r"
      case '\t' => "\\t"
      case '"'  => "\\\""
      case '\\' => "\\\\"
      case c    => c.toString
    }

  // ---------------------------------------------------------------------------
  // trees
  // ---------------------------------------------------------------------------

  private def pad(n: Int): String = "  " * n

  private def line(sb: StringBuilder, indent: Int, text: String): Unit =
    sb.append(pad(indent)).append(text).append('\n')

  /** a named group of children, printed only when non-empty — an always-printed empty group is
    * noise in every diff. */
  private def group(sb: StringBuilder, indent: Int, label: String, xs: List[Tree], style: Style)(using Program): Unit =
    if xs.nonEmpty then
      line(sb, indent, label)
      xs.foreach(tree(sb, _, indent + 1, style))

  private def sub(sb: StringBuilder, indent: Int, label: String, t: Tree, style: Style)(using Program): Unit =
    line(sb, indent, label)
    tree(sb, t, indent + 1, style)

  /** a node's carried comments, as one escaped line each — printed only under a style that asks
    * for them ([[Style.canonical]] does not; [[Style.identity]] does). Escaped rather than
    * reproduced, because a multi-line Javadoc printed raw would break the indent-per-line format
    * this whole rendering is diffable BECAUSE of. */
  private def trivia(sb: StringBuilder, indent: Int, label: String, ts: List[Trivia], style: Style): Unit =
    if style.showTrivia && ts.nonEmpty then
      line(sb, indent, label)
      ts.foreach(t => line(sb, indent + 1, s"${t.kind} \"${escape(t.text)}\""))

  def tree(sb: StringBuilder, t: Tree, indent: Int, style: Style)(using p: Program): Unit = t match
    // ---- definitions ----
    case d: Tree.ClassDef =>
      val f = p.symbolOf(d.symbol).map(_.flags)
      val kind =
        if f.exists(_.isTrait) then "trait" else if f.exists(_.isModule) then "object"
        else if f.exists(_.isEnum) then "enum" else "class"
      line(sb, indent, s"ClassDef $kind ${sym(d.symbol, style)}${origin(d.origin, style)}")
      trivia(sb, indent + 1, "unitLeading", d.unitLeading, style)
      trivia(sb, indent + 1, "leading", d.leading, style)
      group(sb, indent + 1, "tparams", d.tparams, style)
      group(sb, indent + 1, "parents", d.parents.map(x => x: Tree), style)
      d.selfType.foreach(st => sub(sb, indent + 1, "selfType", st, style))
      if d.enumCases.nonEmpty then
        line(sb, indent + 1, "enumCases")
        d.enumCases.foreach { ec =>
          line(sb, indent + 2, s"EnumCase ${sym(ec.symbol, style)}${origin(ec.origin, style)}")
          group(sb, indent + 3, "ctorArgs", ec.ctorArgs.map(x => x: Tree), style)
          group(sb, indent + 3, "body", ec.body.map(x => x: Tree), style)
        }
      group(sb, indent + 1, "body", d.body.map(x => x: Tree), style)

    case d: Tree.TypeDef =>
      line(sb, indent, s"TypeDef ${sym(d.symbol, style)} = ${tpe(d.rhs.tpe, style)}${origin(d.origin, style)}")

    case d: Tree.DefDef =>
      line(sb, indent, s"DefDef ${sym(d.symbol, style)}: ${tpe(d.returnTpt.tpe, style)}${origin(d.origin, style)}")
      trivia(sb, indent + 1, "leading", d.leading, style)
      group(sb, indent + 1, "tparams", d.tparams, style)
      d.paramss.zipWithIndex.foreach((ps, i) => group(sb, indent + 1, s"params[$i]", ps.map(x => x: Tree), style))
      d.rhs.foreach(r => sub(sb, indent + 1, "rhs", r, style))

    case d: Tree.ValDef =>
      val f  = p.symbolOf(d.symbol).map(_.flags)
      val kw = if f.exists(_.isMutable) then "var" else "val"
      line(sb, indent, s"ValDef $kw ${sym(d.symbol, style)}: ${tpe(d.tpt.tpe, style)}${origin(d.origin, style)}")
      trivia(sb, indent + 1, "leading", d.leading, style)
      d.rhs.foreach(r => sub(sb, indent + 1, "rhs", r, style))

    case tt: TypeTree =>
      line(sb, indent, s"TypeTree ${tpe(tt.tpe, style)}${origin(tt.origin, style)}")

    // ---- terms ----
    case x: Tree.Ident =>
      line(sb, indent, s"Ident ${sym(x.sym, style)}${ofType(x.tpe, style)}${origin(x.origin, style)}")
    case x: Tree.Select =>
      line(sb, indent, s"Select ${sym(x.sym, style)}${ofType(x.tpe, style)}${origin(x.origin, style)}")
      sub(sb, indent + 1, "qual", x.qual, style)
    case x: Tree.Literal =>
      line(sb, indent, s"Literal ${const(x.const, style)}${ofType(x.tpe, style)}${origin(x.origin, style)}")
    case x: Tree.This =>
      line(sb, indent, s"This ${sym(x.cls, style)}${ofType(x.tpe, style)}${origin(x.origin, style)}")
    case x: Tree.Super =>
      line(sb, indent, s"Super ${sym(x.cls, style)}${ofType(x.tpe, style)}${origin(x.origin, style)}")
    case x: Tree.New =>
      line(sb, indent, s"New ${tpe(x.tpt.tpe, style)}${ofType(x.tpe, style)}${origin(x.origin, style)}")
      x.anon.foreach { a =>
        line(sb, indent + 1, s"anon ${sym(a.symbol, style)}${if a.dropped.isEmpty then "" else s" DROPPED[${a.dropped.mkString(",")}]"}")
        group(sb, indent + 2, "body", a.body.map(y => y: Tree), style)
      }
    case x: Tree.Apply =>
      line(sb, indent, s"Apply ${sym(x.method, style)}${ofType(x.tpe, style)}${origin(x.origin, style)}")
      sub(sb, indent + 1, "fun", x.fun, style)
      group(sb, indent + 1, "args", x.args.map(y => y: Tree), style)
    case x: Tree.TypeApply =>
      line(sb, indent, s"TypeApply [${x.targs.map(a => tpe(a.tpe, style)).mkString(", ")}]${ofType(x.tpe, style)}${origin(x.origin, style)}")
      sub(sb, indent + 1, "fun", x.fun, style)
    case x: Tree.Assign =>
      line(sb, indent, s"Assign${ofType(x.tpe, style)}${origin(x.origin, style)}")
      sub(sb, indent + 1, "lhs", x.lhs, style)
      sub(sb, indent + 1, "rhs", x.rhs, style)
    case x: Tree.Block =>
      line(sb, indent, s"Block${ofType(x.tpe, style)}${origin(x.origin, style)}")
      group(sb, indent + 1, "stats", x.stats.map(y => y: Tree), style)
      sub(sb, indent + 1, "expr", x.expr, style)
      // …and the block's END-OF-BODY comments, under the same rule as every other trivia field:
      // elided by `canonical` (no phase reads a comment) and carried by `digest`, which keys the
      // action cache on EMITTED TEXT and would otherwise re-serve a file without them (V2).
      trivia(sb, indent + 1, "trailing", x.trailing, style)
    case x: Tree.Lambda =>
      line(sb, indent, s"Lambda${ofType(x.tpe, style)}${origin(x.origin, style)}")
      group(sb, indent + 1, "params", x.params.map(y => y: Tree), style)
      sub(sb, indent + 1, "body", x.body, style)
    case x: Tree.If =>
      line(sb, indent, s"If${ofType(x.tpe, style)}${origin(x.origin, style)}")
      sub(sb, indent + 1, "cond", x.cond, style)
      sub(sb, indent + 1, "then", x.thenp, style)
      sub(sb, indent + 1, "else", x.elsep, style)
    case x: Tree.Typed =>
      line(sb, indent, s"Typed ${tpe(x.tpt.tpe, style)}${ofType(x.tpe, style)}${origin(x.origin, style)}")
      sub(sb, indent + 1, "expr", x.expr, style)
    case x: Tree.Repeated =>
      line(sb, indent, s"Repeated${ofType(x.tpe, style)}${origin(x.origin, style)}")
      group(sb, indent + 1, "elems", x.elems.map(y => y: Tree), style)
    case x: Tree.Return =>
      line(sb, indent, s"Return${ofType(x.tpe, style)}${origin(x.origin, style)}")
      x.expr.foreach(e => sub(sb, indent + 1, "expr", e, style))
    case x: Tree.While =>
      line(sb, indent, s"While${x.label.map(l => s" label=$l").getOrElse("")}${origin(x.origin, style)}")
      sub(sb, indent + 1, "cond", x.cond, style)
      sub(sb, indent + 1, "body", x.body, style)
    case x: Tree.Throw =>
      line(sb, indent, s"Throw${origin(x.origin, style)}")
      sub(sb, indent + 1, "expr", x.expr, style)
    case x: Tree.InstanceOf =>
      line(sb, indent, s"InstanceOf ${tpe(x.tpt.tpe, style)}${origin(x.origin, style)}")
      sub(sb, indent + 1, "expr", x.expr, style)
    case x: Tree.ArrayAccess =>
      line(sb, indent, s"ArrayAccess${ofType(x.tpe, style)}${origin(x.origin, style)}")
      sub(sb, indent + 1, "array", x.array, style)
      sub(sb, indent + 1, "index", x.index, style)
    case x: Tree.ArrayLength =>
      line(sb, indent, s"ArrayLength${ofType(x.tpe, style)}${origin(x.origin, style)}")
      sub(sb, indent + 1, "array", x.array, style)
    case x: Tree.NewArray =>
      line(sb, indent, s"NewArray ${tpe(x.elem.tpe, style)}${ofType(x.tpe, style)}${origin(x.origin, style)}")
      group(sb, indent + 1, "dims", x.dims.map(y => y: Tree), style)
      x.init.foreach(i => group(sb, indent + 1, "init", i.map(y => y: Tree), style))
    case x: Tree.ForEach =>
      line(sb, indent, s"ForEach${x.label.map(l => s" label=$l").getOrElse("")}${origin(x.origin, style)}")
      sub(sb, indent + 1, "binding", x.binding, style)
      sub(sb, indent + 1, "iterable", x.iterable, style)
      sub(sb, indent + 1, "body", x.body, style)
    case x: Tree.For =>
      line(sb, indent, s"For${x.label.map(l => s" label=$l").getOrElse("")}${origin(x.origin, style)}")
      group(sb, indent + 1, "init", x.init.map(y => y: Tree), style)
      x.cond.foreach(c => sub(sb, indent + 1, "cond", c, style))
      group(sb, indent + 1, "update", x.update.map(y => y: Tree), style)
      sub(sb, indent + 1, "body", x.body, style)
    case x: Tree.Try =>
      line(sb, indent, s"Try${ofType(x.tpe, style)}${origin(x.origin, style)}")
      group(sb, indent + 1, "resources", x.resources.map(y => y: Tree), style)
      sub(sb, indent + 1, "body", x.body, style)
      if x.catches.nonEmpty then
        line(sb, indent + 1, "catches")
        x.catches.foreach { c =>
          line(sb, indent + 2, "CatchCase")
          sub(sb, indent + 3, "param", c.param, style)
          sub(sb, indent + 3, "body", c.body, style)
        }
      x.finalizer.foreach(f => sub(sb, indent + 1, "finally", f, style))
    case x: Tree.Match =>
      line(sb, indent, s"Match${ofType(x.tpe, style)}${origin(x.origin, style)}")
      sub(sb, indent + 1, "scrutinee", x.scrutinee, style)
      x.cases.foreach { c =>
        line(sb, indent + 1, s"CaseDef${if c.isDefault then " default" else ""}")
        group(sb, indent + 2, "labels", c.labels.map(y => y: Tree), style)
        c.guard.foreach(g => sub(sb, indent + 2, "guard", g, style))
        sub(sb, indent + 2, "body", c.body, style)
      }
    case x: Tree.MethodRef =>
      line(sb, indent, s"MethodRef ${sym(x.method, style)}${ofType(x.tpe, style)}${origin(x.origin, style)}")
      x.qualifier match
        case Left(tt)  => sub(sb, indent + 1, "qualifier", tt, style)
        case Right(tm) => sub(sb, indent + 1, "qualifier", tm, style)
    case x: Tree.Break =>
      line(sb, indent, s"Break${x.label.map(l => s" $l").getOrElse("")}${origin(x.origin, style)}")
    case x: Tree.Continue =>
      line(sb, indent, s"Continue${x.label.map(l => s" $l").getOrElse("")}${origin(x.origin, style)}")
    case x: Tree.Labeled =>
      line(sb, indent, s"Labeled ${x.name}${origin(x.origin, style)}")
      sub(sb, indent + 1, "stmt", x.stmt, style)
    case x: Tree.Assert =>
      line(sb, indent, s"Assert${origin(x.origin, style)}")
      sub(sb, indent + 1, "cond", x.cond, style)
      x.msg.foreach(m => sub(sb, indent + 1, "msg", m, style))
    case x: Tree.IncDec =>
      line(sb, indent, s"IncDec ${if x.post then "post" else "pre"}${x.op}${ofType(x.tpe, style)}${origin(x.origin, style)}")
      sub(sb, indent + 1, "target", x.target, style)
    case x: Tree.DoWhile =>
      line(sb, indent, s"DoWhile${x.label.map(l => s" label=$l").getOrElse("")}${origin(x.origin, style)}")
      sub(sb, indent + 1, "body", x.body, style)
      sub(sb, indent + 1, "cond", x.cond, style)
    case x: Tree.Synchronized =>
      line(sb, indent, s"Synchronized${origin(x.origin, style)}")
      sub(sb, indent + 1, "lock", x.lock, style)
      sub(sb, indent + 1, "body", x.body, style)
    case x: Tree.Commented =>
      // Under a style that elides trivia this node prints as its statement ALONE, with no wrapper
      // line: a canonical dump is then identical whether the Java had a comment there or not,
      // which is the property that makes two phase dumps comparable.
      if style.showTrivia then
        line(sb, indent, "Commented")
        trivia(sb, indent + 1, "leading", x.leading, style)
        sub(sb, indent + 1, "stmt", x.stmt, style)
      else tree(sb, x.stmt, indent, style)
    case x: Tree.Opaque =>
      // the hole markers are NUL, which no dump should carry: rendered `{0}` `{1}` … so the text
      // reads the way the policy entry that produced it was written, with the terms below it.
      val shown = x.holes.indices.foldLeft(x.raw)((s, i) => s.replace(Tree.Opaque.hole(i), s"{$i}"))
      line(sb, indent, s"Opaque ${"\""}${escape(shown)}${"\""}${ofType(x.tpe, style)}${origin(x.origin, style)}")
      group(sb, indent + 1, "holes", x.holes, style)

