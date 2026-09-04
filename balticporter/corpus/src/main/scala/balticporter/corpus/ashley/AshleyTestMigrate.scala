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
      // an unresolved `import static org.mockito.Mockito.*` resolves WRONGLY (as an unqualified
      // call on the suite itself), not fails -- 12 errors, all one cause, ENGINE-LIMITS.md §6.
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

/** Ashley's TEST-scope dependencies, for shadow-class resolution only. JUnit 4 and Mockito;
  * neither is translated (`TestFrameworkTransform` converts the JUnit surface, Mockito calls
  * survive as ordinary references). Versions are Ashley's OWN (JUnit 4.13.2, Mockito 1.10.19):
  * `ComponentClassFactory` uses `org.mockito.asm`, removed in Mockito 2.x, so guessing a modern
  * version costs three errors.
  */
object AshleyClasspath:

  /** the versions Ashley's own `build.gradle` declares. */
  val Coordinates: List[String] = List("junit:junit:4.13.2", "org.mockito:mockito-core:1.10.19")

  def resolve(repoRoot: Path): List[Path] =
    ClasspathCache.entries(repoRoot.resolve("out/ashley-test-classpath.txt"), "ashley-test", Coordinates)
