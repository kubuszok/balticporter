package com.badlogic.gdx.audio

trait Sound extends com.badlogic.gdx.utils.Disposable {
  def play(): scala.Long
  def play(volume: scala.Float): scala.Long
  def play(volume: scala.Float, pitch: scala.Float, pan: scala.Float): scala.Long
  def loop(): scala.Long
  def loop(volume: scala.Float): scala.Long
  def loop(volume: scala.Float, pitch: scala.Float, pan: scala.Float): scala.Long
  def stop(): scala.Unit
  def pause(): scala.Unit
  def resume(): scala.Unit
  def dispose(): scala.Unit
  def stop(soundId: scala.Long): scala.Unit
  def pause(soundId: scala.Long): scala.Unit
  def resume(soundId: scala.Long): scala.Unit
  def setLooping(soundId: scala.Long, looping: scala.Boolean): scala.Unit
  def setPitch(soundId: scala.Long, pitch: scala.Float): scala.Unit
  def setVolume(soundId: scala.Long, volume: scala.Float): scala.Unit
  def setPan(soundId: scala.Long, pan: scala.Float, volume: scala.Float): scala.Unit
}