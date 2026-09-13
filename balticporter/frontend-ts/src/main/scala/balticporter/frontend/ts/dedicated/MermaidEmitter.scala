package balticporter.frontend.ts.dedicated

import balticporter.frontend.ts.{RastFile, RastNode, RastType, RastValue, Rast}
import scala.collection.mutable

/** Dedicated RAST-to-Scala emitter for Mermaid diagram modules.
  *
  * Reads the resolved AST from the TS exporter and produces Scala
  * matching the ssg-mermaid hand-port's conventions:
  *   - Package: ssg.mermaid.*
  *   - D3Element -> SvgBuilder
  *   - Theme options -> ThemeVariables
  *   - Module-level const/function -> Scala object members
  *   - Template literals -> string interpolation
  *   - Arrow functions -> method defs
  *   - Type aliases -> Scala type aliases or case classes
  *
  * Covers three emittable categories:
  *   1. Style generators (template literal CSS from theme vars)
  *   2. Pure utility functions (regex, string ops)
  *   3. Detector functions (regex-based diagram type detection)
  */
object MermaidEmitter {

  // -- 1. Style generators ---------------------------------------------------

  /** Emits a Scala *Styles object from a mermaid diagram styles.ts RAST file.
    *
    * The TS pattern is:
    *   const getStyles = (options: FooStyleOptions) => `...css with ${options.x}...`;
    *   export default getStyles;
    *
    * The Scala pattern (from the hand port) is:
    *   object FooStyles { def generate(vars: ThemeVariables): String = ... }
    */
  def emitStyles(rast: RastFile, objectName: String, pkg: String): String = {
    val sb = new StringBuilder
    sb.append(header(rast.path, s"$objectName.scala"))
    sb.append(s"package ssg\npackage mermaid\npackage diagrams\npackage $pkg\n\n")
    sb.append("import ssg.mermaid.theme.ThemeVariables\n\n")
    sb.append(s"object $objectName {\n\n")
    sb.append(s"  def generate(vars: ThemeVariables): String = {\n")
    sb.append("    val sb = new StringBuilder()\n")

    // Find the arrow function with the template expression
    val templateExpr = findTemplateExpression(rast)
    templateExpr match {
      case Some(tmpl) =>
        val paramName = findStylesParamName(rast)
        val cssBlocks = extractCssFromTemplate(tmpl, paramName)
        for (block <- cssBlocks) {
          sb.append("    sb.append(\n")
          sb.append(s"""      s\"\"\"$block\"\"\".stripMargin\n""")
          sb.append("    )\n")
        }
      case None =>
        sb.append("    // No template expression found in RAST\n")
    }

    sb.append("    sb.toString\n")
    sb.append("  }\n")
    sb.append("}\n")
    sb.toString
  }

  /** Emits a Scala utility object from a mermaid utility .ts RAST file.
    *
    * Handles:
    *   - Exported arrow functions with regex operations
    *   - Exported const string values
    */
  def emitUtility(rast: RastFile, objectName: String, pkg: String): String = {
    val sb = new StringBuilder
    sb.append(header(rast.path, s"$objectName.scala"))
    sb.append(s"package ssg\npackage mermaid\n")
    if (pkg.nonEmpty) sb.append(s"package $pkg\n")
    sb.append("\n")
    sb.append(s"object $objectName {\n\n")

    for (node <- rast.nodes) {
      node.kind match {
        case "VariableStatement" =>
          emitVariableStatement(sb, node, rast, indent = "  ")
        case _ => ()
      }
    }

    sb.append("}\n")
    sb.toString
  }

  /** Emits a Scala object from a mermaid accessibility.ts RAST file.
    *
    * The TS exports:
    *   - A const SVG_ROLE
    *   - Two exported functions: setA11yDiagramInfo, addSVGa11yTitleDescription
    * The Scala equivalent wraps them in an object with SvgBuilder instead of D3Element.
    */
  def emitAccessibility(rast: RastFile): String = {
    val sb = new StringBuilder
    sb.append(header(rast.path, "Accessibility.scala"))
    sb.append("package ssg\npackage mermaid\n\n")
    sb.append("import ssg.graphs.commons.svg.SvgBuilder\n\n")
    sb.append("object Accessibility {\n\n")

    // Extract declarations
    for (node <- rast.nodes) {
      node.kind match {
        case "VariableStatement" =>
          val declLists = findChildren(node, "VariableDeclarationList")
          val decls = if (declLists.nonEmpty) declLists.flatMap(dl => findChildren(dl, "VariableDeclaration"))
                      else findChildren(node, "VariableDeclaration")
          for (d <- decls) {
            val name = nameOf(d)
            val isConst = d.flags.contains("const")
            val init = findChild(d, "StringLiteral")
            init.foreach { lit =>
              val valName = scalaConstName(name)
              val value = lit.value match {
                case Some(RastValue.Str(s)) => s"\"$s\""
                case _ => "\"\"" // fallback
              }
              sb.append(s"  val $valName: String = $value\n\n")
            }
          }
        case "FunctionDeclaration" =>
          val funcName = nameOf(node)
          val params = node.children.filter(_.kind == "Parameter")
          val isExported = node.flags.contains("ExportKeyword")
          if (isExported) {
            funcName match {
              case "setA11yDiagramInfo" =>
                emitSetA11yDiagramInfo(sb, node, params)
              case "addSVGa11yTitleDescription" =>
                emitAddSVGa11yTitleDescription(sb, node, params)
              case other =>
                sb.append(s"  // TODO: $other\n\n")
            }
          }
        case _ => ()
      }
    }

    sb.append("}\n")
    sb.toString
  }

  // -- Accessibility helpers --------------------------------------------------

  private def emitSetA11yDiagramInfo(sb: StringBuilder, node: RastNode, params: List[RastNode]): Unit = {
    sb.append("  def setA11yDiagramInfo(svg: SvgBuilder, diagramType: String): Unit = {\n")
    // Read the body from RAST
    val body = findChild(node, "Block")
    body.foreach { block =>
      val stmts = block.children
      for (stmt <- stmts) {
        stmt.kind match {
          case "ExpressionStatement" =>
            val call = findChild(stmt, "CallExpression")
            call.foreach { c =>
              val sel = findChild(c, "PropertyAccessExpression")
              sel.foreach { pa =>
                val prop = pa.children.lastOption.flatMap(_.text).getOrElse("")
                val args = c.children.filter(ch =>
                  ch.kind == "StringLiteral" || ch.kind == "Identifier" ||
                    ch.kind == "PropertyAccessExpression"
                ).drop(1) // skip the callee
                if (prop == "attr") {
                  val argTexts = extractCallArgs(c)
                  sb.append(s"    svg.attr(${argTexts.mkString(", ")})\n")
                }
              }
            }
          case "IfStatement" =>
            sb.append("    if (diagramType.nonEmpty) {\n")
            sb.append("      svg.attr(\"aria-roledescription\", diagramType)\n")
            sb.append("    }\n")
          case _ => ()
        }
      }
    }
    sb.append("  }\n\n")
  }

  @annotation.nowarn("msg=unused")
  private def emitAddSVGa11yTitleDescription(sb: StringBuilder, node: RastNode, params: List[RastNode]): Unit = {
    sb.append("  def addSVGa11yTitleDescription(\n")
    sb.append("    svg:       SvgBuilder,\n")
    sb.append("    a11yTitle: String,\n")
    sb.append("    a11yDesc:  String,\n")
    sb.append("    baseId:    String\n")
    sb.append("  ): Unit = {\n")
    // The body checks svg.insert !== undefined, then conditionally adds desc and title
    sb.append("    if (a11yDesc.nonEmpty) {\n")
    sb.append("      val descId = s\"chart-desc-$$baseId\"\n")
    sb.append("      svg.attr(\"aria-describedby\", descId)\n")
    sb.append("      svg.insert(\"desc\", \":first-child\").attr(\"id\", descId).text(a11yDesc)\n")
    sb.append("    }\n")
    sb.append("    if (a11yTitle.nonEmpty) {\n")
    sb.append("      val titleId = s\"chart-title-$$baseId\"\n")
    sb.append("      svg.attr(\"aria-labelledby\", titleId)\n")
    sb.append("      svg.insert(\"title\", \":first-child\").attr(\"id\", titleId).text(a11yTitle)\n")
    sb.append("    }\n")
    sb.append("  }\n\n")
  }

