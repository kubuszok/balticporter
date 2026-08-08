package balticporter.core

/** What a PARAMETERISED phase has to say about the POLICY it was handed — the CLAUDE.md §1(b)
  * half of a rule, supplied by the library's manifest rather than written into the engine.
  *
  * The MECHANISM of a (b) rule is engine code, and the engine's own tests cover it. Its POLICY is
  * a bag of strings the engine cannot type-check: a misspelled `dropTypes` entry, a wrapper class
  * renamed upstream, a redirect key naming a member that no longer exists. Each of those is a
  * NO-OP, and a no-op is invisible — the port emits, compiles, and quietly keeps the construct the
  * policy was written to remove. That is the same shape as every silent omission this engine has
  * been bitten by (CLAUDE.md §3), one level up: the omission is in the CONFIGURATION.
  *
  * The opposite failure is already covered — a drop that FIRED and left a dangling reference is a
  * `RewriteTrace` orphaned call, and a substitution whose declaration survives emission is caught
  * by the migration's "substitutions removed" check. This is the symmetric half: the rule that
  * never fired at all.
  *
  * Two properties make it useful to an agent in another repository (CLAUDE.md §4.45):
  *
  *  - Every finding is CLASSIFIED. An unmatched key is always a §1(b) problem — the mechanism
  *    works, the policy is wrong — so the fix is in that library's manifest and never in the
  *    engine. A finding whose reader cannot tell (a) from (b) from (c) costs a full investigation.
  *  - Findings are COLLECTED, not printed. Each (b) phase exposes a [[PolicySource.policyReport]]
  *    and writes nothing to stdout; an orchestrator gathers the reports from the phases it already
  *    holds ([[PolicyReport.collect]]) and decides whether to print them, fail the run, or file
  *    them. That is also why nothing here needs a call-site change to become reachable: the caller
  *    already holds the `Substitutions` value and the phase instances it configured.
  */
final case class PolicyFinding(
    /** the phase's `name`, or the type of the policy value for a non-phase seam. */
    phase: String,
    /** which knob, precisely enough to find it in a manifest — e.g. `Forwarder("com.x.W").members`. */
    setting: String,
    /** the declared key at fault, verbatim, so it can be grepped for in the manifest. */
    key: String,
    issue: PolicyIssue,
    detail: String,
):
  /** One grep-able line that ENDS in the §1 classification, because that is what the reader has to
    * act on: no engine change is ever the right response to any of these. */
  def render: String =
    s"""$phase — $setting: "$key" ${issue.label}: $detail""" +
      "  [§1(b) per-library policy: fix this key in the library's manifest; the engine needs no change]"

/** Why a declared key is a finding. All three are §1(b); they differ in what the engine could
  * prove. */
enum PolicyIssue(val label: String):
  /** the key matched NOTHING in the program — a typo, or policy left behind by an upstream rename.
    * The rule silently did not run. */
  case NeverMatched extends PolicyIssue("never matched")

  /** the key matched, but the engine cannot prove the rewrite it authorises is the one intended.
    * Not an error: a warning that the policy is relying on something unchecked. */
  case Unverifiable extends PolicyIssue("matched, but unverifiable")

  /** the key or its value is not in the shape the phase documents, so it could never match. */
  case Malformed extends PolicyIssue("malformed")

  /** the key BOUND to a declaration this run owns, and the thing it selected never happened.
    *
    * ==Why this is a fourth case where the binder's five collapse into three==
    * [[PolicyReport.issueOf]] maps five [[balticporter.tir.NotBound]] reasons onto three issues on
    * the stated ground that the issue says what the READER should do, and those five all answer one
    * question — did the key name anything? This does not. A
    * [[balticporter.tir.ResolutionPlan]] entry can bind perfectly, name a live remedy, and still do
    * nothing, because the FINDING the remedy resolves did not occur this run. Reported as
    * [[NeverMatched]] it would send its author looking for a member that is right there; reported as
    * [[Unverifiable]] it would say the engine could not check something, when the engine checked and
    * the answer was zero. The reader's action is its own: find out whether the site went away, or
    * whether something earlier in the pipeline already answered it.
    *
    * It is the source-side half of `CLAUDE.md` §5.5's declared-beside-applied split: a run that
    * printed only what it applied could not tell a port that asked for nothing from a port whose
    * every request was inert. */
  case NeverApplied extends PolicyIssue("bound, and never applied")

final case class PolicyReport(findings: List[PolicyFinding]):
  def isEmpty: Boolean                     = findings.isEmpty
  def nonEmpty: Boolean                    = findings.nonEmpty
  def ++(that: PolicyReport): PolicyReport = PolicyReport(findings ++ that.findings)
  def of(issue: PolicyIssue): List[PolicyFinding] = findings.filter(_.issue == issue)
  def keys: Set[String]                    = findings.map(_.key).toSet

  /** indented block, `"  none"` when clean — the shape the migration's other checks print in. */
  def render: String =
    if findings.isEmpty then "  none" else findings.map("  " + _.render).mkString("\n")

