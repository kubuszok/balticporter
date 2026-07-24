package balticporter.emit

import balticporter.tir.*

/** Emission backend: the TRANSFORMED typed TIR → Scala 3 source (RECOMPILER.md step 3,
  * "source pretty-printing"). Because every node carries its resolved `TypeRepr` and every
  * reference a `SymId`, the emitter inserts the right form by construction — it looks names
  * up in the `Program`'s symbol table rather than re-deriving them, so the inference/diamond
  * bugs of the string-printer era cannot occur.
  *
  * First cut: covers the whole node set. A few Java-only control forms have no direct Scala
  * surface and are lowered approximately (marked inline) — `break`/`continue` (need
  * `boundary`), do-while (dropped in Scala 3), and inc/dec used as a value; those are the
  * emitter's known refinement points, not populator gaps.
  */
final class TirEmitter(source: Program):
  // normalize away Java member-name clashes (a field `x` alongside a method `x()`) before
  // rendering — Scala forbids them; renaming the field symbol propagates to every reference.
  private val program = TirEmitter.resolveMemberClashes(source)

  def emit: String = program.units.map(emitUnit).mkString("\n\n")

  /** types declared in the unit currently being rendered (in scope by simple name). */
  private var currentDeclared: Set[SymId] = Set.empty

  def emitUnit(cd: Tree.ClassDef): String =
    currentDeclared = declaredTypes(cd)
    val body = classDef(cd, 0)
    val full = sym(cd.symbol).fullName
    val pkg  = if full.contains('.') then s"package ${full.substring(0, full.lastIndexOf('.'))}\n\n" else ""
    pkg + body

  private def declaredTypes(cd: Tree.ClassDef): Set[SymId] =
    val acc = collection.mutable.Set[SymId](cd.symbol)
    cd.body.foreach { case c: Tree.ClassDef => acc ++= declaredTypes(c); case _ => () }
    acc.toSet

  // ---- names ----
  private def sym(id: SymId): Symbol = program.symbolOf(id).getOrElse(Symbol(id, "?", "?", Flags(), SymId.None, TypeRepr.NoType))
  private def local(id: SymId): String = esc(sym(id).name)

  private val keywords = Set(
    "type", "object", "val", "var", "def", "class", "trait", "enum", "given", "match", "case",
    "if", "else", "while", "do", "for", "yield", "then", "with", "extends", "new", "this", "super",
    "null", "true", "false", "import", "package", "override", "final", "abstract", "sealed", "private",
    "protected", "implicit", "lazy", "return", "throw", "try", "catch", "finally", "forSome", "using",
    "export", "inline", "opaque", "transparent", "derives", "extension", "macro", "end", "as", "wait",
  )
  /** backtick an identifier that collides with a Scala keyword. */
  private def esc(name: String): String = if keywords(name) then s"`$name`" else name
  /** a TYPE symbol's rendered name. FULLY QUALIFIED by default — for the structural Java→Scala
    * phase we emit fully-qualified references and generate NO imports, which deletes the entire
    * import-decision bug class (import-vs-projection, shadowing, static-receiver qualification):
    * a reference is now a context-free function of the symbol's owner chain. Only two things
    * stay unqualified: type params, and a type declared in THIS unit (in scope by simple name).
    * Human-readable imports are a separate, optional beautification backend, not a correctness
    * prerequisite. (A later refinement handles givens/extensions, which FQN genuinely can't name.) */
  private def typeSym(id: SymId): String =
    val s = sym(id)
    if s.flags.isParam then esc(s.name)
    else if program.definitionOf(id).isDefined && currentDeclared(id) then esc(s.name) // declared here — in scope
    else if program.symbolOf(s.owner).exists(_.flags.isModule) then s"${typeValue(s.owner)}.${esc(s.name)}" // object's type member → path-dependent `O.T`
    else s.fullName.replace('$', '#')                               // everything else → fully qualified (class-nested → `Outer#Inner`)

  private def ind(n: Int): String = "  " * n

  // ---- definitions ----
  private def classDef(cd: Tree.ClassDef, i: Int): String =
    if sym(cd.symbol).flags.isEnum then return enumDef(cd, i)
    val s  = sym(cd.symbol)
    val kw =
      if s.flags.isModule then "object"
      else if s.flags.isTrait then "trait"
      else "class"
    val tps     = if cd.tparams.isEmpty then "" else "[" + cd.tparams.map(typeParam).mkString(", ") + "]"
    val parents = cd.parents.map(parent).filter(_.nonEmpty)
    val ext     = if parents.isEmpty then "" else " extends " + parents.mkString(" with ")
    // an all-static utility class (no instance state, no supertype) is just an `object` — so its
    // static members and nested types live together and see each other by simple name.
    val hasInstanceState = cd.body.exists {
      case d: Tree.DefDef => sym(d.symbol).name != "<init>" && !sym(d.symbol).flags.isStatic
      case v: Tree.ValDef => !sym(v.symbol).flags.isStatic
      case _              => false
    }
    if kw == "class" && parents.isEmpty && cd.body.nonEmpty && !hasInstanceState then
      val members = cd.body.filterNot { case d: Tree.DefDef => sym(d.symbol).name == "<init>"; case _ => false }
      val ob = orderBody(members).map(stat(_, i + 1)).filter(_.nonEmpty).mkString("\n")
      return s"${ind(i)}object ${esc(s.name)}$tps {\n$ob\n${ind(i)}}"
    // Java statics have no instance home in Scala — they move to the companion object.
    val (statics, instance) = if s.flags.isModule then (Nil, cd.body) else cd.body.partition(isStatic)
    val self    = cd.selfType.map(st => s"${ind(i + 1)}self: ${tpe(st.tpe)} =>\n").getOrElse("")
    val body    = orderBody(instance).map(stat(_, i + 1)).filter(_.nonEmpty).mkString("\n")
    val open    = if body.isEmpty && self.isEmpty then "" else s" {\n$self$body\n${ind(i)}}"
    val abs     = if kw == "class" && s.flags.isAbstract then "abstract " else ""
    val cls     = s"${ind(i)}${mods(s.flags)}$abs$kw ${esc(s.name)}$tps$ext$open"
    if statics.isEmpty then cls
    else
      val sb = orderBody(statics).map(stat(_, i + 1)).filter(_.nonEmpty).mkString("\n")
      s"$cls\n${ind(i)}object ${esc(s.name)} {\n$sb\n${ind(i)}}"

  // static vals/defs move to the companion object; nested TYPES stay in the class body so
  // they remain in scope by simple name inside it (a Java static nested class is visible
  // throughout its enclosing class).
  /** Java enum → `sealed abstract class Name <parents-minus-Enum> { members }` plus a
    * companion `object` holding each constant as a `case object` and a `values` array. */
  private def enumDef(cd: Tree.ClassDef, i: Int): String =
    val s       = sym(cd.symbol)
    val name    = esc(s.name)
    val parents = cd.parents.map(parent).filter(p => p.nonEmpty && !p.startsWith("java.lang.Enum"))
    val ext     = if parents.isEmpty then "" else " extends " + parents.mkString(" with ")
    val (statics, instance) = cd.body.partition(isStatic)
    val cbody   = orderBody(instance).map(stat(_, i + 1)).filter(_.nonEmpty).mkString("\n")
    val cls     = s"${ind(i)}sealed abstract class $name$ext" + (if cbody.isEmpty then "" else s" {\n$cbody\n${ind(i)}}")
    val cases = cd.enumCases.map { ec =>
      val cn   = esc(sym(ec.symbol).name)
      val args = if ec.ctorArgs.isEmpty then "" else s"(${ec.ctorArgs.map(term(_, i + 1)).mkString(", ")})"
      val body = if ec.body.isEmpty then "" else s" {\n${ec.body.map(stat(_, i + 2)).mkString("\n")}\n${ind(i + 1)}}"
      s"${ind(i + 1)}case object $cn extends $name$args$body"
    }
    // `def` (not `val`) so Java's `E.values()` call site type-checks; also a no-paren read works.
    val values = s"${ind(i + 1)}def values(): Array[$name] = Array(${cd.enumCases.map(ec => esc(sym(ec.symbol).name)).mkString(", ")})"
    val objBody = (cases :+ values) ++ statics.map(stat(_, i + 1)).filter(_.nonEmpty)
    s"$cls\n${ind(i)}object $name {\n${objBody.mkString("\n")}\n${ind(i)}}"

  private def isStatic(s: Statement): Boolean = s match
    case _: Tree.ClassDef => false
    case d: Definition    => sym(d.symbol).flags.isStatic
    case _                => false

  /** Scala secondary constructors must delegate to a PRECEDING constructor, so order fields
    * first, then constructors by descending arity (a convenience ctor `this(a)` delegating to
    * a fuller `this(a,b)` needs the fuller one earlier), then everything else. */
  private def orderBody(body: List[Statement]): List[Statement] =
    def isCtor(s: Statement) = s match { case d: Tree.DefDef => sym(d.symbol).name == "<init>"; case _ => false }
    // a ctor that delegates to `this(args)` must follow the one it delegates to. A base ctor
    // (delegates to the primary via `this()`/super) sorts first; then by descending arity.
    def delegatesToPeer(d: Tree.DefDef): Boolean = d.rhs match
      case Some(Tree.Block((Tree.Apply(Tree.Select(r, m, _, _), args, _, _, _)) :: _, _, _, _)) =>
        sym(m).name == "<init>" && args.nonEmpty && !r.isInstanceOf[Tree.Super]
      case _ => false
    // a no-arg constructor whose body is only super/this delegation is degenerate — Scala's
    // implicit primary constructor already is no-arg, and `def this() = this()` self-recurses.
    def degenerate(d: Tree.DefDef): Boolean =
      d.paramss.flatten.isEmpty && (d.rhs match
        case Some(Tree.Block(stats, _, _, _)) =>
          stats.forall { case Tree.Apply(Tree.Select(_, m, _, _), _, _, _, _) => sym(m).name == "<init>"; case _ => false }
        case _ => true)
    val ctors = body.collect { case d: Tree.DefDef if isCtor(d) && !degenerate(d) => d }
      .sortBy(d => (if delegatesToPeer(d) then 1 else 0, -d.paramss.map(_.size).sum))
    val fields = body.collect { case v: Tree.ValDef => v }
    val rest   = body.filterNot(s => isCtor(s) || s.isInstanceOf[Tree.ValDef])
    fields ++ ctors ++ rest

  private def typeParam(td: Tree.TypeDef): String =
    val name = esc(sym(td.symbol).name)
    td.rhs.tpe match
      case TypeRepr.TypeBounds(TypeRepr.NoType, TypeRepr.NoType) => name
      case TypeRepr.TypeBounds(lo, hi) =>
        val l = if lo == TypeRepr.NoType then "" else s" >: ${tpe(lo)}"
        val h = if hi == TypeRepr.NoType then "" else s" <: ${tpe(hi)}"
        s"$name$l$h"
      case other => s"$name <: ${tpe(other)}"

  private def parent(p: Term | TypeTree): String = p match
    case tt: TypeTree  => tpe(tt.tpe)
    case t: Term  => tpe(t.tpe)

  private def stat(s: Statement, i: Int): String = s match
    case c: Tree.ClassDef => classDef(c, i)
    case d: Tree.DefDef   => defDef(d, i)
    case v: Tree.ValDef   => valDef(v, i)
    case t: Tree.TypeDef  => s"${ind(i)}${if sym(t.symbol).flags.isOpaque then "opaque " else ""}type ${esc(sym(t.symbol).name)} = ${tpe(t.rhs.tpe)}"
    case t: Term     => ind(i) + term(t, i)

  private def defDef(d: Tree.DefDef, i: Int): String =
    val s     = sym(d.symbol)
    val isCtor = s.name == "<init>"
    val name  = if isCtor then "this" else esc(s.name)
    val tps   = if d.tparams.isEmpty then "" else "[" + d.tparams.map(typeParam).mkString(", ") + "]"
    val pss   = d.paramss.map(paramClause).mkString
    val ret   = if isCtor then "" else s": ${tpe(d.returnTpt.tpe)}"
    val rhs   = if isCtor then s" = ${ctorBody(d.rhs, i)}" else d.rhs.map(r => s" = ${term(r, i)}").getOrElse("")
    s"${ind(i)}${mods(s.flags)}def $name$tps$pss$ret$rhs"

  /** A Scala secondary constructor must delegate to `this(...)` first — never `super(...)`.
    * Keep a Java `this(args)` delegation; rewrite a leading `super(...)`/implicit-super to
    * `this()` (its super args are dropped — the primary-vs-secondary split is future work). */
  private def ctorBody(rhs: Option[Term], i: Int): String =
    val stats = rhs match
      case Some(Tree.Block(s, _, _, _)) => s
      case Some(t)                      => List(t)
      case None                         => Nil
    val (deleg, rest) = stats match
      case (Tree.Apply(Tree.Select(r, m, _, _), args, _, _, _)) :: tl if sym(m).name == "<init>" =>
        val d = r match
          case _: Tree.Super => "this()"
          case _             => s"this(${args.map(term(_, i + 1)).mkString(", ")})"
        (d, tl)
      case all => ("this()", all)
    val lines = (ind(i + 1) + deleg) :: rest.map(stat(_, i + 1)).filter(_.trim.nonEmpty)
    s"{\n${lines.mkString("\n")}\n${ind(i)}}"

  /** a parameter clause; a clause of `given` params renders as a Scala 3 `using` clause. */
  private def paramClause(ps: List[Tree.ValDef]): String =
    if ps.nonEmpty && ps.forall(p => sym(p.symbol).flags.isGiven) then s"(using ${ps.map(param).mkString(", ")})"
    else s"(${ps.map(param).mkString(", ")})"

  private def param(v: Tree.ValDef): String = s"${esc(sym(v.symbol).name)}: ${tpe(v.tpt.tpe)}"

  private def valDef(v: Tree.ValDef, i: Int): String =
    val s = sym(v.symbol)
    if s.flags.isGiven then
      return s"${ind(i)}given ${esc(s.name)}: ${tpe(v.tpt.tpe)}${v.rhs.map(r => s" = ${term(r, i)}").getOrElse("")}"
    v.rhs match
      case Some(r) =>
        val kw = if s.flags.isMutable then "var" else "val"
        val m  = if kw == "var" then mods(s.flags).replace("final ", "") else mods(s.flags)
        s"${ind(i)}$m$kw ${esc(s.name)}: ${tpe(v.tpt.tpe)} = ${term(r, i)}"
      case None =>
        // an uninitialized Java field: a `var` defaulted so constructors can assign it (a bare
        // `val x: T` is an abstract member and won't compile in a class). `final var` is
        // contradictory in Scala, so `final` is dropped here.
        s"${ind(i)}${mods(s.flags).replace("final ", "")}var ${esc(s.name)}: ${tpe(v.tpt.tpe)} = ${defaultFor(v.tpt.tpe)}"

  private def defaultFor(t: TypeRepr): String = t match
    case TypeRepr.TypeRef(_, s) => sym(s).fullName match
        case "scala.Int" | "scala.Short" | "scala.Byte" => "0"
        case "scala.Long"                               => "0L"
        case "scala.Float"                              => "0.0f"
        case "scala.Double"                             => "0.0"
        case "scala.Boolean"                            => "false"
        case "scala.Char"                               => "'\\u0000'"
        case "scala.Unit"                               => "()"
        case _                                          => s"null.asInstanceOf[${tpe(t)}]"
    case _ => s"null.asInstanceOf[${tpe(t)}]"

  private def mods(f: Flags): String =
    val parts = List(
      if f.isPrivate then "private " else "",
      if f.isProtected then "protected " else "",
      if f.isOverride then "override " else "",
      if f.isFinal then "final " else "",
      if f.isSealed then "sealed " else "",
      if f.isImplicit then "implicit " else "",
      if f.isLazy then "lazy " else "",
    )
    parts.mkString

  // ---- terms ----
  /** true when an `Ident`'s symbol is actually a TYPE used as a value (a static-access
    * receiver like `Float.compare`) — those must render as the (qualified) type name. */
  private def isTypeRef(id: SymId): Boolean = program.definitionOf(id) match
    case Some(_: Tree.ClassDef) => true
    case Some(_)                => false
    case None                   => val f = sym(id).fullName; f.contains('.') && !f.contains('#') && sym(id).info == TypeRepr.NoType

  /** a type used as a VALUE (static-access receiver) — dotted path, never a `#` projection
    * (which is type-position-only syntax). */
  private def typeValue(id: SymId): String =
    val s = sym(id)
    if currentDeclared(id) then esc(s.name) else s.fullName.replace('$', '.')

  /** a static member lives in the companion `object`; even inside its own class it must be
    * named `Owner.member`, since a Scala class doesn't see its companion's members unqualified. */
  private def staticRef(s: SymId): String =
    val sm = sym(s)
    if sm.flags.isStatic && sm.owner != SymId.None && program.symbolOf(sm.owner).exists(_.info.isInstanceOf[TypeRepr.TypeRef])
    then s"${typeValue(sm.owner)}.${esc(sm.name)}"
    else local(s)

  private def term(t: Term, i: Int): String = t match
    case Tree.Ident(s, _, _)            => if isTypeRef(s) then typeValue(s) else staticRef(s)
    case Tree.Literal(c, _, _)          => constant(c)
    case Tree.This(_, _, _)             => "this"
    case Tree.Super(_, _, _)            => "super"
    case Tree.Select(q, s, _, _)        => s"${term(q, i)}.${local(s)}"
    case Tree.New(tpt, _, _)            => s"new ${tpe(tpt.tpe)}"
    case Tree.Apply(fun, args, _, _, _) => applyStr(fun, args, i)
    case Tree.TypeApply(fun, targs, _, _) => s"${term(fun, i)}[${targs.map(a => tpe(a.tpe)).mkString(", ")}]"
    case Tree.Assign(l, r, _, _)        => s"${term(l, i)} = ${term(r, i)}"
    case Tree.Block(stats, expr, _, _)  => block(stats, expr, i)
    case Tree.Lambda(ps, body, _, _)    => s"(${ps.map(param).mkString(", ")}) => ${term(body, i)}"
    case Tree.If(c, th, el, _, _)       => s"if (${term(c, i)}) ${term(th, i)} else ${term(el, i)}"
    case Tree.Typed(e, tpt, _, _)       => s"${operand(e, i)}.asInstanceOf[${tpe(tpt.tpe)}]" // Java cast
    case Tree.Repeated(es, _, _)        => es.map(term(_, i)).mkString(", ")
    case Tree.Return(e, _, _)           => "return" + e.map(x => " " + term(x, i)).getOrElse("")
    case Tree.While(c, b, _, _)         => s"while (${term(c, i)}) ${term(b, i)}"
    case Tree.Throw(e, _, _)            => s"throw ${term(e, i)}"
    case Tree.InstanceOf(e, tpt, _, _)  => s"${term(e, i)}.isInstanceOf[${tpe(tpt.tpe)}]"
    case Tree.ArrayAccess(a, idx, _, _) => s"${term(a, i)}(${term(idx, i)})"
    case Tree.ArrayLength(a, _, _)      => s"${term(a, i)}.length"
    case Tree.NewArray(el, dims, init, _, _) =>
      init match
        case Some(es) => s"Array[${tpe(el.tpe)}](${es.map(term(_, i)).mkString(", ")})"
        case None     => s"new Array[${tpe(el.tpe)}](${dims.map(term(_, i)).mkString(", ")})"
    case Tree.ForEach(b, it, body, _, _) => s"for (${esc(sym(b.symbol).name)} <- ${term(it, i)}) ${term(body, i)}"
    case Tree.For(init, cond, upd, body, _, _) =>
      val is = init.map(stat(_, 0)).mkString("; ")
      val c  = cond.map(term(_, i)).getOrElse("true")
      val bodyWithUpd = s"{ ${term(body, i)}; ${upd.map(stat(_, 0)).mkString("; ")} }"
      s"{ $is; while ($c) $bodyWithUpd }"
    case Tree.Try(res, body, catches, fin, _, _) => tryStr(res, body, catches, fin, i)
    case Tree.Match(scr, cases, _, _)   => matchStr(scr, cases, i)
    case Tree.MethodRef(q, s, _, _)     => q match
        case Left(tt)  => s"${tpe(tt.tpe)}.${local(s)}"
        case Right(e)  => s"${term(e, i)}.${local(s)}"
    case Tree.Break(_, _, _)            => "/* break */ ()"    // TODO: scala.util.boundary
    case Tree.Continue(_, _, _)         => "/* continue */ ()" // TODO: scala.util.boundary
    case Tree.Assert(c, m, _, _)        => s"assert(${term(c, i)}${m.map(x => ", " + term(x, i)).getOrElse("")})"
    case Tree.IncDec(tgt, op, _, _, _)  => s"{ ${term(tgt, i)} $op= 1; ${term(tgt, i)} }" // yields the value
    case Tree.DoWhile(b, c, _, _)       => s"while ({ ${term(b, i)}; ${term(c, i)} }) ()" // Scala 3 has no do-while
    case Tree.Synchronized(l, b, _, _)  => s"${term(l, i)}.synchronized ${term(b, i)}"
    case Tree.Opaque(raw, _, _)         => raw

  private def applyStr(fun: Term, args: List[Term], i: Int): String = fun match
    case Tree.New(tpt, _, _) => s"new ${tpe(tpt.tpe)}(${args.map(term(_, i)).mkString(", ")})"
    // operators (populator tags them `scala.<op>#…`) render infix / prefix, not `.op(x)`.
    case Tree.Select(recv, m, _, _) if sym(m).fullName.startsWith("scala.<op>#") =>
      val op = sym(m).name
      if op.startsWith("unary_") then s"${op.stripPrefix("unary_")}${operand(recv, i)}"
      else s"${operand(recv, i)} $op ${args.map(operand(_, i)).mkString(", ")}"
    case Tree.Select(recv, m, _, _) if sym(m).name == "<init>" =>
      val kw = recv match { case _: Tree.Super => "super"; case _ => "this" }
      s"$kw(${args.map(term(_, i)).mkString(", ")})"
    case _ => s"${term(fun, i)}(${args.map(term(_, i)).mkString(", ")})"

  /** parenthesize an operator application when it is an operand, to preserve precedence. */
  private def operand(t: Term, i: Int): String = t match
    case Tree.Apply(Tree.Select(_, m, _, _), _, _, _, _) if sym(m).fullName.startsWith("scala.<op>#") =>
      s"(${term(t, i)})"
    case _ => term(t, i)

  private def block(stats: List[Statement], expr: Term, i: Int): String =
    // drop a redundant trailing `()` when the block already has statements (Java void bodies).
    val tail = expr match
      case Tree.Literal(Constant.UnitC, _, _) if stats.nonEmpty => Nil
      case _                                                    => List(ind(i + 1) + term(expr, i + 1))
    val lines = (stats.map(stat(_, i + 1)) ++ tail).filter(_.trim.nonEmpty)
    s"{\n${lines.mkString("\n")}\n${ind(i)}}"

  private def tryStr(res: List[Tree.ValDef], body: Term, catches: List[Tree.CatchCase], fin: Option[Term], i: Int): String =
    val r  = res.map(v => s"${ind(i + 1)}${valDef(v, 0)}\n").mkString
    val cs = catches.map(c => s"${ind(i + 1)}case ${esc(sym(c.param.symbol).name)}: ${tpe(c.param.tpt.tpe)} => ${term(c.body, i + 1)}").mkString("\n")
    val cl = if catches.isEmpty then "" else s" catch {\n$cs\n${ind(i)}}"
    val fl = fin.map(f => s" finally ${term(f, i)}").getOrElse("")
    s"try ${term(body, i)}$cl$fl" // resources: r prepended when the backend lowers auto-close

  private def matchStr(scr: Term, cases: List[Tree.CaseDef], i: Int): String =
    val cs = cases.map { c =>
      val pat = if c.isDefault then "_" else c.labels.map(term(_, i)).mkString(" | ")
      s"${ind(i + 1)}case $pat => ${term(c.body, i + 1)}"
    }.mkString("\n")
    s"${term(scr, i)} match {\n$cs\n${ind(i)}}"

  // ---- types ----
  private def tpe(t: TypeRepr): String = t match
    case TypeRepr.NoType | TypeRepr.NoPrefix   => "Any"
    case TypeRepr.TypeRef(_, s)                => typeSym(s)
    case TypeRepr.TermRef(_, s)                => s"${typeSym(s)}.type"
    case TypeRepr.ThisType(_)                  => "this.type"
    case TypeRepr.SuperType(_, sup)            => tpe(sup)
    case TypeRepr.ConstantType(c)              => constant(c)
    case TypeRepr.AppliedType(tc, as)          => s"${tpe(tc)}[${as.map(tpe).mkString(", ")}]"
    case TypeRepr.AndType(l, r)                => s"${tpe(l)} & ${tpe(r)}"
    case TypeRepr.OrType(l, r)                 => s"${tpe(l)} | ${tpe(r)}"
    case TypeRepr.ByNameType(u)                => s"=> ${tpe(u)}"
    case TypeRepr.TypeBounds(TypeRepr.NoType, TypeRepr.NoType) => "?"
    case TypeRepr.TypeBounds(lo, hi) =>
      val l = if lo == TypeRepr.NoType then "" else s" >: ${tpe(lo)}"
      val h = if hi == TypeRepr.NoType then "" else s" <: ${tpe(hi)}"
      s"?$l$h"
    case TypeRepr.Refinement(p, _, _)          => tpe(p)
    case TypeRepr.MethodType(ps, res, _)       => s"(${ps.map((_, pt) => tpe(pt)).mkString(", ")}) => ${tpe(res)}"
    case TypeRepr.PolyType(_, res)             => tpe(res)
    case TypeRepr.TypeLambda(ps, body)         => s"[${ps.map(_._1).mkString(", ")}] =>> ${tpe(body)}"
    case TypeRepr.ParamRef(_, _)               => "?"

  // ---- constants ----
  private def constant(c: Constant): String = c match
    case Constant.BoolC(v)   => v.toString
    case Constant.ByteC(v)   => v.toString
    case Constant.ShortC(v)  => v.toString
    case Constant.IntC(v)    => v.toString
    case Constant.LongC(v)   => s"${v}L"
    case Constant.FloatC(v)  => s"${v}f"
    case Constant.DoubleC(v) => v.toString
    case Constant.CharC(v)   => s"'${escape(v.toString)}'"
    case Constant.StringC(v) => "\"" + escape(v) + "\""
    case Constant.NullC      => "null"
    case Constant.UnitC      => "()"
    case Constant.ClassOfC(t) => s"classOf[${tpe(t)}]"

  private def escape(s: String): String =
    s.flatMap {
      case '\\' => "\\\\"; case '"' => "\\\""; case '\n' => "\\n"; case '\r' => "\\r"; case '\t' => "\\t"
      case c    => c.toString
    }

