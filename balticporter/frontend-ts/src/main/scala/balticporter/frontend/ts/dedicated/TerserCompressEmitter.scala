package balticporter.frontend.ts.dedicated

import balticporter.frontend.ts.{RastFile, RastNode, RastValue}
import java.nio.file.{Files, Path}
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
    // Collect def_optimize(AST_Xxx, fn) wrapper calls
    result ++= extractDefOptimize(file)
    result.toList

  /** Extract `def_optimize(AST_Xxx, function(self, compressor) { ... })` calls.
    *
    * Terser's compress/index.js wraps DEFMETHOD("optimize", ...) in a helper:
    * `def_optimize(AST_Block, function(self, compressor) { ... })` which registers
    * `AST_Block.prototype.optimize = fn`. The RAST sees these as CallExpression
    * nodes with callee `def_optimize`.
    */
  def extractDefOptimize(file: RastFile): List[TerserEmitter.DefmethodEntry] =
    val result = mutable.ListBuffer.empty[TerserEmitter.DefmethodEntry]

    def walk(node: RastNode): Unit =
      if node.kind == "ExpressionStatement" then
        for call <- node.children if call.kind == "CallExpression" do
          val callee = call.children.headOption
          if callee.flatMap(_.text).contains("def_optimize") then
            val args = call.children.drop(1)
            if args.size >= 2 then
              val classArg = args.head
              val implArg = args(1)
              val className = classArg.text.getOrElse("")
              if className.startsWith("AST_") then
                implArg.kind match
                  case "FunctionExpression" | "ArrowFunction" =>
                    val params = implArg.children.filter(_.kind == "Parameter").map { p =>
                      p.children.find(_.kind == "Identifier").flatMap(_.text).getOrElse("_")
                    }
                    val body = implArg.children.find(_.kind == "Block").getOrElse(
                      RastNode("Block", 0, (0, 0)))
                    result += TerserEmitter.DefmethodEntry(className, "optimize", params, body)
                  case "Identifier" =>
                    // Reference to a named function: def_optimize(AST_Lambda, opt_AST_Lambda)
                    val refName = implArg.text.getOrElse("")
                    if refName.nonEmpty then
                      // Find the referenced function in the file
                      findNamedFunction(file, refName).foreach { fn =>
                        result += TerserEmitter.DefmethodEntry(className, "optimize", fn.params,
                          fn.bodyNode)
                      }
                  case _ => ()
      node.children.foreach(walk)

    file.nodes.foreach(walk)
    result.toList

  /** Find a named function declaration in a RAST file. */
  private def findNamedFunction(file: RastFile, name: String): Option[TerserEmitter.FreeFunction] =
    TerserEmitter.extractFreeFunctions(file).find(_.name == name)

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
    *
    * When a type oracle is provided, parameter types and return types are
    * derived from the hand-ported reference instead of defaulting to `Any`.
    */
  def emitDefmethodModule(
      file: RastFile,
      moduleName: String,
      objectName: String,
      hierarchy: List[TerserEmitter.DefnodeClass],
      oracle: Option[ReferenceTypeOracle.TypeOracle] = None,
  ): (String, ModuleTranslationSummary) =
    val entries = extractAllDefmethods(file)
    val freeFns = TerserEmitter.extractFreeFunctions(file)
    val refObj = oracle.map(_ => ReferenceTypeOracle.emitterToReferenceObject.getOrElse(objectName, objectName))

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
      val oracleSig = for o <- oracle; r <- refObj; sig <- o.get(r, scalaMethod) yield sig
      sb.append(s"  // --- $methodName (${family.size} overrides) ---\n\n")

      if family.size == 1 then
        // Single override: emit as a plain method
        val entry = family.head
        val scalaClass = astVarToScalaName(entry.className)
        val result = DefmethodBodyTranslator.translateBody(entry, hierarchy, "    ")
        totalRefusals += result.refusalCount
        if result.isComplete then full += 1
        else if result.refusalCount <= 2 then partial += 1
        else refused += 1

        val paramDecls = entry.params.map { p =>
          val camelP = snakeToCamel(p)
          val tpe = oracleSig.flatMap(_.params.find(_.name == camelP).map(_.tpe)).getOrElse("Any")
          s"$camelP: $tpe"
        }
        val paramStr = if paramDecls.isEmpty then s"(node: $scalaClass)" else s"(node: $scalaClass, ${paramDecls.mkString(", ")})"
        val retType = oracleSig.map(_.returnType).getOrElse("Any")
        val statusComment =
          if result.isComplete then ""
          else s" /* ${result.refusalCount} untranslated: ${result.refusalReasons.take(3).mkString(", ")} */"

        sb.append(s"  def $scalaMethod$paramStr: $retType =$statusComment\n")
        sb.append(result.scalaBody)
        sb.append("\n")
      else
        // Multiple overrides: emit as pattern-match function
        val allParams = family.flatMap(_.params).distinct
        val paramDecls = allParams.map { p =>
          val camelP = snakeToCamel(p)
          val tpe = oracleSig.flatMap(_.params.find(_.name == camelP).map(_.tpe)).getOrElse("Any")
          s"$camelP: $tpe"
        }
        val retType = oracleSig.map(_.returnType).getOrElse("Any")
        val paramStr = if paramDecls.isEmpty then "(node: AstNode)" else s"(node: AstNode, ${paramDecls.mkString(", ")})"
        sb.append(s"  def $scalaMethod$paramStr: $retType = node match {\n")

        for entry <- family do
          val scalaClass = astVarToScalaName(entry.className)
          val result = DefmethodBodyTranslator.translateBody(entry, hierarchy, "      ", thisBinding = "n")
          totalRefusals += result.refusalCount
          if result.isComplete then full += 1
          else if result.refusalCount <= 2 then partial += 1
          else refused += 1

          val statusComment =
            if result.isComplete then ""
            else s" /* ${result.refusalCount} untranslated */"

          sb.append(s"    case n: $scalaClass =>$statusComment\n")
          sb.append(result.scalaBody)

        sb.append(s"    case _ => ()\n")
        sb.append(s"  }\n\n")

    // Free functions
    if freeFns.nonEmpty then
      sb.append("  // --- Free functions ---\n\n")
      for fn <- freeFns do
        val scalaName = snakeToCamel(fn.name)
        val oracleSig = for o <- oracle; r <- refObj; sig <- o.get(r, scalaName) yield sig
        val fnEntry = TerserEmitter.DefmethodEntry("_free_", fn.name, fn.params, fn.bodyNode)
        val result = DefmethodBodyTranslator.translateBody(fnEntry, hierarchy, "    ")
        totalRefusals += result.refusalCount
        if result.isComplete then full += 1
        else if result.refusalCount <= 2 then partial += 1
        else refused += 1

        val paramDecls = fn.params.map { p =>
          val camelP = snakeToCamel(p)
          val tpe = oracleSig.flatMap(_.params.find(_.name == camelP).map(_.tpe)).getOrElse("Any")
          s"$camelP: $tpe"
        }
        val retType = oracleSig.map(_.returnType).getOrElse("Any")
        val paramStr = if paramDecls.isEmpty then "" else s"(${paramDecls.mkString(", ")})"
        val statusComment =
          if result.isComplete then ""
          else s" /* ${result.refusalCount} untranslated: ${result.refusalReasons.take(3).mkString(", ")} */"

        sb.append(s"  def $scalaName$paramStr: $retType =$statusComment\n")
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
    *
    * When a type oracle is provided, parameter types and return types are
    * derived from the hand-ported reference instead of defaulting to `Any`.
    */
  def emitFreeFunctionModule(
      file: RastFile,
      moduleName: String,
      objectName: String,
      hierarchy: List[TerserEmitter.DefnodeClass],
      oracle: Option[ReferenceTypeOracle.TypeOracle] = None,
  ): (String, ModuleTranslationSummary) =
    val freeFns = TerserEmitter.extractFreeFunctions(file)
    val constants = extractModuleConstants(file)
    val refObj = oracle.map(_ => ReferenceTypeOracle.emitterToReferenceObject.getOrElse(objectName, objectName))

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
      val oracleSig = for o <- oracle; r <- refObj; sig <- o.get(r, scalaName) yield sig
      val fnEntry = TerserEmitter.DefmethodEntry("_free_", fn.name, fn.params, fn.bodyNode)
      val result = DefmethodBodyTranslator.translateBody(fnEntry, hierarchy, "    ")
      totalRefusals += result.refusalCount
      if result.isComplete then full += 1
      else if result.refusalCount <= 2 then partial += 1
      else refused += 1

      val paramDecls = fn.params.map { p =>
        val camelP = snakeToCamel(p)
        val tpe = oracleSig.flatMap(_.params.find(_.name == camelP).map(_.tpe)).getOrElse("Any")
        s"$camelP: $tpe"
      }
      val retType = oracleSig.map(_.returnType).getOrElse("Any")
      val paramStr = if paramDecls.isEmpty then "" else s"(${paramDecls.mkString(", ")})"
      val statusComment =
        if result.isComplete then ""
        else s" /* ${result.refusalCount} untranslated: ${result.refusalReasons.take(3).mkString(", ")} */"

      sb.append(s"  def $scalaName$paramStr: $retType =$statusComment\n")
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

  // --------------------------------------------------------------------------
  // Non-compress module descriptors and parity-derive
  // --------------------------------------------------------------------------

  /** Descriptor for a non-compress Terser module. */
  final case class NonCompressModule(
      moduleName: String,
      objectName: String,
      rastResource: String,
      referenceSubPath: String,
      isDeFmethod: Boolean,
  )

  /** All non-compress Terser modules with both RAST and reference counterparts. */
  val AllNonCompressModules: List[NonCompressModule] = List(
    NonCompressModule("scope",          "ScopeAnalysis",  "/rast/terser/lib/scope.rast.json",          "scope/ScopeAnalysis.scala",   true),
    NonCompressModule("output",         "OutputStream",   "/rast/terser/lib/output.rast.json",         "output/OutputStream.scala",   true),
    NonCompressModule("size",           "AstSize",        "/rast/terser/lib/size.rast.json",            "ast/AstSize.scala",           true),
    NonCompressModule("equivalent-to",  "AstEquivalent",  "/rast/terser/lib/equivalent-to.rast.json",  "ast/AstEquivalent.scala",     true),
    NonCompressModule("propmangle",     "PropMangler",    "/rast/terser/lib/propmangle.rast.json",     "scope/PropMangler.scala",     false),
    NonCompressModule("transform",      "AstNode",        "/rast/terser/lib/transform.rast.json",      "ast/AstNode.scala",           true),
    NonCompressModule("mangler",        "Mangler",        "/rast/terser/lib/scope.rast.json",          "scope/Mangler.scala",         false),
  )

  /** Emit a non-compress module using parity-derive.
    *
    * @param rastFile the RAST for the module
    * @param referencePath path to the hand-ported .scala file
    * @param hierarchy the AST class hierarchy
    * @param isDeFmethod true for DEFMETHOD-based modules
    */
  def emitNonCompressWithParity(
      rastFile: RastFile,
      referencePath: Path,
      hierarchy: List[TerserEmitter.DefnodeClass],
      isDeFmethod: Boolean = true,
  ): (String, ParityEmitSummary) =
    emitWithParity(rastFile, referencePath, hierarchy, isDeFmethod)

  /** Emit all non-compress modules using parity-derive.
    *
    * @param loadRast function to load a RAST file from a resource path
    * @param hierarchy the AST class hierarchy
    * @param ssgJsRoot path to the ssg-js source root (e.g., .../ssg-js/src/main/scala/ssg/js)
    */
  def emitAllNonCompressWithParity(
      loadRast: String => RastFile,
      hierarchy: List[TerserEmitter.DefnodeClass],
      ssgJsRoot: Path,
  ): List[(NonCompressModule, String, ParityEmitSummary)] =
    AllNonCompressModules.flatMap { mod =>
      val refPath = ssgJsRoot.resolve(mod.referenceSubPath)
      if Files.exists(refPath) then
        val file = loadRast(mod.rastResource)
        val (source, summary) = emitNonCompressWithParity(file, refPath, hierarchy, mod.isDeFmethod)
        Some((mod, source, summary))
      else None
    }

  /** Format a non-compress parity summary table. */
  def formatNonCompressParitySummaryTable(summaries: List[ParityEmitSummary]): String =
    val sb = new StringBuilder
    sb.append(f"${"Module"}%-20s ${"Total"}%6s ${"RAST"}%6s ${"Ref"}%6s ${"Refusals"}%9s\n")
    sb.append("-" * 50)
    sb.append("\n")
    var tTotal = 0; var tRast = 0; var tRef = 0; var tRefusals = 0
    for s <- summaries do
      sb.append(f"${s.moduleName}%-20s ${s.totalMethods}%6d ${s.matchedFromRast}%6d ${s.keptFromReference}%6d ${s.refusalCount}%9d\n")
      tTotal += s.totalMethods; tRast += s.matchedFromRast; tRef += s.keptFromReference; tRefusals += s.refusalCount
    sb.append("-" * 50)
    sb.append("\n")
    sb.append(f"${"TOTAL"}%-20s ${tTotal}%6d ${tRast}%6d ${tRef}%6d ${tRefusals}%9d\n")
    val pctRast = if tTotal > 0 then (tRast * 100.0 / tTotal) else 0.0
    sb.append(f"\nRAST-derived bodies: $tRast/$tTotal (${pctRast}%.1f%%)\n")
    sb.toString

  /** Emit all compress modules, returning summaries.
    *
    * When a type oracle is provided, parameter types and return types are
    * derived from the hand-ported reference instead of defaulting to `Any`.
    */
  def emitAll(
      loadRast: String => RastFile,
      hierarchy: List[TerserEmitter.DefnodeClass],
      oracle: Option[ReferenceTypeOracle.TypeOracle] = None,
  ): List[(CompressModule, String, ModuleTranslationSummary)] =
    AllModules.map { mod =>
      val file = loadRast(mod.rastResource)
      val (source, summary) =
        if mod.isDeFmethod then emitDefmethodModule(file, mod.moduleName, mod.objectName, hierarchy, oracle)
        else emitFreeFunctionModule(file, mod.moduleName, mod.objectName, hierarchy, oracle)
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
    val result = if parts.length <= 1 then s
    else parts.head + parts.tail.map(_.capitalize).mkString
    if scalaKeywords.contains(result) then s"${result}_" else result

  private val scalaKeywords: Set[String] = Set(
    "type", "val", "var", "def", "class", "trait", "object", "enum",
    "match", "case", "if", "else", "for", "while", "do", "return",
    "throw", "try", "catch", "finally", "import", "export", "package",
    "new", "this", "super", "with", "extends", "yield", "abstract",
    "final", "sealed", "private", "protected", "override", "lazy",
    "implicit", "given", "using", "then", "end", "inline", "opaque",
    "transparent", "erased", "open", "infix",
  )

  /** Patterns in a translated RAST body that cannot compile in ssg.
    *
    * Patterns that HAVE been fixed in the body translator (and removed from here):
    *   - `makeNode(` → `translateMakeNode` handles `make_node(AST_X, orig, {props})`
    *   - `.TYPE ==` / `.TYPE !=` → `isTypePropertyAccess` + `isInstanceOf` lowering
    *   - `return ()` → now emits `null` instead
    *   - `this.` → now handled via `nodeNames` set + `thisBinding`
    *   - `walkAbort` → mapped to `TreeWalker.WalkAbort`
    *   - `walk(` → mapped to `.walk()` method call
    *   - `hasFlag(` → mapped to `CompressorFlags.hasFlag`
    *   - `MAP(` → mapped to `.map()` call
    *   - `Number(` → mapped to `.toDouble`
    *   - `foldLeft(` / `foldRight(` → mapped to `.reduce()` / `.reduceRight()`
    *   - `.join(` → mapped to `.mkString()`
    *   - `node.operator` etc. → now allowed when in typed DEFMETHOD context
    *   - `.size()` → mapped to `AstSize.size()`
    *   - `.getValue()` → now a normal method call
    *   - `.definition()` → now a normal method call
    *
    * Remaining: constructs with no mechanical Scala equivalent.
    */
  private val uncompilablePatterns: List[String] = List(
    "DEFMETHOD(",          // JS DEFMETHOD — meta-programming, genuinely uncompilable
  )

  /** True when a translated body contains JS-API constructs that will not
    * compile in ssg.  When true, the reference body is kept instead.
    */
  private def containsUncompilablePatterns(body: String): Boolean =
    uncompilablePatterns.exists(body.contains)

  // --------------------------------------------------------------------------
  // Parity-derive emission: reference structure + RAST bodies
  // --------------------------------------------------------------------------

  /** Summary of parity-derive emission for one module. */
  final case class ParityEmitSummary(
      moduleName: String,
      objectName: String,
      totalMethods: Int,
      matchedFromRast: Int,
      keptFromReference: Int,
      refusalCount: Int,
      matchDetails: List[(String, String)],
  )

  /** A parsed method from the reference file. */
  final case class ParsedMethod(
      name: String,
      signatureLine: Int,
      bodyStartLine: Int,
      bodyEndLine: Int,
      isPrivate: Boolean,
  )

  /** Emit a compress module using parity-derive: the reference file's structure
    * (package, imports, object name, method signatures, types) with RAST-translated
    * bodies where a match is found.
    *
    * For methods where no RAST body matches, the reference body is kept as-is.
    * This produces code that compiles in ssg because the structure matches the
    * hand-ported reference exactly.
    *
    * @param rastFile the RAST for this compress module
    * @param referencePath path to the hand-ported .scala file
    * @param hierarchy the AST class hierarchy for body translation
    * @param isDeFmethod true for DEFMETHOD modules, false for free-function modules
    */
  def emitWithParity(
      rastFile: RastFile,
      referencePath: Path,
      hierarchy: List[TerserEmitter.DefnodeClass],
      isDeFmethod: Boolean = false,
  ): (String, ParityEmitSummary) =
    val referenceSource = new String(Files.readAllBytes(referencePath))
    val lines = referenceSource.split("\n", -1).toList

    // Modules where body interleaving causes more errors than it fixes:
    // use reference bodies for ALL methods (the RAST structure was validated,
    // but findBodyEnd can't handle all Scala method boundary patterns)
    val skipRastModules = Set.empty[String]
    val refObjectName = lines.find(_.matches("^(object|class)\\s+.*\\{.*$"))
      .flatMap("""^(object|class)\s+(\w+)""".r.findFirstMatchIn(_).map(_.group(2)))
      .getOrElse("")
    if skipRastModules.contains(refObjectName) then
      val nMethods = findMethodBoundaries(lines).size
      val summary = ParityEmitSummary(
        moduleName = refObjectName,
        objectName = refObjectName,
        totalMethods = nMethods,
        matchedFromRast = 0,
        keptFromReference = nMethods,
        refusalCount = 0,
        matchDetails = Nil,
      )
      return (referenceSource, summary)

    // Extract RAST functions and build name map: camelCase -> translated body
    val rastBodies = buildRastBodyMap(rastFile, hierarchy, isDeFmethod)

    // Find all method boundaries in the reference file
    val methods = findMethodBoundaries(lines)

    // Determine the reference file's object name for the summary
    val objectName = lines.find(_.matches("^(object|class)\\s+.*\\{.*$"))
      .flatMap("""^(object|class)\s+(\w+)""".r.findFirstMatchIn(_).map(_.group(2)))
      .getOrElse("Unknown")

    val moduleName = referencePath.getFileName.toString.stripSuffix(".scala")

    // Build the output by replacing method bodies where we have RAST matches
    val sb = new StringBuilder
    val matchDetails = mutable.ListBuffer.empty[(String, String)]
    var totalRefusals = 0

    var lineIdx = 0
    var methodIdx = 0

    while lineIdx < lines.size do
      if methodIdx < methods.size && lineIdx == methods(methodIdx).signatureLine then
        val method = methods(methodIdx)
        val camelName = method.name

        // Look up matching RAST body — only use it when the translation
        // does not contain un-compilable JS-API patterns.
        val usableRast = rastBodies.get(camelName).filter { case (body, _) =>
          !containsUncompilablePatterns(body)
        }
        usableRast match
          case Some((translatedBody, refusals)) =>
            // Emit the signature, stripping any trailing `{` after `=` so
            // the RAST body can provide its own structure.
            val sigEndLineIdx = findSignatureEnd(lines, method.signatureLine)
            for i <- method.signatureLine to sigEndLineIdx do
              val line = lines(i)
              if i == sigEndLineIdx then
                val eqIdx = findEqualsInSignature(line)
                if eqIdx >= 0 then
                  // Emit up to and including `=`, stripping trailing `{` and whitespace
                  sb.append(line.substring(0, eqIdx + 1))
                  sb.append("\n")
                else
                  sb.append(line)
                  sb.append("\n")
              else
                sb.append(line)
                sb.append("\n")

            // Emit translated RAST body
            sb.append(translatedBody)

            // Skip original body lines
            lineIdx = method.bodyEndLine + 1
            matchDetails += ((camelName, "rast"))
            totalRefusals += refusals

          case _ =>
            // No RAST match or private method: keep original
            for i <- method.signatureLine to method.bodyEndLine do
              sb.append(lines(i))
              sb.append("\n")
            lineIdx = method.bodyEndLine + 1
            matchDetails += ((camelName, "reference"))

        methodIdx += 1
      else
        sb.append(lines(lineIdx))
        sb.append("\n")
        lineIdx += 1

    val matched = matchDetails.count(_._2 == "rast")
    val kept = matchDetails.count(_._2 == "reference")

    val summary = ParityEmitSummary(
      moduleName = moduleName,
      objectName = objectName,
      totalMethods = methods.size,
      matchedFromRast = matched,
      keptFromReference = kept,
      refusalCount = totalRefusals,
      matchDetails = matchDetails.toList,
    )

    (sb.toString, summary)

  /** Build a map from camelCase method name to (translated body text, refusal count).
    *
    * Extracts both DEFMETHOD entries and free functions from the RAST, translates
    * each body, and builds the lookup map using the snakeToCamel conversion.
    */
  private def buildRastBodyMap(
      rastFile: RastFile,
      hierarchy: List[TerserEmitter.DefnodeClass],
      isDeFmethod: Boolean,
  ): Map[String, (String, Int)] =
    val result = mutable.Map.empty[String, (String, Int)]

    if isDeFmethod then
      val entries = extractAllDefmethods(rastFile)
      for entry <- entries do
        val camelName = snakeToCamel(entry.methodName)
        val translated = DefmethodBodyTranslator.translateBody(entry, hierarchy, "    ")
        result(camelName) = (translated.scalaBody, translated.refusalCount)
        // Also store under methodName+ClassName alias for hand-ports that
        // inlined DEFMETHOD dispatch: AST_Block.optimize → optimizeBlock
        if entry.className.startsWith("AST_") then
          val classShort = entry.className.drop(4) // "AST_Block" → "Block"
          val aliasName = camelName + classShort
          result(aliasName) = (translated.scalaBody, translated.refusalCount)
          // Also lowercase first: optimizeBlock, not optimizeblock
          val lowerAlias = camelName + classShort.head.toUpper + classShort.tail
          if lowerAlias != aliasName then
            result(lowerAlias) = (translated.scalaBody, translated.refusalCount)

    val freeFns = TerserEmitter.extractFreeFunctions(rastFile)
    for fn <- freeFns do
      val camelName = snakeToCamel(fn.name)
      val fnEntry = TerserEmitter.DefmethodEntry("_free_", fn.name, fn.params, fn.bodyNode)
      val translated = DefmethodBodyTranslator.translateBody(fnEntry, hierarchy, "    ")
      result(camelName) = (translated.scalaBody, translated.refusalCount)

    result.toMap

  /** Find method boundaries in a reference Scala file.
    *
    * Returns a list of ParsedMethod entries describing each method's line range.
    * The signatureLine is the first line of the def, bodyStartLine is the first
    * line of the body (after the `=`), and bodyEndLine is the last line of the
    * body (inclusive).
    */
  def findMethodBoundaries(lines: List[String]): List[ParsedMethod] =
    val result = mutable.ListBuffer.empty[ParsedMethod]
    val defPattern = """^\s{2}(private\s+)?def\s+(`?\w+`?)""".r
    var i = 0

    while i < lines.size do
      defPattern.findFirstMatchIn(lines(i)) match
        case Some(m) =>
          val isPrivate = m.group(1) != null
          val name = m.group(2).stripPrefix("`").stripSuffix("`")

          // Find the end of the signature (the line containing `=`)
          val sigEndLine = findSignatureEnd(lines, i)

          // Find the end of the method body
          val bodyEndLine = findBodyEnd(lines, sigEndLine)

          // Body starts on the line after the signature end, or on the same
          // line if the `=` has code after it
          val bodyStartLine =
            val sigLine = lines(sigEndLine)
            val eqIdx = findEqualsInSignature(sigLine)
            val afterEq = if eqIdx >= 0 then sigLine.substring(eqIdx + 1).trim else ""
            if afterEq.nonEmpty && afterEq != "{" then sigEndLine
            else sigEndLine + 1

          result += ParsedMethod(name, i, bodyStartLine, bodyEndLine, isPrivate)
          i = bodyEndLine + 1

        case None =>
          i += 1

    result.toList

  /** Find the line where the method signature ends (the line containing `=`).
    *
    * Handles multi-line signatures by tracking parenthesis depth.
    */
  private[dedicated] def findSignatureEnd(lines: List[String], startLine: Int): Int =
    var depth = 0
    var i = startLine
    while i < lines.size do
      val line = lines(i)
      for ch <- line do
        ch match
          case '(' | '[' => depth += 1
          case ')' | ']' => depth -= 1
          case _ => ()
      // The signature ends on the line where parens are balanced and we find `=`
      if depth <= 0 && findEqualsInSignature(line) >= 0 then
        return i
      i += 1
    // Fallback: return start line
    startLine

  /** Find the position of the `=` that ends a method signature.
    *
    * Scans from right to left for a standalone `=` preceded by whitespace.
    * Rejects `==`, `!=`, `<=`, `>=`, and `=>`. Handles both `def f(): T = {`
    * (at end) and `def f(): T = expr` (in middle).
    */
  private[dedicated] def findEqualsInSignature(line: String): Int =
    var i = line.length - 1
    while i >= 1 do
      if line(i) == '=' then
        val prev = line(i - 1)
        val next = if i + 1 < line.length then line(i + 1) else ' '
        // Must be preceded by whitespace; not part of ==, !=, <=, >=, or =>
        if (prev == ' ' || prev == '\t') && next != '>' && next != '=' then
          return i
      i -= 1
    -1

  /** Find the last line of a method body, given the line where the signature
    * ends (containing `=`).
    *
    * For braced bodies, counts braces to find the matching close. For non-braced
    * bodies, finds the end by indentation.
    */
  private def findBodyEnd(lines: List[String], sigEndLine: Int): Int =
    val sigLine = lines(sigEndLine)
    val eqIdx = findEqualsInSignature(sigLine)
    val afterEq = if eqIdx >= 0 then sigLine.substring(eqIdx + 1).trim else ""

    // Check if this is a braced body
    if afterEq == "{" || afterEq.startsWith("{") then
      // Count braces starting from after the `=`
      findMatchingBrace(lines, sigEndLine, eqIdx + 1)
    else if afterEq.nonEmpty then
      // Body on the same line as `=` — use indentation; the first branch
      // already caught `afterEq` starting with `{`.
      findExpressionEnd(lines, sigEndLine)
    else
      // Body starts on next line — only treat as brace-delimited when the
      // next line is literally `{` (the method body itself is one block).
      // Lines like `if (...) {`, `boundary[T] {`, `value match {` are
      // expression bodies whose internal braces do not delimit the method;
      // use indentation for those.
      if sigEndLine + 1 < lines.size then
        val nextLine = lines(sigEndLine + 1).trim
        if nextLine == "{" then
          findMatchingBrace(lines, sigEndLine + 1, 0)
        else
          findExpressionEnd(lines, sigEndLine + 1)
      else
        sigEndLine

  /** Find the matching closing brace, starting from a given position in the file.
    */
  private def findMatchingBrace(lines: List[String], startLine: Int, startCol: Int): Int =
    var depth = 0
    var i = startLine
    var foundFirstBrace = false
    while i < lines.size do
      val line = lines(i)
      val startJ = if i == startLine then startCol else 0
      var j = startJ
      while j < line.length do
        val ch = line(j)
        // Skip string literals (simplistic: assume no multi-line strings in method bodies)
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
          if foundFirstBrace && depth == 0 then
            return i
        j += 1
      i += 1
    // Fallback
    if startLine < lines.size - 1 then lines.size - 1 else startLine

  /** Find the end of a non-braced expression body.
    *
    * The expression continues as long as subsequent lines are indented more than
    * the base indentation level (2 spaces for top-level methods). An empty line
    * does not end the expression if the next non-empty line is still indented.
    */
  private def findExpressionEnd(lines: List[String], startLine: Int): Int =
    val baseIndent = 2 // top-level methods in an object
    var lastContentLine = startLine
    var i = startLine + 1

    while i < lines.size do
      val line = lines(i)
      if line.trim.isEmpty then
        // Empty line - check if the next non-empty line continues the expression
        var nextNonEmpty = i + 1
        while nextNonEmpty < lines.size && lines(nextNonEmpty).trim.isEmpty do
          nextNonEmpty += 1
        if nextNonEmpty < lines.size then
          val nextLine = lines(nextNonEmpty)
          val nextIndent = nextLine.takeWhile(_ == ' ').length
          if nextIndent > baseIndent && !nextLine.trim.startsWith("def ") &&
             !nextLine.trim.startsWith("private def ") &&
             !nextLine.trim.startsWith("//") &&
             !nextLine.trim.startsWith("/*") &&
             !nextLine.trim.startsWith("val ") &&
             !nextLine.trim.startsWith("var ") &&
             !nextLine.trim.startsWith("class ") &&
             !nextLine.trim.startsWith("object ") &&
             nextLine.trim != "}" then
            i = nextNonEmpty
            // continue
          else
            return lastContentLine
        else
          return lastContentLine
      else
        val indent = line.takeWhile(_ == ' ').length
        if indent <= baseIndent then
          // We've reached a line at the base level or less - method body ended
          return lastContentLine
        else
          lastContentLine = i
          i += 1

    lastContentLine

  /** Emit all compress modules using parity-derive.
    *
    * @param loadRast function to load a RAST file from a resource path
    * @param hierarchy the AST class hierarchy
    * @param referenceRoot path to the compress module directory in ssg-js
    */
  def emitAllWithParity(
      loadRast: String => RastFile,
      hierarchy: List[TerserEmitter.DefnodeClass],
      referenceRoot: Path,
  ): List[(CompressModule, String, ParityEmitSummary)] =
    AllModules.flatMap { mod =>
      val refFileName = ReferenceTypeOracle.emitterToReferenceObject
        .getOrElse(mod.objectName, mod.objectName) + ".scala"
      val refPath = referenceRoot.resolve(refFileName)
      if Files.exists(refPath) then
        val file = loadRast(mod.rastResource)
        val (source, summary) = emitWithParity(file, refPath, hierarchy, mod.isDeFmethod)
        Some((mod, source, summary))
      else None
    }

  /** Format a parity summary table for console output. */
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
