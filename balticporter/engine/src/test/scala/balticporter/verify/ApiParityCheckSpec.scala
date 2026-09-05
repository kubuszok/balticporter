package balticporter.verify

class ApiParityCheckSpec extends munit.FunSuite:

  // ---- surface parsing ----

  test("parseSurface extracts public and protected class members, excludes private") {
    val src =
      """package foo
        |class Bar:
        |  def baz(x: Int): String = ???
        |  val qux: Int = 1
        |  protected def shielded: Int = 3
        |  private def secret: Int = 2
        |""".stripMargin

    val result = ApiParityCheck.parseSurface(List(writeTempScala("Bar.scala", src)))
    assert(result.isRight, result.left.getOrElse(""))
    val decls = result.toOption.get
    // Should have: class Bar, def baz/1, val qux, protected def shielded, but NOT private def secret
    assert(decls.exists(d => d.kind == "class" && d.name == "Bar"), s"missing class Bar in $decls")
    assert(decls.exists(d => d.kind == "def" && d.name == "baz" && d.arity == 1), s"missing def baz in $decls")
    assert(decls.exists(d => d.kind == "val" && d.name == "qux"), s"missing val qux in $decls")
    assert(decls.exists(d => d.kind == "def" && d.name == "shielded" && d.accessLevel == "protected"),
      s"protected member should appear in $decls")
    assert(!decls.exists(d => d.name == "secret"), s"private member should not appear in $decls")
  }

  test("parseSurface excludes PHASE-MINTED names — a `$` in a member name is neither java's nor the hand port's") {
    val src =
      """package foo
        |class Bar:
        |  var index$field: Int = 0
        |  def index: Int = index$field
        |  def x$shadow: Int = 1
        |""".stripMargin
    val decls = ApiParityCheck.parseSurface(List(writeTempScala("Bar.scala", src))).toOption.get
    assert(decls.exists(d => d.kind == "def" && d.name == "index"), s"the accessor is surface: $decls")
    assert(!decls.exists(_.name.contains('$')), s"a minted name is an instrument artefact, not surface: $decls")
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

  // ---- type information extraction ----

  test("parseSurface extracts param types and result type") {
    val src =
      """class Foo:
        |  def bar(x: Int, y: String): Boolean = ???
        |""".stripMargin

    val result = ApiParityCheck.parseSurface(List(writeTempScala("Foo.scala", src)))
    assert(result.isRight)
    val bar = result.toOption.get.find(d => d.name == "bar").get
    assertEquals(bar.paramTypes, List("Int", "String"))
    assertEquals(bar.resultType, "Boolean")
  }

  test("parseSurface extracts type params, canonicalised by position") {
    val src =
      """class Container[T]:
        |  def get[U <: T](key: String): U = ???
        |""".stripMargin

    val result = ApiParityCheck.parseSurface(List(writeTempScala("Container.scala", src)))
    assert(result.isRight)
    val decls = result.toOption.get
    val container = decls.find(d => d.name == "Container").get
    assertEquals(container.typeParams, "[$0]")
    val get = decls.find(d => d.name == "get").get
    assertEquals(get.typeParams, "[$1 <: $0]")
    assertEquals(get.resultType, "$1")
  }

  test("parseSurface extracts parents") {
    val src =
      """trait Base
        |trait Mixin
        |class Foo extends Base with Mixin
        |""".stripMargin

    val result = ApiParityCheck.parseSurface(List(writeTempScala("Foo.scala", src)))
    assert(result.isRight)
    val foo = result.toOption.get.find(d => d.name == "Foo").get
    assertEquals(foo.parents, List("Base", "Mixin"))
  }

  test("parseSurface extracts modifiers") {
    val src =
      """abstract class Foo:
        |  final def bar(): Int = 1
        |  override def toString: String = "foo"
        |  lazy val x: Int = 1
        |""".stripMargin

    val result = ApiParityCheck.parseSurface(List(writeTempScala("Foo.scala", src)))
    assert(result.isRight)
    val decls = result.toOption.get
    val foo = decls.find(d => d.name == "Foo").get
    assert(foo.modifiers.contains("abstract"), s"expected abstract, got ${foo.modifiers}")
    val bar = decls.find(d => d.name == "bar").get
    assert(bar.modifiers.contains("final"), s"expected final, got ${bar.modifiers}")
    val ts = decls.find(d => d.name == "toString").get
    assert(ts.modifiers.contains("override"), s"expected override, got ${ts.modifiers}")
    val x = decls.find(d => d.name == "x").get
    assert(x.modifiers.contains("lazy"), s"expected lazy, got ${x.modifiers}")
  }

  test("parseSurface extracts access level, includes protected") {
    val src =
      """class Foo:
        |  protected def bar(): Int = 1
        |  def baz(): Int = 2
        |  private def secret(): Int = 3
        |""".stripMargin

    val result = ApiParityCheck.parseSurface(List(writeTempScala("Foo.scala", src)))
    assert(result.isRight)
    val decls = result.toOption.get
    // protected members ARE included in the surface (they are part of the API for subclasses)
    val bar = decls.find(d => d.name == "bar")
    assert(bar.isDefined, s"protected member should appear in surface")
    assertEquals(bar.get.accessLevel, "protected")
    val baz = decls.find(d => d.name == "baz").get
    assertEquals(baz.accessLevel, "public")
    // private members are still excluded
    assert(decls.find(d => d.name == "secret").isEmpty, s"private member should not appear")
  }

  test("parseSurface extracts @targetName") {
    val src =
      """import scala.annotation.targetName
        |class Foo:
        |  @targetName("add")
        |  def +(other: Foo): Foo = ???
        |""".stripMargin

    val result = ApiParityCheck.parseSurface(List(writeTempScala("Foo.scala", src)))
    assert(result.isRight)
    val plus = result.toOption.get.find(d => d.name == "+").get
    assertEquals(plus.targetName, "add")
  }

  test("parseSurface extracts val result type") {
    val src =
      """class Foo:
        |  val x: Int = 1
        |  var y: String = "hi"
        |""".stripMargin

    val result = ApiParityCheck.parseSurface(List(writeTempScala("Foo.scala", src)))
    assert(result.isRight)
    val decls = result.toOption.get
    val x = decls.find(d => d.name == "x").get
    assertEquals(x.resultType, "Int")
    val y = decls.find(d => d.name == "y").get
    assertEquals(y.resultType, "String")
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

  // ---- signature family: type-level divergences ----

  test("signature family: same name+arity but different param types") {
    val emitted = List(
      ApiParityCheck.SurfaceDecl("/Foo", "def", "process", 1, paramTypes = List("Int")),
    )
    val reference = List(
      ApiParityCheck.SurfaceDecl("/Foo", "def", "process", 1, paramTypes = List("Long")),
    )
    val divs = ApiParityCheck.compare(emitted, reference, Map.empty)
    assert(divs.exists(_.family == "signature"),
      s"expected signature family, got ${divs.map(d => (d.family, d.detail))}")
  }

  test("signature family: same name+arity but different result type") {
    val emitted = List(
      ApiParityCheck.SurfaceDecl("/Foo", "def", "get", 0, resultType = "String"),
    )
    val reference = List(
      ApiParityCheck.SurfaceDecl("/Foo", "def", "get", 0, resultType = "Option[String]"),
    )
    val divs = ApiParityCheck.compare(emitted, reference, Map.empty)
    assert(divs.exists(d => d.family == "signature" || d.family == "null-model"),
      s"expected signature or null-model, got ${divs.map(d => (d.family, d.detail))}")
  }

  // ---- null-model family ----

  test("null-model family: T | Null vs bare T") {
    val emitted = List(
      ApiParityCheck.SurfaceDecl("/Foo", "def", "find", 1, resultType = "Entity | Null"),
    )
    val reference = List(
      ApiParityCheck.SurfaceDecl("/Foo", "def", "find", 1, resultType = "Entity"),
    )
    val divs = ApiParityCheck.compare(emitted, reference, Map.empty)
    assert(divs.exists(_.family == "null-model"),
      s"expected null-model family, got ${divs.map(d => (d.family, d.detail))}")
  }

  test("null-model family: Nullable[T] vs bare T") {
    val emitted = List(
      ApiParityCheck.SurfaceDecl("/Foo", "def", "find", 1, resultType = "Entity"),
    )
    val reference = List(
      ApiParityCheck.SurfaceDecl("/Foo", "def", "find", 1, resultType = "Nullable[Entity]"),
    )
    val divs = ApiParityCheck.compare(emitted, reference, Map.empty)
    assert(divs.exists(_.family == "null-model"),
      s"expected null-model family, got ${divs.map(d => (d.family, d.detail))}")
  }

  test("null-model family: Option[T] vs bare T") {
    val emitted = List(
      ApiParityCheck.SurfaceDecl("/Foo", "def", "find", 1, resultType = "Entity"),
    )
    val reference = List(
      ApiParityCheck.SurfaceDecl("/Foo", "def", "find", 1, resultType = "Option[Entity]"),
    )
    val divs = ApiParityCheck.compare(emitted, reference, Map.empty)
    assert(divs.exists(_.family == "null-model"),
      s"expected null-model family, got ${divs.map(d => (d.family, d.detail))}")
  }

  // ---- collection-retarget family ----

  test("collection-retarget family: java.util.List vs scala collection") {
    val emitted = List(
      ApiParityCheck.SurfaceDecl("/Foo", "def", "items", 0, resultType = "java.util.List[Int]"),
    )
    val reference = List(
      ApiParityCheck.SurfaceDecl("/Foo", "def", "items", 0, resultType = "Buffer[Int]"),
    )
    val divs = ApiParityCheck.compare(emitted, reference, Map.empty)
    assert(divs.exists(_.family == "collection-retarget"),
      s"expected collection-retarget family, got ${divs.map(d => (d.family, d.detail))}")
  }

  test("collection-retarget family: java.util.Map vs scala.collection.mutable.HashMap") {
    val emitted = List(
      ApiParityCheck.SurfaceDecl("/Foo", "val", "data", 0, resultType = "java.util.Map[String, Int]"),
    )
    val reference = List(
      ApiParityCheck.SurfaceDecl("/Foo", "val", "data", 0, resultType = "scala.collection.mutable.HashMap[String, Int]"),
    )
    val divs = ApiParityCheck.compare(emitted, reference, Map.empty)
    assert(divs.exists(_.family == "collection-retarget"),
      s"expected collection-retarget family, got ${divs.map(d => (d.family, d.detail))}")
  }

  // ---- opaque family ----

  test("opaque family: primitive vs non-primitive type") {
    val emitted = List(
      ApiParityCheck.SurfaceDecl("/Foo", "def", "handle", 1, paramTypes = List("Int")),
    )
    val reference = List(
      ApiParityCheck.SurfaceDecl("/Foo", "def", "handle", 1, paramTypes = List("Handle")),
    )
    val divs = ApiParityCheck.compare(emitted, reference, Map.empty)
    assert(divs.exists(_.family == "opaque"),
      s"expected opaque family, got ${divs.map(d => (d.family, d.detail))}")
  }

  // ---- operator family ----

  test("operator family: @targetName differs") {
    val emitted = List(
      ApiParityCheck.SurfaceDecl("/Foo", "def", "add", 1),
    )
    val reference = List(
      ApiParityCheck.SurfaceDecl("/Foo", "def", "add", 1, targetName = "plus"),
    )
    val divs = ApiParityCheck.compare(emitted, reference, Map.empty)
    assert(divs.exists(_.family == "operator"),
      s"expected operator family, got ${divs.map(d => (d.family, d.detail))}")
  }

  test("operator family: emitted `@targetName(\"add\") def +` AGREES with hand-port `@targetName(\"add\") def +`") {
    // Both sides have the same symbolic name AND the same @targetName — full agreement, no divergence.
    val emitted = List(
      ApiParityCheck.SurfaceDecl("/Foo", "def", "+", 1, targetName = "add"),
    )
    val reference = List(
      ApiParityCheck.SurfaceDecl("/Foo", "def", "+", 1, targetName = "add"),
    )
    val divs = ApiParityCheck.compare(emitted, reference, Map.empty)
    assert(divs.isEmpty, s"matching symbolic + @targetName should produce no divergence, got ${divs.map(d => (d.family, d.detail))}")
  }

  test("operator family: emitted `def +` WITHOUT @targetName vs hand-port `@targetName(\"add\") def +` is the SAME MEMBER") {
    // Both sides have `+` as the name — they MATCH by matchKey. The only divergence is the annotation
    // difference, classified as `operator`. The JVM name is what a consumer's class file sees.
    val emitted = List(
      ApiParityCheck.SurfaceDecl("/Foo", "def", "+", 1),
    )
    val reference = List(
      ApiParityCheck.SurfaceDecl("/Foo", "def", "+", 1, targetName = "add"),
    )
    val divs = ApiParityCheck.compare(emitted, reference, Map.empty)
    // they ARE matched (same name `+`, same arity), so neither is "extra" or "missing"
    assert(!divs.exists(d => d.detail.contains("only")),
      s"should not be extra/missing — they match by name: ${divs.map(d => (d.family, d.detail))}")
    // the annotation difference IS reported as `operator`
    assert(divs.exists(_.family == "operator"),
      s"expected operator family for @targetName difference, got ${divs.map(d => (d.family, d.detail))}")
  }

  test("operator family: emitted `def add` vs hand-port `@targetName(\"add\") def +` classified as operator rename") {
    // Unmatched by name: emitted has `add/1`, reference has `+/1`. The reference's @targetName("add")
    // matches the emitted name — so the classification is `operator`, not `port-extra`/`hand-port-extra`.
    val emitted = List(
      ApiParityCheck.SurfaceDecl("/Foo", "def", "add", 1),
    )
    val reference = List(
      ApiParityCheck.SurfaceDecl("/Foo", "def", "+", 1, targetName = "add"),
    )
    val divs = ApiParityCheck.compare(emitted, reference, Map.empty)
    val families = divs.map(_.family).toSet
    assert(families.contains("operator"),
      s"expected operator family for symbolic rename, got ${divs.map(d => (d.family, d.detail))}")
    // both sides are reported — one extra, one missing — but both classified as `operator`
    assert(divs.forall(_.family == "operator"),
      s"all divergences should be operator family: ${divs.map(d => (d.family, d.detail))}")
  }

  // ---- factory family ----

  test("factory family: companion apply in reference, type on both sides") {
    val emitted = List(
      ApiParityCheck.SurfaceDecl("", "class", "Foo", 0),
    )
    val reference = List(
      ApiParityCheck.SurfaceDecl("", "class", "Foo", 0),
      ApiParityCheck.SurfaceDecl("/Foo$", "def", "apply", 1),
    )
    val divs = ApiParityCheck.compare(emitted, reference, Map.empty)
    assert(divs.exists(_.family == "factory"),
      s"expected factory family, got ${divs.map(d => (d.family, d.detail))}")
  }

  test("factory family: companion create in emitted, type on both sides") {
    val emitted = List(
      ApiParityCheck.SurfaceDecl("", "class", "Foo", 0),
      ApiParityCheck.SurfaceDecl("/Foo$", "def", "create", 0),
    )
    val reference = List(
      ApiParityCheck.SurfaceDecl("", "class", "Foo", 0),
    )
    val divs = ApiParityCheck.compare(emitted, reference, Map.empty)
    assert(divs.exists(_.family == "factory"),
      s"expected factory family, got ${divs.map(d => (d.family, d.detail))}")
  }

  // ---- visibility family ----

  test("visibility family: access level differs") {
    val emitted = List(
      ApiParityCheck.SurfaceDecl("/Foo", "def", "bar", 0, accessLevel = "public"),
    )
    val reference = List(
      ApiParityCheck.SurfaceDecl("/Foo", "def", "bar", 0, accessLevel = "protected"),
    )
    val divs = ApiParityCheck.compare(emitted, reference, Map.empty)
    assert(divs.exists(_.family == "visibility"),
      s"expected visibility family, got ${divs.map(d => (d.family, d.detail))}")
  }

  // ---- rename candidates on hand-port-extra/port-extra ----

  test("hand-port-extra with rename candidates") {
    val emitted = List(
      ApiParityCheck.SurfaceDecl("/Foo", "class", "Foo", 0),
      ApiParityCheck.SurfaceDecl("/Foo", "def", "getItems", 0),
    )
    val reference = List(
      ApiParityCheck.SurfaceDecl("/Foo", "class", "Foo", 0),
      ApiParityCheck.SurfaceDecl("/Foo", "def", "entries", 0),
    )
    val divs = ApiParityCheck.compare(emitted, reference, Map.empty)
    // entries is hand-port-extra and getItems is port-extra
    val handExtra = divs.find(d => d.family == "hand-port-extra" && d.reference.exists(_.name == "entries"))
    assert(handExtra.isDefined, s"expected hand-port-extra for entries, got ${divs.map(d => (d.family, d.detail))}")
    assert(handExtra.get.renameCandidates.contains("getItems"),
      s"expected getItems as rename candidate, got '${handExtra.get.renameCandidates}'")
  }

  // ---- type normalization ----

  test("typesMatch: FQN matches simple name") {
    assert(ApiParityCheck.typesMatch("scala.Int", "Int"))
    assert(ApiParityCheck.typesMatch("Int", "scala.Int"))
    assert(ApiParityCheck.typesMatch("java.lang.String", "String"))
  }

  test("typesMatch: identical types match") {
    assert(ApiParityCheck.typesMatch("Int", "Int"))
    assert(ApiParityCheck.typesMatch("List[Int]", "List[Int]"))
  }

  test("typesMatch: different types do not match") {
    assert(!ApiParityCheck.typesMatch("Int", "Long"))
    assert(!ApiParityCheck.typesMatch("List[Int]", "Array[Int]"))
  }

  test("typesMatch: empty types match each other") {
    assert(ApiParityCheck.typesMatch("", ""))
  }

  test("no divergence when types match despite FQN vs simple name") {
    val emitted = List(
      ApiParityCheck.SurfaceDecl("/Foo", "def", "bar", 1,
        paramTypes = List("scala.Int"), resultType = "java.lang.String"),
    )
    val reference = List(
      ApiParityCheck.SurfaceDecl("/Foo", "def", "bar", 1,
        paramTypes = List("Int"), resultType = "String"),
    )
    val divs = ApiParityCheck.compare(emitted, reference, Map.empty)
    assert(!divs.exists(d => d.family == "signature"),
      s"should not have signature divergence for FQN vs simple name: ${divs.map(d => (d.family, d.detail))}")
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
      ApiParityCheck.SurfaceDecl("/Foo", "def", "bar", 1, paramTypes = List("Int"), resultType = "String"),
      ApiParityCheck.SurfaceDecl("/Foo", "val", "baz", 0, resultType = "Int"),
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

  // ---- classification map covers all families ----

  test("every family has a classification string") {
    ApiParityCheck.Families.foreach { f =>
      assert(ApiParityCheck.Classification.contains(f),
        s"missing classification for family '$f'")
    }
  }

  // ---- null-model helpers ----

  test("isNullWrapped detects union null") {
    assert(ApiParityCheck.isNullWrapped("Entity | Null"))
  }

  test("isNullWrapped detects Nullable wrapper") {
    assert(ApiParityCheck.isNullWrapped("Nullable[Entity]"))
  }

  test("isNullWrapped detects Option wrapper") {
    assert(ApiParityCheck.isNullWrapped("Option[Entity]"))
  }

  test("isNullWrapped rejects bare type") {
    assert(!ApiParityCheck.isNullWrapped("Entity"))
    assert(!ApiParityCheck.isNullWrapped("Int"))
  }

  // ---- a hand-port file is a PARTY only if its header names an upstream (CLAUDE.md §1b) ----

  private val partySrc =
    """/* Ported from com.example.Widget.
      | * Original source: Widget.java
      | */
      |class Widget:
      |  def width: Int = 1
      |""".stripMargin

  private val originalSrc =
    """/*
      | * A helper this hand port wrote itself.
      | * Origin: hand-written for this port, no upstream twin.
      | */
      |class Helper:
      |  def assist(x: Int): Int = x
      |  val label: String = "h"
      |""".stripMargin

  private val extensionsOnlySrc =
    """package foo
      |extension (s: String) def shout: String = s
      |""".stripMargin

  private val headerlessSrc =
    """object Bare:
      |  def go(): Unit = ()
      |""".stripMargin

  private def handPortTree(): java.nio.file.Path =
    val dir = java.nio.file.Files.createTempDirectory("api-parity-markers-")
    dir.toFile.deleteOnExit()
    List("Widget.scala" -> partySrc, "Helper.scala" -> originalSrc, "Bare.scala" -> headerlessSrc,
         "StringOps.scala" -> extensionsOnlySrc)
      .foreach { (n, c) =>
        val f = dir.resolve(n)
        java.nio.file.Files.writeString(f, c)
        f.toFile.deleteOnExit()
      }
    dir

  private def emittedTree(): java.nio.file.Path =
    writeTempScala("Widget.scala",
      """class Widget:
        |  def width: Int = 1
        |""".stripMargin)

  test("a file with no upstream marker yields hand-original rows and no extra rows") {
    import balticporter.core.ParityRef
    val ref = ParityRef(roots = List(handPortTree()))
    val findings = ApiParityCheck.check(ref, emittedTree(), Map.empty)
    val original = findings.filter(_.kind == "hand-original")
    // `StringOps` declares no top-level TYPE: listed under its own file name, or its extension
    // methods would leave the comparison with nothing saying so.
    assertEquals(original.map(_.owner).sorted, List("Bare", "Helper", "StringOps"))
    assert(original.forall(_.detail.startsWith("no upstream marker in header")),
      s"unexpected detail: ${original.map(_.detail)}")
    assert(original.exists(_.detail.contains("Origin: hand-written for this port")),
      s"the Origin line should be quoted: ${original.map(_.detail)}")
    assert(!findings.exists(f => f.kind == "hand-port-extra"),
      s"no member of an original file is a divergence: ${findings.map(f => (f.kind, f.detail))}")
    assert(!findings.exists(f => f.kind == "port-extra"),
      s"the emitted twin has nothing extra: ${findings.map(f => (f.kind, f.detail))}")
  }

  test("upstreamMarkers = Nil makes every file a party — the pre-parameter classification") {
    import balticporter.core.ParityRef
    val ref = ParityRef(roots = List(handPortTree()), upstreamMarkers = Nil)
    val findings = ApiParityCheck.check(ref, emittedTree(), Map.empty)
    assert(!findings.exists(_.kind == "hand-original"), "no file is an original when markers is empty")
    val extra = findings.filter(_.kind == "hand-port-extra").map(_.owner).toSet
    assert(extra.contains("#Helper") && extra.contains("#Bare") && extra.contains("#shout"),
      s"every original's declarations are hand-port-extra again: $extra")
  }

  // ---- a LOCAL declaration is not public surface ----

  test("parseSurface skips declarations inside method bodies, blocks and private templates") {
    val src =
      """package foo
        |class Outer:
        |  def run(): Int =
        |    val local = 1
        |    def helper(y: Int): Int = y
        |    class Inner:
        |      val deep: Int = 2
        |    local + helper(1)
        |  private def hidden(): Int =
        |    var cursor1 = 0
        |    cursor1
        |private class Secret:
        |  val a: Int = 1
        |""".stripMargin

    val decls = ApiParityCheck.parseSurface(List(writeTempScala("Outer.scala", src))).toOption.get
    val names = decls.map(_.name).toSet
    assert(names.contains("Outer") && names.contains("run"), s"surface lost its members: $decls")
    List("local", "helper", "Inner", "deep", "hidden", "cursor1", "Secret", "a").foreach { n =>
      assert(!names.contains(n), s"$n is not public surface: $decls")
    }
  }

  test("extension methods and package-object members stay surface") {
    val src =
      """package foo
        |package object bar:
        |  def inPackageObject(x: Int): Int = x
        |extension (s: String) def shout: String = s
        |""".stripMargin
    val decls = ApiParityCheck.parseSurface(List(writeTempScala("Ext.scala", src))).toOption.get
    val names = decls.map(_.name).toSet
    assert(names.contains("inPackageObject"), s"package-object member lost: $decls")
    assert(names.contains("shout"), s"extension method lost: $decls")
  }

  // ---- a type parameter's NAME is not API (alpha-equivalence, CLAUDE.md §3.5) ----

  private def divergences(emittedSrc: String, referenceSrc: String): List[ApiParityCheck.Divergence] =
    val e = ApiParityCheck.parseSurface(List(writeTempScala("C.scala", emittedSrc))).toOption.get
    val r = ApiParityCheck.parseSurface(List(writeTempScala("C.scala", referenceSrc))).toOption.get
    ApiParityCheck.compare(e, r, Map.empty)

  test("a consistent alpha-renaming of a class type parameter is no divergence") {
    val divs = divergences(
      """class C[T]:
        |  def add(x: T): T = x
        |""".stripMargin,
      """class C[A]:
        |  def add(x: A): A = x
        |""".stripMargin)
    assertEquals(divs.map(d => (d.family, d.detail)), Nil)
  }

  test("a BOUND still diverges: one type-params row, not one per member") {
    val divs = divergences(
      """class C[T <: Comparable[T]]:
        |  def add(x: T): T = x
        |""".stripMargin,
      """class C[A]:
        |  def add(x: A): A = x
        |""".stripMargin)
    assertEquals(divs.map(_.family), List("signature"), divs.map(_.detail).toString)
    assert(divs.head.detail.startsWith("type params differ"), divs.head.detail)
  }

  test("ARITY still diverges: a parameter on one side only") {
    val divs = divergences("class C[T]", "class C[A, B]")
    assertEquals(divs.map(_.family), List("signature"), divs.map(_.detail).toString)
    assert(divs.head.detail.startsWith("type params differ"), divs.head.detail)
  }

  test("a member's OWN type parameter is alpha-renamed too, under the owner's") {
    val divs = divergences(
      """class C[T]:
        |  def map[U](f: U): T = ???
        |""".stripMargin,
      """class C[A]:
        |  def map[B](f: B): A = ???
        |""".stripMargin)
    assertEquals(divs.map(d => (d.family, d.detail)), Nil)
  }

  test("`T <: java.lang.Object` and an unbounded `A` are one absent bound") {
    val divs = divergences(
      """class C[T <: java.lang.Object]:
        |  def id(x: T): T = x
        |""".stripMargin,
      """class C[A]:
        |  def id(x: A): A = x
        |""".stripMargin)
    assertEquals(divs.map(d => (d.family, d.detail)), Nil)
  }

  test("a member type parameter standing where the OWNER's stands still diverges") {
    val divs = divergences(
      """class C[K, V]:
        |  def get[T](key: T): V = ???
        |""".stripMargin,
      """class C[K, V]:
        |  def get(key: K): V = ???
        |""".stripMargin)
    assertEquals(divs.map(_.family), List("signature"), divs.map(_.detail).toString)
    assert(divs.head.detail.startsWith("param 0 type differs"), divs.head.detail)
  }

  // ---- a divergence the ENGINE makes by a CATALOG rule is `rule`, not `signature` ----

  test("an `inline val` at a java constant variable is api-parity(rule), citing JS-C08") {
    val divs = divergences(
      """object C:
        |  inline val X = 0
        |""".stripMargin,
      """object C:
        |  val X: Int = 0
        |""".stripMargin)
    assertEquals(divs.map(_.family), List("rule"), divs.map(_.detail).toString)
    assertEquals(divs.head.detail,
      "rule JS-C08: emitted inline val (a java constant variable is inlined, JLS 4.12.4/13.1), " +
        "reference val")
  }

  test("a hand-written `final val` is a SECOND difference — the rule owns `inline` alone") {
    val divs = divergences(
      """object C:
        |  inline val X = 0
        |""".stripMargin,
      """object C:
        |  final val X: Int = 0
        |""".stripMargin)
    // `final` is the SECOND difference: the rule owns `inline` alone, so this stays `signature`.
    assertEquals(divs.map(_.family), List("signature"), divs.map(_.detail).toString)
  }

  test("`inline` at a NON-literal initialiser is not the constant rule — it stays signature") {
    val divs = divergences(
      """object C:
        |  inline val X = compute()
        |""".stripMargin,
      """object C:
        |  val X: Int = compute()
        |""".stripMargin)
    assertEquals(divs.map(_.family), List("signature"), divs.map(_.detail).toString)
  }

  test("`inline` on a DEF is not the constant rule") {
    val divs = divergences(
      """object C:
        |  inline def x: Int = 0
        |""".stripMargin,
      """object C:
        |  def x: Int = 0
        |""".stripMargin)
    assertEquals(divs.map(_.family), List("signature"), divs.map(_.detail).toString)
  }

  test("a NON-constant `static final` — emitted `val`, hand `val` — is no divergence at all") {
    val divs = divergences(
      """object C:
        |  val X: Int = compute()
        |""".stripMargin,
      """object C:
        |  val X: Int = compute()
        |""".stripMargin)
    assertEquals(divs.map(d => (d.family, d.detail)), Nil)
  }

  test("`rule` is a family of its own — never `unclassified`, and it has a §1 classification") {
    assert(ApiParityCheck.Families.contains("rule"))
    assert(ApiParityCheck.Classification.contains("rule"))
    assert(ApiParityCheck.AllLanes.contains("api-parity(rule)"))
    val divs = divergences(
      """object C:
        |  inline val X = 0
        |""".stripMargin,
      """object C:
        |  val X: Int = 0
        |""".stripMargin)
    assertEquals(divs.map(_.report(Map.empty).check), List("api-parity(rule)"))
    assert(!divs.exists(_.family == "unclassified"))
  }

  test("`inline` on the HAND-PORT side is not this rule — the engine made no such spelling") {
    val divs = divergences(
      """object C:
        |  val X: Int = 0
        |""".stripMargin,
      """object C:
        |  inline val X = 0
        |""".stripMargin)
    assertEquals(divs.map(_.family), List("signature"), divs.map(_.detail).toString)
  }

  test("a negated literal is still a constant initialiser") {
    val divs = divergences(
      """object C:
        |  inline val X = -1
        |""".stripMargin,
      """object C:
        |  val X: Int = -1
        |""".stripMargin)
    assertEquals(divs.map(_.family), List("rule"), divs.map(_.detail).toString)
  }

  // ---- helpers ----

  private def writeTempScala(name: String, content: String): java.nio.file.Path =
    val dir = java.nio.file.Files.createTempDirectory("api-parity-test-")
    dir.toFile.deleteOnExit()
    val file = dir.resolve(name)
    java.nio.file.Files.writeString(file, content)
    file.toFile.deleteOnExit()
    dir
