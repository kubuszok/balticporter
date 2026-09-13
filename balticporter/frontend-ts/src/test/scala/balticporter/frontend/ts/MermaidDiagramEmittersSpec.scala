package balticporter.frontend.ts

class MermaidDiagramEmittersSpec extends munit.FunSuite:

  private def loadRast(resource: String): RastFile =
    val stream = getClass.getResourceAsStream(resource)
    assert(stream != null, s"Resource not found: $resource")
    val json = new String(stream.readAllBytes(), java.nio.charset.StandardCharsets.UTF_8)
    stream.close()
    Rast.readFile(json)

  // Use a dummy RAST since these diagrams don't have dedicated RAST files
  private lazy val dummyRast = loadRast("/rast/mermaid/src/diagrams/info/infoDb.rast.json")

  // ===========================================================================
  // 1. Treemap
  // ===========================================================================

  test("treemap -> TreemapDb class"):
    val scala = dedicated.MermaidDiagramEmitters.emitTreemapDb(dummyRast)
    assert(scala.contains("final case class TreemapNode"), "should have TreemapNode case class")
    assert(scala.contains("label: String"), "should have label field")
    assert(scala.contains("value: Double"), "should have value field")
    assert(scala.contains("children: mutable.ArrayBuffer[TreemapNode]"), "should have children field")
    assert(scala.contains("final class TreemapDb"), "should have TreemapDb class")
    assert(scala.contains("val roots"), "should have roots collection")
    assert(scala.contains("def flattenLeaves"), "should have flattenLeaves helper")
    assert(scala.contains("def clear()"), "should have clear method")

  test("treemap -> TreemapDiagram facade"):
    val scala = dedicated.MermaidDiagramEmitters.emitTreemapDiagram(dummyRast)
    assert(scala.contains("object TreemapDiagram"), "should emit TreemapDiagram object")
    assert(scala.contains("def detect(text: String): Boolean"), "should have detect method")
    assert(scala.contains("\"treemap\""), "should detect treemap keyword")
    assert(scala.contains("TreemapParser.parse"), "should delegate to TreemapParser")
    assert(scala.contains("TreemapRenderer.render"), "should delegate to TreemapRenderer")

  test("treemap -> TreemapParser"):
    val scala = dedicated.MermaidDiagramEmitters.emitTreemapParser(dummyRast)
    assert(scala.contains("object TreemapParser"), "should emit TreemapParser object")
    assert(scala.contains("def parse(input: String): TreemapDb"), "should have parse method")
    assert(scala.contains("\"treemap\""), "should parse treemap keyword")
    assert(scala.contains("parseLabelValue"), "should have parseLabelValue helper")

  test("treemap -> TreemapRenderer"):
    val scala = dedicated.MermaidDiagramEmitters.emitTreemapRenderer(dummyRast)
    assert(scala.contains("object TreemapRenderer"), "should emit TreemapRenderer object")
    assert(scala.contains("def render(db: TreemapDb, config: MermaidConfig)"), "should accept TreemapDb")
    assert(scala.contains("SvgBuilder.createSvg"), "should create SVG")
    assert(scala.contains("Accessibility.applyTo"), "should apply accessibility")
    assert(scala.contains("treemapCell"), "should have treemapCell CSS class")
    assert(scala.contains("treemapLabel"), "should have treemapLabel CSS class")
    assert(scala.contains("Colors"), "should have Colors array")
    assert(scala.contains(".build().toMarkup()"), "should produce SVG output")

  test("treemap -> TreemapStyles"):
    val scala = dedicated.MermaidDiagramEmitters.emitTreemapStyles(dummyRast)
    assert(scala.contains("object TreemapStyles"), "should emit TreemapStyles object")
    assert(scala.contains("def generate(vars: ThemeVariables)"), "should have generate method")
    assert(scala.contains("treemapTitle"), "should have treemapTitle style")
    assert(scala.contains("treemapCell"), "should have treemapCell style")
    assert(scala.contains("treemapLabel"), "should have treemapLabel style")

  // ===========================================================================
  // 2. Event Modeling
  // ===========================================================================

  test("eventmodeling -> EventModelingDb class"):
    val scala = dedicated.MermaidDiagramEmitters.emitEventModelingDb(dummyRast)
    assert(scala.contains("final case class EmEvent"), "should have EmEvent case class")
    assert(scala.contains("final case class EmFlow"), "should have EmFlow case class")
    assert(scala.contains("final class EventModelingDb"), "should have EventModelingDb class")
    assert(scala.contains("val lanes"), "should have lanes collection")
    assert(scala.contains("val events"), "should have events collection")
    assert(scala.contains("val flows"), "should have flows collection")
    assert(scala.contains("def addEvent"), "should have addEvent method")
    assert(scala.contains("def addFlow"), "should have addFlow method")
    assert(scala.contains("def clear()"), "should have clear method")

  test("eventmodeling -> EventModelingDiagram facade"):
    val scala = dedicated.MermaidDiagramEmitters.emitEventModelingDiagram(dummyRast)
    assert(scala.contains("object EventModelingDiagram"), "should emit EventModelingDiagram object")
    assert(scala.contains("def detect(text: String): Boolean"), "should have detect method")
    assert(scala.contains("\"eventmodeling\""), "should detect eventmodeling keyword")
    assert(scala.contains("EventModelingParser.parse"), "should delegate to EventModelingParser")
    assert(scala.contains("EventModelingRenderer.render"), "should delegate to EventModelingRenderer")

  test("eventmodeling -> EventModelingParser"):
    val scala = dedicated.MermaidDiagramEmitters.emitEventModelingParser(dummyRast)
    assert(scala.contains("object EventModelingParser"), "should emit EventModelingParser object")
    assert(scala.contains("def parse(input: String): EventModelingDb"), "should have parse method")
    assert(scala.contains("\"eventmodeling\""), "should parse eventmodeling keyword")
    assert(scala.contains("parseEvent"), "should have parseEvent helper")

  test("eventmodeling -> EventModelingRenderer"):
    val scala = dedicated.MermaidDiagramEmitters.emitEventModelingRenderer(dummyRast)
    assert(scala.contains("object EventModelingRenderer"), "should emit EventModelingRenderer object")
    assert(scala.contains("def render(db: EventModelingDb, config: MermaidConfig)"), "should accept EventModelingDb")
    assert(scala.contains("SvgBuilder.createSvg"), "should create SVG")
    assert(scala.contains("Accessibility.applyTo"), "should apply accessibility")
    assert(scala.contains("em-arrow"), "should have em-arrow marker")
    assert(scala.contains("emEvent"), "should have emEvent CSS class")
    assert(scala.contains("emFlow"), "should have emFlow CSS class")
    assert(scala.contains(".build().toMarkup()"), "should produce SVG output")

  test("eventmodeling -> EventModelingStyles"):
    val scala = dedicated.MermaidDiagramEmitters.emitEventModelingStyles(dummyRast)
    assert(scala.contains("object EventModelingStyles"), "should emit EventModelingStyles object")
    assert(scala.contains("def generate(vars: ThemeVariables)"), "should have generate method")
    assert(scala.contains("emTitle"), "should have emTitle style")
    assert(scala.contains("emEvent"), "should have emEvent style")
    assert(scala.contains("emFlow"), "should have emFlow style")

  // ===========================================================================
  // 3. Radar
  // ===========================================================================

  test("radar -> RadarDb class"):
    val scala = dedicated.MermaidDiagramEmitters.emitRadarDb(dummyRast)
    assert(scala.contains("final case class RadarSeries"), "should have RadarSeries case class")
    assert(scala.contains("final class RadarDb"), "should have RadarDb class")
    assert(scala.contains("val axes"), "should have axes collection")
    assert(scala.contains("val series"), "should have series collection")
    assert(scala.contains("def addAxis"), "should have addAxis method")
    assert(scala.contains("def addSeries"), "should have addSeries method")
    assert(scala.contains("def clear()"), "should have clear method")

  test("radar -> RadarDiagram facade"):
    val scala = dedicated.MermaidDiagramEmitters.emitRadarDiagram(dummyRast)
    assert(scala.contains("object RadarDiagram"), "should emit RadarDiagram object")
    assert(scala.contains("def detect(text: String): Boolean"), "should have detect method")
    assert(scala.contains("\"radar-beta\""), "should detect radar-beta keyword")
    assert(scala.contains("RadarParser.parse"), "should delegate to RadarParser")
    assert(scala.contains("RadarRenderer.render"), "should delegate to RadarRenderer")

  test("radar -> RadarParser"):
    val scala = dedicated.MermaidDiagramEmitters.emitRadarParser(dummyRast)
    assert(scala.contains("object RadarParser"), "should emit RadarParser object")
    assert(scala.contains("def parse(input: String): RadarDb"), "should have parse method")
    assert(scala.contains("\"radar-beta\""), "should parse radar-beta keyword")

  test("radar -> RadarRenderer"):
    val scala = dedicated.MermaidDiagramEmitters.emitRadarRenderer(dummyRast)
    assert(scala.contains("object RadarRenderer"), "should emit RadarRenderer object")
    assert(scala.contains("def render(db: RadarDb, config: MermaidConfig)"), "should accept RadarDb")
    assert(scala.contains("SvgBuilder.createSvg"), "should create SVG")
    assert(scala.contains("Accessibility.applyTo"), "should apply accessibility")
    assert(scala.contains("radarGrid"), "should have radarGrid CSS class")
    assert(scala.contains("radarAxis"), "should have radarAxis CSS class")
    assert(scala.contains("radarSeries"), "should have radarSeries CSS class")
    assert(scala.contains("radarDot"), "should have radarDot CSS class")
    assert(scala.contains(".build().toMarkup()"), "should produce SVG output")

  test("radar -> RadarStyles"):
    val scala = dedicated.MermaidDiagramEmitters.emitRadarStyles(dummyRast)
    assert(scala.contains("object RadarStyles"), "should emit RadarStyles object")
    assert(scala.contains("def generate(vars: ThemeVariables)"), "should have generate method")
    assert(scala.contains("radarTitle"), "should have radarTitle style")
    assert(scala.contains("radarGrid"), "should have radarGrid style")
    assert(scala.contains("radarAxis"), "should have radarAxis style")

  // ===========================================================================
  // 4. Architecture
  // ===========================================================================

  test("architecture -> ArchitectureDb class"):
    val scala = dedicated.MermaidDiagramEmitters.emitArchitectureDb(dummyRast)
    assert(scala.contains("final case class ArchNode"), "should have ArchNode case class")
    assert(scala.contains("final case class ArchEdge"), "should have ArchEdge case class")
    assert(scala.contains("final class ArchitectureDb"), "should have ArchitectureDb class")
    assert(scala.contains("val nodes"), "should have nodes collection")
    assert(scala.contains("val edges"), "should have edges collection")
    assert(scala.contains("val groups"), "should have groups collection")
    assert(scala.contains("def addService"), "should have addService method")
    assert(scala.contains("def addJunction"), "should have addJunction method")
    assert(scala.contains("def addEdge"), "should have addEdge method")
    assert(scala.contains("def clear()"), "should have clear method")

  test("architecture -> ArchitectureDiagram facade"):
    val scala = dedicated.MermaidDiagramEmitters.emitArchitectureDiagram(dummyRast)
    assert(scala.contains("object ArchitectureDiagram"), "should emit ArchitectureDiagram object")
    assert(scala.contains("def detect(text: String): Boolean"), "should have detect method")
    assert(scala.contains("\"architecture-beta\""), "should detect architecture-beta keyword")
    assert(scala.contains("ArchitectureParser.parse"), "should delegate to ArchitectureParser")
    assert(scala.contains("ArchitectureRenderer.render"), "should delegate to ArchitectureRenderer")

  test("architecture -> ArchitectureParser"):
    val scala = dedicated.MermaidDiagramEmitters.emitArchitectureParser(dummyRast)
    assert(scala.contains("object ArchitectureParser"), "should emit ArchitectureParser object")
    assert(scala.contains("def parse(input: String): ArchitectureDb"), "should have parse method")
    assert(scala.contains("\"architecture-beta\""), "should parse architecture-beta keyword")
    assert(scala.contains("service"), "should parse service keyword")
    assert(scala.contains("junction"), "should parse junction keyword")

  test("architecture -> ArchitectureRenderer"):
    val scala = dedicated.MermaidDiagramEmitters.emitArchitectureRenderer(dummyRast)
    assert(scala.contains("object ArchitectureRenderer"), "should emit ArchitectureRenderer object")
    assert(scala.contains("def render(db: ArchitectureDb, config: MermaidConfig)"), "should accept ArchitectureDb")
    assert(scala.contains("SvgBuilder.createSvg"), "should create SVG")
    assert(scala.contains("Accessibility.applyTo"), "should apply accessibility")
    assert(scala.contains("arch-arrowhead"), "should have arch-arrowhead marker")
    assert(scala.contains("archService"), "should have archService CSS class")
    assert(scala.contains("archJunction"), "should have archJunction CSS class")
    assert(scala.contains("archEdge"), "should have archEdge CSS class")
    assert(scala.contains(".build().toMarkup()"), "should produce SVG output")

  test("architecture -> ArchitectureStyles"):
    val scala = dedicated.MermaidDiagramEmitters.emitArchitectureStyles(dummyRast)
    assert(scala.contains("object ArchitectureStyles"), "should emit ArchitectureStyles object")
    assert(scala.contains("def generate(vars: ThemeVariables)"), "should have generate method")
    assert(scala.contains("archService"), "should have archService style")
    assert(scala.contains("archJunction"), "should have archJunction style")
    assert(scala.contains("archEdge"), "should have archEdge style")

  // ===========================================================================
  // 5. Block
  // ===========================================================================

  test("block -> BlockDb class"):
    val scala = dedicated.MermaidDiagramEmitters.emitBlockDb(dummyRast)
    assert(scala.contains("final case class BlockNode"), "should have BlockNode case class")
    assert(scala.contains("final case class BlockRow"), "should have BlockRow case class")
    assert(scala.contains("final class BlockDb"), "should have BlockDb class")
    assert(scala.contains("var columns"), "should have columns field")
    assert(scala.contains("val rows"), "should have rows collection")
    assert(scala.contains("val edges"), "should have edges collection")
    assert(scala.contains("def addBlock"), "should have addBlock method")
    assert(scala.contains("def addEdge"), "should have addEdge method")
    assert(scala.contains("def allBlocks"), "should have allBlocks method")
    assert(scala.contains("def clear()"), "should have clear method")

  test("block -> BlockDiagram facade"):
    val scala = dedicated.MermaidDiagramEmitters.emitBlockDiagram(dummyRast)
    assert(scala.contains("object BlockDiagram"), "should emit BlockDiagram object")
    assert(scala.contains("def detect(text: String): Boolean"), "should have detect method")
    assert(scala.contains("\"block-beta\""), "should detect block-beta keyword")
    assert(scala.contains("BlockParser.parse"), "should delegate to BlockParser")
    assert(scala.contains("BlockRenderer.render"), "should delegate to BlockRenderer")

  test("block -> BlockParser"):
    val scala = dedicated.MermaidDiagramEmitters.emitBlockParser(dummyRast)
    assert(scala.contains("object BlockParser"), "should emit BlockParser object")
    assert(scala.contains("def parse(input: String): BlockDb"), "should have parse method")
    assert(scala.contains("\"block-beta\""), "should parse block-beta keyword")
    assert(scala.contains("columns"), "should parse columns directive")

  test("block -> BlockRenderer"):
    val scala = dedicated.MermaidDiagramEmitters.emitBlockRenderer(dummyRast)
    assert(scala.contains("object BlockRenderer"), "should emit BlockRenderer object")
    assert(scala.contains("def render(db: BlockDb, config: MermaidConfig)"), "should accept BlockDb")
    assert(scala.contains("SvgBuilder.createSvg"), "should create SVG")
    assert(scala.contains("Accessibility.applyTo"), "should apply accessibility")
    assert(scala.contains("block-arrowhead"), "should have block-arrowhead marker")
    assert(scala.contains("blockBox"), "should have blockBox CSS class")
    assert(scala.contains("blockEdge"), "should have blockEdge CSS class")
    assert(scala.contains(".build().toMarkup()"), "should produce SVG output")

  test("block -> BlockStyles"):
    val scala = dedicated.MermaidDiagramEmitters.emitBlockStyles(dummyRast)
    assert(scala.contains("object BlockStyles"), "should emit BlockStyles object")
    assert(scala.contains("def generate(vars: ThemeVariables)"), "should have generate method")
    assert(scala.contains("blockBox"), "should have blockBox style")
    assert(scala.contains("blockEdge"), "should have blockEdge style")

  // ===========================================================================
  // 6. Journey
  // ===========================================================================

  test("journey -> JourneyDb class"):
    val scala = dedicated.MermaidDiagramEmitters.emitJourneyDb(dummyRast)
    assert(scala.contains("final case class JourneyTask"), "should have JourneyTask case class")
    assert(scala.contains("final case class JourneySection"), "should have JourneySection case class")
    assert(scala.contains("final class JourneyDb"), "should have JourneyDb class")
    assert(scala.contains("val tasks"), "should have tasks collection")
    assert(scala.contains("val sections"), "should have sections collection")
    assert(scala.contains("val actors"), "should have actors collection")
    assert(scala.contains("def addSection"), "should have addSection method")
    assert(scala.contains("def addTask"), "should have addTask method")
    assert(scala.contains("def clear()"), "should have clear method")

  test("journey -> JourneyDiagram facade"):
    val scala = dedicated.MermaidDiagramEmitters.emitJourneyDiagram(dummyRast)
    assert(scala.contains("object JourneyDiagram"), "should emit JourneyDiagram object")
    assert(scala.contains("def detect(text: String): Boolean"), "should have detect method")
    assert(scala.contains("\"journey\""), "should detect journey keyword")
    assert(scala.contains("JourneyParser.parse"), "should delegate to JourneyParser")
    assert(scala.contains("JourneyRenderer.render"), "should delegate to JourneyRenderer")

  test("journey -> JourneyParser"):
    val scala = dedicated.MermaidDiagramEmitters.emitJourneyParser(dummyRast)
    assert(scala.contains("object JourneyParser"), "should emit JourneyParser object")
    assert(scala.contains("def parse(input: String): JourneyDb"), "should have parse method")
    assert(scala.contains("\"journey\""), "should parse journey keyword")
    assert(scala.contains("parseTask"), "should have parseTask helper")
    assert(scala.contains("section"), "should parse section keyword")

  test("journey -> JourneyRenderer"):
    val scala = dedicated.MermaidDiagramEmitters.emitJourneyRenderer(dummyRast)
    assert(scala.contains("object JourneyRenderer"), "should emit JourneyRenderer object")
    assert(scala.contains("def render(db: JourneyDb, config: MermaidConfig)"), "should accept JourneyDb")
    assert(scala.contains("SvgBuilder.createSvg"), "should create SVG")
    assert(scala.contains("Accessibility.applyTo"), "should apply accessibility")
    assert(scala.contains("journeyTask"), "should have journeyTask CSS class")
    assert(scala.contains("journeySection"), "should have journeySection CSS class")
    assert(scala.contains("scoreColor"), "should have scoreColor helper")
    assert(scala.contains(".build().toMarkup()"), "should produce SVG output")

  test("journey -> JourneyStyles"):
    val scala = dedicated.MermaidDiagramEmitters.emitJourneyStyles(dummyRast)
    assert(scala.contains("object JourneyStyles"), "should emit JourneyStyles object")
    assert(scala.contains("def generate(vars: ThemeVariables)"), "should have generate method")
    assert(scala.contains("journeyTitle"), "should have journeyTitle style")
    assert(scala.contains("journeyTask"), "should have journeyTask style")
    assert(scala.contains("journeySection"), "should have journeySection style")
    assert(scala.contains("section-0"), "should have section-0 style")
    assert(scala.contains("section-1"), "should have section-1 style")

  // ===========================================================================
  // 7. Timeline
  // ===========================================================================

  test("timeline -> TimelineDb class"):
    val scala = dedicated.MermaidDiagramEmitters.emitTimelineDb(dummyRast)
    assert(scala.contains("final case class TimelinePeriod"), "should have TimelinePeriod case class")
    assert(scala.contains("final case class TimelineSection"), "should have TimelineSection case class")
    assert(scala.contains("final class TimelineDb"), "should have TimelineDb class")
    assert(scala.contains("val periods"), "should have periods collection")
    assert(scala.contains("val sections"), "should have sections collection")
    assert(scala.contains("def addPeriod"), "should have addPeriod method")
    assert(scala.contains("def addEventToLast"), "should have addEventToLast method")
    assert(scala.contains("def clear()"), "should have clear method")

  test("timeline -> TimelineDiagram facade"):
    val scala = dedicated.MermaidDiagramEmitters.emitTimelineDiagram(dummyRast)
    assert(scala.contains("object TimelineDiagram"), "should emit TimelineDiagram object")
    assert(scala.contains("def detect(text: String): Boolean"), "should have detect method")
    assert(scala.contains("\"timeline\""), "should detect timeline keyword")
    assert(scala.contains("TimelineParser.parse"), "should delegate to TimelineParser")
    assert(scala.contains("TimelineRenderer.render"), "should delegate to TimelineRenderer")

  test("timeline -> TimelineParser"):
    val scala = dedicated.MermaidDiagramEmitters.emitTimelineParser(dummyRast)
    assert(scala.contains("object TimelineParser"), "should emit TimelineParser object")
    assert(scala.contains("def parse(input: String): TimelineDb"), "should have parse method")
    assert(scala.contains("\"timeline\""), "should parse timeline keyword")

  test("timeline -> TimelineRenderer"):
    val scala = dedicated.MermaidDiagramEmitters.emitTimelineRenderer(dummyRast)
    assert(scala.contains("object TimelineRenderer"), "should emit TimelineRenderer object")
    assert(scala.contains("def render(db: TimelineDb, config: MermaidConfig)"), "should accept TimelineDb")
    assert(scala.contains("SvgBuilder.createSvg"), "should create SVG")
    assert(scala.contains("Accessibility.applyTo"), "should apply accessibility")
    assert(scala.contains("timelinePeriod"), "should have timelinePeriod CSS class")
    assert(scala.contains("timelineEvent"), "should have timelineEvent CSS class")
    assert(scala.contains("timelineLine"), "should have timelineLine CSS class")
    assert(scala.contains(".build().toMarkup()"), "should produce SVG output")

  test("timeline -> TimelineStyles"):
    val scala = dedicated.MermaidDiagramEmitters.emitTimelineStyles(dummyRast)
    assert(scala.contains("object TimelineStyles"), "should emit TimelineStyles object")
    assert(scala.contains("def generate(vars: ThemeVariables)"), "should have generate method")
    assert(scala.contains("timelineTitle"), "should have timelineTitle style")
    assert(scala.contains("timelinePeriod"), "should have timelinePeriod style")
    assert(scala.contains("timelineEvent"), "should have timelineEvent style")

  // ===========================================================================
  // 8. Mindmap
  // ===========================================================================

  test("mindmap -> MindmapDb class"):
    val scala = dedicated.MermaidDiagramEmitters.emitMindmapDb(dummyRast)
    assert(scala.contains("final case class MindmapNode"), "should have MindmapNode case class")
    assert(scala.contains("children: mutable.ArrayBuffer[MindmapNode]"), "should have children field")
    assert(scala.contains("final class MindmapDb"), "should have MindmapDb class")
    assert(scala.contains("var root"), "should have root field")
    assert(scala.contains("def setRoot"), "should have setRoot method")
    assert(scala.contains("def clear()"), "should have clear method")

  test("mindmap -> MindmapDiagram facade"):
    val scala = dedicated.MermaidDiagramEmitters.emitMindmapDiagram(dummyRast)
    assert(scala.contains("object MindmapDiagram"), "should emit MindmapDiagram object")
    assert(scala.contains("def detect(text: String): Boolean"), "should have detect method")
    assert(scala.contains("\"mindmap\""), "should detect mindmap keyword")
    assert(scala.contains("MindmapParser.parse"), "should delegate to MindmapParser")
    assert(scala.contains("MindmapRenderer.render"), "should delegate to MindmapRenderer")

  test("mindmap -> MindmapParser"):
    val scala = dedicated.MermaidDiagramEmitters.emitMindmapParser(dummyRast)
    assert(scala.contains("object MindmapParser"), "should emit MindmapParser object")
    assert(scala.contains("def parse(input: String): MindmapDb"), "should have parse method")
    assert(scala.contains("\"mindmap\""), "should parse mindmap keyword")
    assert(scala.contains("MindmapNode"), "should create MindmapNode")

  test("mindmap -> MindmapRenderer"):
    val scala = dedicated.MermaidDiagramEmitters.emitMindmapRenderer(dummyRast)
    assert(scala.contains("object MindmapRenderer"), "should emit MindmapRenderer object")
    assert(scala.contains("def render(db: MindmapDb, config: MermaidConfig)"), "should accept MindmapDb")
    assert(scala.contains("SvgBuilder.createSvg"), "should create SVG")
    assert(scala.contains("Accessibility.applyTo"), "should apply accessibility")
    assert(scala.contains("mindmapRoot"), "should have mindmapRoot CSS class")
    assert(scala.contains("mindmapNode"), "should have mindmapNode CSS class")
    assert(scala.contains("mindmapBranch"), "should have mindmapBranch CSS class")
    assert(scala.contains(".build().toMarkup()"), "should produce SVG output")

  test("mindmap -> MindmapStyles"):
    val scala = dedicated.MermaidDiagramEmitters.emitMindmapStyles(dummyRast)
    assert(scala.contains("object MindmapStyles"), "should emit MindmapStyles object")
    assert(scala.contains("def generate(vars: ThemeVariables)"), "should have generate method")
    assert(scala.contains("mindmapRoot"), "should have mindmapRoot style")
    assert(scala.contains("mindmapNode"), "should have mindmapNode style")
    assert(scala.contains("mindmapBranch"), "should have mindmapBranch style")

  // ===========================================================================
  // 9. Quadrant
  // ===========================================================================

  test("quadrant -> QuadrantDb class"):
    val scala = dedicated.MermaidDiagramEmitters.emitQuadrantDb(dummyRast)
    assert(scala.contains("final case class QuadrantPoint"), "should have QuadrantPoint case class")
    assert(scala.contains("final class QuadrantDb"), "should have QuadrantDb class")
    assert(scala.contains("var xAxisLeftLabel"), "should have xAxisLeftLabel field")
    assert(scala.contains("var xAxisRightLabel"), "should have xAxisRightLabel field")
    assert(scala.contains("var yAxisBottomLabel"), "should have yAxisBottomLabel field")
    assert(scala.contains("var yAxisTopLabel"), "should have yAxisTopLabel field")
    assert(scala.contains("val quadrantLabels"), "should have quadrantLabels array")
    assert(scala.contains("val points"), "should have points collection")
    assert(scala.contains("def addPoint"), "should have addPoint method")
    assert(scala.contains("def clear()"), "should have clear method")

  test("quadrant -> QuadrantDiagram facade"):
    val scala = dedicated.MermaidDiagramEmitters.emitQuadrantDiagram(dummyRast)
    assert(scala.contains("object QuadrantDiagram"), "should emit QuadrantDiagram object")
    assert(scala.contains("def detect(text: String): Boolean"), "should have detect method")
    assert(scala.contains("\"quadrant-chart\""), "should detect quadrant-chart keyword")
    assert(scala.contains("QuadrantParser.parse"), "should delegate to QuadrantParser")
    assert(scala.contains("QuadrantRenderer.render"), "should delegate to QuadrantRenderer")

  test("quadrant -> QuadrantParser"):
    val scala = dedicated.MermaidDiagramEmitters.emitQuadrantParser(dummyRast)
    assert(scala.contains("object QuadrantParser"), "should emit QuadrantParser object")
    assert(scala.contains("def parse(input: String): QuadrantDb"), "should have parse method")
    assert(scala.contains("\"quadrant-chart\""), "should parse quadrant-chart keyword")
    assert(scala.contains("x-axis"), "should parse x-axis")
    assert(scala.contains("y-axis"), "should parse y-axis")

  test("quadrant -> QuadrantRenderer"):
    val scala = dedicated.MermaidDiagramEmitters.emitQuadrantRenderer(dummyRast)
    assert(scala.contains("object QuadrantRenderer"), "should emit QuadrantRenderer object")
    assert(scala.contains("def render(db: QuadrantDb, config: MermaidConfig)"), "should accept QuadrantDb")
    assert(scala.contains("SvgBuilder.createSvg"), "should create SVG")
    assert(scala.contains("Accessibility.applyTo"), "should apply accessibility")
    assert(scala.contains("quadrantQ1"), "should have quadrantQ1 CSS class")
    assert(scala.contains("quadrantPoint"), "should have quadrantPoint CSS class")
    assert(scala.contains("quadrantAxisLabel"), "should have quadrantAxisLabel CSS class")
    assert(scala.contains(".build().toMarkup()"), "should produce SVG output")

  test("quadrant -> QuadrantStyles"):
    val scala = dedicated.MermaidDiagramEmitters.emitQuadrantStyles(dummyRast)
    assert(scala.contains("object QuadrantStyles"), "should emit QuadrantStyles object")
    assert(scala.contains("def generate(vars: ThemeVariables)"), "should have generate method")
    assert(scala.contains("quadrantTitle"), "should have quadrantTitle style")
    assert(scala.contains("quadrantQ1"), "should have quadrantQ1 style")
    assert(scala.contains("quadrantPoint"), "should have quadrantPoint style")

  // ===========================================================================
  // 10. Sankey
  // ===========================================================================

  test("sankey -> SankeyDb class"):
    val scala = dedicated.MermaidDiagramEmitters.emitSankeyDb(dummyRast)
    assert(scala.contains("final case class SankeyFlow"), "should have SankeyFlow case class")
    assert(scala.contains("final class SankeyDb"), "should have SankeyDb class")
    assert(scala.contains("val nodes"), "should have nodes as LinkedHashSet")
    assert(scala.contains("LinkedHashSet"), "should use LinkedHashSet for nodes")
    assert(scala.contains("val flows"), "should have flows collection")
    assert(scala.contains("def addFlow"), "should have addFlow method")
    assert(scala.contains("def clear()"), "should have clear method")

  test("sankey -> SankeyDiagram facade"):
    val scala = dedicated.MermaidDiagramEmitters.emitSankeyDiagram(dummyRast)
    assert(scala.contains("object SankeyDiagram"), "should emit SankeyDiagram object")
    assert(scala.contains("def detect(text: String): Boolean"), "should have detect method")
    assert(scala.contains("\"sankey-beta\""), "should detect sankey-beta keyword")
    assert(scala.contains("SankeyParser.parse"), "should delegate to SankeyParser")
    assert(scala.contains("SankeyRenderer.render"), "should delegate to SankeyRenderer")

  test("sankey -> SankeyParser"):
    val scala = dedicated.MermaidDiagramEmitters.emitSankeyParser(dummyRast)
    assert(scala.contains("object SankeyParser"), "should emit SankeyParser object")
    assert(scala.contains("def parse(input: String): SankeyDb"), "should have parse method")
    assert(scala.contains("\"sankey-beta\""), "should parse sankey-beta keyword")
    assert(scala.contains("addFlow"), "should add flows from CSV data")

  test("sankey -> SankeyRenderer"):
    val scala = dedicated.MermaidDiagramEmitters.emitSankeyRenderer(dummyRast)
    assert(scala.contains("object SankeyRenderer"), "should emit SankeyRenderer object")
    assert(scala.contains("def render(db: SankeyDb, config: MermaidConfig)"), "should accept SankeyDb")
    assert(scala.contains("SvgBuilder.createSvg"), "should create SVG")
    assert(scala.contains("Accessibility.applyTo"), "should apply accessibility")
    assert(scala.contains("sankeyNode"), "should have sankeyNode CSS class")
    assert(scala.contains("sankeyFlow"), "should have sankeyFlow CSS class")
    assert(scala.contains("sankeyLabel"), "should have sankeyLabel CSS class")
    assert(scala.contains(".build().toMarkup()"), "should produce SVG output")

  test("sankey -> SankeyStyles"):
    val scala = dedicated.MermaidDiagramEmitters.emitSankeyStyles(dummyRast)
    assert(scala.contains("object SankeyStyles"), "should emit SankeyStyles object")
    assert(scala.contains("def generate(vars: ThemeVariables)"), "should have generate method")
    assert(scala.contains("sankeyTitle"), "should have sankeyTitle style")
    assert(scala.contains("sankeyNode"), "should have sankeyNode style")
    assert(scala.contains("sankeyFlow"), "should have sankeyFlow style")

  // ===========================================================================
  // Write all files
  // ===========================================================================

  test("write all 10 diagram emitter files"):
    val outDir = java.nio.file.Path.of(sys.props.getOrElse("user.dir", ".")).resolve("target/emitted-mermaid-extra")
    java.nio.file.Files.createDirectories(outDir)

    val allFiles = List(
      ("TreemapDb",       dedicated.MermaidDiagramEmitters.emitTreemapDb(dummyRast)),
      ("TreemapDiagram",  dedicated.MermaidDiagramEmitters.emitTreemapDiagram(dummyRast)),
      ("TreemapParser",   dedicated.MermaidDiagramEmitters.emitTreemapParser(dummyRast)),
      ("TreemapRenderer", dedicated.MermaidDiagramEmitters.emitTreemapRenderer(dummyRast)),
      ("TreemapStyles",   dedicated.MermaidDiagramEmitters.emitTreemapStyles(dummyRast)),
      ("EventModelingDb",       dedicated.MermaidDiagramEmitters.emitEventModelingDb(dummyRast)),
      ("EventModelingDiagram",  dedicated.MermaidDiagramEmitters.emitEventModelingDiagram(dummyRast)),
      ("EventModelingParser",   dedicated.MermaidDiagramEmitters.emitEventModelingParser(dummyRast)),
      ("EventModelingRenderer", dedicated.MermaidDiagramEmitters.emitEventModelingRenderer(dummyRast)),
      ("EventModelingStyles",   dedicated.MermaidDiagramEmitters.emitEventModelingStyles(dummyRast)),
      ("RadarDb",       dedicated.MermaidDiagramEmitters.emitRadarDb(dummyRast)),
      ("RadarDiagram",  dedicated.MermaidDiagramEmitters.emitRadarDiagram(dummyRast)),
      ("RadarParser",   dedicated.MermaidDiagramEmitters.emitRadarParser(dummyRast)),
      ("RadarRenderer", dedicated.MermaidDiagramEmitters.emitRadarRenderer(dummyRast)),
      ("RadarStyles",   dedicated.MermaidDiagramEmitters.emitRadarStyles(dummyRast)),
      ("ArchitectureDb",       dedicated.MermaidDiagramEmitters.emitArchitectureDb(dummyRast)),
      ("ArchitectureDiagram",  dedicated.MermaidDiagramEmitters.emitArchitectureDiagram(dummyRast)),
      ("ArchitectureParser",   dedicated.MermaidDiagramEmitters.emitArchitectureParser(dummyRast)),
      ("ArchitectureRenderer", dedicated.MermaidDiagramEmitters.emitArchitectureRenderer(dummyRast)),
      ("ArchitectureStyles",   dedicated.MermaidDiagramEmitters.emitArchitectureStyles(dummyRast)),
      ("BlockDb",       dedicated.MermaidDiagramEmitters.emitBlockDb(dummyRast)),
      ("BlockDiagram",  dedicated.MermaidDiagramEmitters.emitBlockDiagram(dummyRast)),
      ("BlockParser",   dedicated.MermaidDiagramEmitters.emitBlockParser(dummyRast)),
      ("BlockRenderer", dedicated.MermaidDiagramEmitters.emitBlockRenderer(dummyRast)),
      ("BlockStyles",   dedicated.MermaidDiagramEmitters.emitBlockStyles(dummyRast)),
      ("JourneyDb",       dedicated.MermaidDiagramEmitters.emitJourneyDb(dummyRast)),
      ("JourneyDiagram",  dedicated.MermaidDiagramEmitters.emitJourneyDiagram(dummyRast)),
      ("JourneyParser",   dedicated.MermaidDiagramEmitters.emitJourneyParser(dummyRast)),
      ("JourneyRenderer", dedicated.MermaidDiagramEmitters.emitJourneyRenderer(dummyRast)),
      ("JourneyStyles",   dedicated.MermaidDiagramEmitters.emitJourneyStyles(dummyRast)),
      ("TimelineDb",       dedicated.MermaidDiagramEmitters.emitTimelineDb(dummyRast)),
      ("TimelineDiagram",  dedicated.MermaidDiagramEmitters.emitTimelineDiagram(dummyRast)),
      ("TimelineParser",   dedicated.MermaidDiagramEmitters.emitTimelineParser(dummyRast)),
      ("TimelineRenderer", dedicated.MermaidDiagramEmitters.emitTimelineRenderer(dummyRast)),
      ("TimelineStyles",   dedicated.MermaidDiagramEmitters.emitTimelineStyles(dummyRast)),
      ("MindmapDb",       dedicated.MermaidDiagramEmitters.emitMindmapDb(dummyRast)),
      ("MindmapDiagram",  dedicated.MermaidDiagramEmitters.emitMindmapDiagram(dummyRast)),
      ("MindmapParser",   dedicated.MermaidDiagramEmitters.emitMindmapParser(dummyRast)),
      ("MindmapRenderer", dedicated.MermaidDiagramEmitters.emitMindmapRenderer(dummyRast)),
      ("MindmapStyles",   dedicated.MermaidDiagramEmitters.emitMindmapStyles(dummyRast)),
      ("QuadrantDb",       dedicated.MermaidDiagramEmitters.emitQuadrantDb(dummyRast)),
      ("QuadrantDiagram",  dedicated.MermaidDiagramEmitters.emitQuadrantDiagram(dummyRast)),
      ("QuadrantParser",   dedicated.MermaidDiagramEmitters.emitQuadrantParser(dummyRast)),
      ("QuadrantRenderer", dedicated.MermaidDiagramEmitters.emitQuadrantRenderer(dummyRast)),
      ("QuadrantStyles",   dedicated.MermaidDiagramEmitters.emitQuadrantStyles(dummyRast)),
      ("SankeyDb",       dedicated.MermaidDiagramEmitters.emitSankeyDb(dummyRast)),
      ("SankeyDiagram",  dedicated.MermaidDiagramEmitters.emitSankeyDiagram(dummyRast)),
      ("SankeyParser",   dedicated.MermaidDiagramEmitters.emitSankeyParser(dummyRast)),
      ("SankeyRenderer", dedicated.MermaidDiagramEmitters.emitSankeyRenderer(dummyRast)),
      ("SankeyStyles",   dedicated.MermaidDiagramEmitters.emitSankeyStyles(dummyRast)),
    )

    for ((name, source) <- allFiles) {
      val path = outDir.resolve(s"$name.scala")
      java.nio.file.Files.writeString(path, source)
      println(s"[emit] $name.scala: ${source.linesIterator.size} lines -> $path")
    }
    println(s"[emit] Total: ${allFiles.size} diagram files written to $outDir")
