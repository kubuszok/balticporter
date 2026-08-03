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
  * The port is `balticporter/corpus/ports/liqp/main.conf` — read that, not this file. What is here is the
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

  def conf(name: String): Path = repoRoot.resolve("balticporter/corpus/ports/liqp").resolve(name)

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
  * mutually recursive. A SOURCEPATH resolves that and `-implicit:none` keeps javac from writing
  * class files for what it read there, so the output directory holds the 84 class files of
  * `liquid/parser/v4` and NOTHING else. That distinction is the whole point: liqp's own types must
  * reach the frontend as SOURCE, from the port's `sourceRoot`, and a second class file of a ported
  * type on any classpath here is an older definition of something this port emits.
  *
  * WHICH sourcepath is [[rewriteReferences]]'s decision D-liqp-1b, read there: not liqp's own
  * sources any more, but a shape-honest stub of the ONE type the parser reaches back into, at the
  * name the port EMITS it under.
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

  /** the rewritten copy of the generated sources javac actually reads (D-liqp-1b). A BUILD PRODUCT
    * of a build product: never edited, deleted and rewritten on every rebuild. */
  def parserSources(repoRoot: Path): Path = repoRoot.resolve("out/liqp-parser-src")

  /** DECISION D-liqp-1b — THE GENERATED PARSER IS REWRITTEN INTO THE EMITTED NAMESPACE BEFORE JAVAC
    * READS IT.
    *
    * D-liqp-1 and D-liqp-2 CUT EACH OTHER, and this is where the cut is answered. Treating the
    * generated parser as external keeps it at `liquid.parser.v4`, compiled against upstream `liqp`;
    * renaming the port to `ssg.liquid` means nothing named `liqp` is emitted. `LiquidParser` has a
    * member typed `liqp.TemplateParser.ErrorMode`, so the port's own call sites hand a
    * `ssg.liquid.TemplateParser.ErrorMode` to a formal declared `liqp.TemplateParser.ErrorMode` —
    * three compile errors that no manifest key can close, because none of them rewrites an ARGUMENT.
    *
    * But the generated parser is not a DEPENDENCY: it is a BUILD PRODUCT of this port's own build
    * step, regenerated from the grammars under `src/main/antlr4` on demand and untracked. (Spelt in
    * words: a slash-star-star inside a Scala doc comment OPENS a nested comment and swallows the
    * rest of the file — CLAUDE.md §4.58's own rule, and it cost this file a compile.) A build
    * product may be
    * built against whatever this build is producing — so the sources are copied, their references
    * INTO the ported library are rewritten to the emitted namespace, and javac compiles THAT.
    *
    * ==Two rewrites, and the second is the one that is easy to get silently wrong==
    *   1. the PACKAGE, `liqp.` -> `ssg.liquid.`, cut only at a `.` (CLAUDE.md §4.56). The parser's
    *      own package `liquid.parser.v4` is untouched, and so is any identifier that merely
    *      CONTAINS `liqp` — a prefix is not a structural fact about anything;
    *   2. the ENUM CONSTANT ACCESS. The port emits a java enum as a Scala `sealed abstract class`
    *      plus a companion `object` of `case object`s, and Scala's static forwarders put
    *      `values()`/`valueOf(String)` on the companion CLASS while each constant is a static field
    *      of the MODULE class (`…$ErrorMode$`). So `ErrorMode.LAX` compiles against a java enum and
    *      is a `NoSuchFieldError` at RUN time, which is precisely the failure a compile cannot see
    *      (CLAUDE.md §3). `ErrorMode.valueOf("LAX")` reaches the forwarder, returns the singleton,
    *      and `==` still holds — measured.
    *
    * ==What javac resolves `ssg.liquid.TemplateParser` against==
    * `balticporter/corpus/ports/liqp/javac-stub`, handed to javac as a `-sourcepath` with `-implicit:none`, so
    * it is READ and never WRITTEN: the output directory holds `liquid/parser/v4` and nothing else,
    * and no second definition of a ported type can reach the frontend or scalac. The stub is
    * SHAPE-HONEST about the Scala that stands at that FQN at run time — it declares no constants,
    * so an un-rewritten `ErrorMode.LAX` is a javac error rather than a run-time one.
    *
    * ==And the gate is javac itself==
    * Upstream `liqp` is on NO classpath of this step any more — not as sources, not as classes — so
    * a reference the rewrite missed cannot resolve and the build refuses. That replaces the whole
    * of the previous arrangement: `out/liqp-upstream-classes` (upstream liqp compiled beside the
    * parser, for scalac only) existed solely so that the seam arrived as a type error instead of as
    * `AssertionError: failure to resolve inner class` out of `ClassfileParser`, and there is no seam
    * left for it to soften. ONE directory of class files now serves the frontend, scalac and the
    * test run. */
  private val LibraryPackage = "liqp"

  /** the emitted namespace, D-liqp-2's `packageRenames { liqp = "ssg.liquid" }`. Stated here and in
    * `main.conf`; the two must agree, and nothing compares them. */
  private val EmittedPackage = "ssg.liquid"

  /** the ported library's java ENUMs the generated parser names CONSTANTS of, as the parser spells
    * the type. Data rather than a derivation: which of a library's types are enums is knowledge
    * about that library, and a "any SCREAMING_CASE selector" rule would rewrite every `static final`
    * constant in reach. */
  private val EnumTypes: List[String] = List("TemplateParser.ErrorMode")

  /** where the `antlr4-maven-plugin` writes, and the command that puts it there. */
  def generatedSources: Path =
    LiqpPort.upstream.resolve("target/generated-sources/antlr4")

  /** the shape-honest stub javac resolves the rewritten references against — read, never written. */
  def stubSources(repoRoot: Path): Path =
    repoRoot.resolve("balticporter/corpus/ports/liqp/javac-stub")

  private def regenerate: String =
    s"cd ${LiqpPort.upstream} && ./mvnw -q generate-sources"

  /** Guarantee the classpath file and the compiled parser exist, building both once if they do
    * not. Returns the file's path.
    *
    * FOUR things are checked, not just the file:
    *
    *   - the parser CLASSES: the classpath line names the parser directory, so a cached line beside
    *     a `clean`ed `out/` is a classpath that resolves to nothing — exactly the failure mode the
    *     fatality above exists to prevent, arriving by the back door;
    *   - the COORDINATES the line was resolved from ([[ClasspathCache]]): this port's `pom.xml`
    *     pins six of them, including one that reads like a typo and is not, and a cache keyed on
    *     existence alone would answer a bump with the versions the port used to declare — an
    *     unresolvable import that resolves WRONGLY rather than failing. The key carries D-liqp-1b's
    *     REWRITE POLICY beside them, because the classes those coordinates produce depend on it: a
    *     cache keyed on coordinates alone would reuse parser classes compiled against the OTHER
    *     namespace, which is the same silent-wrong-resolution failure one hop out;
    *   - …and the GENERATED SOURCES ([[generatedDigest]]), which is the input javac actually reads
    *     and was the one the key did not name. The tree is untracked and regenerated by
    *     `./mvnw generate-sources`, so a grammar change produces new `.java` under an UNCHANGED key
    *     beside a `parserClasses` directory that still holds `.class` files — and
    *     [[hasParserClasses]] is an existence test. The port then resolves against a parser it no
    *     longer has, which is this cache's own stated failure (an import that resolves WRONGLY
    *     rather than failing) one input further in, and nothing reports it: the port compiles and
    *     every count is flat. */
  def ensure(repoRoot: Path): Path =
    val out     = cache(repoRoot)
    val classes = parserClasses(repoRoot)
    val key     = s"${ClasspathCache.key(Coordinates)} || $rewritePolicy || ${generatedDigest(generatedSources)}"
    if ClasspathCache.fresh(out, key) && hasParserClasses(classes) then out
    else
      val jars = fetch(Coordinates)
      compileParser(repoRoot, jars, classes)
      ClasspathCache.write(out, (jars :+ classes.toString).mkString(File.pathSeparator), key)

  /** D-liqp-1b as a fingerprint — every value the rewrite is driven by, in order. Not passed as
    * [[ClasspathCache.key]]'s `extraArgs`, which means "arguments `cs` was invoked with": this is
    * not a resolver input, it is a property of what javac then produced FROM the resolved jars. */
  private[liqp] def rewritePolicy: String =
    (s"$LibraryPackage->$EmittedPackage" :: EnumTypes.map("enum:" + _)).mkString(" ")

  /** the generated tree as ONE string — every `.java` under it, by RELATIVE PATH and by CONTENT,
    * in a stable order.
    *
    * Both halves are load-bearing and each covers what the other misses. CONTENT alone misses a
    * RENAME (ANTLR renames a generated class when the grammar's own name changes, and javac then
    * produces a class file at a name nothing imports); the PATH SET alone misses every edit to a
    * rule body, which is the ordinary case. Sorted, because `Files.walk` order is a filesystem
    * fact and a key that depends on one is a key two checkouts disagree about.
    *
    * An ABSENT tree digests to a stated value rather than throwing, and a DISTINCT one: this is
    * consulted on a freshness question, before [[compileParser]]'s refusal, which is the right
    * place for the fatality because it can print the command that fixes it. A tree that vanished
    * must not answer "fresh" with the cache of the tree that was there. */
  private[liqp] def generatedDigest(gen: Path): String =
    val body =
      if !Files.isDirectory(gen) then "<absent>"
      else
        val s = Files.walk(gen)
        val fs =
          try s.iterator.asScala.filter(_.getFileName.toString.endsWith(".java")).toList.sortBy(_.toString)
          finally s.close()
        fs.map(f => s"${gen.relativize(f)} ${Files.readString(f)}").mkString("")
    val md = java.security.MessageDigest.getInstance("SHA-256")
    md.digest(body.getBytes(java.nio.charset.StandardCharsets.UTF_8))
      .take(16).map(b => f"${b & 0xff}%02x").mkString

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

  /** rewrite one generated source's references INTO the ported library (D-liqp-1b), and say how
    * many of each kind moved.
    *
    * Both rules cut only at a `.` (CLAUDE.md §4.56). The package rule matches `liqp.` only where a
    * qualified name STARTS — the lookbehind rejects `myliqp.` and `other.liqp.`, and nothing
    * matches a bare identifier that merely contains the string. The enum rule matches a
    * SCREAMING_CASE selector off one of [[EnumTypes]] and nothing wider, so a `static final`
    * constant on any other type is untouched.
    *
    * Returned counts are printed and the caller refuses a rewrite that moved nothing at all — a
    * build step whose policy fires nowhere is a decision that silently did not happen.
    *
    * ==WHAT THIS REWRITE CANNOT SEE, and why that is currently safe==
    * It is a TEXT rewrite over java source, so it does not distinguish CODE from a STRING LITERAL
    * or a COMMENT. A `"liqp.Foo"` inside a literal would be rewritten exactly as an import is, and
    * nothing downstream could report it: javac is happy either way, the port compiles, and the only
    * symptom is a runtime string — a reflective class name, a generated error message, a token
    * table entry — that names a class nobody has. That is `CLAUDE.md` §3's shape arriving through a
    * build step.
    *
    * It is safe TODAY as an observation about the input, not as a property of the rule: the whole
    * ANTLR output holds **exactly one** occurrence of the string, `import liqp.TemplateParser;` on
    * line 5 of `LiquidParser.java`, and zero inside a literal or a comment. A generated parser is
    * the one input where that is plausible to stay true — its literals are the grammar's own token
    * text — but it is not guaranteed, and the shape to watch is a `_LITERAL_NAMES`/`_SYMBOLIC_NAMES`
    * table or a `@header {}` block carrying java source through into a string.
    *
    * If a hit ever appears, the fix is NOT a cleverer regex: a lexer that tells the three apart is
    * the only thing that can, and that is javac's job, not a `Pattern`'s. The honest answer would be
    * to rewrite the generated `import` line ALONE and leave every other occurrence to fail loudly
    * against the stub, which is what makes the current one-hit input worth re-deriving rather than
    * assuming. */
  private[liqp] def rewriteReferences(text: String): (String, Int, Int) =
    val pkg = java.util.regex.Pattern.compile(
      raw"(?<![\p{L}\p{N}_$$.])" + java.util.regex.Pattern.quote(LibraryPackage) + raw"\.")
    val (afterPkg, pkgHits) = replaceCounting(pkg, text, EmittedPackage + ".")

    val (afterEnums, enumHits) = EnumTypes.foldLeft((afterPkg, 0)) { case ((t, n), typePath) =>
      val re = java.util.regex.Pattern.compile(
        raw"(?<![\p{L}\p{N}_$$.])((?:[\p{L}\p{N}_$$]+\.)*)" +
          typePath.split('.').map(java.util.regex.Pattern.quote).mkString(raw"\.") +
          raw"\.([A-Z][A-Z0-9_]*)(?![\p{L}\p{N}_$$(])")
      val (out, hits) = replaceCounting(re, t, "$1" + typePath + ".valueOf(\"$2\")")
      (out, n + hits)
    }
    (afterEnums, pkgHits, enumHits)

  private def replaceCounting(
      re: java.util.regex.Pattern, text: String, replacement: String): (String, Int) =
    val m   = re.matcher(text)
    val sb  = new java.lang.StringBuilder
    var n   = 0
    while m.find() do
      n += 1
      m.appendReplacement(sb, replacement)
    m.appendTail(sb)
    (sb.toString, n)

  /** javac the ANTLR output into `classes`, D-liqp-1b's rewrite first.
    *
    * ONE output directory, read by the frontend, by scalac and by the test run. What used to be two
    * — the frontend's parser-only copy and a scalac copy carrying upstream `liqp` beside it — was
    * the price of compiling the parser against a namespace the port does not emit, and the rewrite
    * is what removes that price rather than softening it. */
  private def compileParser(repoRoot: Path, jars: List[String], classes: Path): Unit =
    val gen = generatedSources
    val sources =
      if !Files.isDirectory(gen) then Nil
      else
        val s = Files.walk(gen)
        try s.iterator.asScala.filter(_.getFileName.toString.endsWith(".java")).toList.sortBy(_.toString)
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

    val stub = stubSources(repoRoot)
    if !Files.isDirectory(stub) then
      throw new IllegalStateException(
        s"[liqp] D-liqp-1b's javac stub is not at $stub — javac cannot resolve $EmittedPackage")

    // the rewritten copy, rebuilt from scratch: a stale file here is a parser compiled from a
    // policy that is no longer this port's.
    val work = parserSources(repoRoot)
    deleteTree(work)
    Files.createDirectories(work)
    var pkgTotal  = 0
    var enumTotal = 0
    val rewritten = sources.map { src =>
      val (text, pkgHits, enumHits) = rewriteReferences(Files.readString(src))
      pkgTotal += pkgHits
      enumTotal += enumHits
      val dst = work.resolve(gen.relativize(src).toString)
      Files.createDirectories(dst.getParent)
      Files.writeString(dst, text)
      dst.toString
    }
    if pkgTotal + enumTotal == 0 then
      throw new IllegalStateException(
        s"""[liqp] D-liqp-1b rewrote NOTHING in ${sources.size} generated sources.
           |
           |The decision is that the generated parser's references INTO the ported library are
           |moved to the emitted namespace before javac reads them, and this run found none to
           |move. Either the grammar stopped referencing `$LibraryPackage.` — in which case delete
           |D-liqp-1b — or the rewrite's own rules stopped matching, which nothing else can report:
           |javac would then simply succeed against a parser nobody renamed.
           |""".stripMargin)
    println(s"[liqp] D-liqp-1b: $pkgTotal package reference(s) and $enumTotal enum constant(s) " +
      s"rewritten to $EmittedPackage across ${sources.size} generated sources")

    Files.createDirectories(classes)
    val cmd = List(
      "javac",
      // pinned rather than left to whatever JDK is current: the class files are read by the
      // FRONTEND (Spoon) as well as by scalac, and a class-file version newer than the frontend's
      // reader is a resolution failure that reports as an unresolved import.
      "--release", "17",
      "-nowarn",
      // the shape-honest stub, READ and never written — `-implicit:none` is what keeps
      // `ssg/liquid/TemplateParser.class` out of the output, where it would be a second definition
      // of a type this port emits.
      "-sourcepath", stub.toString,
      "-implicit:none",
      "-d", classes.toString,
      "-cp", jars.mkString(File.pathSeparator),
    ) ++ rewritten
    val proc = new ProcessBuilder(cmd*).redirectErrorStream(true).start()
    val raw  = new String(proc.getInputStream.readAllBytes()).trim
    if proc.waitFor() != 0 then
      throw new IllegalStateException(
        s"""[liqp] could not compile the generated parser into $classes:
           |$raw
           |
           |Upstream `$LibraryPackage` is on NO classpath of this step (D-liqp-1b), so a reference
           |the rewrite did not move cannot resolve, and an enum CONSTANT the rewrite did not move
           |is rejected by the stub on purpose — the emitted Scala has no such static field, and
           |compiling that form would be a `NoSuchFieldError` at run time instead.""".stripMargin)

  private def deleteTree(dir: Path): Unit =
    if Files.exists(dir) then
      val s = Files.walk(dir)
      try s.sorted(java.util.Comparator.reverseOrder()).iterator.asScala.foreach(Files.delete)
      finally s.close()
