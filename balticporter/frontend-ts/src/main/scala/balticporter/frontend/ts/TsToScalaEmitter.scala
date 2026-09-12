package balticporter.frontend.ts

import scala.collection.mutable

/** Direct TypeScript → Scala source emitter for RAST v1.
  *
  * Reads the resolved AST produced by the Node.js exporter and emits
  * Scala 3 source text. This is the path-data-parser proof of concept:
  * enough lowering to produce compiling Scala from the 4 helper libraries.
  *
  * Each TS module becomes a Scala object. Interfaces with only typed fields
  * become case classes. Functions become methods on the module object. */
object TsToScalaEmitter:

  final case class EmitConfig(
      packageName: String,
      imports: List[String] = Nil,
  )

  def emit(files: List[RastFile], config: EmitConfig): Map[String, String] =
    val ctx = new EmitContext(config)
    files.flatMap { f =>
      val fileName = f.path.split('/').last.stripSuffix(".ts")
      if fileName == "index" then None
      else
        val scala = ctx.emitFile(f, fileName)
        Some(fileName -> scala)
    }.toMap

  private class EmitContext(config: EmitConfig):
    private val sb = new StringBuilder
    private val typeAliasMap = mutable.Map.empty[String, String]

    def emitFile(file: RastFile, fileName: String): String =
      sb.clear()
      val objectName = capitalize(fileName)

      sb.append(s"package ${config.packageName}\n\n")
      for (imp <- config.imports) sb.append(s"import $imp\n")
      if config.imports.nonEmpty then sb.append("\n")

      // Collect top-level declarations
      val interfaces = file.nodes.filter(_.kind == "InterfaceDeclaration")
      val functions = file.nodes.filter(_.kind == "FunctionDeclaration")
      val variables = file.nodes.filter(n =>
        n.kind == "FirstStatement" || n.kind == "VariableStatement")
      val typeAliases = file.nodes.filter(_.kind == "TypeAliasDeclaration")

      // Resolve type aliases FIRST so they're available during emission
      for (ta <- typeAliases)
        val taName = nameOf(ta)
        // Try RAST type field first, then inspect children (UnionType, etc.)
        val tpe = ta.`type`.flatMap(file.types.get).map(rastTypeToScala(_, file)).getOrElse {
          val unionChild = ta.children.find(_.kind == "UnionType")
          unionChild match
            case Some(u) =>
              val memberKinds = u.children.map(_.kind)
              // All numeric literals → Int
              if memberKinds.forall(k => k == "LiteralType" || k == "NumericLiteral") then "Int"
              // All string literals → String
              else if memberKinds.forall(k => k == "LiteralType" || k == "StringLiteral") then "String"
              else "Any"
            case None =>
              // Single type child
              ta.children.find(c => c.kind != "Identifier").map(syntaxTypeToScala).getOrElse("Any")
        }
        typeAliasMap(taName) = tpe

      // Emit exported interfaces as case classes
      for (iface <- interfaces)
        val name = nameOf(iface)
        if isExported(iface) then
          emitCaseClass(iface, file)

      // Emit the module object with functions and variables
      sb.append(s"object $objectName:\n\n")

      // Private interfaces as nested case classes
      for (iface <- interfaces if !isExported(iface))
        emitCaseClass(iface, file, indent = "  ")

      // Constants and variables
      for (vs <- variables)
        emitVariableStatement(vs, file, "  ")

      // Functions
      for (fn <- functions)
        emitFunction(fn, file, "  ")

      sb.toString

    private def emitCaseClass(node: RastNode, file: RastFile, indent: String = ""): Unit =
      val name = nameOf(node)
      val props = node.children.filter(c =>
        c.kind == "PropertySignature" || c.kind == "PropertyDeclaration")
      val params = props.map { p =>
        val pName = nameOf(p)
        val escapedName = if scalaKeywords.contains(pName) then s"`$pName`" else pName
        val pType = resolveScalaType(p, file)
        s"$escapedName: $pType"
      }
      sb.append(s"${indent}final case class $name(${params.mkString(", ")})\n\n")

    private def emitFunction(node: RastNode, file: RastFile, indent: String): Unit =
      val name = nameOf(node)
      val isPrivate = !isExported(node)
      val params = node.children.filter(_.kind == "Parameter")
      val retType = resolveFunctionReturnType(node, file)
      val body = node.children.find(_.kind == "Block")
      val vis = if isPrivate then "private " else ""

      // R7: detect mutated arrays in this function body
      body.foreach(b => currentMutatedArrays = collectMutatedArrays(b))

      // Detect reassigned parameters — need local var copies
      val reassignedParams = body.map(b => collectReassignedVars(b, params.map(nameOf).toSet)).getOrElse(Set.empty)

      val paramList = params.map { p =>
        val pName = nameOf(p)
        val isReassigned = reassignedParams.contains(pName)
        val sigName = if isReassigned then s"${pName}0" else pName
        val escapedName = if scalaKeywords.contains(sigName) then s"`$sigName`" else sigName
        val pType = resolveScalaType(p, file)
        s"$escapedName: $pType"
      }

      sb.append(s"$indent${vis}def $name(${paramList.mkString(", ")}): $retType =\n")
      // Emit var copies for reassigned params
      for (p <- params if reassignedParams.contains(nameOf(p)))
        val pName = nameOf(p)
        val pType = resolveScalaType(p, file)
        sb.append(s"${indent}  var $pName: $pType = ${pName}0\n")
      body match
        case Some(block) => emitBlock(block, file, indent + "  ")
        case None => sb.append(s"${indent}  ???\n")
      currentMutatedArrays = Set.empty
      sb.append("\n")

    // R7: track which variables are mutated via .push() — they need ArrayBuffer
    private var currentMutatedArrays: Set[String] = Set.empty

    private def collectMutatedArrays(body: RastNode): Set[String] =
      val result = mutable.Set.empty[String]
      def walk(n: RastNode): Unit =
        // Detect .push() calls
        if n.kind == "CallExpression" then
          val fn = n.children.headOption
          fn.foreach { f =>
            if f.kind == "PropertyAccessExpression" then
              val method = f.children.lastOption.flatMap(_.text)
              if method.contains("push") then
                val obj = f.children.headOption.flatMap(_.text).orElse(
                  f.children.headOption.map(c => nameOf(c)))
                obj.foreach(result += _)
          }
        // Detect arr[arr.length] = x patterns (R15)
        if n.kind == "BinaryExpression" then
          val lhs = n.children.headOption
          lhs.foreach { l =>
            if l.kind == "ElementAccessExpression" then
              val arrName = l.children.headOption.flatMap(_.text)
              arrName.foreach(result += _)
          }
        // Detect arr += x patterns
        if n.kind == "BinaryExpression" && n.operator.exists(o => o == "PlusEqualsToken" || o == "FirstCompoundAssignment") then
          n.children.headOption.flatMap(_.text).foreach(result += _)
        n.children.foreach(walk)
      walk(body)
      result.toSet

    private def emitVariableStatement(node: RastNode, file: RastFile, indent: String): Unit =
      val decls = node.children.flatMap { child =>
        if child.kind == "VariableDeclarationList" then
          child.children.filter(_.kind == "VariableDeclaration")
        else Nil
      }
      for (d <- decls)
        val name = nameOf(d)
        val isConst = d.flags.contains("const")
        val keyword = if isConst then "val" else "var"
        val rawType = resolveScalaType(d, file)
        // R7: if this array is mutated via .push(), use ArrayBuffer
        val isMutatedArray = currentMutatedArrays.contains(name) && rawType.startsWith("Vector[")
        val tpe = if isMutatedArray then rawType.replace("Vector[", "ArrayBuffer[") else rawType
        // R8: integer variables — Double initialized with int literal → Int
        val tpeFixed = if tpe == "Double" then
          val initText = findInitializer(d, file)
          if initText.matches("-?\\d+") then "Int" else tpe
        else tpe
        val init = findInitializer(d, file)
        val initFixed = if isMutatedArray && (init == "Vector.empty" || init.startsWith("ArrayBuffer")) then
          tpe.replace("ArrayBuffer[", "ArrayBuffer.empty[").stripSuffix("]") + "]"
        else init
        sb.append(s"$indent$keyword $name: $tpeFixed = $initFixed\n")

    private def emitBlock(block: RastNode, file: RastFile, indent: String): Unit =
      val stmts = block.children
      for (stmt <- stmts)
        emitStatement(stmt, file, indent)

    private def emitStatement(node: RastNode, file: RastFile, indent: String): Unit =
      node.kind match
        case "ReturnStatement" =>
          val expr = node.children.headOption.map(c => emitExpr(c, file)).getOrElse("()")
          // If returning a bare mutable collection variable, convert to immutable
          val converted = if currentMutatedArrays.contains(expr.trim) then
            s"$expr.toVector"
          else expr
          sb.append(s"${indent}return $converted\n")

        case "IfStatement" =>
          // R14: if (d.match(regex)) { ... RegExp.$1 ... } → regex.findPrefixMatchOf(d) match { ... }
          val condNode = node.children.head
          val regexMatch = detectRegexMatch(condNode)
          regexMatch match
            case Some((obj, regex)) =>
              sb.append(s"$indent$regex.findPrefixMatchOf($obj) match\n")
              sb.append(s"$indent  case Some(_m) =>\n")
              emitStatement(node.children(1), file, indent + "    ")
              if node.children.length > 2 then
                sb.append(s"$indent  case None =>\n")
                emitStatement(node.children(2), file, indent + "    ")
              else
                sb.append(s"$indent  case None => ()\n")
            case None =>
              val cond = emitExpr(condNode, file)
              sb.append(s"${indent}if ($cond) then\n")
              emitStatement(node.children(1), file, indent + "  ")
              if node.children.length > 2 then
                sb.append(s"${indent}else\n")
                emitStatement(node.children(2), file, indent + "  ")

        case "Block" =>
          emitBlock(node, file, indent)

        case "ExpressionStatement" =>
          val expr = emitExpr(node.children.head, file)
          sb.append(s"$indent$expr\n")

        case "FirstStatement" | "VariableStatement" =>
          emitVariableStatement(node, file, indent)

        case "ForOfStatement" | "ForInStatement" =>
          emitForOf(node, file, indent)

        case "ForStatement" =>
          emitForLoop(node, file, indent)

        case "WhileStatement" =>
          val cond = emitExpr(node.children.head, file)
          sb.append(s"${indent}while ($cond)\n")
          emitStatement(node.children(1), file, indent + "  ")

        case "SwitchStatement" =>
          emitSwitch(node, file, indent)

        case "ThrowStatement" =>
          val expr = emitExpr(node.children.head, file)
          sb.append(s"${indent}throw $expr\n")

        case "BreakStatement" =>
          sb.append(s"${indent}// break\n")

        case _ =>
          sb.append(s"$indent// TODO: ${node.kind}\n")

    private def emitForOf(node: RastNode, file: RastFile, indent: String): Unit =
      val binding = node.children.find(c =>
        c.kind == "VariableDeclarationList" || c.kind == "VariableDeclaration")
      val iterable = node.children.find(c =>
        c.kind != "VariableDeclarationList" && c.kind != "VariableDeclaration" && c.kind != "Block")
      val body = node.children.find(_.kind == "Block")

      val bindingName = binding.map(b => {
        val decls = if b.kind == "VariableDeclarationList" then
          b.children.filter(_.kind == "VariableDeclaration")
        else List(b)
        decls.headOption.map(nameOf).getOrElse("item")
      }).getOrElse("item")

      // Check for destructuring: { key, data }
      val destructured = binding.flatMap { b =>
        val decl = if b.kind == "VariableDeclarationList" then
          b.children.find(_.kind == "VariableDeclaration")
        else Some(b)
        decl.flatMap(_.children.find(_.kind == "ObjectBindingPattern"))
      }

      val iterExpr = iterable.map(emitExpr(_, file)).getOrElse("???")

      destructured match
        case Some(pattern) =>
          val fields = pattern.children.filter(_.kind == "BindingElement").map(nameOf)
          sb.append(s"${indent}for (_item <- $iterExpr)\n")
          for (f <- fields)
            sb.append(s"${indent}  val $f = _item.$f\n")
          body.foreach(b => emitBlock(b, file, indent + "  "))
        case None =>
          sb.append(s"${indent}for ($bindingName <- $iterExpr)\n")
          body.foreach(b => emitBlock(b, file, indent + "  "))

    private def emitForLoop(node: RastNode, file: RastFile, indent: String): Unit =
      // R12: C-style for(init; cond; update) body → { init; while(cond) { body; update } }
      // Children after R1 filtering: init, cond, update, body (may be missing some)
      val children = node.children.toArray
      if children.length >= 4 then
        // Init might be VariableDeclarationList — wrap as VariableStatement
        val initChild = children(0)
        if initChild.kind == "VariableDeclarationList" then
          // Emit each declaration
          val decls = initChild.children.filter(_.kind == "VariableDeclaration")
          for (d <- decls)
            val n = nameOf(d)
            val isConst = d.flags.contains("const")
            val kw = if isConst then "val" else "var"
            // For-loop init: the init expr is the last child after the name
            val initExpr = d.children.drop(1).lastOption.map(emitExpr(_, file)).getOrElse("0")
            // R8: for-loop variables are typically Int (used as indices)
            sb.append(s"$indent$kw $n: Int = $initExpr\n")
        else
          emitStatement(initChild, file, indent)
        val cond = emitExpr(children(1), file)
        sb.append(s"${indent}while ($cond)\n")
        emitStatement(children(3), file, indent + "  ")
        // update
        val update = emitExpr(children(2), file)
        sb.append(s"${indent}  $update\n")
      else if children.length >= 3 then
        val cond = emitExpr(children(0), file)
        sb.append(s"${indent}while ($cond)\n")
        emitStatement(children(2), file, indent + "  ")
        val update = emitExpr(children(1), file)
        sb.append(s"${indent}  $update\n")
      else
        sb.append(s"$indent// TODO: for loop with ${children.length} children\n")

    private def emitSwitch(node: RastNode, file: RastFile, indent: String): Unit =
      val scrutinee = emitExpr(node.children.head, file)
      val caseBlock = node.children.find(_.kind == "CaseBlock")
      sb.append(s"$indent$scrutinee match\n")
      caseBlock.foreach { cb =>
        val clauses = cb.children.toArray
        var i = 0
        while i < clauses.length do
          clauses(i).kind match
            case "CaseClause" =>
              val stmts = clauses(i).children.drop(1).filter(s => s.kind != "BreakStatement")
              if stmts.isEmpty && i + 1 < clauses.length && clauses(i + 1).kind == "CaseClause" then
                // R11: merge empty case with next — collect all empty adjacent cases
                val values = mutable.ArrayBuffer[String]()
                values += clauses(i).children.headOption.map(emitExpr(_, file)).getOrElse("_")
                while i + 1 < clauses.length && clauses(i + 1).kind == "CaseClause" &&
                      clauses(i + 1).children.drop(1).filter(_.kind != "BreakStatement").isEmpty &&
                      i + 2 < clauses.length do
                  i += 1
                  values += clauses(i).children.headOption.map(emitExpr(_, file)).getOrElse("_")
                // Next case has the body
                i += 1
                if i < clauses.length && clauses(i).kind == "CaseClause" then
                  values += clauses(i).children.headOption.map(emitExpr(_, file)).getOrElse("_")
                  sb.append(s"$indent  case ${values.mkString(" | ")} =>\n")
                  clauses(i).children.drop(1).filter(_.kind != "BreakStatement")
                    .foreach(s => emitStatement(s, file, indent + "    "))
                else
                  sb.append(s"$indent  case ${values.mkString(" | ")} =>\n")
              else
                val value = clauses(i).children.headOption.map(emitExpr(_, file)).getOrElse("_")
                sb.append(s"$indent  case $value =>\n")
                stmts.foreach(s => emitStatement(s, file, indent + "    "))
            case "DefaultClause" =>
              sb.append(s"$indent  case _ =>\n")
              clauses(i).children.filter(_.kind != "BreakStatement")
                .foreach(s => emitStatement(s, file, indent + "    "))
            case _ => ()
          i += 1
      }

    private def emitExpr(node: RastNode, file: RastFile): String =
      node.kind match
        case "Identifier" =>
          val name = node.text.getOrElse("???")
          if scalaKeywords.contains(name) then s"`$name`" else name

        case "NumericLiteral" | "FirstLiteralToken" =>
          node.value match
            case Some(RastValue.Num(v)) =>
              if v == v.toLong then v.toLong.toString
              else v.toString
            case _ => "0"

        case "StringLiteral" | "FirstTemplateToken" | "LastTemplateToken" =>
          val text = node.value match
            case Some(RastValue.Str(s)) => s
            case _ => node.text.getOrElse("")
          "\"" + escapeString(text) + "\""

        case "NoSubstitutionTemplateLiteral" =>
          val text = node.value match
            case Some(RastValue.Str(s)) => s
            case _ => node.text.getOrElse("")
          "\"" + escapeString(text) + "\""

        case "TrueKeyword" => "true"
        case "FalseKeyword" => "false"
        case "NullKeyword" => "null"

        case "BinaryExpression" =>
          val op = node.operator.map(tsOpToScala).getOrElse("???")
          val right = emitExpr(node.children.last, file)
          // R15: arr[arr.length] = x → arr += x
          val lhs = node.children.head
          if op == "=" && lhs.kind == "ElementAccessExpression" then
            val arrObj = emitExpr(lhs.children.head, file)
            val idx = lhs.children.lastOption
            val isAppend = idx.exists { i =>
              i.kind == "PropertyAccessExpression" &&
              i.children.lastOption.flatMap(_.text).contains("length") &&
              emitExpr(i.children.head, file) == arrObj
            }
            if isAppend then s"$arrObj += $right"
            else s"${emitExpr(lhs, file)} $op $right"
          else
            val left = emitExpr(node.children.head, file)
            if op == "=" || op == "+=" || op == "-=" then
              s"$left $op $right"
            else
              s"($left $op $right)"

        case "PrefixUnaryExpression" =>
          val operand = emitExpr(node.children.head, file)
          node.operator match
            case Some("MinusToken") => s"-$operand"
            case Some("PlusToken") => s"$operand.toDouble"
            case Some("ExclamationToken") => s"!$operand"
            case Some("TildeToken") => s"~$operand"
            case Some("PlusPlusToken") => s"{ $operand += 1; $operand }"
            case Some("MinusMinusToken") => s"{ $operand -= 1; $operand }"
            case _ => s"$operand"

        case "PostfixUnaryExpression" =>
          val operand = emitExpr(node.children.head, file)
          node.operator match
            case Some("PlusPlusToken") => s"{ $operand += 1; $operand }"
            case Some("MinusMinusToken") => s"{ $operand -= 1; $operand }"
            case _ => s"$operand"

        case "CallExpression" =>
          val fn = emitExpr(node.children.head, file)
          val args = node.children.drop(1).map(emitExpr(_, file))
          // Rewrite JS-specific calls
          fn match
            case s if s.endsWith(".push") =>
              val obj = s.stripSuffix(".push")
              // Check for spread: tokens.push(...data) → tokens ++= data
              val hasSpread = node.children.drop(1).exists(_.kind == "SpreadElement")
              if hasSpread && args.length == 1 then
                s"$obj ++= ${args.head.stripSuffix("*")}"
              else if args.length == 1 then s"$obj += ${args.head}"
              else args.map(a => s"$obj += $a").mkString("; ")
            case s if s.endsWith(".substr") =>
              val obj = s.stripSuffix(".substr")
              s"$obj.substring(${args.mkString(", ")})"
            case s if s.endsWith(".`match`") || s.endsWith(".match") =>
              val obj = s.stripSuffix(".`match`").stripSuffix(".match")
              s"${args.head}.findPrefixMatchOf($obj).isDefined"
            case s if s.endsWith(".join") =>
              val obj = s.stripSuffix(".join")
              s"$obj.mkString(${args.mkString(", ")})"
            case s if s.endsWith(".map") =>
              val obj = s.stripSuffix(".map")
              s"$obj.map(${args.mkString(", ")})"
            case "parseFloat" =>
              s"${args.head}.toDouble"
            case "Math.abs" =>
              s"Math.abs(${args.mkString(", ")})"
            case "Math.floor" =>
              s"Math.floor(${args.mkString(", ")})"
            case "Math.sqrt" =>
              s"Math.sqrt(${args.mkString(", ")})"
            case "Math.cos" | "Math.sin" | "Math.atan2" | "Math.PI" | "Math.ceil" | "Math.round" | "Math.min" | "Math.max" | "Math.pow" =>
              s"$fn(${args.mkString(", ")})"
            case _ =>
              s"$fn(${args.mkString(", ")})"

        case "PropertyAccessExpression" =>
          val obj = emitExpr(node.children.head, file)
          val prop = node.children.lastOption.flatMap(_.text).getOrElse("???")
          // Rewrite JS-specific property access
          if obj == "RegExp" && prop.startsWith("$") then
            val groupNum = prop.drop(1)
            s"_m.group($groupNum)"
          else if prop == "length" then
            s"$obj.length"
          else if scalaKeywords.contains(prop) then
            s"$obj.`$prop`"
          else
            s"$obj.$prop"

        case "ElementAccessExpression" =>
          val obj = emitExpr(node.children.head, file)
          val idx = emitExpr(node.children.last, file)
          s"$obj($idx)"

        case "ArrayLiteralExpression" =>
          val elems = node.children.map(emitExpr(_, file))
          if elems.isEmpty then "Vector.empty"
          else s"Vector(${elems.mkString(", ")})"

        case "ObjectLiteralExpression" =>
          emitObjectLiteral(node, file)

        case "NewExpression" =>
          val cls = emitExpr(node.children.head, file)
          val args = node.children.drop(1).map(emitExpr(_, file))
          if cls == "Array" then "ArrayBuffer.empty"
          else if cls == "Error" then s"new RuntimeException(${args.mkString(", ")})"
          else s"new $cls(${args.mkString(", ")})"

        case "ParenthesizedExpression" =>
          val inner = emitExpr(node.children.head, file)
          s"($inner)"

        case "ConditionalExpression" =>
          val cond = emitExpr(node.children(0), file)
          val thenE = emitExpr(node.children(1), file)
          val elseE = emitExpr(node.children(2), file)
          s"(if ($cond) $thenE else $elseE)"

        case "ArrowFunction" | "FunctionExpression" =>
          emitArrowFunction(node, file)

        case "SpreadElement" =>
          val expr = emitExpr(node.children.head, file)
          s"$expr*" // note: in a += context this becomes data.foreach(arr += _)

        case "TypeAssertionExpression" | "AsExpression" =>
          emitExpr(node.children.head, file)

        case "TemplateExpression" =>
          emitTemplateExpr(node, file)

        case "VoidExpression" => "()"

        case "RegularExpressionLiteral" =>
          val text = node.value match
            case Some(RastValue.Str(s)) => s
            case _ => node.text.getOrElse("/???/")
          val body = text.stripPrefix("/").reverse.dropWhile(_ != '/').reverse.stripSuffix("/")
          // Escape backslashes for Scala string literal
          val escaped = body.replace("\\", "\\\\")
          s"\"$escaped\".r"

        case "TypeOfExpression" =>
          val operand = emitExpr(node.children.head, file)
          s"??? /* typeof $operand */"

        case _ =>
          s"??? /* ${node.kind} */"

    private def emitObjectLiteral(node: RastNode, file: RastFile): String =
      val props = node.children.filter(_.kind == "PropertyAssignment")
      val fieldValues = props.map { p =>
        val name = nameOf(p)
        val value = p.children.drop(1).headOption.map(emitExpr(_, file)).getOrElse("???")
        (name, value)
      }
      val fieldNames = fieldValues.map(_._1).toSet
      val kvMap = fieldValues.toMap
      // Recognize known case class shapes
      if fieldNames.contains("key") && fieldNames.contains("data") then
        val data = kvMap("data")
        val dataFixed = if data.startsWith("Vector(") then data
          else if data.startsWith("[...") then data.drop(4).dropRight(1)
          else if currentMutatedArrays.contains(data) then s"$data.toVector"
          else data
        s"Segment(${kvMap("key")}, $dataFixed)"
      else if fieldNames == Set("type", "text") || fieldNames == Set("`type`", "text") then
        s"PathToken(${kvMap.getOrElse("`type`", kvMap.getOrElse("type", "???"))}, ${kvMap("text")})"
      else
        // Generic: emit as a Map
        val entries = fieldValues.map { (k, v) => s"\"$k\" -> $v" }
        s"Map(${entries.mkString(", ")})"

    // Also handle index signature types
    private def resolveObjectType(rt: RastType, file: RastFile): String =
      if rt.text.contains("[key: string]") && rt.text.contains("number") then "Map[String, Int]"
      else if rt.text.contains("[key: string]") then "Map[String, Any]"
      else rt.text

    private def emitArrowFunction(node: RastNode, file: RastFile): String =
      val params = node.children.filter(_.kind == "Parameter")
      val paramList = params.map { p =>
        val name = nameOf(p)
        name
      }
      val body = node.children.find(c => c.kind == "Block" || c.kind != "Parameter")
      val bodyStr = body match
        case Some(b) if b.kind == "Block" =>
          val inner = new StringBuilder
          emitBlockToString(b, file, inner)
          s"{ ${inner.toString.trim} }"
        case Some(b) =>
          emitExpr(b, file)
        case None => "???"

      if paramList.length == 1 then
        s"${paramList.head} => $bodyStr"
      else
        s"(${paramList.mkString(", ")}) => $bodyStr"

    private def emitBlockToString(block: RastNode, file: RastFile, out: StringBuilder): Unit =
      for (stmt <- block.children)
        out.append(emitExprFromStatement(stmt, file))
        out.append("; ")

    private def emitExprFromStatement(node: RastNode, file: RastFile): String =
      node.kind match
        case "ReturnStatement" =>
          node.children.headOption.map(emitExpr(_, file)).getOrElse("()")
        case "ExpressionStatement" =>
          emitExpr(node.children.head, file)
        case _ => emitExpr(node, file)

    private def emitTemplateExpr(node: RastNode, file: RastFile): String =
      val parts = node.children.map { c =>
        c.kind match
          case "TemplateHead" | "TemplateMiddle" | "TemplateTail" =>
            c.value match
              case Some(RastValue.Str(s)) => s"\"$s\""
              case _ => "\"\""
          case "TemplateSpan" =>
            c.children.headOption.map(emitExpr(_, file)).getOrElse("???")
          case _ =>
            emitExpr(c, file)
      }
      parts.mkString(" + ")

    // --- Type resolution ---

    private def resolveScalaType(node: RastNode, file: RastFile): String =
      node.`type`.flatMap(file.types.get) match
        case Some(rt) => rastTypeToScala(rt, file)
        case None =>
          // Check for explicit type annotation in children
          val typeNode = node.children.find(c =>
            c.kind == "TypeReference" || c.kind == "ArrayType" ||
            c.kind == "NumberKeyword" || c.kind == "StringKeyword" ||
            c.kind == "BooleanKeyword" || c.kind == "VoidKeyword")
          typeNode match
            case Some(tn) => syntaxTypeToScala(tn)
            case None => "Any"

    private def rastTypeToScala(rt: RastType, file: RastFile): String =
      rt.kind match
        case "string" => "String"
        case "number" => "Double"
        case "boolean" => "Boolean"
        case "void" => "Unit"
        case "null" | "undefined" => "Null"
        case "never" => "Nothing"
        case "any" => "Any"
        case "array" =>
          val elem = rt.elementType.flatMap(file.types.get)
            .map(rastTypeToScala(_, file)).getOrElse("Any")
          s"Vector[$elem]"
        case "union" =>
          val memberTypes = rt.types.getOrElse(Nil).flatMap(file.types.get)
          val nonNull = memberTypes.filterNot(t => t.kind == "null" || t.kind == "undefined")
          val scalaTypes = nonNull.map(rastTypeToScala(_, file)).distinct
          // All-number-literal union → Int (R8: TokenType = 0 | 1 | 2)
          if nonNull.forall(_.kind == "numberLiteral") then "Int"
          // All-string-literal union → String
          else if nonNull.forall(_.kind == "stringLiteral") then "String"
          else if scalaTypes.length == 1 then scalaTypes.head
          else scalaTypes.mkString(" | ")
        case "function" =>
          val params = rt.parameters.getOrElse(Nil).map(p =>
            file.types.get(p.`type`).map(rastTypeToScala(_, file)).getOrElse("Any"))
          val ret = rt.returnType.flatMap(file.types.get)
            .map(rastTypeToScala(_, file)).getOrElse("Unit")
          if params.isEmpty then s"() => $ret"
          else s"(${params.mkString(", ")}) => $ret"
        case "stringLiteral" => "String"
        case "numberLiteral" => "Int"
        case "booleanLiteral" => "Boolean"
        case "reference" =>
          val target = rt.target.flatMap(file.types.get).map(_.text).getOrElse("Any")
          // Resolve type aliases (R8: TokenType → Int)
          val resolved = typeAliasMap.getOrElse(target, target)
          val typeArgs = rt.typeArguments.getOrElse(Nil).flatMap(file.types.get)
            .map(rastTypeToScala(_, file))
          if typeArgs.nonEmpty then s"$resolved[${typeArgs.mkString(", ")}]"
          else resolved
        case "object" =>
          resolveObjectType(rt, file)
        case _ =>
          if rt.text.contains("[]") then
            s"Vector[${rt.text.replace("[]", "").trim}]"
          else if rt.text.contains("[key:") then
            resolveObjectType(rt, file)
          else
            typeAliasMap.getOrElse(rt.text, rt.text)

    private def syntaxTypeToScala(node: RastNode): String =
      node.kind match
        case "NumberKeyword" => "Double"
        case "StringKeyword" => "String"
        case "BooleanKeyword" => "Boolean"
        case "VoidKeyword" => "Unit"
        case "ArrayType" =>
          val elem = node.children.headOption.map(syntaxTypeToScala).getOrElse("Any")
          s"Vector[$elem]"
        case "TypeReference" =>
          val name = node.children.headOption.flatMap(_.text).getOrElse("Any")
          typeAliasMap.getOrElse(name, name)
        case _ => "Any"

    private def resolveFunctionReturnType(node: RastNode, file: RastFile): String =
      // R4: read from the function's own type first
      node.`type`.flatMap(file.types.get) match
        case Some(rt) if rt.kind == "function" =>
          rt.returnType.flatMap(file.types.get).map(rastTypeToScala(_, file)).getOrElse("Any")
        case _ =>
          // Try the name identifier's type (it carries the full function signature)
          val nameId = node.children.find(_.kind == "Identifier")
          val fromName = nameId.flatMap(_.`type`).flatMap(file.types.get).flatMap { rt =>
            if rt.kind == "function" then
              rt.returnType.flatMap(file.types.get).map(rastTypeToScala(_, file))
            else None
          }
          fromName.getOrElse {
            // Explicit return type annotation in children
            val returnTypeNode = node.children.find(c =>
              c.kind == "TypeReference" || c.kind == "ArrayType" ||
              c.kind == "NumberKeyword" || c.kind == "StringKeyword" ||
              c.kind == "BooleanKeyword" || c.kind == "VoidKeyword")
            returnTypeNode.map(syntaxTypeToScala).getOrElse("Any")
          }

    // --- Helpers ---

    private def nameOf(node: RastNode): String =
      node.children.find(_.kind == "Identifier").flatMap(_.text)
        .orElse(node.text)
        .getOrElse("$anon")

    private def isExported(node: RastNode): Boolean =
      node.flags.contains("ExportKeyword")

    private def findInitializer(node: RastNode, file: RastFile): String =
      // Find the first child that isn't an Identifier or type annotation
      val init = node.children.find(c =>
        c.kind != "Identifier" && !c.kind.contains("Keyword") &&
        !c.kind.contains("Type") && c.kind != "Parameter")
      init.map(emitExpr(_, file)).getOrElse("???")

    private def capitalize(s: String): String =
      if s.isEmpty then s
      else s.head.toUpper + s.tail

    private def tsOpToScala(op: String): String = op match
      case "PlusToken" => "+"
      case "MinusToken" => "-"
      case "AsteriskToken" => "*"
      case "SlashToken" => "/"
      case "PercentToken" => "%"
      case "EqualsEqualsEqualsToken" | "EqualsEqualsToken" => "=="
      case "ExclamationEqualsEqualsToken" | "ExclamationEqualsToken" => "!="
      case "LessThanToken" | "FirstBinaryOperator" => "<"
      case "LessThanEqualsToken" => "<="
      case "GreaterThanToken" => ">"
      case "GreaterThanEqualsToken" => ">="
      case "AmpersandAmpersandToken" => "&&"
      case "BarBarToken" => "||"
      case "EqualsToken" | "FirstAssignment" => "="
      case "PlusEqualsToken" | "FirstCompoundAssignment" => "+="
      case "MinusEqualsToken" => "-="
      case "AmpersandToken" => "&"
      case "BarToken" => "|"
      case "CaretToken" => "^"
      case _ => op

    private val scalaKeywords = Set(
      "type", "val", "var", "def", "class", "trait", "object", "enum",
      "match", "case", "if", "else", "for", "while", "do", "return",
      "throw", "try", "catch", "finally", "import", "export", "package",
      "new", "this", "super", "with", "extends", "yield", "abstract",
      "final", "sealed", "private", "protected", "override", "lazy",
      "implicit", "given", "using", "then", "end",
    )

    /** Detect which parameter names are reassigned in a function body */
    private def collectReassignedVars(body: RastNode, paramNames: Set[String]): Set[String] =
      val result = mutable.Set.empty[String]
      def walk(n: RastNode): Unit =
        if n.kind == "BinaryExpression" && n.operator.exists(o =>
          o == "EqualsToken" || o == "FirstAssignment" || o == "PlusEqualsToken" ||
          o == "MinusEqualsToken" || o == "FirstCompoundAssignment") then
          n.children.headOption.foreach { lhs =>
            val name = lhs.text.orElse(lhs.children.find(_.kind == "Identifier").flatMap(_.text))
            name.filter(paramNames.contains).foreach(result += _)
          }
        n.children.foreach(walk)
      walk(body)
      result.toSet

    /** R14: detect `d.match(/regex/)` pattern in a condition node */
    private def detectRegexMatch(node: RastNode): Option[(String, String)] =
      if node.kind == "CallExpression" then
        val fn = node.children.headOption
        fn.flatMap { f =>
          if f.kind == "PropertyAccessExpression" then
            val method = f.children.lastOption.flatMap(_.text)
            if method.contains("match") then
              val obj = simpleExprName(f.children.head)
              val regexArg = node.children.drop(1).headOption
              regexArg.flatMap { r =>
                if r.kind == "RegularExpressionLiteral" then
                  val regexStr = r.value match
                    case Some(RastValue.Str(s)) => s
                    case _ => r.text.getOrElse("")
                  val body = regexStr.stripPrefix("/").reverse.dropWhile(_ != '/').reverse.stripSuffix("/")
                  val escaped = body.replace("\\", "\\\\")
                  Some((obj, s"\"$escaped\".r"))
                else None
              }
            else None
          else None
        }
      else None

    // need a version that doesn't require file for simple obj name extraction
    private def simpleExprName(node: RastNode): String =
      node.kind match
        case "Identifier" => node.text.getOrElse("???")
        case "PropertyAccessExpression" =>
          val obj = simpleExprName(node.children.head)
          val prop = node.children.lastOption.flatMap(_.text).getOrElse("???")
          s"$obj.$prop"
        case _ => "???"

    private def escapeString(s: String): String =
      s.replace("\\", "\\\\").replace("\"", "\\\"").replace("\n", "\\n").replace("\r", "\\r")
