package balticporter.corpus

import balticporter.emit.TirEmitter
import balticporter.frontend.spoon.SpoonTir
import balticporter.testkit.PortSuite
import balticporter.tir.{OmissionCheck, Pipeline}

/** A java enum's primary is its ROOT constructor, not the first one written (`CLAUDE.md` §4.4's
  * `super(args)` row read at an enum body). */
class EnumOverloadedCtorSpec extends PortSuite:

  private def emit(src: String) =
    val p = Pipeline.run(SpoonTir.fromSource(src), Nil)
    (new TirEmitter(p).emit, OmissionCheck.overloadedEnumCtors(p))

  test("the ROOT is the primary, and a delegating overload's constant carries the ROOT's arguments") {
    val (out, fs) = emit(
      """package enums;
        |enum Level {
        |  HIGH(3),
        |  LOW,
        |  ;
        |  final int bits;
        |  Level() { this(1); }
        |  Level(int bits) { this.bits = bits; }
        |  public int getBits() { return bits; }
        |}
        |""".stripMargin)
    assert(clue(out).contains("enum Level(private[enums] val bits: scala.Int) extends java.lang.Enum[Level]"))
    assert(out.contains("case HIGH extends Level(3)"))
    // the one that was silently 0 before: java ran `this(1)`
    assert(out.contains("case LOW extends Level(1)"))
    assertEquals(clue(fs), Nil)
  }

  // NOTE the header below moved once since this spec was written, and for a reason that is not
  // about overloading at all: a promoted parameter now carries the modifiers of the field it
  // SUPERSEDES, so `final int level` renders `private[enums1] val` (`EnumPromotedParamFlagsSpec`).
  // What this test is the negative for — the ROOT's parameter list and the constants' arguments —
  // is unchanged.
  test("NEGATIVE — a SINGLE-constructor enum is byte-identical to what the port shipped before") {
    val (out, fs) = emit(
      """package enums1;
        |enum Shade {
        |  DARK(1), LIGHT(2);
        |  final int level;
        |  Shade(int level) { this.level = level; }
        |}
        |""".stripMargin)
    assert(clue(out).contains("enum Shade(private[enums1] val level: scala.Int) extends java.lang.Enum[Shade]"))
    assert(out.contains("case DARK extends Shade(1)"))
    assert(out.contains("case LIGHT extends Shade(2)"))
    assertEquals(clue(fs), Nil)
  }

  test("a delegation ARGUMENT closed over its own parameter is REFUSED and COUNTED") {
    // inlining would need the constant's argument term substituted into the delegation, which is a
    // REWRITE and belongs to the funnel rather than to a rendering layer. The refusal keeps java's
    // own arguments, which is loud at a primary that does not take them.
    val (_, fs) = emit(
      """package enums2;
        |enum Tag {
        |  ONE("x"),
        |  TWO,
        |  ;
        |  final int n;
        |  Tag(String s) { this(s.length()); }
        |  Tag(int n) { this.n = n; }
        |}
        |""".stripMargin)
    // ONE ROW PER CONSTANT, which is the granularity a reader can act on: `ONE("x")` cannot reach a
    // root whose delegation reads `s`, and `TWO` matches no constructor's arity at all once the
    // nilary overload is the one that delegates.
    assertEquals(clue(fs).map(_.owner), List("enums2.Tag", "enums2.Tag"))
    assert(fs.exists(_.detail.contains("1 argument(s)")))
  }

  test("TWO overloads at ONE arity are refused — java resolved them and this engine does not") {
    val (_, fs) = emit(
      """package enums3;
        |enum Pick {
        |  A(1),
        |  B,
        |  ;
        |  final int n;
        |  Pick() { this(9); }
        |  Pick(int n) { this.n = n; }
        |  Pick(long n) { this((int) n); }
        |}
        |""".stripMargin)
    // `A(1)` is arity 1 and TWO constructors take one value parameter, so which one java chose is
    // not a question an arity match can answer; `B` is arity 0 and the nilary overload delegates
    // into the same ambiguity one hop later.
    assertEquals(clue(fs).map(_.owner), List("enums3.Pick", "enums3.Pick"))
  }
