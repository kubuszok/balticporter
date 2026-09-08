package balticporter.transform

import balticporter.core.{MergeablePolicy, PolicyFinding, PolicyIssue, PolicyReport, PolicySource, SurfacePolicy}
import balticporter.tir.*

/** Append hand-written Scala MEMBERS to a mechanically-translated class, at the end of its body —
  * the seam for hand-port-added API that `inject`/`MethodBodyTransform` cannot express. §1(b):
  * mechanism (locate owner by FQN, append verbatim Scala) is universal; WHICH/WHAT is per-library.
  * MINTS members, so no-op is `Only(Set.empty)`. Never changes an EXISTING member; does not
  * type-check — the target compiler is the gate. @param members owner FQN (upstream) -> specs
  * @param fromReference owner FQN (upstream) -> member NAMES read verbatim from the manifest's
  * reference port (`RunScope.referenceSource`, DESIGN.md §8.30): the hand port's own extras, spliced
  * where the reference declares them (class or companion), its imports they mention ahead of them. */
final class AddMembersTransform(val members: Map[String, List[AddMembersTransform.MemberSpec]] = Map.empty,
                                val fromReference: Map[String, List[String]] = Map.empty)
    extends Phase, PolicySource, SurfacePolicy, MergeablePolicy, PolicyBound:
  import AddMembersTransform.*

  def name: String = "add-members"

  private var runScope: RunScope = RunScope.whole
  /** `fromReference` owners bound to their type symbols (a name is not a structural fact, §4.56). */
  private var boundRef: Map[String, SymId] = Map.empty
  private var records: List[PolicyBinder.Record] = Nil
  def bindPolicy(binder: PolicyBinder): Unit =
    runScope = binder.run
    boundRef = fromReference.keys.toList.sorted.flatMap(o =>
      binder.bindType(name, "AddMembersTransform(fromReference)", o).toOption.map(o -> _)).toMap
    records = binder.recordsFor(name)

  /** Fingerprint: owner -> sorted member names; a reference-read name is marked `@ref`. Empty map =
    * empty string = omitted segment. */
  def surfaceFingerprint: String =
    val own = members.toList.sortBy(_._1).map((o, ms) =>
      s"$o=${ms.map(m => s"${m.name}/${m.arity}${if m.static then "!" else ""}").sorted.mkString(",")}")
    val ref = fromReference.toList.sortBy(_._1).map((o, ns) => s"$o=${ns.sorted.map(_ + "@ref").mkString(",")}")
    (own ++ ref).mkString(";")

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
      val refConflicts = for
        (owner, names) <- o.fromReference.toList.sortBy(_._1)
        existing       <- fromReference.get(owner).toList
        n              <- names
        if existing.contains(n)
      yield s"${MemberKey(owner, n).render}: reference member already listed"
      if conflicts.nonEmpty || refConflicts.nonEmpty then Left((conflicts ++ refConflicts).mkString("; "))
      else
        val merged = (members.keySet ++ o.members.keySet).toList.sorted.map { k =>
          k -> (members.getOrElse(k, Nil) ++ o.members.getOrElse(k, Nil))
        }.toMap
        val mergedRef = (fromReference.keySet ++ o.fromReference.keySet).toList.sorted.map { k =>
          k -> (fromReference.getOrElse(k, Nil) ++ o.fromReference.getOrElse(k, Nil))
        }.toMap
        val added = (o.members.keySet ++ o.fromReference.keySet) -- members.keySet -- fromReference.keySet
        Right(MergeablePolicy.Merged(new AddMembersTransform(merged, mergedRef), added.map(MergeablePolicy.subjectOf)))
    case _ => Left(s"expected AddMembersTransform, got ${later.getClass.getSimpleName}")

  def subjects: Set[String] = (members.keySet ++ fromReference.keySet).map(MergeablePolicy.subjectOf)

  def policyReport: PolicyReport = PolicyReport(
    members.toList.sortBy(_._1).flatMap { (owner, specs) =>
      specs.flatMap { s =>
        // If no owner was found in the program, the whole entry is unmatched
        if !ownerFound.contains(owner) then
          List(PolicyFinding(name, "AddMembersTransform", MemberKey(owner, s.name).render,
            PolicyIssue.NeverMatched, s"no type '$owner' in this program"))
        else Nil
      }
    } ++ refFindings) ++ PolicyReport.fromBindings(records)

  private var ownerFound: Set[String] = Set.empty
  private var refFindings: List[PolicyFinding] = Nil

  /** `fromReference` resolved against the reference tree: owner FQN -> the specs it yields; a name
    * the reference does not declare, or a run with no reference, is a counted finding. */
  private def referenceSpecs(program: Program): Map[String, List[MemberSpec]] =
    val out = collection.mutable.ListBuffer.empty[PolicyFinding]
    val specs = fromReference.toList.sortBy(_._1).flatMap { (owner, names) =>
      val sym = boundRef.get(owner).flatMap(program.symbolOf)
      (sym, runScope.referenceSource) match
        case (scala.None, _) => Nil // the binding already reported the unmatched owner
        case (_, scala.None) =>
          names.foreach(n => out += PolicyFinding(name, "AddMembersTransform(fromReference)", MemberKey(owner, n).render,
            PolicyIssue.Unverifiable, "this run has no reference port (`parity` undeclared), so there is no tree to read the member from"))
          Nil
        case (Some(s), Some(lookup)) =>
          val found = lookup.membersOf(s, names)
          names.filterNot(n => found.exists(_.name == n)).foreach(n =>
            out += PolicyFinding(name, "AddMembersTransform(fromReference)", MemberKey(owner, n).render,
              PolicyIssue.NeverMatched, s"the reference declares no `$n` on the type standing for `$owner`"))
          val ms = found.map { m =>
            MemberSpec(m.name, 0, (m.imports :+ m.source).mkString("\n"),
              Reason.Configured("add-members", MemberKey(owner, m.name).render),
              Some(s"the reference port's own ${m.kind} `${m.name}`, spliced verbatim from its tree"), m.static)
          }
          if ms.isEmpty then Nil else List(owner -> ms)
    }.toMap
    refFindings = out.toList
    specs

  override def run(program: Program): Program =
    if members.isEmpty && fromReference.isEmpty then return program

    ownerFound = Set.empty
    val fromRef = referenceSpecs(program)
    val all: Map[String, List[MemberSpec]] =
      (members.keySet ++ fromRef.keySet).toList.map(k => k -> (members.getOrElse(k, Nil) ++ fromRef.getOrElse(k, Nil))).toMap

    val byOwner: Map[SymId, List[MemberSpec]] =
      program.symbols.all.iterator
        .filter(s => all.contains(s.fullName))
        .map { s =>
          ownerFound += s.fullName
          s.id -> all(s.fullName)
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