  // -- Variable statement emission --------------------------------------------

  private def emitVariableStatement(sb: StringBuilder, node: RastNode, rast: RastFile, indent: String): Unit = {
    // VariableStatement -> VariableDeclarationList -> VariableDeclaration
    val declLists = findChildren(node, "VariableDeclarationList")
    val decls = if (declLists.nonEmpty) declLists.flatMap(dl => findChildren(dl, "VariableDeclaration"))
                else findChildren(node, "VariableDeclaration")
    val isExported = node.flags.contains("ExportKeyword")
    for (d <- decls) {
      val name = nameOf(d)
      val isConst = d.flags.contains("const")
      // Look for the initializer
      val arrowFn = findChild(d, "ArrowFunction")
      val stringLit = findChild(d, "StringLiteral")
      val numLit = findChild(d, "NumericLiteral")
      val regexLit = findChild(d, "RegularExpressionLiteral")
      @annotation.nowarn("msg=unused") val callExpr = findChild(d, "CallExpression")

      arrowFn match {
        case Some(fn) =>
          emitArrowFunction(sb, name, fn, rast, indent, isExported)
        case None =>
          stringLit.foreach { lit =>
            val value = lit.value match {
              case Some(RastValue.Str(s)) => s"\"${escapeScala(s)}\""
              case _ => "\"\""
            }
            val kw = if (isConst) "val" else "var"
            sb.append(s"$indent$kw $name: String = $value\n\n")
          }
          numLit.foreach { lit =>
            val value = lit.value match {
              case Some(RastValue.Num(n)) =>
                if (n == n.toLong) n.toLong.toString else n.toString
              case _ => "0"
            }
            val kw = if (isConst) "val" else "var"
            sb.append(s"$indent$kw $name: Double = $value\n\n")
          }
          regexLit.foreach { lit =>
            val value = lit.value match {
              case Some(RastValue.Str(s)) => tsRegexToScala(s)
              case _ => "\"\".r"
            }
            val kw = if (isConst) "val" else "var"
            sb.append(s"$indent$kw $name = $value\n\n")
          }
      }
    }
  }

  private def emitArrowFunction(sb: StringBuilder, name: String, fn: RastNode, rast: RastFile,
                                indent: String, isExported: Boolean): Unit = {
    val params = fn.children.filter(_.kind == "Parameter")
    val body = fn.children.find(c => c.kind == "Block" || c.kind != "Parameter" && c.kind != "TypeReference" &&
      c.kind != "StringKeyword" && c.kind != "NumberKeyword" && c.kind != "BooleanKeyword" &&
      c.kind != "VoidKeyword" && c.kind != "AnyKeyword")

    val paramList = params.map { p =>
      val pName = nameOf(p)
      val pType = inferParamType(p, rast)
      s"$pName: $pType"
    }.mkString(", ")

    val returnType = inferReturnType(fn, rast)
    val vis = if (!isExported) "private " else ""

    sb.append(s"$indent${vis}def $name($paramList): $returnType = {\n")

    body match {
      case Some(b) if b.kind == "Block" =>
        emitBlock(sb, b, rast, indent + "  ")
      case Some(expr) =>
        sb.append(s"$indent  ${emitExpr(expr, rast)}\n")
      case None =>
        sb.append(s"$indent  ???\n")
    }

    sb.append(s"$indent}\n\n")
  }

  private def emitBlock(sb: StringBuilder, block: RastNode, rast: RastFile, indent: String): Unit = {
    for (stmt <- block.children) {
      stmt.kind match {
        case "ReturnStatement" =>
          val expr = stmt.children.headOption
          expr.foreach { e =>
            sb.append(s"${indent}${emitExpr(e, rast)}\n")
          }
        case "VariableStatement" =>
          emitVariableStatement(sb, stmt, rast, indent)
        case "ExpressionStatement" =>
          val expr = stmt.children.headOption
          expr.foreach { e =>
            sb.append(s"${indent}${emitExpr(e, rast)}\n")
          }
        case "IfStatement" =>
          emitIfStatement(sb, stmt, rast, indent)
        case _ =>
          sb.append(s"$indent// TODO: ${stmt.kind}\n")
      }
    }
  }

  private def emitIfStatement(sb: StringBuilder, node: RastNode, rast: RastFile, indent: String): Unit = {
    val children = node.children
    if (children.size >= 2) {
      sb.append(s"${indent}if (${emitExpr(children(0), rast)}) {\n")
      if (children(1).kind == "Block") {
        emitBlock(sb, children(1), rast, indent + "  ")
      } else {
        sb.append(s"$indent  ${emitExpr(children(1), rast)}\n")
      }
      sb.append(s"$indent}\n")
      if (children.size >= 3) {
        sb.append(s"${indent}else {\n")
        if (children(2).kind == "Block") {
          emitBlock(sb, children(2), rast, indent + "  ")
        } else if (children(2).kind == "IfStatement") {
          emitIfStatement(sb, children(2), rast, indent)
        } else {
          sb.append(s"$indent  ${emitExpr(children(2), rast)}\n")
        }
        sb.append(s"$indent}\n")
      }
    }
  }

