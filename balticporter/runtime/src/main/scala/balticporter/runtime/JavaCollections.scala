package balticporter.runtime

/** `java.util.Collections`' statics, and the `java.util.Map.Entry` statics that follow from mapping
  * `Map.Entry` to `Tuple2` — as Scala, with java's own semantics. */
object JavaCollections {

  /** `java.util.Collections.sort(list, cmp)` — IN PLACE, as java's is. */
  def sort[A](xs: scala.collection.mutable.Buffer[A], cmp: java.util.Comparator[? >: A]): Unit =
    inPlace(xs, xs.toList.sortWith((a, b) => cmp.compare(a, b) < 0))

  /** `java.util.Collections.sort(list)` — the natural-ordering overload, which java resolves through
    * `Comparable`. Scala needs an `Ordering`, and there is none for an arbitrary `A`; taking the
    * `Comparable` bound explicitly is what java's own signature says. */
  def sortNatural[A <: Comparable[? >: A]](xs: scala.collection.mutable.Buffer[A]): Unit =
    inPlace(xs, xs.toList.sortWith((a, b) => a.compareTo(b) < 0))

  /** `java.util.Collections.shuffle(list, rnd)` — JAVA'S ALGORITHM, not an equivalent one. */
  def shuffle[A](xs: scala.collection.mutable.Buffer[A], rnd: java.util.Random): Unit = {
    var i = xs.size
    while i > 1 do {
      val j = rnd.nextInt(i)
      val t = xs(i - 1); xs(i - 1) = xs(j); xs(j) = t
      i -= 1
    }
  }

  /** `java.util.Arrays.asList(a, b, c)` — the ELEMENT form only. Java's `asList` has two shapes
    * with different semantics, and only one of them may ever reach this method: */
  def asList[A](xs: A*): scala.collection.mutable.Buffer[A] =
    scala.collection.mutable.ArrayBuffer.from(xs)

  /** `java.util.Arrays.asList(T[] a)` — the OTHER shape behind that syntax, and the one [[asList]]
    * above must never receive: a LIVE, FIXED-SIZE view of the caller's array. */
  def asListView[A](arr: Array[A]): scala.collection.mutable.Buffer[A] = {
    // …and it FAILS FAST, because java's does. `Arrays.asList(T[])` is `new ArrayList<>(a)`, whose
    // constructor is `a = Objects.requireNonNull(array)`, so a null array is an NPE AT THE CALL and
    // the caller never holds anything. Constructed lazily the view throws too — but at the first
    // READ, an arbitrary distance away, in whichever member happened to touch it first.
    if arr == null then
      throw new NullPointerException(
        "java.util.Arrays.asList(T[]): the array is null. Java's own constructor calls " +
          "Objects.requireNonNull, so this throws at the call rather than at the first read")
    new ArrayViewBuffer[A](arr)
  }

  /** `java.util.stream.Stream.noneMatch(Predicate)` — the one short-circuiting terminal with no
    * scala namesake. */
  def noneMatch[A](xs: scala.collection.Iterable[A], p: A => Boolean): Boolean = !xs.exists(p)

  /** `java.util.Collection.addAll(Collection<?>)` — java's read off an UNBOUNDED WILDCARD. */
  def addAll[E](dst: scala.collection.mutable.Iterable[E] & scala.collection.mutable.Growable[E]
                  & scala.collection.mutable.Shrinkable[E],
                src: scala.collection.IterableOnce[?] | JavaIterable[?]): Boolean = {
    val before = dst.size
    dst ++= elementsOf(src).map(_.asInstanceOf[E])
    dst.size != before
  }

  /** `java.util.List.addAll(int index, Collection c)` — the POSITIONAL sibling of [[addAll]], which
    * inserts rather than appends and which scala spells `insertAll`. */
  def insertAll[E](dst: scala.collection.mutable.Buffer[E], index: Int,
                   src: scala.collection.IterableOnce[?] | JavaIterable[?]): Boolean = {
    val before = dst.size
    dst.insertAll(index, elementsOf(src).map(_.asInstanceOf[E]))
    dst.size != before
  }

  /** `java.util.Collection.remove(Object)` — removal BY VALUE, which scala's `Buffer` does not have. */
  def removeValue[A](xs: scala.collection.mutable.Buffer[A], o: scala.Any): Boolean = {
    val i = xs.indexWhere(e => if o == null then e == null else o.equals(e))
    if i < 0 then false else { xs.remove(i); true }
  }

  /** `java.util.Collection.toArray()` — a FRESH `Object[]` of exactly `size`, in iteration order. */
  def toArray(xs: scala.collection.Iterable[?]): Array[Object] = {
    val r  = new Array[Object](xs.size)
    val it = xs.iterator
    var i  = 0
    while i < r.length && it.hasNext do {
      r(i) = it.next().asInstanceOf[Object]
      i += 1
    }
    if i < r.length then java.util.Arrays.copyOf(r, i)  // fewer elements than `size()` said
    else if !it.hasNext then r
    else grownFrom(r, i, it)                            // …and more
  }

  /** `AbstractCollection.finishToArray` — the arm for an iterator that outlives `size()`. */
  private def grownFrom[A <: AnyRef](r0: Array[A], from: Int, it: Iterator[?]): Array[A] = {
    var r = r0
    var i = from
    while it.hasNext do {
      if i == r.length then r = java.util.Arrays.copyOf(r, (r.length >> 1) + r.length + 1)
      r(i) = it.next().asInstanceOf[A]
      i += 1
    }
    if i == r.length then r else java.util.Arrays.copyOf(r, i)
  }

