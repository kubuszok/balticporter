package balticporter.transform

import balticporter.core.{MergeablePolicy, PolicyFinding, PolicyReport, PolicySource, SurfacePolicy}
import balticporter.tir.*

/** Ships a listed member PUBLIC where java declared it narrower — the hand port's own widening
  * (`FileHandle(File, FileType)` is protected in java, public in the reference). A SIGNATURE fact
  * on the symbol, read by the emitter's visibility plan and every dependent (DESIGN.md §8.30).
  * @param widen member keys (`C#m`, `C#<init>(desc)`) @param derive the reference's `Public` rows. */
final class VisibilityTransform(val widen: Set[String] = Set.empty, val derive: Boolean = false)
    extends Phase, PolicySource, SurfacePolicy, MergeablePolicy, PolicyBound, Rewrite:

  def name: String = "visibility"
  def accountedBy: Set[String] = Set(balticporter.runner.PortRun.Policy)

  def surfaceFingerprint: String =
    val ws = if widen.isEmpty then "" else s"widen=${widen.toList.sorted.mkString(",")}"
    val dr = if derive then "derive=reference" else ""
    List(ws, dr).filter(_.nonEmpty).mkString(";")

  def subjects: Set[String] = widen.map(MergeablePolicy.subjectOf)

  def mergedWith(later: Phase): Either[String, MergeablePolicy.Merged] = later match
    case o: VisibilityTransform =>
      Right(MergeablePolicy.Merged(new VisibilityTransform(widen ++ o.widen, derive || o.derive),
        (o.widen -- widen).map(MergeablePolicy.subjectOf)))
    case _ => Left(s"expected VisibilityTransform, got ${later.getClass.getSimpleName}")

  private var bound: Set[SymId]      = Set.empty
  private var derivedIds: Set[SymId] = Set.empty
  private var records: List[PolicyBinder.Record] = Nil
  private var runScope: RunScope = RunScope.whole

  def bindPolicy(binder: PolicyBinder): Unit =
    runScope = binder.run
    bound = widen.toList.sorted.flatMap(k =>
      binder.bindMembers(name, "VisibilityTransform(widen)", k).toOption.getOrElse(Nil).flatMap(_.sym)).toSet
    if derive then derivedIds = binder.run.derived.publicIds
    records = binder.recordsFor(name)

  def policyReport: PolicyReport = PolicyReport.fromBindings(records)

  override def run(program: Program): Program =
    val targets = (bound ++ derivedIds).filter(id => program.owned(id) && runScope.emitsSymbol(program, id))
    if targets.isEmpty then return program
    val table = targets.toList.sortBy(_.raw).foldLeft(program.symbols) { (t, id) =>
      t.get(id).filter(s => s.flags.isPrivate || s.flags.isProtected || s.flags.isPackagePrivate).fold(t) { s =>
        record(Decision(
          kind = Decision.Kind.WidenedVisibility, subject = id, subjectFqn = s.fullName,
          detail = Map(
            "from" -> (if s.flags.isPrivate then "private" else if s.flags.isProtected then "protected" else "package-private"),
            "to"   -> "public",
            "why"  -> "the reference port ships this member public; a hand-written caller outside the package constructs or calls it",
          ),
          reason = Reason.Configured(name, s.fullName),
          origin = Decision.originOf(program, id)))
        t.updated(s.copy(flags = s.flags.copy(isPrivate = false, isPackagePrivate = false, isProtected = false)))
      }
    }
    program.rebuilt(symbols = table)
