package balticporter.corpus

import balticporter.emit.TirEmitter
import balticporter.frontend.spoon.SpoonTir
import balticporter.tir.{OmissionCheck, Pipeline}

/** A parent constructor whose whole body is ONE BRANCH is still a replayable `super(args)`.
  *
  * `CtorFunnel.Plans.replayFor` expresses a secondary constructor's `super(args)` as the parent
  * constructor's own statements run after `this()`, and it is admissible exactly when the replay
  * OVERWRITES every field the emitted `this()` already put into the object. That question used to
  * be asked through one function that recognised a plain `this.f = <e>` and nothing else, so a
  * parent whose body is
  *
  * {{{ if (other == null) data = new HashMap<>(); else data = new HashMap<>(other.getAll()); }}}
  *
  * — ONE `Tree.If` — answered "assigns no field", the replay was refused, and the secondary emitted
  * a bare `this()`. Nothing reports that: the constructor's loss is counted honestly by
  * `OmissionCheck.droppedSuperArgs`, which reads exactly the same predicate, and the port compiles
  * and constructs an EMPTY object. Measured three classes deep on flexmark, where a renderer built
  * through `builder(options)` silently held no options at all (`PROGRESS.md` §10.6.7) — the whole
  * of one library's remaining conformance residue, at 0 compile errors and every check count flat.
  *
  * The widening is two functions and not one, and both directions are asserted here because either
  * alone is unsound:
  *
  *   - the PROLOGUE is read as MAY-assign (the union over a branch's arms), because the question is
  *     what `this()` may have left in the object;
  *   - the REPLAY is read as MUST-assign (the INTERSECTION over a branch's arms), because the
  *     question is what it definitely overwrites. `Half` is that half's negative: a field written in
  *     one arm only is not overwritten on every path, so its replay stays refused. Read through one
  *     union-shaped function it would be admitted, the emitted constructor would compile, and the
  *     prologue's value would survive on the arm nobody looked at.
  */
class CtorFunnelBranchReplaySpec extends munit.FunSuite:

  private val src =
    """package demo;
      |/** the shape: a nilary constructor DELEGATING to the one root, whose whole body is a branch
      |  * assigning the same field on both arms. */
      |public class Bag {
      |  java.util.HashMap<String, Object> data;
      |  public Bag() { this(null); }
      |  public Bag(Bag other) {
      |    if (other == null) data = new java.util.HashMap<String, Object>();
      |    else data = new java.util.HashMap<String, Object>(other.data);
      |  }
      |}
      |/** two roots, one nilary, reaching two DIFFERENT parent constructors — so nothing is
      |  * synthesised, the nilary root is promoted, and `super(other)` can only survive as a REPLAY. */
      |public class MutableBag extends Bag {
      |  public MutableBag() { super(); }
      |  public MutableBag(Bag other) { super(other); }
      |}
      |/** the NEGATIVE: the parent's branch writes `a` on ONE arm only, so replaying it does not
      |  * overwrite what the promoted nilary body put there. The refusal must stand. */
      |public class Half {
      |  int a;
      |  Half() { a = 1; }
      |  Half(int n) { if (n > 0) { a = n; } }
      |}
      |public class HalfUser extends Half {
      |  HalfUser() { super(); }
      |  HalfUser(int n) { super(n); }
      |}
      |""".stripMargin

  private val program = Pipeline.run(SpoonTir.fromSource(src), Nil)
  private val out     = new TirEmitter(program).emit
  private val dropped = OmissionCheck.droppedSuperArgs(program)

  // the replay: `this()` first (scala requires it), then the parent constructor's own statements
  // with `other` in place of its parameter.
  private val replayed =
    raw"""def this\(other: demo\.Bag\) = \{\s*this\(\)\s*if \(other == null\)""".r
  private val refused = raw"""def this\(n: scala\.Int\) = \{\s*this\(\)\s*\}""".r

  test("a BRANCHING parent constructor body is replayed — the argument is not lost") {
    assert(replayed.findFirstIn(clue(out)).isDefined)
    // …and the check agrees, because it reads the same predicate.
    assertEquals(dropped.filter(_.owner == "demo.MutableBag"), Nil)
  }

  test("a branch that writes on ONE arm only is still refused, and still counted") {
    assert(refused.findFirstIn(clue(out)).isDefined)
    assertEquals(dropped.map(f => (f.owner, f.detail)),
                 List(("demo.HalfUser", "1 argument(s) discarded")))
  }
