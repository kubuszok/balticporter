package com.badlogic.gdx.graphics.glutils

class InstanceBufferObjectSubData(isStatic$p: scala.Boolean, numInstances: scala.Int, instanceAttributes: com.badlogic.gdx.graphics.VertexAttributes) extends com.badlogic.gdx.graphics.glutils.InstanceData {
  var attributes: com.badlogic.gdx.graphics.VertexAttributes = null.asInstanceOf[com.badlogic.gdx.graphics.VertexAttributes]
  var buffer: java.nio.FloatBuffer = null.asInstanceOf[java.nio.FloatBuffer]
  var byteBuffer: java.nio.ByteBuffer = null.asInstanceOf[java.nio.ByteBuffer]
  var bufferHandle: scala.Int = 0
  var isDirect: scala.Boolean = false
  var isStatic: scala.Boolean = false
  var usage: scala.Int = 0
  var isDirty: scala.Boolean = false
  var isBound: scala.Boolean = false
  def this(isStatic: scala.Boolean, numInstances: scala.Int, instanceAttributes: scala.Array[com.badlogic.gdx.graphics.VertexAttribute]) = {
    this(isStatic, numInstances, new com.badlogic.gdx.graphics.VertexAttributes(instanceAttributes))
  }
  this.isStatic = isStatic$p
  this.attributes = instanceAttributes
  this.byteBuffer = com.badlogic.gdx.utils.BufferUtils.newByteBuffer(this.attributes.vertexSize * numInstances)
  this.isDirect = true
  this.usage = if (isStatic$p) com.badlogic.gdx.graphics.GL20.GL_STATIC_DRAW else com.badlogic.gdx.graphics.GL20.GL_DYNAMIC_DRAW
  this.buffer = this.byteBuffer.asFloatBuffer()
  this.bufferHandle = this.createBufferObject()
  this.buffer.asInstanceOf[java.nio.Buffer].flip()
  this.byteBuffer.asInstanceOf[java.nio.Buffer].flip()
  private def createBufferObject(): scala.Int = {
    val result: scala.Int = com.badlogic.gdx.Gdx.gl20.glGenBuffer()
    com.badlogic.gdx.Gdx.gl20.glBindBuffer(com.badlogic.gdx.graphics.GL20.GL_ARRAY_BUFFER, result)
    com.badlogic.gdx.Gdx.gl20.glBufferData(com.badlogic.gdx.graphics.GL20.GL_ARRAY_BUFFER, this.byteBuffer.capacity(), null, this.usage)
    com.badlogic.gdx.Gdx.gl20.glBindBuffer(com.badlogic.gdx.graphics.GL20.GL_ARRAY_BUFFER, 0)
    return result
  }
  @java.lang.Override
  def getAttributes(): com.badlogic.gdx.graphics.VertexAttributes = {
    return this.attributes
  }
  @java.lang.Override
  def getNumInstances(): scala.Int = {
    return (this.buffer.limit() * 4) / this.attributes.vertexSize
  }
  @java.lang.Override
  def getNumMaxInstances(): scala.Int = {
    return this.byteBuffer.capacity() / this.attributes.vertexSize
  }
  @java.lang.Override
  @java.lang.Deprecated
  def getBuffer(): java.nio.FloatBuffer = {
    this.isDirty = true
    return this.buffer
  }
  @java.lang.Override
  def getBuffer(forWriting: scala.Boolean): java.nio.FloatBuffer = {
    this.isDirty = this.isDirty | forWriting
    return this.buffer
  }
  private def bufferChanged(): scala.Unit = {
    if (this.isBound) {
      com.badlogic.gdx.Gdx.gl20.glBufferData(com.badlogic.gdx.graphics.GL20.GL_ARRAY_BUFFER, this.byteBuffer.limit(), null, this.usage)
      com.badlogic.gdx.Gdx.gl20.glBufferSubData(com.badlogic.gdx.graphics.GL20.GL_ARRAY_BUFFER, 0, this.byteBuffer.limit(), this.byteBuffer)
      this.isDirty = false
    } else ()
  }
  @java.lang.Override
  def setInstanceData(data: scala.Array[scala.Float], offset: scala.Int, count: scala.Int): scala.Unit = {
    this.isDirty = true
    if (this.isDirect) {
      com.badlogic.gdx.utils.BufferUtils.copy(data, this.byteBuffer, count, offset)
      this.buffer.asInstanceOf[java.nio.Buffer].position(0)
      this.buffer.asInstanceOf[java.nio.Buffer].limit(count)
    } else {
      this.buffer.asInstanceOf[java.nio.Buffer].clear()
      this.buffer.put(data, offset, count)
      this.buffer.asInstanceOf[java.nio.Buffer].flip()
      this.byteBuffer.asInstanceOf[java.nio.Buffer].position(0)
      this.byteBuffer.asInstanceOf[java.nio.Buffer].limit(this.buffer.limit() << 2)
    }
    this.bufferChanged()
  }
  @java.lang.Override
  def setInstanceData(data: java.nio.FloatBuffer, count: scala.Int): scala.Unit = {
    this.isDirty = true
    if (this.isDirect) {
      com.badlogic.gdx.utils.BufferUtils.copy(data, this.byteBuffer, count)
      this.buffer.asInstanceOf[java.nio.Buffer].position(0)
      this.buffer.asInstanceOf[java.nio.Buffer].limit(count)
    } else {
      this.buffer.asInstanceOf[java.nio.Buffer].clear()
      this.buffer.put(data)
      this.buffer.asInstanceOf[java.nio.Buffer].flip()
      this.byteBuffer.asInstanceOf[java.nio.Buffer].position(0)
      this.byteBuffer.asInstanceOf[java.nio.Buffer].limit(this.buffer.limit() << 2)
    }
    this.bufferChanged()
  }
  @java.lang.Override
  def updateInstanceData(targetOffset: scala.Int, data: scala.Array[scala.Float], sourceOffset: scala.Int, count: scala.Int): scala.Unit = {
    this.isDirty = true
    if (this.isDirect) {
      val pos: scala.Int = this.byteBuffer.position()
      this.byteBuffer.asInstanceOf[java.nio.Buffer].position(targetOffset * 4)
      com.badlogic.gdx.utils.BufferUtils.copy(data, sourceOffset, count, this.byteBuffer)
      this.byteBuffer.asInstanceOf[java.nio.Buffer].position(pos)
    } else {
      throw new com.badlogic.gdx.utils.GdxRuntimeException("Buffer must be allocated direct.")
    }
    this.bufferChanged()
  }
  @java.lang.Override
  def updateInstanceData(targetOffset: scala.Int, data: java.nio.FloatBuffer, sourceOffset: scala.Int, count: scala.Int): scala.Unit = {
    this.isDirty = true
    if (this.isDirect) {
      val pos: scala.Int = this.byteBuffer.position()
      this.byteBuffer.asInstanceOf[java.nio.Buffer].position(targetOffset * 4)
      data.asInstanceOf[java.nio.Buffer].position(sourceOffset * 4)
      com.badlogic.gdx.utils.BufferUtils.copy(data, this.byteBuffer, count)
      this.byteBuffer.asInstanceOf[java.nio.Buffer].position(pos)
    } else {
      throw new com.badlogic.gdx.utils.GdxRuntimeException("Buffer must be allocated direct.")
    }
    this.bufferChanged()
  }
  @java.lang.Override
  def bind(shader: com.badlogic.gdx.graphics.glutils.ShaderProgram): scala.Unit = {
    this.bind(shader, null)
  }
  @java.lang.Override
  def bind(shader: com.badlogic.gdx.graphics.glutils.ShaderProgram, locations: scala.Array[scala.Int]): scala.Unit = {
    val gl: com.badlogic.gdx.graphics.GL20 = com.badlogic.gdx.Gdx.gl20
    gl.glBindBuffer(com.badlogic.gdx.graphics.GL20.GL_ARRAY_BUFFER, this.bufferHandle)
    if (this.isDirty) {
      this.byteBuffer.asInstanceOf[java.nio.Buffer].limit(this.buffer.limit() * 4)
      gl.glBufferData(com.badlogic.gdx.graphics.GL20.GL_ARRAY_BUFFER, this.byteBuffer.limit(), this.byteBuffer, this.usage)
      this.isDirty = false
    } else ()
    val numAttributes: scala.Int = this.attributes.size()
    if (locations == null) {
      { var i: scala.Int = 0; while (i < numAttributes) { {
        val attribute: com.badlogic.gdx.graphics.VertexAttribute = this.attributes.get(i)
        val location: scala.Int = shader.getAttributeLocation(attribute.alias)
        if (location < 0) {
          /* continue */ ()
        } else ()
        val unitOffset: scala.Int = +attribute.unit
        shader.enableVertexAttribute(location + unitOffset)
        shader.setVertexAttribute(location + unitOffset, attribute.numComponents, attribute.`type`, attribute.normalized, this.attributes.vertexSize, attribute.offset)
        com.badlogic.gdx.Gdx.gl30.glVertexAttribDivisor(location + unitOffset, 1)
      }; i = i + 1 } }
    } else {
      { var i: scala.Int = 0; while (i < numAttributes) { {
        val attribute: com.badlogic.gdx.graphics.VertexAttribute = this.attributes.get(i)
        val location: scala.Int = locations(i)
        if (location < 0) {
          /* continue */ ()
        } else ()
        val unitOffset: scala.Int = +attribute.unit
        shader.enableVertexAttribute(location + unitOffset)
        shader.setVertexAttribute(location + unitOffset, attribute.numComponents, attribute.`type`, attribute.normalized, this.attributes.vertexSize, attribute.offset)
        com.badlogic.gdx.Gdx.gl30.glVertexAttribDivisor(location + unitOffset, 1)
      }; i = i + 1 } }
    }
    this.isBound = true
  }
  @java.lang.Override
  def unbind(shader: com.badlogic.gdx.graphics.glutils.ShaderProgram): scala.Unit = {
    this.unbind(shader, null)
  }
  @java.lang.Override
  def unbind(shader: com.badlogic.gdx.graphics.glutils.ShaderProgram, locations: scala.Array[scala.Int]): scala.Unit = {
    val gl: com.badlogic.gdx.graphics.GL20 = com.badlogic.gdx.Gdx.gl20
    val numAttributes: scala.Int = this.attributes.size()
    if (locations == null) {
      { var i: scala.Int = 0; while (i < numAttributes) { {
        val attribute: com.badlogic.gdx.graphics.VertexAttribute = this.attributes.get(i)
        val location: scala.Int = shader.getAttributeLocation(attribute.alias)
        if (location < 0) {
          /* continue */ ()
        } else ()
        val unitOffset: scala.Int = +attribute.unit
        shader.disableVertexAttribute(location + unitOffset)
      }; i = i + 1 } }
    } else {
      { var i: scala.Int = 0; while (i < numAttributes) { {
        val attribute: com.badlogic.gdx.graphics.VertexAttribute = this.attributes.get(i)
        val location: scala.Int = locations(i)
        if (location < 0) {
          /* continue */ ()
        } else ()
        val unitOffset: scala.Int = +attribute.unit
        shader.enableVertexAttribute(location + unitOffset)
      }; i = i + 1 } }
    }
    gl.glBindBuffer(com.badlogic.gdx.graphics.GL20.GL_ARRAY_BUFFER, 0)
    this.isBound = false
  }
  def invalidate(): scala.Unit = {
    this.bufferHandle = this.createBufferObject()
    this.isDirty = true
  }
  @java.lang.Override
  def dispose(): scala.Unit = {
    val gl: com.badlogic.gdx.graphics.GL20 = com.badlogic.gdx.Gdx.gl20
    gl.glBindBuffer(com.badlogic.gdx.graphics.GL20.GL_ARRAY_BUFFER, 0)
    gl.glDeleteBuffer(this.bufferHandle)
    this.bufferHandle = 0
  }
  def getBufferHandle(): scala.Int = {
    return this.bufferHandle
  }
}