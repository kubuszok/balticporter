package com.badlogic.gdx.graphics.g3d.particles.values

class ParticleValue extends com.badlogic.gdx.utils.Json.Serializable {
  var active: scala.Boolean = false
  def this(value: ParticleValue) = {
    this()
    this.active = value.active
  }
  def isActive(): scala.Boolean = {
    return this.active
  }
  def setActive(active: scala.Boolean): scala.Unit = {
    this.active = active
  }
  def load(value: ParticleValue): scala.Unit = {
    this.active = value.active
  }
  @java.lang.Override
  override def write(json: com.badlogic.gdx.utils.Json): scala.Unit = {
    json.writeValue("active", this.active.asInstanceOf[java.lang.Boolean])
  }
  @java.lang.Override
  override def read(json: com.badlogic.gdx.utils.Json, jsonData: com.badlogic.gdx.utils.JsonValue): scala.Unit = {
    this.active = json.readValue[java.lang.Boolean]("active", classOf[java.lang.Boolean], jsonData)
  }
}