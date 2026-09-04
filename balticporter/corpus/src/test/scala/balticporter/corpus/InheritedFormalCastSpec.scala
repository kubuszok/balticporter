package balticporter.corpus

import balticporter.emit.TirEmitter
import balticporter.frontend.spoon.SpoonTir

/** JAVA'S UNCHECKED CONVERSION AT AN *INHERITED* FORMAL — the one place a callee's type variable
  * really does resolve at the call site. */
class InheritedFormalCastSpec extends munit.FunSuite:

  private def emitted(src: String): String =
    val p = SpoonTir.fromSource(src)
    new TirEmitter(p).emit

  // -------------------------------------------------------------------------
  // the positive
  // -------------------------------------------------------------------------

  private val single =
    """package demo;
      |class Box<T> { }
      |abstract class Base<H> {
      |  protected void take(H h) { }
      |  protected void takeAll(H[]... hs) { }
      |}
      |class Impl extends Base<Box<String>> {
      |  @SuppressWarnings("rawtypes")
      |  public void one(Box h) { super.take(h); }
      |  @SuppressWarnings("rawtypes")
      |  public void flat(Box[] hs) { super.takeAll(hs); }
      |  @SuppressWarnings("rawtypes")
      |  public void nested(Box[][] hs) { super.takeAll(hs); }
      |  private void mine(Box<String> b) { }
      |  public void exact(Box<String> b) { super.take(b); }
      |}
      |""".stripMargin

  test("a RAW argument at an ancestor's type-variable formal takes java's unchecked cast") {
    val out = emitted(single)
    assert(out.contains("super.take(h.asInstanceOf[demo.Box[java.lang.String]])"),
           s"no cast at the inherited formal\n--- emitted ---\n$out")
  }

  test("…and it reaches INSIDE an array whose dimension already agrees") {
    val out = emitted(single)
    assert(out.contains("asInstanceOf[scala.Array[scala.Array[demo.Box[java.lang.String]]]]"),
           s"the two-dimensional call did not take the cast\n--- emitted ---\n$out")
  }

  test("a DIMENSION mismatch is PACKED, and the cast lands on the ELEMENT — never on the array") {
    // The one cell where this rule and `ENGINE-LIMITS.md` G26's meet, and the reason the cast alone
    // was refused for a wave: at an `H[]...` slot java PACKS a one-dimensional argument into a fresh
    // `H[][]`, so a cast to the two-dimensional type is a `checkcast [[L…` against a value that is
    // `[L…` — it COMPILES and throws at run time, which is the one direction §3 forbids.
    val out  = emitted(single)
    val flat = out.linesIterator.filter(_.contains("super.takeAll")).toList
      .filterNot(_.contains("hs.asInstanceOf[scala.Array[scala.Array"))
    assertEquals(clue(flat).size, 1, s"--- emitted ---\n$out")
    assert(flat.head.contains(
             "scala.Array[scala.Array[demo.Box[java.lang.String]]](hs.asInstanceOf[scala.Array[demo.Box[java.lang.String]]])"),
           s"the one-dimensional call is not java's pack over a one-dimensional cast\n${flat.head}")
    assert(!flat.head.contains("hs.asInstanceOf[scala.Array[scala.Array"),
           s"the argument itself was cast to a two-dimensional type; that compiles and throws (G26)\n${flat.head}")
    assert(!flat.head.contains("?H") && !flat.head.contains("scala.Array[?]"),
           s"the packed element rendered a sentinel rather than the `extends` clause's answer\n${flat.head}")
  }

  // -------------------------------------------------------------------------
  // the negatives
  // -------------------------------------------------------------------------

  test("NEGATIVE: an argument that is NOT raw takes no cast — java converts nothing") {
    val out = emitted(single)
    assert(out.contains("super.take(b)"), s"--- emitted ---\n$out")
  }

  test("NEGATIVE: a callee the class DECLARES ITSELF names ITS OWN variable, not an ancestor's") {
    val own =
      """package demo;
        |class Box<T> { }
        |abstract class Up<T> { protected void up(Box<T> b) { } }
        |class Solo<T> extends Up<String> {
        |  private void mine(Box<T> b) { }
        |  @SuppressWarnings("rawtypes")
        |  public void call(Box b) { mine(b); }
        |}
        |""".stripMargin
    val out = emitted(own)
    assert(!out.contains("?T"), s"an unresolvable variable reached the output\n--- emitted ---\n$out")
    // `mine` is `Solo`'s own, so its `T` is `Solo`'s and the existing same-variable-in-scope path
    // answers it. A lookup that reached for the ANCESTOR's instantiation would write `String`.
    assert(out.contains("this.mine(b.asInstanceOf[demo.Box[T]])"), s"--- emitted ---\n$out")
    assert(!out.contains("this.mine(b.asInstanceOf[demo.Box[java.lang.String]])"), s"--- emitted ---\n$out")
  }

  test("NEGATIVE: two ancestors' same-named variables are two keys, not one") {
    val clash =
      """package demo;
        |class Box<T> { }
        |abstract class Up<T> { protected void up(Box<T> b) { } }
        |interface Side<T> { void side(Box<T> b); }
        |class Both extends Up<String> implements Side<Integer> {
        |  @SuppressWarnings("rawtypes")
        |  public void a(Box b) { super.up(b); }
        |  @SuppressWarnings("rawtypes")
        |  public void side(Box b) { }
        |}
        |""".stripMargin
    val out = emitted(clash)
    assert(out.contains("super.up(b.asInstanceOf[demo.Box[java.lang.String]])"),
           s"`Up`'s T must resolve to String, not to `Side`'s Integer\n--- emitted ---\n$out")
    assert(!out.contains("super.up(b.asInstanceOf[demo.Box[java.lang.Integer]])"),
           s"a name-keyed lookup took the wrong ancestor's argument\n--- emitted ---\n$out")
  }
