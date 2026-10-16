package balticporter.frontend.ts.dedicated

import balticporter.frontend.ts.{ RastNode, RastValue }

/** Whether a read is PROVEN to stay inside its receiver. JS answers `undefined` past an array's end or for an absent key where Scala throws, so such a read is translated only under a condition that
  * bounds that very index on that very receiver (`i < xs.length`, `xs.length > 2`, `m.has(k)`), on the branch where the condition holds, with nothing run between the test and the read able to move
  * the index, the key or the receiver: no write to them, and no call that might shrink an array or drop a key.
  */
private[dedicated] trait BodyBounds extends BodyScope:

  // an integer sum: receivers' lengths and variables by shape, each with its coefficient, and a constant
  private final case class Lin(lens: Map[String, Int], vars: Map[String, Int], c: Int):
    def +(o: Lin): Lin = Lin(merge(lens, o.lens), merge(vars, o.vars), c + o.c)
    def unary_- : Lin  = Lin(lens.view.mapValues(-_).toMap, vars.view.mapValues(-_).toMap, -c)
    def -(o: Lin): Lin = this + (-o)
    def plus(k: Int): Lin = copy(c = c + k)
    def isConst: Boolean = lens.isEmpty && vars.isEmpty

  private def merge(a: Map[String, Int], b: Map[String, Int]): Map[String, Int] =
    (a.keySet ++ b.keySet).iterator.map(k => k -> (a.getOrElse(k, 0) + b.getOrElse(k, 0))).filter(_._2 != 0).toMap

  // what a condition proves where it holds: the sum is positive, or the receiver shaped `recv` holds the key shaped `key`
  private enum Fact:
    case Positive(sum: Lin)
    case Holds(recv: String, key: String)

  private def linear(n: RastNode): Option[Lin] =
    n.kind match
      case "ParenthesizedExpression"              => n.children.headOption.flatMap(linear)
      case "NumericLiteral" | "FirstLiteralToken" =>
        n.value.collect { case RastValue.Num(d) if d == d.toInt.toDouble => Lin(Map.empty, Map.empty, d.toInt) }
      case "PrefixUnaryExpression" if n.operator.contains("MinusToken") => n.children.headOption.flatMap(linear).map(-_)
      case "PropertyAccessExpression" if n.children.size == 2 && n.children.last.text.contains("length") && isPath(n.children.head) =>
        Some(Lin(Map(shapeOf(n.children.head) -> 1), Map.empty, 0))
      case "Identifier"                               => Some(Lin(Map.empty, Map(shapeOf(n) -> 1), 0))
      case "BinaryExpression" if n.children.size == 2 =>
        for
          l <- linear(n.children.head)
          r <- linear(n.children(1))
          s <- n.operator.collect { case "PlusToken" => l + r; case "MinusToken" => l - r }
        yield s
      case _ => None

  // a receiver read through names and member reads alone, which reads alike wherever nothing writes it
  private def isPath(n: RastNode): Boolean =
    n.kind match
      case "Identifier" | "ThisKeyword"                       => true
      case "PropertyAccessExpression" if n.children.size == 2 => isPath(n.children.head)
      case _                                                  => false

  // the path itself and every path it is read through: writing any of them moves it
  private def pathPrefixes(n: RastNode): List[String] =
    shapeOf(n) :: (if n.kind == "PropertyAccessExpression" then n.children.headOption.toList.flatMap(pathPrefixes) else Nil)

  /** What `cond` evaluating to `holds` proves. A `<=` is read as `< + 1`: every value it compares in a proof is an integer (a length, an integer constant, or an index variable the proof requires to be
    * an integer counter).
    */
  private def facts(cond: RastNode, holds: Boolean): List[Fact] =
    def cmp(op: String, a: Lin, b: Lin): List[Lin] =
      (op, holds) match
        case ("LessThanToken", true) | ("GreaterThanEqualsToken", false)        => List(b - a)
        case ("GreaterThanToken", true) | ("LessThanEqualsToken", false)        => List(a - b)
        case ("LessThanEqualsToken", true) | ("GreaterThanToken", false)        => List((b - a).plus(1))
        case ("GreaterThanEqualsToken", true) | ("LessThanToken", false)        => List((a - b).plus(1))
        case ("EqualsEqualsEqualsToken" | "EqualsEqualsToken", true)            => List((a - b).plus(1), (b - a).plus(1))
        case ("ExclamationEqualsEqualsToken" | "ExclamationEqualsToken", false) => List((a - b).plus(1), (b - a).plus(1))
        // a length other than zero is positive
        case ("ExclamationEqualsEqualsToken" | "ExclamationEqualsToken", true) | ("EqualsEqualsEqualsToken" | "EqualsEqualsToken", false) =>
          List(a - b, b - a).filter(d => d.vars.isEmpty && d.c == 0 && d.lens.size == 1 && d.lens.values.forall(_ == 1))
        case _ => Nil
    cond.kind match
      case "ParenthesizedExpression"                                                         => cond.children.headOption.toList.flatMap(facts(_, holds))
      case "PrefixUnaryExpression" if cond.operator.contains("ExclamationToken")             => cond.children.headOption.toList.flatMap(facts(_, !holds))
      case "BinaryExpression" if cond.operator.contains("AmpersandAmpersandToken") && holds => cond.children.flatMap(facts(_, true))
      case "BinaryExpression" if cond.operator.contains("BarBarToken") && !holds            => cond.children.flatMap(facts(_, false))
      case "BinaryExpression" if cond.operator.contains("InKeyword") =>
        if holds && cond.children.size == 2 && isPath(cond.children(1)) then List(Fact.Holds(shapeOf(cond.children(1)), shapeOf(cond.children.head))) else Nil
      case "BinaryExpression" if cond.children.size == 2 =>
        for
          op <- cond.operator.toList
          a  <- linear(cond.children.head).toList
          b  <- linear(cond.children(1)).toList
          d  <- cmp(op, a, b)
        yield Fact.Positive(d)
      case "CallExpression" if holds && cond.children.size == 2 =>
        cond.children.head match
          case c if c.kind == "PropertyAccessExpression" && c.children.size == 2 && c.children.last.text.contains("has") && isPath(c.children.head) =>
            List(Fact.Holds(shapeOf(c.children.head), shapeOf(cond.children(1))))
          case _ => Nil
      // a length read as a condition is its own truthiness: positive
      case "PropertyAccessExpression" if holds => linear(cond).toList.filter(_.lens.nonEmpty).map(Fact.Positive(_))
      case _                                   => Nil

  private val functionKinds = Set("ArrowFunction", "FunctionExpression", "FunctionDeclaration", "MethodDeclaration", "GetAccessor", "SetAccessor", "Constructor")
  private val loopKinds     = Set("ForStatement", "ForInStatement", "ForOfStatement", "WhileStatement", "DoStatement")

  private def ancestorsOf(n: RastNode): List[RastNode] = Iterator.iterate(parentOf(n))(_.flatMap(parentOf)).takeWhile(_.isDefined).flatten.toList

  private def subtree(n: RastNode): List[RastNode] = n :: n.children.flatMap(subtree)

  // a `for`'s condition: the first child after its initialiser that is not its body, where it has one
  private def forCondition(loop: RastNode): Option[RastNode] =
    val body = loop.children.lastOption
    loop.children.filterNot(c => body.exists(_ eq c)).filterNot(c => c.kind == "VariableDeclarationList" || c.kind == "FirstStatement" || c.kind == "BinaryExpression" && c.operator.contains("EqualsToken")).headOption

  /** Each condition holding wherever `site` is reached, with its polarity and the statements or expressions that run after the test, the one holding `site` last. */
  private def guards(site: RastNode): List[(RastNode, Boolean, List[RastNode])] =
    val chain = site :: ancestorsOf(site)
    chain.zip(chain.drop(1)).flatMap { (child, parent) =>
      parent.kind match
        case "IfStatement" | "ConditionalExpression" if parent.children.size >= 2 && !(parent.children.head eq child) =>
          List((parent.children.head, parent.children(1) eq child, List(child)))
        case "WhileStatement" if parent.children.size == 2 && (parent.children(1) eq child) => List((parent.children.head, true, List(child)))
        case "ForStatement" if parent.children.lastOption.exists(_ eq child)                => forCondition(parent).toList.map(c => (c, true, List(child)))
        case "BinaryExpression" if parent.children.size == 2 && (parent.children(1) eq child) =>
          parent.operator match
            case Some("AmpersandAmpersandToken") => List((parent.children.head, true, List(child)))
            case Some("BarBarToken")             => List((parent.children.head, false, List(child)))
            case _                               => Nil
        // after `if (c) <leave>` with no else, the rest of the block runs where `c` failed
        case "Block" | "CaseClause" | "DefaultClause" =>
          val idx = parent.children.indexWhere(_ eq child)
          parent.children.take(idx).zipWithIndex.collect {
            case (s, i) if s.kind == "IfStatement" && s.children.size == 2 && exitsAlways(s.children(1)) =>
              (s.children.head, false, parent.children.slice(i + 1, idx + 1))
          }
        case _ => Nil
    }

  /** The nodes of `region` that run after the test and before `site`: those ahead of it in source order (its enclosing expressions write only after it is read), and every node of a loop around it
    * inside the region, which runs again before it does.
    */
  private def runBefore(region: List[RastNode], site: RastNode): List[RastNode] =
    val around = ancestorsOf(site)
    val loops  = around.filter(a => loopKinds(a.kind) && region.exists(top => (top eq a) || subtree(top).exists(_ eq a)))
    region.flatMap { top =>
      val all = subtree(top)
      val idx = all.indexWhere(_ eq site)
      if idx < 0 then all
      else
        all.take(idx).filterNot(n => around.exists(_ eq n)) ++
          all.drop(idx + 1).filter(n => loops.exists(l => ancestorsOf(n).exists(_ eq l)))
    }

  private val comparisons = Set("EqualsEqualsToken", "EqualsEqualsEqualsToken", "ExclamationEqualsToken", "ExclamationEqualsEqualsToken", "LessThanEqualsToken", "GreaterThanEqualsToken")

  // the node an assignment, a compound assignment or an increment writes
  private def written(n: RastNode): Option[RastNode] =
    val assigns = n.kind == "BinaryExpression" && n.operator.exists(op => op.endsWith("EqualsToken") && !comparisons(op))
    val steps   = (n.kind == "PostfixUnaryExpression" || n.kind == "PrefixUnaryExpression") && n.operator.exists(op => op == "PlusPlusToken" || op == "MinusMinusToken")
    if assigns || steps then n.children.headOption else None

  // `n` moves the path whose prefixes are `prefixes`: it writes one of them, or the path's `length`
  private def moves(n: RastNode, prefixes: List[String]): Boolean =
    written(n).exists { t =>
      prefixes.contains(shapeOf(t)) ||
      (t.kind == "PropertyAccessExpression" && t.children.size == 2 && t.children.last.text.contains("length") && prefixes.headOption.contains(shapeOf(t.children.head)))
    }

  // methods of a JS array, string or map that change no array's length and remove no key
  private val pureMethods =
    Set("indexOf", "lastIndexOf", "includes", "slice", "concat", "charCodeAt", "charAt", "toString", "startsWith", "endsWith", "trim", "toLowerCase", "toUpperCase", "join", "has", "get", "push")

  // a call or construction that may shrink an array or drop a key: anything but a pure method of a collection or a string, or a `Math` function
  private def mayMutate(n: RastNode): Boolean =
    n.kind match
      case "NewExpression" | "DeleteExpression" => true
      case "CallExpression"                     =>
        n.children.headOption match
          case Some(c) if c.kind == "PropertyAccessExpression" && c.children.size == 2 =>
            val method = c.children.last.text.getOrElse("")
            val recv   = c.children.head
            val known  = exprType(recv).exists(t => t == "String" || extractElementType(t).isDefined || mapValueType(t).isDefined)
            !(pureMethods(method) && known || recv.kind == "Identifier" && recv.text.contains("Math"))
          case _ => true
      case _ => false

  /** The least value of the local `v` reads at `site`, where it is declared once in its scope from a non-negative integer and only ever counts up there; none for any other name. Its scope is the
    * nearest `for` around `site` declaring it with `let`, else the whole body.
    */
  private def counterFloor(v: String, site: RastNode): Option[Int] =
    def declares(n: RastNode) = n.kind == "VariableDeclaration" && n.children.headOption.exists(c => shapeOf(c) == v)
    val loop = ancestorsOf(site).find(a =>
      a.kind == "ForStatement" && a.children.headOption.exists(l => l.kind == "VariableDeclarationList" && l.children.exists(d => declares(d) && d.flags.contains("let")))
    )
    val all   = subtree(loop.getOrElse(entry.bodyNode))
    val decls = all.filter(declares)
    val start = decls match
      case List(d) => d.children.drop(1).lastOption.flatMap(linear).filter(l => l.isConst && l.c >= 0).map(_.c)
      case _       => None
    val upOnly = all.filter(n => written(n).exists(t => shapeOf(t) == v)).forall { w =>
      w.kind match
        case "PostfixUnaryExpression" | "PrefixUnaryExpression"           => w.operator.contains("PlusPlusToken")
        case "BinaryExpression" if w.operator.contains("PlusEqualsToken") => w.children.lift(1).flatMap(linear).exists(l => l.isConst && l.c >= 0)
        case _                                                            => false
    }
    start.filter(_ => upOnly && !entry.params.exists(p => shapeOf(RastNode("Identifier", 0, (0, 0), text = Some(p))) == v))

  // `site` runs in the region itself, never in a function written there that may run anywhere
  private def runsInPlace(site: RastNode, region: List[RastNode]): Boolean =
    !ancestorsOf(site).takeWhile(a => !region.exists(_ eq a)).exists(a => functionKinds(a.kind))

  /** True when `recv[idx]` at `site` is proven inside the receiver: an integer index no lower than zero, bounded by a condition on the same receiver that holds there, nothing moving either in between. */
  protected def indexProven(site: RastNode, recv: RastNode, idx: RastNode): Boolean =
    isPath(recv) && linear(idx).exists { i =>
      val r        = shapeOf(recv)
      val prefixes = pathPrefixes(recv)
      val floor    = i.vars.toList match
        case Nil                       => Some(i.c)
        case List((v, 1))              => counterFloor(v, site).map(_ + i.c)
        case _                         => None
      i.lens.isEmpty && floor.exists(_ >= 0) &&
      guards(site).exists { (cond, holds, region) =>
        runsInPlace(site, region) && !subtree(cond).exists(mayMutate) &&
        facts(cond, holds).exists {
          case Fact.Positive(d) => d.lens == Map(r -> 1) && d.vars == (-i).vars && d.c + i.c <= 0
          case _                => false
        } &&
        runBefore(region, site).forall(h => !mayMutate(h) && !moves(h, prefixes) && i.vars.keys.forall(v => !written(h).exists(t => shapeOf(t) == v)))
      }
    }

  /** True when the key `key` of `recv` at `site` is proven present: a `has` or `in` test of the same key on the same receiver holds there, nothing moving either in between. */
  protected def keyProven(site: RastNode, recv: RastNode, key: RastNode): Boolean =
    isPath(recv) && (isPath(key) || linear(key).exists(_.isConst) || key.kind == "StringLiteral") && {
      val prefixes = pathPrefixes(recv)
      val keyPaths = if isPath(key) then pathPrefixes(key) else Nil
      guards(site).exists { (cond, holds, region) =>
        runsInPlace(site, region) && !subtree(cond).exists(mayMutate) &&
        facts(cond, holds).contains(Fact.Holds(shapeOf(recv), shapeOf(key))) &&
        runBefore(region, site).forall(h => !mayMutate(h) && !moves(h, prefixes) && !moves(h, keyPaths))
      }
    }
