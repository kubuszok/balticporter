package balticporter.corpus

import balticporter.emit.TirEmitter
import balticporter.frontend.spoon.SpoonTir
import balticporter.tir.{CtorFunnel, OmissionCheck, Pipeline}

/** A10 / `ENGINE-LIMITS.md` C7 — the PREFIX STRIP, and the runtime shape it repairs.
  *
  * The escaping-promotion divergence is not uniform. Where an escaping root's own body literally
  * BEGINS with the promoted body, the duplication has an exact repair: the class body runs the
  * prefix, `this(…)` returns, and the residual runs — the same statements, in the same order, once
  * each. Nothing is approximated and no argument is guessed.
  *
  * libGDX's `Button` is the shape and the reason this is worth an emission change: `Button()` is
  * `{ initialize(); }` and `Button(Skin)` is `{ initialize(); setSkin(skin); }`. Promoting the
  * first ran `initialize()` twice on eight of ten construction paths, adding a SECOND
  * `ClickListener`; every click then called `setChecked` twice and the button never changed state.
  * A green compile said nothing about it (CLAUDE.md §3), and no count moved.
  *
  * The two halves are asserted together on purpose: the emitted text must lose the duplicate AND
  * `OmissionCheck.promotedBodyOnEveryPath` must stop reporting that path — they are one function
  * (`Plans.residualBody`), and a check that kept reporting a path the emitter had just repaired
  * would be the C7-at-`droppedSuperArgs` failure in its other direction.
  */
class CtorFunnelPrefixStripSpec extends munit.FunSuite:

  private val src =
    """package demo;
      |class Parent { Parent(int a) {} Parent(int a, int b) {} }
      |/** the `Button` shape, ON THE WALL: the paramful root does NOT delegate, its body begins with
      |  * exactly what the promoted nilary root does, and the two roots reach DIFFERENT parent
      |  * constructors. The wall is what keeps a PROMOTION here — with every root reaching one parent
      |  * constructor A2's synthesis takes over, promotes nothing and leaves no prefix to strip.
      |  * These two fixtures are now the only coverage of the fallback, which is why they were
      |  * retargeted rather than deleted. */
      |class Button extends Parent {
      |  int listeners;
      |  String skin;
      |  Button() { super(0); initialize(); }
      |  Button(String skin) { super(0, 1); initialize(); setSkin(skin); }
      |  void initialize() { listeners++; }
      |  void setSkin(String s) { skin = s; }
      |}
      |/** the promoted body is NOT a prefix here — the escaping root does something else first, so
      |  * there is nothing to strip and the divergence stays counted. */
      |class NotAPrefix extends Parent {
      |  int n;
      |  NotAPrefix() { super(0); bump(); }
      |  NotAPrefix(int k) { super(k, 1); n = k; bump(); }
      |  void bump() { n++; }
      |}
      |""".stripMargin

  private val program = Pipeline.run(SpoonTir.fromSource(src), Nil)
  private val out     = new TirEmitter(program).emit
  private val plans   = CtorFunnel.Plans(program)

  private def classOf_(name: String) =
    program.units.find(u => program.symbolOf(u.symbol).exists(_.name == name)).get

  /** the text of ONE `def this(...)`, cut at its own closing brace — not a fixed window, which
    * ran on into the next member and made the assertion below read that member's body. */
  private def ctorText(param: String): String =
    val start = out.indexOf(s"def this($param)")
    assert(start >= 0, s"no `def this($param)` in:\n$out")
    val end = out.indexOf("\n  }", start)
    out.substring(start, if end < 0 then out.length else end + 4)

  test("the promoted body IS the class body — that is what makes it run on every path") {
    assert(clue(out).contains("class Button extends demo.Parent(0) {"))
    // `initialize()` inlined at class-body level, from the promoted nilary constructor
    assert(out.linesIterator.exists(l => l.trim == "this.initialize()"), out)
  }

  test("PREFIX STRIP: the escaping root emits only its RESIDUAL — one `initialize()`, not two") {
    val t = ctorText("skin: java.lang.String")
    assert(t.contains("setSkin(skin"), clue(t))
    assert(!t.contains("this.initialize()"), clue(t))
  }

  test("…and the emitted class therefore installs ONE listener, not two, on that path") {
    // the runtime shape, counted the way the C7 probe counts it: how many times `initialize()`
    // appears on the `Button(String)` construction path — class body once, secondary body zero.
    val body   = out.substring(out.indexOf("class Button extends"), out.indexOf("class NotAPrefix"))
    val onPath = body.linesIterator.count(_.trim == "this.initialize()")
    assertEquals(onPath, 1, clue(body))
  }

  test("the omission count agrees: this path no longer duplicates, so it is no longer reported") {
    assertEquals(OmissionCheck.promotedBodyOnEveryPath(program).filter(_.owner == "demo.Button"), Nil)
  }

  test("residualBody and promotionEscapes are ONE answer, not two") {
    val cd    = classOf_("Button")
    val ctors = CtorFunnel.ctorsOf(program, cd.body)
    val withResidual = ctors.filter(d => plans.residualBody(cd, d).isDefined)
    assertEquals(withResidual.size, 1)
    assert(plans.promotionEscapes(cd).forall(d => !withResidual.exists(_.symbol == d.symbol)))
  }

  test("a promoted body that is NOT a prefix is left alone and stays counted") {
    val cd = classOf_("NotAPrefix")
    assertEquals(CtorFunnel.ctorsOf(program, cd.body).flatMap(plans.residualBody(cd, _)), Nil)
    assertEquals(OmissionCheck.promotedBodyOnEveryPath(program).count(_.owner == "demo.NotAPrefix"), 1)
  }
