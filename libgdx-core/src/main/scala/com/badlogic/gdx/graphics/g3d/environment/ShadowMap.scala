package com.badlogic.gdx.graphics.g3d.environment

trait ShadowMap {
  def getProjViewTrans(): com.badlogic.gdx.math.Matrix4
  def getDepthMap(): com.badlogic.gdx.graphics.g3d.utils.TextureDescriptor
}