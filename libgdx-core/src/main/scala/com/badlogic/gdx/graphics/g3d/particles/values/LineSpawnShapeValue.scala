package com.badlogic.gdx.graphics.g3d.particles.values

final class LineSpawnShapeValue extends com.badlogic.gdx.graphics.g3d.particles.values.PrimitiveSpawnShapeValue {
  def this(value: LineSpawnShapeValue) = {
    this()
    this.spawnWidthValue = new com.badlogic.gdx.graphics.g3d.particles.values.ScaledNumericValue()
    this.spawnHeightValue = new com.badlogic.gdx.graphics.g3d.particles.values.ScaledNumericValue()
    this.spawnDepthValue = new com.badlogic.gdx.graphics.g3d.particles.values.ScaledNumericValue()
    this.load(value)
  }
  @java.lang.Override
  def spawnAux(vector: com.badlogic.gdx.math.Vector3, percent: scala.Float): scala.Unit = {
    val width: scala.Float = spawnWidth + (spawnWidthDiff * spawnWidthValue.getScale(percent))
    val height: scala.Float = spawnHeight + (spawnHeightDiff * spawnHeightValue.getScale(percent))
    val depth: scala.Float = spawnDepth + (spawnDepthDiff * spawnDepthValue.getScale(percent))
    val a: scala.Float = com.badlogic.gdx.math.MathUtils.random()
    vector.x = a * width
    vector.y = a * height
    vector.z = a * depth
  }
  @java.lang.Override
  def copy(): com.badlogic.gdx.graphics.g3d.particles.values.SpawnShapeValue = {
    return new LineSpawnShapeValue(this)
  }
}
object LineSpawnShapeValue {
  export com.badlogic.gdx.graphics.g3d.particles.values.PrimitiveSpawnShapeValue.*
}