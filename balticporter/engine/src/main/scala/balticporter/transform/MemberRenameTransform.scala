package balticporter.transform

import balticporter.core.{MergeablePolicy, PolicyFinding, PolicyIssue, PolicyReport, PolicySource, SurfacePolicy}
import balticporter.tir.*

/** Renames a member to a name the port CHOOSES — reaches [[MemberRenamer]] for a free-form rename
  * (unlike [[TypeRedirectTransform]]'s, dictated by its redirect target). `OnCollision.Refuse`: a
  * collision is reported, never silently resolved. Base-anchored (excludes this run's own emitted
  * units). `runsBefore("type-redirect")` frees a name before a redirect could collide with it;
  * `package-rename` stays last. Empty `renames` is a no-op. `{{{ renames { "com.foo.Stream#close(int)" = "closeAt" } }}}` */
final class MemberRenameTransform(val renames: Map[String, String] = Map.empty)
    extends Phase, PolicySource, SurfacePolicy, MergeablePolicy, PolicyBound:

  def name: String = MemberRenameTransform.Name

  /** exactly two edges needed; see the class note for why no others are declared. */
  override def runsBefore: Set[String] = Set("type-redirect", "package-rename")

  /** two modules that agree must compare equal (§1.5). */
  def surfaceFingerprint: String =
    renames.toList.sorted.map((k, v) => s"$k=$v").mkString(",")

  def subjects: Set[String] = renames.keySet.map(MergeablePolicy.subjectOf)

  /** DESIGN.md §8.13. Independent member keys union; one member with two names refuses. Compared
    * by parsed name via [[MemberKey.mayNameSame]], not by map key — `X#close` and `X#close()` may
    * be one member, and over-refusal is the safe direction here. */
  def mergedWith(later: Phase): Either[String, MergeablePolicy.Merged] = later match
    case o: MemberRenameTransform =>
      val clashes = for
        (theirs, to)   <- o.renames.toList
        (mine, to2)    <- renames.toList
        if MemberKey.mayNameSame(mine, theirs) && to != to2
      yield (mine, to2, theirs, to)
      if clashes.nonEmpty then
        Left(clashes.sorted.distinct.map { (mine, to2, theirs, to) =>
          val how =
            if mine == theirs then s""""$mine""""
            else s""""$mine" and "$theirs", which may be ONE member (a bare key is every overload)"""
          s"""both modules rename $how, to "$to2" and "$to""""
        }.mkString("; ") +
          " — two answers for one member is a rewrite whose outcome depends on which manifest was read")
      else
        Right(MergeablePolicy.Merged(
          new MemberRenameTransform(renames ++ o.renames),
          (o.renames.keySet -- renames.keySet).map(MergeablePolicy.subjectOf)))
    case other =>
      Left(s"`${other.name}` is not a `MemberRenameTransform`, so there is no table to compose")

  // ---- policy, bound before the pipeline starts ---------------------------------------------

  /** the entries that parsed and bound — one row per declaration each key named. */
  private var boundRenames: List[MemberRenameTransform.Entry] = Nil
  private var records: List[PolicyBinder.Record]              = Nil

  /** malformed keys and values — findings about the key. */
  private var ownFindings: List[PolicyFinding] = Nil

  /** which units this run emits; read at [[run]] to base-anchor the graph, defaulted to
    * [[RunScope.whole]] for a phase constructed in a spec. */
  private var scope: RunScope = RunScope.whole

  def bindPolicy(binder: PolicyBinder): Unit =
    scope = binder.run
    val bad = collection.mutable.ListBuffer.empty[PolicyFinding]
    boundRenames = renames.toList.sortBy(_._1).flatMap { (key, newName) =>
      def refuse(what: String): Nil.type =
        bad += PolicyFinding(name, MemberRenameTransform.Setting, key, PolicyIssue.Malformed, what)
        Nil
      MemberKey.parse(key) match
        case Left(m)                                       => refuse(m.what)
        case Right(_) if newName.isEmpty                   => refuse("the new name is empty, which names nothing")
        case Right(_) if !MemberRenamer.isValidMemberName(newName) =>
          refuse(s"`$newName` is not a valid Scala member name — this phase changes NAMES and " +
            "never shapes, so a value must be either an alphanumeric identifier, a symbolic operator " +
            "(`+`, `*`, `<=`), or a prefix operator (`unary_-`). A value carrying a `#`, a parameter " +
            "list, a `.` or whitespace names an act it does not perform (an arity change is " +
            "`bean-properties`; a re-point is `type-redirect`)")
        case Right(mk) =>
          // flatMap(_.sym), not map: a hit with no symbol is a dropped member — a no-op, not a finding.
          val hits = binder.bindMembers(name, MemberRenameTransform.Setting, mk.render)
            .toOption.getOrElse(Nil).flatMap(_.sym)
          // unary_- (and the other prefix operators) is valid only on a nullary method.
          if MemberRenamer.isUnaryName(newName) then
            val nonNullary = hits.flatMap(binder.program.symbolOf).filter { s =>
              s.info match
                case TypeRepr.MethodType(params, _, _) => params.nonEmpty
                case _                                 => false
            }
            if nonNullary.nonEmpty then
              refuse(s"`$newName` is a prefix operator and may only target a nullary method, " +
                s"but ${nonNullary.map(_.fullName).sorted.mkString(", ")} " +
                s"${if nonNullary.sizeIs == 1 then "takes" else "take"} parameters")
            else List(MemberRenameTransform.Entry(mk.render, newName, hits))
          else
            List(MemberRenameTransform.Entry(mk.render, newName, hits))
    }
    ownFindings = bad.toList
    records = binder.recordsFor(name)

  /** refusals this run made — reset per run, since a phase instance is reused across translations. */
  private var runFindings: List[PolicyFinding] = Nil

  /** Declared keys that named nothing, malformed entries, and every refusal this run made.
    * Refusals are `About.ThisRun`, the rest `About.TheKey` — a key inherited from a base is not
    * a string this module can edit. CLAUDE.md §4.45 */
  def policyReport: PolicyReport =
    PolicyReport.fromBindings(records) ++ PolicyReport(ownFindings ++ runFindings)

  // ---- the rename ----------------------------------------------------------------------------

  override def run(program: Program): Program =
    runFindings = Nil
    val live = boundRenames.filter(_.hits.nonEmpty)
    if live.isEmpty then program
    else
      // units this run does not emit; RunScope.whole yields the empty set (single-module/spec).
      val baseUnits = program.units.map(_.symbol).filterNot(scope.emits).toSet
      val graph     = OverrideGraph.build(program, baseUnits = baseUnits)
      val requests  = live.flatMap(e => e.hits.map(h =>
        MemberRenamer.Request(h, e.newName, Reason.Configured(name, e.key), e.key, e.key)))
      val (renamed, refusals) = MemberRenamer.rename(
        program, graph, requests, MemberRenamer.OnCollision.Refuse, decisions)
      refusals.map(_.request.key).distinct.foreach { k =>
        val why = refusals.find(_.request.key == k).map(_.why).getOrElse("refused")
        runFindings :+= PolicyFinding(name, MemberRenameTransform.Setting, k, PolicyIssue.Unverifiable,
          s"$why — so this rename did NOT happen and the member keeps its upstream name",
          PolicyFinding.About.ThisRun)
      }
      // a symbolic member name must carry @targetName(originalJavaName) for JVM binary compat.
      val applied = live.filter(e => !refusals.exists(_.request.key == e.key))
      val symbolicEntries = applied.filter(e => MemberRenamer.isSymbolic(e.newName))
      if symbolicEntries.isEmpty then renamed
      else MemberRenameTransform.addTargetNameAnnotations(renamed, program, symbolicEntries)

