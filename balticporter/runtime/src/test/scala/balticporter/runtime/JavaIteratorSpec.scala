package balticporter.runtime

/** The support types' BEHAVIOUR, which nothing checked while they were a string literal in a
  * transform — they were only ever compiled as part of a port, and only the port's own tests could
  * have caught a defect in them. CLAUDE.md §3: a green compile says nothing.
  */
class JavaIteratorSpec extends munit.FunSuite:

  test("remove() defaults to java.util.Iterator's own documented default") {
    val it = JavaIterator.from(Iterator(1, 2, 3))
    val e  = intercept[UnsupportedOperationException](it.remove())
    assertEquals(e.getMessage, "remove")
  }

  test("an implementation that overrides remove() keeps its behaviour") {
    var removed = 0
    val it = new JavaIterator[Int]:
      private val u          = Iterator(1, 2)
      def hasNext(): Boolean = u.hasNext
      def next(): Int        = u.next()
      override def remove(): Unit = removed += 1
    it.next()
    it.remove()
    assertEquals(removed, 1)
  }

  test("from/asScala round-trip preserves the elements") {
    assertEquals(JavaIterator.from(Iterator(1, 2, 3)).asScala.toList, List(1, 2, 3))
  }

  test("a type cannot be BOTH a scala Iterator and a JavaIterator") {
    // Which makes `JavaIterator.from`'s `case ji: JavaIterator[A] => ji` fast path unreachable:
    // its argument is a `scala.collection.Iterator`, and nothing can be both. Pinned rather than
    // removed — the branch is harmless, and the fact is the reason it looks like it should work.
    // It is CLAUDE.md §4.5 again: `hasNext` and `hasNext()` are the SAME member to Scala, and
    // "neither has parameters" is the error you get.
    assert(!compiletime.testing.typeChecks("""
      new scala.collection.Iterator[Int] with balticporter.runtime.JavaIterator[Int]:
        def hasNext: Boolean   = false
        def hasNext(): Boolean = false
        def next(): Int        = 0
    """))
    assertEquals(JavaIterator.from(Iterator(7)).asScala.toList, List(7))
  }

  test("JavaIterable.iterator() is nilary and its iterator REFUSES removal — a scala List backs it") {
    // The name used to claim "removal-capable", the assertion the opposite. This factory wraps an
    // immutable scala collection, so refusing `remove` is the only honest behaviour — unlike
    // `JavaCollection.from`, whose iterator really does remove (see JavaCollectionSpec).
    val xs: JavaIterable[Int] = JavaIterable.from(List(1, 2, 3))
    assertEquals(xs.iterator().asScala.toList, List(1, 2, 3))
    intercept[UnsupportedOperationException](xs.iterator().remove())
  }

  test("JavaIterable carries foreach so a `for` over the java shape works — and JavaIterator does not") {
    // CLAUDE.md §4.5: `foreach` on BOTH made every `for` over an iterable-and-iterator class
    // ambiguous. This asserts the asymmetry is still there.
    val xs: JavaIterable[Int] = JavaIterable.from(List(1, 2, 3))
    var sum = 0
    for x <- xs do sum += x
    assertEquals(sum, 6)
    assertEquals(xs.asScala.toList, List(1, 2, 3))
    assert(!compiletime.testing.typeChecks("""
      val it: balticporter.runtime.JavaIterator[Int] = balticporter.runtime.JavaIterator.from(Iterator(1))
      it.foreach(_ => ())
    """))
  }
