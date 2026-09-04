package balticporter.tir

/** Enforces marker conservation: an `Open` marker may be DISCHARGED by a phase, never erased.
  * Compares minted markers (before pipeline) against surviving ones (after), keyed on
  * [[Tree.Unportable.markerKey]]. A marker whose owning declaration was removed is legitimate;
  * one whose declaration survives but whose marker is gone is an engine defect.
  * // DESIGN.md §6.2, §6.5 */
object MarkerCheck:

  val Name = "markers"

  /** Sentinel prefix for an unresolvable variable reference, shared with `SpoonTir.resolveVar`. */
  val VarSentinelPrefix = "?var$"

  /** One marker finding: `open` (gates emission), `erased` (engine defect), or `sentinel`. */
  final case class Finding(kind: String, owner: String, detail: String, origin: Origin):
    def render: String = s"$kind: $owner — $detail  (${origin.javaPath}:${origin.line})"
    def report: CheckReport.Finding =
      CheckReport.Finding(Name, kind, owner, CheckReport.relativise(origin.javaPath), origin.line, detail)

  val Classification: String =
    "§1(a) ENGINE: a marker records a construct with no faithful Scala. An OPEN one blocks the " +
      "deliverable emission by design — close it in the engine, or drop the declaration that uses " +
      "it and inject a replacement. An ERASED one is a defect in the phase named beside it: it " +
      "deleted a marked subtree instead of discharging it, which removes the finding rather than " +
      "the problem. A SENTINEL is a name the frontend interned because it could not resolve one " +
      "and could not refuse either; it does NOT block the emission, and it is counted so that the " +
      "cost of converting those two mint sites is a number rather than a guess."

  /** A marker sited on the innermost declaration that contains it. */
  final case class Sited(marker: Tree.Unportable, owner: SymId, ownerFqn: String, unit: SymId)

  /** Every marker in `units`, sited on the innermost declaration that holds it.
    * Uses a claimed-identity set keyed on `markerKey` (not node identity, since traversal
    * rebuilds nodes). Cross-class dedup only; duplicates within one member are preserved. */
  def inventory(program: Program, units: List[Tree.ClassDef]): List[Sited] =
    given Program = program
    units.flatMap { cd =>
      def name(s: SymId) = program.symbolOf(s).map(_.fullName).getOrElse("?")
      def inMember(owner: SymId, ts: List[Tree]): List[Sited] =
        ts.flatMap {
          case t: Term => StandardTraversal.scanTerm(t, List.empty[Sited]) {
            case (acc, m: Tree.Unportable) => Sited(m, owner, name(owner), cd.symbol) :: acc
            case (acc, _)                  => acc
          }
          case _ => Nil
        }
      val claimed = collection.mutable.Set.empty[String]
      StandardTraversal.allClassDefs(cd).flatMap { c =>
        val rows = ownMembers(c).flatMap((owner, ts) => inMember(owner, ts))
        val kept = rows.filterNot(r => claimed(r.marker.markerKey))
        claimed ++= kept.map(_.marker.markerKey)
        kept
      }
    }

  /** Non-recursive member-level decomposition of one class: terms held by each declaration. */
  private def ownMembers(c: Tree.ClassDef): List[(SymId, List[Tree])] =
    c.body.flatMap {
      case d: Tree.DefDef   => List(d.symbol -> d.rhs.toList)
      case v: Tree.ValDef   => List(v.symbol -> v.rhs.toList)
      case _: Tree.ClassDef => Nil
      case s: Term          => List(c.symbol -> List(s))
      case _                => Nil
    } ++
    // enum constants are not in `body`; site markers on the constant itself
    c.enumCases.map(e => e.symbol -> (e.ctorArgs ++ e.body.collect { case t: Term => t }))

  /** Compare markers minted in `before` against survivors in `after`, scoped to `units` (D2). */
  def check(before: Program, after: Program, units: List[Tree.ClassDef]): List[Finding] =
    // matched on SymId, not fullName: pipeline renames move fullName but not the id
    val ownedIds  = units.map(_.symbol).toSet
    val mintedIn  = before.units.filter(u => ownedIds(u.symbol))
    val minted    = inventory(before, mintedIn)
    val surviving = inventory(after, units)
    val survivingKeys = surviving.map(_.marker.markerKey).toSet
    // read from TREES not symbols: a dropped member's symbol stays in the table
    val survivorIds = units.flatMap(u => StandardTraversal.allClassDefs(u)(using after))
      .flatMap(ownMembers).map(_._1).toSet

    val erased = minted.filterNot(s => survivingKeys(s.marker.markerKey))
      .filter(s => survivorIds(s.owner))
      .map(s => Finding("erased", s.ownerFqn,
        s"a ${s.marker.kind.label} marker was minted here and is gone from the final program, while " +
          s"the declaration survives — a phase deleted the marked subtree instead of discharging it " +
          s"(${s.marker.what})", s.marker.origin))

    val open = surviving.filter(_.marker.state.isOpen)
      .map(s => Finding("open", s.ownerFqn,
        s"${s.marker.kind.label}${s.marker.diff.fold("")(d => s" [$d]")} — ${s.marker.what}" +
          s"; ${s.marker.kind.remedies.headOption.map(_.render).getOrElse("no remedy is recorded for this kind")}",
        s.marker.origin))

    (open ++ erased ++ sentinels(after, units)).sortBy(f => (f.kind, f.owner, f.origin.line))

  /** Sentinel symbols: unresolvable type variables (`?T`) and variable references (`?var$name`)
    * interned by the frontend. Counted on the `markers` lane but NOT gated (unlike open markers).
    * Matched by exact equality against the mint site's construction, not by `?` prefix.
    * // ENGINE-LIMITS M6 */
  def sentinels(program: Program, units: List[Tree.ClassDef]): List[Finding] =
    val ownedRoots = units.map(_.symbol).toSet
    def rooted(s: SymId, fuel: Int): Boolean =
      s != SymId.None && fuel > 0 &&
        (ownedRoots(s) || program.symbolOf(s).exists(sym => rooted(sym.owner, fuel - 1)))
    def isTypeVarSentinel(sym: Symbol) = sym.fullName == Symbol.UnresolvedTypeVarPrefix + sym.name
    def isVarSentinel(sym: Symbol)     = sym.fullName == VarSentinelPrefix + sym.name
    program.symbols.all.toList
      .filter(sym => isTypeVarSentinel(sym) || isVarSentinel(sym))
      .sortBy(_.fullName)
      .flatMap { sym =>
        val here = program.usages(sym.id).filter(u => rooted(u.enclosing, 64))
        val what = if isVarSentinel(sym) then "variable reference" else "type variable"
        here.headOption.map { u =>
          Finding("sentinel", program.symbolOf(u.enclosing).map(_.fullName).getOrElse("?"),
            s"an unresolvable $what was interned as the sentinel `${sym.fullName}`, which must " +
              s"never be printed and whose only defence is an emitter rule that does not print it; " +
              s"${here.size} reference(s) in this module",
            u.site.origin)
        }
      }

  /** The open markers alone -- the emission gate's input. // DESIGN.md §6.4 */
  def openMarkers(program: Program, units: List[Tree.ClassDef]): List[Sited] =
    inventory(program, units).filter(_.marker.state.isOpen)

  def summary(findings: List[Finding], resolved: Int): String =
    val o    = findings.count(_.kind == "open")
    val e    = findings.count(_.kind == "erased")
    val sent = findings.count(_.kind == "sentinel")
    if o == 0 && e == 0 && sent == 0 && resolved == 0 then "  no construct was marked unportable"
    else
      val shown = findings.take(10).map("  " + _.render).mkString("\n")
      val more  = if findings.sizeIs > 10 then s"\n  … and ${findings.size - 10} more (findings.tsv)" else ""
      s"  open $o (the deliverable gate refuses these), erased $e (an engine defect), " +
        s"sentinel $sent (an unresolvable name interned rather than marked — counted, NOT gated), " +
        s"discharged $resolved" +
        (if findings.isEmpty then "" else "\n" + shown + more)
