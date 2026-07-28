package com.badlogic.gdx.graphics.g3d.particles.influencers

abstract class ModelInfluencer extends com.badlogic.gdx.graphics.g3d.particles.influencers.Influencer {
  var models: com.badlogic.gdx.utils.Array[com.badlogic.gdx.graphics.g3d.Model] = null.asInstanceOf[com.badlogic.gdx.utils.Array[com.badlogic.gdx.graphics.g3d.Model]]
  var modelChannel: com.badlogic.gdx.graphics.g3d.particles.ParallelArray#ObjectChannel[com.badlogic.gdx.graphics.g3d.ModelInstance] = null.asInstanceOf[com.badlogic.gdx.graphics.g3d.particles.ParallelArray#ObjectChannel[com.badlogic.gdx.graphics.g3d.ModelInstance]]
  def this(models: scala.Array[com.badlogic.gdx.graphics.g3d.Model]) = {
    this()
    this.models = new com.badlogic.gdx.utils.Array[com.badlogic.gdx.graphics.g3d.Model](models)
  }
  def this(influencer: ModelInfluencer) = {
    this(influencer.models.toArray(((size: scala.Int) => new scala.Array[com.badlogic.gdx.graphics.g3d.Model](size))))
  }
  this.models = new com.badlogic.gdx.utils.Array[com.badlogic.gdx.graphics.g3d.Model](true, 1, ((size: scala.Int) => new scala.Array[com.badlogic.gdx.graphics.g3d.Model](size)))
  @java.lang.Override
  override def allocateChannels(): scala.Unit = {
    this.modelChannel = this.controller.particles.addChannel(com.badlogic.gdx.graphics.g3d.particles.ParticleChannels.ModelInstance)
  }
  @java.lang.Override
  override def save(manager: com.badlogic.gdx.assets.AssetManager, resources: com.badlogic.gdx.graphics.g3d.particles.ResourceData[?]): scala.Unit = {
    val data: com.badlogic.gdx.graphics.g3d.particles.ResourceData.SaveData = resources.createSaveData()
    for (model <- this.models) {
      data.saveAsset(manager.getAssetFileName(model), classOf[com.badlogic.gdx.graphics.g3d.Model])
    }
  }
  @java.lang.Override
  override def load(manager: com.badlogic.gdx.assets.AssetManager, resources: com.badlogic.gdx.graphics.g3d.particles.ResourceData[?]): scala.Unit = {
    val data: com.badlogic.gdx.graphics.g3d.particles.ResourceData.SaveData = resources.getSaveData()
    var descriptor: com.badlogic.gdx.assets.AssetDescriptor[?] = null.asInstanceOf[com.badlogic.gdx.assets.AssetDescriptor[?]]
    while ({
      descriptor = data.loadAsset().asInstanceOf[com.badlogic.gdx.assets.AssetDescriptor[?]]
      descriptor
    } != null) {
      val model: com.badlogic.gdx.graphics.g3d.Model = manager.get(descriptor).asInstanceOf[com.badlogic.gdx.graphics.g3d.Model].asInstanceOf[com.badlogic.gdx.graphics.g3d.Model]
      if (model == null) {
        throw new java.lang.RuntimeException("Model is null")
      } else ()
      this.models.add(model)
    }
  }
}
object ModelInfluencer {
  export com.badlogic.gdx.graphics.g3d.particles.influencers.Influencer.{Random => _, Single => _, *}
  class Single extends ModelInfluencer {
    def this(influencer: com.badlogic.gdx.graphics.g3d.particles.influencers.ModelInfluencer.Single) = {
      this()
      this.models = new com.badlogic.gdx.utils.Array[com.badlogic.gdx.graphics.g3d.Model](influencer.models.toArray(((size: scala.Int) => new scala.Array[com.badlogic.gdx.graphics.g3d.Model](size))))
    }
    def this(models: scala.Array[com.badlogic.gdx.graphics.g3d.Model]) = {
      this()
      this.models = new com.badlogic.gdx.utils.Array[com.badlogic.gdx.graphics.g3d.Model](models)
    }
    @java.lang.Override
    override def init(): scala.Unit = {
      val first: com.badlogic.gdx.graphics.g3d.Model = models.first();
      { var i: scala.Int = 0; val c: scala.Int = this.controller.emitter.maxParticleCount; while (i < c) { {
        this.modelChannel.data(i) = new com.badlogic.gdx.graphics.g3d.ModelInstance(first)
      }; i = i + 1 } }
    }
    @java.lang.Override
    override def copy(): com.badlogic.gdx.graphics.g3d.particles.influencers.ModelInfluencer.Single = {
      return new com.badlogic.gdx.graphics.g3d.particles.influencers.ModelInfluencer.Single(this)
    }
  }
  object Single {
    export ModelInfluencer.*
  }
  class Random extends ModelInfluencer {
    var pool: com.badlogic.gdx.graphics.g3d.particles.influencers.ModelInfluencer.Random#ModelInstancePool = null.asInstanceOf[com.badlogic.gdx.graphics.g3d.particles.influencers.ModelInfluencer.Random#ModelInstancePool]
    def this(influencer: com.badlogic.gdx.graphics.g3d.particles.influencers.ModelInfluencer.Random) = {
      this()
      this.models = new com.badlogic.gdx.utils.Array[com.badlogic.gdx.graphics.g3d.Model](influencer.models.toArray(((size: scala.Int) => new scala.Array[com.badlogic.gdx.graphics.g3d.Model](size))))
      this.pool = new ModelInstancePool()
    }
    def this(models: scala.Array[com.badlogic.gdx.graphics.g3d.Model]) = {
      this()
      this.models = new com.badlogic.gdx.utils.Array[com.badlogic.gdx.graphics.g3d.Model](models)
      this.pool = new ModelInstancePool()
    }
    this.pool = new ModelInstancePool()
    @java.lang.Override
    override def init(): scala.Unit = {
      this.pool.clear()
    }
    @java.lang.Override
    override def activateParticles(startIndex: scala.Int, count: scala.Int): scala.Unit = {
      { var i: scala.Int = startIndex; val c: scala.Int = startIndex + count; while (i < c) { {
        this.modelChannel.data(i) = this.pool.obtain()
      }; i = i + 1 } }
    }
    @java.lang.Override
    override def killParticles(startIndex: scala.Int, count: scala.Int): scala.Unit = {
      { var i: scala.Int = startIndex; val c: scala.Int = startIndex + count; while (i < c) { {
        this.pool.free(this.modelChannel.data(i))
        this.modelChannel.data(i) = null
      }; i = i + 1 } }
    }
    @java.lang.Override
    override def copy(): com.badlogic.gdx.graphics.g3d.particles.influencers.ModelInfluencer.Random = {
      return new com.badlogic.gdx.graphics.g3d.particles.influencers.ModelInfluencer.Random(this)
    }
    class ModelInstancePool extends com.badlogic.gdx.utils.Pool[com.badlogic.gdx.graphics.g3d.ModelInstance] {
      @java.lang.Override
      override def newObject(): com.badlogic.gdx.graphics.g3d.ModelInstance = {
        return new com.badlogic.gdx.graphics.g3d.ModelInstance(models.random())
      }
    }
    object ModelInstancePool {
      export com.badlogic.gdx.utils.Pool.*
    }
  }
  object Random {
    export ModelInfluencer.*
  }
}