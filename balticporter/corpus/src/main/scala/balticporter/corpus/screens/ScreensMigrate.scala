package balticporter.corpus.screens

import balticporter.corpus.ClasspathCache
import balticporter.core.{FrontendConfig, PortManifest, Provenance, RuntimeMode}
import balticporter.corpus.libgdx.LibgdxPolicy
import balticporter.runner.{Determinism, PortRun, SourceSet, VendoredCommit}
import balticporter.transform.TypeRedirectTransform

import java.nio.file.{Files, Path}
import scala.jdk.CollectionConverters.*

/** Migrate **libgdx-screenmanager** (`src/main/java`, 22 types — a screen stack, a transition
  * queue and eleven concrete transitions) through the TIR.
  *
  *   corpus/runMain balticporter.corpus.screens.ScreensMigrate [--determinism=full]
  *
  * ==A DEPENDENT port, with a SECOND dependency the corpus has not met before==
  * Every one of the 22 files resolves against libGDX core, so `gdx/src` is a resolution root and
  * the policy is [[LibgdxPolicy.core]] EXTENDED rather than restated (CLAUDE.md §1.5) — the same
  * shape as Ashley's and anim8's.
  *
  * What is new is the OTHER dependency. `build.gradle` declares
  * `api "com.github.crykn.guacamole:gdx:v0.3.6"` — with the comment "is exposed because of
  * NestableFrameBuffer" — and ten guacamole types reach into these sources: `Preconditions`,
  * `Pair`, `@Beta`, `Logger`, `LoggerService`, `GLUtils`, `NestableFrameBuffer`,
  * `QuadMeshGenerator`, `ShaderProgramFactory`, `ShaderCompatibilityHelper`. guacamole is a
  * SEPARATE upstream library that this corpus does not vendor and does not port, so it is a
  * resolution input only: [[ScreensClasspath]] fetches exactly what `build.gradle` declares and
  * the frontend resolves against the JAR.
  *
  * Resolving it is necessary and not sufficient. A resolved-but-unported type still EMITS as
  * `de.damios.guacamole.…`, which is a Scala name nothing in reach declares — the "reference the
  * resolution root cannot supply" shape. The engine already has the (b) mechanism for exactly
  * that, [[TypeRedirectTransform]]: every occurrence is re-pointed at a shape-compatible type this
  * port ships itself, hand-written under `ported/sge-screens/src/main/scala` (CLAUDE.md §5.5 — `src/` is
  * the hand-written half of a port). See [[ScreensPolicy.guacamole]] for the table and for what
  * each replacement is.
  *
  * ==Where this port is strictly better than the reference hand port==
  * `sge-extension/screens` has 20 Scala files to these 22 Java ones, and the difference is not
  * only arithmetic (PROGRESS.md §1.1):
  *
  *   - **`NestableFrameBuffer` is absent from sge**, which uses a plain `FrameBuffer` for the
  *     screen manager's own two buffers. libGDX's `FrameBuffer.end()` unbinds to framebuffer 0
  *     rather than to whatever was bound on `begin()`, so a screen or transition that binds an FBO
  *     of its OWN inside a managed render rebinds to the DEFAULT buffer when it finishes and the
  *     screen manager's buffer is silently lost for the rest of the frame. Upstream's whole reason
  *     for depending on guacamole is that type. This port carries it.
  *   - `ManagedScreenAdapter`, `BasicInputMultiplexer` and `Supplier` are simply not in sge; they
  *     are ported here mechanically.
  *
  * ==Scope==
  * `src/main/java` only for THIS run; [[ScreensTestMigrate]] takes `src/test/java` (7 files, 12
  * `@Test`). `src/example/java` (5 files) is deliberately out of scope and named rather than
  * silently skipped: it is a `gdx-backend-lwjgl3` demo application, and no backend is ported.
  */
