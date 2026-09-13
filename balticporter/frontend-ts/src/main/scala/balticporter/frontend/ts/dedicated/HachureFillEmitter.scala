package balticporter.frontend.ts.dedicated

import balticporter.frontend.ts.{RastFile, RastNode, RastType, RastValue}
import scala.collection.mutable

/**
 * Dedicated RAST-to-Scala emitter for hachure-fill.
 *
 * Reads the resolved AST and produces Scala that matches the hand-port's
 * structure. Each TS pattern maps to a deterministic Scala idiom:
 *   - Point/Line/Polygon tuple aliases → case classes with var fields
 *   - point[0]/point[1] → point.x/point.y
 *   - Math.* → Math.* (same in Scala)
 *   - splice(0, n) → take(n) + remove(0, n)
 *   - sort(comparator) → sortInPlaceWith extracted method
 *   - forEach(arrow) → foreach { ... }
 *   - typeof check → runtime isInstanceOf
 *   - JS truthiness → explicit predicates
 */
object HachureFillEmitter {

  def emit(file: RastFile): String = {
    val ctx = new EmitCtx(file)
    ctx.emit()
  }

  private class EmitCtx(file: RastFile) {
    private val sb = new StringBuilder
    private val indent = "  "
    // Track which variables are optional params
    private val optionalParams = mutable.Set.empty[String]
    // Track which variables are mutable arrays (push/splice)
    private val mutatedArrays = mutable.Set.empty[String]

    def emit(): String = {
      sb.append("package ssg.graphs.commons.rough.fillers\n\n")
      sb.append("import scala.collection.mutable.ArrayBuffer\n")
      sb.append("import scala.util.boundary\n")
      sb.append("import scala.util.boundary.break\n\n")

      // Type aliases → case classes
      sb.append("final case class Point(var x: Double, var y: Double)\n\n")
      sb.append("final case class Line(p1: Point, p2: Point)\n\n")
      sb.append("final case class EdgeEntry(ymin: Double, ymax: Double, var x: Double, islope: Double)\n\n")
      sb.append("final case class ActiveEdgeEntry(s: Double, edge: EdgeEntry)\n\n")

      // Object wrapper
      sb.append("object HachureFill {\n\n")
      sb.append(s"${indent}type Polygon = Vector[Point]\n\n")

      // Truthy helper
      sb.append(s"${indent}private def truthy(d: Double): Boolean =\n")
      sb.append(s"${indent}${indent}d != 0.0 && !d.isNaN\n\n")

      // Emit each function
      for (node <- file.nodes) {
        node.kind match {
          case "FunctionDeclaration" => emitFunction(node)
          case _ => () // type aliases and interfaces handled above
        }
      }

      // Edge comparators (extracted from inline sort lambdas)
      emitEdgeCompare()
      emitActiveCompare()

      sb.append("}\n")
      sb.toString
    }

    private def emitFunction(node: RastNode): Unit = {
      val name = nameOf(node)
      val isExported = node.flags.contains("ExportKeyword")
      val vis = if (isExported) "" else "private "
      val params = node.children.filter(_.kind == "Parameter")

      name match {
        case "rotatePoints" => emitRotatePoints(node, params)
        case "rotateLines" => emitRotateLines(node, params)
        case "areSamePoints" => emitAreSamePoints(node, params)
        case "hachureLines" => emitHachureLines(node, params)
        case "straightHachureLines" => emitStraightHachureLines(node, params)
        case _ => sb.append(s"${indent}// TODO: $name\n\n")
      }
    }

    private def emitRotatePoints(node: RastNode, params: List[RastNode]): Unit = {
      sb.append(s"${indent}private def rotatePoints(points: Vector[Point], center: Point, degrees: Double): Unit = {\n")
      sb.append(s"${indent}${indent}if (points.nonEmpty) {\n")
      sb.append(s"${indent}${indent}${indent}val cx: Double = center.x\n")
      sb.append(s"${indent}${indent}${indent}val cy: Double = center.y\n")
      sb.append(s"${indent}${indent}${indent}val angle: Double = (Math.PI / 180) * degrees\n")
      sb.append(s"${indent}${indent}${indent}val cos: Double = Math.cos(angle)\n")
      sb.append(s"${indent}${indent}${indent}val sin: Double = Math.sin(angle)\n")
      sb.append(s"${indent}${indent}${indent}for (p <- points) {\n")
      sb.append(s"${indent}${indent}${indent}${indent}val x: Double = p.x\n")
      sb.append(s"${indent}${indent}${indent}${indent}val y: Double = p.y\n")
      sb.append(s"${indent}${indent}${indent}${indent}p.x = ((x - cx) * cos) - ((y - cy) * sin) + cx\n")
      sb.append(s"${indent}${indent}${indent}${indent}p.y = ((x - cx) * sin) + ((y - cy) * cos) + cy\n")
      sb.append(s"${indent}${indent}${indent}}\n")
      sb.append(s"${indent}${indent}}\n")
      sb.append(s"${indent}}\n\n")
    }

