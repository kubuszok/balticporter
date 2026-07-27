package com.badlogic.gdx.scenes.scene2d.ui

class Cell[T <: com.badlogic.gdx.scenes.scene2d.Actor] extends com.badlogic.gdx.utils.Pool.Poolable {
  var minWidth$field: com.badlogic.gdx.scenes.scene2d.ui.Value = null.asInstanceOf[com.badlogic.gdx.scenes.scene2d.ui.Value]
  var minHeight$field: com.badlogic.gdx.scenes.scene2d.ui.Value = null.asInstanceOf[com.badlogic.gdx.scenes.scene2d.ui.Value]
  var prefWidth$field: com.badlogic.gdx.scenes.scene2d.ui.Value = null.asInstanceOf[com.badlogic.gdx.scenes.scene2d.ui.Value]
  var prefHeight$field: com.badlogic.gdx.scenes.scene2d.ui.Value = null.asInstanceOf[com.badlogic.gdx.scenes.scene2d.ui.Value]
  var maxWidth$field: com.badlogic.gdx.scenes.scene2d.ui.Value = null.asInstanceOf[com.badlogic.gdx.scenes.scene2d.ui.Value]
  var maxHeight$field: com.badlogic.gdx.scenes.scene2d.ui.Value = null.asInstanceOf[com.badlogic.gdx.scenes.scene2d.ui.Value]
  var spaceTop$field: com.badlogic.gdx.scenes.scene2d.ui.Value = null.asInstanceOf[com.badlogic.gdx.scenes.scene2d.ui.Value]
  var spaceLeft$field: com.badlogic.gdx.scenes.scene2d.ui.Value = null.asInstanceOf[com.badlogic.gdx.scenes.scene2d.ui.Value]
  var spaceBottom$field: com.badlogic.gdx.scenes.scene2d.ui.Value = null.asInstanceOf[com.badlogic.gdx.scenes.scene2d.ui.Value]
  var spaceRight$field: com.badlogic.gdx.scenes.scene2d.ui.Value = null.asInstanceOf[com.badlogic.gdx.scenes.scene2d.ui.Value]
  var padTop$field: com.badlogic.gdx.scenes.scene2d.ui.Value = null.asInstanceOf[com.badlogic.gdx.scenes.scene2d.ui.Value]
  var padLeft$field: com.badlogic.gdx.scenes.scene2d.ui.Value = null.asInstanceOf[com.badlogic.gdx.scenes.scene2d.ui.Value]
  var padBottom$field: com.badlogic.gdx.scenes.scene2d.ui.Value = null.asInstanceOf[com.badlogic.gdx.scenes.scene2d.ui.Value]
  var padRight$field: com.badlogic.gdx.scenes.scene2d.ui.Value = null.asInstanceOf[com.badlogic.gdx.scenes.scene2d.ui.Value]
  var fillX$field: java.lang.Float = null.asInstanceOf[java.lang.Float]
  var fillY$field: java.lang.Float = null.asInstanceOf[java.lang.Float]
  var align$field: java.lang.Integer = null.asInstanceOf[java.lang.Integer]
  var expandX$field: java.lang.Integer = null.asInstanceOf[java.lang.Integer]
  var expandY$field: java.lang.Integer = null.asInstanceOf[java.lang.Integer]
  var colspan$field: java.lang.Integer = null.asInstanceOf[java.lang.Integer]
  var uniformX$field: java.lang.Boolean = null.asInstanceOf[java.lang.Boolean]
  var uniformY$field: java.lang.Boolean = null.asInstanceOf[java.lang.Boolean]
  var actor: com.badlogic.gdx.scenes.scene2d.Actor = null.asInstanceOf[com.badlogic.gdx.scenes.scene2d.Actor]
  var actorX: scala.Float = 0.0f
  var actorY: scala.Float = 0.0f
  var actorWidth: scala.Float = 0.0f
  var actorHeight: scala.Float = 0.0f
  private var table: com.badlogic.gdx.scenes.scene2d.ui.Table = null.asInstanceOf[com.badlogic.gdx.scenes.scene2d.ui.Table]
  var endRow: scala.Boolean = false
  var column: scala.Int = 0
  var row$field: scala.Int = 0
  var cellAboveIndex: scala.Int = 0
  var computedPadTop: scala.Float = 0.0f
  var computedPadLeft: scala.Float = 0.0f
  var computedPadBottom: scala.Float = 0.0f
  var computedPadRight: scala.Float = 0.0f
  val defaults$p: Cell[T] = Cell.defaults().asInstanceOf[Cell[T]]
  this.cellAboveIndex = -1
  if (defaults$p != null) {
    this.set(defaults$p.asInstanceOf[Cell[T]])
  } else ()
  def setTable(table: com.badlogic.gdx.scenes.scene2d.ui.Table): scala.Unit = {
    this.table = table
  }
  def setActor[A <: com.badlogic.gdx.scenes.scene2d.Actor](newActor: A): Cell[A] = {
    if (this.actor != newActor) {
      if ((this.actor != null) && (this.actor.getParent() == this.table)) {
        this.actor.remove()
      } else ()
      this.actor = newActor
      if (newActor != null) {
        this.table.addActor(newActor)
      } else ()
    } else ()
    return this.asInstanceOf[Cell[A]]
  }
  def clearActor(): Cell[T] = {
    this.setActor(null)
    return this
  }
  def getActor(): T = {
    return this.actor.asInstanceOf[T]
  }
  def hasActor(): scala.Boolean = {
    return this.actor != null
  }
  def size(size: com.badlogic.gdx.scenes.scene2d.ui.Value): Cell[T] = {
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
  def size(width: com.badlogic.gdx.scenes.scene2d.ui.Value, height: com.badlogic.gdx.scenes.scene2d.ui.Value): Cell[T] = {
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
  def size(size: scala.Float): Cell[T] = {
    this.size(com.badlogic.gdx.scenes.scene2d.ui.Value.Fixed.valueOf(size))
    return this
  }
  def size(width: scala.Float, height: scala.Float): Cell[T] = {
    this.size(com.badlogic.gdx.scenes.scene2d.ui.Value.Fixed.valueOf(width), com.badlogic.gdx.scenes.scene2d.ui.Value.Fixed.valueOf(height))
    return this
  }
  def width(width: com.badlogic.gdx.scenes.scene2d.ui.Value): Cell[T] = {
    if (width == null) {
      throw new java.lang.IllegalArgumentException("width cannot be null.")
    } else ()
    this.minWidth$field = width
    this.prefWidth$field = width
    this.maxWidth$field = width
    return this
  }
  def width(width: scala.Float): Cell[T] = {
    this.width(com.badlogic.gdx.scenes.scene2d.ui.Value.Fixed.valueOf(width))
    return this
  }
  def height(height: com.badlogic.gdx.scenes.scene2d.ui.Value): Cell[T] = {
    if (height == null) {
      throw new java.lang.IllegalArgumentException("height cannot be null.")
    } else ()
    this.minHeight$field = height
    this.prefHeight$field = height
    this.maxHeight$field = height
    return this
  }
  def height(height: scala.Float): Cell[T] = {
    this.height(com.badlogic.gdx.scenes.scene2d.ui.Value.Fixed.valueOf(height))
    return this
  }
  def minSize(size: com.badlogic.gdx.scenes.scene2d.ui.Value): Cell[T] = {
    if (size == null) {
      throw new java.lang.IllegalArgumentException("size cannot be null.")
    } else ()
    this.minWidth$field = size
    this.minHeight$field = size
    return this
  }
  def minSize(width: com.badlogic.gdx.scenes.scene2d.ui.Value, height: com.badlogic.gdx.scenes.scene2d.ui.Value): Cell[T] = {
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
  def minWidth(minWidth: com.badlogic.gdx.scenes.scene2d.ui.Value): Cell[T] = {
    if (minWidth == null) {
      throw new java.lang.IllegalArgumentException("minWidth cannot be null.")
    } else ()
    this.minWidth$field = minWidth
    return this
  }
  def minHeight(minHeight: com.badlogic.gdx.scenes.scene2d.ui.Value): Cell[T] = {
    if (minHeight == null) {
      throw new java.lang.IllegalArgumentException("minHeight cannot be null.")
    } else ()
    this.minHeight$field = minHeight
    return this
  }
  def minSize(size: scala.Float): Cell[T] = {
    this.minSize(com.badlogic.gdx.scenes.scene2d.ui.Value.Fixed.valueOf(size))
    return this
  }
  def minSize(width: scala.Float, height: scala.Float): Cell[T] = {
    this.minSize(com.badlogic.gdx.scenes.scene2d.ui.Value.Fixed.valueOf(width), com.badlogic.gdx.scenes.scene2d.ui.Value.Fixed.valueOf(height))
    return this
  }
  def minWidth(minWidth: scala.Float): Cell[T] = {
    this.minWidth$field = com.badlogic.gdx.scenes.scene2d.ui.Value.Fixed.valueOf(minWidth)
    return this
  }
  def minHeight(minHeight: scala.Float): Cell[T] = {
    this.minHeight$field = com.badlogic.gdx.scenes.scene2d.ui.Value.Fixed.valueOf(minHeight)
    return this
  }
  def prefSize(size: com.badlogic.gdx.scenes.scene2d.ui.Value): Cell[T] = {
    if (size == null) {
      throw new java.lang.IllegalArgumentException("size cannot be null.")
    } else ()
    this.prefWidth$field = size
    this.prefHeight$field = size
    return this
  }
  def prefSize(width: com.badlogic.gdx.scenes.scene2d.ui.Value, height: com.badlogic.gdx.scenes.scene2d.ui.Value): Cell[T] = {
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
  def prefWidth(prefWidth: com.badlogic.gdx.scenes.scene2d.ui.Value): Cell[T] = {
    if (prefWidth == null) {
      throw new java.lang.IllegalArgumentException("prefWidth cannot be null.")
    } else ()
    this.prefWidth$field = prefWidth
    return this
  }
  def prefHeight(prefHeight: com.badlogic.gdx.scenes.scene2d.ui.Value): Cell[T] = {
    if (prefHeight == null) {
      throw new java.lang.IllegalArgumentException("prefHeight cannot be null.")
    } else ()
    this.prefHeight$field = prefHeight
    return this
  }
  def prefSize(width: scala.Float, height: scala.Float): Cell[T] = {
    this.prefSize(com.badlogic.gdx.scenes.scene2d.ui.Value.Fixed.valueOf(width), com.badlogic.gdx.scenes.scene2d.ui.Value.Fixed.valueOf(height))
    return this
  }
  def prefSize(size: scala.Float): Cell[T] = {
    this.prefSize(com.badlogic.gdx.scenes.scene2d.ui.Value.Fixed.valueOf(size))
    return this
  }
  def prefWidth(prefWidth: scala.Float): Cell[T] = {
    this.prefWidth$field = com.badlogic.gdx.scenes.scene2d.ui.Value.Fixed.valueOf(prefWidth)
    return this
  }
  def prefHeight(prefHeight: scala.Float): Cell[T] = {
    this.prefHeight$field = com.badlogic.gdx.scenes.scene2d.ui.Value.Fixed.valueOf(prefHeight)
    return this
  }
  def maxSize(size: com.badlogic.gdx.scenes.scene2d.ui.Value): Cell[T] = {
    if (size == null) {
      throw new java.lang.IllegalArgumentException("size cannot be null.")
    } else ()
    this.maxWidth$field = size
    this.maxHeight$field = size
    return this
  }
  def maxSize(width: com.badlogic.gdx.scenes.scene2d.ui.Value, height: com.badlogic.gdx.scenes.scene2d.ui.Value): Cell[T] = {
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
  def maxWidth(maxWidth: com.badlogic.gdx.scenes.scene2d.ui.Value): Cell[T] = {
    if (maxWidth == null) {
      throw new java.lang.IllegalArgumentException("maxWidth cannot be null.")
    } else ()
    this.maxWidth$field = maxWidth
    return this
  }
  def maxHeight(maxHeight: com.badlogic.gdx.scenes.scene2d.ui.Value): Cell[T] = {
    if (maxHeight == null) {
      throw new java.lang.IllegalArgumentException("maxHeight cannot be null.")
    } else ()
    this.maxHeight$field = maxHeight
    return this
  }
  def maxSize(size: scala.Float): Cell[T] = {
    this.maxSize(com.badlogic.gdx.scenes.scene2d.ui.Value.Fixed.valueOf(size))
    return this
  }
  def maxSize(width: scala.Float, height: scala.Float): Cell[T] = {
    this.maxSize(com.badlogic.gdx.scenes.scene2d.ui.Value.Fixed.valueOf(width), com.badlogic.gdx.scenes.scene2d.ui.Value.Fixed.valueOf(height))
    return this
  }
  def maxWidth(maxWidth: scala.Float): Cell[T] = {
    this.maxWidth$field = com.badlogic.gdx.scenes.scene2d.ui.Value.Fixed.valueOf(maxWidth)
    return this
  }
  def maxHeight(maxHeight: scala.Float): Cell[T] = {
    this.maxHeight$field = com.badlogic.gdx.scenes.scene2d.ui.Value.Fixed.valueOf(maxHeight)
    return this
  }
  def space(space: com.badlogic.gdx.scenes.scene2d.ui.Value): Cell[T] = {
    if (space == null) {
      throw new java.lang.IllegalArgumentException("space cannot be null.")
    } else ()
    this.spaceTop$field = space
    this.spaceLeft$field = space
    this.spaceBottom$field = space
    this.spaceRight$field = space
    return this
  }
  def space(top: com.badlogic.gdx.scenes.scene2d.ui.Value, left: com.badlogic.gdx.scenes.scene2d.ui.Value, bottom: com.badlogic.gdx.scenes.scene2d.ui.Value, right: com.badlogic.gdx.scenes.scene2d.ui.Value): Cell[T] = {
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
    this.spaceTop$field = top
    this.spaceLeft$field = left
    this.spaceBottom$field = bottom
    this.spaceRight$field = right
    return this
  }
  def spaceTop(spaceTop: com.badlogic.gdx.scenes.scene2d.ui.Value): Cell[T] = {
    if (spaceTop == null) {
      throw new java.lang.IllegalArgumentException("spaceTop cannot be null.")
    } else ()
    this.spaceTop$field = spaceTop
    return this
  }
  def spaceLeft(spaceLeft: com.badlogic.gdx.scenes.scene2d.ui.Value): Cell[T] = {
    if (spaceLeft == null) {
      throw new java.lang.IllegalArgumentException("spaceLeft cannot be null.")
    } else ()
    this.spaceLeft$field = spaceLeft
    return this
  }
  def spaceBottom(spaceBottom: com.badlogic.gdx.scenes.scene2d.ui.Value): Cell[T] = {
    if (spaceBottom == null) {
      throw new java.lang.IllegalArgumentException("spaceBottom cannot be null.")
    } else ()
    this.spaceBottom$field = spaceBottom
    return this
  }
  def spaceRight(spaceRight: com.badlogic.gdx.scenes.scene2d.ui.Value): Cell[T] = {
    if (spaceRight == null) {
      throw new java.lang.IllegalArgumentException("spaceRight cannot be null.")
    } else ()
    this.spaceRight$field = spaceRight
    return this
  }
  def space(space: scala.Float): Cell[T] = {
    if (space < 0) {
      throw new java.lang.IllegalArgumentException("space cannot be < 0: " + space)
    } else ()
    this.space(com.badlogic.gdx.scenes.scene2d.ui.Value.Fixed.valueOf(space))
    return this
  }
  def space(top: scala.Float, left: scala.Float, bottom: scala.Float, right: scala.Float): Cell[T] = {
    if (top < 0) {
      throw new java.lang.IllegalArgumentException("top cannot be < 0: " + top)
    } else ()
    if (left < 0) {
      throw new java.lang.IllegalArgumentException("left cannot be < 0: " + left)
    } else ()
    if (bottom < 0) {
      throw new java.lang.IllegalArgumentException("bottom cannot be < 0: " + bottom)
    } else ()
    if (right < 0) {
      throw new java.lang.IllegalArgumentException("right cannot be < 0: " + right)
    } else ()
    this.space(com.badlogic.gdx.scenes.scene2d.ui.Value.Fixed.valueOf(top), com.badlogic.gdx.scenes.scene2d.ui.Value.Fixed.valueOf(left), com.badlogic.gdx.scenes.scene2d.ui.Value.Fixed.valueOf(bottom), com.badlogic.gdx.scenes.scene2d.ui.Value.Fixed.valueOf(right))
    return this
  }
  def spaceTop(spaceTop: scala.Float): Cell[T] = {
    if (spaceTop < 0) {
      throw new java.lang.IllegalArgumentException("spaceTop cannot be < 0: " + spaceTop)
    } else ()
    this.spaceTop$field = com.badlogic.gdx.scenes.scene2d.ui.Value.Fixed.valueOf(spaceTop)
    return this
  }
  def spaceLeft(spaceLeft: scala.Float): Cell[T] = {
    if (spaceLeft < 0) {
      throw new java.lang.IllegalArgumentException("spaceLeft cannot be < 0: " + spaceLeft)
    } else ()
    this.spaceLeft$field = com.badlogic.gdx.scenes.scene2d.ui.Value.Fixed.valueOf(spaceLeft)
    return this
  }
  def spaceBottom(spaceBottom: scala.Float): Cell[T] = {
    if (spaceBottom < 0) {
      throw new java.lang.IllegalArgumentException("spaceBottom cannot be < 0: " + spaceBottom)
    } else ()
    this.spaceBottom$field = com.badlogic.gdx.scenes.scene2d.ui.Value.Fixed.valueOf(spaceBottom)
    return this
  }
  def spaceRight(spaceRight: scala.Float): Cell[T] = {
    if (spaceRight < 0) {
      throw new java.lang.IllegalArgumentException("spaceRight cannot be < 0: " + spaceRight)
    } else ()
    this.spaceRight$field = com.badlogic.gdx.scenes.scene2d.ui.Value.Fixed.valueOf(spaceRight)
    return this
  }
  def pad(pad: com.badlogic.gdx.scenes.scene2d.ui.Value): Cell[T] = {
    if (pad == null) {
      throw new java.lang.IllegalArgumentException("pad cannot be null.")
    } else ()
    this.padTop$field = pad
    this.padLeft$field = pad
    this.padBottom$field = pad
    this.padRight$field = pad
    return this
  }
  def pad(top: com.badlogic.gdx.scenes.scene2d.ui.Value, left: com.badlogic.gdx.scenes.scene2d.ui.Value, bottom: com.badlogic.gdx.scenes.scene2d.ui.Value, right: com.badlogic.gdx.scenes.scene2d.ui.Value): Cell[T] = {
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
  def padTop(padTop: com.badlogic.gdx.scenes.scene2d.ui.Value): Cell[T] = {
    if (padTop == null) {
      throw new java.lang.IllegalArgumentException("padTop cannot be null.")
    } else ()
    this.padTop$field = padTop
    return this
  }
  def padLeft(padLeft: com.badlogic.gdx.scenes.scene2d.ui.Value): Cell[T] = {
    if (padLeft == null) {
      throw new java.lang.IllegalArgumentException("padLeft cannot be null.")
    } else ()
    this.padLeft$field = padLeft
    return this
  }
  def padBottom(padBottom: com.badlogic.gdx.scenes.scene2d.ui.Value): Cell[T] = {
    if (padBottom == null) {
      throw new java.lang.IllegalArgumentException("padBottom cannot be null.")
    } else ()
    this.padBottom$field = padBottom
    return this
  }
  def padRight(padRight: com.badlogic.gdx.scenes.scene2d.ui.Value): Cell[T] = {
    if (padRight == null) {
      throw new java.lang.IllegalArgumentException("padRight cannot be null.")
    } else ()
    this.padRight$field = padRight
    return this
  }
  def pad(pad: scala.Float): Cell[T] = {
    this.pad(com.badlogic.gdx.scenes.scene2d.ui.Value.Fixed.valueOf(pad))
    return this
  }
  def pad(top: scala.Float, left: scala.Float, bottom: scala.Float, right: scala.Float): Cell[T] = {
    this.pad(com.badlogic.gdx.scenes.scene2d.ui.Value.Fixed.valueOf(top), com.badlogic.gdx.scenes.scene2d.ui.Value.Fixed.valueOf(left), com.badlogic.gdx.scenes.scene2d.ui.Value.Fixed.valueOf(bottom), com.badlogic.gdx.scenes.scene2d.ui.Value.Fixed.valueOf(right))
    return this
  }
  def padTop(padTop: scala.Float): Cell[T] = {
    this.padTop$field = com.badlogic.gdx.scenes.scene2d.ui.Value.Fixed.valueOf(padTop)
    return this
  }
  def padLeft(padLeft: scala.Float): Cell[T] = {
    this.padLeft$field = com.badlogic.gdx.scenes.scene2d.ui.Value.Fixed.valueOf(padLeft)
    return this
  }
  def padBottom(padBottom: scala.Float): Cell[T] = {
    this.padBottom$field = com.badlogic.gdx.scenes.scene2d.ui.Value.Fixed.valueOf(padBottom)
    return this
  }
  def padRight(padRight: scala.Float): Cell[T] = {
    this.padRight$field = com.badlogic.gdx.scenes.scene2d.ui.Value.Fixed.valueOf(padRight)
    return this
  }
  def fill(): Cell[T] = {
    this.fillX$field = Cell.onef
    this.fillY$field = Cell.onef
    return this
  }
  def fillX(): Cell[T] = {
    this.fillX$field = Cell.onef
    return this
  }
  def fillY(): Cell[T] = {
    this.fillY$field = Cell.onef
    return this
  }
  def fill(x: scala.Float, y: scala.Float): Cell[T] = {
    this.fillX$field = x.asInstanceOf[java.lang.Float]
    this.fillY$field = y.asInstanceOf[java.lang.Float]
    return this
  }
  def fill(x: scala.Boolean, y: scala.Boolean): Cell[T] = {
    this.fillX$field = if (x) Cell.onef else Cell.zerof
    this.fillY$field = if (y) Cell.onef else Cell.zerof
    return this
  }
  def fill(fill: scala.Boolean): Cell[T] = {
    this.fillX$field = if (fill) Cell.onef else Cell.zerof
    this.fillY$field = if (fill) Cell.onef else Cell.zerof
    return this
  }
  def align(align: scala.Int): Cell[T] = {
    this.align$field = align.asInstanceOf[java.lang.Integer]
    return this
  }
  def center(): Cell[T] = {
    this.align$field = Cell.centeri
    return this
  }
  def top(): Cell[T] = {
    if (this.align$field == null) {
      this.align$field = Cell.topi
    } else {
      this.align$field = ((this.align$field | com.badlogic.gdx.utils.Align.top) & (~com.badlogic.gdx.utils.Align.bottom)).asInstanceOf[java.lang.Integer]
    }
    return this
  }
  def left(): Cell[T] = {
    if (this.align$field == null) {
      this.align$field = Cell.lefti
    } else {
      this.align$field = ((this.align$field | com.badlogic.gdx.utils.Align.left) & (~com.badlogic.gdx.utils.Align.right)).asInstanceOf[java.lang.Integer]
    }
    return this
  }
  def bottom(): Cell[T] = {
    if (this.align$field == null) {
      this.align$field = Cell.bottomi
    } else {
      this.align$field = ((this.align$field | com.badlogic.gdx.utils.Align.bottom) & (~com.badlogic.gdx.utils.Align.top)).asInstanceOf[java.lang.Integer]
    }
    return this
  }
  def right(): Cell[T] = {
    if (this.align$field == null) {
      this.align$field = Cell.righti
    } else {
      this.align$field = ((this.align$field | com.badlogic.gdx.utils.Align.right) & (~com.badlogic.gdx.utils.Align.left)).asInstanceOf[java.lang.Integer]
    }
    return this
  }
  def grow(): Cell[T] = {
    this.expandX$field = Cell.onei
    this.expandY$field = Cell.onei
    this.fillX$field = Cell.onef
    this.fillY$field = Cell.onef
    return this
  }
  def growX(): Cell[T] = {
    this.expandX$field = Cell.onei
    this.fillX$field = Cell.onef
    return this
  }
  def growY(): Cell[T] = {
    this.expandY$field = Cell.onei
    this.fillY$field = Cell.onef
    return this
  }
  def expand(): Cell[T] = {
    this.expandX$field = Cell.onei
    this.expandY$field = Cell.onei
    return this
  }
  def expandX(): Cell[T] = {
    this.expandX$field = Cell.onei
    return this
  }
  def expandY(): Cell[T] = {
    this.expandY$field = Cell.onei
    return this
  }
  def expand(x: scala.Int, y: scala.Int): Cell[T] = {
    this.expandX$field = x.asInstanceOf[java.lang.Integer]
    this.expandY$field = y.asInstanceOf[java.lang.Integer]
    return this
  }
  def expand(x: scala.Boolean, y: scala.Boolean): Cell[T] = {
    this.expandX$field = if (x) Cell.onei else Cell.zeroi
    this.expandY$field = if (y) Cell.onei else Cell.zeroi
    return this
  }
  def colspan(colspan: scala.Int): Cell[T] = {
    this.colspan$field = colspan.asInstanceOf[java.lang.Integer]
    return this
  }
  def uniform(): Cell[T] = {
    this.uniformX$field = java.lang.Boolean.TRUE
    this.uniformY$field = java.lang.Boolean.TRUE
    return this
  }
  def uniformX(): Cell[T] = {
    this.uniformX$field = java.lang.Boolean.TRUE
    return this
  }
  def uniformY(): Cell[T] = {
    this.uniformY$field = java.lang.Boolean.TRUE
    return this
  }
  def uniform(uniform: scala.Boolean): Cell[T] = {
    this.uniformX$field = uniform.asInstanceOf[java.lang.Boolean]
    this.uniformY$field = uniform.asInstanceOf[java.lang.Boolean]
    return this
  }
  def uniform(x: scala.Boolean, y: scala.Boolean): Cell[T] = {
    this.uniformX$field = x.asInstanceOf[java.lang.Boolean]
    this.uniformY$field = y.asInstanceOf[java.lang.Boolean]
    return this
  }
  def setActorBounds(x: scala.Float, y: scala.Float, width: scala.Float, height: scala.Float): scala.Unit = {
    this.actorX = x
    this.actorY = y
    this.actorWidth = width
    this.actorHeight = height
  }
  def getActorX(): scala.Float = {
    return this.actorX
  }
  def setActorX(actorX: scala.Float): scala.Unit = {
    this.actorX = actorX
  }
  def getActorY(): scala.Float = {
    return this.actorY
  }
  def setActorY(actorY: scala.Float): scala.Unit = {
    this.actorY = actorY
  }
  def getActorWidth(): scala.Float = {
    return this.actorWidth
  }
  def setActorWidth(actorWidth: scala.Float): scala.Unit = {
    this.actorWidth = actorWidth
  }
  def getActorHeight(): scala.Float = {
    return this.actorHeight
  }
  def setActorHeight(actorHeight: scala.Float): scala.Unit = {
    this.actorHeight = actorHeight
  }
  def getColumn(): scala.Int = {
    return this.column
  }
  def getRow(): scala.Int = {
    return this.row$field
  }
  def getMinWidthValue(): com.badlogic.gdx.scenes.scene2d.ui.Value = {
    return this.minWidth$field
  }
  def getMinWidth(): scala.Float = {
    return this.minWidth$field.get(this.actor)
  }
  def getMinHeightValue(): com.badlogic.gdx.scenes.scene2d.ui.Value = {
    return this.minHeight$field
  }
  def getMinHeight(): scala.Float = {
    return this.minHeight$field.get(this.actor)
  }
  def getPrefWidthValue(): com.badlogic.gdx.scenes.scene2d.ui.Value = {
    return this.prefWidth$field
  }
  def getPrefWidth(): scala.Float = {
    return this.prefWidth$field.get(this.actor)
  }
  def getPrefHeightValue(): com.badlogic.gdx.scenes.scene2d.ui.Value = {
    return this.prefHeight$field
  }
  def getPrefHeight(): scala.Float = {
    return this.prefHeight$field.get(this.actor)
  }
  def getMaxWidthValue(): com.badlogic.gdx.scenes.scene2d.ui.Value = {
    return this.maxWidth$field
  }
  def getMaxWidth(): scala.Float = {
    return this.maxWidth$field.get(this.actor)
  }
  def getMaxHeightValue(): com.badlogic.gdx.scenes.scene2d.ui.Value = {
    return this.maxHeight$field
  }
  def getMaxHeight(): scala.Float = {
    return this.maxHeight$field.get(this.actor)
  }
  def getSpaceTopValue(): com.badlogic.gdx.scenes.scene2d.ui.Value = {
    return this.spaceTop$field
  }
  def getSpaceTop(): scala.Float = {
    return this.spaceTop$field.get(this.actor)
  }
  def getSpaceLeftValue(): com.badlogic.gdx.scenes.scene2d.ui.Value = {
    return this.spaceLeft$field
  }
  def getSpaceLeft(): scala.Float = {
    return this.spaceLeft$field.get(this.actor)
  }
  def getSpaceBottomValue(): com.badlogic.gdx.scenes.scene2d.ui.Value = {
    return this.spaceBottom$field
  }
  def getSpaceBottom(): scala.Float = {
    return this.spaceBottom$field.get(this.actor)
  }
  def getSpaceRightValue(): com.badlogic.gdx.scenes.scene2d.ui.Value = {
    return this.spaceRight$field
  }
  def getSpaceRight(): scala.Float = {
    return this.spaceRight$field.get(this.actor)
  }
  def getPadTopValue(): com.badlogic.gdx.scenes.scene2d.ui.Value = {
    return this.padTop$field
  }
  def getPadTop(): scala.Float = {
    return this.padTop$field.get(this.actor)
  }
  def getPadLeftValue(): com.badlogic.gdx.scenes.scene2d.ui.Value = {
    return this.padLeft$field
  }
  def getPadLeft(): scala.Float = {
    return this.padLeft$field.get(this.actor)
  }
  def getPadBottomValue(): com.badlogic.gdx.scenes.scene2d.ui.Value = {
    return this.padBottom$field
  }
  def getPadBottom(): scala.Float = {
    return this.padBottom$field.get(this.actor)
  }
  def getPadRightValue(): com.badlogic.gdx.scenes.scene2d.ui.Value = {
    return this.padRight$field
  }
  def getPadRight(): scala.Float = {
    return this.padRight$field.get(this.actor)
  }
  def getPadX(): scala.Float = {
    return this.padLeft$field.get(this.actor) + this.padRight$field.get(this.actor)
  }
  def getPadY(): scala.Float = {
    return this.padTop$field.get(this.actor) + this.padBottom$field.get(this.actor)
  }
  def getFillX(): java.lang.Float = {
    return this.fillX$field
  }
  def getFillY(): java.lang.Float = {
    return this.fillY$field
  }
  def getAlign(): java.lang.Integer = {
    return this.align$field
  }
  def getExpandX(): java.lang.Integer = {
    return this.expandX$field
  }
  def getExpandY(): java.lang.Integer = {
    return this.expandY$field
  }
  def getColspan(): java.lang.Integer = {
    return this.colspan$field
  }
  def getUniformX(): java.lang.Boolean = {
    return this.uniformX$field
  }
  def getUniformY(): java.lang.Boolean = {
    return this.uniformY$field
  }
  def isEndRow(): scala.Boolean = {
    return this.endRow
  }
  def getComputedPadTop(): scala.Float = {
    return this.computedPadTop
  }
  def getComputedPadLeft(): scala.Float = {
    return this.computedPadLeft
  }
  def getComputedPadBottom(): scala.Float = {
    return this.computedPadBottom
  }
  def getComputedPadRight(): scala.Float = {
    return this.computedPadRight
  }
  def row(): scala.Unit = {
    this.table.row()
  }
  def getTable(): com.badlogic.gdx.scenes.scene2d.ui.Table = {
    return this.table
  }
  def clear(): scala.Unit = {
    this.minWidth$field = null
    this.minHeight$field = null
    this.prefWidth$field = null
    this.prefHeight$field = null
    this.maxWidth$field = null
    this.maxHeight$field = null
    this.spaceTop$field = null
    this.spaceLeft$field = null
    this.spaceBottom$field = null
    this.spaceRight$field = null
    this.padTop$field = null
    this.padLeft$field = null
    this.padBottom$field = null
    this.padRight$field = null
    this.fillX$field = null
    this.fillY$field = null
    this.align$field = null
    this.expandX$field = null
    this.expandY$field = null
    this.colspan$field = null
    this.uniformX$field = null
    this.uniformY$field = null
  }
  def reset(): scala.Unit = {
    this.actor = null
    this.table = null
    this.endRow = false
    this.cellAboveIndex = -1
    this.set(Cell.defaults().asInstanceOf[Cell[T]])
  }
  def set(cell: Cell[T]): scala.Unit = {
    this.minWidth$field = cell.minWidth$field
    this.minHeight$field = cell.minHeight$field
    this.prefWidth$field = cell.prefWidth$field
    this.prefHeight$field = cell.prefHeight$field
    this.maxWidth$field = cell.maxWidth$field
    this.maxHeight$field = cell.maxHeight$field
    this.spaceTop$field = cell.spaceTop$field
    this.spaceLeft$field = cell.spaceLeft$field
    this.spaceBottom$field = cell.spaceBottom$field
    this.spaceRight$field = cell.spaceRight$field
    this.padTop$field = cell.padTop$field
    this.padLeft$field = cell.padLeft$field
    this.padBottom$field = cell.padBottom$field
    this.padRight$field = cell.padRight$field
    this.fillX$field = cell.fillX$field
    this.fillY$field = cell.fillY$field
    this.align$field = cell.align$field
    this.expandX$field = cell.expandX$field
    this.expandY$field = cell.expandY$field
    this.colspan$field = cell.colspan$field
    this.uniformX$field = cell.uniformX$field
    this.uniformY$field = cell.uniformY$field
  }
  def merge(cell: Cell[T]): scala.Unit = {
    if (cell == null) {
      return
    } else ()
    if (cell.minWidth$field != null) {
      this.minWidth$field = cell.minWidth$field
    } else ()
    if (cell.minHeight$field != null) {
      this.minHeight$field = cell.minHeight$field
    } else ()
    if (cell.prefWidth$field != null) {
      this.prefWidth$field = cell.prefWidth$field
    } else ()
    if (cell.prefHeight$field != null) {
      this.prefHeight$field = cell.prefHeight$field
    } else ()
    if (cell.maxWidth$field != null) {
      this.maxWidth$field = cell.maxWidth$field
    } else ()
    if (cell.maxHeight$field != null) {
      this.maxHeight$field = cell.maxHeight$field
    } else ()
    if (cell.spaceTop$field != null) {
      this.spaceTop$field = cell.spaceTop$field
    } else ()
    if (cell.spaceLeft$field != null) {
      this.spaceLeft$field = cell.spaceLeft$field
    } else ()
    if (cell.spaceBottom$field != null) {
      this.spaceBottom$field = cell.spaceBottom$field
    } else ()
    if (cell.spaceRight$field != null) {
      this.spaceRight$field = cell.spaceRight$field
    } else ()
    if (cell.padTop$field != null) {
      this.padTop$field = cell.padTop$field
    } else ()
    if (cell.padLeft$field != null) {
      this.padLeft$field = cell.padLeft$field
    } else ()
    if (cell.padBottom$field != null) {
      this.padBottom$field = cell.padBottom$field
    } else ()
    if (cell.padRight$field != null) {
      this.padRight$field = cell.padRight$field
    } else ()
    if (cell.fillX$field != null) {
      this.fillX$field = cell.fillX$field
    } else ()
    if (cell.fillY$field != null) {
      this.fillY$field = cell.fillY$field
    } else ()
    if (cell.align$field != null) {
      this.align$field = cell.align$field
    } else ()
    if (cell.expandX$field != null) {
      this.expandX$field = cell.expandX$field
    } else ()
    if (cell.expandY$field != null) {
      this.expandY$field = cell.expandY$field
    } else ()
    if (cell.colspan$field != null) {
      this.colspan$field = cell.colspan$field
    } else ()
    if (cell.uniformX$field != null) {
      this.uniformX$field = cell.uniformX$field
    } else ()
    if (cell.uniformY$field != null) {
      this.uniformY$field = cell.uniformY$field
    } else ()
  }
  def toString(): java.lang.String = {
    return if (this.actor != null) this.actor.toString() else super.toString()
  }
}
object Cell {
  private final val zerof: java.lang.Float = 0.0f.asInstanceOf[java.lang.Float]
  private final val onef: java.lang.Float = 1.0f.asInstanceOf[java.lang.Float]
  private final val zeroi: java.lang.Integer = 0.asInstanceOf[java.lang.Integer]
  private final val onei: java.lang.Integer = 1.asInstanceOf[java.lang.Integer]
  private final val centeri: java.lang.Integer = Cell.onei
  private final val topi: java.lang.Integer = com.badlogic.gdx.utils.Align.top.asInstanceOf[java.lang.Integer]
  private final val bottomi: java.lang.Integer = com.badlogic.gdx.utils.Align.bottom.asInstanceOf[java.lang.Integer]
  private final val lefti: java.lang.Integer = com.badlogic.gdx.utils.Align.left.asInstanceOf[java.lang.Integer]
  private final val righti: java.lang.Integer = com.badlogic.gdx.utils.Align.right.asInstanceOf[java.lang.Integer]
  private var files: com.badlogic.gdx.Files = null.asInstanceOf[com.badlogic.gdx.Files]
  var defaults$field: Cell[?] = null.asInstanceOf[Cell[?]]
  def defaults(): Cell[?] = {
    if ((Cell.files == null) || (Cell.files != com.badlogic.gdx.Gdx.files)) {
      Cell.files = com.badlogic.gdx.Gdx.files
      Cell.defaults$field = new Cell().asInstanceOf[Cell[?]]
      Cell.defaults$field.minWidth$field = com.badlogic.gdx.scenes.scene2d.ui.Value.minWidth
      Cell.defaults$field.minHeight$field = com.badlogic.gdx.scenes.scene2d.ui.Value.minHeight
      Cell.defaults$field.prefWidth$field = com.badlogic.gdx.scenes.scene2d.ui.Value.prefWidth
      Cell.defaults$field.prefHeight$field = com.badlogic.gdx.scenes.scene2d.ui.Value.prefHeight
      Cell.defaults$field.maxWidth$field = com.badlogic.gdx.scenes.scene2d.ui.Value.maxWidth
      Cell.defaults$field.maxHeight$field = com.badlogic.gdx.scenes.scene2d.ui.Value.maxHeight
      Cell.defaults$field.spaceTop$field = com.badlogic.gdx.scenes.scene2d.ui.Value.zero
      Cell.defaults$field.spaceLeft$field = com.badlogic.gdx.scenes.scene2d.ui.Value.zero
      Cell.defaults$field.spaceBottom$field = com.badlogic.gdx.scenes.scene2d.ui.Value.zero
      Cell.defaults$field.spaceRight$field = com.badlogic.gdx.scenes.scene2d.ui.Value.zero
      Cell.defaults$field.padTop$field = com.badlogic.gdx.scenes.scene2d.ui.Value.zero
      Cell.defaults$field.padLeft$field = com.badlogic.gdx.scenes.scene2d.ui.Value.zero
      Cell.defaults$field.padBottom$field = com.badlogic.gdx.scenes.scene2d.ui.Value.zero
      Cell.defaults$field.padRight$field = com.badlogic.gdx.scenes.scene2d.ui.Value.zero
      Cell.defaults$field.fillX$field = Cell.zerof
      Cell.defaults$field.fillY$field = Cell.zerof
      Cell.defaults$field.align$field = Cell.centeri
      Cell.defaults$field.expandX$field = Cell.zeroi
      Cell.defaults$field.expandY$field = Cell.zeroi
      Cell.defaults$field.colspan$field = Cell.onei
      Cell.defaults$field.uniformX$field = null
      Cell.defaults$field.uniformY$field = null
    } else ()
    return Cell.defaults$field.asInstanceOf[Cell[?]]
  }
}