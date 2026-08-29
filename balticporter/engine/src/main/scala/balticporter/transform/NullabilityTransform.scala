package balticporter.transform

import balticporter.core.{MergeablePolicy, PolicyFinding, PolicyIssue, PolicyReport, PolicySource}
import balticporter.tir.*

/** Move a library's NULLABILITY ANNOTATIONS out of an annotation the Scala compiler ignores and
  * INTO THE TYPE — `T | Null` (the union floor), `W[T]` (a configured named wrapper), or
  * `Option[T]`.
  *
  * ==Why this is a §1(b) and not a §1(a)==
  * WHICH annotation states nullability is a fact about one library: libGDX declares its own
  * `@Null`, one corpus port uses `org.jspecify.annotations.Nullable`, another
  * `org.jetbrains.annotations.Nullable` — and SEVEN of the eleven upstreams surveyed carry no
  * nullability annotation at all. So the empty annotation set is the NORMAL case and it is a no-op
  * by arithmetic: nothing binds, nothing is retyped, no decision is recorded, no finding is
  * produced. Everything else — reading the annotation off a symbol, rewriting the annotated
  * occurrence of a type, stripping the consumed annotation, coercing at the seams — is the same
  * mechanism for every library.
  *
  * An annotation that marks the OTHER direction (`@NotNull`) is simply not listed. Listing one is
  * not refused: non-null is already the default, so it would retype the wrong half of the API and
  * the port's own diff says so. What IS reported is an entry that matched nothing — the §1(b)
  * silent no-op, via the ordinary [[PolicyBinder]] never-fired machinery.
  *
  * ==Union mode costs nothing at a use site, and that was compiled rather than reasoned==
  * Without `-Yexplicit-nulls` — which no lane passes and which is a later, separately-gated
  * stage — `Null` is a subtype of every reference type, so `T | Null` simplifies at every use: no
  * `.nn`, no inference change, no overload-resolution change, and an override may narrow OR widen.
  * What the floor BUYS is that the contract becomes visible to every IDE and every downstream
  * compiler, that it is byte-forward into explicit nulls with no second migration, and that it
  * DELETES the `null.asInstanceOf[T]` placeholder at an annotated GENERIC return: `def m[T <: X]():
  * T = null` is a type error even without the flag (`Null <: T` does not hold at an abstract `T`),
  * which is exactly why the frontend inserts that cast, while `T | Null = null` compiles. See
  * [[retireNullCast]]. Its honest limitation: without the flag it ENFORCES nothing. It is typed
  * documentation until the flag is turned on.
  *
  * ==Wrapper mode attacks the SLOT, never the type==
  * `given Conversion` is a measured dead end (`ENGINE-LIMITS.md` K2: a conversion never fires
  * through an overloaded call, and the overload-heaviest upstream is also the annotation-heaviest).
  * Nothing here consults an implicit. The phase retypes the annotated declarations and inserts
  * EXPLICIT wrap/unwrap at the four slot kinds a coercion seam reaches — argument-against-formal,
  * declaration-against-initialiser, assignment-against-right-hand-side, return-against-result —
  * plus member selection on a wrapped receiver, all of it BEFORE overload resolution ever runs, so
  * the argument's type is already exactly the formal and nothing is inferred.
  *
  * One rewrite is not optional: `x == null` on an opaque wrapper is a COMPILE ERROR (no `CanEqual`),
  * so every Java null test on a wrapped value becomes `.isEmpty`. The wrapper contract is exactly
  * five members — `apply` (null-normalising), `empty`, extension `get` (unchecked, NPE on empty,
  * which IS Java's semantics at a DEREFERENCE), extension `orNull` (null-preserving unwrap, which
  * IS Java's semantics at a SLOT that accepts null — an unannotated field, parameter, result, or
  * `Object` formal), and extension `isEmpty`. The SLOT-NULLABILITY RULE decides which unwrap to
  * emit: a dereference (member access, array op) uses `.get` because java NPEs on null there; a
  * slot coercion (argument-vs-formal, declaration-vs-init, return-vs-result, lambda body) uses
  * `.orNull` because java's default is that every reference slot accepts null. The exception is a
  * PRIMITIVE slot (unboxing) where java NPEs, so `.get`. `orNull` is fake-`@deprecated` as a lint
  * tripwire in the hand-written repositories, but this generated code is the java-interop boundary
  * the deprecation message names. Emission is FQN-only (§6) and the extensions resolve from the
  * companion's implicit scope with no import.
  *
  * ==Ordering==
  * AFTER the collections family, because their retypes must land first — an annotated
  * `java.util.List` field is `Buffer[T] | Null` and not the reverse — and BEFORE the package
  * rename, because the configured annotation FQNs are written in the UPSTREAM namespace (§4.56).
  *
  * ==Every refusal is COUNTED==
  * A vararg parameter has no nullable Scala form (`T*` cannot be `T* | Null`); a bare primitive
  * cannot be null at all; an annotation carrying ARGUMENTS is a different annotation and the
  * engine will not consume half of it; and in wrapper mode a member that crosses an override
  * boundary would change a signature the other end of the pair does not know about. Each is
  * refused, left exactly as it was — annotation included, so the reader still sees the contract —
  * and reported by [[NullabilityBoundaryCheck]] with its §1 classification. A refusal that moved no
  * number would be the silent no-op this whole design exists to avoid.
  */
