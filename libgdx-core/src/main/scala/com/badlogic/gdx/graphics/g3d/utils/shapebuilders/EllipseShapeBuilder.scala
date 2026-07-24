package com.badlogic.gdx.graphics.g3d.utils.shapebuilders

class EllipseShapeBuilder extends com.badlogic.gdx.graphics.g3d.utils.shapebuilders.BaseShapeBuilder
object EllipseShapeBuilder {
  export com.badlogic.gdx.graphics.g3d.utils.shapebuilders.BaseShapeBuilder.*
  def build(builder: com.badlogic.gdx.graphics.g3d.utils.MeshPartBuilder, radius: scala.Float, divisions: scala.Int, centerX: scala.Float, centerY: scala.Float, centerZ: scala.Float, normalX: scala.Float, normalY: scala.Float, normalZ: scala.Float): scala.Unit = {
    EllipseShapeBuilder.build(builder, radius, divisions, centerX, centerY, centerZ, normalX, normalY, normalZ, 0.0f, 360.0f)
  }
  def build(builder: com.badlogic.gdx.graphics.g3d.utils.MeshPartBuilder, radius: scala.Float, divisions: scala.Int, center: com.badlogic.gdx.math.Vector3, normal: com.badlogic.gdx.math.Vector3): scala.Unit = {
    EllipseShapeBuilder.build(builder, radius, divisions, center.x, center.y, center.z, normal.x, normal.y, normal.z)
  }
  def build(builder: com.badlogic.gdx.graphics.g3d.utils.MeshPartBuilder, radius: scala.Float, divisions: scala.Int, center: com.badlogic.gdx.math.Vector3, normal: com.badlogic.gdx.math.Vector3, tangent: com.badlogic.gdx.math.Vector3, binormal: com.badlogic.gdx.math.Vector3): scala.Unit = {
    EllipseShapeBuilder.build(builder, radius, divisions, center.x, center.y, center.z, normal.x, normal.y, normal.z, tangent.x, tangent.y, tangent.z, binormal.x, binormal.y, binormal.z)
  }
  def build(builder: com.badlogic.gdx.graphics.g3d.utils.MeshPartBuilder, radius: scala.Float, divisions: scala.Int, centerX: scala.Float, centerY: scala.Float, centerZ: scala.Float, normalX: scala.Float, normalY: scala.Float, normalZ: scala.Float, tangentX: scala.Float, tangentY: scala.Float, tangentZ: scala.Float, binormalX: scala.Float, binormalY: scala.Float, binormalZ: scala.Float): scala.Unit = {
    EllipseShapeBuilder.build(builder, radius, divisions, centerX, centerY, centerZ, normalX, normalY, normalZ, tangentX, tangentY, tangentZ, binormalX, binormalY, binormalZ, 0.0f, 360.0f)
  }
  def build(builder: com.badlogic.gdx.graphics.g3d.utils.MeshPartBuilder, radius: scala.Float, divisions: scala.Int, centerX: scala.Float, centerY: scala.Float, centerZ: scala.Float, normalX: scala.Float, normalY: scala.Float, normalZ: scala.Float, angleFrom: scala.Float, angleTo: scala.Float): scala.Unit = {
    EllipseShapeBuilder.build(builder, radius * 2.0f, radius * 2.0f, divisions, centerX, centerY, centerZ, normalX, normalY, normalZ, angleFrom, angleTo)
  }
  def build(builder: com.badlogic.gdx.graphics.g3d.utils.MeshPartBuilder, radius: scala.Float, divisions: scala.Int, center: com.badlogic.gdx.math.Vector3, normal: com.badlogic.gdx.math.Vector3, angleFrom: scala.Float, angleTo: scala.Float): scala.Unit = {
    EllipseShapeBuilder.build(builder, radius, divisions, center.x, center.y, center.z, normal.x, normal.y, normal.z, angleFrom, angleTo)
  }
  def build(builder: com.badlogic.gdx.graphics.g3d.utils.MeshPartBuilder, radius: scala.Float, divisions: scala.Int, center: com.badlogic.gdx.math.Vector3, normal: com.badlogic.gdx.math.Vector3, tangent: com.badlogic.gdx.math.Vector3, binormal: com.badlogic.gdx.math.Vector3, angleFrom: scala.Float, angleTo: scala.Float): scala.Unit = {
    EllipseShapeBuilder.build(builder, radius, divisions, center.x, center.y, center.z, normal.x, normal.y, normal.z, tangent.x, tangent.y, tangent.z, binormal.x, binormal.y, binormal.z, angleFrom, angleTo)
  }
  def build(builder: com.badlogic.gdx.graphics.g3d.utils.MeshPartBuilder, radius: scala.Float, divisions: scala.Int, centerX: scala.Float, centerY: scala.Float, centerZ: scala.Float, normalX: scala.Float, normalY: scala.Float, normalZ: scala.Float, tangentX: scala.Float, tangentY: scala.Float, tangentZ: scala.Float, binormalX: scala.Float, binormalY: scala.Float, binormalZ: scala.Float, angleFrom: scala.Float, angleTo: scala.Float): scala.Unit = {
    EllipseShapeBuilder.build(builder, radius * 2, radius * 2, 0, 0, divisions, centerX, centerY, centerZ, normalX, normalY, normalZ, tangentX, tangentY, tangentZ, binormalX, binormalY, binormalZ, angleFrom, angleTo)
  }
  def build(builder: com.badlogic.gdx.graphics.g3d.utils.MeshPartBuilder, width: scala.Float, height: scala.Float, divisions: scala.Int, centerX: scala.Float, centerY: scala.Float, centerZ: scala.Float, normalX: scala.Float, normalY: scala.Float, normalZ: scala.Float): scala.Unit = {
    EllipseShapeBuilder.build(builder, width, height, divisions, centerX, centerY, centerZ, normalX, normalY, normalZ, 0.0f, 360.0f)
  }
  def build(builder: com.badlogic.gdx.graphics.g3d.utils.MeshPartBuilder, width: scala.Float, height: scala.Float, divisions: scala.Int, center: com.badlogic.gdx.math.Vector3, normal: com.badlogic.gdx.math.Vector3): scala.Unit = {
    EllipseShapeBuilder.build(builder, width, height, divisions, center.x, center.y, center.z, normal.x, normal.y, normal.z)
  }
  def build(builder: com.badlogic.gdx.graphics.g3d.utils.MeshPartBuilder, width: scala.Float, height: scala.Float, divisions: scala.Int, center: com.badlogic.gdx.math.Vector3, normal: com.badlogic.gdx.math.Vector3, tangent: com.badlogic.gdx.math.Vector3, binormal: com.badlogic.gdx.math.Vector3): scala.Unit = {
    EllipseShapeBuilder.build(builder, width, height, divisions, center.x, center.y, center.z, normal.x, normal.y, normal.z, tangent.x, tangent.y, tangent.z, binormal.x, binormal.y, binormal.z)
  }
  def build(builder: com.badlogic.gdx.graphics.g3d.utils.MeshPartBuilder, width: scala.Float, height: scala.Float, divisions: scala.Int, centerX: scala.Float, centerY: scala.Float, centerZ: scala.Float, normalX: scala.Float, normalY: scala.Float, normalZ: scala.Float, tangentX: scala.Float, tangentY: scala.Float, tangentZ: scala.Float, binormalX: scala.Float, binormalY: scala.Float, binormalZ: scala.Float): scala.Unit = {
    EllipseShapeBuilder.build(builder, width, height, divisions, centerX, centerY, centerZ, normalX, normalY, normalZ, tangentX, tangentY, tangentZ, binormalX, binormalY, binormalZ, 0.0f, 360.0f)
  }
  def build(builder: com.badlogic.gdx.graphics.g3d.utils.MeshPartBuilder, width: scala.Float, height: scala.Float, divisions: scala.Int, centerX: scala.Float, centerY: scala.Float, centerZ: scala.Float, normalX: scala.Float, normalY: scala.Float, normalZ: scala.Float, angleFrom: scala.Float, angleTo: scala.Float): scala.Unit = {
    EllipseShapeBuilder.build(builder, width, height, 0.0f, 0.0f, divisions, centerX, centerY, centerZ, normalX, normalY, normalZ, angleFrom, angleTo)
  }
  def build(builder: com.badlogic.gdx.graphics.g3d.utils.MeshPartBuilder, width: scala.Float, height: scala.Float, divisions: scala.Int, center: com.badlogic.gdx.math.Vector3, normal: com.badlogic.gdx.math.Vector3, angleFrom: scala.Float, angleTo: scala.Float): scala.Unit = {
    EllipseShapeBuilder.build(builder, width, height, 0.0f, 0.0f, divisions, center.x, center.y, center.z, normal.x, normal.y, normal.z, angleFrom, angleTo)
  }
  def build(builder: com.badlogic.gdx.graphics.g3d.utils.MeshPartBuilder, width: scala.Float, height: scala.Float, divisions: scala.Int, center: com.badlogic.gdx.math.Vector3, normal: com.badlogic.gdx.math.Vector3, tangent: com.badlogic.gdx.math.Vector3, binormal: com.badlogic.gdx.math.Vector3, angleFrom: scala.Float, angleTo: scala.Float): scala.Unit = {
    EllipseShapeBuilder.build(builder, width, height, 0.0f, 0.0f, divisions, center.x, center.y, center.z, normal.x, normal.y, normal.z, tangent.x, tangent.y, tangent.z, binormal.x, binormal.y, binormal.z, angleFrom, angleTo)
  }
  def build(builder: com.badlogic.gdx.graphics.g3d.utils.MeshPartBuilder, width: scala.Float, height: scala.Float, divisions: scala.Int, centerX: scala.Float, centerY: scala.Float, centerZ: scala.Float, normalX: scala.Float, normalY: scala.Float, normalZ: scala.Float, tangentX: scala.Float, tangentY: scala.Float, tangentZ: scala.Float, binormalX: scala.Float, binormalY: scala.Float, binormalZ: scala.Float, angleFrom: scala.Float, angleTo: scala.Float): scala.Unit = {
    EllipseShapeBuilder.build(builder, width, height, 0.0f, 0.0f, divisions, centerX, centerY, centerZ, normalX, normalY, normalZ, tangentX, tangentY, tangentZ, binormalX, binormalY, binormalZ, angleFrom, angleTo)
  }
  def build(builder: com.badlogic.gdx.graphics.g3d.utils.MeshPartBuilder, width: scala.Float, height: scala.Float, innerWidth: scala.Float, innerHeight: scala.Float, divisions: scala.Int, centerX: scala.Float, centerY: scala.Float, centerZ: scala.Float, normalX: scala.Float, normalY: scala.Float, normalZ: scala.Float, angleFrom: scala.Float, angleTo: scala.Float): scala.Unit = {
    com.badlogic.gdx.graphics.g3d.utils.shapebuilders.BaseShapeBuilder.tmpV1.set(normalX, normalY, normalZ).crs(0, 0, 1)
    com.badlogic.gdx.graphics.g3d.utils.shapebuilders.BaseShapeBuilder.tmpV2.set(normalX, normalY, normalZ).crs(0, 1, 0)
    if (com.badlogic.gdx.graphics.g3d.utils.shapebuilders.BaseShapeBuilder.tmpV2.len2() > com.badlogic.gdx.graphics.g3d.utils.shapebuilders.BaseShapeBuilder.tmpV1.len2()) {
      com.badlogic.gdx.graphics.g3d.utils.shapebuilders.BaseShapeBuilder.tmpV1.set(com.badlogic.gdx.graphics.g3d.utils.shapebuilders.BaseShapeBuilder.tmpV2)
    } else ()
    com.badlogic.gdx.graphics.g3d.utils.shapebuilders.BaseShapeBuilder.tmpV2.set(com.badlogic.gdx.graphics.g3d.utils.shapebuilders.BaseShapeBuilder.tmpV1.nor()).crs(normalX, normalY, normalZ).nor()
    EllipseShapeBuilder.build(builder, width, height, innerWidth, innerHeight, divisions, centerX, centerY, centerZ, normalX, normalY, normalZ, EllipseShapeBuilder.tmpV1.x, EllipseShapeBuilder.tmpV1.y, EllipseShapeBuilder.tmpV1.z, EllipseShapeBuilder.tmpV2.x, EllipseShapeBuilder.tmpV2.y, EllipseShapeBuilder.tmpV2.z, angleFrom, angleTo)
  }
  def build(builder: com.badlogic.gdx.graphics.g3d.utils.MeshPartBuilder, width: scala.Float, height: scala.Float, innerWidth: scala.Float, innerHeight: scala.Float, divisions: scala.Int, centerX: scala.Float, centerY: scala.Float, centerZ: scala.Float, normalX: scala.Float, normalY: scala.Float, normalZ: scala.Float): scala.Unit = {
    EllipseShapeBuilder.build(builder, width, height, innerWidth, innerHeight, divisions, centerX, centerY, centerZ, normalX, normalY, normalZ, 0.0f, 360.0f)
  }
  def build(builder: com.badlogic.gdx.graphics.g3d.utils.MeshPartBuilder, width: scala.Float, height: scala.Float, innerWidth: scala.Float, innerHeight: scala.Float, divisions: scala.Int, center: com.badlogic.gdx.math.Vector3, normal: com.badlogic.gdx.math.Vector3): scala.Unit = {
    EllipseShapeBuilder.build(builder, width, height, innerWidth, innerHeight, divisions, center.x, center.y, center.z, normal.x, normal.y, normal.z, 0.0f, 360.0f)
  }
  def build(builder: com.badlogic.gdx.graphics.g3d.utils.MeshPartBuilder, width: scala.Float, height: scala.Float, innerWidth: scala.Float, innerHeight: scala.Float, divisions: scala.Int, centerX: scala.Float, centerY: scala.Float, centerZ: scala.Float, normalX: scala.Float, normalY: scala.Float, normalZ: scala.Float, tangentX: scala.Float, tangentY: scala.Float, tangentZ: scala.Float, binormalX: scala.Float, binormalY: scala.Float, binormalZ: scala.Float, angleFrom: scala.Float, angleTo: scala.Float): scala.Unit = {
    if ((innerWidth <= 0) || (innerHeight <= 0)) {
      builder.ensureVertices(divisions + 2)
      builder.ensureTriangleIndices(divisions)
    } else {
      if ((innerWidth == width) && (innerHeight == height)) {
        builder.ensureVertices(divisions + 1)
        builder.ensureIndices(divisions + 1)
        if (builder.getPrimitiveType() != com.badlogic.gdx.graphics.GL20.GL_LINES) {
          throw new com.badlogic.gdx.utils.GdxRuntimeException("Incorrect primitive type : expect GL_LINES because innerWidth == width && innerHeight == height")
        } else ()
      } else {
        builder.ensureVertices((divisions + 1) * 2)
        builder.ensureRectangleIndices(divisions + 1)
      }
    }
    val ao: scala.Float = com.badlogic.gdx.math.MathUtils.degreesToRadians * angleFrom
    val step: scala.Float = (com.badlogic.gdx.math.MathUtils.degreesToRadians * (angleTo - angleFrom)) / divisions
    val sxEx: com.badlogic.gdx.math.Vector3 = com.badlogic.gdx.graphics.g3d.utils.shapebuilders.BaseShapeBuilder.tmpV1.set(tangentX, tangentY, tangentZ).scl(width * 0.5f)
    val syEx: com.badlogic.gdx.math.Vector3 = com.badlogic.gdx.graphics.g3d.utils.shapebuilders.BaseShapeBuilder.tmpV2.set(binormalX, binormalY, binormalZ).scl(height * 0.5f)
    val sxIn: com.badlogic.gdx.math.Vector3 = com.badlogic.gdx.graphics.g3d.utils.shapebuilders.BaseShapeBuilder.tmpV3.set(tangentX, tangentY, tangentZ).scl(innerWidth * 0.5f)
    val syIn: com.badlogic.gdx.math.Vector3 = com.badlogic.gdx.graphics.g3d.utils.shapebuilders.BaseShapeBuilder.tmpV4.set(binormalX, binormalY, binormalZ).scl(innerHeight * 0.5f)
    val currIn: com.badlogic.gdx.graphics.g3d.utils.MeshPartBuilder.VertexInfo = com.badlogic.gdx.graphics.g3d.utils.shapebuilders.BaseShapeBuilder.vertTmp3.set(null, null, null, null)
    currIn.hasUV = {
      currIn.hasPosition = {
        currIn.hasNormal = true
        currIn.hasNormal
      }
      currIn.hasPosition
    }
    currIn.uv.set(0.5f, 0.5f)
    currIn.position.set(centerX, centerY, centerZ)
    currIn.normal.set(normalX, normalY, normalZ)
    val currEx: com.badlogic.gdx.graphics.g3d.utils.MeshPartBuilder.VertexInfo = com.badlogic.gdx.graphics.g3d.utils.shapebuilders.BaseShapeBuilder.vertTmp4.set(null, null, null, null)
    currEx.hasUV = {
      currEx.hasPosition = {
        currEx.hasNormal = true
        currEx.hasNormal
      }
      currEx.hasPosition
    }
    currEx.uv.set(0.5f, 0.5f)
    currEx.position.set(centerX, centerY, centerZ)
    currEx.normal.set(normalX, normalY, normalZ)
    val center: scala.Short = builder.vertex(currEx)
    var angle: scala.Float = 0.0f
    val us: scala.Float = 0.5f * (innerWidth / width)
    val vs: scala.Float = 0.5f * (innerHeight / height)
    var i1: scala.Short = 0
    var i2: scala.Short = 0.asInstanceOf[scala.Short]
    var i3: scala.Short = 0.asInstanceOf[scala.Short]
    var i4: scala.Short = 0.asInstanceOf[scala.Short];
    { var i: scala.Int = 0; while (i <= divisions) { {
      angle = ao + (step * i)
      val x: scala.Float = com.badlogic.gdx.math.MathUtils.cos(angle)
      val y: scala.Float = com.badlogic.gdx.math.MathUtils.sin(angle)
      currEx.position.set(centerX, centerY, centerZ).add((sxEx.x * x) + (syEx.x * y), (sxEx.y * x) + (syEx.y * y), (sxEx.z * x) + (syEx.z * y))
      currEx.uv.set(0.5f + (0.5f * x), 0.5f + (0.5f * y))
      i1 = builder.vertex(currEx)
      if ((innerWidth <= 0.0f) || (innerHeight <= 0.0f)) {
        if (i != 0) {
          builder.triangle(i1, i2, center)
        } else ()
        i2 = i1
      } else {
        if ((innerWidth == width) && (innerHeight == height)) {
          if (i != 0) {
            builder.line(i1, i2)
          } else ()
          i2 = i1
        } else {
          currIn.position.set(centerX, centerY, centerZ).add((sxIn.x * x) + (syIn.x * y), (sxIn.y * x) + (syIn.y * y), (sxIn.z * x) + (syIn.z * y))
          currIn.uv.set(0.5f + (us * x), 0.5f + (vs * y))
          i2 = i1
          i1 = builder.vertex(currIn)
          if (i != 0) {
            builder.rect(i1, i2, i4, i3)
          } else ()
          i4 = i2
          i3 = i1
        }
      }
    }; i = i + 1 } }
  }
}