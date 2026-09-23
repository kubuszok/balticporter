package balticporter.frontend.ts

import balticporter.frontend.ts.dedicated.{DefmethodBodyTranslator, DefmethodEntry}

class DefmethodBodyTranslatorSpec extends munit.FunSuite:

  private def node(kind: String, children: RastNode*): RastNode =
    RastNode(kind, 0, (0, 0), children = children.toList)

  private def ident(name: String): RastNode =
    RastNode("Identifier", 0, (0, 0), text = Some(name))

  private def num(n: Double): RastNode =
    RastNode("NumericLiteral", 0, (0, 0), value = Some(RastValue.Num(n)))

  private def ret(expr: RastNode): RastNode =
    node("ReturnStatement", expr)

  private def retVoid: RastNode =
    RastNode("ReturnStatement", 0, (0, 0))

  private def block(stmts: RastNode*): RastNode =
    node("Block", stmts*)

  private def binOp(op: String, left: RastNode, right: RastNode): RastNode =
    RastNode("BinaryExpression", 0, (0, 0), children = List(left, right), operator = Some(op))

  private def thisKw: RastNode =
    RastNode("ThisKeyword", 0, (0, 0))

  private def propAccess(obj: RastNode, prop: String): RastNode =
    node("PropertyAccessExpression", obj, ident(prop))

  private def translate(body: RastNode, apiLookup: Map[String, String] = Map.empty): DefmethodBodyTranslator.TranslationResult =
    val entry = DefmethodEntry("_free_", "test", Nil, body)
    DefmethodBodyTranslator.translateBody(entry, Nil, "    ", apiLookup = apiLookup)

  // ---- return lowering ----

  test("tail return is the expression value, no boundary"):
    val body = block(ret(num(42)))
    val result = translate(body)
    assert(!result.scalaBody.contains("return"), s"tail return should not contain 'return': ${result.scalaBody}")
    assert(!result.scalaBody.contains("boundary"), s"single return should not need boundary: ${result.scalaBody}")
    assert(result.scalaBody.trim == "42", s"unexpected body: ${result.scalaBody}")

  test("early return wraps body in boundary and emits break"):
    val body = block(
      node("IfStatement",
        binOp("EqualsEqualsEqualsToken", ident("x"), num(0)),
        block(ret(num(-1))),
      ),
      ret(num(1))
    )
    val result = translate(body)
    assert(result.scalaBody.contains("scala.util.boundary[Any]"), s"early return needs boundary: ${result.scalaBody}")
    assert(result.scalaBody.contains("scala.util.boundary.break(-1)"), s"non-last return needs break: ${result.scalaBody}")
    assert(!result.scalaBody.contains("return "), s"no raw 'return' keyword: ${result.scalaBody}")
    // The last return should be just the value (tail position)
    assert(result.scalaBody.contains("1\n"), s"tail return should be the value: ${result.scalaBody}")

  test("void early return emits break(null)"):
    val body = block(
      node("IfStatement",
        binOp("EqualsEqualsEqualsToken", ident("x"), num(0)),
        block(retVoid),
      ),
      node("ExpressionStatement", ident("doSomething"))
    )
    val result = translate(body)
    assert(result.scalaBody.contains("scala.util.boundary.break(null)"), s"void early return needs break(null): ${result.scalaBody}")

  test("return inside a lambda does not use the outer boundary"):
    val body = block(
      node("ExpressionStatement",
        node("ArrowFunction",
          RastNode("Parameter", 0, (0, 0), children = List(ident("x"))),
          block(ret(ident("x")))
        )
      ),
      ret(num(1))
    )
    val result = translate(body)
    // The outer body should not have a boundary because the return is inside a lambda
    assert(!result.scalaBody.contains("scala.util.boundary[Any]"), s"lambda return should not trigger outer boundary: ${result.scalaBody}")

  // ---- this. property access ----

  test("this.prop goes through apiLookup"):
    val body = block(ret(propAccess(thisKw, "my_field")))
    val withLookup = translate(body, apiLookup = Map("my_field" -> "myMappedField"))
    assert(withLookup.scalaBody.contains("this.myMappedField"), s"apiLookup should map this.prop: ${withLookup.scalaBody}")

  test("this.prop uses snakeToCamel when no apiLookup entry"):
    val body = block(ret(propAccess(thisKw, "some_name")))
    val result = translate(body)
    assert(result.scalaBody.contains("this.someName"), s"should camelCase: ${result.scalaBody}")

  // ---- delete expression ----

  test("delete obj.prop emits remove"):
    val body = block(
      node("ExpressionStatement",
        node("DeleteExpression",
          propAccess(ident("myMap"), "key")
        )
      ),
      ret(num(0))
    )
    val result = translate(body)
    assert(result.scalaBody.contains("""myMap.remove("key")"""), s"delete should become remove: ${result.scalaBody}")
    assert(!result.scalaBody.contains("delete"), s"no raw delete: ${result.scalaBody}")

  test("delete obj[expr] emits remove"):
    val body = block(
      node("ExpressionStatement",
        node("DeleteExpression",
          node("ElementAccessExpression", ident("myMap"), ident("k"))
        )
      ),
      ret(num(0))
    )
    val result = translate(body)
    assert(result.scalaBody.contains("myMap.remove(k)"), s"delete with element access should become remove: ${result.scalaBody}")

  // ---- empty object literal ----

  test("empty object literal emits qualified mutable Map"):
    val body = block(ret(node("ObjectLiteralExpression")))
    val result = translate(body)
    assert(result.scalaBody.contains("scala.collection.mutable.Map.empty"), s"empty {} should be qualified: ${result.scalaBody}")
    assert(!result.scalaBody.startsWith("    Map.empty"), s"should not start with bare Map.empty: ${result.scalaBody}")

  // ---- array literal ----

  test("array literal emits ArrayBuffer"):
    val body = block(ret(node("ArrayLiteralExpression", num(1), num(2))))
    val result = translate(body)
    assert(result.scalaBody.contains("scala.collection.mutable.ArrayBuffer("), s"array literal should use ArrayBuffer: ${result.scalaBody}")
    assert(!result.scalaBody.contains("Array("), s"should not use bare Array(: ${result.scalaBody}")

  test("empty array literal emits empty ArrayBuffer"):
    val body = block(ret(RastNode("ArrayLiteralExpression", 0, (0, 0))))
    val result = translate(body)
    assert(result.scalaBody.contains("scala.collection.mutable.ArrayBuffer.empty[Any]"), s"empty array should use ArrayBuffer: ${result.scalaBody}")

  // ---- refusal tracking ----

  test("unhandled statement kind is refused with its name"):
    val body = block(
      RastNode("LabeledStatement", 0, (0, 0)),
      ret(num(0))
    )
    val result = translate(body)
    assert(result.refusalReasons.exists(_.contains("LabeledStatement")), s"should refuse with kind name: ${result.refusalReasons}")

  test("unhandled expression kind is refused with its name"):
    val body = block(ret(RastNode("TaggedTemplateExpression", 0, (0, 0))))
    val result = translate(body)
    assert(result.refusalReasons.exists(_.contains("TaggedTemplateExpression")), s"should refuse with kind name: ${result.refusalReasons}")

  test("break and continue are refused"):
    val body = block(
      RastNode("BreakStatement", 0, (0, 0)),
      RastNode("ContinueStatement", 0, (0, 0)),
      ret(num(0))
    )
    val result = translate(body)
    assert(result.refusalReasons.contains("BreakStatement"), s"break should be refused: ${result.refusalReasons}")
    assert(result.refusalReasons.contains("ContinueStatement"), s"continue should be refused: ${result.refusalReasons}")
