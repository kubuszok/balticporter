package balticporter.testkit

import balticporter.core.{RuntimeMode, RuntimePlan}
import balticporter.emit.TirEmitter
import balticporter.frontend.spoon.SpoonTir
import balticporter.catalog.{CatalogLog, DiffId}
import balticporter.tir.{CheckReport, Decision, Phase, Pipeline, Program, SymId}

/** One Java snippet taken through the pipeline, with everything a test wants to assert on.
  *
  * `before` and `after` are both kept because half the useful assertions are about the XREF —
  * "the old symbol is vacated, the new one inherited its positions" — and those need the two
  * programs side by side. `out` is the emitted Scala.
  */
/** @param sources the java the fixture parsed, `fileName -> code`. Handed to the emitter as its
  *   `javaSource`, because an in-memory snippet's `Origin.javaPath` names a file that does not
  *   exist — without it the comment-recovery backstop is the one behaviour no spec could reach,
  *   which is exactly the shape a fixture must not have.
  * @param decisions everything the phases RECORDED while they ran (`Pipeline.runTraced`). Carried
  *   rather than re-derived, because a decision is a value one run owns and the log is drained as
  *   the pipeline goes; a fixture that re-ran the phases to get it would be asserting about a
  *   second translation.
  * @param runtimeMode which delivery the fixture's [[plan]] is derived for. A parameter and not a
  *   constant: `RuntimeMode.Vendored` writes the support sources into the port instead of
  *   depending on them, so it is a different `RuntimePlan` and therefore a different set of
  *   `concreteMembers` reaching the emitter — a distinction no spec could reach while this was
  *   hard-coded. */
final case class Ported(before: Program, after: Program, phases: List[Phase],
                        sources: Map[String, String] = Map.empty,
                        decisions: List[Decision] = Nil,
                        runtimeMode: RuntimeMode = RuntimeMode.Dependency,
                        catalog: CatalogLog = CatalogLog.discarding):

  /** what the phases that ran require of `balticporter-runtime`. Derived, not passed: the fixture
    * is a miniature of the orchestrator, so a test exercises the same derivation a real port does
    * rather than a hand-assembled approximation of it. */
  lazy val plan: RuntimePlan = RuntimePlan.of(phases, runtimeMode)

  private def emitterWith(preview: Boolean = false, bestEffort: Boolean = false,
                          log: CatalogLog = CatalogLog.discarding): TirEmitter =
    new TirEmitter(after, plan.concreteMembers,
                   javaSource = path => sources.collectFirst { case (n, c) if path.endsWith(n) => c },
                   preview = preview, bestEffort = bestEffort, catalog = log)

  /** the SHIPPED emitter, and the only one holding the fixture's obligation log — the preview and
    * best-effort twins re-render the same tree, and a shared log would count every consult twice
    * (the same reason they do not share a source map). */
  lazy val emitter: TirEmitter = emitterWith(log = catalog)

  /** the emitted Scala for the whole program. */
  lazy val out: String = emitter.emit

  /** the same program emitted in PREVIEW mode (`DESIGN.md` §7.4).
    *
    * A SECOND emitter over the same tree, never a flag on the first: an emitter records its own
    * source map and its own member digests as it goes (`CLAUDE.md` §5.1), so re-emitting through
    * one instance would leave a spec asserting about a recording made twice. Two instances, two
    * recordings, and [[out]] stays exactly the text a real run would ship.
    *
    * This is the only fixture surface that can see the engine's emission-side REFUSALS at all:
    * under `preview = false` — the default every measure lane runs — an unrenderable site emits
    * its residue string and nothing marks it, so a spec written against [[out]] would be asserting
    * that a comment is present rather than that the engine refused.
    *
    * The EMITTER is exposed and not only its text, because what preview mode adds is a recorded
    * `Decision.Kind.Unrenderable` (`TirEmitter.emissionDecisions`) beside the rendered marker — and
    * a spec that could only grep for the marker would be back to a text assertion about the one
    * thing that exists to be structural. */
  lazy val previewEmitter: TirEmitter = emitterWith(preview = true)

  lazy val previewOut: String = previewEmitter.emit

  /** the same program emitted in BEST-EFFORT mode (`DESIGN.md` §6.4).
    *
    * A third emitter, for the reason the second one is a second: each records its own source map,
    * member digests and marker inventory. This is the fixture §6.4's standing claim needs — *at
    * zero open markers, best-effort output minus fences and banner is byte-identical to deliverable
    * output, by construction* — and a claim by construction still wants the one comparison that
    * would notice if the construction ever stopped holding. */
  lazy val bestEffortEmitter: TirEmitter = emitterWith(bestEffort = true)

  lazy val bestEffortOut: String = bestEffortEmitter.emit

  /** the symbol id for a fully-qualified name, in whichever program. */
  def idIn(p: Program, fullName: String): Option[SymId] = p.symbols.all.find(_.fullName == fullName).map(_.id)
  def idBefore(fullName: String): Option[SymId]         = idIn(before, fullName)
  def idAfter(fullName: String): Option[SymId]          = idIn(after, fullName)

