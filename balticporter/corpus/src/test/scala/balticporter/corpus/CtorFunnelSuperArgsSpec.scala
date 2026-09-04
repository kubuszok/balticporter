package balticporter.corpus

import balticporter.emit.TirEmitter
import balticporter.frontend.spoon.SpoonTir
import balticporter.tir.{OmissionCheck, Pipeline}

/** `OmissionCheck.droppedSuperArgs` must shadow the EMITTER's decision, one constructor at a time. */
class CtorFunnelSuperArgsSpec extends munit.FunSuite:

  private val src =
    """package demo;
      |public class Parent {
      |  Object a; int b;
      |  Parent(Object a, int b) { this.a = a; this.b = b; }
      |}
      |/** THE COLLAPSE: one root's parameters ARE the parent's, so it is promoted and nothing is
      |  * synthesised. The other root's arguments fit no slot BY TYPE (`String` is not `Object`),
      |  * and are delegated POSITIONALLY instead — which is what java wrote. */
      |public class Mixed extends Parent {
      |  Mixed(Object a, int b) { super(a, b); }
      |  Mixed(String s)        { super(s, 7); }
      |}
      |public class Anchor {
      |  int cap; String tag;
      |  Anchor()        { this.tag = "t"; }
      |  Anchor(int cap) { this.cap = cap; }
      |}
      |/** A WALL, and the one the REPLAY cannot express either: the two roots reach two DIFFERENT
      |  * parent constructors, so nothing is synthesised and the nilary root is promoted; and
      |  * `Anchor()` assigns a field `Anchor(int)` does not, so replaying `Anchor(int)`'s statements
      |  * after `this()` would NOT leave the state java left. Both refusals are correct, and the
      |  * surviving `super(cap)` really is lost — which is what the check must say, for THAT root
      |  * and not for the class. */
      |public class Holder extends Anchor {
      |  Holder()         { }
      |  Holder(int cap)  { super(cap); }
      |}
      |public class Base {
      |  int n; boolean flag;
      |  Base(int n, boolean flag) { this.n = n; this.flag = flag; }
      |}
      |/** a SYNTHESISED primary: every root reaches the same parent constructor, none can be the
      |  * primary, and each becomes a secondary computing its own arguments. Nothing is dropped. */
      |public class Synth extends Base {
      |  Synth()      { super(0, false); }
      |  Synth(int k) { super(k + 1, true); }
      |}
      |/** the PROMOTED primary itself, passing only SOME of its parameters up. Its super arguments
      |  * are in the `extends` clause and nothing is lost, but the delegation the other roots use
      |  * cannot rebuild them (three parameters, two arguments) — so a check that asks the delegation
      |  * about the PRIMARY gets the wrong answer, and must not ask it. */
      |public class Sized extends Store {
      |  Sized(String label, int cap, int max) { super(cap, max); }
      |  Sized(String label)                   { this(label, 16, 99); }
      |}
      |public class Store {
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
    raw"""class Mixed private\[demo\] \(([\w$$]+): java\.lang\.Object, ([\w$$]+): scala\.Int\) extends demo\.Parent\(\1, \2\)""".r
  private val delegated = raw"""def this\(s: java\.lang\.String\) = \{\s*this\(s, 7\)\s*\}""".r
  private val lost      = raw"""def this\(cap: scala\.Int\) = \{\s*this\(\)\s*\}""".r
  // `Synth()`'s own `super(0, false)` reaching the SYNTHESISED primary positionally
  private val nilarySecondary = raw"""def this\(\) = \{\s*this\(0, false\)\s*\}""".r

  test("a COLLAPSED primary is delegated to POSITIONALLY — the sibling's arguments are not lost") {
    // the pass-through root became the primary: its arguments are in the `extends` clause
    assert(promoted.findFirstIn(clue(out)).isDefined)
    // …and the sibling reaches it with its own arguments, in the parent constructor's order.
    assert(delegated.findFirstIn(clue(out)).isDefined)
    assertEquals(dropped.filter(_.owner == "demo.Mixed"), Nil)
  }

  test("exactly the dropped root is reported — not the whole class, not none of it") {
    // `Holder` is the case where the loss is REAL: a wall the replay cannot express either. Its
    // nilary root carries nothing to lose and must NOT be reported; its `super(cap)` must be.
    assert(lost.findFirstIn(clue(out)).isDefined)
    assertEquals(dropped.map(f => (f.owner, f.detail)),
                 List(("demo.Holder", "1 argument(s) discarded")))
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
    // take arguments: `new Synth()` a compile error at every call site, while `Plans.
    assert(nilarySecondary.findFirstIn(clue(out)).isDefined)
  }
