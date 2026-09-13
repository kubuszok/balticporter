package balticporter.frontend.ts.dedicated

import scala.collection.mutable

/** Extracts semantic actions from Mermaid jison grammar files and maps them
  * to Scala Db method calls for cross-checking against the hand-ported parsers.
  *
  * Jison grammars contain two sections:
  *   1. Lexer rules (`%lex ... /lex`) — token definitions
  *   2. Grammar rules (`%% ... `) — production rules with semantic actions
  *
  * The semantic actions call `yy.*` methods which map to the diagram's Db
  * object (e.g., `yy.addVertex($1, $2)` → `db.addVertex(id, label)`).
  */
object JisonActionExtractor:

  /** A production rule extracted from a jison grammar. */
  final case class Production(
      ruleName: String,
      alternative: String,
      action: String,
  )

  /** A `yy.*` call extracted from a semantic action. */
  final case class DbCall(
      methodName: String,
      argCount: Int,
      rawArgs: List[String],
      production: String,
  )

  /** Summary of a jison grammar analysis. */
  final case class GrammarSummary(
      diagramType: String,
      productionCount: Int,
      actionCount: Int,
      dbCalls: List[DbCall],
      dbMethodCounts: Map[String, Int],
      constants: Map[String, List[String]],
  )

  /** Parse a jison grammar file and extract semantic actions.
    *
    * @param source the jison grammar file contents
    * @param diagramType name of the diagram (e.g., "flowchart", "er")
    */
  def extract(source: String, diagramType: String): GrammarSummary =
    val lines = source.split("\n").toList

    // Find the grammar section (after the second %%)
    val grammarStart = findGrammarStart(lines)
    val grammarLines = if grammarStart >= 0 then lines.drop(grammarStart) else Nil

    val productions = extractProductions(grammarLines)
    val dbCalls = productions.flatMap(extractDbCalls)

    val methodCounts = dbCalls
      .groupBy(_.methodName)
      .map { case (name, calls) => name -> calls.size }

    val constants = extractConstants(source)

    GrammarSummary(
      diagramType = diagramType,
      productionCount = productions.size,
      actionCount = productions.count(_.action.nonEmpty),
      dbCalls = dbCalls,
      dbMethodCounts = methodCounts,
      constants = constants,
    )

  /** Map jison `yy.*` method names to the Scala Db method equivalents. */
  val jisonToScalaMethodMap: Map[String, String] = Map(
    // Flowchart
    "addVertex"             -> "addVertex",
    "addLink"               -> "addLink",
    "addSubGraph"           -> "addSubGraph",
    "setDirection"          -> "setDirection",
    "setClass"              -> "setClass",
    "setLink"               -> "setLink",
    "setClickEvent"         -> "setClickEvent",
    "setTooltip"            -> "setTooltip",
    "updateLink"            -> "updateLink",
    "updateLinkInterpolate" -> "updateLinkInterpolate",
    "addClass"              -> "addClass",
    "decorateNode"          -> "decorateNode",
    // Sequence
    "addSignal"             -> "addSignal",
    "addActor"              -> "addActor",
    "addNote"               -> "addNote",
    "parseMessage"          -> "parseMessage",
    "setAccTitle"           -> "setAccTitle",
    "setAccDescription"     -> "setAccDescription",
    "setDiagramTitle"       -> "setDiagramTitle",
    // ER
    "addEntity"             -> "addEntity",
    "addAttributes"         -> "addAttributes",
    "addRelationship"       -> "addRelationship",
    // Class
    "addClass"              -> "addClass",
    "addAnnotation"         -> "addAnnotation",
    "addMember"             -> "addMember",
    "addRelation"           -> "addRelation",
    "setClickEvent"         -> "setClickEvent",
    "setCssClass"           -> "setCssClass",
    "setLink"               -> "setLink",
    "setTooltip"            -> "setTooltip",
    "setDirection"          -> "setDirection",
    "addNamespace"          -> "addNamespace",
    // State
    "addState"              -> "addState",
    "addRelation"           -> "addRelation",
    "addDescription"        -> "addDescription",
    "setDirection"          -> "setDirection",
    "getDividerId"          -> "getDividerId",
    // Gantt
    "addTask"               -> "addTask",
    "addSection"            -> "addSection",
    "setDateFormat"         -> "setDateFormat",
    "setAxisFormat"         -> "setAxisFormat",
    "setTodayMarker"        -> "setTodayMarker",
    "setExcludes"           -> "setExcludes",
    "setIncludes"           -> "setIncludes",
    "setWeekday"            -> "setWeekday",
    // Git
    "commit"                -> "commit",
    "branch"                -> "branch",
    "merge"                 -> "merge",
    "checkout"              -> "checkout",
    "cherryPick"            -> "cherryPick",
    // Mindmap
    "addNode"               -> "addNode",
    "setIcon"               -> "setIcon",
    "setClass"              -> "setClass",
    // Pie
    "addSection"            -> "addSection",
    "setShowData"           -> "setShowData",
    // C4
    "addPersonOrSystem"     -> "addPersonOrSystem",
    "addContainer"          -> "addContainer",
    "addComponent"          -> "addComponent",
    "addRel"                -> "addRel",
    "setC4Type"             -> "setC4Type",
    // Requirement
    "addRequirement"        -> "addRequirement",
    "addElement"            -> "addElement",
    // Quadrant
    "addPoint"              -> "addPoint",
    "setQuadrant1Text"      -> "setQuadrant1Text",
    "setQuadrant2Text"      -> "setQuadrant2Text",
    "setQuadrant3Text"      -> "setQuadrant3Text",
    "setQuadrant4Text"      -> "setQuadrant4Text",
    "setXAxisLeftText"      -> "setXAxisLeftText",
    "setXAxisRightText"     -> "setXAxisRightText",
    "setYAxisTopText"       -> "setYAxisTopText",
    "setYAxisBottomText"    -> "setYAxisBottomText",
    // Sankey
    "findOrCreateNode"      -> "findOrCreateNode",
    // XY Chart
    "setXAxisTitle"         -> "setXAxisTitle",
    "setYAxisTitle"         -> "setYAxisTitle",
    "setXAxisBand"          -> "setXAxisBand",
    "setXAxisRangeData"     -> "setXAxisRangeData",
    "setYAxisRangeData"     -> "setYAxisRangeData",
    // Block
    "setHierarchy"          -> "setHierarchy",
    "typeStr2Type"          -> "typeStr2Type",
    "generateId"            -> "generateId",
    "edgeStrToEdgeData"     -> "edgeStrToEdgeData",
    // C4 extended
    "addContainerBoundary"  -> "addContainerBoundary",
    "addDeploymentNode"     -> "addDeploymentNode",
    "addPersonOrSystemBoundary" -> "addPersonOrSystemBoundary",
    "popBoundaryParseStack" -> "popBoundaryParseStack",
    "setTitle"              -> "setTitle",
    "updateElStyle"         -> "updateElStyle",
    "updateLayoutConfig"    -> "updateLayoutConfig",
    "updateRelStyle"        -> "updateRelStyle",
    // Class extended
    "addClassesToNamespace" -> "addClassesToNamespace",
    "addMembers"            -> "addMembers",
    "addNote"               -> "addNote",
    "cleanupLabel"          -> "cleanupLabel",
    "setClassLabel"         -> "setClassLabel",
    "setCssStyle"           -> "setCssStyle",
    // Flowchart extended
    "destructLink"          -> "destructLink",
    // Gantt extended
    "enableInclusiveEndDates" -> "enableInclusiveEndDates",
    "setTickInterval"       -> "setTickInterval",
    "setWeekend"            -> "setWeekend",
    "TopAxis"               -> "topAxis",
    // Git extended
    "setOptions"            -> "setOptions",
    // Mindmap extended
    "getType"               -> "getType",
    // Requirement extended
    "setNewReqId"           -> "setNewReqId",
    "setNewReqText"         -> "setNewReqText",
    "setNewReqRisk"         -> "setNewReqRisk",
    "setNewReqVerifyMethod" -> "setNewReqVerifyMethod",
    "setNewElementType"     -> "setNewElementType",
    "setNewElementDocRef"   -> "setNewElementDocRef",
    // Sequence extended
    "apply"                 -> "apply",
    // State extended
    "setRootDoc"            -> "setRootDoc",
    "trimColon"             -> "trimColon",
    // Timeline extended
    "addEvent"              -> "addEvent",
    // XY Chart extended
    "setBarData"            -> "setBarData",
    "setLineData"           -> "setLineData",
    "setOrientation"        -> "setOrientation",
    // Common
    "getLogger"             -> "getLogger",
    "getCommonDb"           -> "getCommonDb",
    "parseBoxData"          -> "parseBoxData",
    "lex"                   -> "lex",
  )

  /** Generate a Scala method call from a jison `yy.*` call.
    *
    * Translates `$1`, `$2`, etc. to Scala-friendly parameter names
    * and maps the method name to its Scala equivalent.
    */
  def toScalaCall(call: DbCall): String =
    val scalaMethod = jisonToScalaMethodMap.getOrElse(call.methodName, call.methodName)
    val scalaArgs = call.rawArgs.map(translateJisonArg)
    s"db.$scalaMethod(${scalaArgs.mkString(", ")})"

  /** Translate a jison argument (`$1`, `$2`, `$$`, `$name`) to Scala. */
  private def translateJisonArg(arg: String): String =
    arg.trim match
      case s if s.matches("""\$\d+""") =>
        val idx = s.drop(1).toInt
        s"p$idx"
      case "$$" => "result"
      case s if s.startsWith("$") => s"p_${s.drop(1)}"
      case s if s.matches("""'[^']*'""") => s.replace("'", "\"")
      case s if s.matches(""""[^"]*"""") => s
      case s if s.matches("""\d+""") => s
      case s if s.matches("""true|false|null""") => s
      case s => s"/* $s */"

  /** Format a summary for console output. */
  def formatSummary(summary: GrammarSummary): String =
    val sb = new StringBuilder
    sb.append(s"=== ${summary.diagramType} ===\n")
    sb.append(s"Productions: ${summary.productionCount}, with actions: ${summary.actionCount}\n")
    sb.append(s"Db calls: ${summary.dbCalls.size}\n")
    sb.append(s"\nDb method usage:\n")
    for (method, count) <- summary.dbMethodCounts.toList.sortBy(-_._2) do
      val scalaEquiv = jisonToScalaMethodMap.getOrElse(method, s"??? ($method)")
      sb.append(f"  $method%-30s -> $scalaEquiv%-30s ($count%d)\n")
    if summary.constants.nonEmpty then
      sb.append(s"\nConstants:\n")
      for (prefix, values) <- summary.constants do
        sb.append(s"  $prefix: ${values.take(5).mkString(", ")}${if values.size > 5 then "..." else ""}\n")
    sb.toString

  /** Analyze all jison grammars in a directory.
    *
    * @param jisonDir root directory containing jison files
    */
  def analyzeAll(jisonDir: java.nio.file.Path): List[GrammarSummary] =
    import java.nio.file.{Files, Path}
    val result = mutable.ListBuffer.empty[GrammarSummary]
    Files.walk(jisonDir).forEach { path =>
      if path.toString.endsWith(".jison") then
        val source = new String(Files.readAllBytes(path))
        val name = path.getFileName.toString.stripSuffix(".jison")
        result += extract(source, name)
    }
    result.toList.sortBy(_.diagramType)

  /** Format a combined summary table for all grammars. */
  def formatCombinedTable(summaries: List[GrammarSummary]): String =
    val sb = new StringBuilder
    sb.append(f"${"Diagram"}%-25s ${"Prods"}%6s ${"Actions"}%8s ${"Db Calls"}%9s ${"Methods"}%8s\n")
    sb.append("-" * 60)
    sb.append("\n")
    var totalProds = 0; var totalActions = 0; var totalCalls = 0
    val allMethods = mutable.Set.empty[String]
    for s <- summaries do
      sb.append(f"${s.diagramType}%-25s ${s.productionCount}%6d ${s.actionCount}%8d ${s.dbCalls.size}%9d ${s.dbMethodCounts.size}%8d\n")
      totalProds += s.productionCount
      totalActions += s.actionCount
      totalCalls += s.dbCalls.size
      allMethods ++= s.dbMethodCounts.keys
    sb.append("-" * 60)
    sb.append("\n")
    sb.append(f"${"TOTAL"}%-25s ${totalProds}%6d ${totalActions}%8d ${totalCalls}%9d ${allMethods.size}%8d\n")
    sb.toString

  // --------------------------------------------------------------------------
  // Private parsing helpers
  // --------------------------------------------------------------------------

  /** Find the start of the grammar section (the second `%%`). */
  private def findGrammarStart(lines: List[String]): Int =
    var found = 0
    for (line, idx) <- lines.zipWithIndex do
      if line.trim == "%%" || line.trim.startsWith("%% ") || line.trim.startsWith("%%/") then
        found += 1
        if found == 2 then return idx + 1
    -1

  /** Extract production rules and their semantic actions from grammar lines. */
  private def extractProductions(lines: List[String]): List[Production] =
    val result = mutable.ListBuffer.empty[Production]
    var currentRule = ""
    var inAction = false
    val actionBuf = new StringBuilder
    var braceDepth = 0
    var currentAlt = ""

    for line <- lines do
      val trimmed = line.trim

      // Skip empty lines and comments
      if trimmed.isEmpty || trimmed.startsWith("//") then ()
      // New rule definition
      else if !trimmed.startsWith("|") && !trimmed.startsWith("{") &&
              !trimmed.startsWith("}") && !trimmed.startsWith(";") &&
              !inAction && trimmed.endsWith(":") && !trimmed.contains("'") then
        currentRule = trimmed.stripSuffix(":").trim
      else if !inAction then
        // Check for inline action { ... }
        val actionStart = trimmed.indexOf('{')
        if actionStart >= 0 then
          currentAlt = trimmed.substring(0, actionStart).trim
          inAction = true
          actionBuf.clear()
          braceDepth = 0
          var i = actionStart
          while i < trimmed.length do
            val ch = trimmed(i)
            if ch == '{' then braceDepth += 1
            else if ch == '}' then
              braceDepth -= 1
              if braceDepth == 0 then
                result += Production(currentRule, currentAlt, actionBuf.toString.trim)
                inAction = false
                i = trimmed.length // break
            if inAction && !(ch == '{' && braceDepth == 1) then
              actionBuf.append(ch)
            i += 1
        else if trimmed.startsWith("|") then
          currentAlt = trimmed.stripPrefix("|").trim
        else if trimmed.startsWith(";") then
          currentRule = ""
      else
        // Continue collecting multi-line action
        for ch <- trimmed do
          if ch == '{' then braceDepth += 1
          else if ch == '}' then
            braceDepth -= 1
            if braceDepth == 0 then
              result += Production(currentRule, currentAlt, actionBuf.toString.trim)
              inAction = false
          if inAction then actionBuf.append(ch)
        if inAction then actionBuf.append('\n')

    result.toList

  /** Extract `yy.*` calls from a semantic action string. */
  private def extractDbCalls(prod: Production): List[DbCall] =
    if prod.action.isEmpty then return Nil

    val result = mutable.ListBuffer.empty[DbCall]
    val pattern = """yy\.(\w+)\s*\(""".r

    for m <- pattern.findAllMatchIn(prod.action) do
      val methodName = m.group(1)
      if methodName != "getLogger" then // skip logging calls
        val callStart = m.end
        val args = extractCallArgs(prod.action, callStart)
        result += DbCall(
          methodName = methodName,
          argCount = args.size,
          rawArgs = args,
          production = s"${prod.ruleName}: ${prod.alternative}",
        )

    result.toList

  /** Extract the arguments of a function call starting after the opening `(`. */
  private def extractCallArgs(source: String, startIdx: Int): List[String] =
    val args = mutable.ListBuffer.empty[String]
    val current = new StringBuilder
    var depth = 1
    var i = startIdx
    var inString = false
    var stringChar = ' '

    while i < source.length && depth > 0 do
      val ch = source(i)
      if inString then
        current.append(ch)
        if ch == stringChar && (i == 0 || source(i - 1) != '\\') then
          inString = false
      else
        ch match
          case '\'' | '"' =>
            inString = true
            stringChar = ch
            current.append(ch)
          case '(' | '[' | '{' =>
            depth += 1
            current.append(ch)
          case ')' | ']' | '}' =>
            depth -= 1
            if depth > 0 then current.append(ch)
            else if current.toString.trim.nonEmpty then
              args += current.toString.trim
          case ',' if depth == 1 =>
            if current.toString.trim.nonEmpty then
              args += current.toString.trim
            current.clear()
          case _ =>
            current.append(ch)
      i += 1

    args.toList

  /** Extract constant enums/objects referenced in actions (e.g., `yy.LINETYPE.*`). */
  private def extractConstants(source: String): Map[String, List[String]] =
    val pattern = """yy\.(\w+)\.(\w+)""".r
    val result = mutable.Map.empty[String, mutable.Set[String]]
    for m <- pattern.findAllMatchIn(source) do
      val prefix = m.group(1)
      val value = m.group(2)
      if prefix != "getLogger" && prefix != "lex" then
        result.getOrElseUpdate(prefix, mutable.Set.empty) += value
    result.map { case (k, v) => k -> v.toList.sorted }.toMap
