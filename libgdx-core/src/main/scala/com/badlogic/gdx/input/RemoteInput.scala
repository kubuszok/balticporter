package com.badlogic.gdx.input

class RemoteInput extends java.lang.Runnable with com.badlogic.gdx.Input {
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
  def this(port: scala.Int, listener: com.badlogic.gdx.input.RemoteInput.RemoteInputListener) = {
    this()
    this.listener = listener
    try {
      this.port = port
      this.serverSocket = new java.net.ServerSocket(port)
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
        throw new com.badlogic.gdx.utils.GdxRuntimeException(("Couldn't open listening socket at port '" + port) + "'", e)
      }
    }
  }
  def this(listener: com.badlogic.gdx.input.RemoteInput.RemoteInputListener) = {
    this(RemoteInput.DEFAULT_PORT, listener)
  }
  def this(port: scala.Int) = {
    this(port, null)
  }
  def run(): scala.Unit = {
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
  def getAccelerometerX(): scala.Float = {
    return this.accel(0)
  }
  def getAccelerometerY(): scala.Float = {
    return this.accel(1)
  }
  def getAccelerometerZ(): scala.Float = {
    return this.accel(2)
  }
  def getGyroscopeX(): scala.Float = {
    return this.gyrate(0)
  }
  def getGyroscopeY(): scala.Float = {
    return this.gyrate(1)
  }
  def getGyroscopeZ(): scala.Float = {
    return this.gyrate(2)
  }
  def getMaxPointers(): scala.Int = {
    return RemoteInput.MAX_TOUCHES
  }
  def getX(): scala.Int = {
    return this.touchX(0)
  }
  def getX(pointer: scala.Int): scala.Int = {
    return this.touchX(pointer)
  }
  def getY(): scala.Int = {
    return this.touchY(0)
  }
  def getY(pointer: scala.Int): scala.Int = {
    return this.touchY(pointer)
  }
  def isTouched(): scala.Boolean = {
    return this.isTouched$field(0)
  }
  def justTouched(): scala.Boolean = {
    return this.justTouched$field
  }
  def isTouched(pointer: scala.Int): scala.Boolean = {
    return this.isTouched$field(pointer)
  }
  def getPressure(): scala.Float = {
    return this.getPressure(0)
  }
  def getPressure(pointer: scala.Int): scala.Float = {
    return if (this.isTouched(pointer)) 1 else 0
  }
  def isButtonPressed(button: scala.Int): scala.Boolean = {
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
  def isButtonJustPressed(button: scala.Int): scala.Boolean = {
    return (button == com.badlogic.gdx.Input.Buttons.LEFT) && this.justTouched$field
  }
  def isKeyPressed(key: scala.Int): scala.Boolean = {
    if (key == com.badlogic.gdx.Input.Keys.ANY_KEY) {
      return this.keyCount > 0
    } else ()
    if ((key < 0) || (key > 255)) {
      return false
    } else ()
    return this.keys(key)
  }
  def isKeyJustPressed(key: scala.Int): scala.Boolean = {
    if (key == com.badlogic.gdx.Input.Keys.ANY_KEY) {
      return this.keyJustPressed
    } else ()
    if ((key < 0) || (key > 255)) {
      return false
    } else ()
    return this.justPressedKeys(key)
  }
  def getTextInput(listener: com.badlogic.gdx.Input.TextInputListener, title: java.lang.String, text: java.lang.String, hint: java.lang.String): scala.Unit = {
    com.badlogic.gdx.Gdx.app.getInput().getTextInput(listener, title, text, hint)
  }
  def getTextInput(listener: com.badlogic.gdx.Input.TextInputListener, title: java.lang.String, text: java.lang.String, hint: java.lang.String, `type`: com.badlogic.gdx.Input.OnscreenKeyboardType): scala.Unit = {
    com.badlogic.gdx.Gdx.app.getInput().getTextInput(listener, title, text, hint, `type`)
  }
  def setOnscreenKeyboardVisible(visible: scala.Boolean): scala.Unit = {
    ()
  }
  def setOnscreenKeyboardVisible(visible: scala.Boolean, `type`: com.badlogic.gdx.Input.OnscreenKeyboardType): scala.Unit = {
    ()
  }
  def openTextInputField(configuration: com.badlogic.gdx.input.NativeInputConfiguration): scala.Unit = {
    ()
  }
  def closeTextInputField(sendReturn: scala.Boolean): scala.Unit = {
    ()
  }
  def setKeyboardHeightObserver(observer: com.badlogic.gdx.Input.KeyboardHeightObserver): scala.Unit = {
    ()
  }
  def vibrate(milliseconds: scala.Int): scala.Unit = {
    ()
  }
  def vibrate(milliseconds: scala.Int, fallback: scala.Boolean): scala.Unit = {
    ()
  }
  def vibrate(milliseconds: scala.Int, amplitude: scala.Int, fallback: scala.Boolean): scala.Unit = {
    ()
  }
  def vibrate(vibrationType: com.badlogic.gdx.Input.VibrationType): scala.Unit = {
    ()
  }
  def getAzimuth(): scala.Float = {
    return this.compass(0)
  }
  def getPitch(): scala.Float = {
    return this.compass(1)
  }
  def getRoll(): scala.Float = {
    return this.compass(2)
  }
  def setCatchKey(keycode: scala.Int, catchKey: scala.Boolean): scala.Unit = {
    ()
  }
  def isCatchKey(keycode: scala.Int): scala.Boolean = {
    return false
  }
  def setInputProcessor(processor: com.badlogic.gdx.InputProcessor): scala.Unit = {
    this.processor = processor
  }
  def getInputProcessor(): com.badlogic.gdx.InputProcessor = {
    return this.processor
  }
  def getIPs(): scala.Array[java.lang.String] = {
    return this.ips
  }
  def isPeripheralAvailable(peripheral: com.badlogic.gdx.Input.Peripheral): scala.Boolean = {
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
  def getRotation(): scala.Int = {
    return 0
  }
  def getNativeOrientation(): com.badlogic.gdx.Input.Orientation = {
    return com.badlogic.gdx.Input.Orientation.Landscape
  }
  def setCursorCatched(catched: scala.Boolean): scala.Unit = {
    ()
  }
  def isCursorCatched(): scala.Boolean = {
    return false
  }
  def getDeltaX(): scala.Int = {
    return this.deltaX(0)
  }
  def getDeltaX(pointer: scala.Int): scala.Int = {
    return this.deltaX(pointer)
  }
  def getDeltaY(): scala.Int = {
    return this.deltaY(0)
  }
  def getDeltaY(pointer: scala.Int): scala.Int = {
    return this.deltaY(pointer)
  }
  def setCursorPosition(x: scala.Int, y: scala.Int): scala.Unit = {
    ()
  }
  def getCurrentEventTime(): scala.Long = {
    return 0
  }
  def getRotationMatrix(matrix: scala.Array[scala.Float]): scala.Unit = {
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
  class EventTrigger extends java.lang.Runnable {
    var touchEvent: com.badlogic.gdx.input.RemoteInput#TouchEvent = null.asInstanceOf[com.badlogic.gdx.input.RemoteInput#TouchEvent]
    var keyEvent: com.badlogic.gdx.input.RemoteInput#KeyEvent = null.asInstanceOf[com.badlogic.gdx.input.RemoteInput#KeyEvent]
    def this(touchEvent: com.badlogic.gdx.input.RemoteInput#TouchEvent, keyEvent: com.badlogic.gdx.input.RemoteInput#KeyEvent) = {
      this()
      this.touchEvent = touchEvent
      this.keyEvent = keyEvent
    }
    def run(): scala.Unit = {
      justTouched$field = false
      if (keyJustPressed) {
        keyJustPressed = false;
        { var i: scala.Int = 0; while (i < justPressedKeys.length) { {
          justPressedKeys(i) = false
        }; i = i + 1 } }
      } else ()
      if (processor != null) {
        if (this.touchEvent != null) {
          this.touchEvent.`type` match {
            case TouchEvent.TOUCH_DOWN => {
              deltaX(this.touchEvent.pointer) = 0
              deltaY(this.touchEvent.pointer) = 0
              processor.touchDown(this.touchEvent.x, this.touchEvent.y, this.touchEvent.pointer, com.badlogic.gdx.Input.Buttons.LEFT)
              isTouched$field(this.touchEvent.pointer) = true
              justTouched$field = true
            }
            case TouchEvent.TOUCH_UP => {
              deltaX(this.touchEvent.pointer) = 0
              deltaY(this.touchEvent.pointer) = 0
              processor.touchUp(this.touchEvent.x, this.touchEvent.y, this.touchEvent.pointer, com.badlogic.gdx.Input.Buttons.LEFT)
              isTouched$field(this.touchEvent.pointer) = false
            }
            case TouchEvent.TOUCH_DRAGGED => {
              deltaX(this.touchEvent.pointer) = this.touchEvent.x - touchX(this.touchEvent.pointer)
              deltaY(this.touchEvent.pointer) = this.touchEvent.y - touchY(this.touchEvent.pointer)
              processor.touchDragged(this.touchEvent.x, this.touchEvent.y, this.touchEvent.pointer)
            }
          }
          touchX(this.touchEvent.pointer) = this.touchEvent.x
          touchY(this.touchEvent.pointer) = this.touchEvent.y
        } else ()
        if (this.keyEvent != null) {
          this.keyEvent.`type` match {
            case KeyEvent.KEY_DOWN => {
              processor.keyDown(this.keyEvent.keyCode)
              if (!keys(this.keyEvent.keyCode)) {
                keyCount = keyCount + 1
                keys(this.keyEvent.keyCode) = true
              } else ()
              keyJustPressed = true
              justPressedKeys(this.keyEvent.keyCode) = true
            }
            case KeyEvent.KEY_UP => {
              processor.keyUp(this.keyEvent.keyCode)
              if (keys(this.keyEvent.keyCode)) {
                keyCount = keyCount - 1
                keys(this.keyEvent.keyCode) = false
              } else ()
            }
            case KeyEvent.KEY_TYPED => {
              processor.keyTyped(this.keyEvent.keyChar)
            }
          }
        } else ()
      } else {
        if (this.touchEvent != null) {
          this.touchEvent.`type` match {
            case TouchEvent.TOUCH_DOWN => {
              deltaX(this.touchEvent.pointer) = 0
              deltaY(this.touchEvent.pointer) = 0
              isTouched$field(this.touchEvent.pointer) = true
              justTouched$field = true
            }
            case TouchEvent.TOUCH_UP => {
              deltaX(this.touchEvent.pointer) = 0
              deltaY(this.touchEvent.pointer) = 0
              isTouched$field(this.touchEvent.pointer) = false
            }
            case TouchEvent.TOUCH_DRAGGED => {
              deltaX(this.touchEvent.pointer) = this.touchEvent.x - touchX(this.touchEvent.pointer)
              deltaY(this.touchEvent.pointer) = this.touchEvent.y - touchY(this.touchEvent.pointer)
            }
          }
          touchX(this.touchEvent.pointer) = this.touchEvent.x
          touchY(this.touchEvent.pointer) = this.touchEvent.y
        } else ()
        if (this.keyEvent != null) {
          if (this.keyEvent.`type` == KeyEvent.KEY_DOWN) {
            if (!keys(this.keyEvent.keyCode)) {
              keyCount = keyCount + 1
              keys(this.keyEvent.keyCode) = true
            } else ()
            keyJustPressed = true
            justPressedKeys(this.keyEvent.keyCode) = true
          } else ()
          if (this.keyEvent.`type` == KeyEvent.KEY_UP) {
            if (keys(this.keyEvent.keyCode)) {
              keyCount = keyCount - 1
              keys(this.keyEvent.keyCode) = false
            } else ()
          } else ()
        } else ()
      }
    }
  }
}
object RemoteInput {
  export com.badlogic.gdx.Input.{MAX_TOUCHES => _, DEFAULT_PORT => _, RemoteInputListener => _, *}
  private final val MAX_TOUCHES: scala.Int = 20
  var DEFAULT_PORT: scala.Int = 8190
  trait RemoteInputListener {
    def onConnected(): scala.Unit
    def onDisconnected(): scala.Unit
  }
}