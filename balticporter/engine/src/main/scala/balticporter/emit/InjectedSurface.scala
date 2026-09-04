package balticporter.emit

import java.nio.file.{Files, Path}
import scala.meta.*

/** Member surface of injected Scala files (parsed with scalameta).
  *
  * Extracts signatures so the emitter can align overrides of dropped+injected
  * parents and follow their member arity (hasParens).
  */
object InjectedSurface:

  /** One parameter's rendered type as it appears in the injected Scala source. */
  final case class ParamType(rendered: String)

  /** One member's signature in the injected file. */
  final case class MemberSig(
      ownerFqn: String,
      name: String,
      paramTypes: List[List[ParamType]],
      hasParens: Boolean,
      returnType: Option[String],
  ):
    def arity: Int = paramTypes.flatten.size

  /** Declaration kind of an injected type, for the port map's `shape` column. */
  enum TypeForm:
    case Class, Trait, Object

  final case class Surface(
      members: Map[(String, String, Int), List[MemberSig]],
      /** Type parameter names per injected type, for substitution in overrides. */
      typeParams: Map[String, List[String]] = Map.empty,
      /** Declaration kind per injected type, so a dependent's port map gets `Published`. */
      typeForms: Map[String, TypeForm] = Map.empty,
  ):
    def isEmpty: Boolean = members.isEmpty

    /** Renders a minimal `form=` payload per injected type for port-map type-shape rows. */
    def renderedTypeShapes: Map[String, String] =
      typeForms.map { (fqn, form) =>
        val f = form match
          case TypeForm.Class  => "class"
          case TypeForm.Trait  => "trait"
          case TypeForm.Object => "object"
        fqn -> s"form=$f"
      }

    /** Look up the injected member, substituting the child's actual type args. */
    def lookup(ownerFqn: String, memberName: String, arity: Int,
               actualTypeArgs: List[String] = Nil): Option[MemberSig] =
      members.get((ownerFqn, memberName, arity)).flatMap(_.headOption).map { sig =>
        val tparams = typeParams.getOrElse(ownerFqn, Nil)
        if tparams.isEmpty || actualTypeArgs.isEmpty then sig
        else
          val subst = tparams.zip(actualTypeArgs).toMap
          sig.copy(paramTypes = sig.paramTypes.map(_.map(pt =>
            ParamType(substituteTypeParams(pt.rendered, subst)))))
      }

    /** Whether this member has parens in the injected file. */
    def memberHasParens(ownerFqn: String, memberName: String): Option[Boolean] =
      members.iterator
        .filter { case ((fqn, n, _), _) => fqn == ownerFqn && n == memberName }
        .flatMap(_._2)
        .map(_.hasParens)
        .nextOption()

  /** Substitute type parameter names in a rendered type string (whole-word match). */
  private def substituteTypeParams(rendered: String, subst: Map[String, String]): String =
    if subst.isEmpty then rendered
    else
      // Replace whole-word type parameter names: match at word boundaries
      subst.foldLeft(rendered) { case (s, (from, to)) =>
        s.replaceAll(s"\\b${java.util.regex.Pattern.quote(from)}\\b", java.util.regex.Matcher.quoteReplacement(to))
      }

  val Empty: Surface = Surface(Map.empty)

  /** Parse `.scala` files under the given roots and extract member signatures. */
  def fromRoots(roots: List[Path]): Surface =
    val sigs = List.newBuilder[MemberSig]
    val tparams = collection.mutable.Map[String, List[String]]()
    val forms   = collection.mutable.Map[String, TypeForm]()
    for
      root <- roots if Files.exists(root)
      src  <- scalaSources(root)
    do
      val text = new String(Files.readAllBytes(src), "UTF-8")
      val input = Input.VirtualFile(src.toString, text)
      dialects.Scala3(input).parse[Source] match
        case Parsed.Success(tree) =>
          val pkg = extractPackage(tree)
          collectMembers(tree, pkg, sigs, tparams, forms)
        case _ => () // parse failure: nothing to extract
    val all = sigs.result()
    val grouped = all.groupBy(m => (m.ownerFqn, m.name, m.arity))
    Surface(grouped, tparams.toMap, forms.toMap)

  private def scalaSources(root: Path): List[Path] =
    import scala.jdk.CollectionConverters.*
    if !Files.isDirectory(root) then Nil
    else
      Files.walk(root).iterator().asScala
        .filter(p => Files.isRegularFile(p) && p.toString.endsWith(".scala"))
        .toList

  /** Package name from a parsed Scala source tree. */
  private def extractPackage(tree: Source): String =
    tree.stats.collectFirst {
      case Pkg(ref, _) => ref.syntax
    }.getOrElse("")

  private def collectMembers(
      tree: Tree, pkg: String,
      out: collection.mutable.Builder[MemberSig, List[MemberSig]],
      tparams: collection.mutable.Map[String, List[String]],
      forms: collection.mutable.Map[String, TypeForm],
  ): Unit =
    def walkTemplate(templ: Template, fqn: String): Unit =
      templ.body.stats.foreach(walk(_, fqn))

    def fqnOf(parent: String, name: String): String =
      if parent.isEmpty then name else s"$parent.$name"

    def extractTParams(paramClause: scala.meta.Type.ParamClause): List[String] =
      paramClause.values.map(_.name.value)

    def walk(t: Tree, fqn: String): Unit = t match
      case Pkg(_, stats) =>
        stats.foreach(walk(_, fqn))
      case d: Defn.Class =>
        val childFqn = fqnOf(fqn, d.name.value)
        val tp = extractTParams(d.tparamClause)
        if tp.nonEmpty then tparams(childFqn) = tp
        forms(childFqn) = TypeForm.Class
        walkTemplate(d.templ, childFqn)
      case d: Defn.Trait =>
        val childFqn = fqnOf(fqn, d.name.value)
        val tp = extractTParams(d.tparamClause)
        if tp.nonEmpty then tparams(childFqn) = tp
        forms(childFqn) = TypeForm.Trait
        walkTemplate(d.templ, childFqn)
      case d: Defn.Object =>
        val childFqn = fqnOf(fqn, d.name.value)
        forms(childFqn) = TypeForm.Object
        walkTemplate(d.templ, childFqn)
      case d: Defn.Def =>
        val nonUsingClauses = d.paramClauseGroups.flatMap(_.paramClauses)
          .filterNot(pc => pc.values.nonEmpty && pc.values.forall(_.mods.exists(_.isInstanceOf[Mod.Using])))
        val paramClauses = nonUsingClauses.map { pc =>
          pc.values.map(p => ParamType(p.decltpe.map(_.syntax).getOrElse("?")))
        }
        // hasParens: true if ANY non-using clause exists (even an empty `()`)
        val hasP = nonUsingClauses.nonEmpty
        val ret = d.decltpe.map(_.syntax)
        out += MemberSig(fqn, d.name.value, paramClauses, hasP, ret)
      case d: Decl.Def =>
        val nonUsingClauses = d.paramClauseGroups.flatMap(_.paramClauses)
          .filterNot(pc => pc.values.nonEmpty && pc.values.forall(_.mods.exists(_.isInstanceOf[Mod.Using])))
        val paramClauses = nonUsingClauses.map { pc =>
          pc.values.map(p => ParamType(p.decltpe.map(_.syntax).getOrElse("?")))
        }
        val hasP = nonUsingClauses.nonEmpty
        val ret = Some(d.decltpe.syntax)
        out += MemberSig(fqn, d.name.value, paramClauses, hasP, ret)
      case d: Defn.Val =>
        d.pats.foreach {
          case p: Pat.Var =>
            out += MemberSig(fqn, p.name.value, Nil, false, d.decltpe.map(_.syntax))
          case _ => ()
        }
      case d: Decl.Val =>
        d.pats.foreach {
          case p: Pat.Var =>
            out += MemberSig(fqn, p.name.value, Nil, false, Some(d.decltpe.syntax))
          case _ => ()
        }
      case d: Defn.Var =>
        d.pats.foreach {
          case p: Pat.Var =>
            out += MemberSig(fqn, p.name.value, Nil, false, d.decltpe.map(_.syntax))
          case _ => ()
        }
      case d: Decl.Var =>
        d.pats.foreach {
          case p: Pat.Var =>
            out += MemberSig(fqn, p.name.value, Nil, false, Some(d.decltpe.syntax))
          case _ => ()
        }
      case other =>
        other.children.foreach(walk(_, fqn))

    walk(tree, pkg)
