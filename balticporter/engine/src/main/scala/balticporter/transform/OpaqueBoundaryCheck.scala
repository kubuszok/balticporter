package balticporter.transform

import balticporter.tir.*

/** The OPAQUE-TYPE boundary, counted — every seam the primitive-to-opaque retyping opened and could
  * not close.
  *
  * ==Why this exists==
  * `PrimitiveToOpaqueTransform` retypes a set of `int`/`long`/… symbols to an `opaque type`,
  * wrapping at construction and unwrapping at consumption. The phase inserts coercions at every
  * boundary it can reach — owned method arguments, val initialisers, assignments, returns — but
  * THREE populations sit beyond what inline coercion can close:
  *
  *   - an EXTERNAL CALLEE whose formal the program does not have: `coerceArgs` falls through to
  *     `case _ => t` and the opaque-typed argument reaches a class-file formal that still expects
  *     the primitive. The SCOPE FENCE prevents this for the GL interfaces the libGDX spec names,
  *     and a port without a fence would see a much larger count here;
  *   - the REIFIED positions (`instanceof`/casts/`Class` arguments): these ask about a RUNTIME
  *     OBJECT, and the phase moved the static type without moving any object or any class.
  *     `CLAUDE.md` §1's paragraph on reified positions applies one level down (an opaque type IS
  *     the primitive at the JVM level, so `instanceof Int` still works for a `GLHandle`, but the
  *     STATIC `isInstanceOf[GLHandle]` is a different question and scalac rejects it). Measured at
  *     0 on every current port — the fenced GL interfaces are where casts live;
  *   - the BOXED-PRIMITIVE boundary: a `Cell<Integer>` holds the boxed form, and java's auto-unbox
  *     is implicit in the TIR. Wave 2.6 added the coercion for this face; what remains here is
  *     wherever that coercion could not fire.
  *
  * ==Universal, parameterised by the phase's own mapping==
  * §1(a) in mechanism: it reads the phase's `seeds` and `typeMapping`, holds no library name,
  * and an empty seed set produces an empty findings list by arithmetic rather than by a branch.
  * The check is a conditional lane, required of a run that carries `PrimitiveToOpaqueTransform`
  * and absent otherwise, following the `collection-boundary`/`nullability-boundary` pattern.
  *
  * ==What this deliberately does NOT count==
  * Coercions the phase SUCCESSFULLY INSERTED are not findings — they are the phase working. The
  * count here is the RESIDUE: what the phase could not close, with the §1 classification a bare
  * typer error cannot carry.
  */
object OpaqueBoundaryCheck:

  /** the check's name in `findings.tsv`. */
  val Name = "opaque-boundary"

  /** what kind of boundary this is, which is what decides who fixes it (CLAUDE.md §1). */
  enum Issue:
    /** a call to an EXTERNAL method — one whose definition this program does not have — where the
      * argument carries the opaque type and the class-file formal expects the primitive (or vice
      * versa for returns). The phase's `coerceArgs` reads the callee's `definitionOf`, which is
      * absent for externals; the SCOPE FENCE is the configured defence, so a port without one
      * would see this for every external callee a seed value reaches. */
    case ExternalCallee
    /** the declaration carries the opaque type and the port's SCOPE deliberately holds it back,
      * so it keeps the primitive type. Counted for the same reason `NullabilityBoundaryCheck`
      * counts `ScopedOut`: a residue nobody counts is a residue that grows. */
    case ScopedOut
    /** a BOXED-PRIMITIVE value (`Integer`, `Long`, …) where the opaque wrapping could not fire:
      * the boxed form has no `opaque type` in its ancestry and an auto-unbox node does not exist
      * in the TIR, so the value would reach the opaque slot as the boxed type. */
    case BoxedPrimitive

  object Issue:
    /** which of §1's three kinds the fix is — the thing a bare typer error cannot say. */
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

  /** one boundary site. `unit` is the top-level symbol for D2 ownership filtering, following the
    * `NullabilityBoundaryCheck` pattern. */
  final case class Finding(issue: Issue, subject: String, detail: String, origin: Origin,
                           unit: SymId = SymId.None):
    def render: String = s"$issue $subject — $detail  (${origin.javaPath}:${origin.line})"
    def report: CheckReport.Finding =
      CheckReport.Finding(Name, issue.toString, subject,
        CheckReport.relativise(origin.javaPath), origin.line, detail)

  /** grouped one-line summary, worst family first, each with its §1 classification — a reader must
    * not have to work out who fixes it. */
  def summary(fs: List[Finding]): String =
    if fs.isEmpty then "  none"
    else
      fs.groupBy(_.issue).toList.sortBy((_, v) => -v.size).map { (issue, vs) =>
        val head  = s"  ${vs.size} × $issue\n  ${Issue.classification(issue)}"
        val sites = vs.sortBy(f => (f.origin.javaPath, f.origin.line)).take(10).map("    " + _.render)
        (head :: sites).mkString("\n")
      }.mkString("\n")
