package balticporter.corpus.libgdx

import balticporter.core.{FrontendConfig, PortManifest, Provenance, RuntimeMode, Substitutions}
import balticporter.runner.{Determinism, PortRun, SourceSet, VendoredCommit}
import balticporter.transform.{ClassTableTransform, CollectionsTransform, MutableParamsTransform, PanamaFfiTransform, StaticForwarderTransform, TestFrameworkTransform}

import java.nio.file.{Files, Path}
import scala.jdk.CollectionConverters.*

/** Migrate libGDX's CORE module (`gdx/src`, 605 types — the backend-agnostic, JDK-only heart
  * of libGDX) through the TIR to the `libgdx-core` sbt submodule, then compile it with
  * `sbt libgdx-core/compile`.
  *
  *   corpus/runMain balticporter.corpus.libgdx.LibgdxCoreMigrate [--raw] [--determinism=full]
  *
  * `--raw` skips the transform pipeline (libGDX core uses its own collections, so the java
  * collections transform barely applies here; the port is essentially structural).
  *
  * ==This file is POLICY ONLY==
  * Everything below the `PortRun(...)` call used to live here too — the dropped-type emission skip,
  * the support-source write-out, the injection copy, and the two substitution checks — about 80
  * lines of ENGINE logic that the skill for adding a library told the next port to copy. That is
  * the `ReflectionToPortableTransform` mistake one level up (CLAUDE.md §1), and it is why
  * [[LibgdxTestMigrate]] went its whole life without calling `PortabilityCheck`. It is all in
  * [[balticporter.runner.PortRun]] now, where it cannot be forgotten. What is left here is what
  * genuinely differs between libGDX and the next library: the manifest, the transform arguments,
  * and the provenance.
  */
object LibgdxCoreMigrate:

  def main(args: Array[String]): Unit =
    val raw      = args.contains("--raw")
    val repoRoot = Path.of(sys.props.getOrElse("balticporter.root", ".")).toAbsolutePath.normalize
    val base     = repoRoot.resolve("../sge/original-src/libgdx/gdx/src").normalize

    val files = Files.walk(base).iterator().asScala
      .filter(p => p.toString.endsWith(".java"))
      .map(p => base.relativize(p).toString)
      .filterNot(f => f.endsWith("package-info.java") || f.endsWith("module-info.java"))
      .toList.sorted

    PortRun(
      label     = "libgdx-core",
      portRoot  = repoRoot.resolve("libgdx-core"),
      sourceSet = SourceSet.Main,
      frontend  = FrontendConfig(base, files, Nil, Nil),
      phases    = Nil, // supplied by the manifest — the two sources are mutually exclusive
      // The BASE manifest. `libgdx-test` is a dependent of exactly this value, so the two runs
      // cannot disagree about the shared surface by construction, and `ManifestAgreement` verifies
      // it anyway on every run (a consumer is free to write the dependent's policy out longhand).
      manifest  = Some(if raw then LibgdxPolicy.core(repoRoot).withoutSurface else LibgdxPolicy.core(repoRoot)),
      provenance = Some(Provenance(
        upstreamName     = "libGDX",
        upstreamCommit   = VendoredCommit.of(base),
        originalLicense  = "Apache-2.0",
        sourcePathPrefix = "gdx/src",
        sourceRoot       = base.toString,
      )),
      // A SINGLE source set compiled standalone by `scala-cli`, with no dependency resolution: the
      // support types the collections phase retyped onto have to ship beside the emitted code.
      // The TEST source set of the same module must NOT vendor them again — see LibgdxTestMigrate.
      runtimeMode = RuntimeMode.Vendored,
      determinism = Determinism.fromArgs(args.toSeq),
      nextStep    = "sbt libgdx-core/compile",
    ).execute()

/** libGDX's per-library policy, in one place because two source sets share it.
  *
  * CLAUDE.md §1: none of this may live in the engine. What is here is the WHICH — which types are
  * not translated, which wrapper forwards to which receiver, which reflective lookup becomes which
  * table. The engine holds the mechanism for each, parameterised.
  */
object LibgdxPolicy:

  /** libGDX core's policy AS A VALUE — the thing every dependent module imports and extends.
    *
    * Everything in it is shared-surface policy: the types and methods this port does not translate
    * mechanically, and the phases that reshape the signatures a dependent module compiles against.
    * What is NOT here is what a dependent may decide for itself — its source set, its provenance,
    * its `runtimeMode`, and its own injections. See [[balticporter.core.PortManifest]] for where
    * that line is drawn and why `inject` sits on the must-differ side of it.
    *
    * `governs` is the namespace claim. libGDX's own test suite declares its suites INSIDE
    * `com.badlogic.gdx` (`com.badlogic.gdx.utils.JsonTest` and friends), so a prefix cannot
    * separate the two modules — which is exactly why the substitution half of the agreement check
    * works from unit origins instead. The claim is still worth stating: it is what catches a
    * dependent that renames part of this namespace on its own.
    */
  def core(repoRoot: Path): PortManifest =
    val s = substitutions(repoRoot)
    PortManifest(
      name        = "libgdx-core",
      governs     = Set("com.badlogic.gdx"),
      dropTypes   = s.dropTypes,
      dropMethods = s.dropMethods,
      inject      = s.inject,
      surface     = mainPhases,
      // The namespace the CONSUMER actually uses. sge is `package sge`, with libGDX's own
      // subpackages carried straight through — `sge/maps`, `sge/scenes`, `sge/math`, `sge/graphics`
      // are all there in the hand port — so one prefix pair moves the whole library and
      // longest-prefix-wins does the rest.
      //
      // Emitting the upstream namespace was never a cosmetic mismatch: neither sge nor ssg can
      // adopt output that declares `com.badlogic.gdx`, because their entire dependent codebase is
      // written against the renamed one.
      //
      // The keys of every OTHER policy — dropTypes, dropMethods, the forwarder and class-table
      // maps — stay upstream, because they are consulted at the frontend, before this runs.
      packageRenames = Map("com.badlogic.gdx" -> "sge"),
    )

  /** libGDX's own JUnit suite, as a DEPENDENT of [[core]].
    *
    * It adds one phase and inherits everything else. The hand-written pipeline it replaces listed
    * the shared phases again, minus two, with a comment arguing that the two were unnecessary — a
    * correct argument that nothing checked and that the next module would have had to make again.
    */
  def test(repoRoot: Path): PortManifest = core(repoRoot).extendedBy(PortManifest(
    name    = "libgdx-test",
    surface = List(new TestFrameworkTransform(), selfSuppliedSuites),
  ))

  /** THE ONE `selfSupplied` ENTRY — `ENGINE-LIMITS.md` CT7, contributed the way CT8 says a dependent
    * contributes: a [[ContextHolderExtension]], which has NO field in which the shared half could be
    * restated.
    *
    * `AnimationControllerTest` constructs `new Model()`, `Model` is one of the 188 classes
    * [[globalsToContext]] threads, so the instantiate edge threads the suite and `attach = "class"`
    * puts the clause on its constructor. Every step of that is the design working — and MUnit
    * constructs a suite REFLECTIVELY, which cannot supply a `using`. The result compiled at 0 errors
    * with `context-seam` 0 and `policy` 0, and five tests silently stopped running; only §5.1's
    * `tests.tsv` DID-NOT-RUN gate saw it.
    *
    * So the suite takes the context WITHOUT taking a parameter: java's constructor signature stands
    * and the engine emits `private given sge.Sge = sge.SgeTestFixture.testSge()` at the head of its
    * body. That is the reference hand port's own shape for this very file
    * (`../sge/.../AnimationControllerTest.scala`), reached from policy rather than by editing
    * generated code — which §5.5 forbids and which no consumer could do anyway.
    *
    * ==Why the value is an ABSENT-SERVICE fixture and not a noop one==
    * See `libgdx-core/src/test/scala/sge/SgeTestFixture.scala`. The one affected suite reaches no
    * service at all, so a stub that ANSWERS would let a test pass while asserting nothing about the
    * thing it was answering for.
    *
    * ==Why this is the DEPENDENT's manifest and not the base's==
    * The key is a declaration in the TEST source set, which the base neither parses nor emits.
    * Putting it in [[core]] would bind every module that inherits the base — six of them — to a
    * `selfSupplied` key none of them can ever match, which is six permanently unclearable `policy`
    * rows: exactly the noise floor [[beanProperties]] already documents. The entry sits inside
    * `governs = com.badlogic.gdx` and is admitted because the screen asks what the base EMITS, per
    * its published port map, and the base emits nothing at that name (`ENGINE-LIMITS.md` CT9). */
  def selfSuppliedSuites: balticporter.transform.GlobalsToImplicitsTransform =
    new balticporter.transform.GlobalsToImplicitsTransform(extensions = List(
      balticporter.transform.ContextHolderExtension(
        holder       = "com.badlogic.gdx.Gdx",
        selfSupplied = Map(
          "com.badlogic.gdx.graphics.g3d.utils.AnimationControllerTest" ->
            "sge.SgeTestFixture.testSge()",
        ),
      )
    ))

  /** Typed substitution manifest: constructs sge dropped upstream (and their ready-made Scala
    * replacements). `dropTypes`/`dropMethods` are the seams for opting an in-source type or method
    * out of mechanical translation when a replacement is supplied here.
    *
    * NB: a dropped type is still PARSED — only its OUTPUT is replaced by the injected Scala.
    * Removing it from the model instead would leave references to it unresolved, silently degrading
    * translation of the code that USES it (a `Field` of unknown type stops being recognised as a
    * non-String operand, so Java string concat loses its `String.valueOf` wrap). */
  def substitutions(repoRoot: Path): Substitutions = Substitutions(
    // `utils.reflect` is libGDX's thin cross-platform wrapper over `java.lang.reflect`. sge does
    // not port it — the reflection-driven decoding it served was replaced by Kindlings'
    // Jsoniter/UBJson codecs — so it is substituted wholesale by injected Scala at the same FQNs.
    dropTypes = Set(
      // the reflection-based serializer itself — replaced by Kindlings Jsoniter/UBJson codecs.
      // (JsonValue/JsonReader/JsonWriter/JsonMatcher are DOM/parsing types and port fine.)
      "com.badlogic.gdx.utils.Json",
      // `Pools` fabricated a pool from a `Class` via `ReflectionPool` (reflective no-arg ctor
      // lookup + invoke) — the one thing Scala.js/Native cannot do. Replaced by an injected
      // `Pools` whose creation path takes a factory; `ReflectionPool` is dropped outright
      // (upstream deprecated it for the factory-backed `DefaultPool`, which ports mechanically).
      "com.badlogic.gdx.utils.Pools",
      // dropped with NO replacement — every reference eliminated, so CHECK 2 proves they are gone
      "com.badlogic.gdx.utils.ReflectionPool",
      "com.badlogic.gdx.utils.reflect.Annotation",
      "com.badlogic.gdx.utils.reflect.Field",
      "com.badlogic.gdx.utils.reflect.ArrayReflection",
      "com.badlogic.gdx.utils.reflect.ClassReflection",
      "com.badlogic.gdx.utils.reflect.Constructor",
      "com.badlogic.gdx.utils.reflect.Method",
      "com.badlogic.gdx.utils.reflect.ReflectionException",
      // `NetJavaImpl` implements `Net`'s HTTP half over `java.net.HttpURLConnection` — a type
      // Scala.js and Scala Native do not have, so NO member of it survives to either target and
      // there is nothing to port mechanically. It is a BACKEND helper: nothing in `gdx/src`
      // references it (the desktop/android backends do), so like `ReflectionPool` it is dropped
      // with no replacement and CHECK 2 proves the references are gone. The portable `Net`
      // interface, `HttpRequestBuilder`, `HttpStatus` and `HttpParametersUtils` all stay.
      //
      // NB — dropping it CONCEALS a real engine gap, recorded in ENGINE-LIMITS.md K2: the one
      // compile error it produced was `CollectionsTransform` rewriting OUR signature to
      // `mutable.Map[String, Buffer[String]]` while the body returned an unported JDK method's
      // real `java.util.Map`. That JDK/Scala collection boundary is universal and still open;
      // this drop is justified by portability alone and must not be read as closing it.
      "com.badlogic.gdx.net.NetJavaImpl",
      // dropped with NO replacement, and the pairing is the whole of what keeps
      // `sge/utils/Disposable.scala` from shipping: [[disposableRedirect]] re-points every
      // REFERENCE at `java.lang.AutoCloseable` and a redirect never deletes a declaration
      // (`ENGINE-LIMITS.md` D8). There is nothing to inject — the target is the JDK's own type,
      // which already exists everywhere the port compiles.
      "com.badlogic.gdx.utils.Disposable",
    ),
    // libGDX itself deprecated `setEnabledReflection` (superseded by the typed
    // `setEnabled(Styleable, Boolean)`, already ported); its private `findMethod` helper was the
    // only reflective method scan left. Dropping both removes the last use of `reflect.Method`.
    dropMethods = Set(
      "com.badlogic.gdx.scenes.scene2d.ui.Skin#setEnabledReflection",
      "com.badlogic.gdx.scenes.scene2d.ui.Skin#findMethod",
      // `ArrayReflection` — `java.lang.reflect.Array`, which neither Scala.js nor Native has.
      // Every remaining use of it sits inside a member libGDX ITSELF deprecated in favour of an
      // `ArraySupplier` overload that is already portable (`Array(boolean, int, ArraySupplier)`,
      // `toArray(ArraySupplier)`, `ChannelDescriptor(int, ArraySupplier, int)`, …). The corpus
      // calls none of the deprecated forms — `ParticleChannels` already builds every descriptor
      // with `float[]::new` — so dropping them costs no call site and removes the last JVM-only
      // dependency outright. Overload-precise keys: the `ArraySupplier` twins must survive.
      "com.badlogic.gdx.utils.Array#<init>(boolean,int,Class)",
      "com.badlogic.gdx.utils.Array#<init>(Class)",
      "com.badlogic.gdx.utils.Array#toArray(Class)",
      "com.badlogic.gdx.utils.Array#of(Class)",
      "com.badlogic.gdx.utils.Array#of(boolean,int,Class)",
      // the Array subclasses' own deprecated pairs only forward to Array's, so they go with them.
      // (`DelayedRemovalArray` was found by RewriteTrace's orphaned-call check, not by grep.)
      "com.badlogic.gdx.utils.SnapshotArray#<init>(boolean,int,Class)",
      "com.badlogic.gdx.utils.SnapshotArray#<init>(Class)",
      "com.badlogic.gdx.utils.DelayedRemovalArray#<init>(boolean,int,Class)",
      "com.badlogic.gdx.utils.DelayedRemovalArray#<init>(Class)",
      "com.badlogic.gdx.utils.ArrayMap#<init>(boolean,int,Class,Class)",
      "com.badlogic.gdx.utils.ArrayMap#<init>(Class,Class)",
      "com.badlogic.gdx.utils.Queue#<init>(int,Class)",
      "com.badlogic.gdx.graphics.g3d.particles.ParallelArray$ChannelDescriptor#<init>(int,Class,int)",
      // the one in-corpus caller of a dropped ctor: itself deprecated, with an `ArraySupplier`
      // twin, and both subclasses (`BillboardParticleBatch`, `PointSpriteParticleBatch`) already
      // call the twin — so it too goes with no call site left behind.
      "com.badlogic.gdx.graphics.g3d.particles.batches.BufferedParticleBatch#<init>(Class)",
    ),
    // `SharedLibraryLoader`/`Os` were removed in favour of a dedicated native-extraction library;
    // the corpus still references them, so we inject standalone Scala at those FQNs.
    inject = List(repoRoot.resolve("corpus/libgdx-overrides")),
  )

  /** libGDX's ONE runtime class lookup by name (`ResourceData.AssetData` resolving a persisted
    * asset type) has no counterpart off the JVM, so it is re-pointed at an explicit name->class
    * table — injected as `particles.AssetTypeRegistry`, seeded with the types libGDX core itself
    * stores and open for a downstream port to extend. */
  def classTable: ClassTableTransform = new ClassTableTransform(Map(
    "com.badlogic.gdx.utils.reflect.ClassReflection#forName" ->
      "com.badlogic.gdx.graphics.g3d.particles.AssetTypeRegistry#classFor"
  ))

  /** libGDX routes every reflective operation through `ClassReflection` so its GWT/Android
    * backends can supply their own implementation. sge drops that wrapper: it targets Scala
    * Native and Scala.js, where runtime reflection does not exist. But most of what the corpus
    * actually calls is not reflection at all — these are plain `java.lang.Class` members that
    * both platforms DO provide, reached through the call's first argument. Re-pointing them
    * leaves behind exactly the members that genuinely need replacing (`forName` above, and the
    * declared-field/method/constructor readers, which stay in `Substitutions.dropTypes`). */
  def unwrapReflection: StaticForwarderTransform = new StaticForwarderTransform(List(
    StaticForwarderTransform.Forwarder(
      wrapper  = "com.badlogic.gdx.utils.reflect.ClassReflection",
      receiver = "java.lang.Class",
      // exactly the wrapper's OWN one-arg pass-throughs to `java.lang.Class` that the corpus
      // reaches. `getName` sat here from the first draft and matched nothing on any run —
      // `ClassReflection` never declared it — and the `policy / never matched` finding that
      // reported it is the check this line answers to: a key that matches nothing is either a
      // typo for a member that needed forwarding, or dead policy. This one was dead.
      members  = Set("getSimpleName", "isInstance", "isAssignableFrom", "isArray",
                     "isEnum", "isInterface", "isPrimitive", "isAnnotation", "getComponentType"),
    )
  ))

  /** `java.util.Comparator` → `scala.math.Ordering`, the port's one RETARGET entry.
    *
    * A retarget moves a type at every occurrence and API-maps it NOWHERE — no kind, no factory, no
    * `coerce` boundary. What licenses that here is a single fact about the two standard libraries:
    * Scala declares `trait Ordering[T] extends java.util.Comparator[T]`, so the scala target is
    * usable everywhere the java source was. Three consequences, and they are the whole of what this
    * entry costs:
    *
    *   - a declaration moves BARE. An `Ordering[T]` reaching a slot that still says `Comparator` —
    *     the JDK's own `Arrays.sort`, the engine's `JavaCollections.sort` — already IS one, so
    *     nothing is bridged and `CollectionBoundaryCheck` has nothing to count;
    *   - `implements Comparator<T>` becomes `extends Ordering[T]` with the `compare(a, b)` under it
    *     structurally unchanged, because `compare` is `Ordering`'s ONE abstract member. That is also
    *     what keeps an anonymous `new Comparator<Pixmap>(){…}` — libGDX has four — a valid
    *     `new Ordering[Pixmap]{…}`, and a java lambda SAM-convertible;
    *   - no call site is rewritten at all. `cmp.compare(a, b)` binds to `Ordering.compare`.
    *
    * ==Why there is no companion call-site table==
    * `Collections.sort(xs, c)` → `xs.sortInPlace()(using c)` is expressible in the M4 template
    * language and is REFUTED by the compiler: `sortInPlace` is a `mutable.IndexedSeqOps` member and
    * `java.util.List` maps to `mutable.Buffer`, which is not one. After the retarget the existing
    * `JavaCollections.sort` arm is already correct, and `Arrays.sort`'s idiomatic counterpart trades
    * java's documented stability guarantee for legibility, which is not a trade a seam may make
    * silently. Measured — `DESIGN.md` §8.12 and `ComparatorOrderingPortSpec`.
    *
    * This is SHARED SURFACE and therefore lives in [[core]] alone (§1.5): a base whose `Comparator`s
    * became `Ordering`s and a dependent whose did not emit signatures that cannot meet. It joins the
    * phase's `surfaceFingerprint` for exactly that reason. And it is a parameter of the
    * `CollectionsTransform` this manifest ALREADY carries — not a second instance of it, which is
    * what `ENGINE-LIMITS.md` D9 closes for a base with dependents. */
  def comparatorRetarget: Map[String, String] =
    Map("java.util.Comparator" -> "scala.math.Ordering")

  /** `com.badlogic.gdx.utils.Disposable` → `java.lang.AutoCloseable`, with `dispose` → `close`.
    *
    * libGDX's `Disposable` is `void dispose()` and nothing else — the JDK's own `AutoCloseable`
    * under a different name, minus `try`-with-resources and minus `scala.util.Using`. A consumer
    * that keeps the ported name gets neither, forever, for a type that carries no information the
    * JDK's does not; sge's users write `Using(new Pixmap(…))` in ordinary Scala the moment the
    * parent is the JDK's.
    *
    * The redirect alone would emit 47 classes claiming to be an `AutoCloseable` while declaring
    * `dispose()`, so the entry carries `memberRenames` and the phase renames the member's whole
    * PRE-REDIRECT override component first (see [[TypeRedirectTransform]] for why the two cannot be
    * two phases). 66 declarations move together; the 8 `void dispose()` elsewhere in the library
    * that implement no `Disposable` — `LifecycleListener`, `ApplicationListener`, `Game`,
    * `ApplicationAdapter`, `ImmediateModeRenderer`(`20`), `ParticleController`, and a `Timer`
    * anonymous body — keep the name, correctly, because they are a different member.
    *
    * ==Why the paired `dropTypes` entry below is not optional==
    * A redirect re-points REFERENCES and never deletes a DECLARATION (`ENGINE-LIMITS.md` D8). This
    * port OWNS `Disposable`, so without the drop it would emit `sge/utils/Disposable.scala` — a
    * trait nothing refers to, beside 47 classes that all extend the JDK type instead. Nothing
    * reports it: the port still compiles at 0 errors and every check reports the same number. There
    * is no injection, because there is nothing to replace the type WITH; `java.lang.AutoCloseable`
    * already exists.
    *
    * ==Shared surface, and the first base phase that has to MERGE==
    * This is a fact about signatures every dependent compiles against, so it lives in [[core]]
    * (§1.5). It is also a phase two dependents CONSTRUCT for themselves — ashley's `ReflectionPool`
    * redirect and screens' ten guacamole entries — which made it the case `ENGINE-LIMITS.md` D9
    * blocked and `MergeablePolicy` closes: the base's instance and each dependent's fold into one,
    * at the base's pipeline position, and the base's published `policy=` digest does not move.
    * Ashley's added subject is inside libGDX's `governs` claim and is legal because the base DROPS
    * it (`DESIGN.md` §8.13). */
  def disposableRedirect: balticporter.transform.TypeRedirectTransform =
    new balticporter.transform.TypeRedirectTransform(
      redirects     = Map("com.badlogic.gdx.utils.Disposable" -> "java.lang.AutoCloseable"),
      memberRenames = Map("com.badlogic.gdx.utils.Disposable" -> Map("dispose" -> "close")),
    )

  /** libGDX's JavaBean accessor pairs that the reference hand port turned into Scala properties —
    * `def x` / `def x_=(v)`, with every call site rewritten through them (DESIGN.md §8.5).
    *
    * ==Why an INCLUDE LIST and not a pattern (§1b, and the whole of what makes this policy)==
    * Measured against the reference port, not assumed. libGDX core emits 3,234 `get*`/`set*`/`is*`
    * methods; sge KEPT 1,375 of them (684 distinct names) and converted ~223 — a ~14 percent
    * conversion rate, concentrated almost entirely in `maps.*`, `audio` and `scene2d.utils`, with
    * `scene2d.ui`'s big widgets, `graphics.g3d`, `physics` and `utils` untouched. sge even converts
    * the SAME pair differently in two types: `MapLayer#opacity` is a computed `def`, `MapObject#opacity`
    * is a `var`. A blanket `getX` -> `x` rule would rewrite some three thousand members a careful human
    * deliberately left alone, so the map below IS the policy and the engine holds only the mechanism.
    *
    * ==Where it comes from==
    * Every entry is a conversion sge DOCUMENTED in a `Renames:` header — 132 of its 549 files carry
    * one — joined to the upstream FQN through each header's `Original source:` line. Nothing here is
    * derived or extrapolated. The harvest was 144 rows over 38 upstream types; **133 remain, and the
    * eleven that went were noise rather than policy** (below).
    *
    * ==AN ENTRY NAMES A COMPONENT, so a per-IMPLEMENTOR entry is a duplicate==
    * The phase renames the whole override COMPONENT — that is the design's point — so an entry on the
    * type that DECLARES a member already reaches every implementor of it, and a second entry naming
    * an implementor renames exactly the same set again. The `Drawable` family's `getLeftWidth` rows
    * were collapsed to the one INTERFACE entry when the harvest was written; the same collapse was
    * owed to two more families and is now taken: `isManaged` is declared abstract on `GLTexture`
    * (three per-implementor rows for `Cubemap`, `Texture` and `TextureArray` said it three times) and
    * `TiledMapTile` declares all seven of its properties (thirteen rows across
    * `AnimatedTiledMapTile` and `StaticTiledMapTile` said six of them twice). **The duplicates were
    * never inert**: each recorded its own `RenamedMember` decision per member of the component and
    * rendered its own porter note beside the code, so a reader of `StaticTiledMapTile.scala` was
    * being told twice, in two different `key=`s, why one method is called `id`.
    *
    * ==Refusals are the expected outcome for some of it, and they are COUNTED==
    * A pair is applied whole or not at all, and each refusal is a `PolicyIssue.Unverifiable` finding
    * with its cause plus a `ScopedOut` decision. **Two remain, and both are real pending work**:
    * `ScrollPane#scrollX` and `#scrollY` hit a name the emitter's §4.55 passes will not relocate, and
    * completing those get-only entries against an upstream that has setters is a manifest edit
    * nobody has made. **Three others were PERMANENTLY refused and are deleted** —
    * `VertexAttributes#getOffset(int)`, `Polygon#getVertex(int,Vector2)`,
    * `Polygon#getCentroid(Vector2)` all take ARGUMENTS, so there is no nilary getter to convert and
    * the phase will refuse them on every run for as long as the upstream stands. A finding that can
    * never be cleared is a noise floor: it makes `policy > 0` the normal state of this port and
    * teaches its next reader to skim the number that the two survivors need them to read.
    *
    * A twelve-refusal sixth cause is GONE and worth naming so nobody re-adds a workaround for it:
    * `Selection`/`VertexAttributes`/`TiledMapTileSet`/`OrientedBoundingBox` implement `java.lang.Iterable`,
    * `Comparable` or `Serializable`, and their override components used to anchor on an unparsed
    * external. `ExternalSurface.jdkPlatform` closes those member sets exactly (`ENGINE-LIMITS.md` K12),
    * so all twelve now apply.
    *
    * ==Shared surface (§1.5)==
    * This changes emitted SIGNATURES, so it is `SurfacePolicy` and lives in [[core]] alone: a base whose
    * `getOpacity()` became `def opacity` and a dependent whose did not emit signatures that cannot meet.
    * No dependent CONSTRUCTS a `bean-properties` phase, so there is exactly one instance in every
    * effective pipeline and nothing has to merge (§1.5's instance-count question, asked before writing
    * this). It runs FIRST in the pipeline so the descriptors it matches are java's own — `runsBefore`
    * states that for the two engine phases whose names are static, and the list position states the rest.
    */
  def beanProperties: balticporter.transform.BeanPropertyTransform =
    new balticporter.transform.BeanPropertyTransform(beanPropertyPairs)

  /** the harvested pairs. KEY is the emitted property in the UPSTREAM namespace (§4.56 — the package
    * rename runs last); VALUE names the accessors explicitly, because a hand port's names are not
    * always bean-derivable (`getDragActor` -> `currentDragActor`) and a never-fired report needs them
    * as DATA. */
  def beanPropertyPairs: Map[String, String] = Map(
    // -- com.badlogic.gdx.audio --
    "com.badlogic.gdx.audio.AudioDevice#latency" -> "getLatency",
    "com.badlogic.gdx.audio.Music#playing" -> "isPlaying",
    "com.badlogic.gdx.audio.Music#looping" -> "isLooping/setLooping",
    "com.badlogic.gdx.audio.Music#volume" -> "getVolume/setVolume",
    "com.badlogic.gdx.audio.Music#position" -> "getPosition/setPosition",
    // -- com.badlogic.gdx.Graphics --
    // The four GL accessors, and they are here for a MECHANICAL reason rather than a stylistic one:
    // [[globalsToContext]]'s member map re-points `Gdx.gl20` at the path `graphics.gl20`, and a path
    // segment is an IDENTIFIER — so `graphics.gl20` can only land on a member of that name. Without
    // these four the five `gl*` statics would have nowhere to go. (`Gdx.gl` is an alias of `gl20`
    // upstream and maps to the same path, so four pairs serve five statics.)
    "com.badlogic.gdx.Graphics#gl20" -> "getGL20/setGL20",
    "com.badlogic.gdx.Graphics#gl30" -> "getGL30/setGL30",
    "com.badlogic.gdx.Graphics#gl31" -> "getGL31/setGL31",
    "com.badlogic.gdx.Graphics#gl32" -> "getGL32/setGL32",
    // -- com.badlogic.gdx.graphics --
    "com.badlogic.gdx.graphics.Cubemap#cubemapData" -> "getCubemapData",
    // `isManaged` is declared ABSTRACT on `GLTexture` and the phase renames the whole override
    // COMPONENT, so the interface entry covers `Cubemap`, `Texture` and `TextureArray` — three
    // per-implementor entries said the same thing three times.
    "com.badlogic.gdx.graphics.GLTexture#managed" -> "isManaged",
    "com.badlogic.gdx.graphics.Texture#textureData" -> "getTextureData",
    "com.badlogic.gdx.graphics.VertexAttribute#key" -> "getKey",
    "com.badlogic.gdx.graphics.VertexAttributes#mask" -> "getMask",
    "com.badlogic.gdx.graphics.VertexAttributes#maskWithSizePacked" -> "getMaskWithSizePacked",
    // -- com.badlogic.gdx.graphics.g2d --
    "com.badlogic.gdx.graphics.g2d.SpriteCache#customShader" -> "getCustomShader",
    // -- com.badlogic.gdx.graphics.profiling --
    "com.badlogic.gdx.graphics.profiling.GLProfiler#listener" -> "getListener/setListener",
    "com.badlogic.gdx.graphics.profiling.GLProfiler#enabled" -> "isEnabled",
    // -- com.badlogic.gdx.maps --
    "com.badlogic.gdx.maps.Map#layers" -> "getLayers",
    "com.badlogic.gdx.maps.Map#properties" -> "getProperties",
    "com.badlogic.gdx.maps.MapLayer#name" -> "getName/setName",
    "com.badlogic.gdx.maps.MapLayer#visible" -> "isVisible/setVisible",
    "com.badlogic.gdx.maps.MapLayer#objects" -> "getObjects",
    "com.badlogic.gdx.maps.MapLayer#properties" -> "getProperties",
    "com.badlogic.gdx.maps.MapLayer#parallaxX" -> "getParallaxX/setParallaxX",
    "com.badlogic.gdx.maps.MapLayer#parallaxY" -> "getParallaxY/setParallaxY",
    "com.badlogic.gdx.maps.MapLayer#opacity" -> "getOpacity/setOpacity",
    "com.badlogic.gdx.maps.MapLayer#combinedTintColor" -> "getCombinedTintColor",
    "com.badlogic.gdx.maps.MapLayer#tintColor" -> "getTintColor/setTintColor",
    "com.badlogic.gdx.maps.MapLayer#offsetX" -> "getOffsetX/setOffsetX",
    "com.badlogic.gdx.maps.MapLayer#offsetY" -> "getOffsetY/setOffsetY",
    "com.badlogic.gdx.maps.MapLayer#renderOffsetX" -> "getRenderOffsetX",
    "com.badlogic.gdx.maps.MapLayer#renderOffsetY" -> "getRenderOffsetY",
    "com.badlogic.gdx.maps.MapLayer#parent" -> "getParent/setParent",
    "com.badlogic.gdx.maps.MapObject#name" -> "getName/setName",
    "com.badlogic.gdx.maps.MapObject#color" -> "getColor/setColor",
    "com.badlogic.gdx.maps.MapObject#opacity" -> "getOpacity/setOpacity",
    "com.badlogic.gdx.maps.MapObject#visible" -> "isVisible/setVisible",
    "com.badlogic.gdx.maps.MapObject#properties" -> "getProperties",
    // -- com.badlogic.gdx.maps.objects --
    "com.badlogic.gdx.maps.objects.CircleMapObject#circle" -> "getCircle",
    "com.badlogic.gdx.maps.objects.EllipseMapObject#ellipse" -> "getEllipse",
    "com.badlogic.gdx.maps.objects.PointMapObject#point" -> "getPoint",
    "com.badlogic.gdx.maps.objects.PolygonMapObject#polygon" -> "getPolygon/setPolygon",
    "com.badlogic.gdx.maps.objects.PolylineMapObject#polyline" -> "getPolyline/setPolyline",
    "com.badlogic.gdx.maps.objects.RectangleMapObject#rectangle" -> "getRectangle",
    "com.badlogic.gdx.maps.objects.TextMapObject#rectangle" -> "getRectangle",
    "com.badlogic.gdx.maps.objects.TextMapObject#rotation" -> "getRotation/setRotation",
    "com.badlogic.gdx.maps.objects.TextMapObject#text" -> "getText/setText",
    "com.badlogic.gdx.maps.objects.TextMapObject#pixelSize" -> "getPixelSize/setPixelSize",
    "com.badlogic.gdx.maps.objects.TextMapObject#fontFamily" -> "getFontFamily/setFontFamily",
    "com.badlogic.gdx.maps.objects.TextMapObject#bold" -> "isBold/setBold",
    "com.badlogic.gdx.maps.objects.TextMapObject#italic" -> "isItalic/setItalic",
    "com.badlogic.gdx.maps.objects.TextMapObject#underline" -> "isUnderline/setUnderline",
    "com.badlogic.gdx.maps.objects.TextMapObject#strikeout" -> "isStrikeout/setStrikeout",
    "com.badlogic.gdx.maps.objects.TextMapObject#kerning" -> "isKerning/setKerning",
    "com.badlogic.gdx.maps.objects.TextMapObject#wrap" -> "isWrap/setWrap",
    "com.badlogic.gdx.maps.objects.TextMapObject#horizontalAlign" -> "getHorizontalAlign/setHorizontalAlign",
    "com.badlogic.gdx.maps.objects.TextMapObject#verticalAlign" -> "getVerticalAlign/setVerticalAlign",
    "com.badlogic.gdx.maps.objects.TextureMapObject#x" -> "getX/setX",
    "com.badlogic.gdx.maps.objects.TextureMapObject#y" -> "getY/setY",
    "com.badlogic.gdx.maps.objects.TextureMapObject#originX" -> "getOriginX/setOriginX",
    "com.badlogic.gdx.maps.objects.TextureMapObject#originY" -> "getOriginY/setOriginY",
    "com.badlogic.gdx.maps.objects.TextureMapObject#scaleX" -> "getScaleX/setScaleX",
    "com.badlogic.gdx.maps.objects.TextureMapObject#scaleY" -> "getScaleY/setScaleY",
    "com.badlogic.gdx.maps.objects.TextureMapObject#rotation" -> "getRotation/setRotation",
    "com.badlogic.gdx.maps.objects.TextureMapObject#textureRegion" -> "getTextureRegion/setTextureRegion",
    // -- com.badlogic.gdx.maps.tiled --
    "com.badlogic.gdx.maps.tiled.TiledMapImageLayer#region" -> "getTextureRegion/setTextureRegion",
    "com.badlogic.gdx.maps.tiled.TiledMapImageLayer#x" -> "getX/setX",
    "com.badlogic.gdx.maps.tiled.TiledMapImageLayer#y" -> "getY/setY",
    "com.badlogic.gdx.maps.tiled.TiledMapImageLayer#repeatX" -> "isRepeatX/setRepeatX",
    "com.badlogic.gdx.maps.tiled.TiledMapImageLayer#repeatY" -> "isRepeatY/setRepeatY",
    "com.badlogic.gdx.maps.tiled.TiledMapTileSet#name" -> "getName/setName",
    "com.badlogic.gdx.maps.tiled.TiledMapTileSet#properties" -> "getProperties",
    // `TiledMapTile` DECLARES all seven, and the phase renames the whole override component, so
    // one entry each covers `AnimatedTiledMapTile` and `StaticTiledMapTile` — thirteen
    // per-implementor entries said the same thing twice over.
    "com.badlogic.gdx.maps.tiled.TiledMapTile#id" -> "getId/setId",
    "com.badlogic.gdx.maps.tiled.TiledMapTile#blendMode" -> "getBlendMode/setBlendMode",
    "com.badlogic.gdx.maps.tiled.TiledMapTile#textureRegion" -> "getTextureRegion/setTextureRegion",
    "com.badlogic.gdx.maps.tiled.TiledMapTile#offsetX" -> "getOffsetX/setOffsetX",
    "com.badlogic.gdx.maps.tiled.TiledMapTile#offsetY" -> "getOffsetY/setOffsetY",
    "com.badlogic.gdx.maps.tiled.TiledMapTile#properties" -> "getProperties",
    "com.badlogic.gdx.maps.tiled.TiledMapTile#objects" -> "getObjects",
    // -- com.badlogic.gdx.maps.tiled.objects --
    "com.badlogic.gdx.maps.tiled.objects.TiledMapTileMapObject#flipHorizontally" -> "isFlipHorizontally/setFlipHorizontally",
    "com.badlogic.gdx.maps.tiled.objects.TiledMapTileMapObject#flipVertically" -> "isFlipVertically/setFlipVertically",
    "com.badlogic.gdx.maps.tiled.objects.TiledMapTileMapObject#tile" -> "getTile/setTile",
    // -- com.badlogic.gdx.maps.tiled.renderers --
    "com.badlogic.gdx.maps.tiled.renderers.HexagonalTiledMapRenderer#staggerAxisX" -> "isStaggerAxisX/setStaggerAxisX",
    "com.badlogic.gdx.maps.tiled.renderers.HexagonalTiledMapRenderer#staggerIndexEven" -> "isStaggerIndexEven/setStaggerIndexEven",
    "com.badlogic.gdx.maps.tiled.renderers.HexagonalTiledMapRenderer#hexSideLength" -> "getHexSideLength/setHexSideLength",
    // -- com.badlogic.gdx.maps.tiled.tiles --
    "com.badlogic.gdx.maps.tiled.tiles.AnimatedTiledMapTile#currentFrameIndex" -> "getCurrentFrameIndex",
    "com.badlogic.gdx.maps.tiled.tiles.AnimatedTiledMapTile#currentFrame" -> "getCurrentFrame",
    "com.badlogic.gdx.maps.tiled.tiles.AnimatedTiledMapTile#animationIntervals" -> "getAnimationIntervals/setAnimationIntervals",
    "com.badlogic.gdx.maps.tiled.tiles.AnimatedTiledMapTile#frameTiles" -> "getFrameTiles",
    // -- com.badlogic.gdx.math --
    "com.badlogic.gdx.math.Polygon#vertices" -> "getVertices",
    "com.badlogic.gdx.math.Polygon#transformedVertices" -> "getTransformedVertices",
    "com.badlogic.gdx.math.Polygon#vertexCount" -> "getVertexCount",
    "com.badlogic.gdx.math.Polygon#boundingRectangle" -> "getBoundingRectangle",
    "com.badlogic.gdx.math.Polygon#rotation" -> "getRotation",
    // -- com.badlogic.gdx.math.collision --
    "com.badlogic.gdx.math.collision.OrientedBoundingBox#vertices" -> "getVertices",
    "com.badlogic.gdx.math.collision.OrientedBoundingBox#bounds" -> "getBounds",
    // -- com.badlogic.gdx.scenes.scene2d.ui --
    "com.badlogic.gdx.scenes.scene2d.ui.List#cullingArea" -> "getCullingArea",
    "com.badlogic.gdx.scenes.scene2d.ui.ScrollPane#scrollX" -> "getScrollX",
    "com.badlogic.gdx.scenes.scene2d.ui.ScrollPane#scrollY" -> "getScrollY",
    "com.badlogic.gdx.scenes.scene2d.ui.ScrollPane#overscrollDistance" -> "getOverscrollDistance",
    "com.badlogic.gdx.scenes.scene2d.ui.ScrollPane#fadeScrollBars" -> "getFadeScrollBars",
    "com.badlogic.gdx.scenes.scene2d.ui.ScrollPane#variableSizeKnobs" -> "getVariableSizeKnobs",
    "com.badlogic.gdx.scenes.scene2d.ui.SelectBox#clickListener" -> "getClickListener",
    // -- com.badlogic.gdx.scenes.scene2d.utils --
    "com.badlogic.gdx.scenes.scene2d.utils.BaseDrawable#name" -> "getName",
    "com.badlogic.gdx.scenes.scene2d.utils.ClickListener#tapSquareSize" -> "getTapSquareSize/setTapSquareSize",
    "com.badlogic.gdx.scenes.scene2d.utils.ClickListener#tapCount" -> "getTapCount/setTapCount",
    "com.badlogic.gdx.scenes.scene2d.utils.ClickListener#button" -> "getButton/setButton",
    "com.badlogic.gdx.scenes.scene2d.utils.ClickListener#pressedButton" -> "getPressedButton",
    "com.badlogic.gdx.scenes.scene2d.utils.ClickListener#pressedPointer" -> "getPressedPointer",
    "com.badlogic.gdx.scenes.scene2d.utils.ClickListener#pressed" -> "isPressed",
    "com.badlogic.gdx.scenes.scene2d.utils.ClickListener#over" -> "isOver",
    "com.badlogic.gdx.scenes.scene2d.utils.ClickListener#touchDownX" -> "getTouchDownX",
    "com.badlogic.gdx.scenes.scene2d.utils.ClickListener#touchDownY" -> "getTouchDownY",
    "com.badlogic.gdx.scenes.scene2d.utils.DragAndDrop#currentDragActor" -> "getDragActor",
    "com.badlogic.gdx.scenes.scene2d.utils.DragAndDrop#currentPayload" -> "getDragPayload",
    "com.badlogic.gdx.scenes.scene2d.utils.DragAndDrop#currentSource" -> "getDragSource",
    "com.badlogic.gdx.scenes.scene2d.utils.DragAndDrop#dragTime" -> "getDragTime/setDragTime",
    "com.badlogic.gdx.scenes.scene2d.utils.DragAndDrop#dragging" -> "isDragging",
    "com.badlogic.gdx.scenes.scene2d.utils.DragListener#tapSquareSize" -> "getTapSquareSize/setTapSquareSize",
    "com.badlogic.gdx.scenes.scene2d.utils.DragListener#button" -> "getButton/setButton",
    "com.badlogic.gdx.scenes.scene2d.utils.DragListener#dragDistance" -> "getDragDistance",
    "com.badlogic.gdx.scenes.scene2d.utils.DragListener#dragging" -> "isDragging",
    "com.badlogic.gdx.scenes.scene2d.utils.Drawable#leftWidth" -> "getLeftWidth/setLeftWidth",
    "com.badlogic.gdx.scenes.scene2d.utils.Drawable#rightWidth" -> "getRightWidth/setRightWidth",
    "com.badlogic.gdx.scenes.scene2d.utils.Drawable#topHeight" -> "getTopHeight/setTopHeight",
    "com.badlogic.gdx.scenes.scene2d.utils.Drawable#bottomHeight" -> "getBottomHeight/setBottomHeight",
    "com.badlogic.gdx.scenes.scene2d.utils.Drawable#minWidth" -> "getMinWidth/setMinWidth",
    "com.badlogic.gdx.scenes.scene2d.utils.Drawable#minHeight" -> "getMinHeight/setMinHeight",
    "com.badlogic.gdx.scenes.scene2d.utils.Selection#lastSelected" -> "getLastSelected",
    "com.badlogic.gdx.scenes.scene2d.utils.Selection#toggle" -> "getToggle/setToggle",
    "com.badlogic.gdx.scenes.scene2d.utils.Selection#multiple" -> "getMultiple/setMultiple",
    "com.badlogic.gdx.scenes.scene2d.utils.Selection#required" -> "getRequired/setRequired",
    "com.badlogic.gdx.scenes.scene2d.utils.Selection#disabled" -> "isDisabled/setDisabled",
    "com.badlogic.gdx.scenes.scene2d.utils.Selection#programmaticChangeEvents" -> "getProgrammaticChangeEvents/setProgrammaticChangeEvents",
    "com.badlogic.gdx.scenes.scene2d.utils.TiledDrawable#scale" -> "getScale",
    "com.badlogic.gdx.scenes.scene2d.utils.TiledDrawable#align" -> "getAlign",
  )

  /** the `gdx/src` pipeline. Universal phases first, then the three §1(b) phases configured above,
    * then the one §1(c) rule libGDX plugs in from OUTSIDE the engine
    * ([[GdxSharedIteratorRule]]). */
  def mainPhases: List[balticporter.tir.Phase] =
    List(beanProperties, new CollectionsTransform(retarget = comparatorRetarget), new MutableParamsTransform,
         new PanamaFfiTransform(), unwrapReflection, classTable, new GdxSharedIteratorRule,
         disposableRedirect, textureHandle, nullability, globalsToContext)

  /** `com.badlogic.gdx.Gdx` — eleven `public static` fields read from 100 files — retired into a
    * `sge.Sge` threaded as a `using` parameter (DESIGN.md §8.4).
    *
    * ==Every value here is a fact about libGDX, and none of it is derivable (§1b)==
    * WHICH class is an ambient context, what its counterpart is called, where each static went and
    * what happens at the edges. The mechanism — find the reads, close over five edges, add a clause,
    * rewrite the read through a path, count every seam — is the engine's and names no library.
    *
    * ==`attach = "class"`, and it is a MEASUREMENT and not a preference==
    * The two modes were priced against the same library before either was enabled (PROGRESS §11.12).
    * Class attachment threads **275 declarations in 177 files** and refuses nothing; method
    * attachment threads **2,497 in 324** and FREEZES 32 declarations across 15 override components,
    * every one of them anchored on `Runnable`, `Comparable` or another parent this program does not
    * declare. 177 files against the 100 that name `Gdx.` upstream is 1.77×, beside the reference hand
    * port's own 1.6×; method attachment's 3.3× is the number that is wrong. Class attachment is also
    * the reference port's shape — 82 % of its attachment sites are constructors.
    *
    * ==The member map is PATH-valued, and the two-hop half is why the bean pairs exist==
    * `app` was re-homed onto the bundle as `application`; the five `gl*` were never really the
    * global's at all — they duplicated what `Graphics` owns — so they are two-hop reads through
    * `graphics`, matching the reference port, where two-hop reads are 305 of 557. `gl` is upstream's
    * alias for `gl20` and maps to the same path. See [[beanPropertyPairs]] for the four
    * `Graphics#gl2x` entries that give those paths a member to land on.
    *
    * ==`boundary = "refuse"`==
    * A site the closure cannot reach keeps naming `Gdx` and is a COUNTED `context-seam` row, rather
    * than being quietly re-pointed at a companion `global` — which would retire the static and keep
    * the singleton. The two exceptions are named below.
    *
    * ==The two `sites` entries, and why `lazy-init` is opt-in per site==
    * Both are STATIC FIELD INITIALISERS that CONSTRUCT a now-threaded type, which is the one shape
    * with no signature to thread and no caller to take a clause from. `lazy-init` moves the
    * initialisation from first ACTIVE USE of the class (java's rule) to first READ of the field, and
    * the two coincide only when nothing else in the class is touched first — a fact the mechanism
    * cannot know, so it is never a default. Each is a `DeferredInit` decision, a porter note and a
    * counted seam. `Table#cellPool` was invisible until `ENGINE-LIMITS.md` CT5 cleared the 55 errors
    * around it and CT6 gave a `new` at a GENERIC class an instantiate edge at all.
    *
    * ==No `promoteToClass`, and no `scope`==
    * Class attachment changes no method signature, so no trait ever needed to become an
    * `abstract class`; and the run refuses nothing, so there is nothing to scope out. Both being
    * EMPTY is the measurement, not an omission — `attach = "method"` is where the refusals live.
    *
    * ==Shared surface, ONE instance, and the half a dependent adds (§1.5)==
    * The clause is on emitted constructors, so this is `SurfacePolicy` and lives in [[core]] alone:
    * a base whose `Mesh` takes `(using sge.Sge)` and a dependent whose does not emit signatures that
    * cannot meet. `sites` and `selfSupplied` are keyed on DECLARATIONS, though, and a dependent's
    * boundaries are in the DEPENDENT's own types — so gdx-vfx and this library's own test module
    * each contribute a `ContextHolderExtension`, which the merge folds in at this position
    * (`ENGINE-LIMITS.md` CT8, CT9).
    *
    * ==Position: LAST==
    * Two things in this list move what it reads. [[disposableRedirect]] re-points libGDX's own
    * `Disposable` at `java.lang.AutoCloseable`, which gives 24 threaded classes an ancestor this
    * program does not declare and is therefore 24 of the 25 `unconstructed-thread` WARNINGS this
    * port reports; [[beanProperties]] is what makes `graphics.gl20` resolvable at all. A dry run of
    * this phase alone reports 1 warning, not 25 — CLAUDE.md §5, and it is why the number is quoted
    * from the pipeline. */
  def globalsToContext: balticporter.transform.GlobalsToImplicitsTransform =
    new balticporter.transform.GlobalsToImplicitsTransform(List(
      balticporter.transform.ContextHolder(
        holder   = "com.badlogic.gdx.Gdx",
        context  = balticporter.transform.ContextType.Injected("sge.Sge"),
        members  = Map(
          "app"      -> "application",
          "graphics" -> "graphics",
          "audio"    -> "audio",
          "input"    -> "input",
          "files"    -> "files",
          "net"      -> "net",
          // the five GL statics, two-hop through the service that really owns them
          "gl"       -> "graphics.gl20",
          "gl20"     -> "graphics.gl20",
          "gl30"     -> "graphics.gl30",
          "gl31"     -> "graphics.gl31",
          "gl32"     -> "graphics.gl32",
        ),
        attach   = balticporter.transform.ContextAttach.Class,
        reader   = balticporter.transform.ContextReader.Summon,
        boundary = balticporter.transform.ContextBoundary.Refuse,
        sites    = Map(
          // `static final OnscreenKeyboard DEFAULT_ONSCREEN_KEYBOARD = new DefaultOnscreenKeyboard()`
          "com.badlogic.gdx.scenes.scene2d.ui.TextField#DEFAULT_ONSCREEN_KEYBOARD" ->
            balticporter.transform.ContextSite.LazyInit,
          // `static Pool<Cell> cellPool = new Pool<Cell>(){ protected Cell newObject(){ … } }`
          "com.badlogic.gdx.scenes.scene2d.ui.Table#cellPool" ->
            balticporter.transform.ContextSite.LazyInit,
        ),
      )
    ))

  /** libGDX's GL texture handle — the `int` that is really a texture name — as an opaque type, which
    * is what the reference hand port declares (`sge/graphics/GLHandle.scala`) and APPLIES to a ported
    * declaration (`GLTexture.scala:42`).
    *
    * ==Why ONE family and not the eight sge declares (§1c, and it is a MEASUREMENT)==
    * `TextureHandle` is the only one of sge's handle types that types a declaration libGDX itself
    * declares. `ProgramHandle`/`ShaderHandle` (`ShaderProgram` keeps `private var program: Int`),
    * `FramebufferHandle`/`RenderbufferHandle` (`GLFrameBuffer` keeps `Int`), `BufferHandle` and
    * `UniformLocation` are a typed layer offered to CONSUMERS beside the raw one — their home is
    * `GLHandleOps`, extension methods on `GL20`, and `GL20.scala:89` keeps `def glGenTexture(): Int`
    * to prove it. Configuring them would emit a surface the reference port deliberately does not
    * have. `GLEnum` is a third shape again: sge's ~200 `GL_*` values are hand-authored constants with
    * no Java counterpart, and this mechanism retypes declarations rather than minting a vocabulary.
    * PROGRESS §11.25 holds the evidence table; do not re-derive it.
    *
    * ==The FENCE is load-bearing, and its reason is structural rather than measured==
    * `FlowPropagation.refSym` admits a NULLARY CALL, so `glHandle = Gdx.gl.glGenTexture()` is a real
    * flow edge to `GL20#glGenTexture`, whose `int` return makes it eligible. Unfenced, the seed set
    * would grow into the GL interface and retype it — which sge does not do. With the four GL
    * interfaces scoped out, every one of those crossings becomes a COUNTED coercion instead, and
    * they are 30 of them (14 wraps + 16 unwraps).
    *
    * ==Shared surface, one instance, one mint (§1.5)==
    * The retyped signatures are what every dependent compiles against, so this lives in [[core]] and
    * is inherited through `extendedBy`. No dependent CONSTRUCTS a `primitive->opaque` phase, so
    * nothing merges — but the phase RUNS in every dependent, which is a different question and the
    * one `ENGINE-LIMITS.md` §13 O5 answers: the minted `TextureHandle` object belongs to the module
    * that declares the HINTS, and a dependent retypes and coerces against the object this module
    * emitted. */
  def textureHandle: balticporter.transform.PrimitiveToOpaqueTransform =
    new balticporter.transform.PrimitiveToOpaqueTransform(balticporter.tir.OpaqueSpec(
      fqn        = "com.badlogic.gdx.graphics.TextureHandle",
      hints      = _.fullName == "com.badlogic.gdx.graphics.GLTexture#glHandle",
      underlying = balticporter.tir.OpaqueSpec.Primitive.Int,
      scope      = balticporter.tir.RuleScope.Everywhere(except = Set(
        "com.badlogic.gdx.graphics.GL20", "com.badlogic.gdx.graphics.GL30",
        "com.badlogic.gdx.graphics.GL31", "com.badlogic.gdx.graphics.GL32")),
    ))

  /** libGDX's own `@Null` moved OUT of an annotation the Scala compiler ignores and INTO the type
    * — the union floor, `T | scala.Null` (DESIGN.md §8.6's N1).
    *
    * ==Why this is the base manifest's business (§1.5)==
    * A nullable return is a fact about the SHARED SURFACE. A base emitting `Actor | Null` and a
    * dependent emitting `Actor` for the same member each compile alone and cannot compile together,
    * so the entry lives here once and every dependent inherits it through `extendedBy`. No dependent
    * CONSTRUCTS a `nullability` of its own, so there is one instance in every effective pipeline and
    * nothing has to merge.
    *
    * ==The scope is the policy exit K13 leaves open, and it is not free==
    * `Null` is a subtype of every CONCRETE reference type, so `String | Null` simplifies at every
    * use and the floor costs nothing there. It is NOT a subtype of an abstract `T <: Object` —
    * which is the very fact that makes `def m[T <: X](): T = null` a type error — so every USE of a
    * declaration whose annotated type mentions a type parameter fails in a plain `T` slot.
    * `ENGINE-LIMITS.md` K13 measured that unscoped: 632 declarations retyped, **35 compile errors**,
    * every one inside a generic container or a generic widget, plus one overload-arity movement at a
    * defaulted `get`. The engine COUNTS the class rather than refusing it
    * (`NullabilityBoundaryCheck.Issue.AbstractTypeParameter`) and leaves the choice to the port,
    * which has exactly three exits: accept the errors, scope the generic declarations out, or land
    * §8.6's N2 (`-Yexplicit-nulls -language:unsafeNulls`, under which the whole class disappears and
    * this list is deleted).
    *
    * This port takes the SECOND exit, because the first fails the measure lane's compile guard and
    * the third is gated on work that is not done (A1/A2's placeholder removal). The entries below
    * are the types whose `@Null` members are typed by their OWN type parameter; each keeps its
    * upstream type, each is a counted `ScopedOut` decision with a porter note at the declaration,
    * and nothing else in the library is held back. */
  def nullability: balticporter.transform.NullabilityTransform =
    new balticporter.transform.NullabilityTransform(
      annotations = Set("com.badlogic.gdx.utils.Null"),
      target      = balticporter.transform.NullabilityTransform.Target.Union,
      scope       = balticporter.tir.RuleScope.Everywhere(nullabilityExempt),
    )

  /** the types K13's measurement holds back — see [[nullability]] for why, and PROGRESS §11.17 for
    * what it costs.
    *
    * Every entry is a type whose `@Null` members are typed by its OWN type parameter, so the union
    * the floor would give them does not conform at their uses. The list is MEASURED, not guessed —
    * it is the eleven types the unscoped run's 35 errors landed in, plus the two subclasses a
    * second run found (below) and minus the one that turned out to hold back nothing. Every other
    * generic type in the library — `ArrayMap`, `ObjectSet`, `PooledLinkedList`, `ObjectFloatMap`,
    * `Pool` and the rest — keeps the floor, because their annotated members are typed CONCRETELY
    * and `Null` is a subtype of every concrete reference type.
    *
    * It is an OVER-APPROXIMATION and the number is the honest part of it: 92 of 632 declarations
    * held back to clear 35 errors. A type entry is what a `RuleScope` can say; the predicate that would hold
    * back only the failing declarations is the one the engine already computes and reports
    * (`Issue.AbstractTypeParameter`, 155 occurrences), and a hand-written list of those would be a
    * second copy of it that rots the first time upstream moves a member. The whole entry is deleted
    * — not edited — when §8.6's N2 lands. */
  def nullabilityExempt: Set[String] = Set(
    // the generic containers: `V get(K)`, `V put(K, V)`, `T removeIndex(int)` — the annotation is
    // on the element, which IS the type parameter.
    "com.badlogic.gdx.utils.Array",
    // …and `Array`'s two subclasses, which override `replaceFirst`/`replaceAll` and re-state the
    // annotation on their own `T`. A scope exit on a generic type CLOSES OVER THE SUBCLASSES or it
    // is not an exit: with these two left in, the parent's members were held back and the children's
    // moved, which is half an override pair — 35 -> 6 errors, all six in exactly these two types.
    "com.badlogic.gdx.utils.SnapshotArray",
    "com.badlogic.gdx.utils.DelayedRemovalArray",
    "com.badlogic.gdx.utils.AtomicQueue",
    "com.badlogic.gdx.utils.IntMap",
    "com.badlogic.gdx.utils.LongMap",
    "com.badlogic.gdx.utils.ObjectMap",
    // …and NOT `OrderedMap`, which is where that closure STOPS. It extends `ObjectMap` and
    // `OrderedMap$OrderedMapValues` overrides an annotated `ObjectMap$Values#toArray`, so the
    // unscoped run put an error in it and the first draft listed it — but it re-states no
    // annotation of its own (upstream: zero `@Null` in the whole file), so there is nothing in it
    // for a scope to hold back and scoping `ObjectMap` out settles both ends of that pair. An
    // entry here BINDS (the type exists, so `PolicyBinder.bindScope` reports nothing) and holds
    // back zero declarations: dead policy the never-fired machinery cannot see, found only by the
    // emitted text being byte-identical with and without it. Close over the subtypes that
    // ANNOTATE, never over the subtypes that merely inherit.
    "com.badlogic.gdx.utils.Queue",
    // the generic widgets: a selection model's item and a tree's node are type parameters too.
    "com.badlogic.gdx.scenes.scene2d.ui.List",
    "com.badlogic.gdx.scenes.scene2d.ui.SelectBox",
    "com.badlogic.gdx.scenes.scene2d.ui.Tree",
    "com.badlogic.gdx.scenes.scene2d.utils.Selection",
  )
