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
  @java.lang.Override
  def init(): scala.Unit = {
    this.spawnShapeValue.init()
  }
  @java.lang.Override
  def allocateChannels(): scala.Unit = {
    this.positionChannel = this.controller.particles.addChannel(com.badlogic.gdx.graphics.g3d.particles.ParticleChannels.Position)
    this.rotationChannel = this.controller.particles.addChannel(com.badlogic.gdx.graphics.g3d.particles.ParticleChannels.Rotation3D)
  }
  @java.lang.Override
  def start(): scala.Unit = {
    this.spawnShapeValue.start()
  }
  @java.lang.Override
  def activateParticles(startIndex: scala.Int, count: scala.Int): scala.Unit = {
    { var i: scala.Int = startIndex * this.positionChannel.strideSize; val c: scala.Int = i + (count * this.positionChannel.strideSize); while (i < c) { {
      this.spawnShapeValue.spawn(com.badlogic.gdx.graphics.g3d.particles.ParticleControllerComponent.TMP_V1, this.controller.emitter.percent)
      com.badlogic.gdx.graphics.g3d.particles.ParticleControllerComponent.TMP_V1.mul(this.controller.transform)
      this.positionChannel.data(i + com.badlogic.gdx.graphics.g3d.particles.ParticleChannels.XOffset) = com.badlogic.gdx.graphics.g3d.particles.ParticleControllerComponent.TMP_V1.x
      this.positionChannel.data(i + com.badlogic.gdx.graphics.g3d.particles.ParticleChannels.YOffset) = com.badlogic.gdx.graphics.g3d.particles.ParticleControllerComponent.TMP_V1.y
      this.positionChannel.data(i + com.badlogic.gdx.graphics.g3d.particles.ParticleChannels.ZOffset) = com.badlogic.gdx.graphics.g3d.particles.ParticleControllerComponent.TMP_V1.z
    }; i = i + this.positionChannel.strideSize } };
    { var i: scala.Int = startIndex * this.rotationChannel.strideSize; val c: scala.Int = i + (count * this.rotationChannel.strideSize); while (i < c) { {
      this.controller.transform.getRotation(com.badlogic.gdx.graphics.g3d.particles.ParticleControllerComponent.TMP_Q, true)
      this.rotationChannel.data(i + com.badlogic.gdx.graphics.g3d.particles.ParticleChannels.XOffset) = com.badlogic.gdx.graphics.g3d.particles.ParticleControllerComponent.TMP_Q.x
      this.rotationChannel.data(i + com.badlogic.gdx.graphics.g3d.particles.ParticleChannels.YOffset) = com.badlogic.gdx.graphics.g3d.particles.ParticleControllerComponent.TMP_Q.y
      this.rotationChannel.data(i + com.badlogic.gdx.graphics.g3d.particles.ParticleChannels.ZOffset) = com.badlogic.gdx.graphics.g3d.particles.ParticleControllerComponent.TMP_Q.z
      this.rotationChannel.data(i + com.badlogic.gdx.graphics.g3d.particles.ParticleChannels.WOffset) = com.badlogic.gdx.graphics.g3d.particles.ParticleControllerComponent.TMP_Q.w
    }; i = i + this.rotationChannel.strideSize } }
  }
  @java.lang.Override
  def copy(): SpawnInfluencer = {
    return new SpawnInfluencer(this)
  }
  @java.lang.Override
  def write(json: com.badlogic.gdx.utils.Json): scala.Unit = {
    json.writeValue("spawnShape", this.spawnShapeValue, classOf[com.badlogic.gdx.graphics.g3d.particles.values.SpawnShapeValue])
  }
  @java.lang.Override
  def read(json: com.badlogic.gdx.utils.Json, jsonData: com.badlogic.gdx.utils.JsonValue): scala.Unit = {
    this.spawnShapeValue = json.readValue("spawnShape", classOf[com.badlogic.gdx.graphics.g3d.particles.values.SpawnShapeValue], jsonData)
  }
  @java.lang.Override
  def save(manager: com.badlogic.gdx.assets.AssetManager, data: com.badlogic.gdx.graphics.g3d.particles.ResourceData[?]): scala.Unit = {
    this.spawnShapeValue.save(manager, data.asInstanceOf[com.badlogic.gdx.graphics.g3d.particles.ResourceData[?]])
  }
  @java.lang.Override
  def load(manager: com.badlogic.gdx.assets.AssetManager, data: com.badlogic.gdx.graphics.g3d.particles.ResourceData[?]): scala.Unit = {
    this.spawnShapeValue.load(manager, data.asInstanceOf[com.badlogic.gdx.graphics.g3d.particles.ResourceData[?]])
  }
}
object SpawnInfluencer {
  export com.badlogic.gdx.graphics.g3d.particles.influencers.Influencer.*
}