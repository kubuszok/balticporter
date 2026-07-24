package com.badlogic.gdx.graphics.g3d.particles

class ParticleController extends com.badlogic.gdx.utils.Json.Serializable with com.badlogic.gdx.graphics.g3d.particles.ResourceData.Configurable[?] {
  var name: java.lang.String = null.asInstanceOf[java.lang.String]
  var emitter: com.badlogic.gdx.graphics.g3d.particles.emitters.Emitter = null.asInstanceOf[com.badlogic.gdx.graphics.g3d.particles.emitters.Emitter]
  var influencers: com.badlogic.gdx.utils.Array[com.badlogic.gdx.graphics.g3d.particles.influencers.Influencer] = null.asInstanceOf[com.badlogic.gdx.utils.Array[com.badlogic.gdx.graphics.g3d.particles.influencers.Influencer]]
  var renderer: com.badlogic.gdx.graphics.g3d.particles.renderers.ParticleControllerRenderer[?, ?] = null.asInstanceOf[com.badlogic.gdx.graphics.g3d.particles.renderers.ParticleControllerRenderer[?, ?]]
  var particles: com.badlogic.gdx.graphics.g3d.particles.ParallelArray = null.asInstanceOf[com.badlogic.gdx.graphics.g3d.particles.ParallelArray]
  var particleChannels: com.badlogic.gdx.graphics.g3d.particles.ParticleChannels = null.asInstanceOf[com.badlogic.gdx.graphics.g3d.particles.ParticleChannels]
  var transform: com.badlogic.gdx.math.Matrix4 = null.asInstanceOf[com.badlogic.gdx.math.Matrix4]
  var scale$field: com.badlogic.gdx.math.Vector3 = null.asInstanceOf[com.badlogic.gdx.math.Vector3]
  var boundingBox: com.badlogic.gdx.math.collision.BoundingBox = null.asInstanceOf[com.badlogic.gdx.math.collision.BoundingBox]
  var deltaTime: scala.Float = 0.0f
  var deltaTimeSqr: scala.Float = 0.0f
  def this(name: java.lang.String, emitter: com.badlogic.gdx.graphics.g3d.particles.emitters.Emitter, renderer: com.badlogic.gdx.graphics.g3d.particles.renderers.ParticleControllerRenderer[?, ?], influencers: scala.Array[com.badlogic.gdx.graphics.g3d.particles.influencers.Influencer]) = {
    this()
    this.name = name
    this.emitter = emitter
    this.renderer = renderer
    this.particleChannels = new com.badlogic.gdx.graphics.g3d.particles.ParticleChannels()
    this.influencers = new com.badlogic.gdx.utils.Array[com.badlogic.gdx.graphics.g3d.particles.influencers.Influencer](influencers)
  }
  def this() = {
    this()
    this.transform = new com.badlogic.gdx.math.Matrix4()
    this.scale$field = new com.badlogic.gdx.math.Vector3(1, 1, 1)
    this.influencers = new com.badlogic.gdx.utils.Array[com.badlogic.gdx.graphics.g3d.particles.influencers.Influencer](true, 3, (() => new scala.Array[com.badlogic.gdx.graphics.g3d.particles.influencers.Influencer]()))
    this.setTimeStep(ParticleController.DEFAULT_TIME_STEP)
  }
  private def setTimeStep(timeStep: scala.Float): scala.Unit = {
    this.deltaTime = timeStep
    this.deltaTimeSqr = this.deltaTime * this.deltaTime
  }
  def setTransform(transform: com.badlogic.gdx.math.Matrix4): scala.Unit = {
    this.transform.set(transform)
    transform.getScale(this.scale$field)
  }
  def setTransform(x: scala.Float, y: scala.Float, z: scala.Float, qx: scala.Float, qy: scala.Float, qz: scala.Float, qw: scala.Float, scale: scala.Float): scala.Unit = {
    this.transform.set(x, y, z, qx, qy, qz, qw, scale, scale, scale)
    this.scale$field.set(scale, scale, scale)
  }
  def rotate(rotation: com.badlogic.gdx.math.Quaternion): scala.Unit = {
    this.transform.rotate(rotation)
  }
  def rotate(axis: com.badlogic.gdx.math.Vector3, angle: scala.Float): scala.Unit = {
    this.transform.rotate(axis, angle)
  }
  def translate(translation: com.badlogic.gdx.math.Vector3): scala.Unit = {
    this.transform.translate(translation)
  }
  def setTranslation(translation: com.badlogic.gdx.math.Vector3): scala.Unit = {
    this.transform.setTranslation(translation)
  }
  def scale(scaleX: scala.Float, scaleY: scala.Float, scaleZ: scala.Float): scala.Unit = {
    this.transform.scale(scaleX, scaleY, scaleZ)
    this.transform.getScale(this.scale$field)
  }
  def scale(scale: com.badlogic.gdx.math.Vector3): scala.Unit = {
    this.scale(scale.x, scale.y, scale.z)
  }
  def mul(transform: com.badlogic.gdx.math.Matrix4): scala.Unit = {
    this.transform.mul(transform)
    this.transform.getScale(this.scale$field)
  }
  def getTransform(transform: com.badlogic.gdx.math.Matrix4): scala.Unit = {
    transform.set(this.transform)
  }
  def isComplete(): scala.Boolean = {
    return this.emitter.isComplete()
  }
  def init(): scala.Unit = {
    this.bind()
    if (this.particles != null) {
      this.`end`()
      this.particleChannels.resetIds()
    } else ()
    this.allocateChannels(this.emitter.maxParticleCount)
    this.emitter.init()
    for (influencer <- this.influencers) {
      influencer.init()
    }
    this.renderer.init()
  }
  def allocateChannels(maxParticleCount: scala.Int): scala.Unit = {
    this.particles = new com.badlogic.gdx.graphics.g3d.particles.ParallelArray(maxParticleCount)
    this.emitter.allocateChannels()
    for (influencer <- this.influencers) {
      influencer.allocateChannels()
    }
    this.renderer.allocateChannels()
  }
  def bind(): scala.Unit = {
    this.emitter.set(this)
    for (influencer <- this.influencers) {
      influencer.set(this)
    }
    this.renderer.set(this)
  }
  def start(): scala.Unit = {
    this.emitter.start()
    for (influencer <- this.influencers) {
      influencer.start()
    }
  }
  def reset(): scala.Unit = {
    this.`end`()
    this.start()
  }
  def `end`(): scala.Unit = {
    for (influencer <- this.influencers) {
      influencer.`end`()
    }
    this.emitter.`end`()
  }
  def activateParticles(startIndex: scala.Int, count: scala.Int): scala.Unit = {
    this.emitter.activateParticles(startIndex, count)
    for (influencer <- this.influencers) {
      influencer.activateParticles(startIndex, count)
    }
  }
  def killParticles(startIndex: scala.Int, count: scala.Int): scala.Unit = {
    this.emitter.killParticles(startIndex, count)
    for (influencer <- this.influencers) {
      influencer.killParticles(startIndex, count)
    }
  }
  def update(): scala.Unit = {
    this.update(com.badlogic.gdx.Gdx.graphics.getDeltaTime())
  }
  def update(deltaTime: scala.Float): scala.Unit = {
    this.setTimeStep(deltaTime)
    this.emitter.update()
    for (influencer <- this.influencers) {
      influencer.update()
    }
  }
  def draw(): scala.Unit = {
    if (this.particles.size > 0) {
      this.renderer.update()
    } else ()
  }
  def copy(): ParticleController = {
    val emitter: com.badlogic.gdx.graphics.g3d.particles.emitters.Emitter = this.emitter.copy().asInstanceOf[com.badlogic.gdx.graphics.g3d.particles.emitters.Emitter]
    val influencers: scala.Array[com.badlogic.gdx.graphics.g3d.particles.influencers.Influencer] = new scala.Array[com.badlogic.gdx.graphics.g3d.particles.influencers.Influencer](this.influencers.size)
    var i: scala.Int = 0
    for (influencer <- this.influencers) {
      influencers({ i += 1; i }) = influencer.copy().asInstanceOf[com.badlogic.gdx.graphics.g3d.particles.influencers.Influencer]
    }
    return new ParticleController(new java.lang.String(this.name), emitter, this.renderer.copy().asInstanceOf[com.badlogic.gdx.graphics.g3d.particles.renderers.ParticleControllerRenderer[?, ?]], influencers)
  }
  def dispose(): scala.Unit = {
    this.emitter.dispose()
    for (influencer <- this.influencers) {
      influencer.dispose()
    }
  }
  def getBoundingBox(): com.badlogic.gdx.math.collision.BoundingBox = {
    if (this.boundingBox == null) {
      this.boundingBox = new com.badlogic.gdx.math.collision.BoundingBox()
    } else ()
    this.calculateBoundingBox()
    return this.boundingBox
  }
  def calculateBoundingBox(): scala.Unit = {
    this.boundingBox.clr()
    val positionChannel: com.badlogic.gdx.graphics.g3d.particles.ParallelArray#FloatChannel = this.particles.getChannel(com.badlogic.gdx.graphics.g3d.particles.ParticleChannels.Position);
    { var pos: scala.Int = 0; val c: scala.Int = positionChannel.strideSize * this.particles.size; while (pos < c) { {
      this.boundingBox.ext(positionChannel.data(pos + com.badlogic.gdx.graphics.g3d.particles.ParticleChannels.XOffset), positionChannel.data(pos + com.badlogic.gdx.graphics.g3d.particles.ParticleChannels.YOffset), positionChannel.data(pos + com.badlogic.gdx.graphics.g3d.particles.ParticleChannels.ZOffset))
    }; pos = pos + positionChannel.strideSize } }
  }
  private def findIndex[K <: com.badlogic.gdx.graphics.g3d.particles.influencers.Influencer](`type`: java.lang.Class[K]): scala.Int = {
    { var i: scala.Int = 0; while (i < this.influencers.size) { {
      val influencer: com.badlogic.gdx.graphics.g3d.particles.influencers.Influencer = this.influencers.get(i)
      if (com.badlogic.gdx.utils.reflect.ClassReflection.isAssignableFrom(`type`, influencer.getClass())) {
        return i
      } else ()
    }; i = i + 1 } }
    return -1
  }
  def findInfluencer[K <: com.badlogic.gdx.graphics.g3d.particles.influencers.Influencer](influencerClass: java.lang.Class[K]): K = {
    val index: scala.Int = this.findIndex(influencerClass)
    return if (index > (-1)) this.influencers.get(index).asInstanceOf[K] else null
  }
  def removeInfluencer[K <: com.badlogic.gdx.graphics.g3d.particles.influencers.Influencer](`type`: java.lang.Class[K]): scala.Unit = {
    val index: scala.Int = this.findIndex(`type`)
    if (index > (-1)) {
      this.influencers.removeIndex(index)
    } else ()
  }
  def replaceInfluencer[K <: com.badlogic.gdx.graphics.g3d.particles.influencers.Influencer](`type`: java.lang.Class[K], newInfluencer: K): scala.Boolean = {
    val index: scala.Int = this.findIndex(`type`)
    if (index > (-1)) {
      this.influencers.insert(index, newInfluencer)
      this.influencers.removeIndex(index + 1)
      return true
    } else ()
    return false
  }
  def write(json: com.badlogic.gdx.utils.Json): scala.Unit = {
    json.writeValue("name", this.name)
    json.writeValue("emitter", this.emitter, classOf[com.badlogic.gdx.graphics.g3d.particles.emitters.Emitter])
    json.writeValue("influencers", this.influencers, classOf[com.badlogic.gdx.utils.Array[?]], classOf[com.badlogic.gdx.graphics.g3d.particles.influencers.Influencer])
    json.writeValue("renderer", this.renderer, classOf[com.badlogic.gdx.graphics.g3d.particles.renderers.ParticleControllerRenderer[?, ?]])
  }
  def read(json: com.badlogic.gdx.utils.Json, jsonMap: com.badlogic.gdx.utils.JsonValue): scala.Unit = {
    this.name = json.readValue("name", classOf[java.lang.String], jsonMap)
    this.emitter = json.readValue("emitter", classOf[com.badlogic.gdx.graphics.g3d.particles.emitters.Emitter], jsonMap)
    this.influencers.addAll(json.readValue("influencers", classOf[com.badlogic.gdx.utils.Array[?]], classOf[com.badlogic.gdx.graphics.g3d.particles.influencers.Influencer], jsonMap))
    this.renderer = json.readValue("renderer", classOf[com.badlogic.gdx.graphics.g3d.particles.renderers.ParticleControllerRenderer[?, ?]], jsonMap)
  }
  def save(manager: com.badlogic.gdx.assets.AssetManager, data: com.badlogic.gdx.graphics.g3d.particles.ResourceData[?]): scala.Unit = {
    this.emitter.save(manager, data)
    for (influencer <- this.influencers) {
      influencer.save(manager, data)
    }
    this.renderer.save(manager, data)
  }
  def load(manager: com.badlogic.gdx.assets.AssetManager, data: com.badlogic.gdx.graphics.g3d.particles.ResourceData[?]): scala.Unit = {
    this.emitter.load(manager, data)
    for (influencer <- this.influencers) {
      influencer.load(manager, data)
    }
    this.renderer.load(manager, data)
  }
}
object ParticleController {
  final val DEFAULT_TIME_STEP: scala.Float = 1.0f / 60
}