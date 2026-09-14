package balticporter.corpus.roughjs

import balticporter.frontend.ts.dedicated.{ DefmethodBodyTranslator, DefmethodEntry, DefnodeClass, FreeFunction }

/** Dedicated emitter for roughjs core.ts → Core.scala. */
object RoughCoreEmitter {
  def emit(): String = {
    val sb = new StringBuilder
    sb.append("package ssg.graphs.commons.rough\n\n")
    sb.append("import lowlevel.Nullable\n\n")

    sb.append("final val SVGNS: String = \"http://www.w3.org/2000/svg\"\n\n")

    sb.append("enum OpType(val value: String) {\n")
    sb.append("  case move extends OpType(\"move\")\n")
    sb.append("  case bcurveTo extends OpType(\"bcurveTo\")\n")
    sb.append("  case lineTo extends OpType(\"lineTo\")\n")
    sb.append("}\n\n")

    sb.append("enum OpSetType(val value: String) {\n")
    sb.append("  case path extends OpSetType(\"path\")\n")
    sb.append("  case fillPath extends OpSetType(\"fillPath\")\n")
    sb.append("  case fillSketch extends OpSetType(\"fillSketch\")\n")
    sb.append("}\n\n")

    sb.append("final case class Config(options: Option[Options] = None)\n\n")
    sb.append("final case class DrawingSurface(width: Double, height: Double)\n\n")

    sb.append("final case class Options(\n")
    val optFields = List(
      ("maxRandomnessOffset", "Double"),
      ("roughness", "Double"),
      ("bowing", "Double"),
      ("stroke", "String"),
      ("strokeWidth", "Double"),
      ("curveFitting", "Double"),
      ("curveTightness", "Double"),
      ("curveStepCount", "Double"),
      ("fill", "String"),
      ("fillStyle", "String"),
      ("fillWeight", "Double"),
      ("hachureAngle", "Double"),
      ("hachureGap", "Double"),
      ("simplification", "Double"),
      ("dashOffset", "Double"),
      ("dashGap", "Double"),
      ("zigzagOffset", "Double")
    )
    for ((name, tpe) <- optFields)
      sb.append(s"  $name: Option[$tpe] = None,\n")
    sb.append("  seed: Option[Int] = None,\n")
    sb.append("  strokeLineDash: Option[Vector[Double]] = None,\n")
    sb.append("  strokeLineDashOffset: Option[Double] = None,\n")
    sb.append("  fillLineDash: Option[Vector[Double]] = None,\n")
    sb.append("  fillLineDashOffset: Option[Double] = None,\n")
    sb.append("  disableMultiStroke: Option[Boolean] = None,\n")
    sb.append("  disableMultiStrokeFill: Option[Boolean] = None,\n")
    sb.append("  preserveVertices: Option[Boolean] = None,\n")
    sb.append("  fixedDecimalPlaceDigits: Option[Double] = None,\n")
    sb.append("  fillShapeRoughnessGain: Option[Double] = None\n")
    sb.append(")\n\n")

    sb.append("final case class ResolvedOptions(\n")
    val reqFields = List(
      ("maxRandomnessOffset", "Double"),
      ("roughness", "Double"),
      ("bowing", "Double"),
      ("stroke", "String"),
      ("strokeWidth", "Double"),
      ("curveFitting", "Double"),
      ("curveTightness", "Double"),
      ("curveStepCount", "Double"),
      ("fillStyle", "String"),
      ("fillWeight", "Double"),
      ("hachureAngle", "Double"),
      ("hachureGap", "Double"),
      ("dashOffset", "Double"),
      ("dashGap", "Double"),
      ("zigzagOffset", "Double")
    )
    for ((name, tpe) <- reqFields)
      sb.append(s"  $name: $tpe,\n")
    sb.append("  seed: Int,\n")
    sb.append("  disableMultiStroke: Boolean,\n")
    sb.append("  disableMultiStrokeFill: Boolean,\n")
    sb.append("  preserveVertices: Boolean,\n")
    sb.append("  fillShapeRoughnessGain: Double,\n")
    sb.append("  var randomizer: Nullable[Random] = Nullable.empty,\n")
    sb.append("  fill: Option[String] = None,\n")
    sb.append("  simplification: Option[Double] = None,\n")
    sb.append("  strokeLineDash: Option[Vector[Double]] = None,\n")
    sb.append("  strokeLineDashOffset: Option[Double] = None,\n")
    sb.append("  fillLineDash: Option[Vector[Double]] = None,\n")
    sb.append("  fillLineDashOffset: Option[Double] = None,\n")
    sb.append("  fixedDecimalPlaceDigits: Option[Double] = None\n")
    sb.append(")\n\n")

    sb.append("final case class Op(op: OpType, data: Vector[Double])\n\n")
    sb.append("final case class OpSet(\n")
    sb.append("  `type`: OpSetType,\n")
    sb.append("  ops: Vector[Op],\n")
    sb.append("  size: Option[Point] = None,\n")
    sb.append("  path: Option[String] = None\n")
    sb.append(")\n\n")
    sb.append("final case class Drawable(shape: String, options: ResolvedOptions, sets: Vector[OpSet])\n\n")
    sb.append("final case class PathInfo(\n")
    sb.append("  d: String,\n")
    sb.append("  stroke: String,\n")
    sb.append("  strokeWidth: Double,\n")
    sb.append("  fill: Option[String] = None\n")
    sb.append(")\n")
    sb.toString
  }
}
