package balticporter.corpus

import balticporter.core.*
import balticporter.emit.ScalaPrinter
import balticporter.frontend.spoon.SpoonFrontend

import java.nio.file.{Files, Path}
import scala.jdk.CollectionConverters.*

/** M1 coverage baseline: translate every `done` Liqp file from ssg's migration
  * manifest, per-file tolerant, and report:
  *   - OK              translated, comments preserved
  *   - COMMENT_LOSS    translated but the preservation invariant failed
  *   - UNSUPPORTED     frontend/printer refused a construct (with reason)
  *   - NO_COUNTERPART  translated but no hand-ported file at the mapped path
  *
  * Writes out/liqp-corpus-report.tsv (sorted, deterministic) and prints a
  * histogram of unsupported reasons — that histogram is the M1 worklist.
  */
object LiqpCorpus:

  def main(args: Array[String]): Unit =
    val repoRoot = Path.of(sys.props.getOrElse("balticporter.root", ".")).toAbsolutePath.normalize
    val ssgRoot = repoRoot.resolve("../ssg").normalize
    val sourceRoot = ssgRoot.resolve("original-src/liqp/src/main/java")

    // migration manifest: source_lib \t source_path \t ssg_path \t status \t module ...
    val rows = Files.readAllLines(ssgRoot.resolve(".rescale/data/migration.tsv")).asScala.toList
      .filterNot(_.startsWith("#"))
      .map(_.split('\t').toList)
      .collect { case "liqp" :: sourcePath :: _ :: status :: _ if status == "done" => sourcePath }
    val files = rows.map(_.stripPrefix("src/main/java/")).sorted

    println(s"[corpus] ${files.length} done-status Liqp files")

    val cfg = FrontendConfig(sourceRoot, files, LiqpClasspath.resolve(repoRoot), resolutionRoots = List(sourceRoot))
    val prov = Provenance("Liqp", LiqpClasspath.upstreamCommit(repoRoot), "MIT", "liqp/src/main/java")
    val results = new SpoonFrontend().parseTolerant(cfg).map { case (rel, parsed) =>
      val handPort = ssgRoot.resolve(
        "ssg-liquid/src/main/scala/ssg/liquid/" +
          rel.stripPrefix("liqp/").stripSuffix(".java") + ".scala"
      )
      val status = parsed.flatMap(u => scala.util.Try(ScalaPrinter.print(u, prov)).toEither.map(u -> _)) match
        case Right((u, out)) =>
          if CommentCheck.check(u, out).nonEmpty then "COMMENT_LOSS"
          else if !Files.exists(handPort) then "NO_COUNTERPART"
          else "OK"
        case Left(e: Unsupported) => s"UNSUPPORTED\t${e.what}"
        case Left(e)              => s"ERROR\t${e.getClass.getSimpleName}: ${String.valueOf(e.getMessage).take(120)}"
      rel -> status
    }

    val outFile = repoRoot.resolve("out/liqp-corpus-report.tsv")
    Files.createDirectories(outFile.getParent)
    Files.writeString(outFile, results.map((f, s) => s"$f\t$s").mkString("", "\n", "\n"))

    val counts = results.groupBy(_._2.takeWhile(_ != '\t')).view.mapValues(_.length).toMap
    println(s"[corpus] status counts: " + counts.toList.sortBy(-_._2).map((k, v) => s"$k=$v").mkString(" "))
    val reasons = results.collect { case (_, s) if s.startsWith("UNSUPPORTED\t") => s.split('\t')(1) }
    println("[corpus] top unsupported reasons:")
    reasons.groupBy(identity).view.mapValues(_.length).toList.sortBy(-_._2).take(15).foreach { (r, n) =>
      println(f"[corpus]   $n%3d  $r")
    }
    println(s"[corpus] report: $outFile")
