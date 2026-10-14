package balticporter.frontend.ts.dedicated

import balticporter.frontend.ts.{ RastNode, RastType, RastValue, ReferenceSignatures }
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
  val tsTypes:            Map[String, RastType]
):
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

  /** Look up the declared Scala type of an expression. Checks locals first (they shadow parameters inside guarded scopes), then parameters; a value the TypeScript checker types as a boolean, with
    * nothing in the reference saying otherwise, is a `Boolean`: its truthiness is its value, and a Scala type that disagrees fails to compile rather than reading differently.
    */
  protected def exprType(node: RastNode): Option[String] =
    declaredType(node).orElse(checkerType(node))

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

  private def declaredType(node: RastNode): Option[String] =
    node.kind match
      case "Identifier" =>
        node.text.flatMap { name =>
          val scalaName = snakeToCamel(name)
          localTypes.get(scalaName).orElse(localTypes.get(name)).orElse(paramTypes.get(scalaName)).orElse(paramTypes.get(name)).orElse {
            // a module-level value the reference declares, when nothing in the body binds the name
            if declaredInBody(name) || entry.params.contains(name) || apiLookup.contains(name) then None
            else calleeIndex.resolveMember(scalaName).toOption.flatMap(_.tpe)
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
              case _ if obj.kind == "Identifier" && obj.text.contains("Math")                                                               =>
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
                else if obj.kind == "ThisKeyword" && memberIndex.knowsType(nodeScalaType) then
                  oracle.get(nodeScalaType, snakeToCamel(method)).map(_.returnType).orElse(memberIndex.methodResult(nodeScalaType, snakeToCamel(method)))
                else exprType(obj).flatMap(t => memberIndex.methodResult(baseName(t), snakeToCamel(method)))
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
        val viaInstance = owner.flatMap { o =>
          ctorSchema
            .get(o)
            .flatMap(_.find(p => p.name == field || p.name == snakeToCamel(field)).map(_.tpe))
            .orElse(memberIndex.memberType(o, field))
            .orElse(memberIndex.memberType(o, snakeToCamel(field)))
        }
        // `Obj.f` where `Obj` is an object the reference declares
        def viaObject = node.children.head.text.filter(_ => node.children.head.kind == "Identifier").flatMap { o =>
          calleeIndex.declared(o, field).orElse(calleeIndex.declared(o, snakeToCamel(field))).flatMap(_.tpe)
        }
        viaInstance.orElse(viaObject)
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
  protected def checkTypeCompatibility(have: String, want: String): Unit =
    val haveBase = baseName(have)
    val wantBase = baseName(want)
    if wantBase == "Any" || wantBase == "AnyRef" || haveBase == "Any" ||
      haveBase == "Nothing" || wantBase == "Nothing" || haveBase == "Null"
    then ()
    // a value that may be absent where the slot requires one: JS read it after a guard Scala does not see
    else if isOptionLike(have) && !isOptionLike(want) && !want.contains("| Null") then refuse("optional-at-required-slot")
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
          case (x @ RastNode("Identifier", _, _, _, _, _, _, _, Some(name), _, _, _)) :: (c @ RastNode("Identifier", _, _, _, _, _, _, _, Some(cls), _, _, _)) :: Nil =>
            val scalaCls   = if conventions.isClassName(cls) then conventions.scalaClassName(cls) else cls
            val narrowable =
              (isLocal(name) || entry.params.contains(name)) && !reassignedNames.contains(name) && !reassignedNames.contains(snakeToCamel(name)) &&
                memberIndex.knowsType(scalaCls) && memberIndex.testable(scalaCls) == scalaCls && !unwrappedIds.contains(snakeToCamel(name))
            if narrowable then List(name -> scalaCls) else Nil
          case _ => Nil
      case _ => Nil

  /** What `cond` proves when it FAILS: `!(x instanceof C)` failing makes `x` a `C`, and so does each operand of a failing `||`. */
  protected def refutedBy(cond: RastNode): List[(String, String)] =
    cond.kind match
      case "ParenthesizedExpression"                                                              => cond.children.headOption.toList.flatMap(refutedBy)
      case "PrefixUnaryExpression" if cond.operator.contains("ExclamationToken")                  => cond.children.headOption.toList.flatMap(narrowedBy)
      case "BinaryExpression" if cond.operator.contains("BarBarToken") && cond.children.size >= 2 =>
        refutedBy(cond.children.head) ++ refutedBy(cond.children.last)
      case _ => Nil

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
    // `.toString` binds tighter than any operator the operand may contain
    val text = if raw.contains(' ') && !(raw.startsWith("(") && raw.endsWith(")")) then s"($raw)" else raw
    exprType(n) match
      case Some("String")                                                     => text
      case Some("Int" | "Long" | "Short" | "Byte" | "Boolean" | "Char")       => s"$text.toString"
      case Some(t) if isSpecificClassType(t) && extractElementType(t).isEmpty => s"$text.toString"
      case Some("Double" | "Float")                                           =>
        refuse("number-to-string")
        s"$text.toString"
      case _ =>
        refuse("value-to-string")
        s"$text.toString"

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
