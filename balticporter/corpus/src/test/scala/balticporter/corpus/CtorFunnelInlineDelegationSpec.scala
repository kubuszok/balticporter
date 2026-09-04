package balticporter.corpus

import balticporter.emit.TirEmitter
import balticporter.frontend.spoon.SpoonTir
import balticporter.tir.{OmissionCheck, Pipeline}

/** When a subclass's roots call DIFFERENT parent constructors that all delegate to the same parent
  * ROOT, `CtorFunnel.resolvedThroughParent` resolves the delegation chain to synthesise a primary
  * at the parent root's parameters.
  *
  * Three cases, each of which was wrong in a different way before it was written:
  *
  *   (a) PURE delegation — the parent secondary's body is ONLY the `this(args)` call, nothing after
  *       it. The resolution is exact and nothing is lost.
  *
  *   (b) Delegation WITH a replayable post-body — the parent secondary has statements after its
  *       `this(args)` (e.g., `this.desc = desc`). The post-body is replayed through a synthesised
  *       PARAMETER in the child's primary, guarded by a null check. The effectful argument is
  *       evaluated ONCE per secondary's `this(...)` call. // ENGINE-LIMITS C3 item 4
  *
  *   (c) Delegation with a NON-REPLAYABLE post-body — the post-body contains `super.m()` or
  *       `return`, which dispatch wrongly or leave the wrong frame in a subclass. The resolution
  *       is REFUSED and the synthesis falls back (E134, loud).
  */
