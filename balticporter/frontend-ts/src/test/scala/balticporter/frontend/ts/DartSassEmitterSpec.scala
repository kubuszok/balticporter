package balticporter.frontend.ts

class DartSassEmitterSpec extends munit.FunSuite:

  private def tryLoadRast(resource: String): Option[RastFile] =
    val stream = getClass.getResourceAsStream(resource)
    if stream == null then None
    else
      try
        val json = new String(stream.readAllBytes(), java.nio.charset.StandardCharsets.UTF_8)
        stream.close()
        Some(balticporter.corpus.sass.DartSassEmitter.readDartRast(json))
      catch
        case e: Exception =>
          try stream.close()
          catch case _: Exception => ()
          None

  private val sassRefRoot: java.nio.file.Path =
    val cpRef = getClass.getResource("/reference/dart-sass/Compile.scala")
    if cpRef != null && cpRef.getProtocol == "file" then java.nio.file.Path.of(cpRef.toURI).getParent
    else
      val candidates = List(
        sys.props.get("ssg.root").map(java.nio.file.Path.of(_)),
        Some(java.nio.file.Path.of(sys.props.getOrElse("user.dir", ".")).getParent.resolve("ssg"))
      ).flatten
      candidates.map(_.resolve("ssg-sass/src/main/scala/ssg/sass")).find(p => java.nio.file.Files.exists(p.resolve("Compile.scala"))).getOrElse(java.nio.file.Path.of("nonexistent"))

  // -----------------------------------------------------------------------
  // RAST loading
  // -----------------------------------------------------------------------

  test("exception.dart: load RAST"):
    val rast = tryLoadRast("/rast/dart-sass/lib/src/exception.dart.rast.json")
    assert(rast.isDefined, "should load exception.dart RAST")
    val r = rast.get
    assert(r.nodes.nonEmpty, s"should have nodes, got ${r.nodes.length}")
    println(s"exception.dart: ${r.nodes.length} nodes")
    r.nodes.foreach(n => println(s"  ${n.kind}: ${n.children.length} children"))

  test("callable.dart: load RAST"):
    val rast = tryLoadRast("/rast/dart-sass/lib/src/callable.dart.rast.json")
    assert(rast.isDefined, "should load callable.dart RAST")
    println(s"callable.dart: ${rast.get.nodes.length} nodes")

  test("compile.dart: load RAST"):
    val rast = tryLoadRast("/rast/dart-sass/lib/src/compile.dart.rast.json")
    assert(rast.isDefined, "should load compile.dart RAST")
    println(s"compile.dart: ${rast.get.nodes.length} nodes")

  // -----------------------------------------------------------------------
  // Function extraction
  // -----------------------------------------------------------------------

  test("exception.dart: extract functions"):
    val rast = tryLoadRast("/rast/dart-sass/lib/src/exception.dart.rast.json")
    assert(rast.isDefined)
    val fns = balticporter.corpus.sass.DartSassEmitter.extractAllFunctions(rast.get)
    println(s"exception.dart: ${fns.size} functions: ${fns.map(_.name).take(10).mkString(", ")}")
    assert(fns.nonEmpty, "should extract functions from exception.dart")

  test("color_names.dart: extract functions"):
    val rast = tryLoadRast("/rast/dart-sass/lib/src/color_names.dart.rast.json")
    assert(rast.isDefined)
    val fns = balticporter.corpus.sass.DartSassEmitter.extractAllFunctions(rast.get)
    println(s"color_names.dart: ${fns.size} functions")

  // -----------------------------------------------------------------------
  // Type mapping
  // -----------------------------------------------------------------------

  test("dartTypeToScala: basic types"):
    assertEquals(balticporter.corpus.sass.DartSassEmitter.dartTypeToScala("String"), "String")
    assertEquals(balticporter.corpus.sass.DartSassEmitter.dartTypeToScala("int"), "Int")
    assertEquals(balticporter.corpus.sass.DartSassEmitter.dartTypeToScala("double"), "Double")
    assertEquals(balticporter.corpus.sass.DartSassEmitter.dartTypeToScala("bool"), "Boolean")
    assertEquals(balticporter.corpus.sass.DartSassEmitter.dartTypeToScala("void"), "Unit")
    assertEquals(balticporter.corpus.sass.DartSassEmitter.dartTypeToScala("dynamic"), "Any")

  test("dartTypeToScala: nullable"):
    assertEquals(balticporter.corpus.sass.DartSassEmitter.dartTypeToScala("String?"), "Nullable[String]")
    assertEquals(balticporter.corpus.sass.DartSassEmitter.dartTypeToScala("int?"), "Nullable[Int]")

  test("dartTypeToScala: collections"):
    assertEquals(balticporter.corpus.sass.DartSassEmitter.dartTypeToScala("List<String>"), "List[String]")
    assertEquals(balticporter.corpus.sass.DartSassEmitter.dartTypeToScala("Map<String, int>"), "Map[String, Int]")
    assertEquals(balticporter.corpus.sass.DartSassEmitter.dartTypeToScala("Set<String>"), "Set[String]")

  test("dartTypeToScala: Future unwrap"):
    assertEquals(balticporter.corpus.sass.DartSassEmitter.dartTypeToScala("Future<void>"), "Unit")
    assertEquals(balticporter.corpus.sass.DartSassEmitter.dartTypeToScala("Future<String>"), "String")

  test("dartTypeToScala: nested"):
    assertEquals(balticporter.corpus.sass.DartSassEmitter.dartTypeToScala("List<String?>"), "List[Nullable[String]]")
    assertEquals(balticporter.corpus.sass.DartSassEmitter.dartTypeToScala("Map<String, List<int>>"), "Map[String, List[Int]]")

  // -----------------------------------------------------------------------
  // Body normalization diagnostic
  // -----------------------------------------------------------------------

  test("normalizeNodeTree: Dart body becomes TS-compatible"):
    val rast = tryLoadRast("/rast/dart-sass/lib/src/exception.dart.rast.json")
    assert(rast.isDefined)
    val fns = balticporter.corpus.sass.DartSassEmitter.extractAllFunctions(rast.get)
    println(s"Extracted ${fns.size} functions from exception.dart")
    // Try translating each function body
    var translated = 0
    var refused    = 0
    for fn <- fns do
      val bodyOpt = balticporter.corpus.sass.DartSassEmitter.findFunctionBody(rast.get, fn.name)
      bodyOpt.foreach { body =>
        val normalized = dedicated.DefmethodBodyTranslator.normalizeNodeTree(body)
        val entry      = balticporter.corpus.terser.TerserEmitter.DefmethodEntry("_free_", fn.name, fn.params, normalized)
        val result     = dedicated.DefmethodBodyTranslator.translateBody(entry, Nil, "    ")
        if result.isComplete then translated += 1
        else
          refused += 1
          if result.refusalReasons.size <= 2 then println(s"  ${fn.name}: ${result.refusalReasons.mkString(", ")}")
      }
    println(s"exception.dart body translation: $translated complete, $refused partial/refused out of ${fns.size}")
    assert(translated > 0, "should translate at least some bodies")

  // -----------------------------------------------------------------------
  // Parity-derive
  // -----------------------------------------------------------------------

  test("parity: SassException.scala"):
    if !java.nio.file.Files.exists(sassRefRoot.resolve("SassException.scala")) then println("SKIP: ssg-sass reference not found")
    else
      val rast = tryLoadRast("/rast/dart-sass/lib/src/exception.dart.rast.json")
      assert(rast.isDefined)
      val refPath           = sassRefRoot.resolve("SassException.scala")
      val (source, summary) = balticporter.corpus.sass.DartSassEmitter.emitWithParity(rast.get, refPath)
      println(
        s"SassException parity: ${summary.totalMethods} methods, ${summary.matchedFromRast} RAST, ${summary.keptFromReference} ref"
      )
      assert(source.contains("SassException") || source.contains("class "), "should contain class definition")

  test("parity: Compile.scala"):
    if !java.nio.file.Files.exists(sassRefRoot.resolve("Compile.scala")) then println("SKIP: ssg-sass reference not found")
    else
      val rast = tryLoadRast("/rast/dart-sass/lib/src/compile.dart.rast.json")
      assert(rast.isDefined)
      val refPath           = sassRefRoot.resolve("Compile.scala")
      val (source, summary) = balticporter.corpus.sass.DartSassEmitter.emitWithParity(rast.get, refPath)
      println(s"Compile parity: ${summary.totalMethods} methods, ${summary.matchedFromRast} RAST, ${summary.keptFromReference} ref")
      // Diagnostic: show RAST function names vs reference method names
      val rastFns    = balticporter.corpus.sass.DartSassEmitter.extractAllFunctions(rast.get)
      val refSource  = new String(java.nio.file.Files.readAllBytes(refPath))
      val refMethods = balticporter.corpus.terser.TerserCompressEmitter.findMethodBoundaries(refSource.split("\n", -1).toList)
      println(
        s"  RAST functions: ${rastFns.map(f => balticporter.corpus.sass.DartSassEmitter.dartToCamelCase(f.name)).take(10).mkString(", ")}"
      )
      println(s"  Ref methods: ${refMethods.map(_.name).take(10).mkString(", ")}")

  // -----------------------------------------------------------------------
  // Batch analysis
  // -----------------------------------------------------------------------

  // -----------------------------------------------------------------------
  // Diagnostic: Expression/Statement getter name matching
  // -----------------------------------------------------------------------

  test("diagnostic: Expression RAST vs reference name mismatch"):
    val rast = tryLoadRast("/rast/dart-sass/lib/src/ast/sass/expression.dart.rast.json")
    if rast.isEmpty then println("SKIP: expression.dart RAST not found")
    else
      // Also load subclass files
      val subclassPaths = List(
        "binary_operation",
        "boolean",
        "color",
        "function",
        "if",
        "interpolated_function",
        "legacy_if",
        "list",
        "map",
        "null",
        "number",
        "parenthesized",
        "selector",
        "string",
        "supports",
        "unary_operation",
        "value",
        "variable"
      )
      val subclassRasts = subclassPaths.flatMap(name => tryLoadRast(s"/rast/dart-sass/lib/src/ast/sass/expression/$name.dart.rast.json"))
      val allRasts      = rast.get :: subclassRasts
      val allFns        = allRasts.flatMap(r => balticporter.corpus.sass.DartSassEmitter.extractAllFunctions(r))
      val rastNames     = allFns.map(f => balticporter.corpus.sass.DartSassEmitter.dartToCamelCase(f.name)).toSet
      println(s"Expression RAST: ${allFns.size} functions extracted (from ${allRasts.size} files)")
      println(
        s"  Names: ${allFns.map(f => f.name + " -> " + balticporter.corpus.sass.DartSassEmitter.dartToCamelCase(f.name)).take(20).mkString(", ")}"
      )

      if java.nio.file.Files.exists(sassRefRoot.resolve("ast/sass/Expression.scala")) then
        val refSource  = new String(java.nio.file.Files.readAllBytes(sassRefRoot.resolve("ast/sass/Expression.scala")))
        val refMethods = balticporter.corpus.terser.TerserCompressEmitter.findMethodBoundaries(refSource.split("\n", -1).toList)
        val refNames   = refMethods.map(_.name).toSet
        println(s"Expression reference: ${refMethods.size} methods")
        println(s"  Names: ${refMethods.map(_.name).mkString(", ")}")
        val matched    = rastNames.intersect(refNames)
        val onlyInRast = rastNames -- refNames
        val onlyInRef  = refNames -- rastNames
        println(s"  Matched: ${matched.size} (${matched.toList.sorted.take(10).mkString(", ")})")
        println(s"  Only in RAST: ${onlyInRast.size} (${onlyInRast.toList.sorted.take(10).mkString(", ")})")
        println(s"  Only in ref: ${onlyInRef.size} (${onlyInRef.toList.sorted.take(10).mkString(", ")})")
      else println("SKIP: Expression.scala reference not found")

  test("diagnostic: MathFunctions RAST extraction"):
    val rast = tryLoadRast("/rast/dart-sass/lib/src/functions/math.dart.rast.json")
    if rast.isEmpty then println("SKIP: math.dart RAST not found")
    else
      val fns = balticporter.corpus.sass.DartSassEmitter.extractAllFunctions(rast.get)
      println(s"math.dart: ${fns.size} functions extracted")
      println(s"  Names: ${fns.map(_.name).mkString(", ")}")
      // Show top-level node kinds
      for n <- rast.get.nodes.take(10) do println(s"  Node: ${n.kind}, children: ${n.children.size}")

  // -----------------------------------------------------------------------
  // Batch analysis
  // -----------------------------------------------------------------------

  test("batch: analyze all dart-sass modules"):
    if !java.nio.file.Files.exists(sassRefRoot) then println("SKIP: ssg-sass reference not found at " + sassRefRoot)
    else
      val summary = balticporter.corpus.sass.DartSassEmitter.analyzeAll(tryLoadRast, sassRefRoot)
      println("\n" + balticporter.corpus.sass.DartSassEmitter.formatBatchSummary(summary))
      assert(summary.foundRast >= 20, s"Expected >= 20 RAST files, got ${summary.foundRast}")

  test("batch: emitAllWithParity writes modules"):
    if !java.nio.file.Files.exists(sassRefRoot) then println("SKIP: ssg-sass reference not found at " + sassRefRoot)
    else
      val outDir  = java.nio.file.Path.of(sys.props.getOrElse("user.dir", ".")).resolve("target/emitted-dart-sass-parity")
      val results = balticporter.corpus.sass.DartSassEmitter.emitAllWithParity(tryLoadRast, sassRefRoot, outDir)

      println("\n=== dart-sass Parity-derive Summary ===")
      println(balticporter.corpus.sass.DartSassEmitter.formatParitySummaryTable(results.map(_._2)))

      for (mod, summary) <- results do
        println(
          s"  ${mod.objectName}: ${summary.totalMethods} methods, ${summary.matchedFromRast} RAST, ${summary.keptFromReference} ref"
        )

      assert(results.nonEmpty, "should emit at least some modules")
      println(s"\nEmitted ${results.size} modules to $outDir")
