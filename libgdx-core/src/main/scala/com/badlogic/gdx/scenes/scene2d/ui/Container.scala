package com.badlogic.gdx.scenes.scene2d.ui

class Container[T <: com.badlogic.gdx.scenes.scene2d.Actor] extends com.badlogic.gdx.scenes.scene2d.ui.WidgetGroup {
  private var actor: T = null.asInstanceOf[T]
  var minWidth$field: com.badlogic.gdx.scenes.scene2d.ui.Value = com.badlogic.gdx.scenes.scene2d.ui.Value.minWidth
  var minHeight$field: com.badlogic.gdx.scenes.scene2d.ui.Value = com.badlogic.gdx.scenes.scene2d.ui.Value.minHeight
  var prefWidth$field: com.badlogic.gdx.scenes.scene2d.ui.Value = com.badlogic.gdx.scenes.scene2d.ui.Value.prefWidth
  var prefHeight$field: com.badlogic.gdx.scenes.scene2d.ui.Value = com.badlogic.gdx.scenes.scene2d.ui.Value.prefHeight
  var maxWidth$field: com.badlogic.gdx.scenes.scene2d.ui.Value = com.badlogic.gdx.scenes.scene2d.ui.Value.zero
  var maxHeight$field: com.badlogic.gdx.scenes.scene2d.ui.Value = com.badlogic.gdx.scenes.scene2d.ui.Value.zero
  var padTop$field: com.badlogic.gdx.scenes.scene2d.ui.Value = com.badlogic.gdx.scenes.scene2d.ui.Value.zero
  var padLeft$field: com.badlogic.gdx.scenes.scene2d.ui.Value = com.badlogic.gdx.scenes.scene2d.ui.Value.zero
  var padBottom$field: com.badlogic.gdx.scenes.scene2d.ui.Value = com.badlogic.gdx.scenes.scene2d.ui.Value.zero
  var padRight$field: com.badlogic.gdx.scenes.scene2d.ui.Value = com.badlogic.gdx.scenes.scene2d.ui.Value.zero
  var fillX$field: scala.Float = 0.0f
  var fillY$field: scala.Float = 0.0f
  var align$field: scala.Int = 0
  var background$field: com.badlogic.gdx.scenes.scene2d.utils.Drawable = null.asInstanceOf[com.badlogic.gdx.scenes.scene2d.utils.Drawable]
  var clip$field: scala.Boolean = false
  private var round: scala.Boolean = true
  private var actorCulling: com.badlogic.gdx.math.Rectangle = null.asInstanceOf[com.badlogic.gdx.math.Rectangle]
  def this(actor: T) = {
    this()
    this.setActor(actor)
  }
  this.setTouchable(com.badlogic.gdx.scenes.scene2d.Touchable.childrenOnly)
  this.setTransform(false)
  def draw(batch: com.badlogic.gdx.graphics.g2d.Batch, parentAlpha: scala.Float): scala.Unit = {
    this.validate()
    if (this.isTransform()) {
      this.applyTransform(batch, this.computeTransform())
      this.drawBackground(batch, parentAlpha, 0, 0)
      if (this.clip$field) {
        batch.flush()
        val padLeft: scala.Float = this.padLeft$field.get(this)
        val padBottom: scala.Float = this.padBottom$field.get(this)
        if (this.clipBegin(padLeft, padBottom, (this.getWidth() - padLeft) - this.padRight$field.get(this), (this.getHeight() - padBottom) - this.padTop$field.get(this))) {
          this.drawChildren(batch, parentAlpha)
          batch.flush()
          this.clipEnd()
        } else ()
      } else {
        this.drawChildren(batch, parentAlpha)
      }
      this.resetTransform(batch)
    } else {
      this.drawBackground(batch, parentAlpha, this.getX(), this.getY())
      super.draw(batch, parentAlpha)
    }
  }
  def drawBackground(batch: com.badlogic.gdx.graphics.g2d.Batch, parentAlpha: scala.Float, x: scala.Float, y: scala.Float): scala.Unit = {
    if (this.background$field == null) {
      return
    } else ()
    val color: com.badlogic.gdx.graphics.Color = this.getColor()
    batch.setColor(color.r, color.g, color.b, color.a * parentAlpha)
    this.background$field.draw(batch, x, y, this.getWidth(), this.getHeight())
  }
  def setBackground(background: com.badlogic.gdx.scenes.scene2d.utils.Drawable): scala.Unit = {
    this.setBackground(background, true)
  }
  def setBackground(background: com.badlogic.gdx.scenes.scene2d.utils.Drawable, adjustPadding: scala.Boolean): scala.Unit = {
    if (this.background$field == background) {
      return
    } else ()
    this.background$field = background
    if (adjustPadding) {
      if (background == null) {
        this.pad(com.badlogic.gdx.scenes.scene2d.ui.Value.zero)
      } else {
        this.pad(background.getTopHeight(), background.getLeftWidth(), background.getBottomHeight(), background.getRightWidth())
      }
      this.invalidate()
    } else ()
  }
  def background(background: com.badlogic.gdx.scenes.scene2d.utils.Drawable): Container[T] = {
    this.setBackground(background)
    return this
  }
  def getBackground(): com.badlogic.gdx.scenes.scene2d.utils.Drawable = {
    return this.background$field
  }
  def layout(): scala.Unit = {
    if (this.actor == null) {
      return
    } else ()
    val padLeft: scala.Float = this.padLeft$field.get(this)
    val padBottom: scala.Float = this.padBottom$field.get(this)
    val containerWidth: scala.Float = (this.getWidth() - padLeft) - this.padRight$field.get(this)
    val containerHeight: scala.Float = (this.getHeight() - padBottom) - this.padTop$field.get(this)
    val minWidth: scala.Float = this.minWidth$field.get(this.actor)
    val minHeight: scala.Float = this.minHeight$field.get(this.actor)
    val prefWidth: scala.Float = this.prefWidth$field.get(this.actor)
    val prefHeight: scala.Float = this.prefHeight$field.get(this.actor)
    val maxWidth: scala.Float = this.maxWidth$field.get(this.actor)
    val maxHeight: scala.Float = this.maxHeight$field.get(this.actor)
    var width: scala.Float = 0.0f
    if (this.fillX$field > 0) {
      width = containerWidth * this.fillX$field
    } else {
      width = java.lang.Math.min(prefWidth, containerWidth)
    }
    if (width < minWidth) {
      width = minWidth
    } else ()
    if ((maxWidth > 0) && (width > maxWidth)) {
      width = maxWidth
    } else ()
    var height: scala.Float = 0.0f
    if (this.fillY$field > 0) {
      height = containerHeight * this.fillY$field
    } else {
      height = java.lang.Math.min(prefHeight, containerHeight)
    }
    if (height < minHeight) {
      height = minHeight
    } else ()
    if ((maxHeight > 0) && (height > maxHeight)) {
      height = maxHeight
    } else ()
    var x: scala.Float = padLeft
    if ((this.align$field & com.badlogic.gdx.utils.Align.right) != 0) {
      x = x + (containerWidth - width)
    } else {
      if ((this.align$field & com.badlogic.gdx.utils.Align.left) == 0) {
        x = x + ((containerWidth - width) / 2)
      } else ()
    }
    var y: scala.Float = padBottom
    if ((this.align$field & com.badlogic.gdx.utils.Align.top) != 0) {
      y = y + (containerHeight - height)
    } else {
      if ((this.align$field & com.badlogic.gdx.utils.Align.bottom) == 0) {
        y = y + ((containerHeight - height) / 2)
      } else ()
    }
    if (this.round) {
      x = java.lang.Math.floor(x).asInstanceOf[scala.Float].asInstanceOf[scala.Float]
      y = java.lang.Math.floor(y).asInstanceOf[scala.Float].asInstanceOf[scala.Float]
      width = java.lang.Math.ceil(width).asInstanceOf[scala.Float].asInstanceOf[scala.Float]
      height = java.lang.Math.ceil(height).asInstanceOf[scala.Float].asInstanceOf[scala.Float]
    } else ()
    this.actor.setBounds(x, y, width, height)
    if (this.actor.isInstanceOf[com.badlogic.gdx.scenes.scene2d.utils.Layout]) {
      this.actor.asInstanceOf[com.badlogic.gdx.scenes.scene2d.utils.Layout].validate()
    } else ()
  }
  def setCullingArea(cullingArea$arg: com.badlogic.gdx.math.Rectangle): scala.Unit = {
    var cullingArea: com.badlogic.gdx.math.Rectangle = cullingArea$arg
    super.setCullingArea(cullingArea)
    if (this.actor.isInstanceOf[com.badlogic.gdx.scenes.scene2d.utils.Cullable]) {
      if (cullingArea != null) {
        if (this.actorCulling == null) {
          this.actorCulling = new com.badlogic.gdx.math.Rectangle()
        } else ()
        this.actorCulling.x = cullingArea.x - this.actor.getX()
        this.actorCulling.y = cullingArea.y - this.actor.getY()
        this.actorCulling.width = cullingArea.width
        this.actorCulling.height = cullingArea.height
        cullingArea = this.actorCulling
      } else ()
      this.actor.asInstanceOf[com.badlogic.gdx.scenes.scene2d.utils.Cullable].setCullingArea(cullingArea)
    } else ()
  }
  def setActor(actor: T): scala.Unit = {
    if (actor == this) {
      throw new java.lang.IllegalArgumentException("actor cannot be the Container.")
    } else ()
    if (actor == this.actor) {
      return
    } else ()
    if (this.actor != null) {
      super.removeActor(this.actor)
    } else ()
    this.actor = actor
    if (actor != null) {
      super.addActor(actor)
    } else ()
  }
  def getActor(): T = {
    return this.actor
  }
  def addActor(actor: com.badlogic.gdx.scenes.scene2d.Actor): scala.Unit = {
    throw new java.lang.UnsupportedOperationException("Use Container#setActor.")
  }
  def addActorAt(index: scala.Int, actor: com.badlogic.gdx.scenes.scene2d.Actor): scala.Unit = {
    throw new java.lang.UnsupportedOperationException("Use Container#setActor.")
  }
  def addActorBefore(actorBefore: com.badlogic.gdx.scenes.scene2d.Actor, actor: com.badlogic.gdx.scenes.scene2d.Actor): scala.Unit = {
    throw new java.lang.UnsupportedOperationException("Use Container#setActor.")
  }
  def addActorAfter(actorAfter: com.badlogic.gdx.scenes.scene2d.Actor, actor: com.badlogic.gdx.scenes.scene2d.Actor): scala.Unit = {
    throw new java.lang.UnsupportedOperationException("Use Container#setActor.")
  }
  def removeActor(actor: com.badlogic.gdx.scenes.scene2d.Actor): scala.Boolean = {
    if (actor == null) {
      throw new java.lang.IllegalArgumentException("actor cannot be null.")
    } else ()
    if (actor != this.actor) {
      return false
    } else ()
    this.setActor(null)
    return true
  }
  def removeActor(actor: com.badlogic.gdx.scenes.scene2d.Actor, unfocus: scala.Boolean): scala.Boolean = {
    if (actor == null) {
      throw new java.lang.IllegalArgumentException("actor cannot be null.")
    } else ()
    if (actor != this.actor) {
      return false
    } else ()
    this.actor = null.asInstanceOf[T]
    return super.removeActor(actor, unfocus)
  }
  def removeActorAt(index: scala.Int, unfocus: scala.Boolean): com.badlogic.gdx.scenes.scene2d.Actor = {
    var actor: com.badlogic.gdx.scenes.scene2d.Actor = super.removeActorAt(index, unfocus)
    if (actor == this.actor) {
      this.actor = null.asInstanceOf[T]
    } else ()
    return actor
  }
  def size(size: com.badlogic.gdx.scenes.scene2d.ui.Value): Container[T] = {
    if (size == null) {
      throw new java.lang.IllegalArgumentException("size cannot be null.")
    } else ()
    this.minWidth$field = size
    this.minHeight$field = size
    this.prefWidth$field = size
    this.prefHeight$field = size
    this.maxWidth$field = size
    this.maxHeight$field = size
    return this
  }
  def size(width: com.badlogic.gdx.scenes.scene2d.ui.Value, height: com.badlogic.gdx.scenes.scene2d.ui.Value): Container[T] = {
    if (width == null) {
      throw new java.lang.IllegalArgumentException("width cannot be null.")
    } else ()
    if (height == null) {
      throw new java.lang.IllegalArgumentException("height cannot be null.")
    } else ()
    this.minWidth$field = width
    this.minHeight$field = height
    this.prefWidth$field = width
    this.prefHeight$field = height
    this.maxWidth$field = width
    this.maxHeight$field = height
    return this
  }
  def size(size: scala.Float): Container[T] = {
    this.size(com.badlogic.gdx.scenes.scene2d.ui.Value.Fixed.valueOf(size))
    return this
  }
  def size(width: scala.Float, height: scala.Float): Container[T] = {
    this.size(com.badlogic.gdx.scenes.scene2d.ui.Value.Fixed.valueOf(width), com.badlogic.gdx.scenes.scene2d.ui.Value.Fixed.valueOf(height))
    return this
  }
  def width(width: com.badlogic.gdx.scenes.scene2d.ui.Value): Container[T] = {
    if (width == null) {
      throw new java.lang.IllegalArgumentException("width cannot be null.")
    } else ()
    this.minWidth$field = width
    this.prefWidth$field = width
    this.maxWidth$field = width
    return this
  }
  def width(width: scala.Float): Container[T] = {
    this.width(com.badlogic.gdx.scenes.scene2d.ui.Value.Fixed.valueOf(width))
    return this
  }
  def height(height: com.badlogic.gdx.scenes.scene2d.ui.Value): Container[T] = {
    if (height == null) {
      throw new java.lang.IllegalArgumentException("height cannot be null.")
    } else ()
    this.minHeight$field = height
    this.prefHeight$field = height
    this.maxHeight$field = height
    return this
  }
  def height(height: scala.Float): Container[T] = {
    this.height(com.badlogic.gdx.scenes.scene2d.ui.Value.Fixed.valueOf(height))
    return this
  }
  def minSize(size: com.badlogic.gdx.scenes.scene2d.ui.Value): Container[T] = {
    if (size == null) {
      throw new java.lang.IllegalArgumentException("size cannot be null.")
    } else ()
    this.minWidth$field = size
    this.minHeight$field = size
    return this
  }
  def minSize(width: com.badlogic.gdx.scenes.scene2d.ui.Value, height: com.badlogic.gdx.scenes.scene2d.ui.Value): Container[T] = {
    if (width == null) {
      throw new java.lang.IllegalArgumentException("width cannot be null.")
    } else ()
    if (height == null) {
      throw new java.lang.IllegalArgumentException("height cannot be null.")
    } else ()
    this.minWidth$field = width
    this.minHeight$field = height
    return this
  }
  def minWidth(minWidth: com.badlogic.gdx.scenes.scene2d.ui.Value): Container[T] = {
    if (minWidth == null) {
      throw new java.lang.IllegalArgumentException("minWidth cannot be null.")
    } else ()
    this.minWidth$field = minWidth
    return this
  }
  def minHeight(minHeight: com.badlogic.gdx.scenes.scene2d.ui.Value): Container[T] = {
    if (minHeight == null) {
      throw new java.lang.IllegalArgumentException("minHeight cannot be null.")
    } else ()
    this.minHeight$field = minHeight
    return this
  }
  def minSize(size: scala.Float): Container[T] = {
    this.minSize(com.badlogic.gdx.scenes.scene2d.ui.Value.Fixed.valueOf(size))
    return this
  }
  def minSize(width: scala.Float, height: scala.Float): Container[T] = {
    this.minSize(com.badlogic.gdx.scenes.scene2d.ui.Value.Fixed.valueOf(width), com.badlogic.gdx.scenes.scene2d.ui.Value.Fixed.valueOf(height))
    return this
  }
  def minWidth(minWidth: scala.Float): Container[T] = {
    this.minWidth$field = com.badlogic.gdx.scenes.scene2d.ui.Value.Fixed.valueOf(minWidth)
    return this
  }
  def minHeight(minHeight: scala.Float): Container[T] = {
    this.minHeight$field = com.badlogic.gdx.scenes.scene2d.ui.Value.Fixed.valueOf(minHeight)
    return this
  }
  def prefSize(size: com.badlogic.gdx.scenes.scene2d.ui.Value): Container[T] = {
    if (size == null) {
      throw new java.lang.IllegalArgumentException("size cannot be null.")
    } else ()
    this.prefWidth$field = size
    this.prefHeight$field = size
    return this
  }
  def prefSize(width: com.badlogic.gdx.scenes.scene2d.ui.Value, height: com.badlogic.gdx.scenes.scene2d.ui.Value): Container[T] = {
    if (width == null) {
      throw new java.lang.IllegalArgumentException("width cannot be null.")
    } else ()
    if (height == null) {
      throw new java.lang.IllegalArgumentException("height cannot be null.")
    } else ()
    this.prefWidth$field = width
    this.prefHeight$field = height
    return this
  }
  def prefWidth(prefWidth: com.badlogic.gdx.scenes.scene2d.ui.Value): Container[T] = {
    if (prefWidth == null) {
      throw new java.lang.IllegalArgumentException("prefWidth cannot be null.")
    } else ()
    this.prefWidth$field = prefWidth
    return this
  }
  def prefHeight(prefHeight: com.badlogic.gdx.scenes.scene2d.ui.Value): Container[T] = {
    if (prefHeight == null) {
      throw new java.lang.IllegalArgumentException("prefHeight cannot be null.")
    } else ()
    this.prefHeight$field = prefHeight
    return this
  }
  def prefSize(width: scala.Float, height: scala.Float): Container[T] = {
    this.prefSize(com.badlogic.gdx.scenes.scene2d.ui.Value.Fixed.valueOf(width), com.badlogic.gdx.scenes.scene2d.ui.Value.Fixed.valueOf(height))
    return this
  }
  def prefSize(size: scala.Float): Container[T] = {
    this.prefSize(com.badlogic.gdx.scenes.scene2d.ui.Value.Fixed.valueOf(size))
    return this
  }
  def prefWidth(prefWidth: scala.Float): Container[T] = {
    this.prefWidth$field = com.badlogic.gdx.scenes.scene2d.ui.Value.Fixed.valueOf(prefWidth)
    return this
  }
  def prefHeight(prefHeight: scala.Float): Container[T] = {
    this.prefHeight$field = com.badlogic.gdx.scenes.scene2d.ui.Value.Fixed.valueOf(prefHeight)
    return this
  }
  def maxSize(size: com.badlogic.gdx.scenes.scene2d.ui.Value): Container[T] = {
    if (size == null) {
      throw new java.lang.IllegalArgumentException("size cannot be null.")
    } else ()
    this.maxWidth$field = size
    this.maxHeight$field = size
    return this
  }
  def maxSize(width: com.badlogic.gdx.scenes.scene2d.ui.Value, height: com.badlogic.gdx.scenes.scene2d.ui.Value): Container[T] = {
    if (width == null) {
      throw new java.lang.IllegalArgumentException("width cannot be null.")
    } else ()
    if (height == null) {
      throw new java.lang.IllegalArgumentException("height cannot be null.")
    } else ()
    this.maxWidth$field = width
    this.maxHeight$field = height
    return this
  }
  def maxWidth(maxWidth: com.badlogic.gdx.scenes.scene2d.ui.Value): Container[T] = {
    if (maxWidth == null) {
      throw new java.lang.IllegalArgumentException("maxWidth cannot be null.")
    } else ()
    this.maxWidth$field = maxWidth
    return this
  }
  def maxHeight(maxHeight: com.badlogic.gdx.scenes.scene2d.ui.Value): Container[T] = {
    if (maxHeight == null) {
      throw new java.lang.IllegalArgumentException("maxHeight cannot be null.")
    } else ()
    this.maxHeight$field = maxHeight
    return this
  }
  def maxSize(size: scala.Float): Container[T] = {
    this.maxSize(com.badlogic.gdx.scenes.scene2d.ui.Value.Fixed.valueOf(size))
    return this
  }
  def maxSize(width: scala.Float, height: scala.Float): Container[T] = {
    this.maxSize(com.badlogic.gdx.scenes.scene2d.ui.Value.Fixed.valueOf(width), com.badlogic.gdx.scenes.scene2d.ui.Value.Fixed.valueOf(height))
    return this
  }
  def maxWidth(maxWidth: scala.Float): Container[T] = {
    this.maxWidth$field = com.badlogic.gdx.scenes.scene2d.ui.Value.Fixed.valueOf(maxWidth)
    return this
  }
  def maxHeight(maxHeight: scala.Float): Container[T] = {
    this.maxHeight$field = com.badlogic.gdx.scenes.scene2d.ui.Value.Fixed.valueOf(maxHeight)
    return this
  }
  def pad(pad: com.badlogic.gdx.scenes.scene2d.ui.Value): Container[T] = {
    if (pad == null) {
      throw new java.lang.IllegalArgumentException("pad cannot be null.")
    } else ()
    this.padTop$field = pad
    this.padLeft$field = pad
    this.padBottom$field = pad
    this.padRight$field = pad
    return this
  }
  def pad(top: com.badlogic.gdx.scenes.scene2d.ui.Value, left: com.badlogic.gdx.scenes.scene2d.ui.Value, bottom: com.badlogic.gdx.scenes.scene2d.ui.Value, right: com.badlogic.gdx.scenes.scene2d.ui.Value): Container[T] = {
    if (top == null) {
      throw new java.lang.IllegalArgumentException("top cannot be null.")
    } else ()
    if (left == null) {
      throw new java.lang.IllegalArgumentException("left cannot be null.")
    } else ()
    if (bottom == null) {
      throw new java.lang.IllegalArgumentException("bottom cannot be null.")
    } else ()
    if (right == null) {
      throw new java.lang.IllegalArgumentException("right cannot be null.")
    } else ()
    this.padTop$field = top
    this.padLeft$field = left
    this.padBottom$field = bottom
    this.padRight$field = right
    return this
  }
  def padTop(padTop: com.badlogic.gdx.scenes.scene2d.ui.Value): Container[T] = {
    if (padTop == null) {
      throw new java.lang.IllegalArgumentException("padTop cannot be null.")
    } else ()
    this.padTop$field = padTop
    return this
  }
  def padLeft(padLeft: com.badlogic.gdx.scenes.scene2d.ui.Value): Container[T] = {
    if (padLeft == null) {
      throw new java.lang.IllegalArgumentException("padLeft cannot be null.")
    } else ()
    this.padLeft$field = padLeft
    return this
  }
  def padBottom(padBottom: com.badlogic.gdx.scenes.scene2d.ui.Value): Container[T] = {
    if (padBottom == null) {
      throw new java.lang.IllegalArgumentException("padBottom cannot be null.")
    } else ()
    this.padBottom$field = padBottom
    return this
  }
  def padRight(padRight: com.badlogic.gdx.scenes.scene2d.ui.Value): Container[T] = {
    if (padRight == null) {
      throw new java.lang.IllegalArgumentException("padRight cannot be null.")
    } else ()
    this.padRight$field = padRight
    return this
  }
  def pad(pad: scala.Float): Container[T] = {
    val value: com.badlogic.gdx.scenes.scene2d.ui.Value = com.badlogic.gdx.scenes.scene2d.ui.Value.Fixed.valueOf(pad)
    this.padTop$field = value
    this.padLeft$field = value
    this.padBottom$field = value
    this.padRight$field = value
    return this
  }
  def pad(top: scala.Float, left: scala.Float, bottom: scala.Float, right: scala.Float): Container[T] = {
    this.padTop$field = com.badlogic.gdx.scenes.scene2d.ui.Value.Fixed.valueOf(top)
    this.padLeft$field = com.badlogic.gdx.scenes.scene2d.ui.Value.Fixed.valueOf(left)
    this.padBottom$field = com.badlogic.gdx.scenes.scene2d.ui.Value.Fixed.valueOf(bottom)
    this.padRight$field = com.badlogic.gdx.scenes.scene2d.ui.Value.Fixed.valueOf(right)
    return this
  }
  def padTop(padTop: scala.Float): Container[T] = {
    this.padTop$field = com.badlogic.gdx.scenes.scene2d.ui.Value.Fixed.valueOf(padTop)
    return this
  }
  def padLeft(padLeft: scala.Float): Container[T] = {
    this.padLeft$field = com.badlogic.gdx.scenes.scene2d.ui.Value.Fixed.valueOf(padLeft)
    return this
  }
  def padBottom(padBottom: scala.Float): Container[T] = {
    this.padBottom$field = com.badlogic.gdx.scenes.scene2d.ui.Value.Fixed.valueOf(padBottom)
    return this
  }
  def padRight(padRight: scala.Float): Container[T] = {
    this.padRight$field = com.badlogic.gdx.scenes.scene2d.ui.Value.Fixed.valueOf(padRight)
    return this
  }
  def fill(): Container[T] = {
    this.fillX$field = 1.0f
    this.fillY$field = 1.0f
    return this
  }
  def fillX(): Container[T] = {
    this.fillX$field = 1.0f
    return this
  }
  def fillY(): Container[T] = {
    this.fillY$field = 1.0f
    return this
  }
  def fill(x: scala.Float, y: scala.Float): Container[T] = {
    this.fillX$field = x
    this.fillY$field = y
    return this
  }
  def fill(x: scala.Boolean, y: scala.Boolean): Container[T] = {
    this.fillX$field = if (x) 1.0f else 0
    this.fillY$field = if (y) 1.0f else 0
    return this
  }
  def fill(fill: scala.Boolean): Container[T] = {
    this.fillX$field = if (fill) 1.0f else 0
    this.fillY$field = if (fill) 1.0f else 0
    return this
  }
  def align(align: scala.Int): Container[T] = {
    this.align$field = align
    return this
  }
  def center(): Container[T] = {
    this.align$field = com.badlogic.gdx.utils.Align.center
    return this
  }
  def top(): Container[T] = {
    this.align$field = this.align$field | com.badlogic.gdx.utils.Align.top
    this.align$field = this.align$field & (~com.badlogic.gdx.utils.Align.bottom)
    return this
  }
  def left(): Container[T] = {
    this.align$field = this.align$field | com.badlogic.gdx.utils.Align.left
    this.align$field = this.align$field & (~com.badlogic.gdx.utils.Align.right)
    return this
  }
  def bottom(): Container[T] = {
    this.align$field = this.align$field | com.badlogic.gdx.utils.Align.bottom
    this.align$field = this.align$field & (~com.badlogic.gdx.utils.Align.top)
    return this
  }
  def right(): Container[T] = {
    this.align$field = this.align$field | com.badlogic.gdx.utils.Align.right
    this.align$field = this.align$field & (~com.badlogic.gdx.utils.Align.left)
    return this
  }
  def getMinWidth(): scala.Float = {
    return (this.minWidth$field.get(this.actor) + this.padLeft$field.get(this)) + this.padRight$field.get(this)
  }
  def getMinHeightValue(): com.badlogic.gdx.scenes.scene2d.ui.Value = {
    return this.minHeight$field
  }
  def getMinHeight(): scala.Float = {
    return (this.minHeight$field.get(this.actor) + this.padTop$field.get(this)) + this.padBottom$field.get(this)
  }
  def getPrefWidthValue(): com.badlogic.gdx.scenes.scene2d.ui.Value = {
    return this.prefWidth$field
  }
  def getPrefWidth(): scala.Float = {
    var v: scala.Float = this.prefWidth$field.get(this.actor)
    if (this.background$field != null) {
      v = java.lang.Math.max(v, this.background$field.getMinWidth())
    } else ()
    return java.lang.Math.max(this.getMinWidth(), (v + this.padLeft$field.get(this)) + this.padRight$field.get(this))
  }
  def getPrefHeightValue(): com.badlogic.gdx.scenes.scene2d.ui.Value = {
    return this.prefHeight$field
  }
  def getPrefHeight(): scala.Float = {
    var v: scala.Float = this.prefHeight$field.get(this.actor)
    if (this.background$field != null) {
      v = java.lang.Math.max(v, this.background$field.getMinHeight())
    } else ()
    return java.lang.Math.max(this.getMinHeight(), (v + this.padTop$field.get(this)) + this.padBottom$field.get(this))
  }
  def getMaxWidthValue(): com.badlogic.gdx.scenes.scene2d.ui.Value = {
    return this.maxWidth$field
  }
  def getMaxWidth(): scala.Float = {
    var v: scala.Float = this.maxWidth$field.get(this.actor)
    if (v > 0) {
      v = v + (this.padLeft$field.get(this) + this.padRight$field.get(this))
    } else ()
    return v
  }
  def getMaxHeightValue(): com.badlogic.gdx.scenes.scene2d.ui.Value = {
    return this.maxHeight$field
  }
  def getMaxHeight(): scala.Float = {
    var v: scala.Float = this.maxHeight$field.get(this.actor)
    if (v > 0) {
      v = v + (this.padTop$field.get(this) + this.padBottom$field.get(this))
    } else ()
    return v
  }
  def getPadTopValue(): com.badlogic.gdx.scenes.scene2d.ui.Value = {
    return this.padTop$field
  }
  def getPadTop(): scala.Float = {
    return this.padTop$field.get(this)
  }
  def getPadLeftValue(): com.badlogic.gdx.scenes.scene2d.ui.Value = {
    return this.padLeft$field
  }
  def getPadLeft(): scala.Float = {
    return this.padLeft$field.get(this)
  }
  def getPadBottomValue(): com.badlogic.gdx.scenes.scene2d.ui.Value = {
    return this.padBottom$field
  }
  def getPadBottom(): scala.Float = {
    return this.padBottom$field.get(this)
  }
  def getPadRightValue(): com.badlogic.gdx.scenes.scene2d.ui.Value = {
    return this.padRight$field
  }
  def getPadRight(): scala.Float = {
    return this.padRight$field.get(this)
  }
  def getPadX(): scala.Float = {
    return this.padLeft$field.get(this) + this.padRight$field.get(this)
  }
  def getPadY(): scala.Float = {
    return this.padTop$field.get(this) + this.padBottom$field.get(this)
  }
  def getFillX(): scala.Float = {
    return this.fillX$field
  }
  def getFillY(): scala.Float = {
    return this.fillY$field
  }
  def getAlign(): scala.Int = {
    return this.align$field
  }
  def setRound(round: scala.Boolean): scala.Unit = {
    this.round = round
  }
  def clip(): Container[T] = {
    this.setClip(true)
    return this
  }
  def clip(enabled: scala.Boolean): Container[T] = {
    this.setClip(enabled)
    return this
  }
  def setClip(enabled: scala.Boolean): scala.Unit = {
    this.clip$field = enabled
    this.setTransform(enabled)
    this.invalidate()
  }
  def getClip(): scala.Boolean = {
    return this.clip$field
  }
  def hit(x: scala.Float, y: scala.Float, touchable: scala.Boolean): com.badlogic.gdx.scenes.scene2d.Actor = {
    if (this.clip$field) {
      if (touchable && (this.getTouchable() == com.badlogic.gdx.scenes.scene2d.Touchable.disabled)) {
        return null
      } else ()
      if ((((x < 0) || (x >= this.getWidth())) || (y < 0)) || (y >= this.getHeight())) {
        return null
      } else ()
    } else ()
    return super.hit(x, y, touchable)
  }
  def drawDebug(shapes: com.badlogic.gdx.graphics.glutils.ShapeRenderer): scala.Unit = {
    this.validate()
    if (this.isTransform()) {
      this.applyTransform(shapes, this.computeTransform())
      if (this.clip$field) {
        shapes.flush()
        val padLeft: scala.Float = this.padLeft$field.get(this)
        val padBottom: scala.Float = this.padBottom$field.get(this)
        val draw: scala.Boolean = if (this.background$field == null) this.clipBegin(0, 0, this.getWidth(), this.getHeight()) else this.clipBegin(padLeft, padBottom, (this.getWidth() - padLeft) - this.padRight$field.get(this), (this.getHeight() - padBottom) - this.padTop$field.get(this))
        if (draw) {
          this.drawDebugChildren(shapes)
          this.clipEnd()
        } else ()
      } else {
        this.drawDebugChildren(shapes)
      }
      this.resetTransform(shapes)
    } else {
      super.drawDebug(shapes)
    }
  }
}