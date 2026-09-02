package balticporter.emit

import java.nio.file.{Files, Path}
import scala.meta.*

/** The member surface of INJECTED Scala files, parsed with scalameta.
  *
  * An emitted override of a dropped+injected parent must adopt the INJECTED parent's
  * parameter types, which may differ from what the TIR carries — the TIR holds the java-derived
  * type, while the injected file holds sge's hand-port API choice (e.g. `DynamicArray[? <: A]`
  * vs `DynamicArray[A]`). This reader extracts member signatures so the emitter can align them.
  *
  * Also feeds `calleeHasParens`: a call to a member of a dropped+injected type must follow
  * the injected member's arity, not the java arity.
  *
  * ==Kind==
  * CLAUDE.md section 1(a). The mechanism is universal — a fact about dropped+injected parents
  * and Scala, true of every codebase.
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

  /** Keyed by (ownerFqn, memberName, arity) for override alignment.
    * Multiple overloads at the same arity are kept as a list — the emitter
    * disambiguates by head type when needed. */
  /** What kind of declaration an injected type is — enough for a port map's `shape` column. */
  enum TypeForm:
    case Class, Trait, Object

  final case class Surface(
      members: Map[(String, String, Int), List[MemberSig]],
      /** Type parameter names of each injected type, for substitution.
        * e.g. `"sge.utils.Pool" -> List("A")` */
      typeParams: Map[String, List[String]] = Map.empty,
      /** The declaration kind of each injected type — the minimum a port map's `shape` column needs
        * so a dependent's `PublishedSurface.typeShape` can answer `Published` rather than `Unknown`.
        * Without this, a dropped+injected type's contract row carries no payload and every dependent
        * fails FATAL ("no declared base publishes a contract row"). */
      typeForms: Map[String, TypeForm] = Map.empty,
  ):
    def isEmpty: Boolean = members.isEmpty

    /** Render a minimal `TypeShape` payload for each injected type, in the porter-note `k=v` grammar.
      * Only `form=` is produced — a type the port drops and replaces has no constructor contract,
      * no statics list, and no parent list to publish. What matters is that the payload is NON-EMPTY
      * so `Surface.parseType` returns `Some(…)` and the dependent gets `Published` instead of
      * `Unknown`. */
    def renderedTypeShapes: Map[String, String] =
      typeForms.map { (fqn, form) =>
        val f = form match
          case TypeForm.Class  => "class"
          case TypeForm.Trait  => "trait"
          case TypeForm.Object => "object"
        fqn -> s"form=$f"
      }

    /** Look up the injected member matching this override, applying the type parameter
      * substitution from the child's `extends` clause.
      *
      * @param actualTypeArgs the type arguments the child passes to the injected parent,
      *                       rendered as strings by the emitter's `tpe()` */
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

    /** Does this member have parens in the injected file? */
    def memberHasParens(ownerFqn: String, memberName: String): Option[Boolean] =
      // Check all arities for this member; any match tells us the arity shape
      members.iterator
        .filter { case ((fqn, n, _), _) => fqn == ownerFqn && n == memberName }
        .flatMap(_._2)
        .map(_.hasParens)
        .nextOption()

  /** Substitute type parameter names in a rendered type string.
    * e.g. `substituteTypeParams("DynamicArray[? <: A]", Map("A" -> "T"))` -> `"DynamicArray[? <: T]"` */
  private def substituteTypeParams(rendered: String, subst: Map[String, String]): String =
    if subst.isEmpty then rendered
    else
      // Replace whole-word type parameter names: match at word boundaries
      subst.foldLeft(rendered) { case (s, (from, to)) =>
        s.replaceAll(s"\\b${java.util.regex.Pattern.quote(from)}\\b", java.util.regex.Matcher.quoteReplacement(to))
      }

  val Empty: Surface = Surface(Map.empty)

  /** Parse all `.scala` files under the given injection roots and extract member signatures.
    *
    * Reuses the same scalameta parser the `ApiParityCheck` / `SkeletonDiff` path does
    * (`DESIGN.md` §8.23). */
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

  /** Extract the package name from a parsed Scala source tree. */
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