  /** `java.util.Collection.toArray(T[] a)` — java's THREE-part contract, reproduced exactly. */
  def toArray[A <: AnyRef](xs: scala.collection.Iterable[?], a: Array[A]): Array[A] = {
    val n  = xs.size
    val r  = if a.length >= n then a else java.util.Arrays.copyOf(a, n)
    val it = xs.iterator
    var i  = 0
    while i < r.length && it.hasNext do {
      r(i) = it.next().asInstanceOf[A]
      i += 1
    }
    // …and java's FOURTH part, which is the one the row above states: `size()` is a hint. The
    // terminator goes at the element COUNT and never at what `size()` claimed, and each of java's
    // three early-exit shapes is preserved — write into the caller's own array, trim, or copy back.
    if it.hasNext then grownFrom(r, i, it)
    else if i == r.length then r
    else if a eq r then { r(i) = null.asInstanceOf[A]; r }
    else if a.length < i then java.util.Arrays.copyOf(r, i)
    else {
      System.arraycopy(r, 0, a, 0, i)
      if a.length > i then a(i) = null.asInstanceOf[A]
      a
    }
  }

  // -------------------------------------------------------------------------------------------
  // The IMMUTABLE producers — `emptyList`, `emptyMap`, `emptySet`, `singletonList`,
  // `singletonMap`, `singleton`.

  /** `java.util.Collections.emptyList()` — an immutable, empty `List`. */
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

  /** `java.util.Collections.unmodifiableList(xs)` — a read-only VIEW of `xs`, as java's is. */
  def unmodifiableList[A](xs: scala.collection.Seq[? <: A]): scala.collection.mutable.Buffer[A] =
    new FrozenBuffer[A](xs.asInstanceOf[scala.collection.Seq[A]])

  /** `java.util.Collections.unmodifiableSet(s)` — [[unmodifiableList]]'s rule, one kind along. */
  def unmodifiableSet[A](s: scala.collection.Set[? <: A]): scala.collection.mutable.Set[A] =
    new FrozenSet[A](s.asInstanceOf[scala.collection.Set[A]])

  /** `java.util.Collections.unmodifiableMap(m)` — [[unmodifiableList]]'s rule, one kind along. */
  def unmodifiableMap[K, V](m: scala.collection.Map[? <: K, ? <: V]): scala.collection.mutable.Map[K, V] =
    new FrozenMap[K, V](m.asInstanceOf[scala.collection.Map[K, V]])

  private val frozenEmptyBuffer = new FrozenBuffer[Any](scala.collection.immutable.Vector.empty)
  private val frozenEmptySet    = new FrozenSet[Any](scala.collection.immutable.Set.empty)
  private val frozenEmptyMap    = new FrozenMap[Any, Any](scala.collection.immutable.Map.empty)

  /** `java.util.List.subList(from, to)` — a WRITE-THROUGH VIEW, as java's is. */
  def subList[A](xs: scala.collection.mutable.Buffer[A], from: Int, to: Int): scala.collection.mutable.Buffer[A] = {
    if from < 0 || to > xs.length || from > to then
      throw new IndexOutOfBoundsException(s"subList($from, $to) on a list of size ${xs.length}")
    new SubBuffer(xs, from, to)
  }

  /** `java.util.Map.putIfAbsent(k, v)` — java's exact definition, which is NOT `getOrElseUpdate`. */
  def putIfAbsent[K, V](m: scala.collection.mutable.Map[K, V], k: K, v: V): V = {
    val cur = m.get(k) match {
      case Some(x) => x
      case None    => null.asInstanceOf[V]
    }
    if cur == null then { m.put(k, v); null.asInstanceOf[V] } else cur
  }

  /** `java.util.Map.computeIfAbsent(k, f)` — java's own default implementation, which is NOT
    * `getOrElseUpdate`, and the two differ TWICE with no compile error either time. */
  def computeIfAbsent[K, V](m: scala.collection.mutable.Map[K, V], k: K,
                            f: java.util.function.Function[? >: K, ? <: V]): V = {
    val cur = m.get(k) match {
      case Some(x) => x
      case None    => null.asInstanceOf[V]
    }
    if cur != null then cur
    else {
      val v = f.asInstanceOf[java.util.function.Function[K, V]].apply(k)
      if v != null then { m.put(k, v); v } else null.asInstanceOf[V]
    }
  }

  /** `java.util.Collection.removeIf(p)` on a LIST — removes every element the predicate accepts,
    * and returns whether any went. */
  def removeIf[A](xs: scala.collection.mutable.Buffer[A],
                  p: java.util.function.Predicate[? >: A]): Boolean = {
    val q = p.asInstanceOf[java.util.function.Predicate[A]]
    var i       = 0
    var removed = false
    while i < xs.length do
      if q.test(xs(i)) then { xs.remove(i); removed = true } else i += 1
    removed
  }

  /** `java.util.Collection.removeIf(p)` on a SET — the same member at the other kind. */
  def removeIfSet[A](xs: scala.collection.mutable.Set[A],
                     p: java.util.function.Predicate[? >: A]): Boolean = {
    val q     = p.asInstanceOf[java.util.function.Predicate[A]]
    val doomed = xs.iterator.filter(q.test).toList
    doomed.foreach(xs -= _)
    doomed.nonEmpty
  }

  /** `java.util.List.spliterator()` — java's own DEFAULT, at java's own CHARACTERISTICS. */
  def orderedSpliterator[A](xs: scala.collection.Iterable[A]): java.util.Spliterator[A] =
    spliteratorWith(xs, java.util.Spliterator.ORDERED)

  /** `java.util.Set.spliterator()` — the same, `DISTINCT`. */
  def distinctSpliterator[A](xs: scala.collection.Iterable[A]): java.util.Spliterator[A] =
    spliteratorWith(xs, java.util.Spliterator.DISTINCT)

  private def spliteratorWith[A](xs: scala.collection.Iterable[A], extra: Int): java.util.Spliterator[A] =
    java.util.Spliterators.spliterator(
      new java.util.AbstractCollection[A] {
        def iterator(): java.util.Iterator[A] = {
          val it = xs.iterator
          new java.util.Iterator[A] {
            def hasNext(): Boolean = it.hasNext
            def next(): A          = it.next()
          }
        }
        def size(): Int = xs.size
      }
      ,
      extra)

