package balticporter.runtime

/** `java.util.Collection` / `java.util.AbstractCollection`, as Scala — java's interface, not
  * scala's collection.
  *
  * ==Why a standalone type and not `mutable.Buffer`==
  * A library that merely USES java collections is fine with `Buffer`: `add`/`remove`/`contains`/
  * `size` all have counterparts and the retyping is invisible. A library that **defines its own
  * collection** is not, and the two cases were conflated until simple-graphs made the difference
  * a number — 27 of its 30 compile errors.
  *
  * `Array`, `NodeCollection` and `VertexCollection` there each `extends AbstractCollection<T>` and
  * declare their own `size()`, `isEmpty()`, `contains(o)`, `iterator()`, `add`, `remove`, `clear`,
  * `containsAll`, `addAll`, `removeAll`, `retainAll`. Making such a class extend
  * `scala.collection.mutable.Buffer` is `CLAUDE.md` §4.5 exactly: the scala collection traits are
  * large and interlocking, they demand `apply`/`update`/`insert`/`patchInPlace` the java class never
  * had, and the members it DOES have collide — java's nilary `iterator()` against scala's
  * parameterless `iterator` is the same clash `JavaIterable` was created for.
  *
  * So this is the third member of that family, for the same reason and with the same shape: java's
  * own member ARITY, no scala collection in the parents, interop by extension.
  *
  * ==Why `java.util.Collection` maps here too, and not only the abstract base==
  * Because subtyping has to hold in both directions and no bridge can supply it. In simple-graphs
  * `Collection` is a RETURN type for the library's own collections (`Node.getConnections()`,
  * `Graph.getVertices()`) and a PARAMETER type for arbitrary ones (`addVertices(Collection<V>)`).
  * If the parameter retyped to `Buffer` while the classes became `JavaCollection`, the two would
  * never meet.
  *
  * An implicit bridge is not available either, and that is measured rather than assumed:
  * `ENGINE-LIMITS` records `given Conversion` as inert against the corpus because **scala does not
  * attempt an implicit conversion when no overload alternative matches**, and `addVertices` is
  * overloaded with `addVertices(V...)` — precisely the shape no bridge can rescue.
  *
  * ==What this costs==
  * A scala collection can no longer be passed where a java `Collection` is expected. In
  * simple-graphs that is 2 sites, both `stream().collect(Collectors.toList())` chains, which are
  * already unbuildable for an unrelated reason (`ENGINE-LIMITS` K6). Where it does bite, the fix is
  * [[JavaCollection.from]] at the call site, the same seam `JavaIterable.from` already provides.
  */
/** The ABSTRACT/CONCRETE split is `java.util.AbstractCollection`'s OWN, member for member: only
  * `iterator()` and `size()` are abstract there, and everything else has a default written over
  * those two.
  *
  * ==Why that split, exactly, and not a plausible one==
  * Both halves matter, and getting either wrong is invisible until the very last typer error is
  * gone. `contains`, `isEmpty`, `remove` and `clear` were abstract here for one session, and
  * `RefChecks` — which dotty skips entirely while any typer error remains (CLAUDE.md §3) — then
  * reported `class Array needs to be abstract, since: it has 2 unimplemented members` for four of
  * simple-graphs' classes at once. Java's authors never implemented those members because
  * `AbstractCollection` already had; a shim that demands them is asking for code the source does not
  * contain.
  *
  * The other half of the same rule: a java class extending `AbstractCollection` inherits
  * `addAll`/`removeAll`/`retainAll`/`containsAll`/`toArray` without writing them, and `Collection`'s
  * java-8 `removeIf` likewise, so a shim missing THOSE leaves every such call unresolved. That was 6
  * errors, found the same way.
  *
  * ==`java.lang.Object`, never `Any`==
  * `contains(Object)` and `remove(Object)` take `java.lang.Object` because java does, and because
  * scala keeps the two distinct: a ported `override def contains(item: java.lang.Object)` implements
  * nothing at all against a `contains(o: Any)`, and says so only once RefChecks runs.
  *
  * A class that overrides any of these simply overrides it, which is what java does too. */
