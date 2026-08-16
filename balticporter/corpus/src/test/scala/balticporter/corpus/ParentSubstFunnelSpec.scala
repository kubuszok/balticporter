package balticporter.corpus

import balticporter.emit.TirEmitter
import balticporter.frontend.spoon.SpoonTir
import balticporter.tir.Pipeline

/** A SYNTHESISED PRIMARY's slots are the PARENT CONSTRUCTOR's formals, and those formals are
  * written in the parent's scope — so they take the same substitution the diamond forwarder does
  * (`balticporter.tir.ParentSubst`, `CLAUDE.md` §4.56: one derivation, not one per caller).
  *
  * ==The defect==
  * Every root of `Widget` reaches the same parent constructor, none of them can be the primary, so
  * the funnel synthesises `protected (sup$0: <formal 0>)`. The formal is `Adapter<N>`:
  *
  * {{{
  *   class Widget protected (sup$0: fbound2.Adapter[N])       // Not found: type N
  *     extends fbound2.Handler[Widget, fbound2.Panel](sup$0)
  * }}}
  *
  * The `extends` clause on the very same line says what `N` is. Note what makes this one worse than
  * the forwarder's: the slot is named twice — once in the primary's signature and once as the
  * argument to the parent — and every `def this` in the class delegates to it, so one unresolved
  * parameter is the root of a whole file's worth of `Found: …` cascades.
  *
  * ==The three cases==
  *  - `Widget` — the formal mentions the parent's parameter, closed at a concrete type;
  *  - `Deep` — the binding is a GRANDparent's, reached through an intermediate that binds it on;
  *  - `Plain` — a parent constructor whose formals mention no parameter at all, whose emitted
  *    primary must be exactly what it was.
  *
  * The REPLAY path already substituted (it is where the derivation was first written); this spec is
  * the two callers that did not, and it fails on either half alone.
  */
class ParentSubstFunnelSpec extends munit.FunSuite:

  private val src =
    """package fbound2;
      |public interface Adapter<N> { N root(); }
      |public class Panel { public static final Adapter<Panel> ADAPTER = null; }
      |public class Handler<C, N> {
      |  protected Handler(Adapter<N> adapter) { }
      |}
      |/** TWO roots, ONE parent constructor, neither able to be the primary: the synthesis. */
      |public class Widget extends Handler<Widget, Panel> {
      |  public Widget(int a)    { super(Panel.ADAPTER); }
      |  public Widget(String s) { super(Panel.ADAPTER); }
      |}
      |/** the binding is a GRANDparent's — `Mid<X>` passes its own parameter on to `Handler`. */
      |public class Mid<X> extends Handler<Mid<X>, X> {
      |  protected Mid(Adapter<X> adapter) { super(adapter); }
      |}
      |public class Deep extends Mid<Panel> {
      |  public Deep(int a)    { super(Panel.ADAPTER); }
      |  public Deep(String s) { super(Panel.ADAPTER); }
      |}
      |/** NEGATIVE — the parent's formals name no type parameter, so the slot must not move. */
      |public class Anchor { protected Anchor(int cap, String tag) { } }
      |public class Plain extends Anchor {
      |  public Plain(int a)    { super(a, "a"); }
      |  public Plain(String s) { super(0, s); }
      |}
      |""".stripMargin

  private val program = Pipeline.run(SpoonTir.fromSource(src), Nil)
  private val out     = new TirEmitter(program).emit

  test("a synthesised slot is typed at the PARENT'S INSTANTIATION, not at its parameter") {
    assert(clue(out).contains("class Widget protected (sup$0: fbound2.Adapter[fbound2.Panel])"))
    assert(!out.contains("sup$0: fbound2.Adapter[N]"))
    // the `extends` clause passes the same slot, so both halves of the line agree
    assert(out.contains("extends fbound2.Handler[fbound2.Widget, fbound2.Panel](sup$0)") ||
           out.contains("extends fbound2.Handler[Widget, fbound2.Panel](sup$0)"), clue(out))
  }

  test("TRANSITIVE — the parameter is bound by a GRANDparent") {
    assert(clue(out).contains("class Deep protected (sup$0: fbound2.Adapter[fbound2.Panel])"))
    assert(!out.contains("sup$0: fbound2.Adapter[X]"))
  }

  test("NEGATIVE — a parent whose formals name no parameter is unmoved") {
    assert(clue(out).contains("class Plain protected (sup$0: scala.Int, sup$1: java.lang.String)"))
  }
