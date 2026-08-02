package balticporter.tir

/** Constructs the port carries in the TIR but does NOT emit — counted, located, and reported.
  *
  * The engine's stance is anti-omission (DESIGN.md §3.4): a construct it cannot translate faithfully
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
  def check(program: Program, units: List[Tree.ClassDef],
            surface: Option[Surface] = scala.None): List[Finding] =
    droppedSuperArgs(program, units, surface)
      ++ droppedCauseMessages(program, units, surface)
      ++ promotedBodyOnEveryPath(program, units, surface)
      ++ droppedNilaryCtors(program, units, surface)
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
    * can REPLAY as statements after `this()`, nor one whose arguments the funnel's delegation
    * ([[CtorFunnel.Plans.superCall]]) actually carries. Every other constructor whose `super(...)`
    * carries arguments still loses them, and is reported.
    *
    * "Cannot disagree" is a property of the GRANULARITY as much as of the source: the question is
    * asked per CONSTRUCTOR, because that is where the emitter answers it.
    */
  def droppedSuperArgs(program: Program): List[Finding] = droppedSuperArgs(program, program.units)

  def droppedSuperArgs(program: Program, units: List[Tree.ClassDef],
                  surface: Option[Surface] = scala.None): List[Finding] =
    def classes(cd: Tree.ClassDef): List[Tree.ClassDef] =
      cd :: cd.body.collect { case c: Tree.ClassDef => classes(c) }.flatten

    val plans = CtorFunnel.Plans(program, surface)
    units.flatMap(classes).flatMap { cd =>
      // Per CONSTRUCTOR, from `CtorFunnel.Plans.superExpressed` — the same function the emitter
      // renders its delegation from. It was briefly a class-wide flag the planner asserted
      // (`Plan.superExpressed`), and that is the shape this check must never have: the emitter
      // decides per root and can decline one while expressing another, so a class-wide promise
      // silenced the report for the declined root. A check that shadows a decision has to shadow
      // it at the decision's own granularity, or it stops being a shadow and becomes a claim.
      CtorFunnel.ctorsOf(program, cd.body).flatMap { d =>
        val args = CtorFunnel.superArgsOf(program, d)
        if args.isEmpty || plans.superExpressed(cd, d) then Nil
        else
          val owner = program.symbolOf(cd.symbol).map(_.fullName).getOrElse("?")
          List(Finding("super(args) dropped", owner, s"${args.size} argument(s) discarded", d.origin))
      }
    }

  /** A `super(cause)` that reached the JDK's `Throwable(Throwable)` overload and whose MESSAGE that
    * overload computes for itself could not be rebuilt.
    *
    * `Throwable(Throwable cause)` is `this(cause == null ? null : cause.toString(), cause)`, so the
    * delegation has to name the cause in both slots — and a scala secondary constructor cannot bind
    * a value before its `this(...)` call, so a cause the port cannot read twice is refused rather
    * than evaluated twice. The ARGUMENTS are not lost here (the cause reaches its own slot, so
    * [[droppedSuperArgs]] correctly says nothing); the message is, and that is invisible to a
    * compile and to every other count — a runtime probe over the emitted `GdxRuntimeException` is
    * what found it (CLAUDE.md §4.4). Derived from [[CtorFunnel.Plans.causeMessageLost]], the same
    * function the emitter's refusal comes from, per CONSTRUCTOR. */
  def droppedCauseMessages(program: Program): List[Finding] =
    droppedCauseMessages(program, program.units)

  def droppedCauseMessages(program: Program, units: List[Tree.ClassDef],
                  surface: Option[Surface] = scala.None): List[Finding] =
    def classes(cd: Tree.ClassDef): List[Tree.ClassDef] =
      cd :: cd.body.collect { case c: Tree.ClassDef => classes(c) }.flatten
    val plans = CtorFunnel.Plans(program, surface)
    units.flatMap(classes).flatMap { cd =>
      CtorFunnel.ctorsOf(program, cd.body).filter(plans.causeMessageLost(cd, _)).map { d =>
        val owner = program.symbolOf(cd.symbol).map(_.fullName).getOrElse("?")
        Finding("Throwable(cause) message dropped", owner,
                "cause expression cannot be re-read, so the JDK's own message is not rebuilt", d.origin)
      }
    }

  /** A construction path on which the port runs the PROMOTED constructor's body and java ran
    * nothing — the one omission in this file that is an ADDITION.
    *
    * What is dropped is java's DISTINCTION BETWEEN CONSTRUCTION PATHS. `CtorFunnel` nominates one
    * java constructor as scala's primary and its body becomes the class body; a scala class body
    * runs on every path, because every secondary's first statement is a `this(...)` that reaches
    * the primary. Two java constructors that do not delegate to each other ran disjoint bodies, and
    * that separation has no single-primary encoding: `Base() { this.n = Audit.bump(); }` beside
    * `Base(int n) { this.n = n; }` bumps on `new Base(5)` in the port and does not in java.
    *
    * Refusing the promotion is not the fix and was measured: 0 -> 41 compile errors on libGDX,
    * every one an `E120 Conflicting definitions` where the refused class emits a `def this()`
    * beside scala's implicit nilary primary. So the emission stands and the divergence is COUNTED
    * — `ENGINE-LIMITS.md` C6, and CLAUDE.md §4.4's rule that a form which compiles and means
    * something else is the class of defect that must become a number.
    *
    * Per CONSTRUCTOR, and derived from [[CtorFunnel.Plans.promotionEscapes]], which reads the same
    * `Plan.primaryBody` the emitter inlines (`TirEmitter.lowerCtors`). The check and the emission
    * are one function's answer, as `droppedSuperArgs` is of `superExpressed`: a class-wide flag or
    * a second traversal is exactly how a shadow becomes a claim.
    *
    * Fix kind (a) at the promotion — NOT at `CtorFunnel.Plans.supersedes`, where tightening removes
    * no effect and costs the constructor's argument (C6 again). */
  def promotedBodyOnEveryPath(program: Program): List[Finding] =
    promotedBodyOnEveryPath(program, program.units)

  def promotedBodyOnEveryPath(program: Program, units: List[Tree.ClassDef],
                  surface: Option[Surface] = scala.None): List[Finding] =
    def classes(cd: Tree.ClassDef): List[Tree.ClassDef] =
      cd :: cd.body.collect { case c: Tree.ClassDef => classes(c) }.flatten
    val plans = CtorFunnel.Plans(program, surface)
    units.flatMap(classes).flatMap { cd =>
      val n = plans(cd).primaryBody.size
      plans.promotionEscapes(cd).map { d =>
        val owner = program.symbolOf(cd.symbol).map(_.fullName).getOrElse("?")
        Finding("promoted constructor body runs on every path", owner,
                s"$n statement(s) of the promoted constructor also run here; java ran them only on its own path",
                d.origin)
      }
    }

  /** A NILARY java constructor the port does not emit, whose delegation DID something.
    *
    * `BitmapFont()` delegates `this(classpath("lsans-15.fnt"), classpath("lsans-15.png"), false,
    * true)` — the default 15pt face. The emitted class keeps scala's implicit nilary primary, so
    * that constructor has nowhere to be declared and is dropped; `new BitmapFont()` then builds a
    * font with no data, no page and no glyph, and NOTHING SAW IT. It compiles, every other count is
    * unchanged, and the suite does not construct one — CLAUDE.md §4.4's shape exactly, and the
    * reason this lane exists.
    *
    * The sibling case — `C() { super(); }`, a delegation passing nothing — is not reported, because
    * scala's implicit primary IS that constructor and dropping it loses nothing. The two are told
    * apart by [[CtorFunnel.delegationOnlyNilary]], which is also the predicate the emitter drops
    * with, so this cannot report a constructor the emitter kept or miss one it dropped.
    *
    * Fix kind (a) — and it is a REFUSAL, not a gap: [[CtorFunnel.Plans.droppedNilaryCtor]] records
    * the three alternatives and what each was measured to cost. A port that needs the behaviour
    * writes the constructor by hand (§1.5's `inject`); the engine's job is to say so. */
  def droppedNilaryCtors(program: Program): List[Finding] =
    droppedNilaryCtors(program, program.units)

  def droppedNilaryCtors(program: Program, units: List[Tree.ClassDef],
                  surface: Option[Surface] = scala.None): List[Finding] =
    def classes(cd: Tree.ClassDef): List[Tree.ClassDef] =
      cd :: cd.body.collect { case c: Tree.ClassDef => classes(c) }.flatten
    val plans = CtorFunnel.Plans(program, surface)
    units.flatMap(classes).flatMap { cd =>
      plans.droppedNilaryCtor(cd).map { d =>
        val owner = program.symbolOf(cd.symbol).map(_.fullName).getOrElse("?")
        val n     = CtorFunnel.delegationOnlyNilary(program, d).map(_.size).getOrElse(0)
        Finding("nilary constructor dropped", owner,
                s"its delegation passed $n argument(s); scala's implicit nilary primary runs nothing, " +
                  "so `new C()` no longer performs it",
                d.origin)
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
