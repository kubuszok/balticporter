package balticporter.runtime

/** `java.util.ListIterator`, as Scala — java's interface, not scala's.
  *
  * This shim exists because a REFUSAL did not survive being re-read. The
  * refusal said *scala's `Iterator` is forward-only and read-only, so every mapping is either a
  * different protocol or a detached copy whose `set` updates nothing* — and every word of that is a
  * statement about `scala.collection.Iterator`, not about the RECEIVER. A `java.util.List` retypes
  * to a `scala.collection.mutable.Buffer`, and a `Buffer` supports indexed READ, indexed UPDATE,
  * INSERT and REMOVE, which is `ListIterator`'s whole contract, cursor and all. "There is nothing to
  * map them onto" is true of a MAPPING and false of a SHIM — the distinction
  * between a target and a parent, read one family over.
  *
  * A trait, so a ported class may IMPLEMENT `java.util.ListIterator` and be emitted onto this;
  * `extends JavaIterator[A]` because java's own `ListIterator<E> extends Iterator<E>` and a mapping
  * MUST PRESERVE THE SOURCE LIBRARY'S OWN SUBTYPE RELATIONS. Left unmapped,
  * that lost relation is what `collection-closure` was already reporting.
  *
  * Java's arity throughout (`hasNext()`, `previous()`, `nextIndex()`), which is also the arity every
  * ported override was written with.
  *
  * `remove()` arrives from [[JavaIterator]] with java's own default (throw), which is right for both
  * interfaces: `ListIterator.remove` is an OPTIONAL operation exactly as `Iterator.remove` is.
  *
  * Portable: no JVM-only API, nothing reflective.
  */
trait JavaListIterator[A] extends JavaIterator[A] {
  def hasPrevious(): Boolean
  def previous(): A
  def nextIndex(): Int
  def previousIndex(): Int
  /** `java.util.ListIterator.set`/`add` — ABSTRACT, as java declares them.
    *
    * They are OPTIONAL operations and they are not DEFAULT methods: java's `Iterator.remove` gained
    * a default body in SE8 (which is why [[JavaIterator.remove]] is concrete here) and these two
    * never did, so an implementor must write them or say `throw` itself. A default body here would
    * be this shim inventing a contract java does not give, and a ported class that forgot the member
    * would compile and throw at run time instead of failing to compile. */
  def set(e: A): Unit
  def add(e: A): Unit
}

object JavaListIterator {

  /** the WRITE-THROUGH cursor over a `Buffer`, which is what makes this a shim rather than a copy.
    *
    * `java.util.AbstractList.ListItr`'s own algorithm, written down: a cursor SITS BETWEEN elements,
    * `next()` returns the one after it and advances, `previous()` returns the one before it and
    * retreats, and `last` remembers which index the two of them returned so that `set` and `remove`
    * can act on it. Every one of them reaches THE SAME BUFFER the caller holds — `set` is
    * `buf(i) = e`, `add` is `buf.insert(i, e)`, `remove` is `buf.remove(i)` — so a port that mutates
    * through the cursor mutates the list, which is the capability java's interface is FOR and the
    * one a detached copy silently loses.
    *
    * ==The deltas against `java.util.AbstractList.ListItr`, enumerated==
    *   - NO `ConcurrentModificationException`. java's iterator carries a `modCount` snapshot and
    *     fails fast when the list is structurally modified behind it; a `mutable.Buffer` publishes
    *     no such counter, so there is nothing to snapshot. The cursor then reads whatever the buffer
    *     now holds, which is the same thing scala's own iterators do. This is the one behavioural
    *     difference and it is stated rather than hidden: java THROWS where this reads on.
    *   - `IndexOutOfBoundsException` rather than `NoSuchElementException` is NOT among them —
    *     `next()`/`previous()` check the bound themselves and throw java's own exception for it.
    *
    * `from` is java's `listIterator(int)` argument: the cursor's initial position, which may be
    * `size` (a cursor past the last element, from which only `previous()` is legal). */
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
