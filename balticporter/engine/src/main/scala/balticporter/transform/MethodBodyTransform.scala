package balticporter.transform

import balticporter.core.{MergeablePolicy, PolicyFinding, PolicyIssue, PolicyReport, PolicySource, SurfacePolicy}
import balticporter.tir.*

/** Replaces a named method's body with ready-made Scala, keeping the rest of the class
  * mechanically translated — the seam `dropTypes`/`inject`/`dropMethods` cannot express. Runs as a
  * phase so the replacement lands in the TIR before checks read it. Refuses constructors
  * (`CtorFunnel`'s job). CLAUDE.md §1(b): empty `bodies` = no-op. `bodies` keys `owner#name[(P1,P2)]`
  * → Scala source spliced verbatim at term position, not type-checked by the engine. */
final class MethodBodyTransform(val bodies: Map[String, String] = Map.empty)
    extends Phase, PolicySource, SurfacePolicy, MergeablePolicy, PolicyBound:
  def name: String = "method-body-substitution"

  /** What the run resolved each declared key to. `bySym` orders bare keys before precise ones so
    * a precise `X#m(int)` wins over a bare `X#m` at the same member deterministically. */
  private var bound: Map[String, Binding[List[PolicyBinder.Hit]]] = Map.empty
  private var bySym: Map[SymId, String] = Map.empty
  private var records: List[PolicyBinder.Record] = Nil

  def bindPolicy(binder: PolicyBinder): Unit =
    bound = bodies.keys.toList.sorted
      .map(k => k -> binder.bindMembers(name, "MethodBodyTransform", k)).toMap
    records = binder.recordsFor(name)
    val (bare, precise) = bound.toList.sortBy(_._1)
      .partition((k, _) => MemberKey.parse(k).toOption.exists(_.isBare))
    bySym = (bare ++ precise).flatMap((k, b) => b.toOption.getOrElse(Nil).flatMap(_.sym).map(_ -> k)).toMap

  /** A body is not a signature, so two modules replacing different bodies don't disagree about
    * the shared surface — but keys are fingerprinted anyway, including body text, since a base and
    * a dependent supplying different Scala for one member is a policy mistake worth surfacing. */
  def surfaceFingerprint: String =
    bodies.toList.sorted.map((k, v) => s"$k=${v.hashCode.toHexString}").mkString(",")

  /** Independent keys union; same key with a different body refuses (a conflict only a human can
    * resolve). Same key, same body is accepted silently — the same decision stated twice, which
    * `base.extendedBy(…)` can do legitimately. Lets a dependent's own instance merge with the
    * base's via `surfaceFold` instead of a fatal `SurfaceDivergence`. */
  def mergedWith(later: Phase): Either[String, MergeablePolicy.Merged] = later match
    case o: MethodBodyTransform =>
      val conflicts = for
        (k, v) <- o.bodies.toList.sorted
        v2     <- bodies.get(k)
        if v != v2
      yield s"$k: bodies differ"
      if conflicts.nonEmpty then Left(conflicts.mkString("; "))
      else
        val added = o.bodies.keySet -- bodies.keySet
        Right(MergeablePolicy.Merged(
          new MethodBodyTransform(bodies ++ o.bodies),
          added.map(MergeablePolicy.subjectOf)))
    case _ => Left(s"expected MethodBodyTransform, got ${later.getClass.getSimpleName}")

  def subjects: Set[String] = bodies.keySet.map(MergeablePolicy.subjectOf)

  private var applied: List[String] = Nil

  /** Declared keys that matched nothing, plus the two shapes this phase refuses — both known from
    * the binding, complete before [[run]]. */
  def policyReport: PolicyReport =
    PolicyReport.fromBindings(records) ++ PolicyReport(
      bound.toList.sortBy(_._1).flatMap { (k, b) =>
        b match
          case Binding.Bound(_, hits, _) =>
            // a refused key is not an unmatched key — report them separately, not both.
            val ctors = hits.count(_.key.name == "<init>")
            val refuse = Option.when(ctors > 0)(
              PolicyFinding(name, "MethodBodyTransform", k, PolicyIssue.Malformed,
                "a CONSTRUCTOR body cannot be substituted: CtorFunnel derives the class's Scala " +
                  "primary and its replayable `super(args)` from constructor bodies, and swapping " +
                  "one underneath that analysis changes it silently — drop the type and inject a " +
                  "replacement instead"))
            val many = Option.when(hits.size - ctors > 1)(
              PolicyFinding(name, "MethodBodyTransform", k, PolicyIssue.Unverifiable,
                s"matched ${hits.size - ctors} members: the bare `owner#name` form gives EVERY " +
                  "overload the same body. Use the precise `owner#name(P1,P2)` form unless that is " +
                  "genuinely intended"))
            refuse.toList ++ many.toList
          case _ => Nil
      })

  /** Member keys whose body was actually replaced, in a stable order. CLAUDE.md §3 */
  def substituted: List[String] = applied.sorted

  override def run(program: Program): Program =
    if bodies.isEmpty then
      applied = Nil
      return program

    val done = collection.mutable.ListBuffer.empty[String]

    def rewriteDef(d: Tree.DefDef, owner: String): Tree.DefDef =
      bySym.get(d.symbol) match
        case None => d
        case Some(k) =>
          val nm = program.symbolOf(d.symbol).map(_.name).getOrElse("")
          // refusal is reported by policyReport from the binding; here it only declines to rewrite.
          if nm == "<init>" then d
          else
            done += k
            // one decision row per replaced member; signature omitted from detail since it is
            // unchanged — this row is the only place that says the behaviour isn't upstream's.
            record(Decision(
              kind       = Decision.Kind.SubstitutedBody,
              subject    = d.symbol,
              subjectFqn = MemberKey(owner, nm).render,
              detail     = Map(
                "key"  -> k,
                "from" -> "the mechanically translated java body",
                "to"   -> "hand-written Scala from MethodBodyTransform(bodies)",
                "why"  -> ("the signature is UNCHANGED and every call site still type-checks; " +
                  "only the behaviour behind it is this port's rather than upstream's"),
              ),
              reason = Reason.Configured(name, k),
              origin = d.origin,
            ))
            d.copy(rhs = Some(Tree.Opaque(bodies(k), d.returnTpt.tpe, d.origin)))

    def rewrite(cd: Tree.ClassDef): Tree.ClassDef =
      val owner = program.symbolOf(cd.symbol).map(_.fullName).getOrElse("")
      val body = cd.body.map {
        case d: Tree.DefDef   => rewriteDef(d, owner)
        case c: Tree.ClassDef => rewrite(c)
        case other            => other
      }
      // walk enum constant bodies too — a constant's body is a separate field. ENGINE-LIMITS T23
      val cases = cd.enumCases.map { ec =>
        ec.copy(body = ec.body.map {
          case d: Tree.DefDef => rewriteDef(d, owner)
          case other          => other
        })
      }
      cd.copy(body = body, enumCases = cases)

    val units = program.units.map(rewrite)
    applied = done.toList
    program.rebuilt(units) // xref rebuilt by the Pipeline
