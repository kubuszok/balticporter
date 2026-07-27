package com.badlogic.gdx.utils.reflect

import java.lang.reflect.Modifier

/** INJECTED SCALA (Substitutions.inject).
  *
  * libGDX's `utils.reflect` package is a thin cross-platform wrapper over `java.lang.reflect`
  * (it exists so GWT/Android backends can supply their own implementation). sge does not port it:
  * the reflection-driven decoding it served was replaced by Kindlings' Jsoniter/UBJson codecs, so
  * the wrapper is substituted here rather than mechanically translated — a per-library adjustment
  * declared in the `Substitutions` manifest (see `LibgdxCoreMigrate`).
  *
  * This shim delegates straight to `java.lang.reflect`, which is faithful to what the wrapper does
  * on the JVM backend and keeps the ported corpus self-contained (no external dependency in the
  * compile gate). Swap point: to route through Kindlings instead, replace these bodies — nothing
  * else in the port changes, because the substitution seam is declared, not hand-patched.
  */
object ClassReflection:

  def forName(name: String): Class[?] =
    try Class.forName(name)
    catch case e: Throwable => throw new ReflectionException("Class not found: " + name, e)

  def getSimpleName(c: Class[?]): String = c.getSimpleName

  def isInstance(c: Class[?], obj: Object): Boolean = c.isInstance(obj)

  def isAssignableFrom(c1: Class[?], c2: Class[?]): Boolean = c1.isAssignableFrom(c2)

  def isMemberClass(c: Class[?]): Boolean = c.isMemberClass

  def isStaticClass(c: Class[?]): Boolean = Modifier.isStatic(c.getModifiers)

  def isArray(c: Class[?]): Boolean = c.isArray

  def isPrimitive(c: Class[?]): Boolean = c.isPrimitive

  def isEnum(c: Class[?]): Boolean = c.isEnum

  def isAnnotation(c: Class[?]): Boolean = c.isAnnotation

  def isInterface(c: Class[?]): Boolean = c.isInterface

  def isAbstract(c: Class[?]): Boolean = Modifier.isAbstract(c.getModifiers)

  def newInstance[T](c: Class[T]): T =
    try c.getDeclaredConstructor().newInstance()
    catch
      case e: Throwable =>
        throw new ReflectionException("Could not instantiate instance of class: " + c.getName, e)

  def getComponentType(c: Class[?]): Class[?] = c.getComponentType

  def getConstructor(c: Class[?], parameterTypes: Class[?]*): Constructor =
    try new Constructor(c.getConstructor(parameterTypes*))
    catch
      case e: Throwable =>
        throw new ReflectionException("Constructor not found for class: " + c.getName, e)

  def getDeclaredConstructor(c: Class[?], parameterTypes: Class[?]*): Constructor =
    try new Constructor(c.getDeclaredConstructor(parameterTypes*))
    catch
      case e: Throwable =>
        throw new ReflectionException("Constructor not found for class: " + c.getName, e)

  def getMethods(c: Class[?]): scala.Array[Method] = c.getMethods.map(m => new Method(m))

  def getDeclaredMethods(c: Class[?]): scala.Array[Method] = c.getDeclaredMethods.map(m => new Method(m))

  def getFields(c: Class[?]): scala.Array[Field] = c.getFields.map(f => new Field(f))

  def getDeclaredFields(c: Class[?]): scala.Array[Field] = c.getDeclaredFields.map(f => new Field(f))

  def getInterfaces(c: Class[?]): scala.Array[Class[?]] = c.getInterfaces.map(i => i: Class[?])

  def isAnnotationPresent(c: Class[?], annotationType: Class[? <: java.lang.annotation.Annotation]): Boolean =
    c.isAnnotationPresent(annotationType)

  def getAnnotations(c: Class[?]): scala.Array[Annotation] = c.getAnnotations.map(a => new Annotation(a))

  def getAnnotation(c: Class[?], annotationType: Class[? <: java.lang.annotation.Annotation]): Annotation =
    c.getAnnotations.find(a => a.annotationType() == annotationType).map(a => new Annotation(a)).orNull

  def getDeclaredAnnotations(c: Class[?]): scala.Array[Annotation] =
    c.getDeclaredAnnotations.map(a => new Annotation(a))

  def getDeclaredAnnotation(c: Class[?], annotationType: Class[? <: java.lang.annotation.Annotation]): Annotation =
    c.getDeclaredAnnotations.find(a => a.annotationType() == annotationType).map(a => new Annotation(a)).orNull
