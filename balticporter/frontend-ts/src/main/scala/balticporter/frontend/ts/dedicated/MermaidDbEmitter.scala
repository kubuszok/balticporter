package balticporter.frontend.ts.dedicated

import balticporter.frontend.ts.{RastFile, RastNode, RastType, RastValue}
import scala.collection.mutable

/** Genuine RAST-based emitter for Mermaid Db modules.
  *
  * Reads the Resolved AST and produces a Scala class matching the
  * ssg-mermaid hand-port conventions. The TS "Db" pattern is a module
  * with mutable state (`let` vars), arrow-function accessors/mutators,
  * and an exported `db` object that bundles them.
  *
  * Translation:
  *   - `let x: T = init`      -> `var x: T = init`
  *   - `const fn = () => expr` -> `def fn(): R = expr`
  *   - `data.field`            -> field (module-level state becomes `this`)
  *   - `structuredClone(d)`    -> copy the defaults (in `clear()`)
  *   - `commonClear()`         -> inline reset of common fields
  *   - exported `db` object    -> determines the class's public API
  *
  * DESIGN.md: this is a TS frontend emitter, not an engine phase.
  */
object MermaidDbEmitter {

  /** A module-level mutable variable (from a `let` declaration). */
  private case class StateVar(
      name: String,
      tsType: Option[String],
      scalaType: String,
      initExpr: String,
      defaultExpr: String, // for clear()
  )

  /** An arrow function (from a `const fn = (...) => ...` declaration). */
  private case class DbMethod(
      name: String,
      params: List[(String, String)], // (name, scalaType)
      returnType: String,
      body: List[String], // lines of Scala code
      isGetter: Boolean,
      isSetter: Boolean,
  )

  /** A class declared inside the module. */
  private case class InnerClass(
      name: String,
      fields: List[(String, String, Option[String])], // (name, type, default)
  )

  // -- commonDb functions that the Db class inherits --------------------------
  private val commonDbFunctions: Set[String] = Set(
    "setAccTitle",
    "getAccTitle",
    "setDiagramTitle",
    "getDiagramTitle",
    "getAccDescription",
    "setAccDescription",
    "commonClear",
  )

  // -- Public API -------------------------------------------------------------

  /** Emits a Scala Db class from a RAST file following the Mermaid Db pattern.
    *
    * @param rast        the parsed RAST file
    * @param className   e.g. "PacketDb"
    * @param pkg         e.g. "packet"
    * @param extraImports additional imports to add
    * @return the complete Scala source
    */
  def emitDb(
      rast: RastFile,
      className: String,
      pkg: String,
      extraImports: List[String] = Nil,
  ): String = {
    val analysis = analyzeModule(rast)
    val sb = new StringBuilder

    // Header
    sb.append(header(rast.path, s"$className.scala"))
    sb.append(s"package ssg\npackage mermaid\npackage diagrams\npackage $pkg\n\n")

    // Imports
    val needsMutable = analysis.stateVars.exists(v =>
      v.scalaType.contains("mutable.") || v.scalaType.contains("ArrayBuffer") || v.scalaType.contains("Map")
    ) || analysis.innerClasses.nonEmpty
    if (needsMutable) sb.append("import scala.collection.mutable\n")
    for (imp <- extraImports) sb.append(s"import $imp\n")
    if (needsMutable || extraImports.nonEmpty) sb.append("\n")

    // Inner classes (case classes for data types)
    for (cls <- analysis.innerClasses) {
      emitInnerClass(sb, cls)
      sb.append("\n")
    }

    // Db class
    sb.append(s"/** Mutable database for $pkg diagram data. */\n")
    sb.append(s"final class $className {\n\n")

    // State variables as fields
    for (v <- analysis.stateVars) {
      sb.append(s"  var ${padField(v.name, analysis.stateVars)}: ${v.scalaType} = ${v.initExpr}\n")
    }
    if (analysis.stateVars.nonEmpty) sb.append("\n")

    // Common fields (accTitle, accDescription, title) if not already declared
    val declaredNames = analysis.stateVars.map(_.name).toSet
    val commonFields = List(
      ("title", "String", "\"\""),
      ("accTitle", "String", "\"\""),
      ("accDescription", "String", "\"\""),
    )
    val missingCommon = commonFields.filterNot(f => declaredNames.contains(f._1))
    if (missingCommon.nonEmpty) {
      val allVars = analysis.stateVars ++ missingCommon.map(f => StateVar(f._1, None, f._2, f._3, f._3))
      for ((name, tpe, default) <- missingCommon) {
        sb.append(s"  var ${padField(name, allVars)}: $tpe = $default\n")
      }
      sb.append("\n")
    }

    // Methods
    for (m <- analysis.methods if !isCommonDbMethod(m.name) && m.name != "clear") {
      emitMethod(sb, m, analysis)
      sb.append("\n")
    }

    // Clear method
    emitClear(sb, analysis, missingCommon)

    sb.append("}\n")
    sb.toString
  }

  // -- Module analysis --------------------------------------------------------

