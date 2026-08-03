package balticporter.tir

/** The ONE `k=v` payload grammar this engine writes — space-separated pairs, sorted, values that
  * contain whitespace quoted (CLAUDE.md §4.575).
  *
  * It was invented for the PORTER NOTE and it is now also the payload of the port map's `shape`
  * column (`DESIGN.md` §8.3). Two renderings of one grammar is exactly the shape §4.56 warns about
  * — a reader that learns the note's spelling and then meets a second, nearly-identical one — so the
  * primitives live here, in `api`, where a §1(c) rule can reach them, and both consumers delegate.
  *
  * Two facts that are not style, both learned by getting them wrong once:
  *
  *   - '''a value containing whitespace MUST be quoted.''' The pair list is whitespace-separated, so
  *     an unquoted `key=com.example.a -> b` is three tokens and every reader truncates the value at
  *     the first space. That is how the note-coverage check's first run reported 594 notes as
  *     unbacked: both sides read a value neither had written.
  *   - '''nothing may open or close a comment.''' A note is emitted INSIDE a Scala block comment and
  *     Scala block comments NEST (§4.58), so a value carrying an opening delimiter swallows the rest
  *     of the file — as this very doc comment did, once, while stating the rule.
  *     [[safe]] spaces the delimiters apart rather than rejecting the value: a value that cannot be
  *     rendered safely is still information, and dropping it would make the note say less than the
  *     TSV for no reason a reader could see.
  *
  * Both are properties of the GRAMMAR, not of the note, which is why they moved here rather than
  * being restated at the second consumer.
  */
object KeyValues:

  /** Neutralise anything that could open or close a comment, and flatten to one line. */
  def safe(s: String): String =
    s.replace("/*", "/ *").replace("*/", "* /")
      .replace('\n', ' ').replace('\r', ' ').replace('\t', ' ')
      .replaceAll(" +", " ").trim

  /** A VALUE, as it appears after the `=`. Quoted when it contains whitespace or is empty. */
  def value(v: String): String =
    val s = safe(v)
    if s.exists(_.isWhitespace) || s.isEmpty then "\"" + s.replace("\"", "'") + "\"" else s

  /** Render a pair list. Order is the CALLER's — a porter note puts the §1 classification first and
    * sorts the rest; a contract row sorts throughout — so this does not impose one. */
  def render(pairs: List[(String, String)]): String =
    pairs.map((k, v) => s"$k=${value(v)}").mkString(" ")

  /** …and back. Unknown keys are kept: a payload written by a NEWER engine must degrade to "I do
    * not understand this key", never to a parse failure that discards the keys this engine does
    * understand (`DESIGN.md` §8.3's per-question degradation).
    *
    * A malformed token — no `=`, or a quote that never closes — is skipped rather than throwing, for
    * the same reason: one bad pair must not cost the row. */
  def parse(payload: String): Map[String, String] =
    val out = collection.mutable.LinkedHashMap.empty[String, String]
    var i   = 0
    val n   = payload.length
    while i < n do
      while i < n && payload.charAt(i).isWhitespace do i += 1
      if i < n then
        val eq = payload.indexOf('=', i)
        if eq < 0 then i = n
        else
          val key = payload.substring(i, eq)
          var j   = eq + 1
          val v =
            if j < n && payload.charAt(j) == '"' then
              val close = payload.indexOf('"', j + 1)
              if close < 0 then { val s = payload.substring(j + 1); j = n; s }
              else { val s = payload.substring(j + 1, close); j = close + 1; s }
            else
              val start = j
              while j < n && !payload.charAt(j).isWhitespace do j += 1
              payload.substring(start, j)
          if key.nonEmpty && !key.exists(_.isWhitespace) then out(key) = v
          i = j
    out.toMap
