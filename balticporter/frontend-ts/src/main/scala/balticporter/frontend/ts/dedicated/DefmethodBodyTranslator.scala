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

  /** Translate a DEFMETHOD/prototype body from its RAST Block node to Scala.
    * @param thisBinding if set, `this.x` becomes `$thisBinding.x` (e.g., "n" in pattern-match arms)
    * @param nodeParamName when set, property accesses on this identifier are validated
    *   against the DEFNODE hierarchy for the entry's className */
  def translateBody(
      entry: TerserEmitter.DefmethodEntry,
      hierarchy: List[TerserEmitter.DefnodeClass],
      indent: String = "    ",
      thisBinding: String = "this",
      nodeParamName: Option[String] = None,
  ): TranslationResult =
    val ctx = new BodyContext(entry, hierarchy, indent, thisBinding, nodeParamName)
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
      entry: TerserEmitter.DefmethodEntry,
      hierarchy: List[TerserEmitter.DefnodeClass],
      baseIndent: String,
      thisBinding: String = "this",
      nodeParamName: Option[String] = None,
  ):
    val sb = new StringBuilder
    val refusals = mutable.ListBuffer.empty[String]

    // Build lookup of class properties for `this.x` resolution
    private val byName: Map[String, TerserEmitter.DefnodeClass] =
      hierarchy.map(c => c.varName -> c).toMap
    private val allPropsForClass: Map[String, Set[String]] =
      hierarchy.map { cls =>
        cls.varName -> collectAllProps(cls)
      }.toMap

    // Properties available on `this` or `node` based on the DEFMETHOD className
    private val nodeProps: Set[String] =
      if entry.className.startsWith("AST_") then
        allPropsForClass.getOrElse(entry.className, Set.empty)
      else Set.empty

    // The Scala type name for the DEFMETHOD's class (e.g., "AstBinary")
    private val nodeScalaType: String =
      if entry.className.startsWith("AST_") then astVarToScalaName(entry.className)
      else "AstNode"

    // Names that refer to the current node (this, and the first param in
    // DEFMETHOD context, and any explicitly-named node param)
    private val nodeNames: Set[String] =
      val base = Set(thisBinding)
      val withParam = nodeParamName.map(base + _).getOrElse(base)
      withParam

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
          sb.append(s"${baseIndent}null\n")
        else
          val expr = translateExpr(ret.children.head)
          sb.append(s"$baseIndent$expr\n")
        return

      // Multi-statement body needs braces (the caller emits `def foo(): Any =\n`)
      val needsBraces = stmts.size > 1 || stmts.head.kind == "VariableStatement" ||
        stmts.head.kind == "FirstStatement" || stmts.head.kind == "IfStatement" ||
        stmts.head.kind == "ForStatement" || stmts.head.kind == "ForInStatement" ||
        stmts.head.kind == "ForOfStatement" || stmts.head.kind == "WhileStatement" ||
        stmts.head.kind == "DoStatement" || stmts.head.kind == "SwitchStatement" ||
        stmts.head.kind == "TryStatement"
      if needsBraces then sb.append(s"$baseIndent{\n")

      for (stmt, idx) <- stmts.zipWithIndex do
        val isLast = idx == stmts.size - 1
        val innerIndent = if needsBraces then baseIndent + "  " else baseIndent
        translateStatement(stmt, innerIndent, isLast)

      if needsBraces then sb.append(s"$baseIndent}\n")

    def translateStatement(node: RastNode, indent: String, isLast: Boolean): Unit =
      node.kind match
        case "ReturnStatement" =>
          if node.children.isEmpty then
            if isLast then
              sb.append(s"${indent}null\n")
            else
              sb.append(s"${indent}return null\n")
          else
            val expr = translateExpr(node.children.head)
            if isLast then
              sb.append(s"$indent$expr\n")
            else
              sb.append(s"${indent}return $expr\n")

        case "ExpressionStatement" =>
          val expr = node.children.headOption.map(translateExpr).getOrElse("()")
          sb.append(s"$indent$expr\n")

        case "VariableStatement" | "FirstStatement" =>
          for vdl <- node.children.find(_.kind == "VariableDeclarationList")
              vd <- vdl.children.filter(_.kind == "VariableDeclaration") do
            val name = vd.children.headOption.flatMap(_.text).getOrElse("_")
            val isConst = vd.flags.contains("const") || node.flags.contains("Const")
            val keyword = if isConst then "val" else "var"
            val scalaName = snakeToCamel(name)
            // Handle destructuring: { x, y } = obj
            val hasObjectBinding = vd.children.exists(_.kind == "ObjectBindingPattern")
            if hasObjectBinding then
              val pattern = vd.children.find(_.kind == "ObjectBindingPattern").get
              val fields = pattern.children.filter(_.kind == "BindingElement").flatMap(
                _.children.find(_.kind == "Identifier").flatMap(_.text))
              val initExpr = vd.children.find(c => c.kind != "ObjectBindingPattern" && c.kind != "Identifier")
                .map(translateExpr).getOrElse("???")
              for f <- fields do
                sb.append(s"$indent$keyword ${snakeToCamel(f)} = $initExpr.${snakeToCamel(f)}\n")
            else
              val init = vd.children.drop(1).headOption.map(translateExpr).getOrElse("null")
              sb.append(s"$indent$keyword $scalaName = $init\n")

        case "IfStatement" =>
          val children = node.children
          if children.isEmpty then
            refuse("EmptyIfStatement")
            sb.append(s"$indent??? /* empty if */\n")
          else
            val cond = translateExpr(children.head)
            sb.append(s"${indent}if ($cond) {\n")
            if children.size > 1 then
              translateStatementBody(children(1), indent + "  ", isLast && children.size <= 2)
            if children.size > 2 then
              sb.append(s"$indent} else {\n")
              translateStatementBody(children(2), indent + "  ", isLast)
            sb.append(s"$indent}\n")

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
            sb.append(s"${indent}while ($cond) {\n")
            translateStatementBody(children(1), indent + "  ", false)
            sb.append(s"$indent}\n")
          else
            refuse("MalformedWhile")
            sb.append(s"$indent??? /* malformed while */\n")

        case "ThrowStatement" =>
          val expr = node.children.headOption.map(translateExpr).getOrElse("null")
          sb.append(s"${indent}throw $expr\n")

        case "TryStatement" =>
          sb.append(s"${indent}try {\n")
          val tryBlock = node.children.headOption
          tryBlock.foreach(b => translateStatementBody(b, indent + "  ", false))
          sb.append(s"$indent}")
          node.children.find(_.kind == "CatchClause") match
            case Some(catchClause) =>
              val param = catchClause.children.find(_.kind == "VariableDeclaration")
                .orElse(catchClause.children.find(_.kind == "Identifier"))
              val paramName = param.flatMap(p =>
                p.children.headOption.flatMap(_.text).orElse(p.text)
              ).getOrElse("e")
              sb.append(s" catch {\n")
              sb.append(s"$indent  case ${snakeToCamel(paramName)}: Throwable =>\n")
              catchClause.children.find(_.kind == "Block").foreach { b =>
                for stmt <- b.children do
                  translateStatement(stmt, indent + "    ", false)
              }
              sb.append(s"$indent}\n")
            case None =>
              sb.append("\n")
          node.children.find(_.kind == "Block").filter(_ != tryBlock.orNull).foreach { finallyBlock =>
            // If there's a finally block distinct from the try block
          }

        case "SwitchStatement" =>
          translateSwitchStatement(node, indent)

        case "DoStatement" =>
          val children = node.children
          if children.size >= 2 then
            val body = children.head
            val cond = translateExpr(children(1))
            sb.append(s"${indent}do {\n")
            translateStatementBody(body, indent + "  ", false)
            sb.append(s"$indent} while ($cond)\n")
          else
            sb.append(s"$indent??? /* malformed do-while */\n")

        case "FunctionDeclaration" =>
          val name = node.children.find(_.kind == "Identifier").flatMap(_.text).getOrElse("_fn")
          val params = node.children.filter(_.kind == "Parameter").map { p =>
            val pName = p.children.find(_.kind == "Identifier").flatMap(_.text).getOrElse("_")
            snakeToCamel(pName)
          }
          val body = node.children.find(_.kind == "Block")
          sb.append(s"${indent}def ${snakeToCamel(name)}(${params.map(p => s"$p: Any").mkString(", ")}): Any = {\n")
          body.foreach(b => translateStatementBody(b, indent + "  ", true))
          sb.append(s"$indent}\n")

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
        case "NumericLiteral" | "FirstLiteralToken" =>
          node.value match
            case Some(RastValue.Num(n)) =>
              if n == n.toLong then n.toLong.toString else n.toString
            case _ => "0"

        case "StringLiteral" | "FirstTemplateToken" | "LastTemplateToken" =>
          node.value match
            case Some(RastValue.Str(s)) =>
              "\"" + escapeString(s) + "\""
            case _ => "\"\""

        case "RegularExpressionLiteral" =>
          val text = node.value match
            case Some(RastValue.Str(s)) => s
            case _ => node.text.getOrElse("/???/")
          val body = text.stripPrefix("/").reverse.dropWhile(_ != '/').reverse.stripSuffix("/")
          val escaped = body.replace("\\", "\\\\")
          s"\"$escaped\".r"

        case "TrueKeyword" => "true"
        case "FalseKeyword" => "false"
        case "NullKeyword" => "null"
        case "UndefinedKeyword" => "null"
        case "VoidExpression" => "null"

        case "BooleanLiteral" =>
          node.value match
            case Some(RastValue.Bool(b)) => b.toString
            case _ => node.text.getOrElse("false")

        case "ThisKeyword" => thisBinding
        case "SuperExpression" => "super"

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
          cls match
            case "Array" =>
              if args.isEmpty then "scala.collection.mutable.ArrayBuffer.empty[Any]"
              else s"scala.collection.mutable.ArrayBuffer.fill(${args.head})(null)"
            case "Map" =>
              if args.isEmpty then "scala.collection.mutable.Map.empty[Any, Any]"
              else s"scala.collection.mutable.Map(${args.mkString(", ")})"
            case "`Map`" =>
              if args.isEmpty then "scala.collection.mutable.Map.empty[Any, Any]"
              else s"scala.collection.mutable.Map(${args.mkString(", ")})"
            case "Set" =>
              if args.isEmpty then "scala.collection.mutable.Set.empty[Any]"
              else s"scala.collection.mutable.Set(${args.mkString(", ")})"
            case "`Set`" =>
              if args.isEmpty then "scala.collection.mutable.Set.empty[Any]"
              else s"scala.collection.mutable.Set(${args.mkString(", ")})"
            case "WeakMap" =>
              "scala.collection.mutable.WeakHashMap.empty[Any, Any]"
            case "WeakSet" =>
              "scala.collection.mutable.Set.empty[Any] /* WeakSet */"
            case "RegExp" =>
              s"${args.head}.r"
            case "Error" | "TypeError" | "RangeError" | "SyntaxError" | "ReferenceError" =>
              s"new RuntimeException(${args.mkString(", ")})"
            case _ =>
              s"new $cls(${args.mkString(", ")})"

        case "BinaryExpression" =>
          translateBinaryExpr(node)

        case "PrefixUnaryExpression" =>
          val children = node.children
          val operand = if children.nonEmpty then translateExpr(children.head) else "???"
          node.operator match
            case Some("ExclamationToken") => s"!$operand"
            case Some("MinusToken") => s"-$operand"
            case Some("PlusToken") => s"$operand.toDouble"
            case Some("TildeToken") => s"~$operand"
            case Some("PlusPlusToken") => s"{ $operand += 1; $operand }"
            case Some("MinusMinusToken") => s"{ $operand -= 1; $operand }"
            case Some(op) => s"/* $op */$operand"
            case None => s"!$operand"

        case "PostfixUnaryExpression" =>
          val children = node.children
          val operand = if children.nonEmpty then translateExpr(children.head) else "???"
          node.operator match
            case Some("PlusPlusToken") =>
              s"$operand += 1"
            case Some("MinusMinusToken") =>
              s"$operand -= 1"
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
          val hasSpread = node.children.exists(_.kind == "SpreadElement")
          val allSpread = node.children.nonEmpty && node.children.forall(_.kind == "SpreadElement")
          if node.children.isEmpty then "Array.empty[Any]"
          else if allSpread && node.children.length == 1 then
            // [...arr] -> arr.toArray
            val inner = translateExpr(node.children.head.children.head)
            s"$inner.toArray"
          else if hasSpread then
            // Mixed spread + non-spread: Array(a, b) ++ spread
            val nonSpread = node.children.takeWhile(_.kind != "SpreadElement")
            val spreadPart = node.children.dropWhile(_.kind != "SpreadElement")
            val prefix = if nonSpread.nonEmpty then
              s"Array(${nonSpread.map(translateExpr).mkString(", ")})"
            else "Array.empty[Any]"
            val suffix = spreadPart.map { c =>
              if c.kind == "SpreadElement" then translateExpr(c.children.head)
              else s"Array(${translateExpr(c)})"
            }
            (prefix +: suffix).mkString(" ++ ")
          else
            val elems = node.children.map(translateExpr)
            s"Array(${elems.mkString(", ")})"

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
          // typeof x -> a runtime type check helper; emitted as descriptive comment
          // In boolean context (typeof x === "string") this is rewritten in BinaryExpression
          s"""($operand match { case _: String => "string"; case _: Double | _: Int => "number"; case _: Boolean => "boolean"; case null => "undefined"; case _ => "object" })"""

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
          // TS `expr!` or Dart `expr!` — just pass through
          node.children.headOption.map(translateExpr).getOrElse("???")

        // Dart-specific expressions (after kind normalization, these remain)
        case "CascadeExpression" =>
          val children = node.children
          if children.isEmpty then "??? /* empty cascade */"
          else
            val target = translateExpr(children.head)
            val cascades = children.tail.map(translateExpr)
            if cascades.isEmpty then target
            else s"{ val $$t = $target; ${cascades.map(c => s"$$t.$c").mkString("; ")}; $$t }"

        case "NamedExpression" =>
          val label = node.children.find(_.kind.contains("Label"))
            .flatMap(_.children.find(_.kind.contains("Identifier")).flatMap(_.text))
            .orElse(node.children.headOption.flatMap(_.text))
            .getOrElse("_")
          val value = node.children.lastOption.map(translateExpr).getOrElse("???")
          s"$label = $value"

        case "SwitchExpression" =>
          val children = node.children
          if children.isEmpty then "??? /* empty switch */"
          else
            val scrutinee = translateExpr(children.head)
            val cases = children.tail
            val caseStrs = cases.map { c =>
              val pat = if c.children.nonEmpty then translateExpr(c.children.head) else "_"
              val body = if c.children.size > 1 then translateExpr(c.children.last) else "???"
              s"case $pat => $body"
            }
            s"($scrutinee match { ${caseStrs.mkString("; ")} })"

        case "IfNullExpression" =>
          val children = node.children
          if children.size >= 2 then
            val left = translateExpr(children.head)
            val right = translateExpr(children.last)
            s"(if $left != null then $left else $right)"
          else "???"

        // Skip type annotations, labels, comments — they don't produce code
        case "TypeReference" | "FormalParameterList" | "Label" | "Comment" |
             "ConstructorName" | "ArgumentList" | "NamedType" => ""

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
        case "console"      => "Console"
        case "walkAbort"    => "TreeWalker.WalkAbort"
        case "arguments" =>
          refuse("ArgumentsObject")
          "??? /* arguments */"
        case n if n.startsWith("AST_") => astVarToScalaName(n)
        case n => combinedApiLookup.getOrElse(n, snakeToCamel(n))

    private def translatePropertyAccess(node: RastNode): String =
      val children = node.children
      if children.size < 2 then return "??? /* bad prop access */"
      val obj = children.head
      val prop = children.last.text.getOrElse("")

      // Math.x -> math.x (scala.math)
      if obj.kind == "Identifier" && obj.text.contains("Math") then
        return (prop match
          case "PI" => "math.Pi"
          case "E" => "math.E"
          case "abs" | "sqrt" | "pow" | "cos" | "sin" | "tan" | "asin" | "acos" |
               "atan2" | "floor" | "ceil" | "round" | "max" | "min" | "log" |
               "log2" | "log10" | "exp" | "random" | "sign" | "cbrt" | "hypot" =>
            s"math.$prop"
          case _ => s"math.$prop"
        )

      // Number.x
      if obj.kind == "Identifier" && obj.text.contains("Number") then
        return (prop match
          case "MAX_SAFE_INTEGER" => "Long.MaxValue"
          case "isNaN" => "java.lang.Double.isNaN"
          case "isFinite" => "java.lang.Double.isFinite"
          case "isInteger" => "/* Number.isInteger */ ((_: Any) match { case n: Double => n == n.floor; case _ => false })"
          case _ => s"Number.$prop"
        )

      // this.x -> translate known properties (using thisBinding for pattern-match context)
      if obj.kind == "ThisKeyword" then
        prop match
          case "TYPE" => s"$thisBinding.nodeType"
          case _ => s"$thisBinding.${snakeToCamel(prop)}"
      // node.x where node is a typed node param — validate property access
      else if obj.kind == "Identifier" && nodeNames.contains(obj.text.getOrElse("")) then
        val objName = obj.text.getOrElse("")
        val scalaObj = if objName == "this" then thisBinding else snakeToCamel(objName)
        prop match
          case "TYPE" => s"$scalaObj.nodeType"
          case _ => s"$scalaObj.${snakeToCamel(prop)}"
      // AST_X.prototype -> skip (handled by prototype extraction)
      else if obj.kind == "PropertyAccessExpression" then
        val objParts = obj.children
        if objParts.size >= 2 && objParts.last.text.contains("prototype") then
          val cls = objParts.head.text.getOrElse("?")
          s"${astVarToScalaName(cls)}.${snakeToCamel(prop)}"
        else
          val objExpr = translateExpr(obj)
          translatePropOnExpr(objExpr, prop)
      else
        val objExpr = translateExpr(obj)
        translatePropOnExpr(objExpr, prop)

    /** Translate a property access on an already-translated object expression. */
    private def translatePropOnExpr(objExpr: String, prop: String): String =
      prop match
        case "length" => s"$objExpr.length"
        case "constructor" => s"$objExpr.getClass"
        case "push" => s"$objExpr.addOne"
        case "forEach" => s"$objExpr.foreach"
        case "indexOf" => s"$objExpr.indexOf"
        case "includes" | "has" => s"$objExpr.contains"
        case "splice" => s"$objExpr.remove"
        case "pop" => s"$objExpr.remove($objExpr.length - 1)"
        case "shift" => s"$objExpr.remove(0)"
        case "unshift" => s"$objExpr.prepend"
        case "concat" => s"$objExpr.concat"
        case "slice" => s"$objExpr.slice"
        case "join" => s"$objExpr.mkString"
        case "map" => s"$objExpr.map"
        case "filter" => s"$objExpr.filter"
        case "reduce" => s"$objExpr.reduce"
        case "reduceRight" => s"$objExpr.reduceRight"
        case "some" => s"$objExpr.exists"
        case "every" => s"$objExpr.forall"
        case "find" => s"$objExpr.find"
        case "flat" => s"$objExpr.flatten"
        case "flatMap" => s"$objExpr.flatMap"
        case "reverse" => s"$objExpr.reverse"
        case "keys" => s"$objExpr.keys"
        case "values" => s"$objExpr.values"
        case "entries" => s"$objExpr.iterator"
        case "sort" => s"$objExpr.sorted"
        case "toString" => s"$objExpr.toString"
        case "charAt" => s"$objExpr.charAt"
        case "charCodeAt" => s"$objExpr.charAt"
        case "substring" | "substr" => s"$objExpr.substring"
        case "startsWith" => s"$objExpr.startsWith"
        case "endsWith" => s"$objExpr.endsWith"
        case "replace" => s"$objExpr.replace"
        case "replaceAll" => s"$objExpr.replaceAll"
        case "split" => s"$objExpr.split"
        case "trim" => s"$objExpr.trim"
        case "trimStart" | "trimLeft" => s"$objExpr.stripLeading"
        case "trimEnd" | "trimRight" => s"$objExpr.stripTrailing"
        case "toLowerCase" => s"$objExpr.toLowerCase"
        case "toUpperCase" => s"$objExpr.toUpperCase"
        case "search" => s"$objExpr.search"
        case "match" => s"$objExpr.`match`"
        case "test" => s"$objExpr.test"
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
            val scalaArgs = args.map(translateExpr)

            method match
              // Collection methods
              case "has" | "includes" | "contains" =>
                s"$objExpr.contains(${scalaArgs.mkString(", ")})"
              case "set" =>
                s"$objExpr.update(${scalaArgs.mkString(", ")})"
              case "get" =>
                s"$objExpr.get(${scalaArgs.mkString(", ")})"
              case "delete" =>
                s"$objExpr.remove(${scalaArgs.mkString(", ")})"
              case "forEach" =>
                s"$objExpr.foreach(${scalaArgs.mkString(", ")})"
              case "push" =>
                // .push(x) -> += x; .push(a,b) -> a += x; b += y
                val hasSpread = args.exists(_.kind == "SpreadElement")
                if hasSpread && scalaArgs.length == 1 then
                  val spreadArg = scalaArgs.head.stripSuffix("*")
                  s"$objExpr ++= $spreadArg"
                else if scalaArgs.length == 1 then s"$objExpr += ${scalaArgs.head}"
                else scalaArgs.map(a => s"$objExpr += $a").mkString("; ")
              case "pop" =>
                s"$objExpr.remove($objExpr.length - 1)"
              case "shift" =>
                s"$objExpr.remove(0)"
              case "unshift" =>
                s"$objExpr.prepend(${scalaArgs.mkString(", ")})"
              case "indexOf" =>
                s"$objExpr.indexOf(${scalaArgs.mkString(", ")})"
              case "lastIndexOf" =>
                s"$objExpr.lastIndexOf(${scalaArgs.mkString(", ")})"
              case "hasOwnProperty" =>
                s"$objExpr.contains(${scalaArgs.mkString(", ")})"
              case "splice" =>
                if scalaArgs.length >= 2 then
                  s"$objExpr.remove(${scalaArgs.mkString(", ")})"
                else s"$objExpr.remove(${scalaArgs.mkString(", ")})"
              case "concat" =>
                s"($objExpr ++ ${scalaArgs.mkString(" ++ ")})"
              case "join" =>
                s"$objExpr.mkString(${scalaArgs.mkString(", ")})"
              case "map" =>
                s"$objExpr.map(${scalaArgs.mkString(", ")})"
              case "filter" =>
                s"$objExpr.filter(${scalaArgs.mkString(", ")})"
              case "reduce" =>
                s"$objExpr.reduce(${scalaArgs.mkString(", ")})"
              case "some" =>
                s"$objExpr.exists(${scalaArgs.mkString(", ")})"
              case "every" =>
                s"$objExpr.forall(${scalaArgs.mkString(", ")})"
              case "find" =>
                s"$objExpr.find(${scalaArgs.mkString(", ")})"
              case "findIndex" =>
                s"$objExpr.indexWhere(${scalaArgs.mkString(", ")})"
              case "flat" =>
                s"$objExpr.flatten"
              case "flatMap" =>
                s"$objExpr.flatMap(${scalaArgs.mkString(", ")})"
              case "sort" =>
                if scalaArgs.nonEmpty then s"$objExpr.sortWith((a, b) => ${scalaArgs.head}(a, b) < 0)"
                else s"$objExpr.sorted"
              case "reverse" =>
                s"$objExpr.reverse"
              case "fill" =>
                s"$objExpr.mapInPlace(_ => ${scalaArgs.headOption.getOrElse("null")})"
              case "slice" =>
                s"$objExpr.slice(${scalaArgs.mkString(", ")})"
              // String methods
              case "substr" | "substring" =>
                s"$objExpr.substring(${scalaArgs.mkString(", ")})"
              case "charAt" =>
                s"$objExpr.charAt(${scalaArgs.mkString(", ")})"
              case "charCodeAt" =>
                s"$objExpr.charAt(${scalaArgs.mkString(", ")}).toInt"
              case "startsWith" =>
                s"$objExpr.startsWith(${scalaArgs.mkString(", ")})"
              case "endsWith" =>
                s"$objExpr.endsWith(${scalaArgs.mkString(", ")})"
              case "replace" =>
                s"$objExpr.replace(${scalaArgs.mkString(", ")})"
              case "replaceAll" =>
                s"$objExpr.replaceAll(${scalaArgs.mkString(", ")})"
              case "split" =>
                s"$objExpr.split(${scalaArgs.mkString(", ")})"
              case "trim" =>
                s"$objExpr.trim"
              case "toLowerCase" =>
                s"$objExpr.toLowerCase"
              case "toUpperCase" =>
                s"$objExpr.toUpperCase"
              case "repeat" =>
                s"$objExpr * ${scalaArgs.headOption.getOrElse("1")}"
              case "padStart" =>
                if scalaArgs.length >= 2 then s"$objExpr.reverse.padTo(${scalaArgs.head}, ${scalaArgs(1)}).reverse.mkString"
                else s"$objExpr.padStart(${scalaArgs.mkString(", ")})"
              case "padEnd" =>
                if scalaArgs.length >= 2 then s"$objExpr.padTo(${scalaArgs.head}, ${scalaArgs(1)}).mkString"
                else s"$objExpr.padEnd(${scalaArgs.mkString(", ")})"
              case "toFixed" =>
                val precision = scalaArgs.headOption.getOrElse("0")
                s"""f"$${$objExpr}%.${precision}f""""
              case "toString" =>
                if scalaArgs.nonEmpty then s"java.lang.Integer.toString($objExpr.toInt, ${scalaArgs.head})"
                else s"$objExpr.toString"
              case "match" =>
                s"${scalaArgs.head}.findPrefixMatchOf($objExpr).isDefined"
              case "test" =>
                s"${objExpr}.findFirstIn(${scalaArgs.mkString(", ")}).isDefined"
              case "exec" =>
                s"${objExpr}.findFirstMatchIn(${scalaArgs.mkString(", ")})"
              // Function binding
              case "call" =>
                // fn.call(this, args) -> fn(args)
                val callArgs = scalaArgs.drop(1)
                s"$objExpr(${callArgs.mkString(", ")})"
              case "apply" =>
                // fn.apply(this, argsArray) -> fn(argsArray:_*)
                val applyArgs = scalaArgs.drop(1)
                if applyArgs.length == 1 then s"$objExpr(${applyArgs.head}*)"
                else s"$objExpr(${applyArgs.mkString(", ")})"
              case "bind" =>
                // fn.bind(this) -> fn
                objExpr
              // Object static methods
              case "keys" if objExpr == "Object" =>
                s"${scalaArgs.head}.keys"
              case "values" if objExpr == "Object" =>
                s"${scalaArgs.head}.values"
              case "entries" if objExpr == "Object" =>
                s"${scalaArgs.head}.iterator"
              case "assign" if objExpr == "Object" =>
                if scalaArgs.length >= 2 then s"${scalaArgs.head} ++= ${scalaArgs(1)}"
                else s"${scalaArgs.head}"
              case "create" if objExpr == "Object" =>
                "Map.empty"
              case "defineProperty" if objExpr == "Object" =>
                s"/* Object.defineProperty */ ${scalaArgs.mkString(", ")}"
              // Array static methods
              case "isArray" if objExpr == "Array" =>
                s"${scalaArgs.head}.isInstanceOf[Seq[?]]"
              case "from" if objExpr == "Array" =>
                s"${scalaArgs.head}.toArray"
              // JSON
              case "stringify" if objExpr == "JSON" =>
                s"/* JSON.stringify */ ${scalaArgs.head}.toString"
              case "parse" if objExpr == "JSON" =>
                s"/* JSON.parse */ ${scalaArgs.head}"
              // console
              case "log" if objExpr == "Console" || objExpr == "console" =>
                s"println(${scalaArgs.mkString(", ")})"
              case "warn" if objExpr == "Console" || objExpr == "console" =>
                s"System.err.println(${scalaArgs.mkString(", ")})"
              case "error" if objExpr == "Console" || objExpr == "console" =>
                s"System.err.println(${scalaArgs.mkString(", ")})"
              // AST node walk/transform
              case "walk" =>
                s"$objExpr.walk(${scalaArgs.mkString(", ")})"
              case "transform" =>
                s"$objExpr.transform(${scalaArgs.mkString(", ")})"
              case "clone" =>
                s"$objExpr.clone()"
              case "size" =>
                s"AstSize.size($objExpr)"
              case "getValue" =>
                s"$objExpr.getValue()"
              case "definition" =>
                s"$objExpr.definition()"
              // General method
              case _ =>
                s"$objExpr.${snakeToCamel(method)}(${scalaArgs.mkString(", ")})"

          else
            val calleeExpr = translateExpr(callee)
            val scalaArgs = args.map(translateExpr)
            s"$calleeExpr(${scalaArgs.mkString(", ")})"

        case "Identifier" =>
          val name = callee.text.getOrElse("???")
          // Translate global JS functions
          name match
            case "parseInt" =>
              val scalaArgs = args.map(translateExpr)
              s"${scalaArgs.head}.toInt"
            case "parseFloat" =>
              val scalaArgs = args.map(translateExpr)
              s"${scalaArgs.head}.toDouble"
            case "isNaN" =>
              val scalaArgs = args.map(translateExpr)
              s"${scalaArgs.head}.isNaN"
            case "isFinite" =>
              val scalaArgs = args.map(translateExpr)
              s"${scalaArgs.head}.isInfinite == false"
            case "String" =>
              val scalaArgs = args.map(translateExpr)
              s"${scalaArgs.head}.toString"
            case "Number" =>
              val scalaArgs = args.map(translateExpr)
              s"${scalaArgs.head}.toDouble"
            case "Boolean" =>
              val scalaArgs = args.map(translateExpr)
              s"(${scalaArgs.head} != null && ${scalaArgs.head} != false)"
            case "Array" if args.isEmpty =>
              "Array.empty[Any]"
            case "Array" =>
              val scalaArgs = args.map(translateExpr)
              s"new Array[Any](${scalaArgs.mkString(", ")})"
            case "Set" if args.isEmpty =>
              "scala.collection.mutable.Set.empty[Any]"
            case "Map" if args.isEmpty =>
              "scala.collection.mutable.Map.empty[Any, Any]"
            case "Error" | "TypeError" | "RangeError" | "SyntaxError" =>
              val scalaArgs = args.map(translateExpr)
              s"new RuntimeException(${scalaArgs.mkString(", ")})"
            case "make_void_0" | "makeVoid0" =>
              val scalaArgs = args.map(translateExpr)
              val orig = if scalaArgs.nonEmpty then scalaArgs.head else "null"
              s"{ val $$n = new AstUnaryPrefix(); $$n.operator = \"void\"; $$n.expression = { val $$m = new AstNumber(); $$m.value = 0; $$m }; $$n }"

            case "make_node" =>
              translateMakeNode(args)
            case "MAP" if args.nonEmpty =>
              val scalaArgs = args.map(translateExpr)
              s"${scalaArgs.head}.map(${scalaArgs.drop(1).mkString(", ")})"
            case "has_flag" =>
              val scalaArgs = args.map(translateExpr)
              s"CompressorFlags.hasFlag(${scalaArgs.mkString(", ")})"
            case "set_flag" =>
              val scalaArgs = args.map(translateExpr)
              s"CompressorFlags.setFlag(${scalaArgs.mkString(", ")})"
            case "clear_flag" =>
              val scalaArgs = args.map(translateExpr)
              s"CompressorFlags.clearFlag(${scalaArgs.mkString(", ")})"
            case _ =>
              val scalaArgs = args.map(translateExpr)
              val scalaName = translateIdentifier(name)
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
            if rhsName.startsWith("AST_") then
              // Known AST type -- compile-time isInstanceOf
              s"$lhs.isInstanceOf[${astVarToScalaName(rhsName)}]"
            else if rhsName.head.isUpper && !scalaKeywords.contains(rhsName) then
              // Capitalized non-keyword -- likely a class name
              s"$lhs.isInstanceOf[$rhsName]"
            else
              // Runtime class reference (e.g., a function parameter) -- use isInstance
              val scalaRhs = translateExpr(rhs)
              s"$scalaRhs.isInstance($lhs)"
          else
            val lhs = translateExpr(children.head)
            s"$lhs.isInstanceOf[Any] /* instanceof */"

        case Some(op) =>
          val lhs = children.head
          val rhs = children.last

          // x.TYPE === "Y" -> x.isInstanceOf[AstY]
          if (op == "EqualsEqualsEqualsToken" || op == "EqualsEqualsToken") &&
             isTypePropertyAccess(lhs) then
            val objExpr = translateExpr(lhs.children.head)
            val typeName = rhs.value match
              case Some(RastValue.Str(s)) => s
              case _ => ""
            if typeName.nonEmpty then
              return s"$objExpr.isInstanceOf[Ast$typeName]"

          // x.TYPE !== "Y" -> !x.isInstanceOf[AstY]
          if (op == "ExclamationEqualsEqualsToken" || op == "ExclamationEqualsToken") &&
             isTypePropertyAccess(lhs) then
            val objExpr = translateExpr(lhs.children.head)
            val typeName = rhs.value match
              case Some(RastValue.Str(s)) => s
              case _ => ""
            if typeName.nonEmpty then
              return s"!$objExpr.isInstanceOf[Ast$typeName]"

          // "Y" === x.TYPE -> x.isInstanceOf[AstY]
          if (op == "EqualsEqualsEqualsToken" || op == "EqualsEqualsToken") &&
             isTypePropertyAccess(rhs) then
            val objExpr = translateExpr(rhs.children.head)
            val typeName = lhs.value match
              case Some(RastValue.Str(s)) => s
              case _ => ""
            if typeName.nonEmpty then
              return s"$objExpr.isInstanceOf[Ast$typeName]"

          // "Y" !== x.TYPE -> !x.isInstanceOf[AstY]
          if (op == "ExclamationEqualsEqualsToken" || op == "ExclamationEqualsToken") &&
             isTypePropertyAccess(rhs) then
            val objExpr = translateExpr(rhs.children.head)
            val typeName = lhs.value match
              case Some(RastValue.Str(s)) => s
              case _ => ""
            if typeName.nonEmpty then
              return s"!$objExpr.isInstanceOf[Ast$typeName]"

          // typeof X === "type" -> X.isInstanceOf[Type]
          if (op == "EqualsEqualsEqualsToken" || op == "EqualsEqualsToken") &&
             lhs.kind == "TypeOfExpression" then
            val typeofOperand = lhs.children.head
            val operandExpr = translateExpr(typeofOperand)
            val typeStr = rhs.value match
              case Some(RastValue.Str(s)) => s
              case _ => "object"
            return typeStr match
              case "string" => s"$operandExpr.isInstanceOf[String]"
              case "number" => s"$operandExpr.isInstanceOf[Double]"
              case "boolean" => s"$operandExpr.isInstanceOf[Boolean]"
              case "function" => s"$operandExpr.isInstanceOf[Function[?, ?]]"
              case "undefined" => s"($operandExpr == null)"
              case "object" => s"($operandExpr != null && !$operandExpr.isInstanceOf[Double] && !$operandExpr.isInstanceOf[String] && !$operandExpr.isInstanceOf[Boolean])"
              case _ => s"$operandExpr.isInstanceOf[$typeStr]"

          // typeof X !== "type" -> !X.isInstanceOf[Type]
          if (op == "ExclamationEqualsEqualsToken" || op == "ExclamationEqualsToken") &&
             lhs.kind == "TypeOfExpression" then
            val typeofOperand = lhs.children.head
            val operandExpr = translateExpr(typeofOperand)
            val typeStr = rhs.value match
              case Some(RastValue.Str(s)) => s
              case _ => "object"
            return typeStr match
              case "string" => s"!$operandExpr.isInstanceOf[String]"
              case "number" => s"!$operandExpr.isInstanceOf[Double]"
              case "boolean" => s"!$operandExpr.isInstanceOf[Boolean]"
              case "function" => s"!$operandExpr.isInstanceOf[Function[?, ?]]"
              case "undefined" => s"($operandExpr != null)"
              case _ => s"!$operandExpr.isInstanceOf[$typeStr]"

          val left = translateExpr(lhs)
          val right = translateExpr(rhs)
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
            case "LessThanToken" | "FirstBinaryOperator" => "<"
            case "LessThanEqualsToken" => "<="
            case "GreaterThanToken" => ">"
            case "GreaterThanEqualsToken" => ">="
            case "AmpersandToken" => "&"
            case "BarToken" => "|"
            case "CaretToken" => "^"
            case "LessThanLessThanToken" => "<<"
            case "GreaterThanGreaterThanToken" => ">>"
            case "GreaterThanGreaterThanGreaterThanToken" => ">>>"
            case "EqualsToken" | "FirstAssignment" => "="
            case "PlusEqualsToken" | "FirstCompoundAssignment" => "+="
            case "MinusEqualsToken" => "-="
            case "AsteriskEqualsToken" => "*="
            case "SlashEqualsToken" => "/="
            case "BarEqualsToken" => "|="
            case "AmpersandEqualsToken" => "&="
            case "PercentEqualsToken" => "%="
            case "AsteriskAsteriskEqualsToken" => "**="
            case "QuestionQuestionToken" =>
              // Nullish coalescing: a ?? b -> if a != null then a else b
              return s"(if $left != null then $left else $right)"
            case "InKeyword" =>
              return s"$right.contains($left)"
            case "CommaToken" =>
              // Comma operator: evaluate both, return right
              return s"{ $left; $right }"
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
            // Multi-statement function body -- always brace-wrap
            val innerIndent = baseIndent + "    "
            val bodyLines = new StringBuilder
            for (stmt, idx) <- block.children.zipWithIndex do
              val isLast = idx == block.children.size - 1
              val innerCtx = new BodyContext(entry, hierarchy, innerIndent)
              innerCtx.translateStatement(stmt, innerIndent, isLast)
              refusals ++= innerCtx.refusals
              bodyLines.append(innerCtx.result())
            val bodyStr = bodyLines.toString.stripTrailing()
            if paramStrs.isEmpty then s"(() => {\n$bodyStr\n$baseIndent  })"
            else s"((${paramStrs.mkString(", ")}) => {\n$bodyStr\n$baseIndent  })"
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

    private def translateForStatement(node: RastNode, indent: String): Unit =
      // ForStatement children: [init?, condition?, update?, body]
      // After R1 token filtering, children are semantic only
      val children = node.children
      // Find components by kind
      val init = children.find(c => c.kind == "VariableDeclarationList" || c.kind == "FirstStatement" ||
        (c.kind == "BinaryExpression" && c.operator.contains("EqualsToken")))
      val body = children.find(_.kind == "Block").orElse(children.lastOption)
      // Condition and update are harder — they're positioned between init and body
      val nonInitBody = children.filterNot(c => c == init.orNull || c == body.orNull)
      val cond = nonInitBody.headOption
      val update = nonInitBody.lift(1)

      // Emit as: { init; while (cond) { body; update } }
      sb.append(s"$indent{\n")
      init.foreach { i =>
        if i.kind == "VariableDeclarationList" then
          val decls = i.children.filter(_.kind == "VariableDeclaration")
          for d <- decls do
            val name = d.children.find(_.kind == "Identifier").flatMap(_.text).getOrElse("_i")
            val initExpr = d.children.find(c => c.kind != "Identifier" && !c.kind.contains("Type"))
              .map(translateExpr).getOrElse("0")
            sb.append(s"$indent  var ${snakeToCamel(name)} = $initExpr\n")
        else
          sb.append(s"$indent  ${translateExpr(i)}\n")
      }
      val condStr = cond.map(translateExpr).getOrElse("true")
      sb.append(s"$indent  while ($condStr) {\n")
      body.foreach(b => translateStatementBody(b, indent + "    ", false))
      update.foreach(u => sb.append(s"$indent    ${translateExpr(u)}\n"))
      sb.append(s"$indent  }\n")
      sb.append(s"$indent}\n")

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
        sb.append(s"${indent}for (${snakeToCamel(varName)} <- $iterExpr) {\n")
        body.foreach(b => translateStatementBody(b, indent + "  ", false))
        sb.append(s"$indent}\n")
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
      sb.append(s"$indent($scrutinee) match {\n")
      var hasDefault = false
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
              hasDefault = true
              val stmts = clause.children.filterNot(_.kind == "BreakStatement")
              sb.append(s"$indent  case _ =>\n")
              for stmt <- stmts do
                translateStatement(stmt, indent + "    ", false)
            case _ => ()
      }
      if !hasDefault then sb.append(s"$indent  case _ => ()\n")
      sb.append(s"$indent}\n")

    /** Translate `make_node(AST_X, orig, { prop: val, ... })` to Scala.
      *
      * JS pattern: `make_node(AST_Binary, self, { operator: "+", left: a, right: b })`
      * Scala: `{ val $n = new AstBinary(); $n.operator = "+"; $n.left = a; $n.right = b; $n }`
      */
    private def translateMakeNode(args: List[RastNode]): String =
      if args.isEmpty then return "??? /* make_node: no args */"
      val classArg = args.head
      val className = classArg.text.getOrElse("")
      val scalaClass = astVarToScalaName(className)

      // Optional origin argument and props argument
      val propsArg = args.find(_.kind == "ObjectLiteralExpression")

      propsArg match
        case Some(objLit) =>
          val props = objLit.children.filter(c =>
            c.kind == "PropertyAssignment" || c.kind == "ShorthandPropertyAssignment"
          )
          if props.isEmpty then
            s"new $scalaClass()"
          else
            val assignments = props.map { p =>
              p.kind match
                case "PropertyAssignment" =>
                  val key = p.children.headOption.flatMap(_.text).getOrElse("?")
                  val value = p.children.drop(1).headOption.map(translateExpr).getOrElse("???")
                  s"$$n.${snakeToCamel(key)} = $value"
                case "ShorthandPropertyAssignment" =>
                  val key = p.children.headOption.flatMap(_.text).getOrElse("?")
                  s"$$n.${snakeToCamel(key)} = ${snakeToCamel(key)}"
                case _ => "???"
            }
            s"{ val $$n = new $scalaClass(); ${assignments.mkString("; ")}; $$n }"
        case None =>
          s"new $scalaClass()"

    private def refuse(reason: String): Unit =
      refusals += reason

    private def collectAllProps(cls: TerserEmitter.DefnodeClass): Set[String] =
      val own = cls.selfProps.toSet
      cls.base.flatMap(byName.get).map(p => own ++ collectAllProps(p)).getOrElse(own)

    /** Check if a node is a `.TYPE` property access (e.g., `node.TYPE`). */
    private def isTypePropertyAccess(node: RastNode): Boolean =
      node.kind == "PropertyAccessExpression" &&
        node.children.size >= 2 &&
        node.children.last.text.contains("TYPE")

  // --------------------------------------------------------------------------
  // Shared helpers (visible to BodyContext and the object)
  // --------------------------------------------------------------------------

  private def astVarToScalaName(varName: String): String =
    if varName.startsWith("AST_") then "Ast" + varName.drop(4)
    else varName

  private def snakeToCamel(s: String): String =
    val parts = s.split("_")
    if parts.length <= 1 then escapeKeyword(s)
    else escapeKeyword(parts.head + parts.tail.map(_.capitalize).mkString)

  private def escapeKeyword(name: String): String =
    if scalaKeywords.contains(name) then s"${name}_" else name

  private val scalaKeywords: Set[String] = Set(
    "type", "val", "var", "def", "class", "trait", "object", "enum",
    "match", "case", "if", "else", "for", "while", "do", "return",
    "throw", "try", "catch", "finally", "import", "export", "package",
    "new", "this", "super", "with", "extends", "yield", "abstract",
    "final", "sealed", "private", "protected", "override", "lazy",
    "implicit", "given", "using", "then", "end", "inline", "opaque",
    "transparent", "erased", "open", "infix",
  )

  private def escapeString(s: String): String =
    s.replace("\\", "\\\\")
      .replace("\"", "\\\"")
      .replace("\n", "\\n")
      .replace("\r", "\\r")
      .replace("\t", "\\t")

  /** Terser-specific API name mapping: JS identifier → Scala equivalent. */
  private val terserApiLookup: Map[String, String] = Map(
    "make_void_0"    -> "makeVoid0",
    "has_flag"       -> "CompressorFlags.hasFlag",
    "set_flag"       -> "CompressorFlags.setFlag",
    "clear_flag"     -> "CompressorFlags.clearFlag",
    "walk_abort"     -> "TreeWalker.WalkAbort",
    "WALK_ABORT"     -> "TreeWalker.WalkAbort",
    "LIST_OVERHEAD"  -> "AstSize.ListOverhead",
    "MAP"            -> "mapNodes",
    "MAP_SKIP"       -> "MapSkip",
    "SQUEEZED"       -> "CompressorFlags.SQUEEZED",
    "OPTIMIZED"      -> "CompressorFlags.OPTIMIZED",
    "TOP"            -> "CompressorFlags.TOP",
  )

  /** KaTeX-specific API name mapping: JS identifier → Scala equivalent. */
  private val katexApiLookup: Map[String, String] = Map(
    "assertNodeType"       -> "ParseNode.assertNodeType",
    "assertSymbolNodeType" -> "ParseNode.assertSymbolNodeType",
    "checkNodeType"        -> "ParseNode.checkNodeType",
    "normalizeArgument"    -> "normalizeArgument",
    "ordargument"          -> "ordArgument",
    "makeOrd"              -> "BuildCommon.makeOrd",
    "makeSpan"             -> "BuildCommon.makeSpan",
    "makeVList"            -> "BuildCommon.makeVList",
    "makeFragment"         -> "BuildCommon.makeFragment",
    "makeSymbol"           -> "BuildCommon.makeSymbol",
    "staticSvg"            -> "BuildCommon.staticSvg",
    "svgData"              -> "BuildCommon.svgData",
    "mathsym"              -> "BuildCommon.mathsym",
    "makeLineBreak"        -> "BuildCommon.makeLineBreak",
    "makeEm"               -> "Units.makeEm",
    "calculateSize"        -> "Units.calculateSize",
    "isCharacterBox"       -> "Utils.isCharacterBox",
    "escape"               -> "Utils.escape",
    "getVariant"           -> "getVariant",
    "htmlBuilder"          -> "htmlBuilder",
    "mathmlBuilder"        -> "mathmlBuilder",
    "buildExpression"      -> "BuildHTML.buildExpression",
    "buildGroup"           -> "BuildHTML.buildGroup",
    "buildMathML"          -> "BuildMathML.buildMathML",
    "buildExpressionRow"   -> "BuildMathML.buildExpressionRow",
    "stretchySvg"          -> "Stretchy.stretchySvg",
    "stretchyMathML"       -> "Stretchy.stretchyMathML",
    "MathNode"             -> "MathNode",
    "SymbolNode"           -> "SymbolNode",
    "SpaceNode"            -> "SpaceNode",
  )

  /** Dart-sass-specific API name mapping. */
  private val dartApiLookup: Map[String, String] = Map(
    "assertString"         -> "assertString",
    "assertNumber"         -> "assertNumber",
    "assertColor"          -> "assertColor",
    "assertMap"            -> "assertMap",
    "assertFunction"       -> "assertFunction",
    "assertCalculation"    -> "assertCalculation",
    "asList"               -> "asList",
    "asPlain"              -> "asPlain",
    "sassIndexToListIndex" -> "sassIndexToListIndex",
    "sassIndexToStringIndex" -> "sassIndexToStringIndex",
    "withListContents"     -> "withListContents",
    "changeSeparator"      -> "changeSeparator",
    "sassTrue"             -> "SassBoolean.sassTrue",
    "sassFalse"            -> "SassBoolean.sassFalse",
    "sassNull"             -> "SassNull.sassNull",
  )

  /** Combined API lookup: terser + katex + dart. */
  private val combinedApiLookup: Map[String, String] =
    terserApiLookup ++ katexApiLookup ++ dartApiLookup

  /** Dart RAST node kind → TS RAST node kind mapping.
    * Normalizes Dart analyzer AST kinds to the TS kinds the body translator handles. */
  private val dartKindMap: Map[String, String] = Map(
    "MethodInvocation"          -> "CallExpression",
    "FunctionExpressionInvocation" -> "CallExpression",
    "PrefixedIdentifier"        -> "PropertyAccessExpression",
    "PropertyAccess"            -> "PropertyAccessExpression",
    "InstanceCreationExpression" -> "NewExpression",
    "IndexExpression"           -> "ElementAccessExpression",
    "SimpleIdentifier"          -> "Identifier",
    "SimpleStringLiteral"       -> "StringLiteral",
    "AdjacentStrings"           -> "StringLiteral",
    "IntegerLiteral"            -> "NumericLiteral",
    "DoubleLiteral"             -> "NumericLiteral",
    "NullLiteral"               -> "NullKeyword",
    "ListLiteral"               -> "ArrayLiteralExpression",
    "SetOrMapLiteral"           -> "ObjectLiteralExpression",
    "MapLiteralEntry"           -> "PropertyAssignment",
    "ThrowExpression"           -> "ThrowStatement",
    "AsExpression"              -> "AsExpression",
    "PrefixExpression"          -> "PrefixUnaryExpression",
    "PostfixExpression"         -> "PostfixUnaryExpression",
    "AssignmentExpression"      -> "BinaryExpression",
    "ParenthesizedExpression"   -> "ParenthesizedExpression",
    "FunctionExpression"        -> "FunctionExpression",
    "AwaitExpression"           -> "AwaitExpression",
    "ConditionalExpression"     -> "ConditionalExpression",
    "StringInterpolation"       -> "TemplateExpression",
    "InterpolationString"       -> "TemplateHead",
    "InterpolationExpression"   -> "TemplateExpression",
    "SuperExpression"           -> "SuperExpression",
    "ThisExpression"            -> "ThisKeyword",
    "ExpressionStatement"       -> "ExpressionStatement",
    "ReturnStatement"           -> "ReturnStatement",
    "VariableDeclarationStatement" -> "VariableStatement",
    "VariableDeclarationList"   -> "VariableDeclarationList",
    "VariableDeclaration"       -> "VariableDeclaration",
    "IfStatement"               -> "IfStatement",
    "ForStatement"              -> "ForStatement",
    "WhileStatement"            -> "WhileStatement",
    "DoStatement"               -> "DoStatement",
    "SwitchStatement"           -> "SwitchStatement",
    "SwitchCase"                -> "CaseClause",
    "SwitchDefault"             -> "DefaultClause",
    "SwitchPatternCase"         -> "CaseClause",
    "TryStatement"              -> "TryStatement",
    "CatchClause"               -> "CatchClause",
    "Block"                     -> "Block",
    "BlockFunctionBody"         -> "Block",
    "BreakStatement"            -> "BreakStatement",
    "ContinueStatement"         -> "ContinueStatement",
    "EmptyStatement"            -> "EmptyStatement",
    "FunctionDeclaration"       -> "FunctionDeclaration",
    "MethodDeclaration"         -> "FunctionDeclaration",
    "SpreadElement"             -> "SpreadElement",
    "FormalParameterList"       -> "FormalParameterList",
    "SimpleFormalParameter"     -> "Parameter",
    "DefaultFormalParameter"    -> "Parameter",
    "FieldFormalParameter"      -> "Parameter",
    "NamedType"                 -> "TypeReference",
    "ArgumentList"              -> "ArgumentList",
    "ConstructorName"           -> "ConstructorName",
    "Label"                     -> "Label",
    "Comment"                   -> "Comment",
  )

  /** Normalize a RAST node kind, stripping `Impl` suffix and mapping
    * Dart-specific names to their TS equivalents. */
  def normalizeKind(kind: String): String =
    val base = if kind.endsWith("Impl") then kind.stripSuffix("Impl") else kind
    dartKindMap.getOrElse(base, base)

  /** Recursively normalize all node kinds in a RAST tree.
    * Dart nodes get their kinds mapped to TS equivalents, and
    * ArgumentList nodes get unwrapped so call args appear as direct children. */
  def normalizeNodeTree(node: RastNode): RastNode =
    val nKind = normalizeKind(node.kind)
    val normalizedChildren = node.children.map(normalizeNodeTree)
    // Unwrap ArgumentList: in Dart, call args are inside an ArgumentList node.
    // In TS, they're direct children of the CallExpression. Flatten them.
    val children = if nKind == "CallExpression" then
      normalizedChildren.flatMap { c =>
        if c.kind == "ArgumentList" then c.children
        else List(c)
      }
    else if nKind == "NewExpression" then
      // Dart InstanceCreationExpression has [ConstructorName, ArgumentList]
      // Normalize to [className, ...args]
      val ctorName = normalizedChildren.find(_.kind == "ConstructorName")
      val argList = normalizedChildren.find(_.kind == "ArgumentList")
      val classNode = ctorName.flatMap { cn =>
        val typeName = cn.children.find(_.kind == "TypeReference")
        val clsIdent = typeName.flatMap(_.children.find(_.kind == "Identifier"))
        clsIdent.orElse(cn.children.find(_.kind == "Identifier"))
      }.getOrElse(normalizedChildren.headOption.getOrElse(node))
      val args = argList.map(_.children).getOrElse(Nil)
      classNode :: args
    else
      normalizedChildren
    node.copy(kind = nKind, children = children)