object TirEmitter:
  /** Rename any field whose simple name collides with a method in the same class (legal in
    * Java, illegal in Scala) by suffixing `$field`. Renaming the symbol propagates to every
    * reference, since the emitter reads names from the symbol table. */
  def resolveMemberClashes(p: Program): Program =
    val renames = collection.mutable.Map[SymId, String]()
    def nm(id: SymId): String = p.symbolOf(id).map(_.name).getOrElse("")
    def scan(cd: Tree.ClassDef): Unit =
      val methodNames = cd.body.collect { case d: Tree.DefDef => nm(d.symbol) }.toSet
      cd.body.foreach {
        case v: Tree.ValDef if methodNames(nm(v.symbol)) => renames(v.symbol) = nm(v.symbol) + "$field"
        case c: Tree.ClassDef                            => scan(c)
        case _                                           => ()
      }
      cd.enumCases.foreach(_.body.foreach { case c: Tree.ClassDef => scan(c); case _ => () })
    p.units.foreach(scan)
    if renames.isEmpty then p
    else
      // also relax visibility: Java lets the enclosing class read a nested class's private
      // field (`point.x`); Scala does not, so a renamed clash-field must stay accessible.
      val syms = p.symbols.all.map(s =>
        renames.get(s.id).map(n => s.copy(name = n, flags = s.flags.copy(isPrivate = false, isProtected = false))).getOrElse(s)
      )
      new Program(p.units, SymbolTable(syms), p.xref)
