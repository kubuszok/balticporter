package balticporter.corpus

import balticporter.core.*
import balticporter.frontend.spoon.SpoonTir
import balticporter.tir.TypeRepr

import java.nio.file.{Files, Path}
import scala.jdk.CollectionConverters.*

/** Coverage burn-down for the SpoonTir populator: translate whole corpora into the TIR and
  * tally which constructs still hit `Unsupported`. Drives full coverage across every Java
  * library that sge and ssg port — run, implement the top failure category, repeat.
  *
  *   corpus/runMain balticporter.corpus.SpoonTirCoverage [corpus] [N]
  *
  * corpus = liqp | flexmark | sge | all (default liqp) ; N = example failures per category.
  * sge is a lenient multi-library sweep (each libGDX-ecosystem library modeled on its own).
  */
object SpoonTirCoverage:

  private final case class Corpus(name: String, cfg: FrontendConfig, lenient: Boolean = false)

  def main(args: Array[String]): Unit =
    val corpusName = args.headOption.getOrElse("liqp")
    val examples   = args.lift(1).flatMap(_.toIntOption).getOrElse(2)
    val repoRoot   = Path.of(sys.props.getOrElse("balticporter.root", ".")).toAbsolutePath.normalize
    corpusName match
      case "liqp"     => runOne(liqp(repoRoot), examples)
      case "flexmark" => runOne(flexmark(repoRoot), examples)
      case "sge"      => runMany("sge", sge(repoRoot), examples)
      case "all"      => runMany("all", List(liqp(repoRoot), flexmark(repoRoot)) ++ sge(repoRoot), examples)
      case other      => sys.error(s"unknown corpus '$other' (liqp | flexmark | sge | all)")

  // -------------------------------------------------------------------------
  private def measure(c: Corpus): (Int, Int, List[(String, Throwable)]) =
    scala.util.Try {
      val types     = SpoonTir.buildModel(c.cfg, c.lenient)
      val (ok, bad) = SpoonTir.coverage(types).partition(_._2.isRight)
      (types.size, ok.size, bad.collect { case (n, Left(e)) => (n, e) })
    }.recover { case e => (0, 0, List((c.name + " [model]", e))) }.get

  private def runOne(c: Corpus, examples: Int): Unit =
    println(s"[cov:${c.name}] building model over ${c.cfg.files.length} files…")
    val (total, ok, bad) = measure(c)
    println(s"[cov:${c.name}] top-level types: $total  ok: $ok  FAILED: ${bad.size}")
    tally(c.name, bad, examples)
    if bad.isEmpty then
      scala.util.Try {
        val program     = SpoonTir.buildModel(c.cfg, c.lenient)
        val prog        = SpoonTir.fromTypes(program)
        val methods     = prog.symbols.all.count(_.info.isInstanceOf[TypeRepr.MethodType])
        val withCallers = prog.symbols.all.count(s => prog.callersOf(s.id).nonEmpty)
        println(s"[cov:${c.name}] whole-program: units=${prog.units.size} symbols=${prog.symbols.all.size} " +
          s"methods=$methods methods-with-callers=$withCallers")
      }.recover { case e => println(s"[cov:${c.name}] whole-program build FAILED: ${firstLine(e)}") }.get

  private def runMany(label: String, corpora: List[Corpus], examples: Int): Unit =
    println(s"[cov:$label] sweeping ${corpora.size} libraries…")
    val allBad = List.newBuilder[(String, Throwable)]
    var totTypes = 0
    var totOk    = 0
    corpora.foreach { c =>
      val (t, ok, bad) = measure(c)
      totTypes += t; totOk += ok
      allBad ++= bad
      println(f"  ${c.name}%-26s files=${c.cfg.files.length}%4d types=$t%4d ok=$ok%4d failed=${t - ok}%3d")
    }
    val bad = allBad.result()
    println(s"\n[cov:$label] TOTAL types=$totTypes ok=$totOk FAILED=${bad.size}")
    tally(label, bad, examples)

  private def tally(label: String, bad: List[(String, Throwable)], examples: Int): Unit =
    if bad.isEmpty then
      println(s"[cov:$label] GREEN — every type translated with no Unsupported.")
    else
      def reason(t: Throwable): String = t match
        case u: Unsupported => normalize(u.getMessage)
        case other          => s"${other.getClass.getSimpleName}: ${Option(other.getMessage).getOrElse("")}".take(80)
      val byCategory = bad.groupBy((_, e) => reason(e)).view.mapValues(identity).toList.sortBy(-_._2.size)
      println(s"[cov:$label] failures by construct (${bad.size} types, ${byCategory.size} categories):")
      byCategory.foreach { case (cat, hits) =>
        println(f"  ${hits.size}%3d  $cat")
        hits.take(examples).foreach { case (typeName, e) => println(s"          e.g. $typeName — ${firstLine(e)}") }
      }

  // -------------------------------------------------------------------------
  /** liqp: done/skipped rows, vendored source tree resolves the corpus itself. */
  private def liqp(repoRoot: Path): Corpus =
    val ssgRoot    = repoRoot.resolve("../ssg").normalize
    val sourceRoot = ssgRoot.resolve("original-src/liqp/src/main/java")
    val files = migration(ssgRoot, "liqp") { st => st == "done" || st == "skipped" }
      .map(_.stripPrefix("src/main/java/"))
      .filterNot(_ == "liqp/Examples.java")
      .sorted
    Corpus("liqp", FrontendConfig(sourceRoot, files, LiqpClasspath.resolve(repoRoot), List(sourceRoot)))

  /** flexmark: ported rows across ~30 Maven modules; resolution roots = ported modules. */
  private def flexmark(repoRoot: Path): Corpus =
    val ssgRoot = repoRoot.resolve("../ssg").normalize
    val fmRoot  = ssgRoot.resolve("original-src/flexmark-java")
    val files = migration(ssgRoot, "flexmark") { st => st == "ported" }
      .filter(p => Files.exists(fmRoot.resolve(p)))
      .sorted
    val moduleRoots = files.map(_.takeWhile(_ != '/')).toSet.toList.sorted
      .map(m => fmRoot.resolve(m).resolve("src/main/java"))
      .filter(Files.isDirectory(_))
    val cp = coursier("org.jetbrains:annotations:24.1.0", "org.nibor.autolink:autolink:0.6.0") ++
      LiqpClasspath.junitClasspath(repoRoot)
    Corpus("flexmark", FrontendConfig(fmRoot, files, cp, resolutionRoots = moduleRoots))

  /** sge: the libGDX-ecosystem libraries. Each `original-src/<lib>` is swept on its own,
    * leniently (resolve intra-library from source; tolerate unconfigured native deps).
    * A few libraries vendor multiple modules/backends/versions with duplicate class names
    * that break Spoon's model build — pin those to their canonical module root. */
  private val sgeRoot: Map[String, String] =
    Map("gdx-ai" -> "gdx-ai/src", "libgdx" -> "gdx/src", "textratypist" -> "src/main/java")

  private def sge(repoRoot: Path): List[Corpus] =
    val root = repoRoot.resolve("../sge/original-src").normalize
    if !Files.isDirectory(root) then Nil
    else
      Files.list(root).iterator().asScala.filter(Files.isDirectory(_)).toList
        .sortBy(_.getFileName.toString)
        .map { lib =>
          val base = sgeRoot.get(lib.getFileName.toString).map(lib.resolve).filter(Files.isDirectory(_)).getOrElse(lib)
          // resolutionRoots stay empty: a root is added as a WHOLE-directory resource, which
          // would re-introduce the emu/backend/test duplicates javaFiles filters out. The
          // filtered file list is added individually and still cross-resolves (lenient).
          Corpus(s"sge/${lib.getFileName}", FrontendConfig(base, javaFiles(base), Nil, resolutionRoots = Nil), lenient = true)
        }
        .filter(_.cfg.files.nonEmpty)

  /** every main `.java` under `root` (relative), excluding tests, platform emulation, and
    * package/module descriptors. */
  private def javaFiles(root: Path): List[String] =
    if !Files.isDirectory(root) then Nil
    else
      val skip = List("/test/", "/tests/", "/gwt/", "/emu/", "/android/", "/ios/", "/lwjgl/",
        "/lwjgl3/", "/robovm/", "/backends/", "/desktop/", "/headless/", "/server/", "/demo/",
        "/demos/", "/examples/", "/tools/", "/build/", "/versions/")
      Files.walk(root).iterator().asScala
        .filter(p => p.toString.endsWith(".java"))
        .map(p => root.relativize(p).toString)
        .filterNot(f => f.endsWith("package-info.java") || f.endsWith("module-info.java"))
        .filterNot(f => skip.exists(("/" + f).contains))
        .toList
        .sorted

  /** source paths from the ssg migration ledger for `project` whose status passes `keep`. */
  private def migration(ssgRoot: Path, project: String)(keep: String => Boolean): List[String] =
    Files.readAllLines(ssgRoot.resolve(".rescale/data/migration.tsv")).asScala.toList
      .filterNot(_.startsWith("#"))
      .map(_.split('\t').toList)
      .collect { case `project` :: sp :: _ :: st :: _ if keep(st) => sp }

  private def coursier(deps: String*): List[Path] =
    val pb   = new ProcessBuilder((List("cs", "fetch", "--classpath") ++ deps)*).redirectErrorStream(true)
    val proc = pb.start()
    val out  = new String(proc.getInputStream.readAllBytes()).trim
    if proc.waitFor() != 0 then throw new RuntimeException(s"coursier fetch failed:\n$out")
    out.linesIterator.toList.last.split(java.io.File.pathSeparatorChar).toList.map(Path.of(_))

  private def normalize(msg: String): String =
    val body = msg.split("unsupported construct: ").lastOption.getOrElse(msg)
    body.replaceAll("Impl$", "").replaceAll("Impl ", " ").trim

  private def firstLine(t: Throwable): String =
    Option(t.getMessage).map(_.linesIterator.next()).getOrElse(t.getClass.getSimpleName)
