package com.badlogic.gdx.graphics.g3d.particles.batches

class ModelInstanceParticleBatch extends com.badlogic.gdx.graphics.g3d.particles.batches.ParticleBatch[com.badlogic.gdx.graphics.g3d.particles.renderers.ModelInstanceControllerRenderData] {
  var controllersRenderData: com.badlogic.gdx.utils.Array[com.badlogic.gdx.graphics.g3d.particles.renderers.ModelInstanceControllerRenderData] = null.asInstanceOf[com.badlogic.gdx.utils.Array[com.badlogic.gdx.graphics.g3d.particles.renderers.ModelInstanceControllerRenderData]]
  var bufferedParticlesCount: scala.Int = 0
  def this() = {
    this()
    this.controllersRenderData = new com.badlogic.gdx.utils.Array[com.badlogic.gdx.graphics.g3d.particles.renderers.ModelInstanceControllerRenderData](false, 5)
  }
  def getRenderables(renderables: com.badlogic.gdx.utils.Array[com.badlogic.gdx.graphics.g3d.Renderable], pool: com.badlogic.gdx.utils.Pool[com.badlogic.gdx.graphics.g3d.Renderable]): scala.Unit = {
    for (data <- this.controllersRenderData) {
      { var i: scala.Int = 0; val count: scala.Int = data.controller.particles.size; while (i < count) { {
        data.modelInstanceChannel.data(i).getRenderables(renderables, pool)
      }; i = i + 1 } }
    }
  }
  def getBufferedCount(): scala.Int = {
    return this.bufferedParticlesCount
  }
  def begin(): scala.Unit = {
    this.controllersRenderData.clear()
    this.bufferedParticlesCount = 0
  }
  def `end`(): scala.Unit = {
    ()
  }
  def draw(data: com.badlogic.gdx.graphics.g3d.particles.renderers.ModelInstanceControllerRenderData): scala.Unit = {
    this.controllersRenderData.add(data)
    this.bufferedParticlesCount = this.bufferedParticlesCount + data.controller.particles.size
  }
  def save(manager: com.badlogic.gdx.assets.AssetManager, assetDependencyData: com.badlogic.gdx.graphics.g3d.particles.ResourceData[?]): scala.Unit = {
    ()
  }
  def load(manager: com.badlogic.gdx.assets.AssetManager, assetDependencyData: com.badlogic.gdx.graphics.g3d.particles.ResourceData[?]): scala.Unit = {
    ()
  }
}