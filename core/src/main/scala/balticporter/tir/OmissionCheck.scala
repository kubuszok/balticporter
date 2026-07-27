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

  def check(program: Program): List[Finding] = droppedSuperArgs(program) ++ droppedAnonMembers(program)

  /** A member of a Java ANONYMOUS class body that did not survive translation.
    *
    * The body itself used to be dropped WHOLESALE and nothing here saw it: `SpoonTir.ctorCall`
    * read `CtConstructorCall` and never asked whether the node was the `CtNewClass` subtype, so
    * `new ClickListener() { public void clicked(…) {…} }` emitted as `new ClickListener()` — a
    * listener that compiles and does nothing. That is now translated ([[Tree.AnonClass]]), and
    * this is the counterpart check: `AnonClass.dropped` names any member kind the frontend could
    * not carry, so a future gap is a NUMBER on every run rather than another green-and-wrong port.
    */
  def droppedAnonMembers(program: Program): List[Finding] =
    val out = collection.mutable.ListBuffer[Finding]()
    // walk with the STANDARD traversal rather than a private one: a term node added to the tree
    // later is then covered here for free, which is exactly the property whose absence let the
    // whole-body omission survive unnoticed for the project's entire history.
    val collect = new Phase:
      def name: String = "omission-check/anonymous-class"
      override def transformNew(t: Tree.New)(using Program): Term =
        t.anon.filter(_.dropped.nonEmpty).foreach { a =>
          out += Finding("anonymous-class member dropped",
            program.symbolOf(a.symbol).map(_.fullName).getOrElse("?"), a.dropped.mkString(", "), a.origin)
        }
        t
    given Program = program
    program.units.foreach(u => StandardTraversal.mapClassDef(collect, u))
    out.toList

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
    * `extends` clause) and is not reported; nor is one whose parent constructor `CtorFunnel`
    * can REPLAY as statements after `this()`. Every other constructor whose `super(...)` carries
    * arguments still loses them, and is reported.
    */
  def droppedSuperArgs(program: Program): List[Finding] =
    def classes(cd: Tree.ClassDef): List[Tree.ClassDef] =
      cd :: cd.body.collect { case c: Tree.ClassDef => classes(c) }.flatten

    val plans = CtorFunnel.Plans(program)
    program.units.flatMap(classes).flatMap { cd =>
      val primary = plans(cd).primary.map(_.symbol)
      CtorFunnel.ctorsOf(program, cd.body).flatMap { d =>
        val args = CtorFunnel.superArgsOf(program, d)
        if args.isEmpty || primary.contains(d.symbol) || plans.replayFor(cd, d).isDefined then Nil
        else
          val owner = program.symbolOf(cd.symbol).map(_.fullName).getOrElse("?")
          List(Finding("super(args) dropped", owner, s"${args.size} argument(s) discarded", d.origin))
      }
    }

  /** grouped one-line summary, most-affected owner first. Grouped by KIND as well as owner —
    * there is more than one kind of omission now, and a summary that words them all the same way
    * would misreport the newer one. */
  def summary(findings: List[Finding]): String =
    if findings.isEmpty then "  none"
    else
      findings.groupBy(f => (f.what, f.owner)).toList.sortBy { case ((w, o), fs) => (-fs.size, w, o) }
        .map { case ((what, owner), fs) => s"  $owner: ${fs.size} × $what" }
        .mkString("\n")
