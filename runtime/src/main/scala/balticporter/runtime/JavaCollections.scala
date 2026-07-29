package balticporter.runtime

/** `java.util.Collections`' statics, and the `java.util.Map.Entry` statics that follow from mapping
  * `Map.Entry` to `Tuple2` — as Scala, with java's own semantics.
  *
  * ==Why these need a home at all==
  * A static utility has no receiver, so nothing in `CollectionsTransform` that keys on the
  * receiver's collection kind can see it, and the call reaches the compiler VERBATIM against the
  * real JDK class — where the argument has been retyped and no longer fits. `Collections.sort` is
  * the shape: `Required: java.util.List[T]` against a `Buffer` the port itself produced.
  *
  * ==Why an object and not more methods on [[JavaCollection]]==
  * Because these are not about `JavaCollection`. Their arguments are ordinary scala collections;
  * they exist because a JDK *utility class* has no counterpart, not because a JDK *collection type*
  * has the wrong shape. Naming them for what they mirror is what makes the next one obvious to add.
  *
  * ==What is deliberately NOT here==
  * `unmodifiableList`, `unmodifiableSet`, `unmodifiableMap`. Java returns a read-only VIEW of a
  * mutable collection, and scala has no such view of a `Buffer`/`Set`/`Map` — so the honest options
  * are a copy (which silently detaches the view) or the identity (which silently drops the
  * immutability). Both compile and both are wrong, so neither is offered and the call fails to
  * translate. `unmodifiableCollection` IS mapped, on [[JavaCollection.unmodifiable]], because the
  * shim can express exactly what java returns.
  */
object JavaCollections:

  /** `java.util.Collections.sort(list, cmp)` — IN PLACE, as java's is.
    *
    * `sortInPlace` and not `sorted`: java mutates the argument and returns nothing, and every caller
    * reads the list afterwards through the same reference. A sorted COPY would compile, return the
    * right answer to nobody, and leave the original in its original order — a CLAUDE.md §4.4 defect
    * with no compile error and no changed count. */
  def sort[A](xs: scala.collection.mutable.Buffer[A], cmp: java.util.Comparator[? >: A]): Unit =
    inPlace(xs, xs.toList.sortWith((a, b) => cmp.compare(a, b) < 0))

  /** `java.util.Collections.sort(list)` — the natural-ordering overload, which java resolves through
    * `Comparable`. Scala needs an `Ordering`, and there is none for an arbitrary `A`; taking the
    * `Comparable` bound explicitly is what java's own signature says. */
  def sortNatural[A <: Comparable[A]](xs: scala.collection.mutable.Buffer[A]): Unit =
    inPlace(xs, xs.toList.sortWith((a, b) => a.compareTo(b) < 0))

  /** `java.util.Collections.reverse(list)` — in place, as java's is. */
  def reverse[A](xs: scala.collection.mutable.Buffer[A]): Unit = inPlace(xs, xs.toList.reverse)

  /** Replace a buffer's contents, keeping the IDENTITY the caller holds.
    *
    * `sortInPlaceWith` would be the obvious call and is not available: it lives on
    * `IndexedSeqOps`, and the port's `List`/`Collection` slots are plain `Buffer` — which a
    * `ListBuffer` also satisfies. Sorting a snapshot and writing it back works for every `Buffer`
    * and preserves the one property that matters, that the reference the caller kept sees the
    * change. `sortWith` on a `List` is a stable merge sort, as `java.util.Collections.sort` is. */
  private def inPlace[A](xs: scala.collection.mutable.Buffer[A], replacement: scala.collection.Seq[A]): Unit =
    xs.clear()
    xs ++= replacement

  /** `java.util.Map.Entry.comparingByKey(cmp)` over the `Tuple2` a `Map.Entry` becomes.
    *
    * The engine maps `Map.Entry` to `Tuple2` (a key/value pair has no identity of its own), which
    * means `Entry`'s own statics have to come along: `Tuple2` has no `comparingByKey`, and the call
    * survives to the compiler naming `java.util.Map.Entry` — a type the port no longer produces. */
  def comparingByKey[K, V](cmp: java.util.Comparator[? >: K]): java.util.Comparator[(K, V)] =
    (a: (K, V), b: (K, V)) => cmp.compare(a._1, b._1)

  def comparingByValue[K, V](cmp: java.util.Comparator[? >: V]): java.util.Comparator[(K, V)] =
    (a: (K, V), b: (K, V)) => cmp.compare(a._2, b._2)

  /** `Stream.sorted(Comparator)` on a chain this engine has already collapsed to a `Buffer`.
    *
    * A COPY here, unlike [[sort]], and for the same reason [[sort]] is in place: java's stream
    * operation is non-mutating, and the collection it reads is often one the caller still holds.
    * The two differ because the java methods differ, not because one shape was convenient.
    *
    * Note the name. Scala's `Buffer.sorted` takes an IMPLICIT `Ordering`, so an unmapped
    * `Stream.sorted(cmp)` binds to it and fails with `Required: Ordering[…]` — an error that names
    * neither streams nor comparators. Had the element types lined up it would have been worse: a
    * silently different sort order. */
  def sortedWith[A](xs: scala.collection.mutable.Buffer[A], cmp: java.util.Comparator[? >: A])
      : scala.collection.mutable.Buffer[A] =
    xs.sortWith((a, b) => cmp.compare(a, b) < 0)

  /** `Collectors.toCollection(Factory::new)` — build the factory's collection and fill it.
    *
    * `Growable` is the exact bound: it is what "a collection you can add to" is in scala, and it is
    * what every `Collectors.toCollection` target satisfies. */
  def into[A, C <: scala.collection.mutable.Growable[A]](
      xs: scala.collection.mutable.Buffer[A], factory: () => C): C =
    val c = factory()
    c ++= xs
    c
