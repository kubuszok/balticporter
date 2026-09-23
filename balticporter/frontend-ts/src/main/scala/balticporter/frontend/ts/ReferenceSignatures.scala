package balticporter.frontend.ts

import scala.collection.mutable

/** Reads method signatures from hand-written Scala reference files to recover type information for source languages that lack static types (JavaScript, or Dart at `dynamic` sites).
  *
  * The reader is generic: it parses any Scala object's `def` lines into a signature lookup table. A consumer's builder supplies its own library-specific name mappings and hardcoded tables.
  */
object ReferenceSignatures:

  /** A method parameter with its name and type annotation. */
  final case class ParamSig(name: String, tpe: String)

  /** A complete method signature extracted from a reference file. */
  final case class MethodSig(
    methodName: String,
    params:     List[ParamSig],
    returnType: String
  )

  /** Type oracle: maps `(objectName, methodName)` to the reference signature.
    *
    * Lookups try exact match first, then case-insensitive on the method name.
    */
  final case class TypeOracle(
    methods: Map[(String, String), MethodSig]
  ):
    private lazy val byLower: Map[(String, String), MethodSig] =
      methods.map { case ((obj, name), sig) => ((obj, name.toLowerCase), sig) }

    private def lookup(objectName: String, methodName: String): Option[MethodSig] =
      methods.get((objectName, methodName)).orElse(byLower.get((objectName, methodName.toLowerCase)))

    def paramType(objectName: String, methodName: String, paramName: String): String =
      lookup(objectName, methodName) match
        case Some(sig) =>
          val lowerParam = paramName.toLowerCase
          sig.params.find(_.name == paramName).orElse(sig.params.find(_.name.toLowerCase == lowerParam)).map(_.tpe).getOrElse("Any")
        case None => "Any"

    def returnType(objectName: String, methodName: String): String =
      lookup(objectName, methodName).map(_.returnType).getOrElse("Any")

    def get(objectName: String, methodName: String): Option[MethodSig] =
      lookup(objectName, methodName)

  object TypeOracle:
    val empty: TypeOracle = TypeOracle(Map.empty)

    def fromEntries(entries: List[(String, MethodSig)]): TypeOracle =
      TypeOracle(entries.map { case (obj, sig) => (obj, sig.methodName) -> sig }.toMap)

  /** Parse a Scala source file and extract all method signatures from the top-level object.
    *
    * Handles single-line and multi-line signatures. A multi-line signature is recognized when a `def` line has unbalanced parentheses.
    */
  def parseFile(objectName: String, source: String): List[(String, MethodSig)] =
    val results      = mutable.ListBuffer.empty[(String, MethodSig)]
    val logicalLines = joinMultiLineSignatures(source)
    val defPattern   =
      """^\s{2}(?:private\s+|protected\s+)?def\s+(\w+)\s*(?:\[.*?\])?\s*\((.*)\)\s*:\s*(.+?)\s*=\s*(?:\{?\s*)?$""".r
    val defNoParamsPattern =
      """^\s{2}(?:private\s+|protected\s+)?def\s+(\w+)\s*:\s*(.+?)\s*=\s*(?:\{?\s*)?$""".r
    val defMultiParamPattern =
      """^\s{2}(?:private\s+|protected\s+)?def\s+(\w+)\s*(?:\[.*?\])?\s*\((.*?)\)\s*\(.*\)\s*:\s*(.+?)\s*=\s*(?:\{?\s*)?$""".r

    for line <- logicalLines do
      line match
        case defMultiParamPattern(name, paramStr, retType) =>
          results += ((objectName, MethodSig(name, parseParams(paramStr), retType.trim)))
        case defPattern(name, paramStr, retType) =>
          results += ((objectName, MethodSig(name, parseParams(paramStr), retType.trim)))
        case defNoParamsPattern(name, retType) =>
          results += ((objectName, MethodSig(name, Nil, retType.trim)))
        case _ => ()

    results.toList

  private def joinMultiLineSignatures(source: String): List[String] =
    val result   = mutable.ListBuffer.empty[String]
    val defStart = """^\s{2}(?:private\s+|protected\s+)?def\s+""".r
    var accumulator: StringBuilder = null
    var parenDepth = 0

    for line <- source.linesIterator do
      if accumulator != null then
        accumulator.append(" ")
        accumulator.append(line.trim)
        for ch <- line do
          ch match
            case '(' => parenDepth += 1
            case ')' => parenDepth -= 1
            case _   => ()
        if parenDepth <= 0 && (line.contains("=") || line.trim.endsWith("=")) then
          result += accumulator.toString
          accumulator = null
          parenDepth = 0
        else if parenDepth < 0 then
          result += accumulator.toString
          accumulator = null
          parenDepth = 0
      else if defStart.findFirstIn(line).isDefined then
        var depth = 0
        for ch <- line do
          ch match
            case '(' => depth += 1
            case ')' => depth -= 1
            case _   => ()
        if depth <= 0 then result += line
        else
          accumulator = new StringBuilder(line)
          parenDepth = depth
      else result += line

    if accumulator != null then result += accumulator.toString
    result.toList

  private def parseParams(paramStr: String): List[ParamSig] =
    if paramStr.trim.isEmpty then return Nil
    val params  = mutable.ListBuffer.empty[ParamSig]
    val current = new StringBuilder
    var depth   = 0

    for ch <- paramStr do
      ch match
        case '(' | '[' | '{' =>
          depth += 1
          current.append(ch)
        case ')' | ']' | '}' =>
          depth -= 1
          current.append(ch)
        case ',' if depth == 0 =>
          parseOneParam(current.toString.trim).foreach(params += _)
          current.clear()
        case _ =>
          current.append(ch)

    if current.nonEmpty then parseOneParam(current.toString.trim).foreach(params += _)
    params.toList

  private def parseOneParam(param: String): Option[ParamSig] =
    val trimmed = param.trim
    if trimmed.startsWith("using ") || trimmed.startsWith("implicit ") then return None
    val colonIdx =
      if trimmed.startsWith("`") then
        val endBacktick = trimmed.indexOf('`', 1)
        if endBacktick >= 0 then trimmed.indexOf(':', endBacktick + 1)
        else trimmed.indexOf(':')
      else trimmed.indexOf(':')
    if colonIdx < 0 then return None
    val name = trimmed.substring(0, colonIdx).trim
    val tpe  = stripDefault(trimmed.substring(colonIdx + 1).trim)
    if name.nonEmpty && tpe.nonEmpty then Some(ParamSig(cleanParamName(name), tpe))
    else None

  private def stripDefault(s: String): String =
    var depth    = 0
    var equalsAt = -1
    var i        = 0
    while i < s.length && equalsAt < 0 do
      s.charAt(i) match
        case '(' | '[' | '{'   => depth += 1
        case ')' | ']' | '}'   => depth -= 1
        case '=' if depth == 0 => equalsAt = i
        case _                 => ()
      i += 1
    if equalsAt >= 0 then s.substring(0, equalsAt).trim else s.trim

  private def cleanParamName(name: String): String =
    if name.startsWith("`") && name.endsWith("`") then name.substring(1, name.length - 1)
    else name
