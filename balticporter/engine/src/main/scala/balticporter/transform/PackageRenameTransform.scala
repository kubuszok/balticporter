package balticporter.transform

import balticporter.core.{PolicyFinding, PolicyIssue, PolicyReport, PolicySource, SurfacePolicy}
import balticporter.tir.*

/** Moves the port out of the upstream namespace: rewrites the package prefix of every symbol the
  * program itself declares. §1(b): mechanism (longest-prefix-wins, owned symbols only) is
  * universal; the maps (`renames`/`typeRenames`/`subPackages`/`flattenNestedTypes`, all UPSTREAM
  * namespace) are per-port policy. Renames SYMBOLS, not text, cut only at `.`/`$`/`#`. Runs LAST
  * (`runsAfter` can't say "after everything"); `check` verifies afterward. */
final class PackageRenameTransform(
    renames: Map[String, String] = Map.empty,
    /** upstream TYPE FQN → its name in the port: a dotted FQN, or a bare simple name to rename it
      * in place. Inherited by dependents (CLAUDE.md §1.5). */
    typeRenames: Map[String, String] = Map.empty,
    /** upstream TYPE FQN → a sub-package to nest it under, in place (`internal`, `impl.detail`). */
    subPackages: Map[String, String] = Map.empty,
    /** nested type FQNs (`p.Outer$Inner`) promoted to top level. */
    flattenNestedTypes: Set[String] = Set.empty,
    /** upstream TYPE FQNs whose boundary move the port declares deliberate. An entry that refuses
      * nothing is itself reported. */
    allowPackageSplit: Set[String] = Set.empty,
) extends Phase,
      PolicyBound,
      PolicySource,
      SurfacePolicy:
  import PackageRenameTransform.*

  def name: String = "package-rename"

  /** does this instance carry per-type policy at all? */
  private val perTypeDeclared: Boolean =
    typeRenames.nonEmpty || subPackages.nonEmpty || flattenNestedTypes.nonEmpty

  // ---- bound state (§8.1: a phase decides from BOUND symbols, never from a raw string) ----

  /** the accepted table: upstream prefix → emitted name. Per-type targets are already composed
    * through `renames`, so this is one longest-prefix map for rewrite, check and [[emittedName]]. */
  private var accepted: Map[String, String] = renames
  /** the per-type half of [[accepted]], distinguishing a `RenamedType` row from `RenamedPackage`. */
  private var acceptedTypes: Map[String, String] = Map.empty
  /** accepted `flattenNestedTypes` keys, by the SymId of the nested type to promote. */
  private var promote: Map[SymId, String] = Map.empty
  /** declarations whose access boundary a declared move widens — recorded by [[run]] so D3's
    * qualifier derivation reads it rather than re-deriving. DESIGN.md §8.7 */
  private var widenings: List[Widening] = Nil
  private var records: List[PolicyBinder.Record] = Nil
  private var extraFindings: List[PolicyFinding] = Nil
  private var didBind: Boolean = false

  /** a rename changes emitted signatures, so it is shared surface two modules must agree on. */
  def surfaceFingerprint: String =
    def m(label: String, kv: Map[String, String]) =
      if kv.isEmpty then "" else kv.toList.sorted.map((f, t) => s"$f->$t").mkString(s"$label:", ",", ";")
    m("pkg", renames) + m("type", typeRenames) + m("sub", subPackages) +
      (if flattenNestedTypes.isEmpty then "" else flattenNestedTypes.toList.sorted.mkString("flat:", ",", ";")) +
      (if allowPackageSplit.isEmpty then "" else allowPackageSplit.toList.sorted.mkString("split:", ",", ";"))

  /** declared per-type keys that named nothing, named only an external, or could never have been
    * carried out. Complete before the pipeline runs. */
  def policyReport: PolicyReport = PolicyReport.fromBindings(records) ++ PolicyReport(extraFindings)

  /** the accepted table, for layers that must name a type in both namespaces (§4.56). */
  def upstreamTable: Map[String, String] = accepted

  /** what `fqn` is emitted as under this instance's accepted policy. */
  def emittedName(fqn: String): String = renamed(fqn, accepted)

  /** the boundary moves this run recorded as deliberate, so a spec can assert it without a run dir. */
  def recordedWidenings: List[Widening] = widenings

  // -------------------------------------------------------------------------
  // BINDING — every per-type key becomes a SYMBOL before any phase runs (§8.1)
  // -------------------------------------------------------------------------

  /** Resolves every per-type key, refuses what cannot be carried out, and decides which boundary
    * moves this port has declared. Done here rather than in `run` so this phase's position cannot
    * change what its keys mean. */
  def bindPolicy(binder: PolicyBinder): Unit =
    didBind = true
    accepted = renames
    acceptedTypes = Map.empty
    promote = Map.empty
    widenings = Nil
    records = Nil
    extraFindings = Nil
    if !perTypeDeclared then
      if allowPackageSplit.nonEmpty then
        extraFindings = allowPackageSplit.toList.sorted.map(k =>
          PolicyFinding(name, AllowSetting, k, PolicyIssue.NeverMatched,
            "no per-type rename names this type, so there is no boundary move to declare"))
      return

    val program = binder.program
    // every key, with the map it came from; a key named by two maps is refused on both.
    val requested: List[Request] =
      typeRenames.toList.sorted.map((k, v) => Request(k, TypeSetting, Some(v))) ++
        subPackages.toList.sorted.map((k, v) => Request(k, SubSetting, Some(v))) ++
        flattenNestedTypes.toList.sorted.map(k => Request(k, FlatSetting, scala.None))
    val doubled = requested.groupBy(_.key).collect { case (k, rs) if rs.size > 1 => k }.toSet

    // stage 1: the key names a type this program declares.
    val bound: List[(Request, SymId)] = requested.flatMap { r =>
      binder.bindType(name, r.setting, r.key).toOption.map(r -> _)
    }
    records = binder.recordsFor(name)

    // stage 2: the request is one this phase can carry out at all.
    val shaped: List[Move] = bound.flatMap { (r, sid) =>
      val sym = program.symbolOf(sid)
      if doubled(r.key) then
        refuse(r, PolicyIssue.Malformed,
          "this type is named by more than one of `typeRenames`, `subPackages` and " +
            "`flattenNestedTypes`; one type, one destination")
      else
        target(r, sym) match
          case Left(why)  => refuse(r, PolicyIssue.Malformed, why)
          case Right(tgt) => Some(Move(r, sid, tgt, renamed(tgt, renames)))
    }

    // stage 3: the destination is free. A bound target FQN is a collision, not a hit: two types
    // at one emitted name compile as one file overwriting the other, silently.
    val typeNames: Map[String, String] =
      allClasses(program).flatMap(cd => program.symbolOf(cd.symbol)).map(s => s.fullName -> renamed(s.fullName, renames)).toMap
    val free: List[Move] = shaped.flatMap { mv =>
      val taken = typeNames.collectFirst { case (up, em) if up != mv.key && em == mv.emitted => up }
      val twin  = shaped.collectFirst { case o if o.key != mv.key && o.emitted == mv.emitted => o.key }
      (taken orElse twin) match
        case Some(other) =>
          refuse(mv.request, PolicyIssue.Malformed,
            s"""the destination "${mv.emitted}" is already the emitted name of `$other` — a rename """ +
              "target must be FREE, and a bound one is a collision two files would silently resolve " +
              "by overwriting each other")
        case scala.None => Some(mv)
    }

    // stage 4: the access boundary the move crosses. DESIGN.md §8.7's package-split
    val kept: List[Move] = free.flatMap { mv =>
      val b = boundaryOf(program, mv)
      if b.isEmpty then Some(mv)
      else if allowPackageSplit(mv.key) then
        widenings ++= b
        Some(mv)
      else
        refuse(mv.request, PolicyIssue.Unverifiable,
          s"${b.head.cause} — ${b.size} declaration(s) lose an access boundary Java gave them " +
            s"(${b.map(_.subjectFqn).sorted.take(4).mkString(", ")}${if b.size > 4 then ", …" else ""}). " +
            s"""Either leave the type where it is, or DECLARE the move with `allowPackageSplit += "${mv.key}"`, """ +
            "which records one widening per affected declaration instead of hiding it")
    }

    val unusedAllow = allowPackageSplit -- widenings.map(_.key).toSet
    if unusedAllow.nonEmpty then
      extraFindings ++= unusedAllow.toList.sorted.map(k =>
        PolicyFinding(name, AllowSetting, k, PolicyIssue.NeverMatched,
          "declared as a deliberate boundary move, and no accepted rename of this type moves one"))

    acceptedTypes = kept.map(mv => mv.key -> mv.emitted).toMap
    accepted = renames ++ acceptedTypes
    promote = kept.collect { case mv if mv.request.setting == FlatSetting => mv.sid -> mv.key }.toMap

  private def refuse(r: Request, issue: PolicyIssue, why: String): Option[Move] =
    extraFindings :+= PolicyFinding(name, r.setting, r.key, issue, why)
    scala.None

  /** The upstream-namespace destination a request names, or why it is not one this phase can
    * carry out. Shares `PortManifest.TypeMove`'s string half; adds the one judgement a manifest
    * cannot make since it needs a symbol: only a STATIC nested type can be promoted (an inner
    * class carries an implicit reference to its enclosing instance). */
  private def target(r: Request, sym: Option[Symbol]): Either[String, String] =
    import balticporter.core.PortManifest.TypeMove
    r.setting match
      case TypeSetting => TypeMove.renameTo(r.key, r.value.getOrElse(""))
      case SubSetting  => TypeMove.subPackage(r.key, r.value.getOrElse(""))
      case _ =>
        TypeMove.flatten(r.key).flatMap { t =>
          if sym.exists(_.flags.isStatic) then Right(t)
          else Left("only a STATIC nested type can be promoted: a Java inner class carries an " +
            "implicit reference to its enclosing instance, and a top-level type has nowhere to keep it")
        }

  // -------------------------------------------------------------------------
  // THE BOUNDARY RULE — DESIGN.md §8.7, and why M6 could not ship without it
  // -------------------------------------------------------------------------

  /** Which declarations a move puts on the wrong side of an access boundary Java gave them. A
    * per-type rename (or flatten) can split two types that shared a package or top-level enclosure
    * (JLS 6.6.1) that a whole-package rename never could. Reports one row per broken declaration,
    * for a refusal or a declared move's record. Java's package-private is unrepresented in this
    * TIR, so [[restricted]] sees only the `protected` half. Each entry judged alone. */
  private def boundaryOf(program: Program, mv: Move): List[Widening] =
    val solo   = renames + (mv.key -> mv.emitted)
    val wasKey = renamed(mv.key, renames)
    // read each declaration's OWN fullName, never its top-level enclosure's — a rule asking the
    // enclosure would miss exactly the boundary flattening breaks.
    def name(id: SymId): String    = program.symbolOf(id).map(_.fullName).getOrElse("")
    def now(id: SymId): String     = renamed(name(id), solo)
    def before(id: SymId): String  = renamed(name(id), renames)

    val movedPkg = packageOf(mv.emitted) != packageOf(wasKey)
    val movedTop = typeHeadOf(mv.emitted) != typeHeadOf(wasKey)
    if !movedPkg && !movedTop then Nil
    else
      val cause  = if movedPkg then Cause.PackageSplit else Cause.EnclosureSplit
      val inside = under(program, mv.sid)
      // both directions of one crossing: scan every restricted declaration, not just the moved
      // type's own. A pair is a crossing only if it shared a boundary BEFORE and does not after —
      // an ordinary top-level rename moves every member's head together and stays silent.
      val rows = program.symbols.all.toList.flatMap { m =>
        if !program.owns(m.id) || !restricted(m.flags, cause) then Nil
        else
          val declaredInside = inside(m.id)
          program.usages(m.id).map(_.enclosing).filter(_ != SymId.None).distinct.flatMap { user =>
            if declaredInside == inside(user) || name(user).isEmpty then Nil
            else
              val (was, is) =
                if cause == Cause.PackageSplit then
                  (reaches(before(m.id), before(user)), reaches(now(m.id), now(user)))
                else
                  (typeHeadOf(before(m.id)) == typeHeadOf(before(user)),
                   typeHeadOf(now(m.id)) == typeHeadOf(now(user)))
              if !(was && !is) then Nil
              else List(Widening(mv.key, cause, m.id, name(m.id),
                                 Decision.fqnOf(program, user, ""), Decision.originOf(program, m.id)))
          }
      }
      rows.distinctBy(w => (w.subject, w.readerFqn)).sortBy(w => (w.subjectFqn, w.readerFqn))

  /** Can a declaration emitted at `reader` still see a package-scoped member emitted at `decl`?
    * Not string equality: Scala's `private[p]` covers `p` and its subpackages, while Java's
    * package boundary is exact — so nesting under `p.internal` keeps `p`'s restricted members
    * reachable from it, and only the other direction is lost. Cut at a separator like every other
    * prefix question here. */
  private def reaches(decl: String, reader: String): Boolean =
    val (d, r) = (packageOf(decl), packageOf(reader))
    d == r || covers(r, d)

  /** which visibilities a given boundary move can strip. `private` is exact within a top-level
    * enclosure; `protected` carries Java's package half. Package-private is not representable
    * ([[boundaryOf]]). */
  private def restricted(f: Flags, cause: Cause): Boolean = cause match
    case Cause.PackageSplit   => f.isProtected
    case Cause.EnclosureSplit => f.isPrivate || f.isProtected

  // -------------------------------------------------------------------------
  // the rewrite
  // -------------------------------------------------------------------------

  override def run(program: Program): Program =
    require(didBind || !perTypeDeclared,
      "package-rename carries per-TYPE policy and was run WITHOUT being bound: `Pipeline.runTraced` " +
        "binds every `PolicyBound` phase before the first one runs, and a phase run unbound matches " +
        "nothing, silently (CLAUDE.md §1(b)).")
    if accepted.isEmpty then program
    else
      // flattening first: it changes the tree, not just a name, so everything after reads program.units.
      val hoisted = if promote.isEmpty then program else hoist(program)
      val owned   = PackageRenameTransform.ownedSymbols(hoisted)
      // prefixes the port demonstrably declares types under (covers at least one owned symbol) —
      // only these reach external symbols, so `Map("java" -> "jvm")` still cannot touch
      // java.lang.String, while a substituted type under a real port prefix moves with it.
      val portOwnedPrefixes: Set[String] =
        val ownedNames = hoisted.symbols.all.iterator.filter(s => owned(s.id)).map(_.fullName).toList
        renames.keySet.filter(p => ownedNames.exists(n => PackageRenameTransform.longestMatch(n, Set(p)).isDefined)) ++
          acceptedTypes.keySet
      val table = hoisted.symbols.all.foldLeft(hoisted.symbols) { (t, s) =>
        // owned, or merely under one of the renamed prefixes — a dropped type's injected
        // replacement is interned as external but lives in the library's own namespace, so its
        // references must move with the rename too (ENGINE-LIMITS: 8 errors without this).
        if !(owned(s.id) || PackageRenameTransform.longestMatch(s.fullName, portOwnedPrefixes).isDefined) then t
        else
          PackageRenameTransform.longestMatch(s.fullName, accepted.keySet) match
            case scala.None => t
            case Some(from) =>
              val to      = accepted(from)
              val newFull = to + s.fullName.substring(from.length)
              val newName = if s.fullName == from then PackageRenameTransform.simpleNameOf(to) else s.name
              t.updated(s.copy(name = newName, fullName = newFull))
      }
      recordMoves(hoisted, table)
      // trees and the xref are keyed by SymId and stay valid verbatim.
      hoisted.rebuilt(symbols = table)

  /** Promotes every accepted `flattenNestedTypes` entry to a top-level unit: the `ClassDef` leaves
    * its enclosing body, the symbol's owner becomes `SymId.None`, and the file header is carried
    * across — a promoted type becomes its own derived-work file. CLAUDE.md §4.58 */
  private def hoist(program: Program): Program =
    val ids = promote.keySet
    var out = List.empty[Tree.ClassDef]
    def strip(cd: Tree.ClassDef, header: List[Trivia]): Tree.ClassDef =
      cd.copy(body = cd.body.flatMap {
        case c: Tree.ClassDef if ids(c.symbol) =>
          out :+= strip(c, header).copy(unitLeading = header)
          Nil
        case c: Tree.ClassDef => List(strip(c, header))
        case s                => List(s)
      })
    val units   = program.units.map(u => strip(u, u.unitLeading))
    val symbols = ids.foldLeft(program.symbols)((t, id) =>
      program.symbolOf(id).fold(t)(s => t.updated(s.copy(owner = SymId.None))))
    program.rebuilt(units = units ++ out, symbols = symbols)

  /** One decision row per TYPE that moved, not per symbol — a member's namespace moves because
    * its type's did. `RenamedPackage`/`RenamedType` are separated by which map matched, never by
    * comparing the two strings. */
  private def recordMoves(program: Program, table: SymbolTable): Unit =
    val units = program.units.map(_.symbol).toSet
    allClasses(program).foreach { cd =>
      for
        was <- program.symbolOf(cd.symbol)
        now <- table.get(cd.symbol) if now.fullName != was.fullName
        from <- PackageRenameTransform.longestMatch(was.fullName, accepted.keySet)
      do
        // one row per declaration the policy entry NAMES, never per declaration it merely moved. §4.575
        val isType = acceptedTypes.contains(from) && was.fullName == from
        if isType || units(cd.symbol) then
          record(Decision(
            kind       = if isType then Decision.Kind.RenamedType else Decision.Kind.RenamedPackage,
            subject    = cd.symbol,
            subjectFqn = was.fullName, // the UPSTREAM name: the one every policy key is written in
            detail     = Map("from" -> was.fullName, "to" -> now.fullName) ++
              (if isType then Map("why" -> whyOf(from)) else Map.empty),
            reason = Reason.Configured(name, s"$from -> ${accepted(from)}"),
            origin = cd.origin,
          ))
    }
    // and the boundary the port declared it was moving — one row per affected declaration, which
    // §8.7's qualifier derivation reads instead of re-deriving from an upstream FQN.
    widenings.foreach { w =>
      record(Decision(
        kind       = Decision.Kind.WidenedVisibility,
        subject    = w.subject,
        subjectFqn = w.subjectFqn,
        detail = Map(
          "cause"  -> w.cause.slug,
          "type"   -> w.key,
          "reader" -> w.readerFqn,
          "why"    -> ("the port moves this type across an access boundary Java gave it, and " +
            "declared the move deliberate; this declaration therefore ships wider than Java wrote it"),
        ),
        reason = Reason.Configured(name, s"${w.key} -> ${accepted.getOrElse(w.key, w.key)}"),
        origin = w.origin,
      ))
    }

  private def whyOf(key: String): String =
    if flattenNestedTypes.contains(key) then
      "promoted out of its enclosing type: a consumer reads a nested type as a path-dependent one"
    else if subPackages.contains(key) then "nested into a sub-package the upstream did not have"
    else "renamed by policy: the upstream name is not one this port's consumers can use as it stands"

