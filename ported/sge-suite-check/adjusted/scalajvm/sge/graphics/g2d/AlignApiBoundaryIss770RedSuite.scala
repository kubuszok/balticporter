/*
 * Copyright 2025-2026 Mateusz Kubuszok
 * Licensed under the Apache License, Version 2.0
 *
 * ADAPTED for the port: the port preserves Java's raw `int halign` at API
 * boundaries (no opaque Align type). Both Align constants and raw Ints
 * compile at halign parameters. Test 1 verifies Align.left works; test 2
 * documents that raw Ints also compile (expected in the port, since halign
 * is Int).
 */
package sge
package graphics
package g2d

import scala.compiletime.testing.*

import lowlevel.Nullable
import lowlevel.util.DynamicArray

class AlignApiBoundaryIss770RedSuite extends munit.FunSuite {

  private given Sge = SgeTestFixture.testSge()

  private def makeFont(): BitmapFont = {
    val data = new BitmapFontData()
    data.capHeight = 10f
    data.down = -12f
    data.spaceXadvance = 10f
    val regions = DynamicArray[TextureRegion]()
    regions.add(new TextureRegion())
    new BitmapFont(data, Nullable(regions), true)
  }

  private val font:   BitmapFont  = makeFont()
  private val layout: GlyphLayout = new GlyphLayout()

  test("ISS-770: an Align value must be accepted at GlyphLayout.setText's halign boundary") {
    // In the port, halign is Int and Align.left is Int, so this compiles directly.
    val errors: List[Error] = typeCheckErrors(
      """layout.setText(font, "ab", sge.graphics.Color.WHITE, 50f, sge.utils.Align.left, false)"""
    )
    assert(
      errors.isEmpty,
      s"an Align value must be accepted at the halign boundary; errors: ${errors.map(_.message)}"
    )
  }

  test("ISS-770: in the port, a raw Int also compiles at halign (halign is Int, not opaque Align)") {
    // In the port, halign is raw Int (matching Java's original int halign),
    // so a bare Int like 999 compiles. This documents the port's behavior.
    val errors: List[Error] = typeCheckErrors(
      """layout.setText(font, "ab", sge.graphics.Color.WHITE, 50f, 999, false)"""
    )
    assert(
      errors.isEmpty,
      s"in the port, raw Int should compile at halign (halign is Int); errors: ${errors.map(_.message)}"
    )
  }

  // zinc-visible dependency anchor
  def zincAnchor: Unit = layout.setText(font, "ab", sge.graphics.Color.WHITE, 50f, sge.utils.Align.left, false)
}
