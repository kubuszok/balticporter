package com.badlogic.gdx.graphics.profiling

trait GLErrorListener {
  def onError(error: scala.Int): scala.Unit
}
object GLErrorListener {
  final val LOGGING_LISTENER: GLErrorListener = new GLErrorListener()
  final val THROWING_LISTENER: GLErrorListener = new GLErrorListener()
}