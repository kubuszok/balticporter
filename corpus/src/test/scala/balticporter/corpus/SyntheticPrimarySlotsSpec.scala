package balticporter.corpus

import balticporter.emit.TirEmitter
import balticporter.frontend.spoon.SpoonTir
import balticporter.tir.{OmissionCheck, Pipeline}

/** The SLOTS of a synthesised primary — where each parameter's TYPE comes from, and which classes
  * may have one at all.
  *
  * The synthesis used to demand that one of the roots be NILARY, on the reasoning that a paramful
  * root could otherwise be promoted instead. It cannot, and the cost was silent: with several
  * paramful roots and no nilary one, `plan0` nominated NOTHING (`several.find(_.paramss.isEmpty)` is
  * `None`), the class came out `not-funnelled`, and EVERY root's `super(args)` was lowered to a bare
  * `this()` — the parent constructed with the wrong arguments, compiling perfectly. The condition
  * that actually makes the encoding work is that every root reaches ONE parent constructor, and it
  * has nothing to do with a nilary root; `ENGINE-LIMITS.md` C7's claim to the contrary is corrected.
  *
  * Both halves are here, because the positive alone would pass on a synthesis that fired everywhere:
  * roots reaching DIFFERENT parent constructors are the WALL, must NOT synthesise, and must stay
  * counted by `OmissionCheck` — one `extends` clause cannot make two different parent constructor
  * calls, and padding one to reach the other measured 0 -> 55 errors outside the JDK family.
  */
class SyntheticPrimarySlotsSpec extends munit.FunSuite:

  private val src =
    """package demo;
      |class Widened {
      |  Object a; int b;
      |  Widened(Object a, int b) { this.a = a; this.b = b; }
      |}
      |/** TWO PARAMFUL ROOTS, no nilary one, both reaching `Widened(Object,int)`. The slot types are
      |  * the PARENT's formals (`Object`), never the argument types at the call (`String`, `Integer`)
      |  * — an argument is an expression whose type may be narrower than the formal, and a primary
      |  * built from one call's arguments cannot take the other's. */
      |class Narrowing extends Widened {
      |  Narrowing(String s)  { super(s, s.length()); }
      |  Narrowing(Integer i) { super(i, 1); }
      |}
      |class Overloaded {
      |  int n;
      |  Overloaded(int n) { this.n = n; }
      |  Overloaded(int n, boolean b) { this.n = b ? n : -n; }
      |}
      |/** THE WALL: two roots reaching two DIFFERENT parent constructors. One `extends` clause cannot
      |  * make both calls, so nothing is synthesised and the loss stays counted. */
      |class Wall extends Overloaded {
      |  Wall(int n)              { super(n); }
      |  Wall(int n, boolean b)   { super(n, b); }
      |}
      |""".stripMargin

  private val program = Pipeline.run(SpoonTir.fromSource(src), Nil)
  private val out     = new TirEmitter(program).emit
  private val dropped = OmissionCheck.droppedSuperArgs(program)

  test("several PARAMFUL roots reaching ONE parent constructor synthesise — no nilary root needed") {
    assert(clue(out).contains(
      "class Narrowing protected (sup$0: java.lang.Object, sup$1: scala.Int) extends demo.Widened(sup$0, sup$1)"))
  }

  test("the slot types are the PARENT's FORMALS, not the argument types at the call site") {
    // `super(s, …)` passes a `String` and `super(i, 1)` an `Integer`; the slot is `Object`, which is
    // what `Widened` declares. Built from either call's argument types the primary could not take
    // the other's argument at all.
    assert(!clue(out).contains("class Narrowing protected (sup$0: java.lang.String"))
    assert(!out.contains("class Narrowing protected (sup$0: java.lang.Integer"))
  }

  test("every root's super arguments survive, positionally, and none is reported") {
    assert(clue(out).contains("def this(s: java.lang.String) = {"))
    assert(out.contains("this(s, s.length())"))
    assert(out.contains("this(i, 1)"))
    assertEquals(dropped.filter(_.owner == "demo.Narrowing"), Nil)
  }

  test("NEGATIVE — roots reaching DIFFERENT parent constructors do not synthesise") {
    assert(!clue(out).contains("class Wall protected ("))
  }

  test("NEGATIVE — and the wall invents no argument: no `extends` clause carries one") {
    // The fallback for a wall is the one the funnel already had — `this()` plus the parent
    // constructor's own statements REPLAYED where that is provably equivalent, or the arguments
    // counted where it is not. What must NOT happen is a single `extends Overloaded(…)` built by
    // padding one call to reach the other's overload: that is the guess measured at 0 -> 55 errors
    // outside the JDK-throwable family, and it compiles.
    assert(clue(out).contains("class Wall extends demo.Overloaded {"))
    assert(!out.contains("extends demo.Overloaded("))
    // every root here IS accounted for — by replay, not by synthesis — so nothing is reported, and
    // that silence is the correct answer rather than a missing one
    assertEquals(dropped.filter(_.owner == "demo.Wall"), Nil)
  }

  test("a COMMENT above a consumed `super(args)` rides the delegation that replaces it") {
    // §4.58: the call is consumed into a `this(...)`, and what somebody wrote ABOUT it is not. The
    // funnel is the one place a statement disappears without a diff showing where it went, so the
    // carriage is pinned rather than left to whichever harvest runs last.
    val commented =
      """package demo3;
        |class P { String label; P(String label) { this.label = label; } }
        |class Q extends P {
        |  Q(int k) {
        |    // the base wants a name, not an index
        |    super("n" + k);
        |  }
        |  Q(boolean b) { super(b ? "t" : "f"); }
        |}
        |""".stripMargin
    val p2  = Pipeline.run(SpoonTir.fromSource(commented), Nil)
    val o2  = new TirEmitter(p2).emit
    assert(clue(o2).contains("class Q protected (sup$0: java.lang.String) extends demo3.P(sup$0)"))
    assert(o2.contains("// the base wants a name, not an index"))
    // …and it sits above the delegation, not orphaned at the end of the constructor
    val idx = o2.indexOf("// the base wants a name, not an index")
    assert(o2.indexOf("this(", idx) > idx)
  }
