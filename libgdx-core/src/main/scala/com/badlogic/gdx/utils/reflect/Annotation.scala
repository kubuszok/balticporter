package com.badlogic.gdx.utils.reflect

final class Annotation {
  private var annotation: java.lang.annotation.Annotation = null.asInstanceOf[java.lang.annotation.Annotation]
  def this(annotation: java.lang.annotation.Annotation) = {
    this()
    this.annotation = annotation
  }
  def getAnnotation[T <: java.lang.annotation.Annotation](annotationType: java.lang.Class[T]): T = {
    if (this.annotation.annotationType().equals(annotationType)) {
      return this.annotation.asInstanceOf[T]
    } else ()
    return null
  }
  def getAnnotationType(): java.lang.Class[? <: java.lang.annotation.Annotation] = {
    return this.annotation.annotationType()
  }
}