  private case class ModuleAnalysis(
      stateVars: List[StateVar],
      methods: List[DbMethod],
      innerClasses: List[InnerClass],
      exportedNames: Set[String],
      defaultDataObjects: Map[String, Map[String, String]], // name -> field -> init
      moduleStateNames: Set[String], // names of `let` vars (module-level state)
  )

  private def analyzeModule(rast: RastFile): ModuleAnalysis = {
    val stateVars = mutable.ListBuffer.empty[StateVar]
    val methods = mutable.ListBuffer.empty[DbMethod]
    val innerClasses = mutable.ListBuffer.empty[InnerClass]
    val exportedNames = mutable.Set.empty[String]
    val defaultDataObjects = mutable.Map.empty[String, Map[String, String]]
    val moduleStateNames = mutable.Set.empty[String]

    for (node <- rast.nodes) {
      node.kind match {
        case "VariableStatement" =>
          val decls = extractVarDecls(node)
          for (d <- decls) {
            val name = nameOf(d)
            val flags = d.flags

            if (name == "db" || name == "default") {
              // Export object: extract the property names
              val objLit = findChild(d, "ObjectLiteralExpression")
                .orElse(findChild(d, "AsExpression").flatMap(a => findChild(a, "ObjectLiteralExpression")))
              objLit.foreach { obj =>
                for (prop <- obj.children) {
                  prop.kind match {
                    case "ShorthandPropertyAssignment" =>
                      val pName = nameOf(prop)
                      exportedNames += pName
                    case "PropertyAssignment" =>
                      val pName = nameOf(prop)
                      exportedNames += pName
                    case _ => ()
                  }
                }
              }
            } else if (flags.contains("let") || flags.contains("var")) {
              // Mutable state variable
              moduleStateNames += name
              val tsType = resolveTypeAnnotation(d, rast)
              val scalaType = tsTypeNodeToScala(d, rast)
              val initExpr = resolveInitExpr(d, rast, moduleStateNames.toSet)
              val defaultExpr = resolveDefaultExpr(d, rast, moduleStateNames.toSet)
              stateVars += StateVar(name, tsType, scalaType, initExpr, defaultExpr)
            } else if (flags.contains("const")) {
              // Check if arrow function
              val arrowFn = findChild(d, "ArrowFunction")
              val funcExpr = findChild(d, "FunctionExpression")
              val fn = arrowFn.orElse(funcExpr)

              fn match {
                case Some(f) =>
                  val method = analyzeFunction(name, f, rast, moduleStateNames.toSet)
                  methods += method
                case None =>
                  // Check if it's a default data object
                  val objLit = findChild(d, "ObjectLiteralExpression")
                    .orElse(findChild(d, "AsExpression").flatMap(a => findChild(a, "ObjectLiteralExpression")))
                  objLit.foreach { obj =>
                    val fields = mutable.Map.empty[String, String]
                    for (prop <- obj.children) {
                      if (prop.kind == "PropertyAssignment") {
                        val key = nameOf(prop)
                        val value = prop.children.lastOption.map(c => emitExprSimple(c, rast, moduleStateNames.toSet)).getOrElse("???")
                        fields(key) = value
                      } else if (prop.kind == "ShorthandPropertyAssignment") {
                        val key = nameOf(prop)
                        fields(key) = key
                      }
                    }
                    if (fields.nonEmpty) {
                      defaultDataObjects(name) = fields.toMap
                    }
                  }
              }
            }
          }

        case "ClassDeclaration" =>
          val cls = analyzeClass(node, rast)
          innerClasses += cls

        case "FunctionDeclaration" =>
          val name = nameOf(node)
          val params = node.children.filter(_.kind == "Parameter")
          val body = findChild(node, "Block")
          val returnType = inferReturnTypeFromNode(node, rast)
          val paramList = params.map { p =>
            val pName = nameOf(p)
            val pType = inferParamTypeFromNode(p, rast)
            (pName, pType)
          }.toList
          val bodyLines = body.map(b => emitBlockLines(b, rast, moduleStateNames.toSet)).getOrElse(Nil)
          methods += DbMethod(name, paramList, returnType, bodyLines, isGetter = false, isSetter = false)

        case "ExportAssignment" =>
          // export default { ... }
          val objLit = findChild(node, "ObjectLiteralExpression")
            .orElse(findChild(node, "AsExpression").flatMap(a => findChild(a, "ObjectLiteralExpression")))
          objLit.foreach { obj =>
            for (prop <- obj.children) {
              prop.kind match {
                case "ShorthandPropertyAssignment" =>
                  exportedNames += nameOf(prop)
                case "PropertyAssignment" =>
                  exportedNames += nameOf(prop)
                case _ => ()
              }
            }
          }

        case _ => ()
      }
    }

    ModuleAnalysis(
      stateVars.toList,
      methods.toList,
      innerClasses.toList,
      exportedNames.toSet,
      defaultDataObjects.toMap,
      moduleStateNames.toSet,
    )
  }

