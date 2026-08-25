package balticporter.verify

class ApiParityCheckSpec extends munit.FunSuite:

  // ---- surface parsing ----

  test("parseSurface extracts public class members") {
    val src =
      """package foo
        |class Bar:
        |  def baz(x: Int): String = ???
        |  val qux: Int = 1
        |  private def secret: Int = 2
        |""".stripMargin

    val result = ApiParityCheck.parseSurface(List(writeTempScala("Bar.scala", src)))
    assert(result.isRight, result.left.getOrElse(""))
    val decls = result.toOption.get
    // Should have: class Bar, def baz/1, val qux, but NOT private def secret
    assert(decls.exists(d => d.kind == "class" && d.name == "Bar"), s"missing class Bar in $decls")
    assert(decls.exists(d => d.kind == "def" && d.name == "baz" && d.arity == 1), s"missing def baz in $decls")
    assert(decls.exists(d => d.kind == "val" && d.name == "qux"), s"missing val qux in $decls")
    assert(!decls.exists(d => d.name == "secret"), s"private member should not appear in $decls")
  }

  test("parseSurface extracts companion object members") {
    val src =
      """class Foo
        |object Foo:
        |  def create(): Foo = ???
        |  var count: Int = 0
        |""".stripMargin

    val result = ApiParityCheck.parseSurface(List(writeTempScala("Foo.scala", src)))
    assert(result.isRight)
    val decls = result.toOption.get
    assert(decls.exists(d => d.kind == "class" && d.name == "Foo"))
    assert(decls.exists(d => d.kind == "object" && d.name == "Foo"))
    assert(decls.exists(d => d.path == "/Foo$" && d.kind == "def" && d.name == "create"))
    assert(decls.exists(d => d.path == "/Foo$" && d.kind == "var" && d.name == "count"))
  }

  test("parseSurface extracts enum cases") {
    val src =
      """enum Color:
        |  case Red, Green, Blue
        |""".stripMargin

    val result = ApiParityCheck.parseSurface(List(writeTempScala("Color.scala", src)))
    assert(result.isRight)
    val decls = result.toOption.get
    assert(decls.exists(d => d.kind == "enum" && d.name == "Color"))
    assert(decls.exists(d => d.kind == "case" && d.name == "Red"))
    assert(decls.exists(d => d.kind == "case" && d.name == "Green"))
    assert(decls.exists(d => d.kind == "case" && d.name == "Blue"))
  }

  test("parseSurface includes type aliases") {
    val src =
      """trait Foo:
        |  type Bar
        |  type Baz = Int
        |""".stripMargin

    val result = ApiParityCheck.parseSurface(List(writeTempScala("Foo.scala", src)))
    assert(result.isRight)
    val decls = result.toOption.get
    assert(decls.exists(d => d.kind == "type" && d.name == "Bar"))
    assert(decls.exists(d => d.kind == "type" && d.name == "Baz"))
  }

  // ---- accessor classification ----

  test("accessor family: getX in emitted, x as val in reference") {
    val emitted = List(
      ApiParityCheck.SurfaceDecl("/Foo", "class", "Foo", 0),
      ApiParityCheck.SurfaceDecl("/Foo", "def", "getWidth", 0),
      ApiParityCheck.SurfaceDecl("/Foo", "def", "setWidth", 1),
    )
    val reference = List(
      ApiParityCheck.SurfaceDecl("/Foo", "class", "Foo", 0),
      ApiParityCheck.SurfaceDecl("/Foo", "var", "width", 0),
    )
    val divs = ApiParityCheck.compare(emitted, reference, Map.empty)
    val families = divs.map(_.family).distinct
    assert(families.contains("accessor"), s"expected accessor family, got $families")
    assert(!families.contains("unclassified"), s"should not have unclassified, got ${divs.filter(_.family == "unclassified")}")
  }

  // ---- static-placement classification ----

  test("static-placement family: class vs companion member placement") {
    val emitted = List(
      ApiParityCheck.SurfaceDecl("/Foo$", "def", "create", 0),
    )
    val reference = List(
      ApiParityCheck.SurfaceDecl("/Foo", "def", "create", 0),
    )
    val divs = ApiParityCheck.compare(emitted, reference, Map.empty)
    assert(divs.forall(_.family == "static-placement"),
      s"expected static-placement, got ${divs.map(d => (d.family, d.detail))}")
  }

  // ---- mutability classification ----

  test("mutability family: val vs var drift") {
    val emitted = List(
      ApiParityCheck.SurfaceDecl("/Foo", "var", "x", 0),
    )
    val reference = List(
      ApiParityCheck.SurfaceDecl("/Foo", "val", "x", 0),
    )
    val divs = ApiParityCheck.compare(emitted, reference, Map.empty)
    assert(divs.exists(_.family == "mutability"), s"expected mutability, got ${divs.map(_.family)}")
  }

  // ---- hand-port-extra and port-extra ----

  test("hand-port-extra: member only in reference") {
    val emitted = List(
      ApiParityCheck.SurfaceDecl("/Foo", "class", "Foo", 0),
    )
    val reference = List(
      ApiParityCheck.SurfaceDecl("/Foo", "class", "Foo", 0),
      ApiParityCheck.SurfaceDecl("/Foo", "def", "helperMethod", 0),
    )
    val divs = ApiParityCheck.compare(emitted, reference, Map.empty)
    assert(divs.exists(_.family == "hand-port-extra"), s"expected hand-port-extra, got ${divs.map(_.family)}")
  }

  test("port-extra: member only in emitted") {
    val emitted = List(
      ApiParityCheck.SurfaceDecl("/Foo", "class", "Foo", 0),
      ApiParityCheck.SurfaceDecl("/Foo", "def", "javaOnly", 2),
    )
    val reference = List(
      ApiParityCheck.SurfaceDecl("/Foo", "class", "Foo", 0),
    )
    val divs = ApiParityCheck.compare(emitted, reference, Map.empty)
    assert(divs.exists(_.family == "port-extra"), s"expected port-extra, got ${divs.map(_.family)}")
  }

  // ---- package normalisation ----

  test("normalisePath applies longest-prefix-first rename") {
    val renames = Map("dest.sub" -> "org.upstream.core", "dest" -> "org.upstream")
    // inverse renames for reference -> emitted direction
    val inverse = renames.map((k, v) => (v, k))
    val path = "/org/upstream/core/Widget"
    val normalised = ApiParityCheck.normalisePath(path, inverse)
    assertEquals(normalised, "/dest/sub/Widget")
  }

  test("normalisePath is identity when no rename matches") {
    val renames = Map("foo" -> "bar")
    assertEquals(ApiParityCheck.normalisePath("/baz/Qux", renames), "/baz/Qux")
  }

  // ---- identical surfaces produce no divergences ----

  test("identical surfaces produce zero divergences") {
    val surface = List(
      ApiParityCheck.SurfaceDecl("/Foo", "class", "Foo", 0),
      ApiParityCheck.SurfaceDecl("/Foo", "def", "bar", 1),
      ApiParityCheck.SurfaceDecl("/Foo", "val", "baz", 0),
    )
    val divs = ApiParityCheck.compare(surface, surface, Map.empty)
    assertEquals(divs.size, 0, s"expected no divergences, got $divs")
  }

  // ---- lane naming ----

  test("lane names follow api-parity(<family>) pattern") {
    ApiParityCheck.Families.foreach { f =>
      assertEquals(ApiParityCheck.lane(f), s"api-parity($f)")
    }
  }

  test("AllLanes has one lane per family") {
    assertEquals(ApiParityCheck.AllLanes.size, ApiParityCheck.Families.size)
  }

  // ---- check produces CheckReport.Finding per divergence ----

  test("check returns findings with correct check names") {
    import balticporter.core.ParityRef
    val emittedDir = writeTempScala("Emitted.scala",
      """class Emitted:
        |  def getX(): Int = 1
        |""".stripMargin)
    val refDir = writeTempScala("Emitted.scala",
      """class Emitted:
        |  val x: Int = 1
        |""".stripMargin)
    val ref = ParityRef(roots = List(refDir))
    val findings = ApiParityCheck.check(ref, emittedDir, Map.empty)
    // Every finding's check should start with "api-parity("
    findings.foreach { f =>
      assert(f.check.startsWith("api-parity("), s"unexpected check name: ${f.check}")
    }
  }

  // ---- helpers ----

  private def writeTempScala(name: String, content: String): java.nio.file.Path =
    val dir = java.nio.file.Files.createTempDirectory("api-parity-test-")
    dir.toFile.deleteOnExit()
    val file = dir.resolve(name)
    java.nio.file.Files.writeString(file, content)
    file.toFile.deleteOnExit()
    dir
