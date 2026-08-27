/*
 * Derived from guacamole v0.3.6 — https://github.com/crykn/guacamole
 * Original files: de/damios/guacamole/gdx/graphics/{ShaderProgramFactory,ShaderCompatibilityHelper}.java
 * Copyright 2020 damios; licensed under the Apache License, Version 2.0
 *
 * The hand-written half of the libgdx-screenmanager port — see `package.scala`.
 */
package sge.screen.guacamole

/** Compiles a `ShaderProgram`, optionally suppressing libGDX's global shader prepends.
  *
  * `ignorePrepend` is the whole reason this exists rather than a bare `new ShaderProgram(…)`.
  * `ShaderProgram.prependVertexCode` / `prependFragmentCode` are GLOBAL mutable strings a libGDX
  * application sets once — typically to a `#version` line — and a transition shader that already
  * carries its own version statement would then get two, which no GLSL compiler accepts. Upstream
  * nulls the pair, builds, and puts them back; that save/restore is reproduced exactly, including
  * putting them back BEFORE the compilation check throws, or a failed shader would leave the
  * application's prepends permanently cleared.
  *
  * ==Why the three `fromString` overloads take `(using sge.Sge)`==
  * `sge.graphics.glutils.ShaderProgram` is one of the classes the base port threads (DESIGN.md
  * §8.4), so constructing one takes the context. This is hand-written `src/` Scala the frontend
  * never sees, so the clause is added by hand. The one caller of the four-argument form is
  * `ShaderTransition`'s own constructor body — a threaded class — so the context is in scope there;
  * `checkCompilation` needs none and does not take one.
  */
object ShaderProgramFactory {

  def fromString(vertexShader: String, fragmentShader: String)(using sge.Sge): sge.graphics.glutils.ShaderProgram = {
    fromString(vertexShader, fragmentShader, true)

  }
  def fromString(vertexShader: String, fragmentShader: String, throwException: Boolean)(using sge.Sge): sge.graphics.glutils.ShaderProgram = {
    fromString(vertexShader, fragmentShader, throwException, false)

  }
  def fromString(
      vertexShader: String,
      fragmentShader: String,
      throwException: Boolean,
      ignorePrepend: Boolean,
  )(using sge.Sge): sge.graphics.glutils.ShaderProgram =
    var prependVertexCode: String   = null
    var prependFragmentCode: String = null
    if (ignorePrepend) {
      prependVertexCode = sge.graphics.glutils.ShaderProgram.prependVertexCode
      sge.graphics.glutils.ShaderProgram.prependVertexCode = null
      prependFragmentCode = sge.graphics.glutils.ShaderProgram.prependFragmentCode
      sge.graphics.glutils.ShaderProgram.prependFragmentCode = null

    }
    val program = new sge.graphics.glutils.ShaderProgram(vertexShader, fragmentShader)

    if (ignorePrepend) {
      sge.graphics.glutils.ShaderProgram.prependVertexCode = prependVertexCode
      sge.graphics.glutils.ShaderProgram.prependFragmentCode = prependFragmentCode

    }
    if (throwException) checkCompilation(program)
    program

  /** Throws a `GdxRuntimeException` carrying the compilation log when the program did not build. */
  def checkCompilation(program: sge.graphics.glutils.ShaderProgram, msg: String = ""): Unit = {
    if (!program.compiled) throw new sge.utils.GdxRuntimeException(msg + program.log)

/** Ports GLSL 120 source to GLSL 150 when — and only when — the platform demands it.
  *
  * macOS offers OpenGL 3.2 as a *core* profile only, which is not backward compatible, so a
  * library that has to serve GL ES 2 and GL 3 from one set of shaders has to rewrite them at run
  * time. Everything here is upstream's, including the fact that the three `#version` answers
  * differ per platform and that desktop deliberately gets NO version statement.
  *
  * ==What takes the context and what does not, and the line is the CALLER's==
  * `mustUse32CShader` reads `Gdx.gl30` — one of the nine statics the base port retires — so it and
  * everything that calls it take `(using sge.Sge)` and read the GL handle through the context
  * instead. `toVert150`/`toFrag150` are pure string rewrites, take nothing, and stay reachable from
  * the hand-written suite, which is the only place in this port that exercises them.
  *
  * The two `Gdx.app` reads are LEFT AS THEY ARE, deliberately: `app` is one of the two statics that
  * keep a reader in the base's own emitted code, so this is the residual global the base still has
  * rather than one this shim reintroduced.
  */
  }
}
object ShaderCompatibilityHelper {

  def fromString(vert: String, frag: String)(using sge.Sge): sge.graphics.glutils.ShaderProgram = {
    var v = vert
    var f = frag
    if (mustUse32CShader()) {
      v = toVert150(v)
      f = toFrag150(f)
    }
    ShaderProgramFactory.fromString(
      getDefaultShaderVersionStatement() + v, getDefaultShaderVersionStatement() + f, true, true)

  }
  def toVert150(vert120: String): String = {
    vert120
      .replace("\nattribute ", "\nin ").replace(" attribute ", " in ")
      .replace("\nvarying ", "\nout ").replace(" varying ", " out ")
      .replace("texture2D(", "texture(")

  }
  def toFrag150(frag120: String): String = {
    var s = frag120
      .replace("\nattribute ", "\nout ").replace(" attribute ", " out ")
      .replace("\nvarying ", "\nin ").replace(" varying ", " in ")
    if (s.contains("gl_FragColor")) {
      s = s.replace("void main()", "out vec4 fragColor; \nvoid main()").replace("gl_FragColor", "fragColor")
    }
    s.replace("texture2D(", "texture(").replace("textureCube(", "texture(")

  /** `gl30 != null` also rules out ANGLE, which is why it is part of the test and not a tidier
    * platform check. */
  }
  def mustUse32CShader()(using sge.Sge): Boolean = {
    (sge.Gdx.app.`type` == sge.Application.ApplicationType.Desktop ||
      sge.Gdx.app.`type` == sge.Application.ApplicationType.HeadlessDesktop) &&
      scala.Predef.summon[sge.Sge].graphics.gl30 != null && sge.scenes.scene2d.utils.UIUtils.isMac

  }
  def getDefaultShaderVersionStatement()(using sge.Sge): String = {
    if (mustUse32CShader()) "#version 150\n" // macOS 3.2 core profile
    else if sge.Gdx.app.`type` != sge.Application.ApplicationType.Desktop &&
      sge.Gdx.app.`type` != sge.Application.ApplicationType.HeadlessDesktop
    then "#version 100\n" // GLSL ES (Android, iOS, WebGL)
    else "" // desktop: no version statement — a stricter compiler and an ANGLE probe avoided

  }
}