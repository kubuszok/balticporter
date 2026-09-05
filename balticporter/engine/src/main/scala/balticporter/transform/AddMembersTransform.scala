package balticporter.transform

import balticporter.core.{MergeablePolicy, PolicyFinding, PolicyIssue, PolicyReport, PolicySource, SurfacePolicy}
import balticporter.tir.*

/** Append hand-written Scala MEMBERS to a mechanically-translated class, at the end of its body —
  * the seam for hand-port-added API that `inject`/`MethodBodyTransform` cannot express. §1(b):
  * mechanism (locate owner by FQN, append verbatim Scala) is universal; WHICH/WHAT is per-library.
  * MINTS members, so no-op is `Only(Set.empty)`. Never changes an EXISTING member; does not
  * type-check — the target compiler is the gate. @param members owner FQN (upstream) -> specs. */
final class AddMembersTransform(val members: Map[String, List[AddMembersTransform.MemberSpec]] = Map.empty)
    extends Phase, PolicySource, SurfacePolicy, MergeablePolicy:
  import AddMembersTransform.*

  def name: String = "add-members"

  /** Fingerprint: owner -> sorted member names. Empty map = empty string = omitted segment. */
  def surfaceFingerprint: String =
    if members.isEmpty then ""
    else members.toList.sortBy(_._1).map((o, ms) =>
      s"$o=${ms.map(m => s"${m.name}/${m.arity}${if m.static then "!" else ""}").sorted.mkString(",")}").mkString(";")

  /** Independent owners UNION; same owner+name REFUSES — two different members at the same
    * declaration is a conflict only a human can resolve. */
  def mergedWith(later: Phase): Either[String, MergeablePolicy.Merged] = later match
    case o: AddMembersTransform =>
      val conflicts = for
        (owner, specs) <- o.members.toList.sortBy(_._1)
        existing       <- members.get(owner).toList
        s              <- specs
        if existing.exists(e => e.name == s.name && e.arity == s.arity && e.static == s.static)
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
                "home"   -> (if s.static then "companion" else "class"),
                "why"    -> s.why.getOrElse("hand-port member not present in upstream java"),
              ),
              reason = s.reason,
              origin = program.definitionOf(cd.symbol).map(_.origin).getOrElse(Origin.synthetic),
            ))
            Tree.Opaque(s.source, TypeRepr.NoType, Origin.synthetic, Nil,
                        Option.when(s.static)(s.name))
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
  /** One member to add to a class body. @param name for parity-check/port-map visibility
    * @param arity non-using value parameters (0 for a val/var) @param source verbatim Scala,
    * spliced at statement position, FQN-qualified no imports (CLAUDE.md §6) @param reason §1
    * classification @param why free text for the porter note. */
  final case class MemberSpec(
      name: String,
      arity: Int,
      source: String,
      reason: Reason = Reason.Configured("add-members", ""),
      why: Option[String] = None,
      /** splice into the COMPANION object rather than the class body — a java static's home, and
        * the only shape a FACTORY can take (`CLAUDE.md` §1(b)). */
      static: Boolean = false,
  )
