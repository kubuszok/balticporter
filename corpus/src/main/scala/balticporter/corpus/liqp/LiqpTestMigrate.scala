package balticporter.corpus.liqp

import balticporter.runner.PortConfig

import java.io.File
import java.nio.file.{Files, Path}

/** Port liqp's own JUnit suite (`src/test/java`) through the same pipeline as `src/main/java`.
  *
  *   corpus/runMain balticporter.corpus.liqp.LiqpTestMigrate [--determinism=full]
  *
  * The port is `corpus/ports/liqp/test.conf`; this `main` names it, ensures the test classpath the
  * conf points at exists, and gives the run its report identity (`CheckReport.dir` comes from the
  * main class's simple name, so `port-report/LiqpTestMigrate` is a baseline of its own and liqp
  * MAIN's numbers cannot move because this ran). See [[LiqpMigrate]] for why that is all a
  * migration program is.
  *
  * ==105 files, 639 live `@Test`, and no behavioural gate until they RUN==
  * A 640th is commented out upstream (`filters/date/FuzzyDateDateParserTest`), which is why
  * `java_test_count`'s comment-aware count is the number the lane gates on and a raw grep is not.
  * `PROGRESS.md` §10.5 says "compiles" — and at this milestone not even that: liqp's MAIN source
  * set stands at 31 scalac errors, so `just liqp-measure` prints the census and says, in the run,
  * that it is not running the suite. CLAUDE.md §3 is explicit about the difference between
  * compiling and passing; §4.4 lists the Java forms that translate to valid Scala meaning
  * something else, and not one of them moves an error count.
  *
  * ==What makes this suite worth running rather than merely counting==
  * It is unusually clean for `TestFrameworkTransform` — no `@Rule`, no `@RunWith`, no `@Ignore`,
  * no JUnit 3 suites, no test-class inheritance — so what it exercises is the LIBRARY, not the
  * conversion:
  *
  *   - **`ServiceLoader`.** `Template`'s `static { }` block reaches `SPIHelper.findProviders()`,
  *     i.e. effectively the whole suite, and `LiquidSupport` reaches it on every render of a POJO.
  *     Nothing in the pipeline emits the `META-INF/services` resource that lookup reads
  *     (`ENGINE-LIMITS.md` P5), so the port's hand-written half supplies it — see
  *     [[LiqpTestClasspath]] and `test.conf` for the decision and where the file lives.
  *   - **The filesystem, through the PROCESS working directory.** 45 `@Test`s read `./snippets/`,
  *     `./_includes/` and `src/test/jekyll/` relatively, and `TemplateTest.parseWithInputStream`
  *     goes through `new FileInputStream(…)`, which `-Duser.dir` does not reach. The lane runs the
  *     suite from a symlink fixture tree of its own.
  *   - **38 anonymous classes** (`new Filter(){…}`, `new Inspectable(){…}`) — the shape of the
  *     libGDX defect CLAUDE.md §3 counts at 156 sites, every one of which compiled.
  *   - **767 hamcrest `assertThat(x, is(y))` sites**, which the conversion deliberately leaves on
  *     hamcrest: `TestFrameworkTransform` maps `org.junit.Assert` onto `munit.Assertions` and has
  *     no matcher algebra. Measured — a hamcrest `AssertionError` produces MUnit's `==> X` marker
  *     per test and the suite continues, so `reconcile_outcomes` loses nothing.
  *   - **`@Test(expected = …)` × 46 and `@Before` × 2**, the two §4.4 rows a JUnit suite always
  *     has: run bare, the first would PASS while checking nothing.
  *
  * A DEPENDENT of [[LiqpMigrate]]: it resolves against `src/main/java`, never against the Scala
  * that port emitted, so the conf's `base = "main.conf"` inherits the base's `packageRenames`
  * (`liqp` -> `ssg.liquid`) and surface phases rather than restating them (CLAUDE.md §1.5).
  */
object LiqpTestMigrate:

  def main(args: Array[String]): Unit =
    LiqpTestClasspath.ensure(LiqpPort.repoRoot)
    PortConfig.load(LiqpPort.conf("test.conf"), args.toSeq).execute()

