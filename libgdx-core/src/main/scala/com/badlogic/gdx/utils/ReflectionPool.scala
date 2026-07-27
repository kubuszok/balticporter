package com.badlogic.gdx.utils

class ReflectionPool[T] extends com.badlogic.gdx.utils.Pool[T] {
  private var constructor: com.badlogic.gdx.utils.reflect.Constructor = null.asInstanceOf[com.badlogic.gdx.utils.reflect.Constructor]
  def this(`type`: java.lang.Class[T], initialCapacity: scala.Int, max: scala.Int) = {
    this()
    this.constructor = this.findConstructor(`type`)
    if (this.constructor == null) {
      throw new java.lang.RuntimeException("Class cannot be created (missing no-arg constructor): " + `type`.getName())
    } else ()
  }
  def this(`type`: java.lang.Class[T]) = {
    this(`type`, 16, java.lang.Integer.MAX_VALUE)
  }
  def this(`type`: java.lang.Class[T], initialCapacity: scala.Int) = {
    this(`type`, initialCapacity, java.lang.Integer.MAX_VALUE)
  }
  private def findConstructor(`type`: java.lang.Class[T]): com.badlogic.gdx.utils.reflect.Constructor = {
    try {
      return com.badlogic.gdx.utils.reflect.ClassReflection.getConstructor(`type`, null.asInstanceOf[scala.Array[java.lang.Class[T]]])
    } catch {
      case ex1: java.lang.Exception => {
        try {
          val constructor: com.badlogic.gdx.utils.reflect.Constructor = com.badlogic.gdx.utils.reflect.ClassReflection.getDeclaredConstructor(`type`, null.asInstanceOf[scala.Array[java.lang.Class[T]]])
          constructor.setAccessible(true)
          return constructor
        } catch {
          case ex2: com.badlogic.gdx.utils.reflect.ReflectionException => {
            return null
          }
        }
      }
    }
  }
  def newObject(): T = {
    try {
      return this.constructor.newInstance(null.asInstanceOf[scala.Array[java.lang.Object]]).asInstanceOf[T].asInstanceOf[T]
    } catch {
      case ex: java.lang.Exception => {
        throw new com.badlogic.gdx.utils.GdxRuntimeException("Unable to create new instance: " + this.constructor.getDeclaringClass().getName(), ex)
      }
    }
  }
}
object ReflectionPool {
  export com.badlogic.gdx.utils.Pool.*
}