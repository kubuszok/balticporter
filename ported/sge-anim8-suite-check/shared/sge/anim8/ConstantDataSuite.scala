package sge
package anim8

class ConstantDataSuite extends munit.FunSuite {

  test("ENCODED_SNUGGLY is 32768 bytes with the upstream bytes") {
    val d = ConstantData.ENCODED_SNUGGLY
    assertEquals(d.length, 32768)
    assertEquals(d(0).toInt, 1)
    assertEquals(d(1).toInt, 1)
    assertEquals(d(100).toInt, 2)
    assertEquals(d(1000).toInt, 90)
    assertEquals(d(16384).toInt, -1)
    assertEquals(d(32767).toInt, 20)
  }

  test("TRI_BLUE_NOISE is a 64x64 grid with the upstream bytes") {
    val d = ConstantData.TRI_BLUE_NOISE
    assertEquals(d.length, 4096)
    assertEquals(d(0).toInt, -19)
    assertEquals(d(1).toInt, -8)
    assertEquals(d(100).toInt, 60)
    assertEquals(d(1000).toInt, -43)
    assertEquals(d(4095).toInt, -34)
  }

  test("TRI_BLUE_NOISE_B is a 64x64 grid with the upstream bytes") {
    val d = ConstantData.TRI_BLUE_NOISE_B
    assertEquals(d.length, 4096)
    assertEquals(d(0).toInt, -76)
    assertEquals(d(1).toInt, -38)
    assertEquals(d(100).toInt, -36)
    assertEquals(d(1000).toInt, -62)
    assertEquals(d(4095).toInt, 60)
  }

  test("TRI_BLUE_NOISE_C is a 64x64 grid with the upstream bytes") {
    val d = ConstantData.TRI_BLUE_NOISE_C
    assertEquals(d.length, 4096)
    assertEquals(d(0).toInt, -124)
    assertEquals(d(1).toInt, -23)
    assertEquals(d(100).toInt, -55)
    assertEquals(d(1000).toInt, -27)
    assertEquals(d(4095).toInt, 89)
  }

  test("TRI_BLUE_NOISE_MULTIPLIERS are derived and non-trivial") {
    for ((m, n) <- List(
        (ConstantData.TRI_BLUE_NOISE_MULTIPLIERS, "TRI_BLUE_NOISE_MULTIPLIERS"),
        (ConstantData.TRI_BLUE_NOISE_MULTIPLIERS_B, "TRI_BLUE_NOISE_MULTIPLIERS_B"),
        (ConstantData.TRI_BLUE_NOISE_MULTIPLIERS_C, "TRI_BLUE_NOISE_MULTIPLIERS_C"),
      )) {
      assertEquals(m.length, 4096, n)
      assert(m.exists(_ != 0f), s"$n is all zero")
    }
  }
}
