package balticporter.transform

import balticporter.tir.*

/** A JUnit suite → a CROSS-PLATFORM Scala suite.
  *
  * A ported test suite is the only behavioural evidence this engine can produce, and a JUnit one
  * runs on the JVM alone — neither Scala.js nor Scala Native has JUnit. Emitting Java's tests as
  * JUnit-in-Scala therefore yields a gate that cannot execute on the platforms the port EXISTS for,
  * while looking like full coverage: 221 discovered tests, zero of them runnable on the target.
  *
  * Universal, not per-library (PLAN §1a): every Java library ported to cross-platform Scala hits it.
  * What differs per project is only the TARGET framework, which is the constructor parameter.
  *
  * ==Why a façade rather than rewriting the assertions==
  *
  * The obvious translation rewrites every `Assert.assertEquals(expected, actual)` into MUnit's
  * `assertEquals(obtained, expected)`. That is 558 call sites in libGDX alone, each needing an
  * argument SWAP, and each newly subject to MUnit's type constraint (`B <:< A`) which Java's
  * `assertEquals(Object, Object)` does not have — so calls mixing `int`/`long`/`Object` that
  * compile today would stop compiling.
  *
  * Instead the suite gains a BASE CLASS that re-declares JUnit's assertions with Java's own
  * argument order and loose typing, implemented over the target framework. The call sites then do
  * not move at all, and the semantics are preserved by construction rather than by 558 careful
  * edits. Pointing `suite` at a different base class retargets the whole thing.
  *
  * ==Why `testCase(name, body)` and not `test(name)(body)`==
  *
  * MUnit's `test` takes two argument LISTS, and the TIR has no node for a curried application. The
  * base class exposes an un-curried `testCase` that forwards to it, so this transform builds one
  * ordinary `Apply` and the emitter needs no change: `Tree.ClassDef.body` is `List[Statement]`
  * already, so a bare call in a class body emits as-is.
  *
  * @param suite      fully-qualified base class the ported suites extend
  * @param testMember un-curried `(name, body)` registration member on that base class
  */
