package balticporter.corpus.liqp

import balticporter.corpus.ClasspathCache
import balticporter.runner.PortConfig

import java.io.File
import java.nio.file.{Files, Path}

/** Port liqp's own JUnit suite (`src/test/java`) through the pipeline: `.../ports/liqp/test.conf`.
  * 105 files, 639 live `@Test`, no behavioural gate until they RUN — liqp's MAIN source set is
  * not yet at 0 errors (§3). Unusually clean for `TestFrameworkTransform`; exercises the
  * LIBRARY: `ServiceLoader`, the filesystem via the PROCESS working directory, 38 anonymous
  * classes, 767 hamcrest sites left on hamcrest. A DEPENDENT of [[LiqpMigrate]] (§1.5). */
object LiqpTestMigrate:

  def main(args: Array[String]): Unit =
    LiqpTestClasspath.ensure(LiqpPort.repoRoot)
    PortConfig.load(LiqpPort.conf("test.conf"), args.toSeq).execute()

/** liqp's TEST frontend classpath: everything [[LiqpClasspath]] resolves, plus JUnit — exactly
  * ONE test-scope coordinate; `hamcrest-core` arrives transitively and is deliberately NOT
  * named. The MAIN classpath is included since the test sources import the javac-compiled ANTLR
  * parser directly (D-liqp-1). Cannot supply: the `META-INF/services` RESOURCE (hand-written,
  * §5.5) or the process WORKING DIRECTORY 45 tests read fixtures through. */
object LiqpTestClasspath:

  /** the one test-scope coordinate `pom.xml` declares. hamcrest-core 1.3 is its transitive. */
  val TestCoordinates: List[String] = List("junit:junit:4.13.1")

  def cache(repoRoot: Path): Path = repoRoot.resolve("out/liqp-test-classpath.txt")

  /** Guarantee the test classpath file exists and AGREES with the main one, building it if
    * not: a cached test line from before a `LiqpClasspath.ensure` rebuild (e.g. after
    * `out/` was cleaned) would otherwise name jars and a parser directory no longer
    * current, so the test line is DERIVED from the main one on every run. */
  def ensure(repoRoot: Path): Path =
    val mainEntries =
      Files
        .readString(LiqpClasspath.ensure(repoRoot))
        .trim
        .split(File.pathSeparator)
        .filter(_.nonEmpty)
        .toList
    val out      = cache(repoRoot)
    val key      = ClasspathCache.key(TestCoordinates)
    val existing = if Files.exists(out) then Files.readString(out).trim else ""
    val carried  = existing.split(File.pathSeparator).filter(_.nonEmpty).toList
    // reusable only if it still STARTS with the main classpath, entry for entry, AND was
    // resolved from the test coordinates this port declares now.
    if ClasspathCache.fresh(out, key) && carried.startsWith(mainEntries) &&
       carried.sizeIs > mainEntries.size
    then out
    else
      val junit = LiqpClasspath.fetch(TestCoordinates)
      // `distinct` rather than a union: the ORDER is the resolution order `cs` produced, and the
      // main entries come first because they are what the resolution roots are read against.
      ClasspathCache.write(out, (mainEntries ++ junit).distinct.mkString(File.pathSeparator), key)
