package com.badlogic.gdx.graphics.g3d.particles.influencers

abstract class ParticleControllerInfluencer extends com.badlogic.gdx.graphics.g3d.particles.influencers.Influencer {
  var templates: com.badlogic.gdx.utils.Array[com.badlogic.gdx.graphics.g3d.particles.ParticleController] = null.asInstanceOf[com.badlogic.gdx.utils.Array[com.badlogic.gdx.graphics.g3d.particles.ParticleController]]
  var particleControllerChannel: com.badlogic.gdx.graphics.g3d.particles.ParallelArray#ObjectChannel[com.badlogic.gdx.graphics.g3d.particles.ParticleController] = null.asInstanceOf[com.badlogic.gdx.graphics.g3d.particles.ParallelArray#ObjectChannel[com.badlogic.gdx.graphics.g3d.particles.ParticleController]]
  def this(templates: scala.Array[com.badlogic.gdx.graphics.g3d.particles.ParticleController]) = {
    this()
    this.templates = new com.badlogic.gdx.utils.Array[com.badlogic.gdx.graphics.g3d.particles.ParticleController](templates)
  }
  def this(influencer: ParticleControllerInfluencer) = {
    this(influencer.templates.items)
  }
  this.templates = new com.badlogic.gdx.utils.Array[com.badlogic.gdx.graphics.g3d.particles.ParticleController](true, 1, ((size: scala.Int) => new scala.Array[com.badlogic.gdx.graphics.g3d.particles.ParticleController](size)))
  def allocateChannels(): scala.Unit = {
    this.particleControllerChannel = this.controller.particles.addChannel(com.badlogic.gdx.graphics.g3d.particles.ParticleChannels.ParticleController)
  }
  def `end`(): scala.Unit = {
    { var i: scala.Int = 0; while (i < this.controller.particles.size) { {
      this.particleControllerChannel.data(i).`end`()
    }; i = i + 1 } }
  }
  def dispose(): scala.Unit = {
    if (controller != null) {
      { var i: scala.Int = 0; while (i < this.controller.particles.size) { {
        val controller: com.badlogic.gdx.graphics.g3d.particles.ParticleController = this.particleControllerChannel.data(i)
        if (controller != null) {
          controller.dispose()
          this.particleControllerChannel.data(i) = null
        } else ()
      }; i = i + 1 } }
    } else ()
  }
  def save(manager: com.badlogic.gdx.assets.AssetManager, resources: com.badlogic.gdx.graphics.g3d.particles.ResourceData[?]): scala.Unit = {
    val data: com.badlogic.gdx.graphics.g3d.particles.ResourceData.SaveData = resources.createSaveData()
    val effects: com.badlogic.gdx.utils.Array[com.badlogic.gdx.graphics.g3d.particles.ParticleEffect] = manager.getAll(classOf[com.badlogic.gdx.graphics.g3d.particles.ParticleEffect], new com.badlogic.gdx.utils.Array[com.badlogic.gdx.graphics.g3d.particles.ParticleEffect]())
    val controllers: com.badlogic.gdx.utils.Array[com.badlogic.gdx.graphics.g3d.particles.ParticleController] = new com.badlogic.gdx.utils.Array[com.badlogic.gdx.graphics.g3d.particles.ParticleController](this.templates)
    val effectsIndices: com.badlogic.gdx.utils.Array[com.badlogic.gdx.utils.IntArray] = new com.badlogic.gdx.utils.Array[com.badlogic.gdx.utils.IntArray]();
    { var i: scala.Int = 0; while ((i < effects.size) && (controllers.size > 0)) { {
      val effect: com.badlogic.gdx.graphics.g3d.particles.ParticleEffect = effects.get(i)
      val effectControllers: com.badlogic.gdx.utils.Array[com.badlogic.gdx.graphics.g3d.particles.ParticleController] = effect.getControllers()
      val iterator: scala.collection.Iterator[com.badlogic.gdx.graphics.g3d.particles.ParticleController] = controllers.iterator()
      var indices: com.badlogic.gdx.utils.IntArray = null
      while (iterator.hasNext) {
        val controller: com.badlogic.gdx.graphics.g3d.particles.ParticleController = iterator.next
        var index: scala.Int = -1
        if ({
          index = effectControllers.indexOf(controller, true)
          index
        } > (-1)) {
          if (indices == null) {
            indices = new com.badlogic.gdx.utils.IntArray()
          } else ()
          iterator.remove()
          indices.add(index)
        } else ()
      }
      if (indices != null) {
        data.saveAsset(manager.getAssetFileName(effect), classOf[com.badlogic.gdx.graphics.g3d.particles.ParticleEffect])
        effectsIndices.add(indices)
      } else ()
    }; i = i + 1 } }
    data.save("indices", effectsIndices)
  }
  def load(manager: com.badlogic.gdx.assets.AssetManager, resources: com.badlogic.gdx.graphics.g3d.particles.ResourceData[?]): scala.Unit = {
    val data: com.badlogic.gdx.graphics.g3d.particles.ResourceData.SaveData = resources.getSaveData()
    val effectsIndices: com.badlogic.gdx.utils.Array[com.badlogic.gdx.utils.IntArray] = data.load("indices")
    var descriptor: com.badlogic.gdx.assets.AssetDescriptor[?] = null.asInstanceOf[com.badlogic.gdx.assets.AssetDescriptor[?]]
    val iterator: scala.collection.Iterator[com.badlogic.gdx.utils.IntArray] = effectsIndices.iterator()
    while ({
      descriptor = data.loadAsset()
      descriptor
    } != null) {
      val effect: com.badlogic.gdx.graphics.g3d.particles.ParticleEffect = manager.get(descriptor).asInstanceOf[com.badlogic.gdx.graphics.g3d.particles.ParticleEffect].asInstanceOf[com.badlogic.gdx.graphics.g3d.particles.ParticleEffect]
      if (effect == null) {
        throw new java.lang.RuntimeException("Template is null")
      } else ()
      val effectControllers: com.badlogic.gdx.utils.Array[com.badlogic.gdx.graphics.g3d.particles.ParticleController] = effect.getControllers()
      val effectIndices: com.badlogic.gdx.utils.IntArray = iterator.next;
      { var i: scala.Int = 0; val n: scala.Int = effectIndices.size; while (i < n) { {
        this.templates.add(effectControllers.get(effectIndices.get(i)))
      }; i = i + 1 } }
    }
  }
}
object ParticleControllerInfluencer {
  export com.badlogic.gdx.graphics.g3d.particles.influencers.Influencer.{Single => _, Random => _, *}
  class Single extends ParticleControllerInfluencer {
    def this(templates: scala.Array[com.badlogic.gdx.graphics.g3d.particles.ParticleController]) = {
      this()
      this.templates = new com.badlogic.gdx.utils.Array[com.badlogic.gdx.graphics.g3d.particles.ParticleController](templates)
    }
    def this(particleControllerSingle: com.badlogic.gdx.graphics.g3d.particles.influencers.ParticleControllerInfluencer.Single) = {
      this()
      this.templates = new com.badlogic.gdx.utils.Array[com.badlogic.gdx.graphics.g3d.particles.ParticleController](particleControllerSingle.templates.items)
    }
    def init(): scala.Unit = {
      val first: com.badlogic.gdx.graphics.g3d.particles.ParticleController = templates.first();
      { var i: scala.Int = 0; val c: scala.Int = this.controller.particles.capacity; while (i < c) { {
        val copy: com.badlogic.gdx.graphics.g3d.particles.ParticleController = first.copy()
        copy.init()
        this.particleControllerChannel.data(i) = copy
      }; i = i + 1 } }
    }
    def activateParticles(startIndex: scala.Int, count: scala.Int): scala.Unit = {
      { var i: scala.Int = startIndex; val c: scala.Int = startIndex + count; while (i < c) { {
        this.particleControllerChannel.data(i).start()
      }; i = i + 1 } }
    }
    def killParticles(startIndex: scala.Int, count: scala.Int): scala.Unit = {
      { var i: scala.Int = startIndex; val c: scala.Int = startIndex + count; while (i < c) { {
        this.particleControllerChannel.data(i).`end`()
      }; i = i + 1 } }
    }
    def copy(): com.badlogic.gdx.graphics.g3d.particles.influencers.ParticleControllerInfluencer.Single = {
      return new com.badlogic.gdx.graphics.g3d.particles.influencers.ParticleControllerInfluencer.Single(this)
    }
  }
  object Single {
    export ParticleControllerInfluencer.*
  }
  class Random extends ParticleControllerInfluencer {
    var pool: com.badlogic.gdx.graphics.g3d.particles.influencers.ParticleControllerInfluencer.Random#ParticleControllerPool = null.asInstanceOf[com.badlogic.gdx.graphics.g3d.particles.influencers.ParticleControllerInfluencer.Random#ParticleControllerPool]
    def this(templates: scala.Array[com.badlogic.gdx.graphics.g3d.particles.ParticleController]) = {
      this()
      this.templates = new com.badlogic.gdx.utils.Array[com.badlogic.gdx.graphics.g3d.particles.ParticleController](templates)
      this.pool = new ParticleControllerPool()
    }
    def this(particleControllerRandom: com.badlogic.gdx.graphics.g3d.particles.influencers.ParticleControllerInfluencer.Random) = {
      this()
      this.templates = new com.badlogic.gdx.utils.Array[com.badlogic.gdx.graphics.g3d.particles.ParticleController](particleControllerRandom.templates.items)
      this.pool = new ParticleControllerPool()
    }
    this.pool = new ParticleControllerPool()
    def init(): scala.Unit = {
      this.pool.clear();
      { var i: scala.Int = 0; while (i < this.controller.emitter.maxParticleCount) { {
        this.pool.free(this.pool.newObject())
      }; i = i + 1 } }
    }
    def dispose(): scala.Unit = {
      this.pool.clear()
      super.dispose()
    }
    def activateParticles(startIndex: scala.Int, count: scala.Int): scala.Unit = {
      { var i: scala.Int = startIndex; val c: scala.Int = startIndex + count; while (i < c) { {
        val controller: com.badlogic.gdx.graphics.g3d.particles.ParticleController = this.pool.obtain()
        controller.start()
        this.particleControllerChannel.data(i) = controller
      }; i = i + 1 } }
    }
    def killParticles(startIndex: scala.Int, count: scala.Int): scala.Unit = {
      { var i: scala.Int = startIndex; val c: scala.Int = startIndex + count; while (i < c) { {
        val controller: com.badlogic.gdx.graphics.g3d.particles.ParticleController = this.particleControllerChannel.data(i)
        controller.`end`()
        this.pool.free(controller)
        this.particleControllerChannel.data(i) = null
      }; i = i + 1 } }
    }
    def copy(): com.badlogic.gdx.graphics.g3d.particles.influencers.ParticleControllerInfluencer.Random = {
      return new com.badlogic.gdx.graphics.g3d.particles.influencers.ParticleControllerInfluencer.Random(this)
    }
    class ParticleControllerPool extends com.badlogic.gdx.utils.Pool[com.badlogic.gdx.graphics.g3d.particles.ParticleController] {
      def newObject(): com.badlogic.gdx.graphics.g3d.particles.ParticleController = {
        val controller: com.badlogic.gdx.graphics.g3d.particles.ParticleController = templates.random().copy()
        controller.init()
        return controller
      }
      def clear(): scala.Unit = {
        { var i: scala.Int = 0; val free: scala.Int = Random.this.pool.getFree(); while (i < free) { {
          Random.this.pool.obtain().dispose()
        }; i = i + 1 } }
        super.clear()
      }
    }
    object ParticleControllerPool {
      export com.badlogic.gdx.utils.Pool.*
    }
  }
  object Random {
    export ParticleControllerInfluencer.*
  }
}