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
