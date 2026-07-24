package com.badlogic.gdx.graphics.g3d.particles.values

abstract class SpawnShapeValue extends com.badlogic.gdx.graphics.g3d.particles.values.ParticleValue with com.badlogic.gdx.graphics.g3d.particles.ResourceData.Configurable[scala.AnyRef] with com.badlogic.gdx.utils.Json.Serializable {
  var xOffsetValue: com.badlogic.gdx.graphics.g3d.particles.values.RangedNumericValue = null.asInstanceOf[com.badlogic.gdx.graphics.g3d.particles.values.RangedNumericValue]
  var yOffsetValue: com.badlogic.gdx.graphics.g3d.particles.values.RangedNumericValue = null.asInstanceOf[com.badlogic.gdx.graphics.g3d.particles.values.RangedNumericValue]
  var zOffsetValue: com.badlogic.gdx.graphics.g3d.particles.values.RangedNumericValue = null.asInstanceOf[com.badlogic.gdx.graphics.g3d.particles.values.RangedNumericValue]
  def this(spawnShapeValue: SpawnShapeValue) = {
    this()
  }
  this.xOffsetValue = new com.badlogic.gdx.graphics.g3d.particles.values.RangedNumericValue()
  this.yOffsetValue = new com.badlogic.gdx.graphics.g3d.particles.values.RangedNumericValue()
  this.zOffsetValue = new com.badlogic.gdx.graphics.g3d.particles.values.RangedNumericValue()
  def spawnAux(vector: com.badlogic.gdx.math.Vector3, percent: scala.Float): scala.Unit
  final def spawn(vector: com.badlogic.gdx.math.Vector3, percent: scala.Float): com.badlogic.gdx.math.Vector3 = {
    this.spawnAux(vector, percent)
    if (this.xOffsetValue.active) {
      vector.x = vector.x + this.xOffsetValue.newLowValue()
    } else ()
    if (this.yOffsetValue.active) {
      vector.y = vector.y + this.yOffsetValue.newLowValue()
    } else ()
    if (this.zOffsetValue.active) {
      vector.z = vector.z + this.zOffsetValue.newLowValue()
    } else ()
    return vector
  }
  def init(): scala.Unit = {
    ()
  }
  def start(): scala.Unit = {
    ()
  }
  def load(value: com.badlogic.gdx.graphics.g3d.particles.values.ParticleValue): scala.Unit = {
    super.load(value)
    val shape: SpawnShapeValue = value.asInstanceOf[SpawnShapeValue]
    this.xOffsetValue.load(shape.xOffsetValue)
    this.yOffsetValue.load(shape.yOffsetValue)
    this.zOffsetValue.load(shape.zOffsetValue)
  }
  def copy(): SpawnShapeValue
  def write(json: com.badlogic.gdx.utils.Json): scala.Unit = {
    super.write(json)
    json.writeValue("xOffsetValue", this.xOffsetValue)
    json.writeValue("yOffsetValue", this.yOffsetValue)
    json.writeValue("zOffsetValue", this.zOffsetValue)
  }
  def read(json: com.badlogic.gdx.utils.Json, jsonData: com.badlogic.gdx.utils.JsonValue): scala.Unit = {
    super.read(json, jsonData)
    this.xOffsetValue = json.readValue("xOffsetValue", classOf[com.badlogic.gdx.graphics.g3d.particles.values.RangedNumericValue], jsonData)
    this.yOffsetValue = json.readValue("yOffsetValue", classOf[com.badlogic.gdx.graphics.g3d.particles.values.RangedNumericValue], jsonData)
    this.zOffsetValue = json.readValue("zOffsetValue", classOf[com.badlogic.gdx.graphics.g3d.particles.values.RangedNumericValue], jsonData)
  }
  def save(manager: com.badlogic.gdx.assets.AssetManager, data: com.badlogic.gdx.graphics.g3d.particles.ResourceData[?]): scala.Unit = {
    ()
  }
  def load(manager: com.badlogic.gdx.assets.AssetManager, data: com.badlogic.gdx.graphics.g3d.particles.ResourceData[?]): scala.Unit = {
    ()
  }
}