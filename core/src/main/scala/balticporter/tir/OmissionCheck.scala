package balticporter.tir

/** Constructs the port carries in the TIR but does NOT emit — counted, located, and reported.
  *
  * The engine's stance is anti-omission (PLAN.md §3.3): a construct it cannot translate faithfully
  * is fatal, never silently best-effort. Where a lowering is genuinely deferred, the deferral must
  * still be VISIBLE, because a silent omission is the worst failure this engine has: the generated
  * code compiles, the gate is green, and the program misbehaves at runtime. Two such omissions were
  * found only by accident (`static { … }` blocks, dropped entirely; `super(args)` in a secondary
  * constructor, dropped down to `this()`), and both had gone unnoticed precisely because nothing
  * counted them.
  *
  * This turns that class of defect into a number that shows up on every migration run.
  */
object OmissionCheck:

  final case class Finding(what: String, owner: String, detail: String, origin: Origin):
    def render: String = s"$what: $owner — $detail  (${origin.javaPath}:${origin.line})"

  def check(program: Program): List[Finding] = droppedSuperArgs(program)

  /** A Java secondary constructor whose `super(args)` cannot be expressed in Scala.
    *
    * Scala's secondary constructors must delegate to another constructor of the SAME class; only
    * the primary may reach `super`. The emitter therefore rewrites a leading `super(…)` to
    * `this()` — which is CORRECT when the call takes no arguments (Scala's primary calls the
    * no-arg super implicitly) and LOSSY when it does: `new DelayedRemovalArray(16)` silently
    * builds an empty array because `super(capacity)` became `this()`.
    *
    * Expressing these needs real constructor funnelling — the class's primary must be
    * parameterised to reach each parent constructor its Java constructors target, which several
    * of these classes do across many distinct parent overloads. Until that exists, every affected
    * constructor is reported here rather than quietly shipped.
    */
  def droppedSuperArgs(program: Program): List[Finding] =
    def ctorsOf(cd: Tree.ClassDef): List[(Tree.ClassDef, Tree.DefDef)] =
      cd.body.collect { case d: Tree.DefDef if program.symbolOf(d.symbol).exists(_.name == "<init>") => (cd, d) } ++
        cd.body.collect { case c: Tree.ClassDef => ctorsOf(c) }.flatten

    program.units.flatMap(ctorsOf).flatMap { (cd, d) =>
      val stats = d.rhs match
        case Some(Tree.Block(s, _, _, _)) => s
        case Some(t)                      => List(t)
        case None                         => Nil
      stats.headOption match
        case Some(Tree.Apply(Tree.Select(_: Tree.Super, m, _, _), args, _, _, _))
            if args.nonEmpty && program.symbolOf(m).exists(_.name == "<init>") =>
          val owner = program.symbolOf(cd.symbol).map(_.fullName).getOrElse("?")
          List(Finding("super(args) dropped", owner, s"${args.size} argument(s) discarded", d.origin))
        case _ => Nil
    }

  /** grouped one-line summary, most-affected owner first. */
  def summary(findings: List[Finding]): String =
    if findings.isEmpty then "  none"
    else
      findings.groupBy(_.owner).toList.sortBy(-_._2.size)
        .map((owner, fs) => s"  $owner: ${fs.size} constructor(s)")
        .mkString("\n")
