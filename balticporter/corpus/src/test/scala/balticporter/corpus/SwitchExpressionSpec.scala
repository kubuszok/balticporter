package balticporter.corpus

import balticporter.testkit.PortSuite

/** JLS 15.28 SWITCH EXPRESSIONS and JLS 14.21 `yield` — catalog `JS-S09`.
  *
  * The evidence for this row is entirely FIXTURES, and deliberately so: no library in the corpus is
  * written to SE14 or later, so a measurement over the corpus can only ever say zero. That is the
  * same position `TextBlockSpec` was written from, with the opposite outcome — there the probe
  * found no difference at all, here there is one and it has an exact image.
  *
  * What makes the image exact is that a scala `match` IS an expression, so the construct needed no
  * new node for the switch itself. It needed one for the single shape scala has no counterpart for:
  * a `yield` that is NOT the arm's last statement completes the whole switch expression abruptly
  * from arbitrary depth, and scala has no expression-level jump. That is `Tree.Yield`, and the
  * emitter renders it as the value-carrying `scala.util.boundary` it is.
  *
  * Every test below names the JLS shape it covers, because "every shape" is a claim a reader must
  * be able to check against 15.28's own grammar: arrow-expression arms, arrow-block arms with an
  * explicit `yield`, an arrow `throw` arm, multi-label arms, colon-form arms with `yield`,
  * colon-form fallthrough, an exhaustive enum switch with no `default`, a nested switch expression,
  * a non-tail `yield`, and the null-selector rule composing with `JS-S08`.
  */
