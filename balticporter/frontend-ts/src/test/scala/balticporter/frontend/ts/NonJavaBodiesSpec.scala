package balticporter.frontend.ts

import com.github.plokhotnyuk.jsoniter_scala.core.writeToArray
import java.nio.file.{ Files, Path }

class NonJavaBodiesSpec extends munit.FunSuite:

  private def function(name: String, statements: RastNode*): RastNode =
    RastNode(
      "FunctionDeclaration",
      0,
      (0, 0),
      children = List(RastNode("Identifier", 0, (0, 0), text = Some(name)), RastNode("Block", 0, (0, 0), children = statements.toList))
    )

  private def returning(expression: RastNode): RastNode = RastNode("ReturnStatement", 0, (0, 0), children = List(expression))
  private def number(n:             Double):   RastNode = RastNode("NumericLiteral", 0, (0, 0), value = Some(RastValue.Num(n)))

  private val shapesRast = RastFile(
    version = 1,
    path = "src/shapes.ts",
    sha256 = "",
    nodes = List(
      function("area", returning(number(10))),
      function("area", returning(number(20))),
      function("blocked", returning(RastNode("Identifier", 0, (0, 0), text = Some("forbiddenCall")))),
      function("holed", RastNode("DebuggerStatement", 0, (0, 0))),
      function("toString", returning(number(5)))
    ),
    symbols = Map.empty,
    types = Map.empty
  )

  private val shapesReference =
    """package demo
      |
      |object Shapes {
      |
      |  def area(x: Int): Int = {
      |    x * 2
      |  }
      |
      |  def area(x: Int, y: Int): Int = {
      |    x * y
      |  }
      |
      |  def area(x: Int, y: Int, z: Int): Int = {
      |    x * y * z
      |  }
      |
      |  def blocked(): Int =
      |    1
      |
      |  def holed(): Int =
      |    2
      |
      |  def absent(): Int =
      |    3
      |
      |  override def toString: String =
      |    "Shapes"
      |}
      |""".stripMargin

  /** One body per top-level function, in source order, so two functions of one name are two occurrences. */
  private def bodiesOf(rasts: List[RastFile]): ParityDerive.Bodies =
    val bodies = for
      rast <- rasts
      node <- rast.nodes if node.kind == "FunctionDeclaration"
      name <- node.children.find(_.kind == "Identifier").flatMap(_.text)
      block <- node.children.find(_.kind == "Block")
    yield
      val translated = dedicated.DefmethodBodyTranslator.translateBody(dedicated.DefmethodEntry("_free_", name, Nil, block), Nil, "    ")
      name -> ParityDerive.TranslatedBody(translated.scalaBody, translated.refusalReasons)
    ParityDerive.Bodies(bodies.groupMap(_._1)(_._2))

  private val library = NonJavaBodies.Library(
    name = "shapes",
    policy = ParityDerive.Policy(uncompilablePatterns = List("forbiddenCall"), keepReferenceOnRefusal = true),
    readRast = Rast.readFile(_: Path),
    modules = _ =>
      Right(
        List(
          NonJavaBodies.Module("Shapes.scala", "src/shapes.rast.json", Nil, bodiesOf),
          NonJavaBodies.Module("Missing.scala", "src/missing.rast.json", Nil, bodiesOf),
          NonJavaBodies.Module("Broken.scala", "src/broken.rast.json", Nil, bodiesOf)
        )
      )
  )

  private def fixture(): (Path, Path, Path) =
    val root      = Files.createTempDirectory("non-java-bodies")
    val reference = root.resolve("reference")
    val rast      = root.resolve("rast")
    Files.createDirectories(reference.resolve("demo"))
    Files.createDirectories(reference.resolve("other"))
    Files.createDirectories(rast.resolve("src"))
    Files.writeString(reference.resolve("demo/Shapes.scala"), shapesReference)
    Files.writeString(reference.resolve("demo/Missing.scala"), "package demo\n\nobject Missing {\n\n  def missing(): Int = 0\n}\n")
    Files.writeString(reference.resolve("demo/Broken.scala"), "package demo\n\nobject Broken {\n\n  def broken(): Int = 0\n}\n")
    Files.writeString(
      reference.resolve("other/Unfed.scala"),
      "package other\n\nobject Unfed {\n\n  def area(): Int = 0\n\n  def lonely(): Int = 0\n}\n"
    )
    Files.write(rast.resolve("src/shapes.rast.json"), writeToArray(shapesRast)(using Rast.fileCodec))
    Files.writeString(rast.resolve("src/broken.rast.json"), "{ not a syntax tree")
    (root, reference, rast)

  private def run(): (Path, NonJavaBodies.Run) =
    val (root, reference, rast) = fixture()
    NonJavaBodies.build(library, reference, rast) match
      case built:   NonJavaBodies.Built   => (root, built.derive(root.resolve("out"), root.resolve("report")))
      case refused: NonJavaBodies.Refused => fail(refused.message)

  test("a library with no syntax-tree directory is refused"):
    val (root, reference, _) = fixture()
    assert(NonJavaBodies.build(library, reference, root.resolve("absent")).isInstanceOf[NonJavaBodies.Refused])

  test("bodies.tsv names every member's source and why a reference body was kept"):
    val (root, _) = run()
    val expected  = List(
      List("file", "member", "occurrence", "source", "why"),
      List("demo/Broken.scala", "broken", "0", "reference", "translator-refusal:unreadable-rast"),
      List("demo/Missing.scala", "missing", "0", "reference", "translator-refusal:missing-rast"),
      List("demo/Shapes.scala", "area", "0", "translated", ""),
      List("demo/Shapes.scala", "area", "1", "translated", ""),
      List("demo/Shapes.scala", "area", "2", "reference", "occurrence-out-of-range"),
      List("demo/Shapes.scala", "blocked", "0", "reference", "uncompilable-pattern:forbiddenCall"),
      List("demo/Shapes.scala", "holed", "0", "reference", "translator-refusal:UnhandledStatement:DebuggerStatement"),
      List("demo/Shapes.scala", "absent", "0", "reference", "no-translated-body"),
      List("demo/Shapes.scala", "toString", "-", "reference", "unclassified"),
      List("other/Unfed.scala", "area", "0", "reference", "unclassified"),
      List("other/Unfed.scala", "lonely", "0", "reference", "no-translated-body")
    ).map(_.mkString("\t")).mkString("", "\n", "\n")
    val tsv = Files.readString(root.resolve("report/bodies.tsv"))
    assertEquals(tsv, expected)
    assert(!tsv.contains(root.toString), "the table holds relative paths only")

  test("each member of an overloaded name takes the body of its own occurrence"):
    val (root, _) = run()
    val emitted   = Files.readString(root.resolve("out/demo/Shapes.scala"))
    assert(emitted.contains("def area(x: Int): Int =\n    10\n"), emitted)
    assert(emitted.contains("def area(x: Int, y: Int): Int =\n    20\n"), emitted)
    assert(emitted.contains("x * y * z"), "the third overload has no third body and keeps the reference")
    assert(
      emitted.contains("    1\n") && emitted.contains("    2\n") && emitted.contains("\"Shapes\""),
      "refused and unoffered members keep the reference"
    )
    assert(!emitted.contains("???"), "a body the translator left a hole in is not emitted")

  test("the summary's numbers add up to the table's rows"):
    val (root, result) = run()
    val summary        = result.summary
    assertEquals(summary.total, result.rows.size)
    assertEquals(summary.translated + summary.byWhy.map(_._2).sum, summary.total)
    assertEquals(
      summary.line,
      "translated 2/11 (18.2%); reference: no-translated-body=2, occurrence-out-of-range=1, translator-refusal=3, unclassified=2, uncompilable-pattern=1"
    )
    assertEquals(Files.readString(root.resolve("report/bodies-summary.txt")), summary.line + "\n")

  test("two runs over the same inputs write the same table"):
    val (first, _)  = run()
    val (second, _) = run()
    assertEquals(Files.readString(first.resolve("report/bodies.tsv")), Files.readString(second.resolve("report/bodies.tsv")))
