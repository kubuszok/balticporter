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
  * The `IntBuffer` is allocated ONCE and shared, exactly as upstream does, and for the same
  * reason: `glGetIntegerv` writes into a direct buffer, and allocating one per call is a
  * per-frame allocation on the render path. Upstream marks both methods `synchronized` because of
  * that sharing; that is reproduced rather than dropped — this is not decoration on a JVM where
  * a second thread reading GL state would otherwise see the first one's values.
  */
object GLUtils:

  /** 16 int elements is the largest reply `glGetIntegerv` can produce. */
  private val IntBuf: IntBuffer =
    ByteBuffer.allocateDirect(16 * java.lang.Integer.BYTES).order(ByteOrder.nativeOrder()).asIntBuffer()

  /** the name of the currently bound framebuffer (`GL_FRAMEBUFFER_BINDING`); `0` is the default
    * framebuffer. */
  def getBoundFboHandle(): Int = synchronized {
    sge.Gdx.gl.glGetIntegerv(sge.graphics.GL20.GL_FRAMEBUFFER_BINDING, IntBuf)
    IntBuf.get(0)
  }

  /** the current `GL_VIEWPORT` as `[x, y, width, height]`. */
  def getViewport(): Array[Int] = synchronized {
    sge.Gdx.gl.glGetIntegerv(sge.graphics.GL20.GL_VIEWPORT, IntBuf)
    Array(IntBuf.get(0), IntBuf.get(1), IntBuf.get(2), IntBuf.get(3))
  }
