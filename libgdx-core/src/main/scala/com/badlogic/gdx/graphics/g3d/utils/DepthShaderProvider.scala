package com.badlogic.gdx.graphics.g3d.utils

class DepthShaderProvider(config$p: com.badlogic.gdx.graphics.g3d.shaders.DepthShader.Config) extends com.badlogic.gdx.graphics.g3d.utils.BaseShaderProvider {
  var config: com.badlogic.gdx.graphics.g3d.shaders.DepthShader.Config = null.asInstanceOf[com.badlogic.gdx.graphics.g3d.shaders.DepthShader.Config]
  def this(vertexShader: java.lang.String, fragmentShader: java.lang.String) = {
    this(new com.badlogic.gdx.graphics.g3d.shaders.DepthShader.Config(vertexShader, fragmentShader))
  }
  def this(vertexShader: com.badlogic.gdx.files.FileHandle, fragmentShader: com.badlogic.gdx.files.FileHandle) = {
    this(vertexShader.readString(), fragmentShader.readString())
  }
  def this() = {
    this(null)
  }
  this.config = if (config$p == null) new com.badlogic.gdx.graphics.g3d.shaders.DepthShader.Config() else config$p
  @java.lang.Override
  override def createShader(renderable: com.badlogic.gdx.graphics.g3d.Renderable): com.badlogic.gdx.graphics.g3d.Shader = {
    return new com.badlogic.gdx.graphics.g3d.shaders.DepthShader(renderable, this.config)
  }
}