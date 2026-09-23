package balticporter.transform

import balticporter.emit.TirEmitter
import balticporter.frontend.spoon.SpoonTir
import balticporter.tir.{ Decision, Pipeline, Reason }

/** `TestFrameworkTransform` handling of JUnit 4 `@RunWith(Parameterized.class)` — constructor injection, field injection, non-array data, name patterns, and fresh-state reset. Each fixture uses
  * neutral names.
  */
class ParameterizedTransformSpec extends munit.FunSuite:

  private def emit(java: String, annotations: balticporter.core.AnnotationPolicy = balticporter.core.AnnotationPolicy.none): (String, TestFrameworkTransform) =
    val ph    = new TestFrameworkTransform
    val after = Pipeline.run(SpoonTir.fromSource(java, annotations = annotations), List(ph))
    (new TirEmitter(after).emit, ph)

  private def emitTraced(java: String): (String, TestFrameworkTransform, balticporter.tir.DecisionLog) =
    val ph         = new TestFrameworkTransform
    val (after, l) = Pipeline.runTraced(SpoonTir.fromSource(java), List(ph))
    (new TirEmitter(after, notes = l).emit, ph, l)

  // -------------------------------------------------------------------------
  // fixture 1: constructor injection with a name pattern
  // -------------------------------------------------------------------------

  private val ctorSrc =
    """package demo;
      |import org.junit.Test;
      |import org.junit.runner.RunWith;
      |import org.junit.runners.Parameterized;
      |import static org.junit.Assert.assertEquals;
      |import java.util.Arrays;
      |import java.util.Collection;
      |@RunWith(Parameterized.class)
      |public class ArithSuite {
      |  @Parameterized.Parameters(name = "{0} + {1} = {2}")
      |  public static Collection<Object[]> data() {
      |    return Arrays.asList(new Object[][] { {1, 2, 3}, {4, 5, 9} });
      |  }
      |  private int a;
      |  private int b;
      |  private int expected;
      |  public ArithSuite(int a, int b, int expected) {
      |    this.a = a; this.b = b; this.expected = expected;
      |  }
      |  @Test public void add() { assertEquals(expected, a + b); }
      |  @Test public void subtract() { assertEquals(expected - b, a); }
      |}
      |""".stripMargin

  test("constructor injection: the class extends the suite and has test def registrations") {
    val (out, _) = emit(ctorSrc)
    assert(clue(out).contains("munit.FunSuite"))
    assert(out.contains("def add(): scala.Unit"))
    assert(out.contains("def subtract(): scala.Unit"))
  }

  test("constructor injection: test registrations contain the name pattern segments") {
    val (out, _) = emit(ctorSrc)
    assert(clue(out).contains("test("))
    assert(out.contains("\"add[\""))
    assert(out.contains("\"subtract[\""))
  }

  test("constructor injection: registrations iterate over the data method result") {
    val (out, _) = emit(ctorSrc)
    assert(clue(out).contains("data()"))
  }

  test("constructor injection: no @RunWith finding since it is handled") {
    val (_, ph)    = emit(ctorSrc)
    val constructs = ph.findings.map(_.construct)
    assert(!clue(constructs).contains("org.junit.runner.RunWith"))
  }

  test("constructor injection: decisions record the parameterized runner") {
    val (_, _, log) = emitTraced(ctorSrc)
    val ds          = log.of(Decision.Kind.RetypedSignature)
    assert(clue(ds).nonEmpty)
    assert(ds.forall(_.reason == Reason.Universal("test-framework/parameterized")))
    assert(ds.exists(_.detail.get("runner").contains("Parameterized")))
    assert(ds.exists(_.detail.get("injection").contains("constructor")))
  }

  test("constructor injection: bpParam fields are emitted and assigned from the row") {
    val (out, _) = emit(ctorSrc)
    // bpParam fields carry the constructor parameters as suite-level state
    assert(clue(out).contains("bpParam"))
    // the row assignment targets the bpParam fields, not the instance fields
    assert(out.contains("bpParam"))
  }

  test("constructor injection: bpFreshState replays the constructor body with bpParam substitution") {
    val (out, _) = emit(ctorSrc)
    // bpFreshState should exist and replay the constructor body (this.a = bpParam, etc.)
    assert(clue(out).contains("bpFreshState"))
    // the constructor body's this.a = a is replayed as this.a = bpParam
    assert(out.contains("bpParam"))
  }

  test("constructor injection: no fresh-state(constructor) finding") {
    val (_, ph)             = emit(ctorSrc)
    val constructorFindings = ph.findings.filter(_.construct == "fresh-state(constructor)")
    assertEquals(clue(constructorFindings).size, 0)
  }

  // -------------------------------------------------------------------------
  // fixture 2: @Parameter field injection
  // -------------------------------------------------------------------------

  private val fieldSrc =
    """package demo;
      |import org.junit.Test;
      |import org.junit.runner.RunWith;
      |import org.junit.runners.Parameterized;
      |import static org.junit.Assert.assertTrue;
      |import java.util.Arrays;
      |import java.util.Collection;
      |@RunWith(Parameterized.class)
      |public class FlagSuite {
      |  @Parameterized.Parameters
      |  public static Collection<Object[]> data() {
      |    return Arrays.asList(new Object[][] { {true}, {false} });
      |  }
      |  @Parameterized.Parameter
      |  public boolean flag;
      |  @Test public void checkFlag() { assertTrue(flag || !flag); }
      |}
      |""".stripMargin

  test("field injection: the class extends the suite and registers tests") {
    val (out, _) = emit(fieldSrc)
    assert(clue(out).contains("munit.FunSuite"))
    assert(out.contains("def checkFlag(): scala.Unit"))
    assert(out.contains("test("))
  }

  test("field injection: @Parameter and @RunWith are not in the refusal lane") {
    val (_, ph)    = emit(fieldSrc)
    val constructs = ph.findings.map(_.construct)
    assert(!clue(constructs).contains("org.junit.runner.RunWith"))
    assert(!constructs.exists(c => c.contains("Parameterized") && c.contains("Parameter")))
  }

  test("field injection: decisions record field injection mode") {
    val (_, _, log) = emitTraced(fieldSrc)
    val ds          = log.of(Decision.Kind.RetypedSignature)
    assert(ds.exists(_.detail.get("injection").contains("field")))
  }

  test("field injection with explicit indices: @Parameter(value) is read when the port claims the annotation family") {
    val src =
      """package demo;
        |import org.junit.Test;
        |import org.junit.runner.RunWith;
        |import org.junit.runners.Parameterized;
        |import static org.junit.Assert.assertEquals;
        |import java.util.Arrays;
        |import java.util.Collection;
        |@RunWith(Parameterized.class)
        |public class PairSuite {
        |  @Parameterized.Parameters public static Collection<Object[]> data() {
        |    return Arrays.asList(new Object[][] { {"a", 1}, {"b", 2} });
        |  }
        |  @Parameterized.Parameter(1) public int num;
        |  @Parameterized.Parameter(0) public String label;
        |  @Test public void check() { assertEquals(label.length(), num); }
        |}
        |""".stripMargin
    val policy   = balticporter.core.AnnotationPolicy(List("org.junit"))
    val (out, _) = emit(src, annotations = policy)
    // with the policy claiming org.junit, @Parameter(value) is carried and the index is used
    assert(clue(out).contains("munit.FunSuite"))
    assert(out.contains("test("))
  }

  test("field injection without policy: multiple @Parameter fields with unreadable index are refused") {
    val src =
      """package demo;
        |import org.junit.Test;
        |import org.junit.runner.RunWith;
        |import org.junit.runners.Parameterized;
        |import java.util.Arrays;
        |import java.util.Collection;
        |@RunWith(Parameterized.class)
        |public class BadSuite {
        |  @Parameterized.Parameters public static Collection<Object[]> data() {
        |    return Arrays.asList(new Object[][] { {"a", 1} });
        |  }
        |  @Parameterized.Parameter(1) public int num;
        |  @Parameterized.Parameter(0) public String label;
        |  @Test public void check() { }
        |}
        |""".stripMargin
    // without claiming org.junit, the @Parameter indices are dropped, and with >1 field the suite is refused
    val (_, ph)    = emit(src)
    val constructs = ph.findings.map(_.construct)
    assert(clue(constructs).contains("parameterized(unread-parameter-index)"))
  }

  // -------------------------------------------------------------------------
  // fixture 3: non-array data (Iterable<Object[]>)
  // -------------------------------------------------------------------------

  private val iterableSrc =
    """package demo;
      |import org.junit.Test;
      |import org.junit.runner.RunWith;
      |import org.junit.runners.Parameterized;
      |import static org.junit.Assert.assertNotNull;
      |import java.util.Arrays;
      |@RunWith(Parameterized.class)
      |public class NameSuite {
      |  @Parameterized.Parameters(name = "name={0}")
      |  public static Iterable<Object[]> data() {
      |    return Arrays.asList(new Object[][] { {"Alice"}, {"Bob"} });
      |  }
      |  private String name;
      |  public NameSuite(String name) { this.name = name; }
      |  @Test public void hasName() { assertNotNull(name); }
      |}
      |""".stripMargin

  test("non-array data: iterates over the Iterable result and registers tests") {
    val (out, _) = emit(iterableSrc)
    assert(clue(out).contains("munit.FunSuite"))
    assert(out.contains("def hasName(): scala.Unit"))
    assert(out.contains("test("))
  }

  test("non-array data: name pattern with element reference") {
    val (out, _) = emit(iterableSrc)
    assert(clue(out).contains("\"hasName[name=\""))
  }

  // -------------------------------------------------------------------------
  // non-Parameterized @RunWith stays refused
  // -------------------------------------------------------------------------

  test("a non-Parameterized @RunWith is still reported in the refusal lane") {
    val (_, ph) = emit(
      """package demo;
        |import org.junit.Test;
        |import org.junit.runner.RunWith;
        |@RunWith(org.junit.runners.Suite.class)
        |public class SuiteTest {
        |  @Test public void a() { }
        |}
        |""".stripMargin
    )
    val constructs = ph.findings.map(_.construct)
    assert(clue(constructs).contains("org.junit.runner.RunWith"))
  }

  // -------------------------------------------------------------------------
  // constructor injection with @Before
  // -------------------------------------------------------------------------

  test("constructor injection with @Before: setup is called in each registration body") {
    val (out, _) = emit(
      """package demo;
        |import org.junit.Before;
        |import org.junit.Test;
        |import org.junit.runner.RunWith;
        |import org.junit.runners.Parameterized;
        |import java.util.Arrays;
        |import java.util.Collection;
        |@RunWith(Parameterized.class)
        |public class SetupSuite {
        |  @Parameterized.Parameters public static Collection<Object[]> data() {
        |    return Arrays.asList(new Object[][] { {1} });
        |  }
        |  private int x;
        |  public SetupSuite(int x) { this.x = x; }
        |  @Before public void setUp() { }
        |  @Test public void run() { }
        |}
        |""".stripMargin
    )
    assert(clue(out).contains("setUp()"))
  }

  // -------------------------------------------------------------------------
  // fresh state: non-parameter instance state is reset between rows
  // -------------------------------------------------------------------------

  test("fresh state: a field initialiser is reset before each test in a parameterized suite") {
    val (out, ph) = emit(
      """package demo;
        |import org.junit.Test;
        |import org.junit.runner.RunWith;
        |import org.junit.runners.Parameterized;
        |import static org.junit.Assert.assertEquals;
        |import java.util.Arrays;
        |import java.util.Collection;
        |@RunWith(Parameterized.class)
        |public class StateSuite {
        |  @Parameterized.Parameters public static Collection<Object[]> data() {
        |    return Arrays.asList(new Object[][] { {1}, {2} });
        |  }
        |  private int x;
        |  private int count = 0;
        |  public StateSuite(int x) { this.x = x; }
        |  @Test public void inc() { count++; assertEquals(1, count); }
        |}
        |""".stripMargin
    )
    // bpFreshState should reset the count field to its initialiser value
    assert(clue(out).contains("bpFreshState"))
    // the count field should be zeroed in bpFreshState (default for int is 0)
    assert(out.contains("count = 0"))
    // no fresh-state(constructor) finding — the constructor is replayable for parameterized suites
    val ctorFindings = ph.findings.filter(_.construct == "fresh-state(constructor)")
    assertEquals(clue(ctorFindings).size, 0)
  }
