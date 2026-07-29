package balticporter.transform

import balticporter.core.{PolicyFinding, PolicyIssue, PolicyReport, PolicySource, PortMap, PortManifest, SurfacePolicy}
import balticporter.tir.*

/** Migrate a DEPENDENT's references from what its base module actually PUBLISHED.
  *
  * ==The gap this fills==
  * A dependent module parses the base's *Java* — it never sees the Scala the base emitted. So every
  * decision the base made about that surface has to reach the dependent somehow, and until now
  * there was exactly one route: RE-DERIVE it, by inheriting the base's `PortManifest` and re-running
  * its phases over the same Java. That answers "did we intend the same thing?" and it is blind to
  * three things by construction — a parameterised phase's configuration, a nested-type drop, and
  * anything at all about the base's OUTPUT.
  *
  * A published [[PortMap]] is the other route. It is the base's own record of what it produced, so
  * three questions a dependent could previously only answer *after* emitting become lookups it can
  * do *before* translating:
  *
  *   - '''a renamed type re-points.''' The base emitted `sge.ui.Widget` for upstream
  *     `com.badlogic.gdx.ui.Widget`; this phase moves the dependent's symbol to the name the base
  *     actually wrote, without the dependent restating the rename. A rename the base performed by
  *     any means — a package prefix, a type rename, a phase nobody wrote a rule for — is one entry
  *     in the map and reaches the dependent identically.
  *   - '''a call to a DROPPED member or type is reported, with the base's own record attached.''' The
  *     worked example: an entity-component library's `ImmutableArray.toArray(Class)` forwards to the
  *     collection library's `Array.toArray(Class)`, which the base drops because it is reflective
  *     and neither Scala.js nor Scala Native has reflection. Without a map that surfaces as
  *     `RewriteTrace`'s orphaned-call finding AFTER emission, saying only "a member with no
  *     declaration". With one it is reportable before emission and the message names the base
  *     module and its disposition — which is the difference between "something is missing" and
  *     "libgdx-core dropped this".
  *   - '''a call into a HAND-SUPPLIED body is reported.''' `MethodBodyTransform` replaces behaviour
  *     while leaving the signature exactly as upstream declared it, so a caller cannot see from the
  *     signature that it is not calling upstream's code. Nothing else in the pipeline can tell it.
  *
  * ==Kind==
  * CLAUDE.md §1(b). The MECHANISM — read a published map, re-point what it says was renamed, report
  * what it says is gone — is a fact about porting one module against another and is the same for
  * every library. WHICH map arrives as a constructor parameter. An empty list of maps is a total
  * no-op: nothing matches, nothing is renamed, nothing is reported, and the phase costs one
  * `isEmpty`.
  *
  * ==What it deliberately does NOT do==
  *   - '''It does not rewrite a dropped call into anything.''' There is no honest general rewrite
  *     for "the base does not have this member": the replacement is a per-library decision, and
  *     `MethodBodyTransform`, `StaticForwarderTransform` and `Substitutions.dropMethods` are the
  *     seams for making it. This phase makes the decision NECESSARY and VISIBLE, at the earliest
  *     point it can be, and stops there.
  *   - '''It does not consult a map for its own module.''' The caller supplies the maps; a module's
  *     own map is an OUTPUT and never an input to its own run (design risk R2). `PortRun` enforces
  *     the exclusion at discovery.
  *   - '''It does not verify freshness.''' Reading files to decide whether a map is still true of
  *     its base is [[PortMap.freshness]]'s job and the orchestrator's to call, because it needs the
  *     resolution roots. What this phase guarantees instead is that a map which matches NOTHING is
  *     REPORTED ([[policyReport]]) rather than being a silently inert phase — a wrong map and a
  *     stale map both land there.
  */
