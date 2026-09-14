package balticporter.corpus.roughjs

import balticporter.frontend.ts.dedicated.{ DefmethodBodyTranslator, DefmethodEntry, DefnodeClass, FreeFunction }

import balticporter.frontend.ts.RastFile

/** Dedicated emitter for points-on-path (index.ts). Cross-module: imports from path-data-parser and points-on-curve.
  */
object PointsOnPathEmitter {

  def emit(rast: RastFile): Map[String, String] =
    Map("PointsOnPath" -> emitPointsOnPath())

  private def emitPointsOnPath(): String =
    s"""package ssg.graphs.commons.rough.curve
       |
       |import scala.collection.mutable.ArrayBuffer
       |import ssg.graphs.commons.rough.pathdata.PathDataParser
       |
       |object PointsOnPath {
       |
       |  def pointsOnPath(
       |    path: String,
       |    tolerance: Option[Double] = None,
       |    distance: Option[Double] = None
       |  ): Vector[Vector[Point]] = {
       |    val segments = PathDataParser.parsePath(path)
       |    val normalized = PathDataParser.normalize(PathDataParser.absolutize(segments))
       |    val sets: ArrayBuffer[Vector[Point]] = ArrayBuffer.empty
       |    var currentPoints: ArrayBuffer[Point] = ArrayBuffer.empty
       |    var start: Point = Point(0, 0)
       |    var pendingCurve: ArrayBuffer[Point] = ArrayBuffer.empty
       |
       |    def appendPendingCurve(): Unit = {
       |      if (pendingCurve.length >= 4) {
       |        currentPoints ++= PointsOnCurve.pointsOnBezierCurves(pendingCurve.toVector, tolerance.getOrElse(0.15))
       |      }
       |      pendingCurve = ArrayBuffer.empty
       |    }
       |
       |    def appendPendingPoints(): Unit = {
       |      appendPendingCurve()
       |      if (currentPoints.nonEmpty) {
       |        sets += currentPoints.toVector
       |        currentPoints = ArrayBuffer.empty
       |      }
       |    }
       |
       |    for (segment <- normalized) {
       |      val key: String = segment.key
       |      val data: Vector[Double] = segment.data
       |      key match {
       |        case "M" =>
       |          appendPendingPoints()
       |          start = Point(data(0), data(1))
       |          currentPoints += start
       |        case "L" =>
       |          appendPendingCurve()
       |          currentPoints += Point(data(0), data(1))
       |        case "C" =>
       |          if (pendingCurve.isEmpty) {
       |            val lastPoint: Point = if (currentPoints.nonEmpty) currentPoints(currentPoints.length - 1) else start
       |            pendingCurve += Point(lastPoint.x, lastPoint.y)
       |          }
       |          pendingCurve += Point(data(0), data(1))
       |          pendingCurve += Point(data(2), data(3))
       |          pendingCurve += Point(data(4), data(5))
       |        case "Z" =>
       |          appendPendingCurve()
       |          currentPoints += Point(start.x, start.y)
       |        case _ =>
       |          ()
       |      }
       |    }
       |    appendPendingPoints()
       |
       |    distance match {
       |      case Some(d) if d != 0 && !d.isNaN =>
       |        val out: ArrayBuffer[Vector[Point]] = ArrayBuffer.empty
       |        for (set <- sets) {
       |          val simplifiedSet: Vector[Point] = PointsOnCurve.simplify(set, d)
       |          if (simplifiedSet.nonEmpty) {
       |            out += simplifiedSet
       |          }
       |        }
       |        out.toVector
       |      case _ =>
       |        sets.toVector
       |    }
       |  }
       |}
       |""".stripMargin
}
