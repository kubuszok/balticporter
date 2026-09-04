package balticporter.tir

import balticporter.catalog.FixKind

/** Every emitted CALL whose candidate set spans one of JAVA'S OWN RESOLUTION PHASES — catalog rows
  * `JS-C22` (JLS 15.12.2) and `JS-C23` (JLS 15.12.2.5), counted rather than resolved. Java resolves
  * in THREE PHASES (strict, then loose/boxing, then variable-arity) and a candidate admitted in an
  * earlier phase WINS outright; scala resolves in ONE phase and prefers a non-generic alternative
  * where java's rule does not (JS-C23) — so a call can silently bind a different member with no
  * compile error on either side. COUNTED rather than resolved: predicting the divergence needs
  * scala's own resolution modelled well enough to contradict javac, a compiler-sized project
  * (ENGINE-LIMITS T17). The population is derived from JLS 15.12.2's own phase boundaries — three
  * ways to disagree ([[Issue.VarargPhaseSpan]], [[Issue.BoxingPhaseSpan]],
  * [[Issue.GenericTieBreak]]) — never from "this call is overloaded", which would bury the real
  * spans in noise; see [[Report]]'s own denominator. Structural limits: an external callee's
  * overloads (JDK, dependency) are invisible to this check, as is an inherited overload from an
  * external supertype; `super.f(x)`'s candidate set stays at the callee's own owner ([[rootOf]]). */
