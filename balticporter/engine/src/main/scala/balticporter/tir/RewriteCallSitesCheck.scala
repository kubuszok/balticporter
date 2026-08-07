package balticporter.tir

/** THE STANDING QUESTION EVERY RETYPING PHASE OWES, asked of the pipeline rather than of each phase.
  *
  * ==What it is for==
  * `CLAUDE.md` §1 requires a phase that retypes declarations to COUNT every seam it opened and could
  * not close, with the §1 classification a bare typer error cannot carry. Four phases answer it and
  * each answer was written after a port hit the wall; `ENGINE-LIMITS.md` K5.6 records what was left
  * open — *a NEW retyping phase can reintroduce this shape until it too asks the question* — and
  * nothing could see a phase that did not ask.
  *
  * This check sees it. `Pipeline.runTraced` observes, per phase, which owned declarations' `info`
  * that phase moved (`Patch.retyped`, derived, not declared), and a phase declares which check lane
  * counts its residue (`Rewrite.accountedBy`). Two things then become findings that were previously
  * invisible:
  *
  *   - a phase that MOVED declarations and claims NO lane — [[Issue.Unaccounted]]. The port compiles,
  *     every count is flat, and the seams arrive at whoever compiles it next as `Found: … /
  *     Required: …` with nobody to classify them;
  *   - a phase that claims a lane WHICH DID NOT RECORD in this run — [[Issue.UnwiredAccounting]].
  *     This is not hypothetical and it is not small: `PortRun`'s own history has `LibgdxTestMigrate`
  *     never calling `PortabilityCheck` at all, which is why `RequiredChecks` exists. `RequiredChecks`
  *     covers the lanes required of EVERY run; a lane that is required only when its phase is in the
  *     pipeline is exactly the one that set cannot speak for, and this is where it is asked instead.
  *
  * ==What it deliberately does NOT do==
  * It does not count seams. `§2.5`'s design had the generic form `usagesOf(s) \ callSites` — every
  * usage of a retyped declaration that the phase did not also rewrite — and that question, asked
  * generically, is not the four boundary counts and cannot become them: a position-blind retyping
  * moves the node type on BOTH sides of nearly every usage, so nearly every usage is fine, and what
  * makes a usage a SEAM is the type comparison the four checks already perform. Measured on the
  * largest port in this corpus: 1,077 declaration-moves with 3,045 recorded usages, against the 152
  * seams the four lanes count — a review list twenty times the size of the residue, every row of
  * which is a usage that is fine (`ENGINE-LIMITS.md` K5.10 carries both numbers, and the SCALE line
  * in [[summary]] re-derives them on every run so the claim cannot go stale). A second count of one
  * residue, free to disagree with the first, is the failure `DESIGN.md` §2.8 refused for the
  * catalog's own lanes and chunk 4's `markers` lane refused for refusals.
  *
  * So this is a check about the PIPELINE, one row per phase, and never about a declaration — which
  * is also why `ENGINE-LIMITS.md` D2's ownership filter does not apply to it: a dependent's finding
  * here is about the dependent's own phase list, which no base can act on and no base declares.
  *
  * §1(a), unparameterised: it holds no library's names and no phase's, and a pipeline with no
  * retyping phase produces an empty list by arithmetic rather than by a branch.
  */
