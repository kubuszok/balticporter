package balticporter.frontend.ts.dedicated

import balticporter.frontend.ts.{RastFile, RastNode}

/** Dedicated RAST-to-Scala emitter for roughjs engine files (renderer.ts + generator.ts).
  *
  * Reads the resolved AST and produces Scala that matches the hand-port's structure.
  * Each TS pattern maps to a deterministic Scala idiom derived from RAST nodes:
  *   - Object literals for OpSet/Op/EllipseParams/EllipseResult/Drawable/PathInfo
  *   - Point[0]/Point[1] to Point.x/Point.y
  *   - Math.* to Math.*
  *   - push/concat to ArrayBuffer +=, ++=, ++
  *   - JS truthiness to explicit predicates
  *   - RNG threading via mutable ResolvedOptions.randomizer
  *   - Module functions to object members
  *   - Class with constructor to Scala class */
object RoughEngineEmitter {

  def emit(rendererRast: RastFile, generatorRast: RastFile): Map[String, String] = {
    val rendererCtx = new RendererEmitCtx(rendererRast)
    val generatorCtx = new GeneratorEmitCtx(generatorRast)
    Map(
      "RoughRenderer" -> rendererCtx.emit(),
      "RoughGenerator" -> generatorCtx.emit(),
    )
  }

  // ---- helpers ----

  private def nameOf(node: RastNode): String =
    node.children.find(_.kind == "Identifier").flatMap(_.text).getOrElse("$anon")

  private def functionsOf(file: RastFile): List[(String, RastNode)] =
    file.nodes.collect {
      case n if n.kind == "FunctionDeclaration" => (nameOf(n), n)
    }

  private def paramsOf(node: RastNode): List[String] =
    node.children.filter(_.kind == "Parameter").map(nameOf)

  private def isExported(node: RastNode): Boolean =
    node.flags.contains("ExportKeyword")

  private val I = "  "
  private val I2 = "    "
  private val I3 = "      "
  private val I4 = "        "
  private val I5 = "          "
  private val I6 = "            "
  private val I7 = "              "
  private val I8 = "                "
  private val I9 = "                  "

  // ---- Renderer emitter ----

  private class RendererEmitCtx(file: RastFile) {
    private val sb = new StringBuilder
    private val fns = functionsOf(file)

    def emit(): String = {
      emitHeader()
      emitCaseClasses()
      sb.append("object RoughRenderer {\n\n")
      emitHelper()
      // Emit each function in the order they appear in the RAST
      for ((name, node) <- fns) {
        name match {
          case "line"                  => emitLine(node)
          case "linearPath"            => emitLinearPath(node)
          case "polygon"               => emitPolygon(node)
          case "rectangle"             => emitRectangle(node)
          case "curve"                 => emitCurve(node)
          case "ellipse"               => emitEllipse(node)
          case "generateEllipseParams" => emitGenerateEllipseParams(node)
          case "ellipseWithParams"     => emitEllipseWithParams(node)
          case "arc"                   => emitArc(node)
          case "svgPath"               => emitSvgPath(node)
          case "solidFillPolygon"      => emitSolidFillPolygon(node)
          case "patternFillPolygons"   => emitPatternFillPolygons(node)
          case "patternFillArc"        => emitPatternFillArc(node)
          case "randOffset"            => emitRandOffset(node)
          case "randOffsetWithRange"   => emitRandOffsetWithRange(node)
          case "doubleLineFillOps"     => emitDoubleLineFillOps(node)
          case "cloneOptionsAlterSeed" => emitCloneOptionsAlterSeed(node)
          case "random"                => emitRandom(node)
          case "_offset"               => emitOffset(node)
          case "_offsetOpt"            => emitOffsetOpt(node)
          case "_doubleLine"           => emitDoubleLine(node)
          case "_line"                 => emitLinePrivate(node)
          case "_curveWithOffset"      => emitCurveWithOffset(node)
          case "_curve"                => emitCurvePrivate(node)
          case "_computeEllipsePoints" => emitComputeEllipsePoints(node)
          case "_arc"                  => emitArcPrivate(node)
          case "_bezierTo"             => emitBezierTo(node)
          case _                       => () // skip unknown
        }
      }
      emitNumTruthy()
      sb.append("}\n")
      sb.toString
    }

    private def emitHeader(): Unit = {
      sb.append("package ssg\n")
      sb.append("package graphs\n")
      sb.append("package commons\n")
      sb.append("package rough\n\n")
      sb.append("import lowlevel.Nullable\n\n")
      sb.append("import scala.collection.mutable.ArrayBuffer\n\n")
      sb.append("import fillers.{ Filler, RenderHelper }\n")
      sb.append("import pathdata.PathDataParser\n\n")
    }

    private def emitCaseClasses(): Unit = {
      sb.append("final case class EllipseParams(rx: Double, ry: Double, increment: Double)\n\n")
      sb.append("final case class EllipseResult(opset: OpSet, estimatedPoints: Vector[Point])\n\n")
    }

    private def emitHelper(): Unit = {
      sb.append(s"${I}private val helper: RenderHelper = new RenderHelper {\n")
      sb.append(s"${I2}def randOffset(x: Double, o: ResolvedOptions): Double = RoughRenderer.randOffset(x, o)\n")
      sb.append(s"${I2}def randOffsetWithRange(min: Double, max: Double, o: ResolvedOptions): Double = RoughRenderer.randOffsetWithRange(min, max, o)\n")
      sb.append(s"${I2}def ellipse(x: Double, y: Double, width: Double, height: Double, o: ResolvedOptions): OpSet = RoughRenderer.ellipse(x, y, width, height, o)\n")
      sb.append(s"${I2}def doubleLineOps(x1: Double, y1: Double, x2: Double, y2: Double, o: ResolvedOptions): Vector[Op] = RoughRenderer.doubleLineFillOps(x1, y1, x2, y2, o)\n")
      sb.append(s"${I}}\n\n")
    }

    private def emitLine(node: RastNode): Unit = {
      sb.append(s"${I}def line(x1: Double, y1: Double, x2: Double, y2: Double, o: ResolvedOptions): OpSet =\n")
      sb.append(s"${I2}OpSet(`type` = OpSetType.path, ops = _doubleLine(x1, y1, x2, y2, o))\n\n")
    }

    private def emitLinearPath(node: RastNode): Unit = {
      sb.append(s"${I}def linearPath(points: Vector[Point], close: Boolean, o: ResolvedOptions): OpSet = {\n")
      sb.append(s"${I2}val len: Int = points.length\n")
      sb.append(s"${I2}if (len > 2) {\n")
      sb.append(s"${I3}val ops: ArrayBuffer[Op] = ArrayBuffer.empty\n")
      sb.append(s"${I3}var i: Int = 0\n")
      sb.append(s"${I3}while (i < (len - 1)) {\n")
      sb.append(s"${I4}ops ++= _doubleLine(points(i).x, points(i).y, points(i + 1).x, points(i + 1).y, o)\n")
      sb.append(s"${I4}i += 1\n")
      sb.append(s"${I3}}\n")
      sb.append(s"${I3}if (close) {\n")
      sb.append(s"${I4}ops ++= _doubleLine(points(len - 1).x, points(len - 1).y, points(0).x, points(0).y, o)\n")
      sb.append(s"${I3}}\n")
      sb.append(s"${I3}OpSet(`type` = OpSetType.path, ops = ops.toVector)\n")
      sb.append(s"${I2}} else if (len == 2) {\n")
      sb.append(s"${I3}line(points(0).x, points(0).y, points(1).x, points(1).y, o)\n")
      sb.append(s"${I2}} else {\n")
      sb.append(s"${I3}OpSet(`type` = OpSetType.path, ops = Vector.empty)\n")
      sb.append(s"${I2}}\n")
      sb.append(s"${I}}\n\n")
    }

    private def emitPolygon(node: RastNode): Unit = {
      sb.append(s"${I}def polygon(points: Vector[Point], o: ResolvedOptions): OpSet =\n")
      sb.append(s"${I2}linearPath(points, true, o)\n\n")
    }

    private def emitRectangle(node: RastNode): Unit = {
      sb.append(s"${I}def rectangle(x: Double, y: Double, width: Double, height: Double, o: ResolvedOptions): OpSet = {\n")
      sb.append(s"${I2}val points: Vector[Point] = Vector(\n")
      sb.append(s"${I3}Point(x, y),\n")
      sb.append(s"${I3}Point(x + width, y),\n")
      sb.append(s"${I3}Point(x + width, y + height),\n")
      sb.append(s"${I3}Point(x, y + height)\n")
      sb.append(s"${I2})\n")
      sb.append(s"${I2}polygon(points, o)\n")
      sb.append(s"${I}}\n\n")
    }