/** Run Java source through phases and get the emitted Scala back.
  *
  * This is the four lines every engine spec opened with — parse, run, emit, look a symbol up by
  * name — and the reason it is a MODULE rather than a copied preamble is CLAUDE.md §4.45: the
  * consumer is an agent in another repository, writing tests for its own library's phases without
  * this repository's context. What it must not have to reinvent is how to hold the engine.
  */
object PortFixture:

  /** parse `java`, run `phases` over it, and hand back both programs plus the emitted Scala.
    * With no phases this is the emitter's own identity fixture — useful, and the honest baseline
    * for "did my phase change anything". */
  def port(java: String, phases: Phase*): Ported =
    portIn(RuntimeMode.Dependency, java, phases*)

  /** …for a port whose runtime delivery is not the default. See [[Ported.runtimeMode]]. */
  def portIn(mode: RuntimeMode, java: String, phases: Phase*): Ported =
    // `fatal = true` — the TESTKIT is the mode where an undischarged obligation is an ERROR.
    // `DESIGN.md` §2.8 stages enforcement deliberately: a port run counts, because a run that died
    // on an incomplete rule produces no diagnostics at all, and a spec fails, because every
    // difference gets an edge-case suite and that suite is what the guarantee rests on. A row the
    // registry itself calls `Open` or `Absent` is never fatal — it is the work list, and a mode
    // that died on it would make the work list unrunnable.
    val catalog       = new CatalogLog(fatal = true)
    val before        = SpoonTir.fromSource(java, catalog = catalog)
    val (after, log)  = Pipeline.runTraced(before, phases.toList,
                          new balticporter.tir.PolicyBinder(before, before.members), catalog)
    Ported(before, after, phases.toList, Map("Snippet.java" -> java), log.all, mode, catalog)

  /** the same over SEVERAL compilation units, each `fileName -> code`. A Java file declares exactly
    * one package, so every rule about a PACKAGE BOUNDARY — default access, `protected`, an override
    * that crosses one — is untestable from a single snippet. */
  def portAll(sources: List[(String, String)], phases: Phase*): Ported =
    portAllIn(RuntimeMode.Dependency, sources, phases*)

  def portAllIn(mode: RuntimeMode, sources: List[(String, String)], phases: Phase*): Ported =
    val catalog      = new CatalogLog(fatal = true)
    val before       = SpoonTir.fromSources(sources, catalog = catalog)
    val (after, log) = Pipeline.runTraced(before, phases.toList,
                         new balticporter.tir.PolicyBinder(before, before.members), catalog)
    Ported(before, after, phases.toList, sources.toMap, log.all, mode, catalog)

  /** parse only — for tests about the FRONTEND rather than about a phase.
    *
    * `fatal = true`, exactly as [[portIn]] and [[portAllIn]] are, and here the argument is one step
    * stronger than it is for them: an undischarged obligation is a LOWERING ARM that returned
    * without consulting a difference the catalog attaches to it, so a frontend-only spec is the
    * closest possible witness to it. This path took `CatalogLog.discarding` — the log for a caller
    * that does not want one — so the mode where a hole is an error was off in the one place it is
    * cheapest to see, and every spec here would have gone on passing over a lowering that had
    * stopped asking. */
  def parse(java: String): Program = parseWith(java)._1

  /** …and the LOG beside it, so "is this path fatal" is a question a spec can ask. The three
    * entry points differ by one constructor argument and nothing reports a disagreement between
    * them; this is what makes the next one visible. */
  def parseWith(java: String): (Program, CatalogLog) =
    val catalog = new CatalogLog(fatal = true)
    (SpoonTir.fromSource(java, catalog = catalog), catalog)

