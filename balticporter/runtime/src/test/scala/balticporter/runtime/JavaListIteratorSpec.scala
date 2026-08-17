package balticporter.runtime

/** `java.util.AbstractList.ListItr`'s contract, cell by cell, over a `mutable.Buffer`.
  *
  * This shim exists because K23's refusal — *scala's `Iterator` is forward-only and read-only* — is
  * a statement about `scala.collection.Iterator` and not about the RECEIVER. Every assertion below
  * is one of the capabilities that sentence said was unavailable, and the ones that matter most are
  * the WRITE-THROUGH ones: a detached copy passes `next`/`previous` and fails every `set`/`add`/
  * `remove` test here, silently, which is exactly what made the refusal plausible.
  *
  * Read against `java.util.ArrayList().listIterator()`'s own javadoc rather than by intuition —
  * `add` is the one nobody predicts correctly.
  */
class JavaListIteratorSpec extends munit.FunSuite:

  private def buf(xs: String*) = scala.collection.mutable.Buffer(xs*)

  test("forward traversal, with java's indices") {
    val b  = buf("a", "b", "c")
    val it = JavaListIterator.over(b)
    assertEquals(it.nextIndex(), 0)
    assertEquals(it.previousIndex(), -1)
    assert(it.hasNext())
    assert(!it.hasPrevious())
    assertEquals(it.next(), "a")
    assertEquals(it.nextIndex(), 1)
    assertEquals(it.previousIndex(), 0)
    assert(it.hasPrevious())
  }

  test("BIDIRECTIONAL — `previous` returns what `next` just did, and the cursor sits between") {
    val it = JavaListIterator.over(buf("a", "b", "c"))
    assertEquals(it.next(), "a")
    assertEquals(it.next(), "b")
    assertEquals(it.previous(), "b")
    assertEquals(it.previous(), "a")
    assert(!it.hasPrevious())
    assertEquals(it.next(), "a")
  }

  test("a starting index is java's `listIterator(int)`, and `size` is legal") {
    val it = JavaListIterator.over(buf("a", "b", "c"), 3)
    assert(!it.hasNext())
    assertEquals(it.previous(), "c")
    intercept[IndexOutOfBoundsException](JavaListIterator.over(buf("a"), 2))
    intercept[IndexOutOfBoundsException](JavaListIterator.over(buf("a"), -1))
  }

  test("WRITE-THROUGH — `set` replaces the last returned element IN THE CALLER'S BUFFER") {
    val b  = buf("a", "b", "c")
    val it = JavaListIterator.over(b)
    it.next()
    it.set("A")
    assertEquals(b.toList, List("A", "b", "c"))
    // …and after `previous`, `set` acts on the element previous returned, not on the one before it
    it.next()
    it.previous()
    it.set("B")
    assertEquals(b.toList, List("A", "B", "c"))
  }

  test("WRITE-THROUGH — `remove` deletes the last returned element and keeps the cursor") {
    val b  = buf("a", "b", "c")
    val it = JavaListIterator.over(b)
    it.next()
    it.next()
    it.remove() // removes "b", which `next` returned; the cursor moves back with it
    assertEquals(b.toList, List("a", "c"))
    assertEquals(it.next(), "c")
  }

  test("WRITE-THROUGH — `remove` after `previous` leaves the cursor where it is") {
    val b  = buf("a", "b", "c")
    val it = JavaListIterator.over(b, 3)
    assertEquals(it.previous(), "c")
    it.remove()
    assertEquals(b.toList, List("a", "b"))
    assertEquals(it.previous(), "b")
  }

  test("`add` inserts BEFORE the cursor — javadoc's own three consequences") {
    val b  = buf("a", "c")
    val it = JavaListIterator.over(b)
    assertEquals(it.next(), "a")
    it.add("b")
    assertEquals(b.toList, List("a", "b", "c"))
    // (1) nextIndex grew, (2) a following `next` is unaffected, (3) a following `previous` returns
    // the new element.
    assertEquals(it.nextIndex(), 2)
    assertEquals(it.previous(), "b")
    assertEquals(it.next(), "b")
    assertEquals(it.next(), "c")
  }

  test("`set`/`remove` before any `next`, or after an `add`, are java's IllegalStateException") {
    val it = JavaListIterator.over(buf("a", "b"))
    intercept[IllegalStateException](it.set("x"))
    intercept[IllegalStateException](it.remove())
    it.next()
    it.add("z")
    intercept[IllegalStateException](it.set("x"))
    intercept[IllegalStateException](it.remove())
  }

  test("running off either end is java's NoSuchElementException") {
    val it = JavaListIterator.over(buf("a"))
    intercept[NoSuchElementException](it.previous())
    it.next()
    intercept[NoSuchElementException](it.next())
  }

  test("it IS a `JavaIterator` — java's own `ListIterator <: Iterator`, which the mapping preserves") {
    // the relation `collection-closure` was reporting as LOST while `java.util.ListIterator` was
    // unmapped: every `java.util.Iterator`-typed slot in a port must take one of these.
    val it: JavaIterator[String] = JavaListIterator.over(buf("a"))
    assert(it.hasNext())
    assertEquals(it.next(), "a")
  }

  test("the cursor is a VIEW, so a `Wrapping` reader can reach the buffer underneath") {
    val b = buf("a")
    JavaListIterator.over(b) match
      case w: Wrapping => assert(w.wrapped.asInstanceOf[AnyRef] eq b)
      case _           => fail("the cursor must carry the Wrapping marker (ENGINE-LIMITS K19)")
  }
