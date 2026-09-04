package balticporter.core

/** What a PARAMETERISED phase has to say about the POLICY it was handed — the CLAUDE.md §1(b)
  * half of a rule, since a bag-of-strings policy the engine cannot type-check (a misspelled key,
  * a stale FQN) is a silent no-op otherwise (§3's omission shape, one level up). Every finding is
  * CLASSIFIED (fix is always the manifest, never the engine) and COLLECTED, not printed — each
  * phase exposes [[PolicySource.policyReport]] and the orchestrator decides what to do with it. */
final case class PolicyFinding(
    /** the phase's `name`, or the type of the policy value for a non-phase seam. */
    phase: String,
    /** which knob, precisely enough to find it in a manifest — e.g. `Forwarder("com.x.W").members`. */
    setting: String,
    /** the declared key at fault, verbatim, so it can be grepped for in the manifest. */
    key: String,
    issue: PolicyIssue,
    detail: String,
    /** WHOSE question this answers — see [[PolicyFinding.About]]. Defaults to the pre-existing
      * behaviour, so every construction that predates the field says exactly what it always did. */
    about: PolicyFinding.About = PolicyFinding.About.TheKey,
):
  /** One grep-able line that ENDS in the §1 classification, since no engine change is ever the
    * right response to any of these. Differs by [[about]]: a finding about the KEY points at the
    * manifest entry quoted from it; one about THIS RUN may be a BASE's inherited key, correct
    * there, while what produced the finding is this module's own declarations. */
  def render: String =
    s"""$phase — $setting: "$key" ${issue.label}: $detail""" + (about match
      case PolicyFinding.About.TheKey =>
        "  [§1(b) per-library policy: fix this key in the library's manifest; the engine needs no change]"
      case PolicyFinding.About.ThisRun =>
        "  [§1(b) per-library policy, in THIS module: the key may be a base's and correct there — " +
          "what refused is this run, over declarations only this module has]")

object PolicyFinding:

  /** WHICH QUESTION a finding answers, deciding whether a module that did not DECLARE the key may
    * still be told about it. Independent of [[PolicyIssue]] (what the engine could PROVE): the same
    * `Unverifiable` can be about the key's shape OR about a refusal this run's own declarations
    * caused — the latter must not be dropped by an inherited-key filter (measured on `sge-visui`,
    * `policy` reading 0 with 8 errors). Structural: binding-derived is about the key, running-phase about the run. */
  enum About:
    /** the declared entry is at fault — a typo, a stale name, a malformed shape. Filterable to the
      * module that declared it, and the default. */
    case TheKey

    /** the entry is fine and THIS RUN refused what it authorised, on evidence from this run's own
      * program. Never filtered by key ownership: the module whose declarations caused it is the only
      * module that can act, and it is usually not the module that wrote the key. */
    case ThisRun

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

  /** the key BOUND to a declaration this run owns, and the thing it selected never happened — a
    * FOURTH case distinct from [[PolicyReport.issueOf]]'s three: a `ResolutionPlan` entry can bind
    * perfectly, name a live remedy, and still do nothing because the finding it resolves did not
    * occur this run. Reported as `NeverMatched`/`Unverifiable` would mislead. The source-side half
    * of CLAUDE.md §5.5's declared-beside-applied split. */
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
    * `PolicyBinder` lives in `balticporter.tir` and cannot produce a `PolicyReport` (`core` depends
    * on `tir`, not vice versa) — so the binder owns the ANSWERS and this owns the classification. */
  def fromBindings(records: Iterable[balticporter.tir.PolicyBinder.Record]): PolicyReport =
    import balticporter.tir.Binding
    PolicyReport(records.iterator.collect {
      case r if r.binding.isUnbound =>
        val why = r.binding.why.get
        PolicyFinding(r.phase, r.setting, r.entry, issueOf(why), why.detail)
    }.toList)

  /** The per-location REMEDY SELECTIONS that did not do what their author asked
    * ([[balticporter.tir.ResolutionPlan.troubles]]), classified. Here for [[fromBindings]]'s
    * reason: `ResolutionPlan` lives in `tir` and cannot produce a `PolicyReport`. */
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
        // the `Ambiguous` family: the key BOUND and the engine cannot say the act it authorises is
        // the one intended, because another key of this port's own authorises a different one at the
        // same declaration and lane. `NeverApplied` would state something false about the loser.
        case ResolutionPlan.Issue.ConflictingSelection => PolicyIssue.Unverifiable
      PolicyFinding(Resolution.Seam, Resolution.Setting, t.declared, issue, t.detail)
    }.toList)

  /** The ARTIFACT declarations that answered nothing — `PortManifest.dependencies` entries no
    * requirement in this module's own emitted code names (`DependencyCheck.unneeded`). Filed under
    * the manifest field, not a phase — `dependencies` has none. Key is the coordinate spelled as
    * `dependency-coverage` findings print it. */

  /** …and the DETAIL is the CHECK's sentence, not this function's — the reader is in one of two
    * removable cells (`DependencyCheck.Cell`) each wanting different investigation, so this
    * function does not get an opinion about it (`ENGINE-LIMITS.md` P8). */
  def fromDependencies(unneeded: Iterable[(balticporter.catalog.ArtifactDep, String)]): PolicyReport =
    PolicyReport(unneeded.iterator.map { (d, why) =>
      PolicyFinding(DependencySeam, DependencySetting, d.toString, PolicyIssue.NeverApplied, why)
    }.toList)

  /** the two strings [[fromDependencies]] files under, named so a reader of `findings.tsv` and a
    * reader of the manifest are looking at the same words. */
  val DependencySeam    = "dependencies"
  val DependencySetting = "PortManifest.dependencies"

  /** [[balticporter.tir.NotBound]] → [[PolicyIssue]]. Adds NO case: the binder's five reasons are
    * five answers to ONE question (did the key name anything) with only three reader actions;
    * finer distinctions survive in the DETAIL. `ExternalOnly` → `NeverMatched` (did nothing);
    * `SyntheticTarget` → `Malformed` (could never have matched — not a typo). */
  private def issueOf(why: balticporter.tir.NotBound): PolicyIssue =
    import balticporter.tir.NotBound
    why match
      case NotBound.NeverMatched       => PolicyIssue.NeverMatched
      case NotBound.ExternalOnly(_)    => PolicyIssue.NeverMatched
      case NotBound.Ambiguous(_)       => PolicyIssue.Unverifiable
      case NotBound.Malformed(_)       => PolicyIssue.Malformed
      case NotBound.SyntheticTarget(_) => PolicyIssue.Malformed

/** Implemented by every §1(b) seam — a phase taking a policy parameter, or a policy VALUE like
  * [[Substitutions]] consulted rather than run. Report a no-op policy, never a no-op run. Reading
  * is cheap, side-effect free, reflects the LAST run; empty policy in, empty report out (§1b). */
trait PolicySource:
  def policyReport: PolicyReport
