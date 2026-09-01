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
  * SEVEN shapes are funnelled today, each nominated at its own site and documented there. This list
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
  *  4. SYNTHESISED PRIMARY — every root reaches the SAME parent constructor. A `protected` primary
  *     taking the PARENT constructor's own formals is synthesised and every java constructor becomes
  *     a secondary computing its arguments. NO java body becomes the class body, so this shape is
  *     the only one that cannot escape (5) or lose a root's `super(args)` (3).
  *     ([[syntheticPrimary]], [[Plan.synthetic]])
  *  5. SYNTHETIC-SHAPED ROOT — the same situation where one root ALREADY has the synthesised
  *     signature, so synthesising beside it would duplicate it. That root is promoted instead.
  *     ([[syntheticPrimary]])
  *  6. PROMOTED NILARY CONSTRUCTOR — no constructor carries `super(args)` at all, so the funnel is
  *     needed only to stop Scala's implicit nilary primary clashing with an emitted `def this()`.
  *     The nilary constructor is promoted with its `this(args)` delegation inlined.
  *     ([[Plans.nilaryPlan]])
  *  7. PADDED THROWABLE SYNTHESIS, JDK THROWABLES ONLY — several roots reaching DIFFERENT overloads
  *     of the JDK throwable, and not one of them a pass-through, so (3) has nothing to promote. A
  *     primary is synthesised at the family's WIDEST overload and each root pads into it the way
  *     the JDK's own narrower constructor pads. The one place §4.4's "promote the widest super
  *     call" cannot mean "promote a root". ([[plan0]], [[throwablePadding]], `ENGINE-LIMITS.md` C3)
  *
  * Two mechanisms sit ON TOP of the nomination rather than beside it: [[Plans]] WITHHOLDS a
  * paramful promotion wherever a subclass reaches the class with an argument-free `extends`, and
  * [[Plans.replayFor]] expresses a secondary's `super(args)` as the parent constructor's own
  * statements replayed after `this()` where that is provably equivalent.
  *
  * Anything else (several paramful roots reaching different parent overloads of a parent whose
  * constructor set the engine does not know, e.g. `DelayedRemovalArray`'s ten) has no
  * single-primary encoding and is reported, not approximated.
  *
  * What every promotion COSTS is reported the same way. A promoted body becomes the class body, and
  * a scala class body runs on EVERY construction path — which java's did not, wherever another
  * constructor does not delegate to the promoted one. Refusing the promotion is not available
  * (measured 0 -> 41 compile errors), so [[Plans.promotionEscapes]] names the paths and
  * [[OmissionCheck.promotedBodyOnEveryPath]] counts them. `ENGINE-LIMITS.md` C7.
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
      /** The SIMPLE NAME of a per-class marker type the synthesised primary takes as a final
        * parameter, when the slot list alone would not be a legal or reachable signature.
        *
        * Two different failures need it and neither is visible without it (`ENGINE-LIMITS.md` C8):
        * a real constructor whose ERASED parameter list equals the slots cannot coexist with the
        * primary (`E120 … have the same type after erasure`, which `private` does not separate),
        * and a real constructor whose parameters are NARROWER than the parent's formals WINS the
        * `this(<a root's own super arguments>)` overload resolution outright — `DistanceFieldFontCache`
        * delegating to itself. The marker changes the primary's ARITY, which removes it from every
        * delegation's candidate set at once and makes both questions moot.
        *
        * It is minted PER CLASS, in that class's own companion, rather than once in `runtime/`:
        * emitted code then carries no dependency on the engine's runtime artifact for a purely
        * local encoding, at the price of one companion member per disambiguated class. And it is
        * `protected` in the companion, never `private` — scala requires every type in a member's
        * signature to be at least as visible as the member, so a companion-`private` marker in a
        * `protected` primary is `non-private constructor C in class C refers to private class
        * Funnel` (`ENGINE-LIMITS.md` C9, measured against scalac 3.8.4).
        *
        * Never spelled as an FQN in the port map or in a contract row (`DESIGN.md` §8.1 F4): a
        * companion-protected type is not a name any ordinary consumer may resolve, so the row says
        * `disambiguator=marker` and nothing more. */
      marker: Option[String] = scala.None,
      /** How many of [[synthetic]]'s slots are the PARENT constructor's own formals. Everything
        * after them is a FIELD slot, and the split matters to [[Plans.superCall]], which asks
        * whether a root's `super(args)` reaches the primary POSITIONALLY — a question about the
        * super slots alone. Reading `synthetic.size` there counted the field slots too and
        * reported every expressed root as dropped. */
      superSlots: Int = 0,
      /** the fields whose value is hoisted into the primary's parameter list, in slot order. */
      fieldSlots: List[FieldSlot] = Nil,
      /** the FULL argument list each ROOT delegates with — super slots then field slots, in slot
        * order. The marker's `null` is appended by the emitter, which is the one part of the call
        * that is not a java expression. Keyed by the root's symbol because it is per-ROOT: two
        * constructors of the same class assign the same field different values, which is the
        * entire point of hoisting it. */
      delegations: Map[SymId, List[Term]] = Map.empty,
      /** how many LEADING statements of each root the funnel consumed into field slots — dropped
        * from that secondary's body, since the primary now performs them. */
      consumed: Map[SymId, Int] = Map.empty,
      /** every field that was a CANDIDATE slot and was refused, with the reason, so an agent asking
        * "why is this field a `var`" is answered where the question is asked (§4.575). A1 has no
        * other channel for it. */
      notSlot: List[(String, String)] = Nil,
      /** The trailing CONTEXT clauses the emitted primary carries — a `(using T)` group a PHASE put
        * on this class's constructors (`DESIGN.md` §8.4), never anything java declared.
        *
        * Java's parameter list is ONE list; Scala's is a list of GROUPS, and the difference is not
        * cosmetic once a phase can add a clause. Flattened into [[primaryParams]] the group is lost
        * and the emitter writes `class Scene($p: demo.Ctx)` — an ordinary class parameter, with no
        * given in scope anywhere in the body and every `summon` in it failing. That was one of
        * `ENGINE-LIMITS.md` CT4's three causes; the other two are the same fact read at the
        * NOMINATION, where a constructor that gained a clause stopped counting as java's nilary one.
        *
        * So the plan models the split: [[primaryParams]] is what JAVA declared and this is what the
        * pipeline added, and every "is this constructor nilary" question in this file asks the
        * first. Empty for every unedited program, which is why no port's output moves.
        *
        * Always TRAILING — [[givenClauses]] reads them off the end of `paramss` — because that is
        * where `Phase.transformDefDef` appends one and where Scala requires a `using` clause to be
        * for the call sites to stay unchanged. */
      givens: List[List[Tree.ValDef]] = Nil,
      /** This promotion is THE COLLAPSE: the promoted root's own parameters ARE the parent
        * constructor's formals, passed straight through, in order ([[collapseTo]]).
        *
        * Recorded because it is the one promotion whose primary can be delegated to POSITIONALLY.
        * A promoted root generally passes only SOME of its parameters up (`Sized(String, int, int)`
        * calling `super(cap, max)`), so another root's `super(args)` has to be matched into the
        * primary's parameters BY TYPE — and that fill declines whenever an argument finds no
        * parameter of its own type. Under a collapse the fill is not the right question at all:
        * every root of the class reaches the SAME parent constructor, so every root's arguments
        * fill the SAME formals in the SAME order, and those formals are exactly the primary's
        * parameters. `this(args)` is then type-correct for every sibling by construction.
        *
        * `false` for every other plan, including a synthesis — which is positional for its own
        * reason (`superSlots`) and asks this nothing. */
      collapse: Boolean = false,
  ):
    /** the primary's VALUE parameters — java's own, never the context clause [[givens]] holds. */
    def primaryParams: List[Tree.ValDef] =
      primary.map(_.paramss.dropRight(givens.size).flatten).getOrElse(Nil)

    /** Is this a SYNTHESISED primary — a constructor no java declared?
      *
      * `synthetic.nonEmpty` is not the test, and reading it as one emitted a class whose primary
      * took the marker parameter and whose parameter LIST was empty: every secondary then wrote
      * `this(null)` against scala's implicit nilary primary. A class with no super slots and no
      * hoistable field is disambiguated by the marker ALONE, which is exactly the shape that
      * replaces a promoted nilary constructor (`ENGINE-LIMITS.md` C7). */
    def isSynthesised: Boolean = synthetic.nonEmpty || marker.isDefined

  object Plan:
    /** No primary of this funnel's making — the emitted class keeps scala's own implicit nilary one
      * and every java constructor is a `def this`.
      *
      * It is the THIRD outcome and the most common one in real code, which is why [[Plan.givens]]
      * can be non-empty for a plan that is otherwise this: an implicit primary cannot carry a
      * context clause, so a class whose constructors have one gets [[Plan.givens]] here and the
      * emitter spells the primary `class X(using T)`. Nothing else about the plan changes — no
      * super argument is lifted, no java body becomes the class body, every secondary delegates
      * exactly as it did, and a `super(args)` that was a counted omission still is
      * (`DESIGN.md` §8.2, `ENGINE-LIMITS.md` CT5). */
    val none: Plan = Plan(scala.None, Nil, Nil)

  /** ONE hoisted field: the primary takes its value as a parameter and the field's own declaration
    * binds it, so each java constructor's `this.f = e` becomes an argument of that constructor's
    * delegation instead of a statement it runs afterwards.
    *
    * `mutable` is a SEPARATE question from slot-eligibility, and reading the two as one gate is
    * what `DESIGN.md` §8.2 corrects: a field with an ordinary setter fails the single-write
    * condition while remaining a perfectly good SLOT. It decides `val f = f$name` against
    * `var f = f$name` and nothing else. */
  final case class FieldSlot(
      field: SymId,
      /** the primary's parameter name for it — engine-minted, so `ENGINE-LIMITS.md` C2's collision
        * with an inherited member cannot arise. */
      name: String,
      tpe: TypeRepr,
      mutable: Boolean,
      /** how many writes of this field the funnel CONSUMED. Compared against the whole program's
        * write count by [[Plans]], which is where `mutable` is finally decided — a local
        * derivation cannot see a setter in another member. */
      writes: Int,
  )

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
    /** `this(args)` — positional against a primary whose parameters ARE the parent constructor's,
      * in its order. No matching of any kind is needed or wanted here.
      *
      * TWO plans have that property and both use this: a SYNTHESISED primary (its slots are the
      * formals, `Plan.superSlots` of them) and THE COLLAPSE (a promoted root that passes its own
      * parameters straight through, `Plan.collapse`). */
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
  /** @param surface
    *   what this run may CONCLUDE about a class it does not emit (`DESIGN.md` §8.3). The fixpoint
    *   below is the whole reason the view exists.
    *
    *   '''The demotion step is EXTRA-UNIT SENSITIVE and the plan it edits is not.''' `plan0`,
    *   `nilaryPlan` and the synthesis are local functions of one class's own Java, so a dependent
    *   re-derives every base class's nomination exactly. The FIXPOINT is not: `needNilary` is
    *   computed from the SUBCLASSES, a dependent's program has subclasses the base's did not, and
    *   the step only ever REMOVES promotions — so a dependent computes a same-or-NARROWER parameter
    *   list than the base emitted, and then writes `extends Base()` against a base whose real
    *   primary takes parameters. Three compile errors on one corpus port, with every check in the
    *   run reporting clean (`ENGINE-LIMITS.md` D4).
    *
    *   So the fixpoint runs over OWNED classes only — both the demand set and the demotion target —
    *   and a non-owned class is reconciled against the contract by [[reconciled]]. The default is a
    *   surface over the whole program, which is what a single-module run, a spec and `DebugEmit` all
    *   are; under it every class is owned and this file behaves exactly as it did.
    */
  final class Plans(program: Program, surfaceView: Option[Surface] = scala.None):
    /** `Option`, and not a defaulted `TrivialSurface(program)`, because a Scala class's default
      * argument cannot refer to another parameter of the same list. `None` reads as what it is. */
    private val surface: Surface = surfaceView.getOrElse(TrivialSurface(program))

    // `allClassDefs` and not a body recursion: a METHOD-LOCAL class (`JS-C30`) has its own
    // constructors and stands in a member's block, so a walk over class bodies plans NOTHING for
    // it — and a class the funnel does not plan emits every constructor as a SECONDARY one,
    // delegating to a primary that was never synthesised.
    private val classes: List[Tree.ClassDef] =
      program.units.flatMap(u => StandardTraversal.allClassDefs(u)(using program))

    /** …and the ones this run EMITS, which is the fixpoint's whole domain. */
    private val ownedClasses: List[Tree.ClassDef] = classes.filter(cd => surface.owns(cd.symbol))

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
          nil  <- ctors.find(valueParams(program, _).isEmpty)
          head <- headStmt(nil)
          (m, as) <- head match
            case Tree.Apply(Tree.Select(r, mm, _, _), aas, _, _, _)
                if isInitName(program, mm) && !r.isInstanceOf[Tree.Super] && aas.nonEmpty => Some((mm, aas))
            case _ => scala.None
          eff  <- effects(m, as, 0)
        yield promoted(program, nil, Nil, eff.stats ++ stmtsOf(nil).tail)

    /** Does the emitted class take constructor arguments it cannot be built without? A SYNTHESISED
      * primary is paramful even though `primaryParams` is empty — no java constructor backs it, so
      * its parameters live in `Plan.synthetic`. Reading only `primaryParams` here let the fixpoint's
      * whole-program guard (`ENGINE-LIMITS.md` C1) miss the synthesis entirely. */
    private def paramfulPrimary(p: Plan): Boolean = p.primaryParams.nonEmpty || p.isSynthesised

    /** Can a subclass still write an ARGUMENT-FREE `extends C` against this paramful primary?
      *
      * Yes exactly when some constructor of the class survives as a NILARY secondary — validated
      * against scalac 3.8.4: `class D extends C` with no parentheses at all resolves to the nilary
      * `def this()`, not to the primary. That is what makes a SYNTHESISED primary safe where a
      * PROMOTED one is not: synthesis consumes no java constructor, so a `C()` java declared is
      * still there to be reached, while a promotion turns the constructor it promotes into the
      * class's parameter list and removes that path (C1's `FillViewport extends ScalingViewport`).
      *
      * Deliberately NOT extended to promoted plans. A promoted paramful primary that ALSO has a
      * separate nilary constructor would be safe by the same argument — the promotion CONSUMED
      * that constructor, so it is not there to be reached. */
    private def reachableArgumentFree(s: SymId, p: Plan): Boolean =
      p.isSynthesised &&
        classes.find(_.symbol == s).exists(cd => ctorsOf(program, cd.body).exists(valueParams(program, _).isEmpty))

    private val plans: Map[SymId, Plan] =
      var acc     = classes.map(cd => cd.symbol -> plan0(program, cd)).toMap
      // `primary.isEmpty` is NOT "nothing was nominated": a SYNTHESISED plan has no `primary`
      // either, because no java constructor backs it. Reading it as one let `nilaryPlan` overwrite
      // every synthesis in its own domain — a class with no `super(args)` anywhere is exactly what
      // both of these fire on — and the promotion came back with its escaping body: libGDX core's
      // `CharArray` alone was 9 escaping paths that the synthesis had already removed.
      classes.foreach { cd =>
        val p = acc(cd.symbol)
        if p.primary.isEmpty && !p.isSynthesised then
          nilaryPlan(cd).foreach(q => acc = acc.updated(cd.symbol, q))
      }
      // THE WITHHOLDING FIXPOINT — and its DELETION for synthesised plans was measured and is a
      // dead end, so the guard stays and only the FALLBACK changed (`ENGINE-LIMITS.md` C1).
      //
      // `DESIGN.md` §8.2 argued that a synthesis removes nothing — every java constructor survives
      // as a `def this`, so `extends C` reaches the nilary one — and therefore needs no withholding.
      // The premise is true and the conclusion does not follow, because the TRIGGER is not "java
      // wrote an argument-free `extends`": it is `needNilary`, computed from the SUBCLASS's plan,
      // and a subclass whose own plan carries no super arguments emits `extends P` BARE even where
      // java wrote `super(args)`. A synthesised class with no nilary java constructor is then
      // reached argument-free by a subclass java never reached that way. Measured on libGDX core,
      // guard removed: **0 -> 4 compile errors** (`E134`, "None of the overloaded alternatives of
      // constructor BatchTiledMapRenderer") and omissions 180 -> 196.
      //
      // What WAS wrong is the fallback. Withholding used to drop straight to `nilaryPlan`, which
      // for a class the synthesis had claimed threw away the promotion it would otherwise have had
      // — every root's `super(args)` with it (dropped supers 30 -> 79). A withheld synthesis now
      // falls back to THE PLAN THIS CLASS WOULD HAVE HAD WITHOUT THE SYNTHESIS, which is exactly
      // the pre-A2 emission for it, and nothing regresses.
      var changed = true
      while changed do
        changed = false
        // parents that some subclass reaches with an argument-free `extends` clause.
        //
        // OVER THIS RUN'S OWN CLASSES ONLY. The base's own subclasses already demoted what they
        // demoted, in the base's run, and that answer is published; a dependent's EXTRA subclasses
        // must not demote a class the dependent does not emit (`ENGINE-LIMITS.md` D4). For a
        // single-module run this is every class and nothing moves.
        val needNilary = ownedClasses.filter(cd => acc.get(cd.symbol).forall(_.superArgs.isEmpty))
          .flatMap(parentSyms)
          .toSet
        acc.foreach { (s, p) =>
          if surface.owns(s) && paramfulPrimary(p) && needNilary(s) && !reachableArgumentFree(s, p) then
            // A subclass reaches this class with an argument-free `extends`, so its paramful
            // primary cannot keep parameters. Falling straight to `Plan.none` DISCARDS whatever
            // java's own no-arg constructor did: `Pool()` delegates `this(16, MAX_VALUE)`, which
            // is what allocates `freeObjects`, and every `new NodePool()` then NPE'd on the first
            // `obtain()`. Nothing compiled differently — the migrated suite found it.
            //
            // In order: the plan this class would have had WITHOUT the synthesis (a promotion,
            // whose `super(args)` still reach the `extends` clause); then `nilaryPlan`, which knows
            // how to promote a nilary constructor and inline its `this(args)` delegation; then
            // `Plan.none`, where the class genuinely contributes nothing.
            //
            // THE FIRST ATTEMPT IS CURRENTLY INERT, and that is measured rather than suspected: the
            // filter wants a plan that is NOMINATED and NOT paramful, which for several roots means
            // `several.find(nilary)` found a nilary ROOT — and a class with a nilary constructor is
            // one `reachableArgumentFree` never withholds in the first place, so the two conditions
            // exclude each other. Probed over libGDX core: of 10 withheld classes the filter passes
            // for NONE, and deleting the attempt outright leaves the lane byte-for-byte identical
            // (0 members changed, omissions 65, errors 0). Kept because it is the correct order and
            // it costs nothing; NOT kept as a thing anybody may cite as load-bearing.
            // `ENGINE-LIMITS.md` C1 carries the number.
            val cd0 = classes.find(_.symbol == s)
            val demoted = cd0.map(plan0(program, _, synthesis = false))
              .filter(q => q.primary.isDefined && !paramfulPrimary(q))
              .orElse(cd0.flatMap(nilaryPlan))
              .getOrElse(Plan.none)
            acc = acc.updated(s, demoted)
            changed = true
        }
      // A1 — `val` OR `var`, decided LAST because it is the one part of a slot that is not a local
      // function of the class. A slot is written once per construction path, from a parameter, in
      // the primary; that is `val`-eligibility exactly when NOTHING ELSE in the program writes the
      // field. `DESIGN.md` §8.2's correction is what this separation is: slot-eligibility and
      // `val`-eligibility are two conditions, and reading them as one demoted every settered field
      // out of the slot set for no semantic reason.
      //
      // Narrowed to fields java declared `final` or `private`, and the WIDE version — write count
      // alone — was built and MEASURED: **gdx-gltf 7 -> 23 compile errors**, every one `E052
      // Reassignment to val`, on libGDX core fields (`ShaderProgram.vertexShader`,
      // `PBRFloatAttribute.value`) that gltf writes and libGDX core's own run therefore never saw
      // written. The write count is over THIS RUN's program; a dependent compiled against the
      // emitted base is not in it. That is `ENGINE-LIMITS.md` D4's shape exactly, and it is not
      // fixable by a bigger scan — the base run cannot see a module that does not exist yet.
      // `final` cannot be written after construction in java at all and `private` cannot be written
      // from outside the compilation this run holds, so neither can drift. libGDX core: 20 `val` on
      // the wide rule, 5 on this one; the 15 difference is exactly the class of field a dependent
      // may legitimately assign.
      //
      // …and LAST, the contract. Every class the fixpoint above just declined to touch is one this
      // run does not emit, and the module that DOES emit it published what it emitted.
      hosting(reconciled(acc))

    /** Give every plan with NO PRIMARY OF ITS OWN the context clause its class's constructors carry
      * (`ENGINE-LIMITS.md` CT5). A promoted plan already has one (from the constructor it promotes)
      * and a synthesised one already has one (from its roots); this is the third outcome, where
      * scala's implicit nilary primary would carry none and the class body would have no given in
      * scope.
      *
      * ONE POST-PASS RATHER THAN A BRANCH AT EACH NOMINATION, because "no primary" is reached five
      * ways — `plan0`'s trait/module/enum guard, its two `case None` arms, `syntheticPrimary`'s
      * "nothing to synthesise" refusal, and the withholding fixpoint's `Plan.none` fallback — and a
      * clause missing from any one of them is a class that compiles with its threading silently
      * gone. It runs over EVERY class, owned or not, for the same reason the nomination does: a
      * dependent must derive for a base class exactly what the base emitted.
      *
      * A no-op by arithmetic for every port that threads nothing: [[classGivens]] is `Nil`, the map
      * is returned untouched, and no emitted byte moves. */
    private def hosting(acc: Map[SymId, Plan]): Map[SymId, Plan] =
      classes.foldLeft(acc) { (m, cd) =>
        val p = m.getOrElse(cd.symbol, Plan.none)
        if p.primary.isDefined || p.isSynthesised || p.givens.nonEmpty then m
        else
          classGivens(program, cd) match
            case Nil => m
            case gs  => m.updated(cd.symbol, p.copy(givens = gs))
      }

    /** Reconcile every NON-OWNED class's locally-derived plan against the base's published row.
      *
      * '''ONE implementation, not two''' (`DESIGN.md` §8.11): a dependent DERIVES the signature
      * locally for every non-wall class AND compares it against the `primary=` row wherever a row
      * exists — never one or the other by circumstance. The two halves differ only in what a
      * disagreement means, and the split is a provable property of the fixpoint rather than a
      * heuristic:
      *
      *   - a '''NON-WALL''' class is one the fixpoint's demotion predicate can never fire on
      *     (`!paramfulPrimary` or `reachableArgumentFree`). Adding units can only GROW `needNilary`,
      *     so such a class's plan is INVARIANT under extra subclasses and the local derivation is
      *     necessarily the base's answer — while both modules run the same engine. The row is
      *     therefore a CROSS-CHECK, and a disagreement is an '''engine bug''': the local derivation
      *     is only "the same answer the base got" because the versions match, and an engine upgrade
      *     between the base's run and this one is precisely the drift a purely local derivation
      *     cannot see. (The header's engine fingerprint says the versions differ; only this
      *     comparison says whether the difference changed a signature.) It is FATAL.
      *   - a '''WALL''' class is one the fixpoint CAN demote, so its answer is a function of the set
      *     of subclasses and there is nothing to derive. The row is LOAD-BEARING and this run reads
      *     it: where the row disagrees, the run takes the demotion the base took, and where no
      *     demotion this run can express reproduces the row, the gap is FATAL rather than silently
      *     re-derived — falling back is exactly how D4 shipped.
      *
      * What is NOT done here, deliberately: the base's plan is never DEMOTED to fit this module.
      * Where the contract says the base emitted a primary this module's subclass cannot reach, there
      * is no local repair — the base is emitted and gone — and the honest outcome is to refuse the
      * replay and count it (§8.3's honest-scope statement), never to rewrite the base's plan in a
      * run that does not emit the base. */
    private def reconciled(acc: Map[SymId, Plan]): Map[SymId, Plan] =
      // AN ENUM IS NOT THIS FILE'S TO ANSWER FOR, and the exclusion is structural rather than a
      // narrowing. `TirEmitter.enumDef` lowers a java enum directly — its primary IS the java
      // constructor, because every `case object` passes its arguments to it — and it consults the
      // funnel for nothing. So the funnel's plan for an enum is `Plan.none` while the contract row
      // records the constructor's real slots, and comparing the two compares two different
      // derivations. Measured before it was excluded: 5 FATAL cross-check failures on one dependent,
      // every one an enum (`Texture$TextureFilter` derived `()` against a published `(int)`), and
      // every one of them a false alarm.
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
                // §1.5 / D15: a dependent FOLLOWS the base's published constructor plan rather
                // than re-deriving it. The local derivation may read `(int,int)` where the base
                // published `(int,T)` because the dependent's retyping phases are deliberately NOT
                // allowed to re-derive what the base already published over base units (D12, O8
                // wave 2.11 — `coerceArgs` reads a base callee's formals off the published port
                // map for exactly this reason). The published row is the truth.
                //
                // Try to find a local plan whose rendered primary matches the published one. For a
                // WALL class this is the seeded demotion (the fixpoint demoted the base's
                // promotion and the published row records what it became). For a non-wall class the
                // same fallback may match when the descriptor difference is structural (a
                // synthesis the dependent did not derive). Where it does, adopt it.
                seededDemotion(cd, there.get) match
                  case Some(p) => out = out.updated(cd.symbol, p)
                  case scala.None =>
                    // D15: the dependent does NOT emit this class (it is non-owned), so the
                    // plan's content does not reach emitted text. The fixpoint's withholding
                    // decision depends on whether the plan is PARAMFUL, and the local plan
                    // correctly knows that from the Java. The descriptor disagreement is expected
                    // when the base's retyping/opaque phases renamed parameter types and the
                    // dependent did not re-derive (D12, O8). The dependent follows the base's
                    // published signature at every call site through `coerceArgs` /
                    // `baseMemberUpstream`. Non-fatal.
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
              // An `Unknown` about a class whose plan is INVARIANT is not a failure: the local
              // derivation is the base's answer and the contract would only have confirmed it. One
              // about a WALL class is, because the answer shaped the emitted `extends` clause and
              // the only alternative is the fallback that produced D4.
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

    /** the primary's slots as the CONTRACT spells them — §8.1's descriptor grammar, so the
      * comparison is between two renderings of one grammar and never between a rendering and a
      * parse. Kept beside the plan rather than in the emitter because the cross-check happens here,
      * before anything renders. */
    private def renderedPrimary(cd: Tree.ClassDef, p: Plan): String =
      def param(t: TypeRepr): Param =
        Descriptor.ofInfo(program, TypeRepr.MethodType(List("_" -> t), TypeRepr.NoType))
          .flatMap(_.params.headOption).getOrElse(Param.Unresolved)
      val slots =
        if p.isSynthesised then p.synthetic.map((_, t) => param(t)) ++ p.marker.map(_ => Param.Unresolved).toList
        else p.primaryParams.map(v => param(v.tpt.tpe))
      Descriptor(slots).render

    /** The plan this class would have had under the demotion the CONTRACT records — the same ordered
      * fallback the fixpoint uses, filtered to the one whose signature matches the published row.
      *
      * `scala.None` when none of them does, which is reported rather than approximated: a plan that
      * merely has the right ARITY is not the right plan, and picking one on arity is exactly the
      * guess §4.56 forbids. */
    private def seededDemotion(cd: Tree.ClassDef, published: String): Option[Plan] =
      val candidates =
        plan0(program, cd) :: plan0(program, cd, synthesis = false) :: nilaryPlan(cd).toList ::: List(Plan.none)
      candidates.find(p => renderedPrimary(cd, p) == published)

    /** how many times the WHOLE program writes each field. Built once — the question is asked per
      * field slot and the answer is a property of the program, not of the class. */
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

    /** the plans with A1's `val`/`var` decided — LAST, and after [[replays]], which is the whole
      * reason it is a separate pass.
      *
      * A REPLAY writes fields the java program does not: it lifts a parent constructor's statements
      * into a subclass, so a parent field the funnel hoisted into a slot and saw written exactly
      * once is written again, once per replaying subclass, in code no source scan can see. Ashley's
      * `EntitySystemMock.updates` is java-`private` and assigned by one constructor, which is
      * `val`-eligible by every test over the java — and `EntitySystemMockA`/`B` replay `super(updates)`
      * as `this.updates = updates`. Measured: **0 -> 4 `E052 Reassignment to val`** with the decision
      * taken before the replays were known. So the count is java's writes PLUS the emission's own. */
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

    /** WHICH of the shapes in this file's header the plan for `cd` is, by name — a READ-ONLY view,
      * derived from the plan value and the class's own constructors. Nothing here decides anything.
      *
      * It exists for the DECISION channel: `Plan` says WHAT was nominated and an agent diffing the
      * emitted class against its Java needs to know WHY a constructor became the class's parameter
      * list, which of six situations that was, and therefore what would change it. The names are
      * the header's, so the row and the specification read the same.
      *
      * Derived rather than recorded at planning time on purpose — recording it would mean a second
      * value to keep in step with a nomination that has been measured three orderings deep, and
      * every shape is already distinguishable from the plan plus `ctorsOf`/`superArgsOf`.
      */
    def shape(cd: Tree.ClassDef): String =
      val p     = apply(cd)
      val ctors = ctorsOf(program, cd.body)
      val roots = ctors.filterNot(delegatesToThis(program, _))
      // A JDK-THROWABLE PARENT IS THE PADDED SHAPE, and the equivalence is structural rather than a
      // recorded flag: `plan0` withholds the ordinary synthesis for such a parent outright, so a
      // synthesised plan under one can only have come through `throwablePadding`. Named apart
      // because the two differ in what a reader may conclude — a uniform synthesis reproduces every
      // root's call exactly, while this one reproduces the JDK's own narrower-overload delegation
      // and carries C3's `()`-versus-`(null, null)` residue with it.
      if p.isSynthesised then
        if jdkThrowableParent(program, cd) then "padded-throwable-synthesis" else "synthesised-primary"
      else
        p.primary match
          case scala.None            => if ctors.isEmpty then "no-constructor" else "not-funnelled"
          case Some(_) if roots.sizeIs == 1 => "unique-root"
          case Some(c) if valueParams(program, c).isEmpty =>
            // several roots and a NILARY one chosen. The two shapes differ by whether anything had
            // a `super(args)` to lose: with none, the funnel was needed only to stop scala's
            // implicit nilary primary clashing with an emitted `def this()`.
            if ctors.exists(x => superArgsOf(program, x).nonEmpty) then "no-arg-root" else "promoted-nilary"
          case Some(_)               => "widest-root"

    /** the class's constructors as the funnel read them — read-only, for a caller that wants to
      * say how many there were without re-deriving `ctorsOf`'s notion of one. */
    def constructorsOf(cd: Tree.ClassDef): List[Tree.DefDef] = ctorsOf(program, cd.body)

    // ---- what the promotion COSTS: the promoted body on paths java never ran it ----

    /** The constructors of `cd` on whose path java did NOT run the promoted constructor's body —
      * and on which the emitted class therefore runs it anyway.
      *
      * A promoted constructor's body BECOMES the class body (`TirEmitter.lowerCtors` substitutes
      * `Plan.primaryBody` for the constructor), and a scala class body runs on EVERY construction
      * path, because every secondary constructor's first statement is a `this(...)` that reaches
      * the primary. Java had no such rule: two constructors that do not delegate to each other run
      * disjoint bodies. So promoting one of several roots runs its statements where java ran
      * nothing — `Base() { this.n = Audit.bump(); }` beside `Base(int n) { this.n = n; }` bumps on
      * `new Base(5)` in the port and does not in java.
      *
      * This is exactly the escaping-effect problem `ENGINE-LIMITS.md` C6 located here rather than
      * at [[supersedes]], and there is no third option at the promotion either: REFUSING measured
      * **0 -> 41 compile errors** on libGDX (every refused class then emits a `def this()` beside
      * scala's implicit nilary primary — `E120 Conflicting definitions`), so the honest outcome is
      * the counted one. [[OmissionCheck.promotedBodyOnEveryPath]] reports it, from this function,
      * so the count and the emission cannot disagree: both are `Plan.primaryBody`.
      *
      * Deliberately NOT a purity question about the body. Whether re-running it is observable
      * depends on what the other constructor overwrites, on what the callee touches, and on the
      * caller — 59 of libGDX's 771 promotions land here and most only waste an allocation, but
      * `Material` bumps a static id counter on every construction and `Button` adds a second
      * `ClickListener` to every button. The structural fact is the same in all of them, and it is
      * the one that can be computed. */
    def promotionEscapes(cd: Tree.ClassDef): List[Tree.DefDef] =
      val p = plans.getOrElse(cd.symbol, Plan.none)
      escapesOf(program, cd, p.primary, p.primaryBody)

    /** Does the emitted class have a primary this funnel gave PARAMETERS? The emitter asks it to
      * decide whether a `def this()` has room to be declared beside the primary, and a check asks it
      * to decide whether one was therefore dropped — one function, so the two cannot drift. */
    def paramfulPrimaryOf(cd: Tree.ClassDef): Boolean = paramfulPrimary(apply(cd))

    /** The class's own NILARY java constructor, where the port DROPS IT AND LOSES WHAT IT DID.
      *
      * A `Plan.none` (or promoted-nilary) class keeps scala's implicit nilary primary, so a
      * `def this()` beside it is `E120 Conflicting definitions` and the emitter drops every nilary
      * constructor in front of one ([[delegationOnlyNilary]]). For `C() { super(); }` that is exact:
      * the implicit primary IS that constructor. For `C() { this(seed(), "d"); }` it is not —
      * java ran the delegation, the emitted class runs nothing, and `new C()` builds an object java
      * could never build.
      *
      * There is no third option here, and each alternative was measured rather than assumed:
      *
      *   - EMIT it — `E120` at the declaration, `E051` at every argument-free `extends` (CT4/CT5);
      *   - PROMOTE it to the primary — its body becomes the class body, which runs on EVERY
      *     construction path, so `new BitmapFont(fontFile)` would also load the default face
      *     ([[promotionEscapes]], `ENGINE-LIMITS.md` C6/C7: 9 of libGDX `BitmapFont`'s 10 paths);
      *   - give the class a MARKER-disambiguated synthesised primary so the `def this()` is
      *     declarable — then a subclass's bare `extends C` resolves to that very `def this()` (the
      *     fact [[reachableArgumentFree]] is built on), so `new DistanceFieldFont(…)` would load the
      *     default face where today it loads nothing. A new wrong answer in place of a missing one.
      *
      * So the honest outcome is the counted one, exactly as for [[promotionEscapes]].
      * `OmissionCheck.droppedNilaryCtors` reports it, from this function.
      *
      * `None` when the class has no such constructor, when the primary is paramful (the `def this()`
      * is then declarable and IS emitted), or when the nilary constructor is itself the promoted
      * primary — its body is the class body and nothing is lost. */
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

    /** PREFIX STRIP — the statements of an escaping root that the promoted body does not ALREADY
      * run, when its own body literally begins with the promoted body.
      *
      * `Button()` is `{ initialize(); }` and `Button(Skin)` is `{ initialize(); setSkin(skin); }`:
      * the second does not delegate to the first, so promoting the first runs `initialize()` where
      * java ran it once — a SECOND `ClickListener` on eight of ten construction paths, and every
      * click then calling `setChecked` twice (`ENGINE-LIMITS.md` C7). Because the promoted body is
      * a literal PREFIX of this one, deleting the duplicate from the secondary is not an
      * approximation: the class body runs the prefix, `this(…)` returns, and the residual runs —
      * the same statements, in the same order, once each.
      *
      * `None` when the constructor does not escape, or when its body does NOT begin with the
      * promoted body. That is what makes this one function rather than two: [[promotionEscapes]]
      * subtracts exactly what this returns, so the check and `TirEmitter.ctorBody` cannot disagree
      * about which paths still duplicate — the failure mode C7 warns about at
      * `OmissionCheck.droppedSuperArgs`, in its other form.
      *
      * Compared through the CANONICAL rendering, never by tree equality: two occurrences of
      * `initialize()` are two source positions, so `==` on `Statement` is false for every pair this
      * is meant to find. `TirPrinter.Style.canonical` elides origins, `SymId`s and trivia, which is
      * exactly "the same statement, written twice".
      *
      * The head DELEGATION is excluded from the comparison for the same reason the emitter excludes
      * it: an escaping root may still begin with `super(args)`, which is lifted or dropped
      * elsewhere and is not part of the body being duplicated. */
    def residualBody(cd: Tree.ClassDef, d: Tree.DefDef): Option[List[Statement]] =
      val p = plans.getOrElse(cd.symbol, Plan.none)
      residualBodyOf(program, cd, p.primary, p.primaryBody, d)

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
      // `superSlots`, NOT `synthetic.size`: the question is whether this root's `super(args)` fills
      // the parent's formals, and the FIELD slots that follow them are not part of it.
      if plan.isSynthesised then
        if args.sizeIs == plan.superSlots then SuperCall.Positional(args) else SuperCall.Dropped
      else
        val ps = plan.primaryParams
        // THE COLLAPSE IS POSITIONAL, and the type-matched fill is simply the wrong question for it.
        // A collapse promotes a root whose own parameters ARE the parent constructor's formals,
        // passed straight through in order, and the class was only a collapse candidate because
        // EVERY root reaches that one parent constructor — so every sibling's `super(args)` fills
        // those same formals in that same order, and `this(args)` is type-correct by construction.
        //
        // Left to the fill, `Mixed(String s) { super(s, 7); }` beside a promoted
        // `Mixed(Object a, int b)` lost BOTH arguments: `String` is not `Object` by head name, so
        // the first slot found no home, and the parent was constructed with neither. It compiled,
        // and a spec pinned it as correct. Attempt order is FILL then this, not the other way
        // round: for a JDK throwable the fill is what supplies the padded slot the narrower
        // overload would have left (`Slot.NullAt`, `Slot.CauseMessage`), and no throwable-parent
        // class is a collapse anyway — `plan0` never consults the synthesis for one.
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
      val plan = apply(cd)
      // `.map(_.symbol).contains` — NOT `primary.contains(d.symbol)`, which compiles (both widen to
      // `Any`) and is always FALSE. It reported the promoted primary of 24 libGDX classes as having
      // lost the arguments that were sitting in their `extends` clause.
      //
      // …and `Plan.delegations` beside `superCall`, for the same one-answer reason. A root with a
      // delegation entry is one `TirEmitter.ctorBody` renders as `this(<that entry>)` WITHOUT
      // consulting `superCall` at all, and the entry begins with this root's own super arguments,
      // placed in the primary's slots by the nomination. Left out, a PADDED root — whose java call
      // is narrower than the slots it fills — reads as dropped in the check while its arguments sit
      // in the emitted delegation, which is exactly the emitter-vs-check disagreement this file
      // spent a whole rewrite removing. It adds nothing for a uniform synthesis, where `superCall`
      // already answers `Positional` for every root.
      args.isEmpty || plan.primary.map(_.symbol).contains(d.symbol) || replayFor(cd, d).isDefined ||
        plan.delegations.contains(d.symbol) || superCall(cd, args) != SuperCall.Dropped

    /** members a replay reaches that are `private` where they are declared. The replay executes
      * one level down, in the subclass, so they must be widened — which is compile-safe and
      * cannot change behaviour (the hand-ported corpus widens the same way).
      *
      * This is the population this run OBSERVED. The other one — the replays a subclass in a LATER
      * run will make of a constructor this run is emitting — is `externalReplayWidenings`, kept
      * apart because only the first can name the subclass it is about (`ENGINE-LIMITS.md` C15). */
    def widenedMembers: Set[SymId] = widened.toSet

    private val widened = collection.mutable.Set[SymId]()

    /** THE SAME WIDENING, FOR A SUBCLASS THIS RUN CANNOT SEE — `ENGINE-LIMITS.md` C15.
      *
      * `widened` above is derived from `replays`, which is derived from the SUBCLASSES IN THIS
      * PROGRAM. That makes the answer depend on which run you are in, and §1.5 is explicit that this
      * is the failure mode: a subclass in a dependent module replays the very same parent
      * constructor, `reachablePrivate` reads the base's PUBLISHED `vis=private`, and the replay is
      * refused — so the two modules disagree about a constructor neither of them wrote. Measured on
      * `flexmark-ext-gfm-tasklist`: every super call to a copy constructor dropped WHOLE (the refusal
      * is at the constructor, not at the statement), at 0 compile errors and no moved test.
      *
      * The set is a fact about THIS class's own declarations and involves no guess about who will
      * subclass it: a paramful constructor a subclass may reach is one whose statements a subclass's
      * SECONDARY constructor can only express as a replay (scala lets only the primary reach super),
      * and every `private` those statements touch is a member that replay must reach. So the base
      * widens what its own constructors ask for, exactly as it already does for the subclasses it
      * happens to contain, and a dependent's `reachablePrivate` then finds the member `public` in
      * the published surface with nothing new to ask.
      *
      * SEVEN narrowings, each a fact rather than a budget — a widening nobody needs is emitted
      * surface this port did not have to move (`CLAUDE.md` §5's over-approximation rule):
      *
      *   - only classes THIS RUN EMITS. Widening a base's symbol in a dependent's own table is
      *     `ENGINE-LIMITS.md` D5 exactly — the declaration is in a file this run does not write, so
      *     the emitted call names a member that is still `private` where it lives;
      *   - only a class a subclass can reach at all: not `final`, not an `enum`, not an annotation,
      *     and a TYPE MEMBER or top-level rather than a method-local class (JLS 14.3 — nothing
      *     outside the block can name it, so nothing outside can extend it) — `extensibleFromOutside`;
      *   - only a NON-PRIVATE constructor: java forbids `super(...)` to a private one, so no
      *     subclass anywhere can reach it;
      *   - only a PARAMFUL constructor. `replays` fires on `superApply`, which requires
      *     `args.nonEmpty`; a nilary `super()` is what the emitted `extends P` (or a secondary's
      *     `this()`) already runs, and needs no replay and no access;
      *   - only where a replay of those statements could be `usable` AT ALL. A body holding a
      *     `super.m()` or a `return` is refused one level down whatever it can see, so widening for
      *     it buys nothing;
      *   - only a member SCALA would refuse one level down, which is not `isPrivate` —
      *     `unreachableFromASubclass`;
      *   - only a FIELD (`isField`) and only a MUTABLE one (`immutableSlotFields`): the first
      *     because a method's visibility is half of an override contract, the second because a slot
      *     bound as a `val` is one no replay could assign whatever it can see. Each of those two
      *     cost a measured compile error before it was written down.
      *
      * What it deliberately does NOT ask is who the subclass is, and that is the whole point: the
      * base cannot know, and an answer that waits to find out is the answer that was wrong. */
    def externalReplayWidenings: Set[SymId] = externalReplay

    // LAZY, and it is an initialisation-order fact rather than a preference: this val stands
    // ahead of `replays` in the file and reads `decided`, which reads `replays`. Strict, it
    // forces that chain during construction and the answer is a `NullPointerException`.
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
                // …AND THE MEMBER ITSELF IS ONE THIS RUN WRITES, which the class gate does not
                // imply: the chain climbs into ANCESTOR constructors, and a dependent's own class
                // may inherit from a base's. Widening one of those edits this run's symbol table
                // and NOT the file that declares it — `ENGINE-LIMITS.md` D5, in its own words. A
                // base's private stays refused here and is answered where it can be, in the base's
                // own run of this same pass.
                out ++= touched.collect {
                  case (s, viaPrefix)
                    if isField(s) && !immutableSlotFields(s) && program.symbolOf(s)
                      .exists(sy => surface.owns(sy.owner) && unreachableFromASubclass(sy, viaPrefix)) => s
                }
          }
      }
      out.toSet

    /** Can a replay landing in a SUBCLASS THE RUN CANNOT SEE reach this member under SCALA's access
      * rules? The question is emphatically NOT `isPrivate`, and each of the other two answers cost a
      * measured error before it was written down:
      *
      *   - `private` — never reachable from a subclass, however it is accessed;
      *   - PACKAGE-PRIVATE — java's fourth level, emitted `private[pkg]`. An unknown subclass is in
      *     an unknown package, so neither `this.f` nor `other.f` reaches it;
      *   - `protected` — reachable as `this.f`, and NOT reachable through a prefix whose type is the
      *     DECLARING class. Scala requires such a prefix to conform to the ACCESSING class, and java
      *     agrees for its own code (JLS 6.6.2.1) — which is exactly why this only ever bites a
      *     REPLAY: java wrote `other.f` inside the declaring class, where the rule does not apply,
      *     and the replay moved that statement into a subclass, where it does. Measured as 2 ×
      *     `variable openingMarker cannot be accessed as a member of (block : ListItem)` on the
      *     first module the widening let through (`ENGINE-LIMITS.md` C15).
      *
      * `viaPrefix` is therefore a fact about the STATEMENTS and not about the symbol, which is why
      * `replayReach` carries it per member rather than returning a bare set. */
    private def unreachableFromASubclass(sy: Symbol, viaPrefix: Boolean): Boolean =
      sy.flags.isPrivate || sy.flags.isPackagePrivate || (sy.flags.isProtected && viaPrefix)

    /** …AND A WIDENING IS ADMISSIBLE ONLY WHERE IT CANNOT PARTICIPATE IN AN OVERRIDE, which is the
      * line between a FIELD and a METHOD and not a matter of degree.
      *
      * A field is overridden by nothing, so widening one is a closed act: no other declaration in
      * this module, and none in any other, has to move with it. A method's visibility is half of an
      * override CONTRACT — scala refuses an override that is narrower than what it overrides — so
      * widening one obliges every override BELOW it to widen too, and the overrides below it in a
      * DEPENDENT are declarations this run cannot reach at all. Measured as 2 × `E164 error
      * overriding method setStage in class Group` on libGDX the first time methods were included:
      * a `protected` method a constructor calls through a prefix, widened in the parent and left
      * alone in two subclasses that override it (`ENGINE-LIMITS.md` C15).
      *
      * So a replay that touches a `private` or prefix-selected `protected` METHOD stays REFUSED and
      * stays counted by `OmissionCheck`, which is the answer C3 already gives for everything this
      * mechanism cannot express. */
    private def isField(s: SymId): Boolean =
      program.definitionOf(s).exists(_.isInstanceOf[Tree.ValDef])

    /** …AND NOT A FIELD THIS CLASS BINDS AS AN IMMUTABLE SLOT, which is the same rule read on the
      * MUTABILITY axis instead of the visibility one.
      *
      * `decided`'s A1 gate binds a field slot as a `val` when the field is java-`final` or
      * java-`private` AND the program writes it no more often than the slot does — and the
      * `private` half of that is exactly the argument *nothing outside this compilation can write
      * it*. A widening falsifies the argument it rests on: the field becomes public, a dependent's
      * replay is then admitted by `reachablePrivate`, and the emitted `this.f = …` one level down
      * is `E052 Reassignment to val`. The `final` half is worse only in that java agrees — a
      * subclass cannot assign a `final` field of its parent in either language, so a replay that
      * writes one is inexpressible whatever it can see.
      *
      * Either way the widening BUYS NOTHING and removes an honest refusal, so the field is left
      * alone and the replay stays counted by `OmissionCheck`. Note the direction of the dependency:
      * A1 already counts the replays THIS RUN makes (`byReplay`, `ENGINE-LIMITS.md` C1.6's
      * `0 -> 4 E052`), and this is the same question about a run that has not happened yet. */
    private lazy val immutableSlotFields: Set[SymId] =
      decided.values.flatMap(_.fieldSlots).filterNot(_.mutable).map(_.field).toSet

    /** Can anything OUTSIDE this run extend `cd` and therefore replay one of its constructors?
      *
      * Structural throughout (§4.56): a `final` class has no subclass, an `enum` and an `@interface`
      * cannot be extended by a declaration, and a class that is neither top-level nor a member of a
      * type this program declares is standing in a BLOCK — a method-local or anonymous class, which
      * nothing outside that block can name. Read through `classDecls` rather than a name test for
      * the same reason `Visibility.decide` does. */
    private def extensibleFromOutside(cd: Tree.ClassDef): Boolean =
      program.symbolOf(cd.symbol).exists { s =>
        !s.flags.isFinal && !s.flags.isEnum && !s.flags.isAnnotation && !s.flags.isModule &&
          (unitSymbols(cd.symbol) || classSymbols(s.owner))
      }

    private lazy val classSymbols: Set[SymId] = classes.map(_.symbol).toSet
    private lazy val unitSymbols: Set[SymId]  = program.units.map(_.symbol).toSet

    /** The statements an external subclass's replay of `<ctor>` would run, as (is a replay even
      * CONCEIVABLE here, every symbol they touch).
      *
      * The chain is inlined exactly as `effects` inlines it — head delegation dropped, an
      * ARGUMENT-FREE head not recursed into (the emitted `this()` already ran it, which is
      * `Effects.deferredTo`) — because the two must not disagree about what a replay IS. What it
      * does NOT do is substitute: the arguments belong to a caller this run does not have, and no
      * substitution changes WHICH of the class's own members the body touches. The first component
      * is `usable`'s two structural refusals asked ahead of time: a `super.m()` dispatches one
      * level too high once replayed and a `return` leaves the wrong frame, so such a constructor is
      * refused wherever it lands and widening for it would be a modifier nobody can use. */
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
            /** a qualifier that is NOT this class's own instance — the shape scala refuses for a
              * `protected` member one level down. */
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
                  // a SELECTION carries the one fact `unreachableFromASubclass` cannot read off the
                  // symbol: `this.f` is a subclass's own access and `other.f` is a prefix whose type
                  // is the declaring class, which scala refuses one level down.
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
        // `this()` must run nothing beyond the parent's nilary construction: the class's own
        // primary contributes no statements and passes no super arguments of its own.
        val p = plans.getOrElse(cd.symbol, Plan.none)
        // everything `this()` runs: the class's own promoted primary, and whatever IT reaches.
        //
        // A SYNTHESISED primary is refused outright, and this is a correctness guard rather than an
        // optimisation. A replay is emitted AFTER the delegation the emitter renders, and against a
        // synthesised primary that delegation is `this(<the very arguments of this super call>)` —
        // which reaches the parent constructor through the `extends` clause. Replaying the same
        // constructor's statements on top would run the parent's body TWICE, and nothing else in the
        // pipeline could see it: the class compiles, `superExpressed` is true either way, and only a
        // running test would notice. Every root of a synthesised class is expressed positionally by
        // construction (same target, same arity), so the replay has nothing to add in the first
        // place.
        val prologue =
          if paramfulPrimary(p) then scala.None else prologueOf(cd.symbol, 0)
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
    private def superApply(d: Tree.DefDef): Option[(SymId, List[Term])] = headStmt(d) match
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
            // A SYNTHESISED primary is paramful, and `extends P` with no arguments therefore reaches
            // P's NILARY SECONDARY — which is java's own `P()`, unchanged. So what the emitted
            // `class C extends P` runs on the way in is exactly what java's `new P()` ran, and
            // `effects` already computes that for any constructor, its `super(...)` chain inlined.
            //
            // Getting this wrong is not a missed optimisation. `prologueOf` is what `replayFor`
            // stands on, replay expresses 73 of the 82 WALL classes, and the widened synthesis makes
            // far more PARENTS paramful — so answering `None` here for every synthesised parent
            // traded 45 escaping bodies for 49 dropped `super(args)` on libGDX core, which is the
            // trade `DESIGN.md` §8.2 names as the one that must not be made.
            if p.isSynthesised then
              ctorsOf(program, cd.body).find(valueParams(program, _).isEmpty)
                .flatMap(n => effects(n.symbol, Nil, 0)).map(_.stats)
            // a PROMOTED paramful primary means `extends P` with no arguments reaches a nilary
            // SECONDARY that the promotion CONSUMED — there is none, and this is not "P's promoted
            // primary plus what it reaches", which is the only thing the branch below can compute.
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

    /** Every field this statement CAN write — and `None` the moment it does anything ELSE.
      *
      * [[supersedes]] asks two questions of two different statement lists and they are not the same
      * question, which is what a single [[assignedField]] could not say. On the PROLOGUE side the
      * question is *what did `this()` put into the object*, so the answer must be an over-estimate:
      * every field any path through the statement may have written. On the REPLAY side it is *what
      * does the replay definitely overwrite*, which is [[mustAssign]] and an under-estimate. Both
      * directions are conservative and the pair is what makes the widening below sound.
      *
      * The BRANCH is the shape this exists for, and it is ordinary rather than exotic: a java
      * constructor that normalises one argument — `if (other == null) f = new HashMap<>(); else f =
      * new HashMap<>(other.getAll());` — is ONE `Tree.If` statement, so [[assignedField]] answers
      * `None`, `None.exists` is false, and the replay is refused for a parent whose whole body is
      * that one `if`. Nothing reports it: the emitted secondary is `this()` and its `super(args)`
      * is dropped, `OmissionCheck.droppedSuperArgs` counts it honestly as a refusal, the port
      * compiles, and the constructed object is simply empty. Measured on flexmark's
      * `MutableDataSet(DataHolder)` — three classes deep, so `HtmlRenderer.builder(options)` built
      * a renderer with NO options at all and every non-default one the caller set was silently the
      * default (`PROGRESS.md` §10.6.7).
      *
      * A statement that is not a field write, a branch, a block or a no-op answers `None` — the
      * refusal, unchanged. This is deliberately NOT "does the statement look pure": the right-hand
      * sides are not read here for the reason [[supersedes]] states at length, and a condition is
      * not read for the same reason. */
    private def mayAssign(st: Statement): Option[Set[SymId]] = st match
      case Tree.Commented(_, s)          => mayAssign(s)
      case Tree.If(_, t, e, _, _)        => mayAssign(t).zip(mayAssign(e)).map(_ ++ _)
      case Tree.Block(stats, expr, _, _, _) =>
        (stats :+ expr).foldLeft(Option(Set.empty[SymId]))((acc, s) => acc.zip(mayAssign(s)).map(_ ++ _))
      case Tree.Literal(Constant.UnitC, _, _) => Some(Set.empty)
      case _                             => assignedField(st).map(Set(_))

    /** Every field this statement writes on EVERY path through it — the MUST half of [[mayAssign]].
      *
      * An `if` contributes the INTERSECTION of its branches and never their union: a field written
      * in one arm only is not one the replay is guaranteed to overwrite, so counting it would let
      * [[supersedes]] answer true about a path on which the prologue's value survives. A sequence
      * contributes the union, which is exact. Anything unrecognised contributes nothing, which is
      * the conservative direction here. */
    private def mustAssign(st: Statement): Set[SymId] = st match
      case Tree.Commented(_, s)          => mustAssign(s)
      case Tree.If(_, t, e, _, _)        => mustAssign(t).intersect(mustAssign(e))
      case Tree.Block(stats, expr, _, _, _) => (stats :+ expr).flatMap(mustAssign).toSet
      case _                             => assignedField(st).toSet

    /** Does replaying `stats` after `prologue` leave the same STATE Java's `super(args)` left?
      *
      * State, and only state. `prologue` is what the secondary's own `this()` already ran, and this
      * asks whether the replay overwrites every field it assigned — then each of those fields ends
      * up holding what Java put there, and the prologue's contribution to the OBJECT is dead. A
      * prologue statement that is not a field write, a branch over field writes, a block of them or
      * a no-op already fails it, because [[mayAssign]] answers `None` and `None.exists` is false.
      *
      * The two sides are read through DIFFERENT functions and that is the whole of its soundness:
      * the prologue through [[mayAssign]], which over-estimates, and the replay through
      * [[mustAssign]], which under-estimates. Read through one function they would agree by
      * construction and the answer would be wrong on exactly the branching shape the pair was built
      * for — a field an `if` writes in one arm only is MAY on the prologue side and is not MUST on
      * the replay side.
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

    /** The parent's type PARAMETERS mapped to the arguments this class instantiates them with.
      *
      * A replay lifts the parent constructor's statements into the subclass, and those statements
      * are typed in the PARENT's scope: `class FlushablePoolClass extends FlushablePool[String]`
      * replayed `new Array[T](false, n)` verbatim, where `T` is `Pool`'s parameter and nothing in
      * the subclass can name it (`Not found: type T`). The instantiation is right there in the
      * `extends` clause, so the substitution is exact rather than a guess.
      *
      * THE DERIVATION ITSELF IS [[ParentSubst]] AND NOT THIS METHOD, because a replay was never the
      * only synthesiser that copies a parent's spelling into a subclass: the diamond forwarder and
      * [[syntheticPrimary]]'s own slot types do it too, and both were emitting the parent's `T`
      * verbatim while this one substituted (§4.56 — one derivation, not one per caller). */
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

    /** An inlined `null` ARGUMENT keeps the FORMAL's type; every other argument carries its own.
      *
      * `substituted` puts the argument term at each of the parameter's uses, which is exact for a
      * value whose type is a fact about the value. Java's `null` is not one: JLS 4.1 gives it the
      * NULL TYPE and 5.2 assigns it the type of the slot it is written at, so the parameter's
      * declaration is where `this(null)`'s type lives — and scala's `null` is a value of `Null`,
      * which has no members. Inlined bare, `DataSet() { this(null); }` promoted over
      * `DataSet(DataHolder other) { … other.getAll() … }` emits `null.getAll()`: `Found: Null`, in a
      * branch java never took, which scalac type-checks all the same.
      *
      * Always, rather than only where a use would fail — which use fails is a whole-body question
      * (a selection, a type-variable slot, an overload), and answering it partially is §4.6's
      * fabricated fact in the direction that compiles. What IS narrowed is the one place the
      * ascription could name something this scope cannot write: a type mentioning a variable the
      * CONSTRUCTOR declares (java permits `<T> C(T x)`) is left alone, because the promoted body
      * lands in the class body where the constructor's own frame is gone. */
    private def nullAtFormal(ctor: SymId, p: Tree.ValDef, a: Term): Term = a match
      case lit @ Tree.Literal(Constant.NullC, _, o)
        if p.tpt.tpe != TypeRepr.NoType && !ctorScoped(ctor, p.tpt.tpe) && !statesNull(p.tpt.tpe) =>
        Tree.Typed(lit, p.tpt, p.tpt.tpe, o)
      case _ => a

    /** a `T | scala.Null` STATES its own default, so the ascription there is not merely redundant —
      * it is the placeholder cast the nullability union was introduced to RETIRE (C10, and
      * `TirEmitter.defaultFor` says the same at a field). */
    private def statesNull(t: TypeRepr): Boolean = t match
      case TypeRepr.OrType(_, TypeRepr.TypeRef(_, s)) => program.symbolOf(s).exists(_.fullName == "scala.Null")
      case TypeRepr.OrType(TypeRepr.TypeRef(_, s), _) => program.symbolOf(s).exists(_.fullName == "scala.Null")
      case _                                          => false

    /** does this type mention a symbol the CONSTRUCTOR owns — its own `<T>`? Walked with
      * `StandardTraversal.mapType`, so it is total over `TypeRepr` by construction (§3). */
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
          val ps   = valueParams(program, d)
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
            val body = substituted(stms, ps.zip(args).map((p, a) => p.symbol -> nullAtFormal(ctor, p, a)).toMap)
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
              !sy.flags.isPrivate || sy.owner == cd.symbol || reachablePrivate(cd, s, sy)
            }
          }

    /** May this replay reach `sy`, a `private` member of a class that is NOT the one the statements
      * land in? `ENGINE-LIMITS.md` D5, and the whole of it.
      *
      * WITHIN one module the answer is yes-by-widening: the planner collects the symbol in
      * [[widenedMembers]] and `TirEmitter.widen` drops `private` from it before anything renders.
      * That is exact and cannot change behaviour — the declaration this run is about to write is the
      * one being widened.
      *
      * ACROSS a module boundary it is not a widening at all. `widen` edits the SYMBOL TABLE of this
      * run's `Program`; the DECLARATION lives in the base's already-emitted file, which still says
      * `private`, and the dependent emits a call to it and the compile fails — measured on gdx-gltf
      * as 4 × `value copyNodes is not a member of …ModelInstanceHack`. The test was
      * `classOfSym(sy.owner).isDefined`, "do we have a tree for the owner", which is TRUE for every
      * base class a dependent resolves against: a dependent's `Program` CONTAINS its base.
      *
      * So the question is asked of the SURFACE, not of the symbol table:
      *
      *   - the owner is one this run EMITS — the widening is real, and the old answer stands;
      *   - the base PUBLISHED the member and it is not `private` there — reachable, no widening
      *     needed;
      *   - the base published it `private` (or `private[p]`), or published nothing about it — the
      *     replay is REFUSED. `super(args)` is then dropped, `OmissionCheck.droppedSuperArgs`
      *     counts it, and the port compiles with a named divergence instead of failing (M6/C3).
      *
      * The refusal is RECORDED as a non-fatal [[Surface.Gap]] either way, because a refusal an agent
      * cannot attribute is §4.45's "cannot classify" failure: only the BASE can fix this, and the gap
      * names it. Non-fatal on purpose — the answer did not shape emitted text, it withheld a rewrite,
      * which is exactly what a `Gap.fatal` must not be widened to cover. */
    private def reachablePrivate(cd: Tree.ClassDef, s: SymId, sy: Symbol): Boolean =
      if surface.owns(sy.owner) then classOfSym(sy.owner).isDefined
      else
        // …and the GAP is scoped to the classes THIS RUN EMITS (`ENGINE-LIMITS.md` D2). A
        // dependent's `Plans` decides about its base's classes too — the fixpoint spans the whole
        // program — so a refusal about `Table`/`Button` is a question about text the BASE wrote and
        // this run does not touch. Recording it puts the module's own questions in a minority in its
        // own report; the REFUSAL still stands there, and costs nothing, because nothing is emitted.
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

  /** a term that may be substituted for a parameter: evaluating it twice is evaluating it once,
    * so inlining it at each of the parameter's uses preserves Java's evaluate-arguments-once.
    *
    * At OBJECT level rather than inside [[Plans]] because the nomination asks it too: the JDK's
    * `Throwable(Throwable)` message is rendered by naming the cause in BOTH slots, so a cause that
    * cannot be read twice is what makes [[throwablePadding]] refuse. One predicate, so the
    * nomination and the delegation cannot disagree about which causes are re-readable. */
  private def simple(t: Term): Boolean = t match
    case _: Tree.Ident | _: Tree.Literal | _: Tree.This => true
    case Tree.Select(q, _, _, _)                        => simple(q)
    case Tree.Typed(e, _, _, _)                         => simple(e)
    case Tree.ArrayLength(a, _, _)                      => simple(a)
    case _                                              => false

  /** does this class extend a JDK THROWABLE? Its constructor set is fixed and public — `()`,
    * `(String)`, `(String, Throwable)`, `(Throwable)` — which is what makes null-padding a shorter
    * super call exact rather than a guess. A java fact, not a library one. */
  private def jdkThrowableParent(program: Program, cd: Tree.ClassDef): Boolean =
    cd.parents.headOption.flatMap {
      case tt: TypeTree => headName(program, tt.tpe)
      case t: Term      => headName(program, t.tpe)
    }.exists(n => n == "java.lang.Throwable" ||
                  (n.startsWith("java.") && (n.endsWith("Exception") || n.endsWith("Error"))))

  /** a constructor symbol's own FORMALS — the parameter types the class file declares, never the
    * types of one call's arguments. An argument may be a SUBTYPE of the formal, which is exactly
    * the difference that decides [[throwablePadding]]. */
  private def formalsOf(program: Program, target: SymId): List[TypeRepr] =
    program.symbolOf(target).map(_.info).collect {
      case TypeRepr.MethodType(ps, _, _)                       => ps.map(_._2)
      case TypeRepr.PolyType(_, TypeRepr.MethodType(ps, _, _)) => ps.map(_._2)
    }.getOrElse(Nil)

  /** WHICH of the JDK throwable's four constructors a root's `super(...)` reached.
    *
    * The set is `()`, `(String)`, `(String, Throwable)`, `(Throwable)` and it is FIXED — that is
    * the whole of what makes padding exact here and a guess everywhere else (`ENGINE-LIMITS.md`
    * C3: 0 -> 55 when it was guessed outside the family).
    *
    * Read off the TARGET CONSTRUCTOR's formals, and that is the load-bearing part. Read off the
    * ARGUMENTS instead, `super(createMessage(e), e)` with `e` a `RecognitionException` looks like
    * no JDK overload at all — its second argument's head name is not `java.lang.Throwable` — and
    * the fill that decides by head name declines it, dropping BOTH arguments. Java already
    * resolved the overload; the target symbol is where that answer is written down.
    *
    * `None` for anything the four do not cover, including a parent whose class file this run could
    * not read (no formals at all). Refusing is the answer that keeps the counted omission. */
  private enum ThrowableCtor:
    case Nilary, Message, Cause, Both

  private def throwableCtor(program: Program, target: SymId, args: List[Term]): Option[ThrowableCtor] =
    def is(t: TypeRepr, n: String) = headName(program, t).contains(n)
    // an EMPTY argument list is java's implicit `super()` or an explicit one — the nilary overload
    // either way, and `superTarget` reports `SymId.None` for both, so there is no formal to read.
    if args.isEmpty then Some(ThrowableCtor.Nilary)
    else formalsOf(program, target) match
      case f :: Nil if is(f, "java.lang.String")    => Some(ThrowableCtor.Message)
      case f :: Nil if is(f, "java.lang.Throwable") => Some(ThrowableCtor.Cause)
      case a :: b :: Nil if is(a, "java.lang.String") && is(b, "java.lang.Throwable") =>
        Some(ThrowableCtor.Both)
      case _ => scala.None

  /** Every root's `super(args)` expressed at the JDK throwable's WIDEST overload — `ENGINE-LIMITS.md`
    * C3's missing shape, and the one thing §4.4's "promote the widest super call" cannot mean
    * "promote a root", because here no root IS the widest.
    *
    * Returns the widest overload's own constructor symbol (so the synthesised primary's parameter
    * types come from the JDK's signature rather than from any call) together with the padded
    * argument list for each root. Each shorter overload is padded the way the JDK's own delegation
    * pads it, which is what makes this a translation rather than an approximation:
    *
    *   - `(String)`      — the cause position really is left unset, so `null`;
    *   - `()`            — both, which is the one residue this shape carries: java leaves the cause
    *                       UNSET and `(null, null)` sets it to null, so a later `initCause` behaves
    *                       differently. Recorded in C3; the promoted throwable path has padded the
    *                       same way since it was built;
    *   - `(Throwable)`   — specified as `this(cause == null ? null : cause.toString(), cause)`, so
    *                       it fills its OWN message. `java.util.Objects.toString(c, null)` IS that
    *                       expression and evaluates `c` once — but the delegation then names the
    *                       cause in BOTH slots, and a scala secondary constructor cannot bind a
    *                       value before its `this(...)`. A cause that cannot be read twice refuses
    *                       the WHOLE synthesis rather than that one root: a synthesised primary is
    *                       paramful, so a root with no delegation of its own would emit `this()`
    *                       against it and not compile.
    *
    * `None` — refused, and the class keeps its counted omission — where any root reached something
    * outside the four, or where NO root reached the widest overload. That second one is the
    * boundary worth stating: the JDK really does declare `(String, Throwable)`, but if nothing in
    * the program calls it this run holds no symbol for it, and minting a call to a constructor
    * nobody named is precisely the guess §4.56 forbids. */
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

  /** Statements of a constructor body, block or not — and seen THROUGH a comment wrapper on the
    * BODY ITSELF, which is [[headStmt]]'s transparency one level further out.
    *
    * '''Every shape match in this file reads the body through this function, so this is the one
    * place the transparency can be given once.''' `Tree.Commented` is a TERM, so a comment written
    * above a constructor's first statement can arrive wrapped around the whole `rhs`; matched on
    * `Tree.Block` alone the body then reads as ONE opaque statement, and every question this file
    * asks of it answers wrongly at once — `delegatesToThis` says no, the funnel counts the
    * constructor as a root instead of a delegation, and [[delegationOnlyNilary]] reads it as an
    * empty body and DROPS IT. One comment was the whole difference, and no count moved.
    *
    * The wrapper's comment is not discarded with the wrapper: it was written above the body's FIRST
    * statement, so that is where it is re-attached (§4.58 — looking through a comment must never
    * delete it), using the carrier the statement list already has for exactly this. The one residue
    * is a body that is BOTH commented and empty, where there is no statement to carry it; the
    * emitter renders that body's term directly and never through this list. */
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

  /** …and the comments written at the END of that body, which [[stmtsOf]] by construction cannot
    * carry. Every caller that rebuilds a constructor's braces from the statement list has to place
    * these itself; a caller that renders the body TERM gets them from the emitter's `block`.
    *
    * Read through a comment wrapper for [[stmtsOf]]'s reason: a body's trailing comments do not stop
    * existing because something wrote a leading one. */
  def trailingOf(d: Tree.DefDef): List[Trivia] = d.rhs.map(Tree.uncomment) match
    case Some(b: Tree.Block) => b.trailing
    case _                   => Nil

  /** The FIRST statement of a constructor body, seen THROUGH any comment written above it.
    *
    * Every question this object asks of a constructor's head — is it a `super(args)`, a `this(…)`
    * delegation, a nilary call — is asked by matching that statement's SHAPE, and a
    * [[Tree.Commented]] wrapper defeats a shape match silently. The failure it defeats is the
    * worst one in the file: an unrecognised `super(args)` is DROPPED (CLAUDE.md §4.4, "every
    * exception lost its message"), and one `// call the base` above it would have been enough.
    * The trivia is not lost by looking through it — the wrapper stays on the statement in the
    * body, and `split` puts it back on the tail it keeps. */
  def headStmt(d: Tree.DefDef): Option[Statement] = stmtsOf(d).headOption.map {
    case t: Term => Tree.uncomment(t)
    case other   => other
  }

  /** a constructor's statements MINUS its leading `this(…)` / `super(…)` delegation, which the
    * emitter consumes into `deleg`. The one split both `TirEmitter.ctorBody` and
    * `Plans.residualBody` read, so "the body" means the same list on both sides. */
  def bodyAfterDelegation(program: Program, d: Tree.DefDef): List[Statement] =
    val all = stmtsOf(d)
    headStmt(d) match
      case Some(Tree.Apply(Tree.Select(_, m, _, _), _, _, _, _)) if isInitName(program, m) => all.tail
      case _                                                                               => all

  def isCtor(program: Program, d: Tree.DefDef): Boolean =
    program.symbolOf(d.symbol).exists(_.name == "<init>")

  def ctorsOf(program: Program, body: List[Statement]): List[Tree.DefDef] =
    body.collect { case d: Tree.DefDef if isCtor(program, d) => d }

  // ---- AN ENUM's primary, which is java's ROOT constructor and not the first one written ----
  //
  // `Plans` is deliberately not consulted for an enum: its shape is fixed, because a `case object`
  // reaches exactly one primary and cannot delegate. What was NOT fixed is WHICH constructor that
  // primary is, and the emitter read `ctors.head` — the first in tree order. For the single-
  // constructor enum every corpus library had, head IS the root and nothing could tell the
  // difference. For an OVERLOADED one — `Flags() { this(1); }` beside `Flags(int bits)` — head is
  // the DELEGATING overload, so the emitted class took ITS parameter list (empty) and dropped both
  // bodies: `case object X extends Flags(3)` is `too many arguments`, and every constant that used
  // the nilary overload silently got the field's DEFAULT where java ran `this(1)`.
  //
  // These two functions are the shared derivation (§4.56): the emitter renders from them and
  // `OmissionCheck.overloadedEnumCtors` counts what they refuse, so a refusal can never be a
  // silence and the check can never disagree with what was emitted.

  /** THE constructor an enum's `case object`s all reach — java's ROOT, the one that does not
    * delegate `this(...)`.
    *
    * `None` where the shape cannot be expressed: several roots (a `case object` can reach only one
    * primary), or none at all. Both are refusals the caller must COUNT rather than approximate —
    * attributing one overload's parameters to every constant is the defect this replaces. */
  def enumPrimaryCtor(program: Program, cd: Tree.ClassDef): Option[Tree.DefDef] =
    val ctors = ctorsOf(program, cd.body)
    if ctors.sizeIs <= 1 then ctors.headOption
    else ctors.filterNot(delegatesToThis(program, _)) match
      case one :: Nil => Some(one)
      case _          => scala.None

  /** the enum's own FIELDS that [[enumPrimaryCtor]]'s promoted parameters SUPERSEDE — matched on the
    * name AND THE TYPE, because a name is not a structural fact about anything (`CLAUDE.md` §4.56).
    *
    * The emitter renders every primary parameter as a `var` member of the emitted enum, so a body
    * `ValDef` of the same name would be a second member under one name and cannot be emitted. Where
    * the parameter really IS the field — `TextureFilter(int glEnum)` beside `final int glEnum`, whose
    * whole constructor is the self-assignment `this.glEnum = glEnum` — dropping the field is exact,
    * and the `var` carries its value.
    *
    * Read on the NAME ALONE it is §4.55's rule failing in the other direction: java has TWO variable
    * scopes, so a constructor parameter routinely names a field it is not, and the constructor's job
    * is to COMPUTE one from the other. `HtmlMatch(String open, …)` beside `public final Pattern open`
    * is the shape — `this.open = Pattern.compile(open, …)` is ordinary java, and java resolves `open`
    * to the parameter and `this.open` to the field. Dropped, the enum shipped `var open: String`
    * where every reader wanted a `Pattern`: `value pattern is not a member of String` at each read,
    * and a `Pattern` assigned to a `String` var at the constructor. Eight errors on one enum, and no
    * instrument but the compile could see it — the enum's members are all still there and all still
    * named what java named them.
    *
    * THE TYPE is what tells the two apart and it is exact rather than heuristic: a parameter that IS
    * the field is emitted AS that field, so any difference in the rendered type means the emitted
    * `var` cannot stand for it. Where they differ the field is kept, the parameter is a collidee like
    * any other and `TirEmitter.funnelParamRenames` moves it — which is why that pass and the emitter
    * read THIS function rather than each computing a name test (§4.56 again, and it settles a second
    * disagreement for free: the rename pass used to read `cd.body`'s FIRST constructor where the
    * emitter reads the ROOT, which is `T11.5`'s divergence one level down).
    *
    * Answers a set of FIELD symbols, so a caller holding a `ValDef` asks about the declaration it
    * has rather than about a string it would have to re-derive. */
  def enumSupersededFields(program: Program, cd: Tree.ClassDef): Set[SymId] =
    enumSupersededBy(program, cd).values.toSet

  /** the PAIRING behind [[enumSupersededFields]] — promoted PARAMETER symbol to the FIELD symbol it
    * stands for.
    *
    * The set above is what the drop and the rename need; the pair is what the RENDERING needs,
    * because the emitted parameter IS that field and therefore ships at the field's own access level
    * and the field's own mutability. Read as two derivations those two would be free to disagree
    * about which field a parameter is (§4.56), which is the disagreement `enumSupersededFields`
    * itself exists to prevent one question earlier — so the set is derived FROM the pairing and
    * never beside it. */
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

  /** the arguments a CONSTANT passes to [[enumPrimaryCtor]] — java's own, where it named the root,
    * and the delegation's where it named an overload that delegates to it.
    *
    * WHICH overload java resolved is read off the VALUE-parameter arity, uniquely: an enum constant
    * writes its arguments and java resolved them, and where two overloads share an arity this
    * cannot say which — so it refuses rather than picking, which is `ENGINE-LIMITS.md` T17's rule
    * (java's three-phase resolution has no scala counterpart and is not re-implemented here).
    *
    * TWO further refusals, both because inlining the delegation would otherwise LOSE something:
    *
    *   - the delegating constructor does anything BESIDE delegate. Its extra statements belong to
    *     every constant that names it and there is nowhere to put them;
    *   - a delegation ARGUMENT mentions the delegating constructor's own parameters
    *     (`Flags(String s) { this(s.length()); }`). Substituting the constant's argument terms for
    *     them is a rewrite, and a rewrite performed in a RENDERING layer is a second mechanism for
    *     what `Plans` already does one level up; the honest answer here is the counted refusal.
    *
    * `None` therefore means *emit java's own arguments and count the site* — which is LOUD (the
    * constant passes arguments a nilary primary does not take), and loud is the right residue. */
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

  /** does this tree reference any of `ss`? A whole-`Product` walk, because a delegation argument is
    * an arbitrary expression and the question is only ever "is it CLOSED over the constructor's
    * parameters" — an over-approximation here costs a counted refusal, never a wrong emission. */
  private def mentions(t: Any, ss: Set[SymId]): Boolean = t match
    case Tree.Ident(s, _, _) => ss(s)
    case xs: Iterable[?]     => xs.exists(mentions(_, ss))
    case Some(x)             => mentions(x, ss)
    case p: Product          => p.productIterator.exists(mentions(_, ss))
    case _                   => false

  /** a leading `this(args)` delegation to a PEER constructor (never `super`, never nilary —
    * an explicit nilary call is the implicit primary and carries no information). */
  def delegatesToThis(program: Program, d: Tree.DefDef): Boolean = headStmt(d).exists {
    case Tree.Apply(Tree.Select(r, m, _, _), args, _, _, _) =>
      isInitName(program, m) && !r.isInstanceOf[Tree.Super] && args.nonEmpty
    case _ => false
  }

  private def isInitName(program: Program, m: SymId): Boolean =
    program.symbolOf(m).exists(_.name == "<init>")

  /** A NILARY constructor whose body is NOTHING BUT delegation, with the arguments that delegation
    * passes — `Some(Nil)` for `C() { super(); }`, `Some(args)` for `C() { this(seed(), "d"); }`,
    * `None` for a constructor that is paramful or that does anything else.
    *
    * This is the whole of what `TirEmitter.orderBody` drops in front of a NILARY primary, and it is
    * one function because the two halves of it must never drift apart:
    *
    *   - `Some(Nil)` is DEGENERATE. Scala's implicit primary constructor already is exactly that
    *     constructor, and `def this() = this()` self-recurses. Dropping it costs nothing, and
    *     emitting it costs `E120` at the declaration plus an `E051` at every argument-free `extends`
    *     (`ENGINE-LIMITS.md` CT4/CT5).
    *   - `Some(args)` with `args.nonEmpty` is NOT degenerate — java ran that delegation and scala's
    *     implicit primary does not — and it is dropped ANYWAY, for the same `E120`. That is a real
    *     omission, and it was silent until [[Plans.droppedNilaryCtor]] and
    *     `OmissionCheck.droppedNilaryCtors` gave it a number: `new BitmapFont()` built a font with no
    *     data, no page and no glyph where java loaded the default 15pt face, and nothing in the
    *     pipeline moved (CLAUDE.md §4.4 — it compiles and means something else).
    *
    * An ABSENT body is `Some(Nil)` too: a constructor with no statements at all is the implicit
    * primary exactly as an explicit `super()` is.
    *
    * '''`Some(Nil)` IS RESERVED FOR AN EMPTY STATEMENT LIST — it is not a fallback.''' The arm that
    * produced it used to be `case _ => Some(Nil)` under a match on `d.rhs` shaped as `Tree.Block`, so
    * it also caught two bodies that are not "no body" at all and answered DROP IT, SILENTLY, for
    * both:
    *
    *   - `Some(Tree.Commented(_, block))` — a comment written above the constructor's first
    *     statement wraps the body in a TERM, and the shape match on `Tree.Block` then fails. That is
    *     the failure [[headStmt]] calls the worst one in this file, arriving at the one predicate
    *     whose answer is "delete this constructor": `C() { /* seed it */ this(seed(), "d"); }` read
    *     as degenerate, dropped, and NOT counted by `OmissionCheck.droppedNilaryCtors` either, since
    *     the count reads this same function. One comment was the whole difference.
    *   - `Some(<any non-block term>)` — a single-statement body with no braces around it, likewise
    *     read as empty. Where that statement IS the delegation the drop is now COUNTED; where it is
    *     anything else the answer is `None` and the constructor is EMITTED, which is `E120` beside a
    *     nilary primary — loud, attributable and at the declaration, rather than a constructor that
    *     vanishes with its delegation. A refusal that fails a compile is strictly better than one
    *     that changes behaviour (CLAUDE.md §3).
    *
    * NILARY is asked of the VALUE parameters ([[valueParams]]), never `paramss.flatten`: a `C()`
    * that gained a `(using T)` clause is still java's nilary constructor, and reading the flattened
    * list made it paramful, un-dropped it, and put `def this()(using T)` beside a primary carrying
    * the same clause (CT5). */
  def delegationOnlyNilary(program: Program, d: Tree.DefDef): Option[List[Term]] =
    if valueParams(program, d).nonEmpty then scala.None
    else
      val delegations = stmtsOf(d).map {
        case t: Term => Tree.uncomment(t) match
          case Tree.Apply(Tree.Select(_, m, _, _), as, _, _, _) if isInitName(program, m) => Some(as)
          case _                                                                          => scala.None
        case _ => scala.None
      }
      // an EMPTY list is vacuously all-delegation, which is exactly right and is the ONLY way
      // `Some(Nil)` is now reached: no statements to run means scala's implicit primary already is
      // this constructor.
      if delegations.forall(_.isDefined) then Some(delegations.flatten.flatten) else scala.None

  // ---- JAVA'S PARAMETERS vs THE PIPELINE'S (see `Plan.givens`) ----
  //
  // A java constructor's parameter list is one list. A scala constructor's is a list of GROUPS, and
  // a phase may append a `(using T)` one (`DESIGN.md` §8.4). Every question this file asks about a
  // constructor's parameters — is it nilary, does it pass its parameters straight through, does its
  // signature equal the slots — is a question about what JAVA declared, and reading `paramss.flatten`
  // answers a different one the moment such a clause exists: a class whose only constructor gained
  // `(using Ctx)` stopped being the nilary root the funnel promotes, the promotion was withheld, and
  // the class emitted a synthetic nilary primary beside a `def this()(using Ctx)` with no given in
  // scope at all (`ENGINE-LIMITS.md` CT4). Both functions are the identity on an unedited program.

  /** the TRAILING `using` clauses of a constructor — what a phase added, never what java wrote. */
  def givenClauses(program: Program, d: Tree.DefDef): List[List[Tree.ValDef]] =
    d.paramss.reverse
      .takeWhile(ps => ps.nonEmpty && ps.forall(v => program.symbolOf(v.symbol).exists(_.flags.isGiven)))
      .reverse

  /** the VALUE parameters — the only thing "is this constructor nilary" can mean. */
  def valueParams(program: Program, d: Tree.DefDef): List[Tree.ValDef] =
    d.paramss.dropRight(givenClauses(program, d).size).flatten

  /** THE CLASS'S OWN CONTEXT CLAUSE — what a plan with no primary of its own must still put on the
    * emitted class (`ENGINE-LIMITS.md` CT5, `DESIGN.md` §8.2).
    *
    * A promoted primary reads its clause off the constructor it promotes and a synthesised one off
    * its roots; a `Plan.none` class has neither, and scala's implicit nilary primary carries no
    * clause at all — so the `using` reaches only the `def this` secondaries and the class body has
    * no given in scope. The clause is the same on every constructor by construction (a phase that
    * threads a class puts it on all of them, `DESIGN.md` §8.4), so this reads it back off them.
    *
    * `Nil` — the pre-clause code path, and every port that threads nothing — for four cases, each
    * of which is a REFUSAL rather than an oversight:
    *
    *   - no constructor carries one, which is every unedited program;
    *   - the clauses DISAGREE, so no single primary could carry one every secondary's `this(...)`
    *     resolves against;
    *   - the class declares no constructor at all, so nothing was threaded through it;
    *   - it is a TRAIT, a MODULE or an ENUM. Scala's trait parameters are a different feature with
    *     their own restrictions (a subtrait may not pass arguments), an object has no constructor
    *     to speak of, and an enum's primary IS its java constructor — every `case object` would
    *     have to pass an argument. The port's own `promoteToClass` is the answer for the trait.
    *
    * Every one of those refusals is COUNTED where it can be seen: the emitter records what it
    * rendered against what the constructors carried, and the run reports the difference as a
    * `lost-clause` seam. A clause that silently does not reach the emitted class compiles perfectly
    * and moves no other number (CLAUDE.md §3). */
  def classGivens(program: Program, cd: Tree.ClassDef): List[List[Tree.ValDef]] =
    if program.symbolOf(cd.symbol).exists(x => x.flags.isModule || x.flags.isTrait || x.flags.isEnum)
    then Nil
    else
      val ctors = ctorsOf(program, cd.body)
      val cs    = ctors.map(givenClauses(program, _))
      if ctors.isEmpty || cs.exists(_.isEmpty) then Nil
      else if cs.map(_.map(_.map(_.tpt.tpe))).distinct.sizeIs != 1 then Nil
      else cs.head

  /** does this class's own constructors carry a context clause? The question the EMITTER asks to
    * decide whether the header it just wrote lost one — asked of the class, never of the plan,
    * because the plan is exactly what may have dropped it. */
  def ctorsCarryGivens(program: Program, cd: Tree.ClassDef): Boolean =
    ctorsOf(program, cd.body).exists(givenClauses(program, _).nonEmpty)

  /** a promoted plan, with the constructor's own context clause carried onto the primary. */
  private def promoted(program: Program, c: Tree.DefDef, sa: List[Term], rest: List[Statement]): Plan =
    Plan(Some(c), sa, rest, givens = givenClauses(program, c))

  // ---- WHAT A PROMOTION COSTS, as a function of the promotion alone ----
  //
  // These three used to be `Plans` methods reading the FINAL plan, and that is one caller short.
  // The other one is the NOMINATION itself: `syntheticPrimary`'s collapse promotes a real
  // constructor, so it has to be able to ask what that promotion would cost BEFORE it commits to
  // it — and asking through a second, locally-written predicate is exactly the shape
  // `ENGINE-LIMITS.md` C7 warns about at `OmissionCheck.droppedSuperArgs`, where the count and the
  // emission disagreed about the same question. Parameterised on `(primary, primaryBody)` instead
  // of on a `Plan`, so a candidate and a decided plan are answered by the same code.

  /** Did Java ALSO run `target`'s body when `d` was the constructor invoked? True for `target`
    * itself, and for every constructor whose leading `this(...)` delegation reaches it.
    *
    * ANY arity of `this(...)`, including the nilary one: `C(int n) { this(); … }` is a genuine
    * delegation to `C()` even though [[delegatesToThis]] excludes it (that predicate answers a
    * different question — whether the constructor is a funnel ROOT). Reading root-ness here
    * instead would report `C(int)` as a path java did not run `C()` on, when java ran it first.
    *
    * An ABSENT delegation is java's implicit `super()`, which reaches no peer of this class, so
    * "no leading `this(...)`" is a negative answer and not an unknown. */
  def reachesCtor(program: Program, d: Tree.DefDef, target: SymId, depth: Int = 0): Boolean =
    // through `headStmt`, never the raw head: a java comment above the `this(...)` wraps it in
    // `Tree.Commented`, and matching the raw statement counted that constructor as an ESCAPE —
    // a finding manufactured by a comment (found at the E1 merge, pinned in the spec).
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

  /** the statements of an escaping root that `primaryBody` does not ALREADY run, when its own body
    * literally begins with it — see [[Plans.residualBody]] for why this is exact rather than an
    * approximation, and why the comparison is over the canonical rendering. */
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

  /** the leading `super(args)` of a constructor, when it passes arguments — exactly what a
    * secondary constructor cannot express. */
  /** the ERASED head name of a parameter type — what scalac compares when it decides two
    * constructor DECLARATIONS cannot coexist (`E120 … have the same type after erasure`).
    *
    * A CLASS type parameter erases to its bound, which for every java class this engine reads is
    * `Object` — so `Pool<T>`'s `Pool(T)` and `Pool(Object)` are the same declaration and a
    * synthesis whose slot is the one cannot be emitted beside the other. Reading the applied type's
    * head name alone would have called them different and emitted both. */
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

  /** split a constructor body into (super args to lift, remaining statements).
    *
    * The head is matched THROUGH its comment wrapper ([[headStmt]]); when it is consumed, whatever
    * was written above it moves onto the first statement that survives, so a `// set up the base`
    * ends up above the code that replaced the call rather than deleted with it. */
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

  /** Nominate the Java constructor that becomes Scala's primary for this class, ignoring the
    * whole-program constraint that [[Plans]] applies on top. */
  /** @param synthesis pass `false` to get the plan this class would have had WITHOUT a synthesised
    *                  primary. [[Plans]] asks for it when the whole-program guard withholds one, so
    *                  a withheld class falls back to its own promotion — `super(args)` in the
    *                  `extends` clause and all — instead of to `nilaryPlan`, which discards them.
    *                  Measured: dropped `super(args)` 79 -> 30 on libGDX core. */
  def plan0(program: Program, cd: Tree.ClassDef, synthesis: Boolean = true): Plan =
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
        val ps = valueParams(program, c).map(_.symbol)
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
            .orElse(several.find(c => valueParams(program, c).isEmpty && c.tparams.isEmpty))
        case several                           => several.find(c => valueParams(program, c).isEmpty && c.tparams.isEmpty)
      chosen match
        // A UNIQUE ROOT is the primary and nothing else is possible or wanted: its parameters are
        // the class's, its body the class body, and it cannot escape because there is no other root
        // to run it on. This case used to read `roots.count(superArgs.nonEmpty) <= 1`, which also
        // swallowed every MULTI-root class carrying no `super(args)` at all — the promotion domain
        // where C7's escaping bodies live. Those go to the synthesis now, which promotes nothing.
        case Some(c) if roots.sizeIs == 1 =>
          val (sa, rest) = split(program, c); promoted(program, c, sa, rest)
        // SEVERAL roots: whichever is nominated, another's arguments or another's body is lost.
        // Try the synthesised primary before falling back to that.
        // NOT for a throwable parent: that branch already nominates the WIDEST pass-through root and
        // is measured (0 -> 55 when it guessed). Consulting the synthesis first let it nominate a
        // NARROWER pass-through root instead — libGDX omissions 46 -> 50, four exception classes
        // losing an argument each.
        case other if !throwableParent && synthesis => syntheticPrimary(program, cd, roots).getOrElse {
          other match
            case None    => Plan.none
            case Some(c) => val (sa, rest) = split(program, c); promoted(program, c, sa, rest)
        }
        // A THROWABLE PARENT KEEPS THE MEASURED CHOICE ABOVE — except where it made none at all.
        //
        // K5.5's table says of this branch "leave it alone", and that stands wherever it NOMINATED
        // something: consulting the synthesis first let it pick a narrower pass-through root and
        // cost libGDX omissions 46 -> 50. The fence is NARROWED here, not removed — reached only
        // on `chosen == None`, which is the case K5.5 never had to answer for and which C3 names:
        // no root passes its own parameters straight through and none is nilary, so there is
        // nothing to promote, `Plan.none` was the answer, and every root's `super(args)` was
        // lowered to a bare `this()`. The class compiled and every exception it threw carried a
        // null message and no cause (§4.4's own row, shipping).
        //
        // What the synthesis supplies is the PRIMARY to delegate to. Everything after that already
        // existed: `throwablePadding` expresses each root at the JDK's widest overload exactly as
        // the promotion's fill does, and refuses — keeping the counted omission — for anything
        // outside the fixed four.
        case None if throwableParent && synthesis =>
          syntheticPrimary(program, cd, roots, throwablePad = true).getOrElse(Plan.none)
        case None    => Plan.none
        case Some(c) => val (sa, rest) = split(program, c); promoted(program, c, sa, rest)

  /** A primary whose parameters ARE the parent constructor's — see [[Plan.synthetic]].
    *
    * Refuses unless every root reaches the same parent constructor with the same arity, and unless
    * that constructor's parameter types are all readable here. A refusal leaves the previous
    * behaviour AND the omission finding, which is the honest outcome: `OmissionCheck` reported all
    * five of simple-graphs' dropped `super(args)` correctly, and the defect survived because nobody
    * opened the report — not because the report was missing. */
  /** @param throwablePad admit the ONE shape whose roots reach DIFFERENT parent constructors: the
    *                     JDK throwable family, whose set is fixed, where each root is padded to the
    *                     widest overload ([[throwablePadding]]). `plan0` passes it only where its
    *                     own measured throwable branch nominated nothing at all — the fence K5.5
    *                     put round this parent is narrowed, never removed. */
  private def syntheticPrimary(program: Program, cd: Tree.ClassDef,
                               roots: List[Tree.DefDef],
                               throwablePad: Boolean = false): Option[Plan] =
    val calls = roots.map(r => superTarget(program, r) -> superArgsOf(program, r))
    val targets = calls.map(_._1).distinct
    val arities = calls.map(_._2.size).distinct
    // ONE parent constructor for every root — the whole admissibility condition. `SymId.None` is a
    // legitimate target and not an unknown: a root with no explicit `super(...)`, or with an
    // explicit nilary one, reaches the parent's NILARY constructor, and a class all of whose roots
    // do that reaches exactly one parent constructor just as much as one where they all write
    // `super(a, b)`. Refusing it kept the synthesis away from the entire domain where the PROMOTION
    // escapes live (`ENGINE-LIMITS.md` C7: a promoted body runs on every construction path), which
    // is where the corpus's 188 escaping paths are.
    //
    // MIXED is still the wall, and the mix is exactly what makes it one: a root writing `super(a)`
    // beside a root that does not reaches TWO different parent constructors, and one `extends`
    // clause cannot make both calls.
    // …and ONE context clause for every root. A phase that threads a `(using T)` puts it on EVERY
    // constructor of the class (`DESIGN.md` §8.4), so this is uniform by construction and the check
    // costs nothing; a program where it is NOT uniform is one where a single synthesised primary
    // cannot carry a clause every secondary's delegation needs, and refusing is the answer that
    // keeps the counted omission instead of emitting a signature no secondary can reach.
    val rootGivens = roots.map(r => givenClauses(program, r))
    // THE SUPER SLOTS, AND WHAT EACH ROOT PUTS IN THEM — one value, reached two ways, and the
    // ordering is the whole of how the throwable case stays fenced off from this one.
    //
    // UNIFORM is the shape this synthesis was built for: every root reaches the SAME parent
    // constructor with the same arity, so each root's own arguments ARE its slot values and
    // nothing is padded. That is the only admissible answer for a parent whose constructor set the
    // engine does not know — `DistanceFieldFont extends BitmapFont` has seven roots reaching seven
    // overloads, and padding there measured 0 -> 55.
    //
    // PADDED is `ENGINE-LIMITS.md` C3's, and it is offered ONLY when `plan0` asks for it, which it
    // does only for a JDK throwable whose own measured branch nominated nothing.
    val slotArgs: Option[(SymId, Map[SymId, List[Term]])] =
      Option.when(targets.sizeIs == 1 && arities.sizeIs == 1)(
        targets.head -> roots.map(r => r.symbol -> superArgsOf(program, r)).toMap)
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
      // parameter TYPES from the parent constructor's own signature, never from one call's
      // arguments: an argument is an expression whose type may be narrower than the formal.
      //
      // …AND READ THROUGH THIS CLASS'S INSTANTIATION OF THE PARENT. The formals are written in the
      // PARENT's scope, so a generic parent's constructor mentions type parameters the subclass
      // declares none of: `class Adapter extends Handler<Adapter, Node, …>` emitted
      // `protected (sup$0: AstNode[N])` for `Handler(AstNode<N> n)` — valid-looking Scala naming a
      // type that is not in scope, and the same defect the replay above already substitutes away
      // (§4.56, [[ParentSubst]]). The `extends` clause is right there, so this is exact.
      val formals = formalsOf(program, target).map(ParentSubst.subst(_, ParentSubst.of(cd)(using program)))
      // a java constructor that ALREADY has the synthetic signature would collide with it — the
      // COLLAPSE test, kept as exact type equality on ROOTS because that is the shape a promotion
      // can actually take over (its parameters ARE the slots, passed straight through).
      val collides = roots.exists(valueParams(program, _).map(_.tpt.tpe) == formals)
      // …but SIGNATURE EQUALITY IS NOT THE QUESTION SCALAC ASKS, and reading it as if it were cost
      // 2 compile errors the first time the synthesis was widened. What the emitter writes for each
      // root is `this(<that root's own super arguments>)`, and scalac resolves that by
      // APPLICABILITY plus most-specific — so a real constructor whose parameters are NARROWER than
      // the parent's formals wins the call outright:
      //
      //   class DistanceFieldFontCache protected (sup$0: BitmapFont, sup$1: Boolean)
      //     def this(font: DistanceFieldFont)                  = this(font, font.usesIntegerPositions())
      //     def this(font: DistanceFieldFont, integer: Boolean) = this(font, integer)   // ITSELF
      //
      // The second is infinite self-delegation; the first resolves to the second, which is declared
      // BELOW it ("secondary constructor must call a preceding constructor"). Neither signature
      // EQUALS the synthetic one, so an equality test sees nothing. The predicate has to be "could
      // this delegation reach any real constructor of the class", asked per ROOT against the
      // arguments actually emitted. A2 step 3 answers it with the marker parameter, which changes
      // the ARITY and so cannot be reached by any of them; until then the honest answer is to
      // refuse the synthesis and keep the counted omission.
      val ctors = ctorsOf(program, cd.body)
      def ancestorOf(anc: SymId, s: SymId, fuel: Int): Boolean =
        s != SymId.None && fuel > 0 && (anc == s ||
          program.definitionOf(s).collect { case c: Tree.ClassDef => c }
            .exists(c => parentSyms(c).exists(ancestorOf(anc, _, fuel - 1))))
      def assignable(from: TypeRepr, to: TypeRepr): Boolean =
        val (f, t) = (headName(program, from), headName(program, to))
        // an UNKNOWN type on either side is assignable, because refusing the synthesis is the safe
        // answer and pretending to know is the unsafe one
        f.isEmpty || t.isEmpty || f == t || t.contains("java.lang.Object") ||
          headSym(from).zip(headSym(to)).exists((fs, ts) => ancestorOf(ts, fs, 16))
      def headSym(t: TypeRepr): Option[SymId] = t match
        case TypeRepr.TypeRef(_, s)      => Some(s)
        case TypeRepr.AppliedType(tc, _) => headSym(tc)
        case _                           => scala.None
      // MARKER NAME — free of every name the class already declares, since the marker lands in that
      // class's companion beside its statics and nested types. §4.55's rule, one level down: keep
      // appending until the name is free rather than assuming the first is.
      def markerName: String =
        val taken = cd.body.collect { case d: Definition => program.symbolOf(d.symbol).map(_.name).getOrElse("") }.toSet
        var n = "Funnel"
        while taken(n) do n = n + "$"
        n
      // FIELD SLOTS ARE DERIVED BEFORE EITHER PREDICATE IS ASKED, and that is not an ordering
      // preference. Both questions are about the SIGNATURE the emitter will write and the ARGUMENTS
      // it will pass, and field slots change both: a class whose real constructor collides with the
      // parent's formals does NOT collide with `formals ++ fieldTypes`, and a delegation carrying
      // five arguments cannot reach a two-parameter constructor at all. Asking either against the
      // super slots alone minted a marker for a class that needed none.
      val (fs, values, consumedRuns, refusedFields) = fieldSlotsOf(program, cd, roots)
      val sup = formals.zipWithIndex.map((ft, k) => (s"sup$$$k", ft))
      // super slots FIRST, then field slots — the `extends` clause reads the first `superSlots`
      // of them positionally, which is what makes `superCall` a question about that prefix alone.
      val allSlots    = sup ++ fs.map(s => (s.name, s.tpe))
      val delegations = roots.map { r =>
        r.symbol -> (superValues(r.symbol) ++ values.getOrElse(r.symbol, Nil))
      }.toMap
      def synthesise(mark: Option[String]): Option[Plan] =
        val o = cd.origin
        Some(Plan(scala.None, sup.map((n, ft) => Tree.Opaque(n, ft, o)), Nil, synthetic = allSlots,
                  marker = mark, superSlots = sup.size, fieldSlots = fs,
                  delegations = delegations, consumed = consumedRuns, notSlot = refusedFields,
                  // the roots agree (checked above), so any one of them names the clause the
                  // synthesised primary must carry for every secondary's `this(...)` to resolve.
                  givens = rootGivens.headOption.getOrElse(Nil)))
      /** is some real constructor of the class applicable to a root's DELEGATION — the arguments
        * the emitter will actually write, field slot values included, never the super arguments
        * alone? That is the question scalac answers when it resolves `this(...)`, and it decides
        * whether the BARE synthesis can be reached at all (`ENGINE-LIMITS.md` C8).
        *
        * There is no `shadowedAt(1)` beside it, and that absence is a consequence rather than an
        * omission: the DISAMBIGUATED delegation writes one more argument, ASCRIBED to a type the
        * engine minted for this class alone (`(null: C.Funnel)`). No real constructor declares a
        * parameter of it, and one declaring `Object` there is applicable but strictly LESS specific
        * than the marker's own type, so it can never win. A bare `null` would have been applicable
        * to everything — which is how this predicate was first written, and it refused the
        * synthesis for every class that also declared a one-argument constructor. */
      val shadowed = delegations.values.exists { args =>
        ctors.exists { c =>
          val ps = valueParams(program, c)
          ps.sizeIs == args.size && ps.zip(args).forall((p, a) => assignable(a.tpe, p.tpt.tpe))
        }
      }
      // The DECLARATION half of the collision question, and it is a DIFFERENT test from the one
      // above: scalac compares two constructor declarations AFTER ERASURE (`E120 … have the same
      // type after erasure`, which `private` does not separate), while the delegation question is
      // overload APPLICABILITY. A widening needs both — `DESIGN.md` §8.2, `ENGINE-LIMITS.md` C8.
      val erasedSlots  = allSlots.map((_, t) => erasedName(program, cd, t))
      val erasureClash = ctors.exists(c => valueParams(program, c).map(v => erasedName(program, cd, v.tpt.tpe)) == erasedSlots)
      // the COLLAPSE, decided once — see the comment on the branch that consumes it below.
      //
      // NEVER UNDER PADDING. A collapse promotes a REAL constructor whose parameters ARE the slots,
      // passed straight through — a property of one call, and under padding the roots do not even
      // reach one parent constructor, so no root's own `super(args)` is the primary's argument
      // list. It is also the promotion K5.5's fence already refused for this parent: `plan0` looked
      // for exactly such a root, found none, and only then asked for the synthesis.
      val collapsed =
        if !padding && fs.isEmpty && collides && !roots.exists(valueParams(program, _).isEmpty)
        then collapseTo(program, cd, roots.filter(passesThrough))
        else scala.None
      // the slot list and the parent's formals must be the same shape for EVERY root — asked of
      // the padded values rather than of `arities`, which under padding is the pre-pad answer.
      val slotArity = superValues.values.map(_.size).toList.distinct
      if slotArity.sizeIs != 1 || !slotArity.contains(formals.size) ||
         formals.contains(TypeRepr.NoType) then scala.None
      // TWO DISJOINT SHAPES, and keeping them disjoint is the whole content of this decision. Three
      // orderings were measured against libGDX before this one, and every ordering that let either
      // shape reach the other's classes moved a number: promotion-first cost 4 omissions
      // (`Texture`, `ShaderProgramLoader`, `FloatAttribute`, `IntAttribute`), synthesis-first cost 2
      // compile errors and 5 omissions. libGDX is untouched only when each applies where it belongs.
      //
      // SHAPE 2 — one root ALREADY has the synthetic signature (its own parameters are the parent's,
      // passed straight through). Synthesising beside it would emit a duplicate signature — scala
      // compares constructors AFTER ERASURE and `private` does not separate them (`E120 … have the
      // same type after erasure`) — so that root is promoted and the others delegate.
      // `Path(int, boolean)` / `Path(int)` is this shape.
      // COLLAPSE FIRST, and that ordering is `DESIGN.md` §8.2's, not a preference: a collapse emits
      // no synthetic member at all and leaves the class byte-for-byte as it is today, so every class
      // it reaches is one the marker never has to price. Reached only where a root's parameters ARE
      // the slots and it passes them straight through — anything else is not a constructor a
      // promotion can take over.
      // …and only where there are NO field slots. With them the synthesised signature is strictly
      // longer than any real constructor's, so nothing collides and nothing shadows: collapsing
      // there would trade a class's whole `val` binding — every hoisted field — for a problem it
      // does not have.
      // …and only where the promotion it makes COSTS NOTHING. A collapse promotes a REAL
      // constructor, so C7 applies to it again: its body becomes the class body and runs on every
      // construction path, including the ones java did not run it on. That is the whole of noise4j's
      // omissions residue — `Object2dArray`'s three roots reach one `Array2D(int, int)`, one of them
      // a pure pass-through whose parameters ARE the slots, so it collapses and its
      // `this.array = getArray(width * height)` runs on all three paths. "Byte-for-byte unchanged"
      // is only worth having where nothing is wrong with the bytes; where the promotion escapes,
      // the marker costs one companion member and takes the escape to ZERO, which is the trade the
      // ordering was never meant to refuse. Asked through `escapesOf` — the same function
      // `OmissionCheck.promotedBodyOnEveryPath` counts with, prefix strip included — so the
      // nomination and the count cannot disagree about what a promotion costs.
      else if collapsed.isDefined then collapsed
      // DISAMBIGUATE — the primary cannot be DECLARED beside a real constructor (erasure) or cannot
      // be REACHED past one (applicability), and there is nothing to collapse onto — or the
      // collapse was declined above because its promotion would escape. A final parameter of a
      // per-class marker type answers both at once by changing the primary's ARITY.
      //
      else if erasureClash || shadowed then synthesise(Some(markerName))
      // SHAPE 1 — SYNTHESISE. Every root reaches the SAME parent constructor, so one primary taking
      // that constructor's own formals expresses all of them, and no java body becomes the class
      // body.
      //
      // This used to demand that one of the roots be NILARY, on the reasoning that a paramful root
      // could always be promoted instead. It cannot: promoting one paramful root leaves every OTHER
      // root's `super(args)` to be rebuilt by a type-matched fill that declines whenever an argument
      // finds no parameter of its own type — and where nothing was nominated at all
      // (`several.find(nilary)` is `None` when no root is nilary) the class was simply
      // `not-funnelled`, every root's arguments dropped and counted. The nilary root was never the
      // reason the encoding works; reaching ONE parent constructor is
      // (`ENGINE-LIMITS.md` C7, corrected).
      //
      // …unless there is nothing to synthesise. With no super slots, no field slots and no clash to
      // disambiguate, a "synthesised primary" is scala's own implicit nilary one and the plan is
      // `Plan.none` — which is what the class already gets, with no promotion and so no escape.
      // Emitting a distinguishable zero-parameter primary here would buy nothing and cost every
      // such class a diff.
      //
      // …with ONE exception, and it is not taken here: scala's implicit primary carries no CONTEXT
      // CLAUSE, so for a class whose constructors have one the two are not the same primary after
      // all. That is `Plan.givens` on the `Plan.none` outcome, applied by [[Plans.hosting]] over
      // every road to it rather than by a second branch here (`ENGINE-LIMITS.md` CT5).
      else if allSlots.isEmpty then scala.None
      else synthesise(scala.None)

  /** THE COLLAPSE, as a value: the widest pass-through root promoted, or `None` when there is none
    * or when promoting it would leave an escaping path.
    *
    * The promoted root's parameters ARE the parent's, so every other root's `super(args)` reaches it
    * through `this(...)`. Whether each one ACTUALLY does is not asserted here — that is
    * [[Plans.superCall]]'s answer, per root, and the one the emitter renders. What IS asserted is
    * the thing `syntheticPrimary` cannot ask any other way: does java run this body on every path
    * that will now run it? Where it does not, the caller falls through to the marker, which
    * synthesises and promotes nothing.
    *
    * The plan is MARKED (`Plan.collapse`), which is what lets [[Plans.superCall]] delegate to it
    * positionally where the type-matched fill declines — see that field. Marking it here rather
    * than re-deriving "is this promotion a pass-through of the parent's formals" at the delegation
    * is CLAUDE.md §4.56's rule about a phase concluding only from what the phase itself did. */
  private def collapseTo(program: Program, cd: Tree.ClassDef, candidates: List[Tree.DefDef]): Option[Plan] =
    candidates.sortBy(c => -valueParams(program, c).size).headOption
      .map { c => val (sa, rest) = split(program, c); promoted(program, c, sa, rest).copy(collapse = true) }
      .filter(p => escapesOf(program, cd, p.primary, p.primaryBody).isEmpty)

  // ---- FIELD SLOTS: a `this.f = e` hoisted into the primary's parameter list ----

  /** the field a top-level statement assigns AND the value it assigns, when it is a plain
    * `this.f = <e>` / `f = <e>` — [[Plans.assignedField]]'s other half, needed here because the
    * value is what becomes a delegation argument. */
  private def assignment(st: Statement): Option[(SymId, Term)] = st match
    case Tree.Commented(_, s)                                          => assignment(s)
    case Tree.Assign(Tree.Ident(f, _, _), rhs, _, _, _)                   => Some((f, rhs))
    case Tree.Assign(Tree.Select(_: Tree.This, f, _, _), rhs, _, _, _)    => Some((f, rhs))
    case _                                                             => scala.None

  /** is this the symbol of one of scala's own operators, as the frontend interns them? An operator
    * application is the ONE call shape that is order-blind, because it computes and cannot observe
    * anything. */
  private def isOperator(program: Program, m: SymId): Boolean =
    program.symbolOf(m).exists(_.fullName.startsWith("scala.<op>#"))

  /** ORDER-BLIND — a value whose result cannot depend on WHEN it is evaluated.
    *
    * A slot value is evaluated in the DELEGATION ARGUMENT LIST: before `super(...)`, before the
    * instance initialisers, before the class body. Java evaluated it after all three. Composed only
    * of the constructor's own parameters, literals and operator applications on those, that
    * reordering is unobservable — nothing it reads has been written yet in either order, and it
    * writes nothing. An expression containing a method call, a `new`, a static or field read or an
    * array read CAN observe it, so the assignment stays a post-delegation statement of its own
    * secondary and the field stays a `var`, `reason=order`.
    *
    * This is CLAUDE.md §4.4's discipline applied to the funnel's own synthesis: ask what the form
    * means when its evaluation ORDER is observed, not only what it looks like. Note the condition
    * subsumes `DESIGN.md` §8.2's separate "does not read `this`" one — a `this` read is neither a
    * parameter nor a literal — so there is one predicate here rather than two. */
  private def orderBlind(program: Program, params: Set[SymId], t: Term): Boolean = t match
    case Tree.Commented(_, s)         => orderBlind(program, params, s)
    case _: Tree.Literal              => true
    case Tree.Ident(s, _, _)          => params(s)
    case Tree.Typed(e, _, _, _)       => orderBlind(program, params, e)
    case Tree.Apply(Tree.Select(q, m, _, _), as, _, _, _) if isOperator(program, m) =>
      orderBlind(program, params, q) && as.forall(orderBlind(program, params, _))
    case _                            => false

  /** the java DEFAULT at a field's declared type, as a term a delegation can pass.
    *
    * `None` where the engine cannot name one — a type variable above all, which `null` does not
    * inhabit in scala. A field the funnel cannot default is simply not a slot, since a root that
    * does not assign it has nothing to put in its place. */
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
          // a class type — `null`, which is what java put there. NOT for a symbol the program
          // declares as a TYPE PARAMETER: scala's `Null` does not conform to an abstract type.
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

  /** The FIELD SLOTS of a synthesised primary, the value each root contributes at each, how many
    * leading statements that consumed from each root, and — for every candidate refused — WHY.
    *
    * The three gates, and each of them was earned:
    *
    *  - **one home per field.** Every root either assigns `f` exactly once in its own LEADING RUN
    *    or does not assign it at all. A root that assigns it later runs a statement the primary has
    *    already performed, in an order java did not have.
    *  - **order-safety** ([[orderBlind]]) — the one place this encoding can silently differ from
    *    java, so it is gated rather than assumed.
    *  - **the class body must not depend on the field's value.** A slot moves the write from the
    *    constructor body to the field's own DECLARATION, which is where java ran the field's
    *    initialiser — so `int a; int b = a * 2;` would read the constructor's `a` where java read
    *    `0`, and an instance init block assigning `a` would win where java's constructor won. Any
    *    field a sibling initialiser or an init block mentions is refused, `reason=initialiser`.
    *
    * `mutable` is left TRUE here and decided by [[Plans]], which is the only place that can see a
    * write in another member of the program. */
  private def fieldSlotsOf(program: Program, cd: Tree.ClassDef, roots: List[Tree.DefDef])
      : (List[FieldSlot], Map[SymId, List[Term]], Map[SymId, Int], List[(String, String)]) =
    val fields = cd.body.collect {
      case v: Tree.ValDef if !program.symbolOf(v.symbol).exists(_.flags.isStatic) => v
    }
    val byId = fields.map(v => v.symbol -> v).toMap
    def nameOf(s: SymId) = program.symbolOf(s).map(_.name).getOrElse("?")
    // everything that runs OUTSIDE a constructor at construction time: sibling field initialisers
    // and instance init blocks. A field either of them touches cannot move to its declaration.
    // Every field's initialiser RHS is here, its own included: `int a = a0;` is fine, `int b = a *
    // 2;` is what refuses `a`. The LHS is deliberately not — a field's own declaration is the very
    // place the slot moves the write TO, so counting it as a mention would refuse every initialised
    // field in the corpus.
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
      // what a root that does NOT assign `f` contributes at its slot: the field's own java
      // initialiser, or the java default at its type. Either must itself be order-blind, since it
      // moves into an argument list too — an initialiser that calls something keeps the field out
      // of the slot set rather than running at a moment java did not.
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
    // A field may be a slot in one root and sit BEHIND a refused one in another root's leading run.
    // Only a PREFIX of a run can be consumed — the statements after the refused one still execute
    // in the secondary — so anything trapped behind it is refused too, and that can cascade.
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
