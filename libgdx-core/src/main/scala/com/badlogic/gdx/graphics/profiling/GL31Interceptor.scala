package com.badlogic.gdx.graphics.profiling

class GL31Interceptor(glProfiler$p: com.badlogic.gdx.graphics.profiling.GLProfiler, gl31$p: com.badlogic.gdx.graphics.GL31) extends com.badlogic.gdx.graphics.profiling.GL30Interceptor(glProfiler$p, gl31$p) with com.badlogic.gdx.graphics.GL31 {
  var gl31: com.badlogic.gdx.graphics.GL31 = null.asInstanceOf[com.badlogic.gdx.graphics.GL31]
  this.gl31 = gl31$p
  override def check(): scala.Unit = {
    var error: scala.Int = gl30.glGetError()
    while (error != com.badlogic.gdx.graphics.GL20.GL_NO_ERROR) {
      glProfiler.getListener().onError(error)
      error = gl30.glGetError()
    }
  }
  override def glDispatchCompute(num_groups_x: scala.Int, num_groups_y: scala.Int, num_groups_z: scala.Int): scala.Unit = {
    calls = calls + 1
    this.gl31.glDispatchCompute(num_groups_x, num_groups_y, num_groups_z)
    this.check()
  }
  override def glDispatchComputeIndirect(indirect: scala.Long): scala.Unit = {
    calls = calls + 1
    this.gl31.glDispatchComputeIndirect(indirect)
    this.check()
  }
  override def glDrawArraysIndirect(mode: scala.Int, indirect: scala.Long): scala.Unit = {
    drawCalls = drawCalls + 1
    calls = calls + 1
    this.gl31.glDrawArraysIndirect(mode, indirect)
    this.check()
  }
  override def glDrawElementsIndirect(mode: scala.Int, `type`: scala.Int, indirect: scala.Long): scala.Unit = {
    drawCalls = drawCalls + 1
    calls = calls + 1
    this.gl31.glDrawElementsIndirect(mode, `type`, indirect)
    this.check()
  }
  override def glFramebufferParameteri(target: scala.Int, pname: scala.Int, param: scala.Int): scala.Unit = {
    calls = calls + 1
    this.gl31.glFramebufferParameteri(target, pname, param)
    this.check()
  }
  override def glGetFramebufferParameteriv(target: scala.Int, pname: scala.Int, params: java.nio.IntBuffer): scala.Unit = {
    calls = calls + 1
    this.gl31.glGetFramebufferParameteriv(target, pname, params)
    this.check()
  }
  override def glGetProgramInterfaceiv(program: scala.Int, programInterface: scala.Int, pname: scala.Int, params: java.nio.IntBuffer): scala.Unit = {
    calls = calls + 1
    this.gl31.glGetProgramInterfaceiv(program, programInterface, pname, params)
    this.check()
  }
  override def glGetProgramResourceIndex(program: scala.Int, programInterface: scala.Int, name: java.lang.String): scala.Int = {
    calls = calls + 1
    val v: scala.Int = this.gl31.glGetProgramResourceIndex(program, programInterface, name)
    this.check()
    return v
  }
  override def glGetProgramResourceName(program: scala.Int, programInterface: scala.Int, index: scala.Int): java.lang.String = {
    calls = calls + 1
    val s: java.lang.String = this.gl31.glGetProgramResourceName(program, programInterface, index)
    this.check()
    return s
  }
  override def glGetProgramResourceiv(program: scala.Int, programInterface: scala.Int, index: scala.Int, props: java.nio.IntBuffer, length: java.nio.IntBuffer, params: java.nio.IntBuffer): scala.Unit = {
    calls = calls + 1
    this.gl31.glGetProgramResourceiv(program, programInterface, index, props, length, params)
    this.check()
  }
  override def glGetProgramResourceLocation(program: scala.Int, programInterface: scala.Int, name: java.lang.String): scala.Int = {
    calls = calls + 1
    val v: scala.Int = this.gl31.glGetProgramResourceLocation(program, programInterface, name)
    this.check()
    return v
  }
  override def glUseProgramStages(pipeline: scala.Int, stages: scala.Int, program: scala.Int): scala.Unit = {
    calls = calls + 1
    this.gl31.glUseProgramStages(pipeline, stages, program)
    this.check()
  }
  override def glActiveShaderProgram(pipeline: scala.Int, program: scala.Int): scala.Unit = {
    calls = calls + 1
    this.gl31.glActiveShaderProgram(pipeline, program)
    this.check()
  }
  override def glCreateShaderProgramv(`type`: scala.Int, strings: scala.Array[java.lang.String]): scala.Int = {
    calls = calls + 1
    val v: scala.Int = this.gl31.glCreateShaderProgramv(`type`, strings)
    this.check()
    return v
  }
  override def glBindProgramPipeline(pipeline: scala.Int): scala.Unit = {
    calls = calls + 1
    this.gl31.glBindProgramPipeline(pipeline)
    this.check()
  }
  override def glDeleteProgramPipelines(count: scala.Int, pipelines: java.nio.IntBuffer): scala.Unit = {
    calls = calls + 1
    this.gl31.glDeleteProgramPipelines(count, pipelines)
    this.check()
  }
  override def glGenProgramPipelines(count: scala.Int, pipelines: java.nio.IntBuffer): scala.Unit = {
    calls = calls + 1
    this.gl31.glGenProgramPipelines(count, pipelines)
    this.check()
  }
  override def glIsProgramPipeline(pipeline: scala.Int): scala.Boolean = {
    calls = calls + 1
    val v: scala.Boolean = this.gl31.glIsProgramPipeline(pipeline)
    this.check()
    return v
  }
  override def glGetProgramPipelineiv(pipeline: scala.Int, pname: scala.Int, params: java.nio.IntBuffer): scala.Unit = {
    calls = calls + 1
    this.gl31.glGetProgramPipelineiv(pipeline, pname, params)
    this.check()
  }
  override def glProgramUniform1i(program: scala.Int, location: scala.Int, v0: scala.Int): scala.Unit = {
    calls = calls + 1
    this.gl31.glProgramUniform1i(program, location, v0)
    this.check()
  }
  override def glProgramUniform2i(program: scala.Int, location: scala.Int, v0: scala.Int, v1: scala.Int): scala.Unit = {
    calls = calls + 1
    this.gl31.glProgramUniform2i(program, location, v0, v1)
    this.check()
  }
  override def glProgramUniform3i(program: scala.Int, location: scala.Int, v0: scala.Int, v1: scala.Int, v2: scala.Int): scala.Unit = {
    calls = calls + 1
    this.gl31.glProgramUniform3i(program, location, v0, v1, v2)
    this.check()
  }
  override def glProgramUniform4i(program: scala.Int, location: scala.Int, v0: scala.Int, v1: scala.Int, v2: scala.Int, v3: scala.Int): scala.Unit = {
    calls = calls + 1
    this.gl31.glProgramUniform4i(program, location, v0, v1, v2, v3)
    this.check()
  }
  override def glProgramUniform1ui(program: scala.Int, location: scala.Int, v0: scala.Int): scala.Unit = {
    calls = calls + 1
    this.gl31.glProgramUniform1ui(program, location, v0)
    this.check()
  }
  override def glProgramUniform2ui(program: scala.Int, location: scala.Int, v0: scala.Int, v1: scala.Int): scala.Unit = {
    calls = calls + 1
    this.gl31.glProgramUniform2ui(program, location, v0, v1)
    this.check()
  }
  override def glProgramUniform3ui(program: scala.Int, location: scala.Int, v0: scala.Int, v1: scala.Int, v2: scala.Int): scala.Unit = {
    calls = calls + 1
    this.gl31.glProgramUniform3ui(program, location, v0, v1, v2)
    this.check()
  }
  override def glProgramUniform4ui(program: scala.Int, location: scala.Int, v0: scala.Int, v1: scala.Int, v2: scala.Int, v3: scala.Int): scala.Unit = {
    calls = calls + 1
    this.gl31.glProgramUniform4ui(program, location, v0, v1, v2, v3)
    this.check()
  }
  override def glProgramUniform1f(program: scala.Int, location: scala.Int, v0: scala.Float): scala.Unit = {
    calls = calls + 1
    this.gl31.glProgramUniform1f(program, location, v0)
    this.check()
  }
  override def glProgramUniform2f(program: scala.Int, location: scala.Int, v0: scala.Float, v1: scala.Float): scala.Unit = {
    calls = calls + 1
    this.gl31.glProgramUniform2f(program, location, v0, v1)
    this.check()
  }
  override def glProgramUniform3f(program: scala.Int, location: scala.Int, v0: scala.Float, v1: scala.Float, v2: scala.Float): scala.Unit = {
    calls = calls + 1
    this.gl31.glProgramUniform3f(program, location, v0, v1, v2)
    this.check()
  }
  override def glProgramUniform4f(program: scala.Int, location: scala.Int, v0: scala.Float, v1: scala.Float, v2: scala.Float, v3: scala.Float): scala.Unit = {
    calls = calls + 1
    this.gl31.glProgramUniform4f(program, location, v0, v1, v2, v3)
    this.check()
  }
  override def glProgramUniform1iv(program: scala.Int, location: scala.Int, value: java.nio.IntBuffer): scala.Unit = {
    calls = calls + 1
    this.gl31.glProgramUniform1iv(program, location, value)
    this.check()
  }
  override def glProgramUniform2iv(program: scala.Int, location: scala.Int, value: java.nio.IntBuffer): scala.Unit = {
    calls = calls + 1
    this.gl31.glProgramUniform2iv(program, location, value)
    this.check()
  }
  override def glProgramUniform3iv(program: scala.Int, location: scala.Int, value: java.nio.IntBuffer): scala.Unit = {
    calls = calls + 1
    this.gl31.glProgramUniform3iv(program, location, value)
    this.check()
  }
  override def glProgramUniform4iv(program: scala.Int, location: scala.Int, value: java.nio.IntBuffer): scala.Unit = {
    calls = calls + 1
    this.gl31.glProgramUniform4iv(program, location, value)
    this.check()
  }
  override def glProgramUniform1uiv(program: scala.Int, location: scala.Int, value: java.nio.IntBuffer): scala.Unit = {
    calls = calls + 1
    this.gl31.glProgramUniform1uiv(program, location, value)
    this.check()
  }
  override def glProgramUniform2uiv(program: scala.Int, location: scala.Int, value: java.nio.IntBuffer): scala.Unit = {
    calls = calls + 1
    this.gl31.glProgramUniform2uiv(program, location, value)
    this.check()
  }
  override def glProgramUniform3uiv(program: scala.Int, location: scala.Int, value: java.nio.IntBuffer): scala.Unit = {
    calls = calls + 1
    this.gl31.glProgramUniform3uiv(program, location, value)
    this.check()
  }
  override def glProgramUniform4uiv(program: scala.Int, location: scala.Int, value: java.nio.IntBuffer): scala.Unit = {
    calls = calls + 1
    this.gl31.glProgramUniform4uiv(program, location, value)
    this.check()
  }
  override def glProgramUniform1fv(program: scala.Int, location: scala.Int, value: java.nio.FloatBuffer): scala.Unit = {
    calls = calls + 1
    this.gl31.glProgramUniform1fv(program, location, value)
    this.check()
  }
  override def glProgramUniform2fv(program: scala.Int, location: scala.Int, value: java.nio.FloatBuffer): scala.Unit = {
    calls = calls + 1
    this.gl31.glProgramUniform2fv(program, location, value)
    this.check()
  }
  override def glProgramUniform3fv(program: scala.Int, location: scala.Int, value: java.nio.FloatBuffer): scala.Unit = {
    calls = calls + 1
    this.gl31.glProgramUniform3fv(program, location, value)
    this.check()
  }
  override def glProgramUniform4fv(program: scala.Int, location: scala.Int, value: java.nio.FloatBuffer): scala.Unit = {
    calls = calls + 1
    this.gl31.glProgramUniform4fv(program, location, value)
    this.check()
  }
  override def glProgramUniformMatrix2fv(program: scala.Int, location: scala.Int, transpose: scala.Boolean, value: java.nio.FloatBuffer): scala.Unit = {
    calls = calls + 1
    this.gl31.glProgramUniformMatrix2fv(program, location, transpose, value)
    this.check()
  }
  override def glProgramUniformMatrix3fv(program: scala.Int, location: scala.Int, transpose: scala.Boolean, value: java.nio.FloatBuffer): scala.Unit = {
    calls = calls + 1
    this.gl31.glProgramUniformMatrix3fv(program, location, transpose, value)
    this.check()
  }
  override def glProgramUniformMatrix4fv(program: scala.Int, location: scala.Int, transpose: scala.Boolean, value: java.nio.FloatBuffer): scala.Unit = {
    calls = calls + 1
    this.gl31.glProgramUniformMatrix4fv(program, location, transpose, value)
    this.check()
  }
  override def glProgramUniformMatrix2x3fv(program: scala.Int, location: scala.Int, transpose: scala.Boolean, value: java.nio.FloatBuffer): scala.Unit = {
    calls = calls + 1
    this.gl31.glProgramUniformMatrix2x3fv(program, location, transpose, value)
    this.check()
  }
  override def glProgramUniformMatrix3x2fv(program: scala.Int, location: scala.Int, transpose: scala.Boolean, value: java.nio.FloatBuffer): scala.Unit = {
    calls = calls + 1
    this.gl31.glProgramUniformMatrix3x2fv(program, location, transpose, value)
    this.check()
  }
  override def glProgramUniformMatrix2x4fv(program: scala.Int, location: scala.Int, transpose: scala.Boolean, value: java.nio.FloatBuffer): scala.Unit = {
    calls = calls + 1
    this.gl31.glProgramUniformMatrix2x4fv(program, location, transpose, value)
    this.check()
  }
  override def glProgramUniformMatrix4x2fv(program: scala.Int, location: scala.Int, transpose: scala.Boolean, value: java.nio.FloatBuffer): scala.Unit = {
    calls = calls + 1
    this.gl31.glProgramUniformMatrix4x2fv(program, location, transpose, value)
    this.check()
  }
  override def glProgramUniformMatrix3x4fv(program: scala.Int, location: scala.Int, transpose: scala.Boolean, value: java.nio.FloatBuffer): scala.Unit = {
    calls = calls + 1
    this.gl31.glProgramUniformMatrix3x4fv(program, location, transpose, value)
    this.check()
  }
  override def glProgramUniformMatrix4x3fv(program: scala.Int, location: scala.Int, transpose: scala.Boolean, value: java.nio.FloatBuffer): scala.Unit = {
    calls = calls + 1
    this.gl31.glProgramUniformMatrix4x3fv(program, location, transpose, value)
    this.check()
  }
  override def glValidateProgramPipeline(pipeline: scala.Int): scala.Unit = {
    calls = calls + 1
    this.gl31.glValidateProgramPipeline(pipeline)
    this.check()
  }
  override def glGetProgramPipelineInfoLog(program: scala.Int): java.lang.String = {
    calls = calls + 1
    val s: java.lang.String = this.gl31.glGetProgramPipelineInfoLog(program)
    this.check()
    return s
  }
  override def glBindImageTexture(unit: scala.Int, texture: scala.Int, level: scala.Int, layered: scala.Boolean, layer: scala.Int, access: scala.Int, format: scala.Int): scala.Unit = {
    calls = calls + 1
    this.gl31.glBindImageTexture(unit, texture, level, layered, layer, access, format)
    this.check()
  }
  override def glGetBooleani_v(target: scala.Int, index: scala.Int, data: java.nio.IntBuffer): scala.Unit = {
    calls = calls + 1
    this.gl31.glGetBooleani_v(target, index, data)
    this.check()
  }
  override def glMemoryBarrier(barriers: scala.Int): scala.Unit = {
    calls = calls + 1
    this.gl31.glMemoryBarrier(barriers)
    this.check()
  }
  override def glMemoryBarrierByRegion(barriers: scala.Int): scala.Unit = {
    calls = calls + 1
    this.gl31.glMemoryBarrierByRegion(barriers)
    this.check()
  }
  override def glTexStorage2DMultisample(target: scala.Int, samples: scala.Int, internalformat: scala.Int, width: scala.Int, height: scala.Int, fixedsamplelocations: scala.Boolean): scala.Unit = {
    calls = calls + 1
    this.gl31.glTexStorage2DMultisample(target, samples, internalformat, width, height, fixedsamplelocations)
    this.check()
  }
  override def glGetMultisamplefv(pname: scala.Int, index: scala.Int, `val`: java.nio.FloatBuffer): scala.Unit = {
    calls = calls + 1
    this.gl31.glGetMultisamplefv(pname, index, `val`)
    this.check()
  }
  override def glSampleMaski(maskNumber: scala.Int, mask: scala.Int): scala.Unit = {
    calls = calls + 1
    this.gl31.glSampleMaski(maskNumber, mask)
    this.check()
  }
  override def glGetTexLevelParameteriv(target: scala.Int, level: scala.Int, pname: scala.Int, params: java.nio.IntBuffer): scala.Unit = {
    calls = calls + 1
    this.gl31.glGetTexLevelParameteriv(target, level, pname, params)
    this.check()
  }
  override def glGetTexLevelParameterfv(target: scala.Int, level: scala.Int, pname: scala.Int, params: java.nio.FloatBuffer): scala.Unit = {
    calls = calls + 1
    this.gl31.glGetTexLevelParameterfv(target, level, pname, params)
    this.check()
  }
  override def glBindVertexBuffer(bindingindex: scala.Int, buffer: scala.Int, offset: scala.Long, stride: scala.Int): scala.Unit = {
    calls = calls + 1
    this.gl31.glBindVertexBuffer(bindingindex, buffer, offset, stride)
    this.check()
  }
  override def glVertexAttribFormat(attribindex: scala.Int, size: scala.Int, `type`: scala.Int, normalized: scala.Boolean, relativeoffset: scala.Int): scala.Unit = {
    calls = calls + 1
    this.gl31.glVertexAttribFormat(attribindex, size, `type`, normalized, relativeoffset)
    this.check()
  }
  override def glVertexAttribIFormat(attribindex: scala.Int, size: scala.Int, `type`: scala.Int, relativeoffset: scala.Int): scala.Unit = {
    calls = calls + 1
    this.gl31.glVertexAttribIFormat(attribindex, size, `type`, relativeoffset)
    this.check()
  }
  override def glVertexAttribBinding(attribindex: scala.Int, bindingindex: scala.Int): scala.Unit = {
    calls = calls + 1
    this.gl31.glVertexAttribBinding(attribindex, bindingindex)
    this.check()
  }
  override def glVertexBindingDivisor(bindingindex: scala.Int, divisor: scala.Int): scala.Unit = {
    calls = calls + 1
    this.gl31.glVertexBindingDivisor(bindingindex, divisor)
    this.check()
  }
}
object GL31Interceptor {
  export com.badlogic.gdx.graphics.profiling.GL30Interceptor.{GL_STENCIL_INDEX => _, *}
  export com.badlogic.gdx.graphics.GL31.{GL_ACTIVE_ATTRIBUTES => _, GL_ACTIVE_ATTRIBUTE_MAX_LENGTH => _, GL_ACTIVE_TEXTURE => _, GL_ACTIVE_UNIFORMS => _, GL_ACTIVE_UNIFORM_BLOCKS => _, GL_ACTIVE_UNIFORM_BLOCK_MAX_NAME_LENGTH => _, GL_ACTIVE_UNIFORM_MAX_LENGTH => _, GL_ALIASED_LINE_WIDTH_RANGE => _, GL_ALIASED_POINT_SIZE_RANGE => _, GL_ALPHA => _, GL_ALPHA_BITS => _, GL_ALREADY_SIGNALED => _, GL_ALWAYS => _, GL_ANY_SAMPLES_PASSED => _, GL_ANY_SAMPLES_PASSED_CONSERVATIVE => _, GL_ARRAY_BUFFER => _, GL_ARRAY_BUFFER_BINDING => _, GL_ATTACHED_SHADERS => _, GL_BACK => _, GL_BLEND => _, GL_BLEND_COLOR => _, GL_BLEND_DST_ALPHA => _, GL_BLEND_DST_RGB => _, GL_BLEND_EQUATION => _, GL_BLEND_EQUATION_ALPHA => _, GL_BLEND_EQUATION_RGB => _, GL_BLEND_SRC_ALPHA => _, GL_BLEND_SRC_RGB => _, GL_BLUE => _, GL_BLUE_BITS => _, GL_BOOL => _, GL_BOOL_VEC2 => _, GL_BOOL_VEC3 => _, GL_BOOL_VEC4 => _, GL_BUFFER_ACCESS_FLAGS => _, GL_BUFFER_MAPPED => _, GL_BUFFER_MAP_LENGTH => _, GL_BUFFER_MAP_OFFSET => _, GL_BUFFER_MAP_POINTER => _, GL_BUFFER_SIZE => _, GL_BUFFER_USAGE => _, GL_BYTE => _, GL_CCW => _, GL_CLAMP_TO_EDGE => _, GL_COLOR => _, GL_COLOR_ATTACHMENT0 => _, GL_COLOR_ATTACHMENT1 => _, GL_COLOR_ATTACHMENT10 => _, GL_COLOR_ATTACHMENT11 => _, GL_COLOR_ATTACHMENT12 => _, GL_COLOR_ATTACHMENT13 => _, GL_COLOR_ATTACHMENT14 => _, GL_COLOR_ATTACHMENT15 => _, GL_COLOR_ATTACHMENT2 => _, GL_COLOR_ATTACHMENT3 => _, GL_COLOR_ATTACHMENT4 => _, GL_COLOR_ATTACHMENT5 => _, GL_COLOR_ATTACHMENT6 => _, GL_COLOR_ATTACHMENT7 => _, GL_COLOR_ATTACHMENT8 => _, GL_COLOR_ATTACHMENT9 => _, GL_COLOR_BUFFER_BIT => _, GL_COLOR_CLEAR_VALUE => _, GL_COLOR_WRITEMASK => _, GL_COMPARE_REF_TO_TEXTURE => _, GL_COMPILE_STATUS => _, GL_COMPRESSED_R11_EAC => _, GL_COMPRESSED_RG11_EAC => _, GL_COMPRESSED_RGB8_ETC2 => _, GL_COMPRESSED_RGB8_PUNCHTHROUGH_ALPHA1_ETC2 => _, GL_COMPRESSED_RGBA8_ETC2_EAC => _, GL_COMPRESSED_SIGNED_R11_EAC => _, GL_COMPRESSED_SIGNED_RG11_EAC => _, GL_COMPRESSED_SRGB8_ALPHA8_ETC2_EAC => _, GL_COMPRESSED_SRGB8_ETC2 => _, GL_COMPRESSED_SRGB8_PUNCHTHROUGH_ALPHA1_ETC2 => _, GL_COMPRESSED_TEXTURE_FORMATS => _, GL_CONDITION_SATISFIED => _, GL_CONSTANT_ALPHA => _, GL_CONSTANT_COLOR => _, GL_COPY_READ_BUFFER => _, GL_COPY_READ_BUFFER_BINDING => _, GL_COPY_WRITE_BUFFER => _, GL_COPY_WRITE_BUFFER_BINDING => _, GL_COVERAGE_BUFFER_BIT_NV => _, GL_CULL_FACE => _, GL_CULL_FACE_MODE => _, GL_CURRENT_PROGRAM => _, GL_CURRENT_QUERY => _, GL_CURRENT_VERTEX_ATTRIB => _, GL_CW => _, GL_DECR => _, GL_DECR_WRAP => _, GL_DELETE_STATUS => _, GL_DEPTH => _, GL_DEPTH24_STENCIL8 => _, GL_DEPTH32F_STENCIL8 => _, GL_DEPTH_ATTACHMENT => _, GL_DEPTH_BITS => _, GL_DEPTH_BUFFER_BIT => _, GL_DEPTH_CLEAR_VALUE => _, GL_DEPTH_COMPONENT => _, GL_DEPTH_COMPONENT16 => _, GL_DEPTH_COMPONENT24 => _, GL_DEPTH_COMPONENT32F => _, GL_DEPTH_FUNC => _, GL_DEPTH_RANGE => _, GL_DEPTH_STENCIL => _, GL_DEPTH_STENCIL_ATTACHMENT => _, GL_DEPTH_TEST => _, GL_DEPTH_WRITEMASK => _, GL_DITHER => _, GL_DONT_CARE => _, GL_DRAW_BUFFER0 => _, GL_DRAW_BUFFER1 => _, GL_DRAW_BUFFER10 => _, GL_DRAW_BUFFER11 => _, GL_DRAW_BUFFER12 => _, GL_DRAW_BUFFER13 => _, GL_DRAW_BUFFER14 => _, GL_DRAW_BUFFER15 => _, GL_DRAW_BUFFER2 => _, GL_DRAW_BUFFER3 => _, GL_DRAW_BUFFER4 => _, GL_DRAW_BUFFER5 => _, GL_DRAW_BUFFER6 => _, GL_DRAW_BUFFER7 => _, GL_DRAW_BUFFER8 => _, GL_DRAW_BUFFER9 => _, GL_DRAW_FRAMEBUFFER => _, GL_DRAW_FRAMEBUFFER_BINDING => _, GL_DST_ALPHA => _, GL_DST_COLOR => _, GL_DYNAMIC_COPY => _, GL_DYNAMIC_DRAW => _, GL_DYNAMIC_READ => _, GL_ELEMENT_ARRAY_BUFFER => _, GL_ELEMENT_ARRAY_BUFFER_BINDING => _, GL_EQUAL => _, GL_ES_VERSION_2_0 => _, GL_EXTENSIONS => _, GL_FALSE => _, GL_FASTEST => _, GL_FIXED => _, GL_FLOAT => _, GL_FLOAT_32_UNSIGNED_INT_24_8_REV => _, GL_FLOAT_MAT2 => _, GL_FLOAT_MAT2x3 => _, GL_FLOAT_MAT2x4 => _, GL_FLOAT_MAT3 => _, GL_FLOAT_MAT3x2 => _, GL_FLOAT_MAT3x4 => _, GL_FLOAT_MAT4 => _, GL_FLOAT_MAT4x2 => _, GL_FLOAT_MAT4x3 => _, GL_FLOAT_VEC2 => _, GL_FLOAT_VEC3 => _, GL_FLOAT_VEC4 => _, GL_FRAGMENT_SHADER => _, GL_FRAGMENT_SHADER_DERIVATIVE_HINT => _, GL_FRAMEBUFFER => _, GL_FRAMEBUFFER_ATTACHMENT_ALPHA_SIZE => _, GL_FRAMEBUFFER_ATTACHMENT_BLUE_SIZE => _, GL_FRAMEBUFFER_ATTACHMENT_COLOR_ENCODING => _, GL_FRAMEBUFFER_ATTACHMENT_COMPONENT_TYPE => _, GL_FRAMEBUFFER_ATTACHMENT_DEPTH_SIZE => _, GL_FRAMEBUFFER_ATTACHMENT_GREEN_SIZE => _, GL_FRAMEBUFFER_ATTACHMENT_OBJECT_NAME => _, GL_FRAMEBUFFER_ATTACHMENT_OBJECT_TYPE => _, GL_FRAMEBUFFER_ATTACHMENT_RED_SIZE => _, GL_FRAMEBUFFER_ATTACHMENT_STENCIL_SIZE => _, GL_FRAMEBUFFER_ATTACHMENT_TEXTURE_CUBE_MAP_FACE => _, GL_FRAMEBUFFER_ATTACHMENT_TEXTURE_LAYER => _, GL_FRAMEBUFFER_ATTACHMENT_TEXTURE_LEVEL => _, GL_FRAMEBUFFER_BINDING => _, GL_FRAMEBUFFER_COMPLETE => _, GL_FRAMEBUFFER_DEFAULT => _, GL_FRAMEBUFFER_INCOMPLETE_ATTACHMENT => _, GL_FRAMEBUFFER_INCOMPLETE_DIMENSIONS => _, GL_FRAMEBUFFER_INCOMPLETE_MISSING_ATTACHMENT => _, GL_FRAMEBUFFER_INCOMPLETE_MULTISAMPLE => _, GL_FRAMEBUFFER_UNDEFINED => _, GL_FRAMEBUFFER_UNSUPPORTED => _, GL_FRONT => _, GL_FRONT_AND_BACK => _, GL_FRONT_FACE => _, GL_FUNC_ADD => _, GL_FUNC_REVERSE_SUBTRACT => _, GL_FUNC_SUBTRACT => _, GL_GENERATE_MIPMAP => _, GL_GENERATE_MIPMAP_HINT => _, GL_GEQUAL => _, GL_GREATER => _, GL_GREEN => _, GL_GREEN_BITS => _, GL_HALF_FLOAT => _, GL_HIGH_FLOAT => _, GL_HIGH_INT => _, GL_IMPLEMENTATION_COLOR_READ_FORMAT => _, GL_IMPLEMENTATION_COLOR_READ_TYPE => _, GL_INCR => _, GL_INCR_WRAP => _, GL_INFO_LOG_LENGTH => _, GL_INT => _, GL_INTERLEAVED_ATTRIBS => _, GL_INT_2_10_10_10_REV => _, GL_INT_SAMPLER_2D => _, GL_INT_SAMPLER_2D_ARRAY => _, GL_INT_SAMPLER_3D => _, GL_INT_SAMPLER_CUBE => _, GL_INT_VEC2 => _, GL_INT_VEC3 => _, GL_INT_VEC4 => _, GL_INVALID_ENUM => _, GL_INVALID_FRAMEBUFFER_OPERATION => _, GL_INVALID_INDEX => _, GL_INVALID_OPERATION => _, GL_INVALID_VALUE => _, GL_INVERT => _, GL_KEEP => _, GL_LEQUAL => _, GL_LESS => _, GL_LINEAR => _, GL_LINEAR_MIPMAP_LINEAR => _, GL_LINEAR_MIPMAP_NEAREST => _, GL_LINES => _, GL_LINE_LOOP => _, GL_LINE_STRIP => _, GL_LINE_WIDTH => _, GL_LINK_STATUS => _, GL_LOW_FLOAT => _, GL_LOW_INT => _, GL_LUMINANCE => _, GL_LUMINANCE_ALPHA => _, GL_MAJOR_VERSION => _, GL_MAP_FLUSH_EXPLICIT_BIT => _, GL_MAP_INVALIDATE_BUFFER_BIT => _, GL_MAP_INVALIDATE_RANGE_BIT => _, GL_MAP_READ_BIT => _, GL_MAP_UNSYNCHRONIZED_BIT => _, GL_MAP_WRITE_BIT => _, GL_MAX => _, GL_MAX_3D_TEXTURE_SIZE => _, GL_MAX_ARRAY_TEXTURE_LAYERS => _, GL_MAX_COLOR_ATTACHMENTS => _, GL_MAX_COMBINED_FRAGMENT_UNIFORM_COMPONENTS => _, GL_MAX_COMBINED_TEXTURE_IMAGE_UNITS => _, GL_MAX_COMBINED_UNIFORM_BLOCKS => _, GL_MAX_COMBINED_VERTEX_UNIFORM_COMPONENTS => _, GL_MAX_CUBE_MAP_TEXTURE_SIZE => _, GL_MAX_DRAW_BUFFERS => _, GL_MAX_ELEMENTS_INDICES => _, GL_MAX_ELEMENTS_VERTICES => _, GL_MAX_ELEMENT_INDEX => _, GL_MAX_FRAGMENT_INPUT_COMPONENTS => _, GL_MAX_FRAGMENT_UNIFORM_BLOCKS => _, GL_MAX_FRAGMENT_UNIFORM_COMPONENTS => _, GL_MAX_FRAGMENT_UNIFORM_VECTORS => _, GL_MAX_PROGRAM_TEXEL_OFFSET => _, GL_MAX_RENDERBUFFER_SIZE => _, GL_MAX_SAMPLES => _, GL_MAX_SERVER_WAIT_TIMEOUT => _, GL_MAX_TEXTURE_IMAGE_UNITS => _, GL_MAX_TEXTURE_LOD_BIAS => _, GL_MAX_TEXTURE_MAX_ANISOTROPY_EXT => _, GL_MAX_TEXTURE_SIZE => _, GL_MAX_TEXTURE_UNITS => _, GL_MAX_TRANSFORM_FEEDBACK_INTERLEAVED_COMPONENTS => _, GL_MAX_TRANSFORM_FEEDBACK_SEPARATE_ATTRIBS => _, GL_MAX_TRANSFORM_FEEDBACK_SEPARATE_COMPONENTS => _, GL_MAX_UNIFORM_BLOCK_SIZE => _, GL_MAX_UNIFORM_BUFFER_BINDINGS => _, GL_MAX_VARYING_COMPONENTS => _, GL_MAX_VARYING_VECTORS => _, GL_MAX_VERTEX_ATTRIBS => _, GL_MAX_VERTEX_OUTPUT_COMPONENTS => _, GL_MAX_VERTEX_TEXTURE_IMAGE_UNITS => _, GL_MAX_VERTEX_UNIFORM_BLOCKS => _, GL_MAX_VERTEX_UNIFORM_COMPONENTS => _, GL_MAX_VERTEX_UNIFORM_VECTORS => _, GL_MAX_VIEWPORT_DIMS => _, GL_MEDIUM_FLOAT => _, GL_MEDIUM_INT => _, GL_MIN => _, GL_MINOR_VERSION => _, GL_MIN_PROGRAM_TEXEL_OFFSET => _, GL_MIRRORED_REPEAT => _, GL_NEAREST => _, GL_NEAREST_MIPMAP_LINEAR => _, GL_NEAREST_MIPMAP_NEAREST => _, GL_NEVER => _, GL_NICEST => _, GL_NONE => _, GL_NOTEQUAL => _, GL_NO_ERROR => _, GL_NUM_COMPRESSED_TEXTURE_FORMATS => _, GL_NUM_EXTENSIONS => _, GL_NUM_PROGRAM_BINARY_FORMATS => _, GL_NUM_SAMPLE_COUNTS => _, GL_NUM_SHADER_BINARY_FORMATS => _, GL_OBJECT_TYPE => _, GL_ONE => _, GL_ONE_MINUS_CONSTANT_ALPHA => _, GL_ONE_MINUS_CONSTANT_COLOR => _, GL_ONE_MINUS_DST_ALPHA => _, GL_ONE_MINUS_DST_COLOR => _, GL_ONE_MINUS_SRC_ALPHA => _, GL_ONE_MINUS_SRC_COLOR => _, GL_OUT_OF_MEMORY => _, GL_PACK_ALIGNMENT => _, GL_PACK_ROW_LENGTH => _, GL_PACK_SKIP_PIXELS => _, GL_PACK_SKIP_ROWS => _, GL_PIXEL_PACK_BUFFER => _, GL_PIXEL_PACK_BUFFER_BINDING => _, GL_PIXEL_UNPACK_BUFFER => _, GL_PIXEL_UNPACK_BUFFER_BINDING => _, GL_POINTS => _, GL_POLYGON_OFFSET_FACTOR => _, GL_POLYGON_OFFSET_FILL => _, GL_POLYGON_OFFSET_UNITS => _, GL_PRIMITIVE_RESTART_FIXED_INDEX => _, GL_PROGRAM_BINARY_FORMATS => _, GL_PROGRAM_BINARY_LENGTH => _, GL_PROGRAM_BINARY_RETRIEVABLE_HINT => _, GL_QUERY_RESULT => _, GL_QUERY_RESULT_AVAILABLE => _, GL_R11F_G11F_B10F => _, GL_R16F => _, GL_R16I => _, GL_R16UI => _, GL_R32F => _, GL_R32I => _, GL_R32UI => _, GL_R8 => _, GL_R8I => _, GL_R8UI => _, GL_R8_SNORM => _, GL_RASTERIZER_DISCARD => _, GL_READ_BUFFER => _, GL_READ_FRAMEBUFFER => _, GL_READ_FRAMEBUFFER_BINDING => _, GL_RED => _, GL_RED_BITS => _, GL_RED_INTEGER => _, GL_RENDERBUFFER => _, GL_RENDERBUFFER_ALPHA_SIZE => _, GL_RENDERBUFFER_BINDING => _, GL_RENDERBUFFER_BLUE_SIZE => _, GL_RENDERBUFFER_DEPTH_SIZE => _, GL_RENDERBUFFER_GREEN_SIZE => _, GL_RENDERBUFFER_HEIGHT => _, GL_RENDERBUFFER_INTERNAL_FORMAT => _, GL_RENDERBUFFER_RED_SIZE => _, GL_RENDERBUFFER_SAMPLES => _, GL_RENDERBUFFER_STENCIL_SIZE => _, GL_RENDERBUFFER_WIDTH => _, GL_RENDERER => _, GL_REPEAT => _, GL_REPLACE => _, GL_RG => _, GL_RG16F => _, GL_RG16I => _, GL_RG16UI => _, GL_RG32F => _, GL_RG32I => _, GL_RG32UI => _, GL_RG8 => _, GL_RG8I => _, GL_RG8UI => _, GL_RG8_SNORM => _, GL_RGB => _, GL_RGB10_A2 => _, GL_RGB10_A2UI => _, GL_RGB16F => _, GL_RGB16I => _, GL_RGB16UI => _, GL_RGB32F => _, GL_RGB32I => _, GL_RGB32UI => _, GL_RGB565 => _, GL_RGB5_A1 => _, GL_RGB8 => _, GL_RGB8I => _, GL_RGB8UI => _, GL_RGB8_SNORM => _, GL_RGB9_E5 => _, GL_RGBA => _, GL_RGBA16F => _, GL_RGBA16I => _, GL_RGBA16UI => _, GL_RGBA32F => _, GL_RGBA32I => _, GL_RGBA32UI => _, GL_RGBA4 => _, GL_RGBA8 => _, GL_RGBA8I => _, GL_RGBA8UI => _, GL_RGBA8_SNORM => _, GL_RGBA_INTEGER => _, GL_RGB_INTEGER => _, GL_RG_INTEGER => _, GL_SAMPLER_2D => _, GL_SAMPLER_2D_ARRAY => _, GL_SAMPLER_2D_ARRAY_SHADOW => _, GL_SAMPLER_2D_SHADOW => _, GL_SAMPLER_3D => _, GL_SAMPLER_BINDING => _, GL_SAMPLER_CUBE => _, GL_SAMPLER_CUBE_SHADOW => _, GL_SAMPLES => _, GL_SAMPLE_ALPHA_TO_COVERAGE => _, GL_SAMPLE_BUFFERS => _, GL_SAMPLE_COVERAGE => _, GL_SAMPLE_COVERAGE_INVERT => _, GL_SAMPLE_COVERAGE_VALUE => _, GL_SCISSOR_BOX => _, GL_SCISSOR_TEST => _, GL_SEPARATE_ATTRIBS => _, GL_SHADER_BINARY_FORMATS => _, GL_SHADER_COMPILER => _, GL_SHADER_SOURCE_LENGTH => _, GL_SHADER_TYPE => _, GL_SHADING_LANGUAGE_VERSION => _, GL_SHORT => _, GL_SIGNALED => _, GL_SIGNED_NORMALIZED => _, GL_SRC_ALPHA => _, GL_SRC_ALPHA_SATURATE => _, GL_SRC_COLOR => _, GL_SRGB => _, GL_SRGB8 => _, GL_SRGB8_ALPHA8 => _, GL_STATIC_COPY => _, GL_STATIC_DRAW => _, GL_STATIC_READ => _, GL_STENCIL => _, GL_STENCIL_ATTACHMENT => _, GL_STENCIL_BACK_FAIL => _, GL_STENCIL_BACK_FUNC => _, GL_STENCIL_BACK_PASS_DEPTH_FAIL => _, GL_STENCIL_BACK_PASS_DEPTH_PASS => _, GL_STENCIL_BACK_REF => _, GL_STENCIL_BACK_VALUE_MASK => _, GL_STENCIL_BACK_WRITEMASK => _, GL_STENCIL_BITS => _, GL_STENCIL_BUFFER_BIT => _, GL_STENCIL_CLEAR_VALUE => _, GL_STENCIL_FAIL => _, GL_STENCIL_FUNC => _, GL_STENCIL_INDEX8 => _, GL_STENCIL_PASS_DEPTH_FAIL => _, GL_STENCIL_PASS_DEPTH_PASS => _, GL_STENCIL_REF => _, GL_STENCIL_TEST => _, GL_STENCIL_VALUE_MASK => _, GL_STENCIL_WRITEMASK => _, GL_STREAM_COPY => _, GL_STREAM_DRAW => _, GL_STREAM_READ => _, GL_SUBPIXEL_BITS => _, GL_SYNC_CONDITION => _, GL_SYNC_FENCE => _, GL_SYNC_FLAGS => _, GL_SYNC_FLUSH_COMMANDS_BIT => _, GL_SYNC_GPU_COMMANDS_COMPLETE => _, GL_SYNC_STATUS => _, GL_TEXTURE => _, GL_TEXTURE0 => _, GL_TEXTURE1 => _, GL_TEXTURE10 => _, GL_TEXTURE11 => _, GL_TEXTURE12 => _, GL_TEXTURE13 => _, GL_TEXTURE14 => _, GL_TEXTURE15 => _, GL_TEXTURE16 => _, GL_TEXTURE17 => _, GL_TEXTURE18 => _, GL_TEXTURE19 => _, GL_TEXTURE2 => _, GL_TEXTURE20 => _, GL_TEXTURE21 => _, GL_TEXTURE22 => _, GL_TEXTURE23 => _, GL_TEXTURE24 => _, GL_TEXTURE25 => _, GL_TEXTURE26 => _, GL_TEXTURE27 => _, GL_TEXTURE28 => _, GL_TEXTURE29 => _, GL_TEXTURE3 => _, GL_TEXTURE30 => _, GL_TEXTURE31 => _, GL_TEXTURE4 => _, GL_TEXTURE5 => _, GL_TEXTURE6 => _, GL_TEXTURE7 => _, GL_TEXTURE8 => _, GL_TEXTURE9 => _, GL_TEXTURE_2D => _, GL_TEXTURE_2D_ARRAY => _, GL_TEXTURE_3D => _, GL_TEXTURE_BASE_LEVEL => _, GL_TEXTURE_BINDING_2D => _, GL_TEXTURE_BINDING_2D_ARRAY => _, GL_TEXTURE_BINDING_3D => _, GL_TEXTURE_BINDING_CUBE_MAP => _, GL_TEXTURE_COMPARE_FUNC => _, GL_TEXTURE_COMPARE_MODE => _, GL_TEXTURE_CUBE_MAP => _, GL_TEXTURE_CUBE_MAP_NEGATIVE_X => _, GL_TEXTURE_CUBE_MAP_NEGATIVE_Y => _, GL_TEXTURE_CUBE_MAP_NEGATIVE_Z => _, GL_TEXTURE_CUBE_MAP_POSITIVE_X => _, GL_TEXTURE_CUBE_MAP_POSITIVE_Y => _, GL_TEXTURE_CUBE_MAP_POSITIVE_Z => _, GL_TEXTURE_IMMUTABLE_FORMAT => _, GL_TEXTURE_IMMUTABLE_LEVELS => _, GL_TEXTURE_MAG_FILTER => _, GL_TEXTURE_MAX_ANISOTROPY_EXT => _, GL_TEXTURE_MAX_LEVEL => _, GL_TEXTURE_MAX_LOD => _, GL_TEXTURE_MIN_FILTER => _, GL_TEXTURE_MIN_LOD => _, GL_TEXTURE_SWIZZLE_A => _, GL_TEXTURE_SWIZZLE_B => _, GL_TEXTURE_SWIZZLE_G => _, GL_TEXTURE_SWIZZLE_R => _, GL_TEXTURE_WRAP_R => _, GL_TEXTURE_WRAP_S => _, GL_TEXTURE_WRAP_T => _, GL_TIMEOUT_EXPIRED => _, GL_TIMEOUT_IGNORED => _, GL_TRANSFORM_FEEDBACK => _, GL_TRANSFORM_FEEDBACK_ACTIVE => _, GL_TRANSFORM_FEEDBACK_BINDING => _, GL_TRANSFORM_FEEDBACK_BUFFER => _, GL_TRANSFORM_FEEDBACK_BUFFER_BINDING => _, GL_TRANSFORM_FEEDBACK_BUFFER_MODE => _, GL_TRANSFORM_FEEDBACK_BUFFER_SIZE => _, GL_TRANSFORM_FEEDBACK_BUFFER_START => _, GL_TRANSFORM_FEEDBACK_PAUSED => _, GL_TRANSFORM_FEEDBACK_PRIMITIVES_WRITTEN => _, GL_TRANSFORM_FEEDBACK_VARYINGS => _, GL_TRANSFORM_FEEDBACK_VARYING_MAX_LENGTH => _, GL_TRIANGLES => _, GL_TRIANGLE_FAN => _, GL_TRIANGLE_STRIP => _, GL_TRUE => _, GL_UNIFORM_ARRAY_STRIDE => _, GL_UNIFORM_BLOCK_ACTIVE_UNIFORMS => _, GL_UNIFORM_BLOCK_ACTIVE_UNIFORM_INDICES => _, GL_UNIFORM_BLOCK_BINDING => _, GL_UNIFORM_BLOCK_DATA_SIZE => _, GL_UNIFORM_BLOCK_INDEX => _, GL_UNIFORM_BLOCK_NAME_LENGTH => _, GL_UNIFORM_BLOCK_REFERENCED_BY_FRAGMENT_SHADER => _, GL_UNIFORM_BLOCK_REFERENCED_BY_VERTEX_SHADER => _, GL_UNIFORM_BUFFER => _, GL_UNIFORM_BUFFER_BINDING => _, GL_UNIFORM_BUFFER_OFFSET_ALIGNMENT => _, GL_UNIFORM_BUFFER_SIZE => _, GL_UNIFORM_BUFFER_START => _, GL_UNIFORM_IS_ROW_MAJOR => _, GL_UNIFORM_MATRIX_STRIDE => _, GL_UNIFORM_NAME_LENGTH => _, GL_UNIFORM_OFFSET => _, GL_UNIFORM_SIZE => _, GL_UNIFORM_TYPE => _, GL_UNPACK_ALIGNMENT => _, GL_UNPACK_IMAGE_HEIGHT => _, GL_UNPACK_ROW_LENGTH => _, GL_UNPACK_SKIP_IMAGES => _, GL_UNPACK_SKIP_PIXELS => _, GL_UNPACK_SKIP_ROWS => _, GL_UNSIGNALED => _, GL_UNSIGNED_BYTE => _, GL_UNSIGNED_INT => _, GL_UNSIGNED_INT_10F_11F_11F_REV => _, GL_UNSIGNED_INT_24_8 => _, GL_UNSIGNED_INT_2_10_10_10_REV => _, GL_UNSIGNED_INT_5_9_9_9_REV => _, GL_UNSIGNED_INT_SAMPLER_2D => _, GL_UNSIGNED_INT_SAMPLER_2D_ARRAY => _, GL_UNSIGNED_INT_SAMPLER_3D => _, GL_UNSIGNED_INT_SAMPLER_CUBE => _, GL_UNSIGNED_INT_VEC2 => _, GL_UNSIGNED_INT_VEC3 => _, GL_UNSIGNED_INT_VEC4 => _, GL_UNSIGNED_NORMALIZED => _, GL_UNSIGNED_SHORT => _, GL_UNSIGNED_SHORT_4_4_4_4 => _, GL_UNSIGNED_SHORT_5_5_5_1 => _, GL_UNSIGNED_SHORT_5_6_5 => _, GL_VALIDATE_STATUS => _, GL_VENDOR => _, GL_VERSION => _, GL_VERTEX_ARRAY_BINDING => _, GL_VERTEX_ATTRIB_ARRAY_BUFFER_BINDING => _, GL_VERTEX_ATTRIB_ARRAY_DIVISOR => _, GL_VERTEX_ATTRIB_ARRAY_ENABLED => _, GL_VERTEX_ATTRIB_ARRAY_INTEGER => _, GL_VERTEX_ATTRIB_ARRAY_NORMALIZED => _, GL_VERTEX_ATTRIB_ARRAY_POINTER => _, GL_VERTEX_ATTRIB_ARRAY_SIZE => _, GL_VERTEX_ATTRIB_ARRAY_STRIDE => _, GL_VERTEX_ATTRIB_ARRAY_TYPE => _, GL_VERTEX_PROGRAM_POINT_SIZE => _, GL_VERTEX_SHADER => _, GL_VIEWPORT => _, GL_WAIT_FAILED => _, GL_ZERO => _, *}
}