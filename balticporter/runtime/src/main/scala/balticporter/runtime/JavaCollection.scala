package balticporter.runtime

/** `java.util.Collection` / `java.util.AbstractCollection`, as Scala — java's interface, not
  * scala's collection. */

/** The ABSTRACT/CONCRETE split is `java.util.AbstractCollection`'s OWN, member for member: only
  * `iterator()` and `size()` are abstract there, and everything else has a default written over
  * those two. */
trait JavaCollection[A] extends JavaIterable[A] {

  // ---- the only two java leaves abstract ----
  def size(): Int

  // ---- derived, per java.util.AbstractCollection, over `iterator()` and `size()` ----

  def isEmpty(): Boolean = size() == 0

  /** java's own null-tolerant comparison: a `null` probe matches a `null` element. */
  def contains(o: java.lang.Object): Boolean = {
    val it = iterator()
    var found = false
    while !found && it.hasNext() do {
      val e = it.next().asInstanceOf[java.lang.Object]
      found = if o == null then e == null else o.equals(e)
    }
    found
  }

  /** `AbstractCollection.add` THROWS — it is not a no-op and not abstract. A java subclass that
    * does not override it really does reject `add`, so reproducing that is faithful; making it
    * abstract instead would demand an implementation java never required. */
  def add(e: A): Boolean = throw new UnsupportedOperationException("add")

  /** removes THROUGH the iterator, as java does, so the first matching element goes and the rest of
    * the collection is untouched. */
  def remove(o: java.lang.Object): Boolean = {
    val it = iterator()
    var removed = false
    while !removed && it.hasNext() do {
      val e = it.next().asInstanceOf[java.lang.Object]
      if (if o == null then e == null else o.equals(e)) then { it.remove(); removed = true }
    }
    removed
  }

  def clear(): Unit = {
    val it = iterator()
    while it.hasNext() do { it.next(); it.remove() }
  }

  def containsAll(c: JavaCollection[?]): Boolean = {
    val it = c.iterator()
    var ok = true
    while ok && it.hasNext() do ok = contains(it.next().asInstanceOf[java.lang.Object])
    ok
  }

  def addAll(c: JavaCollection[? <: A]): Boolean = {
    val it = c.iterator()
    var changed = false
    while it.hasNext() do if add(it.next()) then changed = true
    changed
  }

  def removeAll(c: JavaCollection[?]): Boolean = {
    var changed = false
    val it = c.iterator()
    while it.hasNext() do if remove(it.next().asInstanceOf[java.lang.Object]) then changed = true
    changed
  }

  /** java removes THROUGH the iterator here, so the receiver is modified in place and the
    * traversal stays valid — a copy-then-clear would lose an alias the caller may hold. */
  def retainAll(c: JavaCollection[?]): Boolean = {
    var changed = false
    val it = iterator()
    while it.hasNext() do
      if !c.contains(it.next().asInstanceOf[java.lang.Object]) then { it.remove(); changed = true }
    changed
  }

  /** JAVA's signature, `java.util.function.Predicate` included — not `A => Boolean`. */
  def removeIf(filter: java.util.function.Predicate[? >: A]): Boolean = {
    var changed = false
    val it = iterator()
    while it.hasNext() do
      if filter.test(it.next()) then { it.remove(); changed = true }
    changed
  }

  def toArray(): scala.Array[Object] = {
    val out = new scala.Array[Object](size())
    val it  = iterator()
    var i   = 0
    while it.hasNext() && i < out.length do { out(i) = it.next().asInstanceOf[Object]; i += 1 }
    out
  }

  /** `Collection.toArray(T[])` — the ARRAY-TAKING twin, which four of simple-graphs' classes override
    * and which was simply absent: `method toArray overrides nothing`, reported only once RefChecks
    * ran. */

  /** `T <: java.lang.Object`, not a bare `T`. Java's implicit type-parameter bound IS `Object`, and
    * the port renders it — `toArray[U <: java.lang.Object]` — so a shim declaring `[T]` (bound `Any`)
    * has a DIFFERENT signature and overrides nothing. Same rule as `contains(Object)` above, one
    * level in: scala's `Any` is not java's `Object`, and RefChecks is the only thing that says so. */
  def toArray[T <: java.lang.Object](a: scala.Array[T]): scala.Array[T] = {
    val n   = size()
    val out = if a.length >= n then a else scala.Array.copyOf(a, n)
    val it  = iterator()
    var i   = 0
    while it.hasNext() && i < out.length do { out(i) = it.next().asInstanceOf[T]; i += 1 }
    if out.length > n then out(n) = null.asInstanceOf[T]
    out
  }
}

object JavaCollection {

  /** Adapt a scala collection to the java-shaped one — the counterpart of
    * [[JavaIterable.from]], for a call site where a `Collection`-typed parameter meets a
    * collection the port itself mapped to scala. */
  def from[A](xs: scala.collection.mutable.Buffer[A]): JavaCollection[A] = new JavaCollection[A] with Wrapping {
    // …and it SAYS what it delegates to, so a later reified question is asked of the buffer java
    // would still have been looking at.
    def wrapped: Any = xs
    def iterator(): JavaIterator[A] = new JavaIterator[A] {
      private var cursor = 0
      private var last   = -1
      def hasNext(): Boolean = cursor < xs.size
      def next(): A          = { last = cursor; cursor += 1; xs(last) }
      override def remove(): Unit = {
        if last < 0 then throw new IllegalStateException("remove")
        xs.remove(last)
        cursor = last
        last = -1
      }
    }
    def size(): Int                 = xs.size
    override def isEmpty(): Boolean          = xs.isEmpty
    override def contains(o: java.lang.Object): Boolean = xs.contains(o)
    override def add(e: A): Boolean          = { xs += e; true }
    override def remove(o: java.lang.Object): Boolean = {
      val i = xs.indexWhere(_ == o)
      if i < 0 then false else { xs.remove(i); true }
    }
    override def clear(): Unit               = xs.clear()
  }

