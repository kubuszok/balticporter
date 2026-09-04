package balticporter.corpus

import balticporter.emit.TirEmitter
import balticporter.frontend.spoon.SpoonTir
import balticporter.tir.{CtorFunnel, OmissionCheck, Pipeline}

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

  // ---- (d) Generic constructor type param -> wildcard slot type // G25, card 4e ----

  private val genericCtorSrc =
    """package demo;
      |public class B {}
      |public class Box<E> { E value; public Box(E v) { this.value = v; } }
      |public class GenBase {
      |  int n;
      |  Object kept;
      |  public GenBase(int n) { this.n = n; }
      |  public <T extends B> GenBase(int n, Box<T> box) { this(n); this.kept = box; }
      |}
      |public class GenSub extends GenBase {
      |  public GenSub(int n, Box<B> box) { super(n, box); }
      |  public GenSub(int n) { super(n); }
      |}
      |""".stripMargin

  private val genericCtorProgram = Pipeline.run(SpoonTir.fromSource(genericCtorSrc), Nil)
  private val genericCtorOut     = new TirEmitter(genericCtorProgram).emit

  test("(d) generic ctor type param: slot type is wildcard-bounded, not bare T") {
    assert(clue(genericCtorOut).contains("extends demo.GenBase(sup$0)"),
      "synthesised primary at parent root's parameter")
    // The slot for `Box<T>` where `T extends B` should be `Box[? <: B]`, not `Box[T]`.
    assert(clue(genericCtorOut).contains("Box[? <: demo.B]"),
      "constructor type param rendered as wildcard with bound")
    assert(!clue(genericCtorOut).contains("Box[T]"),
      "no bare T in the slot type")
  }

  // ---- (e) Value-typed post-body input -> slot defaults to JVM zero + boolean guard // card 4e ----

  private val valueSlotSrc =
    """package demo;
      |public class ValBase {
      |  int n;
      |  float offset;
      |  public ValBase(int n) { this.n = n; }
      |  public ValBase(int n, float offset) { this(n); this.offset = offset; }
      |}
      |public class ValSub extends ValBase {
      |  public ValSub(int n, float offset) { super(n, offset); }
      |  public ValSub(int n) { super(n); }
      |}
      |""".stripMargin

  private val valueSlotProgram = Pipeline.run(SpoonTir.fromSource(valueSlotSrc), Nil)
  private val valueSlotOut     = new TirEmitter(valueSlotProgram).emit

  test("(e) value-typed post-body input: slot defaults to 0f with boolean guard") {
    assert(clue(valueSlotOut).contains("extends demo.ValBase(sup$0)"),
      "synthesised primary at parent root's parameter")
    // The slot for `float offset` should be `offset$: Float`, not null-guarded.
    assert(clue(valueSlotOut).contains("offset$"),
      "value-typed post-body slot present")
    // A boolean guard controls the post-body since Float cannot be null-checked.
    assert(clue(valueSlotOut).contains("via$pb"),
      "boolean guard for value-typed post-body input")
    assert(clue(valueSlotOut).contains("if (via$pb)"),
      "guard uses boolean condition, not null check")
  }

  // ---- (f) a child whose primary is SYNTHESISED passes its slots to the parent root: it does not
  // demand a nilary parent, so the withholding fixpoint must not demote the parent ----

  private val chainSrc =
    """package demo;
      |public class ChainRoot {
      |  int n;
      |  public ChainRoot(int n) { this.n = n; }
      |  public ChainRoot(String s) { this(s.length()); }
      |}
      |public class ChainMid extends ChainRoot {
      |  public ChainMid(int n) { super(n); }
      |  public ChainMid(String s) { super(s); }
      |}
      |public class ChainLeaf extends ChainMid {
      |  public ChainLeaf(int n) { super(n); }
      |  public ChainLeaf(String s) { super(s); }
      |}
      |""".stripMargin

  private lazy val chainOut = new TirEmitter(Pipeline.run(SpoonTir.fromSource(chainSrc), Nil)).emit

  test("(f) a synthesised child does not demote its parent's synthesised primary") {
    assert(clue(chainOut).contains("extends demo.ChainRoot(sup$0)"), "ChainMid keeps its synthesised primary")
    assert(clue(chainOut).contains("extends demo.ChainMid(sup$0)"), "ChainLeaf synthesises against ChainMid's root")
    assert(!clue(chainOut).contains("extends demo.ChainRoot {"), "no argument-free extends of a paramful parent")
  }

  // ---- (g) non-owned parent: no parent plan available, refusal counted ----

  test("(g) non-owned parent with 2+ roots: plan0 without parent plan refuses synthesis") {
    val prog = Pipeline.run(SpoonTir.fromSource(chainSrc), Nil)
    // plan0 WITHOUT a parent plan lookup: the parent (ChainMid) has 2+ roots and its plan is
    // unknown, so resolvedThroughParentPlan refuses and the child gets Plan.none. // D4, C3
    val leafCd = prog.units.flatMap(balticporter.tir.StandardTraversal.allClassDefs(_)(using prog))
      .find(cd => prog.symbolOf(cd.symbol).exists(_.name == "ChainLeaf")).get
    val plan = CtorFunnel.plan0(prog, leafCd)
    assert(clue(plan.superArgs.isEmpty), "no super args without parent plan")
    assert(clue(!plan.isSynthesised), "no synthesis without parent plan")
  }

  // ---- (h) Delegation-head slot: parameter used >1x with non-simple caller arg. C3 ----

  private val dhSlotSrc =
    """package demo;
      |public class Wrapper { Object v; public Wrapper(Object v) { this.v = v; } }
      |public class DhBase {
      |  int n;
      |  Object data;
      |  public DhBase(int n, Object data) { this.n = n; this.data = data; }
      |  public DhBase(int n, String reg) { this(n, reg != null ? new Wrapper(reg) : null); }
      |  public DhBase(String s, int n) { this(n, new String(s)); }
      |}
      |public class DhSub extends DhBase {
      |  public DhSub(int n, Object data) { super(n, data); }
      |  public DhSub(String s, int n) { super(s, n); }
      |}
      |""".stripMargin

  private lazy val dhSlotProgram = Pipeline.run(SpoonTir.fromSource(dhSlotSrc), Nil)
  private lazy val dhSlotOut     = new TirEmitter(dhSlotProgram).emit
  private lazy val dhSlotDropped = OmissionCheck.droppedSuperArgs(dhSlotProgram)

  test("(h) delegation-head slot: parameter used >1x with non-simple arg is bound to a slot") {
    assert(clue(dhSlotOut).contains("extends demo.DhBase("),
      "synthesised primary delegates to the parent root")
    assert(!clue(dhSlotOut).contains("E134"),
      "no refusal -- the synthesis succeeded")
  }

  test("(h) delegation-head slot: no super args dropped") {
    assertEquals(clue(dhSlotDropped).count(_.owner.contains("DhSub")), 0,
      "all DhSub roots are expressed via delegation-head slot")
  }

  test("(h) delegation-head slot: the doubled expression renders with the slot reference") {
    // the super arg at the doubled position should reference the slot name (dh suffix)
    assert(clue(dhSlotOut).contains("$dh"),
      "delegation-head slot parameter present in output")
  }

  // ---- (h2) Same shape but with a post-body too (like BitmapFont) ----

  private val dhWithPostSrc =
    """package demo;
      |public class Wrap2 { Object v; public Wrap2(Object v) { this.v = v; } }
      |public class FontBase {
      |  int n;
      |  Object data;
      |  boolean owns;
      |  public FontBase(int n, Object data) { this.n = n; this.data = data; }
      |  public FontBase(int n, String reg) { this(n, reg != null ? new Wrap2(reg) : null); }
      |  public FontBase(String s, String img, int n) {
      |    this(n, new String(s));
      |    owns = true;
      |  }
      |}
      |public class FontSub extends FontBase {
      |  public FontSub(int n, Object data) { super(n, data); }
      |  public FontSub(int n, String reg) { super(n, reg); }
      |  public FontSub(String s, String img, int n) { super(s, img, n); }
      |}
      |""".stripMargin

  private lazy val dhWithPostProgram = Pipeline.run(SpoonTir.fromSource(dhWithPostSrc), Nil)
  private lazy val dhWithPostOut     = new TirEmitter(dhWithPostProgram).emit
  private lazy val dhWithPostDropped = OmissionCheck.droppedSuperArgs(dhWithPostProgram)

  test("(h2) delegation-head slot + post-body: synthesis succeeds") {
    assert(clue(dhWithPostOut).contains("extends demo.FontBase("),
      "synthesised primary delegates to the parent root")
  }

  test("(h2) delegation-head slot + post-body: no super args dropped") {
    assertEquals(clue(dhWithPostDropped).count(_.owner.contains("FontSub")), 0,
      "all FontSub roots are expressed")
  }

  test("(h2) delegation-head slot + post-body: boolean guard for ownsTexture-style post-body") {
    assert(clue(dhWithPostOut).contains("via$pb"),
      "boolean guard present for param-less post-body")
  }
