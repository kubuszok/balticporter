package com.badlogic.gdx.graphics.g3d.particles.renderers

class PointSpriteRenderer extends com.badlogic.gdx.graphics.g3d.particles.renderers.ParticleControllerRenderer[com.badlogic.gdx.graphics.g3d.particles.renderers.PointSpriteControllerRenderData, com.badlogic.gdx.graphics.g3d.particles.batches.PointSpriteParticleBatch] {
  def this(batch: com.badlogic.gdx.graphics.g3d.particles.batches.PointSpriteParticleBatch) = {
    this()
    this.setBatch(batch)
  }
  def allocateChannels(): scala.Unit = {
    this.renderData.positionChannel = this.controller.particles.addChannel(com.badlogic.gdx.graphics.g3d.particles.ParticleChannels.Position)
    this.renderData.regionChannel = this.controller.particles.addChannel(com.badlogic.gdx.graphics.g3d.particles.ParticleChannels.TextureRegion, com.badlogic.gdx.graphics.g3d.particles.ParticleChannels.TextureRegionInitializer.get())
    this.renderData.colorChannel = this.controller.particles.addChannel(com.badlogic.gdx.graphics.g3d.particles.ParticleChannels.Color, com.badlogic.gdx.graphics.g3d.particles.ParticleChannels.ColorInitializer.get())
    this.renderData.scaleChannel = this.controller.particles.addChannel(com.badlogic.gdx.graphics.g3d.particles.ParticleChannels.Scale, com.badlogic.gdx.graphics.g3d.particles.ParticleChannels.ScaleInitializer.get())
    this.renderData.rotationChannel = this.controller.particles.addChannel(com.badlogic.gdx.graphics.g3d.particles.ParticleChannels.Rotation2D, com.badlogic.gdx.graphics.g3d.particles.ParticleChannels.Rotation2dInitializer.get())
  }
  def isCompatible(batch: com.badlogic.gdx.graphics.g3d.particles.batches.ParticleBatch[?]): scala.Boolean = {
    return batch.isInstanceOf[com.badlogic.gdx.graphics.g3d.particles.batches.PointSpriteParticleBatch]
  }
  def copy(): com.badlogic.gdx.graphics.g3d.particles.ParticleControllerComponent = {
    return new PointSpriteRenderer(batch)
  }
}