package com.badlogic.gdx.graphics.g3d.particles.batches

trait ParticleBatch[T <: com.badlogic.gdx.graphics.g3d.particles.renderers.ParticleControllerRenderData] extends com.badlogic.gdx.graphics.g3d.RenderableProvider with com.badlogic.gdx.graphics.g3d.particles.ResourceData.Configurable[scala.AnyRef] {
  def begin(): scala.Unit
  def draw(controller: T): scala.Unit
  def `end`(): scala.Unit
  def save(manager: com.badlogic.gdx.assets.AssetManager, assetDependencyData: com.badlogic.gdx.graphics.g3d.particles.ResourceData[?]): scala.Unit
  def load(manager: com.badlogic.gdx.assets.AssetManager, assetDependencyData: com.badlogic.gdx.graphics.g3d.particles.ResourceData[?]): scala.Unit
}