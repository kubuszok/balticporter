package balticporter.corpus

import balticporter.emit.TirEmitter
import balticporter.frontend.spoon.SpoonTir
import balticporter.tir.{OmissionCheck, Pipeline}

/** `OmissionCheck.droppedSuperArgs` must shadow the EMITTER's decision, one constructor at a time.
  *
  * Scala lets only the primary constructor reach `super`, so `CtorFunnel` nominates one Java
  * constructor and every other one delegates. Whether a given delegation actually CARRIES that
  * constructor's super arguments is a computation that can decline — an argument whose type fits no
  * parameter of the promoted primary has nowhere to go, and the emitter falls back to a bare
  * `this()`, losing it.
  *
  * The regression this pins: the funnel briefly asserted a CLASS-WIDE `Plan.superExpressed` flag,
  * and the check skipped every constructor of a class carrying it. A class whose promotion expressed
  * one root and dropped another therefore reported ZERO dropped super arguments — the check hiding
  * exactly the drop class it exists to count, on the shape that motivated the promotion in the first
  * place. Both halves are asserted here: the dropped root IS reported, and the expressed roots are
  * NOT, so a fix in either direction alone fails.
  *
  * Nothing about this is visible to a compile — both emissions type-check; only the parent's state
  * differs at runtime (CLAUDE.md §3, §4.4).
  */
class CtorFunnelSuperArgsSpec extends munit.FunSuite:

  private val src =
    """package demo;
      |class Parent {
      |  Object a; int b;
      |  Parent(Object a, int b) { this.a = a; this.b = b; }
      |}
      |/** one root's parameters ARE the parent's (promoted); the other's arguments fit no slot. */
      |class Mixed extends Parent {
      |  Mixed(Object a, int b) { super(a, b); }
      |  Mixed(String s)        { super(s, 7); }
      |}
      |class Base {
      |  int n; boolean flag;
      |  Base(int n, boolean flag) { this.n = n; this.flag = flag; }
      |}
      |/** a SYNTHESISED primary: every root reaches the same parent constructor, none can be the
      |  * primary, and each becomes a secondary computing its own arguments. Nothing is dropped. */
      |class Synth extends Base {
      |  Synth()      { super(0, false); }
      |  Synth(int k) { super(k + 1, true); }
      |}
      |/** the PROMOTED primary itself, passing only SOME of its parameters up. Its super arguments
      |  * are in the `extends` clause and nothing is lost, but the delegation the other roots use
      |  * cannot rebuild them (three parameters, two arguments) — so a check that asks the delegation
      |  * about the PRIMARY gets the wrong answer, and must not ask it. */
      |class Sized extends Store {
      |  Sized(String label, int cap, int max) { super(cap, max); }
      |  Sized(String label)                   { this(label, 16, 99); }
      |}
      |class Store {
      |  int cap; int max;
      |  Store(int cap, int max) { this.cap = cap; this.max = max; }
      |}
      |""".stripMargin

  private val program = Pipeline.run(SpoonTir.fromSource(src), Nil)
  private val out     = new TirEmitter(program).emit
  private val dropped = OmissionCheck.droppedSuperArgs(program)

  // the promoted primary's parameters are renamed by `funnelParamRenames` (they become class
  // members), so match the SHAPE — both parameters passed straight through — not the mangled names.
  private val promoted =
    raw"""class Mixed\(([\w$$]+): java\.lang\.Object, ([\w$$]+): scala\.Int\) extends demo\.Parent\(\1, \2\)""".r
  private val lost = raw"""def this\(s: java\.lang\.String\) = \{\s*this\(\)\s*\}""".r
  // `Synth()`'s own `super(0, false)` reaching the SYNTHESISED primary positionally
  private val nilarySecondary = raw"""def this\(\) = \{\s*this\(0, false\)\s*\}""".r

  test("the promoted root's super arguments reach the parent, the other root's are lost") {
    // the pass-through root became the primary: its arguments are in the `extends` clause
    assert(promoted.findFirstIn(clue(out)).isDefined)
    // and the root whose arguments fit no parameter of it is emitted as a bare `this()` — the
    // emitter's own decision, which is what the next test requires the check to have counted
    assert(lost.findFirstIn(out).isDefined)
  }

  test("exactly the dropped root is reported — not the whole class, not none of it") {
    assertEquals(dropped.map(f => (f.owner, f.detail)),
                 List(("demo.Mixed", "2 argument(s) discarded")))
  }

  test("the PROMOTED primary is never reported — its arguments are in the `extends` clause") {
    // it passes 2 of its 3 parameters up, so the delegation used by the OTHER roots cannot rebuild
    // this call. Asking it anyway reported the primary of 24 libGDX classes as having lost the
    // arguments standing right there in the emitted `extends` clause.
    assert(clue(out).contains("extends demo.Store("))
    assertEquals(dropped.filter(_.owner == "demo.Sized"), Nil)
  }

  test("a synthesised primary expresses every root, and none of them is reported") {
    assert(clue(out).contains("sup$0"))
    assert(out.contains("extends demo.Base(sup$0, sup$1)"))
    assertEquals(dropped.filter(_.owner == "demo.Synth"), Nil)
  }

  test("the NILARY root of a synthesised primary survives as a secondary constructor") {
    // A synthesised primary IS paramful, but `Plan.primaryParams` is empty for it — no java
    // constructor backs it. Reading only that, the emitter judged `Synth()` degenerate (Scala's
    // implicit primary is already no-arg) and dropped it, leaving a class whose ONLY constructors
    // take arguments: `new Synth()` a compile error at every call site, while `Plans.superCall`
    // reported that same root Positional — the exact check/emitter disagreement the per-root
    // `superCall` refactor exists to make impossible.
    assert(nilarySecondary.findFirstIn(clue(out)).isDefined)
  }
