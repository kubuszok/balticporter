package com.badlogic.gdx.scenes.scene2d.ui

class Tree[N <: com.badlogic.gdx.scenes.scene2d.ui.Tree.Node[?, ?, ?], V] extends com.badlogic.gdx.scenes.scene2d.ui.WidgetGroup with com.badlogic.gdx.scenes.scene2d.ui.Styleable[com.badlogic.gdx.scenes.scene2d.ui.Tree.TreeStyle] {
  var style: com.badlogic.gdx.scenes.scene2d.ui.Tree.TreeStyle = null.asInstanceOf[com.badlogic.gdx.scenes.scene2d.ui.Tree.TreeStyle]
  final val rootNodes: com.badlogic.gdx.utils.Array[N] = new com.badlogic.gdx.utils.Array()
  var selection: com.badlogic.gdx.scenes.scene2d.utils.Selection[N] = null.asInstanceOf[com.badlogic.gdx.scenes.scene2d.utils.Selection[N]]
  var ySpacing: scala.Float = 4
  var iconSpacingLeft: scala.Float = 2
  var iconSpacingRight: scala.Float = 2
  var paddingLeft: scala.Float = 0.0f
  var paddingRight: scala.Float = 0.0f
  var indentSpacing: scala.Float = 0.0f
  private var prefWidth: scala.Float = 0.0f
  private var prefHeight: scala.Float = 0.0f
  private var sizeInvalid: scala.Boolean = true
  private var foundNode: N = null.asInstanceOf[N]
  private var overNode: N = null.asInstanceOf[N]
  var rangeStart: N = null.asInstanceOf[N]
  private var clickListener: com.badlogic.gdx.scenes.scene2d.utils.ClickListener = null.asInstanceOf[com.badlogic.gdx.scenes.scene2d.utils.ClickListener]
  def this(style: com.badlogic.gdx.scenes.scene2d.ui.Tree.TreeStyle) = {
    this()
    this.selection = new com.badlogic.gdx.scenes.scene2d.utils.Selection[N]()
    this.selection.setActor(this)
    this.selection.setMultiple(true)
    this.setStyle(style)
    this.initialize()
  }
  def this(skin: com.badlogic.gdx.scenes.scene2d.ui.Skin) = {
    this(skin.get(classOf[com.badlogic.gdx.scenes.scene2d.ui.Tree.TreeStyle]))
  }
  def this(skin: com.badlogic.gdx.scenes.scene2d.ui.Skin, styleName: java.lang.String) = {
    this(skin.get(styleName, classOf[com.badlogic.gdx.scenes.scene2d.ui.Tree.TreeStyle]))
  }
  private def initialize(): scala.Unit = {
    this.addListener({
      this.clickListener = new com.badlogic.gdx.scenes.scene2d.utils.ClickListener()
      this.clickListener
    })
  }
  def setStyle(style: com.badlogic.gdx.scenes.scene2d.ui.Tree.TreeStyle): scala.Unit = {
    this.style = style
    if (this.indentSpacing == 0) {
      this.indentSpacing = this.plusMinusWidth()
    } else ()
  }
  def add(node: N): scala.Unit = {
    this.insert(this.rootNodes.size, node)
  }
  def insert(index$arg: scala.Int, node: N): scala.Unit = {
    var index: scala.Int = index$arg
    if (node.parent != null) {
      node.parent.remove(node)
      node.parent = null
    } else {
      val existingIndex: scala.Int = this.rootNodes.indexOf(node, true)
      if (existingIndex != (-1)) {
        if (existingIndex == index) {
          return
        } else ()
        if (existingIndex < index) {
          index = index - 1
        } else ()
        this.rootNodes.removeIndex(existingIndex)
        var actorIndex: scala.Int = node.actor.getZIndex()
        if (actorIndex != (-1)) {
          node.removeFromTree(this, actorIndex)
        } else ()
      } else ()
    }
    this.rootNodes.insert(index, node)
    var actorIndex: scala.Int = 0
    if (index == 0) {
      actorIndex = 0
    } else {
      if (index < (this.rootNodes.size - 1)) {
        actorIndex = this.rootNodes.get(index + 1).actor.getZIndex()
      } else {
        val before: N = this.rootNodes.get(index - 1)
        actorIndex = before.actor.getZIndex() + before.countActors()
      }
    }
    node.addToTree(this, actorIndex)
  }
  def remove(node: N): scala.Unit = {
    if (node.parent != null) {
      node.parent.remove(node)
      return
    } else ()
    if (!this.rootNodes.removeValue(node, true)) {
      return
    } else ()
    val actorIndex: scala.Int = node.actor.getZIndex()
    if (actorIndex != (-1)) {
      node.removeFromTree(this, actorIndex)
    } else ()
  }
  def clearChildren(unfocus: scala.Boolean): scala.Unit = {
    super.clearChildren(unfocus)
    this.setOverNode(null.asInstanceOf[N])
    this.rootNodes.clear()
    this.selection.clear()
  }
  def invalidate(): scala.Unit = {
    super.invalidate()
    this.sizeInvalid = true
  }
  private def plusMinusWidth(): scala.Float = {
    var width: scala.Float = java.lang.Math.max(this.style.plus.getMinWidth(), this.style.minus.getMinWidth())
    if (this.style.plusOver != null) {
      width = java.lang.Math.max(width, this.style.plusOver.getMinWidth())
    } else ()
    if (this.style.minusOver != null) {
      width = java.lang.Math.max(width, this.style.minusOver.getMinWidth())
    } else ()
    return width
  }
  private def computeSize(): scala.Unit = {
    this.sizeInvalid = false
    this.prefWidth = this.plusMinusWidth()
    this.prefHeight = 0
    this.computeSize(this.rootNodes, 0, this.prefWidth)
    this.prefWidth = this.prefWidth + (this.paddingLeft + this.paddingRight)
  }
  private def computeSize(nodes: com.badlogic.gdx.utils.Array[N], indent: scala.Float, plusMinusWidth: scala.Float): scala.Unit = {
    val ySpacing: scala.Float = this.ySpacing
    val spacing: scala.Float = this.iconSpacingLeft + this.iconSpacingRight;
    { var i: scala.Int = 0; val n: scala.Int = nodes.size; while (i < n) { {
      val node: N = nodes.get(i)
      var rowWidth: scala.Float = indent + plusMinusWidth
      val actor: com.badlogic.gdx.scenes.scene2d.Actor = node.actor
      if (actor.isInstanceOf[com.badlogic.gdx.scenes.scene2d.utils.Layout]) {
        val layout: com.badlogic.gdx.scenes.scene2d.utils.Layout = actor.asInstanceOf[com.badlogic.gdx.scenes.scene2d.utils.Layout]
        rowWidth = rowWidth + layout.getPrefWidth()
        node.height = layout.getPrefHeight()
      } else {
        rowWidth = rowWidth + actor.getWidth()
        node.height = actor.getHeight()
      }
      if (node.icon != null) {
        rowWidth = rowWidth + (spacing + node.icon.getMinWidth())
        node.height = java.lang.Math.max(node.height, node.icon.getMinHeight())
      } else ()
      this.prefWidth = java.lang.Math.max(this.prefWidth, rowWidth)
      this.prefHeight = this.prefHeight + (node.height + ySpacing)
      if (node.expanded) {
        this.computeSize(node.children, indent + this.indentSpacing, plusMinusWidth)
      } else ()
    }; i = i + 1 } }
  }
  def layout(): scala.Unit = {
    if (this.sizeInvalid) {
      this.computeSize()
    } else ()
    this.layout(this.rootNodes, this.paddingLeft, this.getHeight() - (this.ySpacing / 2), this.plusMinusWidth())
  }
  private def layout(nodes: com.badlogic.gdx.utils.Array[N], indent: scala.Float, y$arg: scala.Float, plusMinusWidth: scala.Float): scala.Float = {
    var y: scala.Float = y$arg
    val ySpacing: scala.Float = this.ySpacing
    val iconSpacingLeft: scala.Float = this.iconSpacingLeft
    val spacing: scala.Float = iconSpacingLeft + this.iconSpacingRight;
    { var i: scala.Int = 0; val n: scala.Int = nodes.size; while (i < n) { {
      val node: N = nodes.get(i)
      var x: scala.Float = indent + plusMinusWidth
      if (node.icon != null) {
        x = x + (spacing + node.icon.getMinWidth())
      } else {
        x = x + iconSpacingLeft
      }
      if (node.actor.isInstanceOf[com.badlogic.gdx.scenes.scene2d.utils.Layout]) {
        node.actor.asInstanceOf[com.badlogic.gdx.scenes.scene2d.utils.Layout].pack()
      } else ()
      y = y - node.getHeight()
      node.actor.setPosition(x, y)
      y = y - ySpacing
      if (node.expanded) {
        y = this.layout(node.children, indent + this.indentSpacing, y, plusMinusWidth)
      } else ()
    }; i = i + 1 } }
    return y
  }
  def draw(batch: com.badlogic.gdx.graphics.g2d.Batch, parentAlpha: scala.Float): scala.Unit = {
    this.drawBackground(batch, parentAlpha)
    val color: com.badlogic.gdx.graphics.Color = this.getColor()
    val a: scala.Float = color.a * parentAlpha
    batch.setColor(color.r, color.g, color.b, a)
    this.drawIcons(batch, color.r, color.g, color.b, a, null.asInstanceOf[N], this.rootNodes, this.paddingLeft, this.plusMinusWidth())
    super.draw(batch, parentAlpha)
  }
  def drawBackground(batch: com.badlogic.gdx.graphics.g2d.Batch, parentAlpha: scala.Float): scala.Unit = {
    if (this.style.background != null) {
      val color: com.badlogic.gdx.graphics.Color = this.getColor()
      batch.setColor(color.r, color.g, color.b, color.a * parentAlpha)
      this.style.background.draw(batch, this.getX(), this.getY(), this.getWidth(), this.getHeight())
    } else ()
  }
  def drawIcons(batch: com.badlogic.gdx.graphics.g2d.Batch, r: scala.Float, g: scala.Float, b: scala.Float, a: scala.Float, parent: N, nodes: com.badlogic.gdx.utils.Array[N], indent: scala.Float, plusMinusWidth: scala.Float): scala.Float = {
    val cullingArea: com.badlogic.gdx.math.Rectangle = this.getCullingArea()
    var cullBottom: scala.Float = 0
    var cullTop: scala.Float = 0
    if (cullingArea != null) {
      cullBottom = cullingArea.y
      cullTop = cullBottom + cullingArea.height
    } else ()
    val style: com.badlogic.gdx.scenes.scene2d.ui.Tree.TreeStyle = this.style
    val x: scala.Float = this.getX()
    val y: scala.Float = this.getY()
    val expandX: scala.Float = x + indent
    val iconX: scala.Float = (expandX + plusMinusWidth) + this.iconSpacingLeft
    var actorY: scala.Float = 0;
    { var i: scala.Int = 0; val n: scala.Int = nodes.size; while (i < n) { {
      val node: N = nodes.get(i)
      val actor: com.badlogic.gdx.scenes.scene2d.Actor = node.actor
      actorY = actor.getY()
      val height: scala.Float = node.height
      if ((cullingArea == null) || (((actorY + height) >= cullBottom) && (actorY <= cullTop))) {
        if (this.selection.contains(node) && (style.selection != null)) {
          this.drawSelection(node, style.selection, batch, x, (y + actorY) - (this.ySpacing / 2), this.getWidth(), height + this.ySpacing)
        } else {
          if ((node == this.overNode) && (style.over != null)) {
            this.drawOver(node, style.over, batch, x, (y + actorY) - (this.ySpacing / 2), this.getWidth(), height + this.ySpacing)
          } else ()
        }
        if (node.icon != null) {
          val iconY: scala.Float = (y + actorY) + java.lang.Math.round((height - node.icon.getMinHeight()) / 2)
          val actorColor: com.badlogic.gdx.graphics.Color = actor.getColor()
          batch.setColor(actorColor.r, actorColor.g, actorColor.b, actorColor.a * a)
          this.drawIcon(node, node.icon, batch, iconX, iconY)
          batch.setColor(r, g, b, a)
        } else ()
        if (node.children.size > 0) {
          val expandIcon: com.badlogic.gdx.scenes.scene2d.utils.Drawable = this.getExpandIcon(node, iconX)
          val iconY: scala.Float = (y + actorY) + java.lang.Math.round((height - expandIcon.getMinHeight()) / 2)
          this.drawExpandIcon(node, expandIcon, batch, expandX, iconY)
        } else ()
      } else {
        if (actorY < cullBottom) {
          /* break */ ()
        } else ()
      }
      if (node.expanded && (node.children.size > 0)) {
        this.drawIcons(batch, r, g, b, a, node, node.children, indent + this.indentSpacing, plusMinusWidth)
      } else ()
    }; i = i + 1 } }
    return actorY
  }
  def drawSelection(node: N, selection: com.badlogic.gdx.scenes.scene2d.utils.Drawable, batch: com.badlogic.gdx.graphics.g2d.Batch, x: scala.Float, y: scala.Float, width: scala.Float, height: scala.Float): scala.Unit = {
    selection.draw(batch, x, y, width, height)
  }
  def drawOver(node: N, over: com.badlogic.gdx.scenes.scene2d.utils.Drawable, batch: com.badlogic.gdx.graphics.g2d.Batch, x: scala.Float, y: scala.Float, width: scala.Float, height: scala.Float): scala.Unit = {
    over.draw(batch, x, y, width, height)
  }
  def drawExpandIcon(node: N, expandIcon: com.badlogic.gdx.scenes.scene2d.utils.Drawable, batch: com.badlogic.gdx.graphics.g2d.Batch, x: scala.Float, y: scala.Float): scala.Unit = {
    expandIcon.draw(batch, x, y, expandIcon.getMinWidth(), expandIcon.getMinHeight())
  }
  def drawIcon(node: N, icon: com.badlogic.gdx.scenes.scene2d.utils.Drawable, batch: com.badlogic.gdx.graphics.g2d.Batch, x: scala.Float, y: scala.Float): scala.Unit = {
    icon.draw(batch, x, y, icon.getMinWidth(), icon.getMinHeight())
  }
  def getExpandIcon(node: N, iconX: scala.Float): com.badlogic.gdx.scenes.scene2d.utils.Drawable = {
    if (((node == this.overNode) && (com.badlogic.gdx.Gdx.app.getType() == com.badlogic.gdx.Application.ApplicationType.Desktop)) && ((!this.selection.getMultiple()) || ((!com.badlogic.gdx.scenes.scene2d.utils.UIUtils.ctrl()) && (!com.badlogic.gdx.scenes.scene2d.utils.UIUtils.shift())))) {
      val mouseX: scala.Float = this.screenToLocalCoordinates(Tree.tmp.set(com.badlogic.gdx.Gdx.input.getX(), 0)).x + this.getX()
      if ((mouseX >= 0) && (mouseX < iconX)) {
        val icon: com.badlogic.gdx.scenes.scene2d.utils.Drawable = if (node.expanded) this.style.minusOver else this.style.plusOver
        if (icon != null) {
          return icon
        } else ()
      } else ()
    } else ()
    return if (node.expanded) this.style.minus else this.style.plus
  }
  def getNodeAt(y: scala.Float): N = {
    this.foundNode = null.asInstanceOf[N]
    this.getNodeAt(this.rootNodes, y, this.getHeight())
    try {
      return this.foundNode
    } finally {
      this.foundNode = null.asInstanceOf[N]
    }
  }
  private def getNodeAt(nodes: com.badlogic.gdx.utils.Array[N], y: scala.Float, rowY$arg: scala.Float): scala.Float = {
    var rowY: scala.Float = rowY$arg;
    { var i: scala.Int = 0; val n: scala.Int = nodes.size; while (i < n) { {
      val node: N = nodes.get(i)
      val height: scala.Float = node.height
      rowY = rowY - (node.getHeight() - height)
      if ((y >= ((rowY - height) - this.ySpacing)) && (y < rowY)) {
        this.foundNode = node
        return -1
      } else ()
      rowY = rowY - (height + this.ySpacing)
      if (node.expanded) {
        rowY = this.getNodeAt(node.children, y, rowY)
        if (rowY == (-1)) {
          return -1
        } else ()
      } else ()
    }; i = i + 1 } }
    return rowY
  }
  def selectNodes(nodes: com.badlogic.gdx.utils.Array[N], low: scala.Float, high: scala.Float): scala.Unit = {
    { var i: scala.Int = 0; val n: scala.Int = nodes.size; while (i < n) { {
      val node: N = nodes.get(i)
      if (node.actor.getY() < low) {
        /* break */ ()
      } else ()
      if (!node.isSelectable()) {
        /* continue */ ()
      } else ()
      if (node.actor.getY() <= high) {
        this.selection.add(node)
      } else ()
      if (node.expanded) {
        this.selectNodes(node.children, low, high)
      } else ()
    }; i = i + 1 } }
  }
  def getSelection(): com.badlogic.gdx.scenes.scene2d.utils.Selection[N] = {
    return this.selection
  }
  def getSelectedNode(): N = {
    return this.selection.first()
  }
  def getSelectedValue(): V = {
    val node: N = this.selection.first()
    return if (node == null) null.asInstanceOf[V] else node.getValue().asInstanceOf[V]
  }
  def getStyle(): com.badlogic.gdx.scenes.scene2d.ui.Tree.TreeStyle = {
    return this.style
  }
  def getRootNodes(): com.badlogic.gdx.utils.Array[N] = {
    return this.rootNodes
  }
  def getNodes(): com.badlogic.gdx.utils.Array[N] = {
    return this.rootNodes
  }
  def updateRootNodes(): scala.Unit = {
    { var i: scala.Int = 0; val n: scala.Int = this.rootNodes.size; while (i < n) { {
      val node: N = this.rootNodes.get(i)
      var actorIndex: scala.Int = node.actor.getZIndex()
      if (actorIndex != (-1)) {
        node.removeFromTree(this, actorIndex)
      } else ()
    }; i = i + 1 } };
    { var i: scala.Int = 0; val n: scala.Int = this.rootNodes.size; var actorIndex: scala.Int = 0; while (i < n) { {
      actorIndex = actorIndex + this.rootNodes.get(i).addToTree(this, actorIndex)
    }; i = i + 1 } }
  }
  def getOverNode(): N = {
    return this.overNode
  }
  def getOverValue(): V = {
    if (this.overNode == null) {
      return null.asInstanceOf[V]
    } else ()
    return this.overNode.getValue().asInstanceOf[V].asInstanceOf[V]
  }
  def setOverNode(overNode: N): scala.Unit = {
    this.overNode = overNode
  }
  def setPadding(padding: scala.Float): scala.Unit = {
    this.paddingLeft = padding
    this.paddingRight = padding
  }
  def setPadding(left: scala.Float, right: scala.Float): scala.Unit = {
    this.paddingLeft = left
    this.paddingRight = right
  }
  def setIndentSpacing(indentSpacing: scala.Float): scala.Unit = {
    this.indentSpacing = indentSpacing
  }
  def getIndentSpacing(): scala.Float = {
    return this.indentSpacing
  }
  def setYSpacing(ySpacing: scala.Float): scala.Unit = {
    this.ySpacing = ySpacing
  }
  def getYSpacing(): scala.Float = {
    return this.ySpacing
  }
  def setIconSpacing(left: scala.Float, right: scala.Float): scala.Unit = {
    this.iconSpacingLeft = left
    this.iconSpacingRight = right
  }
  def getPrefWidth(): scala.Float = {
    if (this.sizeInvalid) {
      this.computeSize()
    } else ()
    return this.prefWidth
  }
  def getPrefHeight(): scala.Float = {
    if (this.sizeInvalid) {
      this.computeSize()
    } else ()
    return this.prefHeight
  }
  def findExpandedValues(values: com.badlogic.gdx.utils.Array[V]): scala.Unit = {
    Tree.findExpandedValues(this.rootNodes, values)
  }
  def restoreExpandedValues(values: com.badlogic.gdx.utils.Array[V]): scala.Unit = {
    { var i: scala.Int = 0; val n: scala.Int = values.size; while (i < n) { {
      val node: N = this.findNode(values.get(i))
      if (node != null) {
        node.setExpanded(true)
        node.expandTo()
      } else ()
    }; i = i + 1 } }
  }
  def findNode(value: V): N = {
    if (value == null) {
      throw new java.lang.IllegalArgumentException("value cannot be null.")
    } else ()
    return Tree.findNode(this.rootNodes, value.asInstanceOf[java.lang.Object]).asInstanceOf[N]
  }
  def collapseAll(): scala.Unit = {
    Tree.collapseAll(this.rootNodes)
  }
  def expandAll(): scala.Unit = {
    Tree.expandAll(this.rootNodes)
  }
  def getClickListener(): com.badlogic.gdx.scenes.scene2d.utils.ClickListener = {
    return this.clickListener
  }
}
object Tree {
  export com.badlogic.gdx.scenes.scene2d.ui.WidgetGroup.{tmp => _, findExpandedValues => _, findNode => _, collapseAll => _, expandAll => _, Node => _, TreeStyle => _, *}
  private final val tmp: com.badlogic.gdx.math.Vector2 = new com.badlogic.gdx.math.Vector2()
  def findExpandedValues(nodes: com.badlogic.gdx.utils.Array[? <: com.badlogic.gdx.scenes.scene2d.ui.Tree.Node[?, ?, ?]], values: com.badlogic.gdx.utils.Array[?]): scala.Boolean = {
    val expanded: scala.Boolean = false;
    { var i: scala.Int = 0; val n: scala.Int = nodes.size; while (i < n) { {
      val node: com.badlogic.gdx.scenes.scene2d.ui.Tree.Node[?, ?, ?] = nodes.get(i)
      if (node.expanded && (!Tree.findExpandedValues(node.children, values))) {
        values.add(node.value)
      } else ()
    }; i = i + 1 } }
    return expanded
  }
  def findNode(nodes: com.badlogic.gdx.utils.Array[? <: com.badlogic.gdx.scenes.scene2d.ui.Tree.Node[?, ?, ?]], value: java.lang.Object): com.badlogic.gdx.scenes.scene2d.ui.Tree.Node[?, ?, ?] = {
    { var i: scala.Int = 0; val n: scala.Int = nodes.size; while (i < n) { {
      val node: com.badlogic.gdx.scenes.scene2d.ui.Tree.Node[?, ?, ?] = nodes.get(i)
      if (value.equals(node.value)) {
        return node
      } else ()
    }; i = i + 1 } };
    { var i: scala.Int = 0; val n: scala.Int = nodes.size; while (i < n) { {
      val node: com.badlogic.gdx.scenes.scene2d.ui.Tree.Node[?, ?, ?] = nodes.get(i)
      val found: com.badlogic.gdx.scenes.scene2d.ui.Tree.Node[?, ?, ?] = Tree.findNode(node.children, value)
      if (found != null) {
        return found
      } else ()
    }; i = i + 1 } }
    return null
  }
  def collapseAll(nodes: com.badlogic.gdx.utils.Array[? <: com.badlogic.gdx.scenes.scene2d.ui.Tree.Node[?, ?, ?]]): scala.Unit = {
    { var i: scala.Int = 0; val n: scala.Int = nodes.size; while (i < n) { {
      val node: com.badlogic.gdx.scenes.scene2d.ui.Tree.Node[?, ?, ?] = nodes.get(i)
      node.setExpanded(false)
      Tree.collapseAll(node.children)
    }; i = i + 1 } }
  }
  def expandAll(nodes: com.badlogic.gdx.utils.Array[? <: com.badlogic.gdx.scenes.scene2d.ui.Tree.Node[?, ?, ?]]): scala.Unit = {
    { var i: scala.Int = 0; val n: scala.Int = nodes.size; while (i < n) { {
      nodes.get(i).expandAll()
    }; i = i + 1 } }
  }
  abstract class Node[N <: com.badlogic.gdx.scenes.scene2d.ui.Tree.Node[?, ?, ?], V, A <: com.badlogic.gdx.scenes.scene2d.Actor] {
    var actor: A = null.asInstanceOf[A]
    var parent: N = null.asInstanceOf[N]
    final val children: com.badlogic.gdx.utils.Array[N] = new com.badlogic.gdx.utils.Array(0)
    var selectable: scala.Boolean = true
    var expanded: scala.Boolean = false
    var icon: com.badlogic.gdx.scenes.scene2d.utils.Drawable = null.asInstanceOf[com.badlogic.gdx.scenes.scene2d.utils.Drawable]
    var height: scala.Float = 0.0f
    var value: V = null.asInstanceOf[V]
    def this(actor: A) = {
      this()
      if (actor == null) {
        throw new java.lang.IllegalArgumentException("actor cannot be null.")
      } else ()
      this.actor = actor
    }
    def setExpanded(expanded: scala.Boolean): scala.Unit = {
      if (expanded == this.expanded) {
        return
      } else ()
      this.expanded = expanded
      if (this.children.size == 0) {
        return
      } else ()
      val tree: Tree[?, ?] = this.getTree()
      if (tree == null) {
        return
      } else ()
      val children: scala.Array[java.lang.Object] = this.children.items.asInstanceOf[scala.Array[java.lang.Object]]
      var actorIndex: scala.Int = this.actor.getZIndex() + 1
      if (expanded) {
        { var i: scala.Int = 0; val n: scala.Int = this.children.size; while (i < n) { {
          actorIndex = actorIndex + children(i).asInstanceOf[N].addToTree(tree, actorIndex)
        }; i = i + 1 } }
      } else {
        { var i: scala.Int = 0; val n: scala.Int = this.children.size; while (i < n) { {
          children(i).asInstanceOf[N].removeFromTree(tree, actorIndex)
        }; i = i + 1 } }
      }
    }
    def addToTree(tree: Tree[N, V], actorIndex: scala.Int): scala.Int = {
      tree.addActorAt(actorIndex, this.actor)
      if (!this.expanded) {
        return 1
      } else ()
      var childIndex: scala.Int = actorIndex + 1
      val children: scala.Array[java.lang.Object] = this.children.items.asInstanceOf[scala.Array[java.lang.Object]];
      { var i: scala.Int = 0; val n: scala.Int = this.children.size; while (i < n) { {
        childIndex = childIndex + children(i).asInstanceOf[N].addToTree(tree, childIndex)
      }; i = i + 1 } }
      return childIndex - actorIndex
    }
    def removeFromTree(tree: Tree[N, V], actorIndex: scala.Int): scala.Unit = {
      val removeActorAt: com.badlogic.gdx.scenes.scene2d.Actor = tree.removeActorAt(actorIndex, true)
      if (!this.expanded) {
        return
      } else ()
      val children: scala.Array[java.lang.Object] = this.children.items.asInstanceOf[scala.Array[java.lang.Object]];
      { var i: scala.Int = 0; val n: scala.Int = this.children.size; while (i < n) { {
        children(i).asInstanceOf[N].removeFromTree(tree, actorIndex)
      }; i = i + 1 } }
    }
    def add(node: N): scala.Unit = {
      this.insert(this.children.size, node)
    }
    def addAll(nodes: com.badlogic.gdx.utils.Array[N]): scala.Unit = {
      { var i: scala.Int = 0; val n: scala.Int = nodes.size; while (i < n) { {
        this.insert(this.children.size, nodes.get(i))
      }; i = i + 1 } }
    }
    def insert(childIndex: scala.Int, node: N): scala.Unit = {
      node.parent = this
      this.children.insert(childIndex, node)
      if (!this.expanded) {
        return
      } else ()
      val tree: Tree[?, ?] = this.getTree()
      if (tree != null) {
        var actorIndex: scala.Int = 0
        if (childIndex == 0) {
          actorIndex = this.actor.getZIndex() + 1
        } else {
          if (childIndex < (this.children.size - 1)) {
            actorIndex = this.children.get(childIndex + 1).actor.getZIndex()
          } else {
            val before: N = this.children.get(childIndex - 1)
            actorIndex = before.actor.getZIndex() + before.countActors()
          }
        }
        node.addToTree(tree, actorIndex)
      } else ()
    }
    def countActors(): scala.Int = {
      if (!this.expanded) {
        return 1
      } else ()
      var count: scala.Int = 1
      val children: scala.Array[java.lang.Object] = this.children.items.asInstanceOf[scala.Array[java.lang.Object]];
      { var i: scala.Int = 0; val n: scala.Int = this.children.size; while (i < n) { {
        count = count + children(i).asInstanceOf[N].countActors()
      }; i = i + 1 } }
      return count
    }
    def remove(): scala.Unit = {
      val tree: Tree[?, ?] = this.getTree()
      if (tree != null) {
        tree.remove(this)
      } else {
        if (this.parent != null) {
          this.parent.remove(this)
        } else ()
      }
    }
    def remove(node: N): scala.Unit = {
      if (!this.children.removeValue(node, true)) {
        return
      } else ()
      if (!this.expanded) {
        return
      } else ()
      val tree: Tree[?, ?] = this.getTree()
      if (tree != null) {
        node.removeFromTree(tree, node.actor.getZIndex())
      } else ()
    }
    def clearChildren(): scala.Unit = {
      if (this.expanded) {
        val tree: Tree[?, ?] = this.getTree()
        if (tree != null) {
          val actorIndex: scala.Int = this.actor.getZIndex() + 1
          val children: scala.Array[java.lang.Object] = this.children.items.asInstanceOf[scala.Array[java.lang.Object]];
          { var i: scala.Int = 0; val n: scala.Int = this.children.size; while (i < n) { {
            children(i).asInstanceOf[N].removeFromTree(tree, actorIndex)
          }; i = i + 1 } }
        } else ()
      } else ()
      this.children.clear()
    }
    def getTree(): Tree[N, V] = {
      val parent: com.badlogic.gdx.scenes.scene2d.Group = this.actor.getParent()
      if (parent.isInstanceOf[Tree[?, ?]]) {
        return parent.asInstanceOf[Tree[?, ?]]
      } else ()
      return null
    }
    def setActor(newActor: A): scala.Unit = {
      if (this.actor != null) {
        val tree: Tree[N, V] = this.getTree()
        if (tree != null) {
          val index: scala.Int = this.actor.getZIndex()
          tree.removeActorAt(index, true)
          tree.addActorAt(index, newActor)
        } else ()
      } else ()
      this.actor = newActor
    }
    def getActor(): A = {
      return this.actor
    }
    def isExpanded(): scala.Boolean = {
      return this.expanded
    }
    def getChildren(): com.badlogic.gdx.utils.Array[N] = {
      return this.children
    }
    def hasChildren(): scala.Boolean = {
      return this.children.size > 0
    }
    def updateChildren(): scala.Unit = {
      if (!this.expanded) {
        return
      } else ()
      val tree: Tree[?, ?] = this.getTree()
      if (tree == null) {
        return
      } else ()
      val children: scala.Array[java.lang.Object] = this.children.items.asInstanceOf[scala.Array[java.lang.Object]]
      val n: scala.Int = this.children.size
      var actorIndex: scala.Int = this.actor.getZIndex() + 1;
      { var i: scala.Int = 0; while (i < n) { {
        children(i).asInstanceOf[N].removeFromTree(tree, actorIndex)
      }; i = i + 1 } };
      { var i: scala.Int = 0; while (i < n) { {
        actorIndex = actorIndex + children(i).asInstanceOf[N].addToTree(tree, actorIndex)
      }; i = i + 1 } }
    }
    def getParent(): N = {
      return this.parent
    }
    def setIcon(icon: com.badlogic.gdx.scenes.scene2d.utils.Drawable): scala.Unit = {
      this.icon = icon
    }
    def getValue(): V = {
      return this.value
    }
    def setValue(value: V): scala.Unit = {
      this.value = value
    }
    def getIcon(): com.badlogic.gdx.scenes.scene2d.utils.Drawable = {
      return this.icon
    }
    def getLevel(): scala.Int = {
      var level: scala.Int = 0
      var current: com.badlogic.gdx.scenes.scene2d.ui.Tree.Node[?, ?, ?] = this
      while ({ {
        level = level + 1
        current = current.getParent()
      }; current != null }) ()
      return level
    }
    def findNode(value: V): N = {
      if (value == null) {
        throw new java.lang.IllegalArgumentException("value cannot be null.")
      } else ()
      if (value.equals(this.value.asInstanceOf[java.lang.Object])) {
        return this.asInstanceOf[N]
      } else ()
      return Tree.findNode(this.children, value.asInstanceOf[java.lang.Object]).asInstanceOf[N]
    }
    def collapseAll(): scala.Unit = {
      this.setExpanded(false)
      Tree.collapseAll(this.children)
    }
    def expandAll(): scala.Unit = {
      this.setExpanded(true)
      if (this.children.size > 0) {
        Tree.expandAll(this.children)
      } else ()
    }
    def expandTo(): scala.Unit = {
      var node: com.badlogic.gdx.scenes.scene2d.ui.Tree.Node[?, ?, ?] = this.parent
      while (node != null) {
        node.setExpanded(true)
        node = node.parent
      }
    }
    def isSelectable(): scala.Boolean = {
      return this.selectable
    }
    def setSelectable(selectable: scala.Boolean): scala.Unit = {
      this.selectable = selectable
    }
    def findExpandedValues(values: com.badlogic.gdx.utils.Array[V]): scala.Unit = {
      if (this.expanded && (!Tree.findExpandedValues(this.children, values))) {
        values.add(this.value)
      } else ()
    }
    def restoreExpandedValues(values: com.badlogic.gdx.utils.Array[V]): scala.Unit = {
      { var i: scala.Int = 0; val n: scala.Int = values.size; while (i < n) { {
        val node: N = this.findNode(values.get(i))
        if (node != null) {
          node.setExpanded(true)
          node.expandTo()
        } else ()
      }; i = i + 1 } }
    }
    def getHeight(): scala.Float = {
      return this.height
    }
    def isAscendantOf(node: N): scala.Boolean = {
      if (node == null) {
        throw new java.lang.IllegalArgumentException("node cannot be null.")
      } else ()
      var current: com.badlogic.gdx.scenes.scene2d.ui.Tree.Node[?, ?, ?] = node
      while ({ {
        if (current == this) {
          return true
        } else ()
        current = current.parent
      }; current != null }) ()
      return false
    }
    def isDescendantOf(node: N): scala.Boolean = {
      if (node == null) {
        throw new java.lang.IllegalArgumentException("node cannot be null.")
      } else ()
      var parent: com.badlogic.gdx.scenes.scene2d.ui.Tree.Node[?, ?, ?] = this
      while ({ {
        if (parent == node) {
          return true
        } else ()
        parent = parent.parent
      }; parent != null }) ()
      return false
    }
  }
  class TreeStyle {
    var plus: com.badlogic.gdx.scenes.scene2d.utils.Drawable = null.asInstanceOf[com.badlogic.gdx.scenes.scene2d.utils.Drawable]
    var minus: com.badlogic.gdx.scenes.scene2d.utils.Drawable = null.asInstanceOf[com.badlogic.gdx.scenes.scene2d.utils.Drawable]
    var plusOver: com.badlogic.gdx.scenes.scene2d.utils.Drawable = null.asInstanceOf[com.badlogic.gdx.scenes.scene2d.utils.Drawable]
    var minusOver: com.badlogic.gdx.scenes.scene2d.utils.Drawable = null.asInstanceOf[com.badlogic.gdx.scenes.scene2d.utils.Drawable]
    var over: com.badlogic.gdx.scenes.scene2d.utils.Drawable = null.asInstanceOf[com.badlogic.gdx.scenes.scene2d.utils.Drawable]
    var selection: com.badlogic.gdx.scenes.scene2d.utils.Drawable = null.asInstanceOf[com.badlogic.gdx.scenes.scene2d.utils.Drawable]
    var background: com.badlogic.gdx.scenes.scene2d.utils.Drawable = null.asInstanceOf[com.badlogic.gdx.scenes.scene2d.utils.Drawable]
    def this(plus: com.badlogic.gdx.scenes.scene2d.utils.Drawable, minus: com.badlogic.gdx.scenes.scene2d.utils.Drawable, selection: com.badlogic.gdx.scenes.scene2d.utils.Drawable) = {
      this()
      this.plus = plus
      this.minus = minus
      this.selection = selection
    }
    def this(style: com.badlogic.gdx.scenes.scene2d.ui.Tree.TreeStyle) = {
      this()
      this.plus = style.plus
      this.minus = style.minus
      this.plusOver = style.plusOver
      this.minusOver = style.minusOver
      this.over = style.over
      this.selection = style.selection
      this.background = style.background
    }
  }
}