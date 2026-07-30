package balticporter.tir

/** Did every decision that must carry a [[PorterNote]] actually get one, and did every note in the
  * output come from a decision?
  *
  * ## Why a check at all
  *
  * A note is derived from a `Decision`, so it looks like it cannot drift. It can, in both
  * directions, and both are silent:
  *
  *   - '''A decision with no note.''' The emitter renders notes at the DECLARATION sites it knows
  *     about — `def`, `val`, `class`, and the head of a type's body for a member that is not
  *     there. A decision whose subject is emitted through some other path (an enum case, a
  *     synthesised member, a kind added to [[PorterNote.Rendered]] with no call site) simply
  *     produces no note. Nothing else in the pipeline can see that: the output compiles, every
  *     count is unchanged, and `decisions.tsv` still has the row. That is the same shape as the
  *     trivia regression §4.58 records, arriving from the other side.
  *   - '''A note with no decision.''' Some future call site prints `/* porter: … */` from a local
  *     condition rather than from the log. That is policy smuggled into the emitter — the exact
  *     thing the derivation exists to prevent — and it would read, to an agent, as authoritative.
  *
  * ## What is IN scope, and what a covered decision means
  *
  * Only decisions whose kind is in [[PorterNote.Rendered]] and whose subject this run EMITTED. The
  * three exclusions are each a statement rather than a gap:
  *
  *   - a kind outside `Rendered` is covered by `decisions.tsv`, deliberately (see `Rendered`'s doc
  *     for why a note per retyped signature is noise rather than information);
  *   - a decision about a subject this run does not emit — a policy key that matched nothing, a
  *     type another module owns, an injected FQN with no `SymId` — has no line of code to sit
  *     beside, and `decisions.tsv` is its whole record;
  *   - [[PorterNote.NotInTree]] kinds (a dropped TYPE) are carried by the INJECTED file that
  *     replaces them, which the orchestrator prepends at copy time; a drop with no replacement has
  *     no file at all and is reported by `SubstitutionCheck.dangling`, not here.
  *
  * ## Why it joins on `SymId` and not on a name
  *
  * Three of the emitter's own passes rename the symbol before it is rendered (`style` ->
  * `style$shadow`), so a name-keyed join is empty on exactly the decisions this check exists for.
  * The decision carries the id; the emitter records the id it printed for; the join is exact.
  */
object NoteCoverageCheck:

  val Name = "porter-notes"

  val Classification: String =
    "  §1(a) ENGINE: a decision the port made is not visible in the code it produced (or a note in " +
      "the code has no decision behind it). Every note is DERIVED from `decisions.tsv` — fix the " +
      "emitter's note placement (TirEmitter) or the recording (PortRun/Pipeline), both in engine. No manifest change " +
      "helps, and nothing else in the pipeline reports this."

  enum Issue(val label: String):
    /** a decision about an EMITTED subject that reached no note. */
    case Missing extends Issue("decision with no note")
    /** a note in the emitted text whose (kind, subject) matches no decision. */
    case Unbacked extends Issue("note with no decision")
    /** the emitter says it printed a note that is not in the text that was written. */
    case NotWritten extends Issue("note recorded but absent from the file")

  /** `kind` is optional because [[Issue.NotWritten]] is about a UNIT, not about one decision — and
    * a sentinel kind in that column would group under a real one in `findings.tsv`. */
  final case class Finding(issue: Issue, kind: Option[Decision.Kind], subject: String, where: String, detail: String):
    private def k = kind.map(PorterNote.slug).getOrElse("-")
    def render: String = s"${issue.label}: $k $subject — $detail  ($where)"
    def report: CheckReport.Finding =
      CheckReport.Finding(Name, issue.label, subject, where, 0, s"$k: $detail")

  /** @param decisions the run's complete log, after the ownership filter
    * @param printed   what the emitter recorded printing, one entry per note
    * @param emitted   every symbol this run wrote a declaration for
    * @param texts     `(unit fqn, emitted text)` for every file the run wrote from the TIR
    */
  def check(
      decisions: List[Decision],
      printed: List[PorterNote.Printed],
      emitted: Set[SymId],
      texts: List[(String, String)],
  ): List[Finding] =
    val printedBy: Set[(Decision.Kind, SymId)] = printed.map(p => p.kind -> p.subject).toSet

    // ---- direction 1: a decision that must be noted and was not ----
    val missing = decisions
      .filter(d => PorterNote.Rendered(d.kind) && !PorterNote.NotInTree(d.kind))
      .filter(d => d.subject != SymId.None && emitted(d.subject))
      .filterNot(d => printedBy(d.kind -> d.subject))
      .map(d => Finding(Issue.Missing, Some(d.kind), d.subjectFqn,
        s"${d.origin.javaPath}:${d.origin.line}",
        "the run emitted this subject and recorded this decision, but no porter note was rendered " +
          "for it — the emitter has no note call site on the path that renders it"))

    // ---- directions 2 and 3: the TEXT against what the emitter says it printed ----
    //
    // One join, per unit, and it must be per unit rather than global: a global count balances a
    // note invented in one file against a note lost in another, which is the shape of check that
    // reports zero forever. `printed` is the derivation's own record; the file is what happened.
    //
    // Two ways they disagree, and they are different defects:
    //   - the file carries a note of a KIND the emitter did not record printing here (or a slug no
    //     `Decision.Kind` has) — some other code path is writing porter notes from a local
    //     condition, which is policy invented at the emitter and reads as authoritative;
    //   - the emitter recorded MORE notes than the file carries — a rendered note was dropped by
    //     the code that assembled the member (a `filter(_.nonEmpty)`, a branch that recomputed).
    //
    // Only the SLUG is read out of the text. Asserting on the `k=v` half would be asserting on a
    // rendering; the authoritative side is the emitter's record, which carries the `SymId`.
    val printedByUnit: Map[String, List[PorterNote.Printed]] = printed.groupBy(_.unit)
    val fromText = texts.flatMap { (unit, text) =>
      val here  = printedByUnit.getOrElse(unit, Nil)
      val kinds = here.map(_.kind).toSet
      val found = PorterNote.scan(text)
      val unbacked = found.map(_.kind).distinct
        .filterNot(_.exists(kinds))
        .map { k =>
          Finding(Issue.Unbacked, k, k.map(PorterNote.slug).getOrElse(found.find(_.kind.isEmpty).map(_.slug).getOrElse("?")), unit,
            "this note is in the emitted text and the emitter recorded printing no note of that " +
              "kind for this unit — a note that is not DERIVED from a decision is policy the " +
              "emitter invented")
        }
      val lost =
        if here.sizeIs <= found.size then Nil
        else List(Finding(Issue.NotWritten, scala.None, unit, unit,
          s"the emitter recorded ${here.size} note(s) for this unit and the file carries " +
            s"${found.size} — a rendered note was dropped by the code that assembled the member"))
      unbacked ++ lost
    }

    (missing ++ fromText)
      .sortBy(f => (f.issue.toString, f.kind.map(_.toString).getOrElse(""), f.subject, f.where))

  def summary(fs: List[Finding], notes: Int): String =
    if fs.isEmpty then s"  $notes porter note(s) emitted, every one derived from a recorded decision"
    else fs.groupBy(_.issue).toList.sortBy(_._1.toString).map { (i, g) =>
      s"  ${g.size} × ${i.label}\n" + g.take(10).map("    " + _.render).mkString("\n") +
        (if g.sizeIs > 10 then s"\n    … ${g.size - 10} more (see findings.tsv)" else "")
    }.mkString("\n")
