package sge.ecs

/** Component instantiation for a port that has no reflection.
  *
  * Ashley's `Engine.createComponent(Class<T>)` calls `ClassReflection.newInstance(componentType)`
  * and returns `null` when that throws. The libGDX port drops `ClassReflection` outright — runtime
  * reflection is the one thing Scala.js and Scala Native do not have — so the body needs a
  * replacement, which `MethodBodyTransform` supplies from the manifest.
  *
  * The registry is a `object` rather than a field on `Engine` deliberately. Ashley's own mechanism
  * is GLOBAL — `ClassReflection` is a static wrapper over `java.lang.reflect` and knows nothing
  * about which `Engine` is asking — so a global registry is the faithful analogue. (The reference
  * hand-port `../sge/sge-extension/ecs` puts it on the `Engine` instead; that is a nicer API and a
  * larger divergence, and it is available to a port that also wants to change the signature.)
  *
  * ==Why this is still hand-written, with two other ports holding the same shape==
  * libGDX core's injected `Pools` and gdx-gltf's `GLTFExtensionFactories` are the other two, and
  * extracting the three into one engine mechanism was read out and REFUSED — `ENGINE-LIMITS.md`
  * P10, which carries the numbers. Two things there bear on this file directly: the covering
  * abstraction is a `Class`-keyed map (`Pools` holds shared pool VALUES, not factories), which the
  * runtime module does not admit — it takes semantics the target LACKS, and a map is not one; and a
  * DEPENDENT port cannot ship a support type at all, because it inherits the base's
  * runtime-requiring phases and vendoring would define every base shim twice. The one thing the
  * extraction would have drained here is the `concurrent` map below — one `portability(injected)`
  * finding of the four this file files, the other three being the reflective fallback the port keeps
  * on purpose.
  *
  * ==Faithfulness of the fallback, which is subtler than it looks==
  * libGDX's `ClassReflection.newInstance` (`ClassReflection.java:91-99`) calls the deprecated
  * `c.newInstance()` and wraps exactly two exceptions — `InstantiationException` and
  * `IllegalAccessException` — into `ReflectionException`, which Ashley's `Engine.java:70-72` turns
  * into `null`.
  *
  * `getConstructor().newInstance()` is not the same call:
  *   - a missing no-arg constructor raises `NoSuchMethodException` where the deprecated form raises
  *     `InstantiationException`, so that third exception must be caught to keep the behaviour;
  *   - a constructor that THROWS arrives wrapped in `InvocationTargetException`, where the
  *     deprecated form propagates it unwrapped. Swallowing that would silently turn "your
  *     component's constructor threw" into "returned null" — a defect that compiles, passes review
  *     and behaves differently (CLAUDE.md §4.4). It is rethrown unwrapped.
  */
object ComponentFactories:

  private val factories = new java.util.concurrent.ConcurrentHashMap[Class[?], () => Component]()

  /** Register the cross-platform way to build a component. Required on Scala.js and Scala Native,
    * where the reflective fallback below cannot exist. */
  def register[T <: Component](componentType: Class[T], factory: () => T): Unit =
    factories.put(componentType, factory.asInstanceOf[() => Component])

  /** A registered factory if there is one, else the JVM reflective path, else `null` — exactly the
    * three outcomes `Engine.createComponent` already had. */
  def create[T <: Component](componentType: Class[T]): T =
    val f = factories.get(componentType)
    if f != null then f().asInstanceOf[T]
    else reflectively(componentType)

  private def reflectively[T <: Component](componentType: Class[T]): T =
    try componentType.getConstructor().newInstance()
    catch
      case _: NoSuchMethodException | _: InstantiationException | _: IllegalAccessException =>
        null.asInstanceOf[T]
      case e: java.lang.reflect.InvocationTargetException => throw e.getCause
