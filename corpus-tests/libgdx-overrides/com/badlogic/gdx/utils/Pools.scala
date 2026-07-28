package com.badlogic.gdx.utils

/** INJECTED SCALA (Substitutions.inject) — the portable replacement for libGDX's `Pools`.
  *
  * The Java original kept a `Class` -> `Pool` map and, on a miss, fabricated a pool with
  * `ReflectionPool`, which resolved the type's no-arg constructor reflectively and invoked it. That
  * is the one thing Scala.js and Scala Native genuinely cannot do: a `Class` is a value on those
  * platforms (identity, `getClass`, `isInstance` all work) but nothing can be INSTANTIATED from it.
  *
  * So the construction side moves to the caller, as a factory function, and `ReflectionPool` is
  * dropped outright — upstream libGDX already deprecated it in favour of `DefaultPool`, which is
  * exactly a factory-backed pool (`PoolSupplier[T]`). The `Class` stays where it is harmless: as the
  * map KEY, which is what `free`/`freeAll` need to find an object's pool.
  *
  * The resulting split is total, and enforced by the signatures:
  *   - CREATE  — needs a factory: `set(factory)`, `get(type, factory, max)`, `obtain(type, factory)`
  *   - LOOK UP — needs only the `Class`: `get(type)`, `getOrNull(type)`, `obtain(type)`, `free(obj)`
  *
  * `get(type)` therefore no longer creates on a miss; it throws, naming the registration call. The
  * old `get(type, max)` overload is REMOVED rather than silently redefined, so any caller that
  * relied on creation-on-demand fails to compile instead of failing at runtime. The
  * `WARN_ON_REFLECTION_POOL_CREATION` / `THROW_ON_REFLECTION_POOL_CREATION` switches go with it —
  * they existed only to police the reflective path that no longer exists.
  */
