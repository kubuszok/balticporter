package balticporter.transform

import balticporter.core.{PolicyFinding, PolicyIssue, PolicyReport, PolicySource, PortMap, PortManifest, SurfacePolicy}
import balticporter.tir.*

/** Migrates a DEPENDENT's references from what its base module actually PUBLISHED — re-points a
  * renamed type/member to the base's emitted name, and reports a call to a member the base dropped
  * or replaced with a hand-supplied body. `maps` are the base's own published [[PortMap]]s; empty
  * is a total no-op. Does not rewrite a dropped call, consult its own module's map, or verify
  * freshness ([[PortMap.freshness]]'s job).
  * CLAUDE.md §1(b), §1.5; ENGINE-LIMITS D2, D14
  */
final class PortMapTransform(maps: List[PortMap.Map0] = Nil) extends Phase, PolicySource, SurfacePolicy:
  def name: String = "port-map-migration"

  /** the map's identity — module, engine, source fingerprint, entry count — so two modules on
    * different base revisions do not fingerprint equal. */
  def surfaceFingerprint: String =
    maps.map(m => s"${m.module}@${m.engine}/${m.sources}#${m.entries.size}").sorted.mkString(",")

  // ---------------------------------------------------------------------------
  // lookups, built once from the maps
  // ---------------------------------------------------------------------------

  /** upstream type FQN → (publishing module, entry). Nearest map LAST so it wins, matching the
    * precedence `PortManifest.effectivePackageRenames` uses. */
  private val typeEntries: Map[String, (String, PortMap.Entry)] =
    maps.foldLeft(Map.empty[String, (String, PortMap.Entry)]) { (acc, m) =>
      acc ++ m.types.iterator.filter(_.upstream.nonEmpty).map(e => e.upstream -> (m.module, e))
    }

  /** `owner#name` → every overload the maps record for it. The map keys members precisely
    * (`owner#name(P1,P2)`); a TIR symbol's `fullName` carries no parameter list at all, so the bare
    * form is the only key both sides can agree on and the overload is chosen by ARITY below. */
  private val memberEntries: Map[String, List[(String, PortMap.Entry)]] =
    maps.flatMap(m => m.members.filter(_.upstream.nonEmpty).map(e => (m.module, e)))
      .groupBy((_, e) => PortMapTransform.bareKey(e.upstream))

  /** upstream prefix → the name the base emitted, for every type it moved. */
  private val renames: Map[String, String] =
    typeEntries.valuesIterator.collect {
      case (_, e) if e.disposition == PortMap.Disposition.Renamed && e.emitted.nonEmpty => e.upstream -> e.emitted
    }.toMap

  private var report: PolicyReport = PolicyReport.empty
  private var found: List[PortMapTransform.Finding] = Nil
  private var repointed: Int = 0

  /** types the base SUBSTITUTED — dropped and replaced by a hand-written injection. A dependent's
    * detection phases skip these owners (D14). Read BEFORE the pipeline runs. */
  def substitutedOwnerTypes: Set[String] = maps.flatMap(_.types).collect {
    case e if e.disposition == PortMap.Disposition.Substituted && e.upstream.nonEmpty => e.upstream
  }.toSet

  /** what the maps say about this program, in a stable order. Read AFTER the pipeline has run. */
  def findings: List[PortMapTransform.Finding] = found

  /** how many symbols this phase moved to the name the base actually emitted. */
  def renamedSymbols: Int = repointed

  /** a map that matched NOTHING — no per-entry `NeverMatched`, since a base publishes far more
    * entries than a dependent touches. Reported once per map. */
  def policyReport: PolicyReport = report

  // ---------------------------------------------------------------------------

  override def run(program: Program): Program =
    if maps.isEmpty then
      report = PolicyReport.empty; found = Nil; repointed = 0
      return program

    val names = program.symbols.all.iterator.map(_.fullName).toSet
    report = PolicyReport(maps.flatMap { m =>
      val touches = m.entries.iterator.filter(_.upstream.nonEmpty).exists { e =>
        names(e.upstream) || names(PortMapTransform.bareKey(e.upstream))
      }
      if touches then Nil
      else List(PolicyFinding(name, "PortMapTransform.maps", s"${m.module} (${m.entries.size} entries)",
        PolicyIssue.NeverMatched,
        "not one of this map's upstream names occurs in this program, so nothing was re-pointed and " +
          "no dropped call could be reported. Either this is the wrong module's map, or it was " +
          "published before a namespace move — re-run the base port"))
    })

    val theirs = PortMapTransform.ownedByBase(program, typeEntries.keySet)
    found = scan(program, theirs)
    recordRepoints(program, theirs)
    val repointed0 = repoint(program)
    followMemberRenames(repointed0)

  /** decision provenance for the re-pointing below: one row per (declaration of THIS module,
    * re-pointed type). `RetypedSignature` — no call was re-targeted, only the declared type. Filtered
    * by [[PortMapTransform.ownedByBase]] for D2's reason. Read from the PRE-repoint program. */
  private def recordRepoints(program: Program, theirs: Set[SymId]): Unit =
    if renames.nonEmpty then
      val byName = program.symbols.all.iterator.map(s => s.fullName -> s).toMap
      renames.toList.sorted.foreach { (from, to) =>
        byName.get(from).foreach { sym =>
          val who = typeEntries.get(from).map(_._1).getOrElse("?")
          Decision.declarationsUsing(program, sym.id).filterNot((encl, _) => theirs(encl)).foreach { (encl, origin) =>
            record(Decision(
              kind       = Decision.Kind.RetypedSignature,
              subject    = encl,
              subjectFqn = Decision.fqnOf(program, encl, from),
              detail     = Map(
                "from" -> from,
                "to"   -> to,
                "key"  -> from,
                "base" -> who,
                "why"  -> ("the base module's PUBLISHED port map records this type emitted under " +
                  "that name; this module never restates the rename and cannot change it here"),
              ),
              reason = Reason.Configured(name, s"$from -> $to"),
              origin = origin,
            ))
          }
        }
      }

  /** Re-points every owned symbol under a name the base MOVED — mechanically identical to
    * [[PackageRenameTransform]] (longest match, cut at a separator, owned symbols only, §4.56); the
    * map comes from the base's published output rather than this module's own config. Idempotent: a
    * symbol already moved by `packageRenames` no longer matches an upstream prefix. */
  private def repoint(program: Program): Program =
    if renames.isEmpty then
      repointed = 0
      program
    else
      val owned = PackageRenameTransform.ownedSymbols(program)
      var moved = 0
      val table = program.symbols.all.foldLeft(program.symbols) { (t, s) =>
        if !owned(s.id) then t
        else
          PortManifest.longestPrefix(s.fullName, renames.keySet) match
            case scala.None => t
            case Some(from) =>
              val to      = renames(from)
              val newFull = to + s.fullName.substring(from.length)
              if newFull == s.fullName then t
              else
                moved += 1
                val newName = if s.fullName == from then PortMapTransform.simpleNameOf(to) else s.name
                t.updated(s.copy(name = newName, fullName = newFull))
      }
      repointed = moved
      // trees and the xref are keyed by `SymId`; renaming the symbol reaches every reference.
      program.rebuilt(symbols = table)

  /** Follows the base's member renames: for every base port-map member entry whose simple name
    * changed, finds the symbol by its UPSTREAM FQN and renames it to the EMITTED simple name — the
    * bean-property collapse and parenless-arity decisions the base already made (D14). Uses
    * `MemberRenamer` so the whole override component (and its call sites) moves too. */
  private def followMemberRenames(program: Program): Program =
    // types the base SUBSTITUTED emit members from the INJECTED file, which never renamed them —
    // renaming the dependent's references here would call a name that file does not have.
    val substitutedTypes: Set[String] = maps.flatMap(_.types).collect {
      case e if e.disposition == PortMap.Disposition.Substituted && e.upstream.nonEmpty => e.upstream
    }.toSet

    val memberRenameEntries = maps.flatMap(_.members).filter { e =>
      e.upstream.nonEmpty && e.emitted.nonEmpty && e.disposition == PortMap.Disposition.Renamed && {
        val ownerFqn = PortMapTransform.ownerOf(e.upstream)
        !substitutedTypes.contains(ownerFqn)
      } && {
        val upSimple   = PortMapTransform.simpleNameOf(PortMapTransform.bareKey(e.upstream))
        val emitSimple = PortMapTransform.simpleNameOf(PortMapTransform.bareKey(e.emitted))
        // a NAME change (bean pair rename) or a FORM change (parenless, collapsed)
        upSimple != emitSimple || e.memberShape.form.nonEmpty
      }
    }
    if memberRenameEntries.isEmpty then return program

    val graph = OverrideGraph.build(program)
    val byFullName = program.symbols.all.iterator.map(s => s.fullName -> s).toMap

    // find the symbol by its UPSTREAM FQN and request a rename to the emitted name.
    case class FollowEntry(sym: Symbol, newName: String, entry: PortMap.Entry)
    val followEntries = memberRenameEntries.flatMap { e =>
      val upBare   = PortMapTransform.bareKey(e.upstream)
      val emitBare = PortMapTransform.bareKey(e.emitted)
      val newName  = PortMapTransform.simpleNameOf(emitBare)
      // try the repointed (emitted-namespace) name first, then the upstream bare name — an owned
      // symbol is in the emitted namespace after `repoint`, an unowned base member is not.
      val inEmitNs = PackageRenameTransform.renamed(upBare, renames.toMap)
      val sym = byFullName.get(inEmitNs).orElse(byFullName.get(upBare))
      sym.filter(_.name != newName).map { s =>
        FollowEntry(s, newName, e)
      }
    }
    // fallback for a lazily-interned external symbol with a NUMERIC owner (unresolved type), whose
    // fullName cannot be found by `byFullName` — match unmatched entries by (owner, name) instead.
    val followEntriesWithFallback: List[FollowEntry] =
      if followEntries.size >= memberRenameEntries.size then followEntries
      else
        val matched = followEntries.map(_.sym.id).toSet
        val extra = collection.mutable.ListBuffer.empty[FollowEntry]
        val byName = program.symbols.all.groupBy(_.name)
        memberRenameEntries.foreach { e =>
          val upBare   = PortMapTransform.bareKey(e.upstream)
          val emitBare = PortMapTransform.bareKey(e.emitted)
          val newName  = PortMapTransform.simpleNameOf(emitBare)
          val upName   = PortMapTransform.simpleNameOf(upBare)
          val ownerFqn = PortMapTransform.ownerOf(upBare)
          val inEmitNs = PackageRenameTransform.renamed(upBare, renames.toMap)
          // skip entries already found by the fullName lookup
          if !byFullName.contains(inEmitNs) && !byFullName.contains(upBare) then
            val ownerEmit = PackageRenameTransform.renamed(ownerFqn, renames.toMap)
            byName.getOrElse(upName, Nil).foreach { s =>
              if !matched.contains(s.id) && s.name != newName then
                val ownerSym = program.symbolOf(s.owner)
                val ownerMatch = ownerSym.exists(o =>
                  o.fullName == ownerFqn || o.fullName == ownerEmit)
                if ownerMatch then
                  extra += FollowEntry(s, newName, e)
            }
        }
        followEntries ++ extra.toList

    if followEntriesWithFallback.isEmpty then return program

    // owned symbols go through MemberRenamer (full override-component handling); unowned base
    // members are renamed directly, since MemberRenamer refuses symbols this program declares.
    val (owned, unowned) = followEntriesWithFallback.partition(fe => program.owns(fe.sym.id))

    val direct = unowned.distinctBy(_.sym.id)
    val directTable = direct.foldLeft(program.symbols) { (t, fe) =>
      t.updated(fe.sym.copy(name = fe.newName))
    }
    val program1 = if direct.isEmpty then program else program.rebuilt(symbols = directTable)

    val requests = owned.map { fe =>
      MemberRenamer.Request(fe.sym.id, fe.newName,
        Reason.Configured(name, s"${fe.entry.upstream} -> ${fe.entry.emitted}"),
        fe.entry.upstream, fe.entry.upstream)
    }

    val program2 = if requests.isEmpty then program1
    else
      val graph1 = if direct.isEmpty then graph else OverrideGraph.build(program1)
      // a getter/setter pair may point to the same symbol
      val deduped = requests.distinctBy(_.member)
      val (r, _) = MemberRenamer.rename(program1, graph1, deduped,
        MemberRenamer.OnCollision.DeferToEmitter, decisions)
      r

    val renamed = program2

    // strip `()` for parenless members (MemberRenamer renames the name but not the arity),
    // expanded to the whole override component so a dependent's own override follows too.
    val graph2 = OverrideGraph.build(renamed)
    val parenlessRoots = memberRenameEntries.filter(_.memberShape.form == "parenless")
      .flatMap { e =>
        val upBare   = PortMapTransform.bareKey(e.upstream)
        val inEmitNs = PackageRenameTransform.renamed(upBare, renames.toMap)
        val emitBare = PortMapTransform.bareKey(e.emitted)
        renamed.symbols.all.iterator.find(s =>
          s.fullName == inEmitNs || s.fullName == emitBare || s.fullName == upBare).map(_.id)
      }.toSet
    val parenlessSyms = parenlessRoots.flatMap(r => graph2.closureOf(r).members)

    if parenlessSyms.isEmpty then renamed
    else
      given Program = renamed
      renamed.rebuilt(units = renamed.units.map { u =>
        StandardTraversal.mapClassDef(new Phase {
          def name = "port-map-follow-parenless"
          override def transformDefDef(t: Tree.DefDef)(using Program): Tree.DefDef =
            if parenlessSyms.contains(t.symbol) then t.copy(paramss = Nil) else t
          override def transformApply(t: Tree.Apply)(using Program): Term =
            if parenlessSyms.contains(t.method) && t.args.isEmpty then
              t.fun match
                case _: Tree.Ident            => Tree.Ident(t.method, t.tpe, t.origin)
                case Tree.Select(q, _, _, _)  => Tree.Select(q, t.method, t.tpe, t.origin)
                case _                        => t
            else t
        }, u)
      })

  // ---------------------------------------------------------------------------
  // reporting — what the dependent references that the base did not emit
  // ---------------------------------------------------------------------------

  /** Every reference this program makes that the maps have something to say about. Driven by the
    * `Xref` index (CLAUDE.md §3), not a private recursion. */
  private def scan(program: Program, theirs: Set[SymId]): List[PortMapTransform.Finding] =
    val out = collection.mutable.ListBuffer.empty[PortMapTransform.Finding]

    program.referenced.toList.flatMap(program.symbolOf).sortBy(_.fullName).foreach { sym =>
      val full = sym.fullName

      def sites(s: SymId) = PortMapTransform.callSites(program, s).filterNot((_, _, encl) => theirs(encl))

      // a reference to a type the base neither emits nor replaces
      typeEntries.get(full) match
        case Some((who, e)) if e.disposition == PortMap.Disposition.Dropped =>
          sites(sym.id).foreach { (origin, _, _) =>
            out += PortMapTransform.Finding(PortMapTransform.Issue.DroppedType, who, full,
              "the base's map records it Dropped: nothing is emitted at that name and nothing replaces it",
              origin)
          }
        case _ => ()

      // a call to a member the base dropped, or one whose body it hand-supplied
      if full.contains('#') then
        // the precise key, built from the callee symbol's own `info` (erased parameter simple
        // names); arity is the weaker fallback for a symbol whose `info` never resolved.
        val all   = memberEntries.getOrElse(full, Nil)
        val exact = PortMapTransform.preciseKey(program, sym).flatMap(k => all.find(_._2.upstream == k))
        val candidates = exact match
          case Some(c)    => List(c)
          case scala.None => all
        if candidates.nonEmpty then
          sites(sym.id).foreach { (origin, site, _) =>
            val arity = site match
              case Some(a: Tree.Apply) => Some(a.args.size)
              case _                   => scala.None
            val picked = if exact.isDefined then candidates else PortMapTransform.select(candidates, arity)
            val dispositions = picked.map((_, e) => (e.disposition, e.body)).distinct
            if dispositions.sizeIs > 1 then
              out += PortMapTransform.Finding(PortMapTransform.Issue.Ambiguous, picked.head._1, full,
                s"${picked.size} overload(s) in the base's map disagree (" +
                  picked.map((_, e) => s"${e.upstream}=${e.disposition}${if e.body then "+body" else ""}").sorted.mkString(", ") +
                  ") and this call site's arity does not separate them",
                origin)
            else
              picked.headOption.foreach { (who, e) =>
                if e.disposition == PortMap.Disposition.Dropped then
                  out += PortMapTransform.Finding(PortMapTransform.Issue.DroppedMember, who, e.upstream,
                    "the base's map records it Dropped: the base emits no such member, so this call " +
                      "resolves to nothing in the module it compiles against" +
                      PortMapTransform.refusalOf(e),
                    origin)
                else if e.body then
                  out += PortMapTransform.Finding(PortMapTransform.Issue.SubstitutedBody, who, e.upstream,
                    "the base emits this member with a HAND-SUPPLIED body: the signature is upstream's " +
                      "and the behaviour is not, which a call site cannot see any other way",
                    origin)
              }
          }
    }
    out.toList.distinct.sortBy(f => (f.issue.toString, f.symbol, f.origin.javaPath, f.origin.line))

