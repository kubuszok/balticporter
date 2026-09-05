package balticporter.corpus

import balticporter.emit.TirEmitter
import balticporter.frontend.spoon.SpoonTir
import balticporter.tir.Pipeline
import balticporter.transform.MutableParamsTransform

/** `MutableParamsTransform` finds a reassigned parameter by SCANNING the method body, and the scan
  * used to be a hand-rolled recursion over a hand-maintained list of node kinds — the thing
  * CLAUDE.md §3 bans, and the shape of two of the four silent defects this project has found. Each
  * method below is a Java form that list did not reach. */
class MutableParamsScanSpec extends munit.FunSuite:

  private val src =
    """package demo;
      |class Scan {
      |  // a local variable's INITIALISER. `Block.stats` was filtered to `case x: Term`, and a
      |  // `ValDef` is a Definition — so the whole initialiser was invisible. Ordinary Java.
      |  int inLocalInit(int p) { int c = p++; return c; }
      |
      |  // an ARRAY INITIALISER — `NewArray` had no case at all
      |  int[] inArrayInit(int p) { return new int[] { p++, 7 }; }
      |
      |  // an array DIMENSION — same node, other field
      |  int[] inArrayDim(int p) { return new int[p++]; }
      |
      |  // a VARARG argument, which is a `Repeated` between the `Apply` and its elements
      |  int inVarargs(int p) { return sum(p++, 1); }
      |  int sum(int... xs) { return 0; }
      |
      |  // an `instanceof` scrutinee — no case either
      |  boolean inInstanceOf(Object p) { return (p = "x") instanceof String; }
      |
      |  // a for-loop INITIALISER declaration: `For.init` was filtered to `case x: Term` too
      |  int inForInit(int p) { int t = 0; for (int i = p++; i < 3; i++) t += i; return t; }
      |
      |  // a `try`-with-resources head
      |  void inResource(java.io.Closeable p) throws Exception { try (java.io.Closeable c = (p = null)) { } }
      |
      |  // CONTROL: never written, so it must stay a plain parameter
      |  int untouched(int q) { return q + 1; }
      |}
      |""".stripMargin

  /** A reassigned CONSTRUCTOR parameter, which is the one position where the `var` cannot be read. */
  private val ctorSrc =
    """package demo;
      |class Sup { Sup(int a, int b) { } }
      |class Sub extends Sup {
      |  int seen;
      |  Sub(int off, int n) {
      |    super(off, n);          // reads the SLOT — java has not run the ++ yet
      |    seen = off++;           // …which is what makes `off` a var at all
      |  }
      |}
      |class Plain extends Sup {
      |  // CONTROL: nothing reassigned, so the delegation keeps java's own names
      |  Plain(int off, int n) { super(off, n); }
      |}
      |""".stripMargin

  private val ctorOut =
    new TirEmitter(Pipeline.run(SpoonTir.fromSource(ctorSrc), List(new MutableParamsTransform))).emit

  private val raw     = SpoonTir.fromSource(src)
  private val program = Pipeline.run(raw, List(new MutableParamsTransform))
  private val out     = new TirEmitter(program).emit

  private def shadowed(name: String) = out.contains(s"var $name: ") && out.contains(s"= $name$$arg")

  test("a parameter written inside a local variable's initialiser becomes a var") {
    assert(clue(out).contains("var p: scala.Int = p$arg"))
    assert(out.contains("def inLocalInit(p$arg: scala.Int)"))
  }

  test("a parameter written inside an array initialiser, a dimension or a vararg becomes a var") {
    assert(clue(out).contains("def inArrayInit(p$arg: scala.Int)"))
    assert(out.contains("def inArrayDim(p$arg: scala.Int)"))
    assert(out.contains("def inVarargs(p$arg: scala.Int)"))
  }

  test("a parameter written inside an instanceof scrutinee or a resource head becomes a var") {
    assert(clue(out).contains("def inInstanceOf(p$arg: "))
    assert(out.contains("def inResource(p$arg: "))
  }

  test("a parameter written in a for-loop's initialiser declaration becomes a var") {
    assert(clue(out).contains("def inForInit(p$arg: scala.Int)"))
  }

  test("a parameter that is never written is left ALONE — no spurious shadow") {
    assert(clue(out).contains("def untouched(q: scala.Int)"))
    assert(!out.contains("q$arg"))
    assert(shadowed("p")) // …while the ones that are written all got theirs
  }

  private def superArgsOf(cls: String) =
    ctorOut.linesIterator.filter(l => l.contains(s"class $cls ") && l.contains("extends demo.Sup("))
      .map { l =>
        val a = l.substring(l.indexOf("extends demo.Sup(") + "extends demo.Sup(".length)
        a.substring(0, a.indexOf(')'))
      }.toList

  test("a constructor's delegation reads the PARAMETER SLOT, never the var declared below it") {
    // the var really is there and really is declared BELOW the extends clause…
    assert(clue(ctorOut).contains("var off$p: scala.Int = off$arg$p"))
    // …so the delegation must name the slot. The untouched sibling in the same call does not move.
    assertEquals(clue(superArgsOf("Sub")), List("off$arg$p, n$p"))
  }

  test("a constructor with NOTHING reassigned keeps java's own delegation, un-renamed") {
    assertEquals(clue(superArgsOf("Plain")), List("off$p, n$p"))
    assert(!ctorOut.linesIterator.filter(_.contains("class Plain")).exists(_.contains("$arg")), clue(ctorOut))
  }

  /** JLS 14.20 — java's EXCEPTION parameter is reassignable too (only a multi-catch's is
    * implicitly final); scala's is a pattern binding, i.e. a `val`. */
  private val catchSrc =
    """package demo;
      |class Handler {
      |  Object retry() {
      |    try { return work(); }
      |    catch (Exception ex) {
      |      try { return fallback(); }
      |      catch (RuntimeException second) { ex = second; }
      |      throw new IllegalStateException("failed", ex);
      |    }
      |  }
      |  // CONTROL: never reassigned, so the binding stays java's own name
      |  Object plain() { try { return work(); } catch (Exception e) { return e.getMessage(); } }
      |  Object work() { return null; }
      |  Object fallback() { return null; }
      |}
      |""".stripMargin

  private val catchOut =
    new TirEmitter(Pipeline.run(SpoonTir.fromSource(catchSrc), List(new MutableParamsTransform))).emit

  test("a reassigned catch parameter binds `$arg` and opens its handler with the var") {
    assert(clue(catchOut).contains("case ex$arg: java.lang.Exception =>"))
    assert(catchOut.contains("var ex: java.lang.Exception = ex$arg"))
    assert(catchOut.contains("ex = second"))
  }

  test("a catch parameter that is never reassigned is left ALONE") {
    assert(clue(catchOut).contains("case e: java.lang.Exception =>"))
    assert(!catchOut.contains("e$arg"))
    assert(!catchOut.contains("second$arg"))
  }
