package balticporter.corpus

import balticporter.emit.TirEmitter
import balticporter.frontend.spoon.SpoonTir
import balticporter.tir.Pipeline

/** A PROMOTED constructor LOCAL keeps java's POSITION — `ENGINE-LIMITS.md` C12.
  *
  * `CtorFunnel` promotes one java constructor to scala's primary and splices its body into the
  * class body, where the constructor's own top-level locals become `val` MEMBERS (C2, and
  * `CLAUDE.md` §4.55 for the rename that goes with it). Both of those are about the NAME. This
  * spec is about WHERE the initialiser runs, which is the half that was wrong:
  * `TirEmitter.orderBody` hoisted every `ValDef` ahead of every statement, so a promoted local
  * initialised itself BEFORE the constructor statements java ran first.
  *
  * JLS 12.5 is the whole of the rule, and it cuts between the two:
  *
  *   - a FIELD initialiser (and an instance initialiser) runs in step 4, in textual order, before
  *     any constructor body statement — so hoisting a field above the promoted body reproduces
  *     java whatever order the java file declared them in;
  *   - a constructor's LOCAL DECLARATION is a step-5 constructor BODY statement, and its position
  *     among the other body statements is what carries every dependency between them.
  *
  * A scala class body IS its constructor and runs in textual order, so the emitted ORDER is the
  * semantics — which is what these tests assert. The behaviour itself was measured on liqp, where
  * `Template`'s three promoted locals read `this.templateParser` one statement before java assigned
  * it: 409 of 414 test failures, `NullPointerException`, and **0 scalac errors with every check
  * count flat**. Nothing but a run could see it (`CLAUDE.md` §3).
  *
  * The distinction is NOT visible in the node kind — both are `Tree.ValDef` — and is read off
  * OWNERSHIP, which is the structural fact `CLAUDE.md` §4.56 demands: the frontend interns a field
  * under the CLASS and a local under the enclosing EXECUTABLE (`SpoonTir.defineLocal`), so
  * "is this ValDef a member of the class whose body I am ordering?" is a symbol lookup and not a
  * guess about names, lines or provenance.
  */
class CtorFunnelPromotedLocalOrderSpec extends munit.FunSuite:

  private val src =
    """package demo;
      |public class Holder { public int v; public Holder(int v) { this.v = v; } }
      |/** THE C12 SHAPE. `probe` is a constructor LOCAL whose initialiser reads `this.h`, which the
      |  * statement above it assigns. Hoisted, it dereferences a null field. */
      |public class Ordered {
      |  private Holder h;
      |  private int seen;
      |  private int later;
      |  Ordered(Holder holder) {
      |    this.h = holder;
      |    int probe = this.h.v;
      |    this.seen = probe;
      |    this.later = probe + 1;
      |  }
      |}
      |/** THE NEGATIVE — a promoted local with no ordering dependency at all. Its position must be
      |  * java's all the same: "moved only where it mattered" is not a rule anything can check. */
      |public class Independent {
      |  private int a;
      |  private int b;
      |  Independent(int x) {
      |    this.a = x;
      |    int doubled = x * 2;
      |    this.b = doubled;
      |  }
      |}
      |/** THE OTHER NEGATIVE — real FIELDS, declared BELOW the constructor that reads them, which is
      |  * the case the hoist exists for. Java runs every field initialiser before the constructor
      |  * body (JLS 12.5), so `count` must still be emitted above the statement that reads it. */
      |public class Fields {
      |  Fields() {
      |    this.total = this.count + 1;
      |  }
      |  private int count = 7;
      |  private int total;
      |}
      |""".stripMargin

  private val program = Pipeline.run(SpoonTir.fromSource(src), Nil)
  private val out     = new TirEmitter(program).emit

  /** the emitted line index of the first line containing `s` — `-1` when absent, so a missing
    * line fails the ordering assertion rather than passing it by accident. */
  private def at(s: String): Int = out.linesIterator.indexWhere(_.contains(s))

  // the `$p` suffixes below are §4.55's promotion rename, asserted deliberately: C12's fix moves
  // the PLACEMENT only, and a rename that stopped firing would be a second change measured as one.
  test("a promoted local's initialiser runs where java's declaration stood, not at the head") {
    val assigned = at("this.h = holder$p")
    val local    = at("probe$p: scala.Int = this.h.v")
    assert(assigned >= 0, clue(out))
    assert(local >= 0, clue(out))
    assert(assigned < local, clue((assigned, local, out)))
  }

  test("the statements AFTER the promoted local still follow it") {
    val local = at("probe$p: scala.Int = this.h.v")
    val seen  = at("this.seen = probe$p")
    val later = at("this.later = probe$p")
    assert(local >= 0 && seen >= 0 && later >= 0, clue(out))
    assert(local < seen && seen < later, clue((local, seen, later, out)))
  }

  test("a promoted local with no dependency keeps java's position too") {
    val a       = at("this.a = x$p")
    val doubled = at("doubled$p: scala.Int = x$p")
    val b       = at("this.b = doubled$p")
    assert(a >= 0 && doubled >= 0 && b >= 0, clue(out))
    assert(a < doubled && doubled < b, clue((a, doubled, b, out)))
  }

  test("a real FIELD declared below the constructor keeps the hoist — JLS 12.5 step 4") {
    val count = at("count: scala.Int = 7")
    val stmt  = at("this.total = this.count + 1")
    assert(count >= 0 && stmt >= 0, clue(out))
    assert(count < stmt, clue((count, stmt, out)))
  }