  /** `java.util.Map.containsValue(v)` — with java's own equality DIRECTION. */
  def containsValue[K, V](m: scala.collection.mutable.Map[K, V], v: scala.Any): Boolean =
    m.exists { case (_, stored) =>
      (stored.asInstanceOf[AnyRef] eq v.asInstanceOf[AnyRef]) || (v != null && v.equals(stored))
    }

  /** `java.util.Collection.containsAll(c)` — java's own default, `for (Object e : c) if
    * (!contains(e)) return false`. */
  def containsAll[A](xs: scala.collection.Iterable[A],
                     c: scala.collection.IterableOnce[?] | JavaIterable[?]): Boolean =
    elementsOf(c).forall(o => xs.exists(e => if o == null then e == null else o.equals(e)))

  /** the ARGUMENT of one of java's four bulk `Collection` operations, read once, from whichever side
    * of the retyping it arrived on. */
  private def elementsOf(c: scala.collection.IterableOnce[?] | JavaIterable[?]): scala.collection.Iterator[Any] =
    c match {
      case it: scala.collection.IterableOnce[?] => it.iterator
      case ji: JavaIterable[?]                  =>
        val jit = ji.iterator()
        new scala.collection.AbstractIterator[Any] {
          def hasNext: Boolean = jit.hasNext()
          def next(): Any      = jit.next()
        }
    }

  // -------------------------------------------------------------------------------------------
  // `java.util.Collection`'s BULK DEFAULTS, at ONE receiver contract

  /** `java.util.Collection.removeAll(c)` — java's own default, `while (it.hasNext()) if (c.contains
    * (it.next())) it.remove()`, answering whether anything went. */
  def removeAll[A](xs: scala.collection.mutable.Iterable[A] & scala.collection.mutable.Growable[A]
                     & scala.collection.mutable.Shrinkable[A],
                   c: scala.collection.IterableOnce[?] | JavaIterable[?]): Boolean = {
    val probes = elementsOf(c).toList
    dropWhere(xs, e => probes.exists(o => if e == null then o == null else e.equals(o)))
  }

  /** `java.util.Collection.retainAll(c)` — the same loop with the test negated: every element of the
    * receiver NOT in the argument goes. */
  def retainAll[A](xs: scala.collection.mutable.Iterable[A] & scala.collection.mutable.Growable[A]
                     & scala.collection.mutable.Shrinkable[A],
                   c: scala.collection.IterableOnce[?] | JavaIterable[?]): Boolean = {
    val probes = elementsOf(c).toList
    dropWhere(xs, e => !probes.exists(o => if e == null then o == null else e.equals(o)))
  }

  /** remove every element the predicate dooms and say whether any did — the shared half of
    * [[removeAll]] and [[retainAll]], which differ only in that predicate. */
  private def dropWhere[A](xs: scala.collection.mutable.Iterable[A] & scala.collection.mutable.Growable[A]
                             & scala.collection.mutable.Shrinkable[A],
                           doomed: A => Boolean): Boolean = xs match {
    case b: scala.collection.mutable.Buffer[A @unchecked] =>
      var i       = 0
      var removed = false
      while i < b.length do
        if doomed(b(i)) then { b.remove(i); removed = true } else i += 1
      removed
    case _ =>
      val gone = xs.iterator.filter(doomed).toList
      gone.foreach(xs -= _)
      gone.nonEmpty
  }

  /** `java.util.ArrayList.ensureCapacity(n)` — a CAPACITY HINT, with no observable semantics. */
  def ensureCapacity(xs: scala.collection.mutable.Buffer[?], n: scala.Int): Unit = xs match {
    case ab: scala.collection.mutable.ArrayBuffer[?] => ab.sizeHint(n)
    case _                                           => ()
  }

  /** `java.util.Optional.orElse(other)` — STRICT, which is the whole reason it needs a member here
    * rather than a rename. */
  def optionalOrElse[A](o: Option[A], other: A): A = o.getOrElse(other)

  // -------------------------------------------------------------------------------------------
  // The EXTERNAL SEAM — a collection that crosses into or out of code the port does not emit.

  import scala.jdk.CollectionConverters.*

  /** a `java.util.List` a third party HANDED BACK, as the `Buffer` the port's code expects. */
  def fromJava[A](xs: java.util.List[A]): scala.collection.mutable.Buffer[A] = xs.asScala

  /** …a `java.util.Set`. */
  def fromJava[A](s: java.util.Set[A]): scala.collection.mutable.Set[A] = s.asScala

  /** …a `java.util.Map`. */
  def fromJava[K, V](m: java.util.Map[K, V]): scala.collection.mutable.Map[K, V] = m.asScala

  /** …a `java.util.Iterator`, which the port retypes to the REMOVAL-CAPABLE shim rather than to
    * scala's `Iterator`. The wrapper's `remove()` throws, which is java's own default for an
    * iterator that offers no removal — and `asScala` does not carry java's `remove` across, so
    * this is the one member of the family that loses something. It loses it LOUDLY. */
  def fromJava[A](it: java.util.Iterator[A]): JavaIterator[A] = JavaIterator.from(it.asScala)

  /** …a `java.lang.Iterable`. */
  def fromJava[A](i: java.lang.Iterable[A]): JavaIterable[A] = JavaIterable.from(i.asScala)

  // -------------------------------------------------------------------------------------------
  // An UNTYPED PROBE — the members java declares over `Object` rather than over the element type

  /** java's `Map.get(Object)` — `null` when absent, and the key is `Object`. */
  def mapGet[K, V](m: scala.collection.Map[K, V], key: Any): V =
    m.asInstanceOf[scala.collection.Map[Any, V]].getOrElse(key, null.asInstanceOf[V])

  /** java's `Map.containsKey(Object)`. */
  def mapContainsKey[K, V](m: scala.collection.Map[K, V], key: Any): Boolean =
    m.asInstanceOf[scala.collection.Map[Any, V]].contains(key)

