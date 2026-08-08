package balticporter.tir

/** ONE LOCATION'S SELECTION — the port picked [[remedy]] at [[key]], and the run BOUND that key to a
  * declaration it owns.
  *
  * The key is a [[MemberKey]] — or, where the remedy says so ([[Remedy.Subject]]), a bare type FQN —
  * and the granularity is therefore PER DECLARATION, which is the compromise
  * the whole engine already made: [[Decision.declarationsUsing]] records one row per (declaration,
  * kind) and never one per expression, `IdiomCandidate.subject` is `owner#member` and never the
  * site's own expression, and `Remediator.Suggestion.subject` is a declared FQN. A finer key would
  * have to be positional, and a positional policy key is the fragility `CheckReport.Finding` already
  * refuses (its id deliberately excludes the line number so an upstream whitespace edit does not
  * orphan a baseline entry).
  *
  * '''So a selection BROADCASTS.''' Where one member holds two sites of the targeted finding kind,
  * the selection applies to both. That is a decision and not an oversight: the alternative is an
  * occurrence index, which reintroduces line-number-shaped fragility one level down. A member that
  * genuinely wants two different remedies at two sites is a feature nobody has needed yet, and it
  * will be an EXPLICIT one when somebody does. What is NOT broadcast is an ambiguous OVERLOAD set —
  * `PolicyBinder.bindMember` refuses a key naming two overloads and lists them, because those are
  * two different members and picking one by position is exactly what this grammar exists to stop.
  */
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

  /** The check an applied resolution files under, and the KIND it files as.
    *
    * `remediation` is REUSED rather than given a lane of its own, and that is the point: it is
    * already a `PortRun.RequiredChecks` member, so a run that stopped recording resolutions fails
    * the way a run that stopped recording remediations does. A new top-level check would have had to
    * be added to that set by somebody remembering to.
    *
    * The strings live here rather than in the orchestrator because [[AppliedResolution.finding]]
    * builds the row and `balticporter.api` cannot see the runner; `PortRun.Remediation` reads this
    * one, so there is exactly one spelling.
    */
  val Check: String        = "remediation"
  val ResolvedKind: String = "resolved"

  /** …and the OTHER half of the same lane. A remedy that verified its own precondition and DECLINED
    * is not a resolution that did nothing, and it is not `NeverApplied` either: the selection was
    * consulted, the engine answered, and the answer is *no, and here is why*. Reported as silence it
    * would read as applied — the port's key is there, the lane did not move, and no compile error,
    * moved digest or drained row says so.
    *
    * `CLAUDE.md` §3's refusal-enumeration rule read at a menu: the refusal POPULATION is a lane, one
    * row per declined site NAMING THE GUARD, because a count of applications says nothing about what
    * was declined and `refused = 0` is a bar met by applying nothing. It rides on `remediation` for
    * `ResolvedKind`'s own reason — one required check, two kinds. */
  val RefusedKind: String  = "refused"

  /** the phase name every resolutions binding and finding is recorded under — one string, so the
    * binder's records and the policy report cannot disagree about which seam asked. */
  val Seam: String    = "resolutions"
  /** …and the knob, precisely enough to find it in a manifest. */
  val Setting: String = "PortManifest.resolutions"

