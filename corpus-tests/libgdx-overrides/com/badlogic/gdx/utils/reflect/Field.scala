package com.badlogic.gdx.utils.reflect

import java.lang.reflect.Modifier

/** INJECTED SCALA (Substitutions.inject) — see [[ClassReflection]]. */
final class Field(private val field: java.lang.reflect.Field):

  def getName(): String = this.field.getName

  def getType(): Class[?] = this.field.getType

  def getDeclaringClass(): Class[?] = this.field.getDeclaringClass

  def isAccessible(): Boolean = this.field.canAccess(null)

  def setAccessible(accessible: Boolean): Unit = this.field.setAccessible(accessible)

  def isDefaultAccess(): Boolean = !isPrivate() && !isProtected() && !isPublic()
  def isFinal(): Boolean     = Modifier.isFinal(this.field.getModifiers)
  def isPrivate(): Boolean   = Modifier.isPrivate(this.field.getModifiers)
  def isProtected(): Boolean = Modifier.isProtected(this.field.getModifiers)
  def isPublic(): Boolean    = Modifier.isPublic(this.field.getModifiers)
  def isStatic(): Boolean    = Modifier.isStatic(this.field.getModifiers)
  def isTransient(): Boolean = Modifier.isTransient(this.field.getModifiers)
  def isVolatile(): Boolean  = Modifier.isVolatile(this.field.getModifiers)
  def isSynthetic(): Boolean = this.field.isSynthetic

  /** the `index`-th type argument of a generic field type (`Array<String>` → `String`), or null. */
  def getElementType(index: Int): Class[?] =
    this.field.getGenericType match
      case p: java.lang.reflect.ParameterizedType =>
        val args = p.getActualTypeArguments
        if index < args.length then
          args(index) match
            case c: Class[?] => c
            case _           => null
        else null
      case _ => null

  def isAnnotationPresent(annotationType: Class[? <: java.lang.annotation.Annotation]): Boolean =
    this.field.isAnnotationPresent(annotationType)

  def getDeclaredAnnotations(): scala.Array[Annotation] =
    this.field.getDeclaredAnnotations.map(a => new Annotation(a))

  def getDeclaredAnnotation(annotationType: Class[? <: java.lang.annotation.Annotation]): Annotation =
    this.field.getDeclaredAnnotations.find(a => a.annotationType() == annotationType)
      .map(a => new Annotation(a)).orNull

  def get(obj: Object): Object =
    try this.field.get(obj)
    catch case e: Throwable => throw new ReflectionException("Could not get field: " + this.field.getName, e)

  def set(obj: Object, value: Object): Unit =
    try this.field.set(obj, value)
    catch case e: Throwable => throw new ReflectionException("Could not set field: " + this.field.getName, e)
