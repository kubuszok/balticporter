package com.badlogic.gdx.graphics.g3d.utils.shapebuilders

class PatchShapeBuilder extends com.badlogic.gdx.graphics.g3d.utils.shapebuilders.BaseShapeBuilder
object PatchShapeBuilder {
  def build(builder: com.badlogic.gdx.graphics.g3d.utils.MeshPartBuilder, corner00: com.badlogic.gdx.graphics.g3d.utils.MeshPartBuilder#VertexInfo, corner10: com.badlogic.gdx.graphics.g3d.utils.MeshPartBuilder#VertexInfo, corner11: com.badlogic.gdx.graphics.g3d.utils.MeshPartBuilder#VertexInfo, corner01: com.badlogic.gdx.graphics.g3d.utils.MeshPartBuilder#VertexInfo, divisionsU: scala.Int, divisionsV: scala.Int): scala.Unit = {
    if ((divisionsU < 1) || (divisionsV < 1)) {
      throw new com.badlogic.gdx.utils.GdxRuntimeException((("divisionsU and divisionV must be > 0, u,v: " + divisionsU) + ", ") + divisionsV)
    } else ()
    builder.ensureVertices((divisionsV + 1) * (divisionsU + 1))
    builder.ensureRectangleIndices(divisionsV * divisionsU)
    { var u: scala.Int = 0; while (u <= divisionsU) { {
      val alphaU: scala.Float = u.asInstanceOf[scala.Float] / divisionsU.asInstanceOf[scala.Float]
      com.badlogic.gdx.graphics.g3d.utils.shapebuilders.BaseShapeBuilder.vertTmp5.set(corner00).lerp(corner10, alphaU)
      com.badlogic.gdx.graphics.g3d.utils.shapebuilders.BaseShapeBuilder.vertTmp6.set(corner01).lerp(corner11, alphaU)
      { var v: scala.Int = 0; while (v <= divisionsV) { {
        val idx: scala.Short = builder.vertex(com.badlogic.gdx.graphics.g3d.utils.shapebuilders.BaseShapeBuilder.vertTmp7.set(com.badlogic.gdx.graphics.g3d.utils.shapebuilders.BaseShapeBuilder.vertTmp5).lerp(com.badlogic.gdx.graphics.g3d.utils.shapebuilders.BaseShapeBuilder.vertTmp6, v.asInstanceOf[scala.Float] / divisionsV.asInstanceOf[scala.Float]))
        if ((u > 0) && (v > 0)) {
          builder.rect(((idx - divisionsV) - 2).asInstanceOf[scala.Short], (idx - 1).asInstanceOf[scala.Short], idx, ((idx - divisionsV) - 1).asInstanceOf[scala.Short])
        } else ()
      }; v = v + 1 } }
    }; u = u + 1 } }
  }
  def build(builder: com.badlogic.gdx.graphics.g3d.utils.MeshPartBuilder, corner00: com.badlogic.gdx.math.Vector3, corner10: com.badlogic.gdx.math.Vector3, corner11: com.badlogic.gdx.math.Vector3, corner01: com.badlogic.gdx.math.Vector3, normal: com.badlogic.gdx.math.Vector3, divisionsU: scala.Int, divisionsV: scala.Int): scala.Unit = {
    PatchShapeBuilder.build(builder, com.badlogic.gdx.graphics.g3d.utils.shapebuilders.BaseShapeBuilder.vertTmp1.set(corner00, normal, null, null).setUV(0.0f, 1.0f), com.badlogic.gdx.graphics.g3d.utils.shapebuilders.BaseShapeBuilder.vertTmp2.set(corner10, normal, null, null).setUV(1.0f, 1.0f), com.badlogic.gdx.graphics.g3d.utils.shapebuilders.BaseShapeBuilder.vertTmp3.set(corner11, normal, null, null).setUV(1.0f, 0.0f), com.badlogic.gdx.graphics.g3d.utils.shapebuilders.BaseShapeBuilder.vertTmp4.set(corner01, normal, null, null).setUV(0.0f, 0.0f), divisionsU, divisionsV)
  }
  def build(builder: com.badlogic.gdx.graphics.g3d.utils.MeshPartBuilder, x00: scala.Float, y00: scala.Float, z00: scala.Float, x10: scala.Float, y10: scala.Float, z10: scala.Float, x11: scala.Float, y11: scala.Float, z11: scala.Float, x01: scala.Float, y01: scala.Float, z01: scala.Float, normalX: scala.Float, normalY: scala.Float, normalZ: scala.Float, divisionsU: scala.Int, divisionsV: scala.Int): scala.Unit = {
    PatchShapeBuilder.build(builder, com.badlogic.gdx.graphics.g3d.utils.shapebuilders.BaseShapeBuilder.vertTmp1.set(null).setPos(x00, y00, z00).setNor(normalX, normalY, normalZ).setUV(0.0f, 1.0f), com.badlogic.gdx.graphics.g3d.utils.shapebuilders.BaseShapeBuilder.vertTmp2.set(null).setPos(x10, y10, z10).setNor(normalX, normalY, normalZ).setUV(1.0f, 1.0f), com.badlogic.gdx.graphics.g3d.utils.shapebuilders.BaseShapeBuilder.vertTmp3.set(null).setPos(x11, y11, z11).setNor(normalX, normalY, normalZ).setUV(1.0f, 0.0f), com.badlogic.gdx.graphics.g3d.utils.shapebuilders.BaseShapeBuilder.vertTmp4.set(null).setPos(x01, y01, z01).setNor(normalX, normalY, normalZ).setUV(0.0f, 0.0f), divisionsU, divisionsV)
  }
}