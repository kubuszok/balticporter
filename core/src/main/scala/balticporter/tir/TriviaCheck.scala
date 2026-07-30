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
    def report: CheckReport.Finding =
      CheckReport.Finding("trivia", s"${kind.toString.toLowerCase} dropped",
        CheckReport.relativise(javaPath), CheckReport.relativise(javaPath), line, detail)

  /** @param emitted one entry per EMITTED unit: the Java file it came from, and its Scala text. */
  final case class Unit(javaPath: String, scala: String)

  def check(emitted: List[Unit], read: String => Option[String] = readFile): List[Finding] =
    emitted
      .filter(_.javaPath.nonEmpty)
      .groupBy(_.javaPath)
      .toList
      .sortBy(_._1)
      .flatMap { (path, units) =>
        read(path) match
          // A source we cannot read is NOT reported as "no comments lost": it is reported as
          // nothing at all, and the coverage number below says how many files were actually
          // compared. A check that silently scores an unreadable file as clean is worse than one
          // that admits it saw fewer files.
          case scala.None => Nil
          case Some(java) =>
            val out  = normalize(units.map(_.scala).mkString("\n"))
            val seen = collection.mutable.Set.empty[String]
            CommentScanner.scan(java).zipWithIndex.flatMap { (t, _) =>
              val body = normalize(t.text)
              // an EMPTY normalisation is a comment with no words in it (`//`, `/****/`); there is
              // nothing to find and nothing lost.
              if body.isEmpty || out.contains(body) then Nil
              // …and the same comment written twice in one file is one finding, not two: the
              // emitted side cannot distinguish them either.
              else if !seen.add(body) then Nil
              else List(Finding(path, t.kind match
                case balticporter.core.TriviaKind.Line    => TriviaKind.Line
                case balticporter.core.TriviaKind.Block   => TriviaKind.Block
                case balticporter.core.TriviaKind.Javadoc => TriviaKind.Javadoc
              , t.text, lineOf(java, t.text)))
            }
      }

  /** how many of the given files could be compared at all — the denominator every count here needs. */
  def comparable(emitted: List[Unit], read: String => Option[String] = readFile): Int =
    emitted.map(_.javaPath).filter(_.nonEmpty).distinct.count(p => read(p).isDefined)

  def summary(findings: List[Finding], compared: Int): String =
    if findings.isEmpty then s"  every comment in $compared source file(s) reached the emitted Scala"
    else
      val byKind = findings.groupBy(_.kind).toList.sortBy(_._1.toString)
        .map((k, fs) => s"${fs.size} × $k").mkString(", ")
      val worst = findings.groupBy(_.javaPath).toList.sortBy(-_._2.size).take(5)
      (s"  $byKind, over $compared compared source file(s)" ::
        worst.map((p, fs) => s"    ${fs.size} in ${CheckReport.relativise(p)}")).mkString("\n")

  /** the comment's body with everything the emitter is allowed to change removed: delimiters, the
    * ` * ` gutter, indentation and repeated whitespace. What is left is the words. */
  def normalize(text: String): String =
    text.linesIterator
      .map(_.trim.stripPrefix("/**").stripPrefix("/*").stripSuffix("*/").stripPrefix("*").stripPrefix("//").trim)
      .filter(_.nonEmpty)
      .mkString(" ")
      .replaceAll("\\s+", " ")

  private def lineOf(source: String, text: String): Int =
    val at = source.indexOf(text)
    if at < 0 then 0 else source.substring(0, at).count(_ == '\n') + 1

  private def readFile(path: String): Option[String] =
    val p = java.nio.file.Path.of(path)
    if java.nio.file.Files.isRegularFile(p) then
      try Some(java.nio.file.Files.readString(p)) catch case _: Throwable => scala.None
    else scala.None
