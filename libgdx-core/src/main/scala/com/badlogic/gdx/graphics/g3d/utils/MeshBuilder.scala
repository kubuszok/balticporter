package com.badlogic.gdx.graphics.g3d.utils

class MeshBuilder extends com.badlogic.gdx.graphics.g3d.utils.MeshPartBuilder {
  final val vertTmp1: com.badlogic.gdx.graphics.g3d.utils.MeshPartBuilder.VertexInfo = new com.badlogic.gdx.graphics.g3d.utils.MeshPartBuilder.VertexInfo()
  final val vertTmp2: com.badlogic.gdx.graphics.g3d.utils.MeshPartBuilder.VertexInfo = new com.badlogic.gdx.graphics.g3d.utils.MeshPartBuilder.VertexInfo()
  final val vertTmp3: com.badlogic.gdx.graphics.g3d.utils.MeshPartBuilder.VertexInfo = new com.badlogic.gdx.graphics.g3d.utils.MeshPartBuilder.VertexInfo()
  final val vertTmp4: com.badlogic.gdx.graphics.g3d.utils.MeshPartBuilder.VertexInfo = new com.badlogic.gdx.graphics.g3d.utils.MeshPartBuilder.VertexInfo()
  final val tempC1: com.badlogic.gdx.graphics.Color = new com.badlogic.gdx.graphics.Color()
  var attributes: com.badlogic.gdx.graphics.VertexAttributes = null.asInstanceOf[com.badlogic.gdx.graphics.VertexAttributes]
  var vertices: com.badlogic.gdx.utils.FloatArray = new com.badlogic.gdx.utils.FloatArray()
  var indices: com.badlogic.gdx.utils.ShortArray = new com.badlogic.gdx.utils.ShortArray()
  var stride: scala.Int = 0
  var vindex: scala.Int = 0
  var istart: scala.Int = 0
  var posOffset: scala.Int = 0
  var posSize: scala.Int = 0
  var norOffset: scala.Int = 0
  var biNorOffset: scala.Int = 0
  var tangentOffset: scala.Int = 0
  var colOffset: scala.Int = 0
  var colSize: scala.Int = 0
  var cpOffset: scala.Int = 0
  var uvOffset: scala.Int = 0
  var part$field: com.badlogic.gdx.graphics.g3d.model.MeshPart = null.asInstanceOf[com.badlogic.gdx.graphics.g3d.model.MeshPart]
  var parts: com.badlogic.gdx.utils.Array[com.badlogic.gdx.graphics.g3d.model.MeshPart] = new com.badlogic.gdx.utils.Array[com.badlogic.gdx.graphics.g3d.model.MeshPart]()
  final val color: com.badlogic.gdx.graphics.Color = new com.badlogic.gdx.graphics.Color(com.badlogic.gdx.graphics.Color.WHITE)
  var hasColor: scala.Boolean = false
  var primitiveType: scala.Int = 0
  var uOffset: scala.Float = 0.0f
  var uScale: scala.Float = 1.0f
  var vOffset: scala.Float = 0.0f
  var vScale: scala.Float = 1.0f
  var hasUVTransform: scala.Boolean = false
  var vertex$field: scala.Array[scala.Float] = null.asInstanceOf[scala.Array[scala.Float]]
  var vertexTransformationEnabled: scala.Boolean = false
  final val positionTransform: com.badlogic.gdx.math.Matrix4 = new com.badlogic.gdx.math.Matrix4()
  final val normalTransform: com.badlogic.gdx.math.Matrix3 = new com.badlogic.gdx.math.Matrix3()
  final val bounds: com.badlogic.gdx.math.collision.BoundingBox = new com.badlogic.gdx.math.collision.BoundingBox()
  var lastIndex$field: scala.Int = -1
  private final val tmpNormal: com.badlogic.gdx.math.Vector3 = new com.badlogic.gdx.math.Vector3()
  def begin(attributes: scala.Long): scala.Unit = {
    this.begin(MeshBuilder.createAttributes(attributes), -1)
  }
  def begin(attributes: com.badlogic.gdx.graphics.VertexAttributes): scala.Unit = {
    this.begin(attributes, -1)
  }
  def begin(attributes: scala.Long, primitiveType: scala.Int): scala.Unit = {
    this.begin(MeshBuilder.createAttributes(attributes), primitiveType)
  }
  def begin(attributes: com.badlogic.gdx.graphics.VertexAttributes, primitiveType: scala.Int): scala.Unit = {
    if (this.attributes != null) {
      throw new java.lang.RuntimeException("Call end() first")
    } else ()
    this.attributes = attributes
    this.vertices.clear()
    this.indices.clear()
    this.parts.clear()
    this.vindex = 0
    this.lastIndex$field = -1
    this.istart = 0
    this.part$field = null
    this.stride = attributes.vertexSize / 4
    if ((this.vertex$field == null) || (this.vertex$field.length < this.stride)) {
      this.vertex$field = new scala.Array[scala.Float](this.stride)
    } else ()
    var a: com.badlogic.gdx.graphics.VertexAttribute = attributes.findByUsage(com.badlogic.gdx.graphics.VertexAttributes.Usage.Position)
    if (a == null) {
      throw new com.badlogic.gdx.utils.GdxRuntimeException("Cannot build mesh without position attribute")
    } else ()
    this.posOffset = a.offset / 4
    this.posSize = a.numComponents
    a = attributes.findByUsage(com.badlogic.gdx.graphics.VertexAttributes.Usage.Normal)
    this.norOffset = if (a == null) -1 else a.offset / 4
    a = attributes.findByUsage(com.badlogic.gdx.graphics.VertexAttributes.Usage.BiNormal)
    this.biNorOffset = if (a == null) -1 else a.offset / 4
    a = attributes.findByUsage(com.badlogic.gdx.graphics.VertexAttributes.Usage.Tangent)
    this.tangentOffset = if (a == null) -1 else a.offset / 4
    a = attributes.findByUsage(com.badlogic.gdx.graphics.VertexAttributes.Usage.ColorUnpacked)
    this.colOffset = if (a == null) -1 else a.offset / 4
    this.colSize = if (a == null) 0 else a.numComponents
    a = attributes.findByUsage(com.badlogic.gdx.graphics.VertexAttributes.Usage.ColorPacked)
    this.cpOffset = if (a == null) -1 else a.offset / 4
    a = attributes.findByUsage(com.badlogic.gdx.graphics.VertexAttributes.Usage.TextureCoordinates)
    this.uvOffset = if (a == null) -1 else a.offset / 4
    this.setColor(null)
    this.setVertexTransform(null)
    this.setUVRange(null)
    this.primitiveType = primitiveType
    this.bounds.inf()
  }
  private def endpart(): scala.Unit = {
    if (this.part$field != null) {
      this.bounds.getCenter(this.part$field.center)
      this.bounds.getDimensions(this.part$field.halfExtents).scl(0.5f)
      this.part$field.radius = this.part$field.halfExtents.len()
      this.bounds.inf()
      this.part$field.offset = this.istart
      this.part$field.size = this.indices.size - this.istart
      this.istart = this.indices.size
      this.part$field = null
    } else ()
  }
  def part(id: java.lang.String, primitiveType: scala.Int): com.badlogic.gdx.graphics.g3d.model.MeshPart = {
    return this.part(id, primitiveType, new com.badlogic.gdx.graphics.g3d.model.MeshPart())
  }
  def part(id: java.lang.String, primitiveType: scala.Int, meshPart: com.badlogic.gdx.graphics.g3d.model.MeshPart): com.badlogic.gdx.graphics.g3d.model.MeshPart = {
    if (this.attributes == null) {
      throw new java.lang.RuntimeException("Call begin() first")
    } else ()
    this.endpart()
    this.part$field = meshPart
    this.part$field.id = id
    this.primitiveType = {
      this.part$field.primitiveType = primitiveType
      this.part$field.primitiveType
    }
    this.parts.add(this.part$field)
    this.setColor(null)
    this.setVertexTransform(null)
    this.setUVRange(null)
    return this.part$field
  }
  def `end`(mesh: com.badlogic.gdx.graphics.Mesh): com.badlogic.gdx.graphics.Mesh = {
    this.endpart()
    if (this.attributes == null) {
      throw new com.badlogic.gdx.utils.GdxRuntimeException("Call begin() first")
    } else ()
    if (!this.attributes.equals(mesh.getVertexAttributes())) {
      throw new com.badlogic.gdx.utils.GdxRuntimeException("Mesh attributes don't match")
    } else ()
    if ((mesh.getMaxVertices() * this.stride) < this.vertices.size) {
      throw new com.badlogic.gdx.utils.GdxRuntimeException((((("Mesh can't hold enough vertices: " + mesh.getMaxVertices()) + " * ") + this.stride) + " < ") + this.vertices.size)
    } else ()
    if (mesh.getMaxIndices() < this.indices.size) {
      throw new com.badlogic.gdx.utils.GdxRuntimeException((("Mesh can't hold enough indices: " + mesh.getMaxIndices()) + " < ") + this.indices.size)
    } else ()
    mesh.setVertices(this.vertices.items, 0, this.vertices.size)
    mesh.setIndices(this.indices.items, 0, this.indices.size)
    for (p <- this.parts) {
      p.mesh = mesh
    }
    this.parts.clear()
    this.attributes = null
    this.vertices.clear()
    this.indices.clear()
    return mesh
  }
  def `end`(): com.badlogic.gdx.graphics.Mesh = {
    return this.`end`(new com.badlogic.gdx.graphics.Mesh(true, java.lang.Math.min(this.vertices.size / this.stride, MeshBuilder.MAX_VERTICES), this.indices.size, this.attributes))
  }
  def clear(): scala.Unit = {
    this.vertices.clear()
    this.indices.clear()
    this.parts.clear()
    this.vindex = 0
    this.lastIndex$field = -1
    this.istart = 0
    this.part$field = null
  }
  def getFloatsPerVertex(): scala.Int = {
    return this.stride
  }
  def getNumVertices(): scala.Int = {
    return this.vertices.size / this.stride
  }
  def getVertices(out: scala.Array[scala.Float], destOffset: scala.Int): scala.Unit = {
    if (this.attributes == null) {
      throw new com.badlogic.gdx.utils.GdxRuntimeException("Must be called in between #begin and #end")
    } else ()
    if ((destOffset < 0) || (destOffset > (out.length - this.vertices.size))) {
      throw new com.badlogic.gdx.utils.GdxRuntimeException("Array too small or offset out of range")
    } else ()
    java.lang.System.arraycopy(this.vertices.items, 0, out, destOffset, this.vertices.size)
  }
  def getVertices(): scala.Array[scala.Float] = {
    return this.vertices.items
  }
  def getNumIndices(): scala.Int = {
    return this.indices.size
  }
  def getIndices(out: scala.Array[scala.Short], destOffset: scala.Int): scala.Unit = {
    if (this.attributes == null) {
      throw new com.badlogic.gdx.utils.GdxRuntimeException("Must be called in between #begin and #end")
    } else ()
    if ((destOffset < 0) || (destOffset > (out.length - this.indices.size))) {
      throw new com.badlogic.gdx.utils.GdxRuntimeException("Array too small or offset out of range")
    } else ()
    java.lang.System.arraycopy(this.indices.items, 0, out, destOffset, this.indices.size)
  }
  def getIndices(): scala.Array[scala.Short] = {
    return this.indices.items
  }
  def getAttributes(): com.badlogic.gdx.graphics.VertexAttributes = {
    return this.attributes
  }
  def getMeshPart(): com.badlogic.gdx.graphics.g3d.model.MeshPart = {
    return this.part$field
  }
  def getPrimitiveType(): scala.Int = {
    return this.primitiveType
  }
  def setColor(r: scala.Float, g: scala.Float, b: scala.Float, a: scala.Float): scala.Unit = {
    this.color.set(r, g, b, a)
    this.hasColor = !this.color.equals(com.badlogic.gdx.graphics.Color.WHITE)
  }
  def setColor(color: com.badlogic.gdx.graphics.Color): scala.Unit = {
    this.color.set(if (!{
      this.hasColor = color != null
      this.hasColor
    }) com.badlogic.gdx.graphics.Color.WHITE else color)
  }
  def setUVRange(u1: scala.Float, v1: scala.Float, u2: scala.Float, v2: scala.Float): scala.Unit = {
    this.uOffset = u1
    this.vOffset = v1
    this.uScale = u2 - u1
    this.vScale = v2 - v1
    this.hasUVTransform = !(((com.badlogic.gdx.math.MathUtils.isZero(u1) && com.badlogic.gdx.math.MathUtils.isZero(v1)) && com.badlogic.gdx.math.MathUtils.isEqual(u2, 1.0f)) && com.badlogic.gdx.math.MathUtils.isEqual(v2, 1.0f))
  }
  def setUVRange(region: com.badlogic.gdx.graphics.g2d.TextureRegion): scala.Unit = {
    if (region == null) {
      this.hasUVTransform = false
      this.uOffset = {
        this.vOffset = 0.0f
        this.vOffset
      }
      this.uScale = {
        this.vScale = 1.0f
        this.vScale
      }
    } else {
      this.hasUVTransform = true
      this.setUVRange(region.getU(), region.getV(), region.getU2(), region.getV2())
    }
  }
  def getVertexTransform(out: com.badlogic.gdx.math.Matrix4): com.badlogic.gdx.math.Matrix4 = {
    return out.set(this.positionTransform)
  }
  def setVertexTransform(transform: com.badlogic.gdx.math.Matrix4): scala.Unit = {
    this.vertexTransformationEnabled = transform != null
    if (this.vertexTransformationEnabled) {
      this.positionTransform.set(transform)
      this.normalTransform.set(transform).inv().transpose()
    } else {
      this.positionTransform.idt()
      this.normalTransform.idt()
    }
  }
  def isVertexTransformationEnabled(): scala.Boolean = {
    return this.vertexTransformationEnabled
  }
  def setVertexTransformationEnabled(enabled: scala.Boolean): scala.Unit = {
    this.vertexTransformationEnabled = enabled
  }
  def ensureVertices(numVertices: scala.Int): scala.Unit = {
    this.vertices.ensureCapacity(this.stride * numVertices)
  }
  def ensureIndices(numIndices: scala.Int): scala.Unit = {
    this.indices.ensureCapacity(numIndices)
  }
  def ensureCapacity(numVertices: scala.Int, numIndices: scala.Int): scala.Unit = {
    this.ensureVertices(numVertices)
    this.ensureIndices(numIndices)
  }
  def ensureTriangleIndices(numTriangles: scala.Int): scala.Unit = {
    if (this.primitiveType == com.badlogic.gdx.graphics.GL20.GL_LINES) {
      this.ensureIndices(6 * numTriangles)
    } else {
      if ((this.primitiveType == com.badlogic.gdx.graphics.GL20.GL_TRIANGLES) || (this.primitiveType == com.badlogic.gdx.graphics.GL20.GL_POINTS)) {
        this.ensureIndices(3 * numTriangles)
      } else {
        throw new com.badlogic.gdx.utils.GdxRuntimeException("Incorrect primtive type")
      }
    }
  }
  def ensureTriangles(numVertices: scala.Int, numTriangles: scala.Int): scala.Unit = {
    this.ensureVertices(numVertices)
    this.ensureTriangleIndices(numTriangles)
  }
  def ensureTriangles(numTriangles: scala.Int): scala.Unit = {
    this.ensureVertices(3 * numTriangles)
    this.ensureTriangleIndices(numTriangles)
  }
  def ensureRectangleIndices(numRectangles: scala.Int): scala.Unit = {
    if (this.primitiveType == com.badlogic.gdx.graphics.GL20.GL_POINTS) {
      this.ensureIndices(4 * numRectangles)
    } else {
      if (this.primitiveType == com.badlogic.gdx.graphics.GL20.GL_LINES) {
        this.ensureIndices(8 * numRectangles)
      } else {
        this.ensureIndices(6 * numRectangles)
      }
    }
  }
  def ensureRectangles(numVertices: scala.Int, numRectangles: scala.Int): scala.Unit = {
    this.ensureVertices(numVertices)
    this.ensureRectangleIndices(numRectangles)
  }
  def ensureRectangles(numRectangles: scala.Int): scala.Unit = {
    this.ensureVertices(4 * numRectangles)
    this.ensureRectangleIndices(numRectangles)
  }
  def lastIndex(): scala.Int = {
    return this.lastIndex$field
  }
  private final def addVertex(values: scala.Array[scala.Float], offset: scala.Int): scala.Unit = {
    val o: scala.Int = this.vertices.size
    this.vertices.addAll(values, offset, this.stride)
    this.lastIndex$field = { this.vindex += 1; this.vindex }
    if (this.vertexTransformationEnabled) {
      MeshBuilder.transformPosition(this.vertices.items, o + this.posOffset, this.posSize, this.positionTransform)
      if (this.norOffset >= 0) {
        MeshBuilder.transformNormal(this.vertices.items, o + this.norOffset, 3, this.normalTransform)
      } else ()
      if (this.biNorOffset >= 0) {
        MeshBuilder.transformNormal(this.vertices.items, o + this.biNorOffset, 3, this.normalTransform)
      } else ()
      if (this.tangentOffset >= 0) {
        MeshBuilder.transformNormal(this.vertices.items, o + this.tangentOffset, 3, this.normalTransform)
      } else ()
    } else ()
    val x: scala.Float = this.vertices.items(o + this.posOffset)
    val y: scala.Float = if (this.posSize > 1) this.vertices.items((o + this.posOffset) + 1) else 0.0f
    val z: scala.Float = if (this.posSize > 2) this.vertices.items((o + this.posOffset) + 2) else 0.0f
    this.bounds.ext(x, y, z)
    if (this.hasColor) {
      if (this.colOffset >= 0) {
        this.vertices.items(o + this.colOffset) = this.vertices.items(o + this.colOffset) * this.color.r
        this.vertices.items((o + this.colOffset) + 1) = this.vertices.items((o + this.colOffset) + 1) * this.color.g
        this.vertices.items((o + this.colOffset) + 2) = this.vertices.items((o + this.colOffset) + 2) * this.color.b
        if (this.colSize > 3) {
          this.vertices.items((o + this.colOffset) + 3) = this.vertices.items((o + this.colOffset) + 3) * this.color.a
        } else ()
      } else {
        if (this.cpOffset >= 0) {
          com.badlogic.gdx.graphics.Color.abgr8888ToColor(this.tempC1, this.vertices.items(o + this.cpOffset))
          this.vertices.items(o + this.cpOffset) = this.tempC1.mul(this.color).toFloatBits()
        } else ()
      }
    } else ()
    if (this.hasUVTransform && (this.uvOffset >= 0)) {
      this.vertices.items(o + this.uvOffset) = this.uOffset + (this.uScale * this.vertices.items(o + this.uvOffset))
      this.vertices.items((o + this.uvOffset) + 1) = this.vOffset + (this.vScale * this.vertices.items((o + this.uvOffset) + 1))
    } else ()
  }
  def vertex(pos: com.badlogic.gdx.math.Vector3, nor$arg: com.badlogic.gdx.math.Vector3, col$arg: com.badlogic.gdx.graphics.Color, uv: com.badlogic.gdx.math.Vector2): scala.Short = {
    var nor: com.badlogic.gdx.math.Vector3 = nor$arg
    var col: com.badlogic.gdx.graphics.Color = col$arg
    if (this.vindex > MeshBuilder.MAX_INDEX) {
      throw new com.badlogic.gdx.utils.GdxRuntimeException("Too many vertices used")
    } else ()
    this.vertex$field(this.posOffset) = pos.x
    if (this.posSize > 1) {
      this.vertex$field(this.posOffset + 1) = pos.y
    } else ()
    if (this.posSize > 2) {
      this.vertex$field(this.posOffset + 2) = pos.z
    } else ()
    if (this.norOffset >= 0) {
      if (nor == null) {
        nor = this.tmpNormal.set(pos).nor()
      } else ()
      this.vertex$field(this.norOffset) = nor.x
      this.vertex$field(this.norOffset + 1) = nor.y
      this.vertex$field(this.norOffset + 2) = nor.z
    } else ()
    if (this.colOffset >= 0) {
      if (col == null) {
        col = com.badlogic.gdx.graphics.Color.WHITE
      } else ()
      this.vertex$field(this.colOffset) = col.r
      this.vertex$field(this.colOffset + 1) = col.g
      this.vertex$field(this.colOffset + 2) = col.b
      if (this.colSize > 3) {
        this.vertex$field(this.colOffset + 3) = col.a
      } else ()
    } else {
      if (this.cpOffset > 0) {
        if (col == null) {
          col = com.badlogic.gdx.graphics.Color.WHITE
        } else ()
        this.vertex$field(this.cpOffset) = col.toFloatBits()
      } else ()
    }
    if ((uv != null) && (this.uvOffset >= 0)) {
      this.vertex$field(this.uvOffset) = uv.x
      this.vertex$field(this.uvOffset + 1) = uv.y
    } else ()
    this.addVertex(this.vertex$field, 0)
    return this.lastIndex$field.asInstanceOf[scala.Short].asInstanceOf[scala.Short]
  }
  def vertex(values: scala.Array[scala.Float]): scala.Short = {
    val n: scala.Int = values.length - this.stride;
    { var i: scala.Int = 0; while (i <= n) { {
      this.addVertex(values, i)
    }; i = i + this.stride } }
    return this.lastIndex$field.asInstanceOf[scala.Short].asInstanceOf[scala.Short]
  }
  def vertex(info: com.badlogic.gdx.graphics.g3d.utils.MeshPartBuilder.VertexInfo): scala.Short = {
    return this.vertex(if (info.hasPosition) info.position else null.asInstanceOf[com.badlogic.gdx.math.Vector3], if (info.hasNormal) info.normal else null.asInstanceOf[com.badlogic.gdx.math.Vector3], if (info.hasColor) info.color else null.asInstanceOf[com.badlogic.gdx.graphics.Color], if (info.hasUV) info.uv else null.asInstanceOf[com.badlogic.gdx.math.Vector2])
  }
  def index(value: scala.Short): scala.Unit = {
    this.indices.add(value)
  }
  def index(value1: scala.Short, value2: scala.Short): scala.Unit = {
    this.ensureIndices(2)
    this.indices.add(value1)
    this.indices.add(value2)
  }
  def index(value1: scala.Short, value2: scala.Short, value3: scala.Short): scala.Unit = {
    this.ensureIndices(3)
    this.indices.add(value1)
    this.indices.add(value2)
    this.indices.add(value3)
  }
  def index(value1: scala.Short, value2: scala.Short, value3: scala.Short, value4: scala.Short): scala.Unit = {
    this.ensureIndices(4)
    this.indices.add(value1)
    this.indices.add(value2)
    this.indices.add(value3)
    this.indices.add(value4)
  }
  def index(value1: scala.Short, value2: scala.Short, value3: scala.Short, value4: scala.Short, value5: scala.Short, value6: scala.Short): scala.Unit = {
    this.ensureIndices(6)
    this.indices.add(value1)
    this.indices.add(value2)
    this.indices.add(value3)
    this.indices.add(value4)
    this.indices.add(value5)
    this.indices.add(value6)
  }
  def index(value1: scala.Short, value2: scala.Short, value3: scala.Short, value4: scala.Short, value5: scala.Short, value6: scala.Short, value7: scala.Short, value8: scala.Short): scala.Unit = {
    this.ensureIndices(8)
    this.indices.add(value1)
    this.indices.add(value2)
    this.indices.add(value3)
    this.indices.add(value4)
    this.indices.add(value5)
    this.indices.add(value6)
    this.indices.add(value7)
    this.indices.add(value8)
  }
  def line(index1: scala.Short, index2: scala.Short): scala.Unit = {
    if (this.primitiveType != com.badlogic.gdx.graphics.GL20.GL_LINES) {
      throw new com.badlogic.gdx.utils.GdxRuntimeException("Incorrect primitive type")
    } else ()
    this.index(index1, index2)
  }
  def line(p1: com.badlogic.gdx.graphics.g3d.utils.MeshPartBuilder.VertexInfo, p2: com.badlogic.gdx.graphics.g3d.utils.MeshPartBuilder.VertexInfo): scala.Unit = {
    this.ensureVertices(2)
    this.line(this.vertex(p1), this.vertex(p2))
  }
  def line(p1: com.badlogic.gdx.math.Vector3, p2: com.badlogic.gdx.math.Vector3): scala.Unit = {
    this.line(this.vertTmp1.set(p1, null, null, null), this.vertTmp2.set(p2, null, null, null))
  }
  def line(x1: scala.Float, y1: scala.Float, z1: scala.Float, x2: scala.Float, y2: scala.Float, z2: scala.Float): scala.Unit = {
    this.line(this.vertTmp1.set(null, null, null, null).setPos(x1, y1, z1), this.vertTmp2.set(null, null, null, null).setPos(x2, y2, z2))
  }
  def line(p1: com.badlogic.gdx.math.Vector3, c1: com.badlogic.gdx.graphics.Color, p2: com.badlogic.gdx.math.Vector3, c2: com.badlogic.gdx.graphics.Color): scala.Unit = {
    this.line(this.vertTmp1.set(p1, null, c1, null), this.vertTmp2.set(p2, null, c2, null))
  }
  def triangle(index1: scala.Short, index2: scala.Short, index3: scala.Short): scala.Unit = {
    if ((this.primitiveType == com.badlogic.gdx.graphics.GL20.GL_TRIANGLES) || (this.primitiveType == com.badlogic.gdx.graphics.GL20.GL_POINTS)) {
      this.index(index1, index2, index3)
    } else {
      if (this.primitiveType == com.badlogic.gdx.graphics.GL20.GL_LINES) {
        this.index(index1, index2, index2, index3, index3, index1)
      } else {
        throw new com.badlogic.gdx.utils.GdxRuntimeException("Incorrect primitive type")
      }
    }
  }
  def triangle(p1: com.badlogic.gdx.graphics.g3d.utils.MeshPartBuilder.VertexInfo, p2: com.badlogic.gdx.graphics.g3d.utils.MeshPartBuilder.VertexInfo, p3: com.badlogic.gdx.graphics.g3d.utils.MeshPartBuilder.VertexInfo): scala.Unit = {
    this.ensureVertices(3)
    this.triangle(this.vertex(p1), this.vertex(p2), this.vertex(p3))
  }
  def triangle(p1: com.badlogic.gdx.math.Vector3, p2: com.badlogic.gdx.math.Vector3, p3: com.badlogic.gdx.math.Vector3): scala.Unit = {
    this.triangle(this.vertTmp1.set(p1, null, null, null), this.vertTmp2.set(p2, null, null, null), this.vertTmp3.set(p3, null, null, null))
  }
  def triangle(p1: com.badlogic.gdx.math.Vector3, c1: com.badlogic.gdx.graphics.Color, p2: com.badlogic.gdx.math.Vector3, c2: com.badlogic.gdx.graphics.Color, p3: com.badlogic.gdx.math.Vector3, c3: com.badlogic.gdx.graphics.Color): scala.Unit = {
    this.triangle(this.vertTmp1.set(p1, null, c1, null), this.vertTmp2.set(p2, null, c2, null), this.vertTmp3.set(p3, null, c3, null))
  }
  def rect(corner00: scala.Short, corner10: scala.Short, corner11: scala.Short, corner01: scala.Short): scala.Unit = {
    if (this.primitiveType == com.badlogic.gdx.graphics.GL20.GL_TRIANGLES) {
      this.index(corner00, corner10, corner11, corner11, corner01, corner00)
    } else {
      if (this.primitiveType == com.badlogic.gdx.graphics.GL20.GL_LINES) {
        this.index(corner00, corner10, corner10, corner11, corner11, corner01, corner01, corner00)
      } else {
        if (this.primitiveType == com.badlogic.gdx.graphics.GL20.GL_POINTS) {
          this.index(corner00, corner10, corner11, corner01)
        } else {
          throw new com.badlogic.gdx.utils.GdxRuntimeException("Incorrect primitive type")
        }
      }
    }
  }
  def rect(corner00: com.badlogic.gdx.graphics.g3d.utils.MeshPartBuilder.VertexInfo, corner10: com.badlogic.gdx.graphics.g3d.utils.MeshPartBuilder.VertexInfo, corner11: com.badlogic.gdx.graphics.g3d.utils.MeshPartBuilder.VertexInfo, corner01: com.badlogic.gdx.graphics.g3d.utils.MeshPartBuilder.VertexInfo): scala.Unit = {
    this.ensureVertices(4)
    this.rect(this.vertex(corner00), this.vertex(corner10), this.vertex(corner11), this.vertex(corner01))
  }
  def rect(corner00: com.badlogic.gdx.math.Vector3, corner10: com.badlogic.gdx.math.Vector3, corner11: com.badlogic.gdx.math.Vector3, corner01: com.badlogic.gdx.math.Vector3, normal: com.badlogic.gdx.math.Vector3): scala.Unit = {
    this.rect(this.vertTmp1.set(corner00, normal, null, null).setUV(0.0f, 1.0f), this.vertTmp2.set(corner10, normal, null, null).setUV(1.0f, 1.0f), this.vertTmp3.set(corner11, normal, null, null).setUV(1.0f, 0.0f), this.vertTmp4.set(corner01, normal, null, null).setUV(0.0f, 0.0f))
  }
  def rect(x00: scala.Float, y00: scala.Float, z00: scala.Float, x10: scala.Float, y10: scala.Float, z10: scala.Float, x11: scala.Float, y11: scala.Float, z11: scala.Float, x01: scala.Float, y01: scala.Float, z01: scala.Float, normalX: scala.Float, normalY: scala.Float, normalZ: scala.Float): scala.Unit = {
    this.rect(this.vertTmp1.set(null, null, null, null).setPos(x00, y00, z00).setNor(normalX, normalY, normalZ).setUV(0.0f, 1.0f), this.vertTmp2.set(null, null, null, null).setPos(x10, y10, z10).setNor(normalX, normalY, normalZ).setUV(1.0f, 1.0f), this.vertTmp3.set(null, null, null, null).setPos(x11, y11, z11).setNor(normalX, normalY, normalZ).setUV(1.0f, 0.0f), this.vertTmp4.set(null, null, null, null).setPos(x01, y01, z01).setNor(normalX, normalY, normalZ).setUV(0.0f, 0.0f))
  }
  def addMesh(mesh: com.badlogic.gdx.graphics.Mesh): scala.Unit = {
    this.addMesh(mesh, 0, mesh.getNumIndices())
  }
  def addMesh(meshpart: com.badlogic.gdx.graphics.g3d.model.MeshPart): scala.Unit = {
    if (meshpart.primitiveType != this.primitiveType) {
      throw new com.badlogic.gdx.utils.GdxRuntimeException("Primitive type doesn't match")
    } else ()
    this.addMesh(meshpart.mesh, meshpart.offset, meshpart.size)
  }
  def addMesh(mesh: com.badlogic.gdx.graphics.Mesh, indexOffset: scala.Int, numIndices: scala.Int): scala.Unit = {
    if (!this.attributes.equals(mesh.getVertexAttributes())) {
      throw new com.badlogic.gdx.utils.GdxRuntimeException("Vertex attributes do not match")
    } else ()
    if (numIndices <= 0) {
      return
    } else ()
    val numFloats: scala.Int = mesh.getNumVertices() * this.stride
    MeshBuilder.tmpVertices.clear()
    MeshBuilder.tmpVertices.ensureCapacity(numFloats)
    MeshBuilder.tmpVertices.size = numFloats
    mesh.getVertices(MeshBuilder.tmpVertices.items)
    MeshBuilder.tmpIndices.clear()
    MeshBuilder.tmpIndices.ensureCapacity(numIndices)
    MeshBuilder.tmpIndices.size = numIndices
    mesh.getIndices(indexOffset, numIndices, MeshBuilder.tmpIndices.items, 0)
    this.addMesh(MeshBuilder.tmpVertices.items, MeshBuilder.tmpIndices.items, 0, numIndices)
  }
  def addMesh(vertices: scala.Array[scala.Float], indices: scala.Array[scala.Short], indexOffset: scala.Int, numIndices: scala.Int): scala.Unit = {
    if (MeshBuilder.indicesMap == null) {
      MeshBuilder.indicesMap = new com.badlogic.gdx.utils.IntIntMap(numIndices)
    } else {
      MeshBuilder.indicesMap.clear()
      MeshBuilder.indicesMap.ensureCapacity(numIndices)
    }
    this.ensureIndices(numIndices)
    val numVertices: scala.Int = vertices.length / this.stride
    this.ensureVertices(if (numVertices < numIndices) numVertices else numIndices);
    { var i: scala.Int = 0; while (i < numIndices) { {
      val sidx: scala.Int = indices(indexOffset + i) & 65535
      var didx: scala.Int = MeshBuilder.indicesMap.get(sidx, -1)
      if (didx < 0) {
        this.addVertex(vertices, sidx * this.stride)
        MeshBuilder.indicesMap.put(sidx, {
          didx = this.lastIndex$field
          didx
        })
      } else ()
      this.index(didx.asInstanceOf[scala.Short].asInstanceOf[scala.Short])
    }; i = i + 1 } }
  }
  def addMesh(vertices: scala.Array[scala.Float], indices: scala.Array[scala.Short]): scala.Unit = {
    val offset: scala.Int = this.lastIndex$field + 1
    val numVertices: scala.Int = vertices.length / this.stride
    this.ensureVertices(numVertices);
    { var v: scala.Int = 0; while (v < vertices.length) { {
      this.addVertex(vertices, v)
    }; v = v + this.stride } }
    this.ensureIndices(indices.length);
    { var i: scala.Int = 0; while (i < indices.length) { {
      this.index(((indices(i) & 65535) + offset).asInstanceOf[scala.Short].asInstanceOf[scala.Short])
    }; i = i + 1 } }
  }
  def patch(corner00: com.badlogic.gdx.graphics.g3d.utils.MeshPartBuilder.VertexInfo, corner10: com.badlogic.gdx.graphics.g3d.utils.MeshPartBuilder.VertexInfo, corner11: com.badlogic.gdx.graphics.g3d.utils.MeshPartBuilder.VertexInfo, corner01: com.badlogic.gdx.graphics.g3d.utils.MeshPartBuilder.VertexInfo, divisionsU: scala.Int, divisionsV: scala.Int): scala.Unit = {
    com.badlogic.gdx.graphics.g3d.utils.shapebuilders.PatchShapeBuilder.build(this, corner00, corner10, corner11, corner01, divisionsU, divisionsV)
  }
  def patch(corner00: com.badlogic.gdx.math.Vector3, corner10: com.badlogic.gdx.math.Vector3, corner11: com.badlogic.gdx.math.Vector3, corner01: com.badlogic.gdx.math.Vector3, normal: com.badlogic.gdx.math.Vector3, divisionsU: scala.Int, divisionsV: scala.Int): scala.Unit = {
    com.badlogic.gdx.graphics.g3d.utils.shapebuilders.PatchShapeBuilder.build(this, corner00, corner10, corner11, corner01, normal, divisionsU, divisionsV)
  }
  def patch(x00: scala.Float, y00: scala.Float, z00: scala.Float, x10: scala.Float, y10: scala.Float, z10: scala.Float, x11: scala.Float, y11: scala.Float, z11: scala.Float, x01: scala.Float, y01: scala.Float, z01: scala.Float, normalX: scala.Float, normalY: scala.Float, normalZ: scala.Float, divisionsU: scala.Int, divisionsV: scala.Int): scala.Unit = {
    com.badlogic.gdx.graphics.g3d.utils.shapebuilders.PatchShapeBuilder.build(this, x00, y00, z00, x10, y10, z10, x11, y11, z11, x01, y01, z01, normalX, normalY, normalZ, divisionsU, divisionsV)
  }
  def box(corner000: com.badlogic.gdx.graphics.g3d.utils.MeshPartBuilder.VertexInfo, corner010: com.badlogic.gdx.graphics.g3d.utils.MeshPartBuilder.VertexInfo, corner100: com.badlogic.gdx.graphics.g3d.utils.MeshPartBuilder.VertexInfo, corner110: com.badlogic.gdx.graphics.g3d.utils.MeshPartBuilder.VertexInfo, corner001: com.badlogic.gdx.graphics.g3d.utils.MeshPartBuilder.VertexInfo, corner011: com.badlogic.gdx.graphics.g3d.utils.MeshPartBuilder.VertexInfo, corner101: com.badlogic.gdx.graphics.g3d.utils.MeshPartBuilder.VertexInfo, corner111: com.badlogic.gdx.graphics.g3d.utils.MeshPartBuilder.VertexInfo): scala.Unit = {
    com.badlogic.gdx.graphics.g3d.utils.shapebuilders.BoxShapeBuilder.build(this, corner000, corner010, corner100, corner110, corner001, corner011, corner101, corner111)
  }
  def box(corner000: com.badlogic.gdx.math.Vector3, corner010: com.badlogic.gdx.math.Vector3, corner100: com.badlogic.gdx.math.Vector3, corner110: com.badlogic.gdx.math.Vector3, corner001: com.badlogic.gdx.math.Vector3, corner011: com.badlogic.gdx.math.Vector3, corner101: com.badlogic.gdx.math.Vector3, corner111: com.badlogic.gdx.math.Vector3): scala.Unit = {
    com.badlogic.gdx.graphics.g3d.utils.shapebuilders.BoxShapeBuilder.build(this, corner000, corner010, corner100, corner110, corner001, corner011, corner101, corner111)
  }
  def box(transform: com.badlogic.gdx.math.Matrix4): scala.Unit = {
    com.badlogic.gdx.graphics.g3d.utils.shapebuilders.BoxShapeBuilder.build(this, transform)
  }
  def box(width: scala.Float, height: scala.Float, depth: scala.Float): scala.Unit = {
    com.badlogic.gdx.graphics.g3d.utils.shapebuilders.BoxShapeBuilder.build(this, width, height, depth)
  }
  def box(x: scala.Float, y: scala.Float, z: scala.Float, width: scala.Float, height: scala.Float, depth: scala.Float): scala.Unit = {
    com.badlogic.gdx.graphics.g3d.utils.shapebuilders.BoxShapeBuilder.build(this, x, y, z, width, height, depth)
  }
  def circle(radius: scala.Float, divisions: scala.Int, centerX: scala.Float, centerY: scala.Float, centerZ: scala.Float, normalX: scala.Float, normalY: scala.Float, normalZ: scala.Float): scala.Unit = {
    com.badlogic.gdx.graphics.g3d.utils.shapebuilders.EllipseShapeBuilder.build(this, radius, divisions, centerX, centerY, centerZ, normalX, normalY, normalZ)
  }
  def circle(radius: scala.Float, divisions: scala.Int, center: com.badlogic.gdx.math.Vector3, normal: com.badlogic.gdx.math.Vector3): scala.Unit = {
    com.badlogic.gdx.graphics.g3d.utils.shapebuilders.EllipseShapeBuilder.build(this, radius, divisions, center, normal)
  }
  def circle(radius: scala.Float, divisions: scala.Int, center: com.badlogic.gdx.math.Vector3, normal: com.badlogic.gdx.math.Vector3, tangent: com.badlogic.gdx.math.Vector3, binormal: com.badlogic.gdx.math.Vector3): scala.Unit = {
    com.badlogic.gdx.graphics.g3d.utils.shapebuilders.EllipseShapeBuilder.build(this, radius, divisions, center, normal, tangent, binormal)
  }
  def circle(radius: scala.Float, divisions: scala.Int, centerX: scala.Float, centerY: scala.Float, centerZ: scala.Float, normalX: scala.Float, normalY: scala.Float, normalZ: scala.Float, tangentX: scala.Float, tangentY: scala.Float, tangentZ: scala.Float, binormalX: scala.Float, binormalY: scala.Float, binormalZ: scala.Float): scala.Unit = {
    com.badlogic.gdx.graphics.g3d.utils.shapebuilders.EllipseShapeBuilder.build(this, radius, divisions, centerX, centerY, centerZ, normalX, normalY, normalZ, tangentX, tangentY, tangentZ, binormalX, binormalY, binormalZ)
  }
  def circle(radius: scala.Float, divisions: scala.Int, centerX: scala.Float, centerY: scala.Float, centerZ: scala.Float, normalX: scala.Float, normalY: scala.Float, normalZ: scala.Float, angleFrom: scala.Float, angleTo: scala.Float): scala.Unit = {
    com.badlogic.gdx.graphics.g3d.utils.shapebuilders.EllipseShapeBuilder.build(this, radius, divisions, centerX, centerY, centerZ, normalX, normalY, normalZ, angleFrom, angleTo)
  }
  def circle(radius: scala.Float, divisions: scala.Int, center: com.badlogic.gdx.math.Vector3, normal: com.badlogic.gdx.math.Vector3, angleFrom: scala.Float, angleTo: scala.Float): scala.Unit = {
    com.badlogic.gdx.graphics.g3d.utils.shapebuilders.EllipseShapeBuilder.build(this, radius, divisions, center, normal, angleFrom, angleTo)
  }
  def circle(radius: scala.Float, divisions: scala.Int, center: com.badlogic.gdx.math.Vector3, normal: com.badlogic.gdx.math.Vector3, tangent: com.badlogic.gdx.math.Vector3, binormal: com.badlogic.gdx.math.Vector3, angleFrom: scala.Float, angleTo: scala.Float): scala.Unit = {
    this.circle(radius, divisions, center.x, center.y, center.z, normal.x, normal.y, normal.z, tangent.x, tangent.y, tangent.z, binormal.x, binormal.y, binormal.z, angleFrom, angleTo)
  }
  def circle(radius: scala.Float, divisions: scala.Int, centerX: scala.Float, centerY: scala.Float, centerZ: scala.Float, normalX: scala.Float, normalY: scala.Float, normalZ: scala.Float, tangentX: scala.Float, tangentY: scala.Float, tangentZ: scala.Float, binormalX: scala.Float, binormalY: scala.Float, binormalZ: scala.Float, angleFrom: scala.Float, angleTo: scala.Float): scala.Unit = {
    com.badlogic.gdx.graphics.g3d.utils.shapebuilders.EllipseShapeBuilder.build(this, radius, divisions, centerX, centerY, centerZ, normalX, normalY, normalZ, tangentX, tangentY, tangentZ, binormalX, binormalY, binormalZ, angleFrom, angleTo)
  }
  def ellipse(width: scala.Float, height: scala.Float, divisions: scala.Int, centerX: scala.Float, centerY: scala.Float, centerZ: scala.Float, normalX: scala.Float, normalY: scala.Float, normalZ: scala.Float): scala.Unit = {
    com.badlogic.gdx.graphics.g3d.utils.shapebuilders.EllipseShapeBuilder.build(this, width, height, divisions, centerX, centerY, centerZ, normalX, normalY, normalZ)
  }
  def ellipse(width: scala.Float, height: scala.Float, divisions: scala.Int, center: com.badlogic.gdx.math.Vector3, normal: com.badlogic.gdx.math.Vector3): scala.Unit = {
    com.badlogic.gdx.graphics.g3d.utils.shapebuilders.EllipseShapeBuilder.build(this, width, height, divisions, center, normal)
  }
  def ellipse(width: scala.Float, height: scala.Float, divisions: scala.Int, center: com.badlogic.gdx.math.Vector3, normal: com.badlogic.gdx.math.Vector3, tangent: com.badlogic.gdx.math.Vector3, binormal: com.badlogic.gdx.math.Vector3): scala.Unit = {
    com.badlogic.gdx.graphics.g3d.utils.shapebuilders.EllipseShapeBuilder.build(this, width, height, divisions, center, normal, tangent, binormal)
  }
  def ellipse(width: scala.Float, height: scala.Float, divisions: scala.Int, centerX: scala.Float, centerY: scala.Float, centerZ: scala.Float, normalX: scala.Float, normalY: scala.Float, normalZ: scala.Float, tangentX: scala.Float, tangentY: scala.Float, tangentZ: scala.Float, binormalX: scala.Float, binormalY: scala.Float, binormalZ: scala.Float): scala.Unit = {
    com.badlogic.gdx.graphics.g3d.utils.shapebuilders.EllipseShapeBuilder.build(this, width, height, divisions, centerX, centerY, centerZ, normalX, normalY, normalZ, tangentX, tangentY, tangentZ, binormalX, binormalY, binormalZ)
  }
  def ellipse(width: scala.Float, height: scala.Float, divisions: scala.Int, centerX: scala.Float, centerY: scala.Float, centerZ: scala.Float, normalX: scala.Float, normalY: scala.Float, normalZ: scala.Float, angleFrom: scala.Float, angleTo: scala.Float): scala.Unit = {
    com.badlogic.gdx.graphics.g3d.utils.shapebuilders.EllipseShapeBuilder.build(this, width, height, divisions, centerX, centerY, centerZ, normalX, normalY, normalZ, angleFrom, angleTo)
  }
  def ellipse(width: scala.Float, height: scala.Float, divisions: scala.Int, center: com.badlogic.gdx.math.Vector3, normal: com.badlogic.gdx.math.Vector3, angleFrom: scala.Float, angleTo: scala.Float): scala.Unit = {
    com.badlogic.gdx.graphics.g3d.utils.shapebuilders.EllipseShapeBuilder.build(this, width, height, divisions, center, normal, angleFrom, angleTo)
  }
  def ellipse(width: scala.Float, height: scala.Float, divisions: scala.Int, center: com.badlogic.gdx.math.Vector3, normal: com.badlogic.gdx.math.Vector3, tangent: com.badlogic.gdx.math.Vector3, binormal: com.badlogic.gdx.math.Vector3, angleFrom: scala.Float, angleTo: scala.Float): scala.Unit = {
    com.badlogic.gdx.graphics.g3d.utils.shapebuilders.EllipseShapeBuilder.build(this, width, height, divisions, center, normal, tangent, binormal, angleFrom, angleTo)
  }
  def ellipse(width: scala.Float, height: scala.Float, divisions: scala.Int, centerX: scala.Float, centerY: scala.Float, centerZ: scala.Float, normalX: scala.Float, normalY: scala.Float, normalZ: scala.Float, tangentX: scala.Float, tangentY: scala.Float, tangentZ: scala.Float, binormalX: scala.Float, binormalY: scala.Float, binormalZ: scala.Float, angleFrom: scala.Float, angleTo: scala.Float): scala.Unit = {
    com.badlogic.gdx.graphics.g3d.utils.shapebuilders.EllipseShapeBuilder.build(this, width, height, divisions, centerX, centerY, centerZ, normalX, normalY, normalZ, tangentX, tangentY, tangentZ, binormalX, binormalY, binormalZ, angleFrom, angleTo)
  }
  def ellipse(width: scala.Float, height: scala.Float, innerWidth: scala.Float, innerHeight: scala.Float, divisions: scala.Int, center: com.badlogic.gdx.math.Vector3, normal: com.badlogic.gdx.math.Vector3): scala.Unit = {
    com.badlogic.gdx.graphics.g3d.utils.shapebuilders.EllipseShapeBuilder.build(this, width, height, innerWidth, innerHeight, divisions, center, normal)
  }
  def ellipse(width: scala.Float, height: scala.Float, innerWidth: scala.Float, innerHeight: scala.Float, divisions: scala.Int, centerX: scala.Float, centerY: scala.Float, centerZ: scala.Float, normalX: scala.Float, normalY: scala.Float, normalZ: scala.Float): scala.Unit = {
    com.badlogic.gdx.graphics.g3d.utils.shapebuilders.EllipseShapeBuilder.build(this, width, height, innerWidth, innerHeight, divisions, centerX, centerY, centerZ, normalX, normalY, normalZ)
  }
  def ellipse(width: scala.Float, height: scala.Float, innerWidth: scala.Float, innerHeight: scala.Float, divisions: scala.Int, centerX: scala.Float, centerY: scala.Float, centerZ: scala.Float, normalX: scala.Float, normalY: scala.Float, normalZ: scala.Float, angleFrom: scala.Float, angleTo: scala.Float): scala.Unit = {
    com.badlogic.gdx.graphics.g3d.utils.shapebuilders.EllipseShapeBuilder.build(this, width, height, innerWidth, innerHeight, divisions, centerX, centerY, centerZ, normalX, normalY, normalZ, angleFrom, angleTo)
  }
  def ellipse(width: scala.Float, height: scala.Float, innerWidth: scala.Float, innerHeight: scala.Float, divisions: scala.Int, centerX: scala.Float, centerY: scala.Float, centerZ: scala.Float, normalX: scala.Float, normalY: scala.Float, normalZ: scala.Float, tangentX: scala.Float, tangentY: scala.Float, tangentZ: scala.Float, binormalX: scala.Float, binormalY: scala.Float, binormalZ: scala.Float, angleFrom: scala.Float, angleTo: scala.Float): scala.Unit = {
    com.badlogic.gdx.graphics.g3d.utils.shapebuilders.EllipseShapeBuilder.build(this, width, height, innerWidth, innerHeight, divisions, centerX, centerY, centerZ, normalX, normalY, normalZ, tangentX, tangentY, tangentZ, binormalX, binormalY, binormalZ, angleFrom, angleTo)
  }
  def cylinder(width: scala.Float, height: scala.Float, depth: scala.Float, divisions: scala.Int): scala.Unit = {
    com.badlogic.gdx.graphics.g3d.utils.shapebuilders.CylinderShapeBuilder.build(this, width, height, depth, divisions)
  }
  def cylinder(width: scala.Float, height: scala.Float, depth: scala.Float, divisions: scala.Int, angleFrom: scala.Float, angleTo: scala.Float): scala.Unit = {
    com.badlogic.gdx.graphics.g3d.utils.shapebuilders.CylinderShapeBuilder.build(this, width, height, depth, divisions, angleFrom, angleTo)
  }
  def cylinder(width: scala.Float, height: scala.Float, depth: scala.Float, divisions: scala.Int, angleFrom: scala.Float, angleTo: scala.Float, close: scala.Boolean): scala.Unit = {
    com.badlogic.gdx.graphics.g3d.utils.shapebuilders.CylinderShapeBuilder.build(this, width, height, depth, divisions, angleFrom, angleTo, close)
  }
  def cone(width: scala.Float, height: scala.Float, depth: scala.Float, divisions: scala.Int): scala.Unit = {
    this.cone(width, height, depth, divisions, 0, 360)
  }
  def cone(width: scala.Float, height: scala.Float, depth: scala.Float, divisions: scala.Int, angleFrom: scala.Float, angleTo: scala.Float): scala.Unit = {
    com.badlogic.gdx.graphics.g3d.utils.shapebuilders.ConeShapeBuilder.build(this, width, height, depth, divisions, angleFrom, angleTo)
  }
  def sphere(width: scala.Float, height: scala.Float, depth: scala.Float, divisionsU: scala.Int, divisionsV: scala.Int): scala.Unit = {
    com.badlogic.gdx.graphics.g3d.utils.shapebuilders.SphereShapeBuilder.build(this, width, height, depth, divisionsU, divisionsV)
  }
  def sphere(transform: com.badlogic.gdx.math.Matrix4, width: scala.Float, height: scala.Float, depth: scala.Float, divisionsU: scala.Int, divisionsV: scala.Int): scala.Unit = {
    com.badlogic.gdx.graphics.g3d.utils.shapebuilders.SphereShapeBuilder.build(this, transform, width, height, depth, divisionsU, divisionsV)
  }
  def sphere(width: scala.Float, height: scala.Float, depth: scala.Float, divisionsU: scala.Int, divisionsV: scala.Int, angleUFrom: scala.Float, angleUTo: scala.Float, angleVFrom: scala.Float, angleVTo: scala.Float): scala.Unit = {
    com.badlogic.gdx.graphics.g3d.utils.shapebuilders.SphereShapeBuilder.build(this, width, height, depth, divisionsU, divisionsV, angleUFrom, angleUTo, angleVFrom, angleVTo)
  }
  def sphere(transform: com.badlogic.gdx.math.Matrix4, width: scala.Float, height: scala.Float, depth: scala.Float, divisionsU: scala.Int, divisionsV: scala.Int, angleUFrom: scala.Float, angleUTo: scala.Float, angleVFrom: scala.Float, angleVTo: scala.Float): scala.Unit = {
    com.badlogic.gdx.graphics.g3d.utils.shapebuilders.SphereShapeBuilder.build(this, transform, width, height, depth, divisionsU, divisionsV, angleUFrom, angleUTo, angleVFrom, angleVTo)
  }
  def capsule(radius: scala.Float, height: scala.Float, divisions: scala.Int): scala.Unit = {
    com.badlogic.gdx.graphics.g3d.utils.shapebuilders.CapsuleShapeBuilder.build(this, radius, height, divisions)
  }
  def arrow(x1: scala.Float, y1: scala.Float, z1: scala.Float, x2: scala.Float, y2: scala.Float, z2: scala.Float, capLength: scala.Float, stemThickness: scala.Float, divisions: scala.Int): scala.Unit = {
    com.badlogic.gdx.graphics.g3d.utils.shapebuilders.ArrowShapeBuilder.build(this, x1, y1, z1, x2, y2, z2, capLength, stemThickness, divisions)
  }
}
object MeshBuilder {
  export com.badlogic.gdx.graphics.g3d.utils.MeshPartBuilder.{MAX_VERTICES => _, MAX_INDEX => _, tmpIndices => _, tmpVertices => _, vTmp => _, indicesMap => _, createAttributes => _, transformPosition => _, transformNormal => _, *}
  final val MAX_VERTICES: scala.Int = 1 << 16
  final val MAX_INDEX: scala.Int = MeshBuilder.MAX_VERTICES - 1
  final val tmpIndices: com.badlogic.gdx.utils.ShortArray = new com.badlogic.gdx.utils.ShortArray()
  final val tmpVertices: com.badlogic.gdx.utils.FloatArray = new com.badlogic.gdx.utils.FloatArray()
  private final val vTmp: com.badlogic.gdx.math.Vector3 = new com.badlogic.gdx.math.Vector3()
  private var indicesMap: com.badlogic.gdx.utils.IntIntMap = null
  def createAttributes(usage: scala.Long): com.badlogic.gdx.graphics.VertexAttributes = {
    val attrs: com.badlogic.gdx.utils.Array[com.badlogic.gdx.graphics.VertexAttribute] = new com.badlogic.gdx.utils.Array[com.badlogic.gdx.graphics.VertexAttribute]()
    if ((usage & com.badlogic.gdx.graphics.VertexAttributes.Usage.Position) == com.badlogic.gdx.graphics.VertexAttributes.Usage.Position) {
      attrs.add(new com.badlogic.gdx.graphics.VertexAttribute(com.badlogic.gdx.graphics.VertexAttributes.Usage.Position, 3, com.badlogic.gdx.graphics.glutils.ShaderProgram.POSITION_ATTRIBUTE))
    } else ()
    if ((usage & com.badlogic.gdx.graphics.VertexAttributes.Usage.ColorUnpacked) == com.badlogic.gdx.graphics.VertexAttributes.Usage.ColorUnpacked) {
      attrs.add(new com.badlogic.gdx.graphics.VertexAttribute(com.badlogic.gdx.graphics.VertexAttributes.Usage.ColorUnpacked, 4, com.badlogic.gdx.graphics.glutils.ShaderProgram.COLOR_ATTRIBUTE))
    } else ()
    if ((usage & com.badlogic.gdx.graphics.VertexAttributes.Usage.ColorPacked) == com.badlogic.gdx.graphics.VertexAttributes.Usage.ColorPacked) {
      attrs.add(new com.badlogic.gdx.graphics.VertexAttribute(com.badlogic.gdx.graphics.VertexAttributes.Usage.ColorPacked, 4, com.badlogic.gdx.graphics.glutils.ShaderProgram.COLOR_ATTRIBUTE))
    } else ()
    if ((usage & com.badlogic.gdx.graphics.VertexAttributes.Usage.Normal) == com.badlogic.gdx.graphics.VertexAttributes.Usage.Normal) {
      attrs.add(new com.badlogic.gdx.graphics.VertexAttribute(com.badlogic.gdx.graphics.VertexAttributes.Usage.Normal, 3, com.badlogic.gdx.graphics.glutils.ShaderProgram.NORMAL_ATTRIBUTE))
    } else ()
    if ((usage & com.badlogic.gdx.graphics.VertexAttributes.Usage.TextureCoordinates) == com.badlogic.gdx.graphics.VertexAttributes.Usage.TextureCoordinates) {
      attrs.add(new com.badlogic.gdx.graphics.VertexAttribute(com.badlogic.gdx.graphics.VertexAttributes.Usage.TextureCoordinates, 2, com.badlogic.gdx.graphics.glutils.ShaderProgram.TEXCOORD_ATTRIBUTE + "0"))
    } else ()
    val attributes: scala.Array[com.badlogic.gdx.graphics.VertexAttribute] = new scala.Array[com.badlogic.gdx.graphics.VertexAttribute](attrs.size);
    { var i: scala.Int = 0; while (i < attributes.length) { {
      attributes(i) = attrs.get(i)
    }; i = i + 1 } }
    return new com.badlogic.gdx.graphics.VertexAttributes(attributes)
  }
  private final def transformPosition(values: scala.Array[scala.Float], offset: scala.Int, size: scala.Int, transform: com.badlogic.gdx.math.Matrix4): scala.Unit = {
    if (size > 2) {
      MeshBuilder.vTmp.set(values(offset), values(offset + 1), values(offset + 2)).mul(transform)
      values(offset) = MeshBuilder.vTmp.x
      values(offset + 1) = MeshBuilder.vTmp.y
      values(offset + 2) = MeshBuilder.vTmp.z
    } else {
      if (size > 1) {
        MeshBuilder.vTmp.set(values(offset), values(offset + 1), 0).mul(transform)
        values(offset) = MeshBuilder.vTmp.x
        values(offset + 1) = MeshBuilder.vTmp.y
      } else {
        values(offset) = MeshBuilder.vTmp.set(values(offset), 0, 0).mul(transform).x
      }
    }
  }
  private final def transformNormal(values: scala.Array[scala.Float], offset: scala.Int, size: scala.Int, transform: com.badlogic.gdx.math.Matrix3): scala.Unit = {
    if (size > 2) {
      MeshBuilder.vTmp.set(values(offset), values(offset + 1), values(offset + 2)).mul(transform).nor()
      values(offset) = MeshBuilder.vTmp.x
      values(offset + 1) = MeshBuilder.vTmp.y
      values(offset + 2) = MeshBuilder.vTmp.z
    } else {
      if (size > 1) {
        MeshBuilder.vTmp.set(values(offset), values(offset + 1), 0).mul(transform).nor()
        values(offset) = MeshBuilder.vTmp.x
        values(offset + 1) = MeshBuilder.vTmp.y
      } else {
        values(offset) = MeshBuilder.vTmp.set(values(offset), 0, 0).mul(transform).nor().x
      }
    }
  }
}