package com.badlogic.gdx.scenes.scene2d.ui

class List[T <: java.lang.Object](style$p: com.badlogic.gdx.scenes.scene2d.ui.List.ListStyle) extends com.badlogic.gdx.scenes.scene2d.ui.Widget with com.badlogic.gdx.scenes.scene2d.utils.Cullable with com.badlogic.gdx.scenes.scene2d.ui.Styleable[com.badlogic.gdx.scenes.scene2d.ui.List.ListStyle] {
  var style: com.badlogic.gdx.scenes.scene2d.ui.List.ListStyle = null.asInstanceOf[com.badlogic.gdx.scenes.scene2d.ui.List.ListStyle]
  final val items: com.badlogic.gdx.utils.Array[T] = new com.badlogic.gdx.utils.Array[T]().asInstanceOf[com.badlogic.gdx.utils.Array[T]]
  var selection: com.badlogic.gdx.scenes.scene2d.utils.ArraySelection[T] = new com.badlogic.gdx.scenes.scene2d.utils.ArraySelection[T](this.items).asInstanceOf[com.badlogic.gdx.scenes.scene2d.utils.ArraySelection[T]]
  private var cullingArea: com.badlogic.gdx.math.Rectangle = null.asInstanceOf[com.badlogic.gdx.math.Rectangle]
  private var prefWidth: scala.Float = 0.0f
  private var prefHeight: scala.Float = 0.0f
  var itemHeight: scala.Float = 0.0f
  private var alignment: scala.Int = com.badlogic.gdx.utils.Align.left
  var pressedIndex: scala.Int = -1
  var overIndex: scala.Int = -1
  private var keyListener: com.badlogic.gdx.scenes.scene2d.InputListener = null.asInstanceOf[com.badlogic.gdx.scenes.scene2d.InputListener]
  var typeToSelect: scala.Boolean = false
  def this(skin: com.badlogic.gdx.scenes.scene2d.ui.Skin) = {
    this(skin.get(classOf[com.badlogic.gdx.scenes.scene2d.ui.List.ListStyle]))
  }
  def this(skin: com.badlogic.gdx.scenes.scene2d.ui.Skin, styleName: java.lang.String) = {
    this(skin.get(styleName, classOf[com.badlogic.gdx.scenes.scene2d.ui.List.ListStyle]))
  }
  this.selection.setActor(this)
  this.selection.setRequired(true)
  this.setStyle(style$p)
  this.setSize(this.getPrefWidth(), this.getPrefHeight())
  this.addListener({
    this.keyListener = new com.badlogic.gdx.scenes.scene2d.InputListener() {
      var typeTimeout: scala.Long = 0L
      var prefix: java.lang.String = null.asInstanceOf[java.lang.String]
      override def keyDown(event: com.badlogic.gdx.scenes.scene2d.InputEvent, keycode: scala.Int): scala.Boolean = {
        if (List.this.items.isEmpty()) {
          return false
        } else ()
        var index: scala.Int = 0
        keycode match {
          case com.badlogic.gdx.Input.Keys.A => {
            if (com.badlogic.gdx.scenes.scene2d.utils.UIUtils.ctrl() && List.this.selection.getMultiple()) {
              List.this.selection.clear()
              List.this.selection.addAll(List.this.items)
              return true
            } else ()
          }
          case com.badlogic.gdx.Input.Keys.HOME => {
            setSelectedIndex(0)
            return true
          }
          case com.badlogic.gdx.Input.Keys.END => {
            setSelectedIndex(List.this.items.size - 1)
            return true
          }
          case com.badlogic.gdx.Input.Keys.DOWN => {
            index = List.this.items.indexOf(getSelected(), false) + 1
            if (index >= List.this.items.size) {
              index = 0
            } else ()
            setSelectedIndex(index)
            return true
          }
          case com.badlogic.gdx.Input.Keys.UP => {
            index = List.this.items.indexOf(getSelected(), false) - 1
            if (index < 0) {
              index = List.this.items.size - 1
            } else ()
            setSelectedIndex(index)
            return true
          }
          case com.badlogic.gdx.Input.Keys.ESCAPE => {
            if (getStage() != null) {
              getStage().setKeyboardFocus(null)
            } else ()
            return true
          }
        }
        return false
      }
      override def keyTyped(event: com.badlogic.gdx.scenes.scene2d.InputEvent, character: scala.Char): scala.Boolean = {
        if (!List.this.typeToSelect) {
          return false
        } else ()
        val time: scala.Long = java.lang.System.currentTimeMillis()
        if (time > typeTimeout) {
          prefix = ""
        } else ()
        typeTimeout = time + 300
        prefix = prefix + java.lang.Character.toLowerCase(character);
        { var i: scala.Int = 0; val n: scala.Int = List.this.items.size; while (i < n) { {
          if (List.this.toString(List.this.items.get(i)).toLowerCase().startsWith(prefix)) {
            setSelectedIndex(i)
            /* break */ ()
          } else ()
        }; i = i + 1 } }
        return false
      }
    }
    this.keyListener
  })
  this.addListener(new com.badlogic.gdx.scenes.scene2d.InputListener() {
    override def touchDown(event: com.badlogic.gdx.scenes.scene2d.InputEvent, x: scala.Float, y: scala.Float, pointer: scala.Int, button: scala.Int): scala.Boolean = {
      if ((pointer != 0) || (button != 0)) {
        return true
      } else ()
      if (List.this.selection.isDisabled()) {
        return true
      } else ()
      if (getStage() != null) {
        getStage().setKeyboardFocus(List.this)
      } else ()
      if (List.this.items.size == 0) {
        return true
      } else ()
      val index: scala.Int = getItemIndexAt(y)
      if (index == (-1)) {
        return true
      } else ()
      List.this.selection.choose(List.this.items.get(index))
      List.this.pressedIndex = index
      return true
    }
    override def touchUp(event: com.badlogic.gdx.scenes.scene2d.InputEvent, x: scala.Float, y: scala.Float, pointer: scala.Int, button: scala.Int): scala.Unit = {
      if ((pointer != 0) || (button != 0)) {
        return
      } else ()
      List.this.pressedIndex = -1
    }
    override def touchDragged(event: com.badlogic.gdx.scenes.scene2d.InputEvent, x: scala.Float, y: scala.Float, pointer: scala.Int): scala.Unit = {
      List.this.overIndex = getItemIndexAt(y)
    }
    override def mouseMoved(event: com.badlogic.gdx.scenes.scene2d.InputEvent, x: scala.Float, y: scala.Float): scala.Boolean = {
      List.this.overIndex = getItemIndexAt(y)
      return false
    }
    override def exit(event: com.badlogic.gdx.scenes.scene2d.InputEvent, x: scala.Float, y: scala.Float, pointer: scala.Int, toActor: com.badlogic.gdx.scenes.scene2d.Actor): scala.Unit = {
      if (pointer == 0) {
        List.this.pressedIndex = -1
      } else ()
      if (pointer == (-1)) {
        List.this.overIndex = -1
      } else ()
    }
  })
  override def setStyle(style: com.badlogic.gdx.scenes.scene2d.ui.List.ListStyle): scala.Unit = {
    if (style == null) {
      throw new java.lang.IllegalArgumentException("style cannot be null.")
    } else ()
    this.style = style
    this.invalidateHierarchy()
  }
  override def getStyle(): com.badlogic.gdx.scenes.scene2d.ui.List.ListStyle = {
    return this.style
  }
  override def layout(): scala.Unit = {
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
  override def draw(batch: com.badlogic.gdx.graphics.g2d.Batch, parentAlpha: scala.Float): scala.Unit = {
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
  @com.badlogic.gdx.utils.Null
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
    return if (this.overIndex == (-1)) null.asInstanceOf[T] else this.items.get(this.overIndex)
  }
  def getPressedItem(): T = {
    return if (this.pressedIndex == (-1)) null.asInstanceOf[T] else this.items.get(this.pressedIndex)
  }
  @com.badlogic.gdx.utils.Null
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
    this.items.addAll(newItems)
    this.overIndex = -1
    this.pressedIndex = -1
    this.selection.validate()
    this.invalidate()
    if ((oldPrefWidth != this.getPrefWidth()) || (oldPrefHeight != this.getPrefHeight())) {
      this.invalidateHierarchy()
    } else ()
  }
  def setItems(newItems: com.badlogic.gdx.utils.Array[T]): scala.Unit = {
    if (newItems == null) {
      throw new java.lang.IllegalArgumentException("newItems cannot be null.")
    } else ()
    val oldPrefWidth: scala.Float = this.getPrefWidth()
    val oldPrefHeight: scala.Float = this.getPrefHeight()
    if (newItems != this.items) {
      this.items.clear()
      this.items.addAll(newItems.asInstanceOf[com.badlogic.gdx.utils.Array[? <: T]])
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
  override def getPrefWidth(): scala.Float = {
    this.validate()
    return this.prefWidth
  }
  override def getPrefHeight(): scala.Float = {
    this.validate()
    return this.prefHeight
  }
  def toString(`object`: T): java.lang.String = {
    return `object`.toString()
  }
  override def setCullingArea(cullingArea: com.badlogic.gdx.math.Rectangle): scala.Unit = {
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