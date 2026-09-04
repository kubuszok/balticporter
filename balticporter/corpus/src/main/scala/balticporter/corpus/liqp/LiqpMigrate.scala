package balticporter.corpus.liqp

import balticporter.corpus.ClasspathCache
import balticporter.runner.PortConfig

import java.io.File
import java.nio.file.{Files, Path}

import scala.jdk.CollectionConverters.*

/** Migrate **liqp** (`src/main/java`, 135 types -- a Java Liquid templating engine backed
  * by an ANTLR grammar).
  *
  *   corpus/runMain balticporter.corpus.liqp.LiqpMigrate [--determinism=full]
  *
  * The port is `balticporter/corpus/ports/liqp/main.conf` -- read that, not this file. This
  * is the `main` that names it and gives the run its report identity, plus
  * [[LiqpClasspath]] (a classpath is produced by a resolver and a compiler, which a
  * `.conf` cannot hold).
  *
  * First library outside the gdx/sge family: a THIRD-PARTY API surface (jackson, antlr4,
  * strftime4j) beyond the JDK/ported-module seams `CollectionsTransform` has priced
  * before; a `ServiceLoader` (`ENGINE-LIMITS.md` CT7 territory, for real rather than in a
  * fixture); a GENERATED PARSER it does not own (see [[LiqpClasspath]]); and a reference
  * port that is `../ssg/ssg-liquid`, not sge (CLAUDE.md §3.5).
  *
  * Milestone 1 is the SKELETON and the WALL: not expected to compile, so the first error
  * census is MEASURED and classified per CLAUDE.md §1 (`PROGRESS.md` §liqp holds the
  * numbers).
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

/** liqp's FRONTEND classpath: the six jars its `pom.xml` declares, plus the ANTLR-generated
  * parser compiled to class files.
  *
  * The generated parser (`liquid.parser.v4`, ~9432 lines under `src/main/antlr4`,
  * UNTRACKED) is resolved as a CLASSPATH input rather than a source root (D-liqp-1):
  * porting it through the engine is a later, separate milestone. A missing generated tree
  * is FATAL (CLAUDE.md §5.1) -- an unresolved `import liquid.parser.v4...` resolves
  * WRONGLY rather than failing outright.
  *
  * javac needs `-sourcepath`/`-implicit:none`, not a plain compile: `LiquidParser` and
  * `liqp` are mutually recursive (see [[rewriteReferences]] for D-liqp-1b, the WHICH
  * sourcepath).
  *
  * Coordinates are read verbatim from `pom.xml`, including the deliberate version split
  * (`jackson.databind.version` 2.13.4.2 vs `jackson.version` 2.15.0) -- `cs` then resolves
  * `jackson-datatype-jsr310`'s own dependency to promote databind to 2.15.0, a resolver
  * difference recorded rather than papered over.
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

  /** DECISION D-liqp-1b -- THE GENERATED PARSER IS REWRITTEN INTO THE EMITTED NAMESPACE
    * BEFORE JAVAC READS IT.
    *
    * D-liqp-1 (external classpath) and D-liqp-2 (`liqp -> ssg.liquid` package rename) CUT
    * EACH OTHER: `LiquidParser` has a member typed `liqp.TemplateParser.ErrorMode`, so an
    * un-rewritten reference hands the port's own type to a formal declared in the upstream
    * namespace. The generated parser is a BUILD PRODUCT, not a dependency, so its sources
    * are copied and their references into the ported library rewritten (package prefix,
    * and enum CONSTANT access -- a java enum becomes a Scala `sealed abstract class` +
    * companion, so `ErrorMode.LAX` compiles against the java shape and is a run-time
    * `NoSuchFieldError`; `.valueOf("LAX")` reaches the static forwarder correctly). javac
    * then resolves the rewritten references against a SHAPE-HONEST STUB (`javac-stub`,
    * read via `-sourcepath`, never written), so an un-rewritten reference is a compile
    * error there rather than a run-time one, and upstream `liqp` is on NO classpath of
    * this step at all. */
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

  /** Guarantee the classpath file and the compiled parser exist, building both once if
    * they do not.
    *
    * FOUR things are checked: the parser CLASSES exist (a cached line beside a
    * `clean`ed `out/` resolves to nothing); the COORDINATES ([[ClasspathCache]]) and
    * D-liqp-1b's REWRITE POLICY (a bump or policy change must not reuse classes compiled
    * under the old namespace); and the GENERATED SOURCES digest ([[generatedDigest]]) --
    * untracked and regenerated by `./mvnw generate-sources`, so a grammar change must
    * invalidate the cache even though the coordinates did not move. */
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

  /** the generated tree as ONE string -- every `.java` under it, by RELATIVE PATH and
    * CONTENT, in a stable sorted order (PATH catches an ANTLR rename; CONTENT catches a
    * rule-body edit). Digests an ABSENT tree to a stated value rather than throwing, so a
    * vanished tree does not answer "fresh" with the cache of the tree that was there. */
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

  /** the jars for these coordinates, through the shared
    * [[balticporter.corpus.ClasspathCache]] mechanism. Takes coordinates explicitly so
    * [[LiqpTestClasspath]] can resolve its one test-scope coordinate the same way. */
  private[liqp] def fetch(coordinates: List[String]): List[String] =
    ClasspathCache.fetch("liqp", coordinates)

  /** rewrite one generated source's references INTO the ported library (D-liqp-1b),
    * returning how many of each kind moved. Both rules cut only at a `.` (CLAUDE.md
    * §4.56); the caller refuses a rewrite that moved nothing at all.
    *
    * It is a TEXT rewrite and cannot distinguish CODE from a STRING LITERAL or COMMENT --
    * currently safe because the whole ANTLR output holds exactly ONE occurrence of the
    * string (`import liqp.TemplateParser;`), not guaranteed in general. A hit inside a
    * literal would need a lexer, not a cleverer regex, to tell the three apart. */
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

  /** javac the ANTLR output into `classes`, D-liqp-1b's rewrite first -- ONE output
    * directory, read by the frontend, scalac and the test run. */
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