  // -- Function analysis ------------------------------------------------------

  private def analyzeFunction(
      name: String,
      fn: RastNode,
      rast: RastFile,
      stateNames: Set[String],
  ): DbMethod = {
    val params = fn.children.filter(_.kind == "Parameter")
    val returnType = inferReturnTypeFromNode(fn, rast)

    val paramList = params.flatMap { p =>
      // Handle destructured parameters: ({ label, value }: D3Section)
      val binding = findChild(p, "ObjectBindingPattern")
      binding match {
        case Some(obp) =>
          // Extract each bound element as a separate parameter
          val elements = obp.children.filter(_.kind == "BindingElement")
          elements.map { elem =>
            val eName = nameOf(elem)
            val eType = inferParamTypeFromNode(elem, rast) match {
              case "Any" => inferParamTypeFromNode(p, rast) match {
                case "Any" => "Any"
                case _ => "Any" // individual fields from destructured type
              }
              case t => t
            }
            (eName, eType)
          }
        case None =>
          val pName = nameOf(p)
          val pType = inferParamTypeFromNode(p, rast)
          List((pName, pType))
      }
    }.toList

    // Body: the LAST child that is not a Parameter or a type annotation.
    // A Block is the body; otherwise the last non-type child is the
    // expression body (e.g. `=> sections`, `=> data.packet`).
    val bodyNode = {
      val block = fn.children.find(_.kind == "Block")
      block.orElse {
        fn.children.filterNot(c =>
          c.kind == "Parameter" || isTypeKeyword(c.kind) ||
            c.kind == "TypeReference" || c.kind == "ArrayType" ||
            c.kind == "UnionType" || c.kind == "FunctionType"
        ).lastOption
      }
    }

    val bodyLines: List[String] = bodyNode match {
      case Some(b) if b.kind == "Block" =>
        emitBlockLines(b, rast, stateNames)
      case Some(expr) =>
        List(emitExprSimple(expr, rast, stateNames))
      case None =>
        List("???")
    }

    val isGetter = name.startsWith("get") && params.isEmpty && bodyLines.size == 1
    val isSetter = name.startsWith("set") && params.size == 1 && returnType == "Unit"

    DbMethod(name, paramList, returnType, bodyLines, isGetter, isSetter)
  }

  // -- Class analysis ---------------------------------------------------------

  private def analyzeClass(node: RastNode, rast: RastFile): InnerClass = {
    val name = nameOf(node)
    val fields = mutable.ListBuffer.empty[(String, String, Option[String])]

    // Look for constructor parameters
    for (child <- node.children) {
      child.kind match {
        case "Constructor" =>
          for (param <- child.children.filter(_.kind == "Parameter")) {
            val pName = nameOf(param)
            val pType = inferParamTypeFromNode(param, rast)
            val default = param.children.find(c =>
              c.kind != "Identifier" && !isTypeKeyword(c.kind) && c.kind != "TypeReference"
            ).map(d => emitExprSimple(d, rast, Set.empty))
            val isPublic = param.flags.contains("PublicKeyword")
            if (isPublic) {
              fields += ((pName, pType, default))
            }
          }
        case "PropertyDeclaration" =>
          val pName = nameOf(child)
          val pType = inferParamTypeFromNode(child, rast)
          fields += ((pName, pType, None))
        case _ => ()
      }
    }

    InnerClass(name, fields.toList)
  }

  // -- Emission ---------------------------------------------------------------

  private def emitInnerClass(sb: StringBuilder, cls: InnerClass): Unit = {
    sb.append(s"final case class ${cls.name}(\n")
    val params = cls.fields.map { case (name, tpe, default) =>
      val defaultStr = default.map(d => s" = $d").getOrElse("")
      s"  $name: $tpe$defaultStr"
    }
    sb.append(params.mkString(",\n"))
    sb.append("\n)\n")
  }

  @annotation.nowarn("msg=unused")
  private def emitMethod(sb: StringBuilder, m: DbMethod, analysis: ModuleAnalysis): Unit = {
    val paramStr = m.params.map { case (n, t) => s"$n: $t" }.mkString(", ")
    val retStr = if (m.returnType == "Unit") ": Unit" else s": ${m.returnType}"

    if (m.body.size == 1 && !m.body.head.contains("\n")) {
      // Single-expression method
      sb.append(s"  def ${m.name}($paramStr)$retStr =\n")
      sb.append(s"    ${m.body.head}\n")
    } else {
      sb.append(s"  def ${m.name}($paramStr)$retStr = {\n")
      for (line <- m.body) {
        sb.append(s"    $line\n")
      }
      sb.append("  }\n")
    }
  }

  private def emitClear(
      sb: StringBuilder,
      analysis: ModuleAnalysis,
      missingCommon: List[(String, String, String)],
  ): Unit = {
    sb.append("  def clear(): Unit = {\n")

    // Reset all state vars
    for (v <- analysis.stateVars) {
      sb.append(s"    ${v.name} = ${v.defaultExpr}\n")
    }

    // Reset common fields
    for ((name, _, default) <- missingCommon) {
      sb.append(s"    $name = $default\n")
    }

    sb.append("  }\n")
  }

