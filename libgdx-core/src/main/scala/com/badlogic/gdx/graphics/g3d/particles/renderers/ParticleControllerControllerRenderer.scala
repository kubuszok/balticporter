package com.badlogic.gdx.graphics.g3d.particles.renderers

class ParticleControllerControllerRenderer extends com.badlogic.gdx.graphics.g3d.particles.renderers.ParticleControllerRenderer[com.badlogic.gdx.graphics.g3d.particles.renderers.ParticleControllerRenderData, com.badlogic.gdx.graphics.g3d.particles.batches.ParticleBatch[com.badlogic.gdx.graphics.g3d.particles.renderers.ParticleControllerRenderData]] {
  var controllerChannel: com.badlogic.gdx.graphics.g3d.particles.ParallelArray#ObjectChannel[com.badlogic.gdx.graphics.g3d.particles.ParticleController] = null.asInstanceOf[com.badlogic.gdx.graphics.g3d.particles.ParallelArray#ObjectChannel[com.badlogic.gdx.graphics.g3d.particles.ParticleController]]
  @java.lang.Override
  override def init(): scala.Unit = {
    this.controllerChannel = this.controller.particles.getChannel(com.badlogic.gdx.graphics.g3d.particles.ParticleChannels.ParticleController)
    if (this.controllerChannel == null) {
      throw new com.badlogic.gdx.utils.GdxRuntimeException("ParticleController channel not found, specify an influencer which will allocate it please.")
    } else ()
  }
  @java.lang.Override
  override def update(): scala.Unit = {
    { var i: scala.Int = 0; val c: scala.Int = this.controller.particles.size; while (i < c) { {
      this.controllerChannel.data(i).draw()
    }; i = i + 1 } }
  }
  @java.lang.Override
  override def copy(): com.badlogic.gdx.graphics.g3d.particles.ParticleControllerComponent = {
    return new ParticleControllerControllerRenderer()
  }
  @java.lang.Override
  override def isCompatible(batch: com.badlogic.gdx.graphics.g3d.particles.batches.ParticleBatch[?]): scala.Boolean = {
    return false
  }
}
object ParticleControllerControllerRenderer {
  export com.badlogic.gdx.graphics.g3d.particles.renderers.ParticleControllerRenderer.*
}