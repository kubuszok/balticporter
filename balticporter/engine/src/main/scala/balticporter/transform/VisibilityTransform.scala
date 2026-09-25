package balticporter.transform

import balticporter.core.{ MergeablePolicy, PolicyFinding, PolicyIssue, PolicyReport, PolicySource, SurfacePolicy }
import balticporter.tir.*

/** Ships a listed member PUBLIC where java declared it narrower (`widen`), or a listed java `protected` member as scala's plain, subclass-only `protected` (`narrow`) — both the hand port's own
  * spelling. A SIGNATURE fact on the symbol, read by the emitter's visibility plan and every dependent. A narrowing moves the whole override component, and is refused and counted where any reference
  * could not survive losing java's package access.
  * @param widen
  *   member keys (`C#m`, `C#<init>(desc)`) @param derive the reference's `Public` rows @param narrow member keys to ship plain `protected` @param deriveNarrow the reference's `Protected` rows.
  */
final class VisibilityTransform(val widen: Set[String] = Set.empty, val derive: Boolean = false, val narrow: Set[String] = Set.empty, val deriveNarrow: Boolean = false)
    extends Phase,
      PolicySource,
      SurfacePolicy,
      MergeablePolicy,
      PolicyBound,
      Rewrite:

  /** the constructor published before `narrow` existed, kept for binary compatibility. */
  def this(widen: Set[String], derive: Boolean) = this(widen, derive, Set.empty, false)

  def name:        String      = "visibility"
  def accountedBy: Set[String] = Set(balticporter.runner.PortRun.Policy)

  def surfaceFingerprint: String =
    val ws = if widen.isEmpty then "" else s"widen=${widen.toList.sorted.mkString(",")}"
    val dr = if derive then "derive=reference" else ""
    val ns = if narrow.isEmpty then "" else s"narrow=${narrow.toList.sorted.mkString(",")}"
    val dn = if deriveNarrow then "narrow-derive=reference" else ""
    List(ws, dr, ns, dn).filter(_.nonEmpty).mkString(";")

  def subjects: Set[String] = (widen ++ narrow).map(MergeablePolicy.subjectOf)

  def mergedWith(later: Phase): Either[String, MergeablePolicy.Merged] = later match
    case o: VisibilityTransform =>
      Right(
        MergeablePolicy.Merged(
          new VisibilityTransform(widen ++ o.widen, derive || o.derive, narrow ++ o.narrow, deriveNarrow || o.deriveNarrow),
          ((o.widen -- widen) ++ (o.narrow -- narrow)).map(MergeablePolicy.subjectOf)
        )
      )
    case _ => Left(s"expected VisibilityTransform, got ${later.getClass.getSimpleName}")

  private var bound:         Set[SymId]                = Set.empty
  private var derivedIds:    Set[SymId]                = Set.empty
  private var narrowKeys:    List[(String, SymId)]     = Nil
  private var records:       List[PolicyBinder.Record] = Nil
  private var runScope:      RunScope                  = RunScope.whole
  private var runFindings:   List[PolicyFinding]       = Nil
  private var derivedNarrow: Set[SymId]                = Set.empty

  def bindPolicy(binder: PolicyBinder): Unit =
    runScope = binder.run
    bound = widen.toList.sorted.flatMap(k => binder.bindMembers(name, "VisibilityTransform(widen)", k).toOption.getOrElse(Nil).flatMap(_.sym)).toSet
    if derive then derivedIds = binder.run.derived.publicIds
    narrowKeys = narrow.toList.sorted.flatMap(k => binder.bindMembers(name, VisibilityTransform.NarrowSetting, k).toOption.getOrElse(Nil).flatMap(_.sym).map(k -> _))
    if deriveNarrow then derivedNarrow = binder.run.derived.protectedIds
    records = binder.recordsFor(name)

  /** Declared keys that named nothing, and every narrowing this run refused. */
  def policyReport: PolicyReport = PolicyReport.fromBindings(records) ++ PolicyReport(runFindings)

  override def run(program: Program): Program =
    runFindings = Nil
    val widened = widenAll(program)
    narrowAll(widened)

  private def widenAll(program: Program): Program =
    val targets = (bound ++ derivedIds).filter(id => program.owned(id) && runScope.emitsSymbol(program, id))
    if targets.isEmpty then return program
    val table = targets.toList.sortBy(_.raw).foldLeft(program.symbols) { (t, id) =>
      t.get(id).filter(s => s.flags.isPrivate || s.flags.isProtected || s.flags.isPackagePrivate).fold(t) { s =>
        record(
          Decision(
            kind = Decision.Kind.WidenedVisibility,
            subject = id,
            subjectFqn = s.fullName,
            detail = Map(
              "from" -> (if s.flags.isPrivate then "private" else if s.flags.isProtected then "protected" else "package-private"),
              "to" -> "public",
              "why" -> "the reference port ships this member public; a hand-written caller outside the package constructs or calls it"
            ),
            reason = Reason.Configured(name, s.fullName),
            origin = Decision.originOf(program, id)
          )
        )
        t.updated(s.copy(flags = s.flags.copy(isPrivate = false, isPackagePrivate = false, isProtected = false)))
      }
    }
    program.rebuilt(symbols = table)

  // ---- narrowing -----------------------------------------------------------------------------

  private def narrowAll(program: Program): Program =
    val derivedKeys = derivedNarrow.toList.filterNot(id => narrowKeys.exists(_._2 == id)).flatMap(id => program.symbolOf(id).map(s => s.fullName -> id))
    // a key naming a member this run does not emit is a base's: the base's own run narrowed it
    val requests = (narrowKeys ++ derivedKeys).filter((_, id) => program.owned(id) && runScope.emitsSymbol(program, id))
    if requests.isEmpty then return program
    val baseUnits = program.units.map(_.symbol).filterNot(runScope.emits).toSet
    val graph     = OverrideGraph.build(program, baseUnits = baseUnits)
    val tagged    = collection.mutable.LinkedHashMap.empty[SymId, String]
    requests.sortBy((k, id) => (k, id.raw)).foreach { (key, id) =>
      refusal(program, graph, id) match
        case Some(why) =>
          runFindings :+= PolicyFinding(
            name,
            VisibilityTransform.NarrowSetting,
            key,
            PolicyIssue.Unverifiable,
            s"$why — so this member keeps java's package access (`protected[pkg]`)",
            PolicyFinding.About.ThisRun
          )
        case scala.None =>
          graph
            .closureOf(id)
            .members
            .filter(m => program.symbolOf(m).exists(s => s.flags.isProtected && !s.flags.isStatic) && runScope.emitsSymbol(program, m))
            .foreach(m => if !tagged.contains(m) then tagged(m) = key)
    }
    if tagged.isEmpty then return program
    val table = tagged.toList.sortBy(_._1.raw).foldLeft(program.symbols) { case (t, (id, key)) =>
      t.get(id).fold(t) { s =>
        record(
          Decision(
            kind = Decision.Kind.NarrowedVisibility,
            subject = id,
            subjectFqn = s.fullName,
            detail = Map(
              "from" -> "protected[pkg]",
              "to" -> "protected",
              "why" -> "the port ships this override component subclass-only, as the reference does; no reference from a non-subclass in the package exists"
            ),
            reason = Reason.Configured(name, key),
            origin = Decision.originOf(program, id)
          )
        )
        t.updated(s.copy(tags = s.tags + VisibilityTransform.SubclassOnly))
      }
    }
    program.rebuilt(symbols = table)

  /** why the component of `id` may NOT lose java's package access, or `None` when every reference to every member of it is one scala's plain `protected` still admits.
    */
  private def refusal(program: Program, graph: OverrideGraph, id: SymId): Option[String] =
    val s = program.symbolOf(id)
    if !s.exists(_.flags.isProtected) then Some("the member is not java `protected`")
    else if s.exists(_.flags.isStatic) then Some("a static member lives in the companion object, which no subclass inherits from")
    else if s.exists(_.name == "<init>") then Some("a constructor is referenced by `new`, which this check cannot read")
    else
      val closure = graph.closureOf(id)
      closure.anchorReason(program).map(r => s"the override component cannot move: $r").orElse {
        closure.members.toList.sortBy(_.raw).iterator.flatMap(m => program.usages(m).iterator.flatMap(u => unsafeUse(program, graph, m, u))).nextOption()
      }

  /** a reference to `m` that java's package access allowed and scala's plain `protected` does not: outside every subclass of `m`'s declaring type, through a receiver that is not the accessing class,
    * or from a static context. Anything this cannot read is unsafe.
    */
  private def unsafeUse(program: Program, graph: OverrideGraph, m: SymId, u: Usage): Option[String] =
    if u.kind != UsageKind.Call && u.kind != UsageKind.TermRef then return scala.None
    val decl = graph.ownerOf(m)
    def site = s"${u.site.origin.javaPath}:${u.site.origin.line} in ${program.symbolOf(u.enclosing).map(_.fullName).getOrElse("?")}"
    def subOfDecl(c: SymId) = decl != SymId.None && (c == decl || graph.ancestorsOf(c).contains(decl))
    def conforms(t:  SymId, c: SymId) = t == c || graph.ancestorsOf(t).contains(c)
    val chain = LazyList.unfold(u.enclosing)(x => if x == SymId.None then scala.None else Some(x -> program.symbolOf(x).map(_.owner).getOrElse(SymId.None))).take(64).toList
    val static = chain.takeWhile(x => !graph.types(x)).exists(x => program.symbolOf(x).exists(_.flags.isStatic))
    val accessing = chain.filter(x => graph.types(x) && subOfDecl(x))
    def headOf(t: TypeRepr): Option[SymId] = t match
      case TypeRepr.TypeRef(_, h)      => Some(h)
      case TypeRepr.AppliedType(tc, _) => headOf(tc)
      case _                           => scala.None
    def receiverOk(q: Term): Boolean = q match
      case Tree.This(cls, _, _)  => subOfDecl(cls)
      case Tree.Super(cls, _, _) => subOfDecl(cls)
      case other                 => headOf(other.tpe).exists(h => accessing.exists(c => conforms(h, c)))
    val receiver: Option[Option[Term]] = u.site match
      case Tree.Apply(Tree.Select(q, _, _, _), _, _, _, _) => Some(Some(q))
      case Tree.Apply(_: Tree.Ident, _, _, _, _)           => Some(scala.None)
      case Tree.Select(q, _, _, _)                         => Some(Some(q))
      case _: Tree.Ident => Some(scala.None)
      case Tree.MethodRef(Right(q), _, _, _, _) => Some(Some(q))
      case _                                    => scala.None
    val ok = !static && accessing.nonEmpty && receiver.exists(_.forall(receiverOk))
    Option.unless(ok) {
      val what = program.symbolOf(m).map(_.fullName).getOrElse(m.raw)
      if u.enclosing == SymId.None then s"a reference to $what sits outside any declaration ($site)"
      else if static then s"$what is referenced from a static context ($site)"
      else if accessing.isEmpty then s"$what is referenced from a class that is not a subclass of its declaring type ($site)"
      else s"$what is referenced through a receiver that is not the accessing subclass ($site)"
    }

object VisibilityTransform:

  /** the setting a narrowing key is reported under. */
  val NarrowSetting = "VisibilityTransform(narrow)"

  /** on a java `protected` symbol: render scala's plain `protected`, not `protected[pkg]`. */
  case object SubclassOnly extends SymTag
