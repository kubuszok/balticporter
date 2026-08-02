package balticporter.corpus

import balticporter.emit.TirEmitter
import balticporter.frontend.spoon.SpoonTir
import balticporter.tir.{CtorFunnel, OmissionCheck, Pipeline}

/** THE THIRD THROWABLE SHAPE: several roots, several different `super(...)`, and NOT ONE of them
  * passing its own parameters straight through.
  *
  * `CtorFunnelThrowablePaddingSpec` covers the shape where one root IS the widest overload —
  * `plan0` promotes it and every narrower root pads into its parameters. That nomination is a
  * PROMOTION and it needs a root to promote. Where no root qualifies, `plan0` nominated NOTHING,
  * `Plan.none` was the answer, and every root's `super(args)` was lowered to a bare `this()`: the
  * class compiled, no count moved except an omission nobody read, and every exception the port
  * threw carried a NULL MESSAGE AND NO CAUSE (`CLAUDE.md` §4.4's own row, shipping).
  *
  * The missing piece was a primary to delegate TO. `ENGINE-LIMITS.md` C3: synthesise one at the JDK
  * throwable's WIDEST overload, and let each root pad into it through exactly the machinery the
  * promotion already uses. Everything the padding rests on is a JDK fact — the constructor set is
  * `()`, `(String)`, `(String, Throwable)`, `(Throwable)` and each shorter overload delegates to the
  * widest with `null` where it takes nothing — which is why the rule may not leave that family:
  * guessing outside it measured 0 -> 55 compile errors (`ENGINE-LIMITS.md` C3, K5.5).
  *
  * Two boundaries are pinned here as hard as the fix itself, because both are ways it could widen
  * into a guess: the widest overload has to be one a root ACTUALLY CALLED (the engine has no symbol
  * for a constructor nothing in the program names), and a non-JDK parent gains nothing at all.
  */