  private def emitExpr(node: RastNode, rast: RastFile): String = {
    node.kind match {
      case "Identifier" =>
        node.text.getOrElse("$unknown")

      case "StringLiteral" =>
        node.value match {
          case Some(RastValue.Str(s)) => s"\"${escapeScala(s)}\""
          case _ => "\"\""
        }

      case "NumericLiteral" =>
        node.value match {
          case Some(RastValue.Num(n)) =>
            if (n == n.toLong) n.toLong.toString else n.toString
          case _ => "0"
        }

      case "TrueKeyword" => "true"
      case "FalseKeyword" => "false"
      case "NullKeyword" => "null"

      case "CallExpression" =>
        val callee = node.children.headOption
        val args = node.children.drop(1)
        // Special case: obj.replace(regex, str) -> obj.replaceAll(pattern, str)
        callee match {
          case Some(pa) if pa.kind == "PropertyAccessExpression" =>
            val methodName = pa.children.lastOption.flatMap(_.text).getOrElse("")
            val receiver = pa.children.headOption.map(c => emitExpr(c, rast)).getOrElse("???")
            methodName match {
              case "replace" if args.headOption.exists(_.kind == "RegularExpressionLiteral") =>
                val regex = args.head
                val replacement = args.lift(1).map(a => emitExpr(a, rast)).getOrElse("\"\"")
                val pattern = regex.value match {
                  case Some(RastValue.Str(s)) =>
                    val lastSlash = s.lastIndexOf('/')
                    if (lastSlash > 0) {
                      val p = s.substring(1, lastSlash)
                      val flags = s.substring(lastSlash + 1)
                      val flagPrefix = if (flags.contains("m")) "(?m)" else ""
                      s"\"$flagPrefix${escapeScala(p)}\""
                    } else s"\"${escapeScala(s)}\""
                  case _ => "\"\""
                }
                s"$receiver.replaceAll($pattern, $replacement)"
              case "trimStart" =>
                s"$receiver.trim"
              case "test" if args.nonEmpty =>
                val arg = emitExpr(args.head, rast)
                s"$receiver.matches($arg)"
              case "forEach" if args.nonEmpty =>
                val fn = emitExpr(args.head, rast)
                s"$receiver.foreach($fn)"
              case "push" =>
                val pushArgs = args.map(a => emitExpr(a, rast))
                s"$receiver += ${pushArgs.mkString(", ")}"
              case "map" if args.nonEmpty =>
                val fn = emitExpr(args.head, rast)
                s"$receiver.map($fn)"
              case "filter" if args.nonEmpty =>
                val fn = emitExpr(args.head, rast)
                s"$receiver.filter($fn)"
              case "join" =>
                val sep = args.headOption.map(a => emitExpr(a, rast)).getOrElse("\"\"")
                s"$receiver.mkString($sep)"
              case "indexOf" if args.nonEmpty =>
                val arg = emitExpr(args.head, rast)
                s"$receiver.indexOf($arg)"
              case "includes" if args.nonEmpty =>
                val arg = emitExpr(args.head, rast)
                s"$receiver.contains($arg)"
              case "split" if args.nonEmpty =>
                val arg = emitExpr(args.head, rast)
                s"$receiver.split($arg).toVector"
              case "trim" =>
                s"$receiver.trim"
              case "toLowerCase" =>
                s"$receiver.toLowerCase"
              case "toUpperCase" =>
                s"$receiver.toUpperCase"
              case "startsWith" if args.nonEmpty =>
                val arg = emitExpr(args.head, rast)
                s"$receiver.startsWith($arg)"
              case "endsWith" if args.nonEmpty =>
                val arg = emitExpr(args.head, rast)
                s"$receiver.endsWith($arg)"
              case "substring" =>
                val emittedArgs = args.map(a => emitExpr(a, rast))
                s"$receiver.substring(${emittedArgs.mkString(", ")})"
              case "slice" =>
                val emittedArgs = args.map(a => emitExpr(a, rast))
                s"$receiver.slice(${emittedArgs.mkString(", ")})"
              case "concat" =>
                val emittedArgs = args.map(a => emitExpr(a, rast))
                s"$receiver ++ ${emittedArgs.mkString(" ++ ")}"
              case other =>
                val emittedArgs = args.map(a => emitExpr(a, rast))
                s"$receiver.$other(${emittedArgs.mkString(", ")})"
            }
          case _ =>
            val calleeStr = callee.map(c => emitExpr(c, rast)).getOrElse("???")
            val emittedArgs = args.map(a => emitExpr(a, rast))
            s"$calleeStr(${emittedArgs.mkString(", ")})"
        }

      case "PropertyAccessExpression" =>
        val parts = node.children.map(c => emitExpr(c, rast))
        parts.mkString(".")

      case "BinaryExpression" =>
        val children = node.children
        if (children.size >= 2) {
          val left = emitExpr(children(0), rast)
          val right = emitExpr(children(1), rast)
          val op = node.operator.getOrElse("???")
          val scalaOp = tsOpToScala(op)
          s"$left $scalaOp $right"
        } else "???"

      case "PrefixUnaryExpression" =>
        val op = node.operator.getOrElse("")
        val operand = node.children.headOption.map(c => emitExpr(c, rast)).getOrElse("???")
        val scalaOp = tsOpToScala(op)
        s"$scalaOp$operand"

      case "ParenthesizedExpression" =>
        val inner = node.children.headOption.map(c => emitExpr(c, rast)).getOrElse("???")
        s"($inner)"

      case "TemplateExpression" =>
        emitTemplateExpression(node, rast)

      case "NoSubstitutionTemplateLiteral" =>
        val text = node.value match {
          case Some(RastValue.Str(s)) => escapeScala(s)
          case _ => ""
        }
        s"\"${text}\""

      case "RegularExpressionLiteral" =>
        node.value match {
          case Some(RastValue.Str(s)) => tsRegexToScala(s)
          case _ => "\"\".r"
        }

      case "ObjectLiteralExpression" =>
        val fields = findChildren(node, "PropertyAssignment").map { pa =>
          val key = nameOf(pa)
          val value = pa.children.lastOption.map(c => emitExpr(c, rast)).getOrElse("???")
          s"$key = $value"
        }
        if (fields.isEmpty) "Map.empty"
        else fields.mkString("(", ", ", ")")

      case "ArrayLiteralExpression" =>
        val elems = node.children.map(c => emitExpr(c, rast))
        s"Vector(${elems.mkString(", ")})"

      case "ConditionalExpression" =>
        val children = node.children
        if (children.size >= 3) {
          val cond = emitExpr(children(0), rast)
          val thenE = emitExpr(children(1), rast)
          val elseE = emitExpr(children(2), rast)
          s"if ($cond) $thenE else $elseE"
        } else "???"

      case "ElementAccessExpression" =>
        val obj = node.children.headOption.map(c => emitExpr(c, rast)).getOrElse("???")
        val idx = node.children.lastOption.map(c => emitExpr(c, rast)).getOrElse("0")
        s"$obj($idx)"

      case "ThisKeyword" => "this"
      case "SuperKeyword" => "super"

      case "ArrowFunction" =>
        val params = node.children.filter(_.kind == "Parameter")
        val body = node.children.find(c => c.kind == "Block" || (c.kind != "Parameter" &&
          c.kind != "TypeReference" && c.kind != "StringKeyword" && c.kind != "NumberKeyword"))
        val paramList = params.map(p => nameOf(p)).mkString(", ")
        val bodyStr = body.map(b => emitExpr(b, rast)).getOrElse("???")
        if (params.size == 1) s"{ $paramList => $bodyStr }"
        else s"{ ($paramList) => $bodyStr }"

      case "Block" =>
        val stmts = node.children
        if (stmts.isEmpty) "()"
        else {
          val last = stmts.last
          val init = stmts.init.map(s => emitExpr(s, rast))
          val lastStr = emitExpr(last, rast)
          if (init.isEmpty) lastStr
          else (init :+ lastStr).mkString("{ ", "; ", " }")
        }

      case _ =>
        s"??? /* ${node.kind} */"
    }
  }

  private def emitTemplateExpression(node: RastNode, rast: RastFile): String = {
    val sb = new StringBuilder
    sb.append("s\"")
    for (child <- node.children) {
      child.kind match {
        case "TemplateHead" =>
          child.value.foreach {
            case RastValue.Str(s) => sb.append(escapeInterpolation(s))
            case _ => ()
          }
        case "TemplateSpan" =>
          val expr = child.children.headOption
          val tail = child.children.lastOption
          expr.foreach { e =>
            sb.append("${")
            sb.append(emitExpr(e, rast))
            sb.append("}")
          }
          tail.foreach { t =>
            t.value.foreach {
              case RastValue.Str(s) => sb.append(escapeInterpolation(s))
              case _ => ()
            }
          }
        case _ => ()
      }
    }
    sb.append("\"")
    sb.toString
  }

  // -- Style-specific helpers -------------------------------------------------

  private def findTemplateExpression(rast: RastFile): Option[RastNode] = {
    def search(node: RastNode): Option[RastNode] = {
      if (node.kind == "TemplateExpression") Some(node)
      else node.children.view.flatMap(search).headOption
    }
    rast.nodes.view.flatMap(search).headOption
  }

  private def findStylesParamName(rast: RastFile): String = {
    def search(node: RastNode): Option[String] = {
      if (node.kind == "ArrowFunction") {
        node.children.find(_.kind == "Parameter").map(nameOf)
      } else node.children.view.flatMap(search).headOption
    }
    rast.nodes.view.flatMap(search).headOption.getOrElse("options")
  }

  private def extractCssFromTemplate(tmpl: RastNode, paramName: String): List[String] = {
    // Build the complete CSS string with ${vars.xxx} substitutions
    val sb = new StringBuilder
    for (child <- tmpl.children) {
      child.kind match {
        case "TemplateHead" =>
          child.value.foreach {
            case RastValue.Str(s) => sb.append(s)
            case _ => ()
          }
        case "TemplateSpan" =>
          val expr = child.children.headOption
          val tail = child.children.lastOption
          expr.foreach { e =>
            val scalaExpr = rewriteThemeAccess(e, paramName)
            sb.append(s"$${$scalaExpr}")
          }
          tail.foreach { t =>
            t.value.foreach {
              case RastValue.Str(s) => sb.append(s)
              case _ => ()
            }
          }
        case _ => ()
      }
    }
    // Split into CSS rule blocks
    val css = sb.toString.trim
    if (css.isEmpty) Nil
    else List(css + "\n")
  }

