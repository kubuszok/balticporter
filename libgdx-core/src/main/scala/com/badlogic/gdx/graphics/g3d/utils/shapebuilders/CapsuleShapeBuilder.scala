package com.badlogic.gdx.graphics.g3d.utils.shapebuilders

class CapsuleShapeBuilder extends com.badlogic.gdx.graphics.g3d.utils.shapebuilders.BaseShapeBuilder
object CapsuleShapeBuilder {
  export com.badlogic.gdx.graphics.g3d.utils.shapebuilders.BaseShapeBuilder.{build => _, *}
  def build(builder: com.badlogic.gdx.graphics.g3d.utils.MeshPartBuilder, radius: scala.Float, height: scala.Float, divisions: scala.Int): scala.Unit = {
    if (height < (2.0f * radius)) {
      throw new com.badlogic.gdx.utils.GdxRuntimeException("Height must be at least twice the radius")
    } else ()
    val d: scala.Float = 2.0f * radius
    com.badlogic.gdx.graphics.g3d.utils.shapebuilders.CylinderShapeBuilder.build(builder, d, height - d, d, divisions, 0, 360, false)
    com.badlogic.gdx.graphics.g3d.utils.shapebuilders.SphereShapeBuilder.build(builder, com.badlogic.gdx.graphics.g3d.utils.shapebuilders.BaseShapeBuilder.matTmp1.setToTranslation(0, 0.5f * (height - d), 0), d, d, d, divisions, divisions, 0, 360, 0, 90)
    com.badlogic.gdx.graphics.g3d.utils.shapebuilders.SphereShapeBuilder.build(builder, com.badlogic.gdx.graphics.g3d.utils.shapebuilders.BaseShapeBuilder.matTmp1.setToTranslation(0, (-0.5f) * (height - d), 0), d, d, d, divisions, divisions, 0, 360, 90, 180)
  }
}