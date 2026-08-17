package balticporter.corpus

import balticporter.emit.TirEmitter
import balticporter.frontend.spoon.SpoonTir
import balticporter.tir.{Decision, Pipeline}

/** A `private` a CONSTRUCTOR writes is widened for a subclass THIS RUN CANNOT SEE —
  * `ENGINE-LIMITS.md` C15.
  *
  * `CtorFunnel.Plans.replayFor` expresses a secondary constructor's `super(args)` as the parent
  * constructor's own statements replayed one level down, and those statements then execute in the
  * SUBCLASS, where the parent's `private` no longer reaches. Within one module the planner widens
  * what the replays it OBSERVED touch. That makes the answer depend on which run you are in: a
  * subclass in a dependent module replays the very same constructor, `reachablePrivate` reads the
  * base's published `vis=private`, and — because the refusal is at the CONSTRUCTOR and not at the
  * statement — the WHOLE `super(args)` is dropped, at 0 compile errors and no moved test.
  *
  * So the widening is derived from the class's OWN declarations instead: a paramful, non-private
  * constructor of an extensible class is one a subclass's secondary can only express as a replay,
  * and every `private` its statements touch is a member that replay must reach.
  *
  * The five negatives below are the narrowings, and each is a FACT rather than a budget — a
  * widening nobody needs is emitted surface the port did not have to move (`CLAUDE.md` §5).
  */
