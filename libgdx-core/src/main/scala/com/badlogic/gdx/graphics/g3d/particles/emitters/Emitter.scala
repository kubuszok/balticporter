package com.badlogic.gdx.graphics.g3d.particles.emitters

abstract class Emitter extends com.badlogic.gdx.graphics.g3d.particles.ParticleControllerComponent with com.badlogic.gdx.utils.Json.Serializable {
  var minParticleCount: scala.Int = 0
  var maxParticleCount: scala.Int = 4
  var percent: scala.Float = 0.0f
  def this(regularEmitter: Emitter) = {
    this()
    this.set(regularEmitter)
  }
  @java.lang.Override
  override def init(): scala.Unit = {
    this.controller.particles.size = 0
  }
  @java.lang.Override
  override def `end`(): scala.Unit = {
    this.controller.particles.size = 0
  }
  def isComplete(): scala.Boolean = {
    return this.percent >= 1.0f
  }
  def getMinParticleCount(): scala.Int = {
    return this.minParticleCount
  }
  def setMinParticleCount(minParticleCount: scala.Int): scala.Unit = {
    this.minParticleCount = minParticleCount
  }
  def getMaxParticleCount(): scala.Int = {
    return this.maxParticleCount
  }
  def setMaxParticleCount(maxParticleCount: scala.Int): scala.Unit = {
    this.maxParticleCount = maxParticleCount
  }
  def setParticleCount(aMin: scala.Int, aMax: scala.Int): scala.Unit = {
    this.setMinParticleCount(aMin)
    this.setMaxParticleCount(aMax)
  }
  override def set(emitter: Emitter): scala.Unit = {
    this.minParticleCount = emitter.minParticleCount
    this.maxParticleCount = emitter.maxParticleCount
  }
  @java.lang.Override
  override def write(json: com.badlogic.gdx.utils.Json): scala.Unit = {
    json.writeValue("minParticleCount", this.minParticleCount.asInstanceOf[java.lang.Integer])
    json.writeValue("maxParticleCount", this.maxParticleCount.asInstanceOf[java.lang.Integer])
  }
  @java.lang.Override
  override def read(json: com.badlogic.gdx.utils.Json, jsonData: com.badlogic.gdx.utils.JsonValue): scala.Unit = {
    this.minParticleCount = json.readValue("minParticleCount", classOf[scala.Int], jsonData)
    this.maxParticleCount = json.readValue("maxParticleCount", classOf[scala.Int], jsonData)
  }
}
object Emitter {
  export com.badlogic.gdx.graphics.g3d.particles.ParticleControllerComponent.*
}