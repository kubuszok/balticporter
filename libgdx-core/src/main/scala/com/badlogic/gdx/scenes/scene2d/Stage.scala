package com.badlogic.gdx.scenes.scene2d

class Stage extends com.badlogic.gdx.InputAdapter with com.badlogic.gdx.utils.Disposable {
  var pools: com.badlogic.gdx.utils.PoolManager = new com.badlogic.gdx.utils.PoolManager()
  private var viewport: com.badlogic.gdx.utils.viewport.Viewport = null.asInstanceOf[com.badlogic.gdx.utils.viewport.Viewport]
  private var batch: com.badlogic.gdx.graphics.g2d.Batch = null.asInstanceOf[com.badlogic.gdx.graphics.g2d.Batch]
  private var ownsBatch: scala.Boolean = false
  private var root: com.badlogic.gdx.scenes.scene2d.Group = null.asInstanceOf[com.badlogic.gdx.scenes.scene2d.Group]
  private final val tempCoords: com.badlogic.gdx.math.Vector2 = new com.badlogic.gdx.math.Vector2()
  private final val pointerOverActors: scala.Array[com.badlogic.gdx.scenes.scene2d.Actor] = new Array[com.badlogic.gdx.scenes.scene2d.Actor](20)
  private final val pointerTouched: scala.Array[scala.Boolean] = new Array[scala.Boolean](20)
  private final val pointerScreenX: scala.Array[scala.Int] = new Array[scala.Int](20)
  private final val pointerScreenY: scala.Array[scala.Int] = new Array[scala.Int](20)
  private var mouseScreenX: scala.Int = 0
  private var mouseScreenY: scala.Int = 0
  private var mouseOverActor: com.badlogic.gdx.scenes.scene2d.Actor = null.asInstanceOf[com.badlogic.gdx.scenes.scene2d.Actor]
  private var keyboardFocus: com.badlogic.gdx.scenes.scene2d.Actor = null.asInstanceOf[com.badlogic.gdx.scenes.scene2d.Actor]
  private var scrollFocus: com.badlogic.gdx.scenes.scene2d.Actor = null.asInstanceOf[com.badlogic.gdx.scenes.scene2d.Actor]
  final val touchFocuses: com.badlogic.gdx.utils.SnapshotArray[com.badlogic.gdx.scenes.scene2d.Stage.TouchFocus] = new com.badlogic.gdx.utils.SnapshotArray[com.badlogic.gdx.scenes.scene2d.Stage.TouchFocus](true, 4, scala.Array[com.badlogic.gdx.scenes.scene2d.Stage.TouchFocus].<init>)
  private var actionsRequestRendering: scala.Boolean = true
  private var debugShapes: com.badlogic.gdx.graphics.glutils.ShapeRenderer = null.asInstanceOf[com.badlogic.gdx.graphics.glutils.ShapeRenderer]
  private var debugInvisible: scala.Boolean = false
  private var debugAll: scala.Boolean = false
  private var debugUnderMouse: scala.Boolean = false
  private var debugParentUnderMouse: scala.Boolean = false
  private var debugTableUnderMouse: com.badlogic.gdx.scenes.scene2d.ui.Table.Debug = com.badlogic.gdx.scenes.scene2d.ui.Table.Debug.none
  private final val debugColor: com.badlogic.gdx.graphics.Color = new com.badlogic.gdx.graphics.Color(0, 1, 0, 0.85f)
  def this(viewport: com.badlogic.gdx.utils.viewport.Viewport, batch: com.badlogic.gdx.graphics.g2d.Batch) = {
    this()
    if (viewport == null) {
      throw new java.lang.IllegalArgumentException("viewport cannot be null.")
    } else ()
    if (batch == null) {
      throw new java.lang.IllegalArgumentException("batch cannot be null.")
    } else ()
    this.viewport = viewport
    this.batch = batch
    this.pools.addPool(classOf[com.badlogic.gdx.scenes.scene2d.InputEvent], com.badlogic.gdx.scenes.scene2d.InputEvent.<init>)
    this.pools.addPool(classOf[com.badlogic.gdx.scenes.scene2d.utils.FocusListener.FocusEvent], com.badlogic.gdx.scenes.scene2d.utils.FocusListener.FocusEvent.<init>)
    this.pools.addPool(classOf[com.badlogic.gdx.scenes.scene2d.Stage.TouchFocus], com.badlogic.gdx.scenes.scene2d.Stage.TouchFocus.<init>)
    this.root = new com.badlogic.gdx.scenes.scene2d.Group()
    this.root.setStage(this)
    viewport.update(com.badlogic.gdx.Gdx.graphics.getWidth(), com.badlogic.gdx.Gdx.graphics.getHeight(), true)
  }
  def this(viewport: com.badlogic.gdx.utils.viewport.Viewport) = {
    this(viewport, new com.badlogic.gdx.graphics.g2d.SpriteBatch())
    this.ownsBatch = true
  }
  def this() = {
    this(new com.badlogic.gdx.utils.viewport.ScalingViewport(com.badlogic.gdx.utils.Scaling.stretch, com.badlogic.gdx.Gdx.graphics.getWidth(), com.badlogic.gdx.Gdx.graphics.getHeight(), new com.badlogic.gdx.graphics.OrthographicCamera()), new com.badlogic.gdx.graphics.g2d.SpriteBatch())
    this.ownsBatch = true
  }
  def draw(): scala.Unit = {
    val camera: com.badlogic.gdx.graphics.Camera = this.viewport.getCamera()
    camera.update()
    if (!this.root.isVisible()) {
      return
    } else ()
    val batch: com.badlogic.gdx.graphics.g2d.Batch = this.batch
    batch.setProjectionMatrix(camera.combined)
    batch.begin()
    this.root.draw(batch, 1)
    batch.`end`()
    if (Stage.debug) {
      this.drawDebug()
    } else ()
  }
  private def drawDebug(): scala.Unit = {
    if (this.debugShapes == null) {
      this.debugShapes = new com.badlogic.gdx.graphics.glutils.ShapeRenderer()
      this.debugShapes.setAutoShapeType(true)
    } else ()
    if ((this.debugUnderMouse || this.debugParentUnderMouse) || (this.debugTableUnderMouse != com.badlogic.gdx.scenes.scene2d.ui.Table.Debug.none)) {
      this.screenToStageCoordinates(this.tempCoords.set(com.badlogic.gdx.Gdx.input.getX(), com.badlogic.gdx.Gdx.input.getY()))
      var actor: com.badlogic.gdx.scenes.scene2d.Actor = this.hit(this.tempCoords.x, this.tempCoords.y, true)
      if (actor == null) {
        return
      } else ()
      if (this.debugParentUnderMouse && (actor.parent != null)) {
        actor = actor.parent
      } else ()
      if (this.debugTableUnderMouse == com.badlogic.gdx.scenes.scene2d.ui.Table.Debug.none) {
        actor.setDebug(true)
      } else {
        while (actor != null) {
          if (actor.isInstanceOf[com.badlogic.gdx.scenes.scene2d.ui.Table]) {
            /* break */ ()
          } else ()
          actor = actor.parent
        }
        if (actor == null) {
          return
        } else ()
        actor.asInstanceOf[com.badlogic.gdx.scenes.scene2d.ui.Table].debug(this.debugTableUnderMouse)
      }
      if (this.debugAll && actor.isInstanceOf[com.badlogic.gdx.scenes.scene2d.Group]) {
        actor.asInstanceOf[com.badlogic.gdx.scenes.scene2d.Group].debugAll()
      } else ()
      this.disableDebug(this.root, actor)
    } else {
      if (this.debugAll) {
        this.root.debugAll()
      } else ()
    }
    com.badlogic.gdx.Gdx.gl.glEnable(com.badlogic.gdx.graphics.GL20.GL_BLEND)
    this.debugShapes.setProjectionMatrix(this.viewport.getCamera().combined)
    this.debugShapes.begin()
    this.root.drawDebug(this.debugShapes)
    this.debugShapes.`end`()
    com.badlogic.gdx.Gdx.gl.glDisable(com.badlogic.gdx.graphics.GL20.GL_BLEND)
  }
  private def disableDebug(actor: com.badlogic.gdx.scenes.scene2d.Actor, except: com.badlogic.gdx.scenes.scene2d.Actor): scala.Unit = {
    if (actor == except) {
      return
    } else ()
    actor.setDebug(false)
    if (actor.isInstanceOf[com.badlogic.gdx.scenes.scene2d.Group]) {
      val children: com.badlogic.gdx.utils.SnapshotArray[com.badlogic.gdx.scenes.scene2d.Actor] = actor.asInstanceOf[com.badlogic.gdx.scenes.scene2d.Group].children;
      { var i: scala.Int = 0; val n: scala.Int = children.size; while (i < n) { {
        this.disableDebug(children.get(i), except)
      }; i = i + 1 } }
    } else ()
  }
  def act(): scala.Unit = {
    this.act(java.lang.Math.min(com.badlogic.gdx.Gdx.graphics.getDeltaTime(), 1 / 30.0f))
  }
  def act(delta: scala.Float): scala.Unit = {
    { var pointer: scala.Int = 0; val n: scala.Int = this.pointerOverActors.length; while (pointer < n) { {
      val overLast: com.badlogic.gdx.scenes.scene2d.Actor = this.pointerOverActors(pointer)
      if (this.pointerTouched(pointer)) {
        this.pointerOverActors(pointer) = this.fireEnterAndExit(overLast, this.pointerScreenX(pointer), this.pointerScreenY(pointer), pointer)
      } else {
        if (overLast != null) {
          this.pointerOverActors(pointer) = null
          this.fireExit(overLast, this.pointerScreenX(pointer), this.pointerScreenY(pointer), pointer)
        } else ()
      }
    }; pointer = pointer + 1 } }
    val `type`: com.badlogic.gdx.Application.ApplicationType = com.badlogic.gdx.Gdx.app.getType()
    if (((`type` == com.badlogic.gdx.Application.ApplicationType.Desktop) || (`type` == com.badlogic.gdx.Application.ApplicationType.Applet)) || (`type` == com.badlogic.gdx.Application.ApplicationType.WebGL)) {
      this.mouseOverActor = this.fireEnterAndExit(this.mouseOverActor, this.mouseScreenX, this.mouseScreenY, -1)
    } else ()
    this.root.act(delta)
  }
  private def fireEnterAndExit(overLast: com.badlogic.gdx.scenes.scene2d.Actor, screenX: scala.Int, screenY: scala.Int, pointer: scala.Int): com.badlogic.gdx.scenes.scene2d.Actor = {
    this.screenToStageCoordinates(this.tempCoords.set(screenX, screenY))
    val over: com.badlogic.gdx.scenes.scene2d.Actor = this.hit(this.tempCoords.x, this.tempCoords.y, true)
    if (over == overLast) {
      return overLast
    } else ()
    if (overLast != null) {
      val event: com.badlogic.gdx.scenes.scene2d.InputEvent = this.pools.obtain(classOf[com.badlogic.gdx.scenes.scene2d.InputEvent])
      event.setType(com.badlogic.gdx.scenes.scene2d.InputEvent.Type.exit)
      event.setStage(this)
      event.setStageX(this.tempCoords.x)
      event.setStageY(this.tempCoords.y)
      event.setPointer(pointer)
      event.setRelatedActor(over)
      overLast.fire(event)
      this.pools.free(event)
    } else ()
    if (over != null) {
      val event: com.badlogic.gdx.scenes.scene2d.InputEvent = this.pools.obtain(classOf[com.badlogic.gdx.scenes.scene2d.InputEvent])
      event.setType(com.badlogic.gdx.scenes.scene2d.InputEvent.Type.enter)
      event.setStage(this)
      event.setStageX(this.tempCoords.x)
      event.setStageY(this.tempCoords.y)
      event.setPointer(pointer)
      event.setRelatedActor(overLast)
      over.fire(event)
      this.pools.free(event)
    } else ()
    return over
  }
  private def fireExit(actor: com.badlogic.gdx.scenes.scene2d.Actor, screenX: scala.Int, screenY: scala.Int, pointer: scala.Int): scala.Unit = {
    this.screenToStageCoordinates(this.tempCoords.set(screenX, screenY))
    val event: com.badlogic.gdx.scenes.scene2d.InputEvent = this.pools.obtain(classOf[com.badlogic.gdx.scenes.scene2d.InputEvent])
    event.setType(com.badlogic.gdx.scenes.scene2d.InputEvent.Type.exit)
    event.setStage(this)
    event.setStageX(this.tempCoords.x)
    event.setStageY(this.tempCoords.y)
    event.setPointer(pointer)
    event.setRelatedActor(actor)
    actor.fire(event)
    this.pools.free(event)
  }
  def touchDown(screenX: scala.Int, screenY: scala.Int, pointer: scala.Int, button: scala.Int): scala.Boolean = {
    if (!this.isInsideViewport(screenX, screenY)) {
      return false
    } else ()
    this.pointerTouched(pointer) = true
    this.pointerScreenX(pointer) = screenX
    this.pointerScreenY(pointer) = screenY
    this.screenToStageCoordinates(this.tempCoords.set(screenX, screenY))
    val event: com.badlogic.gdx.scenes.scene2d.InputEvent = this.pools.obtain(classOf[com.badlogic.gdx.scenes.scene2d.InputEvent])
    event.setType(com.badlogic.gdx.scenes.scene2d.InputEvent.Type.touchDown)
    event.setStage(this)
    event.setStageX(this.tempCoords.x)
    event.setStageY(this.tempCoords.y)
    event.setPointer(pointer)
    event.setButton(button)
    val target: com.badlogic.gdx.scenes.scene2d.Actor = this.hit(this.tempCoords.x, this.tempCoords.y, true)
    if (target == null) {
      if (this.root.getTouchable() == com.badlogic.gdx.scenes.scene2d.Touchable.enabled) {
        this.root.fire(event)
      } else ()
    } else {
      target.fire(event)
    }
    val handled: scala.Boolean = event.isHandled()
    this.pools.free(event)
    return handled
  }
  def touchDragged(screenX: scala.Int, screenY: scala.Int, pointer: scala.Int): scala.Boolean = {
    this.pointerScreenX(pointer) = screenX
    this.pointerScreenY(pointer) = screenY
    this.mouseScreenX = screenX
    this.mouseScreenY = screenY
    if (this.touchFocuses.size == 0) {
      return false
    } else ()
    this.screenToStageCoordinates(this.tempCoords.set(screenX, screenY))
    val event: com.badlogic.gdx.scenes.scene2d.InputEvent = this.pools.obtain(classOf[com.badlogic.gdx.scenes.scene2d.InputEvent])
    event.setType(com.badlogic.gdx.scenes.scene2d.InputEvent.Type.touchDragged)
    event.setStage(this)
    event.setStageX(this.tempCoords.x)
    event.setStageY(this.tempCoords.y)
    event.setPointer(pointer)
    val touchFocuses: com.badlogic.gdx.utils.SnapshotArray[com.badlogic.gdx.scenes.scene2d.Stage.TouchFocus] = this.touchFocuses
    val focuses: scala.Array[com.badlogic.gdx.scenes.scene2d.Stage.TouchFocus] = touchFocuses.begin();
    { var i: scala.Int = 0; val n: scala.Int = touchFocuses.size; while (i < n) { {
      val focus: com.badlogic.gdx.scenes.scene2d.Stage.TouchFocus = focuses(i)
      if (focus.pointer != pointer) {
        /* continue */ ()
      } else ()
      if (!touchFocuses.contains(focus, true)) {
        /* continue */ ()
      } else ()
      event.setTarget(focus.target)
      event.setListenerActor(focus.listenerActor)
      if (focus.listener.handle(event)) {
        event.handle()
      } else ()
    }; i = i + 1 } }
    touchFocuses.`end`()
    val handled: scala.Boolean = event.isHandled()
    this.pools.free(event)
    return handled
  }
  def touchUp(screenX: scala.Int, screenY: scala.Int, pointer: scala.Int, button: scala.Int): scala.Boolean = {
    this.pointerTouched(pointer) = false
    this.pointerScreenX(pointer) = screenX
    this.pointerScreenY(pointer) = screenY
    if (this.touchFocuses.size == 0) {
      return false
    } else ()
    this.screenToStageCoordinates(this.tempCoords.set(screenX, screenY))
    val event: com.badlogic.gdx.scenes.scene2d.InputEvent = this.pools.obtain(classOf[com.badlogic.gdx.scenes.scene2d.InputEvent])
    event.setType(com.badlogic.gdx.scenes.scene2d.InputEvent.Type.touchUp)
    event.setStage(this)
    event.setStageX(this.tempCoords.x)
    event.setStageY(this.tempCoords.y)
    event.setPointer(pointer)
    event.setButton(button)
    val touchFocuses: com.badlogic.gdx.utils.SnapshotArray[com.badlogic.gdx.scenes.scene2d.Stage.TouchFocus] = this.touchFocuses
    val focuses: scala.Array[com.badlogic.gdx.scenes.scene2d.Stage.TouchFocus] = touchFocuses.begin();
    { var i: scala.Int = 0; val n: scala.Int = touchFocuses.size; while (i < n) { {
      val focus: com.badlogic.gdx.scenes.scene2d.Stage.TouchFocus = focuses(i)
      if ((focus.pointer != pointer) || (focus.button != button)) {
        /* continue */ ()
      } else ()
      if (!touchFocuses.removeValue(focus, true)) {
        /* continue */ ()
      } else ()
      event.setTarget(focus.target)
      event.setListenerActor(focus.listenerActor)
      if (focus.listener.handle(event)) {
        event.handle()
      } else ()
      this.pools.free(focus)
    }; i = i + 1 } }
    touchFocuses.`end`()
    val handled: scala.Boolean = event.isHandled()
    this.pools.free(event)
    return handled
  }
  def touchCancelled(screenX: scala.Int, screenY: scala.Int, pointer: scala.Int, button: scala.Int): scala.Boolean = {
    this.cancelTouchFocus()
    return false
  }
  def mouseMoved(screenX: scala.Int, screenY: scala.Int): scala.Boolean = {
    this.mouseScreenX = screenX
    this.mouseScreenY = screenY
    if (!this.isInsideViewport(screenX, screenY)) {
      return false
    } else ()
    this.screenToStageCoordinates(this.tempCoords.set(screenX, screenY))
    val event: com.badlogic.gdx.scenes.scene2d.InputEvent = this.pools.obtain(classOf[com.badlogic.gdx.scenes.scene2d.InputEvent])
    event.setType(com.badlogic.gdx.scenes.scene2d.InputEvent.Type.mouseMoved)
    event.setStage(this)
    event.setStageX(this.tempCoords.x)
    event.setStageY(this.tempCoords.y)
    var target: com.badlogic.gdx.scenes.scene2d.Actor = this.hit(this.tempCoords.x, this.tempCoords.y, true)
    if (target == null) {
      target = this.root
    } else ()
    target.fire(event)
    val handled: scala.Boolean = event.isHandled()
    this.pools.free(event)
    return handled
  }
  def scrolled(amountX: scala.Float, amountY: scala.Float): scala.Boolean = {
    val target: com.badlogic.gdx.scenes.scene2d.Actor = if (this.scrollFocus == null) this.root else this.scrollFocus
    this.screenToStageCoordinates(this.tempCoords.set(this.mouseScreenX, this.mouseScreenY))
    val event: com.badlogic.gdx.scenes.scene2d.InputEvent = this.pools.obtain(classOf[com.badlogic.gdx.scenes.scene2d.InputEvent])
    event.setType(com.badlogic.gdx.scenes.scene2d.InputEvent.Type.scrolled)
    event.setStage(this)
    event.setStageX(this.tempCoords.x)
    event.setStageY(this.tempCoords.y)
    event.setScrollAmountX(amountX)
    event.setScrollAmountY(amountY)
    target.fire(event)
    val handled: scala.Boolean = event.isHandled()
    this.pools.free(event)
    return handled
  }
  def keyDown(keyCode: scala.Int): scala.Boolean = {
    val target: com.badlogic.gdx.scenes.scene2d.Actor = if (this.keyboardFocus == null) this.root else this.keyboardFocus
    val event: com.badlogic.gdx.scenes.scene2d.InputEvent = this.pools.obtain(classOf[com.badlogic.gdx.scenes.scene2d.InputEvent])
    event.setType(com.badlogic.gdx.scenes.scene2d.InputEvent.Type.keyDown)
    event.setStage(this)
    event.setKeyCode(keyCode)
    target.fire(event)
    val handled: scala.Boolean = event.isHandled()
    this.pools.free(event)
    return handled
  }
  def keyUp(keyCode: scala.Int): scala.Boolean = {
    val target: com.badlogic.gdx.scenes.scene2d.Actor = if (this.keyboardFocus == null) this.root else this.keyboardFocus
    val event: com.badlogic.gdx.scenes.scene2d.InputEvent = this.pools.obtain(classOf[com.badlogic.gdx.scenes.scene2d.InputEvent])
    event.setType(com.badlogic.gdx.scenes.scene2d.InputEvent.Type.keyUp)
    event.setStage(this)
    event.setKeyCode(keyCode)
    target.fire(event)
    val handled: scala.Boolean = event.isHandled()
    this.pools.free(event)
    return handled
  }
  def keyTyped(character: scala.Char): scala.Boolean = {
    val target: com.badlogic.gdx.scenes.scene2d.Actor = if (this.keyboardFocus == null) this.root else this.keyboardFocus
    val event: com.badlogic.gdx.scenes.scene2d.InputEvent = this.pools.obtain(classOf[com.badlogic.gdx.scenes.scene2d.InputEvent])
    event.setType(com.badlogic.gdx.scenes.scene2d.InputEvent.Type.keyTyped)
    event.setStage(this)
    event.setCharacter(character)
    target.fire(event)
    val handled: scala.Boolean = event.isHandled()
    this.pools.free(event)
    return handled
  }
  def addTouchFocus(listener: com.badlogic.gdx.scenes.scene2d.EventListener, listenerActor: com.badlogic.gdx.scenes.scene2d.Actor, target: com.badlogic.gdx.scenes.scene2d.Actor, pointer: scala.Int, button: scala.Int): scala.Unit = {
    val focus: com.badlogic.gdx.scenes.scene2d.Stage.TouchFocus = this.pools.obtain(classOf[com.badlogic.gdx.scenes.scene2d.Stage.TouchFocus])
    focus.listenerActor = listenerActor
    focus.target = target
    focus.listener = listener
    focus.pointer = pointer
    focus.button = button
    this.touchFocuses.add(focus)
  }
  def removeTouchFocus(listener: com.badlogic.gdx.scenes.scene2d.EventListener, listenerActor: com.badlogic.gdx.scenes.scene2d.Actor, target: com.badlogic.gdx.scenes.scene2d.Actor, pointer: scala.Int, button: scala.Int): scala.Unit = {
    val touchFocuses: com.badlogic.gdx.utils.SnapshotArray[com.badlogic.gdx.scenes.scene2d.Stage.TouchFocus] = this.touchFocuses;
    { var i: scala.Int = touchFocuses.size - 1; while (i >= 0) { {
      val focus: com.badlogic.gdx.scenes.scene2d.Stage.TouchFocus = touchFocuses.get(i)
      if (((((focus.listener == listener) && (focus.listenerActor == listenerActor)) && (focus.target == target)) && (focus.pointer == pointer)) && (focus.button == button)) {
        touchFocuses.removeIndex(i)
        this.pools.free(focus)
      } else ()
    }; i = i - 1 } }
  }
  def cancelTouchFocus(listenerActor: com.badlogic.gdx.scenes.scene2d.Actor): scala.Unit = {
    var event: com.badlogic.gdx.scenes.scene2d.InputEvent = null
    val touchFocuses: com.badlogic.gdx.utils.SnapshotArray[com.badlogic.gdx.scenes.scene2d.Stage.TouchFocus] = this.touchFocuses
    val items: scala.Array[com.badlogic.gdx.scenes.scene2d.Stage.TouchFocus] = touchFocuses.begin();
    { var i: scala.Int = 0; val n: scala.Int = touchFocuses.size; while (i < n) { {
      val focus: com.badlogic.gdx.scenes.scene2d.Stage.TouchFocus = items(i)
      if (focus.listenerActor != listenerActor) {
        /* continue */ ()
      } else ()
      if (!touchFocuses.removeValue(focus, true)) {
        /* continue */ ()
      } else ()
      if (event == null) {
        event = this.pools.obtain(classOf[com.badlogic.gdx.scenes.scene2d.InputEvent])
        event.setType(com.badlogic.gdx.scenes.scene2d.InputEvent.Type.touchUp)
        event.setStage(this)
        event.setStageX(java.lang.Integer.MIN_VALUE)
        event.setStageY(java.lang.Integer.MIN_VALUE)
      } else ()
      event.setTarget(focus.target)
      event.setListenerActor(focus.listenerActor)
      event.setPointer(focus.pointer)
      event.setButton(focus.button)
      focus.listener.handle(event)
    }; i = i + 1 } }
    touchFocuses.`end`()
    if (event != null) {
      this.pools.free(event)
    } else ()
  }
  def cancelTouchFocus(): scala.Unit = {
    this.cancelTouchFocusExcept(null, null)
  }
  def cancelTouchFocusExcept(exceptListener: com.badlogic.gdx.scenes.scene2d.EventListener, exceptActor: com.badlogic.gdx.scenes.scene2d.Actor): scala.Unit = {
    val event: com.badlogic.gdx.scenes.scene2d.InputEvent = this.pools.obtain(classOf[com.badlogic.gdx.scenes.scene2d.InputEvent])
    event.setType(com.badlogic.gdx.scenes.scene2d.InputEvent.Type.touchUp)
    event.setStage(this)
    event.setStageX(java.lang.Integer.MIN_VALUE)
    event.setStageY(java.lang.Integer.MIN_VALUE)
    val touchFocuses: com.badlogic.gdx.utils.SnapshotArray[com.badlogic.gdx.scenes.scene2d.Stage.TouchFocus] = this.touchFocuses
    val items: scala.Array[com.badlogic.gdx.scenes.scene2d.Stage.TouchFocus] = touchFocuses.begin();
    { var i: scala.Int = 0; val n: scala.Int = touchFocuses.size; while (i < n) { {
      val focus: com.badlogic.gdx.scenes.scene2d.Stage.TouchFocus = items(i)
      if ((focus.listener == exceptListener) && (focus.listenerActor == exceptActor)) {
        /* continue */ ()
      } else ()
      if (!touchFocuses.removeValue(focus, true)) {
        /* continue */ ()
      } else ()
      event.setTarget(focus.target)
      event.setListenerActor(focus.listenerActor)
      event.setPointer(focus.pointer)
      event.setButton(focus.button)
      focus.listener.handle(event)
    }; i = i + 1 } }
    touchFocuses.`end`()
    this.pools.free(event)
  }
  def addActor(actor: com.badlogic.gdx.scenes.scene2d.Actor): scala.Unit = {
    this.root.addActor(actor)
  }
  def addAction(action: com.badlogic.gdx.scenes.scene2d.Action): scala.Unit = {
    this.root.addAction(action)
  }
  def getActors(): com.badlogic.gdx.utils.Array[com.badlogic.gdx.scenes.scene2d.Actor] = {
    return this.root.children
  }
  def addListener(listener: com.badlogic.gdx.scenes.scene2d.EventListener): scala.Boolean = {
    return this.root.addListener(listener)
  }
  def removeListener(listener: com.badlogic.gdx.scenes.scene2d.EventListener): scala.Boolean = {
    return this.root.removeListener(listener)
  }
  def addCaptureListener(listener: com.badlogic.gdx.scenes.scene2d.EventListener): scala.Boolean = {
    return this.root.addCaptureListener(listener)
  }
  def removeCaptureListener(listener: com.badlogic.gdx.scenes.scene2d.EventListener): scala.Boolean = {
    return this.root.removeCaptureListener(listener)
  }
  def actorRemoved(actor: com.badlogic.gdx.scenes.scene2d.Actor): scala.Unit = {
    { var pointer: scala.Int = 0; val n: scala.Int = this.pointerOverActors.length; while (pointer < n) { {
      if (actor == this.pointerOverActors(pointer)) {
        this.pointerOverActors(pointer) = null
        this.fireExit(actor, this.pointerScreenX(pointer), this.pointerScreenY(pointer), pointer)
      } else ()
    }; pointer = pointer + 1 } }
    if (actor == this.mouseOverActor) {
      this.mouseOverActor = null
      this.fireExit(actor, this.mouseScreenX, this.mouseScreenY, -1)
    } else ()
  }
  def clear(): scala.Unit = {
    this.unfocusAll()
    this.root.clear()
  }
  def unfocusAll(): scala.Unit = {
    this.setScrollFocus(null)
    this.setKeyboardFocus(null)
    this.cancelTouchFocus()
  }
  def unfocus(actor: com.badlogic.gdx.scenes.scene2d.Actor): scala.Unit = {
    this.cancelTouchFocus(actor)
    if ((this.scrollFocus != null) && this.scrollFocus.isDescendantOf(actor)) {
      this.setScrollFocus(null)
    } else ()
    if ((this.keyboardFocus != null) && this.keyboardFocus.isDescendantOf(actor)) {
      this.setKeyboardFocus(null)
    } else ()
  }
  def setKeyboardFocus(actor: com.badlogic.gdx.scenes.scene2d.Actor): scala.Boolean = {
    if (this.keyboardFocus == actor) {
      return true
    } else ()
    val event: com.badlogic.gdx.scenes.scene2d.utils.FocusListener.FocusEvent = this.pools.obtain(classOf[com.badlogic.gdx.scenes.scene2d.utils.FocusListener.FocusEvent])
    event.setStage(this)
    event.setType(com.badlogic.gdx.scenes.scene2d.utils.FocusListener.FocusEvent.Type.keyboard)
    val oldKeyboardFocus: com.badlogic.gdx.scenes.scene2d.Actor = this.keyboardFocus
    if (oldKeyboardFocus != null) {
      event.setFocused(false)
      event.setRelatedActor(actor)
      oldKeyboardFocus.fire(event)
    } else ()
    var success: scala.Boolean = !event.isCancelled()
    if (success) {
      this.keyboardFocus = actor
      if (actor != null) {
        event.setFocused(true)
        event.setRelatedActor(oldKeyboardFocus)
        actor.fire(event)
        success = !event.isCancelled()
        if (!success) {
          this.keyboardFocus = oldKeyboardFocus
        } else ()
      } else ()
    } else ()
    this.pools.free(event)
    return success
  }
  def getKeyboardFocus(): com.badlogic.gdx.scenes.scene2d.Actor = {
    return this.keyboardFocus
  }
  def setScrollFocus(actor: com.badlogic.gdx.scenes.scene2d.Actor): scala.Boolean = {
    if (this.scrollFocus == actor) {
      return true
    } else ()
    val event: com.badlogic.gdx.scenes.scene2d.utils.FocusListener.FocusEvent = this.pools.obtain(classOf[com.badlogic.gdx.scenes.scene2d.utils.FocusListener.FocusEvent])
    event.setStage(this)
    event.setType(com.badlogic.gdx.scenes.scene2d.utils.FocusListener.FocusEvent.Type.scroll)
    val oldScrollFocus: com.badlogic.gdx.scenes.scene2d.Actor = this.scrollFocus
    if (oldScrollFocus != null) {
      event.setFocused(false)
      event.setRelatedActor(actor)
      oldScrollFocus.fire(event)
    } else ()
    var success: scala.Boolean = !event.isCancelled()
    if (success) {
      this.scrollFocus = actor
      if (actor != null) {
        event.setFocused(true)
        event.setRelatedActor(oldScrollFocus)
        actor.fire(event)
        success = !event.isCancelled()
        if (!success) {
          this.scrollFocus = oldScrollFocus
        } else ()
      } else ()
    } else ()
    this.pools.free(event)
    return success
  }
  def getScrollFocus(): com.badlogic.gdx.scenes.scene2d.Actor = {
    return this.scrollFocus
  }
  def getBatch(): com.badlogic.gdx.graphics.g2d.Batch = {
    return this.batch
  }
  def getViewport(): com.badlogic.gdx.utils.viewport.Viewport = {
    return this.viewport
  }
  def setViewport(viewport: com.badlogic.gdx.utils.viewport.Viewport): scala.Unit = {
    this.viewport = viewport
  }
  def getWidth(): scala.Float = {
    return this.viewport.getWorldWidth()
  }
  def getHeight(): scala.Float = {
    return this.viewport.getWorldHeight()
  }
  def getCamera(): com.badlogic.gdx.graphics.Camera = {
    return this.viewport.getCamera()
  }
  def getRoot(): com.badlogic.gdx.scenes.scene2d.Group = {
    return this.root
  }
  def setRoot(root: com.badlogic.gdx.scenes.scene2d.Group): scala.Unit = {
    if (root.parent != null) {
      root.parent.removeActor(root, false)
    } else ()
    this.root = root
    root.setParent(null)
    root.setStage(this)
  }
  def hit(stageX: scala.Float, stageY: scala.Float, touchable: scala.Boolean): com.badlogic.gdx.scenes.scene2d.Actor = {
    this.root.parentToLocalCoordinates(this.tempCoords.set(stageX, stageY))
    return this.root.hit(this.tempCoords.x, this.tempCoords.y, touchable)
  }
  def screenToStageCoordinates(screenCoords: com.badlogic.gdx.math.Vector2): com.badlogic.gdx.math.Vector2 = {
    this.viewport.unproject(screenCoords)
    return screenCoords
  }
  def stageToScreenCoordinates(stageCoords: com.badlogic.gdx.math.Vector2): com.badlogic.gdx.math.Vector2 = {
    this.viewport.project(stageCoords)
    stageCoords.y = com.badlogic.gdx.Gdx.graphics.getHeight() - stageCoords.y
    return stageCoords
  }
  def toScreenCoordinates(coords: com.badlogic.gdx.math.Vector2, transformMatrix: com.badlogic.gdx.math.Matrix4): com.badlogic.gdx.math.Vector2 = {
    return this.viewport.toScreenCoordinates(coords, transformMatrix)
  }
  def calculateScissors(localRect: com.badlogic.gdx.math.Rectangle, scissorRect: com.badlogic.gdx.math.Rectangle): scala.Unit = {
    var transformMatrix: com.badlogic.gdx.math.Matrix4 = null.asInstanceOf[com.badlogic.gdx.math.Matrix4]
    if ((this.debugShapes != null) && this.debugShapes.isDrawing()) {
      transformMatrix = this.debugShapes.getTransformMatrix()
    } else {
      transformMatrix = this.batch.getTransformMatrix()
    }
    this.viewport.calculateScissors(transformMatrix, localRect, scissorRect)
  }
  def setActionsRequestRendering(actionsRequestRendering: scala.Boolean): scala.Unit = {
    this.actionsRequestRendering = actionsRequestRendering
  }
  def getActionsRequestRendering(): scala.Boolean = {
    return this.actionsRequestRendering
  }
  def getDebugColor(): com.badlogic.gdx.graphics.Color = {
    return this.debugColor
  }
  def setDebugInvisible(debugInvisible: scala.Boolean): scala.Unit = {
    this.debugInvisible = debugInvisible
  }
  def setDebugAll(debugAll: scala.Boolean): scala.Unit = {
    if (this.debugAll == debugAll) {
      return
    } else ()
    this.debugAll = debugAll
    if (debugAll) {
      Stage.debug = true
    } else {
      this.root.setDebug(false, true)
    }
  }
  def isDebugAll(): scala.Boolean = {
    return this.debugAll
  }
  def setDebugUnderMouse(debugUnderMouse: scala.Boolean): scala.Unit = {
    if (this.debugUnderMouse == debugUnderMouse) {
      return
    } else ()
    this.debugUnderMouse = debugUnderMouse
    if (debugUnderMouse) {
      Stage.debug = true
    } else {
      this.root.setDebug(false, true)
    }
  }
  def setDebugParentUnderMouse(debugParentUnderMouse: scala.Boolean): scala.Unit = {
    if (this.debugParentUnderMouse == debugParentUnderMouse) {
      return
    } else ()
    this.debugParentUnderMouse = debugParentUnderMouse
    if (debugParentUnderMouse) {
      Stage.debug = true
    } else {
      this.root.setDebug(false, true)
    }
  }
  def setDebugTableUnderMouse(debugTableUnderMouse$arg: com.badlogic.gdx.scenes.scene2d.ui.Table.Debug): scala.Unit = {
    var debugTableUnderMouse: com.badlogic.gdx.scenes.scene2d.ui.Table.Debug = debugTableUnderMouse$arg
    if (debugTableUnderMouse == null) {
      debugTableUnderMouse = com.badlogic.gdx.scenes.scene2d.ui.Table.Debug.none
    } else ()
    if (this.debugTableUnderMouse == debugTableUnderMouse) {
      return
    } else ()
    this.debugTableUnderMouse = debugTableUnderMouse
    if (debugTableUnderMouse != com.badlogic.gdx.scenes.scene2d.ui.Table.Debug.none) {
      Stage.debug = true
    } else {
      this.root.setDebug(false, true)
    }
  }
  def setDebugTableUnderMouse(debugTableUnderMouse: scala.Boolean): scala.Unit = {
    this.setDebugTableUnderMouse(if (debugTableUnderMouse) com.badlogic.gdx.scenes.scene2d.ui.Table.Debug.all else com.badlogic.gdx.scenes.scene2d.ui.Table.Debug.none)
  }
  def dispose(): scala.Unit = {
    this.clear()
    if (this.ownsBatch) {
      this.batch.dispose()
    } else ()
    if (this.debugShapes != null) {
      this.debugShapes.dispose()
    } else ()
  }
  def isInsideViewport(screenX: scala.Int, screenY$arg: scala.Int): scala.Boolean = {
    var screenY: scala.Int = screenY$arg
    val x0: scala.Int = this.viewport.getScreenX()
    val x1: scala.Int = x0 + this.viewport.getScreenWidth()
    val y0: scala.Int = this.viewport.getScreenY()
    val y1: scala.Int = y0 + this.viewport.getScreenHeight()
    screenY = (com.badlogic.gdx.Gdx.graphics.getHeight() - 1) - screenY
    return (((screenX >= x0) && (screenX < x1)) && (screenY >= y0)) && (screenY < y1)
  }
}
object Stage {
  var debug: scala.Boolean = false
  final class TouchFocus extends com.badlogic.gdx.utils.Pool.Poolable {
    var listener: com.badlogic.gdx.scenes.scene2d.EventListener = null.asInstanceOf[com.badlogic.gdx.scenes.scene2d.EventListener]
    var listenerActor: com.badlogic.gdx.scenes.scene2d.Actor = null.asInstanceOf[com.badlogic.gdx.scenes.scene2d.Actor]
    var target: com.badlogic.gdx.scenes.scene2d.Actor = null.asInstanceOf[com.badlogic.gdx.scenes.scene2d.Actor]
    var pointer: scala.Int = 0
    var button: scala.Int = 0
    def reset(): scala.Unit = {
      this.listenerActor = null
      this.listener = null
      this.target = null
    }
  }
}