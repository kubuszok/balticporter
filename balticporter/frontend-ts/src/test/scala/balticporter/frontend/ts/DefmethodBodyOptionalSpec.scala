package balticporter.frontend.ts

import balticporter.frontend.ts.dedicated.{ DefmethodBodyTranslator, DefmethodEntry }

/** Optional values and module objects read as JS reads them: a present `""` or `0` is falsy, `if (x)` proves `x` present inside, a present value at an optional slot must be of its inner type, and a
  * module object the reference gave an `apply` is indexed through it.
  */
class DefmethodBodyOptionalSpec extends munit.FunSuite:

  private def node(kind:   String, children: RastNode*): RastNode = RastNode(kind, 0, (0, 0), children = children.toList)
  private def ident(name:  String):                      RastNode = RastNode("Identifier", 0, (0, 0), text = Some(name))
  private def block(stmts: RastNode*):                   RastNode = node("Block", stmts*)
  private def expr(e:      RastNode):                    RastNode = node("ExpressionStatement", e)
  private def ret(e:       RastNode):                    RastNode = node("ReturnStatement", e)
  private def call(callee: RastNode, args:   RastNode*): RastNode = node("CallExpression", (callee :: args.toList)*)
  private def assign(l:    RastNode, r:      RastNode):  RastNode = node("BinaryExpression", l, r).copy(operator = Some("EqualsToken"))
  private def at(recv:     RastNode, key:    RastNode):  RastNode = node("ElementAccessExpression", recv, key)

  private val callees = ReferenceSignatures.CalleeIndex(
    List(
      ReferenceSignatures.MemberRef("Obj", "f", "Obj", isPrivate = false),
      ReferenceSignatures.MemberRef("Symbols", "apply", "Symbols", isPrivate = false)
    ),
    Set("Obj", "Symbols")
  )
  private val oracle = ReferenceSignatures.TypeOracle.fromEntries(
    List(
      "Obj" -> ReferenceSignatures.MethodSig("f", List(ReferenceSignatures.ParamSig("p", "Nullable[Options]")), "Unit"),
      "Symbols" -> ReferenceSignatures.MethodSig("apply", List(ReferenceSignatures.ParamSig("mode", "String")), "scala.collection.mutable.Map[String, SymbolInfo]")
    )
  )

  private def translate(body: RastNode, paramTypes: Map[String, String], returnType: String) =
    DefmethodBodyTranslator.translateBody(
      DefmethodEntry("_free_", "test", paramTypes.keys.toList, body),
      Nil,
      "    ",
      apiLookup = Map("symbols" -> "Symbols"),
      returnType = Some(returnType),
      paramTypes = paramTypes,
      calleeIndex = callees,
      oracle = oracle,
      enclosingOwner = Some("Obj")
    )

  test("a present empty string is as falsy as an absent one, and `if (x)` reads x present inside"):
    val r = translate(
      block(node("IfStatement", ident("x"), block(assign(ident("t"), ident("x")).pipe(expr))), ret(ident("t"))),
      Map("x" -> "Nullable[String]", "t" -> "String"),
      "String"
    )
    assert(r.scalaBody.contains("if ((x.isDefined && x.get.nonEmpty)) {"), r.scalaBody)
    assert(r.scalaBody.contains("t = x.get"), r.scalaBody)

  test("an optional value stored where a value is required is refused"):
    val r = translate(block(expr(assign(ident("t"), ident("x"))), ret(ident("t"))), Map("x" -> "Nullable[String]", "t" -> "String"), "String")
    assert(r.refusalReasons.contains("optional-at-required-slot"), r.refusalReasons.toString)

  test("a present value at an optional slot is wrapped, and must be of the slot's inner type"):
    val ok = translate(block(expr(call(ident("f"), ident("o")))), Map("o" -> "Options"), "Unit")
    assert(ok.isComplete, ok.refusalReasons.toString)
    assert(ok.scalaBody.contains("Obj.f(Nullable(o))"), ok.scalaBody)
    val wrong = translate(block(expr(call(ident("f"), ident("o")))), Map("o" -> "Span"), "Unit")
    assert(wrong.refusalReasons.contains("argument-type-mismatch:Span->Options"), wrong.refusalReasons.toString)

  test("a module object the reference gave an apply is indexed through it, and its map read answers null for an absent key"):
    val r = translate(block(ret(at(at(ident("symbols"), ident("mode")), ident("name")))), Map("mode" -> "String", "name" -> "String"), "SymbolInfo")
    assert(r.isComplete, r.refusalReasons.toString)
    assertNoDiff(r.scalaBody.trim, "Symbols(mode).getOrElse(name, null)")

  test(
    "a local declared without a value is typed by what every path stores before reading it, and refused where one path may read it first"
  ):
    def decl(n:  String) = node("VariableStatement", node("VariableDeclarationList", node("VariableDeclaration", ident(n))))
    def store(v: String) = block(expr(assign(ident("s"), ident(v))))
    val both             = translate(
      block(decl("s"), node("IfStatement", ident("c"), store("a"), store("b")), ret(ident("s"))),
      Map("c" -> "Boolean", "a" -> "String", "b" -> "String"),
      "String"
    )
    assert(both.isComplete, both.refusalReasons.toString)
    assert(both.scalaBody.contains("var s: String = null"), both.scalaBody)
    val one = translate(block(decl("s"), node("IfStatement", ident("c"), store("a")), ret(ident("s"))), Map("c" -> "Boolean", "a" -> "String"), "String")
    assert(one.refusalReasons.contains("uninitialised-local"), one.refusalReasons.toString)

  extension (n: RastNode) private def pipe(f: RastNode => RastNode): RastNode = f(n)
