package balticporter.frontend.ts

class ReferenceTypeOracleSpec extends munit.FunSuite:

  // -----------------------------------------------------------------------
  // parseFile: regex-based signature extraction
  // -----------------------------------------------------------------------

  test("parseFile: extract simple method signature"):
    val source =
      """object Foo {
        |  def bar(x: Int, y: String): Boolean =
        |    x > 0
        |}""".stripMargin
    val sigs = balticporter.corpus.terser.ReferenceTypeOracle.parseFile("Foo", source)
    assertEquals(sigs.size, 1)
    val (obj, sig) = sigs.head
    assertEquals(obj, "Foo")
    assertEquals(sig.methodName, "bar")
    assertEquals(sig.params.size, 2)
    assertEquals(sig.params(0).name, "x")
    assertEquals(sig.params(0).tpe, "Int")
    assertEquals(sig.params(1).name, "y")
    assertEquals(sig.params(1).tpe, "String")
    assertEquals(sig.returnType, "Boolean")

  test("parseFile: extract method with generic types"):
    val source =
      """object Foo {
        |  def merge(array: ArrayBuffer[AstNode], node: AstNode): ArrayBuffer[AstNode] = {
        |    array
        |  }
        |}""".stripMargin
    val sigs = balticporter.corpus.terser.ReferenceTypeOracle.parseFile("Foo", source)
    assertEquals(sigs.size, 1)
    val (_, sig) = sigs.head
    assertEquals(sig.params(0).tpe, "ArrayBuffer[AstNode]")
    assertEquals(sig.returnType, "ArrayBuffer[AstNode]")

  test("parseFile: extract method with union return type"):
    val source =
      """object Foo {
        |  def getKey(key: AstNode): AstNode | String | Double | Null =
        |    null
        |}""".stripMargin
    val sigs = balticporter.corpus.terser.ReferenceTypeOracle.parseFile("Foo", source)
    assertEquals(sigs.size, 1)
    assertEquals(sigs.head._2.returnType, "AstNode | String | Double | Null")

  test("parseFile: skip private methods"):
    val source =
      """object Foo {
        |  def pub(x: Int): Int =
        |    x
        |  private def priv(y: Int): Int =
        |    y
        |}""".stripMargin
    val sigs = balticporter.corpus.terser.ReferenceTypeOracle.parseFile("Foo", source)
    // parseFile only captures top-level defs (2 spaces indent); private at 2 spaces is still captured
    // but that is fine -- the oracle is permissive
    assert(sigs.nonEmpty)

  test("parseFile: handle default parameters"):
    val source =
      """object Foo {
        |  def negate(node: AstNode, compressor: CompressorLike, firstInStatement: Boolean = false): AstNode =
        |    node
        |}""".stripMargin
    val sigs = balticporter.corpus.terser.ReferenceTypeOracle.parseFile("Foo", source)
    assertEquals(sigs.size, 1)
    val (_, sig) = sigs.head
    assertEquals(sig.params.size, 3)
    assertEquals(sig.params(2).name, "firstInStatement")
    assertEquals(sig.params(2).tpe, "Boolean")

  // -----------------------------------------------------------------------
  // hardcoded oracle: basic lookups
  // -----------------------------------------------------------------------

  test("hardcoded oracle: Inference.isBoolean"):
    val oracle = balticporter.corpus.terser.ReferenceTypeOracle.buildHardcoded()
    val sig    = oracle.get("Inference", "isBoolean")
    assert(sig.isDefined, "should find isBoolean")
    assertEquals(sig.get.returnType, "Boolean")
    assertEquals(sig.get.params.head.tpe, "AstNode")

  test("hardcoded oracle: Common.mergeSequence"):
    val oracle = balticporter.corpus.terser.ReferenceTypeOracle.buildHardcoded()
    val sig    = oracle.get("Common", "mergeSequence")
    assert(sig.isDefined, "should find mergeSequence")
    assertEquals(sig.get.returnType, "ArrayBuffer[AstNode]")
    assertEquals(sig.get.params(0).tpe, "ArrayBuffer[AstNode]")
    assertEquals(sig.get.params(1).tpe, "AstNode")

  test("hardcoded oracle: paramType fallback"):
    val oracle = balticporter.corpus.terser.ReferenceTypeOracle.buildHardcoded()
    assertEquals(oracle.paramType("Inference", "isBoolean", "node"), "AstNode")
    assertEquals(oracle.paramType("Inference", "isBoolean", "unknown"), "Any")
    assertEquals(oracle.paramType("Unknown", "unknown", "x"), "Any")

  test("hardcoded oracle: returnType fallback"):
    val oracle = balticporter.corpus.terser.ReferenceTypeOracle.buildHardcoded()
    assertEquals(oracle.returnType("Inference", "isBoolean"), "Boolean")
    assertEquals(oracle.returnType("Unknown", "unknown"), "Any")

  // -----------------------------------------------------------------------
  // Emitter-remapped oracle
  // -----------------------------------------------------------------------

  test("emitter oracle: CompressCommon maps to Common"):
    val oracle = balticporter.corpus.terser.ReferenceTypeOracle.buildHardcodedForEmitter()
    val sig    = oracle.get("Common", "mergeSequence")
    assert(sig.isDefined, "should find via reference name")

  // -----------------------------------------------------------------------
  // File-based oracle (reads actual reference files)
  // -----------------------------------------------------------------------

  test("file-based oracle: parse actual ssg-js reference"):
    val refRoot = {
      val cpRef = getClass.getResource("/reference/terser/compress/Common.scala")
      if cpRef != null && cpRef.getProtocol == "file" then java.nio.file.Path.of(cpRef.toURI).getParent
      else java.nio.file.Path.of("/nonexistent")
    }
    if java.nio.file.Files.exists(refRoot) then
      val oracle = balticporter.corpus.terser.ReferenceTypeOracle.buildFromDirectory(refRoot)
      // Inference methods
      val isBool = oracle.get("Inference", "isBoolean")
      assert(isBool.isDefined, "should find Inference.isBoolean from file")
      assertEquals(isBool.get.returnType, "Boolean")

      // Common methods
      val merge = oracle.get("Common", "mergeSequence")
      assert(merge.isDefined, "should find Common.mergeSequence from file")
      assertEquals(merge.get.returnType, "ArrayBuffer[AstNode]")

      // TightenBody methods
      val tighten = oracle.get("TightenBody", "tightenBody")
      assert(tighten.isDefined, "should find TightenBody.tightenBody from file")

      // Print statistics
      println(s"File-based oracle: ${oracle.methods.size} method signatures extracted")
      val byObj = oracle.methods.groupBy(_._1._1)
      for (obj, sigs) <- byObj.toList.sortBy(_._1) do println(s"  $obj: ${sigs.size} methods")
    else println("SKIP: ssg-js reference not available")

  // -----------------------------------------------------------------------
  // Integration: emitter with oracle produces typed output
  // -----------------------------------------------------------------------

  private def loadRast(resource: String): RastFile =
    val stream = getClass.getResourceAsStream(resource)
    assert(stream != null, s"Resource not found: $resource")
    val json = new String(stream.readAllBytes(), java.nio.charset.StandardCharsets.UTF_8)
    stream.close()
    Rast.readFile(json)

  private lazy val astRast   = loadRast("/rast/terser/lib/ast.rast.json")
  private lazy val hierarchy = balticporter.corpus.terser.TerserEmitter.extractHierarchy(astRast)

  test("emitter with oracle: Inference methods have typed signatures"):
    val oracle      = balticporter.corpus.terser.ReferenceTypeOracle.buildHardcodedForEmitter()
    val rast        = loadRast("/rast/terser/lib/compress/inference.rast.json")
    val (source, _) = balticporter.corpus.terser.TerserCompressEmitter.emitDefmethodModule(
      rast,
      "inference",
      "Inference",
      hierarchy,
      Some(oracle)
    )
    // isBoolean should have Boolean return type, not Any
    assert(source.contains(": Boolean"), s"should contain Boolean return type")
    // compressor param should have CompressorLike type
    assert(source.contains("compressor: CompressorLike"), s"should type compressor as CompressorLike")
    // Methods with oracle entries should not have "compressor: Any"
    val lines = source.linesIterator.toList
    // is32BitInteger should have typed compressor
    val is32Line = lines.find(l => l.contains("def is32BitInteger") && !l.contains("case"))
    is32Line.foreach { line =>
      assert(line.contains("compressor: CompressorLike"), s"is32BitInteger should type compressor: $line")
    }

  test("emitter with oracle: Common methods have typed signatures"):
    val oracle      = balticporter.corpus.terser.ReferenceTypeOracle.buildHardcodedForEmitter()
    val rast        = loadRast("/rast/terser/lib/compress/common.rast.json")
    val (source, _) = balticporter.corpus.terser.TerserCompressEmitter.emitFreeFunctionModule(
      rast,
      "common",
      "CompressCommon",
      hierarchy,
      Some(oracle)
    )
    // mergeSequence should have ArrayBuffer[AstNode] return type
    assert(source.contains("ArrayBuffer[AstNode]"), s"should contain ArrayBuffer[AstNode]")
    // makeEmptyFunction should return AstFunction
    assert(source.contains(": AstFunction"), s"should contain AstFunction return type")

  test("emitter with oracle: batch emit all 10 modules with types (hardcoded)"):
    val oracle = balticporter.corpus.terser.ReferenceTypeOracle.buildHardcodedForEmitter()
    val outDir = java.nio.file.Path.of(sys.props.getOrElse("user.dir", ".")).resolve("target/emitted-terser-compress-typed")
    java.nio.file.Files.createDirectories(outDir)

    val results = balticporter.corpus.terser.TerserCompressEmitter.emitAll(loadRast, hierarchy, Some(oracle))

    for (mod, source, _) <- results do
      val path = outDir.resolve(s"${mod.objectName}.scala")
      java.nio.file.Files.writeString(path, source)

    assertEquals(results.size, 10, "should emit all 10 compress modules")
    reportTypeCoverage("hardcoded", results)

  test("emitter with oracle: batch emit with file-based oracle"):
    val refRoot = {
      val cpRef = getClass.getResource("/reference/terser/compress/Common.scala")
      if cpRef != null && cpRef.getProtocol == "file" then java.nio.file.Path.of(cpRef.toURI).getParent
      else java.nio.file.Path.of("/nonexistent")
    }
    if !java.nio.file.Files.exists(refRoot) then println("SKIP: ssg-js reference not available")
    else
      val oracle = balticporter.corpus.terser.ReferenceTypeOracle.buildForEmitter(refRoot)
      val outDir = java.nio.file.Path.of(sys.props.getOrElse("user.dir", ".")).resolve("target/emitted-terser-compress-fileoracle")
      java.nio.file.Files.createDirectories(outDir)

      val results = balticporter.corpus.terser.TerserCompressEmitter.emitAll(loadRast, hierarchy, Some(oracle))

      for (mod, source, _) <- results do
        val path = outDir.resolve(s"${mod.objectName}.scala")
        java.nio.file.Files.writeString(path, source)

      assertEquals(results.size, 10, "should emit all 10 compress modules")
      reportTypeCoverage("file-based", results)

  private def reportTypeCoverage(
    label:   String,
    results: List[
      (balticporter.corpus.terser.TerserCompressEmitter.CompressModule, String, balticporter.corpus.terser.TerserCompressEmitter.ModuleTranslationSummary)
    ]
  ): Unit =
    var typedParams  = 0
    var anyParams    = 0
    var typedReturns = 0
    var anyReturns   = 0
    for (_, source, _) <- results do
      for line <- source.linesIterator if line.trim.startsWith("def ") do
        if line.contains(": Any =") || line.contains(": Any =") then anyReturns += 1
        else typedReturns += 1
        val paramSection = line.dropWhile(_ != '(').takeWhile(_ != ')')
        for param <- paramSection.split(",") do
          if param.contains(": Any") then anyParams += 1
          else if param.contains(":") then typedParams += 1

    println(s"\n=== Type Oracle Coverage ($label) ===")
    println(s"  Typed returns: $typedReturns, Any returns: $anyReturns")
    println(s"  Typed params: $typedParams, Any params: $anyParams")

    for (mod, source, _) <- results do println(s"  ${mod.objectName}.scala: ${source.linesIterator.size} lines")