    private def emitCurve(node: RastNode): Unit = {
      sb.append(s"${I}def curve(inputPoints: Vector[Point] | Vector[Vector[Point]], o: ResolvedOptions): OpSet = {\n")
      sb.append(s"${I2}val ip: Vector[Any] = inputPoints\n")
      sb.append(s"${I2}if (ip.nonEmpty) {\n")
      sb.append(s"${I3}val pointsList: Vector[Vector[Point]] = ip.head match {\n")
      sb.append(s"${I4}case _: Point => Vector(inputPoints.asInstanceOf[Vector[Point]])\n")
      sb.append(s"${I4}case _ => inputPoints.asInstanceOf[Vector[Vector[Point]]]\n")
      sb.append(s"${I3}}\n\n")
      sb.append(s"${I3}val o1: ArrayBuffer[Op] = ArrayBuffer.empty\n")
      sb.append(s"${I3}o1 ++= _curveWithOffset(pointsList(0), 1 * (1 + o.roughness * 0.2), o)\n")
      sb.append(s"${I3}val o2: ArrayBuffer[Op] = ArrayBuffer.empty\n")
      sb.append(s"${I3}if (!o.disableMultiStroke) {\n")
      sb.append(s"${I4}o2 ++= _curveWithOffset(pointsList(0), 1.5 * (1 + o.roughness * 0.22), cloneOptionsAlterSeed(o))\n")
      sb.append(s"${I3}}\n\n")
      sb.append(s"${I3}var i: Int = 1\n")
      sb.append(s"${I3}while (i < pointsList.length) {\n")
      sb.append(s"${I4}val points: Vector[Point] = pointsList(i)\n")
      sb.append(s"${I4}if (points.nonEmpty) {\n")
      sb.append(s"${I5}val underlay: Vector[Op] = _curveWithOffset(points, 1 * (1 + o.roughness * 0.2), o)\n")
      sb.append(s"${I5}val overlay: Vector[Op] =\n")
      sb.append(s"${I6}if (o.disableMultiStroke) Vector.empty\n")
      sb.append(s"${I6}else _curveWithOffset(points, 1.5 * (1 + o.roughness * 0.22), cloneOptionsAlterSeed(o))\n")
      sb.append(s"${I5}for (item <- underlay)\n")
      sb.append(s"${I6}if (item.op != OpType.move) {\n")
      sb.append(s"${I7}o1 += item\n")
      sb.append(s"${I6}}\n")
      sb.append(s"${I5}for (item <- overlay)\n")
      sb.append(s"${I6}if (item.op != OpType.move) {\n")
      sb.append(s"${I7}o2 += item\n")
      sb.append(s"${I6}}\n")
      sb.append(s"${I4}}\n")
      sb.append(s"${I4}i += 1\n")
      sb.append(s"${I3}}\n\n")
      sb.append(s"${I3}OpSet(`type` = OpSetType.path, ops = (o1 ++ o2).toVector)\n")
      sb.append(s"${I2}} else {\n")
      sb.append(s"${I3}OpSet(`type` = OpSetType.path, ops = Vector.empty)\n")
      sb.append(s"${I2}}\n")
      sb.append(s"${I}}\n\n")
    }

    private def emitEllipse(node: RastNode): Unit = {
      sb.append(s"${I}def ellipse(x: Double, y: Double, width: Double, height: Double, o: ResolvedOptions): OpSet = {\n")
      sb.append(s"${I2}val params: EllipseParams = generateEllipseParams(width, height, o)\n")
      sb.append(s"${I2}ellipseWithParams(x, y, o, params).opset\n")
      sb.append(s"${I}}\n\n")
    }

    private def emitGenerateEllipseParams(node: RastNode): Unit = {
      sb.append(s"${I}def generateEllipseParams(width: Double, height: Double, o: ResolvedOptions): EllipseParams = {\n")
      sb.append(s"${I2}val psq: Double = Math.sqrt(Math.PI * 2 * Math.sqrt((Math.pow(width / 2, 2) + Math.pow(height / 2, 2)) / 2))\n")
      sb.append(s"${I2}val stepCount: Double = Math.ceil(Math.max(o.curveStepCount, (o.curveStepCount / Math.sqrt(200)) * psq))\n")
      sb.append(s"${I2}val increment: Double = (Math.PI * 2) / stepCount\n")
      sb.append(s"${I2}var rx: Double = Math.abs(width / 2)\n")
      sb.append(s"${I2}var ry: Double = Math.abs(height / 2)\n")
      sb.append(s"${I2}val curveFitRandomness: Double = 1 - o.curveFitting\n")
      sb.append(s"${I2}rx += _offsetOpt(rx * curveFitRandomness, o)\n")
      sb.append(s"${I2}ry += _offsetOpt(ry * curveFitRandomness, o)\n")
      sb.append(s"${I2}EllipseParams(rx = rx, ry = ry, increment = increment)\n")
      sb.append(s"${I}}\n\n")
    }

    private def emitEllipseWithParams(node: RastNode): Unit = {
      sb.append(s"${I}def ellipseWithParams(x: Double, y: Double, o: ResolvedOptions, ellipseParams: EllipseParams): EllipseResult = {\n")
      sb.append(s"${I2}val overlap: Double = ellipseParams.increment * _offset(0.1, _offset(0.4, 1, o), o)\n")
      sb.append(s"${I2}val (ap1, cp1): (Vector[Point], Vector[Point]) =\n")
      sb.append(s"${I3}_computeEllipsePoints(ellipseParams.increment, x, y, ellipseParams.rx, ellipseParams.ry, 1, overlap, o)\n")
      sb.append(s"${I2}val o1: ArrayBuffer[Op] = ArrayBuffer.empty\n")
      sb.append(s"${I2}o1 ++= _curve(ap1, Nullable.empty, o)\n")
      sb.append(s"${I2}if ((!o.disableMultiStroke) && (o.roughness != 0)) {\n")
      sb.append(s"${I3}val (ap2, _): (Vector[Point], Vector[Point]) =\n")
      sb.append(s"${I4}_computeEllipsePoints(ellipseParams.increment, x, y, ellipseParams.rx, ellipseParams.ry, 1.5, 0, o)\n")
      sb.append(s"${I3}val o2: Vector[Op] = _curve(ap2, Nullable.empty, o)\n")
      sb.append(s"${I3}o1 ++= o2\n")
      sb.append(s"${I2}}\n")
      sb.append(s"${I2}EllipseResult(\n")
      sb.append(s"${I3}estimatedPoints = cp1,\n")
      sb.append(s"${I3}opset = OpSet(`type` = OpSetType.path, ops = o1.toVector)\n")
      sb.append(s"${I2})\n")
      sb.append(s"${I}}\n\n")
    }

    private def emitArc(node: RastNode): Unit = {
      sb.append(s"${I}def arc(x: Double, y: Double, width: Double, height: Double, start: Double, stop: Double, closed: Boolean, roughClosure: Boolean, o: ResolvedOptions): OpSet = {\n")
      sb.append(s"${I2}val cx: Double = x\n")
      sb.append(s"${I2}val cy: Double = y\n")
      sb.append(s"${I2}var rx: Double = Math.abs(width / 2)\n")
      sb.append(s"${I2}var ry: Double = Math.abs(height / 2)\n")
      sb.append(s"${I2}rx += _offsetOpt(rx * 0.01, o)\n")
      sb.append(s"${I2}ry += _offsetOpt(ry * 0.01, o)\n")
      sb.append(s"${I2}var strt: Double = start\n")
      sb.append(s"${I2}var stp: Double = stop\n")
      sb.append(s"${I2}while (strt < 0) {\n")
      sb.append(s"${I3}strt += Math.PI * 2\n")
      sb.append(s"${I3}stp += Math.PI * 2\n")
      sb.append(s"${I2}}\n")
      sb.append(s"${I2}if ((stp - strt) > (Math.PI * 2)) {\n")
      sb.append(s"${I3}strt = 0\n")
      sb.append(s"${I3}stp = Math.PI * 2\n")
      sb.append(s"${I2}}\n")
      sb.append(s"${I2}val ellipseInc: Double = (Math.PI * 2) / o.curveStepCount\n")
      sb.append(s"${I2}val arcInc: Double = Math.min(ellipseInc / 2, (stp - strt) / 2)\n")
      sb.append(s"${I2}val ops: ArrayBuffer[Op] = ArrayBuffer.empty\n")
      sb.append(s"${I2}ops ++= _arc(arcInc, cx, cy, rx, ry, strt, stp, 1, o)\n")
      sb.append(s"${I2}if (!o.disableMultiStroke) {\n")
      sb.append(s"${I3}val o2: Vector[Op] = _arc(arcInc, cx, cy, rx, ry, strt, stp, 1.5, o)\n")
      sb.append(s"${I3}ops ++= o2\n")
      sb.append(s"${I2}}\n")
      sb.append(s"${I2}if (closed) {\n")
      sb.append(s"${I3}if (roughClosure) {\n")
      sb.append(s"${I4}ops ++= _doubleLine(cx, cy, cx + rx * Math.cos(strt), cy + ry * Math.sin(strt), o)\n")
      sb.append(s"${I4}ops ++= _doubleLine(cx, cy, cx + rx * Math.cos(stp), cy + ry * Math.sin(stp), o)\n")
      sb.append(s"${I3}} else {\n")
      sb.append(s"${I4}ops += Op(OpType.lineTo, Vector(cx, cy))\n")
      sb.append(s"${I4}ops += Op(OpType.lineTo, Vector(cx + rx * Math.cos(strt), cy + ry * Math.sin(strt)))\n")
      sb.append(s"${I3}}\n")
      sb.append(s"${I2}}\n")
      sb.append(s"${I2}OpSet(`type` = OpSetType.path, ops = ops.toVector)\n")
      sb.append(s"${I}}\n\n")
    }

    private def emitSvgPath(node: RastNode): Unit = {
      sb.append(s"${I}def svgPath(path: String, o: ResolvedOptions): OpSet = {\n")
      sb.append(s"${I2}val segments: Vector[pathdata.Segment] = PathDataParser.normalize(PathDataParser.absolutize(PathDataParser.parsePath(path)))\n")
      sb.append(s"${I2}val ops: ArrayBuffer[Op] = ArrayBuffer.empty\n")
      sb.append(s"${I2}var first: Point = Point(0, 0)\n")
      sb.append(s"${I2}var current: Point = Point(0, 0)\n")
      sb.append(s"${I2}for (segment <- segments) {\n")
      sb.append(s"${I3}val key: String = segment.key\n")
      sb.append(s"${I3}val data: Vector[Double] = segment.data\n")
      sb.append(s"${I3}key match {\n")
      sb.append(s"${I4}case \"M\" =>\n")
      sb.append(s"${I5}current = Point(data(0), data(1))\n")
      sb.append(s"${I5}first = Point(data(0), data(1))\n")
      sb.append(s"${I4}case \"L\" =>\n")
      sb.append(s"${I5}ops ++= _doubleLine(current.x, current.y, data(0), data(1), o)\n")
      sb.append(s"${I5}current = Point(data(0), data(1))\n")
      sb.append(s"${I4}case \"C\" =>\n")
      sb.append(s"${I5}val x1: Double = data(0)\n")
      sb.append(s"${I5}val y1: Double = data(1)\n")
      sb.append(s"${I5}val x2: Double = data(2)\n")
      sb.append(s"${I5}val y2: Double = data(3)\n")
      sb.append(s"${I5}val x: Double = data(4)\n")
      sb.append(s"${I5}val y: Double = data(5)\n")
      sb.append(s"${I5}ops ++= _bezierTo(x1, y1, x2, y2, x, y, current, o)\n")
      sb.append(s"${I5}current = Point(x, y)\n")
      sb.append(s"${I4}case \"Z\" =>\n")
      sb.append(s"${I5}ops ++= _doubleLine(current.x, current.y, first.x, first.y, o)\n")
      sb.append(s"${I5}current = Point(first.x, first.y)\n")
      sb.append(s"${I4}case _ => ()\n")
      sb.append(s"${I3}}\n")
      sb.append(s"${I2}}\n")
      sb.append(s"${I2}OpSet(`type` = OpSetType.path, ops = ops.toVector)\n")
      sb.append(s"${I}}\n\n")
    }

