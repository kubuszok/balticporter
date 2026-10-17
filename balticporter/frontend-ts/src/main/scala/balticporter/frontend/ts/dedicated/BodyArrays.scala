package balticporter.frontend.ts.dedicated

import balticporter.frontend.ts.RastNode
import BodySyntax.*

/** A JS array the body builds in a local and then hands to a slot the reference declares as an immutable collection. JS hands over the array itself; Scala must copy the buffer, and the copy is
  * unobservable only where the hand-over is the local's LAST use and nothing before it let the array escape.
  */
private[dedicated] trait BodyArrays extends BodyBounds:

  // array methods that answer the receiver itself, so their result aliases the array
  private val aliasingMethods = Set("sort", "reverse", "fill", "copyWithin")

  // array methods that call their callback before they return and keep no reference to it
  private val synchronousCallbacks = Set("forEach", "map", "filter", "some", "every", "find", "findIndex", "reduce", "reduceRight", "flatMap")

  private val loopKinds     = Set("ForStatement", "ForInStatement", "ForOfStatement", "WhileStatement", "DoStatement")
  private val functionKinds = Set("ArrowFunction", "FunctionExpression", "FunctionDeclaration", "MethodDeclaration")

  /** `xs[i]` as JS reads it: a tuple record's field, a module object's `apply`, or an element or a map value, where JS answers `undefined` past the end or for an absent key and Scala throws, so a
    * read of references answers null there and any other read needs a length or key test on the same receiver around it.
    */
  protected def translateElementAccess(node: RastNode): String =
    val children = node.children
    val field    = if children.size >= 2 then tupleField(children.head, children(1)) else None
    if field.isDefined then s"${translateExpr(children.head)}.${field.get._2}"
    else if children.size < 2 then "??? /* bad element access */"
    else if objectApply(children.head).isDefined then
      // what the reference's `apply` answers for a key, absent ones included
      if isWriteTarget(node) then refuse("element-store-on-object")
      s"${translateExpr(children.head)}(${translateExpr(children(1))})"
    else
      // indexing reads a collection, a map or a string; on a declared class (a tuple the port reshaped) it reads nothing
      exprType(children.head).foreach { t =>
        if isSpecificClassType(t) || isOptionLike(t) then refuse("element-access-on-class")
      }
      // a JS index is a number; a Scala index must be an Int
      if exprType(children(1)).exists(t => t == "Double" || t == "Float") then refuse("fractional-index")
      // a JS string indexes to a one-character string, or `undefined` past its end; Scala's to a Char, or a throw
      if exprType(children.head).contains("String") then refuse("js-string-index")
      val obj      = translateExpr(children.head)
      val idx      = translateExpr(children(1))
      val recvType = exprType(children.head)
      val element  = recvType.flatMap(extractElementType)
      val value    = recvType.flatMap(mapValueType)
      // a key of the map's key type: an optional one is a key JS read after a guard Scala does not see
      for t <- recvType; k <- mapKeyType(t); have <- exprType(children(1)) do checkTypeCompatibility(have, k)
      if isWriteTarget(node) then
        // JS grows an array stored to at or past its end, where Scala throws: a length test on the same receiver must prove the index inside it
        if element.isDefined && !indexProven(node, children.head, children(1)) && !tupleProven(children.head, children(1)) then refuse("write-past-end")
        if recvType.exists(t => handOverConversions.contains(baseName(t))) then refuse("mutated-immutable-collection")
        s"$obj($idx)"
      else if element.exists(absentIsNull) then s"$obj.lift($idx).orNull"
      else if value.exists(absentIsNull) then s"$obj.getOrElse($idx, null)"
      else
        val proven =
          if value.isDefined then keyProven(node, children.head, children(1))
          else if element.isDefined then indexProven(node, children.head, children(1)) || tupleProven(children.head, children(1))
          else indexProven(node, children.head, children(1)) || tupleProven(children.head, children(1)) || keyProven(node, children.head, children(1))
        if !proven then refuse("read-past-end")
        s"$obj($idx)"

  /** JS `xs.splice(start, count, ...items)` on a buffer: a negative start counts from the end, both bounds are clamped into the array, one argument removes to the end, the items are inserted where
    * the removed run was, and the removed elements are the answer. Every argument is evaluated, a spread copied, before the array changes. A bound must be a number; anything else is refused.
    */
  protected def translateSplice(node: RastNode, obj: RastNode, objExpr: String, args: List[RastNode], scalaArgs: List[String]): String =
    def bound(i: Int): String =
      val t = args.lift(i).flatMap(exprType)
      if !args.lift(i).exists(a => a.kind != "SpreadElement" && (intLiteral(a).isDefined || t.exists(Set("Int", "Double", "Float")))) then refuse("js-splice")
      // JS truncates toward zero and reads NaN as 0, as `toInt` does; an infinity saturates and is clamped alike
      if t.exists(x => x == "Double" || x == "Float") then s"(${scalaArgs.lift(i).getOrElse("0")}).toInt" else scalaArgs.lift(i).getOrElse("0")
    if !exprType(obj).exists(isMutableBuffer) || args.isEmpty then
      refuse("js-splice")
      s"$objExpr.remove(${scalaArgs.mkString(", ")})"
    else
      val (b, s0, d0, s, d, r, p) = (freshLabel("b"), freshLabel("s"), freshLabel("c"), freshLabel("i"), freshLabel("d"), freshLabel("r"), freshLabel("p"))
      val items                   = args.drop(2).zip(scalaArgs.drop(2)).map { (a, text) =>
        val n = freshLabel("a")
        if a.kind == "SpreadElement" then
          val from = a.children.headOption.flatMap(exprType).flatMap(extractElementType)
          if from.isEmpty then refuse("array-spread")
          for f <- from; e <- exprType(obj).flatMap(extractElementType) do checkTypeCompatibility(f, e)
          (n, s"val $n = ${text.stripSuffix("*")}.toList", true)
        else (n, s"val $n = $text", false)
      }
      val head =
        s"val $b = $objExpr; val $s0: Int = ${bound(0)}; ${if args.size >= 2 then s"val ${d0}: Int = ${bound(1)}; " else ""}${items.map(_._2).mkString("", "; ", if items.isEmpty then "" else "; ")}" +
          s"val $s = if ($s0 < 0) math.max($b.length + $s0, 0) else math.min($s0, $b.length); " +
          s"val $d = ${if args.size >= 2 then s"math.max(0, math.min($d0, $b.length - $s))" else s"$b.length - $s"}; "
      val used    = statementRoot ne node
      val removed = if used then s"val $r = $b.slice($s, $s + $d); " else ""
      val inserts =
        if items.isEmpty then ""
        else
          s"var $p = $s; " + items
            .map { (n, _, spread) =>
              if spread then s"$b.insertAll($p, $n); $p += $n.length" else s"$b.insert($p, $n); $p += 1"
            }
            .mkString("; ") + "; "
      s"{ $head$removed$b.remove($s, $d); $inserts${if used then r else "()"} }"

  /** The truthiness of a read `xs[i]` or `m[k]` of primitive elements that no guard proves present: JS reads an absent one as `undefined`, which is falsy, so the test asks for a present truthy
    * element (`xs.lift(i).exists(…)`). None for any other read, which renders as before.
    */
  protected def absentFalsyRead(node: RastNode): Option[() => String] =
    val children = node.children
    if node.kind != "ElementAccessExpression" || children.size < 2 || isWriteTarget(node) then None
    else
      val (recv, idx) = (children.head, children(1))
      val recvType    = exprType(recv)
      val plain       =
        tupleField(recv, idx).isDefined || objectApply(recv).isDefined || recvType.contains("String") ||
          recvType.exists(t => isSpecificClassType(t) || isOptionLike(t)) || exprType(idx).exists(t => t == "Double" || t == "Float")
      val element = recvType.flatMap(extractElementType)
      val value   = recvType.flatMap(mapValueType)
      val read    = element.map(("lift", _)).orElse(value.map(("get", _))).filterNot((_, t) => absentIsNull(t))
      val proven  =
        if value.isDefined then keyProven(node, recv, idx) else indexProven(node, recv, idx) || tupleProven(recv, idx)
      read.filter(_ => !plain && !proven).map { (how, t) => () =>
        for rt <- recvType; k <- mapKeyType(rt); have <- exprType(idx) do checkTypeCompatibility(have, k)
        val e = freshLabel("e")
        s"${translateExpr(recv)}.$how(${translateExpr(idx)}).exists($e => ${truthinessTest(t, e)})"
      }

  /** The buffer type of `const xs = []` whose last read is the body's own `return xs`, read off the declared result: JS returns the array itself, and a buffer of the result's element type holds every
    * element the result admits. None where the local or that return sits in a nested function, or the result names no element type the body can write.
    */
  protected def returnedBufferType(decl: RastNode, name: String): Option[String] =
    val uses                     = allNodes.filter(n => n.kind == "Identifier" && n.text.contains(name) && !decl.children.headOption.exists(_ eq n) && isRead(n)).toList
    def atBodyLevel(n: RastNode) = !ancestors(n).exists(a => functionKinds(a.kind))
    for
      last <- uses.lastOption
      if atBodyLevel(decl) && atBodyLevel(last) && parentOf(last).exists(_.kind == "ReturnStatement")
      rt <- declaredReturnType
      if handOverConversions.contains(baseName(rt)) || baseName(rt) == "ArrayBuffer"
      e <- extractElementType(rt)
      if writableType(e)
    yield s"scala.collection.mutable.ArrayBuffer[$e]"

  /** The local `id` reads, copied into the immutable collection `slot` declares; refused unless the copy is unobservable. */
  protected def handOver(id: RastNode, rendered: String, slot: String): String =
    if !isLastUseOfFreshLocal(id) then refuse("array-hand-over-not-last-use")
    s"$rendered.${handOverConversions(baseName(slot))}"

  private def ancestors(n: RastNode): List[RastNode] =
    Iterator.iterate(parentOf(n))(p => p.flatMap(parentOf)).takeWhile(_.isDefined).flatten.toList

  private def encloses(outer: RastNode, inner: RastNode): Boolean = ancestors(inner).exists(_ eq outer)

  private def allNodes: Iterator[RastNode] =
    def walk(n: RastNode): Iterator[RastNode] = Iterator.single(n) ++ n.children.iterator.flatMap(walk)
    walk(entry.bodyNode)

  /** True when `id` is the last read of a local the body declares exactly once, initialised with a fresh array literal, and every earlier read is a member read, an element access or a `for … of`,
    * with no loop or function around `id` that the declaration is outside of, and any closure holding an earlier read run by an array method before it returns.
    */
  private def isLastUseOfFreshLocal(id: RastNode): Boolean =
    id.text.exists { name =>
      val binders = allNodes
        .filter(n => Set("VariableDeclaration", "Parameter", "BindingElement", "FunctionDeclaration")(n.kind) && n.children.headOption.exists(c => c.kind == "Identifier" && c.text.contains(name)))
        .toList
      binders match
        case List(decl) if decl.kind == "VariableDeclaration" && decl.children.lastOption.exists(_.kind == "ArrayLiteralExpression") =>
          // in source order, which a pre-order walk of the tree keeps
          val uses = allNodes.filter(n => n.kind == "Identifier" && n.text.contains(name) && !(n eq decl.children.head) && isRead(n)).toList
          uses.lastOption.exists(_ eq id) &&
          ancestors(id).filter(a => loopKinds(a.kind) || functionKinds(a.kind)).forall(a => encloses(a, decl)) &&
          uses.filterNot(_ eq id).forall(u => staysLocal(u) && closuresRunInPlace(u, decl))
        case _ => false
    }

  // an identifier in a property name's or an object key's place names no binding
  private def isRead(n: RastNode): Boolean =
    parentOf(n) match
      case Some(p) if p.kind == "PropertyAccessExpression" => p.children.headOption.exists(_ eq n)
      case Some(p) if p.kind == "PropertyAssignment"       => !p.children.headOption.exists(_ eq n)
      case _                                               => true

  // a read that uses the array without handing it anywhere: a member read, an element access, the iterable of a `for … of`
  private def staysLocal(u: RastNode): Boolean =
    parentOf(u) match
      case Some(p) if p.kind == "PropertyAccessExpression" => !p.children.lastOption.flatMap(_.text).exists(aliasingMethods)
      case Some(p) if p.kind == "ElementAccessExpression"  => p.children.headOption.exists(_ eq u)
      case Some(p) if p.kind == "ForOfStatement"           => p.children.lift(1).exists(_ eq u)
      case _                                               => false

  // every function around `u` that the declaration is outside of is a callback an array method runs before it returns
  private def closuresRunInPlace(u: RastNode, decl: RastNode): Boolean =
    ancestors(u).filter(a => functionKinds(a.kind) && !encloses(a, decl)).forall { f =>
      parentOf(f).exists { call =>
        call.kind == "CallExpression" && !call.children.headOption.exists(_ eq f) && call.children.headOption.exists { callee =>
          callee.kind == "PropertyAccessExpression" && callee.children.size >= 2 && callee.children.last.text.exists(synchronousCallbacks) &&
          exprType(callee.children.head).exists(t => extractElementType(t).isDefined)
        }
      }
    }
