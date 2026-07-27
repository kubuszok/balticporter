package com.badlogic.gdx.utils.reflect

import java.lang.reflect.Modifier

/** INJECTED SCALA (Substitutions.inject) — see [[ClassReflection]]. */
final class Constructor(private val constructor: java.lang.reflect.Constructor[?]):

  def getParameterTypes(): scala.Array[Class[?]] =
    this.constructor.getParameterTypes.map(c => c: Class[?])

  def getDeclaringClass(): Class[?] = this.constructor.getDeclaringClass

  def isAccessible(): Boolean = this.constructor.canAccess(null)

  def setAccessible(accessible: Boolean): Unit = this.constructor.setAccessible(accessible)

  def isDefaultAccess(): Boolean = !isPrivate() && !isProtected() && !isPublic()
  def isPrivate(): Boolean   = Modifier.isPrivate(this.constructor.getModifiers)
  def isProtected(): Boolean = Modifier.isProtected(this.constructor.getModifiers)
  def isPublic(): Boolean    = Modifier.isPublic(this.constructor.getModifiers)

  def newInstance(args: Object*): Object =
    try this.constructor.newInstance(args*).asInstanceOf[Object]
    catch
      case e: Throwable =>
        throw new ReflectionException(
          "Could not instantiate instance of class: " + this.constructor.getDeclaringClass.getName, e)
