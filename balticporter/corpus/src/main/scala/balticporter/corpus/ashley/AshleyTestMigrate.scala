package balticporter.corpus.ashley

import balticporter.corpus.ClasspathCache
import balticporter.core.{FrontendConfig, Provenance, RuntimeMode}
import balticporter.runner.{Determinism, PortRun, SourceSet, VendoredCommit}

import java.nio.file.{Files, Path}
import scala.jdk.CollectionConverters.*

/** Port Ashley's own JUnit suite (`ashley/tests`) through the same pipeline as `ashley/src`.
  *
  *   corpus/runMain balticporter.corpus.ashley.AshleyTestMigrate [--determinism=full]
  *
  * 18 files, 118 `@Test` methods, 458 assertions — the only behavioural evidence this port can
  * have. CLAUDE.md §3: a green compile says nothing about behaviour, and every silent defect this
  * project has found was found by running tests, not by compiling.
  *
  * A DEPENDENT of a DEPENDENT: the suite resolves against `ashley/src` (ported by
  * [[AshleyMigrate]]) which itself resolves against `libgdx/gdx/src` (ported by
  * [[LibgdxCoreMigrate]]). `PortManifest.baseChain` carries both, so the drops, renames and
  * surface phases of BOTH ancestors are inherited rather than restated — including the two seams
  * Ashley added for itself, `TypeRedirectTransform` and `MethodBodyTransform`, which the suite must
  * see or it would compile against a `ReflectionPool` and a reflective `createComponent` that the
  * library no longer has.
  */
object AshleyTestMigrate:

  def main(args: Array[String]): Unit =
    val repoRoot   = Path.of(sys.props.getOrElse("balticporter.root", ".")).toAbsolutePath.normalize
    val ashleySrc  = repoRoot.resolve("../sge/original-src/ashley/ashley/src").normalize
    val testRoot   = repoRoot.resolve("../sge/original-src/ashley/ashley/tests").normalize
    val gdxSrc     = repoRoot.resolve("../sge/original-src/libgdx/gdx/src").normalize

    val files = Files.walk(testRoot).iterator().asScala
      .filter(p => p.toString.endsWith(".java"))
      .map(p => testRoot.relativize(p).toString)
      .filterNot(f => f.endsWith("package-info.java") || f.endsWith("module-info.java"))
      .toList.sorted

    PortRun(
      label     = "sge-ecs-test",
      portRoot  = repoRoot.resolve("ported/sge-ecs"),
      sourceSet = SourceSet.Test,
      // The suite's own test dependencies must be on the frontend's classpath or Spoon cannot
      // resolve `import static org.mockito.Mockito.*` and silently reads `mock(...)` as an
      // UNQUALIFIED call on the suite itself — which then emits as `this.mock(...)` and fails to
      // compile with "value mock is not a member of EntityListenerTests". 12 errors, all one cause,
      // and the same shape as the `import static org.junit.Assert.*` trap recorded in
      // ENGINE-LIMITS.md §6: an unresolved static import does not fail, it RESOLVES WRONGLY.
      frontend  = FrontendConfig(testRoot, files, AshleyClasspath.resolve(repoRoot),
                                 resolutionRoots = List(ashleySrc, gdxSrc)),
      phases    = Nil, // supplied by the manifest — the two sources are mutually exclusive
      manifest  = Some(AshleyPolicy.test(repoRoot)),
      provenance = Some(Provenance(
        upstreamName     = "Ashley",
        upstreamCommit   = VendoredCommit.of(testRoot),
        originalLicense  = "Apache-2.0",
        sourcePathPrefix = "ashley/tests",
        sourceRoot       = testRoot.toString,
      )),
      // The MAIN source set of this module is compiled beside this one and already resolves the
      // runtime; vendoring again would define every support type twice.
      runtimeMode = RuntimeMode.Dependency,
      determinism = Determinism.fromArgs(args.toSeq),
      nextStep    = "scala-cli test the three emitted source sets together",
    ).execute()

/** Ashley's TEST-scope dependencies, for shadow-class resolution only.
  *
  * The suite uses JUnit 4 and Mockito. Neither is translated — `TestFrameworkTransform` converts
  * the JUnit surface, and the Mockito calls survive into the emitted Scala as ordinary references
  * the target build resolves. The jars are needed at FRONTEND time so Spoon can resolve the static
  * imports; without them an unresolved static import does not fail, it resolves WRONGLY, to an
  * unqualified call on the enclosing class.
  *
  * Versions are Ashley's OWN, from `build.gradle`: JUnit 4.13.2 and Mockito **1.10.19**. The
  * version matters and guessing a modern one costs three errors: `ComponentClassFactory` uses
  * `org.mockito.asm` to generate Component classes at runtime, and that package was removed in
  * Mockito 2.x. A port resolves the dependencies the library DECLARES, not the ones that look
  * current.
  * Cached, like `LiqpClasspath`, so a run does not depend on the network.
  */
object AshleyClasspath:

  /** the versions Ashley's own `build.gradle` declares — see above for what guessing a modern
    * Mockito cost. */
  val Coordinates: List[String] = List("junit:junit:4.13.2", "org.mockito:mockito-core:1.10.19")

  def resolve(repoRoot: Path): List[Path] =
    // through the shared mechanism, which also RECORDS these coordinates beside the cache: reused
    // for a different set, a cached line resolves this suite's imports against versions the port no
    // longer declares — and an import that resolves WRONGLY does not fail, it emits nonsense
    // (`ClasspathCache`). A fetch failure is fatal there rather than an empty classpath here, for
    // the same reason: a quiet fallback looks like a port bug two stages later.
    ClasspathCache.entries(repoRoot.resolve("out/ashley-test-classpath.txt"), "ashley-test", Coordinates)
