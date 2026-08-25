package balticporter.runtime

/** The three `JavaEnumSet` factories that need the enum's CONSTANTS, and therefore
  * `java.lang.Class.getEnumConstants` — which NEITHER non-JVM backend implements.
  *
  * This is the one place in this module where the constraint stated in `package.scala` genuinely
  * cannot be met: `allOf` is defined as *every constant of this enum*, the only portable handle a
  * java call site hands over is the class token, and off the JVM there is nothing to ask. Scala
  * Native additionally has `Enum.getDeclaringClass` (which `range` and `complementOf` also use) and
  * Scala.js does not, so the two rows fail at different depths and for the same reason.
  *
  * Why the shim is not made to THROW off-JVM instead: the varying half would have to live in a
  * platform source directory, and `build.sbt` VENDORS `src/main/scala` verbatim into the engine's
  * resources as one file per type — a `RuntimeMode.Vendored` port would then be handed a
  * `JavaEnumSet` whose helper is not in the drop. That is a change to the vendoring contract, not
  * to this file, and it is recorded in `PROGRESS.md` §13 rather than made here.
  *
  * The other nine `JavaEnumSet`/`JavaEnumMap` behaviours — the ordinal-order guarantee these types
  * exist for, and both halves of java's null rule — are in `JavaEnumCollectionsSpec` and run on all
  * three platforms.
  */
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
