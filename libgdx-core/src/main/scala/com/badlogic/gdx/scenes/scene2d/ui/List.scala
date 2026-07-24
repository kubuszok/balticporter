package com.badlogic.gdx.scenes.scene2d.ui

class List[T] extends com.badlogic.gdx.scenes.scene2d.ui.Widget with com.badlogic.gdx.scenes.scene2d.utils.Cullable with com.badlogic.gdx.scenes.scene2d.ui.Styleable[com.badlogic.gdx.scenes.scene2d.ui.List.ListStyle] {
  var style: com.badlogic.gdx.scenes.scene2d.ui.List.ListStyle = null.asInstanceOf[com.badlogic.gdx.scenes.scene2d.ui.List.ListStyle]
  final val items: com.badlogic.gdx.utils.Array[T] = new com.badlogic.gdx.utils.Array()
  var selection: com.badlogic.gdx.scenes.scene2d.utils.ArraySelection[T] = new com.badlogic.gdx.scenes.scene2d.utils.ArraySelection(this.items)
  private var cullingArea: com.badlogic.gdx.math.Rectangle = null.asInstanceOf[com.badlogic.gdx.math.Rectangle]
  private var prefWidth: scala.Float = 0.0f
  private var prefHeight: scala.Float = 0.0f
  var itemHeight: scala.Float = 0.0f
  private var alignment: scala.Int = com.badlogic.gdx.utils.Align.left
  var pressedIndex: scala.Int = -1
  var overIndex: scala.Int = -1
  private var keyListener: com.badlogic.gdx.scenes.scene2d.InputListener = null.asInstanceOf[com.badlogic.gdx.scenes.scene2d.InputListener]
  var typeToSelect: scala.Boolean = false
  def this(style: com.badlogic.gdx.scenes.scene2d.ui.List.ListStyle) = {
    this()
    this.selection.setActor(this)
    this.selection.setRequired(true)
    this.setStyle(style)
    this.setSize(this.getPrefWidth(), this.getPrefHeight())
    this.addListener({
      this.keyListener = new com.badlogic.gdx.scenes.scene2d.InputListener()
      this.keyListener
    })
    this.addListener(new com.badlogic.gdx.scenes.scene2d.InputListener())
  }
  def this(skin: com.badlogic.gdx.scenes.scene2d.ui.Skin, styleName: java.lang.String) = {
    this(skin.get(styleName, classOf[com.badlogic.gdx.scenes.scene2d.ui.List.ListStyle]))
  }
  def this(skin: com.badlogic.gdx.scenes.scene2d.ui.Skin) = {
    this(skin.get(classOf[com.badlogic.gdx.scenes.scene2d.ui.List.ListStyle]))
  }
  def setStyle(style: com.badlogic.gdx.scenes.scene2d.ui.List.ListStyle): scala.Unit = {
    if (style == null) {
      throw new java.lang.IllegalArgumentException("style cannot be null.")
    } else ()
    this.style = style
    this.invalidateHierarchy()
  }
  def getStyle(): com.badlogic.gdx.scenes.scene2d.ui.List.ListStyle = {
    return this.style
  }
  def layout(): scala.Unit = {
    val font: com.badlogic.gdx.graphics.g2d.BitmapFont = this.style.font
    val selectedDrawable: com.badlogic.gdx.scenes.scene2d.utils.Drawable = this.style.selection
    this.itemHeight = font.getCapHeight() - (font.getDescent() * 2)
    this.itemHeight = this.itemHeight + (selectedDrawable.getTopHeight() + selectedDrawable.getBottomHeight())
    this.prefWidth = 0
    val layoutPool: com.badlogic.gdx.utils.Pool[com.badlogic.gdx.graphics.g2d.GlyphLayout] = com.badlogic.gdx.scenes.scene2d.Actor.POOLS.getPool(classOf[com.badlogic.gdx.graphics.g2d.GlyphLayout])
    val layout: com.badlogic.gdx.graphics.g2d.GlyphLayout = layoutPool.obtain();
    { var i: scala.Int = 0; while (i < this.items.size) { {
      layout.setText(font, this.toString(this.items.get(i)))
      this.prefWidth = java.lang.Math.max(layout.width, this.prefWidth)
    }; i = i + 1 } }
    layoutPool.free(layout)
    this.prefWidth = this.prefWidth + (selectedDrawable.getLeftWidth() + selectedDrawable.getRightWidth())
    this.prefHeight = this.items.size * this.itemHeight
    val background: com.badlogic.gdx.scenes.scene2d.utils.Drawable = this.style.background
    if (background != null) {
      this.prefWidth = java.lang.Math.max((this.prefWidth + background.getLeftWidth()) + background.getRightWidth(), background.getMinWidth())
      this.prefHeight = java.lang.Math.max((this.prefHeight + background.getTopHeight()) + background.getBottomHeight(), background.getMinHeight())
    } else ()
  }
  def draw(batch: com.badlogic.gdx.graphics.g2d.Batch, parentAlpha: scala.Float): scala.Unit = {
    this.validate()
    this.drawBackground(batch, parentAlpha)
    val font: com.badlogic.gdx.graphics.g2d.BitmapFont = this.style.font
    val selectedDrawable: com.badlogic.gdx.scenes.scene2d.utils.Drawable = this.style.selection
    val fontColorSelected: com.badlogic.gdx.graphics.Color = this.style.fontColorSelected
    val fontColorUnselected: com.badlogic.gdx.graphics.Color = this.style.fontColorUnselected
    val color: com.badlogic.gdx.graphics.Color = this.getColor()
    batch.setColor(color.r, color.g, color.b, color.a * parentAlpha)
    var x: scala.Float = this.getX()
    val y: scala.Float = this.getY()
    var width: scala.Float = this.getWidth()
    val height: scala.Float = this.getHeight()
    var itemY: scala.Float = height
    val background: com.badlogic.gdx.scenes.scene2d.utils.Drawable = this.style.background
    if (background != null) {
      val leftWidth: scala.Float = background.getLeftWidth()
      x = x + leftWidth
      itemY = itemY - background.getTopHeight()
      width = width - (leftWidth + background.getRightWidth())
    } else ()
    val textOffsetX: scala.Float = selectedDrawable.getLeftWidth()
    val textWidth: scala.Float = (width - textOffsetX) - selectedDrawable.getRightWidth()
    val textOffsetY: scala.Float = selectedDrawable.getTopHeight() - font.getDescent()
    font.setColor(fontColorUnselected.r, fontColorUnselected.g, fontColorUnselected.b, fontColorUnselected.a * parentAlpha);
    { var i: scala.Int = 0; while (i < this.items.size) { {
      if ((this.cullingArea == null) || (((itemY - this.itemHeight) <= (this.cullingArea.y + this.cullingArea.height)) && (itemY >= this.cullingArea.y))) {
        val item: T = this.items.get(i).asInstanceOf[T]
        val selected: scala.Boolean = this.selection.contains(item)
        var drawable: com.badlogic.gdx.scenes.scene2d.utils.Drawable = null
        if ((this.pressedIndex == i) && (this.style.down != null)) {
          drawable = this.style.down
        } else {
          if (selected) {
            drawable = selectedDrawable
            font.setColor(fontColorSelected.r, fontColorSelected.g, fontColorSelected.b, fontColorSelected.a * parentAlpha)
          } else {
            if ((this.overIndex == i) && (this.style.over != null)) {
              drawable = this.style.over
            } else ()
          }
        }
        this.drawSelection(batch, drawable, x, (y + itemY) - this.itemHeight, width, this.itemHeight)
        this.drawItem(batch, font, i, item, x + textOffsetX, (y + itemY) - textOffsetY, textWidth)
        if (selected) {
          font.setColor(fontColorUnselected.r, fontColorUnselected.g, fontColorUnselected.b, fontColorUnselected.a * parentAlpha)
        } else ()
      } else {
        if (itemY < this.cullingArea.y) {
          /* break */ ()
        } else ()
      }
      itemY = itemY - this.itemHeight
    }; i = i + 1 } }
  }
  def drawSelection(batch: com.badlogic.gdx.graphics.g2d.Batch, drawable: com.badlogic.gdx.scenes.scene2d.utils.Drawable, x: scala.Float, y: scala.Float, width: scala.Float, height: scala.Float): scala.Unit = {
    if (drawable != null) {
      drawable.draw(batch, x, y, width, height)
    } else ()
  }
  def drawBackground(batch: com.badlogic.gdx.graphics.g2d.Batch, parentAlpha: scala.Float): scala.Unit = {
    if (this.style.background != null) {
      val color: com.badlogic.gdx.graphics.Color = this.getColor()
      batch.setColor(color.r, color.g, color.b, color.a * parentAlpha)
      this.style.background.draw(batch, this.getX(), this.getY(), this.getWidth(), this.getHeight())
    } else ()
  }
  def drawItem(batch: com.badlogic.gdx.graphics.g2d.Batch, font: com.badlogic.gdx.graphics.g2d.BitmapFont, index: scala.Int, item: T, x: scala.Float, y: scala.Float, width: scala.Float): com.badlogic.gdx.graphics.g2d.GlyphLayout = {
    val string: java.lang.String = this.toString(item)
    return font.draw(batch, string, x, y, 0, string.length(), width, this.alignment, false, "...")
  }
  def getSelection(): com.badlogic.gdx.scenes.scene2d.utils.ArraySelection[T] = {
    return this.selection
  }
  def setSelection(selection: com.badlogic.gdx.scenes.scene2d.utils.ArraySelection[T]): scala.Unit = {
    this.selection = selection
  }
  def getSelected(): T = {
    return this.selection.first().asInstanceOf[T]
  }
  def setSelected(item: T): scala.Unit = {
    if (this.items.contains(item, false)) {
      this.selection.set(item)
    } else {
      if (this.selection.getRequired() && (this.items.size > 0)) {
        this.selection.set(this.items.first())
      } else {
        this.selection.clear()
      }
    }
  }
  def getSelectedIndex(): scala.Int = {
    val selected: com.badlogic.gdx.utils.ObjectSet[T] = this.selection.items()
    return if (selected.size == 0) -1 else this.items.indexOf(selected.first(), false)
  }
  def setSelectedIndex(index: scala.Int): scala.Unit = {
    if ((index < (-1)) || (index >= this.items.size)) {
      throw new java.lang.IllegalArgumentException((("index must be >= -1 and < " + this.items.size) + ": ") + index)
    } else ()
    if (index == (-1)) {
      this.selection.clear()
    } else {
      this.selection.set(this.items.get(index))
    }
  }
  def getOverItem(): T = {
    return if (this.overIndex == (-1)) null else this.items.get(this.overIndex)
  }
  def getPressedItem(): T = {
    return if (this.pressedIndex == (-1)) null else this.items.get(this.pressedIndex)
  }
  def getItemAt(y: scala.Float): T = {
    val index: scala.Int = this.getItemIndexAt(y)
    if (index == (-1)) {
      return null.asInstanceOf[T]
    } else ()
    return this.items.get(index).asInstanceOf[T]
  }
  def getItemIndexAt(y$arg: scala.Float): scala.Int = {
    var y: scala.Float = y$arg
    var height: scala.Float = this.getHeight()
    val background: com.badlogic.gdx.scenes.scene2d.utils.Drawable = this.style.background
    if (background != null) {
      height = height - (background.getTopHeight() + background.getBottomHeight())
      y = y - background.getBottomHeight()
    } else ()
    val index: scala.Int = ((height - y) / this.itemHeight).asInstanceOf[scala.Int].asInstanceOf[scala.Int]
    if ((index < 0) || (index >= this.items.size)) {
      return -1
    } else ()
    return index
  }
  def setItems(newItems: scala.Array[T]): scala.Unit = {
    if (newItems == null) {
      throw new java.lang.IllegalArgumentException("newItems cannot be null.")
    } else ()
    val oldPrefWidth: scala.Float = this.getPrefWidth()
    val oldPrefHeight: scala.Float = this.getPrefHeight()
    this.items.clear()
    this.items.addAll(newItems.asInstanceOf[scala.Array[java.lang.Object]])
    this.overIndex = -1
    this.pressedIndex = -1
    this.selection.validate()
    this.invalidate()
    if ((oldPrefWidth != this.getPrefWidth()) || (oldPrefHeight != this.getPrefHeight())) {
      this.invalidateHierarchy()
    } else ()
  }
  def setItems(newItems: com.badlogic.gdx.utils.Array[?]): scala.Unit = {
    if (newItems == null) {
      throw new java.lang.IllegalArgumentException("newItems cannot be null.")
    } else ()
    val oldPrefWidth: scala.Float = this.getPrefWidth()
    val oldPrefHeight: scala.Float = this.getPrefHeight()
    if (newItems != this.items) {
      this.items.clear()
      this.items.addAll(newItems)
    } else ()
    this.overIndex = -1
    this.pressedIndex = -1
    this.selection.validate()
    this.invalidate()
    if ((oldPrefWidth != this.getPrefWidth()) || (oldPrefHeight != this.getPrefHeight())) {
      this.invalidateHierarchy()
    } else ()
  }
  def clearItems(): scala.Unit = {
    if (this.items.size == 0) {
      return
    } else ()
    this.items.clear()
    this.overIndex = -1
    this.pressedIndex = -1
    this.selection.clear()
    this.invalidateHierarchy()
  }
  def getItems(): com.badlogic.gdx.utils.Array[T] = {
    return this.items
  }
  def getItemHeight(): scala.Float = {
    return this.itemHeight
  }
  def getPrefWidth(): scala.Float = {
    this.validate()
    return this.prefWidth
  }
  def getPrefHeight(): scala.Float = {
    this.validate()
    return this.prefHeight
  }
  def toString(`object`: T): java.lang.String = {
    return `object`.toString()
  }
  def setCullingArea(cullingArea: com.badlogic.gdx.math.Rectangle): scala.Unit = {
    this.cullingArea = cullingArea
  }
  def getCullingArea(): com.badlogic.gdx.math.Rectangle = {
    return this.cullingArea
  }
  def setAlignment(alignment: scala.Int): scala.Unit = {
    this.alignment = alignment
  }
  def getAlignment(): scala.Int = {
    return this.alignment
  }
  def setTypeToSelect(typeToSelect: scala.Boolean): scala.Unit = {
    this.typeToSelect = typeToSelect
  }
  def getKeyListener(): com.badlogic.gdx.scenes.scene2d.InputListener = {
    return this.keyListener
  }
}
object List {
  export com.badlogic.gdx.scenes.scene2d.ui.Widget.{ListStyle => _, *}
  class ListStyle {
    var font: com.badlogic.gdx.graphics.g2d.BitmapFont = null.asInstanceOf[com.badlogic.gdx.graphics.g2d.BitmapFont]
    var fontColorSelected: com.badlogic.gdx.graphics.Color = new com.badlogic.gdx.graphics.Color(1, 1, 1, 1)
    var fontColorUnselected: com.badlogic.gdx.graphics.Color = new com.badlogic.gdx.graphics.Color(1, 1, 1, 1)
    var selection: com.badlogic.gdx.scenes.scene2d.utils.Drawable = null.asInstanceOf[com.badlogic.gdx.scenes.scene2d.utils.Drawable]
    var down: com.badlogic.gdx.scenes.scene2d.utils.Drawable = null.asInstanceOf[com.badlogic.gdx.scenes.scene2d.utils.Drawable]
    var over: com.badlogic.gdx.scenes.scene2d.utils.Drawable = null.asInstanceOf[com.badlogic.gdx.scenes.scene2d.utils.Drawable]
    var background: com.badlogic.gdx.scenes.scene2d.utils.Drawable = null.asInstanceOf[com.badlogic.gdx.scenes.scene2d.utils.Drawable]
    def this(font: com.badlogic.gdx.graphics.g2d.BitmapFont, fontColorSelected: com.badlogic.gdx.graphics.Color, fontColorUnselected: com.badlogic.gdx.graphics.Color, selection: com.badlogic.gdx.scenes.scene2d.utils.Drawable) = {
      this()
      this.font = font
      this.fontColorSelected.set(fontColorSelected)
      this.fontColorUnselected.set(fontColorUnselected)
      this.selection = selection
    }
    def this(style: com.badlogic.gdx.scenes.scene2d.ui.List.ListStyle) = {
      this()
      this.font = style.font
      this.fontColorSelected.set(style.fontColorSelected)
      this.fontColorUnselected.set(style.fontColorUnselected)
      this.selection = style.selection
      this.down = style.down
      this.over = style.over
      this.background = style.background
    }
  }
}