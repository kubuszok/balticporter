package balticporter.corpus

import balticporter.emit.TirEmitter
import balticporter.frontend.spoon.SpoonTir
import balticporter.tir.{Pipeline, PortabilityCheck}
import balticporter.transform.TestFrameworkTransform

/** The JUnit-4 → MUnit conversion, per translated construct.
  *
  * Every case here is a CLAUDE.md §4.4 defect: it compiles either way, so a compile proves
  * nothing. Each construct therefore gets two checks — the emitted Scala (what the transform
  * built) and, for `@After` and `@Ignore`, a live MUnit test running the SAME shape, so the claim
  * "teardown runs after a failing test" and "an ignored body does not execute" are asserted
  * behaviourally rather than asserted about a string.
  */
class TestFrameworkTransformSpec extends munit.FunSuite:

  private def emit(java: String): (String, TestFrameworkTransform) =
    val ph    = new TestFrameworkTransform
    val after = Pipeline.run(SpoonTir.fromSource(java), List(ph))
    (new TirEmitter(after).emit, ph)

  private val lifecycleSrc =
    """package demo;
      |import org.junit.After;
      |import org.junit.Before;
      |import org.junit.Test;
      |import static org.junit.Assert.assertEquals;
      |public class LifecycleTest {
      |  private StringBuilder log;
      |  @Before public void setUp() { log = new StringBuilder(); }
      |  @After public void tearDown() { log = null; }
      |  @Test public void one() { assertEquals(1, 1); }
      |  @Test(expected = IllegalStateException.class) public void boom() { throw new IllegalStateException(); }
      |}
      |""".stripMargin

  // ---------------------------------------------------------------- @After --

  test("@After becomes try/finally, not a trailing call") {
    val (out, _) = emit(lifecycleSrc)
    // the ONLY faithful shape: a body that throws must still run teardown.
    assert(clue(out).contains("test(\"one\")(try {"))
    assert(out.contains("} finally tearDown())"))
    // and it must NOT have degenerated into "append the call at the end of the body".
    assert(!out.contains("assertEquals(1, 1)\n    tearDown()"))
  }

  test("@After nests OUTSIDE @Before and outside the expected-exception check, as JUnit does") {
    val (out, _) = emit(lifecycleSrc)
    // JUnit's statement chain is afters(befores(expectException(invoke))) — so a setUp that throws
    // still runs teardown, and the intercept sits innermost.
    val boom = out.substring(out.indexOf("test(\"boom\")"))
    assert(clue(boom).contains("try {"))
    assert(boom.indexOf("setUp()") < boom.indexOf("intercept["))
    assert(boom.indexOf("intercept[") < boom.indexOf("finally tearDown()"))
  }

  test("every converted test leaves a §1(a) row NAMING THE INLINED LIFECYCLE — the invisible half") {
    // `Pipeline.runTraced`, not `run`: the latter drains each phase's buffer into a log it discards.
    val log = Pipeline.runTraced(SpoonTir.fromSource(lifecycleSrc), List(new TestFrameworkTransform))._2
    val ds  = log.of(balticporter.tir.Decision.Kind.RetypedSignature).sortBy(_.subjectFqn)
    assertEquals(ds.map(_.subjectFqn), List("demo.LifecycleTest#boom", "demo.LifecycleTest#one"))
    assert(ds.forall(_.reason == balticporter.tir.Reason.Universal("test-framework")))

    // `test("one") { … }` obviously came from a `@Test`. That setUp and tearDown were INLINED into
    // its body is exactly what the emitted file cannot tell you was a DECISION — and it is where
    // both of §4.4's lifecycle defects lived.
    assert(ds.forall(_.detail("inlined") == "setUp, tearDown"), clue(ds.map(_.detail("inlined"))))
    // and `@Test(expected = …)` is recorded as the intercept it became, not lost in the body
    assertEquals(ds.head.detail("intercept"), "java.lang.IllegalStateException")
    assertEquals(ds.last.detail("intercept"), "")
    assert(ds.forall(_.detail("ignored") == "no"))
  }

  test("a class with no @Test records nothing — the phase converts nothing and says nothing") {
    val log = Pipeline.runTraced(
      SpoonTir.fromSource("package demo;\nclass Plain { public void one() {} }\n"),
      List(new TestFrameworkTransform))._2
    assertEquals(log.all, Nil)
  }

  test("the consumed @Before/@After annotations do not survive into the emitted suite") {
    val (out, _) = emit(lifecycleSrc)
    // leaving them would re-import a JVM-only library into the very suite this phase exists to
    // make cross-platform — and would read as though JUnit still drives the methods.
    assert(!clue(out).contains("@org.junit.Before"))
    assert(!out.contains("@org.junit.After"))
    // the methods themselves must stay: the test bodies call them.
    assert(out.contains("def setUp()") && out.contains("def tearDown()"))
  }

  private var afterRan = false
  test("BEHAVIOUR: the emitted try/finally runs teardown after a FAILING test") {
    def tearDown(): Unit = afterRan = true
    // literally the shape asserted above.
    intercept[RuntimeException] {
      try throw new RuntimeException("the test failed")
      finally tearDown()
    }
    assert(afterRan, "teardown was skipped by a failing test — state would leak into the next one")
  }

  // --------------------------------------------------------------- @Ignore --

  private val ignoreSrc =
    """package demo;
      |import org.junit.Ignore;
      |import org.junit.Test;
      |import static org.junit.Assert.assertEquals;
      |public class IgnoreTest {
      |  @Test public void live() { assertEquals(1, 1); }
      |  @Ignore @Test public void broken() { assertEquals(1, 2); }
      |}
      |""".stripMargin

  test("@Ignore registers a DISABLED test, not an enabled one") {
    val (out, _) = emit(ignoreSrc)
    // `munit.TestOptions(...)` rather than `"name".ignore`: the latter needs MUnit's implicit
    // String conversion, and this phase emits fully-qualified names with no imports (CLAUDE.md §6).
    assert(clue(out).contains("test(munit.TestOptions(\"broken\").ignore)"))
    // the un-ignored one is untouched, and the ignored one is NOT also registered plainly.
    assert(out.contains("test(\"live\")"))
    assert(!out.contains("test(\"broken\")"))
    // the body is kept — it still has to compile; MUnit simply never evaluates it. Note the
    // arguments SWAPPED: java asserts `(expected, actual)`, MUnit `(obtained, expected)`.
    assert(out.contains("munit.Assertions.assertEquals(2, 1)"))
  }

  test("@Ignore on the CLASS disables every test it declares") {
    val (out, _) = emit(
      """package demo;
        |import org.junit.Ignore;
        |import org.junit.Test;
        |@Ignore public class AllOffTest {
        |  @Test public void a() { }
        |  @Test public void b() { }
        |}
        |""".stripMargin)
    assert(clue(out).contains("test(munit.TestOptions(\"a\").ignore)"))
    assert(out.contains("test(munit.TestOptions(\"b\").ignore)"))
    assert(!out.contains("@org.junit.Ignore"))
  }

  private var ignoredBodyRan = false
  // exactly the shape emitted for `@Ignore @Test public void broken()`. Registering it here also
  // proves the emitted form COMPILES against MUnit with no import and no implicit conversion.
  test(munit.TestOptions("BEHAVIOUR: an @Ignore'd test body must never execute").ignore) {
    ignoredBodyRan = true
  }
  test("BEHAVIOUR: the ignored body above did not run") {
    assert(!ignoredBodyRan, "an ignored test executed — @Ignore turned into a live gate")
  }

  // ------------------------------------------- @BeforeClass / @AfterClass --

  test("@BeforeClass / @AfterClass become beforeAll / afterAll overrides on the suite") {
    val (out, _) = emit(
      """package demo;
        |import org.junit.AfterClass;
        |import org.junit.BeforeClass;
        |import org.junit.Test;
        |public class OnceTest {
        |  @BeforeClass public static void once() { }
        |  @AfterClass public static void done() { }
        |  @Test public void a() { }
        |}
        |""".stripMargin)
    // java `static`, so the method emits into the companion and the override calls it through it.
    assert(clue(out).contains("override def beforeAll(): scala.Unit = OnceTest.once()"))
    assert(out.contains("override def afterAll(): scala.Unit = OnceTest.done()"))
    assert(!out.contains("@org.junit.BeforeClass"))
  }

  test("a suite with no class-level hooks gains no overrides") {
    val (out, _) = emit(ignoreSrc)
    assert(!clue(out).contains("beforeAll"))
    assert(!out.contains("afterAll"))
  }

  // ------------------------------------------------------------ assertions --

  /** One suite exercising every `org.junit.Assert` shape the corpus resolves, plus the two the
    * corpus does not (`assertSame`/`assertNotSame`) — a mapping is only as good as its coverage,
    * and an unmapped member is silently left on JUnit. */
  private val assertSrc =
    """package demo;
      |import org.junit.Assert;
      |import org.junit.Test;
      |public class AssertShapesTest {
      |  static void staticHelper(int a) { Assert.assertEquals(1, a); Assert.fail("helper"); }
      |  @Test public void shapes() {
      |    Object o = "x";
      |    String s = "x";
      |    java.util.List<Object> lo = new java.util.ArrayList<Object>();
      |    java.util.List<String> ls = new java.util.ArrayList<String>();
      |    long l = 1L;
      |    int i = 1;
      |    char c = 'a';
      |    boolean b = true;
      |    Assert.assertEquals(1, i);
      |    Assert.assertEquals("why", 1, i);
      |    Assert.assertEquals(l, i);
      |    Assert.assertEquals(i, l);
      |    Assert.assertEquals(c, i);
      |    Assert.assertEquals(o, s);
      |    Assert.assertEquals(ls, lo);
      |    Assert.assertEquals(1.0f, 2.0f, 0.5f);
      |    Assert.assertEquals("why", 1.0d, 2.0d, 0.5d);
      |    Assert.assertNotEquals(1, i);
      |    Assert.assertTrue(b);
      |    Assert.assertTrue("why", b);
      |    Assert.assertFalse(b);
      |    Assert.assertNull(o);
      |    Assert.assertNotNull(o);
      |    Assert.assertSame(o, s);
      |    Assert.assertNotSame(o, s);
      |    Assert.assertArrayEquals(new long[] {1}, new long[] {1});
      |    Assert.assertArrayEquals(new float[] {1}, new float[] {1}, 0.5f);
      |    Assert.fail();
      |    Assert.fail("why");
      |  }
      |}
      |""".stripMargin

  test("java's (expected, actual) becomes MUnit's (obtained, expected), and message becomes clue") {
    val (out, _) = emit(assertSrc)
    // the permutation is the whole job: emitted in java's order every failure message would name
    // the wrong side, and every `assertEquals(expected, actual)` diff would read backwards.
    assert(clue(out).contains("munit.Assertions.assertEquals(i, 1)"))
    assert(out.contains("""munit.Assertions.assertEquals(i, 1, "why")"""))
  }

  test("a mixed-numeric comparison WIDENS THE NARROWER operand — 26 of the 33 errors") {
    val (out, _) = emit(assertSrc)
    // java promoted these at the call; MUnit's `assertEquals[A, B]` infers each operand on its own,
    // so nothing drives scala's widening and the pair is rejected.
    assert(clue(out).contains("munit.Assertions.assertEquals(i.toLong, l)"))
    assert(out.contains("munit.Assertions.assertEquals(l, i.toLong)"))
    // Char and Short widen to neither each other nor one another's rank — java promotes to Int.
    assert(out.contains("munit.Assertions.assertEquals(i, c.toInt)"))
    // and an EQUAL-typed pair is left alone: a `.toLong` on both sides would be noise.
    assert(out.contains("munit.Assertions.assertEquals(i, 1)"))
  }

  test("an UNRELATED REFERENCE pair re-applies java's OTHER widening — assertEquals[Object, Object]") {
    val (out, _) = emit(assertSrc)
    // Java resolved `assertEquals(Object, Object)` here and WIDENED both operands; MUnit's
    // `Compare[A, B]` needs the two to relate, and two invariant `java.util.List`s at different
    // element types do not. Same rule as the numeric promotion above, at the other overload —
    // written as the call's type arguments, which is what java's signature said.
    assert(clue(out).contains(
      "munit.Assertions.assertEquals[java.lang.Object, java.lang.Object](lo, ls)"))
    // A ROOT on either side takes it too: `Compare[A, Object]` resolves for every `A`, so MUnit's
    // constraint is ALREADY VACUOUS there and writing java's widening down costs no check — while
    // reading a root as "the two types agree" is what would decline the one pair that needs it.
    assert(out.contains("munit.Assertions.assertEquals[java.lang.Object, java.lang.Object](s, o)"))
    // …and where MUnit's constraint IS a check, it is kept: an equal, non-root pair, and every
    // numeric pair, which `promote` above owns.
    assert(out.contains("munit.Assertions.assertEquals(i, 1)"))
    assert(!out.contains("assertEquals[java.lang.Object, java.lang.Object](i,"))
    assert(!out.contains("assertEquals[java.lang.Object, java.lang.Object](b,"))
  }

  test("the delta overloads become assertEqualsFloat / assertEqualsDouble, by WIDTH") {
    val (out, _) = emit(assertSrc)
    assert(clue(out).contains("munit.Assertions.assertEqualsFloat(2.0f, 1.0f, 0.5f)"))
    assert(out.contains("""munit.Assertions.assertEqualsDouble(2.0, 1.0, 0.5, "why")"""))
  }

  test("assertTrue/False/Null/NotNull/Same/NotSame/fail each map to what they MEAN") {
    val (out, _) = emit(assertSrc)
    assert(clue(out).contains("munit.Assertions.assert(b)"))
    assert(out.contains("""munit.Assertions.assert(b, "why")"""))
    assert(out.contains("munit.Assertions.assertEquals(b, false)"))
    assert(out.contains("munit.Assertions.assertEquals(o, null)"))
    assert(out.contains("munit.Assertions.assertNotEquals(o, null)"))
    // REFERENCE identity: scala's `==` is java's `equals` (CLAUDE.md §4.4), so assertEquals here
    // would silently weaken the assertion into a value comparison that usually still passes.
    assert(out.contains("munit.Assertions.assert(s eq o)"))
    assert(out.contains("munit.Assertions.assert(s ne o)"))
    // MUnit has no no-argument `fail`.
    assert(out.contains("""munit.Assertions.fail("failed")"""))
    assert(out.contains("""munit.Assertions.fail("why")"""))
  }

  test("assertArrayEquals compares SEQUENCES; with a delta it becomes the loop it means") {
    val (out, _) = emit(assertSrc)
    assert(clue(out).contains(".toSeq, scala.Array[scala.Long](1).toSeq)"))
    // The one junit assertion MUnit has no counterpart for. Dropping the delta would make it
    // STRICTER and fail tests that pass in java; comparing `.toSeq` is exactly that mistake.
    assert(out.contains("munit.Assertions.assertEqualsFloat(bpObtained"))
    assert(out.contains(".indices)"))
    // both operands are bound to locals FIRST: they are arbitrary expressions, and naming each
    // once is the difference between java's ONE evaluation and one per element.
    assert(out.contains("val bpObtained") && out.contains("val bpExpected"))
    // junit checks the lengths before the elements, and reports a size mismatch as one.
    assert(out.contains(".length, bpExpected"))
  }

  /** JUnit's assertion statics live at THREE FQNs, not one.
    *
    * `junit.framework.Assert` is JUnit 3's, `junit.framework.TestCase` inherits it, and a JUnit-4
    * suite reaches either through `import static junit.framework.TestCase.assertEquals` — an
    * ordinary `@Test` class with no `TestCase` parent, so `survey`'s JUnit-3 scan correctly says
    * nothing and the calls are the only trace. Their argument order, their optional leading
    * `String message` and their minimal arity are `org.junit.Assert`'s exactly, so one table maps
    * all three; gating on one FQN left the other two emitting `junit.framework.*` into a suite this
    * phase exists to make cross-platform.
    *
    * Written with an IMPORT and a simple-name receiver, as `assertSrc` is: `fromSource` builds with
    * `noClasspath`, so in a one-file snippet an inline `junit.framework.TestCase.assertEquals(…)`
    * is an unresolvable name chain rather than a type access and the receiver carries no FQN at all.
    * A model over a whole source tree resolves both forms — and liqp's own shape, a static import,
    * is a third that only a real classpath resolves (see `transformApply`'s note). */
  private val junit3StaticsSrc =
    """package demo;
      |import junit.framework.Assert;
      |import junit.framework.TestCase;
      |import org.junit.Test;
      |public class NodeTest {
      |  @Test public void a() {
      |    TestCase.assertEquals(7, 8);
      |    Assert.assertEquals("why", 9, 10);
      |  }
      |}
      |""".stripMargin

  test("junit.framework.TestCase / junit.framework.Assert statics map through the SAME table") {
    val (out, ph) = emit(junit3StaticsSrc)
    // same permutation, same clue position — these ARE org.junit.Assert's members, inherited.
    assert(clue(out).contains("munit.Assertions.assertEquals(8, 7)"))
    assert(out.contains("""munit.Assertions.assertEquals(10, 9, "why")"""))
    // and nothing junit-shaped survives: left alone these compile only with junit on the classpath
    // and cannot run on Scala.js / Native.
    assert(!out.contains("junit.framework"))
    assertEquals(ph.findings.map(_.render), Nil)
  }

  test("an unmapped member of ANY of the three assertion classes is reported under ITS OWN name") {
    // the finding names the receiver the call actually had — reported under a class the source
    // never mentions, an agent cannot find the site.
    val (_, ph) = emit(
      """package demo;
        |import junit.framework.Assert;
        |import org.junit.Test;
        |public class OddTest {
        |  @Test public void a() { Assert.assertEquals("m", 1.0d, 2.0d, 3.0d, 4.0d); }
        |}
        |""".stripMargin)
    assert(clue(ph.findings.map(_.construct)).contains("junit.framework.Assert.assertEquals"))
  }

  // ------------------------------------------------------------- assertThrows --

  private val throwsSrc =
    """package demo;
      |import org.junit.Assert;
      |import org.junit.Test;
      |public class ThrowsTest {
      |  @Test public void a() {
      |    Assert.assertThrows(IllegalStateException.class, () -> { throw new IllegalStateException("x"); });
      |  }
      |}
      |""".stripMargin

  test("assertThrows becomes intercept[E] — the SAME assertion @Test(expected=…) already becomes") {
    val (out, ph) = emit(throwsSrc)
    // JUnit 4.13's assertThrows asserts exactly what `intercept` asserts, and returns the throwable
    // exactly as `intercept` does. Left unmapped the call stays on org.junit and the suite is
    // JVM-only — and this is the ONE junit assertion a test HELPER typically carries.
    assert(clue(out).contains("munit.Assertions.intercept[java.lang.IllegalStateException]"))
    assert(!out.contains("assertThrows"))
    assertEquals(ph.findings.map(_.render), Nil)
  }

  test("assertThrows is qualified to munit.Assertions, not the suite's inherited `intercept`") {
    // it is an ASSERTION, so it is rewritten in every scope — a java `static` helper included,
    // which emits into the COMPANION OBJECT and does not extend the suite, so an inherited
    // `intercept` is not in scope there (the same `Not found:` the assertion members hit).
    // `@Test(expected=…)`'s intercept is built only inside a class that gains the parent, and
    // stays inherited.
    val (out, _) = emit(
      """package demo;
        |import org.junit.Assert;
        |import org.junit.Test;
        |public class QualTest {
        |  static void mustThrow(Runnable r) { Assert.assertThrows(RuntimeException.class, () -> r.run()); }
        |  @Test public void a() { mustThrow(null); }
        |}
        |""".stripMargin)
    val companion = out.substring(out.indexOf("object QualTest"))
    assert(clue(companion).contains("munit.Assertions.intercept[java.lang.RuntimeException](r.run())"))
  }

  test("assertThrows with a MESSAGE is refused, not silently stripped of it") {
    // MUnit's `intercept[T](body)` has no clue slot, so junit's leading `String message` has
    // nowhere to go. Emitting the intercept anyway would drop a diagnostic the author wrote, with
    // nothing counting the loss; the refusal keeps the call on org.junit where `PortabilityCheck`
    // already counts it, and says why.
    val (out, ph) = emit(
      """package demo;
        |import org.junit.Assert;
        |import org.junit.Test;
        |public class MsgThrowsTest {
        |  @Test public void a() {
        |    Assert.assertThrows("why", IllegalStateException.class, () -> { throw new IllegalStateException(); });
        |  }
        |}
        |""".stripMargin)
    val f = ph.findings.find(_.construct == "org.junit.Assert.assertThrows")
    assertEquals(f.map(_.fix.label), Some("a"))
    assert(clue(f.map(_.advice)).exists(_.contains("message")))
    assert(!clue(out).contains("munit.Assertions.intercept"))
  }

  test("assertThrows whose runnable is NOT a lambda is refused — the value is not the call") {
    // `intercept[E] { r }` EVALUATES `r` and never runs it: the assertion would pass or fail on
    // whether constructing the runnable threw. There is no shape to derive the invocation from
    // without naming `ThrowingRunnable#run`, so this is a refusal.
    val (out, ph) = emit(
      """package demo;
        |import org.junit.Assert;
        |import org.junit.Test;
        |import org.junit.function.ThrowingRunnable;
        |public class RefThrowsTest {
        |  ThrowingRunnable r;
        |  @Test public void a() { Assert.assertThrows(IllegalStateException.class, r); }
        |}
        |""".stripMargin)
    assert(clue(ph.findings.map(_.construct)).contains("org.junit.Assert.assertThrows"))
    assert(!clue(out).contains("munit.Assertions.intercept"))
  }

  // ------------------------------------------ the rewrite is not SUITE-scoped --

  /** A test HELPER declares no `@Test` — that is what makes it a helper — and it is where a suite's
    * assertions are most often centralised. Gating the `Assert` rewrite on the class declaring a
    * `@Test` meant those calls were never even visited. */
  private val helperSrc =
    """package demo;
      |import org.junit.Assert;
      |public class TestUtils {
      |  public static void check(int a) { Assert.assertEquals(1, a); }
      |  public static void boom() { Assert.fail("nope"); }
      |}
      |""".stripMargin

  test("a helper class with NO @Test still has its Assert calls rewritten") {
    val (out, _) = emit(helperSrc)
    assert(clue(out).contains("munit.Assertions.assertEquals(a, 1)"))
    assert(out.contains("""munit.Assertions.fail("nope")"""))
    assert(!out.contains("org.junit"))
  }

  test("…and it does NOT become a suite: only the CONVERSION stays gated on @Test") {
    val (out, ph) = emit(helperSrc)
    // the gate that had to survive: a helper is not a test class, so it must not gain the parent,
    // and nothing in it may be registered as a test.
    assert(!clue(out).contains("munit.FunSuite"))
    assert(!out.contains("test(\""))
    assertEquals(ph.findings.map(_.render), Nil)
  }

  test("a NESTED suite's assertions are rewritten exactly ONCE — one walk, one finding") {
    // the walk used to run per converted class, so an outer suite re-walked its already-converted
    // nested one. Idempotent for the rewrites, NOT for the findings: an unmapped member inside a
    // nested suite was reported twice, and the "UNTRANSLATED constructs" headline over-counted.
    val (_, ph) = emit(
      """package demo;
        |import org.junit.Assert;
        |import org.junit.Test;
        |public class OuterTest {
        |  @Test public void o() { }
        |  public static class InnerTest {
        |    @Test public void i() { Assert.assertEquals("m", 1.0d, 2.0d, 3.0d, 4.0d); }
        |  }
        |}
        |""".stripMargin)
    assertEquals(ph.findings.count(_.construct == "org.junit.Assert.assertEquals"), 1)
  }

  test("a java STATIC helper resolves — the 6 remaining errors, and why an object was chosen") {
    val (out, _) = emit(assertSrc)
    // `static` emits into the COMPANION object, which does not extend the suite: an assertion
    // inherited from `munit.FunSuite` is simply not in scope there (`Not found: assertEquals`).
    // Every assertion is therefore emitted through the `munit.Assertions` OBJECT, which resolves
    // identically from a suite body, a companion, a nested class and a lambda — so the helper does
    // not have to move onto the suite, and no name of it can collide with MUnit's own members.
    val companion = out.substring(out.indexOf("object AssertShapesTest"))
    assert(clue(companion).contains("munit.Assertions.assertEquals(a, 1)"))
    assert(companion.contains("""munit.Assertions.fail("helper")"""))
  }

  // -------------------------------------------------------------------------------------------
  // …and the phase SHAPES SIGNATURES, so it is comparable across two modules' pipelines
  // -------------------------------------------------------------------------------------------

  test("two differently-configured instances do NOT compare equal — this phase is a SurfacePolicy") {
    // Without this the fingerprint is the phase NAME, and two modules emitting suites with two
    // DIFFERENT parents compare identical: `SurfaceMissing` sees nothing, and a same-name pair can
    // be neither compared nor composed. Both parameters are in it because both are surface — the
    // parent a converted suite gains, and the member every `@Test` becomes a call to.
    val fp = (p: TestFrameworkTransform) => balticporter.core.PortManifest.fingerprint(p)
    assertNotEquals(fp(new TestFrameworkTransform(suite = "a.A")), fp(new TestFrameworkTransform(suite = "b.B")))
    assertNotEquals(fp(new TestFrameworkTransform(testMember = "test")),
                    fp(new TestFrameworkTransform(testMember = "testCase")))
    // …and two instances of one configuration DO, or two modules that agree would report drift.
    assertEquals(fp(new TestFrameworkTransform), fp(new TestFrameworkTransform))
    assert(clue(fp(new TestFrameworkTransform)).contains(TestFrameworkTransform.DefaultSuite),
           "the fingerprint names the parent, which is the thing a dependent compiles against")
  }

  test("NOTHING is injected alongside the port — the Asserts façade is gone") {
    val (out, _) = emit(assertSrc)
    assert(!clue(out).contains("balticporter.runtime"))
    // and the transform no longer offers sources to write: a migrator cannot re-introduce them.
    assert(!TestFrameworkTransform.getClass.getMethods.exists(_.getName == "runtimeSources"))
  }

  // BEHAVIOUR: the emitted shapes, run. Each is a §4.4 defect — every one of them compiles either
  // way, so only executing them says whether the translation kept java's meaning.

  test("BEHAVIOUR: the emitted permutation names the right side in a failure") {
    val e = intercept[munit.ComparisonFailException] {
      // exactly `assertEquals(EXPECTED 1, ACTUAL 2)` as emitted: obtained first.
      munit.Assertions.assertEquals(2, 1)
    }
    // MUnit's diff is `- expected, + obtained`; emitted in java's order this would read backwards
    // on every failing assertion in the port.
    assert(clue(e.getMessage).contains("-1"))
    assert(e.getMessage.contains("+2"))
  }

  test("BEHAVIOUR: the widened comparison still compares VALUES, not widths") {
    val i: Int = 1
    val l: Long = 1L
    munit.Assertions.assertEquals(i.toLong, l)
    munit.Assertions.assertEquals(l, i.toLong)
    val c: Char = 1.toChar
    munit.Assertions.assertEquals(i, c.toInt)
    intercept[munit.ComparisonFailException](munit.Assertions.assertEquals(2.toLong, l))
  }

  test("BEHAVIOUR: the REFERENCE widening still compares CONTENTS, exactly as java's did") {
    // `assertEquals[Object, Object]` is what java's `assertEquals(Object, Object)` was: the
    // comparison is still `equals`, so two lists that hold the same elements are still equal and
    // two that do not are still not. Widening the STATIC types must not weaken the assertion into
    // a reference check, which is the one way this rewrite could pass while checking nothing.
    val lo: java.util.List[Object] = java.util.Arrays.asList[Object]("a", "b")
    val ls: java.util.List[String] = java.util.Arrays.asList("a", "b")
    munit.Assertions.assertEquals[java.lang.Object, java.lang.Object](lo, ls)
    assert(!(lo eq ls))
    val other: java.util.List[String] = java.util.Arrays.asList("a", "c")
    intercept[munit.ComparisonFailException](
      munit.Assertions.assertEquals[java.lang.Object, java.lang.Object](lo, other))
  }

  test("BEHAVIOUR: assertSame maps to `eq`, which is NOT `==`") {
    val a = new String("x")
    val b = new String("x")
    munit.Assertions.assertEquals(a, b)                       // java's assertEquals: equal values
    intercept[munit.FailException](munit.Assertions.assert(a eq b)) // java's assertSame: NOT same
  }

  test("BEHAVIOUR: the array-with-delta loop accepts what junit accepted") {
    val bpObtained0: Array[Float] = Array(1.0f, 2.0f)
    val bpExpected0: Array[Float] = Array(1.4f, 2.0f)
    munit.Assertions.assertEquals(bpObtained0.length, bpExpected0.length)
    for (bpIndex0 <- bpObtained0.indices)
      munit.Assertions.assertEqualsFloat(bpObtained0(bpIndex0), bpExpected0(bpIndex0), 0.5f)
    // and rejects what it rejected — a `.toSeq` comparison would have failed the block above.
    intercept[munit.ComparisonFailException](munit.Assertions.assertEqualsFloat(1.0f, 1.4f, 0.1f))
  }

  // -------------------------------------- @Rule ExpectedException -> the RULE, MODELLED --
  //
  // JUnit's other spelling of `@Test(expected = …)`, and the one with a state machine behind it.
  // Every case below is a CLAUDE.md §4.4 defect: the untranslated form COMPILES and the test simply
  // fails at run time, so the only evidence is the emitted shape, the BEHAVIOUR specs at the end of
  // this block, and — for the refusals — the guard that declined it (`refused = 0` is a bar met by
  // converting nothing, §3).
  //
  // The junit and hamcrest declarations are supplied as SOURCES because `fromSource` builds with
  // `noClasspath`: which of `expect`'s two overloads java resolved is read from the CALLEE'S OWN
  // FORMAL, and an external member with no class file behind it carries no signature at all — that
  // is exactly the state the phase DECLINES on, so a snippet without them would assert the refusal
  // path while claiming to test the conversion.

  private val junitRuleStubs: List[(String, String)] = List(
    "ExpectedException.java" ->
      """package org.junit.rules;
        |public class ExpectedException {
        |  public static ExpectedException none() { return new ExpectedException(); }
        |  public void expect(Class<? extends Throwable> type) { }
        |  public void expect(org.hamcrest.Matcher<?> matcher) { }
        |  public void expectMessage(String substring) { }
        |  public void expectMessage(org.hamcrest.Matcher<String> matcher) { }
        |  public void expectCause(org.hamcrest.Matcher<?> matcher) { }
        |}
        |""".stripMargin,
    "Matcher.java" ->
      """package org.hamcrest;
        |public interface Matcher<T> {
        |  boolean matches(Object item);
        |}
        |""".stripMargin,
    "IsAnything.java" ->
      """package org.hamcrest;
        |public class IsAnything implements Matcher<Object> {
        |  public boolean matches(Object item) { return true; }
        |  public static IsAnything anything() { return new IsAnything(); }
        |}
        |""".stripMargin)

  private def emitWithRules(java: String): (String, TestFrameworkTransform) =
    val ph    = new TestFrameworkTransform
    val after = Pipeline.run(SpoonTir.fromSources(("Snippet.java" -> java) :: junitRuleStubs), List(ph))
    (new TirEmitter(after).emit, ph)

  private def ruleSuite(body: String): String =
    s"""package demo;
       |import org.junit.After;
       |import org.junit.Rule;
       |import org.junit.Test;
       |import org.junit.rules.ExpectedException;
       |public class RuleSuite {
       |  @Rule public ExpectedException thrown = ExpectedException.none();
       |$body
       |}
       |""".stripMargin

  /** The guard each declined site named. NOT spelled with a `#`: that separator is `MemberKey`'s
    * grammar and `PolicyKeyLintSpec` enforces that no phase rebuilds it from a string — a refusal
    * KIND is not a member reference, however much it looks like one. */
  private def guards(ph: TestFrameworkTransform): List[String] =
    ph.findings.map(_.construct).collect {
      case c if c.startsWith("org.junit.rules.ExpectedException(") =>
        c.stripPrefix("org.junit.rules.ExpectedException(").stripSuffix(")")
    }

  test("thrown.expect(E.class) ARMS junit's own matcher list, in place") {
    val (out, ph) = emitWithRules(ruleSuite(
      """  @Test public void a() {
        |    int x = 1;
        |    thrown.expect(IllegalStateException.class);
        |    boom(x);
        |  }
        |  static void boom(int x) { throw new IllegalStateException(); }""".stripMargin))
    val t = out.substring(out.indexOf("test(\"a\")"))
    // the rule call is GONE — an emitted `thrown.expect(…)` is the defect this closes.
    assert(!clue(t).contains("thrown.expect"))
    assert(t.contains("bpExpected = bpExpected :+ ((bpEx: java.lang.Throwable) => " +
                      "bpEx.isInstanceOf[java.lang.IllegalStateException])"), t)
    // …and the arming stands WHERE JAVA WROTE IT: after `x`, before the throwing call. Java armed
    // the rule at the call and not before it, and this is that fact in the emitted order.
    assert(t.indexOf("var x") < t.indexOf("bpExpected = bpExpected"), t)
    assert(t.indexOf("bpExpected = bpExpected") < t.indexOf("boom(x)"), t)
    // junit's own statement around the whole body — run, catch Throwable, apply the accumulation.
    assert(t.contains("catch {"), t)
    assert(t.contains("case bpThrown: java.lang.Throwable => bpCaught = bpThrown"), t)
    assert(t.contains("if (bpCaught ne null)"), t)
    assert(t.contains("if (bpExpected.isEmpty) throw bpCaught"), t)
    assert(t.contains("bpExpected.forall((bpP: (java.lang.Throwable) => scala.Boolean) => " +
                      "bpP.apply(bpCaught))"), t)
    assert(t.contains("else if (bpExpected.nonEmpty)"), t)
    assertEquals(guards(ph), Nil)
  }

  test("…and a site IN A LOOP BODY converts too — the position no lexical wrap could express") {
    // The 17 sites `ENGINE-LIMITS.md` X5 records as refused under the `intercept` shape, and the
    // whole reason this lowering exists: java's rule is armed from the CALL to the end of the test,
    // so an `intercept` around "the rest of the enclosing block" fails a body that completes
    // normally where java simply ran the next iteration. An arming is a statement and has no such
    // problem — it goes where java wrote it.
    val (out, ph) = emitWithRules(ruleSuite(
      """  @Test public void a() {
        |    for (int i = 0; i < 3; i++) {
        |      thrown.expect(IllegalStateException.class);
        |      boom(i);
        |    }
        |  }
        |  static void boom(int x) { throw new IllegalStateException(); }""".stripMargin))
    assertEquals(clue(guards(ph)), Nil)
    val t = out.substring(out.indexOf("test(\"a\")"))
    assert(!clue(t).contains("thrown.expect"))
    // the arming is INSIDE the loop, and the rule's statement is OUTSIDE it.
    assert(t.indexOf("while") < t.indexOf("bpExpected = bpExpected"), t)
    assert(t.indexOf("var bpExpected") < t.indexOf("while"), t)
    assert(t.indexOf("bpExpected = bpExpected") < t.indexOf("if (bpCaught ne null)"), t)
  }

  test("the MATCHER overload becomes hamcrest's own contract — `matches(Object)`, no table") {
    val (out, ph) = emitWithRules(ruleSuite(
      """  @Test public void a() {
        |    thrown.expect(org.hamcrest.IsAnything.anything());
        |    boom();
        |  }
        |  static void boom() { throw new IllegalStateException(); }""".stripMargin))
    val t = out.substring(out.indexOf("test(\"a\")"))
    assertEquals(clue(guards(ph)), Nil)
    assert(clue(t).contains(".matches(bpEx)"), t)
    // the matcher is EVALUATED WHERE JAVA EVALUATED IT — at the `expect` call — and the closure
    // captures the binding, so an arming inside a loop captures THAT iteration's matcher.
    assert(t.indexOf("val bpMatcher") < t.indexOf("bpExpected = bpExpected"), t)
  }

  test("expectMessage(String) is junit's containsString, and does not NPE on a null message") {
    val (out, ph) = emitWithRules(ruleSuite(
      """  @Test public void a() {
        |    thrown.expect(IllegalStateException.class);
        |    thrown.expectMessage("boom");
        |    boom();
        |  }
        |  static void boom() { throw new IllegalStateException("boom"); }""".stripMargin))
    val t = out.substring(out.indexOf("test(\"a\")"))
    assertEquals(clue(guards(ph)), Nil)
    assert(clue(t).contains("bpEx.isInstanceOf[java.lang.IllegalStateException]"), t)
    // hamcrest's `TypeSafeMatcher` answers FALSE for a null item rather than throwing, which is a
    // guard in the predicate and not an accident of the corpus.
    assert(t.contains("(bpEx.getMessage() ne null) && bpEx.getMessage().contains(\"boom\")"), t)
    assert(!t.contains("thrown.expect"), t)
  }

  test("TWO expect calls ACCUMULATE — java requires all of them, and so does the emitted list") {
    // Under the `intercept` shape this was a REFUSAL (`double-expect`): one `intercept` takes one
    // type argument and java's conjunction had no image. The accumulator is junit's own, so the
    // conjunction is `forall` and there is nothing left to decline.
    val (out, ph) = emitWithRules(ruleSuite(
      """  @Test public void a() {
        |    thrown.expect(IllegalStateException.class);
        |    thrown.expect(org.hamcrest.IsAnything.anything());
        |    boom();
        |  }
        |  static void boom() { throw new IllegalStateException(); }""".stripMargin))
    assertEquals(clue(guards(ph)), Nil)
    val t = out.substring(out.indexOf("test(\"a\")"))
    assertEquals(clue(t.sliding("bpExpected = bpExpected".length)
                       .count(_ == "bpExpected = bpExpected")), 2)
  }

  test("…and the operands are bound IN CALL ORDER, whichever of the two java wrote first") {
    // The eager bindings exist to keep java's evaluation order, so ordering them by KIND would
    // reintroduce exactly what they were written to preserve. Nothing else can see this: both
    // orders compile, and both differ only when an operand has a side effect.
    val (out, ph) = emitWithRules(ruleSuite(
      """  @Test public void a() {
        |    thrown.expectMessage(msg());
        |    thrown.expect(org.hamcrest.IsAnything.anything());
        |    boom();
        |  }
        |  static String msg() { return "boom"; }
        |  static void boom() { throw new IllegalStateException("boom"); }""".stripMargin))
    val t = out.substring(out.indexOf("test(\"a\")"))
    assertEquals(clue(guards(ph)), Nil)
    assert(clue(t).indexOf("val bpMessage0") < t.indexOf("val bpMatcher"), t)
  }

  test("any OTHER member of the rule is REFUSED — a state this translation does not model") {
    val (_, ph) = emitWithRules(ruleSuite(
      """  @Test public void a() {
        |    thrown.expect(IllegalStateException.class);
        |    thrown.expectCause(org.hamcrest.IsAnything.anything());
        |    boom();
        |  }
        |  static void boom() { throw new IllegalStateException(); }""".stripMargin))
    assertEquals(clue(guards(ph)), List("unsupported-member"))
  }

  test("…and the FIELD reached anywhere but as a call receiver is REFUSED, naming its own guard") {
    // The accumulator this lowering arms is a LOCAL of the test it was armed in, so a `thrown`
    // handed to something else is a rule state it cannot model. Two guards rather than one: a
    // wrong MEMBER and a reference that is not a call at all are different sentences, and an agent
    // reading the row has to be told which.
    val (out, ph) = emitWithRules(ruleSuite(
      """  @Test public void a() {
        |    thrown.expect(IllegalStateException.class);
        |    use(thrown);
        |    boom();
        |  }
        |  static void use(Object o) { }
        |  static void boom() { throw new IllegalStateException(); }""".stripMargin))
    assertEquals(clue(guards(ph)), List("unsupported-reference"))
    assert(clue(out).contains("thrown.expect"))
  }

  test("a suite declaring @After CONVERTS, with the rule OUTSIDE the teardown — JUnit's nesting") {
    // `BlockJUnit4ClassRunner.methodBlock` wraps `withRules` around `withAfters`, so a teardown that
    // throws is compared against the expectation in java. That was a REFUSAL under the `intercept`
    // shape, which had to sit inside the `try … finally`; the wrap is applied outside it now, so
    // the nesting is java's own. Nothing else could see this: both shapes compile.
    val (out, ph) = emitWithRules(ruleSuite(
      """  @After public void tearDown() { }
        |  @Test public void a() {
        |    thrown.expect(IllegalStateException.class);
        |    boom();
        |  }
        |  static void boom() { throw new IllegalStateException(); }""".stripMargin))
    assertEquals(clue(guards(ph)), Nil)
    val t = out.substring(out.indexOf("test(\"a\")"))
    assert(clue(t).contains("finally tearDown()"), t)
    // the rule's own catch is OUTSIDE the teardown's finally, exactly as java nests them.
    assert(t.indexOf("var bpExpected") < t.indexOf("finally tearDown()"), t)
    assert(t.indexOf("finally tearDown()") < t.indexOf("case bpThrown"), t)
  }

  test("a @ClassRule ExpectedException is REPORTED, not quietly taken for a @Rule") {
    // A method rule wraps each test; a CLASS rule wraps the whole class run, so the region an
    // `expect` arms is a different one and a per-test accumulator is not its image. Nothing in the
    // corpus writes the shape, which is exactly why an unstated exclusion would never be found.
    val (out, ph) = emitWithRules(
      """package demo;
        |import org.junit.ClassRule;
        |import org.junit.Test;
        |import org.junit.rules.ExpectedException;
        |public class ClassRuleTest {
        |  @ClassRule public static ExpectedException thrown = ExpectedException.none();
        |  @Test public void a() {
        |    thrown.expect(IllegalStateException.class);
        |    boom();
        |  }
        |  static void boom() { throw new IllegalStateException(); }
        |}
        |""".stripMargin)
    assertEquals(clue(guards(ph)), List("class-rule"))
    assert(clue(out).contains("thrown.expect"))
    assert(!out.contains("bpExpected"), out)
  }

  test("a @Rule of ANOTHER class is untouched — the translation is keyed on the field's TYPE") {
    val (out, ph) = emitWithRules(
      """package demo;
        |import org.junit.Rule;
        |import org.junit.Test;
        |import org.junit.rules.TemporaryFolder;
        |public class OtherRuleTest {
        |  @Rule public TemporaryFolder folder = new TemporaryFolder();
        |  @Test public void a() { folder.toString(); }
        |}
        |""".stripMargin)
    assertEquals(clue(guards(ph)), Nil)
    assert(clue(ph.findings.map(_.construct)).contains("org.junit.Rule"))
    assert(clue(out).contains("folder.toString()"))
  }

  test("a suite with NO rule produces no ExpectedException row — the refusal lane is not noise") {
    val (out, ph) = emitWithRules(ruleSuite(
      """  @Test public void a() { }"""))
    assertEquals(clue(guards(ph)), Nil)
    // …and no test that never touches the rule pays for it: no accumulator, no catch, no check.
    assert(!clue(out).contains("bpExpected"))
  }

  test("a CONVERTED site is recorded on the test's own Decision — §5.1's other artifact") {
    // The emitted accumulator plainly asserts a throw; what it cannot say is that java said so
    // through a `@Rule` FIELD three screens up, which is the fact an agent reading one emitted file
    // has no way to recover (CLAUDE.md §4.575).
    val log = Pipeline.runTraced(
      SpoonTir.fromSources(("Snippet.java" -> ruleSuite(
        """  @Test public void a() {
          |    thrown.expect(IllegalStateException.class);
          |    boom();
          |  }
          |  static void boom() { throw new IllegalStateException(); }""".stripMargin)) :: junitRuleStubs),
      List(new TestFrameworkTransform))._2
    val d = log.all.find(_.subjectFqn.endsWith("#a"))
    assert(clue(log.all.map(_.subjectFqn)).nonEmpty)
    assert(clue(d.map(_.detail.getOrElse("rule", ""))).exists(_.contains("modelled")))
  }

  // ---- the lowering's SEMANTIC CELLS, run rather than read ----
  //
  // Everything above asserts the emitted SHAPE. These run it: each is the shape the phase emits,
  // written out, over a body that behaves the way the java did. A shape assertion cannot tell an
  // exact model from a plausible one, and junit's contract is what this has to reproduce —
  // `ExpectedExceptionStatement.evaluate` plus `failDueToMissingException`.

  private def ruleCheck(caught: java.lang.Throwable,
                        expected: List[java.lang.Throwable => Boolean]): Unit =
    if caught ne null then
      if expected.isEmpty then throw caught
      else munit.Assertions.assert(expected.forall(p => p.apply(caught)), "did not satisfy")
    else if expected.nonEmpty then munit.Assertions.fail("nothing threw")

  test("BEHAVIOUR: ARMED and MATCHED passes — junit's rule applied to what the test threw") {
    var bpExpected: List[java.lang.Throwable => Boolean] = Nil
    var bpCaught: java.lang.Throwable = null
    try {
      bpExpected = bpExpected :+ ((bpEx: java.lang.Throwable) => bpEx.isInstanceOf[IllegalStateException])
      throw new IllegalStateException("boom")
    } catch { case bpThrown: java.lang.Throwable => bpCaught = bpThrown }
    ruleCheck(bpCaught, bpExpected) // passes: this test's own success IS the assertion
  }

  test("BEHAVIOUR: ARMED and NOT matched FAILS — and it is the expectation that reports it") {
    var bpExpected: List[java.lang.Throwable => Boolean] = Nil
    var bpCaught: java.lang.Throwable = null
    try {
      bpExpected = bpExpected :+ ((bpEx: java.lang.Throwable) => bpEx.isInstanceOf[IllegalStateException])
      throw new java.io.IOException("other")
    } catch { case bpThrown: java.lang.Throwable => bpCaught = bpThrown }
    intercept[munit.FailException](ruleCheck(bpCaught, bpExpected))
  }

  test("BEHAVIOUR: ARMED and NOTHING THROWN fails — junit's failDueToMissingException") {
    var bpExpected: List[java.lang.Throwable => Boolean] = Nil
    val bpCaught: java.lang.Throwable = null
    bpExpected = bpExpected :+ ((bpEx: java.lang.Throwable) => bpEx.isInstanceOf[IllegalStateException])
    intercept[munit.FailException](ruleCheck(bpCaught, bpExpected))
  }

  test("BEHAVIOUR: UNARMED and thrown RETHROWS — a test that fails for its own reason still does") {
    var bpCaught: java.lang.Throwable = null
    try throw new IllegalStateException("mine")
    catch { case bpThrown: java.lang.Throwable => bpCaught = bpThrown }
    intercept[IllegalStateException](ruleCheck(bpCaught, Nil))
  }

  test("BEHAVIOUR: UNARMED and nothing thrown passes — the rule is not an assertion by itself") {
    ruleCheck(null, Nil)
  }

  test("BEHAVIOUR: an arming MID-LOOP governs the rest of the test, as java's does") {
    // The 17 refused sites, exactly: java arms on the first iteration and the throw leaves the
    // METHOD, so the region armed is the rest of this iteration plus every later one plus
    // everything after the loop. An `intercept` around the rest of the enclosing block would fail
    // a body that completed normally; this simply does what java did.
    var iterations = 0
    var bpExpected: List[java.lang.Throwable => Boolean] = Nil
    var bpCaught: java.lang.Throwable = null
    try {
      var i = 0
      while (i < 3) {
        iterations += 1
        if i == 0 then
          bpExpected = bpExpected :+ ((bpEx: java.lang.Throwable) => bpEx.isInstanceOf[IllegalStateException])
        if i == 1 then throw new IllegalStateException("second iteration")
        i += 1
      }
    } catch { case bpThrown: java.lang.Throwable => bpCaught = bpThrown }
    ruleCheck(bpCaught, bpExpected)
    // the arming did NOT end the first iteration — java's rule is not a `try` around the body.
    assertEquals(iterations, 2)
  }

  test("BEHAVIOUR: TWO armings are a CONJUNCTION — junit requires every matcher, not the last") {
    // The reason the accumulator is a LIST: keeping only the last matcher would PASS where java
    // FAILED, which is the false-green direction this engine exists to prevent.
    var bpExpected: List[java.lang.Throwable => Boolean] = Nil
    var bpCaught: java.lang.Throwable = null
    try {
      bpExpected = bpExpected :+ ((bpEx: java.lang.Throwable) => bpEx.isInstanceOf[IllegalStateException])
      bpExpected = bpExpected :+ ((bpEx: java.lang.Throwable) =>
        (bpEx.getMessage ne null) && bpEx.getMessage.contains("wanted"))
      throw new IllegalStateException("other")
    } catch { case bpThrown: java.lang.Throwable => bpCaught = bpThrown }
    intercept[munit.FailException](ruleCheck(bpCaught, bpExpected))
  }

  test("BEHAVIOUR: expectMessage over a NULL message does not throw — hamcrest answers false") {
    var bpExpected: List[java.lang.Throwable => Boolean] = Nil
    var bpCaught: java.lang.Throwable = null
    try {
      bpExpected = bpExpected :+ ((bpEx: java.lang.Throwable) =>
        (bpEx.getMessage ne null) && bpEx.getMessage.contains("boom"))
      throw new IllegalStateException()
    } catch { case bpThrown: java.lang.Throwable => bpCaught = bpThrown }
    intercept[munit.FailException](ruleCheck(bpCaught, bpExpected))
  }
  // ---------------------------------------------------- untranslated: LOUD --

  test("@Rule is reported, classified (a), with its source position") {
    val (_, ph) = emit(
      """package demo;
        |import org.junit.Rule;
        |import org.junit.Test;
        |import org.junit.rules.TemporaryFolder;
        |public class RuleTest {
        |  @Rule public TemporaryFolder folder = new TemporaryFolder();
        |  @Test public void a() { }
        |}
        |""".stripMargin)
    val f = ph.findings.find(_.construct == "org.junit.Rule")
    assert(clue(ph.findings).nonEmpty)
    assertEquals(f.map(_.fix.label), Some("a"))
    assert(f.exists(_.where.line > 0))
  }

  test("@RunWith is reported — the converted suite runs a different SET of tests") {
    val (_, ph) = emit(
      """package demo;
        |import org.junit.Test;
        |import org.junit.runner.RunWith;
        |import org.junit.runners.Parameterized;
        |@RunWith(Parameterized.class)
        |public class ParamTest {
        |  @Test public void a() { }
        |}
        |""".stripMargin)
    assert(clue(ph.findings.map(_.construct)).contains("org.junit.runner.RunWith"))
  }

  test("JUnit 5 is reported — it would otherwise convert to ZERO tests and report success") {
    val junit5 =
      """package demo;
        |import org.junit.jupiter.api.BeforeEach;
        |import org.junit.jupiter.api.Test;
        |public class Junit5Test {
        |  @BeforeEach void setUp() { }
        |  @Test void a() { org.junit.jupiter.api.Assertions.assertEquals(1, 1); }
        |}
        |""".stripMargin
    val (out, ph) = emit(junit5)
    assert(clue(ph.findings.map(_.construct)).contains("org.junit.jupiter.api.Test"))
    // the silent half this makes loud: nothing was converted.
    assert(!clue(out).contains("munit.FunSuite"))

    // VERIFIED, not assumed: JUnit 5 IS caught by PortabilityCheck's `org.junit.` prefix rule —
    // but only through the ASSERTION reference (`org.junit.jupiter.api…`), which is a term. The
    // `@Test` annotation type is not in the xref at all (Xref walks trees, not
    // `Symbol.annotations`), so a JUnit-5 suite whose assertions came from elsewhere would be
    // invisible to the check. "Semi-loudly" is the right word for it.
    val prog = Pipeline.run(SpoonTir.fromSource(junit5), Nil)
    val v = PortabilityCheck.check(prog).map(_.api).distinct
    assert(clue(v).exists(_.startsWith("org.junit.jupiter")))
  }

  test("TestNG is reported HERE because PortabilityCheck cannot see it at all") {
    val testng =
      """package demo;
        |import org.testng.annotations.Test;
        |public class NgTest {
        |  @Test public void a() { org.testng.Assert.assertEquals(1, 1); }
        |}
        |""".stripMargin
    val (out, ph) = emit(testng)
    assert(clue(ph.findings.map(_.construct)).contains("org.testng.annotations.Test"))
    assert(!clue(out).contains("munit.FunSuite"))

    // The audit's claim was that JUnit 5 and TestNG "degrade semi-loudly via the `org.junit.`
    // portability rule". For TestNG that is FALSE: its packages match no rule, so the check
    // reports zero and the suite converts to nothing, silently. This assertion pins the gap.
    val prog = Pipeline.run(SpoonTir.fromSource(testng), Nil)
    assertEquals(PortabilityCheck.check(prog).map(_.api).distinct, Nil)
  }

  test("a JUnit 3 TestCase subclass is reported — it carries no annotation to key off") {
    val (_, ph) = emit(
      """package demo;
        |public class OldTest extends junit.framework.TestCase {
        |  public void testSomething() { assertTrue(true); }
        |}
        |""".stripMargin)
    assert(clue(ph.findings.map(_.construct)).contains("junit.framework.TestCase"))
  }

  test("Hamcrest assertThat is reported, not silently mistranslated") {
    val (out, ph) = emit(
      """package demo;
        |import org.junit.Test;
        |import static org.hamcrest.MatcherAssert.assertThat;
        |import static org.hamcrest.CoreMatchers.equalTo;
        |public class HamcrestTest {
        |  @Test public void a() { assertThat(1, equalTo(1)); }
        |}
        |""".stripMargin)
    val f = ph.findings.find(_.construct == "assertThat")
    assert(clue(ph.findings.map(_.construct)).contains("assertThat"))
    assertEquals(f.map(_.fix.label), Some("a"))
    // deliberately NOT rewritten — MUnit has no matcher algebra to map a matcher ONTO, and
    // inventing a translation would be the silent-miss this project exists to prevent.
    assert(!clue(out).contains("munit.Assertions.assertThat"))
  }

  test("…and the RESIDUE it leaves is COUNTED — PortabilityCheck has an org.hamcrest rule") {
    // The decision not to translate hamcrest is only defensible if what it leaves behind is a
    // NUMBER. `TestFrameworkTransform.findings` prints one; nothing recorded it, because the check
    // had rules for `org.junit.` and `junit.framework.` and none for the vocabulary reached
    // THROUGH them — so a suite could be 100% hamcrest and every portability lane read zero.
    //
    // The receiver is IMPORTED and named, not static-imported: `fromSource` builds with
    // `noClasspath`, so a static import in a one-file snippet resolves to `this.assertThat(…)` and
    // the reference never names hamcrest at all (see `transformApply`'s note). A model over a whole
    // source tree resolves the static-import form — which is the one every liqp suite uses — to the
    // same symbol this one names directly.
    val prog = Pipeline.run(SpoonTir.fromSource(
      """package demo;
        |import org.hamcrest.CoreMatchers;
        |import org.hamcrest.MatcherAssert;
        |import org.junit.Test;
        |public class HamcrestFqnTest {
        |  @Test public void a() { MatcherAssert.assertThat(1, CoreMatchers.equalTo(1)); }
        |}
        |""".stripMargin), Nil)
    val v = PortabilityCheck.check(prog).map(_.api).distinct
    assert(clue(v).exists(_.startsWith("org.hamcrest.")))
  }

  test("a plain JUnit-4 suite produces NO findings — the survey is not noise") {
    val (_, ph) = emit(lifecycleSrc)
    assertEquals(ph.findings.map(_.render), Nil)
  }

  test("a @Test method's JAVADOC lands above the `test(...)` that replaces it") {
    // the method stops being a method, so the `leading` field it carried has no `def` left to sit
    // on. Rendered nowhere, this is the biggest single category the recovery backstop has to put
    // back — 51 comments on one of libGDX's suites — and a backstop placement is member-granular
    // where this one is exact.
    val (out, _) = emit(
      """package demo;
        |import org.junit.Test;
        |public class DocTest {
        |  /** Test of the different adding methods */
        |  @Test public void addTest() { }
        |}
        |""".stripMargin)
    assert(out.contains("Test of the different adding methods"), out)
    assert(out.indexOf("Test of the different adding methods") < out.indexOf("test(\"addTest\")"), out)
  }
