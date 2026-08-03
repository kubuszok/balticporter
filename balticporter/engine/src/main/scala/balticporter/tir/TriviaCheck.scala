package balticporter.tir

import balticporter.core.CommentScanner

/** Comments the port DROPPED — counted, located, and reported on every run.
  *
  * ## Why this exists at all
  *
  * The trivia path (`SpoonTir` harvests, `TirEmitter` re-emits) has exactly the shape CLAUDE.md §3
  * warns about: nothing downstream fails when it stops working. A comment that never reaches the
  * output costs no compile error, moves no check count and breaks no test — the whole feature can
  * regress to zero and every gate stays green. That already happened once WHILE IT WAS BEING
  * WRITTEN: one null from Spoon (`getOriginalSourceCode` on an in-memory file) reached a broad
  * `catch` and turned the entire harvest into `Nil`, and the emitted output was perfectly valid
  * Scala with no comments in it.
  *
  * For a LICENCE the stakes are not readability. §4.57 makes attribution a legal obligation of the
  * generated file, and a silently-dropped notice is the one defect here that a user of the port
  * inherits.
  *
  * ## What it compares, and why not the TIR
  *
  * SOURCE TEXT against EMITTED TEXT — never the tree. A count of `Trivia` nodes would prove the
  * frontend harvested and prove nothing about the emitter; and the frontend's own idea of "every
  * comment" is Spoon's attachment model, which is precisely the thing that could be incomplete.
  * `CommentScanner` re-lexes the Java independently, so a comment Spoon never attached is still
  * counted against the port.
  *
  * Comparison is on NORMALISED body text (delimiters, gutter and indentation removed, whitespace
  * collapsed) because the emitter deliberately re-indents (`TirEmitter.triviaText`), and a check
  * that failed on whitespace would report every comment in the corpus.
  *
  * ## Grouping is by JAVA FILE, not by unit
  *
  * A Java file may declare more than one top-level type and becomes that many Scala files, so a
  * comment "missing" from one of them is present in its sibling. The concatenation of everything a
  * file emitted is the only honest right-hand side.
  *
  * ## What a finding MEANS (CLAUDE.md §4.45 — a check must say which of §1's three kinds it is)
  *
  * A finding is a §1(a) ENGINE gap: a Java position the frontend has no harvest point for, or an
  * emission path that renders a node without its `leading`. It is NOT a policy question and there
  * is nothing to configure. The exception is a member the port DROPS on purpose — its Javadoc goes
  * with it — which is why the caller passes only the units it actually emits, exactly as
  * [[OmissionCheck]] does.
  */
