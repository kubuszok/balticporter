package balticporter.transform

import balticporter.core.{MergeablePolicy, PolicyFinding, PolicyIssue, PolicyReport, PolicySource, SurfacePolicy}
import balticporter.tir.*

/** Append hand-written Scala MEMBERS to a mechanically-translated class, at the end of its body.
  *
  * ==The gap this fills==
  * A reference hand port may add members java never declared — `Engine.registerComponentFactory` in
  * sge-ecs is the worked example: a factory-registry API that replaces the reflective
  * `ClassReflection.newInstance` the mechanical port drops. The two existing seams (`inject` and
  * `MethodBodyTransform`) cannot express this: `inject` is a whole FILE, so the type stops tracking
  * upstream; a body substitution keeps the type mechanical but cannot ADD a member that java never
  * wrote. This phase is that seam.
  *
  * ==Kind==
  * CLAUDE.md §1(b): the MECHANISM — locate an owner by FQN, append verbatim Scala — is a fact
  * about Java and Scala and is the same for every library. WHICH owners and WHAT members is a fact
  * about one library and arrives as a constructor parameter. An empty map is a no-op.
  *
  * ==ADD-scoped, `Only(Set.empty)` default==
  * This phase MINTS members, so its unrestricted form is not a safe default (CLAUDE.md §1(b)). Its
  * no-op is `Only(Set.empty)` — which is an empty `members` map, since the scope is implicit in the
  * keys.
  *
  * ==What it deliberately does NOT do==
  *   - **It never changes an existing member.** Every member the java declares stays mechanically
  *     translated; what this phase adds is BESIDE them. A port that needs to REPLACE a member wants
  *     `MethodBodyTransform` or `dropMethods` + inject.
  *   - **It does not type-check the source.** The text is spliced verbatim, and the target compiler
  *     is the gate — the same contract `MethodBodyTransform` has.
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
      yield s"$owner#${s.name}/${s.arity}: member already declared"
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
          List(PolicyFinding(name, "AddMembersTransform", s"$owner#${s.name}",
            PolicyIssue.NeverMatched, s"no type '$owner' in this program"))
        else Nil
      }
    })

  private var ownerFound: Set[String] = Set.empty

  override def run(program: Program): Program =
    if members.isEmpty then return program

    ownerFound = Set.empty

    // Build a lookup from owner FQN to specs
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
      // Append added members to this class's body
      val appended = byOwner.get(cd.symbol) match
        case Some(specs) =>
          specs.map { s =>
            // Record the decision
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
            // Create an opaque statement with the member source text
            Tree.Opaque(s.source, TypeRepr.NoType, Origin.synthetic)
          }
        case _ => Nil
      // Recursively rewrite nested classes too
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
