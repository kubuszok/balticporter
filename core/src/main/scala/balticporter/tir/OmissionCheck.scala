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
    * of these classes do across many distinct parent overloads. [[CtorFunnel]] performs that
    * nomination, and this check is derived from ITS decision, so the two can never disagree: the
    * constructor `CtorFunnel` promotes to primary has its super arguments EMITTED (into the
    * `extends` clause) and is not reported; every other constructor whose `super(...)` carries
    * arguments still loses them, and is.
    */
  def droppedSuperArgs(program: Program): List[Finding] =
    def classes(cd: Tree.ClassDef): List[Tree.ClassDef] =
      cd :: cd.body.collect { case c: Tree.ClassDef => classes(c) }.flatten

    val plans = CtorFunnel.Plans(program)
    program.units.flatMap(classes).flatMap { cd =>
      val primary = plans(cd).primary.map(_.symbol)
      CtorFunnel.ctorsOf(program, cd.body).flatMap { d =>
        val args = CtorFunnel.superArgsOf(program, d)
        if args.isEmpty || primary.contains(d.symbol) then Nil
        else
          val owner = program.symbolOf(cd.symbol).map(_.fullName).getOrElse("?")
          List(Finding("super(args) dropped", owner, s"${args.size} argument(s) discarded", d.origin))
      }
    }

  /** grouped one-line summary, most-affected owner first. */
  def summary(findings: List[Finding]): String =
    if findings.isEmpty then "  none"
    else
      findings.groupBy(_.owner).toList.sortBy(-_._2.size)
        .map((owner, fs) => s"  $owner: ${fs.size} constructor(s)")
        .mkString("\n")
