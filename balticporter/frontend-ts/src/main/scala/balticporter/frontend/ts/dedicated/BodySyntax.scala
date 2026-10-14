package balticporter.frontend.ts.dedicated

import balticporter.frontend.ts.RastNode
import scala.collection.mutable

/** Facts about JS syntax and Scala type spellings the body translator asks while it renders a body: none reads any one body's state. */
private[dedicated] object BodySyntax:

  /** True for a node that spells a TS TYPE (an annotation), never a value. */
  def isTypeNode(node: RastNode): Boolean =
    node.kind.endsWith("Type") || typeKeywordKinds.contains(node.kind) || node.kind == "TypeReference" || node.kind == "TypeLiteral"

  val typeKeywordKinds: Set[String] = Set(
    "NumberKeyword",
    "StringKeyword",
    "BooleanKeyword",
    "AnyKeyword",
    "UnknownKeyword",
    "VoidKeyword",
    "NeverKeyword",
    "ObjectKeyword",
    "BigIntKeyword",
    "SymbolKeyword"
  )

  /** True when the RAST tree contains a ThisKeyword node. */
  def containsThisKeyword(node: RastNode): Boolean =
    node.kind match
      case "ThisKeyword"                                                  => true
      case "ArrowFunction" | "FunctionExpression" | "FunctionDeclaration" => false
      case _                                                              => node.children.exists(containsThisKeyword)

  /** Collect all identifier names that are assigned to (LHS of `=`) in the body. Used to detect JS const variables that are later reassigned, which must become `var` in Scala. Skips nested function
    * scopes.
    */
  def collectReassignedNames(body: RastNode): Set[String] =
    val names = mutable.Set.empty[String]
    def walk(node: RastNode): Unit =
      node.kind match
        case "ArrowFunction" | "FunctionExpression" | "FunctionDeclaration" => ()
        case "BinaryExpression"
            if node.operator.exists(op =>
              op == "EqualsToken" || op == "FirstAssignment" ||
                op == "PlusEqualsToken" || op == "FirstCompoundAssignment" ||
                op == "MinusEqualsToken" || op == "AsteriskEqualsToken" ||
                op == "SlashEqualsToken" || op == "BarEqualsToken" ||
                op == "AmpersandEqualsToken" || op == "PercentEqualsToken"
            ) =>
          node.children.headOption match
            case Some(lhs) if lhs.kind == "Identifier" =>
              lhs.text.foreach(names += _)
            case _ => ()
          node.children.foreach(walk)
        case "PostfixUnaryExpression" | "PrefixUnaryExpression" if node.operator.exists(op => op == "PlusPlusToken" || op == "MinusMinusToken") =>
          node.children.headOption match
            case Some(operand) if operand.kind == "Identifier" =>
              operand.text.foreach(names += _)
            case _ => ()
          node.children.foreach(walk)
        case _ => node.children.foreach(walk)
    walk(body)
    names.toSet

  /** True when the statement list contains a ReturnStatement that will be emitted as a `boundary.break`, meaning the body needs a `scala.util.boundary` wrapper. This includes returns in non-last
    * sibling positions AND returns inside non-tail branches of the last statement (e.g. the then-branch of an if-else, or a non-last case of a switch). A return inside a nested function expression or
    * arrow function is not counted.
    */
  def hasEarlyReturn(stmts: List[RastNode]): Boolean =
    def containsReturn(node: RastNode): Boolean =
      node.kind match
        case "ReturnStatement"                                              => true
        case "ArrowFunction" | "FunctionExpression" | "FunctionDeclaration" => false
        case _                                                              => node.children.exists(containsReturn)
    // A return inside a branch of the last statement that is not itself the tail
    // expression of the whole block: the then-branch of an if-else whose block
    // has more than one statement, or a switch case body.
    def lastStmtHasNonTailReturn(node: RastNode): Boolean =
      node.kind match
        case "IfStatement" =>
          val ch = node.children
          // If branches contain returns that are inside multi-statement bodies
          ch.drop(1).exists { branch =>
            branch.kind match
              case "Block" =>
                val inner = branch.children
                inner.init.exists(containsReturn) || inner.lastOption.exists(lastStmtHasNonTailReturn)
              case _ => false
          }
        case "SwitchStatement" =>
          node.children.exists { child =>
            child.kind == "CaseBlock" && child.children.exists { clause =>
              val body = clause.children.filterNot(c => c.kind == "BreakStatement")
              body.init.exists(containsReturn) || body.lastOption.exists(lastStmtHasNonTailReturn)
            }
          }
        case "Block" =>
          val ch = node.children
          ch.init.exists(containsReturn) || ch.lastOption.exists(lastStmtHasNonTailReturn)
        case _ => false
    val nonLastHasReturn = stmts.size > 1 && stmts.init.exists(containsReturn)
    nonLastHasReturn || stmts.lastOption.exists(lastStmtHasNonTailReturn)

  /** True when the node tree contains a BreakStatement (unlabelled) that targets THIS loop. Stops at nested loops (they own their own breaks) and function boundaries.
    */
  def containsBreak(node: RastNode): Boolean =
    node.kind match
      case "BreakStatement"                                                                        => node.children.find(_.kind == "Identifier").flatMap(_.text).isEmpty
      case "WhileStatement" | "ForStatement" | "ForInStatement" | "ForOfStatement" | "DoStatement" => false
      case "ArrowFunction" | "FunctionExpression" | "FunctionDeclaration"                          => false
      case _                                                                                       => node.children.exists(containsBreak)

  /** True when the node tree contains a ContinueStatement (unlabelled) that targets THIS loop. */
  def containsContinue(node: RastNode): Boolean =
    node.kind match
      case "ContinueStatement"                                                                     => node.children.find(_.kind == "Identifier").flatMap(_.text).isEmpty
      case "WhileStatement" | "ForStatement" | "ForInStatement" | "ForOfStatement" | "DoStatement" => false
      case "ArrowFunction" | "FunctionExpression" | "FunctionDeclaration"                          => false
      case _                                                                                       => node.children.exists(containsContinue)

  /** `snake_case` to `camelCase`; leading underscores are part of the name and are kept (`_offset_opt` is `_offsetOpt`, never `OffsetOpt`). */
  def snakeToCamel(s: String): String =
    val lead  = s.takeWhile(_ == '_')
    val parts = s.drop(lead.length).split("_")
    if parts.length <= 1 then escapeKeyword(s)
    else escapeKeyword(lead + parts.head + parts.tail.map(_.capitalize).mkString)

  def escapeKeyword(name: String): String =
    if scalaKeywords.contains(name) then s"${name}_" else name

  val scalaKeywords: Set[String] = Set(
    "type",
    "val",
    "var",
    "def",
    "class",
    "trait",
    "object",
    "enum",
    "match",
    "case",
    "if",
    "else",
    "for",
    "while",
    "do",
    "return",
    "throw",
    "try",
    "catch",
    "finally",
    "import",
    "export",
    "package",
    "new",
    "this",
    "super",
    "with",
    "extends",
    "yield",
    "abstract",
    "final",
    "sealed",
    "private",
    "protected",
    "override",
    "lazy",
    "implicit",
    "given",
    "using",
    "then",
    "end",
    "inline",
    "opaque",
    "transparent",
    "erased",
    "open",
    "infix"
  )

  /** True when the type string denotes a Nullable type, e.g. `Nullable[Foo]` or `Nullable[Bar[Baz]]`.
    */
  def isNullableType(tpe: String): Boolean =
    tpe.startsWith("Nullable[") || tpe.startsWith("Nullable [")

  /** A value that may be absent and says so by `isDefined`/`get`: `Nullable[T]` or `Option[T]`. */
  def isOptionLike(tpe: String): Boolean =
    isNullableType(tpe) || tpe.startsWith("Option[")

  /** The `T` of an optional type. */
  def optionInner(tpe: String): String =
    tpe.dropWhile(_ != '[').drop(1).stripSuffix("]")

  /** An identifier or a dotted path of them: reading it twice evaluates nothing twice. */
  def isSimplePath(expr: String): Boolean =
    expr.nonEmpty && expr.split('.').forall(p => p.nonEmpty && (p.head.isLetter || p.head == '_' || p.head == '$') && p.forall(c => c.isLetterOrDigit || c == '_' || c == '$'))

  val numericTypes: Set[String] = Set(
    "Int",
    "Long",
    "Double",
    "Float",
    "Short",
    "Byte"
  )

  val integralTypes: Set[String] = Set("Int", "Long", "Short", "Byte")

  /** JS built-in globals whose members the translator rewrites at the use. */
  val jsGlobals: Set[String] = Set(
    "Math",
    "Number",
    "Object",
    "Array",
    "JSON",
    "String",
    "Boolean",
    "Symbol",
    "Date",
    "RegExp",
    "Map",
    "Set",
    "WeakMap",
    "WeakSet",
    "Error",
    "TypeError",
    "RangeError",
    "SyntaxError",
    "ReferenceError",
    "Promise",
    "Console"
  )

  /** The component types of a Scala tuple type `(A, B[C, D])`, split at top-level commas. */
  def tupleComponents(tpe: String): Option[List[String]] =
    val t = tpe.trim
    if !t.startsWith("(") || !t.endsWith(")") then None
    else
      val inner = t.substring(1, t.length - 1)
      val parts = mutable.ListBuffer.empty[String]
      var depth = 0
      var start = 0
      for (c, i) <- inner.zipWithIndex do
        c match
          case '(' | '['         => depth += 1
          case ')' | ']'         => depth -= 1
          case ',' if depth == 0 =>
            parts += inner.substring(start, i).trim
            start = i + 1
          case _ => ()
      parts += inner.substring(start).trim
      if parts.size >= 2 && !inner.contains("=>") then Some(parts.toList) else None

  /** The Scala result type of `Math.<m>(…)` as the body emits it (`java.lang.Math`); `round` answers a Long there. */
  def mathResultType(method: String, argTypes: List[Option[String]]): Option[String] =
    method match
      case "floor" | "ceil" | "sqrt" | "pow" | "random" | "sin" | "cos" | "tan" | "atan" | "atan2" | "asin" | "acos" | "exp" | "log" | "log10" | "hypot" | "cbrt" | "signum" =>
        Some("Double")
      case "round"               => Some("Long")
      case "abs" | "max" | "min" =>
        if argTypes.nonEmpty && argTypes.forall(_.contains("Int")) then Some("Int")
        else if argTypes.forall(_.exists(numericTypes.contains)) then Some("Double")
        else None
      case _ => None

  /** True when the type is a specific class rather than a primitive, Any, String, or a collection type. Property access on such a type needs API-level knowledge that the translator does not have.
    */
  def isSpecificClassType(tpe: String): Boolean =
    val base = tpe.takeWhile(c => c != '[' && c != ' ')
    !trivialTypes.contains(base) && base.head.isUpper

  val trivialTypes: Set[String] = Set(
    "Any",
    "AnyRef",
    "AnyVal",
    "String",
    "Int",
    "Long",
    "Double",
    "Float",
    "Boolean",
    "Byte",
    "Short",
    "Char",
    "Unit",
    "Nothing",
    "Null",
    "Array",
    "ArrayBuffer",
    "List",
    "Seq",
    "Set",
    "Map",
    "Option",
    "Iterator",
    "Iterable",
    "IndexedSeq",
    "Vector",
    "mutable"
  )

  /** JS property names whose Scala translation is always the same regardless of the receiver type. Access to these does not need API verification.
    */
  val knownSafeProperties: Set[String] = Set(
    "length",
    "size",
    "toString",
    "hashCode",
    "getClass",
    "push",
    "pop",
    "indexOf",
    "contains",
    "has",
    "includes",
    "forEach",
    "map",
    "filter",
    "reduce",
    "some",
    "every",
    "find",
    "slice",
    "concat",
    "join",
    "reverse",
    "sort",
    "keys",
    "values",
    "charAt",
    "substring",
    "startsWith",
    "endsWith",
    "replace",
    "split",
    "trim",
    "toLowerCase",
    "toUpperCase"
  )

  /** JS collection methods whose callback returns a Boolean predicate. A lambda argument in this position has its body translated with truthiness lowering.
    */
  val predicateMethods: Set[String] = Set(
    "filter",
    "find",
    "some",
    "every",
    "findIndex"
  )

  def escapeString(s: String): String =
    s.replace("\\", "\\\\").replace("\"", "\\\"").replace("\n", "\\n").replace("\r", "\\r").replace("\t", "\\t")

  /** Escape a string for use as text inside an s"..." interpolator. Dollar signs must be doubled to avoid being read as interpolation.
    */
  def escapeForSString(s: String): String =
    escapeString(s).replace("$", "$$")
