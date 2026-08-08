package balticporter.tir

import balticporter.catalog.{CatalogLog, FixKind}
import balticporter.core.{PolicyIssue, PolicyReport}
import balticporter.frontend.spoon.SpoonTir

/** PER-LOCATION REMEDY SELECTION, end to end and at each of its refusals.
  *
  * The positive half is one property — a port names a member and a remedy id, and the phase that
  * declared that remedy is handed the selection at that declaration — and the rest of this file is
  * the NEGATIVES, because every one of them is silent by default. A selection that binds to nothing,
  * names a remedy nobody offers, names one whose phase is not enabled, or names a real one at a site
  * the finding never occurred at, all produce the same emitted text as a port that asked for nothing:
  * no compile error, no moved digest, no check count. Each is a classified policy finding here for
  * the reason `PolicyReport` exists at all.
  */
class ResolutionSpec extends munit.FunSuite:

  private val Java =
    """package com.demo;
      |public class Widget {
      |  public String label() { return "w"; }
      |  public String label(int n) { return "w" + n; }
      |  public int size() { return 1; }
      |  public String show() { return new StringBuilder().append(size()).toString(); }
      |}""".stripMargin

  private def program: Program = SpoonTir.fromSource(Java, catalog = CatalogLog.discarding)

  private def planFor(
      program: Program,
      declared: Map[String, String],
      known: RemedyVocabulary,
      active: Set[String],
  ): (ResolutionPlan, PolicyBinder) =
    val binder = new PolicyBinder(program, program.members)
    val plan   = ResolutionPlan.of(declared, known, active, binder)
    binder.resolving(plan)
    (plan, binder)

  private val vocabulary = RemedyVocabulary.from(List(new SpecRemedyPhase))

  // -------------------------------------------------------------------------------------------
  // the vocabulary — a remedy id is PUBLISHED API
  // -------------------------------------------------------------------------------------------

  test("two DIFFERENT remedies claiming one id is refused, naming both declarers") {
    val a = new SpecRemedyPhase()
    val b = new SpecRemedyPhase(remedy = SpecRemedyPhase.Noop.copy(what = "something else"))
    val e = intercept[ConfigError](RemedyVocabulary.from(List(a, b)))
    assert(clue(e.why).contains("exactly one may answer to it"))
    assert(clue(e.where).contains("spec-noop"))
  }

  test("…and the SAME remedy declared twice is ONE entry, not a duplicate") {
    // the shape `TransformFactory.remedies` has by construction: the factory speaks for the phase,
    // and the phase says the same thing.
    val v = RemedyVocabulary.declared("factory", List(SpecRemedyPhase.Noop)) ++
      RemedyVocabulary.from(List(new SpecRemedyPhase))
    assertEquals(v.ids, List("spec-noop"))
  }

  test("an id outside the kebab grammar is refused where it is DECLARED, not where it is used") {
    val bad = new SpecRemedyPhase(remedy = SpecRemedyPhase.Noop.copy(id = "Spec_Noop"))
    val e   = intercept[ConfigError](RemedyVocabulary.from(List(bad)))
    assert(clue(e.why).contains("lower-case kebab"))
  }

  // -------------------------------------------------------------------------------------------
  // the positive: a selection reaches the phase that declared the remedy
  // -------------------------------------------------------------------------------------------

  test("a selection binds to the declaration it names and the phase is handed it") {
    val p            = program
    val (plan, _)    = planFor(p, Map("com.demo.Widget#size" -> "spec-noop"), vocabulary, vocabulary.byId.keySet)
    val size         = p.symbols.all.find(_.fullName == "com.demo.Widget#size").get
    val chosen       = plan.selected(size.id, SpecRemedyPhase.Lane, SpecRemedyPhase.Kind)
    assertEquals(chosen.map(_.remedy.id), Some("spec-noop"))
    assertEquals(chosen.map(_.declaredKey), Some("com.demo.Widget#size"))
    // asking is not applying: the entry is still INERT until something records that it fired, which
    // is the whole reason `selected` and `record` are two calls (a caller may ask and then refuse
    // for a reason of its own, and a plan that counted the question would report a resolution that
    // never happened).
    assertEquals(plan.troubles.map(_.issue), List(ResolutionPlan.Issue.NeverApplied))
  }

  test("a remedy answers ONE kind by default — the shape every declarer had before `alsoKinds`") {
    assert(SpecRemedyPhase.Noop.answers(SpecRemedyPhase.Lane, SpecRemedyPhase.Kind))
    assert(!SpecRemedyPhase.Noop.answers(SpecRemedyPhase.Lane, "second-kind"))
    assertEquals(SpecRemedyPhase.Noop.target, "spec-lane(spec-kind)")
  }

  test("…and a remedy for a lane that splits ONE SITE answers every kind it named — never a lane") {
    // The `overload-risk` shape: JLS 15.12.2's three phase boundaries are three rows about ONE
    // candidate set, and one act answers all of them. Per-member keys mean the alternative is a
    // member that can drain only one of its own rows.
    val wide = SpecRemedyPhase.Noop.copy(alsoKinds = List("second-kind"))
    assert(wide.answers(SpecRemedyPhase.Lane, SpecRemedyPhase.Kind))
    assert(wide.answers(SpecRemedyPhase.Lane, "second-kind"))
    assert(!wide.answers(SpecRemedyPhase.Lane, "third-kind"))
    // it does NOT widen across lanes: the drained lane must fall by exactly what `resolved` gained.
    assert(!wide.answers("another-lane", "second-kind"))
    assertEquals(wide.target, "spec-lane(spec-kind|second-kind)")
  }

  test("…and the PLAN narrows through the same predicate, so a widened remedy reaches both callers") {
    val p     = program
    val wide  = new SpecRemedyPhase(remedy = SpecRemedyPhase.Noop.copy(alsoKinds = List("second-kind")))
    val vocab = RemedyVocabulary.from(List(wide))
    val (plan, _) = planFor(p, Map("com.demo.Widget#size" -> "spec-noop"), vocab, vocab.byId.keySet)
    val size      = p.symbols.all.find(_.fullName == "com.demo.Widget#size").get
    assertEquals(plan.selected(size.id, SpecRemedyPhase.Lane, SpecRemedyPhase.Kind).map(_.remedy.id),
                 Some("spec-noop"))
    assertEquals(plan.selected(size.id, SpecRemedyPhase.Lane, "second-kind").map(_.remedy.id),
                 Some("spec-noop"))
    assertEquals(plan.selected(size.id, SpecRemedyPhase.Lane, "third-kind"), scala.None)
  }

  test("the DRAIN reads what a phase RECORDED, never what the manifest declared") {
    val p         = program
    val (plan, _) = planFor(p, Map("com.demo.Widget#size" -> "spec-noop"), vocabulary, vocabulary.byId.keySet)
    val size      = p.symbols.all.find(_.fullName == "com.demo.Widget#size").get
    val here      = Origin("Widget.java", 4, 3)
    val elsewhere = Origin("Widget.java", 9, 3)
    // declared and bound, and nothing has applied it: the check that mints the lane must still
    // report the row, or a refused selection would silently erase a residue.
    assert(!plan.appliedAt(size.id, SpecRemedyPhase.Lane, SpecRemedyPhase.Kind, here))
    plan.applied(plan.selected(size.id, SpecRemedyPhase.Lane, SpecRemedyPhase.Kind).get,
                 "com.demo.Widget#size", size.id, here, "applied")
    assert(plan.appliedAt(size.id, SpecRemedyPhase.Lane, SpecRemedyPhase.Kind, here))
    // …at THIS declaration, this lane and THIS SITE only. The site is what keeps a broadcast
    // selection honest: a remedy may refuse at one site of a member and apply at another, and a
    // per-declaration drain would empty the lane by more than `resolved` gained.
    assert(!plan.appliedAt(size.id, SpecRemedyPhase.Lane, SpecRemedyPhase.Kind, elsewhere))
    assert(!plan.appliedAt(SymId.None, SpecRemedyPhase.Lane, SpecRemedyPhase.Kind, here))
    assert(!plan.appliedAt(size.id, "another-lane", SpecRemedyPhase.Kind, here))
    assert(!plan.appliedAt(size.id, SpecRemedyPhase.Lane, "another-kind", here))
  }

  test("…and it is NOT handed to a caller asking about another lane or kind") {
    val p         = program
    val (plan, _) = planFor(p, Map("com.demo.Widget#size" -> "spec-noop"), vocabulary, vocabulary.byId.keySet)
    val size      = p.symbols.all.find(_.fullName == "com.demo.Widget#size").get
    assertEquals(plan.selected(size.id, "another-lane", SpecRemedyPhase.Kind), scala.None)
    assertEquals(plan.selected(size.id, SpecRemedyPhase.Lane, "another-kind"), scala.None)
  }

  test("a selection BROADCASTS: one key answers every site in the member it names") {
    val p         = program
    val (plan, _) = planFor(p, Map("com.demo.Widget#size" -> "spec-noop"), vocabulary, vocabulary.byId.keySet)
    val size      = p.symbols.all.find(_.fullName == "com.demo.Widget#size").get
    val first     = plan.selected(size.id, SpecRemedyPhase.Lane, SpecRemedyPhase.Kind).get
    val second    = plan.selected(size.id, SpecRemedyPhase.Lane, SpecRemedyPhase.Kind).get
    assertEquals(first, second)
    plan.applied(first, "com.demo.Widget#size", size.id, Origin.synthetic, "site 1")
    plan.applied(second, "com.demo.Widget#size", size.id, Origin.synthetic, "site 2")
    // two sites, two ledger rows, one key — which is what makes the count and the denominator
    // comparable at all.
    assertEquals(plan.all.size, 2)
    assertEquals(plan.all.map(_.what), List("site 1", "site 2"))
  }

  // -------------------------------------------------------------------------------------------
  // the refusals
  // -------------------------------------------------------------------------------------------

  test("an UNKNOWN remedy id is a malformed policy entry, never a typo'd member") {
    val p         = program
    val (plan, _) = planFor(p, Map("com.demo.Widget#size" -> "no-such-remedy"), vocabulary, vocabulary.byId.keySet)
    val issues    = PolicyReport.fromResolutions(plan.troubles).findings
    assertEquals(issues.map(_.issue), List(PolicyIssue.Malformed))
    assertEquals(issues.map(_.key), List("com.demo.Widget#size"))
    assert(clue(issues.head.detail).contains("not a remedy this engine knows"))
  }

  test("a KNOWN remedy whose declaring phase is not in this run names the phase to enable") {
    val p = program
    // the KNOWN set holds it; the ACTIVE set does not — the two sets `RemedyVocabulary` exists for.
    val (plan, _) = planFor(p, Map("com.demo.Widget#size" -> "spec-noop"), vocabulary, Set.empty)
    val issues    = PolicyReport.fromResolutions(plan.troubles).findings
    assertEquals(issues.map(_.issue), List(PolicyIssue.NeverMatched))
    assert(clue(issues.head.detail).contains("SpecRemedyPhase"))
    assert(clue(issues.head.detail).contains("not in this run's pipeline"))
  }

  test("a selection that BOUND and was never applied is its own issue — not `never matched`") {
    val p         = program
    val (plan, _) = planFor(p, Map("com.demo.Widget#size" -> "spec-noop"), vocabulary, vocabulary.byId.keySet)
    // nothing applies it: the key is right, the remedy is live, the finding did not occur.
    val issues = PolicyReport.fromResolutions(plan.troubles).findings
    assertEquals(issues.map(_.issue), List(PolicyIssue.NeverApplied))
    assert(clue(issues.head.detail).contains("inert"))
    // …and it goes away the moment something applies it.
    val size = p.symbols.all.find(_.fullName == "com.demo.Widget#size").get
    plan.applied(plan.selected(size.id, SpecRemedyPhase.Lane, SpecRemedyPhase.Kind).get,
                 "com.demo.Widget#size", size.id, Origin.synthetic, "applied")
    assertEquals(plan.troubles, Nil)
  }

  test("a BARE key naming two overloads is Ambiguous, with both candidates listed") {
    val p              = program
    val (_, binder)    = planFor(p, Map("com.demo.Widget#label" -> "spec-noop"), vocabulary, vocabulary.byId.keySet)
    val issues         = PolicyReport.fromBindings(binder.bindings).findings
      .filter(_.phase == Resolution.Seam)
    assertEquals(issues.map(_.issue), List(PolicyIssue.Unverifiable))
    assert(clue(issues.head.detail).contains("com.demo.Widget#label(int)"))
  }

  test("…and the same key with a descriptor binds to exactly one of them") {
    val p         = program
    val (plan, _) = planFor(p, Map("com.demo.Widget#label(int)" -> "spec-noop"), vocabulary, vocabulary.byId.keySet)
    val target    = plan.entries.head.target
    assert(clue(target).isDefined)
    assertEquals(plan.selected(target.get, SpecRemedyPhase.Lane, SpecRemedyPhase.Kind).map(_.remedy.id),
                 Some("spec-noop"))
  }

  test("a key naming nothing is NeverMatched, through the binder like every other policy key") {
    val p           = program
    val (_, binder) = planFor(p, Map("com.demo.Widget#gone" -> "spec-noop"), vocabulary, vocabulary.byId.keySet)
    val issues      = PolicyReport.fromBindings(binder.bindings).findings.filter(_.phase == Resolution.Seam)
    assertEquals(issues.map(_.issue), List(PolicyIssue.NeverMatched))
  }

  test("a MALFORMED key is refused by the binder's own grammar, not by a second parser") {
    val p           = program
    val (_, binder) = planFor(p, Map("com.demo.Widget" -> "spec-noop"), vocabulary, vocabulary.byId.keySet)
    val issues      = PolicyReport.fromBindings(binder.bindings).findings.filter(_.phase == Resolution.Seam)
    assertEquals(issues.map(_.issue), List(PolicyIssue.Malformed))
    assert(clue(issues.head.detail).contains("no `#`"))
  }

  // -------------------------------------------------------------------------------------------
  // the SUBJECT KIND — which seam binds the key is the REMEDY's answer (`Remedy.Subject`)
  // -------------------------------------------------------------------------------------------

  private val typeRemedy = SpecRemedyPhase.Noop.copy(
    id = "spec-type", subject = Remedy.Subject.OwnedType, what = "a residue that is a fact about the TYPE")
  private val externalRemedy = SpecRemedyPhase.Noop.copy(
    id = "spec-external", subject = Remedy.Subject.ExternalMember,
    what = "a residue whose subject is a member this program REFERENCES")

  private def vocabOf(rs: Remedy*): RemedyVocabulary = RemedyVocabulary.declared("spec", rs.toList)

  test("a TYPE-subject remedy binds a BARE FQN — the key a member grammar calls malformed") {
    val p    = program
    val v    = vocabOf(typeRemedy)
    val (plan, binder) = planFor(p, Map("com.demo.Widget" -> "spec-type"), v, v.byId.keySet)
    val widget = p.symbols.all.find(_.fullName == "com.demo.Widget").get
    assertEquals(plan.entries.head.target, Some(widget.id))
    assertEquals(plan.selected(widget.id, SpecRemedyPhase.Lane, SpecRemedyPhase.Kind).map(_.remedy.id),
                 Some("spec-type"))
    // …and it carries NO `MemberKey`, because a type key is not one and fabricating a member name
    // for it would be a value the reader could not tell from a real answer (§4.6).
    assertEquals(plan.entries.head.resolution.flatMap(_.key), scala.None)
    assertEquals(PolicyReport.fromBindings(binder.bindings).findings.filter(_.phase == Resolution.Seam), Nil)
  }

  test("…and the SAME key under an OwnedMember remedy is Malformed, in the member seam's words") {
    val p           = program
    val (_, binder) = planFor(p, Map("com.demo.Widget" -> "spec-noop"), vocabulary, vocabulary.byId.keySet)
    val issues      = PolicyReport.fromBindings(binder.bindings).findings.filter(_.phase == Resolution.Seam)
    assertEquals(issues.map(_.issue), List(PolicyIssue.Malformed))
  }

  test("an EXTERNAL-subject remedy binds a callee this program references and does not declare") {
    val p = program
    val v = vocabOf(externalRemedy)
    val (plan, _) = planFor(p, Map("java.lang.StringBuilder#append" -> "spec-external"), v, v.byId.keySet)
    val target = plan.entries.head.target
    assert(clue(target).isDefined)
    assert(!p.owns(target.get))
    assertEquals(plan.selected(target.get, SpecRemedyPhase.Lane, SpecRemedyPhase.Kind).map(_.remedy.id),
                 Some("spec-external"))
  }

  test("…and the same key under an OwnedMember remedy is ExternalOnly, which is the RIGHT refusal there") {
    val p           = program
    val (_, binder) = planFor(p, Map("java.lang.StringBuilder#append" -> "spec-noop"),
                              vocabulary, vocabulary.byId.keySet)
    // `ExternalOnly` renders as `NeverMatched` — from the manifest's point of view the entry did
    // nothing — and the DETAIL is what tells the two apart, which is exactly why the subject kind
    // has to be declared rather than guessed: this sentence would be a lie about an egress row.
    val issues      = PolicyReport.fromBindings(binder.bindings).findings.filter(_.phase == Resolution.Seam)
    assertEquals(issues.map(_.issue), List(PolicyIssue.NeverMatched))
    assert(clue(issues.head.detail).contains("does not DECLARE"))
  }

  // -------------------------------------------------------------------------------------------
  // the DRAIN — a resolution is a MOVE, and both halves come from one traversal (CLAUDE.md §5)
  // -------------------------------------------------------------------------------------------

  private case class Row(kind: String, at: SymId, subject: String)

  private def drainRows(plan: ResolutionPlan, rows: List[Row]): List[Row] =
    plan.drain(SpecRemedyPhase.Lane, rows)(r =>
      ResolutionPlan.Residue(r.kind, r.at, r.subject, Origin.synthetic, s"accepted ${r.subject}"))

  test("draining moves the selected rows OUT of the lane and INTO the ledger, by exactly that count") {
    val p         = program
    val (plan, _) = planFor(p, Map("com.demo.Widget#size" -> "spec-noop"), vocabulary, vocabulary.byId.keySet)
    val size      = p.symbols.all.find(_.fullName == "com.demo.Widget#size").get
    val label     = p.symbols.all.find(_.fullName.startsWith("com.demo.Widget#label")).get
    val rows = List(Row(SpecRemedyPhase.Kind, size.id, "a"), Row(SpecRemedyPhase.Kind, size.id, "b"),
                    Row(SpecRemedyPhase.Kind, label.id, "c"))
    val kept = drainRows(plan, rows)
    // BROADCAST: one key, two sites in the member it names — and the third row is another member's.
    assertEquals(kept.map(_.subject), List("c"))
    assertEquals(plan.all.map(_.subjectFqn), List("a", "b"))
    assertEquals(plan.all.map(_.finding.check).distinct, List("remediation"))
    assertEquals(plan.all.map(_.finding.kind).distinct, List("resolved"))
    assertEquals(plan.troubles, Nil)
  }

  test("…and it does NOT fire at a row of a kind the remedy does not serve") {
    val p         = program
    val (plan, _) = planFor(p, Map("com.demo.Widget#size" -> "spec-noop"), vocabulary, vocabulary.byId.keySet)
    val size      = p.symbols.all.find(_.fullName == "com.demo.Widget#size").get
    val kept      = drainRows(plan, List(Row("another-kind", size.id, "a")))
    assertEquals(kept.map(_.subject), List("a"))
    assertEquals(plan.all, Nil)
    // the entry bound, the remedy is live, and the finding it answers never occurred HERE — which is
    // the third staleness state and not a typo.
    assertEquals(plan.troubles.map(_.issue), List(ResolutionPlan.Issue.NeverApplied))
  }

  test("…and an EMPTY plan is the identity, decided from the plan's own state and not the rows") {
    val kept = drainRows(ResolutionPlan.empty, List(Row(SpecRemedyPhase.Kind, SymId.None, "a")))
    assertEquals(kept.map(_.subject), List("a"))
    assertEquals(ResolutionPlan.empty.all, Nil)
  }

  // -------------------------------------------------------------------------------------------
  // the ledger's two artifacts — ONE value, so they cannot disagree
  // -------------------------------------------------------------------------------------------

  test("an applied resolution files under `remediation` with kind `resolved`, naming its lane") {
    val a = AppliedResolution(
      Resolution("com.demo.Widget#size", Some(MemberKey.of("com.demo.Widget#size")), SymId.None,
                 SpecRemedyPhase.Noop),
      "com.demo.Widget#size", SymId.None, Origin("Widget.java", 4, 3), "wrapped at the seam")
    assertEquals(a.finding.check, "remediation")
    assertEquals(a.finding.kind, "resolved")
    assertEquals(a.finding.owner, "com.demo.Widget#size")
    assertEquals(a.finding.line, 4)
    assert(clue(a.finding.detail).contains("spec-lane(spec-kind)"))
    assert(clue(a.finding.detail).contains("com.demo.Widget#size"))
  }

  test("…and one `decisions.tsv` row whose reason is the MANIFEST ENTRY, not the remedy's own kind") {
    val a = AppliedResolution(
      Resolution("com.demo.Widget#size", Some(MemberKey.of("com.demo.Widget#size")), SymId.None,
                 SpecRemedyPhase.Noop),
      "com.demo.Widget#size", SymId.None, Origin("Widget.java", 4, 3), "wrapped at the seam")
    assertEquals(a.decision.kind, Decision.Kind.SelectedRemedy)
    assertEquals(a.decision.reason, Reason.Configured("resolutions", "com.demo.Widget#size"))
    assertEquals(a.decision.detail.get("remedy"), Some("spec-noop"))
    // …and WHOSE code carried it out, which the reason deliberately does not say.
    assertEquals(a.decision.detail.get("owner"), Some(FixKind.Parameterised.section))
  }

  test("a menu choice carries a PORTER NOTE — the reader at the line is told there was a menu") {
    assert(PorterNote.Rendered(Decision.Kind.SelectedRemedy))
    assert(PorterNote.AtDeclaration(Decision.Kind.SelectedRemedy))
  }

  // -------------------------------------------------------------------------------------------
  // through the PIPELINE, which is the seam a real phase uses
  // -------------------------------------------------------------------------------------------

  test("the pipeline hands a bound plan to the phase, and the phase's application is recorded") {
    val p      = program
    val phase  = new SpecRemedyPhase
    val vocab  = RemedyVocabulary.from(List(phase))
    val binder = new PolicyBinder(p, p.members)
    binder.resolving(ResolutionPlan.of(
      Map("com.demo.Widget#size" -> "spec-noop"), vocab, vocab.byId.keySet, binder))
    Pipeline.runTraced(p, List(phase), binder)
    assertEquals(binder.resolutions.all.map(_.subjectFqn), List("com.demo.Widget#size"))
    assertEquals(binder.resolutions.troubles, Nil)
    assertEquals(binder.resolutions.boundKeys, Set("com.demo.Widget#size"))
  }

  test("…and a phase not in the pipeline applies nothing, which is an INERT entry and not silence") {
    val p      = program
    val vocab  = RemedyVocabulary.from(List(new SpecRemedyPhase))
    val binder = new PolicyBinder(p, p.members)
    binder.resolving(ResolutionPlan.of(
      Map("com.demo.Widget#size" -> "spec-noop"), vocab, vocab.byId.keySet, binder))
    Pipeline.runTraced(p, Nil, binder)
    assertEquals(binder.resolutions.all, Nil)
    assertEquals(PolicyReport.fromResolutions(binder.resolutions.troubles).findings.map(_.issue),
                 List(PolicyIssue.NeverApplied))
  }