object MemberRenameTransform:

  val Name: String = "member-rename"

  val Setting: String = "MemberRenameTransform(renames)"

  /** one entry that parsed and bound: canonical key, new name, every declaration the key named. */
  private[transform] final case class Entry(key: String, newName: String, hits: List[SymId])

  /** is this a bare member name? A whitelist rather than a blacklist of separators, so a value
    * reaching the symbol table can't emit unparseable text. Deprecated for
    * [[MemberRenamer.isValidMemberName]], which also admits symbolic names. */
  private[transform] def isPlain(v: String): Boolean =
    v.nonEmpty && !v.head.isDigit && v.forall(c => c.isLetterOrDigit || c == '_' || c == '$')

  /** Adds `@scala.annotation.targetName("<javaName>")` to every symbol renamed to a symbolic
    * name, reading the java name from the ORIGINAL (pre-rename) program, for JVM binary
    * compatibility and `-Werror`-clean output. */
  private[transform] def addTargetNameAnnotations(
      renamed: Program,
      original: Program,
      symbolicEntries: List[Entry],
  ): Program =
    // find or create a symbol for scala.annotation.targetName in the table.
    val existingId = renamed.symbols.all.find(_.fullName == "scala.annotation.targetName").map(_.id)
    val targetNameSym = existingId.getOrElse {
      // a fresh id below every existing one, staying clear of SymId.None and the minter's counter.
      val minId = renamed.symbols.all.map(_.id.raw).minOption.getOrElse(0)
      SymId(math.min(minId - 1, -2))
    }
    val renamedSymIds = symbolicEntries.flatMap(_.hits).toSet
    val annotated = renamed.symbols.all.map { s =>
      if renamedSymIds.contains(s.id) then
        val origName = original.symbolOf(s.id).map(_.name).getOrElse(s.name)
        val annot = Annot(
          tpe    = TypeRepr.TypeRef(TypeRepr.NoPrefix, targetNameSym),
          args   = List("value" -> Tree.Literal(
            Constant.StringC(origName),
            TypeRepr.TypeRef(TypeRepr.NoPrefix, SymId.None),
            Origin.synthetic)),
          origin = Origin.synthetic,
        )
        s.copy(annotations = s.annotations :+ annot)
      else s
    }
    val allSyms = if existingId.isDefined then annotated
                  else annotated ++ List(Symbol(
                    targetNameSym, "targetName", "scala.annotation.targetName",
                    Flags(), SymId.None, TypeRepr.NoType))
    renamed.rebuilt(symbols = SymbolTable(allSyms))