  /** the OTHER direction — a `java.util.Collection` a third party HANDED BACK, at a slot the
    * retyping made this shim. */
  def fromJava[A](c: java.util.Collection[A]): JavaCollection[A] = new JavaCollection[A] with Wrapping {
    def wrapped: Any = c
    def iterator(): JavaIterator[A] = new JavaIterator[A] {
      private val it = c.iterator()
      def hasNext(): Boolean      = it.hasNext
      def next(): A               = it.next()
      override def remove(): Unit = it.remove()
    }
    def size(): Int                                     = c.size()
    override def isEmpty(): Boolean                     = c.isEmpty()
    override def contains(o: java.lang.Object): Boolean = c.contains(o)
    override def add(e: A): Boolean                     = c.add(e)
    override def remove(o: java.lang.Object): Boolean   = c.remove(o)
    override def clear(): Unit                          = c.clear()
  }

  /** The same seam for a `Kind.Set` source — `java.util.Set` IS a `java.util.Collection`, so a
    * ported method taking a `Collection` must still accept the port's `mutable.Set`. */
  def fromSet[A](xs: scala.collection.mutable.Set[A]): JavaCollection[A] = new JavaCollection[A] with Wrapping {
    def wrapped: Any = xs
    def iterator(): JavaIterator[A] = new JavaIterator[A] {
      private val order          = xs.toList.iterator
      private var last: Option[A] = scala.None
      def hasNext(): Boolean = order.hasNext
      def next(): A          = { val e = order.next(); last = Some(e); e }
      override def remove(): Unit = last match {
        case Some(e)    => xs -= e; last = scala.None
        case scala.None => throw new IllegalStateException("remove")
      }
    }
    def size(): Int                 = xs.size
    override def isEmpty(): Boolean = xs.isEmpty
    override def contains(o: java.lang.Object): Boolean =
      xs.exists(e => if o == null then e == null else o.equals(e))
    override def add(e: A): Boolean = if xs.contains(e) then false else { xs += e; true }
    override def remove(o: java.lang.Object): Boolean =
      xs.find(e => if o == null then e == null else o.equals(e)) match {
        case Some(e) => xs -= e; true
        case scala.None => false
      }
    override def clear(): Unit = xs.clear()
  }

  /** Adapt a scala collection that the port may NOT mutate through — a DISTINCT NAME rather than an
    * overload of [[from]], deliberately. */
  def unmodifiableFrom[A](xs: scala.collection.Iterable[A]): JavaCollection[A] = new JavaCollection[A] {
    def iterator(): JavaIterator[A] = JavaIterator.from(xs.iterator)
    def size(): Int                 = xs.size
    override def isEmpty(): Boolean          = xs.isEmpty
    override def contains(o: java.lang.Object): Boolean = xs.iterator.contains(o)
    override def add(e: A): Boolean          = throw new UnsupportedOperationException("add on an unmodifiable collection")
    override def remove(o: java.lang.Object): Boolean = throw new UnsupportedOperationException("remove on an unmodifiable collection")
    override def clear(): Unit               = throw new UnsupportedOperationException("clear on an unmodifiable collection")
  }

  /** `java.util.Collections.unmodifiableCollection`, with java's own signature. */
  def unmodifiable[T](c: JavaCollection[? <: T]): JavaCollection[T] = new JavaCollection[T] {
    // the WRAPPED collection's iterator may be removal-capable ([[from]] now is), and java's
    // `unmodifiableCollection` returns one whose `remove()` throws — otherwise a caller removes
    // through a view that rejects `remove`, which is the read-only guarantee gone with a green
    // compile. Delegation is not enough here; the removal has to be refused explicitly.
    def iterator(): JavaIterator[T] = new JavaIterator[T] {
      private val u          = c.iterator()
      def hasNext(): Boolean = u.hasNext()
      def next(): T          = u.next()
      override def remove(): Unit = throw new UnsupportedOperationException("remove on an unmodifiable collection")
    }
    def size(): Int                 = c.size()
    override def isEmpty(): Boolean          = c.isEmpty()
    override def contains(o: java.lang.Object): Boolean = c.contains(o)
    override def add(e: T): Boolean          = throw new UnsupportedOperationException("add on an unmodifiable collection")
    override def remove(o: java.lang.Object): Boolean = throw new UnsupportedOperationException("remove on an unmodifiable collection")
    override def clear(): Unit               = throw new UnsupportedOperationException("clear on an unmodifiable collection")
  }

  /** `Stream.filter(Predicate)`, as a function rather than a synthesised lambda. */
  def filtered[A](xs: scala.collection.mutable.Buffer[A], p: java.util.function.Predicate[? >: A])
      : scala.collection.mutable.Buffer[A] = xs.filter(p.test(_))

  extension [A](self: JavaCollection[A]) {
    /** a scala view — `map`, `filter`, `foreach` and the rest. Inherited `foreach` from
      * [[JavaIterable]] already covers `for (x <- xs)`. */
    def asScalaBuffer: scala.collection.mutable.Buffer[A] = {
      val b = scala.collection.mutable.ArrayBuffer.empty[A]
      val it = self.iterator()
      while it.hasNext() do b += it.next()
      b
    }
  }
}
