package balticporter.corpus

import balticporter.core.{FrontendConfig, Provenance, RuntimeMode, Substitutions}
import balticporter.runner.{Determinism, PortRun, SourceSet}
import balticporter.transform.{ClassTableTransform, CollectionsTransform, MutableParamsTransform, PanamaFfiTransform, StaticForwarderTransform}

import java.nio.file.{Files, Path}
import scala.jdk.CollectionConverters.*

/** Migrate libGDX's CORE module (`gdx/src`, 605 types — the backend-agnostic, JDK-only heart
  * of libGDX) through the TIR to the `libgdx-core` sbt submodule, then compile it with
  * `sbt libgdx-core/compile`.
  *
  *   corpus-tests/runMain balticporter.corpus.LibgdxCoreMigrate [--raw] [--determinism=full]
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
      phases    = if raw then Nil else LibgdxPolicy.mainPhases,
      subs      = LibgdxPolicy.substitutions(repoRoot),
      provenance = Some(Provenance(
        upstreamName     = "libGDX",
        upstreamCommit   = "vendored in ../sge/original-src/libgdx",
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
      // NB — dropping it CONCEALS a real engine gap, recorded in LIBGDX-PORT-STATUS.md: the one
      // compile error it produced was `CollectionsTransform` rewriting OUR signature to
      // `mutable.Map[String, Buffer[String]]` while the body returned an unported JDK method's
      // real `java.util.Map`. That JDK/Scala collection boundary is universal and still open;
      // this drop is justified by portability alone and must not be read as closing it.
      "com.badlogic.gdx.net.NetJavaImpl",
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
    inject = List(repoRoot.resolve("corpus-tests/libgdx-overrides")),
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
      members  = Set("getSimpleName", "getName", "isInstance", "isAssignableFrom", "isArray",
                     "isEnum", "isInterface", "isPrimitive", "isAnnotation", "getComponentType"),
    )
  ))

  /** the `gdx/src` pipeline. Universal phases first, then the two §1(b) phases configured above,
    * then the one §1(c) rule libGDX plugs in from OUTSIDE the engine
    * ([[GdxSharedIteratorRule]]). */
  def mainPhases: List[balticporter.tir.Phase] =
    List(new CollectionsTransform, new MutableParamsTransform, new PanamaFfiTransform(),
         unwrapReflection, classTable, new GdxSharedIteratorRule)
