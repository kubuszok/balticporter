package com.badlogic.gdx.graphics.g3d.particles.influencers

class ParticleControllerFinalizerInfluencer extends com.badlogic.gdx.graphics.g3d.particles.influencers.Influencer {
  var positionChannel: com.badlogic.gdx.graphics.g3d.particles.ParallelArray#FloatChannel = null.asInstanceOf[com.badlogic.gdx.graphics.g3d.particles.ParallelArray#FloatChannel]
  var scaleChannel: com.badlogic.gdx.graphics.g3d.particles.ParallelArray#FloatChannel = null.asInstanceOf[com.badlogic.gdx.graphics.g3d.particles.ParallelArray#FloatChannel]
  var rotationChannel: com.badlogic.gdx.graphics.g3d.particles.ParallelArray#FloatChannel = null.asInstanceOf[com.badlogic.gdx.graphics.g3d.particles.ParallelArray#FloatChannel]
  var controllerChannel: com.badlogic.gdx.graphics.g3d.particles.ParallelArray#ObjectChannel[com.badlogic.gdx.graphics.g3d.particles.ParticleController] = null.asInstanceOf[com.badlogic.gdx.graphics.g3d.particles.ParallelArray#ObjectChannel[com.badlogic.gdx.graphics.g3d.particles.ParticleController]]
  var hasScale: scala.Boolean = false
  var hasRotation: scala.Boolean = false
  def init(): scala.Unit = {
    this.controllerChannel = this.controller.particles.getChannel(com.badlogic.gdx.graphics.g3d.particles.ParticleChannels.ParticleController)
    if (this.controllerChannel == null) {
      throw new com.badlogic.gdx.utils.GdxRuntimeException("ParticleController channel not found, specify an influencer which will allocate it please.")
    } else ()
    this.scaleChannel = this.controller.particles.getChannel(com.badlogic.gdx.graphics.g3d.particles.ParticleChannels.Scale)
    this.rotationChannel = this.controller.particles.getChannel(com.badlogic.gdx.graphics.g3d.particles.ParticleChannels.Rotation3D)
    this.hasScale = this.scaleChannel != null
    this.hasRotation = this.rotationChannel != null
  }
  def allocateChannels(): scala.Unit = {
    this.positionChannel = this.controller.particles.addChannel(com.badlogic.gdx.graphics.g3d.particles.ParticleChannels.Position)
  }
  def update(): scala.Unit = {
    { var i: scala.Int = 0; var positionOffset: scala.Int = 0; val c: scala.Int = this.controller.particles.size; while (i < c) { {
      val particleController: com.badlogic.gdx.graphics.g3d.particles.ParticleController = this.controllerChannel.data(i)
      val scale: scala.Float = if (this.hasScale) this.scaleChannel.data(i) else 1
      var qx: scala.Float = 0
      var qy: scala.Float = 0
      var qz: scala.Float = 0
      var qw: scala.Float = 1
      if (this.hasRotation) {
        val rotationOffset: scala.Int = i * this.rotationChannel.strideSize
        qx = this.rotationChannel.data(rotationOffset + com.badlogic.gdx.graphics.g3d.particles.ParticleChannels.XOffset)
        qy = this.rotationChannel.data(rotationOffset + com.badlogic.gdx.graphics.g3d.particles.ParticleChannels.YOffset)
        qz = this.rotationChannel.data(rotationOffset + com.badlogic.gdx.graphics.g3d.particles.ParticleChannels.ZOffset)
        qw = this.rotationChannel.data(rotationOffset + com.badlogic.gdx.graphics.g3d.particles.ParticleChannels.WOffset)
      } else ()
      particleController.setTransform(this.positionChannel.data(positionOffset + com.badlogic.gdx.graphics.g3d.particles.ParticleChannels.XOffset), this.positionChannel.data(positionOffset + com.badlogic.gdx.graphics.g3d.particles.ParticleChannels.YOffset), this.positionChannel.data(positionOffset + com.badlogic.gdx.graphics.g3d.particles.ParticleChannels.ZOffset), qx, qy, qz, qw, scale)
      particleController.update()
    }; i = i + 1; positionOffset = positionOffset + this.positionChannel.strideSize } }
  }
  def copy(): ParticleControllerFinalizerInfluencer = {
    return new ParticleControllerFinalizerInfluencer()
  }
}