package balticporter.frontend.ts

import balticporter.catalog.CatalogLog
import balticporter.core.Substitutions
import balticporter.tir.*

class TsMinterSpec extends munit.FunSuite:

  private def loadRast(resource: String): RastFile =
    val stream = getClass.getResourceAsStream(resource)
    assert(stream != null, s"Resource not found: $resource")
    val json = new String(stream.readAllBytes(), java.nio.charset.StandardCharsets.UTF_8)
    stream.close()
    Rast.readFile(json)

  private def countUnportable(program: Program): Int =
    var count = 0
    def walk(stmt: Statement): Unit = stmt match
      case _: Tree.Unportable => count += 1
      case cd: Tree.ClassDef  => cd.body.foreach(walk)
      case dd: Tree.DefDef    => dd.rhs.foreach(walkTerm)
      case _                  =>
    def walkTerm(term: Term): Unit = term match
      case _: Tree.Unportable => count += 1
      case b: Tree.Block      => b.stats.foreach(walk); walkTerm(b.expr)
      case i: Tree.If         => walkTerm(i.cond); walkTerm(i.thenp); walkTerm(i.elsep)
      case w: Tree.While      => walkTerm(w.cond); walkTerm(w.body)
      case f: Tree.For        => f.init.foreach(walk); f.cond.foreach(walkTerm); walkTerm(f.body)
      case fe: Tree.ForEach   => walkTerm(fe.iterable); walkTerm(fe.body)
      case m: Tree.Match      => walkTerm(m.scrutinee); m.cases.foreach(c => walkTerm(c.body))
      case l: Tree.Lambda     => walkTerm(l.body)
      case a: Tree.Apply      => walkTerm(a.fun); a.args.foreach(walkTerm)
      case s: Tree.Select     => walkTerm(s.qual)
      case t: Tree.Try        => walkTerm(t.body); t.catches.foreach(c => walkTerm(c.body)); t.finalizer.foreach(walkTerm)
      case r: Tree.Return     => r.expr.foreach(walkTerm)
      case th: Tree.Throw     => walkTerm(th.expr)
      case _                  =>
    program.units.foreach(walk)
    count

  private def mintAndReport(name: String, files: List[RastFile]): Program =
    val catalog = new CatalogLog(fatal = false)
    val program = TsMinter.mint(files, Substitutions.none, catalog)
    val units = program.units.size
    val syms = program.symbols.all.size
    val unportable = countUnportable(program)
    println(s"[TsMinter] $name: $units units, $syms symbols, $unportable unportable markers")
    program

  test("RAST JSON round-trip: parser.ts loads without error"):
    val rast = loadRast("/rast/path-data-parser/src/parser.rast.json")
    assertEquals(rast.version, 1)
    assert(rast.nodes.nonEmpty)
    assert(rast.symbols.nonEmpty)
    assert(rast.types.nonEmpty)

  test("path-data-parser parser.ts → Program"):
    val rast = loadRast("/rast/path-data-parser/src/parser.rast.json")
    val program = mintAndReport("parser.ts", List(rast))
    assert(program.units.nonEmpty)

  test("path-data-parser all 4 files → Program"):
    val files = List(
      "/rast/path-data-parser/src/parser.rast.json",
      "/rast/path-data-parser/src/absolutize.rast.json",
      "/rast/path-data-parser/src/normalize.rast.json",
      "/rast/path-data-parser/src/index.rast.json",
    ).map(loadRast)
    val program = mintAndReport("path-data-parser", files)
    assert(program.units.size >= 4)
    assert(countUnportable(program) <= 2, s"expected at most 2 unportable (object literals), got ${countUnportable(program)}")

  test("points-on-curve → Program"):
    val files = List(
      "/rast/points-on-curve/src/index.rast.json",
      "/rast/points-on-curve/src/curve-to-bezier.rast.json",
    ).map(loadRast)
    val program = mintAndReport("points-on-curve", files)
    assert(program.units.nonEmpty)

  test("hachure-fill → Program"):
    val files = List(loadRast("/rast/hachure-fill/src/hachure.rast.json"))
    val program = mintAndReport("hachure-fill", files)
    assert(program.units.nonEmpty)

  test("rough.js core (17 files) → Program"):
    val dir = "/rast/roughjs/src/"
    val fileNames = List(
      "core.rast.json", "math.rast.json", "geometry.rast.json",
      "generator.rast.json", "renderer.rast.json",
      "canvas.rast.json", "svg.rast.json", "rough.rast.json",
      "fillers/filler-interface.rast.json", "fillers/filler.rast.json",
      "fillers/hachure-filler.rast.json", "fillers/hatch-filler.rast.json",
      "fillers/zigzag-filler.rast.json", "fillers/zigzag-line-filler.rast.json",
      "fillers/dashed-filler.rast.json", "fillers/dot-filler.rast.json",
      "fillers/scan-line-hachure.rast.json",
    )
    val files = fileNames.map(f => loadRast(dir + f))
    val program = mintAndReport("rough.js", files)
    assert(program.units.size >= 17, s"expected >= 17 units for 17 files, got ${program.units.size}")

  test("KaTeX subset (3 files) → Program"):
    val files = List(
      "/rast/katex/src/ParseError.rast.json",
      "/rast/katex/src/types.rast.json",
      "/rast/katex/src/Namespace.rast.json",
    ).map(loadRast)
    val program = mintAndReport("katex-subset", files)
    assert(program.units.nonEmpty, "KaTeX should produce units")

  test("path-data-parser → emitted Scala source"):
    val files = List(
      "/rast/path-data-parser/src/parser.rast.json",
      "/rast/path-data-parser/src/absolutize.rast.json",
      "/rast/path-data-parser/src/normalize.rast.json",
    ).map(loadRast)

    val jsNumHelper = """  private def jsNum(v: Double): String = {
    if (v.isNaN) { "NaN" }
    else if (v.isPosInfinity) { "Infinity" }
    else if (v.isNegInfinity) { "-Infinity" }
    else if (v == Math.rint(v) && Math.abs(v) < 1e21) { new java.math.BigDecimal(v).toBigInteger.toString }
    else { java.lang.Double.toString(v) }
  }"""
    val config = TsToScalaEmitter.EmitConfig(
      packageName = "ssg.graphs.commons.rough.pathdata",
      imports = List("scala.collection.mutable.ArrayBuffer"),
      braceStyle = true,
      errorClassName = "PathDataParseError",
      extraDeclarations = Map(
        "parser" -> """final class PathDataParseError(message: String) extends RuntimeException(message)
  private def jsNum(v: Double): String = {
    if (v.isNaN) { "NaN" }
    else if (v.isPosInfinity) { "Infinity" }
    else if (v.isNegInfinity) { "-Infinity" }
    else if (v == Math.rint(v) && Math.abs(v) < 1e21) {
      new java.math.BigDecimal(v).toBigInteger.toString
    } else {
      java.lang.Double.toString(v)
    }
  }""",
      ),
      postProcess = Map(
        "parser" -> List(
          ("tokens \\+= data\\((\\d+)\\)", "tokens += jsNum(data\\($1\\))"),
          ("tokens \\+\\+= data", "data.foreach(d => tokens += jsNum(d))"),
          ("\"\" \\+ data\\((\\d+)\\)", "jsNum(data\\($1\\)) + \",\""),
          ("ArrayBuffer\\[String \\| Double\\]", "ArrayBuffer[String]"),
          ("ArrayBuffer\\.empty\\[String \\| Double\\]", "ArrayBuffer.empty[String]"),
          ("\"\" \\+ _m\\.group\\(1\\)\\.toDouble", "jsNum(_m.group(1).toDouble)"),
        ),
      ),
    )
    val emitted = TsToScalaEmitter.emit(files, config)
    
    for ((name, source) <- emitted)
      println(s"=== $name.scala ===")
      println(source)
      println()

    assert(emitted.nonEmpty, "should emit at least one file")
    assert(emitted.contains("parser"), "should emit parser")
    assert(emitted.contains("absolutize"), "should emit absolutize")
    assert(emitted.contains("normalize"), "should emit normalize")

  test("points-on-curve → emitted Scala source"):
    val files = List(
      "/rast/points-on-curve/src/index.rast.json",
      "/rast/points-on-curve/src/curve-to-bezier.rast.json",
    ).map(loadRast)

    val config = TsToScalaEmitter.EmitConfig(
      packageName = "ssg.graphs.commons.rough.curve",
      imports = List("scala.collection.mutable.ArrayBuffer"),
      braceStyle = true,
      skipIndex = false,
      fileNameMap = Map("index" -> "PointsOnCurve", "curve-to-bezier" -> "CurveToBezier"),
      tupleTypeOverrides = Map("Point" -> "Point"),
      tupleFieldOverrides = Map("Point" -> Map(0 -> "x", 1 -> "y")),
      extraDeclarations = Map(
        "PointsOnCurve" -> "final case class Point(x: Double, y: Double)",
      ),
      postProcess = Map(
        "PointsOnCurve" -> List(
          ("\\(Double, Double\\)", "Point"),  // tuple type → Point
          ("\\._1", ".x"), ("\\._2", ".y"),   // tuple accessors → field names
          ("newPoints\\.isDefined \\|\\| Vector\\.empty", "newPoints.getOrElse(ArrayBuffer.empty[Point])"),
          ("val t: Int = 0\\.5", "val t: Double = 0.5"),
          ("Option\\[Vector\\[Point\\]\\]", "Option[ArrayBuffer[Point]]"),
          ("Some\\(outPoints\\)", "Some(outPoints)"),  // keep as-is, ArrayBuffer is fine
          ("var i: Double = 0", "var i: Int = 0"),
          ("var offset: Double = 0", "var offset: Int = 0"),
          ("val d: Double = 0", "val d: Int = 0"),
          ("var maxNdx: Double", "var maxNdx: Int"),
          ("val offset: Double = \\(i \\* 3\\)", "val offset: Int = i * 3"),
          ("start: Double", "start: Int"),
          ("`end`: Double", "`end`: Int"),
          ("numSegments: Double", "numSegments: Int"),
          ("distanceTolerance: Double", "distanceTolerance: Double"),  // keep as Double
          ("distance\\.isDefined && \\(distance > 0\\)", "distance.exists(_ > 0)"),
          ("Some\\(newPoints\\)", "Some(newPoints.to(ArrayBuffer))"),
          ("simplifyPoints\\(points, 0, \\(points\\.length - 1\\)", "simplifyPoints(points, 0, points.length - 1"),
          ("\\(points\\.length - 1\\)\\.toInt", "points.length - 1"),
          ("newPoints\\.length, distance\\)", "newPoints.length, distance.get)"),
          ("\\} \\{ i \\+= 1; i \\}", "  i += 1\n      }"),  // for-loop update inside while body
          ("\\} \\{ offset \\+= 3; offset \\}", "  offset += 3\n      }"),
        ),
        "CurveToBezier" -> List(
          ("\\(Double, Double\\)", "Point"),
          ("\\._1", ".x"), ("\\._2", ".y"),
          ("var i: Double = 0", "var i: Int = 0"),
          ("\\} \\{ i \\+= 1; i \\}", "  i += 1\n      }"),  // for-loop update inside while body
        ),
      ),
    )
    val emitted = TsToScalaEmitter.emit(files, config)
    for ((name, source) <- emitted)
      println(s"=== poc_$name.scala ===")
      println(source)
      println()
    assert(emitted.nonEmpty, "should emit at least one file")

  test("hachure-fill → emitted Scala source"):
    val files = List(
      "/rast/hachure-fill/src/hachure.rast.json",
    ).map(loadRast)

    val config = TsToScalaEmitter.EmitConfig(
      packageName = "ssg.graphs.commons.rough.fillers",
      imports = List("scala.collection.mutable.ArrayBuffer"),
      braceStyle = true,
    )
    val emitted = TsToScalaEmitter.emit(files, config)
    for ((name, source) <- emitted)
      println(s"=== hf_$name.scala ===")
      println(source)
      println()
    assert(emitted.nonEmpty, "should emit at least one file")

  test("hachure-fill → emitted Scala source"):
    val files = List(loadRast("/rast/hachure-fill/src/hachure.rast.json"))

    val config = TsToScalaEmitter.EmitConfig(
      packageName = "ssg.graphs.commons.rough.fillers",
      imports = List(
        "scala.collection.mutable.ArrayBuffer",
        "scala.util.boundary",
        "scala.util.boundary.break",
      ),
      braceStyle = true,
      extraDeclarations = Map(
        "hachure" -> """final case class Point(var x: Double, var y: Double)
final case class Line(p1: Point, p2: Point)
final case class EdgeEntry(ymin: Double, ymax: Double, var x: Double, islope: Double)
final case class ActiveEdgeEntry(s: Double, edge: EdgeEntry)""",
      ),
    )
    val emitted = TsToScalaEmitter.emit(files, config)

    for ((name, source) <- emitted)
      println(s"=== $name.scala ===")
      println(source)
      println()

    assert(emitted.contains("hachure"), "should emit hachure")
