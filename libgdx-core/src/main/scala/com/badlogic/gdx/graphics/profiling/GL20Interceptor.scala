package com.badlogic.gdx.graphics.profiling

class GL20Interceptor(glProfiler$p: com.badlogic.gdx.graphics.profiling.GLProfiler, gl20$p: com.badlogic.gdx.graphics.GL20) extends com.badlogic.gdx.graphics.profiling.GLInterceptor(glProfiler$p) with com.badlogic.gdx.graphics.GL20 {
  var gl20: com.badlogic.gdx.graphics.GL20 = null.asInstanceOf[com.badlogic.gdx.graphics.GL20]
  this.gl20 = gl20$p
  private def check(): scala.Unit = {
    var error: scala.Int = this.gl20.glGetError()
    while (error != com.badlogic.gdx.graphics.GL20.GL_NO_ERROR) {
      glProfiler.getListener().onError(error)
      error = this.gl20.glGetError()
    }
  }
  @java.lang.Override
  override def glActiveTexture(texture: scala.Int): scala.Unit = {
    calls = calls + 1
    this.gl20.glActiveTexture(texture)
    this.check()
  }
  @java.lang.Override
  override def glBindTexture(target: scala.Int, texture: scala.Int): scala.Unit = {
    textureBindings = textureBindings + 1
    calls = calls + 1
    this.gl20.glBindTexture(target, texture)
    this.check()
  }
  @java.lang.Override
  override def glBlendFunc(sfactor: scala.Int, dfactor: scala.Int): scala.Unit = {
    calls = calls + 1
    this.gl20.glBlendFunc(sfactor, dfactor)
    this.check()
  }
  @java.lang.Override
  override def glClear(mask: scala.Int): scala.Unit = {
    calls = calls + 1
    this.gl20.glClear(mask)
    this.check()
  }
  @java.lang.Override
  override def glClearColor(red: scala.Float, green: scala.Float, blue: scala.Float, alpha: scala.Float): scala.Unit = {
    calls = calls + 1
    this.gl20.glClearColor(red, green, blue, alpha)
    this.check()
  }
  @java.lang.Override
  override def glClearDepthf(depth: scala.Float): scala.Unit = {
    calls = calls + 1
    this.gl20.glClearDepthf(depth)
    this.check()
  }
  @java.lang.Override
  override def glClearStencil(s: scala.Int): scala.Unit = {
    calls = calls + 1
    this.gl20.glClearStencil(s)
    this.check()
  }
  @java.lang.Override
  override def glColorMask(red: scala.Boolean, green: scala.Boolean, blue: scala.Boolean, alpha: scala.Boolean): scala.Unit = {
    calls = calls + 1
    this.gl20.glColorMask(red, green, blue, alpha)
    this.check()
  }
  @java.lang.Override
  override def glCompressedTexImage2D(target: scala.Int, level: scala.Int, internalformat: scala.Int, width: scala.Int, height: scala.Int, border: scala.Int, imageSize: scala.Int, data: java.nio.Buffer): scala.Unit = {
    calls = calls + 1
    this.gl20.glCompressedTexImage2D(target, level, internalformat, width, height, border, imageSize, data)
    this.check()
  }
  @java.lang.Override
  override def glCompressedTexSubImage2D(target: scala.Int, level: scala.Int, xoffset: scala.Int, yoffset: scala.Int, width: scala.Int, height: scala.Int, format: scala.Int, imageSize: scala.Int, data: java.nio.Buffer): scala.Unit = {
    calls = calls + 1
    this.gl20.glCompressedTexSubImage2D(target, level, xoffset, yoffset, width, height, format, imageSize, data)
    this.check()
  }
  @java.lang.Override
  override def glCopyTexImage2D(target: scala.Int, level: scala.Int, internalformat: scala.Int, x: scala.Int, y: scala.Int, width: scala.Int, height: scala.Int, border: scala.Int): scala.Unit = {
    calls = calls + 1
    this.gl20.glCopyTexImage2D(target, level, internalformat, x, y, width, height, border)
    this.check()
  }
  @java.lang.Override
  override def glCopyTexSubImage2D(target: scala.Int, level: scala.Int, xoffset: scala.Int, yoffset: scala.Int, x: scala.Int, y: scala.Int, width: scala.Int, height: scala.Int): scala.Unit = {
    calls = calls + 1
    this.gl20.glCopyTexSubImage2D(target, level, xoffset, yoffset, x, y, width, height)
    this.check()
  }
  @java.lang.Override
  override def glCullFace(mode: scala.Int): scala.Unit = {
    calls = calls + 1
    this.gl20.glCullFace(mode)
    this.check()
  }
  @java.lang.Override
  override def glDeleteTextures(n: scala.Int, textures: java.nio.IntBuffer): scala.Unit = {
    calls = calls + 1
    this.gl20.glDeleteTextures(n, textures)
    this.check()
  }
  @java.lang.Override
  override def glDeleteTexture(texture: scala.Int): scala.Unit = {
    calls = calls + 1
    this.gl20.glDeleteTexture(texture)
    this.check()
  }
  @java.lang.Override
  override def glDepthFunc(func: scala.Int): scala.Unit = {
    calls = calls + 1
    this.gl20.glDepthFunc(func)
    this.check()
  }
  @java.lang.Override
  override def glDepthMask(flag: scala.Boolean): scala.Unit = {
    calls = calls + 1
    this.gl20.glDepthMask(flag)
    this.check()
  }
  @java.lang.Override
  override def glDepthRangef(zNear: scala.Float, zFar: scala.Float): scala.Unit = {
    calls = calls + 1
    this.gl20.glDepthRangef(zNear, zFar)
    this.check()
  }
  @java.lang.Override
  override def glDisable(cap: scala.Int): scala.Unit = {
    calls = calls + 1
    this.gl20.glDisable(cap)
    this.check()
  }
  @java.lang.Override
  override def glDrawArrays(mode: scala.Int, first: scala.Int, count: scala.Int): scala.Unit = {
    vertexCount.put(count)
    drawCalls = drawCalls + 1
    calls = calls + 1
    this.gl20.glDrawArrays(mode, first, count)
    this.check()
  }
  @java.lang.Override
  override def glDrawElements(mode: scala.Int, count: scala.Int, `type`: scala.Int, indices: java.nio.Buffer): scala.Unit = {
    vertexCount.put(count)
    drawCalls = drawCalls + 1
    calls = calls + 1
    this.gl20.glDrawElements(mode, count, `type`, indices)
    this.check()
  }
  @java.lang.Override
  override def glEnable(cap: scala.Int): scala.Unit = {
    calls = calls + 1
    this.gl20.glEnable(cap)
    this.check()
  }
  @java.lang.Override
  override def glFinish(): scala.Unit = {
    calls = calls + 1
    this.gl20.glFinish()
    this.check()
  }
  @java.lang.Override
  override def glFlush(): scala.Unit = {
    calls = calls + 1
    this.gl20.glFlush()
    this.check()
  }
  @java.lang.Override
  override def glFrontFace(mode: scala.Int): scala.Unit = {
    calls = calls + 1
    this.gl20.glFrontFace(mode)
    this.check()
  }
  @java.lang.Override
  override def glGenTextures(n: scala.Int, textures: java.nio.IntBuffer): scala.Unit = {
    calls = calls + 1
    this.gl20.glGenTextures(n, textures)
    this.check()
  }
  @java.lang.Override
  override def glGenTexture(): scala.Int = {
    calls = calls + 1
    val result: scala.Int = this.gl20.glGenTexture()
    this.check()
    return result
  }
  @java.lang.Override
  override def glGetError(): scala.Int = {
    calls = calls + 1
    return this.gl20.glGetError()
  }
  @java.lang.Override
  override def glGetIntegerv(pname: scala.Int, params: java.nio.IntBuffer): scala.Unit = {
    calls = calls + 1
    this.gl20.glGetIntegerv(pname, params)
    this.check()
  }
  @java.lang.Override
  override def glGetString(name: scala.Int): java.lang.String = {
    calls = calls + 1
    val result: java.lang.String = this.gl20.glGetString(name)
    this.check()
    return result
  }
  @java.lang.Override
  override def glHint(target: scala.Int, mode: scala.Int): scala.Unit = {
    calls = calls + 1
    this.gl20.glHint(target, mode)
    this.check()
  }
  @java.lang.Override
  override def glLineWidth(width: scala.Float): scala.Unit = {
    calls = calls + 1
    this.gl20.glLineWidth(width)
    this.check()
  }
  @java.lang.Override
  override def glPixelStorei(pname: scala.Int, param: scala.Int): scala.Unit = {
    calls = calls + 1
    this.gl20.glPixelStorei(pname, param)
    this.check()
  }
  @java.lang.Override
  override def glPolygonOffset(factor: scala.Float, units: scala.Float): scala.Unit = {
    calls = calls + 1
    this.gl20.glPolygonOffset(factor, units)
    this.check()
  }
  @java.lang.Override
  override def glReadPixels(x: scala.Int, y: scala.Int, width: scala.Int, height: scala.Int, format: scala.Int, `type`: scala.Int, pixels: java.nio.Buffer): scala.Unit = {
    calls = calls + 1
    this.gl20.glReadPixels(x, y, width, height, format, `type`, pixels)
    this.check()
  }
  @java.lang.Override
  override def glScissor(x: scala.Int, y: scala.Int, width: scala.Int, height: scala.Int): scala.Unit = {
    calls = calls + 1
    this.gl20.glScissor(x, y, width, height)
    this.check()
  }
  @java.lang.Override
  override def glStencilFunc(func: scala.Int, ref: scala.Int, mask: scala.Int): scala.Unit = {
    calls = calls + 1
    this.gl20.glStencilFunc(func, ref, mask)
    this.check()
  }
  @java.lang.Override
  override def glStencilMask(mask: scala.Int): scala.Unit = {
    calls = calls + 1
    this.gl20.glStencilMask(mask)
    this.check()
  }
  @java.lang.Override
  override def glStencilOp(fail: scala.Int, zfail: scala.Int, zpass: scala.Int): scala.Unit = {
    calls = calls + 1
    this.gl20.glStencilOp(fail, zfail, zpass)
    this.check()
  }
  @java.lang.Override
  override def glTexImage2D(target: scala.Int, level: scala.Int, internalformat: scala.Int, width: scala.Int, height: scala.Int, border: scala.Int, format: scala.Int, `type`: scala.Int, pixels: java.nio.Buffer): scala.Unit = {
    calls = calls + 1
    this.gl20.glTexImage2D(target, level, internalformat, width, height, border, format, `type`, pixels)
    this.check()
  }
  @java.lang.Override
  override def glTexParameterf(target: scala.Int, pname: scala.Int, param: scala.Float): scala.Unit = {
    calls = calls + 1
    this.gl20.glTexParameterf(target, pname, param)
    this.check()
  }
  @java.lang.Override
  override def glTexSubImage2D(target: scala.Int, level: scala.Int, xoffset: scala.Int, yoffset: scala.Int, width: scala.Int, height: scala.Int, format: scala.Int, `type`: scala.Int, pixels: java.nio.Buffer): scala.Unit = {
    calls = calls + 1
    this.gl20.glTexSubImage2D(target, level, xoffset, yoffset, width, height, format, `type`, pixels)
    this.check()
  }
  @java.lang.Override
  override def glViewport(x: scala.Int, y: scala.Int, width: scala.Int, height: scala.Int): scala.Unit = {
    calls = calls + 1
    this.gl20.glViewport(x, y, width, height)
    this.check()
  }
  @java.lang.Override
  override def glAttachShader(program: scala.Int, shader: scala.Int): scala.Unit = {
    calls = calls + 1
    this.gl20.glAttachShader(program, shader)
    this.check()
  }
  @java.lang.Override
  override def glBindAttribLocation(program: scala.Int, index: scala.Int, name: java.lang.String): scala.Unit = {
    calls = calls + 1
    this.gl20.glBindAttribLocation(program, index, name)
    this.check()
  }
  @java.lang.Override
  override def glBindBuffer(target: scala.Int, buffer: scala.Int): scala.Unit = {
    calls = calls + 1
    this.gl20.glBindBuffer(target, buffer)
    this.check()
  }
  @java.lang.Override
  override def glBindFramebuffer(target: scala.Int, framebuffer: scala.Int): scala.Unit = {
    calls = calls + 1
    this.gl20.glBindFramebuffer(target, framebuffer)
    this.check()
  }
  @java.lang.Override
  override def glBindRenderbuffer(target: scala.Int, renderbuffer: scala.Int): scala.Unit = {
    calls = calls + 1
    this.gl20.glBindRenderbuffer(target, renderbuffer)
    this.check()
  }
  @java.lang.Override
  override def glBlendColor(red: scala.Float, green: scala.Float, blue: scala.Float, alpha: scala.Float): scala.Unit = {
    calls = calls + 1
    this.gl20.glBlendColor(red, green, blue, alpha)
    this.check()
  }
  @java.lang.Override
  override def glBlendEquation(mode: scala.Int): scala.Unit = {
    calls = calls + 1
    this.gl20.glBlendEquation(mode)
    this.check()
  }
  @java.lang.Override
  override def glBlendEquationSeparate(modeRGB: scala.Int, modeAlpha: scala.Int): scala.Unit = {
    calls = calls + 1
    this.gl20.glBlendEquationSeparate(modeRGB, modeAlpha)
    this.check()
  }
  @java.lang.Override
  override def glBlendFuncSeparate(srcRGB: scala.Int, dstRGB: scala.Int, srcAlpha: scala.Int, dstAlpha: scala.Int): scala.Unit = {
    calls = calls + 1
    this.gl20.glBlendFuncSeparate(srcRGB, dstRGB, srcAlpha, dstAlpha)
    this.check()
  }
  @java.lang.Override
  override def glBufferData(target: scala.Int, size: scala.Int, data: java.nio.Buffer, usage: scala.Int): scala.Unit = {
    calls = calls + 1
    this.gl20.glBufferData(target, size, data, usage)
    this.check()
  }
  @java.lang.Override
  override def glBufferSubData(target: scala.Int, offset: scala.Int, size: scala.Int, data: java.nio.Buffer): scala.Unit = {
    calls = calls + 1
    this.gl20.glBufferSubData(target, offset, size, data)
    this.check()
  }
  @java.lang.Override
  override def glCheckFramebufferStatus(target: scala.Int): scala.Int = {
    calls = calls + 1
    val result: scala.Int = this.gl20.glCheckFramebufferStatus(target)
    this.check()
    return result
  }
  @java.lang.Override
  override def glCompileShader(shader: scala.Int): scala.Unit = {
    calls = calls + 1
    this.gl20.glCompileShader(shader)
    this.check()
  }
  @java.lang.Override
  override def glCreateProgram(): scala.Int = {
    calls = calls + 1
    val result: scala.Int = this.gl20.glCreateProgram()
    this.check()
    return result
  }
  @java.lang.Override
  override def glCreateShader(`type`: scala.Int): scala.Int = {
    calls = calls + 1
    val result: scala.Int = this.gl20.glCreateShader(`type`)
    this.check()
    return result
  }
  @java.lang.Override
  override def glDeleteBuffer(buffer: scala.Int): scala.Unit = {
    calls = calls + 1
    this.gl20.glDeleteBuffer(buffer)
    this.check()
  }
  @java.lang.Override
  override def glDeleteBuffers(n: scala.Int, buffers: java.nio.IntBuffer): scala.Unit = {
    calls = calls + 1
    this.gl20.glDeleteBuffers(n, buffers)
    this.check()
  }
  @java.lang.Override
  override def glDeleteFramebuffer(framebuffer: scala.Int): scala.Unit = {
    calls = calls + 1
    this.gl20.glDeleteFramebuffer(framebuffer)
    this.check()
  }
  @java.lang.Override
  override def glDeleteFramebuffers(n: scala.Int, framebuffers: java.nio.IntBuffer): scala.Unit = {
    calls = calls + 1
    this.gl20.glDeleteFramebuffers(n, framebuffers)
    this.check()
  }
  @java.lang.Override
  override def glDeleteProgram(program: scala.Int): scala.Unit = {
    calls = calls + 1
    this.gl20.glDeleteProgram(program)
    this.check()
  }
  @java.lang.Override
  override def glDeleteRenderbuffer(renderbuffer: scala.Int): scala.Unit = {
    calls = calls + 1
    this.gl20.glDeleteRenderbuffer(renderbuffer)
    this.check()
  }
  @java.lang.Override
  override def glDeleteRenderbuffers(n: scala.Int, renderbuffers: java.nio.IntBuffer): scala.Unit = {
    calls = calls + 1
    this.gl20.glDeleteRenderbuffers(n, renderbuffers)
    this.check()
  }
  @java.lang.Override
  override def glDeleteShader(shader: scala.Int): scala.Unit = {
    calls = calls + 1
    this.gl20.glDeleteShader(shader)
    this.check()
  }
  @java.lang.Override
  override def glDetachShader(program: scala.Int, shader: scala.Int): scala.Unit = {
    calls = calls + 1
    this.gl20.glDetachShader(program, shader)
    this.check()
  }
  @java.lang.Override
  override def glDisableVertexAttribArray(index: scala.Int): scala.Unit = {
    calls = calls + 1
    this.gl20.glDisableVertexAttribArray(index)
    this.check()
  }
  @java.lang.Override
  override def glDrawElements(mode: scala.Int, count: scala.Int, `type`: scala.Int, indices: scala.Int): scala.Unit = {
    vertexCount.put(count)
    drawCalls = drawCalls + 1
    calls = calls + 1
    this.gl20.glDrawElements(mode, count, `type`, indices)
    this.check()
  }
  @java.lang.Override
  override def glEnableVertexAttribArray(index: scala.Int): scala.Unit = {
    calls = calls + 1
    this.gl20.glEnableVertexAttribArray(index)
    this.check()
  }
  @java.lang.Override
  override def glFramebufferRenderbuffer(target: scala.Int, attachment: scala.Int, renderbuffertarget: scala.Int, renderbuffer: scala.Int): scala.Unit = {
    calls = calls + 1
    this.gl20.glFramebufferRenderbuffer(target, attachment, renderbuffertarget, renderbuffer)
    this.check()
  }
  @java.lang.Override
  override def glFramebufferTexture2D(target: scala.Int, attachment: scala.Int, textarget: scala.Int, texture: scala.Int, level: scala.Int): scala.Unit = {
    calls = calls + 1
    this.gl20.glFramebufferTexture2D(target, attachment, textarget, texture, level)
    this.check()
  }
  @java.lang.Override
  override def glGenBuffer(): scala.Int = {
    calls = calls + 1
    val result: scala.Int = this.gl20.glGenBuffer()
    this.check()
    return result
  }
  @java.lang.Override
  override def glGenBuffers(n: scala.Int, buffers: java.nio.IntBuffer): scala.Unit = {
    calls = calls + 1
    this.gl20.glGenBuffers(n, buffers)
    this.check()
  }
  @java.lang.Override
  override def glGenerateMipmap(target: scala.Int): scala.Unit = {
    calls = calls + 1
    this.gl20.glGenerateMipmap(target)
    this.check()
  }
  @java.lang.Override
  override def glGenFramebuffer(): scala.Int = {
    calls = calls + 1
    val result: scala.Int = this.gl20.glGenFramebuffer()
    this.check()
    return result
  }
  @java.lang.Override
  override def glGenFramebuffers(n: scala.Int, framebuffers: java.nio.IntBuffer): scala.Unit = {
    calls = calls + 1
    this.gl20.glGenFramebuffers(n, framebuffers)
    this.check()
  }
  @java.lang.Override
  override def glGenRenderbuffer(): scala.Int = {
    calls = calls + 1
    val result: scala.Int = this.gl20.glGenRenderbuffer()
    this.check()
    return result
  }
  @java.lang.Override
  override def glGenRenderbuffers(n: scala.Int, renderbuffers: java.nio.IntBuffer): scala.Unit = {
    calls = calls + 1
    this.gl20.glGenRenderbuffers(n, renderbuffers)
    this.check()
  }
  @java.lang.Override
  override def glGetActiveAttrib(program: scala.Int, index: scala.Int, size: java.nio.IntBuffer, `type`: java.nio.IntBuffer): java.lang.String = {
    calls = calls + 1
    val result: java.lang.String = this.gl20.glGetActiveAttrib(program, index, size, `type`)
    this.check()
    return result
  }
  @java.lang.Override
  override def glGetActiveUniform(program: scala.Int, index: scala.Int, size: java.nio.IntBuffer, `type`: java.nio.IntBuffer): java.lang.String = {
    calls = calls + 1
    val result: java.lang.String = this.gl20.glGetActiveUniform(program, index, size, `type`)
    this.check()
    return result
  }
  @java.lang.Override
  override def glGetAttachedShaders(program: scala.Int, maxcount: scala.Int, count: java.nio.Buffer, shaders: java.nio.IntBuffer): scala.Unit = {
    calls = calls + 1
    this.gl20.glGetAttachedShaders(program, maxcount, count, shaders)
    this.check()
  }
  @java.lang.Override
  override def glGetAttribLocation(program: scala.Int, name: java.lang.String): scala.Int = {
    calls = calls + 1
    val result: scala.Int = this.gl20.glGetAttribLocation(program, name)
    this.check()
    return result
  }
  @java.lang.Override
  override def glGetBooleanv(pname: scala.Int, params: java.nio.Buffer): scala.Unit = {
    calls = calls + 1
    this.gl20.glGetBooleanv(pname, params)
    this.check()
  }
  @java.lang.Override
  override def glGetBufferParameteriv(target: scala.Int, pname: scala.Int, params: java.nio.IntBuffer): scala.Unit = {
    calls = calls + 1
    this.gl20.glGetBufferParameteriv(target, pname, params)
    this.check()
  }
  @java.lang.Override
  override def glGetFloatv(pname: scala.Int, params: java.nio.FloatBuffer): scala.Unit = {
    calls = calls + 1
    this.gl20.glGetFloatv(pname, params)
    this.check()
  }
  @java.lang.Override
  override def glGetFramebufferAttachmentParameteriv(target: scala.Int, attachment: scala.Int, pname: scala.Int, params: java.nio.IntBuffer): scala.Unit = {
    calls = calls + 1
    this.gl20.glGetFramebufferAttachmentParameteriv(target, attachment, pname, params)
    this.check()
  }
  @java.lang.Override
  override def glGetProgramiv(program: scala.Int, pname: scala.Int, params: java.nio.IntBuffer): scala.Unit = {
    calls = calls + 1
    this.gl20.glGetProgramiv(program, pname, params)
    this.check()
  }
  @java.lang.Override
  override def glGetProgramInfoLog(program: scala.Int): java.lang.String = {
    calls = calls + 1
    val result: java.lang.String = this.gl20.glGetProgramInfoLog(program)
    this.check()
    return result
  }
  @java.lang.Override
  override def glGetRenderbufferParameteriv(target: scala.Int, pname: scala.Int, params: java.nio.IntBuffer): scala.Unit = {
    calls = calls + 1
    this.gl20.glGetRenderbufferParameteriv(target, pname, params)
    this.check()
  }
  @java.lang.Override
  override def glGetShaderiv(shader: scala.Int, pname: scala.Int, params: java.nio.IntBuffer): scala.Unit = {
    calls = calls + 1
    this.gl20.glGetShaderiv(shader, pname, params)
    this.check()
  }
  @java.lang.Override
  override def glGetShaderInfoLog(shader: scala.Int): java.lang.String = {
    calls = calls + 1
    val result: java.lang.String = this.gl20.glGetShaderInfoLog(shader)
    this.check()
    return result
  }
  @java.lang.Override
  override def glGetShaderPrecisionFormat(shadertype: scala.Int, precisiontype: scala.Int, range: java.nio.IntBuffer, precision: java.nio.IntBuffer): scala.Unit = {
    calls = calls + 1
    this.gl20.glGetShaderPrecisionFormat(shadertype, precisiontype, range, precision)
    this.check()
  }
  @java.lang.Override
  override def glGetTexParameterfv(target: scala.Int, pname: scala.Int, params: java.nio.FloatBuffer): scala.Unit = {
    calls = calls + 1
    this.gl20.glGetTexParameterfv(target, pname, params)
    this.check()
  }
  @java.lang.Override
  override def glGetTexParameteriv(target: scala.Int, pname: scala.Int, params: java.nio.IntBuffer): scala.Unit = {
    calls = calls + 1
    this.gl20.glGetTexParameteriv(target, pname, params)
    this.check()
  }
  @java.lang.Override
  override def glGetUniformfv(program: scala.Int, location: scala.Int, params: java.nio.FloatBuffer): scala.Unit = {
    calls = calls + 1
    this.gl20.glGetUniformfv(program, location, params)
    this.check()
  }
  @java.lang.Override
  override def glGetUniformiv(program: scala.Int, location: scala.Int, params: java.nio.IntBuffer): scala.Unit = {
    calls = calls + 1
    this.gl20.glGetUniformiv(program, location, params)
    this.check()
  }
  @java.lang.Override
  override def glGetUniformLocation(program: scala.Int, name: java.lang.String): scala.Int = {
    calls = calls + 1
    val result: scala.Int = this.gl20.glGetUniformLocation(program, name)
    this.check()
    return result
  }
  @java.lang.Override
  override def glGetVertexAttribfv(index: scala.Int, pname: scala.Int, params: java.nio.FloatBuffer): scala.Unit = {
    calls = calls + 1
    this.gl20.glGetVertexAttribfv(index, pname, params)
    this.check()
  }
  @java.lang.Override
  override def glGetVertexAttribiv(index: scala.Int, pname: scala.Int, params: java.nio.IntBuffer): scala.Unit = {
    calls = calls + 1
    this.gl20.glGetVertexAttribiv(index, pname, params)
    this.check()
  }
  @java.lang.Override
  override def glGetVertexAttribPointerv(index: scala.Int, pname: scala.Int, pointer: java.nio.Buffer): scala.Unit = {
    calls = calls + 1
    this.gl20.glGetVertexAttribPointerv(index, pname, pointer)
    this.check()
  }
  @java.lang.Override
  override def glIsBuffer(buffer: scala.Int): scala.Boolean = {
    calls = calls + 1
    val result: scala.Boolean = this.gl20.glIsBuffer(buffer)
    this.check()
    return result
  }
  @java.lang.Override
  override def glIsEnabled(cap: scala.Int): scala.Boolean = {
    calls = calls + 1
    val result: scala.Boolean = this.gl20.glIsEnabled(cap)
    this.check()
    return result
  }
  @java.lang.Override
  override def glIsFramebuffer(framebuffer: scala.Int): scala.Boolean = {
    calls = calls + 1
    val result: scala.Boolean = this.gl20.glIsFramebuffer(framebuffer)
    this.check()
    return result
  }
  @java.lang.Override
  override def glIsProgram(program: scala.Int): scala.Boolean = {
    calls = calls + 1
    val result: scala.Boolean = this.gl20.glIsProgram(program)
    this.check()
    return result
  }
  @java.lang.Override
  override def glIsRenderbuffer(renderbuffer: scala.Int): scala.Boolean = {
    calls = calls + 1
    val result: scala.Boolean = this.gl20.glIsRenderbuffer(renderbuffer)
    this.check()
    return result
  }
  @java.lang.Override
  override def glIsShader(shader: scala.Int): scala.Boolean = {
    calls = calls + 1
    val result: scala.Boolean = this.gl20.glIsShader(shader)
    this.check()
    return result
  }
  @java.lang.Override
  override def glIsTexture(texture: scala.Int): scala.Boolean = {
    calls = calls + 1
    val result: scala.Boolean = this.gl20.glIsTexture(texture)
    this.check()
    return result
  }
  @java.lang.Override
  override def glLinkProgram(program: scala.Int): scala.Unit = {
    calls = calls + 1
    this.gl20.glLinkProgram(program)
    this.check()
  }
  @java.lang.Override
  override def glReleaseShaderCompiler(): scala.Unit = {
    calls = calls + 1
    this.gl20.glReleaseShaderCompiler()
    this.check()
  }
  @java.lang.Override
  override def glRenderbufferStorage(target: scala.Int, internalformat: scala.Int, width: scala.Int, height: scala.Int): scala.Unit = {
    calls = calls + 1
    this.gl20.glRenderbufferStorage(target, internalformat, width, height)
    this.check()
  }
  @java.lang.Override
  override def glSampleCoverage(value: scala.Float, invert: scala.Boolean): scala.Unit = {
    calls = calls + 1
    this.gl20.glSampleCoverage(value, invert)
    this.check()
  }
  @java.lang.Override
  override def glShaderBinary(n: scala.Int, shaders: java.nio.IntBuffer, binaryformat: scala.Int, binary: java.nio.Buffer, length: scala.Int): scala.Unit = {
    calls = calls + 1
    this.gl20.glShaderBinary(n, shaders, binaryformat, binary, length)
    this.check()
  }
  @java.lang.Override
  override def glShaderSource(shader: scala.Int, string: java.lang.String): scala.Unit = {
    calls = calls + 1
    this.gl20.glShaderSource(shader, string)
    this.check()
  }
  @java.lang.Override
  override def glStencilFuncSeparate(face: scala.Int, func: scala.Int, ref: scala.Int, mask: scala.Int): scala.Unit = {
    calls = calls + 1
    this.gl20.glStencilFuncSeparate(face, func, ref, mask)
    this.check()
  }
  @java.lang.Override
  override def glStencilMaskSeparate(face: scala.Int, mask: scala.Int): scala.Unit = {
    calls = calls + 1
    this.gl20.glStencilMaskSeparate(face, mask)
    this.check()
  }
  @java.lang.Override
  override def glStencilOpSeparate(face: scala.Int, fail: scala.Int, zfail: scala.Int, zpass: scala.Int): scala.Unit = {
    calls = calls + 1
    this.gl20.glStencilOpSeparate(face, fail, zfail, zpass)
    this.check()
  }
  @java.lang.Override
  override def glTexParameterfv(target: scala.Int, pname: scala.Int, params: java.nio.FloatBuffer): scala.Unit = {
    calls = calls + 1
    this.gl20.glTexParameterfv(target, pname, params)
    this.check()
  }
  @java.lang.Override
  override def glTexParameteri(target: scala.Int, pname: scala.Int, param: scala.Int): scala.Unit = {
    calls = calls + 1
    this.gl20.glTexParameteri(target, pname, param)
    this.check()
  }
  @java.lang.Override
  override def glTexParameteriv(target: scala.Int, pname: scala.Int, params: java.nio.IntBuffer): scala.Unit = {
    calls = calls + 1
    this.gl20.glTexParameteriv(target, pname, params)
    this.check()
  }
  @java.lang.Override
  override def glUniform1f(location: scala.Int, x: scala.Float): scala.Unit = {
    calls = calls + 1
    this.gl20.glUniform1f(location, x)
    this.check()
  }
  @java.lang.Override
  override def glUniform1fv(location: scala.Int, count: scala.Int, v: java.nio.FloatBuffer): scala.Unit = {
    calls = calls + 1
    this.gl20.glUniform1fv(location, count, v)
    this.check()
  }
  @java.lang.Override
  override def glUniform1fv(location: scala.Int, count: scala.Int, v: scala.Array[scala.Float], offset: scala.Int): scala.Unit = {
    calls = calls + 1
    this.gl20.glUniform1fv(location, count, v, offset)
    this.check()
  }
  @java.lang.Override
  override def glUniform1i(location: scala.Int, x: scala.Int): scala.Unit = {
    calls = calls + 1
    this.gl20.glUniform1i(location, x)
    this.check()
  }
  @java.lang.Override
  override def glUniform1iv(location: scala.Int, count: scala.Int, v: java.nio.IntBuffer): scala.Unit = {
    calls = calls + 1
    this.gl20.glUniform1iv(location, count, v)
    this.check()
  }
  @java.lang.Override
  override def glUniform1iv(location: scala.Int, count: scala.Int, v: scala.Array[scala.Int], offset: scala.Int): scala.Unit = {
    calls = calls + 1
    this.gl20.glUniform1iv(location, count, v, offset)
    this.check()
  }
  @java.lang.Override
  override def glUniform2f(location: scala.Int, x: scala.Float, y: scala.Float): scala.Unit = {
    calls = calls + 1
    this.gl20.glUniform2f(location, x, y)
    this.check()
  }
  @java.lang.Override
  override def glUniform2fv(location: scala.Int, count: scala.Int, v: java.nio.FloatBuffer): scala.Unit = {
    calls = calls + 1
    this.gl20.glUniform2fv(location, count, v)
    this.check()
  }
  @java.lang.Override
  override def glUniform2fv(location: scala.Int, count: scala.Int, v: scala.Array[scala.Float], offset: scala.Int): scala.Unit = {
    calls = calls + 1
    this.gl20.glUniform2fv(location, count, v, offset)
    this.check()
  }
  @java.lang.Override
  override def glUniform2i(location: scala.Int, x: scala.Int, y: scala.Int): scala.Unit = {
    calls = calls + 1
    this.gl20.glUniform2i(location, x, y)
    this.check()
  }
  @java.lang.Override
  override def glUniform2iv(location: scala.Int, count: scala.Int, v: java.nio.IntBuffer): scala.Unit = {
    calls = calls + 1
    this.gl20.glUniform2iv(location, count, v)
    this.check()
  }
  @java.lang.Override
  override def glUniform2iv(location: scala.Int, count: scala.Int, v: scala.Array[scala.Int], offset: scala.Int): scala.Unit = {
    calls = calls + 1
    this.gl20.glUniform2iv(location, count, v, offset)
    this.check()
  }
  @java.lang.Override
  override def glUniform3f(location: scala.Int, x: scala.Float, y: scala.Float, z: scala.Float): scala.Unit = {
    calls = calls + 1
    this.gl20.glUniform3f(location, x, y, z)
    this.check()
  }
  @java.lang.Override
  override def glUniform3fv(location: scala.Int, count: scala.Int, v: java.nio.FloatBuffer): scala.Unit = {
    calls = calls + 1
    this.gl20.glUniform3fv(location, count, v)
    this.check()
  }
  @java.lang.Override
  override def glUniform3fv(location: scala.Int, count: scala.Int, v: scala.Array[scala.Float], offset: scala.Int): scala.Unit = {
    calls = calls + 1
    this.gl20.glUniform3fv(location, count, v, offset)
    this.check()
  }
  @java.lang.Override
  override def glUniform3i(location: scala.Int, x: scala.Int, y: scala.Int, z: scala.Int): scala.Unit = {
    calls = calls + 1
    this.gl20.glUniform3i(location, x, y, z)
    this.check()
  }
  @java.lang.Override
  override def glUniform3iv(location: scala.Int, count: scala.Int, v: java.nio.IntBuffer): scala.Unit = {
    calls = calls + 1
    this.gl20.glUniform3iv(location, count, v)
    this.check()
  }
  @java.lang.Override
  override def glUniform3iv(location: scala.Int, count: scala.Int, v: scala.Array[scala.Int], offset: scala.Int): scala.Unit = {
    calls = calls + 1
    this.gl20.glUniform3iv(location, count, v, offset)
    this.check()
  }
  @java.lang.Override
  override def glUniform4f(location: scala.Int, x: scala.Float, y: scala.Float, z: scala.Float, w: scala.Float): scala.Unit = {
    calls = calls + 1
    this.gl20.glUniform4f(location, x, y, z, w)
    this.check()
  }
  @java.lang.Override
  override def glUniform4fv(location: scala.Int, count: scala.Int, v: java.nio.FloatBuffer): scala.Unit = {
    calls = calls + 1
    this.gl20.glUniform4fv(location, count, v)
    this.check()
  }
  @java.lang.Override
  override def glUniform4fv(location: scala.Int, count: scala.Int, v: scala.Array[scala.Float], offset: scala.Int): scala.Unit = {
    calls = calls + 1
    this.gl20.glUniform4fv(location, count, v, offset)
    this.check()
  }
  @java.lang.Override
  override def glUniform4i(location: scala.Int, x: scala.Int, y: scala.Int, z: scala.Int, w: scala.Int): scala.Unit = {
    calls = calls + 1
    this.gl20.glUniform4i(location, x, y, z, w)
    this.check()
  }
  @java.lang.Override
  override def glUniform4iv(location: scala.Int, count: scala.Int, v: java.nio.IntBuffer): scala.Unit = {
    calls = calls + 1
    this.gl20.glUniform4iv(location, count, v)
    this.check()
  }
  @java.lang.Override
  override def glUniform4iv(location: scala.Int, count: scala.Int, v: scala.Array[scala.Int], offset: scala.Int): scala.Unit = {
    calls = calls + 1
    this.gl20.glUniform4iv(location, count, v, offset)
    this.check()
  }
  @java.lang.Override
  override def glUniformMatrix2fv(location: scala.Int, count: scala.Int, transpose: scala.Boolean, value: java.nio.FloatBuffer): scala.Unit = {
    calls = calls + 1
    this.gl20.glUniformMatrix2fv(location, count, transpose, value)
    this.check()
  }
  @java.lang.Override
  override def glUniformMatrix2fv(location: scala.Int, count: scala.Int, transpose: scala.Boolean, value: scala.Array[scala.Float], offset: scala.Int): scala.Unit = {
    calls = calls + 1
    this.gl20.glUniformMatrix2fv(location, count, transpose, value, offset)
    this.check()
  }
  @java.lang.Override
  override def glUniformMatrix3fv(location: scala.Int, count: scala.Int, transpose: scala.Boolean, value: java.nio.FloatBuffer): scala.Unit = {
    calls = calls + 1
    this.gl20.glUniformMatrix3fv(location, count, transpose, value)
    this.check()
  }
  @java.lang.Override
  override def glUniformMatrix3fv(location: scala.Int, count: scala.Int, transpose: scala.Boolean, value: scala.Array[scala.Float], offset: scala.Int): scala.Unit = {
    calls = calls + 1
    this.gl20.glUniformMatrix3fv(location, count, transpose, value, offset)
    this.check()
  }
  @java.lang.Override
  override def glUniformMatrix4fv(location: scala.Int, count: scala.Int, transpose: scala.Boolean, value: java.nio.FloatBuffer): scala.Unit = {
    calls = calls + 1
    this.gl20.glUniformMatrix4fv(location, count, transpose, value)
    this.check()
  }
  @java.lang.Override
  override def glUniformMatrix4fv(location: scala.Int, count: scala.Int, transpose: scala.Boolean, value: scala.Array[scala.Float], offset: scala.Int): scala.Unit = {
    calls = calls + 1
    this.gl20.glUniformMatrix4fv(location, count, transpose, value, offset)
    this.check()
  }
  @java.lang.Override
  override def glUseProgram(program: scala.Int): scala.Unit = {
    shaderSwitches = shaderSwitches + 1
    calls = calls + 1
    this.gl20.glUseProgram(program)
    this.check()
  }
  @java.lang.Override
  override def glValidateProgram(program: scala.Int): scala.Unit = {
    calls = calls + 1
    this.gl20.glValidateProgram(program)
    this.check()
  }
  @java.lang.Override
  override def glVertexAttrib1f(indx: scala.Int, x: scala.Float): scala.Unit = {
    calls = calls + 1
    this.gl20.glVertexAttrib1f(indx, x)
    this.check()
  }
  @java.lang.Override
  override def glVertexAttrib1fv(indx: scala.Int, values: java.nio.FloatBuffer): scala.Unit = {
    calls = calls + 1
    this.gl20.glVertexAttrib1fv(indx, values)
    this.check()
  }
  @java.lang.Override
  override def glVertexAttrib2f(indx: scala.Int, x: scala.Float, y: scala.Float): scala.Unit = {
    calls = calls + 1
    this.gl20.glVertexAttrib2f(indx, x, y)
    this.check()
  }
  @java.lang.Override
  override def glVertexAttrib2fv(indx: scala.Int, values: java.nio.FloatBuffer): scala.Unit = {
    calls = calls + 1
    this.gl20.glVertexAttrib2fv(indx, values)
    this.check()
  }
  @java.lang.Override
  override def glVertexAttrib3f(indx: scala.Int, x: scala.Float, y: scala.Float, z: scala.Float): scala.Unit = {
    calls = calls + 1
    this.gl20.glVertexAttrib3f(indx, x, y, z)
    this.check()
  }
  @java.lang.Override
  override def glVertexAttrib3fv(indx: scala.Int, values: java.nio.FloatBuffer): scala.Unit = {
    calls = calls + 1
    this.gl20.glVertexAttrib3fv(indx, values)
    this.check()
  }
  @java.lang.Override
  override def glVertexAttrib4f(indx: scala.Int, x: scala.Float, y: scala.Float, z: scala.Float, w: scala.Float): scala.Unit = {
    calls = calls + 1
    this.gl20.glVertexAttrib4f(indx, x, y, z, w)
    this.check()
  }
  @java.lang.Override
  override def glVertexAttrib4fv(indx: scala.Int, values: java.nio.FloatBuffer): scala.Unit = {
    calls = calls + 1
    this.gl20.glVertexAttrib4fv(indx, values)
    this.check()
  }
  @java.lang.Override
  override def glVertexAttribPointer(indx: scala.Int, size: scala.Int, `type`: scala.Int, normalized: scala.Boolean, stride: scala.Int, ptr: java.nio.Buffer): scala.Unit = {
    calls = calls + 1
    this.gl20.glVertexAttribPointer(indx, size, `type`, normalized, stride, ptr)
    this.check()
  }
  @java.lang.Override
  override def glVertexAttribPointer(indx: scala.Int, size: scala.Int, `type`: scala.Int, normalized: scala.Boolean, stride: scala.Int, ptr: scala.Int): scala.Unit = {
    calls = calls + 1
    this.gl20.glVertexAttribPointer(indx, size, `type`, normalized, stride, ptr)
    this.check()
  }
}
object GL20Interceptor {
  export com.badlogic.gdx.graphics.profiling.GLInterceptor.*
}