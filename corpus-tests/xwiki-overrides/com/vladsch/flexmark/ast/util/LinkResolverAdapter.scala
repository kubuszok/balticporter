/*
 * HANDWRITTEN OVERRIDE — Baltic Porter PLAN §7 whole-file override.
 *
 * Ported from: flexmark-java/flexmark/src/main/java/com/vladsch/flexmark/ast/util/LinkResolverAdapter.java
 * Original license: BSD-2-Clause (see flexmark-java upstream)
 *
 * Why hand-ported: same AstActionHandler family as AttributeProviderAdapter — the
 * `H...` and `H[]...` constructors collide under Scala erasure (both `Seq`), and the
 * flat-varargs super calls mistranslate (the base takes `addActionHandlers(Array[H]*)`,
 * so a Java `H[]` argument is ONE element, not a spread). Every form is routed
 * through the base; the redundant array-varargs backward-compat constructor (unused
 * in the closure) is dropped. The `addHandlers` overloads keep distinct erasures
 * (Collection / Array[H] / Array[H]*) and are preserved.
 */
package com.vladsch.flexmark.ast.util

import com.vladsch.flexmark.html.renderer.LinkResolverBasicContext
import com.vladsch.flexmark.html.renderer.ResolvedLink
import com.vladsch.flexmark.util.ast.Node
import com.vladsch.flexmark.util.visitor.AstActionHandler

class LinkResolverAdapter
    extends AstActionHandler[
      LinkResolverAdapter,
      Node,
      LinkResolvingHandler.LinkResolvingVisitor[Node],
      LinkResolvingHandler[Node],
    ](Node.AST_ADAPTER)
    with LinkResolvingHandler.LinkResolvingVisitor[Node] {

  def this(handlers: LinkResolvingHandler[Node]*) = {
    this()
    super.addActionHandlers(handlers.toArray)
  }

  def this(handlers: java.util.Collection[LinkResolvingHandler[Node]]) = {
    this()
    addHandlers(handlers)
  }

  // add handler variations
  def addHandlers(handlers: java.util.Collection[LinkResolvingHandler[Node]]): LinkResolverAdapter =
    super.addActionHandlers(handlers.toArray(LinkResolverAdapter.EMPTY_HANDLERS))

  def addHandlers(handlers: Array[LinkResolvingHandler[Node]]): LinkResolverAdapter =
    super.addActionHandlers(handlers)

  def addHandlers(handlers: Array[LinkResolvingHandler[Node]]*): LinkResolverAdapter =
    super.addActionHandlers(handlers*)

  def addHandler(handler: LinkResolvingHandler[Node]): LinkResolverAdapter =
    super.addActionHandler(handler)

  override def resolveLink(node: Node, context: LinkResolverBasicContext, link: ResolvedLink): ResolvedLink =
    processNodeOnly(node, link, (n, handler) => handler.resolveLink(n, context, link))
}

object LinkResolverAdapter {
  protected[util] val EMPTY_HANDLERS: Array[LinkResolvingHandler[Node]] =
    new Array[LinkResolvingHandler[Node]](0)
}
