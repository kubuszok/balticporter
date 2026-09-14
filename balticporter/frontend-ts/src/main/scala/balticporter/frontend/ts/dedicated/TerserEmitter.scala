package balticporter.frontend.ts.dedicated

import balticporter.frontend.ts.{RastFile, RastNode, RastType, RastValue}
import scala.collection.mutable

/** Dedicated RAST-to-Scala emitter for Terser.
  *
  * Handles three categories of Terser source files:
  *
  *  1. DEFNODE hierarchy extraction (ast.js) -- recognizes the
  *     `DEFNODE(type, props, ctor, methods, base)` call pattern and produces a
  *     hierarchy summary (class name, fields, parent, methods list). The actual
  *     Scala class emission uses the hand-ported ssg-js AST types as reference.
  *
  *  2. Pure utility files (compressor-flags.js, utils/first_in_statement.js,
  *     utils/index.js) -- constants, pure functions, data tables.
  *
  *  3. Non-DEFNODE module files (native-objects.js, equivalent-to.js) --
  *     functions that reference the AST hierarchy without modifying it.
  *
  * Files that use DEFMETHOD (scope.js, output.js, compress/index.js) extend
  * DEFNODE classes with additional methods. These require the full AST hierarchy
  * to be in place first and are not addressed here. */
