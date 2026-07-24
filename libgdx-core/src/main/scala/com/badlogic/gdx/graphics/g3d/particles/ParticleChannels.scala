package com.badlogic.gdx.graphics.g3d.particles

class ParticleChannels {
  private var currentId: scala.Int = 0
  def this() = {
    this()
    this.resetIds()
  }
  def newId(): scala.Int = {
    return { this.currentId += 1; this.currentId }
  }
  def resetIds(): scala.Unit = {
    this.currentId = ParticleChannels.currentGlobalId
  }
}
object ParticleChannels {
  private var currentGlobalId: scala.Int = 0
  final val Life: com.badlogic.gdx.graphics.g3d.particles.ParallelArray.ChannelDescriptor = new com.badlogic.gdx.graphics.g3d.particles.ParallelArray.ChannelDescriptor(ParticleChannels.newGlobalId(), scala.Array[scala.Float].<init>, 3)
  final val Position: com.badlogic.gdx.graphics.g3d.particles.ParallelArray.ChannelDescriptor = new com.badlogic.gdx.graphics.g3d.particles.ParallelArray.ChannelDescriptor(ParticleChannels.newGlobalId(), scala.Array[scala.Float].<init>, 3)
  final val PreviousPosition: com.badlogic.gdx.graphics.g3d.particles.ParallelArray.ChannelDescriptor = new com.badlogic.gdx.graphics.g3d.particles.ParallelArray.ChannelDescriptor(ParticleChannels.newGlobalId(), scala.Array[scala.Float].<init>, 3)
  final val Color: com.badlogic.gdx.graphics.g3d.particles.ParallelArray.ChannelDescriptor = new com.badlogic.gdx.graphics.g3d.particles.ParallelArray.ChannelDescriptor(ParticleChannels.newGlobalId(), scala.Array[scala.Float].<init>, 4)
  final val TextureRegion: com.badlogic.gdx.graphics.g3d.particles.ParallelArray.ChannelDescriptor = new com.badlogic.gdx.graphics.g3d.particles.ParallelArray.ChannelDescriptor(ParticleChannels.newGlobalId(), scala.Array[scala.Float].<init>, 6)
  final val Rotation2D: com.badlogic.gdx.graphics.g3d.particles.ParallelArray.ChannelDescriptor = new com.badlogic.gdx.graphics.g3d.particles.ParallelArray.ChannelDescriptor(ParticleChannels.newGlobalId(), scala.Array[scala.Float].<init>, 2)
  final val Rotation3D: com.badlogic.gdx.graphics.g3d.particles.ParallelArray.ChannelDescriptor = new com.badlogic.gdx.graphics.g3d.particles.ParallelArray.ChannelDescriptor(ParticleChannels.newGlobalId(), scala.Array[scala.Float].<init>, 4)
  final val Scale: com.badlogic.gdx.graphics.g3d.particles.ParallelArray.ChannelDescriptor = new com.badlogic.gdx.graphics.g3d.particles.ParallelArray.ChannelDescriptor(ParticleChannels.newGlobalId(), scala.Array[scala.Float].<init>, 1)
  final val ModelInstance: com.badlogic.gdx.graphics.g3d.particles.ParallelArray.ChannelDescriptor = new com.badlogic.gdx.graphics.g3d.particles.ParallelArray.ChannelDescriptor(ParticleChannels.newGlobalId(), scala.Array[com.badlogic.gdx.graphics.g3d.ModelInstance].<init>, 1)
  final val ParticleController: com.badlogic.gdx.graphics.g3d.particles.ParallelArray.ChannelDescriptor = new com.badlogic.gdx.graphics.g3d.particles.ParallelArray.ChannelDescriptor(ParticleChannels.newGlobalId(), scala.Array[com.badlogic.gdx.graphics.g3d.particles.ParticleController].<init>, 1)
  final val Acceleration: com.badlogic.gdx.graphics.g3d.particles.ParallelArray.ChannelDescriptor = new com.badlogic.gdx.graphics.g3d.particles.ParallelArray.ChannelDescriptor(ParticleChannels.newGlobalId(), scala.Array[scala.Float].<init>, 3)
  final val AngularVelocity2D: com.badlogic.gdx.graphics.g3d.particles.ParallelArray.ChannelDescriptor = new com.badlogic.gdx.graphics.g3d.particles.ParallelArray.ChannelDescriptor(ParticleChannels.newGlobalId(), scala.Array[scala.Float].<init>, 1)
  final val AngularVelocity3D: com.badlogic.gdx.graphics.g3d.particles.ParallelArray.ChannelDescriptor = new com.badlogic.gdx.graphics.g3d.particles.ParallelArray.ChannelDescriptor(ParticleChannels.newGlobalId(), scala.Array[scala.Float].<init>, 3)
  final val Interpolation: com.badlogic.gdx.graphics.g3d.particles.ParallelArray.ChannelDescriptor = new com.badlogic.gdx.graphics.g3d.particles.ParallelArray.ChannelDescriptor(-1, scala.Array[scala.Float].<init>, 2)
  final val Interpolation4: com.badlogic.gdx.graphics.g3d.particles.ParallelArray.ChannelDescriptor = new com.badlogic.gdx.graphics.g3d.particles.ParallelArray.ChannelDescriptor(-1, scala.Array[scala.Float].<init>, 4)
  final val Interpolation6: com.badlogic.gdx.graphics.g3d.particles.ParallelArray.ChannelDescriptor = new com.badlogic.gdx.graphics.g3d.particles.ParallelArray.ChannelDescriptor(-1, scala.Array[scala.Float].<init>, 6)
  final val CurrentLifeOffset: scala.Int = 0
  final val TotalLifeOffset: scala.Int = 1
  final val LifePercentOffset: scala.Int = 2
  final val RedOffset: scala.Int = 0
  final val GreenOffset: scala.Int = 1
  final val BlueOffset: scala.Int = 2
  final val AlphaOffset: scala.Int = 3
  final val InterpolationStartOffset: scala.Int = 0
  final val InterpolationDiffOffset: scala.Int = 1
  final val VelocityStrengthStartOffset: scala.Int = 0
  final val VelocityStrengthDiffOffset: scala.Int = 1
  final val VelocityThetaStartOffset: scala.Int = 0
  final val VelocityThetaDiffOffset: scala.Int = 1
  final val VelocityPhiStartOffset: scala.Int = 2
  final val VelocityPhiDiffOffset: scala.Int = 3
  final val XOffset: scala.Int = 0
  final val YOffset: scala.Int = 1
  final val ZOffset: scala.Int = 2
  final val WOffset: scala.Int = 3
  final val UOffset: scala.Int = 0
  final val VOffset: scala.Int = 1
  final val U2Offset: scala.Int = 2
  final val V2Offset: scala.Int = 3
  final val HalfWidthOffset: scala.Int = 4
  final val HalfHeightOffset: scala.Int = 5
  final val CosineOffset: scala.Int = 0
  final val SineOffset: scala.Int = 1
  def newGlobalId(): scala.Int = {
    return { ParticleChannels.currentGlobalId += 1; ParticleChannels.currentGlobalId }
  }
  class TextureRegionInitializer extends com.badlogic.gdx.graphics.g3d.particles.ParallelArray.ChannelInitializer[com.badlogic.gdx.graphics.g3d.particles.ParallelArray#FloatChannel] {
    def init(channel: com.badlogic.gdx.graphics.g3d.particles.ParallelArray#FloatChannel): scala.Unit = {
      { var i: scala.Int = 0; val c: scala.Int = channel.data.length; while (i < c) { {
        channel.data(i + ParticleChannels.UOffset) = 0
        channel.data(i + ParticleChannels.VOffset) = 0
        channel.data(i + ParticleChannels.U2Offset) = 1
        channel.data(i + ParticleChannels.V2Offset) = 1
        channel.data(i + ParticleChannels.HalfWidthOffset) = 0.5f
        channel.data(i + ParticleChannels.HalfHeightOffset) = 0.5f
      }; i = i + channel.strideSize } }
    }
  }
  object TextureRegionInitializer {
    private var instance: com.badlogic.gdx.graphics.g3d.particles.ParticleChannels.TextureRegionInitializer = null.asInstanceOf[com.badlogic.gdx.graphics.g3d.particles.ParticleChannels.TextureRegionInitializer]
    def get(): com.badlogic.gdx.graphics.g3d.particles.ParticleChannels.TextureRegionInitializer = {
      if (com.badlogic.gdx.graphics.g3d.particles.ParticleChannels.TextureRegionInitializer.instance == null) {
        com.badlogic.gdx.graphics.g3d.particles.ParticleChannels.TextureRegionInitializer.instance = new com.badlogic.gdx.graphics.g3d.particles.ParticleChannels.TextureRegionInitializer()
      } else ()
      return com.badlogic.gdx.graphics.g3d.particles.ParticleChannels.TextureRegionInitializer.instance
    }
  }
  class ColorInitializer extends com.badlogic.gdx.graphics.g3d.particles.ParallelArray.ChannelInitializer[com.badlogic.gdx.graphics.g3d.particles.ParallelArray#FloatChannel] {
    def init(channel: com.badlogic.gdx.graphics.g3d.particles.ParallelArray#FloatChannel): scala.Unit = {
      java.util.Arrays.fill(channel.data, 0, channel.data.length, 1)
    }
  }
  object ColorInitializer {
    private var instance: com.badlogic.gdx.graphics.g3d.particles.ParticleChannels.ColorInitializer = null.asInstanceOf[com.badlogic.gdx.graphics.g3d.particles.ParticleChannels.ColorInitializer]
    def get(): com.badlogic.gdx.graphics.g3d.particles.ParticleChannels.ColorInitializer = {
      if (com.badlogic.gdx.graphics.g3d.particles.ParticleChannels.ColorInitializer.instance == null) {
        com.badlogic.gdx.graphics.g3d.particles.ParticleChannels.ColorInitializer.instance = new com.badlogic.gdx.graphics.g3d.particles.ParticleChannels.ColorInitializer()
      } else ()
      return com.badlogic.gdx.graphics.g3d.particles.ParticleChannels.ColorInitializer.instance
    }
  }
  class ScaleInitializer extends com.badlogic.gdx.graphics.g3d.particles.ParallelArray.ChannelInitializer[com.badlogic.gdx.graphics.g3d.particles.ParallelArray#FloatChannel] {
    def init(channel: com.badlogic.gdx.graphics.g3d.particles.ParallelArray#FloatChannel): scala.Unit = {
      java.util.Arrays.fill(channel.data, 0, channel.data.length, 1)
    }
  }
  object ScaleInitializer {
    private var instance: com.badlogic.gdx.graphics.g3d.particles.ParticleChannels.ScaleInitializer = null.asInstanceOf[com.badlogic.gdx.graphics.g3d.particles.ParticleChannels.ScaleInitializer]
    def get(): com.badlogic.gdx.graphics.g3d.particles.ParticleChannels.ScaleInitializer = {
      if (com.badlogic.gdx.graphics.g3d.particles.ParticleChannels.ScaleInitializer.instance == null) {
        com.badlogic.gdx.graphics.g3d.particles.ParticleChannels.ScaleInitializer.instance = new com.badlogic.gdx.graphics.g3d.particles.ParticleChannels.ScaleInitializer()
      } else ()
      return com.badlogic.gdx.graphics.g3d.particles.ParticleChannels.ScaleInitializer.instance
    }
  }
  class Rotation2dInitializer extends com.badlogic.gdx.graphics.g3d.particles.ParallelArray.ChannelInitializer[com.badlogic.gdx.graphics.g3d.particles.ParallelArray#FloatChannel] {
    def init(channel: com.badlogic.gdx.graphics.g3d.particles.ParallelArray#FloatChannel): scala.Unit = {
      { var i: scala.Int = 0; val c: scala.Int = channel.data.length; while (i < c) { {
        channel.data(i + ParticleChannels.CosineOffset) = 1
        channel.data(i + ParticleChannels.SineOffset) = 0
      }; i = i + channel.strideSize } }
    }
  }
  object Rotation2dInitializer {
    private var instance: com.badlogic.gdx.graphics.g3d.particles.ParticleChannels.Rotation2dInitializer = null.asInstanceOf[com.badlogic.gdx.graphics.g3d.particles.ParticleChannels.Rotation2dInitializer]
    def get(): com.badlogic.gdx.graphics.g3d.particles.ParticleChannels.Rotation2dInitializer = {
      if (com.badlogic.gdx.graphics.g3d.particles.ParticleChannels.Rotation2dInitializer.instance == null) {
        com.badlogic.gdx.graphics.g3d.particles.ParticleChannels.Rotation2dInitializer.instance = new com.badlogic.gdx.graphics.g3d.particles.ParticleChannels.Rotation2dInitializer()
      } else ()
      return com.badlogic.gdx.graphics.g3d.particles.ParticleChannels.Rotation2dInitializer.instance
    }
  }
  class Rotation3dInitializer extends com.badlogic.gdx.graphics.g3d.particles.ParallelArray.ChannelInitializer[com.badlogic.gdx.graphics.g3d.particles.ParallelArray#FloatChannel] {
    def init(channel: com.badlogic.gdx.graphics.g3d.particles.ParallelArray#FloatChannel): scala.Unit = {
      { var i: scala.Int = 0; val c: scala.Int = channel.data.length; while (i < c) { {
        channel.data(i + ParticleChannels.XOffset) = {
          channel.data(i + ParticleChannels.YOffset) = {
            channel.data(i + ParticleChannels.ZOffset) = 0
            channel.data(i + ParticleChannels.ZOffset)
          }
          channel.data(i + ParticleChannels.YOffset)
        }
        channel.data(i + ParticleChannels.WOffset) = 1
      }; i = i + channel.strideSize } }
    }
  }
  object Rotation3dInitializer {
    private var instance: com.badlogic.gdx.graphics.g3d.particles.ParticleChannels.Rotation3dInitializer = null.asInstanceOf[com.badlogic.gdx.graphics.g3d.particles.ParticleChannels.Rotation3dInitializer]
    def get(): com.badlogic.gdx.graphics.g3d.particles.ParticleChannels.Rotation3dInitializer = {
      if (com.badlogic.gdx.graphics.g3d.particles.ParticleChannels.Rotation3dInitializer.instance == null) {
        com.badlogic.gdx.graphics.g3d.particles.ParticleChannels.Rotation3dInitializer.instance = new com.badlogic.gdx.graphics.g3d.particles.ParticleChannels.Rotation3dInitializer()
      } else ()
      return com.badlogic.gdx.graphics.g3d.particles.ParticleChannels.Rotation3dInitializer.instance
    }
  }
}