package balticporter.runtime

/** `java.util.Iterator`, as Scala — what `scala.collection.Iterator` is missing.
  *
  * Java's `Iterator` has a third method, `remove()`, which removes from the underlying
  * collection the element last returned by `next()`. `scala.collection.Iterator` has no
  * such operation and no way to express one, so a port that maps `java.util.Iterator` to
  * it drops the method — quietly, until a call site fails to compile, and dangerously if
  * the call site is instead "fixed" by dropping the removal.
  *
  * Ported code implementing a Java `Iterator` extends THIS instead. Removal support is
  * therefore preserved exactly: an implementation that defines `remove()` keeps its own
  * behaviour, and one that does not inherits the default the JDK itself specifies for
  * `Iterator.remove` — throw `UnsupportedOperationException`.
  *
  * Portable: no JVM-only API, nothing reflective.
  */
trait JavaIterator[A] extends scala.collection.Iterator[A]:
  /** `java.util.Iterator.remove` — the JDK's own default implementation. */
  def remove(): Unit = throw new UnsupportedOperationException("remove")

object JavaIterator:
  /** Adapt a `scala.collection.Iterator` to the java-shaped one. `remove()` keeps the
    * default above, which is the truth: there is nothing to remove through. */
  def from[A](it: scala.collection.Iterator[A]): JavaIterator[A] = it match
    case ji: JavaIterator[A @unchecked] => ji
    case _ => new JavaIterator[A]:
      def hasNext: Boolean = it.hasNext
      def next(): A = it.next()