final class TestFrameworkTransform(
    suite: String = TestFrameworkTransform.DefaultSuite,
    testMember: String = "test",
) extends Phase:

  def name: String = "junit->portable-suite"

  private val AssertClass = "org.junit.Assert"
  private val AssertsObject = "balticporter.runtime.Asserts"
  private val AssertMembers = Set("assertEquals", "assertNotEquals", "assertTrue", "assertFalse",
    "assertNull", "assertNotNull", "assertSame", "assertArrayEquals", "fail")
  private val TestAnn   = "org.junit.Test"
  private val BeforeAnn = "org.junit.Before"

  private var suiteSym: SymId  = SymId.None
  private var testSym: SymId   = SymId.None
  /** `org.junit.Assert.assertX` → the façade's own `assertX`, by simple name. */
  private var assertSyms: Map[String, SymId] = Map.empty

  override def run(program: Program): Program =
    var next = program.symbols.all.map(_.id.raw).maxOption.getOrElse(-1) + 1
    val added = collection.mutable.ListBuffer[Symbol]()
    def mint(nm: String, full: String): SymId =
      val id = SymId(next); next += 1
      added += Symbol(id, nm, full, Flags(), SymId.None, TypeRepr.NoType)
      id
    suiteSym = mint(suite.substring(suite.lastIndexOf('.') + 1), suite)
    testSym  = mint(testMember, testMember)  // MUnit's own `test`, applied CURRIED
    // keyed by the member's SIMPLE name: a static call renders as `<receiver FQN>.<name>`, so the
    // member symbol itself is not keyed by the owner's FQN and cannot be found that way.
    // fully-qualified to an OBJECT, not inherited from the base class. A java `static` helper
    // emits into the COMPANION object, which does not extend the suite, so inherited assertions are
    // invisible there (`Not found: assertTrue`). An object member resolves the same from both.
    assertSyms = AssertMembers.map(nm => nm -> mint(nm, AssertsObject + "." + nm)).toMap

    val symbols = SymbolTable(program.symbols.all ++ added)
    given Program = new Program(program.units, symbols, program.xref)
    new Program(program.units.map(convert), symbols, program.xref)

  /** `org.junit.Assert.assertEquals(a, b)` → `assertEquals(a, b)`, resolving to the façade member
    * inherited from the base suite. The arguments do not move — that is the whole point of
    * re-declaring java's shapes there rather than rewriting 872 call sites into MUnit's own
    * `(obtained, expected)` order. Same mechanism as [[StaticForwarderTransform]]: a wrapper's
    * statics become plain members. */
  override def transformApply(t: Tree.Apply)(using p: Program): Term = t.fun match
    case Tree.Select(recv, m, _, o) if recvIs(recv, AssertClass) =>
      val nm = p.symbolOf(m).map(_.name).getOrElse("")
      assertSyms.get(nm).map(id => t.copy(fun = Tree.Ident(id, TypeRepr.NoType, o), method = id)).getOrElse(t)
    case _ => t

  private def recvIs(recv: Term, fqn: String)(using p: Program): Boolean = recv match
    case Tree.Ident(s, _, _)     => p.symbolOf(s).exists(_.fullName == fqn)
    case Tree.Select(_, s, _, _) => p.symbolOf(s).exists(_.fullName == fqn)
    case _                       => false

  /** A class is a SUITE when it declares at least one `@Test` member. Nested classes are converted
    * too — libGDX nests helper suites — so the walk is explicit rather than top-level only. */
  private def convert(cd: Tree.ClassDef)(using p: Program): Tree.ClassDef =
    val nested = cd.body.map {
      case c: Tree.ClassDef => convert(c)
      case other            => other
    }
    val cd2 = cd.copy(body = nested)
    if !nested.exists(isAnnotated(_, TestAnn)) then cd2
    else
      // Rewrite `Assert.assertX` to the façade member ONLY inside a class that becomes a suite —
      // it is the base class that supplies those members. Rewriting program-wide (via a traversal
      // in `run`) un-qualified the calls in helper classes that never gained the base class, and
      // they failed with `Not found: assertTrue`.
      val body = StandardTraversal.mapClassDef(this, cd2).body.flatMap {
        case d: Tree.DefDef if isAnnotated(d, TestAnn)   => List(testCase(d))
        case d: Tree.DefDef if isAnnotated(d, BeforeAnn) => List(beforeEach(d))
        case other                                       => List(other)
      }
      cd2.copy(parents = TypeTree(TypeRepr.TypeRef(TypeRepr.NoPrefix, suiteSym), cd.origin) :: cd2.parents,
               body = body)

  private def isAnnotated(s: Statement, fqn: String)(using p: Program): Boolean = s match
    case d: Tree.DefDef =>
      p.symbolOf(d.symbol).exists(_.annotations.exists(a => nameOf(a.tpe) == fqn))
    case _ => false

  private def nameOf(t: TypeRepr)(using p: Program): String = t match
    case TypeRepr.TypeRef(_, s)      => p.symbolOf(s).map(_.fullName).getOrElse("")
    case TypeRepr.AppliedType(tc, _) => nameOf(tc)
    case _                           => ""

  /** `@Test def m(): Unit = { … }` → `testCase("m", { … })`, a statement in the class body.
    *
    * An `expected = classOf[E]` argument becomes `intercept[E] { … }` — NOT dropped. A test that
    * asserts an exception and instead runs the body bare would PASS while checking nothing, which
    * is the silent-omission shape this engine exists to prevent. Until `intercept` is wired the
    * method is left alone, so such a test stays a compile error rather than a false green. */
  private def testCase(d: Tree.DefDef)(using p: Program): Statement =
    val nm = p.symbolOf(d.symbol).map(_.name).getOrElse("test")
    val expectsThrow = p.symbolOf(d.symbol).exists(_.annotations.exists { a =>
      nameOf(a.tpe) == TestAnn && a.args.exists(_._1 == "expected")
    })
    if expectsThrow || d.rhs.isEmpty then d
    else
      // `test("name") { … }` — TWO argument lists, modelled the way `quotes.reflect` does: nested
      // `Apply`, since `Apply.fun` is itself a `Term`. An earlier version routed around this via an
      // un-curried forwarder in an injected base class, on the false belief that the IR could not
      // express currying. The IR follows quotes/BeTASTy and models any correct scala tree; the
      // forwarder was a scaffold built over a gap that did not exist.
      val lit = Tree.Literal(Constant.StringC(nm), TypeRepr.NoType, d.origin)
      val head = Tree.Apply(Tree.Ident(testSym, TypeRepr.NoType, d.origin), List(lit),
                            testSym, TypeRepr.NoType, d.origin)
      Tree.Apply(head, List(d.rhs.get), testSym, TypeRepr.NoType, d.origin)

  /** `@Before` is the framework's per-test setup hook under a fixed name. */
  private def beforeEach(d: Tree.DefDef)(using p: Program): Statement = d

