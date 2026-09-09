package sge
package anim8

class DitherAlgorithmSuite extends munit.FunSuite {

  import Dithered.DitherAlgorithm

  test("all 24 algorithms exist") {
    assertEquals(DitherAlgorithm.values().length, 24)
  }

  test("DitherAlgorithm.ALL contains all values") {
    assertEquals(DitherAlgorithm.ALL.length, DitherAlgorithm.values().length)
    DitherAlgorithm.values().foreach { alg =>
      assert(DitherAlgorithm.ALL.contains(alg), s"ALL missing $alg")
    }
  }

  test("each algorithm has a non-empty legibleName") {
    DitherAlgorithm.values().foreach { alg =>
      assert(alg.legibleName != null, "legibleName is null")
      assert(alg.legibleName.nonEmpty, s"$alg has empty legibleName")
    }
  }

  test("toString returns legibleName") {
    DitherAlgorithm.values().foreach { alg =>
      assertEquals(alg.toString, alg.legibleName)
    }
  }

  test("WREN is the expected dither with the expected name") {
    assertEquals(DitherAlgorithm.WREN.legibleName, "Wren")
  }
}
