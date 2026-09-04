package balticporter.tir

/** ONE LOCATION'S SELECTION — the port picked [[remedy]] at [[key]], and the run bound that key to
  * a declaration it owns. Granularity is PER DECLARATION (matching `Decision.declarationsUsing`),
  * so a selection BROADCASTS to every site of the finding kind inside that member. An ambiguous
  * OVERLOAD set is NOT broadcast — `PolicyBinder.bindMember` refuses a key naming two overloads. */
final case class Resolution(
    /** the manifest entry verbatim — the string an agent edits (`CLAUDE.md` §4.575). */
    declaredKey: String,
    /** the PARSE of [[declaredKey]], where there is one. Absent for a remedy whose
      * [[Remedy.Subject]] is a TYPE, because a type key is not a member key and inventing a
      * `MemberKey(fqn, "")` for it would be §4.6's fabricated fact — a value the reader cannot tell
      * from a real answer. [[declaredKey]] is what every consumer renders. */
    key: Option[MemberKey],
    /** the declaration the key bound to. `SymId.None` where the member was DROPPED before a symbol
      * was minted: the key fired against the index and there is nothing left to resolve at. */
    target: SymId,
    remedy: Remedy,
):
  def render: String = s"""$declaredKey = "${remedy.id}""""

object Resolution:

  /** The check an applied resolution files under, and the KIND it files as. `remediation` is
    * REUSED (already a `PortRun.RequiredChecks` member) rather than given its own lane, so a run
    * that stops recording resolutions fails the same way one that stops recording remediations
    * does. Lives here since `balticporter.api` cannot see the runner that builds the row. */
  val Check: String        = "remediation"
  val ResolvedKind: String = "resolved"

  /** …and the OTHER half of the same lane. A remedy that verified its precondition and DECLINED is
    * neither a no-op nor `NeverApplied` — it was consulted and answered "no, here is why", which
    * reported as silence would read as applied. CLAUDE.md §3's refusal-enumeration rule at a menu:
    * one row per declined site NAMING THE GUARD. Rides on `remediation` for `ResolvedKind`'s reason. */
  val RefusedKind: String  = "refused"

  /** the phase name every resolutions binding and finding is recorded under — one string, so the
    * binder's records and the policy report cannot disagree about which seam asked. */
  val Seam: String    = "resolutions"
  /** …and the knob, precisely enough to find it in a manifest. */
  val Setting: String = "PortManifest.resolutions"

/** A RESOLUTION THAT FIRED — the ledger row, and the two artifacts it becomes. Both produced from
  * ONE value on purpose (CLAUDE.md §5): a `remediation(resolved)` count means nothing unless the
  * drained lane fell by exactly that number, and a `decisions.tsv` row means nothing unless the
  * porter note says the same. Two derivations would be free to disagree. */
final case class AppliedResolution(
    resolution: Resolution,
    /** the declaration the remedy was applied AT, fully qualified — `owner#member`, the grammar
      * `IdiomCandidate.subject` and `decisions.tsv` already speak. */
    subjectFqn: String,
    subject: SymId,
    origin: Origin,
    /** what the remedy DID here, in one phrase. Free text; the machine-readable half is the
      * remedy's own id and lane. */
    what: String,
    /** HOW MANY ROWS THIS ONE APPLICATION TOOK OUT OF [[Remedy.lane]]. Usually 1 — a remedy whose
      * SUBJECT is a type (`Remedy.Subject.OwnedType`) drains every site inside it, so one row here
      * answers for many there. Carried on the value and stated in the finding's own text, per §5's
      * drain rule (`count(rows)` must not stand in for `sum(drained)`). */
    drained: Int = 1,
):
  def remedy: Remedy = resolution.remedy

  /** the `remediation(resolved)` row — see [[Resolution.Check]] for why it is not its own lane. */
  def finding: CheckReport.Finding =
    CheckReport.Finding(Resolution.Check, Resolution.ResolvedKind, subjectFqn,
      CheckReport.relativise(origin.javaPath), origin.line,
      s"${remedy.id} — $what  ·  selected at `${resolution.declaredKey}`, draining $drained row(s) " +
        s"from ${remedy.target}")

  /** …and the `decisions.tsv` row, which carries a PORTER NOTE to the emitted line.
    * `Reason.Configured(Resolution.Seam, declaredKey)`, never the remedy's own [[FixKind]] — the
    * reader's first question is which repository the fix lives in, and for a selection that is
    * always this port's manifest. Which repo owns the remedy's CODE travels in the detail. */
  def decision: Decision =
    Decision(Decision.Kind.SelectedRemedy, subject, subjectFqn,
      Map("remedy" -> remedy.id, "drains" -> remedy.target, "owner" -> remedy.fix.section,
          "why" -> what),
      Reason.Configured(Resolution.Seam, resolution.declaredKey), origin)

  def render: String = s"${remedy.id} at $subjectFqn: $what  (${origin.javaPath}:${origin.line})"