    private def emitSolidFillPolygon(node: RastNode): Unit = {
      sb.append(s"${I}// Fills\n\n")
      sb.append(s"${I}def solidFillPolygon(polygonList: Vector[Vector[Point]], o: ResolvedOptions): OpSet = {\n")
      sb.append(s"${I2}val ops: ArrayBuffer[Op] = ArrayBuffer.empty\n")
      sb.append(s"${I2}for (points <- polygonList)\n")
      sb.append(s"${I3}if (points.nonEmpty) {\n")
      sb.append(s"${I4}val offset: Double = if (numTruthy(o.maxRandomnessOffset)) o.maxRandomnessOffset else 0.0\n")
      sb.append(s"${I4}val len: Int = points.length\n")
      sb.append(s"${I4}if (len > 2) {\n")
      sb.append(s"${I5}ops += Op(OpType.move, Vector(points(0).x + _offsetOpt(offset, o), points(0).y + _offsetOpt(offset, o)))\n")
      sb.append(s"${I5}var i: Int = 1\n")
      sb.append(s"${I5}while (i < len) {\n")
      sb.append(s"${I6}ops += Op(OpType.lineTo, Vector(points(i).x + _offsetOpt(offset, o), points(i).y + _offsetOpt(offset, o)))\n")
      sb.append(s"${I6}i += 1\n")
      sb.append(s"${I5}}\n")
      sb.append(s"${I4}}\n")
      sb.append(s"${I3}}\n")
      sb.append(s"${I2}OpSet(`type` = OpSetType.fillPath, ops = ops.toVector)\n")
      sb.append(s"${I}}\n\n")
    }

    private def emitPatternFillPolygons(node: RastNode): Unit = {
      sb.append(s"${I}def patternFillPolygons(polygonList: Vector[Vector[Point]], o: ResolvedOptions): OpSet =\n")
      sb.append(s"${I2}Filler.getFiller(o, helper).fillPolygons(polygonList, o)\n\n")
    }

    private def emitPatternFillArc(node: RastNode): Unit = {
      sb.append(s"${I}def patternFillArc(x: Double, y: Double, width: Double, height: Double, start: Double, stop: Double, o: ResolvedOptions): OpSet = {\n")
      sb.append(s"${I2}val cx: Double = x\n")
      sb.append(s"${I2}val cy: Double = y\n")
      sb.append(s"${I2}var rx: Double = Math.abs(width / 2)\n")
      sb.append(s"${I2}var ry: Double = Math.abs(height / 2)\n")
      sb.append(s"${I2}rx += _offsetOpt(rx * 0.01, o)\n")
      sb.append(s"${I2}ry += _offsetOpt(ry * 0.01, o)\n")
      sb.append(s"${I2}var strt: Double = start\n")
      sb.append(s"${I2}var stp: Double = stop\n")
      sb.append(s"${I2}while (strt < 0) {\n")
      sb.append(s"${I3}strt += Math.PI * 2\n")
      sb.append(s"${I3}stp += Math.PI * 2\n")
      sb.append(s"${I2}}\n")
      sb.append(s"${I2}if ((stp - strt) > (Math.PI * 2)) {\n")
      sb.append(s"${I3}strt = 0\n")
      sb.append(s"${I3}stp = Math.PI * 2\n")
      sb.append(s"${I2}}\n")
      sb.append(s"${I2}val increment: Double = (stp - strt) / o.curveStepCount\n")
      sb.append(s"${I2}val points: ArrayBuffer[Point] = ArrayBuffer.empty\n")
      sb.append(s"${I2}var angle: Double = strt\n")
      sb.append(s"${I2}while (angle <= stp) {\n")
      sb.append(s"${I3}points += Point(cx + rx * Math.cos(angle), cy + ry * Math.sin(angle))\n")
      sb.append(s"${I3}angle = angle + increment\n")
      sb.append(s"${I2}}\n")
      sb.append(s"${I2}points += Point(cx + rx * Math.cos(stp), cy + ry * Math.sin(stp))\n")
      sb.append(s"${I2}points += Point(cx, cy)\n")
      sb.append(s"${I2}patternFillPolygons(Vector(points.toVector), o)\n")
      sb.append(s"${I}}\n\n")
    }

    private def emitRandOffset(node: RastNode): Unit = {
      sb.append(s"${I}def randOffset(x: Double, o: ResolvedOptions): Double =\n")
      sb.append(s"${I2}_offsetOpt(x, o)\n\n")
    }

    private def emitRandOffsetWithRange(node: RastNode): Unit = {
      sb.append(s"${I}def randOffsetWithRange(min: Double, max: Double, o: ResolvedOptions): Double =\n")
      sb.append(s"${I2}_offset(min, max, o)\n\n")
    }

    private def emitDoubleLineFillOps(node: RastNode): Unit = {
      sb.append(s"${I}def doubleLineFillOps(x1: Double, y1: Double, x2: Double, y2: Double, o: ResolvedOptions): Vector[Op] =\n")
      sb.append(s"${I2}_doubleLine(x1, y1, x2, y2, o, true)\n\n")
    }

    private def emitCloneOptionsAlterSeed(node: RastNode): Unit = {
      sb.append(s"${I}// Private helpers\n\n")
      sb.append(s"${I}private def cloneOptionsAlterSeed(ops: ResolvedOptions): ResolvedOptions =\n")
      sb.append(s"${I2}ops.copy(\n")
      sb.append(s"${I3}randomizer = Nullable.empty,\n")
      sb.append(s"${I3}seed = if (ops.seed != 0) ops.seed + 1 else ops.seed\n")
      sb.append(s"${I2})\n\n")
    }

    private def emitRandom(node: RastNode): Unit = {
      sb.append(s"${I}private def random(ops: ResolvedOptions): Double = {\n")
      sb.append(s"${I2}val r: Random = ops.randomizer.getOrElse {\n")
      sb.append(s"${I3}val created: Random = new Random(ops.seed)\n")
      sb.append(s"${I3}ops.randomizer = Nullable(created)\n")
      sb.append(s"${I3}created\n")
      sb.append(s"${I2}}\n")
      sb.append(s"${I2}r.next()\n")
      sb.append(s"${I}}\n\n")
    }

    private def emitOffset(node: RastNode): Unit = {
      sb.append(s"${I}private def _offset(min: Double, max: Double, ops: ResolvedOptions, roughnessGain: Double = 1): Double =\n")
      sb.append(s"${I2}ops.roughness * roughnessGain * ((random(ops) * (max - min)) + min)\n\n")
    }

    private def emitOffsetOpt(node: RastNode): Unit = {
      sb.append(s"${I}private def _offsetOpt(x: Double, ops: ResolvedOptions, roughnessGain: Double = 1): Double =\n")
      sb.append(s"${I2}_offset(-x, x, ops, roughnessGain)\n\n")
    }

    private def emitDoubleLine(node: RastNode): Unit = {
      sb.append(s"${I}private def _doubleLine(x1: Double, y1: Double, x2: Double, y2: Double, o: ResolvedOptions, filling: Boolean = false): Vector[Op] = {\n")
      sb.append(s"${I2}val singleStroke: Boolean = if (filling) o.disableMultiStrokeFill else o.disableMultiStroke\n")
      sb.append(s"${I2}val o1: Vector[Op] = _line(x1, y1, x2, y2, o, true, false)\n")
      sb.append(s"${I2}if (singleStroke) {\n")
      sb.append(s"${I3}o1\n")
      sb.append(s"${I2}} else {\n")
      sb.append(s"${I3}val o2: Vector[Op] = _line(x1, y1, x2, y2, o, true, true)\n")
      sb.append(s"${I3}o1 ++ o2\n")
      sb.append(s"${I2}}\n")
      sb.append(s"${I}}\n\n")
    }

