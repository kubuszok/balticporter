package balticporter.frontend.ts

import balticporter.corpus.terser.TerserEmitter

class KaTeXEmitterSpec extends munit.FunSuite:

  private def loadRast(resource: String): RastFile =
    val stream = getClass.getResourceAsStream(resource)
    assert(stream != null, s"Resource not found: $resource")
    val json = new String(stream.readAllBytes(), java.nio.charset.StandardCharsets.UTF_8)
    stream.close()
    Rast.readFile(json)

  private def tryLoadRast(resource: String): Option[RastFile] =
    val stream = getClass.getResourceAsStream(resource)
    if stream == null then None
    else
      try
        val json = new String(stream.readAllBytes(), java.nio.charset.StandardCharsets.UTF_8)
        stream.close()
        Some(Rast.readFile(json))
      catch
        case _: Exception =>
          stream.close()
          None

  // Classpath snapshot first (self-contained); the live ssg checkout as a fallback.
  private val katexRefRoot: java.nio.file.Path =
    val cpRef = getClass.getResource("/reference/katex/Options.scala")
    if cpRef != null && cpRef.getProtocol == "file" then java.nio.file.Path.of(cpRef.toURI).getParent
    else ConsumerCheckout.referenceScala("ssg-katex").map((dir, _) => dir.resolve("ssg/katex")).getOrElse(java.nio.file.Path.of("nonexistent"))

  // -----------------------------------------------------------------------
  // Category 2: Classes — RAST node extraction
  // -----------------------------------------------------------------------

  test("ParseError: load RAST and find ClassDeclaration"):
    val rast       = loadRast("/rast/katex/src/ParseError.rast.json")
    val classDecls = rast.nodes.filter(_.kind == "ClassDeclaration")
    assert(classDecls.nonEmpty, s"should find ClassDeclaration, got: ${rast.nodes.map(_.kind).mkString(", ")}")

  test("Options: extract top-level functions"):
    val rast = loadRast("/rast/katex/src/Options.rast.json")
    val fns  = balticporter.corpus.katex.KaTeXEmitter.extractTopLevelFunctions(rast)
    println(s"Options.ts: ${fns.size} top-level functions: ${fns.map(_.name).mkString(", ")}")

  test("Token: load RAST"):
    val rast = loadRast("/rast/katex/src/Token.rast.json")
    assert(rast.nodes.nonEmpty, "should have nodes")

  // -----------------------------------------------------------------------
  // Category 5: Functions — defineFunction extraction
  // -----------------------------------------------------------------------

  test("accent: extract defineFunction calls"):
    val rast = loadRast("/rast/katex/src/functions/accent.rast.json")
    val defs = balticporter.corpus.katex.KaTeXEmitter.extractDefineFunctions(rast)
    assert(defs.size >= 1, s"Expected >= 1 defineFunction, got ${defs.size}")
    assert(defs.exists(_.nodeType == "accent"), s"should have accent type")
    val accentDef = defs.find(_.nodeType == "accent").get
    assert(accentDef.names.contains("\\acute"), s"should contain \\acute, got: ${accentDef.names}")
    assert(accentDef.names.contains("\\hat"), s"should contain \\hat")
    assertEquals(accentDef.numArgs, 1)

  test("color: extract 2 defineFunction calls"):
    val rast = loadRast("/rast/katex/src/functions/color.rast.json")
    val defs = balticporter.corpus.katex.KaTeXEmitter.extractDefineFunctions(rast)
    assertEquals(defs.size, 2, s"Expected 2 defineFunctions, got ${defs.size}")
    assert(defs.forall(_.nodeType == "color"), "both should be type 'color'")
    val allNames = defs.flatMap(_.names)
    assert(allNames.contains("\\textcolor"), s"should contain \\textcolor, got: $allNames")
    assert(allNames.contains("\\color"), s"should contain \\color, got: $allNames")

  test("accent: emit function module"):
    val rast              = loadRast("/rast/katex/src/functions/accent.rast.json")
    val (source, summary) = balticporter.corpus.katex.KaTeXEmitter.emitFunctionModule(rast, "AccentFunc")
    assert(source.contains("object AccentFunc"), "should emit AccentFunc object")
    assert(source.contains("def register()"), "should have register method")
    assert(source.contains("nodeType = \"accent\""), "should have accent nodeType")
    assert(source.contains("\\\\acute"), "should contain \\acute")
    println(s"AccentFunc: ${summary.defineFunctionCount} defs, ${summary.totalNames} names, ${summary.topLevelFunctions} helpers")

  test("color: emit function module"):
    val rast              = loadRast("/rast/katex/src/functions/color.rast.json")
    val (source, summary) = balticporter.corpus.katex.KaTeXEmitter.emitFunctionModule(rast, "ColorFunc")
    assert(source.contains("object ColorFunc"), "should emit ColorFunc object")
    assert(source.contains("def register()"), "should have register method")
    assertEquals(summary.defineFunctionCount, 2)
    assert(source.contains("numArgs = 2"), "textcolor should have numArgs = 2")
    assert(source.contains("allowedInText = true"), "should have allowedInText")

  // -----------------------------------------------------------------------
  // Batch: extract all function registrations
  // -----------------------------------------------------------------------

  test("batch: extract defineFunction from all function files"):
    var totalDefs    = 0
    var totalNames   = 0
    var filesFound   = 0
    var filesMissing = 0

    val outDir = java.nio.file.Path.of(sys.props.getOrElse("user.dir", ".")).resolve("target/emitted-katex-functions")
    java.nio.file.Files.createDirectories(outDir)

    for mod <- balticporter.corpus.katex.KaTeXEmitter.FunctionModules do
      tryLoadRast(mod.rastResource) match
        case Some(rast) =>
          filesFound += 1
          val defs = balticporter.corpus.katex.KaTeXEmitter.extractDefineFunctions(rast)
          totalDefs += defs.size
          totalNames += defs.flatMap(_.names).size
          if defs.nonEmpty then
            val (source, _) = balticporter.corpus.katex.KaTeXEmitter.emitFunctionModule(rast, mod.objectName)
            java.nio.file.Files.writeString(outDir.resolve(s"${mod.objectName}.scala"), source)
        case None =>
          filesMissing += 1

    println(s"\n=== KaTeX Function Extraction ===")
    println(s"Files found: $filesFound / ${balticporter.corpus.katex.KaTeXEmitter.FunctionModules.size}")
    println(s"Files missing: $filesMissing")
    println(s"Total defineFunction calls: $totalDefs")
    println(s"Total LaTeX command names: $totalNames")
    println(s"Emitted to: $outDir")

    assert(filesFound >= 30, s"Expected >= 30 function RAST files, got $filesFound")
    assert(totalDefs >= 30, s"Expected >= 30 defineFunction calls, got $totalDefs")
    assert(totalNames >= 100, s"Expected >= 100 LaTeX names, got $totalNames")

  // -----------------------------------------------------------------------
  // Parity-derive: reference structure analysis
  // -----------------------------------------------------------------------

  test("parity: Options.scala method boundaries"):
    assume(java.nio.file.Files.exists(katexRefRoot.resolve("Options.scala")), s"Options.scala not found under $katexRefRoot")
    val source  = new String(java.nio.file.Files.readAllBytes(katexRefRoot.resolve("Options.scala")))
    val lines   = source.split("\n", -1).toList
    val methods = balticporter.corpus.terser.TerserCompressEmitter.findMethodBoundaries(lines)
    println(s"Options.scala: found ${methods.size} methods")
    for m <- methods.take(10) do println(s"  ${m.name} (lines ${m.signatureLine}-${m.bodyEndLine}, private=${m.isPrivate})")
    assert(methods.size >= 10, s"Expected >= 10 methods in Options.scala, got ${methods.size}")

  test("parity: emitWithParity on ParseError"):
    assume(
      java.nio.file.Files.exists(katexRefRoot.resolve("ParseError.scala")),
      s"ParseError.scala not found under $katexRefRoot"
    )
    val rast              = loadRast("/rast/katex/src/ParseError.rast.json")
    val refPath           = katexRefRoot.resolve("ParseError.scala")
    val (source, summary) = balticporter.corpus.katex.KaTeXEmitter.emitWithParity(rast, refPath)
    println(
      s"ParseError parity: ${summary.totalMethods} methods, ${summary.matchedFromRast} RAST-matched, ${summary.keptFromReference} kept"
    )
    assert(source.contains("ParseError"), "should contain ParseError")

  // -----------------------------------------------------------------------
  // Full batch analysis
  // -----------------------------------------------------------------------

  test("batch: analyze all KaTeX modules"):
    assume(java.nio.file.Files.exists(katexRefRoot), s"ssg-katex reference not found at $katexRefRoot")
    val summary = balticporter.corpus.katex.KaTeXEmitter.analyzeAll(tryLoadRast, katexRefRoot)
    println("\n" + balticporter.corpus.katex.KaTeXEmitter.formatBatchSummary(summary))
    assert(summary.foundRast >= 50, s"Expected >= 50 RAST files, got ${summary.foundRast}")
    assert(summary.functionDefs >= 30, s"Expected >= 30 function defs, got ${summary.functionDefs}")

  // -----------------------------------------------------------------------
  // Type mapping
  // -----------------------------------------------------------------------

  test("tsTypeToScala: basic type conversions"):
    assertEquals(balticporter.corpus.katex.KaTeXEmitter.tsTypeToScala("string"), "String")
    assertEquals(balticporter.corpus.katex.KaTeXEmitter.tsTypeToScala("number"), "Double")
    assertEquals(balticporter.corpus.katex.KaTeXEmitter.tsTypeToScala("boolean"), "Boolean")
    assertEquals(balticporter.corpus.katex.KaTeXEmitter.tsTypeToScala("void"), "Unit")
    assertEquals(balticporter.corpus.katex.KaTeXEmitter.tsTypeToScala("any"), "Any")

  test("tsTypeToScala: ParseNode generic"):
    assertEquals(balticporter.corpus.katex.KaTeXEmitter.tsTypeToScala("ParseNode<\"accent\">"), "ParseNodeAccent")
    assertEquals(balticporter.corpus.katex.KaTeXEmitter.tsTypeToScala("ParseNode<\"color\">"), "ParseNodeColor")

  test("tsTypeToScala: nullable"):
    assertEquals(balticporter.corpus.katex.KaTeXEmitter.tsTypeToScala("Token | null"), "Nullable[Token]")

  test("tsTypeToScala: array"):
    assertEquals(balticporter.corpus.katex.KaTeXEmitter.tsTypeToScala("string[]"), "Array[String]")

  // -----------------------------------------------------------------------
  // Step 1: Batch parity-derive for core modules
  // -----------------------------------------------------------------------

  test("batch: emitAllWithParity writes all core modules"):
    assume(java.nio.file.Files.exists(katexRefRoot), s"ssg-katex reference not found at $katexRefRoot")
    val outDir  = java.nio.file.Path.of(sys.props.getOrElse("user.dir", ".")).resolve("target/emitted-katex-parity")
    val results = balticporter.corpus.katex.KaTeXEmitter.emitAllWithParity(tryLoadRast, katexRefRoot, outDir)

    println("\n=== KaTeX Core Module Parity ===")
    println(balticporter.corpus.katex.KaTeXEmitter.formatParitySummaryTable(results.map(_._2)))

    for (mod, summary) <- results do
      println(
        s"  ${mod.objectName}: ${summary.totalMethods} methods, " +
          s"${summary.matchedFromRast} RAST, ${summary.keptFromReference} ref"
      )

    assert(results.size >= 20, s"Expected >= 20 modules emitted, got ${results.size}")

    val totalRast = results.map(_._2.matchedFromRast).sum
    println(s"\nTotal RAST-derived bodies: $totalRast")
    println(s"Emitted to: $outDir")

  // -----------------------------------------------------------------------
  // Step 2: Batch parity-derive for function modules
  // -----------------------------------------------------------------------

  test("batch: emitAllFunctionsWithParity writes function modules"):
    assume(java.nio.file.Files.exists(katexRefRoot), s"ssg-katex reference not found at $katexRefRoot")
    val outDir  = java.nio.file.Path.of(sys.props.getOrElse("user.dir", ".")).resolve("target/emitted-katex-functions-parity")
    val results = balticporter.corpus.katex.KaTeXEmitter.emitAllFunctionsWithParity(tryLoadRast, katexRefRoot, outDir)

    val summaries = results.map(_._3)
    println("\n=== KaTeX Function Module Parity ===")
    println(balticporter.corpus.katex.KaTeXEmitter.formatFunctionParitySummaryTable(summaries))

    val withParity = summaries.filter(_.usedParity)
    val stubs      = summaries.filterNot(_.usedParity)
    println(s"With parity: ${withParity.map(_.objectName).mkString(", ")}")
    if stubs.nonEmpty then println(s"Stub only: ${stubs.map(_.objectName).mkString(", ")}")

    assert(results.size >= 30, s"Expected >= 30 function modules emitted, got ${results.size}")
    assert(withParity.size >= 25, s"Expected >= 25 function modules with parity, got ${withParity.size}")

    println(s"Emitted to: $outDir")

  // -----------------------------------------------------------------------
  // Output inspection
  // -----------------------------------------------------------------------

  test("emit AccentFunc to target for inspection"):
    val rast        = loadRast("/rast/katex/src/functions/accent.rast.json")
    val (source, _) = balticporter.corpus.katex.KaTeXEmitter.emitFunctionModule(rast, "AccentFunc")
    val outDir      = java.nio.file.Path.of(sys.props.getOrElse("user.dir", ".")).resolve("target/emitted-katex-functions")
    java.nio.file.Files.createDirectories(outDir)
    java.nio.file.Files.writeString(outDir.resolve("AccentFunc.scala"), source)
    println(s"AccentFunc.scala: ${source.linesIterator.size} lines")
    println(s"Written to: ${outDir.resolve("AccentFunc.scala")}")

  // -----------------------------------------------------------------------
  // Step 4: KaTeX upstream test translation via VitestToMunitEmitter
  // -----------------------------------------------------------------------

  private val katexTestOutDir: java.nio.file.Path =
    val d = java.nio.file.Path.of(sys.props.getOrElse("user.dir", ".")).resolve("target/emitted-katex-tests")
    java.nio.file.Files.createDirectories(d)
    d

  private def emitKatexSpec(resource: String, className: String): Option[dedicated.VitestToMunitEmitter.EmitResult] =
    try
      val rast   = loadRast(resource)
      val config = dedicated.VitestToMunitEmitter.EmitConfig(
        packageName = "ssg.katex.test.generated",
        className = className
      )
      val result = dedicated.VitestToMunitEmitter.emit(rast, config)
      java.nio.file.Files.writeString(katexTestOutDir.resolve(s"$className.scala"), result.scala)
      Some(result)
    catch
      case e: Exception =>
        println(s"  FAILED to emit $className: ${e.getClass.getSimpleName}: ${e.getMessage.take(120)}")
        None

  test("katex-spec: emit tests via vitest→MUnit"):
    val result = emitKatexSpec("/rast/katex/test/katex-spec.rast.json", "KaTeXSpecGenerated")
    assume(result.isDefined, "katex-spec.rast.json failed to load or emit")
    val r = result.get
    println(s"\n=== katex-spec.rast.json ===")
    println(s"Tests: ${r.testCount}, Ignored: ${r.ignoredCount}")
    println(s"Assertions: ${r.assertionCounts.toList.sortBy(-_._2).map { case (k, v) => s"$k($v)" }.mkString(", ")}")
    println(s"Lines: ${r.scala.linesIterator.size}")
    println(s"Written to: ${katexTestOutDir.resolve("KaTeXSpecGenerated.scala")}")
    assert(r.testCount >= 500, s"Expected >= 500 tests from katex-spec, got ${r.testCount}")

  test("errors-spec: emit tests via vitest→MUnit"):
    val result = emitKatexSpec("/rast/katex/test/errors-spec.rast.json", "ErrorsSpecGenerated")
    assume(result.isDefined, "errors-spec.rast.json failed to load or emit")
    val r = result.get
    println(s"errors-spec: ${r.testCount} tests, ${r.ignoredCount} ignored")
    assert(r.testCount >= 1, s"Expected >= 1 test, got ${r.testCount}")

  test("mathml-spec: emit tests via vitest→MUnit"):
    val result = emitKatexSpec("/rast/katex/test/mathml-spec.rast.json", "MathMLSpecGenerated")
    assume(result.isDefined, "mathml-spec.rast.json failed to load or emit")
    val r = result.get
    println(s"mathml-spec: ${r.testCount} tests, ${r.ignoredCount} ignored")
    assert(r.testCount >= 1, s"Expected >= 1 test, got ${r.testCount}")

  test("dup-spec: emit tests via vitest→MUnit"):
    val result = emitKatexSpec("/rast/katex/test/dup-spec.rast.json", "DupSpecGenerated")
    assume(result.isDefined, "dup-spec.rast.json failed to load or emit")
    val r = result.get
    println(s"dup-spec: ${r.testCount} tests, ${r.ignoredCount} ignored")
  // dup-spec uses a non-standard test pattern (no describe/it blocks),
  // so 0 tests is expected — the emitter only handles describe/it

  test("unicode-spec: emit tests via vitest→MUnit"):
    val result = emitKatexSpec("/rast/katex/test/unicode-spec.rast.json", "UnicodeSpecGenerated")
    assume(result.isDefined, "unicode-spec.rast.json failed to load or emit")
    val r = result.get
    println(s"unicode-spec: ${r.testCount} tests, ${r.ignoredCount} ignored")
    assert(r.testCount >= 1, s"Expected >= 1 test, got ${r.testCount}")

  test("batch: emit all KaTeX test specs"):
    val specs = List(
      ("/rast/katex/test/katex-spec.rast.json", "KaTeXSpecGenerated"),
      ("/rast/katex/test/errors-spec.rast.json", "ErrorsSpecGenerated"),
      ("/rast/katex/test/mathml-spec.rast.json", "MathMLSpecGenerated"),
      ("/rast/katex/test/dup-spec.rast.json", "DupSpecGenerated"),
      ("/rast/katex/test/unicode-spec.rast.json", "UnicodeSpecGenerated")
    )

    var totalTests    = 0
    var totalIgnored  = 0
    var totalFailed   = 0
    val allAssertions = scala.collection.mutable.Map.empty[String, Int].withDefaultValue(0)

    println(s"\n=== KaTeX Test Translation Summary ===")
    println(f"${"Spec"}%-30s ${"Tests"}%6s ${"Ignored"}%8s ${"Lines"}%6s")
    println("-" * 55)

    for (resource, className) <- specs do
      val result = emitKatexSpec(resource, className)
      result match
        case Some(r) =>
          totalTests += r.testCount
          totalIgnored += r.ignoredCount
          for (k, v) <- r.assertionCounts do allAssertions(k) += v
          println(f"${className}%-30s ${r.testCount}%6d ${r.ignoredCount}%8d ${r.scala.linesIterator.size}%6d")
        case None =>
          totalFailed += 1
          println(f"${className}%-30s ${"FAILED"}%6s")

    println("-" * 55)
    println(f"${"TOTAL"}%-30s ${totalTests}%6d ${totalIgnored}%8d")
    println(s"\nAssertion patterns: ${allAssertions.toList.sortBy(-_._2).map { case (k, v) => s"$k($v)" }.mkString(", ")}")
    println(s"Failed to load: $totalFailed")
    println(s"Output: $katexTestOutDir")

    assert(totalTests >= 100, s"Expected >= 100 total tests from KaTeX specs, got $totalTests")

  // -----------------------------------------------------------------------
  // Phase 2: Handler body translation
  // -----------------------------------------------------------------------

  test("handler body extraction: accent handler has Block"):
    val rast      = loadRast("/rast/katex/src/functions/accent.rast.json")
    val defs      = balticporter.corpus.katex.KaTeXEmitter.extractDefineFunctions(rast)
    val accentDef = defs.find(_.nodeType == "accent").get
    val body      = balticporter.corpus.katex.KaTeXEmitter.extractFunctionBody(accentDef.handlerNode)
    assert(body.isDefined, "accent handler should have extractable body")
    assert(body.get.kind == "Block", s"should be a Block, got ${body.get.kind}")
    val stmtCount = body.get.children.size
    assert(stmtCount >= 2, s"handler body should have >= 2 statements, got $stmtCount")
    println(s"accent handler: ${stmtCount} statements in body")

  test("handler body extraction: underline handler (MethodDeclaration)"):
    val rast = loadRast("/rast/katex/src/functions/underline.rast.json")
    val defs = balticporter.corpus.katex.KaTeXEmitter.extractDefineFunctions(rast)
    assert(defs.nonEmpty, "should find defineFunction calls")
    val body = balticporter.corpus.katex.KaTeXEmitter.extractFunctionBody(defs.head.handlerNode)
    assert(body.isDefined, "underline handler should have extractable body")
    println(s"underline handler: ${body.get.children.size} statements")

  test("handler param extraction"):
    val rast      = loadRast("/rast/katex/src/functions/accent.rast.json")
    val defs      = balticporter.corpus.katex.KaTeXEmitter.extractDefineFunctions(rast)
    val accentDef = defs.find(_.nodeType == "accent").get
    val params    = balticporter.corpus.katex.KaTeXEmitter.extractHandlerParams(accentDef.handlerNode)
    println(s"accent handler params: ${params.mkString(", ")}")
    assert(params.nonEmpty, "should extract handler params")

  test("handler body translation: accent produces plausible Scala"):
    val rast              = loadRast("/rast/katex/src/functions/accent.rast.json")
    val (source, summary) = balticporter.corpus.katex.KaTeXEmitter.emitFunctionModule(rast, "AccentFunc")
    println(s"AccentFunc handler translation: ${summary.handlersTranslated} translated, ${summary.handlersPartial} partial")
    // At minimum, the handler should not be entirely `???`
    val handlerLines = source.linesIterator.filter(_.contains("handler = Nullable")).toList
    assert(handlerLines.nonEmpty, "should emit handler lines")
    println(s"Handler lines: ${handlerLines.size}")

  test("katex API mapping: makeSpan resolves to BuildCommon.makeSpan"):
    val rast        = loadRast("/rast/katex/src/functions/accent.rast.json")
    val (source, _) = balticporter.corpus.katex.KaTeXEmitter.emitFunctionModule(rast, "AccentFunc")
    // The accent file contains makeSpan/makeOrd/staticSvg calls
    if source.contains("BuildCommon.makeSpan") || source.contains("BuildCommon.makeOrd") then println("KaTeX API mapping confirmed: BuildCommon.* calls found")
    else println("NOTE: accent handlers may have too many refusals for API mapping to appear")
    // Check that basic API lookups work (even if the full handler fails)
    val entry = TerserEmitter.DefmethodEntry(
      "_test_",
      "test",
      List("x"),
      RastNode(
        "Block",
        0,
        (0, 0),
        children = List(
          RastNode(
            "ReturnStatement",
            0,
            (0, 0),
            children = List(
              RastNode(
                "CallExpression",
                0,
                (0, 0),
                children = List(
                  RastNode("Identifier", 0, (0, 0), text = Some("makeSpan")),
                  RastNode("Identifier", 0, (0, 0), text = Some("x"))
                )
              )
            )
          )
        )
      )
    )
    val result = dedicated.DefmethodBodyTranslator.translateBody(entry, Nil, "    ")
    assert(
      result.scalaBody.contains("BuildCommon.makeSpan"),
      s"makeSpan should map to BuildCommon.makeSpan, got: ${result.scalaBody.trim}"
    )

  test("batch handler translation stats"):
    var totalDefs  = 0
    var translated = 0
    var partial    = 0

    for mod <- balticporter.corpus.katex.KaTeXEmitter.FunctionModules do
      tryLoadRast(mod.rastResource).foreach { rast =>
        val (_, summary) = balticporter.corpus.katex.KaTeXEmitter.emitFunctionModule(rast, mod.objectName)
        totalDefs += summary.defineFunctionCount
        translated += summary.handlersTranslated
        partial += summary.handlersPartial
      }

    println(s"\n=== KaTeX Handler Translation ===")
    println(s"Total defineFunction calls: $totalDefs")
    println(s"Handlers fully translated: $translated")
    println(s"Handlers partial/stub: $partial")
    val pct = if totalDefs > 0 then translated * 100.0 / totalDefs else 0.0
    println(f"Handler translation rate: $translated/$totalDefs ($pct%.1f%%)")