class CtorFunnelThrowableSynthesisSpec extends munit.FunSuite:

  private val src =
    """package demo;
      |/** a throwable the PROGRAM declares — passed into a `Throwable` slot, it is a SUBTYPE of the
      |  * formal, which is exactly what a head-name type match declines. */
      |public class Rec extends RuntimeException { public int at() { return 1; } }
      |public class Ctx { public int at() { return 2; } }
      |
      |/** THREE roots, three different `super(...)`, none of them a pass-through. */
      |public class Boom extends RuntimeException {
      |  public int line;
      |  Boom(Rec e)                           { super(describe(e), e); this.line = e.at(); }
      |  Boom(String m, Ctx ctx)               { super(m);              this.line = ctx.at(); }
      |  Boom(String m, int line, Throwable c) { super(m, c);           this.line = line; }
      |  static String describe(Rec e) { return "boom"; }
      |}
      |
      |/** the `(Throwable)` overload beside the widest one, neither a pass-through: the message the
      |  * JDK computes for itself has to survive a SYNTHESIS exactly as it survives a promotion. */
      |public class Cause extends RuntimeException {
      |  Cause(Ctx ctx, Throwable c)    { super("x", c); }
      |  Cause(Throwable c, int unused) { super(c); }
      |}
      |
      |/** `(String)` and `(Throwable)` and NOTHING WIDER. The JDK's `(String, Throwable)` is real,
      |  * but no root calls it, so the engine holds no signature for it — there is nothing to
      |  * synthesise AT and the honest answer is the counted omission. */
      |public class NoWidest extends RuntimeException {
      |  NoWidest(Ctx ctx)               { super("x"); }
      |  NoWidest(Throwable c, int unused) { super(c); }
      |}
      |
      |/** THE FENCE. A parent whose constructor set the engine does not know — padding here is a
      |  * guess, and guessing measured 0 -> 55. Same root shape as `Boom`, different parent.
      |  * (The `return`s keep the constructor REPLAY out of it, so what the omission count reports
      |  * is this nomination and not another mechanism expressing the same call.) */
      |public class Lib {
      |  Lib(String a)        { if (a.isEmpty()) return; }
      |  Lib(String a, int b) { if (b < 0) return; }
      |}
      |public class Sub extends Lib {
      |  Sub(Ctx ctx)          { super("x"); }
      |  Sub(Ctx ctx, int n)   { super("y", n); }
      |}
      |""".stripMargin

  private val program = Pipeline.run(SpoonTir.fromSource(src), Nil)
  private val out     = new TirEmitter(program).emit

  private def classBody(name: String): String =
    out.linesIterator.dropWhile(!_.startsWith(s"class $name")).takeWhile(_ != "}").mkString("\n")

  test("the synthesised primary sits at the JDK's WIDEST overload and passes it straight up") {
    assert(clue(classBody("Boom")).contains(
      "protected (sup$0: java.lang.String, sup$1: java.lang.Throwable) " +
        "extends java.lang.RuntimeException(sup$0, sup$1)"))
  }

  test("a root that called the WIDEST overload delivers both arguments unchanged") {
    assert(clue(classBody("Boom")).contains(
      "def this(m: java.lang.String, line: scala.Int, c: java.lang.Throwable) = {\n    this(m, c)"))
  }

  test("a root that called `(String)` pads the CAUSE — the message is NOT lost") {
    assert(clue(classBody("Boom")).contains(
      "def this(m: java.lang.String, ctx: demo.Ctx) = {\n    this(m, null.asInstanceOf[java.lang.Throwable])"))
  }

  test("a SUBTYPE in the `Throwable` slot is delivered, not padded over") {
    // `super(describe(e), e)` passes a `demo.Rec`, whose head name is not `java.lang.Throwable`.
    // The delegation is decided by WHICH overload java called, read off the target constructor's
    // own formals — never by matching the ARGUMENT's type against the formal's name.
    val boom = clue(classBody("Boom"))
    assert(boom.contains("def this(e: demo.Rec) = {"))
    assert(boom.contains(", e)"))
    assert(!boom.contains("null.asInstanceOf[java.lang.Throwable])\n    this.line = e.at()"))
  }

  test("`super(cause)` still computes the JDK's own message under a SYNTHESIS") {
    assert(clue(classBody("Cause")).contains(
      "this(java.util.Objects.toString(c, null), c)"))
  }

  test("nothing is counted as dropped for the classes the synthesis expresses") {
    val owners = OmissionCheck.droppedSuperArgs(program).map(_.owner).distinct
    assert(!owners.contains("demo.Boom"), clue(owners))
    assert(!owners.contains("demo.Cause"), clue(owners))
  }

  test("NO ROOT CALLS THE WIDEST OVERLOAD — refused, and still counted") {
    // the alternative is to mint a call to a constructor the program never names, which is the
    // guess this whole family of rules exists to refuse.
    val nw = clue(classBody("NoWidest"))
    assert(nw.contains("this()"))
    assert(!nw.contains("sup$0"))
    assert(OmissionCheck.droppedSuperArgs(program).map(_.owner).contains("demo.NoWidest"))
  }

  test("THE FENCE HOLDS — a non-JDK parent with the same root shape gains nothing") {
    val sub = clue(classBody("Sub"))
    assert(!sub.contains("sup$0"))
    assert(sub.contains("this()"))
    assert(OmissionCheck.droppedSuperArgs(program).map(_.owner).contains("demo.Sub"))
  }

  test("the shape is NAMED, so `decisions.tsv` and the port map say which of the seven it is") {
    val plans = CtorFunnel.Plans(program)
    def shapeOf(n: String) =
      program.units.find(u => program.symbolOf(u.symbol).exists(_.name == n)).map(plans.shape).getOrElse("?")
    assertEquals(shapeOf("Boom"), "padded-throwable-synthesis")
    assertEquals(shapeOf("NoWidest"), "not-funnelled")
    assertEquals(shapeOf("Sub"), "not-funnelled")
  }
