package balticporter.corpus

import balticporter.core.*
import balticporter.emit.ScalaPrinter
import balticporter.frontend.spoon.SpoonFrontend

import java.nio.file.Path

/** Debug helper: emit one file to stdout. Usage: runMain ... DebugEmit <rel-path> */
object DebugEmit:
  def main(args: Array[String]): Unit =
    val repoRoot = Path.of(sys.props.getOrElse("balticporter.root", ".")).toAbsolutePath.normalize
    val sourceRoot = repoRoot.resolve("../ssg/original-src/liqp/src/main/java").normalize
    val cfg = FrontendConfig(sourceRoot, List(args(0)), LiqpClasspath.resolve(repoRoot), List(sourceRoot))
    val prov = Provenance("Liqp", "debug", "MIT", "liqp/src/main/java")
    val unit = new SpoonFrontend().parse(cfg).head
    println(ScalaPrinter.print(unit, prov))
