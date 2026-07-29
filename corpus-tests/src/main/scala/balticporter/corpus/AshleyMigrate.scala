package balticporter.corpus

import balticporter.core.{FrontendConfig, PortManifest, Provenance, RuntimeMode}
import balticporter.runner.{Determinism, PortRun, SourceSet}

import java.nio.file.{Files, Path}
import scala.jdk.CollectionConverters.*

/** Migrate **Ashley** (`ashley/src`, 21 types — libGDX's entity-component-system) through the TIR.
  *
  *   corpus-tests/runMain balticporter.corpus.AshleyMigrate [--determinism=full]
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
  * imports three types `libgdx-core` deliberately does NOT translate:
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
      label     = "ashley",
      portRoot  = repoRoot.resolve("ashley-core"),
      sourceSet = SourceSet.Main,
      // libGDX core is a RESOLUTION root: parsed so every reference resolves, never emitted here.
      // `LibgdxCoreMigrate` emits it, and the two are compiled together.
      frontend  = FrontendConfig(base, files, Nil, resolutionRoots = List(gdxSrc)),
      phases    = Nil, // supplied by the manifest — the two sources are mutually exclusive
      manifest  = Some(AshleyPolicy.core(repoRoot)),
      provenance = Some(Provenance(
        upstreamName     = "Ashley",
        upstreamCommit   = "vendored in ../sge/original-src/ashley",
        originalLicense  = "Apache-2.0",
        sourcePathPrefix = "ashley/src",
        sourceRoot       = base.toString,
      )),
      // NOT `Vendored`: `LibgdxCoreMigrate` already vendors the collection shims into the module
      // this output is compiled beside. Vendoring again would define every support type twice —
      // which the JVM tolerates only while the copies agree, and the Scala.js/Native linkers reject.
      runtimeMode = RuntimeMode.Dependency,
      determinism = Determinism.fromArgs(args.toSeq),
      nextStep    = "bash scripts/ashley_measure.sh",
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
      name    = "ashley",
      governs = Set("com.badlogic.ashley"),
      // Ashley's OWN replacements. `inject` is not inherited — exactly one module ships each
      // replacement file, and libGDX core ships the ones for the types IT dropped.
      inject  = List(repoRoot.resolve("corpus-tests/ashley-overrides")),
      surface = List(new balticporter.transform.MethodBodyTransform(Map(
        // `Engine.createComponent` is the one reflective site in Ashley's 21 files: it calls
        // `ClassReflection.newInstance(componentType)` and catches `ReflectionException`, both
        // types the base drops. Everything else in `Engine` — 200 lines of entity/system/family
        // bookkeeping — translates mechanically, so dropping the TYPE to fix one method would fork
        // it from upstream permanently. This replaces the BODY and nothing else; the signature,
        // and therefore every call site, is untouched.
        "com.badlogic.ashley.core.Engine#createComponent(Class)" ->
          "com.badlogic.ashley.core.ComponentFactories.create(componentType)",
      ))),
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
    name    = "ashley-test",
    surface = List(new balticporter.transform.TestFrameworkTransform()),
  ))
