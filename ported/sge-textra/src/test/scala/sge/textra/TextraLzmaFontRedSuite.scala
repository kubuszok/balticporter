// ---------------------------------------------------------------------------------------------
// DIFFERENTIAL SUITE — a copy of the REFERENCE HAND PORT's own MUnit suite
//   ../sge/sge-extension/textra/src/test/scalajvm/sge/textra/TextraLzmaFontRedSuite.scala
// run against THIS port's mechanically emitted `sge.textra.*`. It is HAND-WRITTEN Scala and must
// never be counted as a ported test (`CLAUDE.md` §3, and the jbump differential probe's rule);
// `PROGRESS.md` §10.8.17 is the census that says why this file is here and its siblings are not.
//
// NO ASSERTION IS EDITED — an assertion changed is evidence destroyed. The only edits are the
// mapping rows below, each a NAME or SHIM substitution between the hand port's surface and this
// port's emitted one, applied to CODE only, and each applied per RECEIVER rather than per spelling
// (§10.8.15's T6 correction).
//
// mapping rows applied here: T1, T2, T6, T7, and one this file adds —
//   T9  `LzmaUtils`, `FileType` -> `sge.textra.utils.LzmaUtils`, `sge.Files.FileType`: the hand
//       port hoisted both to the enclosing package; this port emits them where java declared them
//       (`textra/utils/LzmaUtils.java`, and `FileType` NESTED inside `Files`). `PROGRESS.md` §10.8.4
//       is the manifest key that decided the first of those and named this very file as one of the
//       three that would have to spell it — a fully-qualified name here, since this port emits no
//       imports at all (`CLAUDE.md` §6).
//
// The reference file's THREE inline stubs — `StubApplication`, `StubNet`, `MapFiles` — are gone,
// replaced by `HeadlessSge` (a REWRITE, `PROGRESS.md` §10.8.13 correction 3), and the `NoopGraphics`
// / `NoopAudio` / `NoopInput` services it passed are ABSENT here. That is not a weakening: this
// suite touches none of them, and an absent service fails at the exact field the moment a test
// reaches one, while a noop answers (`sge.SgeTestFixture`'s argument, `CLAUDE.md` §3).
//
// The reference tree also carries a BYTE-IDENTICAL `scalanative` copy of this file. It is not
// copied twice: this lane compiles ONE tree, where two suites of the same FQN cannot coexist, and
// the five tests would be the same five run twice.
// ---------------------------------------------------------------------------------------------
package sge
package textra

import java.nio.charset.StandardCharsets

class TextraLzmaFontRedSuite extends munit.FunSuite {

  /** Minimal Structured JSON font (msdf-atlas-gen/fontwriter shape) accepted by Font.loadJSON and BitmapFontSupport.JsonFontData:
    *   - "atlas" with "size" 32 (drives all metric scaling; "type" standard avoids distance-field shaders),
    *   - a space glyph (finalizeJsonFont requires ' ' or 'l'),
    *   - 'A' (unicode 65) with advance 0.5 -> xAdvance 0.5 * 32 = 16,
    *   - U+2588 FULL BLOCK (9608) so finalizeJsonFont skips creating a texture-backed solid block in headless mode.
    */
  private val FixtureJson: String =
    """{
      |  "atlas": { "type": "standard", "size": 32, "width": 256, "height": 256 },
      |  "metrics": { "emSize": 1, "lineHeight": 1.25, "ascender": 0.75, "descender": -0.25 },
      |  "glyphs": [
      |    { "unicode": 32, "advance": 0.25 },
      |    { "unicode": 65, "advance": 0.5,
      |      "planeBounds": { "left": 0.05, "bottom": 0.0, "right": 0.45, "top": 0.7 },
      |      "atlasBounds": { "left": 1.0, "bottom": 1.0, "right": 13.0, "top": 23.0 } },
      |    { "unicode": 9608, "advance": 0.5 }
      |  ],
      |  "kerning": []
      |}""".stripMargin

  private val fixtureJsonBytes: Array[Byte] = FixtureJson.getBytes(StandardCharsets.UTF_8)

  /** The headless context, and the in-memory file table `Font.getJsonExtension` probes. */
  private val fs = new HeadlessSge.InMemoryFiles
  private given sge.Sge = fs.context

  /** The fixture compressed into the standalone .lzma container by the module's own (currently caller-less) LzmaUtils codec. */
  private lazy val fixtureLzmaBytes: Array[Byte] = {
    val compressed = new HeadlessSge.CapturingFileHandle("LzmaFixture.json.lzma")
    sge.textra.utils.LzmaUtils.compress(new HeadlessSge.BytesFileHandle("LzmaFixture.json", fixtureJsonBytes), compressed)
    compressed.bytes
  }