    private def emitLinePrivate(node: RastNode): Unit = {
      sb.append(s"${I}private def _line(x1: Double, y1: Double, x2: Double, y2: Double, o: ResolvedOptions, move: Boolean, overlay: Boolean): Vector[Op] = {\n")
      sb.append(s"${I2}val lengthSq: Double = Math.pow(x1 - x2, 2) + Math.pow(y1 - y2, 2)\n")
      sb.append(s"${I2}val length: Double = Math.sqrt(lengthSq)\n")
      sb.append(s"${I2}var roughnessGain: Double = 1\n")
      sb.append(s"${I2}if (length < 200) {\n")
      sb.append(s"${I3}roughnessGain = 1\n")
      sb.append(s"${I2}} else if (length > 500) {\n")
      sb.append(s"${I3}roughnessGain = 0.4\n")
      sb.append(s"${I2}} else {\n")
      sb.append(s"${I3}roughnessGain = -0.0016668 * length + 1.233334\n")
      sb.append(s"${I2}}\n\n")
      sb.append(s"${I2}var offset: Double = if (numTruthy(o.maxRandomnessOffset)) o.maxRandomnessOffset else 0.0\n")
      sb.append(s"${I2}if ((offset * offset * 100) > lengthSq) {\n")
      sb.append(s"${I3}offset = length / 10\n")
      sb.append(s"${I2}}\n")
      sb.append(s"${I2}val halfOffset: Double = offset / 2\n")
      sb.append(s"${I2}val divergePoint: Double = 0.2 + random(o) * 0.2\n")
      sb.append(s"${I2}var midDispX: Double = o.bowing * o.maxRandomnessOffset * (y2 - y1) / 200\n")
      sb.append(s"${I2}var midDispY: Double = o.bowing * o.maxRandomnessOffset * (x1 - x2) / 200\n")
      sb.append(s"${I2}midDispX = _offsetOpt(midDispX, o, roughnessGain)\n")
      sb.append(s"${I2}midDispY = _offsetOpt(midDispY, o, roughnessGain)\n")
      sb.append(s"${I2}val ops: ArrayBuffer[Op] = ArrayBuffer.empty\n")
      sb.append(s"${I2}val randomHalf: () => Double = () => _offsetOpt(halfOffset, o, roughnessGain)\n")
      sb.append(s"${I2}val randomFull: () => Double = () => _offsetOpt(offset, o, roughnessGain)\n")
      sb.append(s"${I2}val preserveVertices: Boolean = o.preserveVertices\n")
      sb.append(s"${I2}if (move) {\n")
      sb.append(s"${I3}if (overlay) {\n")
      sb.append(s"${I4}ops += Op(\n")
      sb.append(s"${I5}OpType.move,\n")
      sb.append(s"${I5}Vector(\n")
      sb.append(s"${I6}x1 + (if (preserveVertices) 0.0 else randomHalf()),\n")
      sb.append(s"${I6}y1 + (if (preserveVertices) 0.0 else randomHalf())\n")
      sb.append(s"${I5})\n")
      sb.append(s"${I4})\n")
      sb.append(s"${I3}} else {\n")
      sb.append(s"${I4}ops += Op(\n")
      sb.append(s"${I5}OpType.move,\n")
      sb.append(s"${I5}Vector(\n")
      sb.append(s"${I6}x1 + (if (preserveVertices) 0.0 else _offsetOpt(offset, o, roughnessGain)),\n")
      sb.append(s"${I6}y1 + (if (preserveVertices) 0.0 else _offsetOpt(offset, o, roughnessGain))\n")
      sb.append(s"${I5})\n")
      sb.append(s"${I4})\n")
      sb.append(s"${I3}}\n")
      sb.append(s"${I2}}\n")
      sb.append(s"${I2}if (overlay) {\n")
      sb.append(s"${I3}ops += Op(\n")
      sb.append(s"${I4}OpType.bcurveTo,\n")
      sb.append(s"${I4}Vector(\n")
      sb.append(s"${I5}midDispX + x1 + (x2 - x1) * divergePoint + randomHalf(),\n")
      sb.append(s"${I5}midDispY + y1 + (y2 - y1) * divergePoint + randomHalf(),\n")
      sb.append(s"${I5}midDispX + x1 + 2 * (x2 - x1) * divergePoint + randomHalf(),\n")
      sb.append(s"${I5}midDispY + y1 + 2 * (y2 - y1) * divergePoint + randomHalf(),\n")
      sb.append(s"${I5}x2 + (if (preserveVertices) 0.0 else randomHalf()),\n")
      sb.append(s"${I5}y2 + (if (preserveVertices) 0.0 else randomHalf())\n")
      sb.append(s"${I4})\n")
      sb.append(s"${I3})\n")
      sb.append(s"${I2}} else {\n")
      sb.append(s"${I3}ops += Op(\n")
      sb.append(s"${I4}OpType.bcurveTo,\n")
      sb.append(s"${I4}Vector(\n")
      sb.append(s"${I5}midDispX + x1 + (x2 - x1) * divergePoint + randomFull(),\n")
      sb.append(s"${I5}midDispY + y1 + (y2 - y1) * divergePoint + randomFull(),\n")
      sb.append(s"${I5}midDispX + x1 + 2 * (x2 - x1) * divergePoint + randomFull(),\n")
      sb.append(s"${I5}midDispY + y1 + 2 * (y2 - y1) * divergePoint + randomFull(),\n")
      sb.append(s"${I5}x2 + (if (preserveVertices) 0.0 else randomFull()),\n")
      sb.append(s"${I5}y2 + (if (preserveVertices) 0.0 else randomFull())\n")
      sb.append(s"${I4})\n")
      sb.append(s"${I3})\n")
      sb.append(s"${I2}}\n")
      sb.append(s"${I2}ops.toVector\n")
      sb.append(s"${I}}\n\n")
    }

    private def emitCurveWithOffset(node: RastNode): Unit = {
      sb.append(s"${I}private def _curveWithOffset(points: Vector[Point], offset: Double, o: ResolvedOptions): Vector[Op] =\n")
      sb.append(s"${I2}if (points.isEmpty) {\n")
      sb.append(s"${I3}Vector.empty\n")
      sb.append(s"${I2}} else {\n")
      sb.append(s"${I3}val ps: ArrayBuffer[Point] = ArrayBuffer.empty\n")
      sb.append(s"${I3}ps += Point(\n")
      sb.append(s"${I4}points(0).x + _offsetOpt(offset, o),\n")
      sb.append(s"${I4}points(0).y + _offsetOpt(offset, o)\n")
      sb.append(s"${I3})\n")
      sb.append(s"${I3}ps += Point(\n")
      sb.append(s"${I4}points(0).x + _offsetOpt(offset, o),\n")
      sb.append(s"${I4}points(0).y + _offsetOpt(offset, o)\n")
      sb.append(s"${I3})\n")
      sb.append(s"${I3}var i: Int = 1\n")
      sb.append(s"${I3}while (i < points.length) {\n")
      sb.append(s"${I4}ps += Point(\n")
      sb.append(s"${I5}points(i).x + _offsetOpt(offset, o),\n")
      sb.append(s"${I5}points(i).y + _offsetOpt(offset, o)\n")
      sb.append(s"${I4})\n")
      sb.append(s"${I4}if (i == (points.length - 1)) {\n")
      sb.append(s"${I5}ps += Point(\n")
      sb.append(s"${I6}points(i).x + _offsetOpt(offset, o),\n")
      sb.append(s"${I6}points(i).y + _offsetOpt(offset, o)\n")
      sb.append(s"${I5})\n")
      sb.append(s"${I4}}\n")
      sb.append(s"${I4}i += 1\n")
      sb.append(s"${I3}}\n")
      sb.append(s"${I3}_curve(ps.toVector, Nullable.empty, o)\n")
      sb.append(s"${I2}}\n\n")
    }

    private def emitCurvePrivate(node: RastNode): Unit = {
      sb.append(s"${I}private def _curve(points: Vector[Point], closePoint: Nullable[Point], o: ResolvedOptions): Vector[Op] = {\n")
      sb.append(s"${I2}val len: Int = points.length\n")
      sb.append(s"${I2}val ops: ArrayBuffer[Op] = ArrayBuffer.empty\n")
      sb.append(s"${I2}if (len > 3) {\n")
      sb.append(s"${I3}val s: Double = 1 - o.curveTightness\n")
      sb.append(s"${I3}ops += Op(OpType.move, Vector(points(1).x, points(1).y))\n")
      sb.append(s"${I3}var i: Int = 1\n")
      sb.append(s"${I3}while ((i + 2) < len) {\n")
      sb.append(s"${I4}val cachedVertArray: Point = points(i)\n")
      sb.append(s"${I4}val b1x: Double = cachedVertArray.x + (s * points(i + 1).x - s * points(i - 1).x) / 6\n")
      sb.append(s"${I4}val b1y: Double = cachedVertArray.y + (s * points(i + 1).y - s * points(i - 1).y) / 6\n")
      sb.append(s"${I4}val b2x: Double = points(i + 1).x + (s * points(i).x - s * points(i + 2).x) / 6\n")
      sb.append(s"${I4}val b2y: Double = points(i + 1).y + (s * points(i).y - s * points(i + 2).y) / 6\n")
      sb.append(s"${I4}val b3x: Double = points(i + 1).x\n")
      sb.append(s"${I4}val b3y: Double = points(i + 1).y\n")
      sb.append(s"${I4}ops += Op(OpType.bcurveTo, Vector(b1x, b1y, b2x, b2y, b3x, b3y))\n")
      sb.append(s"${I4}i += 1\n")
      sb.append(s"${I3}}\n")
      sb.append(s"${I3}closePoint.foreach { cp =>\n")
      sb.append(s"${I4}val ro: Double = o.maxRandomnessOffset\n")
      sb.append(s"${I4}ops += Op(OpType.lineTo, Vector(cp.x + _offsetOpt(ro, o), cp.y + _offsetOpt(ro, o)))\n")
      sb.append(s"${I3}}\n")
      sb.append(s"${I2}} else if (len == 3) {\n")
      sb.append(s"${I3}ops += Op(OpType.move, Vector(points(1).x, points(1).y))\n")
      sb.append(s"${I3}ops += Op(\n")
      sb.append(s"${I4}OpType.bcurveTo,\n")
      sb.append(s"${I4}Vector(\n")
      sb.append(s"${I5}points(1).x,\n")
      sb.append(s"${I5}points(1).y,\n")
      sb.append(s"${I5}points(2).x,\n")
      sb.append(s"${I5}points(2).y,\n")
      sb.append(s"${I5}points(2).x,\n")
      sb.append(s"${I5}points(2).y\n")
      sb.append(s"${I4})\n")
      sb.append(s"${I3})\n")
      sb.append(s"${I2}} else if (len == 2) {\n")
      sb.append(s"${I3}ops ++= _line(points(0).x, points(0).y, points(1).x, points(1).y, o, true, true)\n")
      sb.append(s"${I2}}\n")
      sb.append(s"${I2}ops.toVector\n")
      sb.append(s"${I}}\n\n")
    }

