package sge.vfx.effects

import sge.vfx.effects.util.{GammaThresholdEffect, MixEffect}

/** gdx-vfx's six enums, which are the port's cheapest access to two defects that move NO count.
  *
  *   - **`Enum.ordinal()`** (ENGINE-LIMITS T10's neighbour). `CrtEffect` passes
  *     `lineStyle.ordinal()` straight into a shader `#define`, beside a comment saying the ordinals
  *     match the shader's own constants — so an ordinal that is off by one renders the wrong scan
  *     line and nothing anywhere says so.
  *   - **an enum CONSTRUCTOR's BODY** (T10 itself). `BlurType(Tap tap)` assigns a field of enum
  *     type; the lowering that kept the parameters and dropped the constructor shipped six libGDX
  *     sides with `up == null` and 22 anim8 constants with `toString() == null`, in ports that
  *     compiled with zero errors. `BlurType.tap.radius` is the same shape two levels deep.
  *
  * Nothing here touches GL: an enum constant's initialisation allocates only the constant.
  */
class VfxEnumSuite extends munit.FunSuite {

  test("ordinal() is the DECLARATION INDEX, and CrtEffect's are what its shader is written against") {
    assertEquals(CrtEffect.LineStyle.CROSSLINE_HARD.ordinal(), 0)
    assertEquals(CrtEffect.LineStyle.VERTICAL_HARD.ordinal(), 1)
    assertEquals(CrtEffect.LineStyle.HORIZONTAL_HARD.ordinal(), 2)
    assertEquals(CrtEffect.LineStyle.VERTICAL_SMOOTH.ordinal(), 3)
    assertEquals(CrtEffect.LineStyle.HORIZONTAL_SMOOTH.ordinal(), 4)
  }

  test("ordinal() agrees with the position in values(), for every enum in the library") {
    def check[A](vs: scala.Array[A], ordinalOf: A => Int): Unit = {
      vs.zipWithIndex.foreach((v, i) => assertEquals(ordinalOf(v), i))
    }
    check(CrtEffect.LineStyle.values, (x: CrtEffect.LineStyle) => x.ordinal())
    check(CrtEffect.SizeSource.values, (x: CrtEffect.SizeSource) => x.ordinal())
    check(GaussianBlurEffect.BlurType.values, (x: GaussianBlurEffect.BlurType) => x.ordinal())
    check(MixEffect.Method.values, (x: MixEffect.Method) => x.ordinal())
    check(GammaThresholdEffect.Type.values, (x: GammaThresholdEffect.Type) => x.ordinal())
  }

  test("name() is the java CONSTANT's name, which is what the shader defines are keyed on") {
    // `MixEffect` and `GammaThresholdEffect` both build `"#define METHOD " + method.name()`, so a
    // `name()` that returned anything but the identifier would compile a shader that does not exist.
    assertEquals(MixEffect.Method.MAX.name(), "MAX")
    assertEquals(MixEffect.Method.MIX.name(), "MIX")
    assertEquals(GammaThresholdEffect.Type.RGBA.name(), "RGBA")
    assertEquals(GammaThresholdEffect.Type.ALPHA_PREMULTIPLIED.name(), "ALPHA_PREMULTIPLIED")
  }

  test("valueOf resolves by name and THROWS on an unknown one, as java's does") {
    assertEquals(MixEffect.Method.valueOf("MIX"), MixEffect.Method.MIX)
    intercept[java.lang.IllegalArgumentException](MixEffect.Method.valueOf("mix"))
    intercept[java.lang.IllegalArgumentException](MixEffect.Method.valueOf("NOPE"))
  }

  test("values() lists every constant once, in declaration order") {
    assertEquals(GammaThresholdEffect.Type.values.toList,
      List(GammaThresholdEffect.Type.RGBA, GammaThresholdEffect.Type.RGB,
           GammaThresholdEffect.Type.ALPHA_PREMULTIPLIED))
    assertEquals(CrtEffect.SizeSource.values.toList,
      List(CrtEffect.SizeSource.VIEWPORT, CrtEffect.SizeSource.SCREEN))
  }

  test("an enum CONSTRUCTOR's body ran: BlurType carries its Tap, and Tap carries its radius") {
    // T10's exact shape, two levels: `BlurType(Tap tap) { this.tap = tap; }` and
    // `Tap(int radius) { this.radius = radius; }`. Dropped, both fields stay at their defaults —
    // `tap` null and `radius` 0 — the port compiles, every check count holds, and
    // `GaussianBlurEffect.setType` then builds a convolve filter of radius 0.
    // …asserted THROUGH the public `tap` field, because upstream writes `private enum Tap` and this
    // port now says so. Naming `GaussianBlurEffect.Tap.Tap3x3` here used to compile, and only
    // because the sealed lowering emitted an unqualified `object Tap` beside a
    // `private[GaussianBlurEffect]` class — a private java type whose constants the port published.
    // The scala 3 `enum` (`ENGINE-LIMITS.md` T21) gives the companion the enum's own visibility, so
    // java's privacy is reproduced and this suite reaches the same two fields the library does.
    assertEquals(GaussianBlurEffect.BlurType.Gaussian3x3.tap, GaussianBlurEffect.BlurType.Gaussian3x3b.tap)
    assertEquals(GaussianBlurEffect.BlurType.Gaussian5x5.tap, GaussianBlurEffect.BlurType.Gaussian5x5b.tap)
    assertNotEquals[Any, Any](GaussianBlurEffect.BlurType.Gaussian3x3.tap, GaussianBlurEffect.BlurType.Gaussian5x5.tap)
    // the composition every caller actually reads — `setType` builds a convolve filter of this
    // radius, and T10's defect leaves both of these 0.
    assertEquals(GaussianBlurEffect.BlurType.Gaussian3x3.tap.radius, 1)
    assertEquals(GaussianBlurEffect.BlurType.Gaussian5x5.tap.radius, 2)
  }

  test("distinct constants are distinct values, and a constant equals only itself") {
    assert(CrtEffect.LineStyle.VERTICAL_HARD ne CrtEffect.LineStyle.VERTICAL_SMOOTH)
    assertEquals(CrtEffect.LineStyle.VERTICAL_HARD, CrtEffect.LineStyle.VERTICAL_HARD)
    assertNotEquals[Any, Any](CrtEffect.LineStyle.VERTICAL_HARD, CrtEffect.LineStyle.VERTICAL_SMOOTH)
  }

}