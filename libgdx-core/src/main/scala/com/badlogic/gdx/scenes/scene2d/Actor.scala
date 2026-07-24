package com.badlogic.gdx.scenes.scene2d

class Actor {
  private var stage: com.badlogic.gdx.scenes.scene2d.Stage = null.asInstanceOf[com.badlogic.gdx.scenes.scene2d.Stage]
  var parent: com.badlogic.gdx.scenes.scene2d.Group = null.asInstanceOf[com.badlogic.gdx.scenes.scene2d.Group]
  private final val listeners: com.badlogic.gdx.utils.DelayedRemovalArray[com.badlogic.gdx.scenes.scene2d.EventListener] = new com.badlogic.gdx.utils.DelayedRemovalArray(0)
  private final val captureListeners: com.badlogic.gdx.utils.DelayedRemovalArray[com.badlogic.gdx.scenes.scene2d.EventListener] = new com.badlogic.gdx.utils.DelayedRemovalArray(0)
  private final val actions: com.badlogic.gdx.utils.Array[com.badlogic.gdx.scenes.scene2d.Action] = new com.badlogic.gdx.utils.Array(0)
  private var name: java.lang.String = null.asInstanceOf[java.lang.String]
  private var touchable: com.badlogic.gdx.scenes.scene2d.Touchable = com.badlogic.gdx.scenes.scene2d.Touchable.enabled
  private var visible: scala.Boolean = true
  var debug$field: scala.Boolean = false
  var x: scala.Float = 0.0f
  var y: scala.Float = 0.0f
  var width: scala.Float = 0.0f
  var height: scala.Float = 0.0f
  var originX: scala.Float = 0.0f
  var originY: scala.Float = 0.0f
  var scaleX: scala.Float = 1
  var scaleY: scala.Float = 1
  var rotation: scala.Float = 0.0f
  final val color: com.badlogic.gdx.graphics.Color = new com.badlogic.gdx.graphics.Color(1, 1, 1, 1)
  private var userObject: java.lang.Object = null.asInstanceOf[java.lang.Object]
  def draw(batch: com.badlogic.gdx.graphics.g2d.Batch, parentAlpha: scala.Float): scala.Unit = {
    ()
  }
  def act(delta: scala.Float): scala.Unit = {
    val actions: com.badlogic.gdx.utils.Array[com.badlogic.gdx.scenes.scene2d.Action] = this.actions
    if (actions.size == 0) {
      return
    } else ()
    if ((this.stage != null) && this.stage.getActionsRequestRendering()) {
      com.badlogic.gdx.Gdx.graphics.requestRendering()
    } else ()
    try {
      { var i: scala.Int = 0; while (i < actions.size) { {
        val action: com.badlogic.gdx.scenes.scene2d.Action = actions.get(i)
        if (action.act(delta) && (i < actions.size)) {
          val current: com.badlogic.gdx.scenes.scene2d.Action = actions.get(i)
          val actionIndex: scala.Int = if (current == action) i else actions.indexOf(action, true)
          if (actionIndex != (-1)) {
            actions.removeIndex(actionIndex)
            action.setActor(null)
            i = i - 1
          } else ()
        } else ()
      }; i = i + 1 } }
    } catch {
      case ex: java.lang.RuntimeException => {
        val context: java.lang.String = this.toString()
        throw new java.lang.RuntimeException("Actor: " + context.substring(0, java.lang.Math.min(context.length(), 128)), ex)
      }
    }
  }
  def fire(event: com.badlogic.gdx.scenes.scene2d.Event): scala.Boolean = {
    if (event.getStage() == null) {
      event.setStage(this.getStage())
    } else ()
    event.setTarget(this)
    val ascendants: com.badlogic.gdx.utils.Array[com.badlogic.gdx.scenes.scene2d.Group] = Actor.POOLS.obtain(classOf[com.badlogic.gdx.utils.Array[?]])
    var parent: com.badlogic.gdx.scenes.scene2d.Group = this.parent
    while (parent != null) {
      ascendants.add(parent)
      parent = parent.parent
    }
    try {
      val ascendantsArray: scala.Array[java.lang.Object] = ascendants.items.asInstanceOf[scala.Array[java.lang.Object]];
      { var i: scala.Int = ascendants.size - 1; while (i >= 0) { {
        val currentTarget: com.badlogic.gdx.scenes.scene2d.Group = ascendantsArray(i).asInstanceOf[com.badlogic.gdx.scenes.scene2d.Group].asInstanceOf[com.badlogic.gdx.scenes.scene2d.Group]
        currentTarget.notify(event, true)
        if (event.isStopped()) {
          return event.isCancelled()
        } else ()
      }; i = i - 1 } }
      this.notify(event, true)
      if (event.isStopped()) {
        return event.isCancelled()
      } else ()
      this.notify(event, false)
      if (!event.getBubbles()) {
        return event.isCancelled()
      } else ()
      if (event.isStopped()) {
        return event.isCancelled()
      } else ();
      { var i: scala.Int = 0; val n: scala.Int = ascendants.size; while (i < n) { {
        ascendantsArray(i).asInstanceOf[com.badlogic.gdx.scenes.scene2d.Group].notify(event, false)
        if (event.isStopped()) {
          return event.isCancelled()
        } else ()
      }; i = i + 1 } }
      return event.isCancelled()
    } finally {
      ascendants.clear()
      Actor.POOLS.free(ascendants)
    }
  }
  def notify(event: com.badlogic.gdx.scenes.scene2d.Event, capture: scala.Boolean): scala.Boolean = {
    if (event.getTarget() == null) {
      throw new java.lang.IllegalArgumentException("The event target cannot be null.")
    } else ()
    val listeners: com.badlogic.gdx.utils.DelayedRemovalArray[com.badlogic.gdx.scenes.scene2d.EventListener] = if (capture) this.captureListeners else this.listeners
    if (listeners.size == 0) {
      return event.isCancelled()
    } else ()
    event.setListenerActor(this)
    event.setCapture(capture)
    if (event.getStage() == null) {
      event.setStage(this.stage)
    } else ()
    try {
      listeners.begin();
      { var i: scala.Int = 0; val n: scala.Int = listeners.size; while (i < n) { {
        if (listeners.get(i).handle(event)) {
          event.handle()
        } else ()
      }; i = i + 1 } }
      listeners.`end`()
    } catch {
      case ex: java.lang.RuntimeException => {
        val context: java.lang.String = this.toString()
        throw new java.lang.RuntimeException("Actor: " + context.substring(0, java.lang.Math.min(context.length(), 128)), ex)
      }
    }
    return event.isCancelled()
  }
  def hit(x: scala.Float, y: scala.Float, touchable: scala.Boolean): Actor = {
    if (touchable && (this.touchable != com.badlogic.gdx.scenes.scene2d.Touchable.enabled)) {
      return null
    } else ()
    if (!this.isVisible()) {
      return null
    } else ()
    return if ((((x >= 0) && (x < this.width)) && (y >= 0)) && (y < this.height)) this else null
  }
  def remove(): scala.Boolean = {
    if (this.parent != null) {
      return this.parent.removeActor(this, true)
    } else ()
    return false
  }
  def addListener(listener: com.badlogic.gdx.scenes.scene2d.EventListener): scala.Boolean = {
    if (listener == null) {
      throw new java.lang.IllegalArgumentException("listener cannot be null.")
    } else ()
    if (!this.listeners.contains(listener, true)) {
      this.listeners.add(listener)
      return true
    } else ()
    return false
  }
  def removeListener(listener: com.badlogic.gdx.scenes.scene2d.EventListener): scala.Boolean = {
    if (listener == null) {
      throw new java.lang.IllegalArgumentException("listener cannot be null.")
    } else ()
    return this.listeners.removeValue(listener, true)
  }
  def getListeners(): com.badlogic.gdx.utils.DelayedRemovalArray[com.badlogic.gdx.scenes.scene2d.EventListener] = {
    return this.listeners
  }
  def addCaptureListener(listener: com.badlogic.gdx.scenes.scene2d.EventListener): scala.Boolean = {
    if (listener == null) {
      throw new java.lang.IllegalArgumentException("listener cannot be null.")
    } else ()
    if (!this.captureListeners.contains(listener, true)) {
      this.captureListeners.add(listener)
    } else ()
    return true
  }
  def removeCaptureListener(listener: com.badlogic.gdx.scenes.scene2d.EventListener): scala.Boolean = {
    if (listener == null) {
      throw new java.lang.IllegalArgumentException("listener cannot be null.")
    } else ()
    return this.captureListeners.removeValue(listener, true)
  }
  def getCaptureListeners(): com.badlogic.gdx.utils.DelayedRemovalArray[com.badlogic.gdx.scenes.scene2d.EventListener] = {
    return this.captureListeners
  }
  def addAction(action: com.badlogic.gdx.scenes.scene2d.Action): scala.Unit = {
    action.setActor(this)
    this.actions.add(action)
    if ((this.stage != null) && this.stage.getActionsRequestRendering()) {
      com.badlogic.gdx.Gdx.graphics.requestRendering()
    } else ()
  }
  def removeAction(action: com.badlogic.gdx.scenes.scene2d.Action): scala.Unit = {
    if ((action != null) && this.actions.removeValue(action, true)) {
      action.setActor(null)
    } else ()
  }
  def getActions(): com.badlogic.gdx.utils.Array[com.badlogic.gdx.scenes.scene2d.Action] = {
    return this.actions
  }
  def hasActions(): scala.Boolean = {
    return this.actions.size > 0
  }
  def clearActions(): scala.Unit = {
    { var i: scala.Int = this.actions.size - 1; while (i >= 0) { {
      this.actions.get(i).setActor(null)
    }; i = i - 1 } }
    this.actions.clear()
  }
  def clearListeners(): scala.Unit = {
    this.listeners.clear()
    this.captureListeners.clear()
  }
  def clear(): scala.Unit = {
    this.clearActions()
    this.clearListeners()
  }
  def getStage(): com.badlogic.gdx.scenes.scene2d.Stage = {
    return this.stage
  }
  def setStage(stage: com.badlogic.gdx.scenes.scene2d.Stage): scala.Unit = {
    this.stage = stage
  }
  def isDescendantOf(actor: Actor): scala.Boolean = {
    if (actor == null) {
      throw new java.lang.IllegalArgumentException("actor cannot be null.")
    } else ()
    var parent: Actor = this
    while ({ {
      if (parent == actor) {
        return true
      } else ()
      parent = parent.parent
    }; parent != null }) ()
    return false
  }
  def isAscendantOf(actor$arg: Actor): scala.Boolean = {
    var actor: Actor = actor$arg
    if (actor == null) {
      throw new java.lang.IllegalArgumentException("actor cannot be null.")
    } else ()
    while ({ {
      if (actor == this) {
        return true
      } else ()
      actor = actor.parent
    }; actor != null }) ()
    return false
  }
  def firstAscendant[T <: Actor](`type`: java.lang.Class[T]): T = {
    if (`type` == null) {
      throw new java.lang.IllegalArgumentException("actor cannot be null.")
    } else ()
    var actor: Actor = this
    while ({ {
      if (com.badlogic.gdx.utils.reflect.ClassReflection.isInstance(`type`, actor)) {
        return actor.asInstanceOf[T]
      } else ()
      actor = actor.parent
    }; actor != null }) ()
    return null.asInstanceOf[T]
  }
  def hasParent(): scala.Boolean = {
    return this.parent != null
  }
  def getParent(): com.badlogic.gdx.scenes.scene2d.Group = {
    return this.parent
  }
  def setParent(parent: com.badlogic.gdx.scenes.scene2d.Group): scala.Unit = {
    this.parent = parent
  }
  def isTouchable(): scala.Boolean = {
    return this.touchable == com.badlogic.gdx.scenes.scene2d.Touchable.enabled
  }
  def getTouchable(): com.badlogic.gdx.scenes.scene2d.Touchable = {
    return this.touchable
  }
  def setTouchable(touchable: com.badlogic.gdx.scenes.scene2d.Touchable): scala.Unit = {
    this.touchable = touchable
  }
  def isVisible(): scala.Boolean = {
    return this.visible
  }
  def setVisible(visible: scala.Boolean): scala.Unit = {
    this.visible = visible
  }
  def ascendantsVisible(): scala.Boolean = {
    var actor: Actor = this
    while ({ {
      if (!actor.isVisible()) {
        return false
      } else ()
      actor = actor.parent
    }; actor != null }) ()
    return true
  }
  def ancestorsVisible(): scala.Boolean = {
    return this.ascendantsVisible()
  }
  def hasKeyboardFocus(): scala.Boolean = {
    val stage: com.badlogic.gdx.scenes.scene2d.Stage = this.getStage()
    return (stage != null) && (stage.getKeyboardFocus() == this)
  }
  def hasScrollFocus(): scala.Boolean = {
    val stage: com.badlogic.gdx.scenes.scene2d.Stage = this.getStage()
    return (stage != null) && (stage.getScrollFocus() == this)
  }
  def isTouchFocusTarget(): scala.Boolean = {
    val stage: com.badlogic.gdx.scenes.scene2d.Stage = this.getStage()
    if (stage == null) {
      return false
    } else ();
    { var i: scala.Int = 0; val n: scala.Int = stage.touchFocuses.size; while (i < n) { {
      if (stage.touchFocuses.get(i).target == this) {
        return true
      } else ()
    }; i = i + 1 } }
    return false
  }
  def isTouchFocusListener(): scala.Boolean = {
    val stage: com.badlogic.gdx.scenes.scene2d.Stage = this.getStage()
    if (stage == null) {
      return false
    } else ();
    { var i: scala.Int = 0; val n: scala.Int = stage.touchFocuses.size; while (i < n) { {
      if (stage.touchFocuses.get(i).listenerActor == this) {
        return true
      } else ()
    }; i = i + 1 } }
    return false
  }
  def getUserObject(): java.lang.Object = {
    return this.userObject
  }
  def setUserObject(userObject: java.lang.Object): scala.Unit = {
    this.userObject = userObject
  }
  def getX(): scala.Float = {
    return this.x
  }
  def getX(alignment: scala.Int): scala.Float = {
    var x: scala.Float = this.x
    if ((alignment & com.badlogic.gdx.utils.Align.right) != 0) {
      x = x + this.width
    } else {
      if ((alignment & com.badlogic.gdx.utils.Align.left) == 0) {
        x = x + (this.width / 2)
      } else ()
    }
    return x
  }
  def setX(x: scala.Float): scala.Unit = {
    if (this.x != x) {
      this.x = x
      this.positionChanged()
    } else ()
  }
  def setX(x$arg: scala.Float, alignment: scala.Int): scala.Unit = {
    var x: scala.Float = x$arg
    if ((alignment & com.badlogic.gdx.utils.Align.right) != 0) {
      x = x - this.width
    } else {
      if ((alignment & com.badlogic.gdx.utils.Align.left) == 0) {
        x = x - (this.width / 2)
      } else ()
    }
    if (this.x != x) {
      this.x = x
      this.positionChanged()
    } else ()
  }
  def getY(): scala.Float = {
    return this.y
  }
  def setY(y: scala.Float): scala.Unit = {
    if (this.y != y) {
      this.y = y
      this.positionChanged()
    } else ()
  }
  def setY(y$arg: scala.Float, alignment: scala.Int): scala.Unit = {
    var y: scala.Float = y$arg
    if ((alignment & com.badlogic.gdx.utils.Align.top) != 0) {
      y = y - this.height
    } else {
      if ((alignment & com.badlogic.gdx.utils.Align.bottom) == 0) {
        y = y - (this.height / 2)
      } else ()
    }
    if (this.y != y) {
      this.y = y
      this.positionChanged()
    } else ()
  }
  def getY(alignment: scala.Int): scala.Float = {
    var y: scala.Float = this.y
    if ((alignment & com.badlogic.gdx.utils.Align.top) != 0) {
      y = y + this.height
    } else {
      if ((alignment & com.badlogic.gdx.utils.Align.bottom) == 0) {
        y = y + (this.height / 2)
      } else ()
    }
    return y
  }
  def setPosition(x: scala.Float, y: scala.Float): scala.Unit = {
    if ((this.x != x) || (this.y != y)) {
      this.x = x
      this.y = y
      this.positionChanged()
    } else ()
  }
  def setPosition(x$arg: scala.Float, y$arg: scala.Float, alignment: scala.Int): scala.Unit = {
    var x: scala.Float = x$arg
    var y: scala.Float = y$arg
    if ((alignment & com.badlogic.gdx.utils.Align.right) != 0) {
      x = x - this.width
    } else {
      if ((alignment & com.badlogic.gdx.utils.Align.left) == 0) {
        x = x - (this.width / 2)
      } else ()
    }
    if ((alignment & com.badlogic.gdx.utils.Align.top) != 0) {
      y = y - this.height
    } else {
      if ((alignment & com.badlogic.gdx.utils.Align.bottom) == 0) {
        y = y - (this.height / 2)
      } else ()
    }
    if ((this.x != x) || (this.y != y)) {
      this.x = x
      this.y = y
      this.positionChanged()
    } else ()
  }
  def moveBy(x: scala.Float, y: scala.Float): scala.Unit = {
    if ((x != 0) || (y != 0)) {
      this.x = this.x + x
      this.y = this.y + y
      this.positionChanged()
    } else ()
  }
  def getWidth(): scala.Float = {
    return this.width
  }
  def setWidth(width: scala.Float): scala.Unit = {
    if (this.width != width) {
      this.width = width
      this.sizeChanged()
    } else ()
  }
  def getHeight(): scala.Float = {
    return this.height
  }
  def setHeight(height: scala.Float): scala.Unit = {
    if (this.height != height) {
      this.height = height
      this.sizeChanged()
    } else ()
  }
  def getTop(): scala.Float = {
    return this.y + this.height
  }
  def getRight(): scala.Float = {
    return this.x + this.width
  }
  def positionChanged(): scala.Unit = {
    ()
  }
  def sizeChanged(): scala.Unit = {
    ()
  }
  def scaleChanged(): scala.Unit = {
    ()
  }
  def rotationChanged(): scala.Unit = {
    ()
  }
  def setSize(width: scala.Float, height: scala.Float): scala.Unit = {
    if ((this.width != width) || (this.height != height)) {
      this.width = width
      this.height = height
      this.sizeChanged()
    } else ()
  }
  def sizeBy(size: scala.Float): scala.Unit = {
    if (size != 0) {
      this.width = this.width + size
      this.height = this.height + size
      this.sizeChanged()
    } else ()
  }
  def sizeBy(width: scala.Float, height: scala.Float): scala.Unit = {
    if ((width != 0) || (height != 0)) {
      this.width = this.width + width
      this.height = this.height + height
      this.sizeChanged()
    } else ()
  }
  def setBounds(x: scala.Float, y: scala.Float, width: scala.Float, height: scala.Float): scala.Unit = {
    if ((this.x != x) || (this.y != y)) {
      this.x = x
      this.y = y
      this.positionChanged()
    } else ()
    if ((this.width != width) || (this.height != height)) {
      this.width = width
      this.height = height
      this.sizeChanged()
    } else ()
  }
  def getOriginX(): scala.Float = {
    return this.originX
  }
  def setOriginX(originX: scala.Float): scala.Unit = {
    this.originX = originX
  }
  def getOriginY(): scala.Float = {
    return this.originY
  }
  def setOriginY(originY: scala.Float): scala.Unit = {
    this.originY = originY
  }
  def setOrigin(originX: scala.Float, originY: scala.Float): scala.Unit = {
    this.originX = originX
    this.originY = originY
  }
  def setOrigin(alignment: scala.Int): scala.Unit = {
    if ((alignment & com.badlogic.gdx.utils.Align.left) != 0) {
      this.originX = 0
    } else {
      if ((alignment & com.badlogic.gdx.utils.Align.right) != 0) {
        this.originX = this.width
      } else {
        this.originX = this.width / 2
      }
    }
    if ((alignment & com.badlogic.gdx.utils.Align.bottom) != 0) {
      this.originY = 0
    } else {
      if ((alignment & com.badlogic.gdx.utils.Align.top) != 0) {
        this.originY = this.height
      } else {
        this.originY = this.height / 2
      }
    }
  }
  def getScaleX(): scala.Float = {
    return this.scaleX
  }
  def setScaleX(scaleX: scala.Float): scala.Unit = {
    if (this.scaleX != scaleX) {
      this.scaleX = scaleX
      this.scaleChanged()
    } else ()
  }
  def getScaleY(): scala.Float = {
    return this.scaleY
  }
  def setScaleY(scaleY: scala.Float): scala.Unit = {
    if (this.scaleY != scaleY) {
      this.scaleY = scaleY
      this.scaleChanged()
    } else ()
  }
  def setScale(scaleXY: scala.Float): scala.Unit = {
    if ((this.scaleX != scaleXY) || (this.scaleY != scaleXY)) {
      this.scaleX = scaleXY
      this.scaleY = scaleXY
      this.scaleChanged()
    } else ()
  }
  def setScale(scaleX: scala.Float, scaleY: scala.Float): scala.Unit = {
    if ((this.scaleX != scaleX) || (this.scaleY != scaleY)) {
      this.scaleX = scaleX
      this.scaleY = scaleY
      this.scaleChanged()
    } else ()
  }
  def scaleBy(scale: scala.Float): scala.Unit = {
    if (scale != 0) {
      this.scaleX = this.scaleX + scale
      this.scaleY = this.scaleY + scale
      this.scaleChanged()
    } else ()
  }
  def scaleBy(scaleX: scala.Float, scaleY: scala.Float): scala.Unit = {
    if ((scaleX != 0) || (scaleY != 0)) {
      this.scaleX = this.scaleX + scaleX
      this.scaleY = this.scaleY + scaleY
      this.scaleChanged()
    } else ()
  }
  def getRotation(): scala.Float = {
    return this.rotation
  }
  def setRotation(degrees: scala.Float): scala.Unit = {
    if (this.rotation != degrees) {
      this.rotation = degrees
      this.rotationChanged()
    } else ()
  }
  def rotateBy(amountInDegrees: scala.Float): scala.Unit = {
    if (amountInDegrees != 0) {
      this.rotation = (this.rotation + amountInDegrees) % 360
      this.rotationChanged()
    } else ()
  }
  def setColor(color: com.badlogic.gdx.graphics.Color): scala.Unit = {
    this.color.set(color)
  }
  def setColor(r: scala.Float, g: scala.Float, b: scala.Float, a: scala.Float): scala.Unit = {
    this.color.set(r, g, b, a)
  }
  def getColor(): com.badlogic.gdx.graphics.Color = {
    return this.color
  }
  def getName(): java.lang.String = {
    return this.name
  }
  def setName(name: java.lang.String): scala.Unit = {
    this.name = name
  }
  def toFront(): scala.Unit = {
    this.setZIndex(java.lang.Integer.MAX_VALUE)
  }
  def toBack(): scala.Unit = {
    this.setZIndex(0)
  }
  def setZIndex(index$arg: scala.Int): scala.Boolean = {
    var index: scala.Int = index$arg
    if (index < 0) {
      throw new java.lang.IllegalArgumentException("ZIndex cannot be < 0.")
    } else ()
    val parent: com.badlogic.gdx.scenes.scene2d.Group = this.parent
    if (parent == null) {
      return false
    } else ()
    val children: com.badlogic.gdx.utils.Array[Actor] = parent.children
    if (children.size <= 1) {
      return false
    } else ()
    index = java.lang.Math.min(index, children.size - 1)
    if (children.get(index) == this) {
      return false
    } else ()
    if (!children.removeValue(this, true)) {
      return false
    } else ()
    children.insert(index, this)
    return true
  }
  def getZIndex(): scala.Int = {
    val parent: com.badlogic.gdx.scenes.scene2d.Group = this.parent
    if (parent == null) {
      return -1
    } else ()
    return parent.children.indexOf(this, true)
  }
  def clipBegin(): scala.Boolean = {
    return this.clipBegin(this.x, this.y, this.width, this.height)
  }
  def clipBegin(x: scala.Float, y: scala.Float, width: scala.Float, height: scala.Float): scala.Boolean = {
    if ((width <= 0) || (height <= 0)) {
      return false
    } else ()
    val stage: com.badlogic.gdx.scenes.scene2d.Stage = this.stage
    if (stage == null) {
      return false
    } else ()
    val tableBounds: com.badlogic.gdx.math.Rectangle = com.badlogic.gdx.math.Rectangle.tmp
    tableBounds.x = x
    tableBounds.y = y
    tableBounds.width = width
    tableBounds.height = height
    val scissorBounds: com.badlogic.gdx.math.Rectangle = Actor.POOLS.obtain(classOf[com.badlogic.gdx.math.Rectangle])
    stage.calculateScissors(tableBounds, scissorBounds)
    if (com.badlogic.gdx.scenes.scene2d.utils.ScissorStack.pushScissors(scissorBounds)) {
      return true
    } else ()
    Actor.POOLS.free(scissorBounds)
    return false
  }
  def clipEnd(): scala.Unit = {
    Actor.POOLS.free(com.badlogic.gdx.scenes.scene2d.utils.ScissorStack.popScissors())
  }
  def screenToLocalCoordinates(screenCoords: com.badlogic.gdx.math.Vector2): com.badlogic.gdx.math.Vector2 = {
    val stage: com.badlogic.gdx.scenes.scene2d.Stage = this.stage
    if (stage == null) {
      return screenCoords
    } else ()
    return this.stageToLocalCoordinates(stage.screenToStageCoordinates(screenCoords))
  }
  def stageToLocalCoordinates(stageCoords: com.badlogic.gdx.math.Vector2): com.badlogic.gdx.math.Vector2 = {
    if (this.parent != null) {
      this.parent.stageToLocalCoordinates(stageCoords)
    } else ()
    this.parentToLocalCoordinates(stageCoords)
    return stageCoords
  }
  def parentToLocalCoordinates(parentCoords: com.badlogic.gdx.math.Vector2): com.badlogic.gdx.math.Vector2 = {
    val rotation: scala.Float = this.rotation
    val scaleX: scala.Float = this.scaleX
    val scaleY: scala.Float = this.scaleY
    val childX: scala.Float = this.x
    val childY: scala.Float = this.y
    if (rotation == 0) {
      if ((scaleX == 1) && (scaleY == 1)) {
        parentCoords.x = parentCoords.x - childX
        parentCoords.y = parentCoords.y - childY
      } else {
        val originX: scala.Float = this.originX
        val originY: scala.Float = this.originY
        parentCoords.x = (((parentCoords.x - childX) - originX) / scaleX) + originX
        parentCoords.y = (((parentCoords.y - childY) - originY) / scaleY) + originY
      }
    } else {
      val cos: scala.Float = java.lang.Math.cos(rotation * com.badlogic.gdx.math.MathUtils.degreesToRadians).asInstanceOf[scala.Float].asInstanceOf[scala.Float]
      val sin: scala.Float = java.lang.Math.sin(rotation * com.badlogic.gdx.math.MathUtils.degreesToRadians).asInstanceOf[scala.Float].asInstanceOf[scala.Float]
      val originX: scala.Float = this.originX
      val originY: scala.Float = this.originY
      val tox: scala.Float = (parentCoords.x - childX) - originX
      val toy: scala.Float = (parentCoords.y - childY) - originY
      parentCoords.x = (((tox * cos) + (toy * sin)) / scaleX) + originX
      parentCoords.y = (((tox * (-sin)) + (toy * cos)) / scaleY) + originY
    }
    return parentCoords
  }
  def localToScreenCoordinates(localCoords: com.badlogic.gdx.math.Vector2): com.badlogic.gdx.math.Vector2 = {
    val stage: com.badlogic.gdx.scenes.scene2d.Stage = this.stage
    if (stage == null) {
      return localCoords
    } else ()
    return stage.stageToScreenCoordinates(this.localToAscendantCoordinates(null, localCoords))
  }
  def localToStageCoordinates(localCoords: com.badlogic.gdx.math.Vector2): com.badlogic.gdx.math.Vector2 = {
    return this.localToAscendantCoordinates(null, localCoords)
  }
  def localToParentCoordinates(localCoords: com.badlogic.gdx.math.Vector2): com.badlogic.gdx.math.Vector2 = {
    val rotation: scala.Float = -this.rotation
    val scaleX: scala.Float = this.scaleX
    val scaleY: scala.Float = this.scaleY
    var x: scala.Float = this.x
    var y: scala.Float = this.y
    if (rotation == 0) {
      if ((scaleX == 1) && (scaleY == 1)) {
        localCoords.x = localCoords.x + x
        localCoords.y = localCoords.y + y
      } else {
        val originX: scala.Float = this.originX
        val originY: scala.Float = this.originY
        localCoords.x = (((localCoords.x - originX) * scaleX) + originX) + x
        localCoords.y = (((localCoords.y - originY) * scaleY) + originY) + y
      }
    } else {
      val cos: scala.Float = java.lang.Math.cos(rotation * com.badlogic.gdx.math.MathUtils.degreesToRadians).asInstanceOf[scala.Float].asInstanceOf[scala.Float]
      val sin: scala.Float = java.lang.Math.sin(rotation * com.badlogic.gdx.math.MathUtils.degreesToRadians).asInstanceOf[scala.Float].asInstanceOf[scala.Float]
      val originX: scala.Float = this.originX
      val originY: scala.Float = this.originY
      val tox: scala.Float = (localCoords.x - originX) * scaleX
      val toy: scala.Float = (localCoords.y - originY) * scaleY
      localCoords.x = (((tox * cos) + (toy * sin)) + originX) + x
      localCoords.y = (((tox * (-sin)) + (toy * cos)) + originY) + y
    }
    return localCoords
  }
  def localToAscendantCoordinates(ascendant: Actor, localCoords: com.badlogic.gdx.math.Vector2): com.badlogic.gdx.math.Vector2 = {
    var actor: Actor = this
    while ({ {
      actor.localToParentCoordinates(localCoords)
      actor = actor.parent
      if (actor == ascendant) {
        return localCoords
      } else ()
    }; actor != null }) ()
    throw new java.lang.IllegalArgumentException("Actor is not an ascendant: " + ascendant)
  }
  def localToActorCoordinates(actor: Actor, localCoords: com.badlogic.gdx.math.Vector2): com.badlogic.gdx.math.Vector2 = {
    this.localToStageCoordinates(localCoords)
    return actor.stageToLocalCoordinates(localCoords)
  }
  def drawDebug(shapes: com.badlogic.gdx.graphics.glutils.ShapeRenderer): scala.Unit = {
    this.drawDebugBounds(shapes)
  }
  def drawDebugBounds(shapes: com.badlogic.gdx.graphics.glutils.ShapeRenderer): scala.Unit = {
    if (!this.debug$field) {
      return
    } else ()
    shapes.set(com.badlogic.gdx.graphics.glutils.ShapeRenderer.ShapeType.Line)
    if (this.stage != null) {
      shapes.setColor(this.stage.getDebugColor())
    } else ()
    shapes.rect(this.x, this.y, this.originX, this.originY, this.width, this.height, this.scaleX, this.scaleY, this.rotation)
  }
  def setDebug(enabled: scala.Boolean): scala.Unit = {
    this.debug$field = enabled
    if (enabled) {
      com.badlogic.gdx.scenes.scene2d.Stage.debug = true
    } else ()
  }
  def getDebug(): scala.Boolean = {
    return this.debug$field
  }
  def debug(): Actor = {
    this.setDebug(true)
    return this
  }
  def toString(): java.lang.String = {
    var name: java.lang.String = this.name
    if (name == null) {
      name = this.getClass().getName()
      val dotIndex: scala.Int = name.lastIndexOf('.')
      if (dotIndex != (-1)) {
        name = name.substring(dotIndex + 1)
      } else ()
    } else ()
    return name
  }
}
object Actor {
  var POOLS: com.badlogic.gdx.utils.PoolManager = new com.badlogic.gdx.utils.PoolManager()
}