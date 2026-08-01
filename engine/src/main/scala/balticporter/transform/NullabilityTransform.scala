package balticporter.transform

import balticporter.core.{MergeablePolicy, PolicyFinding, PolicyIssue, PolicyReport, PolicySource}
import balticporter.tir.*

/** Move a library's NULLABILITY ANNOTATIONS out of an annotation the Scala compiler ignores and
  * INTO THE TYPE — `T | Null` (the floor), or a configured wrapper `W[T]`.
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
  * four members — `apply` (null-normalising), `empty`, extension `get` (unchecked, NPE on empty,
  * which IS Java's semantics at a dereference) and extension `isEmpty`. Nothing in it touches
  * `orNull`, which is fake-`@deprecated` as a lint tripwire in the repositories that publish such a
  * wrapper, so generated code must never emit it — and does not. Emission is FQN-only (§6) and the
  * extensions resolve from the companion's implicit scope with no import.
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
) extends Phase, PolicySource, MergeablePolicy, PolicyBound:

  import NullabilityTransform.*
  import NullabilityBoundaryCheck.{Finding, Issue}

  def name: String = NullabilityTransform.Name

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
    PolicyReport.fromBindings(records) ++ PolicyReport(baseIntrusionFindings ++ deadScopeFindings)

  /** Nullability is a fact about the SHARED SURFACE: a base that emits `Actor | Null` and a
    * dependent that emits `Actor` for the same member each compile alone and cannot compile
    * together (§1.5). The target shape and the scope are part of it for the same reason. */
  def surfaceFingerprint: String =
    s"${annotations.toList.sorted.mkString(",")}|${target.tag}|${scope.fingerprint}"

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
  def subjects: Set[String] = (annotations ++ scope.entries).map(MergeablePolicy.subjectOf)

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
      val targetClash = Option.when(target != o.target)(
        s"""both modules state a nullability TARGET, "${target.tag}" and "${o.target.tag}" — the """ +
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
      (targetClash.toList ++ scopeMerged.left.toOption.toList) match
        case Nil => scopeMerged.map { s =>
          MergeablePolicy.Merged(
            new NullabilityTransform(annotations ++ o.annotations, target, s),
            o.subjects -- subjects)
        }
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

  private var wrapperSym, applySym, emptySym, getSym, isEmptySym, notSym, boolSym = SymId.None
  /** the unit currently being walked — see the walk in [[run]] for why a seam cannot be attributed
    * to the callee it was found at. */
  private var currentUnit: SymId = SymId.None

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
    newTypes = Map.empty; wrapped = Map.empty; overridingRead = false
    // §1(b): an empty policy needs no code path. Nothing bound — no annotation configured, or every
    // configured one named nothing — and the program is returned untouched.
    if boundAnnots.isEmpty then return program

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
      case Target.Wrapper(fqn) =>
        wrapperSym = mintOrReuse(fqn, fqn.split('.').last)
        applySym   = mintOrReuse(fqn + ".apply", "apply", wrapperSym)
        emptySym   = mintOrReuse(fqn + ".empty", "empty", wrapperSym)
        getSym     = mintOrReuse(fqn + ".get", "get", wrapperSym)
        isEmptySym = mintOrReuse(fqn + ".isEmpty", "isEmpty", wrapperSym)
        notSym     = mintOrReuse(NotOp, "unary_!")
        boolSym    = mintOrReuse("scala.Boolean", "Boolean")

    /** the target shape, applied to the annotated occurrence's CURRENT type. */
    def nullable(t: TypeRepr): TypeRepr = target match
      case Target.Union       => TypeRepr.OrType(t, nullRef)
      case Target.Wrapper(_)  => TypeRepr.AppliedType(TypeRepr.TypeRef(TypeRepr.NoPrefix, wrapperSym), List(t))

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
      if hits.nonEmpty && program.owns(s.id) then
        val key = hits.flatMap(a => headSym(a.tpe)).flatMap(boundAnnots.get).sorted.head
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
    recordDecisions(program, plan.toList, symbols)
    // The unit is carried WHILE it is walked, because a seam found inside a call has no other way
    // to say which module owns it: the callee is an EXTERNAL symbol whose owner chain ends outside
    // this program, so attributing the finding to it would put it in no unit at all and the
    // emitted-units filter would silently drop the one finding the seam exists to produce.
    val units = program.units.map { u => currentUnit = u.symbol; StandardTraversal.mapClassDef(this, u) }
    currentUnit = SymId.None
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
    if target == Target.Union then false
    else if s.flags.isParam then p.symbolOf(s.owner).exists(m => crosses(p, m))
    else crosses(p, s)

  private def crosses(p: Program, s: Symbol): Boolean =
    s.flags.isOverride || overriding(p).contains(s.name -> s.descriptor)

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
    issues += Finding(issue, s.fullName, s"`$key` on ${describe(p, s)}", Decision.originOf(p, s.id), unitOf(p, s.id))

  private def describe(p: Program, s: Symbol): String =
    if s.flags.isParam then
      s"parameter `${s.name}` of ${p.symbolOf(s.owner).map(_.fullName).getOrElse("?")}"
    else s"`${s.fullName}`"

  /** The COMPLEMENT of a retype, and it needs its own record for the reason `decisions.tsv` exists:
    * the declaration kept its upstream type while the code around it moved, so the row that would
    * have explained it is the one that is NOT there. Always `Reason.Configured` — an exclusion is a
    * policy entry by construction — and `ScopedOut` is one of the kinds a porter note is rendered
    * for, so the answer sits at the line as well as in the artifact. */
  private def scopedOut(p: Program, s: Symbol, entry: String): Unit =
    if !s.flags.isParam && Decision.isDeclaration(p, s) then
      record(Decision(
        kind       = Decision.Kind.ScopedOut,
        subject    = s.id,
        subjectFqn = s.fullName,
        // NO `key` in `detail`: `Reason.Configured` already carries it, and a decider that spells
        // it a second time renders `key=… key=…` in the porter note and repeats itself in
        // `decisions.tsv`'s `detail` column beside a `reason` column that already says `phase:key`.
        detail = Map(
          "why" -> ("this declaration carries a configured nullability annotation, and this port's " +
            "`nullability` scope deliberately holds it back — so it keeps its upstream type while " +
            "the annotated declarations around it moved"),
        ),
        reason = Reason.Configured(name, entry),
        origin = Decision.originOf(p, s.id),
      ))

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

  override def transformValDef(v: Tree.ValDef)(using Program): Tree.ValDef =
    newTypes.get(v.symbol) match
      case scala.None    => if isWrapper then v.copy(rhs = v.rhs.map(coerceTo(v.tpt.tpe, _))) else v
      case Some(t) =>
        val out = v.copy(tpt = TypeTree(t, v.origin))
        if isWrapper then out.copy(rhs = out.rhs.map(coerceTo(t, _))) else out

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
      nullTest(t).getOrElse {
        val recvFixed = t.fun match
          case f @ Tree.Select(recv, m, _, _) if isWrapped(recv) && !isWrapperMember(m) && !isOperator(p, m) =>
            t.copy(fun = f.copy(qual = unwrap(recv)))
          case _ => t
        coerceArgs(recvFixed)
      }

  override def transformTerm(t: Term)(using Program): Term = t match
    case a: Tree.Assign if isWrapper => a.copy(rhs = coerceTo(a.lhs.tpe, a.rhs))
    case other                       => other

  // -------------------------------------------------------------------------
  // wrapper-mode coercion — attack the SLOT (K2), never the type
  // -------------------------------------------------------------------------

  private def isWrapper: Boolean = target != Target.Union

  private def isWrapped(t: Term): Boolean = headSym(t.tpe).contains(wrapperSym) && wrapperSym != SymId.None
  private def isWrapperType(t: TypeRepr): Boolean = headSym(t).contains(wrapperSym) && wrapperSym != SymId.None
  private def isWrapperMember(s: SymId): Boolean = Set(applySym, emptySym, getSym, isEmptySym).contains(s)

  private def elementOf(t: TypeRepr): TypeRepr = t match
    case TypeRepr.AppliedType(_, List(a)) => a
    case _                                => TypeRepr.NoType

  /** `W.apply(e)`, or `W.empty` for a bare `null` — the two directions of the wrap half. */
  private def wrap(want: TypeRepr, e: Term): Term = e match
    case Tree.Literal(Constant.NullC, _, o) =>
      Tree.Select(Tree.Ident(wrapperSym, TypeRepr.NoType, o), emptySym, want, o)
    case _ =>
      Tree.Apply(Tree.Ident(wrapperSym, TypeRepr.NoType, e.origin), List(e), applySym, want, e.origin)

  /** `e.get` — the unchecked unwrap. NPE on empty IS Java's semantics at a dereference, which is
    * why the contract asks for this member and never for the wrapper's `orNull` (fake-deprecated
    * as a lint tripwire wherever such a wrapper is published). */
  private def unwrap(e: Term): Term =
    Tree.Select(e, getSym, elementOf(e.tpe), e.origin)

  private def coerceTo(want: TypeRepr, e: Term): Term =
    if isWrapperType(want) && !isWrapped(e) then wrap(want, e)
    else if !isWrapperType(want) && isWrapped(e) && want != TypeRepr.NoType then unwrap(e)
    else e

  private def coerceArgs(t: Tree.Apply)(using p: Program): Term =
    p.symbolOf(t.method).map(_.info).collect {
      case TypeRepr.MethodType(ps, _, _)                       => ps.map(_._2)
      case TypeRepr.PolyType(_, TypeRepr.MethodType(ps, _, _)) => ps.map(_._2)
    } match
      case Some(fs) if fs.sizeIs == t.args.size => t.copy(args = t.args.zip(fs).map((a, f) => coerceTo(f, a)))
      case _ =>
        // THE ONE SLOT WITH NO FORMAL TO COMPARE AGAINST — the callee is an external the frontend
        // interned without a signature, so there is nothing to coerce to and nothing that could
        // honestly be inserted. Counted rather than guessed: a wrapped value reaching a slot the
        // engine cannot see is exactly the seam a wrapper mode creates, and a seam that moved no
        // number would be worse than no wrapper (`CollectionBoundaryCheck` counts its own for the
        // same reason).
        t.args.filter(isWrapped).foreach { a =>
          issues += Finding(Issue.UncoercibleSeam, p.symbolOf(t.method).map(_.fullName).getOrElse("?"),
            "a wrapped argument reaches a callee whose formals this program does not have",
            a.origin, currentUnit)
        }
        t

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
        case (Some("=="), List(a)) if isWrapped(recv) && isNullLit(a) => Some(isEmpty(recv, o))
        case (Some("!="), List(a)) if isWrapped(recv) && isNullLit(a) => Some(negate(isEmpty(recv, o), o))
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

  private def headSym(t: TypeRepr): Option[SymId] = t match
    case TypeRepr.TypeRef(_, s)      => Some(s)
    case TypeRepr.AppliedType(tc, _) => headSym(tc)
    case _                           => scala.None

  private def ownerNameOf(s: Symbol): String = s.fullName.takeWhile(_ != '#')

  /** is this the ENGINE's own synthetic operator namespace, minted by the frontend under exactly
    * that owner? Engine identity, never a policy key. */
  private def isOperator(p: Program, s: SymId): Boolean =
    p.symbolOf(s).exists(x => OperatorOwners(ownerNameOf(x)))

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

  private val NullFqn = "scala.Null"
  private val NotOp   = "scala.<op>#unary_!"

  /** the engine's synthetic operator namespace, minted by the frontend under exactly this owner. */
  private val OperatorOwners = Set("scala.<op>")

  private val PrimitiveNames = Set(
    "scala.Boolean", "scala.Byte", "scala.Short", "scala.Char",
    "scala.Int", "scala.Long", "scala.Float", "scala.Double", "scala.Unit")

  /** WHICH occurrence of a declaration's type the annotation names. */
  enum Slot:
    case Return, Field, Param

  /** The SHAPE the contract takes in the emitted type.
    *
    * Two, and not a boolean, because the second one carries the wrapper's FQN — which is
    * configuration and not an engine constant: two hand ports of the same ecosystem chose
    * differently (one `T | Null`, one `Nullable[T]`), so the engine has no standing to pick.
    */
  enum Target:
    case Union
    case Wrapper(fqn: String)

    def tag: String = this match
      case Union      => "union"
      case Wrapper(f) => s"wrapper:$f"
