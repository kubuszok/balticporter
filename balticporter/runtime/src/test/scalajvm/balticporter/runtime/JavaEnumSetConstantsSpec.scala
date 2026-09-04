package balticporter.runtime

/** The three `JavaEnumSet` factories that need the enum's CONSTANTS, and therefore
  * `java.lang.Class.getEnumConstants` — which NEITHER non-JVM backend implements. */
class JavaEnumSetConstantsSpec extends munit.FunSuite:

  test("allOf is every constant IN ORDER — the token is load-bearing here and nowhere else") {
    assertEquals(JavaEnumSet.allOf(classOf[Level]).toList, List(Level.Low, Level.Mid, Level.High))
  }

  test("range is INCLUSIVE at both ends, and refuses an inverted one as java does") {
    assertEquals(JavaEnumSet.range(Level.Low, Level.Mid).toList, List(Level.Low, Level.Mid))
    assertEquals(JavaEnumSet.range(Level.Mid, Level.Mid).toList, List(Level.Mid))
    intercept[IllegalArgumentException](JavaEnumSet.range(Level.High, Level.Low))
  }

  test("complementOf reads the enum off the SET, so an empty one throws — java's own limitation") {
    assertEquals(JavaEnumSet.complementOf(JavaEnumSet.of(Level.Mid)).toList,
                 List(Level.Low, Level.High))
    intercept[IllegalArgumentException](JavaEnumSet.complementOf(new JavaEnumSet[Level]))
  }
