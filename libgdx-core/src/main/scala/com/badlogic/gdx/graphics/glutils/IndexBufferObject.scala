package com.badlogic.gdx.graphics.glutils

class IndexBufferObject extends com.badlogic.gdx.graphics.glutils.IndexData {
  var buffer: java.nio.ShortBuffer = null.asInstanceOf[java.nio.ShortBuffer]
  var byteBuffer: java.nio.ByteBuffer = null.asInstanceOf[java.nio.ByteBuffer]
  var ownsBuffer: scala.Boolean = false
  var bufferHandle: scala.Int = 0
  var isDirect: scala.Boolean = false
  var isDirty: scala.Boolean = true
  var isBound: scala.Boolean = false
  var usage: scala.Int = 0
  private var empty: scala.Boolean = false
  def this(isStatic: scala.Boolean, maxIndices$arg: scala.Int) = {
    this()
    var maxIndices: scala.Int = maxIndices$arg
    this.empty = maxIndices == 0
    if (this.empty) {
      maxIndices = 1
    } else ()
    this.byteBuffer = com.badlogic.gdx.utils.BufferUtils.newUnsafeByteBuffer(maxIndices * 2)
    this.isDirect = true
    this.buffer = this.byteBuffer.asShortBuffer()
    this.ownsBuffer = true
    this.buffer.asInstanceOf[java.nio.Buffer].flip()
    this.byteBuffer.asInstanceOf[java.nio.Buffer].flip()
    this.bufferHandle = com.badlogic.gdx.Gdx.gl20.glGenBuffer()
    this.usage = if (isStatic) com.badlogic.gdx.graphics.GL20.GL_STATIC_DRAW else com.badlogic.gdx.graphics.GL20.GL_DYNAMIC_DRAW
  }
  def this(isStatic: scala.Boolean, data: java.nio.ByteBuffer) = {
    this()
    this.empty = data.limit() == 0
    this.byteBuffer = data
    this.isDirect = true
    this.buffer = this.byteBuffer.asShortBuffer()
    this.ownsBuffer = false
    this.bufferHandle = com.badlogic.gdx.Gdx.gl20.glGenBuffer()
    this.usage = if (isStatic) com.badlogic.gdx.graphics.GL20.GL_STATIC_DRAW else com.badlogic.gdx.graphics.GL20.GL_DYNAMIC_DRAW
  }
  def this(maxIndices: scala.Int) = {
    this(true, maxIndices)
  }
  def getNumIndices(): scala.Int = {
    return if (this.empty) 0 else this.buffer.limit()
  }
  def getNumMaxIndices(): scala.Int = {
    return if (this.empty) 0 else this.buffer.capacity()
  }
  def setIndices(indices: scala.Array[scala.Short], offset: scala.Int, count: scala.Int): scala.Unit = {
    this.isDirty = true
    this.buffer.asInstanceOf[java.nio.Buffer].clear()
    this.buffer.put(indices, offset, count)
    this.buffer.asInstanceOf[java.nio.Buffer].flip()
    this.byteBuffer.asInstanceOf[java.nio.Buffer].position(0)
    this.byteBuffer.asInstanceOf[java.nio.Buffer].limit(count << 1)
    if (this.isBound) {
      com.badlogic.gdx.Gdx.gl20.glBufferData(com.badlogic.gdx.graphics.GL20.GL_ELEMENT_ARRAY_BUFFER, this.byteBuffer.limit(), this.byteBuffer, this.usage)
      this.isDirty = false
    } else ()
  }
  def setIndices(indices: java.nio.ShortBuffer): scala.Unit = {
    this.isDirty = true
    val pos: scala.Int = indices.position()
    this.buffer.asInstanceOf[java.nio.Buffer].clear()
    this.buffer.put(indices)
    this.buffer.asInstanceOf[java.nio.Buffer].flip()
    indices.asInstanceOf[java.nio.Buffer].position(pos)
    this.byteBuffer.asInstanceOf[java.nio.Buffer].position(0)
    this.byteBuffer.asInstanceOf[java.nio.Buffer].limit(this.buffer.limit() << 1)
    if (this.isBound) {
      com.badlogic.gdx.Gdx.gl20.glBufferData(com.badlogic.gdx.graphics.GL20.GL_ELEMENT_ARRAY_BUFFER, this.byteBuffer.limit(), this.byteBuffer, this.usage)
      this.isDirty = false
    } else ()
  }
  def updateIndices(targetOffset: scala.Int, indices: scala.Array[scala.Short], offset: scala.Int, count: scala.Int): scala.Unit = {
    this.isDirty = true
    val pos: scala.Int = this.byteBuffer.position()
    this.byteBuffer.asInstanceOf[java.nio.Buffer].position(targetOffset * 2)
    com.badlogic.gdx.utils.BufferUtils.copy(indices, offset, this.byteBuffer, count)
    this.byteBuffer.asInstanceOf[java.nio.Buffer].position(pos)
    this.buffer.asInstanceOf[java.nio.Buffer].position(0)
    if (this.isBound) {
      com.badlogic.gdx.Gdx.gl20.glBufferData(com.badlogic.gdx.graphics.GL20.GL_ELEMENT_ARRAY_BUFFER, this.byteBuffer.limit(), this.byteBuffer, this.usage)
      this.isDirty = false
    } else ()
  }
  def getBuffer(): java.nio.ShortBuffer = {
    this.isDirty = true
    return this.buffer
  }
  def getBuffer(forWriting: scala.Boolean): java.nio.ShortBuffer = {
    this.isDirty = this.isDirty | forWriting
    return this.buffer
  }
  def bind(): scala.Unit = {
    if (this.bufferHandle == 0) {
      throw new com.badlogic.gdx.utils.GdxRuntimeException("No buffer allocated!")
    } else ()
    com.badlogic.gdx.Gdx.gl20.glBindBuffer(com.badlogic.gdx.graphics.GL20.GL_ELEMENT_ARRAY_BUFFER, this.bufferHandle)
    if (this.isDirty) {
      this.byteBuffer.asInstanceOf[java.nio.Buffer].limit(this.buffer.limit() * 2)
      com.badlogic.gdx.Gdx.gl20.glBufferData(com.badlogic.gdx.graphics.GL20.GL_ELEMENT_ARRAY_BUFFER, this.byteBuffer.limit(), this.byteBuffer, this.usage)
      this.isDirty = false
    } else ()
    this.isBound = true
  }
  def unbind(): scala.Unit = {
    com.badlogic.gdx.Gdx.gl20.glBindBuffer(com.badlogic.gdx.graphics.GL20.GL_ELEMENT_ARRAY_BUFFER, 0)
    this.isBound = false
  }
  def invalidate(): scala.Unit = {
    this.bufferHandle = com.badlogic.gdx.Gdx.gl20.glGenBuffer()
    this.isDirty = true
  }
  def dispose(): scala.Unit = {
    com.badlogic.gdx.Gdx.gl20.glBindBuffer(com.badlogic.gdx.graphics.GL20.GL_ELEMENT_ARRAY_BUFFER, 0)
    com.badlogic.gdx.Gdx.gl20.glDeleteBuffer(this.bufferHandle)
    this.bufferHandle = 0
    if (this.ownsBuffer) {
      com.badlogic.gdx.utils.BufferUtils.disposeUnsafeByteBuffer(this.byteBuffer)
    } else ()
  }
}