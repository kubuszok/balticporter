package sge.ecs

/** Global registry replacing Ashley's `Engine.createComponent`/`ClassReflection.newInstance`
  * (no reflection on Scala.js/Native): a registered factory if present, else
  * `getConstructor().newInstance()`, else `null` — the same three outcomes java had, with
  * `NoSuchMethodException`/`InstantiationException`/`IllegalAccessException` mapped to `null` and
  * a constructor's own exception rethrown unwrapped, never swallowed (ENGINE-LIMITS.md P10).
  */
object ComponentFactories {

  private val factories = new java.util.concurrent.ConcurrentHashMap[Class[?], () => Component]()

  /** Register the cross-platform way to build a component. Required on Scala.js and Scala Native,
    * where the reflective fallback below cannot exist. */
  def register[T <: Component](componentType: Class[T], factory: () => T): Unit = {
    factories.put(componentType, factory.asInstanceOf[() => Component])
  }

  /** A registered factory if there is one, else the JVM reflective path, else `null` — exactly the
    * three outcomes `Engine.createComponent` already had. */
  def create[T <: Component](componentType: Class[T]): T = {
    val f = factories.get(componentType)
    if (f != null) { f().asInstanceOf[T] }
    else { reflectively(componentType) }
  }

  private def reflectively[T <: Component](componentType: Class[T]): T = {
    try { componentType.getConstructor().newInstance() }
    catch {
      case _: NoSuchMethodException | _: InstantiationException | _: IllegalAccessException =>
        null.asInstanceOf[T]
      case e: java.lang.reflect.InvocationTargetException => throw e.getCause
    }
  }
}
