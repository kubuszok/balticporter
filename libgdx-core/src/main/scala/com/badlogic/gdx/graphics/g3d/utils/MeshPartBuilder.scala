package com.badlogic.gdx.graphics.g3d.utils

trait MeshPartBuilder {
  def getMeshPart(): com.badlogic.gdx.graphics.g3d.model.MeshPart
  def getPrimitiveType(): scala.Int
  def getAttributes(): com.badlogic.gdx.graphics.VertexAttributes
  def setColor(color: com.badlogic.gdx.graphics.Color): scala.Unit
  def setColor(r: scala.Float, g: scala.Float, b: scala.Float, a: scala.Float): scala.Unit
  def setUVRange(u1: scala.Float, v1: scala.Float, u2: scala.Float, v2: scala.Float): scala.Unit
  def setUVRange(r: com.badlogic.gdx.graphics.g2d.TextureRegion): scala.Unit
  def getVertexTransform(out: com.badlogic.gdx.math.Matrix4): com.badlogic.gdx.math.Matrix4
  def setVertexTransform(transform: com.badlogic.gdx.math.Matrix4): scala.Unit
  def isVertexTransformationEnabled(): scala.Boolean
  def setVertexTransformationEnabled(enabled: scala.Boolean): scala.Unit
  def ensureVertices(numVertices: scala.Int): scala.Unit
  def ensureIndices(numIndices: scala.Int): scala.Unit
  def ensureCapacity(numVertices: scala.Int, numIndices: scala.Int): scala.Unit
  def ensureTriangleIndices(numTriangles: scala.Int): scala.Unit
  def ensureRectangleIndices(numRectangles: scala.Int): scala.Unit
  def vertex(values: scala.Array[scala.Float]): scala.Short
  def vertex(pos: com.badlogic.gdx.math.Vector3, nor: com.badlogic.gdx.math.Vector3, col: com.badlogic.gdx.graphics.Color, uv: com.badlogic.gdx.math.Vector2): scala.Short
  def vertex(info: com.badlogic.gdx.graphics.g3d.utils.MeshPartBuilder.VertexInfo): scala.Short
  def lastIndex(): scala.Int
  def index(value: scala.Short): scala.Unit
  def index(value1: scala.Short, value2: scala.Short): scala.Unit
  def index(value1: scala.Short, value2: scala.Short, value3: scala.Short): scala.Unit
  def index(value1: scala.Short, value2: scala.Short, value3: scala.Short, value4: scala.Short): scala.Unit
  def index(value1: scala.Short, value2: scala.Short, value3: scala.Short, value4: scala.Short, value5: scala.Short, value6: scala.Short): scala.Unit
  def index(value1: scala.Short, value2: scala.Short, value3: scala.Short, value4: scala.Short, value5: scala.Short, value6: scala.Short, value7: scala.Short, value8: scala.Short): scala.Unit
  def line(index1: scala.Short, index2: scala.Short): scala.Unit
  def line(p1: com.badlogic.gdx.graphics.g3d.utils.MeshPartBuilder.VertexInfo, p2: com.badlogic.gdx.graphics.g3d.utils.MeshPartBuilder.VertexInfo): scala.Unit
  def line(p1: com.badlogic.gdx.math.Vector3, p2: com.badlogic.gdx.math.Vector3): scala.Unit
  def line(x1: scala.Float, y1: scala.Float, z1: scala.Float, x2: scala.Float, y2: scala.Float, z2: scala.Float): scala.Unit
  def line(p1: com.badlogic.gdx.math.Vector3, c1: com.badlogic.gdx.graphics.Color, p2: com.badlogic.gdx.math.Vector3, c2: com.badlogic.gdx.graphics.Color): scala.Unit
  def triangle(index1: scala.Short, index2: scala.Short, index3: scala.Short): scala.Unit
  def triangle(p1: com.badlogic.gdx.graphics.g3d.utils.MeshPartBuilder.VertexInfo, p2: com.badlogic.gdx.graphics.g3d.utils.MeshPartBuilder.VertexInfo, p3: com.badlogic.gdx.graphics.g3d.utils.MeshPartBuilder.VertexInfo): scala.Unit
  def triangle(p1: com.badlogic.gdx.math.Vector3, p2: com.badlogic.gdx.math.Vector3, p3: com.badlogic.gdx.math.Vector3): scala.Unit
  def triangle(p1: com.badlogic.gdx.math.Vector3, c1: com.badlogic.gdx.graphics.Color, p2: com.badlogic.gdx.math.Vector3, c2: com.badlogic.gdx.graphics.Color, p3: com.badlogic.gdx.math.Vector3, c3: com.badlogic.gdx.graphics.Color): scala.Unit
  def rect(corner00: scala.Short, corner10: scala.Short, corner11: scala.Short, corner01: scala.Short): scala.Unit
  def rect(corner00: com.badlogic.gdx.graphics.g3d.utils.MeshPartBuilder.VertexInfo, corner10: com.badlogic.gdx.graphics.g3d.utils.MeshPartBuilder.VertexInfo, corner11: com.badlogic.gdx.graphics.g3d.utils.MeshPartBuilder.VertexInfo, corner01: com.badlogic.gdx.graphics.g3d.utils.MeshPartBuilder.VertexInfo): scala.Unit
  def rect(corner00: com.badlogic.gdx.math.Vector3, corner10: com.badlogic.gdx.math.Vector3, corner11: com.badlogic.gdx.math.Vector3, corner01: com.badlogic.gdx.math.Vector3, normal: com.badlogic.gdx.math.Vector3): scala.Unit
  def rect(x00: scala.Float, y00: scala.Float, z00: scala.Float, x10: scala.Float, y10: scala.Float, z10: scala.Float, x11: scala.Float, y11: scala.Float, z11: scala.Float, x01: scala.Float, y01: scala.Float, z01: scala.Float, normalX: scala.Float, normalY: scala.Float, normalZ: scala.Float): scala.Unit
  def addMesh(mesh: com.badlogic.gdx.graphics.Mesh): scala.Unit
  def addMesh(meshpart: com.badlogic.gdx.graphics.g3d.model.MeshPart): scala.Unit
  def addMesh(mesh: com.badlogic.gdx.graphics.Mesh, indexOffset: scala.Int, numIndices: scala.Int): scala.Unit
  def addMesh(vertices: scala.Array[scala.Float], indices: scala.Array[scala.Short]): scala.Unit
  def addMesh(vertices: scala.Array[scala.Float], indices: scala.Array[scala.Short], indexOffset: scala.Int, numIndices: scala.Int): scala.Unit
  @java.lang.Deprecated
  def patch(corner00: com.badlogic.gdx.graphics.g3d.utils.MeshPartBuilder.VertexInfo, corner10: com.badlogic.gdx.graphics.g3d.utils.MeshPartBuilder.VertexInfo, corner11: com.badlogic.gdx.graphics.g3d.utils.MeshPartBuilder.VertexInfo, corner01: com.badlogic.gdx.graphics.g3d.utils.MeshPartBuilder.VertexInfo, divisionsU: scala.Int, divisionsV: scala.Int): scala.Unit
  @java.lang.Deprecated
  def patch(corner00: com.badlogic.gdx.math.Vector3, corner10: com.badlogic.gdx.math.Vector3, corner11: com.badlogic.gdx.math.Vector3, corner01: com.badlogic.gdx.math.Vector3, normal: com.badlogic.gdx.math.Vector3, divisionsU: scala.Int, divisionsV: scala.Int): scala.Unit
  @java.lang.Deprecated
  def patch(x00: scala.Float, y00: scala.Float, z00: scala.Float, x10: scala.Float, y10: scala.Float, z10: scala.Float, x11: scala.Float, y11: scala.Float, z11: scala.Float, x01: scala.Float, y01: scala.Float, z01: scala.Float, normalX: scala.Float, normalY: scala.Float, normalZ: scala.Float, divisionsU: scala.Int, divisionsV: scala.Int): scala.Unit
  @java.lang.Deprecated
  def box(corner000: com.badlogic.gdx.graphics.g3d.utils.MeshPartBuilder.VertexInfo, corner010: com.badlogic.gdx.graphics.g3d.utils.MeshPartBuilder.VertexInfo, corner100: com.badlogic.gdx.graphics.g3d.utils.MeshPartBuilder.VertexInfo, corner110: com.badlogic.gdx.graphics.g3d.utils.MeshPartBuilder.VertexInfo, corner001: com.badlogic.gdx.graphics.g3d.utils.MeshPartBuilder.VertexInfo, corner011: com.badlogic.gdx.graphics.g3d.utils.MeshPartBuilder.VertexInfo, corner101: com.badlogic.gdx.graphics.g3d.utils.MeshPartBuilder.VertexInfo, corner111: com.badlogic.gdx.graphics.g3d.utils.MeshPartBuilder.VertexInfo): scala.Unit
  @java.lang.Deprecated
  def box(corner000: com.badlogic.gdx.math.Vector3, corner010: com.badlogic.gdx.math.Vector3, corner100: com.badlogic.gdx.math.Vector3, corner110: com.badlogic.gdx.math.Vector3, corner001: com.badlogic.gdx.math.Vector3, corner011: com.badlogic.gdx.math.Vector3, corner101: com.badlogic.gdx.math.Vector3, corner111: com.badlogic.gdx.math.Vector3): scala.Unit
  @java.lang.Deprecated
  def box(transform: com.badlogic.gdx.math.Matrix4): scala.Unit
  @java.lang.Deprecated
  def box(width: scala.Float, height: scala.Float, depth: scala.Float): scala.Unit
  @java.lang.Deprecated
  def box(x: scala.Float, y: scala.Float, z: scala.Float, width: scala.Float, height: scala.Float, depth: scala.Float): scala.Unit
  @java.lang.Deprecated
  def circle(radius: scala.Float, divisions: scala.Int, centerX: scala.Float, centerY: scala.Float, centerZ: scala.Float, normalX: scala.Float, normalY: scala.Float, normalZ: scala.Float): scala.Unit
  @java.lang.Deprecated
  def circle(radius: scala.Float, divisions: scala.Int, center: com.badlogic.gdx.math.Vector3, normal: com.badlogic.gdx.math.Vector3): scala.Unit
  @java.lang.Deprecated
  def circle(radius: scala.Float, divisions: scala.Int, center: com.badlogic.gdx.math.Vector3, normal: com.badlogic.gdx.math.Vector3, tangent: com.badlogic.gdx.math.Vector3, binormal: com.badlogic.gdx.math.Vector3): scala.Unit
  @java.lang.Deprecated
  def circle(radius: scala.Float, divisions: scala.Int, centerX: scala.Float, centerY: scala.Float, centerZ: scala.Float, normalX: scala.Float, normalY: scala.Float, normalZ: scala.Float, tangentX: scala.Float, tangentY: scala.Float, tangentZ: scala.Float, binormalX: scala.Float, binormalY: scala.Float, binormalZ: scala.Float): scala.Unit
  @java.lang.Deprecated
  def circle(radius: scala.Float, divisions: scala.Int, centerX: scala.Float, centerY: scala.Float, centerZ: scala.Float, normalX: scala.Float, normalY: scala.Float, normalZ: scala.Float, angleFrom: scala.Float, angleTo: scala.Float): scala.Unit
  @java.lang.Deprecated
  def circle(radius: scala.Float, divisions: scala.Int, center: com.badlogic.gdx.math.Vector3, normal: com.badlogic.gdx.math.Vector3, angleFrom: scala.Float, angleTo: scala.Float): scala.Unit
  @java.lang.Deprecated
  def circle(radius: scala.Float, divisions: scala.Int, center: com.badlogic.gdx.math.Vector3, normal: com.badlogic.gdx.math.Vector3, tangent: com.badlogic.gdx.math.Vector3, binormal: com.badlogic.gdx.math.Vector3, angleFrom: scala.Float, angleTo: scala.Float): scala.Unit
  @java.lang.Deprecated
  def circle(radius: scala.Float, divisions: scala.Int, centerX: scala.Float, centerY: scala.Float, centerZ: scala.Float, normalX: scala.Float, normalY: scala.Float, normalZ: scala.Float, tangentX: scala.Float, tangentY: scala.Float, tangentZ: scala.Float, binormalX: scala.Float, binormalY: scala.Float, binormalZ: scala.Float, angleFrom: scala.Float, angleTo: scala.Float): scala.Unit
  @java.lang.Deprecated
  def ellipse(width: scala.Float, height: scala.Float, divisions: scala.Int, centerX: scala.Float, centerY: scala.Float, centerZ: scala.Float, normalX: scala.Float, normalY: scala.Float, normalZ: scala.Float): scala.Unit
  @java.lang.Deprecated
  def ellipse(width: scala.Float, height: scala.Float, divisions: scala.Int, center: com.badlogic.gdx.math.Vector3, normal: com.badlogic.gdx.math.Vector3): scala.Unit
  @java.lang.Deprecated
  def ellipse(width: scala.Float, height: scala.Float, divisions: scala.Int, center: com.badlogic.gdx.math.Vector3, normal: com.badlogic.gdx.math.Vector3, tangent: com.badlogic.gdx.math.Vector3, binormal: com.badlogic.gdx.math.Vector3): scala.Unit
  @java.lang.Deprecated
  def ellipse(width: scala.Float, height: scala.Float, divisions: scala.Int, centerX: scala.Float, centerY: scala.Float, centerZ: scala.Float, normalX: scala.Float, normalY: scala.Float, normalZ: scala.Float, tangentX: scala.Float, tangentY: scala.Float, tangentZ: scala.Float, binormalX: scala.Float, binormalY: scala.Float, binormalZ: scala.Float): scala.Unit
  @java.lang.Deprecated
  def ellipse(width: scala.Float, height: scala.Float, divisions: scala.Int, centerX: scala.Float, centerY: scala.Float, centerZ: scala.Float, normalX: scala.Float, normalY: scala.Float, normalZ: scala.Float, angleFrom: scala.Float, angleTo: scala.Float): scala.Unit
  @java.lang.Deprecated
  def ellipse(width: scala.Float, height: scala.Float, divisions: scala.Int, center: com.badlogic.gdx.math.Vector3, normal: com.badlogic.gdx.math.Vector3, angleFrom: scala.Float, angleTo: scala.Float): scala.Unit
  @java.lang.Deprecated
  def ellipse(width: scala.Float, height: scala.Float, divisions: scala.Int, center: com.badlogic.gdx.math.Vector3, normal: com.badlogic.gdx.math.Vector3, tangent: com.badlogic.gdx.math.Vector3, binormal: com.badlogic.gdx.math.Vector3, angleFrom: scala.Float, angleTo: scala.Float): scala.Unit
  @java.lang.Deprecated
  def ellipse(width: scala.Float, height: scala.Float, divisions: scala.Int, centerX: scala.Float, centerY: scala.Float, centerZ: scala.Float, normalX: scala.Float, normalY: scala.Float, normalZ: scala.Float, tangentX: scala.Float, tangentY: scala.Float, tangentZ: scala.Float, binormalX: scala.Float, binormalY: scala.Float, binormalZ: scala.Float, angleFrom: scala.Float, angleTo: scala.Float): scala.Unit
  @java.lang.Deprecated
  def ellipse(width: scala.Float, height: scala.Float, innerWidth: scala.Float, innerHeight: scala.Float, divisions: scala.Int, centerX: scala.Float, centerY: scala.Float, centerZ: scala.Float, normalX: scala.Float, normalY: scala.Float, normalZ: scala.Float, tangentX: scala.Float, tangentY: scala.Float, tangentZ: scala.Float, binormalX: scala.Float, binormalY: scala.Float, binormalZ: scala.Float, angleFrom: scala.Float, angleTo: scala.Float): scala.Unit
  @java.lang.Deprecated
  def ellipse(width: scala.Float, height: scala.Float, innerWidth: scala.Float, innerHeight: scala.Float, divisions: scala.Int, centerX: scala.Float, centerY: scala.Float, centerZ: scala.Float, normalX: scala.Float, normalY: scala.Float, normalZ: scala.Float, angleFrom: scala.Float, angleTo: scala.Float): scala.Unit
  @java.lang.Deprecated
  def ellipse(width: scala.Float, height: scala.Float, innerWidth: scala.Float, innerHeight: scala.Float, divisions: scala.Int, centerX: scala.Float, centerY: scala.Float, centerZ: scala.Float, normalX: scala.Float, normalY: scala.Float, normalZ: scala.Float): scala.Unit
  @java.lang.Deprecated
  def ellipse(width: scala.Float, height: scala.Float, innerWidth: scala.Float, innerHeight: scala.Float, divisions: scala.Int, center: com.badlogic.gdx.math.Vector3, normal: com.badlogic.gdx.math.Vector3): scala.Unit
  @java.lang.Deprecated
  def cylinder(width: scala.Float, height: scala.Float, depth: scala.Float, divisions: scala.Int): scala.Unit
  @java.lang.Deprecated
  def cylinder(width: scala.Float, height: scala.Float, depth: scala.Float, divisions: scala.Int, angleFrom: scala.Float, angleTo: scala.Float): scala.Unit
  @java.lang.Deprecated
  def cylinder(width: scala.Float, height: scala.Float, depth: scala.Float, divisions: scala.Int, angleFrom: scala.Float, angleTo: scala.Float, close: scala.Boolean): scala.Unit
  @java.lang.Deprecated
  def cone(width: scala.Float, height: scala.Float, depth: scala.Float, divisions: scala.Int): scala.Unit
  @java.lang.Deprecated
  def cone(width: scala.Float, height: scala.Float, depth: scala.Float, divisions: scala.Int, angleFrom: scala.Float, angleTo: scala.Float): scala.Unit
  @java.lang.Deprecated
  def sphere(width: scala.Float, height: scala.Float, depth: scala.Float, divisionsU: scala.Int, divisionsV: scala.Int): scala.Unit
  @java.lang.Deprecated
  def sphere(transform: com.badlogic.gdx.math.Matrix4, width: scala.Float, height: scala.Float, depth: scala.Float, divisionsU: scala.Int, divisionsV: scala.Int): scala.Unit
  @java.lang.Deprecated
  def sphere(width: scala.Float, height: scala.Float, depth: scala.Float, divisionsU: scala.Int, divisionsV: scala.Int, angleUFrom: scala.Float, angleUTo: scala.Float, angleVFrom: scala.Float, angleVTo: scala.Float): scala.Unit
  @java.lang.Deprecated
  def sphere(transform: com.badlogic.gdx.math.Matrix4, width: scala.Float, height: scala.Float, depth: scala.Float, divisionsU: scala.Int, divisionsV: scala.Int, angleUFrom: scala.Float, angleUTo: scala.Float, angleVFrom: scala.Float, angleVTo: scala.Float): scala.Unit
  @java.lang.Deprecated
  def capsule(radius: scala.Float, height: scala.Float, divisions: scala.Int): scala.Unit
  @java.lang.Deprecated
  def arrow(x1: scala.Float, y1: scala.Float, z1: scala.Float, x2: scala.Float, y2: scala.Float, z2: scala.Float, capLength: scala.Float, stemThickness: scala.Float, divisions: scala.Int): scala.Unit
}
object MeshPartBuilder {
  class VertexInfo extends com.badlogic.gdx.utils.Pool.Poolable {
    final val position: com.badlogic.gdx.math.Vector3 = new com.badlogic.gdx.math.Vector3()
    var hasPosition: scala.Boolean = false
    final val normal: com.badlogic.gdx.math.Vector3 = new com.badlogic.gdx.math.Vector3(0, 1, 0)
    var hasNormal: scala.Boolean = false
    final val color: com.badlogic.gdx.graphics.Color = new com.badlogic.gdx.graphics.Color(1, 1, 1, 1)
    var hasColor: scala.Boolean = false
    final val uv: com.badlogic.gdx.math.Vector2 = new com.badlogic.gdx.math.Vector2()
    var hasUV: scala.Boolean = false
    @java.lang.Override
    override def reset(): scala.Unit = {
      this.position.set(0, 0, 0)
      this.normal.set(0, 1, 0)
      this.color.set(1, 1, 1, 1)
      this.uv.set(0, 0)
    }
    def set(pos: com.badlogic.gdx.math.Vector3, nor: com.badlogic.gdx.math.Vector3, col: com.badlogic.gdx.graphics.Color, uv: com.badlogic.gdx.math.Vector2): com.badlogic.gdx.graphics.g3d.utils.MeshPartBuilder.VertexInfo = {
      this.reset()
      this.hasPosition = pos != null
      if (this.hasPosition) {
        this.position.set(pos)
      } else ()
      this.hasNormal = nor != null
      if (this.hasNormal) {
        this.normal.set(nor)
      } else ()
      this.hasColor = col != null
      if (this.hasColor) {
        this.color.set(col)
      } else ()
      this.hasUV = uv != null
      if (this.hasUV) {
        this.uv.set(uv)
      } else ()
      return this
    }
    def set(other: com.badlogic.gdx.graphics.g3d.utils.MeshPartBuilder.VertexInfo): com.badlogic.gdx.graphics.g3d.utils.MeshPartBuilder.VertexInfo = {
      if (other == null) {
        return this.set(null, null, null, null)
      } else ()
      this.hasPosition = other.hasPosition
      this.position.set(other.position)
      this.hasNormal = other.hasNormal
      this.normal.set(other.normal)
      this.hasColor = other.hasColor
      this.color.set(other.color)
      this.hasUV = other.hasUV
      this.uv.set(other.uv)
      return this
    }
    def setPos(x: scala.Float, y: scala.Float, z: scala.Float): com.badlogic.gdx.graphics.g3d.utils.MeshPartBuilder.VertexInfo = {
      this.position.set(x, y, z)
      this.hasPosition = true
      return this
    }
    def setPos(pos: com.badlogic.gdx.math.Vector3): com.badlogic.gdx.graphics.g3d.utils.MeshPartBuilder.VertexInfo = {
      this.hasPosition = pos != null
      if (this.hasPosition) {
        this.position.set(pos)
      } else ()
      return this
    }
    def setNor(x: scala.Float, y: scala.Float, z: scala.Float): com.badlogic.gdx.graphics.g3d.utils.MeshPartBuilder.VertexInfo = {
      this.normal.set(x, y, z)
      this.hasNormal = true
      return this
    }
    def setNor(nor: com.badlogic.gdx.math.Vector3): com.badlogic.gdx.graphics.g3d.utils.MeshPartBuilder.VertexInfo = {
      this.hasNormal = nor != null
      if (this.hasNormal) {
        this.normal.set(nor)
      } else ()
      return this
    }
    def setCol(r: scala.Float, g: scala.Float, b: scala.Float, a: scala.Float): com.badlogic.gdx.graphics.g3d.utils.MeshPartBuilder.VertexInfo = {
      this.color.set(r, g, b, a)
      this.hasColor = true
      return this
    }
    def setCol(col: com.badlogic.gdx.graphics.Color): com.badlogic.gdx.graphics.g3d.utils.MeshPartBuilder.VertexInfo = {
      this.hasColor = col != null
      if (this.hasColor) {
        this.color.set(col)
      } else ()
      return this
    }
    def setUV(u: scala.Float, v: scala.Float): com.badlogic.gdx.graphics.g3d.utils.MeshPartBuilder.VertexInfo = {
      this.uv.set(u, v)
      this.hasUV = true
      return this
    }
    def setUV(uv: com.badlogic.gdx.math.Vector2): com.badlogic.gdx.graphics.g3d.utils.MeshPartBuilder.VertexInfo = {
      this.hasUV = uv != null
      if (this.hasUV) {
        this.uv.set(uv)
      } else ()
      return this
    }
    def lerp(target: com.badlogic.gdx.graphics.g3d.utils.MeshPartBuilder.VertexInfo, alpha: scala.Float): com.badlogic.gdx.graphics.g3d.utils.MeshPartBuilder.VertexInfo = {
      if (this.hasPosition && target.hasPosition) {
        this.position.lerp(target.position, alpha)
      } else ()
      if (this.hasNormal && target.hasNormal) {
        this.normal.lerp(target.normal, alpha)
      } else ()
      if (this.hasColor && target.hasColor) {
        this.color.lerp(target.color, alpha)
      } else ()
      if (this.hasUV && target.hasUV) {
        this.uv.lerp(target.uv, alpha)
      } else ()
      return this
    }
  }
}