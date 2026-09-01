package balticporter.corpus.libgdx

import balticporter.core.{FrontendConfig, ParityRef, PortManifest, Provenance, RuntimeMode, Substitutions}
import balticporter.runner.{Determinism, PortRun, SourceSet, VendoredCommit}
import balticporter.tir.{Descriptor, Param}
import balticporter.transform.{ClassTableTransform, CollectionsTransform, MutableParamsTransform, PanamaFfiTransform, StaticForwarderTransform, TestFrameworkTransform}

import java.nio.file.{Files, Path}
import scala.jdk.CollectionConverters.*

/** Migrate libGDX's CORE module (`gdx/src`, 605 types — the backend-agnostic, JDK-only heart
  * of libGDX) through the TIR to the `ported/sge` sbt submodule, then compile it with
  * `sbt sge/compile`.
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
      label     = "sge",
      portRoot  = repoRoot.resolve("ported/sge"),
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
      nextStep    = "sbt sge/compile",
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
      name        = "sge",
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
      // ---- wave 1.3: type renames from the `Migration notes: Renames:` census ----
      // sge renamed `List` to `SgeList` to avoid the clash with `scala.List` (1 file). The
      // upstream FQN is the key; a bare simple name renames in place.
      typeRenames    = Map(
        "com.badlogic.gdx.scenes.scene2d.ui.List" -> "SgeList", // avoids clash with scala.List (1 sge file)
      ),
      resolutions    = reviewedBoundaries,
      // THE ARTIFACT THIS MODULE'S BUILD ADDS — what a `Verdict.Depend` is answered WITH
      // (CLAUDE.md §1.5). This manifest declares no `targets`, so it claims all three, and 37 sites
      // of this port's own emitted code name `java.util.Locale`: `I18NBundle` and its loader, which
      // is a whole feature of the library rather than an incidental call. Locale EXISTS on both
      // non-JVM backends, in `scala-java-locales` (`JS-L65`), so the reader's action was never to
      // remove one of those calls — it was this line, and `dependency-coverage` had reported all 37
      // on every run since the lane existed.
      //
      // ONE entry: nothing here calls `java.time` or `java.text`, and an entry naming an artifact
      // no requirement wants is now a `policy` finding of its own. It is NOT inherited (§1.5's
      // right-hand column, `inject`'s line): every dependent's own emitted code is Locale-free, so
      // each of the six declares nothing and the `dependency-coverage` residue it reports is an
      // honest zero rather than a credit taken from here.
      //
      // `cross = Platform` is what the artifact IS — `scala-java-locales_sjs1_3` and
      // `_native0.5_3` are published beside `_3`, so `%%` would ask a JS build for the JVM jar.
      dependencies   = List(
        balticporter.catalog.ArtifactDep("io.github.cquiroz", "scala-java-locales", "1.5.4",
                                         balticporter.catalog.CrossKind.Platform),
        // `lowlevel.Nullable` — the opaque wrapper the `Named` nullability target emits into every
        // annotated declaration's type. The coordinate is the port's to state, never the engine's
        // (§1), and the version is sge's own `Versions.lls` from its `build.sbt`.
        balticporter.catalog.ArtifactDep("com.kubuszok", "lls", "0.3.0"),
      ),
      // THE REFERENCE HAND PORT for sge. NOT inherited (DESIGN.md §8.23).
      parity = Some(ParityRef(roots = List(
        repoRoot.resolve("../sge/sge/src/main/scala").normalize))),
    )

  /** THE BOUNDARY ROWS THIS PORT HAS READ AND ACCEPTS — `DESIGN.md` §8.16.
    *
    * Every entry here is the same statement, at a site somebody opened: *the residue this check
    * counts is the right outcome here*. That statement had no spelling before the menu — the three
    * boundary counts could only ever go up, because a review that concluded "this one is fine" had
    * nowhere to be recorded — and it is deliberately not the same statement as any of the keys these
    * checks point at, each of which changes what the port EMITS. Nothing below moves a byte; each
    * row leaves its refusal lane and arrives in `remediation(resolved)` with the key that moved it.
    *
    * They are on `core` and not on a dependent because they are facts about libGDX core's OWN
    * declarations, which is what §1.5 puts in the inherited column. A dependent binds them and drains
    * nothing, because a finding about a base's unit is the base's (`ENGINE-LIMITS.md` D2).
    */
  def reviewedBoundaries: Map[String, String] = Map(
    // `System.identityHashCode` is specified to answer "the same hash code that would be returned by
    // the default hashCode(), regardless of the object's class" — it reads the object's IDENTITY and
    // never its representation, which is the exact question `OpaqueEgress` asks. Note the other five
    // rows in this lane STAY: `StringBuilder#append(Object)`, `Objects#toString`, `Object#equals`,
    // `Comparable#compareTo` and `Comparator#compare` all read the value they are handed, so a
    // retyped collection reaching one of them is precisely K21 face 1 and is still counted.
    //
    // BARE because it is EXACT: this method has exactly one overload and a bare key names the set.
    // It used to be bare because it had to be — a finding's owner column carries `Symbol.fullName`,
    // which the frontend interns for an external member with FULLY QUALIFIED parameters, while a
    // `Descriptor` is SIMPLE names, so `identityHashCode(java.lang.Object)` copied out of the report
    // was `never matched`. The binder compares descriptors through `Descriptor.matches` now (simple
    // names on both sides), so either spelling binds and this is a choice.
    "java.lang.System#identityHashCode" -> "accept-opaque-egress",

    // `Stage` is the scene graph's root and is the most-written `new` in libGDX's own documentation:
    // application code builds one. Nothing INSIDE the library constructs it, which is why the closure
    // sees no instantiation, and the external ancestor the warning keys on is `java.lang.AutoCloseable`
    // — a JDK interface every `Disposable` has, not a framework base type. So this is the second of
    // the two cases the check says it cannot tell apart ("if YOUR USERS construct it, this is correct
    // as it stands and the clause is part of the ported API"), and a `selfSupplied` entry would be
    // the wrong answer: it would take the context away from the caller who has one.
    "com.badlogic.gdx.scenes.scene2d.Stage" -> "accept-unconstructed-thread",

    // TWO rows, one key — the broadcast this grammar is per-declaration for. Both reads are inside
    // the anonymous `GLErrorListener` that IS this `static final` field's initialiser: its
    // `onError(int)` overrides the interface's, so its signature is not this program's to change, and
    // the field runs at class initialisation, before any caller exists to pass a context. The two
    // spelled alternatives are both worse here — `lazy-init` would change WHEN a diagnostic listener
    // is built for no benefit, and `boundary = "residual-global"` is a WHOLE-PHASE setting that would
    // re-spell every residual read in the library to serve these two.
    "com.badlogic.gdx.graphics.profiling.GLErrorListener#LOGGING_LISTENER" -> "accept-residual-global",

    // K13 CLOSED: the four `accept-scoped-out` and `accept-abstract-type-parameter` entries at
    // `List$ListStyle#{background,down,over}` and `Skin#optional` were the K13 exit's residual
    // resolutions — with the `Named` target, `Nullable[T]` composes at every `T` and no scope exit
    // is needed, so those findings no longer fire and the selections are deleted.
  )

  /** libGDX's own JUnit suite, as a DEPENDENT of [[core]].
    *
    * It adds one phase and inherits everything else. The hand-written pipeline it replaces listed
    * the shared phases again, minus two, with a comment arguing that the two were unnecessary — a
    * correct argument that nothing checked and that the next module would have had to make again.
    */
  def test(repoRoot: Path): PortManifest = core(repoRoot).extendedBy(PortManifest(
    name    = "sge-test",
    surface = List(new TestFrameworkTransform(), selfSuppliedSuites,
      // --- 3.1ae: gdx-test residue ---
      // JsonMatcherTests' two static toString helpers use CharArray as a string builder
      // (append(String, String), replaceAll(String, String), toString). DynamicArray[Char] has
      // none of these. sge dropped the CharArray string-builder API entirely (type-mappings.md:
      // "CharArray -> DynamicArray[T]", CharArrayTest excluded at fdc30967). Replace with
      // java.lang.StringBuilder, which is what these helpers are doing.
      new balticporter.transform.MethodBodyTransform(Map(
        "com.badlogic.gdx.utils.JsonMatcherTests#toString(JsonMatcher,String[])" ->
          """{
            |  val buffer: java.lang.StringBuilder = new java.lang.StringBuilder()
            |  for (pattern <- patterns) {
            |    if (buffer.length() > 0) buffer.append(", ")
            |    if (pattern.isEmpty())
            |      buffer.append("\"\"")
            |    else
            |      buffer.append(sge.utils.PatternParser.parse(matcher, pattern, lowlevel.Nullable.empty).toString())
            |  }
            |  return buffer.toString().replace("\n", "\\n").replace("\t", "\\t")
            |}""".stripMargin,
        "com.badlogic.gdx.utils.JsonMatcherTests#toString(Array)" ->
          """{
            |  val buffer: java.lang.StringBuilder = new java.lang.StringBuilder()
            |  for (value <- values) {
            |    if (buffer.length() > 0) buffer.append(", ")
            |    buffer.append(value.toJson(sge.utils.JsonWriter.OutputType.minimal))
            |  }
            |  return buffer.toString().replace("\n", "\\n").replace("\t", "\\t")
            |}""".stripMargin,
      )),
    ),
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
    * See `ported/sge/src/test/scala/sge/SgeTestFixture.scala`. The one affected suite reaches no
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
      // O6 CLOSED: `Align` is a class of `static final int` constants, replaced by an injected
      // `opaque type Align = Int` (sge's convention). The retype is handled by
      // `PrimitiveToOpaqueTransform(Existing)` below; this drop prevents the java class from being
      // emitted alongside the injection. The class is still PARSED — its static members are visible
      // to every reference in the ported code.
      "com.badlogic.gdx.utils.Align",
      // wave 3.1a: retargetted to mutable.BitSet (sge type-mappings.md: "Bits -> mutable.BitSet").
      // 0 callers in gdx/src; the retarget serves dependents (ashley uses Bits for entity masks).
      "com.badlogic.gdx.utils.Bits",
      // wave 3.1a: retargetted to lowlevel.util.ObjectMap (lls 0.3.0). sge type-mappings.md:
      // "ObjectMap -> ObjectMap[K,V]" (extracted to lls). Same member API (get, put, containsKey,
      // remove, putAll, clear, size, isEmpty, etc. — verified via javap on lls_3-0.3.0.jar), so
      // retarget only — no Kind-based rewrites needed. lls's constructor is PRIVATE, so the
      // Construct rewrite routes `new ObjectMap(args)` through the companion's transparent inline
      // `apply` (in TASTy, erased from bytecode). Inner types (Entry, Keys, Values, Entries) are
      // NOT in lls — references to them from non-dropped files are compile errors, COUNTED.
      "com.badlogic.gdx.utils.ObjectMap",
      // wave 3.1a: retargetted to lowlevel.util.ObjectSet (lls 0.3.0). Same pattern as ObjectMap.
      "com.badlogic.gdx.utils.ObjectSet",
      // wave 3.1b: ObjectMap/ObjectSet SUBCLASSES — retargetted to their lls equivalents.
      // lls's ObjectMap is `final class`, so gdx subclasses cannot extend it; they are independent
      // types in lls. sge type-mappings.md: "OrderedMap -> OrderedMap[K,V]",
      // "OrderedSet -> OrderedSet[A]", "IdentityMap -> ArrayMap[K,V]" (lls has no IdentityMap;
      // ArrayMap with identity semantics is the closest equivalent).
      "com.badlogic.gdx.utils.OrderedMap",
      "com.badlogic.gdx.utils.OrderedSet",
      "com.badlogic.gdx.utils.IdentityMap",
      // wave 3.1d: the remaining MAP family — all retargetted to lowlevel.util.ObjectMap.
      // sge type-mappings.md: "IntMap, IntIntMap, IntFloatMap, LongMap, ObjectIntMap, ObjectFloatMap,
      // ObjectLongMap -> ObjectMap[K,V]". lls has no primitive-keyed specialisations; the runtime
      // cost of boxing is accepted (same as sge's choice). These types' own static `tableSize` and
      // references to `ObjectMap.dummy` disappear with the type itself — every remaining caller is
      // inside one of these dropped files.
      "com.badlogic.gdx.utils.IntMap",
      "com.badlogic.gdx.utils.LongMap",
      "com.badlogic.gdx.utils.IntIntMap",
      "com.badlogic.gdx.utils.IntFloatMap",
      "com.badlogic.gdx.utils.ObjectIntMap",
      "com.badlogic.gdx.utils.ObjectFloatMap",
      "com.badlogic.gdx.utils.ObjectLongMap",
      // wave 3.1d: gdx's ArrayMap retargetted to lowlevel.util.ArrayMap.
      // sge type-mappings.md: "ArrayMap, IdentityMap -> ArrayMap[K,V]". IdentityMap already
      // retargetted in wave 3.1b; gdx's own ArrayMap is the same lls type. Deprecated
      // constructors taking Class already in dropMethods.
      "com.badlogic.gdx.utils.ArrayMap",
      // wave 3.1d: IntSet retargetted to lowlevel.util.ObjectSet.
      // sge type-mappings.md: "ObjectSet, IntSet -> ObjectSet[A]". lls has no primitive-element
      // set specialisation; IntSet -> ObjectSet[Int] with boxing accepted.
      "com.badlogic.gdx.utils.IntSet",
      // wave 3.1n: Array family retargetted to lowlevel.util.DynamicArray.
      // sge type-mappings.md: "ByteArray, CharArray, FloatArray, IntArray, LongArray, ShortArray
      // -> DynamicArray[T]" (unified via MkArray type class). Array<T> -> DynamicArray[T],
      // SnapshotArray/DelayedRemovalArray -> DynamicArray (lls DynamicArray has begin/end for
      // snapshot support). BooleanArray -> DynamicArray[Boolean] (lls MkArray$OfBooleans exists).
      // Queue -> mutable.ArrayDeque (sge type-mappings.md: "Queue -> Scala stdlib queues";
      // sge's own QueueBitsTest uses mutable.ArrayDeque). Retargetted separately from the
      // DynamicArray family because ArrayDeque is stdlib, not lls.
      "com.badlogic.gdx.utils.Array",
      "com.badlogic.gdx.utils.SnapshotArray",
      "com.badlogic.gdx.utils.DelayedRemovalArray",
      "com.badlogic.gdx.utils.IntArray",
      "com.badlogic.gdx.utils.FloatArray",
      "com.badlogic.gdx.utils.LongArray",
      "com.badlogic.gdx.utils.ShortArray",
      "com.badlogic.gdx.utils.ByteArray",
      "com.badlogic.gdx.utils.CharArray",
      "com.badlogic.gdx.utils.BooleanArray",
      "com.badlogic.gdx.utils.Queue",
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
    inject = List(repoRoot.resolve("balticporter/corpus/libgdx-overrides")),
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

  /** `com.badlogic.gdx.utils.Bits` -> `scala.collection.mutable.BitSet`.
    *
    * sge's type-mappings.md: "Bits -> mutable.BitSet". 0 callers in gdx/src; the retarget sits
    * on the base so dependents (ashley uses Bits for entity masks) inherit it through
    * `extendedBy`. Dropped from `substitutions.dropTypes` so the java class is not emitted.
    *
    * The `retargetRewrites` table maps the member names that differ between `Bits` and `BitSet`:
    * `get(i)` -> `apply(i)`, `set(i)` -> `addOne(i)`, `clear(i)` -> `subtractOne(i)`, etc.
    * These fire only on dependents that actually call them; the base has 0 callers. */
  def bitsRetarget: Map[String, String] =
    Map("com.badlogic.gdx.utils.Bits" -> "scala.collection.mutable.BitSet")

  def bitsRetargetRewrites: Map[String, Map[(String, Int), balticporter.transform.CollectionsTransform.RetargetRewrite]] =
    import balticporter.transform.CollectionsTransform.RetargetRewrite.*
    Map("com.badlogic.gdx.utils.Bits" -> Map(
      ("get", 1)          -> Rename("apply"),        // bits.get(i) -> bits(i)
      ("set", 1)          -> Rename("addOne"),        // bits.set(i) -> bits.addOne(i), rendered bits += i
      ("clear", 1)        -> Rename("subtractOne"),   // bits.clear(i) -> bits -= i
      ("and", 1)          -> Rename("&="),            // bits.and(other) -> bits &= other
      ("or", 1)           -> Rename("|="),            // bits.or(other) -> bits |= other
      ("andNot", 1)       -> Rename("&~="),           // bits.andNot(other) -> bits &~= other
      // bits.containsAll(other) = "this is a superset of other" = other.subsetOf(this).
      // Argument and receiver SWAP — Template, not Rename.
      ("containsAll", 1)  -> Template("$0.subsetOf($recv)"),
      // bits.length() -> highest set bit + 1. BitSet has no direct equivalent.
      // BitSet.last is the highest element; +1 matches java's Bits.length semantics.
      // Empty -> 0 (Bits.length returns 0 on empty, BitSet.last throws).
      ("length", 0)       -> Template("(if ($recv.isEmpty) 0 else $recv.last + 1)"),
      // bits.numBits() -> count of set bits = BitSet.size
      ("numBits", 0)      -> Chain(List("size")),
      // bits.flip(i) -> toggle bit i
      ("flip", 1)         -> Template("(if ($recv($0)) $recv -= $0 else $recv += $0)"),
      // bits.nextSetBit(fromIndex) -> first set bit >= fromIndex, or -1
      ("nextSetBit", 1)   -> Template("$recv.rangeFrom($0).headOption.getOrElse(-1)"),
      // bits.intersects(other) -> (bits & other).nonEmpty
      ("intersects", 1)   -> Template("($recv & $0).nonEmpty"),
      // bits.getAndClear(i) -> { val was = bits(i); bits -= i; was }
      ("getAndClear", 1)  -> Template("{ val bpWas = $recv($0); $recv -= $0; bpWas }"),
      // bits.getAndSet(i) -> { val was = bits(i); bits += i; !was } (returns true if ALREADY set = was NOT set before)
      // Java's getAndSet returns true if the bit was ALREADY set BEFORE the operation.
      // Wait: java says "returns true if the bit was already set" which means "was NOT changed".
      // Actually the javadoc says "returns true if the bit was already set", so: { val was = bits(i); bits += i; was }
      ("getAndSet", 1)    -> Template("{ val bpWas = $recv($0); $recv += $0; bpWas }"),
      // bits.clear() (no args) -> clear all bits. BitSet.clear() exists.
      ("clear", 0)        -> Rename("clear"),
      // bits.notEmpty() -> nonEmpty (parenless on BitSet)
      ("notEmpty", 0)     -> Chain(List("nonEmpty")),
      // bits.isEmpty() -> isEmpty (parenless on BitSet)
      // Note: bean-property may have renamed isEmpty->empty. The retarget rewrite
      // ("empty", 0) -> Rename("isEmpty") below reverses it. But BitSet.empty returns
      // a new empty BitSet (not a Boolean), so we MUST reach "isEmpty" not "empty".
      ("empty", 0)        -> Chain(List("isEmpty")),
    ))

  /** `ObjectMap` and `ObjectSet` retargetted to their lls equivalents.
    *
    * sge type-mappings.md: "ObjectMap -> ObjectMap[K,V]", "ObjectSet -> ObjectSet[A]" (extracted
    * to lls). Same member API (get, put, containsKey, remove, add, contains, etc. — verified via
    * `javap` on lls_3-0.3.0.jar), so retarget only — no Kind-based rewrites needed.
    *
    * lls's constructor is PRIVATE, so `new ObjectMap(args)` must be routed through the companion's
    * transparent inline `apply` (exists in TASTy, erased from JVM bytecode). The `Construct`
    * rewrite emits `lowlevel.util.ObjectMap.apply[K,V](args)`, which the Scala compiler resolves
    * from the TASTy and inlines. Three arities:
    *   - `("<init>", 0)` → `apply()` — default capacity 51, loadFactor 0.8
    *   - `("<init>", 1)` → `apply(capacity)` — all gdx/src uses are int-typed (capacity)
    *   - `("<init>", 2)` → `apply(capacity, loadFactor)`
    * The copy constructor `ObjectMap(ObjectMap)` at arity 1 does NOT appear in gdx/src outside
    * dropped files; if a dependent needs it, `from` is the companion's public factory for copies.
    *
    * Inner types (`ObjectMap.Entry`, `ObjectMap.Keys`, `ObjectMap.Values`, `ObjectMap.Entries`)
    * are NOT present in lls — references to them from non-dropped files are compile errors,
    * COUNTED on the `collection-retarget` lane. */
  def libCollectionRetargets: Map[String, String] = Map(
    "com.badlogic.gdx.utils.ObjectMap" -> "lowlevel.util.ObjectMap",
    "com.badlogic.gdx.utils.ObjectSet" -> "lowlevel.util.ObjectSet",
    // subclasses: lls ObjectMap is final, so OrderedMap/IdentityMap/OrderedSet are standalone types
    "com.badlogic.gdx.utils.OrderedMap" -> "lowlevel.util.OrderedMap",
    "com.badlogic.gdx.utils.OrderedSet" -> "lowlevel.util.OrderedSet",
    // sge type-mappings.md maps IdentityMap to ArrayMap (lls has no IdentityMap)
    "com.badlogic.gdx.utils.IdentityMap" -> "lowlevel.util.ArrayMap",
    // ObjectMap.Entry -> Tuple2: same image as JDK Map.Entry -> Tuple2 already in the phase.
    // .key -> ._1, .value -> ._2 field rewrites handled by retargetSelectRewrite (Tree.Select arm).
    // The arity-0 constructor (java's default-constructed Entry with both fields null) is routed
    // through the Construct entry in libCollectionConstructRewrites.
    "com.badlogic.gdx.utils.ObjectMap$Entry" -> "scala.Tuple2",
    // wave 3.1d: remaining MAP family retargets
    "com.badlogic.gdx.utils.IntMap" -> "lowlevel.util.ObjectMap",
    "com.badlogic.gdx.utils.LongMap" -> "lowlevel.util.ObjectMap",
    "com.badlogic.gdx.utils.IntIntMap" -> "lowlevel.util.ObjectMap",
    "com.badlogic.gdx.utils.IntFloatMap" -> "lowlevel.util.ObjectMap",
    "com.badlogic.gdx.utils.ObjectIntMap" -> "lowlevel.util.ObjectMap",
    "com.badlogic.gdx.utils.ObjectFloatMap" -> "lowlevel.util.ObjectMap",
    "com.badlogic.gdx.utils.ObjectLongMap" -> "lowlevel.util.ObjectMap",
    "com.badlogic.gdx.utils.ArrayMap" -> "lowlevel.util.ArrayMap",
    "com.badlogic.gdx.utils.IntSet" -> "lowlevel.util.ObjectSet",
    // wave 3.1n: Array family -> DynamicArray.
    // sge type-mappings.md: primitive arrays -> "DynamicArray[T]" (unified via MkArray type class).
    // Array<T> -> DynamicArray[T] (1:1 type param). DynamicArray has the same member API:
    // add, addAll, insert, removeIndex, removeValue/removeValueByRef, pop, peek, first, clear,
    // truncate, swap, reverse, shuffle, sort(Ordering), toArray, ensureCapacity, begin/end,
    // size (method), items (method), apply(i), update(i,v), contains/containsByRef,
    // indexOf/indexOfByRef, lastIndexOf/lastIndexOfByRef, select, toString(sep), random,
    // selectRanked, iterator. DynamicArray supports `for (x <- da)` natively (verified).
    "com.badlogic.gdx.utils.Array" -> "lowlevel.util.DynamicArray",
    "com.badlogic.gdx.utils.SnapshotArray" -> "lowlevel.util.DynamicArray",
    "com.badlogic.gdx.utils.DelayedRemovalArray" -> "lowlevel.util.DynamicArray",
    "com.badlogic.gdx.utils.IntArray" -> "lowlevel.util.DynamicArray",
    "com.badlogic.gdx.utils.FloatArray" -> "lowlevel.util.DynamicArray",
    "com.badlogic.gdx.utils.LongArray" -> "lowlevel.util.DynamicArray",
    "com.badlogic.gdx.utils.ShortArray" -> "lowlevel.util.DynamicArray",
    "com.badlogic.gdx.utils.ByteArray" -> "lowlevel.util.DynamicArray",
    "com.badlogic.gdx.utils.CharArray" -> "lowlevel.util.DynamicArray",
    "com.badlogic.gdx.utils.BooleanArray" -> "lowlevel.util.DynamicArray",
    "com.badlogic.gdx.utils.Queue" -> "scala.collection.mutable.ArrayDeque",
    // Inner iterator types — java's Keys/Values/Entries are live views backed by the map's
    // own table; lls has foreachKey/foreachValue/foreachEntry (inline) instead.  As TYPES these
    // are used only where java stores them in a local (`I18NBundle`) — Collect handles the call.
    "com.badlogic.gdx.utils.ObjectMap$Keys" -> "lowlevel.util.DynamicArray",
    "com.badlogic.gdx.utils.ObjectMap$Values" -> "lowlevel.util.DynamicArray",
    // Inner Entry types for the map family — same Tuple2 mapping as ObjectMap.Entry
    "com.badlogic.gdx.utils.IntMap$Entry" -> "scala.Tuple2",
    "com.badlogic.gdx.utils.LongMap$Entry" -> "scala.Tuple2",
    "com.badlogic.gdx.utils.ObjectIntMap$Entry" -> "scala.Tuple2",
    "com.badlogic.gdx.utils.ObjectFloatMap$Entry" -> "scala.Tuple2",
    "com.badlogic.gdx.utils.ObjectLongMap$Entry" -> "scala.Tuple2",
  )

  /** TYPE ARGUMENT MAPPING for arity-changing retargets — describes how to fill the target type's
    * type arguments from the source type's when the arities differ.
    *
    * `IntMap<V>` (1 param) -> `ObjectMap[K,V]` (2 params): first arg is always `Int`, second is
    * carried from the source's only type parameter. `IntIntMap` (0 params) -> `ObjectMap[K,V]`
    * (2 params): both args are fixed. Without this mapping, the retarget would emit
    * `ObjectMap[V]` (1 arg for a 2-param target) or bare `ObjectMap` (0 args for IntIntMap). */
  def libCollectionRetargetTypeArgs: Map[String, List[balticporter.transform.CollectionsTransform.RetargetArg]] =
    import balticporter.transform.CollectionsTransform.RetargetArg.*
    Map(
      "com.badlogic.gdx.utils.IntMap"        -> List(FixedType("scala.Int"), SourceArg(0)),
      "com.badlogic.gdx.utils.LongMap"       -> List(FixedType("scala.Long"), SourceArg(0)),
      "com.badlogic.gdx.utils.IntIntMap"      -> List(FixedType("scala.Int"), FixedType("scala.Int")),
      "com.badlogic.gdx.utils.IntFloatMap"    -> List(FixedType("scala.Int"), FixedType("scala.Float")),
      "com.badlogic.gdx.utils.ObjectIntMap"   -> List(SourceArg(0), FixedType("scala.Int")),
      "com.badlogic.gdx.utils.ObjectFloatMap" -> List(SourceArg(0), FixedType("scala.Float")),
      "com.badlogic.gdx.utils.ObjectLongMap"  -> List(SourceArg(0), FixedType("scala.Long")),
      "com.badlogic.gdx.utils.IntSet"         -> List(FixedType("scala.Int")),
      // wave 3.1n: primitive arrays — 0-param source to 1-param DynamicArray[T].
      // sge type-mappings.md: "IntArray -> DynamicArray[Int]", etc.
      "com.badlogic.gdx.utils.IntArray"      -> List(FixedType("scala.Int")),
      "com.badlogic.gdx.utils.FloatArray"    -> List(FixedType("scala.Float")),
      "com.badlogic.gdx.utils.LongArray"     -> List(FixedType("scala.Long")),
      "com.badlogic.gdx.utils.ShortArray"    -> List(FixedType("scala.Short")),
      "com.badlogic.gdx.utils.ByteArray"     -> List(FixedType("scala.Byte")),
      "com.badlogic.gdx.utils.CharArray"     -> List(FixedType("scala.Char")),
      "com.badlogic.gdx.utils.BooleanArray"  -> List(FixedType("scala.Boolean")),
    )

  def libCollectionConstructRewrites: Map[String, Map[(String, Int), balticporter.transform.CollectionsTransform.RetargetRewrite]] =
    import balticporter.transform.CollectionsTransform.RetargetRewrite.*
    Map(
      "com.badlogic.gdx.utils.ObjectMap" -> Map(
        ("<init>", 0) -> Construct("lowlevel.util.ObjectMap", "apply"),
        ("<init>", 1) -> Construct("lowlevel.util.ObjectMap", "apply"),
        ("<init>", 2) -> Construct("lowlevel.util.ObjectMap", "apply"),
        ("notEmpty", 0) -> Rename("nonEmpty"),
        // bean-property renamed isEmpty() -> empty; lls keeps isEmpty
        ("empty", 0) -> Rename("isEmpty"),
        // ForEach: for (Entry e : map.entries()) -> map.foreachEntry((k, v) => body)
        ("entries", 0) -> ForEach("foreachEntry", 2),
        ("keys", 0)    -> Collect("foreachKey", "lowlevel.util.DynamicArray"),
        ("values", 0)  -> Collect("foreachValue", "lowlevel.util.DynamicArray"),
      ),
      // Entry arity-0: java's default-constructed Entry with both fields null.
      // Construct routes `new Tuple2()` -> `Tuple2.apply(null.asInstanceOf[K], null.asInstanceOf[V])`.
      // Every Entry source needs the entry, since retargetTargetToSource maps to whichever
      // source program.symbols.all iterates last.
      "com.badlogic.gdx.utils.ObjectMap$Entry" -> Map(
        ("<init>", 0) -> Construct("scala.Tuple2", "apply", fillTypeArgs = true),
      ),
      "com.badlogic.gdx.utils.IntMap$Entry" -> Map(
        ("<init>", 0) -> Construct("scala.Tuple2", "apply", fillTypeArgs = true),
      ),
      "com.badlogic.gdx.utils.LongMap$Entry" -> Map(
        ("<init>", 0) -> Construct("scala.Tuple2", "apply", fillTypeArgs = true),
      ),
      "com.badlogic.gdx.utils.ObjectIntMap$Entry" -> Map(
        ("<init>", 0) -> Construct("scala.Tuple2", "apply", fillTypeArgs = true),
      ),
      "com.badlogic.gdx.utils.ObjectFloatMap$Entry" -> Map(
        ("<init>", 0) -> Construct("scala.Tuple2", "apply", fillTypeArgs = true),
      ),
      "com.badlogic.gdx.utils.ObjectLongMap$Entry" -> Map(
        ("<init>", 0) -> Construct("scala.Tuple2", "apply", fillTypeArgs = true),
      ),
      "com.badlogic.gdx.utils.ObjectSet" -> Map(
        ("<init>", 0) -> Construct("lowlevel.util.ObjectSet", "apply"),
        ("<init>", 1) -> Construct("lowlevel.util.ObjectSet", "apply"),
        ("<init>", 2) -> Construct("lowlevel.util.ObjectSet", "apply"),
        ("notEmpty", 0) -> Rename("nonEmpty"),
      ),
      "com.badlogic.gdx.utils.OrderedMap" -> Map(
        ("<init>", 0) -> Construct("lowlevel.util.OrderedMap", "apply"),
        ("<init>", 1) -> Construct("lowlevel.util.OrderedMap", "apply"),
        ("<init>", 2) -> Construct("lowlevel.util.OrderedMap", "apply"),
        ("notEmpty", 0) -> Rename("nonEmpty"),
        ("entries", 0) -> ForEach("foreachEntry", 2),
        ("keys", 0)    -> Collect("foreachKey", "lowlevel.util.DynamicArray"),
        ("values", 0)  -> Collect("foreachValue", "lowlevel.util.DynamicArray"),
      ),
      "com.badlogic.gdx.utils.OrderedSet" -> Map(
        ("<init>", 0) -> Construct("lowlevel.util.OrderedSet", "apply"),
        ("<init>", 1) -> Construct("lowlevel.util.OrderedSet", "apply"),
        ("<init>", 2) -> Construct("lowlevel.util.OrderedSet", "apply"),
        ("notEmpty", 0) -> Rename("nonEmpty"),
        ("iterator", 0) -> Chain(List("orderedItems", "iterator")),
      ),
      "com.badlogic.gdx.utils.IdentityMap" -> Map(
        ("<init>", 0) -> Construct("lowlevel.util.ArrayMap", "apply"),
        ("<init>", 1) -> Construct("lowlevel.util.ArrayMap", "apply"),
        ("<init>", 2) -> Construct("lowlevel.util.ArrayMap", "apply"),
        ("notEmpty", 0) -> Rename("nonEmpty"),
        ("entries", 0) -> ForEach("foreachEntry", 2),
        ("keys", 0)    -> Collect("foreachKey", "lowlevel.util.DynamicArray"),
        ("values", 0)  -> Collect("foreachValue", "lowlevel.util.DynamicArray"),
      ),
      // wave 3.1d: remaining MAP family — all to ObjectMap, same Construct + ForEach pattern.
      // IntMap<V> -> ObjectMap[Int, V], LongMap<V> -> ObjectMap[Long, V],
      // IntIntMap -> ObjectMap[Int, Int], IntFloatMap -> ObjectMap[Int, Float],
      // ObjectIntMap<K> -> ObjectMap[K, Int], ObjectFloatMap<K> -> ObjectMap[K, Float],
      // ObjectLongMap<K> -> ObjectMap[K, Long].
      "com.badlogic.gdx.utils.IntMap" -> Map(
        ("<init>", 0) -> Construct("lowlevel.util.ObjectMap", "apply"),
        ("<init>", 1) -> Construct("lowlevel.util.ObjectMap", "apply"),
        ("<init>", 2) -> Construct("lowlevel.util.ObjectMap", "apply"),
        ("notEmpty", 0) -> Rename("nonEmpty"),
        ("entries", 0) -> ForEach("foreachEntry", 2),
        ("keys", 0)    -> Collect("foreachKey", "lowlevel.util.DynamicArray"),
        ("values", 0)  -> Collect("foreachValue", "lowlevel.util.DynamicArray"),
      ),
      "com.badlogic.gdx.utils.LongMap" -> Map(
        ("<init>", 0) -> Construct("lowlevel.util.ObjectMap", "apply"),
        ("<init>", 1) -> Construct("lowlevel.util.ObjectMap", "apply"),
        ("<init>", 2) -> Construct("lowlevel.util.ObjectMap", "apply"),
        ("notEmpty", 0) -> Rename("nonEmpty"),
        ("entries", 0) -> ForEach("foreachEntry", 2),
        ("keys", 0)    -> Collect("foreachKey", "lowlevel.util.DynamicArray"),
        ("values", 0)  -> Collect("foreachValue", "lowlevel.util.DynamicArray"),
      ),
      "com.badlogic.gdx.utils.IntIntMap" -> Map(
        ("<init>", 0) -> Construct("lowlevel.util.ObjectMap", "apply"),
        ("<init>", 1) -> Construct("lowlevel.util.ObjectMap", "apply"),
        ("<init>", 2) -> Construct("lowlevel.util.ObjectMap", "apply"),
        ("notEmpty", 0) -> Rename("nonEmpty"),
        ("entries", 0) -> ForEach("foreachEntry", 2),
        ("keys", 0)    -> Collect("foreachKey", "lowlevel.util.DynamicArray"),
        ("values", 0)  -> Collect("foreachValue", "lowlevel.util.DynamicArray"),
      ),
      "com.badlogic.gdx.utils.IntFloatMap" -> Map(
        ("<init>", 0) -> Construct("lowlevel.util.ObjectMap", "apply"),
        ("<init>", 1) -> Construct("lowlevel.util.ObjectMap", "apply"),
        ("<init>", 2) -> Construct("lowlevel.util.ObjectMap", "apply"),
        ("notEmpty", 0) -> Rename("nonEmpty"),
        ("entries", 0) -> ForEach("foreachEntry", 2),
        ("keys", 0)    -> Collect("foreachKey", "lowlevel.util.DynamicArray"),
        ("values", 0)  -> Collect("foreachValue", "lowlevel.util.DynamicArray"),
      ),
      "com.badlogic.gdx.utils.ObjectIntMap" -> Map(
        ("<init>", 0) -> Construct("lowlevel.util.ObjectMap", "apply"),
        ("<init>", 1) -> Construct("lowlevel.util.ObjectMap", "apply"),
        ("<init>", 2) -> Construct("lowlevel.util.ObjectMap", "apply"),
        ("notEmpty", 0) -> Rename("nonEmpty"),
        ("entries", 0) -> ForEach("foreachEntry", 2),
        ("keys", 0)    -> Collect("foreachKey", "lowlevel.util.DynamicArray"),
        ("values", 0)  -> Collect("foreachValue", "lowlevel.util.DynamicArray"),
      ),
      "com.badlogic.gdx.utils.ObjectFloatMap" -> Map(
        ("<init>", 0) -> Construct("lowlevel.util.ObjectMap", "apply"),
        ("<init>", 1) -> Construct("lowlevel.util.ObjectMap", "apply"),
        ("<init>", 2) -> Construct("lowlevel.util.ObjectMap", "apply"),
        ("notEmpty", 0) -> Rename("nonEmpty"),
        ("entries", 0) -> ForEach("foreachEntry", 2),
        ("keys", 0)    -> Collect("foreachKey", "lowlevel.util.DynamicArray"),
        ("values", 0)  -> Collect("foreachValue", "lowlevel.util.DynamicArray"),
      ),
      "com.badlogic.gdx.utils.ObjectLongMap" -> Map(
        ("<init>", 0) -> Construct("lowlevel.util.ObjectMap", "apply"),
        ("<init>", 1) -> Construct("lowlevel.util.ObjectMap", "apply"),
        ("<init>", 2) -> Construct("lowlevel.util.ObjectMap", "apply"),
        ("notEmpty", 0) -> Rename("nonEmpty"),
        ("entries", 0) -> ForEach("foreachEntry", 2),
        ("keys", 0)    -> Collect("foreachKey", "lowlevel.util.DynamicArray"),
        ("values", 0)  -> Collect("foreachValue", "lowlevel.util.DynamicArray"),
      ),
      // wave 3.1d: gdx's ArrayMap -> lowlevel.util.ArrayMap (same as IdentityMap's target)
      "com.badlogic.gdx.utils.ArrayMap" -> Map(
        ("<init>", 0) -> Construct("lowlevel.util.ArrayMap", "apply"),
        ("<init>", 1) -> Construct("lowlevel.util.ArrayMap", "apply"),
        // arity-2: ArrayMap(MkArray, MkArray) — two array factory lambdas lls does not need;
        // sge uses the no-args constructor: `ArrayMap[Node, Matrix4]()`. dropTrailing = 2.
        ("<init>", 2) -> Construct("lowlevel.util.ArrayMap", "apply", dropTrailing = 2),
        // arity-4: ArrayMap(boolean, int, Class, Class) — last 2 are Class tokens lls does not need
        ("<init>", 4) -> Construct("lowlevel.util.ArrayMap", "apply", dropTrailing = 2),
        ("notEmpty", 0) -> Rename("nonEmpty"),
        ("entries", 0) -> ForEach("foreachEntry", 2),
        ("keys", 0)    -> Collect("foreachKey", "lowlevel.util.DynamicArray"),
        ("values", 0)  -> Collect("foreachValue", "lowlevel.util.DynamicArray"),
      ),
      // wave 3.1d: IntSet -> ObjectSet. No entries/keys/values — sets iterate through
      // themselves (Iterable<Integer>), lowered to foreachKey by the phase when applicable.
      "com.badlogic.gdx.utils.IntSet" -> Map(
        ("<init>", 0) -> Construct("lowlevel.util.ObjectSet", "apply"),
        ("<init>", 1) -> Construct("lowlevel.util.ObjectSet", "apply"),
        ("<init>", 2) -> Construct("lowlevel.util.ObjectSet", "apply"),
        ("notEmpty", 0) -> Rename("nonEmpty"),
      ),
      // wave 3.1n: Array family -> DynamicArray.
      // lls DynamicArray.apply[T: MkArray](capacity, ordered) — MkArray resolves for concrete T.
      // A type-parameter T at a construction needs MkArray[T] threaded: COUNTED (state the count).
      // BoolDispatch: Array's `identity` boolean at flagIndex=1 dispatches to ByRef/non-ByRef.
      // DynamicArray has: apply(i), update(i,v), removeValue/removeValueByRef,
      // contains/containsByRef, indexOf/indexOfByRef, lastIndexOf/lastIndexOfByRef,
      // containsAll/containsAllByRef, containsAny/containsAnyByRef, removeAll/removeAllByRef,
      // replaceFirst/replaceFirstByRef, replaceAll/replaceAllByRef.
      // No ForEach needed: DynamicArray supports `for (x <- da)` natively (verified).
      "com.badlogic.gdx.utils.Array" -> Map(
        ("<init>", 0) -> Construct("lowlevel.util.DynamicArray", "apply"),
        ("<init>", 1) -> Construct("lowlevel.util.DynamicArray", "apply"), // fallback — desc keys win
        ("<init>", 2) -> Construct("lowlevel.util.DynamicArray", "apply"),
        // arity 3: Array(boolean, int, ArraySupplier) — drop the ArraySupplier (lls uses MkArray).
        // Array(boolean, int, Class) is in dropMethods.
        ("<init>", 3) -> Construct("lowlevel.util.DynamicArray", "apply", dropTrailing = 1),
        ("get", 1)          -> Rename("apply"),
        ("set", 2)          -> Rename("update"),
        ("removeValue", 2)  -> BoolDispatch(1, "removeValueByRef", "removeValue"),
        ("contains", 2)     -> BoolDispatch(1, "containsByRef", "contains"),
        ("indexOf", 2)      -> BoolDispatch(1, "indexOfByRef", "indexOf"),
        ("lastIndexOf", 2)  -> BoolDispatch(1, "lastIndexOfByRef", "lastIndexOf"),
        ("containsAll", 2)  -> BoolDispatch(1, "containsAllByRef", "containsAll"),
        ("containsAny", 2)  -> BoolDispatch(1, "containsAnyByRef", "containsAny"),
        ("removeAll", 2)    -> BoolDispatch(1, "removeAllByRef", "removeAll"),
        ("replaceFirst", 3) -> BoolDispatch(1, "replaceFirstByRef", "replaceFirst"),
        ("replaceAll", 3)   -> BoolDispatch(1, "replaceAllByRef", "replaceAll"),
        ("notEmpty", 0)     -> Chain(List("nonEmpty")),
        // bean-property renamed isEmpty() -> empty; lls keeps isEmpty
        ("empty", 0)        -> Rename("isEmpty"),
        // parameterless methods: DynamicArray declares peek, first, iterator, nonEmpty as
        // parameterless (no ()) but java calls them with (). Chain with empty parens set
        // produces arr.peek without (). F9's rule: lls methods are parameterless.
        ("peek", 0)         -> Chain(List("peek")),
        ("first", 0)        -> Chain(List("first")),
        ("iterator", 0)     -> Chain(List("iterator")),
        // wave 3.1o: field-write images. DynamicArray exposes `size` as a METHOD (getter only),
        // so `arr.size = n` must become `arr.setSize(n)`. `setSize` handles both growing (pads
        // with defaults) and shrinking (truncates), which is java's Array.size field semantics.
        // `arr.ordered` -> `arr.preserveOrder` (boolean, read-only on DynamicArray).
        ("size", 0)         -> FieldWrite("size", "setSize"),
        ("ordered", 0)      -> Rename("preserveOrder"),
        ("items", 0)        -> IndexedField("items"),
        // wave 3.1p: toArray. DynamicArray.toArray is parenless (def toArray: Array[A], ClassTag-based).
        // Java's Array.toArray() -> da.toArray (parenless). Java's Array.toArray(Class) drops the
        // Class arg — DynamicArray uses inline ClassTag, not a Class parameter.
        ("toArray", 0)      -> Chain(List("toArray")),
        ("toArray", 1)      -> Chain(List("toArray"), dropArgs = true),
        // CharArray.append(char[], off, len) -> DynamicArray.addAll(raw, off, len)
        ("append", 3)       -> Rename("addAll"),
        // wave 3.1u: static factory `Array.with(T... array)` -> `DynamicArray.from(scalaArray)`.
        // The vararg is already packed into a scala.Array by the engine. DynamicArray.from(Array[A])
        // creates a copy, which matches java's Array.with semantics (creates a new Array from varargs).
        // Matched on the qualifier SYMBOL (the source class symbol in static position, §4.56).
        ("with", 1)         -> Template("$Target.from($0)"),
      ),
      // SnapshotArray extends Array — same rewrites. lls DynamicArray has begin()/end() for
      // snapshot support (sge type-mappings.md: "SnapshotArray -> ArrayBuffer with copy-on-modify";
      // lls DynamicArray has begin/end built in).
      "com.badlogic.gdx.utils.SnapshotArray" -> Map(
        ("<init>", 0) -> Construct("lowlevel.util.DynamicArray", "apply"),
        ("<init>", 1) -> Construct("lowlevel.util.DynamicArray", "apply"), // fallback — desc keys win
        ("<init>", 2) -> Construct("lowlevel.util.DynamicArray", "apply"),
        ("<init>", 3) -> Construct("lowlevel.util.DynamicArray", "apply", dropTrailing = 1),
        ("get", 1)          -> Rename("apply"),
        ("set", 2)          -> Rename("update"),
        ("removeValue", 2)  -> BoolDispatch(1, "removeValueByRef", "removeValue"),
        ("contains", 2)     -> BoolDispatch(1, "containsByRef", "contains"),
        ("indexOf", 2)      -> BoolDispatch(1, "indexOfByRef", "indexOf"),
        ("lastIndexOf", 2)  -> BoolDispatch(1, "lastIndexOfByRef", "lastIndexOf"),
        ("containsAll", 2)  -> BoolDispatch(1, "containsAllByRef", "containsAll"),
        ("containsAny", 2)  -> BoolDispatch(1, "containsAnyByRef", "containsAny"),
        ("removeAll", 2)    -> BoolDispatch(1, "removeAllByRef", "removeAll"),
        ("replaceFirst", 3) -> BoolDispatch(1, "replaceFirstByRef", "replaceFirst"),
        ("replaceAll", 3)   -> BoolDispatch(1, "replaceAllByRef", "replaceAll"),
        ("notEmpty", 0)     -> Chain(List("nonEmpty")),
        ("empty", 0)        -> Rename("isEmpty"),
        ("peek", 0)         -> Chain(List("peek")),
        ("first", 0)        -> Chain(List("first")),
        ("iterator", 0)     -> Chain(List("iterator")),
        ("size", 0)         -> FieldWrite("size", "setSize"),
        ("ordered", 0)      -> Rename("preserveOrder"),
        ("items", 0)        -> IndexedField("items"),
        ("toArray", 0)      -> Chain(List("toArray")),
        ("toArray", 1)      -> Chain(List("toArray"), dropArgs = true),
        ("append", 3)       -> Rename("addAll"),
        ("with", 1)         -> Template("$Target.from($0)"),
      ),
      "com.badlogic.gdx.utils.DelayedRemovalArray" -> Map(
        ("<init>", 0) -> Construct("lowlevel.util.DynamicArray", "apply"),
        ("<init>", 1) -> Construct("lowlevel.util.DynamicArray", "apply"), // fallback — desc keys win
        ("<init>", 2) -> Construct("lowlevel.util.DynamicArray", "apply"),
        ("<init>", 3) -> Construct("lowlevel.util.DynamicArray", "apply", dropTrailing = 1),
        ("get", 1)          -> Rename("apply"),
        ("set", 2)          -> Rename("update"),
        ("removeValue", 2)  -> BoolDispatch(1, "removeValueByRef", "removeValue"),
        ("contains", 2)     -> BoolDispatch(1, "containsByRef", "contains"),
        ("indexOf", 2)      -> BoolDispatch(1, "indexOfByRef", "indexOf"),
        ("lastIndexOf", 2)  -> BoolDispatch(1, "lastIndexOfByRef", "lastIndexOf"),
        ("containsAll", 2)  -> BoolDispatch(1, "containsAllByRef", "containsAll"),
        ("containsAny", 2)  -> BoolDispatch(1, "containsAnyByRef", "containsAny"),
        ("removeAll", 2)    -> BoolDispatch(1, "removeAllByRef", "removeAll"),
        ("replaceFirst", 3) -> BoolDispatch(1, "replaceFirstByRef", "replaceFirst"),
        ("replaceAll", 3)   -> BoolDispatch(1, "replaceAllByRef", "replaceAll"),
        ("notEmpty", 0)     -> Chain(List("nonEmpty")),
        ("empty", 0)        -> Rename("isEmpty"),
        ("peek", 0)         -> Chain(List("peek")),
        ("first", 0)        -> Chain(List("first")),
        ("iterator", 0)     -> Chain(List("iterator")),
        ("size", 0)         -> FieldWrite("size", "setSize"),
        ("ordered", 0)      -> Rename("preserveOrder"),
        ("items", 0)        -> IndexedField("items"),
        ("toArray", 0)      -> Chain(List("toArray")),
        ("toArray", 1)      -> Chain(List("toArray"), dropArgs = true),
        ("append", 3)       -> Rename("addAll"),
        ("with", 1)         -> Template("$Target.from($0)"),
      ),
      // Primitive arrays: no identity flag (no BoolDispatch needed), same get->apply, set->update.
      // sge type-mappings.md: "IntArray -> DynamicArray[Int]", etc.
      "com.badlogic.gdx.utils.IntArray" -> Map(
        ("<init>", 0) -> Construct("lowlevel.util.DynamicArray", "apply"),
        ("<init>", 1) -> Construct("lowlevel.util.DynamicArray", "apply"), // fallback — desc keys win
        ("<init>", 2) -> Construct("lowlevel.util.DynamicArray", "apply"),
        ("get", 1)      -> Rename("apply"),
        ("set", 2)      -> Rename("update"),
        ("notEmpty", 0) -> Chain(List("nonEmpty")),
        ("empty", 0)    -> Rename("isEmpty"),
        ("peek", 0)     -> Chain(List("peek")),
        ("first", 0)    -> Chain(List("first")),
        ("iterator", 0) -> Chain(List("iterator")),
        ("size", 0)     -> FieldWrite("size", "setSize"),
        ("ordered", 0)  -> Rename("preserveOrder"),
        ("items", 0)        -> IndexedField("items"),
        ("toArray", 0)      -> Chain(List("toArray")),
        ("toArray", 1)      -> Chain(List("toArray"), dropArgs = true),
        // IntArray.incr(index, value) -> { val i = index; da(i) = da(i) + value }
        // $recv appears twice — Template binds it to a temp (§4.4/F7).
        ("incr", 2)     -> Template("{ val bpIdx = $0; $recv(bpIdx) = $recv(bpIdx) + $1 }"),
        // IntArray.add(4 args): DynamicArray has up to 3-arg add; split into two calls.
        ("add", 4)      -> Template("{ $recv.add($0, $1); $recv.add($2, $3) }"),
        ("with", 1)     -> Template("$Target.from($0)"),
      ),
      "com.badlogic.gdx.utils.FloatArray" -> Map(
        ("<init>", 0) -> Construct("lowlevel.util.DynamicArray", "apply"),
        ("<init>", 1) -> Construct("lowlevel.util.DynamicArray", "apply"), // fallback — desc keys win
        ("<init>", 2) -> Construct("lowlevel.util.DynamicArray", "apply"),
        ("get", 1)      -> Rename("apply"),
        ("set", 2)      -> Rename("update"),
        ("notEmpty", 0) -> Chain(List("nonEmpty")),
        ("empty", 0)    -> Rename("isEmpty"),
        ("peek", 0)     -> Chain(List("peek")),
        ("first", 0)    -> Chain(List("first")),
        ("iterator", 0) -> Chain(List("iterator")),
        ("size", 0)     -> FieldWrite("size", "setSize"),
        ("ordered", 0)  -> Rename("preserveOrder"),
        ("items", 0)        -> IndexedField("items"),
        ("toArray", 0)      -> Chain(List("toArray")),
        ("toArray", 1)      -> Chain(List("toArray"), dropArgs = true),
        ("with", 1)     -> Template("$Target.from($0)"),
      ),
      "com.badlogic.gdx.utils.LongArray" -> Map(
        ("<init>", 0) -> Construct("lowlevel.util.DynamicArray", "apply"),
        ("<init>", 1) -> Construct("lowlevel.util.DynamicArray", "apply"), // fallback — desc keys win
        ("<init>", 2) -> Construct("lowlevel.util.DynamicArray", "apply"),
        ("get", 1)      -> Rename("apply"),
        ("set", 2)      -> Rename("update"),
        ("notEmpty", 0) -> Chain(List("nonEmpty")),
        ("empty", 0)    -> Rename("isEmpty"),
        ("peek", 0)     -> Chain(List("peek")),
        ("first", 0)    -> Chain(List("first")),
        ("iterator", 0) -> Chain(List("iterator")),
        ("size", 0)     -> FieldWrite("size", "setSize"),
        ("ordered", 0)  -> Rename("preserveOrder"),
        ("items", 0)        -> IndexedField("items"),
        ("toArray", 0)      -> Chain(List("toArray")),
        ("toArray", 1)      -> Chain(List("toArray"), dropArgs = true),
        // LongArray.incr(index, value) -> { val i = index; da(i) = da(i) + value }
        ("incr", 2)     -> Template("{ val bpIdx = $0; $recv(bpIdx) = $recv(bpIdx) + $1 }"),
        // LongArray.incr(value) -> add value to ALL elements
        ("incr", 1)     -> Template("{ var bpI = 0; while (bpI < $recv.size) { $recv(bpI) = $recv(bpI) + $0; bpI += 1 } }"),
        // LongArray.mul(index, value) -> { val i = index; da(i) = da(i) * value }
        ("mul", 2)      -> Template("{ val bpIdx = $0; $recv(bpIdx) = $recv(bpIdx) * $1 }"),
        // LongArray.mul(value) -> multiply ALL elements
        ("mul", 1)      -> Template("{ var bpI = 0; while (bpI < $recv.size) { $recv(bpI) = $recv(bpI) * $0; bpI += 1 } }"),
        ("with", 1)     -> Template("$Target.from($0)"),
        // --- 3.1ae: gdx-test residue ---
        // LongArray.add(4 args): DynamicArray has up to 3-arg add; split into two calls.
        ("add", 4)      -> Template("{ $recv.add($0, $1); $recv.add($2, $3) }"),
        // LongArray.shrink() returns long[] in java; DynamicArray.shrink() returns Unit.
        // Return the backing array after shrink, matching java's return type.
        ("shrink", 0)   -> Template("{ $recv.shrink(); $recv }.items"),
        // LongArray.ensureCapacity(int) returns long[]; DynamicArray returns Unit.
        ("ensureCapacity", 1) -> Template("{ $recv.ensureCapacity($0); $recv }.items"),
        // LongArray.setSize(int) returns long[]; DynamicArray returns Unit.
        ("setSize", 1)  -> Template("{ $recv.setSize($0); $recv }.items"),
        // LongArray.resize(int) is protected, returns long[]; DynamicArray has no resize.
        // setSize + items is the faithful image: allocate to newSize, pad with zeros, return array.
        ("resize", 1)   -> Template("{ $recv.setSize($0); $recv }.items"),
      ),
      "com.badlogic.gdx.utils.ShortArray" -> Map(
        ("<init>", 0) -> Construct("lowlevel.util.DynamicArray", "apply"),
        ("<init>", 1) -> Construct("lowlevel.util.DynamicArray", "apply"), // fallback — desc keys win
        ("<init>", 2) -> Construct("lowlevel.util.DynamicArray", "apply"),
        ("get", 1)      -> Rename("apply"),
        ("set", 2)      -> Rename("update"),
        // wave 3.1t: Java implicitly narrows int->short at ShortArray.add(short). After retarget,
        // DynamicArray[Short].add(Short) does not accept Int. Insert .toShort cast.
        ("add", 1)      -> Template("$recv.add($0.toShort)"),
        ("add", 2)      -> Template("$recv.add($0.toShort, $1.toShort)"),
        ("add", 3)      -> Template("$recv.add($0.toShort, $1.toShort, $2.toShort)"),
        ("notEmpty", 0) -> Chain(List("nonEmpty")),
        ("empty", 0)    -> Rename("isEmpty"),
        ("peek", 0)     -> Chain(List("peek")),
        ("first", 0)    -> Chain(List("first")),
        ("iterator", 0) -> Chain(List("iterator")),
        ("size", 0)     -> FieldWrite("size", "setSize"),
        ("ordered", 0)  -> Rename("preserveOrder"),
        ("items", 0)        -> IndexedField("items"),
        ("toArray", 0)      -> Chain(List("toArray")),
        ("toArray", 1)      -> Chain(List("toArray"), dropArgs = true),
        ("with", 1)     -> Template("$Target.from($0)"),
      ),
      "com.badlogic.gdx.utils.ByteArray" -> Map(
        ("<init>", 0) -> Construct("lowlevel.util.DynamicArray", "apply"),
        ("<init>", 1) -> Construct("lowlevel.util.DynamicArray", "apply"), // fallback — desc keys win
        ("<init>", 2) -> Construct("lowlevel.util.DynamicArray", "apply"),
        ("get", 1)      -> Rename("apply"),
        ("set", 2)      -> Rename("update"),
        ("notEmpty", 0) -> Chain(List("nonEmpty")),
        ("empty", 0)    -> Rename("isEmpty"),
        ("peek", 0)     -> Chain(List("peek")),
        ("first", 0)    -> Chain(List("first")),
        ("iterator", 0) -> Chain(List("iterator")),
        ("size", 0)     -> FieldWrite("size", "setSize"),
        ("ordered", 0)  -> Rename("preserveOrder"),
        ("items", 0)        -> IndexedField("items"),
        ("toArray", 0)      -> Chain(List("toArray")),
        ("toArray", 1)      -> Chain(List("toArray"), dropArgs = true),
        // ensureCapacity(int) returns T[] in java; DynamicArray.ensureCapacity returns Unit.
        // The callers assign the result for indexed access into the backing array — return .items
        // to get the raw Array[Byte], matching the java return type of byte[].
        ("ensureCapacity", 1) -> Template("{ $recv.ensureCapacity($0); $recv }.items"),
        ("with", 1)     -> Template("$Target.from($0)"),
      ),
      "com.badlogic.gdx.utils.CharArray" -> Map(
        ("<init>", 0) -> Construct("lowlevel.util.DynamicArray", "apply"),
        ("<init>", 1) -> Construct("lowlevel.util.DynamicArray", "apply"), // fallback — desc keys win
        ("<init>", 2) -> Construct("lowlevel.util.DynamicArray", "apply"),
        ("get", 1)      -> Rename("apply"),
        ("set", 2)      -> Rename("update"),
        ("notEmpty", 0) -> Chain(List("nonEmpty")),
        ("empty", 0)    -> Rename("isEmpty"),
        ("peek", 0)     -> Chain(List("peek")),
        ("first", 0)    -> Chain(List("first")),
        ("iterator", 0) -> Chain(List("iterator")),
        ("size", 0)     -> FieldWrite("size", "setSize"),
        ("ordered", 0)  -> Rename("preserveOrder"),
        ("items", 0)        -> IndexedField("items"),
        ("toArray", 0)      -> Chain(List("toArray")),
        ("toArray", 1)      -> Chain(List("toArray"), dropArgs = true),
        ("append", 3)   -> Rename("addAll"),
        ("with", 1)     -> Template("$Target.from($0)"),
      ),
      "com.badlogic.gdx.utils.BooleanArray" -> Map(
        ("<init>", 0) -> Construct("lowlevel.util.DynamicArray", "apply"),
        ("<init>", 1) -> Construct("lowlevel.util.DynamicArray", "apply"), // fallback — desc keys win
        ("<init>", 2) -> Construct("lowlevel.util.DynamicArray", "apply"),
        ("get", 1)      -> Rename("apply"),
        ("set", 2)      -> Rename("update"),
        ("notEmpty", 0) -> Chain(List("nonEmpty")),
        ("empty", 0)    -> Rename("isEmpty"),
        ("peek", 0)     -> Chain(List("peek")),
        ("first", 0)    -> Chain(List("first")),
        ("iterator", 0) -> Chain(List("iterator")),
        ("size", 0)     -> FieldWrite("size", "setSize"),
        ("ordered", 0)  -> Rename("preserveOrder"),
        ("items", 0)        -> IndexedField("items"),
        ("toArray", 0)      -> Chain(List("toArray")),
        ("toArray", 1)      -> Chain(List("toArray"), dropArgs = true),
        ("with", 1)     -> Template("$Target.from($0)"),
      ),
      // Queue -> mutable.ArrayDeque (sge type-mappings.md: "Queue -> Scala stdlib queues";
      // sge's QueueBitsTest confirms mutable.ArrayDeque). addLast -> addOne, addFirst -> prepend,
      // removeLast -> removeLast, removeFirst -> removeHead. indexOf/removeValue/contains take
      // the identity boolean — dispatched via Template since ArrayDeque has no ByRef variants.
      "com.badlogic.gdx.utils.Queue" -> Map(
        ("<init>", 0) -> Template("scala.collection.mutable.ArrayDeque[$T0]()"),
        ("<init>", 1) -> Template("scala.collection.mutable.ArrayDeque[$T0]($0)"),
        ("get", 1)          -> Rename("apply"),
        ("addLast", 1)      -> Rename("addOne"),
        ("addFirst", 1)     -> Rename("prepend"),
        ("removeLast", 0)   -> Template("$recv.removeLast()"),
        ("removeFirst", 0)  -> Template("$recv.removeHead()"),
        ("removeIndex", 1)  -> Rename("remove"),
        ("removeValue", 2)  -> Template("{ val bpIdx = (if ($1) $recv.indexWhere(_ eq $0) else $recv.indexOf($0)); if (bpIdx >= 0) { $recv.remove(bpIdx); true } else false }"),
        ("indexOf", 2)      -> Template("(if ($1) $recv.indexWhere(_ eq $0) else $recv.indexOf($0))"),
        ("contains", 2)     -> Template("(if ($1) $recv.exists(_ eq $0) else $recv.contains($0))"),
        ("notEmpty", 0)     -> Chain(List("nonEmpty")),
        ("empty", 0)        -> Rename("isEmpty"),
        ("first", 0)        -> Chain(List("head")),
        ("last", 0)         -> Chain(List("last")),
        ("peek", 0)         -> Chain(List("head")),
        ("iterator", 0)     -> Chain(List("iterator")),
        ("toArray", 0)      -> Chain(List("toArray")),
        ("toArray", 1)      -> Chain(List("toArray"), dropArgs = true),
      ),
    )

  /** DESCRIPTOR-KEYED retarget rewrites — for arity-1 constructors where `(name, arity)` is
    * ambiguous. `Array` has four surviving arity-1 constructors: `(int)` capacity,
    * `(ArraySupplier)` factory, `(Array)` copy, `(T[])` from-array. §4.55: a map from an
    * over-approximate key to a single value is a choice nobody made. */
  def libCollectionConstructRewritesByDesc: Map[String, Map[(String, Descriptor), balticporter.transform.CollectionsTransform.RetargetRewrite]] =
    import balticporter.transform.CollectionsTransform.RetargetRewrite.*
    val intDesc        = Descriptor(List(Param.Prim("int")))
    val arrayDesc      = Descriptor(List(Param.Named("Array")))
    val supplierDesc   = Descriptor(List(Param.Named("ArraySupplier")))
    // wave 3.1t: Array(T[]) — copy-construct from a raw array. DynamicArray.from takes a
    // DynamicArray, not a raw scala.Array, so a Template constructs and addAll's. The $T0
    // placeholder is an AST hole (Tree.Ident with the type arg's head symbol), so
    // PackageRenameTransform reaches and renames the FQN correctly.
    val tArrDesc       = Descriptor(List(Param.Arr(Param.Named("T"))))
    val intArrDesc     = Descriptor(List(Param.Arr(Param.Prim("int"))))
    val floatArrDesc   = Descriptor(List(Param.Arr(Param.Prim("float"))))
    val longArrDesc    = Descriptor(List(Param.Arr(Param.Prim("long"))))
    val shortArrDesc   = Descriptor(List(Param.Arr(Param.Prim("short"))))
    val byteArrDesc    = Descriptor(List(Param.Arr(Param.Prim("byte"))))
    val charArrDesc    = Descriptor(List(Param.Arr(Param.Prim("char"))))
    val boolArrDesc    = Descriptor(List(Param.Arr(Param.Prim("boolean"))))
    // wave 3.1t: addAll(Array/Self, int, int) desc — the 3-arg addAll copies a range from
    // another Array/Self. After retarget, the first arg is DynamicArray, but
    // DynamicArray.addAll(Object, int, int) takes the raw BACKING ARRAY, not a DynamicArray.
    // Extract .items to pass the raw array.
    val addAllArrayDesc = Descriptor(List(Param.Named("Array"), Param.Prim("int"), Param.Prim("int")))
    def genericArrayInitByDesc = Map(
      ("<init>", intDesc)      -> Construct("lowlevel.util.DynamicArray", "apply"),
      ("<init>", arrayDesc)    -> Construct("lowlevel.util.DynamicArray", "from"),
      ("<init>", tArrDesc)     -> Template("{ val bpSrc = $0; val bpDa = $Target[$T0](); bpDa.addAll(bpSrc, 0, bpSrc.length); bpDa }"),
      ("<init>", supplierDesc) -> Construct("lowlevel.util.DynamicArray", "apply", dropTrailing = 1),
      // Cast .items to Array[$T0] to handle wildcard argument types — java arrays are covariant,
      // scala arrays are invariant, so `DynamicArray[? <: T].items` is `Array[? <: T]` which
      // does not conform to `Array[T]`. The asInstanceOf is safe: items really IS an Array[T]
      // at erasure and addAll only reads from it.
      ("addAll", addAllArrayDesc) -> Template("$recv.addAll($0.items.asInstanceOf[scala.Array[$T0]], $1, $2)"),
    )
    def primArrayInitByDesc(selfDesc: Descriptor, rawArrDesc: Descriptor, elemType: String) = Map(
      ("<init>", intDesc)    -> Construct("lowlevel.util.DynamicArray", "apply"),
      ("<init>", selfDesc)   -> Construct("lowlevel.util.DynamicArray", "from"),
      // wave 3.1y: init from raw primitive array — e.g. LongArray(long[]).
      // DynamicArray.apply takes capacity (Int), not a raw array. Construct + addAll.
      // The element type is fixed (primitive), so we template it explicitly.
      ("<init>", rawArrDesc) -> Template(s"{ val bpSrc = $$0; val bpDa = $$Target[$elemType](); bpDa.addAll(bpSrc, 0, bpSrc.length); bpDa }"),
      ("addAll", Descriptor(List(selfDesc.params.head, Param.Prim("int"), Param.Prim("int")))) ->
        Template("$recv.addAll($0.items, $1, $2)"),
    )
    Map(
      "com.badlogic.gdx.utils.Array"                -> genericArrayInitByDesc,
      "com.badlogic.gdx.utils.SnapshotArray"        -> genericArrayInitByDesc,
      "com.badlogic.gdx.utils.DelayedRemovalArray"  -> genericArrayInitByDesc,
      "com.badlogic.gdx.utils.IntArray"             -> primArrayInitByDesc(Descriptor(List(Param.Named("IntArray"))), intArrDesc, "scala.Int"),
      "com.badlogic.gdx.utils.FloatArray"           -> primArrayInitByDesc(Descriptor(List(Param.Named("FloatArray"))), floatArrDesc, "scala.Float"),
      "com.badlogic.gdx.utils.LongArray"            -> primArrayInitByDesc(Descriptor(List(Param.Named("LongArray"))), longArrDesc, "scala.Long"),
      "com.badlogic.gdx.utils.ShortArray"           -> primArrayInitByDesc(Descriptor(List(Param.Named("ShortArray"))), shortArrDesc, "scala.Short"),
      "com.badlogic.gdx.utils.ByteArray"            -> primArrayInitByDesc(Descriptor(List(Param.Named("ByteArray"))), byteArrDesc, "scala.Byte"),
      // CharArray: init-by-desc AND append overloads (arity 1 is ambiguous — char/CharSequence/String/int).
      // append(char) -> add(char), append(CharSequence)/append(String) -> counted (no single-expression
      // translation — sge iterates char-by-char), append(int) -> counted (same reason — `add(c.toChar)`
      // needs a cast the Rename entry cannot express).
      "com.badlogic.gdx.utils.CharArray"            -> (primArrayInitByDesc(Descriptor(List(Param.Named("CharArray"))), charArrDesc, "scala.Char") ++ Map(
        // CharArray(String) -> construct from string's char array.
        ("<init>", Descriptor(List(Param.Named("String")))) ->
          Template("{ val bpStr = $0; val bpDa = $Target[scala.Char](); bpDa.addAll(bpStr.toCharArray, 0, bpStr.length); bpDa }"),
        // CharArray(CharSequence) -> same as String, via toString.
        ("<init>", Descriptor(List(Param.Named("CharSequence")))) ->
          Template("{ val bpCs = $0.toString; val bpDa = $Target[scala.Char](); bpDa.addAll(bpCs.toCharArray, 0, bpCs.length); bpDa }"),
        ("append", Descriptor(List(Param.Prim("char")))) -> Rename("add"),
        // append(CharArray) -> addAll(other) — copies all chars from another CharArray.
        // After retarget both are DynamicArray[Char], and DynamicArray.addAll(DynamicArray) exists.
        ("append", Descriptor(List(Param.Named("CharArray")))) -> Rename("addAll"),
        // append(CharSequence/String) -> addAll(cs.toString.toCharArray, 0, len).
        // Template: $0 appears once, $recv appears once — no temp binding needed.
        ("append", Descriptor(List(Param.Named("CharSequence")))) ->
          Template("{ val bpCa = $0.toString.toCharArray; $recv.addAll(bpCa, 0, bpCa.length) }"),
        ("append", Descriptor(List(Param.Named("String")))) ->
          Template("{ val bpCa = $0.toString.toCharArray; $recv.addAll(bpCa, 0, bpCa.length) }"),
        // append(int) -> add(c.toChar) — the int is a codepoint, cast to Char.
        ("append", Descriptor(List(Param.Prim("int")))) ->
          Template("$recv.add($0.toChar)"),
        // indexOf(String) -> indexOf(Char): DynamicArray.indexOf takes a single element.
        // Java's CharArray.indexOf(String) is a substring search; the only occurrence
        // in gdx/src is indexOf("\n"), a single-char string, so .charAt(0) is faithful.
        ("indexOf", Descriptor(List(Param.Named("String")))) ->
          Template("$recv.indexOf($0.charAt(0))"),
      )),
      "com.badlogic.gdx.utils.BooleanArray"         -> primArrayInitByDesc(Descriptor(List(Param.Named("BooleanArray"))), boolArrDesc, "scala.Boolean"),
      // Queue: arity-1 is ambiguous — Queue(int) capacity vs Queue(int, Class) / Queue(int, ArraySupplier).
      // Queue(int) -> new ArrayDeque[T](capacity). Queue(int, Class) already in dropMethods.
      // Queue(int, ArraySupplier) already in dropMethods.
      "com.badlogic.gdx.utils.Queue" -> Map(
        ("<init>", intDesc) -> Template("new scala.collection.mutable.ArrayDeque[$T0]($0)"),
        ("<init>", Descriptor(List(Param.Prim("int"), Param.Named("Class")))) ->
          Template("new scala.collection.mutable.ArrayDeque[$T0]($0)"),
        ("<init>", Descriptor(List(Param.Prim("int"), Param.Named("ArraySupplier")))) ->
          Template("new scala.collection.mutable.ArrayDeque[$T0]($0)"),
      ),
      // --- 3.1ae: gdx-test residue ---
      // Bits(Bits) copy constructor -> $0.clone(). arity 1 is ambiguous with Bits(int) capacity.
      // BitSet.clone() returns BitSet; the cast is safe because clone() on a mutable.BitSet
      // returns mutable.BitSet at runtime, but the static return type is Object.
      "com.badlogic.gdx.utils.Bits" -> Map(
        ("<init>", Descriptor(List(Param.Named("Bits")))) ->
          Template("$0.clone().asInstanceOf[scala.collection.mutable.BitSet]"),
      ),
    )

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
    // WHOLE-PROGRAM detection: `Everywhere()` auto-detects bean pairs (getX/setX -> x/x_=) across
    // the whole library. Dependents follow the base's published shape through
    // `PortMapTransform.followMemberRenames` rather than re-deciding (wave 1.2h).
    // Measured: base 0, gdx-test 217/4, screens 0/16-0, vfx 0/64-0, ai 0/108-2-2,
    // textra 0, gltf 0, visui 7 (floor).
    new balticporter.transform.BeanPropertyTransform(beanPropertyPairs, beanPropertyTargets, scope = balticporter.tir.RuleScope.Everywhere())

  /** WHICH pairs collapse to a plain `var`/`val` instead of a `def` pair (`DESIGN.md` §8.5).
    *
    * Per ENTRY, because that is where the decision belongs: `def-pair` is the default and every
    * entry not named here keeps exactly the form it has always had. The phase REFUSES a mismatch
    * rather than picking — a `var` needs the setter java published and a `val` needs storage nothing
    * writes — so a wrong answer here is a counted `idiom(refused)` row and never a silent change of
    * surface.
    *
    * ==THIS LIST IS THE WHOLE COLLAPSIBLE POPULATION, refusals included==
    * The first tranche was 13 hand-read `com.badlogic.gdx.maps` entries; widening it to every pair
    * the run reported collapsible asked for **77 more and got 47**, at 0 compile errors. The 30 that
    * did not are one shape under one guard — `MutableStorage`, a GET-ONLY property over storage the
    * program assigns elsewhere (`ClickListener#pressed`, `DragAndDrop#currentDragActor`,
    * `Polygon#vertices`, …). Their refusal is PERMANENT and correct rather than an engine gap: a
    * `val` there would not compile and a `var` would publish a writer java never had, so the `def`
    * pair is the faithful form.
    *
    * They are declared ANYWAY. A permanent refusal in `policy` would be a noise floor — that lane is
    * a work list an operator is meant to clear, and this file carries the lesson one policy up. This
    * one lands in `idiom(refused)`, which is a DENOMINATOR, and the difference is the whole reason
    * the idiom layer has three lanes: a declared entry makes the run SAY `MutableStorage` at that
    * property, while dropping the entry makes it say `NotRequested`, which would be false.
    *
    * `MapLayer#opacity` is the one collapsible-looking pair that is NOT here, for the neighbouring
    * reason: its getter multiplies by the parent's, so it is computed and never converts. Named so a
    * reader does not add it back.
    *
    * What the run then reports is the honest denominator, with nothing left unasked: 30
    * `ComputedBody`, 30 `MutableStorage`, 14 `OverriddenBelow`, 2 `PairRefused`, 1
    * `ConcreteRelative`, 0 `NotRequested`. */
  def beanPropertyTargets: Map[String, balticporter.transform.BeanPropertyTransform.Target] =
    import balticporter.transform.BeanPropertyTransform.Target
    Map(
      // -- `var`: a get/set pair, where a public `var` is exactly the surface java published
      "com.badlogic.gdx.graphics.profiling.GLProfiler#listener" -> Target.Var,
      "com.badlogic.gdx.maps.MapLayer#name" -> Target.Var,
      "com.badlogic.gdx.maps.MapLayer#parallaxX" -> Target.Var,
      "com.badlogic.gdx.maps.MapLayer#parallaxY" -> Target.Var,
      "com.badlogic.gdx.maps.MapLayer#visible" -> Target.Var,
      "com.badlogic.gdx.maps.MapObject#color" -> Target.Var,
      "com.badlogic.gdx.maps.MapObject#name" -> Target.Var,
      "com.badlogic.gdx.maps.MapObject#opacity" -> Target.Var,
      "com.badlogic.gdx.maps.MapObject#visible" -> Target.Var,
      "com.badlogic.gdx.maps.objects.PolygonMapObject#polygon" -> Target.Var,
      "com.badlogic.gdx.maps.objects.PolylineMapObject#polyline" -> Target.Var,
      "com.badlogic.gdx.maps.objects.TextMapObject#bold" -> Target.Var,
      "com.badlogic.gdx.maps.objects.TextMapObject#fontFamily" -> Target.Var,
      "com.badlogic.gdx.maps.objects.TextMapObject#horizontalAlign" -> Target.Var,
      "com.badlogic.gdx.maps.objects.TextMapObject#italic" -> Target.Var,
      "com.badlogic.gdx.maps.objects.TextMapObject#kerning" -> Target.Var,
      "com.badlogic.gdx.maps.objects.TextMapObject#pixelSize" -> Target.Var,
      "com.badlogic.gdx.maps.objects.TextMapObject#rotation" -> Target.Var,
      "com.badlogic.gdx.maps.objects.TextMapObject#strikeout" -> Target.Var,
      "com.badlogic.gdx.maps.objects.TextMapObject#text" -> Target.Var,
      "com.badlogic.gdx.maps.objects.TextMapObject#underline" -> Target.Var,
      "com.badlogic.gdx.maps.objects.TextMapObject#verticalAlign" -> Target.Var,
      "com.badlogic.gdx.maps.objects.TextMapObject#wrap" -> Target.Var,
      "com.badlogic.gdx.maps.objects.TextureMapObject#originX" -> Target.Var,
      "com.badlogic.gdx.maps.objects.TextureMapObject#originY" -> Target.Var,
      "com.badlogic.gdx.maps.objects.TextureMapObject#rotation" -> Target.Var,
      "com.badlogic.gdx.maps.objects.TextureMapObject#scaleX" -> Target.Var,
      "com.badlogic.gdx.maps.objects.TextureMapObject#scaleY" -> Target.Var,
      "com.badlogic.gdx.maps.objects.TextureMapObject#textureRegion" -> Target.Var,
      "com.badlogic.gdx.maps.objects.TextureMapObject#x" -> Target.Var,
      "com.badlogic.gdx.maps.objects.TextureMapObject#y" -> Target.Var,
      "com.badlogic.gdx.maps.tiled.TiledMapImageLayer#region" -> Target.Var,
      "com.badlogic.gdx.maps.tiled.TiledMapImageLayer#repeatX" -> Target.Var,
      "com.badlogic.gdx.maps.tiled.TiledMapImageLayer#repeatY" -> Target.Var,
      "com.badlogic.gdx.maps.tiled.TiledMapImageLayer#x" -> Target.Var,
      "com.badlogic.gdx.maps.tiled.TiledMapImageLayer#y" -> Target.Var,
      "com.badlogic.gdx.maps.tiled.TiledMapTileSet#name" -> Target.Var,
      "com.badlogic.gdx.maps.tiled.objects.TiledMapTileMapObject#flipHorizontally" -> Target.Var,
      "com.badlogic.gdx.maps.tiled.objects.TiledMapTileMapObject#flipVertically" -> Target.Var,
      "com.badlogic.gdx.maps.tiled.objects.TiledMapTileMapObject#tile" -> Target.Var,
      "com.badlogic.gdx.maps.tiled.renderers.HexagonalTiledMapRenderer#hexSideLength" -> Target.Var,
      "com.badlogic.gdx.maps.tiled.renderers.HexagonalTiledMapRenderer#staggerAxisX" -> Target.Var,
      "com.badlogic.gdx.maps.tiled.renderers.HexagonalTiledMapRenderer#staggerIndexEven" -> Target.Var,
      "com.badlogic.gdx.scenes.scene2d.utils.ClickListener#button" -> Target.Var,
      "com.badlogic.gdx.scenes.scene2d.utils.ClickListener#tapCount" -> Target.Var,
      "com.badlogic.gdx.scenes.scene2d.utils.ClickListener#tapSquareSize" -> Target.Var,
      "com.badlogic.gdx.scenes.scene2d.utils.DragAndDrop#dragTime" -> Target.Var,
      "com.badlogic.gdx.scenes.scene2d.utils.DragListener#button" -> Target.Var,
      "com.badlogic.gdx.scenes.scene2d.utils.DragListener#tapSquareSize" -> Target.Var,
      "com.badlogic.gdx.scenes.scene2d.utils.Selection#multiple" -> Target.Var,
      "com.badlogic.gdx.scenes.scene2d.utils.Selection#programmaticChangeEvents" -> Target.Var,
      "com.badlogic.gdx.scenes.scene2d.utils.Selection#required" -> Target.Var,
      "com.badlogic.gdx.scenes.scene2d.utils.Selection#toggle" -> Target.Var,
      // -- `val`: a get-only property over storage the declaration fills and nothing reassigns
      "com.badlogic.gdx.maps.Map#layers" -> Target.Val,
      "com.badlogic.gdx.maps.Map#properties" -> Target.Val,
      "com.badlogic.gdx.maps.MapLayer#objects" -> Target.Val,
      "com.badlogic.gdx.maps.MapLayer#properties" -> Target.Val,
      "com.badlogic.gdx.maps.MapObject#properties" -> Target.Val,
      "com.badlogic.gdx.math.collision.OrientedBoundingBox#bounds" -> Target.Val,
      "com.badlogic.gdx.math.collision.OrientedBoundingBox#vertices" -> Target.Val,
      // -- and the get-only ones the engine REFUSES under `MutableStorage`, declared ANYWAY:
      //    the port's answer for the whole collapsible population then lives in one place,
      //    and the lane says WHY each of these is still a `def` pair rather than saying
      //    nothing about it. `idiom(refused)` is a DENOMINATOR and not a work list, which is
      //    what makes a permanent refusal belong in it.
      "com.badlogic.gdx.graphics.Cubemap#cubemapData" -> Target.Val,
      "com.badlogic.gdx.graphics.Texture#textureData" -> Target.Val,
      "com.badlogic.gdx.graphics.g2d.SpriteCache#customShader" -> Target.Val,
      "com.badlogic.gdx.graphics.profiling.GLProfiler#enabled" -> Target.Val,
      "com.badlogic.gdx.maps.objects.CircleMapObject#circle" -> Target.Val,
      "com.badlogic.gdx.maps.objects.EllipseMapObject#ellipse" -> Target.Val,
      "com.badlogic.gdx.maps.objects.PointMapObject#point" -> Target.Val,
      "com.badlogic.gdx.maps.objects.RectangleMapObject#rectangle" -> Target.Val,
      "com.badlogic.gdx.maps.objects.TextMapObject#rectangle" -> Target.Val,
      "com.badlogic.gdx.maps.tiled.TiledMapTileSet#properties" -> Target.Val,
      "com.badlogic.gdx.maps.tiled.tiles.AnimatedTiledMapTile#frameTiles" -> Target.Val,
      "com.badlogic.gdx.math.Polygon#rotation" -> Target.Val,
      "com.badlogic.gdx.math.Polygon#vertices" -> Target.Val,
      "com.badlogic.gdx.scenes.scene2d.ui.List#cullingArea" -> Target.Val,
      "com.badlogic.gdx.scenes.scene2d.ui.ScrollPane#fadeScrollBars" -> Target.Val,
      "com.badlogic.gdx.scenes.scene2d.ui.ScrollPane#overscrollDistance" -> Target.Val,
      "com.badlogic.gdx.scenes.scene2d.ui.ScrollPane#variableSizeKnobs" -> Target.Val,
      "com.badlogic.gdx.scenes.scene2d.ui.SelectBox#clickListener" -> Target.Val,
      "com.badlogic.gdx.scenes.scene2d.utils.BaseDrawable#name" -> Target.Val,
      "com.badlogic.gdx.scenes.scene2d.utils.ClickListener#pressed" -> Target.Val,
      "com.badlogic.gdx.scenes.scene2d.utils.ClickListener#pressedButton" -> Target.Val,
      "com.badlogic.gdx.scenes.scene2d.utils.ClickListener#pressedPointer" -> Target.Val,
      "com.badlogic.gdx.scenes.scene2d.utils.ClickListener#touchDownX" -> Target.Val,
      "com.badlogic.gdx.scenes.scene2d.utils.ClickListener#touchDownY" -> Target.Val,
      "com.badlogic.gdx.scenes.scene2d.utils.DragAndDrop#currentDragActor" -> Target.Val,
      "com.badlogic.gdx.scenes.scene2d.utils.DragAndDrop#currentPayload" -> Target.Val,
      "com.badlogic.gdx.scenes.scene2d.utils.DragAndDrop#currentSource" -> Target.Val,
      "com.badlogic.gdx.scenes.scene2d.utils.DragListener#dragging" -> Target.Val,
      "com.badlogic.gdx.scenes.scene2d.utils.TiledDrawable#align" -> Target.Val,
      "com.badlogic.gdx.scenes.scene2d.utils.TiledDrawable#scale" -> Target.Val,
    )

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

  /** RETARGET COERCIONS — boundary wraps inserted by `coerce` when a retarget target meets a slot
    * whose head type is a different family.
    *
    * Keyed by (actual head FQN, expected head FQN), both in the TARGET namespace (post-retarget).
    * `$0` in the template is the actual value, inserted as an AST hole. */
  def libRetargetCoercions: Map[(String, String), String] = Map(
    // DynamicArray -> JavaIterable: wrap with fromIterator so the shim delegates to DA's iterator.
    ("lowlevel.util.DynamicArray", "balticporter.runtime.JavaIterable") ->
      "balticporter.runtime.JavaIterable.fromIterator(() => $0.iterator)",
    // DynamicArray -> JavaIterator: wrap DA's iterator into a JavaIterator.
    // MapProperties.keys/values return a DynamicArray where the method declares JavaIterator.
    ("lowlevel.util.DynamicArray", "balticporter.runtime.JavaIterator") ->
      "balticporter.runtime.JavaIterator.from($0.iterator)",
    // scala.collection.Iterator -> JavaIterator: wrap with JavaIterator.from.
    // Retarget target's .iterator returns scala.collection.Iterator; the method declares JavaIterator.
    ("scala.collection.Iterator", "balticporter.runtime.JavaIterator") ->
      "balticporter.runtime.JavaIterator.from($0)",
    // DynamicArray[Char] -> CharSequence: build a String from the backing char array.
    ("lowlevel.util.DynamicArray", "java.lang.CharSequence") ->
      "new java.lang.String($0.toArray)",
    // DynamicArray -> scala.Array: extract a sized copy via toArray.
    ("lowlevel.util.DynamicArray", "scala.Array") ->
      "$0.toArray",
    // scala.Array -> DynamicArray: wrap the array (zero-copy) into a DynamicArray.
    ("scala.Array", "lowlevel.util.DynamicArray") ->
      "lowlevel.util.DynamicArray.wrap($0)",
  )

  /** the `gdx/src` pipeline. Universal phases first, then the three §1(b) phases configured above,
    * then the one §1(c) rule libGDX plugs in from OUTSIDE the engine
    * ([[GdxSharedIteratorRule]]). */
  def mainPhases: List[balticporter.tir.Phase] =
    List(beanProperties, nullaryArity,
         new CollectionsTransform(retarget = comparatorRetarget ++ bitsRetarget ++ libCollectionRetargets,
                                  retargetRewrites = bitsRetargetRewrites ++ libCollectionConstructRewrites,
                                  retargetRewritesByDesc = libCollectionConstructRewritesByDesc,
                                  retargetTypeArgs = libCollectionRetargetTypeArgs,
                                  retargetCoercions = libRetargetCoercions), new MutableParamsTransform,
         new PanamaFfiTransform(), unwrapReflection, classTable, new GdxSharedIteratorRule,
         memberRenames, disposableRedirect, textureHandle, align, uniformLocation,
         nullability, globalsToContext,
         new balticporter.transform.MethodBodyTransform(Map(
           "com.badlogic.gdx.assets.AssetManager#clear" ->
             """{
               |  this.synchronized {
               |    this.loadQueue.clear()
               |  }
               |  this.finishLoading()
               |  this.synchronized {
               |    val dependencyCount = scala.collection.mutable.HashMap[java.lang.String, scala.Int]()
               |    while (this.assetTypes.size > 0) {
               |      dependencyCount.clear()
               |      val assetNames = lowlevel.util.DynamicArray[java.lang.String]()
               |      this.assetTypes.foreachKey(assetNames.add)
               |      assetNames.foreach { asset =>
               |        this.assetDependencies.get(asset).foreach { dependencies =>
               |          dependencies.foreach { dependency =>
               |            dependencyCount(dependency) = dependencyCount.getOrElse(dependency, 0) + 1
               |          }
               |        }
               |      }
               |      assetNames.foreach { asset =>
               |        if (dependencyCount.getOrElse(asset, 0) == 0) this.unload(asset)
               |      }
               |    }
               |    this.assets.clear(51)
               |    this.assetTypes.clear(51)
               |    this.assetDependencies.clear(51)
               |    this.loaded = 0
               |    this.toLoad = 0
               |    this.peakTasks = 0
               |    this.loadQueue.clear()
               |    this.tasks.clear()
               |  }
               |}""".stripMargin,
           // wave 3.1m: AssetManager.getAssetFileName — bare-map iteration with return inside a
           // retargetForEach lambda. sge: nested foreachEntry with boundary.break.
           "com.badlogic.gdx.assets.AssetManager#getAssetFileName" ->
             """{
               |  scala.util.boundary[java.lang.String] { (retFe: scala.util.boundary.Label[java.lang.String]) ?=>
               |    this.assets.foreachEntry((assetType: java.lang.Class[?], assetsByType: lowlevel.util.ObjectMap[java.lang.String, sge.assets.AssetManager.RefCountedContainer]) => {
               |      assetsByType.foreachEntry((fileName: java.lang.String, refCounted: sge.assets.AssetManager.RefCountedContainer) => {
               |        val obj: java.lang.Object = refCounted.`object`
               |        if ((obj.asInstanceOf[scala.AnyRef] eq asset) || asset.equals(obj)) {
               |          scala.util.boundary.break(fileName)(using retFe)
               |        } else ()
               |      })
               |    })
               |    return null
               |  }
               |}""".stripMargin,
           // wave 3.1m: FirstPersonCameraController.keyUp — IntIntMap.remove(key, defaultValue)
           // becomes ObjectMap.remove(key), dropping the unused default. sge: keys.remove(keycode).
           "com.badlogic.gdx.graphics.g3d.utils.FirstPersonCameraController#keyUp(int)" ->
             """{
               |  this.keys.remove(keycode)
               |  return true
               |}""".stripMargin,
           // wave 3.1m: ModelLoader.getDependencies — Tuple2 default-construct then assign _1/_2.
           // sge: val item = (fileName, d). The method is large; replace only relevant lines.
           "com.badlogic.gdx.assets.loaders.ModelLoader#getDependencies" ->
             """{
               |  val deps: lowlevel.util.DynamicArray[sge.assets.AssetDescriptor[?]] = lowlevel.util.DynamicArray[sge.assets.AssetDescriptor[?]]()
               |  val data: sge.graphics.g3d.model.data.ModelData = this.loadModelData(file, parameters)
               |  if (data == null) {
               |    return deps
               |  } else ()
               |  val item: scala.Tuple2[java.lang.String, sge.graphics.g3d.model.data.ModelData] = (fileName, data)
               |  this.items.synchronized {
               |    this.items.add(item)
               |  }
               |  val textureParameter: sge.assets.loaders.TextureLoader.TextureParameter = if (parameters != null) parameters.textureParameter else this.defaultParameters.textureParameter
               |  for (modelMaterial <- data.materials) {
               |    if (modelMaterial.textures != null) {
               |      for (modelTexture <- modelMaterial.textures) {
               |        deps.add(new sge.assets.AssetDescriptor(modelTexture.fileName, classOf[sge.graphics.Texture], textureParameter))
               |      }
               |    } else ()
               |  }
               |  return deps
               |}""".stripMargin,
           // wave 3.1m: ParticleEffectLoader.getDependencies — Tuple2 default-construct then
           // assign _1/_2. Same pattern as ModelLoader. Construct the tuple at once.
           "com.badlogic.gdx.graphics.g3d.particles.ParticleEffectLoader#getDependencies" ->
             """{
               |  val json: sge.utils.Json = new sge.utils.Json()
               |  val data: sge.graphics.g3d.particles.ResourceData[sge.graphics.g3d.particles.ParticleEffect] = json.fromJson(classOf[sge.graphics.g3d.particles.ResourceData[?]], file).asInstanceOf[sge.graphics.g3d.particles.ResourceData[sge.graphics.g3d.particles.ParticleEffect]]
               |  var assets: lowlevel.util.DynamicArray[sge.graphics.g3d.particles.ResourceData.AssetData[?]] = null
               |  this.items.synchronized {
               |    val entry: scala.Tuple2[java.lang.String, sge.graphics.g3d.particles.ResourceData[sge.graphics.g3d.particles.ParticleEffect]] = (fileName, data)
               |    this.items.add(entry)
               |    assets = data.assets.asInstanceOf[lowlevel.util.DynamicArray[sge.graphics.g3d.particles.ResourceData.AssetData[?]]]
               |  }
               |  val descriptors: lowlevel.util.DynamicArray[sge.assets.AssetDescriptor[?]] = lowlevel.util.DynamicArray[sge.assets.AssetDescriptor[?]]().asInstanceOf[lowlevel.util.DynamicArray[sge.assets.AssetDescriptor[?]]]
               |  for (assetData <- assets) {
               |    if (!this.resolve(assetData.filename).exists) {
               |      assetData.filename = file.parent().child(scala.Predef.summon[sge.Sge].files.internal(assetData.filename).name).path()
               |    } else ()
               |    if (assetData.asInstanceOf[sge.graphics.g3d.particles.ResourceData.AssetData[java.lang.Object]].`type` eq classOf[sge.graphics.g3d.particles.ParticleEffect]) {
               |      descriptors.add(new sge.assets.AssetDescriptor(assetData.filename, assetData.asInstanceOf[sge.graphics.g3d.particles.ResourceData.AssetData[java.lang.Object]].`type`.asInstanceOf[java.lang.Class[sge.graphics.g3d.particles.ParticleEffect]], parameter))
               |    } else {
               |      descriptors.add(new sge.assets.AssetDescriptor(assetData.filename, assetData.asInstanceOf[sge.graphics.g3d.particles.ResourceData.AssetData[java.lang.Object]].`type`))
               |    }
               |  }
               |  return descriptors.asInstanceOf[lowlevel.util.DynamicArray[sge.assets.AssetDescriptor[?]]]
               |}""".stripMargin,
           // wave 3.1m: Node.calculateBoneTransforms — keys$field(i) -> getKeyAt(i),
           // values$field(i) -> getValueAt(i). sge: getKeyAt(i) / getValueAt(i).
           "com.badlogic.gdx.graphics.g3d.model.Node#calculateBoneTransforms(boolean)" ->
             """{
               |  for (part <- this.parts) scala.util.boundary { {
               |    if (((part.invBoneBindTransforms == null) || (part.bones == null)) || (part.invBoneBindTransforms.size != part.bones.length)) {
               |      scala.util.boundary.break(())
               |    } else ()
               |    val n: scala.Int = part.invBoneBindTransforms.size;
               |    { var i: scala.Int = 0; while (i < n) { {
               |      part.bones(i).set(part.invBoneBindTransforms.getKeyAt(i).globalTransform).mul(part.invBoneBindTransforms.getValueAt(i))
               |    }; i = i + 1 } }
               |  } }
               |  if (recursive) {
               |    for (child <- this.children$field) {
               |      child.calculateBoneTransforms(true)
               |    }
               |  } else ()
               |}""".stripMargin,
           // wave 3.1m: ModelInstance.invalidate(Node) — keys$field(j) -> getKeyAt(j),
           // setKeyAt(j, v) for the write case. sge: getKeyAt(j) / setKeyAt(j, severed).
           "com.badlogic.gdx.graphics.g3d.ModelInstance#invalidate(Node)" ->
             """{
               |  for (part <- node.parts) {
               |    val bindPose: lowlevel.util.ArrayMap[sge.graphics.g3d.model.Node, sge.math.Matrix4] = part.invBoneBindTransforms
               |    if (bindPose != null) {
               |      { var j: scala.Int = 0; while (j < bindPose.size) { {
               |        bindPose.setKeyAt(j, this.getNode(bindPose.getKeyAt(j).id))
               |      }; j = j + 1 } }
               |    } else ()
               |    if (!this.materials.containsByRef(part.material)) {
               |      val midx: scala.Int = this.materials.indexOf(part.material)
               |      if (midx < 0) {
               |        this.materials.add({
               |          part.material = part.material.copy()
               |          part.material
               |        })
               |      } else {
               |        part.material = this.materials(midx)
               |      }
               |    } else ()
               |  }
               |  for (child <- node.children$field) {
               |    this.invalidate(child)
               |  }
               |}""".stripMargin,
           // wave 3.1m: NodePart.set — putAll with wildcard cast on invariant lls ArrayMap.
           // sge: map.putAll(otherBindTransforms) with no cast. collection-internal seam — java's
           // covariant putAll formal has no image on the invariant lls type.
           "com.badlogic.gdx.graphics.g3d.model.NodePart#set(NodePart)" ->
             """{
               |  this.meshPart.set(other.meshPart)
               |  this.material = other.material
               |  if (other.invBoneBindTransforms == null) {
               |    this.invBoneBindTransforms = null
               |    this.bones = null
               |  } else {
               |    if (this.invBoneBindTransforms == null) {
               |      this.invBoneBindTransforms = lowlevel.util.ArrayMap.apply(true, other.invBoneBindTransforms.size)
               |    } else {
               |      this.invBoneBindTransforms.clear()
               |    }
               |    this.invBoneBindTransforms.putAll(other.invBoneBindTransforms)
               |    if ((this.bones == null) || (this.bones.length != this.invBoneBindTransforms.size)) {
               |      this.bones = new scala.Array[sge.math.Matrix4](this.invBoneBindTransforms.size)
               |    } else ();
               |    { var i: scala.Int = 0; while (i < this.bones.length) { {
               |      if (this.bones(i) == null) {
               |        this.bones(i) = new sge.math.Matrix4()
               |      } else ()
               |    }; i = i + 1 } }
               |  }
               |  return this
               |}""".stripMargin,
           // wave 3.1m: MapProperties.putAll — same wildcard cast as NodePart.set.
           // sge: this.properties.putAll(properties.properties) with no cast.
           "com.badlogic.gdx.maps.MapProperties#putAll(MapProperties)" ->
             """{
               |  this.properties.putAll(properties.properties)
               |}""".stripMargin,
           // wave 3.1m: Selection.iterator — Chain produces Iterator[T] but return type is
           // JavaIterator[T]. Wrap with JavaIterator.from until the Array retarget wave aligns types.
           "com.badlogic.gdx.scenes.scene2d.utils.Selection#iterator" ->
             """{
               |  return balticporter.runtime.JavaIterator.from(this.selected.orderedItems.iterator)
               |}""".stripMargin,
           // wave 3.1m: Selection.toArray() — Chain produces Iterator whose toArray needs ClassTag.
           // Collect from the OrderedSet directly into an sge.utils.Array. sge: selected.foreach(result.add).
           // sge: `val result = DynamicArray.createRef[T](); selected.foreach(result.add); result`
           // createRef provides `given MkArray[A] = MkArray.anyRef.asInstanceOf[MkArray[A]]` locally
           "com.badlogic.gdx.scenes.scene2d.utils.Selection#toArray" ->
             """{
               |  val result: lowlevel.util.DynamicArray[T] = {
               |    @scala.annotation.nowarn("msg=unused local definition")
               |    given lowlevel.MkArray[T] = lowlevel.MkArray.anyRef[AnyRef].asInstanceOf[lowlevel.MkArray[T]]
               |    lowlevel.util.DynamicArray[T]()
               |  }
               |  this.selected.foreach(result.add)
               |  return result
               |}""".stripMargin,
           // wave 3.1m: Selection.toArray(Array<T>) — same pattern, collect into the provided array.
           "com.badlogic.gdx.scenes.scene2d.utils.Selection#toArray(Array)" ->
             """{
               |  this.selected.foreach(array.add)
               |  return array
               |}""".stripMargin,
           // wave 3.1m: ArraySelection.validate — Chain iterator returns Iterator[T], but the loop
           // body calls iter.remove(). sge: collect removals into a DynamicArray, then remove.
           "com.badlogic.gdx.scenes.scene2d.utils.ArraySelection#validate" ->
             """{
               |  val array: lowlevel.util.DynamicArray[T] = this.array
               |  if (array.size == 0) {
               |    this.clear()
               |    return
               |  } else ()
               |  var changed: scala.Boolean = false
               |  val toRemove: lowlevel.util.DynamicArray[T] = {
               |    @scala.annotation.nowarn("msg=unused local definition")
               |    given lowlevel.MkArray[T] = lowlevel.MkArray.anyRef[AnyRef].asInstanceOf[lowlevel.MkArray[T]]
               |    lowlevel.util.DynamicArray[T]()
               |  }
               |  val iter = this.items.orderedItems.iterator
               |  while (iter.hasNext) {
               |    val selected: T = iter.next().asInstanceOf[T]
               |    if (!array.contains(selected)) {
               |      toRemove.add(selected)
               |      changed = true
               |    } else ()
               |  }
               |  toRemove.foreach(this.selected.remove)
               |  if (this.required && (this.selected.size == 0)) {
               |    this.set(array.first)
               |  } else {
               |    if (changed) {
               |      this.changed()
               |    } else ()
               |  }
               |}""".stripMargin,
           // wave 3.1m: SelectBox.selectedIndex — OrderedSet vs ObjectSet (broken subtyping edge).
           // sge: val sel = selection.items (inferred OrderedSet). Fix: widen the type annotation
           // from ObjectSet to OrderedSet. collection-internal seam — java's OrderedSet <: ObjectSet
           // has no image in lls.
           "com.badlogic.gdx.scenes.scene2d.ui.SelectBox#getSelectedIndex" ->
             """{
               |  val selected: lowlevel.util.OrderedSet[T] = this.selection$field.items
               |  return if (selected.size == 0) -1 else this.items$field.indexOf(selected.first)
               |}""".stripMargin,
           // wave 3.1m: SgeList.selectedIndex — same OrderedSet vs ObjectSet pattern.
           "com.badlogic.gdx.scenes.scene2d.ui.List#getSelectedIndex" ->
             """{
               |  val selected: lowlevel.util.OrderedSet[T] = this.selection$field.items
               |  return if (selected.size == 0) -1 else this.items$field.indexOf(selected.first)
               |}""".stripMargin,
           // wave 3.1t: BitmapFont secondary ctor — `Array.with(arr)` is a static call on the
           // retarget source's companion, which the retarget mechanism cannot rewrite (it handles
           // INSTANCE calls). Replace with `DynamicArray.wrap(arr)` which zero-copy wraps the array.
           "com.badlogic.gdx.graphics.g2d.BitmapFont#<init>(BitmapFontData,TextureRegion,boolean)" ->
             """{
               |  this(data, if (region != null) lowlevel.util.DynamicArray.wrap(scala.Array[sge.graphics.g2d.TextureRegion](region)) else null.asInstanceOf[lowlevel.util.DynamicArray[sge.graphics.g2d.TextureRegion]], integer)
               |}""".stripMargin,
           // wave 3.1t: removeDuplicates — java set/restore preserveOrder which is a val in
           // DynamicArray (immutable constructor parameter). DynamicArray.removeIndex always
           // preserves order (unlike gdx Array which optionally swaps the last element in),
           // so setting preserveOrder to true is unnecessary. Drop both writes.
           "com.badlogic.gdx.assets.AssetLoadingTask#removeDuplicates" ->
             """{
               |  { var i: scala.Int = 0; while (i < array.size) { {
               |    val fn: java.lang.String = array.apply(i).fileName
               |    val `type`: java.lang.Class[?] = array.apply(i).asInstanceOf[sge.assets.AssetDescriptor[java.lang.Object]].`type`.asInstanceOf[java.lang.Class[?]];
               |    { var j: scala.Int = array.size - 1; while (j > i) { {
               |      if ((`type` eq array.apply(j).asInstanceOf[sge.assets.AssetDescriptor[java.lang.Object]].`type`) && fn.equals(array.apply(j).fileName)) {
               |        array.removeIndex(j)
               |      } else ()
               |    }; j = j - 1 } }
               |  }; i = i + 1 } }
               |}""".stripMargin,
           // wave 3.1v: MapLayers.getByType(Class) and MapObjects.getByType(Class) — `new Array<T>()`
           // at a METHOD-level type parameter T. MkArray[T] is not summonable inline here because
           // T <: MapLayer/MapObject and the inline given resolves only for T <: AnyRef directly.
           // Provide a local given as sge's `createRef` pattern does.
           // The 2-arg overload takes an existing array — no construction needed.
           "com.badlogic.gdx.maps.MapLayers#getByType(Class)" ->
             """{
               |  @scala.annotation.nowarn("msg=unused local definition")
               |  given lowlevel.MkArray[T] = lowlevel.MkArray.anyRef[AnyRef].asInstanceOf[lowlevel.MkArray[T]]
               |  return this.getByType(`type`, lowlevel.util.DynamicArray[T]())
               |}""".stripMargin,
           "com.badlogic.gdx.maps.MapObjects#getByType(Class)" ->
             """{
               |  @scala.annotation.nowarn("msg=unused local definition")
               |  given lowlevel.MkArray[T] = lowlevel.MkArray.anyRef[AnyRef].asInstanceOf[lowlevel.MkArray[T]]
               |  return this.getByType(`type`, lowlevel.util.DynamicArray[T]())
               |}""".stripMargin,
           // wave 3.1t: Actor.<clinit> — `new DynamicArray()` inside a lambda in the companion's
           // static initialiser. DynamicArray's constructor is private; must use the factory.
           "com.badlogic.gdx.scenes.scene2d.Actor#<clinit>" ->
             """{
               |  Actor.POOLS.addPool(classOf[sge.math.Rectangle], ((() => new sge.math.Rectangle()): sge.utils.DefaultPool.PoolSupplier[sge.math.Rectangle]))
               |  Actor.POOLS.addPool(classOf[lowlevel.util.DynamicArray[?]], ((() => lowlevel.util.DynamicArray[AnyRef]()): sge.utils.DefaultPool.PoolSupplier[lowlevel.util.DynamicArray[?]]))
               |  Actor.POOLS.addPool(classOf[sge.graphics.g2d.GlyphLayout], ((() => new sge.graphics.g2d.GlyphLayout()): sge.utils.DefaultPool.PoolSupplier[sge.graphics.g2d.GlyphLayout]))
               |  Actor.POOLS.addPool(classOf[sge.scenes.scene2d.utils.ChangeListener.ChangeEvent], ((() => new sge.scenes.scene2d.utils.ChangeListener.ChangeEvent()): sge.utils.DefaultPool.PoolSupplier[sge.scenes.scene2d.utils.ChangeListener.ChangeEvent]))
               |}""".stripMargin
         ))
         // SuppressionPhase is now derived unconditionally by PortRun (§1(a) universal, no-op
         // when no `.orNull` symbols exist) — removed from surface, no port needs to declare it.
         )

  /** Drop `()` from nullary getter-like methods — sge's empirical convention, no written rule in
    * `conversion-rules.md`. Enabled with `Everywhere()` because the convention is whole-library:
    * the sge hand port strips parens from EVERY getter-like method, not from a named list.
    *
    * AFTER `bean-properties`, which has already claimed its own getters and dropped their parens.
    * The `runsAfter` edge is on the phase; the list position is the pipeline's contract.
    */
  def nullaryArity: balticporter.transform.NullaryArityTransform =
    // Drop `()` from every getter-like nullary method in scope. Dependents follow the base's
    // published shape (form=parenless in the port map) through PortMapTransform.followMemberRenames.
    // Measured with the bean switch: same six-port table (wave 1.2h).
    new balticporter.transform.NullaryArityTransform(scope = balticporter.tir.RuleScope.Everywhere())

  /** EMPTY, AND IT IS THE POSITION THAT IS THE POLICY — libGDX renames none of its own members.
    *
    * `disposableRedirect` below renames `dispose -> close` across the whole override component of
    * `com.badlogic.gdx.utils.Disposable`, and `java.lang.AutoCloseable#close` is not a name anything
    * may negotiate. Every DEPENDENT that declares a `Disposable` implementor which already has a
    * `close()` of its own therefore inherits a collision this base created, and
    * `MemberRenamer.OnCollision.Refuse` refuses the component whole — correctly, since which of two
    * members keeps a name is not the engine's to invent (`ENGINE-LIMITS.md` D13). The dependent's
    * answer is a `member-rename` entry moving ITS OWN member out of the way, and such an entry has
    * to run BEFORE the redirect.
    *
    * A dependent cannot put a phase early in a pipeline it did not write: an unmerged dependent
    * phase lands at the END of the effective surface, and a `runsBefore` edge from there POSTPONES
    * the phase it names past everything declared in between — measured on `sge-visui` as
    * `type-redirect` moving past `globals->implicits`, `context-seam 42 -> 41`, at 0 emitted bytes
    * (`Pipeline.order`'s own recorded failure shape). What CAN place it is the merge: `SurfaceFold`
    * puts a merged phase at the BASE's position, so this empty instance IS the position, and a
    * dependent's table merges into it. `MemberRenameTransform` with no entries is a structural
    * no-op — §1(b)'s "turned off needs no code path" — so this costs every other port exactly one
    * fingerprint field and nothing else.
    */
  def memberRenames: balticporter.transform.MemberRenameTransform =
    new balticporter.transform.MemberRenameTransform(
      // ---- wave 1.3: member renames from the `Migration notes: Renames:` census ----
      // Each entry is traceable to sge's documented Renames: line on the type's source file.
      // The key is the upstream FQN#member in the UPSTREAM namespace (§4.56).
      renames = Map(
        // `type` is a Scala reserved word; sge renamed the field to `eventType` (1 sge file).
        // The `getType`/`setType` bean pair is handled separately by `beanProperties`.
        "com.badlogic.gdx.scenes.scene2d.InputEvent#type" -> "eventType",
        // `toString(T)` clashes with `Any.toString()` at the same name; sge renamed it to
        // `itemToString` (1 sge file). REFUSED (policy 3 -> 4): the override component reaches
        // `java.lang.Object#toString`, which the program cannot move. The rename is declared so
        // `api-parity` can trace the intent; the refusal is a counted `policy` finding.
        "com.badlogic.gdx.scenes.scene2d.ui.List#toString(T)" -> "itemToString",
      ),
    )

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
    new balticporter.transform.GlobalsToImplicitsTransform(holders = List(
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
    ),
    // wave 3.1v: classes whose retarget constructions need `MkArray[T]` in scope.
    // sge's Octree uses ClassTag (different retarget target); the port's retarget to lls types
    // requires MkArray, which is the right bound for the lls factory's inline summon.
    // BufferedParticleBatch is abstract; the clause propagates to subclass constructors.
    requiredGivens = Map(
      "com.badlogic.gdx.math.Octree" -> "lowlevel.MkArray",
      "com.badlogic.gdx.math.BSpline" -> "lowlevel.MkArray",
      "com.badlogic.gdx.math.Bezier" -> "lowlevel.MkArray",
      "com.badlogic.gdx.utils.FlushablePool" -> "lowlevel.MkArray",
      "com.badlogic.gdx.graphics.g3d.particles.batches.BufferedParticleBatch" -> "lowlevel.MkArray",
      "com.badlogic.gdx.graphics.glutils.GLFrameBuffer" -> "lowlevel.MkArray",
    ))

  /** libGDX's GL texture handle — the `int` that is really a texture name — as an opaque type, which
    * is what the reference hand port declares (`sge/graphics/GLHandle.scala`) and APPLIES to a ported
    * declaration (`GLTexture.scala:42`).
    *
    * ==Why ONE family and not the eight sge declares (§1c, and it is a MEASUREMENT)==
    * `TextureHandle` is the only one of sge's handle types CONFIGURED here, and the census behind
    * that has been corrected once — read PROGRESS §11.25's table, not this list, and note which rows
    * it retracts. `ProgramHandle`/`ShaderHandle` (`ShaderProgram` keeps `private var program: Int`),
    * `FramebufferHandle`/`RenderbufferHandle` (`GLFrameBuffer` keeps `Int`) and `BufferHandle` are a
    * typed layer offered to CONSUMERS beside the raw one — their home is `GLHandleOps`, extension
    * methods on `GL20`, and `GL20.scala:89` keeps `def glGenTexture(): Int` to prove it.
    * Configuring those five would emit a surface the reference port deliberately does not have.
    * `UniformLocation` is NOT one of them (it types 32 positions in `ShaderProgram`/`BaseShader`, all
    * ported declarations) — now configured as [[uniformLocation]], wave 2.7. `GLEnum` is a third
    * shape again:
    * sge types `GL20`/`GL30` FORMALS with 15 families and MINTS a named vocabulary for them, which
    * this mechanism cannot do — `ENGINE-LIMITS.md` §13 O7, an open (b) with an exit criterion.
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
      hints      = Set("com.badlogic.gdx.graphics.GLTexture#glHandle"),
      underlying = balticporter.tir.OpaqueSpec.Primitive.Int,
      scope      = balticporter.tir.RuleScope.Everywhere(except = Set(
        "com.badlogic.gdx.graphics.GL20", "com.badlogic.gdx.graphics.GL30",
        "com.badlogic.gdx.graphics.GL31", "com.badlogic.gdx.graphics.GL32")),
    ))

  /** libGDX's `Align` — a class of `static final int` constants — as an opaque type.
    *
    * ==O6 CLOSED: retype against an EXISTING/injected type==
    * `com.badlogic.gdx.utils.Align` is a java class whose entire body is
    * `static public final int center = 1 << 0; …`. sge's `sge.utils.Align` is
    * `opaque type Align = Int` plus extension methods, and every ported declaration that java
    * typed `int align` is typed `Align`.
    *
    * Two mechanisms, one seam each, no new one:
    *   - the DEFINITION: `Substitutions.dropTypes` drops the java class, `inject` supplies the
    *     hand-written `Align.scala` (copied from sge's own file, stripped of sge-specific imports).
    *   - the RETYPE: `PrimitiveToOpaqueTransform(OpaqueSpec(target = Existing(…)))` seeds from the
    *     align-typed FIELDS, propagates to their getters/setters/parameters, and coerces at every
    *     boundary through `Align(rawInt)` / `Align.toInt(value)`.
    *
    * ==CENSUS: 13 field hints, propagation discovers the rest==
    * Each field hint is the fully-qualified name of a `private int align`-typed field. The flow
    * propagation grows the seed set to every getter/setter/parameter reachable by a pure-move flow
    * from these fields. The METHODS (`setAlign(int)`, `getX(int alignment)`, etc.) and their
    * PARAMETERS are discovered, not listed.
    *
    * ==Shared surface, composed via `MergeablePolicy` (§1.5)==
    * Inherited through `extendedBy`. A dependent that needs to seed ADDITIONAL declarations (ones
    * propagation cannot reach from the base's field hints) constructs its own instance with the same
    * `fqn`/`target`/`underlying` and its own `hints`; `surfaceFold` merges the two by union. gdx-vfx
    * is the first (4 parameters whose only connection to the family is bitwise ops against `Align`
    * constants). */
  def align: balticporter.transform.PrimitiveToOpaqueTransform =
    new balticporter.transform.PrimitiveToOpaqueTransform(balticporter.tir.OpaqueSpec(
      fqn        = "com.badlogic.gdx.utils.Align",
      target     = balticporter.tir.OpaqueSpec.Target.Existing(
        typeFqn    = "sge.utils.Align",
        wrapName   = "apply",
        unwrapName = "toInt",
      ),
      hints      = Set(
        // 13 fields typed `int align` / `int alignment` / `int columnAlign` / `int rowAlign` /
        // `int labelAlign` / `int lineAlign` across the scene2d UI types. Each is a seed; the
        // propagation discovers every getter, setter, and parameter reachable from them.
        "com.badlogic.gdx.scenes.scene2d.ui.Image#align",
        "com.badlogic.gdx.scenes.scene2d.ui.Label#labelAlign",
        "com.badlogic.gdx.scenes.scene2d.ui.Label#lineAlign",
        "com.badlogic.gdx.scenes.scene2d.ui.List#alignment",
        "com.badlogic.gdx.scenes.scene2d.ui.VerticalGroup#align",
        "com.badlogic.gdx.scenes.scene2d.ui.VerticalGroup#columnAlign",
        "com.badlogic.gdx.scenes.scene2d.ui.Table#align",
        "com.badlogic.gdx.scenes.scene2d.ui.SelectBox#alignment",
        "com.badlogic.gdx.scenes.scene2d.ui.Container#align",
        "com.badlogic.gdx.scenes.scene2d.ui.HorizontalGroup#align",
        "com.badlogic.gdx.scenes.scene2d.ui.HorizontalGroup#rowAlign",
        "com.badlogic.gdx.scenes.scene2d.utils.TiledDrawable#align",
        "com.badlogic.gdx.scenes.scene2d.actions.MoveToAction#alignment",
      ),
      underlying = balticporter.tir.OpaqueSpec.Primitive.Int,
    ))

  /** GL uniform locations — the `int` that is really a distinct domain value, given a real type.
    *
    * ==Census from sge (CLAUDE.md §3.5)==
    * `sge.graphics.GLHandle.scala:96` declares `opaque type UniformLocation = Int` with `apply`,
    * `toInt`, `notFound = -1`, and arithmetic extensions (`+`, `-`, `>=`, `<`). `ShaderProgram`
    * stores them as `MutableMap[String, UniformLocation]` (java: `ObjectIntMap<String> uniforms`)
    * and every `setUniform*` overload that takes a location by `int` takes it by `UniformLocation`.
    * `BaseShader.locations` is `Array[UniformLocation]` (java: `int locations[]`).
    *
    * ==Existing target, following the Align pattern==
    * There is no java class `UniformLocation`, so nothing is dropped. The opaque type is injected
    * as `sge.graphics.UniformLocation` under `libgdx-overrides/` and the OpaqueSpec uses
    * `Target.Existing(typeFqn = "sge.graphics.UniformLocation", …)` to retype against it. The
    * injected file carries the comparison extensions (`>=`, `<`, `+`, `-`) that sge declares.
    *
    * ==SEED: `fetchUniformLocation` — the return value seeds, propagation discovers the rest==
    * The one method that PRODUCES uniform locations. Both overloads are overloaded, so the fullName
    * includes the descriptor. `BaseShader#locations` is a second seed (`int[]`, O3).
    *
    * ==FENCE: same as textureHandle==
    * The GL interfaces are scoped out, preventing propagation into `GL20#glGetUniformLocation`. */
  def uniformLocation: balticporter.transform.PrimitiveToOpaqueTransform =
    new balticporter.transform.PrimitiveToOpaqueTransform(balticporter.tir.OpaqueSpec(
      fqn        = "com.badlogic.gdx.graphics.UniformLocation",
      target     = balticporter.tir.OpaqueSpec.Target.Existing(
        typeFqn    = "sge.graphics.UniformLocation",
        wrapName   = "apply",
        unwrapName = "toInt",
      ),
      hints      = Set(
        "com.badlogic.gdx.graphics.glutils.ShaderProgram#fetchUniformLocation(String)",
        "com.badlogic.gdx.graphics.g3d.shaders.BaseShader#locations",
      ),
      underlying = balticporter.tir.OpaqueSpec.Primitive.Int,
      scope      = balticporter.tir.RuleScope.Everywhere(except = Set(
        "com.badlogic.gdx.graphics.GL20", "com.badlogic.gdx.graphics.GL30",
        "com.badlogic.gdx.graphics.GL31", "com.badlogic.gdx.graphics.GL32")),
    ))

  /** libGDX's own `@Null` moved OUT of an annotation the Scala compiler ignores and INTO the type
    * — `lowlevel.Nullable[T]`, the hand port's own wrapper (DESIGN.md §8.6's N1).
    *
    * ==Why this is the base manifest's business (§1.5)==
    * A nullable return is a fact about the SHARED SURFACE. A base emitting `Nullable[Actor]` and a
    * dependent emitting `Actor` for the same member each compile alone and cannot compile together,
    * so the entry lives here once and every dependent inherits it through `extendedBy`. No dependent
    * CONSTRUCTS a `nullability` of its own, so there is one instance in every effective pipeline and
    * nothing has to merge.
    *
    * ==`Named` CLOSES K13==
    * The union floor `T | Null` is NOT transparent at an abstract `T` — measured at 35 errors from
    * 632 declarations, every one inside a generic container, and a scope exit list of 12 entries
    * maintained by hand. `Nullable[T]` IS a proper type that composes at every `T`: the
    * abstract-type-parameter class disappears entirely, and the scope exit list with it. The K13
    * exit that was the union floor's SECOND exit is this target's DEFAULT — no scope needed. */
  def nullability: balticporter.transform.NullabilityTransform =
    new balticporter.transform.NullabilityTransform(
      annotations = Set("com.badlogic.gdx.utils.Null"),
      target      = balticporter.transform.NullabilityTransform.Target.Named("lowlevel.Nullable"),
      scope       = balticporter.tir.RuleScope.Everywhere(nullabilityErasureExempt),
      // K13.6: `IntMap.get(int)` returns `V` WITHOUT `@Null` but CAN return null (the body says
      // `return null`). After the retarget, lls's `ObjectMap.get(K)` returns `Nullable[V]`, so
      // scalac sees `Nullable.Impl[V]` and the caller's `.beforeGroup()` is not a member. Adding
      // the method to `nullableMembers` makes the plan include it, wraps the result, and the
      // existing `transformSelect` unwrapping (`.get` on the Nullable) fires automatically.
      nullableMembers = Set(
        "com.badlogic.gdx.utils.IntMap#get",
      ),
    )

  /** Types whose `@Null`-annotated overload sets create ERASURE CONFLICTS under Named mode.
    *
    * `Nullable[A]` erases to `Object` (the opaque type's underlying is `A | NestedNone`), so
    * `f(Nullable[String])` and `f(Nullable[Object])` share an erasure. In Union mode
    * `String | Null` erases to `String` and `Object | Null` to `Object` — distinct. With Named,
    * every `@Null`-annotated overload of the same arity erases to the same descriptor.
    *
    * The scope holds back these types' `@Null` declarations: they keep their upstream types and
    * markers. Each is a COUNTED `ScopedOut` decision with a porter note.
    *
    * CharArray: 10 `append` overloads, all `@Null` on a different reference type, all erasing to
    * `append(Object, …)`.
    * Image: 3 constructors taking `@Null NinePatch`, `@Null TextureRegion`, `@Null Drawable`,
    * all erasing to `<init>(Object, Sge)`. */
  def nullabilityErasureExempt: Set[String] = Set(
    "com.badlogic.gdx.utils.CharArray",
    "com.badlogic.gdx.scenes.scene2d.ui.Image",
    "com.badlogic.gdx.utils.Json",
    "com.badlogic.gdx.scenes.scene2d.actions.TemporalAction",
    "com.badlogic.gdx.utils.Pools",
  )

  // K13 CLOSED: the `nullabilityExempt` set was the K13 exit for the `Union` floor — with
  // `Named("lowlevel.Nullable")`, `Nullable[T]` IS a proper type that composes at every `T`, so
  // the abstract-type-parameter class disappears entirely. The 12 scope entries that held back 92
  // of 632 declarations to clear 35 errors are deleted, not just emptied — the `Group#findActor`
  // member key with them. Every generic container and generic widget is now retyped unscopped.
