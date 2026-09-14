package balticporter.frontend.ts.dedicated

import balticporter.frontend.ts.{RastFile, RastNode, RastValue}
import java.nio.file.{Files, Path}
import scala.collection.mutable

/** Dedicated RAST-to-Scala emitter for KaTeX — a TypeScript math rendering
  * library.
  *
  * Five module categories:
  *   1. Data/config — pure constant maps and arrays
  *   2. Classes — Options, Token, ParseError, etc.
  *   3. Builder/DOM — domTree, buildCommon, buildHTML, etc.
  *   4. Parser/Lexer — Parser, Lexer, MacroExpander, macros
  *   5. Functions — 45 files following the `defineFunction({...})` pattern
  *
  * The emitter handles category 5 natively by extracting `defineFunction` calls
  * from RAST and emitting Scala `FunctionDef.defineFunction(FunctionDefSpec(...))`
  * registrations. Categories 1-4 use parity-derive against the reference port.
  */
object KaTeXEmitter:

  // --------------------------------------------------------------------------
  // defineFunction extraction (Category 5)
  // --------------------------------------------------------------------------

  /** A defineFunction call extracted from RAST. */
  final case class DefineFunctionCall(
      nodeType: String,
      names: List[String],
      numArgs: Int,
      numOptionalArgs: Int,
      allowedInText: Boolean,
      allowedInMath: Boolean,
      allowedInArgument: Boolean,
      infix: Boolean,
      primitive: Boolean,
      argTypes: List[String],
      handlerNode: Option[RastNode],
      htmlBuilderRef: Option[String],
      mathmlBuilderRef: Option[String],
      htmlBuilderNode: Option[RastNode] = None,
      mathmlBuilderNode: Option[RastNode] = None,
  )

  /** Extract all defineFunction calls from a KaTeX function RAST file. */
  def extractDefineFunctions(file: RastFile): List[DefineFunctionCall] =
    val result = mutable.ListBuffer.empty[DefineFunctionCall]
    for node <- file.nodes do
      if node.kind == "ExpressionStatement" then
        node.children.headOption match
          case Some(call) if call.kind == "CallExpression" =>
            val callee = call.children.headOption
            val calleeName = callee.flatMap(_.text).getOrElse("")
            if calleeName == "defineFunction" then
              call.children.find(_.kind == "ObjectLiteralExpression").foreach { objLit =>
                extractOneDefineFunction(objLit).foreach(result += _)
              }
          case _ => ()
    result.toList

  private def extractOneDefineFunction(objLit: RastNode): Option[DefineFunctionCall] =
    // Collect PropertyAssignment entries
    val props = objLit.children.filter(_.kind == "PropertyAssignment")
    val propMap = props.flatMap { pa =>
      val key = pa.children.headOption.flatMap(_.text)
      val value = pa.children.lift(1)
      key.zip(value)
    }.toMap

    // Also collect MethodDeclaration entries (handler({ctx}, args) { ... } syntax)
    val methodDecls = objLit.children.filter(_.kind == "MethodDeclaration")
    val methodMap = methodDecls.flatMap { md =>
      md.children.find(_.kind == "Identifier").flatMap(_.text).map(_ -> md)
    }.toMap

    val nodeType = propMap.get("type").flatMap(extractStringValue).getOrElse("")
    if nodeType.isEmpty then return None

    val names = propMap.get("names").map(extractStringArray).getOrElse(Nil)
    if names.isEmpty then return None

    val propsObj = propMap.get("props")
    val numArgs = propsObj.flatMap(extractPropInt("numArgs", _)).getOrElse(0)
    val numOptionalArgs = propsObj.flatMap(extractPropInt("numOptionalArgs", _)).getOrElse(0)
    val allowedInText = propsObj.flatMap(extractPropBool("allowedInText", _)).getOrElse(false)
    val allowedInMath = propsObj.flatMap(extractPropBool("allowedInMath", _)).getOrElse(true)
    val allowedInArgument = propsObj.flatMap(extractPropBool("allowedInArgument", _)).getOrElse(false)
    val infix = propsObj.flatMap(extractPropBool("infix", _)).getOrElse(false)
    val primitive = propsObj.flatMap(extractPropBool("primitive", _)).getOrElse(false)
    val argTypes = propsObj.map(extractArgTypes).getOrElse(Nil)

    // Handler: PropertyAssignment (ArrowFunction/FunctionExpression) or MethodDeclaration
    val handlerNode: Option[RastNode] = propMap.get("handler").orElse(
      methodMap.get("handler"))

    // ShorthandPropertyAssignment: { htmlBuilder } has child[0] = Identifier
    // PropertyAssignment: { htmlBuilder: expr } has child[1] = expr
    val shorthandNames = objLit.children
      .filter(_.kind == "ShorthandPropertyAssignment")
      .flatMap(_.children.headOption.flatMap(_.text))
      .toSet
    val htmlBuilderRef =
      if shorthandNames.contains("htmlBuilder") then Some("htmlBuilder")
      else propMap.get("htmlBuilder").flatMap(n =>
        if n.kind == "Identifier" then n.text else None)
    val mathmlBuilderRef =
      if shorthandNames.contains("mathmlBuilder") then Some("mathmlBuilder")
      else propMap.get("mathmlBuilder").flatMap(n =>
        if n.kind == "Identifier" then n.text else None)

    // Inline builder nodes (PropertyAssignment with function value, or MethodDeclaration)
    val htmlBuilderNode: Option[RastNode] =
      if htmlBuilderRef.isDefined then None  // shorthand/identifier reference, not inline
      else propMap.get("htmlBuilder").filter(n =>
        n.kind == "ArrowFunction" || n.kind == "FunctionExpression"
      ).orElse(methodMap.get("htmlBuilder"))
    val mathmlBuilderNode: Option[RastNode] =
      if mathmlBuilderRef.isDefined then None
      else propMap.get("mathmlBuilder").filter(n =>
        n.kind == "ArrowFunction" || n.kind == "FunctionExpression"
      ).orElse(methodMap.get("mathmlBuilder"))

    Some(DefineFunctionCall(
      nodeType = nodeType,
      names = names,
      numArgs = numArgs,
      numOptionalArgs = numOptionalArgs,
      allowedInText = allowedInText,
      allowedInMath = allowedInMath,
      allowedInArgument = allowedInArgument,
      infix = infix,
      primitive = primitive,
      argTypes = argTypes,
      handlerNode = handlerNode,
      htmlBuilderRef = htmlBuilderRef,
      mathmlBuilderRef = mathmlBuilderRef,
      htmlBuilderNode = htmlBuilderNode,
      mathmlBuilderNode = mathmlBuilderNode,
    ))

  // --------------------------------------------------------------------------
  // Function module emission (Category 5)
  // --------------------------------------------------------------------------

  /** Emit a Scala function registration module from a KaTeX function RAST file.
    *
    * Produces an object with a `register()` method containing
    * `FunctionDef.defineFunction(FunctionDefSpec(...))` calls.
    */
  def emitFunctionModule(
      file: RastFile,
      objectName: String,
  ): (String, FunctionEmitSummary) =
    val defs = extractDefineFunctions(file)
    val topLevelFns = extractTopLevelFunctions(file)
    var handlerTranslated = 0
    var handlerPartial = 0

    val sb = new StringBuilder
    sb.append(header(file.path, s"$objectName.scala"))
    sb.append("package ssg\npackage katex\npackage functions\n\n")
    sb.append("import lowlevel.Nullable\n")
    sb.append("import ssg.katex.parse._\n\n")
    sb.append(s"object $objectName {\n\n")

    // Emit top-level helper functions/constants
    for fn <- topLevelFns do
      val scalaName = camelCase(fn.name)
      val params = fn.params.map(p => s"${camelCase(p)}: Any").mkString(", ")
      sb.append(s"  private def $scalaName($params): Any =\n")
      sb.append(s"    ??? // RAST body: ${fn.bodyNodeKind}\n\n")

    // Emit register method
    sb.append("  def register(): Unit = {\n")
    for df <- defs do
      sb.append(s"    FunctionDef.defineFunction(\n")
      sb.append(s"      FunctionDefSpec(\n")
      sb.append(s"""        nodeType = "${df.nodeType}",\n""")
      sb.append(s"        names = Array(${df.names.map(n => "\"" + escapeString(n) + "\"").mkString(", ")}),\n")
      sb.append(s"        props = FunctionPropSpec(\n")
      sb.append(s"          numArgs = ${df.numArgs}")
      if df.numOptionalArgs != 0 then sb.append(s",\n          numOptionalArgs = ${df.numOptionalArgs}")
      if df.allowedInText then sb.append(",\n          allowedInText = true")
      if !df.allowedInMath then sb.append(",\n          allowedInMath = false")
      if df.allowedInArgument then sb.append(",\n          allowedInArgument = true")
      if df.infix then sb.append(",\n          infix = true")
      if df.primitive then sb.append(",\n          primitive = true")
      if df.argTypes.nonEmpty then
        val argTypeStrs = df.argTypes.map(t => s"ArgType.${capitalizeFirst(t)}")
        sb.append(s",\n          argTypes = Nullable(Array(${argTypeStrs.mkString(", ")}))")
      sb.append("\n        ),\n")

      // Handler — translate body from RAST where possible
      val handlerBody = extractFunctionBody(df.handlerNode)
      handlerBody match
        case Some(block) =>
          val entry = TerserEmitter.DefmethodEntry("_handler_", df.nodeType, List("context", "args", "optArgs"), block)
          val result = DefmethodBodyTranslator.translateBody(entry, Nil, "          ")
          if result.refusalCount == 0 then
            sb.append("        handler = Nullable { (context, args, optArgs) =>\n")
            sb.append(result.scalaBody)
            sb.append("        }")
            handlerTranslated += 1
          else
            sb.append(s"        handler = Nullable { (context, args, optArgs) =>\n")
            sb.append(s"          ??? // ${result.refusalCount} untranslated: ${result.refusalReasons.take(3).mkString(", ")}\n")
            sb.append("        }")
            handlerPartial += 1
        case None =>
          sb.append("        handler = Nullable { (context, args, optArgs) =>\n")
          sb.append("          ??? // no handler body in RAST\n")
          sb.append("        }")
          handlerPartial += 1

      // Builders — translate inline bodies, use references for shorthand
      df.htmlBuilderRef match
        case Some(ref) =>
          sb.append(s",\n        htmlBuilder = Nullable(${camelCase(ref)})")
        case None =>
          val builderBody = extractFunctionBody(df.htmlBuilderNode)
          builderBody match
            case Some(block) =>
              val entry = TerserEmitter.DefmethodEntry("_htmlBuilder_", df.nodeType, List("group", "options"), block)
              val result = DefmethodBodyTranslator.translateBody(entry, Nil, "          ")
              if result.refusalCount == 0 then
                sb.append(",\n        htmlBuilder = Nullable { (group, options) =>\n")
                sb.append(result.scalaBody)
                sb.append("        }")
              else
                sb.append(s",\n        htmlBuilder = Nullable { (group, options) =>\n")
                sb.append(s"          ??? // ${result.refusalCount} untranslated\n")
                sb.append("        }")
            case None =>
              sb.append(",\n        htmlBuilder = Nullable.Null")

      df.mathmlBuilderRef match
        case Some(ref) =>
          sb.append(s",\n        mathmlBuilder = Nullable(${camelCase(ref)})")
        case None =>
          val builderBody = extractFunctionBody(df.mathmlBuilderNode)
          builderBody match
            case Some(block) =>
              val entry = TerserEmitter.DefmethodEntry("_mathmlBuilder_", df.nodeType, List("group", "options"), block)
              val result = DefmethodBodyTranslator.translateBody(entry, Nil, "          ")
              if result.refusalCount == 0 then
                sb.append(",\n        mathmlBuilder = Nullable { (group, options) =>\n")
                sb.append(result.scalaBody)
                sb.append("        }")
              else
                sb.append(s",\n        mathmlBuilder = Nullable { (group, options) =>\n")
                sb.append(s"          ??? // ${result.refusalCount} untranslated\n")
                sb.append("        }")
            case None =>
              sb.append(",\n        mathmlBuilder = Nullable.Null")

      sb.append("\n      )\n")
      sb.append("    )\n\n")

    sb.append("  }\n")
    sb.append("}\n")

    val summary = FunctionEmitSummary(
      objectName = objectName,
      defineFunctionCount = defs.size,
      totalNames = defs.flatMap(_.names).size,
      topLevelFunctions = topLevelFns.size,
      nodeTypes = defs.map(_.nodeType).distinct,
      handlersTranslated = handlerTranslated,
      handlersPartial = handlerPartial,
    )

    (sb.toString, summary)

  final case class FunctionEmitSummary(
      objectName: String,
      defineFunctionCount: Int,
      totalNames: Int,
      topLevelFunctions: Int,
      nodeTypes: List[String],
      handlersTranslated: Int = 0,
      handlersPartial: Int = 0,
  )

  // --------------------------------------------------------------------------
  // Parity-derive emission (all categories)
  // --------------------------------------------------------------------------

  final case class ParityEmitSummary(
      moduleName: String,
      totalMethods: Int,
      matchedFromRast: Int,
      keptFromReference: Int,
      refusalCount: Int,
      matchDetails: List[(String, String)],
  )

  /** Patterns in a translated KaTeX RAST body that cannot compile in ssg-katex.
    *
    * Note: `setAttribute` is NOT blocked — in KaTeX it's a tree-node method,
    * not a browser DOM API.
    */
  private val katexUncompilablePatterns: List[String] = List(
    "document.",         // DOM API — ssg has no browser document
    "window.",           // DOM API — ssg has no browser window
    "console.",          // browser console
    "HTMLElement",       // DOM type
    "addEventListener",  // DOM event API
    "createElement",     // DOM creation API
    "querySelector",     // DOM query API
    "innerHTML",         // DOM property
    "DEFMETHOD(",        // Terser construct — not in KaTeX
  )

  private def containsKatexUncompilablePatterns(body: String): Boolean =
    katexUncompilablePatterns.exists(body.contains)

  /** Emit a KaTeX module using parity-derive: the reference file's structure
    * with RAST-translated bodies where a match exists and compiles.
    *
    * Uses the TerserCompressEmitter's findMethodBoundaries and body
    * replacement infrastructure. RAST bodies are translated using
    * DefmethodBodyTranslator (which handles general TS→Scala patterns).
    */
  def emitWithParity(
      rastFile: RastFile,
      referencePath: Path,
  ): (String, ParityEmitSummary) =
    val referenceSource = new String(Files.readAllBytes(referencePath))
    val lines = referenceSource.split("\n", -1).toList

    val methods = TerserCompressEmitter.findMethodBoundaries(lines)
    val rastBodies = buildTranslatedBodyMap(rastFile)
    val consumed = mutable.Map.empty[String, Int].withDefaultValue(0)
    val moduleName = referencePath.getFileName.toString.stripSuffix(".scala")

    val sb = new StringBuilder
    val matchDetails = mutable.ListBuffer.empty[(String, String)]
    var totalRefusals = 0

    var lineIdx = 0
    var methodIdx = 0

    while lineIdx < lines.size do
      if methodIdx < methods.size && lineIdx == methods(methodIdx).signatureLine then
        val method = methods(methodIdx)

        val idx = consumed(method.name)
        val usableRast = rastBodies.get(method.name).flatMap { entries =>
          if idx < entries.size then
            val (body, refusals) = entries(idx)
            if !containsKatexUncompilablePatterns(body) then Some((body, refusals))
            else None
          else None
        }

        usableRast match
          case Some((translatedBody, refusals)) =>
            consumed(method.name) = idx + 1
            val sigEndLineIdx = TerserCompressEmitter.findSignatureEnd(lines, method.signatureLine)
            for i <- method.signatureLine to sigEndLineIdx do
              val line = lines(i)
              if i == sigEndLineIdx then
                val eqIdx = TerserCompressEmitter.findEqualsInSignature(line)
                if eqIdx >= 0 then
                  sb.append(line.substring(0, eqIdx + 1))
                  sb.append("\n")
                else
                  sb.append(line)
                  sb.append("\n")
              else
                sb.append(line)
                sb.append("\n")

            sb.append(translatedBody)
            lineIdx = method.bodyEndLine + 1
            matchDetails += ((method.name, "rast"))
            totalRefusals += refusals

          case _ =>
            consumed(method.name) = idx + 1
            for i <- method.signatureLine to method.bodyEndLine do
              sb.append(lines(i))
              sb.append("\n")
            lineIdx = method.bodyEndLine + 1
            matchDetails += ((method.name, "reference"))

        methodIdx += 1
      else
        sb.append(lines(lineIdx))
        sb.append("\n")
        lineIdx += 1

    val matched = matchDetails.count(_._2 == "rast")
    val kept = matchDetails.count(_._2 == "reference")

    val summary = ParityEmitSummary(
      moduleName = moduleName,
      totalMethods = methods.size,
      matchedFromRast = matched,
      keptFromReference = kept,
      refusalCount = totalRefusals,
      matchDetails = matchDetails.toList,
    )

    (sb.toString, summary)

  /** Emit all core modules using parity-derive.
    *
    * @param loadRast function to load a RAST file from a resource path
    * @param katexRefRoot path to the ssg-katex source root
    * @param outDir output directory for emitted Scala files
    */
  def emitAllWithParity(
      loadRast: String => Option[RastFile],
      katexRefRoot: Path,
      outDir: Path,
  ): List[(KaTeXModule, ParityEmitSummary)] =
    Files.createDirectories(outDir)
    val results = mutable.ListBuffer.empty[(KaTeXModule, ParityEmitSummary)]

    for mod <- CoreModules do
      val refPath = katexRefRoot.resolve(mod.referenceSubPath)
      if Files.exists(refPath) then
        loadRast(mod.rastResource).foreach { rast =>
          val (source, summary) = emitWithParity(rast, refPath)
          val outFile = outDir.resolve(s"${mod.objectName}.scala")
          Files.writeString(outFile, source)
          results += ((mod, summary))
        }

    results.toList

  /** Emit all function modules using parity-derive where reference exists,
    * falling back to stub emission.
    *
    * @param loadRast function to load a RAST file from a resource path
    * @param katexRefRoot path to the ssg-katex source root
    * @param outDir output directory for emitted Scala files
    */
  def emitAllFunctionsWithParity(
      loadRast: String => Option[RastFile],
      katexRefRoot: Path,
      outDir: Path,
  ): List[(KaTeXModule, String, FunctionParitySummary)] =
    Files.createDirectories(outDir)
    val results = mutable.ListBuffer.empty[(KaTeXModule, String, FunctionParitySummary)]

    for mod <- FunctionModules do
      loadRast(mod.rastResource).foreach { rast =>
        val refPath = katexRefRoot.resolve(mod.referenceSubPath)
        val usedParity = Files.exists(refPath)
        val (source, paritySummary) = if usedParity then
          val (src, ps) = emitWithParity(rast, refPath)
          (src, FunctionParitySummary(mod.objectName, usedParity = true,
            totalMethods = ps.totalMethods, matchedFromRast = ps.matchedFromRast,
            keptFromReference = ps.keptFromReference))
        else
          val (src, fs) = emitFunctionModule(rast, mod.objectName)
          (src, FunctionParitySummary(mod.objectName, usedParity = false,
            totalMethods = 0, matchedFromRast = 0,
            keptFromReference = 0))

        val outFile = outDir.resolve(s"${mod.objectName}.scala")
        Files.writeString(outFile, source)
        results += ((mod, source, paritySummary))
      }

    results.toList

  final case class FunctionParitySummary(
      objectName: String,
      usedParity: Boolean,
      totalMethods: Int,
      matchedFromRast: Int,
      keptFromReference: Int,
  )

  /** Format a parity summary table for core modules. */
  def formatParitySummaryTable(summaries: List[ParityEmitSummary]): String =
    val sb = new StringBuilder
    sb.append(f"${"Module"}%-25s ${"Total"}%6s ${"RAST"}%6s ${"Ref"}%6s ${"Refusals"}%9s\n")
    sb.append("-" * 55)
    sb.append("\n")
    var tTotal = 0; var tRast = 0; var tRef = 0; var tRefusals = 0
    for s <- summaries do
      sb.append(f"${s.moduleName}%-25s ${s.totalMethods}%6d ${s.matchedFromRast}%6d ${s.keptFromReference}%6d ${s.refusalCount}%9d\n")
      tTotal += s.totalMethods; tRast += s.matchedFromRast; tRef += s.keptFromReference; tRefusals += s.refusalCount
    sb.append("-" * 55)
    sb.append("\n")
    sb.append(f"${"TOTAL"}%-25s ${tTotal}%6d ${tRast}%6d ${tRef}%6d ${tRefusals}%9d\n")
    val pctRast = if tTotal > 0 then (tRast * 100.0 / tTotal) else 0.0
    sb.append(f"\nRAST-derived bodies: $tRast/$tTotal (${pctRast}%.1f%%)\n")
    sb.toString

  /** Format a function parity summary table. */
  def formatFunctionParitySummaryTable(summaries: List[FunctionParitySummary]): String =
    val sb = new StringBuilder
    val withParity = summaries.filter(_.usedParity)
    val stubs = summaries.filterNot(_.usedParity)
    sb.append(s"Function modules: ${summaries.size} total\n")
    sb.append(s"  With parity: ${withParity.size}\n")
    sb.append(s"  Stub only: ${stubs.size}\n")
    if withParity.nonEmpty then
      val tTotal = withParity.map(_.totalMethods).sum
      val tRast = withParity.map(_.matchedFromRast).sum
      val pct = if tTotal > 0 then (tRast * 100.0 / tTotal) else 0.0
      sb.append(f"\nParity function methods: $tRast/$tTotal ($pct%.1f%%)\n")
    sb.toString

  // --------------------------------------------------------------------------
  // Module inventory
  // --------------------------------------------------------------------------

  /** Descriptor for a KaTeX module. */
  final case class KaTeXModule(
      category: String,
      rastResource: String,
      referenceSubPath: String,
      objectName: String,
  )

  val CoreModules: List[KaTeXModule] = List(
    // Category 2: Classes
    KaTeXModule("class", "/rast/katex/src/Options.rast.json",        "Options.scala",           "Options"),
    KaTeXModule("class", "/rast/katex/src/Token.rast.json",          "Token.scala",             "Token"),
    KaTeXModule("class", "/rast/katex/src/ParseError.rast.json",     "ParseError.scala",        "ParseError"),
    KaTeXModule("class", "/rast/katex/src/SourceLocation.rast.json", "SourceLocation.scala",    "SourceLocation"),
    KaTeXModule("class", "/rast/katex/src/Style.rast.json",          "Style.scala",             "Style"),
    KaTeXModule("class", "/rast/katex/src/Namespace.rast.json",      "Namespace.scala",         "Namespace"),
    KaTeXModule("class", "/rast/katex/src/Settings.rast.json",       "Settings.scala",          "Settings"),
    // Category 3: Builder/DOM
    KaTeXModule("builder", "/rast/katex/src/domTree.rast.json",       "tree/DomTree.scala",      "DomTree"),
    KaTeXModule("builder", "/rast/katex/src/mathMLTree.rast.json",    "tree/MathMLTree.scala",   "MathMLTree"),
    KaTeXModule("builder", "/rast/katex/src/buildCommon.rast.json",   "build/BuildCommon.scala",  "BuildCommon"),
    KaTeXModule("builder", "/rast/katex/src/buildHTML.rast.json",     "build/BuildHTML.scala",    "BuildHTML"),
    KaTeXModule("builder", "/rast/katex/src/buildMathML.rast.json",   "build/BuildMathML.scala",  "BuildMathML"),
    KaTeXModule("builder", "/rast/katex/src/buildTree.rast.json",     "build/BuildTree.scala",    "BuildTree"),
    KaTeXModule("builder", "/rast/katex/src/stretchy.rast.json",      "build/Stretchy.scala",     "Stretchy"),
    KaTeXModule("builder", "/rast/katex/src/delimiter.rast.json",     "build/Delimiter.scala",    "Delimiter"),
    KaTeXModule("builder", "/rast/katex/src/svgGeometry.rast.json",   "data/SvgGeometry.scala",   "SvgGeometry"),
    KaTeXModule("builder", "/rast/katex/src/parseNode.rast.json",     "parse/ParseNode.scala",    "ParseNode"),
    KaTeXModule("builder", "/rast/katex/src/parseTree.rast.json",     "parse/ParseTree.scala",    "ParseTree"),
    // Category 4: Parser/Lexer
    KaTeXModule("parser", "/rast/katex/src/Parser.rast.json",        "parse/Parser.scala",       "Parser"),
    KaTeXModule("parser", "/rast/katex/src/Lexer.rast.json",         "parse/Lexer.scala",        "Lexer"),
    KaTeXModule("parser", "/rast/katex/src/MacroExpander.rast.json", "parse/MacroExpander.scala", "MacroExpander"),
    KaTeXModule("parser", "/rast/katex/src/macros.rast.json",        "data/Macros.scala",         "Macros"),
    // Category 1: Data/config
    KaTeXModule("data", "/rast/katex/src/symbols.rast.json",         "functions/SymbolsSpacingFunc.scala", "SymbolsSpacingFunc"),
    KaTeXModule("data", "/rast/katex/src/spacingData.rast.json",     "data/SpacingData.scala",    "SpacingData"),
    KaTeXModule("data", "/rast/katex/src/fontMetrics.rast.json",     "data/FontMetricsData.scala","FontMetricsData"),
    KaTeXModule("data", "/rast/katex/src/unicodeScripts.rast.json",  "data/UnicodeScripts.scala", "UnicodeScripts"),
    KaTeXModule("data", "/rast/katex/src/unicodeSupOrSub.rast.json", "data/UnicodeSupOrSub.scala","UnicodeSupOrSub"),
    KaTeXModule("data", "/rast/katex/src/units.rast.json",           "data/Units.scala",          "Units"),
    KaTeXModule("data", "/rast/katex/src/utils.rast.json",           "util/Utils.scala",          "Utils"),
    KaTeXModule("data", "/rast/katex/src/unicodeAccents.rast.json",  "data/UnicodeAccents.scala", "UnicodeAccents"),
    KaTeXModule("data", "/rast/katex/src/unicodeSymbols.rast.json",  "data/UnicodeSymbols.scala", "UnicodeSymbols"),
  )

  /** Function modules (Category 5). */
  val FunctionModules: List[KaTeXModule] = List(
    "accent", "accentunder", "arrow", "char", "color", "cr", "def",
    "delimsizing", "enclose", "environment", "font", "genfrac", "hbox",
    "horizBrace", "href", "html", "htmlmathml", "includegraphics", "kern",
    "lap", "math", "mathchoice", "mclass", "newcommand", "not",
    "op", "operatorname", "ordgroup", "overline", "phantom", "pmb",
    "raisebox", "relax", "rule", "sizing", "smash", "sqrt",
    "styling", "supsub", "symbolsOp", "symbolsOrd", "symbolsSpacing",
    "text", "underline", "vcenter",
  ).map { name =>
    val capName = capitalizeFirst(name)
    KaTeXModule(
      category = "function",
      rastResource = s"/rast/katex/src/functions/$name.rast.json",
      referenceSubPath = s"functions/${capName}Func.scala",
      objectName = s"${capName}Func",
    )
  }

  val AllModules: List[KaTeXModule] = CoreModules ++ FunctionModules

  // --------------------------------------------------------------------------
  // Batch operations
  // --------------------------------------------------------------------------

  /** Batch summary for all modules. */
  final case class BatchSummary(
      totalModules: Int,
      foundRast: Int,
      foundReference: Int,
      functionDefs: Int,
      functionNames: Int,
      parityMethods: Int,
      parityMatched: Int,
      byCategory: Map[String, Int],
  )

  /** Analyze all KaTeX modules: count RAST files found, reference files found,
    * defineFunction calls extracted, and parity match rates.
    */
  def analyzeAll(
      loadRast: String => Option[RastFile],
      katexRefRoot: Path,
  ): BatchSummary =
    var foundRast = 0
    var foundRef = 0
    var totalDefs = 0
    var totalNames = 0
    var totalParityMethods = 0
    var totalParityMatched = 0
    val byCategory = mutable.Map.empty[String, Int].withDefaultValue(0)

    for mod <- AllModules do
      val hasRast = loadRast(mod.rastResource).isDefined
      val refPath = katexRefRoot.resolve(mod.referenceSubPath)
      val hasRef = Files.exists(refPath)

      if hasRast then foundRast += 1
      if hasRef then foundRef += 1
      byCategory(mod.category) += 1

      if hasRast && mod.category == "function" then
        loadRast(mod.rastResource).foreach { rast =>
          val defs = extractDefineFunctions(rast)
          totalDefs += defs.size
          totalNames += defs.flatMap(_.names).size
        }

      if hasRast && hasRef && mod.category != "function" then
        loadRast(mod.rastResource).foreach { rast =>
          val (_, summary) = emitWithParity(rast, refPath)
          totalParityMethods += summary.totalMethods
          totalParityMatched += summary.matchedFromRast
        }

    BatchSummary(
      totalModules = AllModules.size,
      foundRast = foundRast,
      foundReference = foundRef,
      functionDefs = totalDefs,
      functionNames = totalNames,
      parityMethods = totalParityMethods,
      parityMatched = totalParityMatched,
      byCategory = byCategory.toMap,
    )

  def formatBatchSummary(summary: BatchSummary): String =
    val sb = new StringBuilder
    sb.append("=== KaTeX Emitter Analysis ===\n")
    sb.append(s"Total modules: ${summary.totalModules}\n")
    sb.append(s"RAST found: ${summary.foundRast}/${summary.totalModules}\n")
    sb.append(s"Reference found: ${summary.foundReference}/${summary.totalModules}\n")
    sb.append(s"\nBy category:\n")
    for (cat, count) <- summary.byCategory.toList.sortBy(_._1) do
      sb.append(f"  $cat%-12s $count%d\n")
    sb.append(s"\nFunction registrations: ${summary.functionDefs} defineFunction calls\n")
    sb.append(s"Function names: ${summary.functionNames} LaTeX commands\n")
    if summary.parityMethods > 0 then
      val pct = summary.parityMatched * 100.0 / summary.parityMethods
      sb.append(f"\nParity (non-function modules): ${summary.parityMatched}/${summary.parityMethods} ($pct%.1f%%)\n")
    sb.toString

  // --------------------------------------------------------------------------
  // Type mapping
  // --------------------------------------------------------------------------

  val typeMap: Map[String, String] = Map(
    "AnyParseNode"      -> "AnyParseNode",
    "ParseNode"         -> "ParseNodeBase",
    "HtmlDomNode"       -> "HtmlDomNode",
    "MathDomNode"       -> "MathDomNode",
    "Options"           -> "Options",
    "Token"             -> "Token",
    "FunctionContext"   -> "FunctionContext",
    "Settings"          -> "Settings",
    "Mode"              -> "Mode",
    "Style"             -> "Style",
    "SourceLocation"    -> "SourceLocation",
    "Namespace"         -> "Namespace",
    "Lexer"             -> "Lexer",
    "MacroExpander"     -> "MacroExpander",
    "Parser"            -> "Parser",
    "string"            -> "String",
    "number"            -> "Double",
    "boolean"           -> "Boolean",
    "null"              -> "Null",
    "undefined"         -> "Null",
    "void"              -> "Unit",
    "any"               -> "Any",
    "never"             -> "Nothing",
    "unknown"           -> "Any",
  )

  /** Convert a TypeScript type to Scala. */
  def tsTypeToScala(tsType: String): String =
    val trimmed = tsType.trim
    typeMap.getOrElse(trimmed, {
      if trimmed.startsWith("ParseNode<\"") && trimmed.endsWith("\">") then
        val nodeType = trimmed.stripPrefix("ParseNode<\"").stripSuffix("\">")
        s"ParseNode${capitalizeFirst(nodeType)}"
      else if trimmed.contains(" | null") then
        val base = trimmed.replace(" | null", "").replace(" | undefined", "").trim
        s"Nullable[${tsTypeToScala(base)}]"
      else if trimmed.contains(" | undefined") then
        val base = trimmed.replace(" | undefined", "").trim
        s"Nullable[${tsTypeToScala(base)}]"
      else if trimmed.endsWith("[]") then
        val elem = trimmed.stripSuffix("[]")
        s"Array[${tsTypeToScala(elem)}]"
      else
        trimmed
    })

  // --------------------------------------------------------------------------
  // Top-level function extraction
  // --------------------------------------------------------------------------

  /** A top-level function or constant extracted from a RAST file. */
  final case class TopLevelFunction(
      name: String,
      params: List[String],
      bodyNodeKind: String,
  )

  /** Extract top-level function declarations and const arrow functions. */
  def extractTopLevelFunctions(file: RastFile): List[TopLevelFunction] =
    val result = mutable.ListBuffer.empty[TopLevelFunction]
    for node <- file.nodes do
      node.kind match
        case "FunctionDeclaration" =>
          val name = node.children.find(_.kind == "Identifier").flatMap(_.text).getOrElse("")
          if name.nonEmpty then
            val params = node.children.filter(_.kind == "Parameter").flatMap(
              _.children.find(_.kind == "Identifier").flatMap(_.text))
            val bodyKind = node.children.find(_.kind == "Block").map(_.kind).getOrElse("none")
            result += TopLevelFunction(name, params, bodyKind)

        case "VariableStatement" =>
          for vdl <- node.children.find(_.kind == "VariableDeclarationList")
              vd <- vdl.children.filter(_.kind == "VariableDeclaration") do
            val name = vd.children.headOption.flatMap(_.text).getOrElse("")
            val rhs = vd.children.find(c =>
              c.kind == "ArrowFunction" || c.kind == "FunctionExpression")
            rhs.foreach { fn =>
              val params = fn.children.filter(_.kind == "Parameter").flatMap(
                _.children.find(_.kind == "Identifier").flatMap(_.text))
              val bodyKind = fn.children.find(_.kind == "Block").map(_.kind).getOrElse("expr")
              result += TopLevelFunction(name, params, bodyKind)
            }

        case _ => ()
    result.toList

  /** Extract all function-like declarations from a RAST file (for body mapping). */
  private def extractAllFunctions(file: RastFile): List[TopLevelFunction] =
    val result = mutable.ListBuffer.empty[TopLevelFunction]

    def walk(node: RastNode): Unit =
      node.kind match
        case "FunctionDeclaration" =>
          val name = node.children.find(_.kind == "Identifier").flatMap(_.text).getOrElse("")
          if name.nonEmpty then
            val params = node.children.filter(_.kind == "Parameter").flatMap(
              _.children.find(_.kind == "Identifier").flatMap(_.text))
            result += TopLevelFunction(name, params, "Block")

        case "VariableStatement" =>
          for vdl <- node.children.find(_.kind == "VariableDeclarationList")
              vd <- vdl.children.filter(_.kind == "VariableDeclaration") do
            val name = vd.children.headOption.flatMap(_.text).getOrElse("")
            val rhs = vd.children.find(c =>
              c.kind == "ArrowFunction" || c.kind == "FunctionExpression")
            rhs.foreach { fn =>
              val params = fn.children.filter(_.kind == "Parameter").flatMap(
                _.children.find(_.kind == "Identifier").flatMap(_.text))
              result += TopLevelFunction(name, params, "Block")
            }

        case "MethodDeclaration" =>
          val name = node.children.find(_.kind == "Identifier").flatMap(_.text).getOrElse("")
          if name.nonEmpty then
            val params = node.children.filter(_.kind == "Parameter").flatMap(
              _.children.find(_.kind == "Identifier").flatMap(_.text))
            result += TopLevelFunction(name, params, "Block")

        case _ => ()
      node.children.foreach(walk)

    file.nodes.foreach(walk)
    result.toList

  /** Build a name→body-exists map from extracted functions. */
  private def buildBodyMap(fns: List[TopLevelFunction]): Map[String, Boolean] =
    fns.map(f => camelCase(f.name) -> true).toMap

  /** Build a map from method name to a list of (translated RAST body, refusal count).
    *
    * Extracts all functions/methods from the RAST, translates each body using
    * DefmethodBodyTranslator (which handles general TS→Scala patterns), and
    * builds the lookup map. Multiple RAST functions with the same name (e.g.,
    * `toMarkup` on different classes) are stored in occurrence order.
    */
  private def buildTranslatedBodyMap(rastFile: RastFile): mutable.Map[String, mutable.ListBuffer[(String, Int)]] =
    val result = mutable.Map.empty[String, mutable.ListBuffer[(String, Int)]]
    val allFns = extractAllFunctions(rastFile)

    for fn <- allFns do
      val scalaName = camelCase(fn.name)
      val bodyNode = findFunctionBody(rastFile, fn.name, allOccurrences = true)
      for body <- bodyNode do
        val entry = TerserEmitter.DefmethodEntry("_free_", fn.name, fn.params, body)
        val translated = DefmethodBodyTranslator.translateBody(entry, Nil, "    ")
        result.getOrElseUpdate(scalaName, mutable.ListBuffer.empty) += ((translated.scalaBody, translated.refusalCount))

    result

  /** Find all Block body nodes for a named function in the RAST.
    *
    * When `allOccurrences` is true, returns all matching bodies (for functions
    * with the same name in different classes, e.g., `toMarkup` on Span and MathNode).
    * When false, returns only the first match.
    */
  private def findFunctionBody(file: RastFile, name: String, allOccurrences: Boolean = false): List[RastNode] =
    val results = mutable.ListBuffer.empty[RastNode]

    def walk(node: RastNode): Unit =
      if !allOccurrences && results.nonEmpty then return
      node.kind match
        case "FunctionDeclaration" =>
          val fnName = node.children.find(_.kind == "Identifier").flatMap(_.text).getOrElse("")
          if fnName == name then
            node.children.find(_.kind == "Block").foreach(results += _)

        case "VariableStatement" =>
          for vdl <- node.children.find(_.kind == "VariableDeclarationList")
              vd <- vdl.children.filter(_.kind == "VariableDeclaration") do
            val varName = vd.children.headOption.flatMap(_.text).getOrElse("")
            if varName == name then
              val rhs = vd.children.find(c =>
                c.kind == "ArrowFunction" || c.kind == "FunctionExpression")
              rhs.foreach { fn =>
                val body = fn.children.find(_.kind == "Block").orElse {
                  val exprBody = fn.children.find(c => c.kind != "Parameter")
                  exprBody.map(e => RastNode("Block", 0, (0, 0), children = List(
                    RastNode("ReturnStatement", 0, (0, 0), children = List(e))
                  )))
                }
                body.foreach(results += _)
              }

        case "MethodDeclaration" =>
          val mName = node.children.find(_.kind == "Identifier").flatMap(_.text).getOrElse("")
          if mName == name then
            node.children.find(_.kind == "Block").foreach(results += _)

        case _ => ()
      node.children.foreach(walk)

    file.nodes.foreach(walk)
    results.toList

  // --------------------------------------------------------------------------
  // Function body extraction from handler/builder nodes
  // --------------------------------------------------------------------------

  /** Extract the Block body from a handler/builder RAST node.
    *
    * Handles three forms:
    *   - ArrowFunction: `(context, args) => { body }`
    *   - FunctionExpression: `function(context, args) { body }`
    *   - MethodDeclaration: `handler(context, args) { body }` (object method shorthand)
    *
    * Returns the Block node for DefmethodBodyTranslator.
    */
  def extractFunctionBody(nodeOpt: Option[RastNode]): Option[RastNode] =
    nodeOpt.flatMap { node =>
      node.kind match
        case "ArrowFunction" | "FunctionExpression" =>
          node.children.find(_.kind == "Block").orElse {
            val exprBody = node.children.find(c => c.kind != "Parameter" && !c.kind.contains("Type"))
            exprBody.map(e => RastNode("Block", 0, (0, 0), children = List(
              RastNode("ReturnStatement", 0, (0, 0), children = List(e))
            )))
          }
        case "MethodDeclaration" =>
          node.children.find(_.kind == "Block")
        case _ => None
    }

  /** Extract handler parameter names from a handler RAST node. */
  def extractHandlerParams(nodeOpt: Option[RastNode]): List[String] =
    nodeOpt.toList.flatMap { node =>
      node.children.filter(_.kind == "Parameter").flatMap { p =>
        p.children.find(_.kind == "Identifier").flatMap(_.text)
          .orElse(p.children.find(_.kind == "ObjectBindingPattern").map(_ => "_ctx"))
      }
    }

  // --------------------------------------------------------------------------
  // Private helpers
  // --------------------------------------------------------------------------

  private def extractStringValue(node: RastNode): Option[String] =
    node.value match
      case Some(RastValue.Str(s)) => Some(s)
      case _ => node.text

  private def extractStringArray(node: RastNode): List[String] =
    if node.kind != "ArrayLiteralExpression" then Nil
    else node.children.flatMap(c => c.value match
      case Some(RastValue.Str(s)) => Some(s)
      case _ => None
    )

  private def extractPropInt(name: String, propsNode: RastNode): Option[Int] =
    if propsNode.kind != "ObjectLiteralExpression" then None
    else propsNode.children.find { pa =>
      pa.kind == "PropertyAssignment" &&
        pa.children.headOption.flatMap(_.text).contains(name)
    }.flatMap(_.children.lift(1)).flatMap(_.value).collect {
      case RastValue.Num(n) => n.toInt
    }

  private def extractPropBool(name: String, propsNode: RastNode): Option[Boolean] =
    if propsNode.kind != "ObjectLiteralExpression" then None
    else propsNode.children.find { pa =>
      pa.kind == "PropertyAssignment" &&
        pa.children.headOption.flatMap(_.text).contains(name)
    }.flatMap(_.children.lift(1)).map { n =>
      n.kind == "TrueKeyword" || n.value.contains(RastValue.Bool(true))
    }

  private def extractArgTypes(propsNode: RastNode): List[String] =
    if propsNode.kind != "ObjectLiteralExpression" then Nil
    else propsNode.children.find { pa =>
      pa.kind == "PropertyAssignment" &&
        pa.children.headOption.flatMap(_.text).contains("argTypes")
    }.flatMap(_.children.lift(1)).map(extractStringArray).getOrElse(Nil)

  private def header(sourcePath: String, targetName: String): String =
    s"""/*
       | * KaTeX math rendering engine — Scala 3 port
       | *
       | * Ported from: $sourcePath
       | * Original license: MIT
       | *
       | * Auto-generated by KaTeXEmitter from RAST v1
       | */
       |""".stripMargin

  private def camelCase(s: String): String =
    if s.contains("_") then
      val parts = s.split("_")
      parts.head + parts.tail.map(p => if p.nonEmpty then p(0).toUpper + p.substring(1) else "").mkString
    else s

  private def capitalizeFirst(s: String): String =
    if s.isEmpty then s else s(0).toUpper + s.substring(1)

  private def escapeString(s: String): String =
    s.replace("\\", "\\\\").replace("\"", "\\\"")
