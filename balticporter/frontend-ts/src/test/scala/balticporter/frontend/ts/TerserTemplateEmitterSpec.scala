package balticporter.frontend.ts

class TerserTemplateEmitterSpec extends munit.FunSuite:

  private val emitter = dedicated.TerserTemplateEmitter

  // ===========================================================================
  // Verify all 52 templates load successfully
  // ===========================================================================

  test("templateCount is 52") {
    assertEquals(emitter.templateCount, 52)
  }

  test("emitAll returns 52 entries") {
    val all = emitter.emitAll()
    assertEquals(all.size, 52)
  }

  // ===========================================================================
  // Verify each template loads and has the auto-generated header
  // ===========================================================================

  private val allTemplatePaths: List[(String, String)] = List(
    // js root
    ("js/Terser.scala.template", "js/Terser.scala"),
    ("js/TerserJsCompressor.scala.template", "js/TerserJsCompressor.scala"),
    ("js/package.scala.template", "js/package.scala"),
    // js/ast
    ("js/ast/AstClasses.scala.template", "js/ast/AstClasses.scala"),
    ("js/ast/AstConstants.scala.template", "js/ast/AstConstants.scala"),
    ("js/ast/AstDefinitions.scala.template", "js/ast/AstDefinitions.scala"),
    ("js/ast/AstEquivalent.scala.template", "js/ast/AstEquivalent.scala"),
    ("js/ast/AstExpressions.scala.template", "js/ast/AstExpressions.scala"),
    ("js/ast/AstNode.scala.template", "js/ast/AstNode.scala"),
    ("js/ast/AstScope.scala.template", "js/ast/AstScope.scala"),
    ("js/ast/AstSize.scala.template", "js/ast/AstSize.scala"),
    ("js/ast/AstStatements.scala.template", "js/ast/AstStatements.scala"),
    ("js/ast/AstSymbols.scala.template", "js/ast/AstSymbols.scala"),
    ("js/ast/AstToken.scala.template", "js/ast/AstToken.scala"),
    ("js/ast/Nodes.scala.template", "js/ast/Nodes.scala"),
    // js/compress
    ("js/compress/Common.scala.template", "js/compress/Common.scala"),
    ("js/compress/Compressor.scala.template", "js/compress/Compressor.scala"),
    ("js/compress/CompressorFlags.scala.template", "js/compress/CompressorFlags.scala"),
    ("js/compress/CompressorLike.scala.template", "js/compress/CompressorLike.scala"),
    ("js/compress/CompressorOptions.scala.template", "js/compress/CompressorOptions.scala"),
    ("js/compress/DropSideEffectFree.scala.template", "js/compress/DropSideEffectFree.scala"),
    ("js/compress/DropUnused.scala.template", "js/compress/DropUnused.scala"),
    ("js/compress/Evaluate.scala.template", "js/compress/Evaluate.scala"),
    ("js/compress/GlobalDefs.scala.template", "js/compress/GlobalDefs.scala"),
    ("js/compress/Hoisting.scala.template", "js/compress/Hoisting.scala"),
    ("js/compress/Inference.scala.template", "js/compress/Inference.scala"),
    ("js/compress/Inline.scala.template", "js/compress/Inline.scala"),
    ("js/compress/NativeObjects.scala.template", "js/compress/NativeObjects.scala"),
    ("js/compress/ReduceVars.scala.template", "js/compress/ReduceVars.scala"),
    ("js/compress/TightenBody.scala.template", "js/compress/TightenBody.scala"),
    // js/output
    ("js/output/FirstInStatement.scala.template", "js/output/FirstInStatement.scala"),
    ("js/output/JsNumber.scala.template", "js/output/JsNumber.scala"),
    ("js/output/OutputOptions.scala.template", "js/output/OutputOptions.scala"),
    ("js/output/OutputStream.scala.template", "js/output/OutputStream.scala"),
    // js/parse
    ("js/parse/Parser.scala.template", "js/parse/Parser.scala"),
    ("js/parse/Precedence.scala.template", "js/parse/Precedence.scala"),
    ("js/parse/Token.scala.template", "js/parse/Token.scala"),
    ("js/parse/Tokenizer.scala.template", "js/parse/Tokenizer.scala"),
    ("js/parse/UnicodeIdentifierTables.scala.template", "js/parse/UnicodeIdentifierTables.scala"),
    // js/scope
    ("js/scope/DomProps.scala.template", "js/scope/DomProps.scala"),
    ("js/scope/Mangler.scala.template", "js/scope/Mangler.scala"),
    ("js/scope/PropMangler.scala.template", "js/scope/PropMangler.scala"),
    ("js/scope/ScopeAnalysis.scala.template", "js/scope/ScopeAnalysis.scala"),
    ("js/scope/SymbolDef.scala.template", "js/scope/SymbolDef.scala"),
    // js/sourcemap
    ("js/sourcemap/Base64.scala.template", "js/sourcemap/Base64.scala"),
    ("js/sourcemap/InlineSourceMap.scala.template", "js/sourcemap/InlineSourceMap.scala"),
    ("js/sourcemap/SourceMap.scala.template", "js/sourcemap/SourceMap.scala"),
    ("js/sourcemap/SourceMapConsumer.scala.template", "js/sourcemap/SourceMapConsumer.scala"),
    ("js/sourcemap/SourceMapGenerator.scala.template", "js/sourcemap/SourceMapGenerator.scala"),
    ("js/sourcemap/SourceMapJson.scala.template", "js/sourcemap/SourceMapJson.scala"),
    ("js/sourcemap/SourceMapTypes.scala.template", "js/sourcemap/SourceMapTypes.scala"),
    ("js/sourcemap/VlqCodec.scala.template", "js/sourcemap/VlqCodec.scala"),
  )

  for ((templatePath, outPath) <- allTemplatePaths)
    test(s"loads template: $outPath") {
      val result = emitter.fromTemplate(templatePath)
      assert(result.contains("Terser JavaScript minifier"),
        s"$outPath should contain auto-generated header")
      assert(result.nonEmpty, s"$outPath should not be empty")
    }

  // ===========================================================================
  // Verify key types exist in the emitted code
  // ===========================================================================

  test("js/Terser.scala contains Terser object") {
    val content = emitter.fromTemplate("js/Terser.scala.template")
    assert(content.contains("object Terser"), "should have Terser object")
  }

  test("js/ast/AstNode.scala contains AstNode trait") {
    val content = emitter.fromTemplate("js/ast/AstNode.scala.template")
    assert(content.contains("AstNode"), "should have AstNode type")
  }

  test("js/compress/Compressor.scala contains Compressor") {
    val content = emitter.fromTemplate("js/compress/Compressor.scala.template")
    assert(content.contains("Compressor"), "should have Compressor type")
  }

  test("js/parse/Parser.scala contains Parser") {
    val content = emitter.fromTemplate("js/parse/Parser.scala.template")
    assert(content.contains("Parser"), "should have Parser type")
  }

  test("js/output/OutputStream.scala contains OutputStream") {
    val content = emitter.fromTemplate("js/output/OutputStream.scala.template")
    assert(content.contains("OutputStream"), "should have OutputStream type")
  }

  test("js/scope/Mangler.scala contains Mangler") {
    val content = emitter.fromTemplate("js/scope/Mangler.scala.template")
    assert(content.contains("Mangler"), "should have Mangler type")
  }

  test("js/sourcemap/SourceMap.scala contains SourceMap") {
    val content = emitter.fromTemplate("js/sourcemap/SourceMap.scala.template")
    assert(content.contains("SourceMap"), "should have SourceMap type")
  }

  // ===========================================================================
  // Verify package declarations
  // ===========================================================================

  test("root package files declare ssg.js") {
    val content = emitter.fromTemplate("js/Terser.scala.template")
    assert(content.contains("package ssg") && content.contains("package js"),
      "should declare ssg.js package")
  }

  test("ast package files declare ssg.js.ast") {
    val content = emitter.fromTemplate("js/ast/AstNode.scala.template")
    assert(content.contains("package ast"), "should declare ast subpackage")
  }

  test("compress package files declare ssg.js.compress") {
    val content = emitter.fromTemplate("js/compress/Compressor.scala.template")
    assert(content.contains("package compress"), "should declare compress subpackage")
  }

  // ===========================================================================
  // Write ALL emitted files to target/emitted-terser
  // ===========================================================================

  test("write all 52 template-emitted files to target/emitted-terser") {
    val outDir = java.nio.file.Path.of(sys.props.getOrElse("user.dir", "."))
      .resolve("target/emitted-terser")

    val all = emitter.emitAll()
    var count = 0
    for ((outPath, content) <- all.toList.sortBy(_._1)) {
      val path = outDir.resolve(outPath)
      java.nio.file.Files.createDirectories(path.getParent)
      java.nio.file.Files.writeString(path, content)
      count += 1
      println(s"[emit] $outPath: ${content.linesIterator.size} lines -> $path")
    }
    println(s"[emit] Total: $count files written to $outDir")
    assertEquals(count, 52)
  }
