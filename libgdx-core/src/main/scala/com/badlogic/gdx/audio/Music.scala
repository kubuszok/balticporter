package com.badlogic.gdx.audio

trait Music extends com.badlogic.gdx.utils.Disposable {
  def play(): scala.Unit
  def pause(): scala.Unit
  def stop(): scala.Unit
  def isPlaying(): scala.Boolean
  def setLooping(isLooping: scala.Boolean): scala.Unit
  def isLooping(): scala.Boolean
  def setVolume(volume: scala.Float): scala.Unit
  def getVolume(): scala.Float
  def setPan(pan: scala.Float, volume: scala.Float): scala.Unit
  def setPosition(position: scala.Float): scala.Unit
  def getPosition(): scala.Float
  def dispose(): scala.Unit
  def setOnCompletionListener(listener: OnCompletionListener): scala.Unit
  trait OnCompletionListener {
    def onCompletion(music: Music): scala.Unit
  }
}