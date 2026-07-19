package balticporter.corpus

import balticporter.core.*
import balticporter.frontend.spoon.SpoonTir
import balticporter.tir.TypeRepr

import java.nio.file.{Files, Path}
import scala.jdk.CollectionConverters.*

/** Coverage burn-down for the SpoonTir populator: translate a whole corpus into the TIR
  * and tally which constructs still hit `Unsupported`. Drives full corpus coverage — run,
  * implement the top failure category, repeat until zero.
  *
  *   corpus-tests/runMain balticporter.corpus.SpoonTirCoverage [corpus] [N]
  *
  * corpus = liqp (default) | flexmark ; N = example failures to print per category.
  */
object SpoonTirCoverage:

  private final case class Corpus(name: String, cfg: FrontendConfig)

  def main(args: Array[String]): Unit =
    val corpusName = args.headOption.getOrElse("liqp")
    val examples   = args.lift(1).flatMap(_.toIntOption).getOrElse(2)
    val repoRoot   = Path.of(sys.props.getOrElse("balticporter.root", ".")).toAbsolutePath.normalize
    val corpus = corpusName match
      case "liqp"     => liqp(repoRoot)
      case "flexmark" => flexmark(repoRoot)
      case other      => sys.error(s"unknown corpus '$other' (liqp | flexmark)")
    run(corpus, examples)

  // -------------------------------------------------------------------------
  private def run(corpus: Corpus, examples: Int): Unit =
    println(s"[cov:${corpus.name}] building model over ${corpus.cfg.files.length} files…")
    val types   = SpoonTir.buildModel(corpus.cfg)
    val results = SpoonTir.coverage(types)
    val (ok, bad) = results.partition(_._2.isRight)

    println(s"[cov:${corpus.name}] top-level types: ${types.size}  ok: ${ok.size}  FAILED: ${bad.size}")

    def reason(t: Throwable): String = t match
      case u: Unsupported => normalize(u.getMessage)
      case other          => s"${other.getClass.getSimpleName}: ${Option(other.getMessage).getOrElse("")}".take(80)

    val byCategory = bad
      .collect { case (name, Left(e)) => reason(e) -> (name, e) }
      .groupBy(_._1)
      .view
      .mapValues(_.map(_._2))
      .toList
      .sortBy(-_._2.size)

    if bad.nonEmpty then
      println(s"\n[cov:${corpus.name}] failures by construct (${bad.size} types, ${byCategory.size} categories):")
      byCategory.foreach { case (cat, hits) =>
        println(f"  ${hits.size}%3d  $cat")
        hits.take(examples).foreach { case (typeName, e) => println(s"          e.g. $typeName — ${firstLine(e)}") }
      }
    else
      println(s"\n[cov:${corpus.name}] FULL CORPUS GREEN — every type translated with no Unsupported.")
      scala.util.Try {
        val program     = SpoonTir.fromTypes(types)
        val methods     = program.symbols.all.count(_.info.isInstanceOf[TypeRepr.MethodType])
        val withCallers = program.symbols.all.count(s => program.callersOf(s.id).nonEmpty)
        println(s"[cov:${corpus.name}] whole-program: units=${program.units.size} symbols=${program.symbols.all.size} " +
          s"methods=$methods methods-with-callers=$withCallers")
      }.recover { case e => println(s"[cov:${corpus.name}] whole-program build FAILED: ${firstLine(e)}") }.get

  // -------------------------------------------------------------------------
  /** liqp: done/skipped rows, vendored source tree resolves the corpus itself. */
  private def liqp(repoRoot: Path): Corpus =
    val ssgRoot    = repoRoot.resolve("../ssg").normalize
    val sourceRoot = ssgRoot.resolve("original-src/liqp/src/main/java")
    val files = migration(ssgRoot, "liqp") { case st => st == "done" || st == "skipped" }
      .map(_.stripPrefix("src/main/java/"))
      .filterNot(_ == "liqp/Examples.java")
      .sorted
    Corpus("liqp", FrontendConfig(sourceRoot, files, LiqpClasspath.resolve(repoRoot), List(sourceRoot)))

  /** flexmark: ported rows across ~30 Maven modules; resolution roots = each ported
    * module's src/main/java (same selection FlexmarkCorpus uses). */
  private def flexmark(repoRoot: Path): Corpus =
    val ssgRoot = repoRoot.resolve("../ssg").normalize
    val fmRoot  = ssgRoot.resolve("original-src/flexmark-java")
    val files = migration(ssgRoot, "flexmark") { case st => st == "ported" }
      .filter(p => Files.exists(fmRoot.resolve(p)))
      .sorted
    val moduleRoots = files.map(_.takeWhile(_ != '/')).toSet.toList.sorted
      .map(m => fmRoot.resolve(m).resolve("src/main/java"))
      .filter(Files.isDirectory(_))
    val cp = coursier("org.jetbrains:annotations:24.1.0", "org.nibor.autolink:autolink:0.6.0") ++
      LiqpClasspath.junitClasspath(repoRoot)
    Corpus("flexmark", FrontendConfig(fmRoot, files, cp, resolutionRoots = moduleRoots))

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
