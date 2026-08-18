package balticporter.corpus

import balticporter.testkit.PortSuite

/** A PROMOTED ENUM PARAMETER THAT SUPERSEDES A FIELD IS THAT FIELD — its ACCESS LEVEL and its
  * MUTABILITY included.
  *
  * `CtorFunnel.enumSupersededFields` decides that a parameter and a body field are ONE member, on
  * (name, TYPE), and the emitter then writes the parameter and drops the field. What it wrote was
  * `var <name>: <T>`, unqualified, whatever java had declared — so `final private int level` shipped
  * as `enum ParsedOptionStatus(var level: scala.Int)`, and `ParsedOptionStatus.ERROR.level = 0`
  * compiled and mutated a shared singleton. No instrument here can see that: the port compiles,
  * every check count is flat, and a write nobody performs is a widening nobody notices
  * (`ENGINE-LIMITS.md` T11's fourth half).
  *
  * ==The three modifiers, and where each comes from==
  *   - the ACCESS LEVEL is [[balticporter.emit.Visibility]]'s answer FOR THE FIELD SYMBOL, so §8.7's
  *     mapping and its `WidenedVisibility` residue govern the parameter exactly as they govern the
  *     field. This rendering invents no widening of its own;
  *   - a bare `private` is QUALIFIED WITH THE ENUM. Java's `private` reaches the whole top-level
  *     enclosure (JLS 6.6.1) and an enum CONSTANT's body is inside it, while scala's bare `private`
  *     on a class parameter is not visible from a `case object` extending that class. Probed both
  *     ways against scalac 3.8.4: `private val glEnum` is `value glEnum is not a member of object
  *     F.LINEAR` at a constant body, `private[F] val glEnum` compiles there and in the companion's
  *     nested types, and `F.NEAREST.glEnum` from outside stays refused;
  *   - `val` where java wrote `final` AND nothing the promotion left behind writes it — a java final
  *     field is assignable in the constructor, and a constructor statement that is not the dropped
  *     self-assignment SURVIVES into the class body, where a scala `val` cannot be its target.
  */
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
