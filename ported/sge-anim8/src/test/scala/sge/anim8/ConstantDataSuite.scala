package sge.anim8

/** anim8's four large constant blobs, pinned BYTE BY BYTE.
  *
  * This is the port's highest-value assertion and the reason it exists. `ConstantData` is 108 lines
  * of Java holding four ISO-8859-1 string literals — 47,935 and three × 6,390 SOURCE characters,
  * full of control characters, high bytes and escapes — decoded in a `static { }` block with
  * `getBytes(StandardCharsets.ISO_8859_1)`. Every one of those bytes has to survive Spoon, the TIR
  * and the emitter unchanged, and nothing but a running assertion can say that it did: the emitted
  * file compiles whatever the literal contains.
  *
  * It did NOT survive the first time. The emitter escaped five characters and passed the rest
  * through raw, so one unescaped newline ended the literal and every byte after it was parsed as
  * source — 1,334 compile errors from this one file.
  *
  * ==The expected values are from an INDEPENDENT oracle, not from the port==
  * Pinning what the port emits against what the port emits proves nothing. These numbers come from
  * decoding the JAVA literal by Java's own escape rules and applying `getBytes(ISO_8859_1)` — each
  * resulting char IS its byte — with no Baltic Porter code involved.
  *
  * ==And they DISAGREE with the reference hand port (CLAUDE.md §3.5)==
  * sge's `sge-anim8` externalised these blobs into `.bin` classpath resources, and its own
  * `DataEmbeddingRedSuite` pins `ENCODED_SNUGGLY.length == 47006` and `TRI_BLUE_NOISE.length ==
  * 6143`. Those are the UTF-8 encodings of the same characters, not the ISO-8859-1 bytes: 32,768
  * Latin-1 bytes re-encode to exactly 47,006 UTF-8 bytes, and 4,096 to exactly 6,143. Upstream's
  * own javadoc settles it independently — `TRI_BLUE_NOISE` is documented as "a 4096-element byte
  * array as a 64x64 grid", and 64 × 64 = 4096, while `ENCODED_SNUGGLY` is a palette mapping, whose
  * shape everywhere else in this library is `byte[0x8000]` = 32768.
  *
  * So the reference port's data is wrong from its first non-ASCII byte, and its "red" suite pins
  * the wrong values. A skip is not a model and neither is a solved-but-wrong; this port keeps
  * upstream's own embedding, which is also the one that works on Scala.js and Native.
  */
class ConstantDataSuite extends munit.FunSuite {

  test("ENCODED_SNUGGLY is 32768 bytes — a byte[0x8000] palette mapping — with the upstream bytes") {
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

  test("the three noise grids hold the SAME multiset of bytes in a different order") {
    // Upstream states this in prose ("the possible byte values appear with the same frequency as in
    // TRI_BLUE_NOISE and TRI_BLUE_NOISE_C, but in a different order"). It is a property of the DATA
    // rather than of any one index, so it catches a corruption that happened to keep the five bytes
    // pinned above — which per-index assertions alone cannot.
    val a = ConstantData.TRI_BLUE_NOISE.sorted.toList
    val b = ConstantData.TRI_BLUE_NOISE_B.sorted.toList
    val c = ConstantData.TRI_BLUE_NOISE_C.sorted.toList
    assertEquals(b, a)
    assertEquals(c, a)
    assert(ConstantData.TRI_BLUE_NOISE.toList != ConstantData.TRI_BLUE_NOISE_B.toList, "…in a DIFFERENT order")
  }

  test("the derived multiplier tables ran their static block — they are not left at zero") {
    // The `static { }` block that fills these is the second one in the file and it reads the three
    // grids above. A dropped static initializer is one of the four silent defects CLAUDE.md §3
    // names, and an all-zero float array is exactly what it looks like.
    for (m, n) <- List(
        (ConstantData.TRI_BLUE_NOISE_MULTIPLIERS, "TRI_BLUE_NOISE_MULTIPLIERS"),
        (ConstantData.TRI_BLUE_NOISE_MULTIPLIERS_B, "TRI_BLUE_NOISE_MULTIPLIERS_B"),
        (ConstantData.TRI_BLUE_NOISE_MULTIPLIERS_C, "TRI_BLUE_NOISE_MULTIPLIERS_C"),
      )
    do
      assertEquals(m.length, 4096, n)
      assert(m.exists(_ != 0f), s"$n is all zero — its static initializer did not run")
      // upstream: "a median value of about 1.0"; half between 1 and 4.232604 and half the inverses.
      val mean = m.map(_.toDouble).sum / m.length
      assert(mean > 0.8 && mean < 2.0, s"$n mean $mean is not near 1.0")
  }

}