    private def emitComputeEllipsePoints(node: RastNode): Unit = {
      sb.append(s"${I}private def _computeEllipsePoints(increment: Double, cx: Double, cy: Double, rx: Double, ry: Double, offset: Double, overlap: Double, o: ResolvedOptions): (Vector[Point], Vector[Point]) = {\n")
      sb.append(s"${I2}val coreOnly: Boolean = o.roughness == 0\n")
      sb.append(s"${I2}val corePoints: ArrayBuffer[Point] = ArrayBuffer.empty\n")
      sb.append(s"${I2}val allPoints: ArrayBuffer[Point] = ArrayBuffer.empty\n")
      sb.append(s"${I2}var inc: Double = increment\n\n")
      sb.append(s"${I2}if (coreOnly) {\n")
      sb.append(s"${I3}inc = inc / 4\n")
      sb.append(s"${I3}allPoints += Point(\n")
      sb.append(s"${I4}cx + rx * Math.cos(-inc),\n")
      sb.append(s"${I4}cy + ry * Math.sin(-inc)\n")
      sb.append(s"${I3})\n")
      sb.append(s"${I3}var angle: Double = 0\n")
      sb.append(s"${I3}while (angle <= Math.PI * 2) {\n")
      sb.append(s"${I4}val p: Point = Point(\n")
      sb.append(s"${I5}cx + rx * Math.cos(angle),\n")
      sb.append(s"${I5}cy + ry * Math.sin(angle)\n")
      sb.append(s"${I4})\n")
      sb.append(s"${I4}corePoints += p\n")
      sb.append(s"${I4}allPoints += p\n")
      sb.append(s"${I4}angle = angle + inc\n")
      sb.append(s"${I3}}\n")
      sb.append(s"${I3}allPoints += Point(\n")
      sb.append(s"${I4}cx + rx * Math.cos(0),\n")
      sb.append(s"${I4}cy + ry * Math.sin(0)\n")
      sb.append(s"${I3})\n")
      sb.append(s"${I3}allPoints += Point(\n")
      sb.append(s"${I4}cx + rx * Math.cos(inc),\n")
      sb.append(s"${I4}cy + ry * Math.sin(inc)\n")
      sb.append(s"${I3})\n")
      sb.append(s"${I2}} else {\n")
      sb.append(s"${I3}val radOffset: Double = _offsetOpt(0.5, o) - (Math.PI / 2)\n")
      sb.append(s"${I3}allPoints += Point(\n")
      sb.append(s"${I4}_offsetOpt(offset, o) + cx + 0.9 * rx * Math.cos(radOffset - inc),\n")
      sb.append(s"${I4}_offsetOpt(offset, o) + cy + 0.9 * ry * Math.sin(radOffset - inc)\n")
      sb.append(s"${I3})\n")
      sb.append(s"${I3}val endAngle: Double = Math.PI * 2 + radOffset - 0.01\n")
      sb.append(s"${I3}var angle: Double = radOffset\n")
      sb.append(s"${I3}while (angle < endAngle) {\n")
      sb.append(s"${I4}val p: Point = Point(\n")
      sb.append(s"${I5}_offsetOpt(offset, o) + cx + rx * Math.cos(angle),\n")
      sb.append(s"${I5}_offsetOpt(offset, o) + cy + ry * Math.sin(angle)\n")
      sb.append(s"${I4})\n")
      sb.append(s"${I4}corePoints += p\n")
      sb.append(s"${I4}allPoints += p\n")
      sb.append(s"${I4}angle = angle + inc\n")
      sb.append(s"${I3}}\n")
      sb.append(s"${I3}allPoints += Point(\n")
      sb.append(s"${I4}_offsetOpt(offset, o) + cx + rx * Math.cos(radOffset + Math.PI * 2 + overlap * 0.5),\n")
      sb.append(s"${I4}_offsetOpt(offset, o) + cy + ry * Math.sin(radOffset + Math.PI * 2 + overlap * 0.5)\n")
      sb.append(s"${I3})\n")
      sb.append(s"${I3}allPoints += Point(\n")
      sb.append(s"${I4}_offsetOpt(offset, o) + cx + 0.98 * rx * Math.cos(radOffset + overlap),\n")
      sb.append(s"${I4}_offsetOpt(offset, o) + cy + 0.98 * ry * Math.sin(radOffset + overlap)\n")
      sb.append(s"${I3})\n")
      sb.append(s"${I3}allPoints += Point(\n")
      sb.append(s"${I4}_offsetOpt(offset, o) + cx + 0.9 * rx * Math.cos(radOffset + overlap * 0.5),\n")
      sb.append(s"${I4}_offsetOpt(offset, o) + cy + 0.9 * ry * Math.sin(radOffset + overlap * 0.5)\n")
      sb.append(s"${I3})\n")
      sb.append(s"${I2}}\n\n")
      sb.append(s"${I2}(allPoints.toVector, corePoints.toVector)\n")
      sb.append(s"${I}}\n\n")
    }

    private def emitArcPrivate(node: RastNode): Unit = {
      sb.append(s"${I}private def _arc(increment: Double, cx: Double, cy: Double, rx: Double, ry: Double, strt: Double, stp: Double, offset: Double, o: ResolvedOptions): Vector[Op] = {\n")
      sb.append(s"${I2}val radOffset: Double = strt + _offsetOpt(0.1, o)\n")
      sb.append(s"${I2}val points: ArrayBuffer[Point] = ArrayBuffer.empty\n")
      sb.append(s"${I2}points += Point(\n")
      sb.append(s"${I3}_offsetOpt(offset, o) + cx + 0.9 * rx * Math.cos(radOffset - increment),\n")
      sb.append(s"${I3}_offsetOpt(offset, o) + cy + 0.9 * ry * Math.sin(radOffset - increment)\n")
      sb.append(s"${I2})\n")
      sb.append(s"${I2}var angle: Double = radOffset\n")
      sb.append(s"${I2}while (angle <= stp) {\n")
      sb.append(s"${I3}points += Point(\n")
      sb.append(s"${I4}_offsetOpt(offset, o) + cx + rx * Math.cos(angle),\n")
      sb.append(s"${I4}_offsetOpt(offset, o) + cy + ry * Math.sin(angle)\n")
      sb.append(s"${I3})\n")
      sb.append(s"${I3}angle = angle + increment\n")
      sb.append(s"${I2}}\n")
      sb.append(s"${I2}points += Point(\n")
      sb.append(s"${I3}cx + rx * Math.cos(stp),\n")
      sb.append(s"${I3}cy + ry * Math.sin(stp)\n")
      sb.append(s"${I2})\n")
      sb.append(s"${I2}points += Point(\n")
      sb.append(s"${I3}cx + rx * Math.cos(stp),\n")
      sb.append(s"${I3}cy + ry * Math.sin(stp)\n")
      sb.append(s"${I2})\n")
      sb.append(s"${I2}_curve(points.toVector, Nullable.empty, o)\n")
      sb.append(s"${I}}\n\n")
    }

    private def emitBezierTo(node: RastNode): Unit = {
      sb.append(s"${I}private def _bezierTo(x1: Double, y1: Double, x2: Double, y2: Double, x: Double, y: Double, current: Point, o: ResolvedOptions): Vector[Op] = {\n")
      sb.append(s"${I2}val ops: ArrayBuffer[Op] = ArrayBuffer.empty\n")
      sb.append(s"${I2}val ros: Vector[Double] = Vector(\n")
      sb.append(s"${I3}if (numTruthy(o.maxRandomnessOffset)) o.maxRandomnessOffset else 1.0,\n")
      sb.append(s"${I3}(if (numTruthy(o.maxRandomnessOffset)) o.maxRandomnessOffset else 1.0) + 0.3\n")
      sb.append(s"${I2})\n")
      sb.append(s"${I2}var f: Point = Point(0, 0)\n")
      sb.append(s"${I2}val iterations: Int = if (o.disableMultiStroke) 1 else 2\n")
      sb.append(s"${I2}val preserveVertices: Boolean = o.preserveVertices\n")
      sb.append(s"${I2}var i: Int = 0\n")
      sb.append(s"${I2}while (i < iterations) {\n")
      sb.append(s"${I3}if (i == 0) {\n")
      sb.append(s"${I4}ops += Op(OpType.move, Vector(current.x, current.y))\n")
      sb.append(s"${I3}} else {\n")
      sb.append(s"${I4}ops += Op(\n")
      sb.append(s"${I5}OpType.move,\n")
      sb.append(s"${I5}Vector(\n")
      sb.append(s"${I6}current.x + (if (preserveVertices) 0.0 else _offsetOpt(ros(0), o)),\n")
      sb.append(s"${I6}current.y + (if (preserveVertices) 0.0 else _offsetOpt(ros(0), o))\n")
      sb.append(s"${I5})\n")
      sb.append(s"${I4})\n")
      sb.append(s"${I3}}\n")
      sb.append(s"${I3}f = if (preserveVertices) Point(x, y) else Point(x + _offsetOpt(ros(i), o), y + _offsetOpt(ros(i), o))\n")
      sb.append(s"${I3}ops += Op(\n")
      sb.append(s"${I4}OpType.bcurveTo,\n")
      sb.append(s"${I4}Vector(\n")
      sb.append(s"${I5}x1 + _offsetOpt(ros(i), o),\n")
      sb.append(s"${I5}y1 + _offsetOpt(ros(i), o),\n")
      sb.append(s"${I5}x2 + _offsetOpt(ros(i), o),\n")
      sb.append(s"${I5}y2 + _offsetOpt(ros(i), o),\n")
      sb.append(s"${I5}f.x,\n")
      sb.append(s"${I5}f.y\n")
      sb.append(s"${I4})\n")
      sb.append(s"${I3})\n")
      sb.append(s"${I3}i += 1\n")
      sb.append(s"${I2}}\n")
      sb.append(s"${I2}ops.toVector\n")
      sb.append(s"${I}}\n\n")
    }

    private def emitNumTruthy(): Unit = {
      sb.append(s"${I}private def numTruthy(d: Double): Boolean =\n")
      sb.append(s"${I2}d != 0.0 && !d.isNaN\n")
    }
  }

  // ---- Generator emitter ----

  private class GeneratorEmitCtx(file: RastFile) {
    private val sb = new StringBuilder

    def emit(): String = {
      emitHeader()
      emitNOS()
      emitClass()
      emitCompanion()
      sb.toString
    }

    private def emitHeader(): Unit = {
      sb.append("package ssg\n")
      sb.append("package graphs\n")
      sb.append("package commons\n")
      sb.append("package rough\n\n")
      sb.append("import lowlevel.Nullable\n\n")
      sb.append("import scala.collection.mutable.ArrayBuffer\n\n")
      sb.append("import ssg.graphs.commons.rough.curve.{ CurveToBezier, Point as CurvePoint, PointsOnCurve, PointsOnPath }\n")
      sb.append("import ssg.graphs.commons.util.FormatUtil\n\n")
    }

    private def emitNOS(): Unit = {
      sb.append("final val NOS: String = \"none\"\n\n")
    }