/** A RESOLUTION THAT WAS CONSULTED AND DECLINED — see [[Resolution.RefusedKind]]. Carries no
  * [[Decision]] on purpose: a refusal changed nothing, so a porter note above the member would
  * falsely say something happened there. Names the GUARD rather than just "no", since every guard
  * has a different next step. */
final case class RefusedResolution(
    resolution: Resolution,
    subjectFqn: String,
    origin: Origin,
    /** the guard that declined, as a short slug — what the population is grouped BY. */
    guard: String,
    /** why, in the reader's next action. */
    why: String,
):
  def remedy: Remedy = resolution.remedy

  def finding: CheckReport.Finding =
    CheckReport.Finding(Resolution.Check, Resolution.RefusedKind, subjectFqn,
      CheckReport.relativise(origin.javaPath), origin.line,
      s"${remedy.id} declined ($guard) — $why  ·  selected at `${resolution.declaredKey}`, " +
        s"which therefore did NOT drain ${remedy.target}")

  def render: String = s"${remedy.id} at $subjectFqn: DECLINED ($guard) — $why"

/** WHAT THE PORT SELECTED, BOUND — a value ONE TRANSLATION owns, never a process-global table
  * (§5.1: `Determinism.Full` translates twice, and a shared ledger would double every row).
  * A phase reaches it through the [[PolicyBinder]] it is already handed, never a new parameter.
  * Reports declared-beside-applied ([[troubles]]) since a bound, real key can still have its
  * finding never fire this run — a THIRD staleness state `PolicyBinder` alone cannot see. */