  // -- Expression emission ----------------------------------------------------

  /** Emits a RAST expression as a simple Scala expression string.
    *
    * This is a recursive walk of the expression tree, translating
    * TypeScript idioms to Scala. Module-level state references
    * (identifiers in `stateNames`) are left as bare names since they
    * become fields on `this`.
    */
  private def emitExprSimple(node: RastNode, rast: RastFile, stateNames: Set[String]): String = {
    node.kind match {
      case "Identifier" =>
        val name = node.text.getOrElse("$unknown")
        // Module-level state becomes a field reference
        if (stateNames.contains(name)) name
        // commonDb references are translated
        else if (name == "commonClear") "/* commonClear */"
        else name

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
      case "ThisKeyword" => "this"
      case "SuperKeyword" => "super"

      case "PropertyAccessExpression" =>
        val parts = node.children
        if (parts.size >= 2) {
          val receiver = emitExprSimple(parts.head, rast, stateNames)
          val prop = parts.last.text.getOrElse("")
          // Module-level state: `data.packet` -> `packet` if `data` is the state
          if (stateNames.contains(receiver) && isCompoundState(receiver, rast)) {
            prop
          } else {
            s"$receiver.$prop"
          }
        } else {
          parts.map(c => emitExprSimple(c, rast, stateNames)).mkString(".")
        }

      case "CallExpression" =>
        val callee = node.children.headOption
        val args = node.children.drop(1)

        callee match {
          case Some(pa) if pa.kind == "PropertyAccessExpression" =>
            val parts = pa.children
            val receiver = if (parts.nonEmpty) emitExprSimple(parts.head, rast, stateNames) else "???"
            val method = if (parts.size >= 2) parts.last.text.getOrElse("") else ""

            // Special translations
            method match {
              case "push" =>
                val pushArgs = args.map(a => emitExprSimple(a, rast, stateNames))
                s"$receiver += ${pushArgs.mkString(", ")}"
              case "set" if args.size == 2 =>
                val k = emitExprSimple(args.head, rast, stateNames)
                val v = emitExprSimple(args(1), rast, stateNames)
                s"$receiver($k) = $v"
              case "get" if args.size == 1 =>
                val k = emitExprSimple(args.head, rast, stateNames)
                s"$receiver.get($k)"
              case "has" if args.size == 1 =>
                val k = emitExprSimple(args.head, rast, stateNames)
                s"$receiver.contains($k)"
              case "forEach" if args.nonEmpty =>
                val fn = emitExprSimple(args.head, rast, stateNames)
                s"$receiver.foreach($fn)"
              case "map" if args.nonEmpty =>
                val fn = emitExprSimple(args.head, rast, stateNames)
                s"$receiver.map($fn)"
              case "filter" if args.nonEmpty =>
                val fn = emitExprSimple(args.head, rast, stateNames)
                s"$receiver.filter($fn)"
              case "length" if args.isEmpty =>
                s"$receiver.length"
              case _ =>
                val emittedArgs = args.map(a => emitExprSimple(a, rast, stateNames))
                s"$receiver.$method(${emittedArgs.mkString(", ")})"
            }

          case Some(id) if id.kind == "Identifier" =>
            val fnName = id.text.getOrElse("")
            fnName match {
              case "structuredClone" =>
                // structuredClone(x) -> copy the default
                val arg = args.headOption.map(a => emitExprSimple(a, rast, stateNames)).getOrElse("???")
                arg
              case "commonClear" =>
                "/* commonClear() */"
              case _ =>
                val emittedArgs = args.map(a => emitExprSimple(a, rast, stateNames))
                s"$fnName(${emittedArgs.mkString(", ")})"
            }

          case _ =>
            val calleeStr = callee.map(c => emitExprSimple(c, rast, stateNames)).getOrElse("???")
            val emittedArgs = args.map(a => emitExprSimple(a, rast, stateNames))
            s"$calleeStr(${emittedArgs.mkString(", ")})"
        }

      case "BinaryExpression" =>
        val children = node.children
        if (children.size >= 2) {
          val left = emitExprSimple(children(0), rast, stateNames)
          val right = emitExprSimple(children(1), rast, stateNames)
          val op = tsOpToScala(node.operator.getOrElse("???"))
          if (op == "=") s"$left = $right"
          else s"$left $op $right"
        } else "???"

      case "PrefixUnaryExpression" =>
        val op = tsOpToScala(node.operator.getOrElse(""))
        val operand = node.children.headOption.map(c => emitExprSimple(c, rast, stateNames)).getOrElse("???")
        s"$op$operand"

      case "ParenthesizedExpression" =>
        val inner = node.children.headOption.map(c => emitExprSimple(c, rast, stateNames)).getOrElse("???")
        s"($inner)"

      case "ConditionalExpression" =>
        val ch = node.children
        if (ch.size >= 3) {
          val cond = emitExprSimple(ch(0), rast, stateNames)
          val thenE = emitExprSimple(ch(1), rast, stateNames)
          val elseE = emitExprSimple(ch(2), rast, stateNames)
          s"if ($cond) $thenE else $elseE"
        } else "???"

      case "ArrayLiteralExpression" =>
        val elems = node.children.map(c => emitExprSimple(c, rast, stateNames))
        if (elems.isEmpty) "mutable.ArrayBuffer.empty"
        else s"mutable.ArrayBuffer(${elems.mkString(", ")})"

      case "ObjectLiteralExpression" =>
        val fields = node.children.collect {
          case pa if pa.kind == "PropertyAssignment" =>
            val key = nameOf(pa)
            val value = pa.children.lastOption.map(c => emitExprSimple(c, rast, stateNames)).getOrElse("???")
            s"\"$key\" -> $value"
        }
        if (fields.isEmpty) "Map.empty"
        else s"Map(${fields.mkString(", ")})"

      case "NewExpression" =>
        val className = node.children.headOption.flatMap(_.text).getOrElse("???")
        val args = node.children.drop(1).map(a => emitExprSimple(a, rast, stateNames))
        className match {
          case "Map" => "mutable.Map.empty"
          case "Set" => "mutable.Set.empty"
          case _ if args.isEmpty => s"new $className()"
          case _ => s"new $className(${args.mkString(", ")})"
        }

      case "TemplateExpression" =>
        emitTemplateExpr(node, rast, stateNames)

      case "NoSubstitutionTemplateLiteral" =>
        val text = node.value match {
          case Some(RastValue.Str(s)) => escapeScala(s)
          case _ => ""
        }
        s"\"$text\""

      case "ArrowFunction" =>
        val params = node.children.filter(_.kind == "Parameter")
        val body = node.children.find(c =>
          c.kind == "Block" || (c.kind != "Parameter" && !isTypeKeyword(c.kind) &&
            c.kind != "TypeReference" && c.kind != "ArrayType")
        )
        val paramList = params.map(p => nameOf(p)).mkString(", ")
        val bodyStr = body.map(b => emitExprSimple(b, rast, stateNames)).getOrElse("???")
        if (params.size == 1) s"$paramList => $bodyStr"
        else s"($paramList) => $bodyStr"

      case "ElementAccessExpression" =>
        val obj = node.children.headOption.map(c => emitExprSimple(c, rast, stateNames)).getOrElse("???")
        val idx = node.children.lastOption.map(c => emitExprSimple(c, rast, stateNames)).getOrElse("0")
        s"$obj($idx)"

      case "TypeOfExpression" =>
        val operand = node.children.headOption.map(c => emitExprSimple(c, rast, stateNames)).getOrElse("???")
        s"$operand /* typeof */"

      case "AsExpression" =>
        // Type assertion: just emit the expression
        node.children.headOption.map(c => emitExprSimple(c, rast, stateNames)).getOrElse("???")

      case _ =>
        s"??? /* ${node.kind} */"
    }
  }

