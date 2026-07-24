package com.badlogic.gdx.utils

class GdxRuntimeException extends java.lang.RuntimeException {
  def this(message: java.lang.String, t: java.lang.Throwable) = {
    this()
  }
  def this(message: java.lang.String) = {
    this()
  }
  def this(t: java.lang.Throwable) = {
    this()
  }
}
object GdxRuntimeException {
  private final val serialVersionUID: scala.Long = 6735854402467673117L
}