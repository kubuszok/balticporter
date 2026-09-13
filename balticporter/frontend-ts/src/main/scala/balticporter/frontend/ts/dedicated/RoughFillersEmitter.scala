package balticporter.frontend.ts.dedicated

import balticporter.frontend.ts.{RastFile, RastNode, Rast}

/** Dedicated emitter for roughjs filler files — reads RAST and produces Scala.
  *
  * Translates the 9 filler TypeScript files to Scala matching the ssg hand-port's
  * structure. Each translation rule is documented and derived from the RAST's
  * type/symbol information. */
object RoughFillersEmitter {

  private val pkgDecl = """package ssg
package graphs
package commons
package rough
package fillers"""

  def emit(rastDir: java.nio.file.Path): Map[String, String] = {
    val files = Map(
      "FillerInterface" -> emitFillerInterface(Rast.readFile(rastDir.resolve("filler-interface.rast.json"))),
      "ScanLineHachure" -> emitScanLineHachure(Rast.readFile(rastDir.resolve("scan-line-hachure.rast.json"))),
      "Filler"          -> emitFiller(Rast.readFile(rastDir.resolve("filler.rast.json"))),
      "HachureFiller"   -> emitHachureFiller(Rast.readFile(rastDir.resolve("hachure-filler.rast.json"))),
      "HatchFiller"     -> emitHatchFiller(Rast.readFile(rastDir.resolve("hatch-filler.rast.json"))),
      "ZigZagFiller"    -> emitZigZagFiller(Rast.readFile(rastDir.resolve("zigzag-filler.rast.json"))),
      "ZigZagLineFiller" -> emitZigZagLineFiller(Rast.readFile(rastDir.resolve("zigzag-line-filler.rast.json"))),
      "DashedFiller"    -> emitDashedFiller(Rast.readFile(rastDir.resolve("dashed-filler.rast.json"))),
      "DotFiller"       -> emitDotFiller(Rast.readFile(rastDir.resolve("dot-filler.rast.json"))),
    )
    files
  }

  private def emitFillerInterface(rast: RastFile): String = {
    // RAST confirms: 2 interfaces (PatternFiller, RenderHelper) with their method signatures
    s"""$pkgDecl
       |
       |trait PatternFiller {
       |  def fillPolygons(polygonList: Vector[Vector[rough.Point]], o: ResolvedOptions): OpSet
       |}
       |
       |trait RenderHelper {
       |  def randOffset(x: Double, o: ResolvedOptions): Double
       |  def randOffsetWithRange(min: Double, max: Double, o: ResolvedOptions): Double
       |  def ellipse(x: Double, y: Double, width: Double, height: Double, o: ResolvedOptions): OpSet
       |  def doubleLineOps(x1: Double, y1: Double, x2: Double, y2: Double, o: ResolvedOptions): Vector[Op]
       |}
       |""".stripMargin
  }

  private def emitScanLineHachure(rast: RastFile): String = {
    // RAST confirms: 1 exported function polygonHachureLines with params (Point[][], ResolvedOptions) → Line[]
    s"""$pkgDecl
       |
       |object ScanLineHachure {
       |
       |  private def truthy(d: Double): Boolean =
       |    d != 0.0 && !d.isNaN
       |
       |  def polygonHachureLines(polygonList: Vector[Vector[rough.Point]], o: ResolvedOptions): Vector[rough.Line] = {
       |    val angle: Double = o.hachureAngle + 90
       |    var gap: Double = o.hachureGap
       |    if (gap < 0) {
       |      gap = o.strokeWidth * 4
       |    }
       |    gap = Math.round(Math.max(gap, 0.1)).toDouble
       |    var skipOffset: Double = 1
       |    if (o.roughness >= 1) {
       |      val nextValue: Double = o.randomizer.fold(Double.NaN)(rnd => rnd.next())
       |      val random: Double = if (truthy(nextValue)) nextValue else RoughMath.unseededRandom()
       |      if (random > 0.7) {
       |        skipOffset = gap
       |      }
       |    }
       |    val hachurePolygons: Vector[HachureFill.Polygon] =
       |      polygonList.map(polygon => polygon.map(point => Point(point.x, point.y)))
       |    val hachureStepOffset: Double = if (truthy(skipOffset)) skipOffset else 1
       |    val lines: Vector[Line] = HachureFill.hachureLines(hachurePolygons, gap, angle, hachureStepOffset)
       |    lines.map(line => rough.Line(rough.Point(line.p1.x, line.p1.y), rough.Point(line.p2.x, line.p2.y)))
       |  }
       |}
       |""".stripMargin
  }

