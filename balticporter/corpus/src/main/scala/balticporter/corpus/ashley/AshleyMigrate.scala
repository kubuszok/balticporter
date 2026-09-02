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
      // `ImmutableArray` wraps `Array<T>` (retargetted to `DynamicArray`) and delegates every
      // method. Three of its methods (`contains(T,boolean)`, `indexOf(T,boolean)`,
      // `lastIndexOf(T,boolean)`) delegate with a non-literal boolean identity flag, which
      // BoolDispatch cannot dispatch statically. Its `iterable` field references
      // `Array.ArrayIterable`, a nested type of the retargetted `Array` that no longer exists.
      // The reference port (sge) hand-writes the whole class. Drop the type and inject the
      // hand-written replacement, which is the same pattern the base uses for its dropped types.
      dropTypes = Set("com.badlogic.ashley.utils.ImmutableArray"),
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
            // `EntitySystem#engine` — C16.1 is CLOSED at the ENGINE level: the `discoverScope`
            // post-pass in `resolveCapturedLocalClashes` now sees anonymous classes inside
            // converted test bodies (blocks inlined by TestFrameworkTransform), so the
            // capture-rename fires for the shape. The BeanDetect auto-detection (scope =
            // Everywhere() on the base) also auto-detects this pair, so the configured entry
            // is redundant for the RENAME — but it is kept as the port's STATED POLICY for
            // this member: the hand port writes `def engine: Nullable[Engine] = _engine`
            // (a def-pair, not a var), and this entry records the decision. It also gives the
            // binder a `neverFired` line if the accessor shape changes upstream.
            "com.badlogic.ashley.core.EntitySystem#engine"               -> "getEngine",
            // NO `#entities` pair on the three iterating systems: the hand port KEEPS `getEntities`
            // (parenless, IteratingSystem.scala:58) over a PRIVATE `entities` field, so a collapse
            // there would be the opposite of parity — measured 2026-08-26 as 3 `getEntities` rows in
            // the reference port only plus 3 `entities` rows in the emitted port only.
            "com.badlogic.ashley.core.ComponentType#index"                -> "getIndex",
            "com.badlogic.ashley.core.Family#index"                      -> "getIndex",
            "com.badlogic.ashley.systems.IteratingSystem#family"          -> "getFamily",
            "com.badlogic.ashley.systems.SortedIteratingSystem#family"    -> "getFamily",
            "com.badlogic.ashley.systems.IntervalIteratingSystem#family"  -> "getFamily",
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
        // --- 3.2g: Pool class-to-trait (ecs drop-in parity) ---
        // Pool is now dropped+injected as sge's trait in the BASE manifest, and the
        // ClassToTraitTransform phase (also in the base) rewrites every subclass. Ashley
        // inherits both through extendedBy -- no instance declared here.
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
        // The body wraps in `Nullable(…)` because the return type moved from `T` to `Nullable[T]`
        // after `nullableMembers` retyped the declaration. `Nullable(null)` normalises to
        // `Nullable.empty`, which is exactly what the hand port does.
        "com.badlogic.ashley.core.Engine#createComponent(Class)" ->
          "lowlevel.Nullable(sge.ecs.ComponentFactories.create(componentType))",
        // ImmutableArray is DROPPED and injected (see `dropTypes` above), so no body transforms
        // are needed for its methods.
        // PooledEngine.ComponentPools.freeAll(Array): the java body uses a RAW `Array` parameter
        // (`freeAll(Array objects)`), which after retarget becomes `DynamicArray[?]`.
        // `objects.apply(i)` returns the wildcard type which does not conform to `Object`.
        // The reference port casts explicitly. Replace the body to iterate and free.
        "com.badlogic.ashley.core.PooledEngine$ComponentPools#freeAll(Array)" ->
          """{ if (objects == null) throw new java.lang.IllegalArgumentException("objects cannot be null.")
            |  else { var i: scala.Int = 0; val n: scala.Int = objects.size; while (i < n) { { val obj = objects.apply(i).asInstanceOf[java.lang.Object]; if (obj != null) this.free(obj) else () }; i = i + 1 } } }""".stripMargin,
        // PooledEngine.ComponentPools.clear(): the java body uses `pools.each().value.clear()` via
        // ForEach on ObjectMap.Values. After retarget, `foreachValue` produces a lambda typed at
        // the VALUE type. The pools field is `ObjectMap[Class[?], ReflectionPool]` which after
        // TypeRedirect becomes `ObjectMap[Class[?], ComponentPool]`. But the frontend resolved the
        // formal as `Pool[?]` (the parent type). Replace with correctly-typed lambda.
        "com.badlogic.ashley.core.PooledEngine$ComponentPools#clear()" ->
          "this.pools.foreachValue((pool: sge.ecs.ComponentPool[?]) => pool.clear())",
        )),
        // SIX MEMBERS WHOSE RETURN TYPE IS NULLABLE — knowledge from sge's migration notes, not from
        // any annotation the java carries. Each member's hand port wraps the return in `Nullable[T]`:
        //
        //   Engine.scala:10:   "Idiom: Nullable[A] for createComponent return"
        //   Engine.scala:168:  `def getSystem[T <: EntitySystem](…): Nullable[T]`
        //   Entity.scala:13:   "Idiom: Nullable[A] in public getComponent return type"
        //   Entity.scala:94:   `def remove[T <: Component](…): Nullable[T]`
        //   EntitySystem.scala:10: "Idiom: Nullable[Engine] for engine reference"
        //   PooledEngine.scala:72: `override def createComponent[T <: Component](…): Nullable[T]`
        //
        // The keys use the name as it exists when NullabilityTransform runs — AFTER bean collapse
        // has renamed `getEngine()` to `engine`. The member FQNs are in the UPSTREAM namespace
        // (before package-rename, which runs later). `Entity#getComponent` names BOTH overloads
        // (public and package-private) because `Symbol.fullName` does not include the descriptor and
        // the hand port wraps both.
        //
        // MERGED with the base's `NullabilityTransform` through `MergeablePolicy`: `nullableMembers`
        // unions, the base's `annotations`/`target`/`scope` are inherited. Ashley is the first
        // dependent to construct its own `NullabilityTransform` instance (the K13 doc's "no dependent
        // constructs a nullability of its own" is superseded by this entry).
        new balticporter.transform.NullabilityTransform(
          nullableMembers = Set(
            "com.badlogic.ashley.core.Engine#createComponent",
            "com.badlogic.ashley.core.Engine#getSystem",
            "com.badlogic.ashley.core.Entity#getComponent",
            "com.badlogic.ashley.core.Entity#remove",
            "com.badlogic.ashley.core.EntitySystem#engine",
            "com.badlogic.ashley.core.PooledEngine#createComponent",
          ),
        ),
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
        // ImmutableArray.iterable: a FIELD whose TYPE is `Array.ArrayIterable`, a nested type of
        // the retargetted `Array`. After retarget, `sge.utils.Array` became `DynamicArray` and
        // the nested type no longer exists. The reference port (sge) does not use ArrayIterable
        // at all — it delegates `iterator()` to `array.iterator` directly. The field is dropped
        // and the `iterator()` body is replaced by MethodBodyTransform above.
        "com.badlogic.ashley.utils.ImmutableArray#iterable",
      ),
    ))

  /** Ashley's own JUnit suite, as a dependent of [[core]]. */
  def test(repoRoot: Path): PortManifest = core(repoRoot).extendedBy(PortManifest(
    name    = "sge-ecs-test",
    surface = List(
      new balticporter.transform.TestFrameworkTransform(),
      // --- 3.2g: adapt forbiddenRemoval body ---
      // The java test calls `iterator().remove()` which throws GdxRuntimeException.
      // Scala's Iterator has no `remove()` — the read-only invariant is enforced by the type
      // system. The sge hand port's ImmutableArraySuite (lines 95-123) replaces this with a
      // verification that iteration works correctly and does not mutate the backing data.
      // Citation: ../sge/sge-extension/ecs/src/test/scala/sge/ecs/utils/ImmutableArraySuite.scala:103-106
      new balticporter.transform.MethodBodyTransform(Map(
        "com.badlogic.ashley.utils.ImmutableArrayTests#forbiddenRemoval" ->
          // Verify the iterator is read-only by type system -- iteration works, backing unmodified.
          // Uses the emitted namespace (sge.ecs). Must define its own `array` and `immutable`
          // locals because MethodBodyTransform replaces the WHOLE body and the original java
          // method defined them as locals (no class-level fields).
          // Citation: ../sge/sge-extension/ecs/src/test/scala/sge/ecs/utils/ImmutableArraySuite.scala:103-106
          """{ val array: lowlevel.util.DynamicArray[java.lang.Integer] = lowlevel.util.DynamicArray.apply[java.lang.Integer](); val immutable: sge.ecs.utils.ImmutableArray[java.lang.Integer] = new sge.ecs.utils.ImmutableArray[java.lang.Integer](array); { var i: scala.Int = 0; while (i < 10) { array.add(i.asInstanceOf[java.lang.Integer]); i = i + 1 } }; val iter = immutable.iterator; val first = iter.next(); munit.Assertions.assertEquals(first, 0.asInstanceOf[java.lang.Integer]); var count: scala.Int = 1; while (iter.hasNext) { iter.next(); count = count + 1 }; munit.Assertions.assertEquals(count, 10); munit.Assertions.assertEquals(immutable.size, 10); { var i: scala.Int = 0; while (i < 10) { munit.Assertions.assertEquals(immutable.get(i), i.asInstanceOf[java.lang.Integer]); i = i + 1 } } }""",
      )),
    ),
  ))
