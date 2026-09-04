package balticporter.runtime

/** `java.util.Stack`, as Scala — a `Buffer` with java's LIFO five on top of it. */
class JavaStack[A] extends scala.collection.mutable.ArrayBuffer[A] {

  /** `java.util.Stack.push(item)` — appends, and RETURNS THE ITEM.
    *
    * `+=` hands back the buffer, so a `push` in expression position would give its caller a
    * collection where java gives it the element. */
  def push(item: A): A = {
    this += item
    item
  }

  /** `java.util.Stack.pop()` — removes and returns the TOP, which is the LAST element.
    *
    * `java.util.EmptyStackException` is java's own, and is named rather than approximated: a
    * `NoSuchElementException` compiles everywhere and silently changes what a
    * `catch (EmptyStackException e)` catches. */
  def pop(): A = {
    if isEmpty then throw new java.util.EmptyStackException
    remove(length - 1)
  }

  /** `java.util.Stack.peek()` — the TOP, without removing it. [[pop]]'s guard, and note this is the
    * OPPOSITE END from `java.util.Deque.peek()`, which the collections phase maps elsewhere. */
  def peek(): A = {
    if isEmpty then throw new java.util.EmptyStackException
    apply(length - 1)
  }

  /** `java.util.Stack.search(o)` — the 1-BASED distance from the TOP, or `-1` when absent. */
  def search(o: Any): Int = {
    val i = lastIndexWhere(e => if o == null then e == null else o.equals(e))
    if i < 0 then -1 else length - i
  }
}
