package com.badlogic.gdx.graphics.glutils

class VertexBufferObject extends com.badlogic.gdx.graphics.glutils.VertexData {
  private var attributes: com.badlogic.gdx.graphics.VertexAttributes = null.asInstanceOf[com.badlogic.gdx.graphics.VertexAttributes]
  private var buffer: java.nio.FloatBuffer = null.asInstanceOf[java.nio.FloatBuffer]
  private var byteBuffer: java.nio.ByteBuffer = null.asInstanceOf[java.nio.ByteBuffer]
  private var ownsBuffer: scala.Boolean = false
  private var bufferHandle: scala.Int = 0
  private var usage: scala.Int = 0
  var isDirty: scala.Boolean = false
  var isBound: scala.Boolean = false
  def this(isStatic: scala.Boolean, numVertices: scala.Int, attributes: com.badlogic.gdx.graphics.VertexAttributes) = {
    this()
    this.bufferHandle = com.badlogic.gdx.Gdx.gl20.glGenBuffer()
    val data: java.nio.ByteBuffer = com.badlogic.gdx.utils.BufferUtils.newUnsafeByteBuffer(attributes.vertexSize * numVertices)
    data.asInstanceOf[java.nio.Buffer].limit(0)
    this.setBuffer(data, true, attributes)
    this.setUsage(if (isStatic) com.badlogic.gdx.graphics.GL20.GL_STATIC_DRAW else com.badlogic.gdx.graphics.GL20.GL_DYNAMIC_DRAW)
  }
  def this(isStatic: scala.Boolean, numVertices: scala.Int, attributes: scala.Array[com.badlogic.gdx.graphics.VertexAttribute]) = {
    this(isStatic, numVertices, new com.badlogic.gdx.graphics.VertexAttributes(attributes))
  }
  def this(usage: scala.Int, data: java.nio.ByteBuffer, ownsBuffer: scala.Boolean, attributes: com.badlogic.gdx.graphics.VertexAttributes) = {
    this()
    this.bufferHandle = com.badlogic.gdx.Gdx.gl20.glGenBuffer()
    this.setBuffer(data, ownsBuffer, attributes)
    this.setUsage(usage)
  }
  def getAttributes(): com.badlogic.gdx.graphics.VertexAttributes = {
    return this.attributes
  }
  def getNumVertices(): scala.Int = {
    return (this.buffer.limit() * 4) / this.attributes.vertexSize
  }
  def getNumMaxVertices(): scala.Int = {
    return this.byteBuffer.capacity() / this.attributes.vertexSize
  }
  def getBuffer(): java.nio.FloatBuffer = {
    this.isDirty = true
    return this.buffer
  }
  def getBuffer(forWriting: scala.Boolean): java.nio.FloatBuffer = {
    this.isDirty = this.isDirty | forWriting
    return this.buffer
  }
  def setBuffer(data: java.nio.Buffer, ownsBuffer: scala.Boolean, value: com.badlogic.gdx.graphics.VertexAttributes): scala.Unit = {
    if (this.isBound) {
      throw new com.badlogic.gdx.utils.GdxRuntimeException("Cannot change attributes while VBO is bound")
    } else ()
    if (this.ownsBuffer && (this.byteBuffer != null)) {
      com.badlogic.gdx.utils.BufferUtils.disposeUnsafeByteBuffer(this.byteBuffer)
    } else ()
    this.attributes = value
    if (data.isInstanceOf[java.nio.ByteBuffer]) {
      this.byteBuffer = data.asInstanceOf[java.nio.ByteBuffer]
    } else {
      throw new com.badlogic.gdx.utils.GdxRuntimeException("Only ByteBuffer is currently supported")
    }
    this.ownsBuffer = ownsBuffer
    val l: scala.Int = this.byteBuffer.limit()
    this.byteBuffer.asInstanceOf[java.nio.Buffer].limit(this.byteBuffer.capacity())
    this.buffer = this.byteBuffer.asFloatBuffer()
    this.byteBuffer.asInstanceOf[java.nio.Buffer].limit(l)
    this.buffer.asInstanceOf[java.nio.Buffer].limit(l / 4)
  }
  private def bufferChanged(): scala.Unit = {
    if (this.isBound) {
      com.badlogic.gdx.Gdx.gl20.glBufferData(com.badlogic.gdx.graphics.GL20.GL_ARRAY_BUFFER, this.byteBuffer.limit(), this.byteBuffer, this.usage)
      this.isDirty = false
    } else ()
  }
  def setVertices(vertices: scala.Array[scala.Float], offset: scala.Int, count: scala.Int): scala.Unit = {
    this.isDirty = true
    com.badlogic.gdx.utils.BufferUtils.copy(vertices, this.byteBuffer, count, offset)
    this.buffer.asInstanceOf[java.nio.Buffer].position(0)
    this.buffer.asInstanceOf[java.nio.Buffer].limit(count)
    this.bufferChanged()
  }
  def updateVertices(targetOffset: scala.Int, vertices: scala.Array[scala.Float], sourceOffset: scala.Int, count: scala.Int): scala.Unit = {
    this.isDirty = true
    val pos: scala.Int = this.byteBuffer.position()
    this.byteBuffer.asInstanceOf[java.nio.Buffer].position(targetOffset * 4)
    com.badlogic.gdx.utils.BufferUtils.copy(vertices, sourceOffset, count, this.byteBuffer)
    this.byteBuffer.asInstanceOf[java.nio.Buffer].position(pos)
    this.buffer.asInstanceOf[java.nio.Buffer].position(0)
    this.bufferChanged()
  }
  def getUsage(): scala.Int = {
    return this.usage
  }
  def setUsage(value: scala.Int): scala.Unit = {
    if (this.isBound) {
      throw new com.badlogic.gdx.utils.GdxRuntimeException("Cannot change usage while VBO is bound")
    } else ()
    this.usage = value
  }
  def bind(shader: com.badlogic.gdx.graphics.glutils.ShaderProgram): scala.Unit = {
    this.bind(shader, null)
  }
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
  def unbind(shader: com.badlogic.gdx.graphics.glutils.ShaderProgram): scala.Unit = {
    this.unbind(shader, null)
  }
  def unbind(shader: com.badlogic.gdx.graphics.glutils.ShaderProgram, locations: scala.Array[scala.Int]): scala.Unit = {
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
  def invalidate(): scala.Unit = {
    this.bufferHandle = com.badlogic.gdx.Gdx.gl20.glGenBuffer()
    this.isDirty = true
  }
  def dispose(): scala.Unit = {
    val gl: com.badlogic.gdx.graphics.GL20 = com.badlogic.gdx.Gdx.gl20
    gl.glBindBuffer(com.badlogic.gdx.graphics.GL20.GL_ARRAY_BUFFER, 0)
    gl.glDeleteBuffer(this.bufferHandle)
    this.bufferHandle = 0
    if (this.ownsBuffer) {
      com.badlogic.gdx.utils.BufferUtils.disposeUnsafeByteBuffer(this.byteBuffer)
    } else ()
  }
}