package com.badlogic.gdx.graphics.glutils

class InstanceBufferObject(isStatic: scala.Boolean, numVertices: scala.Int, instanceAttributes: com.badlogic.gdx.graphics.VertexAttributes) extends com.badlogic.gdx.graphics.glutils.InstanceData {
  private var attributes: com.badlogic.gdx.graphics.VertexAttributes = null.asInstanceOf[com.badlogic.gdx.graphics.VertexAttributes]
  private var buffer: java.nio.FloatBuffer = null.asInstanceOf[java.nio.FloatBuffer]
  private var byteBuffer: java.nio.ByteBuffer = null.asInstanceOf[java.nio.ByteBuffer]
  private var ownsBuffer: scala.Boolean = false
  private var bufferHandle: scala.Int = 0
  private var usage: scala.Int = 0
  var isDirty: scala.Boolean = false
  var isBound: scala.Boolean = false
  val data: java.nio.ByteBuffer = com.badlogic.gdx.utils.BufferUtils.newUnsafeByteBuffer(instanceAttributes.vertexSize * numVertices)
  def this(isStatic: scala.Boolean, numVertices: scala.Int, attributes: scala.Array[com.badlogic.gdx.graphics.VertexAttribute]) = {
    this(isStatic, numVertices, new com.badlogic.gdx.graphics.VertexAttributes(attributes))
  }
  if (com.badlogic.gdx.Gdx.gl30 == null) {
    throw new com.badlogic.gdx.utils.GdxRuntimeException("InstanceBufferObject requires a device running with GLES 3.0 compatibilty")
  } else ()
  this.bufferHandle = com.badlogic.gdx.Gdx.gl20.glGenBuffer()
  data.asInstanceOf[java.nio.Buffer].limit(0)
  this.setBuffer(data, true, instanceAttributes)
  this.setUsage(if (isStatic) com.badlogic.gdx.graphics.GL20.GL_STATIC_DRAW else com.badlogic.gdx.graphics.GL20.GL_DYNAMIC_DRAW)
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
      com.badlogic.gdx.Gdx.gl20.glBufferData(com.badlogic.gdx.graphics.GL20.GL_ARRAY_BUFFER, this.byteBuffer.limit(), null, this.usage)
      com.badlogic.gdx.Gdx.gl20.glBufferData(com.badlogic.gdx.graphics.GL20.GL_ARRAY_BUFFER, this.byteBuffer.limit(), this.byteBuffer, this.usage)
      this.isDirty = false
    } else ()
  }
  @java.lang.Override
  def setInstanceData(data: scala.Array[scala.Float], offset: scala.Int, count: scala.Int): scala.Unit = {
    this.isDirty = true
    com.badlogic.gdx.utils.BufferUtils.copy(data, this.byteBuffer, count, offset)
    this.buffer.asInstanceOf[java.nio.Buffer].position(0)
    this.buffer.asInstanceOf[java.nio.Buffer].limit(count)
    this.bufferChanged()
  }
  @java.lang.Override
  def setInstanceData(data: java.nio.FloatBuffer, count: scala.Int): scala.Unit = {
    this.isDirty = true
    com.badlogic.gdx.utils.BufferUtils.copy(data, this.byteBuffer, count)
    this.buffer.asInstanceOf[java.nio.Buffer].position(0)
    this.buffer.asInstanceOf[java.nio.Buffer].limit(count)
    this.bufferChanged()
  }
  @java.lang.Override
  def updateInstanceData(targetOffset: scala.Int, data: scala.Array[scala.Float], sourceOffset: scala.Int, count: scala.Int): scala.Unit = {
    this.isDirty = true
    val pos: scala.Int = this.byteBuffer.position()
    this.byteBuffer.asInstanceOf[java.nio.Buffer].position(targetOffset * 4)
    com.badlogic.gdx.utils.BufferUtils.copy(data, sourceOffset, count, this.byteBuffer)
    this.byteBuffer.asInstanceOf[java.nio.Buffer].position(pos)
    this.buffer.asInstanceOf[java.nio.Buffer].position(0)
    this.bufferChanged()
  }
  @java.lang.Override
  def updateInstanceData(targetOffset: scala.Int, data: java.nio.FloatBuffer, sourceOffset: scala.Int, count: scala.Int): scala.Unit = {
    this.isDirty = true
    val pos: scala.Int = this.byteBuffer.position()
    this.byteBuffer.asInstanceOf[java.nio.Buffer].position(targetOffset * 4)
    data.asInstanceOf[java.nio.Buffer].position(sourceOffset * 4)
    com.badlogic.gdx.utils.BufferUtils.copy(data, this.byteBuffer, count)
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
        shader.disableVertexAttribute(location + unitOffset)
      }; i = i + 1 } }
    }
    gl.glBindBuffer(com.badlogic.gdx.graphics.GL20.GL_ARRAY_BUFFER, 0)
    this.isBound = false
  }
  @java.lang.Override
  def invalidate(): scala.Unit = {
    this.bufferHandle = com.badlogic.gdx.Gdx.gl20.glGenBuffer()
    this.isDirty = true
  }
  @java.lang.Override
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