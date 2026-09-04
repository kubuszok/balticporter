package balticporter.testkit

import balticporter.core.{RuntimeMode, RuntimePlan}
import balticporter.emit.TirEmitter
import balticporter.frontend.spoon.SpoonTir
import balticporter.catalog.{CatalogLog, DiffId}
import balticporter.tir.{CheckReport, Decision, IdiomCandidate, IdiomKind, IdiomLog, IdiomVerdict,
  MemberIndex, Phase, Pipeline, PolicyBinder, Program, RemedySource, RemedyVocabulary, ResolutionPlan,
  RewriteLog, SymId, SymbolTable, Xref}

/** One Java snippet taken through the pipeline, with everything a test wants to assert on. */

/** @param sources the java the fixture parsed, `fileName -> code`. Handed to the emitter as
  *   its `javaSource`, since an in-memory snippet's `Origin.javaPath` names a file that
  *   does not exist.
  * @param decisions everything the phases RECORDED while they ran (`Pipeline.runTraced`).
  *   Carried rather than re-derived, since a decision is a value one run owns. */
final case class Ported(before: Program, after: Program, phases: List[Phase],
                        sources: Map[String, String] = Map.empty,
                        decisions: List[Decision] = Nil,
                        runtimeMode: RuntimeMode = RuntimeMode.Dependency,
                        catalog: CatalogLog = CatalogLog.discarding,
                        /** what each phase MOVED, observed by the pipeline (`balticporter.tir.Rewrite`). */
                        rewrites: RewriteLog = RewriteLog.discarding,
                        /** what each phase MOVED, observed by the pipeline
                          * (`balticporter.tir.Rewrite`) -- taken while the pipeline holds
                          * the symbol table on BOTH sides of a phase, so it cannot be
                          * re-derived from `before`/`after` alone once more than one phase
                          * ran. */
                        idioms: IdiomLog = IdiomLog.discarding,
                        /** what every `IdiomPhase` CONSIDERED, drained by the pipeline as
                          * it went (`balticporter.tir.IdiomLog`) -- the only surface a
                          * spec has for an idiom transform's REFUSALS, its whole safety
                          * argument. */
                        binder: PolicyBinder =
                          new PolicyBinder(new Program(Nil, SymbolTable(Nil), Xref.build(Nil), MemberIndex.empty),
                                           MemberIndex.empty)):

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

  /** the same program emitted in PREVIEW mode (`DESIGN.md` §7.4). A SECOND emitter over the
    * same tree, never a flag on the first: an emitter records its own source map and member
    * digests as it goes, so a shared instance would double-record. */
  lazy val previewEmitter: TirEmitter = emitterWith(preview = true)

  lazy val previewOut: String = previewEmitter.emit

  /** the same program emitted in BEST-EFFORT mode (`DESIGN.md` §6.4). */
  lazy val bestEffortEmitter: TirEmitter = emitterWith(bestEffort = true)

  lazy val bestEffortOut: String = bestEffortEmitter.emit

  /** the symbol id for a fully-qualified name, in whichever program. */
  def idIn(p: Program, fullName: String): Option[SymId] = p.symbols.all.find(_.fullName == fullName).map(_.id)
  def idBefore(fullName: String): Option[SymId]         = idIn(before, fullName)
  def idAfter(fullName: String): Option[SymId]          = idIn(after, fullName)

