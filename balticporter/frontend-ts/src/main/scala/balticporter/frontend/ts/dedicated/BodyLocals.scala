package balticporter.frontend.ts.dedicated

import balticporter.frontend.ts.RastNode
import BodySyntax.*

/** A JS local declared with no initialiser is `undefined` until assigned. Where every path assigns it before anything reads it, no read can see that `undefined`, so the Scala `var` may start from any
  * value of its type: the local is typed by its annotation or by what each assignment stores, which must agree.
  */
private[dedicated] trait BodyLocals extends BodyScope:

  /** The type of the uninitialised local `name` declared by `decl` when every path through the statements after it assigns the local before reading it; none otherwise. */
  protected def definitelyAssignedType(decl: RastNode, name: String, annotated: Option[String]): Option[String] =
    val stmt = Iterator.iterate(Option(decl))(_.flatMap(parentOf)).takeWhile(_.isDefined).flatten.find(_.kind == "VariableStatement")
    for
      s <- stmt
      block <- parentOf(s)
      rest = block.children.dropWhile(c => !(c eq s)).drop(1)
      stored <- assignsFirst(rest, name)
      tpe <- annotated.orElse(stored.distinct match
        case List(t) => Some(t)
        case _       => None)
      if stored.forall(_ == tpe) || annotated.isDefined
    yield tpe

  /** `Double` for a local initialised with an integer literal that the body later stores a fractional value into (`x = y / 2`, `x += 0.5`): every JS number is a double, and a Scala `Int` local could
    * not hold it. None where every store is integral or of unknown type.
    */
  protected def widenedNumber(name: String): Option[String] =
    def walk(n: RastNode): Boolean =
      val stores = n.kind == "BinaryExpression" && n.children.size >= 2 && n.children.head.kind == "Identifier" && n.children.head.text.contains(name) &&
        n.operator.exists(op => op == "EqualsToken" || op == "PlusEqualsToken" || op == "MinusEqualsToken" || op == "AsteriskEqualsToken" || op == "SlashEqualsToken")
      (stores && (n.operator.contains("SlashEqualsToken") || storedType(n.children.last, 0).exists(t => t == "Double" || t == "Float"))) || n.children.exists(walk)
    // read as the integer it starts as, so that `x = x + 1` stays integral and `x = x + 0.5` does not
    val sc    = snakeToCamel(name)
    val saved = localTypes.get(sc)
    localTypes(sc) = "Int"
    try Option.when(walk(entry.bodyNode))("Double")
    finally saved.fold(localTypes.remove(sc))(localTypes.put(sc, _))

  // the type of a stored value, a body local the translation has not reached yet read off its one initialiser
  private def storedType(n: RastNode, depth: Int): Option[String] =
    exprType(n).orElse {
      if n.kind != "Identifier" || depth > 3 then None
      else
        val decls = allDeclarations.filter(d => d.children.headOption.exists(c => c.kind == "Identifier" && c.text == n.text))
        decls match
          case List(d) if !assignedAnywhere.contains(n.text.getOrElse("")) =>
            d.children.drop(1).filterNot(isTypeNode).headOption.flatMap(storedType(_, depth + 1))
          case _ => None
    }

  // every binder the body declares, in source order
  private lazy val allDeclarations: List[RastNode] =
    def walk(n: RastNode): Iterator[RastNode] =
      (if Set("VariableDeclaration", "Parameter", "BindingElement", "FunctionDeclaration")(n.kind) then Iterator.single(n) else Iterator.empty) ++ n.children.iterator.flatMap(walk)
    walk(entry.bodyNode).toList

  /** A value of `tpe` that no read observes: the local holds it only until the first assignment. */
  protected def unobservedValue(tpe: String): String =
    baseName(tpe) match
      case "Int" | "Long" | "Short" | "Byte" => "0"
      case "Double" | "Float"                => "0.0"
      case "Boolean"                         => "false"
      case "Char"                            => "'\\u0000'"
      case "Nullable"                        => "Nullable.Null"
      case "Option"                          => "None"
      case _                                 => "null"

  // the types stored by the first statements that mention `name`, when each path assigns it before any read; none when a read, a capture or a branch that can skip the assignment comes first
  private def assignsFirst(stmts: List[RastNode], name: String): Option[List[String]] =
    stmts match
      case Nil       => None
      case s :: rest =>
        // a path that returns or throws first reads nothing afterwards
        if !mentions(s, name) then if leavesFunction(s) then Some(Nil) else assignsFirst(rest, name)
        else
          s.kind match
            case "ExpressionStatement" => s.children.headOption.flatMap(e => storeOf(unparenthesized(e), name))
            case "Block"               => assignsFirst(s.children, name)
            // both branches assign, and the condition reads nothing of it
            case "IfStatement" if s.children.size >= 3 && !mentions(s.children.head, name) =>
              for
                a <- assignsFirst(branch(s.children(1)), name)
                b <- assignsFirst(branch(s.children(2)), name)
              yield a ++ b
            case _ => None

  private def branch(n: RastNode): List[RastNode] = if n.kind == "Block" then n.children else List(n)

  private def leavesFunction(s: RastNode): Boolean =
    s.kind match
      case "ReturnStatement" | "ThrowStatement"  => true
      case "Block"                               => s.children.lastOption.exists(leavesFunction)
      case "IfStatement" if s.children.size >= 3 => leavesFunction(s.children(1)) && leavesFunction(s.children(2))
      case _                                     => false

  // `name = e` where `e` reads nothing of `name`: the type `e` has
  private def storeOf(e: RastNode, name: String): Option[List[String]] =
    if e.kind == "BinaryExpression" && e.operator.contains("EqualsToken") && e.children.size >= 2 &&
      e.children.head.kind == "Identifier" && e.children.head.text.contains(name) && !mentions(e.children.last, name)
    then exprType(e.children.last).filter(_ != "Null").map(List(_))
    else None

  private def mentions(n: RastNode, name: String): Boolean =
    (n.kind == "Identifier" && n.text.contains(name)) || n.children.exists(mentions(_, name))
