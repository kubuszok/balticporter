package com.badlogic.gdx.graphics.glutils

class IndexArray(maxIndices$arg: scala.Int) extends com.badlogic.gdx.graphics.glutils.IndexData {
  var buffer: java.nio.ShortBuffer = null.asInstanceOf[java.nio.ShortBuffer]
  var byteBuffer: java.nio.ByteBuffer = null.asInstanceOf[java.nio.ByteBuffer]
  private var empty: scala.Boolean = false
  var maxIndices: scala.Int = maxIndices$arg
  this.empty = maxIndices == 0
  if (this.empty) {
    maxIndices = 1
  } else ()
  this.byteBuffer = com.badlogic.gdx.utils.BufferUtils.newUnsafeByteBuffer(maxIndices * 2)
  this.buffer = this.byteBuffer.asShortBuffer()
  this.buffer.asInstanceOf[java.nio.Buffer].flip()
  this.byteBuffer.asInstanceOf[java.nio.Buffer].flip()
  override def getNumIndices(): scala.Int = {
    return if (this.empty) 0 else this.buffer.limit()
  }
  override def getNumMaxIndices(): scala.Int = {
    return if (this.empty) 0 else this.buffer.capacity()
  }
  override def setIndices(indices: scala.Array[scala.Short], offset: scala.Int, count: scala.Int): scala.Unit = {
    this.buffer.asInstanceOf[java.nio.Buffer].clear()
    this.buffer.put(indices, offset, count)
    this.buffer.asInstanceOf[java.nio.Buffer].flip()
    this.byteBuffer.asInstanceOf[java.nio.Buffer].position(0)
    this.byteBuffer.asInstanceOf[java.nio.Buffer].limit(count << 1)
  }
  override def setIndices(indices: java.nio.ShortBuffer): scala.Unit = {
    val pos: scala.Int = indices.position()
    this.buffer.asInstanceOf[java.nio.Buffer].clear()
    this.buffer.asInstanceOf[java.nio.Buffer].limit(indices.remaining())
    this.buffer.put(indices)
    this.buffer.asInstanceOf[java.nio.Buffer].flip()
    indices.asInstanceOf[java.nio.Buffer].position(pos)
    this.byteBuffer.asInstanceOf[java.nio.Buffer].position(0)
    this.byteBuffer.asInstanceOf[java.nio.Buffer].limit(this.buffer.limit() << 1)
  }
  @java.lang.Override
  override def updateIndices(targetOffset: scala.Int, indices: scala.Array[scala.Short], offset: scala.Int, count: scala.Int): scala.Unit = {
    val pos: scala.Int = this.byteBuffer.position()
    this.byteBuffer.asInstanceOf[java.nio.Buffer].position(targetOffset * 2)
    com.badlogic.gdx.utils.BufferUtils.copy(indices, offset, this.byteBuffer, count)
    this.byteBuffer.asInstanceOf[java.nio.Buffer].position(pos)
  }
  @java.lang.Override
  @java.lang.Deprecated
  override def getBuffer(): java.nio.ShortBuffer = {
    return this.buffer
  }
  @java.lang.Override
  override def getBuffer(forWriting: scala.Boolean): java.nio.ShortBuffer = {
    return this.buffer
  }
  override def bind(): scala.Unit = {
    ()
  }
  override def unbind(): scala.Unit = {
    ()
  }
  override def invalidate(): scala.Unit = {
    ()
  }
  override def dispose(): scala.Unit = {
    com.badlogic.gdx.utils.BufferUtils.disposeUnsafeByteBuffer(this.byteBuffer)
  }
}