package balticporter.verify

import balticporter.core.ParityRef
import balticporter.tir.CheckReport

import java.nio.file.{Files, Path}
import scala.jdk.CollectionConverters.*
import scala.meta.*

/** Compare the emitted port's public surface against a hand-written reference port.
  *
  * ==What this check IS==
  * A §1(b) check: the MECHANISM (parse both sides with scalameta, classify each divergence into a
  * family, report per-family lanes) is universal; the POLICY (which roots, which package mapping)
  * is per-library, declared in `PortManifest.parity`. An empty/absent `parity` makes the check a
  * no-op that records nothing — §1(b)'s rule.
  *
  * ==What this check IS NOT==
  * This is NOT the existing `ApiParity` helper, which checks Java expectations against the emitted
  * skeleton. This check compares TWO SCALA SURFACES — the emitted port and the hand-written
  * reference port — and classifies every divergence into a family the port's maintainer can act on.
  *
  * ==Why a per-family lane==
  * A single count hides the composition: "47 divergences" tells a reader nothing about whether any
  * of them matter. `api-parity(accessor) 12` beside `api-parity(unclassified) 3` says the port's
  * twelve getter/setter collapses are recognised idioms and three findings are the real work list.
  * `unclassified = 0` is the gate.
  *
  * ==§4.45 classification per family==
  * Each family carries a sentence saying which of §1's three kinds the fix is. An agent in another
  * repository reading one of these findings can immediately tell whether the answer is in the
  * engine, in the manifest, or in a library-specific rule.
  */
