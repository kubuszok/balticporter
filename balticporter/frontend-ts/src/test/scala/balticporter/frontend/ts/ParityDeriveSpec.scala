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
      "expression" -> List(("    x + 200\n", 0)),
    )
    val result = ParityDerive.derive(syntheticReference, rastBodies)
    assert(result.emittedSource.contains("x + 100"), "braced body should be replaced")
    assert(result.emittedSource.contains("x + 200"), "expression body should be replaced")
    assert(result.emittedSource.contains("x + 3"), "inline should stay (no RAST match)")
    assertEquals(result.rastCount, 2)
    assertEquals(result.referenceCount, 3) // inline, priv, afterVal

  test("derive: uncompilable pattern blocks substitution"):
    val rastBodies = Map(
      "braced" -> List(("    badPattern( x )\n", 0)),
    )
    val policy = ParityDerive.Policy(uncompilablePatterns = List("badPattern("))
    val result = ParityDerive.derive(syntheticReference, rastBodies, policy)
    assert(result.emittedSource.contains("x + 1"), "braced should keep reference (pattern blocked)")
    val bracedEntry = result.bodies.find(_.methodName == "braced").get
    assertEquals(bracedEntry.source, "reference")
    assert(bracedEntry.why.contains("uncompilable-pattern:badPattern("))

  test("derive: no-rast-symbol recorded"):
    val result = ParityDerive.derive(syntheticReference, Map.empty)
    assertEquals(result.rastCount, 0)
    assertEquals(result.referenceCount, 5)
    assert(result.bodies.forall(_.why == "no-rast-symbol"))

  test("derive: private methods allowed by default"):
    val rastBodies = Map("priv" -> List(("    n * 100\n", 0)))
    val result = ParityDerive.derive(syntheticReference, rastBodies)
    val privEntry = result.bodies.find(_.methodName == "priv").get
    assertEquals(privEntry.source, "rast")

  test("derive: private methods blocked by policy"):
    val rastBodies = Map("priv" -> List(("    n * 100\n", 0)))
    val policy = ParityDerive.Policy(allowPrivate = false)
    val result = ParityDerive.derive(syntheticReference, rastBodies, policy)
    val privEntry = result.bodies.find(_.methodName == "priv").get
    assertEquals(privEntry.source, "reference")
    assertEquals(privEntry.why, "private")

  test("derive: single-body map overload works"):
    val rastBodies = Map("braced" -> ("    x + 999\n", 0))
    val policy = ParityDerive.Policy()
    val result = ParityDerive.derive(syntheticReference, rastBodies, policy)
    assert(result.emittedSource.contains("x + 999"))
    assertEquals(result.rastCount, 1)

  test("formatBodiesTsv: correct format"):
    val entries = List(
      ParityDerive.BodyEntry("foo", "rast", "", 0),
      ParityDerive.BodyEntry("bar", "reference", "no-rast-symbol", 0),
      ParityDerive.BodyEntry("baz", "reference", "uncompilable-pattern:badFunc(", 0),
    )
    val tsv = ParityDerive.formatBodiesTsv(entries)
    assert(tsv.startsWith("method_name\tsource\twhy\trefusal_count\n"))
    assert(tsv.contains("foo\trast\t\t0"))
    assert(tsv.contains("bar\treference\tno-rast-symbol\t0"))
    assert(tsv.contains("baz\treference\tuncompilable-pattern:badFunc(\t0"))

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
        ("    s\"rast-second-$s\"\n", 0),
      ),
    )
    val result = ParityDerive.derive(reference, rastBodies)
    assert(result.emittedSource.contains("rast-first"))
    assert(result.emittedSource.contains("rast-second"))
    assertEquals(result.rastCount, 2)
