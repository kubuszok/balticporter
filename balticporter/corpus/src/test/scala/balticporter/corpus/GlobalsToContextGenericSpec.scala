package balticporter.corpus

import balticporter.emit.TirEmitter
import balticporter.frontend.spoon.SpoonTir
import balticporter.tir.{Decision, DecisionLog, PorterNote, Pipeline, Program, UsageKind}
import balticporter.transform.*

/** `ENGINE-LIMITS.md` CT6 — the two faces of the same blindness, and the fixture that dumped them.
 *
 * Face A: `Xref.walkType`'s `AppliedType` arm re-labels the kind it was called with, so
 * `walkType(tpt.tpe, Instantiate, n)` at a `Tree.New` reaches a GENERIC class as `Tycon` — for
 * `new Cell<String>()` and for a RAW `new Cell()` alike. DESIGN.md §8.4's instantiate edge was
 * therefore absent for every generic class: no threading, no `impose`, and so NO SEAM, which is a
 * boundary the engine cannot see rather than one it refuses. The same relabelling left an anonymous
 * subclass of a GENERIC parent with no lexical home, so a capture inside it climbed to the enclosing
 * CLASS.
 *
 * Face B: the seam's own diagnostic says *give the site a `sites` policy*, and for the shape that
 * most needs one — a static initialiser that CONSTRUCTS a now-threaded type — there was no such
 * policy: the deferral was derived from reads of a MAPPED STATIC, and that initialiser reads none.
 * Measured on a real port, both keys BOUND, both did nothing, and the emitted output was
 * byte-identical with them and without them.
 *
 * Every assertion here is negative-testable: revert the guard named in its comment and it fails.
 */
