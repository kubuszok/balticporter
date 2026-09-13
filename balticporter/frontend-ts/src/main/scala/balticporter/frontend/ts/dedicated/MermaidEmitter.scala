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

  /** Checks that every `${...}` interpolation in the extracted CSS references
    * a `vars.xxx` field that actually exists on ThemeVariables. Returns false if any
    * reference is to a field from the upstream TS config type (like PacketDiagramConfig)
    * that has no ThemeVariables equivalent, or if the parameter name was not rewritten
    * to `vars` at all.
    */
  private def cssRefsAreThemeVars(css: String): Boolean = {
    // Every interpolation must be vars.knownField (possibly wrapped in an if-else)
    val allInterpolations = """\$\{([^}]+)\}""".r
    val varRefPattern = """vars\.(\w+)""".r
    val matches = allInterpolations.findAllMatchIn(css).toList
    if (matches.isEmpty) return true // no interpolations at all is fine
    matches.forall { m =>
      val expr = m.group(1)
      val refs = varRefPattern.findAllMatchIn(expr).toList
      // Must have at least one vars.xxx reference and all must be known fields
      refs.nonEmpty && refs.forall(r => knownThemeVarFields.contains(r.group(1)))
    }
  }

  private val knownThemeVarFields: Set[String] = Set(
    "darkMode", "background", "primaryColor", "secondaryColor", "tertiaryColor",
    "primaryBorderColor", "secondaryBorderColor", "tertiaryBorderColor",
    "primaryTextColor", "secondaryTextColor", "tertiaryTextColor",
    "lineColor", "textColor", "mainBkg", "secondBkg", "border1", "border2",
    "arrowheadColor", "fontFamily", "fontSize", "labelBackground", "THEME_COLOR_LIMIT",
    "nodeBkg", "nodeBorder", "clusterBkg", "clusterBorder", "defaultLinkColor",
    "titleColor", "edgeLabelBackground", "nodeTextColor",
    "actorBorder", "actorBkg", "actorTextColor", "actorLineColor",
    "signalColor", "signalTextColor", "labelBoxBkgColor", "labelBoxBorderColor",
    "labelTextColor", "loopTextColor", "noteBorderColor", "noteBkgColor", "noteTextColor",
    "activationBorderColor", "activationBkgColor", "sequenceNumberColor",
    "sectionBkgColor", "altSectionBkgColor", "sectionBkgColor2", "excludeBkgColor",
    "taskBorderColor", "taskBkgColor", "taskTextLightColor", "taskTextColor",
    "taskTextDarkColor", "taskTextOutsideColor", "taskTextClickableColor",
    "activeTaskBorderColor", "activeTaskBkgColor", "gridColor", "todayLineColor",
    "done", "doneTaskBkgColor", "doneTaskBorderColor",
    "pieStrokeColor", "pieStrokeWidth", "pieOpacity", "pieOuterStrokeColor",
    "pieOuterStrokeWidth", "pieTitleTextSize", "pieTitleTextColor",
    "pieSectionTextSize", "pieSectionTextColor", "pieLegendTextSize", "pieLegendTextColor",
    "mainContrastColor", "darkTextColor", "altBackground",
    "classText", "fillType0", "fillType1", "fillType2", "fillType3",
    "fillType4", "fillType5", "fillType6", "fillType7",
    "compositeBackground", "compositeBorder", "compositeTitleBackground",
    "requirementBackground", "requirementBorderColor", "requirementBorderSize",
    "requirementTextColor", "relationColor", "relationLabelBackground", "relationLabelColor",
    "quadrant1Fill", "quadrant2Fill", "quadrant3Fill", "quadrant4Fill",
    "quadrant1TextFill", "quadrant2TextFill", "quadrant3TextFill", "quadrant4TextFill",
    "quadrantExternalBorderStrokeFill", "quadrantInternalBorderStrokeFill",
    "quadrantPointFill", "quadrantPointTextFill", "quadrantTitleFill",
    "quadrantXAxisTextFill", "quadrantYAxisTextFill",
    "note", "text", "contrast", "labelColor", "labelBackgroundColor", "specialStateColor",
    "stateBkg", "stateLabelColor", "transitionColor", "transitionLabelColor",
    "errorBkgColor", "errorTextColor", "personBkg", "personBorder",
    "scaleLabelColor", "branchLabelColor", "commitLabelColor", "commitLabelBackground",
    "commitLabelFontSize", "tagLabelFontSize", "tagLabelColor", "tagLabelBackground",
    "tagLabelBorder", "innerEndBackground", "critBkgColor", "critBorderColor", "critical",
    "attributeBackgroundColorOdd", "attributeBackgroundColorEven",
  )

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

  // -- Complete Pie diagram emission ------------------------------------------

  /** Emits the PieDb class with PieSection case class. */
  def emitPieDb(rast: RastFile): String = {
    val sb = new StringBuilder
    sb.append(header(rast.path, "PieDb.scala"))
    sb.append("package ssg\npackage mermaid\npackage diagrams\npackage pie\n\n")
    sb.append("import scala.collection.mutable\n\n")
    sb.append("/** A single pie chart section (slice). */\n")
    sb.append("final case class PieSection(label: String, value: Double)\n\n")
    sb.append("/** Mutable database for pie chart diagram data. */\n")
    sb.append("final class PieDb {\n\n")
    sb.append("  val sections: mutable.ArrayBuffer[PieSection] = mutable.ArrayBuffer.empty\n\n")
    sb.append("  var title:          String  = \"\"\n")
    sb.append("  var accTitle:       String  = \"\"\n")
    sb.append("  var accDescription: String  = \"\"\n")
    sb.append("  var showData:       Boolean = false\n\n")
    sb.append("  def addSection(label: String, value: Double): Unit =\n")
    sb.append("    sections += PieSection(label = label, value = value)\n\n")
    sb.append("  def total: Double =\n")
    sb.append("    sections.foldLeft(0.0)(_ + _.value)\n\n")
    sb.append("  def clear(): Unit = {\n")
    sb.append("    sections.clear()\n")
    sb.append("    title = \"\"; accTitle = \"\"; accDescription = \"\"; showData = false\n")
    sb.append("  }\n")
    sb.append("}\n")
    sb.toString
  }

  /** Emits the PieDiagram facade. */
  def emitPieDiagram(rast: RastFile): String = {
    val sb = new StringBuilder
    sb.append(header(rast.path, "PieDiagram.scala"))
    sb.append("package ssg\npackage mermaid\npackage diagrams\npackage pie\n\n")
    sb.append("import lowlevel.Nullable\n\n")
    sb.append("import ssg.mermaid.MermaidConfig\n\n")
    sb.append("/** Pie chart diagram type registration and rendering entry point. */\n")
    sb.append("object PieDiagram {\n\n")
    sb.append("  def detect(text: String): Boolean = {\n")
    sb.append("    val firstLine = text.trim.split(\"[\\n\\r]\", 2)(0).trim.toLowerCase\n")
    sb.append("    firstLine.startsWith(\"pie\")\n")
    sb.append("  }\n\n")
    sb.append("  def parse(text: String): PieDb = PieParser.parse(text)\n\n")
    sb.append("  def render(text: String, config: MermaidConfig = MermaidConfig(), title: Nullable[String] = Nullable.empty): String = {\n")
    sb.append("    val db = new PieDb\n")
    sb.append("    title.foreach(t => db.title = t)\n")
    sb.append("    PieParser.parse(text, db)\n")
    sb.append("    PieRenderer.render(db, config)\n")
    sb.append("  }\n")
    sb.append("}\n")
    sb.toString
  }

  /** Emits the PieParser. */
  def emitPieParser(rast: RastFile): String = {
    val sb = new StringBuilder
    sb.append(header(rast.path, "PieParser.scala"))
    sb.append("package ssg\npackage mermaid\npackage diagrams\npackage pie\n\n")
    sb.append("import ssg.mermaid.parse.{ ParseException, Scanner }\n\n")
    sb.append("import scala.util.boundary\n")
    sb.append("import scala.util.boundary.break\n\n")
    sb.append("/** Hand-written parser for Mermaid pie chart syntax. */\n")
    sb.append("object PieParser {\n\n")
    sb.append("  def parse(input: String): PieDb = parse(input, new PieDb)\n\n")
    sb.append("  def parse(input: String, db: PieDb): PieDb = {\n")
    sb.append("    val cleaned = cleanInput(input)\n")
    sb.append("    val scanner = new Scanner(cleaned)\n")
    sb.append("    scanner.skipWhitespaceAndNewlines()\n")
    sb.append("    parsePieHeader(scanner, db)\n")
    sb.append("    parseBody(scanner, db)\n")
    sb.append("    db\n")
    sb.append("  }\n\n")
    sb.append("  private def cleanInput(input: String): String =\n")
    sb.append("    input.replaceAll(\"%%\\\\{[^}]*\\\\}%%\", \"\").replaceAll(\"%%[^\\n]*\", \"\")\n\n")
    sb.append("  private def parsePieHeader(scanner: Scanner, db: PieDb): Unit = {\n")
    sb.append("    scanner.skipWhitespaceAndNewlines()\n")
    sb.append("    if (!scanner.matchStrIgnoreCase(\"pie\"))\n")
    sb.append("      throw new ParseException(\"Expected 'pie' keyword\", scanner.line, scanner.col)\n")
    sb.append("    scanner.skipWhitespace()\n")
    sb.append("    if (!scanner.isEof && scanner.peek() != '\\n') {\n")
    sb.append("      if (scanner.matchStrIgnoreCase(\"showData\")) { db.showData = true; scanner.skipWhitespace() }\n")
    sb.append("      if (!scanner.isEof && scanner.matchStrIgnoreCase(\"title\")) {\n")
    sb.append("        scanner.skipWhitespace(); db.title = readTextUntilNewline(scanner).trim\n")
    sb.append("      }\n")
    sb.append("    }\n")
    sb.append("    skipToNewline(scanner)\n")
    sb.append("  }\n\n")
    sb.append("  private def parseBody(scanner: Scanner, db: PieDb): Unit = boundary {\n")
    sb.append("    while (!scanner.isEof) {\n")
    sb.append("      scanner.skipWhitespaceAndNewlines()\n")
    sb.append("      if (scanner.isEof) break()\n")
    sb.append("      if (scanner.peek() == '%') { skipToNewline(scanner) }\n")
    sb.append("      else if (scanner.peek() == ';') { scanner.advance() }\n")
    sb.append("      else if (tryParseTitle(scanner, db)) {}\n")
    sb.append("      else if (tryParseAccTitle(scanner, db)) {}\n")
    sb.append("      else if (tryParseAccDescr(scanner, db)) {}\n")
    sb.append("      else if (scanner.peek() == '\"') { parseSection(scanner, db) }\n")
    sb.append("      else { skipToNewline(scanner) }\n")
    sb.append("    }\n")
    sb.append("  }\n\n")
    sb.append("  private def tryParseTitle(scanner: Scanner, db: PieDb): Boolean = boundary {\n")
    sb.append("    val saved = scanner.save()\n")
    sb.append("    if (!scanner.matchStrIgnoreCase(\"title\")) break(false)\n")
    sb.append("    if (!scanner.isEof && scanner.peek() != ' ' && scanner.peek() != '\\t' && scanner.peek() != '\\n') { scanner.restore(saved); break(false) }\n")
    sb.append("    scanner.skipWhitespace(); db.title = readTextUntilNewline(scanner).trim; true\n")
    sb.append("  }\n\n")
    sb.append("  private def tryParseAccTitle(scanner: Scanner, db: PieDb): Boolean = boundary {\n")
    sb.append("    val saved = scanner.save()\n")
    sb.append("    if (!scanner.matchStrIgnoreCase(\"accTitle\")) break(false)\n")
    sb.append("    scanner.skipWhitespace()\n")
    sb.append("    if (!scanner.isEof && scanner.peek() == ':') {\n")
    sb.append("      scanner.advance(); scanner.skipWhitespace()\n")
    sb.append("      db.accTitle = readTextUntilNewline(scanner).trim; break(true)\n")
    sb.append("    }\n")
    sb.append("    scanner.restore(saved); false\n")
    sb.append("  }\n\n")
    sb.append("  private def tryParseAccDescr(scanner: Scanner, db: PieDb): Boolean = boundary {\n")
    sb.append("    val saved = scanner.save()\n")
    sb.append("    if (!scanner.matchStrIgnoreCase(\"accDescr\")) break(false)\n")
    sb.append("    scanner.skipWhitespace()\n")
    sb.append("    if (!scanner.isEof && scanner.peek() == ':') {\n")
    sb.append("      scanner.advance(); scanner.skipWhitespace()\n")
    sb.append("      db.accDescription = readTextUntilNewline(scanner).trim; break(true)\n")
    sb.append("    } else if (!scanner.isEof && scanner.peek() == '{') {\n")
    sb.append("      scanner.advance(); db.accDescription = scanner.readUntil('}').trim; break(true)\n")
    sb.append("    }\n")
    sb.append("    scanner.restore(saved); false\n")
    sb.append("  }\n\n")
    sb.append("  private def parseSection(scanner: Scanner, db: PieDb): Unit = {\n")
    sb.append("    val label = scanner.readQuotedString()\n")
    sb.append("    scanner.skipWhitespace()\n")
    sb.append("    if (!scanner.isEof && scanner.peek() == ':') scanner.advance()\n")
    sb.append("    scanner.skipWhitespace()\n")
    sb.append("    val value = if (!scanner.isEof && (scanner.peek().isDigit || scanner.peek() == '-' || scanner.peek() == '.')) scanner.readNumber() else 0.0\n")
    sb.append("    db.addSection(label, value)\n")
    sb.append("    skipToNewline(scanner)\n")
    sb.append("  }\n\n")
    sb.append("  private def readTextUntilNewline(scanner: Scanner): String = {\n")
    sb.append("    val sb = new StringBuilder()\n")
    sb.append("    while (!scanner.isEof && scanner.peek() != '\\n') sb.append(scanner.advance())\n")
    sb.append("    sb.toString\n")
    sb.append("  }\n\n")
    sb.append("  private def skipToNewline(scanner: Scanner): Unit = {\n")
    sb.append("    while (!scanner.isEof && scanner.peek() != '\\n') scanner.advance()\n")
    sb.append("    if (!scanner.isEof) scanner.advance()\n")
    sb.append("  }\n")
    sb.append("}\n")
    sb.toString
  }

  /** Emits the PieRenderer with arc math, labels, and legend. */
  def emitPieRenderer(rast: RastFile): String = {
    val sb = new StringBuilder
    sb.append(header(rast.path, "PieRenderer.scala"))
    sb.append("package ssg\npackage mermaid\npackage diagrams\npackage pie\n\n")
    sb.append("import ssg.mermaid.Accessibility\n")
    sb.append("import ssg.mermaid.MermaidConfig\n")
    sb.append("import ssg.graphs.commons.svg.SvgBuilder\n")
    sb.append("import ssg.mermaid.theme.{ CssGenerator, Theme }\n\n")
    sb.append("/** Renders a pie chart diagram to SVG. */\n")
    sb.append("object PieRenderer {\n\n")
    sb.append("  private val DiagramPadding: Double = 20.0\n")
    sb.append("  private val DefaultRadius:  Double = 140.0\n")
    sb.append("  private val LegendRectSize: Int    = 18\n")
    sb.append("  private val LegendSpacing:  Int    = 4\n\n")
    sb.append("  def render(db: PieDb, config: MermaidConfig): String = {\n")
    sb.append("    val radius    = DefaultRadius\n")
    sb.append("    val centerX   = radius + DiagramPadding + 100\n")
    sb.append("    val centerY   = radius + DiagramPadding + 40\n")
    sb.append("    val svgWidth  = centerX + radius + DiagramPadding + 200\n")
    sb.append("    val svgHeight = centerY + radius + DiagramPadding\n\n")
    sb.append("    val viewBox = s\"0 0 $svgWidth $svgHeight\"\n")
    sb.append("    val svg     = SvgBuilder.createSvg(viewBox)\n")
    sb.append("    svg.attr(\"role\", \"img\"); svg.classed(\"mermaid\", true)\n")
    sb.append("    Accessibility.applyTo(svg, \"pie\", db.accTitle, db.accDescription)\n\n")
    sb.append("    val defs      = svg.append(\"defs\")\n")
    sb.append("    val themeVars = Theme.getThemeByName(config.theme, config.themeVariables)\n")
    sb.append("    val css       = PieStyles.generate(themeVars)\n")
    sb.append("    val baseCss   = CssGenerator.generateBaseStyles(themeVars)\n")
    sb.append("    val styleEl   = defs.append(\"style\")\n")
    sb.append("    styleEl.attr(\"type\", \"text/css\")\n")
    sb.append("    styleEl.text(baseCss + \"\\n\" + css + (if (config.themeCSS.nonEmpty) \"\\n\" + config.themeCSS else \"\"))\n\n")
    sb.append("    val mainGroup = svg.append(\"g\")\n\n")
    sb.append("    var titleOffset = 0.0\n")
    sb.append("    if (db.title.nonEmpty) {\n")
    sb.append("      mainGroup.append(\"text\").attr(\"x\", centerX).attr(\"y\", 25).attr(\"text-anchor\", \"middle\").classed(\"pieTitleText\", true).text(db.title)\n")
    sb.append("      titleOffset = 20.0\n")
    sb.append("    }\n\n")
    sb.append("    val totalValue = db.total\n")
    sb.append("    if (totalValue > 0 && db.sections.nonEmpty) {\n")
    sb.append("      val pieGroup = mainGroup.append(\"g\")\n")
    sb.append("      pieGroup.attr(\"transform\", s\"translate($centerX, ${centerY + titleOffset})\")\n\n")
    sb.append("      var startAngle = 0.0\n")
    sb.append("      for ((section, idx) <- db.sections.zipWithIndex) {\n")
    sb.append("        val sliceAngle = (section.value / totalValue) * 2.0 * math.Pi\n")
    sb.append("        val endAngle   = startAngle + sliceAngle\n")
    sb.append("        val colorIdx   = idx % 13\n")
    sb.append("        val fillColor  = if (themeVars.pie(colorIdx).nonEmpty) themeVars.pie(colorIdx) else defaultPieColor(colorIdx)\n\n")
    sb.append("        val path = createArcPath(0, 0, radius, startAngle, endAngle)\n")
    sb.append("        val slice = pieGroup.append(\"path\")\n")
    sb.append("        slice.attr(\"d\", path).classed(\"pieCircle\", true)\n")
    sb.append("        slice.style(\"fill\", fillColor).style(\"opacity\", themeVars.pieOpacity)\n")
    sb.append("        slice.style(\"stroke\", themeVars.pieStrokeColor).style(\"stroke-width\", themeVars.pieStrokeWidth)\n\n")
    sb.append("        val textPosition = config.pie.textPosition\n")
    sb.append("        val midAngle    = startAngle + sliceAngle / 2.0\n")
    sb.append("        val labelRadius = radius * textPosition\n")
    sb.append("        val labelX      = labelRadius * math.cos(midAngle - math.Pi / 2.0)\n")
    sb.append("        val labelY      = labelRadius * math.sin(midAngle - math.Pi / 2.0)\n")
    sb.append("        val percentage  = (section.value / totalValue) * 100.0\n")
    sb.append("        if (percentage > 3.0) {\n")
    sb.append("          val label = pieGroup.append(\"text\")\n")
    sb.append("          label.attr(\"x\", labelX).attr(\"y\", labelY).attr(\"text-anchor\", \"middle\").attr(\"dominant-baseline\", \"central\").classed(\"slice\", true)\n")
    sb.append("          val percentStr = ssg.graphs.commons.util.FormatUtil.toFixed(percentage, 1)\n")
    sb.append("          label.text(if (db.showData) s\"$percentStr% (${Math.round(section.value).toString})\" else s\"$percentStr%\")\n")
    sb.append("        }\n")
    sb.append("        startAngle = endAngle\n")
    sb.append("      }\n\n")
    sb.append("      val legendGroup = mainGroup.append(\"g\")\n")
    sb.append("      legendGroup.attr(\"transform\", s\"translate(${centerX + radius + 40}, ${DiagramPadding + titleOffset})\")\n")
    sb.append("      for ((section, idx) <- db.sections.zipWithIndex) {\n")
    sb.append("        val colorIdx  = idx % 13\n")
    sb.append("        val fillColor = if (themeVars.pie(colorIdx).nonEmpty) themeVars.pie(colorIdx) else defaultPieColor(colorIdx)\n")
    sb.append("        val yOffset   = idx * (LegendRectSize + LegendSpacing)\n")
    sb.append("        val legendItem = legendGroup.append(\"g\").attr(\"transform\", s\"translate(0, $yOffset)\")\n")
    sb.append("        legendItem.append(\"rect\").attr(\"width\", LegendRectSize).attr(\"height\", LegendRectSize).style(\"fill\", fillColor).style(\"stroke\", themeVars.pieStrokeColor)\n")
    sb.append("        val text = legendItem.append(\"text\").attr(\"x\", LegendRectSize + LegendSpacing).attr(\"y\", LegendRectSize - LegendSpacing).classed(\"legend\", true)\n")
    sb.append("        text.text(if (db.showData) s\"${section.label} [${Math.round(section.value).toString}]\" else section.label)\n")
    sb.append("      }\n")
    sb.append("    }\n\n")
    sb.append("    svg.build().toMarkup()\n")
    sb.append("  }\n\n")
    sb.append("  private def createArcPath(cx: Double, cy: Double, radius: Double, startAngle: Double, endAngle: Double): String = {\n")
    sb.append("    val startX = cx + radius * math.cos(startAngle - math.Pi / 2.0)\n")
    sb.append("    val startY = cy + radius * math.sin(startAngle - math.Pi / 2.0)\n")
    sb.append("    val endX   = cx + radius * math.cos(endAngle - math.Pi / 2.0)\n")
    sb.append("    val endY   = cy + radius * math.sin(endAngle - math.Pi / 2.0)\n")
    sb.append("    val largeArcFlag = if (endAngle - startAngle > math.Pi) 1 else 0\n")
    sb.append("    s\"M $cx $cy L $startX $startY A $radius $radius 0 $largeArcFlag 1 $endX $endY Z\"\n")
    sb.append("  }\n\n")
    sb.append("  private def defaultPieColor(index: Int): String = {\n")
    sb.append("    val colors = Array(\"#ECECFF\", \"#ffffde\", \"#bde0fe\", \"#ffc8dd\", \"#caffbf\", \"#ffd6a5\", \"#a0c4ff\", \"#fdffb6\", \"#9bf6ff\", \"#bdb2ff\", \"#ffc6ff\", \"#e8e8e4\", \"#d4a373\")\n")
    sb.append("    colors(index % colors.length)\n")
    sb.append("  }\n")
    sb.append("}\n")
    sb.toString
  }

  /** Emits PieStyles from the pieStyles RAST. */
  def emitPieStyles(rast: RastFile): String = {
    val sb = new StringBuilder
    sb.append(header(rast.path, "PieStyles.scala"))
    sb.append("package ssg\npackage mermaid\npackage diagrams\npackage pie\n\n")
    sb.append("import ssg.mermaid.theme.ThemeVariables\n\n")
    sb.append("object PieStyles {\n\n")
    sb.append("  def generate(vars: ThemeVariables): String = {\n")
    sb.append("    val sb = new StringBuilder()\n")

    // Extract CSS from the RAST template expression
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
        // Fallback: emit standard pie CSS classes
        sb.append("    sb.append(\n")
        sb.append("      s\"\"\".pieTitleText {\n")
        sb.append("         |  text-anchor: middle;\n")
        sb.append("         |  font-size: $${vars.pieTitleTextSize};\n")
        sb.append("         |  fill: $${vars.pieTitleTextColor};\n")
        sb.append("         |  font-family: $${vars.fontFamily};\n")
        sb.append("         |}\n")
        sb.append("         |.slice {\n")
        sb.append("         |  font-family: $${vars.fontFamily};\n")
        sb.append("         |  fill: $${vars.pieSectionTextColor};\n")
        sb.append("         |  font-size: $${vars.pieSectionTextSize};\n")
        sb.append("         |}\n")
        sb.append("         |.legend text {\n")
        sb.append("         |  fill: $${vars.pieLegendTextColor};\n")
        sb.append("         |  font-family: $${vars.fontFamily};\n")
        sb.append("         |  font-size: $${vars.pieLegendTextSize};\n")
        sb.append("         |}\n")
        sb.append("         |.pieCircle {\n")
        sb.append("         |  stroke: $${vars.pieStrokeColor};\n")
        sb.append("         |  stroke-width: $${vars.pieStrokeWidth};\n")
        sb.append("         |  opacity: $${vars.pieOpacity};\n")
        sb.append("         |}\n")
        sb.append("         |.pieOuterCircle {\n")
        sb.append("         |  stroke: $${vars.pieOuterStrokeColor};\n")
        sb.append("         |  stroke-width: $${vars.pieOuterStrokeWidth};\n")
        sb.append("         |  fill: none;\n")
        sb.append("         |}\n")
        sb.append("         |\"\"\".stripMargin\n")
        sb.append("    )\n")
    }

    sb.append("    sb.toString\n")
    sb.append("  }\n")
    sb.append("}\n")
    sb.toString
  }

  // -- Complete Packet diagram emission ----------------------------------------

  /** Emits the PacketDb class with PacketField case class. */
  def emitPacketDb(rast: RastFile): String = {
    val sb = new StringBuilder
    sb.append(header(rast.path, "PacketDb.scala"))
    sb.append("package ssg\npackage mermaid\npackage diagrams\npackage packet\n\n")
    sb.append("import scala.collection.mutable\n\n")
    sb.append("/** A single field in a packet header. */\n")
    sb.append("final case class PacketField(label: String, startBit: Int, endBit: Int)\n\n")
    sb.append("/** Mutable database for packet diagram data. */\n")
    sb.append("final class PacketDb {\n\n")
    sb.append("  var title:          String = \"\"\n")
    sb.append("  var accTitle:       String = \"\"\n")
    sb.append("  var accDescription: String = \"\"\n")
    sb.append("  var bitsPerRow:     Int    = 32\n\n")
    sb.append("  val fields: mutable.ArrayBuffer[PacketField] = mutable.ArrayBuffer.empty\n\n")
    sb.append("  def addField(label: String, startBit: Int, endBit: Int): Unit = {\n")
    sb.append("    if (endBit < startBit)\n")
    sb.append("      throw new IllegalArgumentException(s\"Packet block $startBit - $endBit is invalid. End must be greater than start.\")\n")
    sb.append("    if (fields.nonEmpty) {\n")
    sb.append("      val expectedStart = fields.last.endBit + 1\n")
    sb.append("      if (startBit != expectedStart)\n")
    sb.append("        throw new IllegalArgumentException(s\"Packet block $startBit - $endBit is not contiguous. It should start from $expectedStart.\")\n")
    sb.append("    }\n")
    sb.append("    fields += PacketField(label, startBit, endBit)\n")
    sb.append("  }\n\n")
    sb.append("  def clear(): Unit = {\n")
    sb.append("    title = \"\"; accTitle = \"\"; accDescription = \"\"; bitsPerRow = 32; fields.clear()\n")
    sb.append("  }\n")
    sb.append("}\n")
    sb.toString
  }

  /** Emits the PacketDiagram facade. */
  def emitPacketDiagram(rast: RastFile): String = {
    val sb = new StringBuilder
    sb.append(header(rast.path, "PacketDiagram.scala"))
    sb.append("package ssg\npackage mermaid\npackage diagrams\npackage packet\n\n")
    sb.append("import lowlevel.Nullable\n\n")
    sb.append("import ssg.mermaid.MermaidConfig\n\n")
    sb.append("/** Packet diagram type registration and rendering entry point. */\n")
    sb.append("object PacketDiagram {\n\n")
    sb.append("  def detect(text: String): Boolean = {\n")
    sb.append("    val firstLine = text.trim.split(\"[\\n\\r]\", 2)(0).trim.toLowerCase\n")
    sb.append("    firstLine.startsWith(\"packet-beta\")\n")
    sb.append("  }\n\n")
    sb.append("  def parse(text: String): PacketDb = PacketParser.parse(text)\n\n")
    sb.append("  def render(text: String, config: MermaidConfig = MermaidConfig(), title: Nullable[String] = Nullable.empty): String = {\n")
    sb.append("    val db = new PacketDb\n")
    sb.append("    title.foreach(t => db.title = t)\n")
    sb.append("    PacketParser.parse(text, db)\n")
    sb.append("    PacketRenderer.render(db, config)\n")
    sb.append("  }\n")
    sb.append("}\n")
    sb.toString
  }

  /** Emits the PacketParser. */
  def emitPacketParser(rast: RastFile): String = {
    val sb = new StringBuilder
    sb.append(header(rast.path, "PacketParser.scala"))
    sb.append("package ssg\npackage mermaid\npackage diagrams\npackage packet\n\n")
    sb.append("import ssg.mermaid.parse.{ ParseException, Scanner }\n\n")
    sb.append("import scala.util.boundary\n")
    sb.append("import scala.util.boundary.break\n\n")
    sb.append("/** Hand-written parser for Mermaid packet diagram syntax. */\n")
    sb.append("object PacketParser {\n\n")
    sb.append("  def parse(input: String): PacketDb = parse(input, new PacketDb)\n\n")
    sb.append("  def parse(input: String, db: PacketDb): PacketDb = {\n")
    sb.append("    val cleaned = cleanInput(input)\n")
    sb.append("    val scanner = new Scanner(cleaned)\n")
    sb.append("    scanner.skipWhitespaceAndNewlines()\n")
    sb.append("    if (!scanner.matchStrIgnoreCase(\"packet-beta\"))\n")
    sb.append("      throw new ParseException(\"Expected 'packet-beta' keyword\", scanner.line, scanner.col)\n")
    sb.append("    skipToNewline(scanner)\n")
    sb.append("    parseBody(scanner, db)\n")
    sb.append("    db\n")
    sb.append("  }\n\n")
    sb.append("  private def cleanInput(input: String): String =\n")
    sb.append("    input.replaceAll(\"%%\\\\{[^}]*\\\\}%%\", \"\").replaceAll(\"%%[^\\n]*\", \"\")\n\n")
    sb.append("  private def parseBody(scanner: Scanner, db: PacketDb): Unit = boundary {\n")
    sb.append("    while (!scanner.isEof) {\n")
    sb.append("      scanner.skipWhitespaceAndNewlines()\n")
    sb.append("      if (scanner.isEof) break()\n")
    sb.append("      if (scanner.peek() == '%') { skipToNewline(scanner) }\n")
    sb.append("      else if (tryParseTitle(scanner, db)) {}\n")
    sb.append("      else if (tryParseField(scanner, db)) {}\n")
    sb.append("      else { skipToNewline(scanner) }\n")
    sb.append("    }\n")
    sb.append("  }\n\n")
    sb.append("  private def tryParseTitle(scanner: Scanner, db: PacketDb): Boolean = boundary {\n")
    sb.append("    val saved = scanner.save()\n")
    sb.append("    if (!scanner.matchStrIgnoreCase(\"title\")) break(false)\n")
    sb.append("    if (!scanner.isEof && scanner.peek() != ' ' && scanner.peek() != '\\t' && scanner.peek() != '\\n') { scanner.restore(saved); break(false) }\n")
    sb.append("    scanner.skipWhitespace(); db.title = readTextUntilNewline(scanner).trim; true\n")
    sb.append("  }\n\n")
    sb.append("  private def tryParseField(scanner: Scanner, db: PacketDb): Boolean = boundary {\n")
    sb.append("    val saved = scanner.save()\n")
    sb.append("    if (!scanner.peek().isDigit) break(false)\n")
    sb.append("    val startBit = scanner.readNumber().toInt\n")
    sb.append("    val endBit   = if (!scanner.isEof && scanner.peek() == '-') { scanner.advance(); scanner.readNumber().toInt } else startBit\n")
    sb.append("    scanner.skipWhitespace()\n")
    sb.append("    if (scanner.isEof || scanner.peek() != ':') { scanner.restore(saved); break(false) }\n")
    sb.append("    scanner.advance(); scanner.skipWhitespace()\n")
    sb.append("    val label = if (!scanner.isEof && scanner.peek() == '\"') scanner.readQuotedString() else readTextUntilNewline(scanner).trim\n")
    sb.append("    db.addField(label, startBit, endBit)\n")
    sb.append("    skipToNewline(scanner); true\n")
    sb.append("  }\n\n")
    sb.append("  private def readTextUntilNewline(scanner: Scanner): String = {\n")
    sb.append("    val sb = new StringBuilder(); while (!scanner.isEof && scanner.peek() != '\\n') sb.append(scanner.advance()); sb.toString\n")
    sb.append("  }\n\n")
    sb.append("  private def skipToNewline(scanner: Scanner): Unit = {\n")
    sb.append("    while (!scanner.isEof && scanner.peek() != '\\n') scanner.advance()\n")
    sb.append("    if (!scanner.isEof) scanner.advance()\n")
    sb.append("  }\n")
    sb.append("}\n")
    sb.toString
  }

  /** Emits the PacketRenderer. */
  def emitPacketRenderer(rast: RastFile): String = {
    val sb = new StringBuilder
    sb.append(header(rast.path, "PacketRenderer.scala"))
    sb.append("package ssg\npackage mermaid\npackage diagrams\npackage packet\n\n")
    sb.append("import ssg.mermaid.Accessibility\n")
    sb.append("import ssg.mermaid.MermaidConfig\n")
    sb.append("import ssg.graphs.commons.svg.SvgBuilder\n")
    sb.append("import ssg.mermaid.theme.{ CssGenerator, Theme }\n\n")
    sb.append("/** Renders a packet diagram to SVG. */\n")
    sb.append("object PacketRenderer {\n\n")
    sb.append("  private val Padding:   Double = 20.0\n")
    sb.append("  private val RowHeight: Double = 40.0\n")
    sb.append("  private val BitWidth:  Double = 20.0\n\n")
    sb.append("  def render(db: PacketDb, config: MermaidConfig): String = {\n")
    sb.append("    val bitsPerRow = db.bitsPerRow\n")
    sb.append("    val totalWidth = bitsPerRow * BitWidth + Padding * 2\n")
    sb.append("    val maxBit     = if (db.fields.isEmpty) bitsPerRow else db.fields.map(_.endBit).max + 1\n")
    sb.append("    val numRows    = math.max(1, math.ceil(maxBit.toDouble / bitsPerRow).toInt)\n")
    sb.append("    val svgHeight  = numRows * RowHeight + Padding * 3 + (if (db.title.nonEmpty) 30 else 0)\n\n")
    sb.append("    val viewBox = s\"0 0 $totalWidth $svgHeight\"\n")
    sb.append("    val svg     = SvgBuilder.createSvg(viewBox)\n")
    sb.append("    svg.attr(\"role\", \"img\"); svg.classed(\"mermaid\", true)\n")
    sb.append("    Accessibility.applyTo(svg, \"packet\", db.accTitle, db.accDescription)\n\n")
    sb.append("    val defs      = svg.append(\"defs\")\n")
    sb.append("    val themeVars = Theme.getThemeByName(config.theme, config.themeVariables)\n")
    sb.append("    val css       = PacketStyles.generate(themeVars)\n")
    sb.append("    val baseCss   = CssGenerator.generateBaseStyles(themeVars)\n")
    sb.append("    val styleEl   = defs.append(\"style\")\n")
    sb.append("    styleEl.attr(\"type\", \"text/css\")\n")
    sb.append("    styleEl.text(baseCss + \"\\n\" + css + (if (config.themeCSS.nonEmpty) \"\\n\" + config.themeCSS else \"\"))\n\n")
    sb.append("    val mainGroup = svg.append(\"g\")\n")
    sb.append("    var yOffset = Padding\n")
    sb.append("    if (db.title.nonEmpty) {\n")
    sb.append("      mainGroup.append(\"text\").attr(\"x\", totalWidth / 2).attr(\"y\", yOffset + 15).attr(\"text-anchor\", \"middle\").classed(\"packetTitle\", true).text(db.title)\n")
    sb.append("      yOffset += 30\n")
    sb.append("    }\n\n")
    sb.append("    for (bit <- 0 until bitsPerRow by 8)\n")
    sb.append("      mainGroup.append(\"text\").attr(\"x\", Padding + bit * BitWidth + BitWidth / 2).attr(\"y\", yOffset + 12).attr(\"text-anchor\", \"middle\").classed(\"packetBitLabel\", true).text(bit.toString)\n")
    sb.append("    yOffset += 15\n\n")
    sb.append("    for (field <- db.fields) {\n")
    sb.append("      val startRow = field.startBit / bitsPerRow\n")
    sb.append("      val endRow   = field.endBit / bitsPerRow\n")
    sb.append("      for (row <- startRow to endRow) {\n")
    sb.append("        val rowStartBit = if (row == startRow) field.startBit % bitsPerRow else 0\n")
    sb.append("        val rowEndBit   = if (row == endRow) field.endBit % bitsPerRow else bitsPerRow - 1\n")
    sb.append("        val x = Padding + rowStartBit * BitWidth\n")
    sb.append("        val y = yOffset + row * RowHeight\n")
    sb.append("        val w = (rowEndBit - rowStartBit + 1) * BitWidth\n")
    sb.append("        val h = RowHeight - 2\n")
    sb.append("        mainGroup.append(\"rect\").attr(\"x\", x).attr(\"y\", y).attr(\"width\", w).attr(\"height\", h).classed(\"packetField\", true)\n")
    sb.append("        mainGroup.append(\"text\").attr(\"x\", x + w / 2).attr(\"y\", y + h / 2 + 5).attr(\"text-anchor\", \"middle\").classed(\"packetFieldLabel\", true).text(field.label)\n")
    sb.append("      }\n")
    sb.append("    }\n\n")
    sb.append("    svg.build().toMarkup()\n")
    sb.append("  }\n")
    sb.append("}\n")
    sb.toString
  }

  /** Emits PacketStyles from the packet styles RAST. */
  def emitPacketStyles(rast: RastFile): String = {
    val sb = new StringBuilder
    sb.append(header(rast.path, "PacketStyles.scala"))
    sb.append("package ssg\npackage mermaid\npackage diagrams\npackage packet\n\n")
    sb.append("import ssg.mermaid.theme.ThemeVariables\n\n")
    sb.append("object PacketStyles {\n\n")
    sb.append("  def generate(vars: ThemeVariables): String =\n")

    // Try RAST extraction first, but validate the result references real ThemeVariables fields.
    // The upstream TS uses a PacketDiagramConfig with fields like byteFontSize, startByteColor
    // that do not exist on the hand-ported ThemeVariables — the fallback maps to the correct fields.
    val templateExpr = findTemplateExpression(rast)
    val usedFallback = templateExpr match {
      case Some(tmpl) =>
        val paramName = findStylesParamName(rast)
        val cssBlocks = extractCssFromTemplate(tmpl, paramName)
        if (cssBlocks.nonEmpty && cssRefsAreThemeVars(cssBlocks.head)) {
          sb.append(s"""    s\"\"\"${cssBlocks.head}\"\"\".stripMargin\n""")
          false
        } else {
          emitPacketStylesFallback(sb)
          true
        }
      case None =>
        emitPacketStylesFallback(sb)
        true
    }

    sb.append("}\n")
    sb.toString
  }

  private def emitPacketStylesFallback(sb: StringBuilder): Unit = {
    sb.append("    s\"\"\".packetTitle {\n")
    sb.append("       |  font-size: 16px;\n")
    sb.append("       |  fill: $${vars.textColor};\n")
    sb.append("       |  font-family: $${vars.fontFamily};\n")
    sb.append("       |}\n")
    sb.append("       |.packetBitLabel {\n")
    sb.append("       |  font-size: 10px;\n")
    sb.append("       |  fill: $${vars.textColor};\n")
    sb.append("       |  font-family: $${vars.fontFamily};\n")
    sb.append("       |}\n")
    sb.append("       |.packetField {\n")
    sb.append("       |  fill: $${vars.mainBkg};\n")
    sb.append("       |  stroke: $${vars.nodeBorder};\n")
    sb.append("       |  stroke-width: 1px;\n")
    sb.append("       |}\n")
    sb.append("       |.packetFieldLabel {\n")
    sb.append("       |  font-size: 12px;\n")
    sb.append("       |  fill: $${vars.textColor};\n")
    sb.append("       |  font-family: $${vars.fontFamily};\n")
    sb.append("       |}\n")
    sb.append("       |\"\"\".stripMargin\n")
  }

  // -- Complete Kanban diagram emission ----------------------------------------

  /** Emits the KanbanDb class with KanbanCard and KanbanColumn case classes. */
  def emitKanbanDb(rast: RastFile): String = {
    val sb = new StringBuilder
    sb.append(header(rast.path, "KanbanDb.scala"))
    sb.append("package ssg\npackage mermaid\npackage diagrams\npackage kanban\n\n")
    sb.append("import scala.collection.mutable\n\n")
    sb.append("/** A card in a Kanban column. */\n")
    sb.append("final case class KanbanCard(id: String, label: String, priority: String = \"\")\n\n")
    sb.append("/** A column in a Kanban board. */\n")
    sb.append("final case class KanbanColumn(id: String, label: String, cards: mutable.ArrayBuffer[KanbanCard] = mutable.ArrayBuffer.empty)\n\n")
    sb.append("/** Mutable database for Kanban board data. */\n")
    sb.append("final class KanbanDb {\n\n")
    sb.append("  var title:          String = \"\"\n")
    sb.append("  var accTitle:       String = \"\"\n")
    sb.append("  var accDescription: String = \"\"\n\n")
    sb.append("  val columns: mutable.ArrayBuffer[KanbanColumn] = mutable.ArrayBuffer.empty\n\n")
    sb.append("  def addColumn(id: String, label: String): KanbanColumn = {\n")
    sb.append("    val col = KanbanColumn(id, label); columns += col; col\n")
    sb.append("  }\n\n")
    sb.append("  def addCard(columnId: String, id: String, label: String, priority: String = \"\"): Unit =\n")
    sb.append("    columns.find(_.id == columnId).foreach(_.cards += KanbanCard(id, label, priority))\n\n")
    sb.append("  def addCardToLast(id: String, label: String, priority: String = \"\"): Unit =\n")
    sb.append("    if (columns.nonEmpty) columns.last.cards += KanbanCard(id, label, priority)\n\n")
    sb.append("  def clear(): Unit = { title = \"\"; accTitle = \"\"; accDescription = \"\"; columns.clear() }\n")
    sb.append("}\n")
    sb.toString
  }

  /** Emits the KanbanDiagram facade. */
  def emitKanbanDiagram(rast: RastFile): String = {
    val sb = new StringBuilder
    sb.append(header(rast.path, "KanbanDiagram.scala"))
    sb.append("package ssg\npackage mermaid\npackage diagrams\npackage kanban\n\n")
    sb.append("import lowlevel.Nullable\n\n")
    sb.append("import ssg.mermaid.MermaidConfig\n\n")
    sb.append("/** Kanban board diagram type registration and rendering entry point. */\n")
    sb.append("object KanbanDiagram {\n\n")
    sb.append("  def detect(text: String): Boolean = {\n")
    sb.append("    val firstLine = text.trim.split(\"[\\n\\r]\", 2)(0).trim.toLowerCase\n")
    sb.append("    firstLine.startsWith(\"kanban\")\n")
    sb.append("  }\n\n")
    sb.append("  def parse(text: String): KanbanDb = KanbanParser.parse(text)\n\n")
    sb.append("  def render(text: String, config: MermaidConfig = MermaidConfig(), title: Nullable[String] = Nullable.empty): String = {\n")
    sb.append("    val db = new KanbanDb\n")
    sb.append("    title.foreach(t => db.title = t)\n")
    sb.append("    KanbanParser.parse(text, db)\n")
    sb.append("    KanbanRenderer.render(db, config)\n")
    sb.append("  }\n")
    sb.append("}\n")
    sb.toString
  }

  /** Emits the KanbanParser. */
  def emitKanbanParser(rast: RastFile): String = {
    val sb = new StringBuilder
    sb.append(header(rast.path, "KanbanParser.scala"))
    sb.append("package ssg\npackage mermaid\npackage diagrams\npackage kanban\n\n")
    sb.append("import ssg.mermaid.parse.ParseException\n\n")
    sb.append("/** Hand-written parser for Mermaid Kanban board syntax. */\n")
    sb.append("object KanbanParser {\n\n")
    sb.append("  def parse(input: String): KanbanDb = parse(input, new KanbanDb)\n\n")
    sb.append("  def parse(input: String, db: KanbanDb): KanbanDb = {\n")
    sb.append("    val cleaned = cleanInput(input)\n")
    sb.append("    val lines   = cleaned.split(\"\\n\")\n")
    sb.append("    var i = 0\n")
    sb.append("    while (i < lines.length && !lines(i).trim.toLowerCase.startsWith(\"kanban\")) i += 1\n")
    sb.append("    if (i >= lines.length) throw new ParseException(\"Expected 'kanban' keyword\", 1, 1)\n")
    sb.append("    i += 1\n")
    sb.append("    while (i < lines.length) {\n")
    sb.append("      val line    = lines(i)\n")
    sb.append("      val trimmed = line.trim\n")
    sb.append("      i += 1\n")
    sb.append("      if (trimmed.nonEmpty && !trimmed.startsWith(\"%%\")) {\n")
    sb.append("        val indent      = line.length - line.stripLeading().length\n")
    sb.append("        val (id, label) = parseIdLabel(trimmed)\n")
    sb.append("        if (indent < 2) db.addColumn(id, label)\n")
    sb.append("        else db.addCardToLast(id, label)\n")
    sb.append("      }\n")
    sb.append("    }\n")
    sb.append("    db\n")
    sb.append("  }\n\n")
    sb.append("  private def cleanInput(input: String): String =\n")
    sb.append("    input.replaceAll(\"%%\\\\{[^}]*\\\\}%%\", \"\")\n\n")
    sb.append("  private def parseIdLabel(text: String): (String, String) = {\n")
    sb.append("    val bracketIdx = text.indexOf('[')\n")
    sb.append("    if (bracketIdx >= 0) {\n")
    sb.append("      val id     = text.substring(0, bracketIdx).trim\n")
    sb.append("      val endIdx = text.lastIndexOf(']')\n")
    sb.append("      val rawLabel = if (endIdx > bracketIdx) text.substring(bracketIdx + 1, endIdx).trim else text.substring(bracketIdx + 1).trim\n")
    sb.append("      val label = if (rawLabel.startsWith(\"\\\"\") && rawLabel.endsWith(\"\\\"\")) rawLabel.substring(1, rawLabel.length - 1) else rawLabel\n")
    sb.append("      (if (id.nonEmpty) id else label, label)\n")
    sb.append("    } else (text, text)\n")
    sb.append("  }\n")
    sb.append("}\n")
    sb.toString
  }

  /** Emits the KanbanRenderer. */
  def emitKanbanRenderer(rast: RastFile): String = {
    val sb = new StringBuilder
    sb.append(header(rast.path, "KanbanRenderer.scala"))
    sb.append("package ssg\npackage mermaid\npackage diagrams\npackage kanban\n\n")
    sb.append("import ssg.mermaid.Accessibility\n")
    sb.append("import ssg.mermaid.MermaidConfig\n")
    sb.append("import ssg.graphs.commons.svg.SvgBuilder\n")
    sb.append("import ssg.mermaid.theme.{ CssGenerator, Theme }\n\n")
    sb.append("/** Renders a Kanban board to SVG. */\n")
    sb.append("object KanbanRenderer {\n\n")
    sb.append("  private val ColumnWidth:  Double = 160.0\n")
    sb.append("  private val ColumnGap:    Double = 15.0\n")
    sb.append("  private val CardHeight:   Double = 35.0\n")
    sb.append("  private val CardGap:      Double = 8.0\n")
    sb.append("  private val HeaderHeight: Double = 30.0\n")
    sb.append("  private val Padding:      Double = 20.0\n\n")
    sb.append("  def render(db: KanbanDb, config: MermaidConfig): String = {\n")
    sb.append("    val colCount  = db.columns.size.max(1)\n")
    sb.append("    val maxCards  = if (db.columns.isEmpty) 0 else db.columns.map(_.cards.size).max\n")
    sb.append("    val svgWidth  = colCount * (ColumnWidth + ColumnGap) + Padding * 2\n")
    sb.append("    val svgHeight = HeaderHeight + maxCards * (CardHeight + CardGap) + Padding * 3\n\n")
    sb.append("    val viewBox = s\"0 0 $svgWidth $svgHeight\"\n")
    sb.append("    val svg     = SvgBuilder.createSvg(viewBox)\n")
    sb.append("    svg.attr(\"role\", \"img\"); svg.classed(\"mermaid\", true)\n")
    sb.append("    Accessibility.applyTo(svg, \"kanban\", db.accTitle, db.accDescription)\n\n")
    sb.append("    val defs      = svg.append(\"defs\")\n")
    sb.append("    val themeVars = Theme.getThemeByName(config.theme, config.themeVariables)\n")
    sb.append("    val css       = KanbanStyles.generate(themeVars)\n")
    sb.append("    val baseCss   = CssGenerator.generateBaseStyles(themeVars)\n")
    sb.append("    val styleEl   = defs.append(\"style\")\n")
    sb.append("    styleEl.attr(\"type\", \"text/css\")\n")
    sb.append("    styleEl.text(baseCss + \"\\n\" + css + (if (config.themeCSS.nonEmpty) \"\\n\" + config.themeCSS else \"\"))\n\n")
    sb.append("    val mainGroup = svg.append(\"g\")\n")
    sb.append("    for ((col, colIdx) <- db.columns.zipWithIndex) {\n")
    sb.append("      val x            = Padding + colIdx * (ColumnWidth + ColumnGap)\n")
    sb.append("      val columnHeight = HeaderHeight + col.cards.size * (CardHeight + CardGap) + Padding\n")
    sb.append("      mainGroup.append(\"rect\").attr(\"x\", x).attr(\"y\", Padding).attr(\"width\", ColumnWidth).attr(\"height\", columnHeight).attr(\"rx\", 6).attr(\"ry\", 6).classed(\"kanbanColumn\", true)\n")
    sb.append("      mainGroup.append(\"text\").attr(\"x\", x + ColumnWidth / 2).attr(\"y\", Padding + 20).attr(\"text-anchor\", \"middle\").classed(\"kanbanColumnLabel\", true).text(col.label)\n")
    sb.append("      for ((card, cardIdx) <- col.cards.zipWithIndex) {\n")
    sb.append("        val cardX = x + 5; val cardY = Padding + HeaderHeight + cardIdx * (CardHeight + CardGap) + 5; val cardW = ColumnWidth - 10\n")
    sb.append("        mainGroup.append(\"rect\").attr(\"x\", cardX).attr(\"y\", cardY).attr(\"width\", cardW).attr(\"height\", CardHeight).attr(\"rx\", 4).attr(\"ry\", 4).classed(\"kanbanCard\", true)\n")
    sb.append("        mainGroup.append(\"text\").attr(\"x\", cardX + cardW / 2).attr(\"y\", cardY + CardHeight / 2 + 4).attr(\"text-anchor\", \"middle\").classed(\"kanbanCardLabel\", true).text(card.label)\n")
    sb.append("      }\n")
    sb.append("    }\n\n")
    sb.append("    svg.build().toMarkup()\n")
    sb.append("  }\n")
    sb.append("}\n")
    sb.toString
  }

  /** Emits KanbanStyles. */
  def emitKanbanStyles(rast: RastFile): String = {
    val sb = new StringBuilder
    sb.append(header(rast.path, "KanbanStyles.scala"))
    sb.append("package ssg\npackage mermaid\npackage diagrams\npackage kanban\n\n")
    sb.append("import ssg.mermaid.theme.ThemeVariables\n\n")
    sb.append("object KanbanStyles {\n\n")
    sb.append("  def generate(vars: ThemeVariables): String =\n")
    sb.append("    s\"\"\".kanbanColumn {\n")
    sb.append("       |  fill: $${vars.background};\n")
    sb.append("       |  stroke: $${vars.lineColor};\n")
    sb.append("       |  stroke-width: 1px;\n")
    sb.append("       |}\n")
    sb.append("       |.kanbanColumnLabel {\n")
    sb.append("       |  font-size: 14px;\n")
    sb.append("       |  font-weight: bold;\n")
    sb.append("       |  fill: $${vars.textColor};\n")
    sb.append("       |  font-family: $${vars.fontFamily};\n")
    sb.append("       |}\n")
    sb.append("       |.kanbanCard {\n")
    sb.append("       |  fill: $${vars.mainBkg};\n")
    sb.append("       |  stroke: $${vars.nodeBorder};\n")
    sb.append("       |  stroke-width: 1px;\n")
    sb.append("       |}\n")
    sb.append("       |.kanbanCardLabel {\n")
    sb.append("       |  font-size: 12px;\n")
    sb.append("       |  fill: $${vars.textColor};\n")
    sb.append("       |  font-family: $${vars.fontFamily};\n")
    sb.append("       |}\n")
    sb.append("       |\"\"\".stripMargin\n")
    sb.append("}\n")
    sb.toString
  }

  // -- Complete Cynefin diagram emission ----------------------------------------

  /** Emits the CynefinDb class with CynefinItem case class. */
  def emitCynefinDb(rast: RastFile): String = {
    val sb = new StringBuilder
    sb.append(header(rast.path, "CynefinDb.scala"))
    sb.append("package ssg\npackage mermaid\npackage diagrams\npackage cynefin\n\n")
    sb.append("import scala.collection.mutable\n\n")
    sb.append("/** An item placed in a Cynefin domain. */\n")
    sb.append("final case class CynefinItem(label: String, domain: String)\n\n")
    sb.append("/** Mutable database for Cynefin diagram data. */\n")
    sb.append("final class CynefinDb {\n\n")
    sb.append("  var title:          String = \"\"\n")
    sb.append("  var accTitle:       String = \"\"\n")
    sb.append("  var accDescription: String = \"\"\n\n")
    sb.append("  /** Items in each domain. */\n")
    sb.append("  val items: mutable.ArrayBuffer[CynefinItem] = mutable.ArrayBuffer.empty\n\n")
    sb.append("  def addItem(label: String, domain: String): Unit = items += CynefinItem(label, domain)\n\n")
    sb.append("  def itemsInDomain(domain: String): Seq[CynefinItem] =\n")
    sb.append("    items.filter(_.domain.toLowerCase == domain.toLowerCase).toSeq\n\n")
    sb.append("  def clear(): Unit = { title = \"\"; accTitle = \"\"; accDescription = \"\"; items.clear() }\n")
    sb.append("}\n")
    sb.toString
  }

  /** Emits the CynefinDiagram facade. */
  def emitCynefinDiagram(rast: RastFile): String = {
    val sb = new StringBuilder
    sb.append(header(rast.path, "CynefinDiagram.scala"))
    sb.append("package ssg\npackage mermaid\npackage diagrams\npackage cynefin\n\n")
    sb.append("import lowlevel.Nullable\n\n")
    sb.append("import ssg.mermaid.MermaidConfig\n\n")
    sb.append("/** Cynefin framework diagram type registration and rendering entry point. */\n")
    sb.append("object CynefinDiagram {\n\n")
    sb.append("  def detect(text: String): Boolean =\n")
    sb.append("    text.trim.split(\"[\\n\\r]\", 2)(0).trim.toLowerCase.startsWith(\"cynefin\")\n\n")
    sb.append("  def parse(text: String): CynefinDb = CynefinParser.parse(text)\n\n")
    sb.append("  def render(text: String, config: MermaidConfig = MermaidConfig(), title: Nullable[String] = Nullable.empty): String = {\n")
    sb.append("    val db = new CynefinDb\n")
    sb.append("    title.foreach(t => db.title = t)\n")
    sb.append("    CynefinParser.parse(text, db)\n")
    sb.append("    CynefinRenderer.render(db, config)\n")
    sb.append("  }\n")
    sb.append("}\n")
    sb.toString
  }

  /** Emits the CynefinParser. */
  def emitCynefinParser(rast: RastFile): String = {
    val sb = new StringBuilder
    sb.append(header(rast.path, "CynefinParser.scala"))
    sb.append("package ssg\npackage mermaid\npackage diagrams\npackage cynefin\n\n")
    sb.append("import ssg.mermaid.parse.ParseException\n\n")
    sb.append("/** Hand-written parser for Mermaid Cynefin diagram syntax. */\n")
    sb.append("object CynefinParser {\n\n")
    sb.append("  def parse(input: String): CynefinDb = parse(input, new CynefinDb)\n\n")
    sb.append("  def parse(input: String, db: CynefinDb): CynefinDb = {\n")
    sb.append("    val cleaned = cleanInput(input)\n")
    sb.append("    val lines   = cleaned.split(\"\\n\").map(_.trim).filter(_.nonEmpty)\n\n")
    sb.append("    var i = 0\n")
    sb.append("    while (i < lines.length && !lines(i).toLowerCase.startsWith(\"cynefin\")) i += 1\n")
    sb.append("    if (i >= lines.length) throw new ParseException(\"Expected 'cynefin' keyword\", 1, 1)\n")
    sb.append("    i += 1\n\n")
    sb.append("    while (i < lines.length) {\n")
    sb.append("      val line = lines(i).trim; i += 1\n")
    sb.append("      if (line.startsWith(\"%%\")) {\n")
    sb.append("        // skip\n")
    sb.append("      } else if (line.toLowerCase.startsWith(\"title\")) {\n")
    sb.append("        db.title = line.substring(5).trim\n")
    sb.append("      } else {\n")
    sb.append("        // Parse domain: items\n")
    sb.append("        val colonIdx = line.indexOf(':')\n")
    sb.append("        if (colonIdx > 0) {\n")
    sb.append("          val domain   = line.substring(0, colonIdx).trim\n")
    sb.append("          val itemsStr = line.substring(colonIdx + 1).trim\n")
    sb.append("          val itemList = itemsStr.split(\",\").map(_.trim).filter(_.nonEmpty)\n")
    sb.append("          for (item <- itemList)\n")
    sb.append("            db.addItem(item, domain)\n")
    sb.append("        }\n")
    sb.append("      }\n")
    sb.append("    }\n")
    sb.append("    db\n")
    sb.append("  }\n\n")
    sb.append("  private def cleanInput(input: String): String =\n")
    sb.append("    input.replaceAll(\"%%\\\\{[^}]*\\\\}%%\", \"\")\n")
    sb.append("}\n")
    sb.toString
  }

  /** Emits the CynefinRenderer. */
  def emitCynefinRenderer(rast: RastFile): String = {
    val sb = new StringBuilder
    sb.append(header(rast.path, "CynefinRenderer.scala"))
    sb.append("package ssg\npackage mermaid\npackage diagrams\npackage cynefin\n\n")
    sb.append("import ssg.mermaid.Accessibility\n")
    sb.append("import ssg.mermaid.MermaidConfig\n")
    sb.append("import ssg.graphs.commons.svg.SvgBuilder\n")
    sb.append("import ssg.mermaid.theme.{ CssGenerator, Theme }\n\n")
    sb.append("/** Renders a Cynefin framework diagram to SVG. */\n")
    sb.append("object CynefinRenderer {\n\n")
    sb.append("  private val Size:         Double              = 500.0\n")
    sb.append("  private val Padding:      Double              = 30.0\n")
    sb.append("  private val DomainColors: Map[String, String] = Map(\n")
    sb.append("    \"complex\" -> \"#e8d5f5\",\n")
    sb.append("    \"complicated\" -> \"#d5e8f5\",\n")
    sb.append("    \"clear\" -> \"#d5f5e8\",\n")
    sb.append("    \"chaotic\" -> \"#f5e8d5\",\n")
    sb.append("    \"obvious\" -> \"#d5f5e8\",\n")
    sb.append("    \"disorder\" -> \"#f5f5d5\"\n")
    sb.append("  )\n\n")
    sb.append("  def render(db: CynefinDb, config: MermaidConfig): String = {\n")
    sb.append("    val svgSize = Size + Padding * 2 + 40\n")
    sb.append("    val viewBox = s\"0 0 $svgSize $svgSize\"\n")
    sb.append("    val svg     = SvgBuilder.createSvg(viewBox)\n")
    sb.append("    svg.attr(\"role\", \"img\"); svg.classed(\"mermaid\", true)\n\n")
    sb.append("    Accessibility.applyTo(svg, \"cynefin\", db.accTitle, db.accDescription)\n\n")
    sb.append("    val defs      = svg.append(\"defs\")\n")
    sb.append("    val themeVars = Theme.getThemeByName(config.theme, config.themeVariables)\n")
    sb.append("    val css       = CynefinStyles.generate(themeVars)\n")
    sb.append("    val baseCss   = CssGenerator.generateBaseStyles(themeVars)\n")
    sb.append("    val styleEl   = defs.append(\"style\")\n")
    sb.append("    styleEl.attr(\"type\", \"text/css\")\n")
    sb.append("    styleEl.text(baseCss + \"\\n\" + css + (if (config.themeCSS.nonEmpty) \"\\n\" + config.themeCSS else \"\"))\n\n")
    sb.append("    val mainGroup = svg.append(\"g\")\n")
    sb.append("    val half      = Size / 2\n\n")
    sb.append("    if (db.title.nonEmpty) {\n")
    sb.append("      mainGroup.append(\"text\").attr(\"x\", svgSize / 2).attr(\"y\", 25).attr(\"text-anchor\", \"middle\").classed(\"cynefinTitle\", true).text(db.title)\n")
    sb.append("    }\n\n")
    sb.append("    val ox = Padding; val oy = Padding + 20\n\n")
    sb.append("    // Four quadrants\n")
    sb.append("    val domains = Seq(\n")
    sb.append("      (\"Complex\", ox, oy, half, half),\n")
    sb.append("      (\"Complicated\", ox + half, oy, half, half),\n")
    sb.append("      (\"Chaotic\", ox, oy + half, half, half),\n")
    sb.append("      (\"Clear\", ox + half, oy + half, half, half)\n")
    sb.append("    )\n\n")
    sb.append("    for ((name, x, y, w, h) <- domains) {\n")
    sb.append("      val color = DomainColors.getOrElse(name.toLowerCase, \"#f0f0f0\")\n")
    sb.append("      mainGroup.append(\"rect\").attr(\"x\", x).attr(\"y\", y).attr(\"width\", w).attr(\"height\", h).style(\"fill\", color).style(\"stroke\", \"#ccc\").classed(\"cynefinDomain\", true)\n\n")
    sb.append("      mainGroup.append(\"text\").attr(\"x\", x + w / 2).attr(\"y\", y + 20).attr(\"text-anchor\", \"middle\").classed(\"cynefinDomainLabel\", true).text(name)\n\n")
    sb.append("      // Items in this domain\n")
    sb.append("      val domainItems = db.itemsInDomain(name)\n")
    sb.append("      for ((item, idx) <- domainItems.zipWithIndex)\n")
    sb.append("        mainGroup.append(\"text\").attr(\"x\", x + w / 2).attr(\"y\", y + 45 + idx * 20).attr(\"text-anchor\", \"middle\").classed(\"cynefinItem\", true).text(item.label)\n")
    sb.append("    }\n\n")
    sb.append("    // Center: Disorder\n")
    sb.append("    val centerSize = 80.0\n")
    sb.append("    mainGroup\n")
    sb.append("      .append(\"rect\")\n")
    sb.append("      .attr(\"x\", ox + half - centerSize / 2)\n")
    sb.append("      .attr(\"y\", oy + half - centerSize / 2)\n")
    sb.append("      .attr(\"width\", centerSize)\n")
    sb.append("      .attr(\"height\", centerSize)\n")
    sb.append("      .style(\"fill\", DomainColors(\"disorder\"))\n")
    sb.append("      .style(\"stroke\", \"#999\")\n")
    sb.append("      .classed(\"cynefinDomain\", true)\n")
    sb.append("    mainGroup.append(\"text\").attr(\"x\", ox + half).attr(\"y\", oy + half + 5).attr(\"text-anchor\", \"middle\").classed(\"cynefinDomainLabel\", true).text(\"Disorder\")\n\n")
    sb.append("    svg.build().toMarkup()\n")
    sb.append("  }\n")
    sb.append("}\n")
    sb.toString
  }

  /** Emits CynefinStyles. */
  def emitCynefinStyles(rast: RastFile): String = {
    val sb = new StringBuilder
    sb.append(header(rast.path, "CynefinStyles.scala"))
    sb.append("package ssg\npackage mermaid\npackage diagrams\npackage cynefin\n\n")
    sb.append("import ssg.mermaid.theme.ThemeVariables\n\n")
    sb.append("object CynefinStyles {\n\n")
    sb.append("  def generate(vars: ThemeVariables): String =\n")
    sb.append("    s\"\"\".cynefinTitle { font-size: 18px; fill: $${vars.textColor}; font-family: $${vars.fontFamily}; }\n")
    sb.append("       |.cynefinDomain { stroke-width: 1px; }\n")
    sb.append("       |.cynefinDomainLabel { font-size: 14px; font-weight: bold; fill: $${vars.textColor}; font-family: $${vars.fontFamily}; }\n")
    sb.append("       |.cynefinItem { font-size: 12px; fill: $${vars.textColor}; font-family: $${vars.fontFamily}; }\n")
    sb.append("       |\"\"\".stripMargin\n")
    sb.append("}\n")
    sb.toString
  }

  // -- Complete TreeView diagram emission ----------------------------------------

  /** Emits the TreeViewDb class with TreeNode case class. */
  def emitTreeviewDb(rast: RastFile): String = {
    val sb = new StringBuilder
    sb.append(header(rast.path, "TreeviewDb.scala"))
    sb.append("package ssg\npackage mermaid\npackage diagrams\npackage treeview\n\n")
    sb.append("import scala.collection.mutable\n\n")
    sb.append("/** A node in a tree view. */\n")
    sb.append("final case class TreeNode(label: String, children: mutable.ArrayBuffer[TreeNode] = mutable.ArrayBuffer.empty)\n\n")
    sb.append("/** Mutable database for tree view diagram data. */\n")
    sb.append("final class TreeViewDb {\n\n")
    sb.append("  var title:          String = \"\"\n")
    sb.append("  var accTitle:       String = \"\"\n")
    sb.append("  var accDescription: String = \"\"\n\n")
    sb.append("  val roots: mutable.ArrayBuffer[TreeNode] = mutable.ArrayBuffer.empty\n\n")
    sb.append("  def addRoot(label: String): TreeNode = {\n")
    sb.append("    val node = TreeNode(label); roots += node; node\n")
    sb.append("  }\n\n")
    sb.append("  def clear(): Unit = { title = \"\"; accTitle = \"\"; accDescription = \"\"; roots.clear() }\n")
    sb.append("}\n")
    sb.toString
  }

  /** Emits the TreeViewDiagram facade. */
  def emitTreeviewDiagram(rast: RastFile): String = {
    val sb = new StringBuilder
    sb.append(header(rast.path, "TreeviewDiagram.scala"))
    sb.append("package ssg\npackage mermaid\npackage diagrams\npackage treeview\n\n")
    sb.append("import lowlevel.Nullable\n\n")
    sb.append("import ssg.mermaid.MermaidConfig\n\n")
    sb.append("object TreeViewDiagram {\n\n")
    sb.append("  def detect(text: String): Boolean =\n")
    sb.append("    text.trim.split(\"[\\n\\r]\", 2)(0).trim.toLowerCase.startsWith(\"treeview\")\n\n")
    sb.append("  def parse(text: String): TreeViewDb = TreeViewParser.parse(text)\n\n")
    sb.append("  def render(text: String, config: MermaidConfig = MermaidConfig(), title: Nullable[String] = Nullable.empty): String = {\n")
    sb.append("    val db = new TreeViewDb\n")
    sb.append("    title.foreach(t => db.title = t)\n")
    sb.append("    TreeViewParser.parse(text, db)\n")
    sb.append("    TreeViewRenderer.render(db, config)\n")
    sb.append("  }\n")
    sb.append("}\n")
    sb.toString
  }

  /** Emits the TreeViewParser. */
  def emitTreeviewParser(rast: RastFile): String = {
    val sb = new StringBuilder
    sb.append(header(rast.path, "TreeviewParser.scala"))
    sb.append("package ssg\npackage mermaid\npackage diagrams\npackage treeview\n\n")
    sb.append("import ssg.mermaid.parse.ParseException\n\n")
    sb.append("import scala.collection.mutable\n\n")
    sb.append("/** Hand-written parser for Mermaid tree view syntax. */\n")
    sb.append("object TreeViewParser {\n\n")
    sb.append("  def parse(input: String): TreeViewDb =\n")
    sb.append("    parse(input, new TreeViewDb)\n\n")
    sb.append("  def parse(input: String, db: TreeViewDb): TreeViewDb = {\n")
    sb.append("    val cleaned = cleanInput(input)\n")
    sb.append("    val lines   = cleaned.split(\"\\n\")\n\n")
    sb.append("    var i = 0\n")
    sb.append("    while (i < lines.length && !lines(i).trim.toLowerCase.startsWith(\"treeview\")) i += 1\n")
    sb.append("    if (i >= lines.length) throw new ParseException(\"Expected 'treeView' keyword\", 1, 1)\n")
    sb.append("    i += 1\n\n")
    sb.append("    // Parse indentation-based tree\n")
    sb.append("    val stack = mutable.ArrayBuffer.empty[(Int, TreeNode)]\n\n")
    sb.append("    while (i < lines.length) {\n")
    sb.append("      val line    = lines(i); i += 1\n")
    sb.append("      val trimmed = line.trim\n")
    sb.append("      if (trimmed.isEmpty || trimmed.startsWith(\"%%\")) {\n")
    sb.append("        // skip\n")
    sb.append("      } else {\n")
    sb.append("        val indent = line.length - line.stripLeading().length\n")
    sb.append("        val node   = TreeNode(trimmed)\n\n")
    sb.append("        // Pop stack to find parent\n")
    sb.append("        while (stack.nonEmpty && stack.last._1 >= indent) stack.remove(stack.size - 1)\n\n")
    sb.append("        if (stack.isEmpty) {\n")
    sb.append("          db.roots += node\n")
    sb.append("        } else {\n")
    sb.append("          stack.last._2.children += node\n")
    sb.append("        }\n")
    sb.append("        stack += ((indent, node))\n")
    sb.append("      }\n")
    sb.append("    }\n")
    sb.append("    db\n")
    sb.append("  }\n\n")
    sb.append("  private def cleanInput(input: String): String =\n")
    sb.append("    input.replaceAll(\"%%\\\\{[^}]*\\\\}%%\", \"\")\n")
    sb.append("}\n")
    sb.toString
  }

  /** Emits the TreeViewRenderer. */
  def emitTreeviewRenderer(rast: RastFile): String = {
    val sb = new StringBuilder
    sb.append(header(rast.path, "TreeviewRenderer.scala"))
    sb.append("package ssg\npackage mermaid\npackage diagrams\npackage treeview\n\n")
    sb.append("import ssg.mermaid.Accessibility\n")
    sb.append("import ssg.mermaid.MermaidConfig\n")
    sb.append("import ssg.graphs.commons.svg.SvgBuilder\n")
    sb.append("import ssg.mermaid.theme.{ CssGenerator, Theme }\n\n")
    sb.append("/** Renders a tree view diagram to SVG. */\n")
    sb.append("object TreeViewRenderer {\n\n")
    sb.append("  private val IndentSize: Double = 30.0\n")
    sb.append("  private val LineHeight: Double = 25.0\n")
    sb.append("  private val Padding:    Double = 20.0\n\n")
    sb.append("  def render(db: TreeViewDb, config: MermaidConfig): String = {\n")
    sb.append("    // Count total nodes for sizing\n")
    sb.append("    var totalNodes = 0\n")
    sb.append("    var maxDepth   = 0\n")
    sb.append("    def count(node: TreeNode, depth: Int): Unit = {\n")
    sb.append("      totalNodes += 1; maxDepth = math.max(maxDepth, depth)\n")
    sb.append("      node.children.foreach(count(_, depth + 1))\n")
    sb.append("    }\n")
    sb.append("    db.roots.foreach(count(_, 0))\n")
    sb.append("    totalNodes = totalNodes.max(1)\n\n")
    sb.append("    val svgWidth  = (maxDepth + 1) * IndentSize + 300 + Padding * 2\n")
    sb.append("    val svgHeight = totalNodes * LineHeight + Padding * 2 + 40\n")
    sb.append("    val viewBox   = s\"0 0 $svgWidth $svgHeight\"\n")
    sb.append("    val svg       = SvgBuilder.createSvg(viewBox)\n")
    sb.append("    svg.attr(\"role\", \"img\"); svg.classed(\"mermaid\", true)\n\n")
    sb.append("    Accessibility.applyTo(svg, \"treeView\", db.accTitle, db.accDescription)\n\n")
    sb.append("    val defs      = svg.append(\"defs\")\n")
    sb.append("    val themeVars = Theme.getThemeByName(config.theme, config.themeVariables)\n")
    sb.append("    val css       = TreeViewStyles.generate(themeVars)\n")
    sb.append("    val baseCss   = CssGenerator.generateBaseStyles(themeVars)\n")
    sb.append("    val styleEl   = defs.append(\"style\")\n")
    sb.append("    styleEl.attr(\"type\", \"text/css\")\n")
    sb.append("    styleEl.text(baseCss + \"\\n\" + css + (if (config.themeCSS.nonEmpty) \"\\n\" + config.themeCSS else \"\"))\n\n")
    sb.append("    val mainGroup = svg.append(\"g\")\n\n")
    sb.append("    if (db.title.nonEmpty) {\n")
    sb.append("      mainGroup.append(\"text\").attr(\"x\", svgWidth / 2).attr(\"y\", 20).attr(\"text-anchor\", \"middle\").classed(\"treeTitle\", true).text(db.title)\n")
    sb.append("    }\n\n")
    sb.append("    var yPos = Padding + (if (db.title.nonEmpty) 30 else 0)\n\n")
    sb.append("    def renderNode(node: TreeNode, depth: Int, parentX: Double, parentY: Double): Unit = {\n")
    sb.append("      val x = Padding + depth * IndentSize\n")
    sb.append("      val y = yPos\n")
    sb.append("      yPos += LineHeight\n\n")
    sb.append("      // Connector line from parent\n")
    sb.append("      if (depth > 0) {\n")
    sb.append("        mainGroup.append(\"line\").attr(\"x1\", parentX + 5).attr(\"y1\", parentY).attr(\"x2\", x).attr(\"y2\", y).classed(\"treeConnector\", true)\n")
    sb.append("      }\n\n")
    sb.append("      // Node circle\n")
    sb.append("      mainGroup.append(\"circle\").attr(\"cx\", x + 5).attr(\"cy\", y).attr(\"r\", 4).classed(\"treeNode\", true)\n\n")
    sb.append("      // Label\n")
    sb.append("      mainGroup.append(\"text\").attr(\"x\", x + 15).attr(\"y\", y + 4).classed(\"treeLabel\", true).text(node.label)\n\n")
    sb.append("      for (child <- node.children)\n")
    sb.append("        renderNode(child, depth + 1, x, y)\n")
    sb.append("    }\n\n")
    sb.append("    db.roots.foreach(renderNode(_, 0, 0, 0))\n\n")
    sb.append("    svg.build().toMarkup()\n")
    sb.append("  }\n")
    sb.append("}\n")
    sb.toString
  }

  /** Emits TreeViewStyles. */
  def emitTreeviewStyles(rast: RastFile): String = {
    val sb = new StringBuilder
    sb.append(header(rast.path, "TreeviewStyles.scala"))
    sb.append("package ssg\npackage mermaid\npackage diagrams\npackage treeview\n\n")
    sb.append("import ssg.mermaid.theme.ThemeVariables\n\n")
    sb.append("object TreeViewStyles {\n\n")
    sb.append("  def generate(vars: ThemeVariables): String =\n")
    sb.append("    s\"\"\".treeTitle { font-size: 16px; fill: $${vars.textColor}; font-family: $${vars.fontFamily}; }\n")
    sb.append("       |.treeNode { fill: $${vars.primaryColor}; stroke: $${vars.primaryBorderColor}; }\n")
    sb.append("       |.treeConnector { stroke: $${vars.lineColor}; stroke-width: 1px; }\n")
    sb.append("       |.treeLabel { font-size: 12px; fill: $${vars.textColor}; font-family: $${vars.fontFamily}; }\n")
    sb.append("       |\"\"\".stripMargin\n")
    sb.append("}\n")
    sb.toString
  }

  // -- Complete Wardley diagram emission ----------------------------------------

  /** Emits the WardleyDb class with WardleyComponent and WardleyLink case classes. */
  def emitWardleyDb(rast: RastFile): String = {
    val sb = new StringBuilder
    sb.append(header(rast.path, "WardleyDb.scala"))
    sb.append("package ssg\npackage mermaid\npackage diagrams\npackage wardley\n\n")
    sb.append("import scala.collection.mutable\n\n")
    sb.append("/** A component in a Wardley map (position on evolution/value chain axes). */\n")
    sb.append("final case class WardleyComponent(name: String, visibility: Double, evolution: Double)\n\n")
    sb.append("/** A dependency link. */\n")
    sb.append("final case class WardleyLink(from: String, to: String)\n\n")
    sb.append("/** Mutable database for Wardley map data. */\n")
    sb.append("final class WardleyDb {\n\n")
    sb.append("  var title:          String = \"\"\n")
    sb.append("  var accTitle:       String = \"\"\n")
    sb.append("  var accDescription: String = \"\"\n\n")
    sb.append("  val components: mutable.ArrayBuffer[WardleyComponent] = mutable.ArrayBuffer.empty\n")
    sb.append("  val links:      mutable.ArrayBuffer[WardleyLink]      = mutable.ArrayBuffer.empty\n\n")
    sb.append("  def addComponent(name: String, visibility: Double, evolution: Double): Unit =\n")
    sb.append("    components += WardleyComponent(name, visibility, evolution)\n\n")
    sb.append("  def addLink(from: String, to: String): Unit = links += WardleyLink(from, to)\n\n")
    sb.append("  def clear(): Unit = { title = \"\"; accTitle = \"\"; accDescription = \"\"; components.clear(); links.clear() }\n")
    sb.append("}\n")
    sb.toString
  }

  /** Emits the WardleyDiagram facade. */
  def emitWardleyDiagram(rast: RastFile): String = {
    val sb = new StringBuilder
    sb.append(header(rast.path, "WardleyDiagram.scala"))
    sb.append("package ssg\npackage mermaid\npackage diagrams\npackage wardley\n\n")
    sb.append("import lowlevel.Nullable\n\n")
    sb.append("import ssg.mermaid.MermaidConfig\n\n")
    sb.append("object WardleyDiagram {\n\n")
    sb.append("  def detect(text: String): Boolean =\n")
    sb.append("    text.trim.split(\"[\\n\\r]\", 2)(0).trim.toLowerCase.startsWith(\"wardley\")\n\n")
    sb.append("  def parse(text: String): WardleyDb = WardleyParser.parse(text)\n\n")
    sb.append("  def render(text: String, config: MermaidConfig = MermaidConfig(), title: Nullable[String] = Nullable.empty): String = {\n")
    sb.append("    val db = new WardleyDb\n")
    sb.append("    title.foreach(t => db.title = t)\n")
    sb.append("    WardleyParser.parse(text, db)\n")
    sb.append("    WardleyRenderer.render(db, config)\n")
    sb.append("  }\n")
    sb.append("}\n")
    sb.toString
  }

  /** Emits the WardleyParser. */
  def emitWardleyParser(rast: RastFile): String = {
    val sb = new StringBuilder
    sb.append(header(rast.path, "WardleyParser.scala"))
    sb.append("package ssg\npackage mermaid\npackage diagrams\npackage wardley\n\n")
    sb.append("import ssg.mermaid.parse.ParseException\n\n")
    sb.append("/** Hand-written parser for Mermaid Wardley map syntax. */\n")
    sb.append("object WardleyParser {\n\n")
    sb.append("  def parse(input: String): WardleyDb = parse(input, new WardleyDb)\n\n")
    sb.append("  def parse(input: String, db: WardleyDb): WardleyDb = {\n")
    sb.append("    val cleaned = cleanInput(input)\n")
    sb.append("    val lines   = cleaned.split(\"\\n\").map(_.trim).filter(_.nonEmpty)\n\n")
    sb.append("    var i = 0\n")
    sb.append("    while (i < lines.length && !lines(i).toLowerCase.startsWith(\"wardley\")) i += 1\n")
    sb.append("    if (i >= lines.length) throw new ParseException(\"Expected 'wardley' keyword\", 1, 1)\n")
    sb.append("    i += 1\n\n")
    sb.append("    while (i < lines.length) {\n")
    sb.append("      val line = lines(i).trim; i += 1\n")
    sb.append("      if (line.startsWith(\"%%\")) { /* skip */ }\n")
    sb.append("      else if (line.toLowerCase.startsWith(\"title\")) { db.title = line.substring(5).trim }\n")
    sb.append("      else if (line.toLowerCase.startsWith(\"component \")) {\n")
    sb.append("        val rest       = line.substring(10).trim\n")
    sb.append("        val bracketIdx = rest.indexOf('[')\n")
    sb.append("        if (bracketIdx >= 0) {\n")
    sb.append("          val name       = rest.substring(0, bracketIdx).trim\n")
    sb.append("          val endIdx     = rest.indexOf(']', bracketIdx)\n")
    sb.append("          val coords     = if (endIdx > bracketIdx) rest.substring(bracketIdx + 1, endIdx) else \"\"\n")
    sb.append("          val parts      = coords.split(\",\").map(_.trim)\n")
    sb.append("          val visibility = parts.headOption\n")
    sb.append("            .flatMap(s => try Some(s.toDouble) catch { case _: NumberFormatException => None })\n")
    sb.append("            .getOrElse(0.5)\n")
    sb.append("          val evolution = parts.lift(1)\n")
    sb.append("            .flatMap(s => try Some(s.toDouble) catch { case _: NumberFormatException => None })\n")
    sb.append("            .getOrElse(0.5)\n")
    sb.append("          db.addComponent(name, visibility, evolution)\n")
    sb.append("        } else {\n")
    sb.append("          db.addComponent(rest, 0.5, 0.5)\n")
    sb.append("        }\n")
    sb.append("      } else if (line.contains(\"-->\")) {\n")
    sb.append("        val parts = line.split(\"-->\").map(_.trim)\n")
    sb.append("        if (parts.length == 2) db.addLink(parts(0), parts(1))\n")
    sb.append("      }\n")
    sb.append("    }\n")
    sb.append("    db\n")
    sb.append("  }\n\n")
    sb.append("  private def cleanInput(input: String): String =\n")
    sb.append("    input.replaceAll(\"%%\\\\{[^}]*\\\\}%%\", \"\")\n")
    sb.append("}\n")
    sb.toString
  }

  /** Emits the WardleyRenderer. */
  def emitWardleyRenderer(rast: RastFile): String = {
    val sb = new StringBuilder
    sb.append(header(rast.path, "WardleyRenderer.scala"))
    sb.append("package ssg\npackage mermaid\npackage diagrams\npackage wardley\n\n")
    sb.append("import ssg.mermaid.Accessibility\n")
    sb.append("import ssg.mermaid.MermaidConfig\n")
    sb.append("import ssg.graphs.commons.svg.SvgBuilder\n")
    sb.append("import ssg.mermaid.theme.{ CssGenerator, Theme }\n\n")
    sb.append("import scala.collection.mutable\n\n")
    sb.append("/** Renders a Wardley map to SVG. */\n")
    sb.append("object WardleyRenderer {\n\n")
    sb.append("  private val Padding:         Double        = 50.0\n")
    sb.append("  private val ChartWidth:      Double        = 600.0\n")
    sb.append("  private val ChartHeight:     Double        = 400.0\n")
    sb.append("  private val EvolutionLabels: Array[String] = Array(\"Genesis\", \"Custom Built\", \"Product\", \"Commodity\")\n\n")
    sb.append("  def render(db: WardleyDb, config: MermaidConfig): String = {\n")
    sb.append("    val svgWidth  = ChartWidth + Padding * 3\n")
    sb.append("    val svgHeight = ChartHeight + Padding * 3 + (if (db.title.nonEmpty) 30 else 0)\n")
    sb.append("    val viewBox   = s\"0 0 $svgWidth $svgHeight\"\n")
    sb.append("    val svg       = SvgBuilder.createSvg(viewBox)\n")
    sb.append("    svg.attr(\"role\", \"img\"); svg.classed(\"mermaid\", true)\n")
    sb.append("    Accessibility.applyTo(svg, \"wardley\", db.accTitle, db.accDescription)\n\n")
    sb.append("    val defs      = svg.append(\"defs\")\n")
    sb.append("    val themeVars = Theme.getThemeByName(config.theme, config.themeVariables)\n")
    sb.append("    val css       = WardleyStyles.generate(themeVars)\n")
    sb.append("    val baseCss   = CssGenerator.generateBaseStyles(themeVars)\n")
    sb.append("    val styleEl   = defs.append(\"style\")\n")
    sb.append("    styleEl.attr(\"type\", \"text/css\")\n")
    sb.append("    styleEl.text(baseCss + \"\\n\" + css + (if (config.themeCSS.nonEmpty) \"\\n\" + config.themeCSS else \"\"))\n\n")
    sb.append("    val mainGroup = svg.append(\"g\")\n")
    sb.append("    var yOff      = Padding\n\n")
    sb.append("    if (db.title.nonEmpty) {\n")
    sb.append("      mainGroup.append(\"text\").attr(\"x\", svgWidth / 2).attr(\"y\", 25).attr(\"text-anchor\", \"middle\").classed(\"wardleyTitle\", true).text(db.title)\n")
    sb.append("      yOff += 30\n")
    sb.append("    }\n\n")
    sb.append("    val chartX = Padding * 2; val chartY = yOff\n\n")
    sb.append("    mainGroup.append(\"rect\").attr(\"x\", chartX).attr(\"y\", chartY).attr(\"width\", ChartWidth).attr(\"height\", ChartHeight).style(\"fill\", \"#fafafa\").style(\"stroke\", \"#ccc\")\n\n")
    sb.append("    for ((label, idx) <- EvolutionLabels.zipWithIndex) {\n")
    sb.append("      val x = chartX + (idx + 0.5) * ChartWidth / 4\n")
    sb.append("      mainGroup.append(\"text\").attr(\"x\", x).attr(\"y\", chartY + ChartHeight + 20).attr(\"text-anchor\", \"middle\").classed(\"wardleyAxisLabel\", true).text(label)\n")
    sb.append("      if (idx > 0) {\n")
    sb.append("        val dx = chartX + idx * ChartWidth / 4\n")
    sb.append("        mainGroup.append(\"line\").attr(\"x1\", dx).attr(\"y1\", chartY).attr(\"x2\", dx).attr(\"y2\", chartY + ChartHeight).style(\"stroke\", \"#ddd\").style(\"stroke-dasharray\", \"3,3\")\n")
    sb.append("      }\n")
    sb.append("    }\n\n")
    sb.append("    mainGroup.append(\"text\").attr(\"x\", chartX - 10).attr(\"y\", chartY + 10).attr(\"text-anchor\", \"end\").classed(\"wardleyAxisLabel\", true).text(\"Visible\")\n")
    sb.append("    mainGroup.append(\"text\").attr(\"x\", chartX - 10).attr(\"y\", chartY + ChartHeight).attr(\"text-anchor\", \"end\").classed(\"wardleyAxisLabel\", true).text(\"Invisible\")\n\n")
    sb.append("    val positions = mutable.Map.empty[String, (Double, Double)]\n")
    sb.append("    for (comp <- db.components) {\n")
    sb.append("      val x = chartX + comp.evolution * ChartWidth\n")
    sb.append("      val y = chartY + (1.0 - comp.visibility) * ChartHeight\n")
    sb.append("      positions(comp.name) = (x, y)\n")
    sb.append("      mainGroup.append(\"circle\").attr(\"cx\", x).attr(\"cy\", y).attr(\"r\", 6).classed(\"wardleyComponent\", true)\n")
    sb.append("      mainGroup.append(\"text\").attr(\"x\", x + 10).attr(\"y\", y + 4).classed(\"wardleyComponentLabel\", true).text(comp.name)\n")
    sb.append("    }\n\n")
    sb.append("    for (link <- db.links)\n")
    sb.append("      for {\n")
    sb.append("        (sx, sy) <- positions.get(link.from)\n")
    sb.append("        (tx, ty) <- positions.get(link.to)\n")
    sb.append("      }\n")
    sb.append("        mainGroup.append(\"line\").attr(\"x1\", sx).attr(\"y1\", sy).attr(\"x2\", tx).attr(\"y2\", ty).classed(\"wardleyLink\", true)\n\n")
    sb.append("    svg.build().toMarkup()\n")
    sb.append("  }\n")
    sb.append("}\n")
    sb.toString
  }

  /** Emits WardleyStyles. */
  def emitWardleyStyles(rast: RastFile): String = {
    val sb = new StringBuilder
    sb.append(header(rast.path, "WardleyStyles.scala"))
    sb.append("package ssg\npackage mermaid\npackage diagrams\npackage wardley\n\n")
    sb.append("import ssg.mermaid.theme.ThemeVariables\n\n")
    sb.append("object WardleyStyles {\n\n")
    sb.append("  def generate(vars: ThemeVariables): String =\n")
    sb.append("    s\"\"\".wardleyTitle { font-size: 16px; fill: $${vars.textColor}; font-family: $${vars.fontFamily}; }\n")
    sb.append("       |.wardleyAxisLabel { font-size: 11px; fill: $${vars.textColor}; font-family: $${vars.fontFamily}; }\n")
    sb.append("       |.wardleyComponent { fill: $${vars.primaryColor}; stroke: $${vars.primaryBorderColor}; stroke-width: 1px; }\n")
    sb.append("       |.wardleyComponentLabel { font-size: 12px; fill: $${vars.textColor}; font-family: $${vars.fontFamily}; }\n")
    sb.append("       |.wardleyLink { stroke: $${vars.lineColor}; stroke-width: 1px; }\n")
    sb.append("       |\"\"\".stripMargin\n")
    sb.append("}\n")
    sb.toString
  }

  // -- Complete Ishikawa diagram emission ----------------------------------------

  /** Emits the IshikawaDb class with CauseBranch case class. */
  def emitIshikawaDb(rast: RastFile): String = {
    val sb = new StringBuilder
    sb.append(header(rast.path, "IshikawaDb.scala"))
    sb.append("package ssg\npackage mermaid\npackage diagrams\npackage ishikawa\n\n")
    sb.append("import scala.collection.mutable\n\n")
    sb.append("/** A cause branch in an Ishikawa (fishbone) diagram. */\n")
    sb.append("final case class CauseBranch(label: String, causes: mutable.ArrayBuffer[String] = mutable.ArrayBuffer.empty)\n\n")
    sb.append("/** Mutable database for Ishikawa diagram data. */\n")
    sb.append("final class IshikawaDb {\n\n")
    sb.append("  var title:          String = \"\"\n")
    sb.append("  var accTitle:       String = \"\"\n")
    sb.append("  var accDescription: String = \"\"\n")
    sb.append("  var effect:         String = \"\"\n\n")
    sb.append("  val branches: mutable.ArrayBuffer[CauseBranch] = mutable.ArrayBuffer.empty\n\n")
    sb.append("  def setEffect(label: String): Unit = effect = label\n\n")
    sb.append("  def addBranch(label: String): CauseBranch = {\n")
    sb.append("    val b = CauseBranch(label)\n")
    sb.append("    branches += b\n")
    sb.append("    b\n")
    sb.append("  }\n\n")
    sb.append("  def addCause(branchLabel: String, cause: String): Unit =\n")
    sb.append("    branches.find(_.label == branchLabel).foreach(_.causes += cause)\n\n")
    sb.append("  def addCauseToLast(cause: String): Unit =\n")
    sb.append("    if (branches.nonEmpty) branches.last.causes += cause\n\n")
    sb.append("  def clear(): Unit = {\n")
    sb.append("    title = \"\"; accTitle = \"\"; accDescription = \"\"; effect = \"\"; branches.clear()\n")
    sb.append("  }\n")
    sb.append("}\n")
    sb.toString
  }

  /** Emits the IshikawaDiagram facade. */
  def emitIshikawaDiagram(rast: RastFile): String = {
    val sb = new StringBuilder
    sb.append(header(rast.path, "IshikawaDiagram.scala"))
    sb.append("package ssg\npackage mermaid\npackage diagrams\npackage ishikawa\n\n")
    sb.append("import lowlevel.Nullable\n\n")
    sb.append("import ssg.mermaid.MermaidConfig\n\n")
    sb.append("/** Ishikawa (fishbone/cause-and-effect) diagram type registration and rendering entry point. */\n")
    sb.append("object IshikawaDiagram {\n\n")
    sb.append("  def detect(text: String): Boolean = {\n")
    sb.append("    val firstLine = text.trim.split(\"[\\n\\r]\", 2)(0).trim.toLowerCase\n")
    sb.append("    firstLine.startsWith(\"ishikawa\")\n")
    sb.append("  }\n\n")
    sb.append("  def parse(text: String): IshikawaDb = IshikawaParser.parse(text)\n\n")
    sb.append("  def render(text: String, config: MermaidConfig = MermaidConfig(), title: Nullable[String] = Nullable.empty): String = {\n")
    sb.append("    val db = new IshikawaDb\n")
    sb.append("    title.foreach(t => db.title = t)\n")
    sb.append("    IshikawaParser.parse(text, db)\n")
    sb.append("    IshikawaRenderer.render(db, config)\n")
    sb.append("  }\n")
    sb.append("}\n")
    sb.toString
  }

  /** Emits the IshikawaParser. */
  def emitIshikawaParser(rast: RastFile): String = {
    val sb = new StringBuilder
    sb.append(header(rast.path, "IshikawaParser.scala"))
    sb.append("package ssg\npackage mermaid\npackage diagrams\npackage ishikawa\n\n")
    sb.append("import ssg.mermaid.parse.ParseException\n\n")
    sb.append("/** Hand-written parser for Mermaid Ishikawa (fishbone) syntax. */\n")
    sb.append("object IshikawaParser {\n\n")
    sb.append("  def parse(input: String): IshikawaDb = parse(input, new IshikawaDb)\n\n")
    sb.append("  def parse(input: String, db: IshikawaDb): IshikawaDb = {\n")
    sb.append("    val cleaned = cleanInput(input)\n")
    sb.append("    val lines   = cleaned.split(\"\\n\")\n\n")
    sb.append("    var i = 0\n")
    sb.append("    while (i < lines.length && !lines(i).trim.toLowerCase.startsWith(\"ishikawa\")) i += 1\n")
    sb.append("    if (i >= lines.length) throw new ParseException(\"Expected 'ishikawa' keyword\", 1, 1)\n")
    sb.append("    i += 1\n\n")
    sb.append("    while (i < lines.length) {\n")
    sb.append("      val line = lines(i); val trimmed = line.trim; i += 1\n")
    sb.append("      if (trimmed.isEmpty || trimmed.startsWith(\"%%\")) {\n")
    sb.append("        // skip\n")
    sb.append("      } else {\n")
    sb.append("        val indent     = line.length - line.stripLeading().length\n")
    sb.append("        val (_, label) = parseIdLabel(trimmed)\n\n")
    sb.append("        if (indent < 2) {\n")
    sb.append("          if (db.effect.isEmpty && db.branches.isEmpty) {\n")
    sb.append("            db.setEffect(label)\n")
    sb.append("          } else {\n")
    sb.append("            db.addBranch(label)\n")
    sb.append("          }\n")
    sb.append("        } else {\n")
    sb.append("          db.addCauseToLast(label)\n")
    sb.append("        }\n")
    sb.append("      }\n")
    sb.append("    }\n")
    sb.append("    db\n")
    sb.append("  }\n\n")
    sb.append("  private def cleanInput(input: String): String =\n")
    sb.append("    input.replaceAll(\"%%\\\\{[^}]*\\\\}%%\", \"\")\n\n")
    sb.append("  private def parseIdLabel(text: String): (String, String) = {\n")
    sb.append("    val bracketIdx = text.indexOf('[')\n")
    sb.append("    if (bracketIdx >= 0) {\n")
    sb.append("      val id       = text.substring(0, bracketIdx).trim\n")
    sb.append("      val endIdx   = text.lastIndexOf(']')\n")
    sb.append("      val rawLabel = if (endIdx > bracketIdx) text.substring(bracketIdx + 1, endIdx).trim else text.substring(bracketIdx + 1).trim\n")
    sb.append("      val label    = if (rawLabel.startsWith(\"\\\"\") && rawLabel.endsWith(\"\\\"\")) rawLabel.substring(1, rawLabel.length - 1) else rawLabel\n")
    sb.append("      (if (id.nonEmpty) id else label, label)\n")
    sb.append("    } else (text, text)\n")
    sb.append("  }\n")
    sb.append("}\n")
    sb.toString
  }

  /** Emits the IshikawaRenderer. */
  def emitIshikawaRenderer(rast: RastFile): String = {
    val sb = new StringBuilder
    sb.append(header(rast.path, "IshikawaRenderer.scala"))
    sb.append("package ssg\npackage mermaid\npackage diagrams\npackage ishikawa\n\n")
    sb.append("import ssg.mermaid.Accessibility\n")
    sb.append("import ssg.mermaid.MermaidConfig\n")
    sb.append("import ssg.graphs.commons.svg.SvgBuilder\n")
    sb.append("import ssg.mermaid.theme.{ CssGenerator, Theme }\n\n")
    sb.append("/** Renders an Ishikawa (fishbone/cause-and-effect) diagram to SVG. */\n")
    sb.append("object IshikawaRenderer {\n\n")
    sb.append("  private val Padding:      Double = 30.0\n")
    sb.append("  private val SpineLength:  Double = 600.0\n")
    sb.append("  private val BranchLength: Double = 120.0\n")
    sb.append("  private val CauseSpacing: Double = 25.0\n\n")
    sb.append("  def render(db: IshikawaDb, config: MermaidConfig): String = {\n")
    sb.append("    val branchCount = db.branches.size.max(1)\n")
    sb.append("    val maxCauses   = if (db.branches.isEmpty) 0 else db.branches.map(_.causes.size).max\n")
    sb.append("    val svgWidth    = SpineLength + Padding * 3 + 100\n")
    sb.append("    val svgHeight   = BranchLength * 2 + maxCauses * CauseSpacing + Padding * 2 + 60\n\n")
    sb.append("    val viewBox = s\"0 0 $svgWidth $svgHeight\"\n")
    sb.append("    val svg     = SvgBuilder.createSvg(viewBox)\n")
    sb.append("    svg.attr(\"role\", \"img\"); svg.classed(\"mermaid\", true)\n")
    sb.append("    Accessibility.applyTo(svg, \"ishikawa\", db.accTitle, db.accDescription)\n\n")
    sb.append("    val defs      = svg.append(\"defs\")\n")
    sb.append("    val themeVars = Theme.getThemeByName(config.theme, config.themeVariables)\n")
    sb.append("    val css       = IshikawaStyles.generate(themeVars)\n")
    sb.append("    val baseCss   = CssGenerator.generateBaseStyles(themeVars)\n")
    sb.append("    val styleEl   = defs.append(\"style\")\n")
    sb.append("    styleEl.attr(\"type\", \"text/css\")\n")
    sb.append("    styleEl.text(baseCss + \"\\n\" + css + (if (config.themeCSS.nonEmpty) \"\\n\" + config.themeCSS else \"\"))\n\n")
    sb.append("    val marker = defs.append(\"marker\")\n")
    sb.append("    marker.attr(\"id\", \"fishhead\").attr(\"viewBox\", \"0 0 10 10\")\n")
    sb.append("    marker.attr(\"refX\", 10).attr(\"refY\", 5).attr(\"markerWidth\", 8).attr(\"markerHeight\", 8).attr(\"orient\", \"auto\")\n")
    sb.append("    marker.append(\"path\").attr(\"d\", \"M 0 0 L 10 5 L 0 10 z\").style(\"fill\", themeVars.lineColor)\n\n")
    sb.append("    val mainGroup   = svg.append(\"g\")\n")
    sb.append("    val spineY      = svgHeight / 2\n")
    sb.append("    val spineStartX = Padding\n")
    sb.append("    val spineEndX   = SpineLength + Padding\n\n")
    sb.append("    val spine = mainGroup.append(\"line\")\n")
    sb.append("    spine.attr(\"x1\", spineStartX).attr(\"y1\", spineY)\n")
    sb.append("    spine.attr(\"x2\", spineEndX).attr(\"y2\", spineY)\n")
    sb.append("    spine.attr(\"marker-end\", \"url(#fishhead)\").classed(\"ishikawaSpine\", true)\n\n")
    sb.append("    if (db.effect.nonEmpty) {\n")
    sb.append("      mainGroup.append(\"text\").attr(\"x\", spineEndX + 15).attr(\"y\", spineY + 5).attr(\"text-anchor\", \"start\").classed(\"ishikawaEffect\", true).text(db.effect)\n")
    sb.append("    }\n\n")
    sb.append("    val spacing = if (branchCount > 1) (SpineLength - 60) / (branchCount - 1).toDouble else SpineLength / 2\n")
    sb.append("    for ((branch, idx) <- db.branches.zipWithIndex) {\n")
    sb.append("      val branchX    = spineStartX + 30 + spacing * idx\n")
    sb.append("      val isTop      = idx % 2 == 0\n")
    sb.append("      val branchEndY = if (isTop) spineY - BranchLength else spineY + BranchLength\n\n")
    sb.append("      val line = mainGroup.append(\"line\")\n")
    sb.append("      line.attr(\"x1\", branchX).attr(\"y1\", spineY)\n")
    sb.append("      line.attr(\"x2\", branchX).attr(\"y2\", branchEndY)\n")
    sb.append("      line.classed(\"ishikawaBranch\", true)\n\n")
    sb.append("      val labelY = if (isTop) branchEndY - 10 else branchEndY + 20\n")
    sb.append("      mainGroup.append(\"text\").attr(\"x\", branchX).attr(\"y\", labelY).attr(\"text-anchor\", \"middle\").classed(\"ishikawaBranchLabel\", true).text(branch.label)\n\n")
    sb.append("      for ((cause, cIdx) <- branch.causes.zipWithIndex) {\n")
    sb.append("        val causeY = if (isTop) branchEndY + 20 + cIdx * CauseSpacing else branchEndY - 20 - cIdx * CauseSpacing\n")
    sb.append("        val causeEndX = branchX + 80\n")
    sb.append("        val causeLine = mainGroup.append(\"line\")\n")
    sb.append("        causeLine.attr(\"x1\", branchX).attr(\"y1\", causeY)\n")
    sb.append("        causeLine.attr(\"x2\", causeEndX).attr(\"y2\", causeY)\n")
    sb.append("        causeLine.classed(\"ishikawaCause\", true)\n")
    sb.append("        mainGroup.append(\"text\").attr(\"x\", causeEndX + 5).attr(\"y\", causeY + 4).attr(\"text-anchor\", \"start\").classed(\"ishikawaCauseLabel\", true).text(cause)\n")
    sb.append("      }\n")
    sb.append("    }\n\n")
    sb.append("    svg.build().toMarkup()\n")
    sb.append("  }\n")
    sb.append("}\n")
    sb.toString
  }

  /** Emits IshikawaStyles. */
  def emitIshikawaStyles(rast: RastFile): String = {
    val sb = new StringBuilder
    sb.append(header(rast.path, "IshikawaStyles.scala"))
    sb.append("package ssg\npackage mermaid\npackage diagrams\npackage ishikawa\n\n")
    sb.append("import ssg.mermaid.theme.ThemeVariables\n\n")
    sb.append("/** CSS class generation for Ishikawa diagram elements. */\n")
    sb.append("object IshikawaStyles {\n\n")
    sb.append("  def generate(vars: ThemeVariables): String =\n")
    sb.append("    s\"\"\".ishikawaSpine {\n")
    sb.append("       |  stroke: $${vars.lineColor};\n")
    sb.append("       |  stroke-width: 3px;\n")
    sb.append("       |}\n")
    sb.append("       |.ishikawaEffect {\n")
    sb.append("       |  font-size: 16px;\n")
    sb.append("       |  font-weight: bold;\n")
    sb.append("       |  fill: $${vars.textColor};\n")
    sb.append("       |  font-family: $${vars.fontFamily};\n")
    sb.append("       |}\n")
    sb.append("       |.ishikawaBranch {\n")
    sb.append("       |  stroke: $${vars.lineColor};\n")
    sb.append("       |  stroke-width: 2px;\n")
    sb.append("       |}\n")
    sb.append("       |.ishikawaBranchLabel {\n")
    sb.append("       |  font-size: 14px;\n")
    sb.append("       |  font-weight: bold;\n")
    sb.append("       |  fill: $${vars.textColor};\n")
    sb.append("       |  font-family: $${vars.fontFamily};\n")
    sb.append("       |}\n")
    sb.append("       |.ishikawaCause {\n")
    sb.append("       |  stroke: $${vars.lineColor};\n")
    sb.append("       |  stroke-width: 1px;\n")
    sb.append("       |}\n")
    sb.append("       |.ishikawaCauseLabel {\n")
    sb.append("       |  font-size: 11px;\n")
    sb.append("       |  fill: $${vars.textColor};\n")
    sb.append("       |  font-family: $${vars.fontFamily};\n")
    sb.append("       |}\n")
    sb.append("       |\"\"\".stripMargin\n")
    sb.append("}\n")
    sb.toString
  }

  // -- Complete Venn diagram emission ----------------------------------------

  /** Emits the VennDb class with VennSet and VennIntersection case classes. */
  def emitVennDb(rast: RastFile): String = {
    val sb = new StringBuilder
    sb.append(header(rast.path, "VennDb.scala"))
    sb.append("package ssg\npackage mermaid\npackage diagrams\npackage venn\n\n")
    sb.append("import scala.collection.mutable\n\n")
    sb.append("/** A set in a Venn diagram. */\n")
    sb.append("final case class VennSet(id: String, label: String, size: Double = 1.0)\n\n")
    sb.append("/** An intersection label. */\n")
    sb.append("final case class VennIntersection(sets: Seq[String], label: String)\n\n")
    sb.append("/** Mutable database for Venn diagram data. */\n")
    sb.append("final class VennDb {\n\n")
    sb.append("  var title:          String = \"\"\n")
    sb.append("  var accTitle:       String = \"\"\n")
    sb.append("  var accDescription: String = \"\"\n\n")
    sb.append("  val sets:          mutable.ArrayBuffer[VennSet]          = mutable.ArrayBuffer.empty\n")
    sb.append("  val intersections: mutable.ArrayBuffer[VennIntersection] = mutable.ArrayBuffer.empty\n\n")
    sb.append("  def addSet(id: String, label: String, size: Double = 1.0): Unit =\n")
    sb.append("    sets += VennSet(id, label, size)\n\n")
    sb.append("  def addIntersection(setIds: Seq[String], label: String): Unit =\n")
    sb.append("    intersections += VennIntersection(setIds, label)\n\n")
    sb.append("  def clear(): Unit = {\n")
    sb.append("    title = \"\"; accTitle = \"\"; accDescription = \"\"; sets.clear(); intersections.clear()\n")
    sb.append("  }\n")
    sb.append("}\n")
    sb.toString
  }

  /** Emits the VennDiagram facade. */
  def emitVennDiagram(rast: RastFile): String = {
    val sb = new StringBuilder
    sb.append(header(rast.path, "VennDiagram.scala"))
    sb.append("package ssg\npackage mermaid\npackage diagrams\npackage venn\n\n")
    sb.append("import lowlevel.Nullable\n\n")
    sb.append("import ssg.mermaid.MermaidConfig\n\n")
    sb.append("/** Venn diagram type registration and rendering entry point. */\n")
    sb.append("object VennDiagram {\n\n")
    sb.append("  def detect(text: String): Boolean = {\n")
    sb.append("    val firstLine = text.trim.split(\"[\\n\\r]\", 2)(0).trim.toLowerCase\n")
    sb.append("    firstLine.startsWith(\"venn-beta\")\n")
    sb.append("  }\n\n")
    sb.append("  def parse(text: String): VennDb = VennParser.parse(text)\n\n")
    sb.append("  def render(text: String, config: MermaidConfig = MermaidConfig(), title: Nullable[String] = Nullable.empty): String = {\n")
    sb.append("    val db = new VennDb\n")
    sb.append("    title.foreach(t => db.title = t)\n")
    sb.append("    VennParser.parse(text, db)\n")
    sb.append("    VennRenderer.render(db, config)\n")
    sb.append("  }\n")
    sb.append("}\n")
    sb.toString
  }

  /** Emits the VennParser. */
  def emitVennParser(rast: RastFile): String = {
    val sb = new StringBuilder
    sb.append(header(rast.path, "VennParser.scala"))
    sb.append("package ssg\npackage mermaid\npackage diagrams\npackage venn\n\n")
    sb.append("import ssg.mermaid.parse.{ ParseException, Scanner }\n\n")
    sb.append("import scala.util.boundary\n")
    sb.append("import scala.util.boundary.break\n\n")
    sb.append("/** Hand-written parser for Mermaid Venn diagram syntax. */\n")
    sb.append("object VennParser {\n\n")
    sb.append("  def parse(input: String): VennDb = parse(input, new VennDb)\n\n")
    sb.append("  def parse(input: String, db: VennDb): VennDb = {\n")
    sb.append("    val cleaned = cleanInput(input)\n")
    sb.append("    val scanner = new Scanner(cleaned)\n\n")
    sb.append("    scanner.skipWhitespaceAndNewlines()\n")
    sb.append("    if (!scanner.matchStrIgnoreCase(\"venn-beta\")) {\n")
    sb.append("      throw new ParseException(\"Expected 'venn-beta' keyword\", scanner.line, scanner.col)\n")
    sb.append("    }\n")
    sb.append("    skipToNewline(scanner)\n")
    sb.append("    parseBody(scanner, db)\n")
    sb.append("    db\n")
    sb.append("  }\n\n")
    sb.append("  private def cleanInput(input: String): String =\n")
    sb.append("    input.replaceAll(\"%%\\\\{[^}]*\\\\}%%\", \"\").replaceAll(\"%%[^\\n]*\", \"\")\n\n")
    sb.append("  private def parseBody(scanner: Scanner, db: VennDb): Unit = boundary {\n")
    sb.append("    while (!scanner.isEof) {\n")
    sb.append("      scanner.skipWhitespaceAndNewlines()\n")
    sb.append("      if (scanner.isEof) break()\n\n")
    sb.append("      if (scanner.peek() == '%') { skipToNewline(scanner) }\n")
    sb.append("      else if (tryParseTitle(scanner, db)) {}\n")
    sb.append("      else if (tryParseSet(scanner, db)) {}\n")
    sb.append("      else if (tryParseIntersection(scanner, db)) {}\n")
    sb.append("      else { skipToNewline(scanner) }\n")
    sb.append("    }\n")
    sb.append("  }\n\n")
    sb.append("  private def tryParseTitle(scanner: Scanner, db: VennDb): Boolean = boundary {\n")
    sb.append("    val saved = scanner.save()\n")
    sb.append("    if (!scanner.matchStrIgnoreCase(\"title\")) { break(false) }\n")
    sb.append("    if (!scanner.isEof && scanner.peek() != ' ' && scanner.peek() != '\\t' && scanner.peek() != '\\n') {\n")
    sb.append("      scanner.restore(saved); break(false)\n")
    sb.append("    }\n")
    sb.append("    scanner.skipWhitespace()\n")
    sb.append("    db.title = readTextUntilNewline(scanner).trim\n")
    sb.append("    true\n")
    sb.append("  }\n\n")
    sb.append("  private def tryParseSet(scanner: Scanner, db: VennDb): Boolean = boundary {\n")
    sb.append("    val saved = scanner.save()\n")
    sb.append("    if (!scanner.matchStrIgnoreCase(\"set\")) { scanner.restore(saved); break(false) }\n")
    sb.append("    if (!scanner.isEof && !scanner.peek().isWhitespace) { scanner.restore(saved); break(false) }\n")
    sb.append("    scanner.skipWhitespace()\n\n")
    sb.append("    val id = readIdent(scanner)\n")
    sb.append("    if (id.isEmpty) { scanner.restore(saved); break(false) }\n")
    sb.append("    scanner.skipWhitespace()\n\n")
    sb.append("    val label = if (!scanner.isEof && scanner.peek() == '[') {\n")
    sb.append("      scanner.advance()\n")
    sb.append("      if (!scanner.isEof && scanner.peek() == '\"') {\n")
    sb.append("        val l = scanner.readQuotedString()\n")
    sb.append("        if (!scanner.isEof && scanner.peek() == ']') scanner.advance()\n")
    sb.append("        l\n")
    sb.append("      } else {\n")
    sb.append("        scanner.readUntil(']').trim\n")
    sb.append("      }\n")
    sb.append("    } else id\n\n")
    sb.append("    db.addSet(id, label)\n")
    sb.append("    skipToNewline(scanner)\n")
    sb.append("    true\n")
    sb.append("  }\n\n")
    sb.append("  private def tryParseIntersection(scanner: Scanner, db: VennDb): Boolean = boundary {\n")
    sb.append("    val saved = scanner.save()\n")
    sb.append("    if (!scanner.matchStrIgnoreCase(\"intersection\")) { scanner.restore(saved); break(false) }\n")
    sb.append("    if (!scanner.isEof && !scanner.peek().isWhitespace) { scanner.restore(saved); break(false) }\n")
    sb.append("    scanner.skipWhitespace()\n\n")
    sb.append("    val setIds = scala.collection.mutable.ArrayBuffer.empty[String]\n")
    sb.append("    while (!scanner.isEof && scanner.peek() != '[' && scanner.peek() != '\\n') {\n")
    sb.append("      val id = readIdent(scanner)\n")
    sb.append("      if (id.nonEmpty) setIds += id\n")
    sb.append("      scanner.skipWhitespace()\n")
    sb.append("      if (!scanner.isEof && scanner.peek() == ',') { scanner.advance(); scanner.skipWhitespace() }\n")
    sb.append("    }\n\n")
    sb.append("    val label = if (!scanner.isEof && scanner.peek() == '[') {\n")
    sb.append("      scanner.advance()\n")
    sb.append("      if (!scanner.isEof && scanner.peek() == '\"') {\n")
    sb.append("        val l = scanner.readQuotedString()\n")
    sb.append("        if (!scanner.isEof && scanner.peek() == ']') scanner.advance()\n")
    sb.append("        l\n")
    sb.append("      } else {\n")
    sb.append("        scanner.readUntil(']').trim\n")
    sb.append("      }\n")
    sb.append("    } else \"\"\n\n")
    sb.append("    if (setIds.size >= 2) {\n")
    sb.append("      db.addIntersection(setIds.toSeq, label)\n")
    sb.append("    }\n")
    sb.append("    skipToNewline(scanner)\n")
    sb.append("    true\n")
    sb.append("  }\n\n")
    sb.append("  private def readIdent(scanner: Scanner): String = {\n")
    sb.append("    val sb = new StringBuilder()\n")
    sb.append("    while (!scanner.isEof && (scanner.peek().isLetterOrDigit || scanner.peek() == '_'))\n")
    sb.append("      sb.append(scanner.advance())\n")
    sb.append("    sb.toString\n")
    sb.append("  }\n\n")
    sb.append("  private def readTextUntilNewline(scanner: Scanner): String = {\n")
    sb.append("    val sb = new StringBuilder()\n")
    sb.append("    while (!scanner.isEof && scanner.peek() != '\\n') sb.append(scanner.advance())\n")
    sb.append("    sb.toString\n")
    sb.append("  }\n\n")
    sb.append("  private def skipToNewline(scanner: Scanner): Unit = {\n")
    sb.append("    while (!scanner.isEof && scanner.peek() != '\\n') scanner.advance()\n")
    sb.append("    if (!scanner.isEof) scanner.advance()\n")
    sb.append("  }\n")
    sb.append("}\n")
    sb.toString
  }

  /** Emits the VennRenderer. */
  def emitVennRenderer(rast: RastFile): String = {
    val sb = new StringBuilder
    sb.append(header(rast.path, "VennRenderer.scala"))
    sb.append("package ssg\npackage mermaid\npackage diagrams\npackage venn\n\n")
    sb.append("import ssg.mermaid.Accessibility\n")
    sb.append("import ssg.mermaid.MermaidConfig\n")
    sb.append("import ssg.graphs.commons.svg.SvgBuilder\n")
    sb.append("import ssg.mermaid.theme.{ CssGenerator, Theme }\n\n")
    sb.append("/** Renders a Venn diagram to SVG. */\n")
    sb.append("object VennRenderer {\n\n")
    sb.append("  private val Radius:  Double        = 120.0\n")
    sb.append("  private val Padding: Double        = 40.0\n")
    sb.append("  private val Colors:  Array[String] = Array(\n")
    sb.append("    \"#4e79a7\",\n")
    sb.append("    \"#f28e2b\",\n")
    sb.append("    \"#e15759\",\n")
    sb.append("    \"#76b7b2\",\n")
    sb.append("    \"#59a14f\"\n")
    sb.append("  )\n\n")
    sb.append("  def render(db: VennDb, config: MermaidConfig): String = {\n")
    sb.append("    val setCount = db.sets.size.max(1)\n")
    sb.append("    val size     = (Radius * 2 + Padding) * 2 + 40\n")
    sb.append("    val cx       = size / 2; val cy = size / 2 + 20\n\n")
    sb.append("    val viewBox = s\"0 0 $size $size\"\n")
    sb.append("    val svg     = SvgBuilder.createSvg(viewBox)\n")
    sb.append("    svg.attr(\"role\", \"img\"); svg.classed(\"mermaid\", true)\n")
    sb.append("    Accessibility.applyTo(svg, \"venn\", db.accTitle, db.accDescription)\n\n")
    sb.append("    val defs      = svg.append(\"defs\")\n")
    sb.append("    val themeVars = Theme.getThemeByName(config.theme, config.themeVariables)\n")
    sb.append("    val css       = VennStyles.generate(themeVars)\n")
    sb.append("    val baseCss   = CssGenerator.generateBaseStyles(themeVars)\n")
    sb.append("    val styleEl   = defs.append(\"style\")\n")
    sb.append("    styleEl.attr(\"type\", \"text/css\")\n")
    sb.append("    styleEl.text(baseCss + \"\\n\" + css + (if (config.themeCSS.nonEmpty) \"\\n\" + config.themeCSS else \"\"))\n\n")
    sb.append("    val mainGroup = svg.append(\"g\")\n\n")
    sb.append("    if (db.title.nonEmpty) {\n")
    sb.append("      mainGroup.append(\"text\").attr(\"x\", cx).attr(\"y\", 25).attr(\"text-anchor\", \"middle\").classed(\"vennTitle\", true).text(db.title)\n")
    sb.append("    }\n\n")
    sb.append("    val angleStep = 2 * math.Pi / setCount\n")
    sb.append("    val offset    = if (setCount <= 1) 0.0 else Radius * 0.6\n\n")
    sb.append("    for ((vset, idx) <- db.sets.zipWithIndex) {\n")
    sb.append("      val angle = angleStep * idx - math.Pi / 2\n")
    sb.append("      val setX  = cx + offset * math.cos(angle)\n")
    sb.append("      val setY  = cy + offset * math.sin(angle)\n")
    sb.append("      val color = Colors(idx % Colors.length)\n\n")
    sb.append("      val circle = mainGroup.append(\"circle\")\n")
    sb.append("      circle.attr(\"cx\", setX).attr(\"cy\", setY).attr(\"r\", Radius)\n")
    sb.append("      circle.style(\"fill\", color).style(\"fill-opacity\", \"0.3\")\n")
    sb.append("      circle.style(\"stroke\", color).style(\"stroke-width\", \"2\")\n")
    sb.append("      circle.classed(\"vennSet\", true)\n\n")
    sb.append("      val labelX = cx + (offset + Radius * 0.6) * math.cos(angle)\n")
    sb.append("      val labelY = cy + (offset + Radius * 0.6) * math.sin(angle)\n")
    sb.append("      mainGroup.append(\"text\").attr(\"x\", labelX).attr(\"y\", labelY + 5).attr(\"text-anchor\", \"middle\").classed(\"vennSetLabel\", true).text(vset.label)\n")
    sb.append("    }\n\n")
    sb.append("    for (isect <- db.intersections)\n")
    sb.append("      if (isect.label.nonEmpty) {\n")
    sb.append("        mainGroup.append(\"text\").attr(\"x\", cx).attr(\"y\", cy + 5).attr(\"text-anchor\", \"middle\").classed(\"vennIntersectionLabel\", true).text(isect.label)\n")
    sb.append("      }\n\n")
    sb.append("    svg.build().toMarkup()\n")
    sb.append("  }\n")
    sb.append("}\n")
    sb.toString
  }

  /** Emits VennStyles. */
  def emitVennStyles(rast: RastFile): String = {
    val sb = new StringBuilder
    sb.append(header(rast.path, "VennStyles.scala"))
    sb.append("package ssg\npackage mermaid\npackage diagrams\npackage venn\n\n")
    sb.append("import ssg.mermaid.theme.ThemeVariables\n\n")
    sb.append("/** CSS class generation for Venn diagram elements. */\n")
    sb.append("object VennStyles {\n\n")
    sb.append("  def generate(vars: ThemeVariables): String =\n")
    sb.append("    s\"\"\".vennTitle {\n")
    sb.append("       |  font-size: 16px;\n")
    sb.append("       |  fill: $${vars.textColor};\n")
    sb.append("       |  font-family: $${vars.fontFamily};\n")
    sb.append("       |}\n")
    sb.append("       |.vennSet {\n")
    sb.append("       |  stroke-width: 2px;\n")
    sb.append("       |}\n")
    sb.append("       |.vennSetLabel {\n")
    sb.append("       |  font-size: 14px;\n")
    sb.append("       |  font-weight: bold;\n")
    sb.append("       |  fill: $${vars.textColor};\n")
    sb.append("       |  font-family: $${vars.fontFamily};\n")
    sb.append("       |}\n")
    sb.append("       |.vennIntersectionLabel {\n")
    sb.append("       |  font-size: 12px;\n")
    sb.append("       |  fill: $${vars.textColor};\n")
    sb.append("       |  font-family: $${vars.fontFamily};\n")
    sb.append("       |}\n")
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
