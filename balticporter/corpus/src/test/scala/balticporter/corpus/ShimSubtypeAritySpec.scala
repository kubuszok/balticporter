package balticporter.corpus

import balticporter.emit.TirEmitter
import balticporter.frontend.spoon.SpoonTir
import balticporter.testkit.PortSuite
import balticporter.tir.Pipeline
import balticporter.transform.CollectionsTransform

/** The scala-shaped call rewrites are refused on a SHIM receiver — and a library's OWN SUBTYPE of a
  * shim is a shim receiver (`CLAUDE.md` §4.5, §4.56). */
class ShimSubtypeAritySpec extends PortSuite:

  private val src =
    """package shimsub;
      |import java.util.Iterator;
      |import java.util.List;
      |/** a library's OWN iterator interface — the shape flexmark, libGDX and every collection
      |  * library has, invented here so nothing in the engine names a ported library (§1). */
      |interface Cursor<E> extends Iterator<E> { boolean isReversed(); }
      |class Walker {
      |  void drain(Cursor<String> c) { while (c.hasNext()) { c.next(); } }
      |}
      |/** the receiver is a TYPE PARAMETER whose BOUND is the subtype. */
      |class Bounded<I extends Cursor<Integer>> {
      |  private I inner;
      |  boolean more() { return inner.hasNext(); }
      |  Integer step() { return inner.next(); }
      |}
      |/** the receiver is the shim itself — the case the head-symbol guard already covered, kept as
      |  * the regression anchor for it. */
      |class Direct {
      |  void drain(Iterator<String> it) { while (it.hasNext()) { it.next(); } }
      |}
      |/** NEGATIVE — a receiver retyped to a real scala collection must still lose its parens. */
      |class Plain {
      |  int size(List<String> xs) { return xs.size(); }
      |}
      |""".stripMargin

  private val after = Pipeline.run(SpoonTir.fromSource(src), List(new CollectionsTransform))
  private val out   = new TirEmitter(after).emit

  test("a receiver at a program-declared SUBTYPE of a shim keeps java's arity") {
    assert(clue(out).contains("c.hasNext()"))
    assert(out.contains("c.next()"))
    assert(!out.contains("c.hasNext)"))
  }

  test("…and so does one at a TYPE PARAMETER whose bound is that subtype") {
    assert(clue(out).contains("this.inner.hasNext()"))
    assert(out.contains("this.inner.next()"))
  }

  test("the shim itself is unchanged — the guard this widens, still holding") {
    assert(clue(out).contains("it.hasNext()"))
    assert(out.contains("it.next()"))
  }

  test("NEGATIVE — a real scala collection receiver still loses its parens") {
    assert(clue(out).contains("xs.size"))
    assert(!out.contains("xs.size()"))
  }
