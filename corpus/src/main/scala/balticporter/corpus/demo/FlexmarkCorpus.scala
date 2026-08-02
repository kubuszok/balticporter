package balticporter.corpus.demo

import balticporter.core.*
import balticporter.emit.{ScalaPrinter, SentinelRegistry}
import balticporter.frontend.spoon.SpoonFrontend

import java.nio.file.{Files, Path}
import scala.jdk.CollectionConverters.*

/** M5 coverage baseline over the flexmark corpus (845 ported files, ~30 Maven
  * modules) — the measured worklist for the scale-up arc, exactly as LiqpCorpus
  * was for M1. Translate-only sweep; hand-port comparison comes once coverage
  * stabilizes.
  */
object FlexmarkCorpus:

  def main(args: Array[String]): Unit =
    val repoRoot = Path.of(sys.props.getOrElse("balticporter.root", ".")).toAbsolutePath.normalize
    val ssgRoot = repoRoot.resolve("../ssg").normalize
    val fmRoot = ssgRoot.resolve("original-src/flexmark-java")

    val rows = Files.readAllLines(ssgRoot.resolve(".rescale/data/migration.tsv")).asScala.toList
      .filterNot(_.startsWith("#"))
      .map(_.split('\t').toList)
      .collect { case "flexmark" :: sourcePath :: _ :: status :: _ if status == "ported" => sourcePath }
    val files = rows.filter(p => Files.exists(fmRoot.resolve(p))).sorted
    println(s"[fm] ${files.length} ported flexmark files (of ${rows.length} rows)")

    // resolution: only modules that contain ported files (skipped modules like
    // docx-converter pull heavyweight JVM-only deps and are never referenced)
    val portedModules = files.map(_.takeWhile(_ != '/')).toSet
    val moduleRoots = portedModules.toList.sorted
      .map(m => fmRoot.resolve(m).resolve("src/main/java"))
      .filter(Files.isDirectory(_))
    println(s"[fm] ${moduleRoots.length} module source roots")

    val annotationsJar =
      val pb = new ProcessBuilder(
        "cs", "fetch", "--classpath",
        "org.jetbrains:annotations:24.1.0",
        "org.nibor.autolink:autolink:0.6.0", // flexmark-ext-autolink
      ).redirectErrorStream(true)
      val proc = pb.start()
      val out = new String(proc.getInputStream.readAllBytes()).trim
      if proc.waitFor() != 0 then throw new RuntimeException(s"coursier fetch failed:\n$out")
      out.linesIterator.toList.last.split(java.io.File.pathSeparatorChar).toList.map(Path.of(_))

    // flexmark-test-util is a main-scope test-support module — junit belongs on the cp
    val cp = annotationsJar ++ LiqpClasspath.junitClasspath(repoRoot)
    val cfg = FrontendConfig(fmRoot, files, cp, resolutionRoots = moduleRoots)
    val prov = Provenance("flexmark-java", "baseline", "BSD-2-Clause", "flexmark-java")
    val parsed = new SpoonFrontend(ScoutPolicy.PreservedAnnotationPrefixes).parseTolerant(cfg)
    val sentinels = SentinelRegistry.compute(parsed.collect { case (_, Right(u)) => u })
    val ctorReg = Some(new balticporter.emit.CtorRegistry(parsed.collect { case (_, Right(u)) => u }))

    val results = parsed.map { case (rel, e) =>
      val status = if rel.endsWith("package-info.java") then "PACKAGE_INFO"
      else e.flatMap(u => scala.util.Try(ScalaPrinter.print(u, prov, sentinels, ctorReg)).toEither.map(u -> _)) match
        case Right((u, out)) =>
          val lost = CommentCheck.check(u, out)
          if lost.nonEmpty then s"COMMENT_LOSS\t${lost.length} lost; first: ${lost.head.comment.take(120)}" else "OK"
        case Left(err: Unsupported) => s"UNSUPPORTED\t${err.what}"
        case Left(err)              => s"ERROR\t${err.getClass.getSimpleName}: ${String.valueOf(err.getMessage).take(120)}"
      rel -> status
    }

    val outFile = repoRoot.resolve("out/flexmark-corpus-report.tsv")
    Files.createDirectories(outFile.getParent)
    Files.writeString(outFile, results.map((f, s) => s"$f\t$s").mkString("", "\n", "\n"))

    val counts = results.groupBy(_._2.takeWhile(_ != '\t')).view.mapValues(_.length).toMap
    println(s"[fm] status counts: " + counts.toList.sortBy(-_._2).map((k, v) => s"$k=$v").mkString(" "))
    val reasons = results.collect { case (_, s) if s.startsWith("UNSUPPORTED\t") => s.split('\t')(1) }
    println("[fm] top unsupported reasons:")
    reasons.groupBy(identity).view.mapValues(_.length).toList.sortBy(-_._2).take(15).foreach { (r, n) =>
      println(f"[fm]   $n%4d  $r")
    }
    println(s"[fm] report: $outFile")
