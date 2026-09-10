/*
 * Derived from guacamole v0.3.6 — https://github.com/crykn/guacamole
 * Original file: de/damios/guacamole/gdx/graphics/GLUtils.java
 * Copyright 2020 damios; licensed under the Apache License, Version 2.0
 *
 * The hand-written half of the libgdx-screenmanager port — see `package.scala`.
 */
package sge.screen.guacamole

import java.nio.{ByteBuffer, ByteOrder, IntBuffer}

/** Reads back the two pieces of GL state a nested framebuffer has to restore.
  *
  * GL20 is obtained through a stored reference (`setGL20`) rather than `(using Sge)` because the
  * emitted caller `ScreenFboUtils.retrieveFboStatus()` is not threaded and cannot supply a context.
  */
object GLUtils {

  private var _gl20: sge.graphics.GL20 = scala.compiletime.uninitialized

  def setGL20(gl: sge.graphics.GL20): Unit = _gl20 = gl
  def setGL20(using ctx: _root_.sge.Sge): Unit = _gl20 = ctx.graphics.gl20

  private def gl20: sge.graphics.GL20 = {
    val g = _gl20
    if (g == null) throw new IllegalStateException("GLUtils.setGL20 not called — call it from your application's create()")
    g
  }

  /** 16 int elements is the largest reply `glGetIntegerv` can produce. */
  private val IntBuf: IntBuffer =
    ByteBuffer.allocateDirect(16 * java.lang.Integer.BYTES).order(ByteOrder.nativeOrder()).asIntBuffer()

  /** the name of the currently bound framebuffer (`GL_FRAMEBUFFER_BINDING`); `0` is the default
    * framebuffer. */
  def getBoundFboHandle(): Int = synchronized {
    gl20.glGetIntegerv(sge.graphics.GL20.GL_FRAMEBUFFER_BINDING, IntBuf)
    IntBuf.get(0)
  }

  /** the current `GL_VIEWPORT` as `[x, y, width, height]`. */
  def getViewport(): Array[Int] = synchronized {
    gl20.glGetIntegerv(sge.graphics.GL20.GL_VIEWPORT, IntBuf)
    Array(IntBuf.get(0), IntBuf.get(1), IntBuf.get(2), IntBuf.get(3))
  }
}