final class ResolutionPlan(val entries: List[ResolutionPlan.Entry]):

  private val log   = collection.mutable.ListBuffer.empty[AppliedResolution]
  private val fired = collection.mutable.Set.empty[String]
  private val declined = collection.mutable.ListBuffer.empty[RefusedResolution]

  /** entries by the declaration they bound to — the lookup a phase makes per site. Built once;
    * a key that did not bind has no target and is absent by construction. */
  private val byTarget: Map[SymId, List[ResolutionPlan.Entry]] =
    entries.filter(_.actionable).groupBy(_.target.getOrElse(SymId.None)) - SymId.None

  /** TWO ENTRIES BINDING ONE DECLARATION ON ONE LANE — the shape a flat `Map` cannot refuse (two
    * different keys, e.g. `Foo#bar` vs `Foo#bar(int)`, can name one member) and only the BINDING
    * can see. Grouped by declaration then narrowed by [[Remedy.overlaps]] — two lanes, or disjoint
    * kinds on one lane, may legitimately both hold selections. What remains a finding is a pair
    * answering the SAME row: `selected` takes the first match, the second reports `NeverApplied`. */
  private val conflicting: Map[String, List[ResolutionPlan.Entry]] =
    val live = entries.filter(e => e.actionable && e.target.isDefined)
    live.groupBy(_.target).values.flatMap { group =>
      group.flatMap { e =>
        val rivals = group.filter { o =>
          o.declared != e.declared &&
            e.resolution.exists(a => o.resolution.exists(b => a.remedy.overlaps(b.remedy)))
        }
        Option.when(rivals.nonEmpty)(e.declared -> (e :: rivals))
      }
    }.toMap

  def isEmpty: Boolean  = entries.isEmpty
  def nonEmpty: Boolean = entries.nonEmpty
  def size: Int         = entries.size

  /** WHAT DID THE PORT CHOOSE HERE? `target` is the DECLARATION the site sits in, never the site's
    * own symbol — that is [[Resolution]]'s granularity. `lane`/`kind` narrow to the one caller that
    * declared the entry (ids are globally unique). Matched via [[Remedy.answers]], never two
    * equality tests here, since a lane may split one site into several kinds. */
  def selected(target: SymId, lane: String, kind: String): Option[Resolution] =
    byTarget.getOrElse(target, Nil).iterator
      .flatMap(_.resolution)
      .find(_.remedy.answers(lane, kind))

  /** …and the record that it fired. Separate from [[selected]] because a caller may legitimately ask
    * and then refuse for a reason of its own, and a plan that counted the question as the answer
    * would report a resolution that never happened. */
  def record(a: AppliedResolution): Unit =
    fired += a.resolution.declaredKey
    log += a

  /** apply-and-record in one call, for the ordinary caller that has already decided. */
  def applied(r: Resolution, subjectFqn: String, subject: SymId, origin: Origin, what: String,
              drained: Int = 1): Unit =
    record(AppliedResolution(r, subjectFqn, subject, origin, what, drained))

  /** DRAIN a residue lane — the MOVE CLAUDE.md §5 requires (a row leaves the refusal lane and
    * arrives in `remediation(resolved)`), performed once per traversal rather than per check.
    * Returns findings NOT drained, in original order; drained ones are in this plan's ledger.
    * Use this for a remedy that only MOVES A ROW (no phase); use [[appliedAt]] for one that CHANGES
    * EMISSION (`Remedy.emissionAffecting`). Keyed on the CALLER'S OWN remedies, not the lane name. */
  def drain[F](remedies: List[Remedy], findings: List[F])(residue: F => ResolutionPlan.Residue): List[F] =
    if entries.isEmpty || remedies.isEmpty then findings
    else
      val ids = remedies.map(_.id).toSet
      findings.filter { f =>
        val r = residue(f)
        selectedAmong(r.at, ids, r.kind) match
          case Some(res) => applied(res, r.subject, r.at, r.origin, r.what); false
          case scala.None => true
      }

  /** the narrowing [[drain]] performs: a selection at this declaration whose remedy is one of MINE
    * and which answers this row's kind. Private, because "one of mine" is only meaningful to the
    * declarer — every other caller wants [[selected]] with a specific `Remedy`. */
  private def selectedAmong(target: SymId, ids: Set[String], kind: String): Option[Resolution] =
    byTarget.getOrElse(target, Nil).iterator
      .flatMap(_.resolution)
      .find(r => ids(r.remedy.id) && r.remedy.answers(r.remedy.lane, kind))

  /** DID THE PORT PICK **THIS** REMEDY HERE? — narrower than `selected(target, lane, kind)`: a
    * phase holding a specific `Remedy` wants to know if this declaration chose IT, and `(lane,
    * kind)` would match a SIBLING where a lane's remedies all declare `Remedy.AnyKind`. An id is
    * globally unique, so this cannot. */
  def selected(target: SymId, remedy: Remedy): Option[Resolution] =
    byTarget.getOrElse(target, Nil).iterator.flatMap(_.resolution).find(_.remedy.id == remedy.id)

  /** …and the DECLINE, which counts as having FIRED for exactly the reason `record` does: the
    * selection was consulted and answered, so reporting it as `NeverApplied` beside its own refusal
    * row would give one entry two contradictory diagnoses. */
  def refuse(r: Resolution, subjectFqn: String, origin: Origin, guard: String, why: String): Unit =
    fired += r.declaredKey
    declined += RefusedResolution(r, subjectFqn, origin, guard, why)

  def refusals: List[RefusedResolution] = declined.toList

  /** everything recorded, READ and never flushed — `PortRun.appliedRemedies`, [[decisions]] and
    * [[appliedAt]] all read this. No flushing variant: the buffer belongs to ONE `ResolutionPlan`
    * belonging to ONE translation, so it never outlives what it records. */
  def all: List[AppliedResolution] = log.toList

  /** DID A REMEDY ALREADY ANSWER THIS ROW? — asked by the check that mints the lane, about what the
    * phase RECORDED (never what the manifest declared) so a refused selection stays in the lane.
    * Matched at the SITE, not the declaration: a remedy may refuse at one site of a member and
    * apply at another, so a per-declaration test would let the lane fall by more than `resolved`
    * gained. `origin` is the same value the applier recorded — not two spellings of a position. */
  def appliedAt(subject: SymId, lane: String, kind: String, origin: Origin): Boolean =
    subject != SymId.None &&
      log.exists(a => a.subject == subject && a.origin == origin && a.remedy.answers(lane, kind))

  /** THE DECISION ROWS — one per (declaration, remedy), never one per SITE. A `remediation(resolved)`
    * finding is per-site (the drained lane must fall by exactly that many); a `Decision` is per
    * DECLARATION (CLAUDE.md §5.1) since it becomes a porter NOTE and one per site would print the
    * same note twice above one `val`. Both artifacts still come from one value and say one thing. */
  def decisions: List[Decision] =
    log.toList.map(_.decision).distinctBy(d => (d.kind, d.subject, d.subjectFqn, d.detail.get("remedy")))

  /** DECLARED BESIDE APPLIED — every entry that did not do what its author asked, classified.
    *
    * A `def` and not a `val`, because the last of the three answers is only knowable once the run is
    * over: an entry that bound and was never selected is inert, and nothing before the pipeline
    * finishes can say so. */
  def troubles: List[ResolutionPlan.Trouble] =
    entries.flatMap { e =>
      if e.unknown then
        Some(ResolutionPlan.Trouble(e.declared, e.id, ResolutionPlan.Issue.UnknownRemedy,
          s"'${e.id}' is not a remedy this engine knows. A remedy is DECLARED by the phase or check " +
            "that can carry it out, so an unknown id is either a typo or a phase whose library is " +
            "not on this run's classpath at all"))
      else if e.sourceAbsent then
        Some(ResolutionPlan.Trouble(e.declared, e.id, ResolutionPlan.Issue.SourceAbsent,
          s"'${e.id}' is declared by `${e.declaredBy}`, which is not in this run's pipeline — nothing " +
            "here can carry it out. Add that phase to this manifest's `surface`, or remove the entry"))
      else if conflicting.contains(e.declared) then
        val others = conflicting(e.declared).filterNot(_.declared == e.declared)
        Some(ResolutionPlan.Trouble(e.declared, e.id, ResolutionPlan.Issue.ConflictingSelection,
          s"this key and ${others.map(o => s"`${o.declared}` (\"${o.id}\")").mkString(", ")} bound to " +
            s"the SAME declaration and can answer the SAME ROW " +
            s"(${e.resolution.map(_.remedy.target).getOrElse("?")}). Two spellings of one member key " +
            "are legal — `Foo#bar` and `Foo#bar(int)` — and a flat map cannot refuse them, so only " +
            "the binding can see this: the first entry answers and the rest are inert, which would " +
            "otherwise be reported as `never applied` about a finding that DID fire. Two selections " +
            "at one member on DIFFERENT lanes, or on one lane with DISJOINT kinds, are fine and are " +
            "not reported; delete one of these, or narrow it to another overload"))
      else if e.target.isDefined && !fired.contains(e.declared) then
        Some(ResolutionPlan.Trouble(e.declared, e.id, ResolutionPlan.Issue.NeverApplied,
          s"the key names a declaration this run OWNS and '${e.id}' is live, but no " +
            s"${e.resolution.map(_.remedy.target).getOrElse("?")} finding occurred at it this run — " +
            "the selection is not wrong, it is inert. Either the site it was written for is gone, " +
            "something upstream in the pipeline already answered it, or the phase that carries the " +
            "remedy out was OMITTED FROM THIS RUN" + ResolutionPlan.skipNote))
      else scala.None
    }

  /** one line per entry, for stdout — declared beside what it did. */
  def render: String =
    if entries.isEmpty then "  none"
    else entries.map { e =>
      val did = if fired.contains(e.declared) then "applied" else "—"
      s"  ${e.declared} = \"${e.id}\"  $did"
    }.mkString("\n")

