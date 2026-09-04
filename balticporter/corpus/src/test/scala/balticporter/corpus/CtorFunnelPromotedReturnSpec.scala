package balticporter.corpus

import balticporter.emit.TirEmitter
import balticporter.frontend.spoon.SpoonTir
import balticporter.tir.Pipeline

/** JS-C51 — a `return` in a java CONSTRUCTOR body, once `CtorFunnel` has promoted that body into
  * the CLASS BODY. */
class CtorFunnelPromotedReturnSpec extends munit.FunSuite:

  private def emit(src: String): String =
    new TirEmitter(Pipeline.run(SpoonTir.fromSource(src), Nil)).emit

  private val parent = "public class Effect { protected Effect(String label) {} }\n"

  /** the promoted root's body RETURNS EARLY — two exits, one of them inside a loop, which is what a
    * `boundary` would have had to be NAMED for and a `def` does not. */
  private val src =
    s"""package demo;
       |$parent
       |public class Parser extends Effect {
       |  int flags = 0;
       |  boolean all = false;
       |  public Parser(String label, String[] params) {
       |    super(label);
       |    if (params.length == 0) { flags = 3; all = true; return; }
       |    for (int i = 0; i < params.length; i++) {
       |      if (params[i].equals("stop")) return;
       |      flags |= i;
       |    }
       |    all = true;
       |  }
       |}
       |""".stripMargin

  private lazy val out = emit(src)

  test("a promoted body carrying `return` is wrapped in a LOCAL `def`, and the return is kept") {
    assert(clue(out).contains("def ctorBody$(): scala.Unit = {"), "no local `def` was interposed")
    assert(out.contains("ctorBody$()"), "the interposed `def` is declared and never called")
    // the `return`s are UNCHANGED — a `def` is the construct they already mean, so nothing is
    // renamed, nothing is named and no `boundary` appears anywhere near them
    assertEquals(out.linesIterator.count(_.trim == "return"), 2)
    assert(!out.contains("scala.util.boundary"), "a boundary was interposed where a `def` is exact")
  }

  test("the wrapper is a BLOCK, so nothing java never declared reaches the emitted surface") {
    // `private def ctorBody$` as a class MEMBER would be a name a consumer can see and a row in
    // `members.tsv` for a member java never had. Inside a block it is local to the construction
    // sequence, which is exactly what java's constructor body was.
    assert(!out.contains("def ctorBody$(): scala.Unit = {\n  }"), clue(out))
    assert(clue(out).contains("{\n    def ctorBody$(): scala.Unit = {"),
           "the `def` is not inside a block — as a class member it would be emitted surface")
    // …and the block opens a statement, so `joinStats` must have put the `;` in front of it or the
    // `{` reads as an anonymous-class body of the statement above (§4.58)
    assert(clue(out).contains(";\n  {\n    def ctorBody$()"),
           "the block was emitted without the separator that keeps it a statement")
  }

  test("the FIELDS stay where JLS 12.5 step 4 put them — above the wrapper, not inside it") {
    val body    = out.linesIterator.toList
    val flags   = body.indexWhere(_.contains("var flags: scala.Int = 0"))
    val wrapper = body.indexWhere(_.contains("def ctorBody$()"))
    assert(flags >= 0 && wrapper >= 0, clue(out))
    assert(flags < wrapper, "a field initialiser was pushed below the promoted body")
  }

  test("a promoted body with NO return is untouched — no `def` and no block") {
    val plain = emit(
      s"""package demo;
         |$parent
         |public class Plain extends Effect {
         |  int flags = 0;
         |  public Plain(String label, String[] params) { super(label); flags = params.length; }
         |}
         |""".stripMargin)
    assert(!plain.contains("ctorBody$"), clue(plain))
    assert(plain.contains("this.flags = params$p.length"), clue(plain))
  }

  test("a `return` that belongs to an ANONYMOUS CLASS's method is not the constructor's") {
    // java binds it to `run()` and so does scala, so there is nothing to wrap — and wrapping would
    // change a class that has no defect. `returnsIn` stops at `Tree.AnonClass` for exactly this.
    val installs = emit(
      s"""package demo;
         |$parent
         |public class Installs extends Effect {
         |  Runnable r;
         |  public Installs(String label, final int k) {
         |    super(label);
         |    r = new Runnable() { public void run() { if (k == 0) return; System.out.print(k); } };
         |  }
         |}
         |""".stripMargin)
    assert(!installs.contains("ctorBody$"), clue(installs))
    // …and the `return` is still there, in the method java bound it to
    assert(installs.contains("return"))
  }