/** A RESOLUTION THAT FIRED — the ledger row, and the two artifacts it becomes.
  *
  * Both are produced from ONE value on purpose. `CLAUDE.md` §5's trivia-family rule read one artifact
  * over says a bar met by doing nothing is not a bar: a `remediation(resolved)` count says nothing
  * unless the refusal lane it drained fell by exactly that number, and a `decisions.tsv` row says
  * nothing unless the porter note beside the code says the same thing. Two derivations of "what did
  * this resolution do" would be free to disagree, so there is one.
  */
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
    /** HOW MANY ROWS THIS ONE APPLICATION TOOK OUT OF [[Remedy.lane]].
      *
      * One, and that is the whole of [[ResolutionPlan.drain]]'s world: it partitions a lane SITE by
      * site, so `count(rows)` and `sum(drained)` are the same number and no caller has to think about
      * it. The exception is a remedy whose SUBJECT is a type (`Remedy.Subject.OwnedType`): dropping
      * one takes every site inside it, so one row here answers for fifteen there and a diff read by
      * counting rows leaves fourteen with nothing to attribute them to — exactly what §5's drain rule
      * refuses. Carried on the value, and stated in the finding's own text so a reader of one row can
      * do the arithmetic. */
    drained: Int = 1,
):
  def remedy: Remedy = resolution.remedy

  /** the `remediation(resolved)` row — see [[Resolution.Check]] for why it is not its own lane. */
  def finding: CheckReport.Finding =
    CheckReport.Finding(Resolution.Check, Resolution.ResolvedKind, subjectFqn,
      CheckReport.relativise(origin.javaPath), origin.line,
      s"${remedy.id} — $what  ·  selected at `${resolution.declaredKey}`, draining $drained row(s) " +
        s"from ${remedy.target}")

  /** …and the `decisions.tsv` row, which is what carries a PORTER NOTE to the emitted line.
    *
    * `Reason.Configured(Resolution.Seam, declaredKey)` and never the remedy's own [[FixKind]]: the
    * reader's first question is which repository the fix lives in, and for a SELECTION the answer is
    * always this port's manifest — the key is right there to edit. Which repository owns the remedy's
    * CODE is a different question and travels in the detail. */
  def decision: Decision =
    Decision(Decision.Kind.SelectedRemedy, subject, subjectFqn,
      Map("remedy" -> remedy.id, "drains" -> remedy.target, "owner" -> remedy.fix.section,
          "why" -> what),
      Reason.Configured(Resolution.Seam, resolution.declaredKey), origin)

  def render: String = s"${remedy.id} at $subjectFqn: $what  (${origin.javaPath}:${origin.line})"

/** A RESOLUTION THAT WAS CONSULTED AND DECLINED — see [[Resolution.RefusedKind]].
  *
  * It carries no [[Decision]] on purpose, and that is the two-deadline rule read from its other end: a
  * `remediation(resolved)` row may be recorded after the emitter has run, a `Decision` may not,
  * because a porter note is EMITTED TEXT. A refusal changed nothing, so a note above the member would
  * tell its reader that something happened there. The finding is the whole record.
  *
  * It names the GUARD rather than merely reporting failure, which is what lets its reader act: every
  * guard has a different next step, and a decline that said only "no" would send its author looking
  * for a typo in a key that bound perfectly.
  */
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

/** WHAT THE PORT SELECTED, BOUND — a value ONE TRANSLATION owns.
  *
  * Never a process-global table, for the reason `TirEmitter.srcMap`, [[DecisionLog]], [[RewriteLog]]
  * and [[IdiomLog]] are values one run owns (`CLAUDE.md` §5.1): `Determinism.Full` translates twice,
  * and two translations sharing a ledger would double every row in it.
  *
  * ==How a phase reaches it==
  * Through the [[PolicyBinder]] it is already handed ([[PolicyBinder.resolutions]]), and not through
  * a new pipeline parameter or a second trait beside [[PolicyBound]]. The binder is documented as
  * "the only place a phase is allowed to learn what a key names", and a selection is exactly that: a
  * key, bound, with the port's answer attached. A phase that wants one already implements
  * `PolicyBound` to bind its own keys, so the seam costs no wiring anybody can forget.
  *
  * ==The THIRD staleness state, which is what this type exists to make sayable==
  * `PolicyBinder` answers one question — did this key name a real declaration? — and its refusals
  * (`NeverMatched`, `Ambiguous`, `Malformed`, …) are complete for that question. A selection can
  * fail in a way that question cannot see: the key names a real, owned declaration, the remedy
  * exists, and the FINDING IT RESOLVES NEVER FIRED THIS RUN. Nothing is wrong with the key and
  * nothing is wrong with the program; the entry simply did nothing, and reported as `NeverMatched`
  * it would send its author looking for a member that is right there. So the plan reports
  * declared-beside-applied ([[troubles]]), which is `CLAUDE.md` §5.5's `expected#derived` /
  * `expected#declared` split one artifact over: never only the applied count.
  */
