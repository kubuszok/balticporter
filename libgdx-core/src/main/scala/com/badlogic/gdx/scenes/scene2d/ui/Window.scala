package com.badlogic.gdx.scenes.scene2d.ui

class Window extends com.badlogic.gdx.scenes.scene2d.ui.Table with com.badlogic.gdx.scenes.scene2d.ui.Styleable[WindowStyle] {
  private var style: WindowStyle = null.asInstanceOf[WindowStyle]
  var isMovable$field: scala.Boolean = true
  var isModal$field: scala.Boolean = false
  var isResizable$field: scala.Boolean = false
  var resizeBorder: scala.Int = 8
  var keepWithinStage$field: scala.Boolean = true
  var titleLabel: com.badlogic.gdx.scenes.scene2d.ui.Label = null.asInstanceOf[com.badlogic.gdx.scenes.scene2d.ui.Label]
  var titleTable: com.badlogic.gdx.scenes.scene2d.ui.Table = null.asInstanceOf[com.badlogic.gdx.scenes.scene2d.ui.Table]
  var drawTitleTable: scala.Boolean = false
  protected var edge: scala.Int = 0
  protected var dragging: scala.Boolean = false
  def this(title: java.lang.String, style: WindowStyle) = {
    this()
    if (title == null) {
      throw new java.lang.IllegalArgumentException("title cannot be null.")
    } else ()
    this.setTouchable(com.badlogic.gdx.scenes.scene2d.Touchable.enabled)
    this.setClip(true)
    this.titleLabel = this.newLabel(title, new com.badlogic.gdx.scenes.scene2d.ui.Label#LabelStyle(style.titleFont, style.titleFontColor))
    this.titleLabel.setEllipsis(true)
    this.titleTable = new com.badlogic.gdx.scenes.scene2d.ui.Table()
    this.titleTable.add(this.titleLabel).growX().minWidth(0)
    this.addActor(this.titleTable)
    this.setStyle(style)
    this.setWidth(150)
    this.setHeight(150)
    this.addCaptureListener(new com.badlogic.gdx.scenes.scene2d.InputListener())
    this.addListener(new com.badlogic.gdx.scenes.scene2d.InputListener())
  }
  def this(title: java.lang.String, skin: com.badlogic.gdx.scenes.scene2d.ui.Skin, styleName: java.lang.String) = {
    this(title, skin.get(styleName, classOf[java.lang.Class]))
    this.setSkin(skin)
  }
  def this(title: java.lang.String, skin: com.badlogic.gdx.scenes.scene2d.ui.Skin) = {
    this(title, skin.get(classOf[java.lang.Class]))
    this.setSkin(skin)
  }
  protected def newLabel(text: java.lang.String, style: com.badlogic.gdx.scenes.scene2d.ui.Label#LabelStyle): com.badlogic.gdx.scenes.scene2d.ui.Label = {
    return new com.badlogic.gdx.scenes.scene2d.ui.Label(text, style)
  }
  def setStyle(style: WindowStyle): scala.Unit = {
    if (style == null) {
      throw new java.lang.IllegalArgumentException("style cannot be null.")
    } else ()
    this.style = style
    this.setBackground(style.background)
    this.titleLabel.setStyle(new com.badlogic.gdx.scenes.scene2d.ui.Label#LabelStyle(style.titleFont, style.titleFontColor))
    this.invalidateHierarchy()
  }
  def getStyle(): WindowStyle = {
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
  protected def drawStageBackground(batch: com.badlogic.gdx.graphics.g2d.Batch, parentAlpha: scala.Float, x: scala.Float, y: scala.Float, width: scala.Float, height: scala.Float): scala.Unit = {
    val color: com.badlogic.gdx.graphics.Color = this.getColor()
    batch.setColor(color.r, color.g, color.b, color.a * parentAlpha)
    this.style.stageBackground.draw(batch, x, y, width, height)
  }
  protected def drawBackground(batch: com.badlogic.gdx.graphics.g2d.Batch, parentAlpha: scala.Float, x: scala.Float, y: scala.Float): scala.Unit = {
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
    def this(style: WindowStyle) = {
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
object Window {
  private final val tmpPosition: com.badlogic.gdx.math.Vector2 = new com.badlogic.gdx.math.Vector2()
  private final val tmpSize: com.badlogic.gdx.math.Vector2 = new com.badlogic.gdx.math.Vector2()
  private final val MOVE: scala.Int = 1 << 5
}