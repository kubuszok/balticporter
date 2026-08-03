package balticporter.corpus

import balticporter.emit.TirEmitter
import balticporter.frontend.spoon.SpoonTir
import balticporter.tir.Pipeline

/** A SYNTHESISED primary is `protected` — and the fact that decides it was written down BACKWARDS
  * in this engine's own source for as long as the synthesis existed.
  *
  * `TirEmitter` asserted, in a comment, that *"scala's `extends C(args)` can only ever invoke C's
  * PRIMARY, so hiding it would make the class unextendable by exactly the subclasses that motivated
  * it"*. That is false, and it was the only argument keeping a constructor JAVA NEVER DECLARED in
  * the port's published API. Compiled and run against scalac 3.8.4:
  *
  * {{{
  * package p
  * class C private (val n: Int, val b: Boolean):
  *   def this()          = this(0, false)
  *   def this(s: String) = this(s.length, true)
  * package q:
  *   class D extends p.C("hello")   // n=5  — a SECONDARY, from another package
  *   class E extends p.C()          // n=0
  *   class F(k: Int) extends p.C(k.toString)
  * package r:
  *   class G protected (val n: Int, val b: Boolean)
  * package s:
  *   class H(k: Int) extends r.G(k, true)   // n=7 — the PROTECTED PRIMARY, another package
  *   new r.G(3, false) {}                   // and an anonymous subclass reaches it too
  * }}}
  *
  * And the negative that decides `protected` over `private`, which is the half usually missing —
  * `private` is CLASS-private in Scala, not package-private, so a SAME-package subclass cannot
  * reach it either:
  *
  * {{{
  * package g
  * class A private (val n: Int, val b: Boolean) { def this() = this(0, false) }
  * class B extends A(1, true)
  * // -- Error: too many arguments for constructor A in class A: (): g.A
  * }}}
  *
  * Choosing `private` where a class is provably leaf would mean asking *"is this class extended?"*,
  * which is a WHOLE-PROGRAM question asked at emission — the shape `ENGINE-LIMITS.md` D4 measures as
  * drift between a base module's run and a dependent's. `protected` needs no such question. What
  * this spec can pin without a compiler is the emitter's half: the synthesised primary renders
  * `protected`, bare (never `protected[pkg]`, which would deny the cross-module subclassing the
  * choice exists to permit — `DESIGN.md` §8.11), and a PROMOTED java constructor does not gain it.
  */
class SyntheticPrimaryVisibilitySpec extends munit.FunSuite:

  private val src =
    """package demo;
      |class Base {
      |  int n; boolean flag;
      |  Base(int n, boolean flag) { this.n = n; this.flag = flag; }
      |}
      |/** a SYNTHESISED primary: every root reaches the same parent constructor and neither java
      |  * constructor can be scala's primary. */
      |class Synth extends Base {
      |  Synth()      { super(0, false); }
      |  Synth(int k) { super(k + 1, true); }
      |}
      |class Store {
      |  int cap; int max;
      |  Store(int cap, int max) { this.cap = cap; this.max = max; }
      |}
      |/** a PROMOTED java constructor — a real declaration, whose visibility is java's. */
      |class Sized extends Store {
      |  Sized(String label, int cap, int max) { super(cap, max); }
      |  Sized(String label)                   { this(label, 16, 99); }
      |}
      |""".stripMargin

  private val program = Pipeline.run(SpoonTir.fromSource(src), Nil)
  private val out     = new TirEmitter(program).emit

  private val syntheticPrimary =
    raw"""class Synth protected \(sup\$$0: scala\.Int, sup\$$1: scala\.Boolean\) extends demo\.Base\(sup\$$0, sup\$$1\)""".r

  test("the synthesised primary is `protected` — a constructor java never declared is not published") {
    assert(syntheticPrimary.findFirstIn(clue(out)).isDefined)
  }

  test("bare `protected`, never package-qualified — a qualifier would deny a dependent's subclass") {
    // §8.11: the synthetic primary is NOT a java declaration, so the `protected[<pkg>]` mapping that
    // governs java-declared members does not reach it. Its only legitimate callers are this class's
    // own secondaries and a subclass's `extends` clause IN ANY PACKAGE — which is exactly the pair
    // bare `protected` permits and a package qualifier denies across a module boundary.
    assert(!clue(out).contains("class Synth protected["))
  }

  test("it is not `private` — that is class-private, so even a same-package subclass loses it") {
    assert(!clue(out).contains("class Synth private"))
  }

  test("a PROMOTED java constructor does not gain the modifier — only the synthesised one has it") {
    // `Sized(String, int, int)` is a real java declaration promoted to the primary; its visibility
    // is whatever java gave it, and the funnel has no standing to narrow a member java published.
    assert(clue(out).contains("extends demo.Store("))
    assert(!out.contains("class Sized protected"))
  }
