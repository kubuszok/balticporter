package balticporter.corpus

/** What the try-with-resources lowering DOES — JLS 14.20.3.1, executed.
  *
  * `TryResourceSpec` asserts the SHAPE `TirEmitter.resourceStr` writes. This file runs that shape:
  * each test is the emitted pattern, written out by hand at the arity under test, over resources
  * whose `close()` records the order it was called in. A shape assertion cannot say the semantics
  * are java's, and only running them can — which is CLAUDE.md §3's "prefer running ported tests
  * over any number of further compile fixes" at the smallest scale it applies at.
  *
  * The two files are kept honest by being the same text: change the lowering in the emitter and
  * `TryResourceSpec` fails; change what the lowering MEANS and this file does. No corpus library
  * writes a try-with-resources today (ten upstream trees, zero sites), so these are the only
  * behavioural evidence this translation has.
  */
class TryResourceBehaviourSpec extends munit.FunSuite:

  /** a resource that records its own close, and optionally fails while doing it. */
  final class R(name: String, log: collection.mutable.ListBuffer[String], failOnClose: Boolean = false)
      extends java.lang.AutoCloseable:
    def close(): Unit =
      log += name
      if failOnClose then throw new IllegalStateException(s"close:$name")

  // ---- the lowering, transcribed. One level per resource, exactly as `resourceStr` nests it. ----

  private def one[A](r: R)(body: => A): A =
    var primary$1: java.lang.Throwable = null
    try body
    catch { case thrown$1: java.lang.Throwable => { primary$1 = thrown$1; throw thrown$1 } }
    finally if r != null then {
      if primary$1 != null then { try r.close() catch { case suppressed$1: java.lang.Throwable => primary$1.addSuppressed(suppressed$1) } }
      else r.close()
    }

  private def two[A](a: R, b: R)(body: => A): A = one(a)(one(b)(body))

  test("a resource is closed when the body completes NORMALLY") {
    val log = collection.mutable.ListBuffer.empty[String]
    val v = one(new R("a", log))(42)
    assertEquals(v, 42)
    assertEquals(log.toList, List("a"))
  }

  test("…and when it completes ABRUPTLY, with the body's exception propagating") {
    val log = collection.mutable.ListBuffer.empty[String]
    val e = intercept[RuntimeException](one(new R("a", log))(throw new RuntimeException("body")))
    assertEquals(e.getMessage, "body")
    assertEquals(log.toList, List("a"))
  }

  test("resources close in REVERSE declaration order") {
    val log = collection.mutable.ListBuffer.empty[String]
    two(new R("first", log), new R("second", log))(())
    assertEquals(log.toList, List("second", "first"))
  }

  test("a failing close() while the body already threw is SUPPRESSED, not a replacement") {
    val log = collection.mutable.ListBuffer.empty[String]
    val e = intercept[RuntimeException](one(new R("a", log, failOnClose = true))(throw new RuntimeException("body")))
    assertEquals(e.getMessage, "body")
    assertEquals(e.getSuppressed.toList.map(_.getMessage), List("close:a"))
  }

  test("…and a failing close() after a NORMAL body is the statement's own abrupt completion") {
    val log = collection.mutable.ListBuffer.empty[String]
    val e = intercept[IllegalStateException](one(new R("a", log, failOnClose = true))(1))
    assertEquals(e.getMessage, "close:a")
    assertEquals(e.getSuppressed.toList, Nil)
  }

  test("EVERY close() is attempted — an inner one throwing does not skip the outer") {
    val log = collection.mutable.ListBuffer.empty[String]
    val e = intercept[IllegalStateException](
      two(new R("first", log), new R("second", log, failOnClose = true))(()))
    assertEquals(e.getMessage, "close:second")
    assertEquals(log.toList, List("second", "first"))
  }

  test("…and the inner close()'s exception becomes the OUTER level's primary") {
    val log = collection.mutable.ListBuffer.empty[String]
    val e = intercept[IllegalStateException](
      two(new R("first", log, failOnClose = true), new R("second", log, failOnClose = true))(()))
    assertEquals(e.getMessage, "close:second")
    assertEquals(e.getSuppressed.toList.map(_.getMessage), List("close:first"))
  }

  test("a JUMP out of the body still closes, and the catch-all RE-THROWS it") {
    // `boundary.Break extends RuntimeException` (CLAUDE.md §4.4), so the catch-all sees it — and
    // re-throwing is what makes this arm need no BreakGuard beside it. Java's own semantics say
    // the resource closes on a jump too (JLS 14.20.3.1).
    val log = collection.mutable.ListBuffer.empty[String]
    val v = scala.util.boundary {
      one(new R("a", log)) { scala.util.boundary.break(7) }
    }
    assertEquals(v, 7)
    assertEquals(log.toList, List("a"))
  }

  test("a null resource is not closed and does not NPE — JLS 14.20.3.1's `!= null` guard") {
    val log = collection.mutable.ListBuffer.empty[String]
    assertEquals(one(null.asInstanceOf[R])(3), 3)
    assertEquals(log.toList, Nil)
  }
