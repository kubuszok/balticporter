package balticporter.frontend.ts

class ParityDeriveSpec extends munit.FunSuite:

  private val syntheticReference =
    """package test
      |
      |object Foo {
      |
      |  def braced(x: Int): Int = {
      |    x + 1
      |  }
      |
      |  def expression(x: Int): Int =
      |    x + 2
      |
      |  def inline(x: Int): Int = x + 3
      |
      |  private def priv(n: Int): Int =
      |    n * 2
      |
      |  val constant = 42
      |
      |  def afterVal(x: Int): Int = x
      |}
      |""".stripMargin

  test("derive: RAST body replaces reference"):
    val rastBodies = Map(
      "braced" -> List(("    x + 100\n", 0)),
      "expression" -> List(("    x + 200\n", 0))
    )
    val result = ParityDerive.derive(syntheticReference, rastBodies)
    assert(result.emittedSource.contains("x + 100"), "braced body should be replaced")
    assert(result.emittedSource.contains("x + 200"), "expression body should be replaced")
    assert(result.emittedSource.contains("x + 3"), "inline should stay (no RAST match)")
    assertEquals(result.rastCount, 2)
    assertEquals(result.referenceCount, 3) // inline, priv, afterVal

  test("derive: uncompilable pattern blocks substitution"):
    val rastBodies = Map(
      "braced" -> List(("    badPattern( x )\n", 0))
    )
    val policy = ParityDerive.Policy(uncompilablePatterns = List("badPattern("))
    val result = ParityDerive.derive(syntheticReference, rastBodies, policy)
    assert(result.emittedSource.contains("x + 1"), "braced should keep reference (pattern blocked)")
    val bracedEntry = result.bodies.find(_.methodName == "braced").get
    assertEquals(bracedEntry.source, "reference")
    assert(bracedEntry.why.contains("uncompilable-pattern:badPattern("))

  test("derive: a member with no translated body is recorded as such"):
    val result = ParityDerive.derive(syntheticReference, Map.empty[String, List[(String, Int)]])
    assertEquals(result.rastCount, 0)
    assertEquals(result.referenceCount, 5)
    assert(result.bodies.forall(_.why == "no-translated-body"))

  test("derive: private methods allowed by default"):
    val rastBodies = Map("priv" -> List(("    n * 100\n", 0)))
    val result     = ParityDerive.derive(syntheticReference, rastBodies)
    val privEntry  = result.bodies.find(_.methodName == "priv").get
    assertEquals(privEntry.source, "translated")

  test("derive: private methods blocked by policy"):
    val rastBodies = Map("priv" -> List(("    n * 100\n", 0)))
    val policy     = ParityDerive.Policy(allowPrivate = false)
    val result     = ParityDerive.derive(syntheticReference, rastBodies, policy)
    val privEntry  = result.bodies.find(_.methodName == "priv").get
    assertEquals(privEntry.source, "reference")
    assertEquals(privEntry.why, "private")

  test("derive: single-body map overload works"):
    val rastBodies = Map("braced" -> ("    x + 999\n", 0))
    val policy     = ParityDerive.Policy()
    val result     = ParityDerive.derive(syntheticReference, rastBodies, policy)
    assert(result.emittedSource.contains("x + 999"))
    assertEquals(result.rastCount, 1)

  test("derive: a body the translator left a hole in is used unless the policy keeps the reference"):
    val bodies = ParityDerive.Bodies(Map("braced" -> List(ParityDerive.TranslatedBody("    ??? /* AwaitExpression */\n", List("AwaitExpression")))))
    val used   = ParityDerive.derive(syntheticReference, bodies, ParityDerive.Policy())
    assertEquals(used.bodies.find(_.methodName == "braced").get.source, "translated")
    val kept  = ParityDerive.derive(syntheticReference, bodies, ParityDerive.Policy(keepReferenceOnRefusal = true))
    val entry = kept.bodies.find(_.methodName == "braced").get
    assertEquals((entry.source, entry.why), ("reference", "translator-refusal:AwaitExpression"))
    assert(kept.emittedSource.contains("x + 1"))

  test("derive: an alias is tried after the member's own name"):
    val bodies = ParityDerive.Bodies(Map("upstreamName" -> List(ParityDerive.TranslatedBody("    x + 7\n"))))
    val result = ParityDerive.derive(syntheticReference, bodies, ParityDerive.Policy(aliases = Map("braced" -> List("upstreamName"))))
    assert(result.emittedSource.contains("x + 7"))
    assertEquals(ParityDerive.derive(syntheticReference, bodies, ParityDerive.Policy()).rastCount, 0)

  test("derive: sequential consumption for duplicate method names"):
    val reference =
      """package test
        |
        |object Multi {
        |
        |  def toMarkup(n: Int): String =
        |    s"first-$n"
        |
        |  def toMarkup(s: String): String =
        |    s"second-$s"
        |}
        |""".stripMargin
    val rastBodies = Map(
      "toMarkup" -> List(
        ("    s\"rast-first-$n\"\n", 0),
        ("    s\"rast-second-$s\"\n", 0)
      )
    )
    val result = ParityDerive.derive(reference, rastBodies)
    assert(result.emittedSource.contains("rast-first"))
    assert(result.emittedSource.contains("rast-second"))
    assertEquals(result.rastCount, 2)

  test("derive: unoffered members carry skeleton-cannot-offer with the reason kind"):
    val reference =
      """package test
        |
        |object Mixed {
        |
        |  def offered(x: Int): Int =
        |    x + 1
        |
        |  override def overridden(x: Int): Int =
        |    x + 2
        |
        |  protected def guarded(x: Int): Int =
        |    x + 3
        |
        |    def nested(x: Int): Int =
        |      x + 4
        |}
        |""".stripMargin
    val bodies = ParityDerive.Bodies(
      Map(
        "offered" -> List(ParityDerive.TranslatedBody("    x * 10\n")),
        "overridden" -> List(ParityDerive.TranslatedBody("    x * 20\n")),
        "guarded" -> List(ParityDerive.TranslatedBody("    x * 30\n")),
        "nested" -> List(ParityDerive.TranslatedBody("    x * 40\n"))
      )
    )
    val result = ParityDerive.derive(reference, bodies, ParityDerive.Policy())
    assertEquals(result.rastCount, 1) // only `offered` is replaceable
    val entries = (result.bodies ++ result.unoffered).sortBy(_.line)
    val offered = entries.find(_.methodName == "offered").get
    assertEquals(offered.source, "translated")
    val overridden = entries.find(_.methodName == "overridden").get
    assertEquals((overridden.offered, overridden.why), (false, "skeleton-cannot-offer:override"))
    val guarded = entries.find(_.methodName == "guarded").get
    assertEquals((guarded.offered, guarded.why), (false, "skeleton-cannot-offer:protected"))
    val nestedE = entries.find(_.methodName == "nested").get
    assertEquals((nestedE.offered, nestedE.why), (false, "skeleton-cannot-offer:nested"))

  test("derive: boundary-braced body does not bleed into the next method"):
    val reference =
      """package test
        |
        |object BoundaryTest {
        |
        |  def first(x: Int, y: Int): Boolean = boundary {
        |    if (x > 0) {
        |      break(true)
        |    }
        |    false
        |  }
        |
        |  def second(x: Int): Int =
        |    x + 1
        |}
        |""".stripMargin
    val methods = ReferenceSkeleton.findMethodBoundaries(reference.split("\n", -1).toList)
    assertEquals(methods.size, 2, s"should find two methods: ${methods.map(_.name)}")
    assertEquals(methods(0).name, "first")
    assertEquals(methods(1).name, "second")
    // The first method's body must include the closing `}` of boundary
    val firstEnd = methods(0).bodyEndLine
    val lines    = reference.split("\n", -1).toList
    assert(lines(firstEnd).trim == "}", s"first body end should be the closing brace: '${lines(firstEnd)}'")
    // Derive with a replacement body — `second` must stay intact
    val bodies = ParityDerive.Bodies(Map("first" -> List(ParityDerive.TranslatedBody("    true\n"))))
    val result = ParityDerive.derive(reference, bodies, ParityDerive.Policy())
    assert(result.emittedSource.contains("x + 1"), s"second method must be intact: ${result.emittedSource}")
    assert(!result.emittedSource.contains("break(true)"), "first method's old body must be gone")

  test("derive: try-braced body is correctly bounded"):
    val reference =
      """package test
        |
        |object TryTest {
        |
        |  def safeDivide(a: Int, b: Int): Int = try {
        |    a / b
        |  } catch {
        |    case _: ArithmeticException => 0
        |  }
        |
        |  def next(x: Int): Int = x
        |}
        |""".stripMargin
    val methods = ReferenceSkeleton.findMethodBoundaries(reference.split("\n", -1).toList)
    assertEquals(methods.size, 2)
    assertEquals(methods(0).name, "safeDivide")
    assertEquals(methods(1).name, "next")
    val bodies = ParityDerive.Bodies(Map("safeDivide" -> List(ParityDerive.TranslatedBody("    42\n"))))
    val result = ParityDerive.derive(reference, bodies, ParityDerive.Policy())
    assert(result.emittedSource.contains("42"), "safeDivide body replaced")
    assert(!result.emittedSource.contains("ArithmeticException"), "old body gone")

  test("derive: abstract def is skipped, not offered for replacement"):
    val reference =
      """package test
        |
        |trait Base {
        |  def abstractMethod(x: Int): Boolean
        |}
        |
        |object Impl {
        |
        |  def concreteMethod(x: Int): Boolean =
        |    x > 0
        |}
        |""".stripMargin
    val methods = ReferenceSkeleton.findMethodBoundaries(reference.split("\n", -1).toList)
    assertEquals(methods.size, 1, s"should find only the concrete method: ${methods.map(_.name)}")
    assertEquals(methods(0).name, "concreteMethod")

  test("derive: expression body followed by class-closing brace does not consume the brace"):
    val reference =
      """package test
        |
        |class Opts(val phantom: Boolean, val color: String) {
        |
        |  def braced(): Int = {
        |    42
        |  }
        |
        |  def getColor(): String =
        |    if (phantom) {
        |      "transparent"
        |    } else {
        |      color
        |    }
        |}
        |
        |object Opts {
        |  val DEFAULT: Int = 6
        |}
        |""".stripMargin
    val lines   = reference.split("\n", -1).toList
    val methods = ReferenceSkeleton.findMethodBoundaries(lines)
    assertEquals(methods.size, 2, s"should find braced and getColor: ${methods.map(_.name)}")
    assertEquals(methods(0).name, "braced")
    assertEquals(methods(1).name, "getColor")
    // Check that getColor body end does NOT include the class-closing `}`
    val gcEnd       = methods(1).bodyEndLine
    val classCloser = lines.indexWhere(l => l.trim == "}" && l.takeWhile(_ == ' ').length <= 2, gcEnd + 1)
    assert(
      gcEnd < classCloser,
      s"getColor bodyEnd ($gcEnd: '${lines(gcEnd).trim}') must be before class closer ($classCloser: '${lines(classCloser).trim}')"
    )
    // Derive test: class closer must survive
    val bodies = ParityDerive.Bodies(Map("getColor" -> List(ParityDerive.TranslatedBody("    \"replaced\"\n"))))
    val result = ParityDerive.derive(reference, bodies, ParityDerive.Policy())
    assert(result.emittedSource.contains("\"replaced\""), "body replaced")
    assert(result.emittedSource.contains("object Opts"), s"companion object must be intact:\n${result.emittedSource}")
    assert(!result.emittedSource.contains("transparent"), "old body gone")
