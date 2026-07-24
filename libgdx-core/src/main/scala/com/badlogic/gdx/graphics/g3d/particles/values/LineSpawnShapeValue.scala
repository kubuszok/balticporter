package com.badlogic.gdx.graphics.g3d.particles.values

final class LineSpawnShapeValue extends com.badlogic.gdx.graphics.g3d.particles.values.PrimitiveSpawnShapeValue {
  def this(value: LineSpawnShapeValue) = {
    this()
    this.load(value)
  }
  def spawnAux(vector: com.badlogic.gdx.math.Vector3, percent: scala.Float): scala.Unit = {
    val width: scala.Float = spawnWidth + (spawnWidthDiff * spawnWidthValue.getScale(percent))
    val height: scala.Float = spawnHeight + (spawnHeightDiff * spawnHeightValue.getScale(percent))
    val depth: scala.Float = spawnDepth + (spawnDepthDiff * spawnDepthValue.getScale(percent))
    val a: scala.Float = com.badlogic.gdx.math.MathUtils.random()
    vector.x = a * width
    vector.y = a * height
    vector.z = a * depth
  }
  def copy(): com.badlogic.gdx.graphics.g3d.particles.values.SpawnShapeValue = {
    return new LineSpawnShapeValue(this)
  }
}