package com.badlogic.gdx.graphics.g3d.utils

trait TextureBinder {
  def begin(): scala.Unit
  def `end`(): scala.Unit
  def bind(textureDescriptor: com.badlogic.gdx.graphics.g3d.utils.TextureDescriptor): scala.Int
  def bind(texture: com.badlogic.gdx.graphics.GLTexture): scala.Int
  def getBindCount(): scala.Int
  def getReuseCount(): scala.Int
  def resetCounts(): scala.Unit
}