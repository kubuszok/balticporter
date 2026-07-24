package com.badlogic.gdx.utils.reflect

final class Method {
  private var method: java.lang.reflect.Method = null.asInstanceOf[java.lang.reflect.Method]
  def this(method: java.lang.reflect.Method) = {
    this()
    this.method = method
  }
  def getName(): java.lang.String = {
    return this.method.getName()
  }
  def getReturnType(): java.lang.Class = {
    return this.method.getReturnType()
  }
  def getParameterTypes(): scala.Array[java.lang.Class] = {
    return this.method.getParameterTypes()
  }
  def getDeclaringClass(): java.lang.Class = {
    return this.method.getDeclaringClass()
  }
  def isAccessible(): scala.Boolean = {
    return this.method.isAccessible()
  }
  def setAccessible(accessible: scala.Boolean): scala.Unit = {
    this.method.setAccessible(accessible)
  }
  def isAbstract(): scala.Boolean = {
    return java.lang.reflect.Modifier.isAbstract(this.method.getModifiers())
  }
  def isDefaultAccess(): scala.Boolean = {
    return ((!this.isPrivate()) && (!this.isProtected())) && (!this.isPublic())
  }
  def isFinal(): scala.Boolean = {
    return java.lang.reflect.Modifier.isFinal(this.method.getModifiers())
  }
  def isPrivate(): scala.Boolean = {
    return java.lang.reflect.Modifier.isPrivate(this.method.getModifiers())
  }
  def isProtected(): scala.Boolean = {
    return java.lang.reflect.Modifier.isProtected(this.method.getModifiers())
  }
  def isPublic(): scala.Boolean = {
    return java.lang.reflect.Modifier.isPublic(this.method.getModifiers())
  }
  def isNative(): scala.Boolean = {
    return java.lang.reflect.Modifier.isNative(this.method.getModifiers())
  }
  def isStatic(): scala.Boolean = {
    return java.lang.reflect.Modifier.isStatic(this.method.getModifiers())
  }
  def isVarArgs(): scala.Boolean = {
    return this.method.isVarArgs()
  }
  def invoke(obj: java.lang.Object, args: scala.Array[java.lang.Object]): java.lang.Object = {
    try {
      return this.method.invoke(obj, args)
    } catch {
      case e: java.lang.IllegalArgumentException => {
        throw new com.badlogic.gdx.utils.reflect.ReflectionException("Illegal argument(s) supplied to method: " + this.getName(), e)
      }
      case e: java.lang.IllegalAccessException => {
        throw new com.badlogic.gdx.utils.reflect.ReflectionException("Illegal access to method: " + this.getName(), e)
      }
      case e: java.lang.reflect.InvocationTargetException => {
        throw new com.badlogic.gdx.utils.reflect.ReflectionException("Exception occurred in method: " + this.getName(), e)
      }
    }
  }
  def isAnnotationPresent(annotationType: java.lang.Class[? <: java.lang.annotation.Annotation]): scala.Boolean = {
    return this.method.isAnnotationPresent(annotationType)
  }
  def getDeclaredAnnotations(): scala.Array[com.badlogic.gdx.utils.reflect.Annotation] = {
    val annotations: scala.Array[java.lang.annotation.Annotation] = this.method.getDeclaredAnnotations()
    val result: scala.Array[com.badlogic.gdx.utils.reflect.Annotation] = new Array[com.badlogic.gdx.utils.reflect.Annotation](annotations.length)
    { var i: scala.Int = 0; while (i < annotations.length) { {
      result(i) = new com.badlogic.gdx.utils.reflect.Annotation(annotations(i))
    }; i = i + 1 } }
    return result
  }
  def getDeclaredAnnotation(annotationType: java.lang.Class[? <: java.lang.annotation.Annotation]): com.badlogic.gdx.utils.reflect.Annotation = {
    val annotations: scala.Array[java.lang.annotation.Annotation] = this.method.getDeclaredAnnotations()
    if (annotations == null) {
      return null
    } else ()
    for (annotation <- annotations) {
      if (annotation.annotationType().equals(annotationType)) {
        return new com.badlogic.gdx.utils.reflect.Annotation(annotation)
      } else ()
    }
    return null
  }
}