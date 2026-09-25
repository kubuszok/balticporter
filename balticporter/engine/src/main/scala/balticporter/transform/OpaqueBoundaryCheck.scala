package balticporter.transform

import balticporter.tir.*

/** The OPAQUE-TYPE boundary, counted — every seam `PrimitiveToOpaqueTransform`'s retyping opened and could not close: an EXTERNAL CALLEE with no readable formal, a REIFIED position
  * (`instanceof`/cast), and a BOXED-PRIMITIVE boundary the coercion did not reach. Parameterised by the phase's own `seeds`/`typeMapping`; empty seeds is a no-op. Counts residue, not successes.
  */
object OpaqueBoundaryCheck:

  /** the check's name in `findings.tsv`. */
  val Name = "opaque-boundary"

  /** what kind of boundary this is, which decides who fixes it. */
  enum Issue:
    /** a call to an EXTERNAL method whose formal `coerceArgs` cannot read; the SCOPE FENCE is the configured defence.
      */
    case ExternalCallee

    /** the declaration's opaque type is deliberately held back by the port's SCOPE. */
    case ScopedOut

    /** a BOXED-PRIMITIVE value where the wrapping could not fire — no auto-unbox node in the TIR. */
    case BoxedPrimitive

    /** an `OwnClass` target whose class cannot become an opaque type; the detail names the guard and the site. The class is emitted as java wrote it. */
    case OwnClassRefused

    /** a static taking the primitive first that stays a plain member of the object instead of an extension; the detail names why. */
    case ExtensionDeclined

    /** a literal constant a `switch` names as a case label: a pattern needs a stable `final val`, and reading one initialises the object where javac's inlined constant did not. */
    case ConstantInitialises

  object Issue:
    /** which of the three kinds — engine, port policy, or library-specific — the fix is. */
    def classification(i: Issue): String = i match
      case ExternalCallee =>
        "port policy: the SCOPE FENCE is the answer: the phase cannot read this external callee's " +
          "formal, so it cannot insert a coercion. Where the port's scope fences the external " +
          "type's declarations out of the seed set, the arguments reaching this call are still " +
          "the primitive and no coercion is needed. Where the scope does not fence them, the fix " +
          "is to add the external type to the scope's `except` set, or to add an `extraHints` " +
          "entry for the declaration whose value reaches this call."
      case ScopedOut =>
        "port policy, HELD BACK ON PURPOSE, and counted so the residue does not grow silently: this " +
          "declaration's type is the spec's primitive and the port's scope excludes it from the " +
          "seed set. The port decided this, and the count is what holds the decision honest."
      case BoxedPrimitive =>
        "engine gap: the boxed form of this primitive (`Integer` for `Int`, etc.) reached " +
          "a slot where the opaque type is expected, and no auto-unbox exists in the TIR. The " +
          "boxed-primitive coercion (wave 2.6) handles the commonest shape; this residue is what " +
          "it could not reach."
      case OwnClassRefused =>
        "port policy: the class is not a pure constants class at this site (instance members, a " +
          "construction, a subclass, a use as a TYPE, or a shape an opaque type cannot take), so the " +
          "`own-class` target leaves it a class and retypes nothing. Change the usage, or target a " +
          "minted or existing opaque type instead."
      case ExtensionDeclined =>
        "engine refusal, loud by count: an extension named like a member every value has " +
          "(`toString`, `equals`, `hashCode`, …) is never selected — `a.toString` calls the " +
          "primitive's own — and a method REFERENCE has no extension form. The static stays " +
          "`Owner.m(a, …)`, which behaves as java's. Port policy may rename it (`member-rename`, " +
          "ordered before this phase) to get the extension under another name."
      case ConstantInitialises =>
        "engine limit, counted: scala forbids an `inline val` at an opaque type and a case pattern " +
          "needs a stable value, so this constant is a `final val` and reading it initialises the " +
          "object, which javac's inlined constant does not (JLS 12.4.1). Harmless unless the " +
          "object's initialiser has effects or joins a cycle."

  /** one boundary site. `unit` is the top-level symbol for ownership filtering. */
  final case class Finding(issue: Issue, subject: String, detail: String, origin: Origin, unit: SymId = SymId.None):
    def render: String              = s"$issue $subject — $detail  (${origin.javaPath}:${origin.line})"
    def report: CheckReport.Finding =
      CheckReport.Finding(Name, issue.toString, subject, CheckReport.relativise(origin.javaPath), origin.line, detail)

  /** grouped one-line summary, worst family first, each with its classification. */
  def summary(fs: List[Finding]): String =
    if fs.isEmpty then "  none"
    else
      fs.groupBy(_.issue)
        .toList
        .sortBy((_, v) => -v.size)
        .map { (issue, vs) =>
          val head  = s"  ${vs.size} × $issue\n  ${Issue.classification(issue)}"
          val sites = vs.sortBy(f => (f.origin.javaPath, f.origin.line)).take(10).map("    " + _.render)
          (head :: sites).mkString("\n")
        }
        .mkString("\n")
