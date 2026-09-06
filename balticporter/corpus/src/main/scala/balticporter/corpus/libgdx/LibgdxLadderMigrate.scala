package balticporter.corpus.libgdx

import balticporter.corpus.JnigenClasspath
import balticporter.core.{FrontendConfig, PortManifest, Provenance, ResourceTree, RuntimeMode}
import balticporter.runner.{Determinism, PortRun, SourceSet, VendoredCommit}
import balticporter.corpus.lls.{LlsMigrate, LlsPolicy}

import java.nio.file.{Files, Path}
import scala.jdk.CollectionConverters.*

/** Rung L0 of the libGDX ladder (PROGRESS.md §13.29) ON THE LLS BASE: core minus the utilities family,
  * a DEPENDENT of `ported/lls` (CLAUDE.md §1.5), with no policy of its own — its compile count is the
  * honest measure of Java-as-Scala over the base's decisions, before any of core's own. */
object LibgdxL0Migrate:

  def main(args: Array[String]): Unit =
    val repoRoot = Path.of(sys.props.getOrElse("balticporter.root", ".")).toAbsolutePath.normalize
    val base     = repoRoot.resolve("../sge/original-src/libgdx/gdx/src").normalize
    val steps    = LibgdxLadder.stepsFrom(args)

    // the utilities family is the lls port's (PROGRESS.md §13.29): its files are the BASE's units,
    // resolved through `gdx/src` and never emitted twice.
    val files = Files.walk(base).iterator().asScala
      .filter(p => p.toString.endsWith(".java"))
      .map(p => base.relativize(p).toString)
      .filterNot(f => f.endsWith("package-info.java") || f.endsWith("module-info.java"))
      .filterNot(LlsMigrate.Files.toSet)
      .toList.sorted

    PortRun(
      label     = "sge-l0",
      portRoot  = repoRoot.resolve("ported/sge-l0"),
      sourceSet = SourceSet.Main,
      // gdx-jnigen-loader carries `SharedLibraryLoader`, which `gdx/src` references and no
      // longer declares; without it on the classpath Spoon resolves no declaration for it.
      frontend  = FrontendConfig(base, files, JnigenClasspath.entries(repoRoot), resolutionRoots = List(base)),
      phases    = Nil,
      manifest  = Some(LibgdxLadder.universal(repoRoot, steps)),
      provenance = Some(Provenance(
        upstreamName     = "libGDX",
        upstreamCommit   = VendoredCommit.of(base),
        originalLicense  = "Apache-2.0",
        sourcePathPrefix = "gdx/src",
        sourceRoot       = base.toString,
      )),
      runtimeMode = RuntimeMode.Vendored,
      determinism = Determinism.fromArgs(args.toSeq),
      nextStep    = "just gdx-l0-measure",
    ).execute()