  /** Emits the statements of a Block as a list of Scala lines. */
  private def emitBlockLines(block: RastNode, rast: RastFile, stateNames: Set[String]): List[String] = {
    val lines = mutable.ListBuffer.empty[String]
    for (stmt <- block.children) {
      stmt.kind match {
        case "ReturnStatement" =>
          val expr = stmt.children.headOption
          expr.foreach(e => lines += emitExprSimple(e, rast, stateNames))
          if (expr.isEmpty) lines += "()"

        case "ExpressionStatement" =>
          val expr = stmt.children.headOption
          expr.foreach { e =>
            val line = emitExprSimple(e, rast, stateNames)
            // Skip commonClear() calls
            if (!line.contains("commonClear")) {
              lines += line
            }
          }

        case "VariableStatement" =>
          val decls = extractVarDecls(stmt)
          for (d <- decls) {
            val name = nameOf(d)
            val isConst = d.flags.contains("const")
            val kw = if (isConst) "val" else "var"
            val init = d.children.find(c =>
              c.kind != "Identifier" && !isTypeKeyword(c.kind) && c.kind != "TypeReference"
            )
            init.foreach { i =>
              lines += s"$kw $name = ${emitExprSimple(i, rast, stateNames)}"
            }
          }

        case "IfStatement" =>
          lines ++= emitIfLines(stmt, rast, stateNames)

        case "ForStatement" | "ForOfStatement" | "ForInStatement" =>
          lines += s"// TODO: ${stmt.kind}"

        case "ThrowStatement" =>
          val expr = stmt.children.headOption.map(e => emitExprSimple(e, rast, stateNames)).getOrElse("???")
          lines += s"throw $expr"

        case "SwitchStatement" =>
          lines += s"// TODO: switch"

        case _ =>
          lines += s"// TODO: ${stmt.kind}"
      }
    }
    lines.toList
  }

