package com.badlogic.gdx.utils

class PauseableThread(runnable$p: java.lang.Runnable) extends java.lang.Thread {
  var runnable: java.lang.Runnable = null.asInstanceOf[java.lang.Runnable]
  var paused: scala.Boolean = false
  var exit: scala.Boolean = false
  this.runnable = runnable$p
  override def run(): scala.Unit = {
    while (true) {
      this.synchronized {
        try {
          while (this.paused) {
            this.`wait`()
          }
        } catch {
          case e: java.lang.InterruptedException => {
            e.printStackTrace()
          }
        }
      }
      if (this.exit) {
        return
      } else ()
      this.runnable.run()
    }
  }
  def onPause(): scala.Unit = {
    this.paused = true
  }
  def onResume(): scala.Unit = {
    this.synchronized {
      this.paused = false
      this.notifyAll()
    }
  }
  def isPaused(): scala.Boolean = {
    return this.paused
  }
  def stopThread(): scala.Unit = {
    this.exit = true
    if (this.paused) {
      this.onResume()
    } else ()
  }
}