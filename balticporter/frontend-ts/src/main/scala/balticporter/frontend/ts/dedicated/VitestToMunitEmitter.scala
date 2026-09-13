package balticporter.frontend.ts.dedicated

import balticporter.frontend.ts.{RastFile, RastNode, RastValue}
import scala.collection.mutable

/** Translates vitest/jest test files (`.spec.ts`, `.spec.js`) from RAST to
  * MUnit test suites in Scala.
  *
  * Handles `describe`/`it`/`expect` blocks and the common assertion matchers:
  * `toBe`, `toEqual`, `toStrictEqual`, `toBeTruthy`, `toBeFalsy`, `toBeNull`,
  * `toBeUndefined`, `toContain`, `toHaveLength`, `toThrow`, `toThrowError`,
  * `toMatchInlineSnapshot`, `toThrowErrorMatchingInlineSnapshot`.
  *
  * Also handles `beforeEach` (hoisted to each test body), `it.todo` (emitted as
  * `.ignore`), `not` negation, `resolves`/`rejects` chains, and nested
  * `describe` blocks (flattened into test name prefixes).
  *
  * This is capability N12 from the genuine translation plan. */
object VitestToMunitEmitter:

  final case class EmitConfig(
      packageName: String,
      className: String,
      imports: List[String] = Nil,
  )

  /** Result of emitting a test suite from RAST. */
  final case class EmitResult(
      scala: String,
      testCount: Int,
      ignoredCount: Int,
      /** Assertion patterns found, with counts. */
      assertionCounts: Map[String, Int],
  )

  /** Emit a vitest/jest RAST file as an MUnit test suite. */
  def emit(file: RastFile, config: EmitConfig): EmitResult =
    val ctx = new EmitContext(config)
    ctx.emitFile(file)

  // --------------------------------------------------------------------------
  // Private implementation
  // --------------------------------------------------------------------------

  private class EmitContext(config: EmitConfig):
    private val sb = new StringBuilder
    private var testCount = 0
    private var ignoredCount = 0
    private val assertionCounts = mutable.Map.empty[String, Int].withDefaultValue(0)

    def emitFile(file: RastFile): EmitResult =
      sb.append(s"package ${config.packageName}\n\n")
      for imp <- config.imports do sb.append(s"import $imp\n")
      if config.imports.nonEmpty then sb.append("\n")
      sb.append("class ")
      sb.append(config.className)
      sb.append(" extends munit.FunSuite:\n")

      // Walk top-level nodes for describe blocks
      for node <- file.nodes do
        node.kind match
          case "ExpressionStatement" =>
            node.children.headOption.foreach { call =>
              processTopLevel(call, prefix = Nil, beforeEachNodes = Nil)
            }
          case _ => ()

      EmitResult(sb.toString, testCount, ignoredCount, assertionCounts.toMap)

    /** Process a node at any nesting level, looking for describe/it/beforeEach. */
    private def processTopLevel(
        node: RastNode,
        prefix: List[String],
        beforeEachNodes: List[RastNode],
    ): Unit =
      if node.kind != "CallExpression" then return

      val callee = node.children.headOption.getOrElse(return)
      val calleeInfo = identifyCallee(callee)

      calleeInfo match
        case ("describe", false) =>
          val name = extractStringArg(node, 1)
          val body = extractArrowBody(node, 2)
          val newPrefix = prefix :+ name
          // Scan for beforeEach in this describe scope
          val localBeforeEach = mutable.ListBuffer.empty[RastNode]
          localBeforeEach ++= beforeEachNodes

          body.foreach { block =>
            // First pass: collect beforeEach
            for stmt <- block.children do
              stmt.children.headOption.foreach { call =>
                val info = identifyCallee(call.children.headOption.getOrElse(call))
                if info._1 == "beforeEach" then
                  extractArrowBody(call, 1).foreach { bBody =>
                    localBeforeEach ++= bBody.children
                  }
              }
            // Second pass: process it/describe
            for stmt <- block.children do
              stmt.children.headOption.foreach { call =>
                processTopLevel(call, newPrefix, localBeforeEach.toList)
              }
          }

        case ("it", todo) =>
          val name = extractStringArg(node, 1)
          val body = extractArrowBody(node, 2)
          emitTest(prefix, name, body, beforeEachNodes, todo)

        case _ => ()

    /** Identify what a callee node represents. Returns (name, isTodo). */
    private def identifyCallee(node: RastNode): (String, Boolean) =
      node.kind match
        case "Identifier" =>
          (node.text.getOrElse(""), false)
        case "PropertyAccessExpression" if node.children.size >= 2 =>
          val base = node.children.head
          val prop = node.children.last
          val baseName = base.text.getOrElse("")
          val propName = prop.text.getOrElse("")
          if baseName == "it" && propName == "todo" then ("it", true)
          else (s"$baseName.$propName", false)
        case _ => ("", false)

    /** Extract a string argument from a CallExpression at the given position. */
    private def extractStringArg(call: RastNode, pos: Int): String =
      call.children.lift(pos).flatMap { n =>
        n.value match
          case Some(RastValue.Str(s)) => Some(s)
          case _ => n.text
      }.getOrElse("")

    /** Extract the body Block from an ArrowFunction/FunctionExpression at the given position.
      *
      * A braceless arrow like `() => expr` has the expression as a direct child
      * (no Block wrapper). In that case we synthesize a Block containing one
      * ExpressionStatement. */
    private def extractArrowBody(call: RastNode, pos: Int): Option[RastNode] =
      call.children.lift(pos).flatMap { fn =>
        if fn.kind == "ArrowFunction" || fn.kind == "FunctionExpression" then
          fn.children.find(_.kind == "Block").orElse {
            // Braceless arrow: find the first non-Parameter child
            val bodyExpr = fn.children.filterNot(_.kind == "Parameter").headOption
            bodyExpr.map { expr =>
              // Wrap in a synthetic Block with an ExpressionStatement
              val exprStmt = RastNode("ExpressionStatement", 0, (0, 0), children = List(expr))
              RastNode("Block", 0, (0, 0), children = List(exprStmt))
            }
          }
        else None
      }

    /** Emit a single test case. */
    private def emitTest(
        prefix: List[String],
        name: String,
        body: Option[RastNode],
        beforeEach: List[RastNode],
        isTodo: Boolean,
    ): Unit =
      testCount += 1
      if isTodo then ignoredCount += 1

      val fullName = (prefix :+ name).mkString(" > ")
      val escapedName = fullName.replace("\"", "\\\"")

      sb.append("\n")
      if isTodo then
        sb.append(s"""  test("$escapedName".ignore):\n""")
      else
        sb.append(s"""  test("$escapedName"):\n""")

      // Emit beforeEach body
      for node <- beforeEach do
        emitStatement(node, "    ")

      // Emit test body
      body.foreach { block =>
        for stmt <- block.children do
          emitStatement(stmt, "    ")
      }

      // If body was empty, emit a placeholder
      if beforeEach.isEmpty && body.forall(_.children.isEmpty) then
        sb.append("    ()\n")

    // --------------------------------------------------------------------------
    // Statement emission
    // --------------------------------------------------------------------------

    private def emitStatement(node: RastNode, indent: String): Unit =
      node.kind match
        case "ExpressionStatement" =>
          node.children.headOption.foreach { expr =>
            val unwrapped = unwrapAwait(expr)
            if isExpectChain(unwrapped) then
              emitExpectAssertion(unwrapped, indent)
            else
              val code = emitExpr(unwrapped)
              sb.append(s"$indent$code\n")
          }

        case "VariableStatement" =>
          node.children.find(_.kind == "VariableDeclarationList").foreach { vdl =>
            for vd <- vdl.children.filter(_.kind == "VariableDeclaration") do
              val name = vd.children.find(_.kind == "Identifier").flatMap(_.text).getOrElse("_")
              val isConst = vd.flags.contains("const") || vdl.flags.contains("const") ||
                node.flags.contains("const")
              val keyword = if isConst then "val" else "var"
              val init = vd.children.drop(1).lastOption.map(emitExpr).getOrElse("???")
              sb.append(s"$indent$keyword $name = $init\n")
          }

        case "ReturnStatement" =>
          val expr = node.children.headOption.map(emitExpr).getOrElse("()")
          sb.append(s"${indent}return $expr\n")

        case "IfStatement" =>
          val children = node.children
          if children.size >= 2 then
            val cond = emitExpr(children.head)
            sb.append(s"${indent}if $cond then\n")
            emitStatement(children(1), indent + "  ")
            if children.size >= 3 then
              sb.append(s"${indent}else\n")
              emitStatement(children(2), indent + "  ")

        case "Block" =>
          for child <- node.children do
            emitStatement(child, indent)

        case _ =>
          val code = emitExpr(node)
          if code.nonEmpty then sb.append(s"$indent$code\n")

    // --------------------------------------------------------------------------
    // Expect/assertion translation
    // --------------------------------------------------------------------------

    /** Check if a node is an expect() assertion chain. */
    private def isExpectChain(node: RastNode): Boolean =
      node.kind match
        case "CallExpression" =>
          val callee = node.children.headOption.getOrElse(return false)
          callee.kind match
            case "Identifier" =>
              callee.text.contains("expect")
            case "PropertyAccessExpression" =>
              // expect(x).toBe(y) or expect(x).not.toBe(y)
              isExpectChain(callee.children.headOption.getOrElse(return false))
            case _ => false
        case "PropertyAccessExpression" =>
          // expect(x).resolves.not or expect(x).not
          isExpectChain(node.children.headOption.getOrElse(return false))
        case _ => false

    /** Parse an expect chain to extract: (subject, assertion, args, negated, resolves, rejects). */
    private case class ExpectInfo(
        subject: RastNode,
        assertion: String,
        args: List[RastNode],
        negated: Boolean,
        resolves: Boolean,
        rejects: Boolean,
    )

    /** Walk from the outermost call inward to find the expect() call and its chain. */
    private def parseExpectChain(node: RastNode): Option[ExpectInfo] =
      // The outer node is a CallExpression like: expect(x).toBe(y)
      // Or a CallExpression like: expect(x).toBeTruthy()
      // Or nested: expect(x).resolves.not.toThrow()
      // Or: expect(x).rejects.toThrow(msg)

      // Collect the chain of property accesses and calls
      val chain = mutable.ListBuffer.empty[String]
      val outerArgs = mutable.ListBuffer.empty[RastNode]
      var current = node
      var expectSubject: Option[RastNode] = None

      // Walk the chain
      def walk(n: RastNode): Unit =
        n.kind match
          case "CallExpression" =>
            val callee = n.children.headOption.getOrElse(return)
            val args = n.children.drop(1)

            callee.kind match
              case "Identifier" if callee.text.contains("expect") =>
                // Found the expect() call - args(0) is the subject
                expectSubject = args.headOption
              case "PropertyAccessExpression" =>
                // e.g. expect(x).toBe
                val prop = callee.children.lastOption.flatMap(_.text).getOrElse("")
                chain.prepend(prop)
                outerArgs.prependAll(args)
                walk(callee.children.headOption.getOrElse(return))
              case "Identifier" if callee.text.contains("expect") =>
                expectSubject = args.headOption
              case _ =>
                walk(callee)

          case "PropertyAccessExpression" =>
            val prop = n.children.lastOption.flatMap(_.text).getOrElse("")
            chain.prepend(prop)
            walk(n.children.headOption.getOrElse(return))

          case _ => ()

      walk(node)

      expectSubject.map { subj =>
        val chainList = chain.toList
        var negated = false
        var resolves = false
        var rejects = false
        var assertion = ""
        val assertArgs = mutable.ListBuffer.empty[RastNode]

        for part <- chainList do
          part match
            case "not"      => negated = true
            case "resolves" => resolves = true
            case "rejects"  => rejects = true
            case other      => assertion = other

        assertArgs ++= outerArgs

        ExpectInfo(subj, assertion, assertArgs.toList, negated, resolves, rejects)
      }

    /** Emit an expect assertion chain as Scala MUnit assertions. */
    private def emitExpectAssertion(node: RastNode, indent: String): Unit =
      parseExpectChain(node) match
        case None =>
          sb.append(s"$indent${emitExpr(node)}\n")
        case Some(info) =>
          val subjExpr = emitExpr(info.subject)

          // Handle resolves/rejects wrapper
          if info.rejects then
            emitRejectsAssertion(info, subjExpr, indent)
          else if info.resolves then
            emitResolvesAssertion(info, subjExpr, indent)
          else
            emitDirectAssertion(info, subjExpr, indent)

    private def emitDirectAssertion(info: ExpectInfo, subjExpr: String, indent: String): Unit =
      val assertion = info.assertion
      val args = info.args
      val negated = info.negated

      assertion match
        case "toBe" | "toEqual" | "toStrictEqual" =>
          val expected = args.headOption.map(emitExpr).getOrElse("???")
          if negated then
            assertionCounts("assertNotEquals") += 1
            sb.append(s"${indent}assertNotEquals($subjExpr, $expected)\n")
          else
            assertionCounts("assertEquals") += 1
            sb.append(s"${indent}assertEquals($subjExpr, $expected)\n")

        case "toBeTruthy" =>
          assertionCounts("assert") += 1
          if negated then
            sb.append(s"${indent}assert(!$subjExpr)\n")
          else
            sb.append(s"${indent}assert($subjExpr)\n")

        case "toBeFalsy" =>
          assertionCounts("assert") += 1
          if negated then
            sb.append(s"${indent}assert($subjExpr)\n")
          else
            sb.append(s"${indent}assert(!$subjExpr)\n")

        case "toBeNull" | "toBeUndefined" =>
          if negated then
            assertionCounts("assertNotEquals") += 1
            sb.append(s"${indent}assertNotEquals($subjExpr, null)\n")
          else
            assertionCounts("assertEquals") += 1
            sb.append(s"${indent}assertEquals($subjExpr, null)\n")

        case "toContain" =>
          val expected = args.headOption.map(emitExpr).getOrElse("???")
          assertionCounts("assert.contains") += 1
          if negated then
            sb.append(s"${indent}assert(!$subjExpr.contains($expected))\n")
          else
            sb.append(s"${indent}assert($subjExpr.contains($expected))\n")

        case "toHaveLength" =>
          val expected = args.headOption.map(emitExpr).getOrElse("???")
          assertionCounts("assertEquals.length") += 1
          if negated then
            sb.append(s"${indent}assertNotEquals($subjExpr.length, $expected)\n")
          else
            sb.append(s"${indent}assertEquals($subjExpr.length, $expected)\n")

        case "toThrow" | "toThrowError" =>
          assertionCounts("intercept") += 1
          if args.nonEmpty then
            val msg = args.headOption.map(emitExpr).getOrElse("\"\"")
            sb.append(s"${indent}val _e = intercept[Exception] { $subjExpr }\n")
            sb.append(s"${indent}assert(_e.getMessage.contains($msg))\n")
          else
            sb.append(s"${indent}intercept[Exception] { $subjExpr }\n")

        case "toMatchInlineSnapshot" =>
          assertionCounts("assertEquals.snapshot") += 1
          val snapshot = args.headOption.map(emitExpr).getOrElse("\"\"")
          if negated then
            sb.append(s"${indent}assertNotEquals($subjExpr.toString, $snapshot)\n")
          else
            sb.append(s"${indent}assertEquals($subjExpr.toString, $snapshot)\n")

        case "toThrowErrorMatchingInlineSnapshot" =>
          assertionCounts("intercept.snapshot") += 1
          val snapshot = args.headOption.map(emitExpr).getOrElse("\"\"")
          sb.append(s"${indent}val _e = intercept[Exception] { $subjExpr }\n")
          sb.append(s"${indent}assert(_e.getMessage.contains($snapshot))\n")

        case other =>
          assertionCounts(s"unhandled:$other") += 1
          sb.append(s"$indent// TODO: expect($subjExpr).$other(...)\n")

    private def emitResolvesAssertion(info: ExpectInfo, subjExpr: String, indent: String): Unit =
      info.assertion match
        case "toThrow" if info.negated =>
          // resolves.not.toThrow: just call it, no exception means pass
          assertionCounts("resolves.not.toThrow") += 1
          sb.append(s"$indent$subjExpr\n")
        case "toBeUndefined" =>
          // resolves.toBeUndefined: just call it
          assertionCounts("resolves.toBeUndefined") += 1
          sb.append(s"$indent$subjExpr\n")
        case "toBe" | "toEqual" | "toStrictEqual" =>
          val expected = info.args.headOption.map(emitExpr).getOrElse("???")
          assertionCounts("assertEquals") += 1
          sb.append(s"${indent}assertEquals($subjExpr, $expected)\n")
        case _ =>
          assertionCounts(s"resolves.${info.assertion}") += 1
          sb.append(s"$indent$subjExpr // resolves.${info.assertion}\n")

    private def emitRejectsAssertion(info: ExpectInfo, subjExpr: String, indent: String): Unit =
      info.assertion match
        case "toThrow" | "toThrowError" =>
          assertionCounts("intercept") += 1
          if info.args.nonEmpty then
            val msg = info.args.headOption.map(emitExpr).getOrElse("\"\"")
            sb.append(s"${indent}val _e = intercept[Exception] { $subjExpr }\n")
            sb.append(s"${indent}assert(_e.getMessage.contains($msg))\n")
          else
            sb.append(s"${indent}intercept[Exception] { $subjExpr }\n")
        case "toThrowErrorMatchingInlineSnapshot" =>
          assertionCounts("intercept.snapshot") += 1
          val snapshot = info.args.headOption.map(emitExpr).getOrElse("\"\"")
          sb.append(s"${indent}val _e = intercept[Exception] { $subjExpr }\n")
          sb.append(s"${indent}assert(_e.getMessage.contains($snapshot))\n")
        case _ =>
          assertionCounts(s"rejects.${info.assertion}") += 1
          sb.append(s"${indent}intercept[Exception] { $subjExpr } // rejects.${info.assertion}\n")

    // --------------------------------------------------------------------------
    // Expression emission (simplified for test bodies)
    // --------------------------------------------------------------------------

    /** Unwrap AwaitExpression to the inner expression. */
    private def unwrapAwait(node: RastNode): RastNode =
      if node.kind == "AwaitExpression" then
        node.children.headOption.getOrElse(node)
      else node

    private def emitExpr(node: RastNode): String =
      node.kind match
        case "Identifier" =>
          node.text.getOrElse("_")

        case "NumericLiteral" =>
          node.value match
            case Some(RastValue.Num(n)) =>
              if n == n.toLong then n.toLong.toString
              else n.toString
            case _ => "0"

        case "StringLiteral" =>
          node.value match
            case Some(RastValue.Str(s)) => quoteString(s)
            case _ => "\"\""

        case "NoSubstitutionTemplateLiteral" =>
          node.value match
            case Some(RastValue.Str(s)) => quoteString(s)
            case _ => "\"\""

        case "TemplateExpression" =>
          emitTemplateExpr(node)

        case "TrueKeyword" => "true"
        case "FalseKeyword" => "false"
        case "NullKeyword" => "null"

        case "CallExpression" =>
          val callee = node.children.headOption.map(emitExpr).getOrElse("???")
          val args = node.children.drop(1).map(emitExpr)
          s"$callee(${args.mkString(", ")})"

        case "PropertyAccessExpression" =>
          val parts = node.children.map(emitExpr)
          parts.mkString(".")

        case "ElementAccessExpression" =>
          val obj = node.children.headOption.map(emitExpr).getOrElse("???")
          val idx = node.children.lastOption.map(emitExpr).getOrElse("0")
          s"$obj($idx)"

        case "BinaryExpression" =>
          val left = node.children.headOption.map(emitExpr).getOrElse("???")
          val right = node.children.lastOption.map(emitExpr).getOrElse("???")
          val op = node.operator.map(translateOp).getOrElse("???")
          s"$left $op $right"

        case "PrefixUnaryExpression" =>
          val operand = node.children.headOption.map(emitExpr).getOrElse("???")
          val op = node.operator.map(translateUnaryOp).getOrElse("!")
          s"$op$operand"

        case "PostfixUnaryExpression" =>
          val operand = node.children.headOption.map(emitExpr).getOrElse("???")
          val op = node.operator.map(translateUnaryOp).getOrElse("++")
          s"{ val _p = $operand; $operand $op 1; _p }"

        case "ParenthesizedExpression" =>
          val inner = node.children.headOption.map(emitExpr).getOrElse("???")
          s"($inner)"

        case "ArrayLiteralExpression" =>
          val elems = node.children.map { c =>
            if c.kind == "SpreadElement" then
              c.children.headOption.map(emitExpr).getOrElse("???") + "*"
            else emitExpr(c)
          }
          s"Vector(${elems.mkString(", ")})"

        case "SpreadElement" =>
          val inner = node.children.headOption.map(emitExpr).getOrElse("???")
          s"$inner*"

        case "ObjectLiteralExpression" =>
          val props = node.children.map { p =>
            val key = p.children.headOption.flatMap(_.text).getOrElse("_")
            val value = p.children.drop(1).headOption.map(emitExpr).getOrElse("???")
            s"\"$key\" -> $value"
          }
          s"Map(${props.mkString(", ")})"

        case "ArrowFunction" | "FunctionExpression" =>
          val params = node.children.filter(_.kind == "Parameter").map { p =>
            p.children.find(_.kind == "Identifier").flatMap(_.text).getOrElse("_")
          }
          val body = node.children.find(_.kind == "Block")
          body match
            case Some(b) =>
              val bodyStr = b.children.map { s =>
                s.children.headOption.map(emitExpr).getOrElse(emitExpr(s))
              }.mkString("; ")
              if params.isEmpty then s"{ () => $bodyStr }"
              else s"{ (${params.mkString(", ")}) => $bodyStr }"
            case None =>
              val bodyExpr = node.children.filterNot(_.kind == "Parameter")
                .lastOption.map(emitExpr).getOrElse("???")
              if params.isEmpty then s"() => $bodyExpr"
              else s"(${params.mkString(", ")}) => $bodyExpr"

        case "ConditionalExpression" =>
          val cond = node.children.headOption.map(emitExpr).getOrElse("???")
          val thenE = node.children.lift(1).map(emitExpr).getOrElse("???")
          val elseE = node.children.lift(2).map(emitExpr).getOrElse("???")
          s"if $cond then $thenE else $elseE"

        case "NewExpression" =>
          val cls = node.children.headOption.map(emitExpr).getOrElse("???")
          val args = node.children.drop(1).map(emitExpr)
          s"new $cls(${args.mkString(", ")})"

        case "AwaitExpression" =>
          // In Scala context we strip await
          node.children.headOption.map(emitExpr).getOrElse("???")

        case "TypeOfExpression" =>
          val operand = node.children.headOption.map(emitExpr).getOrElse("???")
          s"typeof($operand)"

        case "AsExpression" | "TypeAssertionExpression" =>
          // Type assertions: just emit the expression part
          node.children.headOption.map(emitExpr).getOrElse("???")

        case "VoidExpression" =>
          node.children.headOption.map(emitExpr).getOrElse("()")
          "()"

        case _ =>
          node.text.getOrElse(s"??? /* ${node.kind} */")

    private def emitTemplateExpr(node: RastNode): String =
      val parts = node.children.map { c =>
        c.kind match
          case "TemplateHead" | "TemplateMiddle" | "TemplateTail" =>
            c.value match
              case Some(RastValue.Str(s)) => escapeStringContent(s)
              case _ => ""
          case "TemplateSpan" =>
            c.children.headOption.map(inner => s"$${${emitExpr(inner)}}").getOrElse("")
          case _ =>
            s"$${${emitExpr(c)}}"
      }
      s"""s\"\"\"${parts.mkString}\"\"\""""

    private def translateOp(op: String): String = op match
      case "EqualsEqualsEqualsToken" => "=="
      case "ExclamationEqualsEqualsToken" => "!="
      case "EqualsEqualsToken" => "==" // loose equality, best effort
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
      case "InstanceOfKeyword" => ".isInstanceOf"
      case other => other

    private def translateUnaryOp(op: String): String = op match
      case "ExclamationToken" => "!"
      case "MinusToken" => "-"
      case "PlusToken" => "+"
      case "TildeToken" => "~"
      case "PlusPlusToken" => "+="
      case "MinusMinusToken" => "-="
      case other => other

    private def quoteString(s: String): String =
      val escaped = escapeStringContent(s)
      if s.contains('\n') || s.contains('"') then
        // Use triple-quoted string for multiline or strings with quotes
        s"\"\"\"$s\"\"\""
      else
        s"\"$escaped\""

    private def escapeStringContent(s: String): String =
      s.replace("\\", "\\\\")
       .replace("\"", "\\\"")
       .replace("\n", "\\n")
       .replace("\r", "\\r")
       .replace("\t", "\\t")
