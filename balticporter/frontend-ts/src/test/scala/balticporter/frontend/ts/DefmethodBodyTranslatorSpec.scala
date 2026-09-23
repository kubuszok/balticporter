package balticporter.frontend.ts

import balticporter.frontend.ts.dedicated.{ DefmethodBodyTranslator, DefmethodEntry }

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

  private def translate(
    body:        RastNode,
    apiLookup:   Map[String, String] = Map.empty,
    returnType:  Option[String] = None,
    paramTypes:  Map[String, String] = Map.empty,
    calleeIndex: ReferenceSignatures.CalleeIndex = ReferenceSignatures.CalleeIndex.empty,
    memberIndex: ReferenceSignatures.MemberIndex = ReferenceSignatures.MemberIndex.empty,
    ctorSchema:  ReferenceSignatures.ConstructorSchema = ReferenceSignatures.ConstructorSchema.empty
  ): DefmethodBodyTranslator.TranslationResult =
    val entry = DefmethodEntry("_free_", "test", Nil, body)
    DefmethodBodyTranslator.translateBody(
      entry,
      Nil,
      "    ",
      apiLookup = apiLookup,
      returnType = returnType,
      paramTypes = paramTypes,
      calleeIndex = calleeIndex,
      memberIndex = memberIndex,
      ctorSchema = ctorSchema
    )

  // ---- return lowering ----

  test("tail return is the expression value, no boundary"):
    val body   = block(ret(num(42)))
    val result = translate(body)
    assert(!result.scalaBody.contains("return"), s"tail return should not contain 'return': ${result.scalaBody}")
    assert(!result.scalaBody.contains("boundary"), s"single return should not need boundary: ${result.scalaBody}")
    assert(result.scalaBody.trim == "42", s"unexpected body: ${result.scalaBody}")

  test("early return wraps body in named boundary and emits break with using"):
    val body = block(
      node("IfStatement", binOp("EqualsEqualsEqualsToken", ident("x"), num(0)), block(ret(num(-1)))),
      ret(num(1))
    )
    val result = translate(body)
    assert(result.scalaBody.contains("scala.util.boundary {"), s"early return needs boundary: ${result.scalaBody}")
    assert(result.scalaBody.contains("?=>"), s"boundary must be named: ${result.scalaBody}")
    assert(!result.scalaBody.contains("boundary[Any]"), s"no type argument on boundary: ${result.scalaBody}")
    assert(result.scalaBody.contains("(using ret$"), s"break must target the named label: ${result.scalaBody}")
    assert(!result.scalaBody.contains("return "), s"no raw 'return' keyword: ${result.scalaBody}")
    assert(result.scalaBody.contains("1\n"), s"tail return should be the value: ${result.scalaBody}")

  test("void early return emits break(null) with using"):
    val body = block(
      node("IfStatement", binOp("EqualsEqualsEqualsToken", ident("x"), num(0)), block(retVoid)),
      node("ExpressionStatement", ident("doSomething"))
    )
    val result = translate(body)
    assert(
      result.scalaBody.contains("scala.util.boundary.break(null)(using ret$"),
      s"void early return needs break(null)(using label): ${result.scalaBody}"
    )

  test("return inside a lambda does not use the outer boundary"):
    val body = block(
      node(
        "ExpressionStatement",
        node("ArrowFunction", RastNode("Parameter", 0, (0, 0), children = List(ident("x"))), block(ret(ident("x"))))
      ),
      ret(num(1))
    )
    val result = translate(body)
    // The outer body should not have a boundary because the return is inside a lambda
    assert(
      !result.scalaBody.contains("scala.util.boundary {"),
      s"lambda return should not trigger outer boundary: ${result.scalaBody}"
    )

  // ---- this. property access ----

  test("this.prop goes through apiLookup"):
    val body       = block(ret(propAccess(thisKw, "my_field")))
    val withLookup = translate(body, apiLookup = Map("my_field" -> "myMappedField"))
    assert(withLookup.scalaBody.contains("this.myMappedField"), s"apiLookup should map this.prop: ${withLookup.scalaBody}")

  test("this.prop uses snakeToCamel when no apiLookup entry"):
    val body   = block(ret(propAccess(thisKw, "some_name")))
    val result = translate(body)
    assert(result.scalaBody.contains("this.someName"), s"should camelCase: ${result.scalaBody}")

  // ---- delete expression ----

  test("delete obj.prop emits remove"):
    val body = block(
      node("ExpressionStatement", node("DeleteExpression", propAccess(ident("myMap"), "key"))),
      ret(num(0))
    )
    val result = translate(body)
    assert(result.scalaBody.contains("""myMap.remove("key")"""), s"delete should become remove: ${result.scalaBody}")
    assert(!result.scalaBody.contains("delete"), s"no raw delete: ${result.scalaBody}")

  test("delete obj[expr] emits remove"):
    val body = block(
      node("ExpressionStatement", node("DeleteExpression", node("ElementAccessExpression", ident("myMap"), ident("k")))),
      ret(num(0))
    )
    val result = translate(body)
    assert(result.scalaBody.contains("myMap.remove(k)"), s"delete with element access should become remove: ${result.scalaBody}")

  // ---- empty object literal ----

  test("empty object literal emits qualified mutable Map"):
    val body   = block(ret(node("ObjectLiteralExpression")))
    val result = translate(body)
    assert(result.scalaBody.contains("scala.collection.mutable.Map.empty"), s"empty {} should be qualified: ${result.scalaBody}")
    assert(!result.scalaBody.startsWith("    Map.empty"), s"should not start with bare Map.empty: ${result.scalaBody}")

  // ---- array literal ----

  test("array literal emits ArrayBuffer"):
    val body   = block(ret(node("ArrayLiteralExpression", num(1), num(2))))
    val result = translate(body)
    assert(
      result.scalaBody.contains("scala.collection.mutable.ArrayBuffer("),
      s"array literal should use ArrayBuffer: ${result.scalaBody}"
    )
    assert(!result.scalaBody.contains("Array("), s"should not use bare Array(: ${result.scalaBody}")

  test("empty array literal emits empty ArrayBuffer"):
    val body   = block(ret(RastNode("ArrayLiteralExpression", 0, (0, 0))))
    val result = translate(body)
    assert(
      result.scalaBody.contains("scala.collection.mutable.ArrayBuffer.empty[Any]"),
      s"empty array should use ArrayBuffer: ${result.scalaBody}"
    )

  // ---- refusal tracking ----

  test("unhandled statement kind is refused with its name"):
    val body = block(
      RastNode("LabeledStatement", 0, (0, 0)),
      ret(num(0))
    )
    val result = translate(body)
    assert(
      result.refusalReasons.exists(_.contains("LabeledStatement")),
      s"should refuse with kind name: ${result.refusalReasons}"
    )

  test("unhandled expression kind is refused with its name"):
    val body   = block(ret(RastNode("TaggedTemplateExpression", 0, (0, 0))))
    val result = translate(body)
    assert(
      result.refusalReasons.exists(_.contains("TaggedTemplateExpression")),
      s"should refuse with kind name: ${result.refusalReasons}"
    )

  test("labelled break and continue are refused"):
    val labelledBreak    = RastNode("BreakStatement", 0, (0, 0), children = List(ident("outer")))
    val labelledContinue = RastNode("ContinueStatement", 0, (0, 0), children = List(ident("outer")))
    val body             = block(
      labelledBreak,
      labelledContinue,
      ret(num(0))
    )
    val result = translate(body)
    assert(
      result.refusalReasons.exists(_.contains("LabelledBreak")),
      s"labelled break should be refused: ${result.refusalReasons}"
    )
    assert(
      result.refusalReasons.exists(_.contains("LabelledContinue")),
      s"labelled continue should be refused: ${result.refusalReasons}"
    )

  // ---- break inside a loop ----

  test("break inside a while loop wraps the loop in named boundary"):
    val body = block(
      node(
        "WhileStatement",
        ident("cond"),
        block(
          node("IfStatement", ident("done"), block(RastNode("BreakStatement", 0, (0, 0)))),
          node("ExpressionStatement", ident("work"))
        )
      ),
      ret(num(0))
    )
    val result = translate(body)
    assert(result.scalaBody.contains("(brk$"), s"break boundary must be named: ${result.scalaBody}")
    assert(result.scalaBody.contains("(using brk$"), s"break must target named label: ${result.scalaBody}")
    assert(result.isComplete, s"unlabelled break should not refuse: ${result.refusalReasons}")

  test("continue inside a while loop wraps the body in named boundary"):
    val body = block(
      node(
        "WhileStatement",
        ident("cond"),
        block(
          node("IfStatement", ident("skip"), block(RastNode("ContinueStatement", 0, (0, 0)))),
          node("ExpressionStatement", ident("work"))
        )
      ),
      ret(num(0))
    )
    val result = translate(body)
    assert(result.scalaBody.contains("(cnt$"), s"continue boundary must be named: ${result.scalaBody}")
    assert(result.scalaBody.contains("(using cnt$"), s"continue must target named label: ${result.scalaBody}")
    assert(result.isComplete, s"unlabelled continue should not refuse: ${result.refusalReasons}")

  test("break inside for-of loop wraps the loop in named boundary"):
    val body = block(
      node(
        "ForOfStatement",
        node("VariableDeclarationList", node("VariableDeclaration", ident("item"))),
        ident("items"),
        block(
          node("IfStatement", ident("done"), block(RastNode("BreakStatement", 0, (0, 0)))),
          node("ExpressionStatement", ident("process"))
        )
      ),
      ret(num(0))
    )
    val result = translate(body)
    assert(result.scalaBody.contains("(brk$"), s"break boundary named: ${result.scalaBody}")
    assert(result.scalaBody.contains("(using brk$"), s"break targets named label: ${result.scalaBody}")
    assert(result.isComplete, s"unlabelled break should not refuse: ${result.refusalReasons}")

  // ---- nested boundary compilation specs ----

  /** Wrap the emitted body in a compilable method and check that scalac accepts it. */
  private def assertCompiles(methodBody: String, decl: String = "def f(x: Any, cond: Boolean, done: Boolean, skip: Boolean): Any ="): Unit =
    val source = s"object Test {\n  $decl\n$methodBody}\n"
    val result = scala.util.Try {
      val dir  = java.nio.file.Files.createTempDirectory("bp-spec")
      val file = dir.resolve("Test.scala")
      java.nio.file.Files.writeString(file, source)
      val proc = new ProcessBuilder("scalac", "-d", dir.toString, file.toString).redirectErrorStream(true).start()
      val out  = new String(proc.getInputStream.readAllBytes)
      val exit = proc.waitFor()
      (exit, out)
    }
    result match
      case scala.util.Success((0, _))                                             => ()
      case scala.util.Success((code, out))                                        => fail(s"scalac exit $code on:\n$source\n$out")
      case scala.util.Failure(ex) if ex.getMessage.contains("Cannot run program") =>
        // scalac not on PATH; skip compilation check silently
        ()
      case scala.util.Failure(ex) => fail(s"compilation check failed: $ex")

  test("early return inside a loop with a break compiles with Int return type"):
    val body = block(
      node(
        "WhileStatement",
        ident("cond"),
        block(
          node("IfStatement", ident("done"), block(ret(ident("x")))),
          node("IfStatement", ident("done"), block(RastNode("BreakStatement", 0, (0, 0))))
        )
      ),
      ret(num(0))
    )
    val result = translate(body, returnType = Some("Int"))
    assert(result.scalaBody.contains("Label[Int]"), s"return Label must carry the declared type: ${result.scalaBody}")
    assert(result.scalaBody.contains("(brk$"), s"break boundary named: ${result.scalaBody}")
    assert(result.scalaBody.contains("(using ret$"), s"return targets ret label: ${result.scalaBody}")
    assert(result.scalaBody.contains("(using brk$"), s"break targets brk label: ${result.scalaBody}")
    assertCompiles(result.scalaBody, "def f(x: Int, cond: Boolean, done: Boolean, skip: Boolean): Int =")

  test("continue inside a loop with a break compiles with Int return type"):
    val body = block(
      node(
        "WhileStatement",
        ident("cond"),
        block(
          node("IfStatement", ident("skip"), block(RastNode("ContinueStatement", 0, (0, 0)))),
          node("IfStatement", ident("done"), block(RastNode("BreakStatement", 0, (0, 0)))),
          node("ExpressionStatement", ident("x"))
        )
      ),
      ret(num(0))
    )
    val result = translate(body, returnType = Some("Int"))
    assert(result.scalaBody.contains("(brk$"), s"break boundary named: ${result.scalaBody}")
    assert(result.scalaBody.contains("(cnt$"), s"continue boundary named: ${result.scalaBody}")
    assert(result.scalaBody.contains("(using brk$"), s"break targets brk: ${result.scalaBody}")
    assert(result.scalaBody.contains("(using cnt$"), s"continue targets cnt: ${result.scalaBody}")
    assertCompiles(result.scalaBody, "def f(x: Int, cond: Boolean, done: Boolean, skip: Boolean): Int =")

  test("return inside nested loops compiles with Int return type"):
    val body = block(
      node("WhileStatement", ident("cond"), block(node("WhileStatement", ident("done"), block(ret(ident("x")))))),
      ret(num(0))
    )
    val result = translate(body, returnType = Some("Int"))
    assert(result.scalaBody.contains("Label[Int]"), s"return Label type is Int: ${result.scalaBody}")
    assert(result.scalaBody.contains("(using ret$"), s"inner return targets outer label: ${result.scalaBody}")
    assertCompiles(result.scalaBody, "def f(x: Int, cond: Boolean, done: Boolean, skip: Boolean): Int =")

  test("early return in Unit-typed member compiles"):
    val body = block(
      node("IfStatement", ident("done"), block(retVoid)),
      node("ExpressionStatement", ident("x"))
    )
    val result = translate(body, returnType = Some("Unit"))
    assert(result.scalaBody.contains("Label[Unit]"), s"return Label type is Unit: ${result.scalaBody}")
    assertCompiles(result.scalaBody, "def f(x: Int, cond: Boolean, done: Boolean, skip: Boolean): Unit =")

  // ---- empty-using fix: returns in conditional branches of the last statement ----

  test("single if-else where both branches return does not emit empty label"):
    val body = block(
      node("IfStatement", ident("cond"), block(ret(num(1))), block(ret(num(2))))
    )
    val result = translate(body, returnType = Some("Int"))
    assert(!result.scalaBody.contains("(using )"), s"label must not be empty: ${result.scalaBody}")
    assert(!result.scalaBody.contains("boundary"), s"both-branch-return if-else needs no boundary: ${result.scalaBody}")
    assert(result.isComplete, s"should not refuse: ${result.refusalReasons}")

  test("if-else where then-branch returns early inside multi-statement block creates boundary"):
    val body = block(
      node(
        "IfStatement",
        ident("cond"),
        block(ret(num(1)), node("ExpressionStatement", ident("x"))),
        block(ret(num(2)))
      )
    )
    val result = translate(body, returnType = Some("Int"))
    assert(!result.scalaBody.contains("(using )"), s"label must not be empty: ${result.scalaBody}")
    assert(result.scalaBody.contains("boundary"), s"non-tail return in then-branch needs boundary: ${result.scalaBody}")
    assert(result.scalaBody.contains("(using ret$"), s"break targets the boundary label: ${result.scalaBody}")
    assertCompiles(result.scalaBody, "def f(x: Int, cond: Boolean, done: Boolean, skip: Boolean): Int =")

  test("single if-else with returns compiles with String return type"):
    val body = block(
      node(
        "IfStatement",
        ident("cond"),
        block(ret(RastNode("StringLiteral", 0, (0, 0), value = Some(RastValue.Str("yes"))))),
        block(ret(RastNode("StringLiteral", 0, (0, 0), value = Some(RastValue.Str("no")))))
      )
    )
    val result = translate(body, returnType = Some("String"))
    assert(!result.scalaBody.contains("(using )"), s"label must not be empty: ${result.scalaBody}")
    assertCompiles(result.scalaBody, "def f(x: String, cond: Boolean, done: Boolean, skip: Boolean): String =")

  // ---- truthiness lowering ----

  private def prefixUnary(op: String, operand: RastNode): RastNode =
    RastNode("PrefixUnaryExpression", 0, (0, 0), children = List(operand), operator = Some(op))

  test("negation of Nullable lowers to isEmpty check"):
    val body = block(
      node("IfStatement", prefixUnary("ExclamationToken", ident("second")), block(ret(num(0)))),
      ret(num(1))
    )
    val result = translate(body, paramTypes = Map("second" -> "Nullable[HasLoc]"))
    assert(result.scalaBody.contains(".isDefined"), s"!Nullable should use isDefined: ${result.scalaBody}")
    assert(result.isComplete, s"should not refuse: ${result.refusalReasons}")

  test("logical-or on Nullable lowers to if-isDefined-get-else"):
    val body = block(
      ret(binOp("BarBarToken", ident("first"), ident("fallback")))
    )
    val result = translate(body, paramTypes = Map("first" -> "Nullable[String]"))
    assert(result.scalaBody.contains("isDefined"), s"|| on Nullable should check isDefined: ${result.scalaBody}")
    assert(result.scalaBody.contains(".get"), s"|| on Nullable should use .get: ${result.scalaBody}")
    assert(result.isComplete, s"should not refuse: ${result.refusalReasons}")

  test("logical-and on Nullable lowers to if-isDefined-then-rhs"):
    val body = block(
      ret(binOp("AmpersandAmpersandToken", ident("first"), ident("fallback")))
    )
    val result = translate(body, paramTypes = Map("first" -> "Nullable[HasLoc]"))
    assert(result.scalaBody.contains("isDefined"), s"&& on Nullable should check isDefined: ${result.scalaBody}")
    assert(result.isComplete, s"should not refuse: ${result.refusalReasons}")

  test("ternary on Nullable lowers to isDefined condition"):
    val body = block(
      ret(node("ConditionalExpression", ident("opt"), num(1), num(0)))
    )
    val result = translate(body, paramTypes = Map("opt" -> "Nullable[Int]"))
    assert(result.scalaBody.contains("isDefined"), s"ternary on Nullable should check isDefined: ${result.scalaBody}")
    assert(result.isComplete, s"should not refuse: ${result.refusalReasons}")

  test("negation of Boolean stays as boolean negation"):
    val body = block(
      node("IfStatement", prefixUnary("ExclamationToken", ident("flag")), block(ret(num(0)))),
      ret(num(1))
    )
    val result = translate(body, paramTypes = Map("flag" -> "Boolean"))
    assert(result.scalaBody.contains("!flag"), s"Boolean negation should stay: ${result.scalaBody}")
    assert(result.isComplete, s"should not refuse: ${result.refusalReasons}")

  test("unknown-type operand in truthiness context refuses"):
    val body = block(
      node("IfStatement", prefixUnary("ExclamationToken", ident("mystery")), block(ret(num(0)))),
      ret(num(1))
    )
    val result = translate(body, paramTypes = Map.empty)
    assert(result.refusalReasons.contains("truthiness-unknown-type"), s"unknown type should refuse: ${result.refusalReasons}")

  // ---- js-map-construction refusal ----

  test("object literal in non-Map return type refuses with js-map-construction"):
    val objLit = node(
      "ObjectLiteralExpression",
      node("PropertyAssignment", ident("mode"), RastNode("StringLiteral", 0, (0, 0), value = Some(RastValue.Str("math")))),
      node("PropertyAssignment", ident("style"), RastNode("StringLiteral", 0, (0, 0), value = Some(RastValue.Str("display"))))
    )
    val body   = block(ret(objLit))
    val result = translate(body, returnType = Some("ParseNodeStyling"))
    assert(
      result.refusalReasons.contains("js-map-construction"),
      s"should refuse with js-map-construction: ${result.refusalReasons}"
    )

  test("object literal in Map return type does not refuse"):
    val objLit = node("ObjectLiteralExpression", node("PropertyAssignment", ident("key"), num(1)))
    val body   = block(ret(objLit))
    val result = translate(body, returnType = Some("Map[String, Int]"))
    assert(!result.refusalReasons.contains("js-map-construction"), s"Map return type should not refuse: ${result.refusalReasons}")

  // ---- wrong-member-access ----

  test("property access on a typed parameter with no member index refuses"):
    val body   = block(ret(propAccess(ident("group"), "bodyNodes")))
    val result = translate(body, paramTypes = Map("group" -> "AnyParseNode"))
    assert(
      result.refusalReasons.contains("wrong-member-access"),
      s"unknown member on specific type should refuse: ${result.refusalReasons}"
    )

  test("property access against member index accepts known member"):
    val body   = block(ret(propAccess(ident("group"), "nodeType")))
    val idx    = ReferenceSignatures.MemberIndex(Map("AnyParseNode" -> Set("nodeType", "mode", "loc")))
    val result = translate(body, paramTypes = Map("group" -> "AnyParseNode"), memberIndex = idx)
    assert(!result.refusalReasons.contains("wrong-member-access"), s"known member should not refuse: ${result.refusalReasons}")

  test("property access against member index refuses unknown member"):
    val body   = block(ret(propAccess(ident("group"), "children")))
    val idx    = ReferenceSignatures.MemberIndex(Map("AnyParseNode" -> Set("nodeType", "mode", "loc")))
    val result = translate(body, paramTypes = Map("group" -> "AnyParseNode"), memberIndex = idx)
    assert(result.refusalReasons.contains("wrong-member-access"), s"unknown member should refuse: ${result.refusalReasons}")

  test("property access on untyped parameter does not refuse"):
    val body   = block(ret(propAccess(ident("group"), "bodyNodes")))
    val result = translate(body)
    assert(!result.refusalReasons.contains("wrong-member-access"), s"untyped should not refuse: ${result.refusalReasons}")

  test("safe property access on a typed parameter does not refuse"):
    val body   = block(ret(propAccess(ident("arr"), "length")))
    val result = translate(body, paramTypes = Map("arr" -> "ArrayBuffer[Int]"))
    assert(!result.refusalReasons.contains("wrong-member-access"), s"safe property should not refuse: ${result.refusalReasons}")

  // ---- wrong-function-ref ----

  test("unknown function call refuses with wrong-function-ref"):
    val body   = block(ret(node("CallExpression", ident("unknownHelper"), num(1))))
    val result = translate(body)
    assert(
      result.refusalReasons.contains("wrong-function-ref"),
      s"should refuse with wrong-function-ref: ${result.refusalReasons}"
    )

  test("callee index resolves a function to its enclosing object"):
    val body   = block(ret(node("CallExpression", ident("makeSpan"), num(1))))
    val idx    = ReferenceSignatures.CalleeIndex(Map("makeSpan" -> List("BuildCommon")))
    val result = translate(body, calleeIndex = idx)
    assert(result.scalaBody.contains("BuildCommon.makeSpan("), s"should qualify callee: ${result.scalaBody}")
    assert(result.isComplete, s"should not refuse: ${result.refusalReasons}")

  test("ambiguous callee refuses with callee-ambiguous"):
    val body   = block(ret(node("CallExpression", ident("helper"), num(1))))
    val idx    = ReferenceSignatures.CalleeIndex(Map("helper" -> List("ModuleA", "ModuleB")))
    val result = translate(body, calleeIndex = idx)
    assert(result.refusalReasons.contains("callee-ambiguous"), s"ambiguous callee should refuse: ${result.refusalReasons}")

  test("function call via apiLookup does not refuse"):
    val body   = block(ret(node("CallExpression", ident("makeSpan"), num(1))))
    val result = translate(body, apiLookup = Map("makeSpan" -> "BuildCommon.makeSpan"))
    assert(!result.refusalReasons.contains("wrong-function-ref"), s"apiLookup call should not refuse: ${result.refusalReasons}")

  test("function call of a parameter does not refuse"):
    val entry  = DefmethodEntry("_free_", "test", List("callback"), block(ret(node("CallExpression", ident("callback"), num(1)))))
    val result = DefmethodBodyTranslator.translateBody(entry, Nil, "    ")
    assert(!result.refusalReasons.contains("wrong-function-ref"), s"parameter call should not refuse: ${result.refusalReasons}")
