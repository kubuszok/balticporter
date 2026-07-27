package com.badlogic.gdx.utils.reflect

/** INJECTED SCALA (Substitutions.inject) — see [[ClassReflection]]. */
object ArrayReflection:

  def newInstance(c: Class[?], size: Int): Object =
    java.lang.reflect.Array.newInstance(c, size)

  def getLength(array: Object): Int = java.lang.reflect.Array.getLength(array)

  def get(array: Object, index: Int): Object = java.lang.reflect.Array.get(array, index)

  def set(array: Object, index: Int, value: Object): Unit =
    java.lang.reflect.Array.set(array, index, value)
