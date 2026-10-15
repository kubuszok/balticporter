package balticporter.frontend.ts.dedicated

import balticporter.frontend.ts.RastNode
import BodySyntax.*

/** A JS array the body builds in a local and then hands to a slot the reference declares as an immutable collection. JS hands over the array itself; Scala must copy the buffer, and the copy is
  * unobservable only where the hand-over is the local's LAST use and nothing before it let the array escape.
  */
private[dedicated] trait BodyArrays extends BodyScope:

  // array methods that answer the receiver itself, so their result aliases the array
  private val aliasingMethods = Set("sort", "reverse", "fill", "copyWithin")

  // array methods that call their callback before they return and keep no reference to it
  private val synchronousCallbacks = Set("forEach", "map", "filter", "some", "every", "find", "findIndex", "reduce", "reduceRight", "flatMap")

  private val loopKinds     = Set("ForStatement", "ForInStatement", "ForOfStatement", "WhileStatement", "DoStatement")
  private val functionKinds = Set("ArrowFunction", "FunctionExpression", "FunctionDeclaration", "MethodDeclaration")

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
