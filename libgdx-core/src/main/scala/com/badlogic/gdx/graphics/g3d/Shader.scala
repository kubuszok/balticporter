package com.badlogic.gdx.graphics.g3d

trait Shader extends com.badlogic.gdx.utils.Disposable {
  def init(): scala.Unit
  def compareTo(other: Shader): scala.Int
  def canRender(instance: com.badlogic.gdx.graphics.g3d.Renderable): scala.Boolean
  def begin(camera: com.badlogic.gdx.graphics.Camera, context: com.badlogic.gdx.graphics.g3d.utils.RenderContext): scala.Unit
  def render(renderable: com.badlogic.gdx.graphics.g3d.Renderable): scala.Unit
  def `end`(): scala.Unit
}