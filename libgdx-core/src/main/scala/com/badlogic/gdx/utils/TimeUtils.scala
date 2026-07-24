package com.badlogic.gdx.utils

object TimeUtils {
  private final val nanosPerMilli: scala.Long = 1000000
  def nanoTime(): scala.Long = {
    return java.lang.System.nanoTime()
  }
  def millis(): scala.Long = {
    return java.lang.System.currentTimeMillis()
  }
  def nanosToMillis(nanos: scala.Long): scala.Long = {
    return nanos / TimeUtils.nanosPerMilli
  }
  def millisToNanos(millis: scala.Long): scala.Long = {
    return millis * TimeUtils.nanosPerMilli
  }
  def timeSinceNanos(prevTime: scala.Long): scala.Long = {
    return TimeUtils.nanoTime() - prevTime
  }
  def timeSinceMillis(prevTime: scala.Long): scala.Long = {
    return TimeUtils.millis() - prevTime
  }
}