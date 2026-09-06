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
  def Steps: Map[String, List[balticporter.tir.Phase]] = stepsFor(Set.empty)
  def stepsFor(sel: Set[String]): Map[String, List[balticporter.tir.Phase]] = Map(
    "witness" -> List(
      new balticporter.transform.GlobalsToImplicitsTransform(requiredGivens =
        balticporter.transform.ElementWitnessTransform.constructorGivens(CoreWitnessSubjects, LlsPolicy.Witness)),
      new balticporter.transform.ElementWitnessTransform(
        witness      = LlsPolicy.Witness,
        subjectTypes = CoreWitnessSubjects,
        // the clause is threaded; java's implicit `<: Object` bound STAYS on core's subjects: their
        // collaborators (`ObjectSet[T]`, …) keep theirs, and an unbounded `T` no longer conforms
        // there (12 `E057` on `Octree` the first time the typer pass completed, G30).
        dropBound    = Set.empty,
        boxedWitness = Some("lowlevel.MkArray.anyRef[scala.AnyRef].asInstanceOf[lowlevel.MkArray[{elem}]]"))),
    // core's collections onto lls's and the JDK table, `Comparator -> Ordering`: the base's instance
    // widened to core's entry (merged `Only` scopes, CLAUDE.md §1.5 D12).
    "collections" -> List(
      new balticporter.transform.CollectionsTransform(
        scope    = balticporter.tir.RuleScope.Only(Set("com.badlogic.gdx")),
        retarget = Map("java.util.Comparator" -> "scala.math.Ordering"))),
    // `@Null -> lowlevel.Nullable` on core's entry: merges with lls's instance (`Only` union), so an
    // override of a base member the base retyped (`SnapshotArray.replaceFirst`, 2 `E120` name
    // clashes after erasure) moves with its component; ahead of `enrich`, whose value-map templates
    // are written against the nullable API.
    "nullability" -> List(new balticporter.transform.NullabilityTransform(
      annotations = Set("com.badlogic.gdx.utils.Null"),
      target      = balticporter.transform.NullabilityTransform.Target.Named("lowlevel.Nullable"),
      scope       = balticporter.tir.RuleScope.Only(Set("com.badlogic.gdx")))),
    // lls's added API on core's own collections, and the factories core's subclasses of lls's
    // types must declare themselves (`LibgdxEnrich`).
    "enrich" -> List(LibgdxEnrich.transform(w = true, n = sel("nullability"))),
    // no runtime reflection: the reflective `Json` and the `reflect` package go (types below), the
    // one class lookup by name becomes a table (`AssetTypeRegistry`, injected), and `ClassReflection`'s
    // statics are `java.lang.Class`'s own — the full port's policy, lifted (`LibgdxPolicy`).
    "net" -> Nil,
    "reflection" -> List(
      // `Pools.get` minted a `ReflectionPool` for an unregistered type; java itself offers the
      // refusal (`THROW_ON_REFLECTION_POOL_CREATION`), so the miss throws java's own message and a
      // pool is registered with `Pools.set` (the full port's `Pools` injection says the same).
      new balticporter.transform.MethodBodyTransform(Map(
        "com.badlogic.gdx.utils.Pools#get(Class,int)" ->
          """{
            |  val pool = Pools.typePools.get(`type`)
            |  if (pool.isEmpty) throw new java.lang.RuntimeException(("Please manually define a Pool for " + `type`) + " by calling Pools#set before calling Pools#get")
            |  return pool.get.asInstanceOf[sge.utils.Pool[T]]
            |}""".stripMargin)),
      new balticporter.transform.ClassTableTransform(Map(
        "com.badlogic.gdx.utils.reflect.ClassReflection#forName" ->
          "com.badlogic.gdx.graphics.g3d.particles.AssetTypeRegistry#classFor")),
      new balticporter.transform.StaticForwarderTransform(List(
        balticporter.transform.StaticForwarderTransform.Forwarder(
          wrapper  = "com.badlogic.gdx.utils.reflect.ClassReflection",
          receiver = "java.lang.Class",
          members  = Set("getSimpleName", "isInstance", "isAssignableFrom", "isArray",
                         "isEnum", "isInterface", "isPrimitive", "isAnnotation", "getComponentType"))))),
  )

  /** per step, the TYPES it removes (each replaced by an injection or made dead by the step). */
  val stepTypeDrops: Map[String, Set[String]] = Map(
    // the JVM-only `HttpURLConnection` client: nothing in core references it; the backends supply
    // their own `Net` (sge's capability convention, PROGRESS.md §13.29 R9).
    "net" -> Set("com.badlogic.gdx.net.NetJavaImpl"),
    "reflection" -> Set(
      "com.badlogic.gdx.utils.Json",
      "com.badlogic.gdx.utils.ReflectionPool",
      "com.badlogic.gdx.utils.reflect.Annotation",
      "com.badlogic.gdx.utils.reflect.Field",
      "com.badlogic.gdx.utils.reflect.ArrayReflection",
      "com.badlogic.gdx.utils.reflect.ClassReflection",
      "com.badlogic.gdx.utils.reflect.Constructor",
      "com.badlogic.gdx.utils.reflect.Method",
      "com.badlogic.gdx.utils.reflect.ReflectionException",
    ),
  ).withDefaultValue(Set.empty)

  /** per step, the hand-written injections (standing order 4): `ladder-overrides/` holds the
    * reflection-free `Json`, `ReflectionException` and the asset-type registry. */
  def stepInjects(repoRoot: Path): Map[String, List[Path]] = Map(
    "reflection" -> List(repoRoot.resolve("balticporter/corpus/ladder-overrides")),
  ).withDefaultValue(Nil)

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
    "reflection" -> Set(
      "com.badlogic.gdx.scenes.scene2d.ui.Skin#setEnabledReflection",
      "com.badlogic.gdx.scenes.scene2d.ui.Skin#findMethod",
      "com.badlogic.gdx.graphics.g3d.particles.ParallelArray$ChannelDescriptor#<init>(int,Class,int)",
    ),
  ).withDefaultValue(Set.empty)

  val StepOrder: List[String] = List("witness", "collections", "nullability", "enrich", "reflection", "net")
  /** the steps LANDED so far (measured, baselined, PROGRESS.md §13.29). */
  val DefaultSteps: Set[String] = Set("witness", "collections", "nullability", "enrich", "reflection", "net")

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
      dropTypes      = StepOrder.filter(steps).flatMap(stepTypeDrops).toSet,
      dropMethods    = StepOrder.filter(steps).flatMap(stepDrops).toSet,
      inject         = StepOrder.filter(steps).flatMap(stepInjects(repoRoot)),
      surface        = StepOrder.filter(steps).flatMap(stepsFor(steps)(_)),
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

    // the one exclusion (1 java test): JUnit's `Parameterized` runner declares
    // `Collection<Object[]> parameters()` and fills it from `new ArrayList<>()` — the collections
    // step's `Collection`/`ArrayList` seam, uncoerced under the merged entry scope (K2 in a TEST tree;
    // the full port coerces it). A named delta, not an edited assertion (standing order 1).
    val excludedFiles = Set("com/badlogic/gdx/math/BezierTest.java")

    val files = Files.walk(testRoot).iterator().asScala
      .filter(p => p.toString.endsWith(".java"))
      .map(p => testRoot.relativize(p).toString)
      .filterNot(f => f.endsWith("package-info.java") || f.endsWith("module-info.java"))
      .filterNot(excludedFiles)
      .toList.sorted

    PortRun(
      label     = "sge-l0-test",
      portRoot  = repoRoot.resolve("ported/sge-l0"),
      sourceSet = SourceSet.Test,
      // NO frontend classpath, as `LibgdxTestMigrate`: with a jar present Spoon leaves the JUnit
      // static imports (`assertTrue`, …) attributed to the suite itself and `TestFrameworkTransform`
      // sees no `org.junit.Assert` call to convert — 161 `E008` on the first test compile.
      frontend  = FrontendConfig(testRoot, files, Nil, resolutionRoots = List(srcRoot)),
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
