package balticporter.frontend.ts.dedicated

import balticporter.frontend.ts.{ RastNode, RastType, RastValue, ReferenceDeclarations, ReferenceSignatures }
import scala.collection.mutable
import BodySyntax.*

/** What one body's translation knows and records while it renders: the reference's declarations, the body's own bindings and their types, the labels and the refusals. */
abstract private[dedicated] class BodyScope(
  val entry:              DefmethodEntry,
  val hierarchy:          List[DefnodeClass],
  val baseIndent:         String,
  val thisBinding:        String,
  val nodeParamName:      Option[String],
  val apiLookup:          Map[String, String],
  val declaredReturnType: Option[String],
  val paramTypes:         Map[String, String],
  val calleeIndex:        ReferenceSignatures.CalleeIndex,
  val memberIndex:        ReferenceSignatures.MemberIndex,
  val ctorSchema:         ReferenceSignatures.ConstructorSchema,
  val enumIndex:          ReferenceSignatures.EnumIndex,
  val memberRenames:      Map[String, String],
  val oracle:             ReferenceSignatures.TypeOracle,
  val enclosingOwner:     Option[String],
  val conventions:        JsConventions,
  val tsTypes:            Map[String, RastType],
  val enclosingClass:     Option[String],
  val fileNames:          ReferenceDeclarations.FileNames
):
  // the type names the target file writes itself
  protected def nameableTypes: Set[String] = fileNames.types

  /** How the target file writes the class `cls`: by its simple name where it writes or imports that name, or `cls` is a top-level type of a package the file is in or imports whole; else qualified by
    * the ONE package declaring it; none where no package or two do.
    */
  protected def spelled(cls: String): Option[String] =
    val pkgs = memberIndex.packages.getOrElse(cls, Set.empty)
    if nameableTypes(cls) || pkgs.exists(fileNames.scopes) then Some(cls)
    else if pkgs.size == 1 then Some(s"${pkgs.head}.$cls")
    else None

  val sb                 = new StringBuilder
  val refusals           = mutable.ListBuffer.empty[String]
  val refusalSites       = mutable.ListBuffer.empty[DefmethodBodyTranslator.RefusalSite]
  protected var labelSeq = 0
  // The label name for the current early-return boundary (empty = no boundary)
  protected var returnLabel = ""
  // The label name for the current loop's break boundary
  protected var breakLabel = ""
  // The label name for the current loop's continue boundary
  protected var continueLabel = ""

  protected def freshLabel(prefix: String): String =
    labelSeq += 1
    s"$prefix$$$labelSeq"

  // Build lookup of class properties for `this.x` resolution
  protected val byName: Map[String, DefnodeClass] =
    hierarchy.map(c => c.varName -> c).toMap
  protected val allPropsForClass: Map[String, Set[String]] =
    hierarchy.map { cls =>
      cls.varName -> collectAllProps(cls)
    }.toMap

  // Properties available on `this` or `node` based on the DEFMETHOD className
  protected val nodeProps: Set[String] =
    if conventions.isClassName(entry.className) then allPropsForClass.getOrElse(entry.className, Set.empty)
    else Set.empty

  // The Scala class `this` is: the library's own class the entry names, a class the reference
  // declares under the entry's name, else the library's root class (empty when it has none).
  protected val nodeScalaType: String =
    if conventions.isClassName(entry.className) then conventions.scalaClassName(entry.className)
    else if memberIndex.knowsType(entry.className) then entry.className
    else conventions.rootClass.getOrElse("")

  // Names that refer to the current node (this, and the first param in
  // DEFMETHOD context, and any explicitly-named node param)
  protected val nodeNames: Set[String] =
    val base      = Set(thisBinding)
    val withParam = nodeParamName.map(base + _).getOrElse(base)
    withParam

  // Names that are declared const but later assigned in the body: emit var
  protected val reassignedNames: Set[String] = collectReassignedNames(entry.bodyNode)

  /** Every name an assignment, compound assignment or increment writes anywhere in the body, nested functions included: a local no write reaches is a `val`, whichever JS keyword declared it. */
  protected lazy val assignedAnywhere: Set[String] =
    val out = mutable.Set.empty[String]
    writeTargets.forEach(n => if n.kind == "Identifier" then n.text.foreach(out += _))
    out.toSet

  // Parameter names that are reassigned in the body: refuse upfront
  protected val reassignedParams: Set[String] = entry.params.filter(reassignedNames.contains).toSet

  // the local `var` standing for each parameter the body writes, by JS name; a nested function's context reads the same map
  val paramShadows = mutable.Map.empty[String, String]

  // the reference's name for each JS parameter it renamed, by JS name; a nested function's context reads the same map
  val paramRenames = mutable.Map.empty[String, String]

  /** The name a JS parameter has in the reference: its renamed spelling, else its own. */
  protected def referenceParam(p: String): String = paramRenames.getOrElse(p, snakeToCamel(p))

  // false for the context of a nested function, which renders the body of its enclosing one
  var topLevel = true

  /** True when a function nested in the body writes the name. */
  protected def writtenInNestedFunction(name: String): Boolean =
    def walk(n: RastNode, nested: Boolean): Boolean =
      val inside = nested || Set("ArrowFunction", "FunctionExpression", "FunctionDeclaration", "MethodDeclaration")(n.kind)
      val writes = n.kind == "BinaryExpression" && n.operator.exists(op => (op.endsWith("EqualsToken") && !comparisonOps(op)) || op == "FirstAssignment" || op == "FirstCompoundAssignment") ||
        ((n.kind == "PostfixUnaryExpression" || n.kind == "PrefixUnaryExpression") && n.operator.exists(op => op == "PlusPlusToken" || op == "MinusMinusToken"))
      (inside && writes && n.children.headOption.exists(c => c.kind == "Identifier" && c.text.contains(name))) || n.children.exists(walk(_, inside))
    walk(entry.bodyNode, false)

  // Local variable types, inferred from initialisers whose type is known
  protected val localTypes = mutable.Map.empty[String, String]
  // All locally declared variable names (even when the type is unknown)
  protected val localNames = mutable.Set.empty[String]
  // Qualified references emitted via the callee index or a property access on a known
  // object: (owner, member) pairs validated after translation completes.
  protected val emittedQualifiedRefs = mutable.Set.empty[(String, String)]

  /** Qualify a bare name the callee index resolved, recording the reference for validation. */
  protected def qualify(ref: ReferenceSignatures.MemberRef): String =
    emittedQualifiedRefs += ((ref.owner, ref.member))
    ref.qualified

  /** The oracle's signature of an index-resolved member: under its declaring owner, else under the file declaring it (the oracle may be keyed by file). */
  protected def signatureOf(ref: ReferenceSignatures.MemberRef): Option[ReferenceSignatures.MethodSig] =
    oracle.get(ref.owner, ref.member).orElse(oracle.get(ref.file, ref.member))
  // Identifiers currently in a guarded scope where they have been tested
  // for truthiness and should be unwrapped (Nullable -> .get)
  protected val unwrappedIds = mutable.Set.empty[String]

  /** Look up the declared Scala type of an expression. Checks locals first (they shadow parameters inside guarded scopes), then parameters; a value the TypeScript checker types as a boolean, with
    * nothing in the reference saying otherwise, is a `Boolean`: its truthiness is its value, and a Scala type that disagrees fails to compile rather than reading differently.
    */
  protected def exprType(node: RastNode): Option[String] =
    declaredType(node).orElse(checkerType(node)).map(memberIndex.dealias)

  private val plainClassName = "[A-Za-z_][A-Za-z0-9_]*".r

  /** What the checker's type says in Scala, where it is unambiguous: a boolean, or a local or parameter holding an instance of a class the reference declares under that name with no type parameters.
    * Never a union (an absent value may be any of Scala's optional spellings), never a member read (the reference may declare the member wider, `AnyRef` against a cycle).
    */
  private def checkerType(node: RastNode): Option[String] =
    if isTsBoolean(node) then Some("Boolean")
    else
      node.`type`
        .flatMap(tsTypes.get)
        .collect {
          case t if node.kind == "Identifier" && t.kind == "object" && plainClassName.matches(t.text) =>
            if conventions.isClassName(t.text) then conventions.scalaClassName(t.text) else t.text
        }
        .filter(c => memberIndex.knowsType(c) && memberIndex.testable(c) == c)

  /** True when the checker typed `node` as `boolean`, possibly `| undefined` or `| null` (both falsy, as `false` is). */
  protected def isTsBoolean(node: RastNode): Boolean =
    def booleanish(id: String, depth: Int): Option[Boolean] =
      tsTypes.get(id).flatMap { t =>
        t.kind match
          case "booleanLiteral"     => Some(true)
          case "undefined" | "null" => Some(false)
          case "union" if depth < 3 =>
            val parts = t.types.getOrElse(Nil).map(booleanish(_, depth + 1))
            if parts.nonEmpty && parts.forall(_.isDefined) && parts.exists(_.contains(true)) then Some(true) else None
          case _ => None
      }
    node.`type`.flatMap(booleanish(_, 0)).contains(true)

  // lambda parameters in scope whose type nothing gives: they shadow every outer name and have no type
  protected val opaqueLocals = mutable.Set.empty[String]

  private def declaredType(node: RastNode): Option[String] =
    node.kind match
      case "Identifier" =>
        node.text.filterNot(n => opaqueLocals(snakeToCamel(n))).flatMap { name =>
          val scalaName = snakeToCamel(name)
          localTypes
            .get(scalaName)
            .orElse(localTypes.get(name))
            .orElse(paramTypes.get(scalaName))
            .orElse(paramTypes.get(name))
            .orElse(paramRenames.get(name).filterNot(_ => localNames.contains(scalaName)).flatMap(paramTypes.get))
            .orElse {
              outerBinding(name) match
                // an enclosing function's binding, held by the class the body is emitted into
                case Some(held) => held.flatMap(m => enclosingClass.flatMap(memberIndex.memberType(_, m)))
                // a module-level value the reference declares, when nothing in the body binds the name
                case None =>
                  if declaredInBody(name) || entry.params.contains(name) || apiLookup.contains(name) then None
                  else calleeIndex.resolveMember(scalaName).toOption.flatMap(_.tpe).orElse(fileValue(name).flatten)
            }
        }
      // the literal's EMITTED spelling decides its Scala type: `2` is an Int, `2.5` a Double
      case "NumericLiteral" | "FirstLiteralToken" =>
        node.value match
          case Some(RastValue.Num(n)) if n == n.toLong && n.abs <= Int.MaxValue => Some("Int")
          case _                                                                => Some("Double")
      case "StringLiteral" | "FirstTemplateToken" | "NoSubstitutionTemplateLiteral" => Some("String")
      case "TrueKeyword" | "FalseKeyword" | "BooleanLiteral"                        => Some("Boolean")
      case "NullKeyword" | "UndefinedKeyword"                                       => Some("Null")
      case "TemplateExpression" | "TemplateString"                                  => Some("String")
      case "CallExpression"                                                         =>
        // Known return types for specific JS methods
        val callee = node.children.headOption
        callee match
          case Some(prop) if prop.kind == "PropertyAccessExpression" && prop.children.size >= 2 =>
            val obj    = prop.children.head
            val method = prop.children.last.text.getOrElse("")
            method match
              case "toFixed" | "toString" | "substring" | "trim" | "toLowerCase" | "toUpperCase" | "replace" | "replaceAll" | "join" | "split" => Some("String")
              // a copy of the receiver's own collection or string
              case "concat" | "slice" | "filter" | "reverse" if exprType(obj).exists(t => t == "String" || extractElementType(t).isDefined) => exprType(obj)
              // `Object.assign({}, src, …)` read as a copy of a case-class `src`
              case "assign" if obj.kind == "Identifier" && obj.text.contains("Object") && node.children.lift(1).exists(t => t.kind == "ObjectLiteralExpression" && t.children.isEmpty) =>
                node.children.lift(2).flatMap(exprType).filter(t => ctorSchema.isCase(t))
              case _ if obj.kind == "Identifier" && obj.text.contains("Math") =>
                mathResultType(method, node.children.drop(1).map(exprType))
              case _ =>
                // Look up the oracle for the callee's return type
                if obj.kind == "Identifier" then
                  val oName = snakeToCamel(obj.text.getOrElse(""))
                  val cap   = if oName.nonEmpty then oName.take(1).toUpperCase + oName.drop(1) else ""
                  oracle
                    .get(cap, snakeToCamel(method))
                    .orElse(oracle.get(oName, snakeToCamel(method)))
                    .map(_.returnType)
                    // a method of a value whose class the reference declares, by that class's own declaration
                    .orElse(exprType(obj).flatMap(t => memberIndex.methodResult(baseName(t), snakeToCamel(method))))
                    .orElse(uniformBoolean(obj, method))
                else if obj.kind == "ThisKeyword" && memberIndex.knowsType(nodeScalaType) then
                  oracle.get(nodeScalaType, snakeToCamel(method)).map(_.returnType).orElse(memberIndex.methodResult(nodeScalaType, snakeToCamel(method)))
                else exprType(obj).flatMap(t => memberIndex.methodResult(baseName(t), snakeToCamel(method))).orElse(uniformBoolean(obj, method))
          case Some(id) if id.kind == "Identifier" =>
            val rawName = id.text.getOrElse("")
            val name    = snakeToCamel(rawName)
            // Try apiLookup first (it disambiguates names the callee index
            // considers ambiguous, e.g. buildExpression in both BuildHTML
            // and BuildMathML)
            outerBinding(rawName) match
              case Some(held) => held.flatMap(m => enclosingClass.flatMap(c => oracle.get(c, m).map(_.returnType).orElse(memberIndex.methodResult(c, m))))
              case None       =>
                apiLookup.get(rawName) match
                  case Some(q) if q.contains('.') => lookupCallee(q).map(_.returnType)
                  case _                          => calleeIndex.resolveMember(name).toOption.flatMap(signatureOf).map(_.returnType)
          case _ => None
      // a TS cast or `!` changes nothing at run time: the value keeps the type the body can see
      case "ParenthesizedExpression" | "AsExpression" | "TypeAssertionExpression" | "NonNullExpression" if node.children.nonEmpty => exprType(node.children.head)
      case "PrefixUnaryExpression" if node.children.nonEmpty                                                                      =>
        node.operator match
          case Some("ExclamationToken")               => Some("Boolean")
          case Some("MinusToken") | Some("PlusToken") => exprType(node.children.head).filter(numericTypes.contains)
          case _                                      => None
      case "BinaryExpression" if node.children.size >= 2 =>
        val (l, r) = (exprType(node.children.head), exprType(node.children.last))
        node.operator.getOrElse("") match
          case "EqualsEqualsEqualsToken" | "ExclamationEqualsEqualsToken" | "EqualsEqualsToken" | "ExclamationEqualsToken" | "LessThanToken" | "LessThanEqualsToken" | "GreaterThanToken" |
              "GreaterThanEqualsToken" | "InstanceOfKeyword" =>
            Some("Boolean")
          case "AmpersandAmpersandToken" | "BarBarToken" =>
            if l.contains("Boolean") && r.contains("Boolean") then Some("Boolean")
            // `opt || fallback` yields the present value or the fallback, both of the optional's inner type
            else if node.operator.contains("BarBarToken") && l.exists(isOptionLike) then l.map(optionInner)
            // `xs || []` yields a collection of the left operand's type either way
            else if node.operator.contains("BarBarToken") && r.isEmpty && node.children.last.kind == "ArrayLiteralExpression" && node.children.last.children.isEmpty then
              l.filter(t => extractElementType(t).isDefined)
            // `x || 0` on a fractional `x` is a number of x's type either way
            else if node.operator.contains("BarBarToken") && l.exists(t => (t == "Double" || t == "Float") && fractionalLiteral(node.children.last, t).isDefined) then l
            else if node.operator.contains("BarBarToken") && l.isDefined && l == r && !l.exists(isOptionLike) then l
            else None
          case "PlusToken" if l.contains("String") || r.contains("String") => Some("String")
          // JS division is always fractional; the emitted division widens an integral left operand
          case "SlashToken" if l.exists(numericTypes.contains) && r.exists(numericTypes.contains) => Some("Double")
          // JS bitwise operators always produce a 32-bit integer
          case "AmpersandToken" | "BarToken" | "CaretToken" | "LessThanLessThanToken" | "GreaterThanGreaterThanToken" => Some("Int")
          case "PlusToken" | "MinusToken" | "AsteriskToken" | "SlashToken" | "PercentToken" | "AmpersandToken" | "BarToken" | "CaretToken" | "LessThanLessThanToken" | "GreaterThanGreaterThanToken" |
              "GreaterThanGreaterThanGreaterThanToken" =>
            (l, r) match
              case (Some(a), Some(b)) if numericTypes(a) && numericTypes(b) => Some(if a == "Int" && b == "Int" then "Int" else if a == b then a else "Double")
              case _                                                        => None
          case _ => None
      // `new C(…)` of a class the reference declares
      case "NewExpression" if node.children.nonEmpty =>
        node.children.head.text.filter(n => ctorSchema.get(n).isDefined || memberIndex.knowsType(n))
      // `p[0]` on a tuple record is its field; `xs[i]` on a collection whose element type is declared
      case "ElementAccessExpression" if node.children.size >= 2 =>
        tupleField(node.children.head, node.children(1)) match
          case Some((cls, field)) => memberIndex.memberType(cls, field)
          // an element of a collection, the value a map holds under a key, or what a reference object's `apply` answers
          case None =>
            objectApply(node.children.head) match
              case Some((_, result)) => result
              case None              => exprType(node.children.head).flatMap(t => extractElementType(t).orElse(mapValueType(t)))
      // `xs.length` of a collection or a string
      case "PropertyAccessExpression"
          if node.children.size >= 2 && node.children.last.text.contains("length") &&
            exprType(node.children.head).exists(t => t == "String" || extractElementType(t).isDefined) =>
        Some("Int")
      // `p.f` where `p`'s declared type is a class whose constructor declares `f`
      case "PropertyAccessExpression" if node.children.size >= 2 =>
        val field = node.children.last.text.getOrElse("")
        val owner =
          if node.children.head.kind == "ThisKeyword" then Some(nodeScalaType)
          else exprType(node.children.head).map(_.takeWhile(c => c != '[' && c != ' '))
        val viaInstance = owner.flatMap { o =>
          ctorSchema
            .get(o)
            .flatMap(_.find(p => p.name == field || p.name == snakeToCamel(field)).map(_.tpe))
            .orElse(memberIndex.memberType(o, field))
            .orElse(memberIndex.memberType(o, snakeToCamel(field)))
        }
        // `Obj.f` where `Obj` is an object the reference declares
        def viaObject = node.children.head.text.filter(_ => node.children.head.kind == "Identifier").flatMap { js =>
          // the object the consumer's lookup names for a JS module, else the name itself
          val o = apiLookup.get(js).filterNot(_.contains('(')).getOrElse(js)
          calleeIndex.declared(o, field).orElse(calleeIndex.declared(o, snakeToCamel(field))).flatMap(_.tpe)
        }
        viaInstance.orElse(viaObject)
      case _ => None

  /** A package-level value the target file declares under the JS name, read bare, with its declared type: nothing the body binds, no member of the enclosing object or class and no object member of
    * that name could be what the bare name reaches instead.
    */
  protected def fileValue(n: String): Option[Option[String]] =
    fileNames.terms
      .get(n)
      .filter(_ =>
        !declaredInBody(n) && !entry.params.contains(n) && !isLocal(n) && outerBinding(n).isEmpty && calleeIndex.resolveMember(n).isLeft &&
          !enclosingOwner.exists(calleeIndex.declared(_, n).isDefined) && !enclosingClass.exists(memberIndex.hasMember(_, n)) &&
          !(nodeScalaType.nonEmpty && memberIndex.hasMember(nodeScalaType, n))
      )

  /** A JS module object indexed as a map (`o[k]`) that the reference made an object declaring `apply`: the object's Scala path and the result the reference's one-argument signature of `apply`
    * declares. None for a name the body binds or any other receiver.
    */
  protected def objectApply(recv: RastNode): Option[(String, Option[String])] =
    recv.text.filter(_ => recv.kind == "Identifier").filterNot(n => declaredInBody(n) || entry.params.contains(n) || isLocal(n) || outerBinding(n).isDefined).flatMap { n =>
      val path = apiLookup.getOrElse(n, snakeToCamel(n))
      calleeIndex.declared(path, "apply").filter(_.accessibleFrom(enclosingOwner)).map(ref => (path, signatureOf(ref).filter(_.params.size == 1).map(_.returnType)))
    }

  /** The JS truthiness test for a value of the given Scala type. Returns a function that takes the translated operand and produces the Scala boolean expression.
    */
  protected def truthinessTest(tpe: String, operand: String): String =
    // the test binds tighter than any operator the operand may hold (`a & b != 0` reads `a & (b != 0)`)
    val p = if isSimplePath(operand) || enclosedInParens(operand) then operand else s"($operand)"
    // a present "", 0, NaN or false is as falsy as an absent value
    if isOptionLike(tpe) && (optionInner(tpe) == "String" || optionInner(tpe) == "Boolean" || numericTypes(optionInner(tpe))) then
      if isSimplePath(operand) then s"($operand.isDefined && ${truthinessTest(optionInner(tpe), s"$operand.get")})"
      else s"{ val o$$ = $operand; o$$.isDefined && ${truthinessTest(optionInner(tpe), "o$.get")} }"
    else if isOptionLike(tpe) then s"$p.isDefined"
    else if tpe == "String" then s"$p.nonEmpty"
    else if tpe == "Double" || tpe == "Float" then
      // NaN is falsy in JS; the operand is read once.
      if isSimplePath(operand) then s"($operand != 0 && !$operand.isNaN)"
      else s"{ val n$$ = $operand; n$$ != 0 && !n$$.isNaN }"
    else if numericTypes.contains(tpe) then s"$p != 0"
    else if tpe == "Boolean" then operand
    else if tpe == "Null" then "false"
    // a value of a top type may hold a JS false, 0, NaN or "", which a presence test reads as truthy
    else if tpe.trim.stripSuffix("| Null").split('|').map(_.trim).exists(t => topTypes(t) || t == "String" || t == "Boolean" || numericTypes(t)) then
      refuse("truthiness-unknown-type")
      s"($p != null)"
    else s"($p != null)" // any other object, an array included, is truthy unless it is absent

  private val topTypes = Set("Any", "AnyRef", "Object", "Matchable", "java.lang.Object")

  /** The JS truthiness value (the value that a truthy operand yields in `x && y` or `x || y`). For an optional value, this is `x.get`.
    */
  protected def truthyValue(tpe: String, operand: String): String =
    if isOptionLike(tpe) then s"$operand.get"
    else operand

  /** `use` applied to `operand` read ONCE: JS evaluates the left of `x || y` a single time, and a test and a yield of it would read it twice. */
  protected def onceBound(operand: String)(use: String => String): String =
    if isSimplePath(operand) then s"(${use(operand)})"
    else
      val v = freshLabel("v")
      s"{ val $v = $operand; ${use(v)} }"

  /** Extract element type from Array[T], ArrayBuffer[T], List[T], Seq[T]. */
  /** The Scala type a local's TS annotation names, or the annotation's kind when it names none the translator can spell: a primitive keyword, or a type the reference declares under that name. */
  protected def annotationType(t: RastNode): Either[String, String] =
    t.kind match
      case "NumberKeyword"                         => Right("Double")
      case "StringKeyword"                         => Right("String")
      case "BooleanKeyword"                        => Right("Boolean")
      case "TypeReference" if t.children.size == 1 =>
        t.children.head.text.filter(n => ctorSchema.get(n).isDefined || memberIndex.knowsType(n)).toRight("TypeReference")
      // `T[]` is a JS array: a mutable buffer of `T`, kept only where the body can write `T`, since `[]` must name it
      case "ArrayType" if t.children.size == 1 =>
        annotationType(t.children.head).toOption.filter(writableType).map(e => s"scala.collection.mutable.ArrayBuffer[$e]").toRight("ArrayType")
      case other => Left(other)

  private val standardCollections = Set("Vector", "List", "Seq", "IndexedSeq", "Option", "Map", "Set")

  /** True when the body can write `t` as a type and mean what the target file means: every name in it is a primitive, `String`, a standard collection, a fully qualified `scala.collection` type or a
    * name the file writes itself (`nameableTypes`).
    */
  protected def writableType(t: String): Boolean =
    val unqualified = t.replace("scala.collection.mutable.", "").replace("scala.collection.immutable.", "")
    val names       = "[A-Za-z_][A-Za-z0-9_]*".r.findAllIn(unqualified).toList
    names.nonEmpty && !unqualified.contains('.') && names.forall(n => portableType(n) || standardCollections(n) || n == "ArrayBuffer" && t.contains("scala.collection.mutable.") || nameableTypes(n))

  protected def extractElementType(tpe: String): Option[String] =
    val bracketIdx = tpe.indexOf('[')
    if bracketIdx < 0 then None
    else
      // `mutable.ArrayBuffer[T]` and `scala.collection.mutable.ArrayBuffer[T]` name the same collection
      val prefix = tpe.substring(0, bracketIdx).split('.').last
      if Set("Array", "ArrayBuffer", "List", "Seq", "IndexedSeq", "Vector").contains(prefix) && !tpe.contains("|")
      then Some(tpe.substring(bracketIdx + 1, tpe.lastIndexOf(']')))
      else None

  // The expression a statement evaluates only for its effect: an assignment or `x++` there yields no value anyone reads.
  protected var statementRoot: RastNode | Null = null

  protected def withStatementRoot[A](root: RastNode | Null)(body: => A): A =
    val saved = statementRoot
    statementRoot = root
    try body
    finally statementRoot = saved

  protected def unparenthesized(n: RastNode): RastNode =
    if n.kind == "ParenthesizedExpression" && n.children.nonEmpty then unparenthesized(n.children.head) else n

  /** Every name the body itself binds, at any depth: variables, parameters of nested functions, destructured names, nested function names. Scope is over-approximated. */
  protected lazy val bodyDeclarations: Set[String] =
    val out = mutable.Set.empty[String]
    def walk(n: RastNode): Unit =
      n.kind match
        case "VariableDeclaration" | "Parameter" | "BindingElement" | "FunctionDeclaration" =>
          n.children.find(_.kind == "Identifier").flatMap(_.text).foreach(out += _)
        case _ => ()
      n.children.foreach(walk)
    walk(entry.bodyNode)
    out.toSet

  /** Bound by the body itself, or a parameter the reference signature declares. */
  protected def declaredInBody(n: String): Boolean =
    bodyDeclarations.contains(n) || paramTypes.contains(n) || paramTypes.contains(snakeToCamel(n))

  /** A capitalised name that is a type, an object or a JS global the translator spells itself, rather than a value to resolve. */
  protected def namesAType(n: String): Boolean =
    memberIndex.knowsType(n) || ctorSchema.get(n).isDefined || calleeIndex.knownObjects.contains(n) || jsGlobals.contains(n)

  /** A local variable of this body, by its JS or its Scala spelling. */
  protected def isLocal(n: String): Boolean =
    val sc = snakeToCamel(n)
    localTypes.contains(sc) || localTypes.contains(n) || localNames.contains(sc) || localNames.contains(n)

  // the nodes an assignment, a compound assignment or an increment writes, by identity: a written element is an update, never a guarded read
  private lazy val writeTargets: java.util.Set[RastNode] =
    val out = java.util.Collections.newSetFromMap(new java.util.IdentityHashMap[RastNode, java.lang.Boolean]())
    def walk(n: RastNode): Unit =
      val writes =
        (n.kind == "BinaryExpression" && n.operator.exists(op => (op.endsWith("EqualsToken") && !comparisonOps(op)) || op == "FirstAssignment" || op == "FirstCompoundAssignment")) ||
          ((n.kind == "PostfixUnaryExpression" || n.kind == "PrefixUnaryExpression") && n.operator.exists(op => op == "PlusPlusToken" || op == "MinusMinusToken"))
      if writes then n.children.headOption.foreach(out.add)
      n.children.foreach(walk)
    walk(entry.bodyNode)
    out

  private val comparisonOps = Set(
    "EqualsEqualsToken",
    "EqualsEqualsEqualsToken",
    "ExclamationEqualsToken",
    "ExclamationEqualsEqualsToken",
    "LessThanEqualsToken",
    "GreaterThanEqualsToken"
  )

  // each node's parent, by identity, so a site can ask what encloses it
  private lazy val parents: java.util.Map[RastNode, RastNode] =
    val out = new java.util.IdentityHashMap[RastNode, RastNode]()
    def walk(n: RastNode): Unit = n.children.foreach { c => out.put(c, n); walk(c) }
    walk(entry.bodyNode)
    out

  /** The node `n` sits in, by identity; none for the body itself. */
  protected def parentOf(n: RastNode): Option[RastNode] = Option(parents.get(n))

  /** The immutable sequence a slot declares, and the conversion that copies a buffer into it. */
  protected val handOverConversions: Map[String, String] = Map("Vector" -> "toVector", "List" -> "toList", "IndexedSeq" -> "toIndexedSeq", "Seq" -> "toSeq")

  /** True for the type of a mutable buffer a JS array local is kept in. */
  protected def isMutableBuffer(t: String): Boolean = baseName(t) == "ArrayBuffer" && extractElementType(t).isDefined

  /** True when a buffer handed to `slot` is copied there: the slot is an immutable sequence. */
  protected def isHandOverSlot(slot: String): Boolean = handOverConversions.contains(baseName(slot)) && extractElementType(slot).isDefined

  /** `n` without its source position: two reads of one receiver compare equal. */
  protected def shapeOf(n: RastNode): String = shape(n)

  // a node's shape without its source position: two reads of one receiver compare equal
  private def shape(n: RastNode): String = s"${n.kind}(${n.text.getOrElse("")}${n.value.fold("")(_.toString)}${n.children.map(shape).mkString(",")})"

  /** The key type of a Scala map type, when the type is one. */
  protected def mapKeyType(t: String): Option[String] =
    mapValueType(t).map { v =>
      val args = t.substring(t.indexOf('[') + 1, t.lastIndexOf(']'))
      args.substring(0, args.length - v.length).trim.stripSuffix(",").trim
    }

  /** The value type of a Scala map type, when the type is one. */
  protected def mapValueType(t: String): Option[String] =
    val base = baseName(t)
    if !Set("Map", "HashMap", "LinkedHashMap", "TreeMap").contains(base) || !t.contains('[') then None
    else
      val args = t.substring(t.indexOf('[') + 1, t.lastIndexOf(']'))
      // split at the top-level comma
      var depth = 0
      val cut   = args.indices.find { i =>
        args(i) match
          case '[' => depth += 1; false
          case ']' => depth -= 1; false
          case ',' => depth == 0
          case _   => false
      }
      cut.map(i => args.substring(i + 1).trim)

  /** True for a type whose JS `undefined` Scala spells `null`: a class, a collection, a string. */
  protected def absentIsNull(t: String): Boolean = t == "String" || comparesByIdentity(t)

  /** True for a node an assignment or an increment writes. */
  protected def isWriteTarget(n: RastNode): Boolean = writeTargets.contains(n)

  /** A type whose values JS compares by identity: a class, a collection or an array, possibly absent as `null`; never a primitive, a string, an optional, a wider union or an unknown `Any`. */
  protected def comparesByIdentity(t: String): Boolean =
    val core = t.trim.stripSuffix("| Null").trim
    !core.contains('|') && !numericTypes(core) && !Set("String", "Boolean", "Char", "Unit", "Null").contains(core) && !isOptionLike(core) &&
    !Set("Any", "AnyRef", "Object", "Matchable", "Nullable").contains(baseName(core))

  /** `Boolean` for a method call on a receiver of unknown type when every class the reference declares the method on returns `Boolean`: its truthiness is its value, and a receiver of any other type
    * fails to compile rather than reading differently. No other result is read this way.
    */
  private def uniformBoolean(recv: RastNode, method: String): Option[String] =
    if exprType(recv).isDefined then None
    else memberIndex.uniformResult(memberRenames.getOrElse(snakeToCamel(method), snakeToCamel(method))).filter(_ == "Boolean")

  /** True when the checker types `node` as an array of strings, whose elements print as themselves. */
  protected def checkerStringArray(node: RastNode): Boolean =
    node.`type`.flatMap(tsTypes.get).filter(_.kind == "array").flatMap(_.elementType).flatMap(tsTypes.get).exists(t => t.kind == "string" || t.kind == "stringLiteral")

  /** A numeric literal, or its negation, written at the fractional type `tpe`: `0` is `0.0` at `Double` and `0.0f` at `Float`. */
  protected def fractionalLiteral(n: RastNode, tpe: String): Option[String] =
    val (negated, lit) = if n.kind == "PrefixUnaryExpression" && n.operator.contains("MinusToken") then (true, n.children.headOption) else (false, Some(n))
    lit.filter(l => l.kind == "NumericLiteral" || l.kind == "FirstLiteralToken").flatMap(_.value).collect { case RastValue.Num(d) =>
      val v = if negated then -d else d
      if tpe == "Float" then s"${v.toFloat}f" else v.toString
    }

  /** A non-negative integer literal's value. */
  protected def intLiteral(n: RastNode): Option[Int] =
    n.value.collect { case RastValue.Num(d) if d >= 0 && d == d.toInt => d.toInt }.filter(_ => n.kind == "NumericLiteral" || n.kind == "FirstLiteralToken")

  /** The class and the field a constant index reads off a value whose declared class is one of the library's tuple records; none for any other receiver or index. */
  protected def tupleField(recv: RastNode, idx: RastNode): Option[(String, String)] =
    for
      t <- exprType(recv)
      cls = baseName(t)
      fields <- conventions.tupleRecords.get(cls)
      i <- intLiteral(idx)
      field <- fields.lift(i)
    yield (cls, field)

  /** A name an enclosing JS function binds and this body does not: `Some(Some(m))` when the class the body is emitted into declares it as `m`, `Some(None)` when nothing can hold it, `None` for a name
    * no enclosing function binds. Such a name shadows any module-level one, so it is never resolved there.
    */
  protected def outerBinding(n: String): Option[Option[String]] =
    if !entry.outerScope.contains(n) || entry.params.contains(n) || declaredInBody(n) || isLocal(n) then None
    else
      val sc = memberRenames.getOrElse(snakeToCamel(n), snakeToCamel(n))
      Some(enclosingClass.flatMap(c => List(sc, n).find(memberIndex.hasMember(c, _))))

  protected def refuse(reason: String): Unit =
    refusals += reason
    refusalSites += DefmethodBodyTranslator.RefusalSite(reason, site._1, site._2)

  // the source position of the innermost node being rendered, for the refusal sites
  private var site: (Int, Int) = (0, 0)

  /** Renders under `node`'s source position, so a refusal inside names where it arose. */
  protected def at[A](node: RastNode)(render: => A): A =
    val saved = site
    if node.pos != (0, 0) then site = node.pos
    try render
    finally site = saved

  protected def collectAllProps(cls: DefnodeClass): Set[String] =
    val own = cls.selfProps.toSet
    cls.base.flatMap(byName.get).map(p => own ++ collectAllProps(p)).getOrElse(own)

  /** Try to look up a callee's method signature from the oracle by splitting a qualified name. */
  protected def lookupCallee(qualifiedName: String): Option[ReferenceSignatures.MethodSig] =
    val dotIdx = qualifiedName.lastIndexOf('.')
    if dotIdx >= 0 then oracle.get(qualifiedName.substring(0, dotIdx), qualifiedName.substring(dotIdx + 1))
    else None

  /** Check whether `have` is compatible with `want`. When both are known specific types with different base names that are not in a subtype relationship the member index can prove, refuse
    * argument-type-mismatch.
    */
  protected def checkTypeCompatibility(have0: String, want0: String, subtypes: Boolean = true): Unit =
    val have     = memberIndex.dealias(have0)
    val want     = memberIndex.dealias(want0)
    val haveBase = baseName(have)
    val wantBase = baseName(want)
    // a class the reference declares extending the slot's, at a slot that admits a subtype (a covariant element's or none at all)
    if subtypes && haveBase != wantBase && memberIndex.knowsType(haveBase) && memberIndex.ancestry(haveBase).contains(wantBase) && !want.contains('[') then ()
    else if wantBase == "Any" || wantBase == "AnyRef" || haveBase == "Any" ||
      haveBase == "Nothing" || wantBase == "Nothing" || haveBase == "Null"
    then ()
    // a value that may be absent where the slot requires one: JS read it after a guard Scala does not see
    else if isOptionLike(have) && !isOptionLike(want) && !want.contains("| Null") then refuse("optional-at-required-slot")
    // a present value at an optional slot is wrapped there, so it must be of the slot's inner type
    else if isOptionLike(want) && !isOptionLike(have) then checkTypeCompatibility(have, optionInner(want))
    // two optionals of one spelling hold values of their inner types
    // (an `Option` is covariant, a `Nullable` is not)
    else if isOptionLike(want) && isOptionLike(have) && haveBase == wantBase then checkTypeCompatibility(optionInner(have), optionInner(want), subtypes = haveBase == "Option")
    // a JS array local at an immutable slot is copied there, where the slot's translation decides whether the copy is unobservable
    else if isMutableBuffer(have) && isHandOverSlot(want) then
      (extractElementType(have), extractElementType(want)) match
        case (Some(he), Some(we)) => checkTypeCompatibility(he, we)
        case _                    => ()
    else if haveBase == wantBase then
      // Same container: compare element types when both are parameterized
      val haveElem = extractElementType(have)
      val wantElem = extractElementType(want)
      // an immutable sequence holds a subtype's elements; a buffer or an array only its own element type
      (haveElem, wantElem) match
        case (Some(he), Some(we)) => checkTypeCompatibility(he, we, subtypes = handOverConversions.contains(haveBase))
        case _                    => () // same base, no type params or not extractable
    else if numericTypes.contains(haveBase) && numericTypes.contains(wantBase) then
      // a fractional number at an integral slot does not narrow implicitly in Scala, and JS never truncates there
      if (haveBase == "Double" || haveBase == "Float") && integralTypes.contains(wantBase) then refuse("double-at-int-slot")
    else if trivialTypes.contains(haveBase) && trivialTypes.contains(wantBase) then
      // Both are known, different non-numeric trivial types
      refuse(s"argument-type-mismatch:$haveBase->$wantBase")
    else if isSpecificClassType(have) && isSpecificClassType(want) then
      // Two specific class types with different base names: refuse
      refuse(s"argument-type-mismatch:$haveBase->$wantBase")
    // a class where a primitive or a string is required, or the other way round; a string at an enumeration's slot names its member, which the slot's translation resolves or refuses
    else if (isSpecificClassType(have) && scalarTypes(wantBase)) || (scalarTypes(haveBase) && isSpecificClassType(want) && !(haveBase == "String" && enumIndex.byValue.keys.exists(_._1 == wantBase)))
    then refuse(s"argument-type-mismatch:$haveBase->$wantBase")

  private val scalarTypes = Set("String", "Boolean", "Char", "Int", "Long", "Short", "Byte", "Double", "Float")

  /** Renders an expression; the renderer supplies it, the text conversions below call it. */
  def translateExpr(node: RastNode): String

  /** Renders a statement; the renderer supplies it, [[translateSequence]] calls it. */
  def translateStatement(node: RastNode, indent: String, isLast: Boolean): Unit

  // identifiers an `instanceof` test has narrowed, by Scala name, to the class it tested
  protected val narrowedIds = mutable.Map.empty[String, String]

  /** What `cond` proves when it HOLDS: `x instanceof C` makes the local `x` a `C`, and so does each operand of an `&&`. Only a body-local name the body never reassigns, at a class the reference
    * declares with no type parameters, so the cast it renders is exactly the test JS made.
    */
  protected def narrowedBy(cond: RastNode): List[(String, String)] =
    cond.kind match
      case "ParenthesizedExpression"                                                                          => cond.children.headOption.toList.flatMap(narrowedBy)
      case "BinaryExpression" if cond.operator.contains("AmpersandAmpersandToken") && cond.children.size >= 2 =>
        narrowedBy(cond.children.head) ++ narrowedBy(cond.children.last)
      case "BinaryExpression" if cond.operator.contains("InstanceOfKeyword") =>
        cond.children.filterNot(_.kind == "InstanceOfKeyword") match
          case RastNode("Identifier", _, _, _, _, _, _, _, Some(name), _, _, _) :: RastNode("Identifier", _, _, _, _, _, _, _, Some(cls), _, _, _) :: Nil =>
            val scalaCls = if conventions.isClassName(cls) then conventions.scalaClassName(cls) else cls
            if narrowable(name, scalaCls) then List(name -> scalaCls) else Nil
          case _ => Nil
      case "BinaryExpression" if cond.operator.exists(op => op == "EqualsEqualsEqualsToken" || op == "EqualsEqualsToken") => tagNarrowing(cond)
      case _                                                                                                              => Nil

  /** Renders `render` where `cond` holding proves an optional local present: under `if (x)` on a `Nullable`/`Option` local or parameter the body never reassigns, `x` reads as its value. */
  protected def underPresence[A](cond: RastNode)(render: => A): A =
    val id      = unparenthesized(cond)
    val present = id.text.filter(n => id.kind == "Identifier" && (isLocal(n) || entry.params.contains(n)) && !reassignedNames.contains(n) && !reassignedNames.contains(snakeToCamel(n)))
    present.map(snakeToCamel).filterNot(unwrappedIds.contains).flatMap(n => exprType(id).filter(isOptionLike).map(n -> _)) match
      case None           => render
      case Some((n, tpe)) =>
        val saved = localTypes.get(n)
        unwrappedIds += n
        localTypes(n) = optionInner(tpe)
        try render
        finally
          unwrappedIds -= n
          saved.fold(localTypes.remove(n))(localTypes.put(n, _))

  /** What `cond` proves when it FAILS: `!(x instanceof C)` failing makes `x` a `C`, and so does each operand of a failing `||`. */
  protected def refutedBy(cond: RastNode): List[(String, String)] =
    cond.kind match
      case "ParenthesizedExpression"                                                              => cond.children.headOption.toList.flatMap(refutedBy)
      case "PrefixUnaryExpression" if cond.operator.contains("ExclamationToken")                  => cond.children.headOption.toList.flatMap(narrowedBy)
      case "BinaryExpression" if cond.operator.contains("BarBarToken") && cond.children.size >= 2 =>
        refutedBy(cond.children.head) ++ refutedBy(cond.children.last)
      case "BinaryExpression" if cond.operator.exists(op => op == "ExclamationEqualsEqualsToken" || op == "ExclamationEqualsToken") => tagNarrowing(cond)
      case _                                                                                                                        => Nil

  // a local the body never reassigns, at a class the reference declares with no type parameters: the cast it renders is exactly the test JS made
  private def narrowable(name: String, scalaCls: String): Boolean =
    (isLocal(name) || entry.params.contains(name)) && !reassignedNames.contains(name) && !reassignedNames.contains(snakeToCamel(name)) &&
      memberIndex.knowsType(scalaCls) && memberIndex.testable(scalaCls) == scalaCls && !unwrappedIds.contains(snakeToCamel(name)) &&
      // the cast must be able to write the class
      spelled(scalaCls).isDefined

  /** `x.<tag> == "Y"` proves `x` is the ONE class whose tag the reference declares as `"Y"`; a value two classes declare proves nothing. */
  private def tagNarrowing(cond: RastNode): List[(String, String)] =
    if cond.children.size < 2 then Nil
    else
      val (l, r) = (cond.children.head, cond.children.last)
      tagComparison(l, r).orElse(tagComparison(r, l)).toList.flatMap {
        case (RastNode("Identifier", _, _, _, _, _, _, _, Some(name), _, _, _), tag) =>
          conventions.typeTag.toList.flatMap((_, member) => memberIndex.classesDeclaring(member, tag)) match
            case List(cls) if narrowable(name, cls) => List(name -> cls)
            case _                                  => Nil
        case _ => Nil
      }

  /** True for a read of the library's type-tag field (`node.<tag>`). */
  protected def isTypePropertyAccess(node: RastNode): Boolean =
    node.kind == "PropertyAccessExpression" && node.children.size >= 2 && conventions.tagField.exists(t => node.children.last.text.contains(t))

  /** The receiver and the value when `access` reads the type tag and `literal` is a string literal. */
  protected def tagComparison(access: RastNode, literal: RastNode): Option[(RastNode, String)] =
    if !isTypePropertyAccess(access) || literal.kind != "StringLiteral" then None
    else literal.value.collect { case RastValue.Str(s) => (access.children.head, s) }

  /** The class a tag read's receiver is declared as; `this` is the body's own class. */
  protected def tagReceiverType(recv: RastNode): Option[String] =
    if recv.kind == "ThisKeyword" then Some(nodeScalaType).filter(_.nonEmpty) else exprType(recv).map(baseName)

  /** Renders under the narrowing `pairs` prove: each name reads as its class, and renders cast to it. */
  protected def underNarrowing[A](pairs: List[(String, String)])(render: => A): A =
    if pairs.isEmpty then render
    else
      val names = pairs.map((js, cls) => snakeToCamel(js) -> cls)
      val saved = names.map((n, _) => (n, localTypes.get(n), narrowedIds.get(n)))
      names.foreach { (n, cls) =>
        localTypes(n) = cls
        narrowedIds(n) = cls
      }
      try render
      finally
        saved.foreach { (n, tpe, narrowed) =>
          tpe.fold(localTypes.remove(n))(localTypes.put(n, _))
          narrowed.fold(narrowedIds.remove(n))(narrowedIds.put(n, _))
        }

  /** True for a statement no path completes normally through: control always leaves the enclosing sequence. */
  protected def exitsAlways(stmt: RastNode): Boolean =
    stmt.kind match
      case "ReturnStatement" | "ThrowStatement"     => true
      case "BreakStatement" | "ContinueStatement"   => stmt.children.isEmpty
      case "Block"                                  => stmt.children.lastOption.exists(exitsAlways)
      case "IfStatement" if stmt.children.size >= 3 => exitsAlways(stmt.children(1)) && exitsAlways(stmt.children(2))
      case _                                        => false

  /** Renders a statement sequence; after `if (c) <exit>` with no else, the rest renders under what `c` failing proves. */
  protected def translateSequence(stmts: List[RastNode], indent: String, isLast: Boolean): Unit =
    stmts match
      case Nil          => ()
      case stmt :: rest =>
        translateStatement(stmt, indent, isLast && rest.isEmpty)
        val proven =
          if stmt.kind == "IfStatement" && stmt.children.size == 2 && exitsAlways(stmt.children(1)) then refutedBy(stmt.children.head) else Nil
        underNarrowing(proven)(translateSequence(rest, indent, isLast))

  /** The arguments a JS call leaves out, as Scala must still pass them: a parameter with a default is skipped, an optional one is passed absent by name (JS passes `undefined`), and any other missing
    * one leaves the call untranslatable (`None`).
    */
  protected def omittedArgs(s: ReferenceSignatures.MethodSig, passed: Int): Option[List[String]] =
    val missing = s.params.drop(passed)
    if missing.exists(p => !p.hasDefault && !isOptionLike(p.tpe)) then None
    else
      Some(
        missing.filter(p => !p.hasDefault).map { p =>
          val name = if scalaKeywords.contains(p.name) then s"`${p.name}`" else p.name
          s"$name = ${if isNullableType(p.tpe) then "Nullable.Null" else "None"}"
        }
      )

  /** A value converted to text as JS's `+ ""` and template substitution do. Scala's `toString` agrees for strings, integers, booleans and a class's own `toString`; a Double prints `2.0` where JS
    * prints `2`, and an array, a map or a value of unknown type prints differently too.
    */
  protected def stringified(n: RastNode): String =
    val raw = translateExpr(n)
    // `.toString` binds tighter than any operator the operand may contain, a prefix `-` included
    val text = if isSimplePath(raw) || enclosedInParens(raw) then raw else s"($raw)"
    exprType(n) match
      case Some("String")                                                     => text
      case Some("Int" | "Long" | "Short" | "Byte" | "Boolean" | "Char")       => s"$text.toString"
      case Some(t) if isSpecificClassType(t) && extractElementType(t).isEmpty => s"$text.toString"
      case Some("Double")                                                     => jsNumberText(raw)
      // a Float holds another value than the JS number it stands for
      case Some("Float") =>
        refuse("number-to-string")
        s"$text.toString"
      case _ =>
        refuse("value-to-string")
        s"$text.toString"

  /** The text JS's `Number.prototype.toString` makes of the Double `expr`: an integer without `.0`, the shortest digits that read back as the value, plain from `1e-6` up to `1e21` and in JS's own
    * exponent form (`1.5e-7`, `1e+21`) beyond; `NaN` and the infinities as Scala spells them, a zero of either sign as `0`. Every platform's `Double.toString` gives the shortest digits of a normal
    * value; of a subnormal one the JVM's keeps two, so those are searched for directly.
    */
  protected def jsNumberText(expr: String): String =
    val d        = freshLabel("d")
    val b        = freshLabel("b")
    val e        = freshLabel("e")
    val u        = freshLabel("u")
    val p        = freshLabel("p")
    val shortest =
      s"if (math.abs($d) >= java.lang.Double.MIN_NORMAL) java.math.BigDecimal.valueOf($d) else { var $p = 1; " +
        s"while (new java.math.BigDecimal($d).round(new java.math.MathContext($p, java.math.RoundingMode.HALF_EVEN)).doubleValue != $d) $p += 1; " +
        s"new java.math.BigDecimal($d).round(new java.math.MathContext($p, java.math.RoundingMode.HALF_EVEN)) }"
    s"{ val $d: Double = $expr; if ($d.isNaN || $d.isInfinite) $d.toString else if ($d == 0) \"0\" else { " +
      s"val $b = ($shortest).stripTrailingZeros; val $e = $b.precision - $b.scale - 1; " +
      s"if ($e >= -6 && $e < 21) $b.toPlainString else { val $u = $b.unscaledValue.abs.toString; " +
      s"(if ($d < 0) \"-\" else \"\") + $u.substring(0, 1) + (if ($u.length > 1) \".\" + $u.substring(1) else \"\") + \"e\" + (if ($e >= 0) \"+\" else \"-\") + math.abs($e) } } }"

  // one parenthesised group from the first character to the last, so a member read binds to all of it
  private def enclosedInParens(s: String): Boolean =
    s.startsWith("(") && s.endsWith(")") && {
      var depth = 0
      s.indices.forall { i =>
        if s(i) == '(' then depth += 1 else if s(i) == ')' then depth -= 1
        depth > 0 || i == s.length - 1
      }
    }

  protected def translateTemplateExpr(node: RastNode): String =
    // Emit as string concatenation ("a " + expr + " b") rather than an
    // s-string. Concatenation avoids nested quotes from expressions like
    // toFixed, and the output does not contain "${" which pattern guards
    // in the consumer would catch.
    val parts = mutable.ListBuffer.empty[String]
    for c <- node.children do
      c.kind match
        case "TemplateHead" | "TemplateMiddle" | "TemplateTail" =>
          c.value match
            case Some(RastValue.Str(s)) if s.nonEmpty =>
              parts += "\"" + escapeString(s) + "\""
            case _ => ()
        case "NoSubstitutionTemplateLiteral" =>
          c.value match
            case Some(RastValue.Str(s)) =>
              parts += "\"" + escapeString(s) + "\""
            case _ => ()
        // `${e}text`: the substituted expression, then the literal text up to the next one
        case "TemplateSpan" if c.children.nonEmpty =>
          parts += stringified(c.children.head)
          c.children.drop(1).foreach { lit =>
            lit.value match
              case Some(RastValue.Str(s)) if s.nonEmpty => parts += "\"" + escapeString(s) + "\""
              case _                                    => ()
          }
        case _ =>
          parts += stringified(c)
    if parts.isEmpty then "\"\""
    else if parts.length == 1 then parts.head
    else parts.mkString(" + ")
