/*
 * Injected replacement for com.badlogic.gdx.utils.Pool.
 *
 * Source: sge/sge/src/main/scala/sge/utils/Pool.scala (the hand port's trait form)
 * Original upstream: com/badlogic/gdx/utils/Pool.java
 * Original license: Apache-2.0 (see libGDX upstream)
 *
 * sge hand-ported Pool as a TRAIT with abstract vals `initialCapacity` and `max`
 * (Pool.scala:12 "Issues: Pool changed from abstract class to trait -- intentional
 * design improvement"; divergence-investigator verdict: justified, kind=api; AD-003).
 * The ClassToTraitTransform phase rewrites every subclass to override those vals.
 *
 * This file reproduces sge's Pool trait and Pool.Poolable companion. sge's Pool.Default,
 * Pool.Flushable and Pool.QuadTreeFloat are SGE-ORIGINALS (merged from separate Java
 * files) and are NOT included -- the mechanical port emits DefaultPool and FlushablePool
 * from their own Java sources.
 *
 * Thread safety: sge's Pool is internally synchronized (ISS-603, ISS-797). The mechanical
 * port reproduces this because the injected file replaces the emitted class wholesale.
 *
 * Scala port copyright 2025-2026 Mateusz Kubuszok
 */
package sge.utils

import lowlevel.util.DynamicArray
import sge.utils.createRef

/** A pool of objects that can be reused to avoid allocation.
  * @see PoolManager
  * @author Nathan Sweet */
trait Pool[A] {

  /** The maximum number of objects that will be pooled. */
  protected val max: Int

  protected val initialCapacity: Int

  var peak: Int = 0

  private val freeObjects = DynamicArray.createRef[A](initialCapacity)

  protected val lock = new AnyRef

  protected def newObject(): A

  /** Returns an object from this pool. The object may be new (from [[newObject]]) or reused (previously [[free]]). */
  def obtain(): A = {
    lock.synchronized {
      if (freeObjects.isEmpty) newObject() else freeObjects.pop()
    }
  }

  /** Puts the specified object in the pool, making it eligible to be returned by [[obtain]]. If the pool already contains
    * [[max]] free objects, the specified object is discarded. */
  def free(obj: A): Unit = {
    lock.synchronized {
      if (freeObjects.size < max) {
        freeObjects.add(obj)
        peak = peak max freeObjects.size
        reset(obj)
      } else
        discard(obj)
    }
  }

  /** Adds the specified number of new free objects to the pool. */
  def fill(size: Int): Unit = {
    lock.synchronized {
      for (_ <- 0 until size)
        if (freeObjects.size < max) freeObjects.add(newObject())
      peak = peak max freeObjects.size
    }
  }

  /** Called when an object is freed to clear the state of the object for possible later reuse. */
  protected def reset(obj: A): Unit = obj match {
    case obj: Pool.Poolable => obj.reset()
    case _ => ()
  }

  /** Called when an object is discarded (pool full or cleared). */
  protected def discard(obj: A): Unit = {
    reset(obj)
  }

  def freeAll(objects: DynamicArray[? <: A]): Unit = {
    lock.synchronized {
      objects.foreach { obj =>
        if (obj.asInstanceOf[AnyRef] ne null) {
          val o = obj.asInstanceOf[A]
          if (freeObjects.size < max) {
            freeObjects.add(o)
            reset(o)
          } else {
            discard(o)
          }
        }
      }
      peak = peak max freeObjects.size
    }
  }

  /** Removes and discards all free objects from this pool. */
  def clear(): Unit = {
    lock.synchronized {
      freeObjects.foreach(discard)
      freeObjects.clear()
    }
  }

  /** The number of objects available to be obtained. */
  def getFree(): Int = {
    lock.synchronized {
      freeObjects.size
    }
  }
}

object Pool {
  /** Objects implementing this interface will have [[Pool#reset]] called when passed to [[Pool#free]]. */
  trait Poolable {
    /** Resets the object for reuse. Object references should be nulled and fields may be set to default values. */
    def reset(): Unit
  }
}
