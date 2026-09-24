package balticporter.frontend.ts

import scala.collection.mutable

/** Reads a hand-written reference Scala file as a list of replaceable method bodies, by line ranges.
  *
  * Only a `def` or `private def` indented two spaces is offered for replacement; every other concrete `def` is listed by [[unofferedMembers]] so a report can still account for it.
  */
object ReferenceSkeleton:

  /** One method of the reference file: `signatureLine` is the `def` line, `bodyEndLine` the last body line (inclusive). */
  final case class ParsedMethod(
    name:          String,
    signatureLine: Int,
    bodyStartLine: Int,
    bodyEndLine:   Int,
    isPrivate:     Boolean
  )

  /** A concrete `def` the reader does not offer for replacement, with the line it is declared on and the reason the reader could not offer it. */
  final case class UnofferedMember(name: String, line: Int, reason: String)

  private val offeredDef      = """^\s{2}(private\s+)?def\s+(`?\w+`?)""".r
  private val anyDef          = """^\s*(?:(?:override|final|private|protected|inline|transparent|implicit|infix|open)(?:\[\w+\])?\s+)*def\s+(`?\w+`?)""".r
  private val modifierExtract = """(override|final|protected|inline|transparent|implicit|infix|open)(?:\[\w+\])?""".r

  /** Why `offeredDef` did not match a line that `anyDef` matched: wrong indentation or a modifier the reader does not accept. */
  private[ts] def classifyUnoffered(line: String): String =
    val indent = line.length - line.stripLeading().length
    if indent != 2 then "nested"
    else
      val stripped = line.stripLeading()
      val defIdx   = stripped.indexOf("def ")
      if defIdx <= 0 then "nested"
      else
        val beforeDef  = stripped.substring(0, defIdx)
        val nonPrivate = modifierExtract.findAllMatchIn(beforeDef).map(_.group(1)).toList
        if nonPrivate.isEmpty then "nested"
        else nonPrivate.head

  /** The replaceable methods of a reference file, in source order. */
  def findMethodBoundaries(lines: List[String]): List[ParsedMethod] =
    val result = mutable.ListBuffer.empty[ParsedMethod]
    var i      = 0

    while i < lines.size do
      offeredDef.findFirstMatchIn(lines(i)) match
        case Some(m) =>
          val isPrivate  = m.group(1) != null
          val name       = m.group(2).stripPrefix("`").stripSuffix("`")
          val sigEndLine = findSignatureEnd(lines, i)
          val eqIdx      = findEqualsInSignature(lines(sigEndLine))
          if eqIdx < 0 then
            // Abstract method (no `=`): skip, do not offer for replacement
            i += 1
          else
            val bodyEndLine = findBodyEnd(lines, sigEndLine)

            // The body starts on the signature's last line when code follows the `=`, otherwise on the next line.
            val bodyStartLine =
              val afterEq = lines(sigEndLine).substring(eqIdx + 1).trim
              if afterEq.nonEmpty && afterEq != "{" then sigEndLine
              else sigEndLine + 1

            result += ParsedMethod(name, i, bodyStartLine, bodyEndLine, isPrivate)
            i = bodyEndLine + 1

        case None =>
          i += 1

    result.toList

  /** Every concrete `def` outside the ranges of `offered` — a modifier or an indentation the reader does not match. A `def` inside an offered method's range is a local and is not listed; an abstract
    * `def` has no body and is not listed either.
    */
  def unofferedMembers(lines: List[String], offered: List[ParsedMethod]): List[UnofferedMember] =
    val covered = offered.flatMap(m => m.signatureLine to m.bodyEndLine).toSet
    lines.zipWithIndex.flatMap { case (line, idx) =>
      if covered.contains(idx) then None
      else
        anyDef.findFirstMatchIn(line).flatMap { m =>
          val closing = parametersCloseOn(lines, idx)
          if findEqualsInSignature(lines(closing)) >= 0 then Some(UnofferedMember(m.group(1).stripPrefix("`").stripSuffix("`"), idx, classifyUnoffered(line)))
          else None
        }
    }

  /** The first line from `startLine` on which every parameter list opened so far is closed; an abstract member has no `=` there. */
  private def parametersCloseOn(lines: List[String], startLine: Int): Int =
    var depth = 0
    var i     = startLine
    while i < lines.size do
      for ch <- lines(i) do
        ch match
          case '(' | '[' => depth += 1
          case ')' | ']' => depth -= 1
          case _         => ()
      if depth <= 0 then return i
      i += 1
    startLine

  /** The line on which a method's signature ends — the first line from `startLine` with balanced parentheses and a signature `=`; `startLine` itself when there is none.
    *
    * Stops as soon as parentheses balance out. If the balanced line has no `=`, the method is abstract and `startLine` is returned (so the caller can check for `=`).
    */
  def findSignatureEnd(lines: List[String], startLine: Int): Int =
    var depth = 0
    var i     = startLine
    while i < lines.size do
      val line = lines(i)
      for ch <- line do
        ch match
          case '(' | '[' => depth += 1
          case ')' | ']' => depth -= 1
          case _         => ()
      if depth <= 0 then
        // Parens are balanced: the signature must end here or has ended already
        if findEqualsInSignature(line) >= 0 then return i
        // No `=` with balanced parens and not on the first line means a multi-line
        // signature that closed its parens but continues to the `=` on the next line
        else if i > startLine then
          // Check the next non-blank line for `=`
          var j = i + 1
          while j < lines.size && lines(j).trim.isEmpty do j += 1
          if j < lines.size && findEqualsInSignature(lines(j)) >= 0 then return j
          else return startLine // abstract method
        // First line, no parens opened, no `=`: abstract single-line def
        else return startLine
      i += 1
    startLine

  /** The index of the `=` that ends a signature on `line`, or -1: the rightmost `=` preceded by whitespace that is not part of `==`, `!=`, `<=`, `>=` or `=>`. */
  def findEqualsInSignature(line: String): Int =
    var i = line.length - 1
    while i >= 1 do
      if line(i) == '=' then
        val prev = line(i - 1)
        val next = if i + 1 < line.length then line(i + 1) else ' '
        if (prev == ' ' || prev == '\t') && next != '>' && next != '=' then return i
      i -= 1
    -1

  /** The last line of a method body: the matching brace for a braced body, the end of the indented region otherwise.
    *
    * A body that opens with `{` uses brace matching when the brace IS the method body's outer delimiter: bare `{`, `boundary {`, `scala.util.boundary {`. Control-flow keywords (`try`, `if`, `while`,
    * `for`, `match`) introduce nested blocks where the brace is NOT the method body's outer delimiter, so those use indentation.
    */
  private def findBodyEnd(lines: List[String], sigEndLine: Int): Int =
    val sigLine = lines(sigEndLine)
    val eqIdx   = findEqualsInSignature(sigLine)
    val afterEq = if eqIdx >= 0 then sigLine.substring(eqIdx + 1).trim else ""

    if afterEq.endsWith("{") && isBodyBrace(afterEq) then findMatchingBrace(lines, sigEndLine, eqIdx + 1)
    else if afterEq.nonEmpty then findExpressionEnd(lines, sigEndLine)
    else if sigEndLine + 1 < lines.size then
      val nextLine = lines(sigEndLine + 1).trim
      if nextLine == "{" then findMatchingBrace(lines, sigEndLine + 1, 0)
      else findExpressionEnd(lines, sigEndLine + 1)
    else sigEndLine

  /** True when afterEq ending in `{` is a body-level brace, not a control-flow block. */
  private def isBodyBrace(afterEq: String): Boolean =
    val beforeBrace = afterEq.stripSuffix("{").trim
    if beforeBrace.isEmpty then true // bare `{`
    else
      // The last word before `{` determines the kind
      val lastWord = beforeBrace.split("\\s+|\\.|\\(|\\)").filter(_.nonEmpty).lastOption.getOrElse("")
      !controlFlowKeywords.contains(lastWord)

  private val controlFlowKeywords: Set[String] = Set(
    "try",
    "if",
    "else",
    "while",
    "for",
    "do",
    "match",
    "catch",
    "finally"
  )

  private def findMatchingBrace(lines: List[String], startLine: Int, startCol: Int): Int =
    var depth           = 0
    var i               = startLine
    var foundFirstBrace = false
    while i < lines.size do
      val line   = lines(i)
      val startJ = if i == startLine then startCol else 0
      var j      = startJ
      while j < line.length do
        val ch = line(j)
        // String and character literals are skipped; a multi-line string inside a body is not handled.
        if ch == '"' then
          j += 1
          while j < line.length && line(j) != '"' do
            if line(j) == '\\' then j += 1
            j += 1
        else if ch == '\'' then
          j += 1
          while j < line.length && line(j) != '\'' do
            if line(j) == '\\' then j += 1
            j += 1
        else if ch == '{' then
          depth += 1
          foundFirstBrace = true
        else if ch == '}' then
          depth -= 1
          if foundFirstBrace && depth == 0 then return i
        j += 1
      i += 1
    if startLine < lines.size - 1 then lines.size - 1 else startLine

  /** The end of an unbraced body: it runs while lines stay indented deeper than a top-level member, across blank lines that are followed by more of it. A line at the base indent still belongs to the
    * body when brace depth is positive (e.g. `} catch {` or the closing `}` of a try-catch).
    */
  private def findExpressionEnd(lines: List[String], startLine: Int): Int =
    val baseIndent      = 2
    var lastContentLine = startLine
    var braceDepth      = 0
    var i               = startLine

    // Count braces in the start line to track multi-block expressions
    for ch <- lines(startLine) do
      ch match
        case '{' => braceDepth += 1
        case '}' => braceDepth -= 1
        case _   => ()
    i = startLine + 1

    while i < lines.size do
      val line = lines(i)
      if line.trim.isEmpty then
        var nextNonEmpty = i + 1
        while nextNonEmpty < lines.size && lines(nextNonEmpty).trim.isEmpty do nextNonEmpty += 1
        if nextNonEmpty < lines.size then
          val nextLine   = lines(nextNonEmpty)
          val nextIndent = nextLine.takeWhile(_ == ' ').length
          if (nextIndent > baseIndent || braceDepth > 0) && !nextLine.trim.startsWith("def ") &&
            !nextLine.trim.startsWith("private def ") &&
            !nextLine.trim.startsWith("//") &&
            !nextLine.trim.startsWith("/*") &&
            !nextLine.trim.startsWith("val ") &&
            !nextLine.trim.startsWith("var ") &&
            !nextLine.trim.startsWith("class ") &&
            !nextLine.trim.startsWith("object ")
          then i = nextNonEmpty
          else return lastContentLine
        else return lastContentLine
      else
        val indent          = line.takeWhile(_ == ' ').length
        val depthBeforeLine = braceDepth
        // Track brace depth across the line
        for ch <- line do
          ch match
            case '{' => braceDepth += 1
            case '}' => braceDepth -= 1
            case _   => ()
        if indent <= baseIndent && braceDepth <= 0 then
          // Include the line only when the expression had open braces that
          // this line closes (depthBeforeLine > 0 means the expression opened
          // a brace the class did not). When depthBeforeLine is already 0, the
          // `}` closes the enclosing class, not the expression.
          if depthBeforeLine > 0 then return i
          else return lastContentLine
        else
          lastContentLine = i
          i += 1

    lastContentLine
