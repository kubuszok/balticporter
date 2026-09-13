package balticporter.frontend.ts.dedicated

import balticporter.frontend.ts.{RastFile, RastNode, Rast, TsToScalaEmitter}

/** Dedicated emitter for roughjs engine files (renderer.ts + generator.ts).
  *
  * Uses the generic TsToScalaEmitter with roughjs-specific config and
  * post-processing rules. Reads RAST fixtures — does NOT read hand-ported code. */
object RoughEngineEmitter {

  def emit(rendererRast: RastFile, generatorRast: RastFile): Map[String, String] = {
    val config = TsToScalaEmitter.EmitConfig(
      packageName = "ssg.graphs.commons.rough",
      imports = List(
        "scala.collection.mutable.ArrayBuffer",
        "scala.util.boundary",
        "scala.util.boundary.break",
        "ssg.graphs.commons.rough.curve.Point",
        "ssg.graphs.commons.rough.pathdata.{Parser, Absolutize, Normalize, Segment}",
        "ssg.graphs.commons.rough.fillers.{getFiller, HachureFill}",
      ),
      braceStyle = true,
      skipIndex = false,
      fileNameMap = Map(
        "renderer" -> "RoughRenderer",
        "generator" -> "RoughGenerator",
      ),
      tupleTypeOverrides = Map("Point" -> "Point"),
      tupleFieldOverrides = Map("Point" -> Map(0 -> "x", 1 -> "y")),
    )
    TsToScalaEmitter.emit(List(rendererRast, generatorRast), config)
  }
}
