package balticporter.corpus.ashley

import balticporter.core.{FrontendConfig, ParityRef, PortManifest, Provenance, RuntimeMode}
import balticporter.corpus.libgdx.LibgdxPolicy
import balticporter.runner.{Determinism, PortRun, SourceSet, VendoredCommit}

import java.nio.file.{Files, Path}
import scala.jdk.CollectionConverters.*

/** Migrate **Ashley** (`ashley/src`, 21 types — libGDX's entity-component-system) through the TIR.
  *
  *   corpus/runMain balticporter.corpus.ashley.AshleyMigrate [--determinism=full]
  *
  * ==Why Ashley is the second corpus library==
  * It is the smallest library that is a genuine DEPENDENT port. Every one of its 21 files resolves
  * against libGDX core — `Array`, `ObjectMap`, `Pool`, `Bits`, `SnapshotArray`, `ObjectSet` — so it
  * exercises the thing a single-module port cannot: agreeing with a base module's emitted surface
  * while parsing only the base's *Java*. `ManifestAgreement` is what makes that agreement checked
  * rather than hoped for, and this is the first library to test it outside libGDX's own two source
  * sets.
  *
  * It also lands on the base's substitutions immediately, which is the interesting part. Ashley
  * imports three types `sge` deliberately does NOT translate:
  *
  *   - `utils.reflect.ClassReflection` + `ReflectionException` — `Engine.java:69` calls
  *     `ClassReflection.newInstance(componentType)` to fabricate a component from its `Class`.
  *   - `utils.ReflectionPool` — `PooledEngine.java:141-167` keys a pool per component `Class` and
  *     builds it reflectively.
  *
  * Both are the one thing Scala.js and Scala Native cannot do, which is why the base port dropped
  * them. The reference hand-port (`../sge/sge-extension/ecs`) SOLVED rather than skipped this:
  * `PooledEngine.scala:11` records "factory registry approach instead of ReflectionPool for
  * components", with component factories registered through `Engine.registerComponentFactory`.
  * That is the same shape libGDX core's own substitution already takes — its injected `Pools`
  * takes a factory, and `ReflectionPool` is dropped outright in favour of the factory-backed
  * `DefaultPool` that ports mechanically (CLAUDE.md §3.5: consult the reference port, and record
  * whether it SOLVED or SKIPPED).
  *
  * ==Scope==
  * `ashley/src` only. Deliberately excluded, and named rather than silently dropped:
  *
  *   - `benchmarks/` (21 files) — an Artemis-vs-Ashley harness that depends on a THIRD ECS library.
  *     Not library surface.
  *   - `tests/src` (13 files) — demo applications driving a real libGDX window (`RenderSystemTest`
  *     and friends). They need a backend, and no backend is ported.
  *
  * `ashley/tests` (18 files, 118 `@Test`, 458 assertions) IS in scope and is ported by
  * [[AshleyTestMigrate]] as a dependent of this run — that suite is the only behavioural evidence
  * this port can have (CLAUDE.md §3).
  */
object AshleyMigrate:

  def main(args: Array[String]): Unit =
    val repoRoot = Path.of(sys.props.getOrElse("balticporter.root", ".")).toAbsolutePath.normalize
    val base     = repoRoot.resolve("../sge/original-src/ashley/ashley/src").normalize
    val gdxSrc   = repoRoot.resolve("../sge/original-src/libgdx/gdx/src").normalize

    val files = Files.walk(base).iterator().asScala
      .filter(p => p.toString.endsWith(".java"))
      .map(p => base.relativize(p).toString)
      .filterNot(f => f.endsWith("package-info.java") || f.endsWith("module-info.java"))
      .toList.sorted

    PortRun(
      label     = "sge-ecs",
      portRoot  = repoRoot.resolve("ported/sge-ecs"),
      sourceSet = SourceSet.Main,
      // libGDX core is a RESOLUTION root: parsed so every reference resolves, never emitted here.
      // `LibgdxCoreMigrate` emits it, and the two are compiled together.
      frontend  = FrontendConfig(base, files, Nil, resolutionRoots = List(gdxSrc)),
      phases    = Nil, // supplied by the manifest — the two sources are mutually exclusive
      manifest  = Some(AshleyPolicy.core(repoRoot)),
      provenance = Some(Provenance(
        upstreamName     = "Ashley",
        upstreamCommit   = VendoredCommit.of(base),
        originalLicense  = "Apache-2.0",
        sourcePathPrefix = "ashley/src",
        sourceRoot       = base.toString,
      )),
      // NOT `Vendored`: `LibgdxCoreMigrate` already vendors the collection shims into the module
      // this output is compiled beside. Vendoring again would define every support type twice —
      // which the JVM tolerates only while the copies agree, and the Scala.js/Native linkers reject.
      runtimeMode = RuntimeMode.Dependency,
      determinism = Determinism.fromArgs(args.toSeq),
      nextStep    = "just ashley-measure",
    ).execute()

