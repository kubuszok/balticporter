package balticporter.corpus.liqp

import balticporter.corpus.ClasspathCache
import balticporter.runner.PortConfig

import java.io.File
import java.nio.file.{Files, Path}

import scala.jdk.CollectionConverters.*

/** Migrate **liqp** (`src/main/java`, 135 types — a Java implementation of the Liquid templating
  * engine, backed by an ANTLR grammar).
  *
  *   corpus/runMain balticporter.corpus.liqp.LiqpMigrate [--determinism=full]
  *
  * ==This program is a `main` and a classpath, and that is all==
  * The port is `corpus/ports/liqp/main.conf` — read that, not this file. What is here is the
  * `main` that names the configuration and gives the run its report identity (`CheckReport.dir` is
  * derived from the main class's simple name, so a per-port `main` is what keeps
  * `port-report/LiqpMigrate` a stable measurement baseline), plus [[LiqpClasspath]], which is the
  * one thing a conf cannot hold: a classpath is produced by a resolver and a compiler, and a
  * config file naming a command to run would be the strings-that-are-secretly-code the transform
  * SPI exists to keep out (CLAUDE.md §1.5).
  *
  * ==Why liqp is in the corpus==
  * It is the FIRST library from outside the gdx/sge family, and it is here for what it moves from
  * §1(c) toward §1(b) toward §1(a). Four things no corpus library has exercised before:
  *
  *   1. **A third-party API surface.** libGDX, Ashley, simple-graphs, noise4j and jbump between
  *      them depend on the JDK and on each other. liqp depends on jackson, on antlr4's runtime and
  *      on strftime4j, so every question `CollectionsTransform` answers at a JDK seam is asked
  *      again at a seam that is neither the JDK nor a ported module — `ObjectMapper.convertValue`
  *      taking a `java.util.Map` is not a shim boundary the phase has ever had to price.
  *   2. **`ServiceLoader`.** `liqp/spi` discovers `TypesSupport` implementations reflectively.
  *      That is `ENGINE-LIMITS.md` CT7 territory — a class a FRAMEWORK instantiates has no caller
  *      to change — on a library that really does it, rather than in a test fixture.
  *   3. **A generated parser it does not own.** See [[LiqpClasspath]]: 9 432 lines of ANTLR output
  *      in a package (`liquid.parser.v4`) that is not the library's own, and that references back
  *      INTO the library. Milestone 1 treats it as external.
  *   4. **A reference port that is not sge.** `../ssg/ssg-liquid` is the hand-written Scala port of
  *      this same library, so CLAUDE.md §3.5 has something to consult here that is not the engine's
  *      usual witness.
  *
  * ==Milestone 1 is the SKELETON and the WALL==
  * This port is not expected to compile. It exists so that the first error census is a MEASURED
  * number classified per CLAUDE.md §1 rather than an estimate, and the wall is worked down after
  * that census exists. `PROGRESS.md` §liqp holds the numbers.
  */
object LiqpMigrate:

  def main(args: Array[String]): Unit =
    LiqpClasspath.ensure(LiqpPort.repoRoot)
    PortConfig.load(LiqpPort.conf("main.conf"), args.toSeq).execute()

/** Where this port's configuration lives, and where its upstream is, for the `main`s that name
  * them. */
object LiqpPort:

  def repoRoot: Path =
    Path.of(sys.props.getOrElse("balticporter.root", ".")).toAbsolutePath.normalize

  def conf(name: String): Path = repoRoot.resolve("corpus/ports/liqp").resolve(name)

  /** liqp's upstream checkout — a git SUBMODULE of ssg, not of sge like every other corpus
    * library. Stated once here and once, conf-relatively, in `main.conf`; the two must agree, and
    * the lane compares nothing, so a change is a change in both places. */
  def upstream: Path = repoRoot.resolve("../ssg/original-src/liqp").normalize

