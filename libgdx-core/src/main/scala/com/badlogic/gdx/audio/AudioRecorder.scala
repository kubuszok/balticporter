package com.badlogic.gdx.audio

trait AudioRecorder extends com.badlogic.gdx.utils.Disposable {
  def read(samples: scala.Array[scala.Short], offset: scala.Int, numSamples: scala.Int): scala.Unit
  override def dispose(): scala.Unit
}