final class ResolutionPlan(val entries: List[ResolutionPlan.Entry]):

  private val log   = collection.mutable.ListBuffer.empty[AppliedResolution]
  private val fired = collection.mutable.Set.empty[String]
  private val declined = collection.mutable.ListBuffer.empty[RefusedResolution]

  /** entries by the declaration they bound to — the lookup a phase makes per site. Built once;
    * a key that did not bind has no target and is absent by construction. */
  private val byTarget: Map[SymId, List[ResolutionPlan.Entry]] =
    entries.filter(_.actionable).groupBy(_.target.getOrElse(SymId.None)) - SymId.None

  def isEmpty: Boolean  = entries.isEmpty
  def nonEmpty: Boolean = entries.nonEmpty
  def size: Int         = entries.size

  /** every key that BOUND, whatever it went on to do — what the run's `fired` set is fed from, so an
    * inherited selection that bound is not reported as never-fired by `ManifestAgreement`. */
  def boundKeys: Set[String] = entries.filter(_.target.isDefined).map(_.declared).toSet

  /** WHAT DID THE PORT CHOOSE HERE? — the one question a phase or check asks.
    *
    * `target` is the DECLARATION the site sits in, never the site's own symbol: that is the
    * granularity the key has (see [[Resolution]]), and asking at the site would silently answer
    * `None` for every selection ever written. `lane`/`kind` are the residue row this caller is about
    * to file, so a selection aimed at a different lane is not consulted — an id is globally unique,
    * so this can only ever narrow an entry to the one caller that declared it.
    *
    * The match is [[Remedy.answers]] and never two equality tests written here, because a lane may
    * split ONE SITE into several kinds ([[Remedy.alsoKinds]]) and a remedy that answers such a site
    * answers every row it produced.
    */
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

  /** DRAIN a residue lane — the move `CLAUDE.md` §5 requires, performed once rather than per check.
    *
    * A resolution is not a fix, it is a MOVE: the row leaves the refusal lane that counted it and
    * arrives in `remediation(resolved)`, and a baseline diff must read `<lane> N->M` beside
    * `remediation(resolved) 0->(N-M)`. Written per check that would have been three chances to
    * record one half and not the other — which is exactly the shape "a lane that fell with nothing
    * to attribute the fall to" describes. So the partition and the ledger row come from ONE
    * traversal, the way `AppliedResolution` makes the finding and the `Decision` come from one value.
    *
    * Returns the findings that were NOT drained, in their original order. A caller records those and
    * nothing else; the drained ones are already in this plan's ledger and become
    * `remediation(resolved)` rows and `decisions.tsv` rows through [[all]].
    *
    * The empty-plan fast path is derived from THIS plan's own state and nothing else, which is
    * `CLAUDE.md` §4.56's rule for a guard: a port that selected nothing has no entry to match, so
    * skipping is arithmetic rather than a second opinion about the findings.
    *
    * ==Why this and [[appliedAt]] are BOTH here, and what decides which one a lane uses==
    * They are not two spellings of one act — they are the two halves of a single rule ("a row a
    * remedy answered leaves the lane"), split by WHO carried the remedy out:
    *
    *   - a remedy that CHANGES THE EMISSION must be applied by a phase, before the emitter runs, so
    *     by the time the check walks, the application is already in the ledger and the check's job is
    *     to ASK — [[appliedAt]], at the SITE, because such a remedy may refuse at one site of a
    *     member and apply at another;
    *   - a remedy that only MOVES A ROW has no phase and nothing to carry out. There is no earlier
    *     moment and no separate applier: the check that mints the residue is the only thing that ever
    *     holds the row, so it partitions and records in one traversal — this.
    *
    * A third function would be a third answer to a question these two already partition. Which one a
    * lane needs is read off `Remedy.emissionAffecting` and nothing else.
    */
  def drain[F](lane: String, findings: List[F])(residue: F => ResolutionPlan.Residue): List[F] =
    if entries.isEmpty then findings
    else
      findings.filter { f =>
        val r = residue(f)
        selected(r.at, lane, r.kind) match
          case Some(res) => applied(res, r.subject, r.at, r.origin, r.what); false
          case scala.None => true
      }

  /** DID THE PORT PICK **THIS** REMEDY HERE? — the precise question a PHASE that offers a menu has.
    *
    * [[selected]] narrows by `(lane, kind)` and is right for a caller about to file one residue row.
    * A phase holding a specific `Remedy` wants to know whether this declaration chose IT, and asked
    * through `(lane, kind)` it would match a SIBLING on the same lane — which is unavoidable where a
    * lane's remedies all declare `Remedy.AnyKind`. An id is globally unique, so this cannot. */
  def selected(target: SymId, remedy: Remedy): Option[Resolution] =
    byTarget.getOrElse(target, Nil).iterator.flatMap(_.resolution).find(_.remedy.id == remedy.id)

  /** …and the DECLINE, which counts as having FIRED for exactly the reason `record` does: the
    * selection was consulted and answered, so reporting it as `NeverApplied` beside its own refusal
    * row would give one entry two contradictory diagnoses. */
  def refuse(r: Resolution, subjectFqn: String, origin: Origin, guard: String, why: String): Unit =
    fired += r.declaredKey
    declined += RefusedResolution(r, subjectFqn, origin, guard, why)

  def refusals: List[RefusedResolution] = declined.toList

  def all: List[AppliedResolution] = log.toList

  /** DID A REMEDY ALREADY ANSWER THIS ROW? — the DRAIN, asked by the check that mints the lane.
    *
    * A resolution that is not emission-affecting changes no tree, so the residue it answered is
    * still standing where the check walks and the check would report it beside the
    * `remediation(resolved)` row that says it was answered — two rows for one site, and a lane that
    * cannot fall by what `resolved` gained (`CLAUDE.md` §5). So the check asks HERE, and asks about
    * what the phase RECORDED rather than about what the manifest DECLARED: a selection the phase
    * examined and refused (a guard said the remedy does not apply at this site) has no row here, and
    * its finding stays in the lane exactly as it should. One ledger, read from both ends — never two
    * derivations of "did this fire", which is what `AppliedResolution` exists to prevent.
    *
    * '''Matched at the SITE, not at the declaration.''' A selection BROADCASTS across the member it
    * names, so a per-declaration test looks equivalent and is not: a remedy may REFUSE at one site
    * of a member and apply at another (`ascribe-javac-choice` cannot write every alternative), and
    * the lane would then fall by two where `resolved` gained one. The `origin` is the one the
    * applier recorded, which is the finding's own — so the two sides are the same value and not two
    * spellings of a position.
    */
  def appliedAt(subject: SymId, lane: String, kind: String, origin: Origin): Boolean =
    subject != SymId.None &&
      log.exists(a => a.subject == subject && a.origin == origin && a.remedy.answers(lane, kind))

  /** THE DECISION ROWS — one per (declaration, remedy), never one per SITE.
    *
    * The two artifacts of an application are deliberately not the same shape, and this is where they
    * part. A `remediation(resolved)` FINDING is per site, because it is the row that MOVED and the
    * drained lane has to fall by exactly as many; a `Decision` is per DECLARATION, because
    * `CLAUDE.md` §5.1 says so in as many words — "one row per declaration whose emitted form the
    * decision changes, never one per expression" — and because a decision becomes a PORTER NOTE, so
    * one per site is the same sentence printed twice above one `val`. Measured on the first
    * broadcast selection in the corpus: two residual-global reads, two identical notes.
    *
    * That is not the two artifacts disagreeing, which is what [[AppliedResolution]] exists to
    * prevent: they still come from one value and say the same thing. It is each of them answering at
    * the granularity its own reader has.
    */
  def decisions: List[Decision] =
    log.toList.map(_.decision).distinctBy(d => (d.kind, d.subject, d.subjectFqn, d.detail.get("remedy")))

  /** everything recorded so far, with the ledger emptied — how the run moves a translation's
    * applications into its artifacts without the buffer outliving the translation. */
  def drain(): List[AppliedResolution] = { val out = log.toList; log.clear(); out }

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
      else if e.target.isDefined && !fired.contains(e.declared) then
        Some(ResolutionPlan.Trouble(e.declared, e.id, ResolutionPlan.Issue.NeverApplied,
          s"the key names a declaration this run OWNS and '${e.id}' is live, but no " +
            s"${e.resolution.map(_.remedy.target).getOrElse("?")} finding occurred at it this run — " +
            "the selection is not wrong, it is inert. Either the site it was written for is gone, or " +
            "something upstream in the pipeline already answered it"))
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
    * one argument.
    *
    * A record and not five curried functions, because every field answers a different question and a
    * positional lambda list is where two of them get swapped silently: `kind` is the lane's own
    * finding kind (the remedy names it, so a selection aimed elsewhere is not consulted), `at` is
    * the DECLARATION the selection keys on — never the site's own symbol, which is the granularity
    * `Resolution` chose — `subject` and `origin` are what the `remediation(resolved)` row and the
    * `decisions.tsv` row are written from, and `what` is the one phrase a reader gets in the porter
    * note.
    */
  final case class Residue(kind: String, at: SymId, subject: String, origin: Origin, what: String)

  /** ONE declared entry, with everything the run learned about it before any phase ran.
    *
    * @param declared    the manifest key verbatim.
    * @param id          the remedy id the manifest chose.
    * @param resolution  the bound selection a phase can act on — present iff the id names a live
    *                    remedy AND the key bound to a declaration.
    * @param target      the declaration the key bound to, if it bound.
    * @param unknown     the id names no remedy this engine or classpath ships.
    * @param sourceAbsent the id is known and its declarer is not in THIS run.
    * @param declaredBy  who declares the id, for the `sourceAbsent` message.
    */
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

  /** Why a declared selection did nothing. Three answers because there are three different next
    * actions, which is `PolicyBinder`'s own argument for its five: collapsed into one, "you typed
    * the id wrong", "you did not enable the phase" and "the finding stopped occurring" read
    * identically and mean entirely different things. */
  enum Issue:
    case UnknownRemedy, SourceAbsent, NeverApplied

  final case class Trouble(declared: String, id: String, issue: Issue, detail: String)

  /** BIND every declared selection, once, before the pipeline runs.
    *
    * Where `PortRun.bindDeclaredPolicy` binds the drops — and for the same two reasons, which are
    * not scheduling: every key is written in the UPSTREAM namespace and the package rename runs LAST
    * (§4.56), so binding at the front resolves each key against the names its author wrote; and "did
    * this selection fire?" becomes a property of the policy and the program rather than of the order
    * phases happened to run in.
    *
    * Every entry is bound EVEN WHERE THE ID IS ALREADY IN TROUBLE, so an entry with two mistakes in
    * it reports both. `bindMember` and not `bindMembers`: a selection acts at ONE declaration, so a
    * bare key naming two overloads is `Ambiguous` with both candidates listed — the binder's own
    * vocabulary, which is what `CLAUDE.md`'s §1(b) reader already knows how to act on.
    *
    * WHICH binding seam, and with what [[Ownership]], is the REMEDY's declaration and not this
    * function's ([[Remedy.Subject]]): a residue at a TYPE has no member to name, and an egress row's
    * subject is a member this program references and does not declare. Every refusal still arrives
    * through the same binder and the same vocabulary, so a reader sees one grammar.
    *
    * @param declared   the manifest's effective `resolutions`, key → remedy id.
    * @param vocabulary the KNOWN set — every remedy this engine and classpath ship.
    * @param active     the ids whose declaring source is in THIS run. A subset of the vocabulary's.
    */
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
