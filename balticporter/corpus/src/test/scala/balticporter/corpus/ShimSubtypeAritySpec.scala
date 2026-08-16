package balticporter.corpus

import balticporter.emit.TirEmitter
import balticporter.frontend.spoon.SpoonTir
import balticporter.testkit.PortSuite
import balticporter.tir.Pipeline
import balticporter.transform.CollectionsTransform

/** The scala-shaped call rewrites are refused on a SHIM receiver — and a library's OWN SUBTYPE of a
  * shim is a shim receiver (`CLAUDE.md` §4.5, §4.56).
  *
  * ==The defect==
  * `CollectionsTransform.parenless` strips `()` from `size`/`iterator`/`hasNext`/`next`, because a
  * scala collection's are parameterless. The runtime shims deliberately keep JAVA's arity — a class
  * that is both java `Iterable` and java `Iterator` cannot be modelled on scala's collection traits
  * at all — so a blanket `onShim` guard refuses every rewrite there.
  *
  * That guard asked the receiver's HEAD SYMBOL against the three shim symbols. It is exact for a
  * receiver the phase retyped, and it answers `false` for the one shape a library that defines its
  * own iterator is made of:
  *
  * {{{
  *   interface Cursor<E> extends java.util.Iterator<E>   →   trait Cursor[E] extends JavaIterator[E]
  *   while (c.hasNext())                                 →   while (c.hasNext)   // E: must be called with ()
  * }}}
  *
  * The head symbol is `Cursor`, which is no shim; and `inheritedKind` correctly answers
  * `Kind.Iterator`, because `hasNext` really does resolve to `java.util.Iterator#hasNext`. The two
  * together strip the parens from a call to a member declared `def hasNext()`. Sixteen measured
  * errors on one port, every one of them a receiver at a program-declared subtype.
  *
  * ==Two shapes above a receiver, not one==
  * `Walker` reaches the shim through a class PARENT; `Bounded` reaches it through a type parameter's
  * BOUND, which is the same question asked at the other kind of declaration — a value typed `I`
  * where `I extends Cursor<Integer>` has `Cursor`'s members and therefore java's arity. Two of the
  * sixteen were only the second shape, and a fix for the first alone leaves them.
  *
  * ==And the negative is the whole point of a guard that SUPPRESSES==
  * `Plain` proves the refusal did not become blanket: a receiver retyped to a real scala collection
  * still loses its `()`, which is what `parenless` exists for.
  */
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
