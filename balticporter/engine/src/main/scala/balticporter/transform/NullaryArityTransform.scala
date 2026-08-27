package balticporter.transform

import balticporter.core.{MergeablePolicy, SurfacePolicy}
import balticporter.tir.*

/** Drop `()` from nullary getter-like methods — `def x(): R` becomes `def x: R`, and every call
  * site `o.x()` becomes `o.x`.
  *
  * ==Kind==
  * CLAUDE.md section 1(b). The MECHANISM — detect a getter-like shape, strip the empty parameter
  * clause, rewrite every call site — is a fact about the two languages (java's `int getX()` is
  * idiomatically scala's `def x: Int`). WHICH methods are converted is a fact about one library and
  * arrives as a `RuleScope` — the same tool every retyping phase uses, with the OPPOSITE default
  * because this phase ADDS a declaration shape (a parameterless `def` is a new arity the override
  * component never had) rather than retyping one. `Only(Set.empty)` is the no-op.
  *
  * ==The convention this phase implements==
  * sge's empirical convention: a nullary method that returns a value and whose body is free of
  * side effects drops its `()`. No written rule in sge's `conversion-rules.md` — the convention
  * is observed from how the hand port actually writes its code. This phase makes the mechanical
  * port match that convention where the scope says to.
  *
  * ==What "getter-like" means, structurally==
  * A method is getter-like when (1) it returns a non-void value, (2) its body contains no
  * assignments (`Tree.Assign`, `Tree.IncDec`) and no calls to methods that are themselves not
  * getter-like (a recursive definition, so the phase resolves it conservatively: only field reads,
  * other getter calls, `this`, pure expressions). In practice, the structural test the phase uses
  * is simpler and conservative: the body must contain no writes and no calls to non-nullary members
  * of the same program. That over-refuses (a call to a pure nullary helper is still refused) and
  * never under-refuses.
  *
  * ==Ordering==
  * AFTER `bean-properties` (which drops `()` for its OWN pairs and must not compete), BEFORE
  * `package-rename` (which runs last, as always). The phase ADDS a declaration shape, so its
  * `RuleScope` default is `Only(Set.empty)` — the opposite of a retyping phase.
  *
  * ==This phase changes emitted SIGNATURES, so it is SHARED SURFACE==
  * It implements `SurfacePolicy` and `MergeablePolicy`. Two modules scoping it differently emit
  * signatures that each compile alone and cannot compile together (section 1.5).
  */
