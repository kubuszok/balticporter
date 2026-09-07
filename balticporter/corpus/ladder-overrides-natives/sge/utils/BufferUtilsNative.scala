/*
 * Port-written JVM answers to BufferUtils's JNI members (PROGRESS.md §13.30 step 2).
 * A JNI `Buffer` argument is the buffer's BASE address and every offset counts bytes from it, so
 * each Buffer is viewed whole through a MemorySegment; the array-shaped work goes to the platform
 * contract, as sge's own BufferUtils does.
 */
package sge
package utils

import java.lang.foreign.{ MemorySegment, ValueLayout }
import java.nio.{ Buffer, ByteBuffer }

private[sge] object BufferUtilsNative {
  private def ops = sge.platform.PlatformOps.buffer

  /** the whole buffer from its base — JNI's GetDirectBufferAddress view, position ignored. */
  private def base(b: Buffer): MemorySegment = MemorySegment.ofBuffer(b.duplicate().clear())

  private def floats(b: Buffer): Array[Float] = {
    val s = base(b)
    s.asSlice(0L, s.byteSize() / 4 * 4).toArray(ValueLayout.JAVA_FLOAT_UNALIGNED)
  }
  private def withFloats(b: Buffer)(f: Array[Float] => Unit): Unit = {
    val arr = floats(b)
    f(arr)
    MemorySegment.copy(MemorySegment.ofArray(arr), 0L, base(b), 0L, arr.length.toLong * 4)
  }
  private def copyFrom(src: MemorySegment, srcByteOffset: Long, dst: Buffer, dstOffset: Int, numBytes: Int): Unit =
    MemorySegment.copy(src, srcByteOffset, base(dst), dstOffset.toLong, numBytes.toLong)

  def freeMemory(buffer: ByteBuffer): Unit                = ops.freeMemory(buffer)
  def newDisposableByteBuffer(numBytes: Int): ByteBuffer  = ops.newDisposableByteBuffer(numBytes)
  def getBufferAddress(buffer: Buffer): Long              = base(buffer).address()
  def clear(buffer: ByteBuffer, numBytes: Int): Unit      = base(buffer).asSlice(0L, numBytes.toLong).fill(0.toByte)

  def copyJni(src: Array[Float], dst: Buffer, numFloats: Int, offset: Int): Unit =
    MemorySegment.copy(MemorySegment.ofArray(src), offset.toLong * 4, base(dst), 0L, numFloats.toLong * 4)
  def copyJni(src: Array[Byte], srcOffset: Int, dst: Buffer, dstOffset: Int, numBytes: Int): Unit =
    copyFrom(MemorySegment.ofArray(src), srcOffset.toLong, dst, dstOffset, numBytes)
  def copyJni(src: Array[Char], srcOffset: Int, dst: Buffer, dstOffset: Int, numBytes: Int): Unit =
    copyFrom(MemorySegment.ofArray(src), srcOffset.toLong * 2, dst, dstOffset, numBytes)
  def copyJni(src: Array[Short], srcOffset: Int, dst: Buffer, dstOffset: Int, numBytes: Int): Unit =
    copyFrom(MemorySegment.ofArray(src), srcOffset.toLong * 2, dst, dstOffset, numBytes)
  def copyJni(src: Array[Int], srcOffset: Int, dst: Buffer, dstOffset: Int, numBytes: Int): Unit =
    copyFrom(MemorySegment.ofArray(src), srcOffset.toLong * 4, dst, dstOffset, numBytes)
  def copyJni(src: Array[Long], srcOffset: Int, dst: Buffer, dstOffset: Int, numBytes: Int): Unit =
    copyFrom(MemorySegment.ofArray(src), srcOffset.toLong * 8, dst, dstOffset, numBytes)
  def copyJni(src: Array[Float], srcOffset: Int, dst: Buffer, dstOffset: Int, numBytes: Int): Unit =
    copyFrom(MemorySegment.ofArray(src), srcOffset.toLong * 4, dst, dstOffset, numBytes)
  def copyJni(src: Array[Double], srcOffset: Int, dst: Buffer, dstOffset: Int, numBytes: Int): Unit =
    copyFrom(MemorySegment.ofArray(src), srcOffset.toLong * 8, dst, dstOffset, numBytes)
  def copyJni(src: Buffer, srcOffset: Int, dst: Buffer, dstOffset: Int, numBytes: Int): Unit =
    MemorySegment.copy(base(src), srcOffset.toLong, base(dst), dstOffset.toLong, numBytes.toLong)

  def transformV4M4Jni(data: Buffer, strideInBytes: Int, count: Int, matrix: Array[Float], offsetInBytes: Int): Unit =
    withFloats(data)(arr => ops.transformV4M4(arr, strideInBytes / 4, count, matrix, offsetInBytes / 4))
  def transformV4M4Jni(data: Array[Float], strideInBytes: Int, count: Int, matrix: Array[Float], offsetInBytes: Int): Unit =
    ops.transformV4M4(data, strideInBytes / 4, count, matrix, offsetInBytes / 4)
  def transformV3M4Jni(data: Buffer, strideInBytes: Int, count: Int, matrix: Array[Float], offsetInBytes: Int): Unit =
    withFloats(data)(arr => ops.transformV3M4(arr, strideInBytes / 4, count, matrix, offsetInBytes / 4))
  def transformV3M4Jni(data: Array[Float], strideInBytes: Int, count: Int, matrix: Array[Float], offsetInBytes: Int): Unit =
    ops.transformV3M4(data, strideInBytes / 4, count, matrix, offsetInBytes / 4)
  def transformV2M4Jni(data: Buffer, strideInBytes: Int, count: Int, matrix: Array[Float], offsetInBytes: Int): Unit =
    withFloats(data)(arr => ops.transformV2M4(arr, strideInBytes / 4, count, matrix, offsetInBytes / 4))
  def transformV2M4Jni(data: Array[Float], strideInBytes: Int, count: Int, matrix: Array[Float], offsetInBytes: Int): Unit =
    ops.transformV2M4(data, strideInBytes / 4, count, matrix, offsetInBytes / 4)
  def transformV3M3Jni(data: Buffer, strideInBytes: Int, count: Int, matrix: Array[Float], offsetInBytes: Int): Unit =
    withFloats(data)(arr => ops.transformV3M3(arr, strideInBytes / 4, count, matrix, offsetInBytes / 4))
  def transformV3M3Jni(data: Array[Float], strideInBytes: Int, count: Int, matrix: Array[Float], offsetInBytes: Int): Unit =
    ops.transformV3M3(data, strideInBytes / 4, count, matrix, offsetInBytes / 4)
  def transformV2M3Jni(data: Buffer, strideInBytes: Int, count: Int, matrix: Array[Float], offsetInBytes: Int): Unit =
    withFloats(data)(arr => ops.transformV2M3(arr, strideInBytes / 4, count, matrix, offsetInBytes / 4))
  def transformV2M3Jni(data: Array[Float], strideInBytes: Int, count: Int, matrix: Array[Float], offsetInBytes: Int): Unit =
    ops.transformV2M3(data, strideInBytes / 4, count, matrix, offsetInBytes / 4)

  def find(vertex: Buffer, vertexOffsetInBytes: Int, strideInBytes: Int, vertices: Buffer, verticesOffsetInBytes: Int, numVertices: Int): Long =
    ops.find(floats(vertex), vertexOffsetInBytes / 4, strideInBytes / 4, floats(vertices), verticesOffsetInBytes / 4, numVertices)
  def find(vertex: Array[Float], vertexOffsetInBytes: Int, strideInBytes: Int, vertices: Buffer, verticesOffsetInBytes: Int, numVertices: Int): Long =
    ops.find(vertex, vertexOffsetInBytes / 4, strideInBytes / 4, floats(vertices), verticesOffsetInBytes / 4, numVertices)
  def find(vertex: Buffer, vertexOffsetInBytes: Int, strideInBytes: Int, vertices: Array[Float], verticesOffsetInBytes: Int, numVertices: Int): Long =
    ops.find(floats(vertex), vertexOffsetInBytes / 4, strideInBytes / 4, vertices, verticesOffsetInBytes / 4, numVertices)
  def find(vertex: Array[Float], vertexOffsetInBytes: Int, strideInBytes: Int, vertices: Array[Float], verticesOffsetInBytes: Int, numVertices: Int): Long =
    ops.find(vertex, vertexOffsetInBytes / 4, strideInBytes / 4, vertices, verticesOffsetInBytes / 4, numVertices)
  def find(vertex: Buffer, vertexOffsetInBytes: Int, strideInBytes: Int, vertices: Buffer, verticesOffsetInBytes: Int, numVertices: Int, epsilon: Float): Long =
    ops.find(floats(vertex), vertexOffsetInBytes / 4, strideInBytes / 4, floats(vertices), verticesOffsetInBytes / 4, numVertices, epsilon)
  def find(vertex: Array[Float], vertexOffsetInBytes: Int, strideInBytes: Int, vertices: Buffer, verticesOffsetInBytes: Int, numVertices: Int, epsilon: Float): Long =
    ops.find(vertex, vertexOffsetInBytes / 4, strideInBytes / 4, floats(vertices), verticesOffsetInBytes / 4, numVertices, epsilon)
  def find(vertex: Buffer, vertexOffsetInBytes: Int, strideInBytes: Int, vertices: Array[Float], verticesOffsetInBytes: Int, numVertices: Int, epsilon: Float): Long =
    ops.find(floats(vertex), vertexOffsetInBytes / 4, strideInBytes / 4, vertices, verticesOffsetInBytes / 4, numVertices, epsilon)
  def find(vertex: Array[Float], vertexOffsetInBytes: Int, strideInBytes: Int, vertices: Array[Float], verticesOffsetInBytes: Int, numVertices: Int, epsilon: Float): Long =
    ops.find(vertex, vertexOffsetInBytes / 4, strideInBytes / 4, vertices, verticesOffsetInBytes / 4, numVertices, epsilon)
}
