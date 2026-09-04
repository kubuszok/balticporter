package balticporter.transform

import balticporter.core.{MergeablePolicy, PolicyFinding, PolicyIssue, PolicyReport, PolicySource, SurfacePolicy}
import balticporter.tir.*

/** Append hand-written Scala MEMBERS to a mechanically-translated class, at the end of its body —
  * the seam for hand-port-added API that `inject` (whole file) and `MethodBodyTransform` (body
  * replacement) cannot express.
  *
  * CLAUDE.md §1(b): the MECHANISM (locate an owner by FQN, append verbatim Scala) is universal;
  * WHICH owners and WHAT members is per-library and arrives as a constructor parameter. The phase
  * MINTS members, so its no-op is `Only(Set.empty)` (an empty `members` map), never the
  * unrestricted form. Never changes an EXISTING member — additions sit BESIDE them — and does not
  * type-check the source; the target compiler is the gate.
  *
  * @param members
  *   owner FQN (upstream namespace) -> list of member specifications. Keys use `Symbol.fullName` of
  *   the owning type, in the UPSTREAM namespace (before package rename).
  */
final class AddMembersTransform(val members: Map[String, List[AddMembersTransform.MemberSpec]] = Map.empty)
    extends Phase, PolicySource, SurfacePolicy, MergeablePolicy:
  import AddMembersTransform.*

  def name: String = "add-members"

  /** Fingerprint: owner -> sorted member names. Empty map = empty string = omitted segment. */
  def surfaceFingerprint: String =
    if members.isEmpty then ""
    else members.toList.sortBy(_._1).map((o, ms) =>
      s"$o=${ms.map(m => s"${m.name}/${m.arity}").sorted.mkString(",")}").mkString(";")

  /** Independent owners UNION; same owner+name REFUSES — two different members at the same
    * declaration is a conflict only a human can resolve. */
  def mergedWith(later: Phase): Either[String, MergeablePolicy.Merged] = later match
    case o: AddMembersTransform =>
      val conflicts = for
        (owner, specs) <- o.members.toList.sortBy(_._1)
        existing       <- members.get(owner).toList
        s              <- specs
        if existing.exists(e => e.name == s.name && e.arity == s.arity)
      yield s"${MemberKey(owner, s.name).render}/${s.arity}: member already declared"
      if conflicts.nonEmpty then Left(conflicts.mkString("; "))
      else
        val merged = (members.keySet ++ o.members.keySet).toList.sorted.map { k =>
          k -> (members.getOrElse(k, Nil) ++ o.members.getOrElse(k, Nil))
        }.toMap
        val added = o.members.keySet -- members.keySet
        Right(MergeablePolicy.Merged(new AddMembersTransform(merged), added.map(MergeablePolicy.subjectOf)))
    case _ => Left(s"expected AddMembersTransform, got ${later.getClass.getSimpleName}")

  def subjects: Set[String] = members.keySet.map(MergeablePolicy.subjectOf)

  def policyReport: PolicyReport = PolicyReport(
    members.toList.sortBy(_._1).flatMap { (owner, specs) =>
      specs.flatMap { s =>
        // If no owner was found in the program, the whole entry is unmatched
        if !ownerFound.contains(owner) then
          List(PolicyFinding(name, "AddMembersTransform", MemberKey(owner, s.name).render,
            PolicyIssue.NeverMatched, s"no type '$owner' in this program"))
        else Nil
      }
    })

  private var ownerFound: Set[String] = Set.empty

  override def run(program: Program): Program =
    if members.isEmpty then return program

    ownerFound = Set.empty

    val byOwner: Map[SymId, List[MemberSpec]] =
      program.symbols.all.iterator
        .filter(s => members.contains(s.fullName))
        .map { s =>
          ownerFound += s.fullName
          s.id -> members(s.fullName)
        }.toMap

    if byOwner.isEmpty then return program

    def rewrite(cd: Tree.ClassDef): Tree.ClassDef =
      val owner = program.symbolOf(cd.symbol).map(_.fullName).getOrElse("")
      val appended = byOwner.get(cd.symbol) match
        case Some(specs) =>
          specs.map { s =>
            record(Decision(
              kind       = Decision.Kind.AddedMember,
              subject    = cd.symbol,
              subjectFqn = owner,
              detail     = Map(
                "member" -> s.name,
                "arity"  -> s.arity.toString,
                "why"    -> s.why.getOrElse("hand-port member not present in upstream java"),
              ),
              reason = s.reason,
              origin = program.definitionOf(cd.symbol).map(_.origin).getOrElse(Origin.synthetic),
            ))
            Tree.Opaque(s.source, TypeRepr.NoType, Origin.synthetic)
          }
        case _ => Nil
      val body = cd.body.map {
        case c: Tree.ClassDef => rewrite(c)
        case other            => other
      } ++ appended
      cd.copy(body = body)

    val units = program.units.map(rewrite)
    program.rebuilt(units)

object AddMembersTransform:
  /** One member to add to a class body.
    *
    * @param name   the member's name, for parity-check and port-map visibility.
    * @param arity  the number of non-using value parameters (0 for a `val`/`var`).
    * @param source the verbatim Scala text, spliced at statement position at the end of the owner's
    *               body. FQN-qualified, no imports (CLAUDE.md §6).
    * @param reason the §1 classification: `Configured` for a manifest entry, `LibraryRule` for a
    *               plugged-in rule.
    * @param why    free-text explanation for the porter note's `why` field.
    */
  final case class MemberSpec(
      name: String,
      arity: Int,
      source: String,
      reason: Reason = Reason.Configured("add-members", ""),
      why: Option[String] = None,
  )
