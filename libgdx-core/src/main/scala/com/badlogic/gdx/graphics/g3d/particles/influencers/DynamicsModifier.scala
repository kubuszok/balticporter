package com.badlogic.gdx.graphics.g3d.particles.influencers

abstract class DynamicsModifier extends com.badlogic.gdx.graphics.g3d.particles.influencers.Influencer {
  var isGlobal: scala.Boolean = false
  var lifeChannel: com.badlogic.gdx.graphics.g3d.particles.ParallelArray#FloatChannel = null.asInstanceOf[com.badlogic.gdx.graphics.g3d.particles.ParallelArray#FloatChannel]
  def this(modifier: DynamicsModifier) = {
    this()
    this.isGlobal = modifier.isGlobal
  }
  @java.lang.Override
  override def allocateChannels(): scala.Unit = {
    this.lifeChannel = this.controller.particles.addChannel(com.badlogic.gdx.graphics.g3d.particles.ParticleChannels.Life)
  }
  @java.lang.Override
  override def write(json: com.badlogic.gdx.utils.Json): scala.Unit = {
    super.write(json)
    json.writeValue("isGlobal", this.isGlobal.asInstanceOf[java.lang.Boolean])
  }
  @java.lang.Override
  override def read(json: com.badlogic.gdx.utils.Json, jsonData: com.badlogic.gdx.utils.JsonValue): scala.Unit = {
    super.read(json, jsonData)
    this.isGlobal = json.readValue("isGlobal", classOf[scala.Boolean], jsonData)
  }
}
object DynamicsModifier {
  export com.badlogic.gdx.graphics.g3d.particles.influencers.Influencer.{Angular => _, BrownianAcceleration => _, CentripetalAcceleration => _, FaceDirection => _, PolarAcceleration => _, Rotational2D => _, Rotational3D => _, Strength => _, TMP_Q => _, TMP_V1 => _, TMP_V2 => _, TMP_V3 => _, TangentialAcceleration => _, *}
  final val TMP_V1: com.badlogic.gdx.math.Vector3 = new com.badlogic.gdx.math.Vector3()
  final val TMP_V2: com.badlogic.gdx.math.Vector3 = new com.badlogic.gdx.math.Vector3()
  final val TMP_V3: com.badlogic.gdx.math.Vector3 = new com.badlogic.gdx.math.Vector3()
  final val TMP_Q: com.badlogic.gdx.math.Quaternion = new com.badlogic.gdx.math.Quaternion()
  class FaceDirection extends DynamicsModifier {
    var rotationChannel: com.badlogic.gdx.graphics.g3d.particles.ParallelArray#FloatChannel = null.asInstanceOf[com.badlogic.gdx.graphics.g3d.particles.ParallelArray#FloatChannel]
    var accellerationChannel: com.badlogic.gdx.graphics.g3d.particles.ParallelArray#FloatChannel = null.asInstanceOf[com.badlogic.gdx.graphics.g3d.particles.ParallelArray#FloatChannel]
    def this(rotation: com.badlogic.gdx.graphics.g3d.particles.influencers.DynamicsModifier.FaceDirection) = {
      this()
      this.isGlobal = rotation.isGlobal
    }
    @java.lang.Override
    override def allocateChannels(): scala.Unit = {
      this.rotationChannel = this.controller.particles.addChannel(com.badlogic.gdx.graphics.g3d.particles.ParticleChannels.Rotation3D)
      this.accellerationChannel = this.controller.particles.addChannel(com.badlogic.gdx.graphics.g3d.particles.ParticleChannels.Acceleration)
    }
    @java.lang.Override
    override def update(): scala.Unit = {
      { var i: scala.Int = 0; var accelOffset: scala.Int = 0; val c: scala.Int = i + (this.controller.particles.size * this.rotationChannel.strideSize); while (i < c) { {
        val axisZ: com.badlogic.gdx.math.Vector3 = DynamicsModifier.TMP_V1.set(this.accellerationChannel.data(accelOffset + com.badlogic.gdx.graphics.g3d.particles.ParticleChannels.XOffset), this.accellerationChannel.data(accelOffset + com.badlogic.gdx.graphics.g3d.particles.ParticleChannels.YOffset), this.accellerationChannel.data(accelOffset + com.badlogic.gdx.graphics.g3d.particles.ParticleChannels.ZOffset)).nor()
        val axisY: com.badlogic.gdx.math.Vector3 = DynamicsModifier.TMP_V2.set(DynamicsModifier.TMP_V1).crs(com.badlogic.gdx.math.Vector3.Y).nor().crs(DynamicsModifier.TMP_V1).nor()
        val axisX: com.badlogic.gdx.math.Vector3 = DynamicsModifier.TMP_V3.set(axisY).crs(axisZ).nor()
        DynamicsModifier.TMP_Q.setFromAxes(false, axisX.x, axisY.x, axisZ.x, axisX.y, axisY.y, axisZ.y, axisX.z, axisY.z, axisZ.z)
        this.rotationChannel.data(i + com.badlogic.gdx.graphics.g3d.particles.ParticleChannels.XOffset) = DynamicsModifier.TMP_Q.x
        this.rotationChannel.data(i + com.badlogic.gdx.graphics.g3d.particles.ParticleChannels.YOffset) = DynamicsModifier.TMP_Q.y
        this.rotationChannel.data(i + com.badlogic.gdx.graphics.g3d.particles.ParticleChannels.ZOffset) = DynamicsModifier.TMP_Q.z
        this.rotationChannel.data(i + com.badlogic.gdx.graphics.g3d.particles.ParticleChannels.WOffset) = DynamicsModifier.TMP_Q.w
      }; i = i + this.rotationChannel.strideSize; accelOffset = accelOffset + this.accellerationChannel.strideSize } }
    }
    @java.lang.Override
    override def copy(): com.badlogic.gdx.graphics.g3d.particles.ParticleControllerComponent = {
      return new com.badlogic.gdx.graphics.g3d.particles.influencers.DynamicsModifier.FaceDirection(this)
    }
  }
  object FaceDirection {
    export DynamicsModifier.*
  }
  abstract class Strength extends DynamicsModifier {
    var strengthChannel: com.badlogic.gdx.graphics.g3d.particles.ParallelArray#FloatChannel = null.asInstanceOf[com.badlogic.gdx.graphics.g3d.particles.ParallelArray#FloatChannel]
    var strengthValue: com.badlogic.gdx.graphics.g3d.particles.values.ScaledNumericValue = null.asInstanceOf[com.badlogic.gdx.graphics.g3d.particles.values.ScaledNumericValue]
    def this(rotation: com.badlogic.gdx.graphics.g3d.particles.influencers.DynamicsModifier.Strength) = {
      this()
      this.isGlobal = rotation.isGlobal
      this.strengthValue = new com.badlogic.gdx.graphics.g3d.particles.values.ScaledNumericValue()
      this.strengthValue.load(rotation.strengthValue)
    }
    this.strengthValue = new com.badlogic.gdx.graphics.g3d.particles.values.ScaledNumericValue()
    @java.lang.Override
    override def allocateChannels(): scala.Unit = {
      super.allocateChannels()
      com.badlogic.gdx.graphics.g3d.particles.ParticleChannels.Interpolation.id = this.controller.particleChannels.newId()
      this.strengthChannel = this.controller.particles.addChannel(com.badlogic.gdx.graphics.g3d.particles.ParticleChannels.Interpolation)
    }
    @java.lang.Override
    override def activateParticles(startIndex: scala.Int, count: scala.Int): scala.Unit = {
      var start: scala.Float = 0.0f
      var diff: scala.Float = 0.0f;
      { var i: scala.Int = startIndex * this.strengthChannel.strideSize; val c: scala.Int = i + (count * this.strengthChannel.strideSize); while (i < c) { {
        start = this.strengthValue.newLowValue()
        diff = this.strengthValue.newHighValue()
        if (!this.strengthValue.isRelative()) {
          diff = diff - start
        } else ()
        this.strengthChannel.data(i + com.badlogic.gdx.graphics.g3d.particles.ParticleChannels.VelocityStrengthStartOffset) = start
        this.strengthChannel.data(i + com.badlogic.gdx.graphics.g3d.particles.ParticleChannels.VelocityStrengthDiffOffset) = diff
      }; i = i + this.strengthChannel.strideSize } }
    }
    @java.lang.Override
    override def write(json: com.badlogic.gdx.utils.Json): scala.Unit = {
      super.write(json)
      json.writeValue("strengthValue", this.strengthValue)
    }
    @java.lang.Override
    override def read(json: com.badlogic.gdx.utils.Json, jsonData: com.badlogic.gdx.utils.JsonValue): scala.Unit = {
      super.read(json, jsonData)
      this.strengthValue = json.readValue("strengthValue", classOf[com.badlogic.gdx.graphics.g3d.particles.values.ScaledNumericValue], jsonData)
    }
  }
  object Strength {
    export DynamicsModifier.*
  }
  abstract class Angular extends com.badlogic.gdx.graphics.g3d.particles.influencers.DynamicsModifier.Strength {
    var angularChannel: com.badlogic.gdx.graphics.g3d.particles.ParallelArray#FloatChannel = null.asInstanceOf[com.badlogic.gdx.graphics.g3d.particles.ParallelArray#FloatChannel]
    var thetaValue: com.badlogic.gdx.graphics.g3d.particles.values.ScaledNumericValue = null.asInstanceOf[com.badlogic.gdx.graphics.g3d.particles.values.ScaledNumericValue]
    var phiValue: com.badlogic.gdx.graphics.g3d.particles.values.ScaledNumericValue = null.asInstanceOf[com.badlogic.gdx.graphics.g3d.particles.values.ScaledNumericValue]
    def this(value: com.badlogic.gdx.graphics.g3d.particles.influencers.DynamicsModifier.Angular) = {
      this()
      this.isGlobal = value.isGlobal
      this.strengthValue = new com.badlogic.gdx.graphics.g3d.particles.values.ScaledNumericValue()
      this.strengthValue.load(value.strengthValue)
      this.thetaValue = new com.badlogic.gdx.graphics.g3d.particles.values.ScaledNumericValue()
      this.phiValue = new com.badlogic.gdx.graphics.g3d.particles.values.ScaledNumericValue()
      this.thetaValue.load(value.thetaValue)
      this.phiValue.load(value.phiValue)
    }
    this.thetaValue = new com.badlogic.gdx.graphics.g3d.particles.values.ScaledNumericValue()
    this.phiValue = new com.badlogic.gdx.graphics.g3d.particles.values.ScaledNumericValue()
    @java.lang.Override
    override def allocateChannels(): scala.Unit = {
      super.allocateChannels()
      com.badlogic.gdx.graphics.g3d.particles.ParticleChannels.Interpolation4.id = this.controller.particleChannels.newId()
      this.angularChannel = this.controller.particles.addChannel(com.badlogic.gdx.graphics.g3d.particles.ParticleChannels.Interpolation4)
    }
    @java.lang.Override
    override def activateParticles(startIndex: scala.Int, count: scala.Int): scala.Unit = {
      super.activateParticles(startIndex, count)
      var start: scala.Float = 0.0f
      var diff: scala.Float = 0.0f;
      { var i: scala.Int = startIndex * this.angularChannel.strideSize; val c: scala.Int = i + (count * this.angularChannel.strideSize); while (i < c) { {
        start = this.thetaValue.newLowValue()
        diff = this.thetaValue.newHighValue()
        if (!this.thetaValue.isRelative()) {
          diff = diff - start
        } else ()
        this.angularChannel.data(i + com.badlogic.gdx.graphics.g3d.particles.ParticleChannels.VelocityThetaStartOffset) = start
        this.angularChannel.data(i + com.badlogic.gdx.graphics.g3d.particles.ParticleChannels.VelocityThetaDiffOffset) = diff
        start = this.phiValue.newLowValue()
        diff = this.phiValue.newHighValue()
        if (!this.phiValue.isRelative()) {
          diff = diff - start
        } else ()
        this.angularChannel.data(i + com.badlogic.gdx.graphics.g3d.particles.ParticleChannels.VelocityPhiStartOffset) = start
        this.angularChannel.data(i + com.badlogic.gdx.graphics.g3d.particles.ParticleChannels.VelocityPhiDiffOffset) = diff
      }; i = i + this.angularChannel.strideSize } }
    }
    @java.lang.Override
    override def write(json: com.badlogic.gdx.utils.Json): scala.Unit = {
      super.write(json)
      json.writeValue("thetaValue", this.thetaValue)
      json.writeValue("phiValue", this.phiValue)
    }
    @java.lang.Override
    override def read(json: com.badlogic.gdx.utils.Json, jsonData: com.badlogic.gdx.utils.JsonValue): scala.Unit = {
      super.read(json, jsonData)
      this.thetaValue = json.readValue("thetaValue", classOf[com.badlogic.gdx.graphics.g3d.particles.values.ScaledNumericValue], jsonData)
      this.phiValue = json.readValue("phiValue", classOf[com.badlogic.gdx.graphics.g3d.particles.values.ScaledNumericValue], jsonData)
    }
  }
  object Angular {
    export com.badlogic.gdx.graphics.g3d.particles.influencers.DynamicsModifier.Strength.*
  }
  class Rotational2D extends com.badlogic.gdx.graphics.g3d.particles.influencers.DynamicsModifier.Strength {
    var rotationalVelocity2dChannel: com.badlogic.gdx.graphics.g3d.particles.ParallelArray#FloatChannel = null.asInstanceOf[com.badlogic.gdx.graphics.g3d.particles.ParallelArray#FloatChannel]
    def this(rotation: com.badlogic.gdx.graphics.g3d.particles.influencers.DynamicsModifier.Rotational2D) = {
      this()
      this.isGlobal = rotation.isGlobal
      this.strengthValue = new com.badlogic.gdx.graphics.g3d.particles.values.ScaledNumericValue()
      this.strengthValue.load(rotation.strengthValue)
    }
    @java.lang.Override
    override def allocateChannels(): scala.Unit = {
      super.allocateChannels()
      this.rotationalVelocity2dChannel = this.controller.particles.addChannel(com.badlogic.gdx.graphics.g3d.particles.ParticleChannels.AngularVelocity2D)
    }
    @java.lang.Override
    override def update(): scala.Unit = {
      { var i: scala.Int = 0; var l: scala.Int = com.badlogic.gdx.graphics.g3d.particles.ParticleChannels.LifePercentOffset; var s: scala.Int = 0; val c: scala.Int = i + (this.controller.particles.size * this.rotationalVelocity2dChannel.strideSize); while (i < c) { {
        this.rotationalVelocity2dChannel.data(i) = this.rotationalVelocity2dChannel.data(i) + (this.strengthChannel.data(s + com.badlogic.gdx.graphics.g3d.particles.ParticleChannels.VelocityStrengthStartOffset) + (this.strengthChannel.data(s + com.badlogic.gdx.graphics.g3d.particles.ParticleChannels.VelocityStrengthDiffOffset) * strengthValue.getScale(this.lifeChannel.data(l))))
      }; s = s + this.strengthChannel.strideSize; i = i + this.rotationalVelocity2dChannel.strideSize; l = l + this.lifeChannel.strideSize } }
    }
    @java.lang.Override
    override def copy(): com.badlogic.gdx.graphics.g3d.particles.influencers.DynamicsModifier.Rotational2D = {
      return new com.badlogic.gdx.graphics.g3d.particles.influencers.DynamicsModifier.Rotational2D(this)
    }
  }
  object Rotational2D {
    export com.badlogic.gdx.graphics.g3d.particles.influencers.DynamicsModifier.Strength.*
  }
  class Rotational3D extends com.badlogic.gdx.graphics.g3d.particles.influencers.DynamicsModifier.Angular {
    var rotationChannel: com.badlogic.gdx.graphics.g3d.particles.ParallelArray#FloatChannel = null.asInstanceOf[com.badlogic.gdx.graphics.g3d.particles.ParallelArray#FloatChannel]
    var rotationalForceChannel: com.badlogic.gdx.graphics.g3d.particles.ParallelArray#FloatChannel = null.asInstanceOf[com.badlogic.gdx.graphics.g3d.particles.ParallelArray#FloatChannel]
    def this(rotation: com.badlogic.gdx.graphics.g3d.particles.influencers.DynamicsModifier.Rotational3D) = {
      this()
      this.isGlobal = rotation.isGlobal
      this.strengthValue = new com.badlogic.gdx.graphics.g3d.particles.values.ScaledNumericValue()
      this.strengthValue.load(rotation.strengthValue)
      this.thetaValue = new com.badlogic.gdx.graphics.g3d.particles.values.ScaledNumericValue()
      this.phiValue = new com.badlogic.gdx.graphics.g3d.particles.values.ScaledNumericValue()
      this.thetaValue.load(rotation.thetaValue)
      this.phiValue.load(rotation.phiValue)
    }
    @java.lang.Override
    override def allocateChannels(): scala.Unit = {
      super.allocateChannels()
      this.rotationChannel = this.controller.particles.addChannel(com.badlogic.gdx.graphics.g3d.particles.ParticleChannels.Rotation3D)
      this.rotationalForceChannel = this.controller.particles.addChannel(com.badlogic.gdx.graphics.g3d.particles.ParticleChannels.AngularVelocity3D)
    }
    @java.lang.Override
    override def update(): scala.Unit = {
      { var i: scala.Int = 0; var l: scala.Int = com.badlogic.gdx.graphics.g3d.particles.ParticleChannels.LifePercentOffset; var s: scala.Int = 0; var a: scala.Int = 0; val c: scala.Int = this.controller.particles.size * this.rotationalForceChannel.strideSize; while (i < c) { {
        val lifePercent: scala.Float = this.lifeChannel.data(l)
        val strength: scala.Float = this.strengthChannel.data(s + com.badlogic.gdx.graphics.g3d.particles.ParticleChannels.VelocityStrengthStartOffset) + (this.strengthChannel.data(s + com.badlogic.gdx.graphics.g3d.particles.ParticleChannels.VelocityStrengthDiffOffset) * strengthValue.getScale(lifePercent))
        val phi: scala.Float = this.angularChannel.data(a + com.badlogic.gdx.graphics.g3d.particles.ParticleChannels.VelocityPhiStartOffset) + (this.angularChannel.data(a + com.badlogic.gdx.graphics.g3d.particles.ParticleChannels.VelocityPhiDiffOffset) * phiValue.getScale(lifePercent))
        val theta: scala.Float = this.angularChannel.data(a + com.badlogic.gdx.graphics.g3d.particles.ParticleChannels.VelocityThetaStartOffset) + (this.angularChannel.data(a + com.badlogic.gdx.graphics.g3d.particles.ParticleChannels.VelocityThetaDiffOffset) * thetaValue.getScale(lifePercent))
        val cosTheta: scala.Float = com.badlogic.gdx.math.MathUtils.cosDeg(theta)
        val sinTheta: scala.Float = com.badlogic.gdx.math.MathUtils.sinDeg(theta)
        val cosPhi: scala.Float = com.badlogic.gdx.math.MathUtils.cosDeg(phi)
        val sinPhi: scala.Float = com.badlogic.gdx.math.MathUtils.sinDeg(phi)
        DynamicsModifier.TMP_V3.set(cosTheta * sinPhi, cosPhi, sinTheta * sinPhi)
        DynamicsModifier.TMP_V3.scl(strength * com.badlogic.gdx.math.MathUtils.degreesToRadians)
        this.rotationalForceChannel.data(i + com.badlogic.gdx.graphics.g3d.particles.ParticleChannels.XOffset) = this.rotationalForceChannel.data(i + com.badlogic.gdx.graphics.g3d.particles.ParticleChannels.XOffset) + DynamicsModifier.TMP_V3.x
        this.rotationalForceChannel.data(i + com.badlogic.gdx.graphics.g3d.particles.ParticleChannels.YOffset) = this.rotationalForceChannel.data(i + com.badlogic.gdx.graphics.g3d.particles.ParticleChannels.YOffset) + DynamicsModifier.TMP_V3.y
        this.rotationalForceChannel.data(i + com.badlogic.gdx.graphics.g3d.particles.ParticleChannels.ZOffset) = this.rotationalForceChannel.data(i + com.badlogic.gdx.graphics.g3d.particles.ParticleChannels.ZOffset) + DynamicsModifier.TMP_V3.z
      }; s = s + this.strengthChannel.strideSize; i = i + this.rotationalForceChannel.strideSize; a = a + this.angularChannel.strideSize; l = l + this.lifeChannel.strideSize } }
    }
    @java.lang.Override
    override def copy(): com.badlogic.gdx.graphics.g3d.particles.influencers.DynamicsModifier.Rotational3D = {
      return new com.badlogic.gdx.graphics.g3d.particles.influencers.DynamicsModifier.Rotational3D(this)
    }
  }
  object Rotational3D {
    export com.badlogic.gdx.graphics.g3d.particles.influencers.DynamicsModifier.Angular.*
  }
  class CentripetalAcceleration extends com.badlogic.gdx.graphics.g3d.particles.influencers.DynamicsModifier.Strength {
    var accelerationChannel: com.badlogic.gdx.graphics.g3d.particles.ParallelArray#FloatChannel = null.asInstanceOf[com.badlogic.gdx.graphics.g3d.particles.ParallelArray#FloatChannel]
    var positionChannel: com.badlogic.gdx.graphics.g3d.particles.ParallelArray#FloatChannel = null.asInstanceOf[com.badlogic.gdx.graphics.g3d.particles.ParallelArray#FloatChannel]
    def this(rotation: com.badlogic.gdx.graphics.g3d.particles.influencers.DynamicsModifier.CentripetalAcceleration) = {
      this()
      this.isGlobal = rotation.isGlobal
      this.strengthValue = new com.badlogic.gdx.graphics.g3d.particles.values.ScaledNumericValue()
      this.strengthValue.load(rotation.strengthValue)
    }
    @java.lang.Override
    override def allocateChannels(): scala.Unit = {
      super.allocateChannels()
      this.accelerationChannel = this.controller.particles.addChannel(com.badlogic.gdx.graphics.g3d.particles.ParticleChannels.Acceleration)
      this.positionChannel = this.controller.particles.addChannel(com.badlogic.gdx.graphics.g3d.particles.ParticleChannels.Position)
    }
    @java.lang.Override
    override def update(): scala.Unit = {
      var cx: scala.Float = 0
      var cy: scala.Float = 0
      var cz: scala.Float = 0
      if (!isGlobal) {
        val `val`: scala.Array[scala.Float] = this.controller.transform.`val`
        cx = `val`(com.badlogic.gdx.math.Matrix4.M03)
        cy = `val`(com.badlogic.gdx.math.Matrix4.M13)
        cz = `val`(com.badlogic.gdx.math.Matrix4.M23)
      } else ()
      var lifeOffset: scala.Int = com.badlogic.gdx.graphics.g3d.particles.ParticleChannels.LifePercentOffset
      var strengthOffset: scala.Int = 0
      var positionOffset: scala.Int = 0
      var forceOffset: scala.Int = 0;
      { var i: scala.Int = 0; val c: scala.Int = this.controller.particles.size; while (i < c) { {
        val strength: scala.Float = this.strengthChannel.data(strengthOffset + com.badlogic.gdx.graphics.g3d.particles.ParticleChannels.VelocityStrengthStartOffset) + (this.strengthChannel.data(strengthOffset + com.badlogic.gdx.graphics.g3d.particles.ParticleChannels.VelocityStrengthDiffOffset) * strengthValue.getScale(this.lifeChannel.data(lifeOffset)))
        DynamicsModifier.TMP_V3.set(this.positionChannel.data(positionOffset + com.badlogic.gdx.graphics.g3d.particles.ParticleChannels.XOffset) - cx, this.positionChannel.data(positionOffset + com.badlogic.gdx.graphics.g3d.particles.ParticleChannels.YOffset) - cy, this.positionChannel.data(positionOffset + com.badlogic.gdx.graphics.g3d.particles.ParticleChannels.ZOffset) - cz).nor().scl(strength)
        this.accelerationChannel.data(forceOffset + com.badlogic.gdx.graphics.g3d.particles.ParticleChannels.XOffset) = this.accelerationChannel.data(forceOffset + com.badlogic.gdx.graphics.g3d.particles.ParticleChannels.XOffset) + DynamicsModifier.TMP_V3.x
        this.accelerationChannel.data(forceOffset + com.badlogic.gdx.graphics.g3d.particles.ParticleChannels.YOffset) = this.accelerationChannel.data(forceOffset + com.badlogic.gdx.graphics.g3d.particles.ParticleChannels.YOffset) + DynamicsModifier.TMP_V3.y
        this.accelerationChannel.data(forceOffset + com.badlogic.gdx.graphics.g3d.particles.ParticleChannels.ZOffset) = this.accelerationChannel.data(forceOffset + com.badlogic.gdx.graphics.g3d.particles.ParticleChannels.ZOffset) + DynamicsModifier.TMP_V3.z
      }; i = i + 1; positionOffset = positionOffset + this.positionChannel.strideSize; strengthOffset = strengthOffset + this.strengthChannel.strideSize; forceOffset = forceOffset + this.accelerationChannel.strideSize; lifeOffset = lifeOffset + this.lifeChannel.strideSize } }
    }
    @java.lang.Override
    override def copy(): com.badlogic.gdx.graphics.g3d.particles.influencers.DynamicsModifier.CentripetalAcceleration = {
      return new com.badlogic.gdx.graphics.g3d.particles.influencers.DynamicsModifier.CentripetalAcceleration(this)
    }
  }
  object CentripetalAcceleration {
    export com.badlogic.gdx.graphics.g3d.particles.influencers.DynamicsModifier.Strength.*
  }
  class PolarAcceleration extends com.badlogic.gdx.graphics.g3d.particles.influencers.DynamicsModifier.Angular {
    var directionalVelocityChannel: com.badlogic.gdx.graphics.g3d.particles.ParallelArray#FloatChannel = null.asInstanceOf[com.badlogic.gdx.graphics.g3d.particles.ParallelArray#FloatChannel]
    def this(rotation: com.badlogic.gdx.graphics.g3d.particles.influencers.DynamicsModifier.PolarAcceleration) = {
      this()
      this.isGlobal = rotation.isGlobal
      this.strengthValue = new com.badlogic.gdx.graphics.g3d.particles.values.ScaledNumericValue()
      this.strengthValue.load(rotation.strengthValue)
      this.thetaValue = new com.badlogic.gdx.graphics.g3d.particles.values.ScaledNumericValue()
      this.phiValue = new com.badlogic.gdx.graphics.g3d.particles.values.ScaledNumericValue()
      this.thetaValue.load(rotation.thetaValue)
      this.phiValue.load(rotation.phiValue)
    }
    @java.lang.Override
    override def allocateChannels(): scala.Unit = {
      super.allocateChannels()
      this.directionalVelocityChannel = this.controller.particles.addChannel(com.badlogic.gdx.graphics.g3d.particles.ParticleChannels.Acceleration)
    }
    @java.lang.Override
    override def update(): scala.Unit = {
      { var i: scala.Int = 0; var l: scala.Int = com.badlogic.gdx.graphics.g3d.particles.ParticleChannels.LifePercentOffset; var s: scala.Int = 0; var a: scala.Int = 0; val c: scala.Int = i + (this.controller.particles.size * this.directionalVelocityChannel.strideSize); while (i < c) { {
        val lifePercent: scala.Float = this.lifeChannel.data(l)
        val strength: scala.Float = this.strengthChannel.data(s + com.badlogic.gdx.graphics.g3d.particles.ParticleChannels.VelocityStrengthStartOffset) + (this.strengthChannel.data(s + com.badlogic.gdx.graphics.g3d.particles.ParticleChannels.VelocityStrengthDiffOffset) * strengthValue.getScale(lifePercent))
        val phi: scala.Float = this.angularChannel.data(a + com.badlogic.gdx.graphics.g3d.particles.ParticleChannels.VelocityPhiStartOffset) + (this.angularChannel.data(a + com.badlogic.gdx.graphics.g3d.particles.ParticleChannels.VelocityPhiDiffOffset) * phiValue.getScale(lifePercent))
        val theta: scala.Float = this.angularChannel.data(a + com.badlogic.gdx.graphics.g3d.particles.ParticleChannels.VelocityThetaStartOffset) + (this.angularChannel.data(a + com.badlogic.gdx.graphics.g3d.particles.ParticleChannels.VelocityThetaDiffOffset) * thetaValue.getScale(lifePercent))
        val cosTheta: scala.Float = com.badlogic.gdx.math.MathUtils.cosDeg(theta)
        val sinTheta: scala.Float = com.badlogic.gdx.math.MathUtils.sinDeg(theta)
        val cosPhi: scala.Float = com.badlogic.gdx.math.MathUtils.cosDeg(phi)
        val sinPhi: scala.Float = com.badlogic.gdx.math.MathUtils.sinDeg(phi)
        DynamicsModifier.TMP_V3.set(cosTheta * sinPhi, cosPhi, sinTheta * sinPhi).nor().scl(strength)
        if (!isGlobal) {
          this.controller.transform.getRotation(DynamicsModifier.TMP_Q, true)
          DynamicsModifier.TMP_V3.mul(DynamicsModifier.TMP_Q)
        } else ()
        this.directionalVelocityChannel.data(i + com.badlogic.gdx.graphics.g3d.particles.ParticleChannels.XOffset) = this.directionalVelocityChannel.data(i + com.badlogic.gdx.graphics.g3d.particles.ParticleChannels.XOffset) + DynamicsModifier.TMP_V3.x
        this.directionalVelocityChannel.data(i + com.badlogic.gdx.graphics.g3d.particles.ParticleChannels.YOffset) = this.directionalVelocityChannel.data(i + com.badlogic.gdx.graphics.g3d.particles.ParticleChannels.YOffset) + DynamicsModifier.TMP_V3.y
        this.directionalVelocityChannel.data(i + com.badlogic.gdx.graphics.g3d.particles.ParticleChannels.ZOffset) = this.directionalVelocityChannel.data(i + com.badlogic.gdx.graphics.g3d.particles.ParticleChannels.ZOffset) + DynamicsModifier.TMP_V3.z
      }; s = s + this.strengthChannel.strideSize; i = i + this.directionalVelocityChannel.strideSize; a = a + this.angularChannel.strideSize; l = l + this.lifeChannel.strideSize } }
    }
    @java.lang.Override
    override def copy(): com.badlogic.gdx.graphics.g3d.particles.influencers.DynamicsModifier.PolarAcceleration = {
      return new com.badlogic.gdx.graphics.g3d.particles.influencers.DynamicsModifier.PolarAcceleration(this)
    }
  }
  object PolarAcceleration {
    export com.badlogic.gdx.graphics.g3d.particles.influencers.DynamicsModifier.Angular.*
  }
  class TangentialAcceleration extends com.badlogic.gdx.graphics.g3d.particles.influencers.DynamicsModifier.Angular {
    var directionalVelocityChannel: com.badlogic.gdx.graphics.g3d.particles.ParallelArray#FloatChannel = null.asInstanceOf[com.badlogic.gdx.graphics.g3d.particles.ParallelArray#FloatChannel]
    var positionChannel: com.badlogic.gdx.graphics.g3d.particles.ParallelArray#FloatChannel = null.asInstanceOf[com.badlogic.gdx.graphics.g3d.particles.ParallelArray#FloatChannel]
    def this(rotation: com.badlogic.gdx.graphics.g3d.particles.influencers.DynamicsModifier.TangentialAcceleration) = {
      this()
      this.isGlobal = rotation.isGlobal
      this.strengthValue = new com.badlogic.gdx.graphics.g3d.particles.values.ScaledNumericValue()
      this.strengthValue.load(rotation.strengthValue)
      this.thetaValue = new com.badlogic.gdx.graphics.g3d.particles.values.ScaledNumericValue()
      this.phiValue = new com.badlogic.gdx.graphics.g3d.particles.values.ScaledNumericValue()
      this.thetaValue.load(rotation.thetaValue)
      this.phiValue.load(rotation.phiValue)
    }
    @java.lang.Override
    override def allocateChannels(): scala.Unit = {
      super.allocateChannels()
      this.directionalVelocityChannel = this.controller.particles.addChannel(com.badlogic.gdx.graphics.g3d.particles.ParticleChannels.Acceleration)
      this.positionChannel = this.controller.particles.addChannel(com.badlogic.gdx.graphics.g3d.particles.ParticleChannels.Position)
    }
    @java.lang.Override
    override def update(): scala.Unit = {
      { var i: scala.Int = 0; var l: scala.Int = com.badlogic.gdx.graphics.g3d.particles.ParticleChannels.LifePercentOffset; var s: scala.Int = 0; var a: scala.Int = 0; var positionOffset: scala.Int = 0; val c: scala.Int = i + (this.controller.particles.size * this.directionalVelocityChannel.strideSize); while (i < c) { {
        val lifePercent: scala.Float = this.lifeChannel.data(l)
        val strength: scala.Float = this.strengthChannel.data(s + com.badlogic.gdx.graphics.g3d.particles.ParticleChannels.VelocityStrengthStartOffset) + (this.strengthChannel.data(s + com.badlogic.gdx.graphics.g3d.particles.ParticleChannels.VelocityStrengthDiffOffset) * strengthValue.getScale(lifePercent))
        val phi: scala.Float = this.angularChannel.data(a + com.badlogic.gdx.graphics.g3d.particles.ParticleChannels.VelocityPhiStartOffset) + (this.angularChannel.data(a + com.badlogic.gdx.graphics.g3d.particles.ParticleChannels.VelocityPhiDiffOffset) * phiValue.getScale(lifePercent))
        val theta: scala.Float = this.angularChannel.data(a + com.badlogic.gdx.graphics.g3d.particles.ParticleChannels.VelocityThetaStartOffset) + (this.angularChannel.data(a + com.badlogic.gdx.graphics.g3d.particles.ParticleChannels.VelocityThetaDiffOffset) * thetaValue.getScale(lifePercent))
        val cosTheta: scala.Float = com.badlogic.gdx.math.MathUtils.cosDeg(theta)
        val sinTheta: scala.Float = com.badlogic.gdx.math.MathUtils.sinDeg(theta)
        val cosPhi: scala.Float = com.badlogic.gdx.math.MathUtils.cosDeg(phi)
        val sinPhi: scala.Float = com.badlogic.gdx.math.MathUtils.sinDeg(phi)
        DynamicsModifier.TMP_V3.set(cosTheta * sinPhi, cosPhi, sinTheta * sinPhi)
        DynamicsModifier.TMP_V1.set(this.positionChannel.data(positionOffset + com.badlogic.gdx.graphics.g3d.particles.ParticleChannels.XOffset), this.positionChannel.data(positionOffset + com.badlogic.gdx.graphics.g3d.particles.ParticleChannels.YOffset), this.positionChannel.data(positionOffset + com.badlogic.gdx.graphics.g3d.particles.ParticleChannels.ZOffset))
        if (!isGlobal) {
          this.controller.transform.getTranslation(DynamicsModifier.TMP_V2)
          DynamicsModifier.TMP_V1.sub(DynamicsModifier.TMP_V2)
          this.controller.transform.getRotation(DynamicsModifier.TMP_Q, true)
          DynamicsModifier.TMP_V3.mul(DynamicsModifier.TMP_Q)
        } else ()
        DynamicsModifier.TMP_V3.crs(DynamicsModifier.TMP_V1).nor().scl(strength)
        this.directionalVelocityChannel.data(i + com.badlogic.gdx.graphics.g3d.particles.ParticleChannels.XOffset) = this.directionalVelocityChannel.data(i + com.badlogic.gdx.graphics.g3d.particles.ParticleChannels.XOffset) + DynamicsModifier.TMP_V3.x
        this.directionalVelocityChannel.data(i + com.badlogic.gdx.graphics.g3d.particles.ParticleChannels.YOffset) = this.directionalVelocityChannel.data(i + com.badlogic.gdx.graphics.g3d.particles.ParticleChannels.YOffset) + DynamicsModifier.TMP_V3.y
        this.directionalVelocityChannel.data(i + com.badlogic.gdx.graphics.g3d.particles.ParticleChannels.ZOffset) = this.directionalVelocityChannel.data(i + com.badlogic.gdx.graphics.g3d.particles.ParticleChannels.ZOffset) + DynamicsModifier.TMP_V3.z
      }; s = s + this.strengthChannel.strideSize; i = i + this.directionalVelocityChannel.strideSize; a = a + this.angularChannel.strideSize; l = l + this.lifeChannel.strideSize; positionOffset = positionOffset + this.positionChannel.strideSize } }
    }
    @java.lang.Override
    override def copy(): com.badlogic.gdx.graphics.g3d.particles.influencers.DynamicsModifier.TangentialAcceleration = {
      return new com.badlogic.gdx.graphics.g3d.particles.influencers.DynamicsModifier.TangentialAcceleration(this)
    }
  }
  object TangentialAcceleration {
    export com.badlogic.gdx.graphics.g3d.particles.influencers.DynamicsModifier.Angular.*
  }
  class BrownianAcceleration extends com.badlogic.gdx.graphics.g3d.particles.influencers.DynamicsModifier.Strength {
    var accelerationChannel: com.badlogic.gdx.graphics.g3d.particles.ParallelArray#FloatChannel = null.asInstanceOf[com.badlogic.gdx.graphics.g3d.particles.ParallelArray#FloatChannel]
    def this(rotation: com.badlogic.gdx.graphics.g3d.particles.influencers.DynamicsModifier.BrownianAcceleration) = {
      this()
      this.isGlobal = rotation.isGlobal
      this.strengthValue = new com.badlogic.gdx.graphics.g3d.particles.values.ScaledNumericValue()
      this.strengthValue.load(rotation.strengthValue)
    }
    @java.lang.Override
    override def allocateChannels(): scala.Unit = {
      super.allocateChannels()
      this.accelerationChannel = this.controller.particles.addChannel(com.badlogic.gdx.graphics.g3d.particles.ParticleChannels.Acceleration)
    }
    @java.lang.Override
    override def update(): scala.Unit = {
      var lifeOffset: scala.Int = com.badlogic.gdx.graphics.g3d.particles.ParticleChannels.LifePercentOffset
      var strengthOffset: scala.Int = 0
      var forceOffset: scala.Int = 0;
      { var i: scala.Int = 0; val c: scala.Int = this.controller.particles.size; while (i < c) { {
        val strength: scala.Float = this.strengthChannel.data(strengthOffset + com.badlogic.gdx.graphics.g3d.particles.ParticleChannels.VelocityStrengthStartOffset) + (this.strengthChannel.data(strengthOffset + com.badlogic.gdx.graphics.g3d.particles.ParticleChannels.VelocityStrengthDiffOffset) * strengthValue.getScale(this.lifeChannel.data(lifeOffset)))
        DynamicsModifier.TMP_V3.set(com.badlogic.gdx.math.MathUtils.random(-1, 1.0f), com.badlogic.gdx.math.MathUtils.random(-1, 1.0f), com.badlogic.gdx.math.MathUtils.random(-1, 1.0f)).nor().scl(strength)
        this.accelerationChannel.data(forceOffset + com.badlogic.gdx.graphics.g3d.particles.ParticleChannels.XOffset) = this.accelerationChannel.data(forceOffset + com.badlogic.gdx.graphics.g3d.particles.ParticleChannels.XOffset) + DynamicsModifier.TMP_V3.x
        this.accelerationChannel.data(forceOffset + com.badlogic.gdx.graphics.g3d.particles.ParticleChannels.YOffset) = this.accelerationChannel.data(forceOffset + com.badlogic.gdx.graphics.g3d.particles.ParticleChannels.YOffset) + DynamicsModifier.TMP_V3.y
        this.accelerationChannel.data(forceOffset + com.badlogic.gdx.graphics.g3d.particles.ParticleChannels.ZOffset) = this.accelerationChannel.data(forceOffset + com.badlogic.gdx.graphics.g3d.particles.ParticleChannels.ZOffset) + DynamicsModifier.TMP_V3.z
      }; i = i + 1; strengthOffset = strengthOffset + this.strengthChannel.strideSize; forceOffset = forceOffset + this.accelerationChannel.strideSize; lifeOffset = lifeOffset + this.lifeChannel.strideSize } }
    }
    @java.lang.Override
    override def copy(): com.badlogic.gdx.graphics.g3d.particles.influencers.DynamicsModifier.BrownianAcceleration = {
      return new com.badlogic.gdx.graphics.g3d.particles.influencers.DynamicsModifier.BrownianAcceleration(this)
    }
  }
  object BrownianAcceleration {
    export com.badlogic.gdx.graphics.g3d.particles.influencers.DynamicsModifier.Strength.*
  }
}