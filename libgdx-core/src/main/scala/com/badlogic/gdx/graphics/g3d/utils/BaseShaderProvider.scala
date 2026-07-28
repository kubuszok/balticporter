package com.badlogic.gdx.graphics.g3d.utils

abstract class BaseShaderProvider extends com.badlogic.gdx.graphics.g3d.utils.ShaderProvider {
  var shaders: com.badlogic.gdx.utils.Array[com.badlogic.gdx.graphics.g3d.Shader] = new com.badlogic.gdx.utils.Array[com.badlogic.gdx.graphics.g3d.Shader]()
  @java.lang.Override
  override def getShader(renderable: com.badlogic.gdx.graphics.g3d.Renderable): com.badlogic.gdx.graphics.g3d.Shader = {
    val suggestedShader: com.badlogic.gdx.graphics.g3d.Shader = renderable.shader
    if ((suggestedShader != null) && suggestedShader.canRender(renderable)) {
      return suggestedShader
    } else ()
    for (shader <- this.shaders) {
      if (shader.canRender(renderable)) {
        return shader
      } else ()
    }
    val shader: com.badlogic.gdx.graphics.g3d.Shader = this.createShader(renderable)
    if (!shader.canRender(renderable)) {
      throw new com.badlogic.gdx.utils.GdxRuntimeException("unable to provide a shader for this renderable")
    } else ()
    shader.init()
    this.shaders.add(shader)
    return shader
  }
  def createShader(renderable: com.badlogic.gdx.graphics.g3d.Renderable): com.badlogic.gdx.graphics.g3d.Shader
  @java.lang.Override
  override def dispose(): scala.Unit = {
    for (shader <- this.shaders) {
      shader.dispose()
    }
    this.shaders.clear()
  }
}