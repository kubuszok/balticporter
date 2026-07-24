package com.badlogic.gdx.utils.reflect

object ClassReflection {
  def forName(name: java.lang.String): java.lang.Class = {
    try {
      return java.lang.Class.forName(name)
    } catch {
      case e: java.lang.ClassNotFoundException => {
        throw new com.badlogic.gdx.utils.reflect.ReflectionException("Class not found: " + name, e)
      }
    }
  }
  def getSimpleName(c: java.lang.Class): java.lang.String = {
    return c.getSimpleName()
  }
  def isInstance(c: java.lang.Class, obj: java.lang.Object): scala.Boolean = {
    return c.isInstance(obj)
  }
  def isAssignableFrom(c1: java.lang.Class, c2: java.lang.Class): scala.Boolean = {
    return c1.isAssignableFrom(c2)
  }
  def isMemberClass(c: java.lang.Class): scala.Boolean = {
    return c.isMemberClass()
  }
  def isStaticClass(c: java.lang.Class): scala.Boolean = {
    return java.lang.reflect.Modifier.isStatic(c.getModifiers())
  }
  def isArray(c: java.lang.Class): scala.Boolean = {
    return c.isArray()
  }
  def isPrimitive(c: java.lang.Class): scala.Boolean = {
    return c.isPrimitive()
  }
  def isEnum(c: java.lang.Class): scala.Boolean = {
    return c.isEnum()
  }
  def isAnnotation(c: java.lang.Class): scala.Boolean = {
    return c.isAnnotation()
  }
  def isInterface(c: java.lang.Class): scala.Boolean = {
    return c.isInterface()
  }
  def isAbstract(c: java.lang.Class): scala.Boolean = {
    return java.lang.reflect.Modifier.isAbstract(c.getModifiers())
  }
  def newInstance[T](c: java.lang.Class[T]): T = {
    try {
      return c.newInstance()
    } catch {
      case e: java.lang.InstantiationException => {
        throw new com.badlogic.gdx.utils.reflect.ReflectionException("Could not instantiate instance of class: " + c.getName(), e)
      }
      case e: java.lang.IllegalAccessException => {
        throw new com.badlogic.gdx.utils.reflect.ReflectionException("Could not instantiate instance of class: " + c.getName(), e)
      }
    }
  }
  def getComponentType(c: java.lang.Class): java.lang.Class = {
    return c.getComponentType()
  }
  def getConstructors(c: java.lang.Class): scala.Array[com.badlogic.gdx.utils.reflect.Constructor] = {
    val constructors: scala.Array[java.lang.reflect.Constructor] = c.getConstructors()
    val result: scala.Array[com.badlogic.gdx.utils.reflect.Constructor] = new Array[com.badlogic.gdx.utils.reflect.Constructor](constructors.length)
    { var i: scala.Int = 0; val j: scala.Int = constructors.length; while (i < j) { {
      result(i) = new com.badlogic.gdx.utils.reflect.Constructor(constructors(i))
    }; i = i + 1 } }
    return result
  }
  def getConstructor(c: java.lang.Class, parameterTypes: scala.Array[java.lang.Class]): com.badlogic.gdx.utils.reflect.Constructor = {
    try {
      return new com.badlogic.gdx.utils.reflect.Constructor(c.getConstructor(parameterTypes))
    } catch {
      case e: java.lang.SecurityException => {
        throw new com.badlogic.gdx.utils.reflect.ReflectionException(("Security violation occurred while getting constructor for class: '" + c.getName()) + "'.", e)
      }
      case e: java.lang.NoSuchMethodException => {
        throw new com.badlogic.gdx.utils.reflect.ReflectionException("Constructor not found for class: " + c.getName(), e)
      }
    }
  }
  def getDeclaredConstructor(c: java.lang.Class, parameterTypes: scala.Array[java.lang.Class]): com.badlogic.gdx.utils.reflect.Constructor = {
    try {
      return new com.badlogic.gdx.utils.reflect.Constructor(c.getDeclaredConstructor(parameterTypes))
    } catch {
      case e: java.lang.SecurityException => {
        throw new com.badlogic.gdx.utils.reflect.ReflectionException("Security violation while getting constructor for class: " + c.getName(), e)
      }
      case e: java.lang.NoSuchMethodException => {
        throw new com.badlogic.gdx.utils.reflect.ReflectionException("Constructor not found for class: " + c.getName(), e)
      }
    }
  }
  def getEnumConstants(c: java.lang.Class): scala.Array[java.lang.Object] = {
    return c.getEnumConstants()
  }
  def getMethods(c: java.lang.Class): scala.Array[com.badlogic.gdx.utils.reflect.Method] = {
    val methods: scala.Array[java.lang.reflect.Method] = c.getMethods()
    val result: scala.Array[com.badlogic.gdx.utils.reflect.Method] = new Array[com.badlogic.gdx.utils.reflect.Method](methods.length)
    { var i: scala.Int = 0; val j: scala.Int = methods.length; while (i < j) { {
      result(i) = new com.badlogic.gdx.utils.reflect.Method(methods(i))
    }; i = i + 1 } }
    return result
  }
  def getMethod(c: java.lang.Class, name: java.lang.String, parameterTypes: scala.Array[java.lang.Class]): com.badlogic.gdx.utils.reflect.Method = {
    try {
      return new com.badlogic.gdx.utils.reflect.Method(c.getMethod(name, parameterTypes))
    } catch {
      case e: java.lang.SecurityException => {
        throw new com.badlogic.gdx.utils.reflect.ReflectionException((("Security violation while getting method: " + name) + ", for class: ") + c.getName(), e)
      }
      case e: java.lang.NoSuchMethodException => {
        throw new com.badlogic.gdx.utils.reflect.ReflectionException((("Method not found: " + name) + ", for class: ") + c.getName(), e)
      }
    }
  }
  def getDeclaredMethods(c: java.lang.Class): scala.Array[com.badlogic.gdx.utils.reflect.Method] = {
    val methods: scala.Array[java.lang.reflect.Method] = c.getDeclaredMethods()
    val result: scala.Array[com.badlogic.gdx.utils.reflect.Method] = new Array[com.badlogic.gdx.utils.reflect.Method](methods.length)
    { var i: scala.Int = 0; val j: scala.Int = methods.length; while (i < j) { {
      result(i) = new com.badlogic.gdx.utils.reflect.Method(methods(i))
    }; i = i + 1 } }
    return result
  }
  def getDeclaredMethod(c: java.lang.Class, name: java.lang.String, parameterTypes: scala.Array[java.lang.Class]): com.badlogic.gdx.utils.reflect.Method = {
    try {
      return new com.badlogic.gdx.utils.reflect.Method(c.getDeclaredMethod(name, parameterTypes))
    } catch {
      case e: java.lang.SecurityException => {
        throw new com.badlogic.gdx.utils.reflect.ReflectionException((("Security violation while getting method: " + name) + ", for class: ") + c.getName(), e)
      }
      case e: java.lang.NoSuchMethodException => {
        throw new com.badlogic.gdx.utils.reflect.ReflectionException((("Method not found: " + name) + ", for class: ") + c.getName(), e)
      }
    }
  }
  def getFields(c: java.lang.Class): scala.Array[com.badlogic.gdx.utils.reflect.Field] = {
    val fields: scala.Array[java.lang.reflect.Field] = c.getFields()
    val result: scala.Array[com.badlogic.gdx.utils.reflect.Field] = new Array[com.badlogic.gdx.utils.reflect.Field](fields.length)
    { var i: scala.Int = 0; val j: scala.Int = fields.length; while (i < j) { {
      result(i) = new com.badlogic.gdx.utils.reflect.Field(fields(i))
    }; i = i + 1 } }
    return result
  }
  def getField(c: java.lang.Class, name: java.lang.String): com.badlogic.gdx.utils.reflect.Field = {
    try {
      return new com.badlogic.gdx.utils.reflect.Field(c.getField(name))
    } catch {
      case e: java.lang.SecurityException => {
        throw new com.badlogic.gdx.utils.reflect.ReflectionException((("Security violation while getting field: " + name) + ", for class: ") + c.getName(), e)
      }
      case e: java.lang.NoSuchFieldException => {
        throw new com.badlogic.gdx.utils.reflect.ReflectionException((("Field not found: " + name) + ", for class: ") + c.getName(), e)
      }
    }
  }
  def getDeclaredFields(c: java.lang.Class): scala.Array[com.badlogic.gdx.utils.reflect.Field] = {
    val fields: scala.Array[java.lang.reflect.Field] = c.getDeclaredFields()
    val result: scala.Array[com.badlogic.gdx.utils.reflect.Field] = new Array[com.badlogic.gdx.utils.reflect.Field](fields.length)
    { var i: scala.Int = 0; val j: scala.Int = fields.length; while (i < j) { {
      result(i) = new com.badlogic.gdx.utils.reflect.Field(fields(i))
    }; i = i + 1 } }
    return result
  }
  def getDeclaredField(c: java.lang.Class, name: java.lang.String): com.badlogic.gdx.utils.reflect.Field = {
    try {
      return new com.badlogic.gdx.utils.reflect.Field(c.getDeclaredField(name))
    } catch {
      case e: java.lang.SecurityException => {
        throw new com.badlogic.gdx.utils.reflect.ReflectionException((("Security violation while getting field: " + name) + ", for class: ") + c.getName(), e)
      }
      case e: java.lang.NoSuchFieldException => {
        throw new com.badlogic.gdx.utils.reflect.ReflectionException((("Field not found: " + name) + ", for class: ") + c.getName(), e)
      }
    }
  }
  def isAnnotationPresent(c: java.lang.Class, annotationType: java.lang.Class[? <: java.lang.annotation.Annotation]): scala.Boolean = {
    return c.isAnnotationPresent(annotationType)
  }
  def getAnnotations(c: java.lang.Class): scala.Array[com.badlogic.gdx.utils.reflect.Annotation] = {
    val annotations: scala.Array[java.lang.annotation.Annotation] = c.getAnnotations()
    val result: scala.Array[com.badlogic.gdx.utils.reflect.Annotation] = new Array[com.badlogic.gdx.utils.reflect.Annotation](annotations.length)
    { var i: scala.Int = 0; while (i < annotations.length) { {
      result(i) = new com.badlogic.gdx.utils.reflect.Annotation(annotations(i))
    }; i = i + 1 } }
    return result
  }
  def getAnnotation(c: java.lang.Class, annotationType: java.lang.Class[? <: java.lang.annotation.Annotation]): com.badlogic.gdx.utils.reflect.Annotation = {
    val annotation: java.lang.annotation.Annotation = c.getAnnotation(annotationType)
    if (annotation != null) {
      return new com.badlogic.gdx.utils.reflect.Annotation(annotation)
    } else ()
    return null
  }
  def getDeclaredAnnotations(c: java.lang.Class): scala.Array[com.badlogic.gdx.utils.reflect.Annotation] = {
    val annotations: scala.Array[java.lang.annotation.Annotation] = c.getDeclaredAnnotations()
    val result: scala.Array[com.badlogic.gdx.utils.reflect.Annotation] = new Array[com.badlogic.gdx.utils.reflect.Annotation](annotations.length)
    { var i: scala.Int = 0; while (i < annotations.length) { {
      result(i) = new com.badlogic.gdx.utils.reflect.Annotation(annotations(i))
    }; i = i + 1 } }
    return result
  }
  def getDeclaredAnnotation(c: java.lang.Class, annotationType: java.lang.Class[? <: java.lang.annotation.Annotation]): com.badlogic.gdx.utils.reflect.Annotation = {
    val annotations: scala.Array[java.lang.annotation.Annotation] = c.getDeclaredAnnotations()
    for (annotation <- annotations) {
      if (annotation.annotationType().equals(annotationType)) {
        return new com.badlogic.gdx.utils.reflect.Annotation(annotation)
      } else ()
    }
    return null
  }
  def getInterfaces(c: java.lang.Class): scala.Array[java.lang.Class] = {
    return c.getInterfaces()
  }
}