package balticporter.tir

/** Verifies that every rendered `Decision` produced a `PorterNote`, and vice versa.
  *
  * Scope: decisions in [[PorterNote.Rendered]] whose subject this run emitted (excludes
  * `NotInTree` kinds, unmatched keys, and non-rendered kinds). Joins on `SymId`, not name
  * (renaming passes change names before rendering). // CLAUDE.md §4.575 */
object NoteCoverageCheck:

  val Name = "porter-notes"

  val Classification: String =
    "  §1(a) ENGINE: a decision the port made is not visible in the code it produced (or a note in " +
      "the code has no decision behind it). Every note is DERIVED from `decisions.tsv` — fix the " +
      "emitter's note placement (TirEmitter) or the recording (PortRun/Pipeline), both in engine. No manifest change " +
      "helps, and nothing else in the pipeline reports this."

  enum Issue(val label: String):
    /** A decision about an emitted subject that reached no note. */
    case Missing extends Issue("decision with no note")
    /** A note in the emitted text whose (kind, subject) matches no decision. */
    case Unbacked extends Issue("note with no decision")
    /** The emitter recorded a note that is absent from the written file. */
    case NotWritten extends Issue("note recorded but absent from the file")

  /** `kind` is optional because [[Issue.NotWritten]] is about a unit, not one decision. */
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

    // per-unit join of emitter records against actual file text (only slug is read from text)
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
