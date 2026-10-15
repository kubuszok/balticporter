package balticporter.frontend.ts

import balticporter.frontend.ts.dedicated.{ DefmethodBodyTranslator, DefmethodEntry }

/** A JS `switch` and an object literal as JS evaluates them: a clause runs on into the next, `default` is tried after every label, and a literal's values are evaluated in the literal's order. */
class DefmethodBodySwitchSpec extends munit.FunSuite:

  private def node(kind:     String, children:  RastNode*): RastNode = RastNode(kind, 0, (0, 0), children = children.toList)
  private def ident(name:    String):                       RastNode = RastNode("Identifier", 0, (0, 0), text = Some(name))
  private def num(n:         Double):                       RastNode = RastNode("NumericLiteral", 0, (0, 0), value = Some(RastValue.Num(n)))
  private def str(s:         String):                       RastNode = RastNode("StringLiteral", 0, (0, 0), value = Some(RastValue.Str(s)))
  private def block(stmts:   RastNode*):                    RastNode = node("Block", stmts*)
  private def expr(e:        RastNode):                     RastNode = node("ExpressionStatement", e)
  private def call(callee:   RastNode, args:    RastNode*): RastNode = node("CallExpression", (callee :: args.toList)*)
  private def brk:                                          RastNode = node("BreakStatement")
  private def caseOf(label:  RastNode, stmts:   RastNode*): RastNode = node("CaseClause", (label :: stmts.toList)*)
  private def default(stmts: RastNode*):                    RastNode = node("DefaultClause", stmts*)
  private def switch(sel:    RastNode, clauses: RastNode*): RastNode = node("SwitchStatement", sel, node("CaseBlock", clauses*))

  private val callees = ReferenceSignatures.CalleeIndex(List("f", "g").map(m => ReferenceSignatures.MemberRef("Obj", m, "Obj", isPrivate = false)), Set("Obj"))
  private val oracle  = ReferenceSignatures.TypeOracle.fromEntries(List("f", "g").map(m => "Obj" -> ReferenceSignatures.MethodSig(m, Nil, "Unit")))

  private def translate(body: RastNode, paramTypes: Map[String, String], ctorSchema: ReferenceSignatures.ConstructorSchema = ReferenceSignatures.ConstructorSchema.empty, returnType: String = "Unit") =
    DefmethodBodyTranslator.translateBody(
      DefmethodEntry("_free_", "test", paramTypes.keys.toList, body),
      Nil,
      "    ",
      returnType = Some(returnType),
      paramTypes = paramTypes,
      calleeIndex = callees,
      oracle = oracle,
      ctorSchema = ctorSchema,
      enclosingOwner = Some("Obj")
    )

  private def lines(r: DefmethodBodyTranslator.TranslationResult): String = r.scalaBody.linesIterator.map(_.trim).filter(_.nonEmpty).mkString("\n")

  test("an empty clause runs on into the next, and a clause ending in break leaves the switch"):
    val r = translate(
      block(switch(ident("s"), caseOf(str("a"), expr(call(ident("f"))), brk), caseOf(str("b")), default(expr(call(ident("g")))))),
      Map("s" -> "String")
    )
    assert(r.isComplete, r.refusalReasons.toString)
    assertNoDiff(lines(r), "{\n(s) match {\ncase \"a\" =>\nObj.f()\ncase \"b\" =>\nObj.g()\ncase _ =>\nObj.g()\n}\n}")

  test("default is tried after every label wherever it stands, and runs on into the clause after it"):
    val r = translate(
      block(switch(ident("n"), default(expr(call(ident("g")))), caseOf(num(1), expr(call(ident("f"))), brk))),
      Map("n" -> "Double")
    )
    assert(r.isComplete, r.refusalReasons.toString)
    assertNoDiff(lines(r), "{\n(n) match {\ncase s$1 if s$1 == (1) =>\nObj.f()\ncase _ =>\nObj.g()\nObj.f()\n}\n}")

  test("a break before a clause's end leaves the switch from the middle of an arm, which a match cannot"):
    val early = node("IfStatement", ident("c"), block(brk))
    val r     = translate(block(switch(ident("s"), caseOf(str("a"), early, expr(call(ident("f"))), brk))), Map("s" -> "String", "c" -> "Boolean"))
    assert(r.refusalReasons.contains("switch-break-in-middle"), r.refusalReasons.toString)

  test("an object literal's values are passed in the literal's order, a backticked field by its own key"):
    val schema = ReferenceSignatures.ConstructorSchema(
      Map("OpSet" -> List(ReferenceSignatures.CtorParam("type", "String", false), ReferenceSignatures.CtorParam("ops", "Int", false)))
    )
    val lit = node(
      "ObjectLiteralExpression",
      node("PropertyAssignment", ident("ops"), ident("x")),
      node("PropertyAssignment", ident("type"), str("path"))
    )
    val r = translate(block(node("ReturnStatement", lit)), Map("x" -> "Int"), schema, "OpSet")
    assert(r.isComplete, r.refusalReasons.toString)
    assertNoDiff(r.scalaBody.trim, "OpSet(ops = x, `type` = \"path\")")