object ApiParityCheck:

  // ---- lane names ----

  /** The family slugs. Each becomes `api-parity(<slug>)`. */
  val Families: List[String] = List(
    "accessor",        // getter/setter collapse (getX/setX vs x/x_=) or paren-vs-parenless
    "static-placement", // class vs companion placement
    "mutability",      // val vs var vs def
    "rename",          // same shape, different name (usually a known rename)
    "visibility",      // different access level
    "hand-port-extra", // declared only in the hand port (hand port added API)
    "port-extra",      // declared only in the emitted port (java the hand port skipped)
    "unclassified",    // everything else — the work list
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
    "unclassified" -> (
      "UNKNOWN: a divergence that fits no recognised family. This is the work list — each row " +
        "is either a missing classifier in this check or a real divergence to investigate."),
  )

  // ---- surface model ----

  /** A declaration on the public surface. Richer than `SkeletonDiff.Member` — it captures enough
    * to classify divergences into families rather than just reporting missing/extra. */
  final case class SurfaceDecl(
      /** owner path, e.g. `/Foo` or `/Foo/Bar$` */
      path: String,
      /** `class`, `trait`, `object`, `enum`, `case`, `def`, `val`, `var`, `type` */
      kind: String,
      /** simple name */
      name: String,
      /** for defs: total parameter count across all clauses; 0 for vals/vars/types */
      arity: Int,
  ):
    /** structural key for matching: path + kind-class + name + arity. Kind-class groups
      * val/var/param together and keeps def separate. */
    def matchKey: String =
      val kc = kindClass
      s"$path|$kc|$name/$arity"

    /** coarser kind for matching: val/var/param are one class, def is another */
    private def kindClass: String = kind match
      case "val" | "var" | "param" => "prop"
      case other                    => other

    override def toString: String = s"$path: $kind $name/$arity"

  // ---- parsing ----

  /** Parse all `.scala` files under the given roots into a flat list of surface declarations.
    * Uses the same scalameta parser as `SkeletonDiff.parseSkeleton` — both sides are parsed the
    * SAME way, which is §4.56's rule (two spellings make an edge incomparable). */
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
    else Right(decls.result().sortBy(d => (d.path, d.kind, d.name, d.arity)))

  private def collectDecls(tree: Tree, path: String, out: collection.mutable.Builder[SurfaceDecl, List[SurfaceDecl]]): Unit =
    def isPublic(mods: List[Mod]): Boolean =
      !mods.exists {
        case _: Mod.Private   => true
        case _: Mod.Protected => true
        case _                => false
      }

    def walkTemplate(templ: Template, path: String): Unit =
      templ.body.stats.foreach(walk(_, path))

    def ctorParams(name: String, isCase: Boolean, ctor: Ctor.Primary, path: String): Unit =
      ctor.paramClauses.flatMap(_.values).foreach { p =>
        // Only include if public surface (val/var params, or case class params which are vals)
        val paramKind = p.mods
          .collectFirst {
            case _: Mod.VarParam => "var"
            case _: Mod.ValParam => "val"
          }
          .orElse(if isCase then Some("val") else None)
        paramKind.foreach { k =>
          if isPublic(p.mods) then
            out += SurfaceDecl(s"$path/$name", k, p.name.value, 0)
        }
      }

    def walk(t: Tree, path: String): Unit = t match
      case d: Defn.Class if isPublic(d.mods) =>
        out += SurfaceDecl(path, "class", d.name.value, 0)
        ctorParams(d.name.value, d.mods.exists(_.isInstanceOf[Mod.Case]), d.ctor, path)
        walkTemplate(d.templ, s"$path/${d.name.value}")
      case d: Defn.Trait if isPublic(d.mods) =>
        out += SurfaceDecl(path, "trait", d.name.value, 0)
        walkTemplate(d.templ, s"$path/${d.name.value}")
      case d: Defn.Object if isPublic(d.mods) =>
        out += SurfaceDecl(path, "object", d.name.value, 0)
        walkTemplate(d.templ, s"$path/${d.name.value}$$")
      case d: Defn.Enum if isPublic(d.mods) =>
        out += SurfaceDecl(path, "enum", d.name.value, 0)
        ctorParams(d.name.value, isCase = false, d.ctor, path)
        walkTemplate(d.templ, s"$path/${d.name.value}")
      case d: Defn.EnumCase =>
        out += SurfaceDecl(path, "case", d.name.value, 0)
      case d: Defn.RepeatedEnumCase =>
        d.cases.foreach(c => out += SurfaceDecl(path, "case", c.value, 0))
      case d: Defn.Def if isPublic(d.mods) =>
        out += SurfaceDecl(path, "def", d.name.value, defArity(d.paramClauseGroups.flatMap(_.paramClauses)))
      case d: Decl.Def if isPublic(d.mods) =>
        out += SurfaceDecl(path, "def", d.name.value, defArity(d.paramClauseGroups.flatMap(_.paramClauses)))
      case d: Defn.Val if isPublic(d.mods) =>
        d.pats.foreach { case p: Pat.Var => out += SurfaceDecl(path, "val", p.name.value, 0); case _ => () }
      case d: Decl.Val if isPublic(d.mods) =>
        d.pats.foreach { case p: Pat.Var => out += SurfaceDecl(path, "val", p.name.value, 0); case _ => () }
      case d: Defn.Var if isPublic(d.mods) =>
        d.pats.foreach { case p: Pat.Var => out += SurfaceDecl(path, "var", p.name.value, 0); case _ => () }
      case d: Decl.Var if isPublic(d.mods) =>
        d.pats.foreach { case p: Pat.Var => out += SurfaceDecl(path, "var", p.name.value, 0); case _ => () }
      case d: Defn.Type if isPublic(d.mods) =>
        out += SurfaceDecl(path, "type", d.name.value, 0)
      case d: Decl.Type if isPublic(d.mods) =>
        out += SurfaceDecl(path, "type", d.name.value, 0)
      case _ => t.children.foreach(walk(_, path))

    walk(tree, path)

  private def defArity(clauses: List[Term.ParamClause]): Int =
    clauses.map(_.values.length).sum

  // ---- package normalisation ----

  /** Apply package renames to a path prefix, so that e.g. `/sge/ecs/Engine` and
    * `/com/badlogic/ashley/core/Engine` can be compared after applying
    * `Map("com.badlogic.ashley.core" -> "sge.ecs")`. The mapping is applied as a
    * longest-prefix-first replacement on the path segments. */
  def normalisePath(path: String, renames: Map[String, String]): String =
    if renames.isEmpty then path
    else
      // convert path segments to dotted form, apply longest-prefix-first
      val segments = path.stripPrefix("/").split('/').toList
      val dotted = segments.mkString(".")
      // try longest prefix first
      val sorted = renames.toList.sortBy(-_._1.length)
      sorted.find((from, _) => dotted == from || dotted.startsWith(from + ".")) match
        case Some((from, to)) =>
          val rest = dotted.drop(from.length)
          val newDotted = if rest.isEmpty then to else to + rest
          "/" + newDotted.replace('.', '/')
        case None => path

  // ---- comparison and classification ----

  /** A divergence between the two surfaces. */
  final case class Divergence(
      family: String,
      emitted: Option[SurfaceDecl],
      reference: Option[SurfaceDecl],
      detail: String,
  ):
    def subject: String = emitted.orElse(reference).map(_.toString).getOrElse("?")
    def report(renames: Map[String, String]): CheckReport.Finding =
      val path = emitted.orElse(reference).map(d => normalisePath(d.path, renames)).getOrElse("")
      CheckReport.Finding(
        check  = lane(family),
        kind   = family,
        owner  = emitted.orElse(reference).map(d => d.path.stripPrefix("/").replace('/', '.') + "#" + d.name).getOrElse("?"),
        path   = path,
        line   = 0,
        detail = detail,
      )

  /** Compare two surfaces and classify every divergence.
    *
    * @param emitted   declarations from the mechanically emitted port
    * @param reference declarations from the hand-written reference port
    * @param renames   manifest's effectivePackageRenames, applied to normalise the reference
    *                  port's paths into the emitted port's namespace
    */
  def compare(
      emitted: List[SurfaceDecl],
      reference: List[SurfaceDecl],
      renames: Map[String, String],
  ): List[Divergence] =
    // normalise reference paths into the emitted namespace
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
        // Both sides have it — check for kind drift (val vs var vs def)
        es.zip(rs).foreach { (e, r) =>
          if e.kind != r.kind then
            val family = classifyKindDrift(e, r)
            out += Divergence(family, Some(e), Some(r),
              s"kind differs: emitted ${e.kind}, reference ${r.kind}")
        }
      else if es.nonEmpty && rs.isEmpty then
        // emitted has it, reference does not
        es.foreach { e =>
          // try to find a reference member at the same path with different name but same kind+arity
          val family = tryClassifyExtra(e, normRef, "port-extra")
          out += Divergence(family, Some(e), None,
            s"${e.kind} ${e.name}/${e.arity} in emitted port only")
        }
      else
        // reference has it, emitted does not
        rs.foreach { r =>
          val family = tryClassifyMissing(r, emitted, "hand-port-extra")
          out += Divergence(family, None, Some(r),
            s"${r.kind} ${r.name}/${r.arity} in reference port only")
        }
    }

    out.result()

  /** Classify a kind drift (same key, different kind). */
  private def classifyKindDrift(e: SurfaceDecl, r: SurfaceDecl): String =
    val propKinds = Set("val", "var", "param")
    if propKinds.contains(e.kind) && propKinds.contains(r.kind) then "mutability"
    else "unclassified"

  /** Try to classify an extra member on one side by looking for matches on the other. */
  private def tryClassifyExtra(d: SurfaceDecl, otherSide: List[SurfaceDecl], defaultFamily: String): String =
    // accessor idiom: emitted getX/0, reference has x as val/var
    if d.kind == "def" && d.arity == 0 then
      val prop = accessorPropName(d.name)
      if prop.isDefined && otherSide.exists(o => o.path == d.path && prop.contains(o.name)) then
        return "accessor"
    if d.kind == "def" && d.arity == 1 then
      val prop = setterPropName(d.name)
      if prop.isDefined && otherSide.exists(o => o.path == d.path && prop.contains(o.name)) then
        return "accessor"
    // static placement: same kind+name+arity but path differs by companion marker
    if otherSide.exists(o =>
      normCompanionPath(o.path) == normCompanionPath(d.path) &&
        o.kind == d.kind && o.name == d.name && o.arity == d.arity
    ) then
      return "static-placement"
    defaultFamily

  private def tryClassifyMissing(d: SurfaceDecl, otherSide: List[SurfaceDecl], defaultFamily: String): String =
    // accessor idiom: reference has x as val/var, emitted has getX/0
    if Set("val", "var").contains(d.kind) then
      val getters = List("get" + d.name.capitalize, "is" + d.name.capitalize)
      if otherSide.exists(o => o.path == d.path && o.kind == "def" && o.arity == 0 && getters.contains(o.name)) then
        return "accessor"
    // static placement
    if otherSide.exists(o =>
      normCompanionPath(o.path) == normCompanionPath(d.path) &&
        o.kind == d.kind && o.name == d.name && o.arity == d.arity
    ) then
      return "static-placement"
    defaultFamily

  private def accessorPropName(name: String): Option[String] =
    List("get", "is").collectFirst {
      case prefix if name.length > prefix.length && name.startsWith(prefix) && name(prefix.length).isUpper =>
        name(prefix.length).toLower.toString + name.drop(prefix.length + 1)
    }

  private def setterPropName(name: String): Option[String] =
    if name.length > 3 && name.startsWith("set") && name(3).isUpper then
      Some(name(3).toLower.toString + name.drop(4))
    else None

  private def normCompanionPath(path: String): String =
    path.split('/').map(_.stripSuffix("$")).mkString("/")

  // ---- entry point ----

  /** Run the check. Pure function — the orchestrator (PortRun) records the result.
    *
    * @param ref      the parity reference from the manifest
    * @param emitDir  the directory the port wrote its emitted Scala into
    * @param renames  the manifest's effectivePackageRenames (upstream -> emitted namespace)
    * @return per-family findings, one `CheckReport.Finding` per divergence
    */
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

  /** Summary line for stdout. */
  def summary(findings: List[CheckReport.Finding]): String =
    val byFamily = findings.groupBy(_.kind)
    Families.map { f =>
      val n = byFamily.getOrElse(f, Nil).size
      s"  $f: $n"
    }.mkString("API PARITY:\n", "\n", "")
