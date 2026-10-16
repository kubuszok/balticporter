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