/** liqp's TEST frontend classpath: everything [[LiqpClasspath]] resolves, plus JUnit.
  *
  * ==What the pom DECLARES at test scope, and what arrives with it==
  * Exactly ONE coordinate: `junit:junit:4.13.1`. `org.hamcrest:hamcrest-core:1.3` arrives
  * TRANSITIVELY — verified against `cs fetch --classpath junit:junit:4.13.1`, which resolves both
  * jars — and is deliberately NOT named here. A port resolves what the library DECLARES, and
  * naming hamcrest would state a dependency liqp does not have. Neither is bumped, for the reason
  * `AshleyClasspath` records with its cost: Ashley's suite needs Mockito **1.10.19** because
  * `ComponentClassFactory` uses `org.mockito.asm`, removed in 2.x, and guessing a modern version
  * cost a full cycle.
  *
  * ==Why the MAIN classpath is part of it==
  * `resolutionRoots` is liqp's Java SOURCE, so the frontend type-checks `src/main/java` while
  * modelling the suite and needs every jar that source needs. It also needs the javac-compiled
  * ANTLR parser (D-liqp-1): `TestUtils`, `parser/v4/LiquidLexerTest` and `parser/v4/LiquidParserTest`
  * import `liquid.parser.v4.{LiquidLexer, LiquidParser, …}` DIRECTLY, so a test classpath without
  * it resolves those imports WRONGLY rather than failing (CLAUDE.md §5.1), and the port emits
  * nonsense and reports success. Both arrive by delegating to [[LiqpClasspath.ensure]], which is
  * also what guarantees the parser is compiled — never by a second copy of that list here.
  *
  * ==Two things this object cannot supply, and where they live instead==
  * Neither is a classpath, and both would be silent if forgotten:
  *
  *   - the `META-INF/services/ssg.liquid.spi.TypesSupport` RESOURCE the suite's `ServiceLoader`
  *     lookups read. It is hand-written under `liqp-core/src/main/resources` (CLAUDE.md §5.5 —
  *     `src/` is the port's hand-written half) and reaches the test JVM through `--resource-dir`
  *     on the lane's `scala-cli test`. `ENGINE-LIMITS.md` P5 holds the engine gap: the engine emits
  *     `.scala` and nothing else, and under a package rename such a file's NAME and CONTENTS are
  *     both upstream FQNs that have to be translated (CLAUDE.md §4.56);
  *   - the process WORKING DIRECTORY 45 of the tests read fixtures through. That is the lane's
  *     symlink tree, because `-Duser.dir` does not reach a `FileInputStream`.
  *
  * ==Why this is a FILE the conf points at, and not a list in the conf==
  * The realistic source of a classpath is a dependency resolver and a compiler, and a conf that
  * inlined `cs`'s output would be regenerated by hand on every dependency bump. A config file
  * naming a COMMAND to run would be the strings-that-are-secretly-code the transform SPI exists to
  * keep out (CLAUDE.md §1.5).
  */
object LiqpTestClasspath:

  /** the one test-scope coordinate `pom.xml` declares. hamcrest-core 1.3 is its transitive. */
  val TestCoordinates: List[String] = List("junit:junit:4.13.1")

  def cache(repoRoot: Path): Path = repoRoot.resolve("out/liqp-test-classpath.txt")

  /** Guarantee the test classpath file exists and AGREES with the main one, building it if not.
    *
    * The agreement check is not tidiness. The main classpath carries the javac-compiled parser
    * directory, and `LiqpClasspath.ensure` rebuilds it whenever `out/` has been cleaned — so a
    * cached test line from before that rebuild can name jars and a parser directory that are no
    * longer the ones the MAIN port is being resolved against, which is the two-halves-disagree
    * failure D-liqp-1 exists to prevent, arriving by the back door. Deriving the test line from
    * the main one on every run makes the two impossible to drift.
    */
  def ensure(repoRoot: Path): Path =
    val mainEntries =
      Files
        .readString(LiqpClasspath.ensure(repoRoot))
        .trim
        .split(File.pathSeparator)
        .filter(_.nonEmpty)
        .toList
    val out      = cache(repoRoot)
    val existing = if Files.exists(out) then Files.readString(out).trim else ""
    val carried  = existing.split(File.pathSeparator).filter(_.nonEmpty).toList
    // the cached line is reusable only if it still STARTS with the main classpath, entry for
    // entry: anything else is a line built against a different resolution of the base port.
    if carried.startsWith(mainEntries) && carried.sizeIs > mainEntries.size then out
    else
      val junit = LiqpClasspath.fetch(TestCoordinates)
      // `distinct` rather than a union: the ORDER is the resolution order `cs` produced, and the
      // main entries come first because they are what the resolution roots are read against.
      val line = (mainEntries ++ junit).distinct.mkString(File.pathSeparator)
      Files.createDirectories(out.getParent)
      Files.writeString(out, line)
      out
