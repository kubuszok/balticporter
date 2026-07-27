package com.badlogic.gdx.utils.reflect

/** INJECTED SCALA (Substitutions.inject) — the IRREDUCIBLE residue of libGDX's `utils.reflect`.
  *
  * Most of what the corpus called through this wrapper was never reflection: `getSimpleName`,
  * `isInstance`, `isAssignableFrom`, `isArray` and friends are plain `java.lang.Class` members that
  * Scala.js and Native both provide, and `ReflectionToPortableTransform` now re-points those calls
  * straight at `Class`. What is left here is only what genuinely needs the JVM:
  *
  *   - `forName`            — resolving a class by name (ResourceData)
  *   - `getConstructor` / `getDeclaredConstructor` — reflective instantiation (ReflectionPool)
  *   - `getMethods`         — finding a setter by name (Skin's JSON-driven loading)
  *
  * These are TRACKED PORTABILITY DEBT, not a solution: `PortabilityCheck` reports every one of
  * them, and each needs a real per-library adjustment before the port runs off the JVM — a
  * name→factory table instead of `forName`, `Pool[T](factory: () => T)` instead of a reflective
  * constructor, and explicit wiring instead of `Skin`'s method scan (which belongs with the Json
  * substitution, since that is what drives it).
  */
object ClassReflection:

  def forName(name: String): Class[?] =
    try Class.forName(name)
    catch case e: Throwable => throw new ReflectionException("Class not found: " + name, e)

  def getConstructor(c: Class[?], parameterTypes: Class[?]*): Constructor =
    try new Constructor(c.getConstructor(parameterTypes*))
    catch
      case e: Throwable => throw new ReflectionException("Constructor not found for class: " + c.getName, e)

  def getDeclaredConstructor(c: Class[?], parameterTypes: Class[?]*): Constructor =
    try new Constructor(c.getDeclaredConstructor(parameterTypes*))
    catch
      case e: Throwable => throw new ReflectionException("Constructor not found for class: " + c.getName, e)

  def getMethods(c: Class[?]): scala.Array[Method] = c.getMethods.map(m => new Method(m))
