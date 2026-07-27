package com.badlogic.gdx.utils

object BufferUtils {
  var unsafeBuffers: com.badlogic.gdx.utils.Array[java.nio.ByteBuffer] = new com.badlogic.gdx.utils.Array[java.nio.ByteBuffer]()
  var allocatedUnsafe: scala.Int = 0
  private val freeMemory$41781$handle: java.lang.invoke.MethodHandle = java.lang.foreign.Linker.nativeLinker().downcallHandle(java.lang.foreign.Linker.nativeLinker().defaultLookup().find("freeMemory").orElseThrow(), java.lang.foreign.FunctionDescriptor.ofVoid(java.lang.foreign.ValueLayout.ADDRESS))
  private val newDisposableByteBuffer$41786$handle: java.lang.invoke.MethodHandle = java.lang.foreign.Linker.nativeLinker().downcallHandle(java.lang.foreign.Linker.nativeLinker().defaultLookup().find("newDisposableByteBuffer").orElseThrow(), java.lang.foreign.FunctionDescriptor.of(java.lang.foreign.ValueLayout.ADDRESS, java.lang.foreign.ValueLayout.JAVA_INT))
  private val getBufferAddress$41789$handle: java.lang.invoke.MethodHandle = java.lang.foreign.Linker.nativeLinker().downcallHandle(java.lang.foreign.Linker.nativeLinker().defaultLookup().find("getBufferAddress").orElseThrow(), java.lang.foreign.FunctionDescriptor.of(java.lang.foreign.ValueLayout.JAVA_LONG, java.lang.foreign.ValueLayout.ADDRESS))
  private val clear$41795$handle: java.lang.invoke.MethodHandle = java.lang.foreign.Linker.nativeLinker().downcallHandle(java.lang.foreign.Linker.nativeLinker().defaultLookup().find("clear").orElseThrow(), java.lang.foreign.FunctionDescriptor.ofVoid(java.lang.foreign.ValueLayout.ADDRESS, java.lang.foreign.ValueLayout.JAVA_INT))
  private val copyJni$41563$handle: java.lang.invoke.MethodHandle = java.lang.foreign.Linker.nativeLinker().downcallHandle(java.lang.foreign.Linker.nativeLinker().defaultLookup().find("copyJni").orElseThrow(), java.lang.foreign.FunctionDescriptor.ofVoid(java.lang.foreign.ValueLayout.ADDRESS, java.lang.foreign.ValueLayout.ADDRESS, java.lang.foreign.ValueLayout.JAVA_INT, java.lang.foreign.ValueLayout.JAVA_INT))
  private val copyJni$41570$handle: java.lang.invoke.MethodHandle = java.lang.foreign.Linker.nativeLinker().downcallHandle(java.lang.foreign.Linker.nativeLinker().defaultLookup().find("copyJni").orElseThrow(), java.lang.foreign.FunctionDescriptor.ofVoid(java.lang.foreign.ValueLayout.ADDRESS, java.lang.foreign.ValueLayout.JAVA_INT, java.lang.foreign.ValueLayout.ADDRESS, java.lang.foreign.ValueLayout.JAVA_INT, java.lang.foreign.ValueLayout.JAVA_INT))
  private val copyJni$41582$handle: java.lang.invoke.MethodHandle = java.lang.foreign.Linker.nativeLinker().downcallHandle(java.lang.foreign.Linker.nativeLinker().defaultLookup().find("copyJni").orElseThrow(), java.lang.foreign.FunctionDescriptor.ofVoid(java.lang.foreign.ValueLayout.ADDRESS, java.lang.foreign.ValueLayout.JAVA_INT, java.lang.foreign.ValueLayout.ADDRESS, java.lang.foreign.ValueLayout.JAVA_INT, java.lang.foreign.ValueLayout.JAVA_INT))
  private val copyJni$41576$handle: java.lang.invoke.MethodHandle = java.lang.foreign.Linker.nativeLinker().downcallHandle(java.lang.foreign.Linker.nativeLinker().defaultLookup().find("copyJni").orElseThrow(), java.lang.foreign.FunctionDescriptor.ofVoid(java.lang.foreign.ValueLayout.ADDRESS, java.lang.foreign.ValueLayout.JAVA_INT, java.lang.foreign.ValueLayout.ADDRESS, java.lang.foreign.ValueLayout.JAVA_INT, java.lang.foreign.ValueLayout.JAVA_INT))
  private val copyJni$41588$handle: java.lang.invoke.MethodHandle = java.lang.foreign.Linker.nativeLinker().downcallHandle(java.lang.foreign.Linker.nativeLinker().defaultLookup().find("copyJni").orElseThrow(), java.lang.foreign.FunctionDescriptor.ofVoid(java.lang.foreign.ValueLayout.ADDRESS, java.lang.foreign.ValueLayout.JAVA_INT, java.lang.foreign.ValueLayout.ADDRESS, java.lang.foreign.ValueLayout.JAVA_INT, java.lang.foreign.ValueLayout.JAVA_INT))
  private val copyJni$41594$handle: java.lang.invoke.MethodHandle = java.lang.foreign.Linker.nativeLinker().downcallHandle(java.lang.foreign.Linker.nativeLinker().defaultLookup().find("copyJni").orElseThrow(), java.lang.foreign.FunctionDescriptor.ofVoid(java.lang.foreign.ValueLayout.ADDRESS, java.lang.foreign.ValueLayout.JAVA_INT, java.lang.foreign.ValueLayout.ADDRESS, java.lang.foreign.ValueLayout.JAVA_INT, java.lang.foreign.ValueLayout.JAVA_INT))
  private val copyJni$41599$handle: java.lang.invoke.MethodHandle = java.lang.foreign.Linker.nativeLinker().downcallHandle(java.lang.foreign.Linker.nativeLinker().defaultLookup().find("copyJni").orElseThrow(), java.lang.foreign.FunctionDescriptor.ofVoid(java.lang.foreign.ValueLayout.ADDRESS, java.lang.foreign.ValueLayout.JAVA_INT, java.lang.foreign.ValueLayout.ADDRESS, java.lang.foreign.ValueLayout.JAVA_INT, java.lang.foreign.ValueLayout.JAVA_INT))
  private val copyJni$41605$handle: java.lang.invoke.MethodHandle = java.lang.foreign.Linker.nativeLinker().downcallHandle(java.lang.foreign.Linker.nativeLinker().defaultLookup().find("copyJni").orElseThrow(), java.lang.foreign.FunctionDescriptor.ofVoid(java.lang.foreign.ValueLayout.ADDRESS, java.lang.foreign.ValueLayout.JAVA_INT, java.lang.foreign.ValueLayout.ADDRESS, java.lang.foreign.ValueLayout.JAVA_INT, java.lang.foreign.ValueLayout.JAVA_INT))
  private val copyJni$41636$handle: java.lang.invoke.MethodHandle = java.lang.foreign.Linker.nativeLinker().downcallHandle(java.lang.foreign.Linker.nativeLinker().defaultLookup().find("copyJni").orElseThrow(), java.lang.foreign.FunctionDescriptor.ofVoid(java.lang.foreign.ValueLayout.ADDRESS, java.lang.foreign.ValueLayout.JAVA_INT, java.lang.foreign.ValueLayout.ADDRESS, java.lang.foreign.ValueLayout.JAVA_INT, java.lang.foreign.ValueLayout.JAVA_INT))
  private val transformV4M4Jni$41657$handle: java.lang.invoke.MethodHandle = java.lang.foreign.Linker.nativeLinker().downcallHandle(java.lang.foreign.Linker.nativeLinker().defaultLookup().find("transformV4M4Jni").orElseThrow(), java.lang.foreign.FunctionDescriptor.ofVoid(java.lang.foreign.ValueLayout.ADDRESS, java.lang.foreign.ValueLayout.JAVA_INT, java.lang.foreign.ValueLayout.JAVA_INT, java.lang.foreign.ValueLayout.ADDRESS, java.lang.foreign.ValueLayout.JAVA_INT))
  private val transformV4M4Jni$41666$handle: java.lang.invoke.MethodHandle = java.lang.foreign.Linker.nativeLinker().downcallHandle(java.lang.foreign.Linker.nativeLinker().defaultLookup().find("transformV4M4Jni").orElseThrow(), java.lang.foreign.FunctionDescriptor.ofVoid(java.lang.foreign.ValueLayout.ADDRESS, java.lang.foreign.ValueLayout.JAVA_INT, java.lang.foreign.ValueLayout.JAVA_INT, java.lang.foreign.ValueLayout.ADDRESS, java.lang.foreign.ValueLayout.JAVA_INT))
  private val transformV3M4Jni$41658$handle: java.lang.invoke.MethodHandle = java.lang.foreign.Linker.nativeLinker().downcallHandle(java.lang.foreign.Linker.nativeLinker().defaultLookup().find("transformV3M4Jni").orElseThrow(), java.lang.foreign.FunctionDescriptor.ofVoid(java.lang.foreign.ValueLayout.ADDRESS, java.lang.foreign.ValueLayout.JAVA_INT, java.lang.foreign.ValueLayout.JAVA_INT, java.lang.foreign.ValueLayout.ADDRESS, java.lang.foreign.ValueLayout.JAVA_INT))
  private val transformV3M4Jni$41667$handle: java.lang.invoke.MethodHandle = java.lang.foreign.Linker.nativeLinker().downcallHandle(java.lang.foreign.Linker.nativeLinker().defaultLookup().find("transformV3M4Jni").orElseThrow(), java.lang.foreign.FunctionDescriptor.ofVoid(java.lang.foreign.ValueLayout.ADDRESS, java.lang.foreign.ValueLayout.JAVA_INT, java.lang.foreign.ValueLayout.JAVA_INT, java.lang.foreign.ValueLayout.ADDRESS, java.lang.foreign.ValueLayout.JAVA_INT))
  private val transformV2M4Jni$41659$handle: java.lang.invoke.MethodHandle = java.lang.foreign.Linker.nativeLinker().downcallHandle(java.lang.foreign.Linker.nativeLinker().defaultLookup().find("transformV2M4Jni").orElseThrow(), java.lang.foreign.FunctionDescriptor.ofVoid(java.lang.foreign.ValueLayout.ADDRESS, java.lang.foreign.ValueLayout.JAVA_INT, java.lang.foreign.ValueLayout.JAVA_INT, java.lang.foreign.ValueLayout.ADDRESS, java.lang.foreign.ValueLayout.JAVA_INT))
  private val transformV2M4Jni$41668$handle: java.lang.invoke.MethodHandle = java.lang.foreign.Linker.nativeLinker().downcallHandle(java.lang.foreign.Linker.nativeLinker().defaultLookup().find("transformV2M4Jni").orElseThrow(), java.lang.foreign.FunctionDescriptor.ofVoid(java.lang.foreign.ValueLayout.ADDRESS, java.lang.foreign.ValueLayout.JAVA_INT, java.lang.foreign.ValueLayout.JAVA_INT, java.lang.foreign.ValueLayout.ADDRESS, java.lang.foreign.ValueLayout.JAVA_INT))
  private val transformV3M3Jni$41689$handle: java.lang.invoke.MethodHandle = java.lang.foreign.Linker.nativeLinker().downcallHandle(java.lang.foreign.Linker.nativeLinker().defaultLookup().find("transformV3M3Jni").orElseThrow(), java.lang.foreign.FunctionDescriptor.ofVoid(java.lang.foreign.ValueLayout.ADDRESS, java.lang.foreign.ValueLayout.JAVA_INT, java.lang.foreign.ValueLayout.JAVA_INT, java.lang.foreign.ValueLayout.ADDRESS, java.lang.foreign.ValueLayout.JAVA_INT))
  private val transformV3M3Jni$41697$handle: java.lang.invoke.MethodHandle = java.lang.foreign.Linker.nativeLinker().downcallHandle(java.lang.foreign.Linker.nativeLinker().defaultLookup().find("transformV3M3Jni").orElseThrow(), java.lang.foreign.FunctionDescriptor.ofVoid(java.lang.foreign.ValueLayout.ADDRESS, java.lang.foreign.ValueLayout.JAVA_INT, java.lang.foreign.ValueLayout.JAVA_INT, java.lang.foreign.ValueLayout.ADDRESS, java.lang.foreign.ValueLayout.JAVA_INT))
  private val transformV2M3Jni$41690$handle: java.lang.invoke.MethodHandle = java.lang.foreign.Linker.nativeLinker().downcallHandle(java.lang.foreign.Linker.nativeLinker().defaultLookup().find("transformV2M3Jni").orElseThrow(), java.lang.foreign.FunctionDescriptor.ofVoid(java.lang.foreign.ValueLayout.ADDRESS, java.lang.foreign.ValueLayout.JAVA_INT, java.lang.foreign.ValueLayout.JAVA_INT, java.lang.foreign.ValueLayout.ADDRESS, java.lang.foreign.ValueLayout.JAVA_INT))
  private val transformV2M3Jni$41698$handle: java.lang.invoke.MethodHandle = java.lang.foreign.Linker.nativeLinker().downcallHandle(java.lang.foreign.Linker.nativeLinker().defaultLookup().find("transformV2M3Jni").orElseThrow(), java.lang.foreign.FunctionDescriptor.ofVoid(java.lang.foreign.ValueLayout.ADDRESS, java.lang.foreign.ValueLayout.JAVA_INT, java.lang.foreign.ValueLayout.JAVA_INT, java.lang.foreign.ValueLayout.ADDRESS, java.lang.foreign.ValueLayout.JAVA_INT))
  private val find$41704$handle: java.lang.invoke.MethodHandle = java.lang.foreign.Linker.nativeLinker().downcallHandle(java.lang.foreign.Linker.nativeLinker().defaultLookup().find("find").orElseThrow(), java.lang.foreign.FunctionDescriptor.of(java.lang.foreign.ValueLayout.JAVA_LONG, java.lang.foreign.ValueLayout.ADDRESS, java.lang.foreign.ValueLayout.JAVA_INT, java.lang.foreign.ValueLayout.JAVA_INT, java.lang.foreign.ValueLayout.ADDRESS, java.lang.foreign.ValueLayout.JAVA_INT, java.lang.foreign.ValueLayout.JAVA_INT))
  private val find$41710$handle: java.lang.invoke.MethodHandle = java.lang.foreign.Linker.nativeLinker().downcallHandle(java.lang.foreign.Linker.nativeLinker().defaultLookup().find("find").orElseThrow(), java.lang.foreign.FunctionDescriptor.of(java.lang.foreign.ValueLayout.JAVA_LONG, java.lang.foreign.ValueLayout.ADDRESS, java.lang.foreign.ValueLayout.JAVA_INT, java.lang.foreign.ValueLayout.JAVA_INT, java.lang.foreign.ValueLayout.ADDRESS, java.lang.foreign.ValueLayout.JAVA_INT, java.lang.foreign.ValueLayout.JAVA_INT))
  private val find$41716$handle: java.lang.invoke.MethodHandle = java.lang.foreign.Linker.nativeLinker().downcallHandle(java.lang.foreign.Linker.nativeLinker().defaultLookup().find("find").orElseThrow(), java.lang.foreign.FunctionDescriptor.of(java.lang.foreign.ValueLayout.JAVA_LONG, java.lang.foreign.ValueLayout.ADDRESS, java.lang.foreign.ValueLayout.JAVA_INT, java.lang.foreign.ValueLayout.JAVA_INT, java.lang.foreign.ValueLayout.ADDRESS, java.lang.foreign.ValueLayout.JAVA_INT, java.lang.foreign.ValueLayout.JAVA_INT))
  private val find$41722$handle: java.lang.invoke.MethodHandle = java.lang.foreign.Linker.nativeLinker().downcallHandle(java.lang.foreign.Linker.nativeLinker().defaultLookup().find("find").orElseThrow(), java.lang.foreign.FunctionDescriptor.of(java.lang.foreign.ValueLayout.JAVA_LONG, java.lang.foreign.ValueLayout.ADDRESS, java.lang.foreign.ValueLayout.JAVA_INT, java.lang.foreign.ValueLayout.JAVA_INT, java.lang.foreign.ValueLayout.ADDRESS, java.lang.foreign.ValueLayout.JAVA_INT, java.lang.foreign.ValueLayout.JAVA_INT))
  private val find$41729$handle: java.lang.invoke.MethodHandle = java.lang.foreign.Linker.nativeLinker().downcallHandle(java.lang.foreign.Linker.nativeLinker().defaultLookup().find("find").orElseThrow(), java.lang.foreign.FunctionDescriptor.of(java.lang.foreign.ValueLayout.JAVA_LONG, java.lang.foreign.ValueLayout.ADDRESS, java.lang.foreign.ValueLayout.JAVA_INT, java.lang.foreign.ValueLayout.JAVA_INT, java.lang.foreign.ValueLayout.ADDRESS, java.lang.foreign.ValueLayout.JAVA_INT, java.lang.foreign.ValueLayout.JAVA_INT, java.lang.foreign.ValueLayout.JAVA_FLOAT))
  private val find$41736$handle: java.lang.invoke.MethodHandle = java.lang.foreign.Linker.nativeLinker().downcallHandle(java.lang.foreign.Linker.nativeLinker().defaultLookup().find("find").orElseThrow(), java.lang.foreign.FunctionDescriptor.of(java.lang.foreign.ValueLayout.JAVA_LONG, java.lang.foreign.ValueLayout.ADDRESS, java.lang.foreign.ValueLayout.JAVA_INT, java.lang.foreign.ValueLayout.JAVA_INT, java.lang.foreign.ValueLayout.ADDRESS, java.lang.foreign.ValueLayout.JAVA_INT, java.lang.foreign.ValueLayout.JAVA_INT, java.lang.foreign.ValueLayout.JAVA_FLOAT))
  private val find$41743$handle: java.lang.invoke.MethodHandle = java.lang.foreign.Linker.nativeLinker().downcallHandle(java.lang.foreign.Linker.nativeLinker().defaultLookup().find("find").orElseThrow(), java.lang.foreign.FunctionDescriptor.of(java.lang.foreign.ValueLayout.JAVA_LONG, java.lang.foreign.ValueLayout.ADDRESS, java.lang.foreign.ValueLayout.JAVA_INT, java.lang.foreign.ValueLayout.JAVA_INT, java.lang.foreign.ValueLayout.ADDRESS, java.lang.foreign.ValueLayout.JAVA_INT, java.lang.foreign.ValueLayout.JAVA_INT, java.lang.foreign.ValueLayout.JAVA_FLOAT))
  private val find$41750$handle: java.lang.invoke.MethodHandle = java.lang.foreign.Linker.nativeLinker().downcallHandle(java.lang.foreign.Linker.nativeLinker().defaultLookup().find("find").orElseThrow(), java.lang.foreign.FunctionDescriptor.of(java.lang.foreign.ValueLayout.JAVA_LONG, java.lang.foreign.ValueLayout.ADDRESS, java.lang.foreign.ValueLayout.JAVA_INT, java.lang.foreign.ValueLayout.JAVA_INT, java.lang.foreign.ValueLayout.ADDRESS, java.lang.foreign.ValueLayout.JAVA_INT, java.lang.foreign.ValueLayout.JAVA_INT, java.lang.foreign.ValueLayout.JAVA_FLOAT))
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
  private def freeMemory(buffer: java.nio.ByteBuffer): scala.Unit = { freeMemory$41781$handle.invokeExact(buffer); () }
  private def newDisposableByteBuffer(numBytes: scala.Int): java.nio.ByteBuffer = newDisposableByteBuffer$41786$handle.invokeExact(numBytes).asInstanceOf[java.nio.ByteBuffer]
  private def getBufferAddress(buffer: java.nio.Buffer): scala.Long = getBufferAddress$41789$handle.invokeExact(buffer).asInstanceOf[scala.Long]
  def clear(buffer: java.nio.ByteBuffer, numBytes: scala.Int): scala.Unit = { clear$41795$handle.invokeExact(buffer, numBytes); () }
  private def copyJni(src: scala.Array[scala.Float], dst: java.nio.Buffer, numFloats: scala.Int, offset: scala.Int): scala.Unit = { copyJni$41563$handle.invokeExact(src, dst, numFloats, offset); () }
  private def copyJni(src: scala.Array[scala.Byte], srcOffset: scala.Int, dst: java.nio.Buffer, dstOffset: scala.Int, numBytes: scala.Int): scala.Unit = { copyJni$41570$handle.invokeExact(src, srcOffset, dst, dstOffset, numBytes); () }
  private def copyJni(src: scala.Array[scala.Char], srcOffset: scala.Int, dst: java.nio.Buffer, dstOffset: scala.Int, numBytes: scala.Int): scala.Unit = { copyJni$41582$handle.invokeExact(src, srcOffset, dst, dstOffset, numBytes); () }
  private def copyJni(src: scala.Array[scala.Short], srcOffset: scala.Int, dst: java.nio.Buffer, dstOffset: scala.Int, numBytes: scala.Int): scala.Unit = { copyJni$41576$handle.invokeExact(src, srcOffset, dst, dstOffset, numBytes); () }
  private def copyJni(src: scala.Array[scala.Int], srcOffset: scala.Int, dst: java.nio.Buffer, dstOffset: scala.Int, numBytes: scala.Int): scala.Unit = { copyJni$41588$handle.invokeExact(src, srcOffset, dst, dstOffset, numBytes); () }
  private def copyJni(src: scala.Array[scala.Long], srcOffset: scala.Int, dst: java.nio.Buffer, dstOffset: scala.Int, numBytes: scala.Int): scala.Unit = { copyJni$41594$handle.invokeExact(src, srcOffset, dst, dstOffset, numBytes); () }
  private def copyJni(src: scala.Array[scala.Float], srcOffset: scala.Int, dst: java.nio.Buffer, dstOffset: scala.Int, numBytes: scala.Int): scala.Unit = { copyJni$41599$handle.invokeExact(src, srcOffset, dst, dstOffset, numBytes); () }
  private def copyJni(src: scala.Array[scala.Double], srcOffset: scala.Int, dst: java.nio.Buffer, dstOffset: scala.Int, numBytes: scala.Int): scala.Unit = { copyJni$41605$handle.invokeExact(src, srcOffset, dst, dstOffset, numBytes); () }
  private def copyJni(src: java.nio.Buffer, srcOffset: scala.Int, dst: java.nio.Buffer, dstOffset: scala.Int, numBytes: scala.Int): scala.Unit = { copyJni$41636$handle.invokeExact(src, srcOffset, dst, dstOffset, numBytes); () }
  private def transformV4M4Jni(data: java.nio.Buffer, strideInBytes: scala.Int, count: scala.Int, matrix: scala.Array[scala.Float], offsetInBytes: scala.Int): scala.Unit = { transformV4M4Jni$41657$handle.invokeExact(data, strideInBytes, count, matrix, offsetInBytes); () }
  private def transformV4M4Jni(data: scala.Array[scala.Float], strideInBytes: scala.Int, count: scala.Int, matrix: scala.Array[scala.Float], offsetInBytes: scala.Int): scala.Unit = { transformV4M4Jni$41666$handle.invokeExact(data, strideInBytes, count, matrix, offsetInBytes); () }
  private def transformV3M4Jni(data: java.nio.Buffer, strideInBytes: scala.Int, count: scala.Int, matrix: scala.Array[scala.Float], offsetInBytes: scala.Int): scala.Unit = { transformV3M4Jni$41658$handle.invokeExact(data, strideInBytes, count, matrix, offsetInBytes); () }
  private def transformV3M4Jni(data: scala.Array[scala.Float], strideInBytes: scala.Int, count: scala.Int, matrix: scala.Array[scala.Float], offsetInBytes: scala.Int): scala.Unit = { transformV3M4Jni$41667$handle.invokeExact(data, strideInBytes, count, matrix, offsetInBytes); () }
  private def transformV2M4Jni(data: java.nio.Buffer, strideInBytes: scala.Int, count: scala.Int, matrix: scala.Array[scala.Float], offsetInBytes: scala.Int): scala.Unit = { transformV2M4Jni$41659$handle.invokeExact(data, strideInBytes, count, matrix, offsetInBytes); () }
  private def transformV2M4Jni(data: scala.Array[scala.Float], strideInBytes: scala.Int, count: scala.Int, matrix: scala.Array[scala.Float], offsetInBytes: scala.Int): scala.Unit = { transformV2M4Jni$41668$handle.invokeExact(data, strideInBytes, count, matrix, offsetInBytes); () }
  private def transformV3M3Jni(data: java.nio.Buffer, strideInBytes: scala.Int, count: scala.Int, matrix: scala.Array[scala.Float], offsetInBytes: scala.Int): scala.Unit = { transformV3M3Jni$41689$handle.invokeExact(data, strideInBytes, count, matrix, offsetInBytes); () }
  private def transformV3M3Jni(data: scala.Array[scala.Float], strideInBytes: scala.Int, count: scala.Int, matrix: scala.Array[scala.Float], offsetInBytes: scala.Int): scala.Unit = { transformV3M3Jni$41697$handle.invokeExact(data, strideInBytes, count, matrix, offsetInBytes); () }
  private def transformV2M3Jni(data: java.nio.Buffer, strideInBytes: scala.Int, count: scala.Int, matrix: scala.Array[scala.Float], offsetInBytes: scala.Int): scala.Unit = { transformV2M3Jni$41690$handle.invokeExact(data, strideInBytes, count, matrix, offsetInBytes); () }
  private def transformV2M3Jni(data: scala.Array[scala.Float], strideInBytes: scala.Int, count: scala.Int, matrix: scala.Array[scala.Float], offsetInBytes: scala.Int): scala.Unit = { transformV2M3Jni$41698$handle.invokeExact(data, strideInBytes, count, matrix, offsetInBytes); () }
  private def find(vertex: java.nio.Buffer, vertexOffsetInBytes: scala.Int, strideInBytes: scala.Int, vertices: java.nio.Buffer, verticesOffsetInBytes: scala.Int, numVertices: scala.Int): scala.Long = find$41704$handle.invokeExact(vertex, vertexOffsetInBytes, strideInBytes, vertices, verticesOffsetInBytes, numVertices).asInstanceOf[scala.Long]
  private def find(vertex: scala.Array[scala.Float], vertexOffsetInBytes: scala.Int, strideInBytes: scala.Int, vertices: java.nio.Buffer, verticesOffsetInBytes: scala.Int, numVertices: scala.Int): scala.Long = find$41710$handle.invokeExact(vertex, vertexOffsetInBytes, strideInBytes, vertices, verticesOffsetInBytes, numVertices).asInstanceOf[scala.Long]
  private def find(vertex: java.nio.Buffer, vertexOffsetInBytes: scala.Int, strideInBytes: scala.Int, vertices: scala.Array[scala.Float], verticesOffsetInBytes: scala.Int, numVertices: scala.Int): scala.Long = find$41716$handle.invokeExact(vertex, vertexOffsetInBytes, strideInBytes, vertices, verticesOffsetInBytes, numVertices).asInstanceOf[scala.Long]
  private def find(vertex: scala.Array[scala.Float], vertexOffsetInBytes: scala.Int, strideInBytes: scala.Int, vertices: scala.Array[scala.Float], verticesOffsetInBytes: scala.Int, numVertices: scala.Int): scala.Long = find$41722$handle.invokeExact(vertex, vertexOffsetInBytes, strideInBytes, vertices, verticesOffsetInBytes, numVertices).asInstanceOf[scala.Long]
  private def find(vertex: java.nio.Buffer, vertexOffsetInBytes: scala.Int, strideInBytes: scala.Int, vertices: java.nio.Buffer, verticesOffsetInBytes: scala.Int, numVertices: scala.Int, epsilon: scala.Float): scala.Long = find$41729$handle.invokeExact(vertex, vertexOffsetInBytes, strideInBytes, vertices, verticesOffsetInBytes, numVertices, epsilon).asInstanceOf[scala.Long]
  private def find(vertex: scala.Array[scala.Float], vertexOffsetInBytes: scala.Int, strideInBytes: scala.Int, vertices: java.nio.Buffer, verticesOffsetInBytes: scala.Int, numVertices: scala.Int, epsilon: scala.Float): scala.Long = find$41736$handle.invokeExact(vertex, vertexOffsetInBytes, strideInBytes, vertices, verticesOffsetInBytes, numVertices, epsilon).asInstanceOf[scala.Long]
  private def find(vertex: java.nio.Buffer, vertexOffsetInBytes: scala.Int, strideInBytes: scala.Int, vertices: scala.Array[scala.Float], verticesOffsetInBytes: scala.Int, numVertices: scala.Int, epsilon: scala.Float): scala.Long = find$41743$handle.invokeExact(vertex, vertexOffsetInBytes, strideInBytes, vertices, verticesOffsetInBytes, numVertices, epsilon).asInstanceOf[scala.Long]
  private def find(vertex: scala.Array[scala.Float], vertexOffsetInBytes: scala.Int, strideInBytes: scala.Int, vertices: scala.Array[scala.Float], verticesOffsetInBytes: scala.Int, numVertices: scala.Int, epsilon: scala.Float): scala.Long = find$41750$handle.invokeExact(vertex, vertexOffsetInBytes, strideInBytes, vertices, verticesOffsetInBytes, numVertices, epsilon).asInstanceOf[scala.Long]
}