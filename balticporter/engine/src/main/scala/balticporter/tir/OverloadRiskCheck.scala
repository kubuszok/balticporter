package balticporter.tir

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
object OverloadRiskCheck:

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

  /** one call at risk. `alternatives` names the candidates so a reader can dismiss the row without
    * re-deriving the candidate set, which is the whole cost of an over-approximation. */
  final case class Finding(issue: Issue, owner: String, member: String,
                           alternatives: List[String], origin: Origin):
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
    * shape that carries no receiver. `SymId.None` is honest and costs only that shape's widening. */
  def analyse(a: Tree.Apply, ov: Overloads, enclosing: SymId = SymId.None)(using p: Program): Option[(Int, List[Finding])] =
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
          def f(i: Issue) = Finding(i, ownerName, s"$name/${a.args.size}", alts, a.origin)
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
    * resolves against the enclosing class exactly as `rootOf` would then answer. */
  private def callsIn(t: Any, enclosing: SymId, f: (Tree.Apply, SymId) => Unit): Unit = t match
    case c: Tree.ClassDef => c.productIterator.foreach(callsIn(_, c.symbol, f))
    case a: Tree.Apply    => f(a, enclosing); a.productIterator.foreach(callsIn(_, enclosing, f))
    case xs: Iterable[?]  => xs.foreach(callsIn(_, enclosing, f))
    case Some(x)          => callsIn(x, enclosing, f)
    case p: Product       => p.productIterator.foreach(callsIn(_, enclosing, f))
    case _                => ()

  /** Over the units the run EMITS — the same D2 filter every other per-site report carries. */
  def check(program: Program, units: List[Tree.ClassDef], ov: Overloads): Report =
    given Program = program
    val out    = collection.mutable.ListBuffer.empty[Finding]
    var calls  = 0
    var overld = 0
    units.foreach { u =>
      callsIn(u, u.symbol, { (a, enclosing) =>
        calls += 1
        analyse(a, ov, enclosing).foreach { (_, fs) => overld += 1; out ++= fs }
      })
    }
    Report(out.toList.sortBy(f => (f.issue.toString, f.origin.javaPath, f.origin.line, f.member)),
           calls, overld)

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
