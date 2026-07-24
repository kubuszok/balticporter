package com.badlogic.gdx.utils.reflect

final class Field {
  private var field: java.lang.reflect.Field = null.asInstanceOf[java.lang.reflect.Field]
  def this(field: java.lang.reflect.Field) = {
    this()
    this.field = field
  }
  def getName(): java.lang.String = {
    return this.field.getName()
  }
  def getType(): java.lang.Class[?] = {
    return this.field.getType()
  }
  def getDeclaringClass(): java.lang.Class[?] = {
    return this.field.getDeclaringClass()
  }
  def isAccessible(): scala.Boolean = {
    return this.field.isAccessible()
  }
  def setAccessible(accessible: scala.Boolean): scala.Unit = {
    this.field.setAccessible(accessible)
  }
  def isDefaultAccess(): scala.Boolean = {
    return ((!this.isPrivate()) && (!this.isProtected())) && (!this.isPublic())
  }
  def isFinal(): scala.Boolean = {
    return java.lang.reflect.Modifier.isFinal(this.field.getModifiers())
  }
  def isPrivate(): scala.Boolean = {
    return java.lang.reflect.Modifier.isPrivate(this.field.getModifiers())
  }
  def isProtected(): scala.Boolean = {
    return java.lang.reflect.Modifier.isProtected(this.field.getModifiers())
  }
  def isPublic(): scala.Boolean = {
    return java.lang.reflect.Modifier.isPublic(this.field.getModifiers())
  }
  def isStatic(): scala.Boolean = {
    return java.lang.reflect.Modifier.isStatic(this.field.getModifiers())
  }
  def isTransient(): scala.Boolean = {
    return java.lang.reflect.Modifier.isTransient(this.field.getModifiers())
  }
  def isVolatile(): scala.Boolean = {
    return java.lang.reflect.Modifier.isVolatile(this.field.getModifiers())
  }
  def isSynthetic(): scala.Boolean = {
    return this.field.isSynthetic()
  }
  def getElementType(index: scala.Int): java.lang.Class[?] = {
    val genericType: java.lang.reflect.Type = this.field.getGenericType()
    if (genericType.isInstanceOf[java.lang.reflect.ParameterizedType]) {
      val actualTypes: scala.Array[java.lang.reflect.Type] = genericType.asInstanceOf[java.lang.reflect.ParameterizedType].getActualTypeArguments()
      if ((actualTypes.length - 1) >= index) {
        val actualType: java.lang.reflect.Type = actualTypes(index)
        if (actualType.isInstanceOf[java.lang.Class[?]]) {
          return actualType.asInstanceOf[java.lang.Class[?]]
        } else {
          if (actualType.isInstanceOf[java.lang.reflect.ParameterizedType]) {
            return actualType.asInstanceOf[java.lang.reflect.ParameterizedType].getRawType().asInstanceOf[java.lang.Class[?]]
          } else {
            if (actualType.isInstanceOf[java.lang.reflect.GenericArrayType]) {
              val componentType: java.lang.reflect.Type = actualType.asInstanceOf[java.lang.reflect.GenericArrayType].getGenericComponentType()
              if (componentType.isInstanceOf[java.lang.Class[?]]) {
                return com.badlogic.gdx.utils.reflect.ArrayReflection.newInstance(componentType.asInstanceOf[java.lang.Class[?]], 0).getClass()
              } else ()
            } else ()
          }
        }
      } else ()
    } else ()
    return null
  }
  def isAnnotationPresent(annotationType: java.lang.Class[? <: java.lang.annotation.Annotation]): scala.Boolean = {
    return this.field.isAnnotationPresent(annotationType)
  }
  def getDeclaredAnnotations(): scala.Array[com.badlogic.gdx.utils.reflect.Annotation] = {
    val annotations: scala.Array[java.lang.annotation.Annotation] = this.field.getDeclaredAnnotations()
    val result: scala.Array[com.badlogic.gdx.utils.reflect.Annotation] = new scala.Array[com.badlogic.gdx.utils.reflect.Annotation](annotations.length);
    { var i: scala.Int = 0; while (i < annotations.length) { {
      result(i) = new com.badlogic.gdx.utils.reflect.Annotation(annotations(i))
    }; i = i + 1 } }
    return result
  }
  def getDeclaredAnnotation(annotationType: java.lang.Class[? <: java.lang.annotation.Annotation]): com.badlogic.gdx.utils.reflect.Annotation = {
    val annotations: scala.Array[java.lang.annotation.Annotation] = this.field.getDeclaredAnnotations()
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
  def get(obj: java.lang.Object): java.lang.Object = {
    try {
      return this.field.get(obj)
    } catch {
      case e: java.lang.IllegalArgumentException => {
        throw new com.badlogic.gdx.utils.reflect.ReflectionException("Object is not an instance of " + this.getDeclaringClass(), e)
      }
      case e: java.lang.IllegalAccessException => {
        throw new com.badlogic.gdx.utils.reflect.ReflectionException("Illegal access to field: " + this.getName(), e)
      }
    }
  }
  def set(obj: java.lang.Object, value: java.lang.Object): scala.Unit = {
    try {
      this.field.set(obj, value)
    } catch {
      case e: java.lang.IllegalArgumentException => {
        throw new com.badlogic.gdx.utils.reflect.ReflectionException("Argument not valid for field: " + this.getName(), e)
      }
      case e: java.lang.IllegalAccessException => {
        throw new com.badlogic.gdx.utils.reflect.ReflectionException("Illegal access to field: " + this.getName(), e)
      }
    }
  }
}