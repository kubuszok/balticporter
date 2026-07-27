package com.badlogic.gdx.graphics

class Pixmap extends com.badlogic.gdx.utils.Disposable {
  private var blending: com.badlogic.gdx.graphics.Pixmap.Blending = com.badlogic.gdx.graphics.Pixmap.Blending.SourceOver
  private var filter: com.badlogic.gdx.graphics.Pixmap.Filter = com.badlogic.gdx.graphics.Pixmap.Filter.BiLinear
  var pixmap: com.badlogic.gdx.graphics.g2d.Gdx2DPixmap = null.asInstanceOf[com.badlogic.gdx.graphics.g2d.Gdx2DPixmap]
  var color: scala.Int = 0
  private var disposed: scala.Boolean = false
  def this(width: scala.Int, height: scala.Int, format: com.badlogic.gdx.graphics.Pixmap.Format) = {
    this()
    this.pixmap = new com.badlogic.gdx.graphics.g2d.Gdx2DPixmap(width, height, com.badlogic.gdx.graphics.Pixmap.Format.toGdx2DPixmapFormat(format))
    this.setColor(0, 0, 0, 0)
    this.fill()
  }
  def this(encodedData: scala.Array[scala.Byte], offset: scala.Int, len: scala.Int) = {
    this()
    try {
      this.pixmap = new com.badlogic.gdx.graphics.g2d.Gdx2DPixmap(encodedData, offset, len, 0)
    } catch {
      case e: java.io.IOException => {
        throw new com.badlogic.gdx.utils.GdxRuntimeException("Couldn't load pixmap from image data", e)
      }
    }
  }
  def this(encodedData: java.nio.ByteBuffer, offset: scala.Int, len: scala.Int) = {
    this()
    if (!encodedData.isDirect()) {
      throw new com.badlogic.gdx.utils.GdxRuntimeException("Couldn't load pixmap from non-direct ByteBuffer")
    } else ()
    try {
      this.pixmap = new com.badlogic.gdx.graphics.g2d.Gdx2DPixmap(encodedData, offset, len, 0)
    } catch {
      case e: java.io.IOException => {
        throw new com.badlogic.gdx.utils.GdxRuntimeException("Couldn't load pixmap from image data", e)
      }
    }
  }
  def this(encodedData: java.nio.ByteBuffer) = {
    this(encodedData, encodedData.position(), encodedData.remaining())
  }
  def this(file: com.badlogic.gdx.files.FileHandle) = {
    this()
    try {
      val bytes: scala.Array[scala.Byte] = file.readBytes()
      this.pixmap = new com.badlogic.gdx.graphics.g2d.Gdx2DPixmap(bytes, 0, bytes.length, 0)
    } catch {
      case e: java.lang.Exception => {
        throw new com.badlogic.gdx.utils.GdxRuntimeException("Couldn't load file: " + file, e)
      }
    }
  }
  def this(pixmap: com.badlogic.gdx.graphics.g2d.Gdx2DPixmap) = {
    this()
    this.pixmap = pixmap
  }
  def setBlending(blending: com.badlogic.gdx.graphics.Pixmap.Blending): scala.Unit = {
    this.blending = blending
    this.pixmap.setBlend(if (blending == com.badlogic.gdx.graphics.Pixmap.Blending.None) 0 else 1)
  }
  def setFilter(filter: com.badlogic.gdx.graphics.Pixmap.Filter): scala.Unit = {
    this.filter = filter
    this.pixmap.setScale(if (filter == com.badlogic.gdx.graphics.Pixmap.Filter.NearestNeighbour) com.badlogic.gdx.graphics.g2d.Gdx2DPixmap.GDX2D_SCALE_NEAREST else com.badlogic.gdx.graphics.g2d.Gdx2DPixmap.GDX2D_SCALE_LINEAR)
  }
  def setColor(color: scala.Int): scala.Unit = {
    this.color = color
  }
  def setColor(r: scala.Float, g: scala.Float, b: scala.Float, a: scala.Float): scala.Unit = {
    this.color = com.badlogic.gdx.graphics.Color.rgba8888(r, g, b, a)
  }
  def setColor(color: com.badlogic.gdx.graphics.Color): scala.Unit = {
    this.color = com.badlogic.gdx.graphics.Color.rgba8888(color.r, color.g, color.b, color.a)
  }
  def fill(): scala.Unit = {
    this.pixmap.clear(this.color)
  }
  def drawLine(x: scala.Int, y: scala.Int, x2: scala.Int, y2: scala.Int): scala.Unit = {
    this.pixmap.drawLine(x, y, x2, y2, this.color)
  }
  def drawRectangle(x: scala.Int, y: scala.Int, width: scala.Int, height: scala.Int): scala.Unit = {
    this.pixmap.drawRect(x, y, width, height, this.color)
  }
  def drawPixmap(pixmap: Pixmap, x: scala.Int, y: scala.Int): scala.Unit = {
    this.drawPixmap(pixmap, x, y, 0, 0, pixmap.getWidth(), pixmap.getHeight())
  }
  def drawPixmap(pixmap: Pixmap, x: scala.Int, y: scala.Int, srcx: scala.Int, srcy: scala.Int, srcWidth: scala.Int, srcHeight: scala.Int): scala.Unit = {
    this.pixmap.drawPixmap(pixmap.pixmap, srcx, srcy, x, y, srcWidth, srcHeight)
  }
  def drawPixmap(pixmap: Pixmap, srcx: scala.Int, srcy: scala.Int, srcWidth: scala.Int, srcHeight: scala.Int, dstx: scala.Int, dsty: scala.Int, dstWidth: scala.Int, dstHeight: scala.Int): scala.Unit = {
    this.pixmap.drawPixmap(pixmap.pixmap, srcx, srcy, srcWidth, srcHeight, dstx, dsty, dstWidth, dstHeight)
  }
  def fillRectangle(x: scala.Int, y: scala.Int, width: scala.Int, height: scala.Int): scala.Unit = {
    this.pixmap.fillRect(x, y, width, height, this.color)
  }
  def drawCircle(x: scala.Int, y: scala.Int, radius: scala.Int): scala.Unit = {
    this.pixmap.drawCircle(x, y, radius, this.color)
  }
  def fillCircle(x: scala.Int, y: scala.Int, radius: scala.Int): scala.Unit = {
    this.pixmap.fillCircle(x, y, radius, this.color)
  }
  def fillTriangle(x1: scala.Int, y1: scala.Int, x2: scala.Int, y2: scala.Int, x3: scala.Int, y3: scala.Int): scala.Unit = {
    this.pixmap.fillTriangle(x1, y1, x2, y2, x3, y3, this.color)
  }
  def getPixel(x: scala.Int, y: scala.Int): scala.Int = {
    return this.pixmap.getPixel(x, y)
  }
  def getWidth(): scala.Int = {
    return this.pixmap.getWidth()
  }
  def getHeight(): scala.Int = {
    return this.pixmap.getHeight()
  }
  def dispose(): scala.Unit = {
    if (this.disposed) {
      com.badlogic.gdx.Gdx.app.error("Pixmap", "Pixmap already disposed!")
      return
    } else ()
    this.pixmap.dispose()
    this.disposed = true
  }
  def isDisposed(): scala.Boolean = {
    return this.disposed
  }
  def drawPixel(x: scala.Int, y: scala.Int): scala.Unit = {
    this.pixmap.setPixel(x, y, this.color)
  }
  def drawPixel(x: scala.Int, y: scala.Int, color: scala.Int): scala.Unit = {
    this.pixmap.setPixel(x, y, color)
  }
  def getGLFormat(): scala.Int = {
    return this.pixmap.getGLFormat()
  }
  def getGLInternalFormat(): scala.Int = {
    return this.pixmap.getGLInternalFormat()
  }
  def getGLType(): scala.Int = {
    return this.pixmap.getGLType()
  }
  def getPixels(): java.nio.ByteBuffer = {
    if (this.disposed) {
      throw new com.badlogic.gdx.utils.GdxRuntimeException("Pixmap already disposed")
    } else ()
    return this.pixmap.getPixels()
  }
  def setPixels(pixels: java.nio.ByteBuffer): scala.Unit = {
    if (!pixels.isDirect()) {
      throw new com.badlogic.gdx.utils.GdxRuntimeException("Couldn't setPixels from non-direct ByteBuffer")
    } else ()
    val dst: java.nio.ByteBuffer = this.pixmap.getPixels()
    com.badlogic.gdx.utils.BufferUtils.copy(pixels, dst, dst.limit())
  }
  def getFormat(): com.badlogic.gdx.graphics.Pixmap.Format = {
    return com.badlogic.gdx.graphics.Pixmap.Format.fromGdx2DPixmapFormat(this.pixmap.getFormat())
  }
  def getBlending(): com.badlogic.gdx.graphics.Pixmap.Blending = {
    return this.blending
  }
  def getFilter(): com.badlogic.gdx.graphics.Pixmap.Filter = {
    return this.filter
  }
}
object Pixmap {
  def createFromFrameBuffer(x: scala.Int, y: scala.Int, w: scala.Int, h: scala.Int): Pixmap = {
    com.badlogic.gdx.Gdx.gl.glPixelStorei(com.badlogic.gdx.graphics.GL20.GL_PACK_ALIGNMENT, 1)
    val pixmap: Pixmap = new Pixmap(w, h, com.badlogic.gdx.graphics.Pixmap.Format.RGBA8888)
    val pixels: java.nio.ByteBuffer = pixmap.getPixels()
    com.badlogic.gdx.Gdx.gl.glReadPixels(x, y, w, h, com.badlogic.gdx.graphics.GL20.GL_RGBA, com.badlogic.gdx.graphics.GL20.GL_UNSIGNED_BYTE, pixels)
    return pixmap
  }
  def downloadFromUrl(url: java.lang.String, responseListener: com.badlogic.gdx.graphics.Pixmap.DownloadPixmapResponseListener): scala.Unit = {
    val request: com.badlogic.gdx.Net.HttpRequest = new com.badlogic.gdx.Net.HttpRequest(com.badlogic.gdx.Net.HttpMethods.GET)
    request.setUrl(url)
    com.badlogic.gdx.Gdx.net.sendHttpRequest(request, new com.badlogic.gdx.Net.HttpResponseListener() {
      override def handleHttpResponse(httpResponse: com.badlogic.gdx.Net.HttpResponse): scala.Unit = {
        val result: scala.Array[scala.Byte] = httpResponse.getResult()
        com.badlogic.gdx.Gdx.app.postRunnable(new java.lang.Runnable() {
          override def run(): scala.Unit = {
            try {
              val pixmap: Pixmap = new Pixmap(result, 0, result.length)
              responseListener.downloadComplete(pixmap)
            } catch {
              case t: java.lang.Throwable => {
                this.failed(t)
              }
            }
          }
        })
      }
      override def failed(t: java.lang.Throwable): scala.Unit = {
        responseListener.downloadFailed(t)
      }
      override def cancelled(): scala.Unit = {
        ()
      }
    })
  }
  sealed abstract class Format {
    def name(): java.lang.String = this.toString()
  }
  object Format {
    case object Alpha extends Format
    case object Intensity extends Format
    case object LuminanceAlpha extends Format
    case object RGB565 extends Format
    case object RGBA4444 extends Format
    case object RGB888 extends Format
    case object RGBA8888 extends Format
    def values(): scala.Array[Format] = scala.Array(Alpha, Intensity, LuminanceAlpha, RGB565, RGBA4444, RGB888, RGBA8888)
    def valueOf(name: java.lang.String): Format = name match {
      case "Alpha" => Alpha
      case "Intensity" => Intensity
      case "LuminanceAlpha" => LuminanceAlpha
      case "RGB565" => RGB565
      case "RGBA4444" => RGBA4444
      case "RGB888" => RGB888
      case "RGBA8888" => RGBA8888
      case _ => throw new java.lang.IllegalArgumentException(name)
    }
    def toGdx2DPixmapFormat(format: com.badlogic.gdx.graphics.Pixmap.Format): scala.Int = {
      if (format == com.badlogic.gdx.graphics.Pixmap.Format.Alpha) {
        return com.badlogic.gdx.graphics.g2d.Gdx2DPixmap.GDX2D_FORMAT_ALPHA
      } else ()
      if (format == com.badlogic.gdx.graphics.Pixmap.Format.Intensity) {
        return com.badlogic.gdx.graphics.g2d.Gdx2DPixmap.GDX2D_FORMAT_ALPHA
      } else ()
      if (format == com.badlogic.gdx.graphics.Pixmap.Format.LuminanceAlpha) {
        return com.badlogic.gdx.graphics.g2d.Gdx2DPixmap.GDX2D_FORMAT_LUMINANCE_ALPHA
      } else ()
      if (format == com.badlogic.gdx.graphics.Pixmap.Format.RGB565) {
        return com.badlogic.gdx.graphics.g2d.Gdx2DPixmap.GDX2D_FORMAT_RGB565
      } else ()
      if (format == com.badlogic.gdx.graphics.Pixmap.Format.RGBA4444) {
        return com.badlogic.gdx.graphics.g2d.Gdx2DPixmap.GDX2D_FORMAT_RGBA4444
      } else ()
      if (format == com.badlogic.gdx.graphics.Pixmap.Format.RGB888) {
        return com.badlogic.gdx.graphics.g2d.Gdx2DPixmap.GDX2D_FORMAT_RGB888
      } else ()
      if (format == com.badlogic.gdx.graphics.Pixmap.Format.RGBA8888) {
        return com.badlogic.gdx.graphics.g2d.Gdx2DPixmap.GDX2D_FORMAT_RGBA8888
      } else ()
      throw new com.badlogic.gdx.utils.GdxRuntimeException("Unknown Format: " + format)
    }
    def fromGdx2DPixmapFormat(format: scala.Int): com.badlogic.gdx.graphics.Pixmap.Format = {
      if (format == com.badlogic.gdx.graphics.g2d.Gdx2DPixmap.GDX2D_FORMAT_ALPHA) {
        return com.badlogic.gdx.graphics.Pixmap.Format.Alpha
      } else ()
      if (format == com.badlogic.gdx.graphics.g2d.Gdx2DPixmap.GDX2D_FORMAT_LUMINANCE_ALPHA) {
        return com.badlogic.gdx.graphics.Pixmap.Format.LuminanceAlpha
      } else ()
      if (format == com.badlogic.gdx.graphics.g2d.Gdx2DPixmap.GDX2D_FORMAT_RGB565) {
        return com.badlogic.gdx.graphics.Pixmap.Format.RGB565
      } else ()
      if (format == com.badlogic.gdx.graphics.g2d.Gdx2DPixmap.GDX2D_FORMAT_RGBA4444) {
        return com.badlogic.gdx.graphics.Pixmap.Format.RGBA4444
      } else ()
      if (format == com.badlogic.gdx.graphics.g2d.Gdx2DPixmap.GDX2D_FORMAT_RGB888) {
        return com.badlogic.gdx.graphics.Pixmap.Format.RGB888
      } else ()
      if (format == com.badlogic.gdx.graphics.g2d.Gdx2DPixmap.GDX2D_FORMAT_RGBA8888) {
        return com.badlogic.gdx.graphics.Pixmap.Format.RGBA8888
      } else ()
      throw new com.badlogic.gdx.utils.GdxRuntimeException("Unknown Gdx2DPixmap Format: " + format)
    }
    def toGlFormat(format: com.badlogic.gdx.graphics.Pixmap.Format): scala.Int = {
      return com.badlogic.gdx.graphics.g2d.Gdx2DPixmap.toGlFormat(com.badlogic.gdx.graphics.Pixmap.Format.toGdx2DPixmapFormat(format))
    }
    def toGlType(format: com.badlogic.gdx.graphics.Pixmap.Format): scala.Int = {
      return com.badlogic.gdx.graphics.g2d.Gdx2DPixmap.toGlType(com.badlogic.gdx.graphics.Pixmap.Format.toGdx2DPixmapFormat(format))
    }
  }
  sealed abstract class Blending {
    def name(): java.lang.String = this.toString()
  }
  object Blending {
    case object None extends Blending
    case object SourceOver extends Blending
    def values(): scala.Array[Blending] = scala.Array(None, SourceOver)
    def valueOf(name: java.lang.String): Blending = name match {
      case "None" => None
      case "SourceOver" => SourceOver
      case _ => throw new java.lang.IllegalArgumentException(name)
    }
  }
  sealed abstract class Filter {
    def name(): java.lang.String = this.toString()
  }
  object Filter {
    case object NearestNeighbour extends Filter
    case object BiLinear extends Filter
    def values(): scala.Array[Filter] = scala.Array(NearestNeighbour, BiLinear)
    def valueOf(name: java.lang.String): Filter = name match {
      case "NearestNeighbour" => NearestNeighbour
      case "BiLinear" => BiLinear
      case _ => throw new java.lang.IllegalArgumentException(name)
    }
  }
  trait DownloadPixmapResponseListener {
    def downloadComplete(pixmap: Pixmap): scala.Unit
    def downloadFailed(t: java.lang.Throwable): scala.Unit
  }
}