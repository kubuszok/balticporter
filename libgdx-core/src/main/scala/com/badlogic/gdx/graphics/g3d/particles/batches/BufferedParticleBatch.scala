package com.badlogic.gdx.graphics.g3d.particles.batches

abstract class BufferedParticleBatch[T <: com.badlogic.gdx.graphics.g3d.particles.renderers.ParticleControllerRenderData](arraySupplier: com.badlogic.gdx.utils.ArraySupplier[scala.Array[T]]) extends com.badlogic.gdx.graphics.g3d.particles.batches.ParticleBatch[T] {
  var renderData: com.badlogic.gdx.utils.Array[T] = null.asInstanceOf[com.badlogic.gdx.utils.Array[T]]
  var bufferedParticlesCount: scala.Int = 0
  var currentCapacity: scala.Int = 0
  var sorter: com.badlogic.gdx.graphics.g3d.particles.ParticleSorter = null.asInstanceOf[com.badlogic.gdx.graphics.g3d.particles.ParticleSorter]
  var camera: com.badlogic.gdx.graphics.Camera = null.asInstanceOf[com.badlogic.gdx.graphics.Camera]
  this.sorter = new com.badlogic.gdx.graphics.g3d.particles.ParticleSorter.Distance()
  this.renderData = new com.badlogic.gdx.utils.Array[T](false, 10, arraySupplier)
  def begin(): scala.Unit = {
    this.renderData.clear()
    this.bufferedParticlesCount = 0
  }
  @java.lang.Override
  def draw(data: T): scala.Unit = {
    if (data.controller.particles.size > 0) {
      this.renderData.add(data)
      this.bufferedParticlesCount = this.bufferedParticlesCount + data.controller.particles.size
    } else ()
  }
  def `end`(): scala.Unit = {
    if (this.bufferedParticlesCount > 0) {
      this.ensureCapacity(this.bufferedParticlesCount)
      this.flush(this.sorter.sort(this.renderData))
    } else ()
  }
  def ensureCapacity(capacity: scala.Int): scala.Unit = {
    if (this.currentCapacity >= capacity) {
      return
    } else ()
    this.sorter.ensureCapacity(capacity)
    this.allocParticlesData(capacity)
    this.currentCapacity = capacity
  }
  def resetCapacity(): scala.Unit = {
    this.currentCapacity = {
      this.bufferedParticlesCount = 0
      this.bufferedParticlesCount
    }
  }
  def allocParticlesData(capacity: scala.Int): scala.Unit
  def setCamera(camera: com.badlogic.gdx.graphics.Camera): scala.Unit = {
    this.camera = camera
    this.sorter.setCamera(camera)
  }
  def getSorter(): com.badlogic.gdx.graphics.g3d.particles.ParticleSorter = {
    return this.sorter
  }
  def setSorter(sorter: com.badlogic.gdx.graphics.g3d.particles.ParticleSorter): scala.Unit = {
    this.sorter = sorter
    sorter.setCamera(this.camera)
    sorter.ensureCapacity(this.currentCapacity)
  }
  def flush(offsets: scala.Array[scala.Int]): scala.Unit
  def getBufferedCount(): scala.Int = {
    return this.bufferedParticlesCount
  }
}