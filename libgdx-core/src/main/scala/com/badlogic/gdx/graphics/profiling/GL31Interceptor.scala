package com.badlogic.gdx.graphics.profiling

class GL31Interceptor extends com.badlogic.gdx.graphics.profiling.GL30Interceptor with com.badlogic.gdx.graphics.GL31 {
  var gl31: com.badlogic.gdx.graphics.GL31 = null.asInstanceOf[com.badlogic.gdx.graphics.GL31]
  def this(glProfiler: com.badlogic.gdx.graphics.profiling.GLProfiler, gl31: com.badlogic.gdx.graphics.GL31) = {
    this()
    this.gl31 = gl31
  }
  def check(): scala.Unit = {
    var error: scala.Int = gl30.glGetError()
    while (error != com.badlogic.gdx.graphics.GL20.GL_NO_ERROR) {
      glProfiler.getListener().onError(error)
      error = gl30.glGetError()
    }
  }
  def glDispatchCompute(num_groups_x: scala.Int, num_groups_y: scala.Int, num_groups_z: scala.Int): scala.Unit = {
    calls = calls + 1
    this.gl31.glDispatchCompute(num_groups_x, num_groups_y, num_groups_z)
    this.check()
  }
  def glDispatchComputeIndirect(indirect: scala.Long): scala.Unit = {
    calls = calls + 1
    this.gl31.glDispatchComputeIndirect(indirect)
    this.check()
  }
  def glDrawArraysIndirect(mode: scala.Int, indirect: scala.Long): scala.Unit = {
    drawCalls = drawCalls + 1
    calls = calls + 1
    this.gl31.glDrawArraysIndirect(mode, indirect)
    this.check()
  }
  def glDrawElementsIndirect(mode: scala.Int, `type`: scala.Int, indirect: scala.Long): scala.Unit = {
    drawCalls = drawCalls + 1
    calls = calls + 1
    this.gl31.glDrawElementsIndirect(mode, `type`, indirect)
    this.check()
  }
  def glFramebufferParameteri(target: scala.Int, pname: scala.Int, param: scala.Int): scala.Unit = {
    calls = calls + 1
    this.gl31.glFramebufferParameteri(target, pname, param)
    this.check()
  }
  def glGetFramebufferParameteriv(target: scala.Int, pname: scala.Int, params: java.nio.IntBuffer): scala.Unit = {
    calls = calls + 1
    this.gl31.glGetFramebufferParameteriv(target, pname, params)
    this.check()
  }
  def glGetProgramInterfaceiv(program: scala.Int, programInterface: scala.Int, pname: scala.Int, params: java.nio.IntBuffer): scala.Unit = {
    calls = calls + 1
    this.gl31.glGetProgramInterfaceiv(program, programInterface, pname, params)
    this.check()
  }
  def glGetProgramResourceIndex(program: scala.Int, programInterface: scala.Int, name: java.lang.String): scala.Int = {
    calls = calls + 1
    val v: scala.Int = this.gl31.glGetProgramResourceIndex(program, programInterface, name)
    this.check()
    return v
  }
  def glGetProgramResourceName(program: scala.Int, programInterface: scala.Int, index: scala.Int): java.lang.String = {
    calls = calls + 1
    val s: java.lang.String = this.gl31.glGetProgramResourceName(program, programInterface, index)
    this.check()
    return s
  }
  def glGetProgramResourceiv(program: scala.Int, programInterface: scala.Int, index: scala.Int, props: java.nio.IntBuffer, length: java.nio.IntBuffer, params: java.nio.IntBuffer): scala.Unit = {
    calls = calls + 1
    this.gl31.glGetProgramResourceiv(program, programInterface, index, props, length, params)
    this.check()
  }
  def glGetProgramResourceLocation(program: scala.Int, programInterface: scala.Int, name: java.lang.String): scala.Int = {
    calls = calls + 1
    val v: scala.Int = this.gl31.glGetProgramResourceLocation(program, programInterface, name)
    this.check()
    return v
  }
  def glUseProgramStages(pipeline: scala.Int, stages: scala.Int, program: scala.Int): scala.Unit = {
    calls = calls + 1
    this.gl31.glUseProgramStages(pipeline, stages, program)
    this.check()
  }
  def glActiveShaderProgram(pipeline: scala.Int, program: scala.Int): scala.Unit = {
    calls = calls + 1
    this.gl31.glActiveShaderProgram(pipeline, program)
    this.check()
  }
  def glCreateShaderProgramv(`type`: scala.Int, strings: scala.Array[java.lang.String]): scala.Int = {
    calls = calls + 1
    val v: scala.Int = this.gl31.glCreateShaderProgramv(`type`, strings)
    this.check()
    return v
  }
  def glBindProgramPipeline(pipeline: scala.Int): scala.Unit = {
    calls = calls + 1
    this.gl31.glBindProgramPipeline(pipeline)
    this.check()
  }
  def glDeleteProgramPipelines(count: scala.Int, pipelines: java.nio.IntBuffer): scala.Unit = {
    calls = calls + 1
    this.gl31.glDeleteProgramPipelines(count, pipelines)
    this.check()
  }
  def glGenProgramPipelines(count: scala.Int, pipelines: java.nio.IntBuffer): scala.Unit = {
    calls = calls + 1
    this.gl31.glGenProgramPipelines(count, pipelines)
    this.check()
  }
  def glIsProgramPipeline(pipeline: scala.Int): scala.Boolean = {
    calls = calls + 1
    val v: scala.Boolean = this.gl31.glIsProgramPipeline(pipeline)
    this.check()
    return v
  }
  def glGetProgramPipelineiv(pipeline: scala.Int, pname: scala.Int, params: java.nio.IntBuffer): scala.Unit = {
    calls = calls + 1
    this.gl31.glGetProgramPipelineiv(pipeline, pname, params)
    this.check()
  }
  def glProgramUniform1i(program: scala.Int, location: scala.Int, v0: scala.Int): scala.Unit = {
    calls = calls + 1
    this.gl31.glProgramUniform1i(program, location, v0)
    this.check()
  }
  def glProgramUniform2i(program: scala.Int, location: scala.Int, v0: scala.Int, v1: scala.Int): scala.Unit = {
    calls = calls + 1
    this.gl31.glProgramUniform2i(program, location, v0, v1)
    this.check()
  }
  def glProgramUniform3i(program: scala.Int, location: scala.Int, v0: scala.Int, v1: scala.Int, v2: scala.Int): scala.Unit = {
    calls = calls + 1
    this.gl31.glProgramUniform3i(program, location, v0, v1, v2)
    this.check()
  }
  def glProgramUniform4i(program: scala.Int, location: scala.Int, v0: scala.Int, v1: scala.Int, v2: scala.Int, v3: scala.Int): scala.Unit = {
    calls = calls + 1
    this.gl31.glProgramUniform4i(program, location, v0, v1, v2, v3)
    this.check()
  }
  def glProgramUniform1ui(program: scala.Int, location: scala.Int, v0: scala.Int): scala.Unit = {
    calls = calls + 1
    this.gl31.glProgramUniform1ui(program, location, v0)
    this.check()
  }
  def glProgramUniform2ui(program: scala.Int, location: scala.Int, v0: scala.Int, v1: scala.Int): scala.Unit = {
    calls = calls + 1
    this.gl31.glProgramUniform2ui(program, location, v0, v1)
    this.check()
  }
  def glProgramUniform3ui(program: scala.Int, location: scala.Int, v0: scala.Int, v1: scala.Int, v2: scala.Int): scala.Unit = {
    calls = calls + 1
    this.gl31.glProgramUniform3ui(program, location, v0, v1, v2)
    this.check()
  }
  def glProgramUniform4ui(program: scala.Int, location: scala.Int, v0: scala.Int, v1: scala.Int, v2: scala.Int, v3: scala.Int): scala.Unit = {
    calls = calls + 1
    this.gl31.glProgramUniform4ui(program, location, v0, v1, v2, v3)
    this.check()
  }
  def glProgramUniform1f(program: scala.Int, location: scala.Int, v0: scala.Float): scala.Unit = {
    calls = calls + 1
    this.gl31.glProgramUniform1f(program, location, v0)
    this.check()
  }
  def glProgramUniform2f(program: scala.Int, location: scala.Int, v0: scala.Float, v1: scala.Float): scala.Unit = {
    calls = calls + 1
    this.gl31.glProgramUniform2f(program, location, v0, v1)
    this.check()
  }
  def glProgramUniform3f(program: scala.Int, location: scala.Int, v0: scala.Float, v1: scala.Float, v2: scala.Float): scala.Unit = {
    calls = calls + 1
    this.gl31.glProgramUniform3f(program, location, v0, v1, v2)
    this.check()
  }
  def glProgramUniform4f(program: scala.Int, location: scala.Int, v0: scala.Float, v1: scala.Float, v2: scala.Float, v3: scala.Float): scala.Unit = {
    calls = calls + 1
    this.gl31.glProgramUniform4f(program, location, v0, v1, v2, v3)
    this.check()
  }
  def glProgramUniform1iv(program: scala.Int, location: scala.Int, value: java.nio.IntBuffer): scala.Unit = {
    calls = calls + 1
    this.gl31.glProgramUniform1iv(program, location, value)
    this.check()
  }
  def glProgramUniform2iv(program: scala.Int, location: scala.Int, value: java.nio.IntBuffer): scala.Unit = {
    calls = calls + 1
    this.gl31.glProgramUniform2iv(program, location, value)
    this.check()
  }
  def glProgramUniform3iv(program: scala.Int, location: scala.Int, value: java.nio.IntBuffer): scala.Unit = {
    calls = calls + 1
    this.gl31.glProgramUniform3iv(program, location, value)
    this.check()
  }
  def glProgramUniform4iv(program: scala.Int, location: scala.Int, value: java.nio.IntBuffer): scala.Unit = {
    calls = calls + 1
    this.gl31.glProgramUniform4iv(program, location, value)
    this.check()
  }
  def glProgramUniform1uiv(program: scala.Int, location: scala.Int, value: java.nio.IntBuffer): scala.Unit = {
    calls = calls + 1
    this.gl31.glProgramUniform1uiv(program, location, value)
    this.check()
  }
  def glProgramUniform2uiv(program: scala.Int, location: scala.Int, value: java.nio.IntBuffer): scala.Unit = {
    calls = calls + 1
    this.gl31.glProgramUniform2uiv(program, location, value)
    this.check()
  }
  def glProgramUniform3uiv(program: scala.Int, location: scala.Int, value: java.nio.IntBuffer): scala.Unit = {
    calls = calls + 1
    this.gl31.glProgramUniform3uiv(program, location, value)
    this.check()
  }
  def glProgramUniform4uiv(program: scala.Int, location: scala.Int, value: java.nio.IntBuffer): scala.Unit = {
    calls = calls + 1
    this.gl31.glProgramUniform4uiv(program, location, value)
    this.check()
  }
  def glProgramUniform1fv(program: scala.Int, location: scala.Int, value: java.nio.FloatBuffer): scala.Unit = {
    calls = calls + 1
    this.gl31.glProgramUniform1fv(program, location, value)
    this.check()
  }
  def glProgramUniform2fv(program: scala.Int, location: scala.Int, value: java.nio.FloatBuffer): scala.Unit = {
    calls = calls + 1
    this.gl31.glProgramUniform2fv(program, location, value)
    this.check()
  }
  def glProgramUniform3fv(program: scala.Int, location: scala.Int, value: java.nio.FloatBuffer): scala.Unit = {
    calls = calls + 1
    this.gl31.glProgramUniform3fv(program, location, value)
    this.check()
  }
  def glProgramUniform4fv(program: scala.Int, location: scala.Int, value: java.nio.FloatBuffer): scala.Unit = {
    calls = calls + 1
    this.gl31.glProgramUniform4fv(program, location, value)
    this.check()
  }
  def glProgramUniformMatrix2fv(program: scala.Int, location: scala.Int, transpose: scala.Boolean, value: java.nio.FloatBuffer): scala.Unit = {
    calls = calls + 1
    this.gl31.glProgramUniformMatrix2fv(program, location, transpose, value)
    this.check()
  }
  def glProgramUniformMatrix3fv(program: scala.Int, location: scala.Int, transpose: scala.Boolean, value: java.nio.FloatBuffer): scala.Unit = {
    calls = calls + 1
    this.gl31.glProgramUniformMatrix3fv(program, location, transpose, value)
    this.check()
  }
  def glProgramUniformMatrix4fv(program: scala.Int, location: scala.Int, transpose: scala.Boolean, value: java.nio.FloatBuffer): scala.Unit = {
    calls = calls + 1
    this.gl31.glProgramUniformMatrix4fv(program, location, transpose, value)
    this.check()
  }
  def glProgramUniformMatrix2x3fv(program: scala.Int, location: scala.Int, transpose: scala.Boolean, value: java.nio.FloatBuffer): scala.Unit = {
    calls = calls + 1
    this.gl31.glProgramUniformMatrix2x3fv(program, location, transpose, value)
    this.check()
  }
  def glProgramUniformMatrix3x2fv(program: scala.Int, location: scala.Int, transpose: scala.Boolean, value: java.nio.FloatBuffer): scala.Unit = {
    calls = calls + 1
    this.gl31.glProgramUniformMatrix3x2fv(program, location, transpose, value)
    this.check()
  }
  def glProgramUniformMatrix2x4fv(program: scala.Int, location: scala.Int, transpose: scala.Boolean, value: java.nio.FloatBuffer): scala.Unit = {
    calls = calls + 1
    this.gl31.glProgramUniformMatrix2x4fv(program, location, transpose, value)
    this.check()
  }
  def glProgramUniformMatrix4x2fv(program: scala.Int, location: scala.Int, transpose: scala.Boolean, value: java.nio.FloatBuffer): scala.Unit = {
    calls = calls + 1
    this.gl31.glProgramUniformMatrix4x2fv(program, location, transpose, value)
    this.check()
  }
  def glProgramUniformMatrix3x4fv(program: scala.Int, location: scala.Int, transpose: scala.Boolean, value: java.nio.FloatBuffer): scala.Unit = {
    calls = calls + 1
    this.gl31.glProgramUniformMatrix3x4fv(program, location, transpose, value)
    this.check()
  }
  def glProgramUniformMatrix4x3fv(program: scala.Int, location: scala.Int, transpose: scala.Boolean, value: java.nio.FloatBuffer): scala.Unit = {
    calls = calls + 1
    this.gl31.glProgramUniformMatrix4x3fv(program, location, transpose, value)
    this.check()
  }
  def glValidateProgramPipeline(pipeline: scala.Int): scala.Unit = {
    calls = calls + 1
    this.gl31.glValidateProgramPipeline(pipeline)
    this.check()
  }
  def glGetProgramPipelineInfoLog(program: scala.Int): java.lang.String = {
    calls = calls + 1
    val s: java.lang.String = this.gl31.glGetProgramPipelineInfoLog(program)
    this.check()
    return s
  }
  def glBindImageTexture(unit: scala.Int, texture: scala.Int, level: scala.Int, layered: scala.Boolean, layer: scala.Int, access: scala.Int, format: scala.Int): scala.Unit = {
    calls = calls + 1
    this.gl31.glBindImageTexture(unit, texture, level, layered, layer, access, format)
    this.check()
  }
  def glGetBooleani_v(target: scala.Int, index: scala.Int, data: java.nio.IntBuffer): scala.Unit = {
    calls = calls + 1
    this.gl31.glGetBooleani_v(target, index, data)
    this.check()
  }
  def glMemoryBarrier(barriers: scala.Int): scala.Unit = {
    calls = calls + 1
    this.gl31.glMemoryBarrier(barriers)
    this.check()
  }
  def glMemoryBarrierByRegion(barriers: scala.Int): scala.Unit = {
    calls = calls + 1
    this.gl31.glMemoryBarrierByRegion(barriers)
    this.check()
  }
  def glTexStorage2DMultisample(target: scala.Int, samples: scala.Int, internalformat: scala.Int, width: scala.Int, height: scala.Int, fixedsamplelocations: scala.Boolean): scala.Unit = {
    calls = calls + 1
    this.gl31.glTexStorage2DMultisample(target, samples, internalformat, width, height, fixedsamplelocations)
    this.check()
  }
  def glGetMultisamplefv(pname: scala.Int, index: scala.Int, `val`: java.nio.FloatBuffer): scala.Unit = {
    calls = calls + 1
    this.gl31.glGetMultisamplefv(pname, index, `val`)
    this.check()
  }
  def glSampleMaski(maskNumber: scala.Int, mask: scala.Int): scala.Unit = {
    calls = calls + 1
    this.gl31.glSampleMaski(maskNumber, mask)
    this.check()
  }
  def glGetTexLevelParameteriv(target: scala.Int, level: scala.Int, pname: scala.Int, params: java.nio.IntBuffer): scala.Unit = {
    calls = calls + 1
    this.gl31.glGetTexLevelParameteriv(target, level, pname, params)
    this.check()
  }
  def glGetTexLevelParameterfv(target: scala.Int, level: scala.Int, pname: scala.Int, params: java.nio.FloatBuffer): scala.Unit = {
    calls = calls + 1
    this.gl31.glGetTexLevelParameterfv(target, level, pname, params)
    this.check()
  }
  def glBindVertexBuffer(bindingindex: scala.Int, buffer: scala.Int, offset: scala.Long, stride: scala.Int): scala.Unit = {
    calls = calls + 1
    this.gl31.glBindVertexBuffer(bindingindex, buffer, offset, stride)
    this.check()
  }
  def glVertexAttribFormat(attribindex: scala.Int, size: scala.Int, `type`: scala.Int, normalized: scala.Boolean, relativeoffset: scala.Int): scala.Unit = {
    calls = calls + 1
    this.gl31.glVertexAttribFormat(attribindex, size, `type`, normalized, relativeoffset)
    this.check()
  }
  def glVertexAttribIFormat(attribindex: scala.Int, size: scala.Int, `type`: scala.Int, relativeoffset: scala.Int): scala.Unit = {
    calls = calls + 1
    this.gl31.glVertexAttribIFormat(attribindex, size, `type`, relativeoffset)
    this.check()
  }
  def glVertexAttribBinding(attribindex: scala.Int, bindingindex: scala.Int): scala.Unit = {
    calls = calls + 1
    this.gl31.glVertexAttribBinding(attribindex, bindingindex)
    this.check()
  }
  def glVertexBindingDivisor(bindingindex: scala.Int, divisor: scala.Int): scala.Unit = {
    calls = calls + 1
    this.gl31.glVertexBindingDivisor(bindingindex, divisor)
    this.check()
  }
}
object GL31Interceptor {
  export com.badlogic.gdx.graphics.GL31.*
}