package com.badlogic.gdx.graphics.g3d.particles.influencers

abstract class SimpleInfluencer extends com.badlogic.gdx.graphics.g3d.particles.influencers.Influencer {
  var value: com.badlogic.gdx.graphics.g3d.particles.values.ScaledNumericValue = null.asInstanceOf[com.badlogic.gdx.graphics.g3d.particles.values.ScaledNumericValue]
  var valueChannel: com.badlogic.gdx.graphics.g3d.particles.ParallelArray#FloatChannel = null.asInstanceOf[com.badlogic.gdx.graphics.g3d.particles.ParallelArray#FloatChannel]
  var interpolationChannel: com.badlogic.gdx.graphics.g3d.particles.ParallelArray#FloatChannel = null.asInstanceOf[com.badlogic.gdx.graphics.g3d.particles.ParallelArray#FloatChannel]
  var lifeChannel: com.badlogic.gdx.graphics.g3d.particles.ParallelArray#FloatChannel = null.asInstanceOf[com.badlogic.gdx.graphics.g3d.particles.ParallelArray#FloatChannel]
  var valueChannelDescriptor: com.badlogic.gdx.graphics.g3d.particles.ParallelArray.ChannelDescriptor = null.asInstanceOf[com.badlogic.gdx.graphics.g3d.particles.ParallelArray.ChannelDescriptor]
  def this(billboardScaleinfluencer: SimpleInfluencer) = {
    this()
    this.set(billboardScaleinfluencer)
  }
  this.value = new com.badlogic.gdx.graphics.g3d.particles.values.ScaledNumericValue()
  this.value.setHigh(1)
  private def set(scaleInfluencer: SimpleInfluencer): scala.Unit = {
    this.value.load(scaleInfluencer.value)
    this.valueChannelDescriptor = scaleInfluencer.valueChannelDescriptor
  }
  def allocateChannels(): scala.Unit = {
    this.valueChannel = this.controller.particles.addChannel(this.valueChannelDescriptor)
    com.badlogic.gdx.graphics.g3d.particles.ParticleChannels.Interpolation.id = this.controller.particleChannels.newId()
    this.interpolationChannel = this.controller.particles.addChannel(com.badlogic.gdx.graphics.g3d.particles.ParticleChannels.Interpolation)
    this.lifeChannel = this.controller.particles.addChannel(com.badlogic.gdx.graphics.g3d.particles.ParticleChannels.Life)
  }
  def activateParticles(startIndex: scala.Int, count: scala.Int): scala.Unit = {
    if (!this.value.isRelative()) {
      { var i: scala.Int = startIndex * this.valueChannel.strideSize; var a: scala.Int = startIndex * this.interpolationChannel.strideSize; val c: scala.Int = i + (count * this.valueChannel.strideSize); while (i < c) { {
        val start: scala.Float = this.value.newLowValue()
        val diff: scala.Float = this.value.newHighValue() - start
        this.interpolationChannel.data(a + com.badlogic.gdx.graphics.g3d.particles.ParticleChannels.InterpolationStartOffset) = start
        this.interpolationChannel.data(a + com.badlogic.gdx.graphics.g3d.particles.ParticleChannels.InterpolationDiffOffset) = diff
        this.valueChannel.data(i) = start + (diff * this.value.getScale(0))
      }; i = i + this.valueChannel.strideSize; a = a + this.interpolationChannel.strideSize } }
    } else {
      { var i: scala.Int = startIndex * this.valueChannel.strideSize; var a: scala.Int = startIndex * this.interpolationChannel.strideSize; val c: scala.Int = i + (count * this.valueChannel.strideSize); while (i < c) { {
        val start: scala.Float = this.value.newLowValue()
        val diff: scala.Float = this.value.newHighValue()
        this.interpolationChannel.data(a + com.badlogic.gdx.graphics.g3d.particles.ParticleChannels.InterpolationStartOffset) = start
        this.interpolationChannel.data(a + com.badlogic.gdx.graphics.g3d.particles.ParticleChannels.InterpolationDiffOffset) = diff
        this.valueChannel.data(i) = start + (diff * this.value.getScale(0))
      }; i = i + this.valueChannel.strideSize; a = a + this.interpolationChannel.strideSize } }
    }
  }
  def update(): scala.Unit = {
    { var i: scala.Int = 0; var a: scala.Int = 0; var l: scala.Int = com.badlogic.gdx.graphics.g3d.particles.ParticleChannels.LifePercentOffset; val c: scala.Int = i + (this.controller.particles.size * this.valueChannel.strideSize); while (i < c) { {
      this.valueChannel.data(i) = this.interpolationChannel.data(a + com.badlogic.gdx.graphics.g3d.particles.ParticleChannels.InterpolationStartOffset) + (this.interpolationChannel.data(a + com.badlogic.gdx.graphics.g3d.particles.ParticleChannels.InterpolationDiffOffset) * this.value.getScale(this.lifeChannel.data(l)))
    }; i = i + this.valueChannel.strideSize; a = a + this.interpolationChannel.strideSize; l = l + this.lifeChannel.strideSize } }
  }
  def write(json: com.badlogic.gdx.utils.Json): scala.Unit = {
    json.writeValue("value", this.value)
  }
  def read(json: com.badlogic.gdx.utils.Json, jsonData: com.badlogic.gdx.utils.JsonValue): scala.Unit = {
    this.value = json.readValue("value", classOf[com.badlogic.gdx.graphics.g3d.particles.values.ScaledNumericValue], jsonData)
  }
}
object SimpleInfluencer {
  export com.badlogic.gdx.graphics.g3d.particles.influencers.Influencer.*
}