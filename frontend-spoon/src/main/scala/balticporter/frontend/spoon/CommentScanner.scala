package balticporter.frontend.spoon

import balticporter.core.{Trivia, TriviaKind}

/** Lexes every comment out of a Java source file (skipping string/char literals).
  * Used for the comment-preservation invariant — independent of Spoon's attachment
  * heuristics, so a comment Spoon fails to attach is still caught if we drop it.
  */
object CommentScanner:

  def scan(source: String): List[Trivia] =
    val out = List.newBuilder[Trivia]
    var i = 0
    val n = source.length
    while i < n do
      source.charAt(i) match
        case '/' if i + 1 < n && source.charAt(i + 1) == '/' =>
          val end0 = source.indexOf('\n', i)
          val end = if end0 < 0 then n else end0
          out += Trivia(TriviaKind.Line, source.substring(i, end))
          i = end
        case '/' if i + 1 < n && source.charAt(i + 1) == '*' =>
          val close = source.indexOf("*/", i + 2)
          val end = if close < 0 then n else close + 2
          val kind =
            if i + 2 < n && source.charAt(i + 2) == '*' && !(i + 3 < n && source.charAt(i + 3) == '/') then
              TriviaKind.Javadoc
            else TriviaKind.Block
          out += Trivia(kind, source.substring(i, end))
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
