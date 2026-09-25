package balticporter.runner

import scala.meta.*

/** The surface code outside a type can reach, read off EMITTED Scala: every declaration that is not plain `private`, keyed by where it is declared (`T#m` in the class, `T.m` in the companion),
  * rendered without parameter names or bodies. Two translations of one type compile against the same shared code only where these agree; package-private members count, because shared code in the
  * package reaches them.
  */
object RowSurface:

  /** one member whose signatures differ, overloads sorted; an empty side means the member is absent there. */
  final case class Difference(member: String, main: List[String], row: List[String]):
    def render: String =
      def side(s: List[String]) = if s.isEmpty then "absent" else s.mkString(" | ")
      s"$member — main: ${side(main)}; row: ${side(row)}"

  /** member key → its signatures, sorted; `Left` when the text does not parse. */
  def of(source: String, label: String): Either[String, Map[String, List[String]]] =
    dialects.Scala3(Input.VirtualFile(label, source)).parse[Source] match
      case Parsed.Success(tree) => Right(collect(tree))
      case e: Parsed.Error => Left(s"$label: ${e.message}")

  def diff(main: Map[String, List[String]], row: Map[String, List[String]]): List[Difference] =
    (main.keySet ++ row.keySet).toList.sorted.flatMap { k =>
      val a = main.getOrElse(k, Nil)
      val b = row.getOrElse(k, Nil)
      Option.when(a != b)(Difference(k, a, b))
    }

  private def collect(tree: Tree): Map[String, List[String]] =
    val out = collection.mutable.Map.empty[String, List[String]]
    def add(key: String, sig: String): Unit = out(key) = sig :: out.getOrElse(key, Nil)

    def hidden(mods: List[Mod]): Boolean = mods.exists {
      case Mod.Private(_: Name.Anonymous) | Mod.Private(_: Term.This) => true
      case _                                                          => false
    }

    def modsOf(mods: List[Mod]): String =
      mods.collect {
        case Mod.Private(w)   => s"private[${w.syntax}] "
        case Mod.Protected(w) => if w.syntax.isEmpty then "protected " else s"protected[${w.syntax}] "
        case _: Mod.Final    => "final "
        case _: Mod.Sealed   => "sealed "
        case _: Mod.Abstract => "abstract "
        case _: Mod.Case     => "case "
        case _: Mod.Implicit => "implicit "
        case _: Mod.Lazy     => "lazy "
        case _: Mod.Opaque   => "opaque "
      }.mkString

    def clause(c: Term.ParamClause): String =
      val using = c.mod.fold("")(m => s"${m.syntax} ")
      c.values.map(p => p.decltpe.fold("?")(_.syntax) + (if p.default.isDefined then " = ?" else "")).mkString(s"($using", ", ", ")")

    def groups(gs: List[Member.ParamClauseGroup]): String =
      gs.map(g => g.tparamClause.syntax + g.paramClauses.map(clause).mkString).mkString

    def join(owner: String, sep: String, name: String): String = if owner.isEmpty then name else s"$owner$sep$name"

    def template(kind: String, mods: List[Mod], name: String, tparams: String, ctor: Option[Ctor.Primary], templ: Template, owner: String, sep: String): Unit =
      if !hidden(mods) then
        val self    = join(owner, sep, name)
        val params  = ctor.fold("")(c => (if c.mods.isEmpty then "" else " " + modsOf(c.mods).trim) + c.paramClauses.map(clause).mkString)
        val parents = templ.inits.map(_.tpe.syntax)
        add(
          self,
          s"${modsOf(mods)}$kind $name$tparams$params" + (if parents.isEmpty then "" else parents.mkString(" extends ", " with ", ""))
        )
        ctor.foreach(
          _.paramClauses.flatMap(_.values).foreach { p =>
            p.mods.collectFirst { case _: Mod.ValParam => "val"; case _: Mod.VarParam => "var" }.filterNot(_ => hidden(p.mods)).foreach { kw =>
              add(s"$self#${p.name.value}", s"${modsOf(p.mods)}$kw ${p.name.value}: ${p.decltpe.fold("?")(_.syntax)}")
            }
          }
        )
        val inner = if kind == "object" then "." else "#"
        templ.body.stats.foreach(walk(_, self, inner))

    def value(kw: String, mods: List[Mod], pats: List[Pat], tpe: Option[Type], rhs: Option[Term], owner: String, sep: String): Unit =
      if !hidden(mods) then
        val shown = tpe.map(t => s": ${t.syntax}").orElse(rhs.collect { case l: Lit => s" = ${l.syntax}" }).getOrElse(": ?")
        pats.collect { case p: Pat.Var => p.name.value }.foreach(n => add(join(owner, sep, n), s"${modsOf(mods)}$kw $n$shown"))

    def walk(t: Tree, owner: String, sep: String): Unit = t match
      case s: Source                => s.stats.foreach(walk(_, owner, sep))
      case p: Pkg                   => p.body.stats.foreach(walk(_, join(owner, ".", p.ref.syntax), "."))
      case d: Defn.Class            => template("class", d.mods, d.name.value, d.tparamClause.syntax, Some(d.ctor), d.templ, owner, sep)
      case d: Defn.Trait            => template("trait", d.mods, d.name.value, d.tparamClause.syntax, Some(d.ctor).filter(_.paramClauses.nonEmpty), d.templ, owner, sep)
      case d: Defn.Enum             => template("enum", d.mods, d.name.value, d.tparamClause.syntax, Some(d.ctor), d.templ, owner, sep)
      case d: Defn.Object           => template("object", d.mods, d.name.value, "", scala.None, d.templ, owner, sep)
      case d: Defn.EnumCase         => add(join(owner, sep, d.name.value), s"case ${d.name.value}${d.ctor.paramClauses.map(clause).mkString}")
      case d: Defn.RepeatedEnumCase => d.cases.foreach(c => add(join(owner, sep, c.value), s"case ${c.value}"))
      case d: Defn.Def              =>
        if !hidden(d.mods) then
          add(
            join(owner, sep, d.name.value),
            s"${modsOf(d.mods)}def ${d.name.value}${groups(d.paramClauseGroups)}: ${d.decltpe.fold("?")(_.syntax)}"
          )
      case d: Decl.Def =>
        if !hidden(d.mods) then
          add(
            join(owner, sep, d.name.value),
            s"${modsOf(d.mods)}def ${d.name.value}${groups(d.paramClauseGroups)}: ${d.decltpe.syntax}"
          )
      case d: Ctor.Secondary =>
        if !hidden(d.mods) then add(join(owner, sep, "this"), s"${modsOf(d.mods)}def this${d.paramClauses.map(clause).mkString}")
      case d: Defn.Val            => value("val", d.mods, d.pats, d.decltpe, Some(d.rhs), owner, sep)
      case d: Defn.Var            => value("var", d.mods, d.pats, d.decltpe, d.body match { case l: Lit => Some(l); case _ => scala.None }, owner, sep)
      case d: Decl.Val            => value("val", d.mods, d.pats, Some(d.decltpe), scala.None, owner, sep)
      case d: Decl.Var            => value("var", d.mods, d.pats, Some(d.decltpe), scala.None, owner, sep)
      case d: Defn.Type           => if !hidden(d.mods) then add(join(owner, sep, d.name.value), s"${modsOf(d.mods)}type ${d.name.value}${d.tparamClause.syntax} = ${d.body.syntax}")
      case d: Decl.Type           => if !hidden(d.mods) then add(join(owner, sep, d.name.value), s"${modsOf(d.mods)}type ${d.name.value}${d.tparamClause.syntax}${d.bounds.syntax}")
      case d: Defn.GivenAlias     => if !hidden(d.mods) then add(join(owner, sep, s"given ${d.name.syntax}"), s"given ${d.decltpe.syntax}")
      case e: Defn.ExtensionGroup => walk(e.body, owner, sep)
      case b: Term.Block          => b.stats.foreach(walk(_, owner, sep))
      case _ => ()

    walk(tree, "", ".")
    out.view.mapValues(_.sorted).toMap
