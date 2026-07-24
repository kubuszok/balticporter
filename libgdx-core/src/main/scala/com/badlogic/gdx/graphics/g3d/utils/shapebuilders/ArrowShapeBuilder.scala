package com.badlogic.gdx.graphics.g3d.utils.shapebuilders

class ArrowShapeBuilder extends com.badlogic.gdx.graphics.g3d.utils.shapebuilders.BaseShapeBuilder
object ArrowShapeBuilder {
  export com.badlogic.gdx.graphics.g3d.utils.shapebuilders.BaseShapeBuilder.*
  def build(builder: com.badlogic.gdx.graphics.g3d.utils.MeshPartBuilder, x1: scala.Float, y1: scala.Float, z1: scala.Float, x2: scala.Float, y2: scala.Float, z2: scala.Float, capLength: scala.Float, stemThickness: scala.Float, divisions: scala.Int): scala.Unit = {
    val begin: com.badlogic.gdx.math.Vector3 = com.badlogic.gdx.graphics.g3d.utils.shapebuilders.BaseShapeBuilder.obtainV3().set(x1, y1, z1)
    val `end`: com.badlogic.gdx.math.Vector3 = com.badlogic.gdx.graphics.g3d.utils.shapebuilders.BaseShapeBuilder.obtainV3().set(x2, y2, z2)
    val length: scala.Float = begin.dst(`end`)
    val coneHeight: scala.Float = length * capLength
    val coneDiameter: scala.Float = 2 * (coneHeight * java.lang.Math.sqrt(1.0f / 3)).asInstanceOf[scala.Float]
    val stemLength: scala.Float = length - coneHeight
    val stemDiameter: scala.Float = coneDiameter * stemThickness
    val up: com.badlogic.gdx.math.Vector3 = com.badlogic.gdx.graphics.g3d.utils.shapebuilders.BaseShapeBuilder.obtainV3().set(`end`).sub(begin).nor()
    val forward: com.badlogic.gdx.math.Vector3 = com.badlogic.gdx.graphics.g3d.utils.shapebuilders.BaseShapeBuilder.obtainV3().set(up).crs(com.badlogic.gdx.math.Vector3.Z)
    if (forward.isZero()) {
      forward.set(com.badlogic.gdx.math.Vector3.X)
    } else ()
    forward.crs(up).nor()
    val left: com.badlogic.gdx.math.Vector3 = com.badlogic.gdx.graphics.g3d.utils.shapebuilders.BaseShapeBuilder.obtainV3().set(up).crs(forward).nor()
    val direction: com.badlogic.gdx.math.Vector3 = com.badlogic.gdx.graphics.g3d.utils.shapebuilders.BaseShapeBuilder.obtainV3().set(`end`).sub(begin).nor()
    val userTransform: com.badlogic.gdx.math.Matrix4 = builder.getVertexTransform(com.badlogic.gdx.graphics.g3d.utils.shapebuilders.BaseShapeBuilder.obtainM4())
    val transform: com.badlogic.gdx.math.Matrix4 = com.badlogic.gdx.graphics.g3d.utils.shapebuilders.BaseShapeBuilder.obtainM4()
    val `val`: scala.Array[scala.Float] = transform.`val`
    `val`(com.badlogic.gdx.math.Matrix4.M00) = left.x
    `val`(com.badlogic.gdx.math.Matrix4.M01) = up.x
    `val`(com.badlogic.gdx.math.Matrix4.M02) = forward.x
    `val`(com.badlogic.gdx.math.Matrix4.M10) = left.y
    `val`(com.badlogic.gdx.math.Matrix4.M11) = up.y
    `val`(com.badlogic.gdx.math.Matrix4.M12) = forward.y
    `val`(com.badlogic.gdx.math.Matrix4.M20) = left.z
    `val`(com.badlogic.gdx.math.Matrix4.M21) = up.z
    `val`(com.badlogic.gdx.math.Matrix4.M22) = forward.z
    val temp: com.badlogic.gdx.math.Matrix4 = com.badlogic.gdx.graphics.g3d.utils.shapebuilders.BaseShapeBuilder.obtainM4()
    transform.setTranslation(com.badlogic.gdx.graphics.g3d.utils.shapebuilders.BaseShapeBuilder.obtainV3().set(direction).scl(stemLength / 2).add(x1, y1, z1))
    builder.setVertexTransform(temp.set(transform).mul(userTransform))
    com.badlogic.gdx.graphics.g3d.utils.shapebuilders.CylinderShapeBuilder.build(builder, stemDiameter, stemLength, stemDiameter, divisions)
    transform.setTranslation(com.badlogic.gdx.graphics.g3d.utils.shapebuilders.BaseShapeBuilder.obtainV3().set(direction).scl(stemLength).add(x1, y1, z1))
    builder.setVertexTransform(temp.set(transform).mul(userTransform))
    com.badlogic.gdx.graphics.g3d.utils.shapebuilders.ConeShapeBuilder.build(builder, coneDiameter, coneHeight, coneDiameter, divisions)
    builder.setVertexTransform(userTransform)
    com.badlogic.gdx.graphics.g3d.utils.shapebuilders.BaseShapeBuilder.freeAll()
  }
}