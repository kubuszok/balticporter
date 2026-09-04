package balticporter.frontend.spoon

import balticporter.core.{AnnotationPolicy, FrontendConfig, RealPath, Substituted, Substitutions}
import balticporter.catalog.{CatalogLog, Dispatch, JS, Lowering, Obligations, Typing}
import balticporter.tir.*
import balticporter.tir.TypeRepr.*

import spoon.Launcher
import spoon.reflect.code.*
import spoon.reflect.declaration.*
import spoon.reflect.reference.*
import spoon.support.adaption.TypeAdaptor
import spoon.support.compiler.VirtualFile

import java.nio.file.{Files, Path}

import scala.jdk.CollectionConverters.*

/** Populates the typed IR ([[balticporter.tir]]) directly from Spoon's resolved model: every
  * declaration mints a stable-identity [[Symbol]], every type reference resolves to a structured
  * [[TypeRepr]], and externals are lazily interned so `usagesOf` works with no local definition.
  * Scope: declarations, signatures, types, and method bodies. [[Xref.build]] indexes usages.
  */
object SpoonTir:

  /** the PUBLIC instance methods of `java.lang.Object`, by Spoon signature — JLS 9.8's exclusion
    * when counting a functional interface's abstract methods. `clone`/`finalize` excluded: they
    * are `protected`, so JLS 9.8 does not exclude them. */
  private[spoon] val ObjectPublicSignatures: Set[String] = Set(
    "equals(java.lang.Object)", "hashCode()", "toString()", "getClass()",
    "notify()", "notifyAll()", "wait()", "wait(long)", "wait(long,int)")

  /** Sentinel entries `annotationsOf` can put in `Symbol.droppedAnnotations` beside real annotation
    * names, so `omissions` distinguishes the reasons a drop happened: `<unresolved>` (type would
    * not resolve), `<unreadable-annotations>` (whole set failed to read), `<annotation-arguments-failed>`
    * (constant-expression path threw — an engine defect, not policy). */
  val UnresolvedAnnotation = "<unresolved>"
  val UnreadableAnnotations = "<unreadable-annotations>"
  val FailedAnnotationArguments = "<annotation-arguments-failed>"

  /** Build a [[Program]] from already-resolved top-level Spoon types. `catalog` is the run's
    * obligation log — a parameter, not a field of `Program`, because a log is a value one run owns
    * (CLAUDE.md §5.1); default is a fresh discarding log. */
  def fromTypes(types: List[CtType[?]], subs: Substitutions = Substitutions.none,
                catalog: CatalogLog = CatalogLog.discarding,
                annotations: AnnotationPolicy = AnnotationPolicy.none): Program =
    new Builder(subs, catalog = catalog, annotations = annotations).build(types)

  /** Build the Spoon model over a whole closure and return its top-level types. Full classpath by
    * default; `lenient` uses noClasspath mode so unconfigured external deps still parse (unresolved
    * types degrade to unmapped references). */
  def buildModel(cfg: FrontendConfig, lenient: Boolean = false): List[CtType[?]] =
    val launcher = new Launcher
    val env      = launcher.getEnvironment
    env.setComplianceLevel(21)
    // comments must stay enabled — the licence-notice harvest below needs them (CLAUDE.md §4.58)
    env.setCommentEnabled(true)
    env.setNoClasspath(lenient)
    env.setSourceClasspath(cfg.classpath.map(_.toString).toArray)
    if cfg.resolutionRoots.nonEmpty then
      cfg.resolutionRoots.foreach(r => addResolutionRoot(launcher, r, cfg.resolutionExcludes))
      // declared inputs: an absent one is fatal with a named diagnostic (CLAUDE.md §5.4)
      val covered = cfg.resolutionRoots.map(r => RealPath.ofExisting(r, "resolution root"))
      cfg.files
        .map(f => RealPath.ofExisting(cfg.sourceRoot.resolve(f), s"declared source file $f"))
        .filterNot(abs => covered.exists(abs.startsWith))
        .foreach(abs => launcher.addInputResource(abs.toString))
    else cfg.files.foreach(f => launcher.addInputResource(cfg.sourceRoot.resolve(f).toString))
    launcher.buildModel().getAllTypes.asScala.toList.filter(_.getDeclaringType == null)

  /** Add one resolution root, minus whatever the port excluded from it
    * ([[balticporter.core.FrontendConfig.resolutionExcludes]]). No exclusions: add the directory
    * whole. With exclusions: add surviving `.java` files individually; `cfg.resolutionRoots` itself
    * stays unchanged. Matched at a path separator, never substring (CLAUDE.md §4.56). */
  private def addResolutionRoot(launcher: Launcher, root: Path, excludes: List[String]): Unit =
    if excludes.isEmpty then launcher.addInputResource(root.toString)
    else
      val rr   = RealPath.ofExisting(root, "resolution root")
      val cuts = excludes.map(_.stripSuffix("/"))
      Files.walk(rr).iterator.asScala
        .filter(p => p.toString.endsWith(".java"))
        .filterNot { p =>
          val rel = rr.relativize(p).toString
          cuts.exists(c => rel == c || rel.startsWith(c + "/"))
        }
        .foreach(p => launcher.addInputResource(p.toString))

  /** Translate each top-level type in ISOLATION (fresh symbol space), returning per-type
    * success (symbol count) or the failure. Used to MEASURE corpus coverage — which
    * constructs still hit `Unsupported` — without one bad file sinking the batch. */
  def coverage(types: List[CtType[?]]): List[(String, Either[Throwable, Int])] =
    types.map { t =>
      t.getQualifiedName -> scala.util.Try(new Builder().build(List(t)).symbols.all.size).toEither
    }

  /** Convenience for tests / snippets: parse one in-memory source (no external classpath;
    * JDK types resolve by qualified name) and populate the TIR from its top-level types. */
  def fromSource(code: String, fileName: String = "Snippet.java",
                 subs: Substitutions = Substitutions.none,
                 catalog: CatalogLog = CatalogLog.discarding,
                 annotations: AnnotationPolicy = AnnotationPolicy.none): Program =
    fromSources(List(fileName -> code), subs, catalog, annotations)

  /** The same, over several compilation units — needed to test package-boundary rules (default
    * access, `protected`, cross-package overrides) that one snippet cannot exercise. Each pair is
    * `fileName -> code`; each unit's text is looked up by file name for comment slicing. */
  def fromSources(sources: List[(String, String)],
                  subs: Substitutions = Substitutions.none,
                  catalog: CatalogLog = CatalogLog.discarding,
                  annotations: AnnotationPolicy = AnnotationPolicy.none): Program =
    val launcher = new Launcher
    val env      = launcher.getEnvironment
    env.setComplianceLevel(21)
    env.setCommentEnabled(true)
    env.setNoClasspath(true)
    sources.foreach((name, code) => launcher.addInputResource(new VirtualFile(code, name)))
    val model = launcher.buildModel()
    val tops  = model.getAllTypes.asScala.toList.filter(_.getDeclaringType == null)
    // VirtualFile has no source buffer of its own — pass texts explicitly or comments re-print (§4.58)
    new Builder(subs, sources.toMap, catalog, annotations).build(tops)

  /** The one classification of a Spoon type reference. Since `CtWildcardReference` extends
    * `CtTypeParameterReference`, the wildcard arm must be matched ABOVE the variable arm or `?`
    * reads as a type variable — derived once here so no caller re-derives it by `isInstanceOf`.
    * `ref`/`args` let a caller treat Prim/Intersection/Named alike. CLAUDE.md §4.56, ENGINE-LIMITS G21 */
  private[spoon] enum TypeShape:
    case Absent
    case Wildcard(w: CtWildcardReference, bound: Option[CtTypeReference[?]], upper: Boolean)
    case Variable(tv: CtTypeParameterReference)
    case Arr(a: CtArrayTypeReference[?], component: CtTypeReference[?])
    case Intersection(i: CtIntersectionTypeReference[?], bounds: List[CtTypeReference[?]])
    case Prim(p: CtTypeReference[?])
    case Named(r: CtTypeReference[?], as: List[CtTypeReference[?]])

    /** the reference this shape classifies — `null` only for [[Absent]]. */
    def ref: CtTypeReference[?] = this match
      case Absent             => null
      case Wildcard(w, _, _)  => w
      case Variable(tv)       => tv
      case Arr(a, _)          => a
      case Intersection(i, _) => i
      case Prim(p)            => p
      case Named(r, _)        => r

    /** the reference's own type ARGUMENTS exactly as `getActualTypeArguments` reports them, so a
      * caller that treats several kinds alike reproduces the `case r =>` it used to fall into. */
    def args: List[CtTypeReference[?]] = this match
      case Absent       => Nil
      case Named(_, as) => as
      case s            => TypeShape.actualArgs(s.ref)

  private[spoon] object TypeShape:
    private def actualArgs(r: CtTypeReference[?]): List[CtTypeReference[?]] =
      r.getActualTypeArguments.asScala.toList

    /** THE derivation. The WILDCARD arm is first, and that order is the whole point (see the enum's
      * doc): `CtWildcardReference <: CtTypeParameterReference`, so any other order silently answers
      * "type variable" for every `?` in the program. */
    def of(tr: CtTypeReference[?]): TypeShape = tr match
      case null                              => Absent
      case w: CtWildcardReference            => Wildcard(w, Option(w.getBoundingType), w.isUpper)
      case tv: CtTypeParameterReference      => Variable(tv)
      case a: CtArrayTypeReference[?]        => Arr(a, a.getComponentType)
      case i: CtIntersectionTypeReference[?] =>
        Intersection(i, i.getBounds.asScala.toList)
      case p if (p.isPrimitive) => Prim(p)
      case r                                 => Named(r, actualArgs(r))

