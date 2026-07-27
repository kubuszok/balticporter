package com.badlogic.gdx.utils.reflect

import java.lang.reflect.Modifier

/** INJECTED SCALA (Substitutions.inject) — see [[ClassReflection]]. */
final class Method(private val method: java.lang.reflect.Method):

  def getName(): String = this.method.getName

  def getReturnType(): Class[?] = this.method.getReturnType

  def getParameterTypes(): scala.Array[Class[?]] =
    this.method.getParameterTypes.map(c => c: Class[?])

  def getDeclaringClass(): Class[?] = this.method.getDeclaringClass

  def isAccessible(): Boolean = this.method.canAccess(null)

  def setAccessible(accessible: Boolean): Unit = this.method.setAccessible(accessible)

  def isAbstract(): Boolean      = Modifier.isAbstract(this.method.getModifiers)
  def isDefaultAccess(): Boolean = !isPrivate() && !isProtected() && !isPublic()
  def isFinal(): Boolean         = Modifier.isFinal(this.method.getModifiers)
  def isPrivate(): Boolean       = Modifier.isPrivate(this.method.getModifiers)
  def isProtected(): Boolean     = Modifier.isProtected(this.method.getModifiers)
  def isPublic(): Boolean        = Modifier.isPublic(this.method.getModifiers)
  def isNative(): Boolean        = Modifier.isNative(this.method.getModifiers)
  def isStatic(): Boolean        = Modifier.isStatic(this.method.getModifiers)
  def isVarArgs(): Boolean       = this.method.isVarArgs

  def invoke(obj: Object, args: Object*): Object =
    try this.method.invoke(obj, args*)
    catch case e: Throwable => throw new ReflectionException("Could not invoke method: " + this.method.getName, e)

  def isAnnotationPresent(annotationType: Class[? <: java.lang.annotation.Annotation]): Boolean =
    this.method.isAnnotationPresent(annotationType)

  def getDeclaredAnnotations(): scala.Array[Annotation] =
    this.method.getDeclaredAnnotations.map(a => new Annotation(a))

  def getDeclaredAnnotation(annotationType: Class[? <: java.lang.annotation.Annotation]): Annotation =
    this.method.getDeclaredAnnotations.find(a => a.annotationType() == annotationType)
      .map(a => new Annotation(a)).orNull