object PortMapTransform:

  /** The phase configured from whatever those base modules last PUBLISHED:
    * {{{ surface = List(PortMapTransform.forBases("sge")) }}}
    * A base that published nothing reports so via [[PortMapTransform.policyReport]]. Freshness is
    * checked by `PortRun`, not here. Reads the filesystem at CONSTRUCTION, so
    * [[PortMapTransform.surfaceFingerprint]] depends on what is on disk — intended. */
  def forBases(modules: String*): PortMapTransform = forBasesIn(Nil, modules*)

  /** …with the port's own search path for the bases' report trees (`PortManifest.baseReports`,
    * D6.5). Pass `Nil` where all ports publish under one `port-report/`. */
  def forBasesIn(reports: List[java.nio.file.Path], modules: String*): PortMapTransform =
    new PortMapTransform(modules.toList.flatMap(PortMap.published(_, reports)))

  enum Issue:
    /** a call to a member the base's map records as `Dropped`. */
    case DroppedMember
    /** a reference to a type the base's map records as `Dropped` — not `Substituted`, which has a
      * replacement standing at the same name and is therefore callable. */
    case DroppedType
    /** a call into a member whose body the base replaced. */
    case SubstitutedBody
    /** overloads whose dispositions differ and which this call site's arity does not separate. */
    case Ambiguous

  /** @param base   the module that PUBLISHED the record (CLAUDE.md §4.45).
    * @param detail the base's own record, quoted rather than paraphrased. */
  final case class Finding(issue: Issue, base: String, symbol: String, detail: String, origin: Origin):
    def render: String = s"$issue: $symbol — $detail (base: $base)  (${origin.javaPath}:${origin.line})"
    def report: CheckReport.Finding =
      CheckReport.Finding("port-map", issue.toString, symbol,
        CheckReport.relativise(origin.javaPath), origin.line, s"$detail (base: $base)")

  /** The base's own record, where the drop was an ENGINE REFUSAL rather than a policy decision — a
    * different §1 kind (§4.45): (b) the dependent can ask for it back; (a) IN THE BASE, no manifest
    * key changes that. Empty for an ordinary policy drop. */
  private[transform] def refusalOf(e: PortMap.Entry): String =
    val r = e.memberShape.refusal
    if r.isEmpty then ""
    else s" — and NOT by policy: the base's ENGINE could not render it (`$r`), so no manifest key " +
      "here or there brings it back. §1(a) IN THE BASE: the fix is a hand-written replacement in " +
      "that module (§1.5's `inject`); in this one, stop calling it"

  /** Which of §1's three kinds each issue's fix is (CLAUDE.md §4.45). */
  def classification(issue: Issue): String = issue match
    case Issue.DroppedMember => "§1(b) PER-LIBRARY: the base module does not emit this member. Give this " +
      "module a replacement — `MethodBodyTransform` for a body, `StaticForwarderTransform` to re-point " +
      "it, or `dropMethods` if the member is itself only a forwarder to the dropped one."
    case Issue.DroppedType => "§1(b) PER-LIBRARY: the base module emits nothing at this name and injects " +
      "no replacement. Rewrite the references away in this module, or ship a replacement here — the " +
      "base deliberately does not have one."
    case Issue.SubstitutedBody => "§1(b) PER-LIBRARY, INFORMATIONAL: the behaviour behind this signature " +
      "is not upstream's. Nothing is broken; check that this module's use of it still holds."
    case Issue.Ambiguous => "§1(a) ENGINE or (b): the base's overloads disagree and arity cannot separate " +
      "them. If the call is genuinely to the dropped overload, say so with a precise `dropMethods` key " +
      "here; if this is an engine gap in overload identity, it belongs in ENGINE-LIMITS.md."

  /** Every place a member symbol is used, ONE ENTRY PER SITE, with the `Apply` that gives the site
    * its arity where there is one. The xref records `a.m(x)` twice (`Call` on `Apply`, `TermRef` on
    * `Select`); collapsing to (file, line, enclosing definition) and keeping the `Apply` gives one
    * finding per place an author has to edit, including two calls on one line. */
  def callSites(program: Program, s: SymId): List[(Origin, Option[Tree], SymId)] =
    program.usages(s)
      .groupBy(u => (u.site.origin.javaPath, u.site.origin.line, u.enclosing.raw))
      .toList.sortBy(_._1)
      .map { (k, us) =>
        val applied = us.collectFirst { case Usage(_, a: Tree.Apply, _) => a }
        (applied.map(_.origin).getOrElse(us.head.site.origin), applied, SymId(k._3))
      }

  /** Every symbol that belongs to a type the BASE published — a dependent's model contains the
    * base's Java too (D2), so reporting those would bury this module's own handful. Ownership is
    * decided structurally (§4.56), rooted on the types the map names, fuel-bounded so an
    * unresolvable case errs toward reporting. */
  def ownedByBase(program: Program, baseTypes: Set[String]): Set[SymId] =
    def theirs(s: SymId, fuel: Int): Boolean =
      s != SymId.None && fuel > 0 && program.symbolOf(s).exists { sym =>
        baseTypes(sym.fullName) || theirs(sym.owner, fuel - 1)
      }
    program.symbols.all.collect { case s if theirs(s.id, 64) => s.id }.toSet

  /** `owner#name(P1,P2)` for a callee, built from the SYMBOL's own `info` — the same key convention
    * `Substitutions.dropMethods`/`MethodBodyTransform`/the map use. `scala.None` when the symbol is
    * not a method or its `info` never resolved, rather than guessing a key. */
  def preciseKey(program: Program, sym: Symbol): Option[String] =
    def params(t: TypeRepr): Option[List[TypeRepr]] = t match
      case TypeRepr.MethodType(ps, _, _) => Some(ps.map(_._2))
      case TypeRepr.PolyType(_, r)       => params(r)
      case _                             => scala.None
    def simple(t: TypeRepr): Option[String] = t match
      case TypeRepr.TypeRef(_, s)                          => program.symbolOf(s).map(_.name)
      case TypeRepr.AppliedType(TypeRepr.TypeRef(_, s), _) => program.symbolOf(s).map(_.name)
      case _                                               => scala.None
    for
      ps   <- params(sym.info)
      // all parameters resolve, or none — a partial key would match the wrong overload.
      names <- ps.foldLeft(Option(List.empty[String]))((acc, p) => acc.zip(simple(p)).map(_ :+ _))
    yield s"${sym.fullName}(${names.mkString(",")})"

  /** `owner#name` out of `owner#name(P1,P2)` — the form a TIR symbol's `fullName` takes, since a
    * symbol carries no parameter list. */
  def bareKey(key: String): String =
    val i = key.indexOf('(')
    if i < 0 then key else key.substring(0, i)

  /** how many parameters a member key declares. The keys in a map are already ERASED
    * ([[PortMap.erase]]), so there are no nested generics to balance and a comma count is exact. */
  def arityOf(key: String): Option[Int] =
    val i = key.indexOf('(')
    if i < 0 then scala.None
    else
      val inner = key.substring(i + 1).stripSuffix(")")
      Some(if inner.isBlank then 0 else inner.count(_ == ',') + 1)

  /** Chooses the overloads a call site could mean — arity is the only discriminator, since the map
    * keys by erased parameter types and a TIR call site carries argument trees. Exact arity wins
    * outright; no arity match means NO record (a miss, not the nearest candidate) — the map may be
    * stale, or the member varargs. A key with no parameter list (a field) is kept regardless. */
  def select(candidates: List[(String, PortMap.Entry)], arity: Option[Int]): List[(String, PortMap.Entry)] =
    arity match
      case scala.None => candidates
      case Some(n)    => candidates.filter((_, e) => arityOf(e.upstream).forall(_ == n))

  /** the OWNER type of a member key: everything before `#`. `X.Y#m(P)` -> `X.Y`. */
  private[transform] def ownerOf(key: String): String =
    val i = key.indexOf('#')
    if i < 0 then key else key.substring(0, i)

  /** the last segment of a qualified name, at any of `.`, `$`, `#`. */
  private[transform] def simpleNameOf(q: String): String =
    val i = q.lastIndexWhere(PortManifest.isBoundary)
    if i < 0 then q else q.substring(i + 1)
