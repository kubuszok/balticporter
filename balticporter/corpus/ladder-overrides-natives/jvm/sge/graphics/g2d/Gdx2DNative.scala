/*
 * Port-written JVM answers to Gdx2DPixmap's JNI members (PROGRESS.md §13.30 step 2): the `long`
 * pixmap handle java passes around keys a table of pixel buffers, drawn by Gdx2dDraw (sge's
 * pure-Scala gdx2d.c) and decoded through the platform contract. gdx2d.c's defaults are kept:
 * blend SRC_OVER, scale bilinear (`GDX2D_SCALE_LINEAR`).
 */
package sge
package graphics
package g2d

import java.nio.ByteBuffer
import java.util.concurrent.ConcurrentHashMap
import java.util.concurrent.atomic.AtomicLong

private[sge] object Gdx2DNative {
  import Gdx2DPixmap.{ GDX2D_BLEND_SRC_OVER, GDX2D_SCALE_LINEAR }

  private final class Pix(val pixels: ByteBuffer, val width: Int, val height: Int, val format: Int) {
    var blend: Int = GDX2D_BLEND_SRC_OVER
    var scale: Int = GDX2D_SCALE_LINEAR
  }
  private val table = new ConcurrentHashMap[java.lang.Long, Pix]()
  private val next  = new AtomicLong(1L)

  private def register(p: Pix, nativeData: Array[Long]): ByteBuffer = {
    val h = next.getAndIncrement()
    table.put(h, p)
    nativeData(0) = h
    nativeData(1) = p.width.toLong
    nativeData(2) = p.height.toLong
    nativeData(3) = p.format.toLong
    p.pixels
  }
  private def get(h: Long): Pix = {
    val p = table.get(h)
    if p == null then throw new IllegalStateException(s"gdx2d: no pixmap behind handle $h (already freed?)")
    p
  }

  def load(nativeData: Array[Long], buffer: Array[Byte], offset: Int, len: Int): ByteBuffer =
    sge.platform.PlatformOps.gdx2d.decodeImage(buffer, offset, len) match {
      case Some(r) => register(new Pix(r.pixels, r.width, r.height, r.format), nativeData)
      case None    => null
    }
  def loadByteBuffer(nativeData: Array[Long], buffer: ByteBuffer, offset: Int, len: Int): ByteBuffer =
    if buffer == null then null
    else {
      val bytes = new Array[Byte](len)
      val dup   = buffer.duplicate()
      dup.clear()
      dup.position(offset)
      dup.get(bytes, 0, len)
      load(nativeData, bytes, 0, len)
    }
  def newPixmap(nativeData: Array[Long], width: Int, height: Int, format: Int): ByteBuffer =
    register(new Pix(Gdx2dDraw.newPixelBuffer(width, height, format), width, height, format), nativeData)

  def free(pixmap: Long): Unit = table.remove(pixmap)
  def clear(pixmap: Long, color: Int): Unit = {
    val p = get(pixmap)
    Gdx2dDraw.clear(p.pixels, p.width, p.height, p.format, color)
  }
  def setPixel(pixmap: Long, x: Int, y: Int, color: Int): Unit = {
    val p = get(pixmap)
    Gdx2dDraw.setPixel(p.pixels, p.width, p.height, p.format, p.blend, x, y, color)
  }
  def getPixel(pixmap: Long, x: Int, y: Int): Int = {
    val p = get(pixmap)
    Gdx2dDraw.getPixel(p.pixels, p.width, p.height, p.format, x, y)
  }
  def drawLine(pixmap: Long, x: Int, y: Int, x2: Int, y2: Int, color: Int): Unit = {
    val p = get(pixmap)
    Gdx2dDraw.drawLine(p.pixels, p.width, p.height, p.format, p.blend, x, y, x2, y2, color)
  }
  def drawRect(pixmap: Long, x: Int, y: Int, width: Int, height: Int, color: Int): Unit = {
    val p = get(pixmap)
    Gdx2dDraw.drawRect(p.pixels, p.width, p.height, p.format, p.blend, x, y, width, height, color)
  }
  def drawCircle(pixmap: Long, x: Int, y: Int, radius: Int, color: Int): Unit = {
    val p = get(pixmap)
    Gdx2dDraw.drawCircle(p.pixels, p.width, p.height, p.format, p.blend, x, y, radius, color)
  }
  def fillRect(pixmap: Long, x: Int, y: Int, width: Int, height: Int, color: Int): Unit = {
    val p = get(pixmap)
    Gdx2dDraw.fillRect(p.pixels, p.width, p.height, p.format, p.blend, x, y, width, height, color)
  }
  def fillCircle(pixmap: Long, x: Int, y: Int, radius: Int, color: Int): Unit = {
    val p = get(pixmap)
    Gdx2dDraw.fillCircle(p.pixels, p.width, p.height, p.format, p.blend, x, y, radius, color)
  }
  def fillTriangle(pixmap: Long, x1: Int, y1: Int, x2: Int, y2: Int, x3: Int, y3: Int, color: Int): Unit = {
    val p = get(pixmap)
    Gdx2dDraw.fillTriangle(p.pixels, p.width, p.height, p.format, p.blend, x1, y1, x2, y2, x3, y3, color)
  }
  def drawPixmap(src: Long, dst: Long, srcX: Int, srcY: Int, srcWidth: Int, srcHeight: Int, dstX: Int, dstY: Int, dstWidth: Int, dstHeight: Int): Unit = {
    val s = get(src)
    val d = get(dst)
    Gdx2dDraw.drawPixmap(s.pixels, s.width, s.height, s.format, d.pixels, d.width, d.height, d.format, d.blend, d.scale,
      srcX, srcY, srcWidth, srcHeight, dstX, dstY, dstWidth, dstHeight)
  }
  def setBlend(src: Long, blend: Int): Unit = get(src).blend = blend
  def setScale(src: Long, scale: Int): Unit = get(src).scale = scale
  def getFailureReason(): String = sge.platform.PlatformOps.gdx2d.failureReason
}
