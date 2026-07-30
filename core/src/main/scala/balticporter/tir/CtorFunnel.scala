package balticporter.tir

/** Which Java constructor becomes Scala's PRIMARY — the one decision that makes `super(args)`
  * expressible at all.
  *
  * Java lets every constructor pick its own `super(...)` overload. Scala lets only the PRIMARY
  * constructor reach `super`; auxiliaries must delegate to another constructor of the same class.
  * So a faithful lowering has to nominate one Java constructor as the primary, lift ITS super
  * arguments into the `extends` clause, and route every other constructor to it through
  * `this(...)`.
  *
  * The decision is shared between the emitter (which acts on it) and [[OmissionCheck]] (which
  * counts what the emitter must still drop), so the check can never drift from what is actually
  * emitted: improve the funnel here and both move together.
  *
  * SIX shapes are funnelled today, each nominated at its own site and documented there. This list
  * is the map, not the specification — it started as "two shapes" and stayed that way through four
  * additions, which is exactly how a header stops being readable as evidence:
  *
  *  1. UNIQUE ROOT — exactly one constructor does not delegate `this(args)`. It becomes the
  *     primary whatever its arity; its `super(args)` lands in `extends`, its parameters become
  *     class parameters, its body becomes class-body statements, and every other constructor is
  *     already a `this(...)` delegation, which Scala expresses verbatim. Nothing is lost.
  *     ([[plan0]])
  *  2. NO-ARG ROOT — several roots, one of them nilary. It becomes the primary (a `def this()`
  *     would clash with Scala's implicit primary anyway). The other roots' super arguments reach it
  *     through [[Plans.superCall]] where they can and stay counted by [[OmissionCheck]] where they
  *     cannot. ([[plan0]])
  *  3. WIDEST PASS-THROUGH ROOT, JDK THROWABLES ONLY — several roots reaching different overloads
  *     of a parent whose constructor set is FIXED and public. The widest root that passes its own
  *     parameters straight through is promoted and the narrower ones pad the slots the JDK's own
  *     narrower overload would have left. Exact only for that family; guessing elsewhere measured
  *     0 -> 55 compile errors. ([[plan0]], [[Plans.superCall]])
  *  4. SYNTHESISED PRIMARY — no java constructor can be the primary, but every root reaches the
  *     SAME parent constructor. A primary taking the PARENT's parameters is synthesised and every
  *     java constructor becomes a secondary computing its arguments. ([[syntheticPrimary]],
  *     [[Plan.synthetic]])
  *  5. SYNTHETIC-SHAPED ROOT — the same situation where one root ALREADY has the synthesised
  *     signature, so synthesising beside it would duplicate it. That root is promoted instead.
  *     ([[syntheticPrimary]])
  *  6. PROMOTED NILARY CONSTRUCTOR — no constructor carries `super(args)` at all, so the funnel is
  *     needed only to stop Scala's implicit nilary primary clashing with an emitted `def this()`.
  *     The nilary constructor is promoted with its `this(args)` delegation inlined.
  *     ([[Plans.nilaryPlan]])
  *
  * Two mechanisms sit ON TOP of the nomination rather than beside it: [[Plans]] WITHHOLDS a
  * paramful promotion wherever a subclass reaches the class with an argument-free `extends`, and
  * [[Plans.replayFor]] expresses a secondary's `super(args)` as the parent constructor's own
  * statements replayed after `this()` where that is provably equivalent.
  *
  * Anything else (several paramful roots reaching different parent overloads of a parent whose
  * constructor set the engine does not know, e.g. `DelayedRemovalArray`'s ten) has no
  * single-primary encoding and is reported, not approximated.
  */