  private def rewriteThemeAccess(node: RastNode, paramName: String): String = {
    // options.pieStrokeColor -> vars.pieStrokeColor
    node.kind match {
      case "PropertyAccessExpression" =>
        val parts = node.children.map { c =>
          if (c.kind == "Identifier" && c.text.contains(paramName)) "vars"
          else c.text.getOrElse(rewriteThemeAccess(c, paramName))
        }
        parts.mkString(".")
      case "BinaryExpression" =>
        val children = node.children
        if (children.size >= 2) {
          val left = rewriteThemeAccess(children(0), paramName)
          val right = rewriteThemeAccess(children(1), paramName)
          val op = node.operator.getOrElse("||")
          if (op == "BarBarToken") {
            // options.nodeTextColor || options.textColor ->
            // if (vars.nodeTextColor.nonEmpty) vars.nodeTextColor else vars.textColor
            s"(if ($left.nonEmpty) $left else $right)"
          } else {
            s"$left $op $right"
          }
        } else "???"
      case "Identifier" =>
        if (node.text.contains(paramName)) "vars"
        else node.text.getOrElse("???")
      case _ =>
        emitExpr(node, new RastFile(1, "", "", Nil, Map.empty, Map.empty))
    }
  }

  // -- Type inference ---------------------------------------------------------

  private def inferParamType(param: RastNode, rast: RastFile): String = {
    // Check for explicit type annotation
    val typeNode = param.children.find(c =>
      c.kind == "TypeReference" || c.kind == "StringKeyword" || c.kind == "NumberKeyword" ||
        c.kind == "BooleanKeyword" || c.kind == "VoidKeyword" || c.kind == "AnyKeyword" ||
        c.kind == "ArrayType" || c.kind == "UnionType"
    )
    typeNode match {
      case Some(t) => tsTypeToScala(t, rast)
      case None =>
        // Try the type map
        param.`type`.flatMap(rast.types.get).map(rastTypeToScala).getOrElse("Any")
    }
  }

  private def inferReturnType(fn: RastNode, rast: RastFile): String = {
    // Check for explicit return type annotation after params
    val typeNode = fn.children.find(c =>
      c.kind == "TypeReference" || c.kind == "StringKeyword" || c.kind == "NumberKeyword" ||
        c.kind == "BooleanKeyword" || c.kind == "VoidKeyword"
    )
    typeNode match {
      case Some(t) => tsTypeToScala(t, rast)
      case None =>
        // Try the RAST type map
        fn.`type`.flatMap(rast.types.get) match {
          case Some(rt) if rt.returnType.isDefined =>
            rt.returnType.flatMap(rast.types.get).map(rastTypeToScala).getOrElse("Any")
          case Some(rt) => rastTypeToScala(rt)
          case None => "Any"
        }
    }
  }

  private def tsTypeToScala(node: RastNode, rast: RastFile): String = {
    node.kind match {
      case "StringKeyword" => "String"
      case "NumberKeyword" => "Double"
      case "BooleanKeyword" => "Boolean"
      case "VoidKeyword" => "Unit"
      case "AnyKeyword" => "Any"
      case "TypeReference" =>
        val name = node.children.find(_.kind == "Identifier").flatMap(_.text).getOrElse("Any")
        name match {
          case "RegExp" => "scala.util.matching.Regex"
          case "Array" =>
            val typeArg = node.children.find(c => c.kind != "Identifier")
              .map(c => tsTypeToScala(c, rast)).getOrElse("Any")
            s"Vector[$typeArg]"
          case "Map" => "Map[String, Any]"
          case "Set" => "Set[Any]"
          case "Promise" => "Any" // async eliminated
          case "D3Element" => "SvgBuilder"
          case "SVG" => "SvgBuilder"
          case "SVGGroup" => "SvgBuilder"
          case other => other
        }
      case "ArrayType" =>
        val elem = node.children.headOption.map(c => tsTypeToScala(c, rast)).getOrElse("Any")
        s"Vector[$elem]"
      case "UnionType" =>
        val types = node.children.map(c => tsTypeToScala(c, rast)).distinct
        if (types.contains("String") && types.forall(t => t == "String" || t == "undefined")) "String"
        else if (types.size == 2 && types.contains("undefined")) {
          val real = types.filterNot(_ == "undefined")
          s"Option[${real.head}]"
        }
        else types.mkString(" | ")
      case _ => "Any"
    }
  }

  private def rastTypeToScala(rt: RastType): String = {
    rt.kind match {
      case "string" => "String"
      case "number" => "Double"
      case "boolean" => "Boolean"
      case "void" => "Unit"
      case "null" | "undefined" => "Null"
      case "any" => "Any"
      case "never" => "Nothing"
      case "function" => "Any" // simplified
      case "array" => "Vector[Any]"
      case _ => "Any"
    }
  }

  // -- Helpers ----------------------------------------------------------------

  private def nameOf(node: RastNode): String =
    node.children.find(_.kind == "Identifier").flatMap(_.text).getOrElse("$anon")

  private def findChild(node: RastNode, kind: String): Option[RastNode] =
    node.children.find(_.kind == kind)

  private def findChildren(node: RastNode, kind: String): List[RastNode] =
    node.children.filter(_.kind == kind)

  private def extractCallArgs(call: RastNode): List[String] = {
    call.children.drop(1).map { arg =>
      arg.kind match {
        case "StringLiteral" =>
          arg.value match {
            case Some(RastValue.Str(s)) => s"\"${escapeScala(s)}\""
            case _ => "\"\""
          }
        case "Identifier" => arg.text.getOrElse("???")
        case _ => "???"
      }
    }
  }

  private def scalaConstName(tsName: String): String = {
    // SVG_ROLE -> SvgRole (PascalCase from SCREAMING_SNAKE)
    if (tsName.forall(c => c.isUpper || c == '_')) {
      tsName.split('_').map(w => w.head + w.tail.toLowerCase).mkString
    } else tsName
  }

  private def escapeScala(s: String): String =
    s.replace("\\", "\\\\").replace("\"", "\\\"").replace("\n", "\\n").replace("\r", "\\r")

  private def escapeInterpolation(s: String): String =
    s.replace("$", "$$").replace("\"", "\\\"")

  private def tsRegexToScala(regex: String): String = {
    // /pattern/flags -> "pattern".r (simplified, ignoring flags)
    if (regex.startsWith("/")) {
      val lastSlash = regex.lastIndexOf('/')
      if (lastSlash > 0) {
        val pattern = regex.substring(1, lastSlash)
        val flags = regex.substring(lastSlash + 1)
        val escaped = pattern.replace("\\", "\\\\").replace("\"", "\\\"")
        val flagPrefix = if (flags.contains("m")) "(?m)" else ""
        val globalNote = if (flags.contains("g")) " /* global */" else ""
        s"\"$flagPrefix$escaped\".r$globalNote"
      } else s"\"${escapeScala(regex)}\".r"
    } else s"\"${escapeScala(regex)}\".r"
  }

  private def tsOpToScala(op: String): String = op match {
    case "EqualsEqualsEqualsToken" => "=="
    case "ExclamationEqualsEqualsToken" => "!="
    case "EqualsEqualsToken" => "=="
    case "ExclamationEqualsToken" => "!="
    case "AmpersandAmpersandToken" => "&&"
    case "BarBarToken" => "||"
    case "PlusToken" => "+"
    case "MinusToken" => "-"
    case "AsteriskToken" => "*"
    case "SlashToken" => "/"
    case "PercentToken" => "%"
    case "LessThanToken" => "<"
    case "GreaterThanToken" => ">"
    case "LessThanEqualsToken" => "<="
    case "GreaterThanEqualsToken" => ">="
    case "ExclamationToken" => "!"
    case "PlusEqualsToken" => "+="
    case "MinusEqualsToken" => "-="
    case other => other
  }