  /** Runs body in upstream's headless mode (no Texture objects), restoring the global afterwards. */
  private def headless[A](body: => A): A = {
    val previous = Font.canUseTextures
    Font.canUseTextures = false
    try body
    finally Font.canUseTextures = previous
  }

  private def assertFixtureFontLoaded(font: Font): Unit = {
    assertEquals(font.name, "LzmaFixture")
    assert(font.mapping.containsKey('A'.toInt), "glyph 'A' (unicode 65) from the fixture must be mapped")
    // advance 0.5 * atlas size 32 + widthAdjust 0 = 16
    assertEqualsFloat(font.mapping.get('A'.toInt).xAdvance, 16f, 0.001f, "glyph 'A' must keep its JSON metrics (advance 0.5 * size 32)")
  }

  test("ISS-514: Font.loadJSON loads a .json.lzma FileHandle (upstream Font.java lines 3218-3236)") {
    headless {
      val handle = new HeadlessSge.BytesFileHandle("LzmaFixture.json.lzma", fixtureLzmaBytes)
      // Upstream decompresses via Lzma.decompress and parses the result as UTF-8 JSON.
      // The port currently feeds the raw LZMA bytes to the JSON reader and crashes.
      val font = new Font(handle, new sge.graphics.g2d.TextureRegion(), 0f, 0f, 0f, 0f, false, true)
      assertFixtureFontLoaded(font)
    }
  }

  test("ISS-514: getJsonExtension prefers .json.lzma over .json AND the resolved file must then load (upstream order)") {
    headless {
      fs.put("LzmaFixture.json.lzma", new HeadlessSge.BytesFileHandle("LzmaFixture.json.lzma", fixtureLzmaBytes))
      fs.put("LzmaFixture.json", new HeadlessSge.BytesFileHandle("LzmaFixture.json", fixtureJsonBytes))
      val resolved = Font.getJsonExtension("LzmaFixture")
      // Upstream Font.getJsonExtension (Font.java lines 142-152) tries .ubj.lzma, .json.lzma, .ubj, .dat, .json in order.
      assertEquals(resolved, "LzmaFixture.json.lzma", "getJsonExtension must keep upstream's probe order: .json.lzma wins over .json")
      // ... and what it resolves to must actually be loadable, otherwise the preference order is a crash generator.
      val font = new Font(resolved, new sge.graphics.g2d.TextureRegion(), 0f, 0f, 0f, 0f, false, true)
      assertFixtureFontLoaded(font)
    }
  }

  test("ISS-514: BitmapFontSupport.JsonFontData loads a .json.lzma file (upstream BitmapFontSupport.java lines 150-168)") {
    // Currently throws RuntimeException("LZMA-compressed font loading is not yet supported: ...")
    // behind a stale "not yet ported" comment, while LzmaUtils sits unused in the same package.
    val data  = new BitmapFontSupport.JsonFontData(new HeadlessSge.BytesFileHandle("LzmaFixture.json.lzma", fixtureLzmaBytes))
    val glyph = data.getGlyph('A')
    assert(glyph != null, "glyph 'A' from the LZMA-compressed fixture must be present")
    assertEquals(if (glyph == null) -1 else glyph.xadvance, 16, "glyph 'A' xadvance must be round(advance 0.5 * size 32)")
  }

  test("ISS-514 control (green at red commit): the same fixture loads as plain .json") {
    headless {
      val handle = new HeadlessSge.BytesFileHandle("LzmaFixture.json", fixtureJsonBytes)
      val font   = new Font(handle, new sge.graphics.g2d.TextureRegion(), 0f, 0f, 0f, 0f, false, true)
      assertFixtureFontLoaded(font)
    }
  }

  test("ISS-514 control (green at red commit): LzmaUtils compress/decompress round-trips the fixture") {
    // Proves the dormant codec works, so wiring it into loadJSON / JsonFontData is pure plumbing.
    val decompressed = new HeadlessSge.CapturingFileHandle("LzmaFixture.roundtrip.json")
    sge.textra.utils.LzmaUtils.decompress(new HeadlessSge.BytesFileHandle("LzmaFixture.json.lzma", fixtureLzmaBytes), decompressed)
    assertEquals(new String(decompressed.bytes, StandardCharsets.UTF_8), FixtureJson)
  }
}
