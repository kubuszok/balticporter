package com.badlogic.gdx.graphics.g3d.utils.shapebuilders

class FrustumShapeBuilder extends com.badlogic.gdx.graphics.g3d.utils.shapebuilders.BaseShapeBuilder
object FrustumShapeBuilder {
  export com.badlogic.gdx.graphics.g3d.utils.shapebuilders.BaseShapeBuilder.*
  def build(builder: com.badlogic.gdx.graphics.g3d.utils.MeshPartBuilder, camera: com.badlogic.gdx.graphics.Camera): scala.Unit = {
    FrustumShapeBuilder.build(builder, camera, com.badlogic.gdx.graphics.g3d.utils.shapebuilders.BaseShapeBuilder.tmpColor0.set(1, 0.66f, 0, 1), com.badlogic.gdx.graphics.g3d.utils.shapebuilders.BaseShapeBuilder.tmpColor1.set(1, 0, 0, 1), com.badlogic.gdx.graphics.g3d.utils.shapebuilders.BaseShapeBuilder.tmpColor2.set(0, 0.66f, 1, 1), com.badlogic.gdx.graphics.g3d.utils.shapebuilders.BaseShapeBuilder.tmpColor3.set(1, 1, 1, 1), com.badlogic.gdx.graphics.g3d.utils.shapebuilders.BaseShapeBuilder.tmpColor4.set(0.2f, 0.2f, 0.2f, 1))
  }
  def build(builder: com.badlogic.gdx.graphics.g3d.utils.MeshPartBuilder, camera: com.badlogic.gdx.graphics.Camera, frustumColor: com.badlogic.gdx.graphics.Color, coneColor: com.badlogic.gdx.graphics.Color, upColor: com.badlogic.gdx.graphics.Color, targetColor: com.badlogic.gdx.graphics.Color, crossColor: com.badlogic.gdx.graphics.Color): scala.Unit = {
    val planePoints: scala.Array[com.badlogic.gdx.math.Vector3] = camera.frustum.planePoints
    FrustumShapeBuilder.build(builder, camera.frustum, frustumColor, crossColor)
    builder.line(planePoints(0), coneColor, camera.position, coneColor)
    builder.line(planePoints(1), coneColor, camera.position, coneColor)
    builder.line(planePoints(2), coneColor, camera.position, coneColor)
    builder.line(planePoints(3), coneColor, camera.position, coneColor)
    builder.line(camera.position, targetColor, FrustumShapeBuilder.centerPoint(planePoints(4), planePoints(5), planePoints(6)), targetColor)
    val halfNearSize: scala.Float = com.badlogic.gdx.graphics.g3d.utils.shapebuilders.BaseShapeBuilder.tmpV0.set(planePoints(1)).sub(planePoints(0)).scl(0.5f).len()
    val centerNear: com.badlogic.gdx.math.Vector3 = FrustumShapeBuilder.centerPoint(planePoints(0), planePoints(1), planePoints(2))
    com.badlogic.gdx.graphics.g3d.utils.shapebuilders.BaseShapeBuilder.tmpV0.set(camera.up).scl(halfNearSize * 2)
    centerNear.add(com.badlogic.gdx.graphics.g3d.utils.shapebuilders.BaseShapeBuilder.tmpV0)
    builder.line(centerNear, upColor, planePoints(2), upColor)
    builder.line(planePoints(2), upColor, planePoints(3), upColor)
    builder.line(planePoints(3), upColor, centerNear, upColor)
  }
  def build(builder: com.badlogic.gdx.graphics.g3d.utils.MeshPartBuilder, frustum: com.badlogic.gdx.math.Frustum, frustumColor: com.badlogic.gdx.graphics.Color, crossColor: com.badlogic.gdx.graphics.Color): scala.Unit = {
    val planePoints: scala.Array[com.badlogic.gdx.math.Vector3] = frustum.planePoints
    builder.line(planePoints(0), frustumColor, planePoints(1), frustumColor)
    builder.line(planePoints(1), frustumColor, planePoints(2), frustumColor)
    builder.line(planePoints(2), frustumColor, planePoints(3), frustumColor)
    builder.line(planePoints(3), frustumColor, planePoints(0), frustumColor)
    builder.line(planePoints(4), frustumColor, planePoints(5), frustumColor)
    builder.line(planePoints(5), frustumColor, planePoints(6), frustumColor)
    builder.line(planePoints(6), frustumColor, planePoints(7), frustumColor)
    builder.line(planePoints(7), frustumColor, planePoints(4), frustumColor)
    builder.line(planePoints(0), frustumColor, planePoints(4), frustumColor)
    builder.line(planePoints(1), frustumColor, planePoints(5), frustumColor)
    builder.line(planePoints(2), frustumColor, planePoints(6), frustumColor)
    builder.line(planePoints(3), frustumColor, planePoints(7), frustumColor)
    builder.line(FrustumShapeBuilder.middlePoint(planePoints(1), planePoints(0)), crossColor, FrustumShapeBuilder.middlePoint(planePoints(3), planePoints(2)), crossColor)
    builder.line(FrustumShapeBuilder.middlePoint(planePoints(2), planePoints(1)), crossColor, FrustumShapeBuilder.middlePoint(planePoints(3), planePoints(0)), crossColor)
    builder.line(FrustumShapeBuilder.middlePoint(planePoints(5), planePoints(4)), crossColor, FrustumShapeBuilder.middlePoint(planePoints(7), planePoints(6)), crossColor)
    builder.line(FrustumShapeBuilder.middlePoint(planePoints(6), planePoints(5)), crossColor, FrustumShapeBuilder.middlePoint(planePoints(7), planePoints(4)), crossColor)
  }
  private def middlePoint(point0: com.badlogic.gdx.math.Vector3, point1: com.badlogic.gdx.math.Vector3): com.badlogic.gdx.math.Vector3 = {
    com.badlogic.gdx.graphics.g3d.utils.shapebuilders.BaseShapeBuilder.tmpV0.set(point1).sub(point0).scl(0.5f)
    return com.badlogic.gdx.graphics.g3d.utils.shapebuilders.BaseShapeBuilder.tmpV1.set(point0).add(com.badlogic.gdx.graphics.g3d.utils.shapebuilders.BaseShapeBuilder.tmpV0)
  }
  private def centerPoint(point0: com.badlogic.gdx.math.Vector3, point1: com.badlogic.gdx.math.Vector3, point2: com.badlogic.gdx.math.Vector3): com.badlogic.gdx.math.Vector3 = {
    com.badlogic.gdx.graphics.g3d.utils.shapebuilders.BaseShapeBuilder.tmpV0.set(point1).sub(point0).scl(0.5f)
    com.badlogic.gdx.graphics.g3d.utils.shapebuilders.BaseShapeBuilder.tmpV1.set(point0).add(com.badlogic.gdx.graphics.g3d.utils.shapebuilders.BaseShapeBuilder.tmpV0)
    com.badlogic.gdx.graphics.g3d.utils.shapebuilders.BaseShapeBuilder.tmpV0.set(point2).sub(point1).scl(0.5f)
    return com.badlogic.gdx.graphics.g3d.utils.shapebuilders.BaseShapeBuilder.tmpV1.add(com.badlogic.gdx.graphics.g3d.utils.shapebuilders.BaseShapeBuilder.tmpV0)
  }
}