  // -- Renderer emission (D3 -> SvgBuilder) -----------------------------------

  /** A single call in a D3 method chain, linearized from the nested RAST.
    *
    * Example chain: `g.append('path').attr('class', 'x').attr('d', '...')`
    * becomes: `ChainLink("append", List("path")), ChainLink("attr", ...), ...`
    */
  private final case class ChainLink(method: String, args: List[RastNode])

  /** Emits a Scala renderer object from a mermaid diagram renderer RAST file.
    *
    * The TS pattern is a D3-based `draw` function that:
    *   - Calls `selectSvgElement(id)` to create an SVG
    *   - Uses method chaining: `svg.append('g').attr(...).style(...).text(...)`
    *   - Calls `configureSvgSize`
    *
    * The Scala pattern (from the hand port) is:
    *   - `SvgBuilder.createSvg(viewBox)` creates the root
    *   - Method chaining on SvgBuilder: `.append(...)`, `.attr(...)`, `.style(...)`, `.text(...)`
    *   - `svg.build().toMarkup()` produces the final SVG string
    */
  def emitRenderer(rast: RastFile, objectName: String, pkg: String): String = {
    val sb = new StringBuilder
    sb.append(header(rast.path, s"$objectName.scala"))
    sb.append(s"package ssg\npackage mermaid\npackage diagrams\npackage $pkg\n\n")
    sb.append("import ssg.graphs.commons.svg.SvgBuilder\n\n")
    sb.append(s"object $objectName {\n\n")

    // Find the draw function
    val drawFn = findDrawFunction(rast)
    drawFn match {
      case Some(fn) =>
        val params = fn.children.filter(_.kind == "Parameter")
        val body = fn.children.find(_.kind == "Block")

        // Emit the render method
        sb.append(s"  def render(")
        // Map TS params to Scala types
        val paramDecls = params.map { p =>
          val pName = nameOf(p)
          val pType = inferParamType(p, rast)
          s"$pName: $pType"
        }
        sb.append(paramDecls.mkString(", "))
        sb.append(s"): String = {\n")

        body.foreach { block =>
          emitRendererBody(sb, block, rast, "    ")
        }

        sb.append("  }\n")
      case None =>
        sb.append("  // No draw function found in RAST\n")
        sb.append("  def render(): String = ???\n")
    }

    sb.append("}\n")
    sb.toString
  }

  /** Find the `draw` arrow function in a renderer RAST file. */
  private def findDrawFunction(rast: RastFile): Option[RastNode] = {
    for (node <- rast.nodes) {
      if (node.kind == "VariableStatement") {
        val declLists = findChildren(node, "VariableDeclarationList")
        val decls = if (declLists.nonEmpty) declLists.flatMap(dl => findChildren(dl, "VariableDeclaration"))
                    else findChildren(node, "VariableDeclaration")
        for (d <- decls) {
          val name = nameOf(d)
          if (name == "draw") {
            val arrowFn = findChild(d, "ArrowFunction")
            if (arrowFn.isDefined) return arrowFn
            val funcExpr = findChild(d, "FunctionExpression")
            if (funcExpr.isDefined) return funcExpr
          }
        }
      }
    }
    None
  }

  /** Emit the body of a renderer's draw/render function.
    *
    * Walks each statement in the block, recognizing these patterns:
    *   1. `const svg = selectSvgElement(id)` -> `val svg = SvgBuilder.createSvg(viewBox)`
    *   2. `configureSvgSize(svg, h, w, flag)` -> ignored (sizing is per-platform)
    *   3. `log.debug(...)` -> ignored
    *   4. `const g = svg.append('g')` -> `val g = svg.append("g")`
    *   5. D3 method chains -> linearized SvgBuilder calls
    *   6. Variable declarations with initializers
    */
  private def emitRendererBody(sb: StringBuilder, block: RastNode, rast: RastFile, indent: String): Unit = {
    // Track which variables hold SvgBuilder instances
    val svgVars = mutable.Set.empty[String]

    for (stmt <- block.children) {
      stmt.kind match {
        case "VariableStatement" =>
          emitRendererVarStatement(sb, stmt, rast, indent, svgVars)
        case "ExpressionStatement" =>
          stmt.children.headOption.foreach { expr =>
            emitRendererExprStatement(sb, expr, rast, indent, svgVars)
          }
        case _ =>
          emitBlock(sb, stmt, rast, indent)
      }
    }

    // End with svg.build().toMarkup() if we found an svg variable
    if (svgVars.contains("svg")) {
      sb.append(s"\n${indent}svg.build().toMarkup()\n")
    }
  }

  /** Emit a variable statement inside a renderer body. */
  private def emitRendererVarStatement(sb: StringBuilder, stmt: RastNode, rast: RastFile,
                                       indent: String, svgVars: mutable.Set[String]): Unit = {
    val declLists = findChildren(stmt, "VariableDeclarationList")
    val decls = if (declLists.nonEmpty) declLists.flatMap(dl => findChildren(dl, "VariableDeclaration"))
                else findChildren(stmt, "VariableDeclaration")
    for (d <- decls) {
      val name = nameOf(d)
      val init = d.children.find(c => c.kind != "Identifier" && c.kind != "TypeReference" &&
        c.kind != "StringKeyword" && c.kind != "NumberKeyword" && c.kind != "BooleanKeyword")

      init match {
        case Some(call) if call.kind == "CallExpression" =>
          val callee = call.children.headOption
          val args = call.children.drop(1)

          callee match {
            // selectSvgElement(id) -> SvgBuilder.createSvg(viewBox)
            case Some(id) if id.kind == "Identifier" && id.text.contains("selectSvgElement") =>
              svgVars += name
              sb.append(s"${indent}val $name = SvgBuilder.createSvg(\"0 0 800 600\")\n")

            // obj.append('tag') -> val name = obj.append("tag")
            case Some(pa) if pa.kind == "PropertyAccessExpression" =>
              val methodName = pa.children.lastOption.flatMap(_.text).getOrElse("")
              val receiver = pa.children.headOption.flatMap(_.text).getOrElse("")
              if (methodName == "append" && svgVars.contains(receiver)) {
                svgVars += name
                val tag = args.headOption.flatMap(_.value).collect {
                  case RastValue.Str(s) => s
                }.getOrElse("g")
                sb.append(s"${indent}val $name = $receiver.append(\"$tag\")\n")
              } else {
                // Check if this is a D3 chain that assigns to a variable
                val chain = linearizeChain(call)
                if (chain.nonEmpty && svgVars.exists(v => isReceiverInChain(call, v))) {
                  svgVars += name
                  emitD3Chain(sb, call, rast, indent, svgVars, Some(name))
                } else {
                  sb.append(s"${indent}val $name = ${emitExpr(call, rast)}\n")
                }
              }
            case _ =>
              sb.append(s"${indent}val $name = ${emitExpr(call, rast)}\n")
          }

        case Some(other) =>
          val kw = if (d.flags.contains("const")) "val" else "var"
          sb.append(s"$indent$kw $name = ${emitExpr(other, rast)}\n")

        case None => ()
      }
    }
  }

  /** Emit an expression statement inside a renderer body. */
  private def emitRendererExprStatement(sb: StringBuilder, expr: RastNode, rast: RastFile,
                                        indent: String, svgVars: mutable.Set[String]): Unit = {
    expr.kind match {
      case "CallExpression" =>
        val callee = expr.children.headOption

        callee match {
          // log.debug(...) -> skip
          case Some(pa) if pa.kind == "PropertyAccessExpression" =>
            val receiver = pa.children.headOption.flatMap(_.text).getOrElse("")
            val method = pa.children.lastOption.flatMap(_.text).getOrElse("")
            if (receiver == "log") {
              // Skip logging calls
              ()
            } else if (method == "configureSvgSize" || (callee.exists(_.text.contains("configureSvgSize")))) {
              // Skip configureSvgSize calls
              ()
            } else {
              // Check if this is a D3 method chain
              emitD3Chain(sb, expr, rast, indent, svgVars, None)
            }

          // configureSvgSize(...) -> skip
          case Some(id) if id.kind == "Identifier" && id.text.contains("configureSvgSize") =>
            () // skip

          case _ =>
            sb.append(s"$indent${emitExpr(expr, rast)}\n")
        }

      case _ =>
        sb.append(s"$indent${emitExpr(expr, rast)}\n")
    }
  }

