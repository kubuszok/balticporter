package com.badlogic.gdx.graphics.g3d.utils

class DefaultShaderProvider extends com.badlogic.gdx.graphics.g3d.utils.BaseShaderProvider {
  var config: com.badlogic.gdx.graphics.g3d.shaders.DefaultShader.Config = null.asInstanceOf[com.badlogic.gdx.graphics.g3d.shaders.DefaultShader.Config]
  def this(config: com.badlogic.gdx.graphics.g3d.shaders.DefaultShader.Config) = {
    this()
    this.config = if (config == null) new com.badlogic.gdx.graphics.g3d.shaders.DefaultShader.Config() else config
  }
  def this(vertexShader: java.lang.String, fragmentShader: java.lang.String) = {
    this(new com.badlogic.gdx.graphics.g3d.shaders.DefaultShader.Config(vertexShader, fragmentShader))
  }
  def this(vertexShader: com.badlogic.gdx.files.FileHandle, fragmentShader: com.badlogic.gdx.files.FileHandle) = {
    this(vertexShader.readString(), fragmentShader.readString())
  }
  def createShader(renderable: com.badlogic.gdx.graphics.g3d.Renderable): com.badlogic.gdx.graphics.g3d.Shader = {
    return new com.badlogic.gdx.graphics.g3d.shaders.DefaultShader(renderable, this.config)
  }
}