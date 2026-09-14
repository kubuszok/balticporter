package balticporter.frontend.ts

class MermaidB9EmitterSpec extends munit.FunSuite:

  private def loadRast(resource: String): RastFile =
    val stream = getClass.getResourceAsStream(resource)
    assert(stream != null, s"Resource not found: $resource")
    val json = new String(stream.readAllBytes(), java.nio.charset.StandardCharsets.UTF_8)
    stream.close()
    Rast.readFile(json)

  // Use a dummy RAST for emitters that do not read from it
  private lazy val dummyRast = loadRast("/rast/mermaid/src/diagrams/info/infoDb.rast.json")

  // -- Quadrant ---------------------------------------------------------------

  test("quadrant -> QuadrantDb class"):
    val scala = balticporter.corpus.mermaid.MermaidB9Emitter.emitQuadrantDb(dummyRast)
    assert(scala.contains("final class QuadrantDb"), "should emit QuadrantDb class")
    assert(scala.contains("QuadrantPoint"), "should have QuadrantPoint case class")
    assert(scala.contains("quadrantLabels"), "should have quadrantLabels")
    assert(scala.contains("def clear()"), "should have clear method")
    assert(scala.contains("def addPoint"), "should have addPoint method")

  test("quadrant -> QuadrantDiagram facade"):
    val scala = balticporter.corpus.mermaid.MermaidB9Emitter.emitQuadrantDiagram(dummyRast)
    assert(scala.contains("object QuadrantDiagram"), "should emit QuadrantDiagram")
    assert(scala.contains("def detect"), "should have detect method")
    assert(scala.contains("QuadrantParser"), "should reference parser")
    assert(scala.contains("QuadrantRenderer"), "should reference renderer")

  test("quadrant -> QuadrantParser"):
    val scala = balticporter.corpus.mermaid.MermaidB9Emitter.emitQuadrantParser(dummyRast)
    assert(scala.contains("object QuadrantParser"), "should emit QuadrantParser")
    assert(scala.contains("def parse"), "should have parse method")
    assert(scala.contains("quadrant-1"), "should parse quadrant labels")
    assert(scala.contains("x-axis"), "should parse x-axis")

  test("quadrant -> QuadrantRenderer"):
    val scala = balticporter.corpus.mermaid.MermaidB9Emitter.emitQuadrantRenderer(dummyRast)
    assert(scala.contains("object QuadrantRenderer"), "should emit QuadrantRenderer")
    assert(scala.contains("SvgBuilder"), "should use SvgBuilder")
    assert(scala.contains("Accessibility.applyTo"), "should apply a11y")
    assert(scala.contains("quadrantArea"), "should have quadrant area class")
    assert(scala.contains("quadrantPoint"), "should have point class")
    assert(scala.contains("svg.build().toMarkup()"), "should end with toMarkup")

  test("quadrant -> QuadrantStyles"):
    val scala = balticporter.corpus.mermaid.MermaidB9Emitter.emitQuadrantStyles(dummyRast)
    assert(scala.contains("object QuadrantStyles"), "should emit QuadrantStyles")
    assert(scala.contains("def generate(vars: ThemeVariables)"), "should have generate")
    assert(scala.contains("quadrantTitleText"), "should have title CSS")
    assert(scala.contains("quadrantPoint"), "should have point CSS")

  // -- XY Chart ---------------------------------------------------------------

  test("xychart -> XyChartDb class"):
    val scala = balticporter.corpus.mermaid.MermaidB9Emitter.emitXyChartDb(dummyRast)
    assert(scala.contains("final class XyChartDb"), "should emit XyChartDb class")
    assert(scala.contains("DataSeries"), "should have DataSeries case class")
    assert(scala.contains("dataSeries"), "should have dataSeries")
    assert(scala.contains("def addBarData"), "should have addBarData")
    assert(scala.contains("def addLineData"), "should have addLineData")

  test("xychart -> XyChartDiagram facade"):
    val scala = balticporter.corpus.mermaid.MermaidB9Emitter.emitXyChartDiagram(dummyRast)
    assert(scala.contains("object XyChartDiagram"), "should emit XyChartDiagram")
    assert(scala.contains("xychart"), "should detect xychart")
    assert(scala.contains("XyChartParser"), "should reference parser")
    assert(scala.contains("XyChartRenderer"), "should reference renderer")

  test("xychart -> XyChartParser"):
    val scala = balticporter.corpus.mermaid.MermaidB9Emitter.emitXyChartParser(dummyRast)
    assert(scala.contains("object XyChartParser"), "should emit XyChartParser")
    assert(scala.contains("bar "), "should parse bar data")
    assert(scala.contains("line "), "should parse line data")
    assert(scala.contains("x-axis"), "should parse x-axis")

  test("xychart -> XyChartRenderer"):
    val scala = balticporter.corpus.mermaid.MermaidB9Emitter.emitXyChartRenderer(dummyRast)
    assert(scala.contains("object XyChartRenderer"), "should emit XyChartRenderer")
    assert(scala.contains("SvgBuilder"), "should use SvgBuilder")
    assert(scala.contains("xyChartBar"), "should have bar class")
    assert(scala.contains("xyChartLine"), "should have line class")
    assert(scala.contains("polyline"), "should draw polyline for line charts")

  test("xychart -> XyChartStyles"):
    val scala = balticporter.corpus.mermaid.MermaidB9Emitter.emitXyChartStyles(dummyRast)
    assert(scala.contains("object XyChartStyles"), "should emit XyChartStyles")
    assert(scala.contains("xyChartBar"), "should have bar CSS")
    assert(scala.contains("xyChartLine"), "should have line CSS")

  // -- Journey ----------------------------------------------------------------

  test("journey -> JourneyDb class"):
    val scala = balticporter.corpus.mermaid.MermaidB9Emitter.emitJourneyDb(dummyRast)
    assert(scala.contains("final class JourneyDb"), "should emit JourneyDb class")
    assert(scala.contains("JourneyTask"), "should have JourneyTask")
    assert(scala.contains("JourneySection"), "should have JourneySection")
    assert(scala.contains("def addSection"), "should have addSection")
    assert(scala.contains("def addTask"), "should have addTask")

  test("journey -> JourneyDiagram facade"):
    val scala = balticporter.corpus.mermaid.MermaidB9Emitter.emitJourneyDiagram(dummyRast)
    assert(scala.contains("object JourneyDiagram"), "should emit JourneyDiagram")
    assert(scala.contains("journey"), "should detect journey")

  test("journey -> JourneyParser"):
    val scala = balticporter.corpus.mermaid.MermaidB9Emitter.emitJourneyParser(dummyRast)
    assert(scala.contains("object JourneyParser"), "should emit JourneyParser")
    assert(scala.contains("section "), "should parse sections")

  test("journey -> JourneyRenderer"):
    val scala = balticporter.corpus.mermaid.MermaidB9Emitter.emitJourneyRenderer(dummyRast)
    assert(scala.contains("object JourneyRenderer"), "should emit JourneyRenderer")
    assert(scala.contains("SvgBuilder"), "should use SvgBuilder")
    assert(scala.contains("journeyTask"), "should have task class")
    assert(scala.contains("scoreColor"), "should have scoreColor helper")
    assert(scala.contains("renderTask"), "should have renderTask helper")

  test("journey -> JourneyStyles"):
    val scala = balticporter.corpus.mermaid.MermaidB9Emitter.emitJourneyStyles(dummyRast)
    assert(scala.contains("object JourneyStyles"), "should emit JourneyStyles")
    assert(scala.contains("journeyTitle"), "should have title CSS")
    assert(scala.contains("journeyTask"), "should have task CSS")

  // -- Timeline ---------------------------------------------------------------

  test("timeline -> TimelineDb class"):
    val scala = balticporter.corpus.mermaid.MermaidB9Emitter.emitTimelineDb(dummyRast)
    assert(scala.contains("final class TimelineDb"), "should emit TimelineDb class")
    assert(scala.contains("TimelinePeriod"), "should have TimelinePeriod")
    assert(scala.contains("TimelineSection"), "should have TimelineSection")
    assert(scala.contains("def addPeriod"), "should have addPeriod")

  test("timeline -> TimelineDiagram facade"):
    val scala = balticporter.corpus.mermaid.MermaidB9Emitter.emitTimelineDiagram(dummyRast)
    assert(scala.contains("object TimelineDiagram"), "should emit TimelineDiagram")
    assert(scala.contains("timeline"), "should detect timeline")

  test("timeline -> TimelineParser"):
    val scala = balticporter.corpus.mermaid.MermaidB9Emitter.emitTimelineParser(dummyRast)
    assert(scala.contains("object TimelineParser"), "should emit TimelineParser")
    assert(scala.contains("section "), "should parse sections")

  test("timeline -> TimelineRenderer"):
    val scala = balticporter.corpus.mermaid.MermaidB9Emitter.emitTimelineRenderer(dummyRast)
    assert(scala.contains("object TimelineRenderer"), "should emit TimelineRenderer")
    assert(scala.contains("SvgBuilder"), "should use SvgBuilder")
    assert(scala.contains("timelineMarker"), "should have marker class")
    assert(scala.contains("timelinePeriodTitle"), "should have period title")

  test("timeline -> TimelineStyles"):
    val scala = balticporter.corpus.mermaid.MermaidB9Emitter.emitTimelineStyles(dummyRast)
    assert(scala.contains("object TimelineStyles"), "should emit TimelineStyles")
    assert(scala.contains("timelineLine"), "should have line CSS")
    assert(scala.contains("timelineMarker"), "should have marker CSS")

  // -- C4 ---------------------------------------------------------------------

  test("c4 -> C4Db class"):
    val scala = balticporter.corpus.mermaid.MermaidB9Emitter.emitC4Db(dummyRast)
    assert(scala.contains("final class C4Db"), "should emit C4Db class")
    assert(scala.contains("C4Entity"), "should have C4Entity")
    assert(scala.contains("C4Relationship"), "should have C4Relationship")
    assert(scala.contains("C4Boundary"), "should have C4Boundary")
    assert(scala.contains("def pushBoundary"), "should have pushBoundary")

  test("c4 -> C4Diagram facade"):
    val scala = balticporter.corpus.mermaid.MermaidB9Emitter.emitC4Diagram(dummyRast)
    assert(scala.contains("object C4Diagram"), "should emit C4Diagram")
    assert(scala.contains("C4Context"), "should detect C4Context")
    assert(scala.contains("C4Container"), "should detect C4Container")

  test("c4 -> C4Parser"):
    val scala = balticporter.corpus.mermaid.MermaidB9Emitter.emitC4Parser(dummyRast)
    assert(scala.contains("object C4Parser"), "should emit C4Parser")
    assert(scala.contains("Person("), "should parse Person")
    assert(scala.contains("System("), "should parse System")
    assert(scala.contains("Rel("), "should parse Rel")

  test("c4 -> C4Renderer"):
    val scala = balticporter.corpus.mermaid.MermaidB9Emitter.emitC4Renderer(dummyRast)
    assert(scala.contains("object C4Renderer"), "should emit C4Renderer")
    assert(scala.contains("SvgBuilder"), "should use SvgBuilder")
    assert(scala.contains("c4Box"), "should have box class")
    assert(scala.contains("c4Person"), "should have person class")

  test("c4 -> C4Styles"):
    val scala = balticporter.corpus.mermaid.MermaidB9Emitter.emitC4Styles(dummyRast)
    assert(scala.contains("object C4Styles"), "should emit C4Styles")
    assert(scala.contains("c4Box"), "should have box CSS")
    assert(scala.contains("c4Rel"), "should have rel CSS")

  // -- Git graph --------------------------------------------------------------

  test("git -> GitDb class"):
    val scala = balticporter.corpus.mermaid.MermaidB9Emitter.emitGitDb(dummyRast)
    assert(scala.contains("final class GitDb"), "should emit GitDb class")
    assert(scala.contains("GitCommit"), "should have GitCommit")
    assert(scala.contains("GitBranch"), "should have GitBranch")
    assert(scala.contains("CommitType"), "should have CommitType enum")
    assert(scala.contains("def commit"), "should have commit method")
    assert(scala.contains("def branch"), "should have branch method")
    assert(scala.contains("def merge"), "should have merge method")

  test("git -> GitDiagram facade"):
    val scala = balticporter.corpus.mermaid.MermaidB9Emitter.emitGitDiagram(dummyRast)
    assert(scala.contains("object GitDiagram"), "should emit GitDiagram")
    assert(scala.contains("gitgraph"), "should detect gitgraph")

  test("git -> GitParser"):
    val scala = balticporter.corpus.mermaid.MermaidB9Emitter.emitGitParser(dummyRast)
    assert(scala.contains("object GitParser"), "should emit GitParser")
    assert(scala.contains("commit"), "should parse commit")
    assert(scala.contains("branch "), "should parse branch")
    assert(scala.contains("checkout "), "should parse checkout")
    assert(scala.contains("merge "), "should parse merge")

  test("git -> GitRenderer"):
    val scala = balticporter.corpus.mermaid.MermaidB9Emitter.emitGitRenderer(dummyRast)
    assert(scala.contains("object GitRenderer"), "should emit GitRenderer")
    assert(scala.contains("SvgBuilder"), "should use SvgBuilder")
    assert(scala.contains("gitCommit"), "should have commit class")
    assert(scala.contains("gitBranchLine"), "should have branch line class")
    assert(scala.contains("BranchColors"), "should have branch colors")
    assert(scala.contains("isVertical"), "should handle direction")

  test("git -> GitStyles"):
    val scala = balticporter.corpus.mermaid.MermaidB9Emitter.emitGitStyles(dummyRast)
    assert(scala.contains("object GitStyles"), "should emit GitStyles")
    assert(scala.contains("gitCommit"), "should have commit CSS")
    assert(scala.contains("gitBranchLabel"), "should have branch label CSS")

  // -- Write all emitted files ------------------------------------------------

  test("write complete B9 diagram files"):
    val outDir = java.nio.file.Path.of(sys.props.getOrElse("user.dir", ".")).resolve("target/emitted-mermaid-b9")
    java.nio.file.Files.createDirectories(outDir)

    val allFiles = List(
      ("QuadrantDb", balticporter.corpus.mermaid.MermaidB9Emitter.emitQuadrantDb(dummyRast)),
      ("QuadrantDiagram", balticporter.corpus.mermaid.MermaidB9Emitter.emitQuadrantDiagram(dummyRast)),
      ("QuadrantParser", balticporter.corpus.mermaid.MermaidB9Emitter.emitQuadrantParser(dummyRast)),
      ("QuadrantRenderer", balticporter.corpus.mermaid.MermaidB9Emitter.emitQuadrantRenderer(dummyRast)),
      ("QuadrantStyles", balticporter.corpus.mermaid.MermaidB9Emitter.emitQuadrantStyles(dummyRast)),
      ("XyChartDb", balticporter.corpus.mermaid.MermaidB9Emitter.emitXyChartDb(dummyRast)),
      ("XyChartDiagram", balticporter.corpus.mermaid.MermaidB9Emitter.emitXyChartDiagram(dummyRast)),
      ("XyChartParser", balticporter.corpus.mermaid.MermaidB9Emitter.emitXyChartParser(dummyRast)),
      ("XyChartRenderer", balticporter.corpus.mermaid.MermaidB9Emitter.emitXyChartRenderer(dummyRast)),
      ("XyChartStyles", balticporter.corpus.mermaid.MermaidB9Emitter.emitXyChartStyles(dummyRast)),
      ("JourneyDb", balticporter.corpus.mermaid.MermaidB9Emitter.emitJourneyDb(dummyRast)),
      ("JourneyDiagram", balticporter.corpus.mermaid.MermaidB9Emitter.emitJourneyDiagram(dummyRast)),
      ("JourneyParser", balticporter.corpus.mermaid.MermaidB9Emitter.emitJourneyParser(dummyRast)),
      ("JourneyRenderer", balticporter.corpus.mermaid.MermaidB9Emitter.emitJourneyRenderer(dummyRast)),
      ("JourneyStyles", balticporter.corpus.mermaid.MermaidB9Emitter.emitJourneyStyles(dummyRast)),
      ("TimelineDb", balticporter.corpus.mermaid.MermaidB9Emitter.emitTimelineDb(dummyRast)),
      ("TimelineDiagram", balticporter.corpus.mermaid.MermaidB9Emitter.emitTimelineDiagram(dummyRast)),
      ("TimelineParser", balticporter.corpus.mermaid.MermaidB9Emitter.emitTimelineParser(dummyRast)),
      ("TimelineRenderer", balticporter.corpus.mermaid.MermaidB9Emitter.emitTimelineRenderer(dummyRast)),
      ("TimelineStyles", balticporter.corpus.mermaid.MermaidB9Emitter.emitTimelineStyles(dummyRast)),
      ("C4Db", balticporter.corpus.mermaid.MermaidB9Emitter.emitC4Db(dummyRast)),
      ("C4Diagram", balticporter.corpus.mermaid.MermaidB9Emitter.emitC4Diagram(dummyRast)),
      ("C4Parser", balticporter.corpus.mermaid.MermaidB9Emitter.emitC4Parser(dummyRast)),
      ("C4Renderer", balticporter.corpus.mermaid.MermaidB9Emitter.emitC4Renderer(dummyRast)),
      ("C4Styles", balticporter.corpus.mermaid.MermaidB9Emitter.emitC4Styles(dummyRast)),
      ("GitDb", balticporter.corpus.mermaid.MermaidB9Emitter.emitGitDb(dummyRast)),
      ("GitDiagram", balticporter.corpus.mermaid.MermaidB9Emitter.emitGitDiagram(dummyRast)),
      ("GitParser", balticporter.corpus.mermaid.MermaidB9Emitter.emitGitParser(dummyRast)),
      ("GitRenderer", balticporter.corpus.mermaid.MermaidB9Emitter.emitGitRenderer(dummyRast)),
      ("GitStyles", balticporter.corpus.mermaid.MermaidB9Emitter.emitGitStyles(dummyRast))
    )

    for ((name, source) <- allFiles) {
      val path = outDir.resolve(s"$name.scala")
      java.nio.file.Files.writeString(path, source)
      println(s"[emit] $name.scala: ${source.linesIterator.size} lines -> $path")
    }
    println(s"[emit] Total: ${allFiles.size} B9 diagram files written")
