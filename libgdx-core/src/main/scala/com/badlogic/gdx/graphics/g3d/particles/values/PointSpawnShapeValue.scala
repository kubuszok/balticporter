package com.badlogic.gdx.graphics.g3d.particles.values

final class PointSpawnShapeValue extends com.badlogic.gdx.graphics.g3d.particles.values.PrimitiveSpawnShapeValue {
  def this(value: PointSpawnShapeValue) = {
    this()
    this.spawnWidthValue = new com.badlogic.gdx.graphics.g3d.particles.values.ScaledNumericValue()
    this.spawnHeightValue = new com.badlogic.gdx.graphics.g3d.particles.values.ScaledNumericValue()
    this.spawnDepthValue = new com.badlogic.gdx.graphics.g3d.particles.values.ScaledNumericValue()
    this.load(value)
  }
  @java.lang.Override
  override def spawnAux(vector: com.badlogic.gdx.math.Vector3, percent: scala.Float): scala.Unit = {
    vector.x = spawnWidth + (spawnWidthDiff * spawnWidthValue.getScale(percent))
    vector.y = spawnHeight + (spawnHeightDiff * spawnHeightValue.getScale(percent))
    vector.z = spawnDepth + (spawnDepthDiff * spawnDepthValue.getScale(percent))
  }
  @java.lang.Override
  override def copy(): com.badlogic.gdx.graphics.g3d.particles.values.SpawnShapeValue = {
    return new PointSpawnShapeValue(this)
  }
}
object PointSpawnShapeValue {
  export com.badlogic.gdx.graphics.g3d.particles.values.PrimitiveSpawnShapeValue.*
}