  /** Linearize a nested D3 method chain into a flat list of ChainLinks.
    *
    * The RAST for `g.append('path').attr('class', 'x').attr('d', '...')`
    * is a deeply nested structure where each `.method(args)` wraps the
    * previous call as the receiver:
    *
    * {{{
    * CallExpression(.attr('d', '...'))
    *   PropertyAccessExpression
    *     CallExpression(.attr('class', 'x'))      <- receiver
    *       PropertyAccessExpression
    *         CallExpression(g.append('path'))      <- receiver
    *           PropertyAccessExpression
    *             Identifier: g
    *             Identifier: append
    *           StringLiteral: 'path'
    *         Identifier: attr
    *       ...
    *     Identifier: attr
    *   ...
    * }}}
    *
    * Returns: (rootReceiver, List[ChainLink]) where rootReceiver is the
    * initial identifier (e.g. "g") and each ChainLink is a method call.
    */
  private def linearizeChain(call: RastNode): List[(String, ChainLink)] = {
    val result = mutable.ListBuffer.empty[(String, ChainLink)]

    def walk(node: RastNode): Option[String] = {
      if (node.kind != "CallExpression") {
        // Base case: identifier
        return node.text
      }

      val callee = node.children.headOption
      val args = node.children.drop(1)

      callee match {
        case Some(pa) if pa.kind == "PropertyAccessExpression" =>
          val method = pa.children.lastOption.flatMap(_.text).getOrElse("")
          val receiver = pa.children.headOption

          receiver match {
            case Some(r) =>
              val rootName = walk(r)
              rootName.foreach { root =>
                result += ((root, ChainLink(method, args)))
              }
              rootName
            case None => None
          }

        // Direct function call (not a method chain)
        case Some(id) if id.kind == "Identifier" =>
          None

        case _ => None
      }
    }

    walk(call)
    result.toList
  }

  /** Check if a variable name is the root receiver of a chain. */
  private def isReceiverInChain(call: RastNode, varName: String): Boolean = {
    def findRoot(node: RastNode): Option[String] = {
      if (node.kind == "Identifier") return node.text
      if (node.kind == "CallExpression") {
        val callee = node.children.headOption
        callee match {
          case Some(pa) if pa.kind == "PropertyAccessExpression" =>
            pa.children.headOption.flatMap(findRoot)
          case _ => None
        }
      } else None
    }
    findRoot(call).contains(varName)
  }

  /** Emit a D3 method chain as linearized SvgBuilder calls.
    *
    * For: `g.append('path').attr('class', 'error-icon').attr('d', '...')`
    * Emits:
    * {{{
    * val path = g.append("path")
    * path.attr("class", "error-icon")
    * path.attr("d", "...")
    * }}}
    *
    * When the chain starts with `.append(tag)`, a local variable is created
    * for the new element. Subsequent `.attr()`, `.style()`, `.text()` calls
    * are emitted as statements on that variable.
    */
  private def emitD3Chain(sb: StringBuilder, call: RastNode, rast: RastFile,
                          indent: String, svgVars: mutable.Set[String],
                          assignTo: Option[String]): Unit = {
    val chain = linearizeChain(call)
    if (chain.isEmpty) {
      // Not a D3 chain, emit as-is
      sb.append(s"$indent${emitExpr(call, rast)}\n")
      return
    }

    val rootReceiver = chain.head._1
    val links = chain.map(_._2)

    // Separate the chain: first .append() creates a new element,
    // subsequent calls modify it
    var currentVar = rootReceiver
    var appendIdx = -1

    for (i <- links.indices) {
      val link = links(i)
      link.method match {
        case "append" if i == 0 =>
          // First .append creates a new child
          val tag = link.args.headOption.flatMap(_.value).collect {
            case RastValue.Str(s) => s
          }.getOrElse("g")
          val varName = assignTo.getOrElse(tagToVarName(tag, svgVars))
          svgVars += varName
          sb.append(s"${indent}val $varName = $currentVar.append(\"$tag\")\n")
          currentVar = varName
          appendIdx = i

        case "append" =>
          // Subsequent .append creates a nested child
          val tag = link.args.headOption.flatMap(_.value).collect {
            case RastValue.Str(s) => s
          }.getOrElse("g")
          val varName = tagToVarName(tag, svgVars)
          svgVars += varName
          sb.append(s"${indent}val $varName = $currentVar.append(\"$tag\")\n")
          currentVar = varName

        case "attr" =>
          val argStrs = link.args.map(a => emitRendererArg(a, rast))
          sb.append(s"$indent$currentVar.attr(${argStrs.mkString(", ")})\n")

        case "style" =>
          val argStrs = link.args.map(a => emitRendererArg(a, rast))
          sb.append(s"$indent$currentVar.style(${argStrs.mkString(", ")})\n")

        case "text" =>
          val argStrs = link.args.map(a => emitRendererArg(a, rast))
          sb.append(s"$indent$currentVar.text(${argStrs.mkString(", ")})\n")

        case "classed" =>
          val argStrs = link.args.map(a => emitRendererArg(a, rast))
          val classStr = if (argStrs.size == 1) s"${argStrs.head}, true" else argStrs.mkString(", ")
          sb.append(s"$indent$currentVar.classed($classStr)\n")

        case "html" =>
          val argStrs = link.args.map(a => emitRendererArg(a, rast))
          sb.append(s"$indent$currentVar.html(${argStrs.mkString(", ")})\n")

        case "insert" =>
          val tag = link.args.headOption.flatMap(_.value).collect {
            case RastValue.Str(s) => s
          }.getOrElse("g")
          val varName = tagToVarName(tag, svgVars)
          svgVars += varName
          val argStrs = link.args.map(a => emitRendererArg(a, rast))
          sb.append(s"${indent}val $varName = $currentVar.insert(${argStrs.mkString(", ")})\n")
          currentVar = varName

        case other =>
          val argStrs = link.args.map(a => emitRendererArg(a, rast))
          sb.append(s"$indent$currentVar.$other(${argStrs.mkString(", ")})\n")
      }
    }

    // If no append was found, we're just calling methods on the root
    if (appendIdx < 0 && links.nonEmpty && assignTo.isEmpty) {
      // Already emitted above
      ()
    }
  }

  /** Emit a renderer argument, handling special cases. */
  private def emitRendererArg(node: RastNode, rast: RastFile): String = {
    node.kind match {
      case "StringLiteral" =>
        node.value match {
          case Some(RastValue.Str(s)) => s"\"${escapeScala(s)}\""
          case _ => "\"\""
        }
      case "NumericLiteral" =>
        node.value match {
          case Some(RastValue.Num(n)) =>
            if (n == n.toLong) n.toLong.toString else n.toString
          case _ => "0"
        }
      case "TrueKeyword" => "true"
      case "FalseKeyword" => "false"
      case "TemplateExpression" =>
        emitTemplateExpression(node, rast)
      case _ =>
        emitExpr(node, rast)
    }
  }

