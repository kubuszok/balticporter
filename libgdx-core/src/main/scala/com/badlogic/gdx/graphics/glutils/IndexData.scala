package com.badlogic.gdx.graphics.glutils

trait IndexData extends com.badlogic.gdx.utils.Disposable {
  def getNumIndices(): scala.Int
  def getNumMaxIndices(): scala.Int
  def setIndices(indices: scala.Array[scala.Short], offset: scala.Int, count: scala.Int): scala.Unit
  def setIndices(indices: java.nio.ShortBuffer): scala.Unit
  def updateIndices(targetOffset: scala.Int, indices: scala.Array[scala.Short], offset: scala.Int, count: scala.Int): scala.Unit
  @java.lang.Deprecated
  def getBuffer(): java.nio.ShortBuffer
  def getBuffer(forWriting: scala.Boolean): java.nio.ShortBuffer
  def bind(): scala.Unit
  def unbind(): scala.Unit
  def invalidate(): scala.Unit
  override def dispose(): scala.Unit
}