  private def emitIfLines(node: RastNode, rast: RastFile, stateNames: Set[String]): List[String] = {
    val lines = mutable.ListBuffer.empty[String]
    val ch = node.children
    if (ch.size >= 2) {
      val cond = emitExprSimple(ch(0), rast, stateNames)
      lines += s"if ($cond) {"
      if (ch(1).kind == "Block") {
        for (line <- emitBlockLines(ch(1), rast, stateNames))
          lines += s"  $line"
      } else {
        lines += s"  ${emitExprSimple(ch(1), rast, stateNames)}"
      }
      lines += "}"
      if (ch.size >= 3) {
        if (ch(2).kind == "IfStatement") {
          val elseLines = emitIfLines(ch(2), rast, stateNames)
          if (elseLines.nonEmpty) {
            lines += "else " + elseLines.head
            lines ++= elseLines.tail
          }
        } else {
          lines += "else {"
          if (ch(2).kind == "Block") {
            for (line <- emitBlockLines(ch(2), rast, stateNames))
              lines += s"  $line"
          } else {
            lines += s"  ${emitExprSimple(ch(2), rast, stateNames)}"
          }
          lines += "}"
        }
      }
    }
    lines.toList
  }

  private def emitTemplateExpr(node: RastNode, rast: RastFile, stateNames: Set[String]): String = {
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
            sb.append(emitExprSimple(e, rast, stateNames))
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

  // -- Type resolution --------------------------------------------------------

  private def resolveTypeAnnotation(decl: RastNode, rast: RastFile): Option[String] = {
    val typeNode = decl.children.find(c =>
      c.kind == "TypeReference" || isTypeKeyword(c.kind) || c.kind == "ArrayType" || c.kind == "UnionType"
    )
    typeNode.map(t => tsTypeNodeToScalaStr(t, rast))
  }

  private def tsTypeNodeToScala(decl: RastNode, rast: RastFile): String = {
    val typeNode = decl.children.find(c =>
      c.kind == "TypeReference" || isTypeKeyword(c.kind) || c.kind == "ArrayType" || c.kind == "UnionType"
    )
    typeNode match {
      case Some(t) => tsTypeNodeToScalaStr(t, rast)
      case None =>
        // Fall back to the RAST type map
        decl.`type`.flatMap(rast.types.get) match {
          case Some(rt) => rastTypeToScala(rt, rast)
          case None => "Any"
        }
    }
  }

  private def tsTypeNodeToScalaStr(node: RastNode, rast: RastFile): String = {
    node.kind match {
      case "StringKeyword" => "String"
      case "NumberKeyword" => "Double"
      case "BooleanKeyword" => "Boolean"
      case "VoidKeyword" => "Unit"
      case "AnyKeyword" => "Any"
      case "NeverKeyword" => "Nothing"
      case "NullKeyword" => "Null"
      case "UndefinedKeyword" => "Unit"
      case "TypeReference" =>
        val name = node.children.find(_.kind == "Identifier").flatMap(_.text).getOrElse("Any")
        name match {
          case "Array" =>
            val typeArg = node.children.find(c => c.kind != "Identifier")
              .map(c => tsTypeNodeToScalaStr(c, rast)).getOrElse("Any")
            s"mutable.ArrayBuffer[$typeArg]"
          case "Map" =>
            val typeArgs = node.children.filter(c => c.kind != "Identifier").map(c => tsTypeNodeToScalaStr(c, rast))
            if (typeArgs.size >= 2) s"mutable.Map[${typeArgs(0)}, ${typeArgs(1)}]"
            else "mutable.Map[String, Any]"
          case "Set" =>
            val typeArg = node.children.find(c => c.kind != "Identifier")
              .map(c => tsTypeNodeToScalaStr(c, rast)).getOrElse("Any")
            s"mutable.Set[$typeArg]"
          case "Record" =>
            val typeArgs = node.children.filter(c => c.kind != "Identifier").map(c => tsTypeNodeToScalaStr(c, rast))
            if (typeArgs.size >= 2) s"mutable.Map[${typeArgs(0)}, ${typeArgs(1)}]"
            else "mutable.Map[String, Any]"
          case "Required" | "RequiredDeep" | "Readonly" =>
            // Unwrap utility types
            node.children.find(c => c.kind != "Identifier")
              .map(c => tsTypeNodeToScalaStr(c, rast)).getOrElse("Any")
          case other => other
        }
      case "ArrayType" =>
        val elem = node.children.headOption.map(c => tsTypeNodeToScalaStr(c, rast)).getOrElse("Any")
        s"mutable.ArrayBuffer[$elem]"
      case "UnionType" =>
        val types = node.children.map(c => tsTypeNodeToScalaStr(c, rast)).distinct
        if (types.contains("Null") || types.contains("Unit")) {
          val real = types.filterNot(t => t == "Null" || t == "Unit")
          if (real.size == 1) s"Option[${real.head}]"
          else types.mkString(" | ")
        } else types.mkString(" | ")
      case _ => "Any"
    }
  }

  private def rastTypeToScala(rt: RastType, rast: RastFile): String = {
    rt.kind match {
      case "string" => "String"
      case "number" => "Double"
      case "boolean" => "Boolean"
      case "void" => "Unit"
      case "null" | "undefined" => "Null"
      case "any" => "Any"
      case "never" => "Nothing"
      case "array" =>
        val elemType = rt.elementType.flatMap(rast.types.get).map(t => rastTypeToScala(t, rast)).getOrElse("Any")
        s"mutable.ArrayBuffer[$elemType]"
      case "reference" =>
        // Generic type reference: Map<string, SankeyNode>, Array<PacketWord>, etc.
        val target = rt.target.flatMap(rast.types.get)
        val typeArgs = rt.typeArguments.getOrElse(Nil).flatMap(rast.types.get).map(t => rastTypeToScala(t, rast))
        val baseName = target.map(_.text).getOrElse(rt.text.takeWhile(_ != '<'))
        baseName match {
          case s if s.startsWith("Map") || s == "Map" =>
            if (typeArgs.size >= 2) s"mutable.Map[${typeArgs(0)}, ${typeArgs(1)}]"
            else "mutable.Map[String, Any]"
          case s if s.startsWith("Set") || s == "Set" =>
            val elemType = typeArgs.headOption.getOrElse("Any")
            s"mutable.Set[$elemType]"
          case s if s.startsWith("Array") || s == "Array" =>
            val elemType = typeArgs.headOption.getOrElse("Any")
            s"mutable.ArrayBuffer[$elemType]"
          case _ =>
            if (typeArgs.nonEmpty) s"$baseName[${typeArgs.mkString(", ")}]"
            else baseName
        }
      case "object" =>
        // Check if the text is a named type (e.g. "SankeyNode")
        val txt = rt.text.trim
        if (txt.nonEmpty && txt.head.isUpper && txt.forall(c => c.isLetterOrDigit || c == '_')) {
          txt // Named object type like SankeyNode
        } else if (txt.contains("Map<")) {
          "mutable.Map[String, Any]"
        } else "Any"
      case "function" =>
        val params = rt.parameters.getOrElse(Nil)
        val retType = rt.returnType.flatMap(rast.types.get).map(t => rastTypeToScala(t, rast)).getOrElse("Any")
        if (params.isEmpty) s"() => $retType"
        else {
          val paramTypes = params.map(p => p.`type`).map(t => rast.types.get(t).map(tt => rastTypeToScala(tt, rast)).getOrElse("Any"))
          s"(${paramTypes.mkString(", ")}) => $retType"
        }
      case _ => "Any"
    }
  }

  private def inferReturnTypeFromNode(fn: RastNode, rast: RastFile): String = {
    // Check for explicit return type annotation
    val typeNode = fn.children.find(c =>
      isTypeKeyword(c.kind) || c.kind == "TypeReference" || c.kind == "ArrayType" || c.kind == "UnionType"
    )
    typeNode match {
      case Some(t) if t.kind != "Parameter" => tsTypeNodeToScalaStr(t, rast)
      case _ =>
        // Fall back to RAST type
        fn.`type`.flatMap(rast.types.get) match {
          case Some(rt) if rt.kind == "function" =>
            rt.returnType.flatMap(rast.types.get).map(t => rastTypeToScala(t, rast)).getOrElse("Any")
          case _ => "Any"
        }
    }
  }

  private def inferParamTypeFromNode(param: RastNode, rast: RastFile): String = {
    val typeNode = param.children.find(c =>
      c.kind == "TypeReference" || isTypeKeyword(c.kind) || c.kind == "ArrayType" || c.kind == "UnionType"
    )
    typeNode match {
      case Some(t) => tsTypeNodeToScalaStr(t, rast)
      case None =>
        param.`type`.flatMap(rast.types.get).map(t => rastTypeToScala(t, rast)).getOrElse("Any")
    }
  }

  // -- Init / default resolution ----------------------------------------------

  private def resolveInitExpr(decl: RastNode, rast: RastFile, stateNames: Set[String]): String = {
    val init = decl.children.find(c =>
      c.kind != "Identifier" && !isTypeKeyword(c.kind) && c.kind != "TypeReference" &&
        c.kind != "ArrayType" && c.kind != "UnionType"
    )
    init match {
      case Some(n) =>
        val expr = emitExprSimple(n, rast, stateNames)
        // Handle common patterns
        expr match {
          case s if s.contains("structuredClone") => resolveStructuredClone(n, rast, stateNames)
          case s if s.startsWith("DEFAULT_") && s.contains(".") =>
            // DEFAULT_PIE_DB.sections -> use the default
            resolveDefaultPropertyAccess(n, rast, stateNames)
          case other => other
        }
      case None =>
        // Use type default
        tsTypeNodeToScala(decl, rast) match {
          case "String" => "\"\""
          case "Double" | "Int" => "0"
          case "Boolean" => "false"
          case t if t.contains("ArrayBuffer") => "mutable.ArrayBuffer.empty"
          case t if t.contains("Map") => "mutable.Map.empty"
          case t if t.contains("Set") => "mutable.Set.empty"
          case _ => "???"
        }
    }
  }

  private def resolveDefaultExpr(decl: RastNode, rast: RastFile, stateNames: Set[String]): String = {
    // For clear(): the default value to reset to
    val scalaType = tsTypeNodeToScala(decl, rast)
    val initExpr = resolveInitExpr(decl, rast, stateNames)

    // If the init is a structuredClone or property access, the default is the same
    scalaType match {
      case "String" => "\"\""
      case "Double" | "Int" => "0"
      case "Boolean" => "false"
      case t if t.contains("ArrayBuffer") => "mutable.ArrayBuffer.empty"
      case t if t.contains("Map") =>
        if (initExpr.contains("Map.empty") || initExpr.contains("Map()")) initExpr
        else "mutable.Map.empty"
      case t if t.contains("Set") => "mutable.Set.empty"
      case _ => initExpr // fall back to the init expression
    }
  }

  private def resolveStructuredClone(node: RastNode, rast: RastFile, stateNames: Set[String]): String = {
    // structuredClone(defaultData) -> use the default's initial value
    node.kind match {
      case "CallExpression" =>
        val arg = node.children.drop(1).headOption
        arg.map(a => emitExprSimple(a, rast, stateNames)).getOrElse("???")
      case _ => emitExprSimple(node, rast, stateNames)
    }
  }

  private def resolveDefaultPropertyAccess(node: RastNode, rast: RastFile, stateNames: Set[String]): String = {
    // DEFAULT_PIE_DB.sections -> Map.empty, DEFAULT_PIE_DB.showData -> false
    val scalaType = node.`type`.flatMap(rast.types.get).map(t => rastTypeToScala(t, rast)).getOrElse("Any")
    scalaType match {
      case "String" => "\"\""
      case "Double" | "Int" => "0"
      case "Boolean" => "false"
      case t if t.contains("ArrayBuffer") => "mutable.ArrayBuffer.empty"
      case t if t.contains("Map") => "mutable.Map.empty"
      case _ => emitExprSimple(node, rast, stateNames)
    }
  }

  /** Checks if a state variable holds compound data (an object with sub-fields).
    * Used to decide whether `data.packet` should become just `packet`. */
  private def isCompoundState(name: String, rast: RastFile): Boolean = {
    import scala.util.boundary
    import scala.util.boundary.break
    boundary {
      for (node <- rast.nodes) {
        if (node.kind == "VariableStatement") {
          val decls = extractVarDecls(node)
          for (d <- decls) {
            if (nameOf(d) == name) {
              val typeRef = d.children.find(_.kind == "TypeReference")
              break(typeRef.isDefined)
            }
          }
        }
      }
      false
    }
  }

  // -- Helpers ----------------------------------------------------------------

  @annotation.nowarn("msg=unused")
  private def header(sourcePath: String, targetFile: String): String =
    s"""/*
       | * Mermaid diagramming engine - Scala 3 port
       | *
       | * Ported from: $sourcePath
       | * Original license: MIT
       | *
       | * Auto-generated by MermaidDbEmitter from RAST v1
       | */
       |""".stripMargin

  private def nameOf(node: RastNode): String =
    node.children.find(_.kind == "Identifier").flatMap(_.text).getOrElse("$anon")

  private def findChild(node: RastNode, kind: String): Option[RastNode] =
    node.children.find(_.kind == kind)

  private def extractVarDecls(stmt: RastNode): List[RastNode] = {
    val declLists = stmt.children.filter(_.kind == "VariableDeclarationList")
    if (declLists.nonEmpty) declLists.flatMap(_.children.filter(_.kind == "VariableDeclaration"))
    else stmt.children.filter(_.kind == "VariableDeclaration")
  }

  private def isTypeKeyword(kind: String): Boolean =
    kind == "StringKeyword" || kind == "NumberKeyword" || kind == "BooleanKeyword" ||
      kind == "VoidKeyword" || kind == "AnyKeyword" || kind == "NeverKeyword" ||
      kind == "NullKeyword" || kind == "UndefinedKeyword" || kind == "FunctionType"

  private def isCommonDbMethod(name: String): Boolean =
    commonDbFunctions.contains(name) || name.startsWith("setAcc") || name.startsWith("getAcc") ||
      name.startsWith("setDiagram") || name.startsWith("getDiagram")

  private def padField(name: String, vars: Iterable[StateVar]): String = {
    val maxLen = vars.map(_.name.length).maxOption.getOrElse(0).max(14)
    name.padTo(maxLen, ' ')
  }

  private def escapeScala(s: String): String =
    s.replace("\\", "\\\\").replace("\"", "\\\"").replace("\n", "\\n").replace("\r", "\\r")

  private def escapeInterpolation(s: String): String =
    s.replace("$", "$$").replace("\"", "\\\"")

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
    case "EqualsToken" => "="
    case "PlusEqualsToken" => "+="
    case "MinusEqualsToken" => "-="
    case "ExclamationToken" => "!"
    case other => other
  }
}
