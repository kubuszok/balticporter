package balticporter.tir

/** Marker line for a recovered comment: `/* trivia: recovered from <path>:<line> */`.
  *
  * Has its own token (not porter-note grammar) so [[NoteCoverageCheck]] cannot confuse the two.
  * [[stripAll]] removes both markers and porter notes before any text search. */
object TriviaMark:

  /** The token every marker starts with. */
  val Marker = "/* trivia:"

  /** Render one marker line. */
  def render(javaPath: String, line: Int): String =
    s"$Marker recovered from ${KeyValues.safe(javaPath)}:$line */"

  /** A parsed marker from emitted text. */
  final case class Found(javaPath: String, line: Int)

  private val Pattern = """/\* trivia: recovered from (.+):(\d+) \*/""".r

  /** Every recovered comment in the text, in order (read from the file, not from the emitter). */
  def scan(text: String): List[Found] =
    Pattern.findAllMatchIn(text).map(m => Found(m.group(1), m.group(2).toInt)).toList

  /** Text with every trivia marker removed. */
  def strip(text: String): String = stripFrom(text, Marker)

  /** Text with both trivia markers and porter notes removed. */
  def stripAll(text: String): String = strip(stripFrom(text, PorterNote.Marker))

  /** Remove every block comment that opens with `marker`. Shared implementation for both
    * marker kinds to prevent drift. */
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