/** Ashley's per-library policy — a DEPENDENT of libGDX core's.
  *
  * The base's `dropTypes`, `dropMethods`, `packageRenames` and signature-affecting phases are
  * INHERITED, not restated: they are facts about the surface Ashley compiles against, and a
  * dependent that re-declared them would be free to drift. What Ashley adds is its own namespace
  * claim and whatever its own 21 files need.
  *
  * `inject` is deliberately NOT inherited (see [[balticporter.core.PortManifest]]): a drop is an
  * observation about the shared API and binds every module that sees it, but exactly one module
  * ships each replacement file. libGDX core ships the replacements for the types it dropped.
  */
object AshleyPolicy:

  def core(repoRoot: Path): PortManifest =
    LibgdxPolicy.core(repoRoot).extendedBy(PortManifest(
      name    = "sge-ecs",
      governs = Set("com.badlogic.ashley"),
      // sge puts Ashley at `sge.ecs`, FLATTENING the `core` package away — its tree is
      // `sge/ecs/Engine.scala` with `signals`, `systems` and `utils` beside it. Two entries express
      // that under longest-prefix-wins: `…ashley.core` loses a segment, everything else keeps its
      // own. libGDX's `com.badlogic.gdx -> sge` is INHERITED from the base manifest, not restated.
      packageRenames = Map(
        "com.badlogic.ashley.core" -> "sge.ecs",
        "com.badlogic.ashley"      -> "sge.ecs",
      ),
      // Ashley's OWN replacements. `inject` is not inherited — exactly one module ships each
      // replacement file, and libGDX core ships the ones for the types IT dropped.
      inject  = List(repoRoot.resolve("balticporter/corpus/ashley-overrides")),
      surface = List(
        // ASHLEY'S OWN BEAN PROPERTY PAIRS — merged into the base's `bean-properties` phase through
        // `MergeablePolicy`, so the pair runs at the base's position in the pipeline (before every
        // retyping phase, which is where it reads java's own descriptor shapes). Each key names an
        // ashley declaration in the UPSTREAM namespace. The accessor pattern is java's own:
        // `getX()` -> property `x`, with the backing field as the trivial body.
        //
        // `EntitySystem#engine` is `def engine` (def-pair): the field is assigned at runtime by
        // `addedToEngineInternal`, so `val` would be wrong and `var` would publish a setter java
        // never had. The hand port writes `def engine: Nullable[Engine] = _engine`.
        //
        // The `getFamily()`/`getInterval()`/`getEntities()` families are here too. The hand port
        // RESTRUCTURED them into CONSTRUCTOR PARAMETERS (`val family: Family`), which the mechanical
        // port cannot reproduce — the constructor signature would change. Bean-property keeps the
        // declaration in the body as `def family` (the `def-pair` target), which does not match the
        // hand port's constructor val. These remain as `accessor` api-parity rows, classified as
        // `justified` (hand-port design decision: constructor promotion).
        //
        // `ComponentType#index` and `Family#index` are the same shape as the family entries: their
        // backing storage is constructor-assigned, so `MutableStorage` refuses `val` and `def-pair`
        // is the honest form.
        new balticporter.transform.BeanPropertyTransform(
          pairs = Map(
            // `EntitySystem#engine` is WITHHELD (ENGINE-LIMITS C16.1): the rename `getEngine ->
            // engine` makes a test method's local `engine` AMBIGUOUS with the member an anonymous
            // `EntitySystem` subclass inherits (CLAUDE.md §4.55, second rule). Wave 1.2b taught the
            // capture-rename pass to read a RESOLUTION-ROOT parent's direct members (spec-proven),
            // and the ashley lane still reads 2 × E049 at the real site — measured in the primary,
            // 2026-08-26 — so the pair stays off until that measurement reads 0. One api-parity
            // `accessor` row is the price.
            // "com.badlogic.ashley.core.EntitySystem#engine"             -> "getEngine",
            "com.badlogic.ashley.core.ComponentType#index"                -> "getIndex",
            "com.badlogic.ashley.core.Family#index"                      -> "getIndex",
            "com.badlogic.ashley.systems.IteratingSystem#family"          -> "getFamily",
            "com.badlogic.ashley.systems.IteratingSystem#entities"        -> "getEntities",
            "com.badlogic.ashley.systems.SortedIteratingSystem#family"    -> "getFamily",
            "com.badlogic.ashley.systems.SortedIteratingSystem#entities"  -> "getEntities",
            "com.badlogic.ashley.systems.IntervalIteratingSystem#family"  -> "getFamily",
            "com.badlogic.ashley.systems.IntervalIteratingSystem#entities" -> "getEntities",
            "com.badlogic.ashley.systems.IntervalSystem#interval"        -> "getInterval",
          ),
        ),
        // `PooledEngine.ComponentPools` uses `ReflectionPool` as a TYPE — a field's type, a local's
        // type, a `new`, and several cast targets — so no body seam can reach it. The base drops
        // the type outright (every libGDX use went with the drop; Ashley's did not), and a
        // dependent may not inject at the base's FQN. Re-pointing every reference at Ashley's own
        // factory-backed pool is the seam that fits.
        new balticporter.transform.TypeRedirectTransform(Map(
          "com.badlogic.gdx.utils.ReflectionPool" -> "com.badlogic.ashley.core.ComponentPool",
        )),
        new balticporter.transform.MethodBodyTransform(Map(
        // `Engine.createComponent` is the one reflective site in Ashley's 21 files: it calls
        // `ClassReflection.newInstance(componentType)` and catches `ReflectionException`, both
        // types the base drops. Everything else in `Engine` — 200 lines of entity/system/family
        // bookkeeping — translates mechanically, so dropping the TYPE to fix one method would fork
        // it from upstream permanently. This replaces the BODY and nothing else; the signature,
        // and therefore every call site, is untouched.
        // NB the two namespaces. The KEY names the member in the UPSTREAM namespace, because the
        // phase matches it against the model before the rename runs. The BODY is spliced verbatim
        // into emitted code and the rename never sees it, so it must already be written in the
        // port's FINAL namespace. Getting that backwards is one compile error naming `com.badlogic`
        // in a file that declares `package sge.ecs`.
        "com.badlogic.ashley.core.Engine#createComponent(Class)" ->
          "sge.ecs.ComponentFactories.create(componentType)",
        )),
        // LAST, deliberately. This reads what the BASE actually emitted and reports a reference the
        // base does not ship — so it must run AFTER the seams that re-point those references, or it
        // reports the very sites the next phase repairs. It is a RESIDUE check, exactly like
        // `PortabilityCheck`: what is left once this module's own policy has been applied.
        //
        // Run first it reported 7 findings, every one of them a `ReflectionPool` or
        // `ClassReflection` reference that `TypeRedirectTransform` and `MethodBodyTransform`
        // immediately fix. Run last it reports what an agent must actually act on.
        //
        // An absent or stale base map is a loud finding, never a silent fallback: the run says so
        // and falls back to re-derivation.
        balticporter.transform.PortMapTransform.forBases("sge"),
      ),
      // THE REFERENCE HAND PORT for sge-ecs. The hand-written Ashley port lives in sge's extension
      // tree. NOT inherited — the base's parity points at sge's own hand port, not at Ashley's.
      parity = Some(ParityRef(roots = List(
        repoRoot.resolve("../sge/sge-extension/ecs/src/main/scala").normalize))),
      dropMethods = Set(
        // `ImmutableArray.toArray(Class<V>)` (`ImmutableArray.java:77-79`) is a one-line forwarder
        // to `Array.toArray(Class)`, which the BASE manifest drops: it is the `ArrayReflection`
        // overload libGDX itself deprecated in favour of the portable `ArraySupplier` twin, and
        // `java.lang.reflect.Array` exists on neither Scala.js nor Scala Native.
        //
        // This is exactly the case the base already handles for libGDX's OWN subclasses —
        // "the Array subclasses' own deprecated pairs only forward to Array's, so they go with
        // them" — and Ashley is one more subclass-shaped forwarder, one repository further out.
        // The nilary `toArray()` twin beside it is untouched and is what the corpus calls.
        //
        // Found by RewriteTrace's orphaned-call check on the FIRST run, not by reading: an
        // inherited drop leaves a dangling call in the dependent, and the dependent is the only
        // module that can see it.
        "com.badlogic.ashley.utils.ImmutableArray#toArray(Class)",
      ),
    ))

  /** Ashley's own JUnit suite, as a dependent of [[core]]. */
  def test(repoRoot: Path): PortManifest = core(repoRoot).extendedBy(PortManifest(
    name    = "sge-ecs-test",
    surface = List(new balticporter.transform.TestFrameworkTransform()),
  ))
