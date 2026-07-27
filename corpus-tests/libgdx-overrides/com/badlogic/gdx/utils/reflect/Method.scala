package com.badlogic.gdx.utils.reflect

/** INJECTED SCALA (Substitutions.inject) — see [[ClassReflection]]. Trimmed to what the residue
  * actually uses: Skin's JSON-driven loading scans methods by name and invokes one. */
final class Method(private val method: java.lang.reflect.Method):

  def getName(): String = this.method.getName

  def getDeclaringClass(): Class[?] = this.method.getDeclaringClass

  def setAccessible(accessible: Boolean): Unit = this.method.setAccessible(accessible)

  def invoke(obj: Object, args: Object*): Object =
    try this.method.invoke(obj, args*)
    catch case e: Throwable => throw new ReflectionException("Could not invoke method: " + this.method.getName, e)