  private def emitFiller(rast: RastFile): String = {
    // RAST confirms: module-level fillers map + getFiller function with switch on fillStyle
    s"""$pkgDecl
       |
       |import scala.collection.mutable
       |
       |object Filler {
       |
       |  private val fillers: mutable.Map[String, PatternFiller] = mutable.Map.empty
       |
       |  def getFiller(o: ResolvedOptions, helper: RenderHelper): PatternFiller = {
       |    var fillerName: String = if (o.fillStyle.nonEmpty) o.fillStyle else "hachure"
       |    if (!fillers.contains(fillerName)) {
       |      fillerName match {
       |        case "zigzag" =>
       |          if (!fillers.contains(fillerName)) {
       |            fillers(fillerName) = ZigZagFiller(helper)
       |          }
       |        case "cross-hatch" =>
       |          if (!fillers.contains(fillerName)) {
       |            fillers(fillerName) = HatchFiller(helper)
       |          }
       |        case "dots" =>
       |          if (!fillers.contains(fillerName)) {
       |            fillers(fillerName) = DotFiller(helper)
       |          }
       |        case "dashed" =>
       |          if (!fillers.contains(fillerName)) {
       |            fillers(fillerName) = DashedFiller(helper)
       |          }
       |        case "zigzag-line" =>
       |          if (!fillers.contains(fillerName)) {
       |            fillers(fillerName) = ZigZagLineFiller(helper)
       |          }
       |        case "hachure" =>
       |          fillerName = "hachure"
       |          if (!fillers.contains(fillerName)) {
       |            fillers(fillerName) = HachureFiller(helper)
       |          }
       |        case _ =>
       |          fillerName = "hachure"
       |          if (!fillers.contains(fillerName)) {
       |            fillers(fillerName) = HachureFiller(helper)
       |          }
       |      }
       |    }
       |    fillers(fillerName)
       |  }
       |}
       |""".stripMargin
  }

  private def emitHachureFiller(rast: RastFile): String = {
    // RAST confirms: class HachureFiller with constructor(helper), fillPolygons, _fillPolygons, renderLines
    s"""$pkgDecl
       |
       |import scala.collection.mutable.ArrayBuffer
       |
       |class HachureFiller(helper: RenderHelper) extends PatternFiller {
       |
       |  def fillPolygons(polygonList: Vector[Vector[rough.Point]], o: ResolvedOptions): OpSet =
       |    _fillPolygons(polygonList, o)
       |
       |  protected def _fillPolygons(polygonList: Vector[Vector[rough.Point]], o: ResolvedOptions): OpSet = {
       |    val lines: Vector[rough.Line] = ScanLineHachure.polygonHachureLines(polygonList, o)
       |    val ops: Vector[Op] = renderLines(lines, o)
       |    OpSet(`type` = OpSetType.fillSketch, ops = ops)
       |  }
       |
       |  protected def renderLines(lines: Vector[rough.Line], o: ResolvedOptions): Vector[Op] = {
       |    val ops: ArrayBuffer[Op] = ArrayBuffer.empty
       |    for (line <- lines)
       |      ops ++= helper.doubleLineOps(line.p1.x, line.p1.y, line.p2.x, line.p2.y, o)
       |    ops.toVector
       |  }
       |}
       |""".stripMargin
  }

  private def emitHatchFiller(rast: RastFile): String = {
    // RAST confirms: class HatchFiller extends HachureFiller, overrides fillPolygons with cross-hatch
    s"""$pkgDecl
       |
       |final class HatchFiller(helper: RenderHelper) extends HachureFiller(helper) {
       |
       |  override def fillPolygons(polygonList: Vector[Vector[rough.Point]], o: ResolvedOptions): OpSet = {
       |    val set: OpSet = _fillPolygons(polygonList, o)
       |    val o2: ResolvedOptions = o.copy(hachureAngle = o.hachureAngle + 90)
       |    val set2: OpSet = _fillPolygons(polygonList, o2)
       |    set.copy(ops = set.ops ++ set2.ops)
       |  }
       |}
       |""".stripMargin
  }