    private def emitClass(): Unit = {
      sb.append("final class RoughGenerator(config: Config = Config()) {\n\n")
      emitDefaultOptions()
      emitConstructorBody()
      emitO()
      emitMergeOptions()
      emitD()
      emitGenLine()
      emitGenRectangle()
      emitGenEllipse()
      emitGenCircle()
      emitGenLinearPath()
      emitGenArc()
      emitGenCurve()
      emitGenPolygon()
      emitGenPath()
      emitGenOpsToPath()
      emitGenToPaths()
      emitFillSketch()
      emitMergedShape()
      emitPrivateHelpers()
      sb.append("}\n\n")
    }

    private def emitDefaultOptions(): Unit = {
      sb.append(s"${I}var defaultOptions: ResolvedOptions = ResolvedOptions(\n")
      sb.append(s"${I2}maxRandomnessOffset = 2,\n")
      sb.append(s"${I2}roughness = 1,\n")
      sb.append(s"${I2}bowing = 1,\n")
      sb.append(s"${I2}stroke = \"#000\",\n")
      sb.append(s"${I2}strokeWidth = 1,\n")
      sb.append(s"${I2}curveTightness = 0,\n")
      sb.append(s"${I2}curveFitting = 0.95,\n")
      sb.append(s"${I2}curveStepCount = 9,\n")
      sb.append(s"${I2}fillStyle = \"hachure\",\n")
      sb.append(s"${I2}fillWeight = -1,\n")
      sb.append(s"${I2}hachureAngle = -41,\n")
      sb.append(s"${I2}hachureGap = -1,\n")
      sb.append(s"${I2}dashOffset = -1,\n")
      sb.append(s"${I2}dashGap = -1,\n")
      sb.append(s"${I2}zigzagOffset = -1,\n")
      sb.append(s"${I2}seed = 0,\n")
      sb.append(s"${I2}disableMultiStroke = false,\n")
      sb.append(s"${I2}disableMultiStrokeFill = false,\n")
      sb.append(s"${I2}preserveVertices = false,\n")
      sb.append(s"${I2}fillShapeRoughnessGain = 0.8\n")
      sb.append(s"${I})\n\n")
    }

    private def emitConstructorBody(): Unit = {
      sb.append(s"${I}if (config.options.isDefined) {\n")
      sb.append(s"${I2}defaultOptions = _o(config.options)\n")
      sb.append(s"${I}}\n\n")
    }

    private def emitO(): Unit = {
      sb.append(s"${I}private def _o(options: Option[Options]): ResolvedOptions =\n")
      sb.append(s"${I2}options match {\n")
      sb.append(s"${I3}case Some(o) => mergeOptions(defaultOptions, o)\n")
      sb.append(s"${I3}case None    => defaultOptions\n")
      sb.append(s"${I2}}\n\n")
    }

    private def emitMergeOptions(): Unit = {
      sb.append(s"${I}private def mergeOptions(base: ResolvedOptions, o: Options): ResolvedOptions =\n")
      sb.append(s"${I2}base.copy(\n")
      sb.append(s"${I3}maxRandomnessOffset = o.maxRandomnessOffset.getOrElse(base.maxRandomnessOffset),\n")
      sb.append(s"${I3}roughness = o.roughness.getOrElse(base.roughness),\n")
      sb.append(s"${I3}bowing = o.bowing.getOrElse(base.bowing),\n")
      sb.append(s"${I3}stroke = o.stroke.getOrElse(base.stroke),\n")
      sb.append(s"${I3}strokeWidth = o.strokeWidth.getOrElse(base.strokeWidth),\n")
      sb.append(s"${I3}curveFitting = o.curveFitting.getOrElse(base.curveFitting),\n")
      sb.append(s"${I3}curveTightness = o.curveTightness.getOrElse(base.curveTightness),\n")
      sb.append(s"${I3}curveStepCount = o.curveStepCount.getOrElse(base.curveStepCount),\n")
      sb.append(s"${I3}fillStyle = o.fillStyle.getOrElse(base.fillStyle),\n")
      sb.append(s"${I3}fillWeight = o.fillWeight.getOrElse(base.fillWeight),\n")
      sb.append(s"${I3}hachureAngle = o.hachureAngle.getOrElse(base.hachureAngle),\n")
      sb.append(s"${I3}hachureGap = o.hachureGap.getOrElse(base.hachureGap),\n")
      sb.append(s"${I3}dashOffset = o.dashOffset.getOrElse(base.dashOffset),\n")
      sb.append(s"${I3}dashGap = o.dashGap.getOrElse(base.dashGap),\n")
      sb.append(s"${I3}zigzagOffset = o.zigzagOffset.getOrElse(base.zigzagOffset),\n")
      sb.append(s"${I3}seed = o.seed.getOrElse(base.seed),\n")
      sb.append(s"${I3}disableMultiStroke = o.disableMultiStroke.getOrElse(base.disableMultiStroke),\n")
      sb.append(s"${I3}disableMultiStrokeFill = o.disableMultiStrokeFill.getOrElse(base.disableMultiStrokeFill),\n")
      sb.append(s"${I3}preserveVertices = o.preserveVertices.getOrElse(base.preserveVertices),\n")
      sb.append(s"${I3}fillShapeRoughnessGain = o.fillShapeRoughnessGain.getOrElse(base.fillShapeRoughnessGain),\n")
      sb.append(s"${I3}fill = o.fill.orElse(base.fill),\n")
      sb.append(s"${I3}simplification = o.simplification.orElse(base.simplification),\n")
      sb.append(s"${I3}strokeLineDash = o.strokeLineDash.orElse(base.strokeLineDash),\n")
      sb.append(s"${I3}strokeLineDashOffset = o.strokeLineDashOffset.orElse(base.strokeLineDashOffset),\n")
      sb.append(s"${I3}fillLineDash = o.fillLineDash.orElse(base.fillLineDash),\n")
      sb.append(s"${I3}fillLineDashOffset = o.fillLineDashOffset.orElse(base.fillLineDashOffset),\n")
      sb.append(s"${I3}fixedDecimalPlaceDigits = o.fixedDecimalPlaceDigits.orElse(base.fixedDecimalPlaceDigits)\n")
      sb.append(s"${I2})\n\n")
    }

    private def emitD(): Unit = {
      sb.append(s"${I}private def _d(shape: String, sets: Vector[OpSet], options: ResolvedOptions): Drawable =\n")
      sb.append(s"${I2}Drawable(shape = shape, sets = sets, options = options)\n\n")
    }

    private def emitGenLine(): Unit = {
      sb.append(s"${I}def line(x1: Double, y1: Double, x2: Double, y2: Double, options: Option[Options] = None): Drawable = {\n")
      sb.append(s"${I2}val o: ResolvedOptions = _o(options)\n")
      sb.append(s"${I2}_d(\"line\", Vector(RoughRenderer.line(x1, y1, x2, y2, o)), o)\n")
      sb.append(s"${I}}\n\n")
    }

    private def emitGenRectangle(): Unit = {
      sb.append(s"${I}def rectangle(x: Double, y: Double, width: Double, height: Double, options: Option[Options] = None): Drawable = {\n")
      sb.append(s"${I2}val o: ResolvedOptions = _o(options)\n")
      sb.append(s"${I2}val paths: ArrayBuffer[OpSet] = ArrayBuffer.empty\n")
      sb.append(s"${I2}val outline: OpSet = RoughRenderer.rectangle(x, y, width, height, o)\n")
      sb.append(s"${I2}if (fillTruthy(o)) {\n")
      sb.append(s"${I3}val points: Vector[Point] = Vector(Point(x, y), Point(x + width, y), Point(x + width, y + height), Point(x, y + height))\n")
      sb.append(s"${I3}if (o.fillStyle == \"solid\") {\n")
      sb.append(s"${I4}paths += RoughRenderer.solidFillPolygon(Vector(points), o)\n")
      sb.append(s"${I3}} else {\n")
      sb.append(s"${I4}paths += RoughRenderer.patternFillPolygons(Vector(points), o)\n")
      sb.append(s"${I3}}\n")
      sb.append(s"${I2}}\n")
      sb.append(s"${I2}if (o.stroke != NOS) {\n")
      sb.append(s"${I3}paths += outline\n")
      sb.append(s"${I2}}\n")
      sb.append(s"${I2}_d(\"rectangle\", paths.toVector, o)\n")
      sb.append(s"${I}}\n\n")
    }

    private def emitGenEllipse(): Unit = {
      sb.append(s"${I}def ellipse(x: Double, y: Double, width: Double, height: Double, options: Option[Options] = None): Drawable = {\n")
      sb.append(s"${I2}val o: ResolvedOptions = _o(options)\n")
      sb.append(s"${I2}val paths: ArrayBuffer[OpSet] = ArrayBuffer.empty\n")
      sb.append(s"${I2}val ellipseParams: EllipseParams = RoughRenderer.generateEllipseParams(width, height, o)\n")
      sb.append(s"${I2}val ellipseResponse: EllipseResult = RoughRenderer.ellipseWithParams(x, y, o, ellipseParams)\n")
      sb.append(s"${I2}if (fillTruthy(o)) {\n")
      sb.append(s"${I3}if (o.fillStyle == \"solid\") {\n")
      sb.append(s"${I4}val shape: OpSet = RoughRenderer.ellipseWithParams(x, y, o, ellipseParams).opset.copy(`type` = OpSetType.fillPath)\n")
      sb.append(s"${I4}paths += shape\n")
      sb.append(s"${I3}} else {\n")
      sb.append(s"${I4}paths += RoughRenderer.patternFillPolygons(Vector(ellipseResponse.estimatedPoints), o)\n")
      sb.append(s"${I3}}\n")
      sb.append(s"${I2}}\n")
      sb.append(s"${I2}if (o.stroke != NOS) {\n")
      sb.append(s"${I3}paths += ellipseResponse.opset\n")
      sb.append(s"${I2}}\n")
      sb.append(s"${I2}_d(\"ellipse\", paths.toVector, o)\n")
      sb.append(s"${I}}\n\n")
    }

    private def emitGenCircle(): Unit = {
      sb.append(s"${I}def circle(x: Double, y: Double, diameter: Double, options: Option[Options] = None): Drawable = {\n")
      sb.append(s"${I2}val ret: Drawable = this.ellipse(x, y, diameter, diameter, options)\n")
      sb.append(s"${I2}ret.copy(shape = \"circle\")\n")
      sb.append(s"${I}}\n\n")
    }

