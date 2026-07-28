package balticporter.runtime

/** JUnit's assertions, in JAVA's argument order and with java's loose typing.
  *
  * An OBJECT, not members of a base class: a java `static` helper emits into the COMPANION
  * object, which does not extend the suite, so inherited assertions are invisible exactly
  * where java put half of them.
  *
  * INTERIM. Re-declaring shapes the engine could emit correctly is not what injected
  * sources are for — they exist for semantics the target language LACKS. MUnit's own
  * `assertEquals(obtained, expected)` differs from java's only by argument order and a
  * `B <:< A` constraint, both of which the transform can resolve because it knows the
  * operand types. See LIBGDX-PORT-STATUS.md.
  */
object Asserts:
  private def check(cond: Boolean, msg: => String): Unit =
    if !cond then throw new AssertionError(msg)

  def fail(): Nothing                = throw new AssertionError("failed")
  def fail(message: String): Nothing = throw new AssertionError(message)

  def assertEquals(expected: Any, actual: Any): Unit =
    check(expected == actual, s"expected <$expected> but was <$actual>")
  def assertEquals(message: String, expected: Any, actual: Any): Unit =
    check(expected == actual, message)
  def assertEquals(expected: Long, actual: Long): Unit =
    check(expected == actual, s"expected <$expected> but was <$actual>")
  def assertEquals(expected: Double, actual: Double): Unit =
    check(expected == actual, s"expected <$expected> but was <$actual>")
  def assertEquals(expected: Double, actual: Double, delta: Double): Unit =
    check(math.abs(expected - actual) <= delta, s"expected <$expected> but was <$actual>")
  def assertEquals(message: String, expected: Double, actual: Double, delta: Double): Unit =
    check(math.abs(expected - actual) <= delta, message)
  def assertNotEquals(unexpected: Any, actual: Any): Unit =
    check(unexpected != actual, s"did not expect <$unexpected>")

  def assertTrue(b: Boolean): Unit                   = check(b, "expected true")
  def assertTrue(message: String, b: Boolean): Unit  = check(b, message)
  def assertFalse(b: Boolean): Unit                  = check(!b, "expected false")
  def assertFalse(message: String, b: Boolean): Unit = check(!b, message)
  def assertNull(o: Any): Unit                       = check(o == null, s"expected null, was <$o>")
  def assertNotNull(o: Any): Unit                    = check(o != null, "expected non-null")
  def assertSame(expected: AnyRef, actual: AnyRef): Unit =
    check(expected eq actual, "expected the same instance")

  def assertArrayEquals(expected: Array[Byte], actual: Array[Byte]): Unit =
    check(expected.sameElements(actual), "arrays differ")
  def assertArrayEquals(expected: Array[Short], actual: Array[Short]): Unit =
    check(expected.sameElements(actual), "arrays differ")
  def assertArrayEquals(expected: Array[Int], actual: Array[Int]): Unit =
    check(expected.sameElements(actual), "arrays differ")
  def assertArrayEquals(expected: Array[Long], actual: Array[Long]): Unit =
    check(expected.sameElements(actual), "arrays differ")
  def assertArrayEquals(expected: Array[Char], actual: Array[Char]): Unit =
    check(expected.sameElements(actual), "arrays differ")
  def assertArrayEquals(expected: Array[Object], actual: Array[Object]): Unit =
    check(expected.sameElements(actual), "arrays differ")
  def assertArrayEquals(expected: Array[Float], actual: Array[Float], delta: Float): Unit =
    check(expected.length == actual.length &&
            expected.indices.forall(i => math.abs(expected(i) - actual(i)) <= delta),
          "arrays differ")
  def assertArrayEquals(message: String, expected: Array[Float], actual: Array[Float],
                        delta: Float): Unit =
    check(expected.length == actual.length &&
            expected.indices.forall(i => math.abs(expected(i) - actual(i)) <= delta), message)
  def assertArrayEquals(expected: Array[Double], actual: Array[Double], delta: Double): Unit =
    check(expected.length == actual.length &&
            expected.indices.forall(i => math.abs(expected(i) - actual(i)) <= delta),
          "arrays differ")
  def assertArrayEquals(message: String, expected: Array[Double], actual: Array[Double],
                        delta: Double): Unit =
    check(expected.length == actual.length &&
            expected.indices.forall(i => math.abs(expected(i) - actual(i)) <= delta), message)
