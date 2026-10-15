package balticporter.frontend.ts

import balticporter.frontend.ts.dedicated.{ DefmethodBodyTranslator, DefmethodEntry, JsConventions }

/** The body translator reading the reference's declared types: each spec pins one translation a declared type makes faithful, or one refusal where JS and Scala would disagree. */
class DefmethodBodyTypedSpec extends munit.FunSuite:

  private def node(kind:   String, children: RastNode*): RastNode = RastNode(kind, 0, (0, 0), children = children.toList)
  private def ident(name:  String):                      RastNode = RastNode("Identifier", 0, (0, 0), text = Some(name))
  private def num(n:       Double):                      RastNode = RastNode("NumericLiteral", 0, (0, 0), value = Some(RastValue.Num(n)))
  private def str(s:       String):                      RastNode = RastNode("StringLiteral", 0, (0, 0), value = Some(RastValue.Str(s)))
  private def ret(e:       RastNode):                    RastNode = node("ReturnStatement", e)
  private def block(stmts: RastNode*):                   RastNode = node("Block", stmts*)
  private def expr(e:      RastNode):                    RastNode = node("ExpressionStatement", e)
  private def bin(op: String, l: RastNode, r: RastNode): RastNode =
    RastNode("BinaryExpression", 0, (0, 0), children = List(l, r), operator = Some(op))
  private def prop(obj:    RastNode, p:    String):    RastNode = node("PropertyAccessExpression", obj, ident(p))
  private def call(callee: RastNode, args: RastNode*): RastNode = node("CallExpression", (callee :: args.toList)*)
  private def thisKw:                                  RastNode = RastNode("ThisKeyword", 0, (0, 0))
  private def local(name: String, rest: RastNode*):    RastNode =
    node("VariableStatement", node("VariableDeclarationList", node("VariableDeclaration", (ident(name) :: rest.toList)*)))
  private def postfixInc(e: RastNode): RastNode = RastNode("PostfixUnaryExpression", 0, (0, 0), children = List(e), operator = Some("PlusPlusToken"))

  private def translate(
    body:        RastNode,
    params:      List[String] = Nil,
    paramTypes:  Map[String, String] = Map.empty,
    returnType:  Option[String] = None,
    className:   String = "_free_",
    calleeIndex: ReferenceSignatures.CalleeIndex = ReferenceSignatures.CalleeIndex.empty,
    memberIndex: ReferenceSignatures.MemberIndex = ReferenceSignatures.MemberIndex.empty,
    ctorSchema:  ReferenceSignatures.ConstructorSchema = ReferenceSignatures.ConstructorSchema.empty,
    oracle:      ReferenceSignatures.TypeOracle = ReferenceSignatures.TypeOracle.empty,
    conventions: JsConventions = JsConventions.plain
  ): DefmethodBodyTranslator.TranslationResult =
    DefmethodBodyTranslator.translateBody(
      DefmethodEntry(className, "test", params, body),
      Nil,
      "    ",
      returnType = returnType,
      paramTypes = paramTypes,
      calleeIndex = calleeIndex,
      memberIndex = memberIndex,
      ctorSchema = ctorSchema,
      oracle = oracle,
      conventions = conventions
    )

  private def refuses(r: DefmethodBodyTranslator.TranslationResult, reason: String): Unit =
    assert(r.refusalReasons.contains(reason), s"expected refusal $reason, got ${r.refusalReasons} for:\n${r.scalaBody}")

  private def accepts(r: DefmethodBodyTranslator.TranslationResult): Unit =
    assert(r.isComplete, s"expected no refusal, got ${r.refusalReasons} for:\n${r.scalaBody}")

  private def objectIndex(owner: String, members: String*): ReferenceSignatures.CalleeIndex =
    ReferenceSignatures.CalleeIndex(members.toList.map(m => ReferenceSignatures.MemberRef(owner, m, owner, isPrivate = true)), Set(owner))

  test("a leading underscore is part of the name: the private helper resolves under its own spelling"):
    val r = translate(
      block(ret(call(ident("_offset_opt"), ident("x")))),
      params = List("x"),
      paramTypes = Map("x" -> "Double"),
      calleeIndex = objectIndex("Renderer", "_offsetOpt")
    )
    assertNoDiff(r.scalaBody.trim, "Renderer._offsetOpt(x)")

  test(
    "`this` in a method of a class the reference declares is that class: a declared member is accepted, an undeclared one refused"
  ):
    val mi = ReferenceSignatures.MemberIndex(Map("Svg" -> Set("gen")))
    accepts(translate(block(ret(prop(thisKw, "gen"))), className = "Svg", memberIndex = mi))
    refuses(translate(block(ret(prop(thisKw, "svg"))), className = "Svg", memberIndex = mi), "wrong-member-access")

  test("an array literal builds the collection its slot declares"):
    assertNoDiff(
      translate(block(ret(node("ArrayLiteralExpression", num(1), num(2)))), returnType = Some("Vector[Int]")).scalaBody.trim,
      "scala.collection.immutable.Vector[Int](1, 2)"
    )
    assertNoDiff(
      translate(block(ret(node("ArrayLiteralExpression", num(1), str("a")))), returnType = Some("(Int, String)")).scalaBody.trim,
      "(1, \"a\")"
    )

  test("an array literal at a slot declaring a class is refused"):
    val cs = ReferenceSignatures.ConstructorSchema(
      Map("Point" -> List(ReferenceSignatures.CtorParam("x", "Double", false), ReferenceSignatures.CtorParam("y", "Double", false)))
    )
    refuses(
      translate(block(ret(node("ArrayLiteralExpression", num(1), num(2)))), returnType = Some("Point"), ctorSchema = cs),
      "array-literal-at-class-slot"
    )

  test("truthiness follows the declared type: NaN is falsy, an optional asks isDefined, an object asks for presence"):
    def cond(tpe: String) = translate(
      block(node("IfStatement", ident("v"), block(ret(num(1)))), ret(num(0))),
      params = List("v"),
      paramTypes = Map("v" -> tpe),
      returnType = Some("Int")
    ).scalaBody
    assert(cond("Double").contains("(v != 0 && !v.isNaN)"), cond("Double"))
    assert(cond("Option[Int]").contains("v.isDefined"), cond("Option[Int]"))
    assert(cond("Shape").contains("(v != null)"), cond("Shape"))

  test("`a && b` combines truthiness in a condition and is refused as a value"):
    val inCond = translate(
      block(node("IfStatement", bin("AmpersandAmpersandToken", ident("n"), ident("s")), block(ret(num(1)))), ret(num(0))),
      params = List("n", "s"),
      paramTypes = Map("n" -> "Int", "s" -> "String"),
      returnType = Some("Int")
    )
    accepts(inCond)
    assert(inCond.scalaBody.contains("(n != 0 && s.nonEmpty)"), inCond.scalaBody)
    refuses(
      translate(
        block(expr(call(ident("f"), bin("AmpersandAmpersandToken", ident("n"), ident("s"))))),
        params = List("n", "s", "f"),
        paramTypes = Map("n" -> "Int", "s" -> "String")
      ),
      "truthiness-value-context"
    )

  test("a Boolean slot reads a number's truthiness"):
    val r = translate(
      block(ret(bin("AmpersandToken", ident("flags"), ident("f")))),
      params = List("flags", "f"),
      paramTypes = Map("flags" -> "Int", "f" -> "Int"),
      returnType = Some("Boolean")
    )
    assertNoDiff(r.scalaBody.trim, "flags & f != 0")

  test("an assignment or a post-increment whose value is read is refused; as a statement each is kept"):
    refuses(translate(block(ret(bin("EqualsToken", ident("x"), num(1)))), params = List("x")), "assignment-as-value")
    refuses(translate(block(ret(postfixInc(ident("x")))), params = List("x")), "increment-as-value")
    accepts(
      translate(block(local("x", num(0)), expr(bin("EqualsToken", ident("x"), num(1))), expr(postfixInc(ident("x"))), ret(ident("x"))))
    )

  test("exponentiation is math.pow, and dividing two integers stays fractional"):
    assertNoDiff(translate(block(ret(bin("AsteriskAsteriskToken", num(2), num(31))))).scalaBody.trim, "math.pow(2, 31)")
    val div = translate(block(ret(bin("SlashToken", ident("a"), ident("b")))), params = List("a", "b"), paramTypes = Map("a" -> "Int", "b" -> "Int"))
    assertNoDiff(div.scalaBody.trim, "(a).toDouble / b")

  test("a fractional value at an integral slot is refused"):
    refuses(
      translate(
        block(ret(call(prop(ident("Math"), "floor"), ident("d")))),
        params = List("d"),
        paramTypes = Map("d" -> "Double"),
        returnType = Some("Int")
      ),
      "double-at-int-slot"
    )

  test("a local's type annotation is never its initialiser; an annotation naming no Scala type is refused"):
    val typed = translate(block(local("n", node("NumberKeyword"), num(0)), ret(ident("n"))))
    accepts(typed)
    assert(typed.scalaBody.contains(" n: Double = 0"), typed.scalaBody)
    refuses(
      translate(block(local("xs", node("ArrayType", node("NumberKeyword")), node("ArrayLiteralExpression")), ret(ident("xs")))),
      "local-type-annotation:ArrayType"
    )
    refuses(translate(block(local("v", RastNode("NullKeyword", 0, (0, 0))), ret(ident("v")))), "null-initialised-local")

  test("a name nothing declares is refused, a module member is qualified"):
    refuses(translate(block(ret(call(prop(ident("html"), "build"), num(1))))), "unresolved-identifier")
    assertNoDiff(translate(block(ret(ident("limit"))), calleeIndex = objectIndex("Consts", "limit")).scalaBody.trim, "Consts.limit")

  test("an object literal builds the class of its SLOT, and a key that class lacks is refused"):
    val cs  = ReferenceSignatures.ConstructorSchema(Map("Box" -> List(ReferenceSignatures.CtorParam("w", "Double", false))))
    val lit = node("ObjectLiteralExpression", node("PropertyAssignment", ident("w"), num(2)))
    assertNoDiff(translate(block(ret(lit)), returnType = Some("Box"), ctorSchema = cs).scalaBody.trim, "Box(w = 2)")
    refuses(
      translate(block(expr(call(ident("f"), lit))), params = List("f"), returnType = Some("Box"), ctorSchema = cs),
      "js-map-construction"
    )
    val extra = node("ObjectLiteralExpression", node("PropertyAssignment", ident("w"), num(2)), node("PropertyAssignment", ident("h"), num(1)))
    refuses(translate(block(ret(extra)), returnType = Some("Box"), ctorSchema = cs), "object-literal-unknown-field")

  test("object reflection — delete, for-in, Object.keys — is refused on anything but a map"):
    refuses(
      translate(block(expr(node("DeleteExpression", prop(ident("o"), "k")))), params = List("o"), paramTypes = Map("o" -> "Shape")),
      "delete-on-non-map"
    )
    val forIn = node("ForInStatement", node("VariableDeclarationList", node("VariableDeclaration", ident("k"))), ident("o"), block())
    refuses(translate(block(forIn), params = List("o"), paramTypes = Map("o" -> "Shape")), "for-in-over-object")
    refuses(
      translate(block(ret(call(prop(ident("Object"), "keys"), ident("o")))), params = List("o"), paramTypes = Map("o" -> "Shape")),
      "object-reflection"
    )
    accepts(
      translate(
        block(expr(node("DeleteExpression", prop(ident("m"), "k")))),
        params = List("m"),
        paramTypes = Map("m" -> "scala.collection.mutable.Map[String, Int]")
      )
    )

  test("indexing a declared class, or with a fractional index, is refused"):
    refuses(
      translate(block(ret(node("ElementAccessExpression", ident("p"), num(0)))), params = List("p"), paramTypes = Map("p" -> "Point")),
      "element-access-on-class"
    )
    refuses(
      translate(
        block(ret(node("ElementAccessExpression", ident("xs"), ident("d")))),
        params = List("xs", "d"),
        paramTypes = Map("xs" -> "Vector[Int]", "d" -> "Double")
      ),
      "fractional-index"
    )

  test("a method of an optional value, or one its declared type lacks, is refused"):
    val mi = ReferenceSignatures.MemberIndex(Map("Rng" -> Set("next")))
    refuses(
      translate(block(ret(call(prop(ident("r"), "next")))), params = List("r"), paramTypes = Map("r" -> "Nullable[Rng]"), memberIndex = mi),
      "optional-receiver"
    )
    refuses(
      translate(block(ret(call(prop(ident("r"), "skip")))), params = List("r"), paramTypes = Map("r" -> "Rng"), memberIndex = mi),
      "wrong-member-access"
    )
    accepts(translate(block(ret(call(prop(ident("r"), "next")))), params = List("r"), paramTypes = Map("r" -> "Rng"), memberIndex = mi))

  test("a present value stored into an optional slot is wrapped"):
    val r = translate(
      block(expr(bin("EqualsToken", prop(ident("o"), "rng"), node("NewExpression", ident("Rng"))))),
      params = List("o"),
      paramTypes = Map("o" -> "Opts"),
      ctorSchema = ReferenceSignatures.ConstructorSchema(Map("Opts" -> List(ReferenceSignatures.CtorParam("rng", "Nullable[Rng]", true)), "Rng" -> Nil)),
      memberIndex = ReferenceSignatures.MemberIndex(Map("Opts" -> Set("rng"), "Rng" -> Set.empty), Map("Opts" -> Set("rng")))
    )
    accepts(r)
    assert(r.scalaBody.contains("o.rng = Nullable(new Rng())"), r.scalaBody)

  test("storing into a field its class holds as a val is refused"):
    val mi = ReferenceSignatures.MemberIndex(Map("Drawable" -> Set("shape")))
    refuses(
      translate(
        block(expr(bin("EqualsToken", prop(ident("d"), "shape"), str("circle")))),
        params = List("d"),
        paramTypes = Map("d" -> "Drawable"),
        memberIndex = mi
      ),
      "assign-to-immutable-field"
    )

  test("a parameter the reference renamed is refused"):
    refuses(translate(block(ret(ident("type"))), params = List("type"), paramTypes = Map("tpe" -> "String")), "parameter-name-mismatch")

  test("`X.prototype.m.apply(other, args)` runs m on `other`, never a super call"):
    val applied = call(prop(prop(prop(ident("Array"), "prototype"), "push"), "apply"), ident("xs"), ident("ys"))
    refuses(translate(block(expr(applied)), params = List("xs", "ys")), "prototype-method-apply")

  test("the member index reads class and trait bodies, not only constructor parameters"):
    val src =
      """package p
        |final class Svg(config: Int) {
        |  private val gen: Int = 1
        |  def draw(x: Int): Int = x
        |}
        |object Svg { def make(): Svg = new Svg(1) }
        |""".stripMargin
    val (_, mi, _, _) = ReferenceSignatures.buildIndices(List("Svg" -> src))
    assert(mi.hasMember("Svg", "gen") && mi.hasMember("Svg", "draw") && mi.hasMember("Svg", "config"), mi.byType.toString)
    assert(!mi.hasMember("Svg", "make"), "an object's member is not an instance member")

  private val nodeLib = JsConventions(
    classPrefix = Some("N_" -> "N"),
    rootClass = Some("NBase"),
    typeTag = Some("KIND" -> "kind"),
    nodeFactories = Set("build"),
    methodRewrites = Map("measure" -> "Sizes.of($receiver)")
  )

  test("plain conventions read no name as special: a prefixed name is just an unresolved identifier"):
    refuses(translate(block(ret(ident("N_Leaf")))), "unresolved-identifier")
    assertNoDiff(translate(block(ret(ident("N_Leaf"))), conventions = nodeLib).scalaBody.trim, "NLeaf")

  test("a library's type tag, node factory and moved method are read through its conventions"):
    // the tag is compared as JS compares it: a subclass carrying its own tag is no `Leaf`, though it is an `NLeaf`
    val mi      = ReferenceSignatures.MemberIndex(Map("NBase" -> Set("kind"), "NLeaf" -> Set("kind", "v")), constants = Map(("kind", "Leaf") -> List("NLeaf")))
    val isLeaf  = bin("EqualsEqualsEqualsToken", prop(ident("x"), "KIND"), str("Leaf"))
    val tagTest = translate(block(ret(isLeaf)), params = List("x"), paramTypes = Map("x" -> "NBase"), memberIndex = mi, conventions = nodeLib)
    assertNoDiff(tagTest.scalaBody.trim, "x.kind == \"Leaf\"")
    refuses(translate(block(ret(isLeaf)), params = List("x"), conventions = nodeLib), "tag-on-untyped-receiver")
    // where the test holds, `x` is the one class declaring that tag
    val narrowed = translate(
      block(node("IfStatement", isLeaf, block(ret(prop(ident("x"), "v")))), ret(num(0))),
      params = List("x"),
      paramTypes = Map("x" -> "NBase"),
      returnType = Some("Int"),
      memberIndex = mi,
      conventions = nodeLib
    )
    accepts(narrowed)
    assert(narrowed.scalaBody.contains("x.asInstanceOf[NLeaf].v"), narrowed.scalaBody)
    val built = translate(
      block(
        ret(
          call(
            ident("build"),
            ident("N_Leaf"),
            ident("o"),
            node("ObjectLiteralExpression", node("PropertyAssignment", ident("v"), num(1)))
          )
        )
      ),
      params = List("o"),
      conventions = nodeLib
    )
    assertNoDiff(built.scalaBody.trim, "{ val $n = new NLeaf(); $n.v = 1; $n }")
    // what the factory takes from its origin node is the library's rule, not an assignment
    refuses(built, "node-factory-origin")
    assertNoDiff(
      translate(block(ret(call(prop(ident("x"), "measure")))), params = List("x"), conventions = nodeLib).scalaBody.trim,
      "Sizes.of(x)"
    )
    assertNoDiff(translate(block(ret(call(prop(ident("x"), "measure")))), params = List("x")).scalaBody.trim, "x.measure()")

  private def regex(text: String): RastNode = RastNode("RegularExpressionLiteral", 0, (0, 0), value = Some(RastValue.Str(text)))

  test("a class-body field's declared type is read, and `== null` on an optional field asks isEmpty"):
    val src =
      """package p
        |final class Parser(x: Int) {
        |  var next: Nullable[Token] = Nullable.Null
        |}
        |""".stripMargin
    val (ci, mi, cs, _) = ReferenceSignatures.buildIndices(List("Parser" -> src))
    val r               = translate(
      block(ret(bin("EqualsEqualsEqualsToken", prop(thisKw, "next"), RastNode("NullKeyword", 0, (0, 0))))),
      className = "Parser",
      calleeIndex = ci,
      memberIndex = mi,
      ctorSchema = cs,
      returnType = Some("Boolean")
    )
    accepts(r)
    assertNoDiff(r.scalaBody.trim, "this.next.isEmpty")
    refuses(
      translate(
        block(ret(prop(thisKw, "next"))),
        className = "Parser",
        calleeIndex = ci,
        memberIndex = mi,
        ctorSchema = cs,
        returnType = Some("Token")
      ),
      "optional-at-required-slot"
    )

  test(
    "String.replace replaces the first occurrence of a literal, every match of a /g regex, and refuses a replacement JS and Java read differently"
  ):
    def rep(pat: RastNode, r: String) = translate(block(ret(call(prop(ident("s"), "replace"), pat, str(r)))), params = List("s"), paramTypes = Map("s" -> "String"))
    assertNoDiff(rep(str("a"), "b").scalaBody.trim, "s.replaceFirst(java.util.regex.Pattern.quote(\"a\"), \"b\")")
    assertNoDiff(rep(regex("/x+/g"), "-$1").scalaBody.trim, "\"x+\".r.replaceAllIn(s, \"-$1\")")
    assertNoDiff(rep(regex("/x/i"), "y").scalaBody.trim, "\"(?i)x\".r.replaceFirstIn(s, \"y\")")
    refuses(rep(regex("/x/g"), "$&"), "replace-replacement-pattern")
    refuses(
      translate(
        block(ret(call(prop(ident("s"), "replace"), ident("re"), str("y")))),
        params = List("s", "re"),
        paramTypes = Map("s" -> "String", "re" -> "Regex")
      ),
      "replace-pattern-unknown"
    )

  test("String.split on a literal splits on its text and keeps trailing empty pieces"):
    val r = translate(block(ret(call(prop(ident("s"), "split"), str(".")))), params = List("s"), paramTypes = Map("s" -> "String"))
    assertNoDiff(r.scalaBody.trim, "s.split(java.util.regex.Pattern.quote(\".\"), -1)")

  test("a template substitutes each span; a Double or a value of unknown type is refused, since JS prints it differently"):
    def tmpl(tpe: String) =
      val t = node(
        "TemplateExpression",
        RastNode("TemplateHead", 0, (0, 0), value = Some(RastValue.Str("n="))),
        node("TemplateSpan", ident("n"), RastNode("TemplateTail", 0, (0, 0), value = Some(RastValue.Str("!"))))
      )
      translate(block(ret(t)), params = List("n"), paramTypes = Map("n" -> tpe))
    assertNoDiff(tmpl("Int").scalaBody.trim, "\"n=\" + n.toString + \"!\"")
    refuses(tmpl("Double"), "number-to-string")

  test("a JS built-in's static with no Scala namesake is refused; java.lang.Math is used only where it agrees"):
    refuses(translate(block(ret(call(prop(ident("Object"), "getPrototypeOf"), ident("o")))), params = List("o")), "js-builtin")
    refuses(translate(block(ret(call(prop(ident("Math"), "imul"), num(1), num(2))))), "js-builtin")
    accepts(translate(block(ret(call(prop(ident("Math"), "floor"), ident("d")))), params = List("d"), paramTypes = Map("d" -> "Double")))

  test("`{}` and `[]` where no slot declares their type are refused"):
    refuses(translate(block(expr(call(ident("f"), node("ObjectLiteralExpression")))), params = List("f")), "js-map-construction")
    refuses(translate(block(expr(call(ident("f"), node("ArrayLiteralExpression")))), params = List("f")), "untyped-empty-array")

  test("a type test of a generic class carries one wildcard per type parameter"):
    val (_, mi, _, _) = ReferenceSignatures.buildIndices(List("T" -> "package p\nclass Box[A, B](a: A)\n"))
    val r             = translate(
      block(ret(node("BinaryExpression", ident("x"), ident("Box")).copy(operator = Some("InstanceOfKeyword")))),
      params = List("x"),
      memberIndex = mi
    )
    assertNoDiff(r.scalaBody.trim, "x.isInstanceOf[Box[?, ?]]")

  test("a file's functions carry the class declaring each method"):
    val method = node("MethodDeclaration", ident("m"), block())
    val cls    = node("ClassDeclaration", ident("Shape"), method)
    val free   = node("FunctionDeclaration", ident("f"), block())
    val fns    = RastFunctions.of(RastFile(1, "x.ts", "", List(cls, free), Map.empty, Map.empty))
    assertEquals(fns.map(f => (f.owner, f.name)), List(("Shape", "m"), (RastFunctions.NoClass, "f")))

  test(
    "an argument JS leaves out is skipped where the parameter has a default, passed absent where it is optional, and refused otherwise"
  ):
    val sig = ReferenceSignatures.MethodSig(
      "f",
      List(
        ReferenceSignatures.ParamSig("a", "Int"),
        ReferenceSignatures.ParamSig("b", "Int", hasDefault = true),
        ReferenceSignatures.ParamSig("c", "Option[Int]")
      ),
      "Int"
    )
    val oracle = ReferenceSignatures.TypeOracle.fromEntries(List("Obj" -> sig))
    val r      = translate(block(ret(call(prop(ident("Obj"), "f"), num(1)))), oracle = oracle, calleeIndex = objectIndex("Obj", "f"))
    accepts(r)
    assertNoDiff(r.scalaBody.trim, "Obj.f(1, c = None)")
    val required = ReferenceSignatures.TypeOracle.fromEntries(List("Obj" -> sig.copy(params = sig.params.take(1) :+ ReferenceSignatures.ParamSig("d", "Int"))))
    refuses(
      translate(block(ret(call(prop(ident("Obj"), "f"), num(1)))), oracle = required, calleeIndex = objectIndex("Obj", "f")),
      "callee-arity-mismatch"
    )

  test("on a class the reference declares, a method keeps its own name: no JS collection rename applies"):
    val mi = ReferenceSignatures.MemberIndex(Map("Namespace" -> Set("has")))
    assertNoDiff(
      translate(block(ret(call(prop(ident("ns"), "has"), str("x")))), params = List("ns"), paramTypes = Map("ns" -> "Namespace"), memberIndex = mi).scalaBody.trim,
      "ns.has(\"x\")"
    )

  test("both branches of a conditional take the slot's type"):
    val r = translate(
      block(ret(node("ConditionalExpression", ident("b"), num(1), ident("undefined")))),
      params = List("b"),
      paramTypes = Map("b" -> "Boolean"),
      returnType = Some("Nullable[Int]")
    )
    assertNoDiff(r.scalaBody.trim, "(if (b) Nullable(1) else Nullable.Null)")

  private val checkerTypes = Map(
    "f" -> RastType("booleanLiteral", "false", value = Some(RastValue.Bool(false))),
    "t" -> RastType("booleanLiteral", "true", value = Some(RastValue.Bool(true))),
    "u" -> RastType("undefined", "undefined"),
    "b" -> RastType("union", "boolean", types = Some(List("f", "t"))),
    "bu" -> RastType("union", "boolean | undefined", types = Some(List("u", "f", "t"))),
    "s" -> RastType("string", "string"),
    "su" -> RastType("union", "string | undefined", types = Some(List("u", "s"))),
    "uu" -> RastType("union", "undefined", types = Some(List("u")))
  )

  private def typedIf(tpe: String): DefmethodBodyTranslator.TranslationResult =
    val cond = RastNode("PropertyAccessExpression", 0, (3, 8), children = List(ident("o"), ident("flag")), `type` = Some(tpe))
    DefmethodBodyTranslator.translateBody(
      DefmethodEntry("_free_", "test", List("o"), block(node("IfStatement", cond, block(ret(num(1)))), ret(num(2)))),
      Nil,
      returnType = Some("Int"),
      tsTypes = checkerTypes
    )

  test(
    "a condition the checker types as a boolean, absent or not, is its own truthiness; any other type nothing declares refuses"
  ):
    for tpe <- List("b", "bu") do
      val r = typedIf(tpe)
      assert(!r.refusalReasons.contains("truthiness-unknown-type"), s"$tpe: ${r.refusalReasons}")
      assert(r.scalaBody.contains("if (o.flag)"), r.scalaBody)
    for tpe <- List("s", "su", "uu") do refuses(typedIf(tpe), "truthiness-unknown-type")

  test("a refusal names the source line and column of the node it arose under"):
    val site = typedIf("s").refusalSites.find(_.reason == "truthiness-unknown-type")
    assertEquals(site.map(s => (s.line, s.column)), Some((3, 8)))
    assertEquals(site.map(_.render("src/a.ts")), Some("truthiness-unknown-type src/a.ts:3:8"))

  private val seqIndex = ReferenceSignatures.MemberIndex(Map("Node" -> Set.empty, "Seq" -> Set("items")))
  private def isSeq(x: String):   RastNode = bin("InstanceOfKeyword", ident(x), ident("Seq"))
  private def not(e:   RastNode): RastNode = RastNode("PrefixUnaryExpression", 0, (0, 0), children = List(node("ParenthesizedExpression", e)), operator = Some("ExclamationToken"))

  test(
    "an instanceof test narrows its local where it holds: the rest of the sequence after a failing exit, the right of an &&, a branch"
  ):
    val early = translate(
      block(node("IfStatement", not(isSeq("x")), ret(num(0))), ret(prop(ident("x"), "items"))),
      params = List("x"),
      paramTypes = Map("x" -> "Node"),
      memberIndex = seqIndex
    )
    accepts(early)
    assert(early.scalaBody.contains("x.asInstanceOf[Seq].items"), early.scalaBody)
    val conj = translate(
      block(ret(bin("AmpersandAmpersandToken", isSeq("x"), prop(ident("x"), "items")))),
      params = List("x"),
      paramTypes = Map("x" -> "Node"),
      memberIndex = seqIndex
    )
    assert(conj.scalaBody.contains("x.isInstanceOf[Seq] && x.asInstanceOf[Seq].items"), conj.scalaBody)

  test("no narrowing outside what the test proves, nor of a name the body reassigns"):
    refuses(
      translate(
        block(node("IfStatement", isSeq("x"), ret(num(0))), ret(prop(ident("x"), "items"))),
        params = List("x"),
        paramTypes = Map("x" -> "Node"),
        memberIndex = seqIndex
      ),
      "wrong-member-access"
    )
    val reassigned = translate(
      block(
        local("y", ident("x")),
        expr(bin("EqualsToken", ident("y"), ident("x"))),
        node("IfStatement", isSeq("y"), ret(prop(ident("y"), "items"))),
        ret(num(0))
      ),
      params = List("x"),
      paramTypes = Map("x" -> "Node"),
      memberIndex = seqIndex
    )
    assert(!reassigned.scalaBody.contains("asInstanceOf[Seq]"), reassigned.scalaBody)

  private def checkerTyped(tpe: RastType, member: String): DefmethodBodyTranslator.TranslationResult =
    val recv = RastNode("Identifier", 0, (0, 0), text = Some("tok"), `type` = Some("k"))
    DefmethodBodyTranslator.translateBody(
      DefmethodEntry("_free_", "test", List("tok"), block(ret(prop(recv, member)))),
      Nil,
      memberIndex = ReferenceSignatures.MemberIndex(Map("Token" -> Set("text"), "Box" -> Set("v"))),
      tsTypes = Map("k" -> tpe)
    )

  test(
    "a receiver nothing declares takes the checker's class when the reference declares that class: its members are validated against it"
  ):
    accepts(checkerTyped(RastType("object", "Token"), "text"))
    refuses(checkerTyped(RastType("object", "Token"), "txt"), "wrong-member-access")

  test(
    "a member read off a member read is checked against the type the reference declares for the inner one, AnyRef having none"
  ):
    val mi = ReferenceSignatures.MemberIndex(
      Map("Ctx" -> Set("parser", "opts"), "Opts" -> Set("size")),
      types = Map("Ctx" -> Map("parser" -> "AnyRef", "opts" -> "Opts"))
    )
    val read = (m: String, f: String) => translate(block(ret(prop(prop(ident("c"), m), f))), params = List("c"), paramTypes = Map("c" -> "Ctx"), memberIndex = mi)
    refuses(read("parser", "settings"), "wrong-member-access")
    refuses(read("opts", "colour"), "wrong-member-access")
    accepts(read("opts", "size"))

  test("a checker type that is a union, an unknown class or an anonymous shape says nothing"):
    for t <- List(RastType("union", "Token | undefined"), RastType("object", "Other"), RastType("object", "{ text: string; }")) do refuses(checkerTyped(t, "text"), "receiver-type-unknown")

  test("a member a parent class or trait declares is a member of the class, even one declaring nothing of its own"):
    val src           = "package p\ntrait Block extends Stmt {\n  var body: Int = 0\n}\ntrait Stmt\nclass BlockStatement extends Node with Block {}\nclass Node\n"
    val (_, mi, _, _) = ReferenceSignatures.buildIndices(List("Ast" -> src))
    val read          = (f: String) => translate(block(ret(prop(ident("b"), f))), params = List("b"), paramTypes = Map("b" -> "BlockStatement"), memberIndex = mi)
    assertNoDiff(read("body").scalaBody.trim, "b.body")
    refuses(read("bodies"), "wrong-member-access")
    assert(mi.isMutable("BlockStatement", "body"))
    assertEquals(mi.memberType("BlockStatement", "body"), Some("Int"))

  test(
    "a nested function carries the names its enclosing functions bind; a sibling's call reads the class the reference made of the enclosing function"
  ):
    val param   = node("Parameter", ident("text"))
    val sVar    = local("S", num(0))
    val isFn    = node("FunctionDeclaration", ident("is"), node("Parameter", ident("t")), block(ret(ident("t"))))
    val callIs  = block(ret(call(ident("is"), str("x"))))
    val nextFn  = node("FunctionDeclaration", ident("next"), callIs)
    val parseFn = node("FunctionDeclaration", ident("parse"), param, block(sVar, isFn, nextFn))
    val fns     = RastFunctions.of(RastFile(1, "x.js", "", List(parseFn), Map.empty, Map.empty))
    val next    = fns.find(_.name == "next").get
    assertEquals(next.outer, Set("text", "S", "is", "next"))
    assert(fns.find(_.name == "parse").get.outer.isEmpty)
    val mi       = ReferenceSignatures.MemberIndex(Map("Parser" -> Set("is", "next")))
    val moduleIs = objectIndex("Helpers", "is")
    val run      = (cls: Option[String]) =>
      DefmethodBodyTranslator.translateBody(
        DefmethodEntry(next.owner, next.name, next.params, next.body, next.outer),
        Nil,
        calleeIndex = moduleIs,
        memberIndex = mi,
        enclosingClass = cls
      )
    assertNoDiff(run(Some("Parser")).scalaBody.trim, "is(\"x\")")
    // the enclosing function's `is` shadows the module-level one: with no class to hold it, nothing can
    refuses(run(None), "enclosing-function-binding")

  private val pointSrc                              = "package p\nfinal case class Point(var x: Double, var y: Double)\nfinal case class Line(p1: Point, p2: Point)\n"
  private lazy val (_, pointMembers, pointCtors, _) = ReferenceSignatures.buildIndices(List("Geometry" -> pointSrc))
  private val records                               = JsConventions(tupleRecords = Map("Point" -> List("x", "y"), "Line" -> List("p1", "p2")))

  private def onPoints(body: RastNode, paramTypes: Map[String, String], returnType: Option[String] = None, conventions: JsConventions = records) =
    translate(
      body,
      params = paramTypes.keys.toList,
      paramTypes = paramTypes,
      returnType = returnType,
      memberIndex = pointMembers,
      ctorSchema = pointCtors,
      conventions = conventions
    )

  private def elem(recv: RastNode, i: Int): RastNode = node("ElementAccessExpression", recv, num(i))

  test("a tuple record's constant index reads its field, a store writes it, and an array literal at its slot builds it"):
    val read = onPoints(block(ret(bin("MinusToken", elem(elem(ident("l"), 0), 1), elem(elem(ident("l"), 1), 1)))), Map("l" -> "Line"))
    assertNoDiff(read.scalaBody.trim, "l.p1.y - l.p2.y")
    accepts(read)
    val store = onPoints(block(expr(bin("EqualsToken", elem(ident("p"), 0), num(1))), ret(ident("p"))), Map("p" -> "Point"), Some("Point"))
    accepts(store)
    assert(store.scalaBody.contains("p.x = 1"), store.scalaBody)
    refuses(
      onPoints(
        block(expr(bin("EqualsToken", elem(ident("l"), 0), ident("q"))), ret(ident("l"))),
        Map("l" -> "Line", "q" -> "Point"),
        Some("Line")
      ),
      "assign-to-immutable-field"
    )
    assertNoDiff(
      onPoints(block(ret(node("ArrayLiteralExpression", num(1), num(2)))), Map.empty, Some("Point")).scalaBody.trim,
      "Point(1, 2)"
    )
    // a position past the record's fields, and the same reads with no policy, stay refused
    refuses(onPoints(block(ret(elem(ident("p"), 2))), Map("p" -> "Point")), "element-access-on-class")
    refuses(onPoints(block(ret(elem(ident("p"), 0))), Map("p" -> "Point"), conventions = JsConventions.plain), "element-access-on-class")

  private def pattern(kind: String, names: String*):        RastNode = node(kind, names.map(n => node("BindingElement", ident(n)))*)
  private def destructuring(pat: RastNode, init: RastNode): RastNode =
    node(
      "VariableStatement",
      node("VariableDeclarationList", RastNode("VariableDeclaration", 0, (0, 0), children = List(pat, init), flags = List("const")))
    )

  test("array destructuring reads a tuple record's fields, the source evaluated once; any other source is refused"):
    val r = onPoints(
      block(destructuring(pattern("ArrayBindingPattern", "a", "b"), ident("p")), ret(ident("a"))),
      Map("p" -> "Point"),
      Some("Double")
    )
    accepts(r)
    assertNoDiff(r.scalaBody.trim, "{\n      val a = p.x\n      val b = p.y\n      a\n    }")
    val once = onPoints(block(destructuring(pattern("ArrayBindingPattern", "a", "b"), call(ident("make"))), ret(ident("a"))), Map.empty)
    refuses(once, "array-destructuring")
    refuses(
      onPoints(block(destructuring(pattern("ArrayBindingPattern", "a"), ident("xs")), ret(ident("a"))), Map("xs" -> "Vector[Double]")),
      "array-destructuring"
    )

  test("object destructuring evaluates its source once and refuses a default, a rest, or a field the class lacks"):
    val r = onPoints(
      block(destructuring(pattern("ObjectBindingPattern", "x"), call(prop(ident("l"), "first"))), ret(ident("x"))),
      Map("l" -> "Line")
    )
    assert(r.scalaBody.contains("val d$"), s"a call is bound once: ${r.scalaBody}")
    refuses(
      onPoints(block(destructuring(pattern("ObjectBindingPattern", "z"), ident("p")), ret(ident("z"))), Map("p" -> "Point")),
      "wrong-member-access"
    )
    val rest = node("ObjectBindingPattern", RastNode("BindingElement", 0, (0, 0), children = List(ident("others")), flags = List("rest")))
    refuses(onPoints(block(destructuring(rest, ident("p")), ret(ident("others"))), Map("p" -> "Point")), "destructuring-pattern")
    val renamed = node("ObjectBindingPattern", RastNode("BindingElement", 0, (0, 0), children = List(ident("x"), ident("px")), flags = List("renamed")))
    val ok      = onPoints(block(destructuring(renamed, ident("p")), ret(ident("px"))), Map("p" -> "Point"), Some("Double"))
    accepts(ok)
    assert(ok.scalaBody.contains("val px = p.x"), ok.scalaBody)

  test(
    "an object literal passed where the reference spells the options bag as parameters becomes named arguments; a key naming no parameter refuses"
  ):
    val sig = ReferenceSignatures.MethodSig(
      "extend",
      List(
        ReferenceSignatures.ParamSig("color", "String", hasDefault = true),
        ReferenceSignatures.ParamSig("size", "Int", hasDefault = true)
      ),
      "Opts"
    )
    val oracle = ReferenceSignatures.TypeOracle.fromEntries(List("Opts" -> sig))
    val mi     = ReferenceSignatures.MemberIndex(Map("Opts" -> Set("extend")))
    val bag    = (key: String) => node("ObjectLiteralExpression", node("PropertyAssignment", ident(key), ident("c")))
    val run    = (key: String) =>
      translate(
        block(ret(call(prop(thisKw, "extend"), bag(key)))),
        params = List("c"),
        paramTypes = Map("c" -> "String"),
        className = "Opts",
        memberIndex = mi,
        oracle = oracle
      )
    assertNoDiff(run("color").scalaBody.trim, "this.extend(color = c)")
    refuses(run("colour"), "js-map-construction")

  test("new Set and new Map of an iterable take its elements"):
    val r = translate(block(ret(node("NewExpression", ident("Set"), ident("xs")))), params = List("xs"), paramTypes = Map("xs" -> "Vector[Int]"))
    assertNoDiff(r.scalaBody.trim, "scala.collection.mutable.Set.from(xs)")

  test(
    "a last if with no else falls off the function's end: the else returns the absent value the result type spells, or refuses"
  ):
    val body = block(node("IfStatement", ident("c"), block(ret(ident("n")))))
    val r    = translate(body, params = List("c", "n"), paramTypes = Map("c" -> "Boolean", "n" -> "Node"), returnType = Some("Node | Null"))
    accepts(r)
    assert(r.scalaBody.linesIterator.map(_.trim).toList.containsSlice(List("} else {", "null", "}")), r.scalaBody)
    refuses(
      translate(body, params = List("c", "n"), paramTypes = Map("c" -> "Boolean", "n" -> "Int"), returnType = Some("Int")),
      "null-at-non-nullable-slot"
    )

  test("two objects compare by identity, as JS compares them; a string or a number by value"):
    val same = (t: String) => translate(block(ret(bin("EqualsEqualsEqualsToken", ident("a"), ident("b")))), params = List("a", "b"), paramTypes = Map("a" -> t, "b" -> t)).scalaBody.trim
    assertNoDiff(same("Node | Null"), "(a eq b)")
    assertNoDiff(same("ArrayBuffer[Int]"), "(a eq b)")
    assertNoDiff(same("String"), "a == b")
    assertNoDiff(same("Double"), "a == b")

  test("a JS method that reorders in place, clamps a string index or counts a bound from the end is refused"):
    def onXs(m: String, args: RastNode*) =
      translate(
        block(expr(call(prop(ident("xs"), m), args*))),
        params = List("xs", "i"),
        paramTypes = Map("xs" -> "ArrayBuffer[Int]", "i" -> "Int")
      )
    refuses(onXs("sort"), "js-in-place-reorder")
    refuses(onXs("reverse"), "js-in-place-reorder")
    refuses(onXs("slice", ident("i")), "js-slice")
    assert(onXs("slice", num(1)).scalaBody.contains("xs.drop(1)"), onXs("slice", num(1)).scalaBody)

  test("a local nothing writes is a val whatever JS keyword declared it; one a nested function writes stays a var"):
    val decl = (n: String) =>
      RastNode(
        "VariableStatement",
        0,
        (0, 0),
        children = List(
          node("VariableDeclarationList", RastNode("VariableDeclaration", 0, (0, 0), children = List(ident(n), num(1)), flags = List("let")))
        )
      )
    assert(translate(block(decl("a"), ret(ident("a")))).scalaBody.contains("val a = 1"))
    val bump = node("ArrowFunction", block(expr(bin("PlusEqualsToken", ident("a"), num(1)))))
    assert(translate(block(decl("a"), expr(bump), ret(ident("a")))).scalaBody.contains("var a = 1"))
    val onS = translate(block(ret(call(prop(ident("s"), "charAt"), num(0)))), params = List("s"), paramTypes = Map("s" -> "String"))
    refuses(onS, "js-string-index")

  test("an object literal carrying the type tag builds the one class declaring that tag, where the slot admits it"):
    val src            = "package p\nsealed trait Node {\n  def kind: String\n}\nfinal case class Group(var body: Int = 0) extends Node {\n  override def kind: String = \"group\"\n}\n"
    val (_, mi, cs, _) = ReferenceSignatures.buildIndices(List("Nodes" -> src))
    val lit            = (tag: String) =>
      node(
        "ObjectLiteralExpression",
        node("PropertyAssignment", ident("type"), str(tag)),
        node("PropertyAssignment", ident("body"), num(1))
      )
    val conv = JsConventions(typeTag = Some("type" -> "kind"))
    val r    = translate(block(ret(lit("group"))), returnType = Some("Node"), memberIndex = mi, ctorSchema = cs, conventions = conv)
    accepts(r)
    assertNoDiff(r.scalaBody.trim, "Group(body = 1)")
    refuses(
      translate(block(ret(lit("other"))), returnType = Some("Node"), memberIndex = mi, ctorSchema = cs, conventions = conv),
      "js-map-construction"
    )

  test(
    "a condition on a call the reference declares Boolean on every class reads as its value; any other agreed result is still unknown"
  ):
    val mi = ReferenceSignatures.MemberIndex(
      Map("A" -> Set("isDone", "size"), "B" -> Set("isDone", "size")),
      results = Map("A" -> Map("isDone" -> "Boolean", "size" -> "Int"), "B" -> Map("isDone" -> "Boolean", "size" -> "Int"))
    )
    val cond = (m: String) =>
      translate(
        block(node("IfStatement", call(prop(call(ident("pick")), m)), block(ret(num(1)))), ret(num(0))),
        returnType = Some("Int"),
        memberIndex = mi
      )
    assert(!cond("is_done").refusalReasons.contains("truthiness-unknown-type"), cond("is_done").refusalReasons.toString)
    refuses(cond("size"), "truthiness-unknown-type")

  test("an empty branch is no reason to fail: the early-return scan reads it as returning nothing"):
    val r = translate(block(node("IfStatement", ident("c"), block())), params = List("c"), paramTypes = Map("c" -> "Boolean"), returnType = Some("Unit"))
    accepts(r)

  test("JS reads past an array's end as undefined: a read of reference elements answers null there, a store stays an update"):
    val read = translate(
      block(ret(node("ElementAccessExpression", ident("xs"), ident("i")))),
      params = List("xs", "i"),
      paramTypes = Map("xs" -> "ArrayBuffer[Node]", "i" -> "Int")
    )
    assertNoDiff(read.scalaBody.trim, "xs.lift(i).orNull")
    val store = translate(
      block(expr(bin("EqualsToken", node("ElementAccessExpression", ident("xs"), ident("i")), ident("n")))),
      params = List("xs", "i", "n"),
      paramTypes = Map("xs" -> "ArrayBuffer[Node]", "i" -> "Int", "n" -> "Node")
    )
    assert(store.scalaBody.contains("xs(i) = n"), store.scalaBody)
    refuses(
      translate(block(ret(node("ElementAccessExpression", ident("s"), num(0)))), params = List("s"), paramTypes = Map("s" -> "String")),
      "js-string-index"
    )

  private def lengthOf(r: RastNode):                  RastNode = prop(r, "length")
  private def ifThen(c:   RastNode, body: RastNode*): RastNode = node("IfStatement", c, block(body*))

  test("a read of primitive elements is refused unless a length test on the same receiver guards it"):
    val ints = Map("xs" -> "ArrayBuffer[Int]", "ys" -> "ArrayBuffer[Int]", "i" -> "Int")
    val read = (r: String) => node("ElementAccessExpression", ident(r), ident("i"))
    refuses(translate(block(ret(read("xs"))), params = List("xs", "i"), paramTypes = ints, returnType = Some("Int")), "read-past-end")
    val guarded = translate(
      block(ifThen(bin("LessThanToken", ident("i"), lengthOf(ident("xs"))), ret(read("xs"))), ret(num(0))),
      params = List("xs", "i"),
      paramTypes = ints,
      returnType = Some("Int")
    )
    accepts(guarded)
    // a test on ANOTHER receiver guards nothing
    refuses(
      translate(
        block(ifThen(bin("LessThanToken", ident("i"), lengthOf(ident("ys"))), ret(read("xs"))), ret(num(0))),
        params = List("xs", "ys", "i"),
        paramTypes = ints,
        returnType = Some("Int")
      ),
      "read-past-end"
    )

  test("a map read answers null for an absent key of reference values, and needs a has test otherwise"):
    val refs = translate(
      block(ret(call(prop(ident("m"), "get"), ident("k")))),
      params = List("m", "k"),
      paramTypes = Map("m" -> "mutable.Map[String, Node]", "k" -> "String")
    )
    assertNoDiff(refs.scalaBody.trim, "m.getOrElse(k, null)")
    val nums = Map("m" -> "mutable.Map[String, Int]", "k" -> "String")
    refuses(
      translate(block(ret(call(prop(ident("m"), "get"), ident("k")))), params = List("m", "k"), paramTypes = nums, returnType = Some("Int")),
      "read-past-end"
    )
    // the has test guards the read (the condition's own truthiness is another question)
    val guarded = translate(
      block(ifThen(call(prop(ident("m"), "has"), ident("k")), ret(call(prop(ident("m"), "get"), ident("k")))), ret(num(0))),
      params = List("m", "k"),
      paramTypes = nums,
      returnType = Some("Int")
    )
    assert(!guarded.refusalReasons.contains("read-past-end"), guarded.refusalReasons.toString)
    assert(guarded.scalaBody.contains("m(k)"), guarded.scalaBody)

  test("pop and shift on an empty array answer null for references, and need a length test otherwise"):
    val nodes = translate(block(ret(call(prop(ident("xs"), "pop")))), params = List("xs"), paramTypes = Map("xs" -> "ArrayBuffer[Node]"))
    assertNoDiff(nodes.scalaBody.trim, "{ val b$ = xs; if (b$.isEmpty) null else b$.remove(b$.length - 1) }")
    val ints = Map("xs" -> "ArrayBuffer[Int]")
    refuses(
      translate(block(ret(call(prop(ident("xs"), "shift")))), params = List("xs"), paramTypes = ints, returnType = Some("Int")),
      "read-past-end"
    )
    accepts(
      translate(
        block(ifThen(lengthOf(ident("xs")), ret(call(prop(ident("xs"), "shift")))), ret(num(0))),
        params = List("xs"),
        paramTypes = ints,
        returnType = Some("Int")
      )
    )

  test("a pop nothing reads removes only when there is something to remove"):
    val r = translate(block(expr(call(prop(ident("xs"), "pop")))), params = List("xs"), paramTypes = Map("xs" -> "ArrayBuffer[Int]"))
    accepts(r)
    assertNoDiff(r.scalaBody.trim, "{ val b$ = xs; if (b$.nonEmpty) b$.remove(b$.length - 1) }")
