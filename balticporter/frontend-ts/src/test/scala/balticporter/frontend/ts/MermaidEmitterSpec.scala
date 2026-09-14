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
    val rast  = loadRast("/rast/mermaid/src/diagrams/pie/pieStyles.rast.json")
    val scala = balticporter.corpus.mermaid.MermaidEmitter.emitStyles(rast, "PieStyles", "pie")
    println("=== PieStyles.scala (emitted) ===")
    println(scala)
    assert(scala.contains("object PieStyles"), "should emit PieStyles object")
    assert(scala.contains("def generate(vars: ThemeVariables): String"), "should have generate method")
    assert(scala.contains("vars.pieStrokeColor") || scala.contains("pieStrokeColor"), "should reference theme vars")
    assert(scala.contains(".pieCircle"), "should contain CSS class .pieCircle")
    assert(scala.contains(".pieTitleText"), "should contain CSS class .pieTitleText")

  test("flowchart/styles.ts -> FlowchartStyles object"):
    val rast  = loadRast("/rast/mermaid/src/diagrams/flowchart/styles.rast.json")
    val scala = balticporter.corpus.mermaid.MermaidEmitter.emitStyles(rast, "FlowchartStyles", "flowchart")
    println("=== FlowchartStyles.scala (emitted) ===")
    println(scala)
    assert(scala.contains("object FlowchartStyles"), "should emit FlowchartStyles object")
    assert(scala.contains("def generate(vars: ThemeVariables)"), "should have generate method")
    assert(scala.contains(".label"), "should contain CSS class .label")
    assert(scala.contains("fontFamily"), "should reference fontFamily")

  test("block/styles.ts -> BlockStyles object"):
    val rast  = loadRast("/rast/mermaid/src/diagrams/block/styles.rast.json")
    val scala = balticporter.corpus.mermaid.MermaidEmitter.emitStyles(rast, "BlockStyles", "block")
    println("=== BlockStyles.scala (emitted) ===")
    println(scala)
    assert(scala.contains("object BlockStyles"), "should emit BlockStyles object")
    assert(scala.contains("def generate(vars: ThemeVariables)"), "should have generate method")

  test("packet/styles.ts -> PacketStyles object"):
    val rast  = loadRast("/rast/mermaid/src/diagrams/packet/styles.rast.json")
    val scala = balticporter.corpus.mermaid.MermaidEmitter.emitStyles(rast, "PacketStyles", "packet")
    println("=== PacketStyles.scala (emitted) ===")
    println(scala)
    assert(scala.contains("object PacketStyles"), "should emit PacketStyles object")

  test("mindmap/styles.ts -> MindmapStyles object"):
    val rast  = loadRast("/rast/mermaid/src/diagrams/mindmap/styles.rast.json")
    val scala = balticporter.corpus.mermaid.MermaidEmitter.emitStyles(rast, "MindmapStyles", "mindmap")
    println("=== MindmapStyles.scala (emitted) ===")
    println(scala)
    assert(scala.contains("object MindmapStyles"), "should emit MindmapStyles object")

  // -- Utility functions ------------------------------------------------------

  test("comments.ts -> Comments utility object"):
    val rast  = loadRast("/rast/mermaid/src/diagram-api/comments.rast.json")
    val scala = balticporter.corpus.mermaid.MermaidEmitter.emitUtility(rast, "Comments", "")
    println("=== Comments.scala (emitted) ===")
    println(scala)
    assert(scala.contains("object Comments"), "should emit Comments object")
    assert(scala.contains("cleanupComments"), "should contain cleanupComments")

  // -- CommonDb ---------------------------------------------------------------

  test("commonDb.ts -> CommonDb trait"):
    val rast  = loadRast("/rast/mermaid/src/accessibility.rast.json") // any RAST for the signature
    val scala = balticporter.corpus.mermaid.MermaidEmitter.emitCommonDb(rast)
    println("=== CommonDb.scala (emitted) ===")
    println(scala)
    assert(scala.contains("trait CommonDb"), "should emit CommonDb trait")
    assert(scala.contains("var accTitle"), "should have accTitle field")
    assert(scala.contains("var accDescription"), "should have accDescription field")
    assert(scala.contains("var diagramTitle"), "should have diagramTitle field")
    assert(scala.contains("def clearCommon()"), "should have clearCommon method")

  // -- Accessibility ----------------------------------------------------------

  test("accessibility.ts -> Accessibility object"):
    val rast  = loadRast("/rast/mermaid/src/accessibility.rast.json")
    val scala = balticporter.corpus.mermaid.MermaidEmitter.emitAccessibility(rast)
    println("=== Accessibility.scala (emitted) ===")
    println(scala)
    assert(scala.contains("object Accessibility"), "should emit Accessibility object")
    assert(scala.contains("SvgBuilder"), "should use SvgBuilder not D3Element")
    assert(scala.contains("setA11yDiagramInfo"), "should have setA11yDiagramInfo")
    assert(scala.contains("addSVGa11yTitleDescription"), "should have addSVGa11yTitleDescription")
    assert(scala.contains("graphics-document document"), "should have SVG_ROLE value")
    assert(scala.contains("SvgRole"), "should rename SVG_ROLE to SvgRole")
    assert(scala.contains("def applyTo("), "should have applyTo convenience method")
    // Verify string interpolation is correct (single $ not $$)
    assert(scala.contains("s\"chart-desc-$baseId\""), "descId should use single $")

  // -- Write emitted files for inspection -------------------------------------

  // -- Dedicated style emitters -----------------------------------------------

  test("block/styles.ts -> dedicated BlockStyles"):
    val rast  = loadRast("/rast/mermaid/src/diagrams/block/styles.rast.json")
    val scala = balticporter.corpus.mermaid.MermaidEmitter.emitBlockStyles(rast)
    println("=== BlockStyles.scala (dedicated) ===")
    println(scala)
    assert(scala.contains("object BlockStyles"), "should emit BlockStyles object")
    assert(scala.contains("def generate(vars: ThemeVariables)"), "should have generate method")
    assert(scala.contains("vars."), "should reference vars.xxx fields")
    assert(!scala.contains("options."), "should NOT reference options.xxx")
    assert(!scala.contains("fade("), "should NOT contain fade() calls")

  test("flowchart/styles.ts -> dedicated FlowchartStyles with edgeClass"):
    val rast  = loadRast("/rast/mermaid/src/diagrams/flowchart/styles.rast.json")
    val scala = balticporter.corpus.mermaid.MermaidEmitter.emitFlowchartStyles(rast)
    println("=== FlowchartStyles.scala (dedicated) ===")
    println(scala)
    assert(scala.contains("object FlowchartStyles"), "should emit FlowchartStyles object")
    assert(scala.contains("def generate(vars: ThemeVariables)"), "should have generate method")
    assert(scala.contains("def edgeClass(stroke: String)"), "should have edgeClass method")
    assert(scala.contains("def nodeClass(index: Int)"), "should have nodeClass method")
    assert(scala.contains("vars."), "should reference vars.xxx fields")
    assert(!scala.contains("options."), "should NOT reference options.xxx")
    assert(scala.contains("edge-pattern-dotted"), "should have edge patterns")

  test("mindmap/styles.ts -> dedicated MindmapStyles with cScale loop"):
    val rast  = loadRast("/rast/mermaid/src/diagrams/mindmap/styles.rast.json")
    val scala = balticporter.corpus.mermaid.MermaidEmitter.emitMindmapStyles(rast)
    println("=== MindmapStyles.scala (dedicated) ===")
    println(scala)
    assert(scala.contains("object MindmapStyles"), "should emit MindmapStyles object")
    assert(scala.contains("def generate(vars: ThemeVariables)"), "should have generate method")
    assert(scala.contains("vars.cScale(i)"), "should use cScale with index")
    assert(scala.contains("THEME_COLOR_LIMIT"), "should iterate up to THEME_COLOR_LIMIT")
    assert(scala.contains("mindmap-shape"), "should have mindmap-shape CSS class")
    assert(!scala.contains("options"), "should NOT reference options")

  test("write all emitted files to target/emitted-mermaid"):
    val outDir = java.nio.file.Path.of(sys.props.getOrElse("user.dir", ".")).resolve("target/emitted-mermaid")
    java.nio.file.Files.createDirectories(outDir)

    val emitted = scala.collection.mutable.LinkedHashMap.empty[String, String]

    // Styles - use dedicated emitters
    val pieStylesRast = loadRast("/rast/mermaid/src/diagrams/pie/pieStyles.rast.json")
    emitted("PieStyles") = balticporter.corpus.mermaid.MermaidEmitter.emitPieStyles(pieStylesRast)
    val flowchartStylesRast = loadRast("/rast/mermaid/src/diagrams/flowchart/styles.rast.json")
    emitted("FlowchartStyles") = balticporter.corpus.mermaid.MermaidEmitter.emitFlowchartStyles(flowchartStylesRast)
    val blockStylesRast = loadRast("/rast/mermaid/src/diagrams/block/styles.rast.json")
    emitted("BlockStyles") = balticporter.corpus.mermaid.MermaidEmitter.emitBlockStyles(blockStylesRast)
    val packetStylesRast = loadRast("/rast/mermaid/src/diagrams/packet/styles.rast.json")
    emitted("PacketStyles") = balticporter.corpus.mermaid.MermaidEmitter.emitPacketStyles(packetStylesRast)
    val mindmapStylesRast = loadRast("/rast/mermaid/src/diagrams/mindmap/styles.rast.json")
    emitted("MindmapStyles") = balticporter.corpus.mermaid.MermaidEmitter.emitMindmapStyles(mindmapStylesRast)

    // Utility
    val commentsRast = loadRast("/rast/mermaid/src/diagram-api/comments.rast.json")
    emitted("Comments") = balticporter.corpus.mermaid.MermaidEmitter.emitUtility(commentsRast, "Comments", "")

    // Accessibility
    val a11yRast = loadRast("/rast/mermaid/src/accessibility.rast.json")
    emitted("Accessibility") = balticporter.corpus.mermaid.MermaidEmitter.emitAccessibility(a11yRast)

    for ((name, source) <- emitted) {
      val path = outDir.resolve(s"$name.scala")
      java.nio.file.Files.writeString(path, source)
      println(s"[emit] $name.scala: ${source.linesIterator.size} lines -> $path")
    }
    println(s"[emit] Total: ${emitted.size} files written to $outDir")

  // -- Renderer emission (D3 -> SvgBuilder) -----------------------------------

  test("infoRenderer.ts -> InfoRenderer with SvgBuilder calls"):
    val rast  = loadRast("/rast/mermaid/src/diagrams/info/infoRenderer.rast.json")
    val scala = balticporter.corpus.mermaid.MermaidEmitter.emitRenderer(rast, "InfoRenderer", "info")
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
    val rast  = loadRast("/rast/mermaid/src/diagrams/error/errorRenderer.rast.json")
    val scala = balticporter.corpus.mermaid.MermaidEmitter.emitRenderer(rast, "ErrorRenderer", "error_")
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
    val rast  = loadRast("/rast/mermaid/src/diagrams/pie/pieRenderer.rast.json")
    val scala = balticporter.corpus.mermaid.MermaidEmitter.emitRenderer(rast, "PieRenderer", "pie")
    println("=== PieRenderer.scala (emitted) ===")
    println(scala)
    assert(scala.contains("object PieRenderer"), "should emit PieRenderer object")
    assert(scala.contains("def render("), "should have render method")
    assert(scala.contains("SvgBuilder"), "should use SvgBuilder")
    assert(scala.contains(".append("), "should have append calls")
    assert(scala.contains(".attr("), "should have attr calls")
    assert(scala.contains(".build().toMarkup()"), "should produce SVG output")

  test("renderer emitter linearizes D3 chains correctly"):
    val rast  = loadRast("/rast/mermaid/src/diagrams/info/infoRenderer.rast.json")
    val scala = balticporter.corpus.mermaid.MermaidEmitter.emitRenderer(rast, "InfoRenderer", "info")
    // The info renderer has a chain: group.append('text').attr('x', 100).attr('y', 40)...
    // This should be linearized into separate statements
    val lines = scala.linesIterator.toList
    // Check that append, attr, style, text are on separate lines
    val appendLines = lines.filter(_.contains(".append("))
    val attrLines   = lines.filter(_.contains(".attr("))
    assert(appendLines.nonEmpty, "should have separate append lines")
    assert(attrLines.nonEmpty, "should have separate attr lines")

  test("renderer emitter writes output files"):
    val outDir = java.nio.file.Path.of(sys.props.getOrElse("user.dir", ".")).resolve("target/emitted-mermaid")
    java.nio.file.Files.createDirectories(outDir)

    for (
      (rastPath, objectName, pkg) <- List(
        ("/rast/mermaid/src/diagrams/info/infoRenderer.rast.json", "InfoRenderer", "info"),
        ("/rast/mermaid/src/diagrams/error/errorRenderer.rast.json", "ErrorRenderer", "error_")
      )
    ) {
      val rast  = loadRast(rastPath)
      val scala = balticporter.corpus.mermaid.MermaidEmitter.emitRenderer(rast, objectName, pkg)
      val path  = outDir.resolve(s"$objectName.scala")
      java.nio.file.Files.writeString(path, scala)
      println(s"[emit] $objectName.scala: ${scala.linesIterator.size} lines -> $path")
    }

  // -- Complete Info diagram emission -----------------------------------------

  test("infoDb.ts -> InfoDb class with version/accTitle/accDescription"):
    val rast  = loadRast("/rast/mermaid/src/diagrams/info/infoDb.rast.json")
    val scala = balticporter.corpus.mermaid.MermaidEmitter.emitInfoDb(rast)
    println("=== InfoDb.scala (emitted) ===")
    println(scala)
    assert(scala.contains("final class InfoDb"), "should emit InfoDb class")
    assert(scala.contains("var version"), "should have version field")
    assert(scala.contains("UpstreamVersion"), "should reference UpstreamVersion")
    assert(scala.contains("def clear()"), "should have clear method")
    assert(scala.contains("accTitle"), "should have accTitle field")
    assert(scala.contains("accDescription"), "should have accDescription field")

  test("infoDiagram.ts -> InfoDiagram facade"):
    val rast  = loadRast("/rast/mermaid/src/diagrams/info/infoDiagram.rast.json")
    val scala = balticporter.corpus.mermaid.MermaidEmitter.emitInfoDiagram(rast)
    println("=== InfoDiagram.scala (emitted) ===")
    println(scala)
    assert(scala.contains("object InfoDiagram"), "should emit InfoDiagram object")
    assert(scala.contains("def detect(text: String): Boolean"), "should have detect method")
    assert(scala.contains("def parse(text: String): InfoDb"), "should have parse method")
    assert(scala.contains("def render(text: String, config: MermaidConfig"), "should have render method")
    assert(scala.contains("InfoParser.parse(text)"), "should delegate to InfoParser")
    assert(scala.contains("InfoRenderer.render(db, config)"), "should delegate to InfoRenderer")

  test("infoParser.ts -> InfoParser"):
    val rast  = loadRast("/rast/mermaid/src/diagrams/info/infoParser.rast.json")
    val scala = balticporter.corpus.mermaid.MermaidEmitter.emitInfoParser(rast)
    println("=== InfoParser.scala (emitted) ===")
    println(scala)
    assert(scala.contains("object InfoParser"), "should emit InfoParser object")
    assert(scala.contains("def parse(input: String): InfoDb"), "should have parse method")
    assert(scala.contains("new InfoDb"), "should create InfoDb")

  test("infoRenderer.ts -> complete InfoRenderer with theming and a11y"):
    val rast  = loadRast("/rast/mermaid/src/diagrams/info/infoRenderer.rast.json")
    val scala = balticporter.corpus.mermaid.MermaidEmitter.emitInfoRenderer(rast)
    println("=== InfoRenderer.scala (complete emitted) ===")
    println(scala)
    assert(scala.contains("object InfoRenderer"), "should emit InfoRenderer object")
    assert(scala.contains("def render(db: InfoDb, config: MermaidConfig)"), "should accept InfoDb and MermaidConfig")
    assert(scala.contains("SvgBuilder.createSvg"), "should create SVG")
    assert(scala.contains("Accessibility.applyTo"), "should apply accessibility")
    assert(scala.contains("Theme.getThemeByName"), "should look up theme")
    assert(scala.contains("InfoStyles.generate"), "should generate CSS")
    assert(scala.contains("CssGenerator.generateBaseStyles"), "should generate base CSS")
    assert(scala.contains("db.version"), "should reference db.version")
    assert(scala.contains(".build().toMarkup()"), "should produce SVG output")

  test("info styles -> InfoStyles"):
    // InfoStyles uses the infoDb RAST as a reference (no separate styles RAST exists)
    val rast  = loadRast("/rast/mermaid/src/diagrams/info/infoDb.rast.json")
    val scala = balticporter.corpus.mermaid.MermaidEmitter.emitInfoStyles(rast)
    println("=== InfoStyles.scala (emitted) ===")
    println(scala)
    assert(scala.contains("object InfoStyles"), "should emit InfoStyles object")
    assert(scala.contains("def generate(vars: ThemeVariables): String"), "should have generate method")
    assert(scala.contains("vars.textColor"), "should reference textColor")
    assert(scala.contains("vars.fontFamily"), "should reference fontFamily")

  // -- Complete Error diagram emission ----------------------------------------

  test("errorDiagram.ts -> ErrorDb class"):
    val rast  = loadRast("/rast/mermaid/src/diagrams/error/errorDiagram.rast.json")
    val scala = balticporter.corpus.mermaid.MermaidEmitter.emitErrorDb(rast)
    println("=== ErrorDb.scala (emitted) ===")
    println(scala)
    assert(scala.contains("final class ErrorDb"), "should emit ErrorDb class")
    assert(scala.contains("var errorMessage"), "should have errorMessage field")
    assert(scala.contains("def clear()"), "should have clear method")

  test("errorDiagram.ts -> ErrorDiagram facade"):
    val rast  = loadRast("/rast/mermaid/src/diagrams/error/errorDiagram.rast.json")
    val scala = balticporter.corpus.mermaid.MermaidEmitter.emitErrorDiagram(rast)
    println("=== ErrorDiagram.scala (emitted) ===")
    println(scala)
    assert(scala.contains("object ErrorDiagram"), "should emit ErrorDiagram object")
    assert(scala.contains("def detect(text: String): Boolean"), "should have detect method")
    assert(scala.contains("def parse(text: String): ErrorDb"), "should have parse method")
    assert(scala.contains("def render(text: String, config: MermaidConfig"), "should have render method")
    assert(scala.contains("def renderError(message: String"), "should have renderError method")
    assert(scala.contains("ErrorParser.parse(text)"), "should delegate to ErrorParser")
    assert(scala.contains("ErrorRenderer.render(db, config)"), "should delegate to ErrorRenderer")

  test("errorDiagram.ts -> ErrorParser"):
    val rast  = loadRast("/rast/mermaid/src/diagrams/error/errorDiagram.rast.json")
    val scala = balticporter.corpus.mermaid.MermaidEmitter.emitErrorParser(rast)
    println("=== ErrorParser.scala (emitted) ===")
    println(scala)
    assert(scala.contains("object ErrorParser"), "should emit ErrorParser object")
    assert(scala.contains("def parse(input: String): ErrorDb"), "should have parse method")
    assert(scala.contains("new ErrorDb"), "should create ErrorDb")
    assert(scala.contains("db.errorMessage = cleaned"), "should set error message")

  test("errorRenderer.ts -> complete ErrorRenderer with theming"):
    val rast  = loadRast("/rast/mermaid/src/diagrams/error/errorRenderer.rast.json")
    val scala = balticporter.corpus.mermaid.MermaidEmitter.emitErrorRenderer(rast)
    println("=== ErrorRenderer.scala (complete emitted) ===")
    println(scala)
    assert(scala.contains("object ErrorRenderer"), "should emit ErrorRenderer object")
    assert(scala.contains("def render(db: ErrorDb, config: MermaidConfig)"), "should accept ErrorDb and MermaidConfig")
    assert(scala.contains("SvgBuilder.createSvg"), "should create SVG")
    assert(scala.contains("Theme.getThemeByName"), "should look up theme")
    assert(scala.contains("ErrorStyles.generate"), "should generate CSS")
    assert(scala.contains("db.errorMessage"), "should reference db.errorMessage")
    assert(scala.contains(".build().toMarkup()"), "should produce SVG output")
    // Error icon elements
    assert(scala.contains("circle"), "should have circle element")
    assert(scala.contains("\"errorText\""), "should have errorText class")

  test("error styles -> ErrorStyles"):
    val rast  = loadRast("/rast/mermaid/src/diagrams/error/errorDiagram.rast.json")
    val scala = balticporter.corpus.mermaid.MermaidEmitter.emitErrorStyles(rast)
    println("=== ErrorStyles.scala (emitted) ===")
    println(scala)
    assert(scala.contains("object ErrorStyles"), "should emit ErrorStyles object")
    assert(scala.contains("def generate(vars: ThemeVariables): String"), "should have generate method")
    assert(scala.contains("vars.fontFamily"), "should reference fontFamily")
    assert(scala.contains("#cc0000"), "should have error color")

  // -- Write complete diagram files -------------------------------------------

  test("write complete info and error diagram files"):
    val outDir = java.nio.file.Path.of(sys.props.getOrElse("user.dir", ".")).resolve("target/emitted-mermaid")
    java.nio.file.Files.createDirectories(outDir)

    // Info diagram
    val infoDbRast       = loadRast("/rast/mermaid/src/diagrams/info/infoDb.rast.json")
    val infoDiagramRast  = loadRast("/rast/mermaid/src/diagrams/info/infoDiagram.rast.json")
    val infoParserRast   = loadRast("/rast/mermaid/src/diagrams/info/infoParser.rast.json")
    val infoRendererRast = loadRast("/rast/mermaid/src/diagrams/info/infoRenderer.rast.json")

    val infoFiles = List(
      ("InfoDb", balticporter.corpus.mermaid.MermaidEmitter.emitInfoDb(infoDbRast)),
      ("InfoDiagram", balticporter.corpus.mermaid.MermaidEmitter.emitInfoDiagram(infoDiagramRast)),
      ("InfoParser", balticporter.corpus.mermaid.MermaidEmitter.emitInfoParser(infoParserRast)),
      ("InfoRenderer", balticporter.corpus.mermaid.MermaidEmitter.emitInfoRenderer(infoRendererRast)),
      ("InfoStyles", balticporter.corpus.mermaid.MermaidEmitter.emitInfoStyles(infoDbRast))
    )

    // Error diagram
    val errorDiagramRast  = loadRast("/rast/mermaid/src/diagrams/error/errorDiagram.rast.json")
    val errorRendererRast = loadRast("/rast/mermaid/src/diagrams/error/errorRenderer.rast.json")

    val errorFiles = List(
      ("ErrorDb", balticporter.corpus.mermaid.MermaidEmitter.emitErrorDb(errorDiagramRast)),
      ("ErrorDiagram", balticporter.corpus.mermaid.MermaidEmitter.emitErrorDiagram(errorDiagramRast)),
      ("ErrorParser", balticporter.corpus.mermaid.MermaidEmitter.emitErrorParser(errorDiagramRast)),
      ("ErrorRenderer", balticporter.corpus.mermaid.MermaidEmitter.emitErrorRenderer(errorRendererRast)),
      ("ErrorStyles", balticporter.corpus.mermaid.MermaidEmitter.emitErrorStyles(errorDiagramRast))
    )

    for ((name, source) <- infoFiles ++ errorFiles) {
      val path = outDir.resolve(s"$name.scala")
      java.nio.file.Files.writeString(path, source)
      println(s"[emit] $name.scala: ${source.linesIterator.size} lines -> $path")
    }
    println(s"[emit] Total: ${(infoFiles ++ errorFiles).size} diagram files written")

  // -- Complete Pie diagram emission -------------------------------------------

  test("pieStyles.ts -> complete PieStyles with CSS classes"):
    val rast  = loadRast("/rast/mermaid/src/diagrams/pie/pieStyles.rast.json")
    val scala = balticporter.corpus.mermaid.MermaidEmitter.emitPieStyles(rast)
    println("=== PieStyles.scala (complete emitted) ===")
    println(scala)
    assert(scala.contains("object PieStyles"), "should emit PieStyles object")
    assert(scala.contains("def generate(vars: ThemeVariables)"), "should have generate method")
    assert(scala.contains(".pieTitleText") || scala.contains("pieTitleText"), "should contain pieTitleText CSS class")
    assert(scala.contains(".pieCircle") || scala.contains("pieCircle"), "should contain pieCircle CSS class")

  test("pieRenderer.ts -> PieDb class with sections"):
    val rast  = loadRast("/rast/mermaid/src/diagrams/pie/pieRenderer.rast.json")
    val scala = balticporter.corpus.mermaid.MermaidEmitter.emitPieDb(rast)
    println("=== PieDb.scala (emitted) ===")
    println(scala)
    assert(scala.contains("final case class PieSection"), "should have PieSection case class")
    assert(scala.contains("final class PieDb"), "should have PieDb class")
    assert(scala.contains("val sections"), "should have sections field")
    assert(scala.contains("var showData"), "should have showData field")
    assert(scala.contains("def addSection"), "should have addSection method")
    assert(scala.contains("def total"), "should have total method")
    assert(scala.contains("def clear()"), "should have clear method")

  test("pieRenderer.ts -> PieDiagram facade"):
    val rast  = loadRast("/rast/mermaid/src/diagrams/pie/pieRenderer.rast.json")
    val scala = balticporter.corpus.mermaid.MermaidEmitter.emitPieDiagram(rast)
    println("=== PieDiagram.scala (emitted) ===")
    println(scala)
    assert(scala.contains("object PieDiagram"), "should emit PieDiagram object")
    assert(scala.contains("def detect(text: String): Boolean"), "should have detect method")
    assert(scala.contains("def parse(text: String): PieDb"), "should have parse method")
    assert(scala.contains("def render("), "should have render method")
    assert(scala.contains("PieParser.parse"), "should delegate to PieParser")
    assert(scala.contains("PieRenderer.render"), "should delegate to PieRenderer")

  test("pieRenderer.ts -> PieParser"):
    val rast  = loadRast("/rast/mermaid/src/diagrams/pie/pieRenderer.rast.json")
    val scala = balticporter.corpus.mermaid.MermaidEmitter.emitPieParser(rast)
    println("=== PieParser.scala (emitted) ===")
    println(scala)
    assert(scala.contains("object PieParser"), "should emit PieParser object")
    assert(scala.contains("def parse(input: String): PieDb"), "should have parse method")
    assert(scala.contains("parsePieHeader"), "should have parsePieHeader")
    assert(scala.contains("parseSection"), "should have parseSection")
    assert(scala.contains("readQuotedString"), "should read quoted strings")
    assert(scala.contains("showData"), "should handle showData")
    assert(scala.contains("accTitle"), "should handle accTitle")

  test("pieRenderer.ts -> PieRenderer with arcs, labels, and legend"):
    val rast  = loadRast("/rast/mermaid/src/diagrams/pie/pieRenderer.rast.json")
    val scala = balticporter.corpus.mermaid.MermaidEmitter.emitPieRenderer(rast)
    println("=== PieRenderer.scala (emitted) ===")
    println(scala)
    assert(scala.contains("object PieRenderer"), "should emit PieRenderer object")
    assert(scala.contains("def render(db: PieDb, config: MermaidConfig)"), "should accept PieDb and MermaidConfig")
    assert(scala.contains("SvgBuilder.createSvg"), "should create SVG")
    assert(scala.contains("Accessibility.applyTo"), "should apply accessibility")
    assert(scala.contains("createArcPath"), "should have arc path calculation")
    assert(scala.contains("math.Pi"), "should use Pi for arc math")
    assert(scala.contains("defaultPieColor"), "should have default pie colors")
    assert(scala.contains("legend"), "should render legend")
    assert(scala.contains(".build().toMarkup()"), "should produce SVG output")

  // -- Complete Packet diagram emission ----------------------------------------

  test("packet -> PacketDb class with fields"):
    val rast  = loadRast("/rast/mermaid/src/diagrams/packet/styles.rast.json")
    val scala = balticporter.corpus.mermaid.MermaidEmitter.emitPacketDb(rast)
    println("=== PacketDb.scala (emitted) ===")
    println(scala)
    assert(scala.contains("final case class PacketField"), "should have PacketField case class")
    assert(scala.contains("final class PacketDb"), "should have PacketDb class")
    assert(scala.contains("var bitsPerRow"), "should have bitsPerRow field")
    assert(scala.contains("def addField"), "should have addField method")
    assert(scala.contains("def clear()"), "should have clear method")
    assert(scala.contains("endBit < startBit"), "should validate field ranges")

  test("packet -> PacketDiagram facade"):
    val rast  = loadRast("/rast/mermaid/src/diagrams/packet/styles.rast.json")
    val scala = balticporter.corpus.mermaid.MermaidEmitter.emitPacketDiagram(rast)
    println("=== PacketDiagram.scala (emitted) ===")
    println(scala)
    assert(scala.contains("object PacketDiagram"), "should emit PacketDiagram object")
    assert(scala.contains("packet-beta"), "should detect packet-beta keyword")
    assert(scala.contains("PacketParser.parse"), "should delegate to PacketParser")
    assert(scala.contains("PacketRenderer.render"), "should delegate to PacketRenderer")

  test("packet -> PacketParser"):
    val rast  = loadRast("/rast/mermaid/src/diagrams/packet/styles.rast.json")
    val scala = balticporter.corpus.mermaid.MermaidEmitter.emitPacketParser(rast)
    println("=== PacketParser.scala (emitted) ===")
    println(scala)
    assert(scala.contains("object PacketParser"), "should emit PacketParser object")
    assert(scala.contains("packet-beta"), "should parse packet-beta keyword")
    assert(scala.contains("tryParseField"), "should have tryParseField method")
    assert(scala.contains("startBit"), "should parse start bit")
    assert(scala.contains("endBit"), "should parse end bit")

  test("packet -> PacketRenderer"):
    val rast  = loadRast("/rast/mermaid/src/diagrams/packet/styles.rast.json")
    val scala = balticporter.corpus.mermaid.MermaidEmitter.emitPacketRenderer(rast)
    println("=== PacketRenderer.scala (emitted) ===")
    println(scala)
    assert(scala.contains("object PacketRenderer"), "should emit PacketRenderer object")
    assert(scala.contains("def render(db: PacketDb, config: MermaidConfig)"), "should accept PacketDb")
    assert(scala.contains("SvgBuilder.createSvg"), "should create SVG")
    assert(scala.contains("packetField"), "should have packetField CSS class")
    assert(scala.contains("packetFieldLabel"), "should have packetFieldLabel CSS class")
    assert(scala.contains(".build().toMarkup()"), "should produce SVG output")

  test("packet -> PacketStyles"):
    val rast  = loadRast("/rast/mermaid/src/diagrams/packet/styles.rast.json")
    val scala = balticporter.corpus.mermaid.MermaidEmitter.emitPacketStyles(rast)
    println("=== PacketStyles.scala (emitted) ===")
    println(scala)
    assert(scala.contains("object PacketStyles"), "should emit PacketStyles object")
    assert(scala.contains("def generate(vars: ThemeVariables)"), "should have generate method")

  // -- Complete Kanban diagram emission ----------------------------------------

  test("kanban -> KanbanDb class"):
    // Kanban has no RAST file (SSG-native), use any RAST as dummy
    val rast  = loadRast("/rast/mermaid/src/diagrams/info/infoDb.rast.json")
    val scala = balticporter.corpus.mermaid.MermaidEmitter.emitKanbanDb(rast)
    println("=== KanbanDb.scala (emitted) ===")
    println(scala)
    assert(scala.contains("final case class KanbanCard"), "should have KanbanCard case class")
    assert(scala.contains("final case class KanbanColumn"), "should have KanbanColumn case class")
    assert(scala.contains("final class KanbanDb"), "should have KanbanDb class")
    assert(scala.contains("def addColumn"), "should have addColumn method")
    assert(scala.contains("def addCard"), "should have addCard method")
    assert(scala.contains("def addCardToLast"), "should have addCardToLast method")
    assert(scala.contains("def clear()"), "should have clear method")

  test("kanban -> KanbanDiagram facade"):
    val rast  = loadRast("/rast/mermaid/src/diagrams/info/infoDb.rast.json")
    val scala = balticporter.corpus.mermaid.MermaidEmitter.emitKanbanDiagram(rast)
    println("=== KanbanDiagram.scala (emitted) ===")
    println(scala)
    assert(scala.contains("object KanbanDiagram"), "should emit KanbanDiagram object")
    assert(scala.contains("def detect(text: String): Boolean"), "should have detect method")
    assert(scala.contains("\"kanban\""), "should detect kanban keyword")
    assert(scala.contains("KanbanParser.parse"), "should delegate to KanbanParser")
    assert(scala.contains("KanbanRenderer.render"), "should delegate to KanbanRenderer")

  test("kanban -> KanbanParser"):
    val rast  = loadRast("/rast/mermaid/src/diagrams/info/infoDb.rast.json")
    val scala = balticporter.corpus.mermaid.MermaidEmitter.emitKanbanParser(rast)
    println("=== KanbanParser.scala (emitted) ===")
    println(scala)
    assert(scala.contains("object KanbanParser"), "should emit KanbanParser object")
    assert(scala.contains("def parse(input: String): KanbanDb"), "should have parse method")
    assert(scala.contains("\"kanban\""), "should parse kanban keyword")
    assert(scala.contains("parseIdLabel"), "should have parseIdLabel method")
    assert(scala.contains("addColumn"), "should create columns")
    assert(scala.contains("addCardToLast"), "should add cards to last column")

  test("kanban -> KanbanRenderer"):
    val rast  = loadRast("/rast/mermaid/src/diagrams/info/infoDb.rast.json")
    val scala = balticporter.corpus.mermaid.MermaidEmitter.emitKanbanRenderer(rast)
    println("=== KanbanRenderer.scala (emitted) ===")
    println(scala)
    assert(scala.contains("object KanbanRenderer"), "should emit KanbanRenderer object")
    assert(scala.contains("def render(db: KanbanDb, config: MermaidConfig)"), "should accept KanbanDb")
    assert(scala.contains("SvgBuilder.createSvg"), "should create SVG")
    assert(scala.contains("kanbanColumn"), "should have kanbanColumn CSS class")
    assert(scala.contains("kanbanCard"), "should have kanbanCard CSS class")
    assert(scala.contains(".build().toMarkup()"), "should produce SVG output")

  test("kanban -> KanbanStyles"):
    val rast  = loadRast("/rast/mermaid/src/diagrams/info/infoDb.rast.json")
    val scala = balticporter.corpus.mermaid.MermaidEmitter.emitKanbanStyles(rast)
    println("=== KanbanStyles.scala (emitted) ===")
    println(scala)
    assert(scala.contains("object KanbanStyles"), "should emit KanbanStyles object")
    assert(scala.contains("def generate(vars: ThemeVariables)"), "should have generate method")
    assert(scala.contains("kanbanColumn"), "should have kanbanColumn style")
    assert(scala.contains("kanbanCard"), "should have kanbanCard style")

  // -- Write complete pie/packet/kanban diagram files ---------------------------

  test("write complete pie, packet, and kanban diagram files"):
    val outDir = java.nio.file.Path.of(sys.props.getOrElse("user.dir", ".")).resolve("target/emitted-mermaid")
    java.nio.file.Files.createDirectories(outDir)

    val pieStylesRast    = loadRast("/rast/mermaid/src/diagrams/pie/pieStyles.rast.json")
    val pieRendererRast  = loadRast("/rast/mermaid/src/diagrams/pie/pieRenderer.rast.json")
    val packetStylesRast = loadRast("/rast/mermaid/src/diagrams/packet/styles.rast.json")
    val dummyRast        = loadRast("/rast/mermaid/src/diagrams/info/infoDb.rast.json")

    val allFiles = List(
      ("PieDb", balticporter.corpus.mermaid.MermaidEmitter.emitPieDb(pieRendererRast)),
      ("PieDiagram", balticporter.corpus.mermaid.MermaidEmitter.emitPieDiagram(pieRendererRast)),
      ("PieParser", balticporter.corpus.mermaid.MermaidEmitter.emitPieParser(pieRendererRast)),
      ("PieRenderer", balticporter.corpus.mermaid.MermaidEmitter.emitPieRenderer(pieRendererRast)),
      ("PieStyles", balticporter.corpus.mermaid.MermaidEmitter.emitPieStyles(pieStylesRast)),
      ("PacketDb", balticporter.corpus.mermaid.MermaidEmitter.emitPacketDb(packetStylesRast)),
      ("PacketDiagram", balticporter.corpus.mermaid.MermaidEmitter.emitPacketDiagram(packetStylesRast)),
      ("PacketParser", balticporter.corpus.mermaid.MermaidEmitter.emitPacketParser(packetStylesRast)),
      ("PacketRenderer", balticporter.corpus.mermaid.MermaidEmitter.emitPacketRenderer(packetStylesRast)),
      ("PacketStyles", balticporter.corpus.mermaid.MermaidEmitter.emitPacketStyles(packetStylesRast)),
      ("KanbanDb", balticporter.corpus.mermaid.MermaidEmitter.emitKanbanDb(dummyRast)),
      ("KanbanDiagram", balticporter.corpus.mermaid.MermaidEmitter.emitKanbanDiagram(dummyRast)),
      ("KanbanParser", balticporter.corpus.mermaid.MermaidEmitter.emitKanbanParser(dummyRast)),
      ("KanbanRenderer", balticporter.corpus.mermaid.MermaidEmitter.emitKanbanRenderer(dummyRast)),
      ("KanbanStyles", balticporter.corpus.mermaid.MermaidEmitter.emitKanbanStyles(dummyRast))
    )

    for ((name, source) <- allFiles) {
      val path = outDir.resolve(s"$name.scala")
      java.nio.file.Files.writeString(path, source)
      println(s"[emit] $name.scala: ${source.linesIterator.size} lines -> $path")
    }
    println(s"[emit] Total: ${allFiles.size} diagram files written")

  // -- Complete Cynefin diagram emission ----------------------------------------

  test("cynefin -> CynefinDb class"):
    val rast  = loadRast("/rast/mermaid/src/diagrams/info/infoDb.rast.json")
    val scala = balticporter.corpus.mermaid.MermaidEmitter.emitCynefinDb(rast)
    println("=== CynefinDb.scala (emitted) ===")
    println(scala)
    assert(scala.contains("final case class CynefinItem"), "should have CynefinItem case class")
    assert(scala.contains("final class CynefinDb"), "should have CynefinDb class")
    assert(scala.contains("def addItem"), "should have addItem method")
    assert(scala.contains("def itemsInDomain"), "should have itemsInDomain method")
    assert(scala.contains("def clear()"), "should have clear method")

  test("cynefin -> CynefinDiagram facade"):
    val rast  = loadRast("/rast/mermaid/src/diagrams/info/infoDb.rast.json")
    val scala = balticporter.corpus.mermaid.MermaidEmitter.emitCynefinDiagram(rast)
    println("=== CynefinDiagram.scala (emitted) ===")
    println(scala)
    assert(scala.contains("object CynefinDiagram"), "should emit CynefinDiagram object")
    assert(scala.contains("def detect(text: String): Boolean"), "should have detect method")
    assert(scala.contains("\"cynefin\""), "should detect cynefin keyword")
    assert(scala.contains("CynefinParser.parse"), "should delegate to CynefinParser")
    assert(scala.contains("CynefinRenderer.render"), "should delegate to CynefinRenderer")

  test("cynefin -> CynefinParser"):
    val rast  = loadRast("/rast/mermaid/src/diagrams/info/infoDb.rast.json")
    val scala = balticporter.corpus.mermaid.MermaidEmitter.emitCynefinParser(rast)
    println("=== CynefinParser.scala (emitted) ===")
    println(scala)
    assert(scala.contains("object CynefinParser"), "should emit CynefinParser object")
    assert(scala.contains("def parse(input: String): CynefinDb"), "should have parse method")
    assert(scala.contains("\"cynefin\""), "should parse cynefin keyword")
    assert(scala.contains("addItem"), "should add items")
    assert(scala.contains("colonIdx"), "should parse domain:items")

  test("cynefin -> CynefinRenderer"):
    val rast  = loadRast("/rast/mermaid/src/diagrams/info/infoDb.rast.json")
    val scala = balticporter.corpus.mermaid.MermaidEmitter.emitCynefinRenderer(rast)
    println("=== CynefinRenderer.scala (emitted) ===")
    println(scala)
    assert(scala.contains("object CynefinRenderer"), "should emit CynefinRenderer object")
    assert(scala.contains("def render(db: CynefinDb, config: MermaidConfig)"), "should accept CynefinDb")
    assert(scala.contains("SvgBuilder.createSvg"), "should create SVG")
    assert(scala.contains("cynefinDomain"), "should have cynefinDomain CSS class")
    assert(scala.contains("cynefinTitle"), "should have cynefinTitle CSS class")
    assert(scala.contains("Disorder"), "should have Disorder center")
    assert(scala.contains(".build().toMarkup()"), "should produce SVG output")

  test("cynefin -> CynefinStyles"):
    val rast  = loadRast("/rast/mermaid/src/diagrams/info/infoDb.rast.json")
    val scala = balticporter.corpus.mermaid.MermaidEmitter.emitCynefinStyles(rast)
    println("=== CynefinStyles.scala (emitted) ===")
    println(scala)
    assert(scala.contains("object CynefinStyles"), "should emit CynefinStyles object")
    assert(scala.contains("def generate(vars: ThemeVariables)"), "should have generate method")
    assert(scala.contains("cynefinTitle"), "should have cynefinTitle style")
    assert(scala.contains("cynefinItem"), "should have cynefinItem style")

  // -- Complete TreeView diagram emission ----------------------------------------

  test("treeview -> TreeViewDb class"):
    val rast  = loadRast("/rast/mermaid/src/diagrams/info/infoDb.rast.json")
    val scala = balticporter.corpus.mermaid.MermaidEmitter.emitTreeviewDb(rast)
    println("=== TreeviewDb.scala (emitted) ===")
    println(scala)
    assert(scala.contains("final case class TreeNode"), "should have TreeNode case class")
    assert(scala.contains("final class TreeViewDb"), "should have TreeViewDb class")
    assert(scala.contains("def addRoot"), "should have addRoot method")
    assert(scala.contains("def clear()"), "should have clear method")

  test("treeview -> TreeViewDiagram facade"):
    val rast  = loadRast("/rast/mermaid/src/diagrams/info/infoDb.rast.json")
    val scala = balticporter.corpus.mermaid.MermaidEmitter.emitTreeviewDiagram(rast)
    println("=== TreeviewDiagram.scala (emitted) ===")
    println(scala)
    assert(scala.contains("object TreeViewDiagram"), "should emit TreeViewDiagram object")
    assert(scala.contains("def detect(text: String): Boolean"), "should have detect method")
    assert(scala.contains("\"treeview\""), "should detect treeview keyword")
    assert(scala.contains("TreeViewParser.parse"), "should delegate to TreeViewParser")
    assert(scala.contains("TreeViewRenderer.render"), "should delegate to TreeViewRenderer")

  test("treeview -> TreeViewParser"):
    val rast  = loadRast("/rast/mermaid/src/diagrams/info/infoDb.rast.json")
    val scala = balticporter.corpus.mermaid.MermaidEmitter.emitTreeviewParser(rast)
    println("=== TreeviewParser.scala (emitted) ===")
    println(scala)
    assert(scala.contains("object TreeViewParser"), "should emit TreeViewParser object")
    assert(scala.contains("def parse(input: String): TreeViewDb"), "should have parse method")
    assert(scala.contains("\"treeview\""), "should parse treeview keyword")
    assert(scala.contains("TreeNode"), "should create TreeNode")
    assert(scala.contains("stack"), "should use stack for indentation")

  test("treeview -> TreeViewRenderer"):
    val rast  = loadRast("/rast/mermaid/src/diagrams/info/infoDb.rast.json")
    val scala = balticporter.corpus.mermaid.MermaidEmitter.emitTreeviewRenderer(rast)
    println("=== TreeviewRenderer.scala (emitted) ===")
    println(scala)
    assert(scala.contains("object TreeViewRenderer"), "should emit TreeViewRenderer object")
    assert(scala.contains("def render(db: TreeViewDb, config: MermaidConfig)"), "should accept TreeViewDb")
    assert(scala.contains("SvgBuilder.createSvg"), "should create SVG")
    assert(scala.contains("treeNode"), "should have treeNode CSS class")
    assert(scala.contains("treeConnector"), "should have treeConnector CSS class")
    assert(scala.contains(".build().toMarkup()"), "should produce SVG output")

  test("treeview -> TreeViewStyles"):
    val rast  = loadRast("/rast/mermaid/src/diagrams/info/infoDb.rast.json")
    val scala = balticporter.corpus.mermaid.MermaidEmitter.emitTreeviewStyles(rast)
    println("=== TreeviewStyles.scala (emitted) ===")
    println(scala)
    assert(scala.contains("object TreeViewStyles"), "should emit TreeViewStyles object")
    assert(scala.contains("def generate(vars: ThemeVariables)"), "should have generate method")
    assert(scala.contains("treeNode"), "should have treeNode style")
    assert(scala.contains("treeConnector"), "should have treeConnector style")

  // -- Write cynefin and treeview diagram files ---------------------------------

  test("write complete cynefin and treeview diagram files"):
    val outDir = java.nio.file.Path.of(sys.props.getOrElse("user.dir", ".")).resolve("target/emitted-mermaid")
    java.nio.file.Files.createDirectories(outDir)

    val dummyRast = loadRast("/rast/mermaid/src/diagrams/info/infoDb.rast.json")

    val allFiles = List(
      ("CynefinDb", balticporter.corpus.mermaid.MermaidEmitter.emitCynefinDb(dummyRast)),
      ("CynefinDiagram", balticporter.corpus.mermaid.MermaidEmitter.emitCynefinDiagram(dummyRast)),
      ("CynefinParser", balticporter.corpus.mermaid.MermaidEmitter.emitCynefinParser(dummyRast)),
      ("CynefinRenderer", balticporter.corpus.mermaid.MermaidEmitter.emitCynefinRenderer(dummyRast)),
      ("CynefinStyles", balticporter.corpus.mermaid.MermaidEmitter.emitCynefinStyles(dummyRast)),
      ("TreeviewDb", balticporter.corpus.mermaid.MermaidEmitter.emitTreeviewDb(dummyRast)),
      ("TreeviewDiagram", balticporter.corpus.mermaid.MermaidEmitter.emitTreeviewDiagram(dummyRast)),
      ("TreeviewParser", balticporter.corpus.mermaid.MermaidEmitter.emitTreeviewParser(dummyRast)),
      ("TreeviewRenderer", balticporter.corpus.mermaid.MermaidEmitter.emitTreeviewRenderer(dummyRast)),
      ("TreeviewStyles", balticporter.corpus.mermaid.MermaidEmitter.emitTreeviewStyles(dummyRast))
    )

    for ((name, source) <- allFiles) {
      val path = outDir.resolve(s"$name.scala")
      java.nio.file.Files.writeString(path, source)
      println(s"[emit] $name.scala: ${source.linesIterator.size} lines -> $path")
    }
    println(s"[emit] Total: ${allFiles.size} cynefin+treeview diagram files written")

  // -- Complete Wardley diagram emission ----------------------------------------

  test("wardley -> WardleyDb class"):
    val rast  = loadRast("/rast/mermaid/src/diagrams/info/infoDb.rast.json")
    val scala = balticporter.corpus.mermaid.MermaidEmitter.emitWardleyDb(rast)
    assert(scala.contains("final case class WardleyComponent"), "should have WardleyComponent case class")
    assert(scala.contains("final case class WardleyLink"), "should have WardleyLink case class")
    assert(scala.contains("final class WardleyDb"), "should have WardleyDb class")
    assert(scala.contains("def addComponent"), "should have addComponent method")
    assert(scala.contains("def addLink"), "should have addLink method")
    assert(scala.contains("def clear()"), "should have clear method")

  test("wardley -> WardleyDiagram facade"):
    val rast  = loadRast("/rast/mermaid/src/diagrams/info/infoDb.rast.json")
    val scala = balticporter.corpus.mermaid.MermaidEmitter.emitWardleyDiagram(rast)
    assert(scala.contains("object WardleyDiagram"), "should emit WardleyDiagram object")
    assert(scala.contains("def detect(text: String): Boolean"), "should have detect method")
    assert(scala.contains("\"wardley\""), "should detect wardley keyword")
    assert(scala.contains("WardleyParser.parse"), "should delegate to WardleyParser")
    assert(scala.contains("WardleyRenderer.render"), "should delegate to WardleyRenderer")

  test("wardley -> WardleyParser"):
    val rast  = loadRast("/rast/mermaid/src/diagrams/info/infoDb.rast.json")
    val scala = balticporter.corpus.mermaid.MermaidEmitter.emitWardleyParser(rast)
    assert(scala.contains("object WardleyParser"), "should emit WardleyParser object")
    assert(scala.contains("def parse(input: String): WardleyDb"), "should have parse method")
    assert(scala.contains("\"wardley\""), "should parse wardley keyword")
    assert(scala.contains("component"), "should parse component lines")
    assert(scala.contains("-->"), "should parse dependency links")

  test("wardley -> WardleyRenderer"):
    val rast  = loadRast("/rast/mermaid/src/diagrams/info/infoDb.rast.json")
    val scala = balticporter.corpus.mermaid.MermaidEmitter.emitWardleyRenderer(rast)
    assert(scala.contains("object WardleyRenderer"), "should emit WardleyRenderer object")
    assert(scala.contains("def render(db: WardleyDb, config: MermaidConfig)"), "should accept WardleyDb")
    assert(scala.contains("SvgBuilder.createSvg"), "should create SVG")
    assert(scala.contains("wardleyComponent"), "should have wardleyComponent CSS class")
    assert(scala.contains("wardleyLink"), "should have wardleyLink CSS class")
    assert(scala.contains(".build().toMarkup()"), "should produce SVG output")

  test("wardley -> WardleyStyles"):
    val rast  = loadRast("/rast/mermaid/src/diagrams/info/infoDb.rast.json")
    val scala = balticporter.corpus.mermaid.MermaidEmitter.emitWardleyStyles(rast)
    assert(scala.contains("object WardleyStyles"), "should emit WardleyStyles object")
    assert(scala.contains("def generate(vars: ThemeVariables)"), "should have generate method")
    assert(scala.contains("wardleyTitle"), "should have wardleyTitle style")
    assert(scala.contains("wardleyComponent"), "should have wardleyComponent style")

  // -- Complete Ishikawa diagram emission ----------------------------------------

  test("ishikawa -> IshikawaDb class"):
    val rast  = loadRast("/rast/mermaid/src/diagrams/info/infoDb.rast.json")
    val scala = balticporter.corpus.mermaid.MermaidEmitter.emitIshikawaDb(rast)
    assert(scala.contains("final case class CauseBranch"), "should have CauseBranch case class")
    assert(scala.contains("final class IshikawaDb"), "should have IshikawaDb class")
    assert(scala.contains("def setEffect"), "should have setEffect method")
    assert(scala.contains("def addBranch"), "should have addBranch method")
    assert(scala.contains("def addCause"), "should have addCause method")
    assert(scala.contains("def addCauseToLast"), "should have addCauseToLast method")
    assert(scala.contains("def clear()"), "should have clear method")

  test("ishikawa -> IshikawaDiagram facade"):
    val rast  = loadRast("/rast/mermaid/src/diagrams/info/infoDb.rast.json")
    val scala = balticporter.corpus.mermaid.MermaidEmitter.emitIshikawaDiagram(rast)
    assert(scala.contains("object IshikawaDiagram"), "should emit IshikawaDiagram object")
    assert(scala.contains("def detect(text: String): Boolean"), "should have detect method")
    assert(scala.contains("\"ishikawa\""), "should detect ishikawa keyword")
    assert(scala.contains("IshikawaParser.parse"), "should delegate to IshikawaParser")
    assert(scala.contains("IshikawaRenderer.render"), "should delegate to IshikawaRenderer")

  test("ishikawa -> IshikawaParser"):
    val rast  = loadRast("/rast/mermaid/src/diagrams/info/infoDb.rast.json")
    val scala = balticporter.corpus.mermaid.MermaidEmitter.emitIshikawaParser(rast)
    assert(scala.contains("object IshikawaParser"), "should emit IshikawaParser object")
    assert(scala.contains("def parse(input: String): IshikawaDb"), "should have parse method")
    assert(scala.contains("\"ishikawa\""), "should parse ishikawa keyword")
    assert(scala.contains("setEffect"), "should parse effect")
    assert(scala.contains("addBranch"), "should parse branches")
    assert(scala.contains("addCauseToLast"), "should add causes to last branch")

  test("ishikawa -> IshikawaRenderer"):
    val rast  = loadRast("/rast/mermaid/src/diagrams/info/infoDb.rast.json")
    val scala = balticporter.corpus.mermaid.MermaidEmitter.emitIshikawaRenderer(rast)
    assert(scala.contains("object IshikawaRenderer"), "should emit IshikawaRenderer object")
    assert(scala.contains("def render(db: IshikawaDb, config: MermaidConfig)"), "should accept IshikawaDb")
    assert(scala.contains("SvgBuilder.createSvg"), "should create SVG")
    assert(scala.contains("ishikawaSpine"), "should have ishikawaSpine CSS class")
    assert(scala.contains("ishikawaBranch"), "should have ishikawaBranch CSS class")
    assert(scala.contains("fishhead"), "should have arrowhead marker")
    assert(scala.contains(".build().toMarkup()"), "should produce SVG output")

  test("ishikawa -> IshikawaStyles"):
    val rast  = loadRast("/rast/mermaid/src/diagrams/info/infoDb.rast.json")
    val scala = balticporter.corpus.mermaid.MermaidEmitter.emitIshikawaStyles(rast)
    assert(scala.contains("object IshikawaStyles"), "should emit IshikawaStyles object")
    assert(scala.contains("def generate(vars: ThemeVariables)"), "should have generate method")
    assert(scala.contains("ishikawaSpine"), "should have ishikawaSpine style")
    assert(scala.contains("ishikawaEffect"), "should have ishikawaEffect style")
    assert(scala.contains("ishikawaBranch"), "should have ishikawaBranch style")
    assert(scala.contains("ishikawaCause"), "should have ishikawaCause style")

  // -- Complete Venn diagram emission ----------------------------------------

  test("venn -> VennDb class"):
    val rast  = loadRast("/rast/mermaid/src/diagrams/info/infoDb.rast.json")
    val scala = balticporter.corpus.mermaid.MermaidEmitter.emitVennDb(rast)
    assert(scala.contains("final case class VennSet"), "should have VennSet case class")
    assert(scala.contains("final case class VennIntersection"), "should have VennIntersection case class")
    assert(scala.contains("final class VennDb"), "should have VennDb class")
    assert(scala.contains("def addSet"), "should have addSet method")
    assert(scala.contains("def addIntersection"), "should have addIntersection method")
    assert(scala.contains("def clear()"), "should have clear method")

  test("venn -> VennDiagram facade"):
    val rast  = loadRast("/rast/mermaid/src/diagrams/info/infoDb.rast.json")
    val scala = balticporter.corpus.mermaid.MermaidEmitter.emitVennDiagram(rast)
    assert(scala.contains("object VennDiagram"), "should emit VennDiagram object")
    assert(scala.contains("def detect(text: String): Boolean"), "should have detect method")
    assert(scala.contains("\"venn-beta\""), "should detect venn-beta keyword")
    assert(scala.contains("VennParser.parse"), "should delegate to VennParser")
    assert(scala.contains("VennRenderer.render"), "should delegate to VennRenderer")

  test("venn -> VennParser"):
    val rast  = loadRast("/rast/mermaid/src/diagrams/info/infoDb.rast.json")
    val scala = balticporter.corpus.mermaid.MermaidEmitter.emitVennParser(rast)
    assert(scala.contains("object VennParser"), "should emit VennParser object")
    assert(scala.contains("def parse(input: String): VennDb"), "should have parse method")
    assert(scala.contains("\"venn-beta\""), "should parse venn-beta keyword")
    assert(scala.contains("tryParseSet"), "should have tryParseSet method")
    assert(scala.contains("tryParseIntersection"), "should have tryParseIntersection method")
    assert(scala.contains("Scanner"), "should use Scanner parser")

  test("venn -> VennRenderer"):
    val rast  = loadRast("/rast/mermaid/src/diagrams/info/infoDb.rast.json")
    val scala = balticporter.corpus.mermaid.MermaidEmitter.emitVennRenderer(rast)
    assert(scala.contains("object VennRenderer"), "should emit VennRenderer object")
    assert(scala.contains("def render(db: VennDb, config: MermaidConfig)"), "should accept VennDb")
    assert(scala.contains("SvgBuilder.createSvg"), "should create SVG")
    assert(scala.contains("vennSet"), "should have vennSet CSS class")
    assert(scala.contains("vennSetLabel"), "should have vennSetLabel CSS class")
    assert(scala.contains("vennIntersectionLabel"), "should have vennIntersectionLabel CSS class")
    assert(scala.contains(".build().toMarkup()"), "should produce SVG output")

  test("venn -> VennStyles"):
    val rast  = loadRast("/rast/mermaid/src/diagrams/info/infoDb.rast.json")
    val scala = balticporter.corpus.mermaid.MermaidEmitter.emitVennStyles(rast)
    assert(scala.contains("object VennStyles"), "should emit VennStyles object")
    assert(scala.contains("def generate(vars: ThemeVariables)"), "should have generate method")
    assert(scala.contains("vennTitle"), "should have vennTitle style")
    assert(scala.contains("vennSet"), "should have vennSet style")
    assert(scala.contains("vennSetLabel"), "should have vennSetLabel style")

  // -- Write wardley, ishikawa, venn diagram files ----------------------------

  test("write complete wardley, ishikawa, and venn diagram files"):
    val outDir = java.nio.file.Path.of(sys.props.getOrElse("user.dir", ".")).resolve("target/emitted-mermaid")
    java.nio.file.Files.createDirectories(outDir)

    val dummyRast = loadRast("/rast/mermaid/src/diagrams/info/infoDb.rast.json")

    val allFiles = List(
      ("WardleyDb", balticporter.corpus.mermaid.MermaidEmitter.emitWardleyDb(dummyRast)),
      ("WardleyDiagram", balticporter.corpus.mermaid.MermaidEmitter.emitWardleyDiagram(dummyRast)),
      ("WardleyParser", balticporter.corpus.mermaid.MermaidEmitter.emitWardleyParser(dummyRast)),
      ("WardleyRenderer", balticporter.corpus.mermaid.MermaidEmitter.emitWardleyRenderer(dummyRast)),
      ("WardleyStyles", balticporter.corpus.mermaid.MermaidEmitter.emitWardleyStyles(dummyRast)),
      ("IshikawaDb", balticporter.corpus.mermaid.MermaidEmitter.emitIshikawaDb(dummyRast)),
      ("IshikawaDiagram", balticporter.corpus.mermaid.MermaidEmitter.emitIshikawaDiagram(dummyRast)),
      ("IshikawaParser", balticporter.corpus.mermaid.MermaidEmitter.emitIshikawaParser(dummyRast)),
      ("IshikawaRenderer", balticporter.corpus.mermaid.MermaidEmitter.emitIshikawaRenderer(dummyRast)),
      ("IshikawaStyles", balticporter.corpus.mermaid.MermaidEmitter.emitIshikawaStyles(dummyRast)),
      ("VennDb", balticporter.corpus.mermaid.MermaidEmitter.emitVennDb(dummyRast)),
      ("VennDiagram", balticporter.corpus.mermaid.MermaidEmitter.emitVennDiagram(dummyRast)),
      ("VennParser", balticporter.corpus.mermaid.MermaidEmitter.emitVennParser(dummyRast)),
      ("VennRenderer", balticporter.corpus.mermaid.MermaidEmitter.emitVennRenderer(dummyRast)),
      ("VennStyles", balticporter.corpus.mermaid.MermaidEmitter.emitVennStyles(dummyRast))
    )

    for ((name, source) <- allFiles) {
      val path = outDir.resolve(s"$name.scala")
      java.nio.file.Files.writeString(path, source)
      println(s"[emit] $name.scala: ${source.linesIterator.size} lines -> $path")
    }
    println(s"[emit] Total: ${allFiles.size} wardley+ishikawa+venn diagram files written")

  // -- Styles parity-derive ---------------------------------------------------

  private val mermaidRefRoot: java.nio.file.Path =
    val cpRef = getClass.getResource("/reference/mermaid/flowchart/FlowchartStyles.scala")
    if cpRef != null && cpRef.getProtocol == "file" then java.nio.file.Path.of(cpRef.toURI).getParent.getParent // up from flowchart/ to mermaid/
    else
      val candidates = List(
        sys.props.get("ssg.root").map(java.nio.file.Path.of(_)),
        Some(java.nio.file.Path.of(sys.props.getOrElse("user.dir", ".")).getParent.resolve("ssg"))
      ).flatten
      candidates
        .map(_.resolve("ssg-mermaid/src/main/scala/ssg/mermaid/diagrams"))
        .find(p => java.nio.file.Files.exists(p.resolve("flowchart/FlowchartStyles.scala")))
        .getOrElse(java.nio.file.Path.of("nonexistent"))

  private def tryLoadRastOpt(resource: String): Option[RastFile] =
    val stream = getClass.getResourceAsStream(resource)
    if stream == null then None
    else
      try
        val json = new String(stream.readAllBytes(), java.nio.charset.StandardCharsets.UTF_8)
        stream.close()
        Some(Rast.readFile(json))
      catch
        case _: Exception =>
          stream.close()
          None

  test("styles parity: FlowchartStyles.scala"):
    if !java.nio.file.Files.exists(mermaidRefRoot.resolve("flowchart/FlowchartStyles.scala")) then println("SKIP: ssg-mermaid reference not found")
    else
      val rast              = loadRast("/rast/mermaid/src/diagrams/flowchart/styles.rast.json")
      val refPath           = mermaidRefRoot.resolve("flowchart/FlowchartStyles.scala")
      val (source, summary) = balticporter.corpus.mermaid.MermaidEmitter.emitStylesWithParity(rast, refPath)
      println(
        s"FlowchartStyles: ${summary.totalMethods} methods, ${summary.matchedFromRast} RAST, ${summary.keptFromReference} ref"
      )
      assert(source.contains("FlowchartStyles"), "should preserve FlowchartStyles object")

  test("batch: styles parity for all diagram types"):
    if !java.nio.file.Files.exists(mermaidRefRoot) then println("SKIP: ssg-mermaid reference not found at " + mermaidRefRoot)
    else
      val outDir    = java.nio.file.Path.of(sys.props.getOrElse("user.dir", ".")).resolve("target/emitted-mermaid-parity")
      val results   = balticporter.corpus.mermaid.MermaidEmitter.emitAllStylesWithParity(tryLoadRastOpt, mermaidRefRoot, outDir)
      val summaries = results.map(_._2)

      println("\n=== Mermaid Styles Parity ===")
      println(balticporter.corpus.mermaid.MermaidEmitter.formatStylesParitySummaryTable(summaries))
      println(s"Emitted ${results.size} styles modules to $outDir")

      assert(results.size >= 10, s"Expected >= 10 styles modules, got ${results.size}")
