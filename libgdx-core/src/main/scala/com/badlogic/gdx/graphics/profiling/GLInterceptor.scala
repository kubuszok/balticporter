package com.badlogic.gdx.graphics.profiling

abstract class GLInterceptor extends com.badlogic.gdx.graphics.GL20 {
  var calls: scala.Int = 0
  var textureBindings: scala.Int = 0
  var drawCalls: scala.Int = 0
  var shaderSwitches: scala.Int = 0
  final val vertexCount: com.badlogic.gdx.math.FloatCounter = new com.badlogic.gdx.math.FloatCounter(0)
  var glProfiler: com.badlogic.gdx.graphics.profiling.GLProfiler = null.asInstanceOf[com.badlogic.gdx.graphics.profiling.GLProfiler]
  def this(profiler: com.badlogic.gdx.graphics.profiling.GLProfiler) = {
    this()
    this.glProfiler = profiler
  }
  def getCalls(): scala.Int = {
    return this.calls
  }
  def getTextureBindings(): scala.Int = {
    return this.textureBindings
  }
  def getDrawCalls(): scala.Int = {
    return this.drawCalls
  }
  def getShaderSwitches(): scala.Int = {
    return this.shaderSwitches
  }
  def getVertexCount(): com.badlogic.gdx.math.FloatCounter = {
    return this.vertexCount
  }
  def reset(): scala.Unit = {
    this.calls = 0
    this.textureBindings = 0
    this.drawCalls = 0
    this.shaderSwitches = 0
    this.vertexCount.reset()
  }
}
object GLInterceptor {
  export com.badlogic.gdx.graphics.GL20.*
  def resolveErrorNumber(error: scala.Int): java.lang.String = {
    error match {
      case com.badlogic.gdx.graphics.GL20.GL_INVALID_VALUE => {
        return "GL_INVALID_VALUE"
      }
      case com.badlogic.gdx.graphics.GL20.GL_INVALID_OPERATION => {
        return "GL_INVALID_OPERATION"
      }
      case com.badlogic.gdx.graphics.GL20.GL_INVALID_FRAMEBUFFER_OPERATION => {
        return "GL_INVALID_FRAMEBUFFER_OPERATION"
      }
      case com.badlogic.gdx.graphics.GL20.GL_INVALID_ENUM => {
        return "GL_INVALID_ENUM"
      }
      case com.badlogic.gdx.graphics.GL20.GL_OUT_OF_MEMORY => {
        return "GL_OUT_OF_MEMORY"
      }
      case _ => {
        return "number " + error
      }
    }
  }
}