final class NullabilityTransform(
    val annotations: Set[String] = Set.empty,
    val target: NullabilityTransform.Target = NullabilityTransform.Target.Union,
    val scope: RuleScope = RuleScope.Everywhere(),
    /** Members whose return (or field) type is nullable even though the java source carries NO
      * nullability annotation. Exact FQNs matched against `Symbol.fullName` at BIND TIME — the same
      * key discipline as `OpaqueSpec.hints` (`ENGINE-LIMITS.md` O4): `Class#member` for a unique
      * member, `Class#member(desc)` where the member is overloaded.
      *
      * The mechanism is the SAME for every member: the same target shape, the same slot coercions at
      * every use, the same `== null` / `!= null` rewrites, the same override-component rule
      * (whole-or-none per the phase's existing component logic), the same `nullability-boundary` count.
      * What differs is HOW the member is selected — by FQN instead of by annotation.
      *
      * Empty is the no-op. A non-empty set contributes a fingerprint segment, and each entry is a
      * `PolicyBinder.bindMember` at bind time: a key that names nothing is reported as never-matched
      * exactly as an annotation FQN would be.
      *
      * ==Why this is a (b) and not a (c)==
      * The MECHANISM (retype, coerce, propagate) is the engine's — the same code path the annotation-
      * based selection takes. What differs is WHICH members, and that is §1(c) knowledge: sge wrapped
      * six Ashley returns in `Nullable` from its migration notes, and Ashley's java carries no
      * annotation. The key set is a value a port hands the engine, exactly as `OpaqueSpec.hints` is. */
    val nullableMembers: Set[String] = Set.empty,
) extends Phase, Rewrite, PolicySource, MergeablePolicy, PolicyBound:

  import NullabilityTransform.*
  import NullabilityBoundaryCheck.{Finding, Issue}

  def name: String = NullabilityTransform.Name

  /** the lane that counts every site this phase refused, every wrapper seam it could not close, and
    * every retype whose transparency the LANGUAGE does not grant (`Rewrite`). */
  def accountedBy: Set[String] = Set(NullabilityBoundaryCheck.Name)

  /** After the collection retypes (they must land first — see the class doc) and before the
    * namespace rename (the annotation FQNs are upstream). Both are declared by NAME and
    * `Pipeline.order` ignores an edge naming a phase this pipeline does not have, so a port that
    * runs neither is unaffected. */
  override def runsAfter: Set[String]  = Set("java-collections->scala")
  override def runsBefore: Set[String] = Set("package-rename")

  // -------------------------------------------------------------------------
  // policy, bound once by the RUN before any phase runs (§8.1)
  // -------------------------------------------------------------------------

  /** annotation SYMBOL → the declared FQN that named it, which is the key a decision quotes and the
    * string an agent edits (§4.575). Empty when nothing bound, which is the no-op. */
  private var boundAnnots: Map[SymId, String] = Map.empty
  private var records: List[PolicyBinder.Record] = Nil
  /** `nullableMembers` entries that actually matched at least one symbol during [[run]], tracked for
    * never-matched reporting. Populated at run time, not at bind time, because earlier phases
    * (BeanPropertyTransform) may rename the members this set targets. */
  private val matchedMembers = collection.mutable.Set.empty[String]

  /** what the RUN knows about itself — which units it EMITS, and which of this (possibly MERGED)
    * instance's keys THIS manifest contributed. Both are needed by [[intrudesOnBase]] and neither
    * is derivable from the `Program`; see [[RunScope]]. The default is the base-port answer. */
  private var runScope: RunScope = RunScope.whole
  /** the subjects THIS module contributed to this phase's policy — `None` where this module
    * declares no instance of the phase at all, which is the no-screen answer. */
  private var ownSubjects: Option[Set[String]] = scala.None

  def bindPolicy(binder: PolicyBinder): Unit =
    // `Ownership.Either`: a library's nullability annotation is DECLARED IN-TREE about as often as
    // it is a third-party jar (libGDX ships its own `@Null`; another port uses jspecify's). Neither
    // is a mistake, and demanding `Owned` would report every third-party annotation as a typo.
    boundAnnots = annotations.toList.sorted.flatMap { fqn =>
      binder.bindType(name, "annotations", fqn, Ownership.Either).toOption.map(_ -> fqn)
    }.toMap
    val setting = s"NullabilityTransform(scope) ${scope.productPrefix} entry"
    scope.entries.toList.sorted.foreach(e => binder.bindScope(name, setting, e))
    records     = binder.recordsFor(name)
    runScope    = binder.run
    ownSubjects = binder.run.contributed(name)

  def policyReport: PolicyReport =
    PolicyReport.fromBindings(records) ++ PolicyReport(baseIntrusionFindings ++ deadScopeFindings ++ deadMemberFindings)

  /** Nullability is a fact about the SHARED SURFACE: a base that emits `Actor | Null` and a
    * dependent that emits `Actor` for the same member each compile alone and cannot compile
    * together (§1.5). The target shape and the scope are part of it for the same reason. */
  /** §1(b)'s no-op rule at the FINGERPRINT: the `target` segment is omitted when it is the
    * default (`Union`), so a port that never stated a target contributes NO segment for one —
    * an unstated key and a default one render the same string. A non-default one always
    * contributes, so the mechanism's arrival is flat by construction. */
  /** §1(b)'s no-op rule at the FINGERPRINT: the `nullableMembers` segment is omitted when empty,
    * so a port that never stated a member contributes NO segment for one — an unstated key and an
    * empty one render the same string. */
  def surfaceFingerprint: String =
    val targetSeg = target match { case Target.Union => ""; case t => s"|${t.tag}" }
    val memberSeg = if nullableMembers.isEmpty then "" else s"|members=${nullableMembers.toList.sorted.mkString(",")}"
    s"${annotations.toList.sorted.mkString(",")}$targetSeg|${scope.fingerprint}$memberSeg"

  /** every shared-surface SUBJECT this instance's policy is keyed on — the annotation FQNs and the
    * scope's declared entries, each through [[MergeablePolicy.subjectOf]].
    *
    * '''Both halves, and the scope half is the one that matters.''' A scope entry names a TYPE whose
    * annotated declarations are deliberately held back, and a dependent that adds one for a type its
    * BASE emits re-scopes a surface it does not own: the base emitted `Actor#getStage(): Stage |
    * Null` and the dependent's override of it would keep the upstream type, which is half an
    * override pair — exactly the shape §11.17 measured when a scoped-out parent sat beside a retyped
    * child. The annotation half is included on the trait's own instruction to over-approximate: an
    * annotation FQN inside a base's namespace that the base did not itself consume is a claim about
    * how the base's own marker is read, and a port that means it can say so by naming the base's
    * drop.
    */
  def subjects: Set[String] = (annotations ++ nullableMembers ++ scope.entries).map(MergeablePolicy.subjectOf)

  /** THE MERGE CONTRACT (DESIGN.md §8.13). Three tables, and each composes differently — which is
    * the whole reason `MergeablePolicy` is a contract the PHASE answers rather than a union the
    * engine performs.
    *
    *   - '''`annotations` UNION.''' Each FQN independently selects the declarations it marks, and
    *     nothing about one entry changes what another does. Both inputs keep their behaviour on
    *     their own keys, which is `SurfaceFold`'s first obligation, satisfied by arithmetic.
    *   - '''`target` must AGREE, or the merge refuses.''' It is not a key set; it is the SHAPE every
    *     retyped declaration takes. `T | Null` and `Nullable[T]` are two different emitted
    *     signatures for one member, so a "merge" of them is a choice, and a choice is the thing a
    *     refusal exists to prevent.
    *   - '''`scope` unions its ENTRIES — in BOTH directions, and that is not the same as unioning
    *     the region.''' An entry means "hold this back" under [[RuleScope.Everywhere]] and "move
    *     this" under [[RuleScope.Only]], so honouring both inputs' entries is the union of the sets
    *     either way — and the effect on the covered region therefore runs in OPPOSITE directions:
    *     `Everywhere(except)` gets SMALLER as excepts accumulate, `Only(include)` gets BIGGER. A
    *     merge rule written as "compose the region" would have had to pick one of those and would
    *     have been silently wrong for the other; a merge rule written as "honour every entry" is
    *     right for both, which is why this is the form.
    *
    * '''A base `Everywhere` and a dependent `Only` REFUSE, and the refusal is not squeamishness.'''
    * There is no entry set that preserves both: `Only` says as much by what it OMITS as by what it
    * lists — everything unnamed is deliberately held back — so a merged `Everywhere` would move
    * every declaration the `Only` side excluded, while a merged `Only` would hold back everything
    * the `Everywhere` side covers. That includes the DEFAULT `Everywhere(Set.empty)`: "the whole
    * program" is a direction, not an absence of one, and a port that wants the other direction
    * spells the base's scope the same way the base does.
    *
    * `added` is the SUBJECT side of what the later instance contributes — the annotation FQNs and
    * the scope entries this instance did not already hold. Those are the names a dependent could use
    * to re-scope a base's emitted surface, which is what `SurfaceFold` screens against `governs`,
    * and they are the keys the run holds this module's own policy findings to.
    */
  def mergedWith(later: Phase): Either[String, MergeablePolicy.Merged] = later match
    case o: NullabilityTransform =>
      // TARGET: a dependent at the DEFAULT (`Union`) inherits the base's target — it is not this
      // module's to choose (§1.5). A dependent that explicitly states a non-default target must
      // AGREE with the base's, or the merge refuses. Both sides non-default and different is a
      // choice the merge will not make.
      val mergedTarget = (target, o.target) match
        case (a, Target.Union) => Right(a)       // dependent inherits
        case (Target.Union, b) => Right(b)       // base at default, dependent chooses
        case (a, b) if a == b  => Right(a)       // both agree
        case (a, b)            => Left(
          s"""both modules state a nullability TARGET, "${a.tag}" and "${b.tag}" — the """ +
            "shape every retyped declaration takes is one emitted signature per member, so two " +
            "answers is a choice and not a composition")
      val scopeMerged: Either[String, RuleScope] = (scope, o.scope) match
        case (RuleScope.Everywhere(a), RuleScope.Everywhere(b)) => Right(RuleScope.Everywhere(a ++ b))
        case (RuleScope.Only(a), RuleScope.Only(b))             => Right(RuleScope.Only(a ++ b))
        case (mine, theirs)                                     => Left(
          s"""both modules scope `$name`, one `${mine.productPrefix}` and one """ +
            s"""`${theirs.productPrefix}` — the two point in OPPOSITE directions (an entry EXCLUDES """ +
            "under one and INCLUDES under the other, and `Only` states as much by omission as by " +
            "listing), so no entry set preserves both")
      (mergedTarget.left.toOption.toList ++ scopeMerged.left.toOption.toList) match
        case Nil => (for { t <- mergedTarget; s <- scopeMerged } yield
          MergeablePolicy.Merged(
            new NullabilityTransform(annotations ++ o.annotations, t, s,
              nullableMembers ++ o.nullableMembers),
            o.subjects -- subjects)
        )
        case whys => Left(whys.mkString("; "))
    case other =>
      Left(s"`${other.name}` is not a `NullabilityTransform`, so there is no policy to compose")

  // -------------------------------------------------------------------------
  // per-run state
  // -------------------------------------------------------------------------

  private val issues = collection.mutable.ListBuffer[Finding]()

  /** the NEW type of an annotated occurrence: a value's or parameter's declared type, or a
    * METHOD's RESULT. One map, because a method symbol never also denotes a value. */
  private var newTypes: Map[SymId, TypeRepr] = Map.empty
  /** symbols whose type is now `W[...]` — wrapper mode's own record of what it moved, which is the
    * only thing it is allowed to conclude anything from (§4.56). */
  private var wrapped: Map[SymId, TypeRepr] = Map.empty
  /** every TYPE-VARIABLE symbol in this program, by NAME — a type parameter's `info` is a
    * `TypeBounds` and nothing else's is (the same structural test [[mentionsTypeParam]] makes).
    *
    * Captured for the WALK, which has to ask *can this type be WRITTEN here* at a node whose
    * coercion helpers take no `Program` — and passing one would turn `coerceTo` into a context
    * function at the one call site that hands it over as a VALUE (`mapReturns`). The name is kept
    * beside the id because it is what the refusal has to say: a reader needs `T`, not an id. */
  private var typeVars: Map[SymId, (String, SymId)] = Map.empty

  private var wrapperSym, applySym, emptySym, getSym, orNullSym, isEmptySym, notSym, boolSym = SymId.None
  /** the primitive symbol IDs — cached at RUN time so [[coerceTo]] can decide `.get` vs `.orNull`
    * without threading a `Program` through a function passed as a value. */
  private var primSyms: Set[SymId] = Set.empty
  /** the unit currently being walked — see the walk in [[run]] for why a seam cannot be attributed
    * to the callee it was found at. */
  private var currentUnit: SymId = SymId.None
  /** members whose bodies contain `.orNull` calls inserted by this phase. After the tree walk, each
    * receives `@scala.annotation.nowarn("msg=deprecated")` to suppress the lint warning lls
    * deliberately places on `orNull` — the same pattern sge uses at every Java interop boundary
    * (sge's `nullable-guide.md`, e.g. `RemoteInput.scala:359`). Recorded as a `Decision` so the
    * porter note names the phase and key. */
  // orNullMembers removed — the `@nowarn` scan is now in SuppressionPhase (late, post-retarget)

  /** Every seam and refusal this run produced, restricted to the units the run EMITS.
    *
    * A dependent port's `Program` holds its base's units too, and a refusal inside one of those is
    * the BASE's finding, reported by a repository that cannot act on it (`ENGINE-LIMITS.md` D2). A
    * base port passes `program.units` and this is the identity. */
  def boundary(units: List[Tree.ClassDef]): List[Finding] =
    val emitted = units.map(_.symbol).toSet
    issues.toList.filter(f => emitted(f.unit)).sortBy(f => (f.origin.javaPath, f.origin.line, f.subject, f.issue.toString))

  // -------------------------------------------------------------------------
  // the run
  // -------------------------------------------------------------------------

  override def run(program: Program): Program =
    // Every per-run value is reset HERE, because a phase instance is reused across two translations
    // (`Determinism.Full` does exactly that, and a port with two source sets shares one phase list)
    // and a cached answer from the first run is a wrong answer in the second (§5.1).
    issues.clear(); intrusions.clear(); observedEntries.clear(); planned = false
    newTypes = Map.empty; wrapped = Map.empty; overridingRead = false; typeVars = Map.empty
    primSyms = Set.empty; matchedMembers.clear()
    // §1(b): an empty policy needs no code path. Nothing bound — no annotation configured (or every
    // configured one named nothing) AND no nullableMembers — and the program is returned untouched.
    if boundAnnots.isEmpty && nullableMembers.isEmpty then return program

    var table = program.symbols
    var next  = program.symbols.all.map(_.id.raw).maxOption.getOrElse(-1) + 1
    def mintOrReuse(fqn: String, simple: String, owner: SymId = SymId.None, info: TypeRepr = TypeRepr.NoType): SymId =
      externalNamed(program, fqn) match
        case Some(id) => id
        case scala.None =>
          val id = SymId(next); next += 1
          table = table.updated(Symbol(id, simple, fqn, Flags(), owner, info))
          id

    val nullSym = mintOrReuse(NullFqn, "Null")
    val nullRef = TypeRepr.TypeRef(TypeRepr.NoPrefix, nullSym)

    target match
      case Target.Union => ()
      case Target.Named(fqn) =>
        wrapperSym = mintOrReuse(fqn, fqn.split('.').last)
        applySym   = mintOrReuse(fqn + ".apply", "apply", wrapperSym)
        emptySym   = mintOrReuse(fqn + ".empty", "empty", wrapperSym)
        getSym     = mintOrReuse(fqn + ".get", "get", wrapperSym)
        orNullSym  = mintOrReuse(fqn + ".orNull", "orNull", wrapperSym)
        isEmptySym = mintOrReuse(fqn + ".isEmpty", "isEmpty", wrapperSym)
        notSym     = mintOrReuse(NotOp, "unary_!")
        boolSym    = mintOrReuse("scala.Boolean", "Boolean")
      case Target.OptionTarget =>
        wrapperSym = mintOrReuse(OptionFqn, "Option")
        applySym   = mintOrReuse(OptionFqn + ".apply", "apply", wrapperSym)
        emptySym   = mintOrReuse(NoneFqn, "None")
        getSym     = mintOrReuse(OptionFqn + ".get", "get", wrapperSym)
        orNullSym  = mintOrReuse(OptionFqn + ".orNull", "orNull", wrapperSym)
        isEmptySym = mintOrReuse(OptionFqn + ".isEmpty", "isEmpty", wrapperSym)
        notSym     = mintOrReuse(NotOp, "unary_!")
        boolSym    = mintOrReuse("scala.Boolean", "Boolean")

    // cache primitive symbol IDs so `coerceTo` can decide `.get` vs `.orNull` without `Program`
    primSyms = program.symbols.all.iterator.filter(s => PrimitiveNames(s.fullName)).map(_.id).toSet

    /** the target shape, applied to the annotated occurrence's CURRENT type. */
    def nullable(t: TypeRepr): TypeRepr = target match
      case Target.Union        => TypeRepr.OrType(t, nullRef)
      case Target.Named(_) | Target.OptionTarget =>
        TypeRepr.AppliedType(TypeRepr.TypeRef(TypeRepr.NoPrefix, wrapperSym), List(t))

    def alreadyNullable(t: TypeRepr): Boolean = t match
      case TypeRepr.OrType(_, TypeRepr.TypeRef(_, s))        => s == nullSym
      case TypeRepr.AppliedType(TypeRepr.TypeRef(_, s), _)   => s == wrapperSym && wrapperSym != SymId.None
      case _                                                  => false

    // ---- which declarations the annotations name, and which of them the engine can honour ----
    //
    // In SYMBOL-ID order, so two runs of the same program plan identically and the ids this phase
    // mints are numbered the same way (the hash order of `symbols.all` is not an order).
    val plan = collection.mutable.ListBuffer[Planned]()
    program.symbols.all.toList.sortBy(_.id.raw).foreach { s =>
      val hits = s.annotations.filter(a => headSym(a.tpe).exists(boundAnnots.contains))
      // A member is selected by EITHER an annotation OR an explicit `nullableMembers` entry.
      // Annotations take precedence (they carry `hits` to strip); `nullableMembers` is the fallback
      // for a member whose java carries no annotation but whose hand port wraps in Nullable.
      val memberHit = if hits.nonEmpty then scala.None
                      else nullableMembers.find(_ == s.fullName)
      if (hits.nonEmpty || memberHit.isDefined) && program.owns(s.id) then
        val key = if hits.nonEmpty
                  then hits.flatMap(a => headSym(a.tpe)).flatMap(boundAnnots.get).sorted.head
                  else { matchedMembers += memberHit.get; memberHit.get }
        // THE ONE KEY KIND THAT CAN SELECT A BASE'S DECLARATIONS WITHOUT NAMING A BASE FQN.
        // Refused before anything else is asked, because the alternative is a §1.5 divergence
        // nothing else in the run can see — see `intrudesOnBase`.
        if intrudesOnBase(program, s, key) then baseIntrusion(program, s, key)
        // The DIRECTION matters, and reading `entryFor` alone gets it wrong for `Only`: an entry is
        // present for an EXCLUDED declaration under `Everywhere(except)` and for an INCLUDED one
        // under `Only(include)`. Ask the scope whether it includes the symbol, and quote the entry
        // that decided it when there is one — under `Only` a declaration is held back precisely
        // because NO entry names it, and the key an agent edits is then the whole list.
        //
        // …and RECORD the entry either way. An entry that names no ANNOTATED declaration decided
        // nothing whichever direction it points, and that is the §1(b) no-op only this phase can
        // see: `PolicyBinder.bindScope` asks "does anything in the program fall inside this region",
        // which a real type answers `yes` to whether or not it carries an annotation.
        else
          val entry = scope.entryFor(program, s)
          entry.foreach(observedEntries += _)
          if !scope.includes(program, s) then
            scopedOut(program, s, entry.getOrElse(scope.fingerprint))
          else
            slotOf(program, s) match
              case scala.None => refuse(program, s, key, Issue.NotAValuePosition)
              case Some((slot, was)) =>
                if alreadyNullable(was) then ()                       // idempotent: nothing to do
                else if hits.exists(_.args.nonEmpty) then refuse(program, s, key, Issue.AnnotationArguments)
                else if s.flags.isParam && s.flags.isVararg then refuse(program, s, key, Issue.VarargParameter)
                else if isPrimitive(program, was) then refuse(program, s, key, Issue.PrimitiveType)
                else if wrapperCrossesOverride(program, s) then refuse(program, s, key, Issue.OverrideCrossing)
                else
                  // RETYPED AND COUNTED, which is not a contradiction: the declaration is fine and
                  // every USE of it is not (see `Issue.AbstractTypeParameter`). Recorded before the
                  // plan entry so the order of the two reads as one act.
                  if target == Target.Union && mentionsTypeParam(program, was) then
                    refuse(program, s, key, Issue.AbstractTypeParameter)
                  plan += Planned(s, key, slot, was, hits)
    }
    // ---- the two things a SCOPE owes, both PLAN-TIME and both previously a compile hunt ----
    // The plan walked every symbol, so `observedEntries` is complete and the never-fired complement
    // is meaningful. Before this point it is not, which is what `planned` says.
    planned = true

    // ---- THE OVERRIDE EDGE THE ANNOTATION TRAVELS DOWN (wrapper mode only) ----------------------
    //
    // Java's nullability annotation is a fact about the MEMBER, and an override inherits the
    // contract whether or not it repeats the marker — javac ignores both, so an upstream has no
    // reason to write it twice and routinely does not. Scala has no such freedom: a wrapper retype
    // changes the SIGNATURE, and an override that keeps the upstream spelling is
    // `E038 method … has a different signature than the overridden declaration` — or, at a generic
    // result, `E007 Found: W[T] / Required: T` in a body that returns exactly what the parent gave
    // it.
    //
    // NEITHER IS VISIBLE UNTIL THE PORT IS AT 0 TYPER ERRORS for the first of them (`RefChecks` does
    // not run before that, `CLAUDE.md` §3), and the shape is a DEPENDENT's by nature: a base with an
    // unannotated override of its own annotated member would not compile, so the corpus's bases have
    // none and the whole class arrives one module out — TextraTypist's `setParent` ×2 and VisUI's
    // `DragPane#findActor`, three errors that no count and no member digest could see.
    //
    // So the retype travels the override graph DOWNWARD, at the same slot and the same position,
    // and every derived entry passes the same gates the annotated one did (scope, primitives,
    // varargs, already-nullable). It carries the ANNOTATED member's key, because that is the entry
    // an agent edits to change the outcome, and no annotation to consume — the overrider has none
    // to strip.
    def paramIndexOf(s: Symbol): Int =
      program.definitionOf(s.owner).collect { case d: Tree.DefDef =>
        d.paramss.flatten.indexWhere(_.symbol == s.id)
      }.getOrElse(-1)

    if target != Target.Union then
      val graph   = OverrideGraph.build(program)
      val claimed = collection.mutable.Set.from(plan.iterator.map(_.sym.id))

      // ---- BEAN PAIR: a getter/setter pair is ONE SLOT (BEFORE override propagation) ----------
      //
      // A bean pair collapsed by `BeanPropertyTransform` is a property with ONE backing store:
      // `def stage: T` / `def stage_=(v: T)`. If the getter's return type carries `@Null` and is
      // widened to `Nullable[T]`, the setter's parameter must widen too — java's unannotated
      // `setStage(Stage)` accepts null anyway (JLS 4.1), so widening the setter is faithful, and
      // leaving it un-widened makes `x.stage = x.stage` a type error (13 errors in
      // Group/Stage/Dialog/SelectBox, measured). Placed BEFORE the override propagation so the
      // widened setter parameter propagates to overrides too (Group.stage_=, SelectBox.stage_=).
      plan.toList.filter(_.slot == Slot.Return).foreach { x =>
        val setterName = x.sym.name + "_="
        val getterOwner = x.sym.owner
        program.definitionOf(getterOwner).collect { case cd: Tree.ClassDef =>
          cd.body.collectFirst {
            case d: Tree.DefDef if program.symbolOf(d.symbol).exists(_.name == setterName) =>
              d.paramss.flatten.headOption.foreach { param =>
                program.symbolOf(param.symbol).foreach { paramSym =>
                  if !claimed.contains(paramSym.id) && program.owns(paramSym.id) then
                    slotOf(program, paramSym) match
                      case Some((Slot.Param, was)) if !alreadyNullable(was) &&
                        !isPrimitive(program, was) &&
                        !(paramSym.flags.isParam && paramSym.flags.isVararg) =>
                        claimed += paramSym.id
                        plan += Planned(paramSym, x.key, Slot.Param, was, Nil)
                      case _ => ()
                }
              }
          }
        }
      }

      plan.toList.foreach { x =>
        val (member, pos) =
          if x.slot == Slot.Param then (x.sym.owner, paramIndexOf(x.sym)) else (x.sym.id, -1)
        // …AND ONLY WHERE JAVA HAS AN OVERRIDE EDGE AT ALL. `OverrideGraph` matches members DOWN
        // the subclass chain by name and signature, which is exactly right for a method and is a
        // FABRICATED edge for a CONSTRUCTOR: every `<init>` is named `<init>`, so `ImageButton`'s
        // one-`Drawable` constructor reads as an "override" of `Button`'s and the annotation
        // travels an edge java does not have. Measured: 17 member digests and 4 spurious
        // `OverloadErasureClash` rows on libGDX core, at 0 errors either way — the shape §3 says a
        // green compile cannot see. A `static` method is excluded for the same reason (JLS 8.4.8.2
        // hides, it does not override), and a `final` one has no overriders to find.
        val overridable = program.symbolOf(member).exists(m =>
          m.name != "<init>" && !m.flags.isStatic && !m.flags.isFinal)
        if member != SymId.None && overridable && (x.slot != Slot.Param || pos >= 0) then
          graph.overriders(member).foreach { d =>
            val tgt =
              if pos < 0 then program.symbolOf(d)
              else program.definitionOf(d).collect { case dd: Tree.DefDef =>
                dd.paramss.flatten.lift(pos).map(_.symbol)
              }.flatten.flatMap(program.symbolOf)
            tgt.foreach { s =>
              if !claimed.contains(s.id) && program.owns(s.id) && scope.includes(program, s) then
                slotOf(program, s) match
                  case Some((slot, was)) if slot == x.slot && !alreadyNullable(was) &&
                                            !isPrimitive(program, was) &&
                                            !(s.flags.isParam && s.flags.isVararg) =>
                    claimed += s.id
                    plan += Planned(s, x.key, slot, was, Nil)
                  case _ => ()
            }
          }
      }

    // ---- …and the third: an OVERLOAD SET the retype would ERASE FLAT (wrapper mode only) ----
    // Java kept `f(Font)` and `f(BitmapFont)` apart BY ERASURE; a wrapper erases both to one
    // descriptor, so the pair becomes `E120 Conflicting definitions` at two members that are
    // otherwise perfect translations. Refused HERE, before anything is retyped, because the two
    // declarations have to move or stay TOGETHER and only the plan can see both.
    val kept = if target == Target.Union then plan.toList else refuseErasureClashes(program, plan.toList)
    plan.clear(); plan ++= kept
    scopedOutParents(program, plan.toList)
    if plan.isEmpty then return program

    newTypes = plan.iterator.map(p => p.sym.id -> nullable(p.was)).toMap
    if target != Target.Union then wrapped = newTypes

    /** annotated PARAMETERS, by their owning method and BY POSITION — the method's signature has to
      * move with its parameter symbols, or the two disagree and every caller resolves against the
      * older of them.
      *
      * By POSITION and never by NAME, and that is a measured correction rather than a preference: a
      * `MethodType`'s parameter list and the `DefDef`'s are parallel by construction, while the
      * NAMES are not — an earlier phase may rewrite a parameter SLOT without touching the
      * method's `info`, which is exactly what the reassigned-parameter transform does when it
      * repurposes a `content` parameter as a local `var` and mints `content$arg` for the slot. Read
      * by name, the annotated declaration's emitted parameter moved and its signature silently did
      * not — a disagreement no count can see, found by binding the real corpus policy and reading
      * the artifact rather than by any spec. Name matching survives only where there is no
      * declaration to index against, which for an owned method there never is. */
    val paramsByOwner: Map[SymId, Map[Int, TypeRepr]] =
      plan.iterator.filter(_.slot == Slot.Param).toList.groupBy(_.sym.owner).flatMap { (owner, ps) =>
        program.definitionOf(owner).collect { case d: Tree.DefDef =>
          val at = d.paramss.flatten.map(_.symbol).zipWithIndex.toMap
          owner -> ps.flatMap(p => at.get(p.sym.id).map(_ -> newTypes(p.sym.id))).toMap
        }
      }.toMap

    val consumed: Map[SymId, List[Annot]] = plan.iterator.map(p => p.sym.id -> p.hits).toMap

    def methodType(id: SymId, mt: TypeRepr.MethodType): TypeRepr.MethodType =
      val ps = paramsByOwner.getOrElse(id, Map.empty)
      TypeRepr.MethodType(mt.params.zipWithIndex.map((nt, i) => nt._1 -> ps.getOrElse(i, nt._2)),
                          newTypes.getOrElse(id, mt.result), mt.isImplicit)

    val retyped = table.all.map { s =>
      // The consumed annotation is STRIPPED: the type now states the fact, and leaving it would
      // both double-state it and re-impose the annotation jar on every port that consumes the
      // output. A REFUSED site keeps its annotation, which is what makes the refusal readable at
      // the line as well as countable in the report.
      val s1 = consumed.get(s.id).map(as => s.copy(annotations = s.annotations.filterNot(as.contains))).getOrElse(s)
      val info = s1.info match
        case mt: TypeRepr.MethodType                          => methodType(s.id, mt)
        case TypeRepr.PolyType(tps, mt: TypeRepr.MethodType)  => TypeRepr.PolyType(tps, methodType(s.id, mt))
        case other                                            => newTypes.getOrElse(s.id, other)
      if info == s1.info then s1 else s1.copy(info = info)
    }
    val symbols = SymbolTable(retyped)

    given Program = program.rebuilt(symbols = symbols)
    typeVars = symbols.all.iterator.collect {
      case v if v.info.isInstanceOf[TypeRepr.TypeBounds] => v.id -> (v.name, unitOf(summon[Program], v.id))
    }.toMap
    recordDecisions(program, plan.toList, symbols)
    // The unit is carried WHILE it is walked, because a seam found inside a call has no other way
    // to say which module owns it: the callee is an EXTERNAL symbol whose owner chain ends outside
    // this program, so attributing the finding to it would put it in no unit at all and the
    // emitted-units filter would silently drop the one finding the seam exists to produce.
    val units = program.units.map { u => currentUnit = u.symbol; StandardTraversal.mapClassDef(this, u) }
    currentUnit = SymId.None

    // The `@nowarn("msg=deprecated")` scan that used to live here has moved to SuppressionPhase,
    // a LATE phase that runs AFTER every retyping phase. The scan here ran BEFORE the retarget
    // phases, so it annotated members whose deprecated references a later retarget removed —
    // leaving stale `@nowarn` annotations that `-Wunused:nowarn` reported (237 on libGDX core
    // after the Array -> DynamicArray retarget). SuppressionPhase sees the FINAL tree and
    // annotates only members that still contain a deprecated call.

    program.rebuilt(units, symbols)

  // -------------------------------------------------------------------------
  // planning helpers
  // -------------------------------------------------------------------------

  /** WHICH occurrence of a declaration's type the annotation names, and what that type is today.
    *
    * Java's nullability annotations are DECLARATION-position, not `TYPE_USE`: on a method they
    * state the RESULT, on a field or parameter the declared type. A TYPE declaration has no such
    * occurrence at all, which is why it is `None` here and a counted refusal at the call site —
    * retyping a class's own `info` would rewrite what the class IS. */
  private def slotOf(p: Program, s: Symbol): Option[(Slot, TypeRepr)] =
    if p.definitionOf(s.id).exists { case _: Tree.ClassDef | _: Tree.TypeDef => true; case _ => false } then scala.None
    else s.info match
      case TypeRepr.MethodType(_, r, _)                          => Some(Slot.Return -> r)
      case TypeRepr.PolyType(_, TypeRepr.MethodType(_, r, _))    => Some(Slot.Return -> r)
      case TypeRepr.NoType | TypeRepr.NoPrefix                   => scala.None
      case _: TypeRepr.TypeBounds                                => scala.None
      case other if s.flags.isParam                              => Some(Slot.Param -> other)
      case other if Decision.isDeclaration(p, s)                 => Some(Slot.Field -> other)
      // a method LOCAL: not surface, and Java's own `@Target` normally allows it. Recorded rather
      // than retyped — a local's type is an implementation detail no consumer can see.
      case _                                                     => scala.None

  /** THE OVERLOAD SETS A WRAPPER WOULD COLLAPSE — refused, in both members, at the positions that
    * carry java's distinction.
    *
    * Java resolves overloads on the SOURCE signature and the JVM keeps them apart on the ERASED
    * one; scala has the same one erasure, so a pair java could write is a pair scala can write too
    * — until a phase retypes a parameter to something whose erasure is WIDER than what it replaced.
    * A wrapper is exactly that: erasure drops type arguments, so `W[Font]` and `W[BitmapFont]`
    * arrive at one descriptor (an opaque `W` drops all the way to `Object`) and scalac reports
    * `E120 Conflicting definitions … have the same type … after erasure` — at two constructors
    * whose names, arities and bodies are all correct, with nothing else in the run able to see it.
    * Measured on TextraTypist's `Styles.TextButtonStyle`, whose `(Drawable, Drawable, Drawable,
    * Font)` and `(Drawable, Drawable, Drawable, BitmapFont)` constructors are ordinary java.
    *
    * WHAT IS REFUSED is the minimum that restores the distinction: the planned parameters at every
    * position where the two members' PRE-retype types differ. Both sides, because refusing one is
    * an arbitrary choice between two declarations neither of which is more the port's than the
    * other, and because the answer must not depend on which member the symbol table walked first.
    * A position the two already agree on carries no distinction and keeps its wrapper.
    *
    * The comparison is by HEAD SYMBOL and not by a real erasure, which is deliberately the
    * UNDER-approximating direction: two types this test calls different may still erase together
    * (a type variable and its bound), and the residue of that is a compile error, which is loud.
    * An over-approximation would silently decline a retype nothing was wrong with. */
  private def refuseErasureClashes(p: Program, plan: List[Planned]): List[Planned] =
    val plannedParam: Map[SymId, Planned] =
      plan.iterator.filter(_.slot == Slot.Param).map(x => x.sym.id -> x).toMap
    if plannedParam.isEmpty then return plan

    /** the erasure-ish key of a parameter's CURRENT type — its head symbol, or the type itself
      * where there is no head to read. */
    def key(t: TypeRepr): String =
      headSym(t).flatMap(p.symbolOf).map(_.fullName).getOrElse(t.toString)

    // every OWNED method, with its parameter symbols and their declared types, in order.
    val methods: List[(Symbol, List[(SymId, TypeRepr)])] =
      p.symbols.all.toList.sortBy(_.id.raw).flatMap { m =>
        p.definitionOf(m.id).collect { case d: Tree.DefDef =>
          m -> d.paramss.flatten.map(v => v.symbol -> v.tpt.tpe)
        }
      }

    val refused = collection.mutable.Set[SymId]()
    methods.groupBy((m, ps) => (m.owner, m.name, ps.size)).valuesIterator.foreach { group =>
      val members = group.sortBy((m, _) => m.id.raw)
      for
        i <- members.indices; j <- (i + 1) until members.size
        (_, as) = members(i); (_, bs) = members(j)
      do
        val differ = as.indices.filter(k => key(as(k)._2) != key(bs(k)._2))
        // a POST-retype key: every planned parameter arrives at the wrapper's own erasure, which is
        // one token whatever its element was — that is the whole of the collapse.
        def post(x: (SymId, TypeRepr)) = if plannedParam.contains(x._1) then "<wrapper>" else key(x._2)
        val collapses = differ.nonEmpty && as.indices.forall(k => post(as(k)) == post(bs(k)))
        if collapses then
          differ.foreach { k =>
            List(as(k)._1, bs(k)._1).foreach(id => plannedParam.get(id).foreach { pl =>
              if refused.add(id) then refuse(p, pl.sym, pl.key, Issue.OverloadErasureClash)
            })
          }
    }
    plan.filterNot(x => refused.contains(x.sym.id))

  /** does this type mention an ABSTRACT TYPE PARAMETER anywhere?
    *
    * Decided STRUCTURALLY — a type parameter's `info` is a `TypeBounds` and nothing else's is —
    * never from a name, and never from "is it one letter". `Foo[T]` counts as much as bare `T`:
    * `Foo[T] | Null` is transparent, but a `T` INSIDE it is where `Null` stops being a subtype, and
    * a port reading this number wants every declaration whose transparency depends on an abstract
    * type, not only the ones typed by one directly. */
  private def mentionsTypeParam(p: Program, t: TypeRepr): Boolean = t match
    case TypeRepr.TypeRef(_, s)       => p.symbolOf(s).exists(_.info.isInstanceOf[TypeRepr.TypeBounds])
    case TypeRepr.AppliedType(tc, as) => mentionsTypeParam(p, tc) || as.exists(mentionsTypeParam(p, _))
    case TypeRepr.OrType(l, r)        => mentionsTypeParam(p, l) || mentionsTypeParam(p, r)
    case TypeRepr.AndType(l, r)       => mentionsTypeParam(p, l) || mentionsTypeParam(p, r)
    case TypeRepr.TypeBounds(_, _)    => true
    case _                            => false

  /** Scala's own primitives — `scala.Int` and friends, which cannot be null and for which the
    * annotation is a mistake somewhere upstream. ENGINE identity, not per-library policy: these
    * are the names the frontend interns for Java's primitives, exactly as `TestFrameworkTransform`
    * knows JUnit's. No bare-primitive annotation exists anywhere in the corpus, and the negative
    * spec is what keeps it that way. */
  private def isPrimitive(p: Program, t: TypeRepr): Boolean =
    headSym(t).flatMap(p.symbolOf).exists(s => PrimitiveNames(s.fullName))

  /** WRAPPER mode changes a member's SIGNATURE, so it cannot move one end of an override pair
    * alone. Union mode can and does — measured: without `-Yexplicit-nulls` a `T | Null` return may
    * be narrowed by an override and a `T` return may be widened by one, both compile.
    *
    * Until the shared override closure exists, the test is the conservative one the frontend
    * already affords: a member that OVERRIDES something, or that any owned overriding member
    * matches by name and descriptor (so the parent end of the same pair is refused too). It
    * over-approximates across unrelated hierarchies, which refuses a retype that would have been
    * safe and counts it — never the other way round. Swapping the predicate for the real closure is
    * a one-line change here and nothing else. */
  private def wrapperCrossesOverride(p: Program, s: Symbol): Boolean =
    if !target.isWrapper then false
    else if s.flags.isParam then p.symbolOf(s.owner).exists(m => crosses(p, m))
    else crosses(p, s)

  private def crosses(p: Program, s: Symbol): Boolean =
    s.flags.isOverride

  private var overridingCache: Set[(String, Option[Descriptor])] = Set.empty
  private var overridingRead  = false
  private def overriding(p: Program): Set[(String, Option[Descriptor])] =
    if !overridingRead then
      overridingCache = p.symbols.all.iterator.filter(s => s.flags.isOverride && p.owns(s.id))
        .map(s => s.name -> s.descriptor).toSet
      overridingRead = true
    overridingCache

  // -------------------------------------------------------------------------
  // findings
  // -------------------------------------------------------------------------

  // -------------------------------------------------------------------------
  // the BASE-SURFACE screen — a key this module added, reaching a declaration this run does not emit
  // -------------------------------------------------------------------------

  /** Would honouring `key` here RETYPE A DECLARATION THIS RUN DOES NOT EMIT, on the strength of
    * policy THIS module added?
    *
    * ==The hole this closes, and why nothing else could see it==
    * `SurfaceFold`'s `governs` screen refuses a subject inside a base's claimed namespace that the
    * base does not account for (`DESIGN.md` §8.13) — and an ANNOTATION FQN is the one policy key
    * that selects declarations WITHOUT naming any of them. `org.jspecify.annotations.Nullable` is
    * inside no base's claim, so it is admitted, correctly: the key itself edits nothing. What it
    * SELECTS is another matter — the plan loop walks `Program.owned`, which in a dependent roots on
    * every unit including the base's (`ENGINE-LIMITS.md` D2's substrate note) — so a dependent whose
    * base's Java carries that same third-party annotation retypes the base's declarations, which the
    * base's own run emitted untouched. Two ports that each compile alone and cannot compile
    * together: §1.5's failure, through the one door the fold cannot watch.
    *
    * It is invisible BY CONSTRUCTION, which is why it is a screen and not a check. D2's module
    * scope drops the `decisions.tsv` rows (they are about the base's declarations) and
    * [[boundary]]'s emitted-unit filter drops any finding raised at one — so the retype would move
    * no number anywhere.
    *
    * ==Why only the annotation half==
    * A SCOPE entry names an FQN, so an entry that reaches a base declaration is by construction
    * inside that base's `governs` claim and is already a FATAL `SurfaceIntrusion` at manifest time.
    * The annotation half is the only one whose key does not name what it moves.
    *
    * ==And why an INHERITED key is not screened==
    * `contributed` is the fold's record of what THIS manifest added. A key the base declared is one
    * the base's own run applied to the same declarations, identically — screening it would refuse
    * the composition the merge contract exists to allow.
    */
  private def intrudesOnBase(p: Program, s: Symbol, key: String): Boolean =
    ownSubjects.exists(_.contains(MergeablePolicy.subjectOf(key))) && !runScope.emits(unitOf(p, s.id))

  // -------------------------------------------------------------------------
  // the SCOPE's own two obligations — a dead entry, and the closure it does not compute
  // -------------------------------------------------------------------------

  /** every declared scope entry this run OBSERVED deciding something — the input to
    * [[RuleScope.neverFired]], and the only honest one. */
  private val observedEntries = collection.mutable.Set.empty[String]
  /** did the plan loop run? `policyReport` is read whether or not the phase ever ran (the whole
    * point of deriving the never-fired half from the BINDING), and "no entry fired" means nothing
    * before the walk that would have fired them. */
  private var planned = false

  /** A DECLARED SCOPE ENTRY THAT NAMED NO ANNOTATED DECLARATION — the one §1(b) no-op the ordinary
    * never-fired machinery cannot see, and `ENGINE-LIMITS.md` K13's own instruction.
    *
    * `PolicyBinder.bindScope` asks *did anything in this program fall inside this region*, and a
    * real type answers `yes` whether or not it carries an annotation — so an entry that holds back
    * nothing BINDS. K13 measured exactly that: libGDX's first `nullabilityExempt` draft listed
    * `OrderedMap`, which declares no `@Null` of its own, and the entry held back nothing; with and
    * without it `members.tsv` was byte-identical and `policy` stayed 0. A byte-identity experiment
    * is not a report. This is.
    *
    * Only entries whose BINDING succeeded are reported, or an entry naming a type this program does
    * not contain would be reported twice — once by the binder as `NeverMatched` and once here — for
    * one mistake with one fix.
    */
  private def deadScopeFindings: List[PolicyFinding] =
    if !planned then Nil
    else
      val bound = records.filter(r => r.binding.isBound).map(_.entry).toSet
      scope.neverFired(observedEntries.toSet).intersect(bound).toList.sorted.map { e =>
        PolicyFinding(name, s"NullabilityTransform(scope) ${scope.productPrefix} entry", e,
          PolicyIssue.NeverMatched,
          "the entry names a region of this program, and NO declaration inside it carries a " +
            "configured nullability annotation — so it held nothing back (under `Everywhere`) or " +
            "let nothing through (under `Only`), and removing it would change no emitted byte. " +
            "`PolicyBinder.bindScope` cannot see this: it asks whether the REGION exists, which a " +
            "real type answers whether or not it is annotated. Delete the entry, or fix the FQN if " +
            "it was meant to name a different type.")
      }

  /** A `nullableMembers` ENTRY THAT NAMED NO DECLARATION — reported after the plan loop, so only
    * entries that matched no symbol are here. Like [[deadScopeFindings]], this is the one §1(b) no-op
    * nothing else in the run can see: a key that names nothing costs zero emitted bytes and zero
    * diagnostics. Unmatched entries are reported rather than silently ignored.
    *
    * Reported only after the plan loop ran (`planned`), because a run that skipped the walk has no
    * data to say "nothing matched". */
  private def deadMemberFindings: List[PolicyFinding] =
    if !planned || nullableMembers.isEmpty then Nil
    else
      (nullableMembers -- matchedMembers).toList.sorted.map { e =>
        PolicyFinding(name, "NullabilityTransform(nullableMembers) entry", e,
          PolicyIssue.NeverMatched,
          "the entry names a member that no symbol's `fullName` matched at run time. Either the " +
            "FQN is misspelled, the member was renamed by an earlier phase (use the post-rename name), " +
            "or the member does not exist in this program. Delete the entry, or fix the FQN.")
      }

  /** THE CLOSURE A `RuleScope` DOES NOT COMPUTE — a scoped-out PARENT beside a retyped CHILD.
    *
    * `ENGINE-LIMITS.md` K13's second measured rule, as a plan-time predicate. A scope entry naming a
    * generic container holds its annotated members back; an owned SUBTYPE that RE-STATES the
    * annotation on a same-named member is not covered by that entry and is retyped — half an
    * override pair, which is the one shape a union floor may not emit. Measured on libGDX: scoping
    * eleven types out took 35 errors to 6, and all six survivors were `SnapshotArray` and
    * `DelayedRemovalArray` overriding two annotated members each of the scoped-out `Array`. Adding
    * the two subclasses took it to 0 — and NOTHING computed the closure, so the compile was the only
    * thing that could find a missing entry. This turns that hunt into one run.
    *
    * **And it stops exactly where K13 says it does.** A subtype that merely INHERITS an annotated
    * member declares no annotation, so it is never PLANNED and never reaches this predicate — which
    * is why `OrderedMap` produces nothing here and adding an entry for it would be the dead policy
    * [[deadScopeFindings]] reports. The predicate reads `Definition.parents` and the annotation hits
    * the plan already computed; it invents no notion of overriding beyond the name, deliberately —
    * over-approximating names a pair a port can dismiss, while a signature test would need the
    * override closure this phase does not have (see [[wrapperCrossesOverride]]'s same note).
    *
    * §1(b): the fix is a scope entry in the library's manifest, never an engine change.
    */
  private def scopedOutParents(p: Program, plan: List[Planned]): Unit =
    if scope.isUnrestricted || plan.isEmpty then return
    def classOf(id: SymId): Option[Tree.ClassDef] =
      p.definitionOf(id).collect { case c: Tree.ClassDef => c }
    def parentsOf(c: Tree.ClassDef): List[SymId] =
      c.parents.flatMap { case tt: TypeTree => headSym(tt.tpe); case t: Term => headSym(t.tpe) }
    def annotated(s: Symbol): Boolean =
      s.annotations.exists(a => headSym(a.tpe).exists(boundAnnots.contains))
    /** the same-named member of `a` that carries a bound annotation, if there is one. */
    def annotatedMember(a: Tree.ClassDef, nm: String): Option[Symbol] =
      a.body.collectFirst {
        case d: Definition if p.symbolOf(d.symbol).exists(x => x.name == nm && annotated(x)) =>
          p.symbolOf(d.symbol).get
      }
    // one row per (retyped declaration, scoped-out ancestor), and the DECLARATION is the subject:
    // it is the end the port can move, and the end whose emitted signature is the wrong half.
    plan.map(_.sym).map(declarationOf).distinct.foreach { decl =>
      p.symbolOf(decl).foreach { d =>
        val seen = collection.mutable.Set.empty[SymId]
        def climb(t: SymId, fuel: Int): Unit =
          if fuel > 0 && seen.add(t) then
            classOf(t).foreach { cd =>
              p.symbolOf(t).foreach { ts =>
                if p.owns(t) && !scope.includes(p, ts) then
                  annotatedMember(cd, d.name).foreach { parent =>
                    issues += Finding(Issue.ScopedOutParent, d.fullName,
                      s"`${parent.fullName}` is held back by the `${scope.entryFor(p, ts).getOrElse(scope.fingerprint)}` " +
                        "scope entry while this override of it is retyped",
                      Decision.originOf(p, decl), unitOf(p, decl))
                  }
              }
              parentsOf(cd).foreach(climb(_, fuel - 1))
            }
        classOf(d.owner).foreach(cd => parentsOf(cd).foreach(climb(_, 64)))
      }
    }

  /** every base declaration a key of this module's selected, by key — one `PolicyFinding` per KEY,
    * because that is the string an agent edits (§4.575) and a row per declaration would report one
    * manifest mistake once per member of the base. */
  private val intrusions = collection.mutable.LinkedHashMap.empty[String, collection.mutable.ListBuffer[String]]

  private def baseIntrusion(p: Program, s: Symbol, key: String): Unit =
    intrusions.getOrElseUpdate(key, collection.mutable.ListBuffer.empty) += describe(p, s)

  /** §1(b), COUNTED and NON-FATAL, and the severity is the argument rather than a default: the
    * refusal has already made the emission correct — the declaration keeps exactly the type the
    * base's own run gave it — so there is nothing wrong with what this port WRITES. What is wrong
    * is what its manifest SAYS: a nullability contract stated for a namespace this module does not
    * own. A fatal finding would stop a run whose output is right; a silent one would leave the
    * author believing the annotation applies library-wide. The number is the honest middle, and it
    * reaches `policy`, which is scoped to this module's own keys already. */
  private def baseIntrusionFindings: List[PolicyFinding] =
    intrusions.toList.sortBy(_._1).map { (key, subjects) =>
      val shown = subjects.toList.sorted.distinct
      PolicyFinding(name, s"NullabilityTransform(annotations) `$key`", key, PolicyIssue.Unverifiable,
        s"REFUSED on ${shown.size} declaration(s) this run does NOT emit — they belong to a module " +
          "this one only resolves against, and this manifest is the one that added the annotation, " +
          "so retyping them would re-shape the SHARED surface from the dependent's side and the two " +
          "ports could not compile together (CLAUDE.md §1.5). They keep the type the base's own run " +
          s"gave them. Declare the annotation in the BASE's manifest if the contract is really the " +
          s"shared library's: ${shown.take(3).mkString(", ")}" +
          (if shown.sizeIs > 3 then s", … (${shown.size} in all)" else ""))
    }

  private def refuse(p: Program, s: Symbol, key: String, issue: Issue): Unit =
    issues += Finding(issue, s.fullName, s"`$key` on ${describe(p, s)}", Decision.originOf(p, s.id),
                      unitOf(p, s.id), declarationOf(p, s))

  /** THE DECLARATION a site belongs to — what a per-location selection keys on, and not the same
    * question as [[unitOf]].
    *
    * The commonest site this check reports is a PARAMETER or a method LOCAL, neither of which is a
    * declaration a policy key can name: `Decision.isDeclaration` is exactly that test, and its
    * answer for both is the enclosing executable. Climbing ONE level is enough by construction —
    * java nests no declaration inside a parameter — and the climb stops rather than continuing to
    * the unit, because a fallback that always answered would let one selection drain every row in a
    * file (see `NullabilityBoundaryCheck.Finding.at`). Where nothing above is a declaration either,
    * the honest answer is `SymId.None`: the site is UNSELECTABLE, which is a fact about the site and
    * not a reason to invent a coarser key for it. */
  private def declarationOf(p: Program, s: Symbol): SymId =
    if Decision.isDeclaration(p, s) then s.id
    else p.symbolOf(s.owner).filter(o => Decision.isDeclaration(p, o)).map(_.id).getOrElse(SymId.None)

  private def describe(p: Program, s: Symbol): String =
    if s.flags.isParam then
      s"parameter `${s.name}` of ${p.symbolOf(s.owner).map(_.fullName).getOrElse("?")}"
    else s"`${s.fullName}`"

  /** The COMPLEMENT of a retype, and it needs its own record for the reason `decisions.tsv` exists:
    * the declaration kept its upstream type while the code around it moved, so the row that would
    * have explained it is the one that is NOT there. Always `Reason.Configured` — an exclusion is a
    * policy entry by construction — and `ScopedOut` is one of the kinds a porter note is rendered
    * for, so the answer sits at the line as well as in the artifact.
    *
    * ==A PARAMETER is counted here and its decision is recorded ONE LEVEL OUT==
    * The two halves are asked of different things and used to be refused together, which left the
    * commonest scoped-out site with NO record anywhere. A parameter is not a subject a note can sit
    * above — `PorterNote.AtDeclaration` renders over a `def`/`val`/`class` — but the exclusion still
    * changes an emitted SIGNATURE, and it changes the enclosing method's. So the FINDING is filed at
    * the parameter (that is what makes the lane's arithmetic close) and the DECISION is recorded at
    * [[declarationOf]], naming the parameter in its own detail.
    *
    * Refusing both is not a smaller answer, it is an invisible one, and the shape is exactly what §5
    * refuses: a scope entry that holds back a PARAMETER removes that site's `AbstractTypeParameter`
    * row and adds nothing, so `nullability-boundary` FALLS with nothing to attribute the fall to —
    * indistinguishable, from every artifact a run publishes, from a check that stopped asking. And
    * the emitted text cannot stand in for it: the reason stated three lines below is that a
    * PARAMETER's surviving marker is one of the two the emitter does not render at all. */
  private def scopedOut(p: Program, s: Symbol, entry: String): Unit =
    val at = if s.flags.isParam then declarationOf(p, s) else s.id
    if s.flags.isParam || Decision.isDeclaration(p, s) then
      // …and COUNTED, beside the decision, for the reason every other lane of this check exists: a
      // residue nobody counts is a residue that grows. The only other evidence a scoped-out
      // declaration leaves is its surviving upstream MARKER in the emitted text, and the emitter
      // renders a class's and a method's annotations and neither a field's nor a parameter's — so
      // grepping the output under-reports this by construction, which is exactly the shape §5 says
      // must be a number instead. The finding is attributed to the declaration's own unit, so a
      // dependent does not report its base's exclusions (D2).
      issues += Finding(Issue.ScopedOut, s.fullName, s"`$entry` on ${describe(p, s)}",
                        Decision.originOf(p, s.id), unitOf(p, s.id), declarationOf(p, s))
      // …and no decision where the site is UNSELECTABLE: `declarationOf` answers `SymId.None` for a
      // parameter with no enclosing declaration, and a decision at a subject the run cannot emit is
      // a row `NoteCoverageCheck` is right to have no note for. The finding above still counts it.
      if at != SymId.None then p.symbolOf(at).foreach { d =>
        record(Decision(
          kind       = Decision.Kind.ScopedOut,
          subject    = at,
          subjectFqn = d.fullName,
          // NO `key` in `detail`: `Reason.Configured` already carries it, and a decider that spells
          // it a second time renders `key=… key=…` in the porter note and repeats itself in
          // `decisions.tsv`'s `detail` column beside a `reason` column that already says `phase:key`.
          // `param` ONLY where the subject is not the annotated symbol itself, so a scoped-out
          // DECLARATION's note is byte-identical to what it was before parameters were counted —
          // the pair would otherwise restate `subjectFqn` on every port that already has one.
          detail = Map(
            "why" -> ("this declaration carries a configured nullability annotation, and this port's " +
              "`nullability` scope deliberately holds it back — so it keeps its upstream type while " +
              "the annotated declarations around it moved"),
          ) ++ Option.when(at != s.id)("param" -> s.name),
          reason = Reason.Configured(name, entry),
          origin = Decision.originOf(p, s.id),
        ))
      }

  // -------------------------------------------------------------------------
  // decisions — one row per DECLARATION whose emitted form changed, per policy key (§5.1)
  // -------------------------------------------------------------------------

  private def recordDecisions(before: Program, plan: List[Planned], after: SymbolTable)(using Program): Unit =
    plan.groupBy(p => (declarationOf(p.sym), p.key)).toList
      .sortBy((k, _) => (k._1.raw, k._2))
      .foreach { case ((decl, key), ps) =>
        val was = before.symbolOf(decl)
        val now = after.get(decl)
        (was, now) match
          case (Some(w), Some(n)) if w.info != n.info =>
            record(Decision(
              kind       = Decision.Kind.RetypedSignature,
              subject    = decl,
              subjectFqn = w.fullName,
              detail = Map(
                "from"      -> TirPrinter.tpe(w.info, TirPrinter.Style.canonical),
                "to"        -> TirPrinter.tpe(n.info, TirPrinter.Style.canonical),
                "positions" -> ps.map(_.label).distinct.sorted.mkString(","),
                "why"       -> ("the upstream states this contract with an annotation the scala " +
                  "compiler ignores; the port states it in the TYPE, and drops the annotation " +
                  "rather than saying it twice"),
              ),
              reason = Reason.Configured(name, key),
              origin = Decision.originOf(before, decl),
            ))
          case _ => ()
      }

  /** the DECLARATION a retype is attributed to — a parameter's is its method, whose signature is
    * the thing that moved (§5.1: never one row per parameter). */
  private def declarationOf(s: Symbol): SymId = if s.flags.isParam then s.owner else s.id

  // -------------------------------------------------------------------------
  // the tree
  // -------------------------------------------------------------------------

  override def transformValDef(v: Tree.ValDef)(using p: Program): Tree.ValDef =
    newTypes.get(v.symbol) match
      case scala.None    => if isWrapper then v.copy(rhs = v.rhs.map(coerceTo(v.tpt.tpe, _))) else v
      case Some(t) =>
        val out = v.copy(tpt = TypeTree(t, v.origin))
        if isWrapper then out.rhs match
          // A @Null FIELD with no initialiser defaults to JVM null in Java, and the emitter
          // renders the absent rhs as `scala.compiletime.uninitialized` — which is JVM null.
          // Under an opaque wrapper (Nullable uses a NestedNone sentinel), JVM null is NOT
          // the wrapper's empty value: `isEmpty` returns FALSE, `orNull` returns null after
          // the guard passes, and every consumer of the isEmpty/orNull pair NPEs.
          // Measured: 9 JsonMatcherTests failures on @Null Node prev/next in a Ragel state
          // machine — the backward walk tested `prev.isEmpty` (false on JVM null), read
          // `prev.orNull` (null), then dereferenced. Init to W.empty so the wrapper's own
          // sentinel is in place from the start, matching Java's `null` default.
          // NOT applied to PARAMETERS: a parameter has no field to initialise, and giving it
          // a default value would change the method's calling convention.
          case scala.None if !p.symbolOf(v.symbol).exists(_.flags.isParam) =>
            out.copy(rhs = Some(wrap(t, Tree.Literal(Constant.NullC, TypeRepr.NoType, v.origin))))
          case _ =>
            out.copy(rhs = out.rhs.map(coerceTo(t, _)))
        else out

  override def transformDefDef(d: Tree.DefDef)(using Program): Tree.DefDef =
    val ret = newTypes.get(d.symbol)
    val out = ret.map(t => d.copy(returnTpt = TypeTree(t, d.origin))).getOrElse(d)
    val want = out.returnTpt.tpe
    if isWrapper then out.copy(rhs = out.rhs.map(mapReturns(want, _, coerceTo)))
    else ret match
      case Some(_) => out.copy(rhs = out.rhs.map(mapReturns(d.returnTpt.tpe, _, retireNullCast)))
      case scala.None => out

  /** UNION mode's one body rewrite, and the reason the floor is more than documentation.
    *
    * The frontend renders `return null` at a TYPE-PARAMETER return as `null.asInstanceOf[T]`,
    * because `def m[T <: X](): T = null` does not type-check — `Null <: T` does not hold at an
    * abstract `T`, with or without `-Yexplicit-nulls`. Once the return is `T | Null` the literal
    * conforms and the cast is a placeholder standing in for a contract the type now states, so it
    * goes. Narrow on purpose: only a NULL literal cast to EXACTLY the method's former return type,
    * which is the shape the frontend produces and nothing else. */
  private def retireNullCast(was: TypeRepr, e: Term): Term = e match
    case Tree.Typed(lit @ Tree.Literal(Constant.NullC, _, _), tpt, _, _) if tpt.tpe == was => lit
    case other => other

  override def transformIdent(t: Tree.Ident)(using Program): Term =
    wrapped.get(t.sym).map(w => t.copy(tpe = w)).getOrElse(t)

  /** An OPERATOR is never unwrapped here, and that is not an optimisation.
    *
    * `x == null` arrives as `Select(x, scala.<op>#==)` applied to the literal, and the traversal is
    * bottom-up — so unwrapping the receiver of every wrapped selection would rewrite it to
    * `x.get == null` one node BEFORE [[nullTest]] could see it, silently converting the one rewrite
    * that is mandatory into an NPE at run time. */
  override def transformSelect(t0: Tree.Select)(using p: Program): Term =
    val t = wrapped.get(t0.sym).map(w => t0.copy(tpe = w)).getOrElse(t0)
    if isWrapper && isWrapped(t.qual) && !isWrapperMember(t.sym) && !isOperator(p, t.sym)
    then t.copy(qual = unwrap(t.qual)) else t

  override def transformApply(t: Tree.Apply)(using p: Program): Term =
    if !isWrapper then t
    else
      val result = nullTest(t).getOrElse {
        val recvFixed = t.fun match
          case f @ Tree.Select(recv, m, _, _) if isWrapped(recv) && !isWrapperMember(m)
            && (!isOperator(p, m) || !isNullTestOp(p, m)) =>
            t.copy(fun = f.copy(qual = unwrap(recv)))
          case _ => t
        coerceArgs(recvFixed)
      }
      result match
        case a: Tree.Apply if newTypes.contains(a.method) && !isWrapperType(a.tpe) && a.tpe != TypeRepr.NoType =>
          a.copy(tpe = TypeRepr.AppliedType(TypeRepr.TypeRef(TypeRepr.NoPrefix, wrapperSym), List(a.tpe)))
        case a: Tree.Apply if !newTypes.contains(a.method) && a.tpe != TypeRepr.NoType =>
          val methodSym = summon[Program].symbolOf(a.method)
          val retWrapped = methodSym.map(_.info).exists {
            case TypeRepr.MethodType(_, r, _)                       => isWrapperType(r)
            case TypeRepr.PolyType(_, TypeRepr.MethodType(_, r, _)) => isWrapperType(r)
            case _                                                  => false
          }
          if retWrapped && !isWrapperType(a.tpe) then
            a.copy(tpe = TypeRepr.AppliedType(TypeRepr.TypeRef(TypeRepr.NoPrefix, wrapperSym), List(a.tpe)))
          else result
        case _ => result

  /** A LAMBDA BODY IS A SLOT — the function's result, exactly as a `return` is a method's.
    *
    * Universal (§1(a)): java's lambda body flows into the SAM's result type, so a value this phase
    * wrapped at its DECLARATION (`@Null T transition`, captured as `() -> transition`) arrives at a
    * slot the phase did not retype. The If/Match/Block coercion in [[coerceTo]] is the same seam one
    * node kind further out — measured on the first dependent to hit it (`ScreenManager#pushScreen`,
    * one E007 at a `Supplier[T]` whose `T` is the class's own parameter).
    *
    * WHAT THE SLOT IS, in the order the evidence is available:
    *   - the lambda's recorded `resultTpt` — set by the frontend where it could state the SAM's
    *     result in the target's context;
    *   - a `scala.FunctionN` type's last argument — the emitter's own spelling of a function;
    *   - the SAM method of an OWNED interface, read from the retyped table, so an interface the
    *     phase itself retyped (`@Null T get()`) keeps the body WRAPPED — it is one of ours;
    *   - otherwise the SAM is a CLASS FILE's: the formal cannot say whether it accepts null, so a
    *     wrapped body is unwrapped AND counted, exactly as [[coerceArgs]] treats an external
    *     callee's argument.
    */
  override def transformLambda(t: Tree.Lambda)(using p: Program): Term =
    if !isWrapper then t
    else lambdaResult(p, t) match
      case Some(r) =>
        t.copy(body = mapReturns(r, coerceTo(r, t.body), coerceTo))
      case scala.None =>
        if bodyIsWrapped(t.body) then
          issues += Finding(Issue.UncoercibleSeam,
            headSym(t.tpe).flatMap(p.symbolOf).map(_.fullName).getOrElse("?"),
            "a wrapped value is the RESULT of a lambda whose functional interface is a class file — " +
              "its SAM result carries no annotation the engine reads, so the body is unwrapped at the " +
              "slot on java's own rule (every reference result accepts null)",
            t.body.origin, currentUnit)
        t.copy(body = mapReturns(TypeRepr.NoType, unwrapLeaves(t.body), (_, e) => unwrapLeaves(e)))

  /** the type a lambda's body has to produce, where this phase can read one — see [[transformLambda]]. */
  private def lambdaResult(p: Program, t: Tree.Lambda): Option[TypeRepr] =
    t.resultTpt.map(_.tpe).orElse {
      t.tpe match
        case TypeRepr.AppliedType(tc, args) =>
          headSym(tc).flatMap(p.symbolOf) match
            case Some(s) if FunctionNames(s.fullName) && args.nonEmpty => Some(args.last)
            case Some(s) if p.owns(s.id) =>
              // an OWNED functional interface: its one abstract method's RETYPED result decides
              p.symbols.all.iterator.filter(m => m.owner == s.id && m.flags.isAbstract).toList match
                case m :: Nil => m.info match
                  case TypeRepr.MethodType(_, r, _)                       => Some(r)
                  case TypeRepr.PolyType(_, TypeRepr.MethodType(_, r, _)) => Some(r)
                  case _                                                  => scala.None
                case _ => scala.None
            case _ => scala.None
        case _ => scala.None
    }

  private def bodyIsWrapped(e: Term): Boolean = e match
    case x: Tree.Block => bodyIsWrapped(x.expr)
    case x: Tree.If    => bodyIsWrapped(x.thenp) || bodyIsWrapped(x.elsep)
    case x: Tree.Match => x.cases.exists(c => bodyIsWrapped(c.body))
    case x: Tree.Typed => bodyIsWrapped(x.expr)
    case other         => isWrapped(other)

  /** `.orNull` at every LEAF of a value-producing expression — the unwrap half of [[coerceTo]]
    * where no target type is available to name. These are lambda bodies going to external SAM
    * results, which in java accept null by default, so the null-preserving `.orNull` is the
    * faithful spelling. A primitive CAST target is the exception — unboxing null NPEs in java. */
  private def unwrapLeaves(e: Term): Term = e match
    case x if isWrapped(x) => unwrapOrNull(x)
    case x: Tree.Block     => x.copy(expr = unwrapLeaves(x.expr))
    case x: Tree.If        => x.copy(thenp = unwrapLeaves(x.thenp), elsep = unwrapLeaves(x.elsep))
    case x: Tree.Match     => x.copy(cases = x.cases.map(c => c.copy(body = unwrapLeaves(c.body))))
    // the same rule as [[coerceTo]]'s own `Tree.Typed` arm, at the leaf walk: the unwrap is the
    // OPERAND's and the cast keeps its own type, which is `tpt` and never the wrapper's element —
    // `(int) poll()` over a `Nullable[Integer]` emits `.asInstanceOf[scala.Int]` and the element is
    // `java.lang.Integer`. A primitive cast is an unboxing — java NPEs there, so `.get`. A
    // reference cast passes null through, so `.orNull`.
    case x: Tree.Typed if isWrapped(x.expr) => x.copy(expr = slotUnwrap(x.tpt.tpe, x.expr))
    case other             => other

  override def transformTerm(t: Term)(using Program): Term = t match
    case a: Tree.Assign      if isWrapper => a.copy(rhs = coerceTo(a.lhs.tpe, a.rhs))
    case a: Tree.ArrayLength if isWrapper && isWrapped(a.array) => a.copy(array = unwrap(a.array))
    case a: Tree.ArrayAccess if isWrapper && isWrapped(a.array) => a.copy(array = unwrap(a.array))
    case other => other

  // -------------------------------------------------------------------------
  // wrapper-mode coercion — attack the SLOT (K2), never the type
  // -------------------------------------------------------------------------

  private def isWrapper: Boolean = target.isWrapper

  private def isWrapped(t: Term): Boolean = headSym(t.tpe).contains(wrapperSym) && wrapperSym != SymId.None
  private def isWrapperType(t: TypeRepr): Boolean = headSym(t).contains(wrapperSym) && wrapperSym != SymId.None
  private def isWrapperMember(s: SymId): Boolean = Set(applySym, emptySym, getSym, orNullSym, isEmptySym).contains(s)

  private def elementOf(t: TypeRepr): TypeRepr = t match
    case TypeRepr.AppliedType(_, List(a)) => a
    case _                                => TypeRepr.NoType

  /** `W.apply(e)`, or `W.empty`/`None` for a bare `null` — the two directions of the wrap half.
    *
    * For `OptionTarget`, `None` is a top-level object, not a member of `Option` — so it is
    * emitted as `Ident(noneSym)` rather than `Select(Ident(wrapperSym), emptySym)`. */
  private def wrap(want: TypeRepr, e: Term): Term = e match
    case Tree.Literal(Constant.NullC, _, o) =>
      if target == Target.OptionTarget then Tree.Ident(emptySym, want, o)
      else Tree.Select(Tree.Ident(wrapperSym, TypeRepr.NoType, o), emptySym, want, o)
    case Tree.Typed(Tree.Literal(Constant.NullC, _, _), _, _, o) =>
      if target == Target.OptionTarget then Tree.Ident(emptySym, want, o)
      else Tree.Select(Tree.Ident(wrapperSym, TypeRepr.NoType, o), emptySym, want, o)
    case x: Tree.If =>
      val elem = elementOf(want)
      val coerced = x.copy(thenp = coerceTo(elem, x.thenp), elsep = coerceTo(elem, x.elsep))
      Tree.Apply(Tree.Ident(wrapperSym, TypeRepr.NoType, e.origin), List(coerced), applySym, want, e.origin)
    case x: Tree.Match =>
      val elem = elementOf(want)
      val coerced = x.copy(cases = x.cases.map(c => c.copy(body = coerceTo(elem, c.body))))
      Tree.Apply(Tree.Ident(wrapperSym, TypeRepr.NoType, e.origin), List(coerced), applySym, want, e.origin)
    case x: Tree.Block =>
      val elem = elementOf(want)
      val coerced = x.copy(expr = coerceTo(elem, x.expr))
      Tree.Apply(Tree.Ident(wrapperSym, TypeRepr.NoType, e.origin), List(coerced), applySym, want, e.origin)
    case _ =>
      Tree.Apply(Tree.Ident(wrapperSym, TypeRepr.NoType, e.origin), List(e), applySym, want, e.origin)

  /** `e.get` — the unchecked unwrap. NPE on empty IS Java's semantics at a DEREFERENCE (member
    * access, method call, array element/length), and the contract asks for this member for exactly
    * that case. See [[unwrapOrNull]] for the slot-coercion counterpart. */
  private def unwrap(e: Term): Term =
    Tree.Select(e, getSym, elementOf(e.tpe), e.origin)

  /** `e.orNull` — the null-preserving unwrap. Java's value flows through unchanged: an empty
    * wrapper becomes `null`, which is what the java slot received. This is the faithful spelling
    * at a SLOT that ACCEPTS null — an unannotated java field, local, parameter, result, or
    * `Object` formal — which is the default in java. See [[slotUnwrap]] for the decision rule
    * and [[unwrap]] for the dereference counterpart. */
  private def unwrapOrNull(e: Term): Term =
    Tree.Select(e, orNullSym, elementOf(e.tpe), e.origin)

  /** the SLOT-NULLABILITY RULE: `.get` when the target slot is provably non-null (a primitive after
    * unboxing), `.orNull` when the slot accepts null (the java default for every reference type).
    *
    * The two faces of the unwrap — a DEREFERENCE (member access, array op) throws on null because
    * java does too; a SLOT COERCION preserves null because an unannotated java slot accepts it.
    * Without this distinction, `ObjectMap#get(K)` returning `@Null V` is retyped to `Nullable[V]`,
    * and every consumer that reads the absent-key sentinel receives an NPE instead of `null` — a
    * §4.4 compile-clean-wrong-at-runtime defect. Measured: ashley `OUTCOMES LOST — 4 of 112`,
    * `ExceptionInInitializerError` at `Family.Builder.get`. */
  private def slotUnwrap(want: TypeRepr, e: Term): Term =
    if isPrimitiveSlot(want) then unwrap(e) else unwrapOrNull(e)

  /** is the target slot a PRIMITIVE — the one non-null-accepting slot kind that survives to
    * [[coerceTo]] (the planning phase already refused annotated primitives, so this is about a
    * slot's FORMAL being primitive, not the annotated declaration). Checked against the cached
    * [[primSyms]] set so no `Program` is needed, which is what lets [[coerceTo]] remain a plain
    * `(TypeRepr, Term) => Term` passable to [[mapReturns]]. */
  private def isPrimitiveSlot(t: TypeRepr): Boolean =
    headSym(t).exists(primSyms.contains)

  /** the wrapper's own ABSENT value, however this target spells it — `W.empty` for a `Named`
    * wrapper, `None` for `Option`. */
  private def isEmptyOfWrapper(e: Term): Boolean = e match
    case Tree.Select(_, m, _, _) => m == emptySym && emptySym != SymId.None
    case Tree.Ident(m, _, _)     => m == emptySym && emptySym != SymId.None
    case _                       => false

  private def isNullLiteral(e: Term): Boolean = e match
    case Tree.Literal(Constant.NullC, _, _)    => true
    case Tree.Typed(inner, _, _, _)            => isNullLiteral(inner)
    case _                                     => false

  private def coerceTo(want: TypeRepr, e: Term): Term =
    if isWrapperType(want) && isNullLiteral(e) then wrap(want, e)
    else if isWrapperType(want) && !isWrapped(e) then wrap(want, e)
    else if !isWrapperType(want) && isWrapped(e) && want != TypeRepr.NoType then slotUnwrap(want, e)
    else if isWrapperType(want) && isWrapped(e) && e.tpe != want then
      // …UNLESS THE FORMAL CANNOT BE WRITTEN HERE. This is the one arm that puts a TYPE into the
      // emitted text (`wrap` and `unwrap` name only the wrapper's own members), so it is the one
      // arm that owes the question. A formal naming the CALLEE's type variable does not resolve at
      // the call site (`ENGINE-LIMITS.md` G12): `coerceArgs` substitutes what the RECEIVER
      // instantiated wherever it can, and where it cannot the honest emission is no ascription at
      // all — `item.asInstanceOf[lowlevel.Nullable[T]]` is `E006 Not found: type T` at a line the
      // source never wrote (TextraTypist's `TextraSelectBox#setSelected`, 1 error).
      // …AND THE PHASE'S OWN `empty` NEEDS NO ASCRIPTION AT ALL. `W.empty` is the wrapper's absent
      // value and the contract makes it conform at EVERY element type — the emitter already relies
      // on that at every `return lowlevel.Nullable.empty` in a `Nullable[X]` result. Ascribing it is
      // therefore never load-bearing, and it is the one operand that reaches a slot whose element is
      // written in a scope the site does not have: a companion or `static` member sees NONE of its
      // class's type parameters (`ENGINE-LIMITS.md` G20) and a SUPER-CONSTRUCTOR argument list is
      // evaluated before the class's own parameters bind, so `Nullable.empty.asInstanceOf[
      // Nullable[T]]` is `E006 Not found: type T` at sites the unit-level test below cannot see (the
      // variable's unit IS the unit being walked). Structural, and it needs no scope question at
      // all: the text `lowlevel.Nullable.empty` is correct wherever the slot is.
      if isEmptyOfWrapper(e) then e
      else typeVarsIn(want) match
        case Nil => Tree.Typed(e, TypeTree(want, e.origin), want, e.origin)
        case vs  =>
          issues += Finding(Issue.UnwritableFormal, vs.distinct.mkString(", "),
            "the formal this argument would be ascribed to names a type variable that is not in " +
              "scope where the call stands, so no ascription can be written — the argument is left " +
              "as it is", e.origin, currentUnit)
          e
    else if !isWrapperType(want) && want != TypeRepr.NoType then e match
      case x: Tree.If    => x.copy(thenp = coerceTo(want, x.thenp), elsep = coerceTo(want, x.elsep))
      case x: Tree.Match => x.copy(cases = x.cases.map(c => c.copy(body = coerceTo(want, c.body))))
      case x: Tree.Block => x.copy(expr = coerceTo(want, x.expr))
      // …AND THE CAST KEEPS ITS OWN TYPE. What the unwrap changes is the OPERAND under the cast;
      // the cast itself is untouched, and the emitter renders it from `tpt` (`TirEmitter.castTarget`)
      // — so `(int) poll()` still emits `.asInstanceOf[scala.Int]` however wide the slot is.
      // Recording `want` here — the FORMAL, `long` at junit's `assertEquals(long, long)` — put a
      // type on the node that the emitted Scala does not have (`ENGINE-LIMITS.md` §0), and the
      // sibling arms above never did: coercing an `If`'s branches really does make the node `want`,
      // and unwrapping under a cast does not. Every later rule that consults `tpe` then reasons
      // about the wrong type; the one that did is `TestFrameworkTransform.promote`, which re-applies
      // java's binary numeric promotion (JS-E07) by widening the NARROWER operand — it read `Long`
      // here, widened the literal to `1.toLong`, and left the cast at `Int`: `E172 Can't compare
      // Int and Long` at 4 sites on libGDX's own suite, with no other count moving. Note the slot
      // is not lost by keeping the honest type: java widened `int` to `long` implicitly at the
      // call, and so does scala.
      // the cast's own TARGET type decides the unwrap — a primitive cast (unboxing) is a
      // dereference where java NPEs, so `.get`; a reference cast passes null through, so `.orNull`
      case x: Tree.Typed if isWrapped(x.expr) => x.copy(expr = slotUnwrap(x.tpt.tpe, x.expr))
      case _             => e
    else e

  /** THE CALLEE'S OWN TYPE VARIABLES, replaced by what the RECEIVER instantiated them with.
    *
    * A formal is written in the DECLARING class's scope, and this phase reads formals to coerce
    * against — so a wrapped formal at a generic callee is `W[T]` in the callee's `T`, which is not
    * in scope at the call. The `extends`-free half of `CLAUDE.md` §4.56's substitution rule: the
    * receiver's own type arguments say exactly what those variables are, so the substitution is
    * EXACT and the commonest outcome is that the formal and the argument then agree and NO
    * ascription is emitted at all.
    *
    * Empty where nothing can be said — a raw or non-generic receiver, an arity that does not line
    * up, or a callee INHERITED from an ancestor whose variables the receiver's head does not
    * declare. Those fall through to [[coerceTo]]'s own refusal, which counts them. */
  private def receiverSubst(t: Tree.Apply)(using p: Program): Map[SymId, TypeRepr] =
    val recv = t.fun match
      case s: Tree.Select => s.qual.tpe
      case _              => TypeRepr.NoType
    recv match
      case TypeRepr.AppliedType(tc, args) =>
        val owner = p.symbolOf(t.method).map(_.owner)
        headSym(tc) match
          case Some(h) if owner.contains(h) =>
            p.definitionOf(h) match
              case Some(cd: Tree.ClassDef) if cd.tparams.sizeIs == args.size =>
                cd.tparams.map(_.symbol).zip(args).toMap
              case _ => Map.empty
          case _ => Map.empty
      case _ => Map.empty

  /** Is the receiver's type head an EXTERNAL type while the method symbol belongs to an OWNED one?
    *
    * When `CollectionsTransform.retarget` rewrites a type — e.g. `com.badlogic.gdx.utils.ObjectMap`
    * to `lowlevel.util.ObjectMap` — the call node still references the JAVA method symbol, whose
    * `@Null`-annotated parameters this phase has already wrapped in `Nullable[V]`. But scalac
    * resolves the call against the RETARGET TARGET's own API (from TASTy or class file), which has
    * its own nullability model. Coercing against the java formals is then wrong: lls's
    * `ObjectMap.put(K, V)` takes bare `V`, not `Nullable[V]`.
    *
    * The structural signal is precise: the RECEIVER's head symbol is NOT owned by this program
    * (it is the retarget target — an external type) while the METHOD's owner IS owned (the original
    * java type this program parsed). This excludes ordinary inheritance (both sides owned) and
    * external-to-external calls (neither side owned), catching exactly the retarget shape.
    *
    * §4.56's rule at a phase interaction: "a phase may only conclude something about a type from
    * what the PHASE ITSELF did to that type" — and this phase did not retarget the receiver. */
  private def isRetargetted(t: Tree.Apply)(using p: Program): Boolean =
    val recvHead = t.fun match
      case s: Tree.Select => headSym(s.qual.tpe)
      case _              => scala.None
    val methodOwner = p.symbolOf(t.method).map(_.owner)
    (recvHead, methodOwner) match
      case (Some(rh), Some(mo)) =>
        rh != SymId.None && mo != SymId.None && rh != mo && !p.owns(rh) && p.owns(mo)
      case _ => false

  private def coerceArgs(t: Tree.Apply)(using p: Program): Term =
    // An EXTERNAL callee is excluded BEFORE the formals are read, and that exclusion is the whole
    // difference between this phase's seam and the collection boundary's.
    //
    // `SpoonTir` now interns an external member with its `MethodType` where a class file can be
    // read for one, so the formals ARE available here. They are the wrong evidence.
    // `CollectionsTransform` asks "what TYPE does this slot want", which a class file answers;
    // this phase has to ask "does this slot accept null", which a class file does not answer at
    // all — no annotation is read from one, and every reference type in a java signature accepts
    // null unless something says otherwise. The slot-nullability rule uses `.orNull` for external
    // callees, which preserves null faithfully — java's default. The seam stays COUNTED, and
    // the count now says which of the two facts is missing.
    //
    // A RETARGETTED RECEIVER — one whose head symbol differs from the method's owner — means the
    // call will be resolved by scalac against the retarget TARGET's API, not the java method's.
    // The java formals (which this phase wrapped at plan time) are the wrong evidence here: the
    // target has its own nullability model. Treat the call as an external callee: unwrap wrapped
    // arguments with `.orNull` and do NOT wrap based on the java's `@Null` annotations.
    val retargetted = isRetargetted(t)
    val formals = p.symbolOf(t.method).map(_.info).collect {
      case TypeRepr.MethodType(ps, _, _)                       => ps.map(_._2)
      case TypeRepr.PolyType(_, TypeRepr.MethodType(ps, _, _)) => ps.map(_._2)
    }
    val owned = p.owns(t.method)
    if retargetted then
      // The method symbol belongs to the JAVA type but the receiver is the retarget TARGET.
      // The java's `@Null` formals do not describe the target's API. Unwrap any wrapped
      // arguments — the target takes bare values — and count the seam.
      t.args.filter(isWrapped).foreach { a =>
        issues += Finding(Issue.UncoercibleSeam, p.symbolOf(t.method).map(_.fullName).getOrElse("?"),
          "a wrapped argument reaches a retargetted callee — unwrapped because the receiver's " +
            "type was retargetted and the java method's @Null annotations do not describe the " +
            "target's API",
          a.origin, currentUnit)
      }
      t.copy(args = t.args.map(a => if isWrapped(a) then unwrapOrNull(a) else a))
    else
    formals match
      case Some(fs) if fs.sizeIs == t.args.size =>
        if !owned then
          t.args.zip(fs).foreach { (a, f) =>
            if isWrapped(a) && !isWrapperType(f) then
              issues += Finding(Issue.UncoercibleSeam, p.symbolOf(t.method).map(_.fullName).getOrElse("?"),
                "a wrapped argument reaches an external callee — unwrapped at the slot because a " +
                  "class file carries no annotation the engine reads and every java reference slot " +
                  "accepts null",
                a.origin, currentUnit)
          }
        val sub = receiverSubst(t)
        t.copy(args = t.args.zip(fs).map((a, f) => coerceTo(ParentSubst.subst(f, sub), a)))
      case _ =>
        t.args.filter(isWrapped).foreach { a =>
          issues += Finding(Issue.UncoercibleSeam, p.symbolOf(t.method).map(_.fullName).getOrElse("?"),
            "a wrapped argument reaches a callee whose signature this program does not have — a " +
              "class file could not be read for this symbol, so there is no formal to coerce against",
            a.origin, currentUnit)
        }
        // no formal to coerce against: unwrap with `.orNull` — java's default is that every
        // reference slot accepts null, and the missing formal says nothing about nullability
        t.copy(args = t.args.map(a => if isWrapped(a) then unwrapOrNull(a) else a))

  /** `x == null` / `x != null` on a wrapped value → `x.isEmpty` / `!x.isEmpty`.
    *
    * Not an ergonomic nicety: equality against `null` on an opaque wrapper is a COMPILE ERROR
    * (E172, no `CanEqual`), so every Java null test on a wrapped value has to be rewritten or the
    * port does not build. The rewrite is the portable mechanism — a `CanEqual` given in the
    * wrapper's companion would work, and a published wrapper need not have one. */
  private def nullTest(t: Tree.Apply)(using p: Program): Option[Term] = t.fun match
    case Tree.Select(recv, op, _, o) =>
      val opName = p.symbolOf(op).filter(s => OperatorOwners(ownerNameOf(s))).map(_.name)
      def isNullLit(e: Term) = e match { case Tree.Literal(Constant.NullC, _, _) => true; case _ => false }
      (opName, t.args) match
        case (Some("==" | "eq"), List(a)) if isWrapped(recv) && isNullLit(a) => Some(isEmpty(recv, o))
        case (Some("!=" | "ne"), List(a)) if isWrapped(recv) && isNullLit(a) => Some(negate(isEmpty(recv, o), o))
        case _ => scala.None
    case _ => scala.None

  private def isEmpty(recv: Term, o: Origin): Term =
    Tree.Select(recv, isEmptySym, TypeRepr.TypeRef(TypeRepr.NoPrefix, boolSym), o)

  private def negate(e: Term, o: Origin): Term =
    Tree.Apply(Tree.Select(e, notSym, TypeRepr.NoType, o), Nil, notSym,
               TypeRepr.TypeRef(TypeRepr.NoPrefix, boolSym), o)

  // -------------------------------------------------------------------------
  // shared walks
  // -------------------------------------------------------------------------

  /** every `return` that belongs to THIS method, rewritten by `f`.
    *
    * The same DELIBERATELY BOUNDED walk `CollectionsTransform.coerceReturns` performs, and bounded
    * for the same reason: a `return` inside a lambda, an anonymous class's method or a local class
    * returns from THAT, so rewriting it against this method's declared type would be wrong. The
    * default arm does not descend, which makes a node kind added later a MISSED rewrite — loud by
    * construction, never wrong. A `Commented` wrapper is read THROUGH (§4.58): a `return` under a
    * Java comment is still a return, and with the trivia harvest live that is the common case. */
  private def mapReturns(want: TypeRepr, t: Term, f: (TypeRepr, Term) => Term): Term = t match
    case x: Tree.Return       => x.copy(expr = x.expr.map(f(want, _)))
    case x: Tree.Block        => x.copy(stats = x.stats.map { case s: Term => mapReturns(want, s, f); case s => s },
                                        expr = mapReturns(want, x.expr, f))
    case x: Tree.If           => x.copy(thenp = mapReturns(want, x.thenp, f), elsep = mapReturns(want, x.elsep, f))
    case x: Tree.While        => x.copy(body = mapReturns(want, x.body, f))
    case x: Tree.DoWhile      => x.copy(body = mapReturns(want, x.body, f))
    case x: Tree.For          => x.copy(body = mapReturns(want, x.body, f))
    case x: Tree.ForEach      => x.copy(body = mapReturns(want, x.body, f))
    case x: Tree.Synchronized => x.copy(body = mapReturns(want, x.body, f))
    case x: Tree.Labeled      => x.copy(stmt = mapReturns(want, x.stmt, f))
    case x: Tree.Commented    => x.copy(stmt = mapReturns(want, x.stmt, f))
    case x: Tree.Try          => x.copy(body = mapReturns(want, x.body, f),
                                        catches = x.catches.map(c => c.copy(body = mapReturns(want, c.body, f))),
                                        finalizer = x.finalizer.map(mapReturns(want, _, f)))
    case x: Tree.Match        => x.copy(cases = x.cases.map(c => c.copy(body = mapReturns(want, c.body, f))))
    case other                => other

  /** the type variables a type mentions THAT THIS UNIT CANNOT NAME — [[mentionsTypeParam]]'s
    * question asked without a `Program`, off the table this run captured, and narrowed to the ones
    * that are actually out of reach.
    *
    * A variable declared by the unit being walked (or by anything nested inside it) IS writable
    * where a node of that unit stands: `Tooltip[T]`'s own `T` is in scope in every member of
    * `Tooltip`, including an anonymous listener's, and refusing there would decline five correct
    * ascriptions on libGDX alone. What is NOT writable is a variable belonging to ANOTHER unit —
    * the CALLEE's own, which is what `ENGINE-LIMITS.md` G12 is about.
    *
    * The unit is the granularity because it is the one the walk carries ([[currentUnit]]); a
    * bottom-up traversal reaches a `DefDef` after its body, so there is no enclosing-declaration
    * stack to ask a finer question of. The residue that leaves is G20's — a STATIC member sees none
    * of its class's type parameters, and this test says the unit owns them — which is a false
    * NEGATIVE, i.e. an ascription too many, and loud. */
  private def typeVarsIn(t: TypeRepr): List[String] = t match
    case TypeRepr.TypeRef(_, s)       => typeVars.get(s).filterNot(_._2 == currentUnit).map(_._1).toList
    case TypeRepr.AppliedType(tc, as) => typeVarsIn(tc) ++ as.flatMap(typeVarsIn)
    case TypeRepr.OrType(l, r)        => typeVarsIn(l) ++ typeVarsIn(r)
    case TypeRepr.AndType(l, r)       => typeVarsIn(l) ++ typeVarsIn(r)
    case _                            => Nil

  private def headSym(t: TypeRepr): Option[SymId] = t match
    case TypeRepr.TypeRef(_, s)      => Some(s)
    case TypeRepr.AppliedType(tc, _) => headSym(tc)
    case _                           => scala.None

  private def ownerNameOf(s: Symbol): String = s.fullName.takeWhile(_ != '#')

  /** is this the ENGINE's own synthetic operator namespace, minted by the frontend under exactly
    * that owner? Engine identity, never a policy key. */
  private def isOperator(p: Program, s: SymId): Boolean =
    p.symbolOf(s).exists(x => OperatorOwners(ownerNameOf(x)))

  private def isNullTestOp(p: Program, s: SymId): Boolean =
    p.symbolOf(s).exists(x => x.name == "==" || x.name == "!=")

  /** the TOP-LEVEL unit a symbol belongs to — how a finding is held to the module that emits it. */
  private def unitOf(p: Program, id: SymId, fuel: Int = 64): SymId =
    p.symbolOf(id) match
      case Some(s) if s.owner != SymId.None && fuel > 0 => unitOf(p, s.owner, fuel - 1)
      case _                                            => id

  /** mint-or-reuse: the engine's OWN externals — `scala.Null`, the operator namespace, the
    * configured wrapper — are symbols this program never declares, so there is nothing to bind and
    * the FQN is the only identity there is. Exactly the question `PrimitiveToOpaqueTransform` asks
    * about its underlying primitive. */
  private def externalNamed(p: Program, fqn: String): Option[SymId] =
    p.symbols.all.iterator.find(s => s.fullName == fqn).map(_.id)

  private final case class Planned(sym: Symbol, key: String, slot: Slot, was: TypeRepr, hits: List[Annot]):
    def label: String = slot match
      case Slot.Return => "return"
      case Slot.Field  => "field"
      case Slot.Param  => s"param:${sym.name}"

