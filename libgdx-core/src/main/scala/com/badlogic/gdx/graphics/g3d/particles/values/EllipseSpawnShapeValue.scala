package com.badlogic.gdx.graphics.g3d.particles.values

final class EllipseSpawnShapeValue extends com.badlogic.gdx.graphics.g3d.particles.values.PrimitiveSpawnShapeValue {
  var side: com.badlogic.gdx.graphics.g3d.particles.values.PrimitiveSpawnShapeValue.SpawnSide = com.badlogic.gdx.graphics.g3d.particles.values.PrimitiveSpawnShapeValue.SpawnSide.both
  def this(value: EllipseSpawnShapeValue) = {
    this()
    this.spawnWidthValue = new com.badlogic.gdx.graphics.g3d.particles.values.ScaledNumericValue()
    this.spawnHeightValue = new com.badlogic.gdx.graphics.g3d.particles.values.ScaledNumericValue()
    this.spawnDepthValue = new com.badlogic.gdx.graphics.g3d.particles.values.ScaledNumericValue()
    this.load(value)
  }
  def spawnAux(vector: com.badlogic.gdx.math.Vector3, percent: scala.Float): scala.Unit = {
    val width: scala.Float = spawnWidth + (spawnWidthDiff * spawnWidthValue.getScale(percent))
    val height: scala.Float = spawnHeight + (spawnHeightDiff * spawnHeightValue.getScale(percent))
    val depth: scala.Float = spawnDepth + (spawnDepthDiff * spawnDepthValue.getScale(percent))
    var radiusX: scala.Float = 0.0f
    var radiusY: scala.Float = 0.0f
    var radiusZ: scala.Float = 0.0f
    val minT: scala.Float = 0
    var maxT: scala.Float = com.badlogic.gdx.math.MathUtils.PI2
    if (this.side == com.badlogic.gdx.graphics.g3d.particles.values.PrimitiveSpawnShapeValue.SpawnSide.top) {
      maxT = com.badlogic.gdx.math.MathUtils.PI
    } else {
      if (this.side == com.badlogic.gdx.graphics.g3d.particles.values.PrimitiveSpawnShapeValue.SpawnSide.bottom) {
        maxT = -com.badlogic.gdx.math.MathUtils.PI
      } else ()
    }
    val t: scala.Float = com.badlogic.gdx.math.MathUtils.random(minT, maxT)
    if (edges) {
      if (width == 0) {
        vector.set(0, (height / 2) * com.badlogic.gdx.math.MathUtils.sin(t), (depth / 2) * com.badlogic.gdx.math.MathUtils.cos(t))
        return
      } else ()
      if (height == 0) {
        vector.set((width / 2) * com.badlogic.gdx.math.MathUtils.cos(t), 0, (depth / 2) * com.badlogic.gdx.math.MathUtils.sin(t))
        return
      } else ()
      if (depth == 0) {
        vector.set((width / 2) * com.badlogic.gdx.math.MathUtils.cos(t), (height / 2) * com.badlogic.gdx.math.MathUtils.sin(t), 0)
        return
      } else ()
      radiusX = width / 2
      radiusY = height / 2
      radiusZ = depth / 2
    } else {
      radiusX = com.badlogic.gdx.math.MathUtils.random(width / 2)
      radiusY = com.badlogic.gdx.math.MathUtils.random(height / 2)
      radiusZ = com.badlogic.gdx.math.MathUtils.random(depth / 2)
    }
    val z: scala.Float = com.badlogic.gdx.math.MathUtils.random(-1, 1.0f)
    val r: scala.Float = java.lang.Math.sqrt(1.0f - (z * z)).asInstanceOf[scala.Float].asInstanceOf[scala.Float]
    vector.set((radiusX * r) * com.badlogic.gdx.math.MathUtils.cos(t), (radiusY * r) * com.badlogic.gdx.math.MathUtils.sin(t), radiusZ * z)
  }
  def getSide(): com.badlogic.gdx.graphics.g3d.particles.values.PrimitiveSpawnShapeValue.SpawnSide = {
    return this.side
  }
  def setSide(side: com.badlogic.gdx.graphics.g3d.particles.values.PrimitiveSpawnShapeValue.SpawnSide): scala.Unit = {
    this.side = side
  }
  def load(value: com.badlogic.gdx.graphics.g3d.particles.values.ParticleValue): scala.Unit = {
    super.load(value)
    val shape: EllipseSpawnShapeValue = value.asInstanceOf[EllipseSpawnShapeValue]
    this.side = shape.side
  }
  def copy(): com.badlogic.gdx.graphics.g3d.particles.values.SpawnShapeValue = {
    return new EllipseSpawnShapeValue(this)
  }
  def write(json: com.badlogic.gdx.utils.Json): scala.Unit = {
    super.write(json)
    json.writeValue("side", this.side)
  }
  def read(json: com.badlogic.gdx.utils.Json, jsonData: com.badlogic.gdx.utils.JsonValue): scala.Unit = {
    super.read(json, jsonData)
    this.side = json.readValue("side", classOf[com.badlogic.gdx.graphics.g3d.particles.values.PrimitiveSpawnShapeValue.SpawnSide], jsonData)
  }
}
object EllipseSpawnShapeValue {
  export com.badlogic.gdx.graphics.g3d.particles.values.PrimitiveSpawnShapeValue.*
}