package balticporter.frontend.ts

class MermaidDiagramEmitters2Spec extends munit.FunSuite:

  private def loadRast(resource: String): RastFile =
    val stream = getClass.getResourceAsStream(resource)
    assert(stream != null, s"Resource not found: $resource")
    val json = new String(stream.readAllBytes(), java.nio.charset.StandardCharsets.UTF_8)
    stream.close()
    Rast.readFile(json)

  // Use the info RAST as a dummy input for all emitters (the emitters produce
  // template code that does not depend on the actual RAST content).
  private lazy val dummyRast = loadRast("/rast/mermaid/src/diagrams/info/infoDb.rast.json")

  // ===========================================================================
  // 1. ER diagram
  // ===========================================================================

  test("emitErDb emits ErDb class with entities and relationships"):
    val scala = dedicated.MermaidDiagramEmitters2.emitErDb(dummyRast)
    assert(scala.contains("enum Cardinality"), "should emit Cardinality enum")
    assert(scala.contains("enum Identification"), "should emit Identification enum")
    assert(scala.contains("final case class ErEntity"), "should emit ErEntity case class")
    assert(scala.contains("final case class ErAttribute"), "should emit ErAttribute case class")
    assert(scala.contains("final class ErDb"), "should emit ErDb class")

  test("emitErDiagram emits ErDiagram facade"):
    val scala = dedicated.MermaidDiagramEmitters2.emitErDiagram(dummyRast)
    assert(scala.contains("object ErDiagram"), "should emit ErDiagram object")
    assert(scala.contains("def detect(text: String): Boolean"), "should have detect method")
    assert(scala.contains("ErParser.parse(text)"), "should delegate to ErParser")
    assert(scala.contains("ErRenderer.render(db, config)"), "should delegate to ErRenderer")
    assert(scala.contains("erdiagram"), "should detect erDiagram keyword")

  test("emitErParser emits ErParser with keyword detection"):
    val scala = dedicated.MermaidDiagramEmitters2.emitErParser(dummyRast)
    assert(scala.contains("object ErParser"), "should emit ErParser object")
    assert(scala.contains("def parse(input: String): ErDb"), "should have parse method returning ErDb")
    assert(scala.contains("def parse(input: String, db: ErDb): ErDb"), "should have two-arg parse")
    assert(scala.contains("erDiagram"), "should check erDiagram keyword")
    assert(scala.contains("new ErDb"), "should create ErDb")

  test("emitErRenderer emits ErRenderer with SvgBuilder"):
    val scala = dedicated.MermaidDiagramEmitters2.emitErRenderer(dummyRast)
    assert(scala.contains("object ErRenderer"), "should emit ErRenderer object")
    assert(scala.contains("SvgBuilder.createSvg"), "should create SVG")
    assert(scala.contains("Accessibility.applyTo"), "should apply accessibility")
    assert(scala.contains("ErStyles.generate"), "should generate CSS")
    assert(scala.contains(".build().toMarkup()"), "should produce SVG output")

  test("emitErStyles emits ErStyles with CSS classes"):
    val scala = dedicated.MermaidDiagramEmitters2.emitErStyles(dummyRast)
    assert(scala.contains("object ErStyles"), "should emit ErStyles object")
    assert(scala.contains("def generate(vars: ThemeVariables): String"), "should have generate method")
    assert(scala.contains(".entityBox"), "should contain entityBox CSS")
    assert(scala.contains(".entityLabel"), "should contain entityLabel CSS")
    assert(scala.contains(".relationshipLine"), "should contain relationshipLine CSS")

  // ===========================================================================
  // 2. Requirement diagram
  // ===========================================================================

  test("emitRequirementDb emits RequirementDb with nodes and relationships"):
    val scala = dedicated.MermaidDiagramEmitters2.emitRequirementDb(dummyRast)
    assert(scala.contains("final case class RequirementNode"), "should emit RequirementNode")
    assert(scala.contains("final case class ElementNode"), "should emit ElementNode")
    assert(scala.contains("final case class RequirementRelationship"), "should emit RequirementRelationship")
    assert(scala.contains("final class RequirementDb"), "should emit RequirementDb class")
    assert(scala.contains("def clear()"), "should have clear method")

  test("emitRequirementDiagram emits facade"):
    val scala = dedicated.MermaidDiagramEmitters2.emitRequirementDiagram(dummyRast)
    assert(scala.contains("object RequirementDiagram"), "should emit RequirementDiagram object")
    assert(scala.contains("def detect"), "should have detect")
    assert(scala.contains("RequirementParser.parse"), "should delegate to parser")
    assert(scala.contains("RequirementRenderer.render"), "should delegate to renderer")
    assert(scala.contains("requirementdiagram"), "should detect keyword")

  test("emitRequirementParser emits parser"):
    val scala = dedicated.MermaidDiagramEmitters2.emitRequirementParser(dummyRast)
    assert(scala.contains("object RequirementParser"), "should emit RequirementParser object")
    assert(scala.contains("def parse(input: String): RequirementDb"), "should return RequirementDb")
    assert(scala.contains("new RequirementDb"), "should create RequirementDb")
    assert(scala.contains("requirementDiagram"), "should check keyword")
    assert(scala.contains("ParseException"), "should throw on bad input")

  test("emitRequirementRenderer emits renderer"):
    val scala = dedicated.MermaidDiagramEmitters2.emitRequirementRenderer(dummyRast)
    assert(scala.contains("object RequirementRenderer"), "should emit object")
    assert(scala.contains("SvgBuilder.createSvg"), "should create SVG")
    assert(scala.contains("RequirementStyles.generate"), "should generate CSS")
    assert(scala.contains(".build().toMarkup()"), "should produce SVG")
    assert(scala.contains("reqBox"), "should emit reqBox class")

  test("emitRequirementStyles emits styles"):
    val scala = dedicated.MermaidDiagramEmitters2.emitRequirementStyles(dummyRast)
    assert(scala.contains("object RequirementStyles"), "should emit object")
    assert(scala.contains("def generate(vars: ThemeVariables)"), "should have generate")
    assert(scala.contains(".reqBox"), "should have reqBox CSS")
    assert(scala.contains(".elemBox"), "should have elemBox CSS")
    assert(scala.contains(".reqRelLine"), "should have reqRelLine CSS")

  // ===========================================================================
  // 3. C4 diagram
  // ===========================================================================

  test("emitC4Db emits C4Db with entities, relationships, boundaries"):
    val scala = dedicated.MermaidDiagramEmitters2.emitC4Db(dummyRast)
    assert(scala.contains("final case class C4Entity"), "should emit C4Entity")
    assert(scala.contains("final case class C4Relationship"), "should emit C4Relationship")
    assert(scala.contains("final case class C4Boundary"), "should emit C4Boundary")
    assert(scala.contains("final class C4Db"), "should emit C4Db class")
    assert(scala.contains("parentBoundary: String = \"global\""), "should default parentBoundary to global")

  test("emitC4Diagram emits C4Diagram facade"):
    val scala = dedicated.MermaidDiagramEmitters2.emitC4Diagram(dummyRast)
    assert(scala.contains("object C4Diagram"), "should emit object")
    assert(scala.contains("def detect"), "should have detect")
    assert(scala.contains("C4Parser.parse"), "should delegate to parser")
    assert(scala.contains("C4Renderer.render"), "should delegate to renderer")
    assert(scala.contains("startsWith(\"C4\")"), "should detect C4 prefix")

  test("emitC4Parser emits C4Parser"):
    val scala = dedicated.MermaidDiagramEmitters2.emitC4Parser(dummyRast)
    assert(scala.contains("object C4Parser"), "should emit object")
    assert(scala.contains("def parse(input: String): C4Db"), "should return C4Db")
    assert(scala.contains("new C4Db"), "should create C4Db")
    assert(scala.contains("db.diagramType"), "should set diagramType")
    assert(scala.contains("startsWith(\"C4\")"), "should check C4 keyword")

  test("emitC4Renderer emits C4Renderer"):
    val scala = dedicated.MermaidDiagramEmitters2.emitC4Renderer(dummyRast)
    assert(scala.contains("object C4Renderer"), "should emit object")
    assert(scala.contains("SvgBuilder.createSvg"), "should create SVG")
    assert(scala.contains("C4Styles.generate"), "should generate CSS")
    assert(scala.contains(".build().toMarkup()"), "should produce SVG")
    assert(scala.contains("c4Person"), "should reference c4Person class")

  test("emitC4Styles emits C4Styles"):
    val scala = dedicated.MermaidDiagramEmitters2.emitC4Styles(dummyRast)
    assert(scala.contains("object C4Styles"), "should emit object")
    assert(scala.contains("def generate(vars: ThemeVariables)"), "should have generate")
    assert(scala.contains(".c4Person"), "should have c4Person CSS")
    assert(scala.contains(".c4System"), "should have c4System CSS")
    assert(scala.contains(".c4Boundary"), "should have c4Boundary CSS")

  // ===========================================================================
  // 4. Gantt diagram
  // ===========================================================================

  test("emitGanttDb emits GanttDb with tasks and sections"):
    val scala = dedicated.MermaidDiagramEmitters2.emitGanttDb(dummyRast)
    assert(scala.contains("final case class GanttTask"), "should emit GanttTask")
    assert(scala.contains("final class GanttDb"), "should emit GanttDb class")
    assert(scala.contains("var dateFormat"), "should have dateFormat")
    assert(scala.contains("var axisFormat"), "should have axisFormat")
    assert(scala.contains("def addTask"), "should have addTask method")

  test("emitGanttDiagram emits GanttDiagram facade"):
    val scala = dedicated.MermaidDiagramEmitters2.emitGanttDiagram(dummyRast)
    assert(scala.contains("object GanttDiagram"), "should emit object")
    assert(scala.contains("def detect"), "should have detect")
    assert(scala.contains("GanttParser.parse"), "should delegate to parser")
    assert(scala.contains("GanttRenderer.render"), "should delegate to renderer")
    assert(scala.contains("\"gantt\""), "should detect gantt keyword")

  test("emitGanttParser emits GanttParser"):
    val scala = dedicated.MermaidDiagramEmitters2.emitGanttParser(dummyRast)
    assert(scala.contains("object GanttParser"), "should emit object")
    assert(scala.contains("def parse(input: String): GanttDb"), "should return GanttDb")
    assert(scala.contains("def parse(input: String, db: GanttDb): GanttDb"), "should have two-arg parse")
    assert(scala.contains("new GanttDb"), "should create GanttDb")
    assert(scala.contains("gantt"), "should check keyword")

  test("emitGanttRenderer emits GanttRenderer"):
    val scala = dedicated.MermaidDiagramEmitters2.emitGanttRenderer(dummyRast)
    assert(scala.contains("object GanttRenderer"), "should emit object")
    assert(scala.contains("SvgBuilder.createSvg"), "should create SVG")
    assert(scala.contains("GanttStyles.generate"), "should generate CSS")
    assert(scala.contains(".build().toMarkup()"), "should produce SVG")
    assert(scala.contains("ganttTitleText"), "should reference ganttTitleText CSS class")

  test("emitGanttStyles emits GanttStyles"):
    val scala = dedicated.MermaidDiagramEmitters2.emitGanttStyles(dummyRast)
    assert(scala.contains("object GanttStyles"), "should emit object")
    assert(scala.contains("def generate(vars: ThemeVariables)"), "should have generate")
    assert(scala.contains(".grid"), "should have grid CSS")
    assert(scala.contains(".today"), "should have today CSS")
    assert(scala.contains(".task"), "should have task CSS")

  // ===========================================================================
  // 5. Git diagram
  // ===========================================================================

  test("emitGitDb emits GitDb with commits and branches"):
    val scala = dedicated.MermaidDiagramEmitters2.emitGitDb(dummyRast)
    assert(scala.contains("final case class GitCommit"), "should emit GitCommit")
    assert(scala.contains("final case class GitBranch"), "should emit GitBranch")
    assert(scala.contains("final class GitDb"), "should emit GitDb class")
    assert(scala.contains("currentBranch"), "should have currentBranch")
    assert(scala.contains("def addCommit"), "should have addCommit method")

  test("emitGitDiagram emits GitDiagram facade"):
    val scala = dedicated.MermaidDiagramEmitters2.emitGitDiagram(dummyRast)
    assert(scala.contains("object GitDiagram"), "should emit object")
    assert(scala.contains("def detect"), "should have detect")
    assert(scala.contains("GitParser.parse"), "should delegate to parser")
    assert(scala.contains("GitRenderer.render"), "should delegate to renderer")
    assert(scala.contains("gitgraph"), "should detect gitGraph keyword")

  test("emitGitParser emits GitParser"):
    val scala = dedicated.MermaidDiagramEmitters2.emitGitParser(dummyRast)
    assert(scala.contains("object GitParser"), "should emit object")
    assert(scala.contains("def parse(input: String): GitDb"), "should return GitDb")
    assert(scala.contains("new GitDb"), "should create GitDb")
    assert(scala.contains("gitGraph"), "should check keyword")
    assert(scala.contains("ParseException"), "should throw on bad input")

  test("emitGitRenderer emits GitRenderer"):
    val scala = dedicated.MermaidDiagramEmitters2.emitGitRenderer(dummyRast)
    assert(scala.contains("object GitRenderer"), "should emit object")
    assert(scala.contains("SvgBuilder.createSvg"), "should create SVG")
    assert(scala.contains("GitStyles.generate"), "should generate CSS")
    assert(scala.contains(".build().toMarkup()"), "should produce SVG")
    assert(scala.contains("gitCommit"), "should reference gitCommit class")

  test("emitGitStyles emits GitStyles"):
    val scala = dedicated.MermaidDiagramEmitters2.emitGitStyles(dummyRast)
    assert(scala.contains("object GitStyles"), "should emit object")
    assert(scala.contains("def generate(vars: ThemeVariables)"), "should have generate")
    assert(scala.contains(".gitCommit"), "should have gitCommit CSS")
    assert(scala.contains(".gitBranch"), "should have gitBranch CSS")
    assert(scala.contains(".gitTag"), "should have gitTag CSS")

  // ===========================================================================
  // 6. State diagram
  // ===========================================================================

  test("emitStateDb emits StateDb with states and transitions"):
    val scala = dedicated.MermaidDiagramEmitters2.emitStateDb(dummyRast)
    assert(scala.contains("final case class StateNode"), "should emit StateNode")
    assert(scala.contains("final case class StateTransition"), "should emit StateTransition")
    assert(scala.contains("final class StateDb"), "should emit StateDb class")
    assert(scala.contains("def addState"), "should have addState method")
    assert(scala.contains("def addTransition"), "should have addTransition method")

  test("emitStateDiagram emits StateDiagram facade"):
    val scala = dedicated.MermaidDiagramEmitters2.emitStateDiagram(dummyRast)
    assert(scala.contains("object StateDiagram"), "should emit object")
    assert(scala.contains("def detect"), "should have detect")
    assert(scala.contains("StateParser.parse"), "should delegate to parser")
    assert(scala.contains("StateRenderer.render"), "should delegate to renderer")
    assert(scala.contains("statediagram"), "should detect stateDiagram keyword")

  test("emitStateParser emits StateParser"):
    val scala = dedicated.MermaidDiagramEmitters2.emitStateParser(dummyRast)
    assert(scala.contains("object StateParser"), "should emit object")
    assert(scala.contains("def parse(input: String): StateDb"), "should return StateDb")
    assert(scala.contains("new StateDb"), "should create StateDb")
    assert(scala.contains("statediagram"), "should check keyword")
    assert(scala.contains("ParseException"), "should throw on bad input")

  test("emitStateRenderer emits StateRenderer"):
    val scala = dedicated.MermaidDiagramEmitters2.emitStateRenderer(dummyRast)
    assert(scala.contains("object StateRenderer"), "should emit object")
    assert(scala.contains("SvgBuilder.createSvg"), "should create SVG")
    assert(scala.contains("StateStyles.generate"), "should generate CSS")
    assert(scala.contains(".build().toMarkup()"), "should produce SVG")
    assert(scala.contains("stateNode"), "should reference stateNode class")

  test("emitStateStyles emits StateStyles"):
    val scala = dedicated.MermaidDiagramEmitters2.emitStateStyles(dummyRast)
    assert(scala.contains("object StateStyles"), "should emit object")
    assert(scala.contains("def generate(vars: ThemeVariables)"), "should have generate")
    assert(scala.contains(".stateNode"), "should have stateNode CSS")
    assert(scala.contains(".stateStart"), "should have stateStart CSS")
    assert(scala.contains(".stateTransition"), "should have stateTransition CSS")

  // ===========================================================================
  // 7. XY Chart diagram
  // ===========================================================================

  test("emitXyChartDb emits XyChartDb with datasets"):
    val scala = dedicated.MermaidDiagramEmitters2.emitXyChartDb(dummyRast)
    assert(scala.contains("final case class XyDataset"), "should emit XyDataset")
    assert(scala.contains("final class XyChartDb"), "should emit XyChartDb class")
    assert(scala.contains("var xAxisLabel"), "should have xAxisLabel")
    assert(scala.contains("var yAxisLabel"), "should have yAxisLabel")
    assert(scala.contains("def addDataset"), "should have addDataset method")

  test("emitXyChartDiagram emits XyChartDiagram facade"):
    val scala = dedicated.MermaidDiagramEmitters2.emitXyChartDiagram(dummyRast)
    assert(scala.contains("object XyChartDiagram"), "should emit object")
    assert(scala.contains("def detect"), "should have detect")
    assert(scala.contains("XyChartParser.parse"), "should delegate to parser")
    assert(scala.contains("XyChartRenderer.render"), "should delegate to renderer")
    assert(scala.contains("xychart-beta"), "should detect xychart-beta keyword")

  test("emitXyChartParser emits XyChartParser"):
    val scala = dedicated.MermaidDiagramEmitters2.emitXyChartParser(dummyRast)
    assert(scala.contains("object XyChartParser"), "should emit object")
    assert(scala.contains("def parse(input: String): XyChartDb"), "should return XyChartDb")
    assert(scala.contains("new XyChartDb"), "should create XyChartDb")
    assert(scala.contains("xychart-beta"), "should check keyword")
    assert(scala.contains("ParseException"), "should throw on bad input")

  test("emitXyChartRenderer emits XyChartRenderer"):
    val scala = dedicated.MermaidDiagramEmitters2.emitXyChartRenderer(dummyRast)
    assert(scala.contains("object XyChartRenderer"), "should emit object")
    assert(scala.contains("SvgBuilder.createSvg"), "should create SVG")
    assert(scala.contains("XyChartStyles.generate"), "should generate CSS")
    assert(scala.contains(".build().toMarkup()"), "should produce SVG")
    assert(scala.contains("xyBar"), "should reference xyBar class")

  test("emitXyChartStyles emits XyChartStyles"):
    val scala = dedicated.MermaidDiagramEmitters2.emitXyChartStyles(dummyRast)
    assert(scala.contains("object XyChartStyles"), "should emit object")
    assert(scala.contains("def generate(vars: ThemeVariables)"), "should have generate")
    assert(scala.contains(".xyTitle"), "should have xyTitle CSS")
    assert(scala.contains(".xyAxis"), "should have xyAxis CSS")
    assert(scala.contains(".xyBar"), "should have xyBar CSS")

  // ===========================================================================
  // 8. Flowchart diagram
  // ===========================================================================

  test("emitFlowchartDb emits FlowchartDb with nodes and edges"):
    val scala = dedicated.MermaidDiagramEmitters2.emitFlowchartDb(dummyRast)
    assert(scala.contains("final case class FlowNode"), "should emit FlowNode")
    assert(scala.contains("final case class FlowEdge"), "should emit FlowEdge")
    assert(scala.contains("final case class FlowSubgraph"), "should emit FlowSubgraph")
    assert(scala.contains("final class FlowchartDb"), "should emit FlowchartDb class")
    assert(scala.contains("direction: String = \"TD\""), "should default direction to TD")

  test("emitFlowchartDiagram emits FlowchartDiagram facade"):
    val scala = dedicated.MermaidDiagramEmitters2.emitFlowchartDiagram(dummyRast)
    assert(scala.contains("object FlowchartDiagram"), "should emit object")
    assert(scala.contains("def detect"), "should have detect")
    assert(scala.contains("FlowchartParser.parse"), "should delegate to parser")
    assert(scala.contains("FlowchartRenderer.render"), "should delegate to renderer")
    assert(scala.contains("flowchart"), "should detect flowchart keyword")

  test("emitFlowchartParser emits FlowchartParser"):
    val scala = dedicated.MermaidDiagramEmitters2.emitFlowchartParser(dummyRast)
    assert(scala.contains("object FlowchartParser"), "should emit object")
    assert(scala.contains("def parse(input: String): FlowchartDb"), "should return FlowchartDb")
    assert(scala.contains("new FlowchartDb"), "should create FlowchartDb")
    assert(scala.contains("flowchart"), "should check keyword")
    assert(scala.contains("graph"), "should also check graph keyword")

  test("emitFlowchartRenderer emits FlowchartRenderer"):
    val scala = dedicated.MermaidDiagramEmitters2.emitFlowchartRenderer(dummyRast)
    assert(scala.contains("object FlowchartRenderer"), "should emit object")
    assert(scala.contains("SvgBuilder.createSvg"), "should create SVG")
    assert(scala.contains("FlowchartStyles.generate"), "should generate CSS")
    assert(scala.contains(".build().toMarkup()"), "should produce SVG")
    assert(scala.contains("edgePath"), "should reference edgePath class")

  test("emitFlowchartStyles emits FlowchartStyles"):
    val scala = dedicated.MermaidDiagramEmitters2.emitFlowchartStyles(dummyRast)
    assert(scala.contains("object FlowchartStyles"), "should emit object")
    assert(scala.contains("def generate(vars: ThemeVariables)"), "should have generate")
    assert(scala.contains(".label"), "should have label CSS")
    assert(scala.contains(".edgePath"), "should have edgePath CSS")
    assert(scala.contains(".node"), "should have node CSS")

  // ===========================================================================
  // 9. Class diagram
  // ===========================================================================

  test("emitClassDb emits ClassDiagramDb with classes and relations"):
    val scala = dedicated.MermaidDiagramEmitters2.emitClassDb(dummyRast)
    assert(scala.contains("final case class ClassMember"), "should emit ClassMember")
    assert(scala.contains("final case class ClassNode"), "should emit ClassNode")
    assert(scala.contains("final case class ClassRelation"), "should emit ClassRelation")
    assert(scala.contains("final class ClassDiagramDb"), "should emit ClassDiagramDb class")
    assert(scala.contains("def addClass"), "should have addClass method")

  test("emitClassDiagram emits ClassDiagram facade"):
    val scala = dedicated.MermaidDiagramEmitters2.emitClassDiagram(dummyRast)
    assert(scala.contains("object ClassDiagram"), "should emit object")
    assert(scala.contains("def detect"), "should have detect")
    assert(scala.contains("ClassParser.parse"), "should delegate to parser")
    assert(scala.contains("ClassRenderer.render"), "should delegate to renderer")
    assert(scala.contains("classdiagram"), "should detect classDiagram keyword")

  test("emitClassParser emits ClassParser"):
    val scala = dedicated.MermaidDiagramEmitters2.emitClassParser(dummyRast)
    assert(scala.contains("object ClassParser"), "should emit object")
    assert(scala.contains("def parse(input: String): ClassDiagramDb"), "should return ClassDiagramDb")
    assert(scala.contains("new ClassDiagramDb"), "should create ClassDiagramDb")
    assert(scala.contains("classdiagram"), "should check keyword")
    assert(scala.contains("ParseException"), "should throw on bad input")

  test("emitClassRenderer emits ClassRenderer"):
    val scala = dedicated.MermaidDiagramEmitters2.emitClassRenderer(dummyRast)
    assert(scala.contains("object ClassRenderer"), "should emit object")
    assert(scala.contains("SvgBuilder.createSvg"), "should create SVG")
    assert(scala.contains("ClassStyles.generate"), "should generate CSS")
    assert(scala.contains(".build().toMarkup()"), "should produce SVG")
    assert(scala.contains("classBox"), "should reference classBox class")

  test("emitClassStyles emits ClassStyles"):
    val scala = dedicated.MermaidDiagramEmitters2.emitClassStyles(dummyRast)
    assert(scala.contains("object ClassStyles"), "should emit object")
    assert(scala.contains("def generate(vars: ThemeVariables)"), "should have generate")
    assert(scala.contains(".classBox"), "should have classBox CSS")
    assert(scala.contains(".classLabel"), "should have classLabel CSS")
    assert(scala.contains(".classRelation"), "should have classRelation CSS")

  // ===========================================================================
  // 10. Sequence diagram
  // ===========================================================================

  test("emitSequenceDb emits SequenceDb with actors and messages"):
    val scala = dedicated.MermaidDiagramEmitters2.emitSequenceDb(dummyRast)
    assert(scala.contains("final case class SeqActor"), "should emit SeqActor")
    assert(scala.contains("final case class SeqMessage"), "should emit SeqMessage")
    assert(scala.contains("final case class SeqNote"), "should emit SeqNote")
    assert(scala.contains("final case class SeqLoop"), "should emit SeqLoop")
    assert(scala.contains("final class SequenceDb"), "should emit SequenceDb class")

  test("emitSequenceDiagram emits SequenceDiagram facade"):
    val scala = dedicated.MermaidDiagramEmitters2.emitSequenceDiagram(dummyRast)
    assert(scala.contains("object SequenceDiagram"), "should emit object")
    assert(scala.contains("def detect"), "should have detect")
    assert(scala.contains("SequenceParser.parse"), "should delegate to parser")
    assert(scala.contains("SequenceRenderer.render"), "should delegate to renderer")
    assert(scala.contains("sequencediagram"), "should detect sequenceDiagram keyword")

  test("emitSequenceParser emits SequenceParser"):
    val scala = dedicated.MermaidDiagramEmitters2.emitSequenceParser(dummyRast)
    assert(scala.contains("object SequenceParser"), "should emit object")
    assert(scala.contains("def parse(input: String): SequenceDb"), "should return SequenceDb")
    assert(scala.contains("new SequenceDb"), "should create SequenceDb")
    assert(scala.contains("sequenceDiagram"), "should check keyword")
    assert(scala.contains("ParseException"), "should throw on bad input")

  test("emitSequenceRenderer emits SequenceRenderer"):
    val scala = dedicated.MermaidDiagramEmitters2.emitSequenceRenderer(dummyRast)
    assert(scala.contains("object SequenceRenderer"), "should emit object")
    assert(scala.contains("SvgBuilder.createSvg"), "should create SVG")
    assert(scala.contains("SequenceStyles.generate"), "should generate CSS")
    assert(scala.contains(".build().toMarkup()"), "should produce SVG")
    assert(scala.contains("actorBox"), "should reference actorBox class")

  test("emitSequenceStyles emits SequenceStyles"):
    val scala = dedicated.MermaidDiagramEmitters2.emitSequenceStyles(dummyRast)
    assert(scala.contains("object SequenceStyles"), "should emit object")
    assert(scala.contains("def generate(vars: ThemeVariables)"), "should have generate")
    assert(scala.contains(".actor"), "should have actor CSS")
    assert(scala.contains(".actorLine"), "should have actorLine CSS")
    assert(scala.contains(".messageText"), "should have messageText CSS")
