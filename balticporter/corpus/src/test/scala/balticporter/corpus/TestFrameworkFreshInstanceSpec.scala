package balticporter.corpus

import balticporter.emit.TirEmitter
import balticporter.frontend.spoon.SpoonTir
import balticporter.tir.{Decision, Pipeline}
import balticporter.transform.TestFrameworkTransform

/** JUnit 4 CONSTRUCTS A FRESH TEST OBJECT PER `@Test`; MUnit runs one suite instance
  * (`ENGINE-LIMITS.md` X4, `CLAUDE.md` §4.4).
  *
  * Every cell here is a §4.4 defect in the strict sense — the port compiles either way, no check
  * count moves, and only RUNNING the suite can tell the two apart — so each is asserted twice where
  * it can be: once against the emitted Scala, and once BEHAVIOURALLY, by running the shape the
  * transform emits and comparing it against what a real `junit 4.13` run produced for the same java.
  *
  * The oracle is not the JLS read from memory. Each ordering claim below was probed against junit:
  *
  * {{{
  * @BeforeClass
  * fieldA-init / init-block / fieldB-init   <- JLS 12.5 step 4, ONE sequence, textual order
  * ctor-body                                <- step 5
  * @Before
  * t1 mutated-was=0 sharedStatic-was=0      <- an un-initialised field is back at the DEFAULT
  * @After
  * fieldA-init / init-block / fieldB-init   <- and all of it again, for the second test
  * ctor-body
  * @Before
  * t2 mutated-was=0 sharedStatic-was=1      <- but a `static` field is SHARED
  * }}}
  *
  * …and, for a hierarchy, `Base.field-init / Base.ctor sees sub=null / Sub.field-init / Sub.ctor`
  * on BOTH tests — the superclass constructor reads the subclass field at its DEFAULT even on the
  * second construction, after the first test assigned it. That is the whole argument for zeroing
  * before delegating upward rather than on the way down.
  */
