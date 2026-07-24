package com.badlogic.gdx.utils

object NumberUtils {
  def floatToIntBits(value: scala.Float): scala.Int = {
    return java.lang.Float.floatToIntBits(value)
  }
  def floatToRawIntBits(value: scala.Float): scala.Int = {
    return java.lang.Float.floatToRawIntBits(value)
  }
  def floatToIntColor(value: scala.Float): scala.Int = {
    var intBits: scala.Int = java.lang.Float.floatToRawIntBits(value)
    intBits = intBits | (((intBits >>> 24) * (255.0f / 254.0f)).asInstanceOf[scala.Int] << 24)
    return intBits
  }
  def intToFloatColor(value: scala.Int): scala.Float = {
    return java.lang.Float.intBitsToFloat(value & -16777217)
  }
  def intBitsToFloat(value: scala.Int): scala.Float = {
    return java.lang.Float.intBitsToFloat(value)
  }
  def doubleToLongBits(value: scala.Double): scala.Long = {
    return java.lang.Double.doubleToLongBits(value)
  }
  def longBitsToDouble(value: scala.Long): scala.Double = {
    return java.lang.Double.longBitsToDouble(value)
  }
}