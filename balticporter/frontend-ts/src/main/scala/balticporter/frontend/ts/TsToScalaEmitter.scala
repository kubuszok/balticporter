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
      braceStyle: Boolean = true,
      errorClassName: String = "RuntimeException",
      extraDeclarations: Map[String, String] = Map.empty,
      postProcess: Map[String, List[(String, String)]] = Map.empty,
      skipIndex: Boolean = true,
      fileNameMap: Map[String, String] = Map.empty,
      /** Map from TS type alias name to Scala type name. `Point` → `Point` means
        * the emitter uses `Point` wherever the TS type alias appears. */
      tupleTypeOverrides: Map[String, String] = Map.empty,
      /** Map from TS type alias name to field names for tuple element access.
        * `Point` → Map(0 → "x", 1 → "y") means `p[0]` → `p.x`. */
      tupleFieldOverrides: Map[String, Map[Int, String]] = Map.empty,
      /** Mutable tuple types: emitted as `case class` with `var` fields. */
      mutableTupleTypes: Set[String] = Set.empty,
      /** Type alias → case class definition string (emitted at package level). */
      typeAliasDefinitions: Map[String, String] = Map.empty,
  )

  def emit(files: List[RastFile], config: EmitConfig): Map[String, String] =
    val ctx = new EmitContext(config)
    files.flatMap { f =>
      val rawName = f.path.split('/').last.stripSuffix(".ts")
      val fileName = config.fileNameMap.getOrElse(rawName, rawName)
      if config.skipIndex && rawName == "index" then None
      else
        var scala = ctx.emitFile(f, fileName)
        for (replacements <- config.postProcess.get(fileName); (pattern, replacement) <- replacements)
          scala = scala.replaceAll(pattern, replacement)
        Some(fileName -> scala)
    }.toMap

  private class EmitContext(config: EmitConfig):
    private val sb = new StringBuilder
    private val typeAliasMap = mutable.Map.empty[String, String]
    // Track which function parameters are optional (for Some(...) wrapping at call sites)
    private val functionOptionalParams = mutable.Map.empty[String, Set[Int]]

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

      // Seed type alias map with project-specific overrides
      for ((name, scalaType) <- config.tupleTypeOverrides)
        typeAliasMap(name) = scalaType

      // Resolve type aliases FIRST so they're available during emission
      for (ta <- typeAliases)
        val taName = nameOf(ta)
        if !config.tupleTypeOverrides.contains(taName) then
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

      // Emit extra declarations before the object (e.g., error classes)
      config.extraDeclarations.get(fileName).foreach(d => sb.append(d + "\n\n"))

      // Emit the module object with functions and variables
      sb.append(s"object $objectName {\n\n")

      // Private interfaces as nested case classes
      for (iface <- interfaces if !isExported(iface))
        emitCaseClass(iface, file, indent = "  ")

      // Constants and variables
      for (vs <- variables)
        emitVariableStatement(vs, file, "  ")

      // Functions
      for (fn <- functions)
        emitFunction(fn, file, "  ")

      sb.append("}\n")
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

      // Track optional params for truthiness checks
      val optionalParams = mutable.Set.empty[String]
      val paramList = params.map { p =>
        val pName = nameOf(p)
        val isReassigned = reassignedParams.contains(pName)
        val sigName = if isReassigned then s"${pName}0" else pName
        val escapedName = if scalaKeywords.contains(sigName) then s"`$sigName`" else sigName
        val isOptional = p.flags.contains("QuestionToken") || {
          p.`type`.flatMap(file.types.get).exists(t => t.text.contains("| undefined"))
        }
        val rawType = if isOptional then
          val baseType = p.`type`.flatMap(file.types.get).map { t =>
            if t.kind == "union" then
              val nonUndef = t.types.getOrElse(Nil).flatMap(file.types.get).filter(_.kind != "undefined")
              nonUndef.headOption.map(nt => rastTypeToScala(nt, file)).getOrElse("Any")
            else rastTypeToScala(t, file)
          }.getOrElse("Any")
          baseType
        else resolveScalaType(p, file)
        val pType = if isOptional then
          optionalParams += pName
          s"Option[$rawType] = None"
        else
          // Integer parameter recovery: params used as array indices → Int
          if rawType == "Double" && body.exists(b => isUsedAsIndex(pName, b)) then "Int"
          else rawType
        // Handle array destructuring param: [x, y] → tuple
        val hasArrayBinding = p.children.exists(_.kind == "ArrayBindingPattern")
        if hasArrayBinding then
          s"${escapedName}: (Double, Double)"
        else
          s"$escapedName: $pType"
      }
      currentOptionalParams = optionalParams.toSet
      // Register optional param positions for this function (for call-site Some wrapping)
      val optParamIndices = params.zipWithIndex.collect {
        case (p, i) if optionalParams.contains(nameOf(p)) => i
      }.toSet
      if optParamIndices.nonEmpty then functionOptionalParams(name) = optParamIndices

      sb.append(s"$indent${vis}def $name(${paramList.mkString(", ")}): $retType = {\n")
      // Emit var copies for reassigned params
      for (p <- params if reassignedParams.contains(nameOf(p)))
        val pName = nameOf(p)
        val pType = resolveScalaType(p, file)
        sb.append(s"${indent}  var $pName: $pType = ${pName}0\n")
      body match
        case Some(block) => emitBlock(block, file, indent + "  ")
        case None => sb.append(s"${indent}  ???\n")
      currentMutatedArrays = Set.empty
      currentOptionalParams = Set.empty
      declaredVars.clear()
      sb.append(s"$indent}\n\n")

    // R7: track which variables are mutated via .push() — they need ArrayBuffer
    private var currentMutatedArrays: Set[String] = Set.empty
    // Track optional params for truthiness checks
    private var currentOptionalParams: Set[String] = Set.empty
    // Track declared variables in current function scope (for loop var dedup)
    private val declaredVars: mutable.Set[String] = mutable.Set.empty

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

    private def inferPushedElementType(varName: String, file: RastFile): String =
      // Walk the current function body looking for varName.push(x) and infer type from x
      // For now use a heuristic: if push arg starts with Vector(, element is Vector[Double]
      "Vector[Double]"  // conservative default for path-data-parser

    private def emitVariableStatement(node: RastNode, file: RastFile, indent: String): Unit =
      val decls = node.children.flatMap { child =>
        if child.kind == "VariableDeclarationList" then
          child.children.filter(_.kind == "VariableDeclaration")
        else Nil
      }
      for (d <- decls)
        // Check for array destructuring: const [x, y] = expr
        val hasArrayBinding = d.children.exists(_.kind == "ArrayBindingPattern")
        if hasArrayBinding then
          val pattern = d.children.find(_.kind == "ArrayBindingPattern").get
          val names = pattern.children.filter(_.kind == "BindingElement").flatMap(
            _.children.find(_.kind == "Identifier").flatMap(_.text))
          val initExpr = d.children.find(c => c.kind != "ArrayBindingPattern" && !c.kind.contains("Type") && !c.kind.contains("Keyword"))
            .map(emitExpr(_, file)).getOrElse("???")
          for ((n, idx) <- names.zipWithIndex)
            sb.append(s"${indent}val $n: Double = $initExpr($idx)\n")
        else {
        val name = nameOf(d)
        val isConst = d.flags.contains("const")
        val keyword = if isConst then "val" else "var"
        val rawType = resolveScalaType(d, file)
        // R7: if this array is mutated via .push(), use ArrayBuffer
        val isMutatedArray = currentMutatedArrays.contains(name) && rawType.startsWith("Vector[")
        val tpeAB = if isMutatedArray then rawType.replace("Vector[", "ArrayBuffer[") else rawType
        // Element type inference: if ArrayBuffer[Any], try to infer from pushed values
        val tpe = if tpeAB.contains("[Any]") && isMutatedArray then
          val inferredElem = inferPushedElementType(name, file)
          if inferredElem.nonEmpty then tpeAB.replace("[Any]", s"[$inferredElem]") else tpeAB
        else tpeAB
        // R8: integer variables — int literal init → Int when const OR when
        // the variable is used as an array index (flow analysis approximation)
        val tpeFixed = if tpe == "Double" then
          val initText = findInitializer(d, file)
          if isConst && initText.matches("-?\\d+") then "Int"
          // For vars: only Int if initialized with integer AND name suggests index/counter
          else if !isConst && initText.matches("-?\\d+") then
            val nm = name.toLowerCase
            if nm.contains("index") || nm == "i" || nm == "j" || nm == "pos" ||
               nm.contains("count") || nm.contains("length") then "Int"
            else tpe
          else tpe
        else tpe
        val init = findInitializer(d, file)
        val initFixed = if isMutatedArray && (init == "Vector.empty" || init.startsWith("ArrayBuffer")) then
          tpe.replace("ArrayBuffer[", "ArrayBuffer.empty[").stripSuffix("]") + "]"
        else if isMutatedArray && init.startsWith("Vector(") then
          init.replace("Vector(", "ArrayBuffer(")
        else init
        sb.append(s"$indent$keyword $name: $tpeFixed = $initFixed\n")
        } // end else (not array binding)

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
              sb.append(s"$indent$regex.findPrefixMatchOf($obj) match {\n")
              sb.append(s"$indent  case Some(_m) =>\n")
              emitStatement(node.children(1), file, indent + "    ")
              if node.children.length > 2 then
                sb.append(s"$indent  case None =>\n")
                emitStatement(node.children(2), file, indent + "    ")
              else
                sb.append(s"$indent  case None => ()\n")
              sb.append(s"$indent}\n")
            case None =>
              val cond = emitExpr(condNode, file)
              // JS truthiness: optional params → .isDefined, numeric → != 0
              val condType = condNode.`type`.flatMap(file.types.get).map(_.kind).getOrElse("")
              val isOptionalRef = condNode.kind == "Identifier" && condNode.text.exists(currentOptionalParams.contains)
              val condFixed = if isOptionalRef then
                s"$cond.isDefined"
              else if condType == "number" && !cond.contains("==") && !cond.contains("!=") && !cond.contains("<") && !cond.contains(">")
                then s"($cond) != 0"
                else cond
              sb.append(s"${indent}if ($condFixed) {\n")
              emitStatement(node.children(1), file, indent + "  ")
              if node.children.length > 2 then
                sb.append(s"$indent} else {\n")
                emitStatement(node.children(2), file, indent + "  ")
              sb.append(s"$indent}\n")

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
          sb.append(s"${indent}while ($cond) {\n")
          emitStatement(node.children(1), file, indent + "  ")
          sb.append(s"$indent}\n")

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
          sb.append(s"${indent}for (_item <- $iterExpr) {\n")
          for (f <- fields)
            sb.append(s"${indent}  val $f = _item.$f\n")
          body.foreach(b => emitBlock(b, file, indent + "  "))
          sb.append(s"$indent}\n")
        case None =>
          sb.append(s"${indent}for ($bindingName <- $iterExpr) {\n")
          body.foreach(b => emitBlock(b, file, indent + "  "))
          sb.append(s"$indent}\n")

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
            // For-loop init: the init expr is the last child after the name
            val initExpr = d.children.drop(1).lastOption.map(emitExpr(_, file)).getOrElse("0")
            // R8: for-loop variables are typically Int (used as indices)
            // Skip re-declaration if variable already declared in this scope
            if declaredVars.contains(n) then
              sb.append(s"$indent$n = $initExpr\n")
            else
              val kw = if isConst then "val" else "var"
              sb.append(s"$indent$kw $n: Int = $initExpr\n")
              declaredVars += n
        else
          emitStatement(initChild, file, indent)
        val cond = emitExpr(children(1), file)
        sb.append(s"${indent}while ($cond) {\n")
        // Emit the body block's statements WITHOUT the outer braces
        val bodyNode = children(3)
        if bodyNode.kind == "Block" then
          bodyNode.children.foreach(c => emitStatement(c, file, indent + "  "))
        else
          emitStatement(bodyNode, file, indent + "  ")
        // update (inside the while body)
        val update = emitExpr(children(2), file)
        sb.append(s"${indent}  $update\n")
        sb.append(s"$indent}\n")
      else if children.length >= 3 then
        val cond = emitExpr(children(0), file)
        sb.append(s"${indent}while ($cond) {\n")
        val bodyNode = children(2)
        if bodyNode.kind == "Block" then
          bodyNode.children.foreach(c => emitStatement(c, file, indent + "  "))
        else
          emitStatement(bodyNode, file, indent + "  ")
        val update = emitExpr(children(1), file)
        sb.append(s"${indent}  $update\n")
        sb.append(s"$indent}\n")
      else
        sb.append(s"$indent// TODO: for loop with ${children.length} children\n")

    private def emitSwitch(node: RastNode, file: RastFile, indent: String): Unit =
      val scrutinee = emitExpr(node.children.head, file)
      val caseBlock = node.children.find(_.kind == "CaseBlock")
      sb.append(s"$indent($scrutinee) match {\n")
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
        // R12-exhaustive: if no DefaultClause, add case _ => ()
        val hasDefault = clauses.exists(_.kind == "DefaultClause")
        if !hasDefault then sb.append(s"$indent  case _ => ()\n")
        sb.append(s"$indent}\n")
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
          val lhs = node.children.head
          val right = emitExpr(node.children.last, file)
          // typeof X === "number" → map.contains(key)
          val isTypeofCheck = lhs.kind == "TypeOfExpression" && node.operator.exists(o =>
            o == "EqualsEqualsEqualsToken" || o == "EqualsEqualsToken")
          if isTypeofCheck then
            val typeofOperand = lhs.children.head
            val typeStr = node.children.last.value match
              case Some(RastValue.Str(s)) => s
              case _ => "object"
            if typeStr == "number" && typeofOperand.kind == "ElementAccessExpression" then
              val mapExpr = emitExpr(typeofOperand.children.head, file)
              val keyExpr = emitExpr(typeofOperand.children.last, file)
              return s"$mapExpr.contains($keyExpr)"
            else
              val operandExpr = emitExpr(typeofOperand, file)
              return s"$operandExpr.isInstanceOf[${typeStr.capitalize}]"
          val op = node.operator.map(tsOpToScala).getOrElse("???")
          // Destructuring: [a, b] = expr → a = expr(0); b = expr(1)
          if op == "=" && lhs.kind == "ArrayLiteralExpression" then
            val names = lhs.children.map(c => emitExpr(c, file))
            val rExpr = emitExpr(node.children.last, file)
            // Optional unwrap: if the RHS is an optional param, use .get
            val rhsName = node.children.last.text.getOrElse("")
            val rAccess = if currentOptionalParams.contains(rhsName) then s"$rExpr.get" else rExpr
            // Tuple return: if RHS is a call returning tuple, bind to temp and use ._1/_2
            val rhsType = node.children.last.`type`.flatMap(file.types.get)
            val isTupleReturn = rhsType.exists(t => t.text.startsWith("[") && t.text.contains(","))
            if isTupleReturn then
              val tmpName = s"_d${names.hashCode.abs % 1000}"
              s"val $tmpName = $rAccess; ${names.zipWithIndex.map { case (n, i) => s"$n = $tmpName._${i+1}" }.mkString("; ")}"
            else
              names.zipWithIndex.map { case (n, i) => s"$n = $rAccess($i)" }.mkString("; ")
          // R15: arr[arr.length] = x → arr += x
          else if op == "=" && lhs.kind == "ElementAccessExpression" then
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
            // R7-IntDiv: integer / integer → float division to avoid truncation
            if op == "/" then
              val leftIsInt = node.children.head.kind == "NumericLiteral" && node.children.head.value.exists {
                case RastValue.Num(v) => v == v.toLong
                case _ => false
              }
              val rightIsInt = node.children.last.kind == "NumericLiteral" && node.children.last.value.exists {
                case RastValue.Num(v) => v == v.toLong
                case _ => false
              }
              if leftIsInt && rightIsInt then s"(${left}.0 / $right)"
              else s"($left $op $right)"
            else if op == "=" || op == "+=" || op == "-=" then
              s"$left $op $right"
            else if op == "&&" || op == "||" then
              // Number truthiness in boolean context: sweepFlag && ... → (sweepFlag != 0) && ...
              val lhsType = node.children.head.`type`.flatMap(file.types.get).map(_.kind).getOrElse("")
              val lhsIsOptional = node.children.head.kind == "Identifier" && node.children.head.text.exists(currentOptionalParams.contains)
              val fixedLeft = if lhsIsOptional then s"$left.isDefined"
                else if lhsType == "number" && !left.contains("==") && !left.contains("!=") && !left.contains("<") && !left.contains(">") then s"($left != 0)"
                else left
              s"($fixedLeft $op $right)"
            else
              s"($left $op $right)"

        case "PrefixUnaryExpression" =>
          val operand = emitExpr(node.children.head, file)
          node.operator match
            case Some("MinusToken") => s"-$operand"
            case Some("PlusToken") => s"$operand.toDouble"
            case Some("ExclamationToken") =>
              val opType = node.children.head.`type`.flatMap(file.types.get).map(_.kind).getOrElse("")
              val opIsOptional = node.children.head.kind == "Identifier" && node.children.head.text.exists(currentOptionalParams.contains)
              if opIsOptional then s"$operand.isEmpty"
              else if opType == "number" then s"($operand == 0)"
              else s"!$operand"
            case Some("TildeToken") => s"~$operand"
            case Some("PlusPlusToken") => s"{ $operand += 1; $operand }"
            case Some("MinusMinusToken") => s"{ $operand -= 1; $operand }"
            case _ => s"$operand"

        case "PostfixUnaryExpression" =>
          val operand = emitExpr(node.children.head, file)
          node.operator match
            case Some("PlusPlusToken") => s"$operand += 1"
            case Some("MinusMinusToken") => s"$operand -= 1"
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
              // Detect if target buffer has string|number element type (serialize pattern)
              val receiverNode = node.children.head match
                case pa if pa.kind == "PropertyAccessExpression" => Some(pa.children.head)
                case _ => None
              val isStringBuffer = receiverNode.exists { rn =>
                rn.`type`.flatMap(file.types.get).exists { t =>
                  t.text.contains("string | number") || t.text.contains("(string | number)")
                }
              }
              def wrapJsNum(a: String): String =
                if isStringBuffer && a.matches(""".*\(\d+\)""") && !a.startsWith("jsNum") && !a.contains("\"") then
                  s"jsNum($a)"
                else a
              if hasSpread && args.length == 1 then
                val spreadArg = args.head.stripSuffix("*")
                if isStringBuffer then s"$spreadArg.foreach(d => $obj += jsNum(d))"
                else s"$obj ++= $spreadArg"
              else if args.length == 1 then s"$obj += ${wrapJsNum(args.head)}"
              else args.map(a => s"$obj += ${wrapJsNum(a)}").mkString("; ")
            case s if s.endsWith(".substr") =>
              val obj = s.stripSuffix(".substr")
              s"$obj.substring(${args.mkString(", ")})"
            case s if s.endsWith(".`match`") || s.endsWith(".match") =>
              val obj = s.stripSuffix(".`match`").stripSuffix(".match")
              s"${args.head}.findPrefixMatchOf($obj).isDefined"
            case s if s.endsWith(".join") =>
              val obj = s.stripSuffix(".join")
              s"$obj.mkString(${args.mkString(", ")})"
            case s if s.endsWith(".forEach") || s.endsWith(".foreach") =>
              val obj = s.stripSuffix(".forEach").stripSuffix(".foreach")
              s"$obj.foreach(${args.mkString(", ")})"
            case s if s.endsWith(".concat") =>
              val obj = s.stripSuffix(".concat")
              s"($obj ++ ${args.mkString(", ")})"
            case s if s.endsWith(".splice") =>
              val obj = s.stripSuffix(".splice")
              if args.length == 2 then
                s"{ val _removed = $obj.take(${args(0)} + ${args(1)}); $obj.remove(${args.mkString(", ")}); _removed }"
              else s"$obj.splice(${args.mkString(", ")})"
            case s if s.endsWith(".sort") =>
              val obj = s.stripSuffix(".sort")
              s"$obj.sortInPlaceWith((a, b) => ${args.head}(a, b) < 0)"
            case s if s.endsWith(".filter") =>
              val obj = s.stripSuffix(".filter")
              s"$obj.filter(${args.mkString(", ")})"
            case s if s.endsWith(".toFixed") =>
              val obj = s.stripSuffix(".toFixed")
              val precision = args.headOption.getOrElse("0")
              s"""f"$${$obj}%.${precision}f""""
            case s if s.endsWith(".map") =>
              val obj = s.stripSuffix(".map")
              // R14: detect 2-param arrow (d, i) => ... → zipWithIndex.map { case (d, i) => ... }
              val arrowArg = node.children.drop(1).headOption
              val arrowParams = arrowArg.toList.flatMap(_.children.filter(_.kind == "Parameter"))
              if arrowParams.length == 2 then
                val p1 = nameOf(arrowParams(0))
                val p2 = nameOf(arrowParams(1))
                val body = arrowArg.flatMap(_.children.find(c => c.kind != "Parameter")).map(emitExpr(_, file)).getOrElse("???")
                s"$obj.zipWithIndex.map { case ($p1, $p2) => $body }"
              else
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
              // Option wrapping: wrap args at optional parameter positions with Some(...)
              val calledFnName = node.children.head.text.getOrElse(fn)
              val optIndices = functionOptionalParams.getOrElse(calledFnName, Set.empty)
              if optIndices.nonEmpty then
                val wrappedArgs = args.zipWithIndex.map { case (a, i) =>
                  if optIndices.contains(i) then s"Some($a)" else a
                }
                s"$fn(${wrappedArgs.mkString(", ")})"
              else s"$fn(${args.mkString(", ")})"

        case "PropertyAccessExpression" =>
          val obj = emitExpr(node.children.head, file)
          val prop = node.children.lastOption.flatMap(_.text).getOrElse("???")
          // Math methods → scala.math
          if obj == "Math" then
            prop match
              case "PI" => "math.Pi"
              case "sqrt" | "pow" | "cos" | "sin" | "atan2" | "abs" |
                   "floor" | "ceil" | "max" | "min" | "log" | "tan" | "asin" | "acos" =>
                s"math.$prop"
              case "round" => "math.round"
              case _ => s"math.$prop"
          // RegExp.$1 → _m.group(1)
          else if obj == "RegExp" && prop.startsWith("$") then
            val groupNum = prop.drop(1)
            s"_m.group($groupNum)"
          else if prop == "length" then
            s"$obj.length"
          else if prop == "push" then
            s"$obj.push"
          else if prop == "forEach" then
            s"$obj.foreach"
          else if prop == "splice" then
            s"$obj.remove"
          else if prop == "filter" then
            s"$obj.filter"
          else if scalaKeywords.contains(prop) then
            s"$obj.`$prop`"
          else
            s"$obj.$prop"

        case "ElementAccessExpression" =>
          val obj = emitExpr(node.children.head, file)
          val idx = emitExpr(node.children.last, file)
          // Optional indexing: recursive(0) → recursive.get(0)
          val objName = node.children.head.text.getOrElse("")
          val isOptional = currentOptionalParams.contains(objName)
          // Tuple field access: r._1 instead of r(0) when r has tuple type
          val objType = node.children.head.`type`.flatMap(file.types.get)
          val isTupleAccess = objType.exists(t => isTupleType(t, file))
          // Call returning tuple indexed: rotate(x,y,a)(0) → { val _r = rotate(x,y,a); _r._1 }
          val callReturnsTuple = node.children.head.kind == "CallExpression" && {
            node.children.head.`type`.flatMap(file.types.get).exists(t => isTupleType(t, file))
          }
          // Check for field override (Point.x instead of ._1)
          val objTypeText = objType.map(_.text).getOrElse("")
          val fieldOverride = config.tupleFieldOverrides.find { case (typeName, _) =>
            objTypeText.contains(typeName) || typeAliasMap.get(objTypeText).contains(typeName)
          }.flatMap(_._2.get(if idx.matches("\\d+") then idx.toInt else -1))
          // Also check: if we have a tupleTypeOverride for the obj type, treat as named type access
          val hasNamedType = config.tupleTypeOverrides.values.exists(v => objTypeText.contains(v)) ||
            config.tupleFieldOverrides.keys.exists(k => objTypeText.contains(k))
          if callReturnsTuple && idx.matches("\\d+") then
            val accessor = fieldOverride.getOrElse(s"_${idx.toInt + 1}")
            s"{ val _r = $obj; _r.$accessor }"
          else if (isTupleAccess || hasNamedType || fieldOverride.isDefined) && idx.matches("\\d+") then
            val accessor = fieldOverride.getOrElse(s"_${idx.toInt + 1}")
            s"$obj.$accessor"
          else if isOptional then
            s"$obj.get($idx)"
          else s"$obj($idx)"

        case "ArrayLiteralExpression" =>
          // Check if this is a tuple type (return value like [x, y])
          val isTuple = node.`type`.flatMap(file.types.get).exists { t =>
            t.text.startsWith("[") && t.text.contains(",")
          }
          val hasSpread = node.children.exists(_.kind == "SpreadElement")
          val allSpread = node.children.nonEmpty && node.children.forall(_.kind == "SpreadElement")
          // Check if the array literal's type matches a tuple type override (e.g., Point)
          val tupleOverrideName = node.`type`.flatMap(file.types.get).flatMap { t =>
            config.tupleTypeOverrides.find { case (_, scalaName) =>
              t.text.contains(scalaName) || typeAliasMap.values.exists(_ == scalaName) &&
                (t.text.startsWith("[") && t.text.contains(","))
            }.map(_._2)
          }.orElse {
            if isTuple then config.tupleTypeOverrides.values.headOption else None
          }
          if node.children.isEmpty then "Vector.empty"
          else if allSpread && node.children.length == 1 then
            emitExpr(node.children.head.children.head, file)
          else if isTuple then
            val elems = node.children.map(c =>
              if c.kind == "SpreadElement" then emitExpr(c.children.head, file) else emitExpr(c, file))
            val constructor = tupleOverrideName.getOrElse("")
            if constructor.nonEmpty then s"$constructor(${elems.mkString(", ")})"
            else s"(${elems.mkString(", ")})"
          else if hasSpread then
            // Mixed spread + non-spread: Vector(a, b) ++ spread
            val nonSpread = node.children.takeWhile(_.kind != "SpreadElement")
            val spreadPart = node.children.dropWhile(_.kind != "SpreadElement")
            val prefix = if nonSpread.nonEmpty then
              s"Vector(${nonSpread.map(emitExpr(_, file)).mkString(", ")})"
            else "Vector.empty"
            val suffix = spreadPart.map { c =>
              if c.kind == "SpreadElement" then emitExpr(c.children.head, file)
              else s"Vector(${emitExpr(c, file)})"
            }
            (prefix +: suffix).mkString(" ++ ")
          else
            val elems = node.children.map { c =>
              val e = emitExpr(c, file)
              // If element is a known mutated array (ArrayBuffer), add .toVector
              // Only if the variable's RAST type is an array type
              val eName = c.text.getOrElse("")
              val isArrayVar = currentMutatedArrays.contains(eName) && c.`type`.flatMap(file.types.get).exists(t =>
                t.kind == "array" || t.text.contains("[]"))
              if isArrayVar then s"$e.toVector" else e
            }
            s"Vector(${elems.mkString(", ")})"

        case "ObjectLiteralExpression" =>
          emitObjectLiteral(node, file)

        case "NewExpression" =>
          val cls = emitExpr(node.children.head, file)
          val args = node.children.drop(1).map(emitExpr(_, file))
          if cls == "Array" then "ArrayBuffer.empty"
          else if cls == "Error" then s"new ${config.errorClassName}(${args.mkString(", ")})"
          else s"new $cls(${args.mkString(", ")})"

        case "ParenthesizedExpression" =>
          val inner = emitExpr(node.children.head, file)
          s"($inner)"

        case "ConditionalExpression" =>
          val condNode0 = node.children(0)
          val cond = emitExpr(condNode0, file)
          val thenE = emitExpr(node.children(1), file)
          val elseE = emitExpr(node.children(2), file)
          // JS truthiness: numeric condition in ternary needs != 0
          val condType = condNode0.`type`.flatMap(file.types.get).map(_.kind).getOrElse("")
          val condFixed = if condType == "number" && !cond.contains("==") && !cond.contains("!=") && !cond.contains("<") && !cond.contains(">")
            then s"($cond) != 0"
            else cond
          s"(if ($condFixed) $thenE else $elseE)"

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

    private def isUsedAsIndex(name: String, body: RastNode): Boolean =
      var found = false
      def walk(n: RastNode): Unit =
        if !found && n.kind == "ElementAccessExpression" && n.children.length >= 2 then
          val idx = n.children.last
          if idx.text.contains(name) || idx.children.exists(_.text.contains(name)) then
            found = true
        n.children.foreach(walk)
      walk(body)
      found

    private def isTupleType(rt: RastType, file: RastFile): Boolean =
      if rt.text.startsWith("[") && rt.text.contains(",") then true
      else if rt.kind == "reference" then
        rt.target.flatMap(file.types.get).exists(t => isTupleType(t, file))
      else false

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
          // Check tupleTypeOverrides FIRST — a named type alias takes precedence
          val refText = rt.text.takeWhile(c => c != '<' && c != '[').trim
          config.tupleTypeOverrides.get(refText) match
            case Some(scalaName) => return scalaName
            case None => ()
          val targetType = rt.target.flatMap(file.types.get)
          val target = targetType.map(_.text).getOrElse("Any")
          val isTuple = (rt.text.startsWith("[") && rt.text.contains(",")) ||
            (target.startsWith("[") && target.contains(",")) ||
            targetType.exists(t => t.kind == "reference" && t.text.startsWith("["))
          if isTuple then
            val typeArgs = rt.typeArguments.getOrElse(Nil).flatMap(file.types.get)
              .map(rastTypeToScala(_, file))
            if typeArgs.nonEmpty then s"(${typeArgs.mkString(", ")})"
            else "(Double, Double)" // fallback for unresolved tuple
          else
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
        case "TupleType" =>
          val elems = node.children.map(syntaxTypeToScala)
          s"(${elems.mkString(", ")})"
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
      // VariableDeclaration children: [name, typeAnnotation?, initializer?]
      // The name is the first child (Identifier or ArrayBindingPattern).
      // The initializer is the last child that isn't the name or a type annotation.
      val nonName = node.children.drop(1).filter(c =>
        !c.kind.contains("Keyword") && !c.kind.endsWith("Type") &&
        c.kind != "TypeReference" && c.kind != "ArrayType" && c.kind != "TupleType" &&
        c.kind != "UnionType" && c.kind != "Parameter")
      val init = nonName.lastOption
      init.map(emitExpr(_, file)).getOrElse {
        // Missing initializer: provide type-appropriate zero value
        val tpe = resolveScalaType(node, file)
        tpe match
          case "Double" => "0.0"
          case "Int" => "0"
          case "Boolean" | "java.lang.Boolean" => "false"
          case "String" => "\"\""
          case t if t.startsWith("Vector[") => "Vector.empty"
          case t if t.startsWith("ArrayBuffer[") => "ArrayBuffer.empty"
          case _ => "???"
      }

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
      case "AsteriskEqualsToken" => "*="
      case "SlashEqualsToken" => "/="
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
          o == "MinusEqualsToken" || o == "AsteriskEqualsToken" || o == "SlashEqualsToken" ||
          o == "FirstCompoundAssignment") then
          n.children.headOption.foreach { lhs =>
            if lhs.kind == "ArrayLiteralExpression" then
              // Destructuring: [x1, y1] = ... — each element is reassigned
              lhs.children.flatMap(_.text).filter(paramNames.contains).foreach(result += _)
            else
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
