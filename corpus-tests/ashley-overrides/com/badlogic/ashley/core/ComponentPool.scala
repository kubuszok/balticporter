package com.badlogic.ashley.core

/** A component pool that builds instances from a FACTORY instead of reflectively.
  *
  * `PooledEngine.ComponentPools` keys a pool per component `Class` and fills it with
  * `new ReflectionPool(type, initialSize, maxSize)`. libGDX drops `ReflectionPool` — reflective
  * instantiation is the one thing Scala.js and Scala Native do not have, and every remaining libGDX
  * use of it went with the drop. Ashley's use did not, and a dependent cannot inject at the base's
  * FQN (exactly one module may ship each replacement, or the two definitions collide on the JS and
  * Native linkers). So the references are re-pointed here by `TypeRedirectTransform`.
  *
  * SHAPE-COMPATIBLE with what the emitted code calls, which is the whole contract: a
  * `(Class[T], Int, Int)` constructor, `obtain()`, `free(T)`, `clear()`. Instances come from
  * [[ComponentFactories]] — a registered factory when there is one, the JVM reflective path when
  * there is not — so this is exactly as portable as `Engine.createComponent` and no less.
  *
  * The `maxSize` bound is upstream's and is kept: a pool that grew without limit would turn a
  * pooling optimisation into a leak, which is the sort of divergence that never shows up in a
  * compile and shows up much later in a profile.
  *
  * The parameter is UNBOUNDED, matching `ReflectionPool<T>` and `ComponentPools.obtain(Class<T>)`
  * — both of which are plain `<T>` in Java. Tightening it to `T <: Component`, which is what every
  * runtime use actually is, does not type-check at the call site: the emitted `obtain[T <:
  * java.lang.Object]` passes its own unbounded `T` straight through. A replacement type must have
  * the shape of the one it replaces, not the shape its uses happen to have.
  */
final class ComponentPool[T](componentType: Class[T], initialSize: Int, maxSize: Int):

  private val free = new java.util.ArrayDeque[T](math.max(initialSize, 1))

  /** A pooled instance if one is free, else a new one. Never null for a registered type. */
  def obtain(): T =
    val pooled = free.pollLast()
    if pooled != null then pooled
    else
      // the cast is where the unbounded parameter is paid for: every runtime use IS a Component
      // (this pool is only ever built from `PooledEngine.ComponentPools`), but the static bound
      // cannot say so without breaking the call site.
      ComponentFactories.create(componentType.asInstanceOf[Class[? <: Component]]).asInstanceOf[T]

  /** Return an instance to the pool, resetting it first when it is `Poolable` — upstream's
    * contract, and the reason a pooled component does not carry state across uses. */
  def free(obj: T): Unit =
    if obj != null then
      obj match
        case p: com.badlogic.gdx.utils.Pool.Poolable => p.reset()
        case _                                       => ()
      if free.size < maxSize then free.addLast(obj)

  def clear(): Unit = free.clear()

  /** how many instances are currently pooled — upstream's `Pool.getFree`. */
  def getFree: Int = free.size
