package balticporter.transform

import balticporter.emit.TirEmitter
import balticporter.frontend.spoon.SpoonTir
import balticporter.tir.{ Decision, Pipeline, Reason }

/** `TestFrameworkTransform` handling of JUnit 4 `@RunWith(Parameterized.class)` — constructor injection, field injection, non-array data, and name patterns. Each fixture is a neutral name (no library
  * named in code).
  */
class ParameterizedTransformSpec extends munit.FunSuite:

  private def emit(java: String): (String, TestFrameworkTransform) =
    val ph    = new TestFrameworkTransform
    val after = Pipeline.run(SpoonTir.fromSource(java), List(ph))
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
    val (out, ph) = emit(ctorSrc)
    assert(clue(out).contains("munit.FunSuite"))
    assert(out.contains("def add(): scala.Unit"))
    assert(out.contains("def subtract(): scala.Unit"))
  }

  test("constructor injection: test registrations contain the name pattern segments") {
    val (out, _) = emit(ctorSrc)
    assert(clue(out).contains("test("))
    // the name pattern splits into literals and row element accesses joined by +
    assert(out.contains("\"add[\""))
    assert(out.contains("\"subtract[\""))
  }

  test("constructor injection: registrations iterate over the data method result") {
    val (out, _) = emit(ctorSrc)
    // ForEach over the data method call
    assert(clue(out).contains("data()"))
  }

  test("constructor injection: no @RunWith finding since it is handled") {
    val (_, ph)    = emit(ctorSrc)
    val constructs = ph.findings.map(_.construct)
    assert(!clue(constructs).contains("org.junit.runner.RunWith"))
    assert(!constructs.contains("org.junit.runners.Parameterized.Parameters"))
  }

  test("constructor injection: decisions record the parameterized runner") {
    val (_, _, log) = emitTraced(ctorSrc)
    val ds          = log.of(Decision.Kind.RetypedSignature)
    assert(clue(ds).nonEmpty)
    assert(ds.forall(_.reason == Reason.Universal("test-framework/parameterized")))
    assert(ds.exists(_.detail.get("runner").contains("Parameterized")))
    assert(ds.exists(_.detail.get("injection").contains("constructor")))
  }

  test("constructor injection: fields that were constructor params are made mutable") {
    val (out, _) = emit(ctorSrc)
    // constructor params must be assignable from the row
    assert(clue(out).contains("var a: scala.Int"))
    assert(out.contains("var b: scala.Int"))
    assert(out.contains("var expected: scala.Int"))
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
    val (out, ph) = emit(fieldSrc)
    assert(clue(out).contains("munit.FunSuite"))
    assert(out.contains("def checkFlag(): scala.Unit"))
    assert(out.contains("test("))
  }

  test("field injection: @Parameter and @RunWith are not in the refusal lane") {
    val (_, ph)    = emit(fieldSrc)
    val constructs = ph.findings.map(_.construct)
    assert(!clue(constructs).contains("org.junit.runner.RunWith"))
    // Spoon may spell the nested annotation with $ or . — neither form should appear
    assert(!constructs.exists(c => c.contains("Parameterized") && c.contains("Parameter")))
  }

  test("field injection: decisions record field injection mode") {
    val (_, _, log) = emitTraced(fieldSrc)
    val ds          = log.of(Decision.Kind.RetypedSignature)
    assert(ds.exists(_.detail.get("injection").contains("field")))
  }

  // -------------------------------------------------------------------------
  // fixture 3: non-array data (Iterable<Object>)
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
