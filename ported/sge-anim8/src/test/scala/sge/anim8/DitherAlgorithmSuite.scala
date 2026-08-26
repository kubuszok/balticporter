package sge.anim8

/** `Dithered.DitherAlgorithm` — a Java enum nested in an interface, with a constructor that assigns
  * a field.
  *
  * The `legibleName` assertions are the behavioural half of the enum-constructor fix: the emitter
  * used to keep an enum constructor's PARAMETERS and drop its BODY, so `legibleName` stayed null
  * and `toString()` returned null for all 22 constants — with a green compile and no check count
  * moving (CLAUDE.md §3). Nothing but this suite can see that.
  *
  * The reference hand port lifted this enum OUT of `Dithered` into a top-level `sge.anim8
  * .DitherAlgorithm`. This port keeps upstream's nesting, so it is reached as
  * `Dithered.DitherAlgorithm` — a Java enum inside an interface is implicitly static, which is a
  * companion-object member in Scala and needs no restructuring.
  */
class DitherAlgorithmSuite extends munit.FunSuite {

  import Dithered.DitherAlgorithm

  test("all 22 algorithms exist") {
    assertEquals(DitherAlgorithm.values().length, 22)
  }

  test("ALL is the cached values() array and holds every constant") {
    assertEquals(DitherAlgorithm.ALL.length, DitherAlgorithm.values().length)
    DitherAlgorithm.values().foreach(a => assert(DitherAlgorithm.ALL.contains(a), s"ALL is missing $a"))
  }

  test("every constant's legibleName is non-empty — the enum CONSTRUCTOR BODY ran") {
    DitherAlgorithm.values().foreach { a =>
      assert(a.legibleName != null, "legibleName is null — the enum constructor body was dropped")
      assert(a.legibleName.nonEmpty, "legibleName is empty")
    }
  }

  test("toString returns legibleName, for every constant") {
    DitherAlgorithm.values().foreach(a => assertEquals(a.toString, a.legibleName))
  }

  test("the legible names are upstream's, in upstream's order") {
    // Pins the constructor ARGUMENT reaching the right constant, not merely that something ran.
    assertEquals(
      DitherAlgorithm.values().map(_.legibleName).toList,
      List("None", "GradientNoise", "Pattern", "Diffusion", "BlueNoise", "ChaoticNoise", "Scatter",
           "Neue", "Roberts", "Woven", "Dodgy", "Loaf", "Wren", "Overboard", "Burkes", "Oceanic",
           "Seaside", "Gourd", "Blunt", "Banter", "Marten", "Additive"),
    )
    assertEquals(DitherAlgorithm.WREN.legibleName, "Wren")
  }

  test("valueOf resolves a constant by its JAVA name, which is not its legibleName") {
    assertEquals(DitherAlgorithm.valueOf("WREN"), DitherAlgorithm.WREN)
    intercept[java.lang.IllegalArgumentException](DitherAlgorithm.valueOf("Wren"))
  }

}