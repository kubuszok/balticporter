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
  * Two shapes are funnelled today:
  *
  *  1. UNIQUE ROOT — exactly one constructor does not delegate `this(args)`. It becomes the
  *     primary whatever its arity; its `super(args)` lands in `extends`, its parameters become
  *     class parameters, its body becomes class-body statements, and every other constructor is
  *     already a `this(...)` delegation, which Scala expresses verbatim. Nothing is lost.
  *  2. NO-ARG ROOT — several roots, one of them nilary. It becomes the primary (a `def this()`
  *     would clash with Scala's implicit primary anyway). The other roots' super arguments are
  *     NOT expressible and stay counted by [[OmissionCheck]].
  *
  * Anything else (several paramful roots reaching different parent overloads, e.g.
  * `DelayedRemovalArray`'s ten) has no single-primary encoding and is reported, not approximated.
  */
object CtorFunnel:

  /** the plan for one class: which Java constructor is Scala's primary, the super arguments to
    * lift into the `extends` clause, and the leftover statements that become class body. */
  final case class Plan(
      primary: Option[Tree.DefDef],
      superArgs: List[Term],
      /** the promoted constructor's body minus its leading super/this delegation. */
      primaryBody: List[Statement],
  ):
    def primaryParams: List[Tree.ValDef] = primary.map(_.paramss.flatten).getOrElse(Nil)

  object Plan:
    val none: Plan = Plan(scala.None, Nil, Nil)

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
    private val plans: Map[SymId, Plan] =
      var acc     = classes.map(cd => cd.symbol -> plan0(program, cd)).toMap
      var changed = true
      while changed do
        changed = false
        // parents that some subclass reaches with an argument-free `extends` clause
        val needNilary = classes.filter(cd => acc.get(cd.symbol).forall(_.superArgs.isEmpty))
          .flatMap(parentSyms)
          .toSet
        acc.foreach { (s, p) =>
          if p.primaryParams.nonEmpty && needNilary(s) then
            acc = acc.updated(s, Plan.none)
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
        val p        = plans.getOrElse(cd.symbol, Plan.none)
        val thisIsNoOp = p.primaryParams.isEmpty && p.primaryBody.isEmpty && p.superArgs.isEmpty
        val parents  = parentSyms(cd)
        if thisIsNoOp && parents.forall(nilaryEmpty(_)) then
          ctorsOf(program, cd.body).foreach { d =>
            if !p.primary.contains(d.symbol) then
              superApply(d).foreach { case (m, args) =>
                effectsOf(m, args, 0).foreach { stats =>
                  val touched = collection.mutable.Set[SymId]()
                  if usable(cd, d, stats, touched) then
                    out((cd.symbol, d.symbol)) = stats
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

    /** `new C()` runs no statements — so a `this()` that reaches it adds nothing that the
      * replayed constructor would then have to undo or duplicate. A class outside the translated
      * set (the JDK) qualifies: its nilary constructor touches only its own private state, which
      * no replay of ours can reach anyway. */
    private def nilaryEmpty(cls: SymId, depth: Int = 0): Boolean =
      if depth > 8 then false
      else
        classOfSym(cls) match
          case scala.None => true
          case Some(cd) =>
            val cs = ctorsOf(program, cd.body)
            val own = cs.find(_.paramss.flatten.isEmpty) match
              case scala.None => cs.isEmpty
              case Some(c)    => val (sa, rest) = split(program, c); sa.isEmpty && rest.isEmpty
            own && parentSyms(cd).forall(nilaryEmpty(_, depth + 1))

    /** a term that may be substituted for a parameter: evaluating it twice is evaluating it once,
      * so inlining it at each of the parameter's uses preserves Java's evaluate-arguments-once. */
    private def simple(t: Term): Boolean = t match
      case _: Tree.Ident | _: Tree.Literal | _: Tree.This => true
      case Tree.Select(q, _, _, _)                        => simple(q)
      case Tree.Typed(e, _, _, _)                         => simple(e)
      case _                                              => false

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
    private def effectsOf(ctor: SymId, args: List[Term], depth: Int): Option[List[Statement]] =
      if depth > 6 then scala.None
      else
        defOf(ctor).flatMap { d =>
          val ps = d.paramss.flatten
          if ps.length != args.length || !args.forall(simple) then scala.None
          else
            val body = substituted(stmtsOf(d), ps.map(_.symbol).zip(args).toMap)
            body match
              case Tree.Apply(Tree.Select(_, m, _, _), as, _, _, _) :: tl if isInitName(program, m) =>
                if as.isEmpty then Some(tl) else effectsOf(m, as, depth + 1).map(_ ++ tl)
              case all => Some(all)
        }

    /** can these statements legally run one level down, in `cd`'s constructor? */
    private def usable(cd: Tree.ClassDef, d: Tree.DefDef, stats: List[Statement], touched: collection.mutable.Set[SymId]): Boolean =
      if stats.isEmpty then false
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
      val chosen = roots match
        case one :: Nil if one.tparams.isEmpty => Some(one)
        case several                           => several.find(c => c.paramss.flatten.isEmpty && c.tparams.isEmpty)
      chosen match
        case None    => Plan.none
        case Some(c) => val (sa, rest) = split(program, c); Plan(Some(c), sa, rest)
