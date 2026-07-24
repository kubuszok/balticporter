package com.badlogic.gdx.graphics.profiling

class GL32Interceptor extends com.badlogic.gdx.graphics.profiling.GL31Interceptor with com.badlogic.gdx.graphics.GL32 {
  var gl32: com.badlogic.gdx.graphics.GL32 = null.asInstanceOf[com.badlogic.gdx.graphics.GL32]
  def this(glProfiler: com.badlogic.gdx.graphics.profiling.GLProfiler, gl32: com.badlogic.gdx.graphics.GL32) = {
    this()
    this.gl32 = gl32
  }
  def glBlendBarrier(): scala.Unit = {
    calls = calls + 1
    this.gl32.glBlendBarrier()
    this.check()
  }
  def glCopyImageSubData(srcName: scala.Int, srcTarget: scala.Int, srcLevel: scala.Int, srcX: scala.Int, srcY: scala.Int, srcZ: scala.Int, dstName: scala.Int, dstTarget: scala.Int, dstLevel: scala.Int, dstX: scala.Int, dstY: scala.Int, dstZ: scala.Int, srcWidth: scala.Int, srcHeight: scala.Int, srcDepth: scala.Int): scala.Unit = {
    calls = calls + 1
    this.gl32.glCopyImageSubData(srcName, srcTarget, srcLevel, srcX, srcY, srcZ, dstName, dstTarget, dstLevel, dstX, dstY, dstZ, srcWidth, srcHeight, srcDepth)
    this.check()
  }
  def glDebugMessageControl(source: scala.Int, `type`: scala.Int, severity: scala.Int, ids: java.nio.IntBuffer, enabled: scala.Boolean): scala.Unit = {
    calls = calls + 1
    this.gl32.glDebugMessageControl(source, `type`, severity, ids, enabled)
    this.check()
  }
  def glDebugMessageInsert(source: scala.Int, `type`: scala.Int, id: scala.Int, severity: scala.Int, buf: java.lang.String): scala.Unit = {
    calls = calls + 1
    this.gl32.glDebugMessageInsert(source, `type`, id, severity, buf)
    this.check()
  }
  def glDebugMessageCallback(callsback: com.badlogic.gdx.graphics.GL32.DebugProc): scala.Unit = {
    calls = calls + 1
    this.gl32.glDebugMessageCallback(callsback)
    this.check()
    this.check()
  }
  def glGetDebugMessageLog(count: scala.Int, sources: java.nio.IntBuffer, types: java.nio.IntBuffer, ids: java.nio.IntBuffer, severities: java.nio.IntBuffer, lengths: java.nio.IntBuffer, messageLog: java.nio.ByteBuffer): scala.Int = {
    calls = calls + 1
    val v: scala.Int = this.gl32.glGetDebugMessageLog(count, sources, types, ids, severities, lengths, messageLog)
    this.check()
    return v
  }
  def glPushDebugGroup(source: scala.Int, id: scala.Int, message: java.lang.String): scala.Unit = {
    calls = calls + 1
    this.gl32.glPushDebugGroup(source, id, message)
    this.check()
  }
  def glPopDebugGroup(): scala.Unit = {
    calls = calls + 1
    this.gl32.glPopDebugGroup()
    this.check()
  }
  def glObjectLabel(identifier: scala.Int, name: scala.Int, label: java.lang.String): scala.Unit = {
    calls = calls + 1
    this.gl32.glObjectLabel(identifier, name, label)
    this.check()
  }
  def glGetObjectLabel(identifier: scala.Int, name: scala.Int): java.lang.String = {
    calls = calls + 1
    val v: java.lang.String = this.gl32.glGetObjectLabel(identifier, name)
    this.check()
    return v
  }
  def glGetPointerv(pname: scala.Int): scala.Long = {
    calls = calls + 1
    val v: scala.Long = this.gl32.glGetPointerv(pname)
    this.check()
    return v
  }
  def glEnablei(target: scala.Int, index: scala.Int): scala.Unit = {
    calls = calls + 1
    this.gl32.glEnablei(target, index)
    this.check()
  }
  def glDisablei(target: scala.Int, index: scala.Int): scala.Unit = {
    calls = calls + 1
    this.gl32.glDisablei(target, index)
    this.check()
  }
  def glBlendEquationi(buf: scala.Int, mode: scala.Int): scala.Unit = {
    calls = calls + 1
    this.gl32.glBlendEquationi(buf, mode)
    this.check()
  }
  def glBlendEquationSeparatei(buf: scala.Int, modeRGB: scala.Int, modeAlpha: scala.Int): scala.Unit = {
    calls = calls + 1
    this.gl32.glBlendEquationSeparatei(buf, modeRGB, modeAlpha)
    this.check()
  }
  def glBlendFunci(buf: scala.Int, src: scala.Int, dst: scala.Int): scala.Unit = {
    calls = calls + 1
    this.gl32.glBlendFunci(buf, src, dst)
    this.check()
  }
  def glBlendFuncSeparatei(buf: scala.Int, srcRGB: scala.Int, dstRGB: scala.Int, srcAlpha: scala.Int, dstAlpha: scala.Int): scala.Unit = {
    calls = calls + 1
    this.gl32.glBlendFuncSeparatei(buf, srcRGB, dstRGB, srcAlpha, dstAlpha)
    this.check()
  }
  def glColorMaski(index: scala.Int, r: scala.Boolean, g: scala.Boolean, b: scala.Boolean, a: scala.Boolean): scala.Unit = {
    calls = calls + 1
    this.gl32.glColorMaski(index, r, g, b, a)
    this.check()
  }
  def glIsEnabledi(target: scala.Int, index: scala.Int): scala.Boolean = {
    calls = calls + 1
    val v: scala.Boolean = this.gl32.glIsEnabledi(target, index)
    this.check()
    return v
  }
  def glDrawElementsBaseVertex(mode: scala.Int, count: scala.Int, `type`: scala.Int, indices: java.nio.Buffer, basevertex: scala.Int): scala.Unit = {
    vertexCount.put(count)
    drawCalls = drawCalls + 1
    calls = calls + 1
    this.gl32.glDrawElementsBaseVertex(mode, count, `type`, indices, basevertex)
    this.check()
  }
  def glDrawRangeElementsBaseVertex(mode: scala.Int, start: scala.Int, `end`: scala.Int, count: scala.Int, `type`: scala.Int, indices: java.nio.Buffer, basevertex: scala.Int): scala.Unit = {
    vertexCount.put(count)
    drawCalls = drawCalls + 1
    calls = calls + 1
    this.gl32.glDrawRangeElementsBaseVertex(mode, start, `end`, count, `type`, indices, basevertex)
    this.check()
  }
  def glDrawElementsInstancedBaseVertex(mode: scala.Int, count: scala.Int, `type`: scala.Int, indices: java.nio.Buffer, instanceCount: scala.Int, basevertex: scala.Int): scala.Unit = {
    vertexCount.put(count)
    drawCalls = drawCalls + 1
    calls = calls + 1
    this.gl32.glDrawElementsInstancedBaseVertex(mode, count, `type`, indices, instanceCount, basevertex)
    this.check()
  }
  def glDrawElementsInstancedBaseVertex(mode: scala.Int, count: scala.Int, `type`: scala.Int, indicesOffset: scala.Int, instanceCount: scala.Int, basevertex: scala.Int): scala.Unit = {
    vertexCount.put(count)
    drawCalls = drawCalls + 1
    calls = calls + 1
    this.gl32.glDrawElementsInstancedBaseVertex(mode, count, `type`, indicesOffset, instanceCount, basevertex)
    this.check()
  }
  def glFramebufferTexture(target: scala.Int, attachment: scala.Int, texture: scala.Int, level: scala.Int): scala.Unit = {
    calls = calls + 1
    this.gl32.glFramebufferTexture(target, attachment, texture, level)
    this.check()
  }
  def glGetGraphicsResetStatus(): scala.Int = {
    calls = calls + 1
    val v: scala.Int = this.gl32.glGetGraphicsResetStatus()
    this.check()
    return v
  }
  def glReadnPixels(x: scala.Int, y: scala.Int, width: scala.Int, height: scala.Int, format: scala.Int, `type`: scala.Int, bufSize: scala.Int, data: java.nio.Buffer): scala.Unit = {
    calls = calls + 1
    this.gl32.glReadnPixels(x, y, width, height, format, `type`, bufSize, data)
    this.check()
  }
  def glGetnUniformfv(program: scala.Int, location: scala.Int, params: java.nio.FloatBuffer): scala.Unit = {
    calls = calls + 1
    this.gl32.glGetnUniformfv(program, location, params)
    this.check()
  }
  def glGetnUniformiv(program: scala.Int, location: scala.Int, params: java.nio.IntBuffer): scala.Unit = {
    calls = calls + 1
    this.gl32.glGetnUniformiv(program, location, params)
    this.check()
  }
  def glGetnUniformuiv(program: scala.Int, location: scala.Int, params: java.nio.IntBuffer): scala.Unit = {
    calls = calls + 1
    this.gl32.glGetnUniformuiv(program, location, params)
    this.check()
  }
  def glMinSampleShading(value: scala.Float): scala.Unit = {
    calls = calls + 1
    this.gl32.glMinSampleShading(value)
    this.check()
  }
  def glPatchParameteri(pname: scala.Int, value: scala.Int): scala.Unit = {
    calls = calls + 1
    this.gl32.glPatchParameteri(pname, value)
    this.check()
  }
  def glTexParameterIiv(target: scala.Int, pname: scala.Int, params: java.nio.IntBuffer): scala.Unit = {
    calls = calls + 1
    this.gl32.glTexParameterIiv(target, pname, params)
    this.check()
  }
  def glTexParameterIuiv(target: scala.Int, pname: scala.Int, params: java.nio.IntBuffer): scala.Unit = {
    calls = calls + 1
    this.gl32.glTexParameterIuiv(target, pname, params)
    this.check()
  }
  def glGetTexParameterIiv(target: scala.Int, pname: scala.Int, params: java.nio.IntBuffer): scala.Unit = {
    calls = calls + 1
    this.gl32.glGetTexParameterIiv(target, pname, params)
    this.check()
  }
  def glGetTexParameterIuiv(target: scala.Int, pname: scala.Int, params: java.nio.IntBuffer): scala.Unit = {
    calls = calls + 1
    this.gl32.glGetTexParameterIuiv(target, pname, params)
    this.check()
  }
  def glSamplerParameterIiv(sampler: scala.Int, pname: scala.Int, param: java.nio.IntBuffer): scala.Unit = {
    calls = calls + 1
    this.gl32.glSamplerParameterIiv(sampler, pname, param)
    this.check()
  }
  def glSamplerParameterIuiv(sampler: scala.Int, pname: scala.Int, param: java.nio.IntBuffer): scala.Unit = {
    calls = calls + 1
    this.gl32.glSamplerParameterIuiv(sampler, pname, param)
    this.check()
  }
  def glGetSamplerParameterIiv(sampler: scala.Int, pname: scala.Int, params: java.nio.IntBuffer): scala.Unit = {
    calls = calls + 1
    this.gl32.glGetSamplerParameterIiv(sampler, pname, params)
    this.check()
  }
  def glGetSamplerParameterIuiv(sampler: scala.Int, pname: scala.Int, params: java.nio.IntBuffer): scala.Unit = {
    calls = calls + 1
    this.gl32.glGetSamplerParameterIuiv(sampler, pname, params)
    this.check()
  }
  def glTexBuffer(target: scala.Int, internalformat: scala.Int, buffer: scala.Int): scala.Unit = {
    calls = calls + 1
    this.gl32.glTexBuffer(target, internalformat, buffer)
    this.check()
  }
  def glTexBufferRange(target: scala.Int, internalformat: scala.Int, buffer: scala.Int, offset: scala.Int, size: scala.Int): scala.Unit = {
    calls = calls + 1
    this.gl32.glTexBufferRange(target, internalformat, buffer, offset, size)
    this.check()
  }
  def glTexStorage3DMultisample(target: scala.Int, samples: scala.Int, internalformat: scala.Int, width: scala.Int, height: scala.Int, depth: scala.Int, fixedsamplelocations: scala.Boolean): scala.Unit = {
    calls = calls + 1
    this.gl32.glTexStorage3DMultisample(target, samples, internalformat, width, height, depth, fixedsamplelocations)
    this.check()
  }
}