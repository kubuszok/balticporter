package sge.anim8

import java.io.{ByteArrayOutputStream, DataOutputStream}
import java.util.zip.CRC32

/** `ChunkBuffer` — a PNG chunk framer, and the port's only class that EXTENDS a JDK type.
  *
  * Two things only a run can see:
  *
  *   - the two-constructor funnel. Java's public `ChunkBuffer(int)` delegates to a private
  *     `ChunkBuffer(ByteArrayOutputStream, CRC32)` whose `super(new CheckedOutputStream(buffer,
  *     crc))` is the whole point — CLAUDE.md §4.4 records that a dropped `super(args)` compiles
  *     perfectly and loses everything the parent was given. If it were lost here, nothing would
  *     ever reach the CRC.
  *   - the CRC value itself, checked against a `java.util.zip.CRC32` computed here. Upstream says
  *     this class is "copied straight out of libGDX, in the PixmapIO class", so the bytes it
  *     produces are a wire format and an approximately-right answer is a corrupt PNG.
  *
  * This class is `java.util.zip`-based and therefore JVM-only, which the port's `portability`
  * check reports as a residue rather than a gap (5 sites). The reference hand port made the same
  * call — its own `ChunkBufferSuite` lives in `src/test/scalajvm`.
  */
class ChunkBufferSuite extends munit.FunSuite:

  /** the chunk `endChunk` frames: a big-endian length, then the payload, then a big-endian CRC. */
  private def frame(write: ChunkBuffer => Unit): Array[Byte] =
    val chunk = new ChunkBuffer(64)
    write(chunk)
    val out    = new ByteArrayOutputStream()
    val target = new DataOutputStream(out)
    chunk.endChunk(target)
    target.flush()
    out.toByteArray

  private def be(b: Array[Byte], at: Int): Int =
    ((b(at) & 0xff) << 24) | ((b(at + 1) & 0xff) << 16) | ((b(at + 2) & 0xff) << 8) | (b(at + 3) & 0xff)

  test("endChunk frames length, type, payload and CRC — and the SUPER call reached the CheckedOutputStream") {
    val bytes = frame { c => c.writeInt(0x49484452); c.writeInt(100); c.writeInt(200) } // "IHDR", w, h

    // 4 length + 4 type + 8 payload + 4 crc
    assertEquals(bytes.length, 20)
    // the length field counts the payload only — endChunk writes `buffer.size() - 4`
    assertEquals(be(bytes, 0), 8)
    assertEquals(be(bytes, 4), 0x49484452)
    assertEquals(be(bytes, 8), 100)
    assertEquals(be(bytes, 12), 200)

    // the CRC is over the type AND the payload, which is only true if every write went through the
    // CheckedOutputStream the private constructor handed to `super`.
    val crc = new CRC32()
    crc.update(bytes, 4, 12)
    assertEquals(be(bytes, 16), crc.getValue.toInt, "CRC32 mismatch — the checked stream saw the wrong bytes")
  }

  test("the CRC is not a constant — a different payload gives a different CRC") {
    val a = frame { c => c.writeInt(0x49444154); c.writeByte(0x42) }
    val b = frame { c => c.writeInt(0x49444154); c.writeByte(0x43) }
    assertEquals(a.length, b.length)
    assert(be(a, a.length - 4) != be(b, b.length - 4), "the CRC did not change with the payload")
  }

  test("endChunk RESETS the buffer and the CRC, so the instance is reusable") {
    val chunk = new ChunkBuffer(64)
    chunk.writeInt(0x49444154)
    chunk.writeInt(1)
    val out1 = new ByteArrayOutputStream()
    val t1   = new DataOutputStream(out1)
    chunk.endChunk(t1); t1.flush()

    chunk.writeInt(0x49454e44) // "IEND", no payload
    val out2 = new ByteArrayOutputStream()
    val t2   = new DataOutputStream(out2)
    chunk.endChunk(t2); t2.flush()

    val second = out2.toByteArray
    assertEquals(out1.size(), 16)
    assertEquals(second.length, 12, "the second chunk must not carry the first chunk's bytes")
    assertEquals(be(second, 0), 0, "a payload-free chunk has length 0 — the buffer was reset")

    // …and the CRC was reset too: it must be the CRC of "IEND" alone.
    val crc = new CRC32()
    crc.update(second, 4, 4)
    assertEquals(be(second, 8), crc.getValue.toInt, "the CRC32 was not reset between chunks")
  }