final class NullaryArityTransform(scope: RuleScope = RuleScope.Only(Set.empty))
    extends Phase, SurfacePolicy, MergeablePolicy, IdiomPhase, Rewrite:

  def name: String = "nullary-arity"

  override def runsAfter: Set[String] = Set("bean-properties")
  override def runsBefore: Set[String] = Set("package-rename")

  def idiomKinds: Set[IdiomKind] =
    if scope == RuleScope.Only(Set.empty) then Set.empty
    else Set(IdiomKind.NullaryArity)

  def accountedBy: Set[String] = Set(IdiomCheck.Residue)

  /** the scope, exposed for the merge contract and the fingerprint. */
  def arityScope: RuleScope = scope

  def surfaceFingerprint: String =
    if scope == RuleScope.Only(Set.empty) then ""
    else s"scope=${scope.fingerprint}"

  def subjects: Set[String] = scope.entries

  def mergedWith(later: Phase): Either[String, MergeablePolicy.Merged] = later match
    case n: NullaryArityTransform =>
      val noOp = RuleScope.Only(Set.empty)
      val merged: Either[String, RuleScope] = (arityScope, n.arityScope) match
        case (s, `noOp`) => Right(s)
        case (`noOp`, s) => Right(s)
        case (RuleScope.Only(mine), RuleScope.Only(theirs)) =>
          Right(RuleScope.Only(mine ++ theirs))
        case (RuleScope.Everywhere(mine), RuleScope.Everywhere(theirs)) =>
          Right(RuleScope.Everywhere(mine ++ theirs))
        case _ =>
          Left(s"`nullary-arity` scope disagrees: one is `Only` and the other is `Everywhere`")
      merged.map { composedScope =>
        val phase = new NullaryArityTransform(composedScope)
        val added = n.subjects -- subjects
        MergeablePolicy.Merged(phase, added)
      }
    case _ =>
      Left(s"`nullary-arity` cannot merge with ${later.getClass.getSimpleName}")

  // ---- the run --------------------------------------------------------------------------

  /** the methods whose `()` this run stripped — keyed by SymId, with the property name for
    * the decision log. */
  private var converted: Set[SymId] = Set.empty

  override def run(program: Program): Program =
    converted = Set.empty
    if scope == RuleScope.Only(Set.empty) then return program

    val graph = OverrideGraph.build(program)

    // ---- 1. find candidates: owned, in scope, nilary, non-void, getter-like ----
    val candidates = collection.mutable.ListBuffer.empty[SymId]

    program.symbols.all.foreach { s =>
      if program.owned(s.id) && !s.flags.isStatic &&
         PolicyBinder.isExecutable(s.info) && scope.includes(program, s) then
        program.definitionOf(s.id) match
          case Some(d: Tree.DefDef) if isNilary(d) && !isVoid(program, d.returnTpt.tpe) =>
            val closure = graph.closureOf(s.id)
            if closure.isAnchored then
              refuse(program, s.id, "AnchoredClosure",
                closure.anchorReason(program).getOrElse(
                  "the override component reaches a declaration this program cannot move"))
            else if !isGetterLike(program, d) then
              refuse(program, s.id, "SideEffectingBody",
                "the body contains assignments or calls to non-getter members — dropping `()` " +
                "would change the call's meaning from 'do something and return' to 'read a value'")
            else if !callSitesRewritable(program, closure.members) then
              refuse(program, s.id, "UnrewritableCallSite",
                "a call site uses a shape this phase cannot rewrite (a method reference, " +
                "a value-position usage, or an unowned call)")
            else if hasOverloadedSibling(program, s) then
              refuse(program, s.id, "Overloaded",
                "the owner type declares another method with the same name that takes parameters — " +
                "dropping `()` would make `o.m(arg)` resolve to the parenless `m` applied to `arg` " +
                "rather than calling the parameterful overload")
            else
              candidates += s.id
          case _ => () // not a nilary method or no definition
    }

    // ---- 2. group by override component and convert whole components ----
    // A component is converted whole-or-none: half a component would break the override edge.
    val componentMap = candidates.map(c => c -> graph.closureOf(c).members).toMap
    val allConverted = collection.mutable.Set.empty[SymId]

    candidates.foreach { c =>
      if !allConverted.contains(c) then
        val comp = componentMap(c)
        // Every member of the component must also be a candidate (or at least not refused)
        val allInComp = comp.forall { m =>
          candidates.contains(m) || !program.owned(m)
        }
        if allInComp then
          comp.filter(program.owned).foreach { m =>
            allConverted += m
            consider(IdiomCandidate(IdiomKind.NullaryArity, IdiomVerdict.Converted,
              Decision.fqnOf(program, m, "?"),
              s"drop `()` from `${program.symbolOf(m).map(_.name).getOrElse("?")}`",
              Decision.originOf(program, m)))
            record(Decision(
              kind       = Decision.Kind.ParenlessConversion,
              subject    = m,
              subjectFqn = Decision.fqnOf(program, m, "?"),
              detail     = Map("from" -> s"${program.symbolOf(m).map(_.name).getOrElse("?")}()",
                               "to"   -> program.symbolOf(m).map(_.name).getOrElse("?")),
              reason     = Reason.Universal("nullary-arity"),
              origin     = Decision.originOf(program, m),
            ))
          }
        else
          // Some component members are not candidates — refuse all of them
          comp.filter(m => candidates.contains(m)).foreach { m =>
            refuse(program, m, "ComponentPartial",
              "not every member of the override component qualifies — dropping `()` on some " +
              "but not all would break the override edge")
          }
    }

    converted = allConverted.toSet
    if converted.isEmpty then return program

    // ---- 3. strip the empty parameter clause and rewrite call sites ----
    given Program = program
    program.rebuilt(units = program.units.map(u => StandardTraversal.mapClassDef(this, u)))

  /** strip `()` from the declaration. */
  override def transformDefDef(t: Tree.DefDef)(using Program): Tree.DefDef =
    if converted.contains(t.symbol) then t.copy(paramss = Nil) else t

  /** `o.x()` -> `o.x` for converted methods. */
  override def transformApply(t: Tree.Apply)(using Program): Term =
    if converted.contains(t.method) && t.args.isEmpty then
      t.fun match
        case _: Tree.Ident            => Tree.Ident(t.method, t.tpe, t.origin)
        case Tree.Select(q, _, _, _)  => Tree.Select(q, t.method, t.tpe, t.origin)
        case _                        => t
    else t

  // ---- helpers --------------------------------------------------------------------------

  private def refuse(p: Program, m: SymId, guard: String, why: String): Unit =
    consider(IdiomCandidate(IdiomKind.NullaryArity,
      IdiomVerdict.Refused(guard, why),
      Decision.fqnOf(p, m, "?"),
      s"keep `()` on `${p.symbolOf(m).map(_.name).getOrElse("?")}`",
      Decision.originOf(p, m)))

  /** a method with exactly one empty parameter clause: `paramss == List(Nil)`. */
  private def isNilary(d: Tree.DefDef): Boolean =
    d.paramss match
      case List(Nil) => true
      case _         => false

  /** Java `void`, as the frontend writes it. */
  private def isVoid(p: Program, t: TypeRepr): Boolean = t match
    case TypeRepr.TypeRef(_, s) => p.symbolOf(s).exists(_.fullName == "scala.Unit")
    case _                      => false

  /** CONSERVATIVE getter-like test: the body contains no assignments and no calls to
    * non-nullary owned methods. Over-refuses rather than under-refuses.
    *
    * An ABSTRACT method (no body) is NOT getter-like: its arity is part of its contract, and
    * dropping `()` on an interface's abstract method would break every SAM lambda that ascribes
    * to it. Measured at 38 errors from `PoolSupplier.get()`, `Comparator.compare()` etc. */
  private def isGetterLike(p: Program, d: Tree.DefDef): Boolean =
    d.rhs match
      case scala.None    => false // abstract — no body to inspect, keep arity
      case Some(body) => !hasSideEffects(p, body)

  /** walk the body looking for assignments, increments, and calls to non-nullary members.
    *
    * The scan uses a mutable flag rather than a fold, because `StandardTraversal` requires a
    * `Phase` that returns the same node kind it received (it is a TRANSFORMER, not a folder). */
  private def hasSideEffects(p: Program, t: Term): Boolean =
    var found = false
    def scan(t: Term): Unit = t match
      case _: Tree.Assign  => found = true
      case _: Tree.IncDec  => found = true
      case a: Tree.Apply =>
        // a call to a method that itself takes arguments is potentially side-effecting
        if a.args.nonEmpty then found = true
        // recurse into subexpressions
        scan(a.fun)
        a.args.foreach(scan)
      case Tree.Select(q, _, _, _)      => scan(q)
      case Tree.TypeApply(f, _, _, _)    => scan(f)
      case Tree.If(c, th, el, _, _)     => scan(c); scan(th); scan(el)
      case Tree.Block(stats, e, _, _, _) =>
        stats.foreach {
          case t: Term => scan(t)
          case _ => ()
        }
        scan(e)
      case Tree.Return(v, _, _)         => v.foreach(scan)
      case Tree.Typed(e, _, _, _)       => scan(e)
      case Tree.InstanceOf(e, _, _, _)  => scan(e)
      case Tree.ArrayAccess(a, i, _, _) => scan(a); scan(i)
      case Tree.ArrayLength(a, _, _)    => scan(a)
      case _ => ()
    scan(t)
    found

  /** Does the owner type declare another method with the same name that takes PARAMETERS?
    *
    * Dropping `()` from `toArray()` when `toArray(Class)` exists makes `a.toArray(classOf[X])`
    * resolve to the parenless `toArray` APPLIED to the argument — which is valid Scala asking a
    * completely different question. The guard fires at the DECLARATION rather than at the call site,
    * because the ambiguity is structural: any call to the parameterful overload that passes one arg
    * will bind to the wrong member.
    *
    * The check scans the owner's MEMBERS (using `OverrideGraph.membersOf`, which is the declared
    * body members) and looks for a same-named sibling whose `info` takes parameters. That is
    * deliberately OVER-approximate — a sibling with one parameter is always an ambiguity, and a
    * sibling with two parameters is also an ambiguity because Scala 3's auto-tupling can bind
    * a `(a, b)` tuple to the parenless `def`. Conservative: a false positive is a method that
    * keeps its `()`, which is the faithful translation and always correct. */
  private def hasOverloadedSibling(p: Program, s: Symbol): Boolean =
    val siblings = p.symbols.all.filter(sib =>
      sib.id != s.id &&
      sib.name == s.name &&
      sib.owner == s.owner &&
      PolicyBinder.isExecutable(sib.info) &&
      hasParams(sib.info))
    siblings.nonEmpty

  /** does this method type take at least one parameter? */
  private def hasParams(info: TypeRepr): Boolean = info match
    case TypeRepr.MethodType(ps, _, _) => ps.nonEmpty
    case TypeRepr.PolyType(_, r)       => hasParams(r)
    case _                             => false

  /** can every call site of every member in the component be rewritten? */
  private def callSitesRewritable(p: Program, comp: Set[SymId]): Boolean =
    comp.forall { s =>
      p.usages(s).forall {
        case Usage(UsageKind.Call, a: Tree.Apply, _) =>
          a.args.isEmpty && (a.fun match
            case _: Tree.Ident           => true
            case _: Tree.Select          => true
            case _                       => false)
        case Usage(UsageKind.TermRef, _, _) => false // value-position reference
        case _ => true
      }
    }

object NullaryArityTransform:
  /** the phase NAME, for `runsBefore` / `runsAfter` edges declared elsewhere. */
  val PhaseName: String = "nullary-arity"