/** liqp's FRONTEND classpath: the six jars its `pom.xml` declares, plus the ANTLR-generated parser
  * compiled to class files.
  *
  * ==Why the parser is a CLASSPATH input and not a source root (decision D-liqp-1)==
  * liqp's own sources `import liquid.parser.v4.LiquidLexer` / `LiquidParser` / `NodeVisitor`. Those
  * six files are ANTLR OUTPUT — 9 432 lines — generated from the `.g4` grammars in
  * `src/main/antlr4` by the `antlr4-maven-plugin`, into `target/generated-sources/antlr4`, and they
  * are UNTRACKED: present in a checkout that has been built, absent in a fresh one. (The grammar
  * path is written in words rather than as a glob on purpose: a slash-star inside a Scala doc
  * comment OPENS a nested comment and swallows the rest of the file — CLAUDE.md §4.58's own rule,
  * met here by a hand-written file rather than by the emitter.) Milestone 1 therefore resolves them the
  * way it resolves jackson: externally, by class file, with the emitted Scala naming them fully
  * qualified and the compile lane putting the same directory on scalac's classpath. Porting the
  * generated parser THROUGH the engine is a recorded later milestone (a second module of this
  * port), not a thing to decide by accident here.
  *
  * ==A missing generated tree is FATAL==
  * Never a silently smaller port. CLAUDE.md §5.1's rule for a missing `--tests` path applies with
  * more force to a frontend classpath: `import liquid.parser.v4.LiquidParser` that the frontend
  * cannot resolve does not fail — it resolves WRONGLY, and the port emits nonsense and reports
  * success. The refusal below carries the regeneration command, because the agent that meets it is
  * in a fresh checkout and has no way to know that `target/` is where the input lives.
  *
  * ==Why `-implicit:none` and a `-sourcepath`, and not a plain `javac`==
  * The generated parser does not compile alone: `LiquidParser` has a member of type
  * `liqp.TemplateParser.ErrorMode`, so the ANTLR output and the library that consumes it are
  * mutually recursive. Handing javac liqp's own `src/main/java` as a SOURCEPATH resolves that;
  * `-implicit:none` then keeps javac from writing class files for the implicitly-read liqp
  * sources, so the output directory holds the 84 class files of `liquid/parser/v4` and NOTHING of
  * `liqp`. That distinction is the whole point: liqp's own types must reach the frontend as
  * SOURCE, from the port's `sourceRoot`. A `liqp/…/X.class` on the frontend classpath is a second,
  * older definition of every type being ported.
  *
  * ==What the coordinates are read from==
  * `pom.xml`, verbatim, including the one that looks like a typo and is not: `jackson.databind
  * .version` is **2.13.4.2** while `jackson.version` is 2.15.0 — two properties, and the port
  * resolves what the library DECLARES (the rule `AshleyClasspath` records, where guessing a modern
  * Mockito cost a full cycle). Note that `cs` then applies HIGHEST-version conflict resolution
  * where maven applies nearest-wins, so `jackson-datatype-jsr310:2.15.0`'s own dependency promotes
  * databind to 2.15.0 in the resolved line. That is a resolver difference, not a policy one, and it
  * is recorded here rather than papered over with a `--force-version`: the declared coordinate is
  * what this file states, and the day the promotion matters this note is what says where it came
  * from.
  */
