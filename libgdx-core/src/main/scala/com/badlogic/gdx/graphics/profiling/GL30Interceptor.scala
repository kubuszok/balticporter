package com.badlogic.gdx.graphics.profiling

class GL30Interceptor extends com.badlogic.gdx.graphics.profiling.GLInterceptor with com.badlogic.gdx.graphics.GL30 {
  var gl30: com.badlogic.gdx.graphics.GL30 = null.asInstanceOf[com.badlogic.gdx.graphics.GL30]
  def this(glProfiler: com.badlogic.gdx.graphics.profiling.GLProfiler, gl30: com.badlogic.gdx.graphics.GL30) = {
    this()
    this.gl30 = gl30
  }
  private def check(): scala.Unit = {
    var error: scala.Int = this.gl30.glGetError()
    while (error != com.badlogic.gdx.graphics.GL20.GL_NO_ERROR) {
      glProfiler.getListener().onError(error)
      error = this.gl30.glGetError()
    }
  }
  def glActiveTexture(texture: scala.Int): scala.Unit = {
    calls = calls + 1
    this.gl30.glActiveTexture(texture)
    this.check()
  }
  def glBindTexture(target: scala.Int, texture: scala.Int): scala.Unit = {
    textureBindings = textureBindings + 1
    calls = calls + 1
    this.gl30.glBindTexture(target, texture)
    this.check()
  }
  def glBlendFunc(sfactor: scala.Int, dfactor: scala.Int): scala.Unit = {
    calls = calls + 1
    this.gl30.glBlendFunc(sfactor, dfactor)
    this.check()
  }
  def glClear(mask: scala.Int): scala.Unit = {
    calls = calls + 1
    this.gl30.glClear(mask)
    this.check()
  }
  def glClearColor(red: scala.Float, green: scala.Float, blue: scala.Float, alpha: scala.Float): scala.Unit = {
    calls = calls + 1
    this.gl30.glClearColor(red, green, blue, alpha)
    this.check()
  }
  def glClearDepthf(depth: scala.Float): scala.Unit = {
    calls = calls + 1
    this.gl30.glClearDepthf(depth)
    this.check()
  }
  def glClearStencil(s: scala.Int): scala.Unit = {
    calls = calls + 1
    this.gl30.glClearStencil(s)
    this.check()
  }
  def glColorMask(red: scala.Boolean, green: scala.Boolean, blue: scala.Boolean, alpha: scala.Boolean): scala.Unit = {
    calls = calls + 1
    this.gl30.glColorMask(red, green, blue, alpha)
    this.check()
  }
  def glCompressedTexImage2D(target: scala.Int, level: scala.Int, internalformat: scala.Int, width: scala.Int, height: scala.Int, border: scala.Int, imageSize: scala.Int, data: java.nio.Buffer): scala.Unit = {
    calls = calls + 1
    this.gl30.glCompressedTexImage2D(target, level, internalformat, width, height, border, imageSize, data)
    this.check()
  }
  def glCompressedTexSubImage2D(target: scala.Int, level: scala.Int, xoffset: scala.Int, yoffset: scala.Int, width: scala.Int, height: scala.Int, format: scala.Int, imageSize: scala.Int, data: java.nio.Buffer): scala.Unit = {
    calls = calls + 1
    this.gl30.glCompressedTexSubImage2D(target, level, xoffset, yoffset, width, height, format, imageSize, data)
    this.check()
  }
  def glCopyTexImage2D(target: scala.Int, level: scala.Int, internalformat: scala.Int, x: scala.Int, y: scala.Int, width: scala.Int, height: scala.Int, border: scala.Int): scala.Unit = {
    calls = calls + 1
    this.gl30.glCopyTexImage2D(target, level, internalformat, x, y, width, height, border)
    this.check()
  }
  def glCopyTexSubImage2D(target: scala.Int, level: scala.Int, xoffset: scala.Int, yoffset: scala.Int, x: scala.Int, y: scala.Int, width: scala.Int, height: scala.Int): scala.Unit = {
    calls = calls + 1
    this.gl30.glCopyTexSubImage2D(target, level, xoffset, yoffset, x, y, width, height)
    this.check()
  }
  def glCullFace(mode: scala.Int): scala.Unit = {
    calls = calls + 1
    this.gl30.glCullFace(mode)
    this.check()
  }
  def glDeleteTextures(n: scala.Int, textures: java.nio.IntBuffer): scala.Unit = {
    calls = calls + 1
    this.gl30.glDeleteTextures(n, textures)
    this.check()
  }
  def glDeleteTexture(texture: scala.Int): scala.Unit = {
    calls = calls + 1
    this.gl30.glDeleteTexture(texture)
    this.check()
  }
  def glDepthFunc(func: scala.Int): scala.Unit = {
    calls = calls + 1
    this.gl30.glDepthFunc(func)
    this.check()
  }
  def glDepthMask(flag: scala.Boolean): scala.Unit = {
    calls = calls + 1
    this.gl30.glDepthMask(flag)
    this.check()
  }
  def glDepthRangef(zNear: scala.Float, zFar: scala.Float): scala.Unit = {
    calls = calls + 1
    this.gl30.glDepthRangef(zNear, zFar)
    this.check()
  }
  def glDisable(cap: scala.Int): scala.Unit = {
    calls = calls + 1
    this.gl30.glDisable(cap)
    this.check()
  }
  def glDrawArrays(mode: scala.Int, first: scala.Int, count: scala.Int): scala.Unit = {
    vertexCount.put(count)
    drawCalls = drawCalls + 1
    calls = calls + 1
    this.gl30.glDrawArrays(mode, first, count)
    this.check()
  }
  def glDrawElements(mode: scala.Int, count: scala.Int, `type`: scala.Int, indices: java.nio.Buffer): scala.Unit = {
    vertexCount.put(count)
    drawCalls = drawCalls + 1
    calls = calls + 1
    this.gl30.glDrawElements(mode, count, `type`, indices)
    this.check()
  }
  def glEnable(cap: scala.Int): scala.Unit = {
    calls = calls + 1
    this.gl30.glEnable(cap)
    this.check()
  }
  def glFinish(): scala.Unit = {
    calls = calls + 1
    this.gl30.glFinish()
    this.check()
  }
  def glFlush(): scala.Unit = {
    calls = calls + 1
    this.gl30.glFlush()
    this.check()
  }
  def glFrontFace(mode: scala.Int): scala.Unit = {
    calls = calls + 1
    this.gl30.glFrontFace(mode)
    this.check()
  }
  def glGenTextures(n: scala.Int, textures: java.nio.IntBuffer): scala.Unit = {
    calls = calls + 1
    this.gl30.glGenTextures(n, textures)
    this.check()
  }
  def glGenTexture(): scala.Int = {
    calls = calls + 1
    val result: scala.Int = this.gl30.glGenTexture()
    this.check()
    return result
  }
  def glGetError(): scala.Int = {
    calls = calls + 1
    return this.gl30.glGetError()
  }
  def glGetIntegerv(pname: scala.Int, params: java.nio.IntBuffer): scala.Unit = {
    calls = calls + 1
    this.gl30.glGetIntegerv(pname, params)
    this.check()
  }
  def glGetString(name: scala.Int): java.lang.String = {
    calls = calls + 1
    val result: java.lang.String = this.gl30.glGetString(name)
    this.check()
    return result
  }
  def glHint(target: scala.Int, mode: scala.Int): scala.Unit = {
    calls = calls + 1
    this.gl30.glHint(target, mode)
    this.check()
  }
  def glLineWidth(width: scala.Float): scala.Unit = {
    calls = calls + 1
    this.gl30.glLineWidth(width)
    this.check()
  }
  def glPixelStorei(pname: scala.Int, param: scala.Int): scala.Unit = {
    calls = calls + 1
    this.gl30.glPixelStorei(pname, param)
    this.check()
  }
  def glPolygonOffset(factor: scala.Float, units: scala.Float): scala.Unit = {
    calls = calls + 1
    this.gl30.glPolygonOffset(factor, units)
    this.check()
  }
  def glReadPixels(x: scala.Int, y: scala.Int, width: scala.Int, height: scala.Int, format: scala.Int, `type`: scala.Int, pixels: java.nio.Buffer): scala.Unit = {
    calls = calls + 1
    this.gl30.glReadPixels(x, y, width, height, format, `type`, pixels)
    this.check()
  }
  def glScissor(x: scala.Int, y: scala.Int, width: scala.Int, height: scala.Int): scala.Unit = {
    calls = calls + 1
    this.gl30.glScissor(x, y, width, height)
    this.check()
  }
  def glStencilFunc(func: scala.Int, ref: scala.Int, mask: scala.Int): scala.Unit = {
    calls = calls + 1
    this.gl30.glStencilFunc(func, ref, mask)
    this.check()
  }
  def glStencilMask(mask: scala.Int): scala.Unit = {
    calls = calls + 1
    this.gl30.glStencilMask(mask)
    this.check()
  }
  def glStencilOp(fail: scala.Int, zfail: scala.Int, zpass: scala.Int): scala.Unit = {
    calls = calls + 1
    this.gl30.glStencilOp(fail, zfail, zpass)
    this.check()
  }
  def glTexImage2D(target: scala.Int, level: scala.Int, internalformat: scala.Int, width: scala.Int, height: scala.Int, border: scala.Int, format: scala.Int, `type`: scala.Int, pixels: java.nio.Buffer): scala.Unit = {
    calls = calls + 1
    this.gl30.glTexImage2D(target, level, internalformat, width, height, border, format, `type`, pixels)
    this.check()
  }
  def glTexImage2D(target: scala.Int, level: scala.Int, internalformat: scala.Int, width: scala.Int, height: scala.Int, border: scala.Int, format: scala.Int, `type`: scala.Int, offset: scala.Int): scala.Unit = {
    calls = calls + 1
    this.gl30.glTexImage2D(target, level, internalformat, width, height, border, format, `type`, offset)
    this.check()
  }
  def glTexParameterf(target: scala.Int, pname: scala.Int, param: scala.Float): scala.Unit = {
    calls = calls + 1
    this.gl30.glTexParameterf(target, pname, param)
    this.check()
  }
  def glTexSubImage2D(target: scala.Int, level: scala.Int, xoffset: scala.Int, yoffset: scala.Int, width: scala.Int, height: scala.Int, format: scala.Int, `type`: scala.Int, pixels: java.nio.Buffer): scala.Unit = {
    calls = calls + 1
    this.gl30.glTexSubImage2D(target, level, xoffset, yoffset, width, height, format, `type`, pixels)
    this.check()
  }
  def glTexSubImage2D(target: scala.Int, level: scala.Int, xoffset: scala.Int, yoffset: scala.Int, width: scala.Int, height: scala.Int, format: scala.Int, `type`: scala.Int, offset: scala.Int): scala.Unit = {
    calls = calls + 1
    this.gl30.glTexSubImage2D(target, level, xoffset, yoffset, width, height, format, `type`, offset)
    this.check()
  }
  def glViewport(x: scala.Int, y: scala.Int, width: scala.Int, height: scala.Int): scala.Unit = {
    calls = calls + 1
    this.gl30.glViewport(x, y, width, height)
    this.check()
  }
  def glAttachShader(program: scala.Int, shader: scala.Int): scala.Unit = {
    calls = calls + 1
    this.gl30.glAttachShader(program, shader)
    this.check()
  }
  def glBindAttribLocation(program: scala.Int, index: scala.Int, name: java.lang.String): scala.Unit = {
    calls = calls + 1
    this.gl30.glBindAttribLocation(program, index, name)
    this.check()
  }
  def glBindBuffer(target: scala.Int, buffer: scala.Int): scala.Unit = {
    calls = calls + 1
    this.gl30.glBindBuffer(target, buffer)
    this.check()
  }
  def glBindFramebuffer(target: scala.Int, framebuffer: scala.Int): scala.Unit = {
    calls = calls + 1
    this.gl30.glBindFramebuffer(target, framebuffer)
    this.check()
  }
  def glBindRenderbuffer(target: scala.Int, renderbuffer: scala.Int): scala.Unit = {
    calls = calls + 1
    this.gl30.glBindRenderbuffer(target, renderbuffer)
    this.check()
  }
  def glBlendColor(red: scala.Float, green: scala.Float, blue: scala.Float, alpha: scala.Float): scala.Unit = {
    calls = calls + 1
    this.gl30.glBlendColor(red, green, blue, alpha)
    this.check()
  }
  def glBlendEquation(mode: scala.Int): scala.Unit = {
    calls = calls + 1
    this.gl30.glBlendEquation(mode)
    this.check()
  }
  def glBlendEquationSeparate(modeRGB: scala.Int, modeAlpha: scala.Int): scala.Unit = {
    calls = calls + 1
    this.gl30.glBlendEquationSeparate(modeRGB, modeAlpha)
    this.check()
  }
  def glBlendFuncSeparate(srcRGB: scala.Int, dstRGB: scala.Int, srcAlpha: scala.Int, dstAlpha: scala.Int): scala.Unit = {
    calls = calls + 1
    this.gl30.glBlendFuncSeparate(srcRGB, dstRGB, srcAlpha, dstAlpha)
    this.check()
  }
  def glBufferData(target: scala.Int, size: scala.Int, data: java.nio.Buffer, usage: scala.Int): scala.Unit = {
    calls = calls + 1
    this.gl30.glBufferData(target, size, data, usage)
    this.check()
  }
  def glBufferSubData(target: scala.Int, offset: scala.Int, size: scala.Int, data: java.nio.Buffer): scala.Unit = {
    calls = calls + 1
    this.gl30.glBufferSubData(target, offset, size, data)
    this.check()
  }
  def glCheckFramebufferStatus(target: scala.Int): scala.Int = {
    calls = calls + 1
    val result: scala.Int = this.gl30.glCheckFramebufferStatus(target)
    this.check()
    return result
  }
  def glCompileShader(shader: scala.Int): scala.Unit = {
    calls = calls + 1
    this.gl30.glCompileShader(shader)
    this.check()
  }
  def glCreateProgram(): scala.Int = {
    calls = calls + 1
    val result: scala.Int = this.gl30.glCreateProgram()
    this.check()
    return result
  }
  def glCreateShader(`type`: scala.Int): scala.Int = {
    calls = calls + 1
    val result: scala.Int = this.gl30.glCreateShader(`type`)
    this.check()
    return result
  }
  def glDeleteBuffer(buffer: scala.Int): scala.Unit = {
    calls = calls + 1
    this.gl30.glDeleteBuffer(buffer)
    this.check()
  }
  def glDeleteBuffers(n: scala.Int, buffers: java.nio.IntBuffer): scala.Unit = {
    calls = calls + 1
    this.gl30.glDeleteBuffers(n, buffers)
    this.check()
  }
  def glDeleteFramebuffer(framebuffer: scala.Int): scala.Unit = {
    calls = calls + 1
    this.gl30.glDeleteFramebuffer(framebuffer)
    this.check()
  }
  def glDeleteFramebuffers(n: scala.Int, framebuffers: java.nio.IntBuffer): scala.Unit = {
    calls = calls + 1
    this.gl30.glDeleteFramebuffers(n, framebuffers)
    this.check()
  }
  def glDeleteProgram(program: scala.Int): scala.Unit = {
    calls = calls + 1
    this.gl30.glDeleteProgram(program)
    this.check()
  }
  def glDeleteRenderbuffer(renderbuffer: scala.Int): scala.Unit = {
    calls = calls + 1
    this.gl30.glDeleteRenderbuffer(renderbuffer)
    this.check()
  }
  def glDeleteRenderbuffers(n: scala.Int, renderbuffers: java.nio.IntBuffer): scala.Unit = {
    calls = calls + 1
    this.gl30.glDeleteRenderbuffers(n, renderbuffers)
    this.check()
  }
  def glDeleteShader(shader: scala.Int): scala.Unit = {
    calls = calls + 1
    this.gl30.glDeleteShader(shader)
    this.check()
  }
  def glDetachShader(program: scala.Int, shader: scala.Int): scala.Unit = {
    calls = calls + 1
    this.gl30.glDetachShader(program, shader)
    this.check()
  }
  def glDisableVertexAttribArray(index: scala.Int): scala.Unit = {
    calls = calls + 1
    this.gl30.glDisableVertexAttribArray(index)
    this.check()
  }
  def glDrawElements(mode: scala.Int, count: scala.Int, `type`: scala.Int, indices: scala.Int): scala.Unit = {
    vertexCount.put(count)
    drawCalls = drawCalls + 1
    calls = calls + 1
    this.gl30.glDrawElements(mode, count, `type`, indices)
    this.check()
  }
  def glEnableVertexAttribArray(index: scala.Int): scala.Unit = {
    calls = calls + 1
    this.gl30.glEnableVertexAttribArray(index)
    this.check()
  }
  def glFramebufferRenderbuffer(target: scala.Int, attachment: scala.Int, renderbuffertarget: scala.Int, renderbuffer: scala.Int): scala.Unit = {
    calls = calls + 1
    this.gl30.glFramebufferRenderbuffer(target, attachment, renderbuffertarget, renderbuffer)
    this.check()
  }
  def glFramebufferTexture2D(target: scala.Int, attachment: scala.Int, textarget: scala.Int, texture: scala.Int, level: scala.Int): scala.Unit = {
    calls = calls + 1
    this.gl30.glFramebufferTexture2D(target, attachment, textarget, texture, level)
    this.check()
  }
  def glGenBuffer(): scala.Int = {
    calls = calls + 1
    val result: scala.Int = this.gl30.glGenBuffer()
    this.check()
    return result
  }
  def glGenBuffers(n: scala.Int, buffers: java.nio.IntBuffer): scala.Unit = {
    calls = calls + 1
    this.gl30.glGenBuffers(n, buffers)
    this.check()
  }
  def glGenerateMipmap(target: scala.Int): scala.Unit = {
    calls = calls + 1
    this.gl30.glGenerateMipmap(target)
    this.check()
  }
  def glGenFramebuffer(): scala.Int = {
    calls = calls + 1
    val result: scala.Int = this.gl30.glGenFramebuffer()
    this.check()
    return result
  }
  def glGenFramebuffers(n: scala.Int, framebuffers: java.nio.IntBuffer): scala.Unit = {
    calls = calls + 1
    this.gl30.glGenFramebuffers(n, framebuffers)
    this.check()
  }
  def glGenRenderbuffer(): scala.Int = {
    calls = calls + 1
    val result: scala.Int = this.gl30.glGenRenderbuffer()
    this.check()
    return result
  }
  def glGenRenderbuffers(n: scala.Int, renderbuffers: java.nio.IntBuffer): scala.Unit = {
    calls = calls + 1
    this.gl30.glGenRenderbuffers(n, renderbuffers)
    this.check()
  }
  def glGetActiveAttrib(program: scala.Int, index: scala.Int, size: java.nio.IntBuffer, `type`: java.nio.IntBuffer): java.lang.String = {
    calls = calls + 1
    val result: java.lang.String = this.gl30.glGetActiveAttrib(program, index, size, `type`)
    this.check()
    return result
  }
  def glGetActiveUniform(program: scala.Int, index: scala.Int, size: java.nio.IntBuffer, `type`: java.nio.IntBuffer): java.lang.String = {
    calls = calls + 1
    val result: java.lang.String = this.gl30.glGetActiveUniform(program, index, size, `type`)
    this.check()
    return result
  }
  def glGetAttachedShaders(program: scala.Int, maxcount: scala.Int, count: java.nio.Buffer, shaders: java.nio.IntBuffer): scala.Unit = {
    calls = calls + 1
    this.gl30.glGetAttachedShaders(program, maxcount, count, shaders)
    this.check()
  }
  def glGetAttribLocation(program: scala.Int, name: java.lang.String): scala.Int = {
    calls = calls + 1
    val result: scala.Int = this.gl30.glGetAttribLocation(program, name)
    this.check()
    return result
  }
  def glGetBooleanv(pname: scala.Int, params: java.nio.Buffer): scala.Unit = {
    calls = calls + 1
    this.gl30.glGetBooleanv(pname, params)
    this.check()
  }
  def glGetBufferParameteriv(target: scala.Int, pname: scala.Int, params: java.nio.IntBuffer): scala.Unit = {
    calls = calls + 1
    this.gl30.glGetBufferParameteriv(target, pname, params)
    this.check()
  }
  def glGetFloatv(pname: scala.Int, params: java.nio.FloatBuffer): scala.Unit = {
    calls = calls + 1
    this.gl30.glGetFloatv(pname, params)
    this.check()
  }
  def glGetFramebufferAttachmentParameteriv(target: scala.Int, attachment: scala.Int, pname: scala.Int, params: java.nio.IntBuffer): scala.Unit = {
    calls = calls + 1
    this.gl30.glGetFramebufferAttachmentParameteriv(target, attachment, pname, params)
    this.check()
  }
  def glGetProgramiv(program: scala.Int, pname: scala.Int, params: java.nio.IntBuffer): scala.Unit = {
    calls = calls + 1
    this.gl30.glGetProgramiv(program, pname, params)
    this.check()
  }
  def glGetProgramInfoLog(program: scala.Int): java.lang.String = {
    calls = calls + 1
    val result: java.lang.String = this.gl30.glGetProgramInfoLog(program)
    this.check()
    return result
  }
  def glGetRenderbufferParameteriv(target: scala.Int, pname: scala.Int, params: java.nio.IntBuffer): scala.Unit = {
    calls = calls + 1
    this.gl30.glGetRenderbufferParameteriv(target, pname, params)
    this.check()
  }
  def glGetShaderiv(shader: scala.Int, pname: scala.Int, params: java.nio.IntBuffer): scala.Unit = {
    calls = calls + 1
    this.gl30.glGetShaderiv(shader, pname, params)
    this.check()
  }
  def glGetShaderInfoLog(shader: scala.Int): java.lang.String = {
    calls = calls + 1
    val result: java.lang.String = this.gl30.glGetShaderInfoLog(shader)
    this.check()
    return result
  }
  def glGetShaderPrecisionFormat(shadertype: scala.Int, precisiontype: scala.Int, range: java.nio.IntBuffer, precision: java.nio.IntBuffer): scala.Unit = {
    calls = calls + 1
    this.gl30.glGetShaderPrecisionFormat(shadertype, precisiontype, range, precision)
    this.check()
  }
  def glGetTexParameterfv(target: scala.Int, pname: scala.Int, params: java.nio.FloatBuffer): scala.Unit = {
    calls = calls + 1
    this.gl30.glGetTexParameterfv(target, pname, params)
    this.check()
  }
  def glGetTexParameteriv(target: scala.Int, pname: scala.Int, params: java.nio.IntBuffer): scala.Unit = {
    calls = calls + 1
    this.gl30.glGetTexParameteriv(target, pname, params)
    this.check()
  }
  def glGetUniformfv(program: scala.Int, location: scala.Int, params: java.nio.FloatBuffer): scala.Unit = {
    calls = calls + 1
    this.gl30.glGetUniformfv(program, location, params)
    this.check()
  }
  def glGetUniformiv(program: scala.Int, location: scala.Int, params: java.nio.IntBuffer): scala.Unit = {
    calls = calls + 1
    this.gl30.glGetUniformiv(program, location, params)
    this.check()
  }
  def glGetUniformLocation(program: scala.Int, name: java.lang.String): scala.Int = {
    calls = calls + 1
    val result: scala.Int = this.gl30.glGetUniformLocation(program, name)
    this.check()
    return result
  }
  def glGetVertexAttribfv(index: scala.Int, pname: scala.Int, params: java.nio.FloatBuffer): scala.Unit = {
    calls = calls + 1
    this.gl30.glGetVertexAttribfv(index, pname, params)
    this.check()
  }
  def glGetVertexAttribiv(index: scala.Int, pname: scala.Int, params: java.nio.IntBuffer): scala.Unit = {
    calls = calls + 1
    this.gl30.glGetVertexAttribiv(index, pname, params)
    this.check()
  }
  def glGetVertexAttribPointerv(index: scala.Int, pname: scala.Int, pointer: java.nio.Buffer): scala.Unit = {
    calls = calls + 1
    this.gl30.glGetVertexAttribPointerv(index, pname, pointer)
    this.check()
  }
  def glIsBuffer(buffer: scala.Int): scala.Boolean = {
    calls = calls + 1
    val result: scala.Boolean = this.gl30.glIsBuffer(buffer)
    this.check()
    return result
  }
  def glIsEnabled(cap: scala.Int): scala.Boolean = {
    calls = calls + 1
    val result: scala.Boolean = this.gl30.glIsEnabled(cap)
    this.check()
    return result
  }
  def glIsFramebuffer(framebuffer: scala.Int): scala.Boolean = {
    calls = calls + 1
    val result: scala.Boolean = this.gl30.glIsFramebuffer(framebuffer)
    this.check()
    return result
  }
  def glIsProgram(program: scala.Int): scala.Boolean = {
    calls = calls + 1
    val result: scala.Boolean = this.gl30.glIsProgram(program)
    this.check()
    return result
  }
  def glIsRenderbuffer(renderbuffer: scala.Int): scala.Boolean = {
    calls = calls + 1
    val result: scala.Boolean = this.gl30.glIsRenderbuffer(renderbuffer)
    this.check()
    return result
  }
  def glIsShader(shader: scala.Int): scala.Boolean = {
    calls = calls + 1
    val result: scala.Boolean = this.gl30.glIsShader(shader)
    this.check()
    return result
  }
  def glIsTexture(texture: scala.Int): scala.Boolean = {
    calls = calls + 1
    val result: scala.Boolean = this.gl30.glIsTexture(texture)
    this.check()
    return result
  }
  def glLinkProgram(program: scala.Int): scala.Unit = {
    calls = calls + 1
    this.gl30.glLinkProgram(program)
    this.check()
  }
  def glReleaseShaderCompiler(): scala.Unit = {
    calls = calls + 1
    this.gl30.glReleaseShaderCompiler()
    this.check()
  }
  def glRenderbufferStorage(target: scala.Int, internalformat: scala.Int, width: scala.Int, height: scala.Int): scala.Unit = {
    calls = calls + 1
    this.gl30.glRenderbufferStorage(target, internalformat, width, height)
    this.check()
  }
  def glSampleCoverage(value: scala.Float, invert: scala.Boolean): scala.Unit = {
    calls = calls + 1
    this.gl30.glSampleCoverage(value, invert)
    this.check()
  }
  def glShaderBinary(n: scala.Int, shaders: java.nio.IntBuffer, binaryformat: scala.Int, binary: java.nio.Buffer, length: scala.Int): scala.Unit = {
    calls = calls + 1
    this.gl30.glShaderBinary(n, shaders, binaryformat, binary, length)
    this.check()
  }
  def glShaderSource(shader: scala.Int, string: java.lang.String): scala.Unit = {
    calls = calls + 1
    this.gl30.glShaderSource(shader, string)
    this.check()
  }
  def glStencilFuncSeparate(face: scala.Int, func: scala.Int, ref: scala.Int, mask: scala.Int): scala.Unit = {
    calls = calls + 1
    this.gl30.glStencilFuncSeparate(face, func, ref, mask)
    this.check()
  }
  def glStencilMaskSeparate(face: scala.Int, mask: scala.Int): scala.Unit = {
    calls = calls + 1
    this.gl30.glStencilMaskSeparate(face, mask)
    this.check()
  }
  def glStencilOpSeparate(face: scala.Int, fail: scala.Int, zfail: scala.Int, zpass: scala.Int): scala.Unit = {
    calls = calls + 1
    this.gl30.glStencilOpSeparate(face, fail, zfail, zpass)
    this.check()
  }
  def glTexParameterfv(target: scala.Int, pname: scala.Int, params: java.nio.FloatBuffer): scala.Unit = {
    calls = calls + 1
    this.gl30.glTexParameterfv(target, pname, params)
    this.check()
  }
  def glTexParameteri(target: scala.Int, pname: scala.Int, param: scala.Int): scala.Unit = {
    calls = calls + 1
    this.gl30.glTexParameteri(target, pname, param)
    this.check()
  }
  def glTexParameteriv(target: scala.Int, pname: scala.Int, params: java.nio.IntBuffer): scala.Unit = {
    calls = calls + 1
    this.gl30.glTexParameteriv(target, pname, params)
    this.check()
  }
  def glUniform1f(location: scala.Int, x: scala.Float): scala.Unit = {
    calls = calls + 1
    this.gl30.glUniform1f(location, x)
    this.check()
  }
  def glUniform1fv(location: scala.Int, count: scala.Int, v: java.nio.FloatBuffer): scala.Unit = {
    calls = calls + 1
    this.gl30.glUniform1fv(location, count, v)
    this.check()
  }
  def glUniform1fv(location: scala.Int, count: scala.Int, v: scala.Array[scala.Float], offset: scala.Int): scala.Unit = {
    calls = calls + 1
    this.gl30.glUniform1fv(location, count, v, offset)
    this.check()
  }
  def glUniform1i(location: scala.Int, x: scala.Int): scala.Unit = {
    calls = calls + 1
    this.gl30.glUniform1i(location, x)
    this.check()
  }
  def glUniform1iv(location: scala.Int, count: scala.Int, v: java.nio.IntBuffer): scala.Unit = {
    calls = calls + 1
    this.gl30.glUniform1iv(location, count, v)
    this.check()
  }
  def glUniform1iv(location: scala.Int, count: scala.Int, v: scala.Array[scala.Int], offset: scala.Int): scala.Unit = {
    calls = calls + 1
    this.gl30.glUniform1iv(location, count, v, offset)
    this.check()
  }
  def glUniform2f(location: scala.Int, x: scala.Float, y: scala.Float): scala.Unit = {
    calls = calls + 1
    this.gl30.glUniform2f(location, x, y)
    this.check()
  }
  def glUniform2fv(location: scala.Int, count: scala.Int, v: java.nio.FloatBuffer): scala.Unit = {
    calls = calls + 1
    this.gl30.glUniform2fv(location, count, v)
    this.check()
  }
  def glUniform2fv(location: scala.Int, count: scala.Int, v: scala.Array[scala.Float], offset: scala.Int): scala.Unit = {
    calls = calls + 1
    this.gl30.glUniform2fv(location, count, v, offset)
    this.check()
  }
  def glUniform2i(location: scala.Int, x: scala.Int, y: scala.Int): scala.Unit = {
    calls = calls + 1
    this.gl30.glUniform2i(location, x, y)
    this.check()
  }
  def glUniform2iv(location: scala.Int, count: scala.Int, v: java.nio.IntBuffer): scala.Unit = {
    calls = calls + 1
    this.gl30.glUniform2iv(location, count, v)
    this.check()
  }
  def glUniform2iv(location: scala.Int, count: scala.Int, v: scala.Array[scala.Int], offset: scala.Int): scala.Unit = {
    calls = calls + 1
    this.gl30.glUniform2iv(location, count, v, offset)
    this.check()
  }
  def glUniform3f(location: scala.Int, x: scala.Float, y: scala.Float, z: scala.Float): scala.Unit = {
    calls = calls + 1
    this.gl30.glUniform3f(location, x, y, z)
    this.check()
  }
  def glUniform3fv(location: scala.Int, count: scala.Int, v: java.nio.FloatBuffer): scala.Unit = {
    calls = calls + 1
    this.gl30.glUniform3fv(location, count, v)
    this.check()
  }
  def glUniform3fv(location: scala.Int, count: scala.Int, v: scala.Array[scala.Float], offset: scala.Int): scala.Unit = {
    calls = calls + 1
    this.gl30.glUniform3fv(location, count, v, offset)
    this.check()
  }
  def glUniform3i(location: scala.Int, x: scala.Int, y: scala.Int, z: scala.Int): scala.Unit = {
    calls = calls + 1
    this.gl30.glUniform3i(location, x, y, z)
    this.check()
  }
  def glUniform3iv(location: scala.Int, count: scala.Int, v: java.nio.IntBuffer): scala.Unit = {
    calls = calls + 1
    this.gl30.glUniform3iv(location, count, v)
    this.check()
  }
  def glUniform3iv(location: scala.Int, count: scala.Int, v: scala.Array[scala.Int], offset: scala.Int): scala.Unit = {
    calls = calls + 1
    this.gl30.glUniform3iv(location, count, v, offset)
    this.check()
  }
  def glUniform4f(location: scala.Int, x: scala.Float, y: scala.Float, z: scala.Float, w: scala.Float): scala.Unit = {
    calls = calls + 1
    this.gl30.glUniform4f(location, x, y, z, w)
    this.check()
  }
  def glUniform4fv(location: scala.Int, count: scala.Int, v: java.nio.FloatBuffer): scala.Unit = {
    calls = calls + 1
    this.gl30.glUniform4fv(location, count, v)
    this.check()
  }
  def glUniform4fv(location: scala.Int, count: scala.Int, v: scala.Array[scala.Float], offset: scala.Int): scala.Unit = {
    calls = calls + 1
    this.gl30.glUniform4fv(location, count, v, offset)
    this.check()
  }
  def glUniform4i(location: scala.Int, x: scala.Int, y: scala.Int, z: scala.Int, w: scala.Int): scala.Unit = {
    calls = calls + 1
    this.gl30.glUniform4i(location, x, y, z, w)
    this.check()
  }
  def glUniform4iv(location: scala.Int, count: scala.Int, v: java.nio.IntBuffer): scala.Unit = {
    calls = calls + 1
    this.gl30.glUniform4iv(location, count, v)
    this.check()
  }
  def glUniform4iv(location: scala.Int, count: scala.Int, v: scala.Array[scala.Int], offset: scala.Int): scala.Unit = {
    calls = calls + 1
    this.gl30.glUniform4iv(location, count, v, offset)
    this.check()
  }
  def glUniformMatrix2fv(location: scala.Int, count: scala.Int, transpose: scala.Boolean, value: java.nio.FloatBuffer): scala.Unit = {
    calls = calls + 1
    this.gl30.glUniformMatrix2fv(location, count, transpose, value)
    this.check()
  }
  def glUniformMatrix2fv(location: scala.Int, count: scala.Int, transpose: scala.Boolean, value: scala.Array[scala.Float], offset: scala.Int): scala.Unit = {
    calls = calls + 1
    this.gl30.glUniformMatrix2fv(location, count, transpose, value, offset)
    this.check()
  }
  def glUniformMatrix3fv(location: scala.Int, count: scala.Int, transpose: scala.Boolean, value: java.nio.FloatBuffer): scala.Unit = {
    calls = calls + 1
    this.gl30.glUniformMatrix3fv(location, count, transpose, value)
    this.check()
  }
  def glUniformMatrix3fv(location: scala.Int, count: scala.Int, transpose: scala.Boolean, value: scala.Array[scala.Float], offset: scala.Int): scala.Unit = {
    calls = calls + 1
    this.gl30.glUniformMatrix3fv(location, count, transpose, value, offset)
    this.check()
  }
  def glUniformMatrix4fv(location: scala.Int, count: scala.Int, transpose: scala.Boolean, value: java.nio.FloatBuffer): scala.Unit = {
    calls = calls + 1
    this.gl30.glUniformMatrix4fv(location, count, transpose, value)
    this.check()
  }
  def glUniformMatrix4fv(location: scala.Int, count: scala.Int, transpose: scala.Boolean, value: scala.Array[scala.Float], offset: scala.Int): scala.Unit = {
    calls = calls + 1
    this.gl30.glUniformMatrix4fv(location, count, transpose, value, offset)
    this.check()
  }
  def glUseProgram(program: scala.Int): scala.Unit = {
    shaderSwitches = shaderSwitches + 1
    calls = calls + 1
    this.gl30.glUseProgram(program)
    this.check()
  }
  def glValidateProgram(program: scala.Int): scala.Unit = {
    calls = calls + 1
    this.gl30.glValidateProgram(program)
    this.check()
  }
  def glVertexAttrib1f(indx: scala.Int, x: scala.Float): scala.Unit = {
    calls = calls + 1
    this.gl30.glVertexAttrib1f(indx, x)
    this.check()
  }
  def glVertexAttrib1fv(indx: scala.Int, values: java.nio.FloatBuffer): scala.Unit = {
    calls = calls + 1
    this.gl30.glVertexAttrib1fv(indx, values)
    this.check()
  }
  def glVertexAttrib2f(indx: scala.Int, x: scala.Float, y: scala.Float): scala.Unit = {
    calls = calls + 1
    this.gl30.glVertexAttrib2f(indx, x, y)
    this.check()
  }
  def glVertexAttrib2fv(indx: scala.Int, values: java.nio.FloatBuffer): scala.Unit = {
    calls = calls + 1
    this.gl30.glVertexAttrib2fv(indx, values)
    this.check()
  }
  def glVertexAttrib3f(indx: scala.Int, x: scala.Float, y: scala.Float, z: scala.Float): scala.Unit = {
    calls = calls + 1
    this.gl30.glVertexAttrib3f(indx, x, y, z)
    this.check()
  }
  def glVertexAttrib3fv(indx: scala.Int, values: java.nio.FloatBuffer): scala.Unit = {
    calls = calls + 1
    this.gl30.glVertexAttrib3fv(indx, values)
    this.check()
  }
  def glVertexAttrib4f(indx: scala.Int, x: scala.Float, y: scala.Float, z: scala.Float, w: scala.Float): scala.Unit = {
    calls = calls + 1
    this.gl30.glVertexAttrib4f(indx, x, y, z, w)
    this.check()
  }
  def glVertexAttrib4fv(indx: scala.Int, values: java.nio.FloatBuffer): scala.Unit = {
    calls = calls + 1
    this.gl30.glVertexAttrib4fv(indx, values)
    this.check()
  }
  def glVertexAttribPointer(indx: scala.Int, size: scala.Int, `type`: scala.Int, normalized: scala.Boolean, stride: scala.Int, ptr: java.nio.Buffer): scala.Unit = {
    calls = calls + 1
    this.gl30.glVertexAttribPointer(indx, size, `type`, normalized, stride, ptr)
    this.check()
  }
  def glVertexAttribPointer(indx: scala.Int, size: scala.Int, `type`: scala.Int, normalized: scala.Boolean, stride: scala.Int, ptr: scala.Int): scala.Unit = {
    calls = calls + 1
    this.gl30.glVertexAttribPointer(indx, size, `type`, normalized, stride, ptr)
    this.check()
  }
  def glReadBuffer(mode: scala.Int): scala.Unit = {
    calls = calls + 1
    this.gl30.glReadBuffer(mode)
    this.check()
  }
  def glDrawRangeElements(mode: scala.Int, start: scala.Int, `end`: scala.Int, count: scala.Int, `type`: scala.Int, indices: java.nio.Buffer): scala.Unit = {
    vertexCount.put(count)
    drawCalls = drawCalls + 1
    calls = calls + 1
    this.gl30.glDrawRangeElements(mode, start, `end`, count, `type`, indices)
    this.check()
  }
  def glDrawRangeElements(mode: scala.Int, start: scala.Int, `end`: scala.Int, count: scala.Int, `type`: scala.Int, offset: scala.Int): scala.Unit = {
    vertexCount.put(count)
    drawCalls = drawCalls + 1
    calls = calls + 1
    this.gl30.glDrawRangeElements(mode, start, `end`, count, `type`, offset)
    this.check()
  }
  def glTexImage3D(target: scala.Int, level: scala.Int, internalformat: scala.Int, width: scala.Int, height: scala.Int, depth: scala.Int, border: scala.Int, format: scala.Int, `type`: scala.Int, pixels: java.nio.Buffer): scala.Unit = {
    calls = calls + 1
    this.gl30.glTexImage3D(target, level, internalformat, width, height, depth, border, format, `type`, pixels)
    this.check()
  }
  def glTexImage3D(target: scala.Int, level: scala.Int, internalformat: scala.Int, width: scala.Int, height: scala.Int, depth: scala.Int, border: scala.Int, format: scala.Int, `type`: scala.Int, offset: scala.Int): scala.Unit = {
    calls = calls + 1
    this.gl30.glTexImage3D(target, level, internalformat, width, height, depth, border, format, `type`, offset)
    this.check()
  }
  def glTexSubImage3D(target: scala.Int, level: scala.Int, xoffset: scala.Int, yoffset: scala.Int, zoffset: scala.Int, width: scala.Int, height: scala.Int, depth: scala.Int, format: scala.Int, `type`: scala.Int, pixels: java.nio.Buffer): scala.Unit = {
    calls = calls + 1
    this.gl30.glTexSubImage3D(target, level, xoffset, yoffset, zoffset, width, height, depth, format, `type`, pixels)
    this.check()
  }
  def glTexSubImage3D(target: scala.Int, level: scala.Int, xoffset: scala.Int, yoffset: scala.Int, zoffset: scala.Int, width: scala.Int, height: scala.Int, depth: scala.Int, format: scala.Int, `type`: scala.Int, offset: scala.Int): scala.Unit = {
    calls = calls + 1
    this.gl30.glTexSubImage3D(target, level, xoffset, yoffset, zoffset, width, height, depth, format, `type`, offset)
    this.check()
  }
  def glCopyTexSubImage3D(target: scala.Int, level: scala.Int, xoffset: scala.Int, yoffset: scala.Int, zoffset: scala.Int, x: scala.Int, y: scala.Int, width: scala.Int, height: scala.Int): scala.Unit = {
    calls = calls + 1
    this.gl30.glCopyTexSubImage3D(target, level, xoffset, yoffset, zoffset, x, y, width, height)
    this.check()
  }
  def glGenQueries(n: scala.Int, ids: scala.Array[scala.Int], offset: scala.Int): scala.Unit = {
    calls = calls + 1
    this.gl30.glGenQueries(n, ids, offset)
    this.check()
  }
  def glGenQueries(n: scala.Int, ids: java.nio.IntBuffer): scala.Unit = {
    calls = calls + 1
    this.gl30.glGenQueries(n, ids)
    this.check()
  }
  def glDeleteQueries(n: scala.Int, ids: scala.Array[scala.Int], offset: scala.Int): scala.Unit = {
    calls = calls + 1
    this.gl30.glDeleteQueries(n, ids, offset)
    this.check()
  }
  def glDeleteQueries(n: scala.Int, ids: java.nio.IntBuffer): scala.Unit = {
    calls = calls + 1
    this.gl30.glDeleteQueries(n, ids)
    this.check()
  }
  def glIsQuery(id: scala.Int): scala.Boolean = {
    calls = calls + 1
    val result: scala.Boolean = this.gl30.glIsQuery(id)
    this.check()
    return result
  }
  def glBeginQuery(target: scala.Int, id: scala.Int): scala.Unit = {
    calls = calls + 1
    this.gl30.glBeginQuery(target, id)
    this.check()
  }
  def glEndQuery(target: scala.Int): scala.Unit = {
    calls = calls + 1
    this.gl30.glEndQuery(target)
    this.check()
  }
  def glGetQueryiv(target: scala.Int, pname: scala.Int, params: java.nio.IntBuffer): scala.Unit = {
    calls = calls + 1
    this.gl30.glGetQueryiv(target, pname, params)
    this.check()
  }
  def glGetQueryObjectuiv(id: scala.Int, pname: scala.Int, params: java.nio.IntBuffer): scala.Unit = {
    calls = calls + 1
    this.gl30.glGetQueryObjectuiv(id, pname, params)
    this.check()
  }
  def glUnmapBuffer(target: scala.Int): scala.Boolean = {
    calls = calls + 1
    val result: scala.Boolean = this.gl30.glUnmapBuffer(target)
    this.check()
    return result
  }
  def glGetBufferPointerv(target: scala.Int, pname: scala.Int): java.nio.Buffer = {
    calls = calls + 1
    val result: java.nio.Buffer = this.gl30.glGetBufferPointerv(target, pname)
    this.check()
    return result
  }
  def glDrawBuffers(n: scala.Int, bufs: java.nio.IntBuffer): scala.Unit = {
    drawCalls = drawCalls + 1
    calls = calls + 1
    this.gl30.glDrawBuffers(n, bufs)
    this.check()
  }
  def glUniformMatrix2x3fv(location: scala.Int, count: scala.Int, transpose: scala.Boolean, value: java.nio.FloatBuffer): scala.Unit = {
    calls = calls + 1
    this.gl30.glUniformMatrix2x3fv(location, count, transpose, value)
    this.check()
  }
  def glUniformMatrix3x2fv(location: scala.Int, count: scala.Int, transpose: scala.Boolean, value: java.nio.FloatBuffer): scala.Unit = {
    calls = calls + 1
    this.gl30.glUniformMatrix3x2fv(location, count, transpose, value)
    this.check()
  }
  def glUniformMatrix2x4fv(location: scala.Int, count: scala.Int, transpose: scala.Boolean, value: java.nio.FloatBuffer): scala.Unit = {
    calls = calls + 1
    this.gl30.glUniformMatrix2x4fv(location, count, transpose, value)
    this.check()
  }
  def glUniformMatrix4x2fv(location: scala.Int, count: scala.Int, transpose: scala.Boolean, value: java.nio.FloatBuffer): scala.Unit = {
    calls = calls + 1
    this.gl30.glUniformMatrix4x2fv(location, count, transpose, value)
    this.check()
  }
  def glUniformMatrix3x4fv(location: scala.Int, count: scala.Int, transpose: scala.Boolean, value: java.nio.FloatBuffer): scala.Unit = {
    calls = calls + 1
    this.gl30.glUniformMatrix3x4fv(location, count, transpose, value)
    this.check()
  }
  def glUniformMatrix4x3fv(location: scala.Int, count: scala.Int, transpose: scala.Boolean, value: java.nio.FloatBuffer): scala.Unit = {
    calls = calls + 1
    this.gl30.glUniformMatrix4x3fv(location, count, transpose, value)
    this.check()
  }
  def glBlitFramebuffer(srcX0: scala.Int, srcY0: scala.Int, srcX1: scala.Int, srcY1: scala.Int, dstX0: scala.Int, dstY0: scala.Int, dstX1: scala.Int, dstY1: scala.Int, mask: scala.Int, filter: scala.Int): scala.Unit = {
    calls = calls + 1
    this.gl30.glBlitFramebuffer(srcX0, srcY0, srcX1, srcY1, dstX0, dstY0, dstX1, dstY1, mask, filter)
    this.check()
  }
  def glRenderbufferStorageMultisample(target: scala.Int, samples: scala.Int, internalformat: scala.Int, width: scala.Int, height: scala.Int): scala.Unit = {
    calls = calls + 1
    this.gl30.glRenderbufferStorageMultisample(target, samples, internalformat, width, height)
    this.check()
  }
  def glFramebufferTextureLayer(target: scala.Int, attachment: scala.Int, texture: scala.Int, level: scala.Int, layer: scala.Int): scala.Unit = {
    calls = calls + 1
    this.gl30.glFramebufferTextureLayer(target, attachment, texture, level, layer)
    this.check()
  }
  def glMapBufferRange(target: scala.Int, offset: scala.Int, length: scala.Int, access: scala.Int): java.nio.Buffer = {
    calls = calls + 1
    val result: java.nio.Buffer = this.gl30.glMapBufferRange(target, offset, length, access)
    this.check()
    return result
  }
  def glFlushMappedBufferRange(target: scala.Int, offset: scala.Int, length: scala.Int): scala.Unit = {
    calls = calls + 1
    this.gl30.glFlushMappedBufferRange(target, offset, length)
    this.check()
  }
  def glBindVertexArray(array: scala.Int): scala.Unit = {
    calls = calls + 1
    this.gl30.glBindVertexArray(array)
    this.check()
  }
  def glDeleteVertexArrays(n: scala.Int, arrays: scala.Array[scala.Int], offset: scala.Int): scala.Unit = {
    calls = calls + 1
    this.gl30.glDeleteVertexArrays(n, arrays, offset)
    this.check()
  }
  def glDeleteVertexArrays(n: scala.Int, arrays: java.nio.IntBuffer): scala.Unit = {
    calls = calls + 1
    this.gl30.glDeleteVertexArrays(n, arrays)
    this.check()
  }
  def glGenVertexArrays(n: scala.Int, arrays: scala.Array[scala.Int], offset: scala.Int): scala.Unit = {
    calls = calls + 1
    this.gl30.glGenVertexArrays(n, arrays, offset)
    this.check()
  }
  def glGenVertexArrays(n: scala.Int, arrays: java.nio.IntBuffer): scala.Unit = {
    calls = calls + 1
    this.gl30.glGenVertexArrays(n, arrays)
    this.check()
  }
  def glIsVertexArray(array: scala.Int): scala.Boolean = {
    calls = calls + 1
    val result: scala.Boolean = this.gl30.glIsVertexArray(array)
    this.check()
    return result
  }
  def glBeginTransformFeedback(primitiveMode: scala.Int): scala.Unit = {
    calls = calls + 1
    this.gl30.glBeginTransformFeedback(primitiveMode)
    this.check()
  }
  def glEndTransformFeedback(): scala.Unit = {
    calls = calls + 1
    this.gl30.glEndTransformFeedback()
    this.check()
  }
  def glBindBufferRange(target: scala.Int, index: scala.Int, buffer: scala.Int, offset: scala.Int, size: scala.Int): scala.Unit = {
    calls = calls + 1
    this.gl30.glBindBufferRange(target, index, buffer, offset, size)
    this.check()
  }
  def glBindBufferBase(target: scala.Int, index: scala.Int, buffer: scala.Int): scala.Unit = {
    calls = calls + 1
    this.gl30.glBindBufferBase(target, index, buffer)
    this.check()
  }
  def glTransformFeedbackVaryings(program: scala.Int, varyings: scala.Array[java.lang.String], bufferMode: scala.Int): scala.Unit = {
    calls = calls + 1
    this.gl30.glTransformFeedbackVaryings(program, varyings, bufferMode)
    this.check()
  }
  def glVertexAttribIPointer(index: scala.Int, size: scala.Int, `type`: scala.Int, stride: scala.Int, offset: scala.Int): scala.Unit = {
    calls = calls + 1
    this.gl30.glVertexAttribIPointer(index, size, `type`, stride, offset)
    this.check()
  }
  def glGetVertexAttribIiv(index: scala.Int, pname: scala.Int, params: java.nio.IntBuffer): scala.Unit = {
    calls = calls + 1
    this.gl30.glGetVertexAttribIiv(index, pname, params)
    this.check()
  }
  def glGetVertexAttribIuiv(index: scala.Int, pname: scala.Int, params: java.nio.IntBuffer): scala.Unit = {
    calls = calls + 1
    this.gl30.glGetVertexAttribIuiv(index, pname, params)
    this.check()
  }
  def glVertexAttribI4i(index: scala.Int, x: scala.Int, y: scala.Int, z: scala.Int, w: scala.Int): scala.Unit = {
    calls = calls + 1
    this.gl30.glVertexAttribI4i(index, x, y, z, w)
    this.check()
  }
  def glVertexAttribI4ui(index: scala.Int, x: scala.Int, y: scala.Int, z: scala.Int, w: scala.Int): scala.Unit = {
    calls = calls + 1
    this.gl30.glVertexAttribI4ui(index, x, y, z, w)
    this.check()
  }
  def glGetUniformuiv(program: scala.Int, location: scala.Int, params: java.nio.IntBuffer): scala.Unit = {
    calls = calls + 1
    this.gl30.glGetUniformuiv(program, location, params)
    this.check()
  }
  def glGetFragDataLocation(program: scala.Int, name: java.lang.String): scala.Int = {
    calls = calls + 1
    val result: scala.Int = this.gl30.glGetFragDataLocation(program, name)
    this.check()
    return result
  }
  def glUniform1uiv(location: scala.Int, count: scala.Int, value: java.nio.IntBuffer): scala.Unit = {
    calls = calls + 1
    this.gl30.glUniform1uiv(location, count, value)
    this.check()
  }
  def glUniform3uiv(location: scala.Int, count: scala.Int, value: java.nio.IntBuffer): scala.Unit = {
    calls = calls + 1
    this.gl30.glUniform3uiv(location, count, value)
    this.check()
  }
  def glUniform4uiv(location: scala.Int, count: scala.Int, value: java.nio.IntBuffer): scala.Unit = {
    calls = calls + 1
    this.gl30.glUniform4uiv(location, count, value)
    this.check()
  }
  def glClearBufferiv(buffer: scala.Int, drawbuffer: scala.Int, value: java.nio.IntBuffer): scala.Unit = {
    calls = calls + 1
    this.gl30.glClearBufferiv(buffer, drawbuffer, value)
    this.check()
  }
  def glClearBufferuiv(buffer: scala.Int, drawbuffer: scala.Int, value: java.nio.IntBuffer): scala.Unit = {
    calls = calls + 1
    this.gl30.glClearBufferuiv(buffer, drawbuffer, value)
    this.check()
  }
  def glClearBufferfv(buffer: scala.Int, drawbuffer: scala.Int, value: java.nio.FloatBuffer): scala.Unit = {
    calls = calls + 1
    this.gl30.glClearBufferfv(buffer, drawbuffer, value)
    this.check()
  }
  def glClearBufferfi(buffer: scala.Int, drawbuffer: scala.Int, depth: scala.Float, stencil: scala.Int): scala.Unit = {
    calls = calls + 1
    this.gl30.glClearBufferfi(buffer, drawbuffer, depth, stencil)
    this.check()
  }
  def glGetStringi(name: scala.Int, index: scala.Int): java.lang.String = {
    calls = calls + 1
    val result: java.lang.String = this.gl30.glGetStringi(name, index)
    this.check()
    return result
  }
  def glCopyBufferSubData(readTarget: scala.Int, writeTarget: scala.Int, readOffset: scala.Int, writeOffset: scala.Int, size: scala.Int): scala.Unit = {
    calls = calls + 1
    this.gl30.glCopyBufferSubData(readTarget, writeTarget, readOffset, writeOffset, size)
    this.check()
  }
  def glGetUniformIndices(program: scala.Int, uniformNames: scala.Array[java.lang.String], uniformIndices: java.nio.IntBuffer): scala.Unit = {
    calls = calls + 1
    this.gl30.glGetUniformIndices(program, uniformNames, uniformIndices)
    this.check()
  }
  def glGetActiveUniformsiv(program: scala.Int, uniformCount: scala.Int, uniformIndices: java.nio.IntBuffer, pname: scala.Int, params: java.nio.IntBuffer): scala.Unit = {
    calls = calls + 1
    this.gl30.glGetActiveUniformsiv(program, uniformCount, uniformIndices, pname, params)
    this.check()
  }
  def glGetUniformBlockIndex(program: scala.Int, uniformBlockName: java.lang.String): scala.Int = {
    calls = calls + 1
    val result: scala.Int = this.gl30.glGetUniformBlockIndex(program, uniformBlockName)
    this.check()
    return result
  }
  def glGetActiveUniformBlockiv(program: scala.Int, uniformBlockIndex: scala.Int, pname: scala.Int, params: java.nio.IntBuffer): scala.Unit = {
    calls = calls + 1
    this.gl30.glGetActiveUniformBlockiv(program, uniformBlockIndex, pname, params)
    this.check()
  }
  def glGetActiveUniformBlockName(program: scala.Int, uniformBlockIndex: scala.Int, length: java.nio.Buffer, uniformBlockName: java.nio.Buffer): scala.Unit = {
    calls = calls + 1
    this.gl30.glGetActiveUniformBlockName(program, uniformBlockIndex, length, uniformBlockName)
    this.check()
  }
  def glGetActiveUniformBlockName(program: scala.Int, uniformBlockIndex: scala.Int): java.lang.String = {
    calls = calls + 1
    val result: java.lang.String = this.gl30.glGetActiveUniformBlockName(program, uniformBlockIndex)
    this.check()
    return result
  }
  def glUniformBlockBinding(program: scala.Int, uniformBlockIndex: scala.Int, uniformBlockBinding: scala.Int): scala.Unit = {
    calls = calls + 1
    this.gl30.glUniformBlockBinding(program, uniformBlockIndex, uniformBlockBinding)
    this.check()
  }
  def glDrawArraysInstanced(mode: scala.Int, first: scala.Int, count: scala.Int, instanceCount: scala.Int): scala.Unit = {
    vertexCount.put(count)
    drawCalls = drawCalls + 1
    calls = calls + 1
    this.gl30.glDrawArraysInstanced(mode, first, count, instanceCount)
    this.check()
  }
  def glDrawElementsInstanced(mode: scala.Int, count: scala.Int, `type`: scala.Int, indicesOffset: scala.Int, instanceCount: scala.Int): scala.Unit = {
    vertexCount.put(count)
    drawCalls = drawCalls + 1
    calls = calls + 1
    this.gl30.glDrawElementsInstanced(mode, count, `type`, indicesOffset, instanceCount)
    this.check()
  }
  def glGetInteger64v(pname: scala.Int, params: java.nio.LongBuffer): scala.Unit = {
    calls = calls + 1
    this.gl30.glGetInteger64v(pname, params)
    this.check()
  }
  def glGetBufferParameteri64v(target: scala.Int, pname: scala.Int, params: java.nio.LongBuffer): scala.Unit = {
    calls = calls + 1
    this.gl30.glGetBufferParameteri64v(target, pname, params)
    this.check()
  }
  def glGenSamplers(count: scala.Int, samplers: scala.Array[scala.Int], offset: scala.Int): scala.Unit = {
    calls = calls + 1
    this.gl30.glGenSamplers(count, samplers, offset)
    this.check()
  }
  def glGenSamplers(count: scala.Int, samplers: java.nio.IntBuffer): scala.Unit = {
    calls = calls + 1
    this.gl30.glGenSamplers(count, samplers)
    this.check()
  }
  def glDeleteSamplers(count: scala.Int, samplers: scala.Array[scala.Int], offset: scala.Int): scala.Unit = {
    calls = calls + 1
    this.gl30.glDeleteSamplers(count, samplers, offset)
    this.check()
  }
  def glDeleteSamplers(count: scala.Int, samplers: java.nio.IntBuffer): scala.Unit = {
    calls = calls + 1
    this.gl30.glDeleteSamplers(count, samplers)
    this.check()
  }
  def glIsSampler(sampler: scala.Int): scala.Boolean = {
    calls = calls + 1
    val result: scala.Boolean = this.gl30.glIsSampler(sampler)
    this.check()
    return result
  }
  def glBindSampler(unit: scala.Int, sampler: scala.Int): scala.Unit = {
    calls = calls + 1
    this.gl30.glBindSampler(unit, sampler)
    this.check()
  }
  def glSamplerParameteri(sampler: scala.Int, pname: scala.Int, param: scala.Int): scala.Unit = {
    calls = calls + 1
    this.gl30.glSamplerParameteri(sampler, pname, param)
    this.check()
  }
  def glSamplerParameteriv(sampler: scala.Int, pname: scala.Int, param: java.nio.IntBuffer): scala.Unit = {
    calls = calls + 1
    this.gl30.glSamplerParameteriv(sampler, pname, param)
    this.check()
  }
  def glSamplerParameterf(sampler: scala.Int, pname: scala.Int, param: scala.Float): scala.Unit = {
    calls = calls + 1
    this.gl30.glSamplerParameterf(sampler, pname, param)
    this.check()
  }
  def glSamplerParameterfv(sampler: scala.Int, pname: scala.Int, param: java.nio.FloatBuffer): scala.Unit = {
    calls = calls + 1
    this.gl30.glSamplerParameterfv(sampler, pname, param)
    this.check()
  }
  def glGetSamplerParameteriv(sampler: scala.Int, pname: scala.Int, params: java.nio.IntBuffer): scala.Unit = {
    calls = calls + 1
    this.gl30.glGetSamplerParameteriv(sampler, pname, params)
    this.check()
  }
  def glGetSamplerParameterfv(sampler: scala.Int, pname: scala.Int, params: java.nio.FloatBuffer): scala.Unit = {
    calls = calls + 1
    this.gl30.glGetSamplerParameterfv(sampler, pname, params)
    this.check()
  }
  def glVertexAttribDivisor(index: scala.Int, divisor: scala.Int): scala.Unit = {
    calls = calls + 1
    this.gl30.glVertexAttribDivisor(index, divisor)
    this.check()
  }
  def glBindTransformFeedback(target: scala.Int, id: scala.Int): scala.Unit = {
    calls = calls + 1
    this.gl30.glBindTransformFeedback(target, id)
    this.check()
  }
  def glDeleteTransformFeedbacks(n: scala.Int, ids: scala.Array[scala.Int], offset: scala.Int): scala.Unit = {
    calls = calls + 1
    this.gl30.glDeleteTransformFeedbacks(n, ids, offset)
    this.check()
  }
  def glDeleteTransformFeedbacks(n: scala.Int, ids: java.nio.IntBuffer): scala.Unit = {
    calls = calls + 1
    this.gl30.glDeleteTransformFeedbacks(n, ids)
    this.check()
  }
  def glGenTransformFeedbacks(n: scala.Int, ids: scala.Array[scala.Int], offset: scala.Int): scala.Unit = {
    calls = calls + 1
    this.gl30.glGenTransformFeedbacks(n, ids, offset)
    this.check()
  }
  def glGenTransformFeedbacks(n: scala.Int, ids: java.nio.IntBuffer): scala.Unit = {
    calls = calls + 1
    this.gl30.glGenTransformFeedbacks(n, ids)
    this.check()
  }
  def glIsTransformFeedback(id: scala.Int): scala.Boolean = {
    calls = calls + 1
    val result: scala.Boolean = this.gl30.glIsTransformFeedback(id)
    this.check()
    return result
  }
  def glPauseTransformFeedback(): scala.Unit = {
    calls = calls + 1
    this.gl30.glPauseTransformFeedback()
    this.check()
  }
  def glResumeTransformFeedback(): scala.Unit = {
    calls = calls + 1
    this.gl30.glResumeTransformFeedback()
    this.check()
  }
  def glProgramParameteri(program: scala.Int, pname: scala.Int, value: scala.Int): scala.Unit = {
    calls = calls + 1
    this.gl30.glProgramParameteri(program, pname, value)
    this.check()
  }
  def glInvalidateFramebuffer(target: scala.Int, numAttachments: scala.Int, attachments: java.nio.IntBuffer): scala.Unit = {
    calls = calls + 1
    this.gl30.glInvalidateFramebuffer(target, numAttachments, attachments)
    this.check()
  }
  def glInvalidateSubFramebuffer(target: scala.Int, numAttachments: scala.Int, attachments: java.nio.IntBuffer, x: scala.Int, y: scala.Int, width: scala.Int, height: scala.Int): scala.Unit = {
    calls = calls + 1
    this.gl30.glInvalidateSubFramebuffer(target, numAttachments, attachments, x, y, width, height)
    this.check()
  }
}