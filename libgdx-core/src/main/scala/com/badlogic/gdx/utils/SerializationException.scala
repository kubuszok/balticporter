package com.badlogic.gdx.utils

class SerializationException extends java.lang.RuntimeException {
  private var trace: java.lang.StringBuilder = null.asInstanceOf[java.lang.StringBuilder]
  def this(message: java.lang.String, cause: java.lang.Throwable) = {
    this()
  }
  def this(message: java.lang.String) = {
    this()
  }
  def this(cause: java.lang.Throwable) = {
    this()
  }
  def causedBy(`type`: java.lang.Class[?]): scala.Boolean = {
    return this.causedBy(this, `type`)
  }
  private def causedBy(ex: java.lang.Throwable, `type`: java.lang.Class[?]): scala.Boolean = {
    val cause: java.lang.Throwable = ex.getCause()
    if ((cause == null) || (cause == ex)) {
      return false
    } else ()
    if (`type`.isAssignableFrom(cause.getClass())) {
      return true
    } else ()
    return this.causedBy(cause, `type`)
  }
  def getMessage(): java.lang.String = {
    if (this.trace == null) {
      return super.getMessage()
    } else ()
    val sb: java.lang.StringBuilder = new java.lang.StringBuilder(512)
    sb.append(super.getMessage())
    if (sb.length() > 0) {
      sb.append('\n')
    } else ()
    sb.append("Serialization trace:")
    sb.append(this.trace)
    return sb.toString()
  }
  def addTrace(info: java.lang.String): scala.Unit = {
    if (info == null) {
      throw new java.lang.IllegalArgumentException("info cannot be null.")
    } else ()
    if (this.trace == null) {
      this.trace = new java.lang.StringBuilder(512)
    } else ()
    this.trace.append('\n')
    this.trace.append(info)
  }
}