class TestFrameworkFreshInstanceSpec extends munit.FunSuite:

  private def emit(java: String): (String, TestFrameworkTransform) =
    val ph    = new TestFrameworkTransform
    val after = Pipeline.run(SpoonTir.fromSource(java), List(ph))
    (new TirEmitter(after).emit, ph)

  private def decisions(java: String): List[Decision] =
    Pipeline.runTraced(SpoonTir.fromSource(java), List(new TestFrameworkTransform))._2
      .of(Decision.Kind.RebuiltPerTest)

  /** the body of the emitted `bpFreshState`, as its statement lines — brace-balanced, because an
    * instance initialiser block renders as a nested `{ … }` and a `takeWhile` on the closing line
    * would silently stop at it and report a PREFIX of the sequence as the whole of it. */
  private def rebuild(out: String): List[String] =
    val at = out.indexOf("def bpFreshState()")
    assert(at >= 0, clue(out))
    val lines = out.substring(at).linesIterator.drop(1).toList
    var depth = 1
    val kept  = List.newBuilder[String]
    lines.iterator.takeWhile { l =>
      depth += l.count(_ == '{') - l.count(_ == '}')
      if depth > 0 then { kept += l.trim; true } else false
    }.foreach(_ => ())
    kept.result().filter(_.nonEmpty)

  // ------------------------------------------------------------------ state --

  private val sharedFieldSrc =
    """package demo;
      |import org.junit.Test;
      |public class SharedFieldTest {
      |  private final java.util.List<String> seen = new java.util.ArrayList<String>();
      |  @Test public void one() { seen.add("a"); }
      |  @Test public void two() { seen.add("b"); }
      |}
      |""".stripMargin

  test("TWO TESTS SHARING A FIELD EACH SEE FRESH STATE — the field initialiser moves into the rebuild") {
    val (out, _) = emit(sharedFieldSrc)
    // the declaration keeps only the JVM default the allocation leaves…
    assert(clue(out).contains("private var seen: java.util.List[java.lang.String] = scala.compiletime.uninitialized"))
    // …and java's own initialiser runs once per test, from the rebuild.
    assertEquals(rebuild(out), List("seen = null", "seen = new java.util.ArrayList[java.lang.String]()"))
    // every test body opens with it. Not `beforeEach`: a hook the suite must be asked to run is a
    // hook that can silently not run, and the prologue is the body's first statement.
    assert(out.contains("test(\"one\")({\n    bpFreshState()"), clue(out))
    assert(out.contains("test(\"two\")({\n    bpFreshState()"), clue(out))
  }

  test("…and it BEHAVES that way — the shape the transform emits, run") {
    // the emitted shape, by hand: one suite instance, a var at its default, a rebuild per test.
    class Emitted extends munit.FunSuite:
      var seen: java.util.List[String] = scala.compiletime.uninitialized
      val sizes = collection.mutable.ListBuffer.empty[Int]
      def bpFreshState(): Unit =
        seen = null
        seen = new java.util.ArrayList[String]()
      test("one") { bpFreshState(); seen.add("a"); sizes += seen.size }
      test("two") { bpFreshState(); seen.add("b"); sizes += seen.size }
    val suite = new Emitted
    suite.munitTests().foreach(t => t.body())
    // java: a fresh list per test, so BOTH tests see size 1. Without the rebuild the second reads 2.
    assertEquals(suite.sizes.toList, List(1, 1))
  }

  test("A FIELD WITH NO INITIALISER IS RESET TO THE JVM DEFAULT — junit's allocation, not its step 4") {
    // probed: `t1 mutated-was=0` and `t2 mutated-was=0`, though t1 assigned 42.
    val (out, _) = emit(
      """package demo;
        |import org.junit.Test;
        |public class DefaultsTest {
        |  private int mutated;
        |  private String touched;
        |  private boolean flag;
        |  @Test public void one() { mutated = 42; touched = "x"; flag = true; }
        |  @Test public void two() { }
        |}
        |""".stripMargin)
    assertEquals(rebuild(out), List("mutated = 0", "touched = null", "flag = false"))
  }

  test("A GENUINELY STATIC FIELD IS NOT RESET — java SHARES one across every construction") {
    // probed: `t2 sharedStatic-was=1`. Resetting it would be this defect inverted.
    val (out, _) = emit(
      """package demo;
        |import org.junit.Test;
        |public class StaticTest {
        |  private static int shared = 0;
        |  private int mine = 0;
        |  @Test public void one() { shared++; mine++; }
        |  @Test public void two() { shared++; mine++; }
        |}
        |""".stripMargin)
    assertEquals(rebuild(out), List("mine = 0", "mine = 0"))
    assert(!rebuild(out).exists(_.contains("shared")), clue(out))
  }

  // ------------------------------------------------------------- the ORDER --

  test("STEP 4 IS ONE SEQUENCE IN TEXTUAL ORDER — a field initialiser and an INSTANCE INITIALISER BLOCK interleave") {
    // probed: fieldA-init / init-block / fieldB-init. Grouped "fields then blocks" the assignment
    // java ran FIRST would run LAST — `CLAUDE.md` §4.55's own correction, met here.
    val (out, _) = emit(
      """package demo;
        |import org.junit.Test;
        |public class OrderTest {
        |  private int a = 1;
        |  { a = 2; }
        |  private int b = 5;
        |  @Test public void one() { }
        |}
        |""".stripMargin)
    val body = rebuild(out)
    assert(body.indexOf("a = 1") < body.indexOf("this.a = 2"), clue(body))
    assert(body.indexOf("this.a = 2") < body.indexOf("b = 5"), clue(body))
    // …and the block is CONSUMED rather than left for the emitter to inline a second time.
    assertEquals(out.sliding("a = 2".length).count(_ == "a = 2"), 1, clue(out))
  }

  test("THE CONSTRUCTOR BODY IS REPLAYED, AFTER step 4 — java runs step 5 second") {
    val (out, _) = emit(
      """package demo;
        |import org.junit.Test;
        |public class CtorTest {
        |  private int seen = 3;
        |  public CtorTest() { seen = seen + 1; }
        |  @Test public void one() { }
        |}
        |""".stripMargin)
    val body = rebuild(out)
    assertEquals(body, List("seen = 0", "seen = 3", "this.seen = this.seen + 1"))
  }

  test("THE REBUILD RUNS AHEAD OF @Before — junit's createTest() precedes withBefores()") {
    val (out, _) = emit(
      """package demo;
        |import org.junit.Before;
        |import org.junit.Test;
        |public class BeforeOrderTest {
        |  private int seen = 3;
        |  @Before public void setUp() { seen = seen * 10; }
        |  @Test public void one() { }
        |}
        |""".stripMargin)
    val one = out.substring(out.indexOf("test(\"one\")"))
    assert(one.indexOf("bpFreshState()") < one.indexOf("setUp()"), clue(one))
  }

  test("…and the initialisation is HOISTED, so it runs N times and not N+1, on junit's side of @BeforeClass") {
    val (out, _) = emit(
      """package demo;
        |import org.junit.BeforeClass;
        |import org.junit.Test;
        |public class HoistTest {
        |  private int seen = 3;
        |  @BeforeClass public static void once() { }
        |  @Test public void one() { }
        |}
        |""".stripMargin)
    // the class body initialises NOTHING — junit runs @BeforeClass before the first construction,
    // and MUnit constructs the suite before beforeAll(), so an un-hoisted initialiser would run on
    // the wrong side of it (and once more than java ran it).
    assert(clue(out).contains("private var seen: scala.Int = 0"))
    assert(!out.contains("private var seen: scala.Int = 3"))
    assert(out.contains("override def beforeAll()"))
  }

  // -------------------------------------------------------------- the CHAIN --

  private val hierarchySrc =
    """package demo;
      |import org.junit.Test;
      |public abstract class BaseTest {
      |  protected String baseField = "base";
      |  public static class SubTest extends BaseTest {
      |    private String subField = "sub";
      |    @Test public void one() { }
      |  }
      |}
      |""".stripMargin

  test("A HIERARCHY CHAINS, ZEROING BEFORE DELEGATING UPWARD — java zeroes the whole object first") {
    // probed: `Base.ctor sees sub=null` on the SECOND test too. Zeroing on the way down would show
    // the superclass the previous test's value, which is X4 one level in.
    val (out, _) = emit(hierarchySrc)
    val sub = out.substring(out.indexOf("class SubTest"))
    val body = rebuild(sub)
    assertEquals(body, List("subField = null", "super.bpFreshState()", "subField = \"sub\""))
    assert(sub.contains("override def bpFreshState()"), clue(sub))
    // …and the base declares the member the subclass overrides, though it declares no @Test at all:
    // java rebuilt ITS state per test too.
    val base = out.substring(out.indexOf("class BaseTest"), out.indexOf("class SubTest"))
    assert(clue(base).contains("def bpFreshState()"))
    assert(!base.contains("override def bpFreshState()"))
    assertEquals(rebuild(base), List("baseField = null", "baseField = \"base\""))
  }

  test("…and it BEHAVES that way — the chain, run") {
    class Base:
      var baseField: String = scala.compiletime.uninitialized
      val sawInBase = collection.mutable.ListBuffer.empty[String]
      def bpFreshState(): Unit =
        baseField = null
        baseField = "base"
        sawInBase += String.valueOf(peek)
      def peek: String = null
    class Sub extends Base:
      var subField: String = scala.compiletime.uninitialized
      override def peek: String = subField
      override def bpFreshState(): Unit =
        subField = null
        super.bpFreshState()
        subField = "sub"
    val s = new Sub
    s.bpFreshState(); s.subField = "MUTATED"
    s.bpFreshState()
    // java's second construction shows the base constructor `null`, not the first test's value.
    assertEquals(s.sawInBase.toList, List("null", "null"))
  }

  // ------------------------------------------------------------- REFUSALS --

  test("A CONSTRUCTOR TAKING PARAMETERS IS REFUSED AND COUNTED — and its fields are left ALONE") {
    // junit itself refuses such a class ("Test class should have exactly one public constructor" /
    // zero-arg), so this is the `@RunWith`-supplied shape, which converts as though the runner were
    // absent. Hoisting the fields out from under a constructor body this cannot replay would leave
    // that body reading defaults — a defect the lowering would have CAUSED.
    val (out, ph) = emit(
      """package demo;
        |import org.junit.Test;
        |public class ParamCtorTest {
        |  private final String spec;
        |  private int n = 7;
        |  public ParamCtorTest(String spec) { this.spec = spec; }
        |  @Test public void one() { }
        |}
        |""".stripMargin)
    assert(!clue(out).contains("bpFreshState"))
    val fs = ph.findings.filter(_.construct == "fresh-state(constructor)")
    assertEquals(fs.size, 1)
    assert(clue(fs.head.advice).contains("takes constructor parameters"))
  }

  test("…and so is a constructor that DELEGATES, or passes arguments to super") {
    val (_, ph) = emit(
      """package demo;
        |import org.junit.Test;
        |public class DelegatingTest {
        |  private int n = 7;
        |  public DelegatingTest() { this(1); }
        |  public DelegatingTest(int k) { n = k; }
        |  @Test public void one() { }
        |}
        |""".stripMargin)
    val fs = ph.findings.filter(_.construct == "fresh-state(constructor)")
    assertEquals(fs.size, 1)
    assert(clue(fs.head.advice).contains("declares 2 constructors"))
  }

  test("A SUITE WITH NO INSTANCE STATE GETS NO MEMBER AND NO PROLOGUE — the lowering is not a tax") {
    val (out, _) = emit(
      """package demo;
        |import org.junit.Test;
        |public class StatelessTest {
        |  @Test public void one() { org.junit.Assert.assertEquals(1, 1); }
        |}
        |""".stripMargin)
    assert(!clue(out).contains("bpFreshState"))
  }

  test("THE INSTANCE ITSELF IS NOT RENEWED, and every use of it as a VALUE is counted") {
    val (_, ph) = emit(
      """package demo;
        |import org.junit.Test;
        |public class EscapeTest {
        |  private int n = 1;
        |  private static java.util.List<Object> kept = new java.util.ArrayList<Object>();
        |  @Test public void one() { kept.add(this); }
        |}
        |""".stripMargin)
    val fs = ph.findings.filter(_.construct == "fresh-state(instance-escape)")
    assertEquals(fs.size, 1)
    assert(clue(fs.head.advice).contains("object identity is not"))
  }

  // ------------------------------------------------------------ PROVENANCE --

  test("EVERY REBUILT CLASS LEAVES A §1(a) ROW — the one edit no emitted text explains") {
    val ds = decisions(sharedFieldSrc)
    assertEquals(ds.map(_.subjectFqn), List("demo.SharedFieldTest"))
    assertEquals(ds.head.reason, balticporter.tir.Reason.Universal("test-framework/fresh-instance"))
    assertEquals(ds.head.detail("member"), "bpFreshState")
    assertEquals(ds.head.detail("fields"), "1")
    assertEquals(ds.head.detail("ctor"), "empty")
    assertEquals(ds.head.detail("chains"), "")
  }

  test("…and a CHAINED one names the ancestor it delegates to") {
    val ds = decisions(hierarchySrc).sortBy(_.subjectFqn)
    assertEquals(ds.map(_.subjectFqn), List("demo.BaseTest", "demo.BaseTest$SubTest"))
    assertEquals(ds.head.detail("chains"), "")
    assertEquals(ds.last.detail("chains"), "demo.BaseTest")
  }

  test("…and every converted test says its body opens by rebuilding") {
    val ds = Pipeline.runTraced(SpoonTir.fromSource(sharedFieldSrc), List(new TestFrameworkTransform))._2
      .of(Decision.Kind.RetypedSignature)
    assert(ds.nonEmpty)
    assert(ds.forall(_.detail("rebuilt") == "bpFreshState"), clue(ds.map(_.detail("rebuilt"))))
  }

  // -- P11: a DROPPED FIELD must not appear in bpFreshState ----------------------------------

  private val droppedFieldSrc =
    """import org.junit.Test;
      |import org.junit.Rule;
      |import org.junit.rules.TestWatcher;
      |import org.junit.runner.Description;
      |public class Demo {
      |  private int kept = 42;
      |  @Rule public TestWatcher watcher = new TestWatcher() {
      |    protected void failed(Throwable cause, Description desc) {
      |      System.out.println(desc.getTestClass().getSimpleName());
      |    }
      |  };
      |  @Test public void one() { kept = 1; }
      |}""".stripMargin

  test("P11: a field listed in dropFields is excluded from bpFreshState") {
    val ph    = new TestFrameworkTransform(dropFields = Set("Demo#watcher"))
    val after = Pipeline.run(SpoonTir.fromSource(droppedFieldSrc), List(ph))
    val out   = new TirEmitter(after).emit
    assert(clue(out).contains("def bpFreshState()"), "the method should still exist (for `kept`)")
    val body  = rebuild(out)
    assert(!body.exists(_.contains("watcher")),
      s"dropped field 'watcher' must NOT appear in bpFreshState body: ${body.mkString("\n")}")
    assert(body.exists(_.contains("kept")),
      "non-dropped field 'kept' must still appear in bpFreshState body")
  }

  test("P11: an empty dropFields set changes nothing") {
    val ph    = new TestFrameworkTransform(dropFields = Set.empty)
    val after = Pipeline.run(SpoonTir.fromSource(droppedFieldSrc), List(ph))
    val out   = new TirEmitter(after).emit
    val body  = rebuild(out)
    assert(body.exists(_.contains("watcher")),
      "with empty dropFields, watcher should be in bpFreshState body")
  }
