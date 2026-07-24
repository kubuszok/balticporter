package com.badlogic.gdx.graphics.glutils

class IndexArray extends com.badlogic.gdx.graphics.glutils.IndexData {
  var buffer: java.nio.ShortBuffer = null.asInstanceOf[java.nio.ShortBuffer]
  var byteBuffer: java.nio.ByteBuffer = null.asInstanceOf[java.nio.ByteBuffer]
  private var empty: scala.Boolean = false
  def this(maxIndices: scala.Int) = {
    this()
    this.empty = maxIndices == 0
    if (this.empty) {
      maxIndices = 1
    } else ()
    this.byteBuffer = com.badlogic.gdx.utils.BufferUtils.newUnsafeByteBuffer(maxIndices * 2)
    this.buffer = this.byteBuffer.asShortBuffer()
    this.buffer.asInstanceOf[java.nio.Buffer].flip()
    this.byteBuffer.asInstanceOf[java.nio.Buffer].flip()
  }
  def getNumIndices(): scala.Int = {
    return if (this.empty) 0 else this.buffer.limit()
  }
  def getNumMaxIndices(): scala.Int = {
    return if (this.empty) 0 else this.buffer.capacity()
  }
  def setIndices(indices: scala.Array[scala.Short], offset: scala.Int, count: scala.Int): scala.Unit = {
    this.buffer.asInstanceOf[java.nio.Buffer].clear()
    this.buffer.put(indices, offset, count)
    this.buffer.asInstanceOf[java.nio.Buffer].flip()
    this.byteBuffer.asInstanceOf[java.nio.Buffer].position(0)
    this.byteBuffer.asInstanceOf[java.nio.Buffer].limit(count << 1)
  }
  def setIndices(indices: java.nio.ShortBuffer): scala.Unit = {
    val pos: scala.Int = indices.position()
    this.buffer.asInstanceOf[java.nio.Buffer].clear()
    this.buffer.asInstanceOf[java.nio.Buffer].limit(indices.remaining())
    this.buffer.put(indices)
    this.buffer.asInstanceOf[java.nio.Buffer].flip()
    indices.asInstanceOf[java.nio.Buffer].position(pos)
    this.byteBuffer.asInstanceOf[java.nio.Buffer].position(0)
    this.byteBuffer.asInstanceOf[java.nio.Buffer].limit(this.buffer.limit() << 1)
  }
  def updateIndices(targetOffset: scala.Int, indices: scala.Array[scala.Short], offset: scala.Int, count: scala.Int): scala.Unit = {
    val pos: scala.Int = this.byteBuffer.position()
    this.byteBuffer.asInstanceOf[java.nio.Buffer].position(targetOffset * 2)
    com.badlogic.gdx.utils.BufferUtils.copy(indices, offset, this.byteBuffer, count)
    this.byteBuffer.asInstanceOf[java.nio.Buffer].position(pos)
  }
  def getBuffer(): java.nio.ShortBuffer = {
    return this.buffer
  }
  def getBuffer(forWriting: scala.Boolean): java.nio.ShortBuffer = {
    return this.buffer
  }
  def bind(): scala.Unit = {
    ()
  }
  def unbind(): scala.Unit = {
    ()
  }
  def invalidate(): scala.Unit = {
    ()
  }
  def dispose(): scala.Unit = {
    com.badlogic.gdx.utils.BufferUtils.disposeUnsafeByteBuffer(this.byteBuffer)
  }
}