object ResolutionPlan:

  val empty: ResolutionPlan = new ResolutionPlan(Nil)

  /** WHAT A RESIDUE ROW MUST BE ABLE TO SAY for a remedy to drain it — [[ResolutionPlan.drain]]'s
    * one argument. A record, not curried functions (a positional lambda list swaps fields
    * silently): `kind` is the finding kind, `at` the DECLARATION the selection keys on (never the
    * site's own symbol), `subject`/`origin` feed the resolved/decisions rows, `what` is the note. */
  final case class Residue(kind: String, at: SymId, subject: String, origin: Origin, what: String)

  /** ONE declared entry, with everything the run learned about it before any phase ran.
    * @param declared the manifest key verbatim @param id the chosen remedy id @param resolution the
    *   bound selection, present iff the id names a live remedy AND the key bound to a declaration
    *   @param target the declaration the key bound to, if it bound @param unknown the id names no
    *   remedy shipped @param sourceAbsent the id's declarer is not in THIS run @param declaredBy who declares it */
  final case class Entry(
      declared: String,
      id: String,
      resolution: Option[Resolution],
      target: Option[SymId],
      unknown: Boolean = false,
      sourceAbsent: Boolean = false,
      declaredBy: String = "?",
  ):
    /** can a phase be handed this? Bound, live, and with a declaration to act at. */
    def actionable: Boolean = resolution.isDefined && !unknown && !sourceAbsent

  /** Why a declared selection did nothing. Four answers because there are four different next
    * actions, which is `PolicyBinder`'s own argument for its five: collapsed into one, "you typed
    * the id wrong", "you did not enable the phase", "the finding stopped occurring" and "another key
    * of yours already answered this" read identically and mean entirely different things. */
  enum Issue:
    case UnknownRemedy, SourceAbsent, NeverApplied
    /** two entries bound ONE declaration and answer ONE lane — see [[ResolutionPlan.conflicting]].
      * Kept apart from [[NeverApplied]] because the loser of such a pair would take that one, whose
      * sentence says the finding never fired: it fired, and the other key answered it. */
    case ConflictingSelection

  /** THE THIRD CAUSE OF `NeverApplied`, and the only one this value can OBSERVE rather than list.
    * `SourceAbsent` cannot answer for a remedy that IS in the manifest's `surface` and was then
    * skipped via `balticporter.skipPhases` — assembled inside `Pipeline.run`, after the vocabulary
    * (CLAUDE.md §4.6). Names WHAT is skipped so the reader gets a check, not a hypothesis. */
  private[tir] def skipNote: String =
    val skipped = DebugFlags.skipPhases
    if skipped.isEmpty then
      " — `balticporter.skipPhases` is not set in this run, so if the remedy's phase is in `surface` " +
        "it did run (CLAUDE.md §4.6)"
    else
      s" — THIS RUN SKIPS ${skipped.toList.sorted.mkString(", ")} (`balticporter.skipPhases`), which " +
        "is the first thing to clear before reading this row as a fact about the program " +
        "(`just debug-clear`, CLAUDE.md §4.6)"

  final case class Trouble(declared: String, id: String, issue: Issue, detail: String)

  /** BIND every declared selection, once, before the pipeline runs — like
    * `PortRun.bindDeclaredPolicy` binds drops, keys upstream since the rename runs LAST (§4.56).
    * `bindMember` (not `bindMembers`): a key naming two overloads is `Ambiguous` with both listed.
    * @param declared manifest resolutions @param vocabulary every remedy shipped @param active
    *   ids whose declaring source is in THIS run. */
  def of(
      declared: Map[String, String],
      vocabulary: RemedyVocabulary,
      active: Set[String],
      binder: PolicyBinder,
  ): ResolutionPlan =
    new ResolutionPlan(declared.toList.sortBy(_._1).map { (key, id) =>
      val remedy = vocabulary.get(id)
      // WHICH SEAM binds the key is the REMEDY's answer, not this function's — see [[Remedy.Subject]]
      // for the two menus that made it one. An UNKNOWN id has no answer, and takes the default: it
      // is about to be reported as a typo, and binding it through the commonest seam is what gives
      // that report a second, concrete sentence about the key itself.
      val subject = remedy.map(_.subject).getOrElse(Remedy.Subject.OwnedMember)
      val bound   =
        if subject.isType then
          binder.bindType(Resolution.Seam, Resolution.Setting, key, subject.ownership)
            .map(s => (scala.None: Option[MemberKey]) -> Some(s))
        else
          binder.bindMember(Resolution.Seam, Resolution.Setting, key, subject.ownership)
            .map(h => Some(h.key) -> h.sym)
      val hit    = bound.toOption
      val target = hit.flatMap(_._2)
      Entry(
        declared     = key,
        id           = id,
        resolution   = for
                         r <- remedy if active.contains(id)
                         h <- hit
                         t <- target
                       yield Resolution(key, h._1, t, r),
        target       = target,
        unknown      = remedy.isEmpty,
        sourceAbsent = remedy.isDefined && !active.contains(id),
        declaredBy   = vocabulary.sourceOf(id),
      )
    })
