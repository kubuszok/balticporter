/*
 * Copyright 2025-2026 Mateusz Kubuszok
 * Licensed under the Apache License, Version 2.0
 *
 * ADAPTED for the port: UniformLocation -> Int (port uses raw Int, no opaque type);
 * WorldUnits -> Float (port uses raw Float); Nullable -> null where needed.
 * The test verifies that the screenWidth setter feeds u_screenWidth the framebuffer
 * pixel width (Gdx.graphics.getWidth()), not the camera viewport width.
 */
package sge
package graphics
package g3d
package particles

import sge.graphics.{ GL20, OrthographicCamera }
import sge.graphics.g3d.{ Renderable, Shader }
import sge.graphics.g3d.shaders.BaseShader
import sge.graphics.glutils.ShaderProgram
import sge.noop.{ NoopGL20, NoopGraphics }
import lowlevel.Nullable

class ParticleShaderScreenWidthRedSuite extends munit.FunSuite {

  private val framebufferWidth  = 800
  private val framebufferHeight = 600
  private val viewportWidth     = 10f
  private val viewportHeight    = 8f

  private def makeSge(): Sge = {
    val graphics = new NoopGraphics(framebufferWidth, framebufferHeight) {
      override def gl20: GL20 = NoopGL20
    }
    SgeTestFixture.testSge(graphics = graphics)
  }

  /** ShaderProgram that reports compiled (so BaseShader.init accepts it) and records the float pushed to setUniformf. */
  final private class RecordingShaderProgram(using Sge) extends ShaderProgram("", "") {
    var captured: Nullable[Float] = Nullable.empty

    override def compiled: Boolean = true

    // port uses Int for uniform locations, not opaque UniformLocation
    override def setUniformf(location: Int, value: Float): Unit =
      captured = Nullable(value)
  }

  /** Minimal BaseShader exposing only what the screenWidth setter needs. */
  final private class RecordingBaseShader(using Sge) extends BaseShader {
    def init():                          Unit    = ()
    def compareTo(other:    Shader):     Int     = 0
    def canRender(instance: Renderable): Boolean = true
  }

  test("screenWidth setter feeds u_screenWidth the framebuffer pixel width, not the camera viewport width") {
    given Sge = makeSge()

    // port uses raw Float for viewport dims, not WorldUnits
    val camera = OrthographicCamera(viewportWidth, viewportHeight)

    val program = new RecordingShaderProgram
    val shader  = new RecordingBaseShader
    val inputID = shader.register(ParticleShader.Inputs.screenWidth, Nullable(ParticleShader.Setters.screenWidth))
    shader.init(program, null.asInstanceOf[Renderable])
    shader.camera = Nullable(camera)

    ParticleShader.Setters.screenWidth.set(
      shader,
      inputID,
      null.asInstanceOf[Renderable],
      null.asInstanceOf[graphics.g3d.Attributes]
    )

    val captured = program.captured.getOrElse(
      fail("screenWidth setter never pushed a value to the uniform")
    )

    // sanity: the two candidate values really differ, so the assertion is meaningful
    assertEquals(summon[Sge].graphics.width.toFloat, framebufferWidth.toFloat)
    assert(
      camera.viewportWidth.toFloat != summon[Sge].graphics.width.toFloat,
      "fixture must make viewport width differ from framebuffer width"
    )

    assertEquals(
      captured,
      framebufferWidth.toFloat,
      s"u_screenWidth must be the framebuffer pixel width (${framebufferWidth.toFloat}), " +
        s"but the setter pushed $captured (camera viewport width is ${camera.viewportWidth.toFloat})"
    )
  }
}
