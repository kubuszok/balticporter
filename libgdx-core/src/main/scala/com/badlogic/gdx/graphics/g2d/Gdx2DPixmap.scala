package com.badlogic.gdx.graphics.g2d

class Gdx2DPixmap extends com.badlogic.gdx.utils.Disposable {
  var basePtr: scala.Long = 0L
  var width: scala.Int = 0
  var height: scala.Int = 0
  var format: scala.Int = 0
  var pixelPtr: java.nio.ByteBuffer = null.asInstanceOf[java.nio.ByteBuffer]
  var nativeData: scala.Array[scala.Long] = new Array[scala.Long](4)
  def this(encodedData: scala.Array[scala.Byte], offset: scala.Int, len: scala.Int, requestedFormat: scala.Int) = {
    this()
    this.pixelPtr = Gdx2DPixmap.load(this.nativeData, encodedData, offset, len)
    if (this.pixelPtr == null) {
      throw new java.io.IOException("Error loading pixmap: " + Gdx2DPixmap.getFailureReason())
    } else ()
    this.basePtr = this.nativeData(0)
    this.width = this.nativeData(1).asInstanceOf[scala.Int]
    this.height = this.nativeData(2).asInstanceOf[scala.Int]
    this.format = this.nativeData(3).asInstanceOf[scala.Int]
    if ((requestedFormat != 0) && (requestedFormat != this.format)) {
      this.convert(requestedFormat)
    } else ()
  }
  def this(encodedData: java.nio.ByteBuffer, offset: scala.Int, len: scala.Int, requestedFormat: scala.Int) = {
    this()
    if (!encodedData.isDirect()) {
      throw new java.io.IOException("Couldn't load pixmap from non-direct ByteBuffer")
    } else ()
    this.pixelPtr = Gdx2DPixmap.loadByteBuffer(this.nativeData, encodedData, offset, len)
    if (this.pixelPtr == null) {
      throw new java.io.IOException("Error loading pixmap: " + Gdx2DPixmap.getFailureReason())
    } else ()
    this.basePtr = this.nativeData(0)
    this.width = this.nativeData(1).asInstanceOf[scala.Int]
    this.height = this.nativeData(2).asInstanceOf[scala.Int]
    this.format = this.nativeData(3).asInstanceOf[scala.Int]
    if ((requestedFormat != 0) && (requestedFormat != this.format)) {
      this.convert(requestedFormat)
    } else ()
  }
  def this(width: scala.Int, height: scala.Int, format: scala.Int) = {
    this()
    this.pixelPtr = Gdx2DPixmap.newPixmap(this.nativeData, width, height, format)
    if (this.pixelPtr == null) {
      throw new com.badlogic.gdx.utils.GdxRuntimeException((((("Unable to allocate memory for pixmap: " + width) + "x") + height) + ", ") + Gdx2DPixmap.getFormatString(format))
    } else ()
    this.basePtr = this.nativeData(0)
    this.width = this.nativeData(1).asInstanceOf[scala.Int]
    this.height = this.nativeData(2).asInstanceOf[scala.Int]
    this.format = this.nativeData(3).asInstanceOf[scala.Int]
  }
  def this(in: java.io.InputStream, requestedFormat: scala.Int) = {
    this()
    val bytes: java.io.ByteArrayOutputStream = new java.io.ByteArrayOutputStream(1024)
    var buffer: scala.Array[scala.Byte] = new Array[scala.Byte](1024)
    var readBytes: scala.Int = 0
    while ({
      readBytes = in.read(buffer)
      readBytes
    } != (-1)) {
      bytes.write(buffer, 0, readBytes)
    }
    buffer = bytes.toByteArray()
    this.pixelPtr = Gdx2DPixmap.load(this.nativeData, buffer, 0, buffer.length)
    if (this.pixelPtr == null) {
      throw new java.io.IOException("Error loading pixmap: " + Gdx2DPixmap.getFailureReason())
    } else ()
    this.basePtr = this.nativeData(0)
    this.width = this.nativeData(1).asInstanceOf[scala.Int]
    this.height = this.nativeData(2).asInstanceOf[scala.Int]
    this.format = this.nativeData(3).asInstanceOf[scala.Int]
    if ((requestedFormat != 0) && (requestedFormat != this.format)) {
      this.convert(requestedFormat)
    } else ()
  }
  def this(pixelPtr: java.nio.ByteBuffer, nativeData: scala.Array[scala.Long]) = {
    this()
    this.pixelPtr = pixelPtr
    this.basePtr = nativeData(0)
    this.width = nativeData(1).asInstanceOf[scala.Int]
    this.height = nativeData(2).asInstanceOf[scala.Int]
    this.format = nativeData(3).asInstanceOf[scala.Int]
  }
  private def convert(requestedFormat: scala.Int): scala.Unit = {
    val pixmap: Gdx2DPixmap = new Gdx2DPixmap(this.width, this.height, requestedFormat)
    pixmap.setBlend(Gdx2DPixmap.GDX2D_BLEND_NONE)
    pixmap.drawPixmap(this, 0, 0, 0, 0, this.width, this.height)
    this.dispose()
    this.basePtr = pixmap.basePtr
    this.format = pixmap.format
    this.height = pixmap.height
    this.nativeData = pixmap.nativeData
    this.pixelPtr = pixmap.pixelPtr
    this.width = pixmap.width
  }
  def dispose(): scala.Unit = {
    Gdx2DPixmap.free(this.basePtr)
  }
  def clear(color: scala.Int): scala.Unit = {
    Gdx2DPixmap.clear(this.basePtr, color)
  }
  def setPixel(x: scala.Int, y: scala.Int, color: scala.Int): scala.Unit = {
    Gdx2DPixmap.setPixel(this.basePtr, x, y, color)
  }
  def getPixel(x: scala.Int, y: scala.Int): scala.Int = {
    return Gdx2DPixmap.getPixel(this.basePtr, x, y)
  }
  def drawLine(x: scala.Int, y: scala.Int, x2: scala.Int, y2: scala.Int, color: scala.Int): scala.Unit = {
    Gdx2DPixmap.drawLine(this.basePtr, x, y, x2, y2, color)
  }
  def drawRect(x: scala.Int, y: scala.Int, width: scala.Int, height: scala.Int, color: scala.Int): scala.Unit = {
    Gdx2DPixmap.drawRect(this.basePtr, x, y, width, height, color)
  }
  def drawCircle(x: scala.Int, y: scala.Int, radius: scala.Int, color: scala.Int): scala.Unit = {
    Gdx2DPixmap.drawCircle(this.basePtr, x, y, radius, color)
  }
  def fillRect(x: scala.Int, y: scala.Int, width: scala.Int, height: scala.Int, color: scala.Int): scala.Unit = {
    Gdx2DPixmap.fillRect(this.basePtr, x, y, width, height, color)
  }
  def fillCircle(x: scala.Int, y: scala.Int, radius: scala.Int, color: scala.Int): scala.Unit = {
    Gdx2DPixmap.fillCircle(this.basePtr, x, y, radius, color)
  }
  def fillTriangle(x1: scala.Int, y1: scala.Int, x2: scala.Int, y2: scala.Int, x3: scala.Int, y3: scala.Int, color: scala.Int): scala.Unit = {
    Gdx2DPixmap.fillTriangle(this.basePtr, x1, y1, x2, y2, x3, y3, color)
  }
  def drawPixmap(src: Gdx2DPixmap, srcX: scala.Int, srcY: scala.Int, dstX: scala.Int, dstY: scala.Int, width: scala.Int, height: scala.Int): scala.Unit = {
    Gdx2DPixmap.drawPixmap(src.basePtr, this.basePtr, srcX, srcY, width, height, dstX, dstY, width, height)
  }
  def drawPixmap(src: Gdx2DPixmap, srcX: scala.Int, srcY: scala.Int, srcWidth: scala.Int, srcHeight: scala.Int, dstX: scala.Int, dstY: scala.Int, dstWidth: scala.Int, dstHeight: scala.Int): scala.Unit = {
    Gdx2DPixmap.drawPixmap(src.basePtr, this.basePtr, srcX, srcY, srcWidth, srcHeight, dstX, dstY, dstWidth, dstHeight)
  }
  def setBlend(blend: scala.Int): scala.Unit = {
    Gdx2DPixmap.setBlend(this.basePtr, blend)
  }
  def setScale(scale: scala.Int): scala.Unit = {
    Gdx2DPixmap.setScale(this.basePtr, scale)
  }
  def getPixels(): java.nio.ByteBuffer = {
    return this.pixelPtr
  }
  def getHeight(): scala.Int = {
    return this.height
  }
  def getWidth(): scala.Int = {
    return this.width
  }
  def getFormat(): scala.Int = {
    return this.format
  }
  def getGLInternalFormat(): scala.Int = {
    return Gdx2DPixmap.toGlFormat(this.format)
  }
  def getGLFormat(): scala.Int = {
    return this.getGLInternalFormat()
  }
  def getGLType(): scala.Int = {
    return Gdx2DPixmap.toGlType(this.format)
  }
  def getFormatString(): java.lang.String = {
    return Gdx2DPixmap.getFormatString(this.format)
  }
}
object Gdx2DPixmap {
  final val GDX2D_FORMAT_ALPHA: scala.Int = 1
  final val GDX2D_FORMAT_LUMINANCE_ALPHA: scala.Int = 2
  final val GDX2D_FORMAT_RGB888: scala.Int = 3
  final val GDX2D_FORMAT_RGBA8888: scala.Int = 4
  final val GDX2D_FORMAT_RGB565: scala.Int = 5
  final val GDX2D_FORMAT_RGBA4444: scala.Int = 6
  final val GDX2D_SCALE_NEAREST: scala.Int = 0
  final val GDX2D_SCALE_LINEAR: scala.Int = 1
  final val GDX2D_BLEND_NONE: scala.Int = 0
  final val GDX2D_BLEND_SRC_OVER: scala.Int = 1
  def toGlFormat(format: scala.Int): scala.Int = {
    format match {
      case Gdx2DPixmap.GDX2D_FORMAT_ALPHA => {
        return com.badlogic.gdx.graphics.GL20.GL_ALPHA
      }
      case Gdx2DPixmap.GDX2D_FORMAT_LUMINANCE_ALPHA => {
        return com.badlogic.gdx.graphics.GL20.GL_LUMINANCE_ALPHA
      }
      case Gdx2DPixmap.GDX2D_FORMAT_RGB888 | Gdx2DPixmap.GDX2D_FORMAT_RGB565 => {
        return com.badlogic.gdx.graphics.GL20.GL_RGB
      }
      case Gdx2DPixmap.GDX2D_FORMAT_RGBA8888 | Gdx2DPixmap.GDX2D_FORMAT_RGBA4444 => {
        return com.badlogic.gdx.graphics.GL20.GL_RGBA
      }
      case _ => {
        throw new com.badlogic.gdx.utils.GdxRuntimeException("unknown format: " + format)
      }
    }
  }
  def toGlType(format: scala.Int): scala.Int = {
    format match {
      case Gdx2DPixmap.GDX2D_FORMAT_ALPHA | Gdx2DPixmap.GDX2D_FORMAT_LUMINANCE_ALPHA | Gdx2DPixmap.GDX2D_FORMAT_RGB888 | Gdx2DPixmap.GDX2D_FORMAT_RGBA8888 => {
        return com.badlogic.gdx.graphics.GL20.GL_UNSIGNED_BYTE
      }
      case Gdx2DPixmap.GDX2D_FORMAT_RGB565 => {
        return com.badlogic.gdx.graphics.GL20.GL_UNSIGNED_SHORT_5_6_5
      }
      case Gdx2DPixmap.GDX2D_FORMAT_RGBA4444 => {
        return com.badlogic.gdx.graphics.GL20.GL_UNSIGNED_SHORT_4_4_4_4
      }
      case _ => {
        throw new com.badlogic.gdx.utils.GdxRuntimeException("unknown format: " + format)
      }
    }
  }
  def newPixmap(in: java.io.InputStream, requestedFormat: scala.Int): Gdx2DPixmap = {
    try {
      return new Gdx2DPixmap(in, requestedFormat)
    } catch {
      case e: java.io.IOException => {
        return null
      }
    }
  }
  def newPixmap(width: scala.Int, height: scala.Int, format: scala.Int): Gdx2DPixmap = {
    try {
      return new Gdx2DPixmap(width, height, format)
    } catch {
      case e: java.lang.IllegalArgumentException => {
        return null
      }
    }
  }
  private def getFormatString(format: scala.Int): java.lang.String = {
    format match {
      case Gdx2DPixmap.GDX2D_FORMAT_ALPHA => {
        return "alpha"
      }
      case Gdx2DPixmap.GDX2D_FORMAT_LUMINANCE_ALPHA => {
        return "luminance alpha"
      }
      case Gdx2DPixmap.GDX2D_FORMAT_RGB888 => {
        return "rgb888"
      }
      case Gdx2DPixmap.GDX2D_FORMAT_RGBA8888 => {
        return "rgba8888"
      }
      case Gdx2DPixmap.GDX2D_FORMAT_RGB565 => {
        return "rgb565"
      }
      case Gdx2DPixmap.GDX2D_FORMAT_RGBA4444 => {
        return "rgba4444"
      }
      case _ => {
        return "unknown"
      }
    }
  }
  private def load(nativeData: scala.Array[scala.Long], buffer: scala.Array[scala.Byte], offset: scala.Int, len: scala.Int): java.nio.ByteBuffer
  private def loadByteBuffer(nativeData: scala.Array[scala.Long], buffer: java.nio.ByteBuffer, offset: scala.Int, len: scala.Int): java.nio.ByteBuffer
  private def newPixmap(nativeData: scala.Array[scala.Long], width: scala.Int, height: scala.Int, format: scala.Int): java.nio.ByteBuffer
  private def free(pixmap: scala.Long): scala.Unit
  private def clear(pixmap: scala.Long, color: scala.Int): scala.Unit
  private def setPixel(pixmap: scala.Long, x: scala.Int, y: scala.Int, color: scala.Int): scala.Unit
  private def getPixel(pixmap: scala.Long, x: scala.Int, y: scala.Int): scala.Int
  private def drawLine(pixmap: scala.Long, x: scala.Int, y: scala.Int, x2: scala.Int, y2: scala.Int, color: scala.Int): scala.Unit
  private def drawRect(pixmap: scala.Long, x: scala.Int, y: scala.Int, width: scala.Int, height: scala.Int, color: scala.Int): scala.Unit
  private def drawCircle(pixmap: scala.Long, x: scala.Int, y: scala.Int, radius: scala.Int, color: scala.Int): scala.Unit
  private def fillRect(pixmap: scala.Long, x: scala.Int, y: scala.Int, width: scala.Int, height: scala.Int, color: scala.Int): scala.Unit
  private def fillCircle(pixmap: scala.Long, x: scala.Int, y: scala.Int, radius: scala.Int, color: scala.Int): scala.Unit
  private def fillTriangle(pixmap: scala.Long, x1: scala.Int, y1: scala.Int, x2: scala.Int, y2: scala.Int, x3: scala.Int, y3: scala.Int, color: scala.Int): scala.Unit
  private def drawPixmap(src: scala.Long, dst: scala.Long, srcX: scala.Int, srcY: scala.Int, srcWidth: scala.Int, srcHeight: scala.Int, dstX: scala.Int, dstY: scala.Int, dstWidth: scala.Int, dstHeight: scala.Int): scala.Unit
  private def setBlend(src: scala.Long, blend: scala.Int): scala.Unit
  private def setScale(src: scala.Long, scale: scala.Int): scala.Unit
  def getFailureReason(): java.lang.String
}