object PolicyReport:
  val empty: PolicyReport = PolicyReport(Nil)

  def collect(sources: PolicySource*): PolicyReport      = from(sources)
  def from(sources: Iterable[PolicySource]): PolicyReport =
    PolicyReport(sources.iterator.flatMap(_.policyReport.findings).toList)

  /** The never-fired report, derived from what the RUN BOUND — one row per key that did not.
    *
    * `PolicyBinder` lives in `balticporter.tir` and cannot produce a `PolicyReport`, because `core`
    * depends on `tir` and not the other way round (the same rule `RuleScope.neverFired` follows and
    * documents). So the binder owns the ANSWERS and this owns their classification, which is the
    * right split anyway: a binding is a fact, a `PolicyIssue` is a judgement about what its reader
    * should do. */
  def fromBindings(records: Iterable[balticporter.tir.PolicyBinder.Record]): PolicyReport =
    import balticporter.tir.Binding
    PolicyReport(records.iterator.collect {
      case r if r.binding.isUnbound =>
        val why = r.binding.why.get
        PolicyFinding(r.phase, r.setting, r.entry, issueOf(why), why.detail)
    }.toList)

  /** The per-location REMEDY SELECTIONS that did not do what their author asked
    * ([[balticporter.tir.ResolutionPlan.troubles]]), classified.
    *
    * Here rather than on the plan for [[fromBindings]]' reason exactly: `ResolutionPlan` lives in
    * `balticporter.tir` and cannot produce a `PolicyReport`, because `core` depends on `tir` and not
    * the other way round. The plan owns the ANSWERS and this owns their classification, which is the
    * right split anyway — a trouble is a fact, a `PolicyIssue` is a judgement about what its reader
    * should do next. */
  def fromResolutions(troubles: Iterable[balticporter.tir.ResolutionPlan.Trouble]): PolicyReport =
    import balticporter.tir.{Resolution, ResolutionPlan}
    PolicyReport(troubles.iterator.map { t =>
      val issue = t.issue match
        // it could never have named a mechanism, which is where the keys that are not keys go —
        // and NOT with the typos, exactly as `SyntheticTarget` is not one.
        case ResolutionPlan.Issue.UnknownRemedy => PolicyIssue.Malformed
        // the entry did nothing because nothing in this run could act on it, which is what
        // `NeverMatched` means to its reader. The DETAIL says which phase to enable.
        case ResolutionPlan.Issue.SourceAbsent  => PolicyIssue.NeverMatched
        case ResolutionPlan.Issue.NeverApplied  => PolicyIssue.NeverApplied
      PolicyFinding(Resolution.Seam, Resolution.Setting, t.declared, issue, t.detail)
    }.toList)

  /** [[balticporter.tir.NotBound]] → [[PolicyIssue]]. This mapping adds NO case, deliberately: the
    * issue says what the READER should do, and the binder's five reasons are five answers to ONE
    * question — did the key name anything — with only three actions between them (it named nothing,
    * it named more than you can act on, it could never have named anything). The binder's finer
    * distinctions survive in the DETAIL, which is where they are actionable.
    *
    * ([[PolicyIssue.NeverApplied]] is a fourth case and does not contradict that: it answers a
    * DIFFERENT question — the key bound and the thing it asked for did not happen — and its own doc
    * says why collapsing it into either neighbour misleads.)
    *
    * Two mappings worth stating because they are choices:
    *   - `ExternalOnly` → `NeverMatched`. From the manifest's point of view the entry did nothing,
    *     which is exactly what `NeverMatched` means to its reader; the detail says WHY, which is
    *     what `RuleScope`'s doc has asked for since the scope existed.
    *   - `SyntheticTarget` → `Malformed`. It could never legitimately have matched, so it belongs
    *     with the keys that are not keys — and NOT with the typos, which is the confusion the
    *     binder's own enum exists to prevent. */
  private def issueOf(why: balticporter.tir.NotBound): PolicyIssue =
    import balticporter.tir.NotBound
    why match
      case NotBound.NeverMatched       => PolicyIssue.NeverMatched
      case NotBound.ExternalOnly(_)    => PolicyIssue.NeverMatched
      case NotBound.Ambiguous(_)       => PolicyIssue.Unverifiable
      case NotBound.Malformed(_)       => PolicyIssue.Malformed
      case NotBound.SyntheticTarget(_) => PolicyIssue.Malformed

/** Implemented by every §1(b) seam — a phase taking a policy parameter, or a policy VALUE like
  * [[Substitutions]] that is consulted rather than run. Report a no-op policy, never a no-op run.
  *
  * Contract: reading this is cheap and side-effect free, and it reflects the LAST run/consultation
  * of that instance. Empty policy in, empty report out — a phase configured with nothing has
  * nothing to complain about (CLAUDE.md §1(b): "an empty parameter must make the phase a no-op").
  */
trait PolicySource:
  def policyReport: PolicyReport
