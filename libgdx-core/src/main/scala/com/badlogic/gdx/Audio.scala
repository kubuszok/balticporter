package com.badlogic.gdx

trait Audio extends com.badlogic.gdx.utils.Disposable {
  def newAudioDevice(samplingRate: scala.Int, isMono: scala.Boolean): com.badlogic.gdx.audio.AudioDevice
  def newAudioRecorder(samplingRate: scala.Int, isMono: scala.Boolean): com.badlogic.gdx.audio.AudioRecorder
  def newSound(fileHandle: com.badlogic.gdx.files.FileHandle): com.badlogic.gdx.audio.Sound
  def newMusic(file: com.badlogic.gdx.files.FileHandle): com.badlogic.gdx.audio.Music
  def switchOutputDevice(deviceIdentifier: java.lang.String): scala.Boolean
  def getAvailableOutputDevices(): scala.Array[java.lang.String]
}