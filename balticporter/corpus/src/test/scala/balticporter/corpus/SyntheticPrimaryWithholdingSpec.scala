package balticporter.corpus

import balticporter.emit.TirEmitter
import balticporter.frontend.spoon.SpoonTir
import balticporter.tir.Pipeline

/** THE WHOLE-PROGRAM GUARDS AROUND THE SYNTHESIS — the two that were measured and then pinned by
  * nothing but a lane. */
class SyntheticPrimaryWithholdingSpec extends munit.FunSuite:

  // ---- C1: the withholding fixpoint, on the shape that named it ----

  private val withheldSrc =
    """package with1;
      |class Base { Base(int a, int b) {} }
      |/** TWO PARAMFUL ROOTS reaching one parent constructor, NEITHER a pass-through (so there is
      |  * nothing to collapse onto) and NO NILARY java constructor: the synthesis fires, and the
      |  * primary it would emit is the only constructor the class has. */
      |class Mid extends Base {
      |  Mid(int a)     { super(a, 1); }
      |  Mid(boolean f) { super(0, 2); }
      |}
      |/** This subclass's roots reach two DIFFERENT `Mid` constructors. Because `Mid` has a
      |  * synthesised plan with `rootArgs`, the child resolves through that plan and also
      |  * synthesises — no withholding cascade. // ENGINE-LIMITS C3 item 4c */
      |class Sub extends Mid {
      |  Sub(int a)     { super(a); }
      |  Sub(boolean f) { super(f); }
      |}
      |""".stripMargin

  private val withheld = new TirEmitter(Pipeline.run(SpoonTir.fromSource(withheldSrc), Nil)).emit

  test("C1 — child resolves through parent plan: no withholding cascade") {
    // C3 item 4c: Sub resolves through Mid's synthesised plan, so Mid keeps its synthesis
    assert(clue(withheld).contains(
      "class Mid protected (sup$0: scala.Int, sup$1: scala.Int) extends with1.Base(sup$0, sup$1)"))
    assert(clue(withheld).contains(
      "class Sub protected (sup$0: scala.Int, sup$1: scala.Int) extends with1.Mid(sup$0, sup$1)"))
  }

  test("C1 — a child with ONLY a nilary ctor still withholds the parent's synthesis") {
    // A child whose only constructor is nilary (implicit `super()`) cannot synthesise and its
    // plan has empty superArgs. The fixpoint sees `superArgs.isEmpty && !isSynthesised` and
    // correctly withholds the parent. // ENGINE-LIMITS C1, C3
    val src =
      """package with1b;
        |class Base { Base(int a, int b) {} }
        |class Mid extends Base {
        |  Mid(int a)     { super(a, 1); }
        |  Mid(boolean f) { super(0, 2); }
        |}
        |class Sub extends Mid {
        |  Sub() {}
        |}
        |""".stripMargin
    val o = new TirEmitter(Pipeline.run(SpoonTir.fromSource(src), Nil)).emit
    assert(clue(o).contains("extends with1b.Mid"),
      "nilary-only child has bare extends (no super args)")
    assert(!o.contains("extends with1b.Mid(sup$0"),
      "no paramful extends of a withheld parent")
    assert(!o.contains("class Mid protected (sup$0"),
      "parent synthesis withheld by the nilary child")
  }

  test("C1 — withholding is NOT a whole-program ban: a synthesis nothing reaches bare survives") {
    // the other direction, and it is the one that makes the guard worth having rather than a
    // blanket refusal. Same `Mid`, with the subclass passing arguments up.
    val ok =
      """package with2;
        |class Base { Base(int a, int b) {} }
        |class Mid extends Base {
        |  Mid(int a)     { super(a, 1); }
        |  Mid(boolean f) { super(0, 2); }
        |}
        |class Sub extends Mid {
        |  Sub(int a) { super(a); }
        |}
        |""".stripMargin
    val o = new TirEmitter(Pipeline.run(SpoonTir.fromSource(ok), Nil)).emit
    assert(clue(o).contains("class Mid protected (sup$0: scala.Int, sup$1: scala.Int) extends with2.Base(sup$0, sup$1)"))
  }

  // ---- C1.5: `nilaryPlan` and the two readings of "is this a synthesis" ----

  test("C1.5 — `nilaryPlan` must NOT claim a SYNTHESIS: `primary.isEmpty` is true of both") {
    // No constructor of this class carries `super(args)`, which is `nilaryPlan`'s entire domain —
    // and it is also exactly where the synthesis fires on the IMPLICIT `super()`. Read as
    // "nothing was nominated", the empty `primary` of a synthesised plan handed every one of these
    // classes back to the promotion, whose body then ran on every construction path (libGDX's
    // `CharArray`: 9 escaping paths the synthesis had already removed).
    val src =
      """package nil1;
        |class Buf {
        |  int cap; int size;
        |  Buf()                     { this(16, 0); }
        |  Buf(int cap, int extra)   { this.cap = cap + extra; }
        |  Buf(int cap, boolean big) { this.cap = cap; }
        |}
        |""".stripMargin
    val o = new TirEmitter(Pipeline.run(SpoonTir.fromSource(src), Nil)).emit
    assert(clue(o).contains("class Buf protected (f$cap: scala.Int)"))
    // and the nilary constructor is still there to be called, as a secondary
    assert(o.contains("def this() = {"))
  }

  test("C1.5 — a MARKER-ONLY synthesis has an EMPTY slot list and still emits a valid primary") {
    // No super slots (the parent is `Object`), no hoistable field, and a real nilary constructor
    // whose erased parameter list EQUALS the empty slot list — so the class needs the marker and
    // nothing else. `synthetic.nonEmpty` is false here, and a predicate reading it emitted
    // `class Two protected ()` while every secondary wrote `this((null: Two.Funnel))` — against
    // scala's implicit nilary primary, which is not that constructor.
    val src =
      """package mark1;
        |class Two {
        |  int n;
        |  void go() { n++; }
        |  Two()      { }
        |  Two(int k) { go(); }
        |}
        |""".stripMargin
    val o = new TirEmitter(Pipeline.run(SpoonTir.fromSource(src), Nil)).emit
    assert(clue(o).contains("class Two protected (ctor$: Two.Funnel)"))
    assert(o.contains("def this() = {"))
    assert(o.contains("this((null: Two.Funnel))"))
    assert(o.contains("protected final class Funnel"))
  }
