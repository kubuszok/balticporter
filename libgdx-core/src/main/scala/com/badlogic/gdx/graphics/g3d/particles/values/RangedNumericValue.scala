package com.badlogic.gdx.graphics.g3d.particles.values

class RangedNumericValue extends com.badlogic.gdx.graphics.g3d.particles.values.ParticleValue {
  private var lowMin: scala.Float = 0.0f
  private var lowMax: scala.Float = 0.0f
  def newLowValue(): scala.Float = {
    return this.lowMin + ((this.lowMax - this.lowMin) * com.badlogic.gdx.math.MathUtils.random())
  }
  def setLow(value: scala.Float): scala.Unit = {
    this.lowMin = value
    this.lowMax = value
  }
  def setLow(min: scala.Float, max: scala.Float): scala.Unit = {
    this.lowMin = min
    this.lowMax = max
  }
  def getLowMin(): scala.Float = {
    return this.lowMin
  }
  def setLowMin(lowMin: scala.Float): scala.Unit = {
    this.lowMin = lowMin
  }
  def getLowMax(): scala.Float = {
    return this.lowMax
  }
  def setLowMax(lowMax: scala.Float): scala.Unit = {
    this.lowMax = lowMax
  }
  def load(value: RangedNumericValue): scala.Unit = {
    super.load(value)
    this.lowMax = value.lowMax
    this.lowMin = value.lowMin
  }
  def write(json: com.badlogic.gdx.utils.Json): scala.Unit = {
    super.write(json)
    json.writeValue("lowMin", this.lowMin)
    json.writeValue("lowMax", this.lowMax)
  }
  def read(json: com.badlogic.gdx.utils.Json, jsonData: com.badlogic.gdx.utils.JsonValue): scala.Unit = {
    super.read(json, jsonData)
    this.lowMin = json.readValue("lowMin", classOf[scala.Float], jsonData)
    this.lowMax = json.readValue("lowMax", classOf[scala.Float], jsonData)
  }
}