    private def emitGenLinearPath(): Unit = {
      sb.append(s"${I}def linearPath(points: Vector[Point], options: Option[Options] = None): Drawable = {\n")
      sb.append(s"${I2}val o: ResolvedOptions = _o(options)\n")
      sb.append(s"${I2}_d(\"linearPath\", Vector(RoughRenderer.linearPath(points, false, o)), o)\n")
      sb.append(s"${I}}\n\n")
    }

    private def emitGenArc(): Unit = {
      sb.append(s"${I}def arc(x: Double, y: Double, width: Double, height: Double, start: Double, stop: Double, closed: Boolean = false, options: Option[Options] = None): Drawable = {\n")
      sb.append(s"${I2}val o: ResolvedOptions = _o(options)\n")
      sb.append(s"${I2}val paths: ArrayBuffer[OpSet] = ArrayBuffer.empty\n")
      sb.append(s"${I2}val outline: OpSet = RoughRenderer.arc(x, y, width, height, start, stop, closed, true, o)\n")
      sb.append(s"${I2}if (closed && fillTruthy(o)) {\n")
      sb.append(s"${I3}if (o.fillStyle == \"solid\") {\n")
      sb.append(s"${I4}val fillOptions: ResolvedOptions = o.copy(disableMultiStroke = true)\n")
      sb.append(s"${I4}val shape: OpSet = RoughRenderer.arc(x, y, width, height, start, stop, true, false, fillOptions).copy(`type` = OpSetType.fillPath)\n")
      sb.append(s"${I4}paths += shape\n")
      sb.append(s"${I3}} else {\n")
      sb.append(s"${I4}paths += RoughRenderer.patternFillArc(x, y, width, height, start, stop, o)\n")
      sb.append(s"${I3}}\n")
      sb.append(s"${I2}}\n")
      sb.append(s"${I2}if (o.stroke != NOS) {\n")
      sb.append(s"${I3}paths += outline\n")
      sb.append(s"${I2}}\n")
      sb.append(s"${I2}_d(\"arc\", paths.toVector, o)\n")
      sb.append(s"${I}}\n\n")
    }

    private def emitGenCurve(): Unit = {
      sb.append(s"${I}def curve(points: Vector[Point] | Vector[Vector[Point]], options: Option[Options] = None): Drawable = {\n")
      sb.append(s"${I2}val o: ResolvedOptions = _o(options)\n")
      sb.append(s"${I2}val paths: ArrayBuffer[OpSet] = ArrayBuffer.empty\n")
      sb.append(s"${I2}val outline: OpSet = RoughRenderer.curve(points, o)\n")
      sb.append(s"${I2}if (fillTruthy(o) && o.fill.exists(_ != NOS)) {\n")
      sb.append(s"${I3}if (o.fillStyle == \"solid\") {\n")
      sb.append(s"${I4}val fillShape: OpSet = RoughRenderer.curve(\n")
      sb.append(s"${I5}points,\n")
      sb.append(s"${I5}o.copy(\n")
      sb.append(s"${I6}disableMultiStroke = true,\n")
      sb.append(s"${I6}roughness = if (numTruthy(o.roughness)) o.roughness + o.fillShapeRoughnessGain else 0\n")
      sb.append(s"${I5})\n")
      sb.append(s"${I4})\n")
      sb.append(s"${I4}paths += OpSet(`type` = OpSetType.fillPath, ops = _mergedShape(fillShape.ops))\n")
      sb.append(s"${I3}} else {\n")
      sb.append(s"${I4}val polyPoints: ArrayBuffer[Point] = ArrayBuffer.empty\n")
      sb.append(s"${I4}val inputPoints: Vector[Point] | Vector[Vector[Point]] = points\n")
      sb.append(s"${I4}val ip: Vector[Any] = inputPoints\n")
      sb.append(s"${I4}if (ip.nonEmpty) {\n")
      sb.append(s"${I5}val p1: Any = ip.head\n")
      sb.append(s"${I5}val pointsList: Vector[Vector[Point]] = p1 match {\n")
      sb.append(s"${I6}case _: Point => Vector(inputPoints.asInstanceOf[Vector[Point]])\n")
      sb.append(s"${I6}case _ => inputPoints.asInstanceOf[Vector[Vector[Point]]]\n")
      sb.append(s"${I5}}\n")
      sb.append(s"${I5}for (pts <- pointsList)\n")
      sb.append(s"${I6}if (pts.length < 3) {\n")
      sb.append(s"${I7}polyPoints ++= pts\n")
      sb.append(s"${I6}} else if (pts.length == 3) {\n")
      sb.append(s"${I7}polyPoints ++= bezierPolyPoints(Vector(pts(0), pts(0), pts(1), pts(2)), o.roughness)\n")
      sb.append(s"${I6}} else {\n")
      sb.append(s"${I7}polyPoints ++= bezierPolyPoints(pts, o.roughness)\n")
      sb.append(s"${I6}}\n")
      sb.append(s"${I4}}\n")
      sb.append(s"${I4}if (polyPoints.nonEmpty) {\n")
      sb.append(s"${I5}paths += RoughRenderer.patternFillPolygons(Vector(polyPoints.toVector), o)\n")
      sb.append(s"${I4}}\n")
      sb.append(s"${I3}}\n")
      sb.append(s"${I2}}\n")
      sb.append(s"${I2}if (o.stroke != NOS) {\n")
      sb.append(s"${I3}paths += outline\n")
      sb.append(s"${I2}}\n")
      sb.append(s"${I2}_d(\"curve\", paths.toVector, o)\n")
      sb.append(s"${I}}\n\n")
    }

    private def emitGenPolygon(): Unit = {
      sb.append(s"${I}def polygon(points: Vector[Point], options: Option[Options] = None): Drawable = {\n")
      sb.append(s"${I2}val o: ResolvedOptions = _o(options)\n")
      sb.append(s"${I2}val paths: ArrayBuffer[OpSet] = ArrayBuffer.empty\n")
      sb.append(s"${I2}val outline: OpSet = RoughRenderer.linearPath(points, true, o)\n")
      sb.append(s"${I2}if (fillTruthy(o)) {\n")
      sb.append(s"${I3}if (o.fillStyle == \"solid\") {\n")
      sb.append(s"${I4}paths += RoughRenderer.solidFillPolygon(Vector(points), o)\n")
      sb.append(s"${I3}} else {\n")
      sb.append(s"${I4}paths += RoughRenderer.patternFillPolygons(Vector(points), o)\n")
      sb.append(s"${I3}}\n")
      sb.append(s"${I2}}\n")
      sb.append(s"${I2}if (o.stroke != NOS) {\n")
      sb.append(s"${I3}paths += outline\n")
      sb.append(s"${I2}}\n")
      sb.append(s"${I2}_d(\"polygon\", paths.toVector, o)\n")
      sb.append(s"${I}}\n\n")
    }

    private def emitGenPath(): Unit = {
      sb.append(s"${I}def path(d: String, options: Option[Options] = None): Drawable = {\n")
      sb.append(s"${I2}val o: ResolvedOptions = _o(options)\n")
      sb.append(s"${I2}val paths: ArrayBuffer[OpSet] = ArrayBuffer.empty\n")
      sb.append(s"${I2}if (d.isEmpty) {\n")
      sb.append(s"${I3}_d(\"path\", paths.toVector, o)\n")
      sb.append(s"${I2}} else {\n")
      sb.append(s"${I3}val cleaned: String = MinusSpacePattern.replaceAllIn(d.replace(\"\\n\", \" \"), \"-\").replace(\"/(ss)/g\", \" \")\n\n")
      sb.append(s"${I3}val hasFill: Boolean = o.fill.exists(s => s.nonEmpty && s != \"transparent\" && s != NOS)\n")
      sb.append(s"${I3}val hasStroke: Boolean = o.stroke != NOS\n")
      sb.append(s"${I3}val simplified: Boolean = o.simplification.exists(s => numTruthy(s) && (s < 1))\n")
      sb.append(s"${I3}val distance: Double =\n")
      sb.append(s"${I4}if (simplified) 4 - 4 * o.simplification.filter(numTruthy).getOrElse(1.0)\n")
      sb.append(s"${I4}else (1 + o.roughness) / 2\n")
      sb.append(s"${I3}val sets: Vector[Vector[Point]] = PointsOnPath.pointsOnPath(cleaned, Some(1.0), Some(distance)).map(_.map(toGeomPoint))\n")
      sb.append(s"${I3}val shape: OpSet = RoughRenderer.svgPath(cleaned, o)\n\n")
      sb.append(s"${I3}if (hasFill) {\n")
      sb.append(s"${I4}if (o.fillStyle == \"solid\") {\n")
      sb.append(s"${I5}if (sets.length == 1) {\n")
      sb.append(s"${I6}val fillShape: OpSet = RoughRenderer.svgPath(\n")
      sb.append(s"${I7}cleaned,\n")
      sb.append(s"${I7}o.copy(\n")
      sb.append(s"${I8}disableMultiStroke = true,\n")
      sb.append(s"${I8}roughness = if (numTruthy(o.roughness)) o.roughness + o.fillShapeRoughnessGain else 0\n")
      sb.append(s"${I7})\n")
      sb.append(s"${I6})\n")
      sb.append(s"${I6}paths += OpSet(`type` = OpSetType.fillPath, ops = _mergedShape(fillShape.ops))\n")
      sb.append(s"${I5}} else {\n")
      sb.append(s"${I6}paths += RoughRenderer.solidFillPolygon(sets, o)\n")
      sb.append(s"${I5}}\n")
      sb.append(s"${I4}} else {\n")
      sb.append(s"${I5}paths += RoughRenderer.patternFillPolygons(sets, o)\n")
      sb.append(s"${I4}}\n")
      sb.append(s"${I3}}\n")
      sb.append(s"${I3}if (hasStroke) {\n")
      sb.append(s"${I4}if (simplified) {\n")
      sb.append(s"${I5}for (set <- sets)\n")
      sb.append(s"${I6}paths += RoughRenderer.linearPath(set, false, o)\n")
      sb.append(s"${I4}} else {\n")
      sb.append(s"${I5}paths += shape\n")
      sb.append(s"${I4}}\n")
      sb.append(s"${I3}}\n\n")
      sb.append(s"${I3}_d(\"path\", paths.toVector, o)\n")
      sb.append(s"${I2}}\n")
      sb.append(s"${I}}\n\n")
    }