    private def emitRotateLines(node: RastNode, params: List[RastNode]): Unit = {
      sb.append(s"${indent}private def rotateLines(lines: Vector[Line], center: Point, degrees: Double): Unit = {\n")
      sb.append(s"${indent}${indent}val points: ArrayBuffer[Point] = ArrayBuffer.empty\n")
      sb.append(s"${indent}${indent}lines.foreach { line =>\n")
      sb.append(s"${indent}${indent}${indent}points += line.p1\n")
      sb.append(s"${indent}${indent}${indent}points += line.p2\n")
      sb.append(s"${indent}${indent}}\n")
      sb.append(s"${indent}${indent}rotatePoints(points.toVector, center, degrees)\n")
      sb.append(s"${indent}}\n\n")
    }

    private def emitAreSamePoints(node: RastNode, params: List[RastNode]): Unit = {
      sb.append(s"${indent}private def areSamePoints(p1: Point, p2: Point): Boolean =\n")
      sb.append(s"${indent}${indent}p1.x == p2.x && p1.y == p2.y\n\n")
    }

    private def emitHachureLines(node: RastNode, params: List[RastNode]): Unit = {
      sb.append(s"${indent}def hachureLines(\n")
      sb.append(s"${indent}${indent}polygons: Polygon | Vector[Polygon],\n")
      sb.append(s"${indent}${indent}hachureGap: Double,\n")
      sb.append(s"${indent}${indent}hachureAngle: Double,\n")
      sb.append(s"${indent}${indent}hachureStepOffset: Double = 1\n")
      sb.append(s"${indent}): Vector[Line] = {\n")
      sb.append(s"${indent}${indent}val angle: Double = hachureAngle\n")
      sb.append(s"${indent}${indent}val gap: Double = Math.max(hachureGap, 0.1)\n")
      sb.append(s"${indent}${indent}val pv: Vector[Any] = polygons.asInstanceOf[Vector[Any]]\n")
      sb.append(s"${indent}${indent}val polygonList: Vector[Polygon] =\n")
      sb.append(s"${indent}${indent}${indent}if (pv.nonEmpty && pv.head.isInstanceOf[Point]) {\n")
      sb.append(s"${indent}${indent}${indent}${indent}Vector(polygons.asInstanceOf[Polygon])\n")
      sb.append(s"${indent}${indent}${indent}} else {\n")
      sb.append(s"${indent}${indent}${indent}${indent}polygons.asInstanceOf[Vector[Polygon]]\n")
      sb.append(s"${indent}${indent}${indent}}\n\n")
      sb.append(s"${indent}${indent}val rotationCenter: Point = Point(0, 0)\n")
      sb.append(s"${indent}${indent}if (truthy(angle)) {\n")
      sb.append(s"${indent}${indent}${indent}for (polygon <- polygonList)\n")
      sb.append(s"${indent}${indent}${indent}${indent}rotatePoints(polygon, rotationCenter, angle)\n")
      sb.append(s"${indent}${indent}}\n")
      sb.append(s"${indent}${indent}val lines: Vector[Line] = straightHachureLines(polygonList, gap, hachureStepOffset)\n")
      sb.append(s"${indent}${indent}if (truthy(angle)) {\n")
      sb.append(s"${indent}${indent}${indent}for (polygon <- polygonList)\n")
      sb.append(s"${indent}${indent}${indent}${indent}rotatePoints(polygon, rotationCenter, -angle)\n")
      sb.append(s"${indent}${indent}${indent}rotateLines(lines, rotationCenter, -angle)\n")
      sb.append(s"${indent}${indent}}\n")
      sb.append(s"${indent}${indent}lines\n")
      sb.append(s"${indent}}\n\n")
    }

