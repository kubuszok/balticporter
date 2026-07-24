package com.badlogic.gdx.scenes.scene2d.ui

class HorizontalGroup extends com.badlogic.gdx.scenes.scene2d.ui.WidgetGroup {
  private var prefWidth: scala.Float = 0.0f
  private var prefHeight: scala.Float = 0.0f
  private var lastPrefHeight: scala.Float = 0.0f
  private var sizeInvalid: scala.Boolean = true
  private var rowSizes: com.badlogic.gdx.utils.FloatArray = null.asInstanceOf[com.badlogic.gdx.utils.FloatArray]
  var align$field: scala.Int = com.badlogic.gdx.utils.Align.left
  var rowAlign$field: scala.Int = 0
  var reverse$field: scala.Boolean = false
  private var round: scala.Boolean = true
  var wrap$field: scala.Boolean = false
  var wrapReverse$field: scala.Boolean = false
  var expand$field: scala.Boolean = false
  var space$field: scala.Float = 0.0f
  var wrapSpace$field: scala.Float = 0.0f
  var fill$field: scala.Float = 0.0f
  var padTop$field: scala.Float = 0.0f
  var padLeft$field: scala.Float = 0.0f
  var padBottom$field: scala.Float = 0.0f
  var padRight$field: scala.Float = 0.0f
  this.setTransform(false)
  this.setTouchable(com.badlogic.gdx.scenes.scene2d.Touchable.childrenOnly)
  def invalidate(): scala.Unit = {
    super.invalidate()
    this.sizeInvalid = true
  }
  private def computeSize(): scala.Unit = {
    this.sizeInvalid = false
    val children: com.badlogic.gdx.utils.SnapshotArray[com.badlogic.gdx.scenes.scene2d.Actor] = this.getChildren()
    var n: scala.Int = children.size
    this.prefHeight = 0
    if (this.wrap$field) {
      this.prefWidth = 0
      if (this.rowSizes == null) {
        this.rowSizes = new com.badlogic.gdx.utils.FloatArray()
      } else {
        this.rowSizes.clear()
      }
      var rowSizes: com.badlogic.gdx.utils.FloatArray = this.rowSizes
      val space: scala.Float = this.space$field
      val wrapSpace: scala.Float = this.wrapSpace$field
      val pad: scala.Float = this.padLeft$field + this.padRight$field
      val groupWidth: scala.Float = this.getWidth() - pad
      var x: scala.Float = 0
      var y: scala.Float = 0
      var rowHeight: scala.Float = 0
      var i: scala.Int = 0
      var incr: scala.Int = 1
      if (this.reverse$field) {
        i = n - 1
        n = -1
        incr = -1
      } else ();
      { ; while (i != n) { {
        val child: com.badlogic.gdx.scenes.scene2d.Actor = children.get(i)
        var width: scala.Float = 0.0f
        var height: scala.Float = 0.0f
        if (child.isInstanceOf[com.badlogic.gdx.scenes.scene2d.utils.Layout]) {
          val layout: com.badlogic.gdx.scenes.scene2d.utils.Layout = child.asInstanceOf[com.badlogic.gdx.scenes.scene2d.utils.Layout]
          width = layout.getPrefWidth()
          if (width > groupWidth) {
            width = java.lang.Math.max(groupWidth, layout.getMinWidth())
          } else ()
          height = layout.getPrefHeight()
        } else {
          width = child.getWidth()
          height = child.getHeight()
        }
        var incrX: scala.Float = width + (if (x > 0) space else 0)
        if (((x + incrX) > groupWidth) && (x > 0)) {
          rowSizes.add(x)
          rowSizes.add(rowHeight)
          this.prefWidth = java.lang.Math.max(this.prefWidth, x + pad)
          if (y > 0) {
            y = y + wrapSpace
          } else ()
          y = y + rowHeight
          rowHeight = 0
          x = 0
          incrX = width
        } else ()
        x = x + incrX
        rowHeight = java.lang.Math.max(rowHeight, height)
      }; i = i + incr } }
      rowSizes.add(x)
      rowSizes.add(rowHeight)
      this.prefWidth = java.lang.Math.max(this.prefWidth, x + pad)
      if (y > 0) {
        y = y + wrapSpace
      } else ()
      this.prefHeight = java.lang.Math.max(this.prefHeight, y + rowHeight)
    } else {
      this.prefWidth = (this.padLeft$field + this.padRight$field) + (this.space$field * (n - 1));
      { var i: scala.Int = 0; while (i < n) { {
        val child: com.badlogic.gdx.scenes.scene2d.Actor = children.get(i)
        if (child.isInstanceOf[com.badlogic.gdx.scenes.scene2d.utils.Layout]) {
          val layout: com.badlogic.gdx.scenes.scene2d.utils.Layout = child.asInstanceOf[com.badlogic.gdx.scenes.scene2d.utils.Layout]
          this.prefWidth = this.prefWidth + layout.getPrefWidth()
          this.prefHeight = java.lang.Math.max(this.prefHeight, layout.getPrefHeight())
        } else {
          this.prefWidth = this.prefWidth + child.getWidth()
          this.prefHeight = java.lang.Math.max(this.prefHeight, child.getHeight())
        }
      }; i = i + 1 } }
    }
    this.prefHeight = this.prefHeight + (this.padTop$field + this.padBottom$field)
    if (this.round) {
      this.prefWidth = java.lang.Math.ceil(this.prefWidth).asInstanceOf[scala.Float].asInstanceOf[scala.Float]
      this.prefHeight = java.lang.Math.ceil(this.prefHeight).asInstanceOf[scala.Float].asInstanceOf[scala.Float]
    } else ()
  }
  def layout(): scala.Unit = {
    if (this.sizeInvalid) {
      this.computeSize()
    } else ()
    if (this.wrap$field) {
      this.layoutWrapped()
      return
    } else ()
    val round: scala.Boolean = this.round
    var align: scala.Int = this.align$field
    val space: scala.Float = this.space$field
    val padBottom: scala.Float = this.padBottom$field
    val fill: scala.Float = this.fill$field
    val rowHeight: scala.Float = ((if (this.expand$field) this.getHeight() else this.prefHeight) - this.padTop$field) - padBottom
    var x: scala.Float = this.padLeft$field
    if ((align & com.badlogic.gdx.utils.Align.right) != 0) {
      x = x + (this.getWidth() - this.prefWidth)
    } else {
      if ((align & com.badlogic.gdx.utils.Align.left) == 0) {
        x = x + ((this.getWidth() - this.prefWidth) / 2)
      } else ()
    }
    var startY: scala.Float = 0.0f
    if ((align & com.badlogic.gdx.utils.Align.bottom) != 0) {
      startY = padBottom
    } else {
      if ((align & com.badlogic.gdx.utils.Align.top) != 0) {
        startY = (this.getHeight() - this.padTop$field) - rowHeight
      } else {
        startY = padBottom + ((((this.getHeight() - padBottom) - this.padTop$field) - rowHeight) / 2)
      }
    }
    align = this.rowAlign$field
    val children: com.badlogic.gdx.utils.SnapshotArray[com.badlogic.gdx.scenes.scene2d.Actor] = this.getChildren()
    var i: scala.Int = 0
    var n: scala.Int = children.size
    var incr: scala.Int = 1
    if (this.reverse$field) {
      i = n - 1
      n = -1
      incr = -1
    } else ();
    { val r: scala.Int = 0; while (i != n) { {
      val child: com.badlogic.gdx.scenes.scene2d.Actor = children.get(i)
      var width: scala.Float = 0.0f
      var height: scala.Float = 0.0f
      var layout: com.badlogic.gdx.scenes.scene2d.utils.Layout = null
      if (child.isInstanceOf[com.badlogic.gdx.scenes.scene2d.utils.Layout]) {
        layout = child.asInstanceOf[com.badlogic.gdx.scenes.scene2d.utils.Layout]
        width = layout.getPrefWidth()
        height = layout.getPrefHeight()
      } else {
        width = child.getWidth()
        height = child.getHeight()
      }
      if (fill > 0) {
        height = rowHeight * fill
      } else ()
      if (layout != null) {
        height = java.lang.Math.max(height, layout.getMinHeight())
        val maxHeight: scala.Float = layout.getMaxHeight()
        if ((maxHeight > 0) && (height > maxHeight)) {
          height = maxHeight
        } else ()
      } else ()
      var y: scala.Float = startY
      if ((align & com.badlogic.gdx.utils.Align.top) != 0) {
        y = y + (rowHeight - height)
      } else {
        if ((align & com.badlogic.gdx.utils.Align.bottom) == 0) {
          y = y + ((rowHeight - height) / 2)
        } else ()
      }
      if (round) {
        child.setBounds(java.lang.Math.floor(x).asInstanceOf[scala.Float].asInstanceOf[scala.Float], java.lang.Math.floor(y).asInstanceOf[scala.Float].asInstanceOf[scala.Float], java.lang.Math.ceil(width).asInstanceOf[scala.Float].asInstanceOf[scala.Float], java.lang.Math.ceil(height).asInstanceOf[scala.Float].asInstanceOf[scala.Float])
      } else {
        child.setBounds(x, y, width, height)
      }
      x = x + (width + space)
      if (layout != null) {
        layout.validate()
      } else ()
    }; i = i + incr } }
  }
  private def layoutWrapped(): scala.Unit = {
    val prefHeight: scala.Float = this.getPrefHeight()
    if (prefHeight != this.lastPrefHeight) {
      this.lastPrefHeight = prefHeight
      this.invalidateHierarchy()
    } else ()
    var align: scala.Int = this.align$field
    val round: scala.Boolean = this.round
    val space: scala.Float = this.space$field
    val fill: scala.Float = this.fill$field
    val wrapSpace: scala.Float = this.wrapSpace$field
    val maxWidth: scala.Float = (this.prefWidth - this.padLeft$field) - this.padRight$field
    var rowY: scala.Float = prefHeight - this.padTop$field
    var groupWidth: scala.Float = this.getWidth()
    var xStart: scala.Float = this.padLeft$field
    var x: scala.Float = 0
    var rowHeight: scala.Float = 0
    var rowDir: scala.Float = -1
    if ((align & com.badlogic.gdx.utils.Align.top) != 0) {
      rowY = rowY + (this.getHeight() - prefHeight)
    } else {
      if ((align & com.badlogic.gdx.utils.Align.bottom) == 0) {
        rowY = rowY + ((this.getHeight() - prefHeight) / 2)
      } else ()
    }
    if (this.wrapReverse$field) {
      rowY = rowY - (prefHeight + this.rowSizes.get(1))
      rowDir = 1
    } else ()
    if ((align & com.badlogic.gdx.utils.Align.right) != 0) {
      xStart = xStart + (groupWidth - this.prefWidth)
    } else {
      if ((align & com.badlogic.gdx.utils.Align.left) == 0) {
        xStart = xStart + ((groupWidth - this.prefWidth) / 2)
      } else ()
    }
    groupWidth = groupWidth - this.padRight$field
    align = this.rowAlign$field
    val rowSizes: com.badlogic.gdx.utils.FloatArray = this.rowSizes
    val children: com.badlogic.gdx.utils.SnapshotArray[com.badlogic.gdx.scenes.scene2d.Actor] = this.getChildren()
    var i: scala.Int = 0
    var n: scala.Int = children.size
    var incr: scala.Int = 1
    if (this.reverse$field) {
      i = n - 1
      n = -1
      incr = -1
    } else ();
    { var r: scala.Int = 0; while (i != n) { {
      val child: com.badlogic.gdx.scenes.scene2d.Actor = children.get(i)
      var width: scala.Float = 0.0f
      var height: scala.Float = 0.0f
      var layout: com.badlogic.gdx.scenes.scene2d.utils.Layout = null
      if (child.isInstanceOf[com.badlogic.gdx.scenes.scene2d.utils.Layout]) {
        layout = child.asInstanceOf[com.badlogic.gdx.scenes.scene2d.utils.Layout]
        width = layout.getPrefWidth()
        if (width > groupWidth) {
          width = java.lang.Math.max(groupWidth, layout.getMinWidth())
        } else ()
        height = layout.getPrefHeight()
      } else {
        width = child.getWidth()
        height = child.getHeight()
      }
      if (((x + width) > groupWidth) || (r == 0)) {
        r = java.lang.Math.min(r, rowSizes.size - 2)
        x = xStart
        if ((align & com.badlogic.gdx.utils.Align.right) != 0) {
          x = x + (maxWidth - rowSizes.get(r))
        } else {
          if ((align & com.badlogic.gdx.utils.Align.left) == 0) {
            x = x + ((maxWidth - rowSizes.get(r)) / 2)
          } else ()
        }
        rowHeight = rowSizes.get(r + 1)
        if (r > 0) {
          rowY = rowY + (wrapSpace * rowDir)
        } else ()
        rowY = rowY + (rowHeight * rowDir)
        r = r + 2
      } else ()
      if (fill > 0) {
        height = rowHeight * fill
      } else ()
      if (layout != null) {
        height = java.lang.Math.max(height, layout.getMinHeight())
        val maxHeight: scala.Float = layout.getMaxHeight()
        if ((maxHeight > 0) && (height > maxHeight)) {
          height = maxHeight
        } else ()
      } else ()
      var y: scala.Float = rowY
      if ((align & com.badlogic.gdx.utils.Align.top) != 0) {
        y = y + (rowHeight - height)
      } else {
        if ((align & com.badlogic.gdx.utils.Align.bottom) == 0) {
          y = y + ((rowHeight - height) / 2)
        } else ()
      }
      if (round) {
        child.setBounds(java.lang.Math.floor(x).asInstanceOf[scala.Float].asInstanceOf[scala.Float], java.lang.Math.floor(y).asInstanceOf[scala.Float].asInstanceOf[scala.Float], java.lang.Math.ceil(width).asInstanceOf[scala.Float].asInstanceOf[scala.Float], java.lang.Math.ceil(height).asInstanceOf[scala.Float].asInstanceOf[scala.Float])
      } else {
        child.setBounds(x, y, width, height)
      }
      x = x + (width + space)
      if (layout != null) {
        layout.validate()
      } else ()
    }; i = i + incr } }
  }
  def getPrefWidth(): scala.Float = {
    if (this.wrap$field) {
      return 0
    } else ()
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
  def getRows(): scala.Int = {
    return if (this.wrap$field) this.rowSizes.size >> 1 else 1
  }
  def setRound(round: scala.Boolean): scala.Unit = {
    this.round = round
  }
  def reverse(): HorizontalGroup = {
    this.reverse$field = true
    return this
  }
  def reverse(reverse: scala.Boolean): HorizontalGroup = {
    this.reverse$field = reverse
    return this
  }
  def getReverse(): scala.Boolean = {
    return this.reverse$field
  }
  def wrapReverse(): HorizontalGroup = {
    this.wrapReverse$field = true
    return this
  }
  def wrapReverse(wrapReverse: scala.Boolean): HorizontalGroup = {
    this.wrapReverse$field = wrapReverse
    return this
  }
  def getWrapReverse(): scala.Boolean = {
    return this.wrapReverse$field
  }
  def space(space: scala.Float): HorizontalGroup = {
    this.space$field = space
    return this
  }
  def getSpace(): scala.Float = {
    return this.space$field
  }
  def wrapSpace(wrapSpace: scala.Float): HorizontalGroup = {
    this.wrapSpace$field = wrapSpace
    return this
  }
  def getWrapSpace(): scala.Float = {
    return this.wrapSpace$field
  }
  def pad(pad: scala.Float): HorizontalGroup = {
    this.padTop$field = pad
    this.padLeft$field = pad
    this.padBottom$field = pad
    this.padRight$field = pad
    return this
  }
  def pad(top: scala.Float, left: scala.Float, bottom: scala.Float, right: scala.Float): HorizontalGroup = {
    this.padTop$field = top
    this.padLeft$field = left
    this.padBottom$field = bottom
    this.padRight$field = right
    return this
  }
  def padTop(padTop: scala.Float): HorizontalGroup = {
    this.padTop$field = padTop
    return this
  }
  def padLeft(padLeft: scala.Float): HorizontalGroup = {
    this.padLeft$field = padLeft
    return this
  }
  def padBottom(padBottom: scala.Float): HorizontalGroup = {
    this.padBottom$field = padBottom
    return this
  }
  def padRight(padRight: scala.Float): HorizontalGroup = {
    this.padRight$field = padRight
    return this
  }
  def getPadTop(): scala.Float = {
    return this.padTop$field
  }
  def getPadLeft(): scala.Float = {
    return this.padLeft$field
  }
  def getPadBottom(): scala.Float = {
    return this.padBottom$field
  }
  def getPadRight(): scala.Float = {
    return this.padRight$field
  }
  def align(align: scala.Int): HorizontalGroup = {
    this.align$field = align
    return this
  }
  def center(): HorizontalGroup = {
    this.align$field = com.badlogic.gdx.utils.Align.center
    return this
  }
  def top(): HorizontalGroup = {
    this.align$field = this.align$field | com.badlogic.gdx.utils.Align.top
    this.align$field = this.align$field & (~com.badlogic.gdx.utils.Align.bottom)
    return this
  }
  def left(): HorizontalGroup = {
    this.align$field = this.align$field | com.badlogic.gdx.utils.Align.left
    this.align$field = this.align$field & (~com.badlogic.gdx.utils.Align.right)
    return this
  }
  def bottom(): HorizontalGroup = {
    this.align$field = this.align$field | com.badlogic.gdx.utils.Align.bottom
    this.align$field = this.align$field & (~com.badlogic.gdx.utils.Align.top)
    return this
  }
  def right(): HorizontalGroup = {
    this.align$field = this.align$field | com.badlogic.gdx.utils.Align.right
    this.align$field = this.align$field & (~com.badlogic.gdx.utils.Align.left)
    return this
  }
  def getAlign(): scala.Int = {
    return this.align$field
  }
  def fill(): HorizontalGroup = {
    this.fill$field = 1.0f
    return this
  }
  def fill(fill: scala.Float): HorizontalGroup = {
    this.fill$field = fill
    return this
  }
  def getFill(): scala.Float = {
    return this.fill$field
  }
  def expand(): HorizontalGroup = {
    this.expand$field = true
    return this
  }
  def expand(expand: scala.Boolean): HorizontalGroup = {
    this.expand$field = expand
    return this
  }
  def getExpand(): scala.Boolean = {
    return this.expand$field
  }
  def grow(): HorizontalGroup = {
    this.expand$field = true
    this.fill$field = 1
    return this
  }
  def wrap(): HorizontalGroup = {
    this.wrap$field = true
    return this
  }
  def wrap(wrap: scala.Boolean): HorizontalGroup = {
    this.wrap$field = wrap
    return this
  }
  def getWrap(): scala.Boolean = {
    return this.wrap$field
  }
  def rowAlign(rowAlign: scala.Int): HorizontalGroup = {
    this.rowAlign$field = rowAlign
    return this
  }
  def rowCenter(): HorizontalGroup = {
    this.rowAlign$field = com.badlogic.gdx.utils.Align.center
    return this
  }
  def rowTop(): HorizontalGroup = {
    this.rowAlign$field = this.rowAlign$field | com.badlogic.gdx.utils.Align.top
    this.rowAlign$field = this.rowAlign$field & (~com.badlogic.gdx.utils.Align.bottom)
    return this
  }
  def rowLeft(): HorizontalGroup = {
    this.rowAlign$field = this.rowAlign$field | com.badlogic.gdx.utils.Align.left
    this.rowAlign$field = this.rowAlign$field & (~com.badlogic.gdx.utils.Align.right)
    return this
  }
  def rowBottom(): HorizontalGroup = {
    this.rowAlign$field = this.rowAlign$field | com.badlogic.gdx.utils.Align.bottom
    this.rowAlign$field = this.rowAlign$field & (~com.badlogic.gdx.utils.Align.top)
    return this
  }
  def rowRight(): HorizontalGroup = {
    this.rowAlign$field = this.rowAlign$field | com.badlogic.gdx.utils.Align.right
    this.rowAlign$field = this.rowAlign$field & (~com.badlogic.gdx.utils.Align.left)
    return this
  }
  def drawDebugBounds(shapes: com.badlogic.gdx.graphics.glutils.ShapeRenderer): scala.Unit = {
    super.drawDebugBounds(shapes)
    if (!this.getDebug()) {
      return
    } else ()
    shapes.set(com.badlogic.gdx.graphics.glutils.ShapeRenderer.ShapeType.Line)
    if (this.getStage() != null) {
      shapes.setColor(this.getStage().getDebugColor())
    } else ()
    shapes.rect(this.getX() + this.padLeft$field, this.getY() + this.padBottom$field, this.getOriginX(), this.getOriginY(), (this.getWidth() - this.padLeft$field) - this.padRight$field, (this.getHeight() - this.padBottom$field) - this.padTop$field, this.getScaleX(), this.getScaleY(), this.getRotation())
  }
}
object HorizontalGroup {
  export com.badlogic.gdx.scenes.scene2d.ui.WidgetGroup.*
}