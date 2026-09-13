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
