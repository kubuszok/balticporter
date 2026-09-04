package balticporter.tir

/** The ONE `k=v` payload grammar this engine writes — space-separated pairs, sorted, whitespace
  * values quoted (CLAUDE.md §4.575). Also the port map's `shape` column (`DESIGN.md` §8.3); lives
  * in `api` so both consumers delegate rather than restating it (§4.56). Unquoted whitespace
  * truncates a value (measured: 594 notes reported unbacked); [[safe]] SPACES delimiter characters
  * apart rather than rejecting, since a value carrying `/*`/`*/` could swallow the rest of the file. */
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

  /** …and back. Unknown keys are kept: a payload from a NEWER engine degrades to "I do not
    * understand this key", never a parse failure (`DESIGN.md` §8.3). A malformed token (no `=`, an
    * unclosed quote) is skipped, not thrown — one bad pair must not cost the row. */
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
