package com.badlogic.gdx.graphics.g3d.utils

trait ShaderProvider extends com.badlogic.gdx.utils.Disposable {
  def getShader(renderable: com.badlogic.gdx.graphics.g3d.Renderable): com.badlogic.gdx.graphics.g3d.Shader
}