object Pools:

  private final val typePools: ObjectMap[java.lang.Class[?], Pool[?]] =
    new ObjectMap[java.lang.Class[?], Pool[?]]()

  // The upstream `static { … }` block, ported by hand: every type libGDX itself pools is
  // pre-registered with its constructor as the factory. This is what makes the `Class`-keyed
  // lookups above resolve WITHOUT ever needing to construct from a `Class` — the reflective
  // fallback existed only because these registrations were not exhaustive for user types.
  Pools.set(() => new Array[java.lang.Object]())
  Pools.set(() => new com.badlogic.gdx.scenes.scene2d.utils.ChangeListener.ChangeEvent())
  Pools.set(() => new com.badlogic.gdx.scenes.scene2d.ui.Table.DebugRect())
  Pools.set(() => new com.badlogic.gdx.scenes.scene2d.utils.FocusListener.FocusEvent())
  Pools.set(() => new com.badlogic.gdx.graphics.g2d.GlyphLayout.GlyphRun())
  Pools.set(() => new com.badlogic.gdx.graphics.g2d.GlyphLayout())
  Pools.set(() => new com.badlogic.gdx.Net.HttpRequest())
  Pools.set(() => new com.badlogic.gdx.scenes.scene2d.InputEvent())
  Pools.set(() => new com.badlogic.gdx.math.Rectangle())
  Pools.set(() => new com.badlogic.gdx.scenes.scene2d.Stage.TouchFocus())
  // Actions
  Pools.set(() => new com.badlogic.gdx.scenes.scene2d.actions.AddAction())
  Pools.set(() => new com.badlogic.gdx.scenes.scene2d.actions.AddListenerAction())
  Pools.set(() => new com.badlogic.gdx.scenes.scene2d.actions.AfterAction())
  Pools.set(() => new com.badlogic.gdx.scenes.scene2d.actions.AlphaAction())
  Pools.set(() => new com.badlogic.gdx.scenes.scene2d.actions.ColorAction())
  Pools.set(() => new com.badlogic.gdx.scenes.scene2d.actions.DelayAction())
  Pools.set(() => new com.badlogic.gdx.scenes.scene2d.actions.FloatAction())
  Pools.set(() => new com.badlogic.gdx.scenes.scene2d.actions.IntAction())
  Pools.set(() => new com.badlogic.gdx.scenes.scene2d.actions.LayoutAction())
  Pools.set(() => new com.badlogic.gdx.scenes.scene2d.actions.MoveByAction())
  Pools.set(() => new com.badlogic.gdx.scenes.scene2d.actions.MoveToAction())
  Pools.set(() => new com.badlogic.gdx.scenes.scene2d.actions.ParallelAction())
  Pools.set(() => new com.badlogic.gdx.scenes.scene2d.actions.RemoveAction())
  Pools.set(() => new com.badlogic.gdx.scenes.scene2d.actions.RemoveActorAction())
  Pools.set(() => new com.badlogic.gdx.scenes.scene2d.actions.RemoveListenerAction())
  Pools.set(() => new com.badlogic.gdx.scenes.scene2d.actions.RepeatAction())
  Pools.set(() => new com.badlogic.gdx.scenes.scene2d.actions.RotateByAction())
  Pools.set(() => new com.badlogic.gdx.scenes.scene2d.actions.RotateToAction())
  Pools.set(() => new com.badlogic.gdx.scenes.scene2d.actions.RunnableAction())
  Pools.set(() => new com.badlogic.gdx.scenes.scene2d.actions.ScaleByAction())
  Pools.set(() => new com.badlogic.gdx.scenes.scene2d.actions.ScaleToAction())
  Pools.set(() => new com.badlogic.gdx.scenes.scene2d.actions.SequenceAction())
  Pools.set(() => new com.badlogic.gdx.scenes.scene2d.actions.SizeByAction())
  Pools.set(() => new com.badlogic.gdx.scenes.scene2d.actions.SizeToAction())
  Pools.set(() => new com.badlogic.gdx.scenes.scene2d.actions.TimeScaleAction())
  Pools.set(() => new com.badlogic.gdx.scenes.scene2d.actions.TouchableAction())
  Pools.set(() => new com.badlogic.gdx.scenes.scene2d.actions.VisibleAction())

  /** Registers an existing pool for the specified type. */
  def set[T <: java.lang.Object](`type`: java.lang.Class[T], pool: Pool[T]): Unit =
    Pools.typePools.put(`type`, pool)

  /** Registers a factory-backed pool, keyed by the class of an instance the factory produces.
    * `Pools.set(() => new MyClass, 50)`. */
  def set[T <: java.lang.Object](factory: () => T, max: Int): Unit =
    val probe = factory()
    Pools.set(
      probe.asInstanceOf[java.lang.Object].getClass().asInstanceOf[java.lang.Class[T]],
      new DefaultPool[T](() => factory(), 4, max),
    )

  /** Registers a factory-backed pool with the default maximum of 100. */
  def set[T <: java.lang.Object](factory: () => T): Unit = Pools.set(factory, 100)

  /** The registered pool for `type`, or `null` if there is none. */
  def getOrNull[T <: java.lang.Object](`type`: java.lang.Class[T]): Pool[T] =
    Pools.typePools.get(`type`).asInstanceOf[Pool[T]]

  /** The registered pool for `type`. Unlike the Java original this does NOT create one on a miss —
    * a pool cannot construct `T` from a `Class` without reflection. Use the `factory` overload (or
    * `set`) to register one. */
  def get[T <: java.lang.Object](`type`: java.lang.Class[T]): Pool[T] =
    val pool = Pools.getOrNull(`type`)
    if pool == null then
      throw new GdxRuntimeException(
        "No Pool registered for " + `type` + " — register one with Pools.set(" +
          `type`.getSimpleName() + "::new) or use Pools.get(type, factory)"
      )
    pool

  /** The registered pool for `type`, creating a factory-backed one on a miss. This is the direct
    * replacement for the Java `get(type, max)` reflective fallback: same shape, but the caller
    * supplies the construction. */
  def get[T <: java.lang.Object](`type`: java.lang.Class[T], factory: () => T, max: Int): Pool[T] =
    val existing = Pools.getOrNull(`type`)
    if existing != null then existing
    else
      val pool = new DefaultPool[T](() => factory(), 4, max)
      Pools.typePools.put(`type`, pool)
      pool

  /** As `get(type, factory, max)` with the default maximum of 100. */
  def get[T <: java.lang.Object](`type`: java.lang.Class[T], factory: () => T): Pool[T] = Pools.get(`type`, factory, 100)

  /** Obtains an object from the registered pool for `type`. */
  def obtain[T <: java.lang.Object](`type`: java.lang.Class[T]): T = Pools.get(`type`).obtain()

  /** Obtains an object from the pool for `type`, registering a factory-backed pool on a miss. */
  def obtain[T <: java.lang.Object](`type`: java.lang.Class[T], factory: () => T): T = Pools.get(`type`, factory).obtain()

  /** Frees an object to the pool registered for its runtime class. `getClass` is supported on every
    * target — only INSTANTIATION from a `Class` is not — so the lookup side is unchanged. */
  def free(`object`: java.lang.Object): Unit =
    if `object` == null then throw new java.lang.IllegalArgumentException("object cannot be null.")
    val pool = Pools.typePools.get(`object`.getClass())
    if pool == null then () // Ignore freeing an object that was never retained.
    else pool.asInstanceOf[Pool[java.lang.Object]].free(`object`)

  /** Frees the specified objects. Null objects within the array are silently ignored. */
  def freeAll(objects: Array[?]): Unit = Pools.freeAll(objects, false)

  /** Frees the specified objects.
    * @param samePool if true the pool is looked up once and reused for every object. */
  def freeAll(objects: Array[?], samePool: Boolean): Unit =
    if objects == null then throw new java.lang.IllegalArgumentException("objects cannot be null.")
    var pool: Pool[?] = null
    var i             = 0
    val n             = objects.size
    while i < n do
      val obj = objects.get(i).asInstanceOf[java.lang.Object]
      if obj != null then
        if pool == null then pool = Pools.typePools.get(obj.getClass())
        if pool != null then
          pool.asInstanceOf[Pool[java.lang.Object]].free(obj)
          if !samePool then pool = null
      i += 1
