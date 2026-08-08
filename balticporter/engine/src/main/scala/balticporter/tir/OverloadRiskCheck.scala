package balticporter.tir

import balticporter.catalog.FixKind

/** Every emitted CALL whose candidate set spans one of JAVA'S OWN RESOLUTION PHASES — the risk
  * catalog rows `JS-C22` (JLS 15.12.2) and `JS-C23` (JLS 15.12.2.5) carry, counted rather than
  * resolved.
  *
  * ==What the difference is==
  * Java chooses an overload in THREE PHASES: strict (no boxing, no varargs), then loose (boxing and
  * unboxing allowed), then variable-arity. A candidate admitted in an earlier phase WINS outright —
  * javac never looks at a later phase once one succeeds. Scala resolves in ONE phase, with
  * conversions and default arguments in scope, and then applies its own most-specific rule, which
  * PREFERS A NON-GENERIC ALTERNATIVE where java's does not (`JS-C23`). So a call the java compiler
  * bound to one member can bind to another in the port, silently, with no compile error: both
  * alternatives typecheck, which is the entire premise of the difference.
  *
  * ==Why this is a COUNTER and never a resolver==
  * Predicting the divergence means modelling scala's overload resolution well enough to disagree
  * with javac about a program neither compiler has rejected — a compiler-sized project, and
  * `ENGINE-LIMITS.md` says so rather than half-building one. What is affordable, and what was a
  * total silence before, is the RISK: the calls where the two rules can possibly differ.
  *
  * ==The population is derived from JLS 15.12.2'S OWN PHASE BOUNDARIES==
  * Not from "this call is overloaded", which is most calls in a library and a review list nobody
  * reads. The three phases are separated by exactly two things — BOXING (phase 1 to 2) and VARARGS
  * (phase 2 to 3) — and the tie-break inside a phase is where the generic rule differs. So there are
  * exactly three ways for the java rule and the scala rule to disagree, and each is a fact about the
  * CANDIDATE SET alone:
  *
  *   - [[Issue.VarargPhaseSpan]] — a fixed-arity candidate and a variable-arity candidate are both
  *     applicable to this argument count. Java tries the fixed one first and only falls back;
  *     scala has no such staging;
  *   - [[Issue.BoxingPhaseSpan]] — two applicable candidates take a PRIMITIVE and its WRAPPER (or a
  *     universal type) at the same position. Java admits the primitive one in phase 1 and the other
  *     only in phase 2; scala boxes freely and picks by specificity;
  *   - [[Issue.GenericTieBreak]] — an applicable candidate is GENERIC and another is not
  *     (`JS-C23`). Java's most-specific rule does not prefer the non-polymorphic alternative;
  *     scala's relative-weight rule does.
  *
  * A candidate set separated only by unrelated REFERENCE types is deliberately not reported: both
  * languages admit those in one phase and pick by applicability, and reporting them would bury the
  * three real spans under every overload in the library. That is a NARROWING and it is stated rather
  * than hidden — see [[Report]], which carries its own denominator so the over-approximation's rate
  * is a number on every run and not a claim in this comment.
  *
  * ==Three limits, all structural==
  * The candidate set is what the PROGRAM DECLARES. An external callee's overloads live in a class
  * file the frontend interns lazily and only on reference, so a call into the JDK or a dependency
  * has a candidate set this check cannot see — it is not reported, and that is stated rather than
  * counted as a zero. And ancestors are followed only where the program declares them: an inherited
  * overload from an external supertype is invisible for the same reason. The third is `super.f(x)`,
  * whose candidate set is the SUPERCLASS's and which is therefore left at the callee's own owner
  * rather than widened to the receiver's type — see [[rootOf]] and `ENGINE-LIMITS.md` T17.
  */
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

  /** PIN THE ALTERNATIVE JAVAC BOUND — the ascription, and the only face of this lane the engine can
    * mechanise.
    *
    * ==What is derivable and what is not==
    * `ENGINE-LIMITS.md` T17 rules out the general act: predicting WHICH member scala will bind means
    * modelling scala's own resolution — implicits, defaults, relative weight — well enough to
    * disagree with javac about a program neither compiler has rejected, which is a compiler-sized
    * project. That is not what this remedy does, and the difference is the whole of its safety
    * argument: **which member JAVAC bound is not predicted, it is READ** — the frontend resolved the
    * call, so `Tree.Apply.method` IS javac's answer. What the remedy carries out is naming that
    * answer explicitly, and the only question left is whether the name can be WRITTEN.
    *
    * ==So the shape is a METHOD-VALUE ASCRIPTION, which is already shipped==
    * `(recv.m: (A, B) => R)(x, y)`. Scala picks an overload at an ascribed method value by the
    * EXPECTED TYPE, which is exactly what pins it, and `TirEmitter.numericOverloadAscription` has
    * emitted this shape unconditionally for one closed face (exact-match-against-widening at a
    * numeric literal) since before this menu existed. This is that emission with its trigger moved
    * from a hard-coded predicate to a port's selection — not a new mechanism.
    *
    * ==And it REFUSES wherever the name cannot be written, which is counted rather than guessed==
    * See [[ascription]] for the enumeration. A refusal records nothing, so the finding stays in the
    * lane, and a selection that refused everywhere is reported as `NeverApplied` rather than as
    * silence — which is the difference between a remedy that did not apply and a lane that stopped
    * asking.
    *
    * EMISSION-AFFECTING, and it is the reason the field exists: two modules ascribing one shared
    * declaration's call differently would emit two ports that each compile alone (§1.5).
    */
  val AscribeJavacChoice: Remedy = Remedy(
    id = "ascribe-javac-choice", lane = Name, kind = Issue.VarargPhaseSpan.toString,
    emissionAffecting = true, fix = FixKind.Universal,
    what = "name the alternative javac bound, as a method-value ascription, so scala's " +
      "single-phase resolution cannot pick another",
    alsoKinds = AllKinds.filterNot(_ == Issue.VarargPhaseSpan.toString))

  /** …and the other answer, which is a STATEMENT and not an act: the operator read this call, read
    * the candidates the finding names, and accepts that scala may bind another.
    *
    * Not a suppression. The row moves into `remediation(resolved)` with the port's name on it and a
    * porter note beside the emitted call, so what was an unexamined risk becomes an examined one —
    * which is the only thing that can ever empty an over-approximated lane honestly. A port that
    * merely wants the number smaller has no way to write this without saying, per member, that
    * somebody looked.
    *
    * NOT emission-affecting: it changes no tree, so two modules may disagree about it exactly as
    * they may disagree about `verdictOverrides`.
    */
  val AcceptRisk: Remedy = Remedy(
    id = "accept-risk", lane = Name, kind = Issue.VarargPhaseSpan.toString,
    emissionAffecting = false, fix = FixKind.Universal,
    what = "the operator read this call's candidate set and accepts that scala may bind a " +
      "different alternative than javac did",
    alsoKinds = AllKinds.filterNot(_ == Issue.VarargPhaseSpan.toString))

  /** …and WHAT IS NOT ON THE MENU, stated where the menu is so a reader sees a refusal and not a gap.
    *
    *   - '''resolve the call — auto-ascribe at every spanning site'''. RULED OUT, `ENGINE-LIMITS.md`
    *     T17: an ENGINE act needs to know that scala and javac disagree HERE, and that is scala's
    *     resolution modelled well enough to contradict javac about a program both compilers accept.
    *     [[AscribeJavacChoice]] is not a weaker version of it — it does not predict anything, it
    *     writes down an answer the frontend already has, and it fires only where a PORT asked;
    *   - '''ascribe the ARGUMENT rather than the method'''. REFUSED: it does not pin anything. The
    *     candidates in a `BoxingPhaseSpan` differ by a primitive against its wrapper, and an
    *     argument ascribed to either one still admits both alternatives under scala's single phase —
    *     the expected type has to sit on the METHOD for the choice to be made there;
    *   - '''a per-callee table — "always bind `remove(int)`"'''. REFUSED: the phase java resolved in
    *     is a fact about the ARGUMENTS at one site, not about the member, so a table would state one
    *     answer for calls java answered differently. The key is per member for exactly this reason
    *     (`DESIGN.md` §8.16), and the finding names the candidates so the reader decides per site;
    *   - '''emit both and let scalac pick'''. There is nothing to emit: both alternatives typecheck,
    *     which is the entire premise of the lane.
    */
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

  /** …and their wrappers, which is the OTHER side of JLS 5.1.7's boxing conversion — the one
    * conversion that separates java's phase 1 from its phase 2. The wrapper keeps its JAVA name:
    * `java.lang.Integer` is a class the port references and does not declare. */
  private val wrapperOf = Map(
    "scala.Byte" -> "java.lang.Byte", "scala.Short" -> "java.lang.Short",
    "scala.Char" -> "java.lang.Character", "scala.Int" -> "java.lang.Integer",
    "scala.Long" -> "java.lang.Long", "scala.Float" -> "java.lang.Float",
    "scala.Double" -> "java.lang.Double", "scala.Boolean" -> "java.lang.Boolean")

  /** a slot that admits a BOXED value and nothing narrower. Deliberately just these three and not
    * `java.lang.Number`/`Comparable`: a wrapper's supertypes admit the box too, but so do a dozen
    * other reference types, and widening this set is how a phase-boundary count turns into an
    * everything-is-overloaded count. The narrowing is stated in [[Report]]'s denominator. */
  private val universals = Set("java.lang.Object", "scala.Any", "scala.AnyRef")

  /** the head symbol of a type, for the receiver root below and for [[Overloads]]'s parent walk —
    * one function, because the two ask the same question and a `ThisType` reaching only one of them
    * is how an implicit receiver stops resolving. */
  private def headSym(t: TypeRepr): Option[SymId] = t match
    case TypeRepr.TypeRef(_, s)      => Some(s)
    case TypeRepr.AppliedType(tc, _) => headSym(tc)
    case TypeRepr.ThisType(c)        => Some(c)
    case _                           => scala.None

  /** WHERE JAVA LOOKED — the receiver's STATIC TYPE, which is the type whose members (its own and
    * its inherited ones) JLS 15.12.1 makes the candidate set.
    *
    * Not the resolved callee's OWNER, which is where the winner happened to be DECLARED. The two
    * coincide whenever the winner is the most derived declaration and differ exactly when it is
    * not: javac binds `f(1)` to an inherited `P.f(int)` in phase 1 while the subclass `C` declares
    * `f(Integer)`, so an upward-only climb from `P` never sees the candidate that spans the
    * boundary — the `BoxingPhaseSpan` this lane exists for, invisible in the one direction it is
    * most likely to arrive in. Rooting at the receiver is a strict WIDENING of the old set (the
    * owner is always the receiver's type or an ancestor of it), so nothing previously reported can
    * be lost.
    *
    * Three shapes, and the fallback is what keeps the widening honest:
    *
    *   - a SELECT — the qualifier's type head. `T.f(x)`, `x.f(y)` and the implicit `this.f(y)` are
    *     all this, because the frontend renders the last as a `This` qualifier whose type is a
    *     `ThisType`;
    *   - a bare IDENT — the ENCLOSING class, which is what java resolves a simple name against and
    *     which no node in the expression carries. The caller supplies it (the check tracks it on
    *     the traversal, the emitter has it on its class stack); `SymId.None` where it does not, and
    *     the fallback then answers;
    *   - `super.f(x)` — NOT widened. Java resolves it over the SUPERCLASS's members and the
    *     receiver root here would be the subclass's, which is a set java never considered. Left at
    *     the callee's owner and stated in `ENGINE-LIMITS.md` T17.
    *
    * The guard is the whole of the safety argument: a root is used only where its candidate set
    * CONTAINS the member javac actually bound. Java's set must contain the winner, so a root whose
    * set does not is a root this reasoning got wrong — an unowned receiver type, a type variable, a
    * `null` enclosing class — and the pre-widening root answers instead. */
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

  /** WHAT THIS CALL RISKS, or nothing.
    *
    * `None` where the question does not arise at all — an external callee (its overloads are in a
    * class file), or fewer than two program-declared candidates applicable to this argument count.
    * `Some(n, fs)` otherwise, with `n` the size of the applicable candidate set, so a caller that
    * wants the denominator gets it from the same computation that produced the findings and the two
    * cannot disagree.
    *
    * `enclosing` is the class the call is written IN — see [[rootOf]], which needs it for the one
    * shape that carries no receiver. `SymId.None` is honest and costs only that shape's widening.
    *
    * `declaration` is the MEMBER it is written in, which nothing here reads and which every row
    * carries: it is the granularity a `resolutions` key has, so a caller that wants to ask "did the
    * port choose something for this row?" needs it on the row rather than re-derived from a second
    * walk (`CLAUDE.md` §4.56 — two derivations of one fact are free to disagree). */
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
          // the ROOT and not the callee's owner: the row's whole job is to let a reader re-derive
          // the candidate set without re-deriving it, and the set is a fact about the type java
          // looked in.
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

  /** JLS 15.12.2's own arity test, and the only part of applicability this check performs: a
    * fixed-arity candidate takes exactly its parameter count, a variable-arity one takes that many
    * less the pack, or more. TYPES are deliberately not checked — that is the resolver this is not,
    * and the narrowing is reported as the denominator rather than assumed away. */
  private def applicable(d: Tree.DefDef, n: Int)(using Program): Boolean =
    val k = d.paramss.flatten.size
    if isVararg(d) then n >= k - 1 else n == k

  private def isVararg(d: Tree.DefDef)(using p: Program): Boolean =
    d.paramss.flatten.lastOption.exists(v => p.symbolOf(v.symbol).exists(_.flags.isVararg))

  /** java's phase 1 / phase 2 boundary, read off the candidates: two FIXED-arity candidates of the
    * same length whose parameter at some position is a primitive on one side and that primitive's
    * wrapper (or a universal slot) on the other. A vararg candidate is excluded here because its
    * own span is the row above, and reporting both for one pair would double-count one call. */
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

  /** every call in this subtree, paired with the class it is WRITTEN IN — the innermost `ClassDef`
    * that CONTAINS it.
    *
    * `rootOf` needs that class for the one shape carrying no receiver at all, so getting it wrong
    * is not a reporting detail: it reads the candidate set out of a type java never looked in.
    *
    * ==WHY A PRODUCT-REFLECTION WALK AND NOT `StandardTraversal`==
    *
    * `Jumps`'s reason, one construct over. A `StandardTraversal` phase reaches every node by
    * contract and cannot express "which construct am I INSIDE", so the attribution used to be
    * derived from the traversal's bottom-up ORDER: hold each call unclaimed until some `ClassDef`
    * closes, on the stated invariant that the first one to close is the innermost one containing
    * it. That is false for a SIBLING — `void go() { f(1); } static class Inner extends A { … }`
    * hands `go`'s call to `Inner`, whose own overload set climbs to the callee and therefore passes
    * `rootOf`'s guard. Measured: one `BoxingPhaseSpan` reported at owner `A$Inner` for a call java
    * had no choice about at all.
    *
    * The walk is TOTAL by construction — every node reaches the `Product` arm, so no node kind can
    * be forgotten (§3's real concern) — and it descends INTO an `Apply` because a call's arguments
    * hold calls of their own. An ANONYMOUS class is deliberately not a boundary: `Tree.AnonClass`
    * is not a `ClassDef`, its symbol is not in the overload index, and a bare name written there
    * resolves against the enclosing class exactly as `rootOf` would then answer.
    *
    * …and with the MEMBER it is written in beside the class, which is a second question and not a
    * refinement of the first: the class is what java resolved the NAME against, the member is what a
    * `resolutions` key names. A `ValDef` counts only where no member is open — that is a FIELD, whose
    * initialiser holds calls and which a port can key on, while a `ValDef` reached INSIDE a member is
    * a local variable whose symbol no manifest grammar can name. Decided from the walk's own state
    * (the `ClassDef` arm resets it) rather than from a symbol lookup, because this walk holds no
    * `Program` — and the two agree by construction: a declaration directly in a class body is
    * exactly one whose owner is that class. */
  private def callsIn(t: Any, enclosing: SymId, decl: SymId,
                      f: (Tree.Apply, SymId, SymId) => Unit): Unit = t match
    case c: Tree.ClassDef => c.productIterator.foreach(callsIn(_, c.symbol, SymId.None, f))
    case d: Tree.DefDef   => d.productIterator.foreach(callsIn(_, enclosing, d.symbol, f))
    case v: Tree.ValDef if decl == SymId.None =>
      v.productIterator.foreach(callsIn(_, enclosing, v.symbol, f))
    case a: Tree.Apply    => f(a, enclosing, decl); a.productIterator.foreach(callsIn(_, enclosing, decl, f))
    case xs: Iterable[?]  => xs.foreach(callsIn(_, enclosing, decl, f))
    case Some(x)          => callsIn(x, enclosing, decl, f)
    case p: Product       => p.productIterator.foreach(callsIn(_, enclosing, decl, f))
    case _                => ()

  /** Over the units the run EMITS — the same D2 filter every other per-site report carries.
    *
    * @param resolutions what the port SELECTED, as the phase recorded it. A row a remedy answered
    *   has left this lane for `remediation(resolved)` and must not be counted twice (`CLAUDE.md`
    *   §5). Matched at the SITE and not at the declaration: a selection broadcasts across a member,
    *   but `ascribe-javac-choice` REFUSES at a call whose alternative cannot be written, so a member
    *   with two calls may have one answered and one not — drained per declaration the lane would
    *   fall by two where `resolved` gained one. Empty is the default and the pre-menu behaviour.
    */
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

  /** CAN JAVAC'S ALTERNATIVE BE WRITTEN HERE? — the whole of [[AscribeJavacChoice]]'s guard, and the
    * `Some`/`None` is the difference between an act and a counted refusal.
    *
    * The answer is a `MethodType`, which is the SIGNATURE of the member the frontend resolved: the
    * emitter renders it `(A, B) => R` through its own type printer, so every rename, retype and
    * package move that runs after this phase reaches it exactly as it reaches any other type. A
    * phase that produced the ascription as TEXT would be writing the upstream namespace into the
    * output (§4.56) — which is why the phase mints a node and prints nothing.
    *
    * Every arm below is a shape where the ascription would be WRONG rather than merely ugly, and
    * each is refused rather than approximated:
    *
    *   - a GENERIC callee — a polymorphic method value has no plain function type, so the
    *     ascription would either not compile or instantiate the parameters at whatever scala infers,
    *     which is a different member;
    *   - a VARARG callee — `JS-G37` emits its pack as `Array[T]`, so the emitted arity is not java's
    *     and a function type built from java's parameters names a signature the port does not have;
    *   - a CONSTRUCTOR — `new C(x)` has no method value to ascribe at all;
    *   - an OPERATOR (`scala.<op>#…`) — the emitter renders it infix, and an ascription would have
    *     to change the call's whole shape;
    *   - a STATIC callee — java lets a static be called through an INSTANCE and the emitter has to
    *     move the receiver (JS-C06); wrapping the callee here would take that arm's place and emit a
    *     companion member selected on a value;
    *   - a `super` receiver — scala admits `super` only as a member selection's qualifier, so there
    *     is no method value to ascribe (and `rootOf` already leaves such a call at its own owner);
    *   - a SPREAD argument, or an argument count that is not the callee's — the call is not the
    *     plain application this shape assumes;
    *   - a parameter or result type that is a bare WILDCARD — `(?) => R` names nothing.
    *
    * A refusal records NOTHING, so the finding stays in the lane: the residue is what says the port
    * asked for something the engine could not do here, and a selection that refused everywhere is
    * reported as `NeverApplied`.
    */
  def ascription(a: Tree.Apply)(using p: Program): Option[TypeRepr.MethodType] =
    def bareWildcard(t: TypeRepr): Boolean = t.isInstanceOf[TypeRepr.TypeBounds]
    val fun = a.fun match
      case ta: Tree.TypeApply => ta.fun
      case other              => other
    val shapeOk = fun match
      case Tree.Select(_: Tree.Super, _, _, _) => false
      case _: Tree.Select | _: Tree.Ident      => true
      case _                                   => false
    for
      s <- p.symbolOf(a.method)
      if shapeOk && s.name != "<init>" && !s.flags.isStatic && !s.fullName.startsWith("scala.<op>#")
      d <- p.definitionOf(a.method).collect { case x: Tree.DefDef => x }
      if d.tparams.isEmpty && !isVararg(d)
      ps = d.paramss.flatten
      if ps.sizeIs == a.args.size
      if !a.args.exists(_.isInstanceOf[Tree.Repeated])
      if !bareWildcard(d.returnTpt.tpe) && !ps.exists(v => bareWildcard(v.tpt.tpe))
    yield TypeRepr.MethodType(
      ps.map(v => p.symbolOf(v.symbol).map(_.name).getOrElse("_") -> v.tpt.tpe),
      d.returnTpt.tpe)

  /** THE MENU, CARRIED OUT — a phase, for `HeapPollutionCheck.Apply`'s reason (a resolution has to be
    * recorded before emission, and this check runs after it) plus one this lane adds: the
    * `ascribe-javac-choice` half REWRITES A NODE, and only a phase may.
    *
    * ==One decision point, so the count and the emission cannot disagree==
    * The phase decides, records, and mints the node the emitter renders. The alternative — the
    * emitter re-deriving "did the port select here?" while the phase records — is two derivations of
    * one fact, free to drift, and `CLAUDE.md` §4.56 is a list of exactly that failure. What the
    * emitter contributes is the one thing a phase may not do: PRINTING (the ascription's type text).
    *
    * ==The ascription is a `Tree.Typed` over the callee, and the `Apply` survives==
    * Wrapping the whole call in an `Opaque` would drain the lane structurally — the check would stop
    * seeing a call there at all — and that is precisely what makes it wrong: the drain would then be
    * a SIDE EFFECT of the rewrite rather than a recorded move, and a call whose ascription refused
    * would be indistinguishable from one that was never asked about. Keeping the `Apply` keeps every
    * row visible and lets the ledger say which of them moved.
    *
    * ==Per DECLARATION, walked with `StandardTraversal`==
    * The selection key is a member, so the phase asks at each `DefDef`/`ValDef` and rewrites within
    * it. The inner walk is `StandardTraversal.mapTerm` — never a private recursion (§3) — and the
    * enclosing CLASS handed to `rootOf` is the declaration's own owner, which is what java resolved a
    * bare name against.
    */
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

    override def transformDefDef(d: Tree.DefDef)(using p: Program): Tree.DefDef =
      d.rhs.flatMap(rewritten(d.symbol, _)).fold(d)(b => d.copy(rhs = Some(b)))

    /** A FIELD initialiser holds calls too, and a field is a declaration a `resolutions` key can
      * name. A LOCAL `val` is not: it is a statement inside a member, its symbol's owner is that
      * member, and attributing a call to it would hand every port a key naming something no manifest
      * grammar reaches. Decided from OWNERSHIP (§4.56) — the owner is a class — never from position
      * in a body. */
    override def transformValDef(v: Tree.ValDef)(using p: Program): Tree.ValDef =
      if !isField(v.symbol) then v
      else v.rhs.flatMap(rewritten(v.symbol, _)).fold(v)(b => v.copy(rhs = Some(b)))

    private def isField(s: SymId)(using p: Program): Boolean =
      p.symbolOf(s).map(_.owner).flatMap(p.definitionOf).exists(_.isInstanceOf[Tree.ClassDef])

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
      // body to its enclosing one a second time, and acting twice would put two ledger rows and two
      // ascriptions on one site. Keyed on the site, which is what the ledger records.
      val fs = risks(a, ov, enclosing).filterNot(f =>
        plan.all.exists(x => x.origin == f.origin && x.remedy.lane == Name))
      // one entry per KIND that fired here — the selection is one key, so at most one remedy answers.
      val chosen = fs.flatMap(f => plan.selected(decl, Name, f.issue.toString).map(f -> _))
      chosen.headOption match
        case scala.None                                                => a
        case Some((_, r)) if r.remedy.id != AscribeJavacChoice.id      =>
          chosen.foreach((f, sel) => record(sel, subject, decl, f,
            s"risk at `${f.member}` accepted by this port"))
          a
        case Some(_) =>
          ascription(a) match
            // REFUSED — the alternative cannot be written here, so nothing is recorded and every
            // finding stays in the lane. See `ascription` for the enumeration.
            case scala.None     => a
            case Some(mt) =>
              chosen.foreach((f, sel) => record(sel, subject, decl, f,
                s"call to `${f.member}` pinned to the alternative javac bound"))
              a.copy(fun = Tree.Typed(a.fun, TypeTree(mt, a.origin), mt, a.origin))

    /** one ledger row per DRAINED ROW, never one per call: a call may have filed two of JLS
      * 15.12.2's boundaries and one act answers both, so the lane falls by two and
      * `remediation(resolved)` must gain two. */
    private def record(r: Resolution, subject: String, decl: SymId, f: Finding, what: String): Unit =
      plan.applied(r, subject, decl, f.origin,
        s"$what (${f.issue}); candidates: ${f.alternatives.mkString(", ")}")

  /** grouped one-line summary, worst family first, each with its §1 classification — and the
    * DENOMINATOR first, because an over-approximation whose rate a reader cannot see is one they
    * will learn to ignore. */
  def summary(r: Report): String =
    val scale =
      s"  ${r.calls} program-declared call(s) examined, ${r.overloaded} with more than one " +
        s"applicable candidate, ${r.findings.size} spanning a java resolution phase"
    val body =
      if r.findings.isEmpty then "  none"
      else
        r.findings.groupBy(_.issue).toList.sortBy((_, v) => -v.size).map { (issue, vs) =>
          val head  = s"  ${vs.size} × $issue\n  ${Issue.classification(issue)}"
          val sites = vs.sortBy(f => (f.origin.javaPath, f.origin.line)).take(10).map("    " + _.render)
          (head :: sites).mkString("\n")
        }.mkString("\n")
    s"$scale\n$body"
