package com.badlogic.gdx.graphics

class Mesh extends com.badlogic.gdx.utils.Disposable {
  var vertices: com.badlogic.gdx.graphics.glutils.VertexData = null.asInstanceOf[com.badlogic.gdx.graphics.glutils.VertexData]
  var indices: com.badlogic.gdx.graphics.glutils.IndexData = null.asInstanceOf[com.badlogic.gdx.graphics.glutils.IndexData]
  var autoBind: scala.Boolean = true
  var isVertexArray: scala.Boolean = false
  var instances: com.badlogic.gdx.graphics.glutils.InstanceData = null.asInstanceOf[com.badlogic.gdx.graphics.glutils.InstanceData]
  var isInstanced$field: scala.Boolean = false
  private final val tmpV: com.badlogic.gdx.math.Vector3 = new com.badlogic.gdx.math.Vector3()
  def this(staticVertices: scala.Boolean, staticIndices: scala.Boolean, maxVertices: scala.Int, maxIndices: scala.Int, attributes: com.badlogic.gdx.graphics.VertexAttributes) = {
    this()
    this.vertices = this.makeVertexBuffer(staticVertices, maxVertices, attributes)
    this.indices = new com.badlogic.gdx.graphics.glutils.IndexBufferObject(staticIndices, maxIndices)
    this.isVertexArray = false
    Mesh.addManagedMesh(com.badlogic.gdx.Gdx.app, this)
  }
  def this(`type`: com.badlogic.gdx.graphics.Mesh.VertexDataType, isStatic: scala.Boolean, maxVertices: scala.Int, maxIndices: scala.Int, attributes: com.badlogic.gdx.graphics.VertexAttributes) = {
    this()
    `type` match {
      case com.badlogic.gdx.graphics.Mesh.VertexDataType.VertexBufferObject => {
        this.vertices = new com.badlogic.gdx.graphics.glutils.VertexBufferObject(isStatic, maxVertices, attributes)
        this.indices = new com.badlogic.gdx.graphics.glutils.IndexBufferObject(isStatic, maxIndices)
        this.isVertexArray = false
      }
      case com.badlogic.gdx.graphics.Mesh.VertexDataType.VertexBufferObjectSubData => {
        this.vertices = new com.badlogic.gdx.graphics.glutils.VertexBufferObjectSubData(isStatic, maxVertices, attributes)
        this.indices = new com.badlogic.gdx.graphics.glutils.IndexBufferObjectSubData(isStatic, maxIndices)
        this.isVertexArray = false
      }
      case com.badlogic.gdx.graphics.Mesh.VertexDataType.VertexBufferObjectWithVAO => {
        this.vertices = new com.badlogic.gdx.graphics.glutils.VertexBufferObjectWithVAO(isStatic, maxVertices, attributes)
        this.indices = new com.badlogic.gdx.graphics.glutils.IndexBufferObjectSubData(isStatic, maxIndices)
        this.isVertexArray = false
      }
      case _ => {
        this.vertices = new com.badlogic.gdx.graphics.glutils.VertexArray(maxVertices, attributes)
        this.indices = new com.badlogic.gdx.graphics.glutils.IndexArray(maxIndices)
        this.isVertexArray = true
      }
    }
    Mesh.addManagedMesh(com.badlogic.gdx.Gdx.app, this)
  }
  def this(isStatic: scala.Boolean, maxVertices: scala.Int, maxIndices: scala.Int, attributes: scala.Array[com.badlogic.gdx.graphics.VertexAttribute]) = {
    this()
    this.vertices = this.makeVertexBuffer(isStatic, maxVertices, new com.badlogic.gdx.graphics.VertexAttributes(attributes))
    this.indices = new com.badlogic.gdx.graphics.glutils.IndexBufferObject(isStatic, maxIndices)
    this.isVertexArray = false
    Mesh.addManagedMesh(com.badlogic.gdx.Gdx.app, this)
  }
  def this(isStatic: scala.Boolean, maxVertices: scala.Int, maxIndices: scala.Int, attributes: com.badlogic.gdx.graphics.VertexAttributes) = {
    this()
    this.vertices = this.makeVertexBuffer(isStatic, maxVertices, attributes)
    this.indices = new com.badlogic.gdx.graphics.glutils.IndexBufferObject(isStatic, maxIndices)
    this.isVertexArray = false
    Mesh.addManagedMesh(com.badlogic.gdx.Gdx.app, this)
  }
  def this(vertices: com.badlogic.gdx.graphics.glutils.VertexData, indices: com.badlogic.gdx.graphics.glutils.IndexData, isVertexArray: scala.Boolean) = {
    this()
    this.vertices = vertices
    this.indices = indices
    this.isVertexArray = isVertexArray
    Mesh.addManagedMesh(com.badlogic.gdx.Gdx.app, this)
  }
  def this(`type`: com.badlogic.gdx.graphics.Mesh.VertexDataType, isStatic: scala.Boolean, maxVertices: scala.Int, maxIndices: scala.Int, attributes: scala.Array[com.badlogic.gdx.graphics.VertexAttribute]) = {
    this(`type`, isStatic, maxVertices, maxIndices, new com.badlogic.gdx.graphics.VertexAttributes(attributes))
  }
  private def makeVertexBuffer(isStatic: scala.Boolean, maxVertices: scala.Int, vertexAttributes: com.badlogic.gdx.graphics.VertexAttributes): com.badlogic.gdx.graphics.glutils.VertexData = {
    if (com.badlogic.gdx.Gdx.gl30 != null) {
      return new com.badlogic.gdx.graphics.glutils.VertexBufferObjectWithVAO(isStatic, maxVertices, vertexAttributes)
    } else {
      return new com.badlogic.gdx.graphics.glutils.VertexBufferObject(isStatic, maxVertices, vertexAttributes)
    }
  }
  def enableInstancedRendering(isStatic: scala.Boolean, maxInstances: scala.Int, attributes: scala.Array[com.badlogic.gdx.graphics.VertexAttribute]): Mesh = {
    if (!this.isInstanced$field) {
      this.isInstanced$field = true
      this.instances = new com.badlogic.gdx.graphics.glutils.InstanceBufferObject(isStatic, maxInstances, attributes)
    } else {
      throw new com.badlogic.gdx.utils.GdxRuntimeException("Trying to enable InstancedRendering on same Mesh instance twice." + " Use disableInstancedRendering to clean up old InstanceData first")
    }
    return this
  }
  def disableInstancedRendering(): Mesh = {
    if (this.isInstanced$field) {
      this.isInstanced$field = false
      this.instances.dispose()
      this.instances = null
    } else ()
    return this
  }
  def setInstanceData(instanceData: scala.Array[scala.Float], offset: scala.Int, count: scala.Int): Mesh = {
    if (this.instances != null) {
      this.instances.setInstanceData(instanceData, offset, count)
    } else {
      throw new com.badlogic.gdx.utils.GdxRuntimeException("An InstanceBufferObject must be set before setting instance data!")
    }
    return this
  }
  def setInstanceData(instanceData: scala.Array[scala.Float]): Mesh = {
    if (this.instances != null) {
      this.instances.setInstanceData(instanceData, 0, instanceData.length)
    } else {
      throw new com.badlogic.gdx.utils.GdxRuntimeException("An InstanceBufferObject must be set before setting instance data!")
    }
    return this
  }
  def setInstanceData(instanceData: java.nio.FloatBuffer, count: scala.Int): Mesh = {
    if (this.instances != null) {
      this.instances.setInstanceData(instanceData, count)
    } else {
      throw new com.badlogic.gdx.utils.GdxRuntimeException("An InstanceBufferObject must be set before setting instance data!")
    }
    return this
  }
  def setInstanceData(instanceData: java.nio.FloatBuffer): Mesh = {
    if (this.instances != null) {
      this.instances.setInstanceData(instanceData, instanceData.limit())
    } else {
      throw new com.badlogic.gdx.utils.GdxRuntimeException("An InstanceBufferObject must be set before setting instance data!")
    }
    return this
  }
  def updateInstanceData(targetOffset: scala.Int, source: scala.Array[scala.Float]): Mesh = {
    return this.updateInstanceData(targetOffset, source, 0, source.length)
  }
  def updateInstanceData(targetOffset: scala.Int, source: scala.Array[scala.Float], sourceOffset: scala.Int, count: scala.Int): Mesh = {
    this.instances.updateInstanceData(targetOffset, source, sourceOffset, count)
    return this
  }
  def updateInstanceData(targetOffset: scala.Int, source: java.nio.FloatBuffer): Mesh = {
    return this.updateInstanceData(targetOffset, source, 0, source.limit())
  }
  def updateInstanceData(targetOffset: scala.Int, source: java.nio.FloatBuffer, sourceOffset: scala.Int, count: scala.Int): Mesh = {
    this.instances.updateInstanceData(targetOffset, source, sourceOffset, count)
    return this
  }
  def setVertices(vertices: scala.Array[scala.Float]): Mesh = {
    this.vertices.setVertices(vertices, 0, vertices.length)
    return this
  }
  def isInstanced(): scala.Boolean = {
    return this.isInstanced$field
  }
  def setVertices(vertices: scala.Array[scala.Float], offset: scala.Int, count: scala.Int): Mesh = {
    this.vertices.setVertices(vertices, offset, count)
    return this
  }
  def updateVertices(targetOffset: scala.Int, source: scala.Array[scala.Float]): Mesh = {
    return this.updateVertices(targetOffset, source, 0, source.length)
  }
  def updateVertices(targetOffset: scala.Int, source: scala.Array[scala.Float], sourceOffset: scala.Int, count: scala.Int): Mesh = {
    this.vertices.updateVertices(targetOffset, source, sourceOffset, count)
    return this
  }
  def getVertices(vertices: scala.Array[scala.Float]): scala.Array[scala.Float] = {
    return this.getVertices(0, -1, vertices)
  }
  def getVertices(srcOffset: scala.Int, vertices: scala.Array[scala.Float]): scala.Array[scala.Float] = {
    return this.getVertices(srcOffset, -1, vertices)
  }
  def getVertices(srcOffset: scala.Int, count: scala.Int, vertices: scala.Array[scala.Float]): scala.Array[scala.Float] = {
    return this.getVertices(srcOffset, count, vertices, 0)
  }
  def getVertices(srcOffset: scala.Int, count$arg: scala.Int, vertices: scala.Array[scala.Float], destOffset: scala.Int): scala.Array[scala.Float] = {
    var count: scala.Int = count$arg
    val max: scala.Int = (this.getNumVertices() * this.getVertexSize()) / 4
    if (count == (-1)) {
      count = max - srcOffset
      if (count > (vertices.length - destOffset)) {
        count = vertices.length - destOffset
      } else ()
    } else ()
    if (((((srcOffset < 0) || (count <= 0)) || ((srcOffset + count) > max)) || (destOffset < 0)) || (destOffset >= vertices.length)) {
      throw new java.lang.IndexOutOfBoundsException()
    } else ()
    if ((vertices.length - destOffset) < count) {
      throw new java.lang.IllegalArgumentException((("not enough room in vertices array, has " + vertices.length) + " floats, needs ") + count)
    } else ()
    val verticesBuffer: java.nio.FloatBuffer = this.getVerticesBuffer(false)
    val pos: scala.Int = verticesBuffer.position()
    verticesBuffer.asInstanceOf[java.nio.Buffer].position(srcOffset)
    verticesBuffer.get(vertices, destOffset, count)
    verticesBuffer.asInstanceOf[java.nio.Buffer].position(pos)
    return vertices
  }
  def setIndices(indices: scala.Array[scala.Short]): Mesh = {
    this.indices.setIndices(indices, 0, indices.length)
    return this
  }
  def setIndices(indices: scala.Array[scala.Short], offset: scala.Int, count: scala.Int): Mesh = {
    this.indices.setIndices(indices, offset, count)
    return this
  }
  def getIndices(indices: scala.Array[scala.Short]): scala.Unit = {
    this.getIndices(indices, 0)
  }
  def getIndices(indices: scala.Array[scala.Short], destOffset: scala.Int): scala.Unit = {
    this.getIndices(0, indices, destOffset)
  }
  def getIndices(srcOffset: scala.Int, indices: scala.Array[scala.Short], destOffset: scala.Int): scala.Unit = {
    this.getIndices(srcOffset, -1, indices, destOffset)
  }
  def getIndices(srcOffset: scala.Int, count$arg: scala.Int, indices: scala.Array[scala.Short], destOffset: scala.Int): scala.Unit = {
    var count: scala.Int = count$arg
    val max: scala.Int = this.getNumIndices()
    if (count < 0) {
      count = max - srcOffset
    } else ()
    if (((srcOffset < 0) || (srcOffset >= max)) || ((srcOffset + count) > max)) {
      throw new java.lang.IllegalArgumentException((((("Invalid range specified, offset: " + srcOffset) + ", count: ") + count) + ", max: ") + max)
    } else ()
    if ((indices.length - destOffset) < count) {
      throw new java.lang.IllegalArgumentException((("not enough room in indices array, has " + indices.length) + " shorts, needs ") + count)
    } else ()
    val indicesBuffer: java.nio.ShortBuffer = this.getIndicesBuffer(false)
    val pos: scala.Int = indicesBuffer.position()
    indicesBuffer.asInstanceOf[java.nio.Buffer].position(srcOffset)
    indicesBuffer.get(indices, destOffset, count)
    indicesBuffer.asInstanceOf[java.nio.Buffer].position(pos)
  }
  def getNumIndices(): scala.Int = {
    return this.indices.getNumIndices()
  }
  def getNumVertices(): scala.Int = {
    return this.vertices.getNumVertices()
  }
  def getMaxVertices(): scala.Int = {
    return this.vertices.getNumMaxVertices()
  }
  def getMaxIndices(): scala.Int = {
    return this.indices.getNumMaxIndices()
  }
  def getVertexSize(): scala.Int = {
    return this.vertices.getAttributes().vertexSize
  }
  def getIndexData(): com.badlogic.gdx.graphics.glutils.IndexData = {
    return this.indices
  }
  def setAutoBind(autoBind: scala.Boolean): scala.Unit = {
    this.autoBind = autoBind
  }
  def bind(shader: com.badlogic.gdx.graphics.glutils.ShaderProgram): scala.Unit = {
    this.bind(shader, null, null)
  }
  def bind(shader: com.badlogic.gdx.graphics.glutils.ShaderProgram, locations: scala.Array[scala.Int], instanceLocations: scala.Array[scala.Int]): scala.Unit = {
    this.vertices.bind(shader, locations)
    if ((this.instances != null) && (this.instances.getNumInstances() > 0)) {
      this.instances.bind(shader, instanceLocations)
    } else ()
    if (this.indices.getNumIndices() > 0) {
      this.indices.bind()
    } else ()
  }
  def unbind(shader: com.badlogic.gdx.graphics.glutils.ShaderProgram): scala.Unit = {
    this.unbind(shader, null, null)
  }
  def unbind(shader: com.badlogic.gdx.graphics.glutils.ShaderProgram, locations: scala.Array[scala.Int], instanceLocations: scala.Array[scala.Int]): scala.Unit = {
    this.vertices.unbind(shader, locations)
    if ((this.instances != null) && (this.instances.getNumInstances() > 0)) {
      this.instances.unbind(shader, instanceLocations)
    } else ()
    if (this.indices.getNumIndices() > 0) {
      this.indices.unbind()
    } else ()
  }
  def render(shader: com.badlogic.gdx.graphics.glutils.ShaderProgram, primitiveType: scala.Int): scala.Unit = {
    this.render(shader, primitiveType, 0, if (this.indices.getNumMaxIndices() > 0) this.getNumIndices() else this.getNumVertices(), this.autoBind)
  }
  def render(shader: com.badlogic.gdx.graphics.glutils.ShaderProgram, primitiveType: scala.Int, offset: scala.Int, count: scala.Int): scala.Unit = {
    this.render(shader, primitiveType, offset, count, this.autoBind)
  }
  def render(shader: com.badlogic.gdx.graphics.glutils.ShaderProgram, primitiveType: scala.Int, offset: scala.Int, count: scala.Int, autoBind: scala.Boolean): scala.Unit = {
    if (count == 0) {
      return
    } else ()
    if (autoBind) {
      this.bind(shader)
    } else ()
    if (this.isVertexArray) {
      if (this.indices.getNumIndices() > 0) {
        val buffer: java.nio.ShortBuffer = this.indices.getBuffer(false)
        val oldPosition: scala.Int = buffer.position()
        val oldLimit: scala.Int = buffer.limit()
        buffer.asInstanceOf[java.nio.Buffer].position(offset)
        com.badlogic.gdx.Gdx.gl20.glDrawElements(primitiveType, count, com.badlogic.gdx.graphics.GL20.GL_UNSIGNED_SHORT, buffer)
        buffer.asInstanceOf[java.nio.Buffer].position(oldPosition)
      } else {
        com.badlogic.gdx.Gdx.gl20.glDrawArrays(primitiveType, offset, count)
      }
    } else {
      var numInstances: scala.Int = 0
      if (this.isInstanced$field) {
        numInstances = this.instances.getNumInstances()
      } else ()
      if (this.indices.getNumIndices() > 0) {
        if ((count + offset) > this.indices.getNumMaxIndices()) {
          throw new com.badlogic.gdx.utils.GdxRuntimeException(((((("Mesh attempting to access memory outside of the index buffer (count: " + count) + ", offset: ") + offset) + ", max: ") + this.indices.getNumMaxIndices()) + ")")
        } else ()
        if (this.isInstanced$field && (numInstances > 0)) {
          com.badlogic.gdx.Gdx.gl30.glDrawElementsInstanced(primitiveType, count, com.badlogic.gdx.graphics.GL20.GL_UNSIGNED_SHORT, offset * 2, numInstances)
        } else {
          com.badlogic.gdx.Gdx.gl20.glDrawElements(primitiveType, count, com.badlogic.gdx.graphics.GL20.GL_UNSIGNED_SHORT, offset * 2)
        }
      } else {
        if (this.isInstanced$field && (numInstances > 0)) {
          com.badlogic.gdx.Gdx.gl30.glDrawArraysInstanced(primitiveType, offset, count, numInstances)
        } else {
          com.badlogic.gdx.Gdx.gl20.glDrawArrays(primitiveType, offset, count)
        }
      }
    }
    if (autoBind) {
      this.unbind(shader)
    } else ()
  }
  def dispose(): scala.Unit = {
    if (Mesh.meshes.getOrElse(com.badlogic.gdx.Gdx.app, null.asInstanceOf[com.badlogic.gdx.utils.Array[Mesh]]) != null) {
      Mesh.meshes.getOrElse(com.badlogic.gdx.Gdx.app, null.asInstanceOf[com.badlogic.gdx.utils.Array[Mesh]]).removeValue(this, true)
    } else ()
    this.vertices.dispose()
    if (this.instances != null) {
      this.instances.dispose()
    } else ()
    this.indices.dispose()
  }
  def getVertexAttribute(usage: scala.Int): com.badlogic.gdx.graphics.VertexAttribute = {
    val attributes: com.badlogic.gdx.graphics.VertexAttributes = this.vertices.getAttributes()
    val len: scala.Int = attributes.size();
    { var i: scala.Int = 0; while (i < len) { {
      if (attributes.get(i).usage == usage) {
        return attributes.get(i)
      } else ()
    }; i = i + 1 } }
    return null
  }
  def getVertexAttributes(): com.badlogic.gdx.graphics.VertexAttributes = {
    return this.vertices.getAttributes()
  }
  def getInstancedAttributes(): com.badlogic.gdx.graphics.VertexAttributes = {
    return if (this.instances != null) this.instances.getAttributes() else null.asInstanceOf[com.badlogic.gdx.graphics.VertexAttributes]
  }
  def getVerticesBuffer(): java.nio.FloatBuffer = {
    return this.vertices.getBuffer(true)
  }
  def getVerticesBuffer(forWriting: scala.Boolean): java.nio.FloatBuffer = {
    return this.vertices.getBuffer(forWriting)
  }
  def calculateBoundingBox(): com.badlogic.gdx.math.collision.BoundingBox = {
    val bbox: com.badlogic.gdx.math.collision.BoundingBox = new com.badlogic.gdx.math.collision.BoundingBox()
    this.calculateBoundingBox(bbox)
    return bbox
  }
  def calculateBoundingBox(bbox: com.badlogic.gdx.math.collision.BoundingBox): scala.Unit = {
    val numVertices: scala.Int = this.getNumVertices()
    if (numVertices == 0) {
      throw new com.badlogic.gdx.utils.GdxRuntimeException("No vertices defined")
    } else ()
    val verts: java.nio.FloatBuffer = this.vertices.getBuffer(false)
    bbox.inf()
    val posAttrib: com.badlogic.gdx.graphics.VertexAttribute = this.getVertexAttribute(com.badlogic.gdx.graphics.VertexAttributes.Usage.Position)
    val offset: scala.Int = posAttrib.offset / 4
    val vertexSize: scala.Int = this.vertices.getAttributes().vertexSize / 4
    var idx: scala.Int = offset
    posAttrib.numComponents match {
      case 1 => {
        { var i: scala.Int = 0; while (i < numVertices) { {
          bbox.ext(verts.get(idx), 0, 0)
          idx = idx + vertexSize
        }; i = i + 1 } }
      }
      case 2 => {
        { var i: scala.Int = 0; while (i < numVertices) { {
          bbox.ext(verts.get(idx), verts.get(idx + 1), 0)
          idx = idx + vertexSize
        }; i = i + 1 } }
      }
      case 3 => {
        { var i: scala.Int = 0; while (i < numVertices) { {
          bbox.ext(verts.get(idx), verts.get(idx + 1), verts.get(idx + 2))
          idx = idx + vertexSize
        }; i = i + 1 } }
      }
    }
  }
  def calculateBoundingBox(out: com.badlogic.gdx.math.collision.BoundingBox, offset: scala.Int, count: scala.Int): com.badlogic.gdx.math.collision.BoundingBox = {
    return this.extendBoundingBox(out.inf(), offset, count)
  }
  def calculateBoundingBox(out: com.badlogic.gdx.math.collision.BoundingBox, offset: scala.Int, count: scala.Int, transform: com.badlogic.gdx.math.Matrix4): com.badlogic.gdx.math.collision.BoundingBox = {
    return this.extendBoundingBox(out.inf(), offset, count, transform)
  }
  def extendBoundingBox(out: com.badlogic.gdx.math.collision.BoundingBox, offset: scala.Int, count: scala.Int): com.badlogic.gdx.math.collision.BoundingBox = {
    return this.extendBoundingBox(out, offset, count, null)
  }
  def extendBoundingBox(out: com.badlogic.gdx.math.collision.BoundingBox, offset: scala.Int, count: scala.Int, transform: com.badlogic.gdx.math.Matrix4): com.badlogic.gdx.math.collision.BoundingBox = {
    val numIndices: scala.Int = this.getNumIndices()
    val numVertices: scala.Int = this.getNumVertices()
    val max: scala.Int = if (numIndices == 0) numVertices else numIndices
    if (((offset < 0) || (count < 1)) || ((offset + count) > max)) {
      throw new com.badlogic.gdx.utils.GdxRuntimeException(((((("Invalid part specified ( offset=" + offset) + ", count=") + count) + ", max=") + max) + " )")
    } else ()
    val verts: java.nio.FloatBuffer = this.vertices.getBuffer(false)
    val index: java.nio.ShortBuffer = this.indices.getBuffer(false)
    val posAttrib: com.badlogic.gdx.graphics.VertexAttribute = this.getVertexAttribute(com.badlogic.gdx.graphics.VertexAttributes.Usage.Position)
    val posoff: scala.Int = posAttrib.offset / 4
    val vertexSize: scala.Int = this.vertices.getAttributes().vertexSize / 4
    val `end`: scala.Int = offset + count
    posAttrib.numComponents match {
      case 1 => {
        if (numIndices > 0) {
          { var i: scala.Int = offset; while (i < `end`) { {
            val idx: scala.Int = ((index.get(i) & 65535) * vertexSize) + posoff
            this.tmpV.set(verts.get(idx), 0, 0)
            if (transform != null) {
              this.tmpV.mul(transform)
            } else ()
            out.ext(this.tmpV)
          }; i = i + 1 } }
        } else {
          { var i: scala.Int = offset; while (i < `end`) { {
            val idx: scala.Int = (i * vertexSize) + posoff
            this.tmpV.set(verts.get(idx), 0, 0)
            if (transform != null) {
              this.tmpV.mul(transform)
            } else ()
            out.ext(this.tmpV)
          }; i = i + 1 } }
        }
      }
      case 2 => {
        if (numIndices > 0) {
          { var i: scala.Int = offset; while (i < `end`) { {
            val idx: scala.Int = ((index.get(i) & 65535) * vertexSize) + posoff
            this.tmpV.set(verts.get(idx), verts.get(idx + 1), 0)
            if (transform != null) {
              this.tmpV.mul(transform)
            } else ()
            out.ext(this.tmpV)
          }; i = i + 1 } }
        } else {
          { var i: scala.Int = offset; while (i < `end`) { {
            val idx: scala.Int = (i * vertexSize) + posoff
            this.tmpV.set(verts.get(idx), verts.get(idx + 1), 0)
            if (transform != null) {
              this.tmpV.mul(transform)
            } else ()
            out.ext(this.tmpV)
          }; i = i + 1 } }
        }
      }
      case 3 => {
        if (numIndices > 0) {
          { var i: scala.Int = offset; while (i < `end`) { {
            val idx: scala.Int = ((index.get(i) & 65535) * vertexSize) + posoff
            this.tmpV.set(verts.get(idx), verts.get(idx + 1), verts.get(idx + 2))
            if (transform != null) {
              this.tmpV.mul(transform)
            } else ()
            out.ext(this.tmpV)
          }; i = i + 1 } }
        } else {
          { var i: scala.Int = offset; while (i < `end`) { {
            val idx: scala.Int = (i * vertexSize) + posoff
            this.tmpV.set(verts.get(idx), verts.get(idx + 1), verts.get(idx + 2))
            if (transform != null) {
              this.tmpV.mul(transform)
            } else ()
            out.ext(this.tmpV)
          }; i = i + 1 } }
        }
      }
    }
    return out
  }
  def calculateRadiusSquared(centerX: scala.Float, centerY: scala.Float, centerZ: scala.Float, offset: scala.Int, count: scala.Int, transform: com.badlogic.gdx.math.Matrix4): scala.Float = {
    val numIndices: scala.Int = this.getNumIndices()
    if (((offset < 0) || (count < 1)) || ((offset + count) > numIndices)) {
      throw new com.badlogic.gdx.utils.GdxRuntimeException("Not enough indices")
    } else ()
    val verts: java.nio.FloatBuffer = this.vertices.getBuffer(false)
    val index: java.nio.ShortBuffer = this.indices.getBuffer(false)
    val posAttrib: com.badlogic.gdx.graphics.VertexAttribute = this.getVertexAttribute(com.badlogic.gdx.graphics.VertexAttributes.Usage.Position)
    val posoff: scala.Int = posAttrib.offset / 4
    val vertexSize: scala.Int = this.vertices.getAttributes().vertexSize / 4
    val `end`: scala.Int = offset + count
    var result: scala.Float = 0
    posAttrib.numComponents match {
      case 1 => {
        { var i: scala.Int = offset; while (i < `end`) { {
          val idx: scala.Int = ((index.get(i) & 65535) * vertexSize) + posoff
          this.tmpV.set(verts.get(idx), 0, 0)
          if (transform != null) {
            this.tmpV.mul(transform)
          } else ()
          val r: scala.Float = this.tmpV.sub(centerX, centerY, centerZ).len2()
          if (r > result) {
            result = r
          } else ()
        }; i = i + 1 } }
      }
      case 2 => {
        { var i: scala.Int = offset; while (i < `end`) { {
          val idx: scala.Int = ((index.get(i) & 65535) * vertexSize) + posoff
          this.tmpV.set(verts.get(idx), verts.get(idx + 1), 0)
          if (transform != null) {
            this.tmpV.mul(transform)
          } else ()
          val r: scala.Float = this.tmpV.sub(centerX, centerY, centerZ).len2()
          if (r > result) {
            result = r
          } else ()
        }; i = i + 1 } }
      }
      case 3 => {
        { var i: scala.Int = offset; while (i < `end`) { {
          val idx: scala.Int = ((index.get(i) & 65535) * vertexSize) + posoff
          this.tmpV.set(verts.get(idx), verts.get(idx + 1), verts.get(idx + 2))
          if (transform != null) {
            this.tmpV.mul(transform)
          } else ()
          val r: scala.Float = this.tmpV.sub(centerX, centerY, centerZ).len2()
          if (r > result) {
            result = r
          } else ()
        }; i = i + 1 } }
      }
    }
    return result
  }
  def calculateRadius(centerX: scala.Float, centerY: scala.Float, centerZ: scala.Float, offset: scala.Int, count: scala.Int, transform: com.badlogic.gdx.math.Matrix4): scala.Float = {
    return java.lang.Math.sqrt(this.calculateRadiusSquared(centerX, centerY, centerZ, offset, count, transform)).asInstanceOf[scala.Float].asInstanceOf[scala.Float]
  }
  def calculateRadius(center: com.badlogic.gdx.math.Vector3, offset: scala.Int, count: scala.Int, transform: com.badlogic.gdx.math.Matrix4): scala.Float = {
    return this.calculateRadius(center.x, center.y, center.z, offset, count, transform)
  }
  def calculateRadius(centerX: scala.Float, centerY: scala.Float, centerZ: scala.Float, offset: scala.Int, count: scala.Int): scala.Float = {
    return this.calculateRadius(centerX, centerY, centerZ, offset, count, null)
  }
  def calculateRadius(center: com.badlogic.gdx.math.Vector3, offset: scala.Int, count: scala.Int): scala.Float = {
    return this.calculateRadius(center.x, center.y, center.z, offset, count, null)
  }
  def calculateRadius(centerX: scala.Float, centerY: scala.Float, centerZ: scala.Float): scala.Float = {
    return this.calculateRadius(centerX, centerY, centerZ, 0, this.getNumIndices(), null)
  }
  def calculateRadius(center: com.badlogic.gdx.math.Vector3): scala.Float = {
    return this.calculateRadius(center.x, center.y, center.z, 0, this.getNumIndices(), null)
  }
  def getIndicesBuffer(): java.nio.ShortBuffer = {
    return this.indices.getBuffer(true)
  }
  def getIndicesBuffer(forWriting: scala.Boolean): java.nio.ShortBuffer = {
    return this.indices.getBuffer(forWriting)
  }
  def scale(scaleX: scala.Float, scaleY: scala.Float, scaleZ: scala.Float): scala.Unit = {
    val posAttr: com.badlogic.gdx.graphics.VertexAttribute = this.getVertexAttribute(com.badlogic.gdx.graphics.VertexAttributes.Usage.Position)
    val offset: scala.Int = posAttr.offset / 4
    val numComponents: scala.Int = posAttr.numComponents
    val numVertices: scala.Int = this.getNumVertices()
    val vertexSize: scala.Int = this.getVertexSize() / 4
    val vertices: scala.Array[scala.Float] = new scala.Array[scala.Float](numVertices * vertexSize)
    this.getVertices(vertices)
    var idx: scala.Int = offset
    numComponents match {
      case 1 => {
        { var i: scala.Int = 0; while (i < numVertices) { {
          vertices(idx) = vertices(idx) * scaleX
          idx = idx + vertexSize
        }; i = i + 1 } }
      }
      case 2 => {
        { var i: scala.Int = 0; while (i < numVertices) { {
          vertices(idx) = vertices(idx) * scaleX
          vertices(idx + 1) = vertices(idx + 1) * scaleY
          idx = idx + vertexSize
        }; i = i + 1 } }
      }
      case 3 => {
        { var i: scala.Int = 0; while (i < numVertices) { {
          vertices(idx) = vertices(idx) * scaleX
          vertices(idx + 1) = vertices(idx + 1) * scaleY
          vertices(idx + 2) = vertices(idx + 2) * scaleZ
          idx = idx + vertexSize
        }; i = i + 1 } }
      }
    }
    this.setVertices(vertices)
  }
  def transform(matrix: com.badlogic.gdx.math.Matrix4): scala.Unit = {
    this.transform(matrix, 0, this.getNumVertices())
  }
  def transform(matrix: com.badlogic.gdx.math.Matrix4, start: scala.Int, count: scala.Int): scala.Unit = {
    val posAttr: com.badlogic.gdx.graphics.VertexAttribute = this.getVertexAttribute(com.badlogic.gdx.graphics.VertexAttributes.Usage.Position)
    val posOffset: scala.Int = posAttr.offset / 4
    val stride: scala.Int = this.getVertexSize() / 4
    val numComponents: scala.Int = posAttr.numComponents
    val numVertices: scala.Int = this.getNumVertices()
    val vertices: scala.Array[scala.Float] = new scala.Array[scala.Float](count * stride)
    this.getVertices(start * stride, count * stride, vertices)
    Mesh.transform(matrix, vertices, stride, posOffset, numComponents, 0, count)
    this.updateVertices(start * stride, vertices)
  }
  def transformUV(matrix: com.badlogic.gdx.math.Matrix3): scala.Unit = {
    this.transformUV(matrix, 0, this.getNumVertices())
  }
  def transformUV(matrix: com.badlogic.gdx.math.Matrix3, start: scala.Int, count: scala.Int): scala.Unit = {
    val posAttr: com.badlogic.gdx.graphics.VertexAttribute = this.getVertexAttribute(com.badlogic.gdx.graphics.VertexAttributes.Usage.TextureCoordinates)
    val offset: scala.Int = posAttr.offset / 4
    val vertexSize: scala.Int = this.getVertexSize() / 4
    val numVertices: scala.Int = this.getNumVertices()
    val vertices: scala.Array[scala.Float] = new scala.Array[scala.Float](numVertices * vertexSize)
    this.getVertices(0, vertices.length, vertices)
    Mesh.transformUV(matrix, vertices, vertexSize, offset, start, count)
    this.setVertices(vertices, 0, vertices.length)
  }
  def copy(isStatic: scala.Boolean, removeDuplicates: scala.Boolean, usage: scala.Array[scala.Int]): Mesh = {
    val vertexSize: scala.Int = this.getVertexSize() / 4
    var numVertices: scala.Int = this.getNumVertices()
    var vertices: scala.Array[scala.Float] = new scala.Array[scala.Float](numVertices * vertexSize)
    this.getVertices(0, vertices.length, vertices)
    var checks: scala.Array[scala.Short] = null
    var attrs: scala.Array[com.badlogic.gdx.graphics.VertexAttribute] = null
    var newVertexSize: scala.Int = 0
    if (usage != null) {
      var size: scala.Int = 0
      var `as`: scala.Int = 0;
      { var i: scala.Int = 0; while (i < usage.length) { {
        if (this.getVertexAttribute(usage(i)) != null) {
          size = size + this.getVertexAttribute(usage(i)).numComponents
          `as` = `as` + 1
        } else ()
      }; i = i + 1 } }
      if (size > 0) {
        attrs = new scala.Array[com.badlogic.gdx.graphics.VertexAttribute](`as`)
        checks = new scala.Array[scala.Short](size)
        var idx: scala.Int = -1
        var ai: scala.Int = -1;
        { var i: scala.Int = 0; while (i < usage.length) { {
          val a: com.badlogic.gdx.graphics.VertexAttribute = this.getVertexAttribute(usage(i))
          if (a == null) {
            /* continue */ ()
          } else ();
          { var j: scala.Int = 0; while (j < a.numComponents) { {
            checks({ idx += 1; idx }) = (a.offset + j).asInstanceOf[scala.Short].asInstanceOf[scala.Short]
          }; j = j + 1 } }
          attrs({ ai += 1; ai }) = a.copy()
          newVertexSize = newVertexSize + a.numComponents
        }; i = i + 1 } }
      } else ()
    } else ()
    if (checks == null) {
      checks = new scala.Array[scala.Short](vertexSize);
      { var i: scala.Short = 0.asInstanceOf[scala.Short]; while (i < vertexSize) { {
        checks(i) = i
      }; i = (i + 1).asInstanceOf[scala.Short] } }
      newVertexSize = vertexSize
    } else ()
    val numIndices: scala.Int = this.getNumIndices()
    var indices: scala.Array[scala.Short] = null
    if (numIndices > 0) {
      indices = new scala.Array[scala.Short](numIndices)
      this.getIndices(indices)
      if (removeDuplicates || (newVertexSize != vertexSize)) {
        val tmp: scala.Array[scala.Float] = new scala.Array[scala.Float](vertices.length)
        var size: scala.Int = 0;
        { var i: scala.Int = 0; while (i < numIndices) { {
          val idx1: scala.Int = indices(i) * vertexSize
          var newIndex: scala.Short = (-1).asInstanceOf[scala.Short]
          if (removeDuplicates) {
            { var j: scala.Short = 0.asInstanceOf[scala.Short]; while ((j < size) && (newIndex < 0)) { {
              val idx2: scala.Int = j * newVertexSize
              var found: scala.Boolean = true;
              { var k: scala.Int = 0; while ((k < checks.length) && found) { {
                if (tmp(idx2 + k) != vertices(idx1 + checks(k))) {
                  found = false
                } else ()
              }; k = k + 1 } }
              if (found) {
                newIndex = j
              } else ()
            }; j = (j + 1).asInstanceOf[scala.Short] } }
          } else ()
          if (newIndex > 0) {
            indices(i) = newIndex
          } else {
            var idx: scala.Int = size * newVertexSize;
            { var j: scala.Int = 0; while (j < checks.length) { {
              tmp(idx + j) = vertices(idx1 + checks(j))
            }; j = j + 1 } }
            indices(i) = size.asInstanceOf[scala.Short].asInstanceOf[scala.Short]
            size = size + 1
          }
        }; i = i + 1 } }
        vertices = tmp
        numVertices = size
      } else ()
    } else ()
    var result: Mesh = null.asInstanceOf[Mesh]
    if (attrs == null) {
      result = new Mesh(isStatic, numVertices, if (indices == null) 0 else indices.length, this.getVertexAttributes())
    } else {
      result = new Mesh(isStatic, numVertices, if (indices == null) 0 else indices.length, attrs)
    }
    result.setVertices(vertices, 0, numVertices * newVertexSize)
    if (indices != null) {
      result.setIndices(indices)
    } else ()
    return result
  }
  def copy(isStatic: scala.Boolean): Mesh = {
    return this.copy(isStatic, false, null)
  }
}
object Mesh {
  final val meshes: scala.collection.mutable.Map[com.badlogic.gdx.Application, com.badlogic.gdx.utils.Array[Mesh]] = new scala.collection.mutable.HashMap[com.badlogic.gdx.Application, com.badlogic.gdx.utils.Array[Mesh]]()
  private def addManagedMesh(app: com.badlogic.gdx.Application, mesh: Mesh): scala.Unit = {
    var managedResources: com.badlogic.gdx.utils.Array[Mesh] = Mesh.meshes.getOrElse(app, null.asInstanceOf[com.badlogic.gdx.utils.Array[Mesh]])
    if (managedResources == null) {
      managedResources = new com.badlogic.gdx.utils.Array[Mesh]()
    } else ()
    managedResources.add(mesh)
    Mesh.meshes.update(app, managedResources)
  }
  def invalidateAllMeshes(app: com.badlogic.gdx.Application): scala.Unit = {
    val meshesArray: com.badlogic.gdx.utils.Array[Mesh] = Mesh.meshes.getOrElse(app, null.asInstanceOf[com.badlogic.gdx.utils.Array[Mesh]])
    if (meshesArray == null) {
      return
    } else ();
    { var i: scala.Int = 0; while (i < meshesArray.size) { {
      meshesArray.get(i).vertices.invalidate()
      meshesArray.get(i).indices.invalidate()
    }; i = i + 1 } }
  }
  def clearAllMeshes(app: com.badlogic.gdx.Application): scala.Unit = {
    Mesh.meshes -= app
  }
  def getManagedStatus(): java.lang.String = {
    val builder: java.lang.StringBuilder = new java.lang.StringBuilder()
    val i: scala.Int = 0
    builder.append("Managed meshes/app: { ")
    for (app <- Mesh.meshes.keySet) {
      builder.append(Mesh.meshes.getOrElse(app, null.asInstanceOf[com.badlogic.gdx.utils.Array[Mesh]]).size)
      builder.append(" ")
    }
    builder.append("}")
    return builder.toString()
  }
  def transform(matrix: com.badlogic.gdx.math.Matrix4, vertices: scala.Array[scala.Float], vertexSize: scala.Int, offset: scala.Int, dimensions: scala.Int, start: scala.Int, count: scala.Int): scala.Unit = {
    if (((offset < 0) || (dimensions < 1)) || ((offset + dimensions) > vertexSize)) {
      throw new java.lang.IndexOutOfBoundsException()
    } else ()
    if (((start < 0) || (count < 1)) || (((start + count) * vertexSize) > vertices.length)) {
      throw new java.lang.IndexOutOfBoundsException((((((("start = " + start) + ", count = ") + count) + ", vertexSize = ") + vertexSize) + ", length = ") + vertices.length)
    } else ()
    val tmp: com.badlogic.gdx.math.Vector3 = new com.badlogic.gdx.math.Vector3()
    var idx: scala.Int = offset + (start * vertexSize)
    dimensions match {
      case 1 => {
        { var i: scala.Int = 0; while (i < count) { {
          tmp.set(vertices(idx), 0, 0).mul(matrix)
          vertices(idx) = tmp.x
          idx = idx + vertexSize
        }; i = i + 1 } }
      }
      case 2 => {
        { var i: scala.Int = 0; while (i < count) { {
          tmp.set(vertices(idx), vertices(idx + 1), 0).mul(matrix)
          vertices(idx) = tmp.x
          vertices(idx + 1) = tmp.y
          idx = idx + vertexSize
        }; i = i + 1 } }
      }
      case 3 => {
        { var i: scala.Int = 0; while (i < count) { {
          tmp.set(vertices(idx), vertices(idx + 1), vertices(idx + 2)).mul(matrix)
          vertices(idx) = tmp.x
          vertices(idx + 1) = tmp.y
          vertices(idx + 2) = tmp.z
          idx = idx + vertexSize
        }; i = i + 1 } }
      }
    }
  }
  def transformUV(matrix: com.badlogic.gdx.math.Matrix3, vertices: scala.Array[scala.Float], vertexSize: scala.Int, offset: scala.Int, start: scala.Int, count: scala.Int): scala.Unit = {
    if (((start < 0) || (count < 1)) || (((start + count) * vertexSize) > vertices.length)) {
      throw new java.lang.IndexOutOfBoundsException((((((("start = " + start) + ", count = ") + count) + ", vertexSize = ") + vertexSize) + ", length = ") + vertices.length)
    } else ()
    val tmp: com.badlogic.gdx.math.Vector2 = new com.badlogic.gdx.math.Vector2()
    var idx: scala.Int = offset + (start * vertexSize);
    { var i: scala.Int = 0; while (i < count) { {
      tmp.set(vertices(idx), vertices(idx + 1)).mul(matrix)
      vertices(idx) = tmp.x
      vertices(idx + 1) = tmp.y
      idx = idx + vertexSize
    }; i = i + 1 } }
  }
  sealed abstract class VertexDataType {
    def name(): java.lang.String = this.toString()
  }
  object VertexDataType {
    case object VertexArray extends VertexDataType
    case object VertexBufferObject extends VertexDataType
    case object VertexBufferObjectSubData extends VertexDataType
    case object VertexBufferObjectWithVAO extends VertexDataType
    def values(): scala.Array[VertexDataType] = scala.Array(VertexArray, VertexBufferObject, VertexBufferObjectSubData, VertexBufferObjectWithVAO)
    def valueOf(name: java.lang.String): VertexDataType = name match {
      case "VertexArray" => VertexArray
      case "VertexBufferObject" => VertexBufferObject
      case "VertexBufferObjectSubData" => VertexBufferObjectSubData
      case "VertexBufferObjectWithVAO" => VertexBufferObjectWithVAO
      case _ => throw new java.lang.IllegalArgumentException(name)
    }
  }
}