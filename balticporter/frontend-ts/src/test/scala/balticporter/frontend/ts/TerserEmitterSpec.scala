package balticporter.frontend.ts

class TerserEmitterSpec extends munit.FunSuite:

  private def loadRast(resource: String): RastFile =
    val stream = getClass.getResourceAsStream(resource)
    assert(stream != null, s"Resource not found: $resource")
    val json = new String(stream.readAllBytes(), java.nio.charset.StandardCharsets.UTF_8)
    stream.close()
    Rast.readFile(json)

  // -----------------------------------------------------------------------
  // DEFNODE hierarchy extraction
  // -----------------------------------------------------------------------

  test("ast.js: extract 133 DEFNODE classes"):
    val rast = loadRast("/rast/terser/lib/ast.rast.json")
    val classes = balticporter.corpus.terser.TerserEmitter.extractHierarchy(rast)
    // Terser has 133-134 DEFNODE calls (AST_Node is also a DEFNODE)
    assert(classes.size >= 130, s"Expected >= 130 DEFNODE classes, got ${classes.size}")
    assert(classes.size <= 140, s"Expected <= 140 DEFNODE classes, got ${classes.size}")

  test("ast.js: AST_Node is root with start/end props"):
    val rast = loadRast("/rast/terser/lib/ast.rast.json")
    val classes = balticporter.corpus.terser.TerserEmitter.extractHierarchy(rast)
    val astNode = classes.find(_.varName == "AST_Node")
    assert(astNode.isDefined, "AST_Node should be in the hierarchy")
    assert(astNode.get.selfProps.contains("start"), "AST_Node should have 'start' prop")
    assert(astNode.get.selfProps.contains("end"), "AST_Node should have 'end' prop")

  test("ast.js: AST_Statement extends AST_Node"):
    val rast = loadRast("/rast/terser/lib/ast.rast.json")
    val classes = balticporter.corpus.terser.TerserEmitter.extractHierarchy(rast)
    val stmt = classes.find(_.varName == "AST_Statement")
    assert(stmt.isDefined, "AST_Statement should exist")
    assert(stmt.get.base.contains("AST_Node"), s"AST_Statement base should be AST_Node, got ${stmt.get.base}")

  test("ast.js: AST_For has init/condition/step props"):
    val rast = loadRast("/rast/terser/lib/ast.rast.json")
    val classes = balticporter.corpus.terser.TerserEmitter.extractHierarchy(rast)
    val forNode = classes.find(_.varName == "AST_For")
    assert(forNode.isDefined, "AST_For should exist")
    assert(forNode.get.selfProps == List("init", "condition", "step"),
      s"AST_For props should be [init, condition, step], got ${forNode.get.selfProps}")

  test("ast.js: abstract vs concrete classification"):
    val rast = loadRast("/rast/terser/lib/ast.rast.json")
    val classes = balticporter.corpus.terser.TerserEmitter.extractHierarchy(rast)
    val byName = classes.map(c => c.varName -> c).toMap
    // AST_Statement should be abstract (has subclasses)
    assert(byName("AST_Statement").isAbstract, "AST_Statement should be abstract")
    // AST_Debugger should be concrete (leaf)
    assert(!byName("AST_Debugger").isAbstract, "AST_Debugger should be concrete")
    // AST_Node should be abstract
    assert(byName("AST_Node").isAbstract, "AST_Node should be abstract")

  test("ast.js: hierarchy summary is readable"):
    val rast = loadRast("/rast/terser/lib/ast.rast.json")
    val classes = balticporter.corpus.terser.TerserEmitter.extractHierarchy(rast)
    val summary = balticporter.corpus.terser.TerserEmitter.hierarchySummary(classes)
    println("=== DEFNODE Hierarchy ===")
    println(summary)
    assert(summary.contains("AST_Node"), "Summary should contain AST_Node")
    assert(summary.contains("AST_Statement"), "Summary should contain AST_Statement")
    assert(summary.contains("AST_For"), "Summary should contain AST_For")
    assert(summary.contains("trait"), "Summary should classify abstract classes as traits")
    assert(summary.contains("class"), "Summary should classify concrete classes as classes")

  test("ast.js: methods are extracted"):
    val rast = loadRast("/rast/terser/lib/ast.rast.json")
    val classes = balticporter.corpus.terser.TerserEmitter.extractHierarchy(rast)
    val astNode = classes.find(_.varName == "AST_Node").get
    // AST_Node should have methods like _clone, clone, $documentation
    assert(astNode.methods.nonEmpty, s"AST_Node should have methods, got ${astNode.methods}")
    assert(astNode.methods.contains("_clone") || astNode.methods.contains("clone"),
      s"AST_Node should have clone method, got ${astNode.methods}")

  // -----------------------------------------------------------------------
  // compressor-flags.js
  // -----------------------------------------------------------------------

  test("compressor-flags.js: emit CompressorFlags object"):
    val rast = loadRast("/rast/terser/lib/compress/compressor-flags.rast.json")
    val scala = balticporter.corpus.terser.TerserEmitter.emitCompressorFlags(rast)
    println("=== CompressorFlags.scala (emitted) ===")
    println(scala)
    assert(scala.contains("object CompressorFlags"), "should emit CompressorFlags object")
    assert(scala.contains("val UNUSED"), "should have UNUSED constant")
    assert(scala.contains("val TRUTHY"), "should have TRUTHY constant")
    assert(scala.contains("val FALSY"), "should have FALSY constant")
    assert(scala.contains("val SQUEEZED"), "should have SQUEEZED constant")
    assert(scala.contains("val CLEAR_BETWEEN_PASSES"), "should have CLEAR_BETWEEN_PASSES")
    assert(scala.contains("def hasFlag"), "should have hasFlag method")
    assert(scala.contains("def setFlag"), "should have setFlag method")
    assert(scala.contains("def clearFlag"), "should have clearFlag method")
    assert(scala.contains("AstNode"), "should reference AstNode")

  // -----------------------------------------------------------------------
  // utils/first_in_statement.js
  // -----------------------------------------------------------------------

  test("first_in_statement.js: emit FirstInStatement object"):
    val rast = loadRast("/rast/terser/lib/utils/first_in_statement.rast.json")
    val scala = balticporter.corpus.terser.TerserEmitter.emitFirstInStatement(rast)
    println("=== FirstInStatement.scala (emitted) ===")
    println(scala)
    assert(scala.contains("object FirstInStatement"), "should emit FirstInStatement object")
    assert(scala.contains("def firstInStatement"), "should have firstInStatement method")
    assert(scala.contains("def leftIsObject"), "should have leftIsObject method")
    assert(scala.contains("AstBinary"), "should reference AstBinary")
    assert(scala.contains("AstConditional"), "should reference AstConditional")
    assert(scala.contains("AstSequence"), "should reference AstSequence")
    assert(scala.contains("boundary"), "should use boundary for control flow")

  // -----------------------------------------------------------------------
  // DEFMETHOD extraction (scope.js)
  // -----------------------------------------------------------------------

  test("scope.js: extract DEFMETHOD entries"):
    val rast = loadRast("/rast/terser/lib/scope.rast.json")
    val entries = balticporter.corpus.terser.TerserEmitter.extractDefmethods(rast)
    assert(entries.nonEmpty, "should find DEFMETHOD entries")
    // scope.js has 36 DEFMETHOD calls (including return_false/return_true/return_this references)
    assert(entries.size >= 35, s"Expected >= 35 DEFMETHOD entries, got ${entries.size}")

  test("scope.js: first DEFMETHOD is figure_out_scope on AST_Scope"):
    val rast = loadRast("/rast/terser/lib/scope.rast.json")
    val entries = balticporter.corpus.terser.TerserEmitter.extractDefmethods(rast)
    val first = entries.head
    assert(first.className == "AST_Scope", s"Expected AST_Scope, got ${first.className}")
    assert(first.methodName == "figure_out_scope", s"Expected figure_out_scope, got ${first.methodName}")
    assert(first.params.nonEmpty, "figure_out_scope should have parameters")

  test("scope.js: DEFMETHOD entries include def_global on AST_Toplevel"):
    val rast = loadRast("/rast/terser/lib/scope.rast.json")
    val entries = balticporter.corpus.terser.TerserEmitter.extractDefmethods(rast)
    val defGlobal = entries.find(e => e.methodName == "def_global" && e.className == "AST_Toplevel")
    assert(defGlobal.isDefined, "should find def_global on AST_Toplevel")

  test("scope.js: DEFMETHOD entries include is_block_scope on multiple classes"):
    val rast = loadRast("/rast/terser/lib/scope.rast.json")
    val entries = balticporter.corpus.terser.TerserEmitter.extractDefmethods(rast)
    val blockScopes = entries.filter(_.methodName == "is_block_scope")
    assert(blockScopes.size >= 8, s"Expected >= 8 is_block_scope entries, got ${blockScopes.size}")
    val classNames = blockScopes.map(_.className).toSet
    assert(classNames.contains("AST_Node"), "AST_Node should have is_block_scope")
    assert(classNames.contains("AST_Block"), "AST_Block should have is_block_scope")
    assert(classNames.contains("AST_Scope"), "AST_Scope should have is_block_scope")

  test("scope.js: groupByClass groups correctly"):
    val rast = loadRast("/rast/terser/lib/scope.rast.json")
    val entries = balticporter.corpus.terser.TerserEmitter.extractDefmethods(rast)
    val grouped = balticporter.corpus.terser.TerserEmitter.groupByClass(entries)
    assert(grouped.contains("AST_Scope"), "should have AST_Scope group")
    assert(grouped.contains("AST_Toplevel"), "should have AST_Toplevel group")
    assert(grouped.contains("AST_Symbol"), "should have AST_Symbol group")
    // AST_Scope should have the most methods
    val scopeMethods = grouped("AST_Scope")
    assert(scopeMethods.size >= 8, s"AST_Scope should have >= 8 methods, got ${scopeMethods.size}")

  test("scope.js: defmethodSummary is readable"):
    val rast = loadRast("/rast/terser/lib/scope.rast.json")
    val entries = balticporter.corpus.terser.TerserEmitter.extractDefmethods(rast)
    val summary = balticporter.corpus.terser.TerserEmitter.defmethodSummary(entries)
    println("=== DEFMETHOD Summary ===")
    println(summary)
    assert(summary.contains("DEFMETHOD summary"), "should have summary header")
    assert(summary.contains("AST_Scope"), "should mention AST_Scope")
    assert(summary.contains("figure_out_scope"), "should mention figure_out_scope")

  test("scope.js: merge DEFMETHOD entries into hierarchy"):
    val astRast = loadRast("/rast/terser/lib/ast.rast.json")
    val scopeRast = loadRast("/rast/terser/lib/scope.rast.json")
    val hierarchy = balticporter.corpus.terser.TerserEmitter.extractHierarchy(astRast)
    val defmethods = balticporter.corpus.terser.TerserEmitter.extractDefmethods(scopeRast)
    val merged = balticporter.corpus.terser.TerserEmitter.mergeDefmethods(hierarchy, defmethods)
    // Find AST_Scope and verify it has merged methods
    val scopeEntry = merged.find(_._1.varName == "AST_Scope")
    assert(scopeEntry.isDefined, "AST_Scope should be in merged result")
    val (cls, methods) = scopeEntry.get
    assert(methods.nonEmpty, "AST_Scope should have DEFMETHOD entries after merge")
    val methodNames = methods.map(_.methodName)
    assert(methodNames.contains("figure_out_scope"), "should include figure_out_scope")
    assert(methodNames.contains("init_scope_vars"), "should include init_scope_vars")
    assert(methodNames.contains("find_variable"), "should include find_variable")

  test("scope.js: emit merged AstScope class"):
    val astRast = loadRast("/rast/terser/lib/ast.rast.json")
    val scopeRast = loadRast("/rast/terser/lib/scope.rast.json")
    val hierarchy = balticporter.corpus.terser.TerserEmitter.extractHierarchy(astRast)
    val defmethods = balticporter.corpus.terser.TerserEmitter.extractDefmethods(scopeRast)
    val merged = balticporter.corpus.terser.TerserEmitter.mergeDefmethods(hierarchy, defmethods)
    val scopeEntry = merged.find(_._1.varName == "AST_Scope").get
    val scala = balticporter.corpus.terser.TerserEmitter.emitMergedClass(scopeEntry._1, scopeEntry._2)
    println("=== AstScope (merged) ===")
    println(scala)
    assert(scala.contains("AstScope"), "should emit AstScope")
    assert(scala.contains("DEFMETHOD additions"), "should have DEFMETHOD section")
    assert(scala.contains("figureOutScope"), "should have figureOutScope method (camelCase)")
    assert(scala.contains("initScopeVars"), "should have initScopeVars method (camelCase)")

  test("scope.js: extract free functions"):
    val rast = loadRast("/rast/terser/lib/scope.rast.json")
    val funcs = balticporter.corpus.terser.TerserEmitter.extractFreeFunctions(rast)
    assert(funcs.nonEmpty, "should find free functions")
    val names = funcs.map(_.name)
    assert(names.contains("redefined_catch_def"), "should find redefined_catch_def")

  test("scope.js: extract class declarations"):
    val rast = loadRast("/rast/terser/lib/scope.rast.json")
    val classes = balticporter.corpus.terser.TerserEmitter.extractClassDeclarations(rast)
    assert(classes.contains("SymbolDef"), "should find SymbolDef class")

  // -----------------------------------------------------------------------
  // SymbolDef class emission
  // -----------------------------------------------------------------------

  test("scope.js: emit SymbolDef class"):
    val rast = loadRast("/rast/terser/lib/scope.rast.json")
    val scala = balticporter.corpus.terser.TerserEmitter.emitSymbolDef(rast)
    println("=== SymbolDef.scala (emitted) ===")
    println(scala)
    assert(scala.contains("class SymbolDef"), "should emit SymbolDef class")
    assert(scala.contains("scopeArg: AstScope"), "should have scope constructor param")
    assert(scala.contains("origArg: AstSymbol") || scala.contains("orig"), "should have orig constructor param")
    // Fields
    assert(scala.contains("var name: String"), "should have name field")
    assert(scala.contains("var scope: AstScope"), "should have scope field")
    assert(scala.contains("var global: Boolean"), "should have global field")
    assert(scala.contains("var mangledName: String | Null"), "should have mangledName field")
    assert(scala.contains("var undeclared: Boolean"), "should have undeclared field")
    assert(scala.contains("var id: Int"), "should have id field")
    assert(scala.contains("var references: ArrayBuffer"), "should have references field")
    assert(scala.contains("var chained: Boolean"), "should have chained field")
    assert(scala.contains("var directAccess: Boolean"), "should have directAccess field")
    assert(scala.contains("var escaped: Int"), "should have escaped field")
    assert(scala.contains("var recursiveRefs: Int"), "should have recursiveRefs field")
    assert(scala.contains("var assignments: Int"), "should have assignments field")
    assert(scala.contains("var replaced: Int"), "should have replaced field")
    assert(scala.contains("var singleUse: Any"), "should have singleUse field")
    assert(scala.contains("var fixed: Any"), "should have fixed field")
    assert(scala.contains("var eliminated: Int"), "should have eliminated field")
    // Methods
    assert(scala.contains("def fixedValue"), "should have fixedValue method")
    assert(scala.contains("def unmangleable"), "should have unmangleable method")
    assert(scala.contains("def mangle"), "should have mangle method")
    // Companion object
    assert(scala.contains("object SymbolDef"), "should have companion object")
    assert(scala.contains("var nextId: Int"), "should have nextId counter")
    assert(scala.contains("def resetIds()"), "should have resetIds method")
    assert(scala.contains("def redefinedCatchDef"), "should have redefinedCatchDef method")
    // Export keyword -> exportFlag (Scala keyword avoidance)
    assert(scala.contains("exportFlag"), "should rename export to exportFlag")
    assert(!scala.contains("var export:"), "should NOT have bare 'export' as field name")

  test("scope.js: emitted SymbolDef matches hand-port structure"):
    val rast = loadRast("/rast/terser/lib/scope.rast.json")
    val scala = balticporter.corpus.terser.TerserEmitter.emitSymbolDef(rast)
    // Verify key patterns from hand port
    assert(scala.contains("ArrayBuffer(origArg)"), "orig should be initialized with constructor param")
    assert(scala.contains("origArg.name"), "name should come from orig symbol")
    assert(scala.contains("SymbolDef.nextId"), "id should use companion nextId counter")
    assert(scala.contains("AstSymbolCatch"), "redefinedCatchDef should check AstSymbolCatch")
    assert(scala.contains("ManglerOptions"), "unmangleable should take ManglerOptions")
    assert(scala.contains("keepName"), "should have keepName helper")

  test("scope.js: write emitted SymbolDef to target"):
    val rast = loadRast("/rast/terser/lib/scope.rast.json")
    val scala = balticporter.corpus.terser.TerserEmitter.emitSymbolDef(rast)
    val outDir = java.nio.file.Path.of(sys.props.getOrElse("user.dir", ".")).resolve("target/emitted-terser")
    java.nio.file.Files.createDirectories(outDir)
    val path = outDir.resolve("SymbolDef.scala")
    java.nio.file.Files.writeString(path, scala)
    println(s"[emit] SymbolDef.scala: ${scala.linesIterator.size} lines -> $path")

  // -----------------------------------------------------------------------
  // NativeObjects emission (compress/native-objects.js)
  // -----------------------------------------------------------------------

  test("native-objects.js: emit NativeObjects object"):
    val rast = loadRast("/rast/terser/lib/compress/native-objects.rast.json")
    val scala = balticporter.corpus.terser.TerserEmitter.emitNativeObjects(rast)
    println("=== NativeObjects.scala (emitted) ===")
    println(scala)
    assert(scala.contains("object NativeObjects"), "should emit NativeObjects object")
    // purePropAccessGlobals
    assert(scala.contains("purePropAccessGlobals"), "should have purePropAccessGlobals")
    assert(scala.contains("\"Number\""), "should contain Number")
    assert(scala.contains("\"Array\""), "should contain Array")
    assert(scala.contains("\"Promise\""), "should contain Promise")
    // pureNativeMethods
    assert(scala.contains("pureNativeMethods"), "should have pureNativeMethods")
    assert(scala.contains("\"charAt\""), "should contain charAt method")
    assert(scala.contains("\"indexOf\""), "should contain indexOf method")
    assert(scala.contains("objectMethods"), "should use objectMethods set")
    // pureNativeFns
    assert(scala.contains("pureNativeFns"), "should have pureNativeFns")
    assert(scala.contains("\"isArray\""), "should contain isArray")
    assert(scala.contains("\"abs\""), "should contain abs (Math)")
    assert(scala.contains("\"keys\""), "should contain keys (Object)")
    // pureNativeValues
    assert(scala.contains("pureNativeValues"), "should have pureNativeValues")
    assert(scala.contains("\"PI\""), "should contain PI")
    assert(scala.contains("\"MAX_VALUE\""), "should contain MAX_VALUE")
    // Lookup helpers
    assert(scala.contains("def isPureNativeMethod"), "should have isPureNativeMethod")
    assert(scala.contains("def isPureNativeFn"), "should have isPureNativeFn")
    assert(scala.contains("def isPureNativeValue"), "should have isPureNativeValue")

  test("native-objects.js: emitted NativeObjects matches hand-port API"):
    val rast = loadRast("/rast/terser/lib/compress/native-objects.rast.json")
    val scala = balticporter.corpus.terser.TerserEmitter.emitNativeObjects(rast)
    // Structure
    assert(scala.contains("Map[String, Set[String]]"), "pureNativeMethods should be Map[String, Set[String]]")
    assert(scala.contains("Set[String]"), "purePropAccessGlobals should be Set[String]")
    // objectMethods is private
    assert(scala.contains("private val objectMethods"), "objectMethods should be private")
    // Boolean/Function just use objectMethods
    assert(scala.contains("\"Boolean\" -> objectMethods"), "Boolean should map to objectMethods")
    assert(scala.contains("\"Function\" -> objectMethods"), "Function should map to objectMethods")
    // String has many methods
    assert(scala.contains("\"trimStart\""), "String should have trimStart")
    assert(scala.contains("\"replaceAll\""), "String should have replaceAll")

  test("native-objects.js: write emitted NativeObjects to target"):
    val rast = loadRast("/rast/terser/lib/compress/native-objects.rast.json")
    val scala = balticporter.corpus.terser.TerserEmitter.emitNativeObjects(rast)
    val outDir = java.nio.file.Path.of(sys.props.getOrElse("user.dir", ".")).resolve("target/emitted-terser")
    java.nio.file.Files.createDirectories(outDir)
    val path = outDir.resolve("NativeObjects.scala")
    java.nio.file.Files.writeString(path, scala)
    println(s"[emit] NativeObjects.scala: ${scala.linesIterator.size} lines -> $path")

  // -----------------------------------------------------------------------
  // Sourcemap module emission
  // -----------------------------------------------------------------------

  test("sourcemap: emit Base64 codec"):
    val dummyRast = loadRast("/rast/terser/lib/compress/native-objects.rast.json")
    val scala = balticporter.corpus.terser.TerserEmitter.emitBase64(dummyRast)
    println("=== Base64.scala (emitted) ===")
    println(scala)
    assert(scala.contains("object Base64"), "should emit Base64 object")
    assert(scala.contains("def encode(str: String): String"), "should have encode method")
    assert(scala.contains("def decode(b64: String): String"), "should have decode method")
    assert(scala.contains("StandardCharsets.UTF_8"), "should use UTF_8")
    assert(scala.contains("Alphabet"), "should have Alphabet array")
    assert(scala.contains("ABCDEFGHIJKLMNOPQRSTUVWXYZabcdefghijklmnopqrstuvwxyz0123456789+/"),
      "should have base64 alphabet")
    assert(scala.contains("0x3f"), "should use bit masks")

  test("sourcemap: emit VlqCodec"):
    val dummyRast = loadRast("/rast/terser/lib/compress/native-objects.rast.json")
    val scala = balticporter.corpus.terser.TerserEmitter.emitVlqCodec(dummyRast)
    println("=== VlqCodec.scala (emitted) ===")
    println(scala)
    assert(scala.contains("object VlqCodec"), "should emit VlqCodec object")
    assert(scala.contains("def encode(value: Int): String"), "should have encode method")
    assert(scala.contains("def decode(str: String, offset: Int): (Int, Int)"), "should have decode method")
    assert(scala.contains("def encodeSegment(values: Array[Int]): String"), "should have encodeSegment method")
    assert(scala.contains("def decodeMappings(mappings: String)"), "should have decodeMappings method")
    assert(scala.contains("def encodeMappings(decoded: Array[Array[Array[Int]]])"), "should have encodeMappings method")
    assert(scala.contains("VlqBaseShift"), "should have VLQ constants")
    assert(scala.contains("VlqContinuation"), "should have continuation bit")

  test("sourcemap: emit SourceMapTypes"):
    val dummyRast = loadRast("/rast/terser/lib/compress/native-objects.rast.json")
    val scala = balticporter.corpus.terser.TerserEmitter.emitSourceMapTypes(dummyRast)
    println("=== SourceMapTypes.scala (emitted) ===")
    println(scala)
    assert(scala.contains("final case class SourceMapping"), "should have SourceMapping")
    assert(scala.contains("generatedLine"), "should have generatedLine field")
    assert(scala.contains("generatedColumn"), "should have generatedColumn field")
    assert(scala.contains("originalLine"), "should have originalLine field")
    assert(scala.contains("final case class SourceMapData"), "should have SourceMapData")
    assert(scala.contains("version:        Int = 3"), "should default version to 3")
    assert(scala.contains("sources:"), "should have sources field")
    assert(scala.contains("sourcesContent:"), "should have sourcesContent field")
    assert(scala.contains("final case class OriginalPosition"), "should have OriginalPosition")
    assert(scala.contains("final case class SourceMapOptions"), "should have SourceMapOptions")

  test("sourcemap: emit InlineSourceMap"):
    val dummyRast = loadRast("/rast/terser/lib/compress/native-objects.rast.json")
    val scala = balticporter.corpus.terser.TerserEmitter.emitInlineSourceMap(dummyRast)
    println("=== InlineSourceMap.scala (emitted) ===")
    println(scala)
    assert(scala.contains("object InlineSourceMap"), "should emit InlineSourceMap object")
    assert(scala.contains("def readSourceMap(code: String): String | Null"), "should have readSourceMap method")
    assert(scala.contains("InlineMapRegex"), "should have regex")
    assert(scala.contains("Base64.decode"), "should use Base64.decode")
    assert(scala.contains("sourceMappingURL"), "should reference sourceMappingURL")
    assert(scala.contains("inline source map not found"), "should have warning message")

  test("sourcemap: write all emitted sourcemap files to target"):
    val dummyRast = loadRast("/rast/terser/lib/compress/native-objects.rast.json")
    val outDir = java.nio.file.Path.of(sys.props.getOrElse("user.dir", ".")).resolve("target/emitted-terser")
    java.nio.file.Files.createDirectories(outDir)
    val files = List(
      ("Base64", balticporter.corpus.terser.TerserEmitter.emitBase64(dummyRast)),
      ("VlqCodec", balticporter.corpus.terser.TerserEmitter.emitVlqCodec(dummyRast)),
      ("SourceMapTypes", balticporter.corpus.terser.TerserEmitter.emitSourceMapTypes(dummyRast)),
      ("InlineSourceMap", balticporter.corpus.terser.TerserEmitter.emitInlineSourceMap(dummyRast)),
    )
    for ((name, source) <- files) {
      val path = outDir.resolve(s"$name.scala")
      java.nio.file.Files.writeString(path, source)
      println(s"[emit] $name.scala: ${source.linesIterator.size} lines -> $path")
    }
    println(s"[emit] Total: ${files.size} sourcemap files written")

  // -----------------------------------------------------------------------
  // Output module emission
  // -----------------------------------------------------------------------

  test("output: emit OutputOptions case class"):
    val dummyRast = loadRast("/rast/terser/lib/compress/native-objects.rast.json")
    val scala = balticporter.corpus.terser.TerserEmitter.emitOutputOptions(dummyRast)
    println("=== OutputOptions.scala (emitted) ===")
    println(scala)
    assert(scala.contains("final case class OutputOptions"), "should emit OutputOptions case class")
    assert(scala.contains("asciiOnly:"), "should have asciiOnly field")
    assert(scala.contains("beautify:"), "should have beautify field")
    assert(scala.contains("ecma:                Int = 5"), "should default ecma to 5")
    assert(scala.contains("semicolons:          Boolean = true"), "should default semicolons to true")
    assert(scala.contains("shorthand:           Option[Boolean] = None"), "should have Optional shorthand")
    assert(scala.contains("quoteStyle:          Int = 0"), "should have quoteStyle")
    assert(scala.contains("sourceMap:           ssg.js.sourcemap.SourceMap | Null = null"), "should have sourceMap")
    assert(scala.contains("maxLineLen:"), "should have maxLineLen")
    assert(scala.contains("preamble:"), "should have preamble")
    assert(scala.contains("wrapIife:"), "should have wrapIife")

  test("output: emit JsNumber object"):
    val dummyRast = loadRast("/rast/terser/lib/compress/native-objects.rast.json")
    val scala = balticporter.corpus.terser.TerserEmitter.emitJsNumber(dummyRast)
    println("=== JsNumber.scala (emitted) ===")
    println(scala)
    assert(scala.contains("object JsNumber"), "should emit JsNumber object")
    assert(scala.contains("def toJsString(num: Double): String"), "should have toJsString method")
    assert(scala.contains("\"NaN\""), "should handle NaN")
    assert(scala.contains("\"Infinity\""), "should handle Infinity")
    assert(scala.contains("ecmaFormat"), "should use ecmaFormat helper")
    assert(scala.contains("private def ecmaFormat"), "should have ecmaFormat method")
    assert(scala.contains("n <= 21"), "should implement ECMA cases")
    assert(scala.contains("\"e\""), "should format with exponent")
    assert(scala.contains("mantissa"), "should parse mantissa")
    assert(scala.contains("parsedExp"), "should parse exponent")

  test("output: write all emitted output files to target"):
    val dummyRast = loadRast("/rast/terser/lib/compress/native-objects.rast.json")
    val outDir = java.nio.file.Path.of(sys.props.getOrElse("user.dir", ".")).resolve("target/emitted-terser")
    java.nio.file.Files.createDirectories(outDir)
    val files = List(
      ("OutputOptions", balticporter.corpus.terser.TerserEmitter.emitOutputOptions(dummyRast)),
      ("JsNumber", balticporter.corpus.terser.TerserEmitter.emitJsNumber(dummyRast)),
    )
    for ((name, source) <- files) {
      val path = outDir.resolve(s"$name.scala")
      java.nio.file.Files.writeString(path, source)
      println(s"[emit] $name.scala: ${source.linesIterator.size} lines -> $path")
    }
    println(s"[emit] Total: ${files.size} output files written")

  // -----------------------------------------------------------------------
  // AST token emission
  // -----------------------------------------------------------------------

  test("ast: emit AstToken case class"):
    val dummyRast = loadRast("/rast/terser/lib/compress/native-objects.rast.json")
    val scala = balticporter.corpus.terser.TerserEmitter.emitAstToken(dummyRast)
    println("=== AstToken.scala (emitted) ===")
    println(scala)
    assert(scala.contains("final case class AstToken"), "should emit AstToken case class")
    assert(scala.contains("tokenType:      String"), "should have tokenType field")
    assert(scala.contains("value:          String"), "should have value field")
    assert(scala.contains("line:           Int"), "should have line field")
    assert(scala.contains("col:            Int"), "should have col field")
    assert(scala.contains("pos:            Int"), "should have pos field")
    assert(scala.contains("var flags:      Int = 0"), "should have mutable flags field")
    assert(scala.contains("commentsBefore: List[AstToken]"), "should have commentsBefore")
    assert(scala.contains("commentsAfter:  List[AstToken]"), "should have commentsAfter")
    assert(scala.contains("def nlb:"), "should have nlb accessor")
    assert(scala.contains("def nlb_="), "should have nlb setter")
    assert(scala.contains("def quote:"), "should have quote accessor")
    assert(scala.contains("def quote_="), "should have quote setter")
    assert(scala.contains("def templateEnd:"), "should have templateEnd accessor")
    assert(scala.contains("FlagNlb"), "should have FlagNlb constant")
    assert(scala.contains("FlagQuoteSingle"), "should have FlagQuoteSingle constant")
    assert(scala.contains("FlagQuoteExists"), "should have FlagQuoteExists constant")
    assert(scala.contains("FlagTemplateEnd"), "should have FlagTemplateEnd constant")
    assert(scala.contains("val Empty: AstToken"), "should have Empty sentinel")

  test("ast: write emitted AstToken to target"):
    val dummyRast = loadRast("/rast/terser/lib/compress/native-objects.rast.json")
    val scala = balticporter.corpus.terser.TerserEmitter.emitAstToken(dummyRast)
    val outDir = java.nio.file.Path.of(sys.props.getOrElse("user.dir", ".")).resolve("target/emitted-terser")
    java.nio.file.Files.createDirectories(outDir)
    val path = outDir.resolve("AstToken.scala")
    java.nio.file.Files.writeString(path, scala)
    println(s"[emit] AstToken.scala: ${scala.linesIterator.size} lines -> $path")

  // -----------------------------------------------------------------------
  // AST constants emission
  // -----------------------------------------------------------------------

  test("ast: emit AstConstants with leaf node classes"):
    val dummyRast = loadRast("/rast/terser/lib/compress/native-objects.rast.json")
    val scala = balticporter.corpus.terser.TerserEmitter.emitAstConstants(dummyRast)
    println("=== AstConstants.scala (emitted) ===")
    println(scala)
    assert(scala.contains("final case class RegExpValue"), "should have RegExpValue")
    assert(scala.contains("trait AstConstant extends AstNode"), "should have AstConstant trait")
    assert(scala.contains("class AstString extends AstNode with AstConstant"), "should have AstString")
    assert(scala.contains("class AstNumber extends AstNode with AstConstant"), "should have AstNumber")
    assert(scala.contains("class AstBigInt extends AstNode with AstConstant"), "should have AstBigInt")
    assert(scala.contains("class AstRegExp extends AstNode with AstConstant"), "should have AstRegExp")
    assert(scala.contains("trait AstAtom extends AstConstant"), "should have AstAtom trait")
    assert(scala.contains("class AstNull"), "should have AstNull")
    assert(scala.contains("class AstNaN"), "should have AstNaN")
    assert(scala.contains("class AstUndefined"), "should have AstUndefined")
    assert(scala.contains("class AstInfinity"), "should have AstInfinity")
    assert(scala.contains("class AstHole"), "should have AstHole")
    assert(scala.contains("trait AstBoolean extends AstAtom"), "should have AstBoolean trait")
    assert(scala.contains("class AstTrue"), "should have AstTrue")
    assert(scala.contains("class AstFalse"), "should have AstFalse")
    assert(scala.contains("var value: Double = 0.0"), "AstNumber should have Double value")
    assert(scala.contains("var value: RegExpValue"), "AstRegExp should have RegExpValue")

  test("ast: write emitted AstConstants to target"):
    val dummyRast = loadRast("/rast/terser/lib/compress/native-objects.rast.json")
    val scala = balticporter.corpus.terser.TerserEmitter.emitAstConstants(dummyRast)
    val outDir = java.nio.file.Path.of(sys.props.getOrElse("user.dir", ".")).resolve("target/emitted-terser")
    java.nio.file.Files.createDirectories(outDir)
    val path = outDir.resolve("AstConstants.scala")
    java.nio.file.Files.writeString(path, scala)
    println(s"[emit] AstConstants.scala: ${scala.linesIterator.size} lines -> $path")

  // -----------------------------------------------------------------------
  // AST hierarchy emission
  // -----------------------------------------------------------------------

  test("ast: emit AST hierarchy from DEFNODE extraction"):
    val rast = loadRast("/rast/terser/lib/ast.rast.json")
    val scala = balticporter.corpus.terser.TerserEmitter.emitAstHierarchy(rast)
    println("=== AstHierarchy.scala (emitted, first 80 lines) ===")
    scala.linesIterator.take(80).foreach(println)
    // Check structural elements
    assert(scala.contains("package ssg"), "should have ssg package")
    assert(scala.contains("import scala.collection.mutable.ArrayBuffer"), "should import ArrayBuffer")
    // Should NOT contain skipped classes
    assert(!scala.contains("trait AstNode"), "should NOT emit AstNode (hand-ported)")
    assert(!scala.contains("trait AstConstant"), "should NOT emit AstConstant (emitted by emitAstConstants)")
    assert(!scala.contains("class AstString "), "should NOT emit AstString (emitted by emitAstConstants)")
    assert(!scala.contains("class AstTrue "), "should NOT emit AstTrue (emitted by emitAstConstants)")
    // Should contain non-skipped classes
    assert(scala.contains("AstStatement"), "should contain AstStatement")
    assert(scala.contains("AstBlock"), "should contain AstBlock")
    assert(scala.contains("AstCall"), "should contain AstCall")
    assert(scala.contains("AstBinary"), "should contain AstBinary")
    assert(scala.contains("AstScope"), "should contain AstScope")
    assert(scala.contains("AstSymbol"), "should contain AstSymbol")

  test("ast: emitted hierarchy contains statement nodes"):
    val rast = loadRast("/rast/terser/lib/ast.rast.json")
    val scala = balticporter.corpus.terser.TerserEmitter.emitAstHierarchy(rast)
    assert(scala.contains("AstBlock"), "should contain AstBlock")
    assert(scala.contains("AstIf"), "should contain AstIf")
    assert(scala.contains("AstWhile"), "should contain AstWhile")
    assert(scala.contains("AstFor "), "should contain AstFor")
    assert(scala.contains("AstSwitch"), "should contain AstSwitch")
    assert(scala.contains("AstTry"), "should contain AstTry")
    assert(scala.contains("AstReturn"), "should contain AstReturn")
    assert(scala.contains("AstBreak"), "should contain AstBreak")
    assert(scala.contains("AstContinue"), "should contain AstContinue")
    assert(scala.contains("AstDebugger"), "should contain AstDebugger")

  test("ast: emitted hierarchy contains expression nodes"):
    val rast = loadRast("/rast/terser/lib/ast.rast.json")
    val scala = balticporter.corpus.terser.TerserEmitter.emitAstHierarchy(rast)
    assert(scala.contains("AstBinary"), "should contain AstBinary")
    assert(scala.contains("AstUnary"), "should contain AstUnary")
    assert(scala.contains("AstCall"), "should contain AstCall")
    assert(scala.contains("AstNew"), "should contain AstNew")
    assert(scala.contains("AstDot"), "should contain AstDot")
    assert(scala.contains("AstSub"), "should contain AstSub")
    assert(scala.contains("AstConditional"), "should contain AstConditional")
    assert(scala.contains("AstArray"), "should contain AstArray")
    assert(scala.contains("AstObject"), "should contain AstObject")
    assert(scala.contains("AstSequence"), "should contain AstSequence")

  test("ast: write emitted AST hierarchy to target"):
    val rast = loadRast("/rast/terser/lib/ast.rast.json")
    val scala = balticporter.corpus.terser.TerserEmitter.emitAstHierarchy(rast)
    val outDir = java.nio.file.Path.of(sys.props.getOrElse("user.dir", ".")).resolve("target/emitted-terser")
    java.nio.file.Files.createDirectories(outDir)
    val path = outDir.resolve("AstHierarchy.scala")
    java.nio.file.Files.writeString(path, scala)
    println(s"[emit] AstHierarchy.scala: ${scala.linesIterator.size} lines -> $path")

  // -----------------------------------------------------------------------
  // AST statements emission
  // -----------------------------------------------------------------------

  test("ast: emit AST statements file"):
    val rast = loadRast("/rast/terser/lib/ast.rast.json")
    val scala = balticporter.corpus.terser.TerserEmitter.emitAstStatements(rast)
    println("=== AstStatements.scala (emitted, first 60 lines) ===")
    scala.linesIterator.take(60).foreach(println)
    assert(scala.contains("AstStatement"), "should contain AstStatement")
    assert(scala.contains("AstBlock"), "should contain AstBlock")
    assert(scala.contains("AstIf"), "should contain AstIf")
    assert(scala.contains("AstFor "), "should contain AstFor")
    assert(scala.contains("AstReturn"), "should contain AstReturn")
    assert(scala.contains("AstBreak"), "should contain AstBreak")
    // Should NOT contain scope/expression classes
    assert(!scala.contains("AstToplevel"), "should NOT contain AstToplevel (scope)")
    assert(!scala.contains("AstLambda"), "should NOT contain AstLambda (scope)")
    assert(!scala.contains("AstCall"), "should NOT contain AstCall (expression)")
    assert(!scala.contains("AstBinary"), "should NOT contain AstBinary (expression)")

  // -----------------------------------------------------------------------
  // Write all terser utility files for ssg compilation verification
  // -----------------------------------------------------------------------

  test("write CompressorFlags, FirstInStatement, NativeObjects to target"):
    val outDir = java.nio.file.Path.of(sys.props.getOrElse("user.dir", ".")).resolve("target/emitted-terser")
    java.nio.file.Files.createDirectories(outDir)

    val cfRast = loadRast("/rast/terser/lib/compress/compressor-flags.rast.json")
    val cfScala = balticporter.corpus.terser.TerserEmitter.emitCompressorFlags(cfRast)
    java.nio.file.Files.writeString(outDir.resolve("CompressorFlags.scala"), cfScala)
    println(s"[emit] CompressorFlags.scala: ${cfScala.linesIterator.size} lines")

    val fisRast = loadRast("/rast/terser/lib/utils/first_in_statement.rast.json")
    val fisScala = balticporter.corpus.terser.TerserEmitter.emitFirstInStatement(fisRast)
    java.nio.file.Files.writeString(outDir.resolve("FirstInStatement.scala"), fisScala)
    println(s"[emit] FirstInStatement.scala: ${fisScala.linesIterator.size} lines")

    val noRast = loadRast("/rast/terser/lib/compress/native-objects.rast.json")
    val noScala = balticporter.corpus.terser.TerserEmitter.emitNativeObjects(noRast)
    java.nio.file.Files.writeString(outDir.resolve("NativeObjects.scala"), noScala)
    println(s"[emit] NativeObjects.scala: ${noScala.linesIterator.size} lines")

  // -----------------------------------------------------------------------
  // Deterministic regeneration tests
  // -----------------------------------------------------------------------

  test("deterministic: hierarchy and body translation produce identical output"):
    val astRast = loadRast("/rast/terser/lib/ast.rast.json")
    val commonRast = loadRast("/rast/terser/lib/compress/common.rast.json")

    // Run 1
    val hierarchy1 = balticporter.corpus.terser.TerserEmitter.extractHierarchy(astRast)
    val summary1 = balticporter.corpus.terser.TerserEmitter.hierarchySummary(hierarchy1)
    val freeFns1 = balticporter.corpus.terser.TerserEmitter.extractFreeFunctions(commonRast)
    val bodies1 = freeFns1.map { fn =>
      val entry = balticporter.corpus.terser.TerserEmitter.DefmethodEntry("_free_", fn.name, fn.params, fn.bodyNode)
      dedicated.DefmethodBodyTranslator.translateBody(entry, hierarchy1, "    ")
    }

    // Run 2
    val hierarchy2 = balticporter.corpus.terser.TerserEmitter.extractHierarchy(astRast)
    val summary2 = balticporter.corpus.terser.TerserEmitter.hierarchySummary(hierarchy2)
    val freeFns2 = balticporter.corpus.terser.TerserEmitter.extractFreeFunctions(commonRast)
    val bodies2 = freeFns2.map { fn =>
      val entry = balticporter.corpus.terser.TerserEmitter.DefmethodEntry("_free_", fn.name, fn.params, fn.bodyNode)
      dedicated.DefmethodBodyTranslator.translateBody(entry, hierarchy2, "    ")
    }

    assertEquals(summary1, summary2, "hierarchy summary should be byte-equal across runs")
    assertEquals(hierarchy1.size, hierarchy2.size, "hierarchy size should match")
    for (c1, c2) <- hierarchy1.zip(hierarchy2) do
      assertEquals(c1.varName, c2.varName, s"class name mismatch")
      assertEquals(c1.typeName, c2.typeName, s"type name mismatch for ${c1.varName}")
      assertEquals(c1.selfProps, c2.selfProps, s"props mismatch for ${c1.varName}")
      assertEquals(c1.base, c2.base, s"base mismatch for ${c1.varName}")

    assertEquals(freeFns1.size, freeFns2.size, "free function count should match")
    for (f1, f2) <- freeFns1.zip(freeFns2) do
      assertEquals(f1.name, f2.name, "function name mismatch")

    assertEquals(bodies1.size, bodies2.size, "body count should match")
    for i <- bodies1.indices do
      assertEquals(bodies1(i).scalaBody, bodies2(i).scalaBody,
        s"body ${freeFns1(i).name} should be byte-equal across runs")
      assertEquals(bodies1(i).refusalCount, bodies2(i).refusalCount,
        s"refusal count for ${freeFns1(i).name} should match")

    println(s"Determinism verified: ${hierarchy1.size} classes, ${freeFns1.size} functions, ${bodies1.size} bodies")

  test("deterministic: Mermaid styles emission produces identical output"):
    val rast = loadRast("/rast/mermaid/src/diagrams/pie/pieStyles.rast.json")

    val out1 = balticporter.corpus.mermaid.MermaidEmitter.emitStyles(rast, "PieStyles", "pie")
    val out2 = balticporter.corpus.mermaid.MermaidEmitter.emitStyles(rast, "PieStyles", "pie")

    assertEquals(out1, out2, "Mermaid styles emission should be byte-equal across runs")
    println(s"Mermaid styles determinism verified: ${out1.linesIterator.size} lines")

  test("deterministic: vitest→MUnit emission produces identical output"):
    val rast = loadRast("/rast/mermaid/src/diagrams/info/info.spec.rast.json")
    val cfg = dedicated.VitestToMunitEmitter.EmitConfig(
      packageName = "test.generated",
      className = "InfoSuite",
    )

    val result1 = dedicated.VitestToMunitEmitter.emit(rast, cfg)
    val result2 = dedicated.VitestToMunitEmitter.emit(rast, cfg)

    assertEquals(result1.scala, result2.scala, "vitest emission should be byte-equal across runs")
    assertEquals(result1.testCount, result2.testCount, "test count should match")
    assertEquals(result1.ignoredCount, result2.ignoredCount, "ignored count should match")
    assertEquals(result1.assertionCounts, result2.assertionCounts, "assertion counts should match")
    println(s"Vitest→MUnit determinism verified: ${result1.testCount} tests, ${result1.scala.linesIterator.size} lines")
