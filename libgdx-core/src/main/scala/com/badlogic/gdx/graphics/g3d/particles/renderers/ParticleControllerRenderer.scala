package com.badlogic.gdx.graphics.g3d.particles.renderers

abstract class ParticleControllerRenderer[D <: com.badlogic.gdx.graphics.g3d.particles.renderers.ParticleControllerRenderData, T <: com.badlogic.gdx.graphics.g3d.particles.batches.ParticleBatch[D]] extends com.badlogic.gdx.graphics.g3d.particles.ParticleControllerComponent {
  var batch: T = null.asInstanceOf[T]
  var renderData: D = null.asInstanceOf[D]
  def this(renderData: D) = {
    this()
    this.renderData = renderData
  }
  def update(): scala.Unit = {
    this.batch.draw(this.renderData)
  }
  def setBatch(batch: com.badlogic.gdx.graphics.g3d.particles.batches.ParticleBatch[?]): scala.Boolean = {
    if (this.isCompatible(batch)) {
      this.batch = batch.asInstanceOf[T]
      return true
    } else ()
    return false
  }
  def isCompatible(batch: com.badlogic.gdx.graphics.g3d.particles.batches.ParticleBatch[?]): scala.Boolean
  def set(particleController: com.badlogic.gdx.graphics.g3d.particles.ParticleController): scala.Unit = {
    super.set(particleController)
    if (this.renderData != null) {
      this.renderData.controller = controller
    } else ()
  }
}