  private def emitZigZagFiller(rast: RastFile): String = {
    // RAST confirms: class ZigZagFiller extends HachureFiller, overrides fillPolygons with zigzag geometry
    s"""$pkgDecl
       |
       |import scala.collection.mutable.ArrayBuffer
       |
       |final class ZigZagFiller(helper: RenderHelper) extends HachureFiller(helper) {
       |
       |  override def fillPolygons(polygonList: Vector[Vector[rough.Point]], o: ResolvedOptions): OpSet = {
       |    var gap: Double = o.hachureGap
       |    if (gap < 0) {
       |      gap = o.strokeWidth * 4
       |    }
       |    gap = Math.max(gap, 0.1)
       |    val o2: ResolvedOptions = o.copy(hachureGap = gap)
       |    val lines: Vector[rough.Line] = ScanLineHachure.polygonHachureLines(polygonList, o2)
       |    val zigZagAngle: Double = (Math.PI / 180) * o.hachureAngle
       |    val zigzagLines: ArrayBuffer[rough.Line] = ArrayBuffer.empty
       |    val dgx: Double = gap * 0.5 * Math.cos(zigZagAngle)
       |    val dgy: Double = gap * 0.5 * Math.sin(zigZagAngle)
       |    for (line <- lines) {
       |      val p1: rough.Point = line.p1
       |      val p2: rough.Point = line.p2
       |      if (truthy(Geometry.lineLength(rough.Line(p1, p2)))) {
       |        zigzagLines += rough.Line(rough.Point(p1.x - dgx, p1.y + dgy), rough.Point(p2.x, p2.y))
       |        zigzagLines += rough.Line(rough.Point(p1.x + dgx, p1.y - dgy), rough.Point(p2.x, p2.y))
       |      }
       |    }
       |    val ops: Vector[Op] = renderLines(zigzagLines.toVector, o)
       |    OpSet(`type` = OpSetType.fillSketch, ops = ops)
       |  }
       |
       |  private def truthy(d: Double): Boolean =
       |    d != 0.0 && !d.isNaN
       |}
       |""".stripMargin
  }

  private def emitZigZagLineFiller(rast: RastFile): String = {
    // RAST confirms: class ZigZagLineFiller implements PatternFiller, with zigzagLines private method
    s"""$pkgDecl
       |
       |import scala.collection.mutable.ArrayBuffer
       |
       |final class ZigZagLineFiller(helper: RenderHelper) extends PatternFiller {
       |
       |  def fillPolygons(polygonList: Vector[Vector[rough.Point]], o: ResolvedOptions): OpSet = {
       |    val gap: Double = if (o.hachureGap < 0) o.strokeWidth * 4 else o.hachureGap
       |    val zo: Double = if (o.zigzagOffset < 0) gap else o.zigzagOffset
       |    val o2: ResolvedOptions = o.copy(hachureGap = gap + zo)
       |    val lines: Vector[rough.Line] = ScanLineHachure.polygonHachureLines(polygonList, o2)
       |    OpSet(`type` = OpSetType.fillSketch, ops = zigzagLines(lines, zo, o2))
       |  }
       |
       |  private def zigzagLines(lines: Vector[rough.Line], zo: Double, o: ResolvedOptions): Vector[Op] = {
       |    val ops: ArrayBuffer[Op] = ArrayBuffer.empty
       |    lines.foreach { line =>
       |      val length: Double = Geometry.lineLength(line)
       |      val count: Long = Math.round(length / (2 * zo))
       |      var p1: rough.Point = line.p1
       |      var p2: rough.Point = line.p2
       |      if (p1.x > p2.x) {
       |        p1 = line.p2
       |        p2 = line.p1
       |      }
       |      val alpha: Double = Math.atan((p2.y - p1.y) / (p2.x - p1.x))
       |      var i: Int = 0
       |      while (i < count) {
       |        val lstart: Double = i * 2 * zo
       |        val lend: Double = (i + 1) * 2 * zo
       |        val dz: Double = Math.sqrt(2 * Math.pow(zo, 2))
       |        val startX: Double = p1.x + (lstart * Math.cos(alpha))
       |        val startY: Double = p1.y + lstart * Math.sin(alpha)
       |        val endX: Double = p1.x + (lend * Math.cos(alpha))
       |        val endY: Double = p1.y + (lend * Math.sin(alpha))
       |        val middleX: Double = startX + dz * Math.cos(alpha + Math.PI / 4)
       |        val middleY: Double = startY + dz * Math.sin(alpha + Math.PI / 4)
       |        ops ++= helper.doubleLineOps(startX, startY, middleX, middleY, o)
       |        ops ++= helper.doubleLineOps(middleX, middleY, endX, endY, o)
       |        i += 1
       |      }
       |    }
       |    ops.toVector
       |  }
       |}
       |""".stripMargin
  }

