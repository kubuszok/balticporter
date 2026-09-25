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
    body:          RastNode,
    apiLookup:     Map[String, String] = Map.empty,
    returnType:    Option[String] = None,
    paramTypes:    Map[String, String] = Map.empty,
    calleeIndex:   ReferenceSignatures.CalleeIndex = ReferenceSignatures.CalleeIndex.empty,
    memberIndex:   ReferenceSignatures.MemberIndex = ReferenceSignatures.MemberIndex.empty,
    ctorSchema:    ReferenceSignatures.ConstructorSchema = ReferenceSignatures.ConstructorSchema.empty,
    enumIndex:     ReferenceSignatures.EnumIndex = ReferenceSignatures.EnumIndex.empty,
    memberRenames: Map[String, String] = Map.empty,
    oracle:        ReferenceSignatures.TypeOracle = ReferenceSignatures.TypeOracle.empty
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
      ctorSchema = ctorSchema,
      enumIndex = enumIndex,
      memberRenames = memberRenames,
      oracle = oracle
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

  test("logical-and on Nullable unwraps x to x.get in the guarded operand"):
    val body = block(
      ret(binOp("AmpersandAmpersandToken", ident("first"), propAccess(ident("first"), "loc")))
    )
    val mi     = ReferenceSignatures.MemberIndex(Map("HasLoc" -> Set("loc")))
    val result = translate(body, paramTypes = Map("first" -> "Nullable[HasLoc]"), memberIndex = mi)
    assert(result.scalaBody.contains("isDefined"), s"&& on Nullable should check isDefined: ${result.scalaBody}")
    assert(result.scalaBody.contains("first.get.loc"), s"rhs should unwrap to first.get.loc: ${result.scalaBody}")
    assert(result.isComplete, s"should not refuse: ${result.refusalReasons}")

  test("value-context || with type mismatch refuses"):
    val body = block(
      ret(binOp("BarBarToken", ident("count"), RastNode("StringLiteral", 0, (0, 0), value = Some(RastValue.Str("none")))))
    )
    val result = translate(body, paramTypes = Map("count" -> "Int"))
    assert(
      result.refusalReasons.contains("truthiness-value-context"),
      s"type-mismatched value || should refuse: ${result.refusalReasons}"
    )

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

  // ---- js-map-construction ----

  test("object literal with constructor schema emits typed constructor"):
    val objLit = node(
      "ObjectLiteralExpression",
      node("PropertyAssignment", ident("mode"), RastNode("StringLiteral", 0, (0, 0), value = Some(RastValue.Str("math")))),
      node("PropertyAssignment", ident("style"), RastNode("StringLiteral", 0, (0, 0), value = Some(RastValue.Str("display"))))
    )
    val schema = ReferenceSignatures.ConstructorSchema(
      Map(
        "NodeStyling" -> List(
          ReferenceSignatures.CtorParam("mode", "Mode", hasDefault = false),
          ReferenceSignatures.CtorParam("loc", "Nullable[SourceLocation]", hasDefault = true),
          ReferenceSignatures.CtorParam("style", "StyleStr", hasDefault = true),
          ReferenceSignatures.CtorParam("body", "Array[Any]", hasDefault = true)
        )
      )
    )
    val ei = ReferenceSignatures.EnumIndex(
      Map(("Mode", "math") -> "Math", ("StyleStr", "display") -> "Display")
    )
    val body   = block(ret(objLit))
    val result = translate(body, returnType = Some("NodeStyling"), ctorSchema = schema, enumIndex = ei)
    assert(result.scalaBody.contains("NodeStyling("), s"should construct typed class: ${result.scalaBody}")
    assert(result.scalaBody.contains("mode = Mode.Math"), s"should resolve enum member: ${result.scalaBody}")
    assert(result.isComplete, s"should not refuse: ${result.refusalReasons}")

  test("object literal missing required field refuses with object-literal-missing-field"):
    val objLit = node(
      "ObjectLiteralExpression",
      node("PropertyAssignment", ident("style"), RastNode("StringLiteral", 0, (0, 0), value = Some(RastValue.Str("display"))))
    )
    val schema = ReferenceSignatures.ConstructorSchema(
      Map(
        "NodeStyling" -> List(
          ReferenceSignatures.CtorParam("mode", "Mode", hasDefault = false),
          ReferenceSignatures.CtorParam("style", "StyleStr", hasDefault = true)
        )
      )
    )
    val body   = block(ret(objLit))
    val result = translate(body, returnType = Some("NodeStyling"), ctorSchema = schema)
    assert(
      result.refusalReasons.contains("object-literal-missing-field"),
      s"missing required field should refuse: ${result.refusalReasons}"
    )

  test("object literal with no schema and non-Map return type refuses"):
    val objLit = node("ObjectLiteralExpression", node("PropertyAssignment", ident("key"), num(1)))
    val body   = block(ret(objLit))
    val result = translate(body, returnType = Some("SomeClass"))
    assert(
      result.refusalReasons.contains("js-map-construction"),
      s"should refuse with js-map-construction: ${result.refusalReasons}"
    )

  test("object literal in Map return type does not refuse"):
    val objLit = node("ObjectLiteralExpression", node("PropertyAssignment", ident("key"), num(1)))
    val body   = block(ret(objLit))
    val result = translate(body, returnType = Some("Map[String, Int]"))
    assert(!result.refusalReasons.contains("js-map-construction"), s"Map return type should not refuse: ${result.refusalReasons}")

  // ---- expected-type propagation ----

  test("string literal at enum slot resolves to enum member via enum index"):
    val objLit = node(
      "ObjectLiteralExpression",
      node("PropertyAssignment", ident("mode"), RastNode("StringLiteral", 0, (0, 0), value = Some(RastValue.Str("math"))))
    )
    val schema = ReferenceSignatures.ConstructorSchema(
      Map("NodeStyling" -> List(ReferenceSignatures.CtorParam("mode", "Mode", hasDefault = false)))
    )
    val ei     = ReferenceSignatures.EnumIndex(Map(("Mode", "math") -> "Math"))
    val body   = block(ret(objLit))
    val result = translate(body, returnType = Some("NodeStyling"), ctorSchema = schema, enumIndex = ei)
    assert(result.scalaBody.contains("Mode.Math"), s"should resolve to Mode.Math: ${result.scalaBody}")
    assert(result.isComplete, s"should not refuse: ${result.refusalReasons}")

  test("array literal at typed ArrayBuffer slot carries element type"):
    val arr    = node("ArrayLiteralExpression", num(1), num(2))
    val schema = ReferenceSignatures.ConstructorSchema(
      Map("NodeStyling" -> List(ReferenceSignatures.CtorParam("body", "Array[Int]", hasDefault = true)))
    )
    val objLit = node("ObjectLiteralExpression", node("PropertyAssignment", ident("body"), arr))
    val body   = block(ret(objLit))
    val result = translate(body, returnType = Some("NodeStyling"), ctorSchema = schema)
    assert(result.scalaBody.contains("Array[Int]"), s"should carry element type matching expected: ${result.scalaBody}")

  test("empty-using is refused not emitted when boundary label is missing"):
    // A return inside a try-catch where hasEarlyReturn missed it
    val body = block(
      node("TryStatement", block(ret(num(1))), node("CatchClause", node("VariableDeclaration", ident("e")), block(ret(num(2)))))
    )
    val result = translate(body, returnType = Some("Int"))
    assert(!result.scalaBody.contains("(using )"), s"must not emit empty using: ${result.scalaBody}")

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

  // ---- template literal ----

  test("template literal with interpolation emits string concatenation"):
    val tmpl = node(
      "TemplateExpression",
      RastNode("TemplateHead", 0, (0, 0), value = Some(RastValue.Str("hello "))),
      ident("name"),
      RastNode("TemplateTail", 0, (0, 0), value = Some(RastValue.Str(" world")))
    )
    val body   = block(ret(tmpl))
    val result = translate(body)
    assert(result.scalaBody.contains("\"hello \""), s"should emit text as string literal: ${result.scalaBody}")
    assert(result.scalaBody.contains(".toString"), s"should call toString on interpolated expr: ${result.scalaBody}")
    assert(result.scalaBody.contains("\" world\""), s"should emit tail text: ${result.scalaBody}")
    assert(!result.scalaBody.contains("${"), s"output must not contain dollar-brace: ${result.scalaBody}")
    assert(result.isComplete, s"should not refuse: ${result.refusalReasons}")

  test("template literal with only text parts produces plain string"):
    val tmpl = node(
      "TemplateExpression",
      RastNode("TemplateHead", 0, (0, 0), value = Some(RastValue.Str("price: $5")))
    )
    val body   = block(ret(tmpl))
    val result = translate(body)
    assert(result.scalaBody.contains("\"price: $5\""), s"plain text stays as string literal: ${result.scalaBody}")
    assert(!result.scalaBody.contains("${"), s"output must not contain dollar-brace: ${result.scalaBody}")
    assert(result.isComplete, s"should not refuse: ${result.refusalReasons}")

  // ---- memberRenames ----

  test("memberRenames maps a JS property to a Scala name on this"):
    val body   = block(ret(propAccess(thisKw, "type")))
    val result = translate(body, memberRenames = Map("type_" -> "tpe"))
    assert(result.scalaBody.contains("this.tpe"), s"should use renamed member: ${result.scalaBody}")

  test("memberRenames maps a JS property on a typed parameter"):
    val body   = block(ret(propAccess(ident("group"), "type")))
    val mi     = ReferenceSignatures.MemberIndex(Map("AnyParseNode" -> Set("tpe", "loc")))
    val result = translate(body, paramTypes = Map("group" -> "AnyParseNode"), memberIndex = mi, memberRenames = Map("type_" -> "tpe"))
    assert(result.scalaBody.contains("group.tpe"), s"should use renamed member: ${result.scalaBody}")
    assert(!result.refusalReasons.contains("wrong-member-access"), s"renamed member should not refuse: ${result.refusalReasons}")

  // ---- oracle-based argument type propagation ----

  test("array literal at callee parameter slot carries expected element type"):
    val arr  = node("ArrayLiteralExpression", RastNode("StringLiteral", 0, (0, 0), value = Some(RastValue.Str("bold"))))
    val call = node("CallExpression", ident("makeSpan"), arr, ident("children"))
    val body = block(ret(call))
    val orc  = ReferenceSignatures.TypeOracle.fromEntries(
      List(
        ("TestObj",
         ReferenceSignatures.MethodSig(
           "makeSpan",
           List(
             ReferenceSignatures.ParamSig("classes", "scala.collection.mutable.ArrayBuffer[String]"),
             ReferenceSignatures.ParamSig("children", "scala.collection.mutable.ArrayBuffer[HtmlDomNode]")
           ),
           "Span"
         )
        )
      )
    )
    val ci     = ReferenceSignatures.CalleeIndex(Map("makeSpan" -> List("TestObj")))
    val result = translate(body, calleeIndex = ci, oracle = orc)
    assert(result.scalaBody.contains("ArrayBuffer[String]"), s"should carry element type: ${result.scalaBody}")

  // ---- callee-arity-mismatch ----

  test("call with more arguments than callee declares refuses with callee-arity-mismatch"):
    val call = node("CallExpression", ident("makeSpan"), num(1), num(2), num(3))
    val body = block(ret(call))
    val orc  = ReferenceSignatures.TypeOracle.fromEntries(
      List(
        ("TestObj",
         ReferenceSignatures.MethodSig("makeSpan",
                                       List(
                                         ReferenceSignatures.ParamSig("a", "Int"),
                                         ReferenceSignatures.ParamSig("b", "Int")
                                       ),
                                       "Any"
         )
        )
      )
    )
    val ci     = ReferenceSignatures.CalleeIndex(Map("makeSpan" -> List("TestObj")))
    val result = translate(body, calleeIndex = ci, oracle = orc)
    assert(result.refusalReasons.contains("callee-arity-mismatch"), s"should refuse arity mismatch: ${result.refusalReasons}")

  test("call with matching argument count does not refuse arity"):
    val call = node("CallExpression", ident("makeSpan"), num(1), num(2))
    val body = block(ret(call))
    val orc  = ReferenceSignatures.TypeOracle.fromEntries(
      List(
        ("TestObj",
         ReferenceSignatures.MethodSig("makeSpan",
                                       List(
                                         ReferenceSignatures.ParamSig("a", "Int"),
                                         ReferenceSignatures.ParamSig("b", "Int")
                                       ),
                                       "Any"
         )
        )
      )
    )
    val ci     = ReferenceSignatures.CalleeIndex(Map("makeSpan" -> List("TestObj")))
    val result = translate(body, calleeIndex = ci, oracle = orc)
    assert(
      !result.refusalReasons.contains("callee-arity-mismatch"),
      s"should not refuse matching arity: ${result.refusalReasons}"
    )

  // ---- toFixed locale independence ----

  test("toFixed emits BigDecimal HALF_UP for locale independence"):
    val call = node(
      "CallExpression",
      propAccess(ident("n"), "toFixed"),
      num(4)
    )
    val body   = block(ret(call))
    val result = translate(body, paramTypes = Map("n" -> "Double"))
    assert(
      result.scalaBody.contains("BigDecimal") && result.scalaBody.contains("HALF_UP"),
      s"toFixed should use BigDecimal HALF_UP: ${result.scalaBody}"
    )
    assert(!result.scalaBody.contains("f\""), s"toFixed must not use f-interpolation: ${result.scalaBody}")

  test("toFixed with half-up rounding differs from half-even at 2.5"):
    // JS: (2.55).toFixed(1) === "2.6" (half-up)
    // f"%.1f" on JVM uses half-even: "2.5"
    // BigDecimal HALF_UP: "2.6" -- matches JS
    val call = node(
      "CallExpression",
      propAccess(ident("x"), "toFixed"),
      num(1)
    )
    val body   = block(ret(call))
    val result = translate(body, paramTypes = Map("x" -> "Double"))
    assert(result.scalaBody.contains("setScale(1"), s"precision should be passed to setScale: ${result.scalaBody}")

  // ---- int-division-on-number ----

  test("division in a method with Int return type refuses int-division-on-number"):
    val body   = block(ret(binOp("SlashToken", ident("a"), ident("b"))))
    val result = translate(body, returnType = Some("Int"))
    assert(
      result.refusalReasons.contains("int-division-on-number"),
      s"Int return with / should refuse: ${result.refusalReasons}"
    )

  test("division in a method with Double return type does not refuse"):
    val body   = block(ret(binOp("SlashToken", ident("a"), ident("b"))))
    val result = translate(body, returnType = Some("Double"))
    assert(
      !result.refusalReasons.contains("int-division-on-number"),
      s"Double return with / should not refuse: ${result.refusalReasons}"
    )

  // ---- val vs var for reassigned const ----

  test("const variable later assigned becomes var"):
    val body = block(
      node(
        "VariableStatement",
        node(
          "VariableDeclarationList",
          RastNode("VariableDeclaration", 0, (0, 0), children = List(ident("x"), num(0)), flags = List("const"))
        )
      ),
      node("ExpressionStatement", binOp("EqualsToken", ident("x"), num(1))),
      ret(ident("x"))
    )
    val entry  = DefmethodEntry("_free_", "test", Nil, body)
    val result = DefmethodBodyTranslator.translateBody(entry, Nil, "    ")
    assert(result.scalaBody.contains("var x"), s"reassigned const should become var: ${result.scalaBody}")

  test("const variable not reassigned stays val"):
    val body = block(
      node(
        "VariableStatement",
        node(
          "VariableDeclarationList",
          RastNode("VariableDeclaration", 0, (0, 0), children = List(ident("x"), num(0)), flags = List("const"))
        )
      ),
      ret(ident("x"))
    )
    val entry  = DefmethodEntry("_free_", "test", Nil, body)
    val result = DefmethodBodyTranslator.translateBody(entry, Nil, "    ")
    assert(result.scalaBody.contains("val x"), s"non-reassigned const should stay val: ${result.scalaBody}")

  test("reassigned parameter refuses with reassigned-immutable"):
    val body = block(
      node(
        "ExpressionStatement",
        binOp("EqualsToken", ident("extraVinculum"), binOp("AsteriskToken", num(1000), ident("extraVinculum")))
      ),
      ret(ident("extraVinculum"))
    )
    val entry  = DefmethodEntry("_free_", "test", List("extraVinculum"), body)
    val result = DefmethodBodyTranslator.translateBody(entry, Nil, "    ")
    assert(result.refusalReasons.contains("reassigned-immutable"), s"should refuse reassigned param: ${result.refusalReasons}")

  test("non-reassigned parameter does not refuse reassigned-immutable"):
    val body = block(
      ret(ident("x"))
    )
    val entry  = DefmethodEntry("_free_", "test", List("x"), body)
    val result = DefmethodBodyTranslator.translateBody(entry, Nil, "    ")
    assert(
      !result.refusalReasons.contains("reassigned-immutable"),
      s"should not refuse non-reassigned param: ${result.refusalReasons}"
    )

  // ---- callee-arity-mismatch for too few args ----

  test("call with fewer arguments than callee declares refuses with callee-arity-mismatch"):
    val call = node("CallExpression", ident("makeSpan"), num(1))
    val body = block(ret(call))
    val orc  = ReferenceSignatures.TypeOracle.fromEntries(
      List(
        ("TestObj",
         ReferenceSignatures.MethodSig("makeSpan",
                                       List(
                                         ReferenceSignatures.ParamSig("a", "Int"),
                                         ReferenceSignatures.ParamSig("b", "Int")
                                       ),
                                       "Any"
         )
        )
      )
    )
    val ci     = ReferenceSignatures.CalleeIndex(Map("makeSpan" -> List("TestObj")))
    val result = translate(body, calleeIndex = ci, oracle = orc)
    assert(result.refusalReasons.contains("callee-arity-mismatch"), s"should refuse too few args: ${result.refusalReasons}")

  // ---- null-at-non-nullable-slot ----

  test("null at a String slot refuses null-at-non-nullable-slot"):
    val schema = ReferenceSignatures.ConstructorSchema(
      Map("Foo" -> List(ReferenceSignatures.CtorParam("name", "String", hasDefault = false)))
    )
    val objLit = node("ObjectLiteralExpression", node("PropertyAssignment", ident("name"), RastNode("NullKeyword", 0, (0, 0))))
    val body   = block(ret(objLit))
    val result = translate(body, returnType = Some("Foo"), ctorSchema = schema)
    assert(
      result.refusalReasons.contains("null-at-non-nullable-slot"),
      s"null at String slot should refuse: ${result.refusalReasons}"
    )

  test("null at a Nullable slot does not refuse"):
    val schema = ReferenceSignatures.ConstructorSchema(
      Map("Foo" -> List(ReferenceSignatures.CtorParam("name", "Nullable[String]", hasDefault = false)))
    )
    val objLit = node("ObjectLiteralExpression", node("PropertyAssignment", ident("name"), RastNode("NullKeyword", 0, (0, 0))))
    val body   = block(ret(objLit))
    val result = translate(body, returnType = Some("Foo"), ctorSchema = schema)
    assert(
      !result.refusalReasons.contains("null-at-non-nullable-slot"),
      s"null at Nullable slot should not refuse: ${result.refusalReasons}"
    )

  // ---- array destructuring in for-of ----

  test("for-of with array destructuring emits tuple pattern"):
    val body = block(
      node(
        "ForOfStatement",
        node(
          "VariableDeclarationList",
          node(
            "VariableDeclaration",
            ident("_pair"),
            node("ArrayBindingPattern", node("BindingElement", ident("k")), node("BindingElement", ident("v")))
          )
        ),
        ident("myMap"),
        block(node("ExpressionStatement", ident("process")))
      ),
      ret(num(0))
    )
    val result = translate(body)
    assert(result.scalaBody.contains("(k, v) <- myMap"), s"should destructure as tuple: ${result.scalaBody}")

  // ---- module-level value resolution ----

  test("module-level value resolves through callee index"):
    val body   = block(ret(node("ElementAccessExpression", ident("styles"), num(0))))
    val ci     = ReferenceSignatures.CalleeIndex(Map("styles" -> List("Style")))
    val result = translate(body, calleeIndex = ci)
    assert(result.scalaBody.contains("Style.styles(0)"), s"should qualify module value: ${result.scalaBody}")

  test("module-level value does not shadow local variable"):
    val body = block(
      node(
        "VariableStatement",
        node(
          "VariableDeclarationList",
          RastNode("VariableDeclaration", 0, (0, 0), children = List(ident("styles"), node("ArrayLiteralExpression", num(1))))
        )
      ),
      ret(node("ElementAccessExpression", ident("styles"), num(0)))
    )
    val ci     = ReferenceSignatures.CalleeIndex(Map("styles" -> List("Style")))
    val result = translate(body, calleeIndex = ci)
    assert(!result.scalaBody.contains("Style.styles"), s"local should not be qualified: ${result.scalaBody}")

  test("module-level value does not shadow parameter"):
    val entry  = DefmethodEntry("_free_", "test", List("styles"), block(ret(node("ElementAccessExpression", ident("styles"), num(0)))))
    val ci     = ReferenceSignatures.CalleeIndex(Map("styles" -> List("Style")))
    val result = DefmethodBodyTranslator.translateBody(entry, Nil, "    ", calleeIndex = ci)
    assert(!result.scalaBody.contains("Style.styles"), s"param should not be qualified: ${result.scalaBody}")

  // ---- predicate lambda truthiness ----

  test("filter with identity lambda on String collection applies nonEmpty"):
    val lambda = node("ArrowFunction", RastNode("Parameter", 0, (0, 0), children = List(ident("cls"))), block(ret(ident("cls"))))
    val call   = node("CallExpression", propAccess(ident("classes"), "filter"), lambda)
    val body   = block(ret(call))
    val result = translate(body, paramTypes = Map("classes" -> "Array[String]"))
    assert(result.scalaBody.contains("nonEmpty"), s"String truthiness in filter should use nonEmpty: ${result.scalaBody}")
    assert(result.isComplete, s"should not refuse: ${result.refusalReasons}")

  test("filter with identity lambda on Nullable collection applies isDefined"):
    val lambda = node("ArrowFunction", RastNode("Parameter", 0, (0, 0), children = List(ident("item"))), block(ret(ident("item"))))
    val call   = node("CallExpression", propAccess(ident("items"), "filter"), lambda)
    val body   = block(ret(call))
    val result = translate(body, paramTypes = Map("items" -> "Array[Nullable[Int]]", "item" -> "Nullable[Int]"))
    assert(result.scalaBody.contains("isDefined"), s"Nullable truthiness in filter should use isDefined: ${result.scalaBody}")

  test("find with identity lambda on unknown type refuses"):
    val lambda = node("ArrowFunction", RastNode("Parameter", 0, (0, 0), children = List(ident("x"))), block(ret(ident("x"))))
    val call   = node("CallExpression", propAccess(ident("items"), "find"), lambda)
    val body   = block(ret(call))
    val result = translate(body)
    assert(
      result.refusalReasons.contains("truthiness-unknown-type"),
      s"unknown type in predicate should refuse: ${result.refusalReasons}"
    )

  test("filter with non-identity lambda is not affected"):
    val lambda = node(
      "ArrowFunction",
      RastNode("Parameter", 0, (0, 0), children = List(ident("x"))),
      block(ret(binOp("GreaterThanToken", ident("x"), num(0))))
    )
    val call   = node("CallExpression", propAccess(ident("items"), "filter"), lambda)
    val body   = block(ret(call))
    val result = translate(body, paramTypes = Map("items" -> "Array[Int]"))
    assert(result.scalaBody.contains("> 0"), s"comparison filter stays: ${result.scalaBody}")
    assert(result.isComplete, s"should not refuse: ${result.refusalReasons}")

  // ---- argument-type-mismatch ----

  test("call passing wrong type to callee refuses argument-type-mismatch"):
    // Fixture: two sibling classes HtmlNode and MathNode. makeRow takes MathNode.
    // buildExpr returns HtmlNode. Passing buildExpr result to makeRow mismatches.
    val orc = ReferenceSignatures.TypeOracle.fromEntries(
      List(
        ("Builder", ReferenceSignatures.MethodSig("makeRow", List(ReferenceSignatures.ParamSig("children", "scala.collection.mutable.ArrayBuffer[MathNode]")), "MathNode")),
        ("Builder", ReferenceSignatures.MethodSig("buildExpr", List(ReferenceSignatures.ParamSig("expr", "Array[Any]")), "scala.collection.mutable.ArrayBuffer[HtmlNode]"))
      )
    )
    val ci = ReferenceSignatures.CalleeIndex(
      Map(
        "makeRow" -> List("Builder"),
        "buildExpr" -> List("Builder")
      )
    )
    // body: return makeRow(buildExpr(expression))
    val innerCall = node("CallExpression", ident("buildExpr"), ident("expression"))
    val outerCall = node("CallExpression", ident("makeRow"), innerCall)
    val body      = block(ret(outerCall))
    val result    = translate(body, calleeIndex = ci, oracle = orc)
    assert(
      result.refusalReasons.exists(_.startsWith("argument-type-mismatch:")),
      s"should refuse type mismatch: ${result.refusalReasons}"
    )
    assert(
      result.refusalReasons.exists(_.contains("HtmlNode->MathNode")),
      s"should name the types: ${result.refusalReasons}"
    )

  test("call passing matching type does not refuse"):
    val orc = ReferenceSignatures.TypeOracle.fromEntries(
      List(
        ("Builder", ReferenceSignatures.MethodSig("makeRow", List(ReferenceSignatures.ParamSig("children", "scala.collection.mutable.ArrayBuffer[MathNode]")), "MathNode")),
        ("Builder",
         ReferenceSignatures.MethodSig(
           "buildMathExpr",
           List(ReferenceSignatures.ParamSig("expr", "Array[Any]")),
           "scala.collection.mutable.ArrayBuffer[MathNode]"
         )
        )
      )
    )
    val ci = ReferenceSignatures.CalleeIndex(
      Map(
        "makeRow" -> List("Builder"),
        "buildMathExpr" -> List("Builder")
      )
    )
    val innerCall = node("CallExpression", ident("buildMathExpr"), ident("expression"))
    val outerCall = node("CallExpression", ident("makeRow"), innerCall)
    val body      = block(ret(outerCall))
    val result    = translate(body, calleeIndex = ci, oracle = orc)
    assert(
      !result.refusalReasons.exists(_.startsWith("argument-type-mismatch:")),
      s"matching type should not refuse: ${result.refusalReasons}"
    )

  // ---- unresolved-reference refusal ----

  test("property access on known skeleton object whose member is absent refuses with unresolved-reference"):
    // Obj has 'reset' but not 'frequency' -- accessing Obj.frequency must refuse
    val ci     = ReferenceSignatures.CalleeIndex(Map("reset" -> List("Obj")))
    val body   = block(ret(propAccess(ident("Obj"), "frequency")))
    val result = translate(body, calleeIndex = ci)
    assert(
      result.refusalReasons.contains("unresolved-reference"),
      s"should refuse unresolved member: ${result.refusalReasons}"
    )

  test("property access on known skeleton object whose member is present does not refuse"):
    val ci     = ReferenceSignatures.CalleeIndex(Map("reset" -> List("Obj"), "frequency" -> List("Obj")))
    val body   = block(ret(propAccess(ident("Obj"), "frequency")))
    val result = translate(body, calleeIndex = ci)
    assert(
      !result.refusalReasons.contains("unresolved-reference"),
      s"member is present, should not refuse: ${result.refusalReasons}"
    )

  test("unqualified reference to a parameter translates without unresolved-reference"):
    val entry  = DefmethodEntry("_free_", "test", List("data"), block(ret(ident("data"))))
    val ci     = ReferenceSignatures.CalleeIndex(Map("reset" -> List("Obj")))
    val result = DefmethodBodyTranslator.translateBody(entry, Nil, "    ", calleeIndex = ci)
    assert(
      !result.refusalReasons.contains("unresolved-reference"),
      s"parameter should not refuse: ${result.refusalReasons}"
    )

  test("property access on unknown receiver still refuses with receiver-type-unknown"):
    val ci     = ReferenceSignatures.CalleeIndex(Map("reset" -> List("Obj")))
    val body   = block(ret(propAccess(ident("Unknown"), "field")))
    val result = translate(body, calleeIndex = ci)
    assert(
      result.refusalReasons.contains("receiver-type-unknown"),
      s"unknown receiver should refuse receiver-type-unknown: ${result.refusalReasons}"
    )
    assert(
      !result.refusalReasons.contains("unresolved-reference"),
      s"unknown receiver should not refuse unresolved-reference: ${result.refusalReasons}"
    )
