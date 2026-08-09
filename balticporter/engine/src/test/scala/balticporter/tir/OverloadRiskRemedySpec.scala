package balticporter.tir

import balticporter.catalog.CatalogLog
import balticporter.core.{PolicyIssue, PolicyReport}
import balticporter.emit.TirEmitter
import balticporter.frontend.spoon.SpoonTir

/** `overload-risk`'s MENU — the first EMISSION-AFFECTING remedy (`DESIGN.md` §8.16).
  *
  * Two things are under test that the `heap-pollution` suite could not reach. The first is that an
  * applied remedy MOVES EMITTED TEXT and that the move is attributable: the digest changes because a
  * `Decision` says it should, which is `CLAUDE.md` §3's rule for any wave that rewrites by design.
  * The second is the REFUSAL, which is what keeps this remedy inside `ENGINE-LIMITS.md` T17 — the
  * engine does not predict which member scala would bind, it writes down the one the frontend
  * resolved, and where that name cannot be written it records nothing and the finding stays.
  *
  * The java is the shape `CLAUDE.md` §4.4 already records for the JDK, asked of a library's own
  * declarations: `remove(Object)` beside `remove(int)`, with javac binding the `int` one in phase 1.
  */
class OverloadRiskRemedySpec extends munit.FunSuite:

  private val Java =
    """package com.demo;
      |public class Bag<T> {
      |  public Object[] items = new Object[8];
      |  public int size;
      |  public boolean remove(Object item) { return item != null && remove(indexOf(item)) != null; }
      |  public T remove(int index) { return (T) items[index]; }
      |  public int indexOf(Object item) { return -1; }
      |  public void keep(T one) { }
      |  public void keep(T... many) { }
      |  public void both(T one) { keep(one); }
      |  public static void tag(Object o) { }
      |  public static void tag(int n) { }
      |  public void go() { tag(1); }
      |}""".stripMargin

  private def program: Program = SpoonTir.fromSource(Java, catalog = CatalogLog.discarding)

  private val vocabulary = RemedyVocabulary.from(List(OverloadRiskCheck))

  private def run(p: Program, declared: Map[String, String]): (Program, PolicyBinder) =
    val binder = new PolicyBinder(p, p.members)
    binder.resolving(ResolutionPlan.of(declared, vocabulary, vocabulary.byId.keySet, binder))
    (Pipeline.runTraced(p, List(new OverloadRiskCheck.Apply), binder)._1, binder)

  private def lane(p: Program, plan: ResolutionPlan = ResolutionPlan.empty): List[String] =
    OverloadRiskCheck.check(p, p.units, new OverloadRiskCheck.Overloads(p), plan)
      .findings.map(f => s"${f.issue} ${f.member}@${f.origin.line}")

  private def emitted(p: Program): String = new TirEmitter(p).emit

  // -------------------------------------------------------------------------------------------
  // the menu
  // -------------------------------------------------------------------------------------------

  test("the menu is two entries, and only one of them touches emitted text") {
    assertEquals(OverloadRiskCheck.remedies.map(_.id).sorted, List("accept-risk", "ascribe-javac-choice"))
    assert(OverloadRiskCheck.AscribeJavacChoice.emissionAffecting)
    assert(!OverloadRiskCheck.AcceptRisk.emissionAffecting)
  }

  test("…and each answers ALL THREE of JLS 15.12.2's boundaries, because they are one candidate set") {
    // The alternative is one id per kind, and a per-member key would then leave a member holding two
    // of them able to drain only one — residue no key could ever reach.
    OverloadRiskCheck.Issue.values.foreach { i =>
      assert(clue(OverloadRiskCheck.AscribeJavacChoice).answers(OverloadRiskCheck.Name, i.toString))
      assert(clue(OverloadRiskCheck.AcceptRisk).answers(OverloadRiskCheck.Name, i.toString))
    }
    assert(!OverloadRiskCheck.AcceptRisk.answers("another-lane", "VarargPhaseSpan"))
  }

  test("the run's vocabulary carries both, so a `.conf` refuses a typo and not a real id") {
    val v = RemedyVocabulary.from(balticporter.runner.PortRun.CheckRemedies)
    assert(v.contains("ascribe-javac-choice"))
    assert(v.contains("accept-risk"))
  }

  // -------------------------------------------------------------------------------------------
  // the population this port has, before anything is selected
  // -------------------------------------------------------------------------------------------

  test("with no selections the lane is what it always was — the mechanism's no-op") {
    val p = program
    // `remove(indexOf(item))` inside `remove(Object)`: javac binds `remove(int)` in phase 1 while
    // `remove(Object)` is applicable only after boxing.
    assert(clue(lane(p)).contains("BoxingPhaseSpan remove/1@5"))
    // `keep(one)` inside `both`: a fixed-arity candidate beside a variable-arity one.
    assert(clue(lane(p)).contains("VarargPhaseSpan keep/1@10"))
  }

  // -------------------------------------------------------------------------------------------
  // `ascribe-javac-choice` — the emission, and its attribution
  // -------------------------------------------------------------------------------------------

  test("ascribe-javac-choice PINS the alternative javac bound, and the drain is exact") {
    val p             = program
    val (out, binder) = run(p, Map("com.demo.Bag#remove(Object)" -> "ascribe-javac-choice"))
    val plan          = binder.resolutions
    assertEquals(plan.all.size, 1)
    assertEquals(plan.all.map(_.subjectFqn), List("com.demo.Bag#remove"))
    assert(clue(plan.all.head.what).contains("pinned to the alternative javac bound"))
    // the row left the lane and arrived in `remediation(resolved)` — both halves, one number.
    assert(!clue(lane(out, plan)).contains("BoxingPhaseSpan remove/1@5"))
    assertEquals(plan.all.map(_.finding.kind), List("resolved"))
    assertEquals(plan.troubles, Nil)
  }

  test("…and the emitted text is a METHOD-VALUE ascription, which is what chooses the overload") {
    val out = run(program, Map("com.demo.Bag#remove(Object)" -> "ascribe-javac-choice"))._1
    val src = emitted(out)
    // `(this.remove: (Int) => T)(this.indexOf(item))` — the ascription names javac's signature, and
    // an ARGUMENT ascription would not have chosen anything (both alternatives stay applicable).
    assert(clue(src).contains(": (scala.Int) => "), clue(src))
    // never a cast: `asInstanceOf` on the receiver asserts something else entirely.
    assert(!clue(src).contains("asInstanceOf[(scala.Int)"))
  }

  test("…and the DIGEST MOVED because a Decision says it should — nothing else changed") {
    val p             = program
    val before        = emitted(p)
    val (out, binder) = run(p, Map("com.demo.Bag#remove(Object)" -> "ascribe-javac-choice"))
    val after         = emitted(out)
    assertNotEquals(after, before)
    // every moved line is inside the member the decision names, and there is exactly one decision.
    val decisions = binder.resolutions.all.map(_.decision)
    assertEquals(decisions.map(_.kind), List(Decision.Kind.SelectedRemedy))
    assertEquals(decisions.map(_.subjectFqn), List("com.demo.Bag#remove"))
    assertEquals(decisions.head.reason, Reason.Configured("resolutions", "com.demo.Bag#remove(Object)"))
    val moved = before.linesIterator.toList.zipAll(after.linesIterator.toList, "", "")
      .collect { case (b, a) if b != a => a.trim }
    assert(clue(moved).forall(_.contains("remove")), clue(moved))
  }

  // -------------------------------------------------------------------------------------------
  // `accept-risk` — a statement, not an act
  // -------------------------------------------------------------------------------------------

  test("accept-risk drains the row and leaves the tree exactly as it was") {
    val p             = program
    val before        = emitted(p)
    val (out, binder) = run(p, Map("com.demo.Bag#both(T)" -> "accept-risk"))
    val plan          = binder.resolutions
    assertEquals(plan.all.size, 1)
    assert(clue(plan.all.head.what).contains("accepted by this port"))
    assert(!clue(lane(out, plan)).contains("VarargPhaseSpan keep/1@10"))
    assertEquals(emitted(out), before)
  }

  // -------------------------------------------------------------------------------------------
  // the refusal — what keeps this inside T17
  // -------------------------------------------------------------------------------------------

  test("ascribe REFUSES where javac's alternative cannot be WRITTEN, and the finding stays") {
    // `go` calls the STATIC `tag(int)` beside `tag(Object)` — a `BoxingPhaseSpan` javac answered in
    // phase 1. A static has no method value the emitter may select on a receiver (java lets a static
    // be called through an INSTANCE and JS-C06 has to move the receiver), so the ascription is
    // refused: nothing is recorded, the row stays in the lane, and no text moves.
    val p             = program
    val before        = emitted(p)
    val (out, binder) = run(p, Map("com.demo.Bag#go()" -> "ascribe-javac-choice"))
    val plan          = binder.resolutions
    assertEquals(plan.all, Nil)
    assert(clue(lane(out, plan)).contains("BoxingPhaseSpan tag/1@13"))
    assertEquals(emitted(out), before)
    // …and the decline is a COUNTED row naming its guard, never silence and never `NeverApplied`:
    // the selection WAS consulted and the engine answered. Reported as inert it would send its
    // author looking for a call that is right there.
    val List(r) = plan.refusals: @unchecked
    assertEquals(r.guard, "static-callee")
    assertEquals(r.finding.kind, Resolution.RefusedKind)
    assert(clue(r.why).contains("stay in the lane"))
    assertEquals(plan.troubles, Nil)
  }

  test("…and a PARTIAL refusal is the shape silence hid: one call ascribed, one declined, both said") {
    // A selection BROADCASTS across the member it names, so this is not an exotic case — it is what
    // any member holding two risky calls does. Recorded nowhere, the decline left an applied row, a
    // lane residue, no refusal row and no `NeverApplied` (the key fired, once), so nothing at all
    // said the engine had refused something the port asked for.
    val Mixed =
      """package com.demo;
        |public class Mix {
        |  public boolean remove(Object item) { return false; }
        |  public String remove(int index) { return null; }
        |  public static void tag(Object o) { }
        |  public static void tag(int n) { }
        |  public void both() {
        |    remove(1);
        |    tag(1);
        |  }
        |}""".stripMargin
    val p      = SpoonTir.fromSource(Mixed, catalog = CatalogLog.discarding)
    val binder = new PolicyBinder(p, p.members)
    binder.resolving(ResolutionPlan.of(
      Map("com.demo.Mix#both()" -> "ascribe-javac-choice"), vocabulary, vocabulary.byId.keySet, binder))
    val out  = Pipeline.runTraced(p, List(new OverloadRiskCheck.Apply), binder)._1
    val plan = binder.resolutions
    assertEquals(plan.all.size, 1)
    assert(clue(plan.all.head.what).contains("remove"))
    val List(r) = plan.refusals: @unchecked
    assertEquals(r.guard, "static-callee")
    assert(clue(r.why).contains("tag"))
    // the drained half fell and the refused half stayed — which is the pair §5 asks a reader to read.
    assertEquals(lane(out, plan), List("BoxingPhaseSpan tag/1@9"))
  }

  test("…and the guard is STATED ONCE, so the refusal and the emission read one predicate") {
    val p = program
    given Program = p
    val ov  = new OverloadRiskCheck.Overloads(p)
    val out = collection.mutable.ListBuffer.empty[(String, Boolean)]
    val scan = new Phase:
      def name: String = "spec/scan"
      override def transformApply(a: Tree.Apply)(using Program): Term =
        if p.owns(a.method) then
          out += (p.symbolOf(a.method).map(_.name).getOrElse("?") -> OverloadRiskCheck.ascription(a).isRight)
        a
    p.units.foreach(u => StandardTraversal.mapClassDef(scan, u))
    // a STATIC callee is refused; an instance, fixed-arity, non-generic one is not.
    assert(clue(out.toList).contains("tag" -> false))
    assert(clue(out.toList).contains("remove" -> true))
  }

  test("a selection at a declaration with no risky call at all is INERT, never silence") {
    val (_, binder) = run(program, Map("com.demo.Bag#indexOf(Object)" -> "accept-risk"))
    assertEquals(binder.resolutions.all, Nil)
    assertEquals(PolicyReport.fromResolutions(binder.resolutions.troubles).findings.map(_.issue),
                 List(PolicyIssue.NeverApplied))
  }

  // -------------------------------------------------------------------------------------------
  // ONE derivation of "the declaration a `resolutions` key can name" — read by BOTH sides
  // -------------------------------------------------------------------------------------------

  /** the shape neither side could see the other's answer for: a risky call written inside an
    * ANONYMOUS class in a member's body. The check attributed it to the anon method (a real
    * declaration whose owner is a type — and a name no key can write, `Outer$1#run`), the applier to
    * the member whose body it walked. */
  private val Anon =
    """package com.demo;
      |public class Holder {
      |  public boolean remove(Object item) { return false; }
      |  public String remove(int index) { return null; }
      |  public Runnable inMethod() {
      |    return new Runnable() { public void run() { remove(1); } };
      |  }
      |}""".stripMargin

  private def anonProgram: Program = SpoonTir.fromSource(Anon, catalog = CatalogLog.discarding)

  private def anonRun(declared: Map[String, String]): (Program, PolicyBinder) =
    val p      = anonProgram
    val binder = new PolicyBinder(p, p.members)
    binder.resolving(ResolutionPlan.of(declared, vocabulary, vocabulary.byId.keySet, binder))
    (Pipeline.runTraced(p, List(new OverloadRiskCheck.Apply), binder)._1, binder)

  test("a call inside an ANON class is the ENCLOSING MEMBER's row — the check says so, not just the applier") {
    val p = anonProgram
    assert(clue(lane(p)).contains("BoxingPhaseSpan remove/1@6"))
    val decl = OverloadRiskCheck.check(p, p.units, new OverloadRiskCheck.Overloads(p))
      .findings.head.declaration
    // the anon method is a real declaration and an unwritable KEY (`Holder$1#run`, numbered by a
    // per-class counter), so the row is attributed to the member a port can actually name.
    assertEquals(p.symbolOf(decl).map(_.fullName), Some("com.demo.Holder#inMethod"))
  }

  test("…so `accept-risk` at that member DRAINS it — the two sides name one declaration") {
    val (out, binder) = anonRun(Map("com.demo.Holder#inMethod()" -> "accept-risk"))
    val plan          = binder.resolutions
    assertEquals(plan.all.size, 1)
    assertEquals(plan.all.map(_.subjectFqn), List("com.demo.Holder#inMethod"))
    // the half that used to be missing: the residue really left the lane. Attributed differently by
    // the two sides, the lane fell by 0 while `remediation(resolved)` gained 1, at no error and no
    // moved digest.
    assertEquals(lane(out, plan), Nil)
    assertEquals(plan.troubles, Nil)
  }

  test("…and `ascribe-javac-choice` there both PINS the call and drains, from the same key") {
    val before        = emitted(anonProgram)
    val (out, binder) = anonRun(Map("com.demo.Holder#inMethod()" -> "ascribe-javac-choice"))
    val plan          = binder.resolutions
    assertEquals(plan.all.map(_.subjectFqn), List("com.demo.Holder#inMethod"))
    assert(clue(emitted(out)).contains(": (scala.Int) => "))
    assertNotEquals(emitted(out), before)
    assertEquals(lane(out, plan), Nil)
  }

  test("a declaration this run does NOT emit is another module's row — D2 at the ledger") {
    val p      = program
    val binder = new PolicyBinder(p, p.members, RunScope.of(Set.empty, Map.empty))
    binder.resolving(ResolutionPlan.of(
      Map("com.demo.Bag#remove(Object)" -> "ascribe-javac-choice"), vocabulary,
      vocabulary.byId.keySet, binder))
    val out = Pipeline.runTraced(p, List(new OverloadRiskCheck.Apply), binder)._1
    assertEquals(binder.resolutions.all, Nil)
    assertEquals(emitted(out), emitted(p))
  }
