package com.badlogic.gdx.graphics.g3d.particles

final class ParticleSystem extends com.badlogic.gdx.graphics.g3d.RenderableProvider {
  private var batches: com.badlogic.gdx.utils.Array[com.badlogic.gdx.graphics.g3d.particles.batches.ParticleBatch[?]] = null.asInstanceOf[com.badlogic.gdx.utils.Array[com.badlogic.gdx.graphics.g3d.particles.batches.ParticleBatch[?]]]
  private var effects: com.badlogic.gdx.utils.Array[com.badlogic.gdx.graphics.g3d.particles.ParticleEffect] = null.asInstanceOf[com.badlogic.gdx.utils.Array[com.badlogic.gdx.graphics.g3d.particles.ParticleEffect]]
  this.batches = new com.badlogic.gdx.utils.Array[com.badlogic.gdx.graphics.g3d.particles.batches.ParticleBatch[?]]()
  this.effects = new com.badlogic.gdx.utils.Array[com.badlogic.gdx.graphics.g3d.particles.ParticleEffect]()
  def add(batch: com.badlogic.gdx.graphics.g3d.particles.batches.ParticleBatch[?]): scala.Unit = {
    this.batches.add(batch)
  }
  def add(effect: com.badlogic.gdx.graphics.g3d.particles.ParticleEffect): scala.Unit = {
    this.effects.add(effect)
  }
  def remove(effect: com.badlogic.gdx.graphics.g3d.particles.ParticleEffect): scala.Unit = {
    this.effects.removeValue(effect, true)
  }
  def removeAll(): scala.Unit = {
    this.effects.clear()
  }
  def update(): scala.Unit = {
    for (effect <- this.effects) {
      effect.update()
    }
  }
  def updateAndDraw(): scala.Unit = {
    for (effect <- this.effects) {
      effect.update()
      effect.draw()
    }
  }
  def update(deltaTime: scala.Float): scala.Unit = {
    for (effect <- this.effects) {
      effect.update(deltaTime)
    }
  }
  def updateAndDraw(deltaTime: scala.Float): scala.Unit = {
    for (effect <- this.effects) {
      effect.update(deltaTime)
      effect.draw()
    }
  }
  def begin(): scala.Unit = {
    for (batch <- this.batches) {
      batch.begin()
    }
  }
  def draw(): scala.Unit = {
    for (effect <- this.effects) {
      effect.draw()
    }
  }
  def `end`(): scala.Unit = {
    for (batch <- this.batches) {
      batch.`end`()
    }
  }
  def getRenderables(renderables: com.badlogic.gdx.utils.Array[com.badlogic.gdx.graphics.g3d.Renderable], pool: com.badlogic.gdx.utils.Pool[com.badlogic.gdx.graphics.g3d.Renderable]): scala.Unit = {
    for (batch <- this.batches) {
      batch.getRenderables(renderables, pool)
    }
  }
  def getBatches(): com.badlogic.gdx.utils.Array[com.badlogic.gdx.graphics.g3d.particles.batches.ParticleBatch[?]] = {
    return this.batches
  }
}
object ParticleSystem {
  private var instance: ParticleSystem = null.asInstanceOf[ParticleSystem]
  def get(): ParticleSystem = {
    if (ParticleSystem.instance == null) {
      ParticleSystem.instance = new ParticleSystem()
    } else ()
    return ParticleSystem.instance
  }
}