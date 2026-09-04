package balticporter.transform

import balticporter.core.{MergeablePolicy, PolicyFinding, PolicyIssue, PolicyReport, PolicySource}
import balticporter.tir.*

/** Moves a library's nullability annotations out of an annotation the compiler ignores and into the
  * type — `T | Null` (union), `W[T]` (named wrapper), or `Option[T]` — stripping the annotation and
  * coercing at every slot seam (argument, declaration, assignment, return, member selection).
  * Runs after collections (their retypes must land first) and before package rename (annotation
  * FQNs are upstream). Every refusal (vararg, primitive, annotated args, override boundary) is left
  * untouched and counted by [[NullabilityBoundaryCheck]]. `ENGINE-LIMITS.md` K2, K13
  */
final class NullabilityTransform(
    val annotations: Set[String] = Set.empty,
    val target: NullabilityTransform.Target = NullabilityTransform.Target.Union,
    val scope: RuleScope = RuleScope.Everywhere(),
    /** Members whose return (or field) type is nullable even though java carries no nullability
      * annotation, matched by exact FQN (`Class#member`, or `Class#member(desc)` if overloaded)
      * against `Symbol.fullName` at bind time — same mechanism and counts as annotation selection.
      * Empty is the no-op. `ENGINE-LIMITS.md` O4, K13.6 */
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
    // Ownership.Either: annotation may be in-tree or third-party; Owned would flag third-party as a typo.
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

  /** Surface fingerprint over the shared-surface facts (annotations, target, scope, nullableMembers).
    * `target`/`nullableMembers` segments are omitted at default/empty (§1(b) no-op at the fingerprint). */
  def surfaceFingerprint: String =
    val targetSeg = target match { case Target.Union => ""; case t => s"|${t.tag}" }
    val memberSeg = if nullableMembers.isEmpty then "" else s"|members=${nullableMembers.toList.sorted.mkString(",")}"
    s"${annotations.toList.sorted.mkString(",")}$targetSeg|${scope.fingerprint}$memberSeg"

  /** Every shared-surface subject this instance's policy is keyed on — annotation FQNs,
    * nullableMembers and scope entries — through [[MergeablePolicy.subjectOf]]. A dependent
    * re-scoping a base-emitted type is a `SurfaceIntrusion` (§1.5). */
  def subjects: Set[String] = (annotations ++ nullableMembers ++ scope.entries).map(MergeablePolicy.subjectOf)

  /** Merges two instances' policy (`DESIGN.md` §8.13): `annotations`/`nullableMembers` union;
    * `target` must agree or refuse (two shapes for one member is a choice, not a composition);
    * `scope` unions entries in both directions (`Everywhere` shrinks, `Only` grows as entries
    * accumulate), so a base `Everywhere` merged with a dependent `Only` refuses — no entry set
    * preserves both. `added` is the subject side `SurfaceFold` screens against `governs`. */
  def mergedWith(later: Phase): Either[String, MergeablePolicy.Merged] = later match
    case o: NullabilityTransform =>
      // dependent at default (Union) inherits the base's target; non-default must agree (§1.5).
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
  /** Type-variable symbols by NAME (a type parameter's `info` is `TypeBounds` — the same test
    * [[mentionsTypeParam]] makes). Captured for the walk, which asks *can this type be written
    * here* without threading a `Program`; the name is kept because a refusal names `T`, not an id. */
  private var typeVars: Map[SymId, (String, SymId)] = Map.empty

  private var wrapperSym, applySym, emptySym, getSym, orNullSym, isEmptySym, notSym, boolSym = SymId.None
  /** the primitive symbol IDs — cached at RUN time so [[coerceTo]] can decide `.get` vs `.orNull`
    * without threading a `Program` through a function passed as a value. */
  private var primSyms: Set[SymId] = Set.empty
  /** the unit currently being walked — see the walk in [[run]] for why a seam cannot be attributed
    * to the callee it was found at. */
  private var currentUnit: SymId = SymId.None
  // orNullMembers moved to SuppressionPhase (the `@nowarn` scan runs there, late, post-retarget).

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
    // reset per-run state: a phase instance is reused across two translations (§5.1).
    issues.clear(); intrusions.clear(); observedEntries.clear(); planned = false
    newTypes = Map.empty; wrapped = Map.empty; overridingRead = false; typeVars = Map.empty
    primSyms = Set.empty; matchedMembers.clear()
    // §1(b) no-op: nothing bound, nothing to do.
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

    // In SYMBOL-ID order, so two runs plan identically (hash order is not an order).
    val plan = collection.mutable.ListBuffer[Planned]()
    program.symbols.all.toList.sortBy(_.id.raw).foreach { s =>
      val hits = s.annotations.filter(a => headSym(a.tpe).exists(boundAnnots.contains))
      // annotation wins over nullableMembers (the fallback for an unannotated hand-wrapped member).
      val memberHit = if hits.nonEmpty then scala.None
                      else nullableMembers.find(_ == s.fullName)
      if (hits.nonEmpty || memberHit.isDefined) && program.owns(s.id) then
        val key = if hits.nonEmpty
                  then hits.flatMap(a => headSym(a.tpe)).flatMap(boundAnnots.get).sorted.head
                  else { matchedMembers += memberHit.get; memberHit.get }
        // the one key kind that can select a base's declarations without naming a base FQN — see intrudesOnBase.
        if intrudesOnBase(program, s, key) then baseIntrusion(program, s, key)
        // direction matters: an entry EXCLUDES under Everywhere and INCLUDES under Only; quote it either way.
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
                  // retyped and counted: the declaration is fine, the USE is not (Issue.AbstractTypeParameter).
                  if target == Target.Union && mentionsTypeParam(program, was) then
                    refuse(program, s, key, Issue.AbstractTypeParameter)
                  plan += Planned(s, key, slot, was, hits)
    }
    // `observedEntries` is complete only after the full walk — `planned` gates never-fired reporting.
    planned = true

    // The override edge the annotation travels down (wrapper mode only): java's annotation is a
    // member-level contract an override inherits without repeating; a wrapper retype changes the
    // SIGNATURE, so an unannotated override becomes E038/E007 — invisible until 0 typer errors
    // (`CLAUDE.md` §3), and a dependent-only shape since a base with the defect would not compile.
    // Propagate the retype down the override graph at the same slot/position, under the same gates,
    // keyed on the annotated member (the overrider carries no annotation to strip).
    def paramIndexOf(s: Symbol): Int =
      program.definitionOf(s.owner).collect { case d: Tree.DefDef =>
        d.paramss.flatten.indexWhere(_.symbol == s.id)
      }.getOrElse(-1)

    if target != Target.Union then
      val graph   = OverrideGraph.build(program)
      val claimed = collection.mutable.Set.from(plan.iterator.map(_.sym.id))

      // Bean pair (`BeanPropertyTransform`) is ONE slot: widen the setter's parameter alongside a
      // widened getter return, or `x.stage = x.stage` is a type error (JLS 4.1: unannotated setters
      // accept null anyway). Runs BEFORE override propagation so the widened setter param propagates too.
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
        // only where java has an override edge: `<init>` is always named `<init>`, so a constructor
        // is a FABRICATED edge; `static` hides rather than overrides (JLS 8.4.8.2); `final` has no overriders.
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

    // overload set the retype would erase flat (wrapper mode): two members java kept apart by
    // erasure become one descriptor (E120), so refuse both together before anything is retyped.
    val kept = if target == Target.Union then plan.toList else refuseErasureClashes(program, plan.toList)
    plan.clear(); plan ++= kept
    scopedOutParents(program, plan.toList)
    if plan.isEmpty then return program

    newTypes = plan.iterator.map(p => p.sym.id -> nullable(p.was)).toMap
    if target != Target.Union then wrapped = newTypes

    /** Annotated parameters by owning method and BY POSITION, never by name: a `MethodType`'s
      * parameter list and the `DefDef`'s are parallel by construction, but names are not — a
      * reassigned-parameter transform can rename a slot without touching `info`. */
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
      // consumed annotation is stripped (the type now states the fact); a refused site keeps it.
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
    // unit is carried while walked so a seam inside a call (an external callee has no owning unit) attributes correctly.
    val units = program.units.map { u => currentUnit = u.symbol; StandardTraversal.mapClassDef(this, u) }
    currentUnit = SymId.None

    // the `@nowarn("msg=deprecated")` scan lives in SuppressionPhase, which runs after every retyping phase.
    program.rebuilt(units, symbols)

  // -------------------------------------------------------------------------
  // planning helpers
  // -------------------------------------------------------------------------

  /** Which occurrence of a declaration's type the annotation names, and what it is today. Java's
    * nullability annotations are DECLARATION-position, not `TYPE_USE`: result on a method, declared
    * type on a field/parameter. A TYPE declaration has no such occurrence (`None`, counted refusal). */
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

  /** The overload sets a wrapper would collapse — refused, at both members, at the positions that
    * carry java's distinction. A wrapper's erasure drops type arguments, so `W[Font]` and
    * `W[BitmapFont]` collapse to one descriptor (E120) though java kept them apart by erasure.
    * Refuses the minimum: planned parameters at every position the two members' pre-retype types
    * differ, on both members (neither is more the port's than the other). Head-symbol comparison
    * under-approximates deliberately — a false negative is a loud compile error; a false positive
    * would silently over-refuse. */
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

  /** Does this type mention an abstract type parameter anywhere? Decided structurally (a type
    * parameter's `info` is a `TypeBounds`), never from a name. `Foo[T]` counts as much as bare `T`. */
  private def mentionsTypeParam(p: Program, t: TypeRepr): Boolean = t match
    case TypeRepr.TypeRef(_, s)       => p.symbolOf(s).exists(_.info.isInstanceOf[TypeRepr.TypeBounds])
    case TypeRepr.AppliedType(tc, as) => mentionsTypeParam(p, tc) || as.exists(mentionsTypeParam(p, _))
    case TypeRepr.OrType(l, r)        => mentionsTypeParam(p, l) || mentionsTypeParam(p, r)
    case TypeRepr.AndType(l, r)       => mentionsTypeParam(p, l) || mentionsTypeParam(p, r)
    case TypeRepr.TypeBounds(_, _)    => true
    case _                            => false

  /** Scala's own primitives — `scala.Int` and friends — cannot be null. Engine identity, not
    * per-library policy: these are the names the frontend interns for java's primitives. */
  private def isPrimitive(p: Program, t: TypeRepr): Boolean =
    headSym(t).flatMap(p.symbolOf).exists(s => PrimitiveNames(s.fullName))

  /** Wrapper mode changes a member's SIGNATURE, so it cannot move one end of an override pair alone
    * (union mode can — both narrowing and widening overrides compile without `-Yexplicit-nulls`).
    * Conservative test: a member that overrides, or is overridden, refuses both ends; over-approximates
    * across unrelated hierarchies (safe direction — counted, never silently under-refused). */
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

  /** Would honouring `key` here retype a declaration this run does not emit, on the strength of
    * policy THIS module added? An annotation FQN selects declarations without naming any of them, so
    * `SurfaceFold`'s `governs` screen admits it while a dependent whose base's java carries the same
    * third-party annotation would retype the base's own (untouched) declarations — §1.5's failure,
    * invisible by construction (D2 drops the decisions, [[boundary]] drops the finding). Only the
    * annotation half needs this: a scope entry reaching a base declaration is already a fatal
    * `SurfaceIntrusion`. An INHERITED key (from `contributed`) is not screened — the base already
    * applies it identically. `ENGINE-LIMITS.md` D2 */
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

  /** A declared scope entry that named no annotated declaration — the one §1(b) no-op the ordinary
    * never-fired machinery cannot see: `PolicyBinder.bindScope` asks whether the REGION exists, which
    * a real (unannotated) type answers `yes` to. Only entries whose binding succeeded are reported,
    * or a mistake is reported twice. `ENGINE-LIMITS.md` K13 */
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

  /** A `nullableMembers` entry that named no declaration, reported after the plan loop (`planned`) —
    * like [[deadScopeFindings]], a §1(b) no-op nothing else in the run can see. */
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

  /** The closure a `RuleScope` does not compute — a scoped-out PARENT beside a retyped CHILD (an
    * owned subtype that RE-STATES the annotation on a same-named member is not covered by the
    * parent's scope entry and gets retyped — half an override pair). A subtype that merely INHERITS
    * an annotation is never planned and never reaches this predicate (that gap is
    * [[deadScopeFindings]]'s). Fix is a scope entry in the library's manifest, never an engine change.
    * `ENGINE-LIMITS.md` K13 */
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

  /** §1(b), counted and non-fatal: the emission is already correct (the declaration keeps the
    * base's type), but the manifest states a contract for a namespace this module does not own. */
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

  /** The declaration a site belongs to (not the same question as [[unitOf]]). A parameter or local
    * climbs ONE level to its enclosing executable (java nests no declaration inside a parameter);
    * the climb stops there rather than falling back to the unit, or one selection could drain every
    * row in a file. Where nothing above is a declaration either, the answer is `SymId.None`. */
  private def declarationOf(p: Program, s: Symbol): SymId =
    if Decision.isDeclaration(p, s) then s.id
    else p.symbolOf(s.owner).filter(o => Decision.isDeclaration(p, o)).map(_.id).getOrElse(SymId.None)

  private def describe(p: Program, s: Symbol): String =
    if s.flags.isParam then
      s"parameter `${s.name}` of ${p.symbolOf(s.owner).map(_.fullName).getOrElse("?")}"
    else s"`${s.fullName}`"

  /** The complement of a retype: the declaration kept its upstream type while the code around it
    * moved, so `decisions.tsv` needs its own row for it. Always `Reason.Configured`. A parameter's
    * FINDING is filed at the parameter (closes the lane's arithmetic) but its DECISION is recorded
    * one level out, at [[declarationOf]] — a parameter carries no note position of its own. */
  private def scopedOut(p: Program, s: Symbol, entry: String): Unit =
    val at = if s.flags.isParam then declarationOf(p, s) else s.id
    if s.flags.isParam || Decision.isDeclaration(p, s) then
      // dependent does not report its base's exclusions (D2) — the finding is attributed to the declaration's own unit.
      issues += Finding(Issue.ScopedOut, s.fullName, s"`$entry` on ${describe(p, s)}",
                        Decision.originOf(p, s.id), unitOf(p, s.id), declarationOf(p, s))
      // no decision where the site is UNSELECTABLE (declarationOf answers SymId.None); the finding above still counts it.
      if at != SymId.None then p.symbolOf(at).foreach { d =>
        record(Decision(
          kind       = Decision.Kind.ScopedOut,
          subject    = at,
          subjectFqn = d.fullName,
          // no `key` in `detail` — `Reason.Configured` already carries it; `param` only where subject != s.id.
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
          // an uninitialised @Null field defaults to JVM null, which is NOT the wrapper's empty
          // sentinel (isEmpty would read false); init to W.empty. Not applied to parameters.
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

  /** Union mode's one body rewrite: the frontend renders `return null` at a type-parameter return
    * as `null.asInstanceOf[T]` (`T = null` does not type-check at an abstract `T`); once the return
    * is `T | Null` the cast is a placeholder and goes. Narrow on purpose: only a null literal cast
    * to EXACTLY the former return type. */
  private def retireNullCast(was: TypeRepr, e: Term): Term = e match
    case Tree.Typed(lit @ Tree.Literal(Constant.NullC, _, _), tpt, _, _) if tpt.tpe == was => lit
    case other => other

  override def transformIdent(t: Tree.Ident)(using Program): Term =
    wrapped.get(t.sym).map(w => t.copy(tpe = w)).getOrElse(t)

  /** An operator is never unwrapped here: the traversal is bottom-up, so unwrapping the receiver of
    * `x == null` would rewrite it to `x.get == null` one node BEFORE [[nullTest]] could see it,
    * turning the mandatory rewrite into a run-time NPE. */
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

  /** A lambda body is a slot — the function's result, exactly as `return` is a method's (§1(a)).
    * The slot, in evidence-availability order: the lambda's recorded `resultTpt`; a `FunctionN`'s
    * last argument; the SAM method of an OWNED interface (retyped, so the body stays wrapped);
    * otherwise the SAM is a class file's — the formal can't say, so the body is unwrapped and
    * counted, as [[coerceArgs]] treats an external callee's argument. */
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

  /** `.orNull` at every LEAF of a value-producing expression — [[coerceTo]]'s unwrap half where no
    * target type is available to name (lambda bodies going to external SAM results, which java
    * accepts null by default). A primitive CAST target is the exception — unboxing null NPEs. */
  private def unwrapLeaves(e: Term): Term = e match
    case x if isWrapped(x) => unwrapOrNull(x)
    case x: Tree.Block     => x.copy(expr = unwrapLeaves(x.expr))
    case x: Tree.If        => x.copy(thenp = unwrapLeaves(x.thenp), elsep = unwrapLeaves(x.elsep))
    case x: Tree.Match     => x.copy(cases = x.cases.map(c => c.copy(body = unwrapLeaves(c.body))))
    // same rule as coerceTo's Tree.Typed arm: unwrap the operand, keep the cast's own tpt (never the wrapper's element).
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

  /** The slot-nullability rule: `.get` when the target slot is provably non-null (a primitive after
    * unboxing), `.orNull` when the slot accepts null (the java default for every reference type).
    * A dereference throws on null (java does too); a slot coercion preserves it. `CLAUDE.md` §4.4 */
  private def slotUnwrap(want: TypeRepr, e: Term): Term =
    if isPrimitiveSlot(want) then unwrap(e) else unwrapOrNull(e)

  /** Is the target slot a primitive (about the slot's FORMAL, not the annotated declaration —
    * planning already refused annotated primitives). Checked against cached [[primSyms]] so no
    * `Program` is needed, keeping [[coerceTo]] a plain function passable to [[mapReturns]]. */
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
      // unless the formal cannot be written here: a callee type variable doesn't resolve at the call site (G12).
      // W.empty needs no ascription — it conforms at every element type, so skip it (G20).
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
      // cast keeps its own type (`tpt`, not `want` — ENGINE-LIMITS §0); unwrap decided by the cast's target: primitive -> .get, reference -> .orNull.
      case x: Tree.Typed if isWrapped(x.expr) => x.copy(expr = slotUnwrap(x.tpt.tpe, x.expr))
      case _             => e
    else e

  /** The callee's own type variables, replaced by what the RECEIVER instantiated them with — a
    * formal's `T` is not in scope at the call site, so substitute the receiver's actual type
    * arguments (`CLAUDE.md` §4.56). Empty where nothing can be said (raw/non-generic receiver,
    * arity mismatch, inherited callee); those fall through to [[coerceTo]]'s refusal. */
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
    * `CollectionsTransform.retarget` points the receiver at an external API with its own
    * nullability model, so coercing against the java formals this phase wrapped is wrong. Excludes
    * ordinary inheritance (both owned) and external-to-external calls (neither owned) — §4.56 read
    * at a phase interaction: this phase did not retarget the receiver, so it may not reason about it. */
  private def isRetargetted(t: Tree.Apply)(using p: Program): Boolean =
    val recvHead = t.fun match
      case s: Tree.Select => headSym(s.qual.tpe)
      case _              => scala.None
    val methodOwner = p.symbolOf(t.method).map(_.owner)
    (recvHead, methodOwner) match
      case (Some(rh), Some(mo)) =>
        rh != SymId.None && mo != SymId.None && rh != mo && !p.owns(rh) && p.owns(mo)
      case _ => false

  /** Base's + this run's own SUBSTITUTED (dropped+injected) owners, upstream FQNs. Item 2. */
  private def substitutedOwners: Set[String] = runScope.baseSubstitutedOwners ++ runScope.ownSubstitutedOwners

  /** Receiver type dropped+injected -- its surface's nullability is not java's `@Null`, so a
    * formal wrapped at the (inherited) callee's OWN declaration must not coerce here. Item 2. */
  private def isSubstitutedReceiver(t: Tree.Apply)(using p: Program): Boolean =
    val recvHead = t.fun match
      case s: Tree.Select => headSym(s.qual.tpe)
      case _              => scala.None
    recvHead.exists(h => p.symbolOf(h).exists(s => substitutedOwners(s.fullName)))

  private def coerceArgs(t: Tree.Apply)(using p: Program): Term =
    // external callee: a class file's formal answers "what TYPE", not "does this slot accept null" —
    // unwrap with `.orNull` (java's default) and count the seam.
    // substituted receiver: java formals wrapped at plan time don't describe its actual API.
    val retargetted = isRetargetted(t) || isSubstitutedReceiver(t)
    val formals = p.symbolOf(t.method).map(_.info).collect {
      case TypeRepr.MethodType(ps, _, _)                       => ps.map(_._2)
      case TypeRepr.PolyType(_, TypeRepr.MethodType(ps, _, _)) => ps.map(_._2)
    }
    val owned = p.owns(t.method)
    if retargetted then
      t.args.filter(isWrapped).foreach { a =>
        issues += Finding(Issue.UncoercibleSeam, p.symbolOf(t.method).map(_.fullName).getOrElse("?"),
          "a wrapped argument reaches a retargetted or substituted callee — unwrapped because the " +
            "receiver's type does not describe the java method's @Null-annotated formal",
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
        // no formal to coerce against: unwrap with `.orNull` — java's default is every reference slot accepts null.
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

  /** The shape the contract takes in the emitted type — `T | Null` (union floor, not transparent
    * at an abstract `T`), `W[T]` (a configured wrapper — CLOSES K13, composes at every `T`), or
    * `Option[T]` (same closure, `scala.Option` semantics/allocation cost). `Named` and `Option`
    * share the five-member contract: `apply`, `empty`, `get`, `orNull`, `isEmpty`.
    * `ENGINE-LIMITS.md` K13 */
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