/** MUnit base class for suites that port a snippet and assert on the result.
  *
  * Extends `munit.FunSuite` rather than offering a mixin because that is how the corpus specs are
  * written and there is no second base to compose with. The assertions carry `munit.Location` so a
  * failure points at the caller's line, not at this file.
  */
abstract class PortSuite extends munit.FunSuite:

  def port(java: String, phases: Phase*): Ported = PortFixture.port(java, phases*)

  def portIn(mode: RuntimeMode, java: String, phases: Phase*): Ported = PortFixture.portIn(mode, java, phases*)

  def portAll(sources: List[(String, String)], phases: Phase*): Ported = PortFixture.portAll(sources, phases*)

  def portAllIn(mode: RuntimeMode, sources: List[(String, String)], phases: Phase*): Ported =
    PortFixture.portAllIn(mode, sources, phases*)

  /** the emitted Scala contains `snippet` — with the WHOLE output in the failure message, because
    * "expected substring not found" without the text is the single most expensive failure mode
    * when the emitter is what changed. */
  def assertEmits(p: Ported, snippet: String)(using munit.Location): Unit =
    if !p.out.contains(snippet) then
      fail(s"emitted Scala does not contain:\n  $snippet\n--- emitted ---\n${p.out}\n---------------")

  def assertNotEmits(p: Ported, snippet: String)(using munit.Location): Unit =
    if p.out.contains(snippet) then
      fail(s"emitted Scala still contains:\n  $snippet\n--- emitted ---\n${p.out}\n---------------")

  def assertEmitsMatch(p: Ported, regex: String)(using munit.Location): Unit =
    if !p.out.matches(s"(?s).*$regex.*") then
      fail(s"emitted Scala does not match:\n  $regex\n--- emitted ---\n${p.out}\n---------------")

  /** a type that the phase was supposed to retype AWAY is no longer referenced anywhere.
    *
    * The xref form of the assertion, not the textual one: a name can vanish from the output
    * because the emitter stopped printing it, which is a different (and worse) fact than the
    * symbol having no usages left. */
  def assertVacated(p: Ported, fullName: String)(using munit.Location): Unit =
    val id = p.idBefore(fullName).getOrElse(fail(s"$fullName is not in the pre-phase symbol table"))
    assert(p.before.usagesOf(id).nonEmpty, s"$fullName had no usages before the phase — the fixture proves nothing")
    assertEquals(p.after.usagesOf(id), Nil, s"$fullName is still used after the phase")

  // ---------------------------------------------------------------------------------------------
  // STRUCTURAL assertions.
  //
  // The four above all read EMITTED TEXT, and text is the assertion a spec reaches for when it has
  // nothing better — which is exactly how a rule comes to pass the corpus without being right
  // (`CLAUDE.md` §3). Three facts about a port are not in its text at all: which non-mechanical
  // DECISION the engine recorded (`decisions.tsv`, §4.575), which FINDING a check produced
  // (`findings.tsv`, `DESIGN.md` §6.3), and what the engine REFUSED to render (preview mode, §7.4).
  // A spec that cannot assert on those can only assert that some string is present, and a string is
  // present for many reasons.
  // ---------------------------------------------------------------------------------------------

  /** a decision of `kind` was RECORDED by the phases that ran.
    *
    * `about` is matched as a substring of `subjectFqn` — the name the subject had AT DECISION TIME,
    * which is the only form that survives a later rename (`Decision`'s own rule). Empty matches any
    * subject.
    *
    * Note what this asserts and what it does not: that the engine recorded the decision, never that
    * a porter note for it reached the code. Those are two directions of one contract and
    * `NoteCoverageCheck` is what holds both; a fixture asserting only the first would be satisfied
    * by a decision nobody can find from the emitted file. */
  def assertDecides(p: Ported, kind: Decision.Kind, about: String = "")(using munit.Location): Unit =
    if !p.decisions.exists(d => d.kind == kind && d.subjectFqn.contains(about)) then
      fail(s"no $kind decision${if about.isEmpty then "" else s" about a subject containing '$about'"}" +
        s" was recorded\n--- ${p.decisions.size} decision(s) ---\n${renderDecisions(p)}\n---------------")

  /** …and the other direction, which is the one a regression needs: an act the engine performed
    * SILENTLY is a decision missing from the log, and no text assertion can see it. */
  def assertNotDecides(p: Ported, kind: Decision.Kind, about: String = "")(using munit.Location): Unit =
    val hits = p.decisions.filter(d => d.kind == kind && d.subjectFqn.contains(about))
    if hits.nonEmpty then
      fail(s"$kind was recorded and should not have been:\n${hits.map("  " + render(_)).mkString("\n")}")

  private def renderDecisions(p: Ported): String =
    if p.decisions.isEmpty then "  (none)" else p.decisions.map("  " + render(_)).mkString("\n")

  private def render(d: Decision): String =
    s"${d.kind} ${d.subjectFqn} [${d.reason.className}=${d.reason.detail}] " +
      d.detail.toList.sorted.map((k, v) => s"$k=$v").mkString(" ")

  /** a check produced a finding of `kind`, optionally whose `detail` contains `detail`.
    *
    * The findings are passed in rather than derived, because a check is a plain object with its own
    * `check(program, …)` signature and no registry to look one up in — so the spec names the check
    * it is testing, which is also what makes the assertion readable at the call site:
    * `assertFinds(OmissionCheck.check(p.after), "dropped-anon-member")`. */
  def assertFinds(findings: Seq[CheckReport.Finding], kind: String, detail: String = "")(using munit.Location): Unit =
    if !findings.exists(f => f.kind == kind && f.detail.contains(detail)) then
      fail(s"no finding of kind '$kind'${if detail.isEmpty then "" else s" whose detail contains '$detail'"}" +
        s"\n--- ${findings.size} finding(s) ---\n${renderFindings(findings)}\n---------------")

  /** the check reported NOTHING.
    *
    * Kept as its own assertion rather than `assertEquals(fs.size, 0)` so the failure prints what
    * was found: a check-count assertion that fails on a number tells its reader to go and run the
    * check by hand, which is the diagnostic `CLAUDE.md` §5.1 exists to remove. */
  def assertNoFindings(findings: Seq[CheckReport.Finding])(using munit.Location): Unit =
    if findings.nonEmpty then
      fail(s"expected no findings, got ${findings.size}:\n${renderFindings(findings)}")

  private def renderFindings(fs: Seq[CheckReport.Finding]): String =
    if fs.isEmpty then "  (none)" else fs.map("  " + _.render).mkString("\n")

  /** a catalog row was CONSULTED while this fixture was lowered — the structural assertion for
    * `DESIGN.md` §2.8's obligation surfaces.
    *
    * The one an edge-case suite opens with, and it asserts something no text assertion can reach:
    * that the engine CONSIDERED the difference at this construct. `assertEmits` can only say that
    * some string is present, and a string is present for many reasons — including a lowering that
    * happened to produce the right text without ever asking the question, which is precisely the
    * shape that regresses the day an arm is rewritten.
    *
    * `fired` is the second half and is separate on purpose: a consult that is reached and never
    * applies is the normal state of most rows at most sites, and a suite for the difference's own
    * edge case wants to say the difference APPLIED here. `assertConsults(p, id, fired = true)` is
    * that; the default asserts only that the branch was live. */
  def assertConsults(p: Ported, id: DiffId, fired: Boolean = false)(using munit.Location): Unit =
    val n = reached(p).consulted(id)
    if n == 0 then
      fail(s"$id was never consulted while lowering this fixture" +
        s"\n--- rows reached ---\n${renderReached(p)}\n---------------")
    if fired && p.catalog.fired(id) == 0 then
      fail(s"$id was consulted $n time(s) and never APPLIED — the branch is live and the " +
        s"difference did not fire, which is the one thing a consult count cannot tell you")

  /** …and the direction a regression needs: the engine did NOT consider this difference here.
    *
    * Every assertion in this file is tested in both directions, and this is the one that makes the
    * positive form mean something — a fixture where every row reads as consulted is a fixture that
    * would pass with the wrapper wired to a constant. */
  def assertNotConsults(p: Ported, id: DiffId)(using munit.Location): Unit =
    val n = reached(p).consulted(id)
    if n != 0 then
      fail(s"$id was consulted $n time(s) and should not have been at this construct")

  /** THE LOG WITH BOTH SURFACES IN IT — forcing the emission first, then reading.
    *
    * There are two obligation dispatches and only one of them has run by the time a fixture is
    * constructed. The frontend's consults are recorded while `port(…)` parses; the EMITTER's are
    * recorded while `out` renders, and `out` is lazy — so an assertion that read the log directly
    * would report every `Attaches.Rendered` row as never consulted, in a suite whose whole subject
    * is that they are. Forcing here rather than in each assertion is the F8 shape avoided: one
    * function, so a fourth assertion added tomorrow cannot be the one that forgets. */
  private def reached(p: Ported): CatalogLog =
    val _ = p.out
    p.catalog

  /** a catalog row was CITED by a PHASE, at a declaration whose name contains `about`.
    *
    * The phase surface's assertion, and deliberately a different one: a citation is weaker than an
    * obligation — nothing can assert that a phase *should have* considered a difference at a
    * declaration it never visited — so a suite must not be able to spell the two the same way. */
  def assertCites(p: Ported, id: DiffId, about: String = "")(using munit.Location): Unit =
    val at = p.catalog.citedAt(id)
    if !at.exists(_.contains(about)) then
      fail(s"$id was not cited by any phase${if about.isEmpty then "" else s" at a declaration containing '$about'"}" +
        s"\n--- ${at.size} citation(s) ---\n${if at.isEmpty then "  (none)" else at.map("  " + _).mkString("\n")}\n---------------")

  private def renderReached(p: Ported): String =
    val rows = reached(p).rows.filter(r => r.consulted > 0 || r.declarations > 0)
    if rows.isEmpty then "  (none)"
    else rows.map(r => s"  ${r.id} consulted=${r.consulted} fired=${r.fired} declarations=${r.declarations}").mkString("\n")

  /** the PREVIEW emission (`DESIGN.md` §7.4) contains `snippet`.
    *
    * Separate from [[assertEmits]] and never a mode on it: preview is a DIAGNOSTIC rendering and
    * `Ported.out` is what a port would ship, so a spec that could flip one into the other would be
    * a spec that can assert about output no run produces. */
  def assertPreviewEmits(p: Ported, snippet: String)(using munit.Location): Unit =
    if !p.previewOut.contains(snippet) then
      fail(s"preview emission does not contain:\n  $snippet\n--- preview ---\n${p.previewOut}\n---------------")

  def assertPreviewNotEmits(p: Ported, snippet: String)(using munit.Location): Unit =
    if p.previewOut.contains(snippet) then
      fail(s"preview emission still contains:\n  $snippet\n--- preview ---\n${p.previewOut}\n---------------")
