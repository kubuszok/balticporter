package com.badlogic.gdx.assets.loaders

class ShaderProgramLoader extends com.badlogic.gdx.assets.loaders.AsynchronousAssetLoader[com.badlogic.gdx.graphics.glutils.ShaderProgram, com.badlogic.gdx.assets.loaders.ShaderProgramLoader.ShaderProgramParameter] {
  private var vertexFileSuffix: java.lang.String = ".vert"
  private var fragmentFileSuffix: java.lang.String = ".frag"
  def this(resolver: com.badlogic.gdx.assets.loaders.FileHandleResolver, vertexFileSuffix: java.lang.String, fragmentFileSuffix: java.lang.String) = {
    this()
    this.vertexFileSuffix = vertexFileSuffix
    this.fragmentFileSuffix = fragmentFileSuffix
  }
  def this(resolver: com.badlogic.gdx.assets.loaders.FileHandleResolver) = {
    this()
  }
  def getDependencies(fileName: java.lang.String, file: com.badlogic.gdx.files.FileHandle, parameter: com.badlogic.gdx.assets.loaders.ShaderProgramLoader.ShaderProgramParameter): com.badlogic.gdx.utils.Array[com.badlogic.gdx.assets.AssetDescriptor[?]] = {
    return null
  }
  def loadAsync(manager: com.badlogic.gdx.assets.AssetManager, fileName: java.lang.String, file: com.badlogic.gdx.files.FileHandle, parameter: com.badlogic.gdx.assets.loaders.ShaderProgramLoader.ShaderProgramParameter): scala.Unit = {
    ()
  }
  def loadSync(manager: com.badlogic.gdx.assets.AssetManager, fileName: java.lang.String, file: com.badlogic.gdx.files.FileHandle, parameter: com.badlogic.gdx.assets.loaders.ShaderProgramLoader.ShaderProgramParameter): com.badlogic.gdx.graphics.glutils.ShaderProgram = {
    var vertFileName: java.lang.String = null
    var fragFileName: java.lang.String = null
    if (parameter != null) {
      if (parameter.vertexFile != null) {
        vertFileName = parameter.vertexFile
      } else ()
      if (parameter.fragmentFile != null) {
        fragFileName = parameter.fragmentFile
      } else ()
    } else ()
    if ((vertFileName == null) && fileName.endsWith(this.fragmentFileSuffix)) {
      vertFileName = fileName.substring(0, fileName.length() - this.fragmentFileSuffix.length()) + this.vertexFileSuffix
    } else ()
    if ((fragFileName == null) && fileName.endsWith(this.vertexFileSuffix)) {
      fragFileName = fileName.substring(0, fileName.length() - this.vertexFileSuffix.length()) + this.fragmentFileSuffix
    } else ()
    val vertexFile: com.badlogic.gdx.files.FileHandle = if (vertFileName == null) file else this.resolve(vertFileName)
    val fragmentFile: com.badlogic.gdx.files.FileHandle = if (fragFileName == null) file else this.resolve(fragFileName)
    var vertexCode: java.lang.String = vertexFile.readString()
    var fragmentCode: java.lang.String = if (vertexFile.equals(fragmentFile)) vertexCode else fragmentFile.readString()
    if (parameter != null) {
      if (parameter.prependVertexCode != null) {
        vertexCode = parameter.prependVertexCode + vertexCode
      } else ()
      if (parameter.prependFragmentCode != null) {
        fragmentCode = parameter.prependFragmentCode + fragmentCode
      } else ()
    } else ()
    val shaderProgram: com.badlogic.gdx.graphics.glutils.ShaderProgram = new com.badlogic.gdx.graphics.glutils.ShaderProgram(vertexCode, fragmentCode)
    if (((parameter == null) || parameter.logOnCompileFailure) && (!shaderProgram.isCompiled())) {
      manager.getLogger().error((("ShaderProgram " + fileName) + " failed to compile:\n") + shaderProgram.getLog())
    } else ()
    return shaderProgram
  }
}
object ShaderProgramLoader {
  class ShaderProgramParameter extends com.badlogic.gdx.assets.AssetLoaderParameters[com.badlogic.gdx.graphics.glutils.ShaderProgram] {
    var vertexFile: java.lang.String = null.asInstanceOf[java.lang.String]
    var fragmentFile: java.lang.String = null.asInstanceOf[java.lang.String]
    var logOnCompileFailure: scala.Boolean = true
    var prependVertexCode: java.lang.String = null.asInstanceOf[java.lang.String]
    var prependFragmentCode: java.lang.String = null.asInstanceOf[java.lang.String]
  }
  object ShaderProgramParameter {
    export com.badlogic.gdx.assets.AssetLoaderParameters.*
  }
}