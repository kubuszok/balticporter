package com.badlogic.gdx.utils.reflect

object ArrayReflection {
  def newInstance(c: java.lang.Class[?], size: scala.Int): java.lang.Object = {
    return java.lang.reflect.Array.newInstance(c, size)
  }
  def getLength(array: java.lang.Object): scala.Int = {
    return java.lang.reflect.Array.getLength(array)
  }
  def get(array: java.lang.Object, index: scala.Int): java.lang.Object = {
    return java.lang.reflect.Array.get(array, index)
  }
  def set(array: java.lang.Object, index: scala.Int, value: java.lang.Object): scala.Unit = {
    java.lang.reflect.Array.set(array, index, value)
  }
}