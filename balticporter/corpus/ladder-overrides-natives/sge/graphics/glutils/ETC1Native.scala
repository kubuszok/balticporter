/*
 * Port-written JVM answers to ETC1's JNI members (PROGRESS.md §13.30 step 2): the platform contract
 * works on byte arrays, a JNI ByteBuffer argument is addressed from its BASE, so each call copies the
 * addressed bytes out, runs the contract, and copies the result back — sge's own ETC1 does the same.
 */
package sge
package graphics
package glutils

import java.lang.foreign.{ MemorySegment, ValueLayout }
import java.nio.ByteBuffer

private[sge] object ETC1Native {
  private def etc1 = sge.platform.PlatformOps.etc1

  private def base(b: ByteBuffer): MemorySegment = MemorySegment.ofBuffer(b.duplicate().clear())
  private def bytes(b: ByteBuffer, offset: Int, len: Int): Array[Byte] =
    base(b).asSlice(offset.toLong, len.toLong).toArray(ValueLayout.JAVA_BYTE)
  private def put(b: ByteBuffer, offset: Int, arr: Array[Byte]): Unit =
    MemorySegment.copy(MemorySegment.ofArray(arr), 0L, base(b), offset.toLong, arr.length.toLong)
  /** a malloc-backed direct buffer, as the C code returned — released by BufferUtils.freeMemory. */
  private def malloc(arr: Array[Byte]): ByteBuffer = {
    val r = sge.platform.PlatformOps.buffer.newDisposableByteBuffer(arr.length)
    r.put(arr)
    r.position(0)
    r
  }

  def getCompressedDataSize(width: Int, height: Int): Int = etc1.getCompressedDataSize(width, height)
  def formatHeader(header: ByteBuffer, offset: Int, width: Int, height: Int): Unit = {
    val h = new Array[Byte](etc1.PKM_HEADER_SIZE)
    etc1.formatHeader(h, 0, width, height)
    put(header, offset, h)
  }
  def getWidthPKM(header: ByteBuffer, offset: Int): Int      = etc1.getWidthPKM(bytes(header, offset, etc1.PKM_HEADER_SIZE), 0)
  def getHeightPKM(header: ByteBuffer, offset: Int): Int     = etc1.getHeightPKM(bytes(header, offset, etc1.PKM_HEADER_SIZE), 0)
  def isValidPKM(header: ByteBuffer, offset: Int): Boolean   = etc1.isValidPKM(bytes(header, offset, etc1.PKM_HEADER_SIZE), 0)
  def decodeImage(compressedData: ByteBuffer, offset: Int, decodedData: ByteBuffer, offsetDec: Int, width: Int, height: Int, pixelSize: Int): Unit = {
    val comp = bytes(compressedData, offset, etc1.getCompressedDataSize(width, height))
    val dec  = new Array[Byte](width * height * pixelSize)
    etc1.decodeImage(comp, 0, dec, 0, width, height, pixelSize)
    put(decodedData, offsetDec, dec)
  }
  def encodeImage(imageData: ByteBuffer, offset: Int, width: Int, height: Int, pixelSize: Int): ByteBuffer =
    malloc(etc1.encodeImage(bytes(imageData, offset, width * height * pixelSize), 0, width, height, pixelSize))
  def encodeImagePKM(imageData: ByteBuffer, offset: Int, width: Int, height: Int, pixelSize: Int): ByteBuffer =
    malloc(etc1.encodeImagePKM(bytes(imageData, offset, width * height * pixelSize), 0, width, height, pixelSize))
}
