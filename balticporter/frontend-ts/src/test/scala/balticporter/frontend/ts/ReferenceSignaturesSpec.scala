package balticporter.frontend.ts

class ReferenceSignaturesSpec extends munit.FunSuite:

  private val sampleSource =
    """package demo
      |
      |object Calculator:
      |
      |  def add(a: Int, b: Int): Int =
      |    a + b
      |
      |  def subtract(a: Int, b: Int): Int =
      |    a - b
      |
      |  def negate(x: Double): Double =
      |    -x
      |
      |  def identity: String =
      |    "calculator"
      |
      |  private def helper(x: Int): Boolean =
      |    x > 0
      |
      |  def withDefault(x: Int, y: Int = 10): Int =
      |    x + y
      |
      |  def withBacktick(`type`: String): String =
      |    `type`
      |
      |  def multiLine(
      |    longParamName: String,
      |    anotherParam: Int
      |  ): Boolean =
      |    longParamName.nonEmpty
      |""".stripMargin

  test("parseFile extracts single-line method signatures"):
    val sigs   = ReferenceSignatures.parseFile("Calculator", sampleSource)
    val byName = sigs.map((_, sig) => sig.methodName -> sig).toMap
    assert(byName.contains("add"), "should find add")
    assertEquals(byName("add").params.map(_.name), List("a", "b"))
    assertEquals(byName("add").params.map(_.tpe), List("Int", "Int"))
    assertEquals(byName("add").returnType, "Int")

  test("parseFile extracts no-param methods"):
    val sigs   = ReferenceSignatures.parseFile("Calculator", sampleSource)
    val byName = sigs.map((_, sig) => sig.methodName -> sig).toMap
    assert(byName.contains("identity"), "should find identity")
    assertEquals(byName("identity").params, Nil)
    assertEquals(byName("identity").returnType, "String")

  test("parseFile strips default values"):
    val sigs   = ReferenceSignatures.parseFile("Calculator", sampleSource)
    val byName = sigs.map((_, sig) => sig.methodName -> sig).toMap
    assert(byName.contains("withDefault"), "should find withDefault")
    assertEquals(byName("withDefault").params.map(_.tpe), List("Int", "Int"))

  test("parseFile handles backtick-quoted names"):
    val sigs   = ReferenceSignatures.parseFile("Calculator", sampleSource)
    val byName = sigs.map((_, sig) => sig.methodName -> sig).toMap
    assert(byName.contains("withBacktick"), "should find withBacktick")
    assertEquals(byName("withBacktick").params.map(_.name), List("type"))

  test("parseFile joins multi-line signatures"):
    val sigs   = ReferenceSignatures.parseFile("Calculator", sampleSource)
    val byName = sigs.map((_, sig) => sig.methodName -> sig).toMap
    assert(byName.contains("multiLine"), "should find multiLine")
    assertEquals(byName("multiLine").params.map(_.name), List("longParamName", "anotherParam"))
    assertEquals(byName("multiLine").returnType, "Boolean")

  test("TypeOracle: exact and case-insensitive lookup"):
    val sigs   = ReferenceSignatures.parseFile("Calculator", sampleSource)
    val oracle = ReferenceSignatures.TypeOracle.fromEntries(sigs)
    assertEquals(oracle.paramType("Calculator", "add", "a"), "Int")
    assertEquals(oracle.returnType("Calculator", "add"), "Int")
    assertEquals(oracle.paramType("Calculator", "Add", "a"), "Int")
    assertEquals(oracle.paramType("Calculator", "missing", "x"), "Any")

  // ---- buildIndices ----

  private val refSource1 =
    """object BuildCommon:
      |  def makeSpan(classes: Array[String], children: Array[Any]): Span =
      |    ???
      |  def makeFragment(children: Array[Any]): DocumentFragment =
      |    ???
      |""".stripMargin

  private val refSource2 =
    """object BuildHTML:
      |  def buildExpression(expr: Array[Any], options: Options): Array[HtmlNode] =
      |    ???
      |  def buildGroup(group: Any, options: Options): HtmlNode =
      |    ???
      |""".stripMargin

  private val refSource3 =
    """final case class NodeStyling(
      |  var mode: Mode,
      |  var loc: Nullable[SourceLocation] = Nullable.Null,
      |  var style: StyleStr = StyleStr.Display,
      |  var body: Array[Any] = Array.empty
      |) extends AnyNode {
      |  override def nodeType: String = "styling"
      |}
      |""".stripMargin

  test("buildIndices: callee index resolves unique names"):
    val (ci, _, _) = ReferenceSignatures.buildIndices(List(("BuildCommon", refSource1), ("BuildHTML", refSource2)))
    assertEquals(ci.resolve("makeSpan"), Right("BuildCommon.makeSpan"))
    assertEquals(ci.resolve("buildGroup"), Right("BuildHTML.buildGroup"))
    assert(ci.resolve("noSuchFn").isLeft)

  test("buildIndices: constructor schema parses case class parameters"):
    val (_, _, cs) = ReferenceSignatures.buildIndices(List(("NodeStyling", refSource3)))
    val params     = cs.get("NodeStyling")
    assert(params.isDefined, "should find NodeStyling constructor")
    assertEquals(params.get.map(_.name), List("mode", "loc", "style", "body"))
    assert(params.get.find(_.name == "loc").exists(_.hasDefault), "loc should have a default")
    assert(!params.get.find(_.name == "mode").exists(_.hasDefault), "mode should not have a default")

  test("buildIndices: member index includes constructor params"):
    val (_, mi, _) = ReferenceSignatures.buildIndices(List(("NodeStyling", refSource3)))
    assert(mi.knowsType("NodeStyling"), "should know NodeStyling")
    assert(mi.hasMember("NodeStyling", "mode"), "should have mode")
    assert(mi.hasMember("NodeStyling", "body"), "should have body")
    assert(!mi.hasMember("NodeStyling", "children"), "should not have children")