  /** java's `Map.remove(Object)` — which RETURNS the value that was there, or `null`. */
  def mapRemove[K, V](m: scala.collection.mutable.Map[K, V], key: Any): V =
    m.asInstanceOf[scala.collection.mutable.Map[Any, V]].remove(key).getOrElse(null.asInstanceOf[V])

  /** java's `Collection.contains(Object)` at a SET receiver. */
  def setContains[A](xs: scala.collection.Set[A], o: Any): Boolean =
    xs.asInstanceOf[scala.collection.Set[Any]].contains(o)

  /** java's `Set.remove(Object)` — which RETURNS whether the set held it.
    *
    * `-=` is the rewrite everywhere else and answers the RECEIVER, so java's `boolean` is lost; this
    * member is the probe seam and java's own result, in one. */
  def setRemove[A](xs: scala.collection.mutable.Set[A], o: Any): Boolean =
    xs.asInstanceOf[scala.collection.mutable.Set[Any]].remove(o)

  // -------------------------------------------------------------------------------------------
  // java's two `Set`-typed VIEWS of a map — the semantic scala does not have

  /** java's `Map.keySet()` — the LIVE, WRITE-THROUGH view, at the type the retyping declares.
    *
    * Removal reaches the MAP, which is java's contract and is exactly the capability
    * `scala.collection.Set` cannot express; `add` throws what java's own view throws. */
  def keySetView[K, V](m: scala.collection.mutable.Map[K, V]): scala.collection.mutable.Set[K] =
    new scala.collection.mutable.AbstractSet[K] {
      def iterator: scala.collection.Iterator[K] = m.keysIterator
      def contains(k: K): Boolean                = m.contains(k)
      def addOne(k: K): this.type =
        throw new UnsupportedOperationException("add on a keySet() view")
      def subtractOne(k: K): this.type           = { m.remove(k); this }
      override def size: Int                     = m.size
      override def knownSize: Int                = m.knownSize
      override def isEmpty: Boolean              = m.isEmpty
      override def clear(): Unit                 = m.clear()
    }

  /** java's `Map.entrySet()` — the LIVE view of the map's mappings as a `Set` of pairs. */
  def entrySetView[K, V](m: scala.collection.mutable.Map[K, V]): scala.collection.mutable.Set[(K, V)] =
    new scala.collection.mutable.AbstractSet[(K, V)] {
      def iterator: scala.collection.Iterator[(K, V)] = m.iterator
      def contains(e: (K, V)): Boolean                = m.get(e._1).contains(e._2)
      def addOne(e: (K, V)): this.type =
        throw new UnsupportedOperationException("add on an entrySet() view")
      def subtractOne(e: (K, V)): this.type = {
        if m.get(e._1).contains(e._2) then m.remove(e._1)
        this
      }
      override def size: Int          = m.size
      override def knownSize: Int     = m.knownSize
      override def isEmpty: Boolean   = m.isEmpty
      override def clear(): Unit      = m.clear()
    }

  /** a class the port DECLARES that kept java's `Map.Entry` as its parent, at the `Tuple2` slot the
    * mapping gave every USE of that interface. */
  def entryToPair[K, V](e: java.util.Map.Entry[K, V]): (K, V) = (e.getKey, e.getValue)

  /** the CONSUMER direction: a `Buffer` the port holds, at a class file's `java.util.List` FORMAL. */
  def toJava[A](xs: scala.collection.mutable.Buffer[A]): java.util.List[A] = xs.asJava

  /** …a `Set`. */
  def toJava[A](s: scala.collection.mutable.Set[A]): java.util.Set[A] = s.asJava

  /** …a `Map`. Note java's `Map` is neither a `Collection` nor an `Iterable`, so this overload
    * serves exactly the `java.util.Map` formals and no others — which is the same asymmetry
    * `coerce`'s refusal table records for the shim direction. */
  def toJava[K, V](m: scala.collection.mutable.Map[K, V]): java.util.Map[K, V] = m.asJava

  /** …and a `java.util.stream.Stream` FORMAL, which the collapse is what creates. */
  def toStream[A](xs: scala.collection.Iterable[A]): java.util.stream.Stream[A] =
    xs.toBuffer.asJava.stream()

  // -------------------------------------------------------------------------------------------
  // THREE `mutable.Buffer` MEMBERS JAVA'S `List` HAS NO COUNTERPART FOR —

  /** `mutable.Buffer.remove(idx, count)` — java's `List` has no such member.
    *
    * `AbstractList.removeRange`'s own body: remove at the SAME index `count` times, which is what
    * makes it correct on a list that shifts left after each removal. */
  def bufferRemoveRange[A](self: scala.collection.mutable.Buffer[A], idx: Int, count: Int): Unit = {
    var n = count
    while n > 0 do {
      self.remove(idx)
      n -= 1
    }
  }

  /** `mutable.Buffer.insertAll(idx, elems)` — java's nearest is `addAll(int, Collection)`, whose
    * argument is a `java.util.Collection` and not an `IterableOnce`. */
  def bufferInsertAll[A](self: scala.collection.mutable.Buffer[A], idx: Int,
                        elems: scala.collection.IterableOnce[A]): Unit = {
    var at = idx
    val it = elems.iterator
    while it.hasNext do {
      self.insert(at, it.next())
      at += 1
    }
  }

  /** `mutable.Buffer.patchInPlace(from, patch, replaced)` — java has no counterpart at all.
    *
    * Scala's own contract, over the two above: `replaced` is CLAMPED to what is actually there,
    * which is what `Buffer`'s own implementations do and what keeps a `replaced` past the end from
    * throwing where scala's does not. */
  def bufferPatchInPlace[A](self: scala.collection.mutable.Buffer[A], from: Int,
                      patch: scala.collection.IterableOnce[A], replaced: Int): Unit = {
    val start = math.max(0, math.min(from, self.length))
    bufferRemoveRange(self, start, math.max(0, math.min(replaced, self.length - start)))
    bufferInsertAll(self, start, patch)
  }

