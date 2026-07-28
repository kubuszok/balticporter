package com.badlogic.gdx.graphics.glutils

class VertexBufferObjectSubData(isStatic$p: scala.Boolean, numVertices: scala.Int, attributes$p: com.badlogic.gdx.graphics.VertexAttributes) extends com.badlogic.gdx.graphics.glutils.VertexData {
  var attributes: com.badlogic.gdx.graphics.VertexAttributes = null.asInstanceOf[com.badlogic.gdx.graphics.VertexAttributes]
  var buffer: java.nio.FloatBuffer = null.asInstanceOf[java.nio.FloatBuffer]
  var byteBuffer: java.nio.ByteBuffer = null.asInstanceOf[java.nio.ByteBuffer]
  var bufferHandle: scala.Int = 0
  var isDirect: scala.Boolean = false
  var isStatic: scala.Boolean = false
  var usage: scala.Int = 0
  var isDirty: scala.Boolean = false
  var isBound: scala.Boolean = false
  def this(isStatic: scala.Boolean, numVertices: scala.Int, attributes: scala.Array[com.badlogic.gdx.graphics.VertexAttribute]) = {
    this(isStatic, numVertices, new com.badlogic.gdx.graphics.VertexAttributes(attributes))
  }
  this.isStatic = isStatic$p
  this.attributes = attributes$p
  this.byteBuffer = com.badlogic.gdx.utils.BufferUtils.newByteBuffer(this.attributes.vertexSize * numVertices)
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
  override def getAttributes(): com.badlogic.gdx.graphics.VertexAttributes = {
    return this.attributes
  }
  @java.lang.Override
  override def getNumVertices(): scala.Int = {
    return (this.buffer.limit() * 4) / this.attributes.vertexSize
  }
  @java.lang.Override
  override def getNumMaxVertices(): scala.Int = {
    return this.byteBuffer.capacity() / this.attributes.vertexSize
  }
  @java.lang.Override
  @java.lang.Deprecated
  override def getBuffer(): java.nio.FloatBuffer = {
    this.isDirty = true
    return this.buffer
  }
  @java.lang.Override
  override def getBuffer(forWriting: scala.Boolean): java.nio.FloatBuffer = {
    this.isDirty = this.isDirty | forWriting
    return this.buffer
  }
  private def bufferChanged(): scala.Unit = {
    if (this.isBound) {
      com.badlogic.gdx.Gdx.gl20.glBufferSubData(com.badlogic.gdx.graphics.GL20.GL_ARRAY_BUFFER, 0, this.byteBuffer.limit(), this.byteBuffer)
      this.isDirty = false
    } else ()
  }
  @java.lang.Override
  override def setVertices(vertices: scala.Array[scala.Float], offset: scala.Int, count: scala.Int): scala.Unit = {
    this.isDirty = true
    if (this.isDirect) {
      com.badlogic.gdx.utils.BufferUtils.copy(vertices, this.byteBuffer, count, offset)
      this.buffer.asInstanceOf[java.nio.Buffer].position(0)
      this.buffer.asInstanceOf[java.nio.Buffer].limit(count)
    } else {
      this.buffer.asInstanceOf[java.nio.Buffer].clear()
      this.buffer.put(vertices, offset, count)
      this.buffer.asInstanceOf[java.nio.Buffer].flip()
      this.byteBuffer.asInstanceOf[java.nio.Buffer].position(0)
      this.byteBuffer.asInstanceOf[java.nio.Buffer].limit(this.buffer.limit() << 2)
    }
    this.bufferChanged()
  }
  @java.lang.Override
  override def updateVertices(targetOffset: scala.Int, vertices: scala.Array[scala.Float], sourceOffset: scala.Int, count: scala.Int): scala.Unit = {
    this.isDirty = true
    if (this.isDirect) {
      val pos: scala.Int = this.byteBuffer.position()
      this.byteBuffer.asInstanceOf[java.nio.Buffer].position(targetOffset * 4)
      com.badlogic.gdx.utils.BufferUtils.copy(vertices, sourceOffset, count, this.byteBuffer)
      this.byteBuffer.asInstanceOf[java.nio.Buffer].position(pos)
    } else {
      throw new com.badlogic.gdx.utils.GdxRuntimeException("Buffer must be allocated direct.")
    }
    this.bufferChanged()
  }
  @java.lang.Override
  override def bind(shader: com.badlogic.gdx.graphics.glutils.ShaderProgram): scala.Unit = {
    this.bind(shader, null)
  }
  @java.lang.Override
  override def bind(shader: com.badlogic.gdx.graphics.glutils.ShaderProgram, locations: scala.Array[scala.Int]): scala.Unit = {
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
        shader.enableVertexAttribute(location)
        shader.setVertexAttribute(location, attribute.numComponents, attribute.`type`, attribute.normalized, this.attributes.vertexSize, attribute.offset)
      }; i = i + 1 } }
    } else {
      { var i: scala.Int = 0; while (i < numAttributes) { {
        val attribute: com.badlogic.gdx.graphics.VertexAttribute = this.attributes.get(i)
        val location: scala.Int = locations(i)
        if (location < 0) {
          /* continue */ ()
        } else ()
        shader.enableVertexAttribute(location)
        shader.setVertexAttribute(location, attribute.numComponents, attribute.`type`, attribute.normalized, this.attributes.vertexSize, attribute.offset)
      }; i = i + 1 } }
    }
    this.isBound = true
  }
  @java.lang.Override
  override def unbind(shader: com.badlogic.gdx.graphics.glutils.ShaderProgram): scala.Unit = {
    this.unbind(shader, null)
  }
  @java.lang.Override
  override def unbind(shader: com.badlogic.gdx.graphics.glutils.ShaderProgram, locations: scala.Array[scala.Int]): scala.Unit = {
    val gl: com.badlogic.gdx.graphics.GL20 = com.badlogic.gdx.Gdx.gl20
    val numAttributes: scala.Int = this.attributes.size()
    if (locations == null) {
      { var i: scala.Int = 0; while (i < numAttributes) { {
        shader.disableVertexAttribute(this.attributes.get(i).alias)
      }; i = i + 1 } }
    } else {
      { var i: scala.Int = 0; while (i < numAttributes) { {
        val location: scala.Int = locations(i)
        if (location >= 0) {
          shader.disableVertexAttribute(location)
        } else ()
      }; i = i + 1 } }
    }
    gl.glBindBuffer(com.badlogic.gdx.graphics.GL20.GL_ARRAY_BUFFER, 0)
    this.isBound = false
  }
  override def invalidate(): scala.Unit = {
    this.bufferHandle = this.createBufferObject()
    this.isDirty = true
  }
  @java.lang.Override
  override def dispose(): scala.Unit = {
    val gl: com.badlogic.gdx.graphics.GL20 = com.badlogic.gdx.Gdx.gl20
    gl.glBindBuffer(com.badlogic.gdx.graphics.GL20.GL_ARRAY_BUFFER, 0)
    gl.glDeleteBuffer(this.bufferHandle)
    this.bufferHandle = 0
  }
  def getBufferHandle(): scala.Int = {
    return this.bufferHandle
  }
}