object OverloadRiskCheck extends RemedySource:

  val Name = "overload-risk"

  enum Issue:
    /** a fixed-arity and a variable-arity candidate are both applicable — java's phase 2/3 boundary. */
    case VarargPhaseSpan
    /** two applicable candidates differ by a PRIMITIVE against its wrapper or a universal type at one
      * position — java's phase 1/2 boundary. */
    case BoxingPhaseSpan
    /** an applicable candidate is generic and another is not — `JS-C23`'s tie-break. */
    case GenericTieBreak

  object Issue:
    def classification(i: Issue): String = i match
      case VarargPhaseSpan =>
        "§1(a) ENGINE, COUNTED and deliberately NOT resolved (catalog `JS-C22`, JLS 15.12.2): this " +
          "call has both a fixed-arity and a variable-arity candidate applicable to its argument " +
          "count. Java tries the fixed-arity phases FIRST and reaches the vararg one only if both " +
          "fail, so javac bound this call to the fixed-arity member; Scala resolves in one phase " +
          "and picks by specificity, which can choose the other. Neither compiler rejects the " +
          "program, so there is no error to look for — read the emitted call and check WHICH " +
          "member it now names. Closing this needs scala's own resolution modelled well enough to " +
          "predict a divergence, which is a compiler-sized project; the honest step is this count."
      case BoxingPhaseSpan =>
        "§1(a) ENGINE, COUNTED and deliberately NOT resolved (catalog `JS-C22`, JLS 15.12.2): two " +
          "candidates applicable to this call take a PRIMITIVE and its wrapper (or a universal " +
          "type) at the same position. That is java's phase 1 / phase 2 boundary exactly: javac " +
          "admits the primitive alternative WITHOUT boxing and stops there, while scala boxes " +
          "freely in one phase and then picks by specificity. `remove(int)` against " +
          "`remove(Object)` is the shape `CLAUDE.md` §4.4 already records for one JDK member; this " +
          "row is the same question asked of the library's own declarations."
      case GenericTieBreak =>
        "§1(a) ENGINE, COUNTED and deliberately NOT resolved (catalog `JS-C23`, JLS 15.12.2.5): " +
          "among the candidates applicable here, one is GENERIC and one is not. Java's " +
          "most-specific rule does not prefer the non-polymorphic alternative and Scala's " +
          "relative-weight rule does, so the two languages can pick different members with no " +
          "error on either side. A sub-case of `JS-C22` with its own JLS clause, reported apart " +
          "because the fix — if one is ever affordable — is a different rule."

  // -------------------------------------------------------------------------------------------
  // THE MENU (`DESIGN.md` §8.16) — what a port may ASK FOR at one of these calls
  // -------------------------------------------------------------------------------------------

  /** the three kinds one remedy answers. All three are asked of the SAME candidate set at the SAME
    * call — JLS 15.12.2's three phase boundaries — so one act answers whichever of them fired, and
    * `Remedy.alsoKinds` is what stops a member with two of them being able to drain only one. */
  private val AllKinds = Issue.values.toList.map(_.toString)

  /** PIN THE ALTERNATIVE JAVAC BOUND — the ascription, and the only face of this lane the engine
    * can mechanise. Which member JAVAC bound is not predicted, it is READ (`Tree.Apply.method` IS
    * javac's answer) — the remedy only writes that name down, as a METHOD-VALUE ASCRIPTION
    * (`(recv.m: (A, B) => R)(x, y)`), which scala picks an overload at by the EXPECTED TYPE. Same
    * shape `TirEmitter.numericOverloadAscription` already emits unconditionally for one closed
    * face. REFUSES wherever the name cannot be written (see [[ascription]]), counted rather than
    * guessed. EMISSION-AFFECTING: two modules ascribing one shared call differently would emit two
    * ports that each compile alone (§1.5). */
  val AscribeJavacChoice: Remedy = Remedy(
    id = "ascribe-javac-choice", lane = Name, kind = Issue.VarargPhaseSpan.toString,
    emissionAffecting = true, fix = FixKind.Universal,
    what = "name the alternative javac bound, as a method-value ascription, so scala's " +
      "single-phase resolution cannot pick another",
    alsoKinds = AllKinds.filterNot(_ == Issue.VarargPhaseSpan.toString))

  /** the other answer, a STATEMENT and not an act: the operator read this call and accepts that
    * scala may bind another. Not a suppression — the row moves into `remediation(resolved)` with a
    * porter note beside the emitted call, so an unexamined risk becomes an examined one. NOT
    * emission-affecting: changes no tree, so two modules may disagree about it. */
  val AcceptRisk: Remedy = Remedy(
    id = "accept-risk", lane = Name, kind = Issue.VarargPhaseSpan.toString,
    emissionAffecting = false, fix = FixKind.Universal,
    what = "the operator read this call's candidate set and accepts that scala may bind a " +
      "different alternative than javac did",
    alsoKinds = AllKinds.filterNot(_ == Issue.VarargPhaseSpan.toString))

  /** WHAT IS NOT ON THE MENU: auto-ascribe at every spanning site (RULED OUT, T17 — that needs
    * scala's resolution modelled well enough to contradict javac); ascribe the ARGUMENT rather than
    * the method (REFUSED — the expected type must sit on the METHOD to pin the choice); a
    * per-callee table (REFUSED — the phase is a fact about the ARGUMENTS at one site, not the
    * member, §8.16); emit both and let scalac pick (nothing to emit — both typecheck). */
  def remedies: List[Remedy] = List(AscribeJavacChoice, AcceptRisk)

  /** one call at risk. `alternatives` names the candidates so a reader can dismiss the row without
    * re-deriving the candidate set, which is the whole cost of an over-approximation.
    *
    * @param declaration the MEMBER this call is written in — never reported, and the key a
    *   `resolutions` entry names ([[Resolution]]'s granularity). Absent from the row's own text on
    *   purpose: a finding says what is at risk and where, and the declaration is how a SELECTION
    *   reaches it, which is a different question asked by a different reader.
    */
  final case class Finding(issue: Issue, owner: String, member: String,
                           alternatives: List[String], origin: Origin,
                           declaration: SymId = SymId.None):
    def detail: String =
      s"call to `$member` has ${alternatives.size} applicable candidates spanning java's " +
        s"resolution phases (${issue.toString}): ${alternatives.mkString(", ")} — javac bound one " +
        "and scala resolves in a single phase, so the port may name the other with no error on " +
        "either side"
    def render: String = s"$issue $owner: $member  (${origin.javaPath}:${origin.line})"
    def report: CheckReport.Finding =
      CheckReport.Finding(Name, issue.toString, owner, CheckReport.relativise(origin.javaPath),
        origin.line, detail)

  /** the findings AND the denominator they came out of.
    *
    * The denominator is the point: an over-approximation whose false-positive rate nobody can see is
    * a lane that gets ignored, and a lane that gets ignored is the silence it replaced. `calls` is
    * every call this walk examined, `overloaded` is those with more than one applicable
    * program-declared candidate, and `findings` is the subset where the candidate set spans a phase.
    * `overloaded - findings.size` is exactly what the narrowing declined to report. */
  final case class Report(findings: List[Finding], calls: Int, overloaded: Int)

  // -------------------------------------------------------------------------------------------
  // the candidate INDEX — one per program, because every call asks it
  // -------------------------------------------------------------------------------------------

  /** every same-named method a program-declared type and its program-declared ancestors DECLARE.
    *
    * A value built once per program and shared by the check and by the emitter's own consult, so
    * the count and the obligation cannot disagree about which calls the rows are even about
    * (`HeapPollutionCheck`'s rule, one level up: the PREDICATE is stated once; here the INDEX it
    * reads is too). Not a memo on the check object: a table keyed on nothing that a second program
    * would share is the process-global §5.1 forbids.
    */
  final class Overloads(program: Program):
    private val classes: Map[SymId, Tree.ClassDef] =
      given Program = program
      program.units.flatMap(StandardTraversal.allClassDefs).map(c => c.symbol -> c).toMap

    /** the type's program-declared ancestors, nearest first. External parents end the walk: their
      * members are in a class file the frontend interns only on reference, so an inherited overload
      * from one is invisible here — stated in the class doc rather than counted as a zero. */
    private val ancestors: collection.mutable.Map[SymId, List[SymId]] = collection.mutable.Map.empty

    private def parentsOf(c: Tree.ClassDef): List[SymId] =
      c.parents.flatMap {
        case tt: TypeTree => head(tt.tpe)
        case t: Term      => head(t.tpe)
      }.filter(classes.contains)

    private def ancestryOf(s: SymId): List[SymId] =
      ancestors.getOrElseUpdate(s, {
        // fuel-bounded, for `Program.owned`'s reason: a cycle in a parent list must not hang a run,
        // and a truncated ancestry under-reports, which is the direction that does not invent a row.
        def climb(x: SymId, fuel: Int): List[SymId] =
          if fuel <= 0 then Nil
          else classes.get(x).map(parentsOf).getOrElse(Nil).flatMap(p => p :: climb(p, fuel - 1))
        climb(s, 32).distinct
      })

    private val memo = collection.mutable.Map.empty[(SymId, String), List[Tree.DefDef]]

    /** every method named `name` declared by `owner` or by a program-declared ancestor of it.
      *
      * Memoised, because every rendered call asks it and the answer is a fact about the program
      * rather than about the call — without it the emitter walks the owner's member list once per
      * call site, which on the largest port in this corpus is the difference between a check and a
      * cost. */
    def sameName(owner: SymId, name: String): List[Tree.DefDef] =
      memo.getOrElseUpdate(owner -> name,
        (owner :: ancestryOf(owner)).flatMap(t =>
          classes.get(t).toList.flatMap(_.body.collect {
            case d: Tree.DefDef if program.symbolOf(d.symbol).exists(_.name == name) => d
          })))

    private def head(t: TypeRepr): Option[SymId] = headSym(t)

  // -------------------------------------------------------------------------------------------
  // THE PREDICATE, STATED ONCE — read by this check and by the emitter's JS-C22/JS-C23 consults
  // -------------------------------------------------------------------------------------------

  /** java's eight primitives AS THE TIR SPELLS THEM.
    *
    * `scala.Int`, not `int`: java primitives are mapped to scala's at the frontend (`DESIGN.md`
    * §2.1.3 — the one (a) rule with nothing to scope), so by the time a phase or the emitter reads a
    * parameter's type the java spelling is gone. `TirEmitter.numericRank` is the same table read the
    * same way, and reading it wrong here would be a check that reports nothing while looking
    * correct — §4.56's "decide from what the phase DID, never from a name" in its cheapest form. */
  private val primitives = Set(
    "scala.Byte", "scala.Short", "scala.Char", "scala.Int",
    "scala.Long", "scala.Float", "scala.Double", "scala.Boolean")

  /** the OTHER side of JLS 5.1.7's boxing conversion — java's phase 1/phase 2 boundary. The
    * wrapper keeps its JAVA name: a class the port references and does not declare. */
  private val wrapperOf = Map(
    "scala.Byte" -> "java.lang.Byte", "scala.Short" -> "java.lang.Short",
    "scala.Char" -> "java.lang.Character", "scala.Int" -> "java.lang.Integer",
    "scala.Long" -> "java.lang.Long", "scala.Float" -> "java.lang.Float",
    "scala.Double" -> "java.lang.Double", "scala.Boolean" -> "java.lang.Boolean")

  /** a slot that admits a BOXED value and nothing narrower. Deliberately just these three and not
    * java.lang.Number/Comparable, whose wider reference set would turn a phase-boundary count into
    * an everything-is-overloaded count; the narrowing is stated in [[Report]]'s denominator. */
  private val universals = Set("java.lang.Object", "scala.Any", "scala.AnyRef")

  /** the head symbol of a type, shared by the receiver root below and [[Overloads]]'s parent walk
    * so a ThisType reaching only one of them cannot stop an implicit receiver resolving. */
  private def headSym(t: TypeRepr): Option[SymId] = t match
    case TypeRepr.TypeRef(_, s)      => Some(s)
    case TypeRepr.AppliedType(tc, _) => headSym(tc)
    case TypeRepr.ThisType(c)        => Some(c)
    case _                           => scala.None

  /** WHERE JAVA LOOKED — the receiver's STATIC TYPE, JLS 15.12.1's candidate set — not the resolved
    * callee's OWNER, which is where the winner happened to be DECLARED (the two differ when the
    * winner is inherited and a subclass declares a boundary-spanning overload). Rooting at the
    * receiver strictly WIDENS the old set. Three shapes: a SELECT's qualifier head; a bare IDENT
    * resolves against the ENCLOSING class (supplied by the caller, `SymId.None` where absent);
    * `super.f(x)` is NOT widened (java resolves over the SUPERCLASS, ENGINE-LIMITS T17). The guard
    * is the whole safety argument: a root is used only where its candidate set CONTAINS the member
    * javac actually bound. */
  private def rootOf(a: Tree.Apply, callee: SymId, owner: SymId, name: String,
                     ov: Overloads, enclosing: SymId)(using Program): SymId =
    def unwrap(t: Term): Term = t match
      case ta: Tree.TypeApply => unwrap(ta.fun)
      case other              => other
    val candidate = unwrap(a.fun) match
      case Tree.Select(_: Tree.Super, _, _, _) => SymId.None
      case Tree.Select(q, _, _, _)             => headSym(q.tpe).getOrElse(SymId.None)
      case _: Tree.Ident                       => enclosing
      case _                                   => SymId.None
    if candidate != SymId.None && candidate != owner &&
       ov.sameName(candidate, name).exists(_.symbol == callee)
    then candidate
    else owner

  /** WHAT THIS CALL RISKS, or nothing. `None` where the question does not arise (external callee,
    * or fewer than two applicable program-declared candidates); `Some(n, fs)` with `n` the
    * applicable candidate count, from the same computation that produced the findings. `enclosing`
    * is the class the call is written IN, needed by [[rootOf]]. `declaration` is the MEMBER it is
    * written in, carried on every row rather than re-derived (§4.56). */
  def analyse(a: Tree.Apply, ov: Overloads, enclosing: SymId = SymId.None,
              declaration: SymId = SymId.None)(using p: Program): Option[(Int, List[Finding])] =
    if !p.owns(a.method) then scala.None
    else
      val callee = p.symbolOf(a.method)
      val owner  = callee.map(_.owner).getOrElse(SymId.None)
      val name   = callee.map(_.name).getOrElse("")
      if owner == SymId.None || name.isEmpty then scala.None
      else
        val root  = rootOf(a, a.method, owner, name, ov, enclosing)
        val cands = ov.sameName(root, name).filter(applicable(_, a.args.size))
        if cands.sizeIs < 2 then scala.None
        else
          // the ROOT and not the callee's owner: the set is a fact about the type java looked in.
          val ownerName = p.symbolOf(root).map(_.fullName).getOrElse("?")
          val alts      = cands.map(spell).sorted
          def f(i: Issue) = Finding(i, ownerName, s"$name/${a.args.size}", alts, a.origin, declaration)
          val fs = List(
            Option.when(cands.exists(isVararg) && cands.exists(!isVararg(_)))(f(Issue.VarargPhaseSpan)),
            Option.when(boxingSpan(cands))(f(Issue.BoxingPhaseSpan)),
            Option.when(cands.exists(_.tparams.nonEmpty) && cands.exists(_.tparams.isEmpty))(f(Issue.GenericTieBreak)),
          ).flatten
          Some(cands.size -> fs)

  /** the emitter's half — the same answer, without the denominator. */
  def risks(a: Tree.Apply, ov: Overloads, enclosing: SymId = SymId.None)(using Program): List[Finding] =
    analyse(a, ov, enclosing).map(_._2).getOrElse(Nil)

  /** JLS 15.12.2's own arity test, the only part of applicability this check performs. TYPES are
    * deliberately not checked — that is the resolver this is not; reported as the denominator. */
  private def applicable(d: Tree.DefDef, n: Int)(using Program): Boolean =
    val k = d.paramss.flatten.size
    if isVararg(d) then n >= k - 1 else n == k

  private def isVararg(d: Tree.DefDef)(using p: Program): Boolean =
    d.paramss.flatten.lastOption.exists(v => p.symbolOf(v.symbol).exists(_.flags.isVararg))

  /** java's phase 1/2 boundary: two FIXED-arity candidates of the same length whose parameter at
    * some position is a primitive on one side and its wrapper (or a universal slot) on the other.
    * Vararg excluded — its own span is the row above, and reporting both would double-count. */
  private def boxingSpan(cands: List[Tree.DefDef])(using Program): Boolean =
    val fixed = cands.filterNot(isVararg)
    fixed.combinations(2).exists { pair =>
      val (x, y) = (paramTypes(pair.head), paramTypes(pair(1)))
      x.sizeIs == y.size && x.zip(y).exists((l, r) => boxPair(l, r) || boxPair(r, l))
    }

  private def boxPair(prim: TypeRepr, other: TypeRepr)(using Program): Boolean =
    fqn(prim).exists(pr => primitives(pr) &&
      fqn(other).exists(o => wrapperOf.get(pr).contains(o) || universals(o)))

  private def paramTypes(d: Tree.DefDef): List[TypeRepr] = d.paramss.flatten.map(_.tpt.tpe)

  private def spell(d: Tree.DefDef)(using p: Program): String =
    val ps = paramTypes(d).map(t => fqn(t).getOrElse("?"))
    val tp = if d.tparams.isEmpty then "" else "<generic>"
    s"$tp(${ps.mkString(", ")})${if isVararg(d) then "…" else ""}"

  private def fqn(t: TypeRepr)(using p: Program): Option[String] = t match
    case TypeRepr.TypeRef(_, s)      => p.symbolOf(s).map(_.fullName)
    case TypeRepr.AppliedType(tc, _) => fqn(tc)
    case _                           => scala.None

  // -------------------------------------------------------------------------------------------
  // the walk
  // -------------------------------------------------------------------------------------------

  /** every call in this subtree, paired with the class it is WRITTEN IN (the innermost ClassDef
    * containing it, which [[rootOf]] needs for the no-receiver shape) and the MEMBER it is written
    * in (a second, independent question — `Decision.isKeyable`, not a refinement of the class,
    * since an anonymous class's DefDef is a real declaration whose owner is a type). A PRODUCT-
    * REFLECTION walk rather than StandardTraversal, since a traversal phase cannot express "which
    * construct am I INSIDE" — a bottom-up closing-order derivation misattributed a sibling class's
    * call (measured: one BoxingPhaseSpan reported at the wrong owner). TOTAL by construction;
    * descends into an Apply for nested calls; an ANONYMOUS class is not a boundary (its symbol is
    * not in the overload index, so a bare name resolves against the enclosing class). */
  private def callsIn(t: Any, enclosing: SymId, decl: SymId,
                      f: (Tree.Apply, SymId, SymId) => Unit)(using p: Program): Unit = t match
    case c: Tree.ClassDef => c.productIterator.foreach(callsIn(_, c.symbol, SymId.None, f))
    case d: Tree.DefDef   =>
      val at = if Decision.isKeyable(p, d.symbol) then d.symbol else decl
      d.productIterator.foreach(callsIn(_, enclosing, at, f))
    case v: Tree.ValDef if Decision.isKeyable(p, v.symbol) =>
      v.productIterator.foreach(callsIn(_, enclosing, v.symbol, f))
    case a: Tree.Apply    => f(a, enclosing, decl); a.productIterator.foreach(callsIn(_, enclosing, decl, f))
    case xs: Iterable[?]  => xs.foreach(callsIn(_, enclosing, decl, f))
    case Some(x)          => callsIn(x, enclosing, decl, f)
    case p2: Product      => p2.productIterator.foreach(callsIn(_, enclosing, decl, f))
    case _                => ()

  /** Over the units the run EMITS — the same D2 filter every other per-site report carries.
    * @param resolutions what the port SELECTED. Matched at the SITE and not the declaration: a
    *   selection broadcasts across a member, but `ascribe-javac-choice` REFUSES per call, so a
    *   member with two calls may have one answered and one not. Empty is the pre-menu default. */
  def check(program: Program, units: List[Tree.ClassDef], ov: Overloads,
            resolutions: ResolutionPlan = ResolutionPlan.empty): Report =
    given Program = program
    val out    = collection.mutable.ListBuffer.empty[Finding]
    var calls  = 0
    var overld = 0
    units.foreach { u =>
      callsIn(u, u.symbol, SymId.None, { (a, enclosing, decl) =>
        calls += 1
        analyse(a, ov, enclosing, decl).foreach { (_, fs) =>
          overld += 1
          out ++= fs.filterNot(f =>
            resolutions.appliedAt(f.declaration, Name, f.issue.toString, f.origin))
        }
      })
    }
    Report(out.toList.sortBy(f => (f.issue.toString, f.origin.javaPath, f.origin.line, f.member)),
           calls, overld)

  // -------------------------------------------------------------------------------------------
  // THE MENU, CARRIED OUT
  // -------------------------------------------------------------------------------------------

  /** CAN JAVAC'S ALTERNATIVE BE WRITTEN HERE? — the whole of [[AscribeJavacChoice]]'s guard; the
    * `Some`/`None` is the difference between an act and a counted refusal. The answer is a
    * `MethodType`, the SIGNATURE of the member the frontend resolved, minted as a node so every
    * later rename/retype/package move reaches it exactly as any other type (§4.56 — never printed
    * as text). Each `no(...)` arm below is a shape where the ascription would be WRONG rather than
    * merely ugly; every refusal is COUNTED ([[Decline]]), one row per declined SITE naming its
    * guard, since a selection broadcasts across a member and one risky call may be ascribed while
    * a sibling is declined (§3's refusal-enumeration rule read at a menu). */
  def ascription(a: Tree.Apply)(using p: Program): Either[Decline, TypeRepr.MethodType] =
    def no(guard: String, why: String) = Left(Decline(guard, why))
    def bareWildcard(t: TypeRepr): Boolean = t.isInstanceOf[TypeRepr.TypeBounds]
    val fun = a.fun match
      case ta: Tree.TypeApply => ta.fun
      case other              => other
    val shape = fun match
      case Tree.Select(_: Tree.Super, _, _, _) =>
        no("super-receiver", "scala admits `super` only as a member selection's qualifier, so there " +
          "is no method value to ascribe here")
      case _: Tree.Select | _: Tree.Ident => Right(())
      case _ =>
        no("not-a-selection", "the callee is not named by a selection or an identifier, so this is " +
          "not the plain application the ascription's shape assumes")
    shape.flatMap { _ =>
      p.symbolOf(a.method) match
        case scala.None =>
          no("callee-unresolved", "the frontend interned no symbol for this callee, so there is no " +
            "signature to write down — and javac's answer is the whole of what this remedy carries out")
        case Some(s) if s.name == "<init>" =>
          no("constructor", "`new C(x)` has no method value to ascribe at all")
        case Some(s) if s.flags.isStatic =>
          no("static-callee", "java lets a static be called through an INSTANCE and the emitter has " +
            "to move the receiver (`JS-C06`); an ascription here would take that arm's place and " +
            "emit a companion member selected on a value")
        case Some(s) if s.fullName.startsWith("scala.<op>#") =>
          no("operator", "the emitter renders this call infix, and an ascription would have to " +
            "change the call's whole shape")
        case Some(_) =>
          p.definitionOf(a.method).collect { case x: Tree.DefDef => x } match
            case scala.None =>
              no("callee-external", "this program does not DECLARE the callee, so its parameter list " +
                "is a fact about a class file and the candidate set is one this check cannot see")
            case Some(d) =>
              val ps = d.paramss.flatten
              if d.tparams.nonEmpty then
                no("generic-callee", "a polymorphic method value has no plain function type, so the " +
                  "ascription would either not compile or instantiate the parameters at whatever " +
                  "scala infers — which is a different member")
              else if isVararg(d) then
                no("vararg-callee", "`JS-G37` emits the pack as `Array[T]`, so the emitted arity is " +
                  "not java's and a function type built from java's parameters names a signature " +
                  "this port does not have")
              else if ps.sizeIs != a.args.size then
                no("arity-mismatch", s"the call passes ${a.args.size} argument(s) where the callee " +
                  s"declares ${ps.size} — not the plain application this shape assumes")
              else if a.args.exists(_.isInstanceOf[Tree.Repeated]) then
                no("spread-argument", "a spread argument makes this a variable-arity application, " +
                  "whose emitted shape is not the one a function type names")
              else if bareWildcard(d.returnTpt.tpe) || ps.exists(v => bareWildcard(v.tpt.tpe)) then
                no("bare-wildcard", "a parameter or the result is a bare wildcard, and `(?) => R` " +
                  "names nothing")
              else Right(TypeRepr.MethodType(
                ps.map(v => p.symbolOf(v.symbol).map(_.name).getOrElse("_") -> v.tpt.tpe),
                d.returnTpt.tpe))
    }

  /** WHY THE ASCRIPTION COULD NOT BE WRITTEN HERE — the guard the refusal population groups by.
    * Produced by [[ascription]] itself and nothing else, so emission and refusal cannot drift. */
  final case class Decline(guard: String, why: String)

  /** THE MENU, CARRIED OUT — a phase (a resolution must be recorded before emission), since
    * `ascribe-javac-choice` REWRITES A NODE and only a phase may. One decision point: the phase
    * decides, records, and mints the node the emitter renders (never a second emitter-side
    * derivation, §4.56). The ascription is a `Tree.Typed` over the callee, and the `Apply` SURVIVES
    * — wrapping the call in an Opaque would drain the lane as a side effect of the rewrite rather
    * than a recorded move. Per DECLARATION, walked with `StandardTraversal.mapTerm` (§3); the
    * enclosing CLASS handed to `rootOf` is the declaration's own owner. */
  final class Apply extends Phase, PolicyBound:
    def name: String = "overload-risk/remedy"

    private var plan: ResolutionPlan = ResolutionPlan.empty
    private var scope: RunScope      = RunScope.whole
    /** the candidate index, built once per program — the same value the check and the emitter read,
      * so all three answer one question about one program. */
    private var index: Option[(Program, Overloads)] = scala.None

    def bindPolicy(binder: PolicyBinder): Unit =
      plan  = binder.resolutions
      scope = binder.run

    override def run(program: Program): Program =
      if plan.isEmpty then program
      else
        index = Some(program -> new Overloads(program))
        try super.run(program) finally index = scala.None

    /** at a declaration a `resolutions` key can NAME (`Decision.isKeyable`, same as the check).
      * An anonymous class's method is skipped HERE, covered by the enclosing member's own walk. */
    override def transformDefDef(d: Tree.DefDef)(using p: Program): Tree.DefDef =
      if !Decision.isKeyable(p, d.symbol) then d
      else d.rhs.flatMap(rewritten(d.symbol, _)).fold(d)(b => d.copy(rhs = Some(b)))

    /** A FIELD initialiser holds calls too and is a nameable declaration; a LOCAL val is not (its
      * owner is the enclosing member). Decided from OWNERSHIP (§4.56), never body position. */
    override def transformValDef(v: Tree.ValDef)(using p: Program): Tree.ValDef =
      if !Decision.isKeyable(p, v.symbol) then v
      else v.rhs.flatMap(rewritten(v.symbol, _)).fold(v)(b => v.copy(rhs = Some(b)))

    /** bind the declaration, then rewrite the calls in its body. `None` where there is nothing to
      * do at all, so an untouched declaration is returned as itself rather than rebuilt. */
    private def rewritten(decl: SymId, body: Term)(using p: Program): Option[Term] =
      index.collect { case (prog, ov) if (prog eq p) && scope.emitsSymbol(p, decl) => ov }
        .map { ov =>
          val enclosing = p.symbolOf(decl).map(_.owner).getOrElse(SymId.None)
          val subject   = p.symbolOf(decl).map(_.fullName).getOrElse("?")
          val walk = new Phase:
            def name: String = "overload-risk/remedy/site"
            override def transformApply(a: Tree.Apply)(using Program): Term =
              act(a, decl, subject, enclosing, ov)
          StandardTraversal.mapTerm(walk, body)
        }

    /** ONE call. `None` from the plan at every kind is the ordinary answer and the phase's no-op. */
    private def act(a: Tree.Apply, decl: SymId, subject: String, enclosing: SymId, ov: Overloads)
                   (using Program): Term =
      // A call this run has ALREADY answered — the bottom-up traversal hands a nested declaration's
      // body to its enclosing one a second time. Keyed on the site.
      val fs = risks(a, ov, enclosing).filterNot(f =>
        plan.all.exists(x => x.origin == f.origin && x.remedy.lane == Name))
      // one entry per KIND that fired here — one selection key, so at most one remedy answers.
      val chosen = fs.flatMap(f => plan.selected(decl, Name, f.issue.toString).map(f -> _))
      chosen.headOption match
        case scala.None                                                => a
        case Some((_, r)) if r.remedy.id != AscribeJavacChoice.id      =>
          chosen.foreach((f, sel) => record(sel, subject, decl, f,
            s"risk at `${f.member}` accepted by this port"))
          a
        case Some((f0, r0)) =>
          ascription(a) match
            // REFUSED — the alternative cannot be written here, so the findings STAY in the lane and
            // the decline is COUNTED, naming its guard. ONE row per declined SITE and not per KIND:
            // a call files up to three JLS 15.12.2 boundaries and this is one act declining once.
            case Left(d) =>
              plan.refuse(r0, subject, f0.origin, d.guard,
                s"${d.why} — the ${chosen.size} `$Name` row(s) at `${f0.member}` (line " +
                  s"${f0.origin.line}) therefore stay in the lane")
              a
            case Right(mt) =>
              chosen.foreach((f, sel) => record(sel, subject, decl, f,
                s"call to `${f.member}` pinned to the alternative javac bound"))
              a.copy(fun = Tree.Typed(a.fun, TypeTree(mt, a.origin), mt, a.origin))

    /** one ledger row per DRAINED ROW, never one per call: a call may file two JLS 15.12.2
      * boundaries and one act answers both. The SITE's line rides in the text since a selection
      * BROADCASTS and the porter note sits at the DECLARATION — without the line two answered
      * calls read as one duplicated note. */
    private def record(r: Resolution, subject: String, decl: SymId, f: Finding, what: String): Unit =
      plan.applied(r, subject, decl, f.origin,
        s"$what at line ${f.origin.line} (${f.issue}); candidates: ${f.alternatives.mkString(", ")}")

  /** grouped one-line summary, worst family first, each with its §1 classification — and the
    * DENOMINATOR first, since an over-approximation whose rate a reader cannot see gets ignored. */
  def summary(r: Report): String =
    // "and NOT ANSWERED": findings is what remains after the drain, so the third number would
    // otherwise understate the population by the rows a port took responsibility for.
    val scale =
      s"  ${r.calls} program-declared call(s) examined, ${r.overloaded} with more than one " +
        s"applicable candidate, ${r.findings.size} spanning a java resolution phase and not " +
        "answered by a `resolutions` selection"
    val body =
      if r.findings.isEmpty then "  none"
      else
        r.findings.groupBy(_.issue).toList.sortBy((_, v) => -v.size).map { (issue, vs) =>
          val head  = s"  ${vs.size} × $issue\n  ${Issue.classification(issue)}"
          val sites = vs.sortBy(f => (f.origin.javaPath, f.origin.line)).take(10).map("    " + _.render)
          (head :: sites).mkString("\n")
        }.mkString("\n")
    s"$scale\n$body"
