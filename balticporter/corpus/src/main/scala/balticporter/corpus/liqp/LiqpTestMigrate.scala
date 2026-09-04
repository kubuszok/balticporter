package balticporter.corpus.liqp

import balticporter.corpus.ClasspathCache
import balticporter.runner.PortConfig

import java.io.File
import java.nio.file.{Files, Path}

/** Port liqp's own JUnit suite (`src/test/java`) through the same pipeline as
  * `src/main/java`.
  *
  *   corpus/runMain balticporter.corpus.liqp.LiqpTestMigrate [--determinism=full]
  *
  * The port is `balticporter/corpus/ports/liqp/test.conf`; this `main` names it and gives
  * the run its own report identity. 105 files, 639 live `@Test`, no behavioural gate until
  * they RUN: liqp's MAIN source set is not yet at 0 errors, so `just liqp-measure` prints
  * the census without running the suite (CLAUDE.md §3).
  *
  * Unusually clean for `TestFrameworkTransform` (no `@Rule`, `@RunWith`, `@Ignore`, JUnit 3
  * suites, or test-class inheritance), so it exercises the LIBRARY, not the conversion:
  * `ServiceLoader` (via `SPIHelper.findProviders()`, whose `META-INF/services` resource the
  * port's hand-written half supplies -- `ENGINE-LIMITS.md` P5); the filesystem, through the
  * PROCESS working directory (45 `@Test`s read fixtures relatively, run from a symlink
  * tree); 38 anonymous classes; 767 hamcrest `assertThat` sites left on hamcrest
  * (deliberately, `TestFrameworkTransform` has no matcher algebra); `@Test(expected)` x 46
  * and `@Before` x 2.
  *
  * A DEPENDENT of [[LiqpMigrate]]: resolves against `src/main/java`, inheriting the base's
  * renames and surface phases via `base = "main.conf"` (CLAUDE.md §1.5).
  */
object LiqpTestMigrate:

  def main(args: Array[String]): Unit =
    LiqpTestClasspath.ensure(LiqpPort.repoRoot)
    PortConfig.load(LiqpPort.conf("test.conf"), args.toSeq).execute()

/** liqp's TEST frontend classpath: everything [[LiqpClasspath]] resolves, plus JUnit.
  * Exactly ONE test-scope coordinate (`junit:junit:4.13.1`); `hamcrest-core` arrives
  * transitively and is deliberately NOT named (a port resolves what the library DECLARES).
  * The MAIN classpath is included because `resolutionRoots` is liqp's Java source and
  * because the test sources import the javac-compiled ANTLR parser directly (D-liqp-1) --
  * both delegated to [[LiqpClasspath.ensure]], never duplicated.
  *
  * Two things this object cannot supply: the `META-INF/services` RESOURCE the suite's
  * `ServiceLoader` reads (hand-written under `ported/ssg-liquid/src/main/resources`,
  * CLAUDE.md §5.5; `ENGINE-LIMITS.md` P5 is the engine gap it works around), and the
  * process WORKING DIRECTORY 45 tests read fixtures through (the lane's symlink tree).
  *
  * Written to a FILE rather than inlined in the conf (CLAUDE.md §1.5).
  */
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