  /** `java.util.Collections.reverse(list)` — in place, as java's is. */
  def reverse[A](xs: scala.collection.mutable.Buffer[A]): Unit = inPlace(xs, xs.toList.reverse)

  /** `java.util.Collections.swap(list, i, j)` — in place, as java's is. */
  def swap[A](xs: scala.collection.mutable.Buffer[A], i: scala.Int, j: scala.Int): Unit = {
    val t = xs(i); xs(i) = xs(j); xs(j) = t
  }

  /** Replace a buffer's contents, keeping the IDENTITY the caller holds. */
  private def inPlace[A](xs: scala.collection.mutable.Buffer[A], replacement: scala.collection.Seq[A]): Unit = {
    xs.clear()
    xs ++= replacement
  }

  /** `java.util.Map.Entry.comparingByKey(cmp)` over the `Tuple2` a `Map.Entry` becomes.
    *
    * The engine maps `Map.Entry` to `Tuple2` (a key/value pair has no identity of its own), which
    * means `Entry`'s own statics have to come along: `Tuple2` has no `comparingByKey`, and the call
    * survives to the compiler naming `java.util.Map.Entry` — a type the port no longer produces. */
  def comparingByKey[K, V](cmp: java.util.Comparator[? >: K]): java.util.Comparator[(K, V)] =
    (a: (K, V), b: (K, V)) => cmp.compare(a._1, b._1)

  // -------------------------------------------------------------------------------------------
  // REIFIED OCCURRENCES — an `instanceof` and a downcast ask about a RUNTIME OBJECT

  /** the two halves of a REIFIED occurrence: `is*` answers java's `instanceof`, `as*` performs
    * java's downcast. `CollectionsTransform` emits these where it retyped the tested/cast type;
    * where the target is a CONCRETE one no live view can produce (`mutable.HashMap`,
    * `ArrayBuffer`, `Tuple2`) it emits nothing and counts the refusal instead. */
  object Reified {

    import scala.jdk.CollectionConverters.*

    /** WHAT A SHIM IS DELEGATING TO, and the reason every predicate below
      * asks twice. */
    private def under(x: Any): Any = x match {
      case w: Wrapping => under(w.wrapped)
      case other       => other
    }

    /** java's `x instanceof java.util.Map`. */
    def isMap(x: Any): Boolean = {
      def test(v: Any) = v.isInstanceOf[scala.collection.mutable.Map[?, ?]] || v.isInstanceOf[java.util.Map[?, ?]]
      test(x) || test(under(x))
    }

    /** java's `x instanceof java.util.List`. */
    def isBuffer(x: Any): Boolean = {
      def test(v: Any) = v.isInstanceOf[scala.collection.mutable.Buffer[?]] || v.isInstanceOf[java.util.List[?]]
      test(x) || test(under(x))
    }

    /** java's `x instanceof java.util.Set`. */
    def isSet(x: Any): Boolean = {
      def test(v: Any) = v.isInstanceOf[scala.collection.mutable.Set[?]] || v.isInstanceOf[java.util.Set[?]]
      test(x) || test(under(x))
    }

    /** java's `x instanceof java.util.Collection` — the shim target, so the mapped subtypes'
      * targets are named beside it (see the block comment). */
    def isCollection(x: Any): Boolean = {
      def test(v: Any) = v.isInstanceOf[JavaCollection[?]] || v.isInstanceOf[scala.collection.mutable.Buffer[?]] ||
        v.isInstanceOf[scala.collection.mutable.Set[?]] || v.isInstanceOf[java.util.Collection[?]]
      test(x) || test(under(x))
    }

    /** java's `x instanceof java.lang.Iterable` — every `Collection` representation plus the
      * `Iterable` shim itself. A java `Map` is not an `Iterable`, so no map is named here. */
    def isIterable(x: Any): Boolean = {
      def test(v: Any) = v.isInstanceOf[JavaIterable[?]] || v.isInstanceOf[java.lang.Iterable[?]]
      test(x) || test(under(x)) || isCollection(x)
    }

    /** java's `x instanceof java.util.Iterator`. */
    def isIterator(x: Any): Boolean = {
      def test(v: Any) = v.isInstanceOf[JavaIterator[?]] || v.isInstanceOf[java.util.Iterator[?]]
      test(x) || test(under(x))
    }

    // -----------------------------------------------------------------------------------------
    // …and the CAST. Each returns the port's representation, LIVE where the value is java's —
    // the same `asScala` view `fromJava` gives, for the same reason: the producer may still hold
    // the collection, so a copy would detach every later change. The result carries wildcard type
    // arguments and the emitter leaves java's own cast in place around the call, which is exact:

    // …and each cast is taken THROUGH `under`, for the reason stated there: java's cast is the
    // identity, so `(Collection) list` followed by `(List) list` is two casts of ONE object, and
    // the second one reached `asInstanceOf[Buffer]` on the wrapper the first one built — a
    // `ClassCastException` in a program that compiled and whose every check count was flat.

    /** java's `(java.util.Map<K,V>) x`. */
    def asMap(x: Any): scala.collection.mutable.Map[?, ?] = under(x) match {
      case m: java.util.Map[?, ?] => m.asInstanceOf[java.util.Map[Any, Any]].asScala
      case m                      => m.asInstanceOf[scala.collection.mutable.Map[?, ?]]
    }

    /** java's `(java.util.List<A>) x`. */
    def asBuffer(x: Any): scala.collection.mutable.Buffer[?] = under(x) match {
      case xs: java.util.List[?] => xs.asInstanceOf[java.util.List[Any]].asScala
      case xs                    => xs.asInstanceOf[scala.collection.mutable.Buffer[?]]
    }

    /** java's `(java.util.Set<A>) x`. */
    def asSet(x: Any): scala.collection.mutable.Set[?] = under(x) match {
      case xs: java.util.Set[?] => xs.asInstanceOf[java.util.Set[Any]].asScala
      case xs                   => xs.asInstanceOf[scala.collection.mutable.Set[?]]
    }

