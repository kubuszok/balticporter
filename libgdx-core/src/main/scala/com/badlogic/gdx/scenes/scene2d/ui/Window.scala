package com.badlogic.gdx.scenes.scene2d.ui

class Window extends com.badlogic.gdx.scenes.scene2d.ui.Table with com.badlogic.gdx.scenes.scene2d.ui.Styleable[com.badlogic.gdx.scenes.scene2d.ui.Window.WindowStyle] {
  private var style: com.badlogic.gdx.scenes.scene2d.ui.Window.WindowStyle = null.asInstanceOf[com.badlogic.gdx.scenes.scene2d.ui.Window.WindowStyle]
  var isMovable$field: scala.Boolean = true
  var isModal$field: scala.Boolean = false
  var isResizable$field: scala.Boolean = false
  var resizeBorder: scala.Int = 8
  var keepWithinStage$field: scala.Boolean = true
  var titleLabel: com.badlogic.gdx.scenes.scene2d.ui.Label = null.asInstanceOf[com.badlogic.gdx.scenes.scene2d.ui.Label]
  var titleTable: com.badlogic.gdx.scenes.scene2d.ui.Table = null.asInstanceOf[com.badlogic.gdx.scenes.scene2d.ui.Table]
  var drawTitleTable: scala.Boolean = false
  var edge: scala.Int = 0
  var dragging: scala.Boolean = false
  def this(title: java.lang.String, style: com.badlogic.gdx.scenes.scene2d.ui.Window.WindowStyle) = {
    this()
    if (title == null) {
      throw new java.lang.IllegalArgumentException("title cannot be null.")
    } else ()
    this.setTouchable(com.badlogic.gdx.scenes.scene2d.Touchable.enabled)
    this.setClip(true)
    this.titleLabel = this.newLabel(title, new com.badlogic.gdx.scenes.scene2d.ui.Label.LabelStyle(style.titleFont, style.titleFontColor))
    this.titleLabel.setEllipsis(true)
    this.titleTable = new com.badlogic.gdx.scenes.scene2d.ui.Table() {
      override def draw(batch: com.badlogic.gdx.graphics.g2d.Batch, parentAlpha: scala.Float): scala.Unit = {
        if (Window.this.drawTitleTable) {
          super.draw(batch, parentAlpha)
        } else ()
      }
    }
    this.titleTable.add(this.titleLabel).growX().minWidth(0)
    this.addActor(this.titleTable)
    this.setStyle(style)
    this.setWidth(150)
    this.setHeight(150)
    this.addCaptureListener(new com.badlogic.gdx.scenes.scene2d.InputListener() {
      override def touchDown(event: com.badlogic.gdx.scenes.scene2d.InputEvent, x: scala.Float, y: scala.Float, pointer: scala.Int, button: scala.Int): scala.Boolean = {
        Window.this.toFront()
        return false
      }
    })
    this.addListener(new com.badlogic.gdx.scenes.scene2d.InputListener() {
      var startX: scala.Float = 0.0f
      var startY: scala.Float = 0.0f
      var lastX: scala.Float = 0.0f
      var lastY: scala.Float = 0.0f
      private def updateEdge(x: scala.Float, y: scala.Float): scala.Unit = {
        var border: scala.Float = Window.this.resizeBorder / 2.0f
        val width: scala.Float = Window.this.getWidth()
        val height: scala.Float = Window.this.getHeight()
        val padTop: scala.Float = Window.this.getPadTop()
        val padLeft: scala.Float = Window.this.getPadLeft()
        val padBottom: scala.Float = Window.this.getPadBottom()
        val padRight: scala.Float = Window.this.getPadRight()
        val left: scala.Float = padLeft
        val right: scala.Float = width - padRight
        val bottom: scala.Float = padBottom
        Window.this.edge = 0
        if (((Window.this.isResizable$field && (x >= (left - border))) && (x <= (right + border))) && (y >= (bottom - border))) {
          if (x < (left + border)) {
            Window.this.edge = Window.this.edge | com.badlogic.gdx.utils.Align.left
          } else ()
          if (x > (right - border)) {
            Window.this.edge = Window.this.edge | com.badlogic.gdx.utils.Align.right
          } else ()
          if (y < (bottom + border)) {
            Window.this.edge = Window.this.edge | com.badlogic.gdx.utils.Align.bottom
          } else ()
          if (Window.this.edge != 0) {
            border = border + 25
          } else ()
          if (x < (left + border)) {
            Window.this.edge = Window.this.edge | com.badlogic.gdx.utils.Align.left
          } else ()
          if (x > (right - border)) {
            Window.this.edge = Window.this.edge | com.badlogic.gdx.utils.Align.right
          } else ()
          if (y < (bottom + border)) {
            Window.this.edge = Window.this.edge | com.badlogic.gdx.utils.Align.bottom
          } else ()
        } else ()
        if (((((Window.this.isMovable$field && (Window.this.edge == 0)) && (y <= height)) && (y >= (height - padTop))) && (x >= left)) && (x <= right)) {
          Window.this.edge = Window.MOVE
        } else ()
      }
      override def touchDown(event: com.badlogic.gdx.scenes.scene2d.InputEvent, x: scala.Float, y: scala.Float, pointer: scala.Int, button: scala.Int): scala.Boolean = {
        if (button == 0) {
          updateEdge(x, y)
          Window.this.dragging = Window.this.edge != 0
          startX = x
          startY = y
          lastX = x - Window.this.getWidth()
          lastY = y - Window.this.getHeight()
        } else ()
        return (Window.this.edge != 0) || Window.this.isModal$field
      }
      override def touchUp(event: com.badlogic.gdx.scenes.scene2d.InputEvent, x: scala.Float, y: scala.Float, pointer: scala.Int, button: scala.Int): scala.Unit = {
        Window.this.dragging = false
      }
      override def touchDragged(event: com.badlogic.gdx.scenes.scene2d.InputEvent, x: scala.Float, y: scala.Float, pointer: scala.Int): scala.Unit = {
        if (!Window.this.dragging) {
          return
        } else ()
        var width: scala.Float = Window.this.getWidth()
        var height: scala.Float = Window.this.getHeight()
        var windowX: scala.Float = Window.this.getX()
        var windowY: scala.Float = Window.this.getY()
        val minWidth: scala.Float = Window.this.getMinWidth()
        val maxWidth: scala.Float = Window.this.getMaxWidth()
        val minHeight: scala.Float = Window.this.getMinHeight()
        val maxHeight: scala.Float = Window.this.getMaxHeight()
        val stage: com.badlogic.gdx.scenes.scene2d.Stage = Window.this.getStage()
        val clampPosition: scala.Boolean = (Window.this.keepWithinStage$field && (stage != null)) && (Window.this.getParent() == stage.getRoot())
        if ((Window.this.edge & Window.MOVE) != 0) {
          var amountX: scala.Float = x - startX
          var amountY: scala.Float = y - startY
          windowX = windowX + amountX
          windowY = windowY + amountY
        } else ()
        if ((Window.this.edge & com.badlogic.gdx.utils.Align.left) != 0) {
          var amountX: scala.Float = x - startX
          if ((width - amountX) < minWidth) {
            amountX = -(minWidth - width)
          } else ()
          if (clampPosition && ((windowX + amountX) < 0)) {
            amountX = -windowX
          } else ()
          width = width - amountX
          windowX = windowX + amountX
        } else ()
        if ((Window.this.edge & com.badlogic.gdx.utils.Align.bottom) != 0) {
          var amountY: scala.Float = y - startY
          if ((height - amountY) < minHeight) {
            amountY = -(minHeight - height)
          } else ()
          if (clampPosition && ((windowY + amountY) < 0)) {
            amountY = -windowY
          } else ()
          height = height - amountY
          windowY = windowY + amountY
        } else ()
        if ((Window.this.edge & com.badlogic.gdx.utils.Align.right) != 0) {
          var amountX: scala.Float = (x - lastX) - width
          if ((width + amountX) < minWidth) {
            amountX = minWidth - width
          } else ()
          if (clampPosition && (((windowX + width) + amountX) > stage.getWidth())) {
            amountX = (stage.getWidth() - windowX) - width
          } else ()
          width = width + amountX
        } else ()
        if ((Window.this.edge & com.badlogic.gdx.utils.Align.top) != 0) {
          var amountY: scala.Float = (y - lastY) - height
          if ((height + amountY) < minHeight) {
            amountY = minHeight - height
          } else ()
          if (clampPosition && (((windowY + height) + amountY) > stage.getHeight())) {
            amountY = (stage.getHeight() - windowY) - height
          } else ()
          height = height + amountY
        } else ()
        Window.this.setBounds(java.lang.Math.round(windowX), java.lang.Math.round(windowY), java.lang.Math.round(width), java.lang.Math.round(height))
      }
      override def mouseMoved(event: com.badlogic.gdx.scenes.scene2d.InputEvent, x: scala.Float, y: scala.Float): scala.Boolean = {
        updateEdge(x, y)
        return Window.this.isModal$field
      }
      def scrolled(event: com.badlogic.gdx.scenes.scene2d.InputEvent, x: scala.Float, y: scala.Float, amount: scala.Int): scala.Boolean = {
        return Window.this.isModal$field
      }
      override def keyDown(event: com.badlogic.gdx.scenes.scene2d.InputEvent, keycode: scala.Int): scala.Boolean = {
        return Window.this.isModal$field
      }
      override def keyUp(event: com.badlogic.gdx.scenes.scene2d.InputEvent, keycode: scala.Int): scala.Boolean = {
        return Window.this.isModal$field
      }
      override def keyTyped(event: com.badlogic.gdx.scenes.scene2d.InputEvent, character: scala.Char): scala.Boolean = {
        return Window.this.isModal$field
      }
    })
  }
  def this(title: java.lang.String, skin: com.badlogic.gdx.scenes.scene2d.ui.Skin) = {
    this(title, skin.get(classOf[com.badlogic.gdx.scenes.scene2d.ui.Window.WindowStyle]))
    this.setSkin(skin)
  }
  def this(title: java.lang.String, skin: com.badlogic.gdx.scenes.scene2d.ui.Skin, styleName: java.lang.String) = {
    this(title, skin.get(styleName, classOf[com.badlogic.gdx.scenes.scene2d.ui.Window.WindowStyle]))
    this.setSkin(skin)
  }
  def newLabel(text: java.lang.String, style: com.badlogic.gdx.scenes.scene2d.ui.Label.LabelStyle): com.badlogic.gdx.scenes.scene2d.ui.Label = {
    return new com.badlogic.gdx.scenes.scene2d.ui.Label(text, style)
  }
  def setStyle(style: com.badlogic.gdx.scenes.scene2d.ui.Window.WindowStyle): scala.Unit = {
    if (style == null) {
      throw new java.lang.IllegalArgumentException("style cannot be null.")
    } else ()
    this.style = style
    this.setBackground(style.background)
    this.titleLabel.setStyle(new com.badlogic.gdx.scenes.scene2d.ui.Label.LabelStyle(style.titleFont, style.titleFontColor))
    this.invalidateHierarchy()
  }
  def getStyle(): com.badlogic.gdx.scenes.scene2d.ui.Window.WindowStyle = {
    return this.style
  }
  def keepWithinStage(): scala.Unit = {
    if (!this.keepWithinStage$field) {
      return
    } else ()
    val stage: com.badlogic.gdx.scenes.scene2d.Stage = this.getStage()
    if (stage == null) {
      return
    } else ()
    val camera: com.badlogic.gdx.graphics.Camera = stage.getCamera()
    if (camera.isInstanceOf[com.badlogic.gdx.graphics.OrthographicCamera]) {
      val orthographicCamera: com.badlogic.gdx.graphics.OrthographicCamera = camera.asInstanceOf[com.badlogic.gdx.graphics.OrthographicCamera]
      val parentWidth: scala.Float = stage.getWidth()
      val parentHeight: scala.Float = stage.getHeight()
      if ((this.getX(com.badlogic.gdx.utils.Align.right) - camera.position.x) > ((parentWidth / 2) / orthographicCamera.zoom)) {
        this.setPosition(camera.position.x + ((parentWidth / 2) / orthographicCamera.zoom), this.getY(com.badlogic.gdx.utils.Align.right), com.badlogic.gdx.utils.Align.right)
      } else ()
      if ((this.getX(com.badlogic.gdx.utils.Align.left) - camera.position.x) < (((-parentWidth) / 2) / orthographicCamera.zoom)) {
        this.setPosition(camera.position.x - ((parentWidth / 2) / orthographicCamera.zoom), this.getY(com.badlogic.gdx.utils.Align.left), com.badlogic.gdx.utils.Align.left)
      } else ()
      if ((this.getY(com.badlogic.gdx.utils.Align.top) - camera.position.y) > ((parentHeight / 2) / orthographicCamera.zoom)) {
        this.setPosition(this.getX(com.badlogic.gdx.utils.Align.top), camera.position.y + ((parentHeight / 2) / orthographicCamera.zoom), com.badlogic.gdx.utils.Align.top)
      } else ()
      if ((this.getY(com.badlogic.gdx.utils.Align.bottom) - camera.position.y) < (((-parentHeight) / 2) / orthographicCamera.zoom)) {
        this.setPosition(this.getX(com.badlogic.gdx.utils.Align.bottom), camera.position.y - ((parentHeight / 2) / orthographicCamera.zoom), com.badlogic.gdx.utils.Align.bottom)
      } else ()
    } else {
      if (this.getParent() == stage.getRoot()) {
        val parentWidth: scala.Float = stage.getWidth()
        val parentHeight: scala.Float = stage.getHeight()
        if (this.getX() < 0) {
          this.setX(0)
        } else ()
        if (this.getRight() > parentWidth) {
          this.setX(parentWidth - this.getWidth())
        } else ()
        if (this.getY() < 0) {
          this.setY(0)
        } else ()
        if (this.getTop() > parentHeight) {
          this.setY(parentHeight - this.getHeight())
        } else ()
      } else ()
    }
  }
  def draw(batch: com.badlogic.gdx.graphics.g2d.Batch, parentAlpha: scala.Float): scala.Unit = {
    val stage: com.badlogic.gdx.scenes.scene2d.Stage = this.getStage()
    if (stage != null) {
      if (stage.getKeyboardFocus() == null) {
        stage.setKeyboardFocus(this)
      } else ()
      this.keepWithinStage()
      if (this.style.stageBackground != null) {
        this.stageToLocalCoordinates(Window.tmpPosition.set(0, 0))
        this.stageToLocalCoordinates(Window.tmpSize.set(stage.getWidth(), stage.getHeight()))
        this.drawStageBackground(batch, parentAlpha, this.getX() + Window.tmpPosition.x, this.getY() + Window.tmpPosition.y, this.getX() + Window.tmpSize.x, this.getY() + Window.tmpSize.y)
      } else ()
    } else ()
    super.draw(batch, parentAlpha)
  }
  def drawStageBackground(batch: com.badlogic.gdx.graphics.g2d.Batch, parentAlpha: scala.Float, x: scala.Float, y: scala.Float, width: scala.Float, height: scala.Float): scala.Unit = {
    val color: com.badlogic.gdx.graphics.Color = this.getColor()
    batch.setColor(color.r, color.g, color.b, color.a * parentAlpha)
    this.style.stageBackground.draw(batch, x, y, width, height)
  }
  def drawBackground(batch: com.badlogic.gdx.graphics.g2d.Batch, parentAlpha: scala.Float, x: scala.Float, y: scala.Float): scala.Unit = {
    super.drawBackground(batch, parentAlpha, x, y)
    this.titleTable.getColor().a = this.getColor().a
    val padTop: scala.Float = this.getPadTop()
    val padLeft: scala.Float = this.getPadLeft()
    this.titleTable.setSize((this.getWidth() - padLeft) - this.getPadRight(), padTop)
    this.titleTable.setPosition(padLeft, this.getHeight() - padTop)
    this.drawTitleTable = true
    this.titleTable.draw(batch, parentAlpha)
    this.drawTitleTable = false
  }
  @com.badlogic.gdx.utils.Null
  def hit(x: scala.Float, y: scala.Float, touchable: scala.Boolean): com.badlogic.gdx.scenes.scene2d.Actor = {
    if (!this.isVisible()) {
      return null
    } else ()
    val hit: com.badlogic.gdx.scenes.scene2d.Actor = super.hit(x, y, touchable)
    if (((hit == null) && this.isModal$field) && ((!touchable) || (this.getTouchable() == com.badlogic.gdx.scenes.scene2d.Touchable.enabled))) {
      return this
    } else ()
    val height: scala.Float = this.getHeight()
    if ((hit == null) || (hit == this)) {
      return hit
    } else ()
    if ((((y <= height) && (y >= (height - this.getPadTop()))) && (x >= 0)) && (x <= this.getWidth())) {
      var current: com.badlogic.gdx.scenes.scene2d.Actor = hit
      while (current.getParent() != this) {
        current = current.getParent()
      }
      if (this.getCell(current) != null) {
        return this
      } else ()
    } else ()
    return hit
  }
  def isMovable(): scala.Boolean = {
    return this.isMovable$field
  }
  def setMovable(isMovable: scala.Boolean): scala.Unit = {
    this.isMovable$field = isMovable
  }
  def isModal(): scala.Boolean = {
    return this.isModal$field
  }
  def setModal(isModal: scala.Boolean): scala.Unit = {
    this.isModal$field = isModal
  }
  def setKeepWithinStage(keepWithinStage: scala.Boolean): scala.Unit = {
    this.keepWithinStage$field = keepWithinStage
  }
  def isResizable(): scala.Boolean = {
    return this.isResizable$field
  }
  def setResizable(isResizable: scala.Boolean): scala.Unit = {
    this.isResizable$field = isResizable
  }
  def setResizeBorder(resizeBorder: scala.Int): scala.Unit = {
    this.resizeBorder = resizeBorder
  }
  def isDragging(): scala.Boolean = {
    return this.dragging
  }
  def getPrefWidth(): scala.Float = {
    return java.lang.Math.max(super.getPrefWidth(), (this.titleTable.getPrefWidth() + this.getPadLeft()) + this.getPadRight())
  }
  def getTitleTable(): com.badlogic.gdx.scenes.scene2d.ui.Table = {
    return this.titleTable
  }
  def getTitleLabel(): com.badlogic.gdx.scenes.scene2d.ui.Label = {
    return this.titleLabel
  }
}
object Window {
  export com.badlogic.gdx.scenes.scene2d.ui.Table.{MOVE => _, WindowStyle => _, tmpPosition => _, tmpSize => _, *}
  private final val tmpPosition: com.badlogic.gdx.math.Vector2 = new com.badlogic.gdx.math.Vector2()
  private final val tmpSize: com.badlogic.gdx.math.Vector2 = new com.badlogic.gdx.math.Vector2()
  private final val MOVE: scala.Int = 1 << 5
  class WindowStyle {
    var background: com.badlogic.gdx.scenes.scene2d.utils.Drawable = null.asInstanceOf[com.badlogic.gdx.scenes.scene2d.utils.Drawable]
    var titleFont: com.badlogic.gdx.graphics.g2d.BitmapFont = null.asInstanceOf[com.badlogic.gdx.graphics.g2d.BitmapFont]
    var titleFontColor: com.badlogic.gdx.graphics.Color = new com.badlogic.gdx.graphics.Color(1, 1, 1, 1)
    var stageBackground: com.badlogic.gdx.scenes.scene2d.utils.Drawable = null.asInstanceOf[com.badlogic.gdx.scenes.scene2d.utils.Drawable]
    def this(titleFont: com.badlogic.gdx.graphics.g2d.BitmapFont, titleFontColor: com.badlogic.gdx.graphics.Color, background: com.badlogic.gdx.scenes.scene2d.utils.Drawable) = {
      this()
      this.titleFont = titleFont
      this.titleFontColor.set(titleFontColor)
      this.background = background
    }
    def this(style: com.badlogic.gdx.scenes.scene2d.ui.Window.WindowStyle) = {
      this()
      this.titleFont = style.titleFont
      if (style.titleFontColor != null) {
        this.titleFontColor = new com.badlogic.gdx.graphics.Color(style.titleFontColor)
      } else ()
      this.background = style.background
      this.stageBackground = style.stageBackground
    }
  }
}