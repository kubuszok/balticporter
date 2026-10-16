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

  test("a comment after a parameter of a multi-line signature or constructor is no part of the next parameter"):
    val src =
      """object Tok:
        |  def range(
        |    endToken: Token, // last token of the range, inclusive
        |    text:     String // the text of "the // new" token
        |  ): Token =
        |    ???
        |final case class Span(
        |  start: Int, // first, inclusive
        |  end:   Int
        |)
        |""".stripMargin
    val sig = ReferenceSignatures.parseFile("Tok", src).map(_._2).find(_.methodName == "range").get
    assertEquals(sig.params.map(p => (p.name, p.tpe)), List(("endToken", "Token"), ("text", "String")))
    val (_, _, ctors, _) = ReferenceSignatures.buildIndices(List("Tok" -> src))
    assertEquals(ctors.get("Span").map(_.map(_.name)), Some(List("start", "end")))

  test("a file sees bare the top-level types of its own package, of a package it imports whole, and each name it imports"):
    val leaf  = "package p.q\nfinal case class Leaf(v: Int)\nobject Leaf:\n  final class Inner\n"
    val user  = "package r\npackage s\nimport p.q.*\nimport t.{ One, Two => Deux }\nobject Use:\n  def f(x: Int): Int = x\n"
    val names = ReferenceDeclarations.namesIn("Use", user)
    assertEquals(names.scopes, Set("r.s", "p.q"))
    assert(names.types("One") && !names.types("Two") && !names.types("Deux"), names.types.toString)
    val (_, mi, _, _) = ReferenceSignatures.buildIndices(List("Leaf" -> leaf, "Use" -> user))
    assertEquals(mi.packages.get("Leaf"), Some(Set("p.q")))
    assertEquals(mi.packages.get("Inner"), None)

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
    val (ci, _, _, _) = ReferenceSignatures.buildIndices(List(("BuildCommon", refSource1), ("BuildHTML", refSource2)))
    assertEquals(ci.resolve("makeSpan"), Right("BuildCommon.makeSpan"))
    assertEquals(ci.resolve("buildGroup"), Right("BuildHTML.buildGroup"))
    assert(ci.resolve("noSuchFn").isLeft)

  test("buildIndices: constructor schema parses case class parameters"):
    val (_, _, cs, _) = ReferenceSignatures.buildIndices(List(("NodeStyling", refSource3)))
    val params        = cs.get("NodeStyling")
    assert(params.isDefined, "should find NodeStyling constructor")
    assertEquals(params.get.map(_.name), List("mode", "loc", "style", "body"))
    assert(params.get.find(_.name == "loc").exists(_.hasDefault), "loc should have a default")
    assert(!params.get.find(_.name == "mode").exists(_.hasDefault), "mode should not have a default")

  test("buildIndices: member index includes constructor params"):
    val (_, mi, _, _) = ReferenceSignatures.buildIndices(List(("NodeStyling", refSource3)))
    assert(mi.knowsType("NodeStyling"), "should know NodeStyling")
    assert(mi.hasMember("NodeStyling", "mode"), "should have mode")
    assert(mi.hasMember("NodeStyling", "body"), "should have body")
    assert(!mi.hasMember("NodeStyling", "children"), "should not have children")

  private val refSource4 =
    """enum Mode(val value: String) extends java.lang.Enum[Mode] {
      |  case Math extends Mode("math")
      |  case Text extends Mode("text")
      |}
      |""".stripMargin

  private val refSource5 =
    """object Style:
      |  val styles: Array[Style] = Array(DISPLAY, TEXT, SCRIPT, SCRIPTSCRIPT)
      |  lazy val lookup: Map[String, Style] = Map.empty
      |  def fromId(id: Int): Style = styles(id)
      |""".stripMargin

  test("buildIndices: callee index includes module-level val and lazy val"):
    val (ci, _, _, _) = ReferenceSignatures.buildIndices(List(("Style", refSource5)))
    assertEquals(ci.resolve("styles"), Right("Style.styles"))
    assertEquals(ci.resolve("lookup"), Right("Style.lookup"))
    assertEquals(ci.resolve("fromId"), Right("Style.fromId"))

  test("buildIndices: enum index maps string values to member names"):
    val (_, mi, _, ei) = ReferenceSignatures.buildIndices(List(("Mode", refSource4)))
    assertEquals(ei.resolve("Mode", "math"), Some("Math"))
    assertEquals(ei.resolve("Mode", "text"), Some("Text"))
    assertEquals(ei.resolve("Mode", "other"), None)
    assert(mi.knowsType("Mode"), "should know Mode")
    assert(mi.hasMember("Mode", "Math"), "should have Math member")
    assert(mi.hasMember("Mode", "Text"), "should have Text member")

  // ---- declaring owners ----

  private val twoObjects =
    """package demo
      |
      |object A {
      |  def a(): Int = 1
      |}
      |
      |object B {
      |  private var f: Int = 0
      |  var g: Int = 0
      |
      |  object Inner {
      |    def h(): Int = 2
      |  }
      |}
      |
      |class C {
      |  def m(): Int = 3
      |}
      |""".stripMargin

  test("buildIndices: a member is filed under the object that declares it, not under the file's name"):
    val (ci, _, _, _) = ReferenceSignatures.buildIndices(List(("File", twoObjects)))
    assertEquals(ci.resolve("a"), Right("A.a"))
    assertEquals(ci.resolve("g"), Right("B.g"))
    assertEquals(ci.resolveMember("f").map(r => (r.owner, r.member, r.file, r.isPrivate)), Right(("B", "f", "File", true)))
    assertEquals(ci.declared("File", "f"), None)
    assertEquals(ci.knownObjects, Set("A", "B", "B.Inner"))

  test("buildIndices: a nested object's member is qualified by the whole object path"):
    val (ci, _, _, _) = ReferenceSignatures.buildIndices(List(("File", twoObjects)))
    assertEquals(ci.resolveMember("h").map(r => (r.owner, r.member)), Right(("B.Inner", "h")))

  test("buildIndices: a class member is not reachable through the class name and is not indexed"):
    val (ci, _, _, _) = ReferenceSignatures.buildIndices(List(("File", twoObjects)))
    assertEquals(ci.resolve("m"), Left("callee-not-found"))

  test("buildIndices: a file the parser rejects is recorded, not guessed around"):
    val (ci, _, _, _) = ReferenceSignatures.buildIndices(List(("Bad", "object Bad {\n  def x( = \n")))
    assertEquals(ci.unparsed.size, 1)
    assertEquals(ci.resolve("x"), Left("callee-not-found"))

  test("a private member is accessible only from its owner or a template nested in it"):
    val ref = ReferenceSignatures.MemberRef("B", "f", "File", isPrivate = true)
    assert(ref.accessibleFrom(Some("B")))
    assert(ref.accessibleFrom(Some("B.Inner")))
    assert(!ref.accessibleFrom(Some("A")))
    assert(!ref.accessibleFrom(Some("Bee")))
    assert(!ref.accessibleFrom(None))

  private val hierarchySource =
    "package demo\ntrait Node {\n  def kind: String\n  def done(): Boolean\n}\ntrait Body extends Node {\n  var body: Int = 0\n}\n" +
      "class Leaf extends Body {\n  override def kind: String = \"leaf\"\n  def done(): Boolean = true\n}\nclass Twig extends Node {\n  override def kind: String = \"twig\"\n  def done(): Boolean = false\n}\n"

  test("buildIndices: a class reads the members its parents declare, nearest first, and is known though it declares nothing"):
    val (_, mi, _, _) = ReferenceSignatures.buildIndices(List(("Tree", hierarchySource + "class Bare extends Leaf\n")))
    assertEquals(mi.ancestry("Leaf"), List("Leaf", "Body", "Node"))
    assert(mi.hasMember("Bare", "body") && mi.isMutable("Bare", "body") && mi.knowsType("Bare"))
    assertEquals(mi.memberType("Bare", "body"), Some("Int"))

  test(
    "buildIndices: a string-constant member indexes its classes by value, and a result every declaring class agrees on is the name's"
  ):
    val (_, mi, _, _) = ReferenceSignatures.buildIndices(List(("Tree", hierarchySource)))
    assertEquals(mi.classesDeclaring("kind", "leaf"), List("Leaf"))
    assertEquals(mi.classesDeclaring("kind", "none"), Nil)
    assertEquals(mi.uniformResult("done"), Some("Boolean"))

  test("a type alias is the type it names, and a case class is told from a plain class"):
    val source =
      """package demo
        |
        |trait Node
        |class Span[T](var height: Double)
        |final case class Opts(gap: Double)
        |type DomSpan = Span[Node]
        |type Kind = String
        |type Child = Span[Node] | Opts
        |""".stripMargin
    val (_, mi, cs, _) = ReferenceSignatures.buildIndices(List(("Tree", source)))
    assert(mi.knowsType("DomSpan"))
    assert(mi.hasMember("DomSpan", "height"))
    assertEquals(mi.memberType("DomSpan", "height"), Some("Double"))
    assertEquals(mi.dealias("Nullable[Kind]"), "Nullable[String]")
    assertEquals(mi.dealias("Child"), "Span[Node] | Opts")
    assertEquals(mi.dealias("Vector[Child]"), "Vector[Child]")
    assert(cs.isCase("Opts"))
    assert(!cs.isCase("Span"))