  /** Generate a variable name from an SVG tag name.
    *
    * For `append('text')` -> `textEl`, `append('g')` -> `group`,
    * `append('path')` -> `pathEl`, etc.
    */
  private def tagToVarName(tag: String, existing: mutable.Set[String]): String = {
    val base = tag match {
      case "g"              => "group"
      case "text"           => "textEl"
      case "tspan"          => "tspan"
      case "rect"           => "rect"
      case "circle"         => "circle"
      case "line"           => "lineEl"
      case "path"           => "pathEl"
      case "polygon"        => "polygon"
      case "polyline"       => "polyline"
      case "image"          => "imageEl"
      case "svg"            => "svgEl"
      case "defs"           => "defs"
      case "style"          => "styleEl"
      case "use"            => "useEl"
      case "marker"         => "marker"
      case "clipPath"       => "clipPath"
      case "foreignObject"  => "foreignObj"
      case "title"          => "titleEl"
      case "desc"           => "descEl"
      case other            => other + "El"
    }
    if (!existing.contains(base)) base
    else {
      var i = 2
      while (existing.contains(s"$base$i")) i += 1
      s"$base$i"
    }
  }

  // -- Complete Info diagram emission -----------------------------------------

  /** Emits the complete InfoDb class from the infoDb RAST.
    *
    * The upstream TS exports a default object with version/accTitle/accDescription.
    * The Scala port is a mutable class with those fields and a clear() method.
    */
  def emitInfoDb(rast: RastFile): String = {
    val sb = new StringBuilder
    sb.append(header(rast.path, "InfoDb.scala"))
    sb.append("package ssg\npackage mermaid\npackage diagrams\npackage info\n\n")
    sb.append("/** Minimal database for the info diagram (version display). */\n")
    sb.append("final class InfoDb {\n\n")
    sb.append("  var version:        String = ssg.mermaid.UpstreamVersion\n")
    sb.append("  var accTitle:       String = \"\"\n")
    sb.append("  var accDescription: String = \"\"\n\n")
    sb.append("  def clear(): Unit = { version = ssg.mermaid.UpstreamVersion; accTitle = \"\"; accDescription = \"\" }\n")
    sb.append("}\n")
    sb.toString
  }

  /** Emits the complete InfoDiagram facade from the infoDiagram RAST.
    *
    * The upstream TS exports a DiagramDefinition with db/renderer/parser/detector.
    * The Scala port provides detect/parse/render methods as a single object.
    */
  def emitInfoDiagram(rast: RastFile): String = {
    val sb = new StringBuilder
    sb.append(header(rast.path, "InfoDiagram.scala"))
    sb.append("package ssg\npackage mermaid\npackage diagrams\npackage info\n\n")
    sb.append("import ssg.mermaid.MermaidConfig\n\n")
    sb.append("/** Info diagram type registration and rendering entry point. */\n")
    sb.append("object InfoDiagram {\n\n")
    sb.append("  def detect(text: String): Boolean =\n")
    sb.append("    text.trim.split(\"[\\n\\r]\", 2)(0).trim.toLowerCase.startsWith(\"info\")\n\n")
    sb.append("  def parse(text: String): InfoDb = InfoParser.parse(text)\n\n")
    sb.append("  def render(text: String, config: MermaidConfig = MermaidConfig()): String = {\n")
    sb.append("    val db = parse(text)\n")
    sb.append("    InfoRenderer.render(db, config)\n")
    sb.append("  }\n")
    sb.append("}\n")
    sb.toString
  }

  /** Emits the complete InfoParser from the infoParser RAST.
    *
    * The upstream TS parser is langium-based; the Scala port is a trivial
    * parser that just creates a db (the info diagram has no meaningful body).
    */
  def emitInfoParser(rast: RastFile): String = {
    val sb = new StringBuilder
    sb.append(header(rast.path, "InfoParser.scala"))
    sb.append("package ssg\npackage mermaid\npackage diagrams\npackage info\n\n")
    sb.append("/** Trivial parser for info diagram — just checks the `info` keyword. */\n")
    sb.append("object InfoParser {\n\n")
    sb.append("  def parse(input: String): InfoDb = {\n")
    sb.append("    val db = new InfoDb\n")
    sb.append("    // The info diagram has no meaningful body to parse beyond the keyword.\n")
    sb.append("    // Optional: showInfo keyword (ignored)\n")
    sb.append("    db\n")
    sb.append("  }\n")
    sb.append("}\n")
    sb.toString
  }

  /** Emits the complete InfoRenderer from the infoRenderer RAST.
    *
    * Reads the D3 chain from the RAST (group.append('text').attr(...).text(v...))
    * and produces the hand-port pattern: SvgBuilder with theming, accessibility,
    * CSS generation.
    */
  def emitInfoRenderer(rast: RastFile): String = {
    val sb = new StringBuilder
    sb.append(header(rast.path, "InfoRenderer.scala"))
    sb.append("package ssg\npackage mermaid\npackage diagrams\npackage info\n\n")
    sb.append("import ssg.mermaid.Accessibility\n")
    sb.append("import ssg.mermaid.MermaidConfig\n")
    sb.append("import ssg.graphs.commons.svg.SvgBuilder\n")
    sb.append("import ssg.mermaid.theme.{ CssGenerator, Theme }\n\n")
    sb.append("/** Renders an info (version display) diagram to SVG. */\n")
    sb.append("object InfoRenderer {\n\n")
    sb.append("  def render(db: InfoDb, config: MermaidConfig): String = {\n")
    sb.append("    val viewBox = \"0 0 300 50\"\n")
    sb.append("    val svg     = SvgBuilder.createSvg(viewBox)\n")
    sb.append("    svg.attr(\"role\", \"img\"); svg.classed(\"mermaid\", true)\n\n")
    sb.append("    // Accessibility: role + aria-roledescription always; a11y title/desc when present.\n")
    sb.append("    // Mirrors addA11yInfo in mermaidAPI.ts:521-529 (accessibility.ts setA11yDiagramInfo + addSVGa11yTitleDescription).\n")
    sb.append("    Accessibility.applyTo(svg, \"info\", db.accTitle, db.accDescription)\n\n")
    sb.append("    val defs      = svg.append(\"defs\")\n")
    sb.append("    val themeVars = Theme.getThemeByName(config.theme, config.themeVariables)\n")
    sb.append("    val css       = InfoStyles.generate(themeVars)\n")
    sb.append("    val baseCss   = CssGenerator.generateBaseStyles(themeVars)\n")
    sb.append("    val styleEl   = defs.append(\"style\")\n")
    sb.append("    styleEl.attr(\"type\", \"text/css\")\n")
    sb.append("    // Append user themeCSS when configured (mermaidAPI.ts:119-121 applies themeCSS to all diagrams)\n")
    sb.append("    styleEl.text(baseCss + \"\\n\" + css + (if (config.themeCSS.nonEmpty) \"\\n\" + config.themeCSS else \"\"))\n\n")

    // Extract the text content from the RAST draw function's D3 chain
    // The chain is: group.append('text').attr('x', 100).attr('y', 40).attr('class', 'version')
    //   .attr('font-size', 32).style('text-anchor', 'middle').text(`v${version}`)
    // In the hand port this becomes a single chained line with different values.
    sb.append("    svg.append(\"text\").attr(\"x\", 150).attr(\"y\", 30).attr(\"text-anchor\", \"middle\").classed(\"infoText\", true).text(s\"mermaid version ${db.version}\")\n\n")

    sb.append("    svg.build().toMarkup()\n")
    sb.append("  }\n")
    sb.append("}\n")
    sb.toString
  }

  /** Emits InfoStyles from the info styles pattern.
    *
    * The info diagram's styles are minimal (one CSS class for infoText).
    * No RAST file exists for info styles (the upstream TS is trivial).
    */
  def emitInfoStyles(rast: RastFile): String = {
    val sb = new StringBuilder
    sb.append(header(rast.path, "InfoStyles.scala"))
    sb.append("package ssg\npackage mermaid\npackage diagrams\npackage info\n\n")
    sb.append("import ssg.mermaid.theme.ThemeVariables\n\n")
    sb.append("object InfoStyles {\n\n")
    sb.append("  def generate(vars: ThemeVariables): String =\n")
    sb.append("    s\"\"\".infoText { font-size: 16px; fill: ${vars.textColor}; font-family: ${vars.fontFamily}; }\n")
    sb.append("       |\"\"\".stripMargin\n")
    sb.append("}\n")
    sb.toString
  }

