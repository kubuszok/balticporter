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
