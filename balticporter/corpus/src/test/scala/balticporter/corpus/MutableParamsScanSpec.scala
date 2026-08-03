package balticporter.corpus

import balticporter.emit.TirEmitter
import balticporter.frontend.spoon.SpoonTir
import balticporter.tir.Pipeline
import balticporter.transform.MutableParamsTransform

/** `MutableParamsTransform` finds a reassigned parameter by SCANNING the method body, and the scan
  * used to be a hand-rolled recursion over a hand-maintained list of node kinds — the thing
  * CLAUDE.md §3 bans, and the shape of two of the four silent defects this project has found. Each
  * method below is a Java form that list did not reach.
  *
  * These do not degrade silently: a parameter left a `val` makes the emitted reassignment fail to
  * compile, so the port's error count says so. That is why this is HARDENING rather than a bug
  * fix. But "loud" only holds while somebody is reading the count, and the list would have gone
  * stale again at the next `Tree` case added — so what is pinned here is that the scan now reaches
  * whatever the map traversal reaches, by construction.
  */
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
