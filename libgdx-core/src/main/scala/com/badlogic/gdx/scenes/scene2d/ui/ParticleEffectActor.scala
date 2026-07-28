package com.badlogic.gdx.scenes.scene2d.ui

class ParticleEffectActor extends com.badlogic.gdx.scenes.scene2d.Actor with com.badlogic.gdx.utils.Disposable {
  private var particleEffect: com.badlogic.gdx.graphics.g2d.ParticleEffect = null.asInstanceOf[com.badlogic.gdx.graphics.g2d.ParticleEffect]
  var lastDelta: scala.Float = 0.0f
  var isRunning$field: scala.Boolean = false
  var ownsEffect: scala.Boolean = false
  private var resetOnStart: scala.Boolean = false
  private var autoRemove: scala.Boolean = false
  def this(particleEffect: com.badlogic.gdx.graphics.g2d.ParticleEffect, resetOnStart: scala.Boolean) = {
    this()
    this.particleEffect = particleEffect
    this.resetOnStart = resetOnStart
  }
  def this(particleFile: com.badlogic.gdx.files.FileHandle, atlas: com.badlogic.gdx.graphics.g2d.TextureAtlas) = {
    this()
    this.particleEffect = new com.badlogic.gdx.graphics.g2d.ParticleEffect()
    this.particleEffect.load(particleFile, atlas)
    this.ownsEffect = true
  }
  def this(particleFile: com.badlogic.gdx.files.FileHandle, imagesDir: com.badlogic.gdx.files.FileHandle) = {
    this()
    this.particleEffect = new com.badlogic.gdx.graphics.g2d.ParticleEffect()
    this.particleEffect.load(particleFile, imagesDir)
    this.ownsEffect = true
  }
  @java.lang.Override
  def draw(batch: com.badlogic.gdx.graphics.g2d.Batch, parentAlpha: scala.Float): scala.Unit = {
    this.particleEffect.setPosition(this.getX(), this.getY())
    if (this.lastDelta > 0) {
      this.particleEffect.update(this.lastDelta)
      this.lastDelta = 0
    } else ()
    if (this.isRunning$field) {
      this.particleEffect.draw(batch)
      this.isRunning$field = !this.particleEffect.isComplete()
    } else ()
  }
  @java.lang.Override
  def act(delta: scala.Float): scala.Unit = {
    super.act(delta)
    this.lastDelta = this.lastDelta + delta
    if (this.autoRemove && this.particleEffect.isComplete()) {
      this.remove()
    } else ()
  }
  def start(): scala.Unit = {
    this.isRunning$field = true
    if (this.resetOnStart) {
      this.particleEffect.reset(false)
    } else ()
    this.particleEffect.start()
  }
  def isResetOnStart(): scala.Boolean = {
    return this.resetOnStart
  }
  def setResetOnStart(resetOnStart: scala.Boolean): ParticleEffectActor = {
    this.resetOnStart = resetOnStart
    return this
  }
  def isAutoRemove(): scala.Boolean = {
    return this.autoRemove
  }
  def setAutoRemove(autoRemove: scala.Boolean): ParticleEffectActor = {
    this.autoRemove = autoRemove
    return this
  }
  def isRunning(): scala.Boolean = {
    return this.isRunning$field
  }
  def getEffect(): com.badlogic.gdx.graphics.g2d.ParticleEffect = {
    return this.particleEffect
  }
  @java.lang.Override
  def scaleChanged(): scala.Unit = {
    super.scaleChanged()
    this.particleEffect.scaleEffect(this.getScaleX(), this.getScaleY(), this.getScaleY())
  }
  def cancel(): scala.Unit = {
    this.isRunning$field = true
  }
  def allowCompletion(): scala.Unit = {
    this.particleEffect.allowCompletion()
  }
  @java.lang.Override
  def dispose(): scala.Unit = {
    if (this.ownsEffect) {
      this.particleEffect.dispose()
    } else ()
  }
}
object ParticleEffectActor {
  export com.badlogic.gdx.scenes.scene2d.Actor.*
}