package balticporter.frontend.ts

class AstDtsGeneratorSpec extends munit.FunSuite:

  private def loadRast(resource: String): RastFile =
    val stream = getClass.getResourceAsStream(resource)
    assert(stream != null, s"Resource not found: $resource")
    val json = new String(stream.readAllBytes(), java.nio.charset.StandardCharsets.UTF_8)
    stream.close()
    Rast.readFile(json)

  // --------------------------------------------------------------------------
  // .d.ts generation from hierarchy
  // --------------------------------------------------------------------------

  test("generate produces valid .d.ts for the full hierarchy"):
    val rast      = loadRast("/rast/terser/lib/ast.rast.json")
    val hierarchy = balticporter.corpus.terser.TerserEmitter.extractHierarchy(rast)
    val dts       = balticporter.corpus.terser.AstDtsGenerator.generate(hierarchy)
    // Should contain class declarations
    assert(dts.contains("export declare class AST_Node"), "Should declare AST_Node")
    assert(dts.contains("export declare class AST_Statement"), "Should declare AST_Statement")
    assert(dts.contains("export declare class AST_For"), "Should declare AST_For")
    assert(dts.contains("export declare class AST_Token"), "Should declare AST_Token")
    // Should have the extends chain
    assert(dts.contains("extends AST_Node"), "Should have extends AST_Node")

  test("AST_Node has start and end fields typed"):
    val rast      = loadRast("/rast/terser/lib/ast.rast.json")
    val hierarchy = balticporter.corpus.terser.TerserEmitter.extractHierarchy(rast)
    val dts       = balticporter.corpus.terser.AstDtsGenerator.generate(hierarchy)
    assert(dts.contains("start: AST_Token | null;"), s"AST_Node should have typed start field")
    assert(dts.contains("end: AST_Token | null;"), s"AST_Node should have typed end field")

  test("AST_For has init, condition, step typed as AST_Node | null"):
    val rast      = loadRast("/rast/terser/lib/ast.rast.json")
    val hierarchy = balticporter.corpus.terser.TerserEmitter.extractHierarchy(rast)
    val dts       = balticporter.corpus.terser.AstDtsGenerator.generate(hierarchy)
    // AST_For props are: init, condition, step
    val forSection = extractClassSection(dts, "AST_For")
    assert(forSection.contains("init: AST_Node | null;"), s"AST_For should have init field, got: $forSection")
    assert(forSection.contains("condition: AST_Node | null;"), s"AST_For should have condition field")
    assert(forSection.contains("step: AST_Node | null;"), s"AST_For should have step field")

  test("AST_Call args field is typed as AST_Node[]"):
    val rast        = loadRast("/rast/terser/lib/ast.rast.json")
    val hierarchy   = balticporter.corpus.terser.TerserEmitter.extractHierarchy(rast)
    val dts         = balticporter.corpus.terser.AstDtsGenerator.generate(hierarchy)
    val callSection = extractClassSection(dts, "AST_Call")
    assert(callSection.contains("args: AST_Node[];"), s"AST_Call should have args as AST_Node[], got: $callSection")

  test("AST_Block body field is typed as AST_Node[]"):
    val rast         = loadRast("/rast/terser/lib/ast.rast.json")
    val hierarchy    = balticporter.corpus.terser.TerserEmitter.extractHierarchy(rast)
    val dts          = balticporter.corpus.terser.AstDtsGenerator.generate(hierarchy)
    val blockSection = extractClassSection(dts, "AST_Block")
    assert(blockSection.contains("body: AST_Node[];"), s"AST_Block should have body as AST_Node[], got: $blockSection")

  test("boolean fields typed correctly"):
    val rast      = loadRast("/rast/terser/lib/ast.rast.json")
    val hierarchy = balticporter.corpus.terser.TerserEmitter.extractHierarchy(rast)
    val dts       = balticporter.corpus.terser.AstDtsGenerator.generate(hierarchy)
    // AST_Lambda has is_generator, async props
    val lambdaSection = extractClassSection(dts, "AST_Lambda")
    assert(lambdaSection.contains("async: boolean;"), s"AST_Lambda should have async: boolean, got: $lambdaSection")
    assert(lambdaSection.contains("is_generator: boolean;"), s"AST_Lambda should have is_generator: boolean, got: $lambdaSection")

  test("scope-related fields typed as AST_Scope | null"):
    val rast         = loadRast("/rast/terser/lib/ast.rast.json")
    val hierarchy    = balticporter.corpus.terser.TerserEmitter.extractHierarchy(rast)
    val dts          = balticporter.corpus.terser.AstDtsGenerator.generate(hierarchy)
    val scopeSection = extractClassSection(dts, "AST_Scope")
    assert(
      scopeSection.contains("parent_scope: AST_Scope | null;"),
      s"AST_Scope should have parent_scope: AST_Scope | null, got: $scopeSection"
    )

  test("symbol name is string"):
    val rast          = loadRast("/rast/terser/lib/ast.rast.json")
    val hierarchy     = balticporter.corpus.terser.TerserEmitter.extractHierarchy(rast)
    val dts           = balticporter.corpus.terser.AstDtsGenerator.generate(hierarchy)
    val symbolSection = extractClassSection(dts, "AST_Symbol")
    assert(symbolSection.contains("name: string;"), s"AST_Symbol should have name: string, got: $symbolSection")

  test("generate includes SymbolDef forward declaration"):
    val rast      = loadRast("/rast/terser/lib/ast.rast.json")
    val hierarchy = balticporter.corpus.terser.TerserEmitter.extractHierarchy(rast)
    val dts       = balticporter.corpus.terser.AstDtsGenerator.generate(hierarchy)
    assert(dts.contains("export declare class SymbolDef"), "Should have SymbolDef declaration")
    assert(dts.contains("name: string;"), "SymbolDef should have name: string")

  test("countTypedFields counts all selfProps"):
    val rast      = loadRast("/rast/terser/lib/ast.rast.json")
    val hierarchy = balticporter.corpus.terser.TerserEmitter.extractHierarchy(rast)
    val count     = balticporter.corpus.terser.AstDtsGenerator.countTypedFields(hierarchy)
    assert(count >= 100, s"Expected >= 100 typed fields, got $count")

  // --------------------------------------------------------------------------
  // DEFMETHOD augmentation
  // --------------------------------------------------------------------------

  test("generate with DEFMETHOD declarations"):
    val rast      = loadRast("/rast/terser/lib/ast.rast.json")
    val hierarchy = balticporter.corpus.terser.TerserEmitter.extractHierarchy(rast)
    val dts       = balticporter.corpus.terser.AstDtsGenerator.generate(
      hierarchy,
      balticporter.corpus.terser.AstDtsGenerator.commonDefmethodDecls
    )
    assert(dts.contains("export interface AST_Node"), "Should have AST_Node interface augmentation")
    assert(dts.contains("equivalent_to(node: AST_Node): boolean;"), "Should declare equivalent_to")
    assert(dts.contains("figure_out_scope(options: any): void;"), "Should declare figure_out_scope")
    assert(dts.contains("_size(info: any): number;"), "Should declare _size")

  test("generate with DEFMETHOD for AST_Scope"):
    val rast      = loadRast("/rast/terser/lib/ast.rast.json")
    val hierarchy = balticporter.corpus.terser.TerserEmitter.extractHierarchy(rast)
    val dts       = balticporter.corpus.terser.AstDtsGenerator.generate(
      hierarchy,
      balticporter.corpus.terser.AstDtsGenerator.commonDefmethodDecls
    )
    assert(dts.contains("export interface AST_Scope"), "Should have AST_Scope interface augmentation")
    assert(dts.contains("def_variable(symbol: AST_Symbol, init: AST_Node | null): SymbolDef;"), "Should declare def_variable")

  // --------------------------------------------------------------------------
  // Scala type to TS type mapping
  // --------------------------------------------------------------------------

  test("scalaTypeToTs maps basic types"):
    import balticporter.corpus.terser.AstDtsGenerator.scalaTypeToTs
    assertEquals(scalaTypeToTs("Boolean"), "boolean")
    assertEquals(scalaTypeToTs("Int"), "number")
    assertEquals(scalaTypeToTs("String"), "string")
    assertEquals(scalaTypeToTs("Any"), "any")

  test("scalaTypeToTs maps nullable types"):
    import balticporter.corpus.terser.AstDtsGenerator.scalaTypeToTs
    assertEquals(scalaTypeToTs("AstNode | Null"), "AST_Node | null")
    assertEquals(scalaTypeToTs("AstScope | Null"), "AST_Scope | null")

  test("scalaTypeToTs maps array types"):
    import balticporter.corpus.terser.AstDtsGenerator.scalaTypeToTs
    assertEquals(scalaTypeToTs("ArrayBuffer[AstNode]"), "AST_Node[]")

  test("scalaTypeToTs maps union types"):
    import balticporter.corpus.terser.AstDtsGenerator.scalaTypeToTs
    assertEquals(scalaTypeToTs("String | AstNode"), "string | AST_Node")

  test("scalaTypeToTs maps collection types"):
    import balticporter.corpus.terser.AstDtsGenerator.scalaTypeToTs
    assertEquals(scalaTypeToTs("mutable.Map[String, Any]"), "Map<string, any>")
    assertEquals(scalaTypeToTs("mutable.Set[String]"), "Set<string>")

  // --------------------------------------------------------------------------
  // Reference port field extraction
  // --------------------------------------------------------------------------

  test("parseFieldsFromScala extracts fields from class bodies"):
    val source = """
                   |class AstCall extends AstNode {
                   |  var expression:  AstNode | Null       = null
                   |  var args:        ArrayBuffer[AstNode] = ArrayBuffer.empty
                   |  var optional:    Boolean              = false
                   |  var annotations: Int                  = 0
                   |}
      """.stripMargin
    val fields = balticporter.corpus.terser.AstDtsGenerator.parseFieldsFromScala(source)
    assertEquals(fields.size, 4)
    assertEquals(fields.head.className, "AstCall")
    assertEquals(fields.head.fieldName, "expression")
    assertEquals(fields.head.scalaType, "AstNode | Null")

  test("parseFieldsFromScala handles traits"):
    val source = """
                   |trait AstStatement extends AstNode
                   |
                   |trait AstBlock extends AstStatement {
                   |  var body:       ArrayBuffer[AstNode] = ArrayBuffer.empty
                   |  var blockScope: AstScope | Null      = null
                   |}
      """.stripMargin
    val fields = balticporter.corpus.terser.AstDtsGenerator.parseFieldsFromScala(source)
    assertEquals(fields.size, 2)
    assertEquals(fields(0).className, "AstBlock")
    assertEquals(fields(0).fieldName, "body")

  test("scalaNameToDefnode converts Ast prefix back to AST_"):
    import balticporter.corpus.terser.AstDtsGenerator.scalaNameToDefnode
    assertEquals(scalaNameToDefnode("AstCall"), "AST_Call")
    assertEquals(scalaNameToDefnode("AstSimpleStatement"), "AST_SimpleStatement")
    assertEquals(scalaNameToDefnode("AstNode"), "AST_Node")

  test("buildFieldTypeMap groups by JS class name"):
    val fields = List(
      balticporter.corpus.terser.AstDtsGenerator.ParsedField("AstCall", "expression", "AstNode | Null"),
      balticporter.corpus.terser.AstDtsGenerator.ParsedField("AstCall", "args", "ArrayBuffer[AstNode]"),
      balticporter.corpus.terser.AstDtsGenerator.ParsedField("AstBlock", "body", "ArrayBuffer[AstNode]")
    )
    val map = balticporter.corpus.terser.AstDtsGenerator.buildFieldTypeMap(fields)
    assertEquals(map.size, 2)
    assert(map.contains("AST_Call"), "Should have AST_Call key")
    assert(map.contains("AST_Block"), "Should have AST_Block key")
    assertEquals(map("AST_Call").size, 2)
    // expression -> AST_Node | null
    assertEquals(map("AST_Call")(0).tsType, "AST_Node | null")
    // args -> AST_Node[]
    assertEquals(map("AST_Call")(1).tsType, "AST_Node[]")

  // --------------------------------------------------------------------------
  // End-to-end: full hierarchy generates reasonable output
  // --------------------------------------------------------------------------

  test("full hierarchy .d.ts has reasonable size"):
    val rast      = loadRast("/rast/terser/lib/ast.rast.json")
    val hierarchy = balticporter.corpus.terser.TerserEmitter.extractHierarchy(rast)
    val dts       = balticporter.corpus.terser.AstDtsGenerator.generate(hierarchy, balticporter.corpus.terser.AstDtsGenerator.commonDefmethodDecls)
    val lines     = dts.linesIterator.size
    assert(lines >= 200, s"Expected >= 200 lines, got $lines")
    assert(lines <= 2000, s"Expected <= 2000 lines, got $lines")
    // Print first 50 lines for diagnostics
    println("=== .d.ts (first 50 lines) ===")
    dts.linesIterator.take(50).foreach(println)
    println(
      s"=== total: $lines lines, ${balticporter.corpus.terser.AstDtsGenerator.countTypedFields(hierarchy)} typed fields ==="
    )

  // --------------------------------------------------------------------------
  // Reference-derived type improvement
  // --------------------------------------------------------------------------

  private val astReferenceRoot = {
    val cpRef = getClass.getResource("/reference/terser/ast/AstNode.scala")
    if cpRef != null && cpRef.getProtocol == "file" then java.nio.file.Path.of(cpRef.toURI).getParent
    else java.nio.file.Path.of("/nonexistent")
  }

  private val astReferenceFiles = List(
    "AstNode.scala",
    "AstClasses.scala",
    "AstExpressions.scala",
    "AstStatements.scala",
    "AstDefinitions.scala",
    "AstScope.scala",
    "AstSymbols.scala"
  )

  private def loadReferenceSources: List[String] =
    astReferenceFiles.flatMap { name =>
      val p = astReferenceRoot.resolve(name)
      if java.nio.file.Files.exists(p) then Some(new String(java.nio.file.Files.readAllBytes(p)))
      else None
    }

  test("parseFieldsFromScala extracts val declarations too"):
    val source = """
                   |class AstFoo extends AstNode {
                   |  var mutableField: String = ""
                   |  val readOnlyField: Int = 0
                   |}
      """.stripMargin
    val fields = balticporter.corpus.terser.AstDtsGenerator.parseFieldsFromScala(source)
    assertEquals(fields.size, 2)
    assertEquals(fields(0).fieldName, "mutableField")
    assertEquals(fields(1).fieldName, "readOnlyField")

  test("generateFromReference uses reference types when available"):
    if !java.nio.file.Files.exists(astReferenceRoot) then println("SKIP: ssg reference not found at " + astReferenceRoot)
    else
      val rast      = loadRast("/rast/terser/lib/ast.rast.json")
      val hierarchy = balticporter.corpus.terser.TerserEmitter.extractHierarchy(rast)
      val sources   = loadReferenceSources
      assert(sources.nonEmpty, "Should find reference sources")

      // Parse fields from reference
      val allFields = sources.flatMap(balticporter.corpus.terser.AstDtsGenerator.parseFieldsFromScala)
      val fieldMap  = balticporter.corpus.terser.AstDtsGenerator.buildFieldTypeMap(allFields)

      println(s"\nReference field coverage:")
      println(s"  Total parsed fields: ${allFields.size}")
      println(s"  Distinct JS classes with fields: ${fieldMap.size}")

      // Generate with and without reference
      val dtsHeuristic = balticporter.corpus.terser.AstDtsGenerator.generate(hierarchy, balticporter.corpus.terser.AstDtsGenerator.commonDefmethodDecls)
      val dtsReference = balticporter.corpus.terser.AstDtsGenerator.generateFromReference(hierarchy, sources)

      // The reference version should differ from heuristic (more specific types)
      val metrics = balticporter.corpus.terser.AstDtsGenerator.countDerivedVsHeuristic(hierarchy, fieldMap)
      println(s"\nDerivation metrics:")
      println(s"  Total DEFNODE fields: ${metrics.totalFields}")
      println(s"  Reference-derived:    ${metrics.referenceDerived}")
      println(s"  Heuristic fallback:   ${metrics.heuristicFallback}")
      val pct = if metrics.totalFields > 0 then metrics.referenceDerived * 100.0 / metrics.totalFields else 0.0
      println(f"  Coverage:             $pct%.1f%%")

      // Reference-derived count should be meaningfully higher than 0
      assert(metrics.referenceDerived > 0, s"Expected some reference-derived fields, got ${metrics.referenceDerived}")

      // Write both versions for manual comparison
      val outDir = java.nio.file.Path.of(sys.props.getOrElse("user.dir", ".")).resolve("target/dts-comparison")
      java.nio.file.Files.createDirectories(outDir)
      java.nio.file.Files.writeString(outDir.resolve("ast-heuristic.d.ts"), dtsHeuristic)
      java.nio.file.Files.writeString(outDir.resolve("ast-reference.d.ts"), dtsReference)
      println(s"\nOutput: $outDir/ast-{heuristic,reference}.d.ts")

  test("countDerivedVsHeuristic returns correct counts"):
    val hierarchy = List(
      balticporter.corpus.terser.TerserEmitter.DefnodeClass("AST_Foo", "Foo", List("bar", "baz"), Some("AST_Node"), Nil)
    )
    val refFields = Map(
      "AST_Foo" -> List(balticporter.corpus.terser.AstDtsGenerator.DerivedField("bar", "string"))
    )
    val metrics = balticporter.corpus.terser.AstDtsGenerator.countDerivedVsHeuristic(hierarchy, refFields)
    assertEquals(metrics.totalFields, 2)
    assertEquals(metrics.referenceDerived, 1)
    assertEquals(metrics.heuristicFallback, 1)

  // --------------------------------------------------------------------------
  // Helpers
  // --------------------------------------------------------------------------

  /** Extract the section of a .d.ts for a given class name. */
  private def extractClassSection(dts: String, className: String): String =
    val start = dts.indexOf(s"declare class $className")
    if start < 0 then s"<$className not found>"
    else
      val end = dts.indexOf("\n}\n", start)
      if end < 0 then dts.substring(start)
      else dts.substring(start, end + 2)
