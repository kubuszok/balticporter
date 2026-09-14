package balticporter.frontend.ts

class TerserB10B11C3EmitterSpec extends munit.FunSuite:

  private def loadRast(resource: String): RastFile =
    val stream = getClass.getResourceAsStream(resource)
    assert(stream != null, s"Resource not found: $resource")
    val json = new String(stream.readAllBytes(), java.nio.charset.StandardCharsets.UTF_8)
    stream.close()
    Rast.readFile(json)

  // -- B10: Token constants ---------------------------------------------------

  test("B10: Token constants object with keyword/operator/punctuation sets"):
    val rast  = loadRast("/rast/terser/lib/parse.rast.json")
    val scala = balticporter.corpus.terser.TerserB10B11C3Emitter.emitTokenConstants(rast)
    assert(scala.contains("object Token"), "should emit Token object")
    assert(scala.contains("val Keywords: Set[String]"), "should have Keywords set")
    assert(scala.contains("val Operators: Set[String]"), "should have Operators set")
    assert(scala.contains("val Punctuation: Set[String]"), "should have Punctuation set")
    assert(scala.contains("val Precedence: Map[String, Int]"), "should have Precedence map")
    assert(scala.contains("\"break\""), "should include break keyword")
    assert(scala.contains("\"++\""), "should include ++ operator")
    assert(scala.contains("def isKeyword"), "should have isKeyword helper")
    assert(scala.contains("RAST: Tokenizer scanning logic not translatable"), "should note refusal")

  test("B10: Tokenizer skeleton with class structure"):
    val rast  = loadRast("/rast/terser/lib/parse.rast.json")
    val scala = balticporter.corpus.terser.TerserB10B11C3Emitter.emitTokenizerSkeleton(rast)
    assert(scala.contains("class Tokenizer"), "should emit Tokenizer class")
    assert(scala.contains("text:"), "should have text param")
    assert(scala.contains("def peek()"), "should have peek method")
    assert(scala.contains("def next()"), "should have next method")
    assert(scala.contains("def nextToken()"), "should have nextToken method")
    assert(scala.contains("def isEof"), "should have isEof method")
    assert(scala.contains("counted refusal"), "should note refusal")

  // -- B11: CompressorOptions and MinifyOptions --------------------------------

  test("B11: CompressorOptions case class with all optimizer flags"):
    val rast  = loadRast("/rast/terser/lib/compress/index.rast.json")
    val scala = balticporter.corpus.terser.TerserB10B11C3Emitter.emitCompressorOptions(rast)
    assert(scala.contains("final case class CompressorOptions"), "should emit case class")
    assert(scala.contains("deadCode:"), "should have deadCode flag")
    assert(scala.contains("collapseVars:"), "should have collapseVars flag")
    assert(scala.contains("unsafe:"), "should have unsafe flag")
    assert(scala.contains("unused:"), "should have unused flag")
    assert(scala.contains("passes:"), "should have passes count")
    assert(scala.contains("pureFuncs:"), "should have pureFuncs list")
    assert(scala.contains("object CompressorOptions"), "should have companion")
    assert(scala.contains("val Defaults"), "should have Defaults val")

  test("B11: MinifyOptions top-level configuration"):
    val rast  = loadRast("/rast/terser/lib/minify.rast.json")
    val scala = balticporter.corpus.terser.TerserB10B11C3Emitter.emitMinifyOptions(rast)
    assert(scala.contains("final case class MinifyOptions"), "should emit case class")
    assert(scala.contains("compress:"), "should have compress field")
    assert(scala.contains("output:"), "should have output field")
    assert(scala.contains("CompressorOptions | Boolean"), "should have union type for compress")
    assert(scala.contains("def resolvedCompress"), "should have resolvedCompress method")
    assert(scala.contains("import ssg.js.compress.CompressorOptions"), "should import CompressorOptions")

  // -- C3: Compress module summaries -------------------------------------------

  test("C3: extract DEFMETHOD entries from compress/index RAST"):
    val rast                   = loadRast("/rast/terser/lib/compress/index.rast.json")
    val (name, count, methods) = balticporter.corpus.terser.TerserB10B11C3Emitter.extractCompressModuleSummary(rast, "index")
    assert(name == "index", "should return module name")
    // compress/index.js has DEFMETHOD calls (the exact count depends on RAST)
    println(s"[C3] compress/index: $count DEFMETHODs found: ${methods.take(5).mkString(", ")}${if (methods.size > 5) "..." else ""}")

  test("C3: extract DEFMETHOD entries from compress/inference RAST"):
    val rast                = loadRast("/rast/terser/lib/compress/inference.rast.json")
    val (_, count, methods) = balticporter.corpus.terser.TerserB10B11C3Emitter.extractCompressModuleSummary(rast, "inference")
    println(
      s"[C3] compress/inference: $count DEFMETHODs found: ${methods.take(5).mkString(", ")}${if (methods.size > 5) "..." else ""}"
    )

  test("C3: extract DEFMETHOD entries from compress/evaluate RAST"):
    val rast                = loadRast("/rast/terser/lib/compress/evaluate.rast.json")
    val (_, count, methods) = balticporter.corpus.terser.TerserB10B11C3Emitter.extractCompressModuleSummary(rast, "evaluate")
    println(s"[C3] compress/evaluate: $count DEFMETHODs found: ${methods.take(5).mkString(", ")}${if (methods.size > 5) "..." else ""}")

  test("C3: emit compress module skeleton from RAST"):
    val rast  = loadRast("/rast/terser/lib/compress/inference.rast.json")
    val scala = balticporter.corpus.terser.TerserB10B11C3Emitter.emitCompressModuleSkeleton(rast, "inference", "Inference")
    assert(scala.contains("object Inference"), "should emit Inference object")
    assert(scala.contains("DEFMETHOD"), "should reference DEFMETHOD")
    assert(scala.contains("DefmethodBodyTranslator"), "should reference B8")

  test("C3: emit compress skeleton for drop-side-effect-free"):
    val rast  = loadRast("/rast/terser/lib/compress/drop-side-effect-free.rast.json")
    val scala = balticporter.corpus.terser.TerserB10B11C3Emitter.emitCompressModuleSkeleton(rast, "drop-side-effect-free", "DropSideEffectFree")
    assert(scala.contains("object DropSideEffectFree"), "should emit DropSideEffectFree object")

  // -- Write all emitted files ------------------------------------------------

  test("write all B10/B11/C3 emitted files"):
    val outDir = java.nio.file.Path.of(sys.props.getOrElse("user.dir", ".")).resolve("target/emitted-terser-b10-c3")
    java.nio.file.Files.createDirectories(outDir)

    val parseRast    = loadRast("/rast/terser/lib/parse.rast.json")
    val compressRast = loadRast("/rast/terser/lib/compress/index.rast.json")
    val minifyRast   = loadRast("/rast/terser/lib/minify.rast.json")

    val allFiles = List(
      ("Token", balticporter.corpus.terser.TerserB10B11C3Emitter.emitTokenConstants(parseRast)),
      ("TokenizerSkeleton", balticporter.corpus.terser.TerserB10B11C3Emitter.emitTokenizerSkeleton(parseRast)),
      ("CompressorOptions", balticporter.corpus.terser.TerserB10B11C3Emitter.emitCompressorOptions(compressRast)),
      ("MinifyOptions", balticporter.corpus.terser.TerserB10B11C3Emitter.emitMinifyOptions(minifyRast))
    )

    // Compress module skeletons
    val compressModules = List(
      ("inference", "Inference", "/rast/terser/lib/compress/inference.rast.json"),
      ("evaluate", "Evaluate", "/rast/terser/lib/compress/evaluate.rast.json"),
      ("drop-side-effect-free", "DropSideEffectFree", "/rast/terser/lib/compress/drop-side-effect-free.rast.json"),
      ("drop-unused", "DropUnused", "/rast/terser/lib/compress/drop-unused.rast.json"),
      ("tighten-body", "TightenBody", "/rast/terser/lib/compress/tighten-body.rast.json"),
      ("reduce-vars", "ReduceVars", "/rast/terser/lib/compress/reduce-vars.rast.json"),
      ("inline", "Inline", "/rast/terser/lib/compress/inline.rast.json")
    )

    val moduleFiles = compressModules.map { (mod, obj, path) =>
      val rast = loadRast(path)
      (obj, balticporter.corpus.terser.TerserB10B11C3Emitter.emitCompressModuleSkeleton(rast, mod, obj))
    }

    for ((name, source) <- allFiles ++ moduleFiles) {
      val path = outDir.resolve(s"$name.scala")
      java.nio.file.Files.writeString(path, source)
      println(s"[emit] $name.scala: ${source.linesIterator.size} lines -> $path")
    }
    println(s"[emit] Total: ${(allFiles ++ moduleFiles).size} B10/B11/C3 files written")