class SwitchExpressionSpec extends PortSuite:

  // ---------------------------------------------------------------------------------------------
  // ARROW ARMS — JLS 15.28's `case L -> Expression ;`
  // ---------------------------------------------------------------------------------------------

  private val arrows = port(
    """package p;
      |class S {
      |  int pick(int k) {
      |    return switch (k) { case 1 -> 10; case 2, 3 -> 20; default -> compute(k); };
      |  }
      |  int compute(int k) { return k; }
      |}
      |""".stripMargin)

  test("a switch EXPRESSION is a `match` in value position, with the arms java wrote") {
    assert(clue(arrows.out).contains("case 1 =>"), arrows.out)
    assert(clue(arrows.out).contains("case 2 | 3 =>"), arrows.out)
    assert(clue(arrows.out).contains("case _ =>"), arrows.out)
  }

  test("an ARROW arm does not fall through — JLS 14.11.2, so no next-case tail is duplicated") {
    // Fallthrough is lowered by DUPLICATING the next case's tail into this arm, so the evidence is
    // a count and not a shape: each arm's value appears exactly once. A rule that read only for a
    // `break` terminator would have found none on an arrow arm and duplicated every one of them.
    assertEquals(clue(arrows.out).sliding(2).count(_ == "10"), 1, arrows.out)
    assertEquals(clue(arrows.out).sliding(2).count(_ == "20"), 1, arrows.out)
  }

  test("NO fall-out arm is synthesised for an EXPRESSION — 15.28.1 makes it exhaustive") {
    // A switch expression cannot fall out, so java's `default` is the only default there is. Two
    // `case _` arms would mean the engine had added one of its own — which would answer `()` where
    // java answers nothing, and widen the expression's type with it.
    assertEquals(clue(arrows.out).linesIterator.count(_.contains("case _ =>")), 1, arrows.out)
  }

  // ---------------------------------------------------------------------------------------------
  // ARROW BLOCK + `yield` — JLS 15.28's `case L -> Block`, and 14.21
  // ---------------------------------------------------------------------------------------------

  private val blocks = port(
    """package p;
      |class B {
      |  int f(int k) {
      |    return switch (k) {
      |      case 1 -> { int t = k * 2; yield t; }
      |      case 2 -> throw new RuntimeException("no");
      |      default -> 0;
      |    };
      |  }
      |}
      |""".stripMargin)

  test("an arrow BLOCK arm keeps its statements and its TAIL `yield` becomes the arm's value") {
    // The `yield` is peeled: a scala arm's value is its last expression already, so carrying the
    // jump would make the arm want a boundary it does not need.
    assert(clue(blocks.out).contains("val t"), blocks.out)
    assert(!clue(blocks.out).contains("boundary.break"), blocks.out)
  }

  test("an arrow THROW arm is the arm's body — a `Nothing` conforms wherever the value is used") {
    assert(clue(blocks.out).contains("throw new java.lang.RuntimeException"), blocks.out)
  }

  // ---------------------------------------------------------------------------------------------
  // A NON-TAIL `yield` — the one shape with no scala counterpart
  // ---------------------------------------------------------------------------------------------

  private val nonTail = port(
    """package p;
      |class N {
      |  int f(int k) {
      |    return switch (k) {
      |      default -> { if (k > 0) { yield 1; } yield 2; }
      |    };
      |  }
      |}
      |""".stripMargin)

  test("a NON-TAIL `yield` gets a value-carrying boundary around the ARM, named") {
    // `yield` from inside the `if` leaves the whole switch expression. Scala has no expression-level
    // jump, so the exact image is `boundary`/`break` — with the `Label` typed at the switch's own
    // type, which is what makes it a different boundary from the `Unit`-carrying one a mid-case
    // `break` gets.
    assert(clue(nonTail.out).contains("scala.util.boundary { (yield$1: scala.util.boundary.Label["),
      nonTail.out)
    assert(clue(nonTail.out).contains("scala.util.boundary.break(1)(using yield$1)"), nonTail.out)
    // …and the TAIL one is still the block's value, not a second break.
    assert(!clue(nonTail.out).contains("break(2)"), nonTail.out)
  }

  // ---------------------------------------------------------------------------------------------
  // COLON FORM — JLS 15.28's `SwitchLabeledStatementGroup`, which DOES fall through
  // ---------------------------------------------------------------------------------------------

  private val colon = port(
    """package p;
      |class C {
      |  int f(int k) {
      |    return switch (k) {
      |      case 1: int t = k + 1; yield t;
      |      case 2:
      |      case 3: yield 9;
      |      default: yield 0;
      |    };
      |  }
      |}
      |""".stripMargin)

  test("a COLON-form arm yields, and an empty label group still merges into the next arm") {
    assert(clue(colon.out).contains("case 2 | 3 =>"), colon.out)
    assert(clue(colon.out).contains("val t"), colon.out)
  }

  test("a colon-form `yield` TERMINATES its arm — nothing is duplicated into it") {
    // `yield` completes the switch expression abruptly (JLS 14.21), so the `case 1` arm cannot run
    // on into `case 2`. A terminator test that only knew `return` and `throw` would have appended
    // the `9`.
    val arm = clue(colon.out).linesIterator.find(_.contains("case 1 =>")).getOrElse("")
    assert(!arm.contains("9"), arm)
  }

  // ---------------------------------------------------------------------------------------------
  // EXHAUSTIVE ENUM, NO `default` — 15.28.1's own exhaustiveness
  // ---------------------------------------------------------------------------------------------

  private val enums = port(
    """package p;
      |class E {
      |  enum K { A, B }
      |  int f(K k) { return switch (k) { case A -> 1; case B -> 2; }; }
      |}
      |""".stripMargin)

  test("an exhaustive enum switch expression gets NO fall-out arm — java has no fall-out to model") {
    // Asserted on the SYNTHESISED arm's own text (`case _ => ()`) rather than on `case _`, because
    // the emitted enum companion carries a `valueOf` whose `match` has a `case _ => throw` of its
    // own — a bare-`case _` assertion would have been reading that and saying nothing about this.
    assert(!clue(enums.out).contains("case _ => ()"), enums.out)
    // …and the same text on the STATEMENT form, so the assertion above is a difference and not a
    // string that never appears.
    val stmtNoDefault = port(
      """package p;
        |class E2 {
        |  enum K { A, B }
        |  int seen = 0;
        |  void f(K k) { switch (k) { case A: seen = 1; break; case B: seen = 2; break; } }
        |}
        |""".stripMargin)
    assert(clue(stmtNoDefault.out).contains("case _ => ()"), stmtNoDefault.out)
  }

  // ---------------------------------------------------------------------------------------------
  // COMPOSITION with JS-S08 — the null selector
  // ---------------------------------------------------------------------------------------------

  private val strSel = port(
    """package p;
      |class Z {
      |  int f(String s) { return switch (s) { case "a" -> 1; default -> 0; }; }
      |}
      |""".stripMargin)

  test("the NULL-SELECTOR rule composes: a reference selector still gets java's implicit NPE") {
    // JS-S08 is decided in the emitter, at `Tree.Match`, so it reaches an expression switch by
    // construction — java throws NPE on a null `String` selector in either position (JLS 14.11.2's
    // rule, which 15.28 inherits).
    assert(clue(strSel.out).contains("case null => throw new java.lang.NullPointerException"), strSel.out)
  }

  // ---------------------------------------------------------------------------------------------
  // NESTING
  // ---------------------------------------------------------------------------------------------

  private val nested = port(
    """package p;
      |class Nst {
      |  int f(int a, int b) {
      |    return switch (a) { default -> switch (b) { default -> 9; }; };
      |  }
      |}
      |""".stripMargin)

  test("a NESTED switch expression is an ordinary nested `match`") {
    assertEquals(clue(nested.out).sliding(" match {".length).count(_ == " match {"), 2, nested.out)
  }

  // ---------------------------------------------------------------------------------------------
  // …and the construct that is NOT one of those: a `yield` through a nested switch STATEMENT.
  //
  // JLS 14.21 binds a `yield` to the innermost enclosing switch EXPRESSION, and a switch STATEMENT
  // is not one — so this is ordinary java and javac (22.0.2) runs it: `f(1,2)` is 10, `f(1,3)` is
  // 20, `f(5,2)` is 0. The `yield 10` completes the OUTER expression from two constructs down.
  // ---------------------------------------------------------------------------------------------

  private val throughStmt = port(
    """package p;
      |class T {
      |  int f(int a, int b) {
      |    return switch (a) {
      |      case 1 -> {
      |        switch (b) {
      |          case 2: yield 10;
      |          default: break;
      |        }
      |        yield 20;
      |      }
      |      default -> 0;
      |    };
      |  }
      |}
      |""".stripMargin)

  test("a `yield` through a nested switch STATEMENT targets the OUTER expression") {
    // Both halves of the defect are in one emission. `yieldsOut` stopped at any `Tree.Match`, so
    // the OUTER arm was told it held no yield and got no value-carrying boundary; and `matchStr`
    // minted one for the INNER match anyway, at that match's own type — `Label[Unit]` for a
    // statement switch — so the emitted `break(10)` had nothing of the right type to jump to.
    // A `Label[scala.Int]`, opened by the outer arm, is the only boundary in this program.
    assert(clue(throughStmt.out).contains("scala.util.boundary.Label[scala.Int]"), throughStmt.out)
    assert(!clue(throughStmt.out).contains("scala.util.boundary.Label[scala.Unit]"), throughStmt.out)
    assertEquals(clue(throughStmt.out).sliding("scala.util.boundary {".length)
      .count(_ == "scala.util.boundary {"), 1, throughStmt.out)
    assert(clue(throughStmt.out).contains("scala.util.boundary.break(10)(using yield$1)"), throughStmt.out)
  }

  test("…and the statement switch inside it keeps its own fall-out arm and its own `break`") {
    // The inner switch is still a statement switch: `default: break;` terminates its case, and
    // nothing about the outer expression changes what it is.
    assertEquals(clue(throughStmt.out).sliding(" match {".length).count(_ == " match {"), 2, throughStmt.out)
  }

  // ---------------------------------------------------------------------------------------------
  // THE STATEMENT FORM, arrow-style — Spoon's `CtYieldStatement` wrapper is NOT java
  // ---------------------------------------------------------------------------------------------

  private val stmt = port(
    """package p;
      |class St {
      |  int seen = 0;
      |  void f(int k) { switch (k) { case 1 -> seen = 1; default -> seen = 2; } }
      |}
      |""".stripMargin)

  test("an ARROW-form switch STATEMENT carries no `yield` — JLS 14.21 permits none") {
    // Spoon normalises `case 1 -> seen = 1;` into a `CtYieldStatement` wrapping the assignment. It
    // is a parser artifact: carried through, the arm would hold a jump java never wrote and the
    // emitter would look for a boundary that is not there.
    assert(!clue(stmt.out).contains("boundary"), stmt.out)
    assert(clue(stmt.out).contains("seen = 1"), stmt.out)
    assert(clue(stmt.out).contains("seen = 2"), stmt.out)
  }

  // ---------------------------------------------------------------------------------------------
  // THE SCALAC PROBE — what the emitted shapes MEAN, compiled and run
  //
  // Everything above asserts about emitted TEXT, which is the only thing the fixture path can see.
  // Three of this row's cells are claims about the LANGUAGE the text is written in, and a text
  // assertion cannot settle any of them: that a value-carrying `boundary` with a `$`-suffixed name
  // beginning `yield` is even lexable (scala's `yield` is a reserved word), that `break` at a typed
  // `Label` really produces the arm's value, and that an inexhaustive `match` throws where java's
  // exhaustiveness would have. The functions below are hand-written scala in the emitter's own
  // shape; scalac compiles them as part of this suite and the assertions run them.
  // ---------------------------------------------------------------------------------------------

  /** the emitter's rendering of `switch (k) { default -> { if (k > 0) { yield 1; } yield 2; } }`. */
  private def nonTailShape(k: Int): Int =
    k match
      case _ => scala.util.boundary { (yield$1: scala.util.boundary.Label[scala.Int]) ?=>
        { if k > 0 then { scala.util.boundary.break(1)(using yield$1) }; 2 }
      }

  test("PROBE: a value-carrying `boundary` yields the ARM's value, from arbitrary depth") {
    assertEquals(nonTailShape(5), 1)
    assertEquals(nonTailShape(-5), 2)
  }

  /** the emitter's rendering of an EXHAUSTIVE java switch expression — no fall-out arm. */
  private def exhaustiveShape(s: String): Int =
    s match
      case "a" => 1
      case "b" => 2

  test("PROBE: an inexhaustive `match` throws where java's own exhaustiveness would have") {
    // JLS 15.28.1 makes a switch expression exhaustive, and where separate compilation defeats that
    // guarantee java throws `MatchException`. Scala throws `MatchError` at the same place for the
    // same reason. Both throw; the class differs and nothing else does, which is exactly what the
    // catalog row records as the one cell where the two languages are not identical.
    assertEquals(exhaustiveShape("a"), 1)
    intercept[MatchError](exhaustiveShape("z"))
  }

  /** the emitter's rendering of a `yield` that leaves the outer expression from inside a nested
    * switch STATEMENT — copied from the emitted text, not paraphrased. */
  private def throughStmtShape(a: Int, b: Int): Int =
    a match
      case 1 => scala.util.boundary { (yield$1: scala.util.boundary.Label[scala.Int]) ?=>
        b match
          case 2 => scala.util.boundary.break(10)(using yield$1)
          case _ => ()
        20
      }
      case _ => 0

  test("PROBE: a break at the OUTER label really leaves the inner match — javac's own answers") {
    // Measured against javac 22.0.2 on the same java: 10, 20, 0. `boundary.break` is an exception
    // throw, so it passes straight through the enclosing `match` with nothing to catch it — which
    // is what makes ONE boundary, at the outer arm, the whole translation.
    assertEquals(throughStmtShape(1, 2), 10)
    assertEquals(throughStmtShape(1, 3), 20)
    assertEquals(throughStmtShape(5, 2), 0)
  }

  test("PROBE: a case GUARD is evaluated after the pattern matches, and falls through on false") {
    // The order matters for `case X x when cond ->` (JLS 14.11.1) and it is the same order in both
    // languages: pattern first, guard second, next label on a false guard.
    val order = collection.mutable.ListBuffer.empty[String]
    def f(i: Int): String = i match
      case n if { order += s"g$n"; n > 3 } => "big"
      case _                               => "small"
    assertEquals(f(1), "small")
    assertEquals(f(9), "big")
    assertEquals(order.toList, List("g1", "g9"))
  }

  test("…and a STATEMENT switch with no default still gets the fall-out arm java has") {
    val noDefault = port(
      """package p;
        |class St2 {
        |  int seen = 0;
        |  void f(int k) { switch (k) { case 1 -> seen = 1; } }
        |}
        |""".stripMargin)
    // JS-S05 is unchanged for the classic form: java FALLS OUT of a switch statement that matches
    // nothing, and scala's `match` throws `MatchError` without the arm.
    assert(clue(noDefault.out).contains("case _ =>"), noDefault.out)
  }
