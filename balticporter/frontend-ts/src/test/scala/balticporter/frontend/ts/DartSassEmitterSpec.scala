package balticporter.frontend.ts

class DartSassEmitterSpec extends munit.FunSuite:

  private def tryLoadRast(resource: String): Option[RastFile] =
    val stream = getClass.getResourceAsStream(resource)
    if stream == null then None
    else
      try
        val json = new String(stream.readAllBytes(), java.nio.charset.StandardCharsets.UTF_8)
        stream.close()
        Some(dedicated.DartSassEmitter.readDartRast(json))
      catch
        case e: Exception =>
          try stream.close() catch case _: Exception => ()
          None

  private val sassRefRoot: java.nio.file.Path =
    val candidates = List(
      sys.props.get("ssg.root").map(java.nio.file.Path.of(_)),
      Some(java.nio.file.Path.of(sys.props.getOrElse("user.dir", ".")).getParent.resolve("ssg")),
      Some(java.nio.file.Path.of("/Users/dev/Workspaces/kubuszok/ssg")),
    ).flatten
    candidates.map(_.resolve("ssg-sass/src/main/scala/ssg/sass"))
      .find(p => java.nio.file.Files.exists(p.resolve("Compile.scala")))
      .getOrElse(java.nio.file.Path.of("nonexistent"))

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
    val fns = dedicated.DartSassEmitter.extractAllFunctions(rast.get)
    println(s"exception.dart: ${fns.size} functions: ${fns.map(_.name).take(10).mkString(", ")}")
    assert(fns.nonEmpty, "should extract functions from exception.dart")

  test("color_names.dart: extract functions"):
    val rast = tryLoadRast("/rast/dart-sass/lib/src/color_names.dart.rast.json")
    assert(rast.isDefined)
    val fns = dedicated.DartSassEmitter.extractAllFunctions(rast.get)
    println(s"color_names.dart: ${fns.size} functions")

  // -----------------------------------------------------------------------
  // Type mapping
  // -----------------------------------------------------------------------

  test("dartTypeToScala: basic types"):
    assertEquals(dedicated.DartSassEmitter.dartTypeToScala("String"), "String")
    assertEquals(dedicated.DartSassEmitter.dartTypeToScala("int"), "Int")
    assertEquals(dedicated.DartSassEmitter.dartTypeToScala("double"), "Double")
    assertEquals(dedicated.DartSassEmitter.dartTypeToScala("bool"), "Boolean")
    assertEquals(dedicated.DartSassEmitter.dartTypeToScala("void"), "Unit")
    assertEquals(dedicated.DartSassEmitter.dartTypeToScala("dynamic"), "Any")

  test("dartTypeToScala: nullable"):
    assertEquals(dedicated.DartSassEmitter.dartTypeToScala("String?"), "Nullable[String]")
    assertEquals(dedicated.DartSassEmitter.dartTypeToScala("int?"), "Nullable[Int]")

  test("dartTypeToScala: collections"):
    assertEquals(dedicated.DartSassEmitter.dartTypeToScala("List<String>"), "List[String]")
    assertEquals(dedicated.DartSassEmitter.dartTypeToScala("Map<String, int>"), "Map[String, Int]")
    assertEquals(dedicated.DartSassEmitter.dartTypeToScala("Set<String>"), "Set[String]")

  test("dartTypeToScala: Future unwrap"):
    assertEquals(dedicated.DartSassEmitter.dartTypeToScala("Future<void>"), "Unit")
    assertEquals(dedicated.DartSassEmitter.dartTypeToScala("Future<String>"), "String")

  test("dartTypeToScala: nested"):
    assertEquals(dedicated.DartSassEmitter.dartTypeToScala("List<String?>"), "List[Nullable[String]]")
    assertEquals(dedicated.DartSassEmitter.dartTypeToScala("Map<String, List<int>>"), "Map[String, List[Int]]")

  // -----------------------------------------------------------------------
  // Parity-derive
  // -----------------------------------------------------------------------

  test("parity: SassException.scala"):
    if !java.nio.file.Files.exists(sassRefRoot.resolve("SassException.scala")) then
      println("SKIP: ssg-sass reference not found")
    else
      val rast = tryLoadRast("/rast/dart-sass/lib/src/exception.dart.rast.json")
      assert(rast.isDefined)
      val refPath = sassRefRoot.resolve("SassException.scala")
      val (source, summary) = dedicated.DartSassEmitter.emitWithParity(rast.get, refPath)
      println(s"SassException parity: ${summary.totalMethods} methods, ${summary.matchedFromRast} RAST, ${summary.keptFromReference} ref")
      assert(source.contains("SassException") || source.contains("class "), "should contain class definition")

  test("parity: Compile.scala"):
    if !java.nio.file.Files.exists(sassRefRoot.resolve("Compile.scala")) then
      println("SKIP: ssg-sass reference not found")
    else
      val rast = tryLoadRast("/rast/dart-sass/lib/src/compile.dart.rast.json")
      assert(rast.isDefined)
      val refPath = sassRefRoot.resolve("Compile.scala")
      val (source, summary) = dedicated.DartSassEmitter.emitWithParity(rast.get, refPath)
      println(s"Compile parity: ${summary.totalMethods} methods, ${summary.matchedFromRast} RAST, ${summary.keptFromReference} ref")

  // -----------------------------------------------------------------------
  // Batch analysis
  // -----------------------------------------------------------------------

  test("batch: analyze all dart-sass modules"):
    if !java.nio.file.Files.exists(sassRefRoot) then
      println("SKIP: ssg-sass reference not found at " + sassRefRoot)
    else
      val summary = dedicated.DartSassEmitter.analyzeAll(tryLoadRast, sassRefRoot)
      println("\n" + dedicated.DartSassEmitter.formatBatchSummary(summary))
      assert(summary.foundRast >= 20, s"Expected >= 20 RAST files, got ${summary.foundRast}")

  test("batch: emitAllWithParity writes modules"):
    if !java.nio.file.Files.exists(sassRefRoot) then
      println("SKIP: ssg-sass reference not found at " + sassRefRoot)
    else
      val outDir = java.nio.file.Path.of(sys.props.getOrElse("user.dir", ".")).resolve("target/emitted-dart-sass-parity")
      val results = dedicated.DartSassEmitter.emitAllWithParity(tryLoadRast, sassRefRoot, outDir)

      println("\n=== dart-sass Parity-derive Summary ===")
      println(dedicated.DartSassEmitter.formatParitySummaryTable(results.map(_._2)))

      for (mod, summary) <- results do
        println(s"  ${mod.objectName}: ${summary.totalMethods} methods, ${summary.matchedFromRast} RAST, ${summary.keptFromReference} ref")

      assert(results.nonEmpty, "should emit at least some modules")
      println(s"\nEmitted ${results.size} modules to $outDir")
