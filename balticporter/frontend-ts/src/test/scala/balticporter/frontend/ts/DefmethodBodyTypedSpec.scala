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
    val tagTest = translate(block(ret(bin("EqualsEqualsEqualsToken", prop(ident("x"), "KIND"), str("Leaf")))), params = List("x"), conventions = nodeLib)
    assertNoDiff(tagTest.scalaBody.trim, "x.isInstanceOf[NLeaf]")
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
    assertNoDiff(
      translate(block(ret(call(prop(ident("x"), "measure")))), params = List("x"), conventions = nodeLib).scalaBody.trim,
      "Sizes.of(x)"
    )
    assertNoDiff(translate(block(ret(call(prop(ident("x"), "measure")))), params = List("x")).scalaBody.trim, "x.measure()")
