// ---------------------------------------------------------------------------------------------
// DIFFERENTIAL SUITE — a copy of the REFERENCE HAND PORT's own MUnit suite
//   ../sge/sge-extension/ai/src/test/scala/sge/ai/utils/CircularBufferSuite.scala
// run against THIS port's mechanically emitted `sge.ai.*`. It is HAND-WRITTEN Scala and must
// never be counted as a ported test (`CLAUDE.md` §3, and the jbump differential probe's rule);
// `PROGRESS.md` §10.7.12 is the census that says why this file is here and its siblings are not.
//
// Class (b) of that census. NO ASSERTION IS EDITED — an assertion changed is evidence
// destroyed, and a file whose assertions could not survive the mapping is class (c) and was
// left out rather than repaired. The only edits are the mapping rows below, each a NAME or
// SHIM substitution between the hand port's surface and this port's emitted one, and each
// applied to CODE only — a comment is the hand port's own prose.
//
// mapping rows applied here: M1, M2, M3
// ---------------------------------------------------------------------------------------------
package sge
package ai
package utils


class CircularBufferSuite extends munit.FunSuite {

  test("store and read basic") {
    val buf = new CircularBuffer[String](4)
    buf.store("a")
    buf.store("b")
    assertEquals(buf.size(), 2)
    assertEquals(buf.read(), "a")
    assertEquals(buf.read(), "b")
    assert(buf.isEmpty(), "buffer should be empty after reading all")
  }

  test("FIFO order") {
    val buf = new CircularBuffer[String](8)
    for (i <- 1 to 5) buf.store(s"item$i")
    for (i <- 1 to 5) assertEquals(buf.read(), s"item$i")
  }

  test("wrap-around: store more than initial capacity with resize") {
    val buf = new CircularBuffer[String](2, resizable$p = true)
    assert(buf.store("a"), "store a")
    assert(buf.store("b"), "store b")
    assert(buf.store("c"), "store c triggers resize")
    assert(buf.store("d"), "store d")
    assertEquals(buf.size(), 4)
    assertEquals(buf.read(), "a")
    assertEquals(buf.read(), "b")
    assertEquals(buf.read(), "c")
    assertEquals(buf.read(), "d")
  }

  test("fixed-size buffer returns false when full") {
    val buf = new CircularBuffer[String](2, resizable$p = false)
    assert(buf.store("a"), "store a")
    assert(buf.store("b"), "store b")
    assert(!buf.store("c"), "store c should fail when full")
    assertEquals(buf.size(), 2)
    assert(buf.isFull(), "buffer should be full")
  }

  test("clear resets") {
    val buf = new CircularBuffer[String](4)
    buf.store("a")
    buf.store("b")
    buf.clear()
    assert(buf.isEmpty(), "buffer should be empty after clear")
    assertEquals(buf.size(), 0)
    assert((buf.read() == null), "read from cleared buffer should be empty")
  }

  test("read from empty returns empty Nullable") {
    val buf = new CircularBuffer[String](4)
    assert((buf.read() == null), "read from empty buffer should be empty")
  }

  test("wrap-around internal state: read some, store more, read all") {
    val buf = new CircularBuffer[String](4, resizable$p = false)
    buf.store("a")
    buf.store("b")
    buf.store("c")
    buf.store("d")
    // Read 2 to advance head
    assertEquals(buf.read(), "a")
    assertEquals(buf.read(), "b")
    // Now store 2 more (wrapping the tail around)
    assert(buf.store("e"), "store e after wrapping")
    assert(buf.store("f"), "store f after wrapping")
    assertEquals(buf.size(), 4)
    assertEquals(buf.read(), "c")
    assertEquals(buf.read(), "d")
    assertEquals(buf.read(), "e")
    assertEquals(buf.read(), "f")
    assert(buf.isEmpty(), "buffer should be empty after reading all")
  }
}
