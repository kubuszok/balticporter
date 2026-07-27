package com.badlogic.gdx.graphics.g3d.particles.influencers

class DynamicsInfluencer extends com.badlogic.gdx.graphics.g3d.particles.influencers.Influencer {
  var velocities: com.badlogic.gdx.utils.Array[com.badlogic.gdx.graphics.g3d.particles.influencers.DynamicsModifier] = null.asInstanceOf[com.badlogic.gdx.utils.Array[com.badlogic.gdx.graphics.g3d.particles.influencers.DynamicsModifier]]
  private var accellerationChannel: com.badlogic.gdx.graphics.g3d.particles.ParallelArray#FloatChannel = null.asInstanceOf[com.badlogic.gdx.graphics.g3d.particles.ParallelArray#FloatChannel]
  private var positionChannel: com.badlogic.gdx.graphics.g3d.particles.ParallelArray#FloatChannel = null.asInstanceOf[com.badlogic.gdx.graphics.g3d.particles.ParallelArray#FloatChannel]
  private var previousPositionChannel: com.badlogic.gdx.graphics.g3d.particles.ParallelArray#FloatChannel = null.asInstanceOf[com.badlogic.gdx.graphics.g3d.particles.ParallelArray#FloatChannel]
  private var rotationChannel: com.badlogic.gdx.graphics.g3d.particles.ParallelArray#FloatChannel = null.asInstanceOf[com.badlogic.gdx.graphics.g3d.particles.ParallelArray#FloatChannel]
  private var angularVelocityChannel: com.badlogic.gdx.graphics.g3d.particles.ParallelArray#FloatChannel = null.asInstanceOf[com.badlogic.gdx.graphics.g3d.particles.ParallelArray#FloatChannel]
  var hasAcceleration: scala.Boolean = false
  var has2dAngularVelocity: scala.Boolean = false
  var has3dAngularVelocity: scala.Boolean = false
  def this(velocities: scala.Array[com.badlogic.gdx.graphics.g3d.particles.influencers.DynamicsModifier]) = {
    this()
    this.velocities = new com.badlogic.gdx.utils.Array[com.badlogic.gdx.graphics.g3d.particles.influencers.DynamicsModifier](true, velocities.length, ((size: scala.Int) => new scala.Array[com.badlogic.gdx.graphics.g3d.particles.influencers.DynamicsModifier](size)))
    for (value <- velocities) {
      this.velocities.add(value.copy().asInstanceOf[com.badlogic.gdx.graphics.g3d.particles.influencers.DynamicsModifier])
    }
  }
  def this(velocityInfluencer: DynamicsInfluencer) = {
    this(velocityInfluencer.velocities.toArray(((size: scala.Int) => new scala.Array[com.badlogic.gdx.graphics.g3d.particles.influencers.DynamicsModifier](size))))
  }
  this.velocities = new com.badlogic.gdx.utils.Array[com.badlogic.gdx.graphics.g3d.particles.influencers.DynamicsModifier](true, 3, ((size: scala.Int) => new scala.Array[com.badlogic.gdx.graphics.g3d.particles.influencers.DynamicsModifier](size)))
  def allocateChannels(): scala.Unit = {
    { var k: scala.Int = 0; while (k < this.velocities.size) { {
      this.velocities.items(k).allocateChannels()
    }; k = k + 1 } }
    this.accellerationChannel = this.controller.particles.getChannel(com.badlogic.gdx.graphics.g3d.particles.ParticleChannels.Acceleration)
    this.hasAcceleration = this.accellerationChannel != null
    if (this.hasAcceleration) {
      this.positionChannel = this.controller.particles.addChannel(com.badlogic.gdx.graphics.g3d.particles.ParticleChannels.Position)
      this.previousPositionChannel = this.controller.particles.addChannel(com.badlogic.gdx.graphics.g3d.particles.ParticleChannels.PreviousPosition)
    } else ()
    this.angularVelocityChannel = this.controller.particles.getChannel(com.badlogic.gdx.graphics.g3d.particles.ParticleChannels.AngularVelocity2D)
    this.has2dAngularVelocity = this.angularVelocityChannel != null
    if (this.has2dAngularVelocity) {
      this.rotationChannel = this.controller.particles.addChannel(com.badlogic.gdx.graphics.g3d.particles.ParticleChannels.Rotation2D)
      this.has3dAngularVelocity = false
    } else {
      this.angularVelocityChannel = this.controller.particles.getChannel(com.badlogic.gdx.graphics.g3d.particles.ParticleChannels.AngularVelocity3D)
      this.has3dAngularVelocity = this.angularVelocityChannel != null
      if (this.has3dAngularVelocity) {
        this.rotationChannel = this.controller.particles.addChannel(com.badlogic.gdx.graphics.g3d.particles.ParticleChannels.Rotation3D)
      } else ()
    }
  }
  def set(particleController: com.badlogic.gdx.graphics.g3d.particles.ParticleController): scala.Unit = {
    super.set(particleController);
    { var k: scala.Int = 0; while (k < this.velocities.size) { {
      this.velocities.items(k).set(particleController)
    }; k = k + 1 } }
  }
  def init(): scala.Unit = {
    { var k: scala.Int = 0; while (k < this.velocities.size) { {
      this.velocities.items(k).init()
    }; k = k + 1 } }
  }
  def activateParticles(startIndex: scala.Int, count: scala.Int): scala.Unit = {
    if (this.hasAcceleration) {
      { var i: scala.Int = startIndex * this.positionChannel.strideSize; val c: scala.Int = i + (count * this.positionChannel.strideSize); while (i < c) { {
        this.previousPositionChannel.data(i + com.badlogic.gdx.graphics.g3d.particles.ParticleChannels.XOffset) = this.positionChannel.data(i + com.badlogic.gdx.graphics.g3d.particles.ParticleChannels.XOffset)
        this.previousPositionChannel.data(i + com.badlogic.gdx.graphics.g3d.particles.ParticleChannels.YOffset) = this.positionChannel.data(i + com.badlogic.gdx.graphics.g3d.particles.ParticleChannels.YOffset)
        this.previousPositionChannel.data(i + com.badlogic.gdx.graphics.g3d.particles.ParticleChannels.ZOffset) = this.positionChannel.data(i + com.badlogic.gdx.graphics.g3d.particles.ParticleChannels.ZOffset)
      }; i = i + this.positionChannel.strideSize } }
    } else ()
    if (this.has2dAngularVelocity) {
      { var i: scala.Int = startIndex * this.rotationChannel.strideSize; val c: scala.Int = i + (count * this.rotationChannel.strideSize); while (i < c) { {
        this.rotationChannel.data(i + com.badlogic.gdx.graphics.g3d.particles.ParticleChannels.CosineOffset) = 1
        this.rotationChannel.data(i + com.badlogic.gdx.graphics.g3d.particles.ParticleChannels.SineOffset) = 0
      }; i = i + this.rotationChannel.strideSize } }
    } else {
      if (this.has3dAngularVelocity) {
        { var i: scala.Int = startIndex * this.rotationChannel.strideSize; val c: scala.Int = i + (count * this.rotationChannel.strideSize); while (i < c) { {
          this.rotationChannel.data(i + com.badlogic.gdx.graphics.g3d.particles.ParticleChannels.XOffset) = 0
          this.rotationChannel.data(i + com.badlogic.gdx.graphics.g3d.particles.ParticleChannels.YOffset) = 0
          this.rotationChannel.data(i + com.badlogic.gdx.graphics.g3d.particles.ParticleChannels.ZOffset) = 0
          this.rotationChannel.data(i + com.badlogic.gdx.graphics.g3d.particles.ParticleChannels.WOffset) = 1
        }; i = i + this.rotationChannel.strideSize } }
      } else ()
    };
    { var k: scala.Int = 0; while (k < this.velocities.size) { {
      this.velocities.items(k).activateParticles(startIndex, count)
    }; k = k + 1 } }
  }
  def update(): scala.Unit = {
    if (this.hasAcceleration) {
      java.util.Arrays.fill(this.accellerationChannel.data, 0, this.controller.particles.size * this.accellerationChannel.strideSize, 0)
    } else ()
    if (this.has2dAngularVelocity || this.has3dAngularVelocity) {
      java.util.Arrays.fill(this.angularVelocityChannel.data, 0, this.controller.particles.size * this.angularVelocityChannel.strideSize, 0)
    } else ();
    { var k: scala.Int = 0; while (k < this.velocities.size) { {
      this.velocities.items(k).update()
    }; k = k + 1 } }
    if (this.hasAcceleration) {
      { var i: scala.Int = 0; var offset: scala.Int = 0; while (i < this.controller.particles.size) { {
        val x: scala.Float = this.positionChannel.data(offset + com.badlogic.gdx.graphics.g3d.particles.ParticleChannels.XOffset)
        val y: scala.Float = this.positionChannel.data(offset + com.badlogic.gdx.graphics.g3d.particles.ParticleChannels.YOffset)
        val z: scala.Float = this.positionChannel.data(offset + com.badlogic.gdx.graphics.g3d.particles.ParticleChannels.ZOffset)
        this.positionChannel.data(offset + com.badlogic.gdx.graphics.g3d.particles.ParticleChannels.XOffset) = ((2 * x) - this.previousPositionChannel.data(offset + com.badlogic.gdx.graphics.g3d.particles.ParticleChannels.XOffset)) + (this.accellerationChannel.data(offset + com.badlogic.gdx.graphics.g3d.particles.ParticleChannels.XOffset) * this.controller.deltaTimeSqr)
        this.positionChannel.data(offset + com.badlogic.gdx.graphics.g3d.particles.ParticleChannels.YOffset) = ((2 * y) - this.previousPositionChannel.data(offset + com.badlogic.gdx.graphics.g3d.particles.ParticleChannels.YOffset)) + (this.accellerationChannel.data(offset + com.badlogic.gdx.graphics.g3d.particles.ParticleChannels.YOffset) * this.controller.deltaTimeSqr)
        this.positionChannel.data(offset + com.badlogic.gdx.graphics.g3d.particles.ParticleChannels.ZOffset) = ((2 * z) - this.previousPositionChannel.data(offset + com.badlogic.gdx.graphics.g3d.particles.ParticleChannels.ZOffset)) + (this.accellerationChannel.data(offset + com.badlogic.gdx.graphics.g3d.particles.ParticleChannels.ZOffset) * this.controller.deltaTimeSqr)
        this.previousPositionChannel.data(offset + com.badlogic.gdx.graphics.g3d.particles.ParticleChannels.XOffset) = x
        this.previousPositionChannel.data(offset + com.badlogic.gdx.graphics.g3d.particles.ParticleChannels.YOffset) = y
        this.previousPositionChannel.data(offset + com.badlogic.gdx.graphics.g3d.particles.ParticleChannels.ZOffset) = z
      }; i = i + 1; offset = offset + this.positionChannel.strideSize } }
    } else ()
    if (this.has2dAngularVelocity) {
      { var i: scala.Int = 0; var offset: scala.Int = 0; while (i < this.controller.particles.size) { {
        val rotation: scala.Float = this.angularVelocityChannel.data(i) * this.controller.deltaTime
        if (rotation != 0) {
          val cosBeta: scala.Float = com.badlogic.gdx.math.MathUtils.cosDeg(rotation)
          val sinBeta: scala.Float = com.badlogic.gdx.math.MathUtils.sinDeg(rotation)
          val currentCosine: scala.Float = this.rotationChannel.data(offset + com.badlogic.gdx.graphics.g3d.particles.ParticleChannels.CosineOffset)
          val currentSine: scala.Float = this.rotationChannel.data(offset + com.badlogic.gdx.graphics.g3d.particles.ParticleChannels.SineOffset)
          val newCosine: scala.Float = (currentCosine * cosBeta) - (currentSine * sinBeta)
          val newSine: scala.Float = (currentSine * cosBeta) + (currentCosine * sinBeta)
          this.rotationChannel.data(offset + com.badlogic.gdx.graphics.g3d.particles.ParticleChannels.CosineOffset) = newCosine
          this.rotationChannel.data(offset + com.badlogic.gdx.graphics.g3d.particles.ParticleChannels.SineOffset) = newSine
        } else ()
      }; i = i + 1; offset = offset + this.rotationChannel.strideSize } }
    } else {
      if (this.has3dAngularVelocity) {
        { var i: scala.Int = 0; var offset: scala.Int = 0; var angularOffset: scala.Int = 0; while (i < this.controller.particles.size) { {
          val wx: scala.Float = this.angularVelocityChannel.data(angularOffset + com.badlogic.gdx.graphics.g3d.particles.ParticleChannels.XOffset)
          val wy: scala.Float = this.angularVelocityChannel.data(angularOffset + com.badlogic.gdx.graphics.g3d.particles.ParticleChannels.YOffset)
          val wz: scala.Float = this.angularVelocityChannel.data(angularOffset + com.badlogic.gdx.graphics.g3d.particles.ParticleChannels.ZOffset)
          val qx: scala.Float = this.rotationChannel.data(offset + com.badlogic.gdx.graphics.g3d.particles.ParticleChannels.XOffset)
          val qy: scala.Float = this.rotationChannel.data(offset + com.badlogic.gdx.graphics.g3d.particles.ParticleChannels.YOffset)
          val qz: scala.Float = this.rotationChannel.data(offset + com.badlogic.gdx.graphics.g3d.particles.ParticleChannels.ZOffset)
          val qw: scala.Float = this.rotationChannel.data(offset + com.badlogic.gdx.graphics.g3d.particles.ParticleChannels.WOffset)
          com.badlogic.gdx.graphics.g3d.particles.ParticleControllerComponent.TMP_Q.set(wx, wy, wz, 0).mul(qx, qy, qz, qw).mul(0.5f * this.controller.deltaTime).add(qx, qy, qz, qw).nor()
          this.rotationChannel.data(offset + com.badlogic.gdx.graphics.g3d.particles.ParticleChannels.XOffset) = com.badlogic.gdx.graphics.g3d.particles.ParticleControllerComponent.TMP_Q.x
          this.rotationChannel.data(offset + com.badlogic.gdx.graphics.g3d.particles.ParticleChannels.YOffset) = com.badlogic.gdx.graphics.g3d.particles.ParticleControllerComponent.TMP_Q.y
          this.rotationChannel.data(offset + com.badlogic.gdx.graphics.g3d.particles.ParticleChannels.ZOffset) = com.badlogic.gdx.graphics.g3d.particles.ParticleControllerComponent.TMP_Q.z
          this.rotationChannel.data(offset + com.badlogic.gdx.graphics.g3d.particles.ParticleChannels.WOffset) = com.badlogic.gdx.graphics.g3d.particles.ParticleControllerComponent.TMP_Q.w
        }; i = i + 1; offset = offset + this.rotationChannel.strideSize; angularOffset = angularOffset + this.angularVelocityChannel.strideSize } }
      } else ()
    }
  }
  def copy(): DynamicsInfluencer = {
    return new DynamicsInfluencer(this)
  }
  def write(json: com.badlogic.gdx.utils.Json): scala.Unit = {
    json.writeValue("velocities", this.velocities, classOf[com.badlogic.gdx.utils.Array[?]], classOf[com.badlogic.gdx.graphics.g3d.particles.influencers.DynamicsModifier])
  }
  def read(json: com.badlogic.gdx.utils.Json, jsonData: com.badlogic.gdx.utils.JsonValue): scala.Unit = {
    this.velocities.addAll(json.readValue("velocities", classOf[com.badlogic.gdx.utils.Array[?]], classOf[com.badlogic.gdx.graphics.g3d.particles.influencers.DynamicsModifier], jsonData).asInstanceOf[com.badlogic.gdx.utils.Array[? <: com.badlogic.gdx.graphics.g3d.particles.influencers.DynamicsModifier]])
  }
}
object DynamicsInfluencer {
  export com.badlogic.gdx.graphics.g3d.particles.influencers.Influencer.*
}