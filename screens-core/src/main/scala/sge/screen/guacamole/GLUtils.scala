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
  *
  * ==Why these two read the RESIDUAL GLOBAL and take no `(using sge.Sge)`==
  * The base port retires `Gdx` into a threaded context, and `Gdx.gl20` is one of the nine statics
  * that lose their last reader — but `Gdx.graphics` is one of the two that KEEP one, so the two-hop
  * read below is exactly what the base itself still emits at the boundaries it counts.
  *
  * A context parameter is not available here, and the reason is structural rather than a
  * preference: the one caller of both methods is `ScreenFboUtils.retrieveFboStatus()`, which is
  * GENERATED (§5.5) and is NOT threaded — the closure sees it call an EXTERNAL guacamole symbol and
  * has no way to know that the shim behind it reaches the global. Adding a clause here would make
  * the emitted caller uncompilable, and no manifest key can add one to it. Compare
  * `NestableFrameBuffer`, whose caller IS an instance method of a threaded class: the difference
  * between the two files is entirely the difference between their callers.
  */
object GLUtils:

  /** 16 int elements is the largest reply `glGetIntegerv` can produce. */
  private val IntBuf: IntBuffer =
    ByteBuffer.allocateDirect(16 * java.lang.Integer.BYTES).order(ByteOrder.nativeOrder()).asIntBuffer()

  /** the name of the currently bound framebuffer (`GL_FRAMEBUFFER_BINDING`); `0` is the default
    * framebuffer. */
  def getBoundFboHandle(): Int = synchronized {
    sge.Gdx.graphics.gl20.glGetIntegerv(sge.graphics.GL20.GL_FRAMEBUFFER_BINDING, IntBuf)
    IntBuf.get(0)
  }

  /** the current `GL_VIEWPORT` as `[x, y, width, height]`. */
  def getViewport(): Array[Int] = synchronized {
    sge.Gdx.graphics.gl20.glGetIntegerv(sge.graphics.GL20.GL_VIEWPORT, IntBuf)
    Array(IntBuf.get(0), IntBuf.get(1), IntBuf.get(2), IntBuf.get(3))
  }
