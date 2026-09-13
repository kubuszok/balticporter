package balticporter.frontend.ts

class MermaidEmitterSpec extends munit.FunSuite:

  private def loadRast(resource: String): RastFile =
    val stream = getClass.getResourceAsStream(resource)
    assert(stream != null, s"Resource not found: $resource")
    val json = new String(stream.readAllBytes(), java.nio.charset.StandardCharsets.UTF_8)
    stream.close()
    Rast.readFile(json)

  // -- Style generators -------------------------------------------------------

  test("pieStyles.ts -> PieStyles object with CSS interpolation"):
    val rast = loadRast("/rast/mermaid/src/diagrams/pie/pieStyles.rast.json")
    val scala = dedicated.MermaidEmitter.emitStyles(rast, "PieStyles", "pie")
    println("=== PieStyles.scala (emitted) ===")
    println(scala)
    assert(scala.contains("object PieStyles"), "should emit PieStyles object")
    assert(scala.contains("def generate(vars: ThemeVariables): String"), "should have generate method")
    assert(scala.contains("vars.pieStrokeColor") || scala.contains("pieStrokeColor"),
      "should reference theme vars")
    assert(scala.contains(".pieCircle"), "should contain CSS class .pieCircle")
    assert(scala.contains(".pieTitleText"), "should contain CSS class .pieTitleText")

  test("flowchart/styles.ts -> FlowchartStyles object"):
    val rast = loadRast("/rast/mermaid/src/diagrams/flowchart/styles.rast.json")
    val scala = dedicated.MermaidEmitter.emitStyles(rast, "FlowchartStyles", "flowchart")
    println("=== FlowchartStyles.scala (emitted) ===")
    println(scala)
    assert(scala.contains("object FlowchartStyles"), "should emit FlowchartStyles object")
    assert(scala.contains("def generate(vars: ThemeVariables)"), "should have generate method")
    assert(scala.contains(".label"), "should contain CSS class .label")
    assert(scala.contains("fontFamily"), "should reference fontFamily")

  test("block/styles.ts -> BlockStyles object"):
    val rast = loadRast("/rast/mermaid/src/diagrams/block/styles.rast.json")
    val scala = dedicated.MermaidEmitter.emitStyles(rast, "BlockStyles", "block")
    println("=== BlockStyles.scala (emitted) ===")
    println(scala)
    assert(scala.contains("object BlockStyles"), "should emit BlockStyles object")
    assert(scala.contains("def generate(vars: ThemeVariables)"), "should have generate method")

  test("packet/styles.ts -> PacketStyles object"):
    val rast = loadRast("/rast/mermaid/src/diagrams/packet/styles.rast.json")
    val scala = dedicated.MermaidEmitter.emitStyles(rast, "PacketStyles", "packet")
    println("=== PacketStyles.scala (emitted) ===")
    println(scala)
    assert(scala.contains("object PacketStyles"), "should emit PacketStyles object")

  test("mindmap/styles.ts -> MindmapStyles object"):
    val rast = loadRast("/rast/mermaid/src/diagrams/mindmap/styles.rast.json")
    val scala = dedicated.MermaidEmitter.emitStyles(rast, "MindmapStyles", "mindmap")
    println("=== MindmapStyles.scala (emitted) ===")
    println(scala)
    assert(scala.contains("object MindmapStyles"), "should emit MindmapStyles object")

  // -- Utility functions ------------------------------------------------------

  test("comments.ts -> Comments utility object"):
    val rast = loadRast("/rast/mermaid/src/diagram-api/comments.rast.json")
    val scala = dedicated.MermaidEmitter.emitUtility(rast, "Comments", "")
    println("=== Comments.scala (emitted) ===")
    println(scala)
    assert(scala.contains("object Comments"), "should emit Comments object")
    assert(scala.contains("cleanupComments"), "should contain cleanupComments")

  // -- Accessibility ----------------------------------------------------------

  test("accessibility.ts -> Accessibility object"):
    val rast = loadRast("/rast/mermaid/src/accessibility.rast.json")
    val scala = dedicated.MermaidEmitter.emitAccessibility(rast)
    println("=== Accessibility.scala (emitted) ===")
    println(scala)
    assert(scala.contains("object Accessibility"), "should emit Accessibility object")
    assert(scala.contains("SvgBuilder"), "should use SvgBuilder not D3Element")
    assert(scala.contains("setA11yDiagramInfo"), "should have setA11yDiagramInfo")
    assert(scala.contains("addSVGa11yTitleDescription"), "should have addSVGa11yTitleDescription")
    assert(scala.contains("graphics-document document"), "should have SVG_ROLE value")

  // -- Write emitted files for inspection -------------------------------------

  test("write all emitted files to target/emitted-mermaid"):
    val outDir = java.nio.file.Path.of(sys.props.getOrElse("user.dir", ".")).resolve("target/emitted-mermaid")
    java.nio.file.Files.createDirectories(outDir)

    val emitted = scala.collection.mutable.LinkedHashMap.empty[String, String]

    // Styles
    for ((rastPath, objectName, pkg) <- List(
      ("/rast/mermaid/src/diagrams/pie/pieStyles.rast.json", "PieStyles", "pie"),
      ("/rast/mermaid/src/diagrams/flowchart/styles.rast.json", "FlowchartStyles", "flowchart"),
      ("/rast/mermaid/src/diagrams/block/styles.rast.json", "BlockStyles", "block"),
      ("/rast/mermaid/src/diagrams/packet/styles.rast.json", "PacketStyles", "packet"),
      ("/rast/mermaid/src/diagrams/mindmap/styles.rast.json", "MindmapStyles", "mindmap"),
    )) {
      val rast = loadRast(rastPath)
      val scala = dedicated.MermaidEmitter.emitStyles(rast, objectName, pkg)
      emitted(objectName) = scala
    }

    // Utility
    val commentsRast = loadRast("/rast/mermaid/src/diagram-api/comments.rast.json")
    emitted("Comments") = dedicated.MermaidEmitter.emitUtility(commentsRast, "Comments", "")

    // Accessibility
    val a11yRast = loadRast("/rast/mermaid/src/accessibility.rast.json")
    emitted("Accessibility") = dedicated.MermaidEmitter.emitAccessibility(a11yRast)

    for ((name, source) <- emitted) {
      val path = outDir.resolve(s"$name.scala")
      java.nio.file.Files.writeString(path, source)
      println(s"[emit] $name.scala: ${source.linesIterator.size} lines -> $path")
    }
    println(s"[emit] Total: ${emitted.size} files written to $outDir")

  // -- Renderer emission (D3 -> SvgBuilder) -----------------------------------

  test("infoRenderer.ts -> InfoRenderer with SvgBuilder calls"):
    val rast = loadRast("/rast/mermaid/src/diagrams/info/infoRenderer.rast.json")
    val scala = dedicated.MermaidEmitter.emitRenderer(rast, "InfoRenderer", "info")
    println("=== InfoRenderer.scala (emitted) ===")
    println(scala)
    assert(scala.contains("object InfoRenderer"), "should emit InfoRenderer object")
    assert(scala.contains("def render("), "should have render method")
    assert(scala.contains("SvgBuilder"), "should use SvgBuilder")
    assert(!scala.contains("d3.select"), "should not contain d3.select")
    assert(!scala.contains("D3Element"), "should not contain D3Element")
    // Should have append calls for svg elements
    assert(scala.contains(".append("), "should have append calls")
    // Should have attr calls
    assert(scala.contains(".attr("), "should have attr calls")
    // Should have text calls
    assert(scala.contains(".text("), "should have text calls")
    // Should produce SVG output
    assert(scala.contains(".build().toMarkup()"), "should produce SVG output")

  test("errorRenderer.ts -> ErrorRenderer with SvgBuilder calls"):
    val rast = loadRast("/rast/mermaid/src/diagrams/error/errorRenderer.rast.json")
    val scala = dedicated.MermaidEmitter.emitRenderer(rast, "ErrorRenderer", "error_")
    println("=== ErrorRenderer.scala (emitted) ===")
    println(scala)
    assert(scala.contains("object ErrorRenderer"), "should emit ErrorRenderer object")
    assert(scala.contains("def render("), "should have render method")
    assert(scala.contains("SvgBuilder"), "should use SvgBuilder")
    assert(scala.contains(".append("), "should have append calls")
    assert(scala.contains(".attr("), "should have attr calls")
    assert(scala.contains(".style("), "should have style calls")
    assert(scala.contains(".text("), "should have text calls")
    // Should contain path elements for error icon
    assert(scala.contains("\"path\""), "should create path elements")
    // Should contain 'Syntax error in text' message
    assert(scala.contains("Syntax error in text"), "should contain error text")
    assert(scala.contains(".build().toMarkup()"), "should produce SVG output")

  test("pieRenderer.ts -> PieRenderer with SvgBuilder calls"):
    val rast = loadRast("/rast/mermaid/src/diagrams/pie/pieRenderer.rast.json")
    val scala = dedicated.MermaidEmitter.emitRenderer(rast, "PieRenderer", "pie")
    println("=== PieRenderer.scala (emitted) ===")
    println(scala)
    assert(scala.contains("object PieRenderer"), "should emit PieRenderer object")
    assert(scala.contains("def render("), "should have render method")
    assert(scala.contains("SvgBuilder"), "should use SvgBuilder")
    assert(scala.contains(".append("), "should have append calls")
    assert(scala.contains(".attr("), "should have attr calls")
    assert(scala.contains(".build().toMarkup()"), "should produce SVG output")

  test("renderer emitter linearizes D3 chains correctly"):
    val rast = loadRast("/rast/mermaid/src/diagrams/info/infoRenderer.rast.json")
    val scala = dedicated.MermaidEmitter.emitRenderer(rast, "InfoRenderer", "info")
    // The info renderer has a chain: group.append('text').attr('x', 100).attr('y', 40)...
    // This should be linearized into separate statements
    val lines = scala.linesIterator.toList
    // Check that append, attr, style, text are on separate lines
    val appendLines = lines.filter(_.contains(".append("))
    val attrLines = lines.filter(_.contains(".attr("))
    assert(appendLines.nonEmpty, "should have separate append lines")
    assert(attrLines.nonEmpty, "should have separate attr lines")

  test("renderer emitter writes output files"):
    val outDir = java.nio.file.Path.of(sys.props.getOrElse("user.dir", ".")).resolve("target/emitted-mermaid")
    java.nio.file.Files.createDirectories(outDir)

    for ((rastPath, objectName, pkg) <- List(
      ("/rast/mermaid/src/diagrams/info/infoRenderer.rast.json", "InfoRenderer", "info"),
      ("/rast/mermaid/src/diagrams/error/errorRenderer.rast.json", "ErrorRenderer", "error_"),
    )) {
      val rast = loadRast(rastPath)
      val scala = dedicated.MermaidEmitter.emitRenderer(rast, objectName, pkg)
      val path = outDir.resolve(s"$objectName.scala")
      java.nio.file.Files.writeString(path, scala)
      println(s"[emit] $objectName.scala: ${scala.linesIterator.size} lines -> $path")
    }
