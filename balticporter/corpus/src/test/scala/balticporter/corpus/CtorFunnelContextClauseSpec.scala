package balticporter.corpus

import balticporter.emit.TirEmitter
import balticporter.frontend.spoon.SpoonTir
import balticporter.tir.{Constant, CtorFunnel, Descriptor, Flags, MemberKey, OmissionCheck, Origin, Phase,
                         Pipeline, Program, StandardTraversal, Statement, Surface, SymbolTable, Tree,
                         TypeRepr, TypeTree}
import balticporter.transform.*

/** THE CLAUSE-BEARING EMPTY PRIMARY — a class the funnel neither PROMOTES nor SYNTHESISES, carrying a
  * context clause (`ENGINE-LIMITS.md` CT5, `DESIGN.md` §8.2). */
class CtorFunnelContextClauseSpec extends munit.FunSuite:

  private val preamble =
    """package demo;
      |public class Gdx { public static Graphics graphics; }
      |public class Graphics { public GL gl20; public int getWidth() { return 0; } }
      |public class GL { public void clear() {} }
      |""".stripMargin

  /** The census's dominant shape: TWO paramful roots reaching the same (implicit, nilary) parent
    * constructor, no nilary root and no hoistable field — `syntheticPrimary` has nothing to
    * synthesise and declines, which is `Plan.none` (`IndexBufferObject`, `Mesh`,
    * `VertexBufferObject`). `Buffer(int)` is the deep secondary chain, and `Buffer.Nested` is the
    * same shape one level down — a walk over top-level classes finds neither. */
  private val bufferSrc = preamble +
    """public class Buffer {
      |  int n;
      |  public Buffer(int max)                       { this(true, max); }
      |  public Buffer(boolean isStatic, int max)     { init(); n = max; }
      |  public Buffer(boolean isStatic, String data) { init(); n = data.length(); }
      |  void init() { Gdx.graphics.gl20.clear(); }
      |  public void bind() { Gdx.graphics.gl20.clear(); }
      |  public static class Nested {
      |    int m;
      |    public Nested(int a, int b)    { hop(); m = a; }
      |    public Nested(String s, int b) { hop(); m = b; }
      |    void hop() { Gdx.graphics.gl20.clear(); }
      |  }
      |}
      |""".stripMargin

  /** The `BitmapFont` / `DistanceFieldFont` shape, which reaches `Plan.none` the other way. `Font`
    * has ONE root, so the funnel promotes it — and `Sub` reaches `Font` with an argument-free
    * `extends`, so the withholding fixpoint takes the paramful promotion back. `nilaryPlan` cannot
    * promote `Font()` in its place either: its delegation argument is a call (not re-evaluable) and
    * the target uses `size` twice, so inlining it would evaluate `seed()` twice. */
  private val fontSrc = preamble +
    """public class Font {
      |  int size; String name;
      |  public Font()                      { this(seed(), "d"); }
      |  public Font(int size)              { this(size, "d"); }
      |  public Font(int size, String name) { this.size = size; this.name = name; grow(size); }
      |  static int seed() { return 12; }
      |  void grow(int by) { size = size + by; Gdx.graphics.gl20.clear(); }
      |  public void draw() { Gdx.graphics.gl20.clear(); }
      |}
      |public class Sub extends Font {
      |  public void redraw() { Gdx.graphics.gl20.clear(); }
      |}
      |""".stripMargin

  private def holder = ContextHolder(
    holder  = "demo.Gdx",
    context = ContextType.Injected("demo.Ctx"),
    members = Map("graphics" -> "graphics"),
    attach  = ContextAttach.Class,
  )

  private def run(source: String, phases: List[Phase]): (Program, TirEmitter, String) =
    val (after, log) = Pipeline.runTraced(SpoonTir.fromSource(source, "Clause.java"), phases)
    val emitter      = new TirEmitter(after, notes = log)
    (after, emitter, emitter.emit)

  private def threaded(source: String) = run(source, List(new GlobalsToImplicitsTransform(List(holder))))
  private def plain(source: String)    = run(source, Nil)

  // -------------------------------------------------------------------------
  // the clause reaches the class
  // -------------------------------------------------------------------------

  test("a `Plan.none` class carries the clause as its whole parameter list") {
    val (_, _, out) = threaded(bufferSrc)
    // `(using demo.Ctx)` and NOT `()(using demo.Ctx)`: an empty value group in front is a different
    // signature, and every `new Buffer(…)` call site would have to change.
    assert(clue(out).contains("class Buffer(using demo.Ctx)"), out)
    assert(!out.contains("class Buffer()(using"), out)
    // …and the body can now see it, which is the whole point (55 of the 57 measured errors).
    assert(out.contains("def bind(): scala.Unit"), out)
    assert(out.contains("scala.Predef.summon[demo.Ctx].graphics.gl20.clear()"), out)
  }

  test("…the NESTED class too — a walk over top-level classes would have missed it") {
    assert(clue(threaded(bufferSrc)._3).contains("class Nested(using demo.Ctx)"), threaded(bufferSrc)._3)
  }

  test("every secondary keeps the clause its delegation needs") {
    val out = threaded(bufferSrc)._3
    assert(clue(out).contains("def this(max: scala.Int)(using demo.Ctx)"), out)
    assert(out.contains("def this(isStatic: scala.Boolean, max: scala.Int)(using demo.Ctx)"), out)
  }

  test("nothing about SUPER changes: no argument is lifted and the omission census is identical") {
    List(bufferSrc, fontSrc).foreach { s =>
      val (a0, _, _) = plain(s)
      val (a1, _, _) = threaded(s)
      // the clause-bearing primary DELEGATES NOTHING — it hosts the clause and leaves the `extends`
      // clause, every secondary's delegation and every dropped `super(args)` exactly as they were.
      // So this is not the synthesis widened past its parent-agreement preconditions (CT5's
      // caution): the roots whose `super(args)` were already counted omissions still are.
      assertEquals(OmissionCheck.check(a1, a1.units).size, OmissionCheck.check(a0, a0.units).size, s)
    }
  }

  // -------------------------------------------------------------------------
  // E051 — the nilary constructor beside the clause-bearing primary
  // -------------------------------------------------------------------------

  test("the nilary constructor stays dropped — E120 + E051 is what emitting it costs") {
    val (_, _, out) = threaded(fontSrc)
    assert(clue(out).contains("class Font(using demo.Ctx)"), out)
    // `Font()` is a delegation and nothing else, so it has no place beside a primary carrying the
    // same clause: the two have the same erased signature.
    assert(!out.contains("def this()(using demo.Ctx)"), out)
    // …and that is exactly what the same class emits with NO clause: the drop is not new, and
    // reading `paramss.flatten` instead of the value parameters is what un-dropped it.
    assert(!plain(fontSrc)._3.contains("def this()"), plain(fontSrc)._3)
    // the subclass reaches it with an argument-free `extends` — the site the `E051` was reported at
    assert(clue(out).contains("class Sub(using demo.Ctx) extends demo.Font"), out)
  }

  /** …AND THE DROP IS NOT FREE: a delegation carrying arguments is a counted omission, not silent. */
  test("…and a delegation that CARRIES ARGUMENTS is a counted omission, never a silent drop") {
    val (after, _, out) = threaded(fontSrc)
    assert(!out.contains("def this()"), out)
    val found = OmissionCheck.droppedNilaryCtors(after, after.units)
    assertEquals(clue(found).map(f => f.owner -> f.what), List("demo.Font" -> "nilary constructor dropped"))
    assert(clue(found.head.detail).contains("2 argument(s)"), found.head.detail)
    // …and the PUBLISHED contract stops claiming it. `secondaries` is the emitted `def this` list,
    // so a dependent must not read `()` there for a constructor this module does not emit.
    assert(!clue(shapeOf(fontSrc, "demo.Font").secondaries).contains(Descriptor.empty),
           shapeOf(fontSrc, "demo.Font").secondaries.map(_.render).mkString(";"))
  }

  /** the OTHER half of the same predicate: a delegation that passes NOTHING is genuinely degenerate
    * — scala's implicit primary already is that constructor — so it is dropped and NOT counted. */
  test("a delegation that passes nothing is degenerate: dropped, and reported by nobody") {
    val nilSrc = preamble +
      """public class Empty {
        |  int n;
        |  public Empty()      { super(); }
        |  public Empty(int a) { n = a; }
        |}
        |""".stripMargin
    val (after, _, out) = plain(nilSrc)
    assert(!clue(out).contains("def this()\n"), out)
    assertEquals(OmissionCheck.droppedNilaryCtors(after, after.units), Nil)
  }

  private def shapeOf(source: String, fqn: String): Surface.TypeShape =
    val (_, e, _) = threaded(source)
    val ts        = e.emittedShapes.types
    ts.getOrElse(fqn, fail(s"no published shape for $fqn — ${ts.keys.mkString(", ")}"))

  // -------------------------------------------------------------------------
  // clause-conditional: with no clause, nothing moves
  // -------------------------------------------------------------------------

  test("no clause anywhere: the emitted text is BYTE-FOR-BYTE what it was, and nothing is recorded") {
    List(bufferSrc, fontSrc).foreach { s =>
      val (_, e0, out0) = plain(s)
      val (_, e1, out1) = run(s, List(new GlobalsToImplicitsTransform(Nil)))
      assertEquals(out1, out0)
      assertEquals(e0.contextClauseLosses, Nil)
      assertEquals(e1.contextClauseLosses, Nil)
    }
    // the pre-clause header: no parameter list at all on a `Plan.none` class
    assert(clue(plain(bufferSrc)._3).contains("class Buffer {"), plain(bufferSrc)._3)
    assert(clue(plain(fontSrc)._3).contains("class Font {"), plain(fontSrc)._3)
  }

  test("the emitter RECORDS no loss when the clause is carried — the check's other direction") {
    assertEquals(threaded(bufferSrc)._2.contextClauseLosses.map(_.fqn), Nil)
    assertEquals(threaded(fontSrc)._2.contextClauseLosses.map(_.fqn), Nil)
  }

  // -------------------------------------------------------------------------
  // the SILENT half, negative-tested: three shapes that cannot host a clause
  // -------------------------------------------------------------------------

  /** A clause on SOME constructors and not others. No single primary can carry one every secondary's
    * `this(...)` resolves against, so `CtorFunnel.classGivens` refuses — and the refusal is the
    * shape a check has to see, because the emitted class compiles wherever its body summons nothing.
    */
  test("a NON-UNIFORM clause is refused, and the refusal is COUNTED rather than silent") {
    val (_, e, out) = run(bufferSrc, List(CtorFunnelContextClauseSpec.Clause(Set("demo.Buffer"), firstOnly = true)))
    assert(clue(out).contains("class Buffer {"), out)
    assertEquals(clue(e.contextClauseLosses).map(l => l.fqn -> l.form), List("demo.Buffer" -> "class"))
  }

  test("a TRAIT cannot take one — scala's trait parameters are a different feature") {
    val traitSrc = preamble +
      """public interface Sized { default int width() { return 3; } }
        |public class Box implements Sized { }
        |""".stripMargin
    val (_, e, out) = run(traitSrc, List(CtorFunnelContextClauseSpec.Clause(Set("demo.Sized"))))
    assert(clue(out).contains("trait Sized"), out)
    assert(!out.contains("trait Sized(using"), out)
    assertEquals(clue(e.contextClauseLosses).map(l => l.fqn -> l.form), List("demo.Sized" -> "trait"))
  }

  test("an ENUM's clause is DROPPED from the parameter list, not emitted as `var : T`") {
    val enumSrc = preamble +
      """public enum Filter {
        |  NEAREST(1), LINEAR(2);
        |  public final int glEnum;
        |  Filter(int glEnum) { this.glEnum = glEnum; }
        |}
        |""".stripMargin
    val (_, e, out) = run(enumSrc, List(CtorFunnelContextClauseSpec.Clause(Set("demo.Filter"))))
    // the parameter is ANONYMOUS, so carrying it into the enum's promoted parameter list renders
    // `var : demo.Ctx`, which does not parse — and every CONSTANT would have to pass it. True of
    // both shapes: a `case object` and a scala 3 `enum` case reach the primary the same way
    // (`ENGINE-LIMITS.md` T21), and this enum takes the `enum` one.
    assert(!clue(out).contains("var : demo.Ctx"), out)
    // `val` and public: the parameter supersedes `public final int glEnum` and therefore ships at
    // that field's own modifiers (`EnumPromotedParamFlagsSpec`).
    assert(out.contains("enum Filter(val glEnum: scala.Int) extends java.lang.Enum[Filter]"), out)
    assertEquals(clue(e.contextClauseLosses).map(l => l.fqn -> l.form), List("demo.Filter" -> "enum"))
  }

  test("an all-static class that COLLAPSES to an object has no constructor to carry one") {
    val staticSrc = preamble +
      """public class Util {
        |  public static int twice(int a) { return a + a; }
        |}
        |""".stripMargin
    val (_, e, out) = run(staticSrc, List(CtorFunnelContextClauseSpec.Clause(Set("demo.Util"))))
    assert(clue(out).contains("object Util"), out)
    assertEquals(clue(e.contextClauseLosses).map(l => l.fqn -> l.form), List("demo.Util" -> "object"))
  }

  // -------------------------------------------------------------------------
  // the probe a real compiler reads (M2's lesson: a claim about scalac needs scalac)
  // -------------------------------------------------------------------------

  /** `class X(using T)` reached by `this()` from a secondary, by `new X(…)`, by an argument-free
    * `extends` and by a body `summon` is a claim about scala's overload resolution, and a string
    * assertion is not evidence for it. */
  test("emitted probe is written for a real compiler, ONE FILE PER UNIT as a port writes it") {
    probe("none-buffer", bufferSrc,
      """package demo
        |object ProbeMain {
        |  def main(args: Array[String]): Unit =
        |    val g = new Graphics; g.gl20 = new GL
        |    given Ctx = Ctx(g)
        |    println(new Buffer(3).n); println(new Buffer(true, 4).n); println(new Buffer(true, "xy").n)
        |    println(new Buffer.Nested(1, 2).m)
        |}
        |""".stripMargin)
    probe("none-font", fontSrc,
      """package demo
        |object ProbeMain {
        |  def main(args: Array[String]): Unit =
        |    val g = new Graphics; g.gl20 = new GL
        |    given Ctx = Ctx(g)
        |    println(new Font(7).size); println(new Font(7, "n").size); println(new Sub().size)
        |}
        |""".stripMargin)
  }

  private def probe(label: String, source: String, main: String): Unit =
    val phase      = new GlobalsToImplicitsTransform(List(holder))
    val (after, l) = Pipeline.runTraced(SpoonTir.fromSource(source, "Clause.java"), List(phase))
    val emitter    = new TirEmitter(after, notes = l)
    val dir = java.nio.file.Path
      .of(sys.props.getOrElse("balticporter.dumpProbe", s"${sys.props("user.dir")}/target/probe"),
          s"ctor-clause-$label")
    java.nio.file.Files.createDirectories(dir)
    // the INJECTED context type is the port's own hand-written Scala — the engine never saw it.
    java.nio.file.Files.writeString(dir.resolve("Ctx.scala"),
      "package demo\nfinal case class Ctx(graphics: Graphics)\nobject Ctx { var global: Ctx = null }\n")
    java.nio.file.Files.writeString(dir.resolve("ProbeMain.scala"), main)
    after.units.foreach { u =>
      val nm = after.symbolOf(u.symbol).map(_.name).getOrElse("Unit")
      java.nio.file.Files.writeString(dir.resolve(s"$nm.scala"), emitter.emitUnit(u))
    }
    println(s"[ctor-clause-probe] wrote ${dir.toAbsolutePath}")

