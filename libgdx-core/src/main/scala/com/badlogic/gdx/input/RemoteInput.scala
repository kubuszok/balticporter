package com.badlogic.gdx.input

class RemoteInput(port$p: scala.Int, listener$p: com.badlogic.gdx.input.RemoteInput.RemoteInputListener) extends java.lang.Runnable with com.badlogic.gdx.Input {
  private var serverSocket: java.net.ServerSocket = null.asInstanceOf[java.net.ServerSocket]
  private var accel: scala.Array[scala.Float] = new scala.Array[scala.Float](3)
  private var gyrate: scala.Array[scala.Float] = new scala.Array[scala.Float](3)
  private var compass: scala.Array[scala.Float] = new scala.Array[scala.Float](3)
  private var multiTouch: scala.Boolean = false
  private var remoteWidth: scala.Float = 0
  private var remoteHeight: scala.Float = 0
  private var connected: scala.Boolean = false
  private var listener: com.badlogic.gdx.input.RemoteInput.RemoteInputListener = null.asInstanceOf[com.badlogic.gdx.input.RemoteInput.RemoteInputListener]
  var keyCount: scala.Int = 0
  var keys: scala.Array[scala.Boolean] = new scala.Array[scala.Boolean](256)
  var keyJustPressed: scala.Boolean = false
  var justPressedKeys: scala.Array[scala.Boolean] = new scala.Array[scala.Boolean](256)
  var deltaX: scala.Array[scala.Int] = new scala.Array[scala.Int](RemoteInput.MAX_TOUCHES)
  var deltaY: scala.Array[scala.Int] = new scala.Array[scala.Int](RemoteInput.MAX_TOUCHES)
  var touchX: scala.Array[scala.Int] = new scala.Array[scala.Int](RemoteInput.MAX_TOUCHES)
  var touchY: scala.Array[scala.Int] = new scala.Array[scala.Int](RemoteInput.MAX_TOUCHES)
  var isTouched$field: scala.Array[scala.Boolean] = new scala.Array[scala.Boolean](RemoteInput.MAX_TOUCHES)
  var justTouched$field: scala.Boolean = false
  var processor: com.badlogic.gdx.InputProcessor = null
  private var port: scala.Int = 0
  var ips: scala.Array[java.lang.String] = null.asInstanceOf[scala.Array[java.lang.String]]
  def this(port: scala.Int) = {
    this(port, null)
  }
  def this() = {
    this(RemoteInput.DEFAULT_PORT)
  }
  def this(listener: com.badlogic.gdx.input.RemoteInput.RemoteInputListener) = {
    this(RemoteInput.DEFAULT_PORT, listener)
  }
  this.listener = listener$p
  try {
    this.port = port$p
    this.serverSocket = new java.net.ServerSocket(port$p)
    val thread: java.lang.Thread = new java.lang.Thread(this)
    thread.setDaemon(true)
    thread.start()
    val allByName: scala.Array[java.net.InetAddress] = java.net.InetAddress.getAllByName(java.net.InetAddress.getLocalHost().getHostName())
    this.ips = new scala.Array[java.lang.String](allByName.length);
    { var i: scala.Int = 0; while (i < allByName.length) { {
      this.ips(i) = allByName(i).getHostAddress()
    }; i = i + 1 } }
  } catch {
    case e: java.lang.Exception => {
      throw new com.badlogic.gdx.utils.GdxRuntimeException(("Couldn't open listening socket at port '" + port$p) + "'", e)
    }
  }
  @java.lang.Override
  override def run(): scala.Unit = {
    while (true) {
      try {
        this.connected = false
        if (this.listener != null) {
          this.listener.onDisconnected()
        } else ()
        java.lang.System.out.println("listening, port " + this.port)
        var socket: java.net.Socket = null
        socket = this.serverSocket.accept()
        socket.setTcpNoDelay(true)
        socket.setSoTimeout(3000)
        this.connected = true
        if (this.listener != null) {
          this.listener.onConnected()
        } else ()
        val in: java.io.DataInputStream = new java.io.DataInputStream(socket.getInputStream())
        this.multiTouch = in.readBoolean()
        while (true) {
          val event: scala.Int = in.readInt()
          var keyEvent: com.badlogic.gdx.input.RemoteInput#KeyEvent = null
          var touchEvent: com.badlogic.gdx.input.RemoteInput#TouchEvent = null
          event match {
            case com.badlogic.gdx.input.RemoteSender.ACCEL => {
              this.accel(0) = in.readFloat()
              this.accel(1) = in.readFloat()
              this.accel(2) = in.readFloat()
            }
            case com.badlogic.gdx.input.RemoteSender.COMPASS => {
              this.compass(0) = in.readFloat()
              this.compass(1) = in.readFloat()
              this.compass(2) = in.readFloat()
            }
            case com.badlogic.gdx.input.RemoteSender.SIZE => {
              this.remoteWidth = in.readFloat()
              this.remoteHeight = in.readFloat()
            }
            case com.badlogic.gdx.input.RemoteSender.GYRO => {
              this.gyrate(0) = in.readFloat()
              this.gyrate(1) = in.readFloat()
              this.gyrate(2) = in.readFloat()
            }
            case com.badlogic.gdx.input.RemoteSender.KEY_DOWN => {
              keyEvent = new KeyEvent()
              keyEvent.keyCode = in.readInt()
              keyEvent.`type` = KeyEvent.KEY_DOWN
            }
            case com.badlogic.gdx.input.RemoteSender.KEY_UP => {
              keyEvent = new KeyEvent()
              keyEvent.keyCode = in.readInt()
              keyEvent.`type` = KeyEvent.KEY_UP
            }
            case com.badlogic.gdx.input.RemoteSender.KEY_TYPED => {
              keyEvent = new KeyEvent()
              keyEvent.keyChar = in.readChar()
              keyEvent.`type` = KeyEvent.KEY_TYPED
            }
            case com.badlogic.gdx.input.RemoteSender.TOUCH_DOWN => {
              touchEvent = new TouchEvent()
              touchEvent.x = ((in.readInt() / this.remoteWidth) * com.badlogic.gdx.Gdx.graphics.getWidth()).asInstanceOf[scala.Int].asInstanceOf[scala.Int]
              touchEvent.y = ((in.readInt() / this.remoteHeight) * com.badlogic.gdx.Gdx.graphics.getHeight()).asInstanceOf[scala.Int].asInstanceOf[scala.Int]
              touchEvent.pointer = in.readInt()
              touchEvent.`type` = TouchEvent.TOUCH_DOWN
            }
            case com.badlogic.gdx.input.RemoteSender.TOUCH_UP => {
              touchEvent = new TouchEvent()
              touchEvent.x = ((in.readInt() / this.remoteWidth) * com.badlogic.gdx.Gdx.graphics.getWidth()).asInstanceOf[scala.Int].asInstanceOf[scala.Int]
              touchEvent.y = ((in.readInt() / this.remoteHeight) * com.badlogic.gdx.Gdx.graphics.getHeight()).asInstanceOf[scala.Int].asInstanceOf[scala.Int]
              touchEvent.pointer = in.readInt()
              touchEvent.`type` = TouchEvent.TOUCH_UP
            }
            case com.badlogic.gdx.input.RemoteSender.TOUCH_DRAGGED => {
              touchEvent = new TouchEvent()
              touchEvent.x = ((in.readInt() / this.remoteWidth) * com.badlogic.gdx.Gdx.graphics.getWidth()).asInstanceOf[scala.Int].asInstanceOf[scala.Int]
              touchEvent.y = ((in.readInt() / this.remoteHeight) * com.badlogic.gdx.Gdx.graphics.getHeight()).asInstanceOf[scala.Int].asInstanceOf[scala.Int]
              touchEvent.pointer = in.readInt()
              touchEvent.`type` = TouchEvent.TOUCH_DRAGGED
            }
          }
          com.badlogic.gdx.Gdx.app.postRunnable(new EventTrigger(touchEvent, keyEvent))
        }
      } catch {
        case e: java.io.IOException => {
          e.printStackTrace()
        }
      }
    }
  }
  def isConnected(): scala.Boolean = {
    return this.connected
  }
  @java.lang.Override
  override def getAccelerometerX(): scala.Float = {
    return this.accel(0)
  }
  @java.lang.Override
  override def getAccelerometerY(): scala.Float = {
    return this.accel(1)
  }
  @java.lang.Override
  override def getAccelerometerZ(): scala.Float = {
    return this.accel(2)
  }
  @java.lang.Override
  override def getGyroscopeX(): scala.Float = {
    return this.gyrate(0)
  }
  @java.lang.Override
  override def getGyroscopeY(): scala.Float = {
    return this.gyrate(1)
  }
  @java.lang.Override
  override def getGyroscopeZ(): scala.Float = {
    return this.gyrate(2)
  }
  @java.lang.Override
  override def getMaxPointers(): scala.Int = {
    return RemoteInput.MAX_TOUCHES
  }
  @java.lang.Override
  override def getX(): scala.Int = {
    return this.touchX(0)
  }
  @java.lang.Override
  override def getX(pointer: scala.Int): scala.Int = {
    return this.touchX(pointer)
  }
  @java.lang.Override
  override def getY(): scala.Int = {
    return this.touchY(0)
  }
  @java.lang.Override
  override def getY(pointer: scala.Int): scala.Int = {
    return this.touchY(pointer)
  }
  @java.lang.Override
  override def isTouched(): scala.Boolean = {
    return this.isTouched$field(0)
  }
  @java.lang.Override
  override def justTouched(): scala.Boolean = {
    return this.justTouched$field
  }
  @java.lang.Override
  override def isTouched(pointer: scala.Int): scala.Boolean = {
    return this.isTouched$field(pointer)
  }
  @java.lang.Override
  override def getPressure(): scala.Float = {
    return this.getPressure(0)
  }
  @java.lang.Override
  override def getPressure(pointer: scala.Int): scala.Float = {
    return if (this.isTouched(pointer)) 1 else 0
  }
  @java.lang.Override
  override def isButtonPressed(button: scala.Int): scala.Boolean = {
    if (button != com.badlogic.gdx.Input.Buttons.LEFT) {
      return false
    } else ();
    { var i: scala.Int = 0; while (i < this.isTouched$field.length) { {
      if (this.isTouched$field(i)) {
        return true
      } else ()
    }; i = i + 1 } }
    return false
  }
  @java.lang.Override
  override def isButtonJustPressed(button: scala.Int): scala.Boolean = {
    return (button == com.badlogic.gdx.Input.Buttons.LEFT) && this.justTouched$field
  }
  @java.lang.Override
  override def isKeyPressed(key: scala.Int): scala.Boolean = {
    if (key == com.badlogic.gdx.Input.Keys.ANY_KEY) {
      return this.keyCount > 0
    } else ()
    if ((key < 0) || (key > 255)) {
      return false
    } else ()
    return this.keys(key)
  }
  @java.lang.Override
  override def isKeyJustPressed(key: scala.Int): scala.Boolean = {
    if (key == com.badlogic.gdx.Input.Keys.ANY_KEY) {
      return this.keyJustPressed
    } else ()
    if ((key < 0) || (key > 255)) {
      return false
    } else ()
    return this.justPressedKeys(key)
  }
  @java.lang.Override
  override def getTextInput(listener: com.badlogic.gdx.Input.TextInputListener, title: java.lang.String, text: java.lang.String, hint: java.lang.String): scala.Unit = {
    com.badlogic.gdx.Gdx.app.getInput().getTextInput(listener, title, text, hint)
  }
  @java.lang.Override
  override def getTextInput(listener: com.badlogic.gdx.Input.TextInputListener, title: java.lang.String, text: java.lang.String, hint: java.lang.String, `type`: com.badlogic.gdx.Input.OnscreenKeyboardType): scala.Unit = {
    com.badlogic.gdx.Gdx.app.getInput().getTextInput(listener, title, text, hint, `type`)
  }
  @java.lang.Override
  override def setOnscreenKeyboardVisible(visible: scala.Boolean): scala.Unit = {
    ()
  }
  @java.lang.Override
  override def setOnscreenKeyboardVisible(visible: scala.Boolean, `type`: com.badlogic.gdx.Input.OnscreenKeyboardType): scala.Unit = {
    ()
  }
  @java.lang.Override
  override def openTextInputField(configuration: com.badlogic.gdx.input.NativeInputConfiguration): scala.Unit = {
    ()
  }
  @java.lang.Override
  override def closeTextInputField(sendReturn: scala.Boolean): scala.Unit = {
    ()
  }
  @java.lang.Override
  override def setKeyboardHeightObserver(observer: com.badlogic.gdx.Input.KeyboardHeightObserver): scala.Unit = {
    ()
  }
  @java.lang.Override
  override def vibrate(milliseconds: scala.Int): scala.Unit = {
    ()
  }
  @java.lang.Override
  override def vibrate(milliseconds: scala.Int, fallback: scala.Boolean): scala.Unit = {
    ()
  }
  @java.lang.Override
  override def vibrate(milliseconds: scala.Int, amplitude: scala.Int, fallback: scala.Boolean): scala.Unit = {
    ()
  }
  @java.lang.Override
  override def vibrate(vibrationType: com.badlogic.gdx.Input.VibrationType): scala.Unit = {
    ()
  }
  @java.lang.Override
  override def getAzimuth(): scala.Float = {
    return this.compass(0)
  }
  @java.lang.Override
  override def getPitch(): scala.Float = {
    return this.compass(1)
  }
  @java.lang.Override
  override def getRoll(): scala.Float = {
    return this.compass(2)
  }
  @java.lang.Override
  override def setCatchKey(keycode: scala.Int, catchKey: scala.Boolean): scala.Unit = {
    ()
  }
  @java.lang.Override
  override def isCatchKey(keycode: scala.Int): scala.Boolean = {
    return false
  }
  @java.lang.Override
  override def setInputProcessor(processor: com.badlogic.gdx.InputProcessor): scala.Unit = {
    this.processor = processor
  }
  @java.lang.Override
  override def getInputProcessor(): com.badlogic.gdx.InputProcessor = {
    return this.processor
  }
  def getIPs(): scala.Array[java.lang.String] = {
    return this.ips
  }
  @java.lang.Override
  override def isPeripheralAvailable(peripheral: com.badlogic.gdx.Input.Peripheral): scala.Boolean = {
    if (peripheral == com.badlogic.gdx.Input.Peripheral.Accelerometer) {
      return true
    } else ()
    if (peripheral == com.badlogic.gdx.Input.Peripheral.Compass) {
      return true
    } else ()
    if (peripheral == com.badlogic.gdx.Input.Peripheral.MultitouchScreen) {
      return this.multiTouch
    } else ()
    return false
  }
  @java.lang.Override
  override def getRotation(): scala.Int = {
    return 0
  }
  @java.lang.Override
  override def getNativeOrientation(): com.badlogic.gdx.Input.Orientation = {
    return com.badlogic.gdx.Input.Orientation.Landscape
  }
  @java.lang.Override
  override def setCursorCatched(catched: scala.Boolean): scala.Unit = {
    ()
  }
  @java.lang.Override
  override def isCursorCatched(): scala.Boolean = {
    return false
  }
  @java.lang.Override
  override def getDeltaX(): scala.Int = {
    return this.deltaX(0)
  }
  @java.lang.Override
  override def getDeltaX(pointer: scala.Int): scala.Int = {
    return this.deltaX(pointer)
  }
  @java.lang.Override
  override def getDeltaY(): scala.Int = {
    return this.deltaY(0)
  }
  @java.lang.Override
  override def getDeltaY(pointer: scala.Int): scala.Int = {
    return this.deltaY(pointer)
  }
  @java.lang.Override
  override def setCursorPosition(x: scala.Int, y: scala.Int): scala.Unit = {
    ()
  }
  @java.lang.Override
  override def getCurrentEventTime(): scala.Long = {
    return 0
  }
  @java.lang.Override
  override def getRotationMatrix(matrix: scala.Array[scala.Float]): scala.Unit = {
    ()
  }
  class KeyEvent {
    var timeStamp: scala.Long = 0L
    var `type`: scala.Int = 0
    var keyCode: scala.Int = 0
    var keyChar: scala.Char = '\u0000'
  }
  object KeyEvent {
    final val KEY_DOWN: scala.Int = 0
    final val KEY_UP: scala.Int = 1
    final val KEY_TYPED: scala.Int = 2
  }
  class TouchEvent {
    var timeStamp: scala.Long = 0L
    var `type`: scala.Int = 0
    var x: scala.Int = 0
    var y: scala.Int = 0
    var pointer: scala.Int = 0
  }
  object TouchEvent {
    final val TOUCH_DOWN: scala.Int = 0
    final val TOUCH_UP: scala.Int = 1
    final val TOUCH_DRAGGED: scala.Int = 2
  }
  class EventTrigger(touchEvent$p: com.badlogic.gdx.input.RemoteInput#TouchEvent, keyEvent$p: com.badlogic.gdx.input.RemoteInput#KeyEvent) extends java.lang.Runnable {
    var touchEvent: com.badlogic.gdx.input.RemoteInput#TouchEvent = null.asInstanceOf[com.badlogic.gdx.input.RemoteInput#TouchEvent]
    var keyEvent: com.badlogic.gdx.input.RemoteInput#KeyEvent = null.asInstanceOf[com.badlogic.gdx.input.RemoteInput#KeyEvent]
    this.touchEvent = touchEvent$p
    this.keyEvent = keyEvent$p
    @java.lang.Override
    override def run(): scala.Unit = {
      RemoteInput.this.justTouched$field = false
      if (RemoteInput.this.keyJustPressed) {
        RemoteInput.this.keyJustPressed = false;
        { var i: scala.Int = 0; while (i < RemoteInput.this.justPressedKeys.length) { {
          RemoteInput.this.justPressedKeys(i) = false
        }; i = i + 1 } }
      } else ()
      if (RemoteInput.this.processor != null) {
        if (this.touchEvent != null) {
          this.touchEvent.`type` match {
            case TouchEvent.TOUCH_DOWN => {
              RemoteInput.this.deltaX(this.touchEvent.pointer) = 0
              RemoteInput.this.deltaY(this.touchEvent.pointer) = 0
              RemoteInput.this.processor.touchDown(this.touchEvent.x, this.touchEvent.y, this.touchEvent.pointer, com.badlogic.gdx.Input.Buttons.LEFT)
              RemoteInput.this.isTouched$field(this.touchEvent.pointer) = true
              RemoteInput.this.justTouched$field = true
            }
            case TouchEvent.TOUCH_UP => {
              RemoteInput.this.deltaX(this.touchEvent.pointer) = 0
              RemoteInput.this.deltaY(this.touchEvent.pointer) = 0
              RemoteInput.this.processor.touchUp(this.touchEvent.x, this.touchEvent.y, this.touchEvent.pointer, com.badlogic.gdx.Input.Buttons.LEFT)
              RemoteInput.this.isTouched$field(this.touchEvent.pointer) = false
            }
            case TouchEvent.TOUCH_DRAGGED => {
              RemoteInput.this.deltaX(this.touchEvent.pointer) = this.touchEvent.x - RemoteInput.this.touchX(this.touchEvent.pointer)
              RemoteInput.this.deltaY(this.touchEvent.pointer) = this.touchEvent.y - RemoteInput.this.touchY(this.touchEvent.pointer)
              RemoteInput.this.processor.touchDragged(this.touchEvent.x, this.touchEvent.y, this.touchEvent.pointer)
            }
          }
          RemoteInput.this.touchX(this.touchEvent.pointer) = this.touchEvent.x
          RemoteInput.this.touchY(this.touchEvent.pointer) = this.touchEvent.y
        } else ()
        if (this.keyEvent != null) {
          this.keyEvent.`type` match {
            case KeyEvent.KEY_DOWN => {
              RemoteInput.this.processor.keyDown(this.keyEvent.keyCode)
              if (!RemoteInput.this.keys(this.keyEvent.keyCode)) {
                RemoteInput.this.keyCount = RemoteInput.this.keyCount + 1
                RemoteInput.this.keys(this.keyEvent.keyCode) = true
              } else ()
              RemoteInput.this.keyJustPressed = true
              RemoteInput.this.justPressedKeys(this.keyEvent.keyCode) = true
            }
            case KeyEvent.KEY_UP => {
              RemoteInput.this.processor.keyUp(this.keyEvent.keyCode)
              if (RemoteInput.this.keys(this.keyEvent.keyCode)) {
                RemoteInput.this.keyCount = RemoteInput.this.keyCount - 1
                RemoteInput.this.keys(this.keyEvent.keyCode) = false
              } else ()
            }
            case KeyEvent.KEY_TYPED => {
              RemoteInput.this.processor.keyTyped(this.keyEvent.keyChar)
            }
          }
        } else ()
      } else {
        if (this.touchEvent != null) {
          this.touchEvent.`type` match {
            case TouchEvent.TOUCH_DOWN => {
              RemoteInput.this.deltaX(this.touchEvent.pointer) = 0
              RemoteInput.this.deltaY(this.touchEvent.pointer) = 0
              RemoteInput.this.isTouched$field(this.touchEvent.pointer) = true
              RemoteInput.this.justTouched$field = true
            }
            case TouchEvent.TOUCH_UP => {
              RemoteInput.this.deltaX(this.touchEvent.pointer) = 0
              RemoteInput.this.deltaY(this.touchEvent.pointer) = 0
              RemoteInput.this.isTouched$field(this.touchEvent.pointer) = false
            }
            case TouchEvent.TOUCH_DRAGGED => {
              RemoteInput.this.deltaX(this.touchEvent.pointer) = this.touchEvent.x - RemoteInput.this.touchX(this.touchEvent.pointer)
              RemoteInput.this.deltaY(this.touchEvent.pointer) = this.touchEvent.y - RemoteInput.this.touchY(this.touchEvent.pointer)
            }
          }
          RemoteInput.this.touchX(this.touchEvent.pointer) = this.touchEvent.x
          RemoteInput.this.touchY(this.touchEvent.pointer) = this.touchEvent.y
        } else ()
        if (this.keyEvent != null) {
          if (this.keyEvent.`type` == KeyEvent.KEY_DOWN) {
            if (!RemoteInput.this.keys(this.keyEvent.keyCode)) {
              RemoteInput.this.keyCount = RemoteInput.this.keyCount + 1
              RemoteInput.this.keys(this.keyEvent.keyCode) = true
            } else ()
            RemoteInput.this.keyJustPressed = true
            RemoteInput.this.justPressedKeys(this.keyEvent.keyCode) = true
          } else ()
          if (this.keyEvent.`type` == KeyEvent.KEY_UP) {
            if (RemoteInput.this.keys(this.keyEvent.keyCode)) {
              RemoteInput.this.keyCount = RemoteInput.this.keyCount - 1
              RemoteInput.this.keys(this.keyEvent.keyCode) = false
            } else ()
          } else ()
        } else ()
      }
    }
  }
}
object RemoteInput {
  export com.badlogic.gdx.Input.{DEFAULT_PORT => _, MAX_TOUCHES => _, RemoteInputListener => _, *}
  private final val MAX_TOUCHES: scala.Int = 20
  var DEFAULT_PORT: scala.Int = 8190
  trait RemoteInputListener {
    def onConnected(): scala.Unit
    def onDisconnected(): scala.Unit
  }
}