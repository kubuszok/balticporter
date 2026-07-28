package com.badlogic.gdx.graphics.glutils

class VertexBufferObjectWithVAO extends com.badlogic.gdx.graphics.glutils.VertexData {
  var attributes: com.badlogic.gdx.graphics.VertexAttributes = null.asInstanceOf[com.badlogic.gdx.graphics.VertexAttributes]
  var buffer: java.nio.FloatBuffer = null.asInstanceOf[java.nio.FloatBuffer]
  var byteBuffer: java.nio.ByteBuffer = null.asInstanceOf[java.nio.ByteBuffer]
  var ownsBuffer: scala.Boolean = false
  var bufferHandle: scala.Int = 0
  var isStatic: scala.Boolean = false
  var usage: scala.Int = 0
  var isDirty: scala.Boolean = false
  var isBound: scala.Boolean = false
  var vaoHandle: scala.Int = -1
  var cachedLocations: com.badlogic.gdx.utils.IntArray = new com.badlogic.gdx.utils.IntArray()
  def this(isStatic: scala.Boolean, numVertices: scala.Int, attributes: com.badlogic.gdx.graphics.VertexAttributes) = {
    this()
    this.isStatic = isStatic
    this.attributes = attributes
    this.byteBuffer = com.badlogic.gdx.utils.BufferUtils.newUnsafeByteBuffer(this.attributes.vertexSize * numVertices)
    this.buffer = this.byteBuffer.asFloatBuffer()
    this.ownsBuffer = true
    this.buffer.asInstanceOf[java.nio.Buffer].flip()
    this.byteBuffer.asInstanceOf[java.nio.Buffer].flip()
    this.bufferHandle = com.badlogic.gdx.Gdx.gl20.glGenBuffer()
    this.usage = if (isStatic) com.badlogic.gdx.graphics.GL20.GL_STATIC_DRAW else com.badlogic.gdx.graphics.GL20.GL_DYNAMIC_DRAW
    this.createVAO()
  }
  def this(isStatic: scala.Boolean, numVertices: scala.Int, attributes: scala.Array[com.badlogic.gdx.graphics.VertexAttribute]) = {
    this(isStatic, numVertices, new com.badlogic.gdx.graphics.VertexAttributes(attributes))
  }
  def this(isStatic: scala.Boolean, unmanagedBuffer: java.nio.ByteBuffer, attributes: com.badlogic.gdx.graphics.VertexAttributes) = {
    this()
    this.isStatic = isStatic
    this.attributes = attributes
    this.byteBuffer = unmanagedBuffer
    this.ownsBuffer = false
    this.buffer = this.byteBuffer.asFloatBuffer()
    this.buffer.asInstanceOf[java.nio.Buffer].flip()
    this.byteBuffer.asInstanceOf[java.nio.Buffer].flip()
    this.bufferHandle = com.badlogic.gdx.Gdx.gl20.glGenBuffer()
    this.usage = if (isStatic) com.badlogic.gdx.graphics.GL20.GL_STATIC_DRAW else com.badlogic.gdx.graphics.GL20.GL_DYNAMIC_DRAW
    this.createVAO()
  }
  @java.lang.Override
  def getAttributes(): com.badlogic.gdx.graphics.VertexAttributes = {
    return this.attributes
  }
  @java.lang.Override
  def getNumVertices(): scala.Int = {
    return (this.buffer.limit() * 4) / this.attributes.vertexSize
  }
  @java.lang.Override
  def getNumMaxVertices(): scala.Int = {
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
      com.badlogic.gdx.Gdx.gl20.glBindBuffer(com.badlogic.gdx.graphics.GL20.GL_ARRAY_BUFFER, this.bufferHandle)
      com.badlogic.gdx.Gdx.gl20.glBufferData(com.badlogic.gdx.graphics.GL20.GL_ARRAY_BUFFER, this.byteBuffer.limit(), this.byteBuffer, this.usage)
      this.isDirty = false
    } else ()
  }
  @java.lang.Override
  def setVertices(vertices: scala.Array[scala.Float], offset: scala.Int, count: scala.Int): scala.Unit = {
    this.isDirty = true
    com.badlogic.gdx.utils.BufferUtils.copy(vertices, this.byteBuffer, count, offset)
    this.buffer.asInstanceOf[java.nio.Buffer].position(0)
    this.buffer.asInstanceOf[java.nio.Buffer].limit(count)
    this.bufferChanged()
  }
  @java.lang.Override
  def updateVertices(targetOffset: scala.Int, vertices: scala.Array[scala.Float], sourceOffset: scala.Int, count: scala.Int): scala.Unit = {
    this.isDirty = true
    val pos: scala.Int = this.byteBuffer.position()
    this.byteBuffer.asInstanceOf[java.nio.Buffer].position(targetOffset * 4)
    com.badlogic.gdx.utils.BufferUtils.copy(vertices, sourceOffset, count, this.byteBuffer)
    this.byteBuffer.asInstanceOf[java.nio.Buffer].position(pos)
    this.buffer.asInstanceOf[java.nio.Buffer].position(0)
    this.bufferChanged()
  }
  @java.lang.Override
  def bind(shader: com.badlogic.gdx.graphics.glutils.ShaderProgram): scala.Unit = {
    this.bind(shader, null)
  }
  @java.lang.Override
  def bind(shader: com.badlogic.gdx.graphics.glutils.ShaderProgram, locations: scala.Array[scala.Int]): scala.Unit = {
    val gl: com.badlogic.gdx.graphics.GL30 = com.badlogic.gdx.Gdx.gl30
    gl.glBindVertexArray(this.vaoHandle)
    this.bindAttributes(shader, locations)
    this.bindData(gl)
    this.isBound = true
  }
  private def bindAttributes(shader: com.badlogic.gdx.graphics.glutils.ShaderProgram, locations: scala.Array[scala.Int]): scala.Unit = {
    var stillValid: scala.Boolean = this.cachedLocations.size != 0
    val numAttributes: scala.Int = this.attributes.size()
    if (stillValid) {
      if (locations == null) {
        { var i: scala.Int = 0; while (stillValid && (i < numAttributes)) { {
          val attribute: com.badlogic.gdx.graphics.VertexAttribute = this.attributes.get(i)
          val location: scala.Int = shader.getAttributeLocation(attribute.alias)
          stillValid = location == this.cachedLocations.get(i)
        }; i = i + 1 } }
      } else {
        stillValid = locations.length == this.cachedLocations.size;
        { var i: scala.Int = 0; while (stillValid && (i < numAttributes)) { {
          stillValid = locations(i) == this.cachedLocations.get(i)
        }; i = i + 1 } }
      }
    } else ()
    if (!stillValid) {
      com.badlogic.gdx.Gdx.gl.glBindBuffer(com.badlogic.gdx.graphics.GL20.GL_ARRAY_BUFFER, this.bufferHandle)
      this.unbindAttributes(shader)
      this.cachedLocations.clear();
      { var i: scala.Int = 0; while (i < numAttributes) { {
        val attribute: com.badlogic.gdx.graphics.VertexAttribute = this.attributes.get(i)
        if (locations == null) {
          this.cachedLocations.add(shader.getAttributeLocation(attribute.alias))
        } else {
          this.cachedLocations.add(locations(i))
        }
        val location: scala.Int = this.cachedLocations.get(i)
        if (location < 0) {
          /* continue */ ()
        } else ()
        shader.enableVertexAttribute(location)
        shader.setVertexAttribute(location, attribute.numComponents, attribute.`type`, attribute.normalized, this.attributes.vertexSize, attribute.offset)
      }; i = i + 1 } }
    } else ()
  }
  private def unbindAttributes(shaderProgram: com.badlogic.gdx.graphics.glutils.ShaderProgram): scala.Unit = {
    if (this.cachedLocations.size == 0) {
      return
    } else ()
    val numAttributes: scala.Int = this.attributes.size();
    { var i: scala.Int = 0; while (i < numAttributes) { {
      val location: scala.Int = this.cachedLocations.get(i)
      if (location < 0) {
        /* continue */ ()
      } else ()
      shaderProgram.disableVertexAttribute(location)
    }; i = i + 1 } }
  }
  private def bindData(gl: com.badlogic.gdx.graphics.GL20): scala.Unit = {
    if (this.isDirty) {
      gl.glBindBuffer(com.badlogic.gdx.graphics.GL20.GL_ARRAY_BUFFER, this.bufferHandle)
      this.byteBuffer.asInstanceOf[java.nio.Buffer].limit(this.buffer.limit() * 4)
      gl.glBufferData(com.badlogic.gdx.graphics.GL20.GL_ARRAY_BUFFER, this.byteBuffer.limit(), this.byteBuffer, this.usage)
      this.isDirty = false
    } else ()
  }
  @java.lang.Override
  def unbind(shader: com.badlogic.gdx.graphics.glutils.ShaderProgram): scala.Unit = {
    this.unbind(shader, null)
  }
  @java.lang.Override
  def unbind(shader: com.badlogic.gdx.graphics.glutils.ShaderProgram, locations: scala.Array[scala.Int]): scala.Unit = {
    val gl: com.badlogic.gdx.graphics.GL30 = com.badlogic.gdx.Gdx.gl30
    gl.glBindVertexArray(0)
    this.isBound = false
  }
  @java.lang.Override
  def invalidate(): scala.Unit = {
    this.bufferHandle = com.badlogic.gdx.Gdx.gl30.glGenBuffer()
    this.createVAO()
    this.isDirty = true
  }
  @java.lang.Override
  def dispose(): scala.Unit = {
    val gl: com.badlogic.gdx.graphics.GL30 = com.badlogic.gdx.Gdx.gl30
    gl.glBindBuffer(com.badlogic.gdx.graphics.GL20.GL_ARRAY_BUFFER, 0)
    gl.glDeleteBuffer(this.bufferHandle)
    this.bufferHandle = 0
    if (this.ownsBuffer) {
      com.badlogic.gdx.utils.BufferUtils.disposeUnsafeByteBuffer(this.byteBuffer)
    } else ()
    this.deleteVAO()
  }
  private def createVAO(): scala.Unit = {
    VertexBufferObjectWithVAO.tmpHandle.asInstanceOf[java.nio.Buffer].clear()
    com.badlogic.gdx.Gdx.gl30.glGenVertexArrays(1, VertexBufferObjectWithVAO.tmpHandle)
    this.vaoHandle = VertexBufferObjectWithVAO.tmpHandle.get()
  }
  private def deleteVAO(): scala.Unit = {
    if (this.vaoHandle != (-1)) {
      VertexBufferObjectWithVAO.tmpHandle.asInstanceOf[java.nio.Buffer].clear()
      VertexBufferObjectWithVAO.tmpHandle.put(this.vaoHandle)
      VertexBufferObjectWithVAO.tmpHandle.asInstanceOf[java.nio.Buffer].flip()
      com.badlogic.gdx.Gdx.gl30.glDeleteVertexArrays(1, VertexBufferObjectWithVAO.tmpHandle)
      this.vaoHandle = -1
    } else ()
  }
}
object VertexBufferObjectWithVAO {
  final val tmpHandle: java.nio.IntBuffer = com.badlogic.gdx.utils.BufferUtils.newIntBuffer(1)
}