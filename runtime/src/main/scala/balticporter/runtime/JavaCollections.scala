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

  /** `java.util.Collections.shuffle(list, rnd)` — JAVA'S ALGORITHM, not an equivalent one.
    *
    * The loop below is `Collections.shuffle`'s, verbatim: walk down from the end, swapping each
    * position with `rnd.nextInt(i)`. Any other correct shuffle consumes the `Random` differently and
    * therefore produces a DIFFERENT permutation from the same seed — and a seeded shuffle is only
    * ever written because the caller wants a reproducible one. simple-graphs' `GraphTest` seeds
    * `new Random(123)` and asserts on what follows; a "correct" shuffle that permutes differently
    * would turn a deterministic test into a coin flip, with nothing in the compile to show for it. */
  def shuffle[A](xs: scala.collection.mutable.Buffer[A], rnd: java.util.Random): Unit =
    var i = xs.size
    while i > 1 do
      val j = rnd.nextInt(i)
      val t = xs(i - 1); xs(i - 1) = xs(j); xs(j) = t
      i -= 1

  /** `java.util.Arrays.asList(a, b, c)`.
    *
    * ONE divergence, stated rather than hidden: java returns a FIXED-SIZE list — `set` works, `add`
    * and `remove` throw — and this returns an ordinary `Buffer`. There is no scala type that is both
    * a `Buffer` (which is what `java.util.List` maps to, so it is what the declared slot demands) and
    * fixed-size. The divergence is permissive: code java would have rejected now runs. That direction
    * cannot turn a correct program into an incorrect one, which is the same trade already recorded
    * for `Buffer` adding an ordering guarantee `Collection` does not make. */
  def asList[A](xs: A*): scala.collection.mutable.Buffer[A] =
    scala.collection.mutable.ArrayBuffer.from(xs)

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

  /** `Stream.mapToDouble(f)` on a chain already collapsed to a `Buffer`.
    *
    * The WIDENING is the point, and it is why this is a named function rather than a bare `.map`.
    * Java's `ToDoubleFunction` widens whatever the lambda returns to `double`; simple-graphs sums
    * `Edge.getWeight()`, which is a `float`. Left as `.map(f).sum` scala would sum in FLOAT — the
    * same answer to within a few ulps, which is exactly the kind of difference that passes a
    * tolerance-based assertion until the collection is large enough and then does not. Declaring the
    * function `A => Double` makes scala insert the same widening java did, at the same place. */
  def mapToDouble[A](xs: scala.collection.mutable.Buffer[A], f: A => Double): scala.collection.mutable.Buffer[Double] =
    xs.map(f)

  /** `IntStream.range(a, b)` — a stream SOURCE that is not a collection, so nothing else in the
    * chain-collapse can produce it. Half-open, as java's is. */
  def intRange(startInclusive: Int, endExclusive: Int): scala.collection.mutable.Buffer[Int] =
    scala.collection.mutable.ArrayBuffer.from(startInclusive until endExclusive)

  /** `Collectors.toCollection(Factory::new)` — build the factory's collection and fill it.
    *
    * `Growable` is the exact bound: it is what "a collection you can add to" is in scala, and it is
    * what every `Collectors.toCollection` target satisfies. */
  def into[A, C <: scala.collection.mutable.Growable[A]](
      xs: scala.collection.mutable.Buffer[A], factory: () => C): C =
    val c = factory()
    c ++= xs
    c
