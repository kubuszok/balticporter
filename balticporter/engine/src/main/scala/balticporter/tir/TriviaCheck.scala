package balticporter.tir

import balticporter.core.CommentScanner

/** Compares source-text comments against emitted text to find dropped comments.
  *
  * Re-lexes Java independently via `CommentScanner` (not the TIR). Comparison uses normalised
  * body text, grouped by Java file. A finding is a §1(a) engine gap; a dropped member's
  * Javadoc is classified as deliberate rather than lost. // CLAUDE.md §4.58 */
object TriviaCheck:

  /** One comment present in the Java and absent from everything that file emitted. */
  final case class Finding(javaPath: String, kind: TriviaKind, text: String, line: Int):
    /** First line, trimmed and capped at 120 chars. */
    def detail: String =
      val one = text.linesIterator.map(_.trim).filter(_.nonEmpty).mkString(" ")
      if one.length <= 120 then one else one.take(117) + "…"
    def render: String = s"$kind dropped: $detail  ($javaPath:$line)"
    /** @param check the lane this finding is recorded under (must match the caller's lane name). */
    def report(check: String = "trivia"): CheckReport.Finding =
      CheckReport.Finding(check, s"${kind.toString.toLowerCase} dropped",
        CheckReport.relativise(javaPath), CheckReport.relativise(javaPath), line, detail)

  /** One emitted unit: the Java file it came from, and its Scala text. */
  final case class Unit(javaPath: String, scala: String)

  /** One comment the backstop put back, read off the marker in the emitted file. */
  final case class Recovered(javaPath: String, line: Int):
    def report: CheckReport.Finding =
      CheckReport.Finding("trivia(recovered)", "recovered by the backstop",
        CheckReport.relativise(javaPath), CheckReport.relativise(javaPath), line,
        "the attachment channel could not place this comment; it was put back after the member it " +
          "was written in, with its java coordinates — a counted residue, not a success")

  /** @param lost comments absent from all emitted files (target: zero).
    * @param recovered comments the backstop placed (a counted residue, not a success).
    * @param deliberate comments whose declaration the port drops on purpose (derived from drops).
    */
  final case class Result(lost: List[Finding], recovered: List[Recovered], deliberate: List[Finding])

  /** @param members declarations per java file ([[CommentAnchor]]); empty disables deliberate
    *   classification (over-reporting `lost` is the safe direction). */
  def check(emitted: List[Unit],
            members: Map[String, List[CommentAnchor.Member]] = Map.empty,
            read: String => Option[String] = readFile): Result =
    val groups = emitted.filter(_.javaPath.nonEmpty).groupBy(_.javaPath).toList.sortBy(_._1)
    val out    = groups.map { (path, units) =>
      read(path) match
        // unreadable file: report nothing (the coverage denominator reflects it)
        case scala.None => (Nil, Nil, Nil)
        case Some(java) =>
          val text = units.map(_.scala).mkString("\n")
          // strip porter notes and recovery markers before searching
          val hay   = normalize(TriviaMark.stripAll(text))
          val lines = java.linesIterator.toArray
          val here  = members.getOrElse(CommentAnchor.key(path), Nil)
          val seen  = collection.mutable.Set.empty[String]
          val found = CommentScanner.scanAt(java).flatMap { a =>
            val t    = a.trivia
            val body = normalize(t.text)
            // empty normalisation = comment with no words (`//`, `/****/`)
            if body.isEmpty || hay.contains(body) then Nil
            // deduplicate: same comment written twice in one file is one finding
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

  /** How many of the given files could be compared (the denominator). */
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
    // both lanes always print, zero included
    val rec =
      if r.recovered.isEmpty then "  recovered by the backstop: 0"
      else
        val worst = r.recovered.groupBy(_.javaPath).toList.sortBy(-_._2.size).take(5)
        (s"  recovered by the backstop: ${r.recovered.size} (a counted residue, not a success)" ::
          worst.map((p, fs) => s"    ${fs.size} in ${CheckReport.relativise(p)}")).mkString("\n")
    val del = s"  deliberate (the declaration they document is one this port drops): ${r.deliberate.size}"
    s"$head\n$rec\n$del"

  /** Strip delimiters, gutter, indentation and repeated whitespace, leaving the words.
    * `//` is stripped FIRST so a block comment re-emitted as `//` lines normalises correctly. */
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
