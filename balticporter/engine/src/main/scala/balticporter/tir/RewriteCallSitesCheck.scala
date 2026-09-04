package balticporter.tir

/** Reports retyping phases with no accounting lane or with a named lane that did not record.
  *
  * Reads `Pipeline.runTraced` observations (which phases moved owned declarations) and each
  * phase's `Rewrite.accountedBy`. Does NOT count seams (those are the four boundary checks'
  * job). One row per phase; §1(a) unparameterised. // ENGINE-LIMITS K5.6 */
object RewriteCallSitesCheck:

  /** The check's name in `findings.tsv`. */
  val Name = "rewrite-callsites"

  enum Issue:
    /** Phase moved declarations and claims no accounting lane. */
    case Unaccounted
    /** Phase names a lane that recorded nothing in this run. */
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

  /** One phase's unanswered question. `subject` is the phase name. */
  final case class Finding(issue: Issue, phase: String, detail: String):
    def render: String = s"$issue $phase — $detail"
    def report: CheckReport.Finding =
      CheckReport.Finding(Name, issue.toString, phase, "<pipeline>", 0, detail)

  /** Every retyping phase in this run that did not answer.
    * @param recorded check names that recorded in this run (`None` = artifact layer off, which
    *   disables the `UnwiredAccounting` lane but not `Unaccounted`).
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

  /** Grouped summary with §1 classification. The SCALE line is recomputed every run. */
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