    private def emitGenOpsToPath(): Unit = {
      sb.append(s"${I}def opsToPath(drawing: OpSet, fixedDecimals: Option[Double] = None): String = {\n")
      sb.append(s"${I2}val sb: StringBuilder = new StringBuilder\n")
      sb.append(s"${I2}for (item <- drawing.ops) {\n")
      sb.append(s"${I3}val data: Vector[Double] = fixedDecimals match {\n")
      sb.append(s"${I4}case Some(fd) if fd >= 0 => item.data.map(d => FormatUtil.toFixed(d, fd.toInt).toDouble)\n")
      sb.append(s"${I4}case _ => item.data\n")
      sb.append(s"${I3}}\n")
      sb.append(s"${I3}item.op match {\n")
      sb.append(s"""${I4}case OpType.move     => sb ++= s"M$${RoughGenerator.numToString(data(0))} $${RoughGenerator.numToString(data(1))} "\n""")
      sb.append(s"""${I4}case OpType.bcurveTo =>\n""")
      sb.append(s"""${I5}sb ++= s"C$${RoughGenerator.numToString(data(0))} $${RoughGenerator.numToString(data(1))}, $${RoughGenerator.numToString(data(2))} $${RoughGenerator.numToString(data(3))}, $${RoughGenerator.numToString(data(4))} $${RoughGenerator.numToString(data(5))} "\n""")
      sb.append(s"""${I4}case OpType.lineTo => sb ++= s"L$${RoughGenerator.numToString(data(0))} $${RoughGenerator.numToString(data(1))} "\n""")
      sb.append(s"${I3}}\n")
      sb.append(s"${I2}}\n")
      sb.append(s"${I2}sb.toString.trim\n")
      sb.append(s"${I}}\n\n")
    }

    private def emitGenToPaths(): Unit = {
      sb.append(s"${I}def toPaths(drawable: Drawable): Vector[PathInfo] = {\n")
      sb.append(s"${I2}val sets: Vector[OpSet] = drawable.sets\n")
      sb.append(s"${I2}val o: ResolvedOptions = drawable.options\n")
      sb.append(s"${I2}val paths: ArrayBuffer[PathInfo] = ArrayBuffer.empty\n")
      sb.append(s"${I2}for (drawing <- sets) {\n")
      sb.append(s"${I3}val path: Nullable[PathInfo] = drawing.`type` match {\n")
      sb.append(s"${I4}case OpSetType.path =>\n")
      sb.append(s"${I5}Nullable(PathInfo(d = opsToPath(drawing), stroke = o.stroke, strokeWidth = o.strokeWidth, fill = Some(NOS)))\n")
      sb.append(s"${I4}case OpSetType.fillPath =>\n")
      sb.append(s"${I5}Nullable(PathInfo(d = opsToPath(drawing), stroke = NOS, strokeWidth = 0, fill = Some(o.fill.filter(_.nonEmpty).getOrElse(NOS))))\n")
      sb.append(s"${I4}case OpSetType.fillSketch =>\n")
      sb.append(s"${I5}Nullable(fillSketch(drawing, o))\n")
      sb.append(s"${I3}}\n")
      sb.append(s"${I3}path.foreach(paths += _)\n")
      sb.append(s"${I2}}\n")
      sb.append(s"${I2}paths.toVector\n")
      sb.append(s"${I}}\n\n")
    }

    private def emitFillSketch(): Unit = {
      sb.append(s"${I}private def fillSketch(drawing: OpSet, o: ResolvedOptions): PathInfo = {\n")
      sb.append(s"${I2}var fweight: Double = o.fillWeight\n")
      sb.append(s"${I2}if (fweight < 0) {\n")
      sb.append(s"${I3}fweight = o.strokeWidth / 2\n")
      sb.append(s"${I2}}\n")
      sb.append(s"${I2}PathInfo(\n")
      sb.append(s"${I3}d = opsToPath(drawing),\n")
      sb.append(s"${I3}stroke = o.fill.filter(_.nonEmpty).getOrElse(NOS),\n")
      sb.append(s"${I3}strokeWidth = fweight,\n")
      sb.append(s"${I3}fill = Some(NOS)\n")
      sb.append(s"${I2})\n")
      sb.append(s"${I}}\n\n")
    }

    private def emitMergedShape(): Unit = {
      sb.append(s"${I}private def _mergedShape(input: Vector[Op]): Vector[Op] =\n")
      sb.append(s"${I2}input.zipWithIndex.collect {\n")
      sb.append(s"${I3}case (d, i) if (i == 0) || (d.op != OpType.move) => d\n")
      sb.append(s"${I2}}\n\n")
    }

    private def emitPrivateHelpers(): Unit = {
      sb.append(s"${I}// ---- private helpers ----\n\n")
      sb.append(s"${I}private val MinusSpacePattern = \"(-\\\\s)\".r\n\n")
      sb.append(s"${I}private def fillTruthy(o: ResolvedOptions): Boolean =\n")
      sb.append(s"${I2}o.fill.exists(_.nonEmpty)\n\n")
      sb.append(s"${I}private def numTruthy(d: Double): Boolean =\n")
      sb.append(s"${I2}d != 0.0 && !d.isNaN\n\n")
      sb.append(s"${I}private def toCurvePoint(p: Point): CurvePoint =\n")
      sb.append(s"${I2}CurvePoint(p.x, p.y)\n\n")
      sb.append(s"${I}private def toGeomPoint(p: CurvePoint): Point =\n")
      sb.append(s"${I2}Point(p.x, p.y)\n\n")
      sb.append(s"${I}private def bezierPolyPoints(pts: Vector[Point], roughness: Double): Vector[Point] =\n")
      sb.append(s"${I2}PointsOnCurve.pointsOnBezierCurves(CurveToBezier.curveToBezier(pts.map(toCurvePoint)), 10, Some((1 + roughness) / 2)).map(toGeomPoint)\n")
    }

    private def emitCompanion(): Unit = {
      sb.append("object RoughGenerator {\n\n")
      sb.append(s"${I}def newSeed(): Int =\n")
      sb.append(s"${I2}RoughMath.randomSeed()\n\n")
      emitNumToString()
      sb.append("}\n")
    }

    private def emitNumToString(): Unit = {
      sb.append(s"${I}private[rough] def numToString(num: Double): String =\n")
      sb.append(s"${I2}if (num.isNaN) {\n")
      sb.append(s"${I3}\"NaN\"\n")
      sb.append(s"${I2}} else if (num == 0.0) {\n")
      sb.append(s"${I3}\"0\"\n")
      sb.append(s"${I2}} else if (num < 0) {\n")
      sb.append(s"${I3}\"-\" + numToString(-num)\n")
      sb.append(s"${I2}} else if (num.isInfinite) {\n")
      sb.append(s"${I3}\"Infinity\"\n")
      sb.append(s"${I2}} else {\n")
      sb.append(s"${I3}val raw: String = num.toString\n")
      sb.append(s"${I3}val lower: String = raw.replace('E', 'e')\n")
      sb.append(s"${I3}val eIdx: Int = lower.indexOf('e')\n")
      sb.append(s"${I3}val (mantissa, expPart): (String, String) =\n")
      sb.append(s"${I4}if (eIdx >= 0) (lower.substring(0, eIdx), lower.substring(eIdx + 1))\n")
      sb.append(s"${I4}else (lower, \"\")\n")
      sb.append(s"${I3}val dotIdx: Int = mantissa.indexOf('.')\n")
      sb.append(s"${I3}val (rawDigits, pointPos): (String, Int) =\n")
      sb.append(s"${I4}if (dotIdx >= 0) (mantissa.substring(0, dotIdx) + mantissa.substring(dotIdx + 1), dotIdx)\n")
      sb.append(s"${I4}else (mantissa, mantissa.length)\n")
      sb.append(s"${I3}val parsedExp: Int =\n")
      sb.append(s"${I4}if (expPart.isEmpty) 0\n")
      sb.append(s"${I4}else if (expPart.charAt(0) == '+') expPart.substring(1).toInt\n")
      sb.append(s"${I4}else expPart.toInt\n")
      sb.append(s"${I3}var start: Int = 0\n")
      sb.append(s"${I3}while (start < rawDigits.length && rawDigits.charAt(start) == '0')\n")
      sb.append(s"${I4}start += 1\n")
      sb.append(s"${I3}var end: Int = rawDigits.length\n")
      sb.append(s"${I3}while (end > start && rawDigits.charAt(end - 1) == '0')\n")
      sb.append(s"${I4}end -= 1\n")
      sb.append(s"${I3}val s: String = rawDigits.substring(start, end)\n")
      sb.append(s"${I3}val k: Int = s.length\n")
      sb.append(s"${I3}val n: Int = (pointPos - start) + parsedExp\n")
      sb.append(s"${I3}ecmaFormat(s, k, n)\n")
      sb.append(s"${I2}}\n\n")
      sb.append(s"${I}private def ecmaFormat(s: String, k: Int, n: Int): String =\n")
      sb.append(s"${I2}if (k <= n && n <= 21) {\n")
      sb.append(s"${I3}s + (\"0\" * (n - k))\n")
      sb.append(s"${I2}} else if (0 < n && n <= 21) {\n")
      sb.append(s"${I3}s.substring(0, n) + \".\" + s.substring(n)\n")
      sb.append(s"${I2}} else if (-6 < n && n <= 0) {\n")
      sb.append(s"${I3}\"0.\" + (\"0\" * (-n)) + s\n")
      sb.append(s"${I2}} else if (k == 1) {\n")
      sb.append(s"${I3}s + \"e\" + (if (n - 1 >= 0) \"+\" else \"\") + (n - 1)\n")
      sb.append(s"${I2}} else {\n")
      sb.append(s"${I3}s.charAt(0).toString + \".\" + s.substring(1) + \"e\" + (if (n - 1 >= 0) \"+\" else \"\") + (n - 1)\n")
      sb.append(s"${I2}}\n\n")
    }
  }
}
