package balticporter.verify

import balticporter.core.ParityRef
import balticporter.tir.CheckReport

import java.nio.file.{Files, Path}
import scala.jdk.CollectionConverters.*
import scala.meta.*

/** Compare the emitted port's public surface against a hand-written reference port. // CLAUDE.md §1(b)
  *
  * Parses both sides with scalameta, classifies each divergence into a family, reports per-family
  * lanes. Empty/absent `PortManifest.parity` makes the check a no-op. Each family carries a §1
  * classification so an agent knows whether the fix is engine, manifest, or library-specific. */
object ApiParityCheck:

  /** The family slugs. Each becomes `api-parity(<slug>)`. */
  val Families: List[String] = List(
    "accessor",           // getter/setter collapse (getX/setX vs x/x_=) or paren-vs-parenless
    "static-placement",   // class vs companion placement
    "mutability",         // val vs var vs def
    "rename",             // same shape, different name (usually a known rename)
    "visibility",         // different access level
    "hand-port-extra",    // declared only in the hand port (hand port added API)
    "port-extra",         // declared only in the emitted port (java the hand port skipped)
    "null-model",         // T | Null vs Nullable[T] vs Option[T] vs bare T
    "collection-retarget", // collection family type difference (java.util.* vs scala.collection.*)
    "opaque",             // primitive vs non-primitive where hand port has opaque type
    "operator",           // symbolic name / @targetName on one side
    "factory",            // companion apply/from/wrap vs public constructor; private ctor
    "file-merge",         // same FQN in different file/nesting (informational)
    "signature",          // same name+arity, any other type difference (catch-all)
    "unclassified",       // everything else — the work list
  )

  def lane(family: String): String = s"api-parity($family)"

  val AllLanes: Set[String] = Families.map(lane).toSet

  val Classification: Map[String, String] = Map(
    "accessor" -> (
      "§1(a) ENGINE: the engine emits java-shaped accessors (getX/setX/isX) where the hand port " +
        "collapsed them to scala properties. An idiom phase in the engine produces this shape."),
    "static-placement" -> (
      "§1(a) ENGINE: the engine places java statics in the companion; the hand port may place " +
        "members freely between the class and its companion. Informational."),
    "mutability" -> (
      "§1(a) ENGINE: val vs var vs def drift between the two ports. Usually benign " +
        "(the hand port narrowed mutability)."),
    "rename" -> (
      "§1(a) ENGINE or §1(b) CONFIGURED: the two ports use different names for the same member. " +
        "Known renames (from packageRenames or typeRenames) are expected; others may be a " +
        "missing rename rule or a hand-port freedom."),
    "visibility" -> (
      "§1(a) ENGINE or §1(c) LIBRARY-SPECIFIC: the two ports disagree on access level. " +
        "Often a hand-port decision to widen or narrow access."),
    "hand-port-extra" -> (
      "§1(c) LIBRARY-SPECIFIC or INFORMATIONAL: the hand port declares members the emitted port " +
        "does not have. These are hand-port additions (factory methods, helpers, redesigned APIs) " +
        "that a mechanical port cannot and should not reproduce."),
    "port-extra" -> (
      "§1(a) ENGINE or §1(b) CONFIGURED: the emitted port declares members the hand port does " +
        "not have. These are java members the hand port skipped — either deliberately (drops) or " +
        "because it redesigned the API."),
    "null-model" -> (
      "§1(b) CONFIGURED: NullabilityTransform target — the engine's null-model phase produces " +
        "one spelling (T | Null, Nullable[T], Option[T]), the hand port uses another."),
    "collection-retarget" -> (
      "§1(b) CONFIGURED: TypeRedirect/CollectionsTransform retarget — the engine retargets JDK " +
        "collection types, the hand port may use different collection targets."),
    "opaque" -> (
      "§1(c) LIBRARY-SPECIFIC: OpaqueSpec — the hand port uses opaque types for primitives that " +
        "the emitted port keeps as the underlying type."),
    "operator" -> (
      "§1(b) CONFIGURED: MemberRename symbolic — the hand port uses symbolic operator names " +
        "where the emitted port uses the java name, or one side has @targetName."),
    "factory" -> (
      "§1(b) CONFIGURED: CtorFunnel factory policy — the hand port has companion " +
        "apply/from/wrap/of/create methods instead of public constructors, or vice versa."),
    "file-merge" -> (
      "INFORMATIONAL: same FQN in a different file or nesting — no behavioural difference, " +
        "informational only."),
    "signature" -> (
      "UNKNOWN: same name and arity but different type signature — the catch-all for type-level " +
        "differences not yet classified into a specific family."),
    "unclassified" -> (
      "UNKNOWN: a divergence that fits no recognised family. This is the work list — each row " +
        "is either a missing classifier in this check or a real divergence to investigate."),
  )

  /** A declaration on the public surface. Both sides rendered through the same `.syntax`. */
  final case class SurfaceDecl(
      path: String,
      kind: String,
      name: String,
      arity: Int,
      paramTypes: List[String] = Nil,
      resultType: String = "",
      typeParams: String = "",
      parents: List[String] = Nil,
      modifiers: Set[String] = Set.empty,
      accessLevel: String = "public",
      targetName: String = "",
  ):
    /** Structural key: path + kind-class + name + arity. val/var/param grouped together. */
    def matchKey: String =
      val kc = kindClass
      s"$path|$kc|$name/$arity"

    /** val/var/param are one class, def is another. */
    private def kindClass: String = kind match
      case "val" | "var" | "param" => "prop"
      case other                    => other

    override def toString: String = s"$path: $kind $name/$arity"

  private def renderType(tpe: Option[Type]): String =
    tpe.map(_.syntax).getOrElse("")

  private def renderTypeParams(tparams: List[Type.Param]): String =
    if tparams.isEmpty then ""
    else tparams.map(_.syntax).mkString("[", ", ", "]")

  private def extractModifiers(mods: List[Mod]): Set[String] =
    mods.flatMap {
      case _: Mod.Final       => Some("final")
      case _: Mod.Override    => Some("override")
      case _: Mod.Inline      => Some("inline")
      case _: Mod.Abstract    => Some("abstract")
      case _: Mod.Sealed      => Some("sealed")
      case _: Mod.Open        => Some("open")
      case _: Mod.Lazy        => Some("lazy")
      case _: Mod.Implicit    => Some("implicit")
      case _: Mod.Opaque      => Some("opaque")
      case _: Mod.Case        => Some("case")
      case _: Mod.Transparent => Some("transparent")
      case _                  => None
    }.toSet

  private def extractAccessLevel(mods: List[Mod]): String =
    mods.collectFirst {
      case p: Mod.Private =>
        p.within match
          case ref: Name if ref.value.nonEmpty => s"private[${ref.value}]"
          case _                               => "private"
      case p: Mod.Protected =>
        p.within match
          case ref: Name if ref.value.nonEmpty => s"protected[${ref.value}]"
          case _                               => "protected"
    }.getOrElse("public")

  private def extractTargetName(mods: List[Mod]): String =
    mods.collectFirst {
      case annot: Mod.Annot =>
        annot.init.tpe match
          case n: Type.Name if n.value == "targetName" =>
            annot.init.argClauses.flatMap(_.values).collectFirst {
              case lit: Lit.String => lit.value
            }
          case _ => None
    }.flatten.getOrElse("")

  private def extractParents(templ: Template): List[String] =
    templ.inits.map(_.tpe.syntax)

  /** Parse all `.scala` files under the given roots into surface declarations. */
  def parseSurface(roots: List[Path]): Either[String, List[SurfaceDecl]] =
    val files = roots.flatMap { root =>
      if !Files.isDirectory(root) then Nil
      else Files.walk(root).iterator().asScala
        .filter(p => p.toString.endsWith(".scala") && Files.isRegularFile(p))
        .toList
    }
    val errors = List.newBuilder[String]
    val decls  = List.newBuilder[SurfaceDecl]
    files.foreach { f =>
      val text = Files.readString(f)
      val label = f.toString
      val input = Input.VirtualFile(label, text)
      dialects.Scala3(input).parse[Source] match
        case Parsed.Success(tree) =>
          collectDecls(tree, "", decls)
        case e: Parsed.Error =>
          errors += s"$label: ${e.message}"
    }
    val errs = errors.result()
    if errs.nonEmpty then Left(errs.mkString("; "))
    // `$` in a member name is phase-minted or scalac-internal, never API surface
    else Right(decls.result().filterNot(_.name.contains('$')).sortBy(d => (d.path, d.kind, d.name, d.arity)))

  private def collectDecls(tree: Tree, path: String, out: collection.mutable.Builder[SurfaceDecl, List[SurfaceDecl]]): Unit =
    /** Public or protected -- both are API surface for subclassing. */
    def isAccessible(mods: List[Mod]): Boolean =
      !mods.exists {
        case _: Mod.Private => true
        case _              => false
      }

    def walkTemplate(templ: Template, path: String): Unit =
      templ.body.stats.foreach(walk(_, path))

    def ctorParams(name: String, isCase: Boolean, ctor: Ctor.Primary, path: String): Unit =
      ctor.paramClauses.flatMap(_.values).foreach { p =>
        val paramKind = p.mods
          .collectFirst {
            case _: Mod.VarParam => "var"
            case _: Mod.ValParam => "val"
          }
          .orElse(if isCase then Some("val") else None)
        paramKind.foreach { k =>
          if isAccessible(p.mods) then
            out += SurfaceDecl(
              path = s"$path/$name",
              kind = k,
              name = p.name.value,
              arity = 0,
              resultType = renderType(p.decltpe),
              modifiers = extractModifiers(p.mods),
              accessLevel = extractAccessLevel(p.mods),
            )
        }
      }

    def defParamTypes(clauses: List[Term.ParamClause]): List[String] =
      clauses.flatMap(_.values).map(p => renderType(p.decltpe))

    def walk(t: Tree, path: String): Unit = t match
      case d: Defn.Class if isAccessible(d.mods) =>
        out += SurfaceDecl(
          path = path,
          kind = "class",
          name = d.name.value,
          arity = 0,
          typeParams = renderTypeParams(d.tparamClause.values),
          parents = extractParents(d.templ),
          modifiers = extractModifiers(d.mods),
          accessLevel = extractAccessLevel(d.mods),
        )
        ctorParams(d.name.value, d.mods.exists(_.isInstanceOf[Mod.Case]), d.ctor, path)
        walkTemplate(d.templ, s"$path/${d.name.value}")
      case d: Defn.Trait if isAccessible(d.mods) =>
        out += SurfaceDecl(
          path = path,
          kind = "trait",
          name = d.name.value,
          arity = 0,
          typeParams = renderTypeParams(d.tparamClause.values),
          parents = extractParents(d.templ),
          modifiers = extractModifiers(d.mods),
          accessLevel = extractAccessLevel(d.mods),
        )
        walkTemplate(d.templ, s"$path/${d.name.value}")
      case d: Defn.Object if isAccessible(d.mods) =>
        out += SurfaceDecl(
          path = path,
          kind = "object",
          name = d.name.value,
          arity = 0,
          parents = extractParents(d.templ),
          modifiers = extractModifiers(d.mods),
          accessLevel = extractAccessLevel(d.mods),
        )
        walkTemplate(d.templ, s"$path/${d.name.value}$$")
      case d: Defn.Enum if isAccessible(d.mods) =>
        out += SurfaceDecl(
          path = path,
          kind = "enum",
          name = d.name.value,
          arity = 0,
          typeParams = renderTypeParams(d.tparamClause.values),
          parents = extractParents(d.templ),
          modifiers = extractModifiers(d.mods),
          accessLevel = extractAccessLevel(d.mods),
        )
        ctorParams(d.name.value, isCase = false, d.ctor, path)
        walkTemplate(d.templ, s"$path/${d.name.value}")
      case d: Defn.EnumCase =>
        out += SurfaceDecl(path, "case", d.name.value, 0)
      case d: Defn.RepeatedEnumCase =>
        d.cases.foreach(c => out += SurfaceDecl(path, "case", c.value, 0))
      case d: Defn.Def if isAccessible(d.mods) =>
        val clauses = d.paramClauseGroups.flatMap(_.paramClauses)
        out += SurfaceDecl(
          path = path,
          kind = "def",
          name = d.name.value,
          arity = defArity(clauses),
          paramTypes = defParamTypes(clauses),
          resultType = renderType(d.decltpe),
          typeParams = renderTypeParams(d.paramClauseGroups.flatMap(_.tparamClause.values)),
          modifiers = extractModifiers(d.mods),
          accessLevel = extractAccessLevel(d.mods),
          targetName = extractTargetName(d.mods),
        )
      case d: Decl.Def if isAccessible(d.mods) =>
        val clauses = d.paramClauseGroups.flatMap(_.paramClauses)
        out += SurfaceDecl(
          path = path,
          kind = "def",
          name = d.name.value,
          arity = defArity(clauses),
          paramTypes = defParamTypes(clauses),
          resultType = d.decltpe.syntax,
          typeParams = renderTypeParams(d.paramClauseGroups.flatMap(_.tparamClause.values)),
          modifiers = extractModifiers(d.mods),
          accessLevel = extractAccessLevel(d.mods),
          targetName = extractTargetName(d.mods),
        )
      case d: Defn.Val if isAccessible(d.mods) =>
        d.pats.foreach {
          case p: Pat.Var =>
            out += SurfaceDecl(
              path = path,
              kind = "val",
              name = p.name.value,
              arity = 0,
              resultType = renderType(d.decltpe),
              modifiers = extractModifiers(d.mods),
              accessLevel = extractAccessLevel(d.mods),
            )
          case _ => ()
        }
      case d: Decl.Val if isAccessible(d.mods) =>
        d.pats.foreach {
          case p: Pat.Var =>
            out += SurfaceDecl(
              path = path,
              kind = "val",
              name = p.name.value,
              arity = 0,
              resultType = d.decltpe.syntax,
              modifiers = extractModifiers(d.mods),
              accessLevel = extractAccessLevel(d.mods),
            )
          case _ => ()
        }
      case d: Defn.Var if isAccessible(d.mods) =>
        d.pats.foreach {
          case p: Pat.Var =>
            out += SurfaceDecl(
              path = path,
              kind = "var",
              name = p.name.value,
              arity = 0,
              resultType = renderType(d.decltpe),
              modifiers = extractModifiers(d.mods),
              accessLevel = extractAccessLevel(d.mods),
            )
          case _ => ()
        }
      case d: Decl.Var if isAccessible(d.mods) =>
        d.pats.foreach {
          case p: Pat.Var =>
            out += SurfaceDecl(
              path = path,
              kind = "var",
              name = p.name.value,
              arity = 0,
              resultType = d.decltpe.syntax,
              modifiers = extractModifiers(d.mods),
              accessLevel = extractAccessLevel(d.mods),
            )
          case _ => ()
        }
      case d: Defn.Type if isAccessible(d.mods) =>
        out += SurfaceDecl(
          path = path,
          kind = "type",
          name = d.name.value,
          arity = 0,
          typeParams = renderTypeParams(d.tparamClause.values),
          modifiers = extractModifiers(d.mods),
          accessLevel = extractAccessLevel(d.mods),
        )
      case d: Decl.Type if isAccessible(d.mods) =>
        out += SurfaceDecl(
          path = path,
          kind = "type",
          name = d.name.value,
          arity = 0,
          typeParams = renderTypeParams(d.tparamClause.values),
          modifiers = extractModifiers(d.mods),
          accessLevel = extractAccessLevel(d.mods),
        )
      case _ => t.children.foreach(walk(_, path))

    walk(tree, path)

  private def defArity(clauses: List[Term.ParamClause]): Int =
    clauses.map(_.values.length).sum

  /** Two rendered types match after normalising FQN-vs-simple-name spelling. */
  private[verify] def typesMatch(a: String, b: String): Boolean =
    if a == b then true
    else if a.isEmpty || b.isEmpty then a.isEmpty && b.isEmpty
    else
      val na = normalizeTypeName(a)
      val nb = normalizeTypeName(b)
      na == nb || isSuffixMatch(na, nb) || isSuffixMatch(nb, na)

  /** Strip `scala.Predef.`, `scala.`, `java.lang.` prefixes (always-available without import). */
  private def normalizeTypeName(t: String): String =
    val s = t.trim
    val prefixes = List("scala.Predef.", "scala.", "java.lang.")
    prefixes.foldLeft(s) { (acc, pfx) =>
      if acc.startsWith(pfx) then acc.drop(pfx.length) else acc
    }

  /** True if `short` is the simple-name suffix of `fqn` at a `.` or `$` boundary. */
  private def isSuffixMatch(fqn: String, short: String): Boolean =
    fqn.endsWith("." + short) || fqn.endsWith("$" + short)

  private val nullWrappers = Set("Nullable", "Option")
  private[verify] def isNullWrapped(t: String): Boolean =
    val s = t.trim
    s.endsWith("| Null") || s.endsWith("| Null)") ||
      nullWrappers.exists(w => s.startsWith(w + "[") || s.contains("." + w + "["))

  /** Strip `| Null`, `Nullable[...]`, or `Option[...]` to get the inner type. */
  private[verify] def stripNullWrapper(t: String): String =
    val s = t.trim
    if s.endsWith("| Null") then s.dropRight(6).trim
    else if s.endsWith("| Null)") then
      val inner = s.drop(1).dropRight(7).trim
      inner
    else
      nullWrappers.foldLeft(Option.empty[String]) { (acc, w) =>
        acc.orElse {
          if s.startsWith(w + "[") && s.endsWith("]") then
            Some(s.drop(w.length + 1).dropRight(1))
          else
            val idx = s.indexOf("." + w + "[")
            if idx >= 0 && s.endsWith("]") then
              Some(s.drop(idx + w.length + 2).dropRight(1))
            else None
        }
      }.getOrElse(s)

  private val javaCollectionTypes = Set(
    "java.util.List", "java.util.ArrayList", "java.util.LinkedList",
    "java.util.Set", "java.util.HashSet", "java.util.TreeSet", "java.util.LinkedHashSet",
    "java.util.Map", "java.util.HashMap", "java.util.TreeMap", "java.util.LinkedHashMap",
    "java.util.Collection", "java.util.Iterator", "java.util.Enumeration",
    "java.util.Queue", "java.util.Deque", "java.util.ArrayDeque",
    "java.lang.Iterable",
  )

  private val scalaCollectionPrefixes = List(
    "scala.collection.mutable.", "scala.collection.immutable.", "scala.collection.",
  )

  private val knownCollectionSimpleNames = Set(
    "Array", "ArrayBuffer", "Buffer", "ListBuffer",
    "Seq", "IndexedSeq", "List", "Vector",
    "Set", "HashSet", "TreeSet", "SortedSet",
    "Map", "HashMap", "TreeMap", "SortedMap",
    "Iterator", "Iterable", "IterableOnce",
    "BitSet", "Bits",
    "Queue", "Stack", "ArrayDeque",
    "mutable.Buffer", "mutable.Map", "mutable.Set",
    "mutable.ArrayBuffer", "mutable.HashMap", "mutable.HashSet",
    "immutable.List", "immutable.Map", "immutable.Set",
  )

  private def isJavaCollectionType(t: String): Boolean =
    val head = t.takeWhile(c => c != '[' && c != ' ')
    javaCollectionTypes.contains(head) || head.startsWith("java.util.")

  private def isScalaCollectionType(t: String): Boolean =
    val head = t.takeWhile(c => c != '[' && c != ' ')
    scalaCollectionPrefixes.exists(head.startsWith) ||
      knownCollectionSimpleNames.contains(head)

  /** True when one type is a JDK collection and the other is a scala collection. */
  private def isCollectionRetarget(a: String, b: String): Boolean =
    (isJavaCollectionType(a) && isScalaCollectionType(b)) ||
      (isScalaCollectionType(a) && isJavaCollectionType(b))

  private val primitiveTypes = Set(
    "Int", "Long", "Float", "Double", "Short", "Byte", "Char", "Boolean",
    "scala.Int", "scala.Long", "scala.Float", "scala.Double",
    "scala.Short", "scala.Byte", "scala.Char", "scala.Boolean",
  )

  private val factoryNames = Set("apply", "from", "wrap", "of", "create")

  /** Apply package renames to a path prefix (longest-prefix-first). */
  def normalisePath(path: String, renames: Map[String, String]): String =
    if renames.isEmpty then path
    else
      val segments = path.stripPrefix("/").split('/').toList
      val dotted = segments.mkString(".")
      val sorted = renames.toList.sortBy(-_._1.length)
      sorted.find((from, _) => dotted == from || dotted.startsWith(from + ".")) match
        case Some((from, to)) =>
          val rest = dotted.drop(from.length)
          val newDotted = if rest.isEmpty then to else to + rest
          "/" + newDotted.replace('.', '/')
        case None => path

  /** A divergence between the two surfaces. */
  final case class Divergence(
      family: String,
      emitted: Option[SurfaceDecl],
      reference: Option[SurfaceDecl],
      detail: String,
      renameCandidates: String = "",
  ):
    def subject: String = emitted.orElse(reference).map(_.toString).getOrElse("?")
    def report(renames: Map[String, String]): CheckReport.Finding =
      val path = emitted.orElse(reference).map(d => normalisePath(d.path, renames)).getOrElse("")
      val fullDetail = if renameCandidates.nonEmpty then s"$detail [rename candidates: $renameCandidates]"
                       else detail
      CheckReport.Finding(
        check  = lane(family),
        kind   = family,
        owner  = emitted.orElse(reference).map(d => d.path.stripPrefix("/").replace('/', '.') + "#" + d.name).getOrElse("?"),
        path   = path,
        line   = 0,
        detail = fullDetail,
      )

  /** Compare two surfaces and classify every divergence. */
  def compare(
      emitted: List[SurfaceDecl],
      reference: List[SurfaceDecl],
      renames: Map[String, String],
  ): List[Divergence] =
    val inverseRenames = renames.map((k, v) => (v, k))
    val normRef = reference.map(d => d.copy(path = normalisePath(d.path, inverseRenames)))

    val emittedByKey  = emitted.groupBy(_.matchKey)
    val refByKey      = normRef.groupBy(_.matchKey)
    val allKeys       = (emittedByKey.keySet ++ refByKey.keySet).toList.sorted

    val out = List.newBuilder[Divergence]

    allKeys.foreach { key =>
      val es = emittedByKey.getOrElse(key, Nil)
      val rs = refByKey.getOrElse(key, Nil)

      if es.nonEmpty && rs.nonEmpty then
        es.zip(rs).foreach { (e, r) =>
          if e.kind != r.kind then
            val family = classifyKindDrift(e, r)
            out += Divergence(family, Some(e), Some(r),
              s"kind differs: emitted ${e.kind}, reference ${r.kind}")
          classifyTypeDifferences(e, r).foreach(out += _)
        }
      else if es.nonEmpty && rs.isEmpty then
        es.foreach { e =>
          val (family, candidates) = tryClassifyExtraWithCandidates(e, normRef, "port-extra")
          out += Divergence(family, Some(e), None,
            s"${e.kind} ${e.name}/${e.arity} in emitted port only",
            renameCandidates = candidates)
        }
      else
        rs.foreach { r =>
          val (family, candidates) = tryClassifyMissingWithCandidates(r, emitted, "hand-port-extra")
          out += Divergence(family, None, Some(r),
            s"${r.kind} ${r.name}/${r.arity} in reference port only",
            renameCandidates = candidates)
        }
    }

    out.result()

  /** Classify kind drift (same key, different kind). */
  private def classifyKindDrift(e: SurfaceDecl, r: SurfaceDecl): String =
    val propKinds = Set("val", "var", "param")
    if propKinds.contains(e.kind) && propKinds.contains(r.kind) then "mutability"
    else "unclassified"

  /** Classify type-level divergences between two key-matched declarations. */
  private def classifyTypeDifferences(e: SurfaceDecl, r: SurfaceDecl): List[Divergence] =
    val out = List.newBuilder[Divergence]

    if e.paramTypes.nonEmpty || r.paramTypes.nonEmpty then
      val maxLen = math.max(e.paramTypes.length, r.paramTypes.length)
      val ePad = e.paramTypes.padTo(maxLen, "")
      val rPad = r.paramTypes.padTo(maxLen, "")
      ePad.zip(rPad).zipWithIndex.foreach { case ((et, rt), idx) =>
        if !typesMatch(et, rt) then
          val family = classifyTypePairDivergence(et, rt)
          out += Divergence(family, Some(e), Some(r),
            s"param $idx type differs: emitted '$et', reference '$rt'")
      }

    if e.resultType.nonEmpty && r.resultType.nonEmpty && !typesMatch(e.resultType, r.resultType) then
      val family = classifyTypePairDivergence(e.resultType, r.resultType)
      out += Divergence(family, Some(e), Some(r),
        s"result type differs: emitted '${e.resultType}', reference '${r.resultType}'")

    if e.typeParams.nonEmpty && r.typeParams.nonEmpty && e.typeParams != r.typeParams then
      out += Divergence("signature", Some(e), Some(r),
        s"type params differ: emitted '${e.typeParams}', reference '${r.typeParams}'")

    if Set("class", "trait", "enum").contains(e.kind) && e.parents != r.parents then
      val eDiff = e.parents.filterNot(ep => r.parents.exists(rp => typesMatch(ep, rp)))
      val rDiff = r.parents.filterNot(rp => e.parents.exists(ep => typesMatch(ep, rp)))
      if eDiff.nonEmpty || rDiff.nonEmpty then
        val family = classifyParentDivergence(eDiff, rDiff)
        out += Divergence(family, Some(e), Some(r),
          s"parents differ: emitted-only [${eDiff.mkString(", ")}], reference-only [${rDiff.mkString(", ")}]")

    if e.accessLevel != r.accessLevel then
      out += Divergence("visibility", Some(e), Some(r),
        s"access differs: emitted '${e.accessLevel}', reference '${r.accessLevel}'")

    if e.targetName != r.targetName then
      if e.targetName.nonEmpty || r.targetName.nonEmpty then
        out += Divergence("operator", Some(e), Some(r),
          s"@targetName differs: emitted '${e.targetName}', reference '${r.targetName}'")

    val modDiff = e.modifiers.diff(r.modifiers) ++ r.modifiers.diff(e.modifiers)
    if modDiff.nonEmpty then
      if modDiff.contains("opaque") then
        out += Divergence("opaque", Some(e), Some(r),
          s"opaque modifier differs: emitted ${e.modifiers}, reference ${r.modifiers}")
      else
        out += Divergence("signature", Some(e), Some(r),
          s"modifiers differ: emitted ${e.modifiers.mkString(",")}, reference ${r.modifiers.mkString(",")}")

    out.result()

  private def classifyTypePairDivergence(emitted: String, reference: String): String =
    val eNull = isNullWrapped(emitted)
    val rNull = isNullWrapped(reference)
    if eNull != rNull then
      val inner = if eNull then stripNullWrapper(emitted) else emitted
      val other = if rNull then stripNullWrapper(reference) else reference
      if typesMatch(inner, other) then return "null-model"

    if isCollectionRetarget(emitted, reference) then return "collection-retarget"

    val eHead = emitted.takeWhile(c => c != '[' && c != ' ')
    val rHead = reference.takeWhile(c => c != '[' && c != ' ')
    if primitiveTypes.contains(eHead) && !primitiveTypes.contains(rHead) then return "opaque"
    if !primitiveTypes.contains(eHead) && primitiveTypes.contains(rHead) then return "opaque"

    "signature"

  /** Classify parent-list divergence. */
  private def classifyParentDivergence(emittedOnly: List[String], referenceOnly: List[String]): String =
    val pairs = emittedOnly.zip(referenceOnly)
    if pairs.exists((e, r) => isCollectionRetarget(e, r)) then "collection-retarget"
    else if pairs.exists((e, r) => isNullWrapped(e) != isNullWrapped(r)) then "null-model"
    else "signature"

  /** Classify an extra member on one side, with rename candidates. */
  private def tryClassifyExtraWithCandidates(
      d: SurfaceDecl,
      otherSide: List[SurfaceDecl],
      defaultFamily: String,
  ): (String, String) =
    val family = tryClassifyExtra(d, otherSide, defaultFamily)
    val candidates = if family == defaultFamily then findRenameCandidates(d, otherSide) else ""
    (family, candidates)

  private def tryClassifyMissingWithCandidates(
      d: SurfaceDecl,
      otherSide: List[SurfaceDecl],
      defaultFamily: String,
  ): (String, String) =
    val family = tryClassifyMissing(d, otherSide, defaultFamily)
    val candidates = if family == defaultFamily then findRenameCandidates(d, otherSide) else ""
    (family, candidates)

  /** Find same-arity members with a different name on the other side (rename candidates). */
  private def findRenameCandidates(d: SurfaceDecl, otherSide: List[SurfaceDecl]): String =
    if Set("class", "trait", "object", "enum", "case").contains(d.kind) then ""
    else
      val candidates = otherSide.filter { o =>
        o.path == d.path &&
          o.kind == d.kind &&
          o.arity == d.arity &&
          o.name != d.name
      }
      candidates.map(_.name).mkString(", ")

  private def tryClassifyExtra(d: SurfaceDecl, otherSide: List[SurfaceDecl], defaultFamily: String): String =
    if d.kind == "def" && d.arity == 0 then
      val prop = accessorPropName(d.name)
      if prop.isDefined && otherSide.exists(o => o.path == d.path && prop.contains(o.name)) then
        return "accessor"
    if d.kind == "def" && d.arity == 1 then
      val prop = setterPropName(d.name)
      if prop.isDefined && otherSide.exists(o => o.path == d.path && prop.contains(o.name)) then
        return "accessor"
    if d.path.endsWith("$") && factoryNames.contains(d.name) then
      if hasMatchingType(d.path, otherSide) then
        return "factory"
    if otherSide.exists(o =>
      normCompanionPath(o.path) == normCompanionPath(d.path) &&
        o.kind == d.kind && o.name == d.name && o.arity == d.arity
    ) then
      return "static-placement"
    if isOperatorRename(d, otherSide) then return "operator"
    defaultFamily

  private def tryClassifyMissing(d: SurfaceDecl, otherSide: List[SurfaceDecl], defaultFamily: String): String =
    if Set("val", "var").contains(d.kind) then
      val getters = List("get" + d.name.capitalize, "is" + d.name.capitalize)
      if otherSide.exists(o => o.path == d.path && o.kind == "def" && o.arity == 0 && getters.contains(o.name)) then
        return "accessor"
    if d.path.endsWith("$") && factoryNames.contains(d.name) then
      if hasMatchingType(d.path, otherSide) then
        return "factory"
    if otherSide.exists(o =>
      normCompanionPath(o.path) == normCompanionPath(d.path) &&
        o.kind == d.kind && o.name == d.name && o.arity == d.arity
    ) then
      return "static-placement"
    if isOperatorRename(d, otherSide) then return "operator"
    defaultFamily

  /** True if `d` has a counterpart on `otherSide` connected by `@targetName`. */
  private def isOperatorRename(d: SurfaceDecl, otherSide: List[SurfaceDecl]): Boolean =
    otherSide.exists { o =>
      o.path == d.path && o.kind == d.kind && o.arity == d.arity && o.name != d.name &&
        ((d.targetName.nonEmpty && d.targetName == o.name) ||
         (o.targetName.nonEmpty && o.targetName == d.name) ||
         (d.targetName.nonEmpty && o.targetName.nonEmpty && d.targetName == o.targetName))
    }

  private def accessorPropName(name: String): Option[String] =
    List("get", "is").collectFirst {
      case prefix if name.length > prefix.length && name.startsWith(prefix) && name(prefix.length).isUpper =>
        name(prefix.length).toLower.toString + name.drop(prefix.length + 1)
    }

  private def setterPropName(name: String): Option[String] =
    if name.length > 3 && name.startsWith("set") && name(3).isUpper then
      Some(name(3).toLower.toString + name.drop(4))
    else None

  /** True if a type with matching name exists at the companion's parent path. */
  private def hasMatchingType(companionMemberPath: String, decls: List[SurfaceDecl]): Boolean =
    val companionPath = companionMemberPath.stripSuffix("$")
    val lastSlash = companionPath.lastIndexOf('/')
    val (parentPath, typeName) =
      if lastSlash >= 0 then (companionPath.take(lastSlash), companionPath.drop(lastSlash + 1))
      else ("", companionPath.stripPrefix("/"))
    decls.exists(o =>
      o.path == parentPath && o.name == typeName &&
        Set("class", "trait", "enum").contains(o.kind)
    )

  private def normCompanionPath(path: String): String =
    path.split('/').map(_.stripSuffix("$")).mkString("/")

  /** Run the check. Returns per-family findings. */
  def check(
      ref: ParityRef,
      emitDir: Path,
      renames: Map[String, String],
  ): List[CheckReport.Finding] =
    val emittedResult   = parseSurface(List(emitDir))
    val referenceResult = parseSurface(ref.roots)

    (emittedResult, referenceResult) match
      case (Left(err), _) =>
        List(CheckReport.Finding(lane("unclassified"), "parse-error", "emitted", "", 0,
          s"could not parse emitted sources: $err"))
      case (_, Left(err)) =>
        List(CheckReport.Finding(lane("unclassified"), "parse-error", "reference", "", 0,
          s"could not parse reference sources: $err"))
      case (Right(emitted), Right(reference)) =>
        val effectiveRenames = if ref.packageMapping.nonEmpty then ref.packageMapping else renames
        val divergences = compare(emitted, reference, effectiveRenames)
        divergences.map(_.report(effectiveRenames))

  /** Summary line for stdout, counts by family. */
  def summary(findings: List[CheckReport.Finding]): String =
    val byFamily = findings.groupBy(_.kind)
    Families.map { f =>
      val n = byFamily.getOrElse(f, Nil).size
      s"  $f: $n"
    }.mkString("API PARITY:\n", "\n", "")
