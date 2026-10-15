package balticporter.frontend.ts

import balticporter.frontend.ts.dedicated.{ DefmethodBodyTranslator, DefmethodEntry, JsConventions }

/** JS array locals and callbacks: a local the annotation types is a buffer of a type the target file can write, a callback's parameter is a local of the element type, and the buffer handed to an
  * immutable slot is copied there only where nothing could observe the copy.
  */
class DefmethodBodyArraysSpec extends munit.FunSuite:

  private def node(kind:   String, children: RastNode*): RastNode = RastNode(kind, 0, (0, 0), children = children.toList)
  private def ident(name:  String):                      RastNode = RastNode("Identifier", 0, (0, 0), text = Some(name))
  private def num(n:       Double):                      RastNode = RastNode("NumericLiteral", 0, (0, 0), value = Some(RastValue.Num(n)))
  private def ret(e:       RastNode):                    RastNode = node("ReturnStatement", e)
  private def block(stmts: RastNode*):                   RastNode = node("Block", stmts*)
  private def expr(e:      RastNode):                    RastNode = node("ExpressionStatement", e)
  private def prop(obj:    RastNode, p:      String):    RastNode = node("PropertyAccessExpression", obj, ident(p))
  private def call(callee: RastNode, args:   RastNode*): RastNode = node("CallExpression", (callee :: args.toList)*)
  private def arr(elems:   RastNode*):                   RastNode = node("ArrayLiteralExpression", elems*)
  private def typeRef(n:   String):                      RastNode = node("TypeReference", ident(n))
  private def local(name: String, rest: RastNode*):      RastNode =
    node("VariableStatement", node("VariableDeclarationList", node("VariableDeclaration", (ident(name) :: rest.toList)*)))
  private def param(n:      String):                       RastNode = node("Parameter", ident(n))
  private def arrow(params: List[String], body: RastNode): RastNode = node("ArrowFunction", (params.map(param) :+ body)*)
  private def spread(e:     RastNode):                     RastNode = node("SpreadElement", e)

  // `const points: Point[] = []`
  private def pointsLocal: RastNode = local("points", node("ArrayType", typeRef("Point")), arr())

  private val conventions = JsConventions(tupleRecords = Map("Point" -> List("x", "y"), "Line" -> List("p1", "p2")))
  private val memberIndex = ReferenceSignatures.MemberIndex(
    Map("Point" -> Set("x", "y"), "Line" -> Set("p1", "p2")),
    types = Map("Point" -> Map("x" -> "Double", "y" -> "Double"), "Line" -> Map("p1" -> "Point", "p2" -> "Point"))
  )
  private val ctorSchema = ReferenceSignatures.ConstructorSchema(
    Map(
      "Point" -> List(ReferenceSignatures.CtorParam("x", "Double", false), ReferenceSignatures.CtorParam("y", "Double", false)),
      "Line" -> List(ReferenceSignatures.CtorParam("p1", "Point", false), ReferenceSignatures.CtorParam("p2", "Point", false))
    )
  )
  private val callees = ReferenceSignatures.CalleeIndex(
    List("rotate", "use", "line").map(m => ReferenceSignatures.MemberRef(if m == "line" then "Elsewhere" else "Fill", m, "Fill", isPrivate = false)),
    Set("Fill", "Elsewhere")
  )
  private val oracle = ReferenceSignatures.TypeOracle.fromEntries(
    List(
      "Fill" -> ReferenceSignatures.MethodSig(
        "rotate",
        List(ReferenceSignatures.ParamSig("points", "Vector[Point]"), ReferenceSignatures.ParamSig("c", "Point")),
        "Unit"
      ),
      "Fill" -> ReferenceSignatures.MethodSig("use", List(ReferenceSignatures.ParamSig("p", "Point")), "Unit")
    )
  )

  private def translate(
    body:          RastNode,
    params:        List[String] = Nil,
    paramTypes:    Map[String, String] = Map.empty,
    returnType:    Option[String] = None,
    nameableTypes: Set[String] = Set("Point", "Line")
  ): DefmethodBodyTranslator.TranslationResult =
    DefmethodBodyTranslator.translateBody(
      DefmethodEntry("_free_", "test", params, body),
      Nil,
      "    ",
      returnType = returnType,
      paramTypes = paramTypes,
      calleeIndex = callees,
      memberIndex = memberIndex,
      ctorSchema = ctorSchema,
      oracle = oracle,
      conventions = conventions,
      enclosingOwner = Some("Fill"),
      fileNames = ReferenceDeclarations.FileNames(nameableTypes, Map.empty)
    )

  private def refuses(r: DefmethodBodyTranslator.TranslationResult, reason: String): Unit =
    assert(r.refusalReasons.contains(reason), s"expected refusal $reason, got ${r.refusalReasons} for:\n${r.scalaBody}")

  private def accepts(r: DefmethodBodyTranslator.TranslationResult): Unit =
    assert(r.isComplete, s"expected no refusal, got ${r.refusalReasons} for:\n${r.scalaBody}")

  test(
    "an annotated array local is a buffer of its element type, a pushed literal builds the element, and the last use copies it into the immutable result"
  ):
    val r = translate(
      block(pointsLocal, expr(call(prop(ident("points"), "push"), arr(num(1), num(2)))), ret(ident("points"))),
      returnType = Some("Vector[Point]")
    )
    accepts(r)
    assertNoDiff(
      r.scalaBody.linesIterator.map(_.trim).mkString("\n").trim,
      """{
        |val points: scala.collection.mutable.ArrayBuffer[Point] = scala.collection.mutable.ArrayBuffer.empty[Point]
        |points += Point(1, 2)
        |points.toVector
        |}""".stripMargin
    )

  test("an element type the target file does not write leaves the annotated local refused"):
    refuses(
      translate(block(pointsLocal, ret(ident("points"))), returnType = Some("Vector[Point]"), nameableTypes = Set.empty),
      "local-type-annotation:ArrayType"
    )

  test(
    "a callback's parameter is a local of the element type, shadowing a module-level namesake, and a spread tuple record pushes its fields in order"
  ):
    val r = translate(
      block(
        pointsLocal,
        expr(call(prop(ident("lines"), "forEach"), arrow(List("line"), call(prop(ident("points"), "push"), spread(ident("line")))))),
        expr(call(ident("rotate"), ident("points"), ident("c")))
      ),
      params = List("lines", "c"),
      paramTypes = Map("lines" -> "Vector[Line]", "c" -> "Point"),
      returnType = Some("Unit")
    )
    accepts(r)
    assert(r.scalaBody.contains("lines.foreach(((line) => { points += line.p1; points += line.p2 }))"), r.scalaBody)
    assert(r.scalaBody.contains("Fill.rotate(points.toVector, c)"), r.scalaBody)

  test("a lambda parameter is never resolved as a module-level member of the same name"):
    val r = translate(
      block(expr(call(prop(ident("ps"), "forEach"), arrow(List("line"), call(ident("use"), ident("line")))))),
      params = List("ps"),
      paramTypes = Map("ps" -> "Vector[Point]"),
      returnType = Some("Unit")
    )
    accepts(r)
    assert(r.scalaBody.contains("ps.foreach(((line) => Fill.use(line)))"), r.scalaBody)

  test(
    "the copy is refused where the array is read after it, escaped before it, or is handed over inside a loop the local is declared outside of"
  ):
    val after = translate(
      block(pointsLocal, expr(call(ident("rotate"), ident("points"), ident("c"))), ret(prop(ident("points"), "length"))),
      params = List("c"),
      paramTypes = Map("c" -> "Point"),
      returnType = Some("Int")
    )
    refuses(after, "array-hand-over-not-last-use")
    val escaped = translate(
      block(pointsLocal, local("alias", ident("points")), ret(ident("points"))),
      returnType = Some("Vector[Point]")
    )
    refuses(escaped, "array-hand-over-not-last-use")
    val inLoop = translate(
      block(pointsLocal, node("WhileStatement", ident("go"), block(expr(call(ident("rotate"), ident("points"), ident("c")))))),
      params = List("go", "c"),
      paramTypes = Map("go" -> "Boolean", "c" -> "Point"),
      returnType = Some("Unit")
    )
    refuses(inLoop, "array-hand-over-not-last-use")

  test("a callback that can run off its end answers undefined, which only forEach discards"):
    val body = block(expr(call(ident("use"), ident("p"))))
    refuses(
      translate(
        block(ret(call(prop(ident("ps"), "map"), node("ArrowFunction", param("p"), body)))),
        params = List("ps"),
        paramTypes = Map("ps" -> "Vector[Point]")
      ),
      "function-returns-undefined"
    )
    accepts(
      translate(
        block(expr(call(prop(ident("ps"), "forEach"), node("ArrowFunction", param("p"), body)))),
        params = List("ps"),
        paramTypes = Map("ps" -> "Vector[Point]"),
        returnType = Some("Unit")
      )
    )

  test("JS grows an array stored to past its end, so an unguarded element store is refused"):
    val store = node("BinaryExpression", node("ElementAccessExpression", ident("points"), num(0)), arr(num(1), num(2))).copy(operator = Some("EqualsToken"))
    refuses(translate(block(pointsLocal, expr(store), ret(ident("points"))), returnType = Some("Vector[Point]")), "write-past-end")

  test("every pushed argument is evaluated before any is appended"):
    val r = translate(
      block(pointsLocal, expr(call(prop(ident("points"), "push"), ident("p"), ident("q"))), ret(ident("points"))),
      params = List("p", "q"),
      paramTypes = Map("p" -> "Point", "q" -> "Point"),
      returnType = Some("Vector[Point]")
    )
    accepts(r)
    assert(r.scalaBody.contains("{ val a$1 = p; val a$2 = q; points += a$1; points += a$2 }"), r.scalaBody)

  test("an array the reference holds as an immutable collection is never changed in place"):
    val r = translate(
      block(expr(call(prop(ident("ps"), "push"), ident("p")))),
      params = List("ps", "p"),
      paramTypes = Map("ps" -> "Vector[Point]", "p" -> "Point"),
      returnType = Some("Unit")
    )
    refuses(r, "mutated-immutable-collection")

  test("an empty array local the body returns is a buffer of the declared result's elements"):
    val r = translate(
      block(local("a", arr()), expr(call(prop(ident("a"), "push"), ident("p"))), ret(ident("a"))),
      params = List("p"),
      paramTypes = Map("p" -> "Point"),
      returnType = Some("ArrayBuffer[Point]")
    )
    accepts(r)
    assert(
      r.scalaBody.contains("val a: scala.collection.mutable.ArrayBuffer[Point] = scala.collection.mutable.ArrayBuffer.empty[Point]"),
      r.scalaBody
    )

  test("a number tested for truthiness is compared whole, whatever operator it holds"):
    val and = node("BinaryExpression", ident("a"), ident("b")).copy(operator = Some("AmpersandToken"))
    val r   = translate(
      block(ret(node("ConditionalExpression", and, num(1), num(2)))),
      params = List("a", "b"),
      paramTypes = Map("a" -> "Int", "b" -> "Int"),
      returnType = Some("Int")
    )
    assert(r.scalaBody.contains("(a & b) != 0"), r.scalaBody)

  test("push answers the new length, which no Scala append does"):
    refuses(
      translate(block(pointsLocal, ret(call(prop(ident("points"), "push"), arr(num(1), num(2))))), returnType = Some("Int")),
      "push-as-value"
    )
