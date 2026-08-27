package sge.vfx.framebuffer

import sge.graphics.Pixmap

/** The framebuffer machinery, exercised HEADLESSLY — everything up to the point where a real GL
  * context is needed.
  *
  * That boundary is not a compromise, it is where this library's testable half ends: a
  * `VfxFrameBuffer`'s constructor only allocates matrices, and `initialize(w, h)` is the first line
  * that touches GL. So the ROTATION arithmetic, the guard clauses and the ping-pong swap are all
  * reachable, and they are the parts a translation can silently get wrong — the modulo in
  * `changeToNext`, the `continue` in `rebind` (CLAUDE.md §4.4: dropped, the loop runs on and
  * dereferences the null FBO), and the two `IllegalStateException`s that make `VfxPingPongWrapper`
  * a state machine rather than a pair of fields.
  *
  * The reference hand port covers the same ground with its own headless GL stub
  * (`../sge/sge-extension/vfx/src/test/.../VfxFrameBufferQueueISS561Suite`); this port has no
  * `Sge` context to stub, so it stops at the GL line instead and says so.
  */
class VfxFrameBufferSuite extends munit.FunSuite {

  /** The base port threads libGDX's `Gdx` global as a `using` context (DESIGN.md §8.4), and
    * `VfxFrameBuffer` is one of the classes gdx-vfx inherits that threading into — so constructing
    * one needs a context. This suite is HAND-WRITTEN, so it may simply declare one; the emitted
    * suites of the base module cannot, which is the whole of `ENGINE-LIMITS.md` CT7.
    *
    * Every service is ABSENT rather than a noop (see `sge.SgeTestFixture`), and here that is doing
    * real work rather than being tidy: this suite exists precisely to run everything UP TO the
    * first GL call, and an absent `graphics` is what makes crossing that line a
    * `NullPointerException` at the exact field instead of a stub quietly answering and a test
    * passing while asserting nothing. The doc comment above says the boundary is where the testable
    * half ends; this is what enforces it. */
  private given sge.Sge = sge.SgeTestFixture.testSge()

  private val Format = Pixmap.Format.RGBA8888

  test("the queue rejects a non-positive buffer count") {
    intercept[java.lang.IllegalArgumentException](new VfxFrameBufferQueue(Format, 0))
    intercept[java.lang.IllegalArgumentException](new VfxFrameBufferQueue(Format, -1))
  }

  test("the queue holds exactly fboAmount buffers and starts at the first") {
    val q     = new VfxFrameBufferQueue(Format, 3)
    val first = q.current
    assert(first ne null, "first buffer must be non-null")
    val second = q.changeToNext()
    val third  = q.changeToNext()
    assert(second ne first)
    assert(third ne first)
    assert(third ne second)
  }

  test("changeToNext WRAPS with a modulo — dropping it is IndexOutOfBounds, not a re-scan") {
    val q     = new VfxFrameBufferQueue(Format, 3)
    val first = q.current
    q.changeToNext(); q.changeToNext()
    assert(q.changeToNext() eq first)
  }

  test("a single-buffer queue keeps returning the same buffer") {
    val q = new VfxFrameBufferQueue(Format, 1)
    val b = q.current
    assert(q.changeToNext() eq b)
    assert(q.changeToNext() eq b)
  }

  test("rebind SKIPS uninitialised buffers — the `continue` that has no scala keyword") {
    // Every buffer in a fresh queue has a null FBO, so java's `if (getFbo() == null) continue;`
    // means rebind does nothing at all. Dropped in translation the loop runs on and dereferences
    // the null — which is exactly CLAUDE.md §4.4's `continue` row, and it costs no compile error.
    val q = new VfxFrameBufferQueue(Format, 2)
    q.rebind()
    q.setTextureParams(
      sge.graphics.Texture.TextureWrap.Repeat, sge.graphics.Texture.TextureWrap.Repeat,
      sge.graphics.Texture.TextureFilter.Linear, sge.graphics.Texture.TextureFilter.Linear)
  }

  test("disposing an uninitialised queue is a no-op and empties it") {
    // `VfxFrameBuffer.reset()` returns early when `!initialized`, so no GL call is reached.
    val q = new VfxFrameBufferQueue(Format, 2)
    q.close()
    // …and the queue is now empty, so the modulo in `changeToNext` divides by zero.
    intercept[java.lang.ArithmeticException](q.changeToNext())
  }

  test("a fresh VfxFrameBuffer is neither initialised nor drawing, and keeps its pixel format") {
    val b = new VfxFrameBuffer(Format)
    assert(!b.initialized)
    assert(!b.drawing)
    assertEquals(b.pixelFormat, Format)
    assertEquals(b.fbo, null)
  }

  test("buffer nesting starts at zero and is a STATIC counter shared by every instance") {
    // `private static int bufferNesting` — a `static` field in java, a companion `var` in scala.
    // Reading it through the instance-less accessor is what pins that the collapse kept it static
    // rather than giving every buffer its own.
    assertEquals(VfxFrameBuffer.getBufferNesting(), 0)
  }

  test("the ping-pong wrapper starts uninitialised and not capturing") {
    val w = new VfxPingPongWrapper()
    assert(!w.initialized)
    assert(!w.capturing)
  }

  test("initialize(src, dst) assigns BOTH ends — and the parameter order is src first") {
    // The two-argument constructor calls `initialize(bufSrc, bufDst)` with its own arguments
    // REVERSED relative to its parameter names (`VfxPingPongWrapper(bufDst, bufSrc)` →
    // `initialize(bufSrc, bufDst)`), which is upstream's own quirk. Pinned through `initialize`
    // directly so the expectation is unambiguous.
    val a = new VfxFrameBuffer(Format)
    val b = new VfxFrameBuffer(Format)
    val w = new VfxPingPongWrapper()
    w.initialize(a, b)
    assert(w.initialized)
    assert(w.srcBuffer eq a)
    assert(w.dstBuffer eq b)
  }

  test("swap exchanges src and dst, and is legal OUTSIDE capturing") {
    val a = new VfxFrameBuffer(Format)
    val b = new VfxFrameBuffer(Format)
    val w = new VfxPingPongWrapper()
    w.initialize(a, b)
    w.swap()
    assert(w.srcBuffer eq b)
    assert(w.dstBuffer eq a)
    w.swap()
    assert(w.srcBuffer eq a)
  }

  test("reset clears both ends and leaves the wrapper reusable") {
    val a = new VfxFrameBuffer(Format)
    val b = new VfxFrameBuffer(Format)
    val w = new VfxPingPongWrapper()
    w.initialize(a, b)
    w.reset()
    assert(!w.initialized)
    w.initialize(a, b)
    assert(w.initialized)
  }

  test("re-initialising an INITIALISED wrapper resets it first rather than leaking a buffer") {
    val a = new VfxFrameBuffer(Format)
    val b = new VfxFrameBuffer(Format)
    val c = new VfxFrameBuffer(Format)
    val w = new VfxPingPongWrapper()
    w.initialize(a, b)
    w.initialize(c, a)
    assert(w.srcBuffer eq c)
    assert(w.dstBuffer eq a)
  }

  test("`end()` before `begin()` is an IllegalStateException, not a null dereference") {
    val w = new VfxPingPongWrapper()
    w.initialize(new VfxFrameBuffer(Format), new VfxFrameBuffer(Format))
    intercept[java.lang.IllegalStateException](w.`end`())
  }

}