object ScreensMigrate:

  def main(args: Array[String]): Unit =
    val repoRoot = ScreensPort.repoRoot
    val base     = ScreensPort.upstream(repoRoot).resolve("src/main/java")
    val gdxSrc   = repoRoot.resolve("../sge/original-src/libgdx/gdx/src").normalize

    PortRun(
      label     = "sge-screens",
      portRoot  = repoRoot.resolve("ported/sge-screens"),
      sourceSet = SourceSet.Main,
      frontend  = FrontendConfig(
        base,
        ScreensPort.javaFiles(base),
        classpath       = ScreensClasspath.entries(repoRoot),
        resolutionRoots = List(gdxSrc),
      ),
      phases     = Nil, // supplied by the manifest — the two sources are mutually exclusive
      manifest   = Some(ScreensPolicy.core(repoRoot)),
      provenance = Some(Provenance(
        upstreamName     = "libgdx-screenmanager",
        upstreamCommit   = VendoredCommit.of(base),
        originalLicense  = "Apache-2.0",
        sourcePathPrefix = "src/main/java",
        sourceRoot       = base.toString,
      )),
      // NOT `Vendored`: `LibgdxCoreMigrate` already vendors the collection shims into the module
      // this output is compiled beside. Vendoring again would define every support type twice —
      // which the JVM tolerates only while the copies agree, and the Scala.js/Native linkers reject.
      runtimeMode = RuntimeMode.Dependency,
      determinism = Determinism.fromArgs(args.toSeq),
      nextStep    = "just screens-measure",
    ).execute()

/** Paths and file selection shared by the two source sets of this port. */
object ScreensPort:

  def repoRoot: Path =
    Path.of(sys.props.getOrElse("balticporter.root", ".")).toAbsolutePath.normalize

  def upstream(repoRoot: Path): Path =
    repoRoot.resolve("../sge/original-src/libgdx-screenmanager").normalize

  def javaFiles(base: Path): List[String] =
    Files.walk(base).iterator().asScala
      .filter(p => p.toString.endsWith(".java"))
      .map(p => base.relativize(p).toString)
      .filterNot(f => f.endsWith("package-info.java") || f.endsWith("module-info.java"))
      .toList.sorted

/** libgdx-screenmanager's per-library policy — a DEPENDENT of libGDX core's.
  *
  * The base's `dropTypes`, `dropMethods`, `packageRenames` and signature-affecting phases are
  * INHERITED, not restated: they are facts about the surface screenmanager compiles against, and a
  * dependent that re-declared them would be free to drift. What this module adds is its own
  * namespace claim, its own renames, and the guacamole redirect.
  *
  * `inject` is deliberately NOT inherited (see [[balticporter.core.PortManifest]]): a drop is an
  * observation about the shared API and binds every module that sees it, but exactly one module
  * ships each replacement file. libGDX core ships the replacements for the types it dropped.
  */
