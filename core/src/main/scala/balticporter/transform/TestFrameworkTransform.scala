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
    testMember: String = "testCase",
) extends Phase:

  def name: String = "junit->portable-suite"

  private val AssertClass = "org.junit.Assert"
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
    testSym  = mint(testMember, testMember)
    // keyed by the member's SIMPLE name: a static call renders as `<receiver FQN>.<name>`, so the
    // member symbol itself is not keyed by the owner's FQN and cannot be found that way.
    assertSyms = AssertMembers.map(nm => nm -> mint(nm, nm)).toMap

    val symbols = SymbolTable(program.symbols.all ++ added)
    given Program = new Program(program.units, symbols, program.xref)
    // StandardTraversal FIRST so the term hooks (`transformApply`) actually run — walking the units
    // directly, as this did, silently skips every term-level hook. PLAN §3: walk with the standard
    // traversal, never a private recursion.
    new Program(program.units.map(u => convert(StandardTraversal.mapClassDef(this, u))), symbols, program.xref)

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
      val body = nested.flatMap {
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
      val lit = Tree.Literal(Constant.StringC(nm), TypeRepr.NoType, d.origin)
      Tree.Apply(Tree.Ident(testSym, TypeRepr.NoType, d.origin), List(lit, d.rhs.get),
                 testSym, TypeRepr.NoType, d.origin)

  /** `@Before` is the framework's per-test setup hook under a fixed name. */
  private def beforeEach(d: Tree.DefDef)(using p: Program): Statement = d

object TestFrameworkTransform:
  val DefaultSuite = "balticporter.runtime.PortedSuite"

  /** The façade: JUnit's assertions with JAVA's argument order and loose typing, over MUnit. */
  val runtimeSources: Map[String, String] = Map(
    DefaultSuite ->
      """package balticporter.runtime
        |
        |/** A ported JUnit suite's base class.
        |  *
        |  * It re-declares JUnit's assertions rather than asking the port to rewrite them, for two
        |  * reasons. MUnit's `assertEquals` takes `(obtained, expected)` — the REVERSE of JUnit's —
        |  * so a mechanical rename would silently invert every failure message; and MUnit's is
        |  * type-constrained (`B <:< A`), which java's `assertEquals(Object, Object)` is not, so
        |  * calls mixing `int`/`long`/`Object` would stop compiling. Keeping java's shapes here
        |  * preserves the ported assertions exactly and leaves the call sites untouched.
        |  *
        |  * `testCase` is un-curried because MUnit's `test(name)(body)` is two argument lists and
        |  * the porting engine has no node for a curried application.
        |  */
        |abstract class PortedSuite extends munit.FunSuite:
        |
        |  def testCase(name: String, body: => Unit): Unit = test(name)(body)
        |
        |  def assertEquals(expected: Any, actual: Any): Unit =
        |    assert(expected == actual, s"expected <$expected> but was <$actual>")
        |  def assertEquals(message: String, expected: Any, actual: Any): Unit =
        |    assert(expected == actual, message)
        |  def assertEquals(expected: Double, actual: Double, delta: Double): Unit =
        |    assert(math.abs(expected - actual) <= delta, s"expected <$expected> but was <$actual>")
        |  def assertNotEquals(unexpected: Any, actual: Any): Unit =
        |    assert(unexpected != actual, s"did not expect <$unexpected>")
        |  def assertTrue(b: Boolean): Unit                  = assert(b)
        |  def assertTrue(message: String, b: Boolean): Unit = assert(b, message)
        |  def assertFalse(b: Boolean): Unit                 = assert(!b)
        |  def assertFalse(message: String, b: Boolean): Unit = assert(!b, message)
        |  def assertNull(o: Any): Unit                      = assert(o == null, s"expected null, was <$o>")
        |  def assertNotNull(o: Any): Unit                   = assert(o != null, "expected non-null")
        |  def assertSame(expected: AnyRef, actual: AnyRef): Unit =
        |    assert(expected eq actual, "expected the same instance")
        |
        |  def assertArrayEquals(expected: Array[Byte], actual: Array[Byte]): Unit =
        |    assert(expected.sameElements(actual), "arrays differ")
        |  def assertArrayEquals(expected: Array[Int], actual: Array[Int]): Unit =
        |    assert(expected.sameElements(actual), "arrays differ")
        |  def assertArrayEquals(expected: Array[Long], actual: Array[Long]): Unit =
        |    assert(expected.sameElements(actual), "arrays differ")
        |  def assertArrayEquals(expected: Array[Char], actual: Array[Char]): Unit =
        |    assert(expected.sameElements(actual), "arrays differ")
        |  def assertArrayEquals(expected: Array[Object], actual: Array[Object]): Unit =
        |    assert(expected.sameElements(actual), "arrays differ")
        |  // JUnit's `fail()` and `fail(String)`; MUnit's `fail` demands a message and a Location.
        |  def fail(): Nothing                = super.fail("failed")
        |  override def fail(message: String)(implicit loc: munit.Location): Nothing = super.fail(message)
        |
        |  def assertEquals(expected: Long, actual: Long): Unit =
        |    assert(expected == actual, s"expected <$expected> but was <$actual>")
        |  def assertEquals(expected: Double, actual: Double): Unit =
        |    assert(expected == actual, s"expected <$expected> but was <$actual>")
        |  def assertArrayEquals(expected: Array[Short], actual: Array[Short]): Unit =
        |    assert(expected.sameElements(actual), "arrays differ")
        |  def assertArrayEquals(expected: Array[Double], actual: Array[Double], delta: Double): Unit =
        |    assert(expected.length == actual.length &&
        |             expected.indices.forall(i => math.abs(expected(i) - actual(i)) <= delta),
        |           "arrays differ")
        |  def assertArrayEquals(expected: Array[Float], actual: Array[Float], delta: Float): Unit =
        |    assert(expected.length == actual.length &&
        |             expected.indices.forall(i => math.abs(expected(i) - actual(i)) <= delta),
        |           "arrays differ")
        |""".stripMargin,
  )
