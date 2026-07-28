package com.badlogic.gdx.graphics.glutils

class IndexBufferObjectSubData extends com.badlogic.gdx.graphics.glutils.IndexData {
  var buffer: java.nio.ShortBuffer = null.asInstanceOf[java.nio.ShortBuffer]
  var byteBuffer: java.nio.ByteBuffer = null.asInstanceOf[java.nio.ByteBuffer]
  var bufferHandle: scala.Int = 0
  var isDirect: scala.Boolean = false
  var isDirty: scala.Boolean = true
  var isBound: scala.Boolean = false
  var usage: scala.Int = 0
  def this(isStatic: scala.Boolean, maxIndices: scala.Int) = {
    this()
    this.byteBuffer = com.badlogic.gdx.utils.BufferUtils.newByteBuffer(maxIndices * 2)
    this.isDirect = true
    this.usage = if (isStatic) com.badlogic.gdx.graphics.GL20.GL_STATIC_DRAW else com.badlogic.gdx.graphics.GL20.GL_DYNAMIC_DRAW
    this.buffer = this.byteBuffer.asShortBuffer()
    this.buffer.asInstanceOf[java.nio.Buffer].flip()
    this.byteBuffer.asInstanceOf[java.nio.Buffer].flip()
    this.bufferHandle = this.createBufferObject()
  }
  def this(maxIndices: scala.Int) = {
    this()
    this.byteBuffer = com.badlogic.gdx.utils.BufferUtils.newByteBuffer(maxIndices * 2)
    this.isDirect = true
    this.usage = com.badlogic.gdx.graphics.GL20.GL_STATIC_DRAW
    this.buffer = this.byteBuffer.asShortBuffer()
    this.buffer.asInstanceOf[java.nio.Buffer].flip()
    this.byteBuffer.asInstanceOf[java.nio.Buffer].flip()
    this.bufferHandle = this.createBufferObject()
  }
  private def createBufferObject(): scala.Int = {
    val result: scala.Int = com.badlogic.gdx.Gdx.gl20.glGenBuffer()
    com.badlogic.gdx.Gdx.gl20.glBindBuffer(com.badlogic.gdx.graphics.GL20.GL_ELEMENT_ARRAY_BUFFER, result)
    com.badlogic.gdx.Gdx.gl20.glBufferData(com.badlogic.gdx.graphics.GL20.GL_ELEMENT_ARRAY_BUFFER, this.byteBuffer.capacity(), null, this.usage)
    com.badlogic.gdx.Gdx.gl20.glBindBuffer(com.badlogic.gdx.graphics.GL20.GL_ELEMENT_ARRAY_BUFFER, 0)
    return result
  }
  def getNumIndices(): scala.Int = {
    return this.buffer.limit()
  }
  def getNumMaxIndices(): scala.Int = {
    return this.buffer.capacity()
  }
  def setIndices(indices: scala.Array[scala.Short], offset: scala.Int, count: scala.Int): scala.Unit = {
    this.isDirty = true
    this.buffer.asInstanceOf[java.nio.Buffer].clear()
    this.buffer.put(indices, offset, count)
    this.buffer.asInstanceOf[java.nio.Buffer].flip()
    this.byteBuffer.asInstanceOf[java.nio.Buffer].position(0)
    this.byteBuffer.asInstanceOf[java.nio.Buffer].limit(count << 1)
    if (this.isBound) {
      com.badlogic.gdx.Gdx.gl20.glBufferSubData(com.badlogic.gdx.graphics.GL20.GL_ELEMENT_ARRAY_BUFFER, 0, this.byteBuffer.limit(), this.byteBuffer)
      this.isDirty = false
    } else ()
  }
  def setIndices(indices: java.nio.ShortBuffer): scala.Unit = {
    val pos: scala.Int = indices.position()
    this.isDirty = true
    this.buffer.asInstanceOf[java.nio.Buffer].clear()
    this.buffer.put(indices)
    this.buffer.asInstanceOf[java.nio.Buffer].flip()
    indices.asInstanceOf[java.nio.Buffer].position(pos)
    this.byteBuffer.asInstanceOf[java.nio.Buffer].position(0)
    this.byteBuffer.asInstanceOf[java.nio.Buffer].limit(this.buffer.limit() << 1)
    if (this.isBound) {
      com.badlogic.gdx.Gdx.gl20.glBufferSubData(com.badlogic.gdx.graphics.GL20.GL_ELEMENT_ARRAY_BUFFER, 0, this.byteBuffer.limit(), this.byteBuffer)
      this.isDirty = false
    } else ()
  }
  @java.lang.Override
  def updateIndices(targetOffset: scala.Int, indices: scala.Array[scala.Short], offset: scala.Int, count: scala.Int): scala.Unit = {
    this.isDirty = true
    val pos: scala.Int = this.byteBuffer.position()
    this.byteBuffer.asInstanceOf[java.nio.Buffer].position(targetOffset * 2)
    com.badlogic.gdx.utils.BufferUtils.copy(indices, offset, this.byteBuffer, count)
    this.byteBuffer.asInstanceOf[java.nio.Buffer].position(pos)
    this.buffer.asInstanceOf[java.nio.Buffer].position(0)
    if (this.isBound) {
      com.badlogic.gdx.Gdx.gl20.glBufferSubData(com.badlogic.gdx.graphics.GL20.GL_ELEMENT_ARRAY_BUFFER, 0, this.byteBuffer.limit(), this.byteBuffer)
      this.isDirty = false
    } else ()
  }
  @java.lang.Override
  @java.lang.Deprecated
  def getBuffer(): java.nio.ShortBuffer = {
    this.isDirty = true
    return this.buffer
  }
  @java.lang.Override
  def getBuffer(forWriting: scala.Boolean): java.nio.ShortBuffer = {
    this.isDirty = this.isDirty | forWriting
    return this.buffer
  }
  def bind(): scala.Unit = {
    if (this.bufferHandle == 0) {
      throw new com.badlogic.gdx.utils.GdxRuntimeException("IndexBufferObject cannot be used after it has been disposed.")
    } else ()
    com.badlogic.gdx.Gdx.gl20.glBindBuffer(com.badlogic.gdx.graphics.GL20.GL_ELEMENT_ARRAY_BUFFER, this.bufferHandle)
    if (this.isDirty) {
      this.byteBuffer.asInstanceOf[java.nio.Buffer].limit(this.buffer.limit() * 2)
      com.badlogic.gdx.Gdx.gl20.glBufferSubData(com.badlogic.gdx.graphics.GL20.GL_ELEMENT_ARRAY_BUFFER, 0, this.byteBuffer.limit(), this.byteBuffer)
      this.isDirty = false
    } else ()
    this.isBound = true
  }
  def unbind(): scala.Unit = {
    com.badlogic.gdx.Gdx.gl20.glBindBuffer(com.badlogic.gdx.graphics.GL20.GL_ELEMENT_ARRAY_BUFFER, 0)
    this.isBound = false
  }
  def invalidate(): scala.Unit = {
    this.bufferHandle = this.createBufferObject()
    this.isDirty = true
  }
  def dispose(): scala.Unit = {
    val gl: com.badlogic.gdx.graphics.GL20 = com.badlogic.gdx.Gdx.gl20
    gl.glBindBuffer(com.badlogic.gdx.graphics.GL20.GL_ELEMENT_ARRAY_BUFFER, 0)
    gl.glDeleteBuffer(this.bufferHandle)
    this.bufferHandle = 0
  }
}