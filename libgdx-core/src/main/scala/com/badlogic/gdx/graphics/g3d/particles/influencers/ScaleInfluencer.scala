package com.badlogic.gdx.graphics.g3d.particles.influencers

class ScaleInfluencer extends com.badlogic.gdx.graphics.g3d.particles.influencers.SimpleInfluencer {
  def this(scaleInfluencer: ScaleInfluencer) = {
    this()
  }
  valueChannelDescriptor = com.badlogic.gdx.graphics.g3d.particles.ParticleChannels.Scale
  @java.lang.Override
  override def activateParticles(startIndex: scala.Int, count: scala.Int): scala.Unit = {
    if (value.isRelative()) {
      { var i: scala.Int = startIndex * this.valueChannel.strideSize; var a: scala.Int = startIndex * this.interpolationChannel.strideSize; val c: scala.Int = i + (count * this.valueChannel.strideSize); while (i < c) { {
        val start: scala.Float = value.newLowValue() * this.controller.scale$field.x
        val diff: scala.Float = value.newHighValue() * this.controller.scale$field.x
        this.interpolationChannel.data(a + com.badlogic.gdx.graphics.g3d.particles.ParticleChannels.InterpolationStartOffset) = start
        this.interpolationChannel.data(a + com.badlogic.gdx.graphics.g3d.particles.ParticleChannels.InterpolationDiffOffset) = diff
        this.valueChannel.data(i) = start + (diff * value.getScale(0))
      }; i = i + this.valueChannel.strideSize; a = a + this.interpolationChannel.strideSize } }
    } else {
      { var i: scala.Int = startIndex * this.valueChannel.strideSize; var a: scala.Int = startIndex * this.interpolationChannel.strideSize; val c: scala.Int = i + (count * this.valueChannel.strideSize); while (i < c) { {
        val start: scala.Float = value.newLowValue() * this.controller.scale$field.x
        val diff: scala.Float = (value.newHighValue() * this.controller.scale$field.x) - start
        this.interpolationChannel.data(a + com.badlogic.gdx.graphics.g3d.particles.ParticleChannels.InterpolationStartOffset) = start
        this.interpolationChannel.data(a + com.badlogic.gdx.graphics.g3d.particles.ParticleChannels.InterpolationDiffOffset) = diff
        this.valueChannel.data(i) = start + (diff * value.getScale(0))
      }; i = i + this.valueChannel.strideSize; a = a + this.interpolationChannel.strideSize } }
    }
  }
  @java.lang.Override
  override def copy(): com.badlogic.gdx.graphics.g3d.particles.ParticleControllerComponent = {
    return new ScaleInfluencer(this)
  }
}
object ScaleInfluencer {
  export com.badlogic.gdx.graphics.g3d.particles.influencers.SimpleInfluencer.*
}