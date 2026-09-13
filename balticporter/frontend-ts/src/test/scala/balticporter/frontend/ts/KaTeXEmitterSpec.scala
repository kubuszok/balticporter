package balticporter.frontend.ts

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

  private val katexRefRoot: java.nio.file.Path =
    val candidates = List(
      sys.props.get("ssg.root").map(java.nio.file.Path.of(_)),
      Some(java.nio.file.Path.of(sys.props.getOrElse("user.dir", ".")).getParent.resolve("ssg")),
      Some(java.nio.file.Path.of("/Users/dev/Workspaces/kubuszok/ssg")),
    ).flatten
    candidates.map(_.resolve("ssg-katex/src/main/scala/ssg/katex"))
      .find(p => java.nio.file.Files.exists(p.resolve("Options.scala")))
      .getOrElse(java.nio.file.Path.of("nonexistent"))

  // -----------------------------------------------------------------------
  // Category 2: Classes — RAST node extraction
  // -----------------------------------------------------------------------

  test("ParseError: load RAST and find ClassDeclaration"):
    val rast = loadRast("/rast/katex/src/ParseError.rast.json")
    val classDecls = rast.nodes.filter(_.kind == "ClassDeclaration")
    assert(classDecls.nonEmpty, s"should find ClassDeclaration, got: ${rast.nodes.map(_.kind).mkString(", ")}")

  test("Options: extract top-level functions"):
    val rast = loadRast("/rast/katex/src/Options.rast.json")
    val fns = dedicated.KaTeXEmitter.extractTopLevelFunctions(rast)
    println(s"Options.ts: ${fns.size} top-level functions: ${fns.map(_.name).mkString(", ")}")

  test("Token: load RAST"):
    val rast = loadRast("/rast/katex/src/Token.rast.json")
    assert(rast.nodes.nonEmpty, "should have nodes")

  // -----------------------------------------------------------------------
  // Category 5: Functions — defineFunction extraction
  // -----------------------------------------------------------------------

  test("accent: extract defineFunction calls"):
    val rast = loadRast("/rast/katex/src/functions/accent.rast.json")
    val defs = dedicated.KaTeXEmitter.extractDefineFunctions(rast)
    assert(defs.size >= 1, s"Expected >= 1 defineFunction, got ${defs.size}")
    assert(defs.exists(_.nodeType == "accent"), s"should have accent type")
    val accentDef = defs.find(_.nodeType == "accent").get
    assert(accentDef.names.contains("\\acute"), s"should contain \\acute, got: ${accentDef.names}")
    assert(accentDef.names.contains("\\hat"), s"should contain \\hat")
    assertEquals(accentDef.numArgs, 1)

  test("color: extract 2 defineFunction calls"):
    val rast = loadRast("/rast/katex/src/functions/color.rast.json")
    val defs = dedicated.KaTeXEmitter.extractDefineFunctions(rast)
    assertEquals(defs.size, 2, s"Expected 2 defineFunctions, got ${defs.size}")
    assert(defs.forall(_.nodeType == "color"), "both should be type 'color'")
    val allNames = defs.flatMap(_.names)
    assert(allNames.contains("\\textcolor"), s"should contain \\textcolor, got: $allNames")
    assert(allNames.contains("\\color"), s"should contain \\color, got: $allNames")

  test("accent: emit function module"):
    val rast = loadRast("/rast/katex/src/functions/accent.rast.json")
    val (source, summary) = dedicated.KaTeXEmitter.emitFunctionModule(rast, "AccentFunc")
    assert(source.contains("object AccentFunc"), "should emit AccentFunc object")
    assert(source.contains("def register()"), "should have register method")
    assert(source.contains("nodeType = \"accent\""), "should have accent nodeType")
    assert(source.contains("\\\\acute"), "should contain \\acute")
    println(s"AccentFunc: ${summary.defineFunctionCount} defs, ${summary.totalNames} names, ${summary.topLevelFunctions} helpers")

  test("color: emit function module"):
    val rast = loadRast("/rast/katex/src/functions/color.rast.json")
    val (source, summary) = dedicated.KaTeXEmitter.emitFunctionModule(rast, "ColorFunc")
    assert(source.contains("object ColorFunc"), "should emit ColorFunc object")
    assert(source.contains("def register()"), "should have register method")
    assertEquals(summary.defineFunctionCount, 2)
    assert(source.contains("numArgs = 2"), "textcolor should have numArgs = 2")
    assert(source.contains("allowedInText = true"), "should have allowedInText")

  // -----------------------------------------------------------------------
  // Batch: extract all function registrations
  // -----------------------------------------------------------------------

  test("batch: extract defineFunction from all function files"):
    var totalDefs = 0
    var totalNames = 0
    var filesFound = 0
    var filesMissing = 0

    val outDir = java.nio.file.Path.of(sys.props.getOrElse("user.dir", ".")).resolve("target/emitted-katex-functions")
    java.nio.file.Files.createDirectories(outDir)

    for mod <- dedicated.KaTeXEmitter.FunctionModules do
      tryLoadRast(mod.rastResource) match
        case Some(rast) =>
          filesFound += 1
          val defs = dedicated.KaTeXEmitter.extractDefineFunctions(rast)
          totalDefs += defs.size
          totalNames += defs.flatMap(_.names).size
          if defs.nonEmpty then
            val (source, _) = dedicated.KaTeXEmitter.emitFunctionModule(rast, mod.objectName)
            java.nio.file.Files.writeString(outDir.resolve(s"${mod.objectName}.scala"), source)
        case None =>
          filesMissing += 1

    println(s"\n=== KaTeX Function Extraction ===")
    println(s"Files found: $filesFound / ${dedicated.KaTeXEmitter.FunctionModules.size}")
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
    if !java.nio.file.Files.exists(katexRefRoot.resolve("Options.scala")) then
      println("SKIP: ssg-katex reference not found")
    else
      val source = new String(java.nio.file.Files.readAllBytes(katexRefRoot.resolve("Options.scala")))
      val lines = source.split("\n", -1).toList
      val methods = dedicated.TerserCompressEmitter.findMethodBoundaries(lines)
      println(s"Options.scala: found ${methods.size} methods")
      for m <- methods.take(10) do
        println(s"  ${m.name} (lines ${m.signatureLine}-${m.bodyEndLine}, private=${m.isPrivate})")
      assert(methods.size >= 10, s"Expected >= 10 methods in Options.scala, got ${methods.size}")

  test("parity: emitWithParity on ParseError"):
    if !java.nio.file.Files.exists(katexRefRoot.resolve("ParseError.scala")) then
      println("SKIP: ssg-katex reference not found")
    else
      val rast = loadRast("/rast/katex/src/ParseError.rast.json")
      val refPath = katexRefRoot.resolve("ParseError.scala")
      val (source, summary) = dedicated.KaTeXEmitter.emitWithParity(rast, refPath)
      println(s"ParseError parity: ${summary.totalMethods} methods, ${summary.matchedFromRast} RAST-matched, ${summary.keptFromReference} kept")
      assert(source.contains("ParseError"), "should contain ParseError")

  // -----------------------------------------------------------------------
  // Full batch analysis
  // -----------------------------------------------------------------------

  test("batch: analyze all KaTeX modules"):
    if !java.nio.file.Files.exists(katexRefRoot) then
      println("SKIP: ssg-katex reference not found at " + katexRefRoot)
    else
      val summary = dedicated.KaTeXEmitter.analyzeAll(tryLoadRast, katexRefRoot)
      println("\n" + dedicated.KaTeXEmitter.formatBatchSummary(summary))
      assert(summary.foundRast >= 50, s"Expected >= 50 RAST files, got ${summary.foundRast}")
      assert(summary.functionDefs >= 30, s"Expected >= 30 function defs, got ${summary.functionDefs}")

  // -----------------------------------------------------------------------
  // Type mapping
  // -----------------------------------------------------------------------

  test("tsTypeToScala: basic type conversions"):
    assertEquals(dedicated.KaTeXEmitter.tsTypeToScala("string"), "String")
    assertEquals(dedicated.KaTeXEmitter.tsTypeToScala("number"), "Double")
    assertEquals(dedicated.KaTeXEmitter.tsTypeToScala("boolean"), "Boolean")
    assertEquals(dedicated.KaTeXEmitter.tsTypeToScala("void"), "Unit")
    assertEquals(dedicated.KaTeXEmitter.tsTypeToScala("any"), "Any")

  test("tsTypeToScala: ParseNode generic"):
    assertEquals(dedicated.KaTeXEmitter.tsTypeToScala("ParseNode<\"accent\">"), "ParseNodeAccent")
    assertEquals(dedicated.KaTeXEmitter.tsTypeToScala("ParseNode<\"color\">"), "ParseNodeColor")

  test("tsTypeToScala: nullable"):
    assertEquals(dedicated.KaTeXEmitter.tsTypeToScala("Token | null"), "Nullable[Token]")

  test("tsTypeToScala: array"):
    assertEquals(dedicated.KaTeXEmitter.tsTypeToScala("string[]"), "Array[String]")

  // -----------------------------------------------------------------------
  // Output inspection
  // -----------------------------------------------------------------------

  test("emit AccentFunc to target for inspection"):
    val rast = loadRast("/rast/katex/src/functions/accent.rast.json")
    val (source, _) = dedicated.KaTeXEmitter.emitFunctionModule(rast, "AccentFunc")
    val outDir = java.nio.file.Path.of(sys.props.getOrElse("user.dir", ".")).resolve("target/emitted-katex-functions")
    java.nio.file.Files.createDirectories(outDir)
    java.nio.file.Files.writeString(outDir.resolve("AccentFunc.scala"), source)
    println(s"AccentFunc.scala: ${source.linesIterator.size} lines")
    println(s"Written to: ${outDir.resolve("AccentFunc.scala")}")
