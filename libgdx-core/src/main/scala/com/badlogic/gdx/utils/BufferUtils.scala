package com.badlogic.gdx.utils

object BufferUtils {
  var unsafeBuffers: com.badlogic.gdx.utils.Array[java.nio.ByteBuffer] = new com.badlogic.gdx.utils.Array[java.nio.ByteBuffer]()
  var allocatedUnsafe: scala.Int = 0
  def copy(src: scala.Array[scala.Float], dst: java.nio.Buffer, numFloats: scala.Int, offset: scala.Int): scala.Unit = {
    if (dst.isInstanceOf[java.nio.ByteBuffer]) {
      dst.limit(numFloats << 2)
    } else {
      if (dst.isInstanceOf[java.nio.FloatBuffer]) {
        dst.limit(numFloats)
      } else ()
    }
    BufferUtils.copyJni(src, dst, numFloats, offset)
    dst.position(0)
  }
  def copy(src: scala.Array[scala.Byte], srcOffset: scala.Int, dst: java.nio.Buffer, numElements: scala.Int): scala.Unit = {
    dst.limit(dst.position() + BufferUtils.bytesToElements(dst, numElements))
    BufferUtils.copyJni(src, srcOffset, dst, BufferUtils.positionInBytes(dst), numElements)
  }
  def copy(src: scala.Array[scala.Short], srcOffset: scala.Int, dst: java.nio.Buffer, numElements: scala.Int): scala.Unit = {
    dst.limit(dst.position() + BufferUtils.bytesToElements(dst, numElements << 1))
    BufferUtils.copyJni(src, srcOffset, dst, BufferUtils.positionInBytes(dst), numElements << 1)
  }
  def copy(src: scala.Array[scala.Char], srcOffset: scala.Int, numElements: scala.Int, dst: java.nio.Buffer): scala.Unit = {
    BufferUtils.copyJni(src, srcOffset, dst, BufferUtils.positionInBytes(dst), numElements << 1)
  }
  def copy(src: scala.Array[scala.Int], srcOffset: scala.Int, numElements: scala.Int, dst: java.nio.Buffer): scala.Unit = {
    BufferUtils.copyJni(src, srcOffset, dst, BufferUtils.positionInBytes(dst), numElements << 2)
  }
  def copy(src: scala.Array[scala.Long], srcOffset: scala.Int, numElements: scala.Int, dst: java.nio.Buffer): scala.Unit = {
    BufferUtils.copyJni(src, srcOffset, dst, BufferUtils.positionInBytes(dst), numElements << 3)
  }
  def copy(src: scala.Array[scala.Float], srcOffset: scala.Int, numElements: scala.Int, dst: java.nio.Buffer): scala.Unit = {
    BufferUtils.copyJni(src, srcOffset, dst, BufferUtils.positionInBytes(dst), numElements << 2)
  }
  def copy(src: scala.Array[scala.Double], srcOffset: scala.Int, numElements: scala.Int, dst: java.nio.Buffer): scala.Unit = {
    BufferUtils.copyJni(src, srcOffset, dst, BufferUtils.positionInBytes(dst), numElements << 3)
  }
  def copy(src: scala.Array[scala.Char], srcOffset: scala.Int, dst: java.nio.Buffer, numElements: scala.Int): scala.Unit = {
    dst.limit(dst.position() + BufferUtils.bytesToElements(dst, numElements << 1))
    BufferUtils.copyJni(src, srcOffset, dst, BufferUtils.positionInBytes(dst), numElements << 1)
  }
  def copy(src: scala.Array[scala.Int], srcOffset: scala.Int, dst: java.nio.Buffer, numElements: scala.Int): scala.Unit = {
    dst.limit(dst.position() + BufferUtils.bytesToElements(dst, numElements << 2))
    BufferUtils.copyJni(src, srcOffset, dst, BufferUtils.positionInBytes(dst), numElements << 2)
  }
  def copy(src: scala.Array[scala.Long], srcOffset: scala.Int, dst: java.nio.Buffer, numElements: scala.Int): scala.Unit = {
    dst.limit(dst.position() + BufferUtils.bytesToElements(dst, numElements << 3))
    BufferUtils.copyJni(src, srcOffset, dst, BufferUtils.positionInBytes(dst), numElements << 3)
  }
  def copy(src: scala.Array[scala.Float], srcOffset: scala.Int, dst: java.nio.Buffer, numElements: scala.Int): scala.Unit = {
    dst.limit(dst.position() + BufferUtils.bytesToElements(dst, numElements << 2))
    BufferUtils.copyJni(src, srcOffset, dst, BufferUtils.positionInBytes(dst), numElements << 2)
  }
  def copy(src: scala.Array[scala.Double], srcOffset: scala.Int, dst: java.nio.Buffer, numElements: scala.Int): scala.Unit = {
    dst.limit(dst.position() + BufferUtils.bytesToElements(dst, numElements << 3))
    BufferUtils.copyJni(src, srcOffset, dst, BufferUtils.positionInBytes(dst), numElements << 3)
  }
  def copy(src: java.nio.Buffer, dst: java.nio.Buffer, numElements: scala.Int): scala.Unit = {
    val numBytes: scala.Int = BufferUtils.elementsToBytes(src, numElements)
    dst.limit(dst.position() + BufferUtils.bytesToElements(dst, numBytes))
    BufferUtils.copyJni(src, BufferUtils.positionInBytes(src), dst, BufferUtils.positionInBytes(dst), numBytes)
  }
  def transform(data: java.nio.Buffer, dimensions: scala.Int, strideInBytes: scala.Int, count: scala.Int, matrix: com.badlogic.gdx.math.Matrix4): scala.Unit = {
    BufferUtils.transform(data, dimensions, strideInBytes, count, matrix, 0)
  }
  def transform(data: scala.Array[scala.Float], dimensions: scala.Int, strideInBytes: scala.Int, count: scala.Int, matrix: com.badlogic.gdx.math.Matrix4): scala.Unit = {
    BufferUtils.transform(data, dimensions, strideInBytes, count, matrix, 0)
  }
  def transform(data: java.nio.Buffer, dimensions: scala.Int, strideInBytes: scala.Int, count: scala.Int, matrix: com.badlogic.gdx.math.Matrix4, offset: scala.Int): scala.Unit = {
    dimensions match {
      case 4 => {
        BufferUtils.transformV4M4Jni(data, strideInBytes, count, matrix.`val`, BufferUtils.positionInBytes(data) + offset)
      }
      case 3 => {
        BufferUtils.transformV3M4Jni(data, strideInBytes, count, matrix.`val`, BufferUtils.positionInBytes(data) + offset)
      }
      case 2 => {
        BufferUtils.transformV2M4Jni(data, strideInBytes, count, matrix.`val`, BufferUtils.positionInBytes(data) + offset)
      }
      case _ => {
        throw new java.lang.IllegalArgumentException()
      }
    }
  }
  def transform(data: scala.Array[scala.Float], dimensions: scala.Int, strideInBytes: scala.Int, count: scala.Int, matrix: com.badlogic.gdx.math.Matrix4, offset: scala.Int): scala.Unit = {
    dimensions match {
      case 4 => {
        BufferUtils.transformV4M4Jni(data, strideInBytes, count, matrix.`val`, offset)
      }
      case 3 => {
        BufferUtils.transformV3M4Jni(data, strideInBytes, count, matrix.`val`, offset)
      }
      case 2 => {
        BufferUtils.transformV2M4Jni(data, strideInBytes, count, matrix.`val`, offset)
      }
      case _ => {
        throw new java.lang.IllegalArgumentException()
      }
    }
  }
  def transform(data: java.nio.Buffer, dimensions: scala.Int, strideInBytes: scala.Int, count: scala.Int, matrix: com.badlogic.gdx.math.Matrix3): scala.Unit = {
    BufferUtils.transform(data, dimensions, strideInBytes, count, matrix, 0)
  }
  def transform(data: scala.Array[scala.Float], dimensions: scala.Int, strideInBytes: scala.Int, count: scala.Int, matrix: com.badlogic.gdx.math.Matrix3): scala.Unit = {
    BufferUtils.transform(data, dimensions, strideInBytes, count, matrix, 0)
  }
  def transform(data: java.nio.Buffer, dimensions: scala.Int, strideInBytes: scala.Int, count: scala.Int, matrix: com.badlogic.gdx.math.Matrix3, offset: scala.Int): scala.Unit = {
    dimensions match {
      case 3 => {
        BufferUtils.transformV3M3Jni(data, strideInBytes, count, matrix.`val`, BufferUtils.positionInBytes(data) + offset)
      }
      case 2 => {
        BufferUtils.transformV2M3Jni(data, strideInBytes, count, matrix.`val`, BufferUtils.positionInBytes(data) + offset)
      }
      case _ => {
        throw new java.lang.IllegalArgumentException()
      }
    }
  }
  def transform(data: scala.Array[scala.Float], dimensions: scala.Int, strideInBytes: scala.Int, count: scala.Int, matrix: com.badlogic.gdx.math.Matrix3, offset: scala.Int): scala.Unit = {
    dimensions match {
      case 3 => {
        BufferUtils.transformV3M3Jni(data, strideInBytes, count, matrix.`val`, offset)
      }
      case 2 => {
        BufferUtils.transformV2M3Jni(data, strideInBytes, count, matrix.`val`, offset)
      }
      case _ => {
        throw new java.lang.IllegalArgumentException()
      }
    }
  }
  def findFloats(vertex: java.nio.Buffer, strideInBytes: scala.Int, vertices: java.nio.Buffer, numVertices: scala.Int): scala.Long = {
    return BufferUtils.find(vertex, BufferUtils.positionInBytes(vertex), strideInBytes, vertices, BufferUtils.positionInBytes(vertices), numVertices)
  }
  def findFloats(vertex: scala.Array[scala.Float], strideInBytes: scala.Int, vertices: java.nio.Buffer, numVertices: scala.Int): scala.Long = {
    return BufferUtils.find(vertex, 0, strideInBytes, vertices, BufferUtils.positionInBytes(vertices), numVertices)
  }
  def findFloats(vertex: java.nio.Buffer, strideInBytes: scala.Int, vertices: scala.Array[scala.Float], numVertices: scala.Int): scala.Long = {
    return BufferUtils.find(vertex, BufferUtils.positionInBytes(vertex), strideInBytes, vertices, 0, numVertices)
  }
  def findFloats(vertex: scala.Array[scala.Float], strideInBytes: scala.Int, vertices: scala.Array[scala.Float], numVertices: scala.Int): scala.Long = {
    return BufferUtils.find(vertex, 0, strideInBytes, vertices, 0, numVertices)
  }
  def findFloats(vertex: java.nio.Buffer, strideInBytes: scala.Int, vertices: java.nio.Buffer, numVertices: scala.Int, epsilon: scala.Float): scala.Long = {
    return BufferUtils.find(vertex, BufferUtils.positionInBytes(vertex), strideInBytes, vertices, BufferUtils.positionInBytes(vertices), numVertices, epsilon)
  }
  def findFloats(vertex: scala.Array[scala.Float], strideInBytes: scala.Int, vertices: java.nio.Buffer, numVertices: scala.Int, epsilon: scala.Float): scala.Long = {
    return BufferUtils.find(vertex, 0, strideInBytes, vertices, BufferUtils.positionInBytes(vertices), numVertices, epsilon)
  }
  def findFloats(vertex: java.nio.Buffer, strideInBytes: scala.Int, vertices: scala.Array[scala.Float], numVertices: scala.Int, epsilon: scala.Float): scala.Long = {
    return BufferUtils.find(vertex, BufferUtils.positionInBytes(vertex), strideInBytes, vertices, 0, numVertices, epsilon)
  }
  def findFloats(vertex: scala.Array[scala.Float], strideInBytes: scala.Int, vertices: scala.Array[scala.Float], numVertices: scala.Int, epsilon: scala.Float): scala.Long = {
    return BufferUtils.find(vertex, 0, strideInBytes, vertices, 0, numVertices, epsilon)
  }
  private def positionInBytes(dst: java.nio.Buffer): scala.Int = {
    if (dst.isInstanceOf[java.nio.ByteBuffer]) {
      return dst.position()
    } else {
      if (dst.isInstanceOf[java.nio.ShortBuffer]) {
        return dst.position() << 1
      } else {
        if (dst.isInstanceOf[java.nio.CharBuffer]) {
          return dst.position() << 1
        } else {
          if (dst.isInstanceOf[java.nio.IntBuffer]) {
            return dst.position() << 2
          } else {
            if (dst.isInstanceOf[java.nio.LongBuffer]) {
              return dst.position() << 3
            } else {
              if (dst.isInstanceOf[java.nio.FloatBuffer]) {
                return dst.position() << 2
              } else {
                if (dst.isInstanceOf[java.nio.DoubleBuffer]) {
                  return dst.position() << 3
                } else {
                  throw new com.badlogic.gdx.utils.GdxRuntimeException(("Can't copy to a " + dst.getClass().getName()) + " instance")
                }
              }
            }
          }
        }
      }
    }
  }
  private def bytesToElements(dst: java.nio.Buffer, bytes: scala.Int): scala.Int = {
    if (dst.isInstanceOf[java.nio.ByteBuffer]) {
      return bytes
    } else {
      if (dst.isInstanceOf[java.nio.ShortBuffer]) {
        return bytes >>> 1
      } else {
        if (dst.isInstanceOf[java.nio.CharBuffer]) {
          return bytes >>> 1
        } else {
          if (dst.isInstanceOf[java.nio.IntBuffer]) {
            return bytes >>> 2
          } else {
            if (dst.isInstanceOf[java.nio.LongBuffer]) {
              return bytes >>> 3
            } else {
              if (dst.isInstanceOf[java.nio.FloatBuffer]) {
                return bytes >>> 2
              } else {
                if (dst.isInstanceOf[java.nio.DoubleBuffer]) {
                  return bytes >>> 3
                } else {
                  throw new com.badlogic.gdx.utils.GdxRuntimeException(("Can't copy to a " + dst.getClass().getName()) + " instance")
                }
              }
            }
          }
        }
      }
    }
  }
  private def elementsToBytes(dst: java.nio.Buffer, elements: scala.Int): scala.Int = {
    if (dst.isInstanceOf[java.nio.ByteBuffer]) {
      return elements
    } else {
      if (dst.isInstanceOf[java.nio.ShortBuffer]) {
        return elements << 1
      } else {
        if (dst.isInstanceOf[java.nio.CharBuffer]) {
          return elements << 1
        } else {
          if (dst.isInstanceOf[java.nio.IntBuffer]) {
            return elements << 2
          } else {
            if (dst.isInstanceOf[java.nio.LongBuffer]) {
              return elements << 3
            } else {
              if (dst.isInstanceOf[java.nio.FloatBuffer]) {
                return elements << 2
              } else {
                if (dst.isInstanceOf[java.nio.DoubleBuffer]) {
                  return elements << 3
                } else {
                  throw new com.badlogic.gdx.utils.GdxRuntimeException(("Can't copy to a " + dst.getClass().getName()) + " instance")
                }
              }
            }
          }
        }
      }
    }
  }
  def newFloatBuffer(numFloats: scala.Int): java.nio.FloatBuffer = {
    val buffer: java.nio.ByteBuffer = java.nio.ByteBuffer.allocateDirect(numFloats * 4)
    buffer.order(java.nio.ByteOrder.nativeOrder())
    return buffer.asFloatBuffer()
  }
  def newDoubleBuffer(numDoubles: scala.Int): java.nio.DoubleBuffer = {
    val buffer: java.nio.ByteBuffer = java.nio.ByteBuffer.allocateDirect(numDoubles * 8)
    buffer.order(java.nio.ByteOrder.nativeOrder())
    return buffer.asDoubleBuffer()
  }
  def newByteBuffer(numBytes: scala.Int): java.nio.ByteBuffer = {
    val buffer: java.nio.ByteBuffer = java.nio.ByteBuffer.allocateDirect(numBytes)
    buffer.order(java.nio.ByteOrder.nativeOrder())
    return buffer
  }
  def newShortBuffer(numShorts: scala.Int): java.nio.ShortBuffer = {
    val buffer: java.nio.ByteBuffer = java.nio.ByteBuffer.allocateDirect(numShorts * 2)
    buffer.order(java.nio.ByteOrder.nativeOrder())
    return buffer.asShortBuffer()
  }
  def newCharBuffer(numChars: scala.Int): java.nio.CharBuffer = {
    val buffer: java.nio.ByteBuffer = java.nio.ByteBuffer.allocateDirect(numChars * 2)
    buffer.order(java.nio.ByteOrder.nativeOrder())
    return buffer.asCharBuffer()
  }
  def newIntBuffer(numInts: scala.Int): java.nio.IntBuffer = {
    val buffer: java.nio.ByteBuffer = java.nio.ByteBuffer.allocateDirect(numInts * 4)
    buffer.order(java.nio.ByteOrder.nativeOrder())
    return buffer.asIntBuffer()
  }
  def newLongBuffer(numLongs: scala.Int): java.nio.LongBuffer = {
    val buffer: java.nio.ByteBuffer = java.nio.ByteBuffer.allocateDirect(numLongs * 8)
    buffer.order(java.nio.ByteOrder.nativeOrder())
    return buffer.asLongBuffer()
  }
  def disposeUnsafeByteBuffer(buffer: java.nio.ByteBuffer): scala.Unit = {
    val size: scala.Int = buffer.capacity()
    BufferUtils.unsafeBuffers.synchronized {
      if (!BufferUtils.unsafeBuffers.removeValue(buffer, true)) {
        throw new java.lang.IllegalArgumentException("buffer not allocated with newUnsafeByteBuffer or already disposed")
      } else ()
    }
    BufferUtils.allocatedUnsafe = BufferUtils.allocatedUnsafe - size
    BufferUtils.freeMemory(buffer)
  }
  def isUnsafeByteBuffer(buffer: java.nio.ByteBuffer): scala.Boolean = {
    BufferUtils.unsafeBuffers.synchronized {
      return BufferUtils.unsafeBuffers.contains(buffer, true)
    }
  }
  def newUnsafeByteBuffer(numBytes: scala.Int): java.nio.ByteBuffer = {
    val buffer: java.nio.ByteBuffer = BufferUtils.newDisposableByteBuffer(numBytes)
    buffer.order(java.nio.ByteOrder.nativeOrder())
    BufferUtils.allocatedUnsafe = BufferUtils.allocatedUnsafe + numBytes
    BufferUtils.unsafeBuffers.synchronized {
      BufferUtils.unsafeBuffers.add(buffer)
    }
    return buffer
  }
  def getUnsafeBufferAddress(buffer: java.nio.Buffer): scala.Long = {
    return BufferUtils.getBufferAddress(buffer) + buffer.position()
  }
  def newUnsafeByteBuffer(buffer: java.nio.ByteBuffer): java.nio.ByteBuffer = {
    BufferUtils.allocatedUnsafe = BufferUtils.allocatedUnsafe + buffer.capacity()
    BufferUtils.unsafeBuffers.synchronized {
      BufferUtils.unsafeBuffers.add(buffer)
    }
    return buffer
  }
  def getAllocatedBytesUnsafe(): scala.Int = {
    return BufferUtils.allocatedUnsafe
  }
  private def freeMemory(buffer: java.nio.ByteBuffer): scala.Unit
  private def newDisposableByteBuffer(numBytes: scala.Int): java.nio.ByteBuffer
  private def getBufferAddress(buffer: java.nio.Buffer): scala.Long
  def clear(buffer: java.nio.ByteBuffer, numBytes: scala.Int): scala.Unit
  private def copyJni(src: scala.Array[scala.Float], dst: java.nio.Buffer, numFloats: scala.Int, offset: scala.Int): scala.Unit
  private def copyJni(src: scala.Array[scala.Byte], srcOffset: scala.Int, dst: java.nio.Buffer, dstOffset: scala.Int, numBytes: scala.Int): scala.Unit
  private def copyJni(src: scala.Array[scala.Char], srcOffset: scala.Int, dst: java.nio.Buffer, dstOffset: scala.Int, numBytes: scala.Int): scala.Unit
  private def copyJni(src: scala.Array[scala.Short], srcOffset: scala.Int, dst: java.nio.Buffer, dstOffset: scala.Int, numBytes: scala.Int): scala.Unit
  private def copyJni(src: scala.Array[scala.Int], srcOffset: scala.Int, dst: java.nio.Buffer, dstOffset: scala.Int, numBytes: scala.Int): scala.Unit
  private def copyJni(src: scala.Array[scala.Long], srcOffset: scala.Int, dst: java.nio.Buffer, dstOffset: scala.Int, numBytes: scala.Int): scala.Unit
  private def copyJni(src: scala.Array[scala.Float], srcOffset: scala.Int, dst: java.nio.Buffer, dstOffset: scala.Int, numBytes: scala.Int): scala.Unit
  private def copyJni(src: scala.Array[scala.Double], srcOffset: scala.Int, dst: java.nio.Buffer, dstOffset: scala.Int, numBytes: scala.Int): scala.Unit
  private def copyJni(src: java.nio.Buffer, srcOffset: scala.Int, dst: java.nio.Buffer, dstOffset: scala.Int, numBytes: scala.Int): scala.Unit
  private def transformV4M4Jni(data: java.nio.Buffer, strideInBytes: scala.Int, count: scala.Int, matrix: scala.Array[scala.Float], offsetInBytes: scala.Int): scala.Unit
  private def transformV4M4Jni(data: scala.Array[scala.Float], strideInBytes: scala.Int, count: scala.Int, matrix: scala.Array[scala.Float], offsetInBytes: scala.Int): scala.Unit
  private def transformV3M4Jni(data: java.nio.Buffer, strideInBytes: scala.Int, count: scala.Int, matrix: scala.Array[scala.Float], offsetInBytes: scala.Int): scala.Unit
  private def transformV3M4Jni(data: scala.Array[scala.Float], strideInBytes: scala.Int, count: scala.Int, matrix: scala.Array[scala.Float], offsetInBytes: scala.Int): scala.Unit
  private def transformV2M4Jni(data: java.nio.Buffer, strideInBytes: scala.Int, count: scala.Int, matrix: scala.Array[scala.Float], offsetInBytes: scala.Int): scala.Unit
  private def transformV2M4Jni(data: scala.Array[scala.Float], strideInBytes: scala.Int, count: scala.Int, matrix: scala.Array[scala.Float], offsetInBytes: scala.Int): scala.Unit
  private def transformV3M3Jni(data: java.nio.Buffer, strideInBytes: scala.Int, count: scala.Int, matrix: scala.Array[scala.Float], offsetInBytes: scala.Int): scala.Unit
  private def transformV3M3Jni(data: scala.Array[scala.Float], strideInBytes: scala.Int, count: scala.Int, matrix: scala.Array[scala.Float], offsetInBytes: scala.Int): scala.Unit
  private def transformV2M3Jni(data: java.nio.Buffer, strideInBytes: scala.Int, count: scala.Int, matrix: scala.Array[scala.Float], offsetInBytes: scala.Int): scala.Unit
  private def transformV2M3Jni(data: scala.Array[scala.Float], strideInBytes: scala.Int, count: scala.Int, matrix: scala.Array[scala.Float], offsetInBytes: scala.Int): scala.Unit
  private def find(vertex: java.nio.Buffer, vertexOffsetInBytes: scala.Int, strideInBytes: scala.Int, vertices: java.nio.Buffer, verticesOffsetInBytes: scala.Int, numVertices: scala.Int): scala.Long
  private def find(vertex: scala.Array[scala.Float], vertexOffsetInBytes: scala.Int, strideInBytes: scala.Int, vertices: java.nio.Buffer, verticesOffsetInBytes: scala.Int, numVertices: scala.Int): scala.Long
  private def find(vertex: java.nio.Buffer, vertexOffsetInBytes: scala.Int, strideInBytes: scala.Int, vertices: scala.Array[scala.Float], verticesOffsetInBytes: scala.Int, numVertices: scala.Int): scala.Long
  private def find(vertex: scala.Array[scala.Float], vertexOffsetInBytes: scala.Int, strideInBytes: scala.Int, vertices: scala.Array[scala.Float], verticesOffsetInBytes: scala.Int, numVertices: scala.Int): scala.Long
  private def find(vertex: java.nio.Buffer, vertexOffsetInBytes: scala.Int, strideInBytes: scala.Int, vertices: java.nio.Buffer, verticesOffsetInBytes: scala.Int, numVertices: scala.Int, epsilon: scala.Float): scala.Long
  private def find(vertex: scala.Array[scala.Float], vertexOffsetInBytes: scala.Int, strideInBytes: scala.Int, vertices: java.nio.Buffer, verticesOffsetInBytes: scala.Int, numVertices: scala.Int, epsilon: scala.Float): scala.Long
  private def find(vertex: java.nio.Buffer, vertexOffsetInBytes: scala.Int, strideInBytes: scala.Int, vertices: scala.Array[scala.Float], verticesOffsetInBytes: scala.Int, numVertices: scala.Int, epsilon: scala.Float): scala.Long
  private def find(vertex: scala.Array[scala.Float], vertexOffsetInBytes: scala.Int, strideInBytes: scala.Int, vertices: scala.Array[scala.Float], verticesOffsetInBytes: scala.Int, numVertices: scala.Int, epsilon: scala.Float): scala.Long
}