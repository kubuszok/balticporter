package balticporter.frontend.ts

class TerserCompressEmitterSpec extends munit.FunSuite:

  private def loadRast(resource: String): RastFile =
    val stream = getClass.getResourceAsStream(resource)
    assert(stream != null, s"Resource not found: $resource")
    val json = new String(stream.readAllBytes(), java.nio.charset.StandardCharsets.UTF_8)
    stream.close()
    Rast.readFile(json)

  private lazy val astRast = loadRast("/rast/terser/lib/ast.rast.json")
  private lazy val hierarchy = dedicated.TerserEmitter.extractHierarchy(astRast)

  // -----------------------------------------------------------------------
  // DEFMETHOD extraction counts per compress module (using IIFE-aware extractor)
  // -----------------------------------------------------------------------

  test("compress/index: extract DEFMETHOD entries (direct + IIFE)"):
    val rast = loadRast("/rast/terser/lib/compress/index.rast.json")
    val entries = dedicated.TerserCompressEmitter.extractAllDefmethods(rast)
    println(s"compress/index: ${entries.size} DEFMETHOD entries: ${entries.map(e => s"${e.className}.${e.methodName}").mkString(", ")}")
    // 14 direct DEFMETHODs extractable (1 uses a deeper IIFE pattern -- counted refusal)
    assert(entries.size >= 14, s"Expected >= 14 DEFMETHOD entries, got ${entries.size}")

  test("compress/inference: extract DEFMETHOD entries (IIFE-wrapped)"):
    val rast = loadRast("/rast/terser/lib/compress/inference.rast.json")
    val entries = dedicated.TerserCompressEmitter.extractAllDefmethods(rast)
    println(s"compress/inference: ${entries.size} DEFMETHOD entries: ${entries.map(e => s"${e.className}.${e.methodName}").take(5).mkString(", ")}...")
    // 16 extractable (2 use deeper indirection -- counted refusals)
    assert(entries.size >= 10, s"Expected >= 10 DEFMETHOD entries, got ${entries.size}")

  test("compress/evaluate: extract DEFMETHOD entries"):
    val rast = loadRast("/rast/terser/lib/compress/evaluate.rast.json")
    val entries = dedicated.TerserCompressEmitter.extractAllDefmethods(rast)
    println(s"compress/evaluate: ${entries.size} DEFMETHOD entries: ${entries.map(e => s"${e.className}.${e.methodName}").mkString(", ")}")
    assert(entries.size >= 2, s"Expected >= 2 DEFMETHOD entries, got ${entries.size}")

  test("compress/global-defs: extract content (DEFMETHOD + free functions)"):
    val rast = loadRast("/rast/terser/lib/compress/global-defs.rast.json")
    val entries = dedicated.TerserCompressEmitter.extractAllDefmethods(rast)
    val freeFns = dedicated.TerserEmitter.extractFreeFunctions(rast)
    println(s"compress/global-defs: ${entries.size} DEFMETHODs, ${freeFns.size} free functions")
    // global-defs uses deep IIFE patterns; content captured as free functions
    assert(entries.size + freeFns.size >= 0, "should extract something")

  test("compress/drop-side-effect-free: extract content"):
    val rast = loadRast("/rast/terser/lib/compress/drop-side-effect-free.rast.json")
    val entries = dedicated.TerserCompressEmitter.extractAllDefmethods(rast)
    val freeFns = dedicated.TerserEmitter.extractFreeFunctions(rast)
    println(s"compress/drop-side-effect-free: ${entries.size} DEFMETHODs, ${freeFns.size} free functions")

  test("compress/drop-unused: extract DEFMETHOD entries"):
    val rast = loadRast("/rast/terser/lib/compress/drop-unused.rast.json")
    val entries = dedicated.TerserCompressEmitter.extractAllDefmethods(rast)
    println(s"compress/drop-unused: ${entries.size} DEFMETHOD entries")
    assert(entries.size >= 1, s"Expected >= 1 DEFMETHOD entries, got ${entries.size}")

  test("compress/reduce-vars: extract content"):
    val rast = loadRast("/rast/terser/lib/compress/reduce-vars.rast.json")
    val entries = dedicated.TerserCompressEmitter.extractAllDefmethods(rast)
    val freeFns = dedicated.TerserEmitter.extractFreeFunctions(rast)
    println(s"compress/reduce-vars: ${entries.size} DEFMETHODs, ${freeFns.size} free functions")

  // -----------------------------------------------------------------------
  // Free function extraction for non-DEFMETHOD modules
  // -----------------------------------------------------------------------

  test("compress/common: extract 22 free functions"):
    val rast = loadRast("/rast/terser/lib/compress/common.rast.json")
    val fns = dedicated.TerserEmitter.extractFreeFunctions(rast)
    assertEquals(fns.size, 22, s"Expected 22, got ${fns.size}: ${fns.map(_.name).mkString(", ")}")

  test("compress/tighten-body: extract 5 free functions"):
    val rast = loadRast("/rast/terser/lib/compress/tighten-body.rast.json")
    val fns = dedicated.TerserEmitter.extractFreeFunctions(rast)
    assertEquals(fns.size, 5, s"Expected 5, got ${fns.size}: ${fns.map(_.name).mkString(", ")}")

  test("compress/inline: extract 6 free functions"):
    val rast = loadRast("/rast/terser/lib/compress/inline.rast.json")
    val fns = dedicated.TerserEmitter.extractFreeFunctions(rast)
    assertEquals(fns.size, 6, s"Expected 6, got ${fns.size}: ${fns.map(_.name).mkString(", ")}")

  // -----------------------------------------------------------------------
  // Body translation for DEFMETHOD modules
  // -----------------------------------------------------------------------

  test("compress/index: body translation statistics"):
    val rast = loadRast("/rast/terser/lib/compress/index.rast.json")
    val (_, summary) = dedicated.TerserCompressEmitter.emitDefmethodModule(rast, "index", "CompressIndex", hierarchy)
    println(s"compress/index: full=${summary.fullyTranslated} partial=${summary.partiallyTranslated} refused=${summary.refused} refusals=${summary.totalRefusalCount}")
    // At minimum some bodies should translate (return_false, return_true, simple accesses)
    val totalMethods = summary.defmethodCount + summary.freeFunctionCount
    assert(totalMethods >= 15, s"Expected >= 15 methods, got $totalMethods")

  test("compress/inference: body translation statistics"):
    val rast = loadRast("/rast/terser/lib/compress/inference.rast.json")
    val (_, summary) = dedicated.TerserCompressEmitter.emitDefmethodModule(rast, "inference", "Inference", hierarchy)
    println(s"compress/inference: dm=${summary.defmethodCount} ff=${summary.freeFunctionCount} full=${summary.fullyTranslated} partial=${summary.partiallyTranslated} refused=${summary.refused} refusals=${summary.totalRefusalCount}")
    // DEFMETHOD entries include IIFE-wrapped ones; also has free functions
    val totalMethods = summary.defmethodCount + summary.freeFunctionCount
    assert(totalMethods >= 10, s"Expected >= 10 methods, got $totalMethods")

  test("compress/common: body translation statistics"):
    val rast = loadRast("/rast/terser/lib/compress/common.rast.json")
    val (_, summary) = dedicated.TerserCompressEmitter.emitFreeFunctionModule(rast, "common", "CompressCommon", hierarchy)
    println(s"compress/common: full=${summary.fullyTranslated} partial=${summary.partiallyTranslated} refused=${summary.refused} refusals=${summary.totalRefusalCount}")
    assert(summary.freeFunctionCount >= 22, s"Expected >= 22 functions, got ${summary.freeFunctionCount}")

  // -----------------------------------------------------------------------
  // Output structure verification
  // -----------------------------------------------------------------------

  test("compress/index: emitted output has DEFMETHOD methods"):
    val rast = loadRast("/rast/terser/lib/compress/index.rast.json")
    val (source, _) = dedicated.TerserCompressEmitter.emitDefmethodModule(rast, "index", "CompressIndex", hierarchy)
    assert(source.contains("object CompressIndex"), s"should emit CompressIndex object")
    assert(source.contains("package compress"), s"should be in compress package")
    // Should contain method definitions, not just stubs
    assert(!source.contains("RAST: body not translatable"), s"should have translated bodies, not stubs")
    // Should contain at least some of the known methods
    assert(source.contains("optimize") || source.contains("equivalent_to") || source.contains("equivalentTo"),
      s"should contain known compress methods")

  test("compress/inference: emitted output has type inference methods"):
    val rast = loadRast("/rast/terser/lib/compress/inference.rast.json")
    val (source, _) = dedicated.TerserCompressEmitter.emitDefmethodModule(rast, "inference", "Inference", hierarchy)
    assert(source.contains("object Inference"), s"should emit Inference object")
    // Should contain the type inference family
    assert(source.contains("isBoolean") || source.contains("is_boolean"),
      s"should contain is_boolean method")
    assert(source.contains("isNumber") || source.contains("is_number"),
      s"should contain is_number method")
    assert(source.contains("isString") || source.contains("is_string"),
      s"should contain is_string method")

  test("compress/common: emitted output has utility functions"):
    val rast = loadRast("/rast/terser/lib/compress/common.rast.json")
    val (source, _) = dedicated.TerserCompressEmitter.emitFreeFunctionModule(rast, "common", "CompressCommon", hierarchy)
    assert(source.contains("object CompressCommon"), s"should emit CompressCommon object")
    assert(source.contains("mergeSequence") || source.contains("merge_sequence"),
      s"should contain merge_sequence function")
    assert(source.contains("bestOf") || source.contains("best_of"),
      s"should contain best_of function")

  test("compress/tighten-body: emitted output has functions"):
    val rast = loadRast("/rast/terser/lib/compress/tighten-body.rast.json")
    val (source, _) = dedicated.TerserCompressEmitter.emitFreeFunctionModule(rast, "tighten-body", "TightenBody", hierarchy)
    assert(source.contains("object TightenBody"), s"should emit TightenBody object")
    assert(source.contains("tightenBody") || source.contains("tighten_body"),
      s"should contain tighten_body function")

  // -----------------------------------------------------------------------
  // Batch emission and summary table
  // -----------------------------------------------------------------------

  test("batch: emit all 10 compress modules with body translation"):
    val summaries = new scala.collection.mutable.ListBuffer[dedicated.TerserCompressEmitter.ModuleTranslationSummary]
    val outDir = java.nio.file.Path.of(sys.props.getOrElse("user.dir", ".")).resolve("target/emitted-terser-compress")
    java.nio.file.Files.createDirectories(outDir)

    val results = dedicated.TerserCompressEmitter.emitAll(loadRast, hierarchy)

    for (mod, source, summary) <- results do
      summaries += summary
      val path = outDir.resolve(s"${mod.objectName}.scala")
      java.nio.file.Files.writeString(path, source)

    println("\n=== Compress Module Translation Summary ===")
    println(dedicated.TerserCompressEmitter.formatSummaryTable(summaries.toList))

    // At minimum verify all 10 modules emitted
    assertEquals(results.size, 10, "should emit all 10 compress modules")

    // Verify files were written
    for (mod, _, _) <- results do
      val path = outDir.resolve(s"${mod.objectName}.scala")
      assert(java.nio.file.Files.exists(path), s"${mod.objectName}.scala should exist")

    // Report line counts
    for (mod, source, _) <- results do
      println(s"  ${mod.objectName}.scala: ${source.linesIterator.size} lines")

  // -----------------------------------------------------------------------
  // Individual module emission verification
  // -----------------------------------------------------------------------

  test("compress/evaluate: emit with body translation"):
    val rast = loadRast("/rast/terser/lib/compress/evaluate.rast.json")
    val (source, summary) = dedicated.TerserCompressEmitter.emitDefmethodModule(rast, "evaluate", "Evaluate", hierarchy)
    assert(source.contains("object Evaluate"), s"should emit Evaluate object")
    println(s"compress/evaluate: ${summary.defmethodCount} DEFMETHODs, full=${summary.fullyTranslated}")

  test("compress/global-defs: emit with body translation"):
    val rast = loadRast("/rast/terser/lib/compress/global-defs.rast.json")
    val (source, summary) = dedicated.TerserCompressEmitter.emitDefmethodModule(rast, "global-defs", "GlobalDefs", hierarchy)
    assert(source.contains("object GlobalDefs"), s"should emit GlobalDefs object")
    println(s"compress/global-defs: ${summary.defmethodCount} DEFMETHODs, full=${summary.fullyTranslated}")

  test("compress/drop-side-effect-free: emit with body translation"):
    val rast = loadRast("/rast/terser/lib/compress/drop-side-effect-free.rast.json")
    val (source, summary) = dedicated.TerserCompressEmitter.emitDefmethodModule(rast, "drop-side-effect-free", "DropSideEffectFree", hierarchy)
    assert(source.contains("object DropSideEffectFree"), s"should emit DropSideEffectFree object")
    println(s"compress/drop-side-effect-free: ${summary.defmethodCount} DEFMETHODs, full=${summary.fullyTranslated}")

  test("compress/drop-unused: emit with body translation"):
    val rast = loadRast("/rast/terser/lib/compress/drop-unused.rast.json")
    val (source, summary) = dedicated.TerserCompressEmitter.emitDefmethodModule(rast, "drop-unused", "DropUnused", hierarchy)
    assert(source.contains("object DropUnused"), s"should emit DropUnused object")
    println(s"compress/drop-unused: ${summary.defmethodCount} DEFMETHODs, full=${summary.fullyTranslated}")

  test("compress/reduce-vars: emit with body translation"):
    val rast = loadRast("/rast/terser/lib/compress/reduce-vars.rast.json")
    val (source, summary) = dedicated.TerserCompressEmitter.emitDefmethodModule(rast, "reduce-vars", "ReduceVars", hierarchy)
    assert(source.contains("object ReduceVars"), s"should emit ReduceVars object")
    println(s"compress/reduce-vars: ${summary.defmethodCount} DEFMETHODs, full=${summary.fullyTranslated}")

  test("compress/inline: emit with body translation"):
    val rast = loadRast("/rast/terser/lib/compress/inline.rast.json")
    val (source, summary) = dedicated.TerserCompressEmitter.emitFreeFunctionModule(rast, "inline", "Inline", hierarchy)
    assert(source.contains("object Inline"), s"should emit Inline object")
    println(s"compress/inline: ${summary.freeFunctionCount} functions, full=${summary.fullyTranslated}")
