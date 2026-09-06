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
    surface     = List(new balticporter.transform.TestFrameworkTransform(dropFields = Set(watcherDrop))) ++
      // CT7: `AnimationControllerTest` is constructed by MUnit, so the threaded context cannot reach
      // it as a parameter — it takes one from the hand-written fixture (`ported/sge-l0/src/test`).
      (if steps("context") then List(new balticporter.transform.GlobalsToImplicitsTransform(extensions = List(
        balticporter.transform.ContextHolderExtension(
          holder       = "com.badlogic.gdx.Gdx",
          selfSupplied = Map("com.badlogic.gdx.graphics.g3d.utils.AnimationControllerTest" -> "sge.SgeTestFixture.testSge()")))))
       else Nil),
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
  /** the GL statics' two-hop path: the property step renames the getter (`gl20`); before it, the call. */
  private def glPath(sel: Set[String], n: String): String =
    if sel("properties") then s"graphics.gl$n" else s"graphics.getGL$n()"
  def Steps: Map[String, List[balticporter.tir.Phase]] = stepsFor(Set.empty)
  def stepsFor(sel: Set[String]): Map[String, List[balticporter.tir.Phase]] = Map(
    "witness" -> List(
      new balticporter.transform.GlobalsToImplicitsTransform(requiredGivens =
        balticporter.transform.ElementWitnessTransform.constructorGivens(CoreWitnessSubjects, LlsPolicy.Witness)),
      new balticporter.transform.ElementWitnessTransform(
        witness      = LlsPolicy.Witness,
        subjectTypes = CoreWitnessSubjects,
        // the clause is threaded; java's implicit `<: Object` bound STAYS on core's subjects — their
        // collaborators (`ObjectSet[T]`) keep theirs (ENGINE-LIMITS.md K48).
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
    // sge's renames that need no injection: `Disposable -> java.lang.AutoCloseable` (`dispose` ->
    // `close`, whole override component), and the two member renames the full port carries
    // (`InputEvent.type` -> `eventType`, `List.toString(T)` -> `itemToString`: java overloads
    // `Object.toString`, scala reads a clash).
    "renames" -> List(
      new balticporter.transform.TypeRedirectTransform(
        redirects     = Map("com.badlogic.gdx.utils.Disposable" -> "java.lang.AutoCloseable"),
        memberRenames = Map("com.badlogic.gdx.utils.Disposable" -> Map("dispose" -> "close"))),
      new balticporter.transform.MemberRenameTransform(renames = Map(
        "com.badlogic.gdx.scenes.scene2d.InputEvent#type"       -> "eventType",
        "com.badlogic.gdx.scenes.scene2d.ui.List#toString(T)"   -> "itemToString"))),
    // the implicit `Sge` context instead of the `Gdx` globals: the full port's holder policy lifted
    // verbatim (attach on the CLASS, read by `summon`, refuse at the boundary, two lazy statics);
    // the context type is the injected `sge.Sge`. Late by design: every constructor moves.
    "context" -> List(new balticporter.transform.GlobalsToImplicitsTransform(holders = List(
      balticporter.transform.ContextHolder(
        holder   = "com.badlogic.gdx.Gdx",
        context  = balticporter.transform.ContextType.Injected("sge.Sge"),
        members  = Map(
          "app" -> "application", "graphics" -> "graphics", "audio" -> "audio", "input" -> "input",
          "files" -> "files", "net" -> "net",
          // the GL statics, two hops through the service that owns them — as GETTER CALLS until the
          // property step renames them (`graphics.gl20` in the full port).
          "gl" -> glPath(sel, "20"), "gl20" -> glPath(sel, "20"), "gl30" -> glPath(sel, "30"),
          "gl31" -> glPath(sel, "31"), "gl32" -> glPath(sel, "32")),
        attach   = balticporter.transform.ContextAttach.Class,
        reader   = balticporter.transform.ContextReader.Summon,
        boundary = balticporter.transform.ContextBoundary.Refuse,
        sites    = Map(
          "com.badlogic.gdx.scenes.scene2d.ui.TextField#DEFAULT_ONSCREEN_KEYBOARD" -> balticporter.transform.ContextSite.LazyInit,
          "com.badlogic.gdx.scenes.scene2d.ui.Table#cellPool" -> balticporter.transform.ContextSite.LazyInit))))),
    // `Seconds`: a frame delta is not a bare `Float` (sge's opaque type, injected from sge's own
    // file). Seeded at the two producers on `Graphics`; the phase propagates along pure moves
    // (`render(delta)`, `act(delta)`) and coerces at the boundary.
    "seconds" -> List(new balticporter.transform.PrimitiveToOpaqueTransform(balticporter.tir.OpaqueSpec(
      fqn        = "com.badlogic.gdx.utils.Seconds",
      target     = balticporter.tir.OpaqueSpec.Target.Existing(
        typeFqn = "sge.utils.Seconds", wrapName = "apply", unwrapName = "toFloat"),
      hints      = Set("com.badlogic.gdx.Graphics#getDeltaTime", "com.badlogic.gdx.Graphics#getRawDeltaTime"),
      underlying = balticporter.tir.OpaqueSpec.Primitive.Float,
      // core's own declarations, never the base's: a shared int utility (`MathUtils.isPowerOfTwo`)
      // is a HUB the symmetric propagation would otherwise ride into unrelated ints (a touch bitmask).
      scope      = balticporter.tir.RuleScope.Everywhere(Set.empty)))),
    // `Pool` as sge's TRAIT (injected from sge's own files, with `Pool.Default`, `Pool.Flushable`
    // and the `Poolable` type class the demos use): java's `Pool`/`DefaultPool`/`FlushablePool` go,
    // their references re-point, and a subclass's constructor arguments become the trait's
    // abstract vals (the full port's `ClassToTraitTransform` specs).
    "pool" -> List(
      new balticporter.transform.TypeRedirectTransform(
        // `FlushablePool` stays java's CLASS over the injected trait (as in the full port): a subclass
        // with several constructors passing different `super(...)` arguments cannot map onto one val.
        redirects = Map(
          "com.badlogic.gdx.utils.DefaultPool"               -> "sge.utils.Pool.Default",
          "com.badlogic.gdx.utils.DefaultPool$PoolSupplier"  -> "scala.Function0"), // `T get()` is `() => A`
        memberRenames = Map("com.badlogic.gdx.utils.DefaultPool$PoolSupplier" -> Map("get" -> "apply"))),
      new balticporter.transform.ClassToTraitTransform(specs = Map(
        "com.badlogic.gdx.utils.Pool" -> PoolMappings))),
    // `Pixels`: a screen coordinate or size is not a bare `Int` (sge's opaque type, injected).
    // Seeded at the producers on `Graphics` and `Input` and at the two resize callbacks; the
    // phase propagates along pure moves and coerces at the boundary.
    "pixels" -> List(new balticporter.transform.PrimitiveToOpaqueTransform(balticporter.tir.OpaqueSpec(
      fqn        = "com.badlogic.gdx.Pixels",
      target     = balticporter.tir.OpaqueSpec.Target.Existing(
        typeFqn = "sge.Pixels", wrapName = "apply", unwrapName = "toInt"),
      hints      = Set(
        "com.badlogic.gdx.Graphics#getWidth", "com.badlogic.gdx.Graphics#getHeight",
        "com.badlogic.gdx.Graphics#getBackBufferWidth", "com.badlogic.gdx.Graphics#getBackBufferHeight",
        "com.badlogic.gdx.Graphics#getSafeInsetLeft", "com.badlogic.gdx.Graphics#getSafeInsetTop",
        "com.badlogic.gdx.Graphics#getSafeInsetBottom", "com.badlogic.gdx.Graphics#getSafeInsetRight",
        "com.badlogic.gdx.Input#getX", "com.badlogic.gdx.Input#getY",
        "com.badlogic.gdx.Input#getDeltaX", "com.badlogic.gdx.Input#getDeltaY",
        // a PARAMETER seed is `owner#method#param`
        "com.badlogic.gdx.ApplicationListener#resize#width", "com.badlogic.gdx.ApplicationListener#resize#height",
        "com.badlogic.gdx.Screen#resize#width", "com.badlogic.gdx.Screen#resize#height"),
      underlying = balticporter.tir.OpaqueSpec.Primitive.Int,
      // core's own declarations, never the base's: a shared int utility (`MathUtils.isPowerOfTwo`)
      // is a HUB the symmetric propagation would otherwise ride into unrelated ints (a touch bitmask).
      scope      = balticporter.tir.RuleScope.Everywhere(Set.empty)))),
    // properties and parenless getters: the full port's bean pairs and targets (`LibgdxPolicy`),
    // lifted by reference, on core's entry; the `Only` scope merges with lls's arity instance.
    "properties" -> List(
      new balticporter.transform.BeanPropertyTransform(LibgdxPolicy.beanPropertyPairs, LibgdxPolicy.beanPropertyTargets,
        scope = balticporter.tir.RuleScope.Only(Set("com.badlogic.gdx"))),
      new balticporter.transform.NullaryArityTransform(scope = balticporter.tir.RuleScope.Only(Set("com.badlogic.gdx")))),
    // sge's graphics API spellings the demos use: `ShapeRenderer.rect -> rectangle` (its four
    // overloads, one component) and the `drawing(type) { … }` helper around `begin`/`end`.
    "graphics" -> List(
      new balticporter.transform.MemberRenameTransform(renames = Map(
        "com.badlogic.gdx.graphics.glutils.ShapeRenderer#rect" -> "rectangle")),
      new balticporter.transform.AddMembersTransform(Map(
        "com.badlogic.gdx.graphics.glutils.ShapeRenderer" -> List(
          balticporter.transform.AddMembersTransform.MemberSpec("drawing", 2,
            "inline def drawing[A](shapeType: ShapeRenderer.ShapeType)(inline body: => A): A = { begin(shapeType); try body finally end() }",
            balticporter.tir.Reason.Configured("add-members", "com.badlogic.gdx.graphics.glutils.ShapeRenderer#drawing"),
            Some("sge's `drawing(type) { … }` around `begin`/`end`, `end` guaranteed (PROGRESS.md §13.29)"), false),
          balticporter.transform.AddMembersTransform.MemberSpec("drawing", 1,
            "inline def drawing[A](inline body: => A): A = { begin(); try body finally end() }",
            balticporter.tir.Reason.Configured("add-members", "com.badlogic.gdx.graphics.glutils.ShapeRenderer#drawing"),
            Some("sge's `drawing { … }` around `begin()`/`end` (auto shape type) (PROGRESS.md §13.29)"), false))))),
    "reflection" -> List(
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

  /** `Pool(int initialCapacity, int max)` onto sge's trait: the two vals a subclass site overrides;
    * a site passing no argument keeps the trait's defaults (java's, carried by the injected file). */
  val PoolMappings: List[balticporter.transform.ClassToTraitTransform.ParamMapping] = List(
    balticporter.transform.ClassToTraitTransform.ParamMapping(0, "initialCapacity"),
    balticporter.transform.ClassToTraitTransform.ParamMapping(1, "max"))

  /** per step, the TYPES it removes (each replaced by an injection or made dead by the step). */
  val stepTypeDrops: Map[String, Set[String]] = Map(
    "pool" -> Set("com.badlogic.gdx.utils.Pool", "com.badlogic.gdx.utils.DefaultPool"),
    // the JVM-only `HttpURLConnection` client: nothing in core references it; the backends supply
    // their own `Net` (sge's capability convention, PROGRESS.md §13.29 R9).
    "net" -> Set("com.badlogic.gdx.net.NetJavaImpl"),
    "reflection" -> Set(
      "com.badlogic.gdx.utils.Json",
      // the `Class`-keyed static pool registry minted `ReflectionPool`s and registers, at class
      // initialisation, constructors that take the context; no core reader; sge has no `Pools`.
      "com.badlogic.gdx.utils.Pools",
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
    "context"    -> List(repoRoot.resolve("balticporter/corpus/ladder-overrides-context")),
    "seconds"    -> List(repoRoot.resolve("balticporter/corpus/ladder-overrides-seconds")),
    "pool"       -> List(repoRoot.resolve("balticporter/corpus/ladder-overrides-pool")),
    "pixels"     -> List(repoRoot.resolve("balticporter/corpus/ladder-overrides-pixels")),
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

  val StepOrder: List[String] = List("witness", "collections", "nullability", "enrich", "reflection", "net", "renames", "context", "seconds", "pool", "pixels", "properties", "graphics")
  /** the steps LANDED so far (measured, baselined, PROGRESS.md §13.29). */
  val DefaultSteps: Set[String] = Set("witness", "collections", "nullability", "enrich", "reflection", "net", "renames", "context", "seconds", "pool", "pixels", "graphics")

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
