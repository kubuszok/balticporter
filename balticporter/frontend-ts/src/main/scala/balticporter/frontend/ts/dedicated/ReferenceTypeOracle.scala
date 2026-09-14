package balticporter.corpus.terser

import balticporter.frontend.ts.dedicated.{DefmethodBodyTranslator, DefmethodEntry, DefnodeClass, FreeFunction}

import scala.collection.mutable

/** Extracts method signatures from hand-ported Scala reference files to serve
  * as a type oracle for the Terser compress emitter.
  *
  * JavaScript has no types, so the RAST types everything as `Any`. The
  * hand-ported ssg-js reference HAS types for every parameter and field.
  * This oracle reads those signatures and provides a lookup by
  * `(objectName, methodName)` for the emitter to use.
  */
object ReferenceTypeOracle:

  /** A method parameter with its name and type annotation. */
  final case class ParamSig(name: String, tpe: String)

  /** A complete method signature extracted from a reference file. */
  final case class MethodSig(
      methodName: String,
      params: List[ParamSig],
      returnType: String,
  )

  /** Type oracle: maps `(objectName, methodName)` to the reference signature.
    *
    * Lookups try exact match first, then case-insensitive on the method name
    * to handle differences like `isNumberOrBigint` vs `isNumberOrBigInt`. */
  final case class TypeOracle(
      methods: Map[(String, String), MethodSig],
  ):
    /** Secondary index: `(objectName, lowercasedMethodName)` for fallback. */
    private lazy val byLower: Map[(String, String), MethodSig] =
      methods.map { case ((obj, name), sig) => ((obj, name.toLowerCase), sig) }

    private def lookup(objectName: String, methodName: String): Option[MethodSig] =
      methods.get((objectName, methodName))
        .orElse(byLower.get((objectName, methodName.toLowerCase)))

    /** Look up the type for a parameter by object, method, and param name.
      * Falls back to `Any` if not found.
      * Parameter lookup is also case-insensitive on the param name. */
    def paramType(objectName: String, methodName: String, paramName: String): String =
      lookup(objectName, methodName) match
        case Some(sig) =>
          val lowerParam = paramName.toLowerCase
          sig.params.find(_.name == paramName)
            .orElse(sig.params.find(_.name.toLowerCase == lowerParam))
            .map(_.tpe).getOrElse("Any")
        case None => "Any"

    /** Look up the return type for a method. Falls back to `Any`. */
    def returnType(objectName: String, methodName: String): String =
      lookup(objectName, methodName).map(_.returnType).getOrElse("Any")

    /** Look up the full signature for a method. */
    def get(objectName: String, methodName: String): Option[MethodSig] =
      lookup(objectName, methodName)

  /** Parse a reference Scala file and extract all public method signatures
    * from the top-level object.
    *
    * Handles both single-line and multi-line signatures. A multi-line
    * signature is recognized when a `def` line has unbalanced parentheses;
    * subsequent lines are accumulated until parentheses balance and a
    * return type plus `=` are found.
    */
  def parseFile(objectName: String, source: String): List[(String, MethodSig)] =
    val results = mutable.ListBuffer.empty[(String, MethodSig)]

    // First join multi-line defs into single logical lines
    val logicalLines = joinMultiLineSignatures(source)

    // Match def with params and return type
    val defPattern =
      """^\s{2}(?:private\s+|protected\s+)?def\s+(\w+)\s*(?:\[.*?\])?\s*\((.*)\)\s*:\s*(.+?)\s*=\s*(?:\{?\s*)?$""".r

    // Defs with no params
    val defNoParamsPattern =
      """^\s{2}(?:private\s+|protected\s+)?def\s+(\w+)\s*:\s*(.+?)\s*=\s*(?:\{?\s*)?$""".r

    // Multi-param-list: take the first param list only
    val defMultiParamPattern =
      """^\s{2}(?:private\s+|protected\s+)?def\s+(\w+)\s*(?:\[.*?\])?\s*\((.*?)\)\s*\(.*\)\s*:\s*(.+?)\s*=\s*(?:\{?\s*)?$""".r

    for line <- logicalLines do
      line match
        case defMultiParamPattern(name, paramStr, retType) =>
          val params = parseParams(paramStr)
          val sig = MethodSig(name, params, retType.trim)
          results += ((objectName, sig))

        case defPattern(name, paramStr, retType) =>
          val params = parseParams(paramStr)
          val sig = MethodSig(name, params, retType.trim)
          results += ((objectName, sig))

        case defNoParamsPattern(name, retType) =>
          val sig = MethodSig(name, Nil, retType.trim)
          results += ((objectName, sig))

        case _ => ()

    results.toList

  /** Join multi-line method signatures into single logical lines.
    *
    * A line starting with `  def ` or `  private def ` that has unbalanced
    * parentheses gets subsequent lines appended (whitespace-normalized)
    * until parens balance and `: RetType =` is found.
    */
  private def joinMultiLineSignatures(source: String): List[String] =
    val result = mutable.ListBuffer.empty[String]
    val defStart = """^\s{2}(?:private\s+|protected\s+)?def\s+""".r
    var accumulator: StringBuilder = null
    var parenDepth = 0

    for line <- source.linesIterator do
      if accumulator != null then
        // Continue accumulating
        accumulator.append(" ")
        accumulator.append(line.trim)
        for ch <- line do
          ch match
            case '(' => parenDepth += 1
            case ')' => parenDepth -= 1
            case _ => ()
        if parenDepth <= 0 && (line.contains("=") || line.trim.endsWith("=")) then
          result += accumulator.toString
          accumulator = null
          parenDepth = 0
        else if parenDepth < 0 then
          // Something went wrong, bail
          result += accumulator.toString
          accumulator = null
          parenDepth = 0
      else if defStart.findFirstIn(line).isDefined then
        // Count parens
        var depth = 0
        for ch <- line do
          ch match
            case '(' => depth += 1
            case ')' => depth -= 1
            case _ => ()
        if depth <= 0 then
          // Single-line signature
          result += line
        else
          // Multi-line: start accumulating
          accumulator = new StringBuilder(line)
          parenDepth = depth
      else
        result += line

    if accumulator != null then
      result += accumulator.toString

    result.toList

  /** Parse a comma-separated parameter list, handling nested generics and
    * union types. */
  private def parseParams(paramStr: String): List[ParamSig] =
    if paramStr.trim.isEmpty then return Nil

    val params = mutable.ListBuffer.empty[ParamSig]
    val current = new StringBuilder
    var depth = 0 // track nesting of [], (), {}

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

    if current.nonEmpty then
      parseOneParam(current.toString.trim).foreach(params += _)

    params.toList

  /** Parse a single `name: Type` or `name: Type = default` parameter. */
  private def parseOneParam(param: String): Option[ParamSig] =
    // Skip `using` and `implicit` parameters
    val trimmed = param.trim
    if trimmed.startsWith("using ") || trimmed.startsWith("implicit ") then
      return None

    // Find the colon separating name from type, handling backtick-quoted names
    val colonIdx =
      if trimmed.startsWith("`") then
        val endBacktick = trimmed.indexOf('`', 1)
        if endBacktick >= 0 then trimmed.indexOf(':', endBacktick + 1)
        else trimmed.indexOf(':')
      else trimmed.indexOf(':')

    if colonIdx < 0 then return None

    val name = trimmed.substring(0, colonIdx).trim
    val typeAndDefault = trimmed.substring(colonIdx + 1).trim

    // Strip default value
    val tpe = stripDefault(typeAndDefault)

    if name.nonEmpty && tpe.nonEmpty then
      Some(ParamSig(cleanParamName(name), tpe))
    else None

  /** Strip a default value from a type string, respecting nesting. */
  private def stripDefault(s: String): String =
    var depth = 0
    var equalsAt = -1
    var i = 0
    while i < s.length && equalsAt < 0 do
      s.charAt(i) match
        case '(' | '[' | '{' => depth += 1
        case ')' | ']' | '}' => depth -= 1
        case '=' if depth == 0 => equalsAt = i
        case _ => ()
      i += 1
    if equalsAt >= 0 then s.substring(0, equalsAt).trim else s.trim

  /** Clean backtick-quoted parameter names. */
  private def cleanParamName(name: String): String =
    if name.startsWith("`") && name.endsWith("`") then
      name.substring(1, name.length - 1)
    else name

  // -------------------------------------------------------------------------
  // Mapping from emitter object names to reference object names
  // -------------------------------------------------------------------------

  /** Map from emitter object name to the reference file's object name.
    * The emitter uses names like `CompressCommon` while the reference uses `Common`. */
  val emitterToReferenceObject: Map[String, String] = Map(
    // Compress modules
    "CompressCommon"      -> "Common",
    "CompressIndex"       -> "Compressor",
    "Inference"           -> "Inference",
    "Evaluate"            -> "Evaluate",
    "GlobalDefs"          -> "GlobalDefs",
    "DropSideEffectFree"  -> "DropSideEffectFree",
    "DropUnused"          -> "DropUnused",
    "ReduceVars"          -> "ReduceVars",
    "TightenBody"         -> "TightenBody",
    "Inline"              -> "Inline",
    // Non-compress modules
    "ScopeAnalysis"       -> "ScopeAnalysis",
    "OutputStream"        -> "OutputStream",
    "AstSize"             -> "AstSize",
    "AstEquivalent"       -> "AstEquivalent",
    "PropMangler"         -> "PropMangler",
    "Mangler"             -> "Mangler",
  )

  /** Map from reference file's object name to the source file name. */
  val referenceObjectToFile: Map[String, String] = Map(
    // Compress modules
    "Common"             -> "Common.scala",
    "Compressor"         -> "Compressor.scala",
    "Inference"          -> "Inference.scala",
    "Evaluate"           -> "Evaluate.scala",
    "GlobalDefs"         -> "GlobalDefs.scala",
    "DropSideEffectFree" -> "DropSideEffectFree.scala",
    "DropUnused"         -> "DropUnused.scala",
    "ReduceVars"         -> "ReduceVars.scala",
    "TightenBody"        -> "TightenBody.scala",
    "Inline"             -> "Inline.scala",
    "Hoisting"           -> "Hoisting.scala",
    "CompressorLike"     -> "CompressorLike.scala",
    "CompressorFlags"    -> "CompressorFlags.scala",
    "NativeObjects"      -> "NativeObjects.scala",
    "CompressorOptions"  -> "CompressorOptions.scala",
    // Non-compress modules (sub-paths from ssg-js root)
    "ScopeAnalysis"      -> "scope/ScopeAnalysis.scala",
    "OutputStream"       -> "output/OutputStream.scala",
    "AstSize"            -> "ast/AstSize.scala",
    "AstEquivalent"      -> "ast/AstEquivalent.scala",
    "PropMangler"        -> "scope/PropMangler.scala",
    "Mangler"            -> "scope/Mangler.scala",
    // Additional non-compress modules
    "FirstInStatement"   -> "output/FirstInStatement.scala",
    "JsNumber"           -> "output/JsNumber.scala",
    "OutputOptions"      -> "output/OutputOptions.scala",
    "SymbolDef"          -> "scope/SymbolDef.scala",
    "DomProps"           -> "scope/DomProps.scala",
    "Parser"             -> "parse/Parser.scala",
    "Tokenizer"          -> "parse/Tokenizer.scala",
    "Token"              -> "parse/Token.scala",
    "Precedence"         -> "parse/Precedence.scala",
  )

  // -------------------------------------------------------------------------
  // Building the oracle from reference files
  // -------------------------------------------------------------------------

  /** Build a type oracle from all reference compress module files.
    *
    * @param referenceRoot path to the compress module directory, e.g.
    *   `/path/to/ssg/ssg-js/src/main/scala/ssg/js/compress/`
    */
  def buildFromDirectory(referenceRoot: java.nio.file.Path): TypeOracle =
    val allMethods = mutable.Map.empty[(String, String), MethodSig]

    for (objName, fileName) <- referenceObjectToFile do
      val filePath = referenceRoot.resolve(fileName)
      // Also try resolving relative to parent (for non-compress modules in sibling dirs)
      val altPath = referenceRoot.getParent.resolve(fileName)
      val resolved = if java.nio.file.Files.exists(filePath) then Some(filePath)
        else if java.nio.file.Files.exists(altPath) then Some(altPath)
        else None
      resolved.foreach { fp =>
        val source = new String(java.nio.file.Files.readAllBytes(fp))
        val sigs = parseFile(objName, source)
        for (_, sig) <- sigs do
          allMethods((objName, sig.methodName)) = sig
      }

    TypeOracle(allMethods.toMap)

  /** Build a type oracle from classpath resources (for testing).
    *
    * This uses a hardcoded mapping that covers the most important methods
    * across all compress modules. Built by reading the reference files.
    */
  def buildHardcoded(): TypeOracle =
    val methods = mutable.Map.empty[(String, String), MethodSig]

    // --- Inference ---
    def inf(name: String, params: List[ParamSig], ret: String): Unit =
      methods(("Inference", name)) = MethodSig(name, params, ret)

    inf("isUndeclaredRef", List(ParamSig("node", "AstNode")), "Boolean")
    inf("isRefDeclared", List(ParamSig("ref", "AstSymbolRef"), ParamSig("compressor", "CompressorLike")), "Boolean")
    inf("isRefImmutable", List(ParamSig("ref", "AstSymbolRef")), "Boolean")
    inf("isBoolean", List(ParamSig("node", "AstNode")), "Boolean")
    inf("isNumber", List(ParamSig("node", "AstNode"), ParamSig("compressor", "CompressorLike")), "Boolean")
    inf("isBigInt", List(ParamSig("node", "AstNode"), ParamSig("compressor", "CompressorLike")), "Boolean")
    inf("isNumberOrBigInt", List(ParamSig("node", "AstNode"), ParamSig("compressor", "CompressorLike")), "Boolean")
    inf("is32BitInteger", List(ParamSig("node", "AstNode"), ParamSig("compressor", "CompressorLike")), "Boolean")
    inf("isString", List(ParamSig("node", "AstNode"), ParamSig("compressor", "CompressorLike")), "Boolean")
    inf("isUndefined", List(ParamSig("node", "AstNode"), ParamSig("compressor", "CompressorLike")), "Boolean")
    inf("isNullish", List(ParamSig("node", "AstNode"), ParamSig("compressor", "CompressorLike")), "Boolean")
    inf("isNullishShortcircuited", List(ParamSig("node", "AstNode"), ParamSig("compressor", "CompressorLike")), "Boolean")
    inf("hasSideEffects", List(ParamSig("node", "AstNode"), ParamSig("compressor", "CompressorLike")), "Boolean")
    inf("mayThrow", List(ParamSig("node", "AstNode"), ParamSig("compressor", "CompressorLike")), "Boolean")
    inf("mayThrowOnAccess", List(ParamSig("node", "AstNode"), ParamSig("compressor", "CompressorLike")), "Boolean")
    inf("dotThrow", List(ParamSig("node", "AstNode"), ParamSig("compressor", "CompressorLike")), "Boolean")
    inf("isLhs", List(ParamSig("node", "AstNode"), ParamSig("parent", "AstNode")), "AstNode | Null")
    inf("isModified", List(ParamSig("compressor", "CompressorLike"), ParamSig("tw", "TreeWalker"), ParamSig("node", "AstNode"), ParamSig("value", "AstNode"), ParamSig("level", "Int"), ParamSig("immutable", "Boolean")), "Boolean")
    inf("isUsedInExpression", List(ParamSig("tw", "TreeWalker")), "Boolean")
    inf("aborts", List(ParamSig("thing", "AstNode | Null")), "AstNode | Null")
    inf("isCalleePure", List(ParamSig("call", "AstCall"), ParamSig("compressor", "CompressorLike")), "Boolean")
    inf("isCallPure", List(ParamSig("dot", "AstDot"), ParamSig("compressor", "CompressorLike")), "Boolean")
    inf("bitwiseNegate", List(ParamSig("node", "AstNode"), ParamSig("compressor", "CompressorLike"), ParamSig("in32BitContext", "Boolean")), "AstNode")
    inf("isConstantExpression", List(ParamSig("node", "AstNode"), ParamSig("scope", "AstScope | Null")), "Any")
    inf("containsThis", List(ParamSig("node", "AstNode")), "Boolean")
    inf("isSelfReferential", List(ParamSig("cls", "AstClass")), "Boolean")
    inf("visitNondeferredClassParts", List(ParamSig("cls", "AstClass"), ParamSig("visitor", "(AstNode, () => Unit) => Boolean")), "Unit")
    inf("negate", List(ParamSig("node", "AstNode"), ParamSig("compressor", "CompressorLike"), ParamSig("firstInStatement", "Boolean")), "AstNode")

    // --- Common ---
    def com(name: String, params: List[ParamSig], ret: String): Unit =
      methods(("Common", name)) = MethodSig(name, params, ret)

    com("mergeSequence", List(ParamSig("array", "ArrayBuffer[AstNode]"), ParamSig("node", "AstNode")), "ArrayBuffer[AstNode]")
    com("makeSequence", List(ParamSig("orig", "AstNode"), ParamSig("expressions", "ArrayBuffer[AstNode]")), "AstNode")
    com("makeEmptyFunction", List(ParamSig("self", "AstNode")), "AstFunction")
    com("makeNodeFromConstant", List(ParamSig("value", "Any"), ParamSig("orig", "AstNode")), "AstNode")
    com("bestOfExpression", List(ParamSig("ast1", "AstNode"), ParamSig("ast2", "AstNode")), "AstNode")
    com("bestOfStatement", List(ParamSig("ast1", "AstNode"), ParamSig("ast2", "AstNode")), "AstNode")
    com("bestOf", List(ParamSig("compressor", "CompressorLike"), ParamSig("ast1", "AstNode"), ParamSig("ast2", "AstNode")), "AstNode")
    com("firstInStatement", List(ParamSig("stack", "{ def parent(n: Int): AstNode | Null }")), "Boolean")
    com("getSimpleKey", List(ParamSig("key", "AstNode")), "AstNode | String | Double | Null")
    com("readProperty", List(ParamSig("obj", "AstNode"), ParamSig("key", "AstNode")), "AstNode | Null")
    com("hasBreakOrContinue", List(ParamSig("loop", "AstNode & AstIterationStatement"), ParamSig("parent", "AstNode | Null")), "Boolean")
    com("requiresSequenceToMaintainBinding", List(ParamSig("parent", "AstNode"), ParamSig("orig", "AstNode"), ParamSig("value", "AstNode")), "Boolean")
    com("maintainThisBinding", List(ParamSig("parent", "AstNode"), ParamSig("orig", "AstNode"), ParamSig("value", "AstNode")), "AstNode")
    com("isFuncExpr", List(ParamSig("node", "AstNode")), "Boolean")
    com("isIifeCall", List(ParamSig("node", "AstNode")), "Boolean")
    com("isEmpty", List(ParamSig("thing", "AstNode | Null")), "Boolean")
    com("isIdentifierAtom", List(ParamSig("node", "AstNode")), "Boolean")
    com("canBeEvictedFromBlock", List(ParamSig("node", "AstNode")), "Boolean")
    com("asStatementArray", List(ParamSig("thing", "AstNode | Null")), "ArrayBuffer[AstNode]")
    com("isReachable", List(ParamSig("scopeNode", "AstNode"), ParamSig("defs", "ArrayBuffer[Any]")), "Boolean")
    com("isRecursiveRef", List(ParamSig("tw", "TreeWalker"), ParamSig("theDef", "Any")), "Boolean")
    com("retainTopFunc", List(ParamSig("fn", "AstNode"), ParamSig("hasTopFlag", "Boolean"), ParamSig("topRetainCheck", "Any => Boolean")), "Boolean")
    com("walkParent", List(ParamSig("node", "AstNode"), ParamSig("cb", "(AstNode, WalkParentInfo) => Any")), "Boolean")

    // --- Evaluate ---
    def eval(name: String, params: List[ParamSig], ret: String): Unit =
      methods(("Evaluate", name)) = MethodSig(name, params, ret)

    eval("evaluate", List(ParamSig("node", "AstNode"), ParamSig("compressor", "CompressorLike")), "Any")
    eval("isConstant", List(ParamSig("node", "AstNode")), "Boolean")
    eval("isConstantExpression", List(ParamSig("node", "AstNode")), "Boolean")

    // --- GlobalDefs ---
    def gd(name: String, params: List[ParamSig], ret: String): Unit =
      methods(("GlobalDefs", name)) = MethodSig(name, params, ret)

    gd("toNode", List(ParamSig("value", "Any"), ParamSig("orig", "AstNode")), "AstNode")
    gd("findDefs", List(ParamSig("node", "AstNode"), ParamSig("globalDefs", "Map[String, Any]")), "AstNode | Null")
    gd("resolveDefs", List(ParamSig("toplevel", "AstToplevel"), ParamSig("globalDefs", "Map[String, Any]")), "AstToplevel")

    // --- DropSideEffectFree ---
    def dsf(name: String, params: List[ParamSig], ret: String): Unit =
      methods(("DropSideEffectFree", name)) = MethodSig(name, params, ret)

    dsf("dropSideEffectFree", List(ParamSig("node", "AstNode"), ParamSig("compressor", "CompressorLike"), ParamSig("firstInStatement", "Boolean")), "AstNode | Null")
    dsf("trim", List(ParamSig("nodes", "ArrayBuffer[AstNode]"), ParamSig("compressor", "CompressorLike"), ParamSig("firstInStatement", "Boolean")), "ArrayBuffer[AstNode]")

    // --- DropUnused ---
    def du(name: String, params: List[ParamSig], ret: String): Unit =
      methods(("DropUnused", name)) = MethodSig(name, params, ret)

    du("dropUnused", List(ParamSig("self", "AstScope"), ParamSig("compressor", "CompressorLike")), "Unit")
    du("assignAsUnused", List(ParamSig("node", "AstNode"), ParamSig("keepAssign", "Boolean")), "AstNode | Null")

    // --- ReduceVars ---
    def rv(name: String, params: List[ParamSig], ret: String): Unit =
      methods(("ReduceVars", name)) = MethodSig(name, params, ret)

    rv("reduceVars", List(ParamSig("scope", "AstNode"), ParamSig("compressor", "CompressorLike")), "Unit")
    rv("resetDef", List(ParamSig("compressor", "CompressorLike"), ParamSig("d", "SymbolDef")), "Unit")
    rv("resetVariables", List(ParamSig("state", "ReduceVarsState"), ParamSig("compressor", "CompressorLike"), ParamSig("scope", "AstScope")), "Unit")
    rv("resetBlockVariables", List(ParamSig("compressor", "CompressorLike"), ParamSig("node", "AstNode")), "Unit")
    rv("isImmutable", List(ParamSig("value", "AstNode | Null")), "Boolean")

    // --- TightenBody ---
    def tb(name: String, params: List[ParamSig], ret: String): Unit =
      methods(("TightenBody", name)) = MethodSig(name, params, ret)

    tb("tightenBody", List(ParamSig("statements", "ArrayBuffer[AstNode]"), ParamSig("compressor", "CompressorLike")), "Unit")
    tb("extractFromUnreachableCode", List(ParamSig("compressor", "CompressorLike"), ParamSig("stat", "AstNode"), ParamSig("target", "ArrayBuffer[AstNode]")), "Unit")
    tb("loopBody", List(ParamSig("x", "AstNode | Null")), "AstNode | Null")
    tb("isLhsReadOnly", List(ParamSig("lhs", "AstNode")), "Boolean")
    tb("removeInitializers", List(ParamSig("varStatement", "AstVar")), "AstNode | Null")

    // --- Inline ---
    def inl(name: String, params: List[ParamSig], ret: String): Unit =
      methods(("Inline", name)) = MethodSig(name, params, ret)

    inl("inlineIntoSymbolRef", List(ParamSig("self", "AstSymbolRef"), ParamSig("compressor", "CompressorLike")), "AstNode")
    inl("inlineIntoCall", List(ParamSig("self", "AstCall"), ParamSig("compressor", "CompressorLike")), "AstNode")
    inl("dontInlineLambdaInLoop", List(ParamSig("compressor", "CompressorLike"), ParamSig("maybeLambda", "AstNode")), "Boolean")
    inl("withinArrayOrObjectLiteral", List(ParamSig("compressor", "CompressorLike"), ParamSig("self", "AstNode")), "Boolean")
    inl("scopeEnclosesVariablesInThisScope", List(ParamSig("scope", "AstScope"), ParamSig("pulledScope", "AstScope")), "Boolean")
    inl("isConstSymbolShorterThanInitValue", List(ParamSig("dd", "SymbolDef"), ParamSig("fixedValue", "AstNode | Null")), "Boolean")

    // --- Compressor (CompressIndex) ---
    def ci(name: String, params: List[ParamSig], ret: String): Unit =
      methods(("Compressor", name)) = MethodSig(name, params, ret)

    ci("containsOptional", List(ParamSig("node", "AstNode")), "Boolean")
    ci("dropConsole", List(ParamSig("node", "AstToplevel"), ParamSig("options", "CompressorOptions")), "AstToplevel")
    ci("equivalentTo", List(ParamSig("node", "AstNode"), ParamSig("other", "AstNode")), "Boolean")
    ci("fixedValue", List(ParamSig("node", "AstSymbol")), "AstNode | Null")
    ci("flattenObject", List(ParamSig("node", "AstPropAccess"), ParamSig("key", "String"), ParamSig("compressor", "CompressorLike")), "AstNode | Null")
    ci("hoistDeclarations", List(ParamSig("node", "AstScope"), ParamSig("compressor", "CompressorLike")), "AstScope")
    ci("hoistProperties", List(ParamSig("node", "AstScope"), ParamSig("compressor", "CompressorLike")), "AstScope")
    ci("isDeclared", List(ParamSig("node", "AstSymbolRef"), ParamSig("compressor", "CompressorLike")), "Boolean")
    ci("isImmutable", List(ParamSig("node", "AstSymbolRef")), "Boolean")
    ci("liftSequences", List(ParamSig("node", "AstNode"), ParamSig("compressor", "CompressorLike")), "AstNode")
    ci("processExpression", List(ParamSig("node", "AstScope"), ParamSig("insert", "Boolean"), ParamSig("compressor", "CompressorLike")), "Unit")
    ci("resetOptFlags", List(ParamSig("node", "AstToplevel"), ParamSig("compressor", "CompressorLike")), "Unit")
    ci("toAssignments", List(ParamSig("node", "AstDefinitions"), ParamSig("compressor", "CompressorLike")), "AstNode | Null")
    ci("findVariable", List(ParamSig("compressor", "CompressorLike"), ParamSig("name", "String")), "SymbolDef | Null")
    ci("isObject", List(ParamSig("node", "AstNode")), "Boolean")
    ci("isAtomic", List(ParamSig("lhs", "AstNode"), ParamSig("self", "AstNode")), "Boolean")
    ci("unsafeUndefinedRef", List(ParamSig("self", "AstNode"), ParamSig("compressor", "CompressorLike")), "AstNode | Null")
    ci("isNullishCheck", List(ParamSig("check", "AstNode"), ParamSig("checkSubject", "AstNode"), ParamSig("compressor", "CompressorLike")), "Boolean")
    ci("safeToFlatten", List(ParamSig("value", "AstNode | Null"), ParamSig("compressor", "CompressorLike")), "Boolean")
    ci("literalsInBooleanContext", List(ParamSig("self", "AstNode"), ParamSig("compressor", "CompressorLike")), "AstNode")
    ci("inlineArrayLikeSpread", List(ParamSig("elements", "ArrayBuffer[AstNode]")), "Unit")
    ci("liftKey", List(ParamSig("self", "AstObjectProperty"), ParamSig("compressor", "CompressorLike")), "AstNode")

    // --- Hoisting (used by CompressIndex) ---
    def ho(name: String, params: List[ParamSig], ret: String): Unit =
      methods(("Hoisting", name)) = MethodSig(name, params, ret)

    ho("toAssignments", List(ParamSig("defs", "AstDefinitions"), ParamSig("compressor", "CompressorLike")), "AstNode | Null")
    ho("argsAsNames", List(ParamSig("lambda", "AstLambda")), "ArrayBuffer[AstSymbol]")
    ho("hoistDeclarations", List(ParamSig("self", "AstScope"), ParamSig("compressor", "CompressorLike")), "AstScope")
    ho("hoistProperties", List(ParamSig("self", "AstScope"), ParamSig("compressor", "CompressorLike")), "AstScope")

    // --- ScopeAnalysis ---
    def sa(name: String, params: List[ParamSig], ret: String): Unit =
      methods(("ScopeAnalysis", name)) = MethodSig(name, params, ret)

    sa("figureOutScope", List(ParamSig("node", "AstToplevel"), ParamSig("options", "ScopeOptions")), "Unit")
    sa("nextMangledName", List(ParamSig("scope", "AstScope"), ParamSig("options", "MangleOptions")), "String")

    // --- OutputStream ---
    def os(name: String, params: List[ParamSig], ret: String): Unit =
      methods(("OutputStream", name)) = MethodSig(name, params, ret)

    os("print", List(ParamSig("node", "AstNode")), "Unit")
    os("printAtom", List(ParamSig("str", "String")), "Unit")

    // --- AstSize ---
    def az(name: String, params: List[ParamSig], ret: String): Unit =
      methods(("AstSize", name)) = MethodSig(name, params, ret)

    az("size", List(ParamSig("node", "AstNode")), "Int")

    // --- AstEquivalent ---
    def ae(name: String, params: List[ParamSig], ret: String): Unit =
      methods(("AstEquivalent", name)) = MethodSig(name, params, ret)

    ae("equivalentTo", List(ParamSig("node", "AstNode"), ParamSig("other", "AstNode")), "Boolean")

    TypeOracle(methods.toMap)

  /** Build an oracle and remap keys from reference object names to emitter
    * object names, for direct lookup by the emitter. */
  def buildForEmitter(referenceRoot: java.nio.file.Path): TypeOracle =
    val base = buildFromDirectory(referenceRoot)
    remapForEmitter(base)

  /** Build the hardcoded oracle remapped to emitter object names. */
  def buildHardcodedForEmitter(): TypeOracle =
    remapForEmitter(buildHardcoded())

  /** Remap oracle keys from reference object names to emitter object names. */
  private def remapForEmitter(oracle: TypeOracle): TypeOracle =
    val reverseMap = emitterToReferenceObject.map { case (k, v) => (v, k) }
    val remapped = mutable.Map.empty[(String, String), MethodSig]

    for ((key, sig) <- oracle.methods) do
      // Keep the original key
      remapped(key) = sig
      // Also add a key with the emitter's object name
      reverseMap.get(key._1).foreach { emitterName =>
        remapped((emitterName, key._2)) = sig
      }

    TypeOracle(remapped.toMap)

  /** Convert a snake_case JS method name to camelCase for lookup.
    * Must match the emitter's snakeToCamel. */
  def snakeToCamel(s: String): String =
    val parts = s.split("_")
    if parts.length <= 1 then s
    else parts.head + parts.tail.map(_.capitalize).mkString
