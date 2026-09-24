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

  /** Index of function/method names to their enclosing object, built from the reference tree. Used to qualify unresolved callee identifiers: `makeSpan(...)` becomes `BuildCommon.makeSpan(...)`.
    */
  final case class CalleeIndex(
    byName: Map[String, List[String]]
  ):
    /** Resolve a simple function name to its fully qualified form. Returns Right(qualified) on a unique match, Left(reason) on ambiguity or absence.
      */
    def resolve(simpleName: String): Either[String, String] =
      byName.get(simpleName) match
        case None | Some(Nil)       => Left("callee-not-found")
        case Some(enclosing :: Nil) => Right(s"$enclosing.$simpleName")
        case Some(multiple)         => Left("callee-ambiguous")

  object CalleeIndex:
    val empty: CalleeIndex = CalleeIndex(Map.empty)

  /** Index of class/object member names, built from the reference tree. Used to validate property access on typed parameters.
    */
  final case class MemberIndex(
    byType: Map[String, Set[String]]
  ):
    /** True when the given type declares the given member name. */
    def hasMember(typeName: String, memberName: String): Boolean =
      byType.get(typeName).exists(_.contains(memberName))

    /** True when the type is known (appears in the index). */
    def knowsType(typeName: String): Boolean = byType.contains(typeName)

  object MemberIndex:
    val empty: MemberIndex = MemberIndex(Map.empty)

  /** Constructor parameter schema for a class, parsed from the reference tree. Used to construct typed objects from JS object literals.
    */
  final case class CtorParam(name: String, tpe: String, hasDefault: Boolean)

  final case class ConstructorSchema(
    byType: Map[String, List[CtorParam]]
  ):
    def get(typeName: String): Option[List[CtorParam]] = byType.get(typeName)

  object ConstructorSchema:
    val empty: ConstructorSchema = ConstructorSchema(Map.empty)

  /** Index of enum member names by their declared value, parsed from enum definitions in the reference tree. Maps `(EnumTypeName, "stringValue")` to the Scala member name.
    */
  final case class EnumIndex(
    byValue: Map[(String, String), String]
  ):
    def resolve(typeName: String, value: String): Option[String] =
      byValue.get((typeName, value))

  object EnumIndex:
    val empty: EnumIndex = EnumIndex(Map.empty)

  /** Parse all reference Scala files under a directory and build the indices. */
  def buildIndices(sources: List[(String, String)]): (CalleeIndex, MemberIndex, ConstructorSchema, EnumIndex) =
    val callees  = mutable.Map.empty[String, mutable.ListBuffer[String]]
    val members  = mutable.Map.empty[String, mutable.Set[String]]
    val ctors    = mutable.Map.empty[String, List[CtorParam]]
    val enumVals = mutable.Map.empty[(String, String), String]

    for (objectName, source) <- sources do
      val logicalLines = joinMultiLineSignatures(source)
      // Collect method names for callee index
      val defNamePattern = """^\s{2}(?:private\s+|protected\s+)?def\s+(\w+)""".r
      for line <- logicalLines do
        defNamePattern.findFirstMatchIn(line).foreach { m =>
          callees.getOrElseUpdate(m.group(1), mutable.ListBuffer.empty) += objectName
        }
      // Collect module-level val/lazy val/var names for the callee index,
      // so that a TS module-level `const X = [...]` referenced from a method
      // resolves through the same index as a function call.
      val valNamePattern = """^\s{2}(?:private\s+|protected\s+)?(?:lazy\s+)?(?:val|var)\s+(\w+)""".r
      for line <- source.linesIterator do
        valNamePattern.findFirstMatchIn(line).foreach { m =>
          callees.getOrElseUpdate(m.group(1), mutable.ListBuffer.empty) += objectName
        }

      // Collect class constructors by joining multi-line declarations
      val classLines = joinClassDeclarations(source)
      for line <- classLines do
        parseCaseClassLine(line).foreach { case (className, params) =>
          ctors(className) = params
          val memberSet = members.getOrElseUpdate(className, mutable.Set.empty)
          params.foreach(p => memberSet += p.name)
        }

      // Collect enum definitions and their members
      parseEnumMembers(source, members, enumVals)

    (
      CalleeIndex(callees.map { case (k, v) => k -> v.distinct.toList }.toMap),
      MemberIndex(members.map { case (k, v) => k -> v.toSet }.toMap),
      ConstructorSchema(ctors.toMap),
      EnumIndex(enumVals.toMap)
    )

  /** Parse enum definitions and collect their members with string values. */
  private def parseEnumMembers(
    source:   String,
    members:  mutable.Map[String, mutable.Set[String]],
    enumVals: mutable.Map[(String, String), String]
  ): Unit =
    val enumStart = """^\s*enum\s+(\w+)""".r
    val caseLine  = """^\s+case\s+(\w+)\s+extends\s+\w+\("([^"]*)"\)""".r
    var currentEnum: String = null

    for line <- source.linesIterator do
      enumStart.findFirstMatchIn(line).foreach { m =>
        currentEnum = m.group(1)
        members.getOrElseUpdate(currentEnum, mutable.Set.empty)
      }
      if currentEnum != null then
        caseLine.findFirstMatchIn(line).foreach { m =>
          val memberName = m.group(1)
          val strValue   = m.group(2)
          members.getOrElseUpdate(currentEnum, mutable.Set.empty) += memberName
          enumVals((currentEnum, strValue)) = memberName
        }
        if line.trim == "}" then currentEnum = null

  /** Join multi-line class declarations so constructors with wrapped parameters appear on a single logical line.
    */
  private def joinClassDeclarations(source: String): List[String] =
    val result     = mutable.ListBuffer.empty[String]
    val classStart = """^\s*(?:final\s+)?(?:case\s+)?class\s+""".r
    var accum: StringBuilder = null
    var depth = 0

    for line <- source.linesIterator do
      if accum != null then
        accum.append(" ").append(line.trim)
        for ch <- line do
          ch match
            case '(' => depth += 1
            case ')' => depth -= 1
            case _   => ()
        if depth <= 0 then
          result += accum.toString
          accum = null
          depth = 0
      else if classStart.findFirstIn(line).isDefined then
        var d = 0
        for ch <- line do
          ch match
            case '(' => d += 1
            case ')' => d -= 1
            case _   => ()
        if d <= 0 then result += line
        else
          accum = new StringBuilder(line)
          depth = d
      else result += line

    if accum != null then result += accum.toString
    result.toList

  /** Parse a `final case class X(params)` or `class X(params)` line. */
  private def parseCaseClassLine(line: String): Option[(String, List[CtorParam])] =
    val pattern = """(?:final\s+)?(?:case\s+)?class\s+(\w+)(?:\[.*?\])?\s*\(""".r
    pattern.findFirstMatchIn(line).flatMap { m =>
      val name       = m.group(1)
      val afterParen = line.substring(m.end)
      // Extract the balanced content between the opening ( and its matching )
      var depth = 1
      var i     = 0
      while i < afterParen.length && depth > 0 do
        afterParen.charAt(i) match
          case '(' => depth += 1
          case ')' => depth -= 1
          case _   => ()
        i += 1
      if depth == 0 then
        val paramStr = afterParen.substring(0, i - 1)
        val params   = parseCtorParams(paramStr)
        if params.nonEmpty then Some((name, params)) else None
      else None
    }

  private def parseCtorParams(paramStr: String): List[CtorParam] =
    if paramStr.trim.isEmpty then return Nil
    val params  = mutable.ListBuffer.empty[CtorParam]
    val current = new StringBuilder
    var depth   = 0

    for ch <- paramStr do
      ch match
        case '(' | '[' | '{'   => depth += 1; current.append(ch)
        case ')' | ']' | '}'   => depth -= 1; current.append(ch)
        case ',' if depth == 0 =>
          parseCtorOneParam(current.toString.trim).foreach(params += _)
          current.clear()
        case _ => current.append(ch)

    if current.nonEmpty then parseCtorOneParam(current.toString.trim).foreach(params += _)
    params.toList

  private def parseCtorOneParam(param: String): Option[CtorParam] =
    val trimmed = param.trim.stripPrefix("var ").stripPrefix("val ").trim
    if trimmed.startsWith("using ") || trimmed.startsWith("implicit ") then return None
    val colonIdx = trimmed.indexOf(':')
    if colonIdx < 0 then return None
    val name       = trimmed.substring(0, colonIdx).trim
    val rest       = trimmed.substring(colonIdx + 1).trim
    val hasDefault = rest.contains("=")
    val tpe        = stripDefault(rest)
    if name.nonEmpty && tpe.nonEmpty then Some(CtorParam(cleanParamName(name), tpe, hasDefault))
    else None

  /** Parse a Scala source file and extract all method signatures from the top-level object.
    *
    * Handles single-line and multi-line signatures. A multi-line signature is recognized when a `def` line has unbalanced parentheses.
    */
  def parseFile(objectName: String, source: String): List[(String, MethodSig)] =
    val results      = mutable.ListBuffer.empty[(String, MethodSig)]
    val logicalLines = joinMultiLineSignatures(source)
    val defPattern   =
      """^\s{2}(?:private\s+|protected\s+)?def\s+(\w+)\s*(?:\[.*?\])?\s*\((.*)\)\s*:\s*(.+?)\s*=\s*.*$""".r
    val defNoParamsPattern =
      """^\s{2}(?:private\s+|protected\s+)?def\s+(\w+)\s*:\s*(.+?)\s*=\s*.*$""".r
    val defMultiParamPattern =
      """^\s{2}(?:private\s+|protected\s+)?def\s+(\w+)\s*(?:\[.*?\])?\s*\((.*?)\)\s*\(.*\)\s*:\s*(.+?)\s*=\s*.*$""".r

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