final class PortMapTransform(maps: List[PortMap.Map0] = Nil) extends Phase, PolicySource, SurfacePolicy:
  def name: String = "port-map-migration"

  /** Two modules handed different base maps do not agree about the surface they compile against, so
    * the map's IDENTITY is part of the fingerprint: which module published it, which engine, and the
    * source fingerprint that says which revision of that module. The entry count is included so two
    * maps of the same module from two different runs cannot compare equal on the header alone. */
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

  /** What the maps say about this program, in a stable order. Read by the orchestrator AFTER the
    * pipeline has run; empty before the first [[run]] and for an empty policy. */
  def findings: List[PortMapTransform.Finding] = found

  /** how many symbols this phase moved to the name the base actually emitted. */
  def renamedSymbols: Int = repointed

  /** A map that matched NOTHING, which is the only way this phase can be wrong without saying so.
    *
    * There is no per-entry `NeverMatched` here and there must not be: a base publishes tens of
    * thousands of entries and a dependent legitimately touches a few hundred of them, so "this entry
    * did not fire" is the normal case rather than a defect. What IS a defect is a map none of whose
    * entries names anything in this program — the wrong module's map, a map from a run whose
    * namespace has since moved, or a phase configured with a map it was never meant to have. That is
    * reported once per map, which is the granularity at which a reader can act on it. */
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

    found = scan(program)
    repoint(program)

  /** Re-point every owned symbol under a name the base MOVED.
    *
    * Mechanically identical to [[PackageRenameTransform]] — longest match, cut only at a separator,
    * owned symbols only — and for the same three reasons: `fullName` is one string with three
    * boundaries so `com.foo` must not cover `com.foobar`; the suffix is carried verbatim so nested
    * and member structure survives; and a prefix match on an unowned symbol would rewrite the
    * standard library (CLAUDE.md §4.56). What differs is only where the map comes from — the base's
    * published output rather than this module's own configuration.
    *
    * Idempotent by arithmetic: a symbol the dependent's own `packageRenames` already moved no longer
    * matches an upstream prefix, so a port that both inherits the rename map and reads the base's
    * map renames each symbol exactly once. */
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
      // Trees and the xref are keyed by `SymId` and stay valid verbatim — renaming the SYMBOL is
      // what makes a rename reach every reference, including ones no textual rewrite could find.
      new Program(program.units, table, program.xref)

  // ---------------------------------------------------------------------------
  // reporting — what the dependent references that the base did not emit
  // ---------------------------------------------------------------------------

  /** Every reference this program makes that the maps have something to say about.
    *
    * Driven by the [[XrefIndex]] the pipeline maintains, not by a private recursion (CLAUDE.md §3):
    * `program.referenced` is every symbol any tree names, in any position, and `program.usages`
    * locates each one. A hand-rolled walk is how two of this project's four silent defects got in. */
  private def scan(program: Program): List[PortMapTransform.Finding] =
    val out = collection.mutable.ListBuffer.empty[PortMapTransform.Finding]
    val theirs = PortMapTransform.ownedByBase(program, typeEntries.keySet)

    program.referenced.toList.flatMap(program.symbolOf).sortBy(_.fullName).foreach { sym =>
      val full = sym.fullName

      def sites(s: SymId) = PortMapTransform.callSites(program, s).filterNot((_, _, encl) => theirs(encl))

      // ---- a reference to a type the base neither emits nor replaces -----------------------
      typeEntries.get(full) match
        case Some((who, e)) if e.disposition == PortMap.Disposition.Dropped =>
          sites(sym.id).foreach { (origin, _, _) =>
            out += PortMapTransform.Finding(PortMapTransform.Issue.DroppedType, who, full,
              "the base's map records it Dropped: nothing is emitted at that name and nothing replaces it",
              origin)
          }
        case _ => ()

      // ---- a call to a member the base dropped, or one whose body it hand-supplied ---------
      if full.contains('#') then
        // The PRECISE key, built from the callee symbol's own `info`. A TIR symbol's `fullName` is
        // `X#m` for every overload, but its `info` is that overload's `MethodType` — so the erased
        // parameter simple names give exactly the key a manifest and a map are written in, and the
        // lookup stops being a guess. Arity is the fallback for a symbol whose `info` the frontend
        // never resolved; it is genuinely weaker, because a real library has same-arity overloads
        // with different dispositions (`toArray(ArraySupplier)` ported beside `toArray(Class)`
        // dropped) and arity cannot tell those apart at all.
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
                      "resolves to nothing in the module it compiles against",
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

  /** The phase configured from whatever those base modules last PUBLISHED, for a porting program's
    * manifest:
    * {{{ surface = List(PortMapTransform.forBases("libgdx-core")) }}}
    *
    * A base that has published nothing contributes nothing, and the phase says so through
    * [[PortMapTransform.policyReport]] rather than becoming a quiet no-op. FRESHNESS is not checked
    * here — that needs the run's resolution roots, so `PortRun` checks it for every declared base and
    * reports `BaseMapStale` / `BaseMapUnverified` on the same run.
    *
    * Note this reads the filesystem at CONSTRUCTION, which makes the phase's
    * [[PortMapTransform.surfaceFingerprint]] depend on what is on disk. That is intended: two
    * modules built against two different revisions of a base really do disagree about the surface,
    * and `ManifestAgreement` should say so. */
  def forBases(modules: String*): PortMapTransform =
    new PortMapTransform(modules.toList.flatMap(PortMap.published))

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

  /** @param base   the module that PUBLISHED the record — the whole point of the message. "A member
    *               with no declaration" is what the engine could say before; "libgdx-core dropped
    *               it" is what an agent in another repository can act on (CLAUDE.md §4.45).
    * @param detail the base's own record, quoted rather than paraphrased. */
  final case class Finding(issue: Issue, base: String, symbol: String, detail: String, origin: Origin):
    def render: String = s"$issue: $symbol — $detail (base: $base)  (${origin.javaPath}:${origin.line})"
    def report: CheckReport.Finding =
      CheckReport.Finding("port-map", issue.toString, symbol,
        CheckReport.relativise(origin.javaPath), origin.line, s"$detail (base: $base)")

  /** Which of §1's three kinds each issue's fix is. A finding whose reader cannot tell (a) from (b)
    * from (c) costs a full investigation, and this check's whole value is arriving EARLY — an early
    * finding nobody can act on is just an earlier dead end. */
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
    * its arity where there is one.
    *
    * The xref records `a.m(x)` twice — as a `Call` on the `Apply` and as a `TermRef` on the `Select`
    * inside it — which is right for an index and wrong for a report: it doubles every finding, and
    * the `Select` half carries no argument list, so it cannot pick an overload and lands on
    * `Ambiguous` for a call the `Apply` half resolves exactly. Collapsing to (file, line, enclosing
    * definition) and keeping the `Apply` gives one finding per place an author has to edit.
    *
    * Two calls to the same member on one line collapse to one finding. That is deliberate: the fix
    * is one edit either way, and the alternative — the column — differs between the `Apply` and the
    * `Select` of the very same call, which is the thing being collapsed. */
  def callSites(program: Program, s: SymId): List[(Origin, Option[Tree], SymId)] =
    program.usages(s)
      .groupBy(u => (u.site.origin.javaPath, u.site.origin.line, u.enclosing.raw))
      .toList.sortBy(_._1)
      .map { (k, us) =>
        val applied = us.collectFirst { case Usage(_, a: Tree.Apply, _) => a }
        (applied.map(_.origin).getOrElse(us.head.site.origin), applied, SymId(k._3))
      }

  /** Every symbol that belongs to a type the BASE published — i.e. is not this module's own code.
    *
    * A dependent's model contains the base's Java too: the base is a `resolutionRoot`, so it is
    * parsed, and every call the BASE makes into its own dropped members is in the program as well.
    * Reporting those tells the dependent's author about 240 sites in a module they do not own and
    * cannot fix, and buries the handful that are theirs. The base already reported its own.
    *
    * Ownership is decided the same way §4.56 decides it for a rename — structurally, by climbing the
    * `owner` chain — but the roots here are the TYPES THE MAP NAMES rather than the program's units,
    * because that is exactly the set of types the publishing module is answerable for. Fuel-bounded
    * for the same reason: a corrupt owner cycle must not hang the phase, and a symbol that exhausts
    * the fuel counts as this module's, so an unresolvable case ERRS TOWARD REPORTING. */
  def ownedByBase(program: Program, baseTypes: Set[String]): Set[SymId] =
    def theirs(s: SymId, fuel: Int): Boolean =
      s != SymId.None && fuel > 0 && program.symbolOf(s).exists { sym =>
        baseTypes(sym.fullName) || theirs(sym.owner, fuel - 1)
      }
    program.symbols.all.collect { case s if theirs(s.id, 64) => s.id }.toSet

  /** `owner#name(P1,P2)` for a callee, built from the SYMBOL's own `info`.
    *
    * This is the same key convention `Substitutions.dropMethods`, `MethodBodyTransform` and the map
    * all use — erased parameter type SIMPLE names, primitives as Java spells them — derived here
    * from a `MethodType` instead of from a `DefDef`, because a dependent calling into its base has
    * the callee's SYMBOL and, when the member was dropped, no declaration at all.
    *
    * `scala.None` when the symbol is not a method or its `info` was never resolved — which is a real
    * case in a lenient frontend, and the reason arity survives as a fallback rather than being
    * deleted. Guessing a key from an unresolved signature would put a precise-looking claim about
    * another module in the report, which is the one thing worse than an admitted gap. */
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
      // ALL of them, or none: a key with one parameter guessed is a key that matches the wrong
      // overload, and the map is consulted by exact string.
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

  /** Choose the overloads a call site could mean.
    *
    * Arity is the only discriminator available: the map keys members by erased parameter types and a
    * TIR call site carries argument TREES, so matching by type would mean re-deriving the base's
    * erasure from the dependent's model — which is precisely the re-derivation a published map
    * exists to stop doing.
    *
    * Two rules, both learned by getting the second one wrong. A TIR symbol's `fullName` carries no
    * parameter list, so `X#m` is the SAME string for every overload — and each overload is
    * nevertheless a distinct symbol with a distinct call site. Selecting on arity is therefore not
    * a refinement, it is the whole of overload identity here:
    *
    *   - '''Exact arity wins outright.''' `toArray()` and `toArray(Class)` are one bare key and two
    *     dispositions; without this the portable call is reported as the reflective one.
    *   - '''No arity match means NO RECORD, not the nearest one.''' Falling back to the whole
    *     candidate list attributes a 1-argument call to the map's 0-argument entry, which reported
    *     the base's decision about a member that was never called. A call whose arity matches no
    *     recorded overload is not evidence about any of them — the map may be stale, or the member
    *     may be varargs, for which arity is only a lower bound. That is a MISS, and a miss is the
    *     right side to err on for a message that names another module.
    *
    * A key with no parameter list at all is a FIELD, kept regardless: `arityOf` has nothing to
    * compare and a field reference is not an `Apply` anyway. */
  def select(candidates: List[(String, PortMap.Entry)], arity: Option[Int]): List[(String, PortMap.Entry)] =
    arity match
      case scala.None => candidates
      case Some(n)    => candidates.filter((_, e) => arityOf(e.upstream).forall(_ == n))

  /** the last segment of a qualified name, at any of `.`, `$`, `#`. */
  private[transform] def simpleNameOf(q: String): String =
    val i = q.lastIndexWhere(PortManifest.isBoundary)
    if i < 0 then q else q.substring(i + 1)
