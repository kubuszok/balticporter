package com.badlogic.gdx.graphics.g3d.particles.renderers

class ModelInstanceRenderer extends com.badlogic.gdx.graphics.g3d.particles.renderers.ParticleControllerRenderer[com.badlogic.gdx.graphics.g3d.particles.renderers.ModelInstanceControllerRenderData, com.badlogic.gdx.graphics.g3d.particles.batches.ModelInstanceParticleBatch] {
  private var hasColor: scala.Boolean = false
  private var hasScale: scala.Boolean = false
  private var hasRotation: scala.Boolean = false
  def this(batch: com.badlogic.gdx.graphics.g3d.particles.batches.ModelInstanceParticleBatch) = {
    this()
    this.setBatch(batch)
  }
  def allocateChannels(): scala.Unit = {
    this.renderData.positionChannel = this.controller.particles.addChannel(com.badlogic.gdx.graphics.g3d.particles.ParticleChannels.Position)
  }
  def init(): scala.Unit = {
    this.renderData.modelInstanceChannel = this.controller.particles.getChannel(com.badlogic.gdx.graphics.g3d.particles.ParticleChannels.ModelInstance)
    this.renderData.colorChannel = this.controller.particles.getChannel(com.badlogic.gdx.graphics.g3d.particles.ParticleChannels.Color)
    this.renderData.scaleChannel = this.controller.particles.getChannel(com.badlogic.gdx.graphics.g3d.particles.ParticleChannels.Scale)
    this.renderData.rotationChannel = this.controller.particles.getChannel(com.badlogic.gdx.graphics.g3d.particles.ParticleChannels.Rotation3D)
    this.hasColor = this.renderData.colorChannel != null
    this.hasScale = this.renderData.scaleChannel != null
    this.hasRotation = this.renderData.rotationChannel != null
  }
  def update(): scala.Unit = {
    { var i: scala.Int = 0; var positionOffset: scala.Int = 0; val c: scala.Int = this.controller.particles.size; while (i < c) { {
      val instance: com.badlogic.gdx.graphics.g3d.ModelInstance = this.renderData.modelInstanceChannel.data(i)
      val scale: scala.Float = if (this.hasScale) this.renderData.scaleChannel.data(i) else 1
      var qx: scala.Float = 0
      var qy: scala.Float = 0
      var qz: scala.Float = 0
      var qw: scala.Float = 1
      if (this.hasRotation) {
        val rotationOffset: scala.Int = i * this.renderData.rotationChannel.strideSize
        qx = this.renderData.rotationChannel.data(rotationOffset + com.badlogic.gdx.graphics.g3d.particles.ParticleChannels.XOffset)
        qy = this.renderData.rotationChannel.data(rotationOffset + com.badlogic.gdx.graphics.g3d.particles.ParticleChannels.YOffset)
        qz = this.renderData.rotationChannel.data(rotationOffset + com.badlogic.gdx.graphics.g3d.particles.ParticleChannels.ZOffset)
        qw = this.renderData.rotationChannel.data(rotationOffset + com.badlogic.gdx.graphics.g3d.particles.ParticleChannels.WOffset)
      } else ()
      instance.transform.set(this.renderData.positionChannel.data(positionOffset + com.badlogic.gdx.graphics.g3d.particles.ParticleChannels.XOffset), this.renderData.positionChannel.data(positionOffset + com.badlogic.gdx.graphics.g3d.particles.ParticleChannels.YOffset), this.renderData.positionChannel.data(positionOffset + com.badlogic.gdx.graphics.g3d.particles.ParticleChannels.ZOffset), qx, qy, qz, qw, scale, scale, scale)
      if (this.hasColor) {
        val colorOffset: scala.Int = i * this.renderData.colorChannel.strideSize
        val colorAttribute: com.badlogic.gdx.graphics.g3d.attributes.ColorAttribute = instance.materials.get(0).get(com.badlogic.gdx.graphics.g3d.attributes.ColorAttribute.Diffuse).asInstanceOf[com.badlogic.gdx.graphics.g3d.attributes.ColorAttribute]
        val blendingAttribute: com.badlogic.gdx.graphics.g3d.attributes.BlendingAttribute = instance.materials.get(0).get(com.badlogic.gdx.graphics.g3d.attributes.BlendingAttribute.Type).asInstanceOf[com.badlogic.gdx.graphics.g3d.attributes.BlendingAttribute]
        colorAttribute.color.r = this.renderData.colorChannel.data(colorOffset + com.badlogic.gdx.graphics.g3d.particles.ParticleChannels.RedOffset)
        colorAttribute.color.g = this.renderData.colorChannel.data(colorOffset + com.badlogic.gdx.graphics.g3d.particles.ParticleChannels.GreenOffset)
        colorAttribute.color.b = this.renderData.colorChannel.data(colorOffset + com.badlogic.gdx.graphics.g3d.particles.ParticleChannels.BlueOffset)
        if (blendingAttribute != null) {
          blendingAttribute.opacity = this.renderData.colorChannel.data(colorOffset + com.badlogic.gdx.graphics.g3d.particles.ParticleChannels.AlphaOffset)
        } else ()
      } else ()
    }; i = i + 1; positionOffset = positionOffset + this.renderData.positionChannel.strideSize } }
    super.update()
  }
  def copy(): com.badlogic.gdx.graphics.g3d.particles.ParticleControllerComponent = {
    return new ModelInstanceRenderer(batch)
  }
  def isCompatible(batch: com.badlogic.gdx.graphics.g3d.particles.batches.ParticleBatch[?]): scala.Boolean = {
    return batch.isInstanceOf[com.badlogic.gdx.graphics.g3d.particles.batches.ModelInstanceParticleBatch]
  }
}