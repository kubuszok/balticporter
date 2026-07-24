package com.badlogic.gdx.graphics.g3d

trait RenderableProvider {
  def getRenderables(renderables: com.badlogic.gdx.utils.Array[com.badlogic.gdx.graphics.g3d.Renderable], pool: com.badlogic.gdx.utils.Pool[com.badlogic.gdx.graphics.g3d.Renderable]): scala.Unit
}