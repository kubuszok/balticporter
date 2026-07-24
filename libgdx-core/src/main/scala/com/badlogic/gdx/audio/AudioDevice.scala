package com.badlogic.gdx.audio

trait AudioDevice extends com.badlogic.gdx.utils.Disposable {
  def isMono(): scala.Boolean
  def writeSamples(samples: scala.Array[scala.Short], offset: scala.Int, numSamples: scala.Int): scala.Unit
  def writeSamples(samples: scala.Array[scala.Float], offset: scala.Int, numSamples: scala.Int): scala.Unit
  def getLatency(): scala.Int
  def dispose(): scala.Unit
  def setVolume(volume: scala.Float): scala.Unit
  def pause(): scala.Unit
  def resume(): scala.Unit
}