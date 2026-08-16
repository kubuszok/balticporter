package balticporter.corpus

import balticporter.emit.TirEmitter
import balticporter.frontend.spoon.SpoonTir

/** JAVA'S UNCHECKED CONVERSION AT AN *INHERITED* FORMAL — the one place a callee's type variable
  * really does resolve at the call site.
  *
  * `ENGINE-LIMITS.md` G12 is the general rule and it is right: an executable's formal may name the
  * CALLEE's type variables, which have no meaning where the call is written, so a cast to one
  * renders a `?T` stub. An INHERITED formal is the exception — the variable belongs to an ANCESTOR
  * and the `extends` clause says what this class instantiated it as, which is `ParentSubst`'s own
  * fact in the TIR (`CLAUDE.md` §4.56) and is exact rather than a guess.
  *
  * Four negatives, each of which the positive would swallow:
  *
  *   - a DIMENSION mismatch. At an `H[]...` slot java PACKS a one-dimensional argument into a fresh
  *     `H[][]` (`ENGINE-LIMITS.md` G26, javac-verified) and this port forwards it; a cast over that
  *     would make the arity defect COMPILE and throw `ClassCastException` at run time, which is the
  *     one direction §3 forbids — an error traded for a silent-until-executed throw;
  *   - a callee the class DECLARES ITSELF. Its type variables are its own and G12's refusal stands;
  *   - an argument that is not RAW. Java performs no unchecked conversion, so neither may the port;
  *   - TWO ancestors whose variables share a NAME. The lookup is keyed by (declaring type, name) —
  *     `ParentSubst`'s identity — because the name-keyed map beside it is the one `inheritedTp`
  *     measured at 161/142/141 and is switched off for.
  */
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

  // -------------------------------------------------------------------------
  // the negatives
  // -------------------------------------------------------------------------

  test("NEGATIVE: a DIMENSION mismatch declines — java PACKS there and a cast would throw") {
    val out = emitted(single)
    val flat = out.linesIterator.filter(l => l.contains("super.takeAll") && !l.contains("scala.Array[scala.Array")).toList
    assertEquals(clue(flat).size, 1, s"--- emitted ---\n$out")
    assert(!flat.head.contains("asInstanceOf"),
           s"a one-dimensional argument at an `H[]...` slot was cast to a two-dimensional type; " +
             s"that compiles and throws at run time (G26)\n${flat.head}")
  }

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
