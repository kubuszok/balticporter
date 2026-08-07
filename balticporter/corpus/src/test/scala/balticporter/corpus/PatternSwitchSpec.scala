package balticporter.corpus

import balticporter.testkit.PortSuite

/** SE21 PATTERN CASE LABELS — catalog `JS-S10`, split honestly.
  *
  * The row's own sentence used to be "case labels are read as plain expressions and refused", and
  * the split this suite establishes is that HALF of it has an exact image and half does not:
  *
  *   - a TYPE PATTERN (`case String s ->`), a GUARD (`case Integer i when i > 3 ->`), a `null`
  *     label and `case null, default ->` are all things a scala `match` arm writes natively, so
  *     they LOWER;
  *   - a RECORD PATTERN (`case Point(int x, int y) ->`) and an UNNAMED pattern (`case _ ->`) do
  *     not, and the reason has moved: java's record deconstruction reads the record's ACCESSORS,
  *     and `JS-C43` now derives an `unapply` over exactly those on every emitted record — so the
  *     target exists and what is missing is the frontend's own arm (`ENGINE-LIMITS.md` T19). Those
  *     keep the loud refusal, per site, with a marker.
  *
  * Fixtures are the whole evidence, for `SwitchExpressionSpec`'s reason: no corpus library is
  * written to SE21.
  */
class PatternSwitchSpec extends PortSuite:

  private val patterns = port(
    """package p;
      |class P {
      |  int f(Object o) {
      |    return switch (o) {
      |      case String s -> s.length();
      |      case Integer i when i > 3 -> i;
      |      case null -> -1;
      |      default -> 0;
      |    };
      |  }
      |}
      |""".stripMargin)

  test("a TYPE PATTERN label is a scala typed pattern, binding included") {
    assert(clue(patterns.out).contains("case s: java.lang.String =>"), patterns.out)
    assert(clue(patterns.out).contains("s.length()"), patterns.out)
  }

  test("a GUARD (`when`) is the arm's `if` — and it is rendered, which `Tree.CaseDef.guard` was not") {
    assert(clue(patterns.out).contains("case i: java.lang.Integer if "), patterns.out)
  }

  test("`case null ->` is a null label, and it SUPPRESSES the synthetic NPE arm") {
    // JS-S08 adds `case null => throw new NullPointerException` to a reference-selector switch
    // BECAUSE java's classic switch has no way to opt out. SE21's pattern switch does, and adding
    // the throw ahead of it would invert exactly the behaviour the label exists to state.
    assert(clue(patterns.out).contains("case null =>"), patterns.out)
    assert(!clue(patterns.out).contains("NullPointerException"), patterns.out)
  }

  test("a PATTERN switch is EXHAUSTIVE, so no fall-out arm is synthesised beside java's default") {
    assertEquals(clue(patterns.out).linesIterator.count(_.contains("case _ =>")), 1, patterns.out)
  }

  // ---------------------------------------------------------------------------------------------

  private val nullDefault = port(
    """package p;
      |class Q {
      |  int f(Object o) { return switch (o) { case String s -> 1; case null, default -> 0; }; }
      |}
      |""".stripMargin)

  test("`case null, default ->` is ONE arm that is both — read from getIncludesDefault") {
    // Decided from an empty label list it would render `case null` and leave the switch with no
    // default at all; scala's `case _` matches null too, so the one arm covers both java meanings.
    assert(clue(nullDefault.out).contains("case _ =>"), nullDefault.out)
    assert(!clue(nullDefault.out).contains("NullPointerException"), nullDefault.out)
  }

  // ---------------------------------------------------------------------------------------------

  test("TWO arms may bind the SAME name — each is its own symbol, not one interned twice") {
    // `CtCasePattern` carries no source position, and a local's interning key is built from one —
    // so a naive key would collide these two `v`s onto a single symbol with a single type. Keyed on
    // the enclosing `CtCase` instead, which is real java at a real offset.
    val p = port(
      """package p;
        |class R {
        |  String f(Object o) {
        |    return switch (o) { case String v -> v; case Integer v -> v.toString(); default -> ""; };
        |  }
        |}
        |""".stripMargin)
    assert(clue(p.out).contains("case v: java.lang.String =>"), p.out)
    assert(clue(p.out).contains("case v: java.lang.Integer =>"), p.out)
    assert(clue(p.out).contains("v.toString()"), p.out)
  }
