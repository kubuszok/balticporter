package balticporter.corpus.roughjs

import balticporter.frontend.ts.dedicated.{DefmethodBodyTranslator, DefmethodEntry, DefnodeClass, FreeFunction}

import balticporter.frontend.ts.RastFile

/** Dedicated emitter for points-on-curve (index.ts + curve-to-bezier.ts).
  * Produces Scala matching the hand-port structure: Point case class,
  * PointsOnCurve object, CurveToBezier object. */
object PointsOnCurveEmitter {

  def emit(indexRast: RastFile, curveToBezierRast: RastFile): Map[String, String] = {
    Map(
      "PointsOnCurve" -> emitPointsOnCurve(),
      "CurveToBezier" -> emitCurveToBezier(),
    )
  }

  private def emitPointsOnCurve(): String = {
    s"""package ssg.graphs.commons.rough.curve
       |
       |import scala.collection.mutable.ArrayBuffer
       |
       |final case class Point(x: Double, y: Double)
       |
       |object PointsOnCurve {
       |
       |  private def distance(p1: Point, p2: Point): Double = {
       |    Math.sqrt(distanceSq(p1, p2))
       |  }
       |
       |  private def distanceSq(p1: Point, p2: Point): Double = {
       |    Math.pow(p1.x - p2.x, 2) + Math.pow(p1.y - p2.y, 2)
       |  }
       |
       |  private def distanceToSegmentSq(p: Point, v: Point, w: Point): Double = {
       |    val l2: Double = distanceSq(v, w)
       |    if (l2 == 0) {
       |      distanceSq(p, v)
       |    } else {
       |      var t: Double = ((p.x - v.x) * (w.x - v.x) + (p.y - v.y) * (w.y - v.y)) / l2
       |      t = Math.max(0, Math.min(1, t))
       |      distanceSq(p, lerp(v, w, t))
       |    }
       |  }
       |
       |  private def lerp(a: Point, b: Point, t: Double): Point = {
       |    Point(
       |      a.x + (b.x - a.x) * t,
       |      a.y + (b.y - a.y) * t
       |    )
       |  }
       |
       |  private def flatness(points: Vector[Point], offset: Int): Double = {
       |    val p1: Point = points(offset + 0)
       |    val p2: Point = points(offset + 1)
       |    val p3: Point = points(offset + 2)
       |    val p4: Point = points(offset + 3)
       |    var ux: Double = 3 * p2.x - 2 * p1.x - p4.x; ux *= ux
       |    var uy: Double = 3 * p2.y - 2 * p1.y - p4.y; uy *= uy
       |    var vx: Double = 3 * p3.x - 2 * p4.x - p1.x; vx *= vx
       |    var vy: Double = 3 * p3.y - 2 * p4.y - p1.y; vy *= vy
       |    if (ux < vx) { ux = vx }
       |    if (uy < vy) { uy = vy }
       |    ux + uy
       |  }
       |
       |  private def getPointsOnBezierCurveWithSplitting(
       |    points: Vector[Point], offset: Int, tolerance: Double,
       |    newPoints: Option[ArrayBuffer[Point]]
       |  ): ArrayBuffer[Point] = {
       |    val outPoints: ArrayBuffer[Point] = newPoints.getOrElse(ArrayBuffer.empty)
       |    if (flatness(points, offset) < tolerance) {
       |      val p0: Point = points(offset + 0)
       |      if (outPoints.nonEmpty) {
       |        val d: Double = distance(outPoints(outPoints.length - 1), p0)
       |        if (d > 1) { outPoints += p0 }
       |      } else {
       |        outPoints += p0
       |      }
       |      outPoints += points(offset + 3)
       |    } else {
       |      val t: Double = .5
       |      val p1: Point = points(offset + 0)
       |      val p2: Point = points(offset + 1)
       |      val p3: Point = points(offset + 2)
       |      val p4: Point = points(offset + 3)
       |      val q1: Point = lerp(p1, p2, t)
       |      val q2: Point = lerp(p2, p3, t)
       |      val q3: Point = lerp(p3, p4, t)
       |      val r1: Point = lerp(q1, q2, t)
       |      val r2: Point = lerp(q2, q3, t)
       |      val red: Point = lerp(r1, r2, t)
       |      getPointsOnBezierCurveWithSplitting(Vector(p1, q1, r1, red), 0, tolerance, Some(outPoints))
       |      getPointsOnBezierCurveWithSplitting(Vector(red, r2, q3, p4), 0, tolerance, Some(outPoints))
       |    }
       |    outPoints
       |  }
       |
       |  def simplify(points: Vector[Point], distanceTolerance: Double): Vector[Point] = {
       |    simplifyPoints(points, 0, points.length, distanceTolerance).toVector
       |  }
       |
       |  private def simplifyPoints(
       |    points: Vector[Point], start: Int, end: Int, epsilon: Double,
       |    newPoints: Option[ArrayBuffer[Point]] = None
       |  ): ArrayBuffer[Point] = {
       |    val outPoints: ArrayBuffer[Point] = newPoints.getOrElse(ArrayBuffer.empty)
       |    val s: Point = points(start)
       |    val e: Point = points(end - 1)
       |    var maxDistSq: Double = 0
       |    var maxNdx: Int = 1
       |    var i: Int = start + 1
       |    while (i < end - 1) {
       |      val distSq: Double = distanceToSegmentSq(points(i), s, e)
       |      if (distSq > maxDistSq) {
       |        maxDistSq = distSq
       |        maxNdx = i
       |      }
       |      i += 1
       |    }
       |    if (Math.sqrt(maxDistSq) > epsilon) {
       |      simplifyPoints(points, start, maxNdx + 1, epsilon, Some(outPoints))
       |      simplifyPoints(points, maxNdx, end, epsilon, Some(outPoints))
       |    } else {
       |      if (outPoints.isEmpty) { outPoints += s }
       |      outPoints += e
       |    }
       |    outPoints
       |  }
       |
       |  def pointsOnBezierCurves(
       |    points: Vector[Point], tolerance: Double = 0.15,
       |    distanceTolerance: Option[Double] = None
       |  ): Vector[Point] = {
       |    val newPoints: ArrayBuffer[Point] = ArrayBuffer.empty
       |    val numSegments: Double = (points.length - 1).toDouble / 3
       |    var i: Int = 0
       |    while (i < numSegments) {
       |      val offset: Int = i * 3
       |      getPointsOnBezierCurveWithSplitting(points, offset, tolerance, Some(newPoints))
       |      i += 1
       |    }
       |    distanceTolerance match {
       |      case Some(d) if d > 0 =>
       |        simplifyPoints(newPoints.toVector, 0, newPoints.length, d).toVector
       |      case _ =>
       |        newPoints.toVector
       |    }
       |  }
       |}
       |""".stripMargin
  }

