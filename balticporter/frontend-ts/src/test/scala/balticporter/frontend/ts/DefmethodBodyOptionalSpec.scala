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
    assert(r.scalaBody.contains("t$1 = x.get"), r.scalaBody)

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

  test(
    "a value the target file declares at package level is read bare, unless a member of that name could be what the name reaches"
  ):
    val names = ReferenceDeclarations.namesIn("Gen.scala", "package p\n\nfinal val NOS: String = \"none\"\n\nobject Gen {\n  def f(): Unit = ()\n}\n")
    assertEquals(names.terms, Map("NOS" -> Some("String")))
    def run(callees: ReferenceSignatures.CalleeIndex) =
      DefmethodBodyTranslator.translateBody(
        DefmethodEntry(
          "_free_",
          "test",
          List("s"),
          block(ret(node("BinaryExpression", ident("s"), ident("NOS")).copy(operator = Some("EqualsEqualsEqualsToken"))))
        ),
        Nil,
        "    ",
        returnType = Some("Boolean"),
        paramTypes = Map("s" -> "String"),
        calleeIndex = callees,
        enclosingOwner = Some("Gen"),
        fileNames = names
      )
    val bare = run(ReferenceSignatures.CalleeIndex(Nil, Set("Gen")))
    assert(bare.isComplete, bare.refusalReasons.toString)
    assertNoDiff(bare.scalaBody.trim, "s == NOS")
    val member = run(ReferenceSignatures.CalleeIndex(List(ReferenceSignatures.MemberRef("Gen", "NOS", "Gen", isPrivate = false)), Set("Gen")))
    assertNoDiff(member.scalaBody.trim, "s == Gen.NOS")

  test("a parameter the body writes is copied into a local var; one a nested function writes is refused"):
    val double = assign(ident("n"), node("BinaryExpression", ident("n"), ident("n")).copy(operator = Some("AsteriskToken")))
    val r      = translate(block(expr(double), ret(ident("n"))), Map("n" -> "Double"), "Double")
    assert(r.isComplete, r.refusalReasons.toString)
    assertNoDiff(r.scalaBody.linesIterator.map(_.trim).filter(_.nonEmpty).mkString("\n"), "{\nvar n$1 = n\nn$1 = n$1 * n$1\nn$1\n}")
    val nested = translate(block(expr(call(ident("f"), node("ArrowFunction", block(expr(double))))), ret(ident("n"))), Map("n" -> "Double"), "Double")
    assert(nested.refusalReasons.contains("reassigned-immutable"), nested.refusalReasons.toString)

  test(
    "a subclass the reference declares fits its parent's slot, and an immutable sequence of it, but never a buffer of the parent"
  ):
    val mi      = ReferenceSignatures.MemberIndex(Map("Base" -> Set.empty, "Sub" -> Set.empty), parents = Map("Sub" -> List("Base")))
    val sigs    = List("one" -> "Base", "all" -> "Vector[Base]", "buf" -> "scala.collection.mutable.ArrayBuffer[Base]")
    val callees = ReferenceSignatures.CalleeIndex(sigs.map((m, _) => ReferenceSignatures.MemberRef("Obj", m, "Obj", isPrivate = false)), Set("Obj"))
    val oracle  = ReferenceSignatures.TypeOracle.fromEntries(sigs.map((m, t) => "Obj" -> ReferenceSignatures.MethodSig(m, List(ReferenceSignatures.ParamSig("p", t)), "Unit")))
    def run(callee: String, argType: String) =
      DefmethodBodyTranslator.translateBody(
        DefmethodEntry("_free_", "test", List("x"), block(expr(call(ident(callee), ident("x"))))),
        Nil,
        "    ",
        returnType = Some("Unit"),
        paramTypes = Map("x" -> argType),
        calleeIndex = callees,
        memberIndex = mi,
        oracle = oracle,
        enclosingOwner = Some("Obj")
      )
    assert(run("one", "Sub").isComplete)
    assert(run("all", "Vector[Sub]").isComplete)
    assert(run("buf", "scala.collection.mutable.ArrayBuffer[Sub]").refusalReasons.contains("argument-type-mismatch:Sub->Base"))

  extension (n: RastNode) private def pipe(f: RastNode => RastNode): RastNode = f(n)

  test(
    "an annotated local is definitely assigned by stores of any type, an unannotated one only by stores whose type is known"
  ):
    def decl(annotation: Option[RastNode]) =
      node("VariableStatement", node("VariableDeclarationList", node("VariableDeclaration", (ident("s") :: annotation.toList)*)))
    def store(v: RastNode)                 = block(expr(assign(ident("s"), v)))
    def body(annotation: Option[RastNode]) =
      block(decl(annotation), node("IfStatement", ident("c"), store(at(ident("m"), ident("k"))), store(ident("a"))), ret(ident("s")))
    val types     = Map("c" -> "Boolean", "m" -> "Any", "k" -> "String", "a" -> "Double")
    val annotated = translate(body(Some(node("NumberKeyword"))), types, "Double")
    assert(!annotated.refusalReasons.contains("uninitialised-local"), annotated.refusalReasons.toString)
    assert(annotated.scalaBody.contains("var s: Double = 0.0"), annotated.scalaBody)
    val bare = translate(body(None), types, "Double")
    assert(bare.refusalReasons.contains("uninitialised-local"), bare.refusalReasons.toString)