trait JavaCollection[A] extends JavaIterable[A]:

  // ---- the only two java leaves abstract ----
  def size(): Int

  // ---- derived, per java.util.AbstractCollection, over `iterator()` and `size()` ----

  def isEmpty(): Boolean = size() == 0

  /** java's own null-tolerant comparison: a `null` probe matches a `null` element. */
  def contains(o: java.lang.Object): Boolean =
    val it = iterator()
    var found = false
    while !found && it.hasNext() do
      val e = it.next().asInstanceOf[java.lang.Object]
      found = if o == null then e == null else o.equals(e)
    found

  /** `AbstractCollection.add` THROWS — it is not a no-op and not abstract. A java subclass that
    * does not override it really does reject `add`, so reproducing that is faithful; making it
    * abstract instead would demand an implementation java never required. */
  def add(e: A): Boolean = throw new UnsupportedOperationException("add")

  /** removes THROUGH the iterator, as java does, so the first matching element goes and the rest of
    * the collection is untouched. */
  def remove(o: java.lang.Object): Boolean =
    val it = iterator()
    var removed = false
    while !removed && it.hasNext() do
      val e = it.next().asInstanceOf[java.lang.Object]
      if (if o == null then e == null else o.equals(e)) then { it.remove(); removed = true }
    removed

  def clear(): Unit =
    val it = iterator()
    while it.hasNext() do { it.next(); it.remove() }

  def containsAll(c: JavaCollection[?]): Boolean =
    val it = c.iterator()
    var ok = true
    while ok && it.hasNext() do ok = contains(it.next().asInstanceOf[java.lang.Object])
    ok

  def addAll(c: JavaCollection[? <: A]): Boolean =
    val it = c.iterator()
    var changed = false
    while it.hasNext() do if add(it.next()) then changed = true
    changed

  def removeAll(c: JavaCollection[?]): Boolean =
    var changed = false
    val it = c.iterator()
    while it.hasNext() do if remove(it.next().asInstanceOf[java.lang.Object]) then changed = true
    changed

  /** java removes THROUGH the iterator here, so the receiver is modified in place and the
    * traversal stays valid — a copy-then-clear would lose an alias the caller may hold. */
  def retainAll(c: JavaCollection[?]): Boolean =
    var changed = false
    val it = iterator()
    while it.hasNext() do
      if !c.contains(it.next().asInstanceOf[java.lang.Object]) then { it.remove(); changed = true }
    changed

  /** JAVA's signature, `java.util.function.Predicate` included — not `A => Boolean`.
    *
    * This is §4.5's rule ("java's own shape, java's method arity included") applied to a parameter
    * type, and the reason is the same: a ported class OVERRIDES this. simple-graphs' `Path` declares
    * `removeIf(Predicate<? super V>)`, and scala requires an override's parameter type to match its
    * parent's EXACTLY — contravariance does not help. Declared `A => Boolean` here, no rendering of
    * the java declaration can agree with it: `Function1[? >: V, Boolean]` is a different type, and
    * `Function1[V, Boolean]` would be a different DECLARATION from the one java wrote.
    *
    * The alternative — map `java.util.function.Predicate` to `Function1` and adapt at each call —
    * also changes the parameter type of every ported override, so it moves the disagreement rather
    * than removing it. Note that this is the one place a JDK type is deliberately IN a shim
    * signature; it is admissible because `java.util.function` is a functional interface available on
    * the JVM, Scala.js and Scala Native alike, whereas `java.util.Collection` (which this file
    * exists to replace) is not the portability problem — its INHERITANCE shape is. */
  def removeIf(filter: java.util.function.Predicate[? >: A]): Boolean =
    var changed = false
    val it = iterator()
    while it.hasNext() do
      if filter.test(it.next()) then { it.remove(); changed = true }
    changed

  def toArray(): scala.Array[Object] =
    val out = new scala.Array[Object](size())
    val it  = iterator()
    var i   = 0
    while it.hasNext() && i < out.length do { out(i) = it.next().asInstanceOf[Object]; i += 1 }
    out

  /** `Collection.toArray(T[])` — the ARRAY-TAKING twin, which four of simple-graphs' classes override
    * and which was simply absent: `method toArray overrides nothing`, reported only once RefChecks
    * ran.
    *
    * Java's contract to the letter: fill the caller's array when it is long enough (and null the
    * element just past the last, which is how a caller distinguishes the used prefix), otherwise
    * return a NEW array of the same component type. `scala.Array.copyOf` is what supplies the second
    * case without a `ClassTag` — the component type comes from the argument, as java's reflective
    * allocation does. */
  /** `T <: java.lang.Object`, not a bare `T`. Java's implicit type-parameter bound IS `Object`, and
    * the port renders it — `toArray[U <: java.lang.Object]` — so a shim declaring `[T]` (bound `Any`)
    * has a DIFFERENT signature and overrides nothing. Same rule as `contains(Object)` above, one
    * level in: scala's `Any` is not java's `Object`, and RefChecks is the only thing that says so. */
  def toArray[T <: java.lang.Object](a: scala.Array[T]): scala.Array[T] =
    val n   = size()
    val out = if a.length >= n then a else scala.Array.copyOf(a, n)
    val it  = iterator()
    var i   = 0
    while it.hasNext() && i < out.length do { out(i) = it.next().asInstanceOf[T]; i += 1 }
    if out.length > n then out(n) = null.asInstanceOf[T]
    out