object PackageRenameTransform:

  private val TypeSetting  = "PackageRenameTransform(typeRenames)"
  private val SubSetting   = "PackageRenameTransform(subPackages)"
  private val FlatSetting  = "PackageRenameTransform(flattenNestedTypes)"
  private val AllowSetting = "PackageRenameTransform(allowPackageSplit)"

  /** what a move takes away: a package boundary (`protected[pkg]`/`private[pkg]`) or a top-level
    * enclosure (`private[TopLevel]`). */
  enum Cause(val slug: String):
    case PackageSplit extends Cause("package-split")
    case EnclosureSplit extends Cause("enclosure-split")
    override def toString: String = slug

  /** one declaration a declared move puts across a boundary Java gave it — the row §8.7's
    * qualifier derivation reads. */
  final case class Widening(
      /** the `typeRenames`/`subPackages`/`flattenNestedTypes` key that moved the boundary. */
      key: String,
      cause: Cause,
      subject: SymId,
      /** the restricted declaration, by its UPSTREAM name — policy's namespace (§4.56). */
      subjectFqn: String,
      /** the declaration on the OTHER side, which is what makes this a crossing rather than a move. */
      readerFqn: String,
      origin: Origin,
  )

  private final case class Request(key: String, setting: String, value: Option[String])
  /** an accepted request, with both namespaces of its destination: `upstreamTarget` is what the
    * policy author wrote, `emitted` is that name after the package renames. */
  private final case class Move(request: Request, sid: SymId, upstreamTarget: String, emitted: String):
    def key: String = request.key

  /** `.` separates packages and the top-level type, `$` precedes a nested type, `#` a member. */
  private def isBoundary(c: Char): Boolean = c == '.' || c == '$' || c == '#'

  /** the last segment of a qualified name, at any of the three separators. */
  private def simpleNameOf(q: String): String =
    val i = q.lastIndexWhere(isBoundary)
    if i < 0 then q else q.substring(i + 1)

  /** the package of a fully-qualified name, cut at `.` after the `$`/`#` tail is gone, so a
    * nested type answers with its package and not its enclosure. */
  private[transform] def packageOf(fqn: String): String =
    val head = typeHeadOf(fqn)
    val i    = head.lastIndexOf('.')
    if i < 0 then "" else head.substring(0, i)

  /** the top-level type of a fully-qualified name — the part a Java `private` reaches throughout
    * (JLS 6.6.1), which is the boundary flattening moves. */
  private[transform] def typeHeadOf(fqn: String): String =
    val i = fqn.indexWhere(c => c == '$' || c == '#')
    if i < 0 then fqn else fqn.substring(0, i)

  /** does `fullName` sit under `prefix`, cut at a separator? (`com.foo` covers `com.foo.Bar`,
    * never `com.foobar`.) */
  private def covers(fullName: String, prefix: String): Boolean =
    prefix.nonEmpty && fullName.startsWith(prefix) &&
      (fullName.length == prefix.length || isBoundary(fullName.charAt(prefix.length)))

  /** longest covering prefix, so a more specific entry wins; also orders a per-type key (always
    * longer) ahead of any package prefix covering it. */
  private[transform] def longestMatch(fullName: String, prefixes: Set[String]): Option[String] =
    prefixes.filter(covers(fullName, _)).maxByOption(_.length)

  /** every class the program declares, nested ones included — via `StandardTraversal.allClassDefs`,
    * not a `cd.body` recursion, which misses a method-local class (JLS 14.3). */
  private[transform] def allClasses(program: Program): List[Tree.ClassDef] =
    program.units.flatMap(u => StandardTraversal.allClassDefs(u)(using program))

  /** every symbol under `root` in the owner chain, `root` included. */
  private def under(program: Program, root: SymId): Set[SymId] =
    def rooted(s: SymId, fuel: Int): Boolean =
      s != SymId.None && fuel > 0 && (s == root || program.symbolOf(s).exists(x => rooted(x.owner, fuel - 1)))
    program.symbols.all.collect { case s if rooted(s.id, 64) => s.id }.toSet

  /** the name `fqn` ends up with under `renames` — exposed for code that must translate an
    * upstream name to its emitted one without holding a `Program`. CLAUDE.md §4.56 */
  def renamed(fqn: String, renames: Map[String, String]): String =
    longestMatch(fqn, renames.keySet) match
      case Some(from) => renames(from) + fqn.substring(from.length)
      case scala.None => fqn

  /** symbols the program declares, as opposed to externals the frontend interned on reference. */
  def ownedSymbols(program: Program): Set[SymId] = program.owned

  /** What a rename map does to a program. An unmatched prefix is always §1(b): the phase is
    * configured with a namespace the program does not contain. Run before the phase to see what
    * will move; run after with the same map and every prefix must come back unmatched.
    */
  final case class Report(matched: Map[String, Int], unmatched: List[String]):
    def render: String =
      val hits = matched.toList.sortBy(-_._2).map((p, n) => s"  $p -> $n owned symbol(s)")
      val miss =
        if unmatched.isEmpty then Nil
        else
          List("  unmatched prefixes (policy names a namespace this program does not declare — §1b, configure the phase):")
            ++ unmatched.sorted.map(p => s"    $p")
      (hits ++ miss).mkString("\n")

  def check(program: Program, renames: Map[String, String]): Report =
    val owned = ownedSymbols(program)
    val counts = program.symbols.all.toList
      .collect { case s if owned(s.id) => longestMatch(s.fullName, renames.keySet) }
      .flatten
      .groupBy(identity)
      .view
      .mapValues(_.size)
      .toMap
    Report(counts, (renames.keySet -- counts.keySet).toList)