object CtorFunnel:

  /** the plan for one class: which Java constructor is Scala's primary, the super arguments to
    * lift into the `extends` clause, and the leftover statements that become class body. */
  final case class Plan(
      primary: Option[Tree.DefDef],
      superArgs: List[Term],
      /** the promoted constructor's body minus its leading super/this delegation. */
      primaryBody: List[Statement],
      /** A SYNTHESISED primary, taking the PARENT constructor's parameters, when no java
        * constructor can be the primary but every root reaches the same parent constructor.
        *
        * The case this exists for: `AlgorithmPath()` calls `super(0, false)` and
        * `AlgorithmPath(Node v)` calls `super(v.getIndex() + 1, true)`. Neither can be scala's
        * primary — whichever is chosen, the other's `super(args)` has nowhere to go, and the
        * emitted class then constructs its parent with the WRONG arguments while compiling
        * perfectly. Measured: simple-graphs' shortest-path test returned a path of size 0 instead
        * of 39, and the only thing that noticed was the test.
        *
        * The faithful encoding is the one a scala author would write by hand — a private primary
        * that takes the super call's own parameters, with every java constructor a secondary that
        * computes its arguments and delegates:
        * {{{
        * class AlgorithmPath[V] private (n: Int, b: Boolean) extends Path[V](n, b):
        *   def this()          = this(0, false)
        *   def this(v: Node[V]) = { this(v.getIndex() + 1, true); setByBacktracking(v) }
        * }}}
        * Admissible only when every root reaches the SAME parent constructor: differing overloads
        * would need the null-padding that measured 0 -> 55 outside the JDK-throwable family. */
      synthetic: List[(String, TypeRepr)] = Nil,
  ):
    def primaryParams: List[Tree.ValDef] = primary.map(_.paramss.flatten).getOrElse(Nil)

  object Plan:
    val none: Plan = Plan(scala.None, Nil, Nil)

  /** How ONE secondary constructor's `super(args)` reaches the parent — the single decision the
    * emitter RENDERS and [[OmissionCheck]] COUNTS.
    *
    * It is per-ROOT and not per-class, and that distinction is the whole reason this type exists.
    * A class-wide "every root's super call survives" flag asserted by the planner is a PROMISE; the
    * emitter's delegation is a computation that can decline (an argument that finds no parameter of
    * its own type, an arity the primary cannot take). While the flag existed, a class the planner
    * marked expressed reported ZERO dropped super arguments even for the roots the emitter had just
    * lowered to a bare `this()` — the check hiding exactly the drop class it exists to count. One
    * function answers it now, so the two cannot disagree by construction. */
  enum SuperCall:
    /** `this(args)` — positional against a SYNTHESISED primary whose parameters ARE the parent
      * constructor's, in its order. No matching of any kind is needed or wanted here. */
    case Positional(args: List[Term])
    /** `this(...)` against a PROMOTED root: each of the primary's parameters takes an argument of
      * the java call, or the value the parent's own narrower overload would have left there. */
    case Matched(slots: List[Slot])
    /** `this()` — the arguments are LOST, and [[OmissionCheck]] reports them. */
    case Dropped

  /** one argument position of a [[SuperCall.Matched]] delegation. */
  enum Slot:
    /** an argument of the java `super(...)` call, at the parameter whose type it fits. */
    case Arg(term: Term)
    /** the `null` at this type that the parent's own NARROWER overload left in this position. */
    case NullAt(tpe: TypeRepr)
    /** The MESSAGE the JDK's `Throwable(Throwable)` constructor computes for itself.
      *
      * A padded slot is `null` because the narrower overload java actually called left it `null` —
      * true of every position except this one. `Throwable(Throwable cause)` is documented as
      * `this(cause == null ? null : cause.toString(), cause)`, so padding the message with `null`
      * builds a DIFFERENT exception: `new GdxRuntimeException(cause).getMessage` returned `null`
      * where java's returned `java.lang.IllegalStateException: boom`. It compiles, it moves no
      * count, and only a runtime probe sees it (CLAUDE.md §4.4).
      *
      * Rendered through `java.util.Objects.toString(cause, null)`, which is that expression exactly
      * and evaluates `cause` ONCE — so no purity condition on the argument is needed, and none is
      * imposed. */
    case CauseMessage(cause: Term)

  /** a padded slot needs a value that `null` can inhabit; a primitive has none. */
  val primitiveTypeNames: Set[String] =
    Set("scala.Int", "scala.Long", "scala.Float", "scala.Double",
        "scala.Short", "scala.Byte", "scala.Char", "scala.Boolean", "scala.Unit")

  /** Whole-program funnel decisions. Built once per `Program` because promoting a PARAMFUL
    * constructor to a class's primary is not a local choice: it removes that class's nilary
    * construction path, which every subclass whose own `extends` clause passes no arguments was
    * relying on. Such a promotion is withheld (and the class stays counted as an omission)
    * rather than emitted as code that cannot compile. */
  final class Plans(program: Program):
    private val classes: List[Tree.ClassDef] =
      def walk(cd: Tree.ClassDef): List[Tree.ClassDef] =
        cd :: cd.body.collect { case c: Tree.ClassDef => walk(c) }.flatten
      program.units.flatMap(walk)

    // Withholding one promotion can force another: a class whose own promotion is withheld stops
    // passing super arguments, and so joins the set demanding a nilary parent. Iterate to a
    // fixpoint — the step only ever REMOVES promotions, so it terminates.
    /** Promote the class's explicit NILARY constructor, inlining its `this(args)` delegation.
      *
      * `plan0` nominates nothing when a class has SEVERAL roots and none is nilary —
      * `RegionInfluencer` has `(int)` and `(TextureRegion...)`, and its no-arg constructor is not a
      * root because it delegates `this(1)`. Scala then synthesises a nilary primary that collides
      * with the emitted `def this()`.
      *
      * Where no constructor carries `super(args)`, the funnel is not needed for its original
      * purpose at all — only the clash is. Promoting the nilary constructor removes it, and the
      * delegation is expressible because [[effects]] already inlines exactly this shape.
      *
      * `Effects.deferredTo` is deliberately NOT required to be empty here. It is `Some(parent)`
      * whenever the chain bottoms out at a nilary `super()`, which every Java constructor has
      * implicitly — and which Scala's `extends` clause runs anyway. Requiring it empty made this
      * fire nowhere (measured: no change), which instrumenting `plan0` is what revealed. */
    private def nilaryPlan(cd: Tree.ClassDef): Option[Plan] =
      val ctors = ctorsOf(program, cd.body)
      if ctors.exists(c => superArgsOf(program, c).nonEmpty) then scala.None
      else
        for
          nil  <- ctors.find(_.paramss.flatten.isEmpty)
          head <- stmtsOf(nil).headOption
          (m, as) <- head match
            case Tree.Apply(Tree.Select(r, mm, _, _), aas, _, _, _)
                if isInitName(program, mm) && !r.isInstanceOf[Tree.Super] && aas.nonEmpty => Some((mm, aas))
            case _ => scala.None
          eff  <- effects(m, as, 0)
        yield Plan(Some(nil), Nil, eff.stats ++ stmtsOf(nil).tail)

    private val plans: Map[SymId, Plan] =
      var acc     = classes.map(cd => cd.symbol -> plan0(program, cd)).toMap
      classes.foreach { cd =>
        if acc(cd.symbol).primary.isEmpty then nilaryPlan(cd).foreach(p => acc = acc.updated(cd.symbol, p))
      }
      var changed = true
      while changed do
        changed = false
        // parents that some subclass reaches with an argument-free `extends` clause
        val needNilary = classes.filter(cd => acc.get(cd.symbol).forall(_.superArgs.isEmpty))
          .flatMap(parentSyms)
          .toSet
        acc.foreach { (s, p) =>
          if p.primaryParams.nonEmpty && needNilary(s) then
            // A subclass reaches this class with an argument-free `extends`, so its promoted
            // primary cannot keep parameters. Falling straight to `Plan.none` DISCARDS whatever
            // java's own no-arg constructor did: `Pool()` delegates `this(16, MAX_VALUE)`, which
            // is what allocates `freeObjects`, and every `new NodePool()` then NPE'd on the first
            // `obtain()`. Nothing compiled differently — the migrated suite found it.
            //
            // `nilaryPlan` already knows how to promote a nilary constructor and inline its
            // `this(args)` delegation; use it, and keep `Plan.none` only where there is no nilary
            // constructor to promote (then the class genuinely contributes nothing).
            acc = acc.updated(s, classes.find(_.symbol == s).flatMap(nilaryPlan).getOrElse(Plan.none))
            changed = true
        }
      acc

    def apply(cd: Tree.ClassDef): Plan = plans.getOrElse(cd.symbol, Plan.none)

    // ---- effect replay: expressing a `super(args)` a secondary constructor cannot make ----

    /** The replacement for a secondary constructor's `super(args)`: the parent constructor's own
      * statements, its arguments substituted, to run right after `this()`.
      *
      * This is exact — NOT an approximation — only under conditions checked below: `this()` must
      * be a genuine no-op beyond the parent's nilary construction, and that nilary path must be
      * empty, so `this(); <parent(args) statements>` executes precisely what Java's
      * `super(args)` did, in order, once. Where any of that fails the replay is refused and the
      * constructor stays counted by [[OmissionCheck]]. */
    def replayFor(cd: Tree.ClassDef, d: Tree.DefDef): Option[List[Statement]] =
      replays.get((cd.symbol, d.symbol))

    // ---- the delegation itself: the ONE answer the emitter renders and the check counts ----

    /** What a secondary constructor's `super(args)` becomes. Per-ROOT: two constructors of the same
      * class get different answers, which is precisely what a class-wide flag could not say.
      *
      * The rules are the emitter's, moved here so there is one copy. A SYNTHESISED primary takes
      * the parent constructor's parameters in order, so the delegation is positional and exact. A
      * PROMOTED root's parameters are matched by TYPE, not position — `GdxRuntimeException(Throwable
      * t) { super(t); }` reaches the parent's `(Throwable)` overload, so `t` belongs in the CAUSE
      * slot, and positionally it landed in the message slot and did not even type-check. Every
      * argument must find a home and every unfilled slot must admit `null`, or the call built is not
      * the one Java made and the honest answer is [[SuperCall.Dropped]]. */
    def superCall(cd: Tree.ClassDef, args: List[Term]): SuperCall =
      val plan = apply(cd)
      // NOTE an EMPTY argument list is not a short circuit. An explicit `super()` against a PROMOTED
      // paramful primary still has to reach it — `this()` does not exist there, and returning
      // `Dropped` for it cost one compile error on libGDX. It falls through to the matcher below and
      // becomes the all-`null` call the parent's nilary overload left, exactly as before.
      if plan.synthetic.nonEmpty then
        if args.sizeIs == plan.synthetic.size then SuperCall.Positional(args) else SuperCall.Dropped
      else
        val ps = plan.primaryParams
        if ps.isEmpty || plan.superArgs.size != ps.size || args.sizeIs > ps.size then SuperCall.Dropped
        else
          val used  = collection.mutable.Set[Int]()
          val slots = ps.map { v =>
            val want = headName(program, v.tpt.tpe)
            args.zipWithIndex.find((a, k) => !used(k) && headName(program, a.tpe) == want) match
              case Some((a, k)) => used += k; Some(Slot.Arg(a): Slot)
              case scala.None   =>
                if want.exists(primitiveTypeNames) then scala.None else Some(Slot.NullAt(v.tpt.tpe))
          }
          if slots.exists(_.isEmpty) || used.size != args.size then SuperCall.Dropped
          else
            val ss = slots.flatten
            // the JDK's `Throwable(Throwable)` message, but only when the cause can be READ TWICE —
            // the delegation names it in both slots and scala cannot bind a value before `this(...)`
            if isJdkCauseCall(cd, args, ss) && simple(args.head) then
              SuperCall.Matched(ss.map { case _: Slot.NullAt => Slot.CauseMessage(args.head); case s => s })
            else SuperCall.Matched(ss)

    /** Is this delegation the JDK's `Throwable(Throwable cause)` — the one padded slot that is NOT
      * `null`?
      *
      * Padding is exact for a JDK throwable because the JDK's constructor set is `()`, `(String)`,
      * `(String, Throwable)`, `(Throwable)` and each shorter overload delegates to the widest with
      * `null` in the positions it does not take — with ONE exception, which is the whole content of
      * this function: `Throwable(Throwable cause)` is specified as
      * `this(cause == null ? null : cause.toString(), cause)`, so it FILLS its own message.
      *
      * The configuration is determinate, not a guess: a JDK-throwable parent, exactly ONE super
      * argument, and exactly one padded slot which is the `String`. The only JDK overload that
      * leaves the message unfilled while taking one argument is `(Throwable)`. (`super("m")` pads
      * the THROWABLE slot instead and is untouched; `super()` carries no argument and is untouched.)
      *
      * `java.lang.reflect.InvocationTargetException` is the one JDK type whose `(Throwable)`
      * constructor departs from the contract (it leaves the message null and stores `target`).
      * Nothing can port a subclass of it through this path anyway — `target` is private and no
      * delegation reaches it — so it is out of reach here rather than mishandled. */
    private def isJdkCauseCall(cd: Tree.ClassDef, args: List[Term], slots: List[Slot]): Boolean =
      val padded = slots.collect { case n: Slot.NullAt => n }
      args.sizeIs == 1 && padded.sizeIs == 1 &&
        headName(program, padded.head.tpe).contains("java.lang.String") &&
        jdkThrowableParent(program, cd)

    /** This constructor reached the JDK's `Throwable(Throwable)` overload, and the message that
      * overload computes for itself is LOST — because the delegation would have to name the cause
      * in BOTH slots and re-evaluating it is not free.
      *
      * There is no third option. A scala secondary constructor's first statement must be the
      * `this(...)` call, so no `val` can bind the argument ahead of it (measured: `Not found: x`),
      * and emitting the expression twice would run a side effect java ran once — and could hand the
      * two slots different objects. Refusing and REPORTING is the honest outcome; the alternative
      * is a silent behaviour change of exactly the kind this whole rule exists to remove.
      * [[OmissionCheck.droppedCauseMessages]] counts it. */
    def causeMessageLost(cd: Tree.ClassDef, d: Tree.DefDef): Boolean =
      val args = superArgsOf(program, d)
      args.nonEmpty && !simple(args.head) && !apply(cd).primary.map(_.symbol).contains(d.symbol) &&
        replayFor(cd, d).isEmpty && (superCall(cd, args) match
          case SuperCall.Matched(slots) => isJdkCauseCall(cd, args, slots)
          case _                        => false)

    /** Does THIS constructor's `super(args)` survive into the emitted code? The whole of what
      * [[OmissionCheck.droppedSuperArgs]] asks, and every disjunct is a fact about this one
      * constructor: it carries no arguments to lose, it IS the primary (its arguments go into the
      * `extends` clause), its parent constructor is REPLAYED as statements after `this()`, or the
      * delegation above expresses it. */
    def superExpressed(cd: Tree.ClassDef, d: Tree.DefDef): Boolean =
      val args = superArgsOf(program, d)
      // `.map(_.symbol).contains` — NOT `primary.contains(d.symbol)`, which compiles (both widen to
      // `Any`) and is always FALSE. It reported the promoted primary of 24 libGDX classes as having
      // lost the arguments that were sitting in their `extends` clause.
      args.isEmpty || apply(cd).primary.map(_.symbol).contains(d.symbol) || replayFor(cd, d).isDefined ||
        superCall(cd, args) != SuperCall.Dropped

    /** members a replay reaches that are `private` where they are declared. The replay executes
      * one level down, in the subclass, so they must be widened — which is compile-safe and
      * cannot change behaviour (the hand-ported corpus widens the same way). */
    def widenedMembers: Set[SymId] = widened.toSet

    private val widened = collection.mutable.Set[SymId]()

    private val replays: Map[(SymId, SymId), List[Statement]] =
      val out = collection.mutable.Map[(SymId, SymId), List[Statement]]()
      classes.foreach { cd =>
        // `this()` must run nothing beyond the parent's nilary construction: the class's own
        // primary contributes no statements and passes no super arguments of its own.
        val p = plans.getOrElse(cd.symbol, Plan.none)
        // everything `this()` runs: the class's own promoted primary, and whatever IT reaches
        val prologue = if p.primaryParams.nonEmpty then scala.None else prologueOf(cd.symbol, 0)
        if prologue.isDefined then
          ctorsOf(program, cd.body).foreach { d =>
            if !p.primary.contains(d.symbol) then
              superApply(d).foreach { case (m, args) =>
                effects(m, args, 0).foreach { e =>
                  val stats   = e.stats
                  val touched = collection.mutable.Set[SymId]()
                  // the constructor's OWN statements run after the replay, so they count towards
                  // superseding the prologue too
                  val after = stats ++ stmtsOf(d).tail
                  // a prologue the chain DEFERRED to is what Java ran there as well — it is shared,
                  // not stranded, so it needs no superseding. `prologueOf` builds parents first, so
                  // the deferred class's prologue is a prefix of this one.
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
    private def superApply(d: Tree.DefDef): Option[(SymId, List[Term])] = stmtsOf(d).headOption match
      case Some(Tree.Apply(Tree.Select(_: Tree.Super, m, _, _), args, _, _, _))
          if args.nonEmpty && isInitName(program, m) => Some((m, args))
      case _ => scala.None

    /** The PROLOGUE a replay lands after: what the EMITTED `class C extends P` runs on the way in,
      * which is what the secondary's `this()` reaches. That is P's funnel plan — its promoted
      * primary's super call and body — NOT P's Java nilary constructor, which may not even exist
      * (`BatchTiledMapRenderer` has none, yet the class Scala emits for it has an implicit,
      * effect-free primary). Field initialisers are excluded deliberately: they run identically on
      * both paths. A class outside the translated set contributes nothing — its constructor
      * touches only its own private state, which no replay of ours can reach. */
    private def prologueOf(cls: SymId, depth: Int): Option[List[Statement]] =
      if depth > 8 then scala.None
      else
        classOfSym(cls) match
          case scala.None => Some(Nil)
          case Some(cd) =>
            val p = plans.getOrElse(cd.symbol, Plan.none)
            // a paramful primary means `extends P` with no arguments does not compile; `Plans`
            // withholds such promotions wherever a subclass needs them, so this cannot be reached
            if p.primaryParams.nonEmpty then scala.None
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
      case Tree.Assign(Tree.Ident(f, _, _), _, _, _)            => Some(f)
      case Tree.Assign(Tree.Select(_: Tree.This, f, _, _), _, _, _) => Some(f)
      case _                                                    => scala.None

    /** Does replaying `stats` after `prologue` leave the same STATE Java's `super(args)` left?
      *
      * State, and only state. `prologue` is what the secondary's own `this()` already ran, and this
      * asks whether the replay overwrites every field it assigned — then each of those fields ends
      * up holding what Java put there, and the prologue's contribution to the OBJECT is dead. A
      * prologue statement that is not a plain field assignment already fails it, because
      * [[assignedField]] answers `None` and `None.exists` is false.
      *
      * It compares assignment TARGETS and does not look at the right-hand sides. Read as "the
      * prologue is invisible" that is too strong — an RHS with an escaping effect (`this.n =
      * Registry.register()`) really did happen. Read as what it is, a state question, it is right,
      * and TIGHTENING IT IS A MEASURED REGRESSION: the prologue is the emitted class's own
      * construction path, so `this()` runs it whether this returns true or false. A kill switch
      * forcing `false` left the escaping call exactly where it was and additionally discarded the
      * replay — the constructor lost its argument and gained an omission finding, for no effect
      * removed. `ENGINE-LIMITS.md` C6 has the run.
      *
      * The escaping-effect problem is real and lives one level up, in the PROMOTION: making a
      * nilary constructor's body the class body runs it on every construction path, which Java did
      * not. Nothing here can undo that.
      *
      * What this does NOT preserve is the work: `new DelayedRemovalArray(1000)` allocates the
      * nilary path's 16-element backing array and then throws it away. That is a cost, not a
      * behavioural difference — and it replaces the previous behaviour, which was to silently
      * hand back the 16-element array. */
    private def supersedes(stats: List[Statement], prologue: List[Statement]): Boolean =
      if prologue.isEmpty then true
      else
        val pre = prologue.map(assignedField)
        val set = stats.flatMap(assignedField).toSet
        pre.forall(f => f.exists(set.contains))

    /** a term that may be substituted for a parameter: evaluating it twice is evaluating it once,
      * so inlining it at each of the parameter's uses preserves Java's evaluate-arguments-once. */
    private def simple(t: Term): Boolean = t match
      case _: Tree.Ident | _: Tree.Literal | _: Tree.This => true
      case Tree.Select(q, _, _, _)                        => simple(q)
      case Tree.Typed(e, _, _, _)                         => simple(e)
      case Tree.ArrayLength(a, _, _)                      => simple(a)
      case _                                              => false

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

    /** The parent's type PARAMETERS mapped to the arguments this class instantiates them with.
      *
      * A replay lifts the parent constructor's statements into the subclass, and those statements
      * are typed in the PARENT's scope: `class FlushablePoolClass extends FlushablePool[String]`
      * replayed `new Array[T](false, n)` verbatim, where `T` is `Pool`'s parameter and nothing in
      * the subclass can name it (`Not found: type T`). The instantiation is right there in the
      * `extends` clause, so the substitution is exact rather than a guess. */
    private def parentTypeSubst(cd: Tree.ClassDef): Map[SymId, TypeRepr] =
      def headArgs(t: TypeRepr): Option[(SymId, List[TypeRepr])] = t match
        case TypeRepr.AppliedType(TypeRepr.TypeRef(_, s), as) => Some(s -> as)
        case _                                                => scala.None
      // TRANSITIVE: the statements may come from a GRANDparent. `FlushablePoolClass extends
      // FlushablePool[String]`, `FlushablePool<T> extends Pool<T>`, and the replayed `new Array[T]`
      // is `Pool`'s `T` — so the chain has to be walked, composing each level's instantiation
      // through the one below it.
      def sub(t: TypeRepr, m: Map[SymId, TypeRepr]): TypeRepr = t match
        case TypeRepr.TypeRef(_, x) if m.contains(x) => m(x)
        case TypeRepr.AppliedType(tc, as)            => TypeRepr.AppliedType(sub(tc, m), as.map(sub(_, m)))
        case other                                   => other
      def walk(c: Tree.ClassDef, acc: Map[SymId, TypeRepr], depth: Int): Map[SymId, TypeRepr] =
        if depth > 8 then acc
        else
          c.parents.flatMap {
            case tt: TypeTree => headArgs(tt.tpe)
            case t: Term      => headArgs(t.tpe)
          }.foldLeft(acc) { case (m, (psym, as)) =>
            classOfSym(psym) match
              case scala.None => m
              case Some(pc)   =>
                val here = pc.tparams.map(_.symbol).zip(as.map(sub(_, m))).toMap
                walk(pc, m ++ here, depth + 1)
          }
      walk(cd, Map.empty, 0)

    /** rewrite every TYPE in these statements through `m`. */
    private def retyped(stats: List[Statement], m: Map[SymId, TypeRepr]): List[Statement] =
      if m.isEmpty then stats
      else
        given Program = program
        def sub(t: TypeRepr): TypeRepr = t match
          case TypeRepr.TypeRef(_, s) if m.contains(s) => m(s)
          case TypeRepr.AppliedType(tc, as)            => TypeRepr.AppliedType(sub(tc), as.map(sub))
          case other                                   => other
        val ph = new Phase:
          def name = "ctor-replay-retype"
          override def transformType(t: TypeRepr)(using Program): TypeRepr = sub(t)
        stats.map(StandardTraversal.mapStat(ph, _))

    private def substituted(stats: List[Statement], m: Map[SymId, Term]): List[Statement] =
      if m.isEmpty then stats
      else
        given Program = program
        val ph = new Phase:
          def name = "ctor-replay-subst"
          override def transformIdent(t: Tree.Ident)(using Program): Term = m.getOrElse(t.sym, t)
        stats.map(StandardTraversal.mapStat(ph, _))

    /** the statements `<ctor>(args)` executes, its own delegation chain inlined ahead of its body
      * — the whole of what a `super(args)` did. */
    /** the statements `<ctor>(args)` runs, and — when the chain bottoms out at an explicit nilary
      * `this()`/`super()` — the class whose nilary construction it thereby DEFERS to. Those
      * statements are omitted here because the emitted `this()` already ran exactly them; the
      * caller must not then demand that the replay supersede them. */
    private final case class Effects(stats: List[Statement], deferredTo: Option[SymId])

    private def effectsOf(ctor: SymId, args: List[Term], depth: Int): Option[List[Statement]] =
      effects(ctor, args, depth).map(_.stats)

    private def effects(ctor: SymId, args: List[Term], depth: Int): Option[Effects] =
      if depth > 6 then scala.None
      else
        defOf(ctor).flatMap { d =>
          val ps   = d.paramss.flatten
          val stms = stmtsOf(d)
          // Substitution inlines an argument at each of its parameter's uses. That is Java's
          // evaluate-once only when re-evaluating is free (`simple`) or the parameter is used
          // exactly once and nothing in the body can run that use repeatedly. A parameter used
          // ZERO times would otherwise DISCARD a side-effecting argument, so it needs `simple`
          // too. (An argument cannot read `this` — Java forbids it before `super(...)` — so
          // evaluating it at the use site rather than up front cannot observe the body's own
          // field writes.)
          lazy val counts = useCounts(stms)
          lazy val loopFree = !repeats(stms)
          def ok(p: Tree.ValDef, a: Term) = simple(a) || (loopFree && counts.getOrElse(p.symbol, 0) == 1)
          if ps.length != args.length || !ps.zip(args).forall(ok) then scala.None
          else
            val body = substituted(stms, ps.map(_.symbol).zip(args).toMap)
            body match
              case Tree.Apply(Tree.Select(_, m, _, _), as, _, _, _) :: tl if isInitName(program, m) =>
                if as.isEmpty then Some(Effects(tl, program.symbolOf(m).map(_.owner)))
                else effects(m, as, depth + 1).map(e => Effects(e.stats ++ tl, e.deferredTo))
              case all => Some(Effects(all, scala.None))
        }

    /** can these statements legally run one level down, in `cd`'s constructor? */
    private def usable(cd: Tree.ClassDef, d: Tree.DefDef, stats: List[Statement], touched: collection.mutable.Set[SymId]): Boolean =
      // an EMPTY replay is a real answer, not a failure: `super(value)` on a parent constructor
      // whose whole body is `this();` adds nothing beyond the nilary construction the emitted
      // `this()` already performed. Dropping the ARGUMENTS' effects would not be sound, but a
      // parameter used zero times already requires a re-evaluable argument (see `effectsOf`).
      if stats.isEmpty then true
      else
        given Program = program
        var ok = true
        val ph = new Phase:
          def name = "ctor-replay-scan"
          override def transformTerm(t: Term)(using Program): Term =
            t match
              // a parent's own `super.m()` would dispatch one level too high once replayed here
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
          // a private member of a class OTHER than the one the statements land in is reachable
          // only by widening it; a private member of a class we do not translate is not reachable
          // at all
          touched.forall { s =>
            program.symbolOf(s).forall { sy =>
              !sy.flags.isPrivate || sy.owner == cd.symbol || classOfSym(sy.owner).isDefined
            }
          }

  /** does this class extend a JDK THROWABLE? Its constructor set is fixed and public — `()`,
    * `(String)`, `(String, Throwable)`, `(Throwable)` — which is what makes null-padding a shorter
    * super call exact rather than a guess. A java fact, not a library one. */
  private def jdkThrowableParent(program: Program, cd: Tree.ClassDef): Boolean =
    cd.parents.headOption.flatMap {
      case tt: TypeTree => headName(program, tt.tpe)
      case t: Term      => headName(program, t.tpe)
    }.exists(n => n == "java.lang.Throwable" ||
                  (n.startsWith("java.") && (n.endsWith("Exception") || n.endsWith("Error"))))

  private def parentSyms(cd: Tree.ClassDef): List[SymId] =
    def head(t: TypeRepr): Option[SymId] = t match
      case TypeRepr.TypeRef(_, s)      => Some(s)
      case TypeRepr.AppliedType(tc, _) => head(tc)
      case _                           => scala.None
    cd.parents.flatMap { case tt: TypeTree => head(tt.tpe); case t: Term => head(t.tpe) }

  /** statements of a constructor body, block or not. */
  def stmtsOf(d: Tree.DefDef): List[Statement] = d.rhs match
    case Some(Tree.Block(s, _, _, _)) => s
    case Some(t)                      => List(t)
    case None                         => Nil

  def isCtor(program: Program, d: Tree.DefDef): Boolean =
    program.symbolOf(d.symbol).exists(_.name == "<init>")

  def ctorsOf(program: Program, body: List[Statement]): List[Tree.DefDef] =
    body.collect { case d: Tree.DefDef if isCtor(program, d) => d }

  /** a leading `this(args)` delegation to a PEER constructor (never `super`, never nilary —
    * an explicit nilary call is the implicit primary and carries no information). */
  def delegatesToThis(program: Program, d: Tree.DefDef): Boolean = stmtsOf(d).headOption.exists {
    case Tree.Apply(Tree.Select(r, m, _, _), args, _, _, _) =>
      isInitName(program, m) && !r.isInstanceOf[Tree.Super] && args.nonEmpty
    case _ => false
  }

  private def isInitName(program: Program, m: SymId): Boolean =
    program.symbolOf(m).exists(_.name == "<init>")

  /** the leading `super(args)` of a constructor, when it passes arguments — exactly what a
    * secondary constructor cannot express. */
  private def headName(program: Program, t: TypeRepr): Option[String] = t match
    case TypeRepr.TypeRef(_, s)      => program.symbolOf(s).map(_.fullName)
    case TypeRepr.AppliedType(tc, _) => headName(program, tc)
    case _                           => scala.None

  def superArgsOf(program: Program, d: Tree.DefDef): List[Term] = stmtsOf(d).headOption match
    case Some(Tree.Apply(Tree.Select(_: Tree.Super, m, _, _), args, _, _, _)) if isInitName(program, m) => args
    case _                                                                                              => Nil

  /** split a constructor body into (super args to lift, remaining statements). */
  private def split(program: Program, d: Tree.DefDef): (List[Term], List[Statement]) =
    stmtsOf(d) match
      case Tree.Apply(Tree.Select(_: Tree.Super, m, _, _), args, _, _, _) :: tl if isInitName(program, m) => (args, tl)
      // an explicit nilary `this()`/`super()` is what the implicit primary already does
      case Tree.Apply(Tree.Select(_, m, _, _), args, _, _, _) :: tl if isInitName(program, m) && args.isEmpty => (Nil, tl)
      case all => (Nil, all)

  /** Nominate the Java constructor that becomes Scala's primary for this class, ignoring the
    * whole-program constraint that [[Plans]] applies on top. */
  def plan0(program: Program, cd: Tree.ClassDef): Plan =
    val s = program.symbolOf(cd.symbol)
    if s.exists(x => x.flags.isModule || x.flags.isTrait || x.flags.isEnum) then Plan.none
    else
      val ctors = ctorsOf(program, cd.body)
      val roots = ctors.filterNot(delegatesToThis(program, _))
      // a Java GENERIC constructor has no Scala primary form either (the class's own type params
      // are the only ones in scope in an `extends` clause) — leave it a secondary.
      // Several roots, each with its own `super(args)`: nominating the NILARY one makes a nilary
      // primary available but silently drops every other root's arguments — which is how
      // `SerializationException(String message) { super(message); }` became
      // `def this(message: String) = { this() }` and every exception in the port lost its message.
      //
      // Nominate the WIDEST super call instead, so `extends Parent(a, b)` carries the arguments,
      // and let the narrower roots delegate to it ([[superDelegation]] in the emitter). Only when
      // that root passes its own parameters STRAIGHT THROUGH to super, since the delegation is
      // built by substituting the other root's super arguments into those parameters. This is the
      // shape the reference port writes by hand:
      //   `enum SgeError(message: String, cause: Option[Throwable]) extends Exception(message, cause.orNull)`
      def throwableParent: Boolean = jdkThrowableParent(program, cd)
      def passesThrough(c: Tree.DefDef): Boolean =
        val ps = c.paramss.flatten.map(_.symbol)
        superArgsOf(program, c).map { case Tree.Ident(x, _, _) => x; case _ => SymId.None } == ps && ps.nonEmpty
      val chosen = roots match
        case one :: Nil if one.tparams.isEmpty => Some(one)
        case several if throwableParent =>
          // ONLY for a JDK throwable parent. The delegation pads a shorter super call with `null`,
          // and that is equivalent to the parent's own narrower overload for exactly one family:
          // `Throwable(String)` vs `Throwable(String, null)`, which is the JDK's own documented
          // constructor set. For any other parent — `DistanceFieldFont extends BitmapFont` has
          // seven roots reaching different overloads — padding is a GUESS, and guessing there
          // measured 0 -> 55 errors. Those stay counted by `OmissionCheck`.
          val widest = several.filter(c => c.tparams.isEmpty && passesThrough(c))
            .sortBy(c => -superArgsOf(program, c).size).headOption
          widest.filter(w => several.exists(o => (o ne w) && superArgsOf(program, o).nonEmpty))
            .orElse(several.find(c => c.paramss.flatten.isEmpty && c.tparams.isEmpty))
        case several                           => several.find(c => c.paramss.flatten.isEmpty && c.tparams.isEmpty)
      chosen match
        case Some(c) if roots.count(r => superArgsOf(program, r).nonEmpty) <= 1 =>
          val (sa, rest) = split(program, c); Plan(Some(c), sa, rest)
        // MORE THAN ONE root carries `super(args)`: whichever is nominated, the others' arguments
        // are dropped. Try the synthesised primary before falling back to that.
        // NOT for a throwable parent: that branch already nominates the WIDEST pass-through root and
        // is measured (0 -> 55 when it guessed). Consulting the synthesis first let it nominate a
        // NARROWER pass-through root instead — libGDX omissions 46 -> 50, four exception classes
        // losing an argument each.
        case other if !throwableParent => syntheticPrimary(program, cd, roots).getOrElse {
          other match
            case None    => Plan.none
            case Some(c) => val (sa, rest) = split(program, c); Plan(Some(c), sa, rest)
        }
        // a THROWABLE parent keeps the measured choice above, untouched by the synthesis
        case None    => Plan.none
        case Some(c) => val (sa, rest) = split(program, c); Plan(Some(c), sa, rest)

  /** A primary whose parameters ARE the parent constructor's — see [[Plan.synthetic]].
    *
    * Refuses unless every root reaches the same parent constructor with the same arity, and unless
    * that constructor's parameter types are all readable here. A refusal leaves the previous
    * behaviour AND the omission finding, which is the honest outcome: `OmissionCheck` reported all
    * five of simple-graphs' dropped `super(args)` correctly, and the defect survived because nobody
    * opened the report — not because the report was missing. */
  private def syntheticPrimary(program: Program, cd: Tree.ClassDef,
                               roots: List[Tree.DefDef]): Option[Plan] =
    val calls = roots.map(r => superTarget(program, r) -> superArgsOf(program, r))
    val targets = calls.map(_._1).distinct
    val arities = calls.map(_._2.size).distinct
    if roots.sizeIs < 2 || roots.exists(_.tparams.nonEmpty) then scala.None
    else if targets.sizeIs != 1 || targets.head == SymId.None then scala.None
    else if arities.sizeIs != 1 || arities.head == 0 then scala.None
    else
      def passesThrough(c: Tree.DefDef): Boolean =
        val ps = c.paramss.flatten.map(_.symbol)
        superArgsOf(program, c).map { case Tree.Ident(x, _, _) => x; case _ => SymId.None } == ps && ps.nonEmpty
      // parameter TYPES from the parent constructor's own signature, never from one call's
      // arguments: an argument is an expression whose type may be narrower than the formal.
      val formals = program.symbolOf(targets.head).map(_.info).collect {
        case TypeRepr.MethodType(ps, _, _)                       => ps.map(_._2)
        case TypeRepr.PolyType(_, TypeRepr.MethodType(ps, _, _)) => ps.map(_._2)
      }.getOrElse(Nil)
      // a java constructor that ALREADY has the synthetic signature would collide with it
      val collides = roots.exists(_.paramss.flatten.map(_.tpt.tpe) == formals)
      if formals.sizeIs != arities.head || formals.contains(TypeRepr.NoType) then scala.None
      // TWO DISJOINT SHAPES, and keeping them disjoint is the whole content of this decision. Three
      // orderings were measured against libGDX before this one, and every ordering that let either
      // shape reach the other's classes moved a number: promotion-first cost 4 omissions
      // (`Texture`, `ShaderProgramLoader`, `FloatAttribute`, `IntAttribute`), synthesis-first cost 2
      // compile errors and 5 omissions. libGDX is untouched only when each applies where it belongs.
      //
      // SHAPE 1 — a NO-ARG root that still carries `super(args)`. Nothing can be promoted: the
      // no-arg root would give the class a nilary primary and drop every other root's arguments,
      // and promoting a paramful root leaves the no-arg root with nothing to delegate with.
      // Synthesis is the only encoding. `AlgorithmPath()` / `AlgorithmPath(Node)` is this shape.
      else if roots.exists(_.paramss.flatten.isEmpty) then
        if collides then scala.None
        else
          val o  = cd.origin
          val ps = formals.zipWithIndex.map((ft, k) => (s"sup$$$k", ft))
          Some(Plan(scala.None, ps.map((n, ft) => Tree.Opaque(n, ft, o)), Nil, synthetic = ps))
      // SHAPE 2 — every root is paramful, and one of them already IS the synthetic primary (its own
      // parameters are the parent's, passed straight through). Synthesising beside it would emit a
      // duplicate signature, so that root is promoted and the others delegate.
      // `Path(int, boolean)` / `Path(int)` is this shape. Where there is no such collision the old
      // behaviour stands: the class keeps its omission finding rather than gaining a guess.
      else if collides then
        roots.filter(passesThrough).sortBy(c => -c.paramss.flatten.size).headOption
          // the promoted root's parameters ARE the parent's, so every other root's `super(args)`
          // reaches it through `this(...)`. Whether each one ACTUALLY does is not asserted here —
          // it is [[Plans.superCall]]'s answer, per root, and the one the emitter renders.
          .map { c => val (sa, rest) = split(program, c); Plan(Some(c), sa, rest) }
      else scala.None

  private def superTarget(program: Program, d: Tree.DefDef): SymId = stmtsOf(d).headOption match
    case Some(Tree.Apply(Tree.Select(_: Tree.Super, m, _, _), args, _, _, _))
        if args.nonEmpty && isInitName(program, m) => m
    case _ => SymId.None
