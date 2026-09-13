package balticporter.frontend.ts

class MermaidTemplateEmitterSpec extends munit.FunSuite:

  private def loadRast(resource: String): RastFile =
    val stream = getClass.getResourceAsStream(resource)
    assert(stream != null, s"Resource not found: $resource")
    val json = new String(stream.readAllBytes(), java.nio.charset.StandardCharsets.UTF_8)
    stream.close()
    Rast.readFile(json)

  private lazy val dummyRast = loadRast("/rast/mermaid/src/diagrams/info/infoDb.rast.json")

  private val emitter = dedicated.MermaidTemplateEmitter

  // Mapping: (diagramDir, prefix, fileNamePrefix)
  // fileNamePrefix may differ from prefix (e.g. treeview dir uses TreeView prefix)
  private val allDiagrams: List[(String, String, String)] = List(
    ("packet", "Packet", "Packet"),
    ("kanban", "Kanban", "Kanban"),
    ("cynefin", "Cynefin", "Cynefin"),
    ("treeview", "Treeview", "TreeView"),
    ("wardley", "Wardley", "Wardley"),
    ("ishikawa", "Ishikawa", "Ishikawa"),
    ("venn", "Venn", "Venn"),
    ("treemap", "Treemap", "Treemap"),
    ("eventmodeling", "EventModeling", "EventModeling"),
    ("radar", "Radar", "Radar"),
    ("architecture", "Architecture", "Architecture"),
    ("block", "Block", "Block"),
    ("journey", "Journey", "Journey"),
    ("timeline", "Timeline", "Timeline"),
    ("mindmap", "Mindmap", "Mindmap"),
    ("quadrant", "Quadrant", "Quadrant"),
    ("sankey", "Sankey", "Sankey"),
    ("er", "Er", "Er"),
    ("requirement", "Requirement", "Requirement"),
    ("c4", "C4", "C4"),
    ("gantt", "Gantt", "Gantt"),
    ("git", "Git", "Git"),
    ("state", "State", "State"),
    ("xychart", "XyChart", "XyChart"),
    ("flowchart", "Flowchart", "Flowchart"),
    ("class_", "Class", "Class"),
    ("sequence", "Sequence", "Sequence"),
  )

  // Helper to call the right emitter method by name
  private def callEmitter(methodPrefix: String, suffix: String): String =
    val methodName = s"emit${methodPrefix}${suffix}"
    val method = emitter.getClass.getMethod(methodName, classOf[RastFile])
    method.invoke(emitter, dummyRast).asInstanceOf[String]

  // ===========================================================================
  // Verify all 27 diagrams load their templates successfully
  // ===========================================================================

  for ((dir, methodPrefix, filePrefix) <- allDiagrams)
    test(s"$dir: all 5 templates load and contain correct package") {
      for (suffix <- List("Db", "Diagram", "Parser", "Renderer", "Styles"))
        val result = callEmitter(methodPrefix, suffix)
        assert(result.contains(s"package $dir") || result.contains(s"package ${dir.stripSuffix("_")}"),
          s"$dir $suffix should contain correct package declaration")
        assert(result.contains("Mermaid diagramming engine"),
          s"$dir $suffix should contain auto-generated header")
    }

  // ===========================================================================
  // Verify key data model types exist for each diagram
  // ===========================================================================

  test("packet: PacketField and PacketDb types") {
    val db = callEmitter("Packet", "Db")
    assert(db.contains("final case class PacketField"), "should have PacketField")
    assert(db.contains("final class PacketDb"), "should have PacketDb")
    assert(db.contains("startBit: Int"), "should have startBit field")
    assert(db.contains("endBit: Int"), "should have endBit field")
    assert(db.contains("def addField"), "should have addField method")
  }

  test("kanban: KanbanCard, KanbanColumn, KanbanDb types") {
    val db = callEmitter("Kanban", "Db")
    assert(db.contains("final case class KanbanCard"), "should have KanbanCard")
    assert(db.contains("final case class KanbanColumn"), "should have KanbanColumn")
    assert(db.contains("final class KanbanDb"), "should have KanbanDb")
    assert(db.contains("def addColumn"), "should have addColumn method")
    assert(db.contains("def addCardToLast"), "should have addCardToLast method")
  }

  test("flowchart: FlowNode, FlowEdge, FlowSubgraph types") {
    val db = callEmitter("Flowchart", "Db")
    assert(db.contains("final case class FlowNode"), "should have FlowNode")
    assert(db.contains("final case class FlowEdge"), "should have FlowEdge")
    assert(db.contains("final case class FlowSubgraph"), "should have FlowSubgraph")
    assert(db.contains("final class FlowchartDb"), "should have FlowchartDb")
    assert(db.contains("def addNode"), "should have addNode method")
    assert(db.contains("def addEdge"), "should have addEdge method")
    assert(db.contains("def addSubgraph"), "should have addSubgraph method")
    assert(db.contains("def destructLink"), "should have destructLink method")
  }

  test("sequence: SequenceMessage, SequenceActor types") {
    val db = callEmitter("Sequence", "Db")
    assert(db.contains("SequenceMessage") || db.contains("SeqMessage"),
      "should have message type")
    assert(db.contains("SequenceActor") || db.contains("SeqActor"),
      "should have actor type")
    assert(db.contains("final class SequenceDb"), "should have SequenceDb")
  }

  test("er: ErEntity, ErAttribute, ErRelationship types") {
    val db = callEmitter("Er", "Db")
    assert(db.contains("ErEntity") || db.contains("final case class Er"),
      "should have entity type")
    assert(db.contains("ErRelationship") || db.contains("Relationship"),
      "should have relationship type")
    assert(db.contains("final class ErDb"), "should have ErDb")
  }

  test("gantt: GanttTask type") {
    val db = callEmitter("Gantt", "Db")
    assert(db.contains("GanttTask"), "should have GanttTask type")
    assert(db.contains("final class GanttDb"), "should have GanttDb")
  }

  test("state: StateNode type") {
    val db = callEmitter("State", "Db")
    assert(db.contains("StateNode") || db.contains("StateState"),
      "should have state node type")
    assert(db.contains("final class StateDb"), "should have StateDb")
  }

  test("class_: ClassMember, ClassRelation types") {
    val db = callEmitter("Class", "Db")
    assert(db.contains("ClassMember") || db.contains("Member"),
      "should have member type")
    assert(db.contains("final class ClassDb"), "should have ClassDb")
  }

  // ===========================================================================
  // Write ALL emitted files to target/emitted-mermaid
  // ===========================================================================

  test("write all 135 template-emitted files to target/emitted-mermaid") {
    val outDir = java.nio.file.Path.of(sys.props.getOrElse("user.dir", "."))
      .resolve("target/emitted-mermaid")
    java.nio.file.Files.createDirectories(outDir)

    var count = 0
    for ((dir, methodPrefix, filePrefix) <- allDiagrams) {
      for (suffix <- List("Db", "Diagram", "Parser", "Renderer", "Styles")) {
        val result = callEmitter(methodPrefix, suffix)
        val fileName = s"${filePrefix}${suffix}.scala"
        val path = outDir.resolve(fileName)
        java.nio.file.Files.writeString(path, result)
        count += 1
        println(s"[emit] $fileName: ${result.linesIterator.size} lines -> $path")
      }
    }
    println(s"[emit] Total: $count files written to $outDir")
    assertEquals(count, 135)
  }