object LiqpClasspath:

  /** exactly what `pom.xml` declares at compile scope. `junit:junit:4.13.1` is TEST scope and
    * belongs to the test port's classpath, not this one. */
  val Coordinates: List[String] = List(
    "org.antlr:antlr4-runtime:4.13.0",
    "com.fasterxml.jackson.core:jackson-core:2.15.0",
    "com.fasterxml.jackson.core:jackson-databind:2.13.4.2",
    "com.fasterxml.jackson.core:jackson-annotations:2.15.0",
    "com.fasterxml.jackson.datatype:jackson-datatype-jsr310:2.15.0",
    "ua.co.k:strftime4j:1.0.6",
  )

  /** the ANTLR output's package, and the only package this object's javac step may write. */
  val ParserPackage = "liquid/parser/v4"

  def cache(repoRoot: Path): Path         = repoRoot.resolve("out/liqp-classpath.txt")
  def parserClasses(repoRoot: Path): Path = repoRoot.resolve("out/liqp-parser-classes")

  /** THE SCALA COMPILE'S copy — the generated parser and liqp's own sources together, and never on
    * the frontend's classpath.
    *
    * It exists because D-liqp-1 and D-liqp-2 CUT EACH OTHER. Treating the generated parser as
    * external keeps it at `liquid.parser.v4`, compiled against upstream `liqp`; renaming the port
    * to `ssg.liquid` means nothing named `liqp` is emitted. `LiquidParser` has a member typed
    * `liqp.TemplateParser.ErrorMode`, so scalac reading that class file finds a signature naming a
    * package that does not exist — and does not report it: it throws
    * `AssertionError: failure to resolve inner class` out of `ClassfileParser` and ABORTS, which
    * reads as a smaller error count rather than as a failure (`scripts/_lib.sh` `compile_guard`
    * now says so).
    *
    * So the compile classpath carries upstream `liqp` too. That does not blur the port: the emitted
    * code is `ssg.liquid`, these classes are `liqp`, and no emitted name can resolve to one of them
    * — anything that did would be a reference the rename failed to move, which
    * `PackageRenameTransform.check` is what answers. What it BUYS is that the cost of holding both
    * decisions arrives as an ordinary, counted, attributable type error at the one call that
    * crosses the seam, instead of as a compiler crash.
    *
    * The FRONTEND still gets `parserClasses` and only that: there, a `liqp` class file WOULD be a
    * second, older definition of every type being ported. */
  def upstreamClasses(repoRoot: Path): Path = repoRoot.resolve("out/liqp-upstream-classes")

  /** where the `antlr4-maven-plugin` writes, and the command that puts it there. */
  def generatedSources: Path =
    LiqpPort.upstream.resolve("target/generated-sources/antlr4")

  private def regenerate: String =
    s"cd ${LiqpPort.upstream} && ./mvnw -q generate-sources"

  /** Guarantee the classpath file and the compiled parser exist, building both once if they do
    * not. Returns the file's path.
    *
    * THREE things are checked, not just the file:
    *
    *   - the parser CLASSES, both copies: the classpath line names the parser directory, so a
    *     cached line beside a `clean`ed `out/` is a classpath that resolves to nothing — exactly
    *     the failure mode the fatality above exists to prevent, arriving by the back door;
    *   - the COORDINATES the line was resolved from ([[ClasspathCache]]): this port's `pom.xml`
    *     pins six of them, including one that reads like a typo and is not, and a cache keyed on
    *     existence alone would answer a bump with the versions the port used to declare — an
    *     unresolvable import that resolves WRONGLY rather than failing. */
  def ensure(repoRoot: Path): Path =
    val out      = cache(repoRoot)
    val classes  = parserClasses(repoRoot)
    val upstream = upstreamClasses(repoRoot)
    val key      = ClasspathCache.key(Coordinates)
    if ClasspathCache.fresh(out, key) && hasParserClasses(classes) && hasParserClasses(upstream)
    then out
    else
      val jars = fetch(Coordinates)
      compileParser(jars, classes, parserOnly = true)
      compileParser(jars, upstream, parserOnly = false)
      ClasspathCache.write(out, (jars :+ classes.toString).mkString(File.pathSeparator), key)

  private def hasParserClasses(classes: Path): Boolean =
    val pkg = classes.resolve(ParserPackage)
    Files.isDirectory(pkg) && {
      val s = Files.list(pkg)
      try s.iterator.asScala.exists(_.getFileName.toString.endsWith(".class"))
      finally s.close()
    }

  /** the jars for these coordinates, through the mechanism every port shares
    * ([[balticporter.corpus.ClasspathCache]] — the `cs` invocation, the stream merge and the
    * jar-line filter, once).
    *
    * Takes its coordinates rather than reading [[Coordinates]] so that [[LiqpTestClasspath]] can
    * resolve the ONE test-scope coordinate the same way. */
  private[liqp] def fetch(coordinates: List[String]): List[String] =
    ClasspathCache.fetch("liqp", coordinates)

  /** javac the ANTLR output into `classes`, resolving liqp's own types from SOURCE.
    *
    * `parserOnly` decides whether liqp's own class files are WRITTEN as well as read — the whole
    * difference between the frontend's copy and the scala compile's. See the class doc for why the
    * frontend must have `true` and [[upstreamClasses]] for why the compile must have `false`. */
  private def compileParser(jars: List[String], classes: Path, parserOnly: Boolean): Unit =
    val gen = generatedSources
    val sources =
      if !Files.isDirectory(gen) then Nil
      else
        val s = Files.walk(gen)
        try s.iterator.asScala.filter(_.getFileName.toString.endsWith(".java")).map(_.toString).toList.sorted
        finally s.close()
    if sources.isEmpty then
      throw new IllegalStateException(
        s"""[liqp] the ANTLR-generated parser is NOT PRESENT at $gen.
           |
           |liqp's sources `import liquid.parser.v4.{LiquidLexer, LiquidParser, …}`, which are
           |generated by the antlr4-maven-plugin and are UNTRACKED — a fresh checkout has none.
           |A port cannot resolve them, and an unresolved import does not fail the frontend: it
           |resolves WRONGLY and the port emits nonsense and reports success (CLAUDE.md §5.1).
           |
           |Regenerate them and re-run:
           |
           |    $regenerate
           |""".stripMargin)

    val javaSrc = LiqpPort.upstream.resolve("src/main/java")
    if !Files.isDirectory(javaSrc) then
      throw new IllegalStateException(s"[liqp] upstream sources are not at $javaSrc")

    Files.createDirectories(classes)
    val cmd = List(
      "javac",
      // pinned rather than left to whatever JDK is current: the class files are read by the
      // FRONTEND (Spoon) as well as by scalac, and a class-file version newer than the frontend's
      // reader is a resolution failure that reports as an unresolved import.
      "--release", "17",
      "-nowarn",
      "-sourcepath", javaSrc.toString,
      "-d", classes.toString,
      "-cp", jars.mkString(File.pathSeparator),
    ) ++
      // do not write class files for the liqp sources javac reads to type-check the parser. Those
      // types must reach the frontend as SOURCE; a `liqp/…/X.class` beside them is a second, older
      // definition of every type this port emits. The scala compile's copy wants the opposite.
      (if parserOnly then List("-implicit:none") else Nil) ++ sources
    val proc = new ProcessBuilder(cmd*).redirectErrorStream(true).start()
    val raw  = new String(proc.getInputStream.readAllBytes()).trim
    if proc.waitFor() != 0 then
      throw new IllegalStateException(
        s"[liqp] could not compile the generated parser into $classes:\n$raw")
