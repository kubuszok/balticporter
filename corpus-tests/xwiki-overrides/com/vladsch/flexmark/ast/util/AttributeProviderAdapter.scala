/*
 * HANDWRITTEN OVERRIDE — Baltic Porter PLAN §7 whole-file override.
 *
 * Ported from: flexmark-java/flexmark/src/main/java/com/vladsch/flexmark/ast/util/AttributeProviderAdapter.java
 * Original license: BSD-2-Clause (see flexmark-java upstream)
 *
 * Why hand-ported: the Java class carries both `H...` and `H[]...` varargs
 * overloads (distinct in Java by array dimension) over the raw type
 * AttributeProvidingHandler. Under Scala erasure `H*` and `Array[H]*` both reduce
 * to `Seq`, so the constructor/method set collides (E120) and the flat-varargs
 * super call mistranslates (E007) — the F-bounded AstActionHandler base takes
 * `addActionHandlers(Array[H]*)`, so a Java `H[]` argument is ONE element, not a
 * spread. Every form is routed through the base without an erasure collision.
 */
package com.vladsch.flexmark.ast.util

import com.vladsch.flexmark.html.renderer.AttributablePart
import com.vladsch.flexmark.util.ast.Node
import com.vladsch.flexmark.util.html.MutableAttributes
import com.vladsch.flexmark.util.visitor.AstActionHandler

class AttributeProviderAdapter
    extends AstActionHandler[
      AttributeProviderAdapter,
      Node,
      AttributeProvidingHandler.AttributeProvidingVisitor[Node],
      AttributeProvidingHandler[Node],
    ](Node.AST_ADAPTER)
    with AttributeProvidingHandler.AttributeProvidingVisitor[Node] {

  def this(handlers: AttributeProvidingHandler[Node]*) = {
    this()
    // Java `H[] handlers` passed to base `addActionHandlers(H[]...)` = one element
    super.addActionHandlers(handlers.toArray)
  }

  def this(handlers: java.util.Collection[AttributeProvidingHandler[Node]]) = {
    this()
    addHandlers(handlers)
  }

  def addHandlers(handlers: java.util.Collection[AttributeProvidingHandler[Node]]): AttributeProviderAdapter =
    super.addActionHandlers(handlers.toArray(AttributeProviderAdapter.EMPTY_HANDLERS))

  def addHandlers(handlers: AttributeProvidingHandler[Node]*): AttributeProviderAdapter =
    super.addActionHandlers(handlers.toArray)

  def addHandler(handler: AttributeProvidingHandler[Node]): AttributeProviderAdapter =
    super.addActionHandler(handler)

  override def setAttributes(node: Node, part: AttributablePart, attributes: MutableAttributes): Unit =
    processNode(node, false, (n, handler) => handler.setAttributes(n, part, attributes))
}

object AttributeProviderAdapter {
  protected[util] val EMPTY_HANDLERS: Array[AttributeProvidingHandler[Node]] =
    new Array[AttributeProvidingHandler[Node]](0)
}
