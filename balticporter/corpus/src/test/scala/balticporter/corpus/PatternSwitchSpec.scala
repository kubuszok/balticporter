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
  *   - a RECORD PATTERN (`case Point(int x, int y) ->`) does too, now that `JS-C43` derives an
  *     `unapply` over the record's ACCESSORS — which is the member java's own deconstruction reads
  *     (JLS 14.30.1), and the reason a scala `case class` could not have supplied it;
  *   - an UNNAMED pattern keeps the refusal, and it is one nobody can trigger: no source Spoon
  *     accepts builds a `CtUnnamedPattern` at all (`ENGINE-LIMITS.md` T19).
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

  // ---------------------------------------------------------------------------------------------
  // THE RECORD PATTERN — `ENGINE-LIMITS.md` T19, unblocked by `JS-C43`'s derived extractor
  // ---------------------------------------------------------------------------------------------

  /** the record itself, so a test can assert on the extractor the arms below NAME. */
  private val theRecord = port("package p;\npublic record Pt(int x, int y) { }\n")

  /** three records plus a switch over them — several units, because a record pattern names a type
    * the switch's own compilation unit does not declare, which is the shape a corpus has. */
  private def switchOn(body: String) = portAll(List(
    "Pt.java"  -> "package p;\npublic record Pt(int x, int y) { }\n",
    "One.java" -> "package p;\npublic record One(String only) { }\n",
    "Box.java" -> "package p;\npublic record Box(Object a, Pt b) { }\n",
    "S.java"   -> s"package p;\nclass S {\n$body}\n"))

  test("a RECORD PATTERN is a scala CONSTRUCTOR pattern over the derived extractor") {
    val p = switchOn(
      """  int f(Object o) {
        |    return switch (o) { case Pt(int x, int y) -> x + y; default -> 0; };
        |  }
        |""".stripMargin)
    assert(clue(p.out).contains("case p.Pt(x, y) =>"), p.out)
    assert(clue(p.out).contains("x + y"), p.out)
  }

  test("an UNCONDITIONAL component pattern is the BINDING ALONE, and that is not cosmetic") {
    // JLS 14.30.2: where the pattern's type already covers the component's, java matches a `null`
    // component. Scala's `case One(s: String)` does not match null at all, so a type test here
    // would be a different program — measured in both languages on the same fixture.
    val p = switchOn(
      """  String f(Object o) {
        |    return switch (o) { case One(String s) -> s; default -> ""; };
        |  }
        |""".stripMargin)
    assert(clue(p.out).contains("case p.One(s) =>"), p.out)
    assert(!clue(p.out).contains("case p.One(s: java.lang.String)"), p.out)
  }

  test("…and a NARROWING one keeps the type test, because java tests too") {
    // `Box`'s first component is `Object`; `case Box(String s, …)` really narrows, and java's own
    // pattern then does NOT match a null component either.
    val p = switchOn(
      """  String f(Object o) {
        |    return switch (o) { case Box(String s, Pt q) -> s; default -> ""; };
        |  }
        |""".stripMargin)
    assert(clue(p.out).contains("s: java.lang.String"), p.out)
  }

  test("a NESTED record pattern is a nested constructor pattern") {
    val p = switchOn(
      """  int f(Object o) {
        |    return switch (o) { case Box(Object a, Pt(int x, int y)) -> x + y; default -> 0; };
        |  }
        |""".stripMargin)
    assert(clue(p.out).contains("case p.Box(a, p.Pt(x, y)) =>"), p.out)
  }

  test("`var` in a component position is the component's own type, so it binds unconditionally") {
    val p = switchOn(
      """  int f(Object o) {
        |    return switch (o) { case Pt(var x, var y) -> x + y; default -> 0; };
        |  }
        |""".stripMargin)
    assert(clue(p.out).contains("case p.Pt(x, y) =>"), p.out)
  }

  test("the record half no longer mints a marker — the whole label lowers") {
    val p = switchOn(
      """  int f(Object o) {
        |    return switch (o) { case Pt(int x, int y) -> x; default -> 0; };
        |  }
        |""".stripMargin)
    assert(!clue(p.out).contains("compiletime.error"), p.out)
    assertConsults(p, balticporter.catalog.JS.S(10), fired = true)
    // …and the record itself carries the extractor this arm NAMES, which is what makes the pair a
    // lowering rather than two halves that happen to agree.
    assert(clue(theRecord.out).contains("def unapply(r$rec: Pt): (scala.Int, scala.Int)"), theRecord.out)
  }
