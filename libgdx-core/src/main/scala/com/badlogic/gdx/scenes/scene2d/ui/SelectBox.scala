package com.badlogic.gdx.scenes.scene2d.ui

class SelectBox[T <: java.lang.Object](style$p: com.badlogic.gdx.scenes.scene2d.ui.SelectBox.SelectBoxStyle) extends com.badlogic.gdx.scenes.scene2d.ui.Widget with com.badlogic.gdx.scenes.scene2d.utils.Disableable with com.badlogic.gdx.scenes.scene2d.ui.Styleable[com.badlogic.gdx.scenes.scene2d.ui.SelectBox.SelectBoxStyle] {
  var style: com.badlogic.gdx.scenes.scene2d.ui.SelectBox.SelectBoxStyle = null.asInstanceOf[com.badlogic.gdx.scenes.scene2d.ui.SelectBox.SelectBoxStyle]
  final val items: com.badlogic.gdx.utils.Array[T] = new com.badlogic.gdx.utils.Array[T]().asInstanceOf[com.badlogic.gdx.utils.Array[T]]
  var scrollPane: com.badlogic.gdx.scenes.scene2d.ui.SelectBox.SelectBoxScrollPane[T] = null.asInstanceOf[com.badlogic.gdx.scenes.scene2d.ui.SelectBox.SelectBoxScrollPane[T]]
  private var prefWidth: scala.Float = 0.0f
  private var prefHeight: scala.Float = 0.0f
  private var clickListener: com.badlogic.gdx.scenes.scene2d.utils.ClickListener = null.asInstanceOf[com.badlogic.gdx.scenes.scene2d.utils.ClickListener]
  var disabled: scala.Boolean = false
  private var alignment: scala.Int = com.badlogic.gdx.utils.Align.left
  var selectedPrefWidth: scala.Boolean = false
  final val selection: com.badlogic.gdx.scenes.scene2d.utils.ArraySelection[T] = new com.badlogic.gdx.scenes.scene2d.utils.ArraySelection[T](this.items) {
    override def fireChangeEvent(): scala.Boolean = {
      if (SelectBox.this.selectedPrefWidth) {
        invalidateHierarchy()
      } else ()
      return super.fireChangeEvent()
    }
  }.asInstanceOf[com.badlogic.gdx.scenes.scene2d.utils.ArraySelection[T]]
  def this(skin: com.badlogic.gdx.scenes.scene2d.ui.Skin) = {
    this(skin.get(classOf[com.badlogic.gdx.scenes.scene2d.ui.SelectBox.SelectBoxStyle]))
  }
  def this(skin: com.badlogic.gdx.scenes.scene2d.ui.Skin, styleName: java.lang.String) = {
    this(skin.get(styleName, classOf[com.badlogic.gdx.scenes.scene2d.ui.SelectBox.SelectBoxStyle]))
  }
  this.setStyle(style$p)
  this.setSize(this.getPrefWidth(), this.getPrefHeight())
  this.selection.setActor(this)
  this.selection.setRequired(true)
  this.scrollPane = this.newScrollPane()
  this.addListener({
    this.clickListener = new com.badlogic.gdx.scenes.scene2d.utils.ClickListener() {
      override def touchDown(event: com.badlogic.gdx.scenes.scene2d.InputEvent, x: scala.Float, y: scala.Float, pointer: scala.Int, button: scala.Int): scala.Boolean = {
        if ((pointer == 0) && (button != 0)) {
          return false
        } else ()
        if (isDisabled()) {
          return false
        } else ()
        if (SelectBox.this.scrollPane.hasParent()) {
          hideScrollPane()
        } else {
          showScrollPane()
        }
        return true
      }
    }
    this.clickListener
  })
  def newScrollPane(): com.badlogic.gdx.scenes.scene2d.ui.SelectBox.SelectBoxScrollPane[T] = {
    return new com.badlogic.gdx.scenes.scene2d.ui.SelectBox.SelectBoxScrollPane[T](this.asInstanceOf[SelectBox[T]]).asInstanceOf[com.badlogic.gdx.scenes.scene2d.ui.SelectBox.SelectBoxScrollPane[T]]
  }
  def setMaxListCount(maxListCount: scala.Int): scala.Unit = {
    this.scrollPane.maxListCount = maxListCount
  }
  def getMaxListCount(): scala.Int = {
    return this.scrollPane.maxListCount
  }
  override def setStage(stage: com.badlogic.gdx.scenes.scene2d.Stage): scala.Unit = {
    if (stage == null) {
      this.scrollPane.hide()
    } else ()
    super.setStage(stage)
  }
  override def setStyle(style: com.badlogic.gdx.scenes.scene2d.ui.SelectBox.SelectBoxStyle): scala.Unit = {
    if (style == null) {
      throw new java.lang.IllegalArgumentException("style cannot be null.")
    } else ()
    this.style = style
    if (this.scrollPane != null) {
      this.scrollPane.setStyle(style.scrollStyle)
      this.scrollPane.list.setStyle(style.listStyle)
    } else ()
    this.invalidateHierarchy()
  }
  override def getStyle(): T = {
    return this.style
  }
  def setItems(newItems: scala.Array[T]): scala.Unit = {
    if (newItems == null) {
      throw new java.lang.IllegalArgumentException("newItems cannot be null.")
    } else ()
    val oldPrefWidth: scala.Float = this.getPrefWidth()
    this.items.clear()
    this.items.addAll(newItems)
    this.selection.validate()
    this.scrollPane.list.setItems(this.items)
    this.invalidate()
    if (oldPrefWidth != this.getPrefWidth()) {
      this.invalidateHierarchy()
    } else ()
  }
  def setItems(newItems: com.badlogic.gdx.utils.Array[T]): scala.Unit = {
    if (newItems == null) {
      throw new java.lang.IllegalArgumentException("newItems cannot be null.")
    } else ()
    val oldPrefWidth: scala.Float = this.getPrefWidth()
    if (newItems != this.items) {
      this.items.clear()
      this.items.addAll(newItems.asInstanceOf[com.badlogic.gdx.utils.Array[? <: T]])
    } else ()
    this.selection.validate()
    this.scrollPane.list.setItems(this.items)
    this.invalidate()
    if (oldPrefWidth != this.getPrefWidth()) {
      this.invalidateHierarchy()
    } else ()
  }
  def clearItems(): scala.Unit = {
    if (this.items.size == 0) {
      return
    } else ()
    this.items.clear()
    this.selection.clear()
    this.scrollPane.list.clearItems()
    this.invalidateHierarchy()
  }
  def getItems(): com.badlogic.gdx.utils.Array[T] = {
    return this.items
  }
  override def layout(): scala.Unit = {
    var bg: com.badlogic.gdx.scenes.scene2d.utils.Drawable = this.style.background
    val font: com.badlogic.gdx.graphics.g2d.BitmapFont = this.style.font
    if (bg != null) {
      this.prefHeight = java.lang.Math.max(((bg.getTopHeight() + bg.getBottomHeight()) + font.getCapHeight()) - (font.getDescent() * 2), bg.getMinHeight())
    } else {
      this.prefHeight = font.getCapHeight() - (font.getDescent() * 2)
    }
    val layoutPool: com.badlogic.gdx.utils.Pool[com.badlogic.gdx.graphics.g2d.GlyphLayout] = com.badlogic.gdx.scenes.scene2d.Actor.POOLS.getPool(classOf[com.badlogic.gdx.graphics.g2d.GlyphLayout])
    val layout: com.badlogic.gdx.graphics.g2d.GlyphLayout = layoutPool.obtain()
    if (this.selectedPrefWidth) {
      this.prefWidth = 0
      if (bg != null) {
        this.prefWidth = bg.getLeftWidth() + bg.getRightWidth()
      } else ()
      val selected: T = this.getSelected().asInstanceOf[T]
      if (selected != null) {
        layout.setText(font, this.toString(selected))
        this.prefWidth = this.prefWidth + layout.width
      } else ()
    } else {
      var maxItemWidth: scala.Float = 0;
      { var i: scala.Int = 0; while (i < this.items.size) { {
        layout.setText(font, this.toString(this.items.get(i)))
        maxItemWidth = java.lang.Math.max(layout.width, maxItemWidth)
      }; i = i + 1 } }
      this.prefWidth = maxItemWidth
      if (bg != null) {
        this.prefWidth = java.lang.Math.max((this.prefWidth + bg.getLeftWidth()) + bg.getRightWidth(), bg.getMinWidth())
      } else ()
      val listStyle: com.badlogic.gdx.scenes.scene2d.ui.List.ListStyle = this.style.listStyle
      val scrollStyle: com.badlogic.gdx.scenes.scene2d.ui.ScrollPane.ScrollPaneStyle = this.style.scrollStyle
      var scrollWidth: scala.Float = (maxItemWidth + listStyle.selection.getLeftWidth()) + listStyle.selection.getRightWidth()
      bg = scrollStyle.background
      if (bg != null) {
        scrollWidth = java.lang.Math.max((scrollWidth + bg.getLeftWidth()) + bg.getRightWidth(), bg.getMinWidth())
      } else ()
      if ((this.scrollPane == null) || (!this.scrollPane.disableY)) {
        scrollWidth = scrollWidth + java.lang.Math.max(if (this.style.scrollStyle.vScroll != null) this.style.scrollStyle.vScroll.getMinWidth() else 0, if (this.style.scrollStyle.vScrollKnob != null) this.style.scrollStyle.vScrollKnob.getMinWidth() else 0)
      } else ()
      this.prefWidth = java.lang.Math.max(this.prefWidth, scrollWidth)
    }
    layoutPool.free(layout)
  }
  @com.badlogic.gdx.utils.Null
  def getBackgroundDrawable(): com.badlogic.gdx.scenes.scene2d.utils.Drawable = {
    if (this.isDisabled() && (this.style.backgroundDisabled != null)) {
      return this.style.backgroundDisabled
    } else ()
    if (this.scrollPane.hasParent() && (this.style.backgroundOpen != null)) {
      return this.style.backgroundOpen
    } else ()
    if (this.isOver() && (this.style.backgroundOver != null)) {
      return this.style.backgroundOver
    } else ()
    return this.style.background
  }
  def getFontColor(): com.badlogic.gdx.graphics.Color = {
    if (this.isDisabled() && (this.style.disabledFontColor != null)) {
      return this.style.disabledFontColor
    } else ()
    if ((this.style.overFontColor != null) && (this.isOver() || this.scrollPane.hasParent())) {
      return this.style.overFontColor
    } else ()
    return this.style.fontColor
  }
  override def draw(batch: com.badlogic.gdx.graphics.g2d.Batch, parentAlpha: scala.Float): scala.Unit = {
    this.validate()
    val background: com.badlogic.gdx.scenes.scene2d.utils.Drawable = this.getBackgroundDrawable()
    val fontColor: com.badlogic.gdx.graphics.Color = this.getFontColor()
    val font: com.badlogic.gdx.graphics.g2d.BitmapFont = this.style.font
    val color: com.badlogic.gdx.graphics.Color = this.getColor()
    var x: scala.Float = this.getX()
    var y: scala.Float = this.getY()
    var width: scala.Float = this.getWidth()
    var height: scala.Float = this.getHeight()
    batch.setColor(color.r, color.g, color.b, color.a * parentAlpha)
    if (background != null) {
      background.draw(batch, x, y, width, height)
    } else ()
    val selected: T = this.selection.first().asInstanceOf[T]
    if (selected != null) {
      if (background != null) {
        width = width - (background.getLeftWidth() + background.getRightWidth())
        height = height - (background.getBottomHeight() + background.getTopHeight())
        x = x + background.getLeftWidth()
        y = y + (((height / 2) + background.getBottomHeight()) + (font.getData().capHeight / 2)).asInstanceOf[scala.Int]
      } else {
        y = y + ((height / 2) + (font.getData().capHeight / 2)).asInstanceOf[scala.Int]
      }
      font.setColor(fontColor.r, fontColor.g, fontColor.b, fontColor.a * parentAlpha)
      this.drawItem(batch, font, selected, x, y, width)
    } else ()
  }
  def drawItem(batch: com.badlogic.gdx.graphics.g2d.Batch, font: com.badlogic.gdx.graphics.g2d.BitmapFont, item: T, x: scala.Float, y: scala.Float, width: scala.Float): com.badlogic.gdx.graphics.g2d.GlyphLayout = {
    val string: java.lang.String = this.toString(item)
    return font.draw(batch, string, x, y, 0, string.length(), width, this.alignment, false, "...")
  }
  def setAlignment(alignment: scala.Int): scala.Unit = {
    this.alignment = alignment
  }
  def getSelection(): com.badlogic.gdx.scenes.scene2d.utils.ArraySelection[T] = {
    return this.selection
  }
  @com.badlogic.gdx.utils.Null
  def getSelected(): T = {
    return this.selection.first().asInstanceOf[T]
  }
  def setSelected(item: T): scala.Unit = {
    if (this.items.contains(item, false)) {
      this.selection.set(item)
    } else {
      if (this.items.size > 0) {
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
    this.selection.set(this.items.get(index))
  }
  def setSelectedPrefWidth(selectedPrefWidth: scala.Boolean): scala.Unit = {
    this.selectedPrefWidth = selectedPrefWidth
  }
  def getSelectedPrefWidth(): scala.Boolean = {
    return this.selectedPrefWidth
  }
  def getMaxSelectedPrefWidth(): scala.Float = {
    val layoutPool: com.badlogic.gdx.utils.Pool[com.badlogic.gdx.graphics.g2d.GlyphLayout] = com.badlogic.gdx.scenes.scene2d.Actor.POOLS.getPool(classOf[com.badlogic.gdx.graphics.g2d.GlyphLayout])
    val layout: com.badlogic.gdx.graphics.g2d.GlyphLayout = layoutPool.obtain()
    var width: scala.Float = 0;
    { var i: scala.Int = 0; while (i < this.items.size) { {
      layout.setText(this.style.font, this.toString(this.items.get(i)))
      width = java.lang.Math.max(layout.width, width)
    }; i = i + 1 } }
    layoutPool.free(layout)
    val bg: com.badlogic.gdx.scenes.scene2d.utils.Drawable = this.style.background
    if (bg != null) {
      width = java.lang.Math.max((width + bg.getLeftWidth()) + bg.getRightWidth(), bg.getMinWidth())
    } else ()
    return width
  }
  override def setDisabled(disabled: scala.Boolean): scala.Unit = {
    if (disabled && (!this.disabled)) {
      this.hideScrollPane()
    } else ()
    this.disabled = disabled
  }
  override def isDisabled(): scala.Boolean = {
    return this.disabled
  }
  override def getPrefWidth(): scala.Float = {
    this.validate()
    return this.prefWidth
  }
  override def getPrefHeight(): scala.Float = {
    this.validate()
    return this.prefHeight
  }
  def toString(item: T): java.lang.String = {
    return item.toString()
  }
  @java.lang.Deprecated
  def showList(): scala.Unit = {
    this.showScrollPane()
  }
  def showScrollPane(): scala.Unit = {
    if (this.items.size == 0) {
      return
    } else ()
    if (this.getStage() != null) {
      this.scrollPane.show(this.getStage())
    } else ()
  }
  @java.lang.Deprecated
  def hideList(): scala.Unit = {
    this.hideScrollPane()
  }
  def hideScrollPane(): scala.Unit = {
    this.scrollPane.hide()
  }
  def getList(): com.badlogic.gdx.scenes.scene2d.ui.List[T] = {
    return this.scrollPane.list
  }
  def setScrollingDisabled(y: scala.Boolean): scala.Unit = {
    this.scrollPane.setScrollingDisabled(true, y)
    this.invalidateHierarchy()
  }
  def getScrollPane(): com.badlogic.gdx.scenes.scene2d.ui.SelectBox.SelectBoxScrollPane[T] = {
    return this.scrollPane.asInstanceOf[com.badlogic.gdx.scenes.scene2d.ui.SelectBox.SelectBoxScrollPane[T]]
  }
  def isOver(): scala.Boolean = {
    return this.clickListener.isOver()
  }
  def getClickListener(): com.badlogic.gdx.scenes.scene2d.utils.ClickListener = {
    return this.clickListener
  }
  def onShow(scrollPane: com.badlogic.gdx.scenes.scene2d.Actor, below: scala.Boolean): scala.Unit = {
    scrollPane.getColor().a = 0
    scrollPane.addAction(com.badlogic.gdx.scenes.scene2d.actions.Actions.fadeIn(0.3f, com.badlogic.gdx.math.Interpolation.fade))
  }
  def onHide(scrollPane: com.badlogic.gdx.scenes.scene2d.Actor): scala.Unit = {
    scrollPane.getColor().a = 1
    scrollPane.addAction(com.badlogic.gdx.scenes.scene2d.actions.Actions.sequence(com.badlogic.gdx.scenes.scene2d.actions.Actions.fadeOut(0.15f, com.badlogic.gdx.math.Interpolation.fade), com.badlogic.gdx.scenes.scene2d.actions.Actions.removeActor()))
  }
}
object SelectBox {
  export com.badlogic.gdx.scenes.scene2d.ui.Widget.{SelectBoxScrollPane => _, SelectBoxStyle => _, temp => _, *}
  final val temp: com.badlogic.gdx.math.Vector2 = new com.badlogic.gdx.math.Vector2()
  class SelectBoxScrollPane[T <: java.lang.Object](selectBox$p: SelectBox[T]) extends com.badlogic.gdx.scenes.scene2d.ui.ScrollPane(null, selectBox$p.style.scrollStyle) {
    var selectBox: SelectBox[T] = null.asInstanceOf[SelectBox[T]]
    var maxListCount: scala.Int = 0
    private final val stagePosition: com.badlogic.gdx.math.Vector2 = new com.badlogic.gdx.math.Vector2()
    var list: com.badlogic.gdx.scenes.scene2d.ui.List[T] = null.asInstanceOf[com.badlogic.gdx.scenes.scene2d.ui.List[T]]
    private var hideListener: com.badlogic.gdx.scenes.scene2d.InputListener = null.asInstanceOf[com.badlogic.gdx.scenes.scene2d.InputListener]
    private var previousScrollFocus: com.badlogic.gdx.scenes.scene2d.Actor = null.asInstanceOf[com.badlogic.gdx.scenes.scene2d.Actor]
    this.selectBox = selectBox$p
    this.setOverscroll(false, false)
    this.setFadeScrollBars(false)
    this.setScrollingDisabled(true, false)
    this.list = this.newList()
    this.list.setTouchable(com.badlogic.gdx.scenes.scene2d.Touchable.disabled)
    this.list.setTypeToSelect(true)
    this.setActor(this.list)
    this.list.addListener(new com.badlogic.gdx.scenes.scene2d.utils.ClickListener() {
      override def clicked(event: com.badlogic.gdx.scenes.scene2d.InputEvent, x: scala.Float, y: scala.Float): scala.Unit = {
        val selected: T = SelectBoxScrollPane.this.list.getSelected().asInstanceOf[T]
        if (selected != null) {
          selectBox$p.selection.items().clear(51)
        } else ()
        selectBox$p.selection.choose(selected)
        hide()
      }
      override def mouseMoved(event: com.badlogic.gdx.scenes.scene2d.InputEvent, x: scala.Float, y: scala.Float): scala.Boolean = {
        val index: scala.Int = SelectBoxScrollPane.this.list.getItemIndexAt(y)
        if (index != (-1)) {
          SelectBoxScrollPane.this.list.setSelectedIndex(index)
        } else ()
        return true
      }
    })
    this.addListener(new com.badlogic.gdx.scenes.scene2d.InputListener() {
      override def exit(event: com.badlogic.gdx.scenes.scene2d.InputEvent, x: scala.Float, y: scala.Float, pointer: scala.Int, toActor: com.badlogic.gdx.scenes.scene2d.Actor): scala.Unit = {
        if ((toActor == null) || (!isAscendantOf(toActor))) {
          val selected: T = selectBox$p.getSelected().asInstanceOf[T]
          if (selected != null) {
            SelectBoxScrollPane.this.list.selection.set(selected)
          } else ()
        } else ()
      }
    })
    this.hideListener = new com.badlogic.gdx.scenes.scene2d.InputListener() {
      override def touchDown(event: com.badlogic.gdx.scenes.scene2d.InputEvent, x: scala.Float, y: scala.Float, pointer: scala.Int, button: scala.Int): scala.Boolean = {
        val target: com.badlogic.gdx.scenes.scene2d.Actor = event.getTarget()
        if (isAscendantOf(target)) {
          return false
        } else ()
        SelectBoxScrollPane.this.list.selection.set(selectBox$p.getSelected())
        hide()
        return false
      }
      override def keyDown(event: com.badlogic.gdx.scenes.scene2d.InputEvent, keycode: scala.Int): scala.Boolean = {
        keycode match {
          case com.badlogic.gdx.Input.Keys.NUMPAD_ENTER | com.badlogic.gdx.Input.Keys.ENTER => {
            selectBox$p.selection.choose(SelectBoxScrollPane.this.list.getSelected())
            hide()
            event.stop()
            return true
          }
          case com.badlogic.gdx.Input.Keys.ESCAPE => {
            hide()
            event.stop()
            return true
          }
        }
        return false
      }
    }
    def newList(): com.badlogic.gdx.scenes.scene2d.ui.List[T] = {
      return new com.badlogic.gdx.scenes.scene2d.ui.List[T](this.selectBox.style.listStyle) {
        override def toString(obj: T): java.lang.String = {
          return SelectBoxScrollPane.this.selectBox.toString(obj)
        }
      }
    }
    def show(stage: com.badlogic.gdx.scenes.scene2d.Stage): scala.Unit = {
      if (this.list.isTouchable()) {
        return
      } else ()
      stage.addActor(this)
      stage.addCaptureListener(this.hideListener)
      stage.addListener(this.list.getKeyListener())
      this.selectBox.localToStageCoordinates(this.stagePosition.set(0, 0))
      val itemHeight: scala.Float = this.list.getItemHeight()
      var height: scala.Float = itemHeight * (if (this.maxListCount <= 0) this.selectBox.items.size else java.lang.Math.min(this.maxListCount, this.selectBox.items.size))
      val scrollPaneBackground: com.badlogic.gdx.scenes.scene2d.utils.Drawable = this.getStyle().background
      if (scrollPaneBackground != null) {
        height = height + (scrollPaneBackground.getTopHeight() + scrollPaneBackground.getBottomHeight())
      } else ()
      val listBackground: com.badlogic.gdx.scenes.scene2d.utils.Drawable = this.list.getStyle().background
      if (listBackground != null) {
        height = height + (listBackground.getTopHeight() + listBackground.getBottomHeight())
      } else ()
      val heightBelow: scala.Float = this.stagePosition.y
      val heightAbove: scala.Float = (stage.getHeight() - heightBelow) - this.selectBox.getHeight()
      var below: scala.Boolean = true
      if (height > heightBelow) {
        if (heightAbove > heightBelow) {
          below = false
          height = java.lang.Math.min(height, heightAbove)
        } else {
          height = heightBelow
        }
      } else ()
      if (below) {
        this.setY(this.stagePosition.y - height)
      } else {
        this.setY(this.stagePosition.y + this.selectBox.getHeight())
      }
      this.setHeight(height)
      this.validate()
      val width: scala.Float = java.lang.Math.max(this.getPrefWidth(), this.selectBox.getWidth())
      this.setWidth(width)
      var x: scala.Float = this.stagePosition.x
      if ((x + width) > stage.getWidth()) {
        x = x - ((this.getWidth() - this.selectBox.getWidth()) - 1)
        if (x < 0) {
          x = 0
        } else ()
      } else ()
      this.setX(x)
      this.validate()
      this.scrollTo(0, (this.list.getHeight() - (this.selectBox.getSelectedIndex() * itemHeight)) - (itemHeight / 2), 0, 0, true, true)
      this.updateVisualScroll()
      this.previousScrollFocus = null
      val actor: com.badlogic.gdx.scenes.scene2d.Actor = stage.getScrollFocus()
      if ((actor != null) && (!actor.isDescendantOf(this))) {
        this.previousScrollFocus = actor
      } else ()
      stage.setScrollFocus(this)
      this.list.selection.set(this.selectBox.getSelected())
      this.list.setTouchable(com.badlogic.gdx.scenes.scene2d.Touchable.enabled)
      this.clearActions()
      this.selectBox.onShow(this, below)
    }
    def hide(): scala.Unit = {
      if ((!this.list.isTouchable()) || (!this.hasParent())) {
        return
      } else ()
      this.list.setTouchable(com.badlogic.gdx.scenes.scene2d.Touchable.disabled)
      val stage: com.badlogic.gdx.scenes.scene2d.Stage = this.getStage()
      if (stage != null) {
        stage.removeCaptureListener(this.hideListener)
        stage.removeListener(this.list.getKeyListener())
        if ((this.previousScrollFocus != null) && (this.previousScrollFocus.getStage() == null)) {
          this.previousScrollFocus = null
        } else ()
        val actor: com.badlogic.gdx.scenes.scene2d.Actor = stage.getScrollFocus()
        if ((actor == null) || this.isAscendantOf(actor)) {
          stage.setScrollFocus(this.previousScrollFocus)
        } else ()
      } else ()
      this.clearActions()
      this.selectBox.onHide(this)
    }
    override def draw(batch: com.badlogic.gdx.graphics.g2d.Batch, parentAlpha: scala.Float): scala.Unit = {
      this.selectBox.localToStageCoordinates(SelectBox.temp.set(0, 0))
      if (!SelectBox.temp.equals(this.stagePosition)) {
        this.hide()
      } else ()
      super.draw(batch, parentAlpha)
    }
    override def act(delta: scala.Float): scala.Unit = {
      super.act(delta)
      this.toFront()
    }
    override def setStage(stage: com.badlogic.gdx.scenes.scene2d.Stage): scala.Unit = {
      val oldStage: com.badlogic.gdx.scenes.scene2d.Stage = this.getStage()
      if (oldStage != null) {
        oldStage.removeCaptureListener(this.hideListener)
        oldStage.removeListener(this.list.getKeyListener())
      } else ()
      super.setStage(stage)
    }
    def getList(): com.badlogic.gdx.scenes.scene2d.ui.List[T] = {
      return this.list
    }
    def getSelectBox(): SelectBox[T] = {
      return this.selectBox
    }
  }
  object SelectBoxScrollPane {
    export com.badlogic.gdx.scenes.scene2d.ui.ScrollPane.*
  }
  class SelectBoxStyle {
    var font: com.badlogic.gdx.graphics.g2d.BitmapFont = null.asInstanceOf[com.badlogic.gdx.graphics.g2d.BitmapFont]
    var fontColor: com.badlogic.gdx.graphics.Color = new com.badlogic.gdx.graphics.Color(1, 1, 1, 1)
    var overFontColor: com.badlogic.gdx.graphics.Color = null.asInstanceOf[com.badlogic.gdx.graphics.Color]
    var disabledFontColor: com.badlogic.gdx.graphics.Color = null.asInstanceOf[com.badlogic.gdx.graphics.Color]
    var background: com.badlogic.gdx.scenes.scene2d.utils.Drawable = null.asInstanceOf[com.badlogic.gdx.scenes.scene2d.utils.Drawable]
    var scrollStyle: com.badlogic.gdx.scenes.scene2d.ui.ScrollPane.ScrollPaneStyle = null.asInstanceOf[com.badlogic.gdx.scenes.scene2d.ui.ScrollPane.ScrollPaneStyle]
    var listStyle: com.badlogic.gdx.scenes.scene2d.ui.List.ListStyle = null.asInstanceOf[com.badlogic.gdx.scenes.scene2d.ui.List.ListStyle]
    var backgroundOver: com.badlogic.gdx.scenes.scene2d.utils.Drawable = null.asInstanceOf[com.badlogic.gdx.scenes.scene2d.utils.Drawable]
    var backgroundOpen: com.badlogic.gdx.scenes.scene2d.utils.Drawable = null.asInstanceOf[com.badlogic.gdx.scenes.scene2d.utils.Drawable]
    var backgroundDisabled: com.badlogic.gdx.scenes.scene2d.utils.Drawable = null.asInstanceOf[com.badlogic.gdx.scenes.scene2d.utils.Drawable]
    def this(font: com.badlogic.gdx.graphics.g2d.BitmapFont, fontColor: com.badlogic.gdx.graphics.Color, background: com.badlogic.gdx.scenes.scene2d.utils.Drawable, scrollStyle: com.badlogic.gdx.scenes.scene2d.ui.ScrollPane.ScrollPaneStyle, listStyle: com.badlogic.gdx.scenes.scene2d.ui.List.ListStyle) = {
      this()
      this.font = font
      this.fontColor.set(fontColor)
      this.background = background
      this.scrollStyle = scrollStyle
      this.listStyle = listStyle
    }
    def this(style: com.badlogic.gdx.scenes.scene2d.ui.SelectBox.SelectBoxStyle) = {
      this()
      this.font = style.font
      this.fontColor.set(style.fontColor)
      if (style.overFontColor != null) {
        this.overFontColor = new com.badlogic.gdx.graphics.Color(style.overFontColor)
      } else ()
      if (style.disabledFontColor != null) {
        this.disabledFontColor = new com.badlogic.gdx.graphics.Color(style.disabledFontColor)
      } else ()
      this.background = style.background
      this.scrollStyle = new com.badlogic.gdx.scenes.scene2d.ui.ScrollPane.ScrollPaneStyle(style.scrollStyle)
      this.listStyle = new com.badlogic.gdx.scenes.scene2d.ui.List.ListStyle(style.listStyle)
      this.backgroundOver = style.backgroundOver
      this.backgroundOpen = style.backgroundOpen
      this.backgroundDisabled = style.backgroundDisabled
    }
  }
}