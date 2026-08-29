package balticporter.runtime

class JavaIterableSpec extends munit.FunSuite {

  test("from wraps a scala Iterable") {
    val ji = JavaIterable.from(List(1, 2, 3))
    assertEquals(ji.iterator().asScala.toList, List(1, 2, 3))
  }

  test("from is re-traversable") {
    val ji = JavaIterable.from(List(1, 2, 3))
    assertEquals(ji.iterator().asScala.toList, List(1, 2, 3))
    assertEquals(ji.iterator().asScala.toList, List(1, 2, 3))
  }

  test("fromIterator wraps an iterator-producing function") {
    val ji = JavaIterable.fromIterator(() => Iterator(10, 20, 30))
    assertEquals(ji.iterator().asScala.toList, List(10, 20, 30))
  }

  test("fromIterator is re-traversable — each call creates a fresh iterator") {
    var callCount = 0
    val ji = JavaIterable.fromIterator { () =>
      callCount += 1
      Iterator(1, 2)
    }
    assertEquals(ji.iterator().asScala.toList, List(1, 2))
    assertEquals(ji.iterator().asScala.toList, List(1, 2))
    assertEquals(callCount, 2)
  }

  test("fromIterator reports its factory as wrapped") {
    val factory: () => scala.collection.Iterator[Int] = () => Iterator.empty
    val ji = JavaIterable.fromIterator(factory)
    assertEquals(ji.asInstanceOf[Wrapping].wrapped, factory)
  }

  test("asScala extension works") {
    val ji = JavaIterable.from(List("a", "b"))
    val si = ji.asScala
    assertEquals(si.toList, List("a", "b"))
  }

  test("foreach extension works") {
    val ji = JavaIterable.from(List(1, 2, 3))
    var sum = 0
    ji.foreach(x => sum += x)
    assertEquals(sum, 6)
  }
}
