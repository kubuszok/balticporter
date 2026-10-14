package balticporter.frontend.ts.dedicated

import balticporter.frontend.ts.{RastFile, RastNode, RastType, RastValue, ReferenceSignatures}
import scala.collection.mutable
import BodySyntax.*

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
      refusalSites: List[RefusalSite] = Nil,
  )

  /** Where in the exported source one refusal arose: the line and column of the innermost node being rendered. */
  final case class RefusalSite(reason: String, line: Int, column: Int):
    /** `reason path:line:column`, the form a report prints. */
    def render(path: String): String = s"$reason $path:$line:$column"

  /** Placeholder emitted in the boundary Label type when the return type is not known at
    * translation time. `ParityDerive` replaces it with the method's declared return type
    * before emitting the body into the reference skeleton. */
  val ReturnTypePlaceholder = "$$BP_RET$$"

  /** A prototype assignment extracted from a RAST file.
    *
    * The JS pattern is:
    * {{{
    * AST_X.prototype.method_name = function(params) { body };
    * AST_X.prototype.method_name = identifier_ref;
    * }}}
    */
  def extractPrototypeAssignments(file: RastFile): List[DefmethodEntry] =
    val result = mutable.ListBuffer.empty[DefmethodEntry]
    for node <- file.nodes do
      extractProtoAssignment(node).foreach(result += _)
    result.toList

  /** Translate a DEFMETHOD/prototype body from its RAST Block node to Scala.
    * @param thisBinding if set, `this.x` becomes `$thisBinding.x` (e.g., "n" in pattern-match arms)
    * @param nodeParamName when set, property accesses on this identifier are validated
    *   against the DEFNODE hierarchy for the entry's className */
  /** @param apiLookup JS identifier to Scala equivalent, supplied by the consumer's builder (the engine ships no library-specific table). */
  /** @param returnType the method's declared Scala return type, used for the boundary Label when
    *   the body has early returns. When absent, the body carries `ReturnTypePlaceholder` and
    *   `ParityDerive` fills it from the reference skeleton. */
  /** @param paramTypes maps each parameter name to its declared Scala type from the reference
    *   signature. Used to detect JS idioms that translate differently when the Scala type is
    *   `Nullable` (truthiness operators) or a typed class (object literal construction). */
  /** @param calleeIndex resolves simple function names to their enclosing object from the
    *   reference tree. When absent, unresolved callees are refused with `wrong-function-ref`. */
  /** @param memberIndex validates property access on typed parameters against the reference
    *   tree's declared members. When absent, access on specific class types is refused. */
  /** @param ctorSchema constructor parameter lists from the reference tree, used to construct
    *   typed objects from JS object literals instead of emitting `mutable.Map`. */
  /** @param enclosingOwner the object path the body is emitted into, when the caller knows it; a
    *   private member of any other object is refused, and so is every private member when absent. */
  /** @param tsTypes the checker's types of the body's file (`RastFile.types`), read where the
    *   reference says nothing about a value; empty reads none. */
  def translateBody(
      entry: DefmethodEntry,
      hierarchy: List[DefnodeClass],
      indent: String = "    ",
      thisBinding: String = "this",
      nodeParamName: Option[String] = None,
      apiLookup: Map[String, String] = Map.empty,
      returnType: Option[String] = None,
      paramTypes: Map[String, String] = Map.empty,
      calleeIndex: ReferenceSignatures.CalleeIndex = ReferenceSignatures.CalleeIndex.empty,
      memberIndex: ReferenceSignatures.MemberIndex = ReferenceSignatures.MemberIndex.empty,
      ctorSchema: ReferenceSignatures.ConstructorSchema = ReferenceSignatures.ConstructorSchema.empty,
      enumIndex: ReferenceSignatures.EnumIndex = ReferenceSignatures.EnumIndex.empty,
      memberRenames: Map[String, String] = Map.empty,
      oracle: ReferenceSignatures.TypeOracle = ReferenceSignatures.TypeOracle.empty,
      enclosingOwner: Option[String] = None,
      conventions: JsConventions = JsConventions.plain,
      tsTypes: Map[String, RastType] = Map.empty,
  ): TranslationResult =
    val ctx = new BodyContext(entry, hierarchy, indent, thisBinding, nodeParamName, apiLookup, returnType, paramTypes, calleeIndex, memberIndex, ctorSchema, enumIndex, memberRenames, oracle, enclosingOwner, conventions, tsTypes)
    ctx.translateBlock(entry.bodyNode)
    ctx.checkUnresolvedReferences()
    TranslationResult(
      scalaBody = ctx.result(),
      isComplete = ctx.refusals.isEmpty,
      refusalCount = ctx.refusals.size,
      refusalReasons = ctx.refusals.toList,
      refusalSites = ctx.refusalSites.toList,
    )

  /** Emit a complete DEFMETHOD family as Scala method declarations.
    *
    * Groups entries by class and emits each as an override chain. */
  def emitDefmethodFamily(
      familyName: String,
      entries: List[DefmethodEntry],
      hierarchy: List[DefnodeClass],
      conventions: JsConventions = JsConventions.plain,
  ): String =
    val sb = new StringBuilder
    val grouped = entries.groupBy(_.className)
    val sortedClasses = grouped.keys.toList.sorted(using Ordering.String)

    for cls <- sortedClasses do
      val methods = grouped(cls)
      val scalaClass = conventions.scalaClassName(cls)
      sb.append(s"// $scalaClass.$familyName\n")
      for m <- methods do
        val result = translateBody(m, hierarchy, conventions = conventions)
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
      entries: List[DefmethodEntry],
      hierarchy: List[DefnodeClass],
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

  private def extractProtoAssignment(node: RastNode): Option[DefmethodEntry] =
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
  ): Option[DefmethodEntry] =
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
          Some(DefmethodEntry(className, methodName, params, body))

        case "Identifier" =>
          // `X.prototype.m = f` makes `m` return what the named function would; a library's own
          // helper names (`return_false`) are read through the caller's `apiLookup`.
          val refName = rhs.text.getOrElse("")
          val syntheticBody =
            RastNode("Block", 0, (0, 0), children = List(
              RastNode("ReturnStatement", 0, (0, 0), children = List(
                RastNode("Identifier", 0, (0, 0), text = Some(refName))
              ))
            ))
          Some(DefmethodEntry(className, methodName, Nil, syntheticBody))

        case _ => None


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
