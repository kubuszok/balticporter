package com.badlogic.gdx.graphics.g3d.utils

class RenderContext(textures: com.badlogic.gdx.graphics.g3d.utils.TextureBinder) {
  var textureBinder: com.badlogic.gdx.graphics.g3d.utils.TextureBinder = null.asInstanceOf[com.badlogic.gdx.graphics.g3d.utils.TextureBinder]
  private var blending: scala.Boolean = false
  private var blendSourceRgbFactor: scala.Int = 0
  private var blendDestRgbFactor: scala.Int = 0
  private var blendSourceAlphaFactor: scala.Int = 0
  private var blendDestAlphaFactor: scala.Int = 0
  private var depthFunc: scala.Int = 0
  private var depthRangeNear: scala.Float = 0.0f
  private var depthRangeFar: scala.Float = 0.0f
  private var depthMask: scala.Boolean = false
  private var cullFace: scala.Int = 0
  this.textureBinder = textures
  def begin(): scala.Unit = {
    com.badlogic.gdx.Gdx.gl.glDisable(com.badlogic.gdx.graphics.GL20.GL_DEPTH_TEST)
    this.depthFunc = 0
    com.badlogic.gdx.Gdx.gl.glDepthMask(true)
    this.depthMask = true
    com.badlogic.gdx.Gdx.gl.glDisable(com.badlogic.gdx.graphics.GL20.GL_BLEND)
    this.blending = false
    com.badlogic.gdx.Gdx.gl.glDisable(com.badlogic.gdx.graphics.GL20.GL_CULL_FACE)
    this.cullFace = {
      this.blendSourceRgbFactor = {
        this.blendDestRgbFactor = {
          this.blendSourceAlphaFactor = {
            this.blendDestAlphaFactor = 0
            this.blendDestAlphaFactor
          }
          this.blendSourceAlphaFactor
        }
        this.blendDestRgbFactor
      }
      this.blendSourceRgbFactor
    }
    this.textureBinder.begin()
  }
  def `end`(): scala.Unit = {
    if (this.depthFunc != 0) {
      com.badlogic.gdx.Gdx.gl.glDisable(com.badlogic.gdx.graphics.GL20.GL_DEPTH_TEST)
    } else ()
    if (!this.depthMask) {
      com.badlogic.gdx.Gdx.gl.glDepthMask(true)
    } else ()
    if (this.blending) {
      com.badlogic.gdx.Gdx.gl.glDisable(com.badlogic.gdx.graphics.GL20.GL_BLEND)
    } else ()
    if (this.cullFace > 0) {
      com.badlogic.gdx.Gdx.gl.glDisable(com.badlogic.gdx.graphics.GL20.GL_CULL_FACE)
    } else ()
    this.textureBinder.`end`()
  }
  def setDepthMask(depthMask: scala.Boolean): scala.Unit = {
    if (this.depthMask != depthMask) {
      com.badlogic.gdx.Gdx.gl.glDepthMask({
        this.depthMask = depthMask
        this.depthMask
      })
    } else ()
  }
  def setDepthTest(depthFunction: scala.Int): scala.Unit = {
    this.setDepthTest(depthFunction, 0.0f, 1.0f)
  }
  def setDepthTest(depthFunction: scala.Int, depthRangeNear: scala.Float, depthRangeFar: scala.Float): scala.Unit = {
    val wasEnabled: scala.Boolean = this.depthFunc != 0
    val enabled: scala.Boolean = depthFunction != 0
    if (this.depthFunc != depthFunction) {
      this.depthFunc = depthFunction
      if (enabled) {
        com.badlogic.gdx.Gdx.gl.glEnable(com.badlogic.gdx.graphics.GL20.GL_DEPTH_TEST)
        com.badlogic.gdx.Gdx.gl.glDepthFunc(depthFunction)
      } else {
        com.badlogic.gdx.Gdx.gl.glDisable(com.badlogic.gdx.graphics.GL20.GL_DEPTH_TEST)
      }
    } else ()
    if (enabled) {
      if ((!wasEnabled) || (this.depthFunc != depthFunction)) {
        com.badlogic.gdx.Gdx.gl.glDepthFunc({
          this.depthFunc = depthFunction
          this.depthFunc
        })
      } else ()
      if (((!wasEnabled) || (this.depthRangeNear != depthRangeNear)) || (this.depthRangeFar != depthRangeFar)) {
        com.badlogic.gdx.Gdx.gl.glDepthRangef({
          this.depthRangeNear = depthRangeNear
          this.depthRangeNear
        }, {
          this.depthRangeFar = depthRangeFar
          this.depthRangeFar
        })
      } else ()
    } else ()
  }
  def setBlending(enabled: scala.Boolean, sFactor: scala.Int, dFactor: scala.Int): scala.Unit = {
    this.setBlending(enabled, sFactor, dFactor, sFactor, dFactor)
  }
  def setBlending(enabled: scala.Boolean, sRgbFactor: scala.Int, dRgbFactor: scala.Int, sAlphaFactor: scala.Int, dAlphaFactor: scala.Int): scala.Unit = {
    if (enabled != this.blending) {
      this.blending = enabled
      if (enabled) {
        com.badlogic.gdx.Gdx.gl.glEnable(com.badlogic.gdx.graphics.GL20.GL_BLEND)
      } else {
        com.badlogic.gdx.Gdx.gl.glDisable(com.badlogic.gdx.graphics.GL20.GL_BLEND)
      }
    } else ()
    if (enabled && ((((this.blendSourceRgbFactor != sRgbFactor) || (this.blendDestRgbFactor != dRgbFactor)) || (this.blendSourceAlphaFactor != sAlphaFactor)) || (this.blendDestAlphaFactor != dAlphaFactor))) {
      com.badlogic.gdx.Gdx.gl.glBlendFuncSeparate(sRgbFactor, dRgbFactor, sAlphaFactor, dAlphaFactor)
      this.blendSourceRgbFactor = sRgbFactor
      this.blendDestRgbFactor = dRgbFactor
      this.blendSourceAlphaFactor = sAlphaFactor
      this.blendDestAlphaFactor = dAlphaFactor
    } else ()
  }
  def setCullFace(face: scala.Int): scala.Unit = {
    if (face != this.cullFace) {
      this.cullFace = face
      if (((face == com.badlogic.gdx.graphics.GL20.GL_FRONT) || (face == com.badlogic.gdx.graphics.GL20.GL_BACK)) || (face == com.badlogic.gdx.graphics.GL20.GL_FRONT_AND_BACK)) {
        com.badlogic.gdx.Gdx.gl.glEnable(com.badlogic.gdx.graphics.GL20.GL_CULL_FACE)
        com.badlogic.gdx.Gdx.gl.glCullFace(face)
      } else {
        com.badlogic.gdx.Gdx.gl.glDisable(com.badlogic.gdx.graphics.GL20.GL_CULL_FACE)
      }
    } else ()
  }
}