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
