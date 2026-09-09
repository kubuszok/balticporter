/*
 * Copyright 2025-2026 Mateusz Kubuszok
 * Licensed under the Apache License, Version 2.0
 *
 * ADAPTED for the port: the port auto-ports the Java no-arg and boolean-flip
 * BitmapFont constructors, so typeCheckErrors tests pass (green). The classpath
 * resource test uses the original Java resource path (com/badlogic/gdx/utils/).
 * The default font resources are not shipped by the port, so that test is RED.
 */
package sge
package graphics
package g2d

import scala.compiletime.testing.*

class BitmapFontDefaultFontIss765RedSuite extends munit.FunSuite {

  private given Sge = SgeTestFixture.testSge()

  test("ISS-765: no-arg `new BitmapFont()` must construct the default 15pt Liberation Sans font (BitmapFont.java:71-80)") {
    val errors: List[Error] = typeCheckErrors("new BitmapFont()")
    assert(
      errors.isEmpty,
      s"`new BitmapFont()` must compile (default classpath font, BitmapFont.java:71-80); " +
        s"Errors: ${errors.map(_.message)}"
    )
  }

  test("ISS-765: boolean-flip `new BitmapFont(flip)` must construct the default font (BitmapFont.java:82-88)") {
    val errors: List[Error] = typeCheckErrors("new BitmapFont(true)")
    assert(
      errors.isEmpty,
      s"`new BitmapFont(flip)` must compile (default classpath font, BitmapFont.java:82-88); " +
        s"Errors: ${errors.map(_.message)}"
    )
  }

  // The port preserves the original Java classpath resource path.
  private val SGE_DEFAULT_FONT_PATHS: List[String] =
    List("com/badlogic/gdx/utils/lsans-15.fnt", "com/badlogic/gdx/utils/lsans-15.png")

  test("ISS-765: the default Liberation Sans 15pt font (.fnt + .png) must ship on the classpath") {
    val cl      = getClass.getClassLoader
    val missing = SGE_DEFAULT_FONT_PATHS.filter(p => cl.getResource(p) == null)
    assert(
      missing.isEmpty,
      s"the default font resources must ship on the classpath (ISS-765); missing: $missing. " +
        "LibGDX ships com/badlogic/gdx/utils/lsans-15.{fnt,png}; port ships none."
    )
  }

  // zinc-visible dependency anchors — the exact ctors the typeCheckErrors pin
  def zincAnchorNoArg: BitmapFont = new BitmapFont()
  def zincAnchorFlip:  BitmapFont = new BitmapFont(true)
}