    /** java's `(java.util.Collection<A>) x`. */

    // The three SHIM targets keep their identity arm FIRST and do not unwrap: a value that already
    // IS the target is what java's identity cast yields, and rebuilding it from the underlying
    // would replace one wrapper with another for no gain.
    def asCollection(x: Any): JavaCollection[?] = x match {
      case c: JavaCollection[?]                       => c
      case xs: scala.collection.mutable.Buffer[?]     => JavaCollection.from(xs.asInstanceOf[scala.collection.mutable.Buffer[Any]])
      case xs: scala.collection.mutable.Set[?]        => JavaCollection.fromSet(xs.asInstanceOf[scala.collection.mutable.Set[Any]])
      case c: java.util.Collection[?]                 => JavaCollection.fromJava(c.asInstanceOf[java.util.Collection[Any]])
      case other                                      => other.asInstanceOf[JavaCollection[?]]
    }

    /** java's `(java.lang.Iterable<A>) x`. */
    def asIterable(x: Any): JavaIterable[?] = x match {
      case i: JavaIterable[?]     => i
      case i: java.lang.Iterable[?] =>
        JavaIterable.from(i.asInstanceOf[java.lang.Iterable[Any]].asScala)
      case xs: scala.collection.Iterable[?] =>
        JavaIterable.from(xs.asInstanceOf[scala.collection.Iterable[Any]])
      case other => other.asInstanceOf[JavaIterable[?]]
    }

    /** java's `(java.util.Iterator<A>) x`. */
    def asIterator(x: Any): JavaIterator[?] = x match {
      case it: JavaIterator[?]      => it
      case it: java.util.Iterator[?] =>
        JavaIterator.from(it.asInstanceOf[java.util.Iterator[Any]].asScala)
      case other => other.asInstanceOf[JavaIterator[?]]
    }

    // -----------------------------------------------------------------------------------------
    // THE EGRESS DIRECTION, face 1

    /** the value AS JAVA WOULD HAVE HELD IT, decided from the object rather than from its type.
      *
      * Identity for everything this engine did not put there — a `String`, a boxed number, a
      * `java.util.*` the port never touched, a class the port emits — so it is safe to insert
      * wherever a value crosses out and the phase cannot prove the value is harmless. */
    def toJavaValue(x: Any): java.lang.Object = under(x) match {
      case null                             => null
      // …`Map` BEFORE `Iterable`: a scala `Map` is a `scala.collection.Iterable` of pairs, and a
      // java `Map` is not a `java.util.Collection` at all. The wrong order turns every map into a
      // list of tuples, which is the same asymmetry `isCollection` is exact about.
      case m: scala.collection.Map[?, ?]    => new MapView(m.asInstanceOf[scala.collection.Map[Any, Any]])
      case s: scala.collection.Set[?]       => new SetView(s.asInstanceOf[scala.collection.Set[Any]])
      case q: scala.collection.Seq[?]       => new ListView(q.asInstanceOf[scala.collection.Seq[Any]])
      case i: scala.collection.Iterable[?]  => new IterableView(i.asInstanceOf[scala.collection.Iterable[Any]])
      case it: scala.collection.Iterator[?] => new IteratorView(it.asInstanceOf[scala.collection.Iterator[Any]])
      // …and the three SHIMS, which are the other representation this engine introduces: a scala
      // trait with java's SHAPE is not a java type, so a java consumer sees a bean here too. A
      // DELEGATING one never reaches these arms — `under` has already replaced it with the scala
      // collection it wraps — so what is left is a class the port EMITS that implements the shim,
      // which in java implemented `java.util.Collection` and must read as one.
      case c: JavaCollection[?]             => new ShimCollectionView(c.asInstanceOf[JavaCollection[Any]])
      case i: JavaIterable[?]               => new ShimIterableView(i.asInstanceOf[JavaIterable[Any]])
      case it: JavaIterator[?]              => new ShimIteratorView(it.asInstanceOf[JavaIterator[Any]])
      case a: Array[AnyRef]                 => arrayValue(a, new java.util.IdentityHashMap[AnyRef, AnyRef]())
      case other                            => other.asInstanceOf[java.lang.Object]
    }

    /** java and scala agree about arrays, so the SPINE is already right and only the elements can be
      * wrong. Converted eagerly because an array has no view, and returned AS IT ARRIVED whenever
      * nothing moved — an array of `String` crossing out is the same object it was. */
    private def arrayValue(a: Array[AnyRef], seen: java.util.IdentityHashMap[AnyRef, AnyRef]): AnyRef =
      if seen.containsKey(a) then a
      else {
        seen.put(a, a)
        var moved = false
        val out = new Array[AnyRef](a.length)
        var i = 0
        while i < a.length do {
          out(i) = a(i) match {
            case n: Array[AnyRef] => arrayValue(n, seen)
            case _                => toJavaValue(a(i))
          }
          if out(i) ne a(i) then moved = true
          i += 1
        }
        if moved then out else a
      }

    /** the one entry shape these views need. Not `java.util.AbstractMap.SimpleImmutableEntry`,
      * whose availability is a platform question this module answers for itself; `equals` and
      * `hashCode` are java's own documented contract for `Map.Entry`. */
    private final class Entry(k: java.lang.Object, v: java.lang.Object) extends java.util.Map.Entry[Any, Any] {
      def getKey(): Any = k
      def getValue(): Any = v
      def setValue(value: Any): Any = throw new UnsupportedOperationException("setValue")
      override def equals(o: Any): Boolean = o match {
        case e: java.util.Map.Entry[?, ?] =>
          (if k == null then e.getKey == null else k.equals(e.getKey)) &&
            (if v == null then e.getValue == null else v.equals(e.getValue))
        case _ => false
      }
      override def hashCode(): Int =
        (if k == null then 0 else k.hashCode) ^ (if v == null then 0 else v.hashCode)
    }

