package balticporter.tir

/** THE MARKER'S CONSERVATION LAW — a refusal may be DISCHARGED, never erased.
  *
  * `DESIGN.md` §6.2 makes discharge an explicit act (`Open → Resolved(byPhase, how)`) and §6.5
  * schedules this check to land WITH the marker, for the reason `CLAUDE.md` §3 gives about every
  * translation path: a mechanism whose failure nothing counts is a mechanism that fails silently.
  * The failure here is specific and invisible to every other number in the run —
  *
  *   a phase that DELETES a marked subtree has removed the finding, not fixed it. The emitted code
  *   compiles, the error count does not move, no member digest changes (the member was going to be
  *   rewritten anyway), and the port ships the construct the engine said it could not translate.
  *
  * A discharge and an erasure are the same size in the output and nothing like each other in a
  * port, which is exactly the distinction [[MarkerState]] exists to make and this check to enforce.
  *
  * ==Why an ERASURE can be told from a legitimate deletion, with no drop list==
  *
  * §6.5's own risk row asks for it: *conservation false-positives on legitimate deletions — inject
  * synthetic markers into members known to be dropped or replayed; each must report discharged, not
  * erased.* The answer needs no list, because the OWNER answers it. A marker is minted inside some
  * declaration; if that declaration is gone from the final program then so is everything in it, and
  * the marker went with the code rather than being taken out of it. If the declaration survives and
  * the marker does not, a phase reached in and removed it. The two cases are distinguished by a
  * lookup the check already has to do, and a hand-maintained exemption list is exactly the artifact
  * `CLAUDE.md` §5.1 says rots into "we always ignore those four".
  *
  * ==Keying==
  *
  * On [[Tree.Unportable.markerKey]] — origin plus the mint site's own words — because trees have no
  * identity: the standard traversal rebuilds every node on every phase, which is §6.2's own reason
  * for rejecting a side table. That key is only distinguishing because `Tree.unportable` refuses a
  * synthetic origin; `<synthetic>:0:0` would collapse every marker in the program onto one key and
  * this check would then report nothing, confidently.
  */
