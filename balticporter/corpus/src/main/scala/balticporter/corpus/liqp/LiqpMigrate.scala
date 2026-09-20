package balticporter.corpus.liqp

import balticporter.corpus.ClasspathCache
import balticporter.runner.PortConfig

import java.io.File
import java.nio.file.{ Files, Path }

import scala.jdk.CollectionConverters.*

/** Migrate **liqp** (`src/main/java`, 135 types — a Java Liquid templating engine backed by an ANTLR grammar): `.../ports/liqp/main.conf`, plus [[LiqpClasspath]]. First library outside gdx/sge:
  * THIRD-PARTY API surface (jackson, antlr4, strftime4j), a `ServiceLoader` (reflectively instantiated framework territory), a GENERATED PARSER it does not own, and a reference port that is
  * `ssg-liquid`, not sge. Milestone 1 is the SKELETON — the error census is MEASURED.
  */
object LiqpMigrate:

  def main(args: Array[String]): Unit =
    LiqpClasspath.ensure(LiqpPort.repoRoot)
    PortConfig.load(LiqpPort.conf("main.conf"), args.toSeq).execute()

/** Where this port's configuration lives, and where its upstream is, for the `main`s that name them.
  */
object LiqpPort:

  def repoRoot: Path =
    Path.of(sys.props.getOrElse("balticporter.root", ".")).toAbsolutePath.normalize

  def conf(name: String): Path = repoRoot.resolve("balticporter/corpus/ports/liqp").resolve(name)

  /** liqp's upstream checkout — a git SUBMODULE of ssg, not of sge like every other corpus library. Stated once here and once, conf-relatively, in `main.conf`; the two must agree, and the lane
    * compares nothing, so a change is a change in both places.
    */
  def upstream: Path = repoRoot.resolve("../ssg/original-src/liqp").normalize

/** liqp's FRONTEND classpath: the six jars its `pom.xml` declares, plus the ANTLR-generated parser compiled to class files. The generated parser (`liquid.parser.v4`, ~9432 lines, UNTRACKED) is
  * resolved as a CLASSPATH input rather than a source root (D-liqp-1). javac needs `-sourcepath`/`-implicit:none`: `LiquidParser`/`liqp` are mutually recursive (see [[rewriteReferences]] for
  * D-liqp-1b). Coordinates read verbatim from `pom.xml`.
  */
