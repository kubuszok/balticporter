package balticporter.runtime

/** `JavaStack`'s BEHAVIOUR — java's `Stack`, whose top is its LAST element.
  *
  * Every assertion here but the last would pass against `scala.collection.mutable.Stack` as well,
  * which is exactly why the last one is the point: that type's `push` PREPENDS, so its element 0 is
  * the top and every list-shaped read of the same object answers in the opposite order, with no
  * compile error and nothing to count. The mapping choice rests on that one test.
  */
class JavaStackSpec extends munit.FunSuite:

  test("push appends and returns THE ITEM, not the collection") {
    // `+=` hands back the buffer, so a `push` in expression position would give its caller a
    // collection where java gives it the element.
    val s = new JavaStack[String]
    s.push("a")
    assertEquals(s.push("b"), "b")
    assertEquals(s.toList, List("a", "b"))
  }

  test("pop takes the LAST element — java's top — and returns it") {
    val s = new JavaStack[String]
    s.push("a"); s.push("b"); s.push("c")
    assertEquals(s.pop(), "c")
    assertEquals(s.toList, List("a", "b"))
  }

  test("peek reads the LAST element and leaves it there") {
    val s = new JavaStack[String]
    s.push("a"); s.push("b")
    assertEquals(s.peek(), "b")
    assertEquals(s.toList, List("a", "b"))
  }

  test("pop and peek on an empty stack throw JAVA's exception, not an index error") {
    // `java.util.EmptyStackException` is what a ported `catch` names; a `NoSuchElementException`
    // would compile everywhere and silently stop being caught.
    val s = new JavaStack[String]
    intercept[java.util.EmptyStackException](s.pop())
    intercept[java.util.EmptyStackException](s.peek())
  }

  test("search counts 1-BASED FROM THE TOP, and -1 when absent") {
    val s = new JavaStack[String]
    s.push("a"); s.push("b"); s.push("c")
    assertEquals(s.search("c"), 1) // the top
    assertEquals(s.search("b"), 2)
    assertEquals(s.search("a"), 3) // the bottom
    assertEquals(s.search("z"), -1)
  }

  test("…the LAST occurrence, as java's own `size() - lastIndexOf(o)` does") {
    val s = new JavaStack[String]
    s.push("a"); s.push("b"); s.push("a")
    assertEquals(s.search("a"), 1)
  }

  test("search asks the PROBE's equals, and has a null arm") {
    val s = new JavaStack[Any]
    s.push("a"); s.push(null)
    assertEquals(s.search(null), 1) // the null IS the top
    assertEquals(s.search("a"), 2)
  }

  test("it IS a Buffer, so every List member java's Stack inherits still works") {
    // `Stack extends Vector extends List`, and the port maps `java.util.List` to `Buffer`: a value
    // of this type has to be assignable at every slot java allowed, and indexable as java indexed.
    val s = new JavaStack[String]
    s.push("a"); s.push("b")
    val asBuffer: scala.collection.mutable.Buffer[String] = s
    assertEquals(asBuffer(0), "a")
    assertEquals(s.size, 2)
    assertEquals(s.indexOf("b"), 1)
  }

  test("THE REASON FOR THE TYPE: pushing leaves the stack in java's own list order") {
    // `scala.collection.mutable.Stack` answers `List("c", "b", "a")` here — and a ported `for`,
    // `get(i)`, `indexOf` or `toString` over the same object would be reversed, silently.
    val s = new JavaStack[String]
    s.push("a"); s.push("b"); s.push("c")
    assertEquals(s.toList, List("a", "b", "c"))
    assertEquals(s.head, "a")   // java: get(0) is the BOTTOM
    assertEquals(s.peek(), "c") // …and the top is the LAST
    val scalas = scala.collection.mutable.Stack.empty[String]
    scalas.push("a"); scalas.push("b"); scalas.push("c")
    assertEquals(scalas.toList, List("c", "b", "a"), "the stdlib type's order, recorded rather than assumed")
  }
