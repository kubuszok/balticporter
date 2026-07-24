package com.badlogic.gdx.graphics.g3d.particles.values

class NumericValue extends com.badlogic.gdx.graphics.g3d.particles.values.ParticleValue {
  private var value: scala.Float = 0.0f
  def getValue(): scala.Float = {
    return this.value
  }
  def setValue(value: scala.Float): scala.Unit = {
    this.value = value
  }
  def load(value: NumericValue): scala.Unit = {
    super.load(value)
    this.value = value.value
  }
  def write(json: com.badlogic.gdx.utils.Json): scala.Unit = {
    super.write(json)
    json.writeValue("value", this.value)
  }
  def read(json: com.badlogic.gdx.utils.Json, jsonData: com.badlogic.gdx.utils.JsonValue): scala.Unit = {
    super.read(json, jsonData)
    this.value = json.readValue("value", classOf[java.lang.Class], jsonData)
  }
}