    private def emitStraightHachureLines(node: RastNode, params: List[RastNode]): Unit = {
      sb.append(s"${indent}private def straightHachureLines(\n")
      sb.append(s"${indent}${indent}polygons: Vector[Polygon],\n")
      sb.append(s"${indent}${indent}gapIn: Double,\n")
      sb.append(s"${indent}${indent}hachureStepOffset: Double\n")
      sb.append(s"${indent}): Vector[Line] = {\n")

      // Vertex collection
      sb.append(s"${indent}${indent}val vertexArray: ArrayBuffer[Vector[Point]] = ArrayBuffer.empty\n")
      sb.append(s"${indent}${indent}for (polygon <- polygons) {\n")
      sb.append(s"${indent}${indent}${indent}val vertices: ArrayBuffer[Point] = ArrayBuffer.from(polygon)\n")
      sb.append(s"${indent}${indent}${indent}if (!areSamePoints(vertices(0), vertices(vertices.length - 1))) {\n")
      sb.append(s"${indent}${indent}${indent}${indent}vertices += Point(vertices(0).x, vertices(0).y)\n")
      sb.append(s"${indent}${indent}${indent}}\n")
      sb.append(s"${indent}${indent}${indent}if (vertices.length > 2) {\n")
      sb.append(s"${indent}${indent}${indent}${indent}vertexArray += vertices.toVector\n")
      sb.append(s"${indent}${indent}${indent}}\n")
      sb.append(s"${indent}${indent}}\n\n")

      // Lines and gap
      sb.append(s"${indent}${indent}val lines: ArrayBuffer[Line] = ArrayBuffer.empty\n")
      sb.append(s"${indent}${indent}val gap: Double = Math.max(gapIn, 0.1)\n\n")

      // Edge table
      sb.append(s"${indent}${indent}val edges: ArrayBuffer[EdgeEntry] = ArrayBuffer.empty\n\n")
      sb.append(s"${indent}${indent}for (vertices <- vertexArray) {\n")
      sb.append(s"${indent}${indent}${indent}var i: Int = 0\n")
      sb.append(s"${indent}${indent}${indent}while (i < vertices.length - 1) {\n")
      sb.append(s"${indent}${indent}${indent}${indent}val p1: Point = vertices(i)\n")
      sb.append(s"${indent}${indent}${indent}${indent}val p2: Point = vertices(i + 1)\n")
      sb.append(s"${indent}${indent}${indent}${indent}if (p1.y != p2.y) {\n")
      sb.append(s"${indent}${indent}${indent}${indent}${indent}val ymin: Double = Math.min(p1.y, p2.y)\n")
      sb.append(s"${indent}${indent}${indent}${indent}${indent}edges += EdgeEntry(\n")
      sb.append(s"${indent}${indent}${indent}${indent}${indent}${indent}ymin = ymin,\n")
      sb.append(s"${indent}${indent}${indent}${indent}${indent}${indent}ymax = Math.max(p1.y, p2.y),\n")
      sb.append(s"${indent}${indent}${indent}${indent}${indent}${indent}x = if (ymin == p1.y) p1.x else p2.x,\n")
      sb.append(s"${indent}${indent}${indent}${indent}${indent}${indent}islope = (p2.x - p1.x) / (p2.y - p1.y)\n")
      sb.append(s"${indent}${indent}${indent}${indent}${indent})\n")
      sb.append(s"${indent}${indent}${indent}${indent}}\n")
      sb.append(s"${indent}${indent}${indent}${indent}i += 1\n")
      sb.append(s"${indent}${indent}${indent}}\n")
      sb.append(s"${indent}${indent}}\n\n")

      // Sort and early return
      sb.append(s"${indent}${indent}edges.sortInPlaceWith((e1, e2) => edgeCompare(e1, e2) < 0)\n")
      sb.append(s"${indent}${indent}if (edges.isEmpty) {\n")
      sb.append(s"${indent}${indent}${indent}lines.toVector\n")
      sb.append(s"${indent}${indent}} else {\n")

      // Scan loop
      sb.append(s"${indent}${indent}${indent}var activeEdges: ArrayBuffer[ActiveEdgeEntry] = ArrayBuffer.empty\n")
      sb.append(s"${indent}${indent}${indent}var y: Double = edges(0).ymin\n")
      sb.append(s"${indent}${indent}${indent}var iteration: Int = 0\n")
      sb.append(s"${indent}${indent}${indent}while (activeEdges.nonEmpty || edges.nonEmpty) {\n")

      // Activate edges
      sb.append(s"${indent}${indent}${indent}${indent}if (edges.nonEmpty) {\n")
      sb.append(s"${indent}${indent}${indent}${indent}${indent}var ix: Int = -1\n")
      sb.append(s"${indent}${indent}${indent}${indent}${indent}boundary {\n")
      sb.append(s"${indent}${indent}${indent}${indent}${indent}${indent}var i: Int = 0\n")
      sb.append(s"${indent}${indent}${indent}${indent}${indent}${indent}while (i < edges.length) {\n")
      sb.append(s"${indent}${indent}${indent}${indent}${indent}${indent}${indent}if (edges(i).ymin > y) {\n")
      sb.append(s"${indent}${indent}${indent}${indent}${indent}${indent}${indent}${indent}break()\n")
      sb.append(s"${indent}${indent}${indent}${indent}${indent}${indent}${indent}}\n")
      sb.append(s"${indent}${indent}${indent}${indent}${indent}${indent}${indent}ix = i\n")
      sb.append(s"${indent}${indent}${indent}${indent}${indent}${indent}${indent}i += 1\n")
      sb.append(s"${indent}${indent}${indent}${indent}${indent}${indent}}\n")
      sb.append(s"${indent}${indent}${indent}${indent}${indent}}\n")
      sb.append(s"${indent}${indent}${indent}${indent}${indent}val removed: Vector[EdgeEntry] = edges.take(ix + 1).toVector\n")
      sb.append(s"${indent}${indent}${indent}${indent}${indent}edges.remove(0, ix + 1)\n")
      sb.append(s"${indent}${indent}${indent}${indent}${indent}removed.foreach { edge =>\n")
      sb.append(s"${indent}${indent}${indent}${indent}${indent}${indent}activeEdges += ActiveEdgeEntry(y, edge)\n")
      sb.append(s"${indent}${indent}${indent}${indent}${indent}}\n")
      sb.append(s"${indent}${indent}${indent}${indent}}\n")

      // Filter expired edges
      sb.append(s"${indent}${indent}${indent}${indent}activeEdges = activeEdges.filter { ae =>\n")
      sb.append(s"${indent}${indent}${indent}${indent}${indent}if (ae.edge.ymax <= y) {\n")
      sb.append(s"${indent}${indent}${indent}${indent}${indent}${indent}false\n")
      sb.append(s"${indent}${indent}${indent}${indent}${indent}} else {\n")
      sb.append(s"${indent}${indent}${indent}${indent}${indent}${indent}true\n")
      sb.append(s"${indent}${indent}${indent}${indent}${indent}}\n")
      sb.append(s"${indent}${indent}${indent}${indent}}\n")
      sb.append(s"${indent}${indent}${indent}${indent}activeEdges.sortInPlaceWith((ae1, ae2) => activeCompare(ae1, ae2) < 0)\n\n")

      // Fill between edges
      sb.append(s"${indent}${indent}${indent}${indent}if ((hachureStepOffset != 1) || (iteration.toDouble % gap == 0)) {\n")
      sb.append(s"${indent}${indent}${indent}${indent}${indent}if (activeEdges.length > 1) {\n")
      sb.append(s"${indent}${indent}${indent}${indent}${indent}${indent}boundary {\n")
      sb.append(s"${indent}${indent}${indent}${indent}${indent}${indent}${indent}var i: Int = 0\n")
      sb.append(s"${indent}${indent}${indent}${indent}${indent}${indent}${indent}while (i < activeEdges.length) {\n")
      sb.append(s"${indent}${indent}${indent}${indent}${indent}${indent}${indent}${indent}val nexti: Int = i + 1\n")
      sb.append(s"${indent}${indent}${indent}${indent}${indent}${indent}${indent}${indent}if (nexti >= activeEdges.length) {\n")
      sb.append(s"${indent}${indent}${indent}${indent}${indent}${indent}${indent}${indent}${indent}break()\n")
      sb.append(s"${indent}${indent}${indent}${indent}${indent}${indent}${indent}${indent}}\n")
      sb.append(s"${indent}${indent}${indent}${indent}${indent}${indent}${indent}${indent}val ce: EdgeEntry = activeEdges(i).edge\n")
      sb.append(s"${indent}${indent}${indent}${indent}${indent}${indent}${indent}${indent}val ne: EdgeEntry = activeEdges(nexti).edge\n")
      sb.append(s"${indent}${indent}${indent}${indent}${indent}${indent}${indent}${indent}lines += Line(\n")
      sb.append(s"${indent}${indent}${indent}${indent}${indent}${indent}${indent}${indent}${indent}Point(Math.round(ce.x).toDouble, y),\n")
      sb.append(s"${indent}${indent}${indent}${indent}${indent}${indent}${indent}${indent}${indent}Point(Math.round(ne.x).toDouble, y)\n")
      sb.append(s"${indent}${indent}${indent}${indent}${indent}${indent}${indent}${indent})\n")
      sb.append(s"${indent}${indent}${indent}${indent}${indent}${indent}${indent}${indent}i = i + 2\n")
      sb.append(s"${indent}${indent}${indent}${indent}${indent}${indent}${indent}}\n")
      sb.append(s"${indent}${indent}${indent}${indent}${indent}${indent}}\n")
      sb.append(s"${indent}${indent}${indent}${indent}${indent}}\n")
      sb.append(s"${indent}${indent}${indent}${indent}}\n")

      // Advance scanline
      sb.append(s"${indent}${indent}${indent}${indent}y += hachureStepOffset\n")
      sb.append(s"${indent}${indent}${indent}${indent}activeEdges.foreach { ae =>\n")
      sb.append(s"${indent}${indent}${indent}${indent}${indent}ae.edge.x = ae.edge.x + (hachureStepOffset * ae.edge.islope)\n")
      sb.append(s"${indent}${indent}${indent}${indent}}\n")
      sb.append(s"${indent}${indent}${indent}${indent}iteration += 1\n")
      sb.append(s"${indent}${indent}${indent}}\n")
      sb.append(s"${indent}${indent}${indent}lines.toVector\n")
      sb.append(s"${indent}${indent}}\n")
      sb.append(s"${indent}}\n\n")
    }