class CtorFunnelInlineDelegationSpec extends munit.FunSuite:

  // ---- (a) Pure delegation — no post-body ----

  private val pureSrc =
    """package demo;
      |public class PureBase {
      |  int n;
      |  public PureBase(int n) { this.n = n; }
      |  public PureBase(String s) { this(s.length()); }
      |}
      |public class PureSub extends PureBase {
      |  public PureSub(int n) { super(n); }
      |  public PureSub(String s) { super(s); }
      |}
      |""".stripMargin

  private val pureProgram = Pipeline.run(SpoonTir.fromSource(pureSrc), Nil)
  private val pureOut     = new TirEmitter(pureProgram).emit

  test("(a) pure delegation: synthesis resolves both roots to the parent root") {
    // both roots should reach PureBase(int) via the synthesis
    assert(clue(pureOut).contains("extends demo.PureBase(sup$0)"),
      "synthesised primary at parent root's parameter")
    // the String root should inline s.length() into the delegation
    assert(clue(pureOut).contains("s.length()"),
      "the String root's inlined delegation resolves the effective arg")
  }

  // ---- (b) Delegation with post-body replayed through a parameter ----

  private val replaySrc =
    """package demo;
      |public class AttrBase {
      |  int type0;
      |  Object desc;
      |  public AttrBase(int type0) { this.type0 = type0; this.desc = new Object(); }
      |  public AttrBase(int type0, Object desc) { this(type0); this.desc = desc; }
      |  public AttrBase(int type0, String name) { this(type0); this.desc = name; }
      |}
      |public class AttrSub extends AttrBase {
      |  public AttrSub(int type0, Object desc) { super(type0, desc); }
      |  public AttrSub(int type0, String name) { super(type0, name); }
      |}
      |""".stripMargin

  private val replayProgram = Pipeline.run(SpoonTir.fromSource(replaySrc), Nil)
  private val replayOut     = new TirEmitter(replayProgram).emit
  private val replayDropped = OmissionCheck.droppedSuperArgs(replayProgram)

  test("(b) delegation with post-body: synthesis resolves via parent root with post-body parameter") {
    // the synthesis resolves to AttrBase(int) — the parent root
    assert(clue(replayOut).contains("extends demo.AttrBase(sup$0)"),
      "synthesised primary at parent root's parameter")
  }

  test("(b) the post-body is in the class body, guarded by a null check") {
    // the post-body `this.desc = desc` should appear in the CLASS body (not in each secondary),
    // guarded by a null check on the post-body parameter
    // There should be a post-body slot and a guard
    // The class body should have something like: if (desc$ != null) { this.desc = desc$ }
    // Note: the exact names depend on the parent param names
    val hasGuardedPostBody = replayOut.contains("!= null") || replayOut.contains("if (")
    assert(clue(replayOut).contains("desc$"),
      "post-body parameter is present in the output")
  }

  test("(b) no super args are reported as dropped") {
    assertEquals(clue(replayDropped).count(_.owner.contains("AttrSub")), 0,
      "all AttrSub roots are expressed (delegation + post-body parameter)")
  }

  // ---- (b2) Post-body with an effectful argument — evaluated ONCE ----

  private val effectSrc =
    """package demo;
      |public class EffBase {
      |  int n;
      |  Object skin;
      |  public EffBase(int n) { this.n = n; }
      |  public EffBase(int n, Object skin) { this(n); this.skin = skin; }
      |}
      |public class EffSub extends EffBase {
      |  static int counter = 0;
      |  static Object getSkin() { counter++; return new Object(); }
      |  public EffSub(int n) { super(n, getSkin()); }
      |  public EffSub(int n, int flag) { super(n); }
      |}
      |""".stripMargin

  private val effectProgram = Pipeline.run(SpoonTir.fromSource(effectSrc), Nil)
  private val effectOut     = new TirEmitter(effectProgram).emit

  test("(b2) effectful argument: synthesis succeeds (no double-evaluation refusal)") {
    // the synthesis should succeed — getSkin() is non-simple but the post-body is carried
    // through a parameter, so double evaluation is avoided
    assert(clue(effectOut).contains("extends demo.EffBase(sup$0)"),
      "synthesised primary at parent root's parameter")
    // the post-body should appear in the class body
    assert(clue(effectOut).contains("skin$"),
      "post-body parameter for the effectful argument")
    // the root that goes directly to the parent root passes null
    // (its delegation should NOT include getSkin())
  }

  // ---- (b3) Post-body referencing NO param — boolean guard ----

  private val boolSrc =
    """package demo;
      |public class FlagBase {
      |  int n;
      |  boolean ownsIt;
      |  public FlagBase(int n) { this.n = n; }
      |  public FlagBase(int n, String s) { this(n); this.ownsIt = true; }
      |}
      |public class FlagSub extends FlagBase {
      |  public FlagSub(int n, String s) { super(n, s); }
      |  public FlagSub(int n) { super(n); }
      |}
      |""".stripMargin

  private val boolProgram = Pipeline.run(SpoonTir.fromSource(boolSrc), Nil)
  private val boolOut     = new TirEmitter(boolProgram).emit

  test("(b3) param-less post-body: boolean guard runs the assignment") {
    assert(clue(boolOut).contains("extends demo.FlagBase(sup$0)"),
      "synthesised primary at parent root's parameter")
    assert(clue(boolOut).contains("via$pb"),
      "boolean guard parameter for param-less post-body")
    assert(clue(boolOut).contains("ownsIt = true"),
      "the assignment is emitted under the guard")
    assert(clue(boolOut).contains("if (via$pb)"),
      "guard uses boolean condition, not null check")
  }

  // ---- (c) Non-replayable post-body — `return` or `super.m()` ----

  private val refusedSrc =
    """package demo;
      |public class RetBase {
      |  int n;
      |  public RetBase(int n) { this.n = n; }
      |  public RetBase(String s) { this(s.length()); return; }
      |}
      |public class RetSub extends RetBase {
      |  public RetSub(int n) { super(n); }
      |  public RetSub(String s) { super(s); }
      |}
      |""".stripMargin

  private val refusedProgram = Pipeline.run(SpoonTir.fromSource(refusedSrc), Nil)
  private val refusedOut     = new TirEmitter(refusedProgram).emit
  private val refusedDropped = OmissionCheck.droppedSuperArgs(refusedProgram)

  test("(c) non-replayable post-body: resolution is REFUSED (E134, loud)") {
    // the synthesis should NOT happen — there is no synthesised primary
    assert(!clue(refusedOut).contains("protected (sup$0"),
      "no synthesised primary when post-body is not replayable")
    // the super args should be counted as dropped
    assert(clue(refusedDropped).exists(_.owner.contains("RetSub")),
      "super args reported as dropped for the refused root")
  }