/** Run Java source through phases and get the emitted Scala back. */
object PortFixture:

  /** parse `java`, run `phases` over it, and hand back both programs plus the emitted Scala.
    * With no phases this is the emitter's own identity fixture — useful, and the honest baseline
    * for "did my phase change anything". */
  def port(java: String, phases: Phase*): Ported =
    portIn(RuntimeMode.Dependency, java, phases*)

  /** ...with the port having SELECTED a remedy at one or more locations
    * (`balticporter.core.PortManifest.resolutions`). The vocabulary is derived from the
    * phases handed in, so a fixture cannot select a remedy nothing offers. A spec wanting
    * the two to DIFFER builds the `ResolutionPlan` directly. */
  def portResolving(java: String, resolutions: Map[String, String], phases: Phase*): Ported =
    portIn(RuntimeMode.Dependency, java, resolutions, phases.toList)

  /** …for a port whose runtime delivery is not the default. See [[Ported.runtimeMode]]. */
  def portIn(mode: RuntimeMode, java: String, phases: Phase*): Ported =
    portIn(mode, java, Map.empty, phases.toList)

  private def portIn(mode: RuntimeMode, java: String, resolutions: Map[String, String],
                     phases: List[Phase]): Ported =
    // `fatal = true` — the TESTKIT is the mode where an undischarged obligation is an ERROR.
    // `DESIGN.md` §2.8 stages enforcement deliberately: a port run counts, because a run that died
    // on an incomplete rule produces no diagnostics at all, and a spec fails, because every
    // difference gets an edge-case suite and that suite is what the guarantee rests on.
    val catalog       = new CatalogLog(fatal = true)
    val rewrites      = new RewriteLog
    val idioms        = new IdiomLog
    val before        = SpoonTir.fromSource(java, catalog = catalog)
    val binder        = new PolicyBinder(before, before.members)
    // the miniature of what `PortRun` does: bind every selection through the run's own binder,
    // BEFORE the pipeline, and hand the plan to the phases through it.
    val vocabulary    = RemedyVocabulary.from(phases.collect { case r: RemedySource => r })
    binder.resolving(ResolutionPlan.of(resolutions, vocabulary, vocabulary.byId.keySet, binder))
    val (after, log)  = Pipeline.runTraced(before, phases, binder, catalog, rewrites, idioms)
    Ported(before, after, phases, Map("Snippet.java" -> java), log.all, mode, catalog, rewrites,
           idioms, binder)

  /** the same over SEVERAL compilation units, each `fileName -> code`. A Java file declares exactly
    * one package, so every rule about a PACKAGE BOUNDARY — default access, `protected`, an override
    * that crosses one — is untestable from a single snippet. */
  def portAll(sources: List[(String, String)], phases: Phase*): Ported =
    portAllIn(RuntimeMode.Dependency, sources, phases*)

  /** …several units WITH selections, which is the shape a menu's own precondition needs.
    *
    * A `Remediator` chokepoint grade is decided by whether ANOTHER UNIT references the type, so the
    * difference between its two answers cannot be written in one snippet at all — the same reason
    * [[portAll]] exists for a package boundary. */
  def portAllResolving(sources: List[(String, String)], resolutions: Map[String, String],
                       phases: Phase*): Ported =
    portAllIn(RuntimeMode.Dependency, sources, resolutions, phases.toList)

  def portAllIn(mode: RuntimeMode, sources: List[(String, String)], phases: Phase*): Ported =
    portAllIn(mode, sources, Map.empty, phases.toList)

  private def portAllIn(mode: RuntimeMode, sources: List[(String, String)],
                        resolutions: Map[String, String], phases: List[Phase]): Ported =
    val catalog      = new CatalogLog(fatal = true)
    val rewrites     = new RewriteLog
    val idioms       = new IdiomLog
    val before       = SpoonTir.fromSources(sources, catalog = catalog)
    val binder       = new PolicyBinder(before, before.members)
    val vocabulary   = RemedyVocabulary.from(phases.collect { case r: RemedySource => r })
    binder.resolving(ResolutionPlan.of(resolutions, vocabulary, vocabulary.byId.keySet, binder))
    val (after, log) = Pipeline.runTraced(before, phases, binder, catalog, rewrites, idioms)
    Ported(before, after, phases, sources.toMap, log.all, mode, catalog, rewrites, idioms,
           binder)

  /** parse only -- for tests about the FRONTEND rather than about a phase. `fatal = true`,
    * exactly as [[portIn]]/[[portAllIn]], and here one step stronger: an undischarged
    * obligation is a LOWERING ARM that returned without consulting an attached difference,
    * so a frontend-only spec is the closest witness to it. */
  def parse(java: String): Program = parseWith(java)._1

  /** …and the LOG beside it, so "is this path fatal" is a question a spec can ask. The three
    * entry points differ by one constructor argument and nothing reports a disagreement between
    * them; this is what makes the next one visible. */
  def parseWith(java: String): (Program, CatalogLog) =
    val catalog = new CatalogLog(fatal = true)
    (SpoonTir.fromSource(java, catalog = catalog), catalog)

