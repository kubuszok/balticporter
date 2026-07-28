package com.badlogic.gdx.graphics.g3d.particles.values

final class CylinderSpawnShapeValue extends com.badlogic.gdx.graphics.g3d.particles.values.PrimitiveSpawnShapeValue {
  def this(cylinderSpawnShapeValue: CylinderSpawnShapeValue) = {
    this()
    this.spawnWidthValue = new com.badlogic.gdx.graphics.g3d.particles.values.ScaledNumericValue()
    this.spawnHeightValue = new com.badlogic.gdx.graphics.g3d.particles.values.ScaledNumericValue()
    this.spawnDepthValue = new com.badlogic.gdx.graphics.g3d.particles.values.ScaledNumericValue()
    this.load(cylinderSpawnShapeValue)
  }
  @java.lang.Override
  def spawnAux(vector: com.badlogic.gdx.math.Vector3, percent: scala.Float): scala.Unit = {
    val width: scala.Float = spawnWidth + (spawnWidthDiff * spawnWidthValue.getScale(percent))
    val height: scala.Float = spawnHeight + (spawnHeightDiff * spawnHeightValue.getScale(percent))
    val depth: scala.Float = spawnDepth + (spawnDepthDiff * spawnDepthValue.getScale(percent))
    var radiusX: scala.Float = 0.0f
    var radiusZ: scala.Float = 0.0f
    val hf: scala.Float = height / 2
    val ty: scala.Float = com.badlogic.gdx.math.MathUtils.random(height) - hf
    if (edges) {
      radiusX = width / 2
      radiusZ = depth / 2
    } else {
      radiusX = com.badlogic.gdx.math.MathUtils.random(width) / 2
      radiusZ = com.badlogic.gdx.math.MathUtils.random(depth) / 2
    }
    var spawnTheta: scala.Float = 0
    val isRadiusXZero: scala.Boolean = radiusX == 0
    val isRadiusZZero: scala.Boolean = radiusZ == 0
    if ((!isRadiusXZero) && (!isRadiusZZero)) {
      spawnTheta = com.badlogic.gdx.math.MathUtils.random(360.0f)
    } else {
      if (isRadiusXZero) {
        spawnTheta = if ((com.badlogic.gdx.math.MathUtils.random: (scala.Int) => scala.Int)(1) == 0) -90 else 90
      } else {
        if (isRadiusZZero) {
          spawnTheta = if ((com.badlogic.gdx.math.MathUtils.random: (scala.Int) => scala.Int)(1) == 0) 0 else 180
        } else ()
      }
    }
    vector.set(radiusX * com.badlogic.gdx.math.MathUtils.cosDeg(spawnTheta), ty, radiusZ * com.badlogic.gdx.math.MathUtils.sinDeg(spawnTheta))
  }
  @java.lang.Override
  def copy(): com.badlogic.gdx.graphics.g3d.particles.values.SpawnShapeValue = {
    return new CylinderSpawnShapeValue(this)
  }
}
object CylinderSpawnShapeValue {
  export com.badlogic.gdx.graphics.g3d.particles.values.PrimitiveSpawnShapeValue.*
}