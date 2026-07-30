package balticporter.corpus

import balticporter.core.{FrontendConfig, Provenance, RuntimeMode}
import balticporter.runner.{Determinism, PortRun, SourceSet, VendoredCommit}

import java.nio.file.{Files, Path}
import scala.jdk.CollectionConverters.*

/** Port simple-graphs' own JUnit suite (`src/test/java`) through the same pipeline as `src/main/java`.
  *
  *   corpus/runMain balticporter.corpus.SimpleGraphsTestMigrate [--determinism=full]
  *
  * 7 files, 17 `@Test` — the only behavioural evidence this port can have. `PROGRESS.md`
  * §simple-graphs says "compiles" until this runs, and CLAUDE.md §3 is explicit about the difference:
  * four silent
  * correctness defects in libGDX core all compiled cleanly, and not one of the ten Java forms in §4.4
  * moves a compile-error count.
  *
  * ==What makes this suite worth running rather than merely counting==
  * It exercises precisely the translations this library forced into the engine, from the outside:
  *
  *   - `Collections.sort(list, cmp)` and `Comparator` — is the ORDER the one java produced? A
  *     comparator whose sense is inverted compiles perfectly (`PROGRESS.md` §simple-graphs).
  *   - `java.util.stream` (`Collectors`, `IntStream`) in the TEST sources too, so the chain collapse
  *     is checked on code the library's own authors wrote to be read rather than to be ported.
  *   - `BadHashInteger` — a deliberately colliding `hashCode`, which is how `NodeMap`'s open
  *     addressing gets tested. That is a behavioural property no signature check can see.
  *   - `Array`, `Path`, `NodeCollection` and `VertexCollection` through their COLLECTION surface,
  *     which is the whole subject of K5: `containsAll`, `removeAll`, `retainAll`, `removeIf`,
  *     `toArray(T[])` all now come from a shim whose abstract/concrete split had four defects in it,
  *     each invisible until RefChecks ran.
  *
  * A DEPENDENT of [[SimpleGraphsMigrate]]: it resolves against `src/main/java`, never against the
  * Scala that port emitted, so `SimpleGraphsPolicy.test` inherits the base's rename and surface
  * phases rather than restating them (CLAUDE.md §1.5).
  */
object SimpleGraphsTestMigrate:

  def main(args: Array[String]): Unit =
    val repoRoot = Path.of(sys.props.getOrElse("balticporter.root", ".")).toAbsolutePath.normalize
    val mainSrc  = repoRoot.resolve("../sge/original-src/simple-graphs/src/main/java").normalize
    val testRoot = repoRoot.resolve("../sge/original-src/simple-graphs/src/test/java").normalize

    val files = Files.walk(testRoot).iterator().asScala
      .filter(p => p.toString.endsWith(".java"))
      .map(p => testRoot.relativize(p).toString)
      .filterNot(f => f.endsWith("package-info.java") || f.endsWith("module-info.java"))
      .toList.sorted

    PortRun(
      label     = "simple-graphs-test",
      portRoot  = repoRoot.resolve("simplegraphs-core"),
      sourceSet = SourceSet.Test,
      // JUnit must be on the FRONTEND's classpath. `import static org.junit.Assert.assertEquals` that
      // Spoon cannot resolve does not fail — it resolves WRONGLY, to an unqualified call on the
      // enclosing test class, and emits as `this.assertEquals(...)`. Recorded twice already (libGDX's
      // `org.junit.Assert.*`, Ashley's `org.mockito.Mockito.*`); this port would have made it three.
      frontend  = FrontendConfig(testRoot, files, SimpleGraphsClasspath.resolve(repoRoot),
                                 resolutionRoots = List(mainSrc)),
      phases    = Nil, // supplied by the manifest — the two sources are mutually exclusive
      manifest  = Some(SimpleGraphsPolicy.test),
      provenance = Some(Provenance(
        upstreamName     = "simple-graphs",
        upstreamCommit   = VendoredCommit.of(testRoot),
        originalLicense  = "MIT",
        sourcePathPrefix = "src/test/java",
        sourceRoot       = testRoot.toString,
      )),
      // NOT `Vendored`, unlike the main port: that source set is compiled beside this one and already
      // carries the shims. Vendoring again would define every support type twice — which the JVM
      // tolerates only while the copies agree, and the Scala.js/Native linkers reject.
      runtimeMode = RuntimeMode.Dependency,
      determinism = Determinism.fromArgs(args.toSeq),
      nextStep    = "just sg-measure",
    ).execute()

/** simple-graphs' TEST-scope dependency, for shadow-class resolution only.
  *
  * JUnit 4 and nothing else — `build.gradle` declares exactly `junit:junit:4.12`, and that is the
  * version used rather than a current one. The rule is recorded in [[AshleyClasspath]] with its
  * cost: Ashley's suite needs Mockito **1.10.19** because `ComponentClassFactory` uses
  * `org.mockito.asm`, removed in 2.x, and guessing a modern version cost a full cycle. A port
  * resolves what the library DECLARES.
  *
  * `TestFrameworkTransform` converts the JUnit surface to MUnit, so the jar is a frontend input only
  * and never reaches the emitted code.
  */
object SimpleGraphsClasspath:

  def resolve(repoRoot: Path): List[Path] =
    val cache = repoRoot.resolve("out/simplegraphs-test-classpath.txt")
    val text =
      if Files.exists(cache) then Files.readString(cache).trim
      else
        val pb = new ProcessBuilder("cs", "fetch", "--classpath", "junit:junit:4.12")
          .redirectErrorStream(true)
        val proc = pb.start()
        // `cs` writes PROGRESS to stderr and the classpath to stdout; merged here so a failure is
        // reportable, then filtered to the one line holding a jar. Taking the whole output cached
        // "Downloading https…" as a classpath entry and Spoon refused the run with
        // "Downloading https does not exist".
        val raw  = new String(proc.getInputStream.readAllBytes()).trim
        val out  = raw.linesIterator.filter(_.contains(".jar")).toList.lastOption.getOrElse("")
        if proc.waitFor() != 0 || out.isEmpty then
          System.err.println(s"[simple-graphs-test] could not fetch test classpath (is `cs` installed?):\n$raw")
          ""
        else
          Files.createDirectories(cache.getParent)
          Files.writeString(cache, out)
          out
    text.split(java.io.File.pathSeparator).filter(_.nonEmpty).map(Path.of(_)).toList
