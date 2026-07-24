package com.badlogic.gdx.graphics

trait Texture3DData {
  def isPrepared(): scala.Boolean
  def prepare(): scala.Unit
  def getWidth(): scala.Int
  def getHeight(): scala.Int
  def getDepth(): scala.Int
  def getInternalFormat(): scala.Int
  def getGLType(): scala.Int
  def useMipMaps(): scala.Boolean
  def consume3DData(): scala.Unit
  def isManaged(): scala.Boolean
}