package sge.vfx.utils

/** `CommonUtils` is the port's pure-arithmetic surface, and the reason it is worth a suite is that
  * three of its members translate to valid Scala meaning something else if a rule slips:
  *
  *   - `compare(T, T, boolean)` opens with `if (c1 == c2)` on REFERENCES (CLAUDE.md §4.4);
  *   - `getAlignFactorX/Y` reads `Align`'s `static final int` constants through a STATIC IMPORT,
  *     which is the `inline val` path — a constant read that triggers a class initialiser instead
  *     of being inlined is the libGDX `Vector3`/`Matrix4` cycle one file along;
  *   - `parseHexColor` is a `switch` on a length whose `default` THROWS, and every arm returns the
  *     SAME shared `tmpColor` instance.
  *
  * The reference hand port does not carry this class, so nothing here is checked against it.
  */
class CommonUtilsSuite extends munit.FunSuite:

  test("compare(int, int) is Integer.compare's three-way answer") {
    assertEquals(CommonUtils.compare(1, 2), -1)
    assertEquals(CommonUtils.compare(2, 2), 0)
    assertEquals(CommonUtils.compare(3, 2), 1)
  }

  test("compare(float, float) likewise, including across zero") {
    assertEquals(CommonUtils.compare(-1.5f, 0f), -1)
    assertEquals(CommonUtils.compare(0f, 0f), 0)
    assertEquals(CommonUtils.compare(2.5f, 1.5f), 1)
  }

  test("compare(boolean, boolean) treats true as the greater") {
    assertEquals(CommonUtils.compare(false, false), 0)
    assertEquals(CommonUtils.compare(true, true), 0)
    assertEquals(CommonUtils.compare(true, false), 1)
    assertEquals(CommonUtils.compare(false, true), -1)
  }

  test("compare(T, T, nullGreater) short-circuits on REFERENCE identity, then orders nulls") {
    // the `c1 == c2` opening is java's reference test. Two EQUAL but distinct strings must fall
    // through to `c1.compareTo(c2)` — which agrees here, so the observable difference is the null
    // ordering below rather than this line; both are pinned because only the pair distinguishes a
    // faithful `eq` from an `equals`.
    assertEquals(CommonUtils.compare("a", "a", true), 0)
    assertEquals(CommonUtils.compare[java.lang.String](null, null, true), 0)
    assertEquals(CommonUtils.compare[java.lang.String](null, "a", true), 1)
    assertEquals(CommonUtils.compare[java.lang.String](null, "a", false), -1)
    assertEquals(CommonUtils.compare[java.lang.String]("a", null, true), -1)
    assertEquals(CommonUtils.compare[java.lang.String]("a", null, false), 1)
    assert(CommonUtils.compare("a", "b", true) < 0)
  }

  test("stringEquals is null-safe in BOTH directions") {
    assert(CommonUtils.stringEquals(null, null))
    assert(CommonUtils.stringEquals("a", new String("a".toCharArray)))
    assert(!CommonUtils.stringEquals(null, "a"))
    assert(!CommonUtils.stringEquals("a", null))
    assert(!CommonUtils.stringEquals("a", "b"))
  }

  test("getAlignFactorX reads Align's static constants — left 0, right 1, centre 0.5") {
    assertEquals(CommonUtils.getAlignFactorX(sge.utils.Align.left), 0f)
    assertEquals(CommonUtils.getAlignFactorX(sge.utils.Align.right), 1f)
    assertEquals(CommonUtils.getAlignFactorX(sge.utils.Align.center), 0.5f)
    // a composite alignment still resolves on the horizontal bit
    assertEquals(CommonUtils.getAlignFactorX(sge.utils.Align.topLeft), 0f)
  }

  test("getAlignFactorY — bottom 0, top 1, centre 0.5") {
    assertEquals(CommonUtils.getAlignFactorY(sge.utils.Align.bottom), 0f)
    assertEquals(CommonUtils.getAlignFactorY(sge.utils.Align.top), 1f)
    assertEquals(CommonUtils.getAlignFactorY(sge.utils.Align.center), 0.5f)
    assertEquals(CommonUtils.getAlignFactorY(sge.utils.Align.bottomRight), 0f)
  }

  test("parseHexColor dispatches on LENGTH and each arm decodes its own width") {
    val c3 = CommonUtils.parseHexColor("f00")
    assertEqualsFloat(c3.r, 1f, 0.0001f)
    assertEqualsFloat(c3.g, 0f, 0.0001f)
    assertEqualsFloat(c3.a, 1f, 0.0001f)

    val c6 = CommonUtils.parseHexColor("00ff00")
    assertEqualsFloat(c6.r, 0f, 0.0001f)
    assertEqualsFloat(c6.g, 1f, 0.0001f)
    assertEqualsFloat(c6.a, 1f, 0.0001f)

    val c8 = CommonUtils.parseHexColor("0000ff80")
    assertEqualsFloat(c8.b, 1f, 0.0001f)
    assertEqualsFloat(c8.a, 128f / 255f, 0.0001f)

    val c4 = CommonUtils.parseHexColor("00f8")
    assertEqualsFloat(c4.b, 1f, 0.0001f)
    assertEqualsFloat(c4.a, 8f / 15f, 0.0001f)
  }

  test("parseHexColor's DEFAULT arm throws — java falls out of a switch, scala must not MatchError") {
    // CLAUDE.md §4.4: a `switch` whose default is the only exit needs its arm emitted, and here the
    // arm is a `throw`. A `MatchError` instead would be a different exception with a different
    // message, and an empty fall-out would return whatever the last arm left behind.
    intercept[java.lang.IllegalArgumentException](CommonUtils.parseHexColor("ff"))
    intercept[java.lang.IllegalArgumentException](CommonUtils.parseHexColor("fffff"))
  }

  test("each parse hands back the SAME shared scratch colour — upstream's aliasing, kept") {
    val a = CommonUtils.parseHexColor("f00")
    val b = CommonUtils.parseHexColor("00f")
    assert(a eq b)
    assertEqualsFloat(a.b, 1f, 0.0001f) // the second parse overwrote the first
  }

  test("the per-width parsers reject a wrong-length string outright") {
    intercept[java.lang.IllegalArgumentException](CommonUtils.parseHexColor3("ffff"))
    intercept[java.lang.IllegalArgumentException](CommonUtils.parseHexColor4("fff"))
    intercept[java.lang.IllegalArgumentException](CommonUtils.parseHexColor6("fff"))
    intercept[java.lang.IllegalArgumentException](CommonUtils.parseHexColor8("ffffff"))
  }

  test("random(array) stays inside the array") {
    val xs = scala.Array[java.lang.String]("a", "b", "c")
    (1 to 50).foreach(_ => assert(xs.contains(CommonUtils.random(xs))))
  }
