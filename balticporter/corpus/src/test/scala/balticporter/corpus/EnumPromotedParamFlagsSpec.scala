package balticporter.corpus

import balticporter.testkit.PortSuite

/** A PROMOTED ENUM PARAMETER THAT SUPERSEDES A FIELD IS THAT FIELD — its ACCESS LEVEL and its
  * MUTABILITY included. */
class EnumPromotedParamFlagsSpec extends PortSuite:

  test("a PRIVATE FINAL field's flags reach the parameter that supersedes it") {
    val p = port(
      """package p;
        |enum Status {
        |  OK(0), ERROR(2);
        |  final private int level;
        |  Status(int level) { this.level = level; }
        |  public boolean worseThan(Status o) { return this.level > o.level; }
        |}
        |""".stripMargin
    )
    // `private[Status]` and not bare `private`: java's boundary is the top-level enclosure, which
    // includes the constants — and the constants are `case`s of this very enum.
    assertEmits(p, "enum Status(private[Status] val level: scala.Int) extends java.lang.Enum[Status]")
    // THE NEGATIVE, and it is the whole finding: the emitted parameter was a public `var`, so a
    // caller could assign a shared singleton's field. It must not be one again.
    assertNotEmits(p, "var level")
    // an OTHER-INSTANCE read still resolves — scala's `private` is class-private, not this-private.
    assertEmits(p, "o.level")
  }

  test("a PACKAGE-PRIVATE final field renders at the package qualifier, not at the enum") {
    val p = port(
      """package p;
        |enum Tag {
        |  A("a"), B("b");
        |  final java.lang.String tag;
        |  Tag(java.lang.String tag) { this.tag = tag; }
        |}
        |""".stripMargin
    )
    assertEmits(p, "enum Tag(private[p] val tag: java.lang.String)")
    assertNotEmits(p, "var tag")
  }

  test("a PUBLIC field keeps a public parameter, and a MUTABLE one keeps `var`") {
    val p = port(
      """package p;
        |enum Slot {
        |  ONE(1), TWO(2);
        |  public int bits;
        |  public final int mask;
        |  Slot(int bits) { this.bits = bits; this.mask = 7; }
        |}
        |""".stripMargin
    )
    // java did not write `final` on `bits`, so the port may not write `val`.
    assertEmits(p, "enum Slot(var bits: scala.Int)")
    // …and the un-superseded final field is untouched by any of this.
    assertEmits(p, "mask")
  }

  test("a java-FINAL field the constructor COMPUTES stays a `var` — the write survives the drop") {
    // `this.x = x * 2` is legal java for a final field and is NOT the self-assignment the promotion
    // performs, so it survives into the class body — where a `val` cannot be its target. The
    // mutability question is therefore decided by the write and not by java's modifier alone.
    val p = port(
      """package p;
        |enum Doubled {
        |  A(3), B(4);
        |  public final int x;
        |  Doubled(int x) { this.x = x * 2; }
        |}
        |""".stripMargin
    )
    assertEmits(p, "enum Doubled(var x: scala.Int)")
    assertEmits(p, "this.x = x * 2")
  }

  test("a NESTED enum's private field takes the TOP-LEVEL qualifier, which is java's own scope") {
    val p = port(
      """package p;
        |public class Outer {
        |  public enum Kind {
        |    ONE(1), TWO(2);
        |    private final int code;
        |    Kind(int code) { this.code = code; }
        |    public int read() { return code; }
        |  }
        |}
        |""".stripMargin
    )
    assertEmits(p, "private[Outer] val code: scala.Int")
    assertNotEmits(p, "var code")
  }

  test("the SEALED shape carries the same modifiers, where a CONSTANT BODY reads them") {
    // a constant with a body is what sends an enum to the sealed lowering, and it is also the
    // reader that bare `private` would not compile for.
    val p = port(
      """package p;
        |enum Op {
        |  ADD(1) { public int apply(int a) { return a + step; } },
        |  SUB(2) { public int apply(int a) { return a - step; } };
        |  private final int step;
        |  Op(int step) { this.step = step; }
        |  public abstract int apply(int a);
        |}
        |""".stripMargin
    )
    assertEmits(p, "sealed abstract class Op(private[Op] val step: scala.Int)")
    assertNotEmits(p, "var step")
  }
