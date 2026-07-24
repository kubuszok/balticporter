package com.badlogic.gdx.scenes.scene2d.ui

class VerticalGroup extends com.badlogic.gdx.scenes.scene2d.ui.WidgetGroup {
  private var prefWidth: scala.Float = 0.0f
  private var prefHeight: scala.Float = 0.0f
  private var lastPrefWidth: scala.Float = 0.0f
  private var sizeInvalid: scala.Boolean = true
  private var columnSizes: com.badlogic.gdx.utils.FloatArray = null.asInstanceOf[com.badlogic.gdx.utils.FloatArray]
  var align$field: scala.Int = com.badlogic.gdx.utils.Align.top
  var columnAlign$field: scala.Int = 0
  var reverse$field: scala.Boolean = false
  private var round: scala.Boolean = true
  var wrap$field: scala.Boolean = false
  var expand$field: scala.Boolean = false
  var space$field: scala.Float = 0.0f
  var wrapSpace$field: scala.Float = 0.0f
  var fill$field: scala.Float = 0.0f
  var padTop$field: scala.Float = 0.0f
  var padLeft$field: scala.Float = 0.0f
  var padBottom$field: scala.Float = 0.0f
  var padRight$field: scala.Float = 0.0f
  def this() = {
    this()
    this.setTransform(false)
    this.setTouchable(com.badlogic.gdx.scenes.scene2d.Touchable.childrenOnly)
  }
  def invalidate(): scala.Unit = {
    super.invalidate()
    this.sizeInvalid = true
  }
  private def computeSize(): scala.Unit = {
    this.sizeInvalid = false
    val children: com.badlogic.gdx.utils.SnapshotArray[com.badlogic.gdx.scenes.scene2d.Actor] = this.getChildren()
    var n: scala.Int = children.size
    this.prefWidth = 0
    if (this.wrap$field) {
      this.prefHeight = 0
      if (this.columnSizes == null) {
        this.columnSizes = new com.badlogic.gdx.utils.FloatArray()
      } else {
        this.columnSizes.clear()
      }
      var columnSizes: com.badlogic.gdx.utils.FloatArray = this.columnSizes
      val space: scala.Float = this.space$field
      val wrapSpace: scala.Float = this.wrapSpace$field
      val pad: scala.Float = this.padTop$field + this.padBottom$field
      val groupHeight: scala.Float = this.getHeight() - pad
      var x: scala.Float = 0
      var y: scala.Float = 0
      var columnWidth: scala.Float = 0
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
          height = layout.getPrefHeight()
          if (height > groupHeight) {
            height = java.lang.Math.max(groupHeight, layout.getMinHeight())
          } else ()
        } else {
          width = child.getWidth()
          height = child.getHeight()
        }
        var incrY: scala.Float = height + (if (y > 0) space else 0)
        if (((y + incrY) > groupHeight) && (y > 0)) {
          columnSizes.add(y)
          columnSizes.add(columnWidth)
          this.prefHeight = java.lang.Math.max(this.prefHeight, y + pad)
          if (x > 0) {
            x = x + wrapSpace
          } else ()
          x = x + columnWidth
          columnWidth = 0
          y = 0
          incrY = height
        } else ()
        y = y + incrY
        columnWidth = java.lang.Math.max(columnWidth, width)
      }; i = i + incr } }
      columnSizes.add(y)
      columnSizes.add(columnWidth)
      this.prefHeight = java.lang.Math.max(this.prefHeight, y + pad)
      if (x > 0) {
        x = x + wrapSpace
      } else ()
      this.prefWidth = java.lang.Math.max(this.prefWidth, x + columnWidth)
    } else {
      this.prefHeight = (this.padTop$field + this.padBottom$field) + (this.space$field * (n - 1));
      { var i: scala.Int = 0; while (i < n) { {
        val child: com.badlogic.gdx.scenes.scene2d.Actor = children.get(i)
        if (child.isInstanceOf[com.badlogic.gdx.scenes.scene2d.utils.Layout]) {
          val layout: com.badlogic.gdx.scenes.scene2d.utils.Layout = child.asInstanceOf[com.badlogic.gdx.scenes.scene2d.utils.Layout]
          this.prefWidth = java.lang.Math.max(this.prefWidth, layout.getPrefWidth())
          this.prefHeight = this.prefHeight + layout.getPrefHeight()
        } else {
          this.prefWidth = java.lang.Math.max(this.prefWidth, child.getWidth())
          this.prefHeight = this.prefHeight + child.getHeight()
        }
      }; i = i + 1 } }
    }
    this.prefWidth = this.prefWidth + (this.padLeft$field + this.padRight$field)
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
    val padLeft: scala.Float = this.padLeft$field
    val fill: scala.Float = this.fill$field
    val columnWidth: scala.Float = ((if (this.expand$field) this.getWidth() else this.prefWidth) - padLeft) - this.padRight$field
    var y: scala.Float = (this.prefHeight - this.padTop$field) + space
    if ((align & com.badlogic.gdx.utils.Align.top) != 0) {
      y = y + (this.getHeight() - this.prefHeight)
    } else {
      if ((align & com.badlogic.gdx.utils.Align.bottom) == 0) {
        y = y + ((this.getHeight() - this.prefHeight) / 2)
      } else ()
    }
    var startX: scala.Float = 0.0f
    if ((align & com.badlogic.gdx.utils.Align.left) != 0) {
      startX = padLeft
    } else {
      if ((align & com.badlogic.gdx.utils.Align.right) != 0) {
        startX = (this.getWidth() - this.padRight$field) - columnWidth
      } else {
        startX = padLeft + ((((this.getWidth() - padLeft) - this.padRight$field) - columnWidth) / 2)
      }
    }
    align = this.columnAlign$field
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
        width = columnWidth * fill
      } else ()
      if (layout != null) {
        width = java.lang.Math.max(width, layout.getMinWidth())
        val maxWidth: scala.Float = layout.getMaxWidth()
        if ((maxWidth > 0) && (width > maxWidth)) {
          width = maxWidth
        } else ()
      } else ()
      var x: scala.Float = startX
      if ((align & com.badlogic.gdx.utils.Align.right) != 0) {
        x = x + (columnWidth - width)
      } else {
        if ((align & com.badlogic.gdx.utils.Align.left) == 0) {
          x = x + ((columnWidth - width) / 2)
        } else ()
      }
      y = y - (height + space)
      if (round) {
        child.setBounds(java.lang.Math.floor(x).asInstanceOf[scala.Float], java.lang.Math.floor(y).asInstanceOf[scala.Float], java.lang.Math.ceil(width).asInstanceOf[scala.Float], java.lang.Math.ceil(height).asInstanceOf[scala.Float])
      } else {
        child.setBounds(x, y, width, height)
      }
      if (layout != null) {
        layout.validate()
      } else ()
    }; i = i + incr } }
  }
  private def layoutWrapped(): scala.Unit = {
    val prefWidth: scala.Float = this.getPrefWidth()
    if (prefWidth != this.lastPrefWidth) {
      this.lastPrefWidth = prefWidth
      this.invalidateHierarchy()
    } else ()
    var align: scala.Int = this.align$field
    val round: scala.Boolean = this.round
    val space: scala.Float = this.space$field
    val padLeft: scala.Float = this.padLeft$field
    val fill: scala.Float = this.fill$field
    val wrapSpace: scala.Float = this.wrapSpace$field
    val maxHeight: scala.Float = (this.prefHeight - this.padTop$field) - this.padBottom$field
    var columnX: scala.Float = padLeft
    var groupHeight: scala.Float = this.getHeight()
    var yStart: scala.Float = (this.prefHeight - this.padTop$field) + space
    var y: scala.Float = 0
    var columnWidth: scala.Float = 0
    if ((align & com.badlogic.gdx.utils.Align.right) != 0) {
      columnX = columnX + (this.getWidth() - prefWidth)
    } else {
      if ((align & com.badlogic.gdx.utils.Align.left) == 0) {
        columnX = columnX + ((this.getWidth() - prefWidth) / 2)
      } else ()
    }
    if ((align & com.badlogic.gdx.utils.Align.top) != 0) {
      yStart = yStart + (groupHeight - this.prefHeight)
    } else {
      if ((align & com.badlogic.gdx.utils.Align.bottom) == 0) {
        yStart = yStart + ((groupHeight - this.prefHeight) / 2)
      } else ()
    }
    groupHeight = groupHeight - this.padTop$field
    align = this.columnAlign$field
    val columnSizes: com.badlogic.gdx.utils.FloatArray = this.columnSizes
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
        height = layout.getPrefHeight()
        if (height > groupHeight) {
          height = java.lang.Math.max(groupHeight, layout.getMinHeight())
        } else ()
      } else {
        width = child.getWidth()
        height = child.getHeight()
      }
      if ((((y - height) - space) < this.padBottom$field) || (r == 0)) {
        r = java.lang.Math.min(r, columnSizes.size - 2)
        y = yStart
        if ((align & com.badlogic.gdx.utils.Align.bottom) != 0) {
          y = y - (maxHeight - columnSizes.get(r))
        } else {
          if ((align & com.badlogic.gdx.utils.Align.top) == 0) {
            y = y - ((maxHeight - columnSizes.get(r)) / 2)
          } else ()
        }
        if (r > 0) {
          columnX = columnX + wrapSpace
          columnX = columnX + columnWidth
        } else ()
        columnWidth = columnSizes.get(r + 1)
        r = r + 2
      } else ()
      if (fill > 0) {
        width = columnWidth * fill
      } else ()
      if (layout != null) {
        width = java.lang.Math.max(width, layout.getMinWidth())
        val maxWidth: scala.Float = layout.getMaxWidth()
        if ((maxWidth > 0) && (width > maxWidth)) {
          width = maxWidth
        } else ()
      } else ()
      var x: scala.Float = columnX
      if ((align & com.badlogic.gdx.utils.Align.right) != 0) {
        x = x + (columnWidth - width)
      } else {
        if ((align & com.badlogic.gdx.utils.Align.left) == 0) {
          x = x + ((columnWidth - width) / 2)
        } else ()
      }
      y = y - (height + space)
      if (round) {
        child.setBounds(java.lang.Math.floor(x).asInstanceOf[scala.Float], java.lang.Math.floor(y).asInstanceOf[scala.Float], java.lang.Math.ceil(width).asInstanceOf[scala.Float], java.lang.Math.ceil(height).asInstanceOf[scala.Float])
      } else {
        child.setBounds(x, y, width, height)
      }
      if (layout != null) {
        layout.validate()
      } else ()
    }; i = i + incr } }
  }
  def getPrefWidth(): scala.Float = {
    if (this.sizeInvalid) {
      this.computeSize()
    } else ()
    return this.prefWidth
  }
  def getPrefHeight(): scala.Float = {
    if (this.wrap$field) {
      return 0
    } else ()
    if (this.sizeInvalid) {
      this.computeSize()
    } else ()
    return this.prefHeight
  }
  def getColumns(): scala.Int = {
    return if (this.wrap$field) this.columnSizes.size >> 1 else 1
  }
  def setRound(round: scala.Boolean): scala.Unit = {
    this.round = round
  }
  def reverse(): VerticalGroup = {
    this.reverse$field = true
    return this
  }
  def reverse(reverse: scala.Boolean): VerticalGroup = {
    this.reverse$field = reverse
    return this
  }
  def getReverse(): scala.Boolean = {
    return this.reverse$field
  }
  def space(space: scala.Float): VerticalGroup = {
    this.space$field = space
    return this
  }
  def getSpace(): scala.Float = {
    return this.space$field
  }
  def wrapSpace(wrapSpace: scala.Float): VerticalGroup = {
    this.wrapSpace$field = wrapSpace
    return this
  }
  def getWrapSpace(): scala.Float = {
    return this.wrapSpace$field
  }
  def pad(pad: scala.Float): VerticalGroup = {
    this.padTop$field = pad
    this.padLeft$field = pad
    this.padBottom$field = pad
    this.padRight$field = pad
    return this
  }
  def pad(top: scala.Float, left: scala.Float, bottom: scala.Float, right: scala.Float): VerticalGroup = {
    this.padTop$field = top
    this.padLeft$field = left
    this.padBottom$field = bottom
    this.padRight$field = right
    return this
  }
  def padTop(padTop: scala.Float): VerticalGroup = {
    this.padTop$field = padTop
    return this
  }
  def padLeft(padLeft: scala.Float): VerticalGroup = {
    this.padLeft$field = padLeft
    return this
  }
  def padBottom(padBottom: scala.Float): VerticalGroup = {
    this.padBottom$field = padBottom
    return this
  }
  def padRight(padRight: scala.Float): VerticalGroup = {
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
  def align(align: scala.Int): VerticalGroup = {
    this.align$field = align
    return this
  }
  def center(): VerticalGroup = {
    this.align$field = com.badlogic.gdx.utils.Align.center
    return this
  }
  def top(): VerticalGroup = {
    this.align$field = this.align$field | com.badlogic.gdx.utils.Align.top
    this.align$field = this.align$field & (~com.badlogic.gdx.utils.Align.bottom)
    return this
  }
  def left(): VerticalGroup = {
    this.align$field = this.align$field | com.badlogic.gdx.utils.Align.left
    this.align$field = this.align$field & (~com.badlogic.gdx.utils.Align.right)
    return this
  }
  def bottom(): VerticalGroup = {
    this.align$field = this.align$field | com.badlogic.gdx.utils.Align.bottom
    this.align$field = this.align$field & (~com.badlogic.gdx.utils.Align.top)
    return this
  }
  def right(): VerticalGroup = {
    this.align$field = this.align$field | com.badlogic.gdx.utils.Align.right
    this.align$field = this.align$field & (~com.badlogic.gdx.utils.Align.left)
    return this
  }
  def getAlign(): scala.Int = {
    return this.align$field
  }
  def fill(): VerticalGroup = {
    this.fill$field = 1.0f
    return this
  }
  def fill(fill: scala.Float): VerticalGroup = {
    this.fill$field = fill
    return this
  }
  def getFill(): scala.Float = {
    return this.fill$field
  }
  def expand(): VerticalGroup = {
    this.expand$field = true
    return this
  }
  def expand(expand: scala.Boolean): VerticalGroup = {
    this.expand$field = expand
    return this
  }
  def getExpand(): scala.Boolean = {
    return this.expand$field
  }
  def grow(): VerticalGroup = {
    this.expand$field = true
    this.fill$field = 1
    return this
  }
  def wrap(): VerticalGroup = {
    this.wrap$field = true
    return this
  }
  def wrap(wrap: scala.Boolean): VerticalGroup = {
    this.wrap$field = wrap
    return this
  }
  def getWrap(): scala.Boolean = {
    return this.wrap$field
  }
  def columnAlign(columnAlign: scala.Int): VerticalGroup = {
    this.columnAlign$field = columnAlign
    return this
  }
  def columnCenter(): VerticalGroup = {
    this.columnAlign$field = com.badlogic.gdx.utils.Align.center
    return this
  }
  def columnTop(): VerticalGroup = {
    this.columnAlign$field = this.columnAlign$field | com.badlogic.gdx.utils.Align.top
    this.columnAlign$field = this.columnAlign$field & (~com.badlogic.gdx.utils.Align.bottom)
    return this
  }
  def columnLeft(): VerticalGroup = {
    this.columnAlign$field = this.columnAlign$field | com.badlogic.gdx.utils.Align.left
    this.columnAlign$field = this.columnAlign$field & (~com.badlogic.gdx.utils.Align.right)
    return this
  }
  def columnBottom(): VerticalGroup = {
    this.columnAlign$field = this.columnAlign$field | com.badlogic.gdx.utils.Align.bottom
    this.columnAlign$field = this.columnAlign$field & (~com.badlogic.gdx.utils.Align.top)
    return this
  }
  def columnRight(): VerticalGroup = {
    this.columnAlign$field = this.columnAlign$field | com.badlogic.gdx.utils.Align.right
    this.columnAlign$field = this.columnAlign$field & (~com.badlogic.gdx.utils.Align.left)
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