package balticporter.corpus

import balticporter.emit.TirEmitter
import balticporter.frontend.spoon.SpoonTir
import balticporter.tir.{Decision, DecisionLog, PorterNote, Pipeline, Program}
import balticporter.transform.*

/** globals → context END TO END — java in, emitted Scala out, through the same pipeline a port runs.
  *
  * The unit spec asserts the mechanism on a straight line; this asserts the ARTEFACT on the shapes
  * that broke the predecessor: an override component through an interface, an anonymous body, a
  * FIELD INITIALISER and a CLASS INITIALISER. It also writes a PROBE an operator can put a real
  * compiler over, because "an anonymous `(using T)` clause resolves across four emitted files" is a
  * claim about scalac and a string assertion is not evidence for it — and the cross-file coherence
  * class is exactly what only a compile catches.
  *
  * {{{ scala-cli compile --scala 3.8.4 --server=false <the path printed below> }}}
  */
class GlobalsToContextPortSpec extends munit.FunSuite:

  private val src =
    """package demo;
      |
      |public class Gdx {
      |  public static Graphics graphics;
      |  public static Files files;
      |}
      |
      |public class Graphics { public GL gl20; public int getWidth() { return 0; } }
      |public class GL { public void clear() {} }
      |public class Files { public String read(String n) { return n; } }
      |
      |public interface Renderer { void render(); }
      |
      |public class Basic implements Renderer {
      |  public void render() { Gdx.graphics.gl20.clear(); }
      |}
      |
      |public class Quiet implements Renderer {
      |  public void render() { }
      |}
      |
      |public class Loud extends Basic {
      |  public void render() { super.render(); }
      |}
      |
      |public class Scene {
      |  int w = Gdx.graphics.getWidth();
      |  void draw(Renderer r) { r.render(); }
      |}
      |
      |public class Boot {
      |  static String banner;
      |  static { banner = Gdx.files.read("banner"); }
      |  static String get() { return banner; }
      |}
      |
      |public class Listeners {
      |  void install() {
      |    Runnable x = new Runnable() { public void run() { Gdx.graphics.gl20.clear(); } };
      |    x.run();
      |  }
      |}
      |""".stripMargin

  private def base = ContextHolder(
    holder  = "demo.Gdx",
    context = ContextType.Injected("demo.Ctx"),
    members = Map("graphics" -> "graphics", "files" -> "files"),
  )

  /** A class with TWO constructors reaching ONE parent constructor — §8.2's SYNTHESISED primary,
    * which is the shape the constructor clause is hardest for and the one `ENGINE-LIMITS.md` CT4's
    * first cause lived in. Its own source rather than a fifth class in `src`, because in METHOD mode
    * only the constructor that READS would take a clause and the two roots would then disagree
    * about their signatures — a legitimate refusal, and noise in every assertion above. */
  private val synthSrc =
    """package demo;
      |public class Gdx { public static Graphics graphics; }
      |public class Graphics { public int getWidth() { return 0; } }
      |public class Widget {
      |  int w; boolean vis;
      |  Widget(int w, boolean vis) { this.w = w; this.vis = vis; }
      |}
      |public class Panel extends Widget {
      |  Panel()      { super(Gdx.graphics.getWidth(), true); }
      |  Panel(int w) { super(w, false); }
      |}
      |public class Deck extends Panel { }
      |""".stripMargin

  /** A CLASS INITIALISER THAT DOES BOTH THINGS — the shape that made the two kinds one, and the
    * only fixture in this file where they can be told apart.
    *
    * `Utils.<clinit>` READS a mapped static (`Gdx.files`) and CONSTRUCTS a class the closure threads
    * (`Ext`, whose constructor reads `Gdx.graphics`). One initialiser, one boundary, two seams — and
    * before they had two kinds the second one was filed as a residual READ whose classification told
    * its reader to re-spell a read that is not there. It is a real corpus shape, not a constructed
    * one: `com.crashinvaders.vfx.gl.VfxGLUtils`'s `<clinit>` is exactly this, two reads and one
    * `new DefaultVfxGlExtension()`. */
  private val bothSrc =
    """package demo;
      |public class Gdx { public static Graphics graphics; public static Files files; }
      |public class Graphics { public int getWidth() { return 0; } }
      |public class Files { public String read(String n) { return n; } }
      |public class Ext { int w; public Ext() { w = Gdx.graphics.getWidth(); } }
      |public class Utils {
      |  static String banner;
      |  static Ext ext;
      |  static { banner = Gdx.files.read("banner"); ext = new Ext(); }
      |}
      |""".stripMargin

  private def ported(h: ContextHolder): (GlobalsToImplicitsTransform, Program, DecisionLog, String) =
    portedFrom(src, h)

  private def portedFrom(source: String, h: ContextHolder): (GlobalsToImplicitsTransform, Program, DecisionLog, String) =
    val phase        = new GlobalsToImplicitsTransform(List(h))
    val (after, log) = Pipeline.runTraced(SpoonTir.fromSource(source, "Demo.java"), List(phase))
    (phase, after, log, new TirEmitter(after, notes = log).emit)

  /** the emitted CODE with the porter notes stripped: a note names the UPSTREAM member on purpose,
    * so a check that searches emitted text for an upstream name has to strip them first (§4.575). */
  private def code(out: String): String =
    out.linesIterator.filterNot(l => l.contains(PorterNote.Marker) || l.trim.startsWith("—")).mkString("\n")

  private lazy val (phase, after, log, out) = ported(base)

  // -------------------------------------------------------------------------
  // the override component — the direction the predecessor's call graph could not see
  // -------------------------------------------------------------------------

  test("ONE implementor reads, so the WHOLE component is threaded and `override` still matches") {
    val c = code(out)
    assert(clue(c).contains("def render()(using demo.Ctx)"), c)
    // the interface DECLARATION, the reading implementor, its sibling that reads nothing, and the
    // subclass below it — all four, or the emitted `override`s do not match.
    assertEquals(c.linesIterator.count(_.contains("def render()(using demo.Ctx)")), 4, c)
    assert(c.contains("override def render()(using demo.Ctx)"), c)
  }

  test("the caller THROUGH THE INTERFACE is threaded, and its call site is unchanged") {
    val c = code(out)
    assert(clue(c).contains("def draw(r: demo.Renderer)(using demo.Ctx)"), c)
    assert(c.contains("r.render()"), c)
  }

  test("a component member that never reads the holder says WHY it has the parameter") {
    val vias = log.of(Decision.Kind.RetypedSignature)
      .filter(_.subjectFqn == "demo.Quiet#render").flatMap(_.detail.get("via"))
    assertEquals(clue(vias), List("override-component"))
  }

  test("the two-hop PATH is one rewrite, not a member rename") {
    assert(clue(out).contains("scala.Predef.summon[demo.Ctx].graphics.gl20.clear()"), out)
  }

  // -------------------------------------------------------------------------
  // the two live silent mistranslations of the predecessor — each a NEGATIVE spec
  // -------------------------------------------------------------------------

  test("a `static { }` block NEVER receives a using clause it would then lose at emission") {
    // the predecessor seeded the synthetic `<clinit>` (it has a `MethodType`, so it passed the
    // is-a-method test), gave it `(using ctx)`, and the emitter then inlined only its BODY into the
    // companion — dropping the parameter and leaving `ctx` unresolved. Loud, and unattributable.
    val c = code(out)
    assert(!c.contains("<clinit>"), c)
    assert(clue(c).contains("locally {"), c)
    val clinitLine = c.linesIterator.dropWhile(!_.contains("locally {")).take(3).mkString("\n")
    assert(!clinitLine.contains("using"), clinitLine)
    assert(!clinitLine.contains("summon["), clinitLine)
  }

  test("a `static { }` read is a COUNTED seam, never a silent nothing") {
    val ss = phase.seams(after)
    val clinit = ss.filter(_.subject.contains("<clinit>"))
    assertEquals(clue(clinit).size, 1, ss.map(_.render).mkString("\n"))
    assertEquals(clinit.head.kind, ContextSeamCheck.Kind.ResidualGlobalRead)
    assert(clinit.head.detail.contains("class initialiser"), clinit.head.render)
    assert(ContextSeamCheck.Kind.classification(clinit.head.kind).contains("§1(b)"))
  }

  // -------------------------------------------------------------------------
  // …and the OTHER thing a boundary does: it USES something threaded
  // -------------------------------------------------------------------------

  private lazy val both = portedFrom(bothSrc, base.copy(attach = ContextAttach.Class))

  test("a boundary that CONSTRUCTS a threaded class is `unsuppliable-use`, not a residual READ") {
    // One `<clinit>`, two seams, two kinds — and the difference is whether the emitted file
    // compiles. The read half leaves a coherent program that kept a global; the construction half
    // leaves `new demo.Ext()` with no given anywhere in its scope, which is `No given` every time.
    // NEGATIVE: file `impose`'s `Site.Boundary` arm as `ResidualGlobalRead` again and both rows land
    // in one kind, whose classification opens *this read still reaches a global* about a site with
    // no read in it and offers `boundary = "residual-global"`, which re-spells reads and cannot
    // touch a construction (PROGRESS.md §10.8.9, and the shape ENGINE-LIMITS CT-era vfx carried).
    val (p, a, _, o) = both
    val clinit = p.seams(a).filter(_.subject.contains("<clinit>"))
    assertEquals(clue(clinit).map(_.kind).toSet,
                 Set(ContextSeamCheck.Kind.ResidualGlobalRead, ContextSeamCheck.Kind.UnsuppliableUse),
                 clinit.map(_.render).mkString("\n"))
    val use = clinit.filter(_.kind == ContextSeamCheck.Kind.UnsuppliableUse)
    assertEquals(clue(use).size, 1, clinit.map(_.render).mkString("\n"))
    // the EDGE is in the sentence: *constructs* and *calls* are two different things to go and look
    // at, and the edge kind is already in hand where the seam is filed.
    assert(clue(use.head.detail).contains("CONSTRUCTS `demo.Ext`"), use.head.render)
    assert(use.head.detail.contains("class initialiser"), use.head.render)
    // …and the emitted text really is the uncompilable half, or the assertion above is a string test
    // about a string.
    assert(clue(code(o)).contains("new demo.Ext()"), code(o))
    assert(clue(code(o)).contains("class Ext(using demo.Ctx)"), code(o))
  }

  test("its classification says IT DOES NOT COMPILE, and offers no re-spelling") {
    // §4.45: an error an agent cannot classify costs it a full investigation, and the wrong
    // classification costs it the investigation plus a wrong fix. The two sentences must not be
    // interchangeable.
    val c = ContextSeamCheck.Kind.classification(ContextSeamCheck.Kind.UnsuppliableUse)
    assert(clue(c).contains("§1(b)"), c)
    assert(c.contains("DOES NOT COMPILE"), c)
    assert(c.contains("`sites`"), c)
    assert(c.contains("selfSupplied"), c)
    // the one act that CANNOT help, named as such rather than left off the list
    assert(c.contains("answers a question this site never asked"), c)
    assert(ContextSeamCheck.Kind.classification(ContextSeamCheck.Kind.ResidualGlobalRead) != c)
  }

  test("NOTHING accepts an `unsuppliable-use` — an accept answers a question, not a build failure") {
    // CLAUDE.md §5's second screen, at the one kind where it decides. Every accept on every menu is
    // a port saying *I have read this site and the residue is right here*, which is a statement only
    // where the ENGINE declined to decide. Here the target compiler has already decided: an accept
    // would drain the row and leave the `No given` failing the build, with the arithmetic balanced.
    assert(!clue(ContextSeamCheck.remedies.map(_.kind).toSet)
      .contains(ContextSeamCheck.Kind.UnsuppliableUse.label))
  }

  test("a FIELD INITIALIZER's read is seeded, resolved and COUNTED — never dropped and left broken") {
    // the predecessor's seed test was `isMethod(usage.enclosing)`, and a field initialiser's
    // enclosing IS THE FIELD — so the read was dropped from the seeds, and `rewriteClass` visited
    // only `DefDef` arms, so the initialiser still named a member that was no longer static.
    val ss = phase.seams(after).filter(_.subject == "demo.Scene#w")
    assertEquals(clue(ss).size, 1, phase.seams(after).map(_.render).mkString("\n"))
    assert(ss.head.detail.contains("field initialiser"), ss.head.render)
    // and the read is INTACT — under `refuse` it still names the holder, which is a coherent
    // program, unlike naming a member that no longer exists.
    assert(clue(code(out)).contains("demo.Gdx.graphics.getWidth()"), out)
  }

  // -------------------------------------------------------------------------
  // capture, residual global, and the ambient given that must not come back
  // -------------------------------------------------------------------------

  test("an anonymous body CAPTURES: its own signature is untouched and the enclosing one carries it") {
    val c = code(out)
    assert(clue(c).contains("def install()(using demo.Ctx)"), c)
    assert(c.contains("def run(): scala.Unit"), c)   // the SAM's signature is what Runnable declares
    assert(!c.contains("def run()(using"), c)
    assertEquals(phase.seams(after).count(_.kind == ContextSeamCheck.Kind.CapturedContext), 1,
      phase.seams(after).map(_.render).mkString("\n"))
  }

  test("NO ambient given anywhere in the emitted output") {
    assert(!code(out).contains("given "), out)
  }

  test("`residual-global` rewrites the boundary read to the context companion, and counts it") {
    val (p, a, _, o) = ported(base.copy(boundary = ContextBoundary.ResidualGlobal))
    assert(clue(code(o)).contains("demo.Ctx.global.files.read"), o)
    assert(code(o).contains("demo.Ctx.global.graphics.getWidth()"), o)
    assert(p.seams(a).count(_.kind == ContextSeamCheck.Kind.ResidualGlobalRead) >= 2)
  }

  // -------------------------------------------------------------------------
  // per-site policy: the one EAGER→LAZY change, and it is never a default
  // -------------------------------------------------------------------------

  test("`lazy-init` defers the static, threads its readers, and records a DeferredInit decision") {
    val (p, a, l, o) = ported(base.copy(sites = Map("demo.Boot#<clinit>" -> ContextSite.LazyInit)))
    val c = code(o)
    assert(clue(c).contains("def banner(using demo.Ctx): java.lang.String"), c)
    assert(c.contains("banner$set"), c)
    assert(c.contains("banner$value"), c)
    // the class initialiser is GONE — every statement it had moved
    assert(!c.contains("locally {"), c)
    // its READER is threaded by the Use edge: a parameterless `def` is read as the field was.
    assert(c.contains("def get()(using demo.Ctx): java.lang.String"), c)

    val ds = l.of(Decision.Kind.DeferredInit)
    assertEquals(clue(ds).map(_.subjectFqn), List("demo.Boot#banner"))
    assertEquals(ds.head.reason, balticporter.tir.Reason.Configured("globals->implicits", "demo.Boot#<clinit>"))
    // …and the `sites` key appears ONCE in the note: the §1 classification carries it, so repeating
    // it in the detail renders `key=` twice in one comment.
    assertEquals(clue(o).sliding("key=demo.Boot".length).count(_ == "key=demo.Boot"), 1, o)
    // …and the note is BESIDE the declaration, where the question is asked (§4.575).
    assert(clue(o).contains("/* porter: deferred-init"), o)
    assert(o.contains("key=demo.Boot#<clinit>"), o)
    assertEquals(p.seams(a).count(_.kind == ContextSeamCheck.Kind.DeferredInit), 1)
  }

  test("eager→lazy is NOT a default: without the site policy nothing is deferred") {
    assertEquals(log.of(Decision.Kind.DeferredInit), Nil)
    assertEquals(phase.seams(after).count(_.kind == ContextSeamCheck.Kind.DeferredInit), 0)
  }

  // -------------------------------------------------------------------------
  // the anchored component: refused whole, counted, and no broken `override`
  // -------------------------------------------------------------------------

  test("a component anchored on an UNPARSED parent is refused whole and counted") {
    val anchored =
      """package demo;
        |public class Gdx { public static Graphics graphics; }
        |public class Graphics { public int getWidth() { return 0; } }
        |public class Widget extends javax.swing.JComponent {
        |  public void paint(java.awt.Graphics g) { int w = Gdx.graphics.getWidth(); }
        |}
        |""".stripMargin
    val phase = new GlobalsToImplicitsTransform(List(ContextHolder(
      holder = "demo.Gdx", context = ContextType.Injected("demo.Ctx"),
      members = Map("graphics" -> "graphics"))))
    val (a, l) = Pipeline.runTraced(SpoonTir.fromSource(anchored, "Anchored.java"), List(phase))
    val o = new TirEmitter(a, notes = l).emit
    val frozen = phase.seams(a).filter(_.kind == ContextSeamCheck.Kind.FrozenComponent)
    assert(clue(frozen).nonEmpty, phase.seams(a).map(_.render).mkString("\n"))
    assert(frozen.exists(_.subject == "demo.Widget#paint"), frozen.map(_.render).mkString("\n"))
    // NOT threaded — a clause here would not match the parent's declaration.
    assert(!code(o).contains("def paint(g: java.awt.Graphics)(using"), o)
    // and the refusal is a §1(b) finding an agent can act on, not silence.
    assert(clue(phase.policyReport.findings).exists(_.detail.contains("cannot take a context clause")))
  }

  // -------------------------------------------------------------------------
  // class attachment
  // -------------------------------------------------------------------------

  test("`attach = class` EMITS — the refusal is gone, and nothing is reported in its place") {
    // This spec was the REFUSAL's spec: `attach = "class"` recorded a counted `Unverifiable`
    // finding because the constructor funnel undid the clause three ways (ENGINE-LIMITS CT4, 5
    // scalac errors on this fixture). All three were in the constructor region DESIGN.md §8.2 owns
    // and all three are closed there — the plan models parameter GROUPS, the funnel's "is this
    // nilary" questions read the VALUE parameters, and the emitter renders the clause through
    // `paramClause`. The finding therefore has to be gone, not merely quieter.
    val (p, _, _, _) = ported(base.copy(attach = ContextAttach.Class))
    assertEquals(clue(p.policyReport.findings.filter(_.setting.endsWith(".attach"))), Nil,
      p.policyReport.render)
    assertEquals(phase.policyReport.findings.count(_.setting.endsWith(".attach")), 0)
  }

  test("`attach = class` puts the clause on the CONSTRUCTORS, not on the instance methods") {
    val (p, a, l, o) = ported(base.copy(attach = ContextAttach.Class))
    val c = code(o)
    // the clause is the class's PARAMETER LIST, as a `using` GROUP — not an ordinary parameter,
    // which is what a flattened plan emitted (`class Scene($p: demo.Ctx)`) and what left every
    // `summon` in the body unresolved.
    assert(clue(c).contains("class Scene(using demo.Ctx)"), c)
    assert(clue(c).contains("class Basic(using demo.Ctx)"), c)
    assert(!c.contains("$p: demo.Ctx"), c)
    // …and NOT on the instance methods, which is the whole argument for class attachment: 275
    // threaded declarations against 2,497, and `frozen-component` 32 -> 0 (PROGRESS §11.12).
    assert(!c.contains("def render()(using"), c)
    // a SUBCLASS of a threaded class takes the clause too, or its own `extends` has nothing to pass
    assert(clue(c).contains("class Loud(using demo.Ctx)"), c)
    // the field initialiser is no longer a boundary: the class's constructor carries the context.
    assertEquals(p.seams(a).count(_.subject == "demo.Scene#w"), 0, p.seams(a).map(_.render).mkString("\n"))
    val classRows = l.of(Decision.Kind.RetypedSignature)
      .filter(_.detail.get("to").exists(_.contains("constructors"))).map(_.subjectFqn).toSet
    assert(clue(classRows).contains("demo.Basic"), classRows.toString)
  }

  /** CT4's FIRST cause, end to end: a constructor that has gained a clause is not java's nilary one,
    * and reading it as paramful is what made the funnel decline the promotion and emit a synthetic
    * nilary primary beside it — a class body with no given in scope anywhere. The class here needs a
    * SYNTHESISED primary (two roots, one parent constructor), so the clause has to survive both the
    * nomination and the emission, and every secondary's `this(...)` has to still resolve. */
  test("a SYNTHESISED primary carries the clause as its own GROUP, and the secondaries reach it") {
    val holder = ContextHolder(holder = "demo.Gdx", context = ContextType.Injected("demo.Ctx"),
                               members = Map("graphics" -> "graphics"), attach = ContextAttach.Class)
    val (_, _, _, o) = portedFrom(synthSrc, holder)
    val c = code(o)
    assert(clue(c).contains(
      "class Panel protected (sup$0: scala.Int, sup$1: scala.Boolean)(using demo.Ctx) extends demo.Widget(sup$0, sup$1)"), c)
    // both java constructors survive as secondaries, each carrying the clause its delegation needs
    assert(clue(c).contains("def this()(using demo.Ctx)"), c)
    assert(clue(c).contains("def this(w: scala.Int)(using demo.Ctx)"), c)
    // and the parent, which reads nothing, is untouched — the closure threads what needs it
    assert(clue(c).contains("class Widget private[demo] (") || clue(c).contains("class Widget protected ("), c)
    assert(!c.contains("class Widget(using"), c)
    // a SUBCLASS reaches the synthesised primary's class argument-free, so it needs the clause too
    assert(clue(c).contains("class Deck(using demo.Ctx)"), c)
  }

  test("a TRAIT whose body needs the context is refused unless `promoteToClass` names it") {
    val traitSrc =
      """package demo;
        |public class Gdx { public static Graphics graphics; }
        |public class Graphics { public int getWidth() { return 0; } }
        |public interface Sized { default int width() { return Gdx.graphics.getWidth(); } }
        |public class Box implements Sized { }
        |""".stripMargin
    def run(promote: Set[String]) =
      val ph = new GlobalsToImplicitsTransform(List(ContextHolder(
        holder = "demo.Gdx", context = ContextType.Injected("demo.Ctx"),
        members = Map("graphics" -> "graphics"), attach = ContextAttach.Class,
        promoteToClass = promote)))
      val (a, l) = Pipeline.runTraced(SpoonTir.fromSource(traitSrc, "Trait.java"), List(ph))
      (ph, a, new TirEmitter(a, notes = l).emit)

    val (ph0, a0, _) = run(Set.empty)
    val refused = ph0.seams(a0).filter(_.kind == ContextSeamCheck.Kind.FrozenComponent)
    assert(clue(refused).exists(_.subject == "demo.Sized"), ph0.seams(a0).map(_.render).mkString("\n"))
    assert(refused.head.detail.contains("promoteToClass"), refused.head.render)

    val (ph1, a1, o1) = run(Set("demo.Sized"))
    assert(clue(code(o1)).contains("abstract class Sized"), o1)
    assertEquals(ph1.seams(a1).count(f =>
      f.kind == ContextSeamCheck.Kind.FrozenComponent && f.subject == "demo.Sized"), 0)
  }

  // -------------------------------------------------------------------------
  // the probe a real compiler reads
  // -------------------------------------------------------------------------

  test("emitted probe is written for a real compiler, ONE FILE PER UNIT as a port writes it") {
    probe("method", base.copy(boundary = ContextBoundary.ResidualGlobal,
                              sites = Map("demo.Boot#<clinit>" -> ContextSite.LazyInit)))
  }

  test("…and the CLASS-mode probe too — it used to be the measurement behind a refusal") {
    // While `attach = "class"` did not emit, writing this probe would have left an uncompilable
    // directory in `target/` that reads as a regression, so only the method-mode one was written
    // and the 5 errors were reproduced by hand. Now that the funnel carries the clause the probe is
    // the evidence, not the symptom: an anonymous `(using T)` resolving through a SYNTHESISED
    // primary, a subclass's `extends`, a field initialiser and an anonymous body across ten emitted
    // files is a claim about scalac, and a string assertion is not evidence for it (M2's lesson).
    probe("class", base.copy(attach = ContextAttach.Class))
    // …and the SYNTHESISED-primary shape beside it, in its own directory: a `protected (…)(using T)`
    // primary reached by two secondaries and by a subclass's `extends` is the part of the encoding
    // that no string assertion settles.
    probe("class-synth", ContextHolder(holder = "demo.Gdx", context = ContextType.Injected("demo.Ctx"),
      members = Map("graphics" -> "graphics"), attach = ContextAttach.Class), synthSrc,
      // …plus a MAIN that constructs through both secondaries and through the subclass, so the probe
      // proves the primary is REACHED and not merely declared.
      """package demo
        |final case class Ctx(graphics: Graphics)
        |object Ctx { var global: Ctx = null }
        |object ProbeMain {
        |  def main(args: Array[String]): Unit =
        |    given Ctx = Ctx(new Graphics)
        |    println(new Panel().w); println(new Panel(3).w); println(new Deck().vis)
        |}
        |""".stripMargin)
  }

  private def probe(label: String, h: ContextHolder, source: String = src,
                    ctx: String =
                      """package demo
                        |final case class Ctx(graphics: Graphics, files: Files)
                        |object Ctx { var global: Ctx = null }
                        |""".stripMargin): Unit =
    val phase      = new GlobalsToImplicitsTransform(List(h))
    val (after, l) = Pipeline.runTraced(SpoonTir.fromSource(source, "Demo.java"), List(phase))
    val emitter    = new TirEmitter(after, notes = l)
    val dir = _root_.java.nio.file.Path
      .of(sys.props.getOrElse("balticporter.dumpProbe", s"${sys.props("user.dir")}/target/probe"),
          s"globals-$label")
    _root_.java.nio.file.Files.createDirectories(dir)
    // the INJECTED context type is the port's own hand-written Scala — the engine never saw it, so
    // the probe supplies it exactly as a port would (§8.4: `inject` is where the ergonomics live).
    _root_.java.nio.file.Files.writeString(dir.resolve("Ctx.scala"), ctx)
    // ONE FILE PER UNIT, because that is the layout a port writes (§5.5) and because the whole-
    // program `emit` concatenates ten `package demo` clauses into one file, which is not Scala.
    after.units.foreach { u =>
      val nm = after.symbolOf(u.symbol).map(_.name).getOrElse("Unit")
      _root_.java.nio.file.Files.writeString(dir.resolve(s"$nm.scala"), emitter.emitUnit(u))
    }
    println(s"[globals-context-probe] wrote ${dir.toAbsolutePath}")