class GlobalsToContextGenericSpec extends munit.FunSuite:

  /** The five shapes CT6 needs, side by side — a GENERIC and a NON-GENERIC class with the same
    * constructor, each constructed from a static field initialiser, plus the type-argument slot that
    * must NOT read as a construction. */
  private val src =
    """package demo;
      |
      |public class Cfg { public static Svc svc; }
      |public class Svc { public int width() { return 0; } }
      |
      |public class Plain { int w; Plain() { w = Cfg.svc.width(); } }
      |public class Cell<T> { int w; Cell() { w = Cfg.svc.width(); } }
      |
      |public class Pool<T> { public T obtain() { return null; } }
      |
      |public class Named { static Plain p = new Plain(); }
      |
      |public class Tbl {
      |  static Pool<Cell> cellPool = new Pool<Cell>() { public Cell obtain() { return new Cell(); } };
      |}
      |
      |public class Sized { static Pool<Cell> mine = new Pool<Cell>(); }
      |""".stripMargin

  /** an anonymous subclass of a GENERIC parent written inside a METHOD, whose body reads the holder
    * — CT1's shape, for generics, from the other side of the same table. */
  private val anonSrc =
    """package demo;
      |public class Cfg { public static Svc svc; }
      |public class Svc { public int width() { return 0; } }
      |public class Reg<T> { public void install(T t) {} }
      |public class Boot {
      |  void go() {
      |    Reg<String> r = new Reg<String>() { public void install(String t) { int w = Cfg.svc.width(); } };
      |    r.install("x");
      |  }
      |}
      |""".stripMargin

  private def base = ContextHolder(
    holder  = "demo.Cfg",
    context = ContextType.Injected("demo.Ctx"),
    members = Map("svc" -> "svc"),
    attach  = ContextAttach.Class,
  )

  private def portedFrom(source: String, h: ContextHolder)
      : (GlobalsToImplicitsTransform, Program, DecisionLog, String) =
    val phase        = new GlobalsToImplicitsTransform(List(h))
    val (after, log) = Pipeline.runTraced(SpoonTir.fromSource(source, "Generic.java"), List(phase))
    (phase, after, log, new TirEmitter(after, notes = log).emit)

  private def ported(h: ContextHolder) = portedFrom(src, h)

  /** the emitted CODE with the porter notes stripped — a note names the UPSTREAM member on purpose
    * (§4.575). */
  private def code(out: String): String =
    out.linesIterator.filterNot(l => l.contains(PorterNote.Marker) || l.trim.startsWith("—")).mkString("\n")

  private lazy val (phase, after, log, out) = ported(base)

  private def seams(p: GlobalsToImplicitsTransform, a: Program) = p.seams(a)
  private def render(p: GlobalsToImplicitsTransform, a: Program) = p.seams(a).map(_.render).mkString("\n")

  // -------------------------------------------------------------------------
  // the index really does under-label — the fact both faces follow from
  // -------------------------------------------------------------------------

  test("the shared index labels a GENERIC `new` `Tycon` and a non-generic one `Instantiate`") {
    // This is not a wish: it is the state of `Xref` that CT6 says stays as it is, because
    // `UsageKind` is read by the portability check, the rewrite trace and the external-surface walk
    // and re-labelling one arm is its own thirteen-port measure cycle. The phase compensates; the
    // index does not change. If this test ever fails because the arm was re-labelled, the two
    // widenings below become redundant rather than wrong.
    val p    = SpoonTir.fromSource(src, "Generic.java")
    def kindsAtNew(fqn: String) =
      p.symbols.all.filter(_.fullName == fqn).flatMap(s => p.usages(s.id)).collect {
        case u if u.site.isInstanceOf[balticporter.tir.Tree.New] => u.kind
      }.toSet
    assertEquals(clue(kindsAtNew("demo.Plain")), Set(UsageKind.Instantiate))
    assertEquals(clue(kindsAtNew("demo.Cell")), Set(UsageKind.Tycon))
  }

  // -------------------------------------------------------------------------
  // face A — the instantiate edge, and the seam that was missing entirely
  // -------------------------------------------------------------------------

  test("a GENERIC class's `new` imposes the need, exactly as a non-generic one does") {
    // the CONTROL and the case, in one assertion pair: both static field initialisers construct a
    // threaded class, and before CT6 only the non-generic one was a counted boundary.
    // NEGATIVE: make `ContextNeed.instantiates` read `u.kind == UsageKind.Instantiate` only, and the
    // `demo.Tbl#cellPool` row disappears while `demo.Named#p` stays.
    //
    // Both rows are `unsuppliable-use` and NOT `residual-global-read`: neither initialiser reads a
    // mapped static at all, they CONSTRUCT threaded classes, and that is the whole of the split
    // (PROGRESS.md §10.8.9). This fixture is CT6's, so it is also the corpus's oldest witness that
    // the two were being counted as one.
    val subjects = seams(phase, after)
      .filter(_.kind == ContextSeamCheck.Kind.UnsuppliableUse).map(_.subject).toSet
    assert(clue(subjects).contains("demo.Named#p"), render(phase, after))
    assert(clue(subjects).contains("demo.Tbl#cellPool"), render(phase, after))
  }

  test("…and the seam says the boundary is a STATIC INITIALISER, with the `sites` exit named") {
    val f = seams(phase, after).find(_.subject == "demo.Tbl#cellPool").get
    assert(clue(f.detail).contains("class initialisation"), f.render)
    // the DETAIL is about this site; the EXITS are in the classification, which the grouped summary
    // prints once per kind rather than once per row — a per-row copy of the same advice is what the
    // reader skips.
    assert(clue(ContextSeamCheck.Kind.classification(f.kind)).contains("`sites` policy"), f.render)
    assert(ContextSeamCheck.Kind.classification(f.kind).contains("§1(b)"))
  }

  test("a TYPE ARGUMENT at a `new` is not a construction — `new Pool[Cell]` constructs no `Cell`") {
    // the precision the kind-blind widening would have lost. `Cell` is named at that `New` node as a
    // `TypeArg`, and `Sized#mine` constructs a `Pool` and nothing else.
    // NEGATIVE: relax `instantiates` to `case _: Tree.New => true` and this gains a seam.
    assertEquals(clue(seams(phase, after).count(_.subject == "demo.Sized#mine")), 0,
      render(phase, after))
  }

  test("an anonymous subclass of a GENERIC parent has its LEXICAL HOME, not the enclosing class") {
    // Without the `anonHome` widening the climb from the anon body falls back to `sym.owner` — the
    // enclosing CLASS — so `demo.Tbl` is threaded and the boundary is never reached at all.
    // NEGATIVE: restore `case Usage(UsageKind.Instantiate, n: Tree.New, …)` in `anonHome` and
    // `class Tbl(using demo.Ctx)` appears while the `demo.Tbl#cellPool` seam vanishes.
    val c = code(out)
    assert(clue(c).contains("class Cell"), c)
    assert(!c.contains("class Tbl(using"), c)
    assertEquals(clue(log.of(Decision.Kind.RetypedSignature).map(_.subjectFqn).toSet.contains("demo.Tbl")),
      false, c)
  }

  test("a capture inside an anonymous subclass of a GENERIC parent lands on the ENCLOSING METHOD") {
    // the same widening under `attach = method`, where getting it wrong is loud: the owner fallback
    // resolves to a CLASS, which is a boundary in that mode, so the read stayed global and the
    // enclosing method — the one that can actually supply the context — was never asked.
    val (p, a, _, o) = portedFrom(anonSrc, base.copy(attach = ContextAttach.Method))
    val c = code(o)
    assert(clue(c).contains("def go()(using demo.Ctx)"), c)
    // the SAM body's own signature is what its parent declares, and it is untouched
    assert(!c.contains("def install(t: java.lang.String)(using"), c)
    assertEquals(p.seams(a).count(_.kind == ContextSeamCheck.Kind.CapturedContext), 1, render(p, a))
    assert(clue(c).contains("scala.Predef.summon[demo.Ctx].svc.width()"), c)
  }

  // -------------------------------------------------------------------------
  // face B — a deferral trigger the `sites` policy can actually name
  // -------------------------------------------------------------------------

  test("`lazy-init` reaches a static field that CONSTRUCTS a threaded type — the exit the seam names") {
    // NEGATIVE: restore `planDeferral`'s `readsHolder(rhs)` filter (or its read-derived candidate
    // set) and this emits nothing at all — which is precisely what was measured on a real port:
    // the key BINDS, the output is byte-identical, and `policy` stays at its floor.
    val (p, a, l, o) = ported(base.copy(sites = Map("demo.Tbl#cellPool" -> ContextSite.LazyInit)))
    val c = code(o)
    assert(clue(c).contains("def cellPool(using demo.Ctx)"), c)
    assert(c.contains("cellPool$set"), c)
    assert(c.contains("cellPool$value"), c)

    val ds = l.of(Decision.Kind.DeferredInit)
    assertEquals(clue(ds).map(_.subjectFqn), List("demo.Tbl#cellPool"))
    assertEquals(ds.head.reason, balticporter.tir.Reason.Configured("globals->implicits", "demo.Tbl#cellPool"))
    assertEquals(p.seams(a).count(_.kind == ContextSeamCheck.Kind.DeferredInit), 1, render(p, a))
    // …and the porter note is beside the declaration, where the question is asked (§4.575)
    assert(clue(o).contains("/* porter: deferred-init"), o)
  }

  test("…and the boundary it was the exit FOR is gone: no unsuppliable-use row for that field") {
    val (p, a, _, _) = ported(base.copy(sites = Map("demo.Tbl#cellPool" -> ContextSite.LazyInit)))
    assertEquals(clue(p.seams(a).count(f =>
      f.kind == ContextSeamCheck.Kind.UnsuppliableUse && f.subject == "demo.Tbl#cellPool")), 0,
      render(p, a))
    // the OTHER boundary is untouched — a per-site policy decides one site
    assert(p.seams(a).exists(f =>
      f.kind == ContextSeamCheck.Kind.UnsuppliableUse && f.subject == "demo.Named#p"), render(p, a))
  }

  test("the read-derived trigger still fires: a class initialiser that READS the holder") {
    // the pre-CT6 path, kept — the widening adds a candidate set, it does not replace one. A
    // `<clinit>` is an engine-minted member the binder refuses to bind, so nothing but the read
    // derivation can reach it.
    val clinitSrc =
      """package demo;
        |public class Cfg { public static Svc svc; }
        |public class Svc { public int width() { return 0; } }
        |public class Boot {
        |  static int w;
        |  static { w = Cfg.svc.width(); }
        |  static int get() { return w; }
        |}
        |""".stripMargin
    val (p, a, l, o) = portedFrom(clinitSrc, base.copy(sites = Map("demo.Boot#<clinit>" -> ContextSite.LazyInit)))
    assert(clue(code(o)).contains("def w(using demo.Ctx)"), o)
    assertEquals(clue(l.of(Decision.Kind.DeferredInit)).map(_.subjectFqn), List("demo.Boot#w"))
    assertEquals(p.seams(a).count(_.kind == ContextSeamCheck.Kind.DeferredInit), 1, render(p, a))
  }

  test("a deferral does NOT need a `<clinit>` to strip — the ValDef itself is what is replaced") {
    val (_, _, _, o) = ported(base.copy(sites = Map("demo.Tbl#cellPool" -> ContextSite.LazyInit)))
    // no class initialiser existed for this field, so none is left behind and none is invented
    assert(!code(o).contains("locally {"), o)
  }

  test("eager→lazy is still never a default: no `sites` entry, no deferral") {
    assertEquals(log.of(Decision.Kind.DeferredInit), Nil)
    assertEquals(phase.seams(after).count(_.kind == ContextSeamCheck.Kind.DeferredInit), 0)
  }

  // -------------------------------------------------------------------------
  // the dead binding — the third face of "never fired", and nothing counted it
  // -------------------------------------------------------------------------

  test("a BOUND `sites` entry that selects zero sites is REPORTED, not silently accepted") {
    // `Svc#width` is a real member, so `PolicyBinder.bindMembers` binds it and the never-fired
    // machinery has nothing to say. It is not a boundary and it defers nothing.
    // NEGATIVE: drop `recordDeadSites` and this is a policy entry that is accepted, does nothing,
    // and is invisible to every check in the run.
    val (p, _, _, _) = ported(base.copy(sites = Map("demo.Svc#width" -> ContextSite.LazyInit)))
    val f = p.policyReport.findings.find(_.key == "demo.Svc#width")
    assert(clue(p.policyReport.findings).nonEmpty)
    assertEquals(clue(f).map(_.issue), Some(PolicyIssueLazy))
    assert(f.get.detail.contains("nothing for a context to arrive for"), f.get.render)
    // …and it says which of §1's three kinds the fix is (§4.45)
    assert(f.get.render.contains("§1(b)"), f.get.render)
  }

  test("a `lazy-init` entry on a field with no movable initialiser reports too") {
    val litSrc =
      """package demo;
        |public class Cfg { public static Svc svc; }
        |public class Svc { public int width() { return 0; } }
        |public class Plain { int w; Plain() { w = Cfg.svc.width(); } }
        |public class Named { static Plain p = new Plain(); static int n = 3; }
        |""".stripMargin
    val (p, _, _, _) = portedFrom(litSrc, base.copy(sites = Map("demo.Named#n" -> ContextSite.LazyInit)))
    assert(clue(p.policyReport.findings).exists(_.key == "demo.Named#n"))
  }

  test("an entry that DID fire is not reported — a `lazy-init` that deferred") {
    val (p, _, _, _) = ported(base.copy(sites = Map("demo.Tbl#cellPool" -> ContextSite.LazyInit)))
    assertEquals(clue(p.policyReport.findings.filter(_.key == "demo.Tbl#cellPool")), Nil,
      p.policyReport.render)
  }

  test("…and a `residual-global` that a READ resolved through") {
    // the other two site policies decide how a READ is spelled at a boundary, so a `residual-global`
    // entry fires where `readPlan` consults it — here a field initialiser under `attach = method`.
    val readSrc =
      """package demo;
        |public class Cfg { public static Svc svc; }
        |public class Svc { public int width() { return 0; } }
        |public class Scene { int w = Cfg.svc.width(); }
        |""".stripMargin
    val (p, _, _, o) = portedFrom(readSrc,
      base.copy(attach = ContextAttach.Method, sites = Map("demo.Scene#w" -> ContextSite.ResidualGlobal)))
    assert(clue(code(o)).contains("demo.Ctx.global.svc.width()"), o)
    assertEquals(clue(p.policyReport.findings.filter(_.key == "demo.Scene#w")), Nil, p.policyReport.render)
  }

  test("an UNSUPPLIABLE USE is not a read, and a `residual-global` entry on one says so") {
    // `demo.Named#p` IS a boundary — it constructs a threaded class from a static initialiser — but
    // it reads no mapped static, so there is nothing for `residual-global` to re-spell. The report
    // has to say that rather than claim the site was never reached, or its reader edits the key.
    val (p, _, _, _) = ported(base.copy(sites = Map("demo.Named#p" -> ContextSite.ResidualGlobal)))
    val f = p.policyReport.findings.find(_.key == "demo.Named#p")
    assert(clue(f).isDefined, p.policyReport.render)
    assert(f.get.detail.contains("UNSUPPLIABLE USE"), f.get.render)
    assert(f.get.detail.contains("`lazy-init`"), f.get.render)
  }

  test("an UNBOUND entry is reported ONCE, by the binder — never twice for one mistake") {
    val (p, _, _, _) = ported(base.copy(sites = Map("demo.NoSuch#member" -> ContextSite.LazyInit)))
    assertEquals(clue(p.policyReport.findings.count(_.key == "demo.NoSuch#member")), 1,
      p.policyReport.render)
  }

  test("no `sites` at all: no dead-binding row, and the report is what it always was") {
    assertEquals(clue(phase.policyReport.findings), Nil, phase.policyReport.render)
  }

  private val PolicyIssueLazy = balticporter.core.PolicyIssue.NeverMatched