  private def emitCurveToBezier(): String = {
    s"""package ssg.graphs.commons.rough.curve
       |
       |import scala.collection.mutable.ArrayBuffer
       |
       |final class CurveError(message: String) extends RuntimeException(message)
       |
       |object CurveToBezier {
       |
       |  private def clone(p: Point): Point = {
       |    Point(p.x, p.y)
       |  }
       |
       |  def curveToBezier(pointsIn: Vector[Point], curveTightness: Double = 0): Vector[Point] = {
       |    val len: Int = pointsIn.length
       |    if (len < 3) {
       |      throw new CurveError("A curve must have at least three points.")
       |    }
       |    val out: ArrayBuffer[Point] = ArrayBuffer.empty
       |    if (len == 3) {
       |      out += clone(pointsIn(0))
       |      out += clone(pointsIn(1))
       |      out += clone(pointsIn(2))
       |      out += clone(pointsIn(2))
       |    } else {
       |      val points: ArrayBuffer[Point] = ArrayBuffer.empty
       |      points += pointsIn(0)
       |      points += pointsIn(0)
       |      var i: Int = 1
       |      while (i < pointsIn.length) {
       |        points += pointsIn(i)
       |        if (i == (pointsIn.length - 1)) {
       |          points += pointsIn(i)
       |        }
       |        i += 1
       |      }
       |      val s: Double = 1 - curveTightness
       |      out += clone(points(0))
       |      var j: Int = 1
       |      while ((j + 2) < points.length) {
       |        val cachedVertArray: Point = points(j)
       |        val b1: Point = Point(
       |          cachedVertArray.x + (s * points(j + 1).x - s * points(j - 1).x) / 6,
       |          cachedVertArray.y + (s * points(j + 1).y - s * points(j - 1).y) / 6
       |        )
       |        val b2: Point = Point(
       |          points(j + 1).x + (s * points(j).x - s * points(j + 2).x) / 6,
       |          points(j + 1).y + (s * points(j).y - s * points(j + 2).y) / 6
       |        )
       |        val b3: Point = Point(points(j + 1).x, points(j + 1).y)
       |        out += b1
       |        out += b2
       |        out += b3
       |        j += 1
       |      }
       |    }
       |    out.toVector
       |  }
       |}
       |""".stripMargin
  }
}
