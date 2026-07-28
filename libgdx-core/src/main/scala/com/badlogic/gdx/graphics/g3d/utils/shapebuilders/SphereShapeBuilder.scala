package com.badlogic.gdx.graphics.g3d.utils.shapebuilders

class SphereShapeBuilder extends com.badlogic.gdx.graphics.g3d.utils.shapebuilders.BaseShapeBuilder
object SphereShapeBuilder {
  export com.badlogic.gdx.graphics.g3d.utils.shapebuilders.BaseShapeBuilder.{build => _, normalTransform => _, tmpIndices => _, *}
  private final val tmpIndices: com.badlogic.gdx.utils.ShortArray = new com.badlogic.gdx.utils.ShortArray()
  private final val normalTransform: com.badlogic.gdx.math.Matrix3 = new com.badlogic.gdx.math.Matrix3()
  def build(builder: com.badlogic.gdx.graphics.g3d.utils.MeshPartBuilder, width: scala.Float, height: scala.Float, depth: scala.Float, divisionsU: scala.Int, divisionsV: scala.Int): scala.Unit = {
    SphereShapeBuilder.build(builder, width, height, depth, divisionsU, divisionsV, 0, 360, 0, 180)
  }
  def build(builder: com.badlogic.gdx.graphics.g3d.utils.MeshPartBuilder, transform: com.badlogic.gdx.math.Matrix4, width: scala.Float, height: scala.Float, depth: scala.Float, divisionsU: scala.Int, divisionsV: scala.Int): scala.Unit = {
    SphereShapeBuilder.build(builder, transform, width, height, depth, divisionsU, divisionsV, 0, 360, 0, 180)
  }
  def build(builder: com.badlogic.gdx.graphics.g3d.utils.MeshPartBuilder, width: scala.Float, height: scala.Float, depth: scala.Float, divisionsU: scala.Int, divisionsV: scala.Int, angleUFrom: scala.Float, angleUTo: scala.Float, angleVFrom: scala.Float, angleVTo: scala.Float): scala.Unit = {
    SphereShapeBuilder.build(builder, com.badlogic.gdx.graphics.g3d.utils.shapebuilders.BaseShapeBuilder.matTmp1.idt(), width, height, depth, divisionsU, divisionsV, angleUFrom, angleUTo, angleVFrom, angleVTo)
  }
  def build(builder: com.badlogic.gdx.graphics.g3d.utils.MeshPartBuilder, transform: com.badlogic.gdx.math.Matrix4, width: scala.Float, height: scala.Float, depth: scala.Float, divisionsU: scala.Int, divisionsV: scala.Int, angleUFrom: scala.Float, angleUTo: scala.Float, angleVFrom: scala.Float, angleVTo: scala.Float): scala.Unit = {
    val closedVFrom: scala.Boolean = com.badlogic.gdx.math.MathUtils.isEqual(angleVFrom, 0.0f)
    val closedVTo: scala.Boolean = com.badlogic.gdx.math.MathUtils.isEqual(angleVTo, 180.0f)
    val hw: scala.Float = width * 0.5f
    val hh: scala.Float = height * 0.5f
    val hd: scala.Float = depth * 0.5f
    val auo: scala.Float = com.badlogic.gdx.math.MathUtils.degreesToRadians * angleUFrom
    val stepU: scala.Float = (com.badlogic.gdx.math.MathUtils.degreesToRadians * (angleUTo - angleUFrom)) / divisionsU
    val avo: scala.Float = com.badlogic.gdx.math.MathUtils.degreesToRadians * angleVFrom
    val stepV: scala.Float = (com.badlogic.gdx.math.MathUtils.degreesToRadians * (angleVTo - angleVFrom)) / divisionsV
    val us: scala.Float = 1.0f / divisionsU
    val vs: scala.Float = 1.0f / divisionsV
    var u: scala.Float = 0.0f
    var v: scala.Float = 0.0f
    var angleU: scala.Float = 0.0f
    var angleV: scala.Float = 0.0f
    val curr1: com.badlogic.gdx.graphics.g3d.utils.MeshPartBuilder.VertexInfo = com.badlogic.gdx.graphics.g3d.utils.shapebuilders.BaseShapeBuilder.vertTmp3.set(null, null, null, null)
    curr1.hasUV = {
      curr1.hasPosition = {
        curr1.hasNormal = true
        curr1.hasNormal
      }
      curr1.hasPosition
    }
    SphereShapeBuilder.normalTransform.set(transform)
    val s: scala.Int = divisionsU + 3
    SphereShapeBuilder.tmpIndices.clear()
    SphereShapeBuilder.tmpIndices.ensureCapacity(divisionsU * 2)
    SphereShapeBuilder.tmpIndices.size = s
    var tempOffset: scala.Int = 0
    builder.ensureVertices((divisionsV + 1) * (divisionsU + 1))
    builder.ensureRectangleIndices(divisionsU);
    { var iv: scala.Int = 0; while (iv <= divisionsV) { {
      angleV = avo + (stepV * iv)
      v = vs * iv
      val t: scala.Float = com.badlogic.gdx.math.MathUtils.sin(angleV)
      val h: scala.Float = com.badlogic.gdx.math.MathUtils.cos(angleV) * hh;
      { var iu: scala.Int = 0; while (iu <= divisionsU) { {
        angleU = auo + (stepU * iu)
        if (((iv == 0) && closedVFrom) || ((iv == divisionsV) && closedVTo)) {
          u = 1.0f - (us * (iu - 0.5f))
        } else {
          u = 1.0f - (us * iu)
        }
        curr1.position.set((com.badlogic.gdx.math.MathUtils.cos(angleU) * hw) * t, h, (com.badlogic.gdx.math.MathUtils.sin(angleU) * hd) * t)
        curr1.normal.set(curr1.position).mul(SphereShapeBuilder.normalTransform).nor()
        curr1.position.mul(transform)
        curr1.uv.set(u, v)
        SphereShapeBuilder.tmpIndices.set(tempOffset, builder.vertex(curr1))
        val o: scala.Int = tempOffset + s
        if ((iv > 0) && (iu > 0)) {
          if ((iv == 1) && closedVFrom) {
            builder.triangle(SphereShapeBuilder.tmpIndices.get(tempOffset), SphereShapeBuilder.tmpIndices.get((o - 1) % s), SphereShapeBuilder.tmpIndices.get((o - (divisionsU + 1)) % s))
          } else {
            if ((iv == divisionsV) && closedVTo) {
              builder.triangle(SphereShapeBuilder.tmpIndices.get(tempOffset), SphereShapeBuilder.tmpIndices.get((o - (divisionsU + 2)) % s), SphereShapeBuilder.tmpIndices.get((o - (divisionsU + 1)) % s))
            } else {
              builder.rect(SphereShapeBuilder.tmpIndices.get(tempOffset), SphereShapeBuilder.tmpIndices.get((o - 1) % s), SphereShapeBuilder.tmpIndices.get((o - (divisionsU + 2)) % s), SphereShapeBuilder.tmpIndices.get((o - (divisionsU + 1)) % s))
            }
          }
        } else ()
        tempOffset = (tempOffset + 1) % SphereShapeBuilder.tmpIndices.size
      }; iu = iu + 1 } }
    }; iv = iv + 1 } }
  }
}