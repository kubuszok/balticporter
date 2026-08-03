package balticporter.runtime

/** `java.util.Iterator`, as Scala — java's interface, not scala's.
  *
  * Java's `Iterator` has a third method, `remove()`, which removes from the underlying
  * collection the element last returned by `next()`. `scala.collection.Iterator` has no
  * such operation and no way to express one, so a port that maps java's onto scala's
  * drops the method — quietly, until a call site fails to compile, and dangerously if the
  * call site is instead "fixed" by dropping the removal. libGDX calls it POLYMORPHICALLY,
  * through the interface (`ModelLoader`, `ParticleControllerInfluencer`, `ArraySelection`,
  * `Predicate`), so no call-site narrowing brings it back.
  *
  * Declared standalone rather than as a `scala.collection.Iterator` subtype — see
  * [[JavaIterable]] for why that subtyping is not available at all. The methods carry
  * java's arity (`hasNext()`, `next()`), which is also the arity every ported override
  * was written with.
  *
  * Portable: no JVM-only API, nothing reflective.
  */
trait JavaIterator[A]:
  def hasNext(): Boolean
  def next(): A
  /** `java.util.Iterator.remove` — the JDK's own default implementation. */
  def remove(): Unit = throw new UnsupportedOperationException("remove")

object JavaIterator:
  /** Adapt a `scala.collection.Iterator` to the java-shaped one. `remove()` keeps the
    * default above, which is the truth: there is nothing to remove through. */
  def from[A](it: scala.collection.Iterator[A]): JavaIterator[A] = it match
    case ji: JavaIterator[A @unchecked] => ji
    case _ => new JavaIterator[A] with Wrapping:
      def wrapped: Any = it
      def hasNext(): Boolean = it.hasNext
      def next(): A = it.next()

  extension [A](self: JavaIterator[A])
    /** A scala view of this java iterator. `remove()` is not expressible there and is
      * simply not offered — the view is for traversal. */
    def asScala: scala.collection.Iterator[A] = new scala.collection.Iterator[A]:
      def hasNext: Boolean = self.hasNext()
      def next(): A = self.next()
    /** Deliberately NO `foreach` here, though the loop is trivial: a class that is both a
      * java `Iterable` and a java `Iterator` — which is most of libGDX's — would then have
      * two applicable extensions and scala reports the `for` as ambiguous rather than
      * picking one. `foreach` lives on [[JavaIterable]], which is what java's own for-each
      * requires anyway; traverse an iterator with `asScala`. */