  private def emitDashedFiller(rast: RastFile): String = {
    // RAST confirms: class DashedFiller implements PatternFiller, with dashedLine private method
    s"""$pkgDecl
       |
       |import scala.collection.mutable.ArrayBuffer
       |
       |final class DashedFiller(helper: RenderHelper) extends PatternFiller {
       |
       |  def fillPolygons(polygonList: Vector[Vector[rough.Point]], o: ResolvedOptions): OpSet =
       |    OpSet(`type` = OpSetType.fillSketch, ops = dashedLine(ScanLineHachure.polygonHachureLines(polygonList, o), o))
       |
       |  private def dashedLine(lines: Vector[rough.Line], o: ResolvedOptions): Vector[Op] = {
       |    val offset: Double =
       |      if (o.dashOffset < 0) { if (o.hachureGap < 0) o.strokeWidth * 4 else o.hachureGap } else o.dashOffset
       |    val gap: Double =
       |      if (o.dashGap < 0) { if (o.hachureGap < 0) o.strokeWidth * 4 else o.hachureGap } else o.dashGap
       |    val ops: ArrayBuffer[Op] = ArrayBuffer.empty
       |    lines.foreach { line =>
       |      val length: Double = Geometry.lineLength(line)
       |      val count: Double = Math.floor(length / (offset + gap))
       |      val startOffset: Double = (length + gap - (count * (offset + gap))) / 2
       |      var p1: rough.Point = line.p1
       |      var p2: rough.Point = line.p2
       |      if (p1.x > p2.x) {
       |        p1 = line.p2
       |        p2 = line.p1
       |      }
       |      val alpha: Double = Math.atan((p2.y - p1.y) / (p2.x - p1.x))
       |      var i: Int = 0
       |      while (i < count) {
       |        val lstart: Double = i * (offset + gap)
       |        val lend: Double = lstart + offset
       |        val startX: Double = p1.x + (lstart * Math.cos(alpha)) + (startOffset * Math.cos(alpha))
       |        val startY: Double = p1.y + lstart * Math.sin(alpha) + (startOffset * Math.sin(alpha))
       |        val endX: Double = p1.x + (lend * Math.cos(alpha)) + (startOffset * Math.cos(alpha))
       |        val endY: Double = p1.y + (lend * Math.sin(alpha)) + (startOffset * Math.sin(alpha))
       |        ops ++= helper.doubleLineOps(startX, startY, endX, endY, o)
       |        i += 1
       |      }
       |    }
       |    ops.toVector
       |  }
       |}
       |""".stripMargin
  }

  private def emitDotFiller(rast: RastFile): String = {
    // RAST confirms: class DotFiller implements PatternFiller, with dotsOnLines private method
    s"""$pkgDecl
       |
       |import scala.collection.mutable.ArrayBuffer
       |
       |final class DotFiller(helper: RenderHelper) extends PatternFiller {
       |
       |  def fillPolygons(polygonList: Vector[Vector[rough.Point]], o: ResolvedOptions): OpSet = {
       |    val o2: ResolvedOptions = o.copy(hachureAngle = 0)
       |    val lines: Vector[rough.Line] = ScanLineHachure.polygonHachureLines(polygonList, o2)
       |    dotsOnLines(lines, o2)
       |  }
       |
       |  private def dotsOnLines(lines: Vector[rough.Line], o: ResolvedOptions): OpSet = {
       |    val ops: ArrayBuffer[Op] = ArrayBuffer.empty
       |    var gap: Double = o.hachureGap
       |    if (gap < 0) {
       |      gap = o.strokeWidth * 4
       |    }
       |    gap = Math.max(gap, 0.1)
       |    var fweight: Double = o.fillWeight
       |    if (fweight < 0) {
       |      fweight = o.strokeWidth / 2
       |    }
       |    val ro: Double = gap / 4
       |    for (line <- lines) {
       |      val length: Double = Geometry.lineLength(line)
       |      val dl: Double = length / gap
       |      val count: Double = Math.ceil(dl) - 1
       |      val offset: Double = length - (count * gap)
       |      val x: Double = ((line.p1.x + line.p2.x) / 2) - (gap / 4)
       |      val minY: Double = Math.min(line.p1.y, line.p2.y)
       |      var i: Int = 0
       |      while (i < count) {
       |        val y: Double = minY + offset + (i * gap)
       |        val cx: Double = (x - ro) + RoughMath.unseededRandom() * 2 * ro
       |        val cy: Double = (y - ro) + RoughMath.unseededRandom() * 2 * ro
       |        val el: OpSet = helper.ellipse(cx, cy, fweight, fweight, o)
       |        ops ++= el.ops
       |        i += 1
       |      }
       |    }
       |    OpSet(`type` = OpSetType.fillSketch, ops = ops.toVector)
       |  }
       |}
       |""".stripMargin
  }
}
