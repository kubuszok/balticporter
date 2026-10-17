package balticporter.frontend.ts.dedicated

import balticporter.frontend.ts.{ RastNode, RastType, RastValue, ReferenceDeclarations, ReferenceSignatures }
import BodySyntax.*
import DefmethodBodyTranslator.ReturnTypePlaceholder

/** Renders one RAST body as Scala text: statements, expressions, calls and literals, each refused where JS and Scala would disagree. */
private[dedicated] class BodyContext(
  entry:              DefmethodEntry,
  hierarchy:          List[DefnodeClass],
  baseIndent:         String,
  thisBinding:        String = "this",
  nodeParamName:      Option[String] = None,
  apiLookup:          Map[String, String] = Map.empty,
  declaredReturnType: Option[String] = None,
  paramTypes:         Map[String, String] = Map.empty,
  calleeIndex:        ReferenceSignatures.CalleeIndex = ReferenceSignatures.CalleeIndex.empty,
  memberIndex:        ReferenceSignatures.MemberIndex = ReferenceSignatures.MemberIndex.empty,
  ctorSchema:         ReferenceSignatures.ConstructorSchema = ReferenceSignatures.ConstructorSchema.empty,
  enumIndex:          ReferenceSignatures.EnumIndex = ReferenceSignatures.EnumIndex.empty,
  memberRenames:      Map[String, String] = Map.empty,
  oracle:             ReferenceSignatures.TypeOracle = ReferenceSignatures.TypeOracle.empty,
  enclosingOwner:     Option[String] = None,
  conventions:        JsConventions = JsConventions.plain,
  tsTypes:            Map[String, RastType] = Map.empty,
  enclosingClass:     Option[String] = None,
  fileNames:          ReferenceDeclarations.FileNames = ReferenceDeclarations.FileNames.empty
) extends BodyScope(
      entry,
      hierarchy,
      baseIndent,
      thisBinding,
      nodeParamName,
      apiLookup,
      declaredReturnType,
      paramTypes,
      calleeIndex,
      memberIndex,
      ctorSchema,
      enumIndex,
      memberRenames,
      oracle,
      enclosingOwner,
      conventions,
      tsTypes,
      enclosingClass,
      fileNames
    )
    with BodyBindings
    with BodyObjects
    with BodyArrays
    with BodySwitch
    with BodyLocals:
  /** Translate a JS expression used as a condition (if, while, ternary). Applies truthiness lowering when the operand has a known non-Boolean type.
    */
  private def translateCondition(node: RastNode): String = at(node)(renderCondition(node))

  private def renderCondition(node: RastNode): String =
    // For `!x`, PrefixUnaryExpression already handles truthiness
    // For bare identifiers, apply the truthiness test
    node.kind match
      // In a condition `a && b` / `a || b` is truthy exactly when the truthiness tests combine so; each operand is itself a condition.
      case "BinaryExpression" if node.children.size >= 2 && node.operator.exists(op => op == "AmpersandAmpersandToken" || op == "BarBarToken") =>
        val lhs   = node.children.head
        val rhs   = node.children.last
        val isAnd = node.operator.contains("AmpersandAmpersandToken")
        val left  = translateCondition(lhs)
        // `x && x.f`: inside the right operand an optional `x` is known present
        val unwrap = if isAnd && lhs.kind == "Identifier" then exprType(lhs).filter(isOptionLike).flatMap(t => lhs.text.map(n => (snakeToCamel(n), t))) else None
        val right  = unwrap match
          case Some((name, tpe)) if !unwrappedIds.contains(name) =>
            val saved = localTypes.get(name)
            unwrappedIds += name
            localTypes(name) = optionInner(tpe)
            val r = translateCondition(rhs)
            unwrappedIds -= name
            saved.fold(localTypes.remove(name))(t => localTypes.put(name, t))
            r
          case _ => underNarrowing(if isAnd then narrowedBy(lhs) else refutedBy(lhs))(translateCondition(rhs))
        s"($left ${if isAnd then "&&" else "||"} $right)"
      case "ParenthesizedExpression" if node.children.nonEmpty =>
        s"(${translateCondition(node.children.head)})"
      // a number-valued `a & b` tested for truthiness
      case "BinaryExpression" if exprType(node).exists(_ != "Boolean") =>
        truthinessTest(exprType(node).get, translateExpr(node))
      case "PrefixUnaryExpression" | "BinaryExpression" | "ConditionalExpression" =>
        translateExpr(node) // these already handle truthiness internally
      case "ElementAccessExpression" if absentFalsyRead(node).isDefined =>
        absentFalsyRead(node).get()
      case _ =>
        exprType(node) match
          case Some(tpe) if tpe != "Boolean" =>
            val operand = translateExpr(node)
            truthinessTest(tpe, operand)
          // a length is a count whatever holds it: truthy when not zero
          case None if node.kind == "PropertyAccessExpression" && node.children.lastOption.exists(_.text.contains("length")) =>
            s"${translateExpr(node)} != 0"
          case None =>
            // No type info: a value whose type nothing declares may be any JS value, and its truthiness test is unknown
            if Set("Identifier", "PropertyAccessExpression", "ElementAccessExpression", "CallExpression")(node.kind) then refuse("truthiness-unknown-type")
            translateExpr(node)
          case _ => translateExpr(node)

  /** Translate an expression at a slot whose expected Scala type is known. A string literal at an enum/sealed-object slot becomes the qualified member. An array literal at a typed collection slot
    * carries the element type.
    */
  protected def translateExprAt(node: RastNode, slotType: String): String =
    // an alias is the type it names
    val expectedType = memberIndex.dealias(slotType)
    val baseType     = expectedType.takeWhile(c => c != '[' && c != ' ')
    node.kind match
      // both branches of `c ? a : b` take the slot's type
      case "ConditionalExpression" if node.children.size >= 3 =>
        val condNode = node.children.head
        val combines = condNode.kind == "BinaryExpression" && condNode.operator.exists(op => op == "AmpersandAmpersandToken" || op == "BarBarToken")
        if exprType(condNode).isEmpty && !combines then refuse("truthiness-unknown-type")
        // each branch reads what the condition proves, as an untyped `?:` does
        val thenE = underNarrowing(narrowedBy(condNode))(underPresence(condNode)(translateExprAt(node.children(1), expectedType)))
        val elseE = underNarrowing(refutedBy(condNode))(translateExprAt(node.children(2), expectedType))
        s"(if (${translateCondition(condNode)}) $thenE else $elseE)"
      // an object literal is never absent: at an optional slot it builds the inner type, present
      case "ObjectLiteralExpression" if isOptionLike(expectedType) =>
        val inner = translateExprAt(node, optionInner(expectedType))
        if isNullableType(expectedType) then s"Nullable($inner)" else s"Some($inner)"
      // A present value at an optional slot is wrapped: JS stores the value itself, the reference's optional says it is there.
      case _ if isOptionLike(expectedType) && exprType(node).exists(t => !isOptionLike(t) && t != "Null") =>
        val inner = translateExprAt(node, optionInner(expectedType))
        if isNullableType(expectedType) then s"Nullable($inner)" else s"Some($inner)"
      // a body-local JS array handed to an immutable slot is copied there
      case "Identifier" if exprType(node).exists(isMutableBuffer) && isHandOverSlot(expectedType) =>
        handOver(node, translateExpr(node), expectedType)
      // any other buffer (a call's result, say) may be held elsewhere too, so no copy of it is unobservable
      case _ if exprType(node).exists(isMutableBuffer) && isHandOverSlot(expectedType) =>
        refuse("buffer-at-immutable-slot")
        translateExpr(node)
      // A Boolean slot reads the JS value's truthiness, which is what the reference's Boolean records.
      case "BinaryExpression" | "ParenthesizedExpression" | "Identifier" | "PropertyAccessExpression" | "CallExpression" if expectedType == "Boolean" =>
        translateCondition(node)
      case "StringLiteral" | "FirstTemplateToken" | "LastTemplateToken" =>
        val str = node.value match
          case Some(RastValue.Str(s)) => s
          case _                      => ""
        if str.nonEmpty then
          // Try enum index first: maps (TypeName, "stringValue") -> MemberName
          enumIndex.resolve(baseType, str) match
            case Some(memberName) => s"$baseType.$memberName"
            case None             =>
              // Fallback: check member index for a member matching the string
              if memberIndex.knowsType(baseType) then
                val camel       = snakeToCamel(str)
                val capitalized = str.take(1).toUpperCase + str.drop(1)
                if memberIndex.hasMember(baseType, camel) then s"$baseType.$camel"
                else if memberIndex.hasMember(baseType, capitalized) then s"$baseType.$capitalized"
                else if memberIndex.hasMember(baseType, str) then s"$baseType.$str"
                else
                  refuse("literal-at-enum-slot")
                  translateExpr(node)
              else if isSpecificClassType(expectedType) && !expectedType.contains("String") then
                refuse("literal-at-enum-slot")
                translateExpr(node)
              else translateExpr(node)
        else translateExpr(node)
      case "ArrayLiteralExpression" if !node.children.exists(_.kind == "SpreadElement") =>
        val elemType = extractElementType(expectedType)
        if elemType.isDefined then
          val et = elemType.get
          // The literal builds the collection the slot declares: an immutable slot cannot be mutated through, so a fresh JS array is faithful there.
          val coll = expectedType.takeWhile(_ != '[') match
            case "Array"      => "Array"
            case "Vector"     => "scala.collection.immutable.Vector"
            case "List"       => "scala.collection.immutable.List"
            case "IndexedSeq" => "scala.collection.immutable.IndexedSeq"
            case "Seq"        => "scala.collection.immutable.Seq"
            case _            => "scala.collection.mutable.ArrayBuffer"
          // A type the slot names may be spelled through the CALLEE file's imports: only a standard
          // type is written out, any other is left to Scala's inference from the slot.
          val targ = if writableType(et) then s"[$et]" else ""
          if node.children.isEmpty then s"$coll.empty$targ"
          else
            // each element is of the slot's element type, an optional one never where a value is required
            val elems = node.children.map { c =>
              exprType(c).foreach(checkTypeCompatibility(_, et))
              translateExprAt(c, et)
            }
            s"$coll$targ(${elems.mkString(", ")})"
        else if conventions.tupleRecords.contains(baseName(expectedType)) then
          val cls = baseName(expectedType)
          // a fixed-length JS array at a tuple record's slot builds the record, position by position
          ctorSchema.get(cls) match
            case Some(ps) if ps.map(_.name) == conventions.tupleRecords(cls) && ps.size == node.children.size =>
              node.children.zip(ps).map((c, p) => translateExprAt(c, p.tpe)).mkString(s"$cls(", ", ", ")")
            case _ =>
              refuse("array-literal-at-class-slot")
              translateExpr(node)
        else
          tupleComponents(expectedType) match
            // a fixed-length JS array at a Scala tuple slot: the same values, by position
            case Some(parts) if parts.size == node.children.size =>
              node.children.zip(parts).map((c, t) => translateExprAt(c, t)).mkString("(", ", ", ")")
            case Some(_) =>
              refuse("array-literal-at-class-slot")
              translateExpr(node)
            case None =>
              // A JS array where the reference declares a class (a tuple the port reshaped into a record) has no faithful spelling here.
              if isSpecificClassType(expectedType) then refuse("array-literal-at-class-slot")
              translateExpr(node)
      case "ObjectLiteralExpression" =>
        translateObjectLiteral(node, Some(expectedType))
      case k if k == "NullKeyword" || k == "UndefinedKeyword" || (k == "Identifier" && node.text.contains("undefined")) =>
        if isNullableType(expectedType) then "Nullable.Null"
        else if isOptionLike(expectedType) then "None"
        else if expectedType.contains("| Null") then "null"
        else
          val base = expectedType.takeWhile(c => c != '[' && c != ' ')
          if base != "Any" && base != "AnyRef" && base != "Null" && base != "Nothing" then refuse("null-at-non-nullable-slot")
          translateExpr(node)
      case _ => translateExpr(node)

  /** Translate an expression in return position, applying the declared return type as the expected type for literal propagation.
    */
  private def translateReturnExpr(node: RastNode): String =
    // Check return type compatibility when both the return type and the
    // expression type are known
    declaredReturnType.foreach { rt =>
      exprType(node).foreach(argType => checkTypeCompatibility(argType, rt))
    }
    declaredReturnType match
      case Some(rt) => translateExprAt(node, rt)
      case None     => translateExpr(node)

  def result(): String = sb.toString

  /** Check every qualified reference emitted through the callee index or recorded from a property access on a known skeleton object against what that object itself DECLARES: `X.m` where `X` declares
    * no `m` refuses `unresolved-reference`, and a private `m` the emitting context cannot reach refuses `private-member-of-other-object`.
    */
  def checkUnresolvedReferences(): Unit =
    for (obj, member) <- emittedQualifiedRefs.toList.sorted do
      calleeIndex.declared(obj, member) match
        case Some(ref) if !ref.accessibleFrom(enclosingOwner) => refuse("private-member-of-other-object")
        case Some(_)                                          => ()
        case None if memberIndex.hasMember(obj, member)       => ()
        case None                                             => refuse("unresolved-reference")

  def translateBlock(block: RastNode): Unit =
    // Pre-check: if the body uses `this` and the member index has data but
    // does not know the entry's class type, refuse upfront — the translator
    // cannot validate member accesses on an unknown receiver.
    if memberIndex.byType.nonEmpty && !memberIndex.knowsType(nodeScalaType) && !conventions.rootClass.contains(nodeScalaType) then if containsThisKeyword(block) then refuse("receiver-type-unknown")
    // Also refuse when nodeScalaType is the library's root class only by default:
    // the entry's own class is neither the library's nor one the reference declares
    if conventions.rootClass.contains(nodeScalaType) && !conventions.isClassName(entry.className) && !memberIndex.knowsType(entry.className) && memberIndex.byType.nonEmpty then
      if containsThisKeyword(block) then refuse("receiver-type-unknown")
    // A JS parameter is a variable the body may assign, a Scala one is not: each the body itself writes is copied into a local `var` the body
    // reads instead; one a nested function writes is refused, since that function's own context would write the parameter.
    if topLevel then
      for p <- entry.params if assignedAnywhere.contains(p) do
        if !reassignedParams.contains(p) || writtenInNestedFunction(p) then refuse("reassigned-immutable")
        else paramShadows(p) = freshLabel(snakeToCamel(p))
    // The body names its parameters as upstream does; a reference that renamed one leaves the body naming nothing.
    if paramTypes.nonEmpty && entry.params.exists(p => !paramTypes.contains(p) && !paramTypes.contains(referenceParam(p))) then refuse("parameter-name-mismatch")

    val stmts = block.children
    if stmts.isEmpty then
      sb.append(s"${baseIndent}()\n")
      return

    // Single-return optimization: unwrap `{ return expr; }` to just the expr
    if stmts.size == 1 && stmts.head.kind == "ReturnStatement" && (paramShadows.isEmpty || !topLevel) then
      val ret = stmts.head
      if ret.children.isEmpty then sb.append(s"${baseIndent}null\n")
      else
        val expr = translateReturnExpr(ret.children.head)
        sb.append(s"$baseIndent$expr\n")
      return

    // Detect early returns: any ReturnStatement that is not the last statement
    val needsBoundary = returnLabel.isEmpty && hasEarlyReturn(stmts)

    // Multi-statement body needs braces (the caller emits `def foo(): Any =\n`)
    val needsBraces = stmts.size > 1 || stmts.head.kind == "VariableStatement" ||
      stmts.head.kind == "FirstStatement" || stmts.head.kind == "IfStatement" ||
      stmts.head.kind == "ForStatement" || stmts.head.kind == "ForInStatement" ||
      stmts.head.kind == "ForOfStatement" || stmts.head.kind == "WhileStatement" ||
      stmts.head.kind == "DoStatement" || stmts.head.kind == "SwitchStatement" ||
      stmts.head.kind == "TryStatement" || (topLevel && paramShadows.nonEmpty)
    if needsBoundary then
      val lbl = freshLabel("ret")
      returnLabel = lbl
      val retType = declaredReturnType.getOrElse(ReturnTypePlaceholder)
      sb.append(s"${baseIndent}scala.util.boundary { ($lbl: scala.util.boundary.Label[$retType]) ?=>\n")
    else if needsBraces then sb.append(s"$baseIndent{\n")

    val innerIndent = if needsBoundary || needsBraces then baseIndent + "  " else baseIndent

    if topLevel then for p <- entry.params; shadow <- paramShadows.get(p) do sb.append(s"${innerIndent}var $shadow = ${referenceParam(p)}\n")
    translateSequence(stmts, innerIndent, true)

    if needsBoundary || needsBraces then sb.append(s"$baseIndent}\n")

  private val voidValue: String = if declaredReturnType.contains("Unit") then "()" else "null"

  // the nested functions that bind a `this` of their own: every kind but an arrow
  private val ownThisKinds = Set("FunctionExpression", "FunctionDeclaration", "MethodDeclaration", "GetAccessor", "SetAccessor", "Constructor")

  /** A `function` nested in the body binds its own `this`, whatever its caller calls it on; only an arrow reads the body's, so a `this` under any other is refused. */
  private def refuseOwnThis(thisNode: RastNode): Unit =
    if Iterator.iterate(parentOf(thisNode))(_.flatMap(parentOf)).takeWhile(_.isDefined).flatten.exists(a => ownThisKinds(a.kind)) then refuse("function-own-this")

  def translateStatement(node: RastNode, indent: String, isLast: Boolean): Unit = at(node)(renderStatement(node, indent, isLast))

  // an lvalue built of names, member reads and literal or name indices: evaluating it twice reads the same place
  private def readsAlikeTwice(n: RastNode): Boolean =
    n.kind match
      case "Identifier" | "ThisKeyword" | "NumericLiteral" | "StringLiteral" => true
      case "ParenthesizedExpression"                                         => n.children.forall(readsAlikeTwice)
      case "PropertyAccessExpression" | "ElementAccessExpression"            => n.children.forall(readsAlikeTwice)
      case _                                                                 => false

  /** A function running off its end after its last loop returns `undefined`: the absent value the declared result spells, refused where it has none. A `while (true)` no `break` leaves never ends, so
    * nothing after it runs.
    */
  private def fallOffEnd(indent: String, isLast: Boolean, loop: Option[RastNode] = None): Unit =
    if isLast && declaredReturnType.exists(rt => rt != "Unit" && rt != ReturnTypePlaceholder) then
      // any `break` at all, a labelled one or one of a nested loop included, keeps the end reachable for this test
      def breaks(n: RastNode): Boolean = n.kind == "BreakStatement" || n.children.exists(breaks)
      // `while (true)`, `do … while (true)`, or a `for (;;)` holding nothing but its body
      val endless = loop.exists(l => (l.children.exists(_.kind == "TrueKeyword") || l.kind == "ForStatement" && l.children.size == 1) && !breaks(l))
      if endless then sb.append(s"${indent}throw new IllegalStateException(\"unreachable: the loop above leaves only by return\")\n")
      else sb.append(s"$indent${translateExprAt(RastNode("UndefinedKeyword", 0, (0, 0)), declaredReturnType.get)}\n")

  private def renderStatement(node: RastNode, indent: String, isLast: Boolean): Unit =
    node.kind match
      case "ReturnStatement" =>
        if node.children.isEmpty then
          if isLast then sb.append(s"$indent$voidValue\n")
          else if returnLabel.nonEmpty then sb.append(s"${indent}scala.util.boundary.break($voidValue)(using $returnLabel)\n")
          else
            refuse("early-return-no-boundary")
            sb.append(s"$indent$voidValue\n")
        else
          val expr = translateReturnExpr(node.children.head)
          if isLast then sb.append(s"$indent$expr\n")
          else if returnLabel.nonEmpty then sb.append(s"${indent}scala.util.boundary.break($expr)(using $returnLabel)\n")
          else
            refuse("early-return-no-boundary")
            sb.append(s"$indent$expr\n")

      case "ExpressionStatement" =>
        // When this is the last expression in the body, apply the return type
        val root = node.children.headOption.map(unparenthesized).orNull
        val expr = withStatementRoot(root) {
          if isLast then node.children.headOption.map(translateReturnExpr).getOrElse("()")
          else node.children.headOption.map(translateExpr).getOrElse("()")
        }
        sb.append(s"$indent$expr\n")

      case "VariableStatement" | "FirstStatement" =>
        for
          vdl <- node.children.find(_.kind == "VariableDeclarationList")
          vd <- vdl.children.filter(_.kind == "VariableDeclaration")
        do
          val name           = vd.children.headOption.flatMap(_.text).getOrElse("_")
          val bindingPattern = vd.children.headOption.filter(c => c.kind == "ObjectBindingPattern" || c.kind == "ArrayBindingPattern")
          // a local nothing writes after its declaration is a `val`, whether JS declared it `const`, `let` or `var`
          val bound     = bindingPattern.fold(List(name))(bindingsDeclaredBy)
          val keyword   = if bound.exists(assignedAnywhere.contains) then "var" else "val"
          val scalaName = snakeToCamel(name)
          if bindingPattern.isDefined then destructure(bindingPattern.get, vd.children.drop(1).lastOption, keyword, indent)
          else
            // `let x: T = e`: the annotation precedes the initialiser and is never one itself.
            val (annotation, initNode) = vd.children.drop(1) match
              case t :: rest if isTypeNode(t) => (Some(t), rest.headOption)
              case rest                       => (None, rest.headOption)
            val declared = annotation.map(t => annotationType(t))
            // an annotation is no run-time fact: a local nothing writes after an initialiser that types itself (a typed value, or an object literal, which builds its tag's class or is refused) is what
            // that initialiser is; an empty array needs the annotation to name its elements
            val selfTyped = initNode.exists(n => exprType(n).isDefined || n.kind == "ObjectLiteralExpression") && !bound.exists(assignedAnywhere.contains)
            declared.foreach {
              case Left(kind) if !selfTyped => refuse(s"local-type-annotation:$kind")
              case _                        => ()
            }
            val annotated = declared.flatMap(_.toOption)
            // JS leaves an uninitialised local `undefined`; no Scala value of its type says that, unless no read can see it
            val definite = if initNode.isEmpty then definitelyAssignedType(vd, name, annotated).filter(writableType) else None
            // `const xs = []` the body returns is a buffer of the declared result's elements
            val returned = if annotated.isEmpty && initNode.exists(n => n.kind == "ArrayLiteralExpression" && n.children.isEmpty) then returnedBufferType(vd, name) else None
            // a local starting from an integer that later holds a fraction is a double, as every JS number is
            val widened      = if initNode.exists(n => n.kind == "NumericLiteral" && exprType(n).contains("Int")) then widenedNumber(name) else None
            val declaredType = annotated.orElse(definite).orElse(returned).orElse(widened)
            if initNode.isEmpty && definite.isEmpty then refuse("uninitialised-local")
            // `let x = null` types the local as Null in Scala: nothing can be stored into it later
            if declaredType.isEmpty && initNode.exists(n => n.kind == "NullKeyword" || n.kind == "UndefinedKeyword" || n.text.contains("undefined")) then refuse("null-initialised-local")
            val init = initNode.map(n => declaredType.fold(translateExpr(n))(translateExprAt(n, _))).getOrElse(definite.fold("null")(unobservedValue))
            // Track local variable types from the annotation, else from an initialiser whose type is known
            localNames += scalaName
            declaredType.orElse(initNode.flatMap(exprType)).foreach(t => localTypes(scalaName) = t)
            // A variable whose initialiser is empty or whitespace produces
            // Unit in value position when read later
            if init.trim.isEmpty then refuse("unit-in-value-position")
            // a class the annotation names may not be imported here: only a standard type is written out
            val ascription = declaredType.filter(writableType).fold("")(t => s": $t")
            sb.append(s"$indent$keyword $scalaName$ascription = $init\n")

      case "IfStatement" =>
        val children = node.children
        if children.isEmpty then
          refuse("EmptyIfStatement")
          sb.append(s"$indent??? /* empty if */\n")
        else
          val cond = translateCondition(children.head)
          sb.append(s"${indent}if ($cond) {\n")
          if children.size > 1 then
            // Both branches are in value position when the if-else is the last
            // expression; passing isLast to the then-branch prevents emitting a
            // boundary.break with an empty label when both branches return.
            underNarrowing(narrowedBy(children.head))(underPresence(children.head)(translateStatementBody(children(1), indent + "  ", isLast)))
          if children.size > 2 then
            sb.append(s"$indent} else {\n")
            underNarrowing(refutedBy(children.head))(translateStatementBody(children(2), indent + "  ", isLast))
          else if isLast && declaredReturnType.exists(rt => rt != "Unit" && rt != ReturnTypePlaceholder) then
            // a function falling off its end returns `undefined`: the absent value the declared result spells, refused where it has none
            sb.append(s"$indent} else {\n")
            sb.append(s"$indent  ${translateExprAt(RastNode("UndefinedKeyword", 0, (0, 0)), declaredReturnType.get)}\n")
          sb.append(s"$indent}\n")

      case "Block" => translateSequence(node.children, indent, isLast)

      case "ForStatement" =>
        translateForStatement(node, indent)
        fallOffEnd(indent, isLast, Some(node))

      case "ForInStatement" | "ForOfStatement" =>
        translateForInOfStatement(node, indent)
        fallOffEnd(indent, isLast)

      case "WhileStatement" =>
        val children = node.children
        if children.size >= 2 then
          val bodyNode = children(1)
          emitLoop(indent, translateCondition(children.head), bodyNode, isDoWhile = false)
          fallOffEnd(indent, isLast, Some(node))
        else
          refuse("MalformedWhile")
          sb.append(s"$indent??? /* malformed while */\n")

      case "ThrowStatement" =>
        val expr = node.children.headOption.map(translateExpr).getOrElse("null")
        sb.append(s"${indent}throw $expr\n")

      case "TryStatement" =>
        sb.append(s"${indent}try {\n")
        val tryBlock = node.children.headOption
        // the body's last `try` answers the function's value where neither its block nor its catch falls off the end
        val tail = isLast && tryIsTail(node)
        tryBlock.foreach(b => translateStatementBody(b, indent + "  ", tail))
        sb.append(s"$indent}")
        node.children.find(_.kind == "CatchClause").foreach { catchClause =>
          val param     = catchClause.children.find(_.kind == "VariableDeclaration").orElse(catchClause.children.find(_.kind == "Identifier"))
          val paramName = param.flatMap(p => p.children.headOption.flatMap(_.text).orElse(p.text)).getOrElse("e")
          sb.append(s" catch {\n")
          // no JS catch sees a return, break or continue, while the boundary jump they become is a Throwable
          sb.append(s"$indent  case b: scala.util.boundary.Break[?] => throw b\n")
          sb.append(s"$indent  case ${snakeToCamel(paramName)}: Throwable =>\n")
          catchClause.children.find(_.kind == "Block").foreach { b =>
            for (stmt, i) <- b.children.zipWithIndex do translateStatement(stmt, indent + "    ", tail && i == b.children.size - 1)
          }
          sb.append(s"$indent}")
        }
        // the block after the try's own is its `finally`, which runs however the try completes
        node.children.drop(1).find(_.kind == "Block").foreach { finallyBlock =>
          sb.append(" finally {\n")
          translateStatementBody(finallyBlock, indent + "  ", false)
          sb.append(s"$indent}")
        }
        sb.append("\n")

      case "SwitchStatement" =>
        translateSwitchStatement(node, indent, isLast)

      case "DoStatement" =>
        val children = node.children
        if children.size >= 2 then
          val bodyNode = children.head
          emitLoop(indent, translateExpr(children(1)), bodyNode, isDoWhile = true)
          fallOffEnd(indent, isLast, Some(node))
        else sb.append(s"$indent??? /* malformed do-while */\n")

      case "FunctionDeclaration" =>
        val name   = node.children.find(_.kind == "Identifier").flatMap(_.text).getOrElse("_fn")
        val params = node.children.filter(_.kind == "Parameter").map { p =>
          val pName = p.children.find(_.kind == "Identifier").flatMap(_.text).getOrElse("_")
          snakeToCamel(pName)
        }
        val body = node.children.find(_.kind == "Block")
        sb.append(s"${indent}def ${snakeToCamel(name)}(${params.map(p => s"$p: Any").mkString(", ")}): Any = {\n")
        body.foreach(b => translateStatementBody(b, indent + "  ", true))
        sb.append(s"$indent}\n")

      case "BreakStatement" =>
        val label = node.children.find(_.kind == "Identifier").flatMap(_.text)
        if label.isDefined then
          refuse(s"LabelledBreak:${label.get}")
          sb.append(s"$indent??? /* break ${label.get} */\n")
        else sb.append(s"${indent}scala.util.boundary.break(())(using $breakLabel)\n")

      case "ContinueStatement" =>
        val label = node.children.find(_.kind == "Identifier").flatMap(_.text)
        if label.isDefined then
          refuse(s"LabelledContinue:${label.get}")
          sb.append(s"$indent??? /* continue ${label.get} */\n")
        else sb.append(s"${indent}scala.util.boundary.break(())(using $continueLabel)\n")

      case "EmptyStatement" =>
        () // skip

      case other =>
        refuse(s"UnhandledStatement:$other")
        sb.append(s"$indent??? /* $other */\n")

  private def translateStatementBody(node: RastNode, indent: String, isLast: Boolean): Unit =
    node.kind match
      case "Block" => translateSequence(node.children, indent, isLast)
      case _       =>
        translateStatement(node, indent, isLast)

  def translateExpr(node: RastNode): String = at(node)(renderExpr(node))

  private def renderExpr(node: RastNode): String =
    node.kind match
      case "NumericLiteral" | "FirstLiteralToken" =>
        node.value match
          case Some(RastValue.Num(n)) =>
            // an integral value past Int's range is no Int literal; JS holds it as a double
            if n == n.toLong && n.abs <= Int.MaxValue then n.toLong.toString else n.toString
          case _ => "0"

      case "StringLiteral" | "FirstTemplateToken" | "LastTemplateToken" =>
        node.value match
          case Some(RastValue.Str(s)) =>
            "\"" + escapeString(s) + "\""
          case _ => "\"\""

      case "RegularExpressionLiteral" =>
        val text = node.value match
          case Some(RastValue.Str(s)) => s
          case _                      => node.text.getOrElse("/???/")
        val body  = text.stripPrefix("/").reverse.dropWhile(_ != '/').reverse.stripSuffix("/")
        val flags = text.reverse.takeWhile(_ != '/').reverse
        // i, m and s have inline Java spellings; g is read where the regex is used; u, y and d have none
        if flags.exists(f => !"gims".contains(f)) then refuse("regex-flag")
        val inlineFlags = flags.filter("ims".contains(_)).map(f => s"(?$f)").mkString
        val escaped     = (inlineFlags + body).replace("\\", "\\\\").replace("\"", "\\\"")
        s"\"$escaped\".r"

      case "TrueKeyword"      => "true"
      case "FalseKeyword"     => "false"
      case "NullKeyword"      => "null"
      case "UndefinedKeyword" => "null"
      case "VoidExpression"   => "null"

      case "BooleanLiteral" =>
        node.value match
          case Some(RastValue.Bool(b)) => b.toString
          case _                       => node.text.getOrElse("false")

      case "ThisKeyword" =>
        refuseOwnThis(node)
        thisBinding
      case "SuperExpression" => "super"

      case "Identifier" =>
        val name = node.text.getOrElse("_")
        translateIdentifier(name)

      case "PropertyAccessExpression" =>
        translatePropertyAccess(node)

      case "ElementAccessExpression" =>
        translateElementAccess(node)

      case "CallExpression" =>
        translateCallExpr(node)

      case "NewExpression" =>
        val children = node.children
        val cls      = translateExpr(children.head)
        val rawArgs  = children.drop(1)
        // Apply expected-type propagation to constructor arguments via ctor schema
        val ctorParamTypes = ctorSchema.get(cls).getOrElse(Nil)
        // `new C({ a: x })` where the constructor spells the options bag as parameters
        val named = rawArgs match
          case List(o) if o.kind == "ObjectLiteralExpression" =>
            namedArguments(ctorParamTypes.map(p => ReferenceSignatures.ParamSig(p.name, p.tpe, p.hasDefault)), o)
          case _ => None
        val args = named.getOrElse(
          rawArgs.zipWithIndex.map { case (arg, i) =>
            ctorParamTypes.lift(i) match
              case Some(param) => translateExprAt(arg, param.tpe)
              case None        => translateExpr(arg)
          }
        )
        // a constructor argument is checked against its parameter as a call's is (Settings where a Boolean is expected is refused)
        if named.isEmpty then
          for (arg, i) <- rawArgs.zipWithIndex; param <- ctorParamTypes.lift(i); argType <- exprType(arg) do checkTypeCompatibility(argType, param.tpe)
          // more arguments than the constructor takes, or one left out that has no default to stand for JS's `undefined`
          if ctorSchema.get(cls).exists(ps => rawArgs.size > ps.size || ps.drop(rawArgs.size).exists(!_.hasDefault)) then refuse("callee-arity-mismatch")
        cls match
          case "Array" =>
            if args.isEmpty then "scala.collection.mutable.ArrayBuffer.empty[Any]"
            else s"scala.collection.mutable.ArrayBuffer.fill(${args.head})(null)"
          // `new Map(entries)` and `new Set(xs)` take the iterable's ELEMENTS, never the iterable as one
          case "Map" | "`Map`" =>
            if args.isEmpty then "scala.collection.mutable.Map.empty[Any, Any]"
            else s"scala.collection.mutable.Map.from(${args.head})"
          case "Set" | "`Set`" =>
            if args.isEmpty then "scala.collection.mutable.Set.empty[Any]"
            else s"scala.collection.mutable.Set.from(${args.head})"
          case "WeakMap" =>
            "scala.collection.mutable.WeakHashMap.empty[Any, Any]"
          case "WeakSet" =>
            "scala.collection.mutable.Set.empty[Any] /* WeakSet */"
          case "RegExp" =>
            // a flags argument changes the match, and a JS pattern's syntax is not Java's
            refuse("regex-constructor")
            s"${args.headOption.getOrElse("\"\"")}.r"
          case "Error" | "TypeError" | "RangeError" | "SyntaxError" | "ReferenceError" =>
            s"new RuntimeException(${args.mkString(", ")})"
          case _ =>
            s"new $cls(${args.mkString(", ")})"

      case "BinaryExpression" =>
        translateBinaryExpr(node)

      // an absent element is `undefined`, so `!xs[i]` holds for it
      case "PrefixUnaryExpression" if node.operator.contains("ExclamationToken") && node.children.headOption.flatMap(absentFalsyRead).isDefined =>
        s"!(${node.children.headOption.flatMap(absentFalsyRead).get()})"
      case "PrefixUnaryExpression" =>
        val children = node.children
        val operand  = if children.nonEmpty then translateExpr(children.head) else "???"
        node.operator match
          case Some("ExclamationToken") =>
            // JS `!x` is the negation of the truthiness test. The correct Scala
            // depends on the operand's declared type: Nullable -> .isEmpty,
            // String -> .isEmpty, numeric -> == 0, Boolean -> !x.
            if children.nonEmpty then
              exprType(children.head) match
                case Some(tpe) =>
                  val test = truthinessTest(tpe, operand)
                  if test == operand then s"!$operand" else s"!($test)"
                case None =>
                  refuse("truthiness-unknown-type")
                  s"!$operand"
            else s"!$operand"
          case Some("MinusToken") => s"-$operand"
          case Some("PlusToken")  =>
            // JS unary + is numeric coercion. Skip when the operand is already numeric.
            val operandType = if children.nonEmpty then exprType(children.head) else None
            if operandType.exists(numericTypes.contains) then operand
            else
              // When the operand is a String and the result is used in string
              // concatenation, the JS round-trip `+s` strips trailing zeros and
              // the ".0" suffix for whole numbers. JVM Double.toString keeps
              // ".0", so use BigDecimal.stripTrailingZeros.toPlainString to match.
              // JS reads a string as a number by its own grammar (`""` is 0, `"0x10"` 16, anything else NaN), where Scala's parsers throw
              if operandType.contains("String") then refuse("numeric-coercion")
              s"$operand.toDouble"
          case Some("TildeToken") => s"~$operand"
          // the answer re-reads the operand, which only a path reads alike twice
          case Some("PlusPlusToken" | "MinusMinusToken") if !children.headOption.exists(readsAlikeTwice) || !isSimplePath(operand) && (statementRoot ne node) =>
            refuse("increment-as-value")
            s"{ $operand ${if node.operator.contains("PlusPlusToken") then "+=" else "-="} 1; $operand }"
          case Some("PlusPlusToken")   => s"{ $operand += 1; $operand }"
          case Some("MinusMinusToken") => s"{ $operand -= 1; $operand }"
          case Some(op)                => s"/* $op */$operand"
          case None                    => s"!$operand"

      case "PostfixUnaryExpression" =>
        val children = node.children
        val operand  = if children.nonEmpty then translateExpr(children.head) else "???"
        val step     = node.operator match
          case Some("PlusPlusToken")   => Some("+=")
          case Some("MinusMinusToken") => Some("-=")
          case _                       => None
        // `xs(i) += 1` reads and writes its lvalue's parts twice, which only names and literals read alike
        if !children.headOption.exists(readsAlikeTwice) then refuse("increment-as-value")
        if statementRoot eq node then step.fold(s"$operand /* postfix */")(op => s"$operand $op 1")
        else
          // `x++` yields the value BEFORE the update: read once into a temporary, on a path that reads alike twice
          if !isSimplePath(operand) || step.isEmpty || !children.headOption.flatMap(exprType).exists(numericTypes) then refuse("increment-as-value")
          val p = freshLabel("p")
          s"{ val $p = $operand; $operand ${step.getOrElse("+=")} 1; $p }"

      case "ConditionalExpression" =>
        val children = node.children
        if children.size >= 3 then
          val thenE = underNarrowing(narrowedBy(children(0)))(underPresence(children(0))(translateExpr(children(1))))
          val elseE = underNarrowing(refutedBy(children(0)))(translateExpr(children(2)))
          // JS `x ? a : b` uses truthiness of x. Lower the condition based
          // on the operand's declared type.
          val condNode = children(0)
          val combines = condNode.kind == "BinaryExpression" && condNode.operator.exists(op => op == "AmpersandAmpersandToken" || op == "BarBarToken")
          if exprType(condNode).isEmpty && !combines then refuse("truthiness-unknown-type")
          s"(if (${translateCondition(condNode)}) $thenE else $elseE)"
        else "??? /* bad conditional */"

      case "ParenthesizedExpression" =>
        val inner = node.children.headOption.map(translateExpr).getOrElse("()")
        s"($inner)"

      case "ArrowFunction" | "FunctionExpression" =>
        translateFunctionExpr(node)

      case "ArrayLiteralExpression" =>
        val hasSpread = node.children.exists(_.kind == "SpreadElement")
        val allSpread = node.children.nonEmpty && node.children.forall(_.kind == "SpreadElement")
        if node.children.isEmpty then
          // `[]` where no slot declares the element type: an ArrayBuffer[Any] fits no typed use
          refuse("untyped-empty-array")
          "scala.collection.mutable.ArrayBuffer.empty[Any]"
        else if allSpread && node.children.length == 1 then
          // [...arr] is a fresh mutable copy of a collection; of anything else it has no Scala reading here
          val source = node.children.head.children.head
          if !exprType(source).exists(t => extractElementType(t).isDefined) then refuse("array-spread")
          s"scala.collection.mutable.ArrayBuffer.from(${translateExpr(source)})"
        else if hasSpread then
          // Mixed spread + non-spread
          val nonSpread  = node.children.takeWhile(_.kind != "SpreadElement")
          val spreadPart = node.children.dropWhile(_.kind != "SpreadElement")
          val prefix     = if nonSpread.nonEmpty then s"scala.collection.mutable.ArrayBuffer(${nonSpread.map(translateExpr).mkString(", ")})"
          else "scala.collection.mutable.ArrayBuffer.empty[Any]"
          val suffix = spreadPart.map { c =>
            if c.kind == "SpreadElement" then translateExpr(c.children.head)
            else s"scala.collection.mutable.ArrayBuffer(${translateExpr(c)})"
          }
          (prefix +: suffix).mkString(" ++ ")
        else
          val elems = node.children.map(translateExpr)
          s"scala.collection.mutable.ArrayBuffer(${elems.mkString(", ")})"

      case "ObjectLiteralExpression" =>
        translateObjectLiteral(node, None)

      case "SpreadElement" =>
        val inner = node.children.headOption.map(translateExpr).getOrElse("???")
        s"$inner*"

      case "TemplateExpression" | "TemplateString" =>
        translateTemplateExpr(node)

      case "NoSubstitutionTemplateLiteral" =>
        node.value match
          case Some(RastValue.Str(s)) => "\"" + escapeString(s) + "\""
          case _                      => "\"\""

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
        // `delete o.k` removes a key only from a map; on an object of a declared class it has no Scala counterpart.
        val target = node.children.headOption.flatMap(_.children.headOption)
        if !target.flatMap(exprType).exists(t => t.contains("Map")) then refuse("delete-on-non-map")
        node.children.headOption match
          case Some(prop) if prop.kind == "PropertyAccessExpression" && prop.children.size >= 2 =>
            val obj = translateExpr(prop.children.head)
            val key = prop.children.last.text.getOrElse("")
            s"""$obj.remove("${escapeString(key)}")"""
          case Some(prop) if prop.kind == "ElementAccessExpression" && prop.children.size >= 2 =>
            val obj = translateExpr(prop.children.head)
            val key = translateExpr(prop.children(1))
            s"$obj.remove($key)"
          case _ =>
            refuse("DeleteExpression")
            val operand = node.children.headOption.map(translateExpr).getOrElse("???")
            s"??? /* delete $operand */"

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
          val target   = translateExpr(children.head)
          val cascades = children.tail.map(translateExpr)
          if cascades.isEmpty then target
          else s"{ val $$t = $target; ${cascades.map(c => s"$$t.$c").mkString("; ")}; $$t }"

      case "NamedExpression" =>
        val label = node.children.find(_.kind.contains("Label")).flatMap(_.children.find(_.kind.contains("Identifier")).flatMap(_.text)).orElse(node.children.headOption.flatMap(_.text)).getOrElse("_")
        val value = node.children.lastOption.map(translateExpr).getOrElse("???")
        s"$label = $value"

      case "SwitchExpression" =>
        val children = node.children
        if children.isEmpty then "??? /* empty switch */"
        else
          val scrutinee = translateExpr(children.head)
          val cases     = children.tail
          val caseStrs  = cases.map { c =>
            val pat  = if c.children.nonEmpty then translateExpr(c.children.head) else "_"
            val body = if c.children.size > 1 then translateExpr(c.children.last) else "???"
            s"case $pat => $body"
          }
          s"($scrutinee match { ${caseStrs.mkString("; ")} })"

      case "IfNullExpression" =>
        val children = node.children
        if children.size >= 2 then
          val left  = translateExpr(children.head)
          val right = translateExpr(children.last)
          s"(if $left != null then $left else $right)"
        else "???"

      // Skip type annotations, labels, comments — they don't produce code
      case "TypeReference" | "FormalParameterList" | "Label" | "Comment" | "ConstructorName" | "ArgumentList" | "NamedType" => ""

      case other =>
        refuse(s"UnhandledExpr:$other")
        s"??? /* $other */"

  // --------------------------------------------------------------------------
  // Expression helpers
  // --------------------------------------------------------------------------

  private def translateIdentifier(name: String): String =
    val base = name match
      case "undefined" => "null"
      case "Infinity"  => "Double.PositiveInfinity"
      case "NaN"       => "Double.NaN"
      case "console"   => "Console"
      case "arguments" =>
        refuse("ArgumentsObject")
        "??? /* arguments */"
      // a parameter the body writes is read and written through its local `var`, unless a lambda parameter shadows it
      case n if paramShadows.contains(n) && !localNames.contains(snakeToCamel(n)) => paramShadows(n)
      // a parameter the reference renamed is read under the reference's name
      case n if paramRenames.contains(n) && !localNames.contains(snakeToCamel(n)) => paramRenames(n)
      case n if conventions.isClassName(n)                                        => conventions.scalaClassName(n)
      // an enclosing function's binding: the class the body is emitted into holds it, or nothing does
      case n if outerBinding(n).isDefined =>
        outerBinding(n).flatten.getOrElse {
          refuse("enclosing-function-binding")
          snakeToCamel(n)
        }
      case n if n.startsWith("_") && !apiLookup.contains(n) && !entry.params.contains(n) && !isLocal(n) =>
        // An underscore-prefixed name is usually a private helper: it is known only when the reference declares it under that same name.
        calleeIndex.resolveMember(snakeToCamel(n)) match
          case Right(ref) => qualify(ref)
          case Left(_)    =>
            refuse("wrong-member-access")
            snakeToCamel(n)
      // a value the target file declares at package level
      case n if !apiLookup.contains(n) && fileValue(n).isDefined => n
      case n                                                     =>
        val scName = apiLookup.getOrElse(n, snakeToCamel(n))
        // Resolve unqualified identifiers through the callee index:
        // a TS module-level const referenced from a method body needs
        // its enclosing object qualifier. Skip locals and parameters.
        if !apiLookup.contains(n) && !entry.params.contains(n) &&
          !localTypes.contains(scName) && !localTypes.contains(n) &&
          !localNames.contains(scName) && !localNames.contains(n) &&
          !conventions.isClassName(n) && scName == snakeToCamel(n) &&
          (n.head.isLower || !namesAType(n))
        then
          calleeIndex.resolveMember(scName) match
            case Right(ref) => qualify(ref)
            case Left(_)    =>
              enclosingOwner.flatMap(calleeIndex.declared(_, scName)) match
                case Some(own) => qualify(own) // ambiguous elsewhere, but the body's own object declares it
                case None      =>
                  // A name nothing in scope declares (an import alias of a whole module, a global) would be emitted bare and mean nothing.
                  if !declaredInBody(n) && !calleeIndex.knownObjects.contains(scName) then refuse("unresolved-identifier")
                  scName
        else scName
    // When this identifier was tested for Nullable truthiness in an
    // enclosing && guard, it has been unwrapped and must be read as .get
    val scalaName = snakeToCamel(name)
    if unwrappedIds.contains(scalaName) then s"$base.get" else narrowedIds.get(scalaName).fold(base)(cls => s"$base.asInstanceOf[${spelled(cls).getOrElse(cls)}]")

  private def translatePropertyAccess(node: RastNode): String =
    val children = node.children
    if children.size < 2 then return "??? /* bad prop access */"
    val obj  = children.head
    val prop = children.last.text.getOrElse("")

    // Math.x -> math.x (scala.math)
    if obj.kind == "Identifier" && obj.text.contains("Math") then
      return prop match
        case "PI" => "math.Pi"
        case "E"  => "math.E"
        case "abs" | "sqrt" | "pow" | "cos" | "sin" | "tan" | "asin" | "acos" | "atan2" | "floor" | "ceil" | "round" | "max" | "min" | "log" | "log2" | "log10" | "exp" | "random" | "sign" | "cbrt" |
            "hypot" =>
          s"math.$prop"
        case _ => s"math.$prop"

    // Number.x
    if obj.kind == "Identifier" && obj.text.contains("Number") then
      return prop match
        case "MAX_SAFE_INTEGER" => "Long.MaxValue"
        case "isNaN"            => "java.lang.Double.isNaN"
        case "isFinite"         => "java.lang.Double.isFinite"
        case "isInteger"        => "/* Number.isInteger */ ((_: Any) match { case n: Double => n == n.floor; case _ => false })"
        case _                  => s"Number.$prop"

    // this.x -> check apiLookup first, then translate known properties.
    // When the member index knows the class, validate the property.
    if obj.kind == "ThisKeyword" then
      refuseOwnThis(obj)
      conventions.typeTag match
        case Some((tag, scalaTag)) if tag == prop => s"$thisBinding.$scalaTag"
        case _                                    =>
          val camel     = snakeToCamel(prop)
          val scalaProp = memberRenames.getOrElse(camel, memberRenames.getOrElse(prop, apiLookup.getOrElse(prop, camel)))
          if !apiLookup.contains(prop) && !knownSafeProperties.contains(prop) then
            if memberIndex.knowsType(nodeScalaType) then
              if !memberIndex.hasMember(nodeScalaType, scalaProp) && !memberIndex.hasMember(nodeScalaType, camel) && !memberIndex.hasMember(nodeScalaType, prop) then refuse("wrong-member-access")
            else if memberIndex.byType.nonEmpty then
              // The member index has data but this type is unknown: the
              // translator cannot validate the access on this
              refuse("receiver-type-unknown")
          s"$thisBinding.$scalaProp"
    // node.x where node is a typed node param — validate property access
    else if obj.kind == "Identifier" && nodeNames.contains(obj.text.getOrElse("")) then
      val objName  = obj.text.getOrElse("")
      val scalaObj = if objName == "this" then thisBinding else snakeToCamel(objName)
      conventions.typeTag match
        case Some((tag, scalaTag)) if tag == prop => s"$scalaObj.$scalaTag"
        case _                                    =>
          val camel     = snakeToCamel(prop)
          val scalaProp = memberRenames.getOrElse(camel, memberRenames.getOrElse(prop, camel))
          s"$scalaObj.$scalaProp"
    // AST_X.prototype -> skip (handled by prototype extraction)
    else if obj.kind == "PropertyAccessExpression" && obj.children.size >= 2 && obj.children.last.text.contains("prototype") then
      s"${conventions.scalaClassName(obj.children.head.text.getOrElse("?"))}.${snakeToCamel(prop)}"
    else
      // the library's type-tag field is the member the reference spells it as
      val renamedProp   = conventions.typeTag.collect { case (tag, member) if tag == prop => member }.orElse(memberRenames.get(snakeToCamel(prop))).orElse(memberRenames.get(prop))
      val effectiveProp = renamedProp.getOrElse(snakeToCamel(prop))
      // The spelling the receiver's declared type actually has, when its type is known.
      var declaredSpelling: Option[String] = None
      // When the object is a parameter with a known specific type, check the
      // member index from the reference tree. If the type is known and the
      // member is not declared on it, refuse with wrong-member-access.
      // any receiver whose type is known is checked, a member read's included (`a.b.c` checks `.c` against `b`'s type)
      val tpe = if knownSafeProperties.contains(prop) then None else exprType(obj)
      if !knownSafeProperties.contains(prop) && (obj.kind == "Identifier" || tpe.isDefined) then
        tpe match
          case Some(t) if t.contains("|") || isOptionLike(t) =>
            // a member of a union or of a value that may be absent: TS narrowed it, Scala has not
            refuse("union-receiver")
          case Some(t) =>
            val baseType = t.takeWhile(c => c != '[' && c != ' ')
            if memberIndex.knowsType(baseType) then
              declaredSpelling = List(effectiveProp, snakeToCamel(prop), prop).find(memberIndex.hasMember(baseType, _))
              if declaredSpelling.isEmpty then refuse("wrong-member-access")
            else if isSpecificClassType(t) || Set("Any", "AnyRef", "Object")(baseType) then refuse("wrong-member-access")
          case None =>
            // Receiver type unknown and not a known binding: the translator
            // cannot validate the access.
            val objName   = obj.text.getOrElse("")
            val scObjName = snakeToCamel(objName)
            val isBinding = entry.params.contains(objName) || localNames.contains(scObjName) || localTypes.contains(scObjName)
            // A parameter or local whose type nothing declares: the member cannot be checked against anything.
            if !apiLookup.contains(objName) && isBinding then refuse("receiver-type-unknown")
            else if !apiLookup.contains(objName) then
              if calleeIndex.knownObjects.contains(scObjName) then
                // The receiver is a known skeleton object: record the access
                // for post-translation validation.
                emittedQualifiedRefs += ((scObjName, effectiveProp))
              else if calleeIndex.resolve(scObjName).isLeft then refuse("receiver-type-unknown")
      val objExpr = translateExpr(obj)
      declaredSpelling match
        case Some(p)                       => s"$objExpr.${if scalaKeywords.contains(p) then s"`$p`" else p}"
        case None if renamedProp.isDefined => s"$objExpr.${renamedProp.get}"
        case None                          => translatePropOnExpr(objExpr, prop)

  /** Translate a property access on an already-translated object expression. */
  private def translatePropOnExpr(objExpr: String, prop: String): String =
    prop match
      case "length"           => s"$objExpr.length"
      case "constructor"      => s"$objExpr.getClass"
      case "push"             => s"$objExpr.addOne"
      case "forEach"          => s"$objExpr.foreach"
      case "indexOf"          => s"$objExpr.indexOf"
      case "includes" | "has" => s"$objExpr.contains"
      case "splice"           =>
        refuse("js-splice")
        s"$objExpr.remove"
      case "pop"                    => s"$objExpr.remove($objExpr.length - 1)"
      case "shift"                  => s"$objExpr.remove(0)"
      case "unshift"                => s"$objExpr.prepend"
      case "concat"                 => s"$objExpr.concat"
      case "slice"                  => s"$objExpr.slice"
      case "join"                   => s"$objExpr.mkString"
      case "map"                    => s"$objExpr.map"
      case "filter"                 => s"$objExpr.filter"
      case "reduce"                 => s"$objExpr.reduce"
      case "reduceRight"            => s"$objExpr.reduceRight"
      case "some"                   => s"$objExpr.exists"
      case "every"                  => s"$objExpr.forall"
      case "find"                   => s"$objExpr.find"
      case "flat"                   => s"$objExpr.flatten"
      case "flatMap"                => s"$objExpr.flatMap"
      case "reverse"                => s"$objExpr.reverse"
      case "keys"                   => s"$objExpr.keys"
      case "values"                 => s"$objExpr.values"
      case "entries"                => s"$objExpr.iterator"
      case "sort"                   => s"$objExpr.sorted"
      case "toString"               => s"$objExpr.toString"
      case "charAt"                 => s"$objExpr.charAt"
      case "charCodeAt"             => s"$objExpr.charAt"
      case "substring" | "substr"   => s"$objExpr.substring"
      case "startsWith"             => s"$objExpr.startsWith"
      case "endsWith"               => s"$objExpr.endsWith"
      case "replace"                => s"$objExpr.replace"
      case "replaceAll"             => s"$objExpr.replaceAll"
      case "split"                  => s"$objExpr.split"
      case "trim"                   => s"$objExpr.trim"
      case "trimStart" | "trimLeft" => s"$objExpr.stripLeading"
      case "trimEnd" | "trimRight"  => s"$objExpr.stripTrailing"
      case "toLowerCase"            => s"$objExpr.toLowerCase"
      case "toUpperCase"            => s"$objExpr.toUpperCase"
      case "search"                 => s"$objExpr.search"
      case "match"                  => s"$objExpr.`match`"
      case "test"                   => s"$objExpr.test"
      case _                        => s"$objExpr.${snakeToCamel(prop)}"

  private def translateCallExpr(node: RastNode): String =
    val children = node.children
    if children.isEmpty then return "??? /* empty call */"
    val callee = children.head
    val args   = children.drop(1)

    callee.kind match
      case "PropertyAccessExpression" =>
        val calleeChildren = callee.children
        if calleeChildren.size >= 2 then
          val obj    = calleeChildren.head
          val method = calleeChildren.last.text.getOrElse("")

          // a fresh copy of a case-class value with some fields set
          if method == "assign" && obj.kind == "Identifier" && obj.text.contains("Object") && !declaredInBody("Object") then
            val copied = objectAssignCopy(args)
            if copied.isDefined then return copied.get

          // Handle special patterns
          // AST_X.prototype.method.apply(this, arguments)
          if method == "apply" && obj.kind == "PropertyAccessExpression" then
            val objParts = obj.children
            if objParts.size >= 2 then
              val innerObj  = objParts.head
              val innerProp = objParts.last.text.getOrElse("")
              if innerObj.kind == "PropertyAccessExpression" then
                val deepParts = innerObj.children
                if deepParts.size >= 2 && deepParts.last.text.contains("prototype") then
                  // Only `X.prototype.m.apply(this, …)` is a super call; any other receiver runs `m` on THAT object.
                  if !args.headOption.exists(_.kind == "ThisKeyword") then refuse("prototype-method-apply")
                  val innerScala = snakeToCamel(innerProp)
                  // super.method(args) pattern
                  val scalaArgs = args.drop(1).flatMap { a =>
                    if a.kind == "Identifier" && a.text.contains("arguments") then
                      refuse("ArgumentsObject")
                      List("??? /* arguments */")
                    else List(translateExpr(a))
                  }
                  return s"super.${innerScala}(${scalaArgs.mkString(", ")})"

          // a method of a value that may be absent: JS reached it after a guard Scala does not see
          if exprType(obj).exists(t => isOptionLike(t) || t.contains("| Null")) then refuse("optional-receiver")
          else if exprType(obj).exists(_.contains("|")) then refuse("union-receiver")
          // a method the receiver's declared type does not declare
          // On a class the reference declares the method is that class's own, under its own spelling:
          // JS collection renames (`has` -> `contains`) never apply there.
          val classReceiver = exprType(obj).filterNot(t => isOptionLike(t) || t.contains("|")).map(baseName).filter(memberIndex.knowsType)
          val classMethod   = classReceiver.flatMap(base => List(snakeToCamel(method), method).find(memberIndex.hasMember(base, _)))
          if classReceiver.isDefined && classMethod.isEmpty && !conventions.methodRewrites.contains(method) then refuse("wrong-member-access")
          exprType(obj).filterNot(t => isOptionLike(t) || t.contains("|") || classReceiver.isDefined).foreach { t =>
            if !knownSafeProperties.contains(method) && isSpecificClassType(t) then refuse("wrong-member-access")
          }
          val objExpr = translateExpr(obj)
          // Look up the callee's parameter types for expected-type propagation
          val moduleSig: Option[ReferenceSignatures.MethodSig] =
            if obj.kind == "Identifier" then
              val oName    = snakeToCamel(obj.text.getOrElse(""))
              val cap      = if oName.nonEmpty then oName.take(1).toUpperCase + oName.drop(1) else ""
              val methName = snakeToCamel(method)
              oracle.get(cap, methName).orElse(oracle.get(oName, methName))
            else if obj.kind == "ThisKeyword" && memberIndex.knowsType(nodeScalaType) then oracle.get(nodeScalaType, snakeToCamel(method))
            else None
          // a method of a receiver whose class the reference declares takes that class's parameters
          val calleeSig = moduleSig.orElse(for c <- classReceiver; m <- classMethod; s <- oracle.get(c, m) yield s)
          // a parameter typed by the receiver class's own type parameter (`Namespace[V]#set(v: Nullable[V])`) names a type the slot cannot check
          if exprType(obj).exists(_.contains('[')) && calleeSig.exists(_.params.exists(p => typeVariableIn(p.tpe))) then refuse("generic-receiver-parameter")
          val named = optionsBag(calleeSig, args)
          calleeSig.filter(_ => named.isEmpty).foreach { s =>
            val isVararg = s.params.exists(p => p.tpe.contains("*") || p.tpe.startsWith("Seq["))
            if args.length > s.params.length && !isVararg then refuse("callee-arity-mismatch")
            if args.length < s.params.length && !isVararg && omittedArgs(s, args.length).isEmpty then refuse("callee-arity-mismatch")
            // Check argument types against declared parameter types
            for (arg, i) <- args.zipWithIndex do
              s.params.lift(i).foreach { param =>
                exprType(arg).foreach { argType =>
                  checkTypeCompatibility(argType, param.tpe)
                }
              }
          }
          // For predicate methods, translate the callback with truthiness lowering.
          // Infer the element type from the receiver's collection type so the
          // lambda parameter gets a known type for truthiness.
          val isPredicate = predicateMethods.contains(method)
          val elemType: Option[String] =
            if isPredicate then exprType(obj).flatMap(extractElementType) else None
          val scalaArgs = named.getOrElse(
            calleeSig match
              case Some(s) =>
                args.zipWithIndex.map { case (arg, i) =>
                  s.params.lift(i).map(p => translateExprAt(arg, p.tpe)).getOrElse(translateExpr(arg))
                } ++ omittedArgs(s, args.length).getOrElse(Nil)
              case None =>
                val elem = exprType(obj).flatMap(extractElementType)
                args.zipWithIndex.map { case (arg, i) =>
                  if i == 0 && isPredicate then translatePredicateLambda(arg, elemType)
                  // an array method's callback takes the element, and `forEach` discards what it answers
                  else if i == 0 && elem.isDefined && elementCallbacks(method) && (arg.kind == "ArrowFunction" || arg.kind == "FunctionExpression") then
                    if arg.children.count(_.kind == "Parameter") > 1 then refuse("callback-index-parameter")
                    translateFunctionExpr(arg, List(elem), discardsResult = method == "forEach")
                  // the element an array receives is of the array's element type
                  else if elem.isDefined && (method == "push" || method == "unshift" || method == "splice" && i >= 2) && arg.kind != "SpreadElement" then
                    exprType(arg).foreach(checkTypeCompatibility(_, elem.get))
                    translateExprAt(arg, elem.get)
                  else translateExpr(arg)
                }
          )

          if classMethod.isDefined && !conventions.methodRewrites.contains(method) then
            val name = classMethod.get
            return s"$objExpr.${if scalaKeywords.contains(name) then s"`$name`" else name}(${scalaArgs.mkString(", ")})"

          // JS changes the array in place, which the reference holds as an immutable collection
          if Set("push", "pop", "shift", "unshift", "splice", "fill", "copyWithin")(method) && exprType(obj).exists(t => handOverConversions.contains(baseName(t))) then
            refuse("mutated-immutable-collection")
          method match
            // Collection methods
            case "has" | "includes" | "contains" =>
              s"$objExpr.contains(${scalaArgs.mkString(", ")})"
            case "set" =>
              s"$objExpr.update(${scalaArgs.mkString(", ")})"
            // a JS map answers `undefined` for an absent key: null for a reference value, else a `has` test must guard the read
            case "get" if exprType(obj).flatMap(mapValueType).isDefined =>
              if exprType(obj).flatMap(mapValueType).exists(absentIsNull) then s"$objExpr.getOrElse(${scalaArgs.mkString(", ")}, null)"
              else
                if !args.headOption.exists(keyProven(node, obj, _)) then refuse("read-past-end")
                s"$objExpr(${scalaArgs.mkString(", ")})"
            case "get" =>
              s"$objExpr.get(${scalaArgs.mkString(", ")})"
            case "delete" =>
              s"$objExpr.remove(${scalaArgs.mkString(", ")})"
            case "forEach" =>
              s"$objExpr.foreach(${scalaArgs.mkString(", ")})"
            case "push" =>
              // JS `push` answers the new length, which `+=` does not
              if statementRoot ne node then refuse("push-as-value")
              // JS evaluates every argument before it appends any: two or more are bound first, in order
              val bound = if args.size > 1 then scalaArgs.map(t => (freshLabel("a"), t.stripSuffix("*"))) else Nil
              val temps = bound.map((n, t) => s"val $n = $t")
              // each argument in order, a spread one element by element; a tuple record spreads its fields
              val pieces = args.zip(scalaArgs).zipWithIndex.flatMap { case ((arg, text), i) =>
                val value = bound.lift(i).fold(text.stripSuffix("*"))(_._1)
                if arg.kind != "SpreadElement" then List(s"$objExpr += $value")
                else
                  val src = arg.children.head
                  // spreading the receiver into itself reads what the appends are changing
                  if shapeOf(src) == shapeOf(obj) then refuse("array-spread")
                  exprType(src).flatMap(t => conventions.tupleRecords.get(baseName(t)).map(baseName(t) -> _)) match
                    case Some((cls, fields)) =>
                      if !isSimplePath(value) then refuse("array-spread")
                      exprType(obj).flatMap(extractElementType).foreach(e => fields.flatMap(memberIndex.memberType(cls, _)).foreach(checkTypeCompatibility(_, e)))
                      fields.map(f => s"$objExpr += $value.$f")
                    case None =>
                      if !exprType(src).exists(t => extractElementType(t).isDefined) then refuse("array-spread")
                      List(s"$objExpr ++= $value")
              }
              if pieces.size > 1 && !isSimplePath(objExpr) then refuse("array-spread")
              if pieces.size == 1 && temps.isEmpty then pieces.head else (temps ++ pieces).mkString("{ ", "; ", " }")
            // on an empty array JS answers `undefined` where `remove` throws: null for references, else a length test must guard it
            case "pop" | "shift" =>
              val at = if method == "pop" then "b$.length - 1" else "0"
              // as a statement nothing reads the answer, so an empty array just leaves nothing to remove
              if statementRoot eq node then s"{ val b$$ = $objExpr; if (b$$.nonEmpty) b$$.remove(${at}) }"
              else if exprType(obj).flatMap(extractElementType).exists(absentIsNull) then s"{ val b$$ = $objExpr; if (b$$.isEmpty) null else b$$.remove(${at}) }"
              else
                // an element to remove is a first one there
                if !indexProven(node, obj, RastNode("NumericLiteral", 0, node.pos, value = Some(RastValue.Num(0)))) then refuse("read-past-end")
                s"{ val b$$ = $objExpr; b$$.remove(${at}) }"
            case "unshift" =>
              s"$objExpr.prepend(${scalaArgs.mkString(", ")})"
            case "indexOf" =>
              s"$objExpr.indexOf(${scalaArgs.mkString(", ")})"
            case "lastIndexOf" =>
              s"$objExpr.lastIndexOf(${scalaArgs.mkString(", ")})"
            case "hasOwnProperty" =>
              s"$objExpr.contains(${scalaArgs.mkString(", ")})"
            case "splice" =>
              translateSplice(node, obj, objExpr, args, scalaArgs)
            case "concat" =>
              s"($objExpr ++ ${scalaArgs.mkString(" ++ ")})"
            case "join" =>
              // JS joins with "," by default and prints a whole double without its ".0"
              if !exprType(obj).flatMap(extractElementType).exists(t => t == "String" || integralTypes.contains(t)) && !checkerStringArray(obj) then refuse("value-to-string")
              s"$objExpr.mkString(${if scalaArgs.isEmpty then "\",\"" else scalaArgs.mkString(", ")})"
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
            // JS sorts and reverses IN PLACE (a default sort compares as strings); Scala's return a copy
            case "sort" =>
              refuse("js-in-place-reorder")
              if scalaArgs.nonEmpty then s"$objExpr.sortWith((a, b) => ${scalaArgs.head}(a, b) < 0)"
              else s"$objExpr.sorted"
            case "reverse" =>
              refuse("js-in-place-reorder")
              s"$objExpr.reverse"
            case "fill" =>
              if args.size > 1 then refuse("js-fill-range")
              s"$objExpr.mapInPlace(_ => ${scalaArgs.headOption.getOrElse("null")})"
            case "slice" =>
              // JS counts a negative bound from the end; only non-negative literal bounds read the same (`slice(a)` is `drop(a)`)
              if !args.forall(a => intLiteral(a).isDefined) || args.isEmpty || args.size > 2 then refuse("js-slice")
              if args.size == 1 then s"$objExpr.drop(${scalaArgs.head})" else s"$objExpr.slice(${scalaArgs.mkString(", ")})"
            // String methods: JS clamps, swaps or answers "" and NaN where Java's throw, and `charAt` yields a String, not a Char
            case m if writtenOutStringMethods(m) =>
              translateStringMethod(node, obj, objExpr, method, args, scalaArgs)
            // `substr` counts a negative start from the end and takes a LENGTH
            case "substr" =>
              refuse("js-string-index")
              s"$objExpr.substring(${scalaArgs.mkString(", ")})"
            case "startsWith" =>
              s"$objExpr.startsWith(${scalaArgs.mkString(", ")})"
            case "endsWith" =>
              s"$objExpr.endsWith(${scalaArgs.mkString(", ")})"
            case "replace" | "replaceAll" =>
              translateStringReplace(objExpr, method == "replaceAll", args)
            case "split" =>
              // JS splits on the literal text and keeps trailing empty pieces; Java's split reads a regex and drops them
              args match
                case List(sep) if sep.kind == "StringLiteral" =>
                  s"$objExpr.split(java.util.regex.Pattern.quote(${translateExpr(sep)}), -1)"
                case _ =>
                  refuse("split-pattern-unknown")
                  s"$objExpr.split(${scalaArgs.mkString(", ")})"
            case "trim" =>
              s"$objExpr.trim"
            case "toLowerCase" =>
              s"$objExpr.toLowerCase"
            case "toUpperCase" =>
              s"$objExpr.toUpperCase"
            case "toFixed" =>
              val precision = scalaArgs.headOption.getOrElse("0")
              // JS toFixed returns a String with locale-independent "." decimal
              // separator and half-away-from-zero rounding. java.lang.String.format
              // follows Locale.getDefault on the JVM (comma on comma-locale hosts).
              // BigDecimal HALF_UP matches JS rounding; toPlainString uses ".".
              // JS answers a non-finite value or one of 1e21 and more as its plain text, and keeps the sign of a negative that rounds to zero
              if !exprType(obj).exists(Set("Double", "Int", "Long", "Short", "Byte")) then refuse("number-to-string")
              val d = freshLabel("d")
              val r = freshLabel("r")
              s"{ val $d: Double = $objExpr; if ($d.isNaN || $d.isInfinite || math.abs($d) >= 1e21) ${jsNumberText(d)} else { " +
                s"val $r = new java.math.BigDecimal($d).setScale($precision, java.math.RoundingMode.HALF_UP); if ($d < 0 && $r.signum == 0) \"-\" + $r.toPlainString else $r.toPlainString } }"
            case "toString" =>
              // JS writes a number in its own form, and a radix only an integer's digits read the same in both
              exprType(obj) match
                case Some("Double") if scalaArgs.isEmpty                                                 => jsNumberText(objExpr)
                case Some("Int" | "Long" | "Short" | "Byte") if scalaArgs.nonEmpty                       => s"java.lang.Long.toString($objExpr.toLong, ${scalaArgs.head})"
                case Some("Int" | "Long" | "Short" | "Byte" | "String" | "Boolean") if scalaArgs.isEmpty => s"$objExpr.toString"
                case _                                                                                   =>
                  refuse("value-to-string")
                  s"$objExpr.toString"
            // `match` and `exec` answer a match array or null, and a global regex keeps its position between calls
            case "match" =>
              refuse("regex-method")
              s"${scalaArgs.head}.findPrefixMatchOf($objExpr).isDefined"
            case "test" =>
              s"${objExpr}.findFirstIn(${scalaArgs.mkString(", ")}).isDefined"
            case "exec" =>
              refuse("regex-method")
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
            // Object static methods: an object's own keys exist in Scala only on a map
            case "keys" | "values" | "entries" if objExpr == "Object" && !args.headOption.flatMap(exprType).exists(_.contains("Map")) =>
              refuse("object-reflection")
              s"${scalaArgs.head}.keys"
            case "keys" if objExpr == "Object" =>
              s"${scalaArgs.head}.keys"
            case "values" if objExpr == "Object" =>
              s"${scalaArgs.head}.values"
            case "entries" if objExpr == "Object" =>
              s"${scalaArgs.head}.iterator"
            case "assign" if objExpr == "Object" =>
              // Copies every own property of the sources onto the target: no shape-independent Scala spelling.
              refuse("js-object-assign")
              s"${scalaArgs.head}"
            case "create" | "defineProperty" if objExpr == "Object" =>
              refuse("object-reflection")
              s"/* Object.$method */ ${scalaArgs.mkString(", ")}"
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
            // a method the port moved into a helper
            case m if conventions.methodRewrites.contains(m) =>
              conventions.methodRewrites(m).replace("$receiver", objExpr)
            case "clone" =>
              s"$objExpr.clone()"
            // General method
            case _ =>
              // a JS built-in's static the cases above do not render has no Scala namesake; `Math`'s read through java.lang.Math only where they agree
              if obj.kind == "Identifier" && obj.text.exists(jsGlobals) && !calleeIndex.knownObjects.exists(obj.text.contains) &&
                !(obj.text.contains("Math") && javaMathAgrees(method))
              then refuse("js-builtin")
              // JS Math.max/min take any number of arguments, java.lang.Math's exactly two
              if obj.text.contains("Math") && (method == "max" || method == "min") && args.size != 2 then refuse("js-builtin")
              s"$objExpr.${snakeToCamel(method)}(${scalaArgs.mkString(", ")})"

        else
          val calleeExpr = translateExpr(callee)
          val scalaArgs  = args.map(translateExpr)
          s"$calleeExpr(${scalaArgs.mkString(", ")})"

      case "Identifier" =>
        val name = callee.text.getOrElse("???")
        // Translate global JS functions
        name match
          // JS parses a prefix and answers NaN where Scala's conversions throw: no faithful spelling
          case "parseInt" | "parseFloat" =>
            refuse("js-builtin")
            s"${args.headOption.map(translateExpr).getOrElse("")}.toDouble"
          // the global tests coerce their argument to a number first: only a number reads the same
          case "isNaN" | "isFinite" =>
            if !args.headOption.flatMap(exprType).exists(numericTypes.contains) then refuse("js-builtin")
            s"java.lang.Double.$name(${args.headOption.map(translateExpr).getOrElse("")})"
          case "String" if args.size == 1 =>
            stringified(args.head)
          case "Number" =>
            if !args.headOption.flatMap(exprType).exists(numericTypes.contains) then refuse("js-builtin")
            s"${args.headOption.map(translateExpr).getOrElse("")}.toDouble"
          case "Boolean" if args.size == 1 =>
            s"(${translateCondition(args.head)})"
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
          case n if conventions.nodeFactories(n) =>
            translateMakeNode(args)
          case _ =>
            val scalaName = translateIdentifier(name)
            val outer     = outerBinding(name)
            // Resolve the callee: try callee index for unqualified names; an enclosing function's binding shadows them all
            val resolvedCallee: Option[ReferenceSignatures.MemberRef] =
              if outer.isEmpty && !apiLookup.contains(name) && !entry.params.contains(name) &&
                !conventions.isClassName(name) && scalaName == snakeToCamel(name)
              then
                calleeIndex.resolveMember(scalaName) match
                  case Right(ref) => Some(ref)
                  // ambiguous elsewhere, but the body's own object declares it, as a module's own function shadows every other
                  case Left("callee-ambiguous") if enclosingOwner.flatMap(calleeIndex.declared(_, scalaName)).isDefined =>
                    enclosingOwner.flatMap(calleeIndex.declared(_, scalaName))
                  case Left("callee-ambiguous") =>
                    refuse("callee-ambiguous")
                    None
                  case Left(_) =>
                    refuse("wrong-function-ref")
                    None
              else None
            val qualifiedCallee = resolvedCallee.fold(scalaName)(qualify)
            // Look up the callee's signature for expected-type propagation
            val calleeSig = outer match
              case Some(held) => held.flatMap(m => enclosingClass.flatMap(oracle.get(_, m)))
              case None       => resolvedCallee.fold(lookupCallee(qualifiedCallee))(signatureOf)
            val named = optionsBag(calleeSig, args)
            calleeSig.filter(_ => named.isEmpty).foreach { s =>
              val isVararg = s.params.exists(p => p.tpe.contains("*") || p.tpe.startsWith("Seq["))
              if args.length > s.params.length && !isVararg then refuse("callee-arity-mismatch")
              if args.length < s.params.length && !isVararg && omittedArgs(s, args.length).isEmpty then refuse("callee-arity-mismatch")
              for (arg, i) <- args.zipWithIndex do
                s.params.lift(i).foreach { param =>
                  exprType(arg).foreach { argType =>
                    checkTypeCompatibility(argType, param.tpe)
                  }
                }
            }
            val scalaArgs = named.getOrElse(
              calleeSig match
                case Some(s) =>
                  args.zipWithIndex.map { case (arg, i) =>
                    s.params.lift(i).map(p => translateExprAt(arg, p.tpe)).getOrElse(translateExpr(arg))
                  } ++ omittedArgs(s, args.length).getOrElse(Nil)
                case None => args.map(translateExpr)
            )
            s"$qualifiedCallee(${scalaArgs.mkString(", ")})"

      case _ =>
        val calleeExpr = translateExpr(callee)
        val scalaArgs  = args.map(translateExpr)
        s"$calleeExpr(${scalaArgs.mkString(", ")})"

  private def translateBinaryExpr(node: RastNode): String =
    val children = node.children
    if children.size < 2 then return "??? /* bad binary */"

    node.operator match
      case Some("InstanceOfKeyword") =>
        // Special handling for `instanceof`
        val instChildren = children.filterNot(_.kind == "InstanceOfKeyword")
        if instChildren.size >= 2 then
          val lhs     = translateExpr(instChildren.head)
          val rhs     = instChildren(1)
          val rhsName = rhs.text.getOrElse(rhs.children.headOption.flatMap(_.text).getOrElse("Any"))
          if conventions.isClassName(rhsName) then
            // one of the library's own classes -- compile-time isInstanceOf
            s"$lhs.isInstanceOf[${memberIndex.testable(conventions.scalaClassName(rhsName))}]"
          else if rhsName.head.isUpper && !scalaKeywords.contains(rhsName) then
            // Capitalized non-keyword -- likely a class name; a generic one is tested with wildcards
            s"$lhs.isInstanceOf[${memberIndex.testable(rhsName)}]"
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

        // `x == null` on an optional value asks whether it is absent (JS `== null` also matches undefined)
        def isNullish(n: RastNode) = n.kind == "NullKeyword" || n.kind == "UndefinedKeyword" || (n.kind == "Identifier" && n.text.contains("undefined"))
        val equality               = Set("EqualsEqualsEqualsToken", "EqualsEqualsToken", "ExclamationEqualsEqualsToken", "ExclamationEqualsToken")
        if equality(op) && (isNullish(lhs) || isNullish(rhs)) then
          val value = if isNullish(lhs) then rhs else lhs
          if exprType(value).exists(isOptionLike) then
            val negated = op.startsWith("Exclamation")
            return s"${translateExpr(value)}.${if negated then "isDefined" else "isEmpty"}"

        val isEq = op == "EqualsEqualsEqualsToken" || op == "EqualsEqualsToken"
        val isNe = op == "ExclamationEqualsEqualsToken" || op == "ExclamationEqualsToken"
        // `x.<tag> === "Y"` compares the tag x's class declares, as JS does: a subclass carries its own tag, so no type test reads the same
        if isEq || isNe then
          tagComparison(lhs, rhs).orElse(tagComparison(rhs, lhs)).foreach { (recv, tag) =>
            if !tagReceiverType(recv).exists(t => conventions.typeTag.exists((_, member) => memberIndex.hasMember(t, member))) then refuse("tag-on-untyped-receiver")
            return s"${translateExpr(recv)}.${conventions.typeTag.get._2} ${if isEq then "==" else "!="} \"${escapeString(tag)}\""
          }

        // typeof X === "type" -> X.isInstanceOf[Type]
        if isEq && lhs.kind == "TypeOfExpression" then
          val typeofOperand = lhs.children.head
          // TS narrows a union by the test; Scala does not, so a later use of the narrowed operand would not typecheck
          if exprType(typeofOperand).exists(_.contains("|")) then refuse("typeof-narrowing")
          val operandExpr = translateExpr(typeofOperand)
          val typeStr     = rhs.value match
            case Some(RastValue.Str(s)) => s
            case _                      => "object"
          return typeStr match
            case "string"    => s"$operandExpr.isInstanceOf[String]"
            case "number"    => s"$operandExpr.isInstanceOf[Double]"
            case "boolean"   => s"$operandExpr.isInstanceOf[Boolean]"
            case "function"  => s"$operandExpr.isInstanceOf[Function[?, ?]]"
            case "undefined" => s"($operandExpr == null)"
            case "object"    => s"($operandExpr != null && !$operandExpr.isInstanceOf[Double] && !$operandExpr.isInstanceOf[String] && !$operandExpr.isInstanceOf[Boolean])"
            case _           => s"$operandExpr.isInstanceOf[$typeStr]"

        // typeof X !== "type" -> !X.isInstanceOf[Type]
        if isNe && lhs.kind == "TypeOfExpression" then
          val typeofOperand = lhs.children.head
          val operandExpr   = translateExpr(typeofOperand)
          val typeStr       = rhs.value match
            case Some(RastValue.Str(s)) => s
            case _                      => "object"
          return typeStr match
            case "string"    => s"!$operandExpr.isInstanceOf[String]"
            case "number"    => s"!$operandExpr.isInstanceOf[Double]"
            case "boolean"   => s"!$operandExpr.isInstanceOf[Boolean]"
            case "function"  => s"!$operandExpr.isInstanceOf[Function[?, ?]]"
            case "undefined" => s"($operandExpr != null)"
            case _           => s"!$operandExpr.isInstanceOf[$typeStr]"

        // JS `x && y` and `x || y` on a non-Boolean operand are truthiness
        // short-circuits, not boolean logic. The lowering depends on the
        // operand's declared Scala type and the context (condition vs value).
        if op == "AmpersandAmpersandToken" || op == "BarBarToken" then
          exprType(lhs) match
            case Some(tpe) if tpe != "Boolean" =>
              val left = translateExpr(lhs)
              val test = truthinessTest(tpe, left)
              // In a condition context (when the result is used as a boolean),
              // x && y is `test(x) && <y with x unwrapped>`.
              // In a value context, JS returns one operand: only valid when
              // both sides have the same declared Scala type.
              if isOptionLike(tpe) then
                val lhsName = lhs.text.map(snakeToCamel)
                // Translate the RHS with x unwrapped: inside the guarded
                // operand, `x` renders as `x.get` (one level)
                // operand; the right of `||` runs only where x is absent, so it is rendered once, at the inner type below
                def rightWithUnwrap = lhsName match
                  case Some(name) =>
                    val wasUnwrapped = unwrappedIds.contains(name)
                    val saved        = localTypes.get(name)
                    unwrappedIds += name
                    localTypes(name) = optionInner(tpe)
                    val r = translateExpr(rhs)
                    if !wasUnwrapped then unwrappedIds -= name
                    saved.fold(localTypes.remove(name))(t => localTypes.put(name, t))
                    r
                  case None => translateExpr(rhs)
                if op == "AmpersandAmpersandToken" then return s"($test && $rightWithUnwrap)"
                else
                  // x || y: if x is truthy, yield x (unwrapped); else y
                  val inner    = optionInner(tpe)
                  val fallback = (if inner == "Double" || inner == "Float" then fractionalLiteral(rhs, inner) else None).getOrElse(translateExprAt(rhs, inner))
                  return onceBound(left)(l => s"if (${truthinessTest(tpe, l)}) ${truthyValue(tpe, l)} else $fallback")
              else
                val rhsType = exprType(rhs)
                // `xs || []`: the fresh array takes the left operand's collection type, which is what JS yields either way
                val emptyAtSlot = op == "BarBarToken" && rhsType.isEmpty && rhs.kind == "ArrayLiteralExpression" && rhs.children.isEmpty && extractElementType(tpe).isDefined
                // `x || 0` on a fractional `x`: the literal is written at x's type, the number JS yields either way
                val fractionalFallback = op == "BarBarToken" && (tpe == "Double" || tpe == "Float") && fractionalLiteral(rhs, tpe).isDefined
                val right              =
                  if emptyAtSlot then translateExprAt(rhs, tpe) else if fractionalFallback then fractionalLiteral(rhs, tpe).get else translateExpr(rhs)
                if op == "AmpersandAmpersandToken" then
                  // Outside a condition JS yields one OPERAND, the falsy left one included: no Boolean says that.
                  refuse("truthiness-value-context")
                  return s"($test && $right)"
                else
                  // x || y in value context: both sides must agree in type, which an unknown right side does not show
                  if !rhsType.contains(tpe) && !emptyAtSlot && !fractionalFallback then refuse("truthiness-value-context")
                  return onceBound(left)(l => s"if (${truthinessTest(tpe, l)}) $l else $right")
            case None =>
              refuse("truthiness-unknown-type")
            case _ => () // Boolean: emit normal && / ||

        // JS `/` is always floating-point division. When the reference declares
        // an integral return type the Scala `/` on Int operands truncates, which
        // JS does not. Refuse so the consumer can decide.
        if op == "SlashToken" then
          declaredReturnType.foreach { rt =>
            val base = rt.takeWhile(c => c != '[' && c != ' ')
            if Set("Int", "Long", "Short", "Byte").contains(base) then refuse("int-division-on-number")
          }

        // JS + on string and non-string is string concatenation. Scala 2.13
        // deprecated numeric + String. Use .toString to keep the concat safe.
        if op == "PlusToken" then
          val lhsStr = lhs.kind == "StringLiteral" || lhs.kind == "FirstTemplateToken" || lhs.kind == "NoSubstitutionTemplateLiteral" || lhs.kind == "TemplateExpression"
          val rhsStr = rhs.kind == "StringLiteral" || rhs.kind == "FirstTemplateToken" || rhs.kind == "NoSubstitutionTemplateLiteral" || rhs.kind == "TemplateExpression"
          if !lhsStr && rhsStr then return s"${stringified(lhs)} + ${translateExpr(rhs)}"
          if lhsStr && !rhsStr then return s"${translateExpr(lhs)} + ${stringified(rhs)}"

        val isAssignment = op.endsWith("EqualsToken") && !Set(
          "EqualsEqualsToken",
          "EqualsEqualsEqualsToken",
          "ExclamationEqualsToken",
          "ExclamationEqualsEqualsToken",
          "LessThanEqualsToken",
          "GreaterThanEqualsToken"
        )(op) ||
          op == "FirstAssignment" || op == "FirstCompoundAssignment"
        // JS assignment is an expression yielding the stored value; Scala's yields Unit
        if isAssignment && (statementRoot ne node) then refuse("assignment-as-value")
        // storing into a field the receiver's declared type holds as a `val` (a case-class field, say)
        if isAssignment && lhs.kind == "PropertyAccessExpression" && lhs.children.size >= 2 then
          val field = lhs.children.last.text.getOrElse("")
          val owner =
            if lhs.children.head.kind == "ThisKeyword" then Some(nodeScalaType)
            else exprType(lhs.children.head).map(_.takeWhile(c => c != '[' && c != ' '))
          owner.filter(memberIndex.knowsType).foreach { t =>
            if !List(field, snakeToCamel(field)).exists(memberIndex.isMutable(t, _)) then refuse("assign-to-immutable-field")
          }
        // storing into a tuple record's position stores into its field
        if isAssignment && lhs.kind == "ElementAccessExpression" && lhs.children.size >= 2 then
          tupleField(lhs.children.head, lhs.children(1)).foreach((cls, field) => if !memberIndex.isMutable(cls, field) then refuse("assign-to-immutable-field"))
        // the stored value takes the target's declared type, an optional target included
        val lhsType = if op == "EqualsToken" || op == "FirstAssignment" then exprType(lhs) else None
        for lt <- lhsType; have <- exprType(rhs) do checkTypeCompatibility(have, lt)
        val left   = translateExpr(lhs)
        val proven = if op == "AmpersandAmpersandToken" then narrowedBy(lhs) else if op == "BarBarToken" then refutedBy(lhs) else Nil
        val right  = underNarrowing(proven)(lhsType.fold(translateExpr(rhs))(translateExprAt(rhs, _)))
        if op == "AsteriskAsteriskToken" then return s"math.pow($left, $right)"
        // JS `/` never truncates: two integral operands divide as doubles
        if op == "SlashToken" && exprType(lhs).exists(integralTypes.contains) && exprType(rhs).exists(integralTypes.contains) then return s"($left).toDouble / $right"
        if op == "AsteriskAsteriskEqualsToken" then refuse("exponent-assignment")
        // JS compares two objects by identity; Scala's `==` calls `equals`, which a case class or a collection answers structurally.
        // Parenthesised: a letter operator binds looser than `&&`.
        if equality(op) && List(lhs, rhs).forall(n => exprType(n).exists(comparesByIdentity)) then return s"($left ${if op.startsWith("Exclamation") then "ne" else "eq"} $right)"
        val scalaOp = op match
          case "EqualsEqualsEqualsToken"                     => "=="
          case "ExclamationEqualsEqualsToken"                => "!="
          case "EqualsEqualsToken"                           => "=="
          case "ExclamationEqualsToken"                      => "!="
          case "AmpersandAmpersandToken"                     => "&&"
          case "BarBarToken"                                 => "||"
          case "PlusToken"                                   => "+"
          case "MinusToken"                                  => "-"
          case "AsteriskToken"                               => "*"
          case "SlashToken"                                  => "/"
          case "PercentToken"                                => "%"
          case "LessThanToken" | "FirstBinaryOperator"       => "<"
          case "LessThanEqualsToken"                         => "<="
          case "GreaterThanToken"                            => ">"
          case "GreaterThanEqualsToken"                      => ">="
          case "AmpersandToken"                              => "&"
          case "BarToken"                                    => "|"
          case "CaretToken"                                  => "^"
          case "LessThanLessThanToken"                       => "<<"
          case "GreaterThanGreaterThanToken"                 => ">>"
          case "GreaterThanGreaterThanGreaterThanToken"      => ">>>"
          case "EqualsToken" | "FirstAssignment"             => "="
          case "PlusEqualsToken" | "FirstCompoundAssignment" => "+="
          case "MinusEqualsToken"                            => "-="
          case "AsteriskEqualsToken"                         => "*="
          case "SlashEqualsToken"                            => "/="
          case "BarEqualsToken"                              => "|="
          case "AmpersandEqualsToken"                        => "&="
          case "PercentEqualsToken"                          => "%="
          case "AsteriskAsteriskEqualsToken"                 => "**="
          case "QuestionQuestionToken"                       =>
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
        val left  = translateExpr(children.head)
        val right = translateExpr(children.last)
        s"$left /* op? */ $right"

  /** A JS function literal as a Scala lambda. Its parameters are locals of the types `paramTypes` gives by position (unknown where it gives none), shadowing any outer name; `discardsResult` says the
    * caller ignores what the function returns, as an array's `forEach` does.
    */
  private def translateFunctionExpr(node: RastNode, paramTypes: List[Option[String]] = Nil, discardsResult: Boolean = false): String =
    val params    = node.children.filter(_.kind == "Parameter")
    val body      = node.children.find(_.kind == "Block")
    val paramStrs = params.map { p =>
      val name = p.children.find(_.kind == "Identifier").flatMap(_.text).getOrElse("_")
      snakeToCamel(name)
    }
    // the value of an expression the function evaluates for its effect: nothing reads it where the caller discards the result
    def effect(e: RastNode): String = if discardsResult then withStatementRoot(unparenthesized(e))(translateExpr(e)) else translateExpr(e)

    withLambdaParams(paramStrs, paramTypes) {
      body match
        case Some(block) =>
          if block.children.size == 1 && block.children.head.kind == "ReturnStatement" then
            val ret  = block.children.head
            val expr = ret.children.headOption.map(translateExpr).getOrElse("()")
            if paramStrs.isEmpty then s"(() => $expr)"
            else s"((${paramStrs.mkString(", ")}) => $expr)"
          else if block.children.size == 1 && block.children.head.kind == "ExpressionStatement" then
            // a block with no `return` answers `undefined`, which only a caller discarding the result never sees
            if !discardsResult then refuse("function-returns-undefined")
            val expr = block.children.head.children.headOption.map(effect).getOrElse("()")
            if paramStrs.isEmpty then s"(() => $expr)"
            else s"((${paramStrs.mkString(", ")}) => $expr)"
          else translateFunctionBlock(block, paramStrs, paramTypes, discardsResult)
        case None =>
          // Arrow without block: single expression body
          val exprBody = node.children.find(c => c.kind != "Parameter")
          val expr     = exprBody.map(effect).getOrElse("???")
          if paramStrs.isEmpty then s"(() => $expr)"
          else s"((${paramStrs.mkString(", ")}) => $expr)"
    }

  /** Renders `render` with lambda parameters bound as this body's locals, each of its type where one is given and of no known type otherwise, restoring whatever they shadowed. */
  private def withLambdaParams[A](names: List[String], types: List[Option[String]])(render: => A): A =
    val saved = names.map(n => (n, localNames.contains(n), localTypes.get(n), opaqueLocals.contains(n), narrowedIds.get(n), unwrappedIds.contains(n)))
    names.zipWithIndex.foreach { (n, i) =>
      localNames += n
      narrowedIds.remove(n)
      unwrappedIds -= n
      types.lift(i).flatten match
        case Some(t) =>
          localTypes(n) = t
          opaqueLocals -= n
        case None =>
          localTypes.remove(n)
          opaqueLocals += n
    }
    try render
    finally
      saved.reverse.foreach { (n, wasLocal, tpe, wasOpaque, narrowed, unwrapped) =>
        if wasLocal then localNames += n else localNames -= n
        tpe.fold(localTypes.remove(n))(localTypes.put(n, _))
        if wasOpaque then opaqueLocals += n else opaqueLocals -= n
        narrowed.fold(narrowedIds.remove(n))(narrowedIds.put(n, _))
        if unwrapped then unwrappedIds += n else unwrappedIds -= n
      }

  /** A multi-statement function body, in a context of its own for the lambda's scope that knows everything this body's does: the reference's declarations, the enclosing class, and the locals in scope
    * where the function is written with their types. Only the result type and the guards' narrowings stay behind, since the function may run where no guard holds.
    */
  private def translateFunctionBlock(block: RastNode, paramStrs: List[String], lambdaTypes: List[Option[String]], discardsResult: Boolean): String =
    // a body that can run off its end answers `undefined` there, which only a caller discarding the result never sees
    if !discardsResult && !block.children.lastOption.exists(exitsAlways) then refuse("function-returns-undefined")
    val innerIndent = baseIndent + "    "
    val innerCtx    = new BodyContext(
      entry,
      hierarchy,
      innerIndent,
      thisBinding,
      nodeParamName,
      apiLookup,
      None,
      paramTypes,
      calleeIndex,
      memberIndex,
      ctorSchema,
      enumIndex,
      memberRenames,
      oracle,
      enclosingOwner,
      conventions,
      tsTypes,
      enclosingClass,
      fileNames
    )
    // the nested function renders inside this body: it reads this body's parameter copies and locals, and declares none of its own
    innerCtx.topLevel = false
    innerCtx.labelSeq = labelSeq
    innerCtx.paramShadows ++= paramShadows
    innerCtx.paramRenames ++= paramRenames
    innerCtx.localNames ++= localNames
    innerCtx.localTypes ++= localTypes
    innerCtx.opaqueLocals ++= opaqueLocals
    // its own parameters shadow every outer name, each of the type the caller gives or of none
    paramStrs.zipWithIndex.foreach { (n, i) =>
      innerCtx.localNames += n
      lambdaTypes.lift(i).flatten match
        case Some(t) =>
          innerCtx.localTypes(n) = t
          innerCtx.opaqueLocals -= n
        case None =>
          innerCtx.localTypes.remove(n)
          innerCtx.opaqueLocals += n
    }
    innerCtx.translateBlock(block)
    labelSeq = innerCtx.labelSeq
    refusals ++= innerCtx.refusals
    refusalSites ++= innerCtx.refusalSites
    val bodyStr = innerCtx.result().stripTrailing()
    if paramStrs.isEmpty then s"(() => {\n$bodyStr\n$baseIndent  })"
    else s"((${paramStrs.mkString(", ")}) => {\n$bodyStr\n$baseIndent  })"

  /** Translate a lambda whose result is used as a Boolean predicate (filter, find, some/exists, every/forall, findIndex). The body expression goes through truthiness lowering: a String becomes
    * `.nonEmpty`, a Nullable becomes `.isDefined`, unknown type refuses.
    */
  private def translatePredicateLambda(node: RastNode, elemType: Option[String]): String =
    node.kind match
      case "ArrowFunction" | "FunctionExpression" =>
        val params    = node.children.filter(_.kind == "Parameter")
        val paramStrs = params.map { p =>
          val name = p.children.find(_.kind == "Identifier").flatMap(_.text).getOrElse("_")
          snakeToCamel(name)
        }
        // JS passes the index and the array too; a Scala predicate takes the element alone
        if params.size > 1 then refuse("callback-index-parameter")
        val body     = node.children.find(_.kind == "Block")
        val exprNode = body match
          case Some(block) =>
            if block.children.size == 1 && block.children.head.kind == "ReturnStatement" then block.children.head.children.headOption
            else None // anything else: the plain lambda, which refuses a body that can answer `undefined`
          case None => node.children.find(c => c.kind != "Parameter")
        exprNode match
          case Some(expr) =>
            // the element is the parameter's type, so that its truthiness test is known
            withLambdaParams(paramStrs, List(elemType)) {
              // In predicate context, an expression whose type is unknown and
              // is not already Boolean must refuse
              val knownType = exprType(expr)
              if knownType.isEmpty && expr.kind == "Identifier" then refuse("truthiness-unknown-type")
              val translated = translateCondition(expr)
              if paramStrs.isEmpty then s"(() => $translated)"
              else s"((${paramStrs.mkString(", ")}) => $translated)"
            }
          case None => translateFunctionExpr(node, List(elemType))
      case _ =>
        // Not a lambda literal: translate as-is
        translateExpr(node)

  /** Emit a while or do-while loop with named boundary wrapping for break/continue. */
  private def emitLoop(indent: String, cond: String, bodyNode: RastNode, isDoWhile: Boolean): Unit =
    val hasBreak      = containsBreak(bodyNode)
    val hasContinue   = containsContinue(bodyNode)
    val savedBreak    = breakLabel
    val savedContinue = continueLabel
    if hasBreak then
      val brkLbl = freshLabel("brk")
      breakLabel = brkLbl
      sb.append(s"${indent}scala.util.boundary { ($brkLbl: scala.util.boundary.Label[scala.Unit]) ?=>\n")
    val loopIndent = if hasBreak then indent + "  " else indent
    if hasContinue then
      val cntLbl = freshLabel("cnt")
      continueLabel = cntLbl
    if isDoWhile then
      if hasContinue then
        sb.append(s"${loopIndent}var $$doFirst = true\n")
        sb.append(
          s"${loopIndent}while ($$doFirst || $cond) scala.util.boundary { ($continueLabel: scala.util.boundary.Label[scala.Unit]) ?=>\n"
        )
        sb.append(s"$loopIndent  $$doFirst = false\n")
        translateStatementBody(bodyNode, loopIndent + "  ", false)
        sb.append(s"$loopIndent}\n")
      else
        sb.append(s"${loopIndent}var $$doFirst = true\n")
        sb.append(s"${loopIndent}while ($$doFirst || $cond) {\n")
        sb.append(s"$loopIndent  $$doFirst = false\n")
        translateStatementBody(bodyNode, loopIndent + "  ", false)
        sb.append(s"$loopIndent}\n")
    else if hasContinue then
      sb.append(s"${loopIndent}while ($cond) scala.util.boundary { ($continueLabel: scala.util.boundary.Label[scala.Unit]) ?=>\n")
      translateStatementBody(bodyNode, loopIndent + "  ", false)
      sb.append(s"$loopIndent}\n")
    else
      sb.append(s"${loopIndent}while ($cond) {\n")
      translateStatementBody(bodyNode, loopIndent + "  ", false)
      sb.append(s"$loopIndent}\n")
    if hasBreak then sb.append(s"$indent}\n")
    breakLabel = savedBreak
    continueLabel = savedContinue

  private def translateForStatement(node: RastNode, indent: String): Unit =
    // ForStatement children: [init?, condition?, update?, body]
    // After R1 token filtering, children are semantic only
    val children = node.children
    // Find components by kind
    val init = children.find(c =>
      c.kind == "VariableDeclarationList" || c.kind == "FirstStatement" ||
        (c.kind == "BinaryExpression" && c.operator.contains("EqualsToken"))
    )
    val body = children.find(_.kind == "Block").orElse(children.lastOption)
    // Condition and update are harder — they're positioned between init and body
    val nonInitBody = children.filterNot(c => c == init.orNull || c == body.orNull)
    val cond        = nonInitBody.headOption
    val update      = nonInitBody.lift(1)

    // Emit as: { init; while (cond) { body; update } }
    val bodyNode      = body.getOrElse(RastNode("Block", 0, (0, 0)))
    val hasBreak      = containsBreak(bodyNode)
    val hasContinue   = containsContinue(bodyNode)
    val savedBreak    = breakLabel
    val savedContinue = continueLabel

    sb.append(s"$indent{\n")
    init.foreach { i =>
      if i.kind == "VariableDeclarationList" then
        val decls = i.children.filter(_.kind == "VariableDeclaration")
        for d <- decls do
          val name     = d.children.find(_.kind == "Identifier").flatMap(_.text).getOrElse("_i")
          val initNode = d.children.drop(1).find(c => !isTypeNode(c))
          // JS leaves it `undefined`, which no Scala number says
          if initNode.isEmpty then refuse("uninitialised-local")
          val initExpr = initNode.map(translateExpr).getOrElse("0")
          // a counter starting from an integer that a step makes fractional is a double, as every JS number is
          val widened = if initNode.exists(n => n.kind == "NumericLiteral" && exprType(n).contains("Int")) then widenedNumber(name) else None
          localNames += snakeToCamel(name)
          widened.orElse(initNode.flatMap(exprType)).foreach(t => localTypes(snakeToCamel(name)) = t)
          sb.append(s"$indent  var ${snakeToCamel(name)}${widened.fold("")(t => s": $t")} = $initExpr\n")
      else sb.append(s"$indent  ${withStatementRoot(unparenthesized(i))(translateExpr(i))}\n")
    }
    val condStr = cond.map(translateCondition).getOrElse("true")
    def updateText(u: RastNode): String = withStatementRoot(unparenthesized(u))(translateExpr(u))
    // For a C-style for loop, continue skips the rest of the body but update still runs.
    // The continue boundary wraps only the body; the update is emitted after it.
    val loopIndent = indent + "  "
    if hasBreak then
      val brkLbl = freshLabel("brk")
      breakLabel = brkLbl
      sb.append(s"${loopIndent}scala.util.boundary { ($brkLbl: scala.util.boundary.Label[scala.Unit]) ?=>\n")
    val whileIndent = if hasBreak then loopIndent + "  " else loopIndent
    if hasContinue then
      val cntLbl = freshLabel("cnt")
      continueLabel = cntLbl
      sb.append(s"${whileIndent}while ($condStr) {\n")
      sb.append(s"$whileIndent  scala.util.boundary { ($cntLbl: scala.util.boundary.Label[scala.Unit]) ?=>\n")
      translateStatementBody(bodyNode, whileIndent + "    ", false)
      sb.append(s"$whileIndent  }\n")
      update.foreach(u => sb.append(s"$whileIndent  ${updateText(u)}\n"))
      sb.append(s"$whileIndent}\n")
    else
      sb.append(s"${whileIndent}while ($condStr) {\n")
      translateStatementBody(bodyNode, whileIndent + "  ", false)
      update.foreach(u => sb.append(s"$whileIndent  ${updateText(u)}\n"))
      sb.append(s"$whileIndent}\n")
    if hasBreak then sb.append(s"$loopIndent}\n")
    breakLabel = savedBreak
    continueLabel = savedContinue
    sb.append(s"$indent}\n")

  private def translateForInOfStatement(node: RastNode, indent: String): Unit =
    val children = node.children
    if children.size >= 2 then
      val varDecl  = children.head
      val iterable = children(1)
      // `for (k in o)` walks an object's KEYS: only a map has keys to walk in Scala
      if node.kind == "ForInStatement" && !exprType(iterable).exists(_.contains("Map")) then refuse("for-in-over-object")
      val elemType = if node.kind == "ForOfStatement" then exprType(iterable).flatMap(extractElementType) else None
      // `for (const {a, b} of xs)` and `for (const [x, y] of points)` read fields off each element, by name or by a tuple record's position
      val fieldPattern = varDecl.children.headOption.flatMap(
        _.children.find { p =>
          p.kind == "ObjectBindingPattern" || (p.kind == "ArrayBindingPattern" && elemType.exists(t => conventions.tupleRecords.contains(baseName(t))))
        }
      )
      if node.kind == "ForInStatement" && fieldPattern.isDefined then refuse("for-in-over-object")
      // Detect array destructuring: `for (const [k, v] of map)`
      val arrayBinding = varDecl.children.headOption.flatMap(_.children.find(_.kind == "ArrayBindingPattern")).filter(_ => fieldPattern.isEmpty)
      val elemVar      = fieldPattern.map(_ => freshLabel("e"))
      val varPattern   = arrayBinding match
        case _ if elemVar.isDefined => elemVar.get
        case Some(abp)              =>
          // a hole, a default, a rest or a nested pattern is no tuple pattern's
          if !abp.children.forall(e => e.kind == "BindingElement" && e.flags.isEmpty && e.children.size == 1 && e.children.head.kind == "Identifier") then refuse("destructuring-pattern")
          val elems = abp.children.filter(_.kind == "BindingElement").flatMap(_.children.find(_.kind == "Identifier").flatMap(_.text)).map(snakeToCamel)
          if elems.size == 2 then s"(${elems.head}, ${elems(1)})"
          else s"(${elems.mkString(", ")})"
        case None =>
          val varName = varDecl.children.headOption.flatMap(c => c.children.headOption.flatMap(_.text).orElse(c.text)).getOrElse("_item")
          localNames += snakeToCamel(varName)
          if node.kind == "ForOfStatement" then exprType(iterable).flatMap(extractElementType).foreach(t => localTypes(snakeToCamel(varName)) = t)
          snakeToCamel(varName)
      // `for (k in m)` binds the map's keys; iterating the map itself would bind its entries
      val iterExpr      = if node.kind == "ForInStatement" then s"${translateExpr(iterable)}.keys" else translateExpr(iterable)
      val bodyNode      = children.find(_.kind == "Block").orElse(children.lift(2)).getOrElse(RastNode("Block", 0, (0, 0)))
      val hasBreak      = containsBreak(bodyNode)
      val hasContinue   = containsContinue(bodyNode)
      val savedBreak    = breakLabel
      val savedContinue = continueLabel
      if hasBreak then
        val brkLbl = freshLabel("brk")
        breakLabel = brkLbl
        sb.append(s"${indent}scala.util.boundary { ($brkLbl: scala.util.boundary.Label[scala.Unit]) ?=>\n")
      val forIndent = if hasBreak then indent + "  " else indent
      if hasContinue then
        val cntLbl = freshLabel("cnt")
        continueLabel = cntLbl
        sb.append(
          s"${forIndent}for ($varPattern <- $iterExpr) scala.util.boundary { ($cntLbl: scala.util.boundary.Label[scala.Unit]) ?=>\n"
        )
      else sb.append(s"${forIndent}for ($varPattern <- $iterExpr) {\n")
      fieldPattern.foreach { p =>
        val keyword = if bindingsDeclaredBy(p).exists(reassignedNames.contains) then "var" else "val"
        destructureFrom(p, elemVar.get, elemType, keyword, forIndent + "  ")
      }
      translateStatementBody(bodyNode, forIndent + "  ", false)
      sb.append(s"$forIndent}\n")
      if hasBreak then sb.append(s"$indent}\n")
      breakLabel = savedBreak
      continueLabel = savedContinue
    else
      refuse("MalformedForInOf")
      sb.append(s"$indent??? /* for-in/of */\n")
