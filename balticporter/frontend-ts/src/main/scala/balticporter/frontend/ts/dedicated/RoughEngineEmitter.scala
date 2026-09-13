package balticporter.frontend.ts.dedicated

import balticporter.frontend.ts.{RastFile, RastNode, Rast, TsToScalaEmitter}

/** Dedicated emitter for roughjs engine files (renderer.ts + generator.ts).
  *
  * Uses the generic TsToScalaEmitter with roughjs-specific config and
  * post-processing. Reads RAST fixtures — does NOT read hand-ported code.
  *
  * The post-processing transforms generic emitter output into correct Scala:
  * - Object literals { type: 'path', ops: [...] } → OpSet(OpSetType.path, ops.toVector)
  * - { op: 'move', data: [...] } → Op(OpType.move, Vector(...))
  * - JS truthiness patterns
  * - Class declarations (generator.ts)
  */
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
    val raw = TsToScalaEmitter.emit(List(rendererRast, generatorRast), config)

    // Post-process: apply roughjs-specific transformations
    raw.map { case (name, source) =>
      name -> postProcess(name, source)
    }
  }

  private def postProcess(name: String, source: String): String = {
    var s = source

    // Fix OpSet object literals: Map("type" -> "path", "ops" -> expr) → OpSet(OpSetType.path, expr.toVector)
    s = s.replaceAll("""Map\("type" -> "path", "ops" -> ([^)]+)\)""", """OpSet(OpSetType.path, $1.toVector)""")
    s = s.replaceAll("""Map\("type" -> "fillPath", "ops" -> ([^)]+)\)""", """OpSet(OpSetType.fillPath, $1.toVector)""")
    s = s.replaceAll("""Map\("type" -> "fillSketch", "ops" -> ([^)]+)\)""", """OpSet(OpSetType.fillSketch, $1.toVector)""")

    // Fix Op object literals: Map("op" -> "move", "data" -> Vector(x, y)) → Op(OpType.move, Vector(x, y))
    s = s.replaceAll("""Map\("op" -> "move", "data" -> ([^)]+\))\)""", """Op(OpType.move, $1)""")
    s = s.replaceAll("""Map\("op" -> "lineTo", "data" -> ([^)]+\))\)""", """Op(OpType.lineTo, $1)""")
    s = s.replaceAll("""Map\("op" -> "bcurveTo", "data" -> ([^)]+\))\)""", """Op(OpType.bcurveTo, $1)""")

    // Fix JS truthiness: (points || Vector.empty) → points (already non-null in Scala)
    s = s.replaceAll("""\((\w+) \|\| Vector\.empty\)""", "$1")

    // Fix EllipseParams object literal
    s = s.replaceAll("""Map\("rx" -> ([^,]+), "ry" -> ([^,]+), "increment" -> ([^)]+)\)""",
      """EllipseParams($1, $2, $3)""")

    // Fix Random.random → Random.random(seed)
    s = s.replace("Random(ops.seed)", "Random(ops.seed.toLong)")

    // Fix numTruthy — add before the closing brace of the object
    if (!s.contains("def numTruthy")) {
      val lastBrace = s.lastIndexOf("}")
      if (lastBrace >= 0) {
        s = s.substring(0, lastBrace) +
          "\n  private def numTruthy(d: Double): Boolean =\n    d != 0.0 && !d.isNaN\n}\n"
      }
    }

    s
  }
}
