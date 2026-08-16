package balticporter.corpus

import balticporter.testkit.PortSuite

/** WHICH field a promoted enum constructor parameter SUPERSEDES — a (name, TYPE) question, never a
  * name one.
  *
  * The lowering renders every primary parameter as a `var` member of the emitted enum, so a body
  * field of the same name would be a second member under one name and cannot be emitted. Where the
  * parameter really IS the field — `Filter(int glEnum)` beside `public int glEnum`, whose whole
  * constructor is `this.glEnum = glEnum` — dropping the field is exact and the `var` carries it.
  *
  * ==And java's TWO variable scopes make the other shape ordinary==
  * A constructor parameter routinely names a field it is not, precisely so the constructor can
  * COMPUTE one from the other: `Handler(String open)` beside `public final Pattern open`, whose body
  * is `this.open = Pattern.compile(open, …)`. Java resolves `open` to the parameter and `this.open`
  * to the field, and both members exist. Matched on the name alone the field is DROPPED and the enum
  * ships `var open: String` under the field's name — every read of it is `value pattern is not a
  * member of String`, and the constructor assigns a `Pattern` to a `String`. `CLAUDE.md` §4.56 at a
  * rename: two names being equal is not a structural fact about anything
  * (`ENGINE-LIMITS.md` T11's third half).
  */
class EnumCtorParamSupersedesSpec extends PortSuite:

  test("a DIFFERENT type is a DIFFERENT member — the field survives and the parameter moves aside") {
    val p = port(
      """package p;
        |import java.util.regex.Pattern;
        |enum Marker {
        |  OPEN("<a>", true), CLOSE("</a>", false);
        |  public final Pattern open;
        |  public final boolean strict;
        |  Marker(String open, boolean strict) {
        |    this.open = open == null ? null : Pattern.compile(open);
        |    this.strict = strict;
        |  }
        |  public String source() { return open.pattern(); }
        |}
        |""".stripMargin
    )
    // the PARAMETER is a `String` and moves aside; `strict` is the same member at the same type and
    // stays superseded, which is what keeps `TextureFilter(glEnum)`-shaped enums byte-for-byte.
    assertEmits(p, "enum Marker(var open$p: java.lang.String, var strict: scala.Boolean)")
    // …the FIELD is emitted, at java's own type …
    assertEmits(p, "var open: java.util.regex.Pattern")
    // …the constructor fills it, reading the renamed parameter …
    assertEmits(p, "this.open = if (open$p == null) null")
    // …and the reader gets java's member back.
    assertEmits(p, "return this.open.pattern()")
    // the self-assignment of the SUPERSEDED one is still dropped — the promotion performed it.
    assertNotEmits(p, "this.strict = strict")
  }

  test("NEGATIVE — the SAME type still supersedes, and nothing about that emission moves") {
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
    // the shape every corpus enum is in: one member, promoted, self-assignment dropped, no rename.
    assertEmits(p, "enum Filter(var glEnum: scala.Int) extends java.lang.Enum[Filter]")
    assertNotEmits(p, "this.glEnum = glEnum")
    assertNotEmits(p, "glEnum$p")
  }

  test("NEGATIVE — a WIDENING assignment is not superseding either, and the field is then filled") {
    // `this.bits = bits` type-checks in java by widening `int` to `long`, so the two really are two
    // members. Read on the name alone the field vanished and the enum carried an `Int` where every
    // reader wanted a `Long`; read on the type, the field survives, the parameter moves, and the
    // assignment that fills it is no longer a self-assignment and is therefore not dropped.
    val p = port(
      """package p;
        |enum Slot {
        |  ONE(1), TWO(2);
        |  public long bits;
        |  Slot(int bits) { this.bits = bits; }
        |}
        |""".stripMargin
    )
    assertEmits(p, "enum Slot(var bits$p: scala.Int)")
    assertEmits(p, "var bits: scala.Long")
    assertEmits(p, "this.bits = bits$p")
  }
