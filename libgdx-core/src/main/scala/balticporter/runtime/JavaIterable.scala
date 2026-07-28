package balticporter.runtime

/** `java.lang.Iterable`, as Scala — `scala.collection.Iterable` whose `iterator` is the
  * removal-capable [[JavaIterator]] that java's `Iterable.iterator()` is declared to
  * return. Iteration is unaffected (it IS a `scala.collection.Iterable`, so `for (x <- xs)`,
  * `map`, `foreach` all work); what it adds back is the guarantee java gives, that the
  * iterator you get can remove from the collection you got it from.
  */
trait JavaIterable[A] extends scala.collection.Iterable[A]:
  def iterator: JavaIterator[A]

object JavaIterable:
  /** Adapt a plain scala collection to the java-shaped one. Inserted by the engine at call
    * sites where a shim-typed parameter meets a collection the port ITSELF mapped to scala
    * (`CharArray.appendAll(list)`). `remove()` stays at [[JavaIterator]]'s default —
    * `UnsupportedOperationException` — because a scala iterator genuinely cannot remove,
    * which is what java reports for a non-removable iterator too. */
  def from[A](xs: scala.collection.Iterable[A]): JavaIterable[A] = new JavaIterable[A]:
    def iterator: JavaIterator[A] = new JavaIterator[A]:
      private val underlying = xs.iterator
      def hasNext: Boolean = underlying.hasNext
      def next(): A = underlying.next()
