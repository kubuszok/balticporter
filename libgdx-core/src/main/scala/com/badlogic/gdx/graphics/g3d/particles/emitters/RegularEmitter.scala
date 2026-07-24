package com.badlogic.gdx.graphics.g3d.particles.emitters

class RegularEmitter extends com.badlogic.gdx.graphics.g3d.particles.emitters.Emitter with com.badlogic.gdx.utils.Json.Serializable {
  var delayValue: com.badlogic.gdx.graphics.g3d.particles.values.RangedNumericValue = null.asInstanceOf[com.badlogic.gdx.graphics.g3d.particles.values.RangedNumericValue]
  var durationValue: com.badlogic.gdx.graphics.g3d.particles.values.RangedNumericValue = null.asInstanceOf[com.badlogic.gdx.graphics.g3d.particles.values.RangedNumericValue]
  var lifeOffsetValue: com.badlogic.gdx.graphics.g3d.particles.values.ScaledNumericValue = null.asInstanceOf[com.badlogic.gdx.graphics.g3d.particles.values.ScaledNumericValue]
  var lifeValue: com.badlogic.gdx.graphics.g3d.particles.values.ScaledNumericValue = null.asInstanceOf[com.badlogic.gdx.graphics.g3d.particles.values.ScaledNumericValue]
  var emissionValue: com.badlogic.gdx.graphics.g3d.particles.values.ScaledNumericValue = null.asInstanceOf[com.badlogic.gdx.graphics.g3d.particles.values.ScaledNumericValue]
  var emission: scala.Int = 0
  var emissionDiff: scala.Int = 0
  var emissionDelta: scala.Int = 0
  var lifeOffset: scala.Int = 0
  var lifeOffsetDiff: scala.Int = 0
  var life: scala.Int = 0
  var lifeDiff: scala.Int = 0
  var duration: scala.Float = 0.0f
  var delay: scala.Float = 0.0f
  var durationTimer: scala.Float = 0.0f
  var delayTimer: scala.Float = 0.0f
  private var continuous: scala.Boolean = false
  private var emissionMode: com.badlogic.gdx.graphics.g3d.particles.emitters.RegularEmitter.EmissionMode = null.asInstanceOf[com.badlogic.gdx.graphics.g3d.particles.emitters.RegularEmitter.EmissionMode]
  private var lifeChannel: com.badlogic.gdx.graphics.g3d.particles.ParallelArray#FloatChannel = null.asInstanceOf[com.badlogic.gdx.graphics.g3d.particles.ParallelArray#FloatChannel]
  def this(regularEmitter: RegularEmitter) = {
    this()
    this.set(regularEmitter)
  }
  this.delayValue = new com.badlogic.gdx.graphics.g3d.particles.values.RangedNumericValue()
  this.durationValue = new com.badlogic.gdx.graphics.g3d.particles.values.RangedNumericValue()
  this.lifeOffsetValue = new com.badlogic.gdx.graphics.g3d.particles.values.ScaledNumericValue()
  this.lifeValue = new com.badlogic.gdx.graphics.g3d.particles.values.ScaledNumericValue()
  this.emissionValue = new com.badlogic.gdx.graphics.g3d.particles.values.ScaledNumericValue()
  this.durationValue.setActive(true)
  this.emissionValue.setActive(true)
  this.lifeValue.setActive(true)
  this.continuous = true
  this.emissionMode = com.badlogic.gdx.graphics.g3d.particles.emitters.RegularEmitter.EmissionMode.Enabled
  def allocateChannels(): scala.Unit = {
    this.lifeChannel = this.controller.particles.addChannel(com.badlogic.gdx.graphics.g3d.particles.ParticleChannels.Life)
  }
  def start(): scala.Unit = {
    this.delay = if (this.delayValue.active) this.delayValue.newLowValue() else 0
    this.delayTimer = 0
    this.durationTimer = 0.0f
    this.duration = this.durationValue.newLowValue()
    percent = this.durationTimer / this.duration.asInstanceOf[scala.Float]
    this.emission = this.emissionValue.newLowValue().asInstanceOf[scala.Int].asInstanceOf[scala.Int]
    this.emissionDiff = this.emissionValue.newHighValue().asInstanceOf[scala.Int].asInstanceOf[scala.Int]
    if (!this.emissionValue.isRelative()) {
      this.emissionDiff = this.emissionDiff - this.emission
    } else ()
    this.life = this.lifeValue.newLowValue().asInstanceOf[scala.Int].asInstanceOf[scala.Int]
    this.lifeDiff = this.lifeValue.newHighValue().asInstanceOf[scala.Int].asInstanceOf[scala.Int]
    if (!this.lifeValue.isRelative()) {
      this.lifeDiff = this.lifeDiff - this.life
    } else ()
    this.lifeOffset = if (this.lifeOffsetValue.active) this.lifeOffsetValue.newLowValue().asInstanceOf[scala.Int] else 0
    this.lifeOffsetDiff = this.lifeOffsetValue.newHighValue().asInstanceOf[scala.Int].asInstanceOf[scala.Int]
    if (!this.lifeOffsetValue.isRelative()) {
      this.lifeOffsetDiff = this.lifeOffsetDiff - this.lifeOffset
    } else ()
  }
  def init(): scala.Unit = {
    super.init()
    this.emissionDelta = 0
    this.durationTimer = this.duration
  }
  def activateParticles(startIndex: scala.Int, count: scala.Int): scala.Unit = {
    val currentTotaLife: scala.Int = this.life + (this.lifeDiff * this.lifeValue.getScale(percent)).asInstanceOf[scala.Int]
    var currentLife: scala.Int = currentTotaLife
    var offsetTime: scala.Int = (this.lifeOffset + (this.lifeOffsetDiff * this.lifeOffsetValue.getScale(percent))).asInstanceOf[scala.Int].asInstanceOf[scala.Int]
    if (offsetTime > 0) {
      if (offsetTime >= currentLife) {
        offsetTime = currentLife - 1
      } else ()
      currentLife = currentLife - offsetTime
    } else ()
    val lifePercent: scala.Float = 1 - (currentLife / currentTotaLife.asInstanceOf[scala.Float]);
    { var i: scala.Int = startIndex * this.lifeChannel.strideSize; val c: scala.Int = i + (count * this.lifeChannel.strideSize); while (i < c) { {
      this.lifeChannel.data(i + com.badlogic.gdx.graphics.g3d.particles.ParticleChannels.CurrentLifeOffset) = currentLife
      this.lifeChannel.data(i + com.badlogic.gdx.graphics.g3d.particles.ParticleChannels.TotalLifeOffset) = currentTotaLife
      this.lifeChannel.data(i + com.badlogic.gdx.graphics.g3d.particles.ParticleChannels.LifePercentOffset) = lifePercent
    }; i = i + this.lifeChannel.strideSize } }
  }
  def update(): scala.Unit = {
    val deltaMillis: scala.Float = this.controller.deltaTime * 1000
    if (this.delayTimer < this.delay) {
      this.delayTimer = this.delayTimer + deltaMillis
    } else {
      var emit: scala.Boolean = this.emissionMode != com.badlogic.gdx.graphics.g3d.particles.emitters.RegularEmitter.EmissionMode.Disabled
      if (this.durationTimer < this.duration) {
        this.durationTimer = this.durationTimer + deltaMillis
        percent = this.durationTimer / this.duration.asInstanceOf[scala.Float]
      } else {
        if ((this.continuous && emit) && (this.emissionMode == com.badlogic.gdx.graphics.g3d.particles.emitters.RegularEmitter.EmissionMode.Enabled)) {
          controller.start()
        } else {
          emit = false
        }
      }
      if (emit) {
        this.emissionDelta = (this.emissionDelta + deltaMillis).asInstanceOf[scala.Int]
        var emissionTime: scala.Float = this.emission + (this.emissionDiff * this.emissionValue.getScale(percent))
        if (emissionTime > 0) {
          emissionTime = 1000 / emissionTime
          if (this.emissionDelta >= emissionTime) {
            var emitCount: scala.Int = (this.emissionDelta / emissionTime).asInstanceOf[scala.Int].asInstanceOf[scala.Int]
            emitCount = java.lang.Math.min(emitCount, maxParticleCount - this.controller.particles.size)
            this.emissionDelta = (this.emissionDelta - (emitCount * emissionTime)).asInstanceOf[scala.Int]
            this.emissionDelta = (this.emissionDelta % emissionTime).asInstanceOf[scala.Int]
            this.addParticles(emitCount)
          } else ()
        } else ()
        if (this.controller.particles.size < minParticleCount) {
          this.addParticles(minParticleCount - this.controller.particles.size)
        } else ()
      } else ()
    }
    val activeParticles: scala.Int = this.controller.particles.size;
    { var i: scala.Int = 0; var k: scala.Int = 0; while (i < this.controller.particles.size) { {
      if ({
        this.lifeChannel.data(k + com.badlogic.gdx.graphics.g3d.particles.ParticleChannels.CurrentLifeOffset) = this.lifeChannel.data(k + com.badlogic.gdx.graphics.g3d.particles.ParticleChannels.CurrentLifeOffset) - deltaMillis
        this.lifeChannel.data(k + com.badlogic.gdx.graphics.g3d.particles.ParticleChannels.CurrentLifeOffset)
      } <= 0) {
        this.controller.particles.removeElement(i)
        /* continue */ ()
      } else {
        this.lifeChannel.data(k + com.badlogic.gdx.graphics.g3d.particles.ParticleChannels.LifePercentOffset) = 1 - (this.lifeChannel.data(k + com.badlogic.gdx.graphics.g3d.particles.ParticleChannels.CurrentLifeOffset) / this.lifeChannel.data(k + com.badlogic.gdx.graphics.g3d.particles.ParticleChannels.TotalLifeOffset))
      }
      i = i + 1
      k = k + this.lifeChannel.strideSize
    };  } }
    if (this.controller.particles.size < activeParticles) {
      controller.killParticles(this.controller.particles.size, activeParticles - this.controller.particles.size)
    } else ()
  }
  private def addParticles(count$arg: scala.Int): scala.Unit = {
    var count: scala.Int = count$arg
    count = java.lang.Math.min(count, maxParticleCount - this.controller.particles.size)
    if (count <= 0) {
      return
    } else ()
    controller.activateParticles(this.controller.particles.size, count)
    this.controller.particles.size = this.controller.particles.size + count
  }
  def getLife(): com.badlogic.gdx.graphics.g3d.particles.values.ScaledNumericValue = {
    return this.lifeValue
  }
  def getEmission(): com.badlogic.gdx.graphics.g3d.particles.values.ScaledNumericValue = {
    return this.emissionValue
  }
  def getDuration(): com.badlogic.gdx.graphics.g3d.particles.values.RangedNumericValue = {
    return this.durationValue
  }
  def getDelay(): com.badlogic.gdx.graphics.g3d.particles.values.RangedNumericValue = {
    return this.delayValue
  }
  def getLifeOffset(): com.badlogic.gdx.graphics.g3d.particles.values.ScaledNumericValue = {
    return this.lifeOffsetValue
  }
  def isContinuous(): scala.Boolean = {
    return this.continuous
  }
  def setContinuous(continuous: scala.Boolean): scala.Unit = {
    this.continuous = continuous
  }
  def getEmissionMode(): com.badlogic.gdx.graphics.g3d.particles.emitters.RegularEmitter.EmissionMode = {
    return this.emissionMode
  }
  def setEmissionMode(emissionMode: com.badlogic.gdx.graphics.g3d.particles.emitters.RegularEmitter.EmissionMode): scala.Unit = {
    this.emissionMode = emissionMode
  }
  def isComplete(): scala.Boolean = {
    if (this.delayTimer < this.delay) {
      return false
    } else ()
    return (this.durationTimer >= this.duration) && (this.controller.particles.size == 0)
  }
  def getPercentComplete(): scala.Float = {
    if (this.delayTimer < this.delay) {
      return 0
    } else ()
    return java.lang.Math.min(1, this.durationTimer / this.duration.asInstanceOf[scala.Float])
  }
  def set(emitter: RegularEmitter): scala.Unit = {
    super.set(emitter)
    this.delayValue.load(emitter.delayValue)
    this.durationValue.load(emitter.durationValue)
    this.lifeOffsetValue.load(emitter.lifeOffsetValue)
    this.lifeValue.load(emitter.lifeValue)
    this.emissionValue.load(emitter.emissionValue)
    this.emission = emitter.emission
    this.emissionDiff = emitter.emissionDiff
    this.emissionDelta = emitter.emissionDelta
    this.lifeOffset = emitter.lifeOffset
    this.lifeOffsetDiff = emitter.lifeOffsetDiff
    this.life = emitter.life
    this.lifeDiff = emitter.lifeDiff
    this.duration = emitter.duration
    this.delay = emitter.delay
    this.durationTimer = emitter.durationTimer
    this.delayTimer = emitter.delayTimer
    this.continuous = emitter.continuous
  }
  def copy(): com.badlogic.gdx.graphics.g3d.particles.ParticleControllerComponent = {
    return new RegularEmitter(this)
  }
  def write(json: com.badlogic.gdx.utils.Json): scala.Unit = {
    super.write(json)
    json.writeValue("continous", this.continuous.asInstanceOf[java.lang.Object])
    json.writeValue("emission", this.emissionValue)
    json.writeValue("delay", this.delayValue)
    json.writeValue("duration", this.durationValue)
    json.writeValue("life", this.lifeValue)
    json.writeValue("lifeOffset", this.lifeOffsetValue)
  }
  def read(json: com.badlogic.gdx.utils.Json, jsonData: com.badlogic.gdx.utils.JsonValue): scala.Unit = {
    super.read(json, jsonData)
    this.continuous = json.readValue("continous", classOf[scala.Boolean], jsonData)
    this.emissionValue = json.readValue("emission", classOf[com.badlogic.gdx.graphics.g3d.particles.values.ScaledNumericValue], jsonData)
    this.delayValue = json.readValue("delay", classOf[com.badlogic.gdx.graphics.g3d.particles.values.RangedNumericValue], jsonData)
    this.durationValue = json.readValue("duration", classOf[com.badlogic.gdx.graphics.g3d.particles.values.RangedNumericValue], jsonData)
    this.lifeValue = json.readValue("life", classOf[com.badlogic.gdx.graphics.g3d.particles.values.ScaledNumericValue], jsonData)
    this.lifeOffsetValue = json.readValue("lifeOffset", classOf[com.badlogic.gdx.graphics.g3d.particles.values.ScaledNumericValue], jsonData)
  }
}
object RegularEmitter {
  export com.badlogic.gdx.graphics.g3d.particles.emitters.Emitter.*
  sealed abstract class EmissionMode
  object EmissionMode {
    case object Enabled extends EmissionMode
    case object EnabledUntilCycleEnd extends EmissionMode
    case object Disabled extends EmissionMode
    def values(): scala.Array[EmissionMode] = scala.Array(Enabled, EnabledUntilCycleEnd, Disabled)
    def valueOf(name: java.lang.String): EmissionMode = name match {
      case "Enabled" => Enabled
      case "EnabledUntilCycleEnd" => EnabledUntilCycleEnd
      case "Disabled" => Disabled
      case _ => throw new java.lang.IllegalArgumentException(name)
    }
  }
}