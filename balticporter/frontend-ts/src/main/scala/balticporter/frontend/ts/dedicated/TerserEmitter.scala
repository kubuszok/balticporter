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

  /** A class extracted from a single `DEFNODE(type, props, ctor, methods, base)` call. */
  final case class DefnodeClass(
      varName: String,      // the JS variable name, e.g. "AST_Node"
      typeName: String,     // the TYPE string, e.g. "Node"
      selfProps: List[String],
      base: Option[String], // parent variable name, None for root
      methods: List[String],
  ):
    /** Whether any known subclasses exist (set after hierarchy is built). */
    var isAbstract: Boolean = false

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
  // Private helpers
  // --------------------------------------------------------------------------

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
