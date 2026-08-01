package balticporter.tir

/** The line that accompanies a RECOVERED comment — one the attachment channel could not place, put
  * back beside the member it was written in, with the java coordinates it came from.
  *
  * {{{
  * /* trivia: recovered from com/example/Json.java:412 */
  * }}}
  *
  * ## Why it is not porter-note grammar
  *
  * A recovered comment records no [[Decision]]: a row per COMMENT is finer than §5.1's
  * one-row-per-declaration rule, and inventing one would put thousands of rows in `decisions.tsv`
  * saying nothing about any declaration. But a `/* porter: … */` line with no decision behind it is
  * exactly what [[NoteCoverageCheck]] fails the run for, in that direction — a note the emitter
  * invented from a local condition. So the marker has its OWN token, and the exemption is BY SHAPE:
  * `PorterNote.scan` looks for that object's own `Marker` and can never see this one, with no
  * exemption list for a future kind to fall off.
  *
  * ## What the line is FOR
  *
  * `ENGINE-LIMITS.md` V1's standing objection to recovery is right: a comment that describes a
  * statement, printed above a method, is worse than absent, because it now says something false. The
  * coordinates are the answer to it — a comment relocated WITH its source position is a QUOTATION,
  * and a reader who wants the context knows the file and the line to open. It is also why the
  * marker is a separate LINE rather than a prefix: [[TriviaCheck.normalize]] still finds the
  * comment's own body, so the check keeps counting the thing rather than the engine's words about
  * it.
  *
  * ## Stripping
  *
  * Every check that searches emitted TEXT for a string must remove what the engine wrote ABOUT the
  * code before it looks, or it matches the engine's own words — the trap that produced three
  * phantom dangling drops the first time porter notes shipped. This marker names an upstream PATH
  * on purpose, so it is stripped exactly as a note is; [[stripAll]] is the form every such check
  * should call.
  */
object TriviaMark:

  /** the token every marker starts with, and the only thing a scan needs to know. */
  val Marker = "/* trivia:"

  /** one marker, ready to sit on its own line above the comment it introduces. */
  def render(javaPath: String, line: Int): String =
    s"$Marker recovered from ${KeyValues.safe(javaPath)}:$line */"

  /** one marker as it stands IN the emitted text. */
  final case class Found(javaPath: String, line: Int)

  private val Pattern = """/\* trivia: recovered from (.+):(\d+) \*/""".r

  /** every recovered comment a file records, in order — the `recovered` lane's rows, read off the
    * text rather than reported by the emitter, for the reason [[TriviaCheck]] compares text to text
    * at all: what the file SAYS is the only side that cannot be a claim about a run that no longer
    * exists. */
  def scan(text: String): List[Found] =
    Pattern.findAllMatchIn(text).map(m => Found(m.group(1), m.group(2).toInt)).toList

  /** the text with every marker removed — see the note on stripping above. */
  def strip(text: String): String = stripFrom(text, Marker)

  /** …and with porter notes removed too: the two things the engine writes ABOUT the code. */
  def stripAll(text: String): String = strip(stripFrom(text, PorterNote.Marker))

  /** Remove every block comment that OPENS with `marker`.
    *
    * One implementation for both markers rather than one per marker: the second copy is where the
    * two grammars drift, and a check that strips one and not the other is exactly the bug this
    * exists to prevent. Only marked comments are removed — an upstream Javadoc that genuinely
    * discusses a dropped type is text the checks have always counted. */
  private def stripFrom(text: String, marker: String): String =
    if !text.contains(marker) then text
    else
      val sb = new java.lang.StringBuilder
      var i  = 0
      var go = true
      while go do
        val at = text.indexOf(marker, i)
        if at < 0 then
          sb.append(text.substring(i))
          go = false
        else
          sb.append(text.substring(i, at))
          val end = text.indexOf("*/", at)
          if end < 0 then go = false else i = end + 2
      sb.toString
