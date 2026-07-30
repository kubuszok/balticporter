/*
 * HANDWRITTEN OVERRIDE — Baltic Porter PLAN §7 whole-file override.
 *
 * Ported from: flexmark-java/flexmark-util-ast/src/main/java/com/vladsch/flexmark/util/ast/BlockNodeVisitor.java
 * Original license: BSD-2-Clause (see flexmark-java upstream)
 *
 * Why hand-ported: subclass of NodeVisitor carrying the same
 * `VisitHandler...` / `VisitHandler[]...` constructor collision under Scala erasure,
 * with the flat-varargs super call mistranslated. Routes each form through the base;
 * the redundant array-varargs backward-compat constructor (unused in the closure)
 * is dropped.
 */
package com.vladsch.flexmark.util.ast

class BlockNodeVisitor extends NodeVisitor {

  def this(handlers: VisitHandler[?]*) = {
    this()
    super.addActionHandlers(handlers.toArray.asInstanceOf[Array[VisitHandler[Node]]])
  }

  def this(handlers: java.util.Collection[VisitHandler[Node]]) = {
    this()
    addHandlers(handlers)
  }

  override def processNode(
      node: Node,
      withChildren: Boolean,
      processor: java.util.function.BiConsumer[Node, Visitor[Node]],
  ): Unit =
    if node.isInstanceOf[Block] then super.processNode(node, withChildren, processor)
}