object TestFrameworkTransform:
  val DefaultSuite = "munit.FunSuite"
  /** Only the ASSERTIONS remain injected: java's argument order and loose typing differ from
    * MUnit's `(obtained, expected)` with `B <:< A`. That is still shape-adaptation the transform
    * should do itself — see LIBGDX-PORT-STATUS.md — so this too is interim. */
  val AssertsObjectFqn = "balticporter.runtime.Asserts"

  /** The façade: JUnit's assertions with JAVA's argument order and loose typing, over MUnit. */
  val runtimeSources: Map[String, String] = Map(
    AssertsObjectFqn ->
      """package balticporter.runtime
        |
        |/** JUnit's assertions, in JAVA's argument order and with java's loose typing.
        |  *
        |  * An OBJECT, not members of a base class: a java `static` helper emits into the COMPANION
        |  * object, which does not extend the suite, so inherited assertions are invisible exactly
        |  * where java put half of them.
        |  *
        |  * INTERIM. Re-declaring shapes the engine could emit correctly is not what injected
        |  * sources are for — they exist for semantics the target language LACKS. MUnit's own
        |  * `assertEquals(obtained, expected)` differs from java's only by argument order and a
        |  * `B <:< A` constraint, both of which the transform can resolve because it knows the
        |  * operand types. See LIBGDX-PORT-STATUS.md.
        |  */
        |object Asserts:
        |  private def check(cond: Boolean, msg: => String): Unit =
        |    if !cond then throw new AssertionError(msg)
        |
        |  def fail(): Nothing                = throw new AssertionError("failed")
        |  def fail(message: String): Nothing = throw new AssertionError(message)
        |
        |  def assertEquals(expected: Any, actual: Any): Unit =
        |    check(expected == actual, s"expected <$expected> but was <$actual>")
        |  def assertEquals(message: String, expected: Any, actual: Any): Unit =
        |    check(expected == actual, message)
        |  def assertEquals(expected: Long, actual: Long): Unit =
        |    check(expected == actual, s"expected <$expected> but was <$actual>")
        |  def assertEquals(expected: Double, actual: Double): Unit =
        |    check(expected == actual, s"expected <$expected> but was <$actual>")
        |  def assertEquals(expected: Double, actual: Double, delta: Double): Unit =
        |    check(math.abs(expected - actual) <= delta, s"expected <$expected> but was <$actual>")
        |  def assertEquals(message: String, expected: Double, actual: Double, delta: Double): Unit =
        |    check(math.abs(expected - actual) <= delta, message)
        |  def assertNotEquals(unexpected: Any, actual: Any): Unit =
        |    check(unexpected != actual, s"did not expect <$unexpected>")
        |
        |  def assertTrue(b: Boolean): Unit                   = check(b, "expected true")
        |  def assertTrue(message: String, b: Boolean): Unit  = check(b, message)
        |  def assertFalse(b: Boolean): Unit                  = check(!b, "expected false")
        |  def assertFalse(message: String, b: Boolean): Unit = check(!b, message)
        |  def assertNull(o: Any): Unit                       = check(o == null, s"expected null, was <$o>")
        |  def assertNotNull(o: Any): Unit                    = check(o != null, "expected non-null")
        |  def assertSame(expected: AnyRef, actual: AnyRef): Unit =
        |    check(expected eq actual, "expected the same instance")
        |
        |  def assertArrayEquals(expected: Array[Byte], actual: Array[Byte]): Unit =
        |    check(expected.sameElements(actual), "arrays differ")
        |  def assertArrayEquals(expected: Array[Short], actual: Array[Short]): Unit =
        |    check(expected.sameElements(actual), "arrays differ")
        |  def assertArrayEquals(expected: Array[Int], actual: Array[Int]): Unit =
        |    check(expected.sameElements(actual), "arrays differ")
        |  def assertArrayEquals(expected: Array[Long], actual: Array[Long]): Unit =
        |    check(expected.sameElements(actual), "arrays differ")
        |  def assertArrayEquals(expected: Array[Char], actual: Array[Char]): Unit =
        |    check(expected.sameElements(actual), "arrays differ")
        |  def assertArrayEquals(expected: Array[Object], actual: Array[Object]): Unit =
        |    check(expected.sameElements(actual), "arrays differ")
        |  def assertArrayEquals(expected: Array[Float], actual: Array[Float], delta: Float): Unit =
        |    check(expected.length == actual.length &&
        |            expected.indices.forall(i => math.abs(expected(i) - actual(i)) <= delta),
        |          "arrays differ")
        |  def assertArrayEquals(message: String, expected: Array[Float], actual: Array[Float],
        |                        delta: Float): Unit =
        |    check(expected.length == actual.length &&
        |            expected.indices.forall(i => math.abs(expected(i) - actual(i)) <= delta), message)
        |  def assertArrayEquals(expected: Array[Double], actual: Array[Double], delta: Double): Unit =
        |    check(expected.length == actual.length &&
        |            expected.indices.forall(i => math.abs(expected(i) - actual(i)) <= delta),
        |          "arrays differ")
        |  def assertArrayEquals(message: String, expected: Array[Double], actual: Array[Double],
        |                        delta: Double): Unit =
        |    check(expected.length == actual.length &&
        |            expected.indices.forall(i => math.abs(expected(i) - actual(i)) <= delta), message)
        |""".stripMargin,
  )
