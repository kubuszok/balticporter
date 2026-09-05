package balticporter.corpus

import balticporter.testkit.PortSuite
import balticporter.transform.ClassToTraitTransform

/** A subclass's OWN field assignment must survive `class-to-trait`. `stripSuperArgs` reconstructs
  * the constructor body as a `Block`; it must preserve the original `expr` so that statements stay
  * in `stats` where `CtorFunnel.stmtsOf` can read them. ENGINE-LIMITS A1. */
class ClassToTraitSubclassFieldSpec extends PortSuite:

  private val java =
    """package demo;
      |
      |abstract class Base {
      |  protected int cap;
      |  protected int max;
      |
      |  public Base() { this(16, Integer.MAX_VALUE); }
      |  public Base(int cap) { this(cap, Integer.MAX_VALUE); }
      |  public Base(int cap, int max) {
      |    this.cap = cap;
      |    this.max = max;
      |  }
      |
      |  abstract protected Object newObject();
      |}
      |
      |class Sub extends Base {
      |  private final Object effect;
      |
      |  public Sub(Object effect, int cap, int max) {
      |    super(cap, max);
      |    this.effect = effect;
      |  }
      |
      |  protected Object newObject() { return effect; }
      |}
      |""".stripMargin

  private val mappings = List(
    ClassToTraitTransform.ParamMapping(0, "cap"),
    ClassToTraitTransform.ParamMapping(1, "max"),
  )

  private val phase = ClassToTraitTransform(Map("demo.Base" -> mappings))

  test("subclass's own field assignment survives class-to-trait") {
    val p = port(java, phase)
    // The promoted constructor body must contain the assignment to the subclass's own field.
    // Java's `private final Object effect;` has no initializer (rhs = None), so the field
    // declaration is `var effect = uninitialized` — but the constructor ASSIGNMENT must be
    // emitted, or the field stays null at run time.
    assertEmits(p, "this.effect = effect")
  }

  test("override vals for parent's mapped params are still emitted") {
    val p = port(java, phase)
    assertEmits(p, "override val cap")
    assertEmits(p, "override val max")
  }
