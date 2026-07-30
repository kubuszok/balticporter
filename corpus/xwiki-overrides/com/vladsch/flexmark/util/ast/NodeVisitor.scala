/*
 * HANDWRITTEN OVERRIDE — Baltic Porter PLAN §7 whole-file override.
 *
 * Ported from: flexmark-java/flexmark-util-ast/src/main/java/com/vladsch/flexmark/util/ast/NodeVisitor.java
 * Original license: BSD-2-Clause (see flexmark-java upstream)
 *
 * Why hand-ported: same AstActionHandler family as the *Adapter classes — the
 * `VisitHandler...` / `VisitHandler[]...` constructors collide under Scala erasure,
 * and the flat-varargs super calls mistranslate (the base takes
 * `addActionHandlers(Array[H]*)`, so a Java `H[]` argument is ONE element). Every
 * form is routed through the base; the redundant array-varargs backward-compat
 * constructor (unused in the closure) is dropped.
 */
package com.vladsch.flexmark.util.ast

import com.vladsch.flexmark.util.visitor.AstActionHandler

class NodeVisitor
    extends AstActionHandler[NodeVisitor, Node, Visitor[Node], VisitHandler[Node]](Node.AST_ADAPTER)
    with NodeVisitHandler {

  // raw `VisitHandler...` in Java accepts heterogeneous handlers (VisitHandler[Text],
  // VisitHandler[HtmlEntity], ...) — a wildcard param, cast to the base's H array
  def this(handlers: VisitHandler[?]*) = {
    this()
    super.addActionHandlers(handlers.toArray.asInstanceOf[Array[VisitHandler[Node]]])
  }

  def this(handlers: java.util.Collection[VisitHandler[Node]]) = {
    this()
    addHandlers(handlers)
  }

  // add handler variations
  def addTypedHandlers(handlers: java.util.Collection[VisitHandler[?]]): NodeVisitor =
    super.addActionHandlers(handlers.toArray(NodeVisitor.EMPTY_HANDLERS))

  def addHandlers(handlers: java.util.Collection[VisitHandler[Node]]): NodeVisitor =
    super.addActionHandlers(handlers.toArray(NodeVisitor.EMPTY_HANDLERS))

  def addHandlers(handlers: Array[VisitHandler[Node]]): NodeVisitor =
    super.addActionHandlers(handlers)

  def addHandlers(handlers: Array[VisitHandler[Node]]*): NodeVisitor =
    super.addActionHandlers(handlers*)

  def addHandler(handler: VisitHandler[Node]): NodeVisitor =
    super.addActionHandler(handler)

  final override def visit(node: Node): Unit =
    processNode(node, true, (n, handler) => visit(n, handler))

  final override def visitNodeOnly(node: Node): Unit =
    processNode(node, false, (n, handler) => visit(n, handler))

  final override def visitChildren(parent: Node): Unit =
    processChildren(parent, (n, handler) => visit(n, handler))

  private def visit(node: Node, handler: Visitor[Node]): Unit =
    handler.visit(node)
}

object NodeVisitor {
  protected[ast] val EMPTY_HANDLERS: Array[VisitHandler[Node]] =
    new Array[VisitHandler[Node]](0)
}
