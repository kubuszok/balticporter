package balticporter.frontend.ts.dedicated

import balticporter.frontend.ts.{RastFile, RastNode, RastValue}
import java.nio.file.{Files, Path}
import scala.collection.mutable

/** Dedicated RAST-to-Scala emitter for dart-sass — a Dart implementation of
  * the Sass CSS preprocessor.
  *
  * Dart is structurally closer to Scala than JavaScript/TypeScript:
  *   - Classes with constructors, named parameters, factory constructors
  *   - Mixins → traits
  *   - Extensions → extension methods
  *   - `late` fields → lazy vals
  *   - Null-safety (`?` types) → Nullable or `| Null`
  *   - Cascade `..` → temp variable + method chain
  *
  * The emitter uses parity-derive against the ssg-sass hand-ported reference
  * (132 files, 48K LOC). RAST bodies are translated using
  * DefmethodBodyTranslator where they compile; otherwise reference bodies
  * are kept. */
object DartSassEmitter:

  // --------------------------------------------------------------------------
  // Module inventory
  // --------------------------------------------------------------------------

  final case class DartSassModule(
      category: String,
      dartPath: String,
      referenceSubPath: String,
      objectName: String,
  )

  val TopLevelModules: List[DartSassModule] = List(
    DartSassModule("class", "lib/src/exception.dart",           "SassException.scala",      "SassException"),
    DartSassModule("class", "lib/src/callable.dart",            "Callable.scala",           "Callable"),
    DartSassModule("class", "lib/src/configuration.dart",       "Configuration.scala",      "Configuration"),
    DartSassModule("class", "lib/src/compile.dart",             "Compile.scala",            "Compile"),
    DartSassModule("class", "lib/src/environment.dart",         "Environment.scala",        "Environment"),
    DartSassModule("class", "lib/src/evaluation_context.dart",  "EvaluationContext.scala",  "EvaluationContext"),
    DartSassModule("class", "lib/src/import_cache.dart",        "ImportCache.scala",        "ImportCache"),
    DartSassModule("class", "lib/src/interpolation_buffer.dart","InterpolationBuffer.scala", "InterpolationBuffer"),
    DartSassModule("class", "lib/src/interpolation_map.dart",   "InterpolationMap.scala",   "InterpolationMap"),
    DartSassModule("class", "lib/src/deprecation.dart",         "Deprecation.scala",        "Deprecation"),
    DartSassModule("class", "lib/src/syntax.dart",              "Syntax.scala",             "Syntax"),
    DartSassModule("class", "lib/src/module.dart",              "Module.scala",             "Module"),
    DartSassModule("data",  "lib/src/color_names.dart",         "ColorNames.scala",         "ColorNames"),
    DartSassModule("data",  "lib/src/utils.dart",               "Utils.scala",              "Utils"),
    DartSassModule("data",  "lib/src/logger.dart",              "Logger.scala",             "Logger"),
  )

  val AstCssModules: List[DartSassModule] = List(
    DartSassModule("ast", "lib/src/ast/css/at_rule.dart",       "ast/css/CssAtRule.scala",       "CssAtRule"),
    DartSassModule("ast", "lib/src/ast/css/comment.dart",       "ast/css/CssComment.scala",      "CssComment"),
    DartSassModule("ast", "lib/src/ast/css/declaration.dart",   "ast/css/CssDeclaration.scala",  "CssDeclaration"),
    DartSassModule("ast", "lib/src/ast/css/import.dart",        "ast/css/CssImport.scala",       "CssImport"),
    DartSassModule("ast", "lib/src/ast/css/keyframe_block.dart","ast/css/CssKeyframeBlock.scala", "CssKeyframeBlock"),
    DartSassModule("ast", "lib/src/ast/css/media_query.dart",   "ast/css/CssMediaQuery.scala",   "CssMediaQuery"),
    DartSassModule("ast", "lib/src/ast/css/media_rule.dart",    "ast/css/CssMediaRule.scala",    "CssMediaRule"),
    DartSassModule("ast", "lib/src/ast/css/style_rule.dart",    "ast/css/CssStyleRule.scala",    "CssStyleRule"),
    DartSassModule("ast", "lib/src/ast/css/stylesheet.dart",    "ast/css/CssStylesheet.scala",   "CssStylesheet"),
    DartSassModule("ast", "lib/src/ast/css/supports_rule.dart", "ast/css/CssSupportsRule.scala",  "CssSupportsRule"),
    DartSassModule("ast", "lib/src/ast/css/value.dart",         "ast/css/CssValue.scala",        "CssValue"),
  )

  val AstSassModules: List[DartSassModule] = List(
    DartSassModule("ast", "lib/src/ast/sass/argument_declaration.dart", "ast/sass/ArgumentDeclaration.scala", "ArgumentDeclaration"),
    DartSassModule("ast", "lib/src/ast/sass/at_root_query.dart",       "ast/sass/AtRootQuery.scala",        "AtRootQuery"),
    DartSassModule("ast", "lib/src/ast/sass/expression.dart",          "ast/sass/Expression.scala",          "Expression"),
    DartSassModule("ast", "lib/src/ast/sass/import.dart",              "ast/sass/Import.scala",              "Import"),
    DartSassModule("ast", "lib/src/ast/sass/interpolation.dart",       "ast/sass/Interpolation.scala",       "Interpolation"),
    DartSassModule("ast", "lib/src/ast/sass/statement.dart",           "ast/sass/Statement.scala",           "Statement"),
  )

  val SelectorModules: List[DartSassModule] = List(
    DartSassModule("selector", "lib/src/ast/selector/complex.dart",    "ast/selector/ComplexSelector.scala", "ComplexSelector"),
  )

  val ParseModules: List[DartSassModule] = List(
    DartSassModule("parse", "lib/src/parse/stylesheet.dart",  "parse/StylesheetParser.scala", "StylesheetParser"),
    DartSassModule("parse", "lib/src/parse/sass.dart",        "parse/SassParser.scala",       "SassParser"),
    DartSassModule("parse", "lib/src/parse/scss.dart",        "parse/ScssParser.scala",       "ScssParser"),
    DartSassModule("parse", "lib/src/parse/css.dart",         "parse/CssParser.scala",        "CssParser"),
    DartSassModule("parse", "lib/src/parse/selector.dart",    "parse/SelectorParser.scala",   "SelectorParser"),
    DartSassModule("parse", "lib/src/parse/media_query.dart", "parse/MediaQueryParser.scala",  "MediaQueryParser"),
    DartSassModule("parse", "lib/src/parse/at_root_query.dart","parse/AtRootQueryParser.scala","AtRootQueryParser"),
    DartSassModule("parse", "lib/src/parse/key_frame.dart",   "parse/KeyframeParser.scala",    "KeyframeParser"),
  )

  val ValueModules: List[DartSassModule] = List(
    DartSassModule("value", "lib/src/value.dart",                "value/Value.scala",              "Value"),
    DartSassModule("value", "lib/src/value/boolean.dart",        "value/SassBoolean.scala",        "SassBoolean"),
    DartSassModule("value", "lib/src/value/calculation.dart",    "value/SassCalculation.scala",    "SassCalculation"),
    DartSassModule("value", "lib/src/value/color.dart",          "value/SassColor.scala",          "SassColor"),
    DartSassModule("value", "lib/src/value/function.dart",       "value/SassFunction.scala",       "SassFunction"),
    DartSassModule("value", "lib/src/value/list.dart",           "value/SassList.scala",           "SassList"),
    DartSassModule("value", "lib/src/value/map.dart",            "value/SassMap.scala",            "SassMap"),
    DartSassModule("value", "lib/src/value/mixin.dart",          "value/SassMixin.scala",          "SassMixin"),
    DartSassModule("value", "lib/src/value/null.dart",           "value/SassNull.scala",           "SassNull"),
    DartSassModule("value", "lib/src/value/number.dart",         "value/SassNumber.scala",         "SassNumber"),
    DartSassModule("value", "lib/src/value/string.dart",         "value/SassString.scala",         "SassString"),
  )

  val VisitorModules: List[DartSassModule] = List(
    DartSassModule("visitor", "lib/src/visitor/serialize.dart",       "visitor/SerializeVisitor.scala",       "SerializeVisitor"),
    DartSassModule("visitor", "lib/src/visitor/evaluate.dart",        "visitor/EvaluateVisitor.scala",        "EvaluateVisitor"),
    DartSassModule("visitor", "lib/src/visitor/clone_css.dart",       "visitor/CloneCssVisitor.scala",        "CloneCssVisitor"),
    DartSassModule("visitor", "lib/src/visitor/recursive_ast.dart",   "visitor/RecursiveAstVisitor.scala",    "RecursiveAstVisitor"),
    DartSassModule("visitor", "lib/src/visitor/recursive_statement.dart", "visitor/RecursiveStatementVisitor.scala", "RecursiveStatementVisitor"),
    DartSassModule("visitor", "lib/src/visitor/find_dependencies.dart", "visitor/FindDependenciesVisitor.scala", "FindDependenciesVisitor"),
  )

  val FunctionModules: List[DartSassModule] = List(
    DartSassModule("function", "lib/src/functions/color.dart",  "functions/ColorFunctions.scala",  "ColorFunctions"),
    DartSassModule("function", "lib/src/functions/list.dart",   "functions/ListFunctions.scala",   "ListFunctions"),
    DartSassModule("function", "lib/src/functions/map.dart",    "functions/MapFunctions.scala",    "MapFunctions"),
    DartSassModule("function", "lib/src/functions/math.dart",   "functions/MathFunctions.scala",   "MathFunctions"),
    DartSassModule("function", "lib/src/functions/meta.dart",   "functions/MetaFunctions.scala",   "MetaFunctions"),
    DartSassModule("function", "lib/src/functions/selector.dart","functions/SelectorFunctions.scala","SelectorFunctions"),
    DartSassModule("function", "lib/src/functions/string.dart", "functions/StringFunctions.scala", "StringFunctions"),
  )

  val AllModules: List[DartSassModule] =
    TopLevelModules ++ AstCssModules ++ AstSassModules ++ SelectorModules ++
      ParseModules ++ ValueModules ++ VisitorModules ++ FunctionModules

  // --------------------------------------------------------------------------
  // Dart-specific type mapping
  // --------------------------------------------------------------------------

  val dartTypeMap: Map[String, String] = Map(
    "String"  -> "String",
    "int"     -> "Int",
    "double"  -> "Double",
    "bool"    -> "Boolean",
    "num"     -> "Double",
    "void"    -> "Unit",
    "dynamic" -> "Any",
    "Object"  -> "Any",
    "Never"   -> "Nothing",
    "Null"    -> "Null",
  )

  def dartTypeToScala(dartType: String): String =
    val trimmed = dartType.trim
    dartTypeMap.getOrElse(trimmed, {
      if trimmed.endsWith("?") then
        val base = trimmed.stripSuffix("?")
        s"Nullable[${dartTypeToScala(base)}]"
      else if trimmed.startsWith("List<") && trimmed.endsWith(">") then
        val elem = trimmed.stripPrefix("List<").stripSuffix(">")
        s"List[${dartTypeToScala(elem)}]"
      else if trimmed.startsWith("Map<") && trimmed.endsWith(">") then
        val inner = trimmed.stripPrefix("Map<").stripSuffix(">")
        val comma = findTopLevelComma(inner)
        if comma >= 0 then
          val k = inner.substring(0, comma).trim
          val v = inner.substring(comma + 1).trim
          s"Map[${dartTypeToScala(k)}, ${dartTypeToScala(v)}]"
        else s"Map[Any, Any]"
      else if trimmed.startsWith("Set<") && trimmed.endsWith(">") then
        val elem = trimmed.stripPrefix("Set<").stripSuffix(">")
        s"Set[${dartTypeToScala(elem)}]"
      else if trimmed.startsWith("Future<") && trimmed.endsWith(">") then
        val elem = trimmed.stripPrefix("Future<").stripSuffix(">")
        dartTypeToScala(elem)
      else if trimmed.startsWith("Iterable<") && trimmed.endsWith(">") then
        val elem = trimmed.stripPrefix("Iterable<").stripSuffix(">")
        s"Iterable[${dartTypeToScala(elem)}]"
      else
        trimmed
    })

  private def findTopLevelComma(s: String): Int =
    var depth = 0
    for i <- 0 until s.length do
      s(i) match
        case '<' | '(' | '[' => depth += 1
        case '>' | ')' | ']' => depth -= 1
        case ',' if depth == 0 => return i
        case _ =>
    -1

  // --------------------------------------------------------------------------
  // Parity-derive emission
  // --------------------------------------------------------------------------

  final case class ParityEmitSummary(
      moduleName: String,
      totalMethods: Int,
      matchedFromRast: Int,
      keptFromReference: Int,
      refusalCount: Int,
      matchDetails: List[(String, String)] = Nil,
  )

  private val dartUncompilablePatterns: List[String] = List(
    "async ",           // async/await — ssg-sass is synchronous
    "await ",           // async/await
    "Future<",          // Future type
    "dart:io",          // platform-specific I/O
    "File(",            // file system
    "HttpClient",       // HTTP
    "js_interop",       // JS bindings
    "dart:html",        // browser DOM
    "StreamController", // async streams
    "Zone.",            // Dart zones
  )

  private def containsDartUncompilablePatterns(body: String): Boolean =
    dartUncompilablePatterns.exists(body.contains)

  def emitWithParity(
      rastFile: RastFile,
      referencePath: Path,
  ): (String, ParityEmitSummary) =
    val referenceSource = new String(Files.readAllBytes(referencePath))
    val lines = referenceSource.split("\n", -1).toList

    val methods = TerserCompressEmitter.findMethodBoundaries(lines)
    val rastBodies = buildTranslatedBodyMap(rastFile)
    val moduleName = referencePath.getFileName.toString.stripSuffix(".scala")

    val sb = new StringBuilder
    val matchDetails = mutable.ListBuffer.empty[(String, String)]
    var totalRefusals = 0

    var lineIdx = 0
    var methodIdx = 0
    val usedNames = mutable.Map.empty[String, Int].withDefaultValue(0)

    while lineIdx < lines.size do
      if methodIdx < methods.size && lineIdx == methods(methodIdx).signatureLine then
        val method = methods(methodIdx)

        val bodyList = rastBodies.getOrElse(method.name, Nil)
        val idx = usedNames(method.name)
        usedNames(method.name) = idx + 1
        val usableRast = bodyList.lift(idx).filter { case (body, _) =>
          !containsDartUncompilablePatterns(body)
        }

        usableRast match
          case Some((translatedBody, refusals)) =>
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

  // --------------------------------------------------------------------------
  // Batch operations
  // --------------------------------------------------------------------------

  final case class BatchSummary(
      totalModules: Int,
      foundRast: Int,
      foundReference: Int,
      parityMethods: Int,
      parityMatched: Int,
      byCategory: Map[String, Int],
  )

  def analyzeAll(
      loadRast: String => Option[RastFile],
      sassRefRoot: Path,
  ): BatchSummary =
    var foundRast = 0
    var foundRef = 0
    var totalParityMethods = 0
    var totalParityMatched = 0
    val byCategory = mutable.Map.empty[String, Int].withDefaultValue(0)

    for mod <- AllModules do
      val rastResource = s"/rast/dart-sass/${mod.dartPath}.rast.json"
      val hasRast = loadRast(rastResource).isDefined
      val refPath = sassRefRoot.resolve(mod.referenceSubPath)
      val hasRef = Files.exists(refPath)

      if hasRast then foundRast += 1
      if hasRef then foundRef += 1
      byCategory(mod.category) += 1

      if hasRast && hasRef then
        loadRast(rastResource).foreach { rast =>
          val (_, summary) = emitWithParity(rast, refPath)
          totalParityMethods += summary.totalMethods
          totalParityMatched += summary.matchedFromRast
        }

    BatchSummary(
      totalModules = AllModules.size,
      foundRast = foundRast,
      foundReference = foundRef,
      parityMethods = totalParityMethods,
      parityMatched = totalParityMatched,
      byCategory = byCategory.toMap,
    )

  def emitAllWithParity(
      loadRast: String => Option[RastFile],
      sassRefRoot: Path,
      outDir: Path,
  ): List[(DartSassModule, ParityEmitSummary)] =
    Files.createDirectories(outDir)
    val results = mutable.ListBuffer.empty[(DartSassModule, ParityEmitSummary)]

    for mod <- AllModules do
      val rastResource = s"/rast/dart-sass/${mod.dartPath}.rast.json"
      val refPath = sassRefRoot.resolve(mod.referenceSubPath)
      if Files.exists(refPath) then
        loadRast(rastResource).foreach { rast =>
          val (source, summary) = emitWithParity(rast, refPath)
          val outFile = outDir.resolve(s"${mod.objectName}.scala")
          Files.writeString(outFile, source)
          results += ((mod, summary))
        }

    results.toList

  def formatBatchSummary(summary: BatchSummary): String =
    val sb = new StringBuilder
    sb.append("=== dart-sass Emitter Analysis ===\n")
    sb.append(s"Total modules: ${summary.totalModules}\n")
    sb.append(s"RAST found: ${summary.foundRast}/${summary.totalModules}\n")
    sb.append(s"Reference found: ${summary.foundReference}/${summary.totalModules}\n")
    sb.append(s"\nBy category:\n")
    for (cat, count) <- summary.byCategory.toList.sortBy(_._1) do
      sb.append(f"  $cat%-12s $count%d\n")
    if summary.parityMethods > 0 then
      val pct = summary.parityMatched * 100.0 / summary.parityMethods
      sb.append(f"\nParity: ${summary.parityMatched}/${summary.parityMethods} ($pct%.1f%%)\n")
    sb.toString

  def formatParitySummaryTable(summaries: List[ParityEmitSummary]): String =
    val sb = new StringBuilder
    sb.append(f"${"Module"}%-30s ${"Total"}%6s ${"RAST"}%6s ${"Ref"}%6s ${"Refusals"}%9s\n")
    sb.append("-" * 60)
    sb.append("\n")
    var tTotal = 0; var tRast = 0; var tRef = 0; var tRefusals = 0
    for s <- summaries do
      sb.append(f"${s.moduleName}%-30s ${s.totalMethods}%6d ${s.matchedFromRast}%6d ${s.keptFromReference}%6d ${s.refusalCount}%9d\n")
      tTotal += s.totalMethods; tRast += s.matchedFromRast; tRef += s.keptFromReference; tRefusals += s.refusalCount
    sb.append("-" * 60)
    sb.append("\n")
    sb.append(f"${"TOTAL"}%-30s ${tTotal}%6d ${tRast}%6d ${tRef}%6d ${tRefusals}%9d\n")
    val pctRast = if tTotal > 0 then (tRast * 100.0 / tTotal) else 0.0
    sb.append(f"\nRAST-derived bodies: $tRast/$tTotal (${pctRast}%.1f%%)\n")
    sb.toString

  // --------------------------------------------------------------------------
  // Body translation
  // --------------------------------------------------------------------------

  private def buildTranslatedBodyMap(rastFile: RastFile): Map[String, List[(String, Int)]] =
    val result = mutable.Map.empty[String, mutable.ListBuffer[(String, Int)]]
    val allFns = extractAllFunctions(rastFile)

    for fn <- allFns do
      val scalaName = dartToCamelCase(fn.name)
      val bodyNode = findFunctionBody(rastFile, fn.name)
      bodyNode.foreach { body =>
        val entry = TerserEmitter.DefmethodEntry("_free_", fn.name, fn.params, body)
        val translated = DefmethodBodyTranslator.translateBody(entry, Nil, "    ")
        result.getOrElseUpdate(scalaName, mutable.ListBuffer.empty) +=
          ((translated.scalaBody, translated.refusalCount))
      }

    result.map { case (k, v) => k -> v.toList }.toMap

  final case class ExtractedFunction(
      name: String,
      params: List[String],
      bodyKind: String,
  )

  def extractAllFunctions(file: RastFile): List[ExtractedFunction] =
    val result = mutable.ListBuffer.empty[ExtractedFunction]
    val symbolMap = file.symbols

    def nameFromSymbol(node: RastNode): String =
      node.symbol.flatMap(symbolMap.get).map(_.name).getOrElse(
        node.children.find(c => c.kind.contains("Identifier")).flatMap(_.text).getOrElse(""))

    def walk(node: RastNode): Unit =
      val kind = node.kind.stripSuffix("Impl")
      kind match
        case "MethodDeclaration" | "FunctionDeclaration" =>
          val name = nameFromSymbol(node)
          if name.nonEmpty then
            // Dart FunctionDeclaration wraps params and body in FunctionExpression
            val fnExpr = node.children.find(_.kind.contains("FunctionExpression"))
            val searchIn = fnExpr.map(_.children).getOrElse(node.children)
            val params = searchIn
              .find(c => c.kind.contains("FormalParameterList"))
              .map(_.children.flatMap(p =>
                nameFromSymbol(p) match
                  case n if n.nonEmpty => Some(n)
                  case _ => p.children.find(c => c.kind.contains("Identifier")).flatMap(_.text)
              ))
              .getOrElse(Nil)
            val hasBody = searchIn.exists(c =>
              c.kind.contains("BlockFunctionBody") || c.kind.contains("ExpressionFunctionBody"))
            if hasBody then
              result += ExtractedFunction(name, params, "Block")

        case "ConstructorDeclaration" =>
          val name = nameFromSymbol(node)
          val actualName = if name.isEmpty then "<init>" else name
          val hasBody = node.children.exists(c =>
            c.kind.contains("BlockFunctionBody") || c.kind.contains("ExpressionFunctionBody"))
          if hasBody then
            val params = node.children
              .find(c => c.kind.contains("FormalParameterList"))
              .map(_.children.flatMap(p =>
                nameFromSymbol(p) match
                  case n if n.nonEmpty => Some(n)
                  case _ => p.children.find(c => c.kind.contains("Identifier")).flatMap(_.text)
              ))
              .getOrElse(Nil)
            result += ExtractedFunction(actualName, params, "Block")

        case _ => ()

      node.children.foreach(walk)

    file.nodes.foreach(walk)
    result.toList

  def findFunctionBody(file: RastFile, name: String): Option[RastNode] =
    var found: Option[RastNode] = None
    val symbolMap = file.symbols

    def nameFromSymbol(node: RastNode): String =
      node.symbol.flatMap(symbolMap.get).map(_.name).getOrElse("")

    def walk(node: RastNode): Unit =
      if found.isDefined then return
      val kind = node.kind.stripSuffix("Impl")
      kind match
        case "MethodDeclaration" | "FunctionDeclaration" =>
          val fnName = nameFromSymbol(node)
          if fnName == name then
            // Dart FunctionDeclaration wraps body in FunctionExpression
            val fnExpr = node.children.find(_.kind.contains("FunctionExpression"))
            val searchIn = fnExpr.map(_.children).getOrElse(node.children)
            found = searchIn.find(c =>
              c.kind.contains("BlockFunctionBody") || c.kind.contains("ExpressionFunctionBody"))
            // Unwrap BlockFunctionBody to its inner Block
            found = found.flatMap { body =>
              if body.kind.contains("BlockFunctionBody") then
                body.children.find(_.kind.contains("Block")).orElse(Some(body))
              else if body.kind.contains("ExpressionFunctionBody") then
                // Wrap the expression in a synthetic return block
                body.children.headOption
                  .filterNot(_.kind.contains("Type")) // skip return type annotation
                  .map { expr =>
                    RastNode("Block", 0, (0, 0), children = List(
                      RastNode("ReturnStatement", 0, (0, 0), children = List(expr))
                    ))
                  }
              else Some(body)
            }

        case _ => ()
      if found.isEmpty then node.children.foreach(walk)

    file.nodes.foreach(walk)
    found

  // --------------------------------------------------------------------------
  // Dart RAST reader (compatible with TS RastFile but handles missing kindCode)
  // --------------------------------------------------------------------------

  def readDartRast(json: String): RastFile =
    import com.github.plokhotnyuk.jsoniter_scala.core.*
    val parsed = readFromString[DartRastJson](json)(using dartRastCodec)
    RastFile(
      version = parsed.version,
      path = parsed.path,
      sha256 = parsed.sha256,
      nodes = parsed.nodes.map(convertNode),
      symbols = parsed.symbols.map { case (k, v) =>
        k -> balticporter.frontend.ts.RastSymbol(
          name = v.name,
          flags = v.flags,
          declarationType = v.declarationType,
          parent = v.parent,
        )
      },
      types = parsed.types.map { case (k, v) =>
        k -> balticporter.frontend.ts.RastType(
          kind = v.kind,
          text = v.text,
        )
      },
    )

  private def convertNode(n: DartNode): RastNode =
    RastNode(
      kind = n.kind,
      kindCode = 0,
      pos = n.pos,
      children = n.children.map(convertNode),
      symbol = n.symbol,
      `type` = n.`type`,
      resolvedSymbol = n.resolvedSymbol,
      value = n.value,
      text = n.text,
      operator = n.operator,
      flags = n.flags,
    )

  private case class DartRastJson(
      version: Int = 1,
      path: String = "",
      sha256: String = "",
      nodes: List[DartNode] = Nil,
      symbols: Map[String, DartSymbol] = Map.empty,
      types: Map[String, DartTypeEntry] = Map.empty,
  )

  private case class DartNode(
      kind: String = "",
      pos: (Int, Int) = (0, 0),
      children: List[DartNode] = Nil,
      symbol: Option[String] = None,
      `type`: Option[String] = None,
      resolvedSymbol: Option[String] = None,
      value: Option[RastValue] = None,
      text: Option[String] = None,
      operator: Option[String] = None,
      flags: List[String] = Nil,
      // Dart-specific fields (consumed but not mapped to TS RastNode)
      isFactory: Option[Boolean] = None,
      isLate: Option[Boolean] = None,
      isExtension: Option[Boolean] = None,
      mixins: Option[List[String]] = None,
  )

  private case class DartSymbol(
      name: String = "",
      flags: List[String] = Nil,
      declarationType: Option[String] = None,
      parent: Option[String] = None,
      isNullable: Option[Boolean] = None,
  )

  private case class DartTypeEntry(
      kind: String = "",
      text: String = "",
      isNullable: Boolean = false,
      typeArguments: Option[List[String]] = None,
      returnType: Option[String] = None,
      bound: Option[String] = None,
  )

  import com.github.plokhotnyuk.jsoniter_scala.core.*
  import com.github.plokhotnyuk.jsoniter_scala.macros.*

  private given dartRastCodec: JsonValueCodec[DartRastJson] = JsonCodecMaker.make(
    CodecMakerConfig
      .withDiscriminatorFieldName(None)
      .withAllowRecursiveTypes(true)
      .withMapMaxInsertNumber(1000000)
      .withSetMaxInsertNumber(1000000)
      .withSkipUnexpectedFields(true)
  )

  // --------------------------------------------------------------------------
  // Helpers
  // --------------------------------------------------------------------------

  def dartToCamelCase(s: String): String =
    if !s.contains("_") then s
    else
      val parts = s.split("_")
      parts.head + parts.tail.map(p => if p.nonEmpty then p(0).toUpper + p.substring(1) else "").mkString
