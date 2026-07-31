package balticporter.corpus

import balticporter.emit.TirEmitter
import balticporter.frontend.spoon.SpoonTir
import balticporter.tir.{Decision, Pipeline, Reason, RuleScope}
import balticporter.transform.*

/** globals → context, at the MECHANISM level: the straight-line closure the predecessor pinned, kept
  * as this replacement's regression floor, plus the two things the replacement REVERSED.
  *
  * The predecessor's spec asserted a named `ctx` parameter and a `given ConfigCtx: Config = new
  * Config` in the companion. Both are gone on purpose (DESIGN.md §8.4): a named context parameter
  * shadows an emitted root package, and an ambient given makes every seam silent. What stays true is
  * the shape that made the mechanism worth having — a method that reads the holder is threaded, its
  * caller is threaded, and the CALL SITE does not change at all.
  */
class GlobalsToImplicitsTransformSpec extends munit.FunSuite:

  private val src =
    """package demo;
      |class Config {
      |  static int verbosity = 0;
      |  static boolean enabled() { return verbosity > 0; }
      |}
      |class Logger {
      |  void log(String msg) { if (Config.verbosity > 0) System.out.println(msg); }
      |}
      |class App {
      |  void run(Logger l, String m) { l.log(m); }
      |}
      |""".stripMargin

  private def holder(f: ContextHolder => ContextHolder = identity) = f(ContextHolder(
    holder  = "demo.Config",
    context = ContextType.Minted("demo.Ctx"),
    members = Map("verbosity" -> "verbosity"),
  ))

  private def ported(h: ContextHolder) =
    val phase        = new GlobalsToImplicitsTransform(List(h))
    val (after, log) = Pipeline.runTraced(SpoonTir.fromSource(src), List(phase))
    (phase, after, log, new TirEmitter(after, notes = log).emit)

  private lazy val (phase, after, log, out) = ported(holder())

  test("threads the closure: the reader, and the caller of the reader") {
    // cross-unit type references are fully qualified (FQN emission, no imports).
    assert(clue(out).contains("def log(msg: java.lang.String)(using demo.Ctx)"), out)
    assert(out.contains("def run(l: demo.Logger, m: java.lang.String)(using demo.Ctx)"), out)
    // …and the CALL SITE is unchanged: the argument comes from the `using` in scope, which is the
    // whole reason `using` was chosen over an explicit parameter.
    assert(out.contains("l.log(m)"), out)
  }

  test("the read is an anonymous clause plus a SUMMON — never a named context parameter") {
    assert(clue(out).contains("scala.Predef.summon[demo.Ctx].verbosity"), out)
    // a parameter named after an emitted root package shadows it and breaks every qualified
    // reference in scope; this backend emits nothing but qualified references (§6).
    assert(!out.contains("(using ctx:"), out)
    assert(!out.matches("(?s).*\\(using [A-Za-z_][A-Za-z0-9_]*: demo\\.Ctx\\).*"), out)
  }

  test("NO ambient given is emitted anywhere — the load-bearing reversal") {
    assert(!out.contains("given "), out)
    assert(!out.contains("Ctx: demo.Ctx = new"), out)
  }

  test("every threaded DECLARATION leaves a §1(b) row, and no call site does") {
    val ds = log.of(Decision.Kind.RetypedSignature)
    assert(clue(ds).nonEmpty)
    assert(ds.forall(_.reason == Reason.Configured("globals->implicits", "demo.Config")))
    val fqns = ds.map(_.subjectFqn).toSet
    assert(clue(fqns).contains("demo.Logger#log"))
    assert(fqns.contains("demo.App#run"))
    assert(ds.forall(_.detail.contains("key")))
  }

  test("the residual holder is DERIVED: a static with no reader left is dropped") {
    val dropped = log.of(Decision.Kind.DroppedMember).map(_.subjectFqn)
    assertEquals(clue(dropped), List("demo.Config#verbosity"))
    // exactly ONE `verbosity` declaration survives, and it is the MINTED context's member — the
    // holder's own static is gone because nothing reads it any more.
    assertEquals(clue(out).linesIterator.count(_.contains("var verbosity")), 1, out)
    assert(out.split("final class Ctx").head.linesIterator.forall(!_.contains("var verbosity")), out)
    // the note for a member that is NOT there heads the owning type's body.
    assert(out.contains("/* porter: dropped-member"), out)
  }

  test("EMPTY holders leaves the port byte-identical, with no decisions and no seams") {
    val plain        = new TirEmitter(SpoonTir.fromSource(src)).emit
    val phase        = new GlobalsToImplicitsTransform()
    val (after, log) = Pipeline.runTraced(SpoonTir.fromSource(src), List(phase))
    assertEquals(new TirEmitter(after).emit, plain)
    assertEquals(log.all, Nil)
    assertEquals(phase.seams(after), Nil)
    assertEquals(phase.policyReport.findings, Nil)
  }

  test("a holder FQN naming nothing reports through the binder and rewrites nothing") {
    val (p, _, l, o) = ported(holder(_.copy(holder = "demo.NoSuchHolder")))
    assert(clue(p.policyReport.findings).nonEmpty)
    assert(p.policyReport.findings.exists(_.key == "demo.NoSuchHolder"))
    assertEquals(l.all, Nil)
    assert(!o.contains("using"), o)
  }

  test("a member key naming no static on the holder reports, and the rest still fires") {
    val (p, _, _, o) = ported(holder(h => h.copy(members = h.members + ("nope" -> "nope"))))
    assert(clue(p.policyReport.findings).exists(_.key == "demo.Config#nope"))
    assert(o.contains("(using demo.Ctx)"), o)
  }

  test("an EMPTY member map is refused as the silent no-op it would be") {
    val (p, _, l, _) = ported(holder(_.copy(members = Map.empty)))
    assert(clue(p.policyReport.findings).exists(_.detail.contains("silent no-op")))
    assertEquals(l.all, Nil)
  }

  test("a MINTED context refuses a two-hop path — there is no intermediate type to mint") {
    val (p, _, _, _) = ported(holder(h => h.copy(members = Map("verbosity" -> "a.b"))))
    assert(clue(p.policyReport.findings).exists(_.detail.contains("two-hop")))
  }

  test("a SCOPED-OUT declaration keeps the global, and says so") {
    val (_, _, l, o) = ported(holder(_.copy(scope = RuleScope.Everywhere(Set("demo.Logger")))))
    val scoped = l.of(Decision.Kind.ScopedOut).map(_.subjectFqn)
    assertEquals(clue(scoped), List("demo.Logger#log"))
    assert(o.contains("demo.Config.verbosity"), o)
    assert(!o.contains("def log(msg: java.lang.String)(using"), o)
  }

  test("the closure's EDGES are a value, not only their result") {
    val need = edgesOf(holder())
    assert(clue(need).exists(e => e.kind == ContextNeed.Edge.Kind.Seed))
    assert(need.exists(e => e.kind == ContextNeed.Edge.Kind.Use))
  }

  private def edgesOf(h: ContextHolder): List[ContextNeed.Edge] =
    val program = SpoonTir.fromSource(src)
    val phase   = new GlobalsToImplicitsTransform(List(h))
    phase.bindPolicy(new balticporter.tir.PolicyBinder(program, program.members))
    // the phase builds its own `ContextNeed`; this reproduces it over the same inputs so a spec can
    // pin the derivation rather than only the emitted text.
    val statics = program.symbols.all.filter(s => s.fullName == "demo.Config#verbosity").map(_.id).toSet
    val need = new ContextNeed(program, balticporter.tir.OverrideGraph.build(program), h,
      statics.map(_ -> "verbosity").toMap, Set.empty, (_, _, _, _, _, _) => (), (_, _) => ())
    need.grow()
    need.edges
