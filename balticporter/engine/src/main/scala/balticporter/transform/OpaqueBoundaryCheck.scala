package balticporter.transform

import balticporter.tir.*

/** The OPAQUE-TYPE boundary, counted — every seam `PrimitiveToOpaqueTransform`'s retyping opened
  * and could not close: an EXTERNAL CALLEE with no readable formal, a REIFIED position
  * (`instanceof`/cast), and a BOXED-PRIMITIVE boundary the coercion did not reach. Parameterised by
  * the phase's own `seeds`/`typeMapping`; empty seeds is a no-op. Counts residue, not successes. */
object OpaqueBoundaryCheck:

  /** the check's name in `findings.tsv`. */
  val Name = "opaque-boundary"

  /** what kind of boundary this is, which decides who fixes it (CLAUDE.md §1). */
  enum Issue:
    /** a call to an EXTERNAL method whose formal `coerceArgs` cannot read; the SCOPE FENCE is the
      * configured defence. */
    case ExternalCallee
    /** the declaration's opaque type is deliberately held back by the port's SCOPE. */
    case ScopedOut
    /** a BOXED-PRIMITIVE value where the wrapping could not fire — no auto-unbox node in the TIR. */
    case BoxedPrimitive

  object Issue:
    /** which of §1's three kinds the fix is (CLAUDE.md §4.45). */
    def classification(i: Issue): String = i match
      case ExternalCallee =>
        "§1(b) the SCOPE FENCE is the answer: the phase cannot read this external callee's " +
          "formal, so it cannot insert a coercion. Where the port's scope fences the external " +
          "type's declarations out of the seed set, the arguments reaching this call are still " +
          "the primitive and no coercion is needed. Where the scope does not fence them, the fix " +
          "is to add the external type to the scope's `except` set, or to add an `extraHints` " +
          "entry for the declaration whose value reaches this call."
      case ScopedOut =>
        "§1(b) HELD BACK ON PURPOSE, and counted so the residue does not grow silently: this " +
          "declaration's type is the spec's primitive and the port's scope excludes it from the " +
          "seed set. The port decided this, and the count is what holds the decision honest."
      case BoxedPrimitive =>
        "§1(a) engine gap: the boxed form of this primitive (`Integer` for `Int`, etc.) reached " +
          "a slot where the opaque type is expected, and no auto-unbox exists in the TIR. The " +
          "boxed-primitive coercion (wave 2.6) handles the commonest shape; this residue is what " +
          "it could not reach."

  /** one boundary site. `unit` is the top-level symbol for D2 ownership filtering. */
  final case class Finding(issue: Issue, subject: String, detail: String, origin: Origin,
                           unit: SymId = SymId.None):
    def render: String = s"$issue $subject — $detail  (${origin.javaPath}:${origin.line})"
    def report: CheckReport.Finding =
      CheckReport.Finding(Name, issue.toString, subject,
        CheckReport.relativise(origin.javaPath), origin.line, detail)

  /** grouped one-line summary, worst family first, each with its §1 classification. */
  def summary(fs: List[Finding]): String =
    if fs.isEmpty then "  none"
    else
      fs.groupBy(_.issue).toList.sortBy((_, v) => -v.size).map { (issue, vs) =>
        val head  = s"  ${vs.size} × $issue\n  ${Issue.classification(issue)}"
        val sites = vs.sortBy(f => (f.origin.javaPath, f.origin.line)).take(10).map("    " + _.render)
        (head :: sites).mkString("\n")
      }.mkString("\n")
