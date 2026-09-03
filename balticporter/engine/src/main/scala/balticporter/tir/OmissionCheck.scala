package balticporter.tir

import balticporter.catalog.FixKind

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
object OmissionCheck extends RemedySource:

  /** the check's name in `findings.tsv`, as a CONSTANT — because a [[Remedy]] names this lane too,
    * and `Remedy.lane` asking for a literal is how a renamed lane becomes a silently unwired claim
    * rather than a compile error. `PortRun.Omissions` reads it, so there is one spelling. */
  val Name = "omissions"

  /** THE KINDS THIS LANE FILES, as constants for the reason [[Name]] is one.
    *
    * The `what` column of a `Finding` IS the kind a `CheckReport.Finding` carries and IS what a
    * [[Remedy]] declares it drains, and until there was a menu those three were one string literal
    * written at one site. A remedy naming a kind by literal cannot be told from a remedy naming a
    * kind that does not exist — the vocabulary accepts the id, the key binds, and the port reads
    * `NeverApplied` about a site that is right there (`BoundaryRemedySpec`'s own subject).
    *
    * The strings are UNCHANGED from the literals they replace: this lane's kinds are in every
    * committed `findings.tsv` baseline and in `counts.tsv`, so re-wording one is a baseline move
    * that says nothing about a port. */
  object Kind:
    val DroppedSuperArgs        = "super(args) dropped"
    val DroppedCauseMessage     = "Throwable(cause) message dropped"
    val PromotedBodyEveryPath   = "promoted constructor body runs on every path"
    val DroppedNilaryCtor       = "nilary constructor dropped"
    val DroppedAnonMember       = "anonymous-class member dropped"
    val UnnameableLambdaReturn  = "lambda `return` with an unnameable result type"
    val DroppedAnnotation       = "annotation dropped"
    val OverloadedEnumCtor      = "enum constant reaching no expressible primary"
    val InlineDelegationRefused = "parent-delegation inlining refused"
    val EnumNotJavaLangEnum     = "enum emitted without its java.lang.Enum supertype"

    /** every kind this lane files — `Issue.values`' role for a set of strings, and what a menu spec
      * checks a remedy's declared kind against. A kind absent here is a kind no such spec can see,
      * so it is added beside the `val` above and not remembered separately. */
    val all: List[String] = List(DroppedSuperArgs, DroppedCauseMessage, PromotedBodyEveryPath,
      DroppedNilaryCtor, DroppedAnonMember, UnnameableLambdaReturn, DroppedAnnotation,
      OverloadedEnumCtor, EnumNotJavaLangEnum)

  /** @param at the DECLARATION a per-location selection keys on ([[Resolution]]) — the constructor
    *   for a constructor-shaped row, the annotated symbol for an annotation one. `SymId.None` where
    *   the row has no nameable declaration, which makes it UNSELECTABLE rather than selectable by
    *   whatever else happens to carry that id: a fallback to the enclosing unit would let one key
    *   drain every row in a file. */
  final case class Finding(what: String, owner: String, detail: String, origin: Origin, at: SymId):
    def render: String = s"$what: $owner — $detail  (${origin.javaPath}:${origin.line})"
    def report: CheckReport.Finding =
      CheckReport.Finding(Name, what, owner, CheckReport.relativise(origin.javaPath), origin.line, detail)

  // -------------------------------------------------------------------------------------------
  // THE MENU (`DESIGN.md` §8.16) — what a port may ASK FOR at one of these rows
  // -------------------------------------------------------------------------------------------

  /** THE PORT RAN MORE THAN JAVA DID, AND READ THE BODY — the one omission kind that is an ADDITION.
    *
    * `CtorFunnel` nominates one java constructor as scala's primary and its body becomes the class
    * body, which runs on EVERY construction path; java's non-delegating constructors ran disjoint
    * bodies. Refusing the promotion was measured at **0 -> 41 compile errors** on libGDX
    * (`ENGINE-LIMITS.md` C6/C7), so the emission stands and the divergence is counted.
    *
    * ==Why THIS kind takes an accept where its four neighbours do not==
    * `CtorFunnel.Plans.promotionEscapes` says in as many words that this is "deliberately NOT a
    * purity question about the body": whether re-running the promoted statements is OBSERVABLE
    * "depends on what the other constructor overwrites, on what the callee touches, and on the
    * caller", and 59 of libGDX's 771 promotions land here of which "most only waste an allocation"
    * while `Material` bumps a static id counter and `Button` adds a second listener to every button.
    * That is §8.16's premise exactly — a difference with no single right answer, where only the port
    * can read this body and say which of the two it is — and the engine has declined to compute it
    * on purpose rather than for want of a mechanism.
    *
    * ==NOT emission-affecting==
    * The promoted body is already the class body; applying this changes no statement, no signature
    * and no order. Only the porter note moves, which is a comment — so two modules choosing
    * differently cannot produce two ports that compile alone and fail together (§1.5).
    *
    * ==Keyed at the ESCAPING constructor, not at the class==
    * The row is per CONSTRUCTOR because that is where the emitter answers it, and a class routinely
    * has one escaping path whose body is harmless beside another whose is not — libGDX `BitmapFont`
    * has 9. A type key would broadcast the port's reading of one path onto all of them. */
  val AcceptPromotedBody: Remedy = Remedy(
    id = "accept-promoted-body", lane = Name, kind = Kind.PromotedBodyEveryPath,
    emissionAffecting = false, fix = FixKind.Universal,
    what = "the port has READ the promoted constructor body and states that running it on this " +
      "path is not observable — the divergence C6 counts, examined")

  /** THE ANNOTATION IS RIGHT TO LOSE HERE — the complement of `FrontendConfig.preservedAnnotations`.
    *
    * An argument-bearing java annotation the frontend could not carry is reported rather than
    * emitted bare, because `@A` where java wrote `@A(x)` is a different annotation. Which of them
    * MATTER is not java's question and cannot be the engine's: `preservedAnnotations` exists
    * precisely because "WHICH annotations are behaviour-bearing is a fact about a library and its
    * dependencies, never about java" (`ENGINE-LIMITS.md` T16). The corpus population is mostly
    * `@SuppressWarnings`, which suppresses a JAVAC warning scala does not have and therefore means
    * nothing wherever it lands; the same lane also holds a `@RunWith` on a suite a phase converts to
    * MUnit, which would be actively WRONG to carry, and one annotation that decides behaviour.
    *
    * So the two answers are `preservedAnnotations` (carry the family) and this (the drop is right
    * at this declaration) — the same pair `accept-opaque-egress` is one half of, one lane over.
    *
    * ==Why there are TWO ids for one act==
    * [[Remedy.subject]] is declared PER REMEDY and this lane's rows sit at both kinds of subject: a
    * `@SuppressWarnings` on a class and one on a field are the same row shape over a TYPE symbol and
    * a MEMBER symbol. A single id would have to pick one seam, and the other half of the population
    * would then be residue no key can drain — the bar `Remedy.alsoKinds` exists to stop a MECHANISM
    * failing, met here at the subject axis instead. They cannot collide: a bare FQN binds a type and
    * `owner#member` binds a member, so no key can name both and no declaration can hold both.
    *
    * NOT emission-affecting: nothing was emitted for this annotation before the selection and
    * nothing is after. */
  val AcceptDroppedAnnotation: Remedy = Remedy(
    id = "accept-dropped-annotation", lane = Name, kind = Kind.DroppedAnnotation,
    emissionAffecting = false, fix = FixKind.Universal,
    what = "the port has READ this member's dropped annotation and states that it carries no " +
      "meaning in scala — the complement of a `preservedAnnotations` family")

  /** …the same statement at a TYPE. See [[AcceptDroppedAnnotation]] for why the pair is two ids. */
  val AcceptDroppedTypeAnnotation: Remedy = Remedy(
    id = "accept-dropped-type-annotation", lane = Name, kind = Kind.DroppedAnnotation,
    emissionAffecting = false, fix = FixKind.Universal,
    subject = Remedy.Subject.OwnedType,
    what = "the port has READ this type's dropped annotation and states that it carries no " +
      "meaning in scala — the complement of a `preservedAnnotations` family")

  /** THE MENU, AND WHAT IS DELIBERATELY NOT ON IT.
    *
    * ==The line this lane is cut on==
    * Every other kind here is a LOSS: the port runs LESS than java, and there is no site at which
    * reading it yields *this is fine*. An accept on one would drain a DEFECT rather than a question,
    * which is the same refusal `collection-boundary` makes for `ReifiedOccurrence` and
    * `context-seam` makes for `lost-clause` — a remedy for an engine-caused loss lets a port silence
    * it, and the row is the only instrument there is. The two kinds that DO take one are the two
    * where the engine has said, in code, that it cannot decide: the promoted body's observability
    * (`promotionEscapes`' own doc) and an annotation's meaning to a library (T16).
    *
    * ==Absent, each with what refused it==
    *   - '''`super(args) dropped`''' — `ENGINE-LIMITS.md` C3. Scala's secondary constructors cannot
    *     reach `super`, so a root the funnel could neither promote nor replay loses its arguments,
    *     and PADDING them was measured and refused (it is a guess outside the JDK throwables). Every
    *     one of the corpus's rows is a real loss — a copy constructor that copies nothing, a
    *     `super(type, texture)` that builds an untextured attribute — so an accept has no honest
    *     site. The port's answer is to write the constructor by hand (§1.5's `inject`), which is a
    *     POINTER below and not a remedy;
    *   - '''`nilary constructor dropped`''' — `ENGINE-LIMITS.md` C11, where all three ways of
    *     keeping the delegation were measured WORSE. The accept would say *nothing constructs
    *     `new C()`*, which is a claim about the port's CONSUMERS and not about a site the port can
    *     read — the same reason `DESIGN.md` §8.18 refuses to offer a `dropMethods`-scale drop of the
    *     members nothing calls;
    *   - '''`Throwable(cause) message dropped`''' — a refusal inside the delegation (a cause the
    *     port cannot read twice), and the JDK's own computed message is gone. A loss, as above;
    *   - '''`anonymous-class member dropped`''' — an ENGINE GAP (`ENGINE-LIMITS.md` T1's residue):
    *     the frontend could not carry a member kind. Nothing about the site licenses it;
    *   - '''`lambda `return` with an unnameable result type`''' — a WORK ITEM rather than a refusal
    *     (`ENGINE-LIMITS.md` M6/I9: a builder that holds the SAM method fills `Tree.Lambda
    *     .resultTpt`, which is why `SamLambdaTransform` closed part of it). Accepting a work item
    *     retires it silently, which is the one thing a residue lane must not let a port do.
    *
    * ==Pointers — acts that already have a spelling, so they are NOT remedies (`CLAUDE.md` §5)==
    *   - carry the annotation instead → `FrontendConfig.preservedAnnotations` (`annotations = […]`
    *     in a `.conf`), which is per-library policy with an empty default;
    *   - supply a constructor the funnel could not express → `Substitutions.inject`, plus the
    *     `dropTypes`/`dropMethods` entry beside it;
    *   - keep java's construction paths apart → there is no key, and there is no engine act either:
    *     `CtorFunnel.Plans.supersedes` was the obvious place and tightening it "removes no effect and
    *     costs the constructor's argument" (C6). The finding is the answer. */
  def remedies: List[Remedy] =
    List(AcceptPromotedBody, AcceptDroppedAnnotation, AcceptDroppedTypeAnnotation)

  /** DRAIN what this port selected — `CLAUDE.md` §5's move, performed through the one function that
    * performs it for every lane. Returns the rows that were NOT drained; the rest are in the plan's
    * ledger and become `remediation(resolved)` rows and `decisions.tsv` rows.
    *
    * Passed THIS object's own remedies and never a lane name, because an id is globally unique while
    * a `(lane, kind)` pair is not — see `ResolutionPlan.drain`. */
  def resolved(plan: ResolutionPlan, findings: List[Finding]): List[Finding] =
    plan.drain(remedies, findings)(f => ResolutionPlan.Residue(f.what, f.at, f.owner, f.origin, f.detail))

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
      ++ inlineDelegationRefused(program, units, surface)
      ++ droppedCauseMessages(program, units, surface)
      ++ promotedBodyOnEveryPath(program, units, surface)
      ++ droppedNilaryCtors(program, units, surface)
      ++ droppedAnonMembers(program, units)
      ++ unnameableLambdaReturn(program, units)
      ++ overloadedEnumCtors(program, units)
      ++ enumShapeRefusals(program, units)
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
      // …`at = s.id`, which is the ANNOTATED symbol itself and is a type for some rows and a member
      // for others. That is why the menu carries two ids: `Remedy.subject` is per remedy, and a bare
      // FQN binds one seam while `owner#member` binds the other (see `AcceptDroppedAnnotation`).
      Finding(Kind.DroppedAnnotation, s.fullName, s.droppedAnnotations.mkString(", "), s.origin, s.id)
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
          out += Finding(Kind.DroppedAnonMember,
            program.symbolOf(a.symbol).map(_.fullName).getOrElse("?"), a.dropped.mkString(", "), a.origin,
            a.symbol)
        }
        t
    given Program = program
    units.foreach(u => StandardTraversal.mapClassDef(collect, u))
    out.toList

  /** A `return` inside a LAMBDA whose result type nothing in the program states — M6's refusal,
    * NARROWED, and turned into a number.
    *
    * A java lambda body is a METHOD body: `return` is legal in it and leaves the LAMBDA (JLS
    * 15.27.2). A scala lambda is an EXPRESSION and rejects `return` outright, so `TirEmitter`
    * interposes a nested `def` (`JS-S21`) — and a `def` needs a RESULT TYPE, which is the SAM
    * METHOD's and not the functional interface's. The emitter can decide one case from the body
    * alone (every `return` valueless ⇒ a java `void` lambda) and reads [[Tree.Lambda.resultTpt]]
    * for the rest. What is LEFT is a lambda with a value-returning `return` that nobody told the
    * type — and the emitter refuses it, correctly, because a guessed result type is a `def` that
    * COMPILES and means something else (§4.6).
    *
    * '''Why this belongs here and not in a lane of its own.''' This object's whole subject is
    * *constructs the port carries in the TIR and does NOT emit faithfully*, and that is exactly
    * what the refusal leaves: the `return` is in the tree, the emitted text is a bare `return`
    * inside a function literal, and the only other evidence is a scalac error somebody has to
    * classify. Counted here it is a number on every run, with no new required check and no
    * fifteen-baseline promotion for a residue that is at zero.
    *
    * '''What moves it.''' Nothing about the SITE — the site is java, and java is fine. What moves
    * it is a builder that knows the method: `SamLambdaTransform` converts an anonymous class and
    * hands the emitter the anon's own `returnTpt`, which is why `ENGINE-LIMITS.md` I9 was a work
    * item and not a refusal. A lambda the SOURCE wrote has no method anywhere in the program —
    * javac inferred its type from the target's class file — so this row is what M6 still stands
    * for, stated at exactly the sites where it does. */
  def unnameableLambdaReturn(program: Program): List[Finding] =
    unnameableLambdaReturn(program, program.units)

  def unnameableLambdaReturn(program: Program, units: List[Tree.ClassDef]): List[Finding] =
    given Program = program
    val out = collection.mutable.ListBuffer[Finding]()
    // `allClassDefs` + a term scan per member, so the finding names the DECLARATION a reader would
    // open — and so a method-LOCAL class is reached, which a `cd.body` recursion cannot (§3).
    units.foreach { u =>
      StandardTraversal.allClassDefs(u).foreach { cd =>
        val clsFqn = program.symbolOf(cd.symbol).map(_.fullName).getOrElse("?")
        cd.body.foreach { st =>
          val (fqn, at, terms) = st match
            case d: Tree.DefDef => (program.symbolOf(d.symbol).map(_.fullName).getOrElse(clsFqn), d.symbol, d.rhs.toList)
            case v: Tree.ValDef => (program.symbolOf(v.symbol).map(_.fullName).getOrElse(clsFqn), v.symbol, v.rhs.toList)
            case t: Term        => (clsFqn, cd.symbol, List(t))
            case _              => (clsFqn, cd.symbol, Nil)
          terms.foreach { t =>
            StandardTraversal.scanTerm(t, ()) { (_, x) =>
              x match
                case lam: Tree.Lambda if lam.resultTpt.isEmpty && valuedReturns(lam.body).nonEmpty =>
                  out += Finding(Kind.UnnameableLambdaReturn, fqn,
                    s"${valuedReturns(lam.body).size} value-returning `return`(s) in a lambda body; " +
                      "the nested `def` that restores java's meaning (JS-S21) needs the SAM " +
                      "METHOD's result type and nothing in the program states it [§1(a) engine: a " +
                      "builder that holds the method fills `Tree.Lambda.resultTpt`; " +
                      "ENGINE-LIMITS M6/I9]",
                    lam.origin, at)
                case _ => ()
            }
          }
        }
      }
    }
    out.toList

  /** the value-returning `return`s that belong to THIS construct — stopping at a nested one for the
    * same reason the emitter's own walk does: a `return` inside a nested lambda, `def` or anonymous
    * body is that construct's, and counting it here would attribute one refusal to two sites. */
  private def valuedReturns(t: Any): List[Tree.Return] = t match
    case r: Tree.Return                                      => r.expr.map(_ => r).toList
    case _: Tree.Lambda | _: Tree.DefDef | _: Tree.AnonClass => Nil
    case xs: Iterable[?]                                     => xs.toList.flatMap(valuedReturns)
    case Some(x)                                             => valuedReturns(x)
    case p: Product                                          => p.productIterator.toList.flatMap(valuedReturns)
    case _                                                   => Nil

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
    // `allClassDefs`, not a `cd.body` recursion: a class body is the type's MEMBERS, which is one
    // node short of java — a method-LOCAL class (JLS 14.3, `JS-C30`) stands in a member's block.
    def classes(cd: Tree.ClassDef): List[Tree.ClassDef] =
      StandardTraversal.allClassDefs(cd)(using program)

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
          List(Finding(Kind.DroppedSuperArgs, owner, s"${args.size} argument(s) discarded", d.origin, d.symbol))
      }
    }

  /** A parent-delegation inlining (`resolvedThroughParent`) was attempted and REFUSED for this
    * constructor. The roots target different parent constructors that all delegate to one parent
    * root, and inlining would resolve the super args, but the parent constructor's post-delegation
    * body failed usability: a parameter used more than once with a non-simple caller argument
    * (evaluating it twice differs from java's once), or the post-body holds `super.m()` / `return`.
    *
    * Reported on the same `omissions` lane as `droppedSuperArgs` so the refusal is a counted row
    * with its guard named, not only an E134. */
  def inlineDelegationRefused(program: Program): List[Finding] =
    inlineDelegationRefused(program, program.units)

  def inlineDelegationRefused(program: Program, units: List[Tree.ClassDef],
                  surface: Option[Surface] = scala.None): List[Finding] =
    def classes(cd: Tree.ClassDef): List[Tree.ClassDef] =
      StandardTraversal.allClassDefs(cd)(using program)
    val plans = CtorFunnel.Plans(program, surface)
    units.flatMap(classes).flatMap { cd =>
      CtorFunnel.ctorsOf(program, cd.body).flatMap { d =>
        plans.inlineDelegationRefused(cd, d).map { reason =>
          val owner = program.symbolOf(cd.symbol).map(_.fullName).getOrElse("?")
          Finding(Kind.InlineDelegationRefused, owner, reason, d.origin, d.symbol)
        }.toList
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
    // `allClassDefs`, not a `cd.body` recursion: a class body is the type's MEMBERS, which is one
    // node short of java — a method-LOCAL class (JLS 14.3, `JS-C30`) stands in a member's block.
    def classes(cd: Tree.ClassDef): List[Tree.ClassDef] =
      StandardTraversal.allClassDefs(cd)(using program)
    val plans = CtorFunnel.Plans(program, surface)
    units.flatMap(classes).flatMap { cd =>
      CtorFunnel.ctorsOf(program, cd.body).filter(plans.causeMessageLost(cd, _)).map { d =>
        val owner = program.symbolOf(cd.symbol).map(_.fullName).getOrElse("?")
        Finding(Kind.DroppedCauseMessage, owner,
                "cause expression cannot be re-read, so the JDK's own message is not rebuilt", d.origin,
                d.symbol)
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
    // `allClassDefs`, not a `cd.body` recursion: a class body is the type's MEMBERS, which is one
    // node short of java — a method-LOCAL class (JLS 14.3, `JS-C30`) stands in a member's block.
    def classes(cd: Tree.ClassDef): List[Tree.ClassDef] =
      StandardTraversal.allClassDefs(cd)(using program)
    val plans = CtorFunnel.Plans(program, surface)
    units.flatMap(classes).flatMap { cd =>
      val n = plans(cd).primaryBody.size
      plans.promotionEscapes(cd).map { d =>
        val owner = program.symbolOf(cd.symbol).map(_.fullName).getOrElse("?")
        // `at = d.symbol` — the ESCAPING constructor, which is where the row is and where a
        // selection names it. Not `cd.symbol`: a class routinely has one escaping path whose
        // promoted body is harmless beside another whose is not (see [[AcceptPromotedBody]]).
        Finding(Kind.PromotedBodyEveryPath, owner,
                s"$n statement(s) of the promoted constructor also run here; java ran them only on its own path",
                d.origin, d.symbol)
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
    // `allClassDefs`, not a `cd.body` recursion: a class body is the type's MEMBERS, which is one
    // node short of java — a method-LOCAL class (JLS 14.3, `JS-C30`) stands in a member's block.
    def classes(cd: Tree.ClassDef): List[Tree.ClassDef] =
      StandardTraversal.allClassDefs(cd)(using program)
    val plans = CtorFunnel.Plans(program, surface)
    units.flatMap(classes).flatMap { cd =>
      plans.droppedNilaryCtor(cd).map { d =>
        val owner = program.symbolOf(cd.symbol).map(_.fullName).getOrElse("?")
        val n     = CtorFunnel.delegationOnlyNilary(program, d).map(_.size).getOrElse(0)
        Finding(Kind.DroppedNilaryCtor, owner,
                s"its delegation passed $n argument(s); scala's implicit nilary primary runs nothing, " +
                  "so `new C()` no longer performs it",
                d.origin, d.symbol)
      }
    }

  /** An enum CONSTANT whose arguments cannot be routed to the one primary a `case object` reaches.
    *
    * A java enum lowers to a sealed abstract class whose primary IS java's constructor, because
    * every `case object` passes its arguments to it and a `case object` cannot delegate. With ONE
    * java constructor that is exact. With several it holds only where one of them is the ROOT and
    * every other delegates to it with arguments that do not mention its own parameters — then the
    * constant that named a delegating overload is emitted with the delegation's arguments, which is
    * what java ran ([[CtorFunnel.enumConstantArgs]], the same function the emitter renders from, so
    * this can neither report a site the emitter handled nor miss one it did not).
    *
    * Everything else is refused: several roots, two overloads at one arity (which java's own
    * three-phase resolution decided and this engine does not re-implement — `ENGINE-LIMITS.md`
    * T17), a delegating constructor that does anything besides delegate, a delegation argument
    * closed over its own parameters.
    *
    * ==Why it needs a lane at all==
    * The refusal used to be `ctors.head` — the FIRST constructor in tree order — whose parameters
    * became the primary's and whose siblings' bodies were dropped. That is not a refusal, it is a
    * wrong answer: `Flags() { this(1); }` written above `Flags(int bits)` gave the class an EMPTY
    * primary, so every constant carrying an argument failed to compile AND every constant that
    * named the nilary overload silently took the field's declared default where java ran `this(1)`.
    * Half of that is loud and half is CLAUDE.md §4.4's shape exactly.
    *
    * Fix kind (a). */
  def overloadedEnumCtors(program: Program): List[Finding] =
    overloadedEnumCtors(program, program.units)

  def overloadedEnumCtors(program: Program, units: List[Tree.ClassDef]): List[Finding] =
    def classes(cd: Tree.ClassDef): List[Tree.ClassDef] =
      StandardTraversal.allClassDefs(cd)(using program)
    units.flatMap(classes)
      .filter(cd => program.symbolOf(cd.symbol).exists(_.flags.isEnum))
      .flatMap { cd =>
        val owner = program.symbolOf(cd.symbol).map(_.fullName).getOrElse("?")
        val n     = CtorFunnel.ctorsOf(program, cd.body).size
        cd.enumCases.filter(ec => CtorFunnel.enumConstantArgs(program, cd, ec.ctorArgs).isEmpty)
          .map { ec =>
            Finding(Kind.OverloadedEnumCtor, owner,
                    s"`${program.symbolOf(ec.symbol).map(_.name).getOrElse("?")}` passes " +
                      s"${ec.ctorArgs.size} argument(s) and this enum declares $n constructors; a " +
                      "`case object` reaches exactly one primary and cannot delegate, so java's own " +
                      "arguments are emitted against the root's parameter list",
                    ec.origin, ec.symbol)
          }
      }

  /** A java enum emitted WITHOUT `java.lang.Enum[X]` — the shape refusal, one row per enum.
    *
    * A java enum IS a `java.lang.Enum`, and scala 3 offers that supertype to the `enum` syntax and
    * to nothing else. Where java's declaration cannot be written as a scala 3 `enum` — a constant
    * with a class body, or a member whose name java.lang.Enum has already made final — the port
    * keeps the `sealed abstract class` shape, which compiles and behaves identically in every
    * respect but this one: it is not a `java.lang.Enum`, so no `<E extends Enum<E>>` bound accepts
    * it, `EnumSet`/`EnumMap` cannot hold it and `Comparable<E>` is absent.
    *
    * That is exactly the shape of omission this lane exists for. It is SILENT at the enum itself —
    * the type compiles, every member is there and no digest says which shape was chosen — and loud
    * only at some caller, possibly in another module, possibly in `CLAUDE.md` §4.45's other
    * repository. `EnumShape.refusal` is the emitter's own function, so a row here can neither name
    * an enum the emitter conformed nor miss one it refused.
    *
    * NO menu entry, deliberately: this is a LOSS (the port carries less than java did) and not a
    * question the engine has declined to decide, which is the screen `CLAUDE.md` §5 puts on an
    * `accept`. The way to drain a row is to make the enum expressible, not to agree with it.
    *
    * Fix kind (a). */
  def enumShapeRefusals(program: Program): List[Finding] =
    enumShapeRefusals(program, program.units)

  def enumShapeRefusals(program: Program, units: List[Tree.ClassDef]): List[Finding] =
    units.flatMap(cd => StandardTraversal.allClassDefs(cd)(using program))
      .filter(cd => program.symbolOf(cd.symbol).exists(_.flags.isEnum))
      .flatMap { cd =>
        EnumShape.refusal(program, cd).map { why =>
          Finding(Kind.EnumNotJavaLangEnum,
                  program.symbolOf(cd.symbol).map(_.fullName).getOrElse("?"),
                  s"$why — emitted as a sealed abstract class, which is not a `java.lang.Enum`, so " +
                    "no `<E extends Enum<E>>` bound, `EnumSet`, `EnumMap` or `Comparable<E>` accepts it",
                  cd.origin, cd.symbol)
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
