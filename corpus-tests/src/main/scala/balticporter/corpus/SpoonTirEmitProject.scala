package balticporter.corpus

import balticporter.core.FrontendConfig
import balticporter.emit.TirEmitter
import balticporter.frontend.spoon.SpoonTir
import balticporter.runner.M0Pipeline

import java.nio.file.{Files, Path}
import scala.jdk.CollectionConverters.*

/** Step-3 gate: emit a whole (small, dependency-free) library through the TIR to Scala and
  * run scalac over it. Burn-down harness — run, read the errors, fix the emitter, repeat.
  *
  *   corpus-tests/runMain balticporter.corpus.SpoonTirEmitProject [lib]   (default noise4j)
  */
object SpoonTirEmitProject:

  def main(args: Array[String]): Unit =
    val lib      = args.headOption.getOrElse("noise4j")
    val repoRoot = Path.of(sys.props.getOrElse("balticporter.root", ".")).toAbsolutePath.normalize
    val base     = repoRoot.resolve(s"../sge/original-src/$lib").normalize
    val files = Files.walk(base).iterator().asScala
      .filter(p => p.toString.endsWith(".java"))
      .map(p => base.relativize(p).toString)
      .filterNot(f => f.contains("/test/") || f.endsWith("package-info.java") || f.endsWith("module-info.java"))
      .toList.sorted

    val types   = SpoonTir.buildModel(FrontendConfig(base, files, Nil, Nil), lenient = true)
    val program = SpoonTir.fromTypes(types)
    val emitter = new TirEmitter(program)

    val outDir = repoRoot.resolve(s"out/tir-emit/$lib")
    if Files.exists(outDir) then Files.walk(outDir).iterator().asScala.toList.reverse.foreach(Files.delete)
    program.units.foreach { u =>
      val full = program.symbolOf(u.symbol).map(_.fullName).getOrElse("Unit")
      val rel  = full.replace('.', '/') + ".scala"
      val p    = outDir.resolve(rel)
      Files.createDirectories(p.getParent)
      Files.writeString(p, emitter.emitUnit(u))
    }
    println(s"[emit] $lib: ${program.units.size} units, ${program.symbols.all.size} symbols -> $outDir")

    M0Pipeline.compileGate("3.8.4", List(outDir)) match
      case Right(())  => println(s"[emit] $lib: SCALAC GREEN")
      case Left(err)  =>
        val errs = err.linesIterator.filter(l => l.contains("error") && l.contains(".scala")).toList
        println(s"[emit] $lib: scalac FAILED — ${errs.size} error line(s)")
        // tally by message signature
        val byKind = errs.groupBy(errSignature).view.mapValues(_.size).toList.sortBy(-_._2)
        byKind.foreach { case (sig, n) => println(f"  $n%3d  $sig") }
        println("\n--- first errors ---")
        err.linesIterator.filter(_.contains(".scala")).take(20).foreach(println)

  private def errSignature(line: String): String =
    val msg = line.split("error(:| )").drop(1).mkString(" ").trim
    msg.replaceAll("\"[^\"]*\"", "\"…\"").replaceAll("[A-Za-z0-9_.]+\\.scala:\\d+:\\d+", "").take(70)