    private final class MapView(m: scala.collection.Map[Any, Any]) extends java.util.AbstractMap[Any, Any] {
      def entrySet(): java.util.Set[java.util.Map.Entry[Any, Any]] =
        new java.util.AbstractSet[java.util.Map.Entry[Any, Any]] {
          def iterator(): java.util.Iterator[java.util.Map.Entry[Any, Any]] =
            new java.util.Iterator[java.util.Map.Entry[Any, Any]] {
              private val it = m.iterator
              def hasNext(): Boolean = it.hasNext
              def next(): java.util.Map.Entry[Any, Any] = {
                val (k, v) = it.next()
                new Entry(toJavaValue(k), toJavaValue(v))
              }
            }
          def size(): Int = m.size
        }
      // `AbstractMap` derives both from `entrySet`, i.e. in O(n). The map this views has them, and
      // a consumer that looks a key up should not pay for the whole spine.
      override def size(): Int = m.size
      override def isEmpty(): Boolean = m.isEmpty
    }

    private final class ListView(q: scala.collection.Seq[Any]) extends java.util.AbstractList[Any] {
      def get(i: Int): Any = toJavaValue(q(i))
      override def size(): Int = q.size
    }

    private final class SetView(s: scala.collection.Set[Any]) extends java.util.AbstractSet[Any] {
      def iterator(): java.util.Iterator[Any] = new IteratorView(s.iterator)
      def size(): Int = s.size
    }

    /** a scala `Iterable` that is neither a `Seq`, a `Set` nor a `Map` — java's nearest honest
      * shape for it is a `Collection` whose `add` throws, which is what `AbstractCollection` is. */
    private final class IterableView(i: scala.collection.Iterable[Any]) extends java.util.AbstractCollection[Any] {
      def iterator(): java.util.Iterator[Any] = new IteratorView(i.iterator)
      def size(): Int = i.size
    }

    private final class IteratorView(it: scala.collection.Iterator[Any]) extends java.util.Iterator[Any] {
      def hasNext(): Boolean = it.hasNext
      def next(): Any = toJavaValue(it.next())
    }

    private final class ShimCollectionView(c: JavaCollection[Any]) extends java.util.AbstractCollection[Any] {
      def iterator(): java.util.Iterator[Any] = new ShimIteratorView(c.iterator())
      def size(): Int = c.size()
    }

    private final class ShimIterableView(i: JavaIterable[Any]) extends java.util.AbstractCollection[Any] {
      def iterator(): java.util.Iterator[Any] = new ShimIteratorView(i.iterator())
      // `java.lang.Iterable` has no `size`, so the only honest answer is to count — which is what
      // a java caller asking a `Collection` view of an `Iterable` is asking for.
      def size(): Int = {
        val it = i.iterator()
        var n = 0
        while it.hasNext() do { it.next(); n += 1 }
        n
      }
    }

    private final class ShimIteratorView(it: JavaIterator[Any]) extends java.util.Iterator[Any] {
      def hasNext(): Boolean = it.hasNext()
      def next(): Any = toJavaValue(it.next())
    }
  }

  def comparingByValue[K, V](cmp: java.util.Comparator[? >: V]): java.util.Comparator[(K, V)] =
    (a: (K, V), b: (K, V)) => cmp.compare(a._2, b._2)

  /** `Stream.sorted(Comparator)` on a chain this engine has already collapsed to a `Buffer`. */
  def sortedWith[A](xs: scala.collection.mutable.Buffer[A], cmp: java.util.Comparator[? >: A])
      : scala.collection.mutable.Buffer[A] =
    xs.sortWith((a, b) => cmp.compare(a, b) < 0)

  /** `Stream.mapToDouble(f)` on a chain already collapsed to a `Buffer`. */
  def mapToDouble[A](xs: scala.collection.mutable.Buffer[A], f: A => Double): scala.collection.mutable.Buffer[Double] =
    xs.map(f)

  /** `IntStream.range(a, b)` — a stream SOURCE that is not a collection, so nothing else in the
    * chain-collapse can produce it. Half-open, as java's is. */
  def intRange(startInclusive: Int, endExclusive: Int): scala.collection.mutable.Buffer[Int] =
    scala.collection.mutable.ArrayBuffer.from(startInclusive until endExclusive)

  /** `Collectors.toSet()` on a chain this engine has already collapsed to a `Buffer`. */
  def toSet[A](xs: scala.collection.mutable.Buffer[A]): scala.collection.mutable.Set[A] =
    scala.collection.mutable.HashSet.from(xs)

  /** `Collectors.toMap(keyFn, valueFn)` — java's TWO-argument form, which THROWS on a duplicate key. */
  def toMap[A, K, V](
      xs: scala.collection.mutable.Buffer[A],
      key: java.util.function.Function[? >: A, ? <: K],
      value: java.util.function.Function[? >: A, ? <: V]): scala.collection.mutable.Map[K, V] = {
    val m = scala.collection.mutable.HashMap.empty[K, V]
    xs.foreach { x =>
      val k = key.apply(x)
      if m.contains(k) then throw new IllegalStateException(s"Duplicate key $k")
      m(k) = value.apply(x)
    }
    m
  }

  /** `Collectors.toMap(keyFn, valueFn, mergeFn)` — the overload that RESOLVES a duplicate key. */
  def toMap[A, K, V](
      xs: scala.collection.mutable.Buffer[A],
      key: java.util.function.Function[? >: A, ? <: K],
      value: java.util.function.Function[? >: A, ? <: V],
      merge: java.util.function.BinaryOperator[V]): scala.collection.mutable.Map[K, V] = {
    val m = scala.collection.mutable.HashMap.empty[K, V]
    xs.foreach { x =>
      val k = key.apply(x)
      val v = value.apply(x)
      m.get(k) match {
        case Some(old) =>
          val merged = merge.apply(old, v)
          if merged == null then m.remove(k) else m(k) = merged
        case None => m(k) = v
      }
    }
    m
  }