object ScreensPolicy:

  def core(repoRoot: Path): PortManifest =
    LibgdxPolicy.core(repoRoot).extendedBy(PortManifest(
      name    = "sge-screens",
      governs = Set("de.eskalon.commons"),
      // sge flattens this library's three upstream packages into `sge.screen` and
      // `sge.screen.utils` (`../sge/sge-extension/screens/src/main/scala/sge/screen`), so
      // `core.ManagedGame` and `screen.ScreenManager` become siblings. Three pairs rather than
      // one, because `de.eskalon.commons -> sge.screen` alone would produce `sge.screen.screen`.
      // libGDX's `com.badlogic.gdx -> sge` is INHERITED from the base, not restated.
      packageRenames = Map(
        "de.eskalon.commons.screen" -> "sge.screen",
        "de.eskalon.commons.core"   -> "sge.screen",
        "de.eskalon.commons.utils"  -> "sge.screen.utils",
      ),
      surface = List(guacamole, nullability),
    ))

  /** screenmanager's OWN nullability annotation — `org.jspecify.annotations.Nullable`, which is a
    * different marker from the one libGDX declares and consumes.
    *
    * ==Why this is a SECOND instance and not a line in the base's set==
    * Folding jspecify's FQN into `LibgdxPolicy.core`'s `annotations` would put a fact about THIS
    * module's sources into the shared surface: libGDX declares no jspecify annotation anywhere, so
    * the entry would report as never-fired on every libGDX lane, forever. The honest shape is the
    * one §1.5 describes — each module states its own policy and the merge composes them — and
    * `NullabilityTransform`'s `MergeablePolicy` is what makes that expressible (`DESIGN.md` §8.13).
    * Both halves of the merged instance are visible in this port's run: the base's
    * `com.badlogic.gdx.utils.Null` and this entry.
    *
    * ==`target` is left at its default ON PURPOSE==
    * It is not this module's to choose: the shape must AGREE with the base's or the merge refuses,
    * and `T | Null` is what libGDX's floor emits.
    *
    * ==The `scope` is this module's OWN, and it composes with the base's rather than replacing it==
    * `Everywhere` unions its excepts, so the merged scope is `LibgdxPolicy.nullabilityExempt` PLUS
    * the two entries below — libGDX's exemptions are facts about libGDX's generic containers and
    * screenmanager neither adds to nor subtracts from them, and these two are facts about
    * screenmanager and have no business in the base's manifest. See [[nullabilityExempt]] for what
    * they are and what measured them.
    *
    * ==What retires with it==
    * `--dependency org.jspecify:jspecify:0.3.0` leaves `screens_deps` in the same commit. The
    * annotation is CONSUMED — stripped from every declaration whose type now states the contract —
    * so the emitted Scala names it nowhere, and a lane that compiles without the jar is the proof
    * of that. The jar stays on the FRONTEND classpath ([[ScreensClasspath]]): the Java sources still
    * carry the annotation and it has to RESOLVE for the phase to see it at all.
    */
  def nullability: balticporter.transform.NullabilityTransform =
    new balticporter.transform.NullabilityTransform(
      annotations = Set("org.jspecify.annotations.Nullable"),
      scope       = balticporter.tir.RuleScope.Everywhere(nullabilityExempt))

  /** The K13 exit, screenmanager's half — MEASURED, at the MEMBER, not guessed at the type.
    *
    * `ENGINE-LIMITS.md` K13: `Null` is a subtype of every CONCRETE reference type and not of an
    * abstract `T`, so the floor is free for a declaration's own signature and not for its USES.
    * `ScreenManager<S extends ManagedScreen, T extends ScreenTransition>` is the one type here that
    * annotates its own type parameters, and the unscoped run put **3 errors** in it — one
    * overload-resolution failure at `pushScreen` and two `T | Null` mismatches inside `render`.
    * These two entries clear all three.
    *
    * '''Member-level, and that is the difference from the base's list.''' libGDX's exit names TYPES
    * because its failing declarations are spread across a container's whole API; here the failing
    * set is four declarations in one class, so a `RuleScope` can say exactly them and the port pays
    * nothing for the rest. `ScreenManager#getCurrentScreen` and `#getLastScreen` are annotated at
    * an abstract `S` too — they are counted (`nullability-boundary`) and are NOT here, because
    * nothing in reach uses them in a position `S | Null` does not satisfy; the exit is what the
    * compiler measured, not every declaration that could in principle have failed.
    *
    * '''The two travel together.''' The field is assigned from the parameter, so scoping out one
    * and retyping the other is an assignment of `T | Null` to `T` — the same "half a pair" shape
    * K13 records for a scoped-out parent beside a retyped override, one level down.
    *
    * Deleted whole, not edited, when `DESIGN.md` §8.6's N2 (`-Yexplicit-nulls`) lands.
    */
  def nullabilityExempt: Set[String] = Set(
    "de.eskalon.commons.screen.ScreenManager#transition",
    // the bare member name is every OVERLOAD of it, which is what this needs: both `pushScreen`
    // overloads take the annotated `T`
    "de.eskalon.commons.screen.ScreenManager#pushScreen",
  )

  /** The guacamole seam.
    *
    * `com.github.crykn.guacamole:gdx` is a separate Apache-2.0 upstream that this corpus neither
    * vendors nor ports. Its types RESOLVE (through [[ScreensClasspath]]) and therefore translate
    * correctly — a `Preconditions.checkArgument` call is understood as a static call and a
    * `NestableFrameBuffer` field is understood as a `FrameBuffer` subtype — but they cannot be
    * EMITTED, because this run converts no guacamole compilation unit. Re-pointing them at Scala
    * this port ships is the engine's §1(b) answer to precisely that (see [[TypeRedirectTransform]]
    * for why a dependent cannot instead inject at the other module's FQN).
    *
    * The replacements live in `ported/sge-screens/src/main/scala/sge/screen/guacamole` and are
    * hand-written — which is a statement about scope, not a claim about quality: they are the
    * hand-written half of a port (CLAUDE.md §5.5), and the day guacamole becomes a corpus port of
    * its own, this table is what gets deleted.
    */
  def guacamole: TypeRedirectTransform = new TypeRedirectTransform(Map(
    // `NestableFrameBuffer` is why upstream depends on guacamole at all, and it is the one type
    // whose ABSENCE from the reference hand port is a behavioural defect (PROGRESS.md §1.1).
    "de.damios.guacamole.gdx.graphics.NestableFrameBuffer"      -> "sge.screen.guacamole.NestableFrameBuffer",
    "de.damios.guacamole.gdx.graphics.GLUtils"                  -> "sge.screen.guacamole.GLUtils",
    "de.damios.guacamole.gdx.graphics.QuadMeshGenerator"        -> "sge.screen.guacamole.QuadMeshGenerator",
    "de.damios.guacamole.gdx.graphics.ShaderProgramFactory"     -> "sge.screen.guacamole.ShaderProgramFactory",
    "de.damios.guacamole.gdx.graphics.ShaderCompatibilityHelper" -> "sge.screen.guacamole.ShaderCompatibilityHelper",
    "de.damios.guacamole.gdx.log.Logger"                        -> "sge.screen.guacamole.Logger",
    "de.damios.guacamole.gdx.log.LoggerService"                 -> "sge.screen.guacamole.LoggerService",
    "de.damios.guacamole.Preconditions"                         -> "sge.screen.guacamole.Preconditions",
    "de.damios.guacamole.tuple.Pair"                            -> "sge.screen.guacamole.Pair",
    "de.damios.guacamole.annotations.Beta"                      -> "sge.screen.guacamole.Beta",
  ))

