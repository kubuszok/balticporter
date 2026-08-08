package balticporter.tir

import balticporter.catalog.CatalogLog
import balticporter.core.{PolicyIssue, PolicyReport}
import balticporter.frontend.spoon.SpoonTir

/** THE FIRST MENU, END TO END — `heap-pollution`'s `acknowledge` (`DESIGN.md` §8.16).
  *
  * What this file is really asserting is the ACCOUNTING, because that is the half nothing else can
  * see. A resolution is not a fix, it is a MOVE: a row leaves the lane that counted it and arrives in
  * `remediation(resolved)`, and both halves have to move by the same number or the improvement is
  * unreadable (`CLAUDE.md` §5). `acknowledge` changes no tree at all, so if the drain were missing
  * the run would report the residue BESIDE the row saying it was answered and every count would look
  * plausible.
  *
  * The negatives are the rest, and each is silent by default: a selection at a declaration whose
  * finding is a DIFFERENT KIND, a selection at a declaration with no finding at all, and a selection
  * at a declaration this run does not emit. None of the three moves a compile, a digest or a count.
  */
class HeapPollutionRemedySpec extends munit.FunSuite:

  private val Java =
    """package com.demo;
      |public class Vault<T> {
      |  public void keep(T... items) { }
      |  @SafeVarargs public final void keepSafely(T... items) { }
      |  public void plain(int n) { }
      |}""".stripMargin

  private def program: Program = SpoonTir.fromSource(Java, catalog = CatalogLog.discarding)

  private val vocabulary = RemedyVocabulary.from(List(HeapPollutionCheck))

  /** bind a selection and RUN the applier through the pipeline — the seam a port uses, never a
    * direct call into the phase. */
  private def run(p: Program, declared: Map[String, String]): (Program, PolicyBinder) =
    val binder = new PolicyBinder(p, p.members)
    binder.resolving(ResolutionPlan.of(declared, vocabulary, vocabulary.byId.keySet, binder))
    (Pipeline.runTraced(p, List(new HeapPollutionCheck.Apply), binder)._1, binder)

  private def lane(p: Program, plan: ResolutionPlan = ResolutionPlan.empty): List[String] =
    HeapPollutionCheck.check(p, p.units, plan).map(f => s"${f.issue} ${f.owner}")

  // -------------------------------------------------------------------------------------------
  // the menu itself
  // -------------------------------------------------------------------------------------------

  test("the CHECK declares the menu — the mechanism that mints the row is the one that offers one") {
    assertEquals(HeapPollutionCheck.remedies.map(_.id), List("acknowledge"))
    assertEquals(HeapPollutionCheck.Acknowledge.lane, HeapPollutionCheck.Name)
    assertEquals(HeapPollutionCheck.Acknowledge.kind, HeapPollutionCheck.Issue.Unacknowledged.toString)
    // NOT emission-affecting: applying it changes no type, no parameter and no body, so two modules
    // choosing differently cannot produce two ports that compile alone and fail together (§1.5).
    assert(!HeapPollutionCheck.Acknowledge.emissionAffecting)
    // …and it answers ONE kind. The two kinds here PARTITION the lane — an `Acknowledged` row is
    // java's own author's statement — so `alsoKinds` would erase the distinction they exist for.
    assertEquals(HeapPollutionCheck.Acknowledge.alsoKinds, Nil)
  }

  test("…and the run's own vocabulary carries it, so a `.conf` can tell a typo from a missing phase") {
    assert(balticporter.runner.PortRun.CheckRemedies.contains(HeapPollutionCheck))
    assert(RemedyVocabulary.from(balticporter.runner.PortRun.CheckRemedies).contains("acknowledge"))
  }

  // -------------------------------------------------------------------------------------------
  // the positive: one row LEAVES the lane and ARRIVES in `remediation(resolved)`
  // -------------------------------------------------------------------------------------------

  test("with no selections the lane is what it always was — the mechanism's no-op") {
    val p = program
    assertEquals(lane(p).sorted, List("Acknowledged com.demo.Vault#keepSafely",
                                      "Unacknowledged com.demo.Vault#keep"))
  }

  test("a selection DRAINS its row: the lane falls by exactly what `remediation(resolved)` gained") {
    val p            = program
    val (out, binder) = run(p, Map("com.demo.Vault#keep(T[])" -> "acknowledge"))
    val plan          = binder.resolutions
    // ONE row moved, and it is the one the key named.
    assertEquals(plan.all.size, 1)
    assertEquals(plan.all.map(_.subjectFqn), List("com.demo.Vault#keep"))
    assertEquals(plan.all.map(_.finding.check), List("remediation"))
    assertEquals(plan.all.map(_.finding.kind), List("resolved"))
    assert(clue(plan.all.head.finding.detail).contains("heap-pollution(Unacknowledged)"))
    // …and the lane no longer reports it. The other row is untouched: it is a different KIND, and
    // the port said nothing about it.
    assertEquals(lane(out, plan), List("Acknowledged com.demo.Vault#keepSafely"))
    // nothing is inert — the selection did what its author asked.
    assertEquals(plan.troubles, Nil)
  }

  test("…and the `decisions.tsv` row names the MANIFEST ENTRY, which is the string an agent edits") {
    val (_, binder) = run(program, Map("com.demo.Vault#keep(T[])" -> "acknowledge"))
    val d           = binder.resolutions.all.head.decision
    assertEquals(d.kind, Decision.Kind.SelectedRemedy)
    assertEquals(d.reason, Reason.Configured("resolutions", "com.demo.Vault#keep(T[])"))
    assertEquals(d.detail.get("remedy"), Some("acknowledge"))
    assertEquals(d.detail.get("drains"), Some("heap-pollution(Unacknowledged)"))
    // the porter note is what carries this to the reader at the emitted line (§4.575).
    assert(PorterNote.Rendered(Decision.Kind.SelectedRemedy))
  }

  test("the TREE is untouched — `acknowledge` is not emission-affecting, and that is structural") {
    val p   = program
    val out = run(p, Map("com.demo.Vault#keep(T[])" -> "acknowledge"))._1
    val before = p.definitionOf(p.symbols.all.find(_.fullName == "com.demo.Vault#keep").get.id)
    val after  = out.definitionOf(out.symbols.all.find(_.fullName == "com.demo.Vault#keep").get.id)
    assertEquals(after, before)
  }

  // -------------------------------------------------------------------------------------------
  // the negatives — each of them silent without a report
  // -------------------------------------------------------------------------------------------

  test("a remedy selected at a site whose finding is ANOTHER KIND applies nothing, and says so") {
    // `keepSafely` carries java's own `@SafeVarargs`, so its row is `Acknowledged` and `acknowledge`
    // does not answer it. Nothing drains, and the entry is INERT rather than wrong — reported as
    // `NeverApplied`, which is the state no binding can express: the key names a real declaration
    // this run owns and the remedy is live.
    val p             = program
    val (out, binder) = run(p, Map("com.demo.Vault#keepSafely(T[])" -> "acknowledge"))
    val plan          = binder.resolutions
    assertEquals(plan.all, Nil)
    assertEquals(lane(out, plan).sorted, List("Acknowledged com.demo.Vault#keepSafely",
                                              "Unacknowledged com.demo.Vault#keep"))
    val issues = PolicyReport.fromResolutions(plan.troubles).findings
    assertEquals(issues.map(_.issue), List(PolicyIssue.NeverApplied))
    assert(clue(issues.head.detail).contains("inert"))
    assert(clue(issues.head.detail).contains("heap-pollution(Unacknowledged)"))
  }

  test("…and one at a declaration with NO finding at all is the same report, not silence") {
    val (_, binder) = run(program, Map("com.demo.Vault#plain(int)" -> "acknowledge"))
    assertEquals(binder.resolutions.all, Nil)
    assertEquals(PolicyReport.fromResolutions(binder.resolutions.troubles).findings.map(_.issue),
                 List(PolicyIssue.NeverApplied))
  }

  test("a declaration this run does NOT emit is another module's row — D2 at the resolution ledger") {
    // A dependent's `Program` CONTAINS its base's units, so an inherited selection binds here too.
    // Applied, it would file a `remediation(resolved)` row about a declaration this module does not
    // write — the shape `ENGINE-LIMITS.md` D2 records five times on the reporting side.
    val p       = program
    val binder  = new PolicyBinder(p, p.members, RunScope.of(Set.empty, Map.empty))
    binder.resolving(ResolutionPlan.of(
      Map("com.demo.Vault#keep(T[])" -> "acknowledge"), vocabulary, vocabulary.byId.keySet, binder))
    Pipeline.runTraced(p, List(new HeapPollutionCheck.Apply), binder)
    assertEquals(binder.resolutions.all, Nil)
    assertEquals(PolicyReport.fromResolutions(binder.resolutions.troubles).findings.map(_.issue),
                 List(PolicyIssue.NeverApplied))
  }

  test("the DRAIN reads the ledger, so a refused selection keeps its finding") {
    // The check is handed a plan whose entry BOUND and never fired. Reading the manifest it would
    // drop the row; reading what the phase RECORDED it does not, which is the whole difference
    // between a resolution and a suppression.
    val p      = program
    val binder = new PolicyBinder(p, p.members)
    binder.resolving(ResolutionPlan.of(
      Map("com.demo.Vault#keep(T[])" -> "acknowledge"), vocabulary, vocabulary.byId.keySet, binder))
    assertEquals(lane(p, binder.resolutions).sorted,
                 List("Acknowledged com.demo.Vault#keepSafely", "Unacknowledged com.demo.Vault#keep"))
  }
