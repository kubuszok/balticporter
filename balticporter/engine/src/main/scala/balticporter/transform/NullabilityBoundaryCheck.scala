package balticporter.transform

import balticporter.catalog.FixKind
import balticporter.tir.*

/** The NULLABILITY boundary, counted — every annotated site the phase could not honour, every seam
  * a wrapper retype opened and did not close, and every retype whose transparency the LANGUAGE does
  * not grant.
  *
  * ==Why a refusal has to be a number==
  * `NullabilityTransform` moves a contract out of an annotation and into the type. Where it cannot
  * — a vararg parameter has no nullable Scala form, a primitive cannot be null, an annotation
  * carrying arguments is a different annotation — the declaration is left exactly as the upstream
  * wrote it. That is the RIGHT outcome and it is indistinguishable, from the emitted file alone,
  * from the phase never having been configured. A refusal that moved no number would be the §1(b)
  * silent no-op the whole design exists to avoid, so each one arrives here with an origin and a §1
  * classification, before any compiler runs.
  *
  * ==And one retype that SUCCEEDS still has to be counted==
  * `Null` is a subtype of every CONCRETE reference type, which is what makes the union floor free
  * at a use site — and it is NOT a subtype of an ABSTRACT type parameter, which is the very reason
  * a `return null` at a `T` return needs a cast at all. So a retyped `T | Null` declaration compiles
  * and every USE of it in a plain `T` slot does not. Nothing at the declaration is wrong, there is
  * nothing to refuse, and the cost is entirely somewhere else — which is exactly the shape that has
  * to be a number rather than a discovery. Measured on the reference port: 0 → 35 compile errors,
  * every one inside a generic type.
  *
  * ==And a wrapper mode has a residue by construction==
  * Wrapper mode retypes a declaration to `W[T]` and inserts explicit wrap/unwrap at the four slot
  * kinds a coercion seam reaches. One slot has NO formal to compare against: a call whose callee is
  * an external the frontend interned without a signature. Nothing honest can be inserted there, so
  * the site is counted — the same shape, and the same reasoning, as the collection boundary's
  * scoped-out receiver.
  *
  * ==Universal, parameterised by the policy==
  * §1(a) in mechanism; it holds no library's names. An empty annotation set produces an empty
  * findings list by arithmetic rather than by a branch.
  */
object NullabilityBoundaryCheck extends RemedySource:

  /** the check's name in `findings.tsv`. */
  val Name = "nullability-boundary"

  /** THE MENU — see [[balticporter.tir.Remedy]] and `DESIGN.md` §8.16.
    *
    * Two entries, and the ONE-SPELLING rule accounts for every act that is not here. Widening or
    * narrowing the exclusion is `NullabilityTransform(scope)`; choosing between a union and a
    * wrapper is its `target`; deciding whether an annotation belongs in the set at all is
    * `annotations`; and `-Yexplicit-nulls -language:unsafeNulls` is a BUILD act, which no manifest
    * key can or should hold. A remedy restating any of those would be a second spelling of a key a
    * port already has.
    *
    * What none of them spells is the port having READ a site and stating that the residue is
    * acceptable THERE, which is what these two are:
    *
    *   - `ScopedOut` — this declaration keeps its upstream type and its upstream marker because the
    *     port's own scope holds it back. The check exists because "a residue nobody counts is a
    *     residue that grows"; a review that reached the opposite conclusion at one declaration had
    *     nowhere to be recorded, so the number could only ever go up. Deleting the scope entry is
    *     the OTHER answer and it is already spelled;
    *   - `AbstractTypeParameter` — the classification names three ways out and says all three are
    *     policy: scope it out, stage the build, or ACCEPT THE ERRORS. The first two are spelled; the
    *     third is a statement about the USES, which is the one thing no key here can see, and on a
    *     port that compiles it is a statement the compile itself corroborates.
    *
    * The other seven take none. `VarargParameter` and `AnnotationArguments` are refused
    * STRUCTURALLY — a Scala vararg has no nullable form and `@A` is not `@A(x)` — so there is
    * nothing to accept and no site at which accepting would mean anything. `PrimitiveType` is an
    * upstream ANNOTATION ERROR: the entry names a site its own library cannot mean, and a port that
    * accepted it would be recording agreement with a mistake. `OverrideCrossing` and
    * `UncoercibleSeam` are wrapper-mode gaps whose answers are `target`/`scope` and the same K15
    * external-callee limit the collection lane counts. `ScopedOutParent` is a scope EXIT THAT DID
    * NOT CLOSE — half an override pair, which is the one shape a union floor may not emit — so its
    * only answer is the missing scope entry (`ENGINE-LIMITS.md` K13: 35 -> 6 -> 0). And
    * `NotAValuePosition` is an OUTCOME row: its own classification says the emitted signature is
    * already correct, which makes it `captured-context`'s neighbour rather than a residue, and
    * draining it would move a report of something that worked.
    */
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

  /** DRAIN what this port selected — see [[remedies]] and `CLAUDE.md` §5. */
  def resolved(plan: ResolutionPlan, findings: List[Finding]): List[Finding] =
    plan.drain(remedies, findings)(f =>
      ResolutionPlan.Residue(f.issue.toString, f.at, f.subject, f.origin, f.detail))

  /** what kind of boundary this is, which is what decides who fixes it (CLAUDE.md §1). */
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
          "the USES and is invisible here, which is why it is a number. Three ways out, all policy: " +
          "scope this port's generic types out of `nullability`; accept the errors; or stage to " +
          "`-Yexplicit-nulls -language:unsafeNulls`, under which the whole class disappears."
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

  /** one boundary site. `unit` is the top-level symbol it belongs to, which is how a dependent
    * port holds a finding to the module that EMITS it (`ENGINE-LIMITS.md` D2).
    *
    * `at` is a DIFFERENT symbol answering a different question and the two are deliberately not one:
    * the unit is for OWNERSHIP and the declaration is for SELECTION ([[remedies]]). They coincide
    * only for a top-level type, and the site this check reports most often is a PARAMETER, whose
    * declaration is the method it belongs to and whose unit is the file's outermost class. Defaulted
    * to [[SymId.None]] rather than to `unit`, because a finding minted without one must be
    * UNSELECTABLE rather than selectable at the wrong granularity — `SymId.None` matches no key by
    * construction, and a fallback to `unit` would silently let one selection drain every row in a
    * file. */
  final case class Finding(issue: Issue, subject: String, detail: String, origin: Origin, unit: SymId,
                           at: SymId = SymId.None):
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
