/*
 * Ported from Ashley ECS - https://github.com/libgdx/ashley
 * Original source: com/badlogic/ashley/utils/ImmutableArray.java
 * Original authors: David Saltares
 * Licensed under the Apache License, Version 2.0
 *
 * Injected replacement: the mechanical port cannot handle ImmutableArray because it wraps
 * `Array<T>` (retargetted to `DynamicArray`), and three of its methods delegate with a non-literal
 * boolean identity flag that BoolDispatch cannot dispatch statically. The `iterable` field
 * references `Array.ArrayIterable`, a nested type of the retargetted `Array` that no longer exists.
 * The reference port (sge) hand-writes the whole class; this is the same file, adapted to use
 * DynamicArray directly.
 *
 * Scala port copyright 2025-2026 Mateusz Kubuszok
 */
package sge.ecs.utils

import lowlevel.util.DynamicArray
import lowlevel.Nullable

/** Read-only wrapper around a mutable [[DynamicArray]]. This is a live view -- changes to the
  * backing array are visible through this wrapper.
  *
  * @author David Saltares (original implementation)
  */
final class ImmutableArray[A](private val array: DynamicArray[A]) extends balticporter.runtime.JavaIterable[A] {

  // The empty backing array is built in the COMPANION: a local `given` inside a constructor
  // argument is lifted to a member by the Scala.js back end and refused as a self reference
  // (`super constructor cannot be passed a self reference this.given_MkArray_A`), while the JVM
  // compile accepts it — measured at the 3.1ac merge, ashley js 0 -> 1. `DynamicArray.apply` is
  // `inline` and summons its `MkArray` at the call site, so the given must be in lexical scope.
  def this() = this(ImmutableArray.emptyBacking[A])

  def size: Int = array.size

  def apply(index: Int): A = array(index)

  /** Alias for [[apply]] — the mechanically ported code calls `get(i)` because that is what the
    * java source declares. */
  def get(index: Int): A = array(index)

  def contains(value: A, identity: Boolean): Boolean =
    if (identity) array.containsByRef(value) else array.contains(value)

  def indexOf(value: A, identity: Boolean): Int =
    if (identity) array.indexOfByRef(value) else array.indexOf(value)

  def lastIndexOf(value: A, identity: Boolean): Int =
    if (identity) array.lastIndexOfByRef(value) else array.lastIndexOf(value)

  def random(): Nullable[A] =
    if (array.isEmpty) Nullable.empty[A]
    else Nullable(array(lowlevel.math.MathUtils.random(array.size - 1)))

  def peek: A = array.peek

  def first: A = array.first

  def toArray: Array[Any] = array.toArray.asInstanceOf[Array[Any]]

  override def hashCode(): Int = array.hashCode()

  override def equals(obj: Any): Boolean = obj match {
    case other: ImmutableArray[?] => array == other.array
    case _ => false
  }

  /** Java's `iterator()` has parens; the mechanically ported test calls it that way.
    * `JavaIterable.iterator()` has parens too (java's own arity), so the override matches.
    * `remove()` throws `GdxRuntimeException("Remove not allowed.")` to match java's
    * `ArrayIterable`/`ArrayIterator` behaviour (the original `ImmutableArray` creates
    * `ArrayIterable(array, false)` where `false` = allowRemove). sge's own ImmutableArray
    * uses `array.iterator` (scala Iterator, no `remove()` at all). */
  override def iterator(): balticporter.runtime.JavaIterator[A] = {
    val it = array.iterator
    new balticporter.runtime.JavaIterator[A] {
      def hasNext(): Boolean = it.hasNext
      def next(): A = it.next()
      override def remove(): Unit = throw new sge.utils.GdxRuntimeException("Remove not allowed.")
    }
  }

  override def toString(): String = array.toString()

  def toString(separator: String): String = array.iterator.mkString(separator)
}

object ImmutableArray {
  private[utils] def emptyBacking[A]: DynamicArray[A] = {
    given lowlevel.MkArray[A] = lowlevel.MkArray.anyRef.asInstanceOf[lowlevel.MkArray[A]]
    DynamicArray[A]()
  }
}
