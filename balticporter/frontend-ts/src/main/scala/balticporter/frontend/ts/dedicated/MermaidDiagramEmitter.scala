package balticporter.corpus.mermaid

import balticporter.frontend.ts.dedicated.{DefmethodBodyTranslator, DefmethodEntry, DefnodeClass, FreeFunction}

import balticporter.frontend.ts.{RastFile, RastNode}
import scala.annotation.nowarn

/** RAST-based emitter for Mermaid diagram registration modules.
  *
  * Each diagram module follows a fixed pattern:
  *   - Imports parser, db, renderer
  *   - `export const diagram: DiagramDefinition = { parser, db, renderer }`
  *
  * The Scala translation emits an object with detect/parse/render entry points.
  */
object MermaidDiagramEmitter {

  /** Emits a Scala diagram object from a RAST file.
    *
    * @param rast        the parsed RAST file
    * @param objectName  e.g. "InfoDiagram"
    * @param pkg         e.g. "info"
    * @param dbClass     e.g. "InfoDb"
    * @param parserClass e.g. "InfoParser" (None if no parser)
    * @param rendererClass e.g. "InfoRenderer" (None if no renderer)
    * @param detectorClass e.g. "InfoDetector" (None to skip detect method)
    * @return the complete Scala source
    */
  def emitDiagram(
      rast: RastFile,
      objectName: String,
      pkg: String,
      dbClass: String,
      parserClass: Option[String] = None,
      rendererClass: Option[String] = None,
      detectorClass: Option[String] = None,
  ): String = {
    val sb = new StringBuilder
    sb.append(header(rast.path, s"$objectName.scala"))
    sb.append(s"package ssg\npackage mermaid\npackage diagrams\npackage $pkg\n\n")

    // Extract what the diagram exports reference (used for potential future wiring)
    @nowarn("msg=unused") val exports = findExportedComponents(rast)

    sb.append(s"import ssg.mermaid.MermaidConfig\n\n")
    sb.append(s"/** $pkg diagram type registration and rendering entry point. */\n")
    sb.append(s"object $objectName {\n\n")

    // detect method
    detectorClass match {
      case Some(det) =>
        sb.append(s"  def detect(text: String): Boolean =\n")
        sb.append(s"    $det.detect(text)\n\n")
      case None =>
        // Try to extract from the RAST itself
        val keyword = pkg.toLowerCase
        sb.append(s"  def detect(text: String): Boolean =\n")
        sb.append(s"    text.trim.split(\"[\\\\n\\\\r]\", 2)(0).trim.toLowerCase.startsWith(\"$keyword\")\n\n")
    }

    // parse method
    parserClass match {
      case Some(parser) =>
        sb.append(s"  def parse(text: String): $dbClass = $parser.parse(text)\n\n")
      case None =>
        sb.append(s"  def parse(text: String): $dbClass = {\n")
        sb.append(s"    val db = new $dbClass\n")
        sb.append(s"    // TODO: wire parser\n")
        sb.append(s"    db\n")
        sb.append(s"  }\n\n")
    }

    // render method
    rendererClass match {
      case Some(renderer) =>
        sb.append(s"  def render(text: String, config: MermaidConfig = MermaidConfig()): String = {\n")
        sb.append(s"    val db = parse(text)\n")
        sb.append(s"    $renderer.render(db, config)\n")
        sb.append(s"  }\n")
      case None =>
        sb.append(s"  def render(text: String, config: MermaidConfig = MermaidConfig()): String = {\n")
        sb.append(s"    val db = parse(text)\n")
        sb.append(s"    // TODO: wire renderer\n")
        sb.append(s"    \"\"\n")
        sb.append(s"  }\n")
    }

    sb.append("}\n")
    sb.toString
  }

  /** Extracts the component names referenced in the export object. */
  private def findExportedComponents(rast: RastFile): Map[String, String] = {
    val result = scala.collection.mutable.Map.empty[String, String]
    for (node <- rast.nodes) {
      node.kind match {
        case "VariableStatement" if node.flags.contains("ExportKeyword") =>
          val decls = extractVarDecls(node)
          for (d <- decls) {
            val objLit = findChild(d, "ObjectLiteralExpression")
            objLit.foreach { obj =>
              for (prop <- obj.children) {
                prop.kind match {
                  case "ShorthandPropertyAssignment" =>
                    val name = nameOf(prop)
                    result(name) = name
                  case "PropertyAssignment" =>
                    val key = nameOf(prop)
                    val value = prop.children.lastOption.flatMap(_.text).getOrElse(key)
                    result(key) = value
                  case _ => ()
                }
              }
            }
          }
        case "ExportAssignment" =>
          val objLit = findChild(node, "ObjectLiteralExpression")
          objLit.foreach { obj =>
            for (prop <- obj.children) {
              prop.kind match {
                case "ShorthandPropertyAssignment" =>
                  val name = nameOf(prop)
                  result(name) = name
                case "PropertyAssignment" =>
                  val key = nameOf(prop)
                  val value = prop.children.lastOption.flatMap(_.text).getOrElse(key)
                  result(key) = value
                case _ => ()
              }
            }
          }
        case _ => ()
      }
    }
    result.toMap
  }

  private def nameOf(node: RastNode): String =
    node.children.find(_.kind == "Identifier").flatMap(_.text).getOrElse("$anon")

  private def findChild(node: RastNode, kind: String): Option[RastNode] =
    node.children.find(_.kind == kind)

  private def extractVarDecls(stmt: RastNode): List[RastNode] = {
    val declLists = stmt.children.filter(_.kind == "VariableDeclarationList")
    if (declLists.nonEmpty) declLists.flatMap(_.children.filter(_.kind == "VariableDeclaration"))
    else stmt.children.filter(_.kind == "VariableDeclaration")
  }

  @nowarn("msg=unused")
  private def header(sourcePath: String, targetFile: String): String =
    s"""/*
       | * Mermaid diagramming engine - Scala 3 port
       | *
       | * Ported from: $sourcePath
       | * Original license: MIT
       | *
       | * Auto-generated by MermaidDiagramEmitter from RAST v1
       | */
       |""".stripMargin
}
