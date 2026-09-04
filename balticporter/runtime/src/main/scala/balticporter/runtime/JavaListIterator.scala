package balticporter.runtime

/** `java.util.ListIterator`, as Scala — java's interface, not scala's. */
trait JavaListIterator[A] extends JavaIterator[A] {
  def hasPrevious(): Boolean
  def previous(): A
  def nextIndex(): Int
  def previousIndex(): Int
  /** `java.util.ListIterator.set`/`add` — ABSTRACT, as java declares them. */
  def set(e: A): Unit
  def add(e: A): Unit
}

object JavaListIterator {

  /** the WRITE-THROUGH cursor over a `Buffer`, which is what makes this a shim rather than a copy. */

  /** java's nilary `listIterator()` — a cursor at the head. */
  def over[A](buf: scala.collection.mutable.Buffer[A]): JavaListIterator[A] = over(buf, 0)

  def over[A](buf: scala.collection.mutable.Buffer[A], from: Int): JavaListIterator[A] = {
    if from < 0 || from > buf.size then
      throw new IndexOutOfBoundsException(s"Index: $from, Size: ${buf.size}")
    new JavaListIterator[A] with Wrapping {
      private var cursor: Int = from
      /** the index `next()`/`previous()` last returned, or -1 when neither may be acted on. */
      private var last: Int = -1

      def wrapped: Any = buf

      def hasNext(): Boolean     = cursor < buf.size
      def hasPrevious(): Boolean = cursor > 0
      def nextIndex(): Int       = cursor
      def previousIndex(): Int   = cursor - 1

      def next(): A = {
        if cursor >= buf.size then throw new NoSuchElementException()
        val e = buf(cursor)
        last = cursor
        cursor += 1
        e
      }

      def previous(): A = {
        if cursor <= 0 then throw new NoSuchElementException()
        cursor -= 1
        last = cursor
        buf(cursor)
      }

      override def remove(): Unit = {
        if last < 0 then throw new IllegalStateException("remove() before next()/previous()")
        buf.remove(last)
        // the cursor sits AFTER the element `next()` returned and BEFORE the one `previous()` did,
        // so only the first case moves it — java's own `if (lastRet < cursor) cursor--`.
        if last < cursor then cursor -= 1
        last = -1
      }

      def set(e: A): Unit = {
        if last < 0 then throw new IllegalStateException("set() before next()/previous()")
        buf(last) = e
      }

      def add(e: A): Unit = {
        // java inserts BEFORE the implicit cursor: `nextIndex()` grows by one, the element a
        // following `next()` returns is unchanged, and a following `previous()` returns what was
        // just added. `last` is cleared, so `set`/`remove` after an `add` is an `IllegalStateException`
        // exactly as java's is.
        buf.insert(cursor, e)
        cursor += 1
        last = -1
      }
    }
  }
}
