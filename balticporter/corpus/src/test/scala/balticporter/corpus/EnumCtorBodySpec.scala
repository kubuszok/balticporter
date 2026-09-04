package balticporter.corpus

import balticporter.testkit.PortSuite

/** The Java ENUM lowering, pinned through the pipeline — a Java snippet in, the emitted Scala
  * asserted. */
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
    assertEmits(p, "enum Side(var ax: scala.Float, var ay: scala.Float) extends java.lang.Enum[Side]")
    assertEmits(p, "case UP extends Side(")
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
    assertEmits(p, "enum Filter(var glEnum: scala.Int) extends java.lang.Enum[Filter]")
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
    // …and it is now the SHAPE that the collision decides, not just the synthesis: a member named
    // `name` cannot coexist with `java.lang.Enum`'s final `name()` at all, so this enum keeps the
    // sealed lowering and is counted (`ENGINE-LIMITS.md` T21, `EnumShape.Reserved`).
    assertEmits(p, "sealed abstract class Algo(var name: java.lang.String)")
    assertNotEmits(p, "def name(): java.lang.String")
    assertEmits(p, "this.legibleName = name")
  }

  test("an enum with NO `name` parameter takes `name()` from the PARENT, not from a synthesis") {
    // The guard is still not a removal — what changed is where the member comes from. An enum the
    // scala 3 `enum` can express extends `java.lang.Enum[Plain]`, whose `name()` is FINAL and is
    // java's own; emitting the synthesis beside it would be an error rather than a duplicate.
    val p = port(
      """package p;
        |enum Plain { A, B; }
        |""".stripMargin
    )
    assertEmits(p, "enum Plain extends java.lang.Enum[Plain]")
    assertNotEmits(p, "def name(): java.lang.String = this.toString()")
  }

  test("`Enum.ordinal()` is the constant's DECLARATION INDEX — synthesised for the SEALED shape") {
    // Part of every java enum's surface whether the enum mentions it or not, and a library reaches
    // for it wherever the constants stand for consecutive integers somewhere else (gdx-vfx feeds
    // `lineStyle.ordinal()` into a shader `#define`). Absent, it is `value ordinal is not a member
    // of …` and there is no substitute a reader would reach for.
    val p = port(
      """package p;
        |enum Plain {
        |  A { public int n() { return 0; } },
        |  B { public int n() { return 1; } },
        |  C { public int n() { return 2; } };
        |  public abstract int n();
        |}
        |""".stripMargin
    )
    assertEmits(p, "def ordinal(): scala.Int")
    assertEmits(p, "case object A extends Plain {")
    assertEmits(p, "override def ordinal(): scala.Int = 0")
    assertEmits(p, "override def ordinal(): scala.Int = 1")
    assertEmits(p, "override def ordinal(): scala.Int = 2")
  }

  test("…and an EXPRESSIBLE enum synthesises neither — `java.lang.Enum` declares both, FINAL") {
    val p = port(
      """package p;
        |enum Bare { A, B, C; }
        |""".stripMargin
    )
    assertEmits(p, "enum Bare extends java.lang.Enum[Bare]")
    assertNotEmits(p, "def ordinal()")
    assertNotEmits(p, "def name()")
  }

  test("a constant's own BODY keeps its members, with the ordinal override beside them") {
    val p = port(
      """package p;
        |enum Op {
        |  ADD { public int apply(int a, int b) { return a + b; } },
        |  SUB { public int apply(int a, int b) { return a - b; } };
        |  public abstract int apply(int a, int b);
        |}
        |""".stripMargin
    )
    assertEmits(p, "def apply(a: scala.Int, b: scala.Int): scala.Int")
    assertEmits(p, "override def ordinal(): scala.Int = 1")
  }

  test("an enum that declares its OWN `ordinal` suppresses the synthesis — base AND constants") {
    // The `name` trap one member along: java's two namespaces let a FIELD carry the name beside the
    // final method; scala's one namespace cannot, and an abstract `def ordinal()` beside a
    // `var ordinal` is E120. The suppression must reach the CONSTANTS too, or every one of them
    // carries an `override` of a member the base no longer declares.
    val p = port(
      """package p;
        |enum Slot {
        |  HEAD(0), BODY(1);
        |  public final int ordinal;
        |  Slot(int ordinal) { this.ordinal = ordinal; }
        |}
        |""".stripMargin
    )
    assertNotEmits(p, "def ordinal(): scala.Int")
    assertNotEmits(p, "override def ordinal()")
  }

  // -- T11's OTHER half: the collidee is DECLARED, not synthesised ------------------------------

  test("a promoted enum parameter clashing with a DECLARED method is renamed, and the method stays") {
    val p = port(
      """package p;
        |enum Flavour {
        |  LIQUID("a", true), JEKYLL("b", false);
        |  private final boolean styled;
        |  public String folder;
        |  Flavour(String folder, boolean isStyled) {
        |    this.folder = folder;
        |    this.styled = isStyled;
        |  }
        |  public boolean isStyled() { return styled; }
        |}
        |""".stripMargin
    )
    // the parameter moves, not the method: the method is the java API and the `var` is the port's
    // own artefact (java's parameter was not a member at all).
    assertEmits(p, "isStyled$p: scala.Boolean")
    assertEmits(p, "def isStyled(): scala.Boolean")
    // the constants pass positionally, so the rename is invisible where it matters
    assertEmits(p, "case LIQUID extends Flavour(")
    // and the body's reference followed the symbol
    assertEmits(p, "this.styled = isStyled$p")
  }

  test("a promoted enum parameter that SUPERSEDES a field is NOT renamed") {
    // `var glEnum` IS the field, so the field is never emitted and cannot be a collidee. Renaming
    // the parameter would un-supersede it — emitting both, and breaking the self-assignment drop
    // that goes with it. This is the shape most java enums have, so a rename here would move the
    // emitted surface of every enum in the corpus.
    val p = port(
      """package p;
        |enum Filter {
        |  NEAREST(9728), LINEAR(9729);
        |  public final int glEnum;
        |  Filter(int glEnum) { this.glEnum = glEnum; }
        |}
        |""".stripMargin
    )
    assertEmits(p, "enum Filter(val glEnum: scala.Int) extends java.lang.Enum[Filter]")
    assertNotEmits(p, "glEnum$p")
  }
