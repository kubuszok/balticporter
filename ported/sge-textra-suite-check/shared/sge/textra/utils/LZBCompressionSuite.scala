// ---------------------------------------------------------------------------------------------
// DIFFERENTIAL SUITE — a copy of the REFERENCE HAND PORT's own MUnit suite
//   ../sge/sge-extension/textra/src/test/scala/sge/textra/utils/LZBCompressionSuite.scala
// run against THIS port's mechanically emitted `sge.textra.*`. It is HAND-WRITTEN Scala and must
// never be counted as a ported test (`CLAUDE.md` §3, and the jbump differential probe's rule);
// `PROGRESS.md` §10.8.15 is the census that says why this file is here and its siblings are not.
//
// Class (a) of that census. NO ASSERTION IS EDITED — an assertion changed is evidence
// destroyed, and a file whose assertions could not survive the mapping is class (c) and was
// left out rather than repaired. The only edits are the mapping rows below, each a NAME or
// SHIM substitution between the hand port's surface and this port's emitted one, and each
// applied to CODE only — a comment is the hand port's own prose.
//
// mapping rows applied here: none — this file compiles unadapted
// ---------------------------------------------------------------------------------------------
package sge
package textra
package utils

class LZBCompressionSuite extends munit.FunSuite {

  test("compress then decompress roundtrip equals original") {
    val original     = "Hello, World! This is a test of LZB compression."
    val compressed   = LZBCompression.compressToBytes(original)
    val decompressed = LZBDecompression.decompressFromBytes(compressed)
    assertEquals(decompressed, original)
  }

  test("empty string roundtrip") {
    val compressed = LZBCompression.compressToBytes("")
    assertEquals(compressed.length, 0)
  }

  test("decompressFromBytes on empty array returns empty string") {
    val result = LZBDecompression.decompressFromBytes(Array.emptyByteArray)
    assertEquals(result, "")
  }

  test("repeated text compresses smaller than original") {
    val original   = "abcabcabcabcabcabcabcabcabcabcabcabcabcabcabcabc"
    val compressed = LZBCompression.compressToBytes(original)
    assert(
      compressed.length < original.length,
      s"compressed size (${compressed.length}) should be less than original (${original.length})"
    )
    val decompressed = LZBDecompression.decompressFromBytes(compressed)
    assertEquals(decompressed, original)
  }

  test("unicode text roundtrip") {
    val original     = "Scala 3 is great! \u00e9\u00e8\u00ea\u00eb \u00fc\u00f6\u00e4"
    val compressed   = LZBCompression.compressToBytes(original)
    val decompressed = LZBDecompression.decompressFromBytes(compressed)
    assertEquals(decompressed, original)
  }

  test("single character string roundtrip") {
    val original     = "a"
    val compressed   = LZBCompression.compressToBytes(original)
    val decompressed = LZBDecompression.decompressFromBytes(compressed)
    assertEquals(decompressed, original)
  }

  test("long text roundtrip") {
    val original     = "The quick brown fox jumps over the lazy dog. " * 50
    val compressed   = LZBCompression.compressToBytes(original)
    val decompressed = LZBDecompression.decompressFromBytes(compressed)
    assertEquals(decompressed, original)
  }

  test("all printable ASCII characters roundtrip") {
    val original     = (32 to 126).map(_.toChar).mkString
    val compressed   = LZBCompression.compressToBytes(original)
    val decompressed = LZBDecompression.decompressFromBytes(compressed)
    assertEquals(decompressed, original)
  }

  test("binary-like content roundtrip") {
    // characters 0-255
    val original     = (0 until 256).map(_.toChar).mkString
    val compressed   = LZBCompression.compressToBytes(original)
    val decompressed = LZBDecompression.decompressFromBytes(compressed)
    assertEquals(decompressed, original)
  }

  test("highly compressible text roundtrip") {
    val original   = "a" * 10000
    val compressed = LZBCompression.compressToBytes(original)
    assert(compressed.length < original.length / 2, "highly repetitive text should compress significantly")
    val decompressed = LZBDecompression.decompressFromBytes(compressed)
    assertEquals(decompressed, original)
  }

  test("decompressFromBytes with offset and length") {
    val original   = "Hello!"
    val compressed = LZBCompression.compressToBytes(original)
    // Wrap in a larger array with padding
    val padded = new Array[Byte](compressed.length + 10)
    System.arraycopy(compressed, 0, padded, 5, compressed.length)
    val decompressed = LZBDecompression.decompressFromBytes(padded, 5, compressed.length)
    assertEquals(decompressed, original)
  }

  test("two-character string roundtrip") {
    val original     = "ab"
    val compressed   = LZBCompression.compressToBytes(original)
    val decompressed = LZBDecompression.decompressFromBytes(compressed)
    assertEquals(decompressed, original)
  }

  test("newlines and whitespace roundtrip") {
    val original     = "line1\nline2\ttab\rcarriage\n\n"
    val compressed   = LZBCompression.compressToBytes(original)
    val decompressed = LZBDecompression.decompressFromBytes(compressed)
    assertEquals(decompressed, original)
  }
}
