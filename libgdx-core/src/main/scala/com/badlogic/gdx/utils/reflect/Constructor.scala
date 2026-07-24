package com.badlogic.gdx.utils.reflect

final class Constructor {
  private var constructor: java.lang.reflect.Constructor[?] = null.asInstanceOf[java.lang.reflect.Constructor[?]]
  def this(constructor: java.lang.reflect.Constructor[?]) = {
    this()
    this.constructor = constructor
  }
  def getParameterTypes(): scala.Array[java.lang.Class[?]] = {
    return this.constructor.getParameterTypes()
  }
  def getDeclaringClass(): java.lang.Class[?] = {
    return this.constructor.getDeclaringClass()
  }
  def isAccessible(): scala.Boolean = {
    return this.constructor.isAccessible()
  }
  def setAccessible(accessible: scala.Boolean): scala.Unit = {
    this.constructor.setAccessible(accessible)
  }
  def newInstance(args: scala.Array[java.lang.Object]): java.lang.Object = {
    try {
      return this.constructor.newInstance(args)
    } catch {
      case e: java.lang.IllegalArgumentException => {
        throw new com.badlogic.gdx.utils.reflect.ReflectionException("Illegal argument(s) supplied to constructor for class: " + this.getDeclaringClass().getName(), e)
      }
      case e: java.lang.InstantiationException => {
        throw new com.badlogic.gdx.utils.reflect.ReflectionException("Could not instantiate instance of class: " + this.getDeclaringClass().getName(), e)
      }
      case e: java.lang.IllegalAccessException => {
        throw new com.badlogic.gdx.utils.reflect.ReflectionException("Could not instantiate instance of class: " + this.getDeclaringClass().getName(), e)
      }
      case e: java.lang.reflect.InvocationTargetException => {
        throw new com.badlogic.gdx.utils.reflect.ReflectionException("Exception occurred in constructor for class: " + this.getDeclaringClass().getName(), e)
      }
    }
  }
}