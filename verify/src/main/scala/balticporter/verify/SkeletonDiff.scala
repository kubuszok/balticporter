package balticporter.verify

import scala.meta.*

/** Declaration-skeleton comparison between engine output and the accepted hand port.
  *
  * Compares the member surface (kind + name + arity, nested via owner paths), not
  * types or bodies — Tier 2/3 idiom passes (DataView, Nullable, package renames)
  * change types everywhere, but the *shape* of a faithful port must match modulo a
  * small set of known idioms. Divergences are classified (M1 gate, PLAN.md §13):
  *
  *   - SKELETON_EQUAL — surfaces identical
  *   - IDIOM          — every difference explained by a known hand-port idiom
  *                      (getter/setter collapse today; grows as idioms are cataloged)
  *   - DIFF           — unexplained differences (missing rule, rule bug, or hand-port
  *                      divergence to investigate)
  */
object SkeletonDiff:

  final case class Member(path: String, kind: String, name: String, arity: Int):
    def key: String = s"$path|$kind|$name/$arity"
    override def toString: String = s"$path: $kind $name/$arity"

  def parseSkeleton(source: String, label: String): Either[String, List[Member]] =
    val input = Input.VirtualFile(label, source)
    dialects.Scala3(input).parse[Source] match
      case Parsed.Success(tree) => Right(collect(tree))
      case e: Parsed.Error      => Left(s"$label: ${e.message}")

  private def collect(tree: Tree): List[Member] =
    val out = List.newBuilder[Member]
    def walkTemplate(templ: Template, path: String): Unit =
      templ.body.stats.foreach(walk(_, path))
    def ctorParams(d: Defn.Class, path: String): Unit =
      val isCase = d.mods.exists(_.isInstanceOf[Mod.Case])
      d.ctor.paramClauses.flatMap(_.values).foreach { p =>
        val kind = p.mods.collectFirst {
          case _: Mod.VarParam => "var"
          case _: Mod.ValParam => "val"
        }
        kind.orElse(if isCase then Some("val") else None).foreach { k =>
          out += Member(s"$path/${d.name.value}", k, p.name.value, 0)
        }
      }
    def walk(t: Tree, path: String): Unit = t match
      case d: Defn.Class =>
        out += Member(path, "class", d.name.value, 0)
        ctorParams(d, path)
        walkTemplate(d.templ, s"$path/${d.name.value}")
      case d: Defn.Trait =>
        out += Member(path, "trait", d.name.value, 0)
        walkTemplate(d.templ, s"$path/${d.name.value}")
      case d: Defn.Object =>
        out += Member(path, "object", d.name.value, 0)
        walkTemplate(d.templ, s"$path/${d.name.value}$$")
      case d: Defn.Enum =>
        out += Member(path, "enum", d.name.value, 0)
        walkTemplate(d.templ, s"$path/${d.name.value}")
      case d: Defn.Def =>
        out += Member(path, "def", d.name.value, arity(d.paramClauseGroups.flatMap(_.paramClauses)))
      case d: Decl.Def =>
        out += Member(path, "def", d.name.value, arity(d.paramClauseGroups.flatMap(_.paramClauses)))
      case d: Defn.Val =>
        d.pats.foreach { case p: Pat.Var => out += Member(path, "val", p.name.value, 0); case _ => () }
      case d: Decl.Val =>
        d.pats.foreach { case p: Pat.Var => out += Member(path, "val", p.name.value, 0); case _ => () }
      case d: Defn.Var =>
        d.pats.foreach { case p: Pat.Var => out += Member(path, "var", p.name.value, 0); case _ => () }
      case d: Decl.Var =>
        d.pats.foreach { case p: Pat.Var => out += Member(path, "var", p.name.value, 0); case _ => () }
      case _: Defn.Type | _: Decl.Type => ()
      case other => other.children.foreach(walk(_, path))
    walk(tree, "")
    out.result().sortBy(_.key)

  private def arity(clauses: List[Term.ParamClause]): Int =
    clauses.map(_.values.length).sum

  enum Status:
    case SkeletonEqual, Idiom, HandAdditions, Diff

  final case class Result(
      status: Status,
      missingInHand: List[Member],  // engine has, hand port lacks
      extraInHand: List[Member],    // hand port has, engine lacks
      explained: List[String],
  )

  def compare(engine: List[Member], hand: List[Member]): Result =
    val engineKeys = engine.map(_.key).toSet
    val handKeys = hand.map(_.key).toSet
    var missing = engine.filterNot(m => handKeys.contains(m.key))
    var extra = hand.filterNot(m => engineKeys.contains(m.key))
    val explained = List.newBuilder[String]

    // Idiom: getter/setter collapse — engine `def getX/0` where the hand port (anywhere
    // in its full skeleton, including val class params) exposes the property `x`.
    def propName(n: String, prefix: String): Option[String] =
      if n.length > prefix.length && n.startsWith(prefix) then
        Some(n(prefix.length).toLower.toString + n.drop(prefix.length + 1))
      else None
    def handHasProp(path: String, p: String): Boolean =
      hand.exists(h => h.path == path && (h.name == p || h.name == p + "_="))
    val (getterLike, restMissing) = missing.partition { m =>
      m.kind == "def" && m.arity == 0 &&
        List("get", "is").flatMap(propName(m.name, _)).exists(handHasProp(m.path, _))
    }
    getterLike.foreach(g => explained += s"getter-collapse: ${g.name}")
    val (setterLike, restMissing2) = restMissing.partition { m =>
      m.kind == "def" && m.arity == 1 &&
        propName(m.name, "set").exists(handHasProp(m.path, _))
    }
    setterLike.foreach(s => explained += s"setter-collapse: ${s.name}")
    missing = restMissing2
    // the hand-port property members that explain collapsed getters/setters are expected
    val explainedProps = (getterLike ++ setterLike).flatMap { m =>
      List("get", "is", "set").flatMap(propName(m.name, _)).flatMap(p => List(p, p + "_="))
    }.toSet
    extra = extra.filterNot(h => explainedProps.contains(h.name))

    // Idiom: mutability narrowed — engine `var x` (Java non-final), hand port `val x`.
    val (varToVal, restMissing3) = missing.partition { m =>
      (m.kind == "var" || m.kind == "val") &&
        extra.exists(h => h.path == m.path && h.name == m.name && Set("val", "var").contains(h.kind))
    }
    varToVal.foreach(v => explained += s"mutability: ${v.name}")
    missing = restMissing3
    val varToValNames = varToVal.map(v => (v.path, v.name)).toSet
    extra = extra.filterNot(h => varToValNames.contains((h.path, h.name)))

    val status =
      if missing.isEmpty && extra.isEmpty then
        if explained.result().isEmpty then Status.SkeletonEqual else Status.Idiom
      else if missing.isEmpty then
        // the engine lost nothing; the hand port added members (factories, helpers) —
        // "equal or better" territory, but listed for review
        Status.HandAdditions
      else Status.Diff
    Result(status, missing, extra, explained.result())
