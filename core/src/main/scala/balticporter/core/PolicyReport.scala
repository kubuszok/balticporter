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

/** Implemented by every §1(b) seam — a phase taking a policy parameter, or a policy VALUE like
  * [[Substitutions]] that is consulted rather than run. Report a no-op policy, never a no-op run.
  *
  * Contract: reading this is cheap and side-effect free, and it reflects the LAST run/consultation
  * of that instance. Empty policy in, empty report out — a phase configured with nothing has
  * nothing to complain about (CLAUDE.md §1(b): "an empty parameter must make the phase a no-op").
  */
trait PolicySource:
  def policyReport: PolicyReport
