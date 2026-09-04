package balticporter.corpus

import balticporter.emit.TirEmitter
import balticporter.frontend.spoon.SpoonTir
import balticporter.tir.{OmissionCheck, Pipeline}

/** When a subclass's roots call DIFFERENT parent constructors that all delegate to the same parent
  * ROOT, `CtorFunnel.resolvedThroughParent` inlines the delegation chain to synthesise a primary
  * at the parent root's parameters. */
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

  // ---- (b) Delegation with replayable post-body ----

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

  test("(b) delegation with replayable post-body: synthesis includes the post-body") {
    // the synthesis resolves to AttrBase(int) — the parent root
    assert(clue(replayOut).contains("protected (sup$0: scala.Int) extends demo.AttrBase(sup$0)"),
      "synthesised primary at parent root's parameter")
    // the post-body `this.desc = desc` should appear after `this(type0)` in each secondary
    assert(clue(replayOut).contains("this.desc = desc"),
      "Object-typed post-body is replayed")
    assert(clue(replayOut).contains("this.desc = name"),
      "String-typed post-body is replayed")
  }

  test("(b) no super args are reported as dropped") {
    assertEquals(clue(replayDropped).count(_.owner.contains("AttrSub")), 0,
      "all AttrSub roots are expressed (delegation + inlined body)")
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

  test("(c) non-replayable post-body: inlining is REFUSED (E134, loud)") {
    // the synthesis should NOT happen — there is no synthesised primary
    assert(!clue(refusedOut).contains("protected (sup$0"),
      "no synthesised primary when post-body is not replayable")
    // the super args should be counted as dropped
    assert(clue(refusedDropped).exists(_.owner.contains("RetSub")),
      "super args reported as dropped for the refused root")
  }
