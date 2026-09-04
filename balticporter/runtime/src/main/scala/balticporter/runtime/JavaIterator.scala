package balticporter.runtime

/** `java.util.Iterator`, as Scala — java's interface, not scala's. */
trait JavaIterator[A] {
  def hasNext(): Boolean
  def next(): A
  /** `java.util.Iterator.remove` — the JDK's own default implementation. */
  def remove(): Unit = throw new UnsupportedOperationException("remove")
}

object JavaIterator {
  /** Adapt a `scala.collection.Iterator` to the java-shaped one. `remove()` keeps the
    * default above, which is the truth: there is nothing to remove through. */
  def from[A](it: scala.collection.Iterator[A]): JavaIterator[A] = it match {
    case ji: JavaIterator[A @unchecked] => ji
    case _ => new JavaIterator[A] with Wrapping {
      def wrapped: Any = it
      def hasNext(): Boolean = it.hasNext
      def next(): A = it.next()
    }
  }

  /** A removing iterator over an indexed mutable collection. */
  def removing[A](size: () => Int, get: Int => A, removeAt: Int => Unit): JavaIterator[A] = {
    new JavaIterator[A] {
      private var cursor: Int = 0
      private var lastReturned: Int = -1 // -1 means "next() not yet called" or "already removed"

      def hasNext(): Boolean = cursor < size()

      def next(): A = {
        val i = cursor
        if (i >= size()) {
          throw new java.util.NoSuchElementException()
        }
        lastReturned = i
        cursor = i + 1
        get(i)
      }

      override def remove(): Unit = {
        if (lastReturned < 0) {
          throw new IllegalStateException("next() has not been called or remove() already called")
        }
        removeAt(lastReturned)
        // Step the cursor back: the element at `cursor` slid down into `lastReturned`'s slot,
        // so the next call to `next()` should read from `lastReturned`, not `cursor`.
        cursor = lastReturned
        lastReturned = -1
      }
    }
  }

  /** A removing iterator for a `scala.collection.mutable.Buffer` (which includes `ArrayDeque`).
    *
    * Delegates `size`, `apply` and `remove` to the buffer's own methods.
    * Portable: `mutable.Buffer` is in the Scala stdlib on all platforms. */
  def removingFromBuffer[A](buf: scala.collection.mutable.Buffer[A]): JavaIterator[A] = {
    removing[A](() => buf.size, i => buf(i), i => { buf.remove(i); () })
  }

  extension [A](self: JavaIterator[A]) {
    /** A scala view of this java iterator. `remove()` is not expressible there and is
      * simply not offered — the view is for traversal. */
    def asScala: scala.collection.Iterator[A] = new scala.collection.Iterator[A] {
      def hasNext: Boolean = self.hasNext()
      def next(): A = self.next()
    }
    /** Deliberately NO `foreach` here, though the loop is trivial: a class that is both a
      * java `Iterable` and a java `Iterator` — which is most of libGDX's — would then have
      * two applicable extensions and scala reports the `for` as ambiguous rather than
      * picking one. `foreach` lives on [[JavaIterable]], which is what java's own for-each
      * requires anyway; traverse an iterator with `asScala`. */
  }
}