object LibgdxLadder:

  /** JUnit's `@Rule TestWatcher` has no MUnit model; the field is dropped, as on the full port. */
  private val watcherDrop = "com.badlogic.gdx.utils.JsonMatcherTests#watcher"

  /** The test manifest: JUnit -> MUnit only (`TestFrameworkTransform`), inheriting `universal`;
    * `externalParenless` is P11 (munit's JS/Native `Description` is parenless). No sge policy. */
  def universalTest(repoRoot: Path, steps: Set[String] = DefaultSteps): PortManifest = universal(repoRoot, steps).extendedBy(PortManifest(
    name        = "sge-l0-test",
    dropMethods = Set(watcherDrop),
    surface     = List(new balticporter.transform.TestFrameworkTransform(dropFields = Set(watcherDrop))),
    externalParenless = Set(
      "org.junit.runner.Description#getTestClass",
      "org.junit.runner.Description#getMethodName",
      "org.junit.runner.Description#getAnnotations",
    ),
  ))

  /** `--steps=a,b` on the command line; absent or empty = [[DefaultSteps]] (the steps landed so
    * far), `--steps=none` = the bare universal translation. */
  def stepsFrom(args: Array[String]): Set[String] =
    args.collectFirst { case a if a.startsWith("--steps=") => a.stripPrefix("--steps=").trim }
      .filter(_.nonEmpty) match
      case None         => DefaultSteps
      case Some("none") => Set.empty
      case Some(v)      => v.split(',').map(_.trim).filter(_.nonEmpty).toSet

  /** Core's declarations that allocate an array at their OWN type parameter, or construct a
    * `DynamicArray` at it, so they take the `MkArray` clause (PROGRESS.md §13.29, step "witness");
    * the null-as-empty tables (`IntMap`, `ObjectIntMap`, …) stay refused and counted (K41). */
  val CoreWitnessSubjects: Map[String, List[Int]] = Map(
    "com.badlogic.gdx.utils.SnapshotArray"                         -> List(0),
    "com.badlogic.gdx.utils.DelayedRemovalArray"                   -> List(0),
    "com.badlogic.gdx.utils.Queue"                                 -> List(0),
    "com.badlogic.gdx.graphics.g2d.Animation"                      -> List(0),
    "com.badlogic.gdx.graphics.g3d.particles.ParallelArray$ObjectChannel" -> List(0),
    "com.badlogic.gdx.math.Octree"                                 -> List(0),
    "com.badlogic.gdx.graphics.g3d.particles.batches.BufferedParticleBatch" -> List(0),
  )

  /** The step fragments, cumulative; each merges with the base's instance of the same phase at
    * the base's position (CLAUDE.md §1.5). */
  val Steps: Map[String, List[balticporter.tir.Phase]] = Map(
    "witness" -> List(
      new balticporter.transform.GlobalsToImplicitsTransform(requiredGivens =
        balticporter.transform.ElementWitnessTransform.constructorGivens(CoreWitnessSubjects, LlsPolicy.Witness)),
      new balticporter.transform.ElementWitnessTransform(
        witness      = LlsPolicy.Witness,
        subjectTypes = CoreWitnessSubjects,
        dropBound    = CoreWitnessSubjects.keySet,
        boxedWitness = Some("lowlevel.MkArray.anyRef[scala.AnyRef].asInstanceOf[lowlevel.MkArray[{elem}]]"))),
  )

  /** Per step, the members the step makes dead: the reflective `Class`-typed constructors the
    * witness replaces (each has a portable twin; the full port dropped the same, `LibgdxPolicy`). */
  val stepDrops: Map[String, Set[String]] = Map(
    "witness" -> Set(
      "com.badlogic.gdx.utils.SnapshotArray#<init>(boolean,int,Class)",
      "com.badlogic.gdx.utils.SnapshotArray#<init>(Class)",
      "com.badlogic.gdx.utils.DelayedRemovalArray#<init>(boolean,int,Class)",
      "com.badlogic.gdx.utils.DelayedRemovalArray#<init>(Class)",
      "com.badlogic.gdx.utils.Queue#<init>(int,Class)",
      "com.badlogic.gdx.graphics.g3d.particles.batches.BufferedParticleBatch#<init>(Class)",
    ),
  ).withDefaultValue(Set.empty)

  val StepOrder: List[String] = List("witness")
  /** the steps LANDED so far (measured, baselined, PROGRESS.md §13.29). */
  val DefaultSteps: Set[String] = Set("witness")

  /** L0's manifest: a dependent of the lls port carrying the universal facts only. `packageRenames`
    * for the rest of core (the base's `utils`/`math -> lowlevel.*` are inherited, longest prefix
    * wins); the `List` rename keeps `scala.List` out; `MutableParamsTransform` is inherited from the
    * base. No drop, inject, resolutions or parity (PROGRESS.md §13.29). */
  def universal(repoRoot: Path, steps: Set[String] = DefaultSteps): PortManifest =
    val unknown = steps -- Steps.keySet
    require(unknown.isEmpty, s"unknown ladder steps: ${unknown.mkString(",")}; known: ${Steps.keySet.toList.sorted.mkString(",")}")
    LlsPolicy.core(repoRoot, LlsPolicy.DefaultRungs).extendedBy(PortManifest(
      name           = "sge-l0",
      governs        = Set("com.badlogic.gdx"),
      dropMethods    = StepOrder.filter(steps).flatMap(stepDrops).toSet,
      surface        = StepOrder.filter(steps).flatMap(Steps(_)),
      packageRenames = Map("com.badlogic.gdx" -> "sge"),
      typeRenames    = Map("com.badlogic.gdx.scenes.scene2d.ui.List" -> "SgeList"),
      resources      = List(ResourceTree(
        root  = repoRoot.resolve("../sge/original-src/libgdx/gdx/res").normalize,
        files = List(
          "com/badlogic/gdx/utils/lsans-15.fnt", "com/badlogic/gdx/utils/lsans-15.png",
          "com/badlogic/gdx/graphics/g3d/shaders/default.vertex.glsl",
          "com/badlogic/gdx/graphics/g3d/shaders/default.fragment.glsl",
          "com/badlogic/gdx/graphics/g3d/shaders/depth.vertex.glsl",
          "com/badlogic/gdx/graphics/g3d/shaders/depth.fragment.glsl"))),
      dependencies   = List(balticporter.catalog.ArtifactDep("com.badlogicgames.gdx", "gdx-jnigen-loader", "2.5.2",
                                                             balticporter.catalog.CrossKind.Java)),
    ))

/** The ladder port's TEST source set: libGDX's own `gdx/test` tree converted to MUnit on the
  * universal translation, a dependent of `sge-l0` (+ `lls`) — the suite is the step gate the
  * standing orders require (PROGRESS.md §13.29); one exclusion list, empty at L0. */
object LibgdxL0TestMigrate:

  def main(args: Array[String]): Unit =
    val repoRoot = Path.of(sys.props.getOrElse("balticporter.root", ".")).toAbsolutePath.normalize
    val srcRoot  = repoRoot.resolve("../sge/original-src/libgdx/gdx/src").normalize
    val testRoot = repoRoot.resolve("../sge/original-src/libgdx/gdx/test").normalize
    val steps    = LibgdxLadder.stepsFrom(args)

    val files = Files.walk(testRoot).iterator().asScala
      .filter(p => p.toString.endsWith(".java"))
      .map(p => testRoot.relativize(p).toString)
      .filterNot(f => f.endsWith("package-info.java") || f.endsWith("module-info.java"))
      .toList.sorted

    PortRun(
      label     = "sge-l0-test",
      portRoot  = repoRoot.resolve("ported/sge-l0"),
      sourceSet = SourceSet.Test,
      frontend  = FrontendConfig(testRoot, files, JnigenClasspath.entries(repoRoot), resolutionRoots = List(srcRoot)),
      phases    = Nil,
      manifest  = Some(LibgdxLadder.universalTest(repoRoot, steps)),
      provenance = Some(Provenance(
        upstreamName     = "libGDX",
        upstreamCommit   = VendoredCommit.of(testRoot),
        originalLicense  = "Apache-2.0",
        sourcePathPrefix = "gdx/test",
        sourceRoot       = testRoot.toString,
      )),
      runtimeMode = RuntimeMode.Dependency,
      determinism = Determinism.fromArgs(args.toSeq),
      nextStep    = "just gdx-l0-test-measure",
    ).execute()
