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
    val classes = dedicated.TerserEmitter.extractHierarchy(rast)
    // Terser has 133-134 DEFNODE calls (AST_Node is also a DEFNODE)
    assert(classes.size >= 130, s"Expected >= 130 DEFNODE classes, got ${classes.size}")
    assert(classes.size <= 140, s"Expected <= 140 DEFNODE classes, got ${classes.size}")

  test("ast.js: AST_Node is root with start/end props"):
    val rast = loadRast("/rast/terser/lib/ast.rast.json")
    val classes = dedicated.TerserEmitter.extractHierarchy(rast)
    val astNode = classes.find(_.varName == "AST_Node")
    assert(astNode.isDefined, "AST_Node should be in the hierarchy")
    assert(astNode.get.selfProps.contains("start"), "AST_Node should have 'start' prop")
    assert(astNode.get.selfProps.contains("end"), "AST_Node should have 'end' prop")

  test("ast.js: AST_Statement extends AST_Node"):
    val rast = loadRast("/rast/terser/lib/ast.rast.json")
    val classes = dedicated.TerserEmitter.extractHierarchy(rast)
    val stmt = classes.find(_.varName == "AST_Statement")
    assert(stmt.isDefined, "AST_Statement should exist")
    assert(stmt.get.base.contains("AST_Node"), s"AST_Statement base should be AST_Node, got ${stmt.get.base}")

  test("ast.js: AST_For has init/condition/step props"):
    val rast = loadRast("/rast/terser/lib/ast.rast.json")
    val classes = dedicated.TerserEmitter.extractHierarchy(rast)
    val forNode = classes.find(_.varName == "AST_For")
    assert(forNode.isDefined, "AST_For should exist")
    assert(forNode.get.selfProps == List("init", "condition", "step"),
      s"AST_For props should be [init, condition, step], got ${forNode.get.selfProps}")

  test("ast.js: abstract vs concrete classification"):
    val rast = loadRast("/rast/terser/lib/ast.rast.json")
    val classes = dedicated.TerserEmitter.extractHierarchy(rast)
    val byName = classes.map(c => c.varName -> c).toMap
    // AST_Statement should be abstract (has subclasses)
    assert(byName("AST_Statement").isAbstract, "AST_Statement should be abstract")
    // AST_Debugger should be concrete (leaf)
    assert(!byName("AST_Debugger").isAbstract, "AST_Debugger should be concrete")
    // AST_Node should be abstract
    assert(byName("AST_Node").isAbstract, "AST_Node should be abstract")

  test("ast.js: hierarchy summary is readable"):
    val rast = loadRast("/rast/terser/lib/ast.rast.json")
    val classes = dedicated.TerserEmitter.extractHierarchy(rast)
    val summary = dedicated.TerserEmitter.hierarchySummary(classes)
    println("=== DEFNODE Hierarchy ===")
    println(summary)
    assert(summary.contains("AST_Node"), "Summary should contain AST_Node")
    assert(summary.contains("AST_Statement"), "Summary should contain AST_Statement")
    assert(summary.contains("AST_For"), "Summary should contain AST_For")
    assert(summary.contains("trait"), "Summary should classify abstract classes as traits")
    assert(summary.contains("class"), "Summary should classify concrete classes as classes")

  test("ast.js: methods are extracted"):
    val rast = loadRast("/rast/terser/lib/ast.rast.json")
    val classes = dedicated.TerserEmitter.extractHierarchy(rast)
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
    val scala = dedicated.TerserEmitter.emitCompressorFlags(rast)
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
    val scala = dedicated.TerserEmitter.emitFirstInStatement(rast)
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
    val entries = dedicated.TerserEmitter.extractDefmethods(rast)
    assert(entries.nonEmpty, "should find DEFMETHOD entries")
    // scope.js has 36 DEFMETHOD calls (including return_false/return_true/return_this references)
    assert(entries.size >= 35, s"Expected >= 35 DEFMETHOD entries, got ${entries.size}")

  test("scope.js: first DEFMETHOD is figure_out_scope on AST_Scope"):
    val rast = loadRast("/rast/terser/lib/scope.rast.json")
    val entries = dedicated.TerserEmitter.extractDefmethods(rast)
    val first = entries.head
    assert(first.className == "AST_Scope", s"Expected AST_Scope, got ${first.className}")
    assert(first.methodName == "figure_out_scope", s"Expected figure_out_scope, got ${first.methodName}")
    assert(first.params.nonEmpty, "figure_out_scope should have parameters")

  test("scope.js: DEFMETHOD entries include def_global on AST_Toplevel"):
    val rast = loadRast("/rast/terser/lib/scope.rast.json")
    val entries = dedicated.TerserEmitter.extractDefmethods(rast)
    val defGlobal = entries.find(e => e.methodName == "def_global" && e.className == "AST_Toplevel")
    assert(defGlobal.isDefined, "should find def_global on AST_Toplevel")

  test("scope.js: DEFMETHOD entries include is_block_scope on multiple classes"):
    val rast = loadRast("/rast/terser/lib/scope.rast.json")
    val entries = dedicated.TerserEmitter.extractDefmethods(rast)
    val blockScopes = entries.filter(_.methodName == "is_block_scope")
    assert(blockScopes.size >= 8, s"Expected >= 8 is_block_scope entries, got ${blockScopes.size}")
    val classNames = blockScopes.map(_.className).toSet
    assert(classNames.contains("AST_Node"), "AST_Node should have is_block_scope")
    assert(classNames.contains("AST_Block"), "AST_Block should have is_block_scope")
    assert(classNames.contains("AST_Scope"), "AST_Scope should have is_block_scope")

  test("scope.js: groupByClass groups correctly"):
    val rast = loadRast("/rast/terser/lib/scope.rast.json")
    val entries = dedicated.TerserEmitter.extractDefmethods(rast)
    val grouped = dedicated.TerserEmitter.groupByClass(entries)
    assert(grouped.contains("AST_Scope"), "should have AST_Scope group")
    assert(grouped.contains("AST_Toplevel"), "should have AST_Toplevel group")
    assert(grouped.contains("AST_Symbol"), "should have AST_Symbol group")
    // AST_Scope should have the most methods
    val scopeMethods = grouped("AST_Scope")
    assert(scopeMethods.size >= 8, s"AST_Scope should have >= 8 methods, got ${scopeMethods.size}")

  test("scope.js: defmethodSummary is readable"):
    val rast = loadRast("/rast/terser/lib/scope.rast.json")
    val entries = dedicated.TerserEmitter.extractDefmethods(rast)
    val summary = dedicated.TerserEmitter.defmethodSummary(entries)
    println("=== DEFMETHOD Summary ===")
    println(summary)
    assert(summary.contains("DEFMETHOD summary"), "should have summary header")
    assert(summary.contains("AST_Scope"), "should mention AST_Scope")
    assert(summary.contains("figure_out_scope"), "should mention figure_out_scope")

  test("scope.js: merge DEFMETHOD entries into hierarchy"):
    val astRast = loadRast("/rast/terser/lib/ast.rast.json")
    val scopeRast = loadRast("/rast/terser/lib/scope.rast.json")
    val hierarchy = dedicated.TerserEmitter.extractHierarchy(astRast)
    val defmethods = dedicated.TerserEmitter.extractDefmethods(scopeRast)
    val merged = dedicated.TerserEmitter.mergeDefmethods(hierarchy, defmethods)
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
    val hierarchy = dedicated.TerserEmitter.extractHierarchy(astRast)
    val defmethods = dedicated.TerserEmitter.extractDefmethods(scopeRast)
    val merged = dedicated.TerserEmitter.mergeDefmethods(hierarchy, defmethods)
    val scopeEntry = merged.find(_._1.varName == "AST_Scope").get
    val scala = dedicated.TerserEmitter.emitMergedClass(scopeEntry._1, scopeEntry._2)
    println("=== AstScope (merged) ===")
    println(scala)
    assert(scala.contains("AstScope"), "should emit AstScope")
    assert(scala.contains("DEFMETHOD additions"), "should have DEFMETHOD section")
    assert(scala.contains("figureOutScope"), "should have figureOutScope method (camelCase)")
    assert(scala.contains("initScopeVars"), "should have initScopeVars method (camelCase)")

  test("scope.js: extract free functions"):
    val rast = loadRast("/rast/terser/lib/scope.rast.json")
    val funcs = dedicated.TerserEmitter.extractFreeFunctions(rast)
    assert(funcs.nonEmpty, "should find free functions")
    val names = funcs.map(_.name)
    assert(names.contains("redefined_catch_def"), "should find redefined_catch_def")

  test("scope.js: extract class declarations"):
    val rast = loadRast("/rast/terser/lib/scope.rast.json")
    val classes = dedicated.TerserEmitter.extractClassDeclarations(rast)
    assert(classes.contains("SymbolDef"), "should find SymbolDef class")

  // -----------------------------------------------------------------------
  // SymbolDef class emission
  // -----------------------------------------------------------------------

  test("scope.js: emit SymbolDef class"):
    val rast = loadRast("/rast/terser/lib/scope.rast.json")
    val scala = dedicated.TerserEmitter.emitSymbolDef(rast)
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
    val scala = dedicated.TerserEmitter.emitSymbolDef(rast)
    // Verify key patterns from hand port
    assert(scala.contains("ArrayBuffer(origArg)"), "orig should be initialized with constructor param")
    assert(scala.contains("origArg.name"), "name should come from orig symbol")
    assert(scala.contains("SymbolDef.nextId"), "id should use companion nextId counter")
    assert(scala.contains("AstSymbolCatch"), "redefinedCatchDef should check AstSymbolCatch")
    assert(scala.contains("ManglerOptions"), "unmangleable should take ManglerOptions")
    assert(scala.contains("keepName"), "should have keepName helper")

  test("scope.js: write emitted SymbolDef to target"):
    val rast = loadRast("/rast/terser/lib/scope.rast.json")
    val scala = dedicated.TerserEmitter.emitSymbolDef(rast)
    val outDir = java.nio.file.Path.of(sys.props.getOrElse("user.dir", ".")).resolve("target/emitted-terser")
    java.nio.file.Files.createDirectories(outDir)
    val path = outDir.resolve("SymbolDef.scala")
    java.nio.file.Files.writeString(path, scala)
    println(s"[emit] SymbolDef.scala: ${scala.linesIterator.size} lines -> $path")