object TriviaCheck:

  /** one comment present in the Java and absent from everything that file emitted. */
  final case class Finding(javaPath: String, kind: TriviaKind, text: String, line: Int):
    /** first line, trimmed and capped — a whole Apache header in a TSV cell helps nobody. */
    def detail: String =
      val one = text.linesIterator.map(_.trim).filter(_.nonEmpty).mkString(" ")
      if one.length <= 120 then one else one.take(117) + "…"
    def render: String = s"$kind dropped: $detail  ($javaPath:$line)"
    /** @param check the LANE this finding is recorded under. A finding carries the check name it is
      *   filed against, so a lane that passed its own name to `record` and left the name inside the
      *   finding alone would file every row under the other lane — silently, with both counts
      *   plausible: `lost` read 12 and `deliberate` read 0 on a run whose stdout said the reverse. */
    def report(check: String = "trivia"): CheckReport.Finding =
      CheckReport.Finding(check, s"${kind.toString.toLowerCase} dropped",
        CheckReport.relativise(javaPath), CheckReport.relativise(javaPath), line, detail)

  /** @param emitted one entry per EMITTED unit: the Java file it came from, and its Scala text. */
  final case class Unit(javaPath: String, scala: String)

  /** one comment the backstop put back, read off the marker the emitted file carries. */
  final case class Recovered(javaPath: String, line: Int):
    def report: CheckReport.Finding =
      CheckReport.Finding("trivia(recovered)", "recovered by the backstop",
        CheckReport.relativise(javaPath), CheckReport.relativise(javaPath), line,
        "the attachment channel could not place this comment; it was put back after the member it " +
          "was written in, with its java coordinates — a counted residue, not a success")

  /** THE THREE LANES, and why they are three rather than one number.
    *
    * @param lost the publishable bar, target ZERO: a comment in the java that reached no emitted
    *   file. A §1(a) ENGINE gap, and a licence among them is a §4.57 obligation.
    * @param recovered comments the emitter's backstop placed. NOT a success — a residue that says
    *   the attachment channel could not carry them, and the per-file breakdown IS the work list
    *   for giving each category an honest home.
    * @param deliberate a comment whose declaration the port DROPS on purpose. Derived from the
    *   run's own drops, exactly as the expected-failure ledger is — nothing is hand-listed, so the
    *   set follows the manifest with nobody editing anything. The exemption used to be TYPE-level
    *   only, so a dropped MEMBER's javadoc was counted as engine loss on every run.
    */
  final case class Result(lost: List[Finding], recovered: List[Recovered], deliberate: List[Finding])

  /** @param members every declaration of every java file, emitted and dropped ([[CommentAnchor]]).
    *   Empty means "this caller cannot tell them apart", and then nothing is classified as
    *   deliberate — the honest degradation, since over-reporting `lost` is visible and
    *   under-reporting it is not. */
  def check(emitted: List[Unit],
            members: Map[String, List[CommentAnchor.Member]] = Map.empty,
            read: String => Option[String] = readFile): Result =
    val groups = emitted.filter(_.javaPath.nonEmpty).groupBy(_.javaPath).toList.sortBy(_._1)
    val out    = groups.map { (path, units) =>
      read(path) match
        // A source we cannot read is NOT reported as "no comments lost": it is reported as
        // nothing at all, and the coverage number below says how many files were actually
        // compared. A check that silently scores an unreadable file as clean is worse than one
        // that admits it saw fewer files.
        case scala.None => (Nil, Nil, Nil)
        case Some(java) =>
          val text = units.map(_.scala).mkString("\n")
          // The engine's own commentary is removed BEFORE the search. A porter note names an
          // upstream FQN on purpose and a recovery marker names an upstream PATH, so either can
          // match text that is not the comment — the trap that produced three phantom dangling
          // drops the first time notes shipped, one check over.
          val hay   = normalize(TriviaMark.stripAll(text))
          val lines = java.linesIterator.toArray
          val here  = members.getOrElse(CommentAnchor.key(path), Nil)
          val seen  = collection.mutable.Set.empty[String]
          val found = CommentScanner.scanAt(java).flatMap { a =>
            val t    = a.trivia
            val body = normalize(t.text)
            // an EMPTY normalisation is a comment with no words in it (`//`, `/****/`); there is
            // nothing to find and nothing lost.
            if body.isEmpty || hay.contains(body) then Nil
            // …and the same comment written twice in one file is one finding, not two: the
            // emitted side cannot distinguish them either.
            else if !seen.add(body) then Nil
            else
              val line = a.line(java)
              val f = Finding(path, t.kind match
                case balticporter.core.TriviaKind.Line    => TriviaKind.Line
                case balticporter.core.TriviaKind.Block   => TriviaKind.Block
                case balticporter.core.TriviaKind.Javadoc => TriviaKind.Javadoc
              , t.text, line)
              val owner = CommentAnchor.owner(lines, line, line + t.text.count(_ == '\n'), here)
              List(f -> owner.exists(!_.emitted))
          }
          (found.filterNot(_._2).map(_._1),
           units.flatMap(u => TriviaMark.scan(u.scala)).map(f => Recovered(f.javaPath, f.line)),
           found.filter(_._2).map(_._1))
    }
    Result(out.flatMap(_._1), out.flatMap(_._2), out.flatMap(_._3))

  /** how many of the given files could be compared at all — the denominator every count here needs. */
  def comparable(emitted: List[Unit], read: String => Option[String] = readFile): Int =
    emitted.map(_.javaPath).filter(_.nonEmpty).distinct.count(p => read(p).isDefined)

  def summary(r: Result, compared: Int): String =
    val head =
      if r.lost.isEmpty then s"  every comment in $compared source file(s) reached the emitted Scala"
      else
        val byKind = r.lost.groupBy(_.kind).toList.sortBy(_._1.toString)
          .map((k, fs) => s"${fs.size} × $k").mkString(", ")
        val worst = r.lost.groupBy(_.javaPath).toList.sortBy(-_._2.size).take(5)
        (s"  $byKind, over $compared compared source file(s)" ::
          worst.map((p, fs) => s"    ${fs.size} in ${CheckReport.relativise(p)}")).mkString("\n")
    // the other two lanes ALWAYS print, zero included: `recovered` is a residue whose per-file
    // breakdown is the work list for the honest homes, and a lane that only appears when it is
    // non-zero is a lane a reader learns to stop looking for.
    val rec =
      if r.recovered.isEmpty then "  recovered by the backstop: 0"
      else
        val worst = r.recovered.groupBy(_.javaPath).toList.sortBy(-_._2.size).take(5)
        (s"  recovered by the backstop: ${r.recovered.size} (a counted residue, not a success)" ::
          worst.map((p, fs) => s"    ${fs.size} in ${CheckReport.relativise(p)}")).mkString("\n")
    val del = s"  deliberate (the declaration they document is one this port drops): ${r.deliberate.size}"
    s"$head\n$rec\n$del"

  /** the comment's body with everything the emitter is allowed to change removed: delimiters, the
    * ` * ` gutter, indentation and repeated whitespace. What is left is the words.
    *
    * `//` comes off FIRST, and that ordering is the whole correctness of this function on one of
    * the two forms it has to compare. §4.58 renders a block comment that Scala would NEST on
    * line-by-line as a `//` line, so a javadoc opener arrives here with a slash-slash in front of
    * it — and with the slash-slash stripped LAST the javadoc opener was still on the front, the two
    * sides normalised differently, and every such comment was reported lost while sitting in the
    * emitted file. The rule is symmetric: a java LINE comment that itself quotes a block delimiter
    * normalises the same way on both sides. */
  def normalize(text: String): String =
    text.linesIterator
      .map(_.trim.stripPrefix("//").trim
             .stripPrefix("/**").stripPrefix("/*").stripSuffix("*/").stripPrefix("*").trim)
      .filter(_.nonEmpty)
      .mkString(" ")
      .replaceAll("\\s+", " ")

  private def readFile(path: String): Option[String] =
    val p = java.nio.file.Path.of(path)
    if java.nio.file.Files.isRegularFile(p) then
      try Some(java.nio.file.Files.readString(p)) catch case _: Throwable => scala.None
    else scala.None
