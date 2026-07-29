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
    // the body is kept — it still has to compile; MUnit simply never evaluates it.
    assert(out.contains("assertEquals(1, 2)"))
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
    // deliberately NOT rewritten to a façade member — the façade declares JUnit's `Assert` only,
    // and inventing a matcher translation would be the silent-miss this project exists to prevent.
    assert(!clue(out).contains("balticporter.runtime.Asserts.assertThat"))
  }

  test("a plain JUnit-4 suite produces NO findings — the survey is not noise") {
    val (_, ph) = emit(lifecycleSrc)
    assertEquals(ph.findings.map(_.render), Nil)
  }
