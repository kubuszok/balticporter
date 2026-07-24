package com.badlogic.gdx.graphics.g3d.particles.influencers

class SpawnInfluencer extends com.badlogic.gdx.graphics.g3d.particles.influencers.Influencer {
  var spawnShapeValue: com.badlogic.gdx.graphics.g3d.particles.values.SpawnShapeValue = null.asInstanceOf[com.badlogic.gdx.graphics.g3d.particles.values.SpawnShapeValue]
  var positionChannel: com.badlogic.gdx.graphics.g3d.particles.ParallelArray#FloatChannel = null.asInstanceOf[com.badlogic.gdx.graphics.g3d.particles.ParallelArray#FloatChannel]
  var rotationChannel: com.badlogic.gdx.graphics.g3d.particles.ParallelArray#FloatChannel = null.asInstanceOf[com.badlogic.gdx.graphics.g3d.particles.ParallelArray#FloatChannel]
  def this(spawnShapeValue: com.badlogic.gdx.graphics.g3d.particles.values.SpawnShapeValue) = {
    this()
    this.spawnShapeValue = spawnShapeValue
  }
  def this(source: SpawnInfluencer) = {
    this()
    this.spawnShapeValue = source.spawnShapeValue.copy()
  }
  this.spawnShapeValue = new com.badlogic.gdx.graphics.g3d.particles.values.PointSpawnShapeValue()
  def init(): scala.Unit = {
    this.spawnShapeValue.init()
  }
  def allocateChannels(): scala.Unit = {
    this.positionChannel = this.controller.particles.addChannel(com.badlogic.gdx.graphics.g3d.particles.ParticleChannels.Position)
    this.rotationChannel = this.controller.particles.addChannel(com.badlogic.gdx.graphics.g3d.particles.ParticleChannels.Rotation3D)
  }
  def start(): scala.Unit = {
    this.spawnShapeValue.start()
  }
  def activateParticles(startIndex: scala.Int, count: scala.Int): scala.Unit = {
    { var i: scala.Int = startIndex * this.positionChannel.strideSize; val c: scala.Int = i + (count * this.positionChannel.strideSize); while (i < c) { {
      this.spawnShapeValue.spawn(com.badlogic.gdx.graphics.g3d.particles.ParticleControllerComponent.TMP_V1, this.controller.emitter.percent)
      com.badlogic.gdx.graphics.g3d.particles.ParticleControllerComponent.TMP_V1.mul(this.controller.transform)
      this.positionChannel.data(i + com.badlogic.gdx.graphics.g3d.particles.ParticleChannels.XOffset) = SpawnInfluencer.TMP_V1.x
      this.positionChannel.data(i + com.badlogic.gdx.graphics.g3d.particles.ParticleChannels.YOffset) = SpawnInfluencer.TMP_V1.y
      this.positionChannel.data(i + com.badlogic.gdx.graphics.g3d.particles.ParticleChannels.ZOffset) = SpawnInfluencer.TMP_V1.z
    }; i = i + this.positionChannel.strideSize } };
    { var i: scala.Int = startIndex * this.rotationChannel.strideSize; val c: scala.Int = i + (count * this.rotationChannel.strideSize); while (i < c) { {
      this.controller.transform.getRotation(com.badlogic.gdx.graphics.g3d.particles.ParticleControllerComponent.TMP_Q, true)
      this.rotationChannel.data(i + com.badlogic.gdx.graphics.g3d.particles.ParticleChannels.XOffset) = SpawnInfluencer.TMP_Q.x
      this.rotationChannel.data(i + com.badlogic.gdx.graphics.g3d.particles.ParticleChannels.YOffset) = SpawnInfluencer.TMP_Q.y
      this.rotationChannel.data(i + com.badlogic.gdx.graphics.g3d.particles.ParticleChannels.ZOffset) = SpawnInfluencer.TMP_Q.z
      this.rotationChannel.data(i + com.badlogic.gdx.graphics.g3d.particles.ParticleChannels.WOffset) = SpawnInfluencer.TMP_Q.w
    }; i = i + this.rotationChannel.strideSize } }
  }
  def copy(): SpawnInfluencer = {
    return new SpawnInfluencer(this)
  }
  def write(json: com.badlogic.gdx.utils.Json): scala.Unit = {
    json.writeValue("spawnShape", this.spawnShapeValue, classOf[com.badlogic.gdx.graphics.g3d.particles.values.SpawnShapeValue])
  }
  def read(json: com.badlogic.gdx.utils.Json, jsonData: com.badlogic.gdx.utils.JsonValue): scala.Unit = {
    this.spawnShapeValue = json.readValue("spawnShape", classOf[com.badlogic.gdx.graphics.g3d.particles.values.SpawnShapeValue], jsonData)
  }
  def save(manager: com.badlogic.gdx.assets.AssetManager, data: com.badlogic.gdx.graphics.g3d.particles.ResourceData[?]): scala.Unit = {
    this.spawnShapeValue.save(manager, data)
  }
  def load(manager: com.badlogic.gdx.assets.AssetManager, data: com.badlogic.gdx.graphics.g3d.particles.ResourceData[?]): scala.Unit = {
    this.spawnShapeValue.load(manager, data)
  }
}