/** MUnit base class for suites that port a snippet and assert on the result. */
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

  // STRUCTURAL assertions. The four above all read EMITTED TEXT -- the assertion a spec
  // reaches for when it has nothing better. Three facts are not in the text at all: which
  // non-mechanical DECISION the engine recorded, which FINDING a check produced, and what
  // the engine REFUSED to render (preview mode).

  /** a decision of `kind` was RECORDED by the phases that ran. `about` is matched as a
    * substring of `subjectFqn` -- the name the subject had AT DECISION TIME, the only form
    * that survives a later rename. Empty matches any subject. Asserts only that the engine
    * recorded the decision, never that a porter note reached the code
    * (`NoteCoverageCheck` holds both). */
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

  // IDIOM candidates -- the surface an idiom transform's SAFETY ARGUMENT is asserted on. Its
  // licence is not a suite result but a REFUSAL ENUMERATION: every behavioural difference is
  // made impossible by a guard, made impossible by the emitted shape, or COUNTED. A spec
  // asserting only emitted text would see the conversions and nothing the phase declined.

  /** the phase CONVERTED a site of `kind`, at a subject containing `about`. */
  def assertIdiomConverts(p: Ported, kind: IdiomKind, about: String = "")(using munit.Location): Unit =
    if !p.idioms.all.exists(c => c.kind == kind && c.verdict == IdiomVerdict.Converted &&
                                 c.subject.contains(about)) then
      fail(s"no $kind CONVERSION${label(about)} was filed\n${renderIdioms(p)}")

  /** …and DECLINED one, naming the guard. `guard` is matched exactly, because the guard IS the
    * classification a reader acts on: a substring match would let a spec pass on a different
    * refusal that happens to share a prefix. */
  def assertIdiomRefuses(p: Ported, kind: IdiomKind, guard: String, about: String = "")
                        (using munit.Location): Unit =
    if !p.idioms.all.exists(c => c.kind == kind && c.subject.contains(about) && (c.verdict match
          case IdiomVerdict.Refused(g, _) => g == guard
          case _                          => false)) then
      fail(s"no $kind REFUSAL under guard `$guard`${label(about)} was filed\n${renderIdioms(p)}")

  /** the site was CONSIDERED at all — the denominator half, and the one a spec needs to prove a
    * census is not silently skipping a shape. A site nothing filed is invisible to both assertions
    * above, and "no conversion" and "never looked" are the two answers those cannot tell apart. */
  def assertIdiomConsiders(p: Ported, kind: IdiomKind, about: String = "")(using munit.Location): Unit =
    if !p.idioms.all.exists(c => c.kind == kind && c.subject.contains(about)) then
      fail(s"no $kind candidate${label(about)} was filed at all\n${renderIdioms(p)}")

  /** …and the complement: nothing of `kind` was filed about this subject. */
  def assertIdiomIgnores(p: Ported, kind: IdiomKind, about: String)(using munit.Location): Unit =
    val hits = p.idioms.all.filter(c => c.kind == kind && c.subject.contains(about))
    if hits.nonEmpty then
      fail(s"$kind candidates were filed and should not have been:\n" +
        hits.map("  " + _.render).mkString("\n"))

  private def label(about: String): String =
    if about.isEmpty then "" else s" at a subject containing '$about'"

  private def renderIdioms(p: Ported): String =
    if p.idioms.isEmpty then "--- 0 idiom candidate(s) ---"
    else s"--- ${p.idioms.size} idiom candidate(s) ---\n" +
      p.idioms.all.map("  " + _.render).mkString("\n") + "\n---------------"

  private def renderDecisions(p: Ported): String =
    if p.decisions.isEmpty then "  (none)" else p.decisions.map("  " + render(_)).mkString("\n")

  private def render(d: Decision): String =
    s"${d.kind} ${d.subjectFqn} [${d.reason.className}=${d.reason.detail}] " +
      d.detail.toList.sorted.map((k, v) => s"$k=$v").mkString(" ")

  /** a check produced a finding of `kind`, optionally whose `detail` contains `detail`. */
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

  /** a catalog row was CONSULTED while this fixture was lowered -- the structural assertion
    * for `DESIGN.md` §2.8's obligation surfaces. Asserts that the engine CONSIDERED the
    * difference at this construct, which `assertEmits` cannot: a string can be present for
    * many reasons, including a lowering that produced the right text without asking. */
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

  /** THE LOG WITH BOTH SURFACES IN IT -- forcing the emission first, then reading. Only the
    * frontend's consults have run by the time a fixture is constructed; the EMITTER's
    * consults run lazily on `out`, so reading the log directly would report every
    * `Attaches.Rendered` row as never consulted. */
  private def reached(p: Ported): CatalogLog =
    val _ = p.out
    p.catalog

  /** a catalog row was CITED by a PHASE, at a declaration whose name contains `about`.
    *
    * The phase surface's assertion, and deliberately a different one: a citation is weaker than an
    * obligation — nothing can assert that a phase *should have* considered a difference at a
    * declaration it never visited — so a suite must not be able to spell the two the same way. */
  def assertCites(p: Ported, id: DiffId, about: String = "")(using munit.Location): Unit =
    // …through `reached`, which is the very thing its own doc warns about one assertion up: a
    // citation is not only a PHASE's any more. `TirEmitter`'s whole-program renaming passes cite
    // JS-C04 and JS-C46, and they run when the EMITTER is constructed — which `Ported.out` does,
    // lazily. Read straight off `p.catalog` this assertion reported every one of them as absent.
    val at = reached(p).citedAt(id)
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
