/*
 * Derived from guacamole v0.3.6 — https://github.com/crykn/guacamole
 * Original file: de/damios/guacamole/gdx/graphics/NestableFrameBuffer.java
 * Copyright 2020 damios; licensed under the Apache License, Version 2.0
 *
 * The hand-written half of the libgdx-screenmanager port — see `package.scala`.
 */
package sge.screen.guacamole

/** A `FrameBuffer` that restores the PREVIOUSLY BOUND buffer on `end()`, so framebuffers nest:
  *
  * {{{
  * fbo0.begin()   // stuff is rendered into fbo0
  * fbo1.begin()   // stuff is rendered into fbo1
  * fbo1.end()     // …and from here into fbo0 again
  * fbo0.end()
  * }}}
  *
  * ==Why this type is the point of the whole guacamole dependency==
  * libGDX's own `FrameBuffer.end()` calls `GLFrameBuffer.unbind()`, which binds framebuffer **0** —
  * the default one — whatever was bound when `begin()` ran. `ScreenManager` renders every screen
  * into a framebuffer of its own, so a screen or a transition that binds an FBO inside that render
  * unbinds to the DEFAULT buffer when it finishes, and the manager's buffer is silently lost for
  * the rest of the frame. Nothing about that is a compile error and nothing about it throws; the
  * frame just comes out wrong. libgdx-screenmanager's `build.gradle` says as much in a comment on
  * the dependency itself — `is exposed because of NestableFrameBuffer`.
  *
  * The reference hand port (`sge-extension/screens`) does not have this type and uses a plain
  * `FrameBuffer` for the manager's two buffers, which is the defect above (PROGRESS.md §1.1). It
  * is the one place this port is expected to be strictly better than the reference, so it is
  * carried rather than dropped.
  *
  * ==What is reproduced and what is not==
  * The two public constructors, `begin`, `bind`, both `end` overloads, `build`, `hasDepth` and
  * `isBound` are upstream's, statement for statement — including the `IllegalStateException` on
  * an out-of-order close, which is the only thing that turns a mis-nested pair into a diagnosable
  * failure rather than a wrong frame. `NestableFrameBufferBuilder` is NOT reproduced: it is
  * upstream's seam for attaching extra buffers, this port calls none of it, and a shim member with
  * no caller is untested code that reads as verified.
  *
  * ==Why the constructor takes `(using sge.Sge)`==
  * The base port retires libGDX's `Gdx` global into a threaded context (DESIGN.md §8.4), and
  * `GLFrameBuffer` is one of the classes it threads — so `sge.graphics.glutils.FrameBuffer`'s
  * constructors now take the clause and a subclass has to be able to pass it on. This shim is
  * hand-written `src/` Scala the frontend never sees, so nothing threads it automatically; the
  * clause is added here by hand. Its one caller, `ScreenManager.createFrameBuffer()`, is an
  * instance method of a threaded class, so the context is already in scope there.
  */
class NestableFrameBuffer(
    format: sge.graphics.Pixmap.Format,
    width: Int,
    height: Int,
    private val depth: Boolean,
    hasStencil: Boolean,
)(using sge.Sge) extends sge.graphics.glutils.FrameBuffer(format, width, height, depth, hasStencil):

  def this(format: sge.graphics.Pixmap.Format, width: Int, height: Int, hasDepth: Boolean)(using sge.Sge) = {
    this(format, width, height, hasDepth, false)

  }
  private var previousFBOHandle: Int      = -1
  private var previousViewport: Array[Int] = new Array[Int](4)
  private var bound: Boolean               = false

  /** Binds the framebuffer and sets the viewport, remembering what was bound before. */
  override def begin(): Unit = {
    Preconditions.checkState(!bound, "end() has to be called before another draw can begin!")
    bound = true

    previousFBOHandle = GLUtils.getBoundFboHandle()
    bind()

    previousViewport = GLUtils.getViewport()
    setFrameBufferViewport()

  /** Upstream deprecates this — it does not support nesting; `begin()` is the entry point. */
  }
  @deprecated("does not support nesting — use begin()", "")
  override def bind(): Unit = {
    scala.Predef.summon[sge.Sge].graphics.gl20.glBindFramebuffer(sge.graphics.GL20.GL_FRAMEBUFFER, this.framebufferHandle)

  /** Rebinds the PREVIOUS framebuffer — not the default one — and restores its viewport. */
  }
  override def `end`(): Unit = {
    `end`(previousViewport(0), previousViewport(1), previousViewport(2), previousViewport(3))

  }
  override def `end`(x: Int, y: Int, w: Int, h: Int): Unit = {
    Preconditions.checkState(bound, "begin() has to be called first!")
    bound = false

    if (GLUtils.getBoundFboHandle() != this.framebufferHandle) {
      throw new IllegalStateException(
        "The currently bound framebuffer (" + GLUtils.getBoundFboHandle() +
          ") doesn't match this one. Make sure the nested framebuffers are closed in the same " +
          "order they were opened in!")

    }
    scala.Predef.summon[sge.Sge].graphics.gl20.glBindFramebuffer(sge.graphics.GL20.GL_FRAMEBUFFER, previousFBOHandle)
    scala.Predef.summon[sge.Sge].graphics.gl20.glViewport(x, y, w, h)

  /** Building an FBO binds it; upstream restores what was bound before, and so does this. */
  }
  override def build(): Unit = {
    val previous = GLUtils.getBoundFboHandle()
    super.build()
    scala.Predef.summon[sge.Sge].graphics.gl20.glBindFramebuffer(sge.graphics.GL20.GL_FRAMEBUFFER, previous)

  /** whether this framebuffer was created with a depth buffer. */
  }
  def hasDepth(): Boolean = depth

  def isBound(): Boolean = bound
