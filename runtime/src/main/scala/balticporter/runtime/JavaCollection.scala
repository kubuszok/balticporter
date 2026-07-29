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
/** The ABSTRACT half is what a java class must supply; everything below it is DERIVED, exactly as
  * `java.util.AbstractCollection` derives it — over `iterator()` and `remove`, nothing else.
  *
  * Carrying the derived half matters as much as the abstract half. A java class that extends
  * `AbstractCollection` inherits `addAll`/`removeAll`/`retainAll`/`containsAll`/`toArray` without
  * writing them, and `Collection`'s java-8 `removeIf` likewise; a shim with only the abstract
  * members leaves every one of those calls unresolved at a site the java author never had to think
  * about. That was 6 of simple-graphs' remaining errors.
  *
  * A class that overrides one simply overrides it, which is what java does too. */
trait JavaCollection[A] extends JavaIterable[A]:
  def size(): Int
  def isEmpty(): Boolean
  def contains(o: Any): Boolean
  def add(e: A): Boolean
  def remove(o: Any): Boolean
  def clear(): Unit

  // ---- derived, per java.util.AbstractCollection ----

  def containsAll(c: JavaCollection[?]): Boolean =
    val it = c.iterator()
    var ok = true
    while ok && it.hasNext() do ok = contains(it.next())
    ok

  def addAll(c: JavaCollection[? <: A]): Boolean =
    val it = c.iterator()
    var changed = false
    while it.hasNext() do if add(it.next()) then changed = true
    changed

  def removeAll(c: JavaCollection[?]): Boolean =
    var changed = false
    val it = c.iterator()
    while it.hasNext() do if remove(it.next()) then changed = true
    changed

  /** java removes THROUGH the iterator here, so the receiver is modified in place and the
    * traversal stays valid — a copy-then-clear would lose an alias the caller may hold. */
  def retainAll(c: JavaCollection[?]): Boolean =
    var changed = false
    val it = iterator()
    while it.hasNext() do
      if !c.contains(it.next()) then { it.remove(); changed = true }
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

object JavaCollection:

  /** Adapt a scala collection to the java-shaped one — the counterpart of
    * [[JavaIterable.from]], for a call site where a `Collection`-typed parameter meets a
    * collection the port itself mapped to scala.
    *
    * Backed by the ORIGINAL buffer rather than a copy, so `add`/`remove` are visible to whoever
    * holds it. `.asScala` on a nested collection COPIES and turns a live view into a detached
    * snapshot — the failure `ENGINE-LIMITS` records — and this is the same hazard from the other
    * side, so it is deliberately not a copy. */
  def from[A](xs: scala.collection.mutable.Buffer[A]): JavaCollection[A] = new JavaCollection[A]:
    def iterator(): JavaIterator[A] = JavaIterator.from(xs.iterator)
    def size(): Int                 = xs.size
    def isEmpty(): Boolean          = xs.isEmpty
    def contains(o: Any): Boolean   = xs.contains(o)
    def add(e: A): Boolean          = { xs += e; true }
    def remove(o: Any): Boolean     =
      val i = xs.indexWhere(_ == o)
      if i < 0 then false else { xs.remove(i); true }
    def clear(): Unit               = xs.clear()

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
    def isEmpty(): Boolean          = xs.isEmpty
    def contains(o: Any): Boolean   = xs.iterator.contains(o)
    def add(e: A): Boolean          = throw new UnsupportedOperationException("add on an unmodifiable collection")
    def remove(o: Any): Boolean     = throw new UnsupportedOperationException("remove on an unmodifiable collection")
    def clear(): Unit               = throw new UnsupportedOperationException("clear on an unmodifiable collection")

  /** `java.util.Collections.unmodifiableCollection`, with java's own signature.
    *
    * The `? <: T` is the whole reason this exists rather than being erased to the identity: java's
    * `<T> Collection<T> unmodifiableCollection(Collection<? extends T>)` is where a
    * `Collection<Connection<V>>` becomes the `Collection<Edge<V>>` a method declares it returns, and
    * [[JavaCollection]] — like java's own `Collection` — is invariant. Drop the call and the widening
    * goes with it. */
  def unmodifiable[T](c: JavaCollection[? <: T]): JavaCollection[T] = new JavaCollection[T]:
    def iterator(): JavaIterator[T] = c.iterator().asInstanceOf[JavaIterator[T]]
    def size(): Int                 = c.size()
    def isEmpty(): Boolean          = c.isEmpty()
    def contains(o: Any): Boolean   = c.contains(o)
    def add(e: T): Boolean          = throw new UnsupportedOperationException("add on an unmodifiable collection")
    def remove(o: Any): Boolean     = throw new UnsupportedOperationException("remove on an unmodifiable collection")
    def clear(): Unit               = throw new UnsupportedOperationException("clear on an unmodifiable collection")

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
