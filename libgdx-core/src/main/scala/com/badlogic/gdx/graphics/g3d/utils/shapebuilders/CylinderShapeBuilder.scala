package com.badlogic.gdx.graphics.g3d.utils.shapebuilders

class CylinderShapeBuilder extends com.badlogic.gdx.graphics.g3d.utils.shapebuilders.BaseShapeBuilder
object CylinderShapeBuilder {
  def build(builder: com.badlogic.gdx.graphics.g3d.utils.MeshPartBuilder, width: scala.Float, height: scala.Float, depth: scala.Float, divisions: scala.Int): scala.Unit = {
    CylinderShapeBuilder.build(builder, width, height, depth, divisions, 0, 360)
  }
  def build(builder: com.badlogic.gdx.graphics.g3d.utils.MeshPartBuilder, width: scala.Float, height: scala.Float, depth: scala.Float, divisions: scala.Int, angleFrom: scala.Float, angleTo: scala.Float): scala.Unit = {
    CylinderShapeBuilder.build(builder, width, height, depth, divisions, angleFrom, angleTo, true)
  }
  def build(builder: com.badlogic.gdx.graphics.g3d.utils.MeshPartBuilder, width: scala.Float, height: scala.Float, depth: scala.Float, divisions: scala.Int, angleFrom: scala.Float, angleTo: scala.Float, close: scala.Boolean): scala.Unit = {
    val hw: scala.Float = width * 0.5f
    val hh: scala.Float = height * 0.5f
    val hd: scala.Float = depth * 0.5f
    val ao: scala.Float = com.badlogic.gdx.math.MathUtils.degreesToRadians * angleFrom
    val step: scala.Float = (com.badlogic.gdx.math.MathUtils.degreesToRadians * (angleTo - angleFrom)) / divisions
    val us: scala.Float = 1.0f / divisions
    var u: scala.Float = 0.0f
    var angle: scala.Float = 0.0f
    val curr1: com.badlogic.gdx.graphics.g3d.utils.MeshPartBuilder.VertexInfo = com.badlogic.gdx.graphics.g3d.utils.shapebuilders.BaseShapeBuilder.vertTmp3.set(null, null, null, null)
    curr1.hasUV = {
      curr1.hasPosition = {
        curr1.hasNormal = true
        curr1.hasNormal
      }
      curr1.hasPosition
    }
    val curr2: com.badlogic.gdx.graphics.g3d.utils.MeshPartBuilder.VertexInfo = com.badlogic.gdx.graphics.g3d.utils.shapebuilders.BaseShapeBuilder.vertTmp4.set(null, null, null, null)
    curr2.hasUV = {
      curr2.hasPosition = {
        curr2.hasNormal = true
        curr2.hasNormal
      }
      curr2.hasPosition
    }
    var i1: scala.Short = 0
    var i2: scala.Short = 0
    var i3: scala.Short = 0.asInstanceOf[scala.Short]
    var i4: scala.Short = 0.asInstanceOf[scala.Short]
    builder.ensureVertices(2 * (divisions + 1))
    builder.ensureRectangleIndices(divisions);
    { var i: scala.Int = 0; while (i <= divisions) { {
      angle = ao + (step * i)
      u = 1.0f - (us * i)
      curr1.position.set(com.badlogic.gdx.math.MathUtils.cos(angle) * hw, 0.0f, com.badlogic.gdx.math.MathUtils.sin(angle) * hd)
      curr1.normal.set(curr1.position).nor()
      curr1.position.y = -hh
      curr1.uv.set(u, 1)
      curr2.position.set(curr1.position)
      curr2.normal.set(curr1.normal)
      curr2.position.y = hh
      curr2.uv.set(u, 0)
      i2 = builder.vertex(curr1)
      i1 = builder.vertex(curr2)
      if (i != 0) {
        builder.rect(i3, i1, i2, i4)
      } else ()
      i4 = i2
      i3 = i1
    }; i = i + 1 } }
    if (close) {
      com.badlogic.gdx.graphics.g3d.utils.shapebuilders.EllipseShapeBuilder.build(builder, width, depth, 0, 0, divisions, 0, hh, 0, 0, 1, 0, 1, 0, 0, 0, 0, 1, angleFrom, angleTo)
      com.badlogic.gdx.graphics.g3d.utils.shapebuilders.EllipseShapeBuilder.build(builder, width, depth, 0, 0, divisions, 0, -hh, 0, 0, -1, 0, -1, 0, 0, 0, 0, 1, 180.0f - angleTo, 180.0f - angleFrom)
    } else ()
  }
}