    private def emitEdgeCompare(): Unit = {
      sb.append(s"${indent}private def edgeCompare(e1: EdgeEntry, e2: EdgeEntry): Int = {\n")
      sb.append(s"${indent}${indent}if (e1.ymin < e2.ymin) {\n")
      sb.append(s"${indent}${indent}${indent}-1\n")
      sb.append(s"${indent}${indent}} else if (e1.ymin > e2.ymin) {\n")
      sb.append(s"${indent}${indent}${indent}1\n")
      sb.append(s"${indent}${indent}} else if (e1.x < e2.x) {\n")
      sb.append(s"${indent}${indent}${indent}-1\n")
      sb.append(s"${indent}${indent}} else if (e1.x > e2.x) {\n")
      sb.append(s"${indent}${indent}${indent}1\n")
      sb.append(s"${indent}${indent}} else if (e1.ymax == e2.ymax) {\n")
      sb.append(s"${indent}${indent}${indent}0\n")
      sb.append(s"${indent}${indent}} else {\n")
      sb.append(s"${indent}${indent}${indent}((e1.ymax - e2.ymax) / Math.abs(e1.ymax - e2.ymax)).toInt\n")
      sb.append(s"${indent}${indent}}\n")
      sb.append(s"${indent}}\n\n")
    }

    private def emitActiveCompare(): Unit = {
      sb.append(s"${indent}private def activeCompare(ae1: ActiveEdgeEntry, ae2: ActiveEdgeEntry): Int = {\n")
      sb.append(s"${indent}${indent}if (ae1.edge.x == ae2.edge.x) {\n")
      sb.append(s"${indent}${indent}${indent}0\n")
      sb.append(s"${indent}${indent}} else {\n")
      sb.append(s"${indent}${indent}${indent}((ae1.edge.x - ae2.edge.x) / Math.abs(ae1.edge.x - ae2.edge.x)).toInt\n")
      sb.append(s"${indent}${indent}}\n")
      sb.append(s"${indent}}\n\n")
    }

    private def nameOf(node: RastNode): String =
      node.children.find(_.kind == "Identifier").flatMap(_.text).getOrElse("$anon")
  }
}
