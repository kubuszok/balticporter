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
    // manifest mapping entries beyond the path convention (DESIGN.md §3.5): renamed
    // counterparts, and files whose hand-port role was substituted by a documented
    // dependency replacement (docs/architecture/liqp-port.md).
    val renamedCounterparts = Map(
      "liqp/filters/Map.java" -> "filters/MapFilter.scala" // avoids scala.collection.Map clash
    )
    // manifest `Renames:` entries — engine-name → hand-port-name, applied before diffing
    val memberRenames: Map[String, Map[String, String]] = Map(
      "liqp/filters/Map.java" -> Map("Map" -> "MapFilter"),
      "liqp/nodes/OutputNode.java" -> Map("unparsedline" -> "unparsedLine"), // casing fixed in hand port
    )
    val substituted = Map(
      "liqp/parser/v4/NodeVisitor.java" -> "ANTLR visitor replaced by hand-written parser",
      "liqp/parser/Inspectable.java" -> "Jackson introspection replaced by LiquidSupport disposition",
      "liqp/parser/LiquidSupport.java" -> "Jackson replaced by LiquidSupport trait (ssg-data-commons DataView)",
    )

    val parsedAll = new SpoonFrontend().parseTolerant(cfg)
    val sentinels = balticporter.emit.SentinelRegistry.compute(parsedAll.collect { case (_, Right(u)) => u })
    val ctorReg = Some(new balticporter.emit.CtorRegistry(parsedAll.collect { case (_, Right(u)) => u }))
    val results = parsedAll.map { case (rel, parsed) =>
      val handRel = renamedCounterparts.getOrElse(rel, rel.stripPrefix("liqp/").stripSuffix(".java") + ".scala")
      val handPort = ssgRoot.resolve("ssg-liquid/src/main/scala/ssg/liquid/" + handRel)
      val status = parsed.flatMap(u => scala.util.Try(ScalaPrinter.print(u, prov, sentinels, ctorReg)).toEither.map(u -> _)) match
        case Right((u, out)) =>
          val lost = CommentCheck.check(u, out)
          if lost.nonEmpty then s"COMMENT_LOSS\t${lost.head.comment.take(140)}"
          else
            parityFailures(u, out, rel) match
              case Some(missing) => s"PARITY_FAIL\t$missing"
              case None =>
                if substituted.contains(rel) then s"SUBSTITUTED\t${substituted(rel)}"
                else if !Files.exists(handPort) then "NO_COUNTERPART"
                else skeletonStatus(out, Files.readString(handPort), rel, memberRenames.getOrElse(rel, Map.empty))
        case Left(e: Unsupported) => s"UNSUPPORTED\t${e.what}"
        case Left(e)              => s"ERROR\t${e.getClass.getSimpleName}: ${String.valueOf(e.getMessage).take(120)}"
      rel -> status
    }

    val outFile = repoRoot.resolve("out/liqp-corpus-report.tsv")
    Files.createDirectories(outFile.getParent)
    Files.writeString(outFile, results.map((f, s) => s"$f\t$s").mkString("", "\n", "\n"))

    val counts = results.groupBy(_._2.takeWhile(_ != '\t')).view.mapValues(_.length).toMap
    val skelDiffs = results.collect { case (f, s) if s.startsWith("SKEL_DIFF\t") => s"$f: ${s.split('\t').drop(1).mkString(" ")}" }
    if skelDiffs.nonEmpty then
      println("[corpus] sample skeleton diffs:")
      skelDiffs.take(8).foreach(d => println(s"[corpus]   ${d.take(180)}"))
    println(s"[corpus] status counts: " + counts.toList.sortBy(-_._2).map((k, v) => s"$k=$v").mkString(" "))
    val reasons = results.collect { case (_, s) if s.startsWith("UNSUPPORTED\t") => s.split('\t')(1) }
    println("[corpus] top unsupported reasons:")
    reasons.groupBy(identity).view.mapValues(_.length).toList.sortBy(-_._2).take(15).foreach { (r, n) =>
      println(f"[corpus]   $n%3d  $r")
    }
    println(s"[corpus] report: $outFile")

  /** Structural API-parity (the computed covenant): original surface ⊆ emitted output. */
  private def parityFailures(u: BUnit, out: String, rel: String): Option[String] =
    import balticporter.verify.{ApiParity, SkeletonDiff as SD}
    SD.parseSkeleton(out, s"engine:$rel") match
      case Left(err) => Some(s"emitted output unparseable: ${err.take(100)}")
      case Right(emitted) =>
        val missing = ApiParity.check(u, emitted)
        if missing.isEmpty then None else Some(missing.take(5).mkString("; "))

  /** Skeleton comparison vs the hand port (SkeletonDiff): the M1 convergence metric. */
  private def skeletonStatus(engineOut: String, handSrc: String, rel: String, renames: Map[String, String]): String =
    import balticporter.verify.SkeletonDiff as SD
    (SD.parseSkeleton(engineOut, s"engine:$rel"), SD.parseSkeleton(handSrc, s"hand:$rel")) match
      case (Right(e), Right(h)) =>
        val r = SD.compare(SD.applyRenames(e, renames), h)
        r.status match
          case SD.Status.SkeletonEqual => "SKEL_EQUAL"
          case SD.Status.Idiom         => "SKEL_IDIOM"
          case SD.Status.HandAdditions =>
            s"SKEL_HAND_ADDITIONS\t${r.extraInHand.take(4).mkString("; ")}"
          case SD.Status.Diff =>
            Ledger.lookup(rel) match
              case Some((fp, reason)) if fp == r.fingerprint => s"SKEL_ACCEPTED\t$reason"
              case Some((fp, _)) =>
                s"SKEL_DIFF\tledger stale: accepted fp=$fp, actual fp=${r.fingerprint}"
              case None =>
                val detail =
                  (r.missingInHand.take(3).map(m => s"engine-only:$m") ++ r.extraInHand.take(3).map(m => s"hand-only:$m"))
                    .mkString("; ")
                s"SKEL_DIFF\tfp=${r.fingerprint}\t$detail"
      case (Left(err), _) => s"SKEL_PARSE_ERROR\tengine: ${err.take(120)}"
      case (_, Left(err)) => s"SKEL_PARSE_ERROR\thand: ${err.take(120)}"

/** Accepted-divergence ledger: corpus/liqp-divergences.tsv
  * (rel_path \t diff-fingerprint \t verified reason). An entry only suppresses the
  * exact diff it was written for — fingerprint drift resurfaces as SKEL_DIFF.
  */
object Ledger:
  private lazy val entries: Map[String, (String, String)] =
    val p = Path
      .of(sys.props.getOrElse("balticporter.root", "."))
      .resolve("corpus/liqp-divergences.tsv")
    if !Files.exists(p) then Map.empty
    else
      Files
        .readAllLines(p)
        .asScala
        .toList
        .filterNot(l => l.startsWith("#") || l.isBlank)
        .flatMap { l =>
          l.split('\t') match
            case Array(rel, fp, reason) => Some(rel -> (fp, reason))
            case _                      => None
        }
        .toMap
  def lookup(rel: String): Option[(String, String)] = entries.get(rel)