object RewriteCallSitesCheck:

  /** the check's name in `findings.tsv`. */
  val Name = "rewrite-callsites"

  enum Issue:
    /** this phase moved owned declarations' types and names no lane that counts what it left open. */
    case Unaccounted
    /** this phase names a lane, and that lane recorded nothing in this run — so the claim is a
      * claim about a check that did not run. */
    case UnwiredAccounting

  object Issue:
    /** which of §1's three kinds the fix is — the thing a bare typer error cannot say. */
    def classification(i: Issue): String = i match
      case Unaccounted =>
        "§1(a) engine: this phase RETYPES declarations and no check counts the seams that creates. " +
          "A retyping is position-blind, so most slots move on both sides and the port compiles — " +
          "what is left is the slots where one side could not move (an external callee's class " +
          "file, a scoped-out declaration, a reified occurrence), and those reach whoever compiles " +
          "the port as bare `Found: … / Required: …` with no §1 classification, which CLAUDE.md " +
          "§4.45 names as the bulk of a new library's first wall. The fix is the one the four " +
          "existing retyping phases already took: count the residue in a check of this phase's own, " +
          "with a classification per issue kind, and name it in `Rewrite.accountedBy`. Where a " +
          "phase's retyping genuinely cannot strand anything, that is a claim worth writing down " +
          "AND counting at zero — a lane that reports 0 and a phase nobody instrumented are the " +
          "same silence otherwise (ENGINE-LIMITS K5.6)."
      case UnwiredAccounting =>
        "§1(a) engine WIRING, not a translation defect: this phase names a check lane that counts " +
          "its residue and that lane recorded NOTHING in this run — so either the check is not " +
          "called from `PortRun` on this port's path, or it is called under a condition this " +
          "pipeline does not meet. A number that reaches stdout and not `findings.tsv` fails the " +
          "run (`PortRun.RequiredChecks`); this is the same guarantee for a lane that is required " +
          "only when its phase is present, which `RequiredChecks` cannot express. Record the lane " +
          "unconditionally beside the phase's own block — an empty result recorded is a fact, an " +
          "absent row is not."

  /** one phase's unanswered question. `subject` is the PHASE name: this check is about the
    * pipeline, so a row names the phase a reader has to go and edit. */
  final case class Finding(issue: Issue, phase: String, detail: String):
    def render: String = s"$issue $phase — $detail"
    def report: CheckReport.Finding =
      CheckReport.Finding(Name, issue.toString, phase, "<pipeline>", 0, detail)

  /** Every retyping phase in this run that did not answer.
    *
    * `recorded` is the set of check names that reached `findings.tsv` in THIS run
    * (`CheckReport.snapshot().keySet`), which is why this check runs after the others and not
    * beside them: it asks whether a lane recorded, and a lane that has not been called yet has not.
    *
    * **`None` means the question CANNOT BE ASKED, and it is not the same as the empty set.**
    * `CheckReport.record` is a no-op when the artifact layer is off (§5.1: the gate is on the LAYER,
    * and a run with no derivable port identity has it off whatever the flags say), so `snapshot()`
    * is then empty for every lane — and read as an answer that would report EVERY accounted phase as
    * unwired, which is a finding manufactured by the diagnostic switch rather than by the program.
    * A testkit fixture and a §1(c) rule's own harness are exactly that case. The `Unaccounted` lane
    * is unaffected and still reported: it reads the pipeline's own observation, which the artifact
    * layer has nothing to do with.
    */
  def check(log: RewriteLog, recorded: Option[Set[String]]): List[Finding] =
    log.all.flatMap { p =>
      if !p.isAccounted then
        List(Finding(Issue.Unaccounted, p.phase,
          s"retyped ${p.retyped.size} owned declaration(s) and names no check lane"))
      else
        recorded.toList.flatMap(rec =>
          p.accountedBy.toList.sorted.filterNot(rec).map(lane =>
            Finding(Issue.UnwiredAccounting, p.phase,
              s"names lane '$lane', which recorded nothing in this run " +
                s"(it retyped ${p.retyped.size} owned declaration(s))")))
    }

  /** grouped one-line summary, worst family first, each with its §1 classification — a reader must
    * not have to work out who fixes it.
    *
    * The SCALE line is computed and not written down, for `PROGRESS.md` §12.3's reason: it is the
    * evidence for why this check counts phases and not usages, and a number restated in prose beside
    * the artifact that computes it is a number that goes stale silently. */
  def summary(fs: List[Finding], log: RewriteLog, program: Program): String =
    val moves  = log.all.map(_.retyped.size).sum
    val usages = log.all.map(p => p.retyped.toList.map(s => program.usagesOf(s).size).sum).sum
    val scale =
      s"  ${log.all.size} retyping phase(s), $moves declaration-move(s) with $usages recorded usage(s) — " +
        s"${log.all.count(_.isAccounted)} accounted"
    val body =
      if fs.isEmpty then "  none"
      else
        fs.groupBy(_.issue).toList.sortBy((_, v) => -v.size).map { (issue, vs) =>
          val head  = s"  ${vs.size} × $issue\n  ${Issue.classification(issue)}"
          val sites = vs.sortBy(_.phase).map("    " + _.render)
          (head :: sites).mkString("\n")
        }.mkString("\n")
    s"$scale\n$body"
