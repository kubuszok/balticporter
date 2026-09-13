package balticporter.frontend.ts.dedicated

import balticporter.frontend.ts.{RastFile, RastNode, RastValue}
import scala.collection.mutable

/** Emits complete compress module Scala files with translated method bodies.
  *
  * Uses DefmethodBodyTranslator (B8) to translate DEFMETHOD and free-function
  * bodies from RAST into Scala. Each compress module is emitted as a Scala
  * object containing either DEFMETHOD-derived methods or free functions,
  * with body translation statistics tracked per module.
  *
  * Handles two DEFMETHOD calling conventions:
  *   - Direct: `AST_X.DEFMETHOD("name", function(...) { ... })`
  *   - IIFE-wrapped: `(function(def_name) { def_name(AST_X, fn); ... })(function(node, func) { node.DEFMETHOD("name", func); })`
  *
  * Three module categories:
  *   1. DEFMETHOD-based: index, inference, evaluate, global-defs,
  *      drop-side-effect-free, drop-unused, reduce-vars
  *   2. Free-function-based: common, tighten-body, inline
  *   3. Data/constants: compressor-flags, native-objects (handled by
  *      TerserEmitter and TerserB10B11C3Emitter)
  */
object TerserCompressEmitter:

  /** Summary of translation for one module. */
  final case class ModuleTranslationSummary(
      moduleName: String,
      objectName: String,
      defmethodCount: Int,
      freeFunctionCount: Int,
      fullyTranslated: Int,
      partiallyTranslated: Int,
      refused: Int,
      totalRefusalCount: Int,
  )

  // --------------------------------------------------------------------------
  // IIFE-aware DEFMETHOD extraction for compress modules
  // --------------------------------------------------------------------------

  /** Extract all DEFMETHOD entries from a compress module RAST, including
    * those wrapped in IIFE patterns.
    *
    * Two forms are recognized:
    *   1. Direct: `AST_X.DEFMETHOD("name", function(...) { ... })`
    *      (handled by TerserEmitter.extractDefmethods)
    *   2. IIFE-wrapped:
    *      {{{
    *      (function(def_name) {
    *        def_name(AST_Node, return_false);
    *        def_name(AST_Number, return_true);
    *        def_name(AST_Unary, function(compressor) { ... });
    *      })(function(node, func) {
    *        node.DEFMETHOD("method_name", func);
    *      });
    *      }}}
    */
  def extractAllDefmethods(file: RastFile): List[TerserEmitter.DefmethodEntry] =
    val result = mutable.ListBuffer.empty[TerserEmitter.DefmethodEntry]
    // Collect direct DEFMETHODs
    result ++= TerserEmitter.extractDefmethods(file)
    // Collect IIFE-wrapped DEFMETHODs
    for node <- file.nodes do
      result ++= extractIifeDefmethods(node)
    result.toList

  /** Extract DEFMETHOD entries from an IIFE wrapper node.
    *
    * Recognizes the pattern:
    *   ExpressionStatement > (ParenthesizedExpression | CallExpression) >
    *   CallExpression(FunctionExpression(body), FunctionExpression(wrapper))
    *
    * The wrapper function calls `node.DEFMETHOD("name", func)` to determine
    * the method name. The body function calls `def_name(AST_X, impl)` to
    * bind each class to its implementation.
    */
  private def extractIifeDefmethods(node: RastNode): List[TerserEmitter.DefmethodEntry] =
    if node.kind != "ExpressionStatement" then return Nil

    // Find the CallExpression (may be wrapped in ParenthesizedExpression)
    val callExpr = node.children.headOption match
      case Some(paren) if paren.kind == "ParenthesizedExpression" =>
        paren.children.find(_.kind == "CallExpression")
      case Some(call) if call.kind == "CallExpression" =>
        // Check if this is an IIFE (first child is FunctionExpression)
        if call.children.headOption.exists(c =>
          c.kind == "FunctionExpression" || c.kind == "ArrowFunction"
        ) then Some(call)
        else None
      case _ => None

    callExpr match
      case None => Nil
      case Some(call) =>
        val children = call.children
        // Need at least 2 function expressions: body and wrapper
        val funcExprs = children.filter(c =>
          c.kind == "FunctionExpression" || c.kind == "ArrowFunction"
        )
        if funcExprs.size < 2 then return Nil

        val bodyFn = funcExprs.head
        val wrapperFn = funcExprs(1)

        // Extract method name from wrapper: node.DEFMETHOD("name", func)
        val methodName = extractWrapperMethodName(wrapperFn)
        if methodName.isEmpty then return Nil

        // Extract parameter name used in body function (e.g., "def_is_boolean")
        val paramName = bodyFn.children
          .find(_.kind == "Parameter")
          .flatMap(_.children.find(_.kind == "Identifier"))
          .flatMap(_.text)
          .getOrElse("")

        // Extract bindings from body: def_name(AST_X, impl)
        val bodyBlock = bodyFn.children.find(_.kind == "Block")
        bodyBlock match
          case None => Nil
          case Some(block) =>
            extractIifeBindings(block, paramName, methodName)

  /** Extract the DEFMETHOD method name from the wrapper function.
    *
    * The wrapper body contains: `node.DEFMETHOD("method_name", func)`
    */
  private def extractWrapperMethodName(wrapperFn: RastNode): String =
    val block = wrapperFn.children.find(_.kind == "Block")
    block match
      case None => ""
      case Some(b) =>
        val found = for
          stmt <- b.children.find(_.kind == "ExpressionStatement")
          call <- stmt.children.find(_.kind == "CallExpression")
          callee <- call.children.headOption
          if callee.kind == "PropertyAccessExpression"
          prop <- callee.children.lastOption.flatMap(_.text)
          if prop == "DEFMETHOD"
          nameArg <- call.children.drop(1).headOption
          name <- nameArg.value.collect { case RastValue.Str(s) => s }
        yield name
        found.getOrElse("")

  /** Extract class-to-implementation bindings from the IIFE body.
    *
    * Each statement is: `def_name(AST_ClassName, function(...) { ... })` or
    * `def_name(AST_ClassName, return_false)`.
    */
  private def extractIifeBindings(
      block: RastNode,
      paramName: String,
      methodName: String,
  ): List[TerserEmitter.DefmethodEntry] =
    val result = mutable.ListBuffer.empty[TerserEmitter.DefmethodEntry]

    def walkStatements(nodes: List[RastNode]): Unit =
      for node <- nodes do
        node.kind match
          case "ExpressionStatement" =>
            node.children.headOption match
              case Some(call) if call.kind == "CallExpression" =>
                val callee = call.children.headOption
                val calleeName = callee.flatMap(_.text).getOrElse("")
                if calleeName == paramName || (paramName.isEmpty && calleeName.nonEmpty) then
                  val args = call.children.drop(1)
                  if args.size >= 2 then
                    val classArg = args.head
                    val implArg = args(1)
                    val className = classArg.text.getOrElse("")
                    if className.nonEmpty then
                      extractBinding(className, methodName, implArg).foreach(result += _)
              case _ => ()
          case "VariableStatement" => () // skip
          case "IfStatement" => () // skip conditional bindings
          case _ => ()

    walkStatements(block.children)
    result.toList

  /** Extract a single binding from the implementation argument. */
  private def extractBinding(
      className: String,
      methodName: String,
      implArg: RastNode,
  ): Option[TerserEmitter.DefmethodEntry] =
    implArg.kind match
      case "FunctionExpression" | "ArrowFunction" =>
        val params = implArg.children.filter(_.kind == "Parameter").map { p =>
          p.children.find(_.kind == "Identifier").flatMap(_.text).getOrElse("_")
        }
        val body = implArg.children.find(_.kind == "Block").getOrElse {
          // Braceless arrow
          val exprBody = implArg.children.find(c => c.kind != "Parameter")
          exprBody match
            case Some(expr) =>
              RastNode("Block", 0, (0, 0), children = List(
                RastNode("ReturnStatement", 0, (0, 0), children = List(expr))
              ))
            case None =>
              RastNode("Block", 0, (0, 0))
        }
        Some(TerserEmitter.DefmethodEntry(className, methodName, params, body))

      case "Identifier" =>
        // Reference to a utility function (return_false, return_true, etc.)
        val refName = implArg.text.getOrElse("")
        val syntheticBody = refName match
          case "return_false" =>
            RastNode("Block", 0, (0, 0), children = List(
              RastNode("ReturnStatement", 0, (0, 0), children = List(
                RastNode("FalseKeyword", 0, (0, 0), value = Some(RastValue.Bool(false)))
              ))
            ))
          case "return_true" =>
            RastNode("Block", 0, (0, 0), children = List(
              RastNode("ReturnStatement", 0, (0, 0), children = List(
                RastNode("TrueKeyword", 0, (0, 0), value = Some(RastValue.Bool(true)))
              ))
            ))
          case "return_this" =>
            RastNode("Block", 0, (0, 0), children = List(
              RastNode("ReturnStatement", 0, (0, 0), children = List(
                RastNode("ThisKeyword", 0, (0, 0))
              ))
            ))
          case _ =>
            RastNode("Block", 0, (0, 0), children = List(
              RastNode("ReturnStatement", 0, (0, 0), children = List(
                RastNode("Identifier", 0, (0, 0), text = Some(refName))
              ))
            ))
        Some(TerserEmitter.DefmethodEntry(className, methodName, Nil, syntheticBody))

      case _ => None

  // --------------------------------------------------------------------------
  // DEFMETHOD-based module emission
  // --------------------------------------------------------------------------

  /** Emit a complete compress module from DEFMETHOD entries with translated bodies.
    *
    * Extracts DEFMETHODs (both direct and IIFE-wrapped) from the RAST,
    * translates each body using DefmethodBodyTranslator, and emits a
    * Scala object with all methods.
    */
  def emitDefmethodModule(
      file: RastFile,
      moduleName: String,
      objectName: String,
      hierarchy: List[TerserEmitter.DefnodeClass],
  ): (String, ModuleTranslationSummary) =
    val entries = extractAllDefmethods(file)
    val freeFns = TerserEmitter.extractFreeFunctions(file)

    val sb = new StringBuilder
    sb.append(s"package ssg\npackage js\npackage compress\n\n")
    sb.append(s"import ssg.js.ast._\n\n")
    sb.append(s"/** $objectName -- compress module with translated method bodies.\n")
    sb.append(s"  *\n")
    sb.append(s"  * Source: terser/lib/compress/$moduleName.js\n")
    sb.append(s"  * ${entries.size} DEFMETHOD entries, ${freeFns.size} free functions.\n")
    sb.append(s"  */\n")
    sb.append(s"object $objectName {\n\n")

    var full = 0
    var partial = 0
    var refused = 0
    var totalRefusals = 0

    // Group DEFMETHODs by method name for families
    val byMethod = entries.groupBy(_.methodName)
    val sortedMethods = byMethod.keys.toList.sorted

    for methodName <- sortedMethods do
      val family = byMethod(methodName)
      val scalaMethod = snakeToCamel(methodName)
      sb.append(s"  // --- $methodName (${family.size} overrides) ---\n\n")

      for entry <- family do
        val scalaClass = astVarToScalaName(entry.className)
        val result = DefmethodBodyTranslator.translateBody(entry, hierarchy, "    ")
        totalRefusals += result.refusalCount
        if result.isComplete then full += 1
        else if result.refusalCount <= 2 then partial += 1
        else refused += 1

        val paramDecls = entry.params.map(p => s"${snakeToCamel(p)}: Any")
        val paramStr = if paramDecls.isEmpty then "" else s"(${paramDecls.mkString(", ")})"
        val statusComment =
          if result.isComplete then ""
          else s" /* ${result.refusalCount} untranslated: ${result.refusalReasons.take(3).mkString(", ")} */"

        sb.append(s"  /** $scalaClass.$scalaMethod */\n")
        sb.append(s"  def ${scalaMethod}_${scalaClass}$paramStr: Any =$statusComment\n")
        sb.append(result.scalaBody)
        sb.append("\n")

    // Free functions
    if freeFns.nonEmpty then
      sb.append("  // --- Free functions ---\n\n")
      for fn <- freeFns do
        val scalaName = snakeToCamel(fn.name)
        val fnEntry = TerserEmitter.DefmethodEntry("_free_", fn.name, fn.params, fn.bodyNode)
        val result = DefmethodBodyTranslator.translateBody(fnEntry, hierarchy, "    ")
        totalRefusals += result.refusalCount
        if result.isComplete then full += 1
        else if result.refusalCount <= 2 then partial += 1
        else refused += 1

        val paramDecls = fn.params.map(p => s"${snakeToCamel(p)}: Any")
        val paramStr = if paramDecls.isEmpty then "" else s"(${paramDecls.mkString(", ")})"
        val statusComment =
          if result.isComplete then ""
          else s" /* ${result.refusalCount} untranslated: ${result.refusalReasons.take(3).mkString(", ")} */"

        sb.append(s"  def $scalaName$paramStr: Any =$statusComment\n")
        sb.append(result.scalaBody)
        sb.append("\n")

    sb.append("}\n")

    val summary = ModuleTranslationSummary(
      moduleName = moduleName,
      objectName = objectName,
      defmethodCount = entries.size,
      freeFunctionCount = freeFns.size,
      fullyTranslated = full,
      partiallyTranslated = partial,
      refused = refused,
      totalRefusalCount = totalRefusals,
    )

    (sb.toString, summary)

  // --------------------------------------------------------------------------
  // Free-function-based module emission
  // --------------------------------------------------------------------------

  /** Emit a compress module that consists entirely of free functions.
    *
    * Modules like common.js, tighten-body.js, inline.js contain standalone
    * function declarations rather than DEFMETHOD calls. These are translated
    * using the same DefmethodBodyTranslator infrastructure.
    */
  def emitFreeFunctionModule(
      file: RastFile,
      moduleName: String,
      objectName: String,
      hierarchy: List[TerserEmitter.DefnodeClass],
  ): (String, ModuleTranslationSummary) =
    val freeFns = TerserEmitter.extractFreeFunctions(file)
    val constants = extractModuleConstants(file)

    val sb = new StringBuilder
    sb.append(s"package ssg\npackage js\npackage compress\n\n")
    sb.append(s"import ssg.js.ast._\n\n")
    sb.append(s"/** $objectName -- compress utility module with translated bodies.\n")
    sb.append(s"  *\n")
    sb.append(s"  * Source: terser/lib/compress/$moduleName.js\n")
    sb.append(s"  * ${freeFns.size} free functions, ${constants.size} constants.\n")
    sb.append(s"  */\n")
    sb.append(s"object $objectName {\n\n")

    var full = 0
    var partial = 0
    var refused = 0
    var totalRefusals = 0

    // Emit constants first
    for (name, value) <- constants do
      val scalaName = snakeToCamel(name)
      sb.append(s"  val $scalaName: Any = $value\n\n")

    // Emit functions
    for fn <- freeFns do
      val scalaName = snakeToCamel(fn.name)
      val fnEntry = TerserEmitter.DefmethodEntry("_free_", fn.name, fn.params, fn.bodyNode)
      val result = DefmethodBodyTranslator.translateBody(fnEntry, hierarchy, "    ")
      totalRefusals += result.refusalCount
      if result.isComplete then full += 1
      else if result.refusalCount <= 2 then partial += 1
      else refused += 1

      val paramDecls = fn.params.map(p => s"${snakeToCamel(p)}: Any")
      val paramStr = if paramDecls.isEmpty then "" else s"(${paramDecls.mkString(", ")})"
      val statusComment =
        if result.isComplete then ""
        else s" /* ${result.refusalCount} untranslated: ${result.refusalReasons.take(3).mkString(", ")} */"

      sb.append(s"  def $scalaName$paramStr: Any =$statusComment\n")
      sb.append(result.scalaBody)
      sb.append("\n")

    sb.append("}\n")

    val summary = ModuleTranslationSummary(
      moduleName = moduleName,
      objectName = objectName,
      defmethodCount = 0,
      freeFunctionCount = freeFns.size,
      fullyTranslated = full,
      partiallyTranslated = partial,
      refused = refused,
      totalRefusalCount = totalRefusals,
    )

    (sb.toString, summary)

  // --------------------------------------------------------------------------
  // Batch emission for all compress modules
  // --------------------------------------------------------------------------

  /** Descriptor for a compress module to emit. */
  final case class CompressModule(
      moduleName: String,
      objectName: String,
      rastResource: String,
      isDeFmethod: Boolean,
  )

  /** All compress modules that should receive body translation. */
  val AllModules: List[CompressModule] = List(
    CompressModule("index",                  "CompressIndex",       "/rast/terser/lib/compress/index.rast.json",                  true),
    CompressModule("inference",              "Inference",           "/rast/terser/lib/compress/inference.rast.json",               true),
    CompressModule("evaluate",               "Evaluate",            "/rast/terser/lib/compress/evaluate.rast.json",                true),
    CompressModule("global-defs",            "GlobalDefs",          "/rast/terser/lib/compress/global-defs.rast.json",             true),
    CompressModule("drop-side-effect-free",  "DropSideEffectFree",  "/rast/terser/lib/compress/drop-side-effect-free.rast.json",   true),
    CompressModule("drop-unused",            "DropUnused",          "/rast/terser/lib/compress/drop-unused.rast.json",             true),
    CompressModule("reduce-vars",            "ReduceVars",          "/rast/terser/lib/compress/reduce-vars.rast.json",             true),
    CompressModule("common",                 "CompressCommon",      "/rast/terser/lib/compress/common.rast.json",                  false),
    CompressModule("tighten-body",           "TightenBody",         "/rast/terser/lib/compress/tighten-body.rast.json",            false),
    CompressModule("inline",                 "Inline",              "/rast/terser/lib/compress/inline.rast.json",                  false),
  )

  /** Emit all compress modules, returning summaries. */
  def emitAll(
      loadRast: String => RastFile,
      hierarchy: List[TerserEmitter.DefnodeClass],
  ): List[(CompressModule, String, ModuleTranslationSummary)] =
    AllModules.map { mod =>
      val file = loadRast(mod.rastResource)
      val (source, summary) =
        if mod.isDeFmethod then emitDefmethodModule(file, mod.moduleName, mod.objectName, hierarchy)
        else emitFreeFunctionModule(file, mod.moduleName, mod.objectName, hierarchy)
      (mod, source, summary)
    }

  /** Format a summary table for console output. */
  def formatSummaryTable(summaries: List[ModuleTranslationSummary]): String =
    val sb = new StringBuilder
    sb.append(f"${"Module"}%-25s ${"DM"}%4s ${"FF"}%4s ${"Full"}%6s ${"Part"}%6s ${"Ref"}%5s ${"Refusals"}%9s\n")
    sb.append("-" * 65)
    sb.append("\n")
    var totalDm = 0; var totalFf = 0; var totalFull = 0; var totalPart = 0; var totalRef = 0; var totalRefusals = 0
    for s <- summaries do
      sb.append(f"${s.moduleName}%-25s ${s.defmethodCount}%4d ${s.freeFunctionCount}%4d ${s.fullyTranslated}%6d ${s.partiallyTranslated}%6d ${s.refused}%5d ${s.totalRefusalCount}%9d\n")
      totalDm += s.defmethodCount; totalFf += s.freeFunctionCount; totalFull += s.fullyTranslated
      totalPart += s.partiallyTranslated; totalRef += s.refused; totalRefusals += s.totalRefusalCount
    sb.append("-" * 65)
    sb.append("\n")
    sb.append(f"${"TOTAL"}%-25s ${totalDm}%4d ${totalFf}%4d ${totalFull}%6d ${totalPart}%6d ${totalRef}%5d ${totalRefusals}%9d\n")
    val totalMethods = totalDm + totalFf
    val pctFull = if totalMethods > 0 then (totalFull * 100.0 / totalMethods) else 0.0
    sb.append(f"\nFully translated: $totalFull/$totalMethods (${pctFull}%.1f%%)\n")
    sb.toString

  // --------------------------------------------------------------------------
  // Private helpers
  // --------------------------------------------------------------------------

  /** Extract top-level constant declarations from a module. */
  private def extractModuleConstants(file: RastFile): List[(String, String)] =
    val result = mutable.ListBuffer.empty[(String, String)]
    for node <- file.nodes do
      if node.kind == "VariableStatement" then
        for vdl <- node.children.find(_.kind == "VariableDeclarationList")
            vd <- vdl.children.filter(_.kind == "VariableDeclaration") do
          val name = vd.children.headOption.flatMap(_.text).getOrElse("")
          val rhs = vd.children.lift(1)
          rhs match
            case Some(lit) if lit.kind == "NumericLiteral" =>
              val value = lit.value match
                case Some(RastValue.Num(n)) =>
                  if n == n.toLong then n.toLong.toString else n.toString
                case _ => "0"
              if name.nonEmpty then result += ((name, value))
            case Some(lit) if lit.kind == "StringLiteral" =>
              val value = lit.value match
                case Some(RastValue.Str(s)) => "\"" + s.replace("\"", "\\\"") + "\""
                case _ => "\"\""
              if name.nonEmpty then result += ((name, value))
            case Some(kw) if kw.kind == "TrueKeyword" =>
              if name.nonEmpty then result += ((name, "true"))
            case Some(kw) if kw.kind == "FalseKeyword" =>
              if name.nonEmpty then result += ((name, "false"))
            case Some(kw) if kw.kind == "NullKeyword" =>
              if name.nonEmpty then result += ((name, "null"))
            case _ => ()
    result.toList

  private def astVarToScalaName(varName: String): String =
    if varName.startsWith("AST_") then "Ast" + varName.drop(4)
    else varName

  private def snakeToCamel(s: String): String =
    val parts = s.split("_")
    if parts.length <= 1 then s
    else parts.head + parts.tail.map(_.capitalize).mkString
