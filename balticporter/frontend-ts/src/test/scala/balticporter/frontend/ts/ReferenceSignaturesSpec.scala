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