  /** `Collectors.toCollection(Factory::new)` — build the factory's collection and fill it.
    *
    * `Growable` is the exact bound: it is what "a collection you can add to" is in scala, and it is
    * what every `Collectors.toCollection` target satisfies. */
  def into[A, C <: scala.collection.mutable.Growable[A]](
      xs: scala.collection.mutable.Buffer[A], factory: () => C): C = {
    val c = factory()
    c ++= xs
    c
  }

  /** A `mutable.Buffer` that REFUSES every mutation, as java's immutable lists do. */
  private final class FrozenBuffer[A](under: scala.collection.Seq[A])
      extends scala.collection.mutable.AbstractBuffer[A] {
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
  }

  /** `java.util.Arrays.asList(T[])`'s live, fixed-size view — see [[asListView]] for the contract.
    *
    * Every size-preserving operation reads or writes the ARRAY; every size-CHANGING one throws, as
    * `java.util.Arrays$ArrayList` does. `refuseFixedSize` rather than [[refuse]] because the reason
    * is a different one and the message a reader gets at run time is the whole value of throwing. */
  private final class ArrayViewBuffer[A](arr: Array[A]) extends scala.collection.mutable.AbstractBuffer[A] {
    def apply(i: Int): A                                = arr(i)
    def length: Int                                     = arr.length
    override def knownSize: Int                         = arr.length
    override def iterator: scala.collection.Iterator[A] = arr.iterator
    def update(i: Int, elem: A): Unit                   = arr(i) = elem
    def insert(idx: Int, elem: A): Unit                 = refuseFixedSize
    def insertAll(idx: Int, elems: scala.collection.IterableOnce[A]): Unit = refuseFixedSize
    def prepend(elem: A): this.type                     = refuseFixedSize
    def remove(idx: Int): A                             = refuseFixedSize
    def remove(idx: Int, count: Int): Unit              = refuseFixedSize
    def addOne(elem: A): this.type                      = refuseFixedSize
    def clear(): Unit                                   = refuseFixedSize
    override def patchInPlace(from: Int, patch: scala.collection.IterableOnce[A], replaced: Int): this.type =
      refuseFixedSize
  }

  /** `java.util.List.subList`'s view — see [[subList]] for the contract and for what is
    * deliberately not reproduced. `until` is a `var` because java's view resizes when you insert or
    * remove THROUGH it. */
  private final class SubBuffer[A](
      under: scala.collection.mutable.Buffer[A], from: Int, private var until: Int)
      extends scala.collection.mutable.AbstractBuffer[A] {
    def length: Int = until - from
    private def at(i: Int): Int =
      if i < 0 || i >= length then throw new IndexOutOfBoundsException(s"$i (sublist size $length)")
      else from + i
    private def gap(i: Int): Int =
      if i < 0 || i > length then throw new IndexOutOfBoundsException(s"$i (sublist size $length)")
      else from + i
    def apply(i: Int): A                                = under(at(i))
    override def iterator: scala.collection.Iterator[A] = Iterator.range(0, length).map(apply)
    def update(i: Int, elem: A): Unit                   = under(at(i)) = elem
    def insert(idx: Int, elem: A): Unit                 = { under.insert(gap(idx), elem); until += 1 }
    def insertAll(idx: Int, elems: scala.collection.IterableOnce[A]): Unit = {
      val es = scala.collection.immutable.Vector.from(elems)
      under.insertAll(gap(idx), es)
      until += es.size
    }
    def prepend(elem: A): this.type = { insert(0, elem); this }
    def addOne(elem: A): this.type  = { insert(length, elem); this }
    def remove(idx: Int): A         = { val v = under.remove(at(idx)); until -= 1; v }
    def remove(idx: Int, count: Int): Unit = {
      if count < 0 then throw new IllegalArgumentException(s"removing a negative number of elements: $count")
      var n = count
      while n > 0 do { under.remove(at(idx)); until -= 1; n -= 1 }
    }
    def clear(): Unit = remove(0, length)
    def patchInPlace(idx: Int, patch: scala.collection.IterableOnce[A], replaced: Int): this.type = {
      val es = scala.collection.immutable.Vector.from(patch)
      remove(idx, math.min(math.max(replaced, 0), length - idx))
      insertAll(idx, es)
      this
    }
  }

  /** [[FrozenBuffer]]'s `Set`. */
  private final class FrozenSet[A](under: scala.collection.Set[A])
      extends scala.collection.mutable.AbstractSet[A] {
    def contains(elem: A): Boolean                      = under.contains(elem)
    def iterator: scala.collection.Iterator[A]          = under.iterator
    override def knownSize: Int                         = under.size
    def addOne(elem: A): this.type                      = refuse
    def subtractOne(elem: A): this.type                 = refuse
    override def clear(): Unit                          = refuse
  }

  /** [[FrozenBuffer]]'s `Map`. */
  private final class FrozenMap[K, V](under: scala.collection.Map[K, V])
      extends scala.collection.mutable.AbstractMap[K, V] {
    def get(key: K): Option[V]                          = under.get(key)
    def iterator: scala.collection.Iterator[(K, V)]     = under.iterator
    override def knownSize: Int                         = under.size
    def addOne(kv: (K, V)): this.type                   = refuse
    def subtractOne(k: K): this.type                    = refuse
    override def clear(): Unit                          = refuse
  }

  /** java's own answer at every one of those members — `UnsupportedOperationException`, with the
    * message naming what the value IS, since the alternative a reader will guess is an engine bug. */
  private def refuseFixedSize: Nothing = throw new UnsupportedOperationException(
    "this list is java's `Arrays.asList(array)` — a FIXED-SIZE view of the caller's array. " +
      "`set` writes through; `add`/`remove`/`clear` throw, and java throws here too")

  private def refuse: Nothing = throw new UnsupportedOperationException(
    "this collection came from a java factory that returns an IMMUTABLE collection or an " +
      "unmodifiable VIEW (Collections.emptyList/emptyMap/emptySet/singletonList/singletonMap/" +
      "singleton/unmodifiableList/unmodifiableSet/unmodifiableMap); java throws here too")
}
