package com.badlogic.gdx.graphics

class FPSLogger {
  var startTime: scala.Long = 0L
  var bound: scala.Int = 0
  def this(bound: scala.Int) = {
    this()
    this.bound = bound
    this.startTime = com.badlogic.gdx.utils.TimeUtils.nanoTime()
  }
  def setBound(bound: scala.Int): scala.Unit = {
    this.bound = bound
    this.startTime = com.badlogic.gdx.utils.TimeUtils.nanoTime()
  }
  def log(): scala.Unit = {
    val nanoTime: scala.Long = com.badlogic.gdx.utils.TimeUtils.nanoTime()
    if ((nanoTime - this.startTime) > 1000000000) {
      val fps: scala.Int = com.badlogic.gdx.Gdx.graphics.getFramesPerSecond()
      if (fps < this.bound) {
        com.badlogic.gdx.Gdx.app.log("FPSLogger", "fps: " + fps)
        this.startTime = nanoTime
      } else ()
    } else ()
  }
}