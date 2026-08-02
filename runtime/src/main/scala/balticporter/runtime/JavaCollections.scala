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

  /** `java.util.Arrays.asList(a, b, c)` — the ELEMENT form only. Java's `asList` has two shapes
    * with different semantics, and only one of them may ever reach this method:
    *
    *   - **Elements listed at the call site** (`asList(1, 2, 3)`): java packs them into a fresh
    *     array nobody else can reach, so the only observable divergence here is that java's list
    *     is FIXED-SIZE — `set` works, `add`/`remove` throw — and this returns an ordinary
    *     `Buffer`. There is no scala type that is both a `Buffer` (which is what `java.util.List`
    *     maps to, so it is what the declared slot demands) and fixed-size. That divergence is
    *     permissive: code java would have rejected now runs, and that direction cannot turn a
    *     correct program into an incorrect one — the same trade already recorded for `Buffer`
    *     adding an ordering guarantee `Collection` does not make.
    *   - **A caller-held array passed whole** (`asList(arr)`): java returns a LIVE VIEW — `set`
    *     writes through to `arr`, and writes to `arr` are visible through the list. A copy here
    *     would compile and silently detach every aliased write, which is exactly a §4.4 defect.
    *     This method MUST NOT receive that form, and now cannot: `CollectionsTransform.asListArgs`
    *     REFUSES the rewrite for a single array-typed argument, so the emitted text keeps the JDK
    *     name and fails to compile as `Found: java.util.List[Array[Object]] / Required:
    *     Buffer[String]` — an untranslated call rather than a broken helper.
    *
    * Note this is the one rewritten static declared with a SCALA vararg. The engine renders a java
    * `T...` parameter as `Array[T]` and materialises the pack at the call site, so `asListArgs`
    * opens that pack back into separate arguments before it reaches here; a change to this
    * signature has to change that with it. */
  def asList[A](xs: A*): scala.collection.mutable.Buffer[A] =
    scala.collection.mutable.ArrayBuffer.from(xs)

  /** `java.util.Collection.remove(Object)` — removal BY VALUE, which scala's `Buffer` does not have.
    *
    * Not `Collections`', and deliberately here anyway: like every other member of this object it
    * exists because a java call has no scala counterpart with the same MEANING, and it is a
    * receiver-first function for the same reason `sort` is — `Buffer` cannot be extended with a
    * member that would clash with the `remove(Int)` it already declares.
    *
    * Two things are java's and not scala's, and both are the reason this is not `xs -= o`:
    *
    *   - **the RESULT.** Java returns whether anything was removed, and code branches on it
    *     (`if (list.remove(x)) …`). `-=` returns the buffer.
    *   - **the explicit NULL arm.** Java's `ArrayList.remove(Object o)` has one, and it is what
    *     makes `remove(null)` remove a null element rather than throw.
    *
    * The DIRECTION of the equality is java's too — `o.equals(element)`, the PROBE's `equals`, not
    * the element's — and it must be preserved, but it is NOT one of the reasons this is a helper.
    * That was claimed here and is wrong: `SeqOps.indexOf(elem)` is `indexWhere(elem == _)`, so
    * scala's own `indexOf` (and `-=` through it) already asks the probe. MEASURED in
    * `JavaCollectionsSpec`, which pins both the direction and that fact — because a hand-written
    * `indexWhere(_ == o)` WOULD diverge, silently, for any asymmetric `equals` (a subclass that
    * narrows it, `java.sql.Timestamp` against `java.util.Date`).
    *
    * Only the FIRST match goes, as java's does. */
  def removeValue[A](xs: scala.collection.mutable.Buffer[A], o: scala.Any): Boolean =
    val i = xs.indexWhere(e => if o == null then e == null else o.equals(e))
    if i < 0 then false else { xs.remove(i); true }

  /** `java.util.Collection.toArray()` — a FRESH `Object[]` of exactly `size`, in iteration order.
    *
    * Not `xs.toArray`, and the difference is not stylistic: scala's `toArray` takes a `ClassTag[B]`
    * and therefore allocates an array of the ELEMENT's runtime class, so a `Buffer[String]` would
    * hand back a `String[]` where java hands back an `Object[]`. That is invisible to a compile —
    * an `Array[String]` conforms nowhere an `Array[Object]` is wanted in scala (arrays are
    * invariant), so it fails at the SLOT and reads as a missing mapping; and where the result flows
    * into an `Object` it type-checks and diverges only at `arr[0] = someNonString`, which java
    * permits and an `Object[]`-declared-`String[]` rejects with `ArrayStoreException`. Allocating
    * `Object[]` is java's own contract, stated on `Collection.toArray()`. */
  def toArray(xs: scala.collection.Iterable[?]): Array[Object] =
    val out = new Array[Object](xs.size)
    var i   = 0
    val it  = xs.iterator
    while it.hasNext do
      out(i) = it.next().asInstanceOf[Object]
      i += 1
    out

  /** `java.util.Collection.toArray(T[] a)` — java's THREE-part contract, reproduced exactly.
    *
    * Java does not simply allocate: it fills the caller's array when the elements fit, and the
    * caller may be relying on that (`list.toArray(shared)` and then reading `shared`). The contract,
    * from `Collection.toArray(T[])` and implemented by `AbstractCollection`:
    *
    *   1. **`a.length >= size`** — the elements are written INTO `a`, which is also what is
    *      returned. The array the caller passed is the array the caller gets back.
    *   2. **`a.length < size`** — a NEW array is allocated, with `a`'s RUNTIME component type
    *      (`java.util.Arrays.copyOf`, which is what preserves it — a fresh `Array[A]` here would
    *      have the erased component type and the result would throw `ArrayStoreException` on the
    *      first element the caller stored into it through a `T[]`-typed reference).
    *   3. **the NULL TERMINATOR** — if the returned array is LONGER than the collection, index
    *      `size` is set to `null`, so a caller that walks until it sees a `null` stops in the right
    *      place. Java sets exactly that one element and leaves the rest of the tail alone; so does
    *      this.
    *
    * Every one of the three is a CLAUDE.md §4.4 shape — a naive `xs.toArray` compiles, returns the
    * right elements, and silently breaks all three: it never fills `a`, so an aliasing caller reads
    * a stale array; it allocates on the element's class rather than `a`'s; and it is exactly `size`
    * long, so the terminator a caller looks for is never written. None of that moves a compile-error
    * count, which is why the contract is spelled out here and pinned in `JavaCollectionsSpec`.
    *
    * `java.util.Arrays.copyOf` rather than `java.lang.reflect.Array.newInstance`: the two do the
    * same thing here, and only the first survives `PortabilityCheck` (reflection does not exist on
    * Scala.js or Native). */
  def toArray[A <: AnyRef](xs: scala.collection.Iterable[?], a: Array[A]): Array[A] =
    val n   = xs.size
    val out = if a.length >= n then a else java.util.Arrays.copyOf(a, n)
    var i   = 0
    val it  = xs.iterator
    while it.hasNext do
      out(i) = it.next().asInstanceOf[A]
      i += 1
    if out.length > n then out(n) = null.asInstanceOf[A]
    out

  // -------------------------------------------------------------------------------------------
  // The IMMUTABLE producers — `emptyList`, `emptyMap`, `emptySet`, `singletonList`,
  // `singletonMap`, `singleton`.
  //
  // Every one of these hands back a collection java REFUSES to modify, at a slot the port retyped
  // to a MUTABLE scala collection. `mutable.ArrayBuffer.empty` would compile and be wrong in the
  // one direction that matters: `Insertions.EMPTY = new Insertions(Collections.emptyMap())` is a
  // shared static, and a caller that put into it gets `UnsupportedOperationException` in java and,
  // with a growable buffer, silently corrupts a global here. That is CLAUDE.md §4.4 exactly — valid
  // scala meaning something else, with no compile error and no count moved.
  //
  // So the immutability is REPRODUCED rather than dropped: the three `Frozen*` classes below are
  // scala collections of the retyped shape whose every mutator throws
  // `UnsupportedOperationException`, which is java's own behaviour and not an approximation of it.
  // This is what ENGINE-LIMITS K6 said `Collections.unmodifiableList` had no target for — correctly
  // for the STDLIB, which has no read-only `Buffer` view; the runtime can supply one, and these
  // factories are the half of that family with no VIEW semantics to get wrong.
  // -------------------------------------------------------------------------------------------

  /** `java.util.Collections.emptyList()` — an immutable, empty `List`.
    *
    * SHARED, exactly as java's is (`Collections.EMPTY_LIST`, cast on the way out), because
    * reference identity is observable: java code writing `xs == Collections.emptyList()` is a
    * reference comparison, which this engine emits as `eq` (§4.4), and a fresh instance per call
    * would answer `false` where java answers `true`. The cast java performs is sound for the same
    * reason java's is — the value is empty and cannot be written to, so no element of type `A` is
    * ever read out of it or stored into it. */
  def emptyList[A](): scala.collection.mutable.Buffer[A] =
    frozenEmptyBuffer.asInstanceOf[scala.collection.mutable.Buffer[A]]

  /** `java.util.Collections.emptyMap()` — shared and immutable, for [[emptyList]]'s reasons. */
  def emptyMap[K, V](): scala.collection.mutable.Map[K, V] =
    frozenEmptyMap.asInstanceOf[scala.collection.mutable.Map[K, V]]

  /** `java.util.Collections.emptySet()` — shared and immutable, for [[emptyList]]'s reasons. */
  def emptySet[A](): scala.collection.mutable.Set[A] =
    frozenEmptySet.asInstanceOf[scala.collection.mutable.Set[A]]

  /** `java.util.Collections.singletonList(x)` — one element, immutable.
    *
    * FRESH per call, and that is java's behaviour too (`new SingletonList<>(o)`), so the identity
    * argument [[emptyList]] makes does not apply here. */
  def singletonList[A](x: A): scala.collection.mutable.Buffer[A] =
    new FrozenBuffer(scala.collection.immutable.Vector(x))

  /** `java.util.Collections.singleton(x)` — the `Set` of the same shape. */
  def singleton[A](x: A): scala.collection.mutable.Set[A] =
    new FrozenSet(scala.collection.immutable.Set(x))

  /** `java.util.Collections.singletonMap(k, v)` — one mapping, immutable. */
  def singletonMap[K, V](k: K, v: V): scala.collection.mutable.Map[K, V] =
    new FrozenMap(scala.collection.immutable.Map(k -> v))

  private val frozenEmptyBuffer = new FrozenBuffer[Any](scala.collection.immutable.Vector.empty)
  private val frozenEmptySet    = new FrozenSet[Any](scala.collection.immutable.Set.empty)
  private val frozenEmptyMap    = new FrozenMap[Any, Any](scala.collection.immutable.Map.empty)

  /** `java.util.Collections.reverse(list)` — in place, as java's is. */
  def reverse[A](xs: scala.collection.mutable.Buffer[A]): Unit = inPlace(xs, xs.toList.reverse)

  /** `java.util.Collections.swap(list, i, j)` — in place, as java's is.
    *
    * Not routed through [[inPlace]], and that is the point of writing it out: `swap` touches two
    * positions, so rebuilding the whole buffer would be a clear-and-refill where java performs two
    * `set` calls. On a buffer that is also aliased as something else — jbump's `Collisions.keySort`
    * swaps through a `List<?>` the caller still holds — an empty intermediate state is observable in
    * a way java's never is.
    *
    * Java's own `swap` is silent about equal indices and throws `IndexOutOfBoundsException`
    * otherwise; both fall out of the two `apply`/`update` calls unchanged, so nothing is
    * approximated here either. */
  def swap[A](xs: scala.collection.mutable.Buffer[A], i: scala.Int, j: scala.Int): Unit =
    val t = xs(i); xs(i) = xs(j); xs(j) = t

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

  /** A `mutable.Buffer` that REFUSES every mutation, as java's immutable lists do.
    *
    * PRIVATE, and it is the target the retyping demands rather than a type any port names: the
    * slot a `Collections.emptyList()` reaches is `scala.collection.mutable.Buffer`, because that is
    * what `java.util.List` maps to — so the value has to BE one. Everything scala derives from
    * `apply`/`length`/`iterator` (`map`, `filter`, `foreach`, `contains`, `mkString`) works and
    * builds an ordinary `ArrayBuffer` when it builds anything, which is what java's own read
    * operations do; everything that would MUTATE throws `UnsupportedOperationException`, which is
    * java's own behaviour and not an approximation of it.
    *
    * CLAUDE.md §4.5 forbids modelling a JAVA INTERFACE on a scala collection trait, and this is not
    * that: nothing in a port ever extends this, so there is no second java interface to satisfy and
    * no member of a ported class for the trait's hundreds of inherited names to collide with. The
    * rule's hazard is inheritance in the PORT, and this class is never in one. */
  private final class FrozenBuffer[A](under: scala.collection.Seq[A])
      extends scala.collection.mutable.AbstractBuffer[A]:
    def apply(i: Int): A                                   = under(i)
    def length: Int                                        = under.length
    override def iterator: scala.collection.Iterator[A]    = under.iterator
    override def knownSize: Int                            = under.length
    def update(i: Int, elem: A): Unit                      = refuse
    def insert(idx: Int, elem: A): Unit                    = refuse
    def insertAll(idx: Int, elems: scala.collection.IterableOnce[A]): Unit = refuse
    def prepend(elem: A): this.type                        = refuse
    def remove(idx: Int): A                                = refuse
    def remove(idx: Int, count: Int): Unit                 = refuse
    def addOne(elem: A): this.type                         = refuse
    def clear(): Unit                                      = refuse
    override def patchInPlace(from: Int, patch: scala.collection.IterableOnce[A], replaced: Int): this.type = refuse

  /** [[FrozenBuffer]]'s `Set`. */
  private final class FrozenSet[A](under: scala.collection.Set[A])
      extends scala.collection.mutable.AbstractSet[A]:
    def contains(elem: A): Boolean                      = under.contains(elem)
    def iterator: scala.collection.Iterator[A]          = under.iterator
    override def knownSize: Int                         = under.size
    def addOne(elem: A): this.type                      = refuse
    def subtractOne(elem: A): this.type                 = refuse
    override def clear(): Unit                          = refuse

  /** [[FrozenBuffer]]'s `Map`. */
  private final class FrozenMap[K, V](under: scala.collection.Map[K, V])
      extends scala.collection.mutable.AbstractMap[K, V]:
    def get(key: K): Option[V]                          = under.get(key)
    def iterator: scala.collection.Iterator[(K, V)]     = under.iterator
    override def knownSize: Int                         = under.size
    def addOne(kv: (K, V)): this.type                   = refuse
    def subtractOne(k: K): this.type                    = refuse
    override def clear(): Unit                          = refuse

  /** java's own answer at every one of those members — `UnsupportedOperationException`, with the
    * message naming what the value IS, since the alternative a reader will guess is an engine bug. */
  private def refuse: Nothing = throw new UnsupportedOperationException(
    "this collection came from a java factory that returns an IMMUTABLE collection " +
      "(Collections.emptyList/emptyMap/emptySet/singletonList/singletonMap/singleton); " +
      "java throws here too")
