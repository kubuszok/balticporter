package com.badlogic.gdx.graphics.profiling

class GL30Interceptor(glProfiler$p: com.badlogic.gdx.graphics.profiling.GLProfiler, gl30$p: com.badlogic.gdx.graphics.GL30) extends com.badlogic.gdx.graphics.profiling.GLInterceptor(glProfiler$p) with com.badlogic.gdx.graphics.GL30 {
  var gl30: com.badlogic.gdx.graphics.GL30 = null.asInstanceOf[com.badlogic.gdx.graphics.GL30]
  this.gl30 = gl30$p
  private def check(): scala.Unit = {
    var error: scala.Int = this.gl30.glGetError()
    while (error != com.badlogic.gdx.graphics.GL20.GL_NO_ERROR) {
      glProfiler.getListener().onError(error)
      error = this.gl30.glGetError()
    }
  }
  @java.lang.Override
  override def glActiveTexture(texture: scala.Int): scala.Unit = {
    calls = calls + 1
    this.gl30.glActiveTexture(texture)
    this.check()
  }
  @java.lang.Override
  override def glBindTexture(target: scala.Int, texture: scala.Int): scala.Unit = {
    textureBindings = textureBindings + 1
    calls = calls + 1
    this.gl30.glBindTexture(target, texture)
    this.check()
  }
  @java.lang.Override
  override def glBlendFunc(sfactor: scala.Int, dfactor: scala.Int): scala.Unit = {
    calls = calls + 1
    this.gl30.glBlendFunc(sfactor, dfactor)
    this.check()
  }
  @java.lang.Override
  override def glClear(mask: scala.Int): scala.Unit = {
    calls = calls + 1
    this.gl30.glClear(mask)
    this.check()
  }
  @java.lang.Override
  override def glClearColor(red: scala.Float, green: scala.Float, blue: scala.Float, alpha: scala.Float): scala.Unit = {
    calls = calls + 1
    this.gl30.glClearColor(red, green, blue, alpha)
    this.check()
  }
  @java.lang.Override
  override def glClearDepthf(depth: scala.Float): scala.Unit = {
    calls = calls + 1
    this.gl30.glClearDepthf(depth)
    this.check()
  }
  @java.lang.Override
  override def glClearStencil(s: scala.Int): scala.Unit = {
    calls = calls + 1
    this.gl30.glClearStencil(s)
    this.check()
  }
  @java.lang.Override
  override def glColorMask(red: scala.Boolean, green: scala.Boolean, blue: scala.Boolean, alpha: scala.Boolean): scala.Unit = {
    calls = calls + 1
    this.gl30.glColorMask(red, green, blue, alpha)
    this.check()
  }
  @java.lang.Override
  override def glCompressedTexImage2D(target: scala.Int, level: scala.Int, internalformat: scala.Int, width: scala.Int, height: scala.Int, border: scala.Int, imageSize: scala.Int, data: java.nio.Buffer): scala.Unit = {
    calls = calls + 1
    this.gl30.glCompressedTexImage2D(target, level, internalformat, width, height, border, imageSize, data)
    this.check()
  }
  @java.lang.Override
  override def glCompressedTexSubImage2D(target: scala.Int, level: scala.Int, xoffset: scala.Int, yoffset: scala.Int, width: scala.Int, height: scala.Int, format: scala.Int, imageSize: scala.Int, data: java.nio.Buffer): scala.Unit = {
    calls = calls + 1
    this.gl30.glCompressedTexSubImage2D(target, level, xoffset, yoffset, width, height, format, imageSize, data)
    this.check()
  }
  @java.lang.Override
  override def glCopyTexImage2D(target: scala.Int, level: scala.Int, internalformat: scala.Int, x: scala.Int, y: scala.Int, width: scala.Int, height: scala.Int, border: scala.Int): scala.Unit = {
    calls = calls + 1
    this.gl30.glCopyTexImage2D(target, level, internalformat, x, y, width, height, border)
    this.check()
  }
  @java.lang.Override
  override def glCopyTexSubImage2D(target: scala.Int, level: scala.Int, xoffset: scala.Int, yoffset: scala.Int, x: scala.Int, y: scala.Int, width: scala.Int, height: scala.Int): scala.Unit = {
    calls = calls + 1
    this.gl30.glCopyTexSubImage2D(target, level, xoffset, yoffset, x, y, width, height)
    this.check()
  }
  @java.lang.Override
  override def glCullFace(mode: scala.Int): scala.Unit = {
    calls = calls + 1
    this.gl30.glCullFace(mode)
    this.check()
  }
  @java.lang.Override
  override def glDeleteTextures(n: scala.Int, textures: java.nio.IntBuffer): scala.Unit = {
    calls = calls + 1
    this.gl30.glDeleteTextures(n, textures)
    this.check()
  }
  @java.lang.Override
  override def glDeleteTexture(texture: scala.Int): scala.Unit = {
    calls = calls + 1
    this.gl30.glDeleteTexture(texture)
    this.check()
  }
  @java.lang.Override
  override def glDepthFunc(func: scala.Int): scala.Unit = {
    calls = calls + 1
    this.gl30.glDepthFunc(func)
    this.check()
  }
  @java.lang.Override
  override def glDepthMask(flag: scala.Boolean): scala.Unit = {
    calls = calls + 1
    this.gl30.glDepthMask(flag)
    this.check()
  }
  @java.lang.Override
  override def glDepthRangef(zNear: scala.Float, zFar: scala.Float): scala.Unit = {
    calls = calls + 1
    this.gl30.glDepthRangef(zNear, zFar)
    this.check()
  }
  @java.lang.Override
  override def glDisable(cap: scala.Int): scala.Unit = {
    calls = calls + 1
    this.gl30.glDisable(cap)
    this.check()
  }
  @java.lang.Override
  override def glDrawArrays(mode: scala.Int, first: scala.Int, count: scala.Int): scala.Unit = {
    vertexCount.put(count)
    drawCalls = drawCalls + 1
    calls = calls + 1
    this.gl30.glDrawArrays(mode, first, count)
    this.check()
  }
  @java.lang.Override
  override def glDrawElements(mode: scala.Int, count: scala.Int, `type`: scala.Int, indices: java.nio.Buffer): scala.Unit = {
    vertexCount.put(count)
    drawCalls = drawCalls + 1
    calls = calls + 1
    this.gl30.glDrawElements(mode, count, `type`, indices)
    this.check()
  }
  @java.lang.Override
  override def glEnable(cap: scala.Int): scala.Unit = {
    calls = calls + 1
    this.gl30.glEnable(cap)
    this.check()
  }
  @java.lang.Override
  override def glFinish(): scala.Unit = {
    calls = calls + 1
    this.gl30.glFinish()
    this.check()
  }
  @java.lang.Override
  override def glFlush(): scala.Unit = {
    calls = calls + 1
    this.gl30.glFlush()
    this.check()
  }
  @java.lang.Override
  override def glFrontFace(mode: scala.Int): scala.Unit = {
    calls = calls + 1
    this.gl30.glFrontFace(mode)
    this.check()
  }
  @java.lang.Override
  override def glGenTextures(n: scala.Int, textures: java.nio.IntBuffer): scala.Unit = {
    calls = calls + 1
    this.gl30.glGenTextures(n, textures)
    this.check()
  }
  @java.lang.Override
  override def glGenTexture(): scala.Int = {
    calls = calls + 1
    val result: scala.Int = this.gl30.glGenTexture()
    this.check()
    return result
  }
  @java.lang.Override
  override def glGetError(): scala.Int = {
    calls = calls + 1
    return this.gl30.glGetError()
  }
  @java.lang.Override
  override def glGetIntegerv(pname: scala.Int, params: java.nio.IntBuffer): scala.Unit = {
    calls = calls + 1
    this.gl30.glGetIntegerv(pname, params)
    this.check()
  }
  @java.lang.Override
  override def glGetString(name: scala.Int): java.lang.String = {
    calls = calls + 1
    val result: java.lang.String = this.gl30.glGetString(name)
    this.check()
    return result
  }
  @java.lang.Override
  override def glHint(target: scala.Int, mode: scala.Int): scala.Unit = {
    calls = calls + 1
    this.gl30.glHint(target, mode)
    this.check()
  }
  @java.lang.Override
  override def glLineWidth(width: scala.Float): scala.Unit = {
    calls = calls + 1
    this.gl30.glLineWidth(width)
    this.check()
  }
  @java.lang.Override
  override def glPixelStorei(pname: scala.Int, param: scala.Int): scala.Unit = {
    calls = calls + 1
    this.gl30.glPixelStorei(pname, param)
    this.check()
  }
  @java.lang.Override
  override def glPolygonOffset(factor: scala.Float, units: scala.Float): scala.Unit = {
    calls = calls + 1
    this.gl30.glPolygonOffset(factor, units)
    this.check()
  }
  @java.lang.Override
  override def glReadPixels(x: scala.Int, y: scala.Int, width: scala.Int, height: scala.Int, format: scala.Int, `type`: scala.Int, pixels: java.nio.Buffer): scala.Unit = {
    calls = calls + 1
    this.gl30.glReadPixels(x, y, width, height, format, `type`, pixels)
    this.check()
  }
  @java.lang.Override
  override def glScissor(x: scala.Int, y: scala.Int, width: scala.Int, height: scala.Int): scala.Unit = {
    calls = calls + 1
    this.gl30.glScissor(x, y, width, height)
    this.check()
  }
  @java.lang.Override
  override def glStencilFunc(func: scala.Int, ref: scala.Int, mask: scala.Int): scala.Unit = {
    calls = calls + 1
    this.gl30.glStencilFunc(func, ref, mask)
    this.check()
  }
  @java.lang.Override
  override def glStencilMask(mask: scala.Int): scala.Unit = {
    calls = calls + 1
    this.gl30.glStencilMask(mask)
    this.check()
  }
  @java.lang.Override
  override def glStencilOp(fail: scala.Int, zfail: scala.Int, zpass: scala.Int): scala.Unit = {
    calls = calls + 1
    this.gl30.glStencilOp(fail, zfail, zpass)
    this.check()
  }
  @java.lang.Override
  override def glTexImage2D(target: scala.Int, level: scala.Int, internalformat: scala.Int, width: scala.Int, height: scala.Int, border: scala.Int, format: scala.Int, `type`: scala.Int, pixels: java.nio.Buffer): scala.Unit = {
    calls = calls + 1
    this.gl30.glTexImage2D(target, level, internalformat, width, height, border, format, `type`, pixels)
    this.check()
  }
  @java.lang.Override
  override def glTexImage2D(target: scala.Int, level: scala.Int, internalformat: scala.Int, width: scala.Int, height: scala.Int, border: scala.Int, format: scala.Int, `type`: scala.Int, offset: scala.Int): scala.Unit = {
    calls = calls + 1
    this.gl30.glTexImage2D(target, level, internalformat, width, height, border, format, `type`, offset)
    this.check()
  }
  @java.lang.Override
  override def glTexParameterf(target: scala.Int, pname: scala.Int, param: scala.Float): scala.Unit = {
    calls = calls + 1
    this.gl30.glTexParameterf(target, pname, param)
    this.check()
  }
  @java.lang.Override
  override def glTexSubImage2D(target: scala.Int, level: scala.Int, xoffset: scala.Int, yoffset: scala.Int, width: scala.Int, height: scala.Int, format: scala.Int, `type`: scala.Int, pixels: java.nio.Buffer): scala.Unit = {
    calls = calls + 1
    this.gl30.glTexSubImage2D(target, level, xoffset, yoffset, width, height, format, `type`, pixels)
    this.check()
  }
  @java.lang.Override
  override def glTexSubImage2D(target: scala.Int, level: scala.Int, xoffset: scala.Int, yoffset: scala.Int, width: scala.Int, height: scala.Int, format: scala.Int, `type`: scala.Int, offset: scala.Int): scala.Unit = {
    calls = calls + 1
    this.gl30.glTexSubImage2D(target, level, xoffset, yoffset, width, height, format, `type`, offset)
    this.check()
  }
  @java.lang.Override
  override def glViewport(x: scala.Int, y: scala.Int, width: scala.Int, height: scala.Int): scala.Unit = {
    calls = calls + 1
    this.gl30.glViewport(x, y, width, height)
    this.check()
  }
  @java.lang.Override
  override def glAttachShader(program: scala.Int, shader: scala.Int): scala.Unit = {
    calls = calls + 1
    this.gl30.glAttachShader(program, shader)
    this.check()
  }
  @java.lang.Override
  override def glBindAttribLocation(program: scala.Int, index: scala.Int, name: java.lang.String): scala.Unit = {
    calls = calls + 1
    this.gl30.glBindAttribLocation(program, index, name)
    this.check()
  }
  @java.lang.Override
  override def glBindBuffer(target: scala.Int, buffer: scala.Int): scala.Unit = {
    calls = calls + 1
    this.gl30.glBindBuffer(target, buffer)
    this.check()
  }
  @java.lang.Override
  override def glBindFramebuffer(target: scala.Int, framebuffer: scala.Int): scala.Unit = {
    calls = calls + 1
    this.gl30.glBindFramebuffer(target, framebuffer)
    this.check()
  }
  @java.lang.Override
  override def glBindRenderbuffer(target: scala.Int, renderbuffer: scala.Int): scala.Unit = {
    calls = calls + 1
    this.gl30.glBindRenderbuffer(target, renderbuffer)
    this.check()
  }
  @java.lang.Override
  override def glBlendColor(red: scala.Float, green: scala.Float, blue: scala.Float, alpha: scala.Float): scala.Unit = {
    calls = calls + 1
    this.gl30.glBlendColor(red, green, blue, alpha)
    this.check()
  }
  @java.lang.Override
  override def glBlendEquation(mode: scala.Int): scala.Unit = {
    calls = calls + 1
    this.gl30.glBlendEquation(mode)
    this.check()
  }
  @java.lang.Override
  override def glBlendEquationSeparate(modeRGB: scala.Int, modeAlpha: scala.Int): scala.Unit = {
    calls = calls + 1
    this.gl30.glBlendEquationSeparate(modeRGB, modeAlpha)
    this.check()
  }
  @java.lang.Override
  override def glBlendFuncSeparate(srcRGB: scala.Int, dstRGB: scala.Int, srcAlpha: scala.Int, dstAlpha: scala.Int): scala.Unit = {
    calls = calls + 1
    this.gl30.glBlendFuncSeparate(srcRGB, dstRGB, srcAlpha, dstAlpha)
    this.check()
  }
  @java.lang.Override
  override def glBufferData(target: scala.Int, size: scala.Int, data: java.nio.Buffer, usage: scala.Int): scala.Unit = {
    calls = calls + 1
    this.gl30.glBufferData(target, size, data, usage)
    this.check()
  }
  @java.lang.Override
  override def glBufferSubData(target: scala.Int, offset: scala.Int, size: scala.Int, data: java.nio.Buffer): scala.Unit = {
    calls = calls + 1
    this.gl30.glBufferSubData(target, offset, size, data)
    this.check()
  }
  @java.lang.Override
  override def glCheckFramebufferStatus(target: scala.Int): scala.Int = {
    calls = calls + 1
    val result: scala.Int = this.gl30.glCheckFramebufferStatus(target)
    this.check()
    return result
  }
  @java.lang.Override
  override def glCompileShader(shader: scala.Int): scala.Unit = {
    calls = calls + 1
    this.gl30.glCompileShader(shader)
    this.check()
  }
  @java.lang.Override
  override def glCreateProgram(): scala.Int = {
    calls = calls + 1
    val result: scala.Int = this.gl30.glCreateProgram()
    this.check()
    return result
  }
  @java.lang.Override
  override def glCreateShader(`type`: scala.Int): scala.Int = {
    calls = calls + 1
    val result: scala.Int = this.gl30.glCreateShader(`type`)
    this.check()
    return result
  }
  @java.lang.Override
  override def glDeleteBuffer(buffer: scala.Int): scala.Unit = {
    calls = calls + 1
    this.gl30.glDeleteBuffer(buffer)
    this.check()
  }
  @java.lang.Override
  override def glDeleteBuffers(n: scala.Int, buffers: java.nio.IntBuffer): scala.Unit = {
    calls = calls + 1
    this.gl30.glDeleteBuffers(n, buffers)
    this.check()
  }
  @java.lang.Override
  override def glDeleteFramebuffer(framebuffer: scala.Int): scala.Unit = {
    calls = calls + 1
    this.gl30.glDeleteFramebuffer(framebuffer)
    this.check()
  }
  @java.lang.Override
  override def glDeleteFramebuffers(n: scala.Int, framebuffers: java.nio.IntBuffer): scala.Unit = {
    calls = calls + 1
    this.gl30.glDeleteFramebuffers(n, framebuffers)
    this.check()
  }
  @java.lang.Override
  override def glDeleteProgram(program: scala.Int): scala.Unit = {
    calls = calls + 1
    this.gl30.glDeleteProgram(program)
    this.check()
  }
  @java.lang.Override
  override def glDeleteRenderbuffer(renderbuffer: scala.Int): scala.Unit = {
    calls = calls + 1
    this.gl30.glDeleteRenderbuffer(renderbuffer)
    this.check()
  }
  @java.lang.Override
  override def glDeleteRenderbuffers(n: scala.Int, renderbuffers: java.nio.IntBuffer): scala.Unit = {
    calls = calls + 1
    this.gl30.glDeleteRenderbuffers(n, renderbuffers)
    this.check()
  }
  @java.lang.Override
  override def glDeleteShader(shader: scala.Int): scala.Unit = {
    calls = calls + 1
    this.gl30.glDeleteShader(shader)
    this.check()
  }
  @java.lang.Override
  override def glDetachShader(program: scala.Int, shader: scala.Int): scala.Unit = {
    calls = calls + 1
    this.gl30.glDetachShader(program, shader)
    this.check()
  }
  @java.lang.Override
  override def glDisableVertexAttribArray(index: scala.Int): scala.Unit = {
    calls = calls + 1
    this.gl30.glDisableVertexAttribArray(index)
    this.check()
  }
  @java.lang.Override
  override def glDrawElements(mode: scala.Int, count: scala.Int, `type`: scala.Int, indices: scala.Int): scala.Unit = {
    vertexCount.put(count)
    drawCalls = drawCalls + 1
    calls = calls + 1
    this.gl30.glDrawElements(mode, count, `type`, indices)
    this.check()
  }
  @java.lang.Override
  override def glEnableVertexAttribArray(index: scala.Int): scala.Unit = {
    calls = calls + 1
    this.gl30.glEnableVertexAttribArray(index)
    this.check()
  }
  @java.lang.Override
  override def glFramebufferRenderbuffer(target: scala.Int, attachment: scala.Int, renderbuffertarget: scala.Int, renderbuffer: scala.Int): scala.Unit = {
    calls = calls + 1
    this.gl30.glFramebufferRenderbuffer(target, attachment, renderbuffertarget, renderbuffer)
    this.check()
  }
  @java.lang.Override
  override def glFramebufferTexture2D(target: scala.Int, attachment: scala.Int, textarget: scala.Int, texture: scala.Int, level: scala.Int): scala.Unit = {
    calls = calls + 1
    this.gl30.glFramebufferTexture2D(target, attachment, textarget, texture, level)
    this.check()
  }
  @java.lang.Override
  override def glGenBuffer(): scala.Int = {
    calls = calls + 1
    val result: scala.Int = this.gl30.glGenBuffer()
    this.check()
    return result
  }
  @java.lang.Override
  override def glGenBuffers(n: scala.Int, buffers: java.nio.IntBuffer): scala.Unit = {
    calls = calls + 1
    this.gl30.glGenBuffers(n, buffers)
    this.check()
  }
  @java.lang.Override
  override def glGenerateMipmap(target: scala.Int): scala.Unit = {
    calls = calls + 1
    this.gl30.glGenerateMipmap(target)
    this.check()
  }
  @java.lang.Override
  override def glGenFramebuffer(): scala.Int = {
    calls = calls + 1
    val result: scala.Int = this.gl30.glGenFramebuffer()
    this.check()
    return result
  }
  @java.lang.Override
  override def glGenFramebuffers(n: scala.Int, framebuffers: java.nio.IntBuffer): scala.Unit = {
    calls = calls + 1
    this.gl30.glGenFramebuffers(n, framebuffers)
    this.check()
  }
  @java.lang.Override
  override def glGenRenderbuffer(): scala.Int = {
    calls = calls + 1
    val result: scala.Int = this.gl30.glGenRenderbuffer()
    this.check()
    return result
  }
  @java.lang.Override
  override def glGenRenderbuffers(n: scala.Int, renderbuffers: java.nio.IntBuffer): scala.Unit = {
    calls = calls + 1
    this.gl30.glGenRenderbuffers(n, renderbuffers)
    this.check()
  }
  @java.lang.Override
  override def glGetActiveAttrib(program: scala.Int, index: scala.Int, size: java.nio.IntBuffer, `type`: java.nio.IntBuffer): java.lang.String = {
    calls = calls + 1
    val result: java.lang.String = this.gl30.glGetActiveAttrib(program, index, size, `type`)
    this.check()
    return result
  }
  @java.lang.Override
  override def glGetActiveUniform(program: scala.Int, index: scala.Int, size: java.nio.IntBuffer, `type`: java.nio.IntBuffer): java.lang.String = {
    calls = calls + 1
    val result: java.lang.String = this.gl30.glGetActiveUniform(program, index, size, `type`)
    this.check()
    return result
  }
  @java.lang.Override
  override def glGetAttachedShaders(program: scala.Int, maxcount: scala.Int, count: java.nio.Buffer, shaders: java.nio.IntBuffer): scala.Unit = {
    calls = calls + 1
    this.gl30.glGetAttachedShaders(program, maxcount, count, shaders)
    this.check()
  }
  @java.lang.Override
  override def glGetAttribLocation(program: scala.Int, name: java.lang.String): scala.Int = {
    calls = calls + 1
    val result: scala.Int = this.gl30.glGetAttribLocation(program, name)
    this.check()
    return result
  }
  @java.lang.Override
  override def glGetBooleanv(pname: scala.Int, params: java.nio.Buffer): scala.Unit = {
    calls = calls + 1
    this.gl30.glGetBooleanv(pname, params)
    this.check()
  }
  @java.lang.Override
  override def glGetBufferParameteriv(target: scala.Int, pname: scala.Int, params: java.nio.IntBuffer): scala.Unit = {
    calls = calls + 1
    this.gl30.glGetBufferParameteriv(target, pname, params)
    this.check()
  }
  @java.lang.Override
  override def glGetFloatv(pname: scala.Int, params: java.nio.FloatBuffer): scala.Unit = {
    calls = calls + 1
    this.gl30.glGetFloatv(pname, params)
    this.check()
  }
  @java.lang.Override
  override def glGetFramebufferAttachmentParameteriv(target: scala.Int, attachment: scala.Int, pname: scala.Int, params: java.nio.IntBuffer): scala.Unit = {
    calls = calls + 1
    this.gl30.glGetFramebufferAttachmentParameteriv(target, attachment, pname, params)
    this.check()
  }
  @java.lang.Override
  override def glGetProgramiv(program: scala.Int, pname: scala.Int, params: java.nio.IntBuffer): scala.Unit = {
    calls = calls + 1
    this.gl30.glGetProgramiv(program, pname, params)
    this.check()
  }
  @java.lang.Override
  override def glGetProgramInfoLog(program: scala.Int): java.lang.String = {
    calls = calls + 1
    val result: java.lang.String = this.gl30.glGetProgramInfoLog(program)
    this.check()
    return result
  }
  @java.lang.Override
  override def glGetRenderbufferParameteriv(target: scala.Int, pname: scala.Int, params: java.nio.IntBuffer): scala.Unit = {
    calls = calls + 1
    this.gl30.glGetRenderbufferParameteriv(target, pname, params)
    this.check()
  }
  @java.lang.Override
  override def glGetShaderiv(shader: scala.Int, pname: scala.Int, params: java.nio.IntBuffer): scala.Unit = {
    calls = calls + 1
    this.gl30.glGetShaderiv(shader, pname, params)
    this.check()
  }
  @java.lang.Override
  override def glGetShaderInfoLog(shader: scala.Int): java.lang.String = {
    calls = calls + 1
    val result: java.lang.String = this.gl30.glGetShaderInfoLog(shader)
    this.check()
    return result
  }
  @java.lang.Override
  override def glGetShaderPrecisionFormat(shadertype: scala.Int, precisiontype: scala.Int, range: java.nio.IntBuffer, precision: java.nio.IntBuffer): scala.Unit = {
    calls = calls + 1
    this.gl30.glGetShaderPrecisionFormat(shadertype, precisiontype, range, precision)
    this.check()
  }
  @java.lang.Override
  override def glGetTexParameterfv(target: scala.Int, pname: scala.Int, params: java.nio.FloatBuffer): scala.Unit = {
    calls = calls + 1
    this.gl30.glGetTexParameterfv(target, pname, params)
    this.check()
  }
  @java.lang.Override
  override def glGetTexParameteriv(target: scala.Int, pname: scala.Int, params: java.nio.IntBuffer): scala.Unit = {
    calls = calls + 1
    this.gl30.glGetTexParameteriv(target, pname, params)
    this.check()
  }
  @java.lang.Override
  override def glGetUniformfv(program: scala.Int, location: scala.Int, params: java.nio.FloatBuffer): scala.Unit = {
    calls = calls + 1
    this.gl30.glGetUniformfv(program, location, params)
    this.check()
  }
  @java.lang.Override
  override def glGetUniformiv(program: scala.Int, location: scala.Int, params: java.nio.IntBuffer): scala.Unit = {
    calls = calls + 1
    this.gl30.glGetUniformiv(program, location, params)
    this.check()
  }
  @java.lang.Override
  override def glGetUniformLocation(program: scala.Int, name: java.lang.String): scala.Int = {
    calls = calls + 1
    val result: scala.Int = this.gl30.glGetUniformLocation(program, name)
    this.check()
    return result
  }
  @java.lang.Override
  override def glGetVertexAttribfv(index: scala.Int, pname: scala.Int, params: java.nio.FloatBuffer): scala.Unit = {
    calls = calls + 1
    this.gl30.glGetVertexAttribfv(index, pname, params)
    this.check()
  }
  @java.lang.Override
  override def glGetVertexAttribiv(index: scala.Int, pname: scala.Int, params: java.nio.IntBuffer): scala.Unit = {
    calls = calls + 1
    this.gl30.glGetVertexAttribiv(index, pname, params)
    this.check()
  }
  @java.lang.Override
  override def glGetVertexAttribPointerv(index: scala.Int, pname: scala.Int, pointer: java.nio.Buffer): scala.Unit = {
    calls = calls + 1
    this.gl30.glGetVertexAttribPointerv(index, pname, pointer)
    this.check()
  }
  @java.lang.Override
  override def glIsBuffer(buffer: scala.Int): scala.Boolean = {
    calls = calls + 1
    val result: scala.Boolean = this.gl30.glIsBuffer(buffer)
    this.check()
    return result
  }
  @java.lang.Override
  override def glIsEnabled(cap: scala.Int): scala.Boolean = {
    calls = calls + 1
    val result: scala.Boolean = this.gl30.glIsEnabled(cap)
    this.check()
    return result
  }
  @java.lang.Override
  override def glIsFramebuffer(framebuffer: scala.Int): scala.Boolean = {
    calls = calls + 1
    val result: scala.Boolean = this.gl30.glIsFramebuffer(framebuffer)
    this.check()
    return result
  }
  @java.lang.Override
  override def glIsProgram(program: scala.Int): scala.Boolean = {
    calls = calls + 1
    val result: scala.Boolean = this.gl30.glIsProgram(program)
    this.check()
    return result
  }
  @java.lang.Override
  override def glIsRenderbuffer(renderbuffer: scala.Int): scala.Boolean = {
    calls = calls + 1
    val result: scala.Boolean = this.gl30.glIsRenderbuffer(renderbuffer)
    this.check()
    return result
  }
  @java.lang.Override
  override def glIsShader(shader: scala.Int): scala.Boolean = {
    calls = calls + 1
    val result: scala.Boolean = this.gl30.glIsShader(shader)
    this.check()
    return result
  }
  @java.lang.Override
  override def glIsTexture(texture: scala.Int): scala.Boolean = {
    calls = calls + 1
    val result: scala.Boolean = this.gl30.glIsTexture(texture)
    this.check()
    return result
  }
  @java.lang.Override
  override def glLinkProgram(program: scala.Int): scala.Unit = {
    calls = calls + 1
    this.gl30.glLinkProgram(program)
    this.check()
  }
  @java.lang.Override
  override def glReleaseShaderCompiler(): scala.Unit = {
    calls = calls + 1
    this.gl30.glReleaseShaderCompiler()
    this.check()
  }
  @java.lang.Override
  override def glRenderbufferStorage(target: scala.Int, internalformat: scala.Int, width: scala.Int, height: scala.Int): scala.Unit = {
    calls = calls + 1
    this.gl30.glRenderbufferStorage(target, internalformat, width, height)
    this.check()
  }
  @java.lang.Override
  override def glSampleCoverage(value: scala.Float, invert: scala.Boolean): scala.Unit = {
    calls = calls + 1
    this.gl30.glSampleCoverage(value, invert)
    this.check()
  }
  @java.lang.Override
  override def glShaderBinary(n: scala.Int, shaders: java.nio.IntBuffer, binaryformat: scala.Int, binary: java.nio.Buffer, length: scala.Int): scala.Unit = {
    calls = calls + 1
    this.gl30.glShaderBinary(n, shaders, binaryformat, binary, length)
    this.check()
  }
  @java.lang.Override
  override def glShaderSource(shader: scala.Int, string: java.lang.String): scala.Unit = {
    calls = calls + 1
    this.gl30.glShaderSource(shader, string)
    this.check()
  }
  @java.lang.Override
  override def glStencilFuncSeparate(face: scala.Int, func: scala.Int, ref: scala.Int, mask: scala.Int): scala.Unit = {
    calls = calls + 1
    this.gl30.glStencilFuncSeparate(face, func, ref, mask)
    this.check()
  }
  @java.lang.Override
  override def glStencilMaskSeparate(face: scala.Int, mask: scala.Int): scala.Unit = {
    calls = calls + 1
    this.gl30.glStencilMaskSeparate(face, mask)
    this.check()
  }
  @java.lang.Override
  override def glStencilOpSeparate(face: scala.Int, fail: scala.Int, zfail: scala.Int, zpass: scala.Int): scala.Unit = {
    calls = calls + 1
    this.gl30.glStencilOpSeparate(face, fail, zfail, zpass)
    this.check()
  }
  @java.lang.Override
  override def glTexParameterfv(target: scala.Int, pname: scala.Int, params: java.nio.FloatBuffer): scala.Unit = {
    calls = calls + 1
    this.gl30.glTexParameterfv(target, pname, params)
    this.check()
  }
  @java.lang.Override
  override def glTexParameteri(target: scala.Int, pname: scala.Int, param: scala.Int): scala.Unit = {
    calls = calls + 1
    this.gl30.glTexParameteri(target, pname, param)
    this.check()
  }
  @java.lang.Override
  override def glTexParameteriv(target: scala.Int, pname: scala.Int, params: java.nio.IntBuffer): scala.Unit = {
    calls = calls + 1
    this.gl30.glTexParameteriv(target, pname, params)
    this.check()
  }
  @java.lang.Override
  override def glUniform1f(location: scala.Int, x: scala.Float): scala.Unit = {
    calls = calls + 1
    this.gl30.glUniform1f(location, x)
    this.check()
  }
  @java.lang.Override
  override def glUniform1fv(location: scala.Int, count: scala.Int, v: java.nio.FloatBuffer): scala.Unit = {
    calls = calls + 1
    this.gl30.glUniform1fv(location, count, v)
    this.check()
  }
  @java.lang.Override
  override def glUniform1fv(location: scala.Int, count: scala.Int, v: scala.Array[scala.Float], offset: scala.Int): scala.Unit = {
    calls = calls + 1
    this.gl30.glUniform1fv(location, count, v, offset)
    this.check()
  }
  @java.lang.Override
  override def glUniform1i(location: scala.Int, x: scala.Int): scala.Unit = {
    calls = calls + 1
    this.gl30.glUniform1i(location, x)
    this.check()
  }
  @java.lang.Override
  override def glUniform1iv(location: scala.Int, count: scala.Int, v: java.nio.IntBuffer): scala.Unit = {
    calls = calls + 1
    this.gl30.glUniform1iv(location, count, v)
    this.check()
  }
  @java.lang.Override
  override def glUniform1iv(location: scala.Int, count: scala.Int, v: scala.Array[scala.Int], offset: scala.Int): scala.Unit = {
    calls = calls + 1
    this.gl30.glUniform1iv(location, count, v, offset)
    this.check()
  }
  @java.lang.Override
  override def glUniform2f(location: scala.Int, x: scala.Float, y: scala.Float): scala.Unit = {
    calls = calls + 1
    this.gl30.glUniform2f(location, x, y)
    this.check()
  }
  @java.lang.Override
  override def glUniform2fv(location: scala.Int, count: scala.Int, v: java.nio.FloatBuffer): scala.Unit = {
    calls = calls + 1
    this.gl30.glUniform2fv(location, count, v)
    this.check()
  }
  @java.lang.Override
  override def glUniform2fv(location: scala.Int, count: scala.Int, v: scala.Array[scala.Float], offset: scala.Int): scala.Unit = {
    calls = calls + 1
    this.gl30.glUniform2fv(location, count, v, offset)
    this.check()
  }
  @java.lang.Override
  override def glUniform2i(location: scala.Int, x: scala.Int, y: scala.Int): scala.Unit = {
    calls = calls + 1
    this.gl30.glUniform2i(location, x, y)
    this.check()
  }
  @java.lang.Override
  override def glUniform2iv(location: scala.Int, count: scala.Int, v: java.nio.IntBuffer): scala.Unit = {
    calls = calls + 1
    this.gl30.glUniform2iv(location, count, v)
    this.check()
  }
  @java.lang.Override
  override def glUniform2iv(location: scala.Int, count: scala.Int, v: scala.Array[scala.Int], offset: scala.Int): scala.Unit = {
    calls = calls + 1
    this.gl30.glUniform2iv(location, count, v, offset)
    this.check()
  }
  @java.lang.Override
  override def glUniform3f(location: scala.Int, x: scala.Float, y: scala.Float, z: scala.Float): scala.Unit = {
    calls = calls + 1
    this.gl30.glUniform3f(location, x, y, z)
    this.check()
  }
  @java.lang.Override
  override def glUniform3fv(location: scala.Int, count: scala.Int, v: java.nio.FloatBuffer): scala.Unit = {
    calls = calls + 1
    this.gl30.glUniform3fv(location, count, v)
    this.check()
  }
  @java.lang.Override
  override def glUniform3fv(location: scala.Int, count: scala.Int, v: scala.Array[scala.Float], offset: scala.Int): scala.Unit = {
    calls = calls + 1
    this.gl30.glUniform3fv(location, count, v, offset)
    this.check()
  }
  @java.lang.Override
  override def glUniform3i(location: scala.Int, x: scala.Int, y: scala.Int, z: scala.Int): scala.Unit = {
    calls = calls + 1
    this.gl30.glUniform3i(location, x, y, z)
    this.check()
  }
  @java.lang.Override
  override def glUniform3iv(location: scala.Int, count: scala.Int, v: java.nio.IntBuffer): scala.Unit = {
    calls = calls + 1
    this.gl30.glUniform3iv(location, count, v)
    this.check()
  }
  @java.lang.Override
  override def glUniform3iv(location: scala.Int, count: scala.Int, v: scala.Array[scala.Int], offset: scala.Int): scala.Unit = {
    calls = calls + 1
    this.gl30.glUniform3iv(location, count, v, offset)
    this.check()
  }
  @java.lang.Override
  override def glUniform4f(location: scala.Int, x: scala.Float, y: scala.Float, z: scala.Float, w: scala.Float): scala.Unit = {
    calls = calls + 1
    this.gl30.glUniform4f(location, x, y, z, w)
    this.check()
  }
  @java.lang.Override
  override def glUniform4fv(location: scala.Int, count: scala.Int, v: java.nio.FloatBuffer): scala.Unit = {
    calls = calls + 1
    this.gl30.glUniform4fv(location, count, v)
    this.check()
  }
  @java.lang.Override
  override def glUniform4fv(location: scala.Int, count: scala.Int, v: scala.Array[scala.Float], offset: scala.Int): scala.Unit = {
    calls = calls + 1
    this.gl30.glUniform4fv(location, count, v, offset)
    this.check()
  }
  @java.lang.Override
  override def glUniform4i(location: scala.Int, x: scala.Int, y: scala.Int, z: scala.Int, w: scala.Int): scala.Unit = {
    calls = calls + 1
    this.gl30.glUniform4i(location, x, y, z, w)
    this.check()
  }
  @java.lang.Override
  override def glUniform4iv(location: scala.Int, count: scala.Int, v: java.nio.IntBuffer): scala.Unit = {
    calls = calls + 1
    this.gl30.glUniform4iv(location, count, v)
    this.check()
  }
  @java.lang.Override
  override def glUniform4iv(location: scala.Int, count: scala.Int, v: scala.Array[scala.Int], offset: scala.Int): scala.Unit = {
    calls = calls + 1
    this.gl30.glUniform4iv(location, count, v, offset)
    this.check()
  }
  @java.lang.Override
  override def glUniformMatrix2fv(location: scala.Int, count: scala.Int, transpose: scala.Boolean, value: java.nio.FloatBuffer): scala.Unit = {
    calls = calls + 1
    this.gl30.glUniformMatrix2fv(location, count, transpose, value)
    this.check()
  }
  @java.lang.Override
  override def glUniformMatrix2fv(location: scala.Int, count: scala.Int, transpose: scala.Boolean, value: scala.Array[scala.Float], offset: scala.Int): scala.Unit = {
    calls = calls + 1
    this.gl30.glUniformMatrix2fv(location, count, transpose, value, offset)
    this.check()
  }
  @java.lang.Override
  override def glUniformMatrix3fv(location: scala.Int, count: scala.Int, transpose: scala.Boolean, value: java.nio.FloatBuffer): scala.Unit = {
    calls = calls + 1
    this.gl30.glUniformMatrix3fv(location, count, transpose, value)
    this.check()
  }
  @java.lang.Override
  override def glUniformMatrix3fv(location: scala.Int, count: scala.Int, transpose: scala.Boolean, value: scala.Array[scala.Float], offset: scala.Int): scala.Unit = {
    calls = calls + 1
    this.gl30.glUniformMatrix3fv(location, count, transpose, value, offset)
    this.check()
  }
  @java.lang.Override
  override def glUniformMatrix4fv(location: scala.Int, count: scala.Int, transpose: scala.Boolean, value: java.nio.FloatBuffer): scala.Unit = {
    calls = calls + 1
    this.gl30.glUniformMatrix4fv(location, count, transpose, value)
    this.check()
  }
  @java.lang.Override
  override def glUniformMatrix4fv(location: scala.Int, count: scala.Int, transpose: scala.Boolean, value: scala.Array[scala.Float], offset: scala.Int): scala.Unit = {
    calls = calls + 1
    this.gl30.glUniformMatrix4fv(location, count, transpose, value, offset)
    this.check()
  }
  @java.lang.Override
  override def glUseProgram(program: scala.Int): scala.Unit = {
    shaderSwitches = shaderSwitches + 1
    calls = calls + 1
    this.gl30.glUseProgram(program)
    this.check()
  }
  @java.lang.Override
  override def glValidateProgram(program: scala.Int): scala.Unit = {
    calls = calls + 1
    this.gl30.glValidateProgram(program)
    this.check()
  }
  @java.lang.Override
  override def glVertexAttrib1f(indx: scala.Int, x: scala.Float): scala.Unit = {
    calls = calls + 1
    this.gl30.glVertexAttrib1f(indx, x)
    this.check()
  }
  @java.lang.Override
  override def glVertexAttrib1fv(indx: scala.Int, values: java.nio.FloatBuffer): scala.Unit = {
    calls = calls + 1
    this.gl30.glVertexAttrib1fv(indx, values)
    this.check()
  }
  @java.lang.Override
  override def glVertexAttrib2f(indx: scala.Int, x: scala.Float, y: scala.Float): scala.Unit = {
    calls = calls + 1
    this.gl30.glVertexAttrib2f(indx, x, y)
    this.check()
  }
  @java.lang.Override
  override def glVertexAttrib2fv(indx: scala.Int, values: java.nio.FloatBuffer): scala.Unit = {
    calls = calls + 1
    this.gl30.glVertexAttrib2fv(indx, values)
    this.check()
  }
  @java.lang.Override
  override def glVertexAttrib3f(indx: scala.Int, x: scala.Float, y: scala.Float, z: scala.Float): scala.Unit = {
    calls = calls + 1
    this.gl30.glVertexAttrib3f(indx, x, y, z)
    this.check()
  }
  @java.lang.Override
  override def glVertexAttrib3fv(indx: scala.Int, values: java.nio.FloatBuffer): scala.Unit = {
    calls = calls + 1
    this.gl30.glVertexAttrib3fv(indx, values)
    this.check()
  }
  @java.lang.Override
  override def glVertexAttrib4f(indx: scala.Int, x: scala.Float, y: scala.Float, z: scala.Float, w: scala.Float): scala.Unit = {
    calls = calls + 1
    this.gl30.glVertexAttrib4f(indx, x, y, z, w)
    this.check()
  }
  @java.lang.Override
  override def glVertexAttrib4fv(indx: scala.Int, values: java.nio.FloatBuffer): scala.Unit = {
    calls = calls + 1
    this.gl30.glVertexAttrib4fv(indx, values)
    this.check()
  }
  @java.lang.Override
  override def glVertexAttribPointer(indx: scala.Int, size: scala.Int, `type`: scala.Int, normalized: scala.Boolean, stride: scala.Int, ptr: java.nio.Buffer): scala.Unit = {
    calls = calls + 1
    this.gl30.glVertexAttribPointer(indx, size, `type`, normalized, stride, ptr)
    this.check()
  }
  @java.lang.Override
  override def glVertexAttribPointer(indx: scala.Int, size: scala.Int, `type`: scala.Int, normalized: scala.Boolean, stride: scala.Int, ptr: scala.Int): scala.Unit = {
    calls = calls + 1
    this.gl30.glVertexAttribPointer(indx, size, `type`, normalized, stride, ptr)
    this.check()
  }
  @java.lang.Override
  override def glReadBuffer(mode: scala.Int): scala.Unit = {
    calls = calls + 1
    this.gl30.glReadBuffer(mode)
    this.check()
  }
  @java.lang.Override
  override def glDrawRangeElements(mode: scala.Int, start: scala.Int, `end`: scala.Int, count: scala.Int, `type`: scala.Int, indices: java.nio.Buffer): scala.Unit = {
    vertexCount.put(count)
    drawCalls = drawCalls + 1
    calls = calls + 1
    this.gl30.glDrawRangeElements(mode, start, `end`, count, `type`, indices)
    this.check()
  }
  @java.lang.Override
  override def glDrawRangeElements(mode: scala.Int, start: scala.Int, `end`: scala.Int, count: scala.Int, `type`: scala.Int, offset: scala.Int): scala.Unit = {
    vertexCount.put(count)
    drawCalls = drawCalls + 1
    calls = calls + 1
    this.gl30.glDrawRangeElements(mode, start, `end`, count, `type`, offset)
    this.check()
  }
  @java.lang.Override
  override def glTexImage3D(target: scala.Int, level: scala.Int, internalformat: scala.Int, width: scala.Int, height: scala.Int, depth: scala.Int, border: scala.Int, format: scala.Int, `type`: scala.Int, pixels: java.nio.Buffer): scala.Unit = {
    calls = calls + 1
    this.gl30.glTexImage3D(target, level, internalformat, width, height, depth, border, format, `type`, pixels)
    this.check()
  }
  @java.lang.Override
  override def glTexImage3D(target: scala.Int, level: scala.Int, internalformat: scala.Int, width: scala.Int, height: scala.Int, depth: scala.Int, border: scala.Int, format: scala.Int, `type`: scala.Int, offset: scala.Int): scala.Unit = {
    calls = calls + 1
    this.gl30.glTexImage3D(target, level, internalformat, width, height, depth, border, format, `type`, offset)
    this.check()
  }
  @java.lang.Override
  override def glTexSubImage3D(target: scala.Int, level: scala.Int, xoffset: scala.Int, yoffset: scala.Int, zoffset: scala.Int, width: scala.Int, height: scala.Int, depth: scala.Int, format: scala.Int, `type`: scala.Int, pixels: java.nio.Buffer): scala.Unit = {
    calls = calls + 1
    this.gl30.glTexSubImage3D(target, level, xoffset, yoffset, zoffset, width, height, depth, format, `type`, pixels)
    this.check()
  }
  @java.lang.Override
  override def glTexSubImage3D(target: scala.Int, level: scala.Int, xoffset: scala.Int, yoffset: scala.Int, zoffset: scala.Int, width: scala.Int, height: scala.Int, depth: scala.Int, format: scala.Int, `type`: scala.Int, offset: scala.Int): scala.Unit = {
    calls = calls + 1
    this.gl30.glTexSubImage3D(target, level, xoffset, yoffset, zoffset, width, height, depth, format, `type`, offset)
    this.check()
  }
  @java.lang.Override
  override def glCopyTexSubImage3D(target: scala.Int, level: scala.Int, xoffset: scala.Int, yoffset: scala.Int, zoffset: scala.Int, x: scala.Int, y: scala.Int, width: scala.Int, height: scala.Int): scala.Unit = {
    calls = calls + 1
    this.gl30.glCopyTexSubImage3D(target, level, xoffset, yoffset, zoffset, x, y, width, height)
    this.check()
  }
  @java.lang.Override
  override def glGenQueries(n: scala.Int, ids: scala.Array[scala.Int], offset: scala.Int): scala.Unit = {
    calls = calls + 1
    this.gl30.glGenQueries(n, ids, offset)
    this.check()
  }
  @java.lang.Override
  override def glGenQueries(n: scala.Int, ids: java.nio.IntBuffer): scala.Unit = {
    calls = calls + 1
    this.gl30.glGenQueries(n, ids)
    this.check()
  }
  @java.lang.Override
  override def glDeleteQueries(n: scala.Int, ids: scala.Array[scala.Int], offset: scala.Int): scala.Unit = {
    calls = calls + 1
    this.gl30.glDeleteQueries(n, ids, offset)
    this.check()
  }
  @java.lang.Override
  override def glDeleteQueries(n: scala.Int, ids: java.nio.IntBuffer): scala.Unit = {
    calls = calls + 1
    this.gl30.glDeleteQueries(n, ids)
    this.check()
  }
  @java.lang.Override
  override def glIsQuery(id: scala.Int): scala.Boolean = {
    calls = calls + 1
    val result: scala.Boolean = this.gl30.glIsQuery(id)
    this.check()
    return result
  }
  @java.lang.Override
  override def glBeginQuery(target: scala.Int, id: scala.Int): scala.Unit = {
    calls = calls + 1
    this.gl30.glBeginQuery(target, id)
    this.check()
  }
  @java.lang.Override
  override def glEndQuery(target: scala.Int): scala.Unit = {
    calls = calls + 1
    this.gl30.glEndQuery(target)
    this.check()
  }
  @java.lang.Override
  override def glGetQueryiv(target: scala.Int, pname: scala.Int, params: java.nio.IntBuffer): scala.Unit = {
    calls = calls + 1
    this.gl30.glGetQueryiv(target, pname, params)
    this.check()
  }
  @java.lang.Override
  override def glGetQueryObjectuiv(id: scala.Int, pname: scala.Int, params: java.nio.IntBuffer): scala.Unit = {
    calls = calls + 1
    this.gl30.glGetQueryObjectuiv(id, pname, params)
    this.check()
  }
  @java.lang.Override
  override def glUnmapBuffer(target: scala.Int): scala.Boolean = {
    calls = calls + 1
    val result: scala.Boolean = this.gl30.glUnmapBuffer(target)
    this.check()
    return result
  }
  @java.lang.Override
  override def glGetBufferPointerv(target: scala.Int, pname: scala.Int): java.nio.Buffer = {
    calls = calls + 1
    val result: java.nio.Buffer = this.gl30.glGetBufferPointerv(target, pname)
    this.check()
    return result
  }
  @java.lang.Override
  override def glDrawBuffers(n: scala.Int, bufs: java.nio.IntBuffer): scala.Unit = {
    drawCalls = drawCalls + 1
    calls = calls + 1
    this.gl30.glDrawBuffers(n, bufs)
    this.check()
  }
  @java.lang.Override
  override def glUniformMatrix2x3fv(location: scala.Int, count: scala.Int, transpose: scala.Boolean, value: java.nio.FloatBuffer): scala.Unit = {
    calls = calls + 1
    this.gl30.glUniformMatrix2x3fv(location, count, transpose, value)
    this.check()
  }
  @java.lang.Override
  override def glUniformMatrix3x2fv(location: scala.Int, count: scala.Int, transpose: scala.Boolean, value: java.nio.FloatBuffer): scala.Unit = {
    calls = calls + 1
    this.gl30.glUniformMatrix3x2fv(location, count, transpose, value)
    this.check()
  }
  @java.lang.Override
  override def glUniformMatrix2x4fv(location: scala.Int, count: scala.Int, transpose: scala.Boolean, value: java.nio.FloatBuffer): scala.Unit = {
    calls = calls + 1
    this.gl30.glUniformMatrix2x4fv(location, count, transpose, value)
    this.check()
  }
  @java.lang.Override
  override def glUniformMatrix4x2fv(location: scala.Int, count: scala.Int, transpose: scala.Boolean, value: java.nio.FloatBuffer): scala.Unit = {
    calls = calls + 1
    this.gl30.glUniformMatrix4x2fv(location, count, transpose, value)
    this.check()
  }
  @java.lang.Override
  override def glUniformMatrix3x4fv(location: scala.Int, count: scala.Int, transpose: scala.Boolean, value: java.nio.FloatBuffer): scala.Unit = {
    calls = calls + 1
    this.gl30.glUniformMatrix3x4fv(location, count, transpose, value)
    this.check()
  }
  @java.lang.Override
  override def glUniformMatrix4x3fv(location: scala.Int, count: scala.Int, transpose: scala.Boolean, value: java.nio.FloatBuffer): scala.Unit = {
    calls = calls + 1
    this.gl30.glUniformMatrix4x3fv(location, count, transpose, value)
    this.check()
  }
  @java.lang.Override
  override def glBlitFramebuffer(srcX0: scala.Int, srcY0: scala.Int, srcX1: scala.Int, srcY1: scala.Int, dstX0: scala.Int, dstY0: scala.Int, dstX1: scala.Int, dstY1: scala.Int, mask: scala.Int, filter: scala.Int): scala.Unit = {
    calls = calls + 1
    this.gl30.glBlitFramebuffer(srcX0, srcY0, srcX1, srcY1, dstX0, dstY0, dstX1, dstY1, mask, filter)
    this.check()
  }
  @java.lang.Override
  override def glRenderbufferStorageMultisample(target: scala.Int, samples: scala.Int, internalformat: scala.Int, width: scala.Int, height: scala.Int): scala.Unit = {
    calls = calls + 1
    this.gl30.glRenderbufferStorageMultisample(target, samples, internalformat, width, height)
    this.check()
  }
  @java.lang.Override
  override def glFramebufferTextureLayer(target: scala.Int, attachment: scala.Int, texture: scala.Int, level: scala.Int, layer: scala.Int): scala.Unit = {
    calls = calls + 1
    this.gl30.glFramebufferTextureLayer(target, attachment, texture, level, layer)
    this.check()
  }
  @java.lang.Override
  override def glMapBufferRange(target: scala.Int, offset: scala.Int, length: scala.Int, access: scala.Int): java.nio.Buffer = {
    calls = calls + 1
    val result: java.nio.Buffer = this.gl30.glMapBufferRange(target, offset, length, access)
    this.check()
    return result
  }
  @java.lang.Override
  override def glFlushMappedBufferRange(target: scala.Int, offset: scala.Int, length: scala.Int): scala.Unit = {
    calls = calls + 1
    this.gl30.glFlushMappedBufferRange(target, offset, length)
    this.check()
  }
  @java.lang.Override
  override def glBindVertexArray(array: scala.Int): scala.Unit = {
    calls = calls + 1
    this.gl30.glBindVertexArray(array)
    this.check()
  }
  @java.lang.Override
  override def glDeleteVertexArrays(n: scala.Int, arrays: scala.Array[scala.Int], offset: scala.Int): scala.Unit = {
    calls = calls + 1
    this.gl30.glDeleteVertexArrays(n, arrays, offset)
    this.check()
  }
  @java.lang.Override
  override def glDeleteVertexArrays(n: scala.Int, arrays: java.nio.IntBuffer): scala.Unit = {
    calls = calls + 1
    this.gl30.glDeleteVertexArrays(n, arrays)
    this.check()
  }
  @java.lang.Override
  override def glGenVertexArrays(n: scala.Int, arrays: scala.Array[scala.Int], offset: scala.Int): scala.Unit = {
    calls = calls + 1
    this.gl30.glGenVertexArrays(n, arrays, offset)
    this.check()
  }
  @java.lang.Override
  override def glGenVertexArrays(n: scala.Int, arrays: java.nio.IntBuffer): scala.Unit = {
    calls = calls + 1
    this.gl30.glGenVertexArrays(n, arrays)
    this.check()
  }
  @java.lang.Override
  override def glIsVertexArray(array: scala.Int): scala.Boolean = {
    calls = calls + 1
    val result: scala.Boolean = this.gl30.glIsVertexArray(array)
    this.check()
    return result
  }
  @java.lang.Override
  override def glBeginTransformFeedback(primitiveMode: scala.Int): scala.Unit = {
    calls = calls + 1
    this.gl30.glBeginTransformFeedback(primitiveMode)
    this.check()
  }
  @java.lang.Override
  override def glEndTransformFeedback(): scala.Unit = {
    calls = calls + 1
    this.gl30.glEndTransformFeedback()
    this.check()
  }
  @java.lang.Override
  override def glBindBufferRange(target: scala.Int, index: scala.Int, buffer: scala.Int, offset: scala.Int, size: scala.Int): scala.Unit = {
    calls = calls + 1
    this.gl30.glBindBufferRange(target, index, buffer, offset, size)
    this.check()
  }
  @java.lang.Override
  override def glBindBufferBase(target: scala.Int, index: scala.Int, buffer: scala.Int): scala.Unit = {
    calls = calls + 1
    this.gl30.glBindBufferBase(target, index, buffer)
    this.check()
  }
  @java.lang.Override
  override def glTransformFeedbackVaryings(program: scala.Int, varyings: scala.Array[java.lang.String], bufferMode: scala.Int): scala.Unit = {
    calls = calls + 1
    this.gl30.glTransformFeedbackVaryings(program, varyings, bufferMode)
    this.check()
  }
  @java.lang.Override
  override def glVertexAttribIPointer(index: scala.Int, size: scala.Int, `type`: scala.Int, stride: scala.Int, offset: scala.Int): scala.Unit = {
    calls = calls + 1
    this.gl30.glVertexAttribIPointer(index, size, `type`, stride, offset)
    this.check()
  }
  @java.lang.Override
  override def glGetVertexAttribIiv(index: scala.Int, pname: scala.Int, params: java.nio.IntBuffer): scala.Unit = {
    calls = calls + 1
    this.gl30.glGetVertexAttribIiv(index, pname, params)
    this.check()
  }
  @java.lang.Override
  override def glGetVertexAttribIuiv(index: scala.Int, pname: scala.Int, params: java.nio.IntBuffer): scala.Unit = {
    calls = calls + 1
    this.gl30.glGetVertexAttribIuiv(index, pname, params)
    this.check()
  }
  @java.lang.Override
  override def glVertexAttribI4i(index: scala.Int, x: scala.Int, y: scala.Int, z: scala.Int, w: scala.Int): scala.Unit = {
    calls = calls + 1
    this.gl30.glVertexAttribI4i(index, x, y, z, w)
    this.check()
  }
  @java.lang.Override
  override def glVertexAttribI4ui(index: scala.Int, x: scala.Int, y: scala.Int, z: scala.Int, w: scala.Int): scala.Unit = {
    calls = calls + 1
    this.gl30.glVertexAttribI4ui(index, x, y, z, w)
    this.check()
  }
  @java.lang.Override
  override def glGetUniformuiv(program: scala.Int, location: scala.Int, params: java.nio.IntBuffer): scala.Unit = {
    calls = calls + 1
    this.gl30.glGetUniformuiv(program, location, params)
    this.check()
  }
  @java.lang.Override
  override def glGetFragDataLocation(program: scala.Int, name: java.lang.String): scala.Int = {
    calls = calls + 1
    val result: scala.Int = this.gl30.glGetFragDataLocation(program, name)
    this.check()
    return result
  }
  @java.lang.Override
  override def glUniform1uiv(location: scala.Int, count: scala.Int, value: java.nio.IntBuffer): scala.Unit = {
    calls = calls + 1
    this.gl30.glUniform1uiv(location, count, value)
    this.check()
  }
  @java.lang.Override
  override def glUniform3uiv(location: scala.Int, count: scala.Int, value: java.nio.IntBuffer): scala.Unit = {
    calls = calls + 1
    this.gl30.glUniform3uiv(location, count, value)
    this.check()
  }
  @java.lang.Override
  override def glUniform4uiv(location: scala.Int, count: scala.Int, value: java.nio.IntBuffer): scala.Unit = {
    calls = calls + 1
    this.gl30.glUniform4uiv(location, count, value)
    this.check()
  }
  @java.lang.Override
  override def glClearBufferiv(buffer: scala.Int, drawbuffer: scala.Int, value: java.nio.IntBuffer): scala.Unit = {
    calls = calls + 1
    this.gl30.glClearBufferiv(buffer, drawbuffer, value)
    this.check()
  }
  @java.lang.Override
  override def glClearBufferuiv(buffer: scala.Int, drawbuffer: scala.Int, value: java.nio.IntBuffer): scala.Unit = {
    calls = calls + 1
    this.gl30.glClearBufferuiv(buffer, drawbuffer, value)
    this.check()
  }
  @java.lang.Override
  override def glClearBufferfv(buffer: scala.Int, drawbuffer: scala.Int, value: java.nio.FloatBuffer): scala.Unit = {
    calls = calls + 1
    this.gl30.glClearBufferfv(buffer, drawbuffer, value)
    this.check()
  }
  @java.lang.Override
  override def glClearBufferfi(buffer: scala.Int, drawbuffer: scala.Int, depth: scala.Float, stencil: scala.Int): scala.Unit = {
    calls = calls + 1
    this.gl30.glClearBufferfi(buffer, drawbuffer, depth, stencil)
    this.check()
  }
  @java.lang.Override
  override def glGetStringi(name: scala.Int, index: scala.Int): java.lang.String = {
    calls = calls + 1
    val result: java.lang.String = this.gl30.glGetStringi(name, index)
    this.check()
    return result
  }
  @java.lang.Override
  override def glCopyBufferSubData(readTarget: scala.Int, writeTarget: scala.Int, readOffset: scala.Int, writeOffset: scala.Int, size: scala.Int): scala.Unit = {
    calls = calls + 1
    this.gl30.glCopyBufferSubData(readTarget, writeTarget, readOffset, writeOffset, size)
    this.check()
  }
  @java.lang.Override
  override def glGetUniformIndices(program: scala.Int, uniformNames: scala.Array[java.lang.String], uniformIndices: java.nio.IntBuffer): scala.Unit = {
    calls = calls + 1
    this.gl30.glGetUniformIndices(program, uniformNames, uniformIndices)
    this.check()
  }
  @java.lang.Override
  override def glGetActiveUniformsiv(program: scala.Int, uniformCount: scala.Int, uniformIndices: java.nio.IntBuffer, pname: scala.Int, params: java.nio.IntBuffer): scala.Unit = {
    calls = calls + 1
    this.gl30.glGetActiveUniformsiv(program, uniformCount, uniformIndices, pname, params)
    this.check()
  }
  @java.lang.Override
  override def glGetUniformBlockIndex(program: scala.Int, uniformBlockName: java.lang.String): scala.Int = {
    calls = calls + 1
    val result: scala.Int = this.gl30.glGetUniformBlockIndex(program, uniformBlockName)
    this.check()
    return result
  }
  @java.lang.Override
  override def glGetActiveUniformBlockiv(program: scala.Int, uniformBlockIndex: scala.Int, pname: scala.Int, params: java.nio.IntBuffer): scala.Unit = {
    calls = calls + 1
    this.gl30.glGetActiveUniformBlockiv(program, uniformBlockIndex, pname, params)
    this.check()
  }
  @java.lang.Override
  override def glGetActiveUniformBlockName(program: scala.Int, uniformBlockIndex: scala.Int, length: java.nio.Buffer, uniformBlockName: java.nio.Buffer): scala.Unit = {
    calls = calls + 1
    this.gl30.glGetActiveUniformBlockName(program, uniformBlockIndex, length, uniformBlockName)
    this.check()
  }
  @java.lang.Override
  override def glGetActiveUniformBlockName(program: scala.Int, uniformBlockIndex: scala.Int): java.lang.String = {
    calls = calls + 1
    val result: java.lang.String = this.gl30.glGetActiveUniformBlockName(program, uniformBlockIndex)
    this.check()
    return result
  }
  @java.lang.Override
  override def glUniformBlockBinding(program: scala.Int, uniformBlockIndex: scala.Int, uniformBlockBinding: scala.Int): scala.Unit = {
    calls = calls + 1
    this.gl30.glUniformBlockBinding(program, uniformBlockIndex, uniformBlockBinding)
    this.check()
  }
  @java.lang.Override
  override def glDrawArraysInstanced(mode: scala.Int, first: scala.Int, count: scala.Int, instanceCount: scala.Int): scala.Unit = {
    vertexCount.put(count)
    drawCalls = drawCalls + 1
    calls = calls + 1
    this.gl30.glDrawArraysInstanced(mode, first, count, instanceCount)
    this.check()
  }
  @java.lang.Override
  override def glDrawElementsInstanced(mode: scala.Int, count: scala.Int, `type`: scala.Int, indicesOffset: scala.Int, instanceCount: scala.Int): scala.Unit = {
    vertexCount.put(count)
    drawCalls = drawCalls + 1
    calls = calls + 1
    this.gl30.glDrawElementsInstanced(mode, count, `type`, indicesOffset, instanceCount)
    this.check()
  }
  @java.lang.Override
  override def glGetInteger64v(pname: scala.Int, params: java.nio.LongBuffer): scala.Unit = {
    calls = calls + 1
    this.gl30.glGetInteger64v(pname, params)
    this.check()
  }
  @java.lang.Override
  override def glGetBufferParameteri64v(target: scala.Int, pname: scala.Int, params: java.nio.LongBuffer): scala.Unit = {
    calls = calls + 1
    this.gl30.glGetBufferParameteri64v(target, pname, params)
    this.check()
  }
  @java.lang.Override
  override def glGenSamplers(count: scala.Int, samplers: scala.Array[scala.Int], offset: scala.Int): scala.Unit = {
    calls = calls + 1
    this.gl30.glGenSamplers(count, samplers, offset)
    this.check()
  }
  @java.lang.Override
  override def glGenSamplers(count: scala.Int, samplers: java.nio.IntBuffer): scala.Unit = {
    calls = calls + 1
    this.gl30.glGenSamplers(count, samplers)
    this.check()
  }
  @java.lang.Override
  override def glDeleteSamplers(count: scala.Int, samplers: scala.Array[scala.Int], offset: scala.Int): scala.Unit = {
    calls = calls + 1
    this.gl30.glDeleteSamplers(count, samplers, offset)
    this.check()
  }
  @java.lang.Override
  override def glDeleteSamplers(count: scala.Int, samplers: java.nio.IntBuffer): scala.Unit = {
    calls = calls + 1
    this.gl30.glDeleteSamplers(count, samplers)
    this.check()
  }
  @java.lang.Override
  override def glIsSampler(sampler: scala.Int): scala.Boolean = {
    calls = calls + 1
    val result: scala.Boolean = this.gl30.glIsSampler(sampler)
    this.check()
    return result
  }
  @java.lang.Override
  override def glBindSampler(unit: scala.Int, sampler: scala.Int): scala.Unit = {
    calls = calls + 1
    this.gl30.glBindSampler(unit, sampler)
    this.check()
  }
  @java.lang.Override
  override def glSamplerParameteri(sampler: scala.Int, pname: scala.Int, param: scala.Int): scala.Unit = {
    calls = calls + 1
    this.gl30.glSamplerParameteri(sampler, pname, param)
    this.check()
  }
  @java.lang.Override
  override def glSamplerParameteriv(sampler: scala.Int, pname: scala.Int, param: java.nio.IntBuffer): scala.Unit = {
    calls = calls + 1
    this.gl30.glSamplerParameteriv(sampler, pname, param)
    this.check()
  }
  @java.lang.Override
  override def glSamplerParameterf(sampler: scala.Int, pname: scala.Int, param: scala.Float): scala.Unit = {
    calls = calls + 1
    this.gl30.glSamplerParameterf(sampler, pname, param)
    this.check()
  }
  @java.lang.Override
  override def glSamplerParameterfv(sampler: scala.Int, pname: scala.Int, param: java.nio.FloatBuffer): scala.Unit = {
    calls = calls + 1
    this.gl30.glSamplerParameterfv(sampler, pname, param)
    this.check()
  }
  @java.lang.Override
  override def glGetSamplerParameteriv(sampler: scala.Int, pname: scala.Int, params: java.nio.IntBuffer): scala.Unit = {
    calls = calls + 1
    this.gl30.glGetSamplerParameteriv(sampler, pname, params)
    this.check()
  }
  @java.lang.Override
  override def glGetSamplerParameterfv(sampler: scala.Int, pname: scala.Int, params: java.nio.FloatBuffer): scala.Unit = {
    calls = calls + 1
    this.gl30.glGetSamplerParameterfv(sampler, pname, params)
    this.check()
  }
  @java.lang.Override
  override def glVertexAttribDivisor(index: scala.Int, divisor: scala.Int): scala.Unit = {
    calls = calls + 1
    this.gl30.glVertexAttribDivisor(index, divisor)
    this.check()
  }
  @java.lang.Override
  override def glBindTransformFeedback(target: scala.Int, id: scala.Int): scala.Unit = {
    calls = calls + 1
    this.gl30.glBindTransformFeedback(target, id)
    this.check()
  }
  @java.lang.Override
  override def glDeleteTransformFeedbacks(n: scala.Int, ids: scala.Array[scala.Int], offset: scala.Int): scala.Unit = {
    calls = calls + 1
    this.gl30.glDeleteTransformFeedbacks(n, ids, offset)
    this.check()
  }
  @java.lang.Override
  override def glDeleteTransformFeedbacks(n: scala.Int, ids: java.nio.IntBuffer): scala.Unit = {
    calls = calls + 1
    this.gl30.glDeleteTransformFeedbacks(n, ids)
    this.check()
  }
  @java.lang.Override
  override def glGenTransformFeedbacks(n: scala.Int, ids: scala.Array[scala.Int], offset: scala.Int): scala.Unit = {
    calls = calls + 1
    this.gl30.glGenTransformFeedbacks(n, ids, offset)
    this.check()
  }
  @java.lang.Override
  override def glGenTransformFeedbacks(n: scala.Int, ids: java.nio.IntBuffer): scala.Unit = {
    calls = calls + 1
    this.gl30.glGenTransformFeedbacks(n, ids)
    this.check()
  }
  @java.lang.Override
  override def glIsTransformFeedback(id: scala.Int): scala.Boolean = {
    calls = calls + 1
    val result: scala.Boolean = this.gl30.glIsTransformFeedback(id)
    this.check()
    return result
  }
  @java.lang.Override
  override def glPauseTransformFeedback(): scala.Unit = {
    calls = calls + 1
    this.gl30.glPauseTransformFeedback()
    this.check()
  }
  @java.lang.Override
  override def glResumeTransformFeedback(): scala.Unit = {
    calls = calls + 1
    this.gl30.glResumeTransformFeedback()
    this.check()
  }
  @java.lang.Override
  override def glProgramParameteri(program: scala.Int, pname: scala.Int, value: scala.Int): scala.Unit = {
    calls = calls + 1
    this.gl30.glProgramParameteri(program, pname, value)
    this.check()
  }
  @java.lang.Override
  override def glInvalidateFramebuffer(target: scala.Int, numAttachments: scala.Int, attachments: java.nio.IntBuffer): scala.Unit = {
    calls = calls + 1
    this.gl30.glInvalidateFramebuffer(target, numAttachments, attachments)
    this.check()
  }
  @java.lang.Override
  override def glInvalidateSubFramebuffer(target: scala.Int, numAttachments: scala.Int, attachments: java.nio.IntBuffer, x: scala.Int, y: scala.Int, width: scala.Int, height: scala.Int): scala.Unit = {
    calls = calls + 1
    this.gl30.glInvalidateSubFramebuffer(target, numAttachments, attachments, x, y, width, height)
    this.check()
  }
}
object GL30Interceptor {
  export com.badlogic.gdx.graphics.profiling.GLInterceptor.*
  export com.badlogic.gdx.graphics.GL30.{GL_ACTIVE_ATTRIBUTES => _, GL_ACTIVE_ATTRIBUTE_MAX_LENGTH => _, GL_ACTIVE_TEXTURE => _, GL_ACTIVE_UNIFORMS => _, GL_ACTIVE_UNIFORM_MAX_LENGTH => _, GL_ALIASED_LINE_WIDTH_RANGE => _, GL_ALIASED_POINT_SIZE_RANGE => _, GL_ALPHA => _, GL_ALPHA_BITS => _, GL_ALWAYS => _, GL_ARRAY_BUFFER => _, GL_ARRAY_BUFFER_BINDING => _, GL_ATTACHED_SHADERS => _, GL_BACK => _, GL_BLEND => _, GL_BLEND_COLOR => _, GL_BLEND_DST_ALPHA => _, GL_BLEND_DST_RGB => _, GL_BLEND_EQUATION => _, GL_BLEND_EQUATION_ALPHA => _, GL_BLEND_EQUATION_RGB => _, GL_BLEND_SRC_ALPHA => _, GL_BLEND_SRC_RGB => _, GL_BLUE_BITS => _, GL_BOOL => _, GL_BOOL_VEC2 => _, GL_BOOL_VEC3 => _, GL_BOOL_VEC4 => _, GL_BUFFER_SIZE => _, GL_BUFFER_USAGE => _, GL_BYTE => _, GL_CCW => _, GL_CLAMP_TO_EDGE => _, GL_COLOR_ATTACHMENT0 => _, GL_COLOR_BUFFER_BIT => _, GL_COLOR_CLEAR_VALUE => _, GL_COLOR_WRITEMASK => _, GL_COMPILE_STATUS => _, GL_COMPRESSED_TEXTURE_FORMATS => _, GL_CONSTANT_ALPHA => _, GL_CONSTANT_COLOR => _, GL_COVERAGE_BUFFER_BIT_NV => _, GL_CULL_FACE => _, GL_CULL_FACE_MODE => _, GL_CURRENT_PROGRAM => _, GL_CURRENT_VERTEX_ATTRIB => _, GL_CW => _, GL_DECR => _, GL_DECR_WRAP => _, GL_DELETE_STATUS => _, GL_DEPTH_ATTACHMENT => _, GL_DEPTH_BITS => _, GL_DEPTH_BUFFER_BIT => _, GL_DEPTH_CLEAR_VALUE => _, GL_DEPTH_COMPONENT => _, GL_DEPTH_COMPONENT16 => _, GL_DEPTH_FUNC => _, GL_DEPTH_RANGE => _, GL_DEPTH_TEST => _, GL_DEPTH_WRITEMASK => _, GL_DITHER => _, GL_DONT_CARE => _, GL_DST_ALPHA => _, GL_DST_COLOR => _, GL_DYNAMIC_DRAW => _, GL_ELEMENT_ARRAY_BUFFER => _, GL_ELEMENT_ARRAY_BUFFER_BINDING => _, GL_EQUAL => _, GL_ES_VERSION_2_0 => _, GL_EXTENSIONS => _, GL_FALSE => _, GL_FASTEST => _, GL_FIXED => _, GL_FLOAT => _, GL_FLOAT_MAT2 => _, GL_FLOAT_MAT3 => _, GL_FLOAT_MAT4 => _, GL_FLOAT_VEC2 => _, GL_FLOAT_VEC3 => _, GL_FLOAT_VEC4 => _, GL_FRAGMENT_SHADER => _, GL_FRAMEBUFFER => _, GL_FRAMEBUFFER_ATTACHMENT_OBJECT_NAME => _, GL_FRAMEBUFFER_ATTACHMENT_OBJECT_TYPE => _, GL_FRAMEBUFFER_ATTACHMENT_TEXTURE_CUBE_MAP_FACE => _, GL_FRAMEBUFFER_ATTACHMENT_TEXTURE_LEVEL => _, GL_FRAMEBUFFER_BINDING => _, GL_FRAMEBUFFER_COMPLETE => _, GL_FRAMEBUFFER_INCOMPLETE_ATTACHMENT => _, GL_FRAMEBUFFER_INCOMPLETE_DIMENSIONS => _, GL_FRAMEBUFFER_INCOMPLETE_MISSING_ATTACHMENT => _, GL_FRAMEBUFFER_UNSUPPORTED => _, GL_FRONT => _, GL_FRONT_AND_BACK => _, GL_FRONT_FACE => _, GL_FUNC_ADD => _, GL_FUNC_REVERSE_SUBTRACT => _, GL_FUNC_SUBTRACT => _, GL_GENERATE_MIPMAP => _, GL_GENERATE_MIPMAP_HINT => _, GL_GEQUAL => _, GL_GREATER => _, GL_GREEN_BITS => _, GL_HIGH_FLOAT => _, GL_HIGH_INT => _, GL_IMPLEMENTATION_COLOR_READ_FORMAT => _, GL_IMPLEMENTATION_COLOR_READ_TYPE => _, GL_INCR => _, GL_INCR_WRAP => _, GL_INFO_LOG_LENGTH => _, GL_INT => _, GL_INT_VEC2 => _, GL_INT_VEC3 => _, GL_INT_VEC4 => _, GL_INVALID_ENUM => _, GL_INVALID_FRAMEBUFFER_OPERATION => _, GL_INVALID_OPERATION => _, GL_INVALID_VALUE => _, GL_INVERT => _, GL_KEEP => _, GL_LEQUAL => _, GL_LESS => _, GL_LINEAR => _, GL_LINEAR_MIPMAP_LINEAR => _, GL_LINEAR_MIPMAP_NEAREST => _, GL_LINES => _, GL_LINE_LOOP => _, GL_LINE_STRIP => _, GL_LINE_WIDTH => _, GL_LINK_STATUS => _, GL_LOW_FLOAT => _, GL_LOW_INT => _, GL_LUMINANCE => _, GL_LUMINANCE_ALPHA => _, GL_MAX_COMBINED_TEXTURE_IMAGE_UNITS => _, GL_MAX_CUBE_MAP_TEXTURE_SIZE => _, GL_MAX_FRAGMENT_UNIFORM_VECTORS => _, GL_MAX_RENDERBUFFER_SIZE => _, GL_MAX_TEXTURE_IMAGE_UNITS => _, GL_MAX_TEXTURE_MAX_ANISOTROPY_EXT => _, GL_MAX_TEXTURE_SIZE => _, GL_MAX_TEXTURE_UNITS => _, GL_MAX_VARYING_VECTORS => _, GL_MAX_VERTEX_ATTRIBS => _, GL_MAX_VERTEX_TEXTURE_IMAGE_UNITS => _, GL_MAX_VERTEX_UNIFORM_VECTORS => _, GL_MAX_VIEWPORT_DIMS => _, GL_MEDIUM_FLOAT => _, GL_MEDIUM_INT => _, GL_MIRRORED_REPEAT => _, GL_NEAREST => _, GL_NEAREST_MIPMAP_LINEAR => _, GL_NEAREST_MIPMAP_NEAREST => _, GL_NEVER => _, GL_NICEST => _, GL_NONE => _, GL_NOTEQUAL => _, GL_NO_ERROR => _, GL_NUM_COMPRESSED_TEXTURE_FORMATS => _, GL_NUM_SHADER_BINARY_FORMATS => _, GL_ONE => _, GL_ONE_MINUS_CONSTANT_ALPHA => _, GL_ONE_MINUS_CONSTANT_COLOR => _, GL_ONE_MINUS_DST_ALPHA => _, GL_ONE_MINUS_DST_COLOR => _, GL_ONE_MINUS_SRC_ALPHA => _, GL_ONE_MINUS_SRC_COLOR => _, GL_OUT_OF_MEMORY => _, GL_PACK_ALIGNMENT => _, GL_POINTS => _, GL_POLYGON_OFFSET_FACTOR => _, GL_POLYGON_OFFSET_FILL => _, GL_POLYGON_OFFSET_UNITS => _, GL_RED_BITS => _, GL_RENDERBUFFER => _, GL_RENDERBUFFER_ALPHA_SIZE => _, GL_RENDERBUFFER_BINDING => _, GL_RENDERBUFFER_BLUE_SIZE => _, GL_RENDERBUFFER_DEPTH_SIZE => _, GL_RENDERBUFFER_GREEN_SIZE => _, GL_RENDERBUFFER_HEIGHT => _, GL_RENDERBUFFER_INTERNAL_FORMAT => _, GL_RENDERBUFFER_RED_SIZE => _, GL_RENDERBUFFER_STENCIL_SIZE => _, GL_RENDERBUFFER_WIDTH => _, GL_RENDERER => _, GL_REPEAT => _, GL_REPLACE => _, GL_RGB => _, GL_RGB565 => _, GL_RGB5_A1 => _, GL_RGBA => _, GL_RGBA4 => _, GL_SAMPLER_2D => _, GL_SAMPLER_CUBE => _, GL_SAMPLES => _, GL_SAMPLE_ALPHA_TO_COVERAGE => _, GL_SAMPLE_BUFFERS => _, GL_SAMPLE_COVERAGE => _, GL_SAMPLE_COVERAGE_INVERT => _, GL_SAMPLE_COVERAGE_VALUE => _, GL_SCISSOR_BOX => _, GL_SCISSOR_TEST => _, GL_SHADER_BINARY_FORMATS => _, GL_SHADER_COMPILER => _, GL_SHADER_SOURCE_LENGTH => _, GL_SHADER_TYPE => _, GL_SHADING_LANGUAGE_VERSION => _, GL_SHORT => _, GL_SRC_ALPHA => _, GL_SRC_ALPHA_SATURATE => _, GL_SRC_COLOR => _, GL_STATIC_DRAW => _, GL_STENCIL_ATTACHMENT => _, GL_STENCIL_BACK_FAIL => _, GL_STENCIL_BACK_FUNC => _, GL_STENCIL_BACK_PASS_DEPTH_FAIL => _, GL_STENCIL_BACK_PASS_DEPTH_PASS => _, GL_STENCIL_BACK_REF => _, GL_STENCIL_BACK_VALUE_MASK => _, GL_STENCIL_BACK_WRITEMASK => _, GL_STENCIL_BITS => _, GL_STENCIL_BUFFER_BIT => _, GL_STENCIL_CLEAR_VALUE => _, GL_STENCIL_FAIL => _, GL_STENCIL_FUNC => _, GL_STENCIL_INDEX => _, GL_STENCIL_INDEX8 => _, GL_STENCIL_PASS_DEPTH_FAIL => _, GL_STENCIL_PASS_DEPTH_PASS => _, GL_STENCIL_REF => _, GL_STENCIL_TEST => _, GL_STENCIL_VALUE_MASK => _, GL_STENCIL_WRITEMASK => _, GL_STREAM_DRAW => _, GL_SUBPIXEL_BITS => _, GL_TEXTURE => _, GL_TEXTURE0 => _, GL_TEXTURE1 => _, GL_TEXTURE10 => _, GL_TEXTURE11 => _, GL_TEXTURE12 => _, GL_TEXTURE13 => _, GL_TEXTURE14 => _, GL_TEXTURE15 => _, GL_TEXTURE16 => _, GL_TEXTURE17 => _, GL_TEXTURE18 => _, GL_TEXTURE19 => _, GL_TEXTURE2 => _, GL_TEXTURE20 => _, GL_TEXTURE21 => _, GL_TEXTURE22 => _, GL_TEXTURE23 => _, GL_TEXTURE24 => _, GL_TEXTURE25 => _, GL_TEXTURE26 => _, GL_TEXTURE27 => _, GL_TEXTURE28 => _, GL_TEXTURE29 => _, GL_TEXTURE3 => _, GL_TEXTURE30 => _, GL_TEXTURE31 => _, GL_TEXTURE4 => _, GL_TEXTURE5 => _, GL_TEXTURE6 => _, GL_TEXTURE7 => _, GL_TEXTURE8 => _, GL_TEXTURE9 => _, GL_TEXTURE_2D => _, GL_TEXTURE_BINDING_2D => _, GL_TEXTURE_BINDING_CUBE_MAP => _, GL_TEXTURE_CUBE_MAP => _, GL_TEXTURE_CUBE_MAP_NEGATIVE_X => _, GL_TEXTURE_CUBE_MAP_NEGATIVE_Y => _, GL_TEXTURE_CUBE_MAP_NEGATIVE_Z => _, GL_TEXTURE_CUBE_MAP_POSITIVE_X => _, GL_TEXTURE_CUBE_MAP_POSITIVE_Y => _, GL_TEXTURE_CUBE_MAP_POSITIVE_Z => _, GL_TEXTURE_MAG_FILTER => _, GL_TEXTURE_MAX_ANISOTROPY_EXT => _, GL_TEXTURE_MIN_FILTER => _, GL_TEXTURE_WRAP_S => _, GL_TEXTURE_WRAP_T => _, GL_TRIANGLES => _, GL_TRIANGLE_FAN => _, GL_TRIANGLE_STRIP => _, GL_TRUE => _, GL_UNPACK_ALIGNMENT => _, GL_UNSIGNED_BYTE => _, GL_UNSIGNED_INT => _, GL_UNSIGNED_SHORT => _, GL_UNSIGNED_SHORT_4_4_4_4 => _, GL_UNSIGNED_SHORT_5_5_5_1 => _, GL_UNSIGNED_SHORT_5_6_5 => _, GL_VALIDATE_STATUS => _, GL_VENDOR => _, GL_VERSION => _, GL_VERTEX_ATTRIB_ARRAY_BUFFER_BINDING => _, GL_VERTEX_ATTRIB_ARRAY_ENABLED => _, GL_VERTEX_ATTRIB_ARRAY_NORMALIZED => _, GL_VERTEX_ATTRIB_ARRAY_POINTER => _, GL_VERTEX_ATTRIB_ARRAY_SIZE => _, GL_VERTEX_ATTRIB_ARRAY_STRIDE => _, GL_VERTEX_ATTRIB_ARRAY_TYPE => _, GL_VERTEX_PROGRAM_POINT_SIZE => _, GL_VERTEX_SHADER => _, GL_VIEWPORT => _, GL_ZERO => _, *}
}