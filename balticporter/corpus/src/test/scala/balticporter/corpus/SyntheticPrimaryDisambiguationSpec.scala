package balticporter.corpus

import balticporter.emit.TirEmitter
import balticporter.frontend.spoon.SpoonTir
import balticporter.tir.{OmissionCheck, Pipeline}

/** A synthesised primary has to COEXIST with the class's real constructors and to be REACHABLE
  * past them, and those are two different questions with two different tests. */
class SyntheticPrimaryDisambiguationSpec extends munit.FunSuite:

  private val src =
    """package demo;
      |public class Base { Base(int n, boolean b) {} }
      |/** COLLAPSE — `Coll(int,boolean)` already HAS the slot signature and passes its parameters
      |  * straight through, so it is promoted and nothing is synthesised. */
      |public class Coll extends Base {
      |  Coll(int n, boolean b) { super(n, b); }
      |  Coll(int n)            { super(n, false); }
      |}
      |/** MARKER, by ERASURE — the same signature, but the constructor COMPUTES its super arguments,
      |  * so there is no pass-through to promote and the two declarations cannot coexist. */
      |public class Marked extends Base {
      |  Marked(int n, boolean b) { super(n + 1, b); }
      |  Marked(int n)            { super(n, false); }
      |}
      |public class BFont { }
      |public class DFont extends BFont { }
      |public class BCache { BCache(BFont f, boolean b) {} }
      |/** MARKER, by APPLICABILITY — C8's shape. No signature EQUALS the slots `(BFont, boolean)`,
      |  * but `DCache(DFont, boolean)` is applicable to `this(f, b)` and strictly more specific, so
      |  * without the marker it delegates to ITSELF and the other root resolves to it. */
      |public class DCache extends BCache {
      |  DCache(DFont f)            { super(f, true); }
      |  DCache(DFont f, boolean b) { super(f, b); }
      |}
      |""".stripMargin

  private val program = Pipeline.run(SpoonTir.fromSource(src), Nil)
  private val out     = new TirEmitter(program).emit

  test("COLLAPSE — a pass-through root whose parameters ARE the slots is promoted, not disambiguated") {
    assert(clue(out).contains("class Coll private[demo] (n$p$: scala.Int, b$p$: scala.Boolean) extends demo.Base(n$p$, b$p$)"))
    // no synthetic member and no marker: this class is byte-for-byte what it was before the
    // disambiguator existed, which is the whole reason collapse is tried first
    assert(!out.contains("class Coll protected ("))
    assert(!out.contains("object Coll"))
  }

  test("MARKER by ERASURE — same signature, nothing to collapse onto, so the arity changes") {
    assert(clue(out).contains(
      "class Marked protected (sup$0: scala.Int, sup$1: scala.Boolean, ctor$: Marked.Funnel) extends demo.Base(sup$0, sup$1)"))
  }

  test("MARKER by APPLICABILITY — the C8 negative: the narrower real constructor must NOT win") {
    assert(clue(out).contains(
      "class DCache protected (sup$0: demo.BFont, sup$1: scala.Boolean, ctor$: DCache.Funnel) extends demo.BCache(sup$0, sup$1)"))
    // the delegation writes THREE arguments, so `DCache(DFont, boolean)` — which is applicable to
    // two and more specific than the primary — is not in the candidate set. With two it delegated
    // to itself, which is an infinite recursion the compiler only caught because the other root
    // then resolved to a constructor declared BELOW it.
    assert(clue(out).contains("def this(f: demo.DFont, b: scala.Boolean) = {"))
    // ASCRIBED, never a bare `null`: `null` inhabits every reference type, so `this(f, b, null)`
    // would be applicable to `DCache(DFont, boolean)` as well and scalac would report an ambiguous
    // overload — the very failure the marker exists to remove.
    assert(out.contains("this(f, b, (null: DCache.Funnel))"))
    assert(!out.contains("this(f, b)\n"))
  }

  test("the marker is PROTECTED in the companion — a companion-`private` one cannot be declared") {
    assert(clue(out).contains("object Marked {\n  protected final class Funnel\n}"))
    assert(clue(out).contains("object DCache {\n  protected final class Funnel\n}"))
    assert(!out.contains("private final class Funnel"))
  }

  test("every root of a disambiguated class is expressed — none is reported as a dropped super") {
    val dropped = OmissionCheck.droppedSuperArgs(program)
    assertEquals(dropped.filter(d => d.owner == "demo.Marked" || d.owner == "demo.DCache"), Nil)
  }

  test("NEGATIVE — a marker is minted only where one is NEEDED, never on every synthesis") {
    // `Coll` collapses and the ordinary synthesis (pinned by `SyntheticPrimarySlotsSpec`) has no
    // marker at all. A disambiguator that fired everywhere would cost a companion member on ~430
    // corpus classes and change the construction API of every one of them.
    assertEquals(clue(out).sliding("class Funnel".length).count(_ == "class Funnel"), 2)
  }

  /** COLLAPSE IS DECLINED WHERE ITS PROMOTION WOULD ESCAPE, and that is a correction to the
    * ordering rather than an exception to it. */
  test("COLLAPSE DECLINED — a pass-through root whose body would ESCAPE gets the marker instead") {
    val escaping =
      """package demo3;
        |public class EBase { EBase(int n, boolean b) {} }
        |public class Escaping extends EBase {
        |  static int count;
        |  static void bump() { count++; }
        |  Escaping(int n, boolean b) { super(n, b); bump(); }
        |  Escaping(int n)            { super(n, false); }
        |}
        |""".stripMargin
    val p3 = Pipeline.run(SpoonTir.fromSource(escaping), Nil)
    val o3 = new TirEmitter(p3).emit
    // `Escaping(int, boolean)` HAS the slot signature and passes straight through, so the pre-
    // correction ordering promoted it — and `Escaping(int)` does not delegate to it, so `bump()`
    // ran on `new Escaping(5)` where java ran nothing.
    assert(clue(o3).contains(
      "class Escaping protected (sup$0: scala.Int, sup$1: scala.Boolean, ctor$: Escaping.Funnel) extends demo3.EBase(sup$0, sup$1)"))
    assert(o3.contains("protected final class Funnel"))
    // no java body became the class body, so there is nothing left to escape — and the check that
    // counts it agrees, because it is the same function the nomination asked.
    assertEquals(OmissionCheck.promotedBodyOnEveryPath(p3).filter(_.owner == "demo3.Escaping"), Nil)
    assert(!o3.contains("class Escaping(n"))
  }

  test("the marker NAME avoids what the class already declares") {
    val clash =
      """package demo2;
        |public class B2 { B2(int n, boolean b) {} }
        |public class M2 extends B2 {
        |  static class Funnel { }
        |  M2(int n, boolean b) { super(n + 1, b); }
        |  M2(int n)            { super(n, false); }
        |}
        |""".stripMargin
    val o2 = new TirEmitter(Pipeline.run(SpoonTir.fromSource(clash), Nil)).emit
    // §4.55 one level down: keep appending until the name is free rather than assuming the first
    // is — the java `Funnel` lives in the same companion the marker would.
    assert(clue(o2).contains("ctor$: M2.Funnel$"))
    assert(o2.contains("protected final class Funnel$"))
  }