object NullabilityTransform:

  /** the stable name — a `Phase.name`, a `Reason.Configured` phase half, and the factory's config
    * key, which is one identity on purpose. */
  val Name = "nullability"

  private val NullFqn   = "scala.Null"
  private val OptionFqn = "scala.Option"
  private val NoneFqn   = "scala.None"
  private val NotOp     = "scala.<op>#unary_!"

  /** the engine's synthetic operator namespace, minted by the frontend under exactly this owner. */
  private val OperatorOwners = Set("scala.<op>")

  /** the emitter's own spelling of a function type — engine identity, like [[PrimitiveNames]]. */
  private val FunctionNames = (0 to 22).map(n => s"scala.Function$n").toSet

  private val PrimitiveNames = Set(
    "scala.Boolean", "scala.Byte", "scala.Short", "scala.Char",
    "scala.Int", "scala.Long", "scala.Float", "scala.Double", "scala.Unit")

  /** WHICH occurrence of a declaration's type the annotation names. */
  enum Slot:
    case Return, Field, Param

  /** The SHAPE the contract takes in the emitted type — `T | Null` (the union floor),
    * `W[T]` (a configured wrapper satisfying the four-member contract), or `Option[T]`.
    *
    * Three, because the second and third carry DIFFERENT wrapper semantics — `Named` uses a
    * per-library opaque wrapper whose FQN is a fact about the port (two hand ports of one
    * ecosystem chose differently: one `T | Null`, one `Nullable[T]`), and `Option` uses
    * `scala.Option` whose allocation cost (I7) and semantics (`Option(null) == None`,
    * null-normalising by construction) are different from both.
    *
    * ==`Named` CLOSES K13==
    * `T | Null` is transparent at every CONCRETE reference type but NOT at an abstract `T`, which
    * is what K13 measured: 35 compile errors from 632 declarations, every one inside a generic
    * container, and a scope exit list that has to be maintained by hand. A named wrapper `W[T]`
    * IS a proper type that composes at every `T` — the abstract-type-parameter class disappears
    * entirely, and the scope exit list with it. `Option[T]` has the same property.
    *
    * ==The five-member contract==
    * Both `Named` and `Option` rely on the same five members — `apply` (null-normalising),
    * `empty`, extension `get` (unchecked, NPE on empty, which IS Java's semantics at a
    * dereference), extension `orNull` (null-preserving unwrap, Java's semantics at a slot that
    * accepts null), and extension `isEmpty`. For `Option` these are `Some.apply`/`None`/`.get`/
    * `.orNull`/`.isEmpty` — the same shape, different types.
    */
  enum Target:
    /** `T | Null` — the union floor. Free at every concrete reference type, NOT transparent at
      * an abstract `T` (K13). */
    case Union
    /** `W[T]` — a per-library opaque wrapper satisfying the five-member contract. The FQN is
      * the port's to state, never the engine's; sge's `lowlevel.Nullable` is the first policy
      * value. CLOSES K13: `W[T]` composes at every `T`. */
    case Named(fqn: String)
    /** `Option[T]` — `scala.Option`, whose allocation cost (I7) is measured separately. CLOSES
      * K13 for the same reason as `Named`. */
    case OptionTarget

    def tag: String = this match
      case Union         => "union"
      case Named(f)      => s"named:$f"
      case OptionTarget  => "option"

    /** is this a wrapper mode (Named or Option) as opposed to the union floor? */
    def isWrapper: Boolean = this != Union
