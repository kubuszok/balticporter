package balticporter.runtime

/** A ported JUnit suite's base class.
  *
  * It re-declares JUnit's assertions rather than asking the port to rewrite them, for two
  * reasons. MUnit's `assertEquals` takes `(obtained, expected)` — the REVERSE of JUnit's —
  * so a mechanical rename would silently invert every failure message; and MUnit's is
  * type-constrained (`B <:< A`), which java's `assertEquals(Object, Object)` is not, so
  * calls mixing `int`/`long`/`Object` would stop compiling. Keeping java's shapes here
  * preserves the ported assertions exactly and leaves the call sites untouched.
  *
  * `testCase` is un-curried because MUnit's `test(name)(body)` is two argument lists and
  * the porting engine has no node for a curried application.
  */
abstract class PortedSuite extends munit.FunSuite:
  def testCase(name: String, body: => Unit): Unit = test(name)(body)

/** JUnit's assertions, in JAVA's argument order and with java's loose typing.
  *
  * An OBJECT, not members of the suite: a java `static` helper emits into the companion
  * object, which does not extend the suite, so inherited assertions would be invisible
  * exactly where java put half of them.
  */
object Asserts:
  private def check(cond: Boolean, msg: => String): Unit =
    if !cond then throw new AssertionError(msg)

  def assertEquals(expected: Any, actual: Any): Unit =
    check(expected == actual, s"expected <$expected> but was <$actual>")
  def assertEquals(message: String, expected: Any, actual: Any): Unit =
    check(expected == actual, message)
  def assertEquals(expected: Double, actual: Double, delta: Double): Unit =
    check(math.abs(expected - actual) <= delta, s"expected <$expected> but was <$actual>")
  def assertNotEquals(unexpected: Any, actual: Any): Unit =
    check(unexpected != actual, s"did not expect <$unexpected>")
  def assertTrue(b: Boolean): Unit                  = assert(b)
  def assertTrue(message: String, b: Boolean): Unit = assert(b, message)
  def assertFalse(b: Boolean): Unit                 = assert(!b)
  def assertFalse(message: String, b: Boolean): Unit = assert(!b, message)
  def assertNull(o: Any): Unit                      = assert(o == null, s"expected null, was <$o>")
  def assertNotNull(o: Any): Unit                   = assert(o != null, "expected non-null")
  def assertSame(expected: AnyRef, actual: AnyRef): Unit =
    check(expected eq actual, "expected the same instance")

  def assertArrayEquals(expected: Array[Byte], actual: Array[Byte]): Unit =
    check(expected.sameElements(actual), "arrays differ")
  def assertArrayEquals(expected: Array[Int], actual: Array[Int]): Unit =
    check(expected.sameElements(actual), "arrays differ")
  def assertArrayEquals(expected: Array[Long], actual: Array[Long]): Unit =
    check(expected.sameElements(actual), "arrays differ")
  def assertArrayEquals(expected: Array[Char], actual: Array[Char]): Unit =
    check(expected.sameElements(actual), "arrays differ")
  def assertArrayEquals(expected: Array[Object], actual: Array[Object]): Unit =
    check(expected.sameElements(actual), "arrays differ")
  // JUnit's `fail()` and `fail(String)`; MUnit's `fail` demands a message and a Location.
  // the message-carrying delta forms java also has
  def assertEquals(message: String, expected: Double, actual: Double, delta: Double): Unit =
    check(math.abs(expected - actual) <= delta, message)
  def assertArrayEquals(message: String, expected: Array[Float], actual: Array[Float],
                        delta: Float): Unit =
    check(expected.length == actual.length &&
             expected.indices.forall(i => math.abs(expected(i) - actual(i)) <= delta), message)
  def assertArrayEquals(message: String, expected: Array[Double], actual: Array[Double],
                        delta: Double): Unit =
    check(expected.length == actual.length &&
             expected.indices.forall(i => math.abs(expected(i) - actual(i)) <= delta), message)

  def fail(): Nothing                = throw new AssertionError("failed")
  def fail(message: String): Nothing = throw new AssertionError(message)

  def assertEquals(expected: Long, actual: Long): Unit =
    check(expected == actual, s"expected <$expected> but was <$actual>")
  def assertEquals(expected: Double, actual: Double): Unit =
    check(expected == actual, s"expected <$expected> but was <$actual>")
  def assertArrayEquals(expected: Array[Short], actual: Array[Short]): Unit =
    check(expected.sameElements(actual), "arrays differ")
  def assertArrayEquals(expected: Array[Double], actual: Array[Double], delta: Double): Unit =
    check(expected.length == actual.length &&
             expected.indices.forall(i => math.abs(expected(i) - actual(i)) <= delta),
          "arrays differ")
  def assertArrayEquals(expected: Array[Float], actual: Array[Float], delta: Float): Unit =
    check(expected.length == actual.length &&
             expected.indices.forall(i => math.abs(expected(i) - actual(i)) <= delta),
          "arrays differ")
