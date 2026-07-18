package balticporter.corpus

import balticporter.core.*
import balticporter.frontend.spoon.SpoonTir
import balticporter.tir.TypeRepr

import java.nio.file.{Files, Path}
import scala.jdk.CollectionConverters.*

/** Coverage burn-down for the SpoonTir populator: translate every done/skipped Liqp file
  * into the TIR and tally which constructs still hit `Unsupported`. Drives full corpus
  * coverage — run, implement the top failure category, repeat until zero.
  *
  *   corpus-tests/runMain balticporter.corpus.SpoonTirCoverage [N]
  *
  * N (optional) = how many example failures to print per category.
  */
object SpoonTirCoverage:

  def main(args: Array[String]): Unit =
    val examples = args.headOption.flatMap(_.toIntOption).getOrElse(2)
    val repoRoot = Path.of(sys.props.getOrElse("balticporter.root", ".")).toAbsolutePath.normalize
    val ssgRoot  = repoRoot.resolve("../ssg").normalize
    val sourceRoot = ssgRoot.resolve("original-src/liqp/src/main/java")

    val files = Files.readAllLines(ssgRoot.resolve(".rescale/data/migration.tsv")).asScala.toList
      .filterNot(_.startsWith("#"))
      .map(_.split('\t').toList)
      .collect { case "liqp" :: sp :: _ :: st :: _ if st == "done" || st == "skipped" => sp }
      .map(_.stripPrefix("src/main/java/"))
      .filterNot(_ == "liqp/Examples.java")
      .sorted

    val cfg = FrontendConfig(sourceRoot, files, LiqpClasspath.resolve(repoRoot), List(sourceRoot))
    println(s"[cov] building model over ${files.length} liqp files…")
    val types = SpoonTir.buildModel(cfg)
    val results = SpoonTir.coverage(types)
    val (ok, bad) = results.partition(_._2.isRight)

    println(s"[cov] top-level types: ${types.size}  ok: ${ok.size}  FAILED: ${bad.size}")

    // normalize each failure to a category key (the construct), keep an example location.
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

    println(s"\n[cov] failures by construct (${bad.size} types across ${byCategory.size} categories):")
    byCategory.foreach { case (cat, hits) =>
      println(f"  ${hits.size}%3d  $cat")
      hits.take(examples).foreach { case (typeName, e) =>
        println(s"          e.g. $typeName — ${firstLine(e)}")
      }
    }
    if bad.isEmpty then
      println("\n[cov] FULL CORPUS GREEN — every type translated with no Unsupported.")
      // stronger gate: build ONE whole-program Program (cross-type symbols resolve, one
      // xref over the entire corpus) and sanity-check a few whole-program queries.
      scala.util.Try {
        val program = SpoonTir.fromTypes(types)
        val calls   = program.symbols.all.count(s => s.info.isInstanceOf[TypeRepr.MethodType])
        val withCallers = program.symbols.all.count(s => program.callersOf(s.id).nonEmpty)
        println(s"[cov] whole-program: units=${program.units.size} symbols=${program.symbols.all.size} " +
          s"methods=$calls methods-with-callers=$withCallers")
      }.recover { case e => println(s"[cov] whole-program build FAILED: ${firstLine(e)}") }.get

  /** collapse the message to its construct, dropping file:line and specific names. */
  private def normalize(msg: String): String =
    val body = msg.split("unsupported construct: ").lastOption.getOrElse(msg)
    body.replaceAll("Impl$", "").replaceAll("Impl ", " ").trim

  private def firstLine(t: Throwable): String =
    Option(t.getMessage).map(_.linesIterator.next()).getOrElse(t.getClass.getSimpleName)
