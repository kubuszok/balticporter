package balticporter.corpus

import balticporter.core.*
import balticporter.emit.{ScalaPrinter, SentinelRegistry}
import balticporter.frontend.spoon.SpoonFrontend
import balticporter.vocab.PackageRenamePass

import java.nio.file.{Files, Path}
import scala.jdk.CollectionConverters.*

/** M5 sge-extension gate: translate the whole jbump upstream (dependency-free,
  * 19 files) through the engine with the Tier-3 package rename
  * (com.dongbat.jbump -> sge.jbump) and skeleton-diff against sge's hand port —
  * the second hand-port corpus acting as rule oracle, exactly as ssg-liquid was
  * for M1.
  */
object JbumpCorpus:

  def main(args: Array[String]): Unit =
    val repoRoot = Path.of(sys.props.getOrElse("balticporter.root", ".")).toAbsolutePath.normalize
    val sgeRoot = repoRoot.resolve("../sge").normalize
    val sourceRoot = sgeRoot.resolve("original-src/jbump/jbump/src")
    val handRoot = sgeRoot.resolve("sge-extension/jbump/src/main/scala/sge/jbump")

    val files = Files.walk(sourceRoot).iterator().asScala
      .filter(p => p.toString.endsWith(".java"))
      .map(p => sourceRoot.relativize(p).toString)
      .toList
      .sorted
    println(s"[jbump] ${files.length} upstream files")

    val upstreamCommit =
      val pb = new ProcessBuilder("git", "-C", sourceRoot.toString, "rev-parse", "HEAD").redirectErrorStream(true)
      val proc = pb.start()
      val out = new String(proc.getInputStream.readAllBytes()).trim
      if proc.waitFor() == 0 then out else "unknown"

    val cfg = FrontendConfig(sourceRoot, files, Nil, resolutionRoots = List(sourceRoot))
    val prov = Provenance("jbump", upstreamCommit, "MIT", "jbump/src")
    val passes: List[BirPass] = List(new PackageRenamePass("com.dongbat.jbump", "sge.jbump"))
    println(s"[jbump] passes: ${PassPipeline.fingerprint(passes)}")

    // hand-port dispositions (sge docs): Extra dropped; the GWT-era primitive
    // collections replaced by Scala collections in the hand port.
    val substituted = Map(
      "com/dongbat/jbump/Extra.java" -> "GWT reflection helper — dropped in sge",
      "com/dongbat/jbump/util/BooleanArray.java" -> "primitive collection replaced by Scala collections",
      "com/dongbat/jbump/util/FloatArray.java" -> "primitive collection replaced by Scala collections",
      "com/dongbat/jbump/util/IntArray.java" -> "primitive collection replaced by Scala collections",
      "com/dongbat/jbump/util/IntIntMap.java" -> "primitive collection replaced by Scala collections",
    )

    // engine refusals whose hand-port answer is a reduced covenant, not a rule
    val acceptedUnsupported = Map(
      "com/dongbat/jbump/util/MathUtils.java" ->
        "public field 'random' + random(...) overloads dropped by the sge covenant (only nearest/DELTA ported); engine refuses the public field-vs-method clash",
    )

    val parsedAll = new SpoonFrontend().parseTolerant(cfg)
    val translatedUnits = parsedAll.collect { case (_, Right(u)) => u }
    val sentinels = SentinelRegistry.compute(translatedUnits)
    val ctorReg = Some(new balticporter.emit.CtorRegistry(translatedUnits))

    val results = parsedAll.map { case (rel, parsed) =>
      val handRel = rel.stripPrefix("com/dongbat/jbump/").stripSuffix(".java") + ".scala"
      val handPort = handRoot.resolve(handRel)
      val status = parsed.flatMap(u =>
        scala.util.Try(ScalaPrinter.print(PassPipeline.run(passes, u), prov, sentinels, ctorReg)).toEither.map(u -> _)
      ) match
        case _ if substituted.contains(rel) => s"SUBSTITUTED\t${substituted(rel)}"
        case Right((u, out)) =>
          val lost = CommentCheck.check(u, out)
          if lost.nonEmpty then s"COMMENT_LOSS\t${lost.head.comment.take(140)}"
          else if !Files.exists(handPort) then "NO_COUNTERPART"
          else skeletonStatus(out, Files.readString(handPort), rel)
        case Left(e: Unsupported) =>
          acceptedUnsupported.get(rel) match
            case Some(reason) => s"UNSUPPORTED_ACCEPTED\t$reason"
            case None         => s"UNSUPPORTED\t${e.what}"
        case Left(e)              => s"ERROR\t${e.getClass.getSimpleName}: ${String.valueOf(e.getMessage).take(120)}"
      rel -> status
    }

    val outFile = repoRoot.resolve("out/jbump-corpus-report.tsv")
    Files.createDirectories(outFile.getParent)
    Files.writeString(outFile, results.map((f, s) => s"$f\t$s").mkString("", "\n", "\n"))

    val counts = results.groupBy(_._2.takeWhile(_ != '\t')).view.mapValues(_.length).toMap
    println(s"[jbump] status counts: " + counts.toList.sortBy(-_._2).map((k, v) => s"$k=$v").mkString(" "))
    results.collect { case (f, s) if s.startsWith("SKEL_DIFF") || s.startsWith("UNSUPPORTED") || s.startsWith("ERROR") =>
      println(s"[jbump]   ${f.split('/').last}: ${s.replace('\t', ' ').take(160)}")
    }
    println(s"[jbump] report: $outFile")

  private def skeletonStatus(engineOut: String, handSrc: String, rel: String): String =
    import balticporter.verify.SkeletonDiff as SD
    (SD.parseSkeleton(engineOut, s"engine:$rel"), SD.parseSkeleton(handSrc, s"hand:$rel")) match
      case (Right(e), Right(h)) =>
        val r = SD.compare(e, h)
        r.status match
          case SD.Status.SkeletonEqual => "SKEL_EQUAL"
          case SD.Status.Idiom         => "SKEL_IDIOM"
          case SD.Status.HandAdditions => s"SKEL_HAND_ADDITIONS\t${r.extraInHand.take(4).mkString("; ")}"
          case SD.Status.Diff =>
            JbumpLedger.lookup(rel) match
              case Some((fp, reason)) if fp == r.fingerprint => s"SKEL_ACCEPTED\t$reason"
              case Some((fp, _)) => s"SKEL_DIFF\tledger stale: accepted fp=$fp, actual fp=${r.fingerprint}"
              case None =>
                val detail =
                  (r.missingInHand.take(3).map(m => s"engine-only:$m") ++ r.extraInHand.take(3).map(m => s"hand-only:$m"))
                    .mkString("; ")
                s"SKEL_DIFF\tfp=${r.fingerprint}\t$detail"
      case (Left(err), _) => s"SKEL_PARSE_ERROR\tengine: ${err.take(120)}"
      case (_, Left(err)) => s"SKEL_PARSE_ERROR\thand: ${err.take(120)}"

/** Fingerprint-pinned accepted divergences: corpus/jbump-divergences.tsv */
object JbumpLedger:
  private lazy val entries: Map[String, (String, String)] =
    val p = Path
      .of(sys.props.getOrElse("balticporter.root", "."))
      .resolve("corpus/jbump-divergences.tsv")
    if !Files.exists(p) then Map.empty
    else
      Files.readAllLines(p).asScala.toList
        .filterNot(l => l.startsWith("#") || l.isBlank)
        .flatMap { l =>
          l.split('\t') match
            case Array(rel, fp, reason) => Some(rel -> (fp, reason))
            case _                      => None
        }
        .toMap
  def lookup(rel: String): Option[(String, String)] = entries.get(rel)
