package balticporter.emit

import balticporter.core.*
import balticporter.core.BExpr.*

/** How one field renders. Pure structure — the printer does all rendering. */
enum FieldLine:
  /** ordinary field declaration with an initializer (own or fused from the single ctor). */
  case FromField(f: BField, init: BExpr)
  /** null-sentinel merge: `val f: T = if (_p != null) _p else <default>`. */
  case SentinelVal(f: BField, paramName: String, default: BExpr)
  /** generalized sentinel: `val f: T = if (_p != null) <whenSome> else <whenNull>`. */
  case CondInit(f: BField, paramName: String, whenSome: BExpr, whenNull: BExpr)
  /** no initializer usable at the declaration → Java default value, as `var`.
    * For final fields this is the definite-assignment fallback (RESEARCH.md §4.2:
    * ctor assigns inside branches/try → lift is impossible mechanically; `var` +
    * in-body assignment preserves behavior at the cost of final-field publication). */
  case DefaultInit(f: BField)

/** The primary/secondary constructor layout for a class (PLAN.md funnel strategies).
  *
  * Java constructor graphs don't map 1:1 onto Scala's primary/auxiliary model
  * (auxiliaries can never call `super`); this module picks one of three shapes:
  *
  *  1. zero/one constructor → it becomes the primary; a final field assigned
  *     exactly once as `this.f = f` from the same-named parameter becomes a
  *     `val` class parameter; other single-assigned final fields become
  *     `val f = expr` members; remaining body statements stay as class body;
  *  2. several constructors with empty bodies where exactly one calls `super`
  *     with exactly its own parameters → that one is primary, the others
  *     delegate (`def this(...) = this(<their super/this args>)`);
  *  3. exactly two constructors — `(p)` assigning a final ref-typed field from
  *     its parameter and `()` assigning the same field from a no-param
  *     expression — merge via the null-sentinel idiom used across the
  *     hand-ported corpus: primary `(_p: T)`, secondary `def this() = this(null)`,
  *     `val f: T = if (_p != null) _p else <expr>`.
  *
  * Anything else is Unsupported: the engine refuses rather than approximates.
  */
final case class CtorPlan(
    primaryParams: List[CtorPlan.Param],
    primaryMods: Option[Mods],
    /** true when this class's no-arg construction path is equivalent to passing the
      * null sentinel to its primary (the sentinel merge itself, or a super() rewrite
      * against a sentinel-like parent). Subclass translation consults this via
      * the sentinel registry. */
    sentinelLike: Boolean,
    /** the primary Java ctor's comments — hoisted above the class line, since the
      * primary constructor has no declaration of its own in Scala. */
    primaryLeading: List[Trivia],
    superArgs: List[BExpr],
    primaryBody: List[BStmt],
    fieldLines: List[FieldLine],
    secondaryCtors: List[CtorPlan.Secondary],
)

