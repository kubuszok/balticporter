package com.badlogic.gdx

trait Graphics {
  def isGL30Available(): scala.Boolean
  def isGL31Available(): scala.Boolean
  def isGL32Available(): scala.Boolean
  def getGL20(): com.badlogic.gdx.graphics.GL20
  def getGL30(): com.badlogic.gdx.graphics.GL30
  def getGL31(): com.badlogic.gdx.graphics.GL31
  def getGL32(): com.badlogic.gdx.graphics.GL32
  def setGL20(gl20: com.badlogic.gdx.graphics.GL20): scala.Unit
  def setGL30(gl30: com.badlogic.gdx.graphics.GL30): scala.Unit
  def setGL31(gl31: com.badlogic.gdx.graphics.GL31): scala.Unit
  def setGL32(gl32: com.badlogic.gdx.graphics.GL32): scala.Unit
  def getWidth(): scala.Int
  def getHeight(): scala.Int
  def getBackBufferWidth(): scala.Int
  def getBackBufferHeight(): scala.Int
  def getBackBufferScale(): scala.Float
  def getSafeInsetLeft(): scala.Int
  def getSafeInsetTop(): scala.Int
  def getSafeInsetBottom(): scala.Int
  def getSafeInsetRight(): scala.Int
  def getFrameId(): scala.Long
  def getDeltaTime(): scala.Float
  def getRawDeltaTime(): scala.Float
  def getFramesPerSecond(): scala.Int
  def getType(): com.badlogic.gdx.Graphics.GraphicsType
  def getGLVersion(): com.badlogic.gdx.graphics.glutils.GLVersion
  def getPpiX(): scala.Float
  def getPpiY(): scala.Float
  def getPpcX(): scala.Float
  def getPpcY(): scala.Float
  def getDensity(): scala.Float
  def supportsDisplayModeChange(): scala.Boolean
  def getPrimaryMonitor(): com.badlogic.gdx.Graphics.Monitor
  def getMonitor(): com.badlogic.gdx.Graphics.Monitor
  def getMonitors(): scala.Array[com.badlogic.gdx.Graphics.Monitor]
  def getDisplayModes(): scala.Array[com.badlogic.gdx.Graphics.DisplayMode]
  def getDisplayModes(monitor: com.badlogic.gdx.Graphics.Monitor): scala.Array[com.badlogic.gdx.Graphics.DisplayMode]
  def getDisplayMode(): com.badlogic.gdx.Graphics.DisplayMode
  def getDisplayMode(monitor: com.badlogic.gdx.Graphics.Monitor): com.badlogic.gdx.Graphics.DisplayMode
  def setFullscreenMode(displayMode: com.badlogic.gdx.Graphics.DisplayMode): scala.Boolean
  def setWindowedMode(width: scala.Int, height: scala.Int): scala.Boolean
  def setTitle(title: java.lang.String): scala.Unit
  def setUndecorated(undecorated: scala.Boolean): scala.Unit
  def setResizable(resizable: scala.Boolean): scala.Unit
  def setVSync(vsync: scala.Boolean): scala.Unit
  def setForegroundFPS(fps: scala.Int): scala.Unit
  def getBufferFormat(): com.badlogic.gdx.Graphics.BufferFormat
  def supportsExtension(`extension`: java.lang.String): scala.Boolean
  def setContinuousRendering(isContinuous: scala.Boolean): scala.Unit
  def isContinuousRendering(): scala.Boolean
  def requestRendering(): scala.Unit
  def isFullscreen(): scala.Boolean
  def newCursor(pixmap: com.badlogic.gdx.graphics.Pixmap, xHotspot: scala.Int, yHotspot: scala.Int): com.badlogic.gdx.graphics.Cursor
  def setCursor(cursor: com.badlogic.gdx.graphics.Cursor): scala.Unit
  def setSystemCursor(systemCursor: com.badlogic.gdx.graphics.Cursor.SystemCursor): scala.Unit
}
object Graphics {
  sealed abstract class GraphicsType {
    def name(): java.lang.String = this.toString()
  }
  object GraphicsType {
    case object AndroidGL extends GraphicsType
    case object LWJGL extends GraphicsType
    case object WebGL extends GraphicsType
    case object iOSGL extends GraphicsType
    case object JGLFW extends GraphicsType
    case object Mock extends GraphicsType
    case object LWJGL3 extends GraphicsType
    def values(): scala.Array[GraphicsType] = scala.Array(AndroidGL, LWJGL, WebGL, iOSGL, JGLFW, Mock, LWJGL3)
    def valueOf(name: java.lang.String): GraphicsType = name match {
      case "AndroidGL" => AndroidGL
      case "LWJGL" => LWJGL
      case "WebGL" => WebGL
      case "iOSGL" => iOSGL
      case "JGLFW" => JGLFW
      case "Mock" => Mock
      case "LWJGL3" => LWJGL3
      case _ => throw new java.lang.IllegalArgumentException(name)
    }
  }
  class DisplayMode {
    var width: scala.Int = 0
    var height: scala.Int = 0
    var refreshRate: scala.Int = 0
    var bitsPerPixel: scala.Int = 0
    def this(width: scala.Int, height: scala.Int, refreshRate: scala.Int, bitsPerPixel: scala.Int) = {
      this()
      this.width = width
      this.height = height
      this.refreshRate = refreshRate
      this.bitsPerPixel = bitsPerPixel
    }
    def toString(): java.lang.String = {
      return (((((java.lang.String.valueOf(this.width) + "x") + this.height) + ", bpp: ") + this.bitsPerPixel) + ", hz: ") + this.refreshRate
    }
  }
  class Monitor {
    var virtualX: scala.Int = 0
    var virtualY: scala.Int = 0
    var name: java.lang.String = null.asInstanceOf[java.lang.String]
    def this(virtualX: scala.Int, virtualY: scala.Int, name: java.lang.String) = {
      this()
      this.virtualX = virtualX
      this.virtualY = virtualY
      this.name = name
    }
  }
  class BufferFormat {
    var r: scala.Int = 0
    var g: scala.Int = 0
    var b: scala.Int = 0
    var a: scala.Int = 0
    var depth: scala.Int = 0
    var stencil: scala.Int = 0
    var samples: scala.Int = 0
    var coverageSampling: scala.Boolean = false
    def this(r: scala.Int, g: scala.Int, b: scala.Int, a: scala.Int, depth: scala.Int, stencil: scala.Int, samples: scala.Int, coverageSampling: scala.Boolean) = {
      this()
      this.r = r
      this.g = g
      this.b = b
      this.a = a
      this.depth = depth
      this.stencil = stencil
      this.samples = samples
      this.coverageSampling = coverageSampling
    }
    def toString(): java.lang.String = {
      return (((((((((((((("r: " + this.r) + ", g: ") + this.g) + ", b: ") + this.b) + ", a: ") + this.a) + ", depth: ") + this.depth) + ", stencil: ") + this.stencil) + ", num samples: ") + this.samples) + ", coverage sampling: ") + this.coverageSampling
    }
  }
}