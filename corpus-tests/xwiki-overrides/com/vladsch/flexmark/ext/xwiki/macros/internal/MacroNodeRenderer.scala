/*
 * HANDWRITTEN OVERRIDE — Baltic Porter PLAN §7 whole-file override.
 *
 * Ported from: flexmark-java/flexmark-ext-xwiki-macros/src/main/java/com/vladsch/flexmark/ext/xwiki/macros/internal/MacroNodeRenderer.java
 * Original license: BSD-2-Clause (see flexmark-java upstream)
 *
 * Why hand-ported: `new HashSet<>()` (diamond, inferred from the method's declared
 * return type in Java) emitted as a raw `new HashSet()` -> HashSet[Object], which
 * doesn't conform to the returned Set[NodeRenderingHandler[?]]. Typed the local set.
 */
package com.vladsch.flexmark.ext.xwiki.macros.internal

class MacroNodeRenderer(_options: com.vladsch.flexmark.util.data.DataHolder) extends com.vladsch.flexmark.html.renderer.NodeRenderer {
  private val options: MacroOptions = new MacroOptions(_options)

  override def getNodeRenderingHandlers(): java.util.Set[com.vladsch.flexmark.html.renderer.NodeRenderingHandler[? <: Any]] = {
    val set = new java.util.HashSet[com.vladsch.flexmark.html.renderer.NodeRenderingHandler[? <: Any]]()
    set.add(new com.vladsch.flexmark.html.renderer.NodeRenderingHandler[com.vladsch.flexmark.ext.xwiki.macros.Macro](classOf[com.vladsch.flexmark.ext.xwiki.macros.Macro], this.render))
    set.add(new com.vladsch.flexmark.html.renderer.NodeRenderingHandler[com.vladsch.flexmark.ext.xwiki.macros.MacroAttribute](classOf[com.vladsch.flexmark.ext.xwiki.macros.MacroAttribute], this.render))
    set.add(new com.vladsch.flexmark.html.renderer.NodeRenderingHandler[com.vladsch.flexmark.ext.xwiki.macros.MacroClose](classOf[com.vladsch.flexmark.ext.xwiki.macros.MacroClose], this.render))
    set.add(new com.vladsch.flexmark.html.renderer.NodeRenderingHandler[com.vladsch.flexmark.ext.xwiki.macros.MacroBlock](classOf[com.vladsch.flexmark.ext.xwiki.macros.MacroBlock], this.render))
    set
  }

  private def render(node: com.vladsch.flexmark.ext.xwiki.macros.Macro, context: com.vladsch.flexmark.html.renderer.NodeRendererContext, html: com.vladsch.flexmark.html.HtmlWriter): Unit = {
    if (this.options.enableRendering) {
      html.text(com.vladsch.flexmark.util.ast.Node.spanningChars(node.getOpeningMarker(), node.getClosingMarker()))
      context.renderChildren(node)
    }
  }

  private def render(node: com.vladsch.flexmark.ext.xwiki.macros.MacroAttribute, context: com.vladsch.flexmark.html.renderer.NodeRendererContext, html: com.vladsch.flexmark.html.HtmlWriter): Unit = {
  }

  private def render(node: com.vladsch.flexmark.ext.xwiki.macros.MacroClose, context: com.vladsch.flexmark.html.renderer.NodeRendererContext, html: com.vladsch.flexmark.html.HtmlWriter): Unit = {
    if (this.options.enableRendering) {
      html.text(com.vladsch.flexmark.util.ast.Node.spanningChars(node.getOpeningMarker(), node.getClosingMarker()))
    }
  }

  private def render(node: com.vladsch.flexmark.ext.xwiki.macros.MacroBlock, context: com.vladsch.flexmark.html.renderer.NodeRendererContext, html: com.vladsch.flexmark.html.HtmlWriter): Unit = {
    if (this.options.enableRendering) {
      context.renderChildren(node)
    }
  }

}

object MacroNodeRenderer {
  class Factory extends com.vladsch.flexmark.html.renderer.NodeRendererFactory {
    override def apply(options: com.vladsch.flexmark.util.data.DataHolder): com.vladsch.flexmark.html.renderer.NodeRenderer = {
      new MacroNodeRenderer(options)
    }

  }

}
