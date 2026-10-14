package balticporter.frontend.ts.dedicated

import balticporter.frontend.ts.{ RastNode, RastValue, ReferenceSignatures }
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
  val conventions:        JsConventions
):
  val sb                 = new StringBuilder
  val refusals           = mutable.ListBuffer.empty[String]
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
  // Parameter names that are reassigned in the body: refuse upfront
  protected val reassignedParams: Set[String] = entry.params.filter(reassignedNames.contains).toSet

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

  /** Look up the declared Scala type of an expression. Checks locals first (they shadow parameters inside guarded scopes), then parameters.
    */
  protected def exprType(node: RastNode): Option[String] =
    node.kind match
      case "Identifier" =>
        node.text.flatMap { name =>
          val scalaName = snakeToCamel(name)
          localTypes.get(scalaName).orElse(localTypes.get(name)).orElse(paramTypes.get(scalaName)).orElse(paramTypes.get(name))
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
              case _ if obj.kind == "Identifier" && obj.text.contains("Math")                                                                  =>
                mathResultType(method, node.children.drop(1).map(exprType))
              case _ =>
                // Look up the oracle for the callee's return type
                if obj.kind == "Identifier" then
                  val oName = snakeToCamel(obj.text.getOrElse(""))
                  val cap   = if oName.nonEmpty then oName.take(1).toUpperCase + oName.drop(1) else ""
                  oracle.get(cap, snakeToCamel(method)).orElse(oracle.get(oName, snakeToCamel(method))).map(_.returnType)
                else if obj.kind == "ThisKeyword" && memberIndex.knowsType(nodeScalaType) then oracle.get(nodeScalaType, snakeToCamel(method)).map(_.returnType)
                else None
          case Some(id) if id.kind == "Identifier" =>
            val rawName = id.text.getOrElse("")
            val name    = snakeToCamel(rawName)
            // Try apiLookup first (it disambiguates names the callee index
            // considers ambiguous, e.g. buildExpression in both BuildHTML
            // and BuildMathML)
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
      // `xs[i]` on a collection whose element type is declared
      case "ElementAccessExpression" if node.children.size >= 2 =>
        exprType(node.children.head).flatMap(extractElementType)
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
        owner.flatMap(ctorSchema.get).flatMap(_.find(p => p.name == field || p.name == snakeToCamel(field)).map(_.tpe))
      case _ => None

  /** The JS truthiness test for a value of the given Scala type. Returns a function that takes the translated operand and produces the Scala boolean expression.
    */
  protected def truthinessTest(tpe: String, operand: String): String =
    if isOptionLike(tpe) then s"$operand.isDefined"
    else if tpe == "String" then s"$operand.nonEmpty"
    else if tpe == "Double" || tpe == "Float" then
      // NaN is falsy in JS; the operand is read once.
      if isSimplePath(operand) then s"($operand != 0 && !$operand.isNaN)"
      else s"{ val n$$ = $operand; n$$ != 0 && !n$$.isNaN }"
    else if numericTypes.contains(tpe) then s"$operand != 0"
    else if tpe == "Boolean" then operand
    else if tpe == "Null" then "false"
    else s"($operand != null)" // any other object, an array included, is truthy unless it is absent

  /** The JS truthiness value (the value that a truthy operand yields in `x && y` or `x || y`). For an optional value, this is `x.get`.
    */
  protected def truthyValue(tpe: String, operand: String): String =
    if isOptionLike(tpe) then s"$operand.get"
    else operand

  /** Extract element type from Array[T], ArrayBuffer[T], List[T], Seq[T]. */
  /** The Scala type a local's TS annotation names, or the annotation's kind when it names none the translator can spell: a primitive keyword, or a type the reference declares under that name. */
  protected def annotationType(t: RastNode): Either[String, String] =
    t.kind match
      case "NumberKeyword"                         => Right("Double")
      case "StringKeyword"                         => Right("String")
      case "BooleanKeyword"                        => Right("Boolean")
      case "TypeReference" if t.children.size == 1 =>
        t.children.head.text.filter(n => ctorSchema.get(n).isDefined || memberIndex.knowsType(n)).toRight("TypeReference")
      case other => Left(other)

  protected def extractElementType(tpe: String): Option[String] =
    val bracketIdx = tpe.indexOf('[')
    if bracketIdx < 0 then None
    else
      val prefix = tpe.substring(0, bracketIdx)
      if Set("Array", "ArrayBuffer", "List", "Seq", "IndexedSeq", "Vector").contains(prefix) ||
        tpe.startsWith("scala.collection.mutable.ArrayBuffer")
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

  protected def refuse(reason: String): Unit =
    refusals += reason

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
  protected def checkTypeCompatibility(have: String, want: String): Unit =
    val haveBase = have.takeWhile(c => c != '[' && c != ' ')
    val wantBase = want.takeWhile(c => c != '[' && c != ' ')
    if wantBase == "Any" || wantBase == "AnyRef" || haveBase == "Any" ||
      haveBase == "Nothing" || wantBase == "Nothing" || haveBase == "Null"
    then ()
    else if haveBase == wantBase then
      // Same container: compare element types when both are parameterized
      val haveElem = extractElementType(have)
      val wantElem = extractElementType(want)
      (haveElem, wantElem) match
        case (Some(he), Some(we)) => checkTypeCompatibility(he, we)
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
