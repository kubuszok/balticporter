package balticporter.corpus

import balticporter.emit.TirEmitter
import balticporter.frontend.spoon.SpoonTir
import balticporter.tir.{Constant, CtorFunnel, OmissionCheck, Phase, Pipeline, Program, Term, Tree, Trivia,
                         TriviaKind}

/** WHAT SHAPE A CONSTRUCTOR'S BODY ARRIVES IN — and why `CtorFunnel.delegationOnlyNilary` may not
  * have a fallback arm (`ENGINE-LIMITS.md` C11). */
class CtorFunnelBodyShapeSpec extends munit.FunSuite:

  /** The C11 shape itself: `Font()` is nilary, delegates with ARGUMENTS, and sits in front of a class
    * whose primary is scala's own implicit nilary one. */
  private val src =
    """package demo;
      |public class Font {
      |  int size; String name;
      |  public Font()                      { this(seed(), "d"); }
      |  public Font(int size)              { this(size, "d"); }
      |  public Font(int size, String name) { this.size = size; this.name = name; grow(size); }
      |  static int seed() { return 12; }
      |  void grow(int by) { size = size + by; }
      |}
      |public class Sub extends Font { }
      |""".stripMargin

  private def parsed: Program = SpoonTir.fromSource(src, "Font.java")

  /** rewrite the rhs of the NILARY constructor of `demo.Font`, through the pipeline rather than by a
    * private walk (CLAUDE.md §3). */
  private def reshaped(p: Program)(f: Tree.Block => Term): Program =
    val phase = new Phase:
      def name: String = "spec/reshape-ctor-body"
      override def transformDefDef(d: Tree.DefDef)(using prog: Program): Tree.DefDef =
        val isNilaryCtor =
          prog.symbolOf(d.symbol).exists(s => s.name == "<init>" && s.fullName.startsWith("demo.Font")) &&
            d.paramss.flatten.isEmpty
        d.rhs match
          case Some(b: Tree.Block) if isNilaryCtor => d.copy(rhs = Some(f(b)))
          case _                                   => d
    Pipeline.run(p, List(phase))

  private def nilaryCtorOf(p: Program): Tree.DefDef =
    val font = p.units.find(u => p.symbolOf(u.symbol).exists(_.fullName == "demo.Font")).get
    CtorFunnel.ctorsOf(p, font.body)
      .find(d => CtorFunnel.valueParams(p, d).isEmpty)
      .getOrElse(fail("no nilary constructor in demo.Font"))

  private def emitted(p: Program): String = new TirEmitter(p).emit

  // -------------------------------------------------------------------------

  test("the reference answer: a BLOCK body of nothing but a delegation carries its arguments") {
    val p = parsed
    assertEquals(CtorFunnel.delegationOnlyNilary(p, nilaryCtorOf(p)).map(_.size), Some(2))
    assertEquals(OmissionCheck.droppedNilaryCtors(p, p.units).map(_.owner), List("demo.Font"))
    assert(!clue(emitted(p)).contains("def this()"), emitted(p))
  }

  test("TRAP 1: one comment above the body must not turn a carried delegation into a silent drop") {
    val p = reshaped(parsed)(b => Tree.Commented(List(Trivia(TriviaKind.Line, "// seed it")), b))
    // the arguments are still there, so the drop is still an OMISSION and still counted…
    assertEquals(clue(CtorFunnel.delegationOnlyNilary(p, nilaryCtorOf(p))).map(_.size), Some(2))
    assertEquals(clue(OmissionCheck.droppedNilaryCtors(p, p.units)).map(_.owner), List("demo.Font"))
    // …and the emission agrees with the count, which is the property the two share this predicate for
    assert(!clue(emitted(p)).contains("def this()"), emitted(p))
  }

  test("TRAP 2: an unbraced NON-delegation body is `None` — emitted and loud, never dropped") {
    // a single statement that is not a delegation at all: java could not write this constructor
    // without braces, but a lowering can, and `Some(Nil)` would have deleted a body that RUNS.
    val p = reshaped(parsed)(b => Tree.Literal(Constant.UnitC, b.tpe, b.origin))
    assertEquals(CtorFunnel.delegationOnlyNilary(p, nilaryCtorOf(p)), scala.None)
    // …so nothing DROPS it, and nothing counts it as dropped either: the declaration is emitted and
    // `E120` names it, which is a compile error an agent can act on rather than a behaviour change.
    assertEquals(OmissionCheck.droppedNilaryCtors(p, p.units), Nil)
    assert(clue(emitted(p)).contains("def this()"), emitted(p))
  }

  test("…and an unbraced body that IS the delegation is dropped AND counted, not silently emptied") {
    val p = reshaped(parsed)(b => b.stats.collectFirst { case t: Term => t }.get)
    assertEquals(clue(CtorFunnel.delegationOnlyNilary(p, nilaryCtorOf(p))).map(_.size), Some(2))
    assertEquals(OmissionCheck.droppedNilaryCtors(p, p.units).map(_.owner), List("demo.Font"))
  }

  test("NEGATIVE: `Some(Nil)` still means an EMPTY statement list, and that drop stays uncounted") {
    // `C() { }` and `C() { super(); }` are both scala's implicit primary; dropping them loses nothing
    // and must not be reported as an omission.
    val nil = SpoonTir.fromSource(
      """package demo;
        |public class Empty {
        |  int n;
        |  public Empty()      { super(); }
        |  public Empty(int a) { n = a; }
        |}
        |""".stripMargin, "Empty.java")
    val ctor = CtorFunnel.ctorsOf(nil, nil.units.head.body).find(d => CtorFunnel.valueParams(nil, d).isEmpty).get
    assertEquals(CtorFunnel.delegationOnlyNilary(nil, ctor), Some(Nil))
    assertEquals(OmissionCheck.droppedNilaryCtors(nil, nil.units), Nil)
    // …and an EMPTIED block is the same answer, reached the same way
    val p = reshaped(parsed)(b => b.copy(stats = Nil))
    assertEquals(CtorFunnel.delegationOnlyNilary(p, nilaryCtorOf(p)), Some(Nil))
    assertEquals(OmissionCheck.droppedNilaryCtors(p, p.units), Nil)
  }
