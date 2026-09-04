package balticporter.transform

import balticporter.catalog.FixKind
import balticporter.tir.*

/** The nullability boundary, counted: every annotated site the phase could not honour, every seam
  * a wrapper retype opened and did not close, and every retype whose transparency the language
  * does not grant (e.g. `T | Null` compiles at the declaration but a use at a plain abstract `T`
  * does not — cost lands on uses, invisible at the declaration). Parameterised by the annotation
  * policy; empty produces empty by arithmetic. */
object NullabilityBoundaryCheck extends RemedySource:

  /** The check's name in `findings.tsv`. */
  val Name = "nullability-boundary"

  /** The menu (`Remedy`, DESIGN.md §8.16). Two entries — everything else a port could restate
    * already has a spelling (`NullabilityTransform(scope)`/`target`/`annotations`, or a build flag).
    * `ScopedOut` records that the port read a held-back site and accepts the residue there, as
    * opposed to deleting the scope entry; `AbstractTypeParameter` records accepting the use-site
    * errors, the one of its three ways out no manifest key or build flag already states. */
  def remedies: List[Remedy] = List(
    Remedy(
      id = "accept-scoped-out", lane = Name, kind = Issue.ScopedOut.toString,
      emissionAffecting = false, fix = FixKind.Parameterised,
      what = "the port states that this declaration is meant to keep its upstream type and marker — " +
        "the review outcome the count had no way to record, as opposed to deleting the scope entry, " +
        "which is `NullabilityTransform(scope)`'s own spelling"),
    Remedy(
      id = "accept-abstract-type-parameter", lane = Name, kind = Issue.AbstractTypeParameter.toString,
      emissionAffecting = false, fix = FixKind.Parameterised,
      what = "the port ACCEPTS what this retype costs at the USES — the third of the three ways out " +
        "the classification names, and the only one no manifest key or build flag already states"),
  )

  /** Drains what this port selected (CLAUDE.md §5). */
  def resolved(plan: ResolutionPlan, findings: List[Finding]): List[Finding] =
    plan.drain(remedies, findings)(f =>
      ResolutionPlan.Residue(f.issue.toString, f.at, f.subject, f.origin, f.detail))

  /** What kind of boundary this is, which decides who fixes it (CLAUDE.md §1). */
  enum Issue:
    /** `@Null Object... rest` — a Scala vararg has no nullable form (`T*` cannot be `T* | Null`). */
    case VarargParameter
    /** the annotated type is a primitive, which cannot be null at all. */
    case PrimitiveType
    /** the annotation carries element values at this site, so consuming it would silently drop
      * them — and `@A` where the upstream wrote `@A(x)` is a different annotation. */
    case AnnotationArguments
    /** the annotation sits where the declaration has no type occurrence to move — a TYPE, or a
      * method-local, which is not surface at all. */
    case NotAValuePosition
    /** WRAPPER mode only: the member is one end of an override pair, and a wrapper retype changes
      * the signature, so moving one end alone breaks the other. */
    case OverrideCrossing
    /** WRAPPER mode only: a wrapped value reached a slot whose formal this program does not have. */
    case UncoercibleSeam
    /** UNION mode: the annotated type mentions an ABSTRACT TYPE PARAMETER, where the union is not
      * transparent — retyped, and counted, because the cost lands on the USES and not here. */
    case AbstractTypeParameter
    /** the SCOPE's own closure: an ancestor this port scoped out declares a same-named annotated
      * member, so its half of the override pair keeps the upstream type while this one moves. */
    case ScopedOutParent
    /** the declaration carries a configured annotation and this port's `nullability` scope
      * deliberately holds it back, so it keeps its upstream type and its upstream marker. */
    case ScopedOut
    /** WRAPPER mode only: retyping this parameter would make two OVERLOADS of the same member
      * erase to one descriptor, which java's own erasure kept apart. */
    case OverloadErasureClash
    /** WRAPPER mode only: the formal to coerce against names a type variable that is not in scope
      * where the call stands, so no ascription can be WRITTEN there. */
    case UnwritableFormal

  object Issue:
    /** which of §1's three kinds the fix is — the thing a bare typer error cannot say. */
    def classification(i: Issue): String = i match
      case VarargParameter =>
        "§1(a) REFUSED on purpose: a Scala vararg has no nullable form — `T*` cannot be written " +
          "`T* | Null` and a wrapper around the repeated parameter would change its arity. The " +
          "upstream annotation is left in place; there is no engine change that makes this " +
          "expressible, and guessing one would silently change a signature."
      case PrimitiveType =>
        "§1(b) the ANNOTATION is wrong, not the port: a primitive cannot be null, so the entry " +
          "names a site its own library cannot mean. Left untouched; fix it upstream, or narrow " +
          "the `nullability` scope so this declaration is not considered."
      case AnnotationArguments =>
        "§1(a) REFUSED on purpose: this annotation carries element values at this site, and " +
          "consuming it into the type would drop them — `@A` where the upstream wrote `@A(x)` is a " +
          "different annotation. A nullability marker normally has none; if this one does, it is " +
          "not a plain nullability marker and should not be listed in `annotations`."
      case NotAValuePosition =>
        "§1(a) not an error and deliberately not retyped: the annotation sits on a TYPE or a " +
          "method LOCAL, neither of which has a signature occurrence to move — a local's type is " +
          "an implementation detail no consumer can see. READ THE ORIGIN BEFORE ACTING: the " +
          "commonest source is a PARAMETER an earlier phase demoted, because Java lets a method " +
          "reassign its parameters and Scala does not, so the reassigned-parameter transform " +
          "repurposes the parameter symbol as a local `var` and mints a `<name>$arg` symbol for " +
          "the slot. The slot carries the annotation too and IS retyped; this row is its local " +
          "half, and the emitted signature is already correct."
      case OverrideCrossing =>
        "§1(b)/§1(a): WRAPPER mode changes the member's signature, so both ends of an override " +
          "pair have to move together and this phase can only see one of them today. Use `union` " +
          "mode (a union return may be narrowed or widened across an override, measured), or " +
          "scope the wrapper to declarations that do not participate in an override."
      case UncoercibleSeam =>
        "§1(a) engine gap: a wrapped value reaches a call whose callee is an EXTERNAL symbol the " +
          "frontend interned without a signature, so there is no formal to coerce against and " +
          "nothing honest to insert. Unwrap at the source declaration, or scope the wrapper away " +
          "from the declarations that feed this call."
      case AbstractTypeParameter =>
        "§1(b) COUNTED, not refused, and the one place the union floor is NOT free: `Null` is a " +
          "subtype of every CONCRETE reference type, so `String | Null` simplifies at every use — " +
          "but it is NOT a subtype of an ABSTRACT `T`, which is the very reason a `return null` at " +
          "a `T` return needs a cast in the first place. So `T | Null` does not conform to `T`, and " +
          "every use of this declaration in a plain `T` slot is a compile error. The cost lands on " +
          "the USES and is invisible here, which is why it is a number. FOUR ways out, all policy: " +
          "switch to a `named` or `option` target (a wrapper `W[T]` composes at every `T` — K13 " +
          "CLOSED); scope this port's generic types out of `nullability`; accept the errors; or " +
          "stage to `-Yexplicit-nulls -language:unsafeNulls`, under which the whole class disappears."
      case ScopedOutParent =>
        "§1(b) A SCOPE EXIT THAT DID NOT CLOSE: an ANCESTOR of this declaration is held back by one " +
          "of this port's own `nullability` scope entries, and it declares a member of the same " +
          "name carrying the same annotation — so the parent keeps its upstream type while THIS " +
          "override moves, which is half an override pair and the one shape a union floor may not " +
          "emit. Add this type to the scope beside its ancestor. A `RuleScope` is a set of FQNs and " +
          "nothing computes this closure, so before this was reported the COMPILER was the only " +
          "thing that could find a missing entry (`ENGINE-LIMITS.md` K13: 35 errors -> 6 -> 0, the " +
          "six being exactly this shape). A subtype that merely INHERITS an annotated member is not " +
          "reported and needs no entry — adding one is dead policy, which `policy` now reports."
      case OverloadErasureClash =>
        "§1(a) REFUSED on purpose, and the refusal is the whole answer: java kept these overloads " +
          "apart BY ERASURE — `f(Font)` beside `f(BitmapFont)` — and a wrapper erases every one of " +
          "them to the same descriptor, because erasure drops type arguments and an opaque wrapper " +
          "drops to `Object`. Retyped, the two declarations are `E120 Conflicting definitions … " +
          "have the same type … after erasure`, which is a compile error at a member whose name, " +
          "arity and bodies are all correct. So the parameters that CARRY the distinction keep " +
          "their upstream type on BOTH members and their upstream marker with it. There is no " +
          "engine change that makes the pair expressible — scala has one erasure, exactly as java " +
          "does — and the alternatives are a port's: rename one overload, or scope the wrapper away " +
          "from this type."
      case UnwritableFormal =>
        "§1(a) REFUSED on purpose: the formal this argument would be ascribed to names a type " +
          "VARIABLE of the CALLEE, and a callee's own type variables do not resolve at the call " +
          "site (`ENGINE-LIMITS.md` G12). Where the RECEIVER instantiates them the phase " +
          "substitutes and no ascription is needed at all; where it does not — an inherited " +
          "callee, a raw receiver, a static member, which sees NONE of its class's type " +
          "parameters (G20) — there is no expression to write, and emitting the formal verbatim is " +
          "`E006 Not found: type T` at a line the source never wrote. The argument is left as it " +
          "stands, which is at worst an ascription too few and never a name that does not exist."
      case ScopedOut =>
        "§1(b) HELD BACK ON PURPOSE, and counted for the reason every other lane here is: this " +
          "declaration carries a configured nullability annotation and the port's `nullability` " +
          "scope excludes it, so it keeps its upstream type AND its upstream marker while the " +
          "declarations around it moved. That is a residue, not a defect — but a residue nobody " +
          "counts is a residue that grows: the emitted markers are the only other evidence it " +
          "exists, and the emitter renders a class's and a method's annotations and neither a " +
          "field's nor a parameter's, so the text under-reports it by construction. Shrink it by " +
          "deleting the scope entry (and paying `AbstractTypeParameter`'s errors), or by staging " +
          "to `-Yexplicit-nulls -language:unsafeNulls`, under which the whole exit disappears."

  /** One boundary site. `unit` is the top-level symbol it belongs to (ownership, D2); `at` is the
    * declaration for selection (`remedies`) and is deliberately a different symbol — they coincide
    * only for a top-level type. Defaults to `SymId.None` rather than `unit`, so an unset finding is
    * unselectable rather than selectable at the wrong granularity. */
  final case class Finding(issue: Issue, subject: String, detail: String, origin: Origin, unit: SymId,
                           at: SymId = SymId.None):
    def render: String = s"$issue $subject — $detail  (${origin.javaPath}:${origin.line})"
    def report: CheckReport.Finding =
      CheckReport.Finding(Name, issue.toString, subject,
        CheckReport.relativise(origin.javaPath), origin.line, detail)

  /** Grouped one-line summary, worst family first, each with its §1 classification. */
  def summary(fs: List[Finding]): String =
    if fs.isEmpty then "  none"
    else
      fs.groupBy(_.issue).toList.sortBy((_, v) => -v.size).map { (issue, vs) =>
        val head  = s"  ${vs.size} × $issue\n  ${Issue.classification(issue)}"
        val sites = vs.sortBy(f => (f.origin.javaPath, f.origin.line)).take(10).map("    " + _.render)
        (head :: sites).mkString("\n")
      }.mkString("\n")
