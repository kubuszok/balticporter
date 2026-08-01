package balticporter.corpus

import balticporter.emit.TirEmitter
import balticporter.frontend.spoon.SpoonTir
import balticporter.tir.Pipeline

/** THE WHOLE-PROGRAM GUARDS AROUND THE SYNTHESIS — the two that were measured and then pinned by
  * nothing but a lane.
  *
  * `CtorFunnel.Plans` decides three things no single class can decide for itself, and each of them
  * was wrong once in a way that compiles:
  *
  *  - **WITHHOLDING** (`ENGINE-LIMITS.md` C1). A paramful primary — synthesised or promoted — is
  *    withheld where some subclass reaches this class with an argument-free `extends`. `DESIGN.md`
  *    §8.2 argued a synthesis needs no such guard, since every java constructor survives as a
  *    `def this` and `extends C` reaches the nilary one. True, and it does not follow: the TRIGGER
  *    is the SUBCLASS's plan, and a subclass carrying no super arguments emits `extends P` BARE
  *    even where java wrote `super(args)`. Deleted, libGDX core measured **0 -> 4 compile errors**
  *    (`E134`, "None of the overloaded alternatives of constructor BatchTiledMapRenderer") and
  *    omissions 180 -> 196.
  *  - **`nilaryPlan` MUST NOT OVERWRITE A SYNTHESIS** (C1.5). A synthesised plan has no `primary`
  *    either, so `primary.isEmpty` claimed every one of them for the nilary promotion — and the
  *    promotion came back with its escaping body. libGDX core escapes **95 -> 31**, and the port
  *    compiled at 0 either way.
  *  - **"is this a synthesis" is `Plan.isSynthesised`** (C1.5 again), never `synthetic.nonEmpty`:
  *    a class disambiguated by the MARKER ALONE has an EMPTY slot list, and reading the field
  *    emitted a primary whose parameter list was empty while every secondary wrote
  *    `this((null: C.Funnel))` against scala's implicit nilary primary.
  *
  * Each is a whole-program decision, so each needs a fixture with the SHAPE — one class is never
  * enough. Every assertion below was verified to fail against its own guard reverted.
  */
class SyntheticPrimaryWithholdingSpec extends munit.FunSuite:

  // ---- C1: the withholding fixpoint, on the shape that named it ----

  private val withheldSrc =
    """package with1;
      |class Base { Base(int a, int b) {} }
      |/** TWO PARAMFUL ROOTS reaching one parent constructor, NEITHER a pass-through (so there is
      |  * nothing to collapse onto) and NO NILARY java constructor: the synthesis fires, and the
      |  * primary it would emit is the only constructor the class has. */
      |class Mid extends Base {
      |  Mid(int a)     { super(a, 1); }
      |  Mid(boolean f) { super(0, 2); }
      |}
      |/** …and this subclass reaches it ARGUMENT-FREE. Its own roots reach two DIFFERENT `Mid`
      |  * constructors, so it is a wall, its plan carries no super arguments, and the `extends`
      |  * clause it emits is bare — where java wrote `super(a)` and `super(f)`. */
      |class Sub extends Mid {
      |  Sub(int a)     { super(a); }
      |  Sub(boolean f) { super(f); }
      |}
      |""".stripMargin

  private val withheld = new TirEmitter(Pipeline.run(SpoonTir.fromSource(withheldSrc), Nil)).emit

  test("C1 — a synthesis a subclass would reach ARGUMENT-FREE is WITHHELD") {
    // the subclass's clause is the fact the guard reads, and it is bare
    assert(clue(withheld).contains("class Sub extends with1.Mid {"))
    // so `Mid` may not carry a paramful primary of any kind. Emitted, the class would have exactly
    // one constructor — `(sup$0, sup$1)` — and `extends with1.Mid` names none of them: `E134`, four
    // times, on the run that deleted this guard.
    assert(!withheld.contains("class Mid protected ("), "the synthesis was not withheld")
    assert(clue(withheld).contains("class Mid extends with1.Base"))
  }

  test("C1 — withholding is NOT a whole-program ban: a synthesis nothing reaches bare survives") {
    // the other direction, and it is the one that makes the guard worth having rather than a
    // blanket refusal. Same `Mid`, with the subclass passing arguments up.
    val ok =
      """package with2;
        |class Base { Base(int a, int b) {} }
        |class Mid extends Base {
        |  Mid(int a)     { super(a, 1); }
        |  Mid(boolean f) { super(0, 2); }
        |}
        |class Sub extends Mid {
        |  Sub(int a) { super(a); }
        |}
        |""".stripMargin
    val o = new TirEmitter(Pipeline.run(SpoonTir.fromSource(ok), Nil)).emit
    assert(clue(o).contains("class Mid protected (sup$0: scala.Int, sup$1: scala.Int) extends with2.Base(sup$0, sup$1)"))
  }

  // ---- C1.5: `nilaryPlan` and the two readings of "is this a synthesis" ----

  test("C1.5 — `nilaryPlan` must NOT claim a SYNTHESIS: `primary.isEmpty` is true of both") {
    // No constructor of this class carries `super(args)`, which is `nilaryPlan`'s entire domain —
    // and it is also exactly where the synthesis fires on the IMPLICIT `super()`. Read as
    // "nothing was nominated", the empty `primary` of a synthesised plan handed every one of these
    // classes back to the promotion, whose body then ran on every construction path (libGDX's
    // `CharArray`: 9 escaping paths the synthesis had already removed).
    val src =
      """package nil1;
        |class Buf {
        |  int cap; int size;
        |  Buf()                     { this(16, 0); }
        |  Buf(int cap, int extra)   { this.cap = cap + extra; }
        |  Buf(int cap, boolean big) { this.cap = cap; }
        |}
        |""".stripMargin
    val o = new TirEmitter(Pipeline.run(SpoonTir.fromSource(src), Nil)).emit
    assert(clue(o).contains("class Buf protected (f$cap: scala.Int)"))
    // and the nilary constructor is still there to be called, as a secondary
    assert(o.contains("def this() = {"))
  }

  test("C1.5 — a MARKER-ONLY synthesis has an EMPTY slot list and still emits a valid primary") {
    // No super slots (the parent is `Object`), no hoistable field, and a real nilary constructor
    // whose erased parameter list EQUALS the empty slot list — so the class needs the marker and
    // nothing else. `synthetic.nonEmpty` is false here, and a predicate reading it emitted
    // `class Two protected ()` while every secondary wrote `this((null: Two.Funnel))` — against
    // scala's implicit nilary primary, which is not that constructor.
    val src =
      """package mark1;
        |class Two {
        |  int n;
        |  void go() { n++; }
        |  Two()      { }
        |  Two(int k) { go(); }
        |}
        |""".stripMargin
    val o = new TirEmitter(Pipeline.run(SpoonTir.fromSource(src), Nil)).emit
    assert(clue(o).contains("class Two protected (ctor$: Two.Funnel)"))
    assert(o.contains("def this() = {"))
    assert(o.contains("this((null: Two.Funnel))"))
    assert(o.contains("protected final class Funnel"))
  }
