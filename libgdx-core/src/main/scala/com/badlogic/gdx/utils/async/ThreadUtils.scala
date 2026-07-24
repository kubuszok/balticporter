package com.badlogic.gdx.utils.async

object ThreadUtils {
  def `yield`(): scala.Unit = {
    java.lang.Thread.`yield`()
  }
}