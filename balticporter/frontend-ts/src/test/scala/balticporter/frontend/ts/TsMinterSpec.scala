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

  test("RAST JSON round-trip: parser.ts loads without error"):
    val rast = loadRast("/rast/path-data-parser/src/parser.rast.json")
    assertEquals(rast.version, 1)
    assert(rast.nodes.nonEmpty, "should have top-level nodes")
    assert(rast.symbols.nonEmpty, "should have symbols")
    assert(rast.types.nonEmpty, "should have types")

  test("path-data-parser parser.ts → Program"):
    val rast = loadRast("/rast/path-data-parser/src/parser.rast.json")
    val catalog = new CatalogLog(fatal = false)
    val program = TsMinter.mint(List(rast), Substitutions.none, catalog)
    assert(program.units.nonEmpty, s"should produce at least one unit, got ${program.units.size}")

  test("path-data-parser all 4 files → Program"):
    val files = List(
      "/rast/path-data-parser/src/parser.rast.json",
      "/rast/path-data-parser/src/absolutize.rast.json",
      "/rast/path-data-parser/src/normalize.rast.json",
      "/rast/path-data-parser/src/index.rast.json",
    ).map(loadRast)
    val catalog = new CatalogLog(fatal = false)
    val program = TsMinter.mint(files, Substitutions.none, catalog)

    val unitCount = program.units.size
    val symCount = program.symbols.all.size

    var unportableCount = 0
    def walk(stmt: Statement): Unit = stmt match
      case u: Tree.Unportable => unportableCount += 1
      case cd: Tree.ClassDef  => cd.body.foreach(walk)
      case dd: Tree.DefDef    => dd.rhs.foreach(walkTerm)
      case _                  =>
    def walkTerm(term: Term): Unit = term match
      case u: Tree.Unportable =>
        unportableCount += 1
      case b: Tree.Block =>
        b.stats.foreach(walk)
        walkTerm(b.expr)
      case i: Tree.If =>
        walkTerm(i.cond)
        walkTerm(i.thenp)
        walkTerm(i.elsep)
      case _ =>
    program.units.foreach(walk)

    println(s"[TsMinter] path-data-parser: $unitCount units, $symCount symbols, $unportableCount unportable markers")
    assert(unitCount >= 4, s"expected >= 4 units (one per file module), got $unitCount")
