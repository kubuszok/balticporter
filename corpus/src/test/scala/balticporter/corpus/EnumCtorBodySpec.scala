package balticporter.corpus

import balticporter.testkit.PortSuite

/** The Java ENUM lowering, pinned through the pipeline — a Java snippet in, the emitted Scala
  * asserted.
  *
  * A Java enum constructor is an ordinary constructor: it has a body and the body RUNS. The
  * emitter kept its PARAMETERS (a `case object` has to be able to pass its arguments) and dropped
  * the constructor outright, so every field the body assigned stayed at its declared default — in a
  * port that compiled with zero errors and moved no check count, which is CLAUDE.md §3's defect
  * class exactly.
  *
  * Two worked examples, and note that only the second one a compiler could ever have told you
  * about:
  *
  *   - libGDX `Cubemap.CubemapSide` builds `up` and `direction` from six float parameters. All six
  *     sides shipped with `up == null`; `getUp(out)` threw. Nothing in the corpus called it.
  *   - anim8 `Dithered.DitherAlgorithm` assigns `legibleName` from a `String name` parameter, so
  *     `toString()` returned null for all 22 constants — AND the promoted `var name` collided with
  *     the synthesised `Enum.name()`, which is the one error that made it visible at all.
  */
class EnumCtorBodySpec extends PortSuite:

  test("an enum constructor's BODY runs — the field it computes is not left at its default") {
    val p = port(
      """package p;
        |enum Side {
        |  UP(0f, 1f), DOWN(0f, -1f);
        |  public String label;
        |  public float x, y;
        |  Side(float ax, float ay) {
        |    this.x = ax;
        |    this.y = ay;
        |    this.label = "" + ax + "/" + ay;
        |  }
        |}
        |""".stripMargin
    )
    // the computed field, which the old lowering dropped entirely
    assertEmits(p, "this.label = ")
    assertEmits(p, "this.x = ax")
    // …and the parameters are still promoted, so `case object UP extends Side(0f, 1f)` has somewhere
    // to pass its arguments.
    assertEmits(p, "sealed abstract class Side(var ax: scala.Float, var ay: scala.Float)")
    assertEmits(p, "case object UP extends Side(")
  }

  test("a PURE self-assignment is dropped — the promotion already performed it") {
    // `this.glEnum = glEnum` is what most java enum constructors are, and `var glEnum` IS the
    // parameter. Re-emitting it is correct and pure churn; dropping it is safe only in that exact
    // shape, which is why the test above asserts the computing assignment survives.
    val p = port(
      """package p;
        |enum Filter {
        |  NEAREST(9728), LINEAR(9729);
        |  Filter(int glEnum) { this.glEnum = glEnum; }
        |  public int glEnum;
        |  public int get() { return glEnum; }
        |}
        |""".stripMargin
    )
    assertEmits(p, "sealed abstract class Filter(var glEnum: scala.Int)")
    assertNotEmits(p, "this.glEnum = glEnum")
  }

  test("a constructor parameter named `name` suppresses the synthesised `Enum.name()`") {
    // Java never has to choose: `Enum.name()` is FINAL there and a parameter is not a member at
    // all. Scala gets both from the promotion, and E120 "Conflicting definitions" is the result.
    val p = port(
      """package p;
        |enum Algo {
        |  NONE("None"), WREN("Wren");
        |  public String legibleName;
        |  Algo(String name) { this.legibleName = name; }
        |  public String toString() { return legibleName; }
        |}
        |""".stripMargin
    )
    assertEmits(p, "sealed abstract class Algo(var name: java.lang.String)")
    assertNotEmits(p, "def name(): java.lang.String")
    assertEmits(p, "this.legibleName = name")
  }

  test("an enum with NO `name` parameter still gets `Enum.name()` — the guard is not a removal") {
    val p = port(
      """package p;
        |enum Plain { A, B; }
        |""".stripMargin
    )
    assertEmits(p, "def name(): java.lang.String = this.toString()")
  }
