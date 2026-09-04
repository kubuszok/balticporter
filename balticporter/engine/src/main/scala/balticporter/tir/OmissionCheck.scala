package balticporter.tir

import balticporter.catalog.FixKind

/** Constructs the port carries in the TIR but does NOT emit — counted, located, and reported. The
  * engine's stance is anti-omission (DESIGN.md §3.4): a construct it cannot translate faithfully is
  * fatal, never silently best-effort, since a silent omission compiles green and misbehaves at
  * runtime (two such omissions — dropped `static { }` blocks, dropped `super(args)` — went
  * unnoticed for exactly that reason). This turns that defect class into a number every run shows. */
object OmissionCheck extends RemedySource:

  /** the check's name in `findings.tsv`, as a CONSTANT — `Remedy.lane` naming a literal is how a
    * renamed lane becomes a silently unwired claim rather than a compile error. */
  val Name = "omissions"

  /** THE KINDS THIS LANE FILES, as constants — until there was a menu, the `what` column, the
    * `Finding` kind and the `Remedy` kind it drains were one string literal at one site, and a
    * remedy naming a kind by literal cannot be told from one naming a kind that does not exist.
    * UNCHANGED from the literals they replace — every kind is in a committed baseline. */
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

    /** every kind this lane files — what a menu spec checks a remedy's declared kind against. */
    val all: List[String] = List(DroppedSuperArgs, DroppedCauseMessage, PromotedBodyEveryPath,
      DroppedNilaryCtor, DroppedAnonMember, UnnameableLambdaReturn, DroppedAnnotation,
      OverloadedEnumCtor, EnumNotJavaLangEnum)

  /** @param at the DECLARATION a per-location selection keys on ([[Resolution]]). `SymId.None`
    *   where the row has no nameable declaration, making it UNSELECTABLE rather than falling back
    *   to an enclosing unit that would let one key drain every row in a file. */
  final case class Finding(what: String, owner: String, detail: String, origin: Origin, at: SymId):
    def render: String = s"$what: $owner — $detail  (${origin.javaPath}:${origin.line})"
    def report: CheckReport.Finding =
      CheckReport.Finding(Name, what, owner, CheckReport.relativise(origin.javaPath), origin.line, detail)

  // -------------------------------------------------------------------------------------------
  // THE MENU (`DESIGN.md` §8.16) — what a port may ASK FOR at one of these rows
  // -------------------------------------------------------------------------------------------

  /** THE PORT RAN MORE THAN JAVA DID, AND READ THE BODY — the one omission kind that is an
    * ADDITION. `CtorFunnel` nominates one java constructor as scala's primary and its body becomes
    * the class body, running on EVERY construction path, where java's non-delegating constructors
    * ran disjoint bodies (refusing this measured 0 -> 41 errors, ENGINE-LIMITS C6/C7). Takes an
    * accept because `CtorFunnel.Plans.promotionEscapes` is "deliberately NOT a purity question" —
    * whether re-running is observable depends on facts only the port can read. NOT
    * emission-affecting (the promoted body is already the class body; only the note moves). Keyed
    * at the ESCAPING constructor, not the class — one type may have several escaping paths. */
  val AcceptPromotedBody: Remedy = Remedy(
    id = "accept-promoted-body", lane = Name, kind = Kind.PromotedBodyEveryPath,
    emissionAffecting = false, fix = FixKind.Universal,
    what = "the port has READ the promoted constructor body and states that running it on this " +
      "path is not observable — the divergence C6 counts, examined")

  /** THE ANNOTATION IS RIGHT TO LOSE HERE — the complement of `FrontendConfig.preservedAnnotations`.
    * An argument-bearing java annotation the frontend could not carry is reported rather than
    * emitted bare (`@A` where java wrote `@A(x)` is a different annotation). WHICH annotations are
    * behaviour-bearing is a fact about a library, never about java (T16), so the two answers are
    * `preservedAnnotations` (carry the family) or this (the drop is right here). TWO ids for one
    * act since [[Remedy.subject]] is per-remedy and this lane's rows sit at both a TYPE symbol and
    * a MEMBER symbol, which cannot collide (a bare FQN vs `owner#member`). NOT emission-affecting. */
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

  /** THE MENU, AND WHAT IS DELIBERATELY NOT ON IT. Every other kind here is a LOSS with no site
    * where reading it yields "this is fine" — an accept would drain a DEFECT rather than a
    * question. Absent, each with what refused it: `super(args) dropped` (C3 — padding was measured
    * and refused; use `inject`); `nilary constructor dropped` (C11 — all three delegation-keeping
    * shapes measured worse); `Throwable(cause) message dropped` (a loss, as above);
    * `anonymous-class member dropped` (T1's engine-gap residue); `lambda return with an unnameable
    * result type` (a WORK ITEM, M6/I9 — accepting it would retire it silently). Pointers to
    * existing spellings rather than new remedies (§5): carry the annotation via
    * `FrontendConfig.preservedAnnotations`; supply the constructor via `Substitutions.inject`. */
  def remedies: List[Remedy] =
    List(AcceptPromotedBody, AcceptDroppedAnnotation, AcceptDroppedTypeAnnotation)

  /** DRAIN what this port selected (§5's move). Returns the rows NOT drained; the rest become
    * `remediation(resolved)` and `decisions.tsv` rows. Passed THIS object's own remedies, never a
    * lane name, since an id is globally unique while a (lane, kind) pair is not. */
  def resolved(plan: ResolutionPlan, findings: List[Finding]): List[Finding] =
    plan.drain(remedies, findings)(f => ResolutionPlan.Residue(f.what, f.at, f.owner, f.origin, f.detail))

  /** The complete result. A PURE function of the program: persisting it is the orchestrator's job. */
  def check(program: Program): List[Finding] = check(program, program.units)

  /** The complete result, restricted to the units the run actually EMITS. A DEPENDENT port
    * resolves against another module's Java, so its Program carries units it will never write —
    * checking those misattributes the BASE module's findings entirely (measured: Ashley reported
    * 47 omissions and 67 portability sites, none its own — §4.45). `units` is the run's own set,
    * so a BASE port passes `program.units` and this is the identity. */
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

  /** Every symbol whose top-level owner is one of `units` — the symbol-side counterpart of the
    * unit filter. Fuel-bounded: an unrooted symbol counts as NOT owned. */
  private def ownedBy(program: Program, units: List[Tree.ClassDef]): SymId => Boolean =
    val roots = units.map(_.symbol).toSet
    def rooted(s: SymId, fuel: Int): Boolean =
      s != SymId.None && fuel > 0 &&
        (roots(s) || program.symbolOf(s).exists(sym => rooted(sym.owner, fuel - 1)))
    id => rooted(id, 64)

  /** A Java ANNOTATION the frontend could not carry. An annotation whose arguments would not
    * translate is REPORTED rather than emitted bare, since `@A` where java wrote `@A(x)` is a
    * different annotation. */
  def droppedAnnotations(program: Program): List[Finding] = droppedAnnotations(program, _ => true)

  def droppedAnnotations(program: Program, owned: SymId => Boolean): List[Finding] =
    program.symbols.all.toList.filter(s => s.droppedAnnotations.nonEmpty && owned(s.id)).sortBy(_.fullName).map { s =>
      // at = s.id, the ANNOTATED symbol — a type for some rows, a member for others (two remedy ids).
      Finding(Kind.DroppedAnnotation, s.fullName, s.droppedAnnotations.mkString(", "), s.origin, s.id)
    }

  /** A member of a Java ANONYMOUS class body that did not survive translation. `AnonClass.dropped`
    * names any member kind the frontend could not carry, so a future gap is a NUMBER on every run. */
  def droppedAnonMembers(program: Program): List[Finding] = droppedAnonMembers(program, program.units)

  def droppedAnonMembers(program: Program, units: List[Tree.ClassDef]): List[Finding] =
    val out = collection.mutable.ListBuffer[Finding]()
    // STANDARD traversal, not a private one: a term node added to the tree later is covered for free.
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
    * NARROWED, turned into a number. Java's lambda body is a METHOD body and `return` leaves the
    * lambda (JLS 15.27.2); scala's is an EXPRESSION, so TirEmitter interposes a nested `def`
    * (JS-S21), which needs a RESULT TYPE from the SAM METHOD. What moves this row: a lambda a
    * SOURCE wrote has no method anywhere in the program (javac inferred it from a class file);
    * `SamLambdaTransform` supplies one for a converted anonymous class (I9). */
  def unnameableLambdaReturn(program: Program): List[Finding] =
    unnameableLambdaReturn(program, program.units)

  def unnameableLambdaReturn(program: Program, units: List[Tree.ClassDef]): List[Finding] =
    given Program = program
    val out = collection.mutable.ListBuffer[Finding]()
    // allClassDefs + a term scan per member so a method-LOCAL class is reached too (§3).
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

  /** the value-returning `return`s that belong to THIS construct — stops at a nested lambda/def/
    * anonymous body, which owns its own returns. */
  private def valuedReturns(t: Any): List[Tree.Return] = t match
    case r: Tree.Return                                      => r.expr.map(_ => r).toList
    case _: Tree.Lambda | _: Tree.DefDef | _: Tree.AnonClass => Nil
    case xs: Iterable[?]                                     => xs.toList.flatMap(valuedReturns)
    case Some(x)                                             => valuedReturns(x)
    case p: Product                                          => p.productIterator.toList.flatMap(valuedReturns)
    case _                                                   => Nil

  /** A Java secondary constructor whose `super(args)` cannot be expressed in Scala. Scala's
    * secondary constructors must delegate to another constructor of the SAME class, so a leading
    * `super(…)` becomes `this()` — CORRECT at no arguments, LOSSY otherwise. Derived from
    * [[CtorFunnel]]'s own decision so the two can never disagree: not reported where the
    * constructor is promoted to primary (arguments EMITTED into `extends`), replayed, or carried
    * by the funnel's delegation ([[CtorFunnel.Plans.superCall]]). Asked per CONSTRUCTOR. */
  def droppedSuperArgs(program: Program): List[Finding] = droppedSuperArgs(program, program.units)

  def droppedSuperArgs(program: Program, units: List[Tree.ClassDef],
                  surface: Option[Surface] = scala.None): List[Finding] =
    // allClassDefs, not a cd.body recursion: reaches a method-LOCAL class too (JS-C30).
    def classes(cd: Tree.ClassDef): List[Tree.ClassDef] =
      StandardTraversal.allClassDefs(cd)(using program)

    val plans = CtorFunnel.Plans(program, surface)
    units.flatMap(classes).flatMap { cd =>
      // Per CONSTRUCTOR, from CtorFunnel.Plans.superExpressed — the same function the emitter
      // renders its delegation from, since the emitter can decline one root while expressing another.
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
    // allClassDefs, not a cd.body recursion: reaches a method-LOCAL class too (JS-C30).
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
    * nothing — the one omission in this file that is an ADDITION. CtorFunnel nominates one java
    * constructor as scala's primary; a scala class body runs on every path, so two non-delegating
    * java constructors that ran disjoint bodies now both run the promoted one's. Refusing the
    * promotion was measured 0 -> 41 errors (ENGINE-LIMITS C6), so the emission stands and the
    * divergence is COUNTED. Derived from [[CtorFunnel.Plans.promotionEscapes]], the same
    * `Plan.primaryBody` the emitter inlines. Fix kind (a) at the promotion, not at
    * `CtorFunnel.Plans.supersedes` (C6 again). */
  def promotedBodyOnEveryPath(program: Program): List[Finding] =
    promotedBodyOnEveryPath(program, program.units)

  def promotedBodyOnEveryPath(program: Program, units: List[Tree.ClassDef],
                  surface: Option[Surface] = scala.None): List[Finding] =
    // allClassDefs, not a cd.body recursion: reaches a method-LOCAL class too (JS-C30).
    def classes(cd: Tree.ClassDef): List[Tree.ClassDef] =
      StandardTraversal.allClassDefs(cd)(using program)
    val plans = CtorFunnel.Plans(program, surface)
    units.flatMap(classes).flatMap { cd =>
      val n = plans(cd).primaryBody.size
      plans.promotionEscapes(cd).map { d =>
        val owner = program.symbolOf(cd.symbol).map(_.fullName).getOrElse("?")
        // at = d.symbol, the ESCAPING constructor — a class may have several with different risk.
        Finding(Kind.PromotedBodyEveryPath, owner,
                s"$n statement(s) of the promoted constructor also run here; java ran them only on its own path",
                d.origin, d.symbol)
      }
    }

  /** A NILARY java constructor the port does not emit, whose delegation DID something (e.g.
    * `BitmapFont()` delegating to a default-face constructor) — `new C()` then builds an empty
    * object and NOTHING SAW IT (§4.4's shape exactly). The sibling case (a nilary delegation
    * passing nothing) is not reported, since scala's implicit primary IS that constructor.
    * [[CtorFunnel.delegationOnlyNilary]] is the same predicate the emitter drops with. Fix kind
    * (a), a REFUSAL not a gap — [[CtorFunnel.Plans.droppedNilaryCtor]] records the alternatives
    * measured worse; a port needing the behaviour writes it by hand (`inject`). */
  def droppedNilaryCtors(program: Program): List[Finding] =
    droppedNilaryCtors(program, program.units)

  def droppedNilaryCtors(program: Program, units: List[Tree.ClassDef],
                  surface: Option[Surface] = scala.None): List[Finding] =
    // allClassDefs, not a cd.body recursion: reaches a method-LOCAL class too (JS-C30).
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
    * With ONE java constructor the lowering is exact; with several it holds only where one is the
    * ROOT and every other delegates to it with arguments not mentioning its own parameters
    * ([[CtorFunnel.enumConstantArgs]], the same function the emitter renders from). Everything else
    * is refused (several roots, an overload ambiguity T17 does not re-implement, a delegating
    * constructor doing more than delegating). Replaces an earlier `ctors.head` shape that emitted
    * an EMPTY primary and silently defaulted delegating constants (§4.4's shape). Fix kind (a). */
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

  /** A java enum emitted WITHOUT `java.lang.Enum[X]` — the shape refusal, one row per enum. Java's
    * enum IS a java.lang.Enum, and scala 3 offers that supertype only to the `enum` syntax; where
    * a constant has a class body or a member name java.lang.Enum already made final, the port
    * keeps the `sealed abstract class` shape instead, silently at the enum itself and loud only at
    * some caller (possibly another module, §4.45). `EnumShape.refusal` is the emitter's own
    * function. NO menu entry: a LOSS, not a question the engine declined to decide (§5). Fix kind
    * (a). */
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
