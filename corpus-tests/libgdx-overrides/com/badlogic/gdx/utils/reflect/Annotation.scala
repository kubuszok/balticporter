package com.badlogic.gdx.utils.reflect

/** INJECTED SCALA (Substitutions.inject) — see [[ClassReflection]]. */
final class Annotation(private val annotation: java.lang.annotation.Annotation):
  def getAnnotation[T <: java.lang.annotation.Annotation](annotationType: Class[T]): T =
    if annotationType.isInstance(this.annotation) then this.annotation.asInstanceOf[T] else null.asInstanceOf[T]

  def getAnnotationType(): Class[? <: java.lang.annotation.Annotation] = this.annotation.annotationType()
