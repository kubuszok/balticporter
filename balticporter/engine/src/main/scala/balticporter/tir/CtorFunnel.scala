package balticporter.tir

/** Nominates which Java constructor becomes Scala's PRIMARY, lifts its `super(args)` into the
  * `extends` clause, and routes every other constructor through `this(...)`. Shared between the
  * emitter and [[OmissionCheck]] so the check cannot drift. Seven shapes: unique root, no-arg
  * root, widest pass-through, synthesised primary, synthetic-shaped root, promoted nilary, padded
  * throwable synthesis. `ENGINE-LIMITS.md` C3, C7. */
object CtorFunnel:

  /** the plan for one class: which Java constructor is Scala's primary, the super arguments to
    * lift into the `extends` clause, and the leftover statements that become class body. */
  final case class Plan(
      primary: Option[Tree.DefDef],
      superArgs: List[Term],
      /** the promoted constructor's body minus its leading super/this delegation. */
      primaryBody: List[Statement],
      /** Synthesised primary slots: parent constructor's formals as `(name, type)` pairs.
        * Non-empty when no java constructor can be the primary but every root reaches the same
        * parent constructor. Admissible only for a uniform target; differing overloads refuse. */
      synthetic: List[(String, TypeRepr)] = Nil,
      /** Per-class marker type name disambiguating the synthesised primary when the slot list
        * would collide or be shadowed by a real constructor. Minted in the class's companion as
        * `protected` (not `private`). // ENGINE-LIMITS C8, C9 */
      marker: Option[String] = scala.None,
      /** How many of [[synthetic]]'s slots are parent-constructor formals (the rest are field slots). */
      superSlots: Int = 0,
      /** the fields whose value is hoisted into the primary's parameter list, in slot order. */
      fieldSlots: List[FieldSlot] = Nil,
      /** Full delegation argument list per root (super slots then field slots). Marker `null`
        * appended by the emitter. */
      delegations: Map[SymId, List[Term]] = Map.empty,
      /** Leading statements consumed into field slots per root. */
      consumed: Map[SymId, Int] = Map.empty,
      /** Refused field-slot candidates with reason, for porter notes. */
      notSlot: List[(String, String)] = Nil,
      /** Trailing context clauses (`using T`) a phase added to this class's constructors.
        * Kept separate from [[primaryParams]] so "is this constructor nilary" asks java's list.
        * Always trailing. // ENGINE-LIMITS CT4 */
      givens: List[List[Tree.ValDef]] = Nil,
      /** Post-body parameter slots from `resolvedThroughParent`: parameters the parent secondary's
        * post-delegation body uses, carried as synthesised class parameters so the primary's body
        * can replay the post-body without double evaluation. Positioned after super slots and
        * before field slots. Each is `(name, nullable type)` — null for roots that did not go
        * through that parent secondary. // ENGINE-LIMITS C3 item 4 */
      postBodySlots: List[(String, TypeRepr)] = Nil,
      /** Parent secondary's post-delegation body, guarded by a null check on the post-body
        * parameter. Rendered in the synthesised primary's class body (not in each secondary).
        * Already retyped through the child's `extends` clause. */
      primaryPostBody: List[Statement] = Nil,
      /** True when the promoted root passes its own parameters straight through to super,
        * enabling positional delegation for all siblings. */
      collapse: Boolean = false,
      /** Per-java-ctor delegation to the synthesised primary. Keyed by java ctor symbol; the
        * terms reference that ctor's own param symbols. A child's `resolvedThroughParent` resolves
        * through this when the parent has 2+ java roots. Empty for non-synthesised plans.
        * // ENGINE-LIMITS C3 */
      rootArgs: Map[SymId, List[Term]] = Map.empty,
  ):
    /** the primary's VALUE parameters — java's own, never the context clause [[givens]] holds. */
    def primaryParams: List[Tree.ValDef] =
      primary.map(_.paramss.dropRight(givens.size).flatten).getOrElse(Nil)

    /** True for a synthesised primary (marker-only plans included). // ENGINE-LIMITS C7 */
    def isSynthesised: Boolean = synthetic.nonEmpty || marker.isDefined

  object Plan:
    /** No primary nominated; the class keeps scala's implicit nilary primary. May still carry
      * [[Plan.givens]] for classes whose constructors have a context clause. */
    val none: Plan = Plan(scala.None, Nil, Nil)

  /** One hoisted field: the primary takes its value as a parameter. `mutable` decides
    * `val` vs `var` and is separate from slot-eligibility. */
  final case class FieldSlot(
      field: SymId,
      /** Engine-minted parameter name. // ENGINE-LIMITS C2 */
      name: String,
      tpe: TypeRepr,
      mutable: Boolean,
      /** Writes consumed by the funnel; compared against whole-program writes by [[Plans]]. */
      writes: Int,
  )

  /** How one secondary's `super(args)` reaches the parent. Per-ROOT: the emitter renders it and
    * [[OmissionCheck]] counts it through the same function. */
  enum SuperCall:
    /** Positional delegation — for synthesised primaries and collapses. */
    case Positional(args: List[Term])
    /** Type-matched delegation against a promoted root. */
    case Matched(slots: List[Slot])
    /** Arguments lost; [[OmissionCheck]] reports them. */
    case Dropped

  /** One argument position of a [[SuperCall.Matched]] delegation. */
  enum Slot:
    case Arg(term: Term)
    case NullAt(tpe: TypeRepr)
    /** The message `Throwable(Throwable)` computes (`Objects.toString(cause, null)`). */
    case CauseMessage(cause: Term)

  /** a padded slot needs a value that `null` can inhabit; a primitive has none. */
  val primitiveTypeNames: Set[String] =
    Set("scala.Int", "scala.Long", "scala.Float", "scala.Double",
        "scala.Short", "scala.Byte", "scala.Char", "scala.Boolean", "scala.Unit")

  /** Whole-program funnel decisions. Withholds paramful promotions where a subclass reaches the
    * class argument-free, iterating to a fixpoint. The fixpoint runs over OWNED classes only;
    * non-owned classes are reconciled against the base's published contract.
    * @param surfaceView what this run may conclude about classes it does not emit. // ENGINE-LIMITS D4 */
  final class Plans(program: Program, surfaceView: Option[Surface] = scala.None):
    // `Option` because a default argument cannot refer to another parameter of the same list.
    private val surface: Surface = surfaceView.getOrElse(TrivialSurface(program))

    // `allClassDefs` covers method-local classes too (JS-C30).
    private val classes: List[Tree.ClassDef] =
      program.units.flatMap(u => StandardTraversal.allClassDefs(u)(using program))

    /** …and the ones this run EMITS, which is the fixpoint's whole domain. */
    private val ownedClasses: List[Tree.ClassDef] = classes.filter(cd => surface.owns(cd.symbol))

    /** Promote the nilary constructor when no constructor carries `super(args)`, to avoid
      * `def this()` clashing with scala's implicit nilary primary. */
    private def nilaryPlan(cd: Tree.ClassDef): Option[Plan] =
      val ctors = ctorsOf(program, cd.body)
      if ctors.exists(c => superArgsOf(program, c).nonEmpty) then scala.None
      else
        for
          nil  <- ctors.find(valueParams(program, _).isEmpty)
          head <- headStmt(nil)
          (m, as) <- head match
            case Tree.Apply(Tree.Select(r, mm, _, _), aas, _, _, _)
                if isInitName(program, mm) && !r.isInstanceOf[Tree.Super] && aas.nonEmpty => Some((mm, aas))
            case _ => scala.None
          eff  <- effects(m, as, 0)
        yield promoted(program, nil, Nil, eff.stats ++ stmtsOf(nil).tail)

    /** Paramful including synthesised primaries (slots live in `Plan.synthetic`). // ENGINE-LIMITS C1 */
    private def paramfulPrimary(p: Plan): Boolean = p.primaryParams.nonEmpty || p.isSynthesised

    /** True when a nilary secondary exists so `extends C` with no args is reachable. */
    private def reachableArgumentFree(s: SymId, p: Plan): Boolean =
      val hasNilary = classes.find(_.symbol == s).exists(cd =>
        ctorsOf(program, cd.body).exists(valueParams(program, _).isEmpty))
      if p.isSynthesised then hasNilary
      // A promoted primary reachable through a nilary delegation, narrowed to classes whose
      // parent was converted to a trait (structural signature: `isTrait && !isAbstract`).
      else if p.primary.isDefined && hasNilary then
        val hasTraitConvertedParent = classes.find(_.symbol == s).exists { cd =>
          parentSyms(cd).exists(ps =>
            program.symbolOf(ps).exists(sym => sym.flags.isTrait && !sym.flags.isAbstract))
        }
        if !hasTraitConvertedParent then false
        else
          val primarySym = p.primary.get.symbol
          classes.find(_.symbol == s).exists { cd =>
            ctorsOf(program, cd.body).exists { c =>
              valueParams(program, c).isEmpty && c.symbol != primarySym &&
                reachesCtor(program, c, primarySym)
            }
          }
      else false

    /** Topological sort: parents before children, depth-limited BFS. */
    private val topoClasses: List[Tree.ClassDef] =
      val syms = classes.map(_.symbol).toSet
      val depth = collection.mutable.Map[SymId, Int]()
      def d(s: SymId, fuel: Int): Int =
        if fuel <= 0 || !syms(s) then 0
        else depth.getOrElseUpdate(s, {
          val cd = classes.find(_.symbol == s).get
          val pd = parentSyms(cd).filter(syms).map(p => d(p, fuel - 1)).maxOption.getOrElse(0)
          pd + 1
        })
      classes.foreach(cd => d(cd.symbol, 32))
      classes.sortBy(cd => depth.getOrElse(cd.symbol, 0))

    private val plans: Map[SymId, Plan] =
      // C3: compute parents-first so a child can resolve through a parent's synthesised plan.
      var acc     = Map.empty[SymId, Plan]
      topoClasses.foreach { cd =>
        acc = acc.updated(cd.symbol, plan0(program, cd, parentPlanOf = acc.get))
      }
      // only apply nilaryPlan where the plan is truly un-nominated (not synthesised)
      classes.foreach { cd =>
        val p = acc(cd.symbol)
        if p.primary.isEmpty && !p.isSynthesised then
          nilaryPlan(cd).foreach(q => acc = acc.updated(cd.symbol, q))
      }
      // Withholding fixpoint: only ever REMOVES promotions, so it terminates.
      // A withheld synthesis falls back to the plan WITHOUT synthesis. // ENGINE-LIMITS C1
      var changed = true
      while changed do
        changed = false
        // owned classes only: a dependent must not demote what the base emitted. // ENGINE-LIMITS D4
        // a synthesised child passes its slots to the parent root: it demands no nilary parent
        val needNilary = ownedClasses.filter(cd => acc.get(cd.symbol).forall(p => p.superArgs.isEmpty && !p.isSynthesised))
          .flatMap(parentSyms)
          .toSet
        acc.foreach { (s, p) =>
          if surface.owns(s) && paramfulPrimary(p) && needNilary(s) && !reachableArgumentFree(s, p) then
            // Fallback order: plan without synthesis, then nilaryPlan, then Plan.none.
            // First attempt is currently inert (C1) but kept for correct ordering.
            val cd0 = classes.find(_.symbol == s)
            val demoted = cd0.map(plan0(program, _, synthesis = false))
              .filter(q => q.primary.isDefined && !paramfulPrimary(q))
              .orElse(cd0.flatMap(nilaryPlan))
              .getOrElse(Plan.none)
            acc = acc.updated(s, demoted)
            changed = true
        }
      // A1: val/var decided last. Narrowed to `final` or `private` fields. // ENGINE-LIMITS D4
      hosting(reconciled(acc))

    /** Add context clauses to plans with no primary. Runs over ALL classes (including non-owned)
      * as a single post-pass. No-op when no phase threads constructors. // ENGINE-LIMITS CT5 */
    private def hosting(acc: Map[SymId, Plan]): Map[SymId, Plan] =
      classes.foldLeft(acc) { (m, cd) =>
        val p = m.getOrElse(cd.symbol, Plan.none)
        if p.primary.isDefined || p.isSynthesised || p.givens.nonEmpty then m
        else
          classGivens(program, cd) match
            case Nil => m
            case gs  => m.updated(cd.symbol, p.copy(givens = gs))
      }

    /** Reconcile non-owned classes against the base's published `primary=` row. Non-wall classes
      * are cross-checked (disagreement is fatal); wall classes adopt the base's demotion. */
    private def reconciled(acc: Map[SymId, Plan]): Map[SymId, Plan] =
      // exclude enums: TirEmitter.enumDef handles them directly, not through the funnel
      val nonOwned = classes.filterNot(cd =>
        surface.owns(cd.symbol) || program.symbolOf(cd.symbol).exists(_.flags.isEnum))
      if nonOwned.isEmpty then acc
      else
        var out = acc
        nonOwned.foreach { cd =>
          val local = out.getOrElse(cd.symbol, Plan.none)
          val wall  = paramfulPrimary(local) && !reachableArgumentFree(cd.symbol, local)
          val fqn   = program.symbolOf(cd.symbol).map(_.fullName).getOrElse("?")
          surface.typeShape(cd.symbol) match
            case Surface.Answer.Own => () // cannot happen: `nonOwned` is the complement of `owns`
            case Surface.Answer.Published(shape, module) =>
              val here = renderedPrimary(cd, local)
              val there = shape.primary.map(_.render)
              if there.isEmpty || there.contains(here) then ()
              else
                // D15: follow the base's published plan, try seeded demotion first
                seededDemotion(cd, there.get) match
                  case Some(p) => out = out.updated(cd.symbol, p)
                  case scala.None =>
                    // D15: non-owned class, descriptor disagreement expected (D12/O8). Non-fatal.
                    surface.gap(Surface.Gap(fqn,
                      s"$module published the primary `(${there.get})` for this class " +
                        s"(locally derived: `(${here})`). " +
                        (if wall then
                          "This class's primary is a function of its SUBCLASSES, which differ between " +
                            "the two modules. "
                        else "") +
                        "The published descriptor names types the dependent's local derivation cannot " +
                          "resolve (an opaque type the base's retyping phases minted over base units, " +
                          "D12/O8). This run does not emit this class, so the local plan is used for the " +
                          "fixpoint only and the dependent follows the base's published signature at " +
                          "call sites",
                      Some(module), fatal = false,
                      fix = "§1(a) ENGINE, FOLLOWED (D15): the dependent follows the base's published " +
                        "constructor signature — the descriptor disagreement is expected (the " +
                        "opaque/retyping phases do not re-derive over base units) and does not " +
                        "reach emitted text"))
            case Surface.Answer.Unknown(why, module) =>
              // fatal only for WALL classes whose plan depends on subclasses
              surface.gap(Surface.Gap(fqn, why, module, fatal = wall,
                fix = if wall then
                        "§1(b) PER-LIBRARY, OPERATIONAL: run the base port so it publishes a contract, " +
                        "or declare an EMPTY manifest for that resolution root if it is genuinely not a " +
                        "ported module (§1.5) — that is a statement, and it exempts this question"
                      else
                        "§1(b) PER-LIBRARY, OPERATIONAL: this class's primary does not depend on its " +
                        "subclasses, so the local derivation is the base's answer and nothing is wrong " +
                        "today; running the base port would let the engine CONFIRM that"))
        }
        out

    /** Render the primary's slots in the contract's descriptor grammar. */
    private def renderedPrimary(cd: Tree.ClassDef, p: Plan): String =
      def param(t: TypeRepr): Param =
        Descriptor.ofInfo(program, TypeRepr.MethodType(List("_" -> t), TypeRepr.NoType))
          .flatMap(_.params.headOption).getOrElse(Param.Unresolved)
      val slots =
        if p.isSynthesised then p.synthetic.map((_, t) => param(t)) ++ p.marker.map(_ => Param.Unresolved).toList
        else p.primaryParams.map(v => param(v.tpt.tpe))
      Descriptor(slots).render

    /** Find a local plan whose rendered primary matches the published row. `None` if none does. */
    private def seededDemotion(cd: Tree.ClassDef, published: String): Option[Plan] =
      val candidates =
        plan0(program, cd) :: plan0(program, cd, synthesis = false) :: nilaryPlan(cd).toList ::: List(Plan.none)
      candidates.find(p => renderedPrimary(cd, p) == published)

    /** Whole-program write count per field, built once. */
    private lazy val writesPerField: Map[SymId, Int] =
      given Program = program
      val acc = collection.mutable.Map[SymId, Int]().withDefaultValue(0)
      def targeted(t: Term): Option[SymId] = t match
        case Tree.Ident(s, _, _)     => Some(s)
        case Tree.Select(_, s, _, _) => Some(s)
        case _                       => scala.None
      val ph = new Phase:
        def name = "ctor-field-writes-program"
        override def transformTerm(t: Term)(using Program): Term =
          t match
            case Tree.Assign(lhs, _, _, _, _)  => targeted(lhs).foreach(s => acc(s) += 1)
            case Tree.IncDec(tg, _, _, _, _) => targeted(tg).foreach(s => acc(s) += 1)
            case _                           => ()
          t
      program.units.foreach(u => StandardTraversal.mapStat(ph, u))
      acc.toMap

    /** Plans with val/var decided LAST (after replays). Write count includes replay writes. */
    private lazy val decided: Map[SymId, Plan] =
      val written = writesPerField
      val byReplay = collection.mutable.Map[SymId, Int]().withDefaultValue(0)
      plans.values.flatMap(_.fieldSlots).map(_.field).toSet.foreach { f =>
        replays.values.foreach(stats => byReplay(f) += writeCount(program, stats, f))
      }
      plans.map { (s, p) =>
        if p.fieldSlots.isEmpty then s -> p
        else s -> p.copy(fieldSlots = p.fieldSlots.map { fs =>
          val fl   = program.symbolOf(fs.field).map(_.flags)
          val safe = fl.exists(f => f.isFinal || f.isPrivate)
          val all  = written.getOrElse(fs.field, 0) + byReplay(fs.field)
          fs.copy(mutable = !(safe && all <= fs.writes))
        })
      }

    def apply(cd: Tree.ClassDef): Plan = decided.getOrElse(cd.symbol, Plan.none)

    /** Read-only shape name for the decision channel. Derived from the plan, not recorded. */
    def shape(cd: Tree.ClassDef): String =
      val p     = apply(cd)
      val ctors = ctorsOf(program, cd.body)
      val roots = ctors.filterNot(delegatesToThis(program, _))
      // a synthesised plan under a throwable parent came through `throwablePadding`
      if p.isSynthesised then
        if jdkThrowableParent(program, cd) then "padded-throwable-synthesis" else "synthesised-primary"
      else
        p.primary match
          case scala.None            => if ctors.isEmpty then "no-constructor" else "not-funnelled"
          case Some(_) if roots.sizeIs == 1 => "unique-root"
          case Some(c) if valueParams(program, c).isEmpty =>
            // several roots with a nilary one chosen
            if ctors.exists(x => superArgsOf(program, x).nonEmpty) then "no-arg-root" else "promoted-nilary"
          case Some(_)               => "widest-root"

    /** The class's constructors as the funnel read them. */
    def constructorsOf(cd: Tree.ClassDef): List[Tree.DefDef] = ctorsOf(program, cd.body)

    // ---- what the promotion COSTS: the promoted body on paths java never ran it ----

    /** Constructors on whose path the promoted body runs but java's did not.
      * Counted by [[OmissionCheck.promotedBodyOnEveryPath]]. // ENGINE-LIMITS C6, C7 */
    def promotionEscapes(cd: Tree.ClassDef): List[Tree.DefDef] =
      val p = plans.getOrElse(cd.symbol, Plan.none)
      escapesOf(program, cd, p.primary, p.primaryBody)

    /** True if the primary has parameters (emitter and check share this). */
    def paramfulPrimaryOf(cd: Tree.ClassDef): Boolean = paramfulPrimary(apply(cd))

    /** The nilary constructor dropped because it clashes with the implicit primary. `None` when
      * paramful or when the nilary ctor IS the promoted primary.
      * Counted by `OmissionCheck.droppedNilaryCtors`. // ENGINE-LIMITS CT4, CT5 */
    def droppedNilaryCtor(cd: Tree.ClassDef): Option[Tree.DefDef] =
      val p = plans.getOrElse(cd.symbol, Plan.none)
      if paramfulPrimary(p) then scala.None
      else
        ctorsOf(program, cd.body).find { d =>
          !p.primary.map(_.symbol).contains(d.symbol) &&
            delegationOnlyNilary(program, d).exists(_.nonEmpty)
        }

    /** the constructors on whose path java did not run the promoted body — BEFORE the prefix strip
      * below has a chance to make the duplication disappear. */
    private def escapingRoots(cd: Tree.ClassDef): List[Tree.DefDef] =
      val p = plans.getOrElse(cd.symbol, Plan.none)
      escapingRootsOf(program, cd, p.primary, p.primaryBody)

    /** The escaping root's body after stripping the promoted body as a prefix. `None` when
      * not escaping or when the prefix does not match. Compared via canonical rendering. */
    def residualBody(cd: Tree.ClassDef, d: Tree.DefDef): Option[List[Statement]] =
      val p = plans.getOrElse(cd.symbol, Plan.none)
      residualBodyOf(program, cd, p.primary, p.primaryBody, d)

    // ---- effect replay: expressing a `super(args)` a secondary constructor cannot make ----

    /** Parent constructor statements replayed after `this()`, or `None` if refused. */
    def replayFor(cd: Tree.ClassDef, d: Tree.DefDef): Option[List[Statement]] =
      replays.get((cd.symbol, d.symbol))

    /** Post-body statements for the synthesised primary's class body (guarded by a null check).
      * Non-empty only when `resolvedThroughParent` produced a post-body carried through a
      * parameter. Rendered once in the class body, not per-secondary. */
    def primaryPostBodyFor(cd: Tree.ClassDef): List[Statement] =
      decided.getOrElse(cd.symbol, Plan.none).primaryPostBody

    /** Names the guard that refused parent-delegation resolution, or `None` if not refused.
      * Used by `OmissionCheck` to produce a finding naming the refusal guard.
      * With post-bodies carried through parameters (not inlined), the only remaining refusals
      * are `super.m()` or `return` in the post-body. // ENGINE-LIMITS C3 item 4 */
    def inlineDelegationRefused(cd: Tree.ClassDef, d: Tree.DefDef): Option[String] =
      val p = decided.getOrElse(cd.symbol, Plan.none)
      // only for classes that are NOT synthesised (the synthesis succeeded without this root)
      if p.isSynthesised then scala.None
      else
        val roots = ctorsOf(program, cd.body).filterNot(delegatesToThis(program, _))
        val calls = roots.map(r => superTarget(program, r) -> superArgsOf(program, r))
        val targets = calls.map(_._1).distinct
        if targets.sizeIs <= 1 then scala.None // uniform — not the inline delegation case
        else
          val parentSym = parentSyms(cd).headOption.getOrElse(SymId.None)
          program.definitionOf(parentSym).collect { case c: Tree.ClassDef => c }.flatMap { pcd =>
            val parentRoots = ctorsOf(program, pcd.body).filterNot(delegatesToThis(program, _))
            if parentRoots.sizeIs != 1 then scala.None
            else
              val parentRootSym = parentRoots.head.symbol
              val (target, args) = (superTarget(program, d), superArgsOf(program, d))
              if target == parentRootSym || args.isEmpty then scala.None
              else
                inlineDelegation(program, target, args, parentRootSym, depth = 0) match
                  case Some(_) => scala.None // resolution succeeded — no refusal
                  case scala.None =>
                    Some("parent-delegation resolution refused: the parent constructor's " +
                      "post-body contains `super.m()` or `return`, which dispatch wrongly " +
                      "or leave the wrong frame in a subclass")
          }

    // ---- the delegation itself: the ONE answer the emitter renders and the check counts ----

    /** Per-root delegation: synthesised = positional, promoted = type-matched.
      * Every argument must find a home or the result is [[SuperCall.Dropped]]. */
    def superCall(cd: Tree.ClassDef, args: List[Term]): SuperCall =
      val plan = apply(cd)
      // empty args still falls through: `super()` against a promoted paramful primary needs matching.
      // compare against `superSlots`, not `synthetic.size` (field slots are not super formals)
      if plan.isSynthesised then
        if args.sizeIs == plan.superSlots then SuperCall.Positional(args) else SuperCall.Dropped
      else
        val ps = plan.primaryParams
        // collapse is positional; attempt type-matched fill FIRST (throwable padding needs it)
        def positional: SuperCall =
          if plan.collapse && args.sizeIs == ps.size then SuperCall.Positional(args) else SuperCall.Dropped
        if ps.isEmpty || plan.superArgs.size != ps.size || args.sizeIs > ps.size then positional
        else
          val used  = collection.mutable.Set[Int]()
          val slots = ps.map { v =>
            val want = headName(program, v.tpt.tpe)
            args.zipWithIndex.find((a, k) => !used(k) && headName(program, a.tpe) == want) match
              case Some((a, k)) => used += k; Some(Slot.Arg(a): Slot)
              case scala.None   =>
                if want.exists(primitiveTypeNames) then scala.None else Some(Slot.NullAt(v.tpt.tpe))
          }
          if slots.exists(_.isEmpty) || used.size != args.size then positional
          else
            val ss = slots.flatten
            // the JDK's `Throwable(Throwable)` message, but only when the cause can be READ TWICE —
            // the delegation names it in both slots and scala cannot bind a value before `this(...)`
            if isJdkCauseCall(cd, args, ss) && simple(args.head) then
              SuperCall.Matched(ss.map { case _: Slot.NullAt => Slot.CauseMessage(args.head); case s => s })
            else SuperCall.Matched(ss)

    /** True when this is a `Throwable(Throwable)` call with exactly one padded String slot. */
    private def isJdkCauseCall(cd: Tree.ClassDef, args: List[Term], slots: List[Slot]): Boolean =
      val padded = slots.collect { case n: Slot.NullAt => n }
      args.sizeIs == 1 && padded.sizeIs == 1 &&
        headName(program, padded.head.tpe).contains("java.lang.String") &&
        jdkThrowableParent(program, cd)

    /** True when a non-simple cause argument cannot be duplicated for the message slot. */
    def causeMessageLost(cd: Tree.ClassDef, d: Tree.DefDef): Boolean =
      val args = superArgsOf(program, d)
      args.nonEmpty && !simple(args.head) && !apply(cd).primary.map(_.symbol).contains(d.symbol) &&
        replayFor(cd, d).isEmpty && (superCall(cd, args) match
          case SuperCall.Matched(slots) => isJdkCauseCall(cd, args, slots)
          case _                        => false)

    /** True when this constructor's `super(args)` survives in emitted code. */
    def superExpressed(cd: Tree.ClassDef, d: Tree.DefDef): Boolean =
      val args = superArgsOf(program, d)
      val plan = apply(cd)
      // use `.map(_.symbol).contains`, NOT `primary.contains(d.symbol)` (widens to `Any`, always false)
      // also check `Plan.delegations`: a padded root's delegation is rendered without consulting `superCall`
      args.isEmpty || plan.primary.map(_.symbol).contains(d.symbol) || replayFor(cd, d).isDefined ||
        plan.delegations.contains(d.symbol) || superCall(cd, args) != SuperCall.Dropped

    /** Private members a replay reaches, to be widened. // ENGINE-LIMITS C15 */
    def widenedMembers: Set[SymId] = widened.toSet

    private val widened = collection.mutable.Set[SymId]()

    /** Widenings for subclasses this run cannot see. Narrowed to owned, extensible, non-private,
      * paramful constructors whose replay is usable, touching unreachable mutable fields.
      * // ENGINE-LIMITS C15, D5 */
    def externalReplayWidenings: Set[SymId] = externalReplay

    // lazy: reads `decided` which reads `replays`; strict would NPE
    private lazy val externalReplay: Set[SymId] =
      val out = collection.mutable.Set[SymId]()
      ownedClasses.foreach { cd =>
        if extensibleFromOutside(cd) then
          ctorsOf(program, cd.body).foreach { d =>
            val paramful = valueParams(program, d).nonEmpty
            val callable = !program.symbolOf(d.symbol).exists(_.flags.isPrivate)
            if paramful && callable then
              val (conceivable, touched) = replayReach(d.symbol, 0, Set.empty)
              if conceivable then
                // only widen members this run OWNS (chain may climb into ancestor classes) // ENGINE-LIMITS D5
                out ++= touched.collect {
                  case (s, viaPrefix)
                    if isField(s) && !immutableSlotFields(s) && program.symbolOf(s)
                      .exists(sy => surface.owns(sy.owner) && unreachableFromASubclass(sy, viaPrefix)) => s
                }
          }
      }
      out.toSet

    /** True when a subclass replay cannot reach this member: private, package-private,
      * or protected accessed through a prefix (not `this`). */
    private def unreachableFromASubclass(sy: Symbol, viaPrefix: Boolean): Boolean =
      sy.flags.isPrivate || sy.flags.isPackagePrivate || (sy.flags.isProtected && viaPrefix)

    /** Only fields may be widened; methods participate in override contracts. // ENGINE-LIMITS C15 */
    private def isField(s: SymId): Boolean =
      program.definitionOf(s).exists(_.isInstanceOf[Tree.ValDef])

    /** Immutable slot fields: widening would cause E052 in a dependent's replay. */
    private lazy val immutableSlotFields: Set[SymId] =
      decided.values.flatMap(_.fieldSlots).filterNot(_.mutable).map(_.field).toSet

    /** True if external code can extend `cd` (not final/enum/annotation, not method-local). */
    private def extensibleFromOutside(cd: Tree.ClassDef): Boolean =
      program.symbolOf(cd.symbol).exists { s =>
        !s.flags.isFinal && !s.flags.isEnum && !s.flags.isAnnotation && !s.flags.isModule &&
          (unitSymbols(cd.symbol) || classSymbols(s.owner))
      }

    private lazy val classSymbols: Set[SymId] = classes.map(_.symbol).toSet
    private lazy val unitSymbols: Set[SymId]  = program.units.map(_.symbol).toSet

    /** Returns (is replay conceivable, symbols touched with via-prefix flag). */
    private def replayReach(ctor: SymId, depth: Int, seen: Set[SymId]): (Boolean, Map[SymId, Boolean]) =
      if depth > 6 || seen(ctor) then (false, Map.empty)
      else
        defOf(ctor) match
          case scala.None => (false, Map.empty)
          case Some(d) =>
            given Program = program
            val here = collection.mutable.Map[SymId, Boolean]()
            var ok   = true
            def saw(s: SymId, seenViaPrefix: Boolean): Unit =
              here(s) = here.getOrElse(s, false) || seenViaPrefix
            // a qualifier that is not `this` or `super` — scala refuses protected access one level down
            def viaPrefix(q: Term): Boolean =
              !(q.isInstanceOf[Tree.This] || q.isInstanceOf[Tree.Super])
            val ph = new Phase:
              def name = "ctor-replay-reach"
              override def transformTerm(t: Term)(using Program): Term =
                t match
                  case _: Tree.Super  => ok = false
                  case _: Tree.Return => ok = false
                  case _              => ()
                t match
                  // track whether access is via a prefix (not `this`/`super`)
                  case Tree.Apply(Tree.Select(q, _, _, _), _, m, _, _) => saw(m, viaPrefix(q))
                  case Tree.Select(q, s, _, _)                        => saw(s, viaPrefix(q))
                  case other => program.symbolIn(other).foreach(saw(_, false))
                t
            val (body, chain) = headStmt(d) match
              case Some(Tree.Apply(Tree.Select(_, m, _, _), as, _, _, _)) if isInitName(program, m) =>
                (stmtsOf(d).tail, if as.nonEmpty then Some(m) else scala.None)
              case _ => (stmtsOf(d), scala.None)
            body.foreach(StandardTraversal.mapStat(ph, _))
            val (chainOk, chainTouched) =
              chain.map(replayReach(_, depth + 1, seen + ctor)).getOrElse((true, Map.empty[SymId, Boolean]))
            val merged = chainTouched.foldLeft(here.toMap) { case (acc, (s, v)) =>
              acc.updated(s, acc.getOrElse(s, false) || v)
            }
            (ok && chainOk, merged)

    private val replays: Map[(SymId, SymId), List[Statement]] =
      val out = collection.mutable.Map[(SymId, SymId), List[Statement]]()
      classes.foreach { cd =>
        val p = plans.getOrElse(cd.symbol, Plan.none)
        // refuse synthesised primaries: replaying would run the parent's body TWICE
        val prologue =
          if paramfulPrimary(p) then scala.None else prologueOf(cd.symbol, 0)
        if prologue.isDefined then
          ctorsOf(program, cd.body).foreach { d =>
            if !p.primary.contains(d.symbol) then
              superApply(d).foreach { case (m, args) =>
                effects(m, args, 0).foreach { e =>
                  val stats   = e.stats
                  val touched = collection.mutable.Set[SymId]()
                  // include ctor's own statements for superseding check
                  val after = stats ++ stmtsOf(d).tail
                  // deferred prologue is shared (java ran it too), so skip that prefix
                  val shared = e.deferredTo.flatMap(prologueOf(_, 0)).map(_.size).getOrElse(0)
                  if usable(cd, d, stats, touched) && supersedes(after, prologue.get.drop(shared)) then
                    out((cd.symbol, d.symbol)) = retyped(stats, parentTypeSubst(cd))
                    widened ++= touched.filter { s =>
                      program.symbolOf(s).exists(sy => sy.flags.isPrivate && sy.owner != cd.symbol)
                    }
                }
              }
          }
      }
      out.toMap

    private def defOf(s: SymId): Option[Tree.DefDef]      = program.definitionOf(s).collect { case d: Tree.DefDef => d }
    private def classOfSym(s: SymId): Option[Tree.ClassDef] = program.definitionOf(s).collect { case c: Tree.ClassDef => c }

    /** a leading `super(args)` with arguments, as (target constructor, arguments). */
    private def superApply(d: Tree.DefDef): Option[(SymId, List[Term])] = headStmt(d) match
      case Some(Tree.Apply(Tree.Select(_: Tree.Super, m, _, _), args, _, _, _))
          if args.nonEmpty && isInitName(program, m) => Some((m, args))
      case _ => scala.None

    /** What the emitted `extends P` runs on the way in (the plan's super call and body). */
    private def prologueOf(cls: SymId, depth: Int): Option[List[Statement]] =
      if depth > 8 then scala.None
      else
        classOfSym(cls) match
          case scala.None => Some(Nil)
          case Some(cd) =>
            val p = plans.getOrElse(cd.symbol, Plan.none)
            // synthesised primary is paramful, so `extends P` reaches P's nilary secondary
            if p.isSynthesised then
              ctorsOf(program, cd.body).find(valueParams(program, _).isEmpty)
                .flatMap(n => effects(n.symbol, Nil, 0)).map(_.stats)
            // promoted paramful: no nilary secondary to reach
            else if p.primaryParams.nonEmpty then scala.None
            else
              val up = p.primary.flatMap(superApply) match
                case Some((m, args)) => effectsOf(m, args, 0)
                case scala.None =>
                  parentSyms(cd).foldLeft(Option(List.empty[Statement])) { (acc, s) =>
                    acc.zip(prologueOf(s, depth + 1)).map(_ ++ _)
                  }
              up.map(_ ++ p.primaryBody)

    /** the field a top-level statement assigns, when it is a plain `this.f = <e>` / `f = <e>`. */
    private def assignedField(st: Statement): Option[SymId] = st match
      case Tree.Commented(_, s)                                 => assignedField(s)
      case Tree.Assign(Tree.Ident(f, _, _), _, _, _, _)            => Some(f)
      case Tree.Assign(Tree.Select(_: Tree.This, f, _, _), _, _, _, _) => Some(f)
      case _                                                    => scala.None

    /** Over-estimate: every field any path may write. `None` if the statement is not a field
      * write, branch, block or no-op. Used on the PROLOGUE side of [[supersedes]]. */
    private def mayAssign(st: Statement): Option[Set[SymId]] = st match
      case Tree.Commented(_, s)          => mayAssign(s)
      case Tree.If(_, t, e, _, _)        => mayAssign(t).zip(mayAssign(e)).map(_ ++ _)
      case Tree.Block(stats, expr, _, _, _) =>
        (stats :+ expr).foldLeft(Option(Set.empty[SymId]))((acc, s) => acc.zip(mayAssign(s)).map(_ ++ _))
      case Tree.Literal(Constant.UnitC, _, _) => Some(Set.empty)
      case _                             => assignedField(st).map(Set(_))

    /** Under-estimate: fields written on EVERY path. `if` uses intersection, sequence uses union. */
    private def mustAssign(st: Statement): Set[SymId] = st match
      case Tree.Commented(_, s)          => mustAssign(s)
      case Tree.If(_, t, e, _, _)        => mustAssign(t).intersect(mustAssign(e))
      case Tree.Block(stats, expr, _, _, _) => (stats :+ expr).flatMap(mustAssign).toSet
      case _                             => assignedField(st).toSet

    /** True when `stats` overwrites every field the prologue may have written. Prologue read
      * through [[mayAssign]] (over-estimate), replay through [[mustAssign]] (under-estimate).
      * Compares targets only, not RHS. // ENGINE-LIMITS C6 */
    private def supersedes(stats: List[Statement], prologue: List[Statement]): Boolean =
      if prologue.isEmpty then true
      else
        val set = stats.flatMap(mustAssign).toSet
        prologue.forall(st => mayAssign(st).exists(fs => fs.nonEmpty && fs.forall(set.contains)))

    /** how many times each symbol is referenced by these statements. */
    private def useCounts(stats: List[Statement]): Map[SymId, Int] =
      given Program = program
      val acc = collection.mutable.Map[SymId, Int]().withDefaultValue(0)
      val ph = new Phase:
        def name = "ctor-replay-uses"
        override def transformIdent(t: Tree.Ident)(using Program): Term = { acc(t.sym) += 1; t }
      stats.foreach(StandardTraversal.mapStat(ph, _))
      acc.toMap

    /** true when some construct here could execute a sub-expression more than once. */
    private def repeats(stats: List[Statement]): Boolean =
      given Program = program
      var found = false
      val ph = new Phase:
        def name = "ctor-replay-loops"
        override def transformTerm(t: Term)(using Program): Term =
          t match
            case _: Tree.While | _: Tree.DoWhile | _: Tree.For | _: Tree.ForEach | _: Tree.Lambda => found = true
            case _ => ()
          t
      stats.foreach(StandardTraversal.mapStat(ph, _))
      found

    /** Parent type parameter substitution from the `extends` clause. Delegates to [[ParentSubst]]. */
    private def parentTypeSubst(cd: Tree.ClassDef): Map[SymId, TypeRepr] =
      given Program = program
      ParentSubst.of(cd)

    /** rewrite every TYPE in these statements through `m`. */
    private def retyped(stats: List[Statement], m: Map[SymId, TypeRepr]): List[Statement] =
      if m.isEmpty then stats
      else
        given Program = program
        val ph = new Phase:
          def name = "ctor-replay-retype"
          override def transformType(t: TypeRepr)(using Program): TypeRepr = ParentSubst.subst(t, m)
        stats.map(StandardTraversal.mapStat(ph, _))

    /** Ascribe a `null` argument to the formal's type (scala's `null` is `Null`, which has no
      * members). Skipped when the formal type mentions a constructor-scoped type parameter. */
    private def nullAtFormal(ctor: SymId, p: Tree.ValDef, a: Term): Term = a match
      case lit @ Tree.Literal(Constant.NullC, _, o)
        if p.tpt.tpe != TypeRepr.NoType && !ctorScoped(ctor, p.tpt.tpe) && !statesNull(p.tpt.tpe) =>
        Tree.Typed(lit, p.tpt, p.tpt.tpe, o)
      case _ => a

    /** True for `T | scala.Null` — its default is already stated. */
    private def statesNull(t: TypeRepr): Boolean = t match
      case TypeRepr.OrType(_, TypeRepr.TypeRef(_, s)) => program.symbolOf(s).exists(_.fullName == "scala.Null")
      case TypeRepr.OrType(TypeRepr.TypeRef(_, s), _) => program.symbolOf(s).exists(_.fullName == "scala.Null")
      case _                                          => false

    /** True if the type mentions a constructor-owned type parameter. */
    private def ctorScoped(ctor: SymId, t: TypeRepr): Boolean =
      given Program = program
      var hit = false
      val ph = new Phase:
        def name = "ctor-replay-null-scope"
        override def transformType(x: TypeRepr)(using Program): TypeRepr =
          x match
            case TypeRepr.TypeRef(_, s) if program.symbolOf(s).exists(_.owner == ctor) => hit = true
            case _                                                                     => ()
          x
      StandardTraversal.mapType(ph, t)
      hit

    private def substituted(stats: List[Statement], m: Map[SymId, Term]): List[Statement] =
      if m.isEmpty then stats
      else
        given Program = program
        val ph = new Phase:
          def name = "ctor-replay-subst"
          override def transformIdent(t: Tree.Ident)(using Program): Term = m.getOrElse(t.sym, t)
        stats.map(StandardTraversal.mapStat(ph, _))

    /** Statements a constructor runs (delegation chain inlined), with the class deferred to. */
    private final case class Effects(stats: List[Statement], deferredTo: Option[SymId])

    private def effectsOf(ctor: SymId, args: List[Term], depth: Int): Option[List[Statement]] =
      effects(ctor, args, depth).map(_.stats)

    private def effects(ctor: SymId, args: List[Term], depth: Int): Option[Effects] =
      if depth > 6 then scala.None
      else
        defOf(ctor).flatMap { d =>
          val ps   = valueParams(program, d)
          val stms = stmtsOf(d)
          // argument is safe to inline when simple or used exactly once in a loop-free body
          lazy val counts = useCounts(stms)
          lazy val loopFree = !repeats(stms)
          def ok(p: Tree.ValDef, a: Term) = simple(a) || (loopFree && counts.getOrElse(p.symbol, 0) == 1)
          if ps.length != args.length || !ps.zip(args).forall(ok) then scala.None
          else
            val body = substituted(stms, ps.zip(args).map((p, a) => p.symbol -> nullAtFormal(ctor, p, a)).toMap)
            body match
              case Tree.Apply(Tree.Select(_, m, _, _), as, _, _, _) :: tl if isInitName(program, m) =>
                if as.isEmpty then Some(Effects(tl, program.symbolOf(m).map(_.owner)))
                else effects(m, as, depth + 1).map(e => Effects(e.stats ++ tl, e.deferredTo))
              case all => Some(Effects(all, scala.None))
        }

    /** can these statements legally run one level down, in `cd`'s constructor? */
    private def usable(cd: Tree.ClassDef, d: Tree.DefDef, stats: List[Statement], touched: collection.mutable.Set[SymId]): Boolean =
      // empty replay is valid: the nilary construction the emitted `this()` already performed
      if stats.isEmpty then true
      else
        given Program = program
        var ok = true
        val ph = new Phase:
          def name = "ctor-replay-scan"
          override def transformTerm(t: Term)(using Program): Term =
            t match
              // `super.m()` dispatches too high; `return` leaves the wrong frame
              case _: Tree.Super  => ok = false
              case _: Tree.Return => ok = false
              case _              => ()
            program.symbolIn(t).foreach(touched += _)
            t
        stats.foreach(StandardTraversal.mapStat(ph, _))
        // a replayed local must not collide with a parameter of the constructor it lands in
        val paramNames = d.paramss.flatten.flatMap(v => program.symbolOf(v.symbol).map(_.name)).toSet
        val localNames = stats.collect { case v: Tree.ValDef => program.symbolOf(v.symbol).map(_.name).getOrElse("") }
        ok && localNames.forall(n => !paramNames(n)) &&
          // private members of other classes need widening or are unreachable
          touched.forall { s =>
            program.symbolOf(s).forall { sy =>
              !sy.flags.isPrivate || sy.owner == cd.symbol || reachablePrivate(cd, s, sy)
            }
          }

    /** Can this replay reach a private member across a module boundary? Asks the Surface: owned
      * members are widened locally; published non-private members are reachable; otherwise refused
      * and reported as a non-fatal gap. // ENGINE-LIMITS D5 */
    private def reachablePrivate(cd: Tree.ClassDef, s: SymId, sy: Symbol): Boolean =
      if surface.owns(sy.owner) then classOfSym(sy.owner).isDefined
      else
        // only report gaps for classes this run emits (D2)
        def report(g: Surface.Gap): Unit = if surface.owns(cd.symbol) then surface.gap(g)
        val who = s"${sy.fullName} (replayed into ${program.symbolOf(cd.symbol).map(_.fullName).getOrElse("?")})"
        surface.memberShape(s) match
          // cannot happen: `surface.owns(sy.owner)` above is the complement of this
          case Surface.Answer.Own => classOfSym(sy.owner).isDefined
          case Surface.Answer.Published(shape, module) =>
            if shape.vis == "public" || shape.vis == "protected" then true
            else
              report(Surface.Gap(who,
                s"$module emitted this member `${shape.vis}` and this run does not write its " +
                  "declaration, so the constructor replay that reaches it cannot be widened; the " +
                  "replay is refused and the `super(args)` it expressed is counted as an omission",
                Some(module), fatal = false,
                fix = s"§1(a) ENGINE, in the BASE: only $module can widen a member it emits, and it " +
                  "cannot know a future dependent will replay one. The divergence is counted by " +
                  "`omissions`; hand-write the constructor in this module if the behaviour is needed"))
              false
          case Surface.Answer.Unknown(why, module) =>
            report(Surface.Gap(who, why + " — the constructor replay that reaches it is refused, " +
              "because a replay across a module boundary cannot widen anything",
              module, fatal = false,
              fix = "§1(b) PER-LIBRARY: declare the module that emits this member as a base " +
                "(`base = \"…\"`) and re-run it with this engine so its port map carries a `vis=` row"))
            false

  /** True for terms safe to evaluate more than once (idents, literals, this, selections, typed). */
  private def simple(t: Term): Boolean = t match
    case _: Tree.Ident | _: Tree.Literal | _: Tree.This => true
    case Tree.Select(q, _, _, _)                        => simple(q)
    case Tree.Typed(e, _, _, _)                         => simple(e)
    case Tree.ArrayLength(a, _, _)                      => simple(a)
    case _                                              => false

  /** True for classes extending a JDK throwable (fixed constructor set). */
  private def jdkThrowableParent(program: Program, cd: Tree.ClassDef): Boolean =
    cd.parents.headOption.flatMap {
      case tt: TypeTree => headName(program, tt.tpe)
      case t: Term      => headName(program, t.tpe)
    }.exists(n => n == "java.lang.Throwable" ||
                  (n.startsWith("java.") && (n.endsWith("Exception") || n.endsWith("Error"))))

  /** A constructor's declared formal types (from the class file, not from a call's arguments). */
  private def formalsOf(program: Program, target: SymId): List[TypeRepr] =
    program.symbolOf(target).map(_.info).collect {
      case TypeRepr.MethodType(ps, _, _)                       => ps.map(_._2)
      case TypeRepr.PolyType(_, TypeRepr.MethodType(ps, _, _)) => ps.map(_._2)
    }.getOrElse(Nil)

  /** Constructor type parameters (JLS 8.8.4) of `targets` → use-site wildcards at their upper bound
    * (`T extends Texture` → `? <: Texture`), bounds first substituted through `classSubst`; a child
    * primary cannot declare method-level type parameters. G25, card 4e. */
  private def ctorTypeParamSubst(program: Program, targets: List[SymId],
                                  classSubst: Map[SymId, TypeRepr]): Map[SymId, TypeRepr] =
    targets.flatMap { target =>
      program.definitionOf(target).collect { case d: Tree.DefDef => d }.toList.flatMap { d =>
        d.tparams.map { tp =>
          val bounds = program.symbolOf(tp.symbol).map(_.info).collect {
            case TypeRepr.TypeBounds(lo, hi) => TypeRepr.TypeBounds(
              ParentSubst.subst(lo, classSubst), ParentSubst.subst(hi, classSubst))
          }.getOrElse(TypeRepr.AnyBounds)
          tp.symbol -> bounds
        }
      }
    }.toMap

  /** Classify which of the JDK's four throwable constructors was reached. Read off the target's
    * formals, not the argument types. `None` if outside the four. */
  private enum ThrowableCtor:
    case Nilary, Message, Cause, Both

  private def throwableCtor(program: Program, target: SymId, args: List[Term]): Option[ThrowableCtor] =
    def is(t: TypeRepr, n: String) = headName(program, t).contains(n)
    // empty args = nilary overload
    if args.isEmpty then Some(ThrowableCtor.Nilary)
    else formalsOf(program, target) match
      case f :: Nil if is(f, "java.lang.String")    => Some(ThrowableCtor.Message)
      case f :: Nil if is(f, "java.lang.Throwable") => Some(ThrowableCtor.Cause)
      case a :: b :: Nil if is(a, "java.lang.String") && is(b, "java.lang.Throwable") =>
        Some(ThrowableCtor.Both)
      case _ => scala.None

  /** Pad each root's super args to the JDK throwable's widest `(String, Throwable)` overload.
    * Returns `(widest ctor symbol, padded args per root)`. `None` if any root is outside the
    * four overloads or the widest was never called. // ENGINE-LIMITS C3 */
  private def throwablePadding(program: Program, roots: List[Tree.DefDef])
      : Option[(SymId, Map[SymId, List[Term]])] =
    val classified = roots.map { r =>
      val args = superArgsOf(program, r)
      (r, args, throwableCtor(program, superTarget(program, r), args))
    }
    val widest = classified.collectFirst { case (r, _, Some(ThrowableCtor.Both)) => superTarget(program, r) }
    widest.flatMap { target =>
      val formals = formalsOf(program, target)
      def nullAt(t: TypeRepr, o: Origin): Term =
        Tree.Typed(Tree.Literal(Constant.NullC, t, o), TypeTree(t, o), t, o)
      def pad(r: Tree.DefDef, args: List[Term], k: ThrowableCtor): Option[List[Term]] =
        val o = r.origin
        k match
          case ThrowableCtor.Both    => Some(args)
          case ThrowableCtor.Message => Some(List(args.head, nullAt(formals(1), o)))
          case ThrowableCtor.Nilary  => Some(List(nullAt(formals.head, o), nullAt(formals(1), o)))
          case ThrowableCtor.Cause   =>
            Option.when(simple(args.head))(List(
              Tree.Opaque.spliced(List("java.util.Objects.toString(", ", null)"),
                                  List(args.head), formals.head, o),
              args.head))
      val padded = classified.map { (r, args, k) => k.flatMap(pad(r, args, _)).map(r.symbol -> _) }
      Option.when(formals.sizeIs == 2 && padded.forall(_.isDefined))(target -> padded.flatten.toMap)
    }

  private def parentSyms(cd: Tree.ClassDef): List[SymId] =
    def head(t: TypeRepr): Option[SymId] = t match
      case TypeRepr.TypeRef(_, s)      => Some(s)
      case TypeRepr.AppliedType(tc, _) => head(tc)
      case _                           => scala.None
    cd.parents.flatMap { case tt: TypeTree => head(tt.tpe); case t: Term => head(t.tpe) }

  /** Constructor body as a statement list, seen through `Tree.Commented` wrappers. A comment on
    * the body's first statement is re-attached, never discarded. */
  def stmtsOf(d: Tree.DefDef): List[Statement] =
    def stats(t: Term): List[Statement] = t match
      case b: Tree.Block => b.stats
      case other         => List(other)
    d.rhs match
      case None                    => Nil
      case Some(c: Tree.Commented) => stats(Tree.uncomment(c)) match
        case (t: Term) :: rest => Tree.Commented(Tree.triviaOn(c), t) :: rest
        case other             => other
      case Some(t)                 => stats(t)

  /** Trailing comments of the body (not carried by [[stmtsOf]]). */
  def trailingOf(d: Tree.DefDef): List[Trivia] = d.rhs.map(Tree.uncomment) match
    case Some(b: Tree.Block) => b.trailing
    case _                   => Nil

  /** First statement of a constructor body, seen through `Tree.Commented`. */
  def headStmt(d: Tree.DefDef): Option[Statement] = stmtsOf(d).headOption.map {
    case t: Term => Tree.uncomment(t)
    case other   => other
  }

  /** Constructor body minus the leading delegation. */
  def bodyAfterDelegation(program: Program, d: Tree.DefDef): List[Statement] =
    val all = stmtsOf(d)
    headStmt(d) match
      case Some(Tree.Apply(Tree.Select(_, m, _, _), _, _, _, _)) if isInitName(program, m) => all.tail
      case _                                                                               => all

  def isCtor(program: Program, d: Tree.DefDef): Boolean =
    program.symbolOf(d.symbol).exists(_.name == "<init>")

  def ctorsOf(program: Program, body: List[Statement]): List[Tree.DefDef] =
    body.collect { case d: Tree.DefDef if isCtor(program, d) => d }

  // ---- enum primary: java's ROOT constructor, shared with OmissionCheck ----

  /** The enum's root constructor. `None` for multiple roots or none (counted refusal). */
  def enumPrimaryCtor(program: Program, cd: Tree.ClassDef): Option[Tree.DefDef] =
    val ctors = ctorsOf(program, cd.body)
    if ctors.sizeIs <= 1 then ctors.headOption
    else ctors.filterNot(delegatesToThis(program, _)) match
      case one :: Nil => Some(one)
      case _          => scala.None

  /** Fields superseded by the root constructor's parameters, matched on name AND type. */
  def enumSupersededFields(program: Program, cd: Tree.ClassDef): Set[SymId] =
    enumSupersededBy(program, cd).values.toSet

  /** Parameter-to-field pairing behind [[enumSupersededFields]]. */
  def enumSupersededBy(program: Program, cd: Tree.ClassDef): Map[SymId, SymId] =
    val params = enumPrimaryCtor(program, cd).map(valueParams(program, _)).getOrElse(Nil)
    if params.isEmpty then Map.empty
    else
      def nameOf(id: SymId): Option[String] = program.symbolOf(id).map(_.name)
      val byName: Map[String, (SymId, TypeRepr)] =
        params.flatMap(v => nameOf(v.symbol).map(_ -> (v.symbol, v.tpt.tpe))).toMap
      cd.body.collect {
        case v: Tree.ValDef
          if nameOf(v.symbol).flatMap(byName.get).exists((_, t) => t == v.tpt.tpe) =>
          nameOf(v.symbol).flatMap(byName.get).get._1 -> v.symbol
      }.toMap

  /** Resolve a constant's arguments to the root constructor, following delegations.
    * `None` when the overload is ambiguous or the delegation does more than delegate. */
  def enumConstantArgs(program: Program, cd: Tree.ClassDef, args: List[Term],
                       fuel: Int = 8): Option[List[Term]] =
    val ctors = ctorsOf(program, cd.body)
    if ctors.sizeIs <= 1 then Some(args)
    else if fuel <= 0 then scala.None
    else ctors.filter(c => valueParams(program, c).sizeIs == args.size) match
      case c :: Nil if !delegatesToThis(program, c) => Some(args)
      case c :: Nil =>
        val ps = valueParams(program, c).map(_.symbol).toSet
        stmtsOf(c) match
          case (t: Term) :: Nil => Tree.uncomment(t) match
            case Tree.Apply(Tree.Select(_, m, _, _), das, _, _, _)
              if isInitName(program, m) && !das.exists(mentions(_, ps)) =>
              enumConstantArgs(program, cd, das, fuel - 1)
            case _ => scala.None
          case _ => scala.None
      case _ => scala.None

  /** True if any symbol in `ss` appears in the tree. */
  private def mentions(t: Any, ss: Set[SymId]): Boolean = t match
    case Tree.Ident(s, _, _) => ss(s)
    case xs: Iterable[?]     => xs.exists(mentions(_, ss))
    case Some(x)             => mentions(x, ss)
    case p: Product          => p.productIterator.exists(mentions(_, ss))
    case _                   => false

  /** True if the constructor delegates to a peer with arguments (not super, not nilary). */
  def delegatesToThis(program: Program, d: Tree.DefDef): Boolean = headStmt(d).exists {
    case Tree.Apply(Tree.Select(r, m, _, _), args, _, _, _) =>
      isInitName(program, m) && !r.isInstanceOf[Tree.Super] && args.nonEmpty
    case _ => false
  }

  private def isInitName(program: Program, m: SymId): Boolean =
    program.symbolOf(m).exists(_.name == "<init>")

  /** `Some(args)` when a nilary constructor's body is only delegation(s); `None` if paramful or
    * if the body does anything else. Nilary = value params only (ignores `using` clauses). */
  def delegationOnlyNilary(program: Program, d: Tree.DefDef): Option[List[Term]] =
    if valueParams(program, d).nonEmpty then scala.None
    else
      val delegations = stmtsOf(d).map {
        case t: Term => Tree.uncomment(t) match
          case Tree.Apply(Tree.Select(_, m, _, _), as, _, _, _) if isInitName(program, m) => Some(as)
          case _                                                                          => scala.None
        case _ => scala.None
      }
      // empty list is vacuously all-delegation: `Some(Nil)` = scala's implicit primary
      if delegations.forall(_.isDefined) then Some(delegations.flatten.flatten) else scala.None

  // ---- java's parameters vs the pipeline's (using clauses) // ENGINE-LIMITS CT4 ----

  /** the TRAILING `using` clauses of a constructor — what a phase added, never what java wrote. */
  def givenClauses(program: Program, d: Tree.DefDef): List[List[Tree.ValDef]] =
    d.paramss.reverse
      .takeWhile(ps => ps.nonEmpty && ps.forall(v => program.symbolOf(v.symbol).exists(_.flags.isGiven)))
      .reverse

  /** the VALUE parameters — the only thing "is this constructor nilary" can mean. */
  def valueParams(program: Program, d: Tree.DefDef): List[Tree.ValDef] =
    d.paramss.dropRight(givenClauses(program, d).size).flatten

  /** The class's uniform context clause, or `Nil` if none, disagreeing, or trait/module/enum.
    * // ENGINE-LIMITS CT5 */
  def classGivens(program: Program, cd: Tree.ClassDef): List[List[Tree.ValDef]] =
    if program.symbolOf(cd.symbol).exists(x => x.flags.isModule || x.flags.isTrait || x.flags.isEnum)
    then Nil
    else
      val ctors = ctorsOf(program, cd.body)
      val cs    = ctors.map(givenClauses(program, _))
      if ctors.isEmpty || cs.exists(_.isEmpty) then Nil
      else if cs.map(_.map(_.map(_.tpt.tpe))).distinct.sizeIs != 1 then Nil
      else cs.head

  /** True if any constructor of this class carries a context clause. */
  def ctorsCarryGivens(program: Program, cd: Tree.ClassDef): Boolean =
    ctorsOf(program, cd.body).exists(givenClauses(program, _).nonEmpty)

  /** a promoted plan, with the constructor's own context clause carried onto the primary. */
  private def promoted(program: Program, c: Tree.DefDef, sa: List[Term], rest: List[Statement]): Plan =
    Plan(Some(c), sa, rest, givens = givenClauses(program, c))

  // ---- promotion cost, parameterised on (primary, primaryBody) for use before commit ----

  /** True if `d` reaches `target` through its delegation chain (any arity). */
  def reachesCtor(program: Program, d: Tree.DefDef, target: SymId, depth: Int = 0): Boolean =
    // through `headStmt` to see through `Tree.Commented` wrappers
    d.symbol == target || (depth <= 8 && (headStmt(d) match
      case Some(Tree.Apply(Tree.Select(r, m, _, _), _, _, _, _))
          if !r.isInstanceOf[Tree.Super] && isInitName(program, m) =>
        program.definitionOf(m).collect { case x: Tree.DefDef => x }
          .exists(reachesCtor(program, _, target, depth + 1))
      case _ => false))

  /** the constructors on whose path java did not run `primary`'s body — before the prefix strip. */
  def escapingRootsOf(program: Program, cd: Tree.ClassDef,
                      primary: Option[Tree.DefDef], primaryBody: List[Statement]): List[Tree.DefDef] =
    primary match
      case Some(c) if primaryBody.nonEmpty =>
        ctorsOf(program, cd.body).filterNot(reachesCtor(program, _, c.symbol))
      case _ => Nil

  /** Escaping root's body after stripping the promoted body prefix. Canonical rendering. */
  def residualBodyOf(program: Program, cd: Tree.ClassDef, primary: Option[Tree.DefDef],
                     primaryBody: List[Statement], d: Tree.DefDef): Option[List[Statement]] =
    if primaryBody.isEmpty || !escapingRootsOf(program, cd, primary, primaryBody).exists(_.symbol == d.symbol)
    then scala.None
    else
      given Program = program
      val rest = bodyAfterDelegation(program, d)
      def canon(s: Statement): String = TirPrinter.render(s, TirPrinter.Style.canonical)
      if primaryBody.sizeIs <= rest.size && primaryBody.zip(rest).forall((a, b) => canon(a) == canon(b))
      then Some(rest.drop(primaryBody.size))
      else scala.None

  /** the paths this promotion would still duplicate — escaping roots minus the ones the prefix
    * strip repairs. Empty means the promotion costs nothing C7 counts. */
  def escapesOf(program: Program, cd: Tree.ClassDef,
                primary: Option[Tree.DefDef], primaryBody: List[Statement]): List[Tree.DefDef] =
    escapingRootsOf(program, cd, primary, primaryBody)
      .filter(d => residualBodyOf(program, cd, primary, primaryBody, d).isEmpty)

  /** Erased head name for erasure-clash detection. Class type params erase to `Object`. */
  private def erasedName(program: Program, cd: Tree.ClassDef, t: TypeRepr): String =
    val tparams = cd.tparams.map(_.symbol).toSet
    def head(x: TypeRepr): Option[SymId] = x match
      case TypeRepr.TypeRef(_, s)      => Some(s)
      case TypeRepr.AppliedType(tc, _) => head(tc)
      case _                           => scala.None
    head(t) match
      case Some(s) if tparams(s) => "java.lang.Object"
      case Some(s)               => program.symbolOf(s).map(_.fullName).getOrElse("?")
      case scala.None            => "?"

  private def headName(program: Program, t: TypeRepr): Option[String] = t match
    case TypeRepr.TypeRef(_, s)      => program.symbolOf(s).map(_.fullName)
    case TypeRepr.AppliedType(tc, _) => headName(program, tc)
    case _                           => scala.None

  def superArgsOf(program: Program, d: Tree.DefDef): List[Term] = headStmt(d) match
    case Some(Tree.Apply(Tree.Select(_: Tree.Super, m, _, _), args, _, _, _)) if isInitName(program, m) => args
    case _                                                                                              => Nil

  /** Split into (super args, remaining body). Comments on the consumed head are carried forward. */
  private def split(program: Program, d: Tree.DefDef): (List[Term], List[Statement]) =
    val all = stmtsOf(d)
    def consumed(tl: List[Statement]): List[Statement] = all.headOption match
      case Some(h: Term) => carry(Tree.triviaOn(h), tl)
      case _             => tl
    all.headOption.map { case t: Term => Tree.uncomment(t); case other => other } match
      case Some(Tree.Apply(Tree.Select(_: Tree.Super, m, _, _), args, _, _, _)) if isInitName(program, m) =>
        (args, consumed(all.tail))
      // an explicit nilary `this()`/`super()` is what the implicit primary already does
      case Some(Tree.Apply(Tree.Select(_, m, _, _), args, _, _, _)) if isInitName(program, m) && args.isEmpty =>
        (Nil, consumed(all.tail))
      case _ => (Nil, all)

  /** prepend `ts` to whatever the first of `rest` already carries; `Nil` when nothing survives to
    * carry it (a constructor whose ONLY statement was the lifted `super(args)`). */
  private def carry(ts: List[Trivia], rest: List[Statement]): List[Statement] =
    if ts.isEmpty then rest
    else
      rest match
        case (h: Term) :: tl => Tree.Commented(ts ++ Tree.triviaOn(h), Tree.uncomment(h)) :: tl
        case other           => other

  /** Local nomination (ignoring whole-program constraints [[Plans]] applies).
    * @param synthesis `false` to get the plan without a synthesised primary (fallback).
    * @param parentPlanOf lookup for the parent's already-computed plan. // ENGINE-LIMITS C3 */
  def plan0(program: Program, cd: Tree.ClassDef, synthesis: Boolean = true,
            parentPlanOf: SymId => Option[Plan] = _ => scala.None): Plan =
    val s = program.symbolOf(cd.symbol)
    if s.exists(x => x.flags.isModule || x.flags.isTrait || x.flags.isEnum) then Plan.none
    else
      val ctors = ctorsOf(program, cd.body)
      val roots = ctors.filterNot(delegatesToThis(program, _))
      // generic constructors stay secondary; for multiple roots nominate the widest pass-through
      def throwableParent: Boolean = jdkThrowableParent(program, cd)
      def passesThrough(c: Tree.DefDef): Boolean =
        val ps = valueParams(program, c).map(_.symbol)
        superArgsOf(program, c).map { case Tree.Ident(x, _, _) => x; case _ => SymId.None } == ps && ps.nonEmpty
      val chosen = roots match
        case one :: Nil if one.tparams.isEmpty => Some(one)
        case several if throwableParent =>
          // JDK throwable only: padding is exact for this family, 0->55 elsewhere // ENGINE-LIMITS C3
          val widest = several.filter(c => c.tparams.isEmpty && passesThrough(c))
            .sortBy(c => -superArgsOf(program, c).size).headOption
          widest.filter(w => several.exists(o => (o ne w) && superArgsOf(program, o).nonEmpty))
            .orElse(several.find(c => valueParams(program, c).isEmpty && c.tparams.isEmpty))
        case several                           => several.find(c => valueParams(program, c).isEmpty && c.tparams.isEmpty)
      chosen match
        // unique root: cannot escape
        case Some(c) if roots.sizeIs == 1 =>
          val (sa, rest) = split(program, c); promoted(program, c, sa, rest)
        // several roots (non-throwable): try synthesis first
        case other if !throwableParent && synthesis => syntheticPrimary(program, cd, roots, parentPlanOf = parentPlanOf).getOrElse {
          other match
            case None    => Plan.none
            case Some(c) => val (sa, rest) = split(program, c); promoted(program, c, sa, rest)
        }
        // throwable parent with no nomination: try padded synthesis // ENGINE-LIMITS C3
        case None if throwableParent && synthesis =>
          syntheticPrimary(program, cd, roots, throwablePad = true, parentPlanOf = parentPlanOf).getOrElse(Plan.none)
        case None    => Plan.none
        case Some(c) => val (sa, rest) = split(program, c); promoted(program, c, sa, rest)

  /** Synthesise a primary from the parent constructor's formals. Refuses if roots diverge.
    * @param throwablePad allow padding for JDK throwable parents with diverging roots.
    * @param parentPlanOf lookup for the parent's already-computed plan. // ENGINE-LIMITS C3 */
  private def syntheticPrimary(program: Program, cd: Tree.ClassDef,
                               roots: List[Tree.DefDef],
                               throwablePad: Boolean = false,
                               parentPlanOf: SymId => Option[Plan] = _ => scala.None): Option[Plan] =
    val calls = roots.map(r => superTarget(program, r) -> superArgsOf(program, r))
    val targets = calls.map(_._1).distinct
    val arities = calls.map(_._2.size).distinct
    // admissibility: ONE parent constructor target, ONE arity, ONE context clause shape
    val rootGivens = roots.map(r => givenClauses(program, r))
    // super slots: uniform (same target/arity), resolved through parent, or padded (throwable)
    var resolvedResult: Option[ResolvedResult] = scala.None
    val slotArgs: Option[(SymId, Map[SymId, List[Term]])] =
      Option.when(targets.sizeIs == 1 && arities.sizeIs == 1)(
        targets.head -> roots.map(r => r.symbol -> superArgsOf(program, r)).toMap)
        // resolve through parent delegation chain when roots call different parent secondaries
        .orElse(Option.when(targets.sizeIs > 1 && !throwablePad)(
          resolvedThroughParent(program, cd, roots, calls, parentPlanOf)).flatten.map { rr =>
            resolvedResult = Some(rr)
            (rr.parentRoot, rr.argsMap)
          })
        .orElse(Option.when(throwablePad)(throwablePadding(program, roots)).flatten)
    if roots.sizeIs < 2 || roots.exists(_.tparams.nonEmpty) then scala.None
    else if slotArgs.isEmpty then scala.None
    else if rootGivens.map(_.map(_.map(_.tpt.tpe))).distinct.sizeIs != 1 then scala.None
    else
      val (target, superValues) = slotArgs.get
      // a PADDED plan is one whose roots do NOT all reach the same parent constructor, which is
      // what every question below that reads a root's own `super(args)` has to know.
      val padding = targets.sizeIs != 1 || arities.sizeIs != 1
      def passesThrough(c: Tree.DefDef): Boolean =
        val ps = valueParams(program, c).map(_.symbol)
        superArgsOf(program, c).map { case Tree.Ident(x, _, _) => x; case _ => SymId.None } == ps && ps.nonEmpty
      // formals from parent's signature, substituted through this class's instantiation.
      // G25: class type params first, then constructor type params as wildcards (card 4e).
      // C3: when resolved through the parent's plan, use the parent plan's slot types directly.
      val classSubst = ParentSubst.of(cd)(using program)
      val ctorSubst  = ctorTypeParamSubst(program, targets, classSubst)
      val formals = resolvedResult.flatMap(_.resolvedFormals).getOrElse(
        formalsOf(program, target).map(t =>
          ParentSubst.subst(ParentSubst.subst(t, classSubst), ctorSubst)))
      // collision test: a root whose params exactly match the formals
      val collides = roots.exists(valueParams(program, _).map(_.tpt.tpe) == formals)
      // also check APPLICABILITY: a narrower real ctor can shadow the synthesis // ENGINE-LIMITS C8
      val ctors = ctorsOf(program, cd.body)
      def ancestorOf(anc: SymId, s: SymId, fuel: Int): Boolean =
        s != SymId.None && fuel > 0 && (anc == s ||
          program.definitionOf(s).collect { case c: Tree.ClassDef => c }
            .exists(c => parentSyms(c).exists(ancestorOf(anc, _, fuel - 1))))
      def assignable(from: TypeRepr, to: TypeRepr): Boolean =
        val (f, t) = (headName(program, from), headName(program, to))
        // unknown types are assignable: refusing is the safe direction
        f.isEmpty || t.isEmpty || f == t || t.contains("java.lang.Object") ||
          headSym(from).zip(headSym(to)).exists((fs, ts) => ancestorOf(ts, fs, 16))
      def headSym(t: TypeRepr): Option[SymId] = t match
        case TypeRepr.TypeRef(_, s)      => Some(s)
        case TypeRepr.AppliedType(tc, _) => headSym(tc)
        case _                           => scala.None
      // marker name: free of all names in the class body
      def markerName: String =
        val taken = cd.body.collect { case d: Definition => program.symbolOf(d.symbol).map(_.name).getOrElse("") }.toSet
        var n = "Funnel"
        while taken(n) do n = n + "$"
        n
      // field slots change the signature, so derive them before collision/shadowing checks
      val (fs, values, consumedRuns, refusedFields) = fieldSlotsOf(program, cd, roots)
      val sup = formals.zipWithIndex.map((ft, k) => (s"sup$$$k", ft))
      // C3 item 4: post-body slots for the parent secondary's post-delegation body.
      // G25 + card 4e: apply both class and constructor type param substitutions.
      val pbSubst = classSubst ++ ctorSubst
      val (pbSlots, pbPostBody) = resolvedResult match
        case Some(rr) if rr.postBodyParams.nonEmpty =>
          given Program = program
          val typedSlots = rr.postBodyParams.map { (pv, _) =>
            val pName = program.symbolOf(pv.symbol).map(_.name).getOrElse("p")
            val slotType = ParentSubst.subst(pv.tpt.tpe, pbSubst)
            (s"${pName}$$", slotType)
          }
          // card 4e: if the first slot is value-typed, null cannot guard the post-body.
          // Prepend a boolean guard; the emitter reads `via$pb` as the boolean condition.
          val needsBoolGuard = typedSlots.headOption.exists((_, t) => javaDefault(program, t, cd.origin).exists {
            case Tree.Literal(Constant.NullC, _, _) => false; case _ => true
          })
          val boolSlot =
            if needsBoolGuard then
              val boolSym = program.symbols.all.find(_.fullName == "scala.Boolean").map(_.id).getOrElse(SymId.None)
              List(("via$pb", TypeRepr.TypeRef(TypeRepr.NoPrefix, boolSym)))
            else Nil
          val slots = boolSlot ++ typedSlots
          val paramSubst = rr.postBodyParams.zip(typedSlots).map { case ((pv, _), (slotName, slotTpe)) =>
            pv.symbol -> Tree.Opaque(slotName, slotTpe, cd.origin).asInstanceOf[Term]
          }.toMap
          val substPh = new Phase:
            def name = "ctor-postbody-subst"
            override def transformIdent(t: Tree.Ident)(using Program): Term = paramSubst.getOrElse(t.sym, t)
          val substBody = rr.rawPostBody.map(s => StandardTraversal.mapStat(substPh, s))
          val retypedBody = if pbSubst.isEmpty then substBody // G25
            else
              val retypePh = new Phase:
                def name = "ctor-postbody-retype"
                override def transformType(t: TypeRepr)(using Program): TypeRepr = ParentSubst.subst(t, pbSubst)
              substBody.map(StandardTraversal.mapStat(retypePh, _))
          (slots, retypedBody)
        case Some(rr) if rr.boolGuard =>
          // Param-less post-body: a boolean guard slot controls whether it runs.
          given Program = program
          val boolSym = program.symbols.all.find(_.fullName == "scala.Boolean").map(_.id).getOrElse(SymId.None)
          val boolType = TypeRepr.TypeRef(TypeRepr.NoPrefix, boolSym)
          val retypedBody = if pbSubst.isEmpty then rr.rawPostBody
            else
              val retypePh = new Phase:
                def name = "ctor-postbody-retype"
                override def transformType(t: TypeRepr)(using Program): TypeRepr = ParentSubst.subst(t, pbSubst)
              rr.rawPostBody.map(StandardTraversal.mapStat(retypePh, _))
          (List(("via$pb", boolType)), retypedBody)
        case _ => (Nil, Nil)
      val allSlots    = sup ++ pbSlots ++ fs.map(s => (s.name, s.tpe))
      // card 4e: detect if we added a boolean guard for value-typed post-body slots, so the
      // delegation values are prepended with the guard value.
      val pbHasBoolGuard = pbSlots.headOption.exists(_._1 == "via$pb") &&
        resolvedResult.exists(rr => !rr.boolGuard && rr.postBodyParams.nonEmpty)
      val delegations = roots.map { r =>
        val rawPbVals = resolvedResult.map(_.postBodyValues.getOrElse(r.symbol, Nil)).getOrElse(Nil)
        val pbVals =
          if pbHasBoolGuard then
            val contributes = resolvedResult.exists(_.rootsWithPostBody(r.symbol))
            val boolVal: Term = Tree.Literal(
              Constant.BoolC(contributes), pbSlots.head._2, cd.origin)
            boolVal :: rawPbVals
          else rawPbVals
        r.symbol -> (superValues(r.symbol) ++ pbVals ++ values.getOrElse(r.symbol, Nil))
      }.toMap
      // C3: per-java-ctor delegation for child resolution. Roots are directly from delegations;
      // non-roots follow their this(...) chain and compose substitutions.
      val ra = buildRootArgs(program, cd, roots, delegations)
      def synthesise(mark: Option[String]): Option[Plan] =
        val o = cd.origin
        Some(Plan(scala.None, sup.map((n, ft) => Tree.Opaque(n, ft, o)), Nil, synthetic = allSlots,
                  marker = mark, superSlots = sup.size, fieldSlots = fs,
                  delegations = delegations, consumed = consumedRuns, notSlot = refusedFields,
                  postBodySlots = pbSlots, primaryPostBody = pbPostBody,
                  // the roots agree (checked above), so any one of them names the clause the
                  // synthesised primary must carry for every secondary's `this(...)` to resolve.
                  givens = rootGivens.headOption.getOrElse(Nil),
                  rootArgs = ra))
      // is any real constructor applicable to the delegation args? // ENGINE-LIMITS C8
      val shadowed = delegations.values.exists { args =>
        ctors.exists { c =>
          val ps = valueParams(program, c)
          ps.sizeIs == args.size && ps.zip(args).forall((p, a) => assignable(a.tpe, p.tpt.tpe))
        }
      }
      // declaration-level erasure clash (E120) — separate from the applicability check above
      val erasedSlots  = allSlots.map((_, t) => erasedName(program, cd, t))
      val erasureClash = ctors.exists(c => valueParams(program, c).map(v => erasedName(program, cd, v.tpt.tpe)) == erasedSlots)
      // collapse: never under padding, only without field slots, only cost-free promotions
      val collapsed =
        if !padding && fs.isEmpty && collides && !roots.exists(valueParams(program, _).isEmpty)
        then collapseTo(program, cd, roots.filter(passesThrough))
        else scala.None
      // all roots must fill the same number of slots as there are formals
      val slotArity = superValues.values.map(_.size).toList.distinct
      if slotArity.sizeIs != 1 || !slotArity.contains(formals.size) ||
         formals.contains(TypeRepr.NoType) then scala.None
      // ordering: collapse > disambiguate > synthesise. Shapes are disjoint by design.
      else if collapsed.isDefined then collapsed
      // disambiguate with a marker parameter when erasure clashes or shadowed
      else if erasureClash || shadowed then synthesise(Some(markerName))
      // synthesise when there are slots; empty slots = Plan.none (context clause via hosting)
      else if allSlots.isEmpty then scala.None
      else synthesise(scala.None)

  /** Per-java-ctor delegation mapping for child resolution. Roots have entries from `delegations`;
    * non-root ctors follow `this(...)` chains and compose substitutions. // ENGINE-LIMITS C3 */
  private def buildRootArgs(program: Program, cd: Tree.ClassDef,
                             roots: List[Tree.DefDef],
                             delegations: Map[SymId, List[Term]]): Map[SymId, List[Term]] =
    val rootSet   = roots.map(_.symbol).toSet
    val ctors     = ctorsOf(program, cd.body)
    val rootPart  = delegations
    // non-root ctors: follow this(...) chain, substituting along the way. // ENGINE-LIMITS C3
    val nonRoots = ctors.filter(c => !rootSet(c.symbol))
    def derive(c: Tree.DefDef, depth: Int): Option[List[Term]] =
      if depth > 6 then scala.None
      else headStmt(c) match
        case Some(Tree.Apply(Tree.Select(r, m, _, _), as, _, _, _))
          if isInitName(program, m) && !r.isInstanceOf[Tree.Super] =>
          rootPart.get(m).orElse(
            ctors.find(_.symbol == m).flatMap(derive(_, depth + 1))
          ).flatMap { targetArgs =>
            val ps = valueParams(program, c)
            program.definitionOf(m).collect { case d: Tree.DefDef => d }.flatMap { td =>
              val targetPs = valueParams(program, td)
              if targetPs.length != as.length then scala.None
              else
                val subst = targetPs.zip(as).map((p, a) => p.symbol -> a).toMap
                given Program = program
                val substPh = new Phase:
                  def name = "ctor-rootargs-subst"
                  override def transformIdent(t: Tree.Ident)(using Program): Term = subst.getOrElse(t.sym, t)
                Some(targetArgs.map(a => StandardTraversal.mapStat(substPh, a).asInstanceOf[Term]))
            }
          }
        case _ => scala.None
    val nonRootPart = nonRoots.flatMap(c => derive(c, 0).map(c.symbol -> _)).toMap
    rootPart ++ nonRootPart

  /** Promote the widest pass-through root if no path escapes. Marked `collapse = true`. */
  private def collapseTo(program: Program, cd: Tree.ClassDef, candidates: List[Tree.DefDef]): Option[Plan] =
    candidates.sortBy(c => -valueParams(program, c).size).headOption
      .map { c => val (sa, rest) = split(program, c); promoted(program, c, sa, rest).copy(collapse = true) }
      .filter(p => escapesOf(program, cd, p.primary, p.primaryBody).isEmpty)

  // ---- FIELD SLOTS: a `this.f = e` hoisted into the primary's parameter list ----

  /** The field and value of a plain `this.f = e` / `f = e` assignment. */
  private def assignment(st: Statement): Option[(SymId, Term)] = st match
    case Tree.Commented(_, s)                                          => assignment(s)
    case Tree.Assign(Tree.Ident(f, _, _), rhs, _, _, _)                   => Some((f, rhs))
    case Tree.Assign(Tree.Select(_: Tree.This, f, _, _), rhs, _, _, _)    => Some((f, rhs))
    case _                                                             => scala.None

  /** True for scala's own operator symbols (always order-blind). */
  private def isOperator(program: Program, m: SymId): Boolean =
    program.symbolOf(m).exists(_.fullName.startsWith("scala.<op>#"))

  /** True when the expression is order-blind: only parameters, literals, and operator applications. */
  private def orderBlind(program: Program, params: Set[SymId], t: Term): Boolean = t match
    case Tree.Commented(_, s)         => orderBlind(program, params, s)
    case _: Tree.Literal              => true
    case Tree.Ident(s, _, _)          => params(s)
    case Tree.Typed(e, _, _, _)       => orderBlind(program, params, e)
    case Tree.Apply(Tree.Select(q, m, _, _), as, _, _, _) if isOperator(program, m) =>
      orderBlind(program, params, q) && as.forall(orderBlind(program, params, _))
    case _                            => false

  /** Java's default value for the type, or `None` for type variables. */
  private def javaDefault(program: Program, t: TypeRepr, o: Origin): Option[Term] =
    def lit(c: Constant) = Some(Tree.Literal(c, t, o): Term)
    t match
      case TypeRepr.TypeRef(_, s) =>
        program.symbolOf(s).map(_.fullName) match
          case Some("scala.Int")     => lit(Constant.IntC(0))
          case Some("scala.Short")   => lit(Constant.ShortC(0))
          case Some("scala.Byte")    => lit(Constant.ByteC(0))
          case Some("scala.Long")    => lit(Constant.LongC(0L))
          case Some("scala.Float")   => lit(Constant.FloatC(0f))
          case Some("scala.Double")  => lit(Constant.DoubleC(0d))
          case Some("scala.Boolean") => lit(Constant.BoolC(false))
          case Some("scala.Char")    => lit(Constant.CharC(' '))
          case Some("scala.Unit")    => scala.None
          // class type = null, but not for a type parameter (Null does not conform)
          case Some(_) if program.definitionOf(s).exists(_.isInstanceOf[Tree.TypeDef]) => scala.None
          case Some(_)               => lit(Constant.NullC)
          case scala.None            => scala.None
      case _: TypeRepr.AppliedType => lit(Constant.NullC)
      case _                       => scala.None

  /** how many times these statements WRITE `f` — an assignment or an increment, at any depth. */
  private def writeCount(program: Program, stats: List[Statement], f: SymId): Int =
    given Program = program
    var n = 0
    def targets(t: Term): Boolean = t match
      case Tree.Ident(s, _, _)                  => s == f
      case Tree.Select(_: Tree.This, s, _, _)   => s == f
      case Tree.Select(_, s, _, _)              => s == f
      case _                                    => false
    val ph = new Phase:
      def name = "ctor-field-writes"
      override def transformTerm(t: Term)(using Program): Term =
        t match
          case Tree.Assign(lhs, _, _, _, _) if targets(lhs) => n += 1
          case Tree.IncDec(tg, _, _, _, _) if targets(tg) => n += 1
          case _                                          => ()
        t
    stats.foreach(StandardTraversal.mapStat(ph, _))
    n

  /** does anything in these statements MENTION `f` at all? */
  private def mentions(program: Program, stats: List[Statement], f: SymId): Boolean =
    given Program = program
    var found = false
    val ph = new Phase:
      def name = "ctor-field-mentions"
      override def transformTerm(t: Term)(using Program): Term =
        program.symbolIn(t).foreach(s => if s == f then found = true)
        t
    stats.foreach(StandardTraversal.mapStat(ph, _))
    found

  /** Derive field slots: (slots, values per root, consumed count per root, refused with reason).
    * Gates: one leading assignment per root, order-blind values, no body dependency on the field.
    * `mutable` is left true here; [[Plans]] decides it. */
  private def fieldSlotsOf(program: Program, cd: Tree.ClassDef, roots: List[Tree.DefDef])
      : (List[FieldSlot], Map[SymId, List[Term]], Map[SymId, Int], List[(String, String)]) =
    val fields = cd.body.collect {
      case v: Tree.ValDef if !program.symbolOf(v.symbol).exists(_.flags.isStatic) => v
    }
    val byId = fields.map(v => v.symbol -> v).toMap
    def nameOf(s: SymId) = program.symbolOf(s).map(_.name).getOrElse("?")
    // field initialisers and init blocks: a field mentioned by either cannot become a slot
    val initSites: List[Statement] =
      fields.flatMap(_.rhs) ++ cd.body.collect {
        case d: Tree.DefDef if program.symbolOf(d.symbol).exists(_.name == "<initblock>") => stmtsOf(d)
      }.flatten
    // per root: the LEADING RUN of assignments to this class's own instance fields
    def leadingRun(d: Tree.DefDef): List[(SymId, Term)] =
      bodyAfterDelegation(program, d)
        .map(st => assignment(st).filter((f, _) => byId.contains(f)))
        .takeWhile(_.isDefined).flatten
    val runs = roots.map(d => d.symbol -> leadingRun(d)).toMap
    val bodies = roots.map(d => d.symbol -> bodyAfterDelegation(program, d)).toMap
    val params = roots.map(d => d.symbol -> d.paramss.flatten.map(_.symbol).toSet).toMap
    val refused = collection.mutable.LinkedHashMap[SymId, String]()
    // candidates in DECLARATION order, so the slot list reads like the class does
    var candidates = fields.map(_.symbol).filter(f => runs.values.exists(_.exists(_._1 == f)))
    def refuse(f: SymId, why: String): Unit =
      if !refused.contains(f) then refused(f) = why
      candidates = candidates.filterNot(_ == f)
    candidates.toList.foreach { f =>
      val v = byId(f)
      // fill for a root that doesn't assign: field initialiser (if order-blind) or java default
      val fill = v.rhs.map(r => Option.when(orderBlind(program, Set.empty, r))(r))
        .getOrElse(javaDefault(program, v.tpt.tpe, v.origin))
      if mentions(program, initSites, f) then refuse(f, "initialiser")
      else if fill.isEmpty && roots.exists(d => !runs(d.symbol).exists(_._1 == f)) then refuse(f, "no-default")
      else roots.foreach { d =>
        val inRun  = runs(d.symbol).count(_._1 == f)
        val inBody = writeCount(program, bodies(d.symbol), f)
        if inRun > 1 || inBody != inRun then refuse(f, "interleaved")
        else if inRun == 1 &&
                !orderBlind(program, params(d.symbol), runs(d.symbol).find(_._1 == f).get._2)
        then refuse(f, "order")
      }
    }
    // cascade: a field behind a refused one in any root's leading run is also refused
    var changed = true
    while changed do
      changed = false
      roots.foreach { d =>
        val run = runs(d.symbol)
        val ok  = run.map(_._1).takeWhile(candidates.contains)
        run.map(_._1).drop(ok.size).distinct.foreach { f =>
          if candidates.contains(f) then { refuse(f, "interleaved"); changed = true }
        }
      }
    val slots = candidates.map { f =>
      val v = byId(f)
      FieldSlot(f, s"f$$${nameOf(f)}", v.tpt.tpe, mutable = true,
                writes = roots.count(d => runs(d.symbol).exists(_._1 == f)))
    }
    val consumed = roots.map { d =>
      d.symbol -> runs(d.symbol).map(_._1).takeWhile(candidates.contains).size
    }.toMap
    val values = roots.map { d =>
      d.symbol -> slots.map { s =>
        val v = byId(s.field)
        runs(d.symbol).find(_._1 == s.field).map(_._2)
          .orElse(v.rhs.filter(orderBlind(program, Set.empty, _)))
          .orElse(javaDefault(program, s.tpe, v.origin))
          .getOrElse(sys.error(s"field slot without a value: ${nameOf(s.field)}"))
      }
    }.toMap
    (slots, values, consumed, refused.toList.map((f, why) => (nameOf(f), why)))

  private def superTarget(program: Program, d: Tree.DefDef): SymId = headStmt(d) match
    case Some(Tree.Apply(Tree.Select(_: Tree.Super, m, _, _), args, _, _, _))
        if args.nonEmpty && isInitName(program, m) => m
    case _ => SymId.None

  /** Result of [[resolvedThroughParent]]: the parent root, per-root effective args, per-root
    * post-body param values, and the shared post-body params and statements. */
  final case class ResolvedResult(
      parentRoot: SymId,
      argsMap: Map[SymId, List[Term]],
      /** Per-root values for the post-body params (in the order of [[postBodyParams]]).
        * Roots that target the parent root directly have empty lists. */
      postBodyValues: Map[SymId, List[Term]],
      /** The parent secondary's params that the post-body uses — `(paramDef, callerArg)`.
        * Source of the synthesised parameter names and types. */
      postBodyParams: List[(Tree.ValDef, Term)],
      /** The raw post-body statements (un-substituted for post-body params). */
      rawPostBody: List[Statement],
      /** True when the post-body uses no params and needs a boolean guard slot. */
      boolGuard: Boolean = false,
      /** Roots that went through a secondary with a post-body (card 4e: boolean guard). */
      rootsWithPostBody: Set[SymId] = Set.empty,
      /** Parent's synthesised primary's slot types, when resolved through a parent plan rather
        * than a unique java root. Overrides `formalsOf` in the caller. // ENGINE-LIMITS C3 */
      resolvedFormals: Option[List[TypeRepr]] = scala.None,
  )

  /** Resolve diverging roots through the parent's delegation chain, so they converge on the
    * parent's unique root. Returns the parent root, per-root effective args, and post-body info
    * for synthesised parameter creation. // ENGINE-LIMITS C3 item 4, C3 item 4c */
  private def resolvedThroughParent(
      program: Program, cd: Tree.ClassDef,
      roots: List[Tree.DefDef],
      calls: List[(SymId, List[Term])],
      parentPlanOf: SymId => Option[Plan] = _ => scala.None
  ): Option[ResolvedResult] =
    // find the PARENT class's ClassDef from the first parent in the extends clause
    val parentSym = parentSyms(cd).headOption.getOrElse(SymId.None)
    val parentCd  = program.definitionOf(parentSym).collect { case c: Tree.ClassDef => c }.getOrElse(return scala.None)
    val parentCtors = ctorsOf(program, parentCd.body)
    val parentRoots = parentCtors.filterNot(delegatesToThis(program, _))
    // multiple parent roots with a synthesised parent plan: resolve through the plan. // C3
    if parentRoots.sizeIs != 1 then
      return resolvedThroughParentPlan(program, cd, roots, calls, parentSym, parentCd, parentPlanOf)
    val parentRoot = parentRoots.head
    val parentRootSym = parentRoot.symbol

    // for each dependent root, compute the effective args that reach the parent root
    val resolved = roots.zip(calls).map { case (dependentRoot, (target, superArgs)) =>
      // is this target already the parent root?
      if target == parentRootSym then
        Some((dependentRoot.symbol, InlineResult(superArgs, Nil, Nil)))
      else
        // follow the parent's delegation chain: the target is a parent secondary
        // substitute the dependent root's super args into the parent secondary's body
        // and follow the `this(args)` delegation to find what reaches the parent root
        inlineDelegation(program, target, superArgs, parentRootSym, depth = 0)
          .map(result => (dependentRoot.symbol, result))
    }
    if resolved.exists(_.isEmpty) then scala.None
    else
      val flat = resolved.flatten
      val argsMap = flat.map((sym, r) => sym -> r.effectiveArgs).toMap
      // verify all roots now target the same parent constructor and same arity
      val effectiveArities = argsMap.values.map(_.size).toList.distinct
      if effectiveArities.sizeIs != 1 then scala.None
      else
        val allPbParams = flat.flatMap(_._2.postBodyParams)
        val seenPb = collection.mutable.Set[SymId]()
        val uniquePbParams = allPbParams.filter { (p, _) =>
          if seenPb(p.symbol) then false else { seenPb += p.symbol; true }
        }
        val rawPostBody = flat.map(_._2.postBody).find(_.nonEmpty).getOrElse(Nil)
        val needsBoolGuard = rawPostBody.nonEmpty && uniquePbParams.isEmpty
        val boolSym = SymId.None // placeholder for the boolean type ref
        val pbValues = flat.map { (sym, r) =>
          if needsBoolGuard then
            val v: Term = Tree.Literal(if r.postBody.nonEmpty then Constant.BoolC(true) else Constant.BoolC(false),
              TypeRepr.TypeRef(TypeRepr.NoPrefix, boolSym), cd.origin)
            sym -> List(v)
          else
            // card 4e: map against uniquePbParams, using actual values for params this root
            // contributes and JVM defaults for the rest (a root going through a 2-arg chain
            // does not contribute the 7-arg chain's params, but the slot list needs all).
            val contributed = r.postBodyParams.map((p, v) => p.symbol -> v).toMap
            sym -> uniquePbParams.map { (pv, _) =>
              contributed.getOrElse(pv.symbol,
                javaDefault(program, pv.tpt.tpe, cd.origin)
                  .getOrElse(Tree.Literal(Constant.NullC, TypeRepr.NoType, cd.origin): Term))
            }
        }.toMap
        // card 4e: track which roots went through a secondary that has a post-body.
        val withPb = flat.collect { case (sym, r) if r.postBody.nonEmpty => sym }.toSet
        Some(ResolvedResult(parentRootSym, argsMap, pbValues, uniquePbParams, rawPostBody,
          needsBoolGuard, withPb))

  /** Resolve through the parent's SYNTHESISED PLAN when the parent has 2+ java roots but an
    * already-computed synthesised primary. Substitutes the child's `super(args)` into the
    * parent plan's per-ctor delegation mapping. // ENGINE-LIMITS C3 */
  private def resolvedThroughParentPlan(
      program: Program, cd: Tree.ClassDef,
      roots: List[Tree.DefDef],
      calls: List[(SymId, List[Term])],
      parentSym: SymId,
      parentCd: Tree.ClassDef,
      parentPlanOf: SymId => Option[Plan]
  ): Option[ResolvedResult] =
    val pp = parentPlanOf(parentSym).getOrElse(return scala.None)
    if !pp.isSynthesised || pp.rootArgs.isEmpty then return scala.None
    val superSlotCount = pp.superSlots
    // resolve each child root through the parent plan's rootArgs mapping (super-slot portion only;
    // the parent's post-body and field slots are internal to the parent class). // C3
    val resolved = roots.zip(calls).map { case (childRoot, (target, superArgs)) =>
      pp.rootArgs.get(target).flatMap { parentDelegArgs =>
        val superDelegArgs = parentDelegArgs.take(superSlotCount)
        // substitute childRoot's super(args) for the target parent ctor's params
        program.definitionOf(target).collect { case d: Tree.DefDef => d }.flatMap { td =>
          val targetPs = valueParams(program, td)
          if targetPs.length != superArgs.length then scala.None
          else
            val subst = targetPs.zip(superArgs).map((p, a) => p.symbol -> a).toMap
            given Program = program
            val substPh = new Phase:
              def name = "ctor-parent-plan-subst"
              override def transformIdent(t: Tree.Ident)(using Program): Term =
                subst.getOrElse(t.sym, t)
            val effectiveArgs = superDelegArgs.map(a =>
              StandardTraversal.mapStat(substPh, a).asInstanceOf[Term])
            Some((childRoot.symbol, effectiveArgs))
        }
      }
    }
    if resolved.exists(_.isEmpty) then scala.None
    else
      val flat = resolved.flatten
      val argsMap = flat.toMap
      val effectiveArities = argsMap.values.map(_.size).toList.distinct
      if effectiveArities.sizeIs != 1 then scala.None
      else
        // the parent's synthesised primary's super-slot types become the child's formals
        val classSubst = ParentSubst.of(cd)(using program)
        val parentFormals = pp.synthetic.take(pp.superSlots).map { (_, t) =>
          ParentSubst.subst(t, classSubst)
        }
        // no post-body: the parent plan already handles its own post-body internally
        Some(ResolvedResult(SymId.None, argsMap, Map.empty, Nil, Nil,
          resolvedFormals = Some(parentFormals)))

  /** Effective args reaching the parent root, un-substituted post-body, and post-body param
    * dependencies for synthesised parameter creation. // ENGINE-LIMITS C3 */
  final case class InlineResult(
      effectiveArgs: List[Term],
      postBody: List[Statement],
      postBodyParams: List[(Tree.ValDef, Term)]
  )

  /** Follow `currentCtor`'s delegation chain to `targetRoot`. Post-body params are carried
    * through synthesised parameters (not inlined), so non-simple args are allowed there.
    * Refuses on `super.m()`, `return` in the post-body, or unsafe delegation substitution.
    * // ENGINE-LIMITS C3 */
  private def inlineDelegation(
      program: Program, currentCtor: SymId, callerArgs: List[Term],
      targetRoot: SymId, depth: Int
  ): Option[InlineResult] =
    if depth > 6 then scala.None
    else
      program.definitionOf(currentCtor).collect { case d: Tree.DefDef => d }.flatMap { d =>
        val ps   = valueParams(program, d)
        val stms = stmtsOf(d)
        // the post-delegation body: everything after the head `this(...)` delegation.
        val postBody = headStmt(d) match
          case Some(Tree.Apply(Tree.Select(r, m, _, _), _, _, _, _))
            if isInitName(program, m) && !r.isInstanceOf[Tree.Super] => stms.tail
          case _ => Nil
        val postBodyUsed: Set[SymId] =
          if postBody.isEmpty then Set.empty
          else
            val used = collection.mutable.Set[SymId]()
            val paramSet = ps.map(_.symbol).toSet
            given Program = program
            val ph = new Phase:
              def name = "ctor-inline-postbody-uses"
              override def transformIdent(t: Tree.Ident)(using Program): Term =
                if paramSet(t.sym) then used += t.sym
                t
            postBody.foreach(StandardTraversal.mapStat(ph, _))
            used.toSet
        // C3: count uses in the delegation head only, not the post-body.
        val headArgs = headStmt(d) match
          case Some(Tree.Apply(Tree.Select(_, m, _, _), as, _, _, _)) if isInitName(program, m) => as
          case _ => Nil
        lazy val delegCounts = headArgs.foldLeft(Map.empty[SymId, Int]) { (acc, a) =>
          var m = acc
          given Program = program
          val ph = new Phase:
            def name = "ctor-inline-count"
            override def transformIdent(t: Tree.Ident)(using Program): Term =
              m = m.updated(t.sym, m.getOrElse(t.sym, 0) + 1)
              t
          StandardTraversal.mapStat(ph, a)
          m
        }
        lazy val delegLoopFree = !headArgs.exists { a =>
          given Program = program
          var found = false
          val ph = new Phase:
            def name = "ctor-inline-loops"
            override def transformTerm(t: Term)(using Program): Term =
              t match
                case _: Tree.While | _: Tree.DoWhile | _: Tree.For | _: Tree.ForEach | _: Tree.Lambda => found = true
                case _ => ()
              t
          StandardTraversal.mapStat(ph, a)
          found
        }
        def ok(p: Tree.ValDef, a: Term) = simple(a) || (delegLoopFree && delegCounts.getOrElse(p.symbol, 0) <= 1)
        if ps.length != callerArgs.length || !ps.zip(callerArgs).forall(ok) then scala.None
        else
          val subst = ps.zip(callerArgs).map((p, a) => p.symbol -> a).toMap
          given Program = program
          val substPh = new Phase:
            def name = "ctor-inline-subst"
            override def transformIdent(t: Tree.Ident)(using Program): Term = subst.getOrElse(t.sym, t)
          // usability: refuse `super.m()` and `return` in the post-body
          if postBody.nonEmpty then
            var usable = true
            val scanPh = new Phase:
              def name = "ctor-inline-usable"
              override def transformTerm(t: Term)(using Program): Term =
                t match
                  case _: Tree.Super  => usable = false
                  case _: Tree.Return => usable = false
                  case _              => ()
                t
            postBody.foreach(StandardTraversal.mapStat(scanPh, _))
            if !usable then return scala.None
          val pbParams = ps.zip(callerArgs).filter((p, _) => postBodyUsed(p.symbol))
          // apply substitution to the delegation's arguments
          val head = headStmt(d)
          head match
            case Some(Tree.Apply(Tree.Select(_, m, _, _), as, _, _, _)) if isInitName(program, m) =>
              val substArgs = as.map(a => StandardTraversal.mapStat(substPh, a).asInstanceOf[Term])
              if m == targetRoot then Some(InlineResult(substArgs, postBody, pbParams))
              else inlineDelegation(program, m, substArgs, targetRoot, depth + 1)
                .map(inner => InlineResult(inner.effectiveArgs,
                  inner.postBody ++ postBody, inner.postBodyParams ++ pbParams))
            case _ => scala.None
      }
