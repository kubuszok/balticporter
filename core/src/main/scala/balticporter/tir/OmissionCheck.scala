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
    def report: CheckReport.Finding =
      CheckReport.Finding("omissions", what, owner, CheckReport.relativise(origin.javaPath), origin.line, detail)

  /** The complete result. A PURE function of the program: persisting it is the orchestrator's job
    * (`PortRun` records `omissions` from this list), so a caller's `take(n)` render can never be
    * the only record of it and the check itself stays testable without an artifact directory. */
  def check(program: Program): List[Finding] = check(program, program.units)

  /** The complete result, restricted to the units the run actually EMITS.
    *
    * A DEPENDENT port resolves against another module's Java (`FrontendConfig.resolutionRoots`), so
    * its `Program` carries units it will never write. Checking those attributes the BASE module's
    * findings to the dependent, and the misattribution is total rather than marginal: Ashley — 21
    * files of its own — reported 47 omissions and 67 portability sites, **none of which were
    * Ashley's**. Every one belonged to the 605 libGDX units it merely resolved against.
    *
    * That is worse than a wrong number. A finding an agent cannot act on in its own repository is
    * CLAUDE.md §4.45's "cannot classify" failure with a plausible owner attached to it, and it
    * scales with the size of the base: sge's 17 extension modules would each have reported libGDX
    * core's entire finding set as their own.
    *
    * `units` is the run's own set, so a BASE port passes `program.units` and this is the identity —
    * the no-op is the general path taken to its limit, not a branch around it (CLAUDE.md §1). */
  def check(program: Program, units: List[Tree.ClassDef]): List[Finding] =
    droppedSuperArgs(program, units)
      ++ droppedAnonMembers(program, units)
      ++ droppedAnnotations(program, ownedBy(program, units))

  /** Every symbol whose top-level owner is one of `units` — the symbol-side counterpart of the unit
    * filter, for checks that scan the symbol table rather than the trees. Fuel-bounded: a symbol
    * that cannot be rooted counts as NOT owned, because attributing another module's finding is the
    * defect this exists to prevent. */
  private def ownedBy(program: Program, units: List[Tree.ClassDef]): SymId => Boolean =
    val roots = units.map(_.symbol).toSet
    def rooted(s: SymId, fuel: Int): Boolean =
      s != SymId.None && fuel > 0 &&
        (roots(s) || program.symbolOf(s).exists(sym => rooted(sym.owner, fuel - 1)))
    id => rooted(id, 64)

  /** A Java ANNOTATION the frontend could not carry.
    *
    * Annotations were dropped WHOLESALE until 2026-07-28 — 221 `@Test` in libGDX's own suite, and
    * every `@Override`, `@Deprecated` and `@Null` in the corpus. That is the worst shape a silent
    * omission can take: a JUnit suite with no `@Test` runs zero tests and reports SUCCESS, so the
    * defect manufactures the evidence that behaviour is fine and disables the gate meant to catch
    * it. Now that they are translated, this counts whatever still cannot be — an annotation whose
    * arguments would not translate is REPORTED rather than emitted bare, since `@A` where Java
    * wrote `@A(x)` is a different annotation. */
  def droppedAnnotations(program: Program): List[Finding] = droppedAnnotations(program, _ => true)

  def droppedAnnotations(program: Program, owned: SymId => Boolean): List[Finding] =
    program.symbols.all.toList.filter(s => s.droppedAnnotations.nonEmpty && owned(s.id)).sortBy(_.fullName).map { s =>
      Finding("annotation dropped", s.fullName, s.droppedAnnotations.mkString(", "), s.origin)
    }

  /** A member of a Java ANONYMOUS class body that did not survive translation.
    *
    * The body itself used to be dropped WHOLESALE and nothing here saw it: `SpoonTir.ctorCall`
    * read `CtConstructorCall` and never asked whether the node was the `CtNewClass` subtype, so
    * `new ClickListener() { public void clicked(…) {…} }` emitted as `new ClickListener()` — a
    * listener that compiles and does nothing. That is now translated ([[Tree.AnonClass]]), and
    * this is the counterpart check: `AnonClass.dropped` names any member kind the frontend could
    * not carry, so a future gap is a NUMBER on every run rather than another green-and-wrong port.
    */
  def droppedAnonMembers(program: Program): List[Finding] = droppedAnonMembers(program, program.units)

  def droppedAnonMembers(program: Program, units: List[Tree.ClassDef]): List[Finding] =
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
    units.foreach(u => StandardTraversal.mapClassDef(collect, u))
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
  def droppedSuperArgs(program: Program): List[Finding] = droppedSuperArgs(program, program.units)

  def droppedSuperArgs(program: Program, units: List[Tree.ClassDef]): List[Finding] =
    def classes(cd: Tree.ClassDef): List[Tree.ClassDef] =
      cd :: cd.body.collect { case c: Tree.ClassDef => classes(c) }.flatten

    val plans = CtorFunnel.Plans(program)
    units.flatMap(classes).flatMap { cd =>
      val plan    = plans(cd)
      val primary = plan.primary.map(_.symbol)
      // `superExpressed` marks a plan under which EVERY root's super call survives — a synthesised
      // primary (each root a secondary delegating its own arguments), or a promoted pass-through
      // root (whose parameters ARE the parent's, so `this(...)` carries them verbatim). Counting
      // those anyway — they are all non-primary, which is the test below — reported first 4 then 2
      // extra omissions on libGDX for code that had just become MORE faithful. This check exists to
      // shadow the funnel decision exactly; a decision it does not know about is a drift, and the
      // number moving the wrong way is what a drift looks like.
      val synthesised = plan.superExpressed
      CtorFunnel.ctorsOf(program, cd.body).flatMap { d =>
        val args = CtorFunnel.superArgsOf(program, d)
        if args.isEmpty || synthesised || primary.contains(d.symbol) || plans.replayFor(cd, d).isDefined then Nil
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
