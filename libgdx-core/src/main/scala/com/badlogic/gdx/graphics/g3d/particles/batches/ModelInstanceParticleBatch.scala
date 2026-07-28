package com.badlogic.gdx.graphics.g3d.particles.batches

class ModelInstanceParticleBatch extends com.badlogic.gdx.graphics.g3d.particles.batches.ParticleBatch[com.badlogic.gdx.graphics.g3d.particles.renderers.ModelInstanceControllerRenderData] {
  var controllersRenderData: com.badlogic.gdx.utils.Array[com.badlogic.gdx.graphics.g3d.particles.renderers.ModelInstanceControllerRenderData] = null.asInstanceOf[com.badlogic.gdx.utils.Array[com.badlogic.gdx.graphics.g3d.particles.renderers.ModelInstanceControllerRenderData]]
  var bufferedParticlesCount: scala.Int = 0
  this.controllersRenderData = new com.badlogic.gdx.utils.Array[com.badlogic.gdx.graphics.g3d.particles.renderers.ModelInstanceControllerRenderData](false, 5)
  @java.lang.Override
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
  @java.lang.Override
  def begin(): scala.Unit = {
    this.controllersRenderData.clear()
    this.bufferedParticlesCount = 0
  }
  @java.lang.Override
  def `end`(): scala.Unit = {
    ()
  }
  @java.lang.Override
  def draw(data: com.badlogic.gdx.graphics.g3d.particles.renderers.ModelInstanceControllerRenderData): scala.Unit = {
    this.controllersRenderData.add(data)
    this.bufferedParticlesCount = this.bufferedParticlesCount + data.controller.particles.size
  }
  @java.lang.Override
  def save(manager: com.badlogic.gdx.assets.AssetManager, assetDependencyData: com.badlogic.gdx.graphics.g3d.particles.ResourceData[?]): scala.Unit = {
    ()
  }
  @java.lang.Override
  def load(manager: com.badlogic.gdx.assets.AssetManager, assetDependencyData: com.badlogic.gdx.graphics.g3d.particles.ResourceData[?]): scala.Unit = {
    ()
  }
}