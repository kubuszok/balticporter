package balticporter.tir

import balticporter.catalog.FixKind

/** Counts every declaration whose vararg parameter has a non-reifiable component type
  * (java's heap pollution, catalog `JS-G41`). Reports `Acknowledged` (java had `@SafeVarargs`)
  * and `Unacknowledged` (javac warned, author did not annotate). Nothing to translate --
  * the port reproduces java's unsoundness exactly; this makes it a number.
  * // JLS 9.6.4.7, JLS 4.7, JLS 4.12.2 */
object HeapPollutionCheck extends RemedySource:

  val Name = "heap-pollution"

  /** java's own annotation FQN. */
  private val SafeVarargs = "java.lang.SafeVarargs"
  private val ScalaArray  = "scala.Array"

  enum Issue:
    /** Java wrote `@SafeVarargs`; the annotation is inert in scala. */
    case Acknowledged
    /** Javac warned and the author did not annotate; nothing warns in scala. */
    case Unacknowledged

  object Issue:
    def classification(i: Issue): String = i match
      case Acknowledged =>
        "§1(a) ENGINE, and DELIBERATELY NOT FIXED: the port reproduces java's heap pollution " +
          "exactly, so there is nothing to translate — what has no Scala image is the " +
          "ACKNOWLEDGEMENT. `@SafeVarargs` is emitted verbatim onto a method whose vararg is now a " +
          "plain `Array` parameter (JS-G37), where scalac neither checks its placement nor derives " +
          "anything from it. Read this row as `the author of the java said they had checked this " +
          "body`, and read the port as carrying that promise unverified."
      case Unacknowledged =>
        "§1(a) ENGINE, and DELIBERATELY NOT FIXED for the same reason: javac warns at a " +
          "declaration whose vararg component is not reifiable (JLS 4.7) and scalac has no such " +
          "warning, so this declaration crossed with neither an annotation nor a diagnostic. " +
          "Nothing in the emitted file mentions it and no other count can see it."

  // -------------------------------------------------------------------------------------------
  // THE MENU (DESIGN.md §8.16)
  // -------------------------------------------------------------------------------------------

  /** Acknowledge that the vararg use at this member is safe. NOT emission-affecting.
    * Answers `Unacknowledged` only; `Acknowledged` rows are java's own assertion, kept apart. */
  val Acknowledge: Remedy = Remedy(
    id = "acknowledge", lane = Name, kind = Issue.Unacknowledged.toString,
    emissionAffecting = false, fix = FixKind.Universal,
    what = "the operator states this vararg use is safe — java's `@SafeVarargs` conversation, " +
      "carried out where java could not always hold it")

  /** Not on the menu: emitting a scala-side marker (inert), proving the body safe
    * (an analysis, not a port choice), or narrowing the signature (different API). */
  def remedies: List[Remedy] = List(Acknowledge)

  /** @param param     the vararg parameter's name
    * @param component the emitted COMPONENT type — the thing that is not reifiable */
  final case class Finding(issue: Issue, owner: String, param: String, component: String, origin: Origin):
    def detail: String =
      s"vararg `$param` has component `$component`, which JLS 4.7 does not make reifiable: java " +
        (if issue == Issue.Acknowledged then "warned here and the author wrote `@SafeVarargs`"
         else "WARNED here and the author did not annotate it") +
        ", and scala has neither the warning nor the annotation — the unsoundness crosses unmarked"
    def render: String = s"$issue $owner: $param: $component  (${origin.javaPath}:${origin.line})"
    def report: CheckReport.Finding =
      CheckReport.Finding(Name, issue.toString, owner, CheckReport.relativise(origin.javaPath),
        origin.line, detail)

  /** Shared predicate for this check and the emitter's JS-G41 consult.
    * Returns `None` for declarations with no unchecked vararg. // ENGINE-LIMITS F8 */
  def uncheckedVararg(d: Tree.DefDef)(using p: Program): Option[Finding] =
    val owner = p.symbolOf(d.symbol).map(_.fullName).getOrElse("?")
    val ack   = p.symbolOf(d.symbol).exists(_.annotations.exists(a => headFqn(a.tpe).contains(SafeVarargs)))
    d.paramss.flatten.collectFirst {
      case v if p.symbolOf(v.symbol).exists(_.flags.isVararg) && !reifiable(componentOf(v.tpt.tpe)) =>
        Finding(if ack then Issue.Acknowledged else Issue.Unacknowledged, owner,
          p.symbolOf(v.symbol).map(_.name).getOrElse("?"),
          render(componentOf(v.tpt.tpe)), d.origin)
    }

  /** The array element type, or the type itself if no longer `scala.Array`. */
  private def componentOf(t: TypeRepr)(using Program): TypeRepr = t match
    case TypeRepr.AppliedType(tc, List(a)) if headFqn(tc).contains(ScalaArray) => a
    case other                                                                    => other

  /** JLS 4.7 reifiability test. Conservative: unclassifiable shapes are left reifiable. */
  private def reifiable(t: TypeRepr)(using Program): Boolean = t match
    case TypeRepr.TypeRef(_, s) => !isTypeVariable(s)
    // an array is reifiable iff its component is (JLS 4.7)
    case TypeRepr.AppliedType(tc, List(a)) if headFqn(tc).contains(ScalaArray) => reifiable(a)
    case TypeRepr.AppliedType(tc, as)                                         => reifiable(tc) && as.forall(unboundedWildcard)
    case TypeRepr.AndType(_, _)                                               => false
    case TypeRepr.OrType(_, _)                                                => false
    case _                                                                    => true

  /** A type parameter, structurally: has `TypeBounds` as its `info` and `isParam`. */
  private def isTypeVariable(s: SymId)(using p: Program): Boolean =
    p.symbolOf(s).exists(x => x.flags.isParam && x.info.isInstanceOf[TypeRepr.TypeBounds])

  /** JLS 4.7: `List<?>` is reifiable, `List<? extends X>` is not. */
  private def unboundedWildcard(t: TypeRepr)(using Program): Boolean = t match
    case TypeRepr.TypeBounds(_, hi) =>
      hi == TypeRepr.NoType ||
        headFqn(hi).exists(f => f == "java.lang.Object" || f == "scala.Any" || f == "scala.AnyRef")
    case _ => false

  private def headFqn(t: TypeRepr)(using p: Program): Option[String] = t match
    case TypeRepr.TypeRef(_, s)      => p.symbolOf(s).map(_.fullName)
    case TypeRepr.AppliedType(tc, _) => headFqn(tc)
    case _                           => scala.None

  private def render(t: TypeRepr)(using p: Program): String = t match
    case TypeRepr.AppliedType(tc, as) => s"${render(tc)}[${as.map(render).mkString(", ")}]"
    case TypeRepr.TypeBounds(_, hi)   => if hi == TypeRepr.NoType then "?" else s"? <: ${render(hi)}"
    case other                        => headFqn(other).getOrElse(other.toString)

  /** Check over the units the run emits (D2). `resolutions` drains rows a remedy answered. */
  def check(program: Program, units: List[Tree.ClassDef],
            resolutions: ResolutionPlan = ResolutionPlan.empty): List[Finding] =
    given Program = program
    val out = collection.mutable.ListBuffer.empty[Finding]
    val scan = new Phase:
      def name: String = "heap-pollution/scan"
      override def transformDefDef(d: Tree.DefDef)(using Program): Tree.DefDef =
        uncheckedVararg(d)
          .filterNot(f => resolutions.appliedAt(d.symbol, Name, f.issue.toString, f.origin))
          .foreach(out += _)
        d
    units.foreach(u => StandardTraversal.mapClassDef(scan, u))
    out.toList.sortBy(f => (f.issue.toString, f.origin.javaPath, f.origin.line, f.param))

  /** Pipeline phase that records remedy decisions before emission.
    * No-op with an empty plan. Scoped to this run's own declarations (D2). */
  final class Apply extends Phase, PolicyBound:
    def name: String = "heap-pollution/remedy"

    private var plan: ResolutionPlan = ResolutionPlan.empty
    private var scope: RunScope      = RunScope.whole

    def bindPolicy(binder: PolicyBinder): Unit =
      plan  = binder.resolutions
      scope = binder.run

    /** No-op when empty: returns program untouched without rebuilding units. */
    override def run(program: Program): Program =
      if plan.isEmpty then program else super.run(program)

    override def transformDefDef(d: Tree.DefDef)(using p: Program): Tree.DefDef =
      if plan.isEmpty || !scope.emitsSymbol(p, d.symbol) then d
      else
        uncheckedVararg(d).foreach { f =>
          plan.selected(d.symbol, Name, f.issue.toString).foreach { r =>
            plan.applied(r, f.owner, d.symbol, d.origin,
              s"vararg `${f.param}` (component `${f.component}`) is stated safe by this port: " +
                "java warned here and scala neither warns nor carries an annotation that means " +
                "anything — the assertion had no other home")
          }
        }
        d

  def summary(fs: List[Finding]): String =
    if fs.isEmpty then "  none"
    else
      fs.groupBy(_.issue).toList.sortBy((_, v) => -v.size).map { (issue, vs) =>
        val head  = s"  ${vs.size} × $issue\n  ${Issue.classification(issue)}"
        val sites = vs.sortBy(f => (f.origin.javaPath, f.origin.line)).take(10).map("    " + _.render)
        (head :: sites).mkString("\n")
      }.mkString("\n")
