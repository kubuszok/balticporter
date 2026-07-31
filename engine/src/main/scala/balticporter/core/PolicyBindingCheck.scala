package balticporter.core

import balticporter.tir.{Binding, NotBound, PolicyBinder}

/** DID THE BINDER AND THE PHASE AGREE? — the measurement that earns [[PolicyBinder]] the right to
  * replace eighteen hand-written key tests, taken while BOTH are still running.
  *
  * ==Why a check and not a spec==
  * A spec proves the binder answers correctly about a program the spec wrote. What nobody can write
  * a spec for is the corpus: nine ports, thirteen lanes, every key a real manifest contains, matched
  * against a real Java tree by code that has been patched one library at a time. This runs both
  * answers over exactly that and reports every place they differ — which is the only evidence that
  * swapping one for the other changes nothing.
  *
  * ==Two kinds of difference, and only one of them is a defect==
  *   - a '''CONTRADICTION''' — the phase's own matcher says a key never fired and the binder bound
  *     it, or the reverse. That is a defect in one of the two, and the expectation is ZERO.
  *   - a '''NEWLY VISIBLE''' refusal — the binder reports something the phase's matcher had no way
  *     to say. `ExternalOnly` is the case that pays for itself: `RuleScope`'s own doc describes it
  *     as a live silent no-op, where the entry matches an interned external, the phase rewrites
  *     nothing, and the entry COUNTS AS HAVING FIRED. A rise here is the gate beginning to tell the
  *     truth (CLAUDE.md §3), not a regression.
  *
  * ==And it is scaffolding, deliberately==
  * Once the phases take bound values there is no second implementation left to compare against, and
  * a check that can only ever report zero is the shape `ENGINE-LIMITS.md` P2 warns about. It is
  * deleted with the matchers it measures, and the measurement is recorded in `PROGRESS.md`.
  */
object PolicyBindingCheck:

  val Name: String = "policy-binding"

  val Classification: String =
    "  [§1(a) engine: the binder and a phase's own key matcher disagree about what a declared key " +
      "names — one of the two is wrong, and every keyed phase reads the same grammar]"

  /** one disagreement, rendered for `findings.tsv`. */
  final case class Finding(kind: String, phase: String, setting: String, key: String, detail: String):
    def render: String = s"""$phase — $setting: "$key" [$kind] $detail"""

  /** @param bindings what the binder was asked and what it answered
    * @param phases   what the PHASES' own matchers reported for the same run
    */
  def check(bindings: List[PolicyBinder.Record], phases: PolicyReport): List[Finding] =
    // A phase reports a key it could not find as `NeverMatched`; that is the only verdict both
    // sides can express, so it is the only one a CONTRADICTION can be about.
    val phaseUnfired: Set[String] =
      phases.findings.filter(_.issue == PolicyIssue.NeverMatched).map(_.key).toSet

    bindings.flatMap { r =>
      val binderUnbound = r.binding.isUnbound
      val phaseUnfound  = phaseUnfired(r.entry)
      (r.binding, phaseUnfound) match
        case (Binding.Bound(_, _, n), true) =>
          List(Finding("contradiction", r.phase, r.setting, r.entry,
            s"the binder resolved this key to $n symbol(s) and the phase's own matcher reported it " +
              "as never matched — the two grammars disagree about the same string"))
        case (Binding.Unbound(_, NotBound.NeverMatched), false) =>
          List(Finding("contradiction", r.phase, r.setting, r.entry,
            "the binder found nothing and the phase's own matcher did not report it as unmatched — " +
              "so the phase believes it fired"))
        case (Binding.Unbound(_, why), false) =>
          List(Finding("newly-visible", r.phase, r.setting, r.entry,
            s"${why.label}: ${why.detail}  (the phase's own matcher cannot express this verdict)"))
        case _ => Nil
    }

  def summary(fs: List[Finding]): String =
    val contradictions = fs.count(_.kind == "contradiction")
    val newly          = fs.size - contradictions
    if fs.isEmpty then "  binder and phase matchers agree on every declared key"
    else
      s"  $contradictions contradiction(s), $newly newly-visible refusal(s)\n" +
        fs.sortBy(f => (f.kind, f.phase, f.key)).map("  " + _.render).mkString("\n")
