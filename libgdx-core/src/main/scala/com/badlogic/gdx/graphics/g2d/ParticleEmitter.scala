package com.badlogic.gdx.graphics.g2d

class ParticleEmitter {
  private var delayValue: RangedNumericValue = new RangedNumericValue()
  private var lifeOffsetValue: IndependentScaledNumericValue = new IndependentScaledNumericValue()
  private var durationValue: RangedNumericValue = new RangedNumericValue()
  private var lifeValue: IndependentScaledNumericValue = new IndependentScaledNumericValue()
  private var emissionValue: ScaledNumericValue = new ScaledNumericValue()
  private var xScaleValue: ScaledNumericValue = new ScaledNumericValue()
  private var yScaleValue: ScaledNumericValue = new ScaledNumericValue()
  private var rotationValue: ScaledNumericValue = new ScaledNumericValue()
  private var velocityValue: ScaledNumericValue = new ScaledNumericValue()
  private var angleValue: ScaledNumericValue = new ScaledNumericValue()
  private var windValue: ScaledNumericValue = new ScaledNumericValue()
  private var gravityValue: ScaledNumericValue = new ScaledNumericValue()
  private var transparencyValue: ScaledNumericValue = new ScaledNumericValue()
  private var tintValue: GradientColorValue = new GradientColorValue()
  private var xOffsetValue: RangedNumericValue = new ScaledNumericValue()
  private var yOffsetValue: RangedNumericValue = new ScaledNumericValue()
  private var spawnWidthValue: ScaledNumericValue = new ScaledNumericValue()
  private var spawnHeightValue: ScaledNumericValue = new ScaledNumericValue()
  private var spawnShapeValue: SpawnShapeValue = new SpawnShapeValue()
  private var xSizeValues: scala.Array[RangedNumericValue] = null.asInstanceOf[scala.Array[RangedNumericValue]]
  private var ySizeValues: scala.Array[RangedNumericValue] = null.asInstanceOf[scala.Array[RangedNumericValue]]
  private var motionValues: scala.Array[RangedNumericValue] = null.asInstanceOf[scala.Array[RangedNumericValue]]
  private var accumulator: scala.Float = 0.0f
  private var sprites: com.badlogic.gdx.utils.Array[com.badlogic.gdx.graphics.g2d.Sprite] = null.asInstanceOf[com.badlogic.gdx.utils.Array[com.badlogic.gdx.graphics.g2d.Sprite]]
  private var spriteMode: SpriteMode = SpriteMode.single
  private var particles: scala.Array[Particle] = null.asInstanceOf[scala.Array[Particle]]
  private var minParticleCount: scala.Int = 0
  private var maxParticleCount: scala.Int = 4
  private var x: scala.Float = 0.0f
  private var y: scala.Float = 0.0f
  private var name: java.lang.String = null.asInstanceOf[java.lang.String]
  private var imagePaths: com.badlogic.gdx.utils.Array[java.lang.String] = null.asInstanceOf[com.badlogic.gdx.utils.Array[java.lang.String]]
  private var activeCount: scala.Int = 0
  private var active: scala.Array[scala.Boolean] = null.asInstanceOf[scala.Array[scala.Boolean]]
  private var firstUpdate: scala.Boolean = false
  private var flipX: scala.Boolean = false
  var flipY$field: scala.Boolean = false
  private var updateFlags: scala.Int = 0
  var allowCompletion$field: scala.Boolean = false
  private var bounds: com.badlogic.gdx.math.collision.BoundingBox = null.asInstanceOf[com.badlogic.gdx.math.collision.BoundingBox]
  private var emission: scala.Int = 0
  private var emissionDiff: scala.Int = 0
  private var emissionDelta: scala.Int = 0
  private var lifeOffset: scala.Int = 0
  private var lifeOffsetDiff: scala.Int = 0
  private var life: scala.Int = 0
  private var lifeDiff: scala.Int = 0
  private var spawnWidth: scala.Float = 0.0f
  private var spawnWidthDiff: scala.Float = 0.0f
  private var spawnHeight: scala.Float = 0.0f
  private var spawnHeightDiff: scala.Float = 0.0f
  var duration: scala.Float = 1
  var durationTimer: scala.Float = 0.0f
  private var delay: scala.Float = 0.0f
  private var delayTimer: scala.Float = 0.0f
  private var attached: scala.Boolean = false
  private var continuous: scala.Boolean = false
  private var aligned: scala.Boolean = false
  private var behind: scala.Boolean = false
  private var additive: scala.Boolean = true
  private var premultipliedAlpha: scala.Boolean = false
  var cleansUpBlendFunction$field: scala.Boolean = true
  def this(reader: java.io.BufferedReader) = {
    this()
    this.initialize()
    this.load(reader)
  }
  def this(emitter: ParticleEmitter) = {
    this()
    this.sprites = new com.badlogic.gdx.utils.Array[com.badlogic.gdx.graphics.g2d.Sprite](emitter.sprites)
    this.name = emitter.name
    this.imagePaths = new com.badlogic.gdx.utils.Array[java.lang.String](emitter.imagePaths)
    this.setMaxParticleCount(emitter.maxParticleCount)
    this.minParticleCount = emitter.minParticleCount
    this.delayValue.load(emitter.delayValue)
    this.durationValue.load(emitter.durationValue)
    this.emissionValue.load(emitter.emissionValue)
    this.lifeValue.load(emitter.lifeValue)
    this.lifeOffsetValue.load(emitter.lifeOffsetValue)
    this.xScaleValue.load(emitter.xScaleValue)
    this.yScaleValue.load(emitter.yScaleValue)
    this.rotationValue.load(emitter.rotationValue)
    this.velocityValue.load(emitter.velocityValue)
    this.angleValue.load(emitter.angleValue)
    this.windValue.load(emitter.windValue)
    this.gravityValue.load(emitter.gravityValue)
    this.transparencyValue.load(emitter.transparencyValue)
    this.tintValue.load(emitter.tintValue)
    this.xOffsetValue.load(emitter.xOffsetValue)
    this.yOffsetValue.load(emitter.yOffsetValue)
    this.spawnWidthValue.load(emitter.spawnWidthValue)
    this.spawnHeightValue.load(emitter.spawnHeightValue)
    this.spawnShapeValue.load(emitter.spawnShapeValue)
    this.attached = emitter.attached
    this.continuous = emitter.continuous
    this.aligned = emitter.aligned
    this.behind = emitter.behind
    this.additive = emitter.additive
    this.premultipliedAlpha = emitter.premultipliedAlpha
    this.cleansUpBlendFunction$field = emitter.cleansUpBlendFunction$field
    this.spriteMode = emitter.spriteMode
    this.setPosition(emitter.getX(), emitter.getY())
  }
  def this() = {
    this()
    this.initialize()
  }
  private def initialize(): scala.Unit = {
    this.sprites = new com.badlogic.gdx.utils.Array[com.badlogic.gdx.graphics.g2d.Sprite]()
    this.imagePaths = new com.badlogic.gdx.utils.Array[java.lang.String]()
    this.durationValue.setAlwaysActive(true)
    this.emissionValue.setAlwaysActive(true)
    this.lifeValue.setAlwaysActive(true)
    this.xScaleValue.setAlwaysActive(true)
    this.transparencyValue.setAlwaysActive(true)
    this.spawnShapeValue.setAlwaysActive(true)
    this.spawnWidthValue.setAlwaysActive(true)
    this.spawnHeightValue.setAlwaysActive(true)
  }
  def setMaxParticleCount(maxParticleCount: scala.Int): scala.Unit = {
    this.maxParticleCount = maxParticleCount
    this.active = new Array[scala.Boolean](maxParticleCount)
    this.activeCount = 0
    this.particles = new Array[Particle](maxParticleCount)
  }
  def addParticle(): scala.Unit = {
    var activeCount: scala.Int = this.activeCount
    if (activeCount == this.maxParticleCount) {
      return
    } else ()
    val active: scala.Array[scala.Boolean] = this.active
    { var i: scala.Int = 0; val n: scala.Int = active.length; while (i < n) { {
      if (!active(i)) {
        this.activateParticle(i)
        active(i) = true
        this.activeCount = activeCount + 1
        /* break */ ()
      } else ()
    }; i = i + 1 } }
  }
  def addParticles(count$arg: scala.Int): scala.Unit = {
    var count: scala.Int = count$arg
    count = java.lang.Math.min(count, this.maxParticleCount - this.activeCount)
    if (count == 0) {
      return
    } else ()
    val active: scala.Array[scala.Boolean] = this.active
    var index: scala.Int = 0
    val n: scala.Int = active.length
    { var i: scala.Int = 0; while (i < count) { {
      { ; while (index < n) { {
        if (!active(index)) {
          this.activateParticle(index)
          active({ index += 1; index }) = true
          /* continue */ ()
        } else ()
      }; index = index + 1 } }
      /* break */ ()
    }; i = i + 1 } }
    this.activeCount = this.activeCount + count
  }
  def update(delta: scala.Float): scala.Unit = {
    this.accumulator = this.accumulator + (delta * 1000)
    if (this.accumulator < 1) {
      return
    } else ()
    val deltaMillis: scala.Int = this.accumulator.asInstanceOf[scala.Int]
    this.accumulator = this.accumulator - deltaMillis
    if (this.delayTimer < this.delay) {
      this.delayTimer = this.delayTimer + deltaMillis
    } else {
      var done: scala.Boolean = false
      if (this.firstUpdate) {
        this.firstUpdate = false
        this.addParticle()
      } else ()
      if (this.durationTimer < this.duration) {
        this.durationTimer = this.durationTimer + deltaMillis
      } else {
        if ((!this.continuous) || this.allowCompletion$field) {
          done = true
        } else {
          this.restart()
        }
      }
      if (!done) {
        this.emissionDelta = this.emissionDelta + deltaMillis
        var emissionTime: scala.Float = this.emission + (this.emissionDiff * this.emissionValue.getScale(this.durationTimer / this.duration.asInstanceOf[scala.Float]))
        if (emissionTime > 0) {
          emissionTime = 1000 / emissionTime
          if (this.emissionDelta >= emissionTime) {
            var emitCount: scala.Int = (this.emissionDelta / emissionTime).asInstanceOf[scala.Int]
            emitCount = java.lang.Math.min(emitCount, this.maxParticleCount - this.activeCount)
            this.emissionDelta = this.emissionDelta - (emitCount * emissionTime)
            this.emissionDelta = this.emissionDelta % emissionTime
            this.addParticles(emitCount)
          } else ()
        } else ()
        if (this.activeCount < this.minParticleCount) {
          this.addParticles(this.minParticleCount - this.activeCount)
        } else ()
      } else ()
    }
    val active: scala.Array[scala.Boolean] = this.active
    var activeCount: scala.Int = this.activeCount
    val particles: scala.Array[Particle] = this.particles
    { var i: scala.Int = 0; val n: scala.Int = active.length; while (i < n) { {
      if (active(i) && (!this.updateParticle(particles(i), delta, deltaMillis))) {
        active(i) = false
        activeCount = activeCount - 1
      } else ()
    }; i = i + 1 } }
    this.activeCount = activeCount
  }
  def draw(batch: com.badlogic.gdx.graphics.g2d.Batch): scala.Unit = {
    if (this.premultipliedAlpha) {
      batch.setBlendFunction(com.badlogic.gdx.graphics.GL20.GL_ONE, com.badlogic.gdx.graphics.GL20.GL_ONE_MINUS_SRC_ALPHA)
    } else {
      if (this.additive) {
        batch.setBlendFunction(com.badlogic.gdx.graphics.GL20.GL_SRC_ALPHA, com.badlogic.gdx.graphics.GL20.GL_ONE)
      } else {
        batch.setBlendFunction(com.badlogic.gdx.graphics.GL20.GL_SRC_ALPHA, com.badlogic.gdx.graphics.GL20.GL_ONE_MINUS_SRC_ALPHA)
      }
    }
    val particles: scala.Array[Particle] = this.particles
    val active: scala.Array[scala.Boolean] = this.active
    { var i: scala.Int = 0; val n: scala.Int = active.length; while (i < n) { {
      if (active(i)) {
        particles(i).draw(batch)
      } else ()
    }; i = i + 1 } }
    if (this.cleansUpBlendFunction$field && (this.additive || this.premultipliedAlpha)) {
      batch.setBlendFunction(com.badlogic.gdx.graphics.GL20.GL_SRC_ALPHA, com.badlogic.gdx.graphics.GL20.GL_ONE_MINUS_SRC_ALPHA)
    } else ()
  }
  def draw(batch: com.badlogic.gdx.graphics.g2d.Batch, delta: scala.Float): scala.Unit = {
    this.accumulator = this.accumulator + (delta * 1000)
    if (this.accumulator < 1) {
      this.draw(batch)
      return
    } else ()
    val deltaMillis: scala.Int = this.accumulator.asInstanceOf[scala.Int]
    this.accumulator = this.accumulator - deltaMillis
    if (this.premultipliedAlpha) {
      batch.setBlendFunction(com.badlogic.gdx.graphics.GL20.GL_ONE, com.badlogic.gdx.graphics.GL20.GL_ONE_MINUS_SRC_ALPHA)
    } else {
      if (this.additive) {
        batch.setBlendFunction(com.badlogic.gdx.graphics.GL20.GL_SRC_ALPHA, com.badlogic.gdx.graphics.GL20.GL_ONE)
      } else {
        batch.setBlendFunction(com.badlogic.gdx.graphics.GL20.GL_SRC_ALPHA, com.badlogic.gdx.graphics.GL20.GL_ONE_MINUS_SRC_ALPHA)
      }
    }
    val particles: scala.Array[Particle] = this.particles
    val active: scala.Array[scala.Boolean] = this.active
    var activeCount: scala.Int = this.activeCount
    { var i: scala.Int = 0; val n: scala.Int = active.length; while (i < n) { {
      if (active(i)) {
        val particle: Particle = particles(i)
        if (this.updateParticle(particle, delta, deltaMillis)) {
          particle.draw(batch)
        } else {
          active(i) = false
          activeCount = activeCount - 1
        }
      } else ()
    }; i = i + 1 } }
    this.activeCount = activeCount
    if (this.cleansUpBlendFunction$field && (this.additive || this.premultipliedAlpha)) {
      batch.setBlendFunction(com.badlogic.gdx.graphics.GL20.GL_SRC_ALPHA, com.badlogic.gdx.graphics.GL20.GL_ONE_MINUS_SRC_ALPHA)
    } else ()
    if (this.delayTimer < this.delay) {
      this.delayTimer = this.delayTimer + deltaMillis
      return
    } else ()
    if (this.firstUpdate) {
      this.firstUpdate = false
      this.addParticle()
    } else ()
    if (this.durationTimer < this.duration) {
      this.durationTimer = this.durationTimer + deltaMillis
    } else {
      if ((!this.continuous) || this.allowCompletion$field) {
        return
      } else ()
      this.restart()
    }
    this.emissionDelta = this.emissionDelta + deltaMillis
    var emissionTime: scala.Float = this.emission + (this.emissionDiff * this.emissionValue.getScale(this.durationTimer / this.duration.asInstanceOf[scala.Float]))
    if (emissionTime > 0) {
      emissionTime = 1000 / emissionTime
      if (this.emissionDelta >= emissionTime) {
        var emitCount: scala.Int = (this.emissionDelta / emissionTime).asInstanceOf[scala.Int]
        emitCount = java.lang.Math.min(emitCount, this.maxParticleCount - activeCount)
        this.emissionDelta = this.emissionDelta - (emitCount * emissionTime)
        this.emissionDelta = this.emissionDelta % emissionTime
        this.addParticles(emitCount)
      } else ()
    } else ()
    if (activeCount < this.minParticleCount) {
      this.addParticles(this.minParticleCount - activeCount)
    } else ()
  }
  def start(): scala.Unit = {
    this.firstUpdate = true
    this.allowCompletion$field = false
    this.restart()
  }
  def reset(): scala.Unit = {
    this.reset(true)
  }
  def reset(start: scala.Boolean): scala.Unit = {
    this.emissionDelta = 0
    this.durationTimer = this.duration
    val active: scala.Array[scala.Boolean] = this.active
    { var i: scala.Int = 0; val n: scala.Int = active.length; while (i < n) { {
      active(i) = false
    }; i = i + 1 } }
    this.activeCount = 0
    if (start) {
      this.start()
    } else ()
  }
  private def restart(): scala.Unit = {
    this.delay = if (this.delayValue.active) this.delayValue.newLowValue() else 0
    this.delayTimer = 0
    this.durationTimer = this.durationTimer - this.duration
    this.duration = this.durationValue.newLowValue()
    this.emission = this.emissionValue.newLowValue().asInstanceOf[scala.Int]
    this.emissionDiff = this.emissionValue.newHighValue().asInstanceOf[scala.Int]
    if (!this.emissionValue.relative) {
      this.emissionDiff = this.emissionDiff - this.emission
    } else ()
    if (!this.lifeValue.independent) {
      this.generateLifeValues()
    } else ()
    if (!this.lifeOffsetValue.independent) {
      this.generateLifeOffsetValues()
    } else ()
    this.spawnWidth = this.spawnWidthValue.newLowValue()
    this.spawnWidthDiff = this.spawnWidthValue.newHighValue()
    if (!this.spawnWidthValue.relative) {
      this.spawnWidthDiff = this.spawnWidthDiff - this.spawnWidth
    } else ()
    this.spawnHeight = this.spawnHeightValue.newLowValue()
    this.spawnHeightDiff = this.spawnHeightValue.newHighValue()
    if (!this.spawnHeightValue.relative) {
      this.spawnHeightDiff = this.spawnHeightDiff - this.spawnHeight
    } else ()
    this.updateFlags = 0
    if (this.angleValue.active && (this.angleValue.timeline.length > 1)) {
      this.updateFlags = this.updateFlags | ParticleEmitter.UPDATE_ANGLE
    } else ()
    if (this.velocityValue.active) {
      this.updateFlags = this.updateFlags | ParticleEmitter.UPDATE_VELOCITY
    } else ()
    if (this.xScaleValue.timeline.length > 1) {
      this.updateFlags = this.updateFlags | ParticleEmitter.UPDATE_SCALE
    } else ()
    if (this.yScaleValue.active && (this.yScaleValue.timeline.length > 1)) {
      this.updateFlags = this.updateFlags | ParticleEmitter.UPDATE_SCALE
    } else ()
    if (this.rotationValue.active && (this.rotationValue.timeline.length > 1)) {
      this.updateFlags = this.updateFlags | ParticleEmitter.UPDATE_ROTATION
    } else ()
    if (this.windValue.active) {
      this.updateFlags = this.updateFlags | ParticleEmitter.UPDATE_WIND
    } else ()
    if (this.gravityValue.active) {
      this.updateFlags = this.updateFlags | ParticleEmitter.UPDATE_GRAVITY
    } else ()
    if (this.tintValue.timeline.length > 1) {
      this.updateFlags = this.updateFlags | ParticleEmitter.UPDATE_TINT
    } else ()
    if (this.spriteMode == SpriteMode.animated) {
      this.updateFlags = this.updateFlags | ParticleEmitter.UPDATE_SPRITE
    } else ()
  }
  protected def newParticle(sprite: com.badlogic.gdx.graphics.g2d.Sprite): Particle = {
    return new Particle(sprite)
  }
  protected def getParticles(): scala.Array[Particle] = {
    return this.particles
  }
  private def activateParticle(index: scala.Int): scala.Unit = {
    var sprite: com.badlogic.gdx.graphics.g2d.Sprite = null
    this.spriteMode match {
      case SpriteMode.single | SpriteMode.animated => {
        sprite = this.sprites.first()
      }
      case SpriteMode.random => {
        sprite = this.sprites.random()
      }
    }
    var particle: Particle = this.particles(index)
    if (particle == null) {
      this.particles(index) = {
        particle = this.newParticle(sprite)
        particle
      }
      particle.flip(this.flipX, this.flipY$field)
    } else {
      particle.set(sprite)
    }
    val percent: scala.Float = this.durationTimer / this.duration.asInstanceOf[scala.Float]
    val updateFlags: scala.Int = this.updateFlags
    if (this.lifeValue.independent) {
      this.generateLifeValues()
    } else ()
    if (this.lifeOffsetValue.independent) {
      this.generateLifeOffsetValues()
    } else ()
    particle.currentLife = {
      particle.life = this.life + (this.lifeDiff * this.lifeValue.getScale(percent)).asInstanceOf[scala.Int]
      particle.life
    }
    if (this.velocityValue.active) {
      particle.velocity = this.velocityValue.newLowValue()
      particle.velocityDiff = this.velocityValue.newHighValue()
      if (!this.velocityValue.relative) {
        particle.velocityDiff = particle.velocityDiff - particle.velocity
      } else ()
    } else ()
    particle.angle = this.angleValue.newLowValue()
    particle.angleDiff = this.angleValue.newHighValue()
    if (!this.angleValue.relative) {
      particle.angleDiff = particle.angleDiff - particle.angle
    } else ()
    var angle: scala.Float = 0
    if ((updateFlags & ParticleEmitter.UPDATE_ANGLE) == 0) {
      angle = particle.angle + (particle.angleDiff * this.angleValue.getScale(0))
      particle.angle = angle
      particle.angleCos = com.badlogic.gdx.math.MathUtils.cosDeg(angle)
      particle.angleSin = com.badlogic.gdx.math.MathUtils.sinDeg(angle)
    } else ()
    val spriteWidth: scala.Float = sprite.getWidth()
    val spriteHeight: scala.Float = sprite.getHeight()
    particle.xScale = this.xScaleValue.newLowValue() / spriteWidth
    particle.xScaleDiff = this.xScaleValue.newHighValue() / spriteWidth
    if (!this.xScaleValue.relative) {
      particle.xScaleDiff = particle.xScaleDiff - particle.xScale
    } else ()
    if (this.yScaleValue.active) {
      particle.yScale = this.yScaleValue.newLowValue() / spriteHeight
      particle.yScaleDiff = this.yScaleValue.newHighValue() / spriteHeight
      if (!this.yScaleValue.relative) {
        particle.yScaleDiff = particle.yScaleDiff - particle.yScale
      } else ()
      particle.setScale(particle.xScale + (particle.xScaleDiff * this.xScaleValue.getScale(0)), particle.yScale + (particle.yScaleDiff * this.yScaleValue.getScale(0)))
    } else {
      particle.setScale(particle.xScale + (particle.xScaleDiff * this.xScaleValue.getScale(0)))
    }
    if (this.rotationValue.active) {
      particle.rotation = this.rotationValue.newLowValue()
      particle.rotationDiff = this.rotationValue.newHighValue()
      if (!this.rotationValue.relative) {
        particle.rotationDiff = particle.rotationDiff - particle.rotation
      } else ()
      var rotation: scala.Float = particle.rotation + (particle.rotationDiff * this.rotationValue.getScale(0))
      if (this.aligned) {
        rotation = rotation + angle
      } else ()
      particle.setRotation(rotation)
    } else ()
    if (this.windValue.active) {
      particle.wind = this.windValue.newLowValue()
      particle.windDiff = this.windValue.newHighValue()
      if (!this.windValue.relative) {
        particle.windDiff = particle.windDiff - particle.wind
      } else ()
    } else ()
    if (this.gravityValue.active) {
      particle.gravity = this.gravityValue.newLowValue()
      particle.gravityDiff = this.gravityValue.newHighValue()
      if (!this.gravityValue.relative) {
        particle.gravityDiff = particle.gravityDiff - particle.gravity
      } else ()
    } else ()
    var color: scala.Array[scala.Float] = particle.tint
    if (color == null) {
      particle.tint = {
        color = new Array[scala.Float](3)
        color
      }
    } else ()
    val temp: scala.Array[scala.Float] = this.tintValue.getColor(0)
    color(0) = temp(0)
    color(1) = temp(1)
    color(2) = temp(2)
    particle.transparency = this.transparencyValue.newLowValue()
    particle.transparencyDiff = this.transparencyValue.newHighValue() - particle.transparency
    var x: scala.Float = this.x
    if (this.xOffsetValue.active) {
      x = x + this.xOffsetValue.newLowValue()
    } else ()
    var y: scala.Float = this.y
    if (this.yOffsetValue.active) {
      y = y + this.yOffsetValue.newLowValue()
    } else ()
    this.spawnShapeValue.shape match {
      case SpawnShape.square => {
        val width: scala.Float = this.spawnWidth + (this.spawnWidthDiff * this.spawnWidthValue.getScale(percent))
        val height: scala.Float = this.spawnHeight + (this.spawnHeightDiff * this.spawnHeightValue.getScale(percent))
        x = x + (com.badlogic.gdx.math.MathUtils.random(width) - (width * 0.5f))
        y = y + (com.badlogic.gdx.math.MathUtils.random(height) - (height * 0.5f))
      }
      case SpawnShape.ellipse => {
        val width: scala.Float = this.spawnWidth + (this.spawnWidthDiff * this.spawnWidthValue.getScale(percent))
        val height: scala.Float = this.spawnHeight + (this.spawnHeightDiff * this.spawnHeightValue.getScale(percent))
        val radiusX: scala.Float = width * 0.5f
        val radiusY: scala.Float = height * 0.5f
        if ((radiusX == 0) || (radiusY == 0)) {
          /* break */ ()
        } else ()
        val scaleY: scala.Float = radiusX / radiusY.asInstanceOf[scala.Float]
        if (this.spawnShapeValue.edges) {
          var spawnAngle: scala.Float = 0.0f
          this.spawnShapeValue.side match {
            case SpawnEllipseSide.top => {
              spawnAngle = -com.badlogic.gdx.math.MathUtils.random(179.0f)
            }
            case SpawnEllipseSide.bottom => {
              spawnAngle = com.badlogic.gdx.math.MathUtils.random(179.0f)
            }
            case _ => {
              spawnAngle = com.badlogic.gdx.math.MathUtils.random(360.0f)
            }
          }
          val cosDeg: scala.Float = com.badlogic.gdx.math.MathUtils.cosDeg(spawnAngle)
          val sinDeg: scala.Float = com.badlogic.gdx.math.MathUtils.sinDeg(spawnAngle)
          x = x + (cosDeg * radiusX)
          y = y + ((sinDeg * radiusX) / scaleY)
          if ((updateFlags & ParticleEmitter.UPDATE_ANGLE) == 0) {
            particle.angle = spawnAngle
            particle.angleCos = cosDeg
            particle.angleSin = sinDeg
          } else ()
        } else {
          val radius2: scala.Float = radiusX * radiusX
          while (true) {
            val px: scala.Float = com.badlogic.gdx.math.MathUtils.random(width) - radiusX
            val py: scala.Float = com.badlogic.gdx.math.MathUtils.random(width) - radiusX
            if (((px * px) + (py * py)) <= radius2) {
              x = x + px
              y = y + (py / scaleY)
              /* break */ ()
            } else ()
          }
        }
      }
      case SpawnShape.line => {
        val width: scala.Float = this.spawnWidth + (this.spawnWidthDiff * this.spawnWidthValue.getScale(percent))
        val height: scala.Float = this.spawnHeight + (this.spawnHeightDiff * this.spawnHeightValue.getScale(percent))
        if (width != 0) {
          val lineX: scala.Float = width * com.badlogic.gdx.math.MathUtils.random()
          x = x + lineX
          y = y + (lineX * (height / width.asInstanceOf[scala.Float]))
        } else {
          y = y + (height * com.badlogic.gdx.math.MathUtils.random())
        }
      }
    }
    particle.setBounds(x - (spriteWidth * 0.5f), y - (spriteHeight * 0.5f), spriteWidth, spriteHeight)
    var offsetTime: scala.Int = (this.lifeOffset + (this.lifeOffsetDiff * this.lifeOffsetValue.getScale(percent))).asInstanceOf[scala.Int]
    if (offsetTime > 0) {
      if (offsetTime >= particle.currentLife) {
        offsetTime = particle.currentLife - 1
      } else ()
      this.updateParticle(particle, offsetTime / 1000.0f, offsetTime)
    } else ()
  }
  private def updateParticle(particle: Particle, delta: scala.Float, deltaMillis: scala.Int): scala.Boolean = {
    val life: scala.Int = particle.currentLife - deltaMillis
    if (life <= 0) {
      return false
    } else ()
    particle.currentLife = life
    val percent: scala.Float = 1 - (particle.currentLife / particle.life.asInstanceOf[scala.Float])
    val updateFlags: scala.Int = this.updateFlags
    if ((updateFlags & ParticleEmitter.UPDATE_SCALE) != 0) {
      if (this.yScaleValue.active) {
        particle.setScale(particle.xScale + (particle.xScaleDiff * this.xScaleValue.getScale(percent)), particle.yScale + (particle.yScaleDiff * this.yScaleValue.getScale(percent)))
      } else {
        particle.setScale(particle.xScale + (particle.xScaleDiff * this.xScaleValue.getScale(percent)))
      }
    } else ()
    if ((updateFlags & ParticleEmitter.UPDATE_VELOCITY) != 0) {
      val velocity: scala.Float = (particle.velocity + (particle.velocityDiff * this.velocityValue.getScale(percent))) * delta
      var velocityX: scala.Float = 0.0f
      var velocityY: scala.Float = 0.0f
      if ((updateFlags & ParticleEmitter.UPDATE_ANGLE) != 0) {
        val angle: scala.Float = particle.angle + (particle.angleDiff * this.angleValue.getScale(percent))
        velocityX = velocity * com.badlogic.gdx.math.MathUtils.cosDeg(angle)
        velocityY = velocity * com.badlogic.gdx.math.MathUtils.sinDeg(angle)
        if ((updateFlags & ParticleEmitter.UPDATE_ROTATION) != 0) {
          var rotation: scala.Float = particle.rotation + (particle.rotationDiff * this.rotationValue.getScale(percent))
          if (this.aligned) {
            rotation = rotation + angle
          } else ()
          particle.setRotation(rotation)
        } else ()
      } else {
        velocityX = velocity * particle.angleCos
        velocityY = velocity * particle.angleSin
        if (this.aligned || ((updateFlags & ParticleEmitter.UPDATE_ROTATION) != 0)) {
          var rotation: scala.Float = particle.rotation + (particle.rotationDiff * this.rotationValue.getScale(percent))
          if (this.aligned) {
            rotation = rotation + particle.angle
          } else ()
          particle.setRotation(rotation)
        } else ()
      }
      if ((updateFlags & ParticleEmitter.UPDATE_WIND) != 0) {
        velocityX = velocityX + ((particle.wind + (particle.windDiff * this.windValue.getScale(percent))) * delta)
      } else ()
      if ((updateFlags & ParticleEmitter.UPDATE_GRAVITY) != 0) {
        velocityY = velocityY + ((particle.gravity + (particle.gravityDiff * this.gravityValue.getScale(percent))) * delta)
      } else ()
      particle.translate(velocityX, velocityY)
    } else {
      if ((updateFlags & ParticleEmitter.UPDATE_ROTATION) != 0) {
        particle.setRotation(particle.rotation + (particle.rotationDiff * this.rotationValue.getScale(percent)))
      } else ()
    }
    var color: scala.Array[scala.Float] = null.asInstanceOf[scala.Array[scala.Float]]
    if ((updateFlags & ParticleEmitter.UPDATE_TINT) != 0) {
      color = this.tintValue.getColor(percent)
    } else {
      color = particle.tint
    }
    if (this.premultipliedAlpha) {
      val alphaMultiplier: scala.Float = if (this.additive) 0 else 1
      val a: scala.Float = particle.transparency + (particle.transparencyDiff * this.transparencyValue.getScale(percent))
      particle.setColor(color(0) * a, color(1) * a, color(2) * a, a * alphaMultiplier)
    } else {
      particle.setColor(color(0), color(1), color(2), particle.transparency + (particle.transparencyDiff * this.transparencyValue.getScale(percent)))
    }
    if ((updateFlags & ParticleEmitter.UPDATE_SPRITE) != 0) {
      var frame: scala.Int = java.lang.Math.min((percent * this.sprites.size).asInstanceOf[scala.Int], this.sprites.size - 1)
      if (particle.frame != frame) {
        val sprite: com.badlogic.gdx.graphics.g2d.Sprite = this.sprites.get(frame)
        val prevSpriteWidth: scala.Float = particle.getWidth()
        val prevSpriteHeight: scala.Float = particle.getHeight()
        particle.setRegion(sprite)
        particle.setSize(sprite.getWidth(), sprite.getHeight())
        particle.setOrigin(sprite.getOriginX(), sprite.getOriginY())
        particle.translate((prevSpriteWidth - sprite.getWidth()) * 0.5f, (prevSpriteHeight - sprite.getHeight()) * 0.5f)
        particle.frame = frame
      } else ()
    } else ()
    return true
  }
  private def generateLifeValues(): scala.Unit = {
    this.life = this.lifeValue.newLowValue().asInstanceOf[scala.Int]
    this.lifeDiff = this.lifeValue.newHighValue().asInstanceOf[scala.Int]
    if (!this.lifeValue.relative) {
      this.lifeDiff = this.lifeDiff - this.life
    } else ()
  }
  private def generateLifeOffsetValues(): scala.Unit = {
    this.lifeOffset = if (this.lifeOffsetValue.active) this.lifeOffsetValue.newLowValue().asInstanceOf[scala.Int] else 0
    this.lifeOffsetDiff = this.lifeOffsetValue.newHighValue().asInstanceOf[scala.Int]
    if (!this.lifeOffsetValue.relative) {
      this.lifeOffsetDiff = this.lifeOffsetDiff - this.lifeOffset
    } else ()
  }
  def setPosition(x: scala.Float, y: scala.Float): scala.Unit = {
    if (this.attached) {
      val xAmount: scala.Float = x - this.x
      val yAmount: scala.Float = y - this.y
      val active: scala.Array[scala.Boolean] = this.active
      { var i: scala.Int = 0; val n: scala.Int = active.length; while (i < n) { {
        if (active(i)) {
          this.particles(i).translate(xAmount, yAmount)
        } else ()
      }; i = i + 1 } }
    } else ()
    this.x = x
    this.y = y
  }
  def setSprites(sprites: com.badlogic.gdx.utils.Array[com.badlogic.gdx.graphics.g2d.Sprite]): scala.Unit = {
    this.sprites = sprites
    if (sprites.size == 0) {
      return
    } else ()
    { var i: scala.Int = 0; val n: scala.Int = this.particles.length; while (i < n) { {
      val particle: Particle = this.particles(i)
      if (particle == null) {
        /* break */ ()
      } else ()
      var sprite: com.badlogic.gdx.graphics.g2d.Sprite = null
      this.spriteMode match {
        case SpriteMode.single => {
          sprite = sprites.first()
        }
        case SpriteMode.random => {
          sprite = sprites.random()
        }
        case SpriteMode.animated => {
          val percent: scala.Float = 1 - (particle.currentLife / particle.life.asInstanceOf[scala.Float])
          particle.frame = java.lang.Math.min((percent * sprites.size).asInstanceOf[scala.Int], sprites.size - 1)
          sprite = sprites.get(particle.frame)
        }
      }
      particle.setRegion(sprite)
      particle.setOrigin(sprite.getOriginX(), sprite.getOriginY())
    }; i = i + 1 } }
  }
  def setSpriteMode(spriteMode: SpriteMode): scala.Unit = {
    this.spriteMode = spriteMode
  }
  def preAllocateParticles(): scala.Unit = {
    if (this.sprites.isEmpty()) {
      throw new java.lang.IllegalStateException("ParticleEmitter.setSprites() must have been called before preAllocateParticles()")
    } else ()
    { var index: scala.Int = 0; while (index < this.particles.length) { {
      var particle: Particle = this.particles(index)
      if (particle == null) {
        this.particles(index) = {
          particle = this.newParticle(this.sprites.first())
          particle
        }
        particle.flip(this.flipX, this.flipY$field)
      } else ()
    }; index = index + 1 } }
  }
  def allowCompletion(): scala.Unit = {
    this.allowCompletion$field = true
    this.durationTimer = this.duration
  }
  def getAllowCompletion(): scala.Boolean = {
    return this.allowCompletion$field
  }
  def getSprites(): com.badlogic.gdx.utils.Array[com.badlogic.gdx.graphics.g2d.Sprite] = {
    return this.sprites
  }
  def getSpriteMode(): SpriteMode = {
    return this.spriteMode
  }
  def getName(): java.lang.String = {
    return this.name
  }
  def setName(name: java.lang.String): scala.Unit = {
    this.name = name
  }
  def getLife(): ScaledNumericValue = {
    return this.lifeValue
  }
  def getXScale(): ScaledNumericValue = {
    return this.xScaleValue
  }
  def getYScale(): ScaledNumericValue = {
    return this.yScaleValue
  }
  def getRotation(): ScaledNumericValue = {
    return this.rotationValue
  }
  def getTint(): GradientColorValue = {
    return this.tintValue
  }
  def getVelocity(): ScaledNumericValue = {
    return this.velocityValue
  }
  def getWind(): ScaledNumericValue = {
    return this.windValue
  }
  def getGravity(): ScaledNumericValue = {
    return this.gravityValue
  }
  def getAngle(): ScaledNumericValue = {
    return this.angleValue
  }
  def getEmission(): ScaledNumericValue = {
    return this.emissionValue
  }
  def getTransparency(): ScaledNumericValue = {
    return this.transparencyValue
  }
  def getDuration(): RangedNumericValue = {
    return this.durationValue
  }
  def getDelay(): RangedNumericValue = {
    return this.delayValue
  }
  def getLifeOffset(): ScaledNumericValue = {
    return this.lifeOffsetValue
  }
  def getXOffsetValue(): RangedNumericValue = {
    return this.xOffsetValue
  }
  def getYOffsetValue(): RangedNumericValue = {
    return this.yOffsetValue
  }
  def getSpawnWidth(): ScaledNumericValue = {
    return this.spawnWidthValue
  }
  def getSpawnHeight(): ScaledNumericValue = {
    return this.spawnHeightValue
  }
  def getSpawnShape(): SpawnShapeValue = {
    return this.spawnShapeValue
  }
  def isAttached(): scala.Boolean = {
    return this.attached
  }
  def setAttached(attached: scala.Boolean): scala.Unit = {
    this.attached = attached
  }
  def isContinuous(): scala.Boolean = {
    return this.continuous
  }
  def setContinuous(continuous: scala.Boolean): scala.Unit = {
    this.continuous = continuous
  }
  def isAligned(): scala.Boolean = {
    return this.aligned
  }
  def setAligned(aligned: scala.Boolean): scala.Unit = {
    this.aligned = aligned
  }
  def isAdditive(): scala.Boolean = {
    return this.additive
  }
  def setAdditive(additive: scala.Boolean): scala.Unit = {
    this.additive = additive
  }
  def cleansUpBlendFunction(): scala.Boolean = {
    return this.cleansUpBlendFunction$field
  }
  def setCleansUpBlendFunction(cleansUpBlendFunction: scala.Boolean): scala.Unit = {
    this.cleansUpBlendFunction$field = cleansUpBlendFunction
  }
  def isBehind(): scala.Boolean = {
    return this.behind
  }
  def setBehind(behind: scala.Boolean): scala.Unit = {
    this.behind = behind
  }
  def isPremultipliedAlpha(): scala.Boolean = {
    return this.premultipliedAlpha
  }
  def setPremultipliedAlpha(premultipliedAlpha: scala.Boolean): scala.Unit = {
    this.premultipliedAlpha = premultipliedAlpha
  }
  def getMinParticleCount(): scala.Int = {
    return this.minParticleCount
  }
  def setMinParticleCount(minParticleCount: scala.Int): scala.Unit = {
    this.minParticleCount = minParticleCount
  }
  def getMaxParticleCount(): scala.Int = {
    return this.maxParticleCount
  }
  def isComplete(): scala.Boolean = {
    if (this.continuous && (!this.allowCompletion$field)) {
      return false
    } else ()
    if (this.delayTimer < this.delay) {
      return false
    } else ()
    return (this.durationTimer >= this.duration) && (this.activeCount == 0)
  }
  def getPercentComplete(): scala.Float = {
    if (this.delayTimer < this.delay) {
      return 0
    } else ()
    return java.lang.Math.min(1, this.durationTimer / this.duration.asInstanceOf[scala.Float])
  }
  def getX(): scala.Float = {
    return this.x
  }
  def getY(): scala.Float = {
    return this.y
  }
  def getActiveCount(): scala.Int = {
    return this.activeCount
  }
  def getImagePaths(): com.badlogic.gdx.utils.Array[java.lang.String] = {
    return this.imagePaths
  }
  def setImagePaths(imagePaths: com.badlogic.gdx.utils.Array[java.lang.String]): scala.Unit = {
    this.imagePaths = imagePaths
  }
  def setFlip(flipX: scala.Boolean, flipY: scala.Boolean): scala.Unit = {
    this.flipX = flipX
    this.flipY$field = flipY
    if (this.particles == null) {
      return
    } else ()
    { var i: scala.Int = 0; val n: scala.Int = this.particles.length; while (i < n) { {
      val particle: Particle = this.particles(i)
      if (particle != null) {
        particle.flip(flipX, flipY)
      } else ()
    }; i = i + 1 } }
  }
  def flipY(): scala.Unit = {
    this.angleValue.setHigh(-this.angleValue.getHighMin(), -this.angleValue.getHighMax())
    this.angleValue.setLow(-this.angleValue.getLowMin(), -this.angleValue.getLowMax())
    this.gravityValue.setHigh(-this.gravityValue.getHighMin(), -this.gravityValue.getHighMax())
    this.gravityValue.setLow(-this.gravityValue.getLowMin(), -this.gravityValue.getLowMax())
    this.windValue.setHigh(-this.windValue.getHighMin(), -this.windValue.getHighMax())
    this.windValue.setLow(-this.windValue.getLowMin(), -this.windValue.getLowMax())
    this.rotationValue.setHigh(-this.rotationValue.getHighMin(), -this.rotationValue.getHighMax())
    this.rotationValue.setLow(-this.rotationValue.getLowMin(), -this.rotationValue.getLowMax())
    this.yOffsetValue.setLow(-this.yOffsetValue.getLowMin(), -this.yOffsetValue.getLowMax())
  }
  def getBoundingBox(): com.badlogic.gdx.math.collision.BoundingBox = {
    if (this.bounds == null) {
      this.bounds = new com.badlogic.gdx.math.collision.BoundingBox()
    } else ()
    val particles: scala.Array[Particle] = this.particles
    val active: scala.Array[scala.Boolean] = this.active
    var bounds: com.badlogic.gdx.math.collision.BoundingBox = this.bounds
    bounds.inf()
    { var i: scala.Int = 0; val n: scala.Int = active.length; while (i < n) { {
      if (active(i)) {
        val r: com.badlogic.gdx.math.Rectangle = particles(i).getBoundingRectangle()
        bounds.ext(r.x, r.y, 0)
        bounds.ext(r.x + r.width, r.y + r.height, 0)
      } else ()
    }; i = i + 1 } }
    return bounds
  }
  protected def getXSizeValues(): scala.Array[RangedNumericValue] = {
    if (this.xSizeValues == null) {
      this.xSizeValues = new Array[RangedNumericValue](3)
      this.xSizeValues(0) = this.xScaleValue
      this.xSizeValues(1) = this.spawnWidthValue
      this.xSizeValues(2) = this.xOffsetValue
    } else ()
    return this.xSizeValues
  }
  protected def getYSizeValues(): scala.Array[RangedNumericValue] = {
    if (this.ySizeValues == null) {
      this.ySizeValues = new Array[RangedNumericValue](3)
      this.ySizeValues(0) = this.yScaleValue
      this.ySizeValues(1) = this.spawnHeightValue
      this.ySizeValues(2) = this.yOffsetValue
    } else ()
    return this.ySizeValues
  }
  protected def getMotionValues(): scala.Array[RangedNumericValue] = {
    if (this.motionValues == null) {
      this.motionValues = new Array[RangedNumericValue](3)
      this.motionValues(0) = this.velocityValue
      this.motionValues(1) = this.windValue
      this.motionValues(2) = this.gravityValue
    } else ()
    return this.motionValues
  }
  def scaleSize(scale: scala.Float): scala.Unit = {
    if (scale == 1.0f) {
      return
    } else ()
    this.scaleSize(scale, scale)
  }
  def scaleSize(scaleX: scala.Float, scaleY: scala.Float): scala.Unit = {
    if ((scaleX == 1.0f) && (scaleY == 1.0f)) {
      return
    } else ()
    for (value <- this.getXSizeValues()) {
      value.scale(scaleX)
    }
    for (value <- this.getYSizeValues()) {
      value.scale(scaleY)
    }
  }
  def scaleMotion(scale: scala.Float): scala.Unit = {
    if (scale == 1.0f) {
      return
    } else ()
    for (value <- this.getMotionValues()) {
      value.scale(scale)
    }
  }
  def matchSize(template: ParticleEmitter): scala.Unit = {
    this.matchXSize(template)
    this.matchYSize(template)
  }
  def matchXSize(template: ParticleEmitter): scala.Unit = {
    val values: scala.Array[RangedNumericValue] = this.getXSizeValues()
    val templateValues: scala.Array[RangedNumericValue] = template.getXSizeValues()
    { var i: scala.Int = 0; while (i < values.length) { {
      values(i).set(templateValues(i))
    }; i = i + 1 } }
  }
  def matchYSize(template: ParticleEmitter): scala.Unit = {
    val values: scala.Array[RangedNumericValue] = this.getYSizeValues()
    val templateValues: scala.Array[RangedNumericValue] = template.getYSizeValues()
    { var i: scala.Int = 0; while (i < values.length) { {
      values(i).set(templateValues(i))
    }; i = i + 1 } }
  }
  def matchMotion(template: ParticleEmitter): scala.Unit = {
    val values: scala.Array[RangedNumericValue] = this.getMotionValues()
    val templateValues: scala.Array[RangedNumericValue] = template.getMotionValues()
    { var i: scala.Int = 0; while (i < values.length) { {
      values(i).set(templateValues(i))
    }; i = i + 1 } }
  }
  def save(output: java.io.Writer): scala.Unit = {
    output.write(this.name + "\n")
    output.write("- Delay -\n")
    this.delayValue.save(output)
    output.write("- Duration - \n")
    this.durationValue.save(output)
    output.write("- Count - \n")
    output.write(("min: " + this.minParticleCount) + "\n")
    output.write(("max: " + this.maxParticleCount) + "\n")
    output.write("- Emission - \n")
    this.emissionValue.save(output)
    output.write("- Life - \n")
    this.lifeValue.save(output)
    output.write("- Life Offset - \n")
    this.lifeOffsetValue.save(output)
    output.write("- X Offset - \n")
    this.xOffsetValue.save(output)
    output.write("- Y Offset - \n")
    this.yOffsetValue.save(output)
    output.write("- Spawn Shape - \n")
    this.spawnShapeValue.save(output)
    output.write("- Spawn Width - \n")
    this.spawnWidthValue.save(output)
    output.write("- Spawn Height - \n")
    this.spawnHeightValue.save(output)
    output.write("- X Scale - \n")
    this.xScaleValue.save(output)
    output.write("- Y Scale - \n")
    this.yScaleValue.save(output)
    output.write("- Velocity - \n")
    this.velocityValue.save(output)
    output.write("- Angle - \n")
    this.angleValue.save(output)
    output.write("- Rotation - \n")
    this.rotationValue.save(output)
    output.write("- Wind - \n")
    this.windValue.save(output)
    output.write("- Gravity - \n")
    this.gravityValue.save(output)
    output.write("- Tint - \n")
    this.tintValue.save(output)
    output.write("- Transparency - \n")
    this.transparencyValue.save(output)
    output.write("- Options - \n")
    output.write(("attached: " + this.attached) + "\n")
    output.write(("continuous: " + this.continuous) + "\n")
    output.write(("aligned: " + this.aligned) + "\n")
    output.write(("additive: " + this.additive) + "\n")
    output.write(("behind: " + this.behind) + "\n")
    output.write(("premultipliedAlpha: " + this.premultipliedAlpha) + "\n")
    output.write(("spriteMode: " + this.spriteMode.toString()) + "\n")
    output.write("- Image Paths -\n")
    for (imagePath <- this.imagePaths) {
      output.write(imagePath + "\n")
    }
    output.write("\n")
  }
  def load(reader: java.io.BufferedReader): scala.Unit = {
    try {
      this.name = ParticleEmitter.readString(reader, "name")
      reader.readLine()
      this.delayValue.load(reader)
      reader.readLine()
      this.durationValue.load(reader)
      reader.readLine()
      this.setMinParticleCount(ParticleEmitter.readInt(reader, "minParticleCount"))
      this.setMaxParticleCount(ParticleEmitter.readInt(reader, "maxParticleCount"))
      reader.readLine()
      this.emissionValue.load(reader)
      reader.readLine()
      this.lifeValue.load(reader)
      reader.readLine()
      this.lifeOffsetValue.load(reader)
      reader.readLine()
      this.xOffsetValue.load(reader)
      reader.readLine()
      this.yOffsetValue.load(reader)
      reader.readLine()
      this.spawnShapeValue.load(reader)
      reader.readLine()
      this.spawnWidthValue.load(reader)
      reader.readLine()
      this.spawnHeightValue.load(reader)
      var line: java.lang.String = reader.readLine()
      if (line.trim().equals("- Scale -")) {
        this.xScaleValue.load(reader)
        this.yScaleValue.setActive(false)
      } else {
        this.xScaleValue.load(reader)
        reader.readLine()
        this.yScaleValue.load(reader)
      }
      reader.readLine()
      this.velocityValue.load(reader)
      reader.readLine()
      this.angleValue.load(reader)
      reader.readLine()
      this.rotationValue.load(reader)
      reader.readLine()
      this.windValue.load(reader)
      reader.readLine()
      this.gravityValue.load(reader)
      reader.readLine()
      this.tintValue.load(reader)
      reader.readLine()
      this.transparencyValue.load(reader)
      reader.readLine()
      this.attached = ParticleEmitter.readBoolean(reader, "attached")
      this.continuous = ParticleEmitter.readBoolean(reader, "continuous")
      this.aligned = ParticleEmitter.readBoolean(reader, "aligned")
      this.additive = ParticleEmitter.readBoolean(reader, "additive")
      this.behind = ParticleEmitter.readBoolean(reader, "behind")
      line = reader.readLine()
      if (line.startsWith("premultipliedAlpha")) {
        this.premultipliedAlpha = ParticleEmitter.readBoolean(line)
        line = reader.readLine()
      } else ()
      if (line.startsWith("spriteMode")) {
        this.spriteMode = SpriteMode.valueOf(ParticleEmitter.readString(line))
        line = reader.readLine()
      } else ()
      val imagePaths: com.badlogic.gdx.utils.Array[java.lang.String] = new com.badlogic.gdx.utils.Array[java.lang.String]()
      while (({
        line = reader.readLine()
        line
      } != null) && (!line.isEmpty())) {
        imagePaths.add(line)
      }
      this.setImagePaths(imagePaths)
    } catch {
      case ex: java.lang.RuntimeException => {
        if (this.name == null) {
          throw ex
        } else ()
        throw new java.lang.RuntimeException("Error parsing emitter: " + this.name, ex)
      }
    }
  }
  class Particle extends com.badlogic.gdx.graphics.g2d.Sprite {
    protected var life: scala.Int = 0
    protected var currentLife: scala.Int = 0
    protected var xScale: scala.Float = 0.0f
    protected var xScaleDiff: scala.Float = 0.0f
    protected var yScale: scala.Float = 0.0f
    protected var yScaleDiff: scala.Float = 0.0f
    protected var rotation: scala.Float = 0.0f
    protected var rotationDiff: scala.Float = 0.0f
    protected var velocity: scala.Float = 0.0f
    protected var velocityDiff: scala.Float = 0.0f
    protected var angle: scala.Float = 0.0f
    protected var angleDiff: scala.Float = 0.0f
    protected var angleCos: scala.Float = 0.0f
    protected var angleSin: scala.Float = 0.0f
    protected var transparency: scala.Float = 0.0f
    protected var transparencyDiff: scala.Float = 0.0f
    protected var wind: scala.Float = 0.0f
    protected var windDiff: scala.Float = 0.0f
    protected var gravity: scala.Float = 0.0f
    protected var gravityDiff: scala.Float = 0.0f
    protected var tint: scala.Array[scala.Float] = null.asInstanceOf[scala.Array[scala.Float]]
    protected var frame: scala.Int = 0
    def this(sprite: com.badlogic.gdx.graphics.g2d.Sprite) = {
      this()
    }
  }
  class ParticleValue {
    var active: scala.Boolean = false
    var alwaysActive: scala.Boolean = false
    def setAlwaysActive(alwaysActive: scala.Boolean): scala.Unit = {
      this.alwaysActive = alwaysActive
    }
    def isAlwaysActive(): scala.Boolean = {
      return this.alwaysActive
    }
    def isActive(): scala.Boolean = {
      return this.alwaysActive || this.active
    }
    def setActive(active: scala.Boolean): scala.Unit = {
      this.active = active
    }
    def save(output: java.io.Writer): scala.Unit = {
      if (!this.alwaysActive) {
        output.write(("active: " + this.active) + "\n")
      } else {
        this.active = true
      }
    }
    def load(reader: java.io.BufferedReader): scala.Unit = {
      if (!this.alwaysActive) {
        this.active = ParticleEmitter.readBoolean(reader, "active")
      } else {
        this.active = true
      }
    }
    def load(value: ParticleValue): scala.Unit = {
      this.active = value.active
      this.alwaysActive = value.alwaysActive
    }
  }
  class NumericValue extends ParticleValue {
    private var value: scala.Float = 0.0f
    def getValue(): scala.Float = {
      return this.value
    }
    def setValue(value: scala.Float): scala.Unit = {
      this.value = value
    }
    def save(output: java.io.Writer): scala.Unit = {
      super.save(output)
      if (!active) {
        return
      } else ()
      output.write(("value: " + this.value) + "\n")
    }
    def load(reader: java.io.BufferedReader): scala.Unit = {
      super.load(reader)
      if (!active) {
        return
      } else ()
      this.value = ParticleEmitter.readFloat(reader, "value")
    }
    def load(value: NumericValue): scala.Unit = {
      super.load(value)
      this.value = value.value
    }
  }
  class RangedNumericValue extends ParticleValue {
    private var lowMin: scala.Float = 0.0f
    private var lowMax: scala.Float = 0.0f
    def newLowValue(): scala.Float = {
      return this.lowMin + ((this.lowMax - this.lowMin) * com.badlogic.gdx.math.MathUtils.random())
    }
    def setLow(value: scala.Float): scala.Unit = {
      this.lowMin = value
      this.lowMax = value
    }
    def setLow(min: scala.Float, max: scala.Float): scala.Unit = {
      this.lowMin = min
      this.lowMax = max
    }
    def getLowMin(): scala.Float = {
      return this.lowMin
    }
    def setLowMin(lowMin: scala.Float): scala.Unit = {
      this.lowMin = lowMin
    }
    def getLowMax(): scala.Float = {
      return this.lowMax
    }
    def setLowMax(lowMax: scala.Float): scala.Unit = {
      this.lowMax = lowMax
    }
    def scale(scale: scala.Float): scala.Unit = {
      this.lowMin = this.lowMin * scale
      this.lowMax = this.lowMax * scale
    }
    def set(value: RangedNumericValue): scala.Unit = {
      this.lowMin = value.lowMin
      this.lowMax = value.lowMax
    }
    def save(output: java.io.Writer): scala.Unit = {
      super.save(output)
      if (!active) {
        return
      } else ()
      output.write(("lowMin: " + this.lowMin) + "\n")
      output.write(("lowMax: " + this.lowMax) + "\n")
    }
    def load(reader: java.io.BufferedReader): scala.Unit = {
      super.load(reader)
      if (!active) {
        return
      } else ()
      this.lowMin = ParticleEmitter.readFloat(reader, "lowMin")
      this.lowMax = ParticleEmitter.readFloat(reader, "lowMax")
    }
    def load(value: RangedNumericValue): scala.Unit = {
      super.load(value)
      this.lowMax = value.lowMax
      this.lowMin = value.lowMin
    }
  }
  class ScaledNumericValue extends RangedNumericValue {
    private var scaling: scala.Array[scala.Float] = Array[scala.Float](1)
    var timeline: scala.Array[scala.Float] = Array[scala.Float](0)
    private var highMin: scala.Float = 0.0f
    private var highMax: scala.Float = 0.0f
    var relative: scala.Boolean = false
    def newHighValue(): scala.Float = {
      return this.highMin + ((this.highMax - this.highMin) * com.badlogic.gdx.math.MathUtils.random())
    }
    def setHigh(value: scala.Float): scala.Unit = {
      this.highMin = value
      this.highMax = value
    }
    def setHigh(min: scala.Float, max: scala.Float): scala.Unit = {
      this.highMin = min
      this.highMax = max
    }
    def getHighMin(): scala.Float = {
      return this.highMin
    }
    def setHighMin(highMin: scala.Float): scala.Unit = {
      this.highMin = highMin
    }
    def getHighMax(): scala.Float = {
      return this.highMax
    }
    def setHighMax(highMax: scala.Float): scala.Unit = {
      this.highMax = highMax
    }
    def scale(scale: scala.Float): scala.Unit = {
      super.scale(scale)
      this.highMin = this.highMin * scale
      this.highMax = this.highMax * scale
    }
    def set(value: RangedNumericValue): scala.Unit = {
      if (value.isInstanceOf[ScaledNumericValue]) {
        this.set(value.asInstanceOf[ScaledNumericValue])
      } else {
        super.set(value)
      }
    }
    def set(value: ScaledNumericValue): scala.Unit = {
      super.set(value)
      this.highMin = value.highMin
      this.highMax = value.highMax
      if (this.scaling.length != value.scaling.length) {
        this.scaling = java.util.Arrays.copyOf(value.scaling, value.scaling.length)
      } else {
        java.lang.System.arraycopy(value.scaling, 0, this.scaling, 0, this.scaling.length)
      }
      if (this.timeline.length != value.timeline.length) {
        this.timeline = java.util.Arrays.copyOf(value.timeline, value.timeline.length)
      } else {
        java.lang.System.arraycopy(value.timeline, 0, this.timeline, 0, this.timeline.length)
      }
      this.relative = value.relative
    }
    def getScaling(): scala.Array[scala.Float] = {
      return this.scaling
    }
    def setScaling(values: scala.Array[scala.Float]): scala.Unit = {
      this.scaling = values
    }
    def getTimeline(): scala.Array[scala.Float] = {
      return this.timeline
    }
    def setTimeline(timeline: scala.Array[scala.Float]): scala.Unit = {
      this.timeline = timeline
    }
    def isRelative(): scala.Boolean = {
      return this.relative
    }
    def setRelative(relative: scala.Boolean): scala.Unit = {
      this.relative = relative
    }
    def getScale(percent: scala.Float): scala.Float = {
      var endIndex: scala.Int = -1
      val timeline: scala.Array[scala.Float] = this.timeline
      val n: scala.Int = timeline.length
      { var i: scala.Int = 1; while (i < n) { {
        val t: scala.Float = timeline(i)
        if (t > percent) {
          endIndex = i
          /* break */ ()
        } else ()
      }; i = i + 1 } }
      if (endIndex == (-1)) {
        return this.scaling(n - 1)
      } else ()
      val scaling: scala.Array[scala.Float] = this.scaling
      val startIndex: scala.Int = endIndex - 1
      val startValue: scala.Float = scaling(startIndex)
      val startTime: scala.Float = timeline(startIndex)
      return startValue + ((scaling(endIndex) - startValue) * ((percent - startTime) / (timeline(endIndex) - startTime)))
    }
    def save(output: java.io.Writer): scala.Unit = {
      super.save(output)
      if (!active) {
        return
      } else ()
      output.write(("highMin: " + this.highMin) + "\n")
      output.write(("highMax: " + this.highMax) + "\n")
      output.write(("relative: " + this.relative) + "\n")
      output.write(("scalingCount: " + this.scaling.length) + "\n")
      { var i: scala.Int = 0; while (i < this.scaling.length) { {
        output.write(((("scaling" + i) + ": ") + this.scaling(i)) + "\n")
      }; i = i + 1 } }
      output.write(("timelineCount: " + this.timeline.length) + "\n")
      { var i: scala.Int = 0; while (i < this.timeline.length) { {
        output.write(((("timeline" + i) + ": ") + this.timeline(i)) + "\n")
      }; i = i + 1 } }
    }
    def load(reader: java.io.BufferedReader): scala.Unit = {
      super.load(reader)
      if (!active) {
        return
      } else ()
      this.highMin = ParticleEmitter.readFloat(reader, "highMin")
      this.highMax = ParticleEmitter.readFloat(reader, "highMax")
      this.relative = ParticleEmitter.readBoolean(reader, "relative")
      this.scaling = new Array[scala.Float](ParticleEmitter.readInt(reader, "scalingCount"))
      { var i: scala.Int = 0; while (i < this.scaling.length) { {
        this.scaling(i) = ParticleEmitter.readFloat(reader, "scaling" + i)
      }; i = i + 1 } }
      this.timeline = new Array[scala.Float](ParticleEmitter.readInt(reader, "timelineCount"))
      { var i: scala.Int = 0; while (i < this.timeline.length) { {
        this.timeline(i) = ParticleEmitter.readFloat(reader, "timeline" + i)
      }; i = i + 1 } }
    }
    def load(value: ScaledNumericValue): scala.Unit = {
      super.load(value)
      this.highMax = value.highMax
      this.highMin = value.highMin
      this.scaling = new Array[scala.Float](value.scaling.length)
      java.lang.System.arraycopy(value.scaling, 0, this.scaling, 0, this.scaling.length)
      this.timeline = new Array[scala.Float](value.timeline.length)
      java.lang.System.arraycopy(value.timeline, 0, this.timeline, 0, this.timeline.length)
      this.relative = value.relative
    }
  }
  class IndependentScaledNumericValue extends ScaledNumericValue {
    var independent: scala.Boolean = false
    def isIndependent(): scala.Boolean = {
      return this.independent
    }
    def setIndependent(independent: scala.Boolean): scala.Unit = {
      this.independent = independent
    }
    def set(value: RangedNumericValue): scala.Unit = {
      if (value.isInstanceOf[IndependentScaledNumericValue]) {
        this.set(value.asInstanceOf[IndependentScaledNumericValue])
      } else {
        super.set(value)
      }
    }
    def set(value: ScaledNumericValue): scala.Unit = {
      if (value.isInstanceOf[IndependentScaledNumericValue]) {
        this.set(value.asInstanceOf[IndependentScaledNumericValue])
      } else {
        super.set(value)
      }
    }
    def set(value: IndependentScaledNumericValue): scala.Unit = {
      super.set(value)
      this.independent = value.independent
    }
    def save(output: java.io.Writer): scala.Unit = {
      super.save(output)
      output.write(("independent: " + this.independent) + "\n")
    }
    def load(reader: java.io.BufferedReader): scala.Unit = {
      super.load(reader)
      if (reader.markSupported()) {
        reader.mark(100)
      } else ()
      val line: java.lang.String = reader.readLine()
      if (line == null) {
        throw new java.io.IOException("Missing value: independent")
      } else ()
      if (line.contains("independent")) {
        this.independent = java.lang.Boolean.parseBoolean(ParticleEmitter.readString(line))
      } else {
        if (reader.markSupported()) {
          reader.reset()
        } else {
          val errorMessage: java.lang.String = ("The loaded particle effect descriptor file uses an old invalid format. " + "Please download the latest version of the Particle Editor tool and recreate the file by") + " loading and saving it again."
          com.badlogic.gdx.Gdx.app.error("ParticleEmitter", errorMessage)
          throw new java.io.IOException(errorMessage)
        }
      }
    }
    def load(value: IndependentScaledNumericValue): scala.Unit = {
      super.load(value)
      this.independent = value.independent
    }
  }
  class GradientColorValue extends ParticleValue {
    private var colors: scala.Array[scala.Float] = Array[scala.Float](1, 1, 1)
    var timeline: scala.Array[scala.Float] = Array[scala.Float](0)
    def this() = {
      this()
      alwaysActive = true
    }
    def getTimeline(): scala.Array[scala.Float] = {
      return this.timeline
    }
    def setTimeline(timeline: scala.Array[scala.Float]): scala.Unit = {
      this.timeline = timeline
    }
    def getColors(): scala.Array[scala.Float] = {
      return this.colors
    }
    def setColors(colors: scala.Array[scala.Float]): scala.Unit = {
      this.colors = colors
    }
    def getColor(percent: scala.Float): scala.Array[scala.Float] = {
      var startIndex: scala.Int = 0
      var endIndex: scala.Int = -1
      val timeline: scala.Array[scala.Float] = this.timeline
      val n: scala.Int = timeline.length
      { var i: scala.Int = 1; while (i < n) { {
        val t: scala.Float = timeline(i)
        if (t > percent) {
          endIndex = i
          /* break */ ()
        } else ()
        startIndex = i
      }; i = i + 1 } }
      val startTime: scala.Float = timeline(startIndex)
      startIndex = startIndex * 3
      val r1: scala.Float = this.colors(startIndex)
      val g1: scala.Float = this.colors(startIndex + 1)
      val b1: scala.Float = this.colors(startIndex + 2)
      if (endIndex == (-1)) {
        GradientColorValue.temp(0) = r1
        GradientColorValue.temp(1) = g1
        GradientColorValue.temp(2) = b1
        return GradientColorValue.temp
      } else ()
      val factor: scala.Float = (percent - startTime) / (timeline(endIndex) - startTime)
      endIndex = endIndex * 3
      GradientColorValue.temp(0) = r1 + ((this.colors(endIndex) - r1) * factor)
      GradientColorValue.temp(1) = g1 + ((this.colors(endIndex + 1) - g1) * factor)
      GradientColorValue.temp(2) = b1 + ((this.colors(endIndex + 2) - b1) * factor)
      return GradientColorValue.temp
    }
    def save(output: java.io.Writer): scala.Unit = {
      super.save(output)
      if (!active) {
        return
      } else ()
      output.write(("colorsCount: " + this.colors.length) + "\n")
      { var i: scala.Int = 0; while (i < this.colors.length) { {
        output.write(((("colors" + i) + ": ") + this.colors(i)) + "\n")
      }; i = i + 1 } }
      output.write(("timelineCount: " + this.timeline.length) + "\n")
      { var i: scala.Int = 0; while (i < this.timeline.length) { {
        output.write(((("timeline" + i) + ": ") + this.timeline(i)) + "\n")
      }; i = i + 1 } }
    }
    def load(reader: java.io.BufferedReader): scala.Unit = {
      super.load(reader)
      if (!active) {
        return
      } else ()
      this.colors = new Array[scala.Float](ParticleEmitter.readInt(reader, "colorsCount"))
      { var i: scala.Int = 0; while (i < this.colors.length) { {
        this.colors(i) = ParticleEmitter.readFloat(reader, "colors" + i)
      }; i = i + 1 } }
      this.timeline = new Array[scala.Float](ParticleEmitter.readInt(reader, "timelineCount"))
      { var i: scala.Int = 0; while (i < this.timeline.length) { {
        this.timeline(i) = ParticleEmitter.readFloat(reader, "timeline" + i)
      }; i = i + 1 } }
    }
    def load(value: GradientColorValue): scala.Unit = {
      super.load(value)
      this.colors = new Array[scala.Float](value.colors.length)
      java.lang.System.arraycopy(value.colors, 0, this.colors, 0, this.colors.length)
      this.timeline = new Array[scala.Float](value.timeline.length)
      java.lang.System.arraycopy(value.timeline, 0, this.timeline, 0, this.timeline.length)
    }
  }
  object GradientColorValue {
    private var temp: scala.Array[scala.Float] = new Array[scala.Float](4)
  }
  class SpawnShapeValue extends ParticleValue {
    var shape: SpawnShape = SpawnShape.point
    var edges: scala.Boolean = false
    var side: SpawnEllipseSide = SpawnEllipseSide.both
    def getShape(): SpawnShape = {
      return this.shape
    }
    def setShape(shape: SpawnShape): scala.Unit = {
      this.shape = shape
    }
    def isEdges(): scala.Boolean = {
      return this.edges
    }
    def setEdges(edges: scala.Boolean): scala.Unit = {
      this.edges = edges
    }
    def getSide(): SpawnEllipseSide = {
      return this.side
    }
    def setSide(side: SpawnEllipseSide): scala.Unit = {
      this.side = side
    }
    def save(output: java.io.Writer): scala.Unit = {
      super.save(output)
      if (!active) {
        return
      } else ()
      output.write(("shape: " + this.shape) + "\n")
      if (this.shape == SpawnShape.ellipse) {
        output.write(("edges: " + this.edges) + "\n")
        output.write(("side: " + this.side) + "\n")
      } else ()
    }
    def load(reader: java.io.BufferedReader): scala.Unit = {
      super.load(reader)
      if (!active) {
        return
      } else ()
      this.shape = SpawnShape.valueOf(ParticleEmitter.readString(reader, "shape"))
      if (this.shape == SpawnShape.ellipse) {
        this.edges = ParticleEmitter.readBoolean(reader, "edges")
        this.side = SpawnEllipseSide.valueOf(ParticleEmitter.readString(reader, "side"))
      } else ()
    }
    def load(value: SpawnShapeValue): scala.Unit = {
      super.load(value)
      this.shape = value.shape
      this.edges = value.edges
      this.side = value.side
    }
  }
  sealed abstract class SpawnShape
  object SpawnShape {
    case object point extends SpawnShape
    case object line extends SpawnShape
    case object square extends SpawnShape
    case object ellipse extends SpawnShape
    def values(): Array[SpawnShape] = Array(point, line, square, ellipse)
  }
  sealed abstract class SpawnEllipseSide
  object SpawnEllipseSide {
    case object both extends SpawnEllipseSide
    case object top extends SpawnEllipseSide
    case object bottom extends SpawnEllipseSide
    def values(): Array[SpawnEllipseSide] = Array(both, top, bottom)
  }
  sealed abstract class SpriteMode
  object SpriteMode {
    case object single extends SpriteMode
    case object random extends SpriteMode
    case object animated extends SpriteMode
    def values(): Array[SpriteMode] = Array(single, random, animated)
  }
}
object ParticleEmitter {
  private final val UPDATE_SCALE: scala.Int = 1 << 0
  private final val UPDATE_ANGLE: scala.Int = 1 << 1
  private final val UPDATE_ROTATION: scala.Int = 1 << 2
  private final val UPDATE_VELOCITY: scala.Int = 1 << 3
  private final val UPDATE_WIND: scala.Int = 1 << 4
  private final val UPDATE_GRAVITY: scala.Int = 1 << 5
  private final val UPDATE_TINT: scala.Int = 1 << 6
  private final val UPDATE_SPRITE: scala.Int = 1 << 7
  def readString(line: java.lang.String): java.lang.String = {
    return line.substring(line.indexOf(":") + 1).trim()
  }
  def readString(reader: java.io.BufferedReader, name: java.lang.String): java.lang.String = {
    val line: java.lang.String = reader.readLine()
    if (line == null) {
      throw new java.io.IOException("Missing value: " + name)
    } else ()
    return ParticleEmitter.readString(line)
  }
  def readBoolean(line: java.lang.String): scala.Boolean = {
    return java.lang.Boolean.parseBoolean(ParticleEmitter.readString(line))
  }
  def readBoolean(reader: java.io.BufferedReader, name: java.lang.String): scala.Boolean = {
    return java.lang.Boolean.parseBoolean(ParticleEmitter.readString(reader, name))
  }
  def readInt(reader: java.io.BufferedReader, name: java.lang.String): scala.Int = {
    return java.lang.Integer.parseInt(ParticleEmitter.readString(reader, name))
  }
  def readFloat(reader: java.io.BufferedReader, name: java.lang.String): scala.Float = {
    return java.lang.Float.parseFloat(ParticleEmitter.readString(reader, name))
  }
}