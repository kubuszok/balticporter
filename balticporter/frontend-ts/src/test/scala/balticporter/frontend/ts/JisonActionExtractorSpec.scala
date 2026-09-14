package balticporter.frontend.ts

class JisonActionExtractorSpec extends munit.FunSuite:

  private val jisonRoot: java.nio.file.Path =
    val cpRef = getClass.getResource("/reference/jison/flowchart/parser/flow.jison")
    if cpRef != null && cpRef.getProtocol == "file" then
      java.nio.file.Path.of(cpRef.toURI).getParent.getParent.getParent // up to jison/
    else
      val candidates = List(
        sys.props.get("ssg.root").map(java.nio.file.Path.of(_)),
        Some(java.nio.file.Path.of(sys.props.getOrElse("user.dir", ".")).getParent.resolve("ssg")),
      ).flatten
      candidates.map(_.resolve("original-src/mermaid/packages/mermaid/src/diagrams"))
        .find(java.nio.file.Files.exists(_))
        .getOrElse(java.nio.file.Path.of("nonexistent"))

  private def loadJison(path: String): String =
    val fullPath = jisonRoot.resolve(path)
    if java.nio.file.Files.exists(fullPath) then
      new String(java.nio.file.Files.readAllBytes(fullPath))
    else ""

  // -----------------------------------------------------------------------
  // Individual grammar extraction
  // -----------------------------------------------------------------------

  test("sankey: extract 1 action with yy.findOrCreateNode + yy.addLink"):
    val source = loadJison("sankey/parser/sankey.jison")
    if source.isEmpty then
      println("SKIP: ssg original-src not found")
    else
      val summary = balticporter.corpus.mermaid.JisonActionExtractor.extract(source, "sankey")
      println(balticporter.corpus.mermaid.JisonActionExtractor.formatSummary(summary))
      assert(summary.dbCalls.nonEmpty, "should find db calls")
      assert(summary.dbMethodCounts.contains("findOrCreateNode"),
        s"should find findOrCreateNode, got: ${summary.dbMethodCounts.keys.mkString(", ")}")
      assert(summary.dbMethodCounts.contains("addLink"),
        s"should find addLink, got: ${summary.dbMethodCounts.keys.mkString(", ")}")

  test("er: extract entity and relationship actions"):
    val source = loadJison("er/parser/erDiagram.jison")
    if source.isEmpty then
      println("SKIP: ssg original-src not found")
    else
      val summary = balticporter.corpus.mermaid.JisonActionExtractor.extract(source, "er")
      println(balticporter.corpus.mermaid.JisonActionExtractor.formatSummary(summary))
      assert(summary.dbMethodCounts.contains("addEntity"),
        s"should find addEntity, got: ${summary.dbMethodCounts.keys.mkString(", ")}")
      assert(summary.dbMethodCounts.contains("addRelationship"),
        s"should find addRelationship")
      // ER has Cardinality and Identification enums
      assert(summary.constants.contains("Cardinality") || summary.constants.contains("Identification"),
        s"should find constants, got: ${summary.constants.keys.mkString(", ")}")

  test("flowchart: extract vertex and link actions"):
    val source = loadJison("flowchart/parser/flow.jison")
    if source.isEmpty then
      println("SKIP: ssg original-src not found")
    else
      val summary = balticporter.corpus.mermaid.JisonActionExtractor.extract(source, "flowchart")
      println(balticporter.corpus.mermaid.JisonActionExtractor.formatSummary(summary))
      assert(summary.dbMethodCounts.contains("addVertex"),
        s"should find addVertex, got: ${summary.dbMethodCounts.keys.mkString(", ")}")
      assert(summary.dbMethodCounts.contains("setDirection"),
        s"should find setDirection")
      assert(summary.productionCount >= 20,
        s"Expected >= 20 productions, got ${summary.productionCount}")

  test("sequence: extract signal and actor actions"):
    val source = loadJison("sequence/parser/sequenceDiagram.jison")
    if source.isEmpty then
      println("SKIP: ssg original-src not found")
    else
      val summary = balticporter.corpus.mermaid.JisonActionExtractor.extract(source, "sequence")
      println(balticporter.corpus.mermaid.JisonActionExtractor.formatSummary(summary))
      // Sequence diagram has LINETYPE constants
      assert(summary.constants.contains("LINETYPE"),
        s"should find LINETYPE constants, got: ${summary.constants.keys.mkString(", ")}")
      assert(summary.dbCalls.size >= 5,
        s"Expected >= 5 db calls, got ${summary.dbCalls.size}")

  // -----------------------------------------------------------------------
  // Batch analysis
  // -----------------------------------------------------------------------

  test("batch: analyze all 17 jison grammars"):
    if !java.nio.file.Files.exists(jisonRoot) then
      println("SKIP: ssg original-src not found at " + jisonRoot)
    else
      val summaries = balticporter.corpus.mermaid.JisonActionExtractor.analyzeAll(jisonRoot)
      println("\n=== Jison Grammar Analysis ===")
      println(balticporter.corpus.mermaid.JisonActionExtractor.formatCombinedTable(summaries))

      // Individual grammar details
      for s <- summaries do
        println(s"\n--- ${s.diagramType} ---")
        println(s"  Db calls: ${s.dbCalls.size}")
        println(s"  Methods: ${s.dbMethodCounts.keys.toList.sorted.mkString(", ")}")

      assert(summaries.size >= 15,
        s"Expected >= 15 jison files, got ${summaries.size}")

      // Verify we extract meaningful data from at least some grammars
      val totalDbCalls = summaries.map(_.dbCalls.size).sum
      assert(totalDbCalls >= 50,
        s"Expected >= 50 total db calls, got $totalDbCalls")

      // Count mapped vs unmapped methods
      val allMethods = summaries.flatMap(_.dbMethodCounts.keys).toSet
      val mapped = allMethods.filter(balticporter.corpus.mermaid.JisonActionExtractor.jisonToScalaMethodMap.contains)
      val unmapped = allMethods -- mapped
      println(s"\nMethod coverage: ${mapped.size}/${allMethods.size} mapped")
      if unmapped.nonEmpty then
        println(s"Unmapped methods: ${unmapped.toList.sorted.mkString(", ")}")

  // -----------------------------------------------------------------------
  // Scala call generation
  // -----------------------------------------------------------------------

  test("toScalaCall: translates yy.addVertex($1, $2) to db.addVertex(p1, p2)"):
    val call = balticporter.corpus.mermaid.JisonActionExtractor.DbCall(
      methodName = "addVertex",
      argCount = 2,
      rawArgs = List("$1", "$2"),
      production = "vertex: ID ID",
    )
    val scala = balticporter.corpus.mermaid.JisonActionExtractor.toScalaCall(call)
    assertEquals(scala, "db.addVertex(p1, p2)")

  test("toScalaCall: translates string literal args"):
    val call = balticporter.corpus.mermaid.JisonActionExtractor.DbCall(
      methodName = "setDirection",
      argCount = 1,
      rawArgs = List("'TB'"),
      production = "dir: DOWN",
    )
    val scala = balticporter.corpus.mermaid.JisonActionExtractor.toScalaCall(call)
    assertEquals(scala, """db.setDirection("TB")""")
