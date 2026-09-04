package balticporter.corpus

import balticporter.emit.TirEmitter
import balticporter.frontend.spoon.SpoonTir
import balticporter.tir.Pipeline

/** A PROMOTED constructor LOCAL keeps java's POSITION — `ENGINE-LIMITS.md` C12. */
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
      |/** THE OTHER HALF OF STEP 4 — an instance INITIALISER BLOCK. JLS 12.5 step 4 runs field
      |  * initialisers and instance initialisers as ONE sequence, in TEXTUAL ORDER, so a block
      |  * written above a field runs first and the field's initialiser then overwrites it. The
      |  * counterexample is exact: java leaves `b == 5`. */
      |public class Interleaved {
      |  { this.b = 2; }
      |  int b = 5;
      |}
      |/** …and the same sequence read the other way: a block written BELOW a field sees the field's
      |  * value and wins. Java leaves `d == 11`. Together the two pin the ORDER rather than a rule
      |  * that happens to put blocks on one side. */
      |public class InterleavedAfter {
      |  int c = 10;
      |  int d;
      |  { this.d = this.c + 1; }
      |}
      |/** A STATIC initialiser is the same fact in the companion — JLS 12.4.2 step 9 runs static
      |  * field initialisers and `static { }` blocks in textual order too. */
      |public class StaticInterleaved {
      |  static { S = 2; }
      |  static int S = 5;
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

  // -----------------------------------------------------------------------------------------
  // …AND STEP 4 HAS TWO KINDS OF MEMBER IN IT. C12's own doc said the hoist "reproduces java
  // WHATEVER order the java file declared them in", which is true of fields ALONE and false the
  // moment an instance initialiser block is in the same class: JLS 12.5 step 4 runs field
  // initialisers and instance initialisers as ONE sequence, in TEXTUAL ORDER.

  test("an instance INITIALISER BLOCK keeps its textual position among the fields") {
    // java: the block runs first, `b = 5` overwrites it, `b == 5`.
    val block = at("this.b = 2")
    val field = at("b: scala.Int = 5")
    assert(block >= 0 && field >= 0, clue(out))
    assert(block < field, clue((block, field, out)))
  }

  test("…and a block written BELOW a field still runs after it") {
    // java: `c = 10` runs, then the block, so `d == 11`. Read the other way round, this is what
    // says the rule is java's ORDER and not "blocks first".
    val field = at("c: scala.Int = 10")
    val block = at("this.d = this.c + 1")
    assert(field >= 0 && block >= 0, clue(out))
    assert(field < block, clue((field, block, out)))
  }

  test("a STATIC initialiser interleaves with static fields the same way — JLS 12.4.2 step 9") {
    val block = at("S = 2")
    val field = at("S: scala.Int = 5")
    assert(block >= 0 && field >= 0, clue(out))
    assert(block < field, clue((block, field, out)))
  }
