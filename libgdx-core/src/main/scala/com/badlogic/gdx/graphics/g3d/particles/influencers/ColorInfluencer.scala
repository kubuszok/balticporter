package com.badlogic.gdx.graphics.g3d.particles.influencers

abstract class ColorInfluencer extends com.badlogic.gdx.graphics.g3d.particles.influencers.Influencer {
  var colorChannel: com.badlogic.gdx.graphics.g3d.particles.ParallelArray#FloatChannel = null.asInstanceOf[com.badlogic.gdx.graphics.g3d.particles.ParallelArray#FloatChannel]
  @java.lang.Override
  override def allocateChannels(): scala.Unit = {
    this.colorChannel = this.controller.particles.addChannel(com.badlogic.gdx.graphics.g3d.particles.ParticleChannels.Color)
  }
}
object ColorInfluencer {
  export com.badlogic.gdx.graphics.g3d.particles.influencers.Influencer.{Random => _, Single => _, *}
  class Random extends ColorInfluencer {
    var colorChannel: com.badlogic.gdx.graphics.g3d.particles.ParallelArray#FloatChannel = null.asInstanceOf[com.badlogic.gdx.graphics.g3d.particles.ParallelArray#FloatChannel]
    @java.lang.Override
    override def allocateChannels(): scala.Unit = {
      this.colorChannel = this.controller.particles.addChannel(com.badlogic.gdx.graphics.g3d.particles.ParticleChannels.Color)
    }
    @java.lang.Override
    override def activateParticles(startIndex: scala.Int, count: scala.Int): scala.Unit = {
      { var i: scala.Int = startIndex * this.colorChannel.strideSize; val c: scala.Int = i + (count * this.colorChannel.strideSize); while (i < c) { {
        this.colorChannel.data(i + com.badlogic.gdx.graphics.g3d.particles.ParticleChannels.RedOffset) = com.badlogic.gdx.math.MathUtils.random()
        this.colorChannel.data(i + com.badlogic.gdx.graphics.g3d.particles.ParticleChannels.GreenOffset) = com.badlogic.gdx.math.MathUtils.random()
        this.colorChannel.data(i + com.badlogic.gdx.graphics.g3d.particles.ParticleChannels.BlueOffset) = com.badlogic.gdx.math.MathUtils.random()
        this.colorChannel.data(i + com.badlogic.gdx.graphics.g3d.particles.ParticleChannels.AlphaOffset) = com.badlogic.gdx.math.MathUtils.random()
      }; i = i + this.colorChannel.strideSize } }
    }
    @java.lang.Override
    override def copy(): com.badlogic.gdx.graphics.g3d.particles.influencers.ColorInfluencer.Random = {
      return new com.badlogic.gdx.graphics.g3d.particles.influencers.ColorInfluencer.Random()
    }
  }
  object Random {
    export ColorInfluencer.*
  }
  class Single extends ColorInfluencer {
    var alphaInterpolationChannel: com.badlogic.gdx.graphics.g3d.particles.ParallelArray#FloatChannel = null.asInstanceOf[com.badlogic.gdx.graphics.g3d.particles.ParallelArray#FloatChannel]
    var lifeChannel: com.badlogic.gdx.graphics.g3d.particles.ParallelArray#FloatChannel = null.asInstanceOf[com.badlogic.gdx.graphics.g3d.particles.ParallelArray#FloatChannel]
    var alphaValue: com.badlogic.gdx.graphics.g3d.particles.values.ScaledNumericValue = null.asInstanceOf[com.badlogic.gdx.graphics.g3d.particles.values.ScaledNumericValue]
    var colorValue: com.badlogic.gdx.graphics.g3d.particles.values.GradientColorValue = null.asInstanceOf[com.badlogic.gdx.graphics.g3d.particles.values.GradientColorValue]
    def this(billboardColorInfluencer: com.badlogic.gdx.graphics.g3d.particles.influencers.ColorInfluencer.Single) = {
      this()
      this.set(billboardColorInfluencer)
    }
    this.colorValue = new com.badlogic.gdx.graphics.g3d.particles.values.GradientColorValue()
    this.alphaValue = new com.badlogic.gdx.graphics.g3d.particles.values.ScaledNumericValue()
    this.alphaValue.setHigh(1)
    override def set(colorInfluencer: com.badlogic.gdx.graphics.g3d.particles.influencers.ColorInfluencer.Single): scala.Unit = {
      this.colorValue.load(colorInfluencer.colorValue)
      this.alphaValue.load(colorInfluencer.alphaValue)
    }
    @java.lang.Override
    override def allocateChannels(): scala.Unit = {
      super.allocateChannels()
      com.badlogic.gdx.graphics.g3d.particles.ParticleChannels.Interpolation.id = this.controller.particleChannels.newId()
      this.alphaInterpolationChannel = this.controller.particles.addChannel(com.badlogic.gdx.graphics.g3d.particles.ParticleChannels.Interpolation)
      this.lifeChannel = this.controller.particles.addChannel(com.badlogic.gdx.graphics.g3d.particles.ParticleChannels.Life)
    }
    @java.lang.Override
    override def activateParticles(startIndex: scala.Int, count: scala.Int): scala.Unit = {
      { var i: scala.Int = startIndex * this.colorChannel.strideSize; var a: scala.Int = startIndex * this.alphaInterpolationChannel.strideSize; var l: scala.Int = (startIndex * this.lifeChannel.strideSize) + com.badlogic.gdx.graphics.g3d.particles.ParticleChannels.LifePercentOffset; val c: scala.Int = i + (count * this.colorChannel.strideSize); while (i < c) { {
        val alphaStart: scala.Float = this.alphaValue.newLowValue()
        val alphaDiff: scala.Float = this.alphaValue.newHighValue() - alphaStart
        this.colorValue.getColor(0, this.colorChannel.data, i)
        this.colorChannel.data(i + com.badlogic.gdx.graphics.g3d.particles.ParticleChannels.AlphaOffset) = alphaStart + (alphaDiff * this.alphaValue.getScale(this.lifeChannel.data(l)))
        this.alphaInterpolationChannel.data(a + com.badlogic.gdx.graphics.g3d.particles.ParticleChannels.InterpolationStartOffset) = alphaStart
        this.alphaInterpolationChannel.data(a + com.badlogic.gdx.graphics.g3d.particles.ParticleChannels.InterpolationDiffOffset) = alphaDiff
      }; i = i + this.colorChannel.strideSize; a = a + this.alphaInterpolationChannel.strideSize; l = l + this.lifeChannel.strideSize } }
    }
    @java.lang.Override
    override def update(): scala.Unit = {
      { var i: scala.Int = 0; var a: scala.Int = 0; var l: scala.Int = com.badlogic.gdx.graphics.g3d.particles.ParticleChannels.LifePercentOffset; val c: scala.Int = i + (this.controller.particles.size * this.colorChannel.strideSize); while (i < c) { {
        val lifePercent: scala.Float = this.lifeChannel.data(l)
        this.colorValue.getColor(lifePercent, this.colorChannel.data, i)
        this.colorChannel.data(i + com.badlogic.gdx.graphics.g3d.particles.ParticleChannels.AlphaOffset) = this.alphaInterpolationChannel.data(a + com.badlogic.gdx.graphics.g3d.particles.ParticleChannels.InterpolationStartOffset) + (this.alphaInterpolationChannel.data(a + com.badlogic.gdx.graphics.g3d.particles.ParticleChannels.InterpolationDiffOffset) * this.alphaValue.getScale(lifePercent))
      }; i = i + this.colorChannel.strideSize; a = a + this.alphaInterpolationChannel.strideSize; l = l + this.lifeChannel.strideSize } }
    }
    @java.lang.Override
    override def copy(): com.badlogic.gdx.graphics.g3d.particles.influencers.ColorInfluencer.Single = {
      return new com.badlogic.gdx.graphics.g3d.particles.influencers.ColorInfluencer.Single(this)
    }
    @java.lang.Override
    override def write(json: com.badlogic.gdx.utils.Json): scala.Unit = {
      json.writeValue("alpha", this.alphaValue)
      json.writeValue("color", this.colorValue)
    }
    @java.lang.Override
    override def read(json: com.badlogic.gdx.utils.Json, jsonData: com.badlogic.gdx.utils.JsonValue): scala.Unit = {
      this.alphaValue = json.readValue("alpha", classOf[com.badlogic.gdx.graphics.g3d.particles.values.ScaledNumericValue], jsonData)
      this.colorValue = json.readValue("color", classOf[com.badlogic.gdx.graphics.g3d.particles.values.GradientColorValue], jsonData)
    }
  }
  object Single {
    export ColorInfluencer.*
  }
}