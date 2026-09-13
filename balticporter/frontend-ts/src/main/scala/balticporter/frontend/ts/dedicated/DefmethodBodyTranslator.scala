package balticporter.frontend.ts.dedicated

import balticporter.frontend.ts.{RastFile, RastNode, RastValue}
import scala.collection.mutable

/** Translates DEFMETHOD and prototype-assignment function bodies from RAST
  * to Scala source text.
  *
  * Three tiers:
  *   - Simple bodies (return literal, return field access, identifier refs)
  *   - Moderate bodies (conditionals, instanceof, property comparisons)
  *   - Complex bodies are refused with a counted marker
  *
  * Also extracts prototype assignments (`AST_X.prototype.method = fn`) as a
  * sibling of the existing DEFMETHOD extraction. Both forms produce the same
  * `DefmethodEntry` representation. */
object DefmethodBodyTranslator:

  /** Result of translating one method body. */
  final case class TranslationResult(
      scalaBody: String,
      isComplete: Boolean,
      refusalCount: Int,
      refusalReasons: List[String],
  )

  /** A prototype assignment extracted from a RAST file.
    *
    * The JS pattern is:
    * {{{
    * AST_X.prototype.method_name = function(params) { body };
    * AST_X.prototype.method_name = identifier_ref;
    * }}}
    */
  def extractPrototypeAssignments(file: RastFile): List[TerserEmitter.DefmethodEntry] =
    val result = mutable.ListBuffer.empty[TerserEmitter.DefmethodEntry]
    for node <- file.nodes do
      extractProtoAssignment(node).foreach(result += _)
    result.toList

  /** Translate a DEFMETHOD/prototype body from its RAST Block node to Scala. */
  def translateBody(
      entry: TerserEmitter.DefmethodEntry,
      hierarchy: List[TerserEmitter.DefnodeClass],
      indent: String = "    ",
  ): TranslationResult =
    val ctx = new BodyContext(entry, hierarchy, indent)
    ctx.translateBlock(entry.bodyNode)
    TranslationResult(
      scalaBody = ctx.result(),
      isComplete = ctx.refusals.isEmpty,
      refusalCount = ctx.refusals.size,
      refusalReasons = ctx.refusals.toList,
    )

  /** Emit a complete DEFMETHOD family as Scala method declarations.
    *
    * Groups entries by class and emits each as an override chain. */
  def emitDefmethodFamily(
      familyName: String,
      entries: List[TerserEmitter.DefmethodEntry],
      hierarchy: List[TerserEmitter.DefnodeClass],
  ): String =
    val sb = new StringBuilder
    val grouped = entries.groupBy(_.className)
    val sortedClasses = grouped.keys.toList.sorted

    for cls <- sortedClasses do
      val methods = grouped(cls)
      val scalaClass = astVarToScalaName(cls)
      sb.append(s"// $scalaClass.$familyName\n")
      for m <- methods do
        val result = translateBody(m, hierarchy)
        val scalaMethodName = snakeToCamel(m.methodName)
        val paramDecls = m.params.map(p => s"${snakeToCamel(p)}: Any")
        val paramStr = if paramDecls.isEmpty then "" else s"(${paramDecls.mkString(", ")})"
        val statusComment = if result.isComplete then "" else s" /* ${result.refusalCount} untranslated */"
        sb.append(s"  def $scalaMethodName$paramStr: Any =$statusComment\n")
        sb.append(result.scalaBody)
        sb.append("\n")

    sb.toString

  /** Summary statistics for a batch of translations. */
  final case class TranslationStats(
      total: Int,
      fullyTranslated: Int,
      partiallyTranslated: Int,
      refused: Int,
      totalRefusalCount: Int,
  )

  /** Compute translation statistics for a list of entries. */
  def computeStats(
      entries: List[TerserEmitter.DefmethodEntry],
      hierarchy: List[TerserEmitter.DefnodeClass],
  ): TranslationStats =
    var full = 0
    var partial = 0
    var refused = 0
    var totalRefusals = 0
    for e <- entries do
      val r = translateBody(e, hierarchy)
      totalRefusals += r.refusalCount
      if r.isComplete then full += 1
      else if r.refusalCount <= 2 then partial += 1
      else refused += 1
    TranslationStats(entries.size, full, partial, refused, totalRefusals)

  // --------------------------------------------------------------------------
  // Private: prototype assignment extraction
  // --------------------------------------------------------------------------

  private def extractProtoAssignment(node: RastNode): Option[TerserEmitter.DefmethodEntry] =
    if node.kind != "ExpressionStatement" then None
    else node.children.find(_.kind == "BinaryExpression").flatMap { bin =>
      if bin.operator.getOrElse("") != "EqualsToken" || bin.children.size < 2 then None
      else
        val lhs = bin.children.head
        val rhs = bin.children(1)
        parseProtoLhs(lhs).flatMap { case (className, methodName) =>
          parseProtoRhs(className, methodName, rhs)
        }
    }

  private def parseProtoLhs(lhs: RastNode): Option[(String, String)] =
    if lhs.kind != "PropertyAccessExpression" || lhs.children.size < 2 then None
    else
      val methodName = lhs.children.last.text.getOrElse("")
      val protoAccess = lhs.children.head
      if protoAccess.kind != "PropertyAccessExpression" || protoAccess.children.size < 2 then None
      else if protoAccess.children.last.text.getOrElse("") != "prototype" then None
      else
        val className = protoAccess.children.head.text.getOrElse("")
        if className.isEmpty || methodName.isEmpty then None
        else Some((className, methodName))

  private def parseProtoRhs(
      className: String,
      methodName: String,
      rhs: RastNode,
  ): Option[TerserEmitter.DefmethodEntry] =
      rhs.kind match
        case "FunctionExpression" | "ArrowFunction" =>
          val params = rhs.children.filter(_.kind == "Parameter").map { p =>
            p.children.find(_.kind == "Identifier").flatMap(_.text).getOrElse("_")
          }
          val body = rhs.children.find(_.kind == "Block").getOrElse {
            // Braceless arrow: `() => expr` -- wrap expr in a return
            val exprBody = rhs.children.find(c => c.kind != "Parameter")
            exprBody match
              case Some(expr) =>
                RastNode("Block", 0, (0, 0), children = List(
                  RastNode("ReturnStatement", 0, (0, 0), children = List(expr))
                ))
              case None =>
                RastNode("Block", 0, (0, 0))
          }
          Some(TerserEmitter.DefmethodEntry(className, methodName, params, body))

        case "Identifier" =>
          val refName = rhs.text.getOrElse("")
          val syntheticBody = refName match
            case "return_false" =>
              RastNode("Block", 0, (0, 0), children = List(
                RastNode("ReturnStatement", 0, (0, 0), children = List(
                  RastNode("FalseKeyword", 0, (0, 0), value = Some(RastValue.Bool(false)))
                ))
              ))
            case "return_true" =>
              RastNode("Block", 0, (0, 0), children = List(
                RastNode("ReturnStatement", 0, (0, 0), children = List(
                  RastNode("TrueKeyword", 0, (0, 0), value = Some(RastValue.Bool(true)))
                ))
              ))
            case "return_this" =>
              RastNode("Block", 0, (0, 0), children = List(
                RastNode("ReturnStatement", 0, (0, 0), children = List(
                  RastNode("ThisKeyword", 0, (0, 0))
                ))
              ))
            case "pass_through" =>
              RastNode("Block", 0, (0, 0), children = List(
                RastNode("ReturnStatement", 0, (0, 0), children = List(
                  RastNode("TrueKeyword", 0, (0, 0), value = Some(RastValue.Bool(true)))
                ))
              ))
            case "first_in_statement" =>
              // Reference to first_in_statement utility function
              RastNode("Block", 0, (0, 0), children = List(
                RastNode("ReturnStatement", 0, (0, 0), children = List(
                  RastNode("Identifier", 0, (0, 0), text = Some(refName))
                ))
              ))
            case _ =>
              RastNode("Block", 0, (0, 0), children = List(
                RastNode("ReturnStatement", 0, (0, 0), children = List(
                  RastNode("Identifier", 0, (0, 0), text = Some(refName))
                ))
              ))
          Some(TerserEmitter.DefmethodEntry(className, methodName, Nil, syntheticBody))

        case _ => None

  // --------------------------------------------------------------------------
  // Private: body translation context
  // --------------------------------------------------------------------------

  private class BodyContext(
      @annotation.unused entry: TerserEmitter.DefmethodEntry,
      hierarchy: List[TerserEmitter.DefnodeClass],
      baseIndent: String,
  ):
    val sb = new StringBuilder
    val refusals = mutable.ListBuffer.empty[String]

    // Build lookup of class properties for `this.x` resolution
    private val byName: Map[String, TerserEmitter.DefnodeClass] =
      hierarchy.map(c => c.varName -> c).toMap
    @annotation.unused
    private val allPropsForClass: Map[String, Set[String]] =
      hierarchy.map { cls =>
        cls.varName -> collectAllProps(cls)
      }.toMap

    def result(): String = sb.toString

    def translateBlock(block: RastNode): Unit =
      val stmts = block.children
      if stmts.isEmpty then
        sb.append(s"${baseIndent}()\n")
        return

      // Single-return optimization: unwrap `{ return expr; }` to just the expr
      if stmts.size == 1 && stmts.head.kind == "ReturnStatement" then
        val ret = stmts.head
        if ret.children.isEmpty then
          sb.append(s"${baseIndent}()\n")
        else
          val expr = translateExpr(ret.children.head)
          sb.append(s"$baseIndent$expr\n")
        return

      for (stmt, idx) <- stmts.zipWithIndex do
        val isLast = idx == stmts.size - 1
        translateStatement(stmt, baseIndent, isLast)

    def translateStatement(node: RastNode, indent: String, isLast: Boolean): Unit =
      node.kind match
        case "ReturnStatement" =>
          if node.children.isEmpty then
            sb.append(s"${indent}return ()\n")
          else
            val expr = translateExpr(node.children.head)
            if isLast then
              sb.append(s"$indent$expr\n")
            else
              sb.append(s"${indent}return $expr\n")

        case "ExpressionStatement" =>
          val expr = node.children.headOption.map(translateExpr).getOrElse("()")
          sb.append(s"$indent$expr\n")

        case "VariableStatement" =>
          for vdl <- node.children.find(_.kind == "VariableDeclarationList")
              vd <- vdl.children.filter(_.kind == "VariableDeclaration") do
            val name = vd.children.headOption.flatMap(_.text).getOrElse("_")
            val isConst = vd.flags.contains("const") || node.flags.contains("Const")
            val keyword = if isConst then "val" else "var"
            val scalaName = snakeToCamel(name)
            val init = vd.children.drop(1).headOption.map(translateExpr).getOrElse("null")
            sb.append(s"$indent$keyword $scalaName = $init\n")

        case "IfStatement" =>
          val children = node.children
          if children.isEmpty then
            refuse("EmptyIfStatement")
            sb.append(s"$indent??? /* empty if */\n")
          else
            val cond = translateExpr(children.head)
            sb.append(s"${indent}if $cond then\n")
            if children.size > 1 then
              translateStatementBody(children(1), indent + "  ", isLast && children.size <= 2)
            if children.size > 2 then
              sb.append(s"${indent}else\n")
              translateStatementBody(children(2), indent + "  ", isLast)

        case "Block" =>
          for (stmt, idx) <- node.children.zipWithIndex do
            translateStatement(stmt, indent, isLast && idx == node.children.size - 1)

        case "ForStatement" =>
          translateForStatement(node, indent)

        case "ForInStatement" | "ForOfStatement" =>
          translateForInOfStatement(node, indent)

        case "WhileStatement" =>
          val children = node.children
          if children.size >= 2 then
            val cond = translateExpr(children.head)
            sb.append(s"${indent}while $cond do\n")
            translateStatementBody(children(1), indent + "  ", false)
          else
            refuse("MalformedWhile")
            sb.append(s"$indent??? /* malformed while */\n")

        case "ThrowStatement" =>
          val expr = node.children.headOption.map(translateExpr).getOrElse("null")
          sb.append(s"${indent}throw $expr\n")

        case "TryStatement" =>
          sb.append(s"${indent}try\n")
          val tryBlock = node.children.headOption
          tryBlock.foreach(b => translateStatementBody(b, indent + "  ", false))
          for catchClause <- node.children.find(_.kind == "CatchClause") do
            val param = catchClause.children.find(_.kind == "VariableDeclaration")
              .orElse(catchClause.children.find(_.kind == "Identifier"))
            val paramName = param.flatMap(p =>
              p.children.headOption.flatMap(_.text).orElse(p.text)
            ).getOrElse("e")
            sb.append(s"${indent}catch\n")
            sb.append(s"$indent  case ${snakeToCamel(paramName)}: Throwable =>\n")
            catchClause.children.find(_.kind == "Block").foreach { b =>
              for stmt <- b.children do
                translateStatement(stmt, indent + "    ", false)
            }

        case "SwitchStatement" =>
          translateSwitchStatement(node, indent)

        case "BreakStatement" =>
          sb.append(s"$indent// break\n")

        case "ContinueStatement" =>
          sb.append(s"$indent// continue\n")

        case "EmptyStatement" =>
          () // skip

        case other =>
          refuse(s"UnhandledStatement:$other")
          sb.append(s"$indent??? /* $other */\n")

    private def translateStatementBody(node: RastNode, indent: String, isLast: Boolean): Unit =
      node.kind match
        case "Block" =>
          for (stmt, idx) <- node.children.zipWithIndex do
            translateStatement(stmt, indent, isLast && idx == node.children.size - 1)
        case _ =>
          translateStatement(node, indent, isLast)

    def translateExpr(node: RastNode): String =
      node.kind match
        case "NumericLiteral" =>
          node.value match
            case Some(RastValue.Num(n)) =>
              if n == n.toLong then n.toLong.toString else n.toString
            case _ => "0"

        case "StringLiteral" =>
          node.value match
            case Some(RastValue.Str(s)) =>
              "\"" + escapeString(s) + "\""
            case _ => "\"\""

        case "TrueKeyword" => "true"
        case "FalseKeyword" => "false"
        case "NullKeyword" => "null"
        case "UndefinedKeyword" => "null /* undefined */"
        case "VoidExpression" => "null /* void */"

        case "ThisKeyword" => "this"

        case "Identifier" =>
          val name = node.text.getOrElse("_")
          translateIdentifier(name)

        case "PropertyAccessExpression" =>
          translatePropertyAccess(node)

        case "ElementAccessExpression" =>
          val children = node.children
          if children.size >= 2 then
            val obj = translateExpr(children.head)
            val idx = translateExpr(children(1))
            s"$obj($idx)"
          else "??? /* bad element access */"

        case "CallExpression" =>
          translateCallExpr(node)

        case "NewExpression" =>
          val children = node.children
          val cls = translateExpr(children.head)
          val args = children.drop(1).map(translateExpr)
          s"new $cls(${args.mkString(", ")})"

        case "BinaryExpression" =>
          translateBinaryExpr(node)

        case "PrefixUnaryExpression" =>
          val children = node.children
          val operand = if children.nonEmpty then translateExpr(children.head) else "???"
          node.operator match
            case Some("ExclamationToken") => s"!$operand"
            case Some("MinusToken") => s"-$operand"
            case Some("PlusToken") => s"+$operand"
            case Some("TildeToken") => s"~$operand"
            case Some(op) => s"/* $op */$operand"
            case None => s"!$operand"

        case "PostfixUnaryExpression" =>
          val children = node.children
          val operand = if children.nonEmpty then translateExpr(children.head) else "???"
          node.operator match
            case Some("PlusPlusToken") =>
              s"{ val _prev = $operand; $operand += 1; _prev }"
            case Some("MinusMinusToken") =>
              s"{ val _prev = $operand; $operand -= 1; _prev }"
            case _ => s"$operand /* postfix */"

        case "ConditionalExpression" =>
          val children = node.children
          if children.size >= 3 then
            val cond = translateExpr(children(0))
            val thenE = translateExpr(children(1))
            val elseE = translateExpr(children(2))
            s"(if $cond then $thenE else $elseE)"
          else "??? /* bad conditional */"

        case "ParenthesizedExpression" =>
          val inner = node.children.headOption.map(translateExpr).getOrElse("()")
          s"($inner)"

        case "ArrowFunction" | "FunctionExpression" =>
          translateFunctionExpr(node)

        case "ArrayLiteralExpression" =>
          val elems = node.children.map(translateExpr)
          if elems.isEmpty then "Array.empty"
          else s"Array(${elems.mkString(", ")})"

        case "ObjectLiteralExpression" =>
          translateObjectLiteral(node)

        case "SpreadElement" =>
          val inner = node.children.headOption.map(translateExpr).getOrElse("???")
          s"$inner*"

        case "TemplateExpression" | "TemplateString" =>
          translateTemplateExpr(node)

        case "NoSubstitutionTemplateLiteral" =>
          node.value match
            case Some(RastValue.Str(s)) => "\"" + escapeString(s) + "\""
            case _ => "\"\""

        case "TypeOfExpression" =>
          val operand = node.children.headOption.map(translateExpr).getOrElse("???")
          refuse("TypeOfExpression")
          s"/* typeof */ $operand.getClass.getSimpleName"

        case "TypeAssertionExpression" | "AsExpression" =>
          // Type assertions: just pass through the expression
          node.children.headOption.map(translateExpr).getOrElse("???")

        case "AwaitExpression" =>
          val inner = node.children.headOption.map(translateExpr).getOrElse("???")
          refuse("AwaitExpression")
          s"/* await */ $inner"

        case "DeleteExpression" =>
          refuse("DeleteExpression")
          val operand = node.children.headOption.map(translateExpr).getOrElse("???")
          s"/* delete */ $operand"

        case "CommaToken" =>
          // Sometimes a binary expression has CommaToken children
          "/* comma */"

        case "NonNullExpression" =>
          // TS `expr!` - just pass through
          node.children.headOption.map(translateExpr).getOrElse("???")

        case other =>
          refuse(s"UnhandledExpr:$other")
          s"??? /* $other */"

    // --------------------------------------------------------------------------
    // Expression helpers
    // --------------------------------------------------------------------------

    private def translateIdentifier(name: String): String =
      name match
        case "undefined"    => "null"
        case "Infinity"     => "Double.PositiveInfinity"
        case "NaN"          => "Double.NaN"
        case "return_false" => "false"
        case "return_true"  => "true"
        case "return_this"  => "this"
        case "pass_through" => "true"
        case "arguments" =>
          refuse("ArgumentsObject")
          "??? /* arguments */"
        case n if n.startsWith("AST_") => astVarToScalaName(n)
        case n => snakeToCamel(n)

    private def translatePropertyAccess(node: RastNode): String =
      val children = node.children
      if children.size < 2 then return "??? /* bad prop access */"
      val obj = children.head
      val prop = children.last.text.getOrElse("")

      // this.x -> translate known properties
      if obj.kind == "ThisKeyword" then
        prop match
          case "TYPE" => "this.nodeType"
          case _ => s"this.${snakeToCamel(prop)}"
      // AST_X.prototype -> skip (handled by prototype extraction)
      else if obj.kind == "PropertyAccessExpression" then
        val objParts = obj.children
        if objParts.size >= 2 && objParts.last.text.contains("prototype") then
          val cls = objParts.head.text.getOrElse("?")
          s"${astVarToScalaName(cls)}.${snakeToCamel(prop)}"
        else
          val objExpr = translateExpr(obj)
          s"$objExpr.${snakeToCamel(prop)}"
      else
        val objExpr = translateExpr(obj)
        prop match
          case "length" => s"$objExpr.length"
          case "constructor" => s"$objExpr.getClass"
          case "push" => s"$objExpr.addOne"
          case "forEach" => s"$objExpr.foreach"
          case "indexOf" => s"$objExpr.indexOf"
          case "includes" => s"$objExpr.contains"
          case "splice" => s"$objExpr.remove"
          case "pop" => s"$objExpr.remove($objExpr.length - 1)"
          case _ => s"$objExpr.${snakeToCamel(prop)}"

    private def translateCallExpr(node: RastNode): String =
      val children = node.children
      if children.isEmpty then return "??? /* empty call */"
      val callee = children.head
      val args = children.drop(1)

      callee.kind match
        case "PropertyAccessExpression" =>
          val calleeChildren = callee.children
          if calleeChildren.size >= 2 then
            val obj = calleeChildren.head
            val method = calleeChildren.last.text.getOrElse("")

            // Handle special patterns
            // AST_X.prototype.method.apply(this, arguments)
            if method == "apply" && obj.kind == "PropertyAccessExpression" then
              val objParts = obj.children
              if objParts.size >= 2 then
                val innerObj = objParts.head
                val innerProp = objParts.last.text.getOrElse("")
                if innerObj.kind == "PropertyAccessExpression" then
                  val deepParts = innerObj.children
                  if deepParts.size >= 2 && deepParts.last.text.contains("prototype") then
                    val innerScala = snakeToCamel(innerProp)
                    // super.method(args) pattern
                    val scalaArgs = args.drop(1).flatMap { a =>
                      if a.kind == "Identifier" && a.text.contains("arguments") then
                        refuse("ArgumentsObject")
                        List("??? /* arguments */")
                      else List(translateExpr(a))
                    }
                    return s"super.${innerScala}(${scalaArgs.mkString(", ")})"

            val objExpr = translateExpr(obj)
            val scalaMethod = method match
              case "has" => "contains"
              case "set" => "update"
              case "get" => "get"
              case "delete" => "remove"
              case "forEach" => "foreach"
              case "push" => "addOne"
              case "indexOf" => "indexOf"
              case "includes" => "contains"
              case "hasOwnProperty" => "contains"
              case "call" =>
                // fn.call(this, args) -> fn(args)
                val scalaArgs = args.drop(1).map(translateExpr)
                return s"$objExpr(${scalaArgs.mkString(", ")})"
              case "bind" =>
                // fn.bind(this) -> fn
                return objExpr
              case _ => snakeToCamel(method)
            val scalaArgs = args.map(translateExpr)
            s"$objExpr.$scalaMethod(${scalaArgs.mkString(", ")})"

          else
            val calleeExpr = translateExpr(callee)
            val scalaArgs = args.map(translateExpr)
            s"$calleeExpr(${scalaArgs.mkString(", ")})"

        case "Identifier" =>
          val name = callee.text.getOrElse("???")
          val scalaName = translateIdentifier(name)
          val scalaArgs = args.map(translateExpr)
          s"$scalaName(${scalaArgs.mkString(", ")})"

        case _ =>
          val calleeExpr = translateExpr(callee)
          val scalaArgs = args.map(translateExpr)
          s"$calleeExpr(${scalaArgs.mkString(", ")})"

    private def translateBinaryExpr(node: RastNode): String =
      val children = node.children
      if children.size < 2 then return "??? /* bad binary */"

      node.operator match
        case Some("InstanceOfKeyword") =>
          // Special handling for `instanceof`
          val instChildren = children.filterNot(_.kind == "InstanceOfKeyword")
          if instChildren.size >= 2 then
            val lhs = translateExpr(instChildren.head)
            val rhs = instChildren(1)
            val rhsName = rhs.text.getOrElse(rhs.children.headOption.flatMap(_.text).getOrElse("Any"))
            val scalaType = if rhsName.startsWith("AST_") then astVarToScalaName(rhsName) else rhsName
            s"$lhs.isInstanceOf[$scalaType]"
          else
            val lhs = translateExpr(children.head)
            s"$lhs.isInstanceOf[Any] /* instanceof */"

        case Some(op) =>
          val left = translateExpr(children.head)
          val right = translateExpr(children.last)
          val scalaOp = op match
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
            case "LessThanEqualsToken" => "<="
            case "GreaterThanToken" => ">"
            case "GreaterThanEqualsToken" => ">="
            case "AmpersandToken" => "&"
            case "BarToken" => "|"
            case "CaretToken" => "^"
            case "LessThanLessThanToken" => "<<"
            case "GreaterThanGreaterThanToken" => ">>"
            case "GreaterThanGreaterThanGreaterThanToken" => ">>>"
            case "EqualsToken" => "="
            case "PlusEqualsToken" => "+="
            case "MinusEqualsToken" => "-="
            case "AsteriskEqualsToken" => "*="
            case "SlashEqualsToken" => "/="
            case "BarEqualsToken" => "|="
            case "AmpersandEqualsToken" => "&="
            case "QuestionQuestionToken" =>
              // Nullish coalescing: a ?? b -> if a != null then a else b
              return s"(if $left != null then $left else $right)"
            case "InKeyword" =>
              return s"$right.contains($left)"
            case other => other
          s"$left $scalaOp $right"

        case None =>
          val left = translateExpr(children.head)
          val right = translateExpr(children.last)
          s"$left /* op? */ $right"

    private def translateFunctionExpr(node: RastNode): String =
      val params = node.children.filter(_.kind == "Parameter")
      val body = node.children.find(_.kind == "Block")
      val paramStrs = params.map { p =>
        val name = p.children.find(_.kind == "Identifier").flatMap(_.text).getOrElse("_")
        snakeToCamel(name)
      }

      body match
        case Some(block) =>
          if block.children.size == 1 && block.children.head.kind == "ReturnStatement" then
            val ret = block.children.head
            val expr = ret.children.headOption.map(translateExpr).getOrElse("()")
            if paramStrs.isEmpty then s"(() => $expr)"
            else s"((${paramStrs.mkString(", ")}) => $expr)"
          else if block.children.size == 1 && block.children.head.kind == "ExpressionStatement" then
            val expr = block.children.head.children.headOption.map(translateExpr).getOrElse("()")
            if paramStrs.isEmpty then s"(() => $expr)"
            else s"((${paramStrs.mkString(", ")}) => $expr)"
          else
            // Multi-statement function body
            val innerCtx = new BodyContext(entry, hierarchy, baseIndent + "  ")
            innerCtx.translateBlock(block)
            refusals ++= innerCtx.refusals
            val bodyStr = innerCtx.result().stripTrailing()
            if paramStrs.isEmpty then s"(() => {\n$bodyStr\n$baseIndent})"
            else s"((${paramStrs.mkString(", ")}) => {\n$bodyStr\n$baseIndent})"
        case None =>
          // Arrow without block: single expression body
          val exprBody = node.children.find(c => c.kind != "Parameter")
          val expr = exprBody.map(translateExpr).getOrElse("???")
          if paramStrs.isEmpty then s"(() => $expr)"
          else s"((${paramStrs.mkString(", ")}) => $expr)"

    private def translateObjectLiteral(node: RastNode): String =
      val props = node.children.filter(c =>
        c.kind == "PropertyAssignment" || c.kind == "ShorthandPropertyAssignment" ||
        c.kind == "SpreadAssignment" || c.kind == "MethodDeclaration"
      )
      if props.isEmpty then "Map.empty"
      else
        val entries = props.map { p =>
          p.kind match
            case "PropertyAssignment" =>
              val key = p.children.headOption.flatMap(_.text).getOrElse("?")
              val value = p.children.drop(1).headOption.map(translateExpr).getOrElse("???")
              s"\"${escapeString(key)}\" -> $value"
            case "ShorthandPropertyAssignment" =>
              val key = p.children.headOption.flatMap(_.text).getOrElse("?")
              s"\"${escapeString(key)}\" -> ${snakeToCamel(key)}"
            case "SpreadAssignment" =>
              val expr = p.children.headOption.map(translateExpr).getOrElse("???")
              s"/* spread */ $expr"
            case "MethodDeclaration" =>
              val name = p.children.find(_.kind == "Identifier").flatMap(_.text).getOrElse("?")
              refuse(s"MethodInObject:$name")
              s"\"$name\" -> ??? /* method */"
            case _ => "??? /* unknown prop */"
        }
        s"Map(${entries.mkString(", ")})"

    private def translateTemplateExpr(node: RastNode): String =
      val parts = mutable.ListBuffer.empty[String]
      for c <- node.children do
        c.kind match
          case "TemplateHead" | "TemplateMiddle" | "TemplateTail" =>
            c.value match
              case Some(RastValue.Str(s)) => parts += escapeString(s)
              case _ => ()
          case "NoSubstitutionTemplateLiteral" =>
            c.value match
              case Some(RastValue.Str(s)) => parts += escapeString(s)
              case _ => ()
          case _ =>
            parts += s"$${${translateExpr(c)}}"
      s"s\"${parts.mkString}\""

    private def translateForStatement(@annotation.unused node: RastNode, indent: String): Unit =
      // ForStatement has: init, condition, update, body (some may be missing)
      // In RAST it depends on the structure
      refuse("ForStatement")
      sb.append(s"$indent??? /* for loop */\n")

    private def translateForInOfStatement(node: RastNode, indent: String): Unit =
      val children = node.children
      if children.size >= 2 then
        val varDecl = children.head
        val iterable = children(1)
        val varName = varDecl.children.headOption
          .flatMap(c => c.children.headOption.flatMap(_.text).orElse(c.text))
          .getOrElse("_item")
        val iterExpr = translateExpr(iterable)
        val body = children.find(_.kind == "Block").orElse(children.lift(2))
        sb.append(s"${indent}for ${snakeToCamel(varName)} <- $iterExpr do\n")
        body.foreach(b => translateStatementBody(b, indent + "  ", false))
      else
        refuse("MalformedForInOf")
        sb.append(s"$indent??? /* for-in/of */\n")

    private def translateSwitchStatement(node: RastNode, indent: String): Unit =
      val children = node.children
      if children.isEmpty then
        refuse("EmptySwitch")
        sb.append(s"$indent??? /* empty switch */\n")
        return
      val scrutinee = translateExpr(children.head)
      val caseBlock = children.find(_.kind == "CaseBlock")
      sb.append(s"$indent$scrutinee match\n")
      caseBlock.foreach { cb =>
        for clause <- cb.children do
          clause.kind match
            case "CaseClause" =>
              val value = clause.children.headOption.map(translateExpr).getOrElse("_")
              val stmts = clause.children.drop(1).filterNot(_.kind == "BreakStatement")
              sb.append(s"$indent  case $value =>\n")
              for stmt <- stmts do
                translateStatement(stmt, indent + "    ", false)
            case "DefaultClause" =>
              val stmts = clause.children.filterNot(_.kind == "BreakStatement")
              sb.append(s"$indent  case _ =>\n")
              for stmt <- stmts do
                translateStatement(stmt, indent + "    ", false)
            case _ => ()
      }

    private def refuse(reason: String): Unit =
      refusals += reason

    private def collectAllProps(cls: TerserEmitter.DefnodeClass): Set[String] =
      val own = cls.selfProps.toSet
      cls.base.flatMap(byName.get).map(p => own ++ collectAllProps(p)).getOrElse(own)

  // --------------------------------------------------------------------------
  // Shared helpers (visible to BodyContext and the object)
  // --------------------------------------------------------------------------

  private def astVarToScalaName(varName: String): String =
    if varName.startsWith("AST_") then "Ast" + varName.drop(4)
    else varName

  private def snakeToCamel(s: String): String =
    val parts = s.split("_")
    if parts.length <= 1 then s
    else parts.head + parts.tail.map(_.capitalize).mkString

  private def escapeString(s: String): String =
    s.replace("\\", "\\\\")
      .replace("\"", "\\\"")
      .replace("\n", "\\n")
      .replace("\r", "\\r")
      .replace("\t", "\\t")