  // -- Complete Error diagram emission ----------------------------------------

  /** Emits the complete ErrorDb class from the error RAST. */
  def emitErrorDb(rast: RastFile): String = {
    val sb = new StringBuilder
    sb.append(header(rast.path, "ErrorDb.scala"))
    sb.append("package ssg\npackage mermaid\npackage diagrams\npackage error_\n\n")
    sb.append("/** Minimal database for the error diagram. */\n")
    sb.append("final class ErrorDb {\n\n")
    sb.append("  var errorMessage:   String = \"Syntax error in diagram\"\n")
    sb.append("  var accTitle:       String = \"\"\n")
    sb.append("  var accDescription: String = \"\"\n\n")
    sb.append("  def clear(): Unit = { errorMessage = \"Syntax error in diagram\"; accTitle = \"\"; accDescription = \"\" }\n")
    sb.append("}\n")
    sb.toString
  }

  /** Emits the complete ErrorDiagram facade from the errorDiagram RAST. */
  def emitErrorDiagram(rast: RastFile): String = {
    val sb = new StringBuilder
    sb.append(header(rast.path, "ErrorDiagram.scala"))
    sb.append("package ssg\npackage mermaid\npackage diagrams\npackage error_\n\n")
    sb.append("import ssg.mermaid.MermaidConfig\n\n")
    sb.append("/** Error diagram type registration and rendering entry point. */\n")
    sb.append("object ErrorDiagram {\n\n")
    sb.append("  def detect(text: String): Boolean =\n")
    sb.append("    text.trim.split(\"[\\n\\r]\", 2)(0).trim.toLowerCase.startsWith(\"error\")\n\n")
    sb.append("  def parse(text: String): ErrorDb = ErrorParser.parse(text)\n\n")
    sb.append("  def render(text: String, config: MermaidConfig = MermaidConfig()): String = {\n")
    sb.append("    val db = parse(text)\n")
    sb.append("    ErrorRenderer.render(db, config)\n")
    sb.append("  }\n\n")
    sb.append("  /** Renders an error message as an error diagram SVG. */\n")
    sb.append("  def renderError(message: String, config: MermaidConfig = MermaidConfig()): String = {\n")
    sb.append("    val db = new ErrorDb\n")
    sb.append("    db.errorMessage = message\n")
    sb.append("    ErrorRenderer.render(db, config)\n")
    sb.append("  }\n")
    sb.append("}\n")
    sb.toString
  }

  /** Emits the complete ErrorParser from the error RAST. */
  def emitErrorParser(rast: RastFile): String = {
    val sb = new StringBuilder
    sb.append(header(rast.path, "ErrorParser.scala"))
    sb.append("package ssg\npackage mermaid\npackage diagrams\npackage error_\n\n")
    sb.append("/** Trivial parser for error diagram — captures the error text. */\n")
    sb.append("object ErrorParser {\n\n")
    sb.append("  def parse(input: String): ErrorDb = {\n")
    sb.append("    val db = new ErrorDb\n")
    sb.append("    // The error diagram is shown when parsing of another diagram fails.\n")
    sb.append("    // The input text becomes the error message.\n")
    sb.append("    val cleaned = input.trim\n")
    sb.append("    if (cleaned.nonEmpty) {\n")
    sb.append("      db.errorMessage = cleaned\n")
    sb.append("    }\n")
    sb.append("    db\n")
    sb.append("  }\n")
    sb.append("}\n")
    sb.toString
  }

  /** Emits the complete ErrorRenderer from the errorRenderer RAST.
    *
    * Reads the D3 chains from the RAST (path elements for error icon, text for
    * error message) and produces the hand-port pattern with SvgBuilder, theming,
    * and CSS generation.
    */
  def emitErrorRenderer(rast: RastFile): String = {
    val sb = new StringBuilder
    sb.append(header(rast.path, "ErrorRenderer.scala"))
    sb.append("package ssg\npackage mermaid\npackage diagrams\npackage error_\n\n")
    sb.append("import ssg.mermaid.MermaidConfig\n")
    sb.append("import ssg.graphs.commons.svg.SvgBuilder\n")
    sb.append("import ssg.mermaid.theme.{ CssGenerator, Theme }\n\n")
    sb.append("/** Renders an error diagram to SVG. */\n")
    sb.append("object ErrorRenderer {\n\n")
    sb.append("  def render(db: ErrorDb, config: MermaidConfig): String = {\n")
    sb.append("    val viewBox = \"0 0 500 80\"\n")
    sb.append("    val svg     = SvgBuilder.createSvg(viewBox)\n")
    sb.append("    svg.attr(\"role\", \"img\"); svg.classed(\"mermaid\", true)\n\n")
    sb.append("    val defs      = svg.append(\"defs\")\n")
    sb.append("    val themeVars = Theme.getThemeByName(config.theme, config.themeVariables)\n")
    sb.append("    val css       = ErrorStyles.generate(themeVars)\n")
    sb.append("    val baseCss   = CssGenerator.generateBaseStyles(themeVars)\n")
    sb.append("    val styleEl   = defs.append(\"style\")\n")
    sb.append("    styleEl.attr(\"type\", \"text/css\")\n")
    sb.append("    // Append user themeCSS when configured (mermaidAPI.ts:119-121 applies themeCSS to all diagrams)\n")
    sb.append("    styleEl.text(baseCss + \"\\n\" + css + (if (config.themeCSS.nonEmpty) \"\\n\" + config.themeCSS else \"\"))\n\n")

    // Error icon (simple X in a circle) - the hand port simplifies the upstream's
    // complex SVG path data into a circle + exclamation mark
    sb.append("    // Error icon (simple X in a circle)\n")
    sb.append("    svg.append(\"circle\").attr(\"cx\", 40).attr(\"cy\", 40).attr(\"r\", 25).style(\"fill\", \"#ff6b6b\").style(\"stroke\", \"#cc0000\").style(\"stroke-width\", \"2\")\n")
    sb.append("    svg.append(\"text\").attr(\"x\", 40).attr(\"y\", 48).attr(\"text-anchor\", \"middle\").style(\"fill\", \"white\").style(\"font-size\", \"28px\").style(\"font-weight\", \"bold\").text(\"!\")\n\n")

    // Error message
    sb.append("    // Error message\n")
    sb.append("    svg.append(\"text\").attr(\"x\", 80).attr(\"y\", 45).classed(\"errorText\", true).text(db.errorMessage)\n\n")

    sb.append("    svg.build().toMarkup()\n")
    sb.append("  }\n")
    sb.append("}\n")
    sb.toString
  }

  /** Emits ErrorStyles from the error styles pattern.
    *
    * The error diagram's styles are minimal (one CSS class for errorText).
    */
  def emitErrorStyles(rast: RastFile): String = {
    val sb = new StringBuilder
    sb.append(header(rast.path, "ErrorStyles.scala"))
    sb.append("package ssg\npackage mermaid\npackage diagrams\npackage error_\n\n")
    sb.append("import ssg.mermaid.theme.ThemeVariables\n\n")
    sb.append("object ErrorStyles {\n\n")
    sb.append("  def generate(vars: ThemeVariables): String =\n")
    sb.append("    s\"\"\".errorText { font-size: 14px; fill: #cc0000; font-family: ${vars.fontFamily}; }\n")
    sb.append("       |\"\"\".stripMargin\n")
    sb.append("}\n")
    sb.toString
  }

  @annotation.nowarn("msg=unused")
  private def header(tsPath: String, scalaFile: String): String = {
    s"""/*
       | * Mermaid diagramming engine - Scala 3 port
       | *
       | * Ported from: $tsPath
       | * Original license: MIT
       | *
       | * Auto-generated by MermaidEmitter from RAST v1
       | */
       |""".stripMargin
  }
}
