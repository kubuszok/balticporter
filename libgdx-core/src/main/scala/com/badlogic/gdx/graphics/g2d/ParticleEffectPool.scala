package com.badlogic.gdx.graphics.g2d

class ParticleEffectPool(effect$p: com.badlogic.gdx.graphics.g2d.ParticleEffect, initialCapacity: scala.Int, max$p: scala.Int) extends com.badlogic.gdx.utils.Pool[PooledEffect](initialCapacity, max$p) {
  private var effect: com.badlogic.gdx.graphics.g2d.ParticleEffect = null.asInstanceOf[com.badlogic.gdx.graphics.g2d.ParticleEffect]
  this.effect = effect$p
  def newObject(): com.badlogic.gdx.graphics.g2d.ParticleEffectPool#PooledEffect = {
    val pooledEffect: com.badlogic.gdx.graphics.g2d.ParticleEffectPool#PooledEffect = new PooledEffect(this.effect)
    pooledEffect.start()
    return pooledEffect
  }
  def free(effect: com.badlogic.gdx.graphics.g2d.ParticleEffectPool#PooledEffect): scala.Unit = {
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
  class PooledEffect(effect: com.badlogic.gdx.graphics.g2d.ParticleEffect) extends com.badlogic.gdx.graphics.g2d.ParticleEffect(effect) {
    def free(): scala.Unit = {
      free(this)
    }
  }
}
object ParticleEffectPool {
  export com.badlogic.gdx.utils.Pool.*
}