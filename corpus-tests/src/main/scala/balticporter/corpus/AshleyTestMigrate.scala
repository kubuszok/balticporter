package balticporter.corpus

import balticporter.core.{FrontendConfig, Provenance, RuntimeMode}
import balticporter.runner.{Determinism, PortRun, SourceSet}

import java.nio.file.{Files, Path}
import scala.jdk.CollectionConverters.*

/** Port Ashley's own JUnit suite (`ashley/tests`) through the same pipeline as `ashley/src`.
  *
  *   corpus-tests/runMain balticporter.corpus.AshleyTestMigrate [--determinism=full]
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
      label     = "ashley-test",
      portRoot  = repoRoot.resolve("ashley-core"),
      sourceSet = SourceSet.Test,
      // The suite's own test dependencies must be on the frontend's classpath or Spoon cannot
      // resolve `import static org.mockito.Mockito.*` and silently reads `mock(...)` as an
      // UNQUALIFIED call on the suite itself — which then emits as `this.mock(...)` and fails to
      // compile with "value mock is not a member of EntityListenerTests". 12 errors, all one cause,
      // and the same shape as the `import static org.junit.Assert.*` trap recorded in
      // LIBGDX-PORT-STATUS: an unresolved static import does not fail, it RESOLVES WRONGLY.
      frontend  = FrontendConfig(testRoot, files, AshleyClasspath.resolve(repoRoot),
                                 resolutionRoots = List(ashleySrc, gdxSrc)),
      phases    = Nil, // supplied by the manifest — the two sources are mutually exclusive
      manifest  = Some(AshleyPolicy.test(repoRoot)),
      provenance = Some(Provenance(
        upstreamName     = "Ashley",
        upstreamCommit   = "vendored in ../sge/original-src/ashley",
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

  def resolve(repoRoot: Path): List[Path] =
    val cache = repoRoot.resolve("out/ashley-test-classpath.txt")
    val text =
      if Files.exists(cache) then Files.readString(cache).trim
      else
        val pb = new ProcessBuilder("cs", "fetch", "--classpath",
          "junit:junit:4.13.2", "org.mockito:mockito-core:1.10.19").redirectErrorStream(true)
        val proc = pb.start()
        // `cs` writes PROGRESS to stderr and the classpath to stdout, and the streams are merged
        // here so a failure is reportable. The classpath is the last line and the only one holding
        // a path separator — taking the whole output cached "Downloading https…" as a classpath
        // entry, and Spoon then refused the run with "Downloading https does not exist".
        val raw  = new String(proc.getInputStream.readAllBytes()).trim
        val out  = raw.linesIterator.filter(_.contains(".jar")).toList.lastOption.getOrElse("")
        if proc.waitFor() != 0 || out.isEmpty then
          // A missing classpath is reported, never silently swallowed: the failure it causes is a
          // WRONG resolution rather than an error, so a quiet fallback would look like a port bug.
          System.err.println(s"[ashley-test] could not fetch test classpath (is `cs` installed?):\n$raw")
          ""
        else
          Files.createDirectories(cache.getParent)
          Files.writeString(cache, out)
          out
    text.split(java.io.File.pathSeparator).filter(_.nonEmpty).map(Path.of(_)).toList
