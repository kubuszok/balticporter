package com.badlogic.gdx.graphics.g2d

class ParticleEffectPool extends com.badlogic.gdx.utils.Pool[PooledEffect] {
  private var effect: com.badlogic.gdx.graphics.g2d.ParticleEffect = null.asInstanceOf[com.badlogic.gdx.graphics.g2d.ParticleEffect]
  def this(effect: com.badlogic.gdx.graphics.g2d.ParticleEffect, initialCapacity: scala.Int, max: scala.Int) = {
    this()
    this.effect = effect
  }
  def newObject(): PooledEffect = {
    val pooledEffect: PooledEffect = new PooledEffect(this.effect)
    pooledEffect.start()
    return pooledEffect
  }
  def free(effect: PooledEffect): scala.Unit = {
    super.free(effect)
    effect.reset(false)
    if (((effect.xSizeScale != this.effect.xSizeScale) || (effect.ySizeScale != this.effect.ySizeScale)) || (effect.motionScale != this.effect.motionScale)) {
      val emitters: com.badlogic.gdx.utils.Array[com.badlogic.gdx.graphics.g2d.ParticleEmitter] = effect.getEmitters()
      val templateEmitters: com.badlogic.gdx.utils.Array[com.badlogic.gdx.graphics.g2d.ParticleEmitter] = this.effect.getEmitters();
      { var i: scala.Int = 0; while (i < emitters.size) { {
        val emitter: com.badlogic.gdx.graphics.g2d.ParticleEmitter = emitters.get(i)
        val templateEmitter: com.badlogic.gdx.graphics.g2d.ParticleEmitter = templateEmitters.get(i)
        emitter.matchSize(templateEmitter)
        emitter.matchMotion(templateEmitter)
      }; i = i + 1 } }
      effect.xSizeScale = this.effect.xSizeScale
      effect.ySizeScale = this.effect.ySizeScale
      effect.motionScale = this.effect.motionScale
    } else ()
  }
  class PooledEffect extends com.badlogic.gdx.graphics.g2d.ParticleEffect {
    def this(effect: com.badlogic.gdx.graphics.g2d.ParticleEffect) = {
      this()
    }
    def free(): scala.Unit = {
      free(this)
    }
  }
}