class CtorFunnelExternalReplaySpec extends munit.FunSuite:

  /** Every class below extends `Base` and its roots reach THREE DIFFERENT `Base` constructors,
    * which is the shape `CtorFunnel` answers with shape (2) NO-ARG ROOT — the nilary root promoted
    * and the fields left as declared members. Roots that all reach ONE parent constructor get a
    * SYNTHESISED primary instead, and the fields become its `val`s, where there is no modifier for
    * this pass to move and nothing to read off the emitted text. */
  private val src =
    """package demo;
      |public class Base {
      |  public Base() { }
      |  public Base(int a) { }
      |  public Base(int a, int b) { }
      |}
      |/** THE SHAPE: `n` is written by a paramful constructor, so a subclass in another module that
      |  * cannot promote this root has to replay `this.n = n` one level down. */
      |public class Widened extends Base {
      |  private int n = 0;
      |  private int untouched = 0;
      |  public Widened() { super(); }
      |  public Widened(int n) { super(1); this.n = n; }
      |  public Widened(int n, int m) { super(1, 2); this.n = n; }
      |}
      |/** THE SECOND FACE: a `protected` member is reachable one level down as `this.p`, and NOT
      |  * through a prefix whose type is the DECLARING class — which is exactly the shape a COPY
      |  * constructor writes and exactly what java permits INSIDE the declaring class. So `viaPrefix`
      |  * is widened and `ownOnly`, written only through `this`, keeps its modifier. */
      |public class Copied extends Base {
      |  protected int viaPrefix = 0;
      |  protected int ownOnly = 0;
      |  public Copied() { super(); }
      |  public Copied(Copied other) { super(1); this.viaPrefix = other.viaPrefix; this.ownOnly = 3; }
      |  public Copied(int a, int b) { super(a, b); }
      |}
      |/** NEGATIVE 1 — a `final` class has no subclass anywhere, so no replay can exist. */
      |public final class Sealed extends Base {
      |  private int n = 0;
      |  public Sealed() { super(); }
      |  public Sealed(int n) { super(1); this.n = n; }
      |  public Sealed(int a, int b) { super(a, b); }
      |}
      |/** NEGATIVE 2 — a NILARY constructor is not a replay target: `extends P` (or a secondary's
      |  * `this()`) already runs it, so nothing has to be written one level down. */
      |public class NilaryOnly extends Base {
      |  private int n = 0;
      |  public NilaryOnly() { super(); this.n = 1; }
      |}
      |/** NEGATIVE 3 — java forbids `super(...)` to a PRIVATE constructor, so no subclass anywhere
      |  * can reach this one. */
      |public class PrivateCtor extends Base {
      |  private int n = 0;
      |  public PrivateCtor() { super(); }
      |  private PrivateCtor(int n) { super(1); this.n = n; }
      |  public PrivateCtor(int a, int b) { super(a, b); }
      |}
      |/** NEGATIVE 4 — a METHOD is half of an override CONTRACT, so widening one obliges every
      |  * override below it — including overrides in a module this run cannot reach. A replay that
      |  * touches a private method stays REFUSED and stays counted, which is C3's own answer. */
      |public class MethodTouch extends Base {
      |  private int n = 0;
      |  public MethodTouch() { super(); }
      |  public MethodTouch(int v) { super(1); this.init(v); }
      |  public MethodTouch(int a, int b) { super(a, b); }
      |  private void init(int v) { this.n = v; }
      |}
      |/** NEGATIVE 5 — the body holds a `super.m()`, which dispatches one level too high once
      |  * replayed, so `usable` refuses this replay wherever it lands and a widening for it is a
      |  * modifier nobody can use. */
      |public class SuperCaller extends Base {
      |  private int n = 0;
      |  public SuperCaller() { super(); }
      |  public SuperCaller(int n) { super(1); this.n = n; super.hashCode(); }
      |  public SuperCaller(int a, int b) { super(a, b); }
      |}
      |""".stripMargin

  private val program = Pipeline.run(SpoonTir.fromSource(src), Nil)
  private val emitter = new TirEmitter(program)
  private val out     = emitter.emit

  /** the emitted declaration line for one field, whichever class it is in — read off the emitted
    * TEXT rather than off the planner's set, so the assertion fails against an engine that computes
    * the set and never renders it. */
  private def declOf(field: String): String =
    out.linesIterator.find(l => l.contains(s"var $field:"))
      .getOrElse(fail(s"no declaration of `$field` in\n$out"))

  test("a private field a PARAMFUL constructor writes loses `private` — the C15 widening") {
    assert(!clue(declOf("n")).contains("private"), "the field a cross-module replay must reach")
  }

  test("a private field NO constructor writes keeps `private`") {
    assert(clue(declOf("untouched")).contains("private"))
  }

  test("NEGATIVES: final class, nilary-only, private ctor, private method, un-replayable body") {
    // one `n` per class, so the count of surviving `private var n` is the count of refusals.
    val kept = out.linesIterator.count(l => l.trim.startsWith("private var n:"))
    assertEquals(clue(kept), 5, s"expected the five narrowings to keep their modifier in\n$out")
  }

  test("a private METHOD a constructor calls is NOT widened — a method is half of an override contract") {
    assert(out.linesIterator.exists(_.trim.startsWith("private def init(")),
           s"a widened method would oblige every override below it, in modules this run cannot reach:\n$out")
  }

  test("a PROTECTED member read through a non-`this` prefix is widened; one written via `this` is not") {
    assert(!clue(declOf("viaPrefix")).contains("protected"),
           "scala refuses a protected member at a prefix typed as the DECLARING class")
    assert(clue(declOf("ownOnly")).contains("protected"),
           "`this.p = 3` is a subclass's own access and needs nothing")
  }

  test("the widening is RECORDED, and its note says which run's subclass it is about") {
    // read off the emitter's OWN log — the value a run drains into `decisions.tsv` and the one
    // `NoteCoverageCheck` holds the emitted text against. The emitted note itself is rendered from
    // the log the RUN supplies, which a bare emitter does not have.
    val rows = emitter.ownDecisions
      .filter(d => d.kind == Decision.Kind.WidenedVisibility &&
                   d.detail.get("cause").contains("ctor-replay-widening"))
    assertEquals(clue(rows).size, 2)
    assert(rows.forall(_.detail.get("scope").contains("dependent-modules")))
    assertEquals(rows.count(_.detail.get("from").contains("private")), 1)
    assertEquals(rows.count(_.detail.get("from").contains("protected")), 1)
    assertEquals(rows.map(_.subjectFqn).sorted, List("demo.Copied#viaPrefix", "demo.Widened#n"))
  }