object CtorFunnelContextClauseSpec:

  /** A context clause put on constructors DIRECTLY, with no closure and no policy — the shapes above
    * are ones a real holder's closure would refuse before reaching, and the question here is what the
    * CONSTRUCTOR REGION does with a clause, whoever attached it and however wrongly. It mints through
    * the phase's own `Minter`, so the parameter is anonymous and `isGiven` exactly as the threading's
    * is, and it mints a constructor for a type that has none, exactly as the threading does. */
  final class Clause(on: Set[String], firstOnly: Boolean = false) extends Phase:
    def name = "spec/clause"

    override def run(program: Program): Program =
      val mint   = new GlobalsToImplicitsTransform.Minter(program)
      val ctxSym = mint.selfTyped("Ctx", "demo.Ctx", Flags(isFinal = true))
      val ctxRef = TypeRepr.TypeRef(TypeRepr.NoPrefix, ctxSym)
      val edit = new Phase:
        def name = "spec/clause/edit"
        override def transformClassDef(t: Tree.ClassDef)(using p: Program): Tree.ClassDef =
          if !p.symbolOf(t.symbol).map(_.fullName).exists(on) then t
          else
            val ctors = t.body.collect { case d: Tree.DefDef if GlobalsToImplicitsTransform.isCtor(p, d.symbol) => d.symbol }
            if ctors.isEmpty then
              val fqn  = p.symbolOf(t.symbol).map(_.fullName).getOrElse("?")
              val ctor = mint.member("<init>", MemberKey(fqn, "<init>").render, t.symbol,
                                     TypeRepr.MethodType(Nil, TypeRepr.NoType), Flags())
              t.copy(body = Tree.DefDef(ctor, List(List(mint.usingParam(ctor, "demo.Ctx", ctxRef, t.origin))),
                TypeTree(TypeRepr.NoType, t.origin),
                Some(Tree.Block(Nil, Tree.Literal(Constant.UnitC, TypeRepr.NoType, t.origin),
                                TypeRepr.NoType, t.origin)), t.origin) :: t.body)
            else
              var seen = 0
              t.copy(body = t.body.map {
                case d: Tree.DefDef if ctors.contains(d.symbol) =>
                  seen += 1
                  if firstOnly && seen > 1 then d
                  else d.copy(paramss = d.paramss :+ List(mint.usingParam(d.symbol, "demo.Ctx", ctxRef, d.origin)))
                case s => s
              })
      val p1 = program.rebuilt(symbols = SymbolTable(program.symbols.all ++ mint.minted))
      given Program = p1
      p1.rebuilt(units   = p1.units.map(u => StandardTraversal.mapClassDef(edit, u)),
                 symbols = SymbolTable(p1.symbols.all ++ mint.minted))
