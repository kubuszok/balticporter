package balticporter.corpus.gdxai

import balticporter.corpus.ClasspathCache
import balticporter.core.{FrontendConfig, Provenance, RuntimeMode}
import balticporter.runner.{Determinism, PortRun, SourceSet, VendoredCommit}

import java.nio.file.{Files, Path}
import scala.jdk.CollectionConverters.*

/** Migrate **gdx-ai's own JUnit suite** (`gdx-ai/tests`, 2 files / 10 `@Test`) through the TIR.
  *
  *   corpus/runMain balticporter.corpus.gdxai.GdxAiTestMigrate [--determinism=full]
  *
  * ==TWO files, and the number is the whole point of this port==
  * `PROGRESS.md` §1.1's hand-port table records **24 / 196** against `sge-ai`. That figure is the
  * REFERENCE HAND PORT's own MUnit suite (`../sge/sge-extension/ai/src/test/scala`), hand-WRITTEN
  * for the port, and it is not what upstream ships. Upstream ships
  * `pfa/indexed/IndexedAStarPathFinderTest` (5 `@Test`) and `btree/branch/ParallelTest` (5 `@Test`,
  * with a `@Before`), and the separate top-level `gdx-ai/tests` gradle project — 111 files, 54 of
  * them named `*Test*.java` — declares ZERO `@Test` and is an LWJGL demo application. `just
  * ai-test-measure` censuses the two trees APART and gates each on its own number, in BOTH
  * directions, because every wrong answer this library has produced came from conflating them.
  *
  * So this suite validates **two of gdx-ai's eight packages** and says nothing about `msg`, `fsm`,
  * `sched`, `fma`, `steer` or the rest of `btree`/`pfa`. Ten passing tests is not a claim about the
  * library; it is the first evidence of BEHAVIOUR this port has at all, which is exactly what
  * `CLAUDE.md` §3 says a green compile cannot be.
  *
  * ==A dependent OF a dependent==
  * The test source set resolves against gdx-ai's Java AND libGDX's, never against the Scala either
  * port emitted (§1.5), so both are RESOLUTION ROOTS and the manifest is [[GdxAiPolicy.test]] —
  * `core` extended, not restated. `just ai-test-measure` compiles all three emitted source sets on
  * one `scala-cli` invocation and must run after `just gdx-measure` and `just ai-measure`.
  *
  * ==The classpath is not optional==
  * `import org.junit.Assert` must resolve or Spoon reads `assertEquals(…)` as an UNQUALIFIED call
  * on the suite itself and emits `this.assertEquals(…)` — the trap `AshleyTestMigrate` records and
  * `ENGINE-LIMITS.md` §6 states: an unresolved static import does not fail, it RESOLVES WRONGLY.
  * The version is gdx-ai's own (`gdx-ai/build.gradle`: `testImplementation 'junit:junit:4.12'`),
  * not a modern guess.
  */
object GdxAiTestMigrate:

  def main(args: Array[String]): Unit =
    val repoRoot = Path.of(sys.props.getOrElse("balticporter.root", ".")).toAbsolutePath.normalize
    val upstream = repoRoot.resolve("../sge/original-src/gdx-ai").normalize
    val testRoot = upstream.resolve("gdx-ai/tests").normalize
    val aiSrc    = upstream.resolve("gdx-ai/src").normalize
    val gdxSrc   = repoRoot.resolve("../sge/original-src/libgdx/gdx/src").normalize

    val files = Files.walk(testRoot).iterator().asScala
      .filter(p => p.toString.endsWith(".java"))
      .map(p => testRoot.relativize(p).toString)
      .filterNot(f => f.endsWith("package-info.java") || f.endsWith("module-info.java"))
      .toList.sorted

    PortRun(
      label     = "sge-ai-test",
      portRoot  = repoRoot.resolve("ported/sge-ai"),
      sourceSet = SourceSet.Test,
      frontend  = FrontendConfig(testRoot, files, GdxAiTestClasspath.resolve(repoRoot),
                                 resolutionRoots = List(aiSrc, gdxSrc),
                                 // THE SAME EXCLUSION `GdxAiMigrate` STATES, at the other kind of
                                 // input. Its convert list is explicit and filters
                                 // `com/badlogic/gdx/emu/` out of it; a resolution root is a
                                 // DIRECTORY and has no list, so without this the frontend is
                                 // handed the GWT super-source tree — which exists to REDECLARE
                                 // classes — and Spoon refuses the model outright:
                                 // `The type StandaloneFileSystem is already defined`. Narrowing
                                 // the ROOT to the package instead is measured worse and is a
                                 // different bug (25 `SurfaceNameDivergence`; see
                                 // `FrontendConfig.resolutionExcludes`).
                                 resolutionExcludes = List("com/badlogic/gdx/emu")),
      phases    = Nil, // supplied by the manifest — the two sources are mutually exclusive
      manifest  = Some(GdxAiPolicy.test(repoRoot)),
      provenance = Some(Provenance(
        upstreamName     = "gdx-ai",
        upstreamCommit   = VendoredCommit.of(testRoot),
        originalLicense  = "Apache-2.0",
        sourcePathPrefix = "gdx-ai/tests",
        sourceRoot       = testRoot.toString,
        // BOTH upstream test files carry the Apache header, so the banner plus §4.58's reproduced
        // comment meets the obligation by construction and nothing is declared here — unlike the
        // MAIN source set, where one file in 167 has no per-file notice (`GdxAiMigrate`).
      )),
      // The MAIN source set of this module is compiled beside this one and already resolves the
      // runtime; vendoring again would define every support type twice.
      runtimeMode = RuntimeMode.Dependency,
      determinism = Determinism.fromArgs(args.toSeq),
      nextStep    = "just ai-test-measure",
    ).execute()

/** gdx-ai's suite dependencies, at the versions its own build declares.
  *
  * Cached like every other corpus suite's, so a run does not depend on the network, and RECORDED
  * beside the cache so a line reused for a different coordinate set is a fatal mismatch rather than
  * a suite silently resolved against versions the port no longer declares (`ClasspathCache`).
  */
object GdxAiTestClasspath:

  /** `gdx-ai/gdx-ai/build.gradle`: `testImplementation group: 'junit', name: 'junit', version:
    * '4.12'`. Read off the build rather than aligned with Ashley's 4.13.2 — the two ports declare
    * different versions and guessing one cost `AshleyTestMigrate` twelve errors on a Mockito. */
  val Coordinates: List[String] = List("junit:junit:4.12")

  def resolve(repoRoot: Path): List[Path] =
    ClasspathCache.entries(repoRoot.resolve("out/gdx-ai-test-classpath.txt"), "gdx-ai-test", Coordinates)
