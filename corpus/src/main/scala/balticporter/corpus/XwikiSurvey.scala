package balticporter.corpus

import balticporter.core.*
import balticporter.emit.{ScalaPrinter, SentinelRegistry}
import balticporter.frontend.spoon.SpoonFrontend

import java.nio.file.{Files, Path}
import scala.jdk.CollectionConverters.*

/** M6 candidate survey: translate-readiness of the flexmark-ext-xwiki-macros
  * dependency closure (the cold-port target + everything its build needs).
  * Reports per-module status counts and every blocker file.
  */
object XwikiSurvey:

  val closureModules: List[String] = List(
    "flexmark-ext-xwiki-macros", // the cold target (ssg skipped it entirely)
    "flexmark",                  // core parser/renderer
    "flexmark-util",             // aggregate facade
    "flexmark-util-ast",
    "flexmark-util-builder",
    "flexmark-util-collection",
    "flexmark-util-data",
    "flexmark-util-dependency",
    "flexmark-util-format",
    "flexmark-util-html",
    "flexmark-util-misc",
    "flexmark-util-options",
    "flexmark-util-sequence",
    "flexmark-util-visitor",
    "flexmark-test-util",        // spec-test framework (main scope)
  )

  def main(args: Array[String]): Unit =
    val repoRoot = Path.of(sys.props.getOrElse("balticporter.root", ".")).toAbsolutePath.normalize
    val ssgRoot = repoRoot.resolve("../ssg").normalize
    val fmRoot = ssgRoot.resolve("original-src/flexmark-java")

    val moduleRoots = closureModules
      .map(m => m -> fmRoot.resolve(m).resolve("src/main/java"))
      .filter((_, p) => Files.isDirectory(p))
    println(s"[xwiki] ${moduleRoots.length} closure modules present")

    val files = moduleRoots.flatMap { (m, root) =>
      Files.walk(root).iterator().asScala
        .filter(p => p.toString.endsWith(".java"))
        .map(p => fmRoot.relativize(p).toString)
    }.sorted
    println(s"[xwiki] ${files.length} closure files")

    val annotationsJar =
      val pb = new ProcessBuilder("cs", "fetch", "--classpath", "org.jetbrains:annotations:24.1.0")
        .redirectErrorStream(true)
      val proc = pb.start()
      val out = new String(proc.getInputStream.readAllBytes()).trim
      if proc.waitFor() != 0 then throw new RuntimeException(s"coursier fetch failed:\n$out")
      out.linesIterator.toList.last.split(java.io.File.pathSeparatorChar).toList.map(Path.of(_))

    val cp = annotationsJar ++ LiqpClasspath.junitClasspath(repoRoot)
    val cfg = FrontendConfig(fmRoot, files, cp, resolutionRoots = moduleRoots.map(_._2))
    val prov = Provenance("flexmark-java", "survey", "BSD-2-Clause", "flexmark-java")
    val parsed = new SpoonFrontend().parseTolerant(cfg)
    val units = parsed.collect { case (_, Right(u)) => u }
    val sentinels = SentinelRegistry.compute(units)
    val ctorReg = Some(new balticporter.emit.CtorRegistry(units))

    val results = parsed.map { case (rel, e) =>
      val status = if rel.endsWith("package-info.java") then "PACKAGE_INFO"
      else e.flatMap(u => scala.util.Try(ScalaPrinter.print(u, prov, sentinels, ctorReg, XwikiOverrides.map)).toEither.map(u -> _)) match
        case Right((u, out)) =>
          if CommentCheck.check(u, out).nonEmpty then "COMMENT_LOSS" else "OK"
        case Left(err: Unsupported) => s"UNSUPPORTED\t${err.what}"
        case Left(err)              => s"ERROR\t${err.getClass.getSimpleName}: ${String.valueOf(err.getMessage).take(120)}"
      rel -> status
    }

    val outFile = repoRoot.resolve("out/xwiki-survey-report.tsv")
    Files.createDirectories(outFile.getParent)
    Files.writeString(outFile, results.map((f, s) => s"$f\t$s").mkString("", "\n", "\n"))

    val counts = results.groupBy(_._2.takeWhile(_ != '\t')).view.mapValues(_.length).toMap
    println(s"[xwiki] status counts: " + counts.toList.sortBy(-_._2).map((k, v) => s"$k=$v").mkString(" "))
    results.collect { case (f, s) if s.startsWith("UNSUPPORTED") || s.startsWith("ERROR") || s == "COMMENT_LOSS" =>
      println(s"[xwiki]   BLOCKER ${f}: ${s.replace('\t', ' ').take(140)}")
    }
    println(s"[xwiki] report: $outFile")
