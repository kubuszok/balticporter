package com.badlogic.gdx.graphics

trait CubemapData {
  def isPrepared(): scala.Boolean
  def prepare(): scala.Unit
  def consumeCubemapData(): scala.Unit
  def getWidth(): scala.Int
  def getHeight(): scala.Int
  def isManaged(): scala.Boolean
}