/** libgdx-screenmanager's COMPILE-scope dependency, for shadow-class resolution only.
  *
  * `build.gradle` declares `implementation "com.badlogicgames.gdx:gdx:1.13.5"` and
  * `api "com.github.crykn.guacamole:gdx:v0.3.6"`. libGDX arrives as a SOURCE resolution root
  * instead — the same tree `LibgdxCoreMigrate` ports, so the two runs cannot see different
  * signatures — and is therefore excluded here rather than resolved twice from two versions.
  * What is left is guacamole and the annotation jars it and screenmanager both use
  * (`org.jspecify:jspecify`, which supplies the `@Nullable`/`@NullMarked` on these sources).
  *
  * guacamole is published only on jitpack, so the repository is named explicitly. A failure here
  * is FATAL rather than an empty classpath, for the reason [[SimpleGraphsClasspath]] records: an
  * import the frontend cannot resolve does not fail, it resolves WRONGLY — `Preconditions.check…`
  * becomes an unqualified call on the enclosing class — and the port then emits nonsense and
  * reports success.
  */
object ScreensClasspath:

  def cache(repoRoot: Path): Path = repoRoot.resolve("out/screens-classpath.txt")

  private val coordinates = List(
    "com.github.crykn.guacamole:gdx:v0.3.6",
    "org.jspecify:jspecify:0.3.0",
  )

  /** everything before the coordinates in the resolver invocation — part of the cache's fingerprint,
    * because an exclusion decides the classpath as much as a version does. */
  private val resolverArgs = List(
    "-r", "https://jitpack.io",
    // libGDX is a SOURCE resolution root; a second copy of it on the frontend classpath is a
    // second answer to every `com.badlogic.gdx.*` name, decided by scan order.
    "--exclude", "com.badlogicgames.gdx:gdx",
  )

  def entries(repoRoot: Path): List[Path] =
    ClasspathCache.entries(cache(repoRoot), "screens", coordinates, resolverArgs)

  /** Guarantee the cache file exists AND was resolved from these coordinates and exclusions,
    * fetching once if not. Returns the joined line. */
  def ensure(repoRoot: Path): String =
    Files.readString(ClasspathCache.ensure(cache(repoRoot), "screens", coordinates, resolverArgs)).trim