object JavaCollection:

  /** Adapt a scala collection to the java-shaped one — the counterpart of
    * [[JavaIterable.from]], for a call site where a `Collection`-typed parameter meets a
    * collection the port itself mapped to scala.
    *
    * Backed by the ORIGINAL buffer rather than a copy, so `add`/`remove` are visible to whoever
    * holds it. `.asScala` on a nested collection COPIES and turns a live view into a detached
    * snapshot — the failure `ENGINE-LIMITS` records — and this is the same hazard from the other
    * side, so it is deliberately not a copy.
    *
    * Its `iterator()` is REMOVAL-CAPABLE, which is not a nicety: `AbstractCollection`'s
    * `removeAll`, `retainAll` and `removeIf` are all implemented as iterate-and-remove, and
    * `JavaIterator.from` hands back the throwing default — so all three threw
    * `UnsupportedOperationException` on a wrapper documented as live. That COMPILED and no count
    * moved (CLAUDE.md §4.4); it was found by calling them, in `JavaCollectionSpec`. And it is the
    * reason `JavaIterator` carries `remove` at all: java's own `ArrayList.iterator()` supports it,
    * so a shim standing in for one must too. The index bookkeeping below is
    * `java.util.ArrayList.Itr`'s, including `IllegalStateException` before the first `next()` and
    * on a second `remove()`. */
  def from[A](xs: scala.collection.mutable.Buffer[A]): JavaCollection[A] = new JavaCollection[A]:
    def iterator(): JavaIterator[A] = new JavaIterator[A]:
      private var cursor = 0
      private var last   = -1
      def hasNext(): Boolean = cursor < xs.size
      def next(): A          = { last = cursor; cursor += 1; xs(last) }
      override def remove(): Unit =
        if last < 0 then throw new IllegalStateException("remove")
        xs.remove(last)
        cursor = last
        last = -1
    def size(): Int                 = xs.size
    override def isEmpty(): Boolean          = xs.isEmpty
    override def contains(o: java.lang.Object): Boolean = xs.contains(o)
    override def add(e: A): Boolean          = { xs += e; true }
    override def remove(o: java.lang.Object): Boolean =
      val i = xs.indexWhere(_ == o)
      if i < 0 then false else { xs.remove(i); true }
    override def clear(): Unit               = xs.clear()

  /** the OTHER direction — a `java.util.Collection` a third party HANDED BACK, at a slot the
    * retyping made this shim.
    *
    * `JavaCollections.fromJava` has no overload for this and deliberately so: at a static slot the
    * only evidence is a declared type, `java.util.Collection` has no `scala.jdk` converter, and
    * building one over a copied `Buffer` would detach both directions — the refusal
    * `CollectionsTransform.liveWrappable` states and counts. Here there is no such doubt, because
    * the OBJECT is in hand: this is a REIFIED occurrence (`(Collection<?>) x`), so the wrapper is a
    * straight delegation to the java collection's own members and nothing is copied. Its
    * `iterator()` is removal-capable through java's own iterator, as [[from]]'s is through the
    * buffer's. */
  def fromJava[A](c: java.util.Collection[A]): JavaCollection[A] = new JavaCollection[A]:
    def iterator(): JavaIterator[A] = new JavaIterator[A]:
      private val it = c.iterator()
      def hasNext(): Boolean      = it.hasNext
      def next(): A               = it.next()
      override def remove(): Unit = it.remove()
    def size(): Int                                     = c.size()
    override def isEmpty(): Boolean                     = c.isEmpty()
    override def contains(o: java.lang.Object): Boolean = c.contains(o)
    override def add(e: A): Boolean                     = c.add(e)
    override def remove(o: java.lang.Object): Boolean   = c.remove(o)
    override def clear(): Unit                          = c.clear()

  /** The same seam for a `Kind.Set` source — `java.util.Set` IS a `java.util.Collection`, so a
    * ported method taking a `Collection` must still accept the port's `mutable.Set`.
    *
    * A DISTINCT NAME and not an overload of [[from]], for the reason spelled out on
    * [[unmodifiableFrom]] one level down: an overload resolves on the STATIC type, and a `Buffer`
    * and a `mutable.Set` are both `scala.collection.Iterable` — so a third `Iterable`-shaped
    * candidate added later would silently start winning calls that used to reach this one. An
    * emitted call that names the wrapper it wants cannot be re-resolved by accident.
    *
    * Live, like [[from]]: `add`/`remove` are visible to whoever else holds the set. Two details are
    * java's `Set`, not java's `Collection`, and both differ from the buffer version above:
    *
    *   - `add` returns whether the set CHANGED — `false` for an element already present, where a
    *     `List` always returns `true`.
    *   - `contains`/`remove` test `o.equals(element)`, the PROBE's `equals`, as
    *     `java.util.AbstractCollection` does.
    *
    * REMOVAL-CAPABLE for the reason [[from]] gives. Over a SNAPSHOT of the set rather than the
    * set's own iterator: removing through a live `mutable.Set` iterator is undefined, and java's
    * own answer to iterating a `HashSet` while mutating it is `ConcurrentModificationException` —
    * so nothing correct depends on the difference, and the snapshot makes iterate-and-remove work
    * where the live iterator would corrupt the traversal. */
  def fromSet[A](xs: scala.collection.mutable.Set[A]): JavaCollection[A] = new JavaCollection[A]:
    def iterator(): JavaIterator[A] = new JavaIterator[A]:
      private val order          = xs.toList.iterator
      private var last: Option[A] = scala.None
      def hasNext(): Boolean = order.hasNext
      def next(): A          = { val e = order.next(); last = Some(e); e }
      override def remove(): Unit = last match
        case Some(e)    => xs -= e; last = scala.None
        case scala.None => throw new IllegalStateException("remove")
    def size(): Int                 = xs.size
    override def isEmpty(): Boolean = xs.isEmpty
    override def contains(o: java.lang.Object): Boolean =
      xs.exists(e => if o == null then e == null else o.equals(e))
    override def add(e: A): Boolean = if xs.contains(e) then false else { xs += e; true }
    override def remove(o: java.lang.Object): Boolean =
      xs.find(e => if o == null then e == null else o.equals(e)) match
        case Some(e) => xs -= e; true
        case scala.None => false
    override def clear(): Unit = xs.clear()

  /** Adapt a scala collection that the port may NOT mutate through — a DISTINCT NAME rather than an
    * overload of [[from]], deliberately.
    *
    * An overload would resolve on the static type, and `mutable.Set` is a `scala.collection.Iterable`
    * — so `Collection<X> c = new HashSet<>(); c.add(x)` would silently pick the read-only wrapper and
    * throw at runtime while compiling perfectly. That is precisely the CLAUDE.md §4.4 defect class:
    * valid scala meaning something else, invisible to every count. A separate name cannot be reached
    * by accident, and the emitted call says which one it is.
    *
    * Read-only is not a narrowing where it is used. `Map.values()` in java is a VIEW that rejects
    * `add` with `UnsupportedOperationException`, and `Collections.unmodifiableCollection` rejects
    * every mutator — so throwing is what java does, not a capability the port lost. */
  def unmodifiableFrom[A](xs: scala.collection.Iterable[A]): JavaCollection[A] = new JavaCollection[A]:
    def iterator(): JavaIterator[A] = JavaIterator.from(xs.iterator)
    def size(): Int                 = xs.size
    override def isEmpty(): Boolean          = xs.isEmpty
    override def contains(o: java.lang.Object): Boolean = xs.iterator.contains(o)
    override def add(e: A): Boolean          = throw new UnsupportedOperationException("add on an unmodifiable collection")
    override def remove(o: java.lang.Object): Boolean = throw new UnsupportedOperationException("remove on an unmodifiable collection")
    override def clear(): Unit               = throw new UnsupportedOperationException("clear on an unmodifiable collection")

  /** `java.util.Collections.unmodifiableCollection`, with java's own signature.
    *
    * The `? <: T` is the whole reason this exists rather than being erased to the identity: java's
    * `<T> Collection<T> unmodifiableCollection(Collection<? extends T>)` is where a
    * `Collection<Connection<V>>` becomes the `Collection<Edge<V>>` a method declares it returns, and
    * [[JavaCollection]] — like java's own `Collection` — is invariant. Drop the call and the widening
    * goes with it. */
  def unmodifiable[T](c: JavaCollection[? <: T]): JavaCollection[T] = new JavaCollection[T]:
    // the WRAPPED collection's iterator may be removal-capable ([[from]] now is), and java's
    // `unmodifiableCollection` returns one whose `remove()` throws — otherwise a caller removes
    // through a view that rejects `remove`, which is the read-only guarantee gone with a green
    // compile. Delegation is not enough here; the removal has to be refused explicitly.
    def iterator(): JavaIterator[T] = new JavaIterator[T]:
      private val u          = c.iterator()
      def hasNext(): Boolean = u.hasNext()
      def next(): T          = u.next()
      override def remove(): Unit = throw new UnsupportedOperationException("remove on an unmodifiable collection")
    def size(): Int                 = c.size()
    override def isEmpty(): Boolean          = c.isEmpty()
    override def contains(o: java.lang.Object): Boolean = c.contains(o)
    override def add(e: T): Boolean          = throw new UnsupportedOperationException("add on an unmodifiable collection")
    override def remove(o: java.lang.Object): Boolean = throw new UnsupportedOperationException("remove on an unmodifiable collection")
    override def clear(): Unit               = throw new UnsupportedOperationException("clear on an unmodifiable collection")

  /** `Stream.filter(Predicate)`, as a function rather than a synthesised lambda.
    *
    * A `java.util.stream` chain does not translate call-for-call — scala's collections carry the
    * operations directly, so `stream()` and `collect(Collectors.toList())` both DISAPPEAR and only
    * the middle survives. What survives still takes a `java.util.function.Predicate` where scala's
    * `filter` wants `A => Boolean`, and doing that in the transform would mean minting a lambda with
    * a fresh parameter symbol. A named function needs neither, and keeps the adaptation in one
    * readable place. */
  def filtered[A](xs: scala.collection.mutable.Buffer[A], p: java.util.function.Predicate[? >: A])
      : scala.collection.mutable.Buffer[A] = xs.filter(p.test(_))

  extension [A](self: JavaCollection[A])
    /** a scala view — `map`, `filter`, `foreach` and the rest. Inherited `foreach` from
      * [[JavaIterable]] already covers `for (x <- xs)`. */
    def asScalaBuffer: scala.collection.mutable.Buffer[A] =
      val b = scala.collection.mutable.ArrayBuffer.empty[A]
      val it = self.iterator()
      while it.hasNext() do b += it.next()
      b