object CtorPlan:
  /** promoted=true renders as `<vis>val name: T`; the vis comes from the promoted field. */
  final case class Param(p: BParam, promoted: Option[BField])
  final case class Secondary(
      leading: List[Trivia],
      mods: Mods,
      params: List[BParam],
      delegateArgs: List[BExpr],
      /** statements after the delegation (Scala auxiliaries may run code after this(...)). */
      body: List[BStmt] = Nil,
      /** the delegation target's param types — drives varargs/array adaptation. */
      targetTypes: List[BType] = Nil,
  )

  def of(
      t: BTypeDecl,
      unit: BUnit,
      sentinelSupers: Set[String] = Set.empty,
      registry: Option[CtorRegistry] = None,
  ): CtorPlan =
    def fail(what: String): Nothing = throw Unsupported(unit.sourcePath, t.name, what)

    def fieldWithOwnInit(f: BField): FieldLine =
      f.init match
        case Some(i) => FieldLine.FromField(f, i)
        case None    => FieldLine.DefaultInit(f) // incl. blank finals: definite-assignment fallback

    /** `this.f = <e>` assignments in a ctor body (with their leading trivia, so the
      * fusion into field declarations can't lose comments), in order; everything
      * else stays.
      */
    def splitAssigns(body: List[BStmt]): (List[(String, BExpr)], List[BStmt]) =
      val (a, r) = splitAssignsT(body)
      (a.map(x => (x._1, x._2)), r)

    def splitAssignsT(body: List[BStmt]): (List[(String, BExpr, List[Trivia])], List[BStmt]) =
      val assigns = List.newBuilder[(String, BExpr, List[Trivia])]
      val rest = List.newBuilder[BStmt]
      body.foreach { s =>
        s.k match
          case BStmtK.Assign(Ident(f, RefKind.OwnField), rhs, None) => assigns += ((f, rhs, s.leading))
          case BStmtK.Empty if s.leading.isEmpty                    => ()
          case _                                                    => rest += s
      }
      (assigns.result(), rest.result())

    /** shape 1 generalized: `root` is the single non-delegating ctor; `secondaries`
      * are this(...)-delegators already converted.
      */
    def rootPlan(c: BCtor, secondaries: List[Secondary]): CtorPlan =
        val (assignsT, rest) = splitAssignsT(c.body)
        val assigns = assignsT.map(x => (x._1, x._2))
        val counts = assigns.groupBy(_._1).view.mapValues(_.length).toMap
        counts.find(_._2 > 1).foreach { case (f, _) => fail(s"field $f assigned more than once in ctor") }
        val assignedOnce = assigns.toMap
        val assignTrivia: Map[String, List[Trivia]] = assignsT.map(x => x._1 -> x._3).toMap
        def withAssignTrivia(f: BField): BField =
          f.copy(leading = f.leading ++ assignTrivia.getOrElse(f.name, Nil))
        // fields assigned again in secondary-ctor bodies can be neither promoted nor val
        val secondaryAssigned: Set[String] = secondaries
          .flatMap(_.body)
          .collect { case BStmt(_, BStmtK.Assign(Ident(f, RefKind.OwnField), _, None)) => f }
          .toSet
        val promoted: Map[String, BField] = assignedOnce.collect {
          case (f, Ident(p, RefKind.Param(_)))
              if p == f && t.fields.exists(fd => fd.name == f && fd.mods.isFinal) &&
                !secondaryAssigned.contains(f) &&
                !t.methods.exists(_.name == f) => // field-vs-method clash stays a field (renamed later)
            f -> t.fields.find(_.name == f).get
        }
        // a class param may not share a name with a member it doesn't become — rename
        // such params `_p` and rewrite every reference (field-init exprs, super args,
        // remaining ctor body)
        // ...or with a method name (a used plain param materializes as private[this] val)
        val renamed: Set[String] = c.params
          .map(_.name)
          .filter(pn =>
            !promoted.contains(pn) &&
              (t.fields.exists(_.name == pn) || t.methods.exists(_.name == pn))
          )
          .toSet
        def rn(e: BExpr): BExpr =
          renamed.foldLeft(e)((acc, n) => renameParam(acc, n, "_" + n))
        val renameStmts: BExpr => BExpr = {
          case Ident(n, k @ RefKind.Param(_)) if renamed.contains(n) => Ident("_" + n, k)
          case e                                                     => e
        }
        val assignedR = assignedOnce.view.mapValues(rn).toMap
        val superArgsR = c.superArgs.getOrElse(Nil).map(rn)
        val restR = rest.map(BirTransform.mapStmt(_)(renameStmts))
        val params = c.params.map { p =>
          if renamed.contains(p.name) then Param(p.copy(name = "_" + p.name), None)
          else Param(p, promoted.get(p.name))
        }
        def unfinal(f: BField): BField =
          if secondaryAssigned.contains(f.name) then f.copy(mods = f.mods.copy(isFinal = false)) else f
        val reassignedInCtor = collection.mutable.Set[String]()
        val fieldLines = t.fields.flatMap { f0 =>
          val f = unfinal(f0)
          if promoted.contains(f.name) then None
          else
            (f.init, assignedR.get(f.name)) match
              case (Some(i), None) => Some(FieldLine.FromField(f, i))
              case (None, Some(e)) => Some(FieldLine.FromField(withAssignTrivia(f), e))
              case (Some(i), Some(_)) =>
                // Java: field init runs first, ctor assignment overwrites — keep the
                // declaration init and reinstate the assignment in the ctor body
                // (position among other body stmts approximated to front; gates verify)
                reassignedInCtor += f.name
                Some(FieldLine.FromField(f.copy(mods = f.mods.copy(isFinal = false)), i))
              case (None, None) => Some(fieldWithOwnInit(f))
        }
        val reinstated = assignsT
          .filter(x => reassignedInCtor.contains(x._1))
          .map(x => BStmt(x._3, BStmtK.Assign(Ident(x._1, RefKind.OwnField), rn(x._2), None)))
        // promoted fields become val class params, which can't carry block comments —
        // hoist their Javadoc above the class line so nothing is lost
        val promotedTrivia = t.fields.filter(f => promoted.contains(f.name)).flatMap(f => withAssignTrivia(f).leading)
        CtorPlan(params, Some(c.mods), sentinelLike = false, c.leading ++ promotedTrivia,
          superArgsR, reinstated ++ restR, fieldLines, secondaries)

    def identityBasisPlan(ctors: List[BCtor], roots: List[BCtor]): Option[CtorPlan] =
      identityBasisPlanImpl(ctors, roots, splitAssigns, rootPlan)

    t.ctors match
      case Nil =>
        CtorPlan(Nil, None, sentinelLike = false, Nil, Nil, Nil, t.fields.map(fieldWithOwnInit), Nil)

      case c :: Nil =>
        c.thisArgs.foreach(_ => fail("single constructor delegating to this(...)"))
        rootPlan(c, Nil)

      case ctors =>
        val (roots, delegators) = ctors.partition(_.thisArgs.isEmpty)
        val allEmptyBodies = ctors.forall { c =>
          val (a, r) = splitAssigns(c.body); a.isEmpty && r.isEmpty
        }
        // Date shape: every ctor is a root with IDENTICAL super args and no field
        // assignments; one no-arg ctor with an empty body exists → it becomes the
        // primary and the others delegate `this()` then run their statements.
        val sameSuper = ctors.map(_.superArgs.getOrElse(Nil)).distinct.lengthIs == 1
        val noFieldAssigns = ctors.forall(c => splitAssigns(c.body)._1.isEmpty)
        val noArgEmptyRoot =
          ctors.find(c => c.thisArgs.isEmpty && c.params.isEmpty && splitAssigns(c.body)._2.isEmpty)
        if roots.length == ctors.length && sameSuper && noFieldAssigns && noArgEmptyRoot.isDefined then
          val primary = noArgEmptyRoot.get
          val secondaries = ctors.filterNot(_ eq primary).map { c =>
            Secondary(c.leading, c.mods, c.params, Nil, splitAssigns(c.body)._2)
          }
          rootPlan(primary, secondaries)
        else if roots.length == 1 && delegators.nonEmpty then
          // this(...)-chain: the sole root becomes the primary; each delegator is an
          // auxiliary running its remaining statements after the delegation. Auxiliaries
          // may only call PRECEDING ctors in Scala, so order by delegation depth
          // (target found by arity — scalac re-verifies the resolution).
          def depth(c: BCtor, seen: Set[BCtor]): Int =
            c.thisArgs match
              case None => 0
              case Some(args) =>
                ctors.find(o => !seen.contains(o) && (o ne c) && o.params.length == args.length) match
                  case Some(target) => 1 + depth(target, seen + c)
                  case None         => 99
          val secondaries = delegators
            .sortBy(d => depth(d, Set.empty))
            .map(d =>
              fixSecondaryCollisions(
                t,
                Secondary(d.leading, d.mods, d.params, d.thisArgs.get, d.body,
                  targetTypes = roots.head.params.map(_.tpe)),
              )
            )
          rootPlan(roots.head, secondaries)
        else if allEmptyBodies then
          def isIdentitySuper(c: BCtor): Boolean = c.superArgs.exists { args =>
            args.length == c.params.length && args.zip(c.params).forall {
              case (Ident(n, RefKind.Param(_)), p) => n == p.name
              case _                               => false
            }
          }
          ctors.filter(isIdentitySuper) match
            case Nil =>
              noArgPrimaryPlan(t, unit, registry, fail).getOrElse(
                fail("multiple constructors, none with an identity super(...) call"))
            case candidates =>
              // several identity-super ctors: the max-arity one is primary (first in
              // source order on ties — `candidates` preserves source order)
              val primary = candidates.maxBy(_.params.length)
              // super() ≡ super(null) when the parent's no-arg path is the null-sentinel
              // (by construction of the sentinel merge) — so a no-arg secondary calling a
              // different super overload can still delegate as this(null).
              val parentSentinel = t.superClass.exists(s => sentinelSupers.contains(s.qname))
              def canDelegate(c: BCtor): Boolean =
                val dArgs = c.thisArgs.orElse(c.superArgs).getOrElse(Nil)
                dArgs.length == primary.params.length ||
                (dArgs.isEmpty && c.params.isEmpty && parentSentinel &&
                  primary.params.length == 1 && !primary.params.head.tpe.isInstanceOf[BType.Prim])
              val others = ctors.filterNot(_ eq primary)
              if !others.forall(canDelegate) then
                // different-super-overload family: the no-arg-primary + effect-replay
                // encoding (the hand-ported corpus's own answer to this shape)
                noArgPrimaryPlan(t, unit, registry, fail).getOrElse(
                  fail("secondary ctor cannot delegate to primary (arity mismatch)")
                )
              else
                var usedSentinelRewrite = false
                val secondaries = others.map { c =>
                  val delegateArgs =
                    c.thisArgs.orElse(c.superArgs).getOrElse(fail("secondary ctor without super/this args"))
                  if delegateArgs.length == primary.params.length then
                    Secondary(c.leading, c.mods, c.params, delegateArgs)
                  else
                    usedSentinelRewrite = true
                    Secondary(c.leading, c.mods, Nil, List(Lit(LitKind.NullL, "null")))
                }
                CtorPlan(
                  primary.params.map(p => Param(p, None)),
                  Some(primary.mods),
                  sentinelLike = usedSentinelRewrite,
                  primary.leading,
                  primary.superArgs.getOrElse(Nil),
                  Nil,
                  t.fields.map(fieldWithOwnInit),
                  secondaries,
                )
        else if identityBasisPlan(ctors, roots).isDefined then identityBasisPlan(ctors, roots).get
        else
          ctors.sortBy(_.params.length) match
            case List(noArg, paramful) if noArg.params.isEmpty && paramful.params.length == 1 =>
              val (naT, nr) = splitAssignsT(noArg.body)
              val (paT, pr) = splitAssignsT(paramful.body)
              val na = naT.map(x => (x._1, x._2))
              val pa = paT.map(x => (x._1, x._2))
              val mergeTrivia: Map[String, List[Trivia]] =
                (paT ++ naT).groupBy(_._1).view.mapValues(_.flatMap(_._3).toList).toMap
              def withMergeTrivia(f: BField): BField =
                f.copy(leading = f.leading ++ mergeTrivia.getOrElse(f.name, Nil))
              if nr.nonEmpty || pr.nonEmpty then
                return noArgPrimaryPlan(t, unit, registry, fail).getOrElse(
                  fail("two-ctor merge: ctor bodies contain more than field assignments"))
              (na, pa) match
                case (List((f1, defaultExpr)), List((f2, Ident(pn, RefKind.Param(_)))))
                    if f1 == f2 && paramful.params.head.name == pn &&
                      t.fields.exists(fd => fd.name == f1 && fd.mods.isFinal) =>
                  val p = paramful.params.head
                  if p.tpe.isInstanceOf[BType.Prim] then
                    return noArgPrimaryPlan(t, unit, registry, fail).getOrElse(
                      fail("two-ctor merge: sentinel requires a reference-typed parameter"))
                  val fld = t.fields.find(_.name == f1).get
                  CtorPlan(
                    List(Param(p.copy(name = "_" + p.name), None)),
                    Some(paramful.mods),
                    sentinelLike = true,
                    paramful.leading,
                    paramful.superArgs.getOrElse(Nil),
                    Nil,
                    FieldLine.SentinelVal(withMergeTrivia(fld), "_" + p.name, defaultExpr)
                      :: t.fields.filterNot(_.name == f1).map(fieldWithOwnInit),
                    List(Secondary(noArg.leading, noArg.mods, Nil, List(Lit(LitKind.NullL, "null")))),
                  )
                case (na, pa) =>
                  // generalized N-field sentinel merge: for every field the paramful ctor
                  // assigns, the no-null branch takes that expression and the null branch
                  // takes the no-arg ctor's assignment or the field's own initializer —
                  // exactly Java's two construction paths, merged.
                  val naMap = na.toMap
                  val paMap = pa.toMap
                  val noDupes = naMap.size == na.length && paMap.size == pa.length
                  val superOk = noArg.superArgs.getOrElse(Nil) == paramful.superArgs.getOrElse(Nil)
                  val p = paramful.params.head
                  if !noDupes || !superOk then
                    return noArgPrimaryPlan(t, unit, registry, fail).getOrElse(
                      fail("two-ctor merge: shapes don't match the sentinel pattern"))
                  if p.tpe.isInstanceOf[BType.Prim] then
                    return noArgPrimaryPlan(t, unit, registry, fail).getOrElse(
                      fail("two-ctor merge: sentinel requires a reference-typed parameter"))
                  val pn = "_" + p.name
                  val merged = t.fields.map { f =>
                    (paMap.get(f.name), naMap.get(f.name), f.init) match
                      case (Some(_), Some(_), Some(_)) =>
                        fail(s"two-ctor merge: field ${f.name} has an initializer and assignments in both ctors")
                      case (Some(pe), Some(ne), None) =>
                        FieldLine.CondInit(withMergeTrivia(f), pn, renameParam(pe, p.name, pn), ne)
                      case (Some(pe), None, Some(init)) =>
                        FieldLine.CondInit(withMergeTrivia(f), pn, renameParam(pe, p.name, pn), init)
                      case (Some(pe), None, None) =>
                        // no-arg path leaves the Java default value
                        FieldLine.CondInit(withMergeTrivia(f), pn, renameParam(pe, p.name, pn), javaDefault(f.tpe))
                      case (None, Some(_), _) =>
                        fail(s"two-ctor merge: field ${f.name} assigned only in the no-arg ctor")
                      case (None, None, _) => fieldWithOwnInit(f)
                  }
                  CtorPlan(
                    List(Param(p.copy(name = pn), None)),
                    Some(paramful.mods),
                    sentinelLike = true,
                    paramful.leading,
                    paramful.superArgs.getOrElse(Nil),
                    Nil,
                    merged,
                    List(Secondary(noArg.leading, noArg.mods, Nil, List(Lit(LitKind.NullL, "null")))),
                  )
            case _ =>
              noArgPrimaryPlan(t, unit, registry, fail).getOrElse(
                fail(s"${ctors.length} constructors with field logic — no funnel strategy applies"))

  /** Identity-basis funnel: a root ctor P whose super args are all distinct param refs
    * and whose field assigns are all param refs, with every param used exactly once,
    * is a complete basis — any sibling with the SAME super arity and SAME assigned
    * field set delegates by filling P's slots from its own exprs:
    *   P(in, path){ super(in); this.path = path }
    *   R(path){ super(CharStreams.fromPath(path)); this.path = path }
    *     → def this(path) = this(CharStreams.fromPath(path), path)
    */
  private def identityBasisPlanImpl(
      ctors: List[BCtor],
      roots: List[BCtor],
      splitAssigns: List[BStmt] => (List[(String, BExpr)], List[BStmt]),
      rootPlan: (BCtor, List[Secondary]) => CtorPlan,
  ): Option[CtorPlan] =
    def paramRef(e: BExpr): Option[String] = e match
      case Ident(n, RefKind.Param(_)) => Some(n)
      case _                          => None
    val basis = roots.find { p =>
      val (pa, pr) = splitAssigns(p.body)
      val superParams = p.superArgs.getOrElse(Nil).map(paramRef)
      val assignParams = pa.map((_, e) => paramRef(e))
      pr.isEmpty &&
      superParams.forall(_.isDefined) && assignParams.forall(_.isDefined) && {
        val used = superParams.flatten ++ assignParams.flatten
        used.sorted == p.params.map(_.name).sorted && used.distinct.length == used.length
      }
    }
    basis.flatMap { p =>
      val (pAssigns, _) = splitAssigns(p.body)
      val superParams = p.superArgs.getOrElse(Nil).flatMap(paramRef)
      val fieldOfParam: Map[String, String] = pAssigns.flatMap((f, e) => paramRef(e).map(_ -> f)).toMap
      val others = ctors.filterNot(_ eq p)
      val secondaries: Option[List[Secondary]] =
        others.foldRight(Option(List.empty[Secondary])) { (r, acc) =>
          acc.flatMap { tail =>
            val (rAssigns, rRest) = splitAssigns(r.body)
            val rSuper = r.superArgs.getOrElse(Nil)
            if r.thisArgs.isDefined || rSuper.length != superParams.length ||
              rAssigns.map(_._1).toSet != pAssigns.map(_._1).toSet ||
              rAssigns.map(_._1).distinct.length != rAssigns.length
            then None
            else
              val rAssignMap = rAssigns.toMap
              val delegateArgs = p.params.map { param =>
                superParams.indexOf(param.name) match
                  case -1 => rAssignMap(fieldOfParam(param.name))
                  case i  => rSuper(i)
              }
              // delegate args run BEFORE construction — they may not touch `this`
              if delegateArgs.exists(usesThis) then None
              else Some(Secondary(r.leading, r.mods, r.params, delegateArgs, rRest) :: tail)
          }
        }
      secondaries.map(rootPlan(p, _))
    }

  /** The no-arg-primary + effect-replay funnel (the hand-ported corpus's encoding
    * for Node-family hierarchies with different-super-overload ctors): synthesize a
    * bare primary; every Java ctor becomes `def this(params) = { this(); <transitive
    * inlined super effects>; <own body> }`. Requires the parent chain reachable via
    * empty no-arg construction and overload effects inlinable from the registry.
    */
  private def noArgPrimaryPlan(
      t: BTypeDecl,
      unit: BUnit,
      registry: Option[CtorRegistry],
      fail: String => Nothing,
  ): Option[CtorPlan] =
    registry.flatMap { reg =>
      val selfFqcn = reg.resolveFqcn(unit.pkg, t.name)
        .getOrElse(if unit.pkg.isEmpty then t.name else s"${unit.pkg}.${t.name}")
      def dbg(msg: => String): Unit =
        if sys.env.get("BP_DEBUG_CLASS").exists(selfFqcn.endsWith) then System.err.println(s"[noargpp] $selfFqcn: $msg")
      val noArgJava = t.ctors.find(_.params.isEmpty)
      def upstreamOf(c: BCtor): Option[List[BStmt]] = c.thisArgs match
        case Some(ta) => reg.inlineSuperEffects(selfFqcn, ta, forFqcn = selfFqcn)
        case None =>
          val sargs = c.superArgs.getOrElse(Nil)
          t.superClass match
            case Some(p) => reg.inlineSuperEffects(p.qname, sargs, forFqcn = selfFqcn)
            case None    => if sargs.isEmpty then Some(Nil) else None
      def flatBody(c: BCtor): Option[List[BStmt]] =
        upstreamOf(c).map(_ ++ c.body.filterNot(st => st.k == BStmtK.Empty && st.leading.isEmpty))
      // a body-ful (or this-delegating) no-arg ctor flattens into the synthetic
      // primary's body — its effects run on every construction path, and replayed
      // secondaries overwrite them, the funnel's standing replay semantics
      val primaryBody: Option[List[BStmt]] = noArgJava match
        case None    => Some(Nil)
        case Some(c) => flatBody(c)
      dbg(s"noArg=${noArgJava.isDefined} thisArgs=${noArgJava.flatMap(_.thisArgs).map(_.length)} primaryBody=${primaryBody.map(_.length)}")
      if primaryBody.isEmpty then None
      else
        val toReplay = t.ctors.filter(_.params.nonEmpty)
        val secondaries: Option[List[Secondary]] =
          toReplay.foldRight(Option(List.empty[Secondary])) { (c, acc) =>
            acc.flatMap { tail =>
              val fb = flatBody(c)
              dbg(s"replay ctor/${c.params.length}: flatBody=${fb.map(_.length)}")
              fb.map { body =>
                fixSecondaryCollisions(t, Secondary(c.leading, c.mods, c.params, Nil, body)) :: tail
              }
            }
          }
        secondaries.map { secs =>
          val assigned = collection.mutable.Set[String]()
          def collectAssigns(st: BStmt): Unit =
            st.k match
              case BStmtK.Assign(Ident(f, RefKind.OwnField), _, _) => assigned += f
              case BStmtK.If(_, a, b)      => a.foreach(collectAssigns); b.foreach(_.foreach(collectAssigns))
              case BStmtK.While(_, b)      => b.foreach(collectAssigns)
              case BStmtK.DoWhile(b, _)    => b.foreach(collectAssigns)
              case BStmtK.Block(b)         => b.foreach(collectAssigns)
              case BStmtK.Boundary(b, _)   => b.foreach(collectAssigns)
              case BStmtK.Try(b, cs, f2)   =>
                b.foreach(collectAssigns); cs.foreach(_.body.foreach(collectAssigns)); f2.foreach(_.foreach(collectAssigns))
              case BStmtK.Match(_, cases)  => cases.foreach(_.body.foreach(collectAssigns))
              case _ => ()
          secs.foreach(_.body.foreach(collectAssigns))
          primaryBody.getOrElse(Nil).foreach(collectAssigns)
          val fieldLines = t.fields.map { f0 =>
            val f = if assigned.contains(f0.name) then f0.copy(mods = f0.mods.copy(isFinal = false)) else f0
            f.init match
              case Some(i) => FieldLine.FromField(f, i)
              case None    => FieldLine.DefaultInit(f)
          }
          CtorPlan(
            Nil,
            noArgJava.map(_.mods),
            sentinelLike = false,
            noArgJava.map(_.leading).getOrElse(Nil),
            Nil,
            primaryBody.getOrElse(Nil),
            fieldLines,
            secs,
          )
        }
    }

  /** Secondary-ctor params sharing a name with a class member would make
    * `member = param` a self-assignment — rename them `_p` throughout. */
  private def fixSecondaryCollisions(t: BTypeDecl, s: Secondary): Secondary =
    val collide = s.params
      .map(_.name)
      .filter(pn => t.fields.exists(_.name == pn) || t.methods.exists(_.name == pn))
      .toSet
    if collide.isEmpty then s
    else
      val rnE: BExpr => BExpr = {
        case Ident(n, k @ RefKind.Param(_)) if collide.contains(n) => Ident("_" + n, k)
        case e                                                     => e
      }
      s.copy(
        params = s.params.map(p => if collide.contains(p.name) then p.copy(name = "_" + p.name) else p),
        delegateArgs = s.delegateArgs.map(BirTransform.mapExpr(_)(rnE)),
        body = s.body.map(BirTransform.mapStmt(_)(rnE)),
      )

  /** true when the expression touches the instance under construction. */
  private def usesThis(e: BExpr): Boolean = e match
    case This                       => true
    case Ident(_, RefKind.OwnField) => true
    case Select(r, _)               => usesThis(r)
    case ArrayLength(a)             => usesThis(a)
    case ArrayAccess(a, i)          => usesThis(a) || usesThis(i)
    case Call(recv, _, args, _, _) =>
      val recvThis = recv match
        case Recv.OnThis | Recv.OnSuper => true
        case Recv.On(r)                 => usesThis(r)
        case Recv.Static(_)             => false
      recvThis || args.exists(usesThis)
    case New(_, args, _, _)     => args.exists(usesThis)
    case NewArray(_, d, i)      => d.exists(usesThis) || i.exists(_.exists(usesThis))
    case AssignExpr(l, r)       => usesThis(l) || usesThis(r)
    case IncDecExpr(t2, _, _)   => usesThis(t2)
    case Binary(_, l, r, _)     => usesThis(l) || usesThis(r)
    case Unary(_, x, _)         => usesThis(x)
    case Ternary(c, t, e2)      => usesThis(c) || usesThis(t) || usesThis(e2)
    case Cast(_, x)             => usesThis(x)
    case InstanceOf(x, _)       => usesThis(x)
    case Typed(x, _)            => usesThis(x)
    case Lambda(_, body)        => body.fold(_ => true, usesThis) // conservative for stmt bodies
    case MethodRef(p2, _)       => p2.fold(_ => false, usesThis)
    case _: UnboundMethodRef | _: Ident | _: Lit | _: ClassLit | _: CtorRef => false

  /** Java default value for a type, as an expression. */
  private def javaDefault(t: BType): BExpr = t match
    case BType.Prim("boolean") => Lit(LitKind.BoolL, "false")
    case BType.Prim("long")    => Lit(LitKind.LongL, "0L")
    case BType.Prim("float")   => Lit(LitKind.FloatL, "0.0f")
    case BType.Prim("double")  => Lit(LitKind.DoubleL, "0.0d")
    case BType.Prim("char")    => Lit(LitKind.CharL, "'\\u0000'")
    case BType.Prim(_)         => Lit(LitKind.IntL, "0")
    case _                     => Lit(LitKind.NullL, "null")

  /** Renames a parameter reference throughout an expression (best-effort structural
    * map; lambda bodies that shadow the name are left untouched, scalac verifies). */
  private def renameParam(e: BExpr, from: String, to: String): BExpr =
    def rp(x: BExpr): BExpr = renameParam(x, from, to)
    e match
      case Ident(n, k @ RefKind.Param(_)) if n == from => Ident(to, k)
      case _: Ident | _: Lit | This | _: ClassLit      => e
      case Select(r, n)                                => Select(rp(r), n)
      case ArrayLength(a)                              => ArrayLength(rp(a))
      case ArrayAccess(a, i)                           => ArrayAccess(rp(a), rp(i))
      case Call(recv, n, args, f, o) =>
        val r2 = recv match
          case Recv.On(r) => Recv.On(rp(r))
          case other      => other
        Call(r2, n, args.map(rp), f, o)
      case n: New                  => n.copy(args = n.args.map(rp))
      case NewArray(el, d, i)      => NewArray(el, d.map(rp), i.map(_.map(rp)))
      case Binary(op, l, r, c)     => Binary(op, rp(l), rp(r), c)
      case Unary(op, x, p2)        => Unary(op, rp(x), p2)
      case Ternary(c, tt, ee)      => Ternary(rp(c), rp(tt), rp(ee))
      case Cast(t2, x)             => Cast(t2, rp(x))
      case InstanceOf(x, t2)       => InstanceOf(rp(x), t2)
      case Typed(x, t2)            => Typed(rp(x), t2)
      case l: Lambda               => if l.params.contains(from) then l else l.copy(body = l.body.map(rp))
      case m: MethodRef            => m.copy(prefix = m.prefix.map(rp))
      case u: UnboundMethodRef     => u
