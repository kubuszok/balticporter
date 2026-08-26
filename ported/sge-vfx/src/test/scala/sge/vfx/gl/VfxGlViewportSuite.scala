package sge.vfx.gl

/** `VfxGlViewport` is four `int` fields and two overloaded `set`s that both return `this` — the
  * smallest thing in the library, and worth pinning for exactly that reason: it is the one class
  * where a wrong answer could only come from the emission itself.
  *
  * The self-returning `set` is what `VfxGLUtils.getViewport()` relies on to hand its shared scratch
  * viewport back to the caller, so "returns THIS instance" is a contract and not a convenience.
  */
class VfxGlViewportSuite extends munit.FunSuite {

  test("a fresh viewport is all zeroes") {
    val v = new VfxGlViewport()
    assertEquals(v.x, 0)
    assertEquals(v.y, 0)
    assertEquals(v.width, 0)
    assertEquals(v.height, 0)
  }

  test("set(x, y, w, h) assigns all four and returns THIS instance, not a copy") {
    val v = new VfxGlViewport()
    val r = v.set(1, 2, 3, 4)
    assert(r eq v)
    assertEquals(v.x, 1)
    assertEquals(v.y, 2)
    assertEquals(v.width, 3)
    assertEquals(v.height, 4)
  }

  test("set(viewport) COPIES the four fields — it does not alias the source") {
    val src = new VfxGlViewport().set(5, 6, 7, 8)
    val dst = new VfxGlViewport()
    assert(dst.set(src) eq dst)
    assertEquals(dst.width, 7)
    src.set(0, 0, 0, 0)
    assertEquals(dst.width, 7)
  }

  test("toString is upstream's exact form") {
    assertEquals(new VfxGlViewport().set(1, 2, 3, 4).toString(),
      "x=1, y=2, width=3, height=4")
  }

}