package com.badlogic.gdx.input

class RemoteSender extends com.badlogic.gdx.InputProcessor {
  private var out: java.io.DataOutputStream = null.asInstanceOf[java.io.DataOutputStream]
  private var connected: scala.Boolean = false
  def this(ip: java.lang.String, port: scala.Int) = {
    this()
    try {
      val socket: java.net.Socket = new java.net.Socket(ip, port)
      socket.setTcpNoDelay(true)
      socket.setSoTimeout(3000)
      this.out = new java.io.DataOutputStream(socket.getOutputStream())
      this.out.writeBoolean(com.badlogic.gdx.Gdx.input.isPeripheralAvailable(com.badlogic.gdx.Input.Peripheral.MultitouchScreen))
      this.connected = true
      com.badlogic.gdx.Gdx.input.setInputProcessor(this)
    } catch {
      case e: java.lang.Exception => {
        com.badlogic.gdx.Gdx.app.log("RemoteSender", (("couldn't connect to " + ip) + ":") + port)
      }
    }
  }
  def sendUpdate(): scala.Unit = {
    this.synchronized {
      if (!this.connected) {
        return
      } else ()
    }
    try {
      this.out.writeInt(RemoteSender.ACCEL)
      this.out.writeFloat(com.badlogic.gdx.Gdx.input.getAccelerometerX())
      this.out.writeFloat(com.badlogic.gdx.Gdx.input.getAccelerometerY())
      this.out.writeFloat(com.badlogic.gdx.Gdx.input.getAccelerometerZ())
      this.out.writeInt(RemoteSender.COMPASS)
      this.out.writeFloat(com.badlogic.gdx.Gdx.input.getAzimuth())
      this.out.writeFloat(com.badlogic.gdx.Gdx.input.getPitch())
      this.out.writeFloat(com.badlogic.gdx.Gdx.input.getRoll())
      this.out.writeInt(RemoteSender.SIZE)
      this.out.writeFloat(com.badlogic.gdx.Gdx.graphics.getWidth())
      this.out.writeFloat(com.badlogic.gdx.Gdx.graphics.getHeight())
      this.out.writeInt(RemoteSender.GYRO)
      this.out.writeFloat(com.badlogic.gdx.Gdx.input.getGyroscopeX())
      this.out.writeFloat(com.badlogic.gdx.Gdx.input.getGyroscopeY())
      this.out.writeFloat(com.badlogic.gdx.Gdx.input.getGyroscopeZ())
    } catch {
      case t: java.lang.Throwable => {
        this.out = null
        this.connected = false
      }
    }
  }
  def keyDown(keycode: scala.Int): scala.Boolean = {
    this.synchronized {
      if (!this.connected) {
        return false
      } else ()
    }
    try {
      this.out.writeInt(RemoteSender.KEY_DOWN)
      this.out.writeInt(keycode)
    } catch {
      case t: java.lang.Throwable => {
        this.synchronized {
          this.connected = false
        }
      }
    }
    return false
  }
  def keyUp(keycode: scala.Int): scala.Boolean = {
    this.synchronized {
      if (!this.connected) {
        return false
      } else ()
    }
    try {
      this.out.writeInt(RemoteSender.KEY_UP)
      this.out.writeInt(keycode)
    } catch {
      case t: java.lang.Throwable => {
        this.synchronized {
          this.connected = false
        }
      }
    }
    return false
  }
  def keyTyped(character: scala.Char): scala.Boolean = {
    this.synchronized {
      if (!this.connected) {
        return false
      } else ()
    }
    try {
      this.out.writeInt(RemoteSender.KEY_TYPED)
      this.out.writeChar(character)
    } catch {
      case t: java.lang.Throwable => {
        this.synchronized {
          this.connected = false
        }
      }
    }
    return false
  }
  def touchDown(x: scala.Int, y: scala.Int, pointer: scala.Int, button: scala.Int): scala.Boolean = {
    this.synchronized {
      if (!this.connected) {
        return false
      } else ()
    }
    try {
      this.out.writeInt(RemoteSender.TOUCH_DOWN)
      this.out.writeInt(x)
      this.out.writeInt(y)
      this.out.writeInt(pointer)
    } catch {
      case t: java.lang.Throwable => {
        this.synchronized {
          this.connected = false
        }
      }
    }
    return false
  }
  def touchUp(x: scala.Int, y: scala.Int, pointer: scala.Int, button: scala.Int): scala.Boolean = {
    this.synchronized {
      if (!this.connected) {
        return false
      } else ()
    }
    try {
      this.out.writeInt(RemoteSender.TOUCH_UP)
      this.out.writeInt(x)
      this.out.writeInt(y)
      this.out.writeInt(pointer)
    } catch {
      case t: java.lang.Throwable => {
        this.synchronized {
          this.connected = false
        }
      }
    }
    return false
  }
  def touchCancelled(screenX: scala.Int, screenY: scala.Int, pointer: scala.Int, button: scala.Int): scala.Boolean = {
    return this.touchUp(screenX, screenY, pointer, button)
  }
  def touchDragged(x: scala.Int, y: scala.Int, pointer: scala.Int): scala.Boolean = {
    this.synchronized {
      if (!this.connected) {
        return false
      } else ()
    }
    try {
      this.out.writeInt(RemoteSender.TOUCH_DRAGGED)
      this.out.writeInt(x)
      this.out.writeInt(y)
      this.out.writeInt(pointer)
    } catch {
      case t: java.lang.Throwable => {
        this.synchronized {
          this.connected = false
        }
      }
    }
    return false
  }
  def mouseMoved(x: scala.Int, y: scala.Int): scala.Boolean = {
    return false
  }
  def scrolled(amountX: scala.Float, amountY: scala.Float): scala.Boolean = {
    return false
  }
  def isConnected(): scala.Boolean = {
    this.synchronized {
      return this.connected
    }
  }
}
object RemoteSender {
  final val KEY_DOWN: scala.Int = 0
  final val KEY_UP: scala.Int = 1
  final val KEY_TYPED: scala.Int = 2
  final val TOUCH_DOWN: scala.Int = 3
  final val TOUCH_UP: scala.Int = 4
  final val TOUCH_DRAGGED: scala.Int = 5
  final val ACCEL: scala.Int = 6
  final val COMPASS: scala.Int = 7
  final val SIZE: scala.Int = 8
  final val GYRO: scala.Int = 9
}