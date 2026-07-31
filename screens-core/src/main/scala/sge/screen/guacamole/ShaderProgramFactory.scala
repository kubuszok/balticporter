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
  */
object ShaderProgramFactory:

  def fromString(vertexShader: String, fragmentShader: String): sge.graphics.glutils.ShaderProgram =
    fromString(vertexShader, fragmentShader, true)

  def fromString(vertexShader: String, fragmentShader: String, throwException: Boolean): sge.graphics.glutils.ShaderProgram =
    fromString(vertexShader, fragmentShader, throwException, false)

  def fromString(
      vertexShader: String,
      fragmentShader: String,
      throwException: Boolean,
      ignorePrepend: Boolean,
  ): sge.graphics.glutils.ShaderProgram =
    var prependVertexCode: String   = null
    var prependFragmentCode: String = null
    if ignorePrepend then
      prependVertexCode = sge.graphics.glutils.ShaderProgram.prependVertexCode
      sge.graphics.glutils.ShaderProgram.prependVertexCode = null
      prependFragmentCode = sge.graphics.glutils.ShaderProgram.prependFragmentCode
      sge.graphics.glutils.ShaderProgram.prependFragmentCode = null

    val program = new sge.graphics.glutils.ShaderProgram(vertexShader, fragmentShader)

    if ignorePrepend then
      sge.graphics.glutils.ShaderProgram.prependVertexCode = prependVertexCode
      sge.graphics.glutils.ShaderProgram.prependFragmentCode = prependFragmentCode

    if throwException then checkCompilation(program)
    program

  /** Throws a `GdxRuntimeException` carrying the compilation log when the program did not build. */
  def checkCompilation(program: sge.graphics.glutils.ShaderProgram, msg: String = ""): Unit =
    if !program.isCompiled() then throw new sge.utils.GdxRuntimeException(msg + program.getLog())

/** Ports GLSL 120 source to GLSL 150 when — and only when — the platform demands it.
  *
  * macOS offers OpenGL 3.2 as a *core* profile only, which is not backward compatible, so a
  * library that has to serve GL ES 2 and GL 3 from one set of shaders has to rewrite them at run
  * time. Everything here is upstream's, including the fact that the three `#version` answers
  * differ per platform and that desktop deliberately gets NO version statement.
  */
object ShaderCompatibilityHelper:

  def fromString(vert: String, frag: String): sge.graphics.glutils.ShaderProgram =
    var v = vert
    var f = frag
    if mustUse32CShader() then
      v = toVert150(v)
      f = toFrag150(f)
    ShaderProgramFactory.fromString(
      getDefaultShaderVersionStatement() + v, getDefaultShaderVersionStatement() + f, true, true)

  def toVert150(vert120: String): String =
    vert120
      .replace("\nattribute ", "\nin ").replace(" attribute ", " in ")
      .replace("\nvarying ", "\nout ").replace(" varying ", " out ")
      .replace("texture2D(", "texture(")

  def toFrag150(frag120: String): String =
    var s = frag120
      .replace("\nattribute ", "\nout ").replace(" attribute ", " out ")
      .replace("\nvarying ", "\nin ").replace(" varying ", " in ")
    if s.contains("gl_FragColor") then
      s = s.replace("void main()", "out vec4 fragColor; \nvoid main()").replace("gl_FragColor", "fragColor")
    s.replace("texture2D(", "texture(").replace("textureCube(", "texture(")

  /** `gl30 != null` also rules out ANGLE, which is why it is part of the test and not a tidier
    * platform check. */
  def mustUse32CShader(): Boolean =
    (sge.Gdx.app.getType() == sge.Application.ApplicationType.Desktop ||
      sge.Gdx.app.getType() == sge.Application.ApplicationType.HeadlessDesktop) &&
      sge.Gdx.gl30 != null && sge.scenes.scene2d.utils.UIUtils.isMac

  def getDefaultShaderVersionStatement(): String =
    if mustUse32CShader() then "#version 150\n" // macOS 3.2 core profile
    else if sge.Gdx.app.getType() != sge.Application.ApplicationType.Desktop &&
      sge.Gdx.app.getType() != sge.Application.ApplicationType.HeadlessDesktop
    then "#version 100\n" // GLSL ES (Android, iOS, WebGL)
    else "" // desktop: no version statement — a stricter compiler and an ANGLE probe avoided