object MarkerCheck:

  val Name = "markers"

  /** the key `SpoonTir.resolveVar` mints for a variable reference it cannot resolve. Here rather
    * than in the frontend beside its mint site, because this is the READING half and the two must
    * agree — the same reason `Symbol.UnresolvedTypeVarPrefix` lives in `api` rather than in the arm
    * that writes it. */
  val VarSentinelPrefix = "?var$"

  /** what a run does about each: an `Open` marker is the EMISSION GATE's input (§6.4), an erased
    * one is an engine defect, and a resolved one is work done and is reported as a count only. */
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

  /** one marker, with the declaration it was minted inside. */
  final case class Sited(marker: Tree.Unportable, owner: SymId, ownerFqn: String, unit: SymId)

  /** every marker in `units`, sited on the declaration that holds it. */
  def inventory(program: Program, units: List[Tree.ClassDef]): List[Sited] =
    given Program = program
    units.flatMap { cd =>
      def name(s: SymId) = program.symbolOf(s).map(_.fullName).getOrElse("?")
      // sited on the MEMBER, not on the unit: a marker's home is the declaration a reader would
      // open, and the source map keys members the same way (`DESIGN.md` §6.3).
      def inMember(owner: SymId, ts: List[Tree]): List[Sited] =
        ts.flatMap {
          case t: Term => StandardTraversal.scanTerm(t, List.empty[Sited]) {
            case (acc, m: Tree.Unportable) => Sited(m, owner, name(owner), cd.symbol) :: acc
            case (acc, _)                  => acc
          }
          case _ => Nil
        }
      members(cd).flatMap((owner, ts) => inMember(owner, ts))
    }

  /** the member-level decomposition of a unit: every declaration that can hold a term, with the
    * terms it holds. A nested class is recursed into, so a marker inside one is sited on ITS
    * member rather than on the outer type. */
  private def members(cd: Tree.ClassDef): List[(SymId, List[Tree])] =
    def go(c: Tree.ClassDef): List[(SymId, List[Tree])] =
      c.body.flatMap {
        case d: Tree.DefDef   => List(d.symbol -> d.rhs.toList)
        case v: Tree.ValDef   => List(v.symbol -> v.rhs.toList)
        case n: Tree.ClassDef => go(n)
        case s: Term          => List(c.symbol -> List(s))
        case _                => Nil
      } ++
      // an enum CONSTANT is not in `body` — it is a field of its own, and a walk over the body
      // alone reaches neither its constructor arguments nor its per-constant overrides. Sited on
      // the CONSTANT, because that is the declaration a reader would open.
      c.enumCases.flatMap { e =>
        List(e.symbol -> (e.ctorArgs ++ e.body.collect { case t: Term => t })) ++
          e.body.collect { case n: Tree.ClassDef => go(n) }.flatten
      }
    go(cd)

  /** THE CHECK. `before` is the frontend's own output and `after` the program the pipeline
    * produced; `units` restricts both to what this run OWNS (`ENGINE-LIMITS.md` D2 — a dependent's
    * phases decide about its base's units too, and reporting those attributes another module's
    * findings to this one). */
  def check(before: Program, after: Program, units: List[Tree.ClassDef]): List[Finding] =
    // MATCHED ON `SymId`, NEVER ON `fullName`. `before` is the frontend's output and `after` is the
    // program a pipeline ending in `PackageRenameTransform` produced, so the two hold the SAME
    // declarations under DIFFERENT names — the upstream ones and the emitted ones (`CLAUDE.md`
    // §4.56: any artifact joining two namespaces has to say which is which). Compared by name, the
    // owned-unit filter matches nothing on every renaming port, `minted` is empty, and this check
    // reports a confident zero for the life of that port. A unit's symbol id is what survives the
    // pipeline unchanged — it is the same fact `PortRun.runScope` relies on — and a rename moves
    // `fullName` and never the id.
    val ownedIds  = units.map(_.symbol).toSet
    val mintedIn  = before.units.filter(u => ownedIds(u.symbol))
    val minted    = inventory(before, mintedIn)
    val surviving = inventory(after, units)
    val survivingKeys = surviving.map(_.marker.markerKey).toSet
    // a declaration that is GONE took its markers with it; one that survives and lost a marker did
    // not.
    //
    // Read from the TREES and never from the symbol table: a phase that drops a member removes the
    // `DefDef` and leaves the `Symbol` behind — nothing prunes the table, and it would be wrong to,
    // since every reference to the dropped member still has to resolve. Asked of the table, this
    // check answers "the declaration survives" for a member that is gone, and then reports the
    // legitimate deletion §6.5's risk row is about as an engine defect. That is the false positive
    // that would have made the whole lane un-baselineable.
    //
    // By id, for the same two-namespace reason as the unit filter above.
    val survivorIds = units.flatMap(members).map(_._1).toSet

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

  /** SENTINEL SYMBOLS — the marker's shape from before there was a marker, and the ONE thing on
    * `DESIGN.md` §6.5's mint list that could not simply be converted.
    *
    * Two sites mint one. `SpoonTir.tpe` mints `?T` for a type variable it cannot resolve and
    * `SpoonTir.resolveVar` mints `?var$name` for a variable reference it cannot resolve; each is a
    * name that MUST NEVER BE PRINTED, and each one's only defence is an emitter rule that does not
    * print it (`TirEmitter.isUnresolvedTypeVar`). That is a degradation with no counter attached:
    * it moves no error count, produces no finding, and the emitted file is valid Scala that has
    * silently lost a type argument or a reference.
    *
    * WHY THESE ARE COUNTED AND NOT MARKED. Minting a `Tree.Unportable` at those two sites would be
    * the obvious conversion and it is the wrong one, for a reason the gate makes concrete: an open
    * marker refuses the emission, so a port that mints even one sentinel would stop BUILDING.
    * Whether any port does was, until this lane existed, unknown — nothing in the engine could say
    * — and converting a site whose live frequency nobody has measured is how a mechanism takes a
    * whole corpus down. So the number comes first, and the conversion is a decision somebody takes
    * ON it. `ENGINE-LIMITS.md` M6's discipline, applied to the engine's own refusals rather than to
    * a library's constructs.
    *
    * Reported as `sentinel` findings on the `markers` lane rather than as their own check, because
    * they are the same fact at a different level of the tree — §6.2's rejected "symbol tags only"
    * alternative, observed rather than designed — and a reader asking "what did this port refuse"
    * should not have to know there are two places to look. They do NOT feed the gate.
    *
    * Attributed through the XREF, not through the symbol: an external symbol has no origin worth
    * printing, and the question a reader has is which of THEIR declarations reaches it.
    *
    * ==THE FILTER IS EXACT, AND THE FIRST VERSION OF IT WAS NOT==
    *
    * `Symbol.isUnresolvedTypeVar` is `fullName.startsWith("?")`, and that is not the question. The
    * frontend mints a `?` prefix for TWO unrelated reasons: this sentinel, and — incidentally —
    * `Minter.fullNameOf`'s fallback for a member whose OWNER it could not name, which produces
    * `?#actual`, `?#points`, `?#stride`. Those are ordinary method PARAMETERS. Asked the prefix
    * question, this lane reported **10,417 on libGDX core and 29 on its test set**, none of them a
    * sentinel — `CLAUDE.md` §4.56's own rule ("a prefix is not a structural fact about anything")
    * reproduced inside the check written to measure a different one.
    *
    * So the test is EQUALITY against the mint site's own construction: `?` + the symbol's simple
    * name, and `?var$` + the symbol's simple name. `?#actual` has the name `actual` and would have
    * to be `?actual` to match. That is as structural as this can be made — the sentinel's identity
    * IS its key, and the key is what the mint site built.
    *
    * The over-broad predicate is left where it is on purpose: it is the EMITTER's, it is load-bearing
    * there, and narrowing it is a change to emitted text that belongs in its own measured commit. */
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

  /** the OPEN markers alone — the emission gate's input (`DESIGN.md` §6.4). */
  def openMarkers(program: Program, units: List[Tree.ClassDef]): List[Sited] =
    inventory(program, units).filter(_.marker.state.isOpen)

  def summary(findings: List[Finding], resolved: Int): String =
    val o    = findings.count(_.kind == "open")
    val e    = findings.count(_.kind == "erased")
    val sent = findings.count(_.kind == "sentinel")
    if o == 0 && e == 0 && sent == 0 && resolved == 0 then "  no construct was marked unportable"
    else
      // at most ten rendered: a sentinel lane on a large port is a LIST, and a check summary
      // that scrolls is one nobody reads. The complete set is in `findings.tsv`, which is what
      // the baseline diffs and what an agent greps — the split every other lane here uses.
      val shown = findings.take(10).map("  " + _.render).mkString("\n")
      val more  = if findings.sizeIs > 10 then s"\n  … and ${findings.size - 10} more (findings.tsv)" else ""
      s"  open $o (the deliverable gate refuses these), erased $e (an engine defect), " +
        s"sentinel $sent (an unresolvable name interned rather than marked — counted, NOT gated), " +
        s"discharged $resolved" +
        (if findings.isEmpty then "" else "\n" + shown + more)
