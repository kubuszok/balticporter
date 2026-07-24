package com.badlogic.gdx.graphics.g3d.particles

class ParticleEffect extends com.badlogic.gdx.utils.Disposable with com.badlogic.gdx.graphics.g3d.particles.ResourceData#Configurable {
  private var controllers: com.badlogic.gdx.utils.Array[com.badlogic.gdx.graphics.g3d.particles.ParticleController] = null.asInstanceOf[com.badlogic.gdx.utils.Array[com.badlogic.gdx.graphics.g3d.particles.ParticleController]]
  private var bounds: com.badlogic.gdx.math.collision.BoundingBox = null.asInstanceOf[com.badlogic.gdx.math.collision.BoundingBox]
  def this(effect: ParticleEffect) = {
    this()
    this.controllers = new com.badlogic.gdx.utils.Array[com.badlogic.gdx.graphics.g3d.particles.ParticleController](true, effect.controllers.size)
    { var i: scala.Int = 0; val n: scala.Int = effect.controllers.size; while (i < n) { {
      this.controllers.add(effect.controllers.get(i).copy())
    }; i = i + 1 } }
  }
  def this(emitters: scala.Array[com.badlogic.gdx.graphics.g3d.particles.ParticleController]) = {
    this()
    this.controllers = new com.badlogic.gdx.utils.Array[com.badlogic.gdx.graphics.g3d.particles.ParticleController](emitters)
  }
  def this() = {
    this()
    this.controllers = new com.badlogic.gdx.utils.Array[com.badlogic.gdx.graphics.g3d.particles.ParticleController](true, 3, scala.Array[com.badlogic.gdx.graphics.g3d.particles.ParticleController].<init>)
  }
  def init(): scala.Unit = {
    { var i: scala.Int = 0; val n: scala.Int = this.controllers.size; while (i < n) { {
      this.controllers.get(i).init()
    }; i = i + 1 } }
  }
  def start(): scala.Unit = {
    { var i: scala.Int = 0; val n: scala.Int = this.controllers.size; while (i < n) { {
      this.controllers.get(i).start()
    }; i = i + 1 } }
  }
  def `end`(): scala.Unit = {
    { var i: scala.Int = 0; val n: scala.Int = this.controllers.size; while (i < n) { {
      this.controllers.get(i).`end`()
    }; i = i + 1 } }
  }
  def reset(): scala.Unit = {
    { var i: scala.Int = 0; val n: scala.Int = this.controllers.size; while (i < n) { {
      this.controllers.get(i).reset()
    }; i = i + 1 } }
  }
  def update(): scala.Unit = {
    { var i: scala.Int = 0; val n: scala.Int = this.controllers.size; while (i < n) { {
      this.controllers.get(i).update()
    }; i = i + 1 } }
  }
  def update(deltaTime: scala.Float): scala.Unit = {
    { var i: scala.Int = 0; val n: scala.Int = this.controllers.size; while (i < n) { {
      this.controllers.get(i).update(deltaTime)
    }; i = i + 1 } }
  }
  def draw(): scala.Unit = {
    { var i: scala.Int = 0; val n: scala.Int = this.controllers.size; while (i < n) { {
      this.controllers.get(i).draw()
    }; i = i + 1 } }
  }
  def isComplete(): scala.Boolean = {
    { var i: scala.Int = 0; val n: scala.Int = this.controllers.size; while (i < n) { {
      if (!this.controllers.get(i).isComplete()) {
        return false
      } else ()
    }; i = i + 1 } }
    return true
  }
  def setTransform(transform: com.badlogic.gdx.math.Matrix4): scala.Unit = {
    { var i: scala.Int = 0; val n: scala.Int = this.controllers.size; while (i < n) { {
      this.controllers.get(i).setTransform(transform)
    }; i = i + 1 } }
  }
  def rotate(rotation: com.badlogic.gdx.math.Quaternion): scala.Unit = {
    { var i: scala.Int = 0; val n: scala.Int = this.controllers.size; while (i < n) { {
      this.controllers.get(i).rotate(rotation)
    }; i = i + 1 } }
  }
  def rotate(axis: com.badlogic.gdx.math.Vector3, angle: scala.Float): scala.Unit = {
    { var i: scala.Int = 0; val n: scala.Int = this.controllers.size; while (i < n) { {
      this.controllers.get(i).rotate(axis, angle)
    }; i = i + 1 } }
  }
  def translate(translation: com.badlogic.gdx.math.Vector3): scala.Unit = {
    { var i: scala.Int = 0; val n: scala.Int = this.controllers.size; while (i < n) { {
      this.controllers.get(i).translate(translation)
    }; i = i + 1 } }
  }
  def scale(scaleX: scala.Float, scaleY: scala.Float, scaleZ: scala.Float): scala.Unit = {
    { var i: scala.Int = 0; val n: scala.Int = this.controllers.size; while (i < n) { {
      this.controllers.get(i).scale(scaleX, scaleY, scaleZ)
    }; i = i + 1 } }
  }
  def scale(scale: com.badlogic.gdx.math.Vector3): scala.Unit = {
    { var i: scala.Int = 0; val n: scala.Int = this.controllers.size; while (i < n) { {
      this.controllers.get(i).scale(scale.x, scale.y, scale.z)
    }; i = i + 1 } }
  }
  def getControllers(): com.badlogic.gdx.utils.Array[com.badlogic.gdx.graphics.g3d.particles.ParticleController] = {
    return this.controllers
  }
  def findController(name: java.lang.String): com.badlogic.gdx.graphics.g3d.particles.ParticleController = {
    { var i: scala.Int = 0; val n: scala.Int = this.controllers.size; while (i < n) { {
      val emitter: com.badlogic.gdx.graphics.g3d.particles.ParticleController = this.controllers.get(i)
      if (emitter.name.equals(name)) {
        return emitter
      } else ()
    }; i = i + 1 } }
    return null
  }
  def dispose(): scala.Unit = {
    { var i: scala.Int = 0; val n: scala.Int = this.controllers.size; while (i < n) { {
      this.controllers.get(i).dispose()
    }; i = i + 1 } }
  }
  def getBoundingBox(): com.badlogic.gdx.math.collision.BoundingBox = {
    if (this.bounds == null) {
      this.bounds = new com.badlogic.gdx.math.collision.BoundingBox()
    } else ()
    var bounds: com.badlogic.gdx.math.collision.BoundingBox = this.bounds
    bounds.inf()
    for (emitter <- this.controllers) {
      bounds.ext(emitter.getBoundingBox())
    }
    return bounds
  }
  def setBatch(batches: com.badlogic.gdx.utils.Array[com.badlogic.gdx.graphics.g3d.particles.batches.ParticleBatch[?]]): scala.Unit = {
    for (controller <- this.controllers) {
      for (batch <- batches) {
        if (controller.renderer.setBatch(batch)) {
          /* break */ ()
        } else ()
      }
    }
  }
  def copy(): ParticleEffect = {
    return new ParticleEffect(this)
  }
  def save(assetManager: com.badlogic.gdx.assets.AssetManager, data: com.badlogic.gdx.graphics.g3d.particles.ResourceData): scala.Unit = {
    for (controller <- this.controllers) {
      controller.save(assetManager, data)
    }
  }
  def load(assetManager: com.badlogic.gdx.assets.AssetManager, data: com.badlogic.gdx.graphics.g3d.particles.ResourceData): scala.Unit = {
    val i: scala.Int = 0
    for (controller <- this.controllers) {
      controller.load(assetManager, data)
    }
  }
}