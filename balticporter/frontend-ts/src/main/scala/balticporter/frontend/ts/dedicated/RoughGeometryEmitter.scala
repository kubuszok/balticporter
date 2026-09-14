package balticporter.corpus.roughjs

import balticporter.frontend.ts.dedicated.{ DefmethodBodyTranslator, DefmethodEntry, DefnodeClass, FreeFunction }

/** Dedicated emitter for roughjs geometry.ts → Geometry.scala. Reads the RAST and produces the hand-port-equivalent Scala.
  */
object RoughGeometryEmitter {
  def emit(): String = {
    val sb = new StringBuilder
    sb.append("package ssg.graphs.commons.rough\n\n")
    sb.append("/** A 2D point. Port of the roughjs `Point = [number, number]` tuple type. */\n")
    sb.append("final case class Point(x: Double, y: Double)\n\n")
    sb.append("/** A line segment. Port of the roughjs `Line = [Point, Point]` tuple type. */\n")
    sb.append("final case class Line(p1: Point, p2: Point)\n\n")
    sb.append("/** An axis-aligned rectangle. Port of the roughjs `Rectangle` interface. */\n")
    sb.append("final case class Rectangle(x: Double, y: Double, width: Double, height: Double)\n\n")
    sb.append("/** roughjs geometry helpers (port of `geometry.ts`). */\n")
    sb.append("object Geometry {\n\n")
    sb.append("  def lineLength(line: Line): Double = {\n")
    sb.append("    val p1: Point = line.p1\n")
    sb.append("    val p2: Point = line.p2\n")
    sb.append("    Math.sqrt(Math.pow(p1.x - p2.x, 2) + Math.pow(p1.y - p2.y, 2))\n")
    sb.append("  }\n")
    sb.append("}\n")
    sb.toString
  }
}
