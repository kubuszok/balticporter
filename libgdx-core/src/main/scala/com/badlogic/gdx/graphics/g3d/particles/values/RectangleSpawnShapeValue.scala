package com.badlogic.gdx.graphics.g3d.particles.values

final class RectangleSpawnShapeValue extends com.badlogic.gdx.graphics.g3d.particles.values.PrimitiveSpawnShapeValue {
  def this(value: RectangleSpawnShapeValue) = {
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
    if (edges) {
      val a: scala.Int = (com.badlogic.gdx.math.MathUtils.random: (scala.Int, scala.Int) => scala.Int)(-1, 1)
      var tx: scala.Float = 0
      var ty: scala.Float = 0
      var tz: scala.Float = 0
      if (a == (-1)) {
        tx = if ((com.badlogic.gdx.math.MathUtils.random: (scala.Int) => scala.Int)(1) == 0) (-width) / 2 else width / 2
        if (tx == 0) {
          ty = if ((com.badlogic.gdx.math.MathUtils.random: (scala.Int) => scala.Int)(1) == 0) (-height) / 2 else height / 2
          tz = if ((com.badlogic.gdx.math.MathUtils.random: (scala.Int) => scala.Int)(1) == 0) (-depth) / 2 else depth / 2
        } else {
          ty = com.badlogic.gdx.math.MathUtils.random(height) - (height / 2)
          tz = com.badlogic.gdx.math.MathUtils.random(depth) - (depth / 2)
        }
      } else {
        if (a == 0) {
          tz = if ((com.badlogic.gdx.math.MathUtils.random: (scala.Int) => scala.Int)(1) == 0) (-depth) / 2 else depth / 2
          if (tz == 0) {
            ty = if ((com.badlogic.gdx.math.MathUtils.random: (scala.Int) => scala.Int)(1) == 0) (-height) / 2 else height / 2
            tx = if ((com.badlogic.gdx.math.MathUtils.random: (scala.Int) => scala.Int)(1) == 0) (-width) / 2 else width / 2
          } else {
            ty = com.badlogic.gdx.math.MathUtils.random(height) - (height / 2)
            tx = com.badlogic.gdx.math.MathUtils.random(width) - (width / 2)
          }
        } else {
          ty = if ((com.badlogic.gdx.math.MathUtils.random: (scala.Int) => scala.Int)(1) == 0) (-height) / 2 else height / 2
          if (ty == 0) {
            tx = if ((com.badlogic.gdx.math.MathUtils.random: (scala.Int) => scala.Int)(1) == 0) (-width) / 2 else width / 2
            tz = if ((com.badlogic.gdx.math.MathUtils.random: (scala.Int) => scala.Int)(1) == 0) (-depth) / 2 else depth / 2
          } else {
            tx = com.badlogic.gdx.math.MathUtils.random(width) - (width / 2)
            tz = com.badlogic.gdx.math.MathUtils.random(depth) - (depth / 2)
          }
        }
      }
      vector.x = tx
      vector.y = ty
      vector.z = tz
    } else {
      vector.x = com.badlogic.gdx.math.MathUtils.random(width) - (width / 2)
      vector.y = com.badlogic.gdx.math.MathUtils.random(height) - (height / 2)
      vector.z = com.badlogic.gdx.math.MathUtils.random(depth) - (depth / 2)
    }
  }
  @java.lang.Override
  def copy(): com.badlogic.gdx.graphics.g3d.particles.values.SpawnShapeValue = {
    return new RectangleSpawnShapeValue(this)
  }
}
object RectangleSpawnShapeValue {
  export com.badlogic.gdx.graphics.g3d.particles.values.PrimitiveSpawnShapeValue.*
}