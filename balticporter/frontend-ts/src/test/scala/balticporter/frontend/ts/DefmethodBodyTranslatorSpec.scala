package balticporter.frontend.ts

class DefmethodBodyTranslatorSpec extends munit.FunSuite:

  private def loadRast(resource: String): RastFile =
    val stream = getClass.getResourceAsStream(resource)
    assert(stream != null, s"Resource not found: $resource")
    val json = new String(stream.readAllBytes(), java.nio.charset.StandardCharsets.UTF_8)
    stream.close()
    Rast.readFile(json)

  private lazy val astRast = loadRast("/rast/terser/lib/ast.rast.json")
  private lazy val hierarchy = dedicated.TerserEmitter.extractHierarchy(astRast)

  // -----------------------------------------------------------------------
  // scope.js DEFMETHOD extraction (existing extractor)
  // -----------------------------------------------------------------------

  test("scope.js: extract 36 DEFMETHOD entries"):
    val rast = loadRast("/rast/terser/lib/scope.rast.json")
    val entries = dedicated.TerserEmitter.extractDefmethods(rast)
    assertEquals(entries.size, 36, s"Expected 36 DEFMETHOD entries, got ${entries.size}")

  test("scope.js: DEFMETHOD classes are correct"):
    val rast = loadRast("/rast/terser/lib/scope.rast.json")
    val entries = dedicated.TerserEmitter.extractDefmethods(rast)
    val classes = entries.map(_.className).distinct.sorted
    assert(classes.contains("AST_Scope"), s"Should contain AST_Scope, got $classes")
    assert(classes.contains("AST_Symbol"), s"Should contain AST_Symbol, got $classes")
    assert(classes.contains("AST_Toplevel"), s"Should contain AST_Toplevel, got $classes")

  // -----------------------------------------------------------------------
  // equivalent-to.js prototype assignment extraction
  // -----------------------------------------------------------------------

  test("equivalent-to.js: extract prototype assignments"):
    val rast = loadRast("/rast/terser/lib/equivalent-to.rast.json")
    val entries = dedicated.DefmethodBodyTranslator.extractPrototypeAssignments(rast)
    // 66 prototype assignments for shallow_cmp
    assert(entries.size >= 60, s"Expected >= 60 prototype assignments, got ${entries.size}")
    assert(entries.forall(_.methodName == "shallow_cmp"),
      s"All should be shallow_cmp, got ${entries.map(_.methodName).distinct}")

  test("equivalent-to.js: pass_through references become return true"):
    val rast = loadRast("/rast/terser/lib/equivalent-to.rast.json")
    val entries = dedicated.DefmethodBodyTranslator.extractPrototypeAssignments(rast)
    val debugger = entries.find(_.className == "AST_Debugger")
    assert(debugger.isDefined, "Should find AST_Debugger")
    val result = dedicated.DefmethodBodyTranslator.translateBody(debugger.get, hierarchy)
    assert(result.isComplete, s"pass_through should translate fully, refusals: ${result.refusalReasons}")
    assert(result.scalaBody.contains("true"), s"Should return true, got: ${result.scalaBody}")

  test("equivalent-to.js: function body translates this.value === other.value"):
    val rast = loadRast("/rast/terser/lib/equivalent-to.rast.json")
    val entries = dedicated.DefmethodBodyTranslator.extractPrototypeAssignments(rast)
    val directive = entries.find(_.className == "AST_Directive")
    assert(directive.isDefined, "Should find AST_Directive")
    val result = dedicated.DefmethodBodyTranslator.translateBody(directive.get, hierarchy)
    assert(result.isComplete, s"Should translate fully, refusals: ${result.refusalReasons}")
    // Should compare this.value with other.value
    assert(result.scalaBody.contains("this.value") || result.scalaBody.contains("value"),
      s"Should reference value field, got: ${result.scalaBody}")

  // -----------------------------------------------------------------------
  // size.js prototype assignment extraction
  // -----------------------------------------------------------------------

  test("size.js: extract prototype assignments"):
    val rast = loadRast("/rast/terser/lib/size.rast.json")
    val entries = dedicated.DefmethodBodyTranslator.extractPrototypeAssignments(rast)
    assert(entries.size >= 40, s"Expected >= 40 prototype assignments, got ${entries.size}")

  test("size.js: simple return-literal bodies translate"):
    val rast = loadRast("/rast/terser/lib/size.rast.json")
    val entries = dedicated.DefmethodBodyTranslator.extractPrototypeAssignments(rast)
    // AST_Node._size returns 0 (there is also AST_Node.size which is more complex)
    val astNode = entries.find(e => e.className == "AST_Node" && e.methodName == "_size")
    assert(astNode.isDefined, s"Should find AST_Node._size, methods found: ${entries.filter(_.className == "AST_Node").map(_.methodName)}")
    val result = dedicated.DefmethodBodyTranslator.translateBody(astNode.get, hierarchy)
    assert(result.isComplete, s"Simple return 0 should translate, refusals: ${result.refusalReasons}")
    assert(result.scalaBody.trim == "0", s"Should return 0, got: '${result.scalaBody.trim}'")

  test("size.js: AST_Debugger._size returns 8"):
    val rast = loadRast("/rast/terser/lib/size.rast.json")
    val entries = dedicated.DefmethodBodyTranslator.extractPrototypeAssignments(rast)
    val debugger = entries.find(e => e.className == "AST_Debugger" && e.methodName == "_size")
    assert(debugger.isDefined, "Should find AST_Debugger._size")
    val result = dedicated.DefmethodBodyTranslator.translateBody(debugger.get, hierarchy)
    assert(result.isComplete, s"Return 8 should translate, refusals: ${result.refusalReasons}")
    assert(result.scalaBody.trim == "8", s"Should return 8, got: '${result.scalaBody.trim}'")

  // -----------------------------------------------------------------------
  // scope.js body translation
  // -----------------------------------------------------------------------

  test("scope.js: return_false references translate to false"):
    val rast = loadRast("/rast/terser/lib/scope.rast.json")
    val entries = dedicated.TerserEmitter.extractDefmethods(rast)
    // AST_Node.is_block_scope = return_false
    val isBlockScope = entries.find(e =>
      e.className == "AST_Node" && e.methodName == "is_block_scope")
    assert(isBlockScope.isDefined, "Should find AST_Node.is_block_scope")
    val result = dedicated.DefmethodBodyTranslator.translateBody(isBlockScope.get, hierarchy)
    assert(result.isComplete, s"return_false should translate, refusals: ${result.refusalReasons}")
    assert(result.scalaBody.contains("false"), s"Should return false, got: ${result.scalaBody}")

  test("scope.js: simple field access translates"):
    val rast = loadRast("/rast/terser/lib/scope.rast.json")
    val entries = dedicated.TerserEmitter.extractDefmethods(rast)
    // AST_Symbol.definition returns this.thedef
    val definition = entries.find(e =>
      e.className == "AST_Symbol" && e.methodName == "definition")
    assert(definition.isDefined, "Should find AST_Symbol.definition")
    val result = dedicated.DefmethodBodyTranslator.translateBody(definition.get, hierarchy)
    assert(result.isComplete, s"Simple field access should translate, refusals: ${result.refusalReasons}")
    assert(result.scalaBody.contains("this.thedef"), s"Should access this.thedef, got: ${result.scalaBody}")

  test("scope.js: instanceof translates correctly"):
    val rast = loadRast("/rast/terser/lib/scope.rast.json")
    val entries = dedicated.TerserEmitter.extractDefmethods(rast)
    // AST_Scope.find_variable has `name instanceof AST_Symbol`
    val findVariable = entries.find(e =>
      e.className == "AST_Scope" && e.methodName == "find_variable")
    assert(findVariable.isDefined, "Should find AST_Scope.find_variable")
    val result = dedicated.DefmethodBodyTranslator.translateBody(findVariable.get, hierarchy)
    assert(result.scalaBody.contains("isInstanceOf[AstSymbol]"),
      s"Should translate instanceof to isInstanceOf, got: ${result.scalaBody}")

  test("scope.js: this.thedef.global translates property chain"):
    val rast = loadRast("/rast/terser/lib/scope.rast.json")
    val entries = dedicated.TerserEmitter.extractDefmethods(rast)
    // AST_Symbol.global returns this.thedef.global
    val global = entries.find(e =>
      e.className == "AST_Symbol" && e.methodName == "global")
    assert(global.isDefined, "Should find AST_Symbol.global")
    val result = dedicated.DefmethodBodyTranslator.translateBody(global.get, hierarchy)
    assert(result.isComplete, s"Property chain should translate, refusals: ${result.refusalReasons}")
    assert(result.scalaBody.contains("this.thedef.global"),
      s"Should access this.thedef.global, got: ${result.scalaBody}")

  // -----------------------------------------------------------------------
  // Statistics
  // -----------------------------------------------------------------------

  test("scope.js: translation statistics"):
    val rast = loadRast("/rast/terser/lib/scope.rast.json")
    val entries = dedicated.TerserEmitter.extractDefmethods(rast)
    val stats = dedicated.DefmethodBodyTranslator.computeStats(entries, hierarchy)
    println(s"scope.js DEFMETHOD translation stats:")
    println(s"  Total:    ${stats.total}")
    println(s"  Full:     ${stats.fullyTranslated}")
    println(s"  Partial:  ${stats.partiallyTranslated}")
    println(s"  Refused:  ${stats.refused}")
    println(s"  Refusals: ${stats.totalRefusalCount}")
    // At minimum, the simple return_false/return_true/return_this ones should translate
    assert(stats.fullyTranslated >= 10,
      s"Expected >= 10 fully translated, got ${stats.fullyTranslated}")

  test("equivalent-to.js: translation statistics"):
    val rast = loadRast("/rast/terser/lib/equivalent-to.rast.json")
    val entries = dedicated.DefmethodBodyTranslator.extractPrototypeAssignments(rast)
    val stats = dedicated.DefmethodBodyTranslator.computeStats(entries, hierarchy)
    println(s"equivalent-to.js prototype assignment stats:")
    println(s"  Total:    ${stats.total}")
    println(s"  Full:     ${stats.fullyTranslated}")
    println(s"  Partial:  ${stats.partiallyTranslated}")
    println(s"  Refused:  ${stats.refused}")
    println(s"  Refusals: ${stats.totalRefusalCount}")
    // Most shallow_cmp bodies are trivial
    assert(stats.fullyTranslated >= 40,
      s"Expected >= 40 fully translated, got ${stats.fullyTranslated}")

  test("size.js: translation statistics"):
    val rast = loadRast("/rast/terser/lib/size.rast.json")
    val entries = dedicated.DefmethodBodyTranslator.extractPrototypeAssignments(rast)
    val stats = dedicated.DefmethodBodyTranslator.computeStats(entries, hierarchy)
    println(s"size.js prototype assignment stats:")
    println(s"  Total:    ${stats.total}")
    println(s"  Full:     ${stats.fullyTranslated}")
    println(s"  Partial:  ${stats.partiallyTranslated}")
    println(s"  Refused:  ${stats.refused}")
    println(s"  Refusals: ${stats.totalRefusalCount}")
    // Most _size bodies are return-literal or simple field accesses
    assert(stats.fullyTranslated >= 20,
      s"Expected >= 20 fully translated, got ${stats.fullyTranslated}")

  // -----------------------------------------------------------------------
  // Family emission
  // -----------------------------------------------------------------------

  test("equivalent-to.js: emit shallow_cmp family"):
    val rast = loadRast("/rast/terser/lib/equivalent-to.rast.json")
    val entries = dedicated.DefmethodBodyTranslator.extractPrototypeAssignments(rast)
    val output = dedicated.DefmethodBodyTranslator.emitDefmethodFamily(
      "shallow_cmp", entries, hierarchy)
    // Should contain class names and method definitions
    assert(output.contains("AstNode"), s"Should contain AstNode, got:\n$output")
    assert(output.contains("def shallowCmp"), s"Should contain shallowCmp method")
    assert(output.contains("AstDirective"), s"Should contain AstDirective")

  test("scope.js: emit is_block_scope family"):
    val rast = loadRast("/rast/terser/lib/scope.rast.json")
    val entries = dedicated.TerserEmitter.extractDefmethods(rast)
    val isBlockScope = entries.filter(_.methodName == "is_block_scope")
    assert(isBlockScope.size >= 7, s"Expected >= 7 is_block_scope entries, got ${isBlockScope.size}")
    val output = dedicated.DefmethodBodyTranslator.emitDefmethodFamily(
      "is_block_scope", isBlockScope, hierarchy)
    assert(output.contains("def isBlockScope"), s"Should emit isBlockScope")
    assert(output.contains("false") || output.contains("true"),
      s"Should contain boolean returns")

  // -----------------------------------------------------------------------
  // Sample output verification
  // -----------------------------------------------------------------------

  test("scope.js: show sample translated bodies"):
    val rast = loadRast("/rast/terser/lib/scope.rast.json")
    val entries = dedicated.TerserEmitter.extractDefmethods(rast)
    println("=== Sample scope.js DEFMETHOD translations ===")
    val samples = List(
      ("AST_Symbol", "definition"),
      ("AST_Symbol", "global"),
      ("AST_Scope", "is_block_scope"),
      ("AST_Scope", "find_variable"),
      ("AST_Sequence", "tail_node"),
    )
    for (cls, method) <- samples do
      val entry = entries.find(e => e.className == cls && e.methodName == method)
      entry.foreach { e =>
        val result = dedicated.DefmethodBodyTranslator.translateBody(e, hierarchy)
        val status = if result.isComplete then "COMPLETE" else s"PARTIAL (${result.refusalCount} refusals)"
        println(s"\n  $cls.$method [$status]:")
        println(s"    ${result.scalaBody.trim}")
        if result.refusalReasons.nonEmpty then
          println(s"    Refusals: ${result.refusalReasons.mkString(", ")}")
      }
