package balticporter.core

/** Lexes every comment out of a Java source file (skipping string/char literals), independent of
  * Spoon's attachment heuristics — for the comment-preservation invariant. Lives in `core` since
  * both the BIR and TIR paths need the same answer. Carries each comment's OFFSET: the file-leading
  * harvest needs positional truth (a parser loses one of two leading blocks, `ENGINE-LIMITS.md` V3),
  * and `TriviaCheck` needs a real position rather than `indexOf`'s first-occurrence match. */
object CommentScanner:

  /** one comment, with the offset it starts at. `end` is derived rather than stored: the text is
    * the verbatim slice, so a second field could disagree with it. */
  final case class At(trivia: Trivia, start: Int):
    def end: Int          = start + trivia.text.length
    def kind: TriviaKind  = trivia.kind
    def text: String      = trivia.text
    /** 1-based line of the comment's first character. */
    def line(source: String): Int = source.substring(0, math.min(start, source.length)).count(_ == '\n') + 1

  def scan(source: String): List[Trivia] = scanAt(source).map(_.trivia)

  def scanAt(source: String): List[At] =
    val out = List.newBuilder[At]
    var i = 0
    val n = source.length
    while i < n do
      source.charAt(i) match
        case '/' if i + 1 < n && source.charAt(i + 1) == '/' =>
          val end0 = source.indexOf('\n', i)
          val end = if end0 < 0 then n else end0
          out += At(Trivia(TriviaKind.Line, source.substring(i, end)), i)
          i = end
        case '/' if i + 1 < n && source.charAt(i + 1) == '*' =>
          val close = source.indexOf("*/", i + 2)
          val end = if close < 0 then n else close + 2
          val kind =
            if i + 2 < n && source.charAt(i + 2) == '*' && !(i + 3 < n && source.charAt(i + 3) == '/') then
              TriviaKind.Javadoc
            else TriviaKind.Block
          out += At(Trivia(kind, source.substring(i, end)), i)
          i = end
        case '"' =>
          i += 1
          var done = false
          while i < n && !done do
            source.charAt(i) match
              case '\\' => i += 2
              case '"'  => i += 1; done = true
              case _    => i += 1
        case '\'' =>
          i += 1
          var done = false
          while i < n && !done do
            source.charAt(i) match
              case '\\' => i += 2
              case '\'' => i += 1; done = true
              case _    => i += 1
        case _ =>
          i += 1
    out.result()

  /** The offset of the first character a JAVA COMPILER would read as code — whitespace and
    * comments skipped, exactly as a scanner skips them. The whole definition of "file-leading": a
    * comment is the file's own iff no code precedes it. Not "before `package`" — a comment
    * containing the word `package` would defeat a token search. */
  def firstCodeOffset(source: String): Int =
    val n  = source.length
    val cs = scanAt(source).iterator.buffered
    var i  = 0
    var at = -1
    while at < 0 && i < n do
      if source.charAt(i).isWhitespace then i += 1
      else
        while cs.hasNext && cs.head.start < i do cs.next()
        if cs.hasNext && cs.head.start == i then i = cs.next().end
        else at = i
    if at < 0 then n else at