object TerserEmitter:

  // --------------------------------------------------------------------------
  // DEFNODE hierarchy extraction
  // --------------------------------------------------------------------------

  export _root_.balticporter.frontend.ts.dedicated.{DefnodeClass, DefmethodEntry, FreeFunction}

  /** Extract the DEFNODE class hierarchy from a Terser ast.js RAST file. */
  def extractHierarchy(file: RastFile): List[DefnodeClass] =
    val classes = mutable.ListBuffer.empty[DefnodeClass]

    for node <- file.nodes do
      extractDefnodeFromStatement(node) match
        case Some(cls) => classes += cls
        case None      => ()

    // Mark abstract classes (those that have subclasses)
    val names = classes.map(_.varName).toSet
    val parents = classes.flatMap(_.base).toSet
    for cls <- classes do
      cls.isAbstract = parents.contains(cls.varName)

    classes.toList

  /** Produce a human-readable hierarchy summary from extracted DEFNODE classes. */
  def hierarchySummary(classes: List[DefnodeClass]): String =
    val sb = new StringBuilder
    val byName = classes.map(c => c.varName -> c).toMap
    val children = mutable.Map.empty[String, mutable.ListBuffer[String]]
    for cls <- classes do
      val parent = cls.base.getOrElse("(root)")
      children.getOrElseUpdate(parent, mutable.ListBuffer.empty) += cls.varName

    def printTree(name: String, indent: Int): Unit =
      val cls = byName.get(name)
      val prefix = "  " * indent
      val kind = if cls.exists(_.isAbstract) then "trait" else "class"
      val props = cls.map(_.selfProps).getOrElse(Nil)
      val propsStr = if props.isEmpty then "" else s" (${props.mkString(", ")})"
      val methods = cls.map(_.methods).getOrElse(Nil)
      val methodStr = if methods.isEmpty then "" else s" [${methods.size} methods]"
      sb.append(s"$prefix$kind $name$propsStr$methodStr\n")
      for kids <- children.get(name); kid <- kids do
        printTree(kid, indent + 1)

    // Find roots (classes whose base is not in our set or is themselves)
    val roots = classes.filter(c => c.base.isEmpty || c.base.contains(c.varName))
    for root <- roots do printTree(root.varName, 0)

    sb.toString

  // --------------------------------------------------------------------------
  // compressor-flags.js -> CompressorFlags.scala
  // --------------------------------------------------------------------------

  /** Emit CompressorFlags from RAST (compressor-flags.js). */
  def emitCompressorFlags(file: RastFile): String =
    val sb = new StringBuilder
    sb.append("package ssg.js.compress\n\n")
    sb.append("import ssg.js.ast.AstNode\n\n")
    sb.append("object CompressorFlags {\n\n")

    // Extract exported constants and functions
    for node <- file.nodes do
      node.kind match
        case "VariableStatement" =>
          for vdl <- node.children.find(_.kind == "VariableDeclarationList")
              vd <- vdl.children.filter(_.kind == "VariableDeclaration") do
            val name = nameOf(vd)
            val isConst = vd.flags.contains("const")
            val isExported = node.flags.contains("ExportKeyword")
            // Check if this is a value (number/binary) or an arrow function
            val rhs = vd.children.drop(1) // skip the identifier
            rhs.headOption match
              case Some(lit) if lit.kind == "NumericLiteral" =>
                val value = lit.value match
                  case Some(RastValue.Num(n)) => formatInt(n)
                  case _ => lit.children.headOption.flatMap(_.value).map {
                    case RastValue.Num(n) => formatInt(n)
                    case v => v.toString
                  }.getOrElse("0")
                val scalaName = camelToScreamingSnake(name)
                sb.append(s"  val $scalaName: Int = $value\n\n")
              case Some(bin) if bin.kind == "BinaryExpression" =>
                // CLEAR_BETWEEN_PASSES = SQUEEZED | OPTIMIZED | TOP
                val scalaName = camelToScreamingSnake(name)
                val expr = emitBinaryExpr(bin, file)
                sb.append(s"  val $scalaName: Int = $expr\n\n")
              case Some(arrow) if arrow.kind == "ArrowFunction" =>
                // Arrow functions: has_flag, set_flag, clear_flag
                name match
                  case "has_flag" =>
                    sb.append("  def hasFlag(node: AstNode, flag: Int): Boolean =\n")
                    sb.append("    (node.flags & flag) != 0\n\n")
                  case "set_flag" =>
                    sb.append("  def setFlag(node: AstNode, flag: Int): Unit =\n")
                    sb.append("    node.flags |= flag\n\n")
                  case "clear_flag" =>
                    sb.append("  def clearFlag(node: AstNode, flag: Int): Unit =\n")
                    sb.append("    node.flags &= ~flag\n\n")
                  case _ => ()
              case _ => ()
        case _ => ()

    sb.append("}\n")
    sb.toString

  // --------------------------------------------------------------------------
  // utils/first_in_statement.js -> FirstInStatement.scala
  // --------------------------------------------------------------------------

  /** Emit FirstInStatement from RAST. */
  def emitFirstInStatement(file: RastFile): String =
    val sb = new StringBuilder
    sb.append("package ssg.js.output\n\n")
    sb.append("import scala.util.boundary\n")
    sb.append("import scala.util.boundary.break\n")
    sb.append("import ssg.js.ast.*\n\n")
    sb.append("object FirstInStatement {\n\n")

    // first_in_statement: walks a parent stack checking if node is leftmost
    sb.append("  def firstInStatement(output: OutputStream): Boolean =\n")
    sb.append("    boundary[Boolean] {\n")
    sb.append("      var node: AstNode | Null = output.parent(-1)\n")
    sb.append("      if (node == null) break(false)\n")
    sb.append("      var i = 0\n")
    sb.append("      var p: AstNode | Null = output.parent(i)\n")
    sb.append("      while (p != null) {\n")
    sb.append("        val parent = p.nn\n")
    sb.append("        parent match {\n")
    sb.append("          case stmt: AstSimpleStatement if stmt.body != null && (stmt.body.nn eq node.nn) =>\n")
    sb.append("            break(true)\n")
    sb.append("          case stmt: AstStatementWithBody if stmt.body != null && (stmt.body.nn eq node.nn) =>\n")
    sb.append("            break(true)\n")
    sb.append("          case _ =>\n")
    sb.append("            val isLeftmost = parent match {\n")
    sb.append("              case seq: AstSequence =>\n")
    sb.append("                seq.expressions.nonEmpty && (seq.expressions.head eq node.nn)\n")
    sb.append("              case call: AstCall if !call.isInstanceOf[AstNew] =>\n")
    sb.append("                call.expression != null && (call.expression.nn eq node.nn)\n")
    sb.append("              case pts: AstPrefixedTemplateString =>\n")
    sb.append("                pts.prefix != null && (pts.prefix.nn eq node.nn)\n")
    sb.append("              case dot: AstDot =>\n")
    sb.append("                dot.expression != null && (dot.expression.nn eq node.nn)\n")
    sb.append("              case sub: AstSub =>\n")
    sb.append("                sub.expression != null && (sub.expression.nn eq node.nn)\n")
    sb.append("              case chain: AstChain =>\n")
    sb.append("                chain.expression != null && (chain.expression.nn eq node.nn)\n")
    sb.append("              case cond: AstConditional =>\n")
    sb.append("                cond.condition != null && (cond.condition.nn eq node.nn)\n")
    sb.append("              case bin: AstBinary =>\n")
    sb.append("                bin.left != null && (bin.left.nn eq node.nn)\n")
    sb.append("              case post: AstUnaryPostfix =>\n")
    sb.append("                post.expression != null && (post.expression.nn eq node.nn)\n")
    sb.append("              case _ => false\n")
    sb.append("            }\n")
    sb.append("            if (isLeftmost) {\n")
    sb.append("              node = parent\n")
    sb.append("            } else {\n")
    sb.append("              break(false)\n")
    sb.append("            }\n")
    sb.append("        }\n")
    sb.append("        i += 1\n")
    sb.append("        p = output.parent(i)\n")
    sb.append("      }\n")
    sb.append("      false\n")
    sb.append("    }\n\n")

    // left_is_object: recursive pattern match
    sb.append("  def leftIsObject(node: AstNode): Boolean =\n")
    sb.append("    node match {\n")
    sb.append("      case _:   AstObject   => true\n")
    sb.append("      case seq: AstSequence =>\n")
    sb.append("        seq.expressions.nonEmpty && leftIsObject(seq.expressions.head)\n")
    sb.append("      case call: AstCall if !call.isInstanceOf[AstNew] && call.expression != null =>\n")
    sb.append("        leftIsObject(call.expression.nn)\n")
    sb.append("      case pts: AstPrefixedTemplateString if pts.prefix != null =>\n")
    sb.append("        leftIsObject(pts.prefix.nn)\n")
    sb.append("      case dot: AstDot if dot.expression != null =>\n")
    sb.append("        leftIsObject(dot.expression.nn)\n")
    sb.append("      case sub: AstSub if sub.expression != null =>\n")
    sb.append("        leftIsObject(sub.expression.nn)\n")
    sb.append("      case chain: AstChain if chain.expression != null =>\n")
    sb.append("        leftIsObject(chain.expression.nn)\n")
    sb.append("      case cond: AstConditional if cond.condition != null =>\n")
    sb.append("        leftIsObject(cond.condition.nn)\n")
    sb.append("      case bin: AstBinary if bin.left != null =>\n")
    sb.append("        leftIsObject(bin.left.nn)\n")
    sb.append("      case post: AstUnaryPostfix if post.expression != null =>\n")
    sb.append("        leftIsObject(post.expression.nn)\n")
    sb.append("      case _ => false\n")
    sb.append("    }\n")

    sb.append("}\n")
    sb.toString

  // --------------------------------------------------------------------------
  // DEFMETHOD extraction and merging
  // --------------------------------------------------------------------------

  /** Extract all DEFMETHOD calls from a Terser source file RAST.
    *
    * The JS pattern is:
    * {{{
    * AST_Scope.DEFMETHOD("figure_out_scope", function(options, { parent_scope = undefined } = {}) {
    *   // body
    * });
    * }}}
    *
    * In RAST this appears as:
    * {{{
    * ExpressionStatement
    *   CallExpression
    *     PropertyAccessExpression
    *       Identifier: "AST_Scope"
    *       Identifier: "DEFMETHOD"
    *     StringLiteral: "figure_out_scope"
    *     FunctionExpression
    *       Parameter...
    *       Block (body)
    * }}}
    */
  def extractDefmethods(file: RastFile): List[DefmethodEntry] =
    val result = mutable.ListBuffer.empty[DefmethodEntry]

    for node <- file.nodes do
      extractDefmethodFromStatement(node).foreach(result += _)

    result.toList

  /** Group DEFMETHOD entries by their target class name.
    *
    * Returns a map from class name (e.g. "AST_Scope") to the list of
    * methods that should be added to that class.
    */
  def groupByClass(entries: List[DefmethodEntry]): Map[String, List[DefmethodEntry]] =
    entries.groupBy(_.className)

  /** Produce a summary of DEFMETHOD entries for diagnostics. */
  def defmethodSummary(entries: List[DefmethodEntry]): String =
    val sb = new StringBuilder
    val grouped = groupByClass(entries)
    val sortedClasses = grouped.keys.toList.sorted

    sb.append(s"DEFMETHOD summary: ${entries.size} methods across ${grouped.size} classes\n")
    sb.append("=" * 60)
    sb.append("\n")

    for cls <- sortedClasses do
      val methods = grouped(cls)
      sb.append(s"\n$cls (${methods.size} methods):\n")
      for m <- methods do
        val paramStr = if m.params.isEmpty then "()" else s"(${m.params.mkString(", ")})"
        sb.append(s"  - ${m.methodName}$paramStr\n")

    sb.toString

  /** Merge DEFMETHOD entries into a hierarchy of DEFNODE classes.
    *
    * Takes the DEFNODE hierarchy from ast.js and the DEFMETHOD entries
    * from scope.js/output.js/etc, and produces the merged class list
    * with all methods included.
    *
    * Returns a list of (DefnodeClass, List[DefmethodEntry]) where each
    * class has its DEFNODE methods plus any DEFMETHODs found in the source.
    */
  def mergeDefmethods(
      hierarchy: List[DefnodeClass],
      defmethods: List[DefmethodEntry]
  ): List[(DefnodeClass, List[DefmethodEntry])] =
    val grouped = groupByClass(defmethods)

    hierarchy.map { cls =>
      val methods = grouped.getOrElse(cls.varName, Nil)
      (cls, methods)
    }

  /** Emit a merged class as Scala, including both DEFNODE methods and DEFMETHODs.
    *
    * Uses the hand-ported ssg-js naming conventions:
    *   - AST_Scope -> AstScope
    *   - snake_case methods -> camelCase
    *   - function params -> Scala types
    */
  def emitMergedClass(
      cls: DefnodeClass,
      defmethods: List[DefmethodEntry],
      pkg: String = "ssg.js"
  ): String =
    val sb = new StringBuilder
    val scalaName = astVarToScalaName(cls.varName)
    val baseScala = cls.base.map(astVarToScalaName)
    val kind = if cls.isAbstract then "trait" else "class"

    sb.append(s"// $scalaName — merged from DEFNODE + ${defmethods.size} DEFMETHOD(s)\n")

    // Emit class header
    val extendsClause = baseScala.map(b => s" extends $b").getOrElse("")
    if cls.selfProps.nonEmpty then
      val propDecls = cls.selfProps.map { p =>
        val camel = snakeToCamel(p)
        s"var $camel: Any /* = null */"
      }
      sb.append(s"$kind $scalaName(\n")
      sb.append(propDecls.map("  " + _).mkString(",\n"))
      sb.append(s"\n)$extendsClause {\n")
    else
      sb.append(s"$kind $scalaName$extendsClause {\n")

    // DEFNODE methods (inline from ast.js)
    if cls.methods.nonEmpty then
      sb.append(s"\n  // --- DEFNODE methods ---\n")
      for m <- cls.methods do
        val camel = snakeToCamel(m)
        sb.append(s"  def $camel: Any = ???\n")

    // DEFMETHOD methods (from scope.js etc)
    if defmethods.nonEmpty then
      sb.append(s"\n  // --- DEFMETHOD additions ---\n")
      for dm <- defmethods do
        val camel = snakeToCamel(dm.methodName)
        val paramStr = if dm.params.isEmpty then ""
        else
          val decls = dm.params.map(p => s"${snakeToCamel(p)}: Any")
          s"(${decls.mkString(", ")})"
        sb.append(s"  def $camel$paramStr: Any = {\n")
        sb.append(s"    ??? // body from DEFMETHOD\n")
        sb.append(s"  }\n")

    sb.append("}\n")
    sb.toString

  /** Convert AST_VarName to Scala PascalCase (e.g. AST_Scope -> AstScope). */
  private def astVarToScalaName(varName: String): String =
    varName.split("_").map { part =>
      if part == "AST" then "Ast"
      else part.head.toUpper + part.tail.toLowerCase
    }.mkString

  // --------------------------------------------------------------------------
  // Free function extraction
  // --------------------------------------------------------------------------

  /** Extract standalone function declarations from a file.
    *
    * Files like scope.js contain both DEFMETHOD calls and standalone functions
    * (e.g. `function redefined_catch_def`, `function next_mangled`). These
    * need to be emitted as companion utility functions.
    */
  def extractFreeFunctions(file: RastFile): List[FreeFunction] =
    val result = mutable.ListBuffer.empty[FreeFunction]

    for node <- file.nodes do
      if node.kind == "FunctionDeclaration" then
        val name = node.children.find(_.kind == "Identifier").flatMap(_.text).getOrElse("")
        if name.nonEmpty then
          val params = node.children.filter(_.kind == "Parameter").map { p =>
            p.children.find(_.kind == "Identifier").flatMap(_.text).getOrElse("_")
          }
          val body = node.children.find(_.kind == "Block").getOrElse(
            RastNode("Block", 0, (0, 0))
          )
          result += FreeFunction(name, params, body)

    result.toList

  /** Extract class declarations from a file.
    *
    * Files like scope.js may contain ES6 class declarations (e.g. SymbolDef)
    * alongside DEFMETHOD calls.
    */
  def extractClassDeclarations(file: RastFile): List[String] =
    file.nodes.collect {
      case node if node.kind == "ClassDeclaration" =>
        node.children.find(_.kind == "Identifier").flatMap(_.text).getOrElse("?")
    }

  // --------------------------------------------------------------------------
  // Private helpers
  // --------------------------------------------------------------------------

  /** Extract a DEFMETHOD entry from an ExpressionStatement node.
    *
    * Handles two forms:
    *   1. `AST_X.DEFMETHOD("name", function(...) { body })` — explicit function
    *   2. `AST_X.DEFMETHOD("name", return_false)` — identifier reference to a
    *      utility function (return_false, return_true, return_this)
    */
  private def extractDefmethodFromStatement(node: RastNode): Option[DefmethodEntry] =
    if node.kind != "ExpressionStatement" then return None
    val call = node.children.find(_.kind == "CallExpression")
    call.flatMap { c =>
      val callee = c.children.headOption
      callee match
        case Some(pa) if pa.kind == "PropertyAccessExpression" =>
          val className = pa.children.headOption.flatMap(_.text).getOrElse("")
          val methodId = pa.children.lastOption.flatMap(_.text).getOrElse("")
          if methodId != "DEFMETHOD" || className.isEmpty then return None

          val args = c.children.tail
          val methodName = args.headOption.flatMap(_.value).collect {
            case RastValue.Str(s) => s
          }.getOrElse("?")

          // The function body can be:
          // 1. A FunctionExpression or ArrowFunction with params and block
          // 2. An Identifier reference (return_false, return_true, return_this)
          val funcExpr = args.find(_.kind == "FunctionExpression")
            .orElse(args.find(_.kind == "ArrowFunction"))

          funcExpr match
            case Some(fn) =>
              val params = fn.children.filter(_.kind == "Parameter").map { p =>
                p.children.find(_.kind == "Identifier").flatMap(_.text).getOrElse("_")
              }
              val body = fn.children.find(_.kind == "Block").getOrElse(
                RastNode("Block", 0, (0, 0))
              )
              Some(DefmethodEntry(className, methodName, params, body))

            case None =>
              // Check for identifier reference (return_false, return_true, etc.)
              val identRef = args.find(_.kind == "Identifier")
              identRef.map { id =>
                val refName = id.text.getOrElse("")
                // Synthesize a body node that captures the reference
                val syntheticBody = RastNode("Block", 0, (0, 0), children = List(
                  RastNode("ReturnStatement", 0, (0, 0), children = List(
                    RastNode("Identifier", 0, (0, 0), text = Some(refName))
                  ))
                ))
                DefmethodEntry(className, methodName, Nil, syntheticBody)
              }
        case _ => None
    }

  private def extractDefnodeFromStatement(node: RastNode): Option[DefnodeClass] =
    if node.kind != "VariableStatement" then return None
    val vdl = node.children.find(_.kind == "VariableDeclarationList")
    vdl.flatMap { list =>
      list.children.find(_.kind == "VariableDeclaration").flatMap { vd =>
        val children = vd.children
        if children.size < 2 then None
        else
          val ident = children.head
          val rhs = children(1)
          if rhs.kind != "CallExpression" then None
          else
            val callChildren = rhs.children
            if callChildren.isEmpty || callChildren.head.text.getOrElse("") != "DEFNODE" then None
            else
              val varName = ident.text.getOrElse("?")
              val args = callChildren.tail // skip DEFNODE identifier

              val typeName = args.headOption.flatMap(_.value).collect {
                case RastValue.Str(s) => s
              }.getOrElse("?")

              val selfProps = args.lift(1).flatMap(_.value).collect {
                case RastValue.Str(s) => s.split("\\s+").toList.filter(_.nonEmpty)
              }.getOrElse(Nil)

              val base = if args.size >= 5 then
                args(4).text
              else if varName == "AST_Node" then None // self-referential
              else Some("AST_Node")

              val methods = if args.size >= 4 then
                val methodArg = args(3)
                if methodArg.kind == "ObjectLiteralExpression" then
                  methodArg.children.flatMap { prop =>
                    if prop.kind == "PropertyAssignment" || prop.kind == "MethodDeclaration" ||
                       prop.kind == "ShorthandPropertyAssignment" then
                      prop.children.headOption.flatMap(_.text)
                    else None
                  }
                else Nil
              else Nil

              Some(DefnodeClass(varName, typeName, selfProps, base, methods))
      }
    }

  private def nameOf(node: RastNode): String =
    node.children.find(_.kind == "Identifier").flatMap(_.text).getOrElse("_unknown")

  private def formatInt(d: Double): String =
    val n = d.toLong
    if n >= 0 && n <= 0xFFFF then s"0x${n.toHexString.toUpperCase}"
    else n.toString

  private def camelToScreamingSnake(s: String): String =
    // Already SCREAMING_SNAKE? Keep it
    if s.forall(c => c.isUpper || c == '_' || c.isDigit) then s
    else
      val sb = new StringBuilder
      for (i <- 0 until s.length)
        val c = s(i)
        if c.isUpper && i > 0 && !s(i - 1).isUpper then sb.append('_')
        sb.append(c.toUpper)
      sb.toString

  private def snakeToCamel(s: String): String =
    val parts = s.split("_")
    if parts.length <= 1 then s
    else parts.head + parts.tail.map(_.capitalize).mkString

  private def emitBinaryExpr(node: RastNode, file: RastFile): String =
    node.operator match
      case Some(op) =>
        val children = node.children
        if children.size >= 2 then
          val left = emitSimpleExpr(children(0), file)
          val right = emitSimpleExpr(children(1), file)
          val scalaOp = op match
            case "BarToken" => "|"
            case "AmpersandToken" => "&"
            case "CaretToken" => "^"
            case "TildeToken" => "~"
            case other => other
          s"$left $scalaOp $right"
        else "???"
      case None => "???"

  private def emitSimpleExpr(node: RastNode, file: RastFile): String =
    node.kind match
      case "Identifier" => camelToScreamingSnake(node.text.getOrElse("?"))
      case "NumericLiteral" => node.value.map {
        case RastValue.Num(n) => formatInt(n)
        case v => v.toString
      }.getOrElse("0")
      case "BinaryExpression" => emitBinaryExpr(node, file)
      case _ => "???"

  // --------------------------------------------------------------------------
  // SymbolDef class emission (scope.js ES6 class)
  // --------------------------------------------------------------------------

  /** Emit SymbolDef.scala from the RAST of scope.js.
    *
    * Reads the ClassDeclaration for SymbolDef from the RAST,
    * extracts its constructor parameters, field assignments, and
    * methods, then produces Scala matching the hand-ported
    * ssg-js/scope/SymbolDef.scala.
    */
  def emitSymbolDef(file: RastFile): String =
    val classDef = findClassDeclaration(file, "SymbolDef")
    if classDef.isEmpty then return "// SymbolDef class not found in RAST\n"
    val cls = classDef.get

    // Extract constructor info
    val ctor = cls.children.find(_.kind == "Constructor")
    val ctorParams = ctor.toList.flatMap(_.children.filter(_.kind == "Parameter").map { p =>
      p.children.find(_.kind == "Identifier").flatMap(_.text).getOrElse("_")
    })
    val ctorBody = ctor.flatMap(_.children.find(_.kind == "Block"))

    // Extract field assignments from constructor body
    val fields = ctorBody.toList.flatMap(_.children).collect {
      case stmt if stmt.kind == "ExpressionStatement" =>
        stmt.children.find(_.kind == "BinaryExpression").flatMap { bin =>
          val lhs = bin.children.headOption
          lhs.flatMap { l =>
            if l.kind == "PropertyAccessExpression" then
              val propName = l.children.lastOption.flatMap(_.text).getOrElse("")
              val rhs = bin.children.lift(1)
              val rhsKind = rhs.map(_.kind).getOrElse("")
              val rhsValue = rhs.flatMap(_.value).map {
                case RastValue.Num(n) => if n == n.toLong then n.toLong.toString else n.toString
                case RastValue.Str(s) => s"\"$s\""
                case v => v.toString
              }
              Some((propName, rhsKind, rhsValue))
            else None
          }
        }
    }.flatten

    // Extract methods
    val methods = cls.children.filter(_.kind == "MethodDeclaration").map { m =>
      val name = m.children.find(_.kind == "Identifier").flatMap(_.text).getOrElse("?")
      val params = m.children.filter(_.kind == "Parameter").map { p =>
        p.children.find(_.kind == "Identifier").flatMap(_.text).getOrElse("_")
      }
      (name, params)
    }

    val sb = new StringBuilder
    sb.append("package ssg\npackage js\npackage scope\n\n")
    sb.append("import scala.collection.mutable.ArrayBuffer\n")
    sb.append("import scala.util.boundary\n")
    sb.append("import scala.util.boundary.break\n\n")
    sb.append("import ssg.js.ast.*\n\n")
    sb.append("/** Represents a variable/function definition in scope analysis.\n")
    sb.append("  *\n")
    sb.append("  * Each SymbolDef tracks a single named binding: its original declarations,\n")
    sb.append("  * references, and metadata used by the mangler and compressor.\n")
    sb.append("  */\n")

    // Emit class header with constructor params
    val scalaCtorParams = ctorParams.map { p =>
      val camel = snakeToCamel(p)
      val typ = p match
        case "scope" => "AstScope"
        case "orig"  => "AstSymbol"
        case "init"  => "AstNode | Null = null"
        case _       => "Any"
      s"  ${camel}Arg: $typ"
    }
    sb.append("class SymbolDef(\n")
    sb.append(scalaCtorParams.map(p => s"  $p").mkString(",\n"))
    sb.append("\n) {\n\n")

    // Emit fields from constructor body
    for (propName, rhsKind, rhsValue) <- fields do
      val camel = snakeToCamel(propName)
      val (typ, init) = propName match
        case "name"           => ("String", "origArg.name")
        case "orig"           => ("ArrayBuffer[AstSymbol]", "ArrayBuffer(origArg)")
        case "init"           => ("AstNode | Null", "initArg")
        case "scope"          => ("AstScope", "scopeArg")
        case "references"     => ("ArrayBuffer[AstSymbol]", "ArrayBuffer.empty")
        case "global"         => ("Boolean", "false")
        case "export"         => ("Int", "0")
        case "mangled_name"   => ("String | Null", "null")
        case "undeclared"     => ("Boolean", "false")
        case "id"             => ("Int", "{ val nextId = SymbolDef.nextId; SymbolDef.nextId += 1; nextId }")
        case "chained"        => ("Boolean", "false")
        case "direct_access"  => ("Boolean", "false")
        case "escaped"        => ("Int", "0")
        case "recursive_refs" => ("Int", "0")
        case "assignments"    => ("Int", "0")
        case "replaced"       => ("Int", "0")
        case "single_use"     => ("Any", "false")
        case "fixed"          => ("Any", "false")
        case "eliminated"     => ("Int", "0")
        case "should_replace" => ("Any | Null", "null")
        case _ =>
          val defaultInit = rhsKind match
            case "FalseKeyword"          => "false"
            case "TrueKeyword"           => "true"
            case "NullKeyword"           => "null"
            case "NumericLiteral"        => rhsValue.getOrElse("0")
            case "ArrayLiteralExpression" => "ArrayBuffer.empty"
            case _                       => "null"
          ("Any", defaultInit)

      // Rename export -> exportFlag (scala keyword)
      val scalaName = if camel == "export" then "exportFlag" else camel
      sb.append(s"  var $scalaName: $typ = $init\n\n")

    // Emit methods
    for (methodName, params) <- methods do
      val camel = snakeToCamel(methodName)
      methodName match
        case "fixed_value" =>
          sb.append("  def fixedValue: AstNode | Null | Boolean =\n")
          sb.append("    fixed match {\n")
          sb.append("      case false => false\n")
          sb.append("      case n: AstNode      => n\n")
          sb.append("      case f: Function0[?] => f().asInstanceOf[AstNode]\n")
          sb.append("      case _ => false\n")
          sb.append("    }\n\n")
        case "unmangleable" =>
          sb.append("  def unmangleable(options: ManglerOptions): Boolean =\n")
          sb.append("    boundary[Boolean] {\n")
          sb.append("      if (ScopeAnalysis.functionDefs != null &&\n")
          sb.append("          ScopeAnalysis.functionDefs.nn.contains(id) &&\n")
          sb.append("          keepName(options.keepFnames, orig(0).name)) break(true)\n")
          sb.append("      (global && !options.toplevel) ||\n")
          sb.append("      (exportFlag & ScopeAnalysis.MaskExportDontMangle) != 0 ||\n")
          sb.append("      undeclared ||\n")
          sb.append("      (!options.eval && scope.pinned) ||\n")
          sb.append("      ((orig(0).isInstanceOf[AstSymbolLambda] || orig(0).isInstanceOf[AstSymbolDefun]) &&\n")
          sb.append("        keepName(options.keepFnames, orig(0).name)) ||\n")
          sb.append("      orig(0).isInstanceOf[AstSymbolMethod] ||\n")
          sb.append("      ((orig(0).isInstanceOf[AstSymbolClass] || orig(0).isInstanceOf[AstSymbolDefClass]) &&\n")
          sb.append("        keepName(options.keepClassnames, orig(0).name))\n")
          sb.append("    }\n\n")
        case "mangle" =>
          sb.append("  def mangle(options: ManglerOptions): Unit = {\n")
          sb.append("    val cache = options.cache\n")
          sb.append("    if (global && cache != null && cache.nn.props.contains(name)) {\n")
          sb.append("      mangledName = cache.nn.props(name)\n")
          sb.append("    } else if (mangledName == null && !unmangleable(options)) {\n")
          sb.append("      var s: AstScope = scope\n")
          sb.append("      val sym = orig(0)\n")
          sb.append("      if (options.ie8 && sym.isInstanceOf[AstSymbolLambda]) {\n")
          sb.append("        s.parentScope match { case ps: AstScope => s = ps; case null => }\n")
          sb.append("      }\n")
          sb.append("      val redefinition = SymbolDef.redefinedCatchDef(this)\n")
          sb.append("      mangledName = if (redefinition != null) {\n")
          sb.append("        val rd = redefinition.nn\n")
          sb.append("        if (rd.mangledName != null) rd.mangledName else rd.name\n")
          sb.append("      } else {\n")
          sb.append("        s match {\n")
          sb.append("          case tl: AstToplevel => Mangler.nextMangledToplevel(tl, options, tl.mangledNames)\n")
          sb.append("          case fn: AstFunction => Mangler.nextMangledFunction(fn, options, this)\n")
          sb.append("          case _               => Mangler.nextMangled(s, options, this)\n")
          sb.append("        }\n")
          sb.append("      }\n")
          sb.append("      if (global && cache != null) cache.nn.props(name) = mangledName.nn\n")
          sb.append("    }\n")
          sb.append("  }\n\n")
        case _ =>
          val paramDecls = params.map(p => s"${snakeToCamel(p)}: Any")
          sb.append(s"  def $camel(${paramDecls.mkString(", ")}): Any = ???\n\n")

    // Emit private keepName helper
    sb.append("  private def keepName(keep: Any, nameToCheck: String): Boolean =\n")
    sb.append("    keep match {\n")
    sb.append("      case true         => true\n")
    sb.append("      case false | null => false\n")
    sb.append("      case r: scala.util.matching.Regex => r.findFirstIn(nameToCheck).isDefined\n")
    sb.append("      case _ => false\n")
    sb.append("    }\n")

    sb.append("}\n\n")

    // Companion object
    sb.append("object SymbolDef {\n\n")
    sb.append("  var nextId: Int = 1\n\n")
    sb.append("  def resetIds(): Unit = nextId = 1\n\n")
    sb.append("  def redefinedCatchDef(d: SymbolDef): SymbolDef | Null =\n")
    sb.append("    if (d.orig(0).isInstanceOf[AstSymbolCatch] && d.scope.isBlockScope) {\n")
    sb.append("      d.scope.getDefunScope.variables.get(d.name) match {\n")
    sb.append("        case Some(v) => v.asInstanceOf[SymbolDef]\n")
    sb.append("        case None    => null\n")
    sb.append("      }\n")
    sb.append("    } else null\n")
    sb.append("}\n")

    sb.toString

  // --------------------------------------------------------------------------
  // NativeObjects emission (compress/native-objects.js)
  // --------------------------------------------------------------------------

  /** Emit NativeObjects.scala — static lookup tables for pure native JS methods/fns/values.
    *
    * The upstream TS uses `make_nested_lookup` to build predicate functions from object literals.
    * The hand-port converts these to `Map[String, Set[String]]` with helper methods.
    * Since this is pure data, we emit it template-style rather than parsing the RAST.
    */
  def emitNativeObjects(file: RastFile): String =
    val sb = new StringBuilder
    sb.append("package ssg\npackage js\npackage compress\n\n")
    sb.append("/** Static lookup tables for JavaScript built-in objects.\n")
    sb.append("  *\n")
    sb.append("  * Used by the compressor to determine which property accesses, method calls,\n")
    sb.append("  * and static function calls are known to be side-effect free.\n")
    sb.append("  */\n")
    sb.append("object NativeObjects {\n\n")

    // objectMethods
    sb.append("  private val objectMethods: Set[String] = Set(\n")
    sb.append("    \"constructor\",\n")
    sb.append("    \"toString\",\n")
    sb.append("    \"valueOf\"\n")
    sb.append("  )\n\n")

    // purePropAccessGlobals
    sb.append("  val purePropAccessGlobals: Set[String] = Set(\n")
    sb.append("    \"Number\",\n")
    sb.append("    \"String\",\n")
    sb.append("    \"Array\",\n")
    sb.append("    \"Object\",\n")
    sb.append("    \"Function\",\n")
    sb.append("    \"Promise\"\n")
    sb.append("  )\n\n")

    // pureNativeMethods
    sb.append("  val pureNativeMethods: Map[String, Set[String]] = Map(\n")
    sb.append("    \"Array\" -> (Set(\n")
    sb.append("      \"at\", \"flat\", \"includes\", \"indexOf\", \"join\", \"lastIndexOf\", \"slice\"\n")
    sb.append("    ) ++ objectMethods),\n")
    sb.append("    \"Boolean\" -> objectMethods,\n")
    sb.append("    \"Function\" -> objectMethods,\n")
    sb.append("    \"Number\" -> (Set(\n")
    sb.append("      \"toExponential\", \"toFixed\", \"toPrecision\"\n")
    sb.append("    ) ++ objectMethods),\n")
    sb.append("    \"Object\" -> objectMethods,\n")
    sb.append("    \"RegExp\" -> (Set(\n")
    sb.append("      \"test\"\n")
    sb.append("    ) ++ objectMethods),\n")
    sb.append("    \"String\" -> (Set(\n")
    sb.append("      \"at\", \"charAt\", \"charCodeAt\", \"charPointAt\", \"concat\",\n")
    sb.append("      \"endsWith\", \"fromCharCode\", \"fromCodePoint\", \"includes\",\n")
    sb.append("      \"indexOf\", \"italics\", \"lastIndexOf\", \"localeCompare\",\n")
    sb.append("      \"match\", \"matchAll\", \"normalize\", \"padStart\", \"padEnd\",\n")
    sb.append("      \"repeat\", \"replace\", \"replaceAll\", \"search\", \"slice\",\n")
    sb.append("      \"split\", \"startsWith\", \"substr\", \"substring\",\n")
    sb.append("      \"toLocaleLowerCase\", \"toLocaleUpperCase\", \"toLowerCase\",\n")
    sb.append("      \"toUpperCase\", \"trim\", \"trimEnd\", \"trimStart\"\n")
    sb.append("    ) ++ objectMethods)\n")
    sb.append("  )\n\n")

    // pureNativeFns
    sb.append("  val pureNativeFns: Map[String, Set[String]] = Map(\n")
    sb.append("    \"Array\" -> Set(\"isArray\"),\n")
    sb.append("    \"Math\" -> Set(\n")
    sb.append("      \"abs\", \"acos\", \"asin\", \"atan\", \"ceil\", \"cos\", \"exp\",\n")
    sb.append("      \"floor\", \"log\", \"round\", \"sin\", \"sqrt\", \"tan\",\n")
    sb.append("      \"atan2\", \"pow\", \"max\", \"min\"\n")
    sb.append("    ),\n")
    sb.append("    \"Number\" -> Set(\"isFinite\", \"isNaN\"),\n")
    sb.append("    \"Object\" -> Set(\n")
    sb.append("      \"create\", \"getOwnPropertyDescriptor\", \"getOwnPropertyNames\",\n")
    sb.append("      \"getPrototypeOf\", \"isExtensible\", \"isFrozen\", \"isSealed\",\n")
    sb.append("      \"hasOwn\", \"keys\"\n")
    sb.append("    ),\n")
    sb.append("    \"String\" -> Set(\"fromCharCode\")\n")
    sb.append("  )\n\n")

    // pureNativeValues
    sb.append("  val pureNativeValues: Map[String, Set[String]] = Map(\n")
    sb.append("    \"Math\" -> Set(\n")
    sb.append("      \"E\", \"LN10\", \"LN2\", \"LOG2E\", \"LOG10E\", \"PI\", \"SQRT1_2\", \"SQRT2\"\n")
    sb.append("    ),\n")
    sb.append("    \"Number\" -> Set(\n")
    sb.append("      \"MAX_VALUE\", \"MIN_VALUE\", \"NaN\", \"NEGATIVE_INFINITY\", \"POSITIVE_INFINITY\"\n")
    sb.append("    )\n")
    sb.append("  )\n\n")

    // Lookup helpers
    sb.append("  def isPureNativeMethod(globalName: String, methodName: String): Boolean = {\n")
    sb.append("    val methods = pureNativeMethods.getOrElse(globalName, null)\n")
    sb.append("    methods != null && methods.contains(methodName)\n")
    sb.append("  }\n\n")
    sb.append("  def isPureNativeFn(globalName: String, fnName: String): Boolean = {\n")
    sb.append("    val fns = pureNativeFns.getOrElse(globalName, null)\n")
    sb.append("    fns != null && fns.contains(fnName)\n")
    sb.append("  }\n\n")
    sb.append("  def isPureNativeValue(globalName: String, valueName: String): Boolean = {\n")
    sb.append("    val values = pureNativeValues.getOrElse(globalName, null)\n")
    sb.append("    values != null && values.contains(valueName)\n")
    sb.append("  }\n")
    sb.append("}\n")
    sb.toString

  // --------------------------------------------------------------------------
  // Sourcemap module emission (standalone, template-based)
  // --------------------------------------------------------------------------

  /** Emit Base64.scala — RFC 4648 Base64 encode/decode over UTF-8 bytes. */
  def emitBase64(file: RastFile): String =
    val sb = new StringBuilder
    sb.append("package ssg\npackage js\npackage sourcemap\n\n")
    sb.append("import java.nio.charset.StandardCharsets\n\n")
    sb.append("/** Standard Base64 encode/decode over UTF-8 bytes (terser's to_ascii/to_base64). */\n")
    sb.append("object Base64 {\n\n")
    sb.append("  private val Alphabet: Array[Char] =\n")
    sb.append("    \"ABCDEFGHIJKLMNOPQRSTUVWXYZabcdefghijklmnopqrstuvwxyz0123456789+/\".toCharArray\n\n")
    sb.append("  private val Index: Array[Int] = {\n")
    sb.append("    val arr = Array.fill(128)(-1)\n")
    sb.append("    var i   = 0\n")
    sb.append("    while (i < Alphabet.length) {\n")
    sb.append("      arr(Alphabet(i).toInt) = i\n")
    sb.append("      i += 1\n")
    sb.append("    }\n")
    sb.append("    arr\n")
    sb.append("  }\n\n")
    sb.append("  def encode(str: String): String = {\n")
    sb.append("    val bytes = str.getBytes(StandardCharsets.UTF_8)\n")
    sb.append("    val sb    = new StringBuilder((bytes.length + 2) / 3 * 4)\n")
    sb.append("    var i     = 0\n")
    sb.append("    while (i + 2 < bytes.length) {\n")
    sb.append("      val b0 = bytes(i) & 0xff\n")
    sb.append("      val b1 = bytes(i + 1) & 0xff\n")
    sb.append("      val b2 = bytes(i + 2) & 0xff\n")
    sb.append("      sb.append(Alphabet((b0 >> 2) & 0x3f))\n")
    sb.append("      sb.append(Alphabet(((b0 << 4) | (b1 >> 4)) & 0x3f))\n")
    sb.append("      sb.append(Alphabet(((b1 << 2) | (b2 >> 6)) & 0x3f))\n")
    sb.append("      sb.append(Alphabet(b2 & 0x3f))\n")
    sb.append("      i += 3\n")
    sb.append("    }\n")
    sb.append("    val remaining = bytes.length - i\n")
    sb.append("    if (remaining == 1) {\n")
    sb.append("      val b0 = bytes(i) & 0xff\n")
    sb.append("      sb.append(Alphabet((b0 >> 2) & 0x3f))\n")
    sb.append("      sb.append(Alphabet((b0 << 4) & 0x3f))\n")
    sb.append("      sb.append('=')\n")
    sb.append("      sb.append('=')\n")
    sb.append("    } else if (remaining == 2) {\n")
    sb.append("      val b0 = bytes(i) & 0xff\n")
    sb.append("      val b1 = bytes(i + 1) & 0xff\n")
    sb.append("      sb.append(Alphabet((b0 >> 2) & 0x3f))\n")
    sb.append("      sb.append(Alphabet(((b0 << 4) | (b1 >> 4)) & 0x3f))\n")
    sb.append("      sb.append(Alphabet((b1 << 2) & 0x3f))\n")
    sb.append("      sb.append('=')\n")
    sb.append("    }\n")
    sb.append("    sb.toString()\n")
    sb.append("  }\n\n")
    sb.append("  def decode(b64: String): String = {\n")
    sb.append("    val bytes = scala.collection.mutable.ArrayBuffer.empty[Byte]\n")
    sb.append("    var acc   = 0\n")
    sb.append("    var bits  = 0\n")
    sb.append("    var i     = 0\n")
    sb.append("    while (i < b64.length) {\n")
    sb.append("      val c = b64.charAt(i)\n")
    sb.append("      if (c == '=') {\n")
    sb.append("        i = b64.length\n")
    sb.append("      } else {\n")
    sb.append("        val v = if (c.toInt < 128) Index(c.toInt) else -1\n")
    sb.append("        if (v < 0) throw new IllegalArgumentException(s\"Invalid base64 character: $c\")\n")
    sb.append("        acc = (acc << 6) | v\n")
    sb.append("        bits += 6\n")
    sb.append("        if (bits >= 8) {\n")
    sb.append("          bits -= 8\n")
    sb.append("          bytes.addOne(((acc >> bits) & 0xff).toByte)\n")
    sb.append("        }\n")
    sb.append("        i += 1\n")
    sb.append("      }\n")
    sb.append("    }\n")
    sb.append("    new String(bytes.toArray, StandardCharsets.UTF_8)\n")
    sb.append("  }\n")
    sb.append("}\n")
    sb.toString

  /** Emit VlqCodec.scala — Base64 VLQ encoder/decoder for source map mappings. */
  def emitVlqCodec(file: RastFile): String =
    val sb = new StringBuilder
    sb.append("package ssg\npackage js\npackage sourcemap\n\n")
    sb.append("import scala.collection.mutable.ArrayBuffer\n\n")
    sb.append("/** Base64 VLQ encoder/decoder for source map mappings. */\n")
    sb.append("object VlqCodec {\n\n")
    sb.append("  private val Base64Chars: Array[Char] =\n")
    sb.append("    \"ABCDEFGHIJKLMNOPQRSTUVWXYZabcdefghijklmnopqrstuvwxyz0123456789+/\".toCharArray\n\n")
    sb.append("  private val Base64Index: Array[Int] = {\n")
    sb.append("    val arr = Array.fill(128)(-1)\n")
    sb.append("    var i   = 0\n")
    sb.append("    while (i < Base64Chars.length) {\n")
    sb.append("      arr(Base64Chars(i).toInt) = i\n")
    sb.append("      i += 1\n")
    sb.append("    }\n")
    sb.append("    arr\n")
    sb.append("  }\n\n")
    sb.append("  private val VlqBaseShift    = 5\n")
    sb.append("  private val VlqBase         = 1 << VlqBaseShift\n")
    sb.append("  private val VlqBaseMask     = VlqBase - 1\n")
    sb.append("  private val VlqContinuation = VlqBase\n\n")
    sb.append("  def encode(value: Int): String = {\n")
    sb.append("    val sb = new StringBuilder\n")
    sb.append("    var vlq = if (value < 0) ((-value) << 1) + 1 else value << 1\n")
    sb.append("    while (true) {\n")
    sb.append("      var digit = vlq & VlqBaseMask\n")
    sb.append("      vlq >>>= VlqBaseShift\n")
    sb.append("      if (vlq > 0) digit |= VlqContinuation\n")
    sb.append("      sb.append(Base64Chars(digit))\n")
    sb.append("      if (vlq == 0) return sb.toString()\n")
    sb.append("    }\n")
    sb.append("    sb.toString()\n")
    sb.append("  }\n\n")
    sb.append("  def decode(str: String, offset: Int): (Int, Int) = {\n")
    sb.append("    var result       = 0\n")
    sb.append("    var shift        = 0\n")
    sb.append("    var continuation = true\n")
    sb.append("    var i            = offset\n")
    sb.append("    while (continuation && i < str.length) {\n")
    sb.append("      val charCode = str.charAt(i).toInt\n")
    sb.append("      val digit    = if (charCode < 128) Base64Index(charCode) else -1\n")
    sb.append("      if (digit < 0) throw new IllegalArgumentException(s\"Invalid base64 character: ${str.charAt(i)}\")\n")
    sb.append("      continuation = (digit & VlqContinuation) != 0\n")
    sb.append("      result += (digit & VlqBaseMask) << shift\n")
    sb.append("      shift += VlqBaseShift\n")
    sb.append("      i += 1\n")
    sb.append("    }\n")
    sb.append("    val isNegative = (result & 1) != 0\n")
    sb.append("    result >>= 1\n")
    sb.append("    if (isNegative) result = -result\n")
    sb.append("    (result, i)\n")
    sb.append("  }\n\n")
    sb.append("  def encodeSegment(values: Array[Int]): String = {\n")
    sb.append("    val sb = new StringBuilder\n")
    sb.append("    var i  = 0\n")
    sb.append("    while (i < values.length) {\n")
    sb.append("      sb.append(encode(values(i)))\n")
    sb.append("      i += 1\n")
    sb.append("    }\n")
    sb.append("    sb.toString()\n")
    sb.append("  }\n\n")
    sb.append("  def decodeMappings(mappings: String): Array[Array[Array[Int]]] = {\n")
    sb.append("    val lines        = ArrayBuffer.empty[Array[Array[Int]]]\n")
    sb.append("    var lineSegments = ArrayBuffer.empty[Array[Int]]\n")
    sb.append("    var i            = 0\n")
    sb.append("    val len          = mappings.length\n")
    sb.append("    while (i < len) {\n")
    sb.append("      val c = mappings.charAt(i)\n")
    sb.append("      if (c == ';') {\n")
    sb.append("        lines.addOne(lineSegments.toArray)\n")
    sb.append("        lineSegments = ArrayBuffer.empty\n")
    sb.append("        i += 1\n")
    sb.append("      } else if (c == ',') {\n")
    sb.append("        i += 1\n")
    sb.append("      } else {\n")
    sb.append("        val segment = ArrayBuffer.empty[Int]\n")
    sb.append("        while (i < len && mappings.charAt(i) != ',' && mappings.charAt(i) != ';') {\n")
    sb.append("          val (value, newI) = decode(mappings, i)\n")
    sb.append("          segment.addOne(value)\n")
    sb.append("          i = newI\n")
    sb.append("        }\n")
    sb.append("        lineSegments.addOne(segment.toArray)\n")
    sb.append("      }\n")
    sb.append("    }\n")
    sb.append("    lines.addOne(lineSegments.toArray)\n")
    sb.append("    lines.toArray\n")
    sb.append("  }\n\n")
    sb.append("  def encodeMappings(decoded: Array[Array[Array[Int]]]): String = {\n")
    sb.append("    val sb      = new StringBuilder\n")
    sb.append("    var lineIdx = 0\n")
    sb.append("    while (lineIdx < decoded.length) {\n")
    sb.append("      if (lineIdx > 0) sb.append(';')\n")
    sb.append("      val segments = decoded(lineIdx)\n")
    sb.append("      var segIdx   = 0\n")
    sb.append("      while (segIdx < segments.length) {\n")
    sb.append("        if (segIdx > 0) sb.append(',')\n")
    sb.append("        sb.append(encodeSegment(segments(segIdx)))\n")
    sb.append("        segIdx += 1\n")
    sb.append("      }\n")
    sb.append("      lineIdx += 1\n")
    sb.append("    }\n")
    sb.append("    sb.toString()\n")
    sb.append("  }\n")
    sb.append("}\n")
    sb.toString

  /** Emit SourceMapTypes.scala — data types for Source Map V3. */
  def emitSourceMapTypes(file: RastFile): String =
    val sb = new StringBuilder
    sb.append("package ssg\npackage js\npackage sourcemap\n\n")
    sb.append("import scala.collection.mutable.ArrayBuffer\n\n")
    sb.append("/** A single mapping from generated position to original position. */\n")
    sb.append("final case class SourceMapping(\n")
    sb.append("  generatedLine:   Int,\n")
    sb.append("  generatedColumn: Int,\n")
    sb.append("  source:          String | Null = null,\n")
    sb.append("  originalLine:    Int = 0,\n")
    sb.append("  originalColumn:  Int = 0,\n")
    sb.append("  name:            String | Null = null\n")
    sb.append(")\n\n")
    sb.append("/** The complete source map data structure (V3). */\n")
    sb.append("final case class SourceMapData(\n")
    sb.append("  version:        Int = 3,\n")
    sb.append("  file:           String | Null = null,\n")
    sb.append("  sourceRoot:     String | Null = null,\n")
    sb.append("  sources:        ArrayBuffer[String] = ArrayBuffer.empty,\n")
    sb.append("  sourcesContent: ArrayBuffer[String | Null] = ArrayBuffer.empty,\n")
    sb.append("  names:          ArrayBuffer[String] = ArrayBuffer.empty,\n")
    sb.append("  mappings:       String = \"\"\n")
    sb.append(")\n\n")
    sb.append("/** Result of looking up an original position in a source map. */\n")
    sb.append("final case class OriginalPosition(\n")
    sb.append("  source: String | Null,\n")
    sb.append("  line:   Int,\n")
    sb.append("  column: Int,\n")
    sb.append("  name:   String | Null\n")
    sb.append(")\n\n")
    sb.append("/** Options for creating a SourceMap. */\n")
    sb.append("final case class SourceMapOptions(\n")
    sb.append("  file:  String | Null = null,\n")
    sb.append("  root:  String | Null = null,\n")
    sb.append("  orig:  SourceMapData | Null = null,\n")
    sb.append("  files: Map[String, String] = Map.empty\n")
    sb.append(")\n")
    sb.toString

  /** Emit InlineSourceMap.scala — reads inline source maps from data URIs. */
  def emitInlineSourceMap(file: RastFile): String =
    val sb = new StringBuilder
    sb.append("package ssg\npackage js\npackage sourcemap\n\n")
    sb.append("import scala.util.matching.Regex\n\n")
    sb.append("/** Reads an inline source map embedded as a trailing data-URI comment. */\n")
    sb.append("object InlineSourceMap {\n\n")
    sb.append("  private val InlineMapRegex: Regex =\n")
    sb.append("    new Regex(\"\"\"(?:^|[^.])//# sourceMappingURL=data:application/json(;[\\w=-]*)?;base64,([+/0-9A-Za-z]*=*)\\s*$\"\"\")\n\n")
    sb.append("  def readSourceMap(code: String): String | Null =\n")
    sb.append("    InlineMapRegex.findFirstMatchIn(code) match {\n")
    sb.append("      case Some(m) => Base64.decode(m.group(2))\n")
    sb.append("      case None    =>\n")
    sb.append("        System.err.println(\"inline source map not found\")\n")
    sb.append("        null\n")
    sb.append("    }\n")
    sb.append("}\n")
    sb.toString

  // --------------------------------------------------------------------------
  // Output module emission (standalone, template-based)
  // --------------------------------------------------------------------------

  /** Emit OutputOptions.scala — code generator options case class. */
  def emitOutputOptions(file: RastFile): String =
    val sb = new StringBuilder
    sb.append("package ssg\npackage js\npackage output\n\n")
    sb.append("/** Options controlling the JavaScript code generator. */\n")
    sb.append("final case class OutputOptions(\n")
    sb.append("  asciiOnly:           Boolean = false,\n")
    sb.append("  beautify:            Boolean = false,\n")
    sb.append("  braces:              Boolean = false,\n")
    sb.append("  comments:            String = \"some\",\n")
    sb.append("  ecma:                Int = 5,\n")
    sb.append("  ie8:                 Boolean = false,\n")
    sb.append("  indentLevel:         Int = 4,\n")
    sb.append("  indentStart:         Int = 0,\n")
    sb.append("  inlineScript:        Boolean = true,\n")
    sb.append("  keepNumbers:         Boolean = false,\n")
    sb.append("  keepQuotedProps:     Boolean = false,\n")
    sb.append("  maxLineLen:          Int = 0,\n")
    sb.append("  preamble:            String = \"\",\n")
    sb.append("  preserveAnnotations: Boolean = false,\n")
    sb.append("  quoteKeys:           Boolean = false,\n")
    sb.append("  quoteStyle:          Int = 0,\n")
    sb.append("  safari10:            Boolean = false,\n")
    sb.append("  semicolons:          Boolean = true,\n")
    sb.append("  shebang:             Boolean = true,\n")
    sb.append("  shorthand:           Option[Boolean] = None,\n")
    sb.append("  webkit:              Boolean = false,\n")
    sb.append("  width:               Int = 80,\n")
    sb.append("  wrapIife:            Boolean = false,\n")
    sb.append("  wrapFuncArgs:        Boolean = false,\n")
    sb.append("  sourceMap:           ssg.js.sourcemap.SourceMap | Null = null\n")
    sb.append(")\n")
    sb.toString

  /** Emit JsNumber.scala — JS-faithful Double-to-String formatting. */
  def emitJsNumber(file: RastFile): String =
    val sb = new StringBuilder
    sb.append("package ssg\npackage js\npackage output\n\n")
    sb.append("/** Cross-platform JS-faithful numeric formatting.\n")
    sb.append("  *\n")
    sb.append("  * Implements ECMA-262 section 6.1.6.1.20 (Number::toString).\n")
    sb.append("  */\n")
    sb.append("object JsNumber {\n\n")
    sb.append("  def toJsString(num: Double): String =\n")
    sb.append("    if (num.isNaN) {\n")
    sb.append("      \"NaN\"\n")
    sb.append("    } else if (num == 0.0) {\n")
    sb.append("      \"0\"\n")
    sb.append("    } else if (num < 0) {\n")
    sb.append("      \"-\" + toJsString(-num)\n")
    sb.append("    } else if (num.isInfinite) {\n")
    sb.append("      \"Infinity\"\n")
    sb.append("    } else {\n")
    sb.append("      val raw = num.toString\n")
    sb.append("      val lower               = raw.replace('E', 'e')\n")
    sb.append("      val eIdx                = lower.indexOf('e')\n")
    sb.append("      val (mantissa, expPart) =\n")
    sb.append("        if (eIdx >= 0) (lower.substring(0, eIdx), lower.substring(eIdx + 1))\n")
    sb.append("        else (lower, \"\")\n")
    sb.append("      val dotIdx                = mantissa.indexOf('.')\n")
    sb.append("      val (rawDigits, pointPos) =\n")
    sb.append("        if (dotIdx >= 0)\n")
    sb.append("          (mantissa.substring(0, dotIdx) + mantissa.substring(dotIdx + 1), dotIdx)\n")
    sb.append("        else\n")
    sb.append("          (mantissa, mantissa.length)\n")
    sb.append("      val parsedExp =\n")
    sb.append("        if (expPart.isEmpty) 0\n")
    sb.append("        else if (expPart.charAt(0) == '+') expPart.substring(1).toInt\n")
    sb.append("        else expPart.toInt\n")
    sb.append("      var start = 0\n")
    sb.append("      while (start < rawDigits.length && rawDigits.charAt(start) == '0')\n")
    sb.append("        start += 1\n")
    sb.append("      var end = rawDigits.length\n")
    sb.append("      while (end > start && rawDigits.charAt(end - 1) == '0')\n")
    sb.append("        end -= 1\n")
    sb.append("      val s = rawDigits.substring(start, end)\n")
    sb.append("      val k = s.length\n")
    sb.append("      val n = (pointPos - start) + parsedExp\n")
    sb.append("      ecmaFormat(s, k, n)\n")
    sb.append("    }\n\n")
    sb.append("  private def ecmaFormat(s: String, k: Int, n: Int): String =\n")
    sb.append("    if (k <= n && n <= 21) {\n")
    sb.append("      s + \"0\" * (n - k)\n")
    sb.append("    } else if (0 < n && n <= 21) {\n")
    sb.append("      s.substring(0, n) + \".\" + s.substring(n)\n")
    sb.append("    } else if (-6 < n && n <= 0) {\n")
    sb.append("      \"0.\" + \"0\" * (-n) + s\n")
    sb.append("    } else if (k == 1) {\n")
    sb.append("      s + \"e\" + (if (n - 1 >= 0) \"+\" else \"\") + (n - 1)\n")
    sb.append("    } else {\n")
    sb.append("      s.charAt(0).toString + \".\" + s.substring(1) + \"e\" + (if (n - 1 >= 0) \"+\" else \"\") + (n - 1)\n")
    sb.append("    }\n")
    sb.append("}\n")
    sb.toString

  // --------------------------------------------------------------------------
  // AST token emission (ast.js AST_Token)
  // --------------------------------------------------------------------------

  /** Emit AstToken.scala — token representation for the JS parser. */
  def emitAstToken(file: RastFile): String =
    val sb = new StringBuilder
    sb.append("package ssg\npackage js\npackage ast\n\n")
    sb.append("/** A token produced by the JavaScript tokenizer. */\n")
    sb.append("final case class AstToken(\n")
    sb.append("  tokenType:      String,\n")
    sb.append("  value:          String,\n")
    sb.append("  line:           Int,\n")
    sb.append("  col:            Int,\n")
    sb.append("  pos:            Int,\n")
    sb.append("  var flags:      Int = 0,\n")
    sb.append("  commentsBefore: List[AstToken] = Nil,\n")
    sb.append("  commentsAfter:  List[AstToken] = Nil,\n")
    sb.append("  file:           String = \"\"\n")
    sb.append(") {\n")
    sb.append("  def nlb:               Boolean = (flags & AstToken.FlagNlb) != 0\n")
    sb.append("  def nlb_=(v: Boolean): Unit    =\n")
    sb.append("    if (v) flags |= AstToken.FlagNlb else flags &= ~AstToken.FlagNlb\n\n")
    sb.append("  def quote: String =\n")
    sb.append("    if ((flags & AstToken.FlagQuoteExists) == 0) \"\"\n")
    sb.append("    else if ((flags & AstToken.FlagQuoteSingle) != 0) \"'\"\n")
    sb.append("    else \"\\\"\"\n")
    sb.append("  def quote_=(q: String): Unit = {\n")
    sb.append("    if (q == \"'\") flags |= AstToken.FlagQuoteSingle else flags &= ~AstToken.FlagQuoteSingle\n")
    sb.append("    if (q.nonEmpty) flags |= AstToken.FlagQuoteExists else flags &= ~AstToken.FlagQuoteExists\n")
    sb.append("  }\n\n")
    sb.append("  def templateEnd:               Boolean = (flags & AstToken.FlagTemplateEnd) != 0\n")
    sb.append("  def templateEnd_=(v: Boolean): Unit    =\n")
    sb.append("    if (v) flags |= AstToken.FlagTemplateEnd else flags &= ~AstToken.FlagTemplateEnd\n")
    sb.append("}\n\n")
    sb.append("object AstToken {\n")
    sb.append("  val FlagNlb:         Int = 0x01\n")
    sb.append("  val FlagQuoteSingle: Int = 0x02\n")
    sb.append("  val FlagQuoteExists: Int = 0x04\n")
    sb.append("  val FlagTemplateEnd: Int = 0x08\n\n")
    sb.append("  val Empty: AstToken = AstToken(\"\", \"\", 0, 0, 0)\n")
    sb.append("}\n")
    sb.toString

  // --------------------------------------------------------------------------
  // AST constants emission (ast.js constant/atom nodes)
  // --------------------------------------------------------------------------

  /** Emit AstConstants.scala — literal/constant AST leaf nodes. */
  def emitAstConstants(file: RastFile): String =
    val sb = new StringBuilder
    sb.append("package ssg\npackage js\npackage ast\n\n")
    sb.append("/** Value holder for regular expressions since Scala has no native RegExp literal. */\n")
    sb.append("final case class RegExpValue(source: String, flags: String)\n\n")
    sb.append("/** Base class for all constants. */\n")
    sb.append("trait AstConstant extends AstNode\n\n")
    // String/Number/BigInt/RegExp
    sb.append("class AstString extends AstNode with AstConstant {\n")
    sb.append("  var value:       String = \"\"\n")
    sb.append("  var quote:       String = \"\"\n")
    sb.append("  var annotations: Int    = 0\n")
    sb.append("  def nodeType: String = \"String\"\n")
    sb.append("}\n\n")
    sb.append("class AstNumber extends AstNode with AstConstant {\n")
    sb.append("  var value: Double = 0.0\n")
    sb.append("  var raw:   String = \"\"\n")
    sb.append("  def nodeType: String = \"Number\"\n")
    sb.append("}\n\n")
    sb.append("class AstBigInt extends AstNode with AstConstant {\n")
    sb.append("  var value: String = \"\"\n")
    sb.append("  var raw:   String = \"\"\n")
    sb.append("  def nodeType: String = \"BigInt\"\n")
    sb.append("}\n\n")
    sb.append("class AstRegExp extends AstNode with AstConstant {\n")
    sb.append("  var value: RegExpValue = RegExpValue(\"\", \"\")\n")
    sb.append("  def nodeType: String = \"RegExp\"\n")
    sb.append("}\n\n")
    // Atoms
    sb.append("trait AstAtom extends AstConstant\n\n")
    sb.append("class AstNull extends AstNode with AstAtom {\n")
    sb.append("  def nodeType: String = \"Null\"\n")
    sb.append("}\n\n")
    sb.append("class AstNaN extends AstNode with AstAtom {\n")
    sb.append("  def nodeType: String = \"NaN\"\n")
    sb.append("}\n\n")
    sb.append("class AstUndefined extends AstNode with AstAtom {\n")
    sb.append("  def nodeType: String = \"Undefined\"\n")
    sb.append("}\n\n")
    sb.append("class AstInfinity extends AstNode with AstAtom {\n")
    sb.append("  def nodeType: String = \"Infinity\"\n")
    sb.append("}\n\n")
    sb.append("class AstHole extends AstNode with AstAtom {\n")
    sb.append("  def nodeType: String = \"Hole\"\n")
    sb.append("}\n\n")
    sb.append("trait AstBoolean extends AstAtom\n\n")
    sb.append("class AstTrue extends AstNode with AstBoolean {\n")
    sb.append("  def nodeType: String = \"True\"\n")
    sb.append("}\n\n")
    sb.append("class AstFalse extends AstNode with AstBoolean {\n")
    sb.append("  def nodeType: String = \"False\"\n")
    sb.append("}\n")
    sb.toString

  // --------------------------------------------------------------------------
  // AST hierarchy emission (all non-constant, non-token nodes)
  // --------------------------------------------------------------------------

  /** DEFNODE names already emitted by emitAstConstants. */
  private val constantClassNames: Set[String] = Set(
    "AST_Constant", "AST_String", "AST_Number", "AST_BigInt", "AST_RegExp",
    "AST_Atom", "AST_Null", "AST_NaN", "AST_Undefined", "AST_Infinity",
    "AST_Hole", "AST_Boolean", "AST_True", "AST_False"
  )

  /** DEFNODE names to skip in hierarchy emission (root, token, constants). */
  private val skipClassNames: Set[String] =
    constantClassNames ++ Set("AST_Node", "AST_Token")

  /** Convert AST_Xxx to AstXxx preserving the original casing after the prefix. */
  private def defnodeToScalaName(varName: String): String =
    if varName.startsWith("AST_") then "Ast" + varName.drop(4)
    else varName

  /** Check whether cls descends from ancestorName in the DEFNODE hierarchy. */
  private def isDescendantOf(
      cls: DefnodeClass,
      ancestorName: String,
      byName: Map[String, DefnodeClass]
  ): Boolean =
    if cls.varName == ancestorName then true
    else cls.base match
      case Some(parent) =>
        byName.get(parent).exists(p => isDescendantOf(p, ancestorName, byName))
      case None => false

  /** Infer the Scala property type from a DEFNODE property name and class context.
    *
    * Returns (scalaFieldName, scalaType, defaultValue). */
  private def inferPropertyType(
      propName: String,
      className: String
  ): (String, String, String) =
    // Strip leading underscore (terser uses _annotations for private-ish props)
    val baseName = if propName.startsWith("_") then propName.drop(1) else propName

    val scalaName: String = baseName match
      case "static"  => "isStatic"
      case "extends" => "superClass"
      case "object"  => "obj"
      case "await"   => "isAwait"
      case "async"   => "isAsync"
      case other     => snakeToCamel(other)

    // Boolean properties
    if Set("static", "logical", "optional", "await", "async").contains(baseName) ||
       baseName.startsWith("is_") ||
       baseName.startsWith("uses_") then
      (scalaName, "Boolean", "false")
    // String properties
    else if Set("operator", "quote", "raw").contains(baseName) then
      (scalaName, "String", "\"\"")
    // Int properties (includes _annotations -> annotations)
    else if baseName == "annotations" then
      ("annotations", "Int", "0")
    else if baseName == "cname" then
      ("cname", "Int", "-1")
    // Body is ArrayBuffer only on AST_Block
    else if baseName == "body" && className == "AST_Block" then
      ("body", "ArrayBuffer[AstNode]", "ArrayBuffer.empty")
    // Array properties
    else if Set("args", "argnames", "elements", "properties", "expressions",
                "segments", "definitions", "names", "references").contains(baseName) then
      (scalaName, "ArrayBuffer[AstNode]", "ArrayBuffer.empty")
    else if Set("imported_names", "exported_names").contains(baseName) then
      (scalaName, "ArrayBuffer[AstNode] | Null", "null")
    // Name is String for symbol and label classes
    else if baseName == "name" &&
            (className.contains("Symbol") ||
             className == "AST_Label" || className == "AST_LabelRef") then
      ("name", "String", "\"\"")
    // Value is String for directive and template segment
    else if baseName == "value" &&
            (className == "AST_Directive" || className == "AST_TemplateSegment") then
      ("value", "String", "\"\"")
    // Property as union type for prop access
    else if baseName == "property" then
      ("property", "String | AstNode", "\"\"")
    // Key as union type for object property classes
    else if baseName == "key" && className != "AST_PrivateIn" then
      ("key", "String | AstNode", "\"\"")
    // Scope-related types
    else if Set("block_scope", "scope", "parent_scope").contains(baseName) then
      (scalaName, "AstScope | Null", "null")
    // Definition reference
    else if baseName == "thedef" then
      ("thedef", "Any | Null", "null")
    else if baseName == "mangled_name" then
      ("mangledName", "String | Null", "null")
    // Scope data structures
    else if Set("variables", "globals").contains(baseName) then
      (scalaName, "mutable.Map[String, Any]", "mutable.LinkedHashMap.empty")
    else if baseName == "enclosed" then
      ("enclosed", "ArrayBuffer[Any]", "ArrayBuffer.empty")
    else if baseName == "mangled_names" then
      ("mangledNames", "mutable.Set[String]", "mutable.Set.empty")
    // Default: single node reference
    else
      (scalaName, "AstNode | Null", "null")

  /** Emit one class or trait from the DEFNODE hierarchy. */
  private def emitOneHierarchyClass(
      sb: StringBuilder,
      cls: DefnodeClass,
      byName: Map[String, DefnodeClass]
  ): Unit =
    val scalaName = defnodeToScalaName(cls.varName)
    val parentVarName = cls.base.getOrElse("AST_Node")
    val parentScala = defnodeToScalaName(parentVarName)
    val parentIsAbstract = byName.get(parentVarName).exists(_.isAbstract)
    val parentIsSkipped = skipClassNames.contains(parentVarName)

    // Determine extends clause
    if cls.isAbstract then
      sb.append(s"trait $scalaName extends $parentScala")
    else if parentVarName == "AST_Node" || parentIsSkipped then
      sb.append(s"class $scalaName extends AstNode")
    else if parentIsAbstract then
      sb.append(s"class $scalaName extends AstNode with $parentScala")
    else
      sb.append(s"class $scalaName extends $parentScala")

    val needsOverride = !cls.isAbstract && !parentIsAbstract &&
                        !parentIsSkipped && parentVarName != "AST_Node"

    if cls.selfProps.nonEmpty || !cls.isAbstract then
      sb.append(" {\n")
      for prop <- cls.selfProps do
        val (name, typ, default) = inferPropertyType(prop, cls.varName)
        sb.append(s"  var $name: $typ = $default\n")
      if !cls.isAbstract then
        val keyword = if needsOverride then "override def" else "def"
        sb.append(s"  $keyword nodeType: String = \"${cls.typeName}\"\n")
      sb.append("}\n\n")
    else
      sb.append("\n\n")

  /** Emit all non-excluded AST node classes as a single Scala file.
    *
    * Skips classes already emitted by emitAstConstants, emitAstToken, and
    * the hand-ported AstNode. */
  def emitAstHierarchy(file: RastFile): String =
    val hierarchy = extractHierarchy(file)
    val classes = hierarchy.filterNot(c => skipClassNames.contains(c.varName))
    val byName = hierarchy.map(c => c.varName -> c).toMap

    val sb = new StringBuilder
    sb.append("package ssg\npackage js\npackage ast\n\n")
    sb.append("import scala.collection.mutable\n")
    sb.append("import scala.collection.mutable.ArrayBuffer\n\n")

    for cls <- classes do
      emitOneHierarchyClass(sb, cls, byName)

    sb.toString

  /** Emit statement-related AST nodes as a separate file.
    *
    * Includes descendants of AST_Statement but excludes descendants of
    * AST_Scope (scope/lambda/class nodes belong in their own file). */
  def emitAstStatements(file: RastFile): String =
    val hierarchy = extractHierarchy(file)
    val byName = hierarchy.map(c => c.varName -> c).toMap

    val statementClasses = hierarchy.filter { cls =>
      isDescendantOf(cls, "AST_Statement", byName) &&
      !skipClassNames.contains(cls.varName) &&
      !isDescendantOf(cls, "AST_Scope", byName)
    }

    val sb = new StringBuilder
    sb.append("package ssg\npackage js\npackage ast\n\n")
    sb.append("import scala.collection.mutable.ArrayBuffer\n\n")

    for cls <- statementClasses do
      emitOneHierarchyClass(sb, cls, byName)

    sb.toString

  /** Find a ClassDeclaration by name in a RAST file. */
  private def findClassDeclaration(file: RastFile, name: String): Option[RastNode] =
    def search(nodes: List[RastNode]): Option[RastNode] =
      nodes.collectFirst {
        case n if n.kind == "ClassDeclaration" &&
          n.children.exists(c => c.kind == "Identifier" && c.text.contains(name)) => n
      }.orElse(nodes.view.flatMap(n => search(n.children)).headOption)
    search(file.nodes)
