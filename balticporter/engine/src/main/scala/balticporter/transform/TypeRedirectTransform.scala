package balticporter.transform

import balticporter.core.{MergeablePolicy, PolicyFinding, PolicyIssue, PolicyReport, PolicySource}
import balticporter.tir.*


/** Re-points every reference to one type at another, differently-named type, whole-or-none. Fills
  * the gap a dependent module otherwise has no way to close: it cannot `inject` at a base's dropped
  * FQN (one module per FQN) or un-drop it, so it supplies a shape-compatible type of its own — the
  * target compiler is the gate on shape, never this phase. `memberRenames` renames a member's whole
  * override component first (target may spell it differently, e.g. `dispose` -> `close`), against
  * the pre-redirect graph, or the split hierarchy defeats whole-or-none (`TypeRedirectMemberRenameSpec`).
  * `scopes` is per-entry (§1.5) since a base's whole-program redirect and a dependent's package-scoped
  * one merge into one instance. CLAUDE.md §1(b); safe unordered unlike `PackageRenameTransform`
  * since it leaves the original symbol resolvable.
  */
final class TypeRedirectTransform(
    val redirects: Map[String, String] = Map.empty,
    val memberRenames: Map[String, Map[String, String]] = Map.empty,
    val external: ExternalSurface = ExternalSurface.default,
    val scopes: Map[String, RuleScope] = Map.empty,
) extends Phase, Rewrite, PolicySource, MergeablePolicy, PolicyBound:

  /** The scope of one redirect entry — absent means `RuleScope.everywhere`, the phase's pre-scope
    * behaviour. */
  def scopeOf(from: String): RuleScope = scopes.getOrElse(from, RuleScope.everywhere)
  def name: String = "type-redirect"

  /** Counted by `base-surface`, not a lane of its own: the redirect is total within scope (no
    * position-blind residue for a boundary check to count), and its seam is between MODULES — a
    * dependent re-pointing a type its base dropped produces signatures the base's published map
    * does not expect (ENGINE-LIMITS: one fatal gap on the first port that hit it). */
  def accountedBy: Set[String] = Set(balticporter.runner.PortRun.BaseSurface)

  /** What the run resolved each declared SOURCE type to (§8.1), bound `Ownership.Either` rather
    * than `Owned`: this phase's whole subject is a type the module does not and cannot declare
    * (the base dropped it), so `Owned` reported ten good redirects as never-matched on the one
    * port that uses it (`policy 0 -> 10`). */
  private var bound: Map[String, Binding[SymId]] = Map.empty
  private var records: List[PolicyBinder.Record] = Nil

  /** The member renames, parsed and bound `Owned` (unlike the type above: a rename rewrites a
    * declaration, and an interned-external-only key has nothing to rewrite). */
  private var boundRenames: List[TypeRedirectTransform.Rename] = Nil

  /** This phase's own findings — a malformed member segment, a `memberRenames` block for a type
    * that is not redirected, and every counted refusal the run makes. */
  private var ownFindings: List[PolicyFinding] = Nil

  def bindPolicy(binder: PolicyBinder): Unit =
    bound = redirects.keys.toList.sorted
      .map(k => k -> binder.bindType(name, "TypeRedirectTransform(redirects) source", k, Ownership.Either))
      .toMap
    val bad = collection.mutable.ListBuffer.empty[PolicyFinding]
    boundRenames = memberRenames.toList.sortBy(_._1).flatMap { (from, renames) =>
      redirects.get(from) match
        // a rename exists because the TARGET spells the member differently; with no `redirects`
        // entry for this type there is no other spelling to rename towards
        case scala.None =>
          bad += PolicyFinding(name, "TypeRedirectTransform(memberRenames)", from, PolicyIssue.Malformed,
            "`memberRenames` names a type this phase does not redirect — a member rename exists " +
              "because the REDIRECT TARGET spells the member differently, so with no `redirects` " +
              "entry for this type there is nothing it could be renamed towards")
          Nil
        // keyed `owner#member`, never the bare segment — a bare `dispose()` would match no
        // contributed set and drop the dependent's own malformed-entry finding on a merged phase
        case Some(to) => renames.toList.sortBy(_._1).flatMap { (member, newName) =>
          MemberKey.parseIn(from, member) match
            case Left(m) =>
              bad += PolicyFinding(name, s"TypeRedirectTransform(memberRenames) of `$from`",
                MemberKey.spell(from, member), PolicyIssue.Malformed, m.what)
              Nil
            case Right(_) if newName.isEmpty =>
              bad += PolicyFinding(name, s"TypeRedirectTransform(memberRenames) of `$from`",
                MemberKey.spell(from, member), PolicyIssue.Malformed,
                "the new name is empty, which names nothing")
              Nil
            case Right(mk) =>
              val entry = mk.render
              // a hit with no symbol is a member the port already DROPPED — nothing left to rename
              val hits  = binder.bindMembers(name, s"TypeRedirectTransform(memberRenames) of `$from`", entry)
                .toOption.getOrElse(Nil).flatMap(_.sym)
              List(TypeRedirectTransform.Rename(from, to, entry, newName, hits))
        }
    }
    ownFindings = bad.toList
    records = binder.recordsFor(name)

  /** Re-pointing a type changes emitted signatures, so it is shared surface; a member rename moves
    * the signature too and is rendered here, while an entry with no renames spells exactly what it
    * always did so old configs still compare equal. */
  def surfaceFingerprint: String = redirects.toList.sorted.map { (f, t) =>
    // the scope decides which declarations carry the redirected type, so it is surface too;
    // rendered only where it says something, or a port predating the parameter compares unequal
    val sc = scopeOf(f)
    val at = if sc.isUnrestricted then "" else s"@${sc.fingerprint}"
    memberRenames.getOrElse(f, Map.empty).toList.sorted match
      case Nil => s"$f->$t$at"
      case rs  => s"$f->$t$at[" + rs.map((m, n) => s"$m=$n").mkString(",") + "]"
  }.mkString(",")

  /** Every type this instance's policy is keyed on — a redirect source, and the owner of a member
    * rename. Used by `mergedWith`'s contract (DESIGN.md §8.13): independent-FQN keys union, a key
    * both hold with the same value agrees, different values refuse; `external` unions freely (not
    * policy — a fact about a platform's known members, not in `surfaceFingerprint` either). */
  def subjects: Set[String] =
    (redirects.keySet ++ memberRenames.keySet).map(MergeablePolicy.subjectOf)

  def mergedWith(later: Phase): Either[String, MergeablePolicy.Merged] = later match
    case o: TypeRedirectTransform =>
      val typeClash = (redirects.keySet & o.redirects.keySet).filter(k => redirects(k) != o.redirects(k))
      // compared by parsed NAME, not by map key — `dispose` and `dispose()` are one member
      def named(t: String, mem: String): String =
        MemberKey.parseIn(t, mem).map(_.name).getOrElse(mem)
      val memberClash = for
        (t, rs)   <- o.memberRenames.toList
        (mem, to) <- rs
        mine       = memberRenames.getOrElse(t, Map.empty)
        (m2, to2) <- mine.toList
        if named(t, m2) == named(t, mem) && to2 != to
      yield (t, mem, to, m2, to2)
      // a scope does not compose as an independent-key table — two scopes on one source refuse
      val scopeClash = (redirects.keySet & o.redirects.keySet).toList.sorted
        .filter(k => scopeOf(k) != o.scopeOf(k))
      if typeClash.nonEmpty || memberClash.nonEmpty || scopeClash.nonEmpty then
        Left(
          (scopeClash.map(k =>
             s"""both modules scope the redirect of "$k" and disagree — """ +
               s""""${scopeOf(k).fingerprint}" and "${o.scopeOf(k).fingerprint}"; a scope decides """ +
               "which declarations carry the redirected type in their signatures") ++
            typeClash.toList.sorted.map(k =>
             s"""both modules redirect "$k", to "${redirects(k)}" and "${o.redirects(k)}"""") ++
            memberClash.sorted.distinct.map { (t, mem, to, m2, to2) =>
              val how =
                if mem == m2 then s"""the member `$mem` of "$t""""
                else s"""`$m2` and `$mem` of "$t", which are ONE member (a bare key is every overload)"""
              s"""both modules rename $how, to "$to2" and "$to""""
            })
            .mkString("; ") +
            " — two answers for one key is a rewrite whose outcome depends on which manifest was read")
      else
        val renames = o.memberRenames.foldLeft(memberRenames) { (acc, e) =>
          acc.updated(e._1, acc.getOrElse(e._1, Map.empty) ++ e._2)
        }
        val addedRenameOwners = o.memberRenames.collect {
          case (t, rs) if rs.exists((m, n) => !memberRenames.getOrElse(t, Map.empty).get(m).contains(n)) => t
        }.toSet
        Right(MergeablePolicy.Merged(
          new TypeRedirectTransform(
            redirects     = redirects ++ o.redirects,
            memberRenames = renames,
            external      = ExternalSurface(external.known ++ o.external.known),
            scopes        = scopes ++ o.scopes),
          (o.redirects.keySet -- redirects.keySet) ++ addedRenameOwners))
    case other =>
      Left(s"`${other.name}` is not a `TypeRedirectTransform`, so there is no table to compose")

  private var mapping: Map[SymId, SymId]     = Map.empty
  private var memberTwins: Map[SymId, SymId] = Map.empty

  /** Refusals this run made — reset at the head of every run, since a phase instance is reused
    * across two translations. */
  private var runFindings: List[PolicyFinding] = Nil

  /** Declared sources that occur nowhere, plus this phase's own malformed entries and counted
    * rename refusals. */
  def policyReport: PolicyReport =
    PolicyReport.fromBindings(records) ++ PolicyReport(ownFindings ++ runFindings) ++
      PolicyReport(scopes.toList.sortBy(_._1).flatMap { (from, sc) =>
        sc.neverFired(scopeFired.getOrElse(from, Set.empty)).toList.sorted.map(k =>
          PolicyFinding(name, s"""TypeRedirectTransform(scope) of "$from", ${sc.productPrefix} entry""",
            k, PolicyIssue.NeverMatched,
            "no top-level type with this fully-qualified name occurs in this program, so the scope " +
              "neither held a declaration back nor admitted one — this redirect ran as if the entry " +
              "were absent"))
      })

  /** Per redirect source, the scope entries that placed at least one unit this run — the
    * complement of `RuleScope.neverFired`. */
  private var scopeFired: Map[String, Set[String]] = Map.empty

  /** Is this declaration one the given scope admits, asked through the owner chain? An unresolvable
    * symbol takes each direction's conservative arm — IN for `Everywhere`, OUT for `Only`. */
  private def inScope(sc: RuleScope, program: Program, id: SymId): Boolean =
    if sc.isUnrestricted then true
    else program.symbolOf(id) match
      case Some(s)    => sc.includes(program, s)
      case scala.None => sc match
        case RuleScope.Everywhere(_) => true
        case RuleScope.Only(_)       => false

  override def run(program0: Program): Program =
    runFindings = Nil
    scopeFired  = Map.empty
    if redirects.isEmpty then
      mapping = Map.empty; memberTwins = Map.empty
      return program0

    // renamed first, against the graph as JAVA declared it (see class doc)
    val program = renameMembers(program0)

    val byName = program.symbols.all.iterator.map(s => s.fullName -> s).toMap
    var table  = program.symbols
    var next   = program.symbols.all.map(_.id.raw).maxOption.getOrElse(-1) + 1

    // reuse the target symbol when referenced, else mint one — the dependent's injected replacement
    // is never parsed. `NoType`/`SymId.None` with a dotted, `#`-free name is what the emitter reads
    // as an external TYPE, letting the minted symbol stand in a static-access receiver position.
    def targetOf(fqn: String): SymId = byName.get(fqn).map(_.id).getOrElse {
      val id = SymId(next); next += 1
      table = table.updated(Symbol(id, fqn.split('.').last, fqn, Flags(), SymId.None, TypeRepr.NoType))
      id
    }

    mapping = redirects.toList.sortBy(_._1).flatMap { (from, to) =>
      bound.get(from).flatMap(_.toOption).map(_ -> targetOf(to))
    }.toMap

    /** The scope governing each mapped source symbol — the entry's own, so a base's whole-program
      * redirect and a dependent's package-scoped one can sit in the merged instance together. */
    val scopeBySource: Map[SymId, RuleScope] = redirects.toList.flatMap { (from, _) =>
      bound.get(from).flatMap(_.toOption).map(_ -> scopeOf(from))
    }.toMap
    val entryBySource: Map[SymId, String] = redirects.toList.flatMap { (from, _) =>
      bound.get(from).flatMap(_.toOption).map(_ -> from)
    }.toMap

    // members move with the type. A never-parsed type reaches its statics through an explicit
    // `Select(Ident(type), member)`, which `transformIdent` moves; a PARSED type is re-qualified by
    // the emitter from the member symbol's OWNER (`staticThroughInstance`), undoing a qualifier-only
    // rewrite silently — so a TWIN (same name/signature, owner = target) is minted instead of
    // re-pointing the original's owner, which would detach it from its unit (§4.56) and break every
    // "base's declarations, not mine" filter (D2).
    memberTwins = mapping.flatMap { (fromType, toType) =>
      // read both names from `table`, never `program` — a minted target is only in the former
      val fromFqn = table.get(fromType).map(_.fullName).getOrElse("")
      val toFqn   = table.get(toType).map(_.fullName).getOrElse("")
      // static members only — an instance member's receiver TYPE has already moved
      program.symbols.all.filter(m => m.owner == fromType && m.flags.isStatic).toList.map { m =>
        val id = SymId(next); next += 1
        table = table.updated(m.copy(
          id       = id,
          owner    = toType,
          fullName = if m.fullName.startsWith(fromFqn) then toFqn + m.fullName.drop(fromFqn.length)
                     else m.fullName,
        ))
        m.id -> id
      }
    }

    val membersOf: Map[SymId, List[SymId]] =
      mapping.map((fromType, _) => fromType -> program.symbols.all.filter(_.owner == fromType).map(_.id).toList)

    // one decision row per (declaration, redirect entry), read from the pre-rewrite program; a call
    // to a static of the redirected type uses the member symbol, so members are folded in too
    redirects.toList.sorted.foreach { (from, to) =>
      bound.get(from).flatMap(_.toOption).foreach { fromId =>
        val sites = (fromId :: membersOf.getOrElse(fromId, Nil)).flatMap(Decision.declarationsUsing(program, _))
          // …SCOPED, exactly as the rewrite is. A row for a declaration the scope held back would
          // claim a signature change that did not happen, and a porter note claiming one is worse
          // than none (`NoteCoverageCheck` fails a run either way).
          .filter((encl, _) => inScope(scopeOf(from), program, encl))
          .groupBy(_._1).toList
          .flatMap((encl, os) => os.map(_._2).minByOption(o => (o.javaPath, o.line)).map(encl -> _))
          .sortBy((encl, o) => (o.javaPath, o.line, encl.raw))
        sites.foreach { (encl, origin) =>
          record(Decision(
            kind       = Decision.Kind.RetypedSignature,
            subject    = encl,
            subjectFqn = Decision.fqnOf(program, encl, from),
            detail     = Map(
              "from" -> from,
              "to"   -> to,
              "key"  -> from,
              "why"  -> ("a module this port depends on drops this type outright, and exactly one " +
                "module may ship a replacement at a given FQN — so every reference is re-pointed " +
                "at a shape-compatible type this port declares itself"),
            ),
            reason = Reason.Configured(name, s"$from -> $to"),
            origin = origin,
          ))
        }
      }
    }

    if mapping.isEmpty then
      program.rebuilt(symbols = table)
    else
      given Program = program.rebuilt(symbols = table)
      // standard traversal (§3), so every type occurrence is reached; scoped at both the tree and
      // the symbol `info` or the two read differently. One pass per distinct scope — the `mapping`
      // hooks read is narrowed outside the hook, since a hook cannot see which declaration it is
      // under; unrestricted entries collapse to the single pre-scope pass.
      val scoped     = summon[Program]
      val fullMap    = mapping
      val fullTwins  = memberTwins
      val twinScope  = fullTwins.keys.flatMap(m =>
        scoped.symbolOf(m).flatMap(s => scopeBySource.get(s.owner)).map(m -> _)).toMap
      var units      = program.units
      var symbols    = table
      var fired      = Map.empty[String, Set[String]]
      scopeBySource.values.toList.distinct.foreach { sc =>
        val sources = scopeBySource.collect { case (k, v) if v == sc => k }.toSet
        mapping     = fullMap.view.filterKeys(sources).toMap
        memberTwins = fullTwins.view.filterKeys(m => twinScope.get(m).contains(sc)).toMap
        units   = units.map(u =>
          if inScope(sc, scoped, u.symbol) then StandardTraversal.mapClassDef(this, u) else u)
        symbols = StandardTraversal.mapSymbols(this, symbols, s => inScope(sc, scoped, s.id))
        if !sc.isUnrestricted then
          sources.flatMap(entryBySource.get).foreach { entry =>
            val hits = program.units.flatMap(u => scoped.symbolOf(u.symbol).flatMap(sc.entryFor(scoped, _))).toSet
            fired = fired.updated(entry, fired.getOrElse(entry, Set.empty) ++ hits)
          }
      }
      mapping     = fullMap
      memberTwins = fullTwins
      scopeFired  = fired
      program.rebuilt(units, symbols) // xref rebuilt by the Pipeline

  // -------------------------------------------------------------------------------------------
  // the member renames — everything below runs BEFORE a single reference has moved
  // -------------------------------------------------------------------------------------------

  /** Rename every declaration of each named member's whole override component to what the target
    * calls it, then hand the rewritten program to the redirect. Three counted refusals: the target
    * must declare the new name where known; the component must be movable (`MemberRenamer`'s own
    * screen); and the new name must be free (`OnCollision.Refuse` — the requested name IS the
    * target's, so a `$`-suffixed landing spot would defeat the first screen). */
  private def renameMembers(program: Program): Program =
    if boundRenames.isEmpty then program
    else
      // no `baseUnits`: a dependent's Program contains its base and does not emit it (D2), so
      // renaming here defines nothing twice and lets the dependent's own overrides come out under
      // the base's already-emitted name; `SurfacePolicy` catches a base/dependent disagreement.
      val graph = OverrideGraph.build(program, external)
      val screened        = boundRenames.map(r => r -> targetRefusal(graph, r))
      val (refused, live) = (screened.collect { case (r, Some(why)) => (r, why) },
                             screened.collect { case (r, scala.None) => r })
      refused.foreach((r, why) => refuseRename(r, why))
      val requests = live.flatMap(r => r.hits.map(h =>
        MemberRenamer.Request(h, r.newName, Reason.Configured(name, r.key), r.key, r.key)))
      if requests.isEmpty then program
      else
        val (out, refusals) = MemberRenamer.rename(
          program, graph, requests, MemberRenamer.OnCollision.Refuse, decisions)
        refusals.map(_.request.key).distinct.foreach { k =>
          val why = refusals.find(_.request.key == k).map(_.why).getOrElse("refused")
          live.find(_.key == k).foreach(r => refuseRename(r, why))
        }
        out

  /** Does the redirect target declare the name this entry asks for? `None` when it does, or when
    * nothing is known about the target (the target compiler stays the gate). The signature checked
    * is the member's own with the new name substituted, so an arity mismatch is caught too. */
  private def targetRefusal(graph: OverrideGraph, r: TypeRedirectTransform.Rename): Option[String] =
    if r.hits.isEmpty || !external.isKnown(r.target) then scala.None
    else
      val declared = external.known.getOrElse(r.target, Set.empty)
      val ok = r.hits.forall { h =>
        graph.signatureOf(h) match
          case Some(sig) => declared.exists(_.matches(sig.copy(name = r.newName)))
          case scala.None => declared.exists(_.name == r.newName)
      }
      val has = declared.toList.map(_.name).distinct.sorted match
        case Nil => "declares nothing at all"
        case ns  => s"declares ${ns.mkString(", ")}"
      Option.when(!ok)(
        s"`${r.target}` does not declare `${r.newName}` with this member's shape — its surface is " +
          s"known to this engine and it $has. A rename to a name the target does not have emits " +
          "code that calls a method which does not exist, which is a compile error in the " +
          "consumer's repository and not here")

  /** One counted refusal: a `PolicyReport` row plus a `ScopedOut` decision. `About.ThisRun`, not
    * `TheKey` — the base owns every key here (a merged phase), but the refusal evidence is the
    * dependent's own program (ENGINE-LIMITS D13). */
  private def refuseRename(r: TypeRedirectTransform.Rename, why: String): Unit =
    runFindings = runFindings :+ PolicyFinding(
      name, s"TypeRedirectTransform(memberRenames) of `${r.source}`", r.key, PolicyIssue.Unverifiable, why,
      about = balticporter.core.PolicyFinding.About.ThisRun)
    record(Decision(
      kind       = Decision.Kind.ScopedOut,
      subject    = SymId.None,
      subjectFqn = r.entry,
      detail     = Map(
        "refused" -> "member-rename",
        "to"      -> r.newName,
        "target"  -> r.target,
        "why"     -> why,
      ),
      reason = Reason.Configured(name, r.key),
      origin = Origin.synthetic,
    ))

  override def transformType(t: TypeRepr)(using Program): TypeRepr = t match
    case TypeRepr.TypeRef(p, s) if mapping.contains(s) => TypeRepr.TypeRef(p, mapping(s))
    case other                                         => other

  /** A type also occurs as a TERM: a static access's receiver is an `Ident` of the type's own
    * symbol, rendered by the emitter from that symbol rather than the node's `TypeRepr`, so
    * `transformType` alone would leave a partial redirect at every static reference. Only type
    * symbols are in `mapping`, so a same-named term cannot be caught by accident. */
  override def transformIdent(t: Tree.Ident)(using Program): Term =
    if mapping.contains(t.sym) then t.copy(sym = mapping(t.sym))
    else if memberTwins.contains(t.sym) then t.copy(sym = memberTwins(t.sym))
    else t

  /** The member half: for a PARSED redirected type the emitter re-derives a static access from the
    * member symbol's owner, undoing a qualifier-only rewrite — so this points at the twin instead,
    * putting qualifier and owner back in agreement. */
  override def transformSelect(t: Tree.Select)(using Program): Term =
    if memberTwins.contains(t.sym) then t.copy(sym = memberTwins(t.sym)) else t

  override def transformApply(t: Tree.Apply)(using Program): Term =
    if memberTwins.contains(t.method) then t.copy(method = memberTwins(t.method)) else t

object TypeRedirectTransform:

  /** One declared member rename, parsed and bound.
    *
    * @param source the redirected type, as the policy spells it
    * @param target what it is redirected TO — the type whose name for the member is being adopted
    * @param entry  the bound member key (`owner#name`, or one overload), rendered from a `MemberKey`
    * @param newName the target's name for it
    * @param hits   every declaration the key named. The override CLOSURE of each is what actually
    *               moves; this is only what the key itself pointed at.
    */
  final case class Rename(source: String, target: String, entry: String, newName: String,
                          hits: List[SymId]):
    /** The string an agent edits — the `Reason.Configured` key and refusal-report id (§4.575). */
    def key: String = s"$entry -> $newName"