object LiqpClasspath:

  /** exactly what `pom.xml` declares at compile scope. `junit:junit:4.13.1` is TEST scope and belongs to the test port's classpath, not this one.
    *
    * Five of these six are FRONTEND-ONLY now — antlr4-runtime, strftime4j and the three jackson artifacts are on no compile classpath the port emits against, because the port replaces each of them
    * with hand-written Scala. They stay here because the frontend still has to RESOLVE those names inside the java declarations that policy re-points.
    */
  val Coordinates: List[String] = List(
    "org.antlr:antlr4-runtime:4.13.0",
    "com.fasterxml.jackson.core:jackson-core:2.15.0",
    "com.fasterxml.jackson.core:jackson-databind:2.13.4.2",
    "com.fasterxml.jackson.core:jackson-annotations:2.15.0",
    "com.fasterxml.jackson.datatype:jackson-datatype-jsr310:2.15.0",
    "ua.co.k:strftime4j:1.0.6"
  )

  /** the ANTLR output's package, and the only package this object's javac step may write. */
  val ParserPackage = "liquid/parser/v4"

  /** the file the liqp configurations read as `@work/liqp-classpath.txt`. */
  val FileName = "liqp-classpath.txt"

  def cache(repoRoot:         Path): Path = repoRoot.resolve("out").resolve(FileName)
  def parserClasses(repoRoot: Path): Path = parserClassesIn(repoRoot.resolve("out"))

  /** the rewritten copy of the generated sources javac actually reads (D-liqp-1b). A BUILD PRODUCT of a build product: never edited, deleted and rewritten on every rebuild.
    */
  def parserSources(repoRoot: Path): Path = parserSourcesIn(repoRoot.resolve("out"))

  private def parserClassesIn(work: Path): Path = work.resolve("liqp-parser-classes")
  private def parserSourcesIn(work: Path): Path = work.resolve("liqp-parser-src")

  /** DECISION D-liqp-1b — THE GENERATED PARSER IS REWRITTEN INTO THE EMITTED NAMESPACE BEFORE JAVAC READS IT. D-liqp-1 (external classpath) and D-liqp-2 (`liqp -> ssg.liquid` rename) CUT EACH OTHER:
    * an un-rewritten reference hands the port's own type to an upstream-namespace formal. The generated parser is a BUILD PRODUCT, copied with references rewritten (package prefix, enum CONSTANT
    * access); javac resolves against a SHAPE-HONEST STUB (`javac-stub`).
    */
  private val LibraryPackage = "liqp"

  /** the emitted namespace, D-liqp-2's `packageRenames { liqp = "ssg.liquid" }`. Stated here and in `main.conf`; the two must agree, and nothing compares them.
    */
  private val EmittedPackage = "ssg.liquid"

  /** the ported library's java ENUMs the generated parser names CONSTANTS of, as the parser spells the type. Data rather than a derivation: which of a library's types are enums is knowledge about
    * that library, and a "any SCREAMING_CASE selector" rule would rewrite every `static final` constant in reach.
    */
  private val EnumTypes: List[String] = List("TemplateParser.ErrorMode")

  /** where the `antlr4-maven-plugin` writes, under the caller's own upstream checkout. */
  def generatedSourcesIn(upstream: Path): Path =
    upstream.resolve("target/generated-sources/antlr4")

  def generatedSources: Path = generatedSourcesIn(LiqpPort.upstream)

  /** the shape-honest stub javac resolves the rewritten references against — read, never written. Lives beside this port's configurations, so it is found under the CORPUS root, whether that is a
    * checkout's `balticporter/corpus` or the directory the published jar unpacks into.
    */
  def stubSourcesIn(corpus: Path): Path =
    corpus.resolve("ports/liqp/javac-stub")

  def stubSources(repoRoot: Path): Path =
    stubSourcesIn(repoRoot.resolve("balticporter/corpus"))

  private def regenerate(upstream: Path): String =
    s"cd $upstream && ./mvnw -q generate-sources"

  /** Guarantee the classpath file and the compiled parser exist, building both once if they do not. FOUR things are checked: the parser CLASSES exist; the COORDINATES ([[ClasspathCache]]);
    * D-liqp-1b's REWRITE POLICY (a bump must not reuse classes compiled under the old namespace); and the GENERATED SOURCES digest ([[generatedDigest]]) — untracked, regenerated by
    * `./mvnw generate-sources`, so a grammar change must invalidate the cache too.
    */
  def ensure(repoRoot: Path): Path =
    ensureIn(repoRoot.resolve("out"), LiqpPort.upstream, repoRoot.resolve("balticporter/corpus"))

  /** The same, with each of its three inputs named: `work` is the directory the configurations get as their `work` root and the ONLY place this writes, `upstream` is the caller's own liqp checkout,
    * and `corpus` is where this port's configurations live — a checkout's `balticporter/corpus`, or the tree the published jar unpacks. A consumer has none of this repository's layout.
    */
  def ensureIn(work: Path, upstream: Path, corpus: Path): Path =
    val out     = work.resolve(FileName)
    val classes = parserClassesIn(work)
    val gen     = generatedSourcesIn(upstream)
    val key     = s"${ClasspathCache.key(Coordinates)} || $rewritePolicy || ${generatedDigest(gen)}"
    if ClasspathCache.fresh(out, key) && hasParserClasses(classes) then out
    else
      val sources = requiredInputs(upstream, corpus)
      val jars    = fetch(Coordinates)
      compileParser(work, upstream, sources, stubSourcesIn(corpus), jars, classes)
      ClasspathCache.write(out, (jars :+ classes.toString).mkString(File.pathSeparator), key)

  /** D-liqp-1b as a fingerprint — every value the rewrite is driven by, in order. Not passed as [[ClasspathCache.key]]'s `extraArgs`, which means "arguments `cs` was invoked with": this is not a
    * resolver input, it is a property of what javac then produced FROM the resolved jars.
    */
  private[liqp] def rewritePolicy: String =
    (s"$LibraryPackage->$EmittedPackage" :: EnumTypes.map("enum:" + _)).mkString(" ")

  /** the generated tree as ONE string -- every `.java` under it, by RELATIVE PATH and CONTENT, in a stable sorted order (PATH catches an ANTLR rename; CONTENT catches a rule-body edit). Digests an
    * ABSENT tree to a stated value rather than throwing, so a vanished tree does not answer "fresh" with the cache of the tree that was there.
    */
  private[liqp] def generatedDigest(gen: Path): String =
    val body =
      if !Files.isDirectory(gen) then "<absent>"
      else
        val s  = Files.walk(gen)
        val fs =
          try s.iterator.asScala.filter(_.getFileName.toString.endsWith(".java")).toList.sortBy(_.toString)
          finally s.close()
        fs.map(f => s"${gen.relativize(f)} ${Files.readString(f)}").mkString("")
    val md = java.security.MessageDigest.getInstance("SHA-256")
    md.digest(body.getBytes(java.nio.charset.StandardCharsets.UTF_8)).take(16).map(b => f"${b & 0xff}%02x").mkString

  private def hasParserClasses(classes: Path): Boolean =
    val pkg = classes.resolve(ParserPackage)
    Files.isDirectory(pkg) && {
      val s = Files.list(pkg)
      try s.iterator.asScala.exists(_.getFileName.toString.endsWith(".class"))
      finally s.close()
    }

  /** the jars for these coordinates, through the shared [[balticporter.corpus.ClasspathCache]] mechanism. Takes coordinates explicitly so [[LiqpTestClasspath]] can resolve its one test-scope
    * coordinate the same way.
    */
  private[liqp] def fetch(coordinates: List[String]): List[String] =
    ClasspathCache.fetch("liqp", coordinates)

  /** rewrite one generated source's references INTO the ported library (D-liqp-1b), returning how many of each kind moved. Both rules cut only at a `.`; the caller refuses a rewrite that moved
    * nothing. It is a TEXT rewrite and cannot distinguish CODE from a STRING LITERAL or COMMENT — currently safe since the whole output holds exactly ONE occurrence of the string, not guaranteed in
    * general.
    */
  private[liqp] def rewriteReferences(text: String): (String, Int, Int) =
    val pkg                 = java.util.regex.Pattern.compile(raw"(?<![\p{L}\p{N}_$$.])" + java.util.regex.Pattern.quote(LibraryPackage) + raw"\.")
    val (afterPkg, pkgHits) = replaceCounting(pkg, text, EmittedPackage + ".")

    val (afterEnums, enumHits) = EnumTypes.foldLeft((afterPkg, 0)) { case ((t, n), typePath) =>
      val re = java.util.regex.Pattern.compile(
        raw"(?<![\p{L}\p{N}_$$.])((?:[\p{L}\p{N}_$$]+\.)*)" +
          typePath.split('.').map(java.util.regex.Pattern.quote).mkString(raw"\.") +
          raw"\.([A-Z][A-Z0-9_]*)(?![\p{L}\p{N}_$$(])"
      )
      val (out, hits) = replaceCounting(re, t, "$1" + typePath + ".valueOf(\"$2\")")
      (out, n + hits)
    }
    (afterEnums, pkgHits, enumHits)

  private def replaceCounting(re: java.util.regex.Pattern, text: String, replacement: String): (String, Int) =
    val m  = re.matcher(text)
    val sb = new java.lang.StringBuilder
    var n  = 0
    while m.find() do
      n += 1
      m.appendReplacement(sb, replacement)
    m.appendTail(sb)
    (sb.toString, n)

  /** The generated parser's sources, after checking BOTH inputs the caller supplies — asked before anything is resolved, so a wrong path is refused by path on a machine that cannot resolve jars at
    * all.
    */
  private def requiredInputs(upstream: Path, corpus: Path): List[Path] =
    val gen     = generatedSourcesIn(upstream)
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
           |resolves WRONGLY and the port emits nonsense and reports success.
           |
           |Regenerate them and re-run:
           |
           |    ${regenerate(upstream)}
           |""".stripMargin
      )

    val stub = stubSourcesIn(corpus)
    if !Files.isDirectory(stub) then throw new IllegalStateException(s"[liqp] D-liqp-1b's javac stub is not at $stub — javac cannot resolve $EmittedPackage")
    sources

  private def compileParser(work: Path, upstream: Path, sources: List[Path], stub: Path, jars: List[String], classes: Path): Unit =
    val gen = generatedSourcesIn(upstream)

    // the rewritten copy, rebuilt from scratch: a stale file here is a parser compiled from a
    // policy that is no longer this port's. Under `work` like everything else this writes.
    val rewrittenSources = parserSourcesIn(work)
    deleteTree(rewrittenSources)
    Files.createDirectories(rewrittenSources)
    var pkgTotal  = 0
    var enumTotal = 0
    val rewritten = sources.map { src =>
      val (text, pkgHits, enumHits) = rewriteReferences(Files.readString(src))
      pkgTotal += pkgHits
      enumTotal += enumHits
      val dst = rewrittenSources.resolve(gen.relativize(src).toString)
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
           |""".stripMargin
      )
    println(
      s"[liqp] D-liqp-1b: $pkgTotal package reference(s) and $enumTotal enum constant(s) " +
        s"rewritten to $EmittedPackage across ${sources.size} generated sources"
    )

    Files.createDirectories(classes)
    val cmd = List(
      "javac",
      // pinned rather than left to whatever JDK is current: the class files are read by the
      // FRONTEND (Spoon) as well as by scalac, and a class-file version newer than the frontend's
      // reader is a resolution failure that reports as an unresolved import.
      "--release",
      "17",
      "-nowarn",
      // the shape-honest stub, READ and never written — `-implicit:none` is what keeps
      // `ssg/liquid/TemplateParser.class` out of the output, where it would be a second definition
      // of a type this port emits.
      "-sourcepath",
      stub.toString,
      "-implicit:none",
      "-d",
      classes.toString,
      "-cp",
      jars.mkString(File.pathSeparator)
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
           |compiling that form would be a `NoSuchFieldError` at run time instead.""".stripMargin
      )

  private def deleteTree(dir: Path): Unit =
    if Files.exists(dir) then
      val s = Files.walk(dir)
      try s.sorted(java.util.Comparator.reverseOrder()).iterator.asScala.foreach(Files.delete)
      finally s.close()
