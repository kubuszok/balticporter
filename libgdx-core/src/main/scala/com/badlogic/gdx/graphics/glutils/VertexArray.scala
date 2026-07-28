package com.badlogic.gdx.graphics.glutils

class VertexArray(numVertices: scala.Int, attributes$p: com.badlogic.gdx.graphics.VertexAttributes) extends com.badlogic.gdx.graphics.glutils.VertexData {
  var attributes: com.badlogic.gdx.graphics.VertexAttributes = null.asInstanceOf[com.badlogic.gdx.graphics.VertexAttributes]
  var buffer: java.nio.FloatBuffer = null.asInstanceOf[java.nio.FloatBuffer]
  var byteBuffer: java.nio.ByteBuffer = null.asInstanceOf[java.nio.ByteBuffer]
  var isBound: scala.Boolean = false
  def this(numVertices: scala.Int, attributes: scala.Array[com.badlogic.gdx.graphics.VertexAttribute]) = {
    this(numVertices, new com.badlogic.gdx.graphics.VertexAttributes(attributes))
  }
  this.attributes = attributes$p
  this.byteBuffer = com.badlogic.gdx.utils.BufferUtils.newUnsafeByteBuffer(this.attributes.vertexSize * numVertices)
  this.buffer = this.byteBuffer.asFloatBuffer()
  this.buffer.asInstanceOf[java.nio.Buffer].flip()
  this.byteBuffer.asInstanceOf[java.nio.Buffer].flip()
  @java.lang.Override
  def dispose(): scala.Unit = {
    com.badlogic.gdx.utils.BufferUtils.disposeUnsafeByteBuffer(this.byteBuffer)
  }
  @java.lang.Override
  @java.lang.Deprecated
  def getBuffer(): java.nio.FloatBuffer = {
    return this.buffer
  }
  @java.lang.Override
  def getBuffer(forWriting: scala.Boolean): java.nio.FloatBuffer = {
    return this.buffer
  }
  @java.lang.Override
  def getNumVertices(): scala.Int = {
    return (this.buffer.limit() * 4) / this.attributes.vertexSize
  }
  def getNumMaxVertices(): scala.Int = {
    return this.byteBuffer.capacity() / this.attributes.vertexSize
  }
  @java.lang.Override
  def setVertices(vertices: scala.Array[scala.Float], offset: scala.Int, count: scala.Int): scala.Unit = {
    com.badlogic.gdx.utils.BufferUtils.copy(vertices, this.byteBuffer, count, offset)
    this.buffer.asInstanceOf[java.nio.Buffer].position(0)
    this.buffer.asInstanceOf[java.nio.Buffer].limit(count)
  }
  @java.lang.Override
  def updateVertices(targetOffset: scala.Int, vertices: scala.Array[scala.Float], sourceOffset: scala.Int, count: scala.Int): scala.Unit = {
    val pos: scala.Int = this.byteBuffer.position()
    this.byteBuffer.asInstanceOf[java.nio.Buffer].position(targetOffset * 4)
    com.badlogic.gdx.utils.BufferUtils.copy(vertices, sourceOffset, count, this.byteBuffer)
    this.byteBuffer.asInstanceOf[java.nio.Buffer].position(pos)
  }
  @java.lang.Override
  def bind(shader: com.badlogic.gdx.graphics.glutils.ShaderProgram): scala.Unit = {
    this.bind(shader, null)
  }
  @java.lang.Override
  def bind(shader: com.badlogic.gdx.graphics.glutils.ShaderProgram, locations: scala.Array[scala.Int]): scala.Unit = {
    val numAttributes: scala.Int = this.attributes.size()
    this.byteBuffer.asInstanceOf[java.nio.Buffer].limit(this.buffer.limit() * 4)
    if (locations == null) {
      { var i: scala.Int = 0; while (i < numAttributes) { {
        val attribute: com.badlogic.gdx.graphics.VertexAttribute = this.attributes.get(i)
        val location: scala.Int = shader.getAttributeLocation(attribute.alias)
        if (location < 0) {
          /* continue */ ()
        } else ()
        shader.enableVertexAttribute(location)
        if (attribute.`type` == com.badlogic.gdx.graphics.GL20.GL_FLOAT) {
          this.buffer.asInstanceOf[java.nio.Buffer].position(attribute.offset / 4)
          shader.setVertexAttribute(location, attribute.numComponents, attribute.`type`, attribute.normalized, this.attributes.vertexSize, this.buffer)
        } else {
          this.byteBuffer.asInstanceOf[java.nio.Buffer].position(attribute.offset)
          shader.setVertexAttribute(location, attribute.numComponents, attribute.`type`, attribute.normalized, this.attributes.vertexSize, this.byteBuffer)
        }
      }; i = i + 1 } }
    } else {
      { var i: scala.Int = 0; while (i < numAttributes) { {
        val attribute: com.badlogic.gdx.graphics.VertexAttribute = this.attributes.get(i)
        val location: scala.Int = locations(i)
        if (location < 0) {
          /* continue */ ()
        } else ()
        shader.enableVertexAttribute(location)
        if (attribute.`type` == com.badlogic.gdx.graphics.GL20.GL_FLOAT) {
          this.buffer.asInstanceOf[java.nio.Buffer].position(attribute.offset / 4)
          shader.setVertexAttribute(location, attribute.numComponents, attribute.`type`, attribute.normalized, this.attributes.vertexSize, this.buffer)
        } else {
          this.byteBuffer.asInstanceOf[java.nio.Buffer].position(attribute.offset)
          shader.setVertexAttribute(location, attribute.numComponents, attribute.`type`, attribute.normalized, this.attributes.vertexSize, this.byteBuffer)
        }
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
    this.isBound = false
  }
  @java.lang.Override
  def getAttributes(): com.badlogic.gdx.graphics.VertexAttributes = {
    return this.attributes
  }
  @java.lang.Override
  def invalidate(): scala.Unit = {
    ()
  }
}