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
    surface = List(new TestFrameworkTransform()),
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

  /** the `gdx/src` pipeline. Universal phases first, then the three §1(b) phases configured above,
    * then the one §1(c) rule libGDX plugs in from OUTSIDE the engine
    * ([[GdxSharedIteratorRule]]). */
  def mainPhases: List[balticporter.tir.Phase] =
    List(new CollectionsTransform(retarget = comparatorRetarget), new MutableParamsTransform,
         new PanamaFfiTransform(), unwrapReflection, classTable, new GdxSharedIteratorRule,
         disposableRedirect)
