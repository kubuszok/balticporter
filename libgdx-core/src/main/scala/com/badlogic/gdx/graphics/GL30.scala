package com.badlogic.gdx.graphics

trait GL30 extends com.badlogic.gdx.graphics.GL20 {
  def glReadBuffer(mode: scala.Int): scala.Unit
  def glDrawRangeElements(mode: scala.Int, start: scala.Int, `end`: scala.Int, count: scala.Int, `type`: scala.Int, indices: java.nio.Buffer): scala.Unit
  def glDrawRangeElements(mode: scala.Int, start: scala.Int, `end`: scala.Int, count: scala.Int, `type`: scala.Int, offset: scala.Int): scala.Unit
  def glTexImage2D(target: scala.Int, level: scala.Int, internalformat: scala.Int, width: scala.Int, height: scala.Int, border: scala.Int, format: scala.Int, `type`: scala.Int, offset: scala.Int): scala.Unit
  def glTexImage3D(target: scala.Int, level: scala.Int, internalformat: scala.Int, width: scala.Int, height: scala.Int, depth: scala.Int, border: scala.Int, format: scala.Int, `type`: scala.Int, pixels: java.nio.Buffer): scala.Unit
  def glTexImage3D(target: scala.Int, level: scala.Int, internalformat: scala.Int, width: scala.Int, height: scala.Int, depth: scala.Int, border: scala.Int, format: scala.Int, `type`: scala.Int, offset: scala.Int): scala.Unit
  def glTexSubImage2D(target: scala.Int, level: scala.Int, xoffset: scala.Int, yoffset: scala.Int, width: scala.Int, height: scala.Int, format: scala.Int, `type`: scala.Int, offset: scala.Int): scala.Unit
  def glTexSubImage3D(target: scala.Int, level: scala.Int, xoffset: scala.Int, yoffset: scala.Int, zoffset: scala.Int, width: scala.Int, height: scala.Int, depth: scala.Int, format: scala.Int, `type`: scala.Int, pixels: java.nio.Buffer): scala.Unit
  def glTexSubImage3D(target: scala.Int, level: scala.Int, xoffset: scala.Int, yoffset: scala.Int, zoffset: scala.Int, width: scala.Int, height: scala.Int, depth: scala.Int, format: scala.Int, `type`: scala.Int, offset: scala.Int): scala.Unit
  def glCopyTexSubImage3D(target: scala.Int, level: scala.Int, xoffset: scala.Int, yoffset: scala.Int, zoffset: scala.Int, x: scala.Int, y: scala.Int, width: scala.Int, height: scala.Int): scala.Unit
  def glGenQueries(n: scala.Int, ids: scala.Array[scala.Int], offset: scala.Int): scala.Unit
  def glGenQueries(n: scala.Int, ids: java.nio.IntBuffer): scala.Unit
  def glDeleteQueries(n: scala.Int, ids: scala.Array[scala.Int], offset: scala.Int): scala.Unit
  def glDeleteQueries(n: scala.Int, ids: java.nio.IntBuffer): scala.Unit
  def glIsQuery(id: scala.Int): scala.Boolean
  def glBeginQuery(target: scala.Int, id: scala.Int): scala.Unit
  def glEndQuery(target: scala.Int): scala.Unit
  def glGetQueryiv(target: scala.Int, pname: scala.Int, params: java.nio.IntBuffer): scala.Unit
  def glGetQueryObjectuiv(id: scala.Int, pname: scala.Int, params: java.nio.IntBuffer): scala.Unit
  def glUnmapBuffer(target: scala.Int): scala.Boolean
  def glGetBufferPointerv(target: scala.Int, pname: scala.Int): java.nio.Buffer
  def glDrawBuffers(n: scala.Int, bufs: java.nio.IntBuffer): scala.Unit
  def glUniformMatrix2x3fv(location: scala.Int, count: scala.Int, transpose: scala.Boolean, value: java.nio.FloatBuffer): scala.Unit
  def glUniformMatrix3x2fv(location: scala.Int, count: scala.Int, transpose: scala.Boolean, value: java.nio.FloatBuffer): scala.Unit
  def glUniformMatrix2x4fv(location: scala.Int, count: scala.Int, transpose: scala.Boolean, value: java.nio.FloatBuffer): scala.Unit
  def glUniformMatrix4x2fv(location: scala.Int, count: scala.Int, transpose: scala.Boolean, value: java.nio.FloatBuffer): scala.Unit
  def glUniformMatrix3x4fv(location: scala.Int, count: scala.Int, transpose: scala.Boolean, value: java.nio.FloatBuffer): scala.Unit
  def glUniformMatrix4x3fv(location: scala.Int, count: scala.Int, transpose: scala.Boolean, value: java.nio.FloatBuffer): scala.Unit
  def glBlitFramebuffer(srcX0: scala.Int, srcY0: scala.Int, srcX1: scala.Int, srcY1: scala.Int, dstX0: scala.Int, dstY0: scala.Int, dstX1: scala.Int, dstY1: scala.Int, mask: scala.Int, filter: scala.Int): scala.Unit
  def glRenderbufferStorageMultisample(target: scala.Int, samples: scala.Int, internalformat: scala.Int, width: scala.Int, height: scala.Int): scala.Unit
  def glFramebufferTextureLayer(target: scala.Int, attachment: scala.Int, texture: scala.Int, level: scala.Int, layer: scala.Int): scala.Unit
  def glMapBufferRange(target: scala.Int, offset: scala.Int, length: scala.Int, access: scala.Int): java.nio.Buffer
  def glFlushMappedBufferRange(target: scala.Int, offset: scala.Int, length: scala.Int): scala.Unit
  def glBindVertexArray(array: scala.Int): scala.Unit
  def glDeleteVertexArrays(n: scala.Int, arrays: scala.Array[scala.Int], offset: scala.Int): scala.Unit
  def glDeleteVertexArrays(n: scala.Int, arrays: java.nio.IntBuffer): scala.Unit
  def glGenVertexArrays(n: scala.Int, arrays: scala.Array[scala.Int], offset: scala.Int): scala.Unit
  def glGenVertexArrays(n: scala.Int, arrays: java.nio.IntBuffer): scala.Unit
  def glIsVertexArray(array: scala.Int): scala.Boolean
  def glBeginTransformFeedback(primitiveMode: scala.Int): scala.Unit
  def glEndTransformFeedback(): scala.Unit
  def glBindBufferRange(target: scala.Int, index: scala.Int, buffer: scala.Int, offset: scala.Int, size: scala.Int): scala.Unit
  def glBindBufferBase(target: scala.Int, index: scala.Int, buffer: scala.Int): scala.Unit
  def glTransformFeedbackVaryings(program: scala.Int, varyings: scala.Array[java.lang.String], bufferMode: scala.Int): scala.Unit
  def glVertexAttribIPointer(index: scala.Int, size: scala.Int, `type`: scala.Int, stride: scala.Int, offset: scala.Int): scala.Unit
  def glGetVertexAttribIiv(index: scala.Int, pname: scala.Int, params: java.nio.IntBuffer): scala.Unit
  def glGetVertexAttribIuiv(index: scala.Int, pname: scala.Int, params: java.nio.IntBuffer): scala.Unit
  def glVertexAttribI4i(index: scala.Int, x: scala.Int, y: scala.Int, z: scala.Int, w: scala.Int): scala.Unit
  def glVertexAttribI4ui(index: scala.Int, x: scala.Int, y: scala.Int, z: scala.Int, w: scala.Int): scala.Unit
  def glGetUniformuiv(program: scala.Int, location: scala.Int, params: java.nio.IntBuffer): scala.Unit
  def glGetFragDataLocation(program: scala.Int, name: java.lang.String): scala.Int
  def glUniform1uiv(location: scala.Int, count: scala.Int, value: java.nio.IntBuffer): scala.Unit
  def glUniform3uiv(location: scala.Int, count: scala.Int, value: java.nio.IntBuffer): scala.Unit
  def glUniform4uiv(location: scala.Int, count: scala.Int, value: java.nio.IntBuffer): scala.Unit
  def glClearBufferiv(buffer: scala.Int, drawbuffer: scala.Int, value: java.nio.IntBuffer): scala.Unit
  def glClearBufferuiv(buffer: scala.Int, drawbuffer: scala.Int, value: java.nio.IntBuffer): scala.Unit
  def glClearBufferfv(buffer: scala.Int, drawbuffer: scala.Int, value: java.nio.FloatBuffer): scala.Unit
  def glClearBufferfi(buffer: scala.Int, drawbuffer: scala.Int, depth: scala.Float, stencil: scala.Int): scala.Unit
  def glGetStringi(name: scala.Int, index: scala.Int): java.lang.String
  def glCopyBufferSubData(readTarget: scala.Int, writeTarget: scala.Int, readOffset: scala.Int, writeOffset: scala.Int, size: scala.Int): scala.Unit
  def glGetUniformIndices(program: scala.Int, uniformNames: scala.Array[java.lang.String], uniformIndices: java.nio.IntBuffer): scala.Unit
  def glGetActiveUniformsiv(program: scala.Int, uniformCount: scala.Int, uniformIndices: java.nio.IntBuffer, pname: scala.Int, params: java.nio.IntBuffer): scala.Unit
  def glGetUniformBlockIndex(program: scala.Int, uniformBlockName: java.lang.String): scala.Int
  def glGetActiveUniformBlockiv(program: scala.Int, uniformBlockIndex: scala.Int, pname: scala.Int, params: java.nio.IntBuffer): scala.Unit
  def glGetActiveUniformBlockName(program: scala.Int, uniformBlockIndex: scala.Int, length: java.nio.Buffer, uniformBlockName: java.nio.Buffer): scala.Unit
  def glGetActiveUniformBlockName(program: scala.Int, uniformBlockIndex: scala.Int): java.lang.String
  def glUniformBlockBinding(program: scala.Int, uniformBlockIndex: scala.Int, uniformBlockBinding: scala.Int): scala.Unit
  def glDrawArraysInstanced(mode: scala.Int, first: scala.Int, count: scala.Int, instanceCount: scala.Int): scala.Unit
  def glDrawElementsInstanced(mode: scala.Int, count: scala.Int, `type`: scala.Int, indicesOffset: scala.Int, instanceCount: scala.Int): scala.Unit
  def glGetInteger64v(pname: scala.Int, params: java.nio.LongBuffer): scala.Unit
  def glGetBufferParameteri64v(target: scala.Int, pname: scala.Int, params: java.nio.LongBuffer): scala.Unit
  def glGenSamplers(count: scala.Int, samplers: scala.Array[scala.Int], offset: scala.Int): scala.Unit
  def glGenSamplers(count: scala.Int, samplers: java.nio.IntBuffer): scala.Unit
  def glDeleteSamplers(count: scala.Int, samplers: scala.Array[scala.Int], offset: scala.Int): scala.Unit
  def glDeleteSamplers(count: scala.Int, samplers: java.nio.IntBuffer): scala.Unit
  def glIsSampler(sampler: scala.Int): scala.Boolean
  def glBindSampler(unit: scala.Int, sampler: scala.Int): scala.Unit
  def glSamplerParameteri(sampler: scala.Int, pname: scala.Int, param: scala.Int): scala.Unit
  def glSamplerParameteriv(sampler: scala.Int, pname: scala.Int, param: java.nio.IntBuffer): scala.Unit
  def glSamplerParameterf(sampler: scala.Int, pname: scala.Int, param: scala.Float): scala.Unit
  def glSamplerParameterfv(sampler: scala.Int, pname: scala.Int, param: java.nio.FloatBuffer): scala.Unit
  def glGetSamplerParameteriv(sampler: scala.Int, pname: scala.Int, params: java.nio.IntBuffer): scala.Unit
  def glGetSamplerParameterfv(sampler: scala.Int, pname: scala.Int, params: java.nio.FloatBuffer): scala.Unit
  def glVertexAttribDivisor(index: scala.Int, divisor: scala.Int): scala.Unit
  def glBindTransformFeedback(target: scala.Int, id: scala.Int): scala.Unit
  def glDeleteTransformFeedbacks(n: scala.Int, ids: scala.Array[scala.Int], offset: scala.Int): scala.Unit
  def glDeleteTransformFeedbacks(n: scala.Int, ids: java.nio.IntBuffer): scala.Unit
  def glGenTransformFeedbacks(n: scala.Int, ids: scala.Array[scala.Int], offset: scala.Int): scala.Unit
  def glGenTransformFeedbacks(n: scala.Int, ids: java.nio.IntBuffer): scala.Unit
  def glIsTransformFeedback(id: scala.Int): scala.Boolean
  def glPauseTransformFeedback(): scala.Unit
  def glResumeTransformFeedback(): scala.Unit
  def glProgramParameteri(program: scala.Int, pname: scala.Int, value: scala.Int): scala.Unit
  def glInvalidateFramebuffer(target: scala.Int, numAttachments: scala.Int, attachments: java.nio.IntBuffer): scala.Unit
  def glInvalidateSubFramebuffer(target: scala.Int, numAttachments: scala.Int, attachments: java.nio.IntBuffer, x: scala.Int, y: scala.Int, width: scala.Int, height: scala.Int): scala.Unit
  @java.lang.Override
  @java.lang.Deprecated
  override def glVertexAttribPointer(indx: scala.Int, size: scala.Int, `type`: scala.Int, normalized: scala.Boolean, stride: scala.Int, ptr: java.nio.Buffer): scala.Unit
}
object GL30 {
  export com.badlogic.gdx.graphics.GL20.{GL_ACTIVE_UNIFORM_BLOCKS => _, GL_ACTIVE_UNIFORM_BLOCK_MAX_NAME_LENGTH => _, GL_ALREADY_SIGNALED => _, GL_ANY_SAMPLES_PASSED => _, GL_ANY_SAMPLES_PASSED_CONSERVATIVE => _, GL_BLUE => _, GL_BUFFER_ACCESS_FLAGS => _, GL_BUFFER_MAPPED => _, GL_BUFFER_MAP_LENGTH => _, GL_BUFFER_MAP_OFFSET => _, GL_BUFFER_MAP_POINTER => _, GL_COLOR => _, GL_COLOR_ATTACHMENT1 => _, GL_COLOR_ATTACHMENT10 => _, GL_COLOR_ATTACHMENT11 => _, GL_COLOR_ATTACHMENT12 => _, GL_COLOR_ATTACHMENT13 => _, GL_COLOR_ATTACHMENT14 => _, GL_COLOR_ATTACHMENT15 => _, GL_COLOR_ATTACHMENT2 => _, GL_COLOR_ATTACHMENT3 => _, GL_COLOR_ATTACHMENT4 => _, GL_COLOR_ATTACHMENT5 => _, GL_COLOR_ATTACHMENT6 => _, GL_COLOR_ATTACHMENT7 => _, GL_COLOR_ATTACHMENT8 => _, GL_COLOR_ATTACHMENT9 => _, GL_COMPARE_REF_TO_TEXTURE => _, GL_COMPRESSED_R11_EAC => _, GL_COMPRESSED_RG11_EAC => _, GL_COMPRESSED_RGB8_ETC2 => _, GL_COMPRESSED_RGB8_PUNCHTHROUGH_ALPHA1_ETC2 => _, GL_COMPRESSED_RGBA8_ETC2_EAC => _, GL_COMPRESSED_SIGNED_R11_EAC => _, GL_COMPRESSED_SIGNED_RG11_EAC => _, GL_COMPRESSED_SRGB8_ALPHA8_ETC2_EAC => _, GL_COMPRESSED_SRGB8_ETC2 => _, GL_COMPRESSED_SRGB8_PUNCHTHROUGH_ALPHA1_ETC2 => _, GL_CONDITION_SATISFIED => _, GL_COPY_READ_BUFFER => _, GL_COPY_READ_BUFFER_BINDING => _, GL_COPY_WRITE_BUFFER => _, GL_COPY_WRITE_BUFFER_BINDING => _, GL_CURRENT_QUERY => _, GL_DEPTH => _, GL_DEPTH24_STENCIL8 => _, GL_DEPTH32F_STENCIL8 => _, GL_DEPTH_COMPONENT24 => _, GL_DEPTH_COMPONENT32F => _, GL_DEPTH_STENCIL => _, GL_DEPTH_STENCIL_ATTACHMENT => _, GL_DRAW_BUFFER0 => _, GL_DRAW_BUFFER1 => _, GL_DRAW_BUFFER10 => _, GL_DRAW_BUFFER11 => _, GL_DRAW_BUFFER12 => _, GL_DRAW_BUFFER13 => _, GL_DRAW_BUFFER14 => _, GL_DRAW_BUFFER15 => _, GL_DRAW_BUFFER2 => _, GL_DRAW_BUFFER3 => _, GL_DRAW_BUFFER4 => _, GL_DRAW_BUFFER5 => _, GL_DRAW_BUFFER6 => _, GL_DRAW_BUFFER7 => _, GL_DRAW_BUFFER8 => _, GL_DRAW_BUFFER9 => _, GL_DRAW_FRAMEBUFFER => _, GL_DRAW_FRAMEBUFFER_BINDING => _, GL_DYNAMIC_COPY => _, GL_DYNAMIC_READ => _, GL_FLOAT_32_UNSIGNED_INT_24_8_REV => _, GL_FLOAT_MAT2x3 => _, GL_FLOAT_MAT2x4 => _, GL_FLOAT_MAT3x2 => _, GL_FLOAT_MAT3x4 => _, GL_FLOAT_MAT4x2 => _, GL_FLOAT_MAT4x3 => _, GL_FRAGMENT_SHADER_DERIVATIVE_HINT => _, GL_FRAMEBUFFER_ATTACHMENT_ALPHA_SIZE => _, GL_FRAMEBUFFER_ATTACHMENT_BLUE_SIZE => _, GL_FRAMEBUFFER_ATTACHMENT_COLOR_ENCODING => _, GL_FRAMEBUFFER_ATTACHMENT_COMPONENT_TYPE => _, GL_FRAMEBUFFER_ATTACHMENT_DEPTH_SIZE => _, GL_FRAMEBUFFER_ATTACHMENT_GREEN_SIZE => _, GL_FRAMEBUFFER_ATTACHMENT_RED_SIZE => _, GL_FRAMEBUFFER_ATTACHMENT_STENCIL_SIZE => _, GL_FRAMEBUFFER_ATTACHMENT_TEXTURE_LAYER => _, GL_FRAMEBUFFER_DEFAULT => _, GL_FRAMEBUFFER_INCOMPLETE_MULTISAMPLE => _, GL_FRAMEBUFFER_UNDEFINED => _, GL_GREEN => _, GL_HALF_FLOAT => _, GL_INTERLEAVED_ATTRIBS => _, GL_INT_2_10_10_10_REV => _, GL_INT_SAMPLER_2D => _, GL_INT_SAMPLER_2D_ARRAY => _, GL_INT_SAMPLER_3D => _, GL_INT_SAMPLER_CUBE => _, GL_INVALID_INDEX => _, GL_MAJOR_VERSION => _, GL_MAP_FLUSH_EXPLICIT_BIT => _, GL_MAP_INVALIDATE_BUFFER_BIT => _, GL_MAP_INVALIDATE_RANGE_BIT => _, GL_MAP_READ_BIT => _, GL_MAP_UNSYNCHRONIZED_BIT => _, GL_MAP_WRITE_BIT => _, GL_MAX => _, GL_MAX_3D_TEXTURE_SIZE => _, GL_MAX_ARRAY_TEXTURE_LAYERS => _, GL_MAX_COLOR_ATTACHMENTS => _, GL_MAX_COMBINED_FRAGMENT_UNIFORM_COMPONENTS => _, GL_MAX_COMBINED_UNIFORM_BLOCKS => _, GL_MAX_COMBINED_VERTEX_UNIFORM_COMPONENTS => _, GL_MAX_DRAW_BUFFERS => _, GL_MAX_ELEMENTS_INDICES => _, GL_MAX_ELEMENTS_VERTICES => _, GL_MAX_ELEMENT_INDEX => _, GL_MAX_FRAGMENT_INPUT_COMPONENTS => _, GL_MAX_FRAGMENT_UNIFORM_BLOCKS => _, GL_MAX_FRAGMENT_UNIFORM_COMPONENTS => _, GL_MAX_PROGRAM_TEXEL_OFFSET => _, GL_MAX_SAMPLES => _, GL_MAX_SERVER_WAIT_TIMEOUT => _, GL_MAX_TEXTURE_LOD_BIAS => _, GL_MAX_TRANSFORM_FEEDBACK_INTERLEAVED_COMPONENTS => _, GL_MAX_TRANSFORM_FEEDBACK_SEPARATE_ATTRIBS => _, GL_MAX_TRANSFORM_FEEDBACK_SEPARATE_COMPONENTS => _, GL_MAX_UNIFORM_BLOCK_SIZE => _, GL_MAX_UNIFORM_BUFFER_BINDINGS => _, GL_MAX_VARYING_COMPONENTS => _, GL_MAX_VERTEX_OUTPUT_COMPONENTS => _, GL_MAX_VERTEX_UNIFORM_BLOCKS => _, GL_MAX_VERTEX_UNIFORM_COMPONENTS => _, GL_MIN => _, GL_MINOR_VERSION => _, GL_MIN_PROGRAM_TEXEL_OFFSET => _, GL_NUM_EXTENSIONS => _, GL_NUM_PROGRAM_BINARY_FORMATS => _, GL_NUM_SAMPLE_COUNTS => _, GL_OBJECT_TYPE => _, GL_PACK_ROW_LENGTH => _, GL_PACK_SKIP_PIXELS => _, GL_PACK_SKIP_ROWS => _, GL_PIXEL_PACK_BUFFER => _, GL_PIXEL_PACK_BUFFER_BINDING => _, GL_PIXEL_UNPACK_BUFFER => _, GL_PIXEL_UNPACK_BUFFER_BINDING => _, GL_PRIMITIVE_RESTART_FIXED_INDEX => _, GL_PROGRAM_BINARY_FORMATS => _, GL_PROGRAM_BINARY_LENGTH => _, GL_PROGRAM_BINARY_RETRIEVABLE_HINT => _, GL_QUERY_RESULT => _, GL_QUERY_RESULT_AVAILABLE => _, GL_R11F_G11F_B10F => _, GL_R16F => _, GL_R16I => _, GL_R16UI => _, GL_R32F => _, GL_R32I => _, GL_R32UI => _, GL_R8 => _, GL_R8I => _, GL_R8UI => _, GL_R8_SNORM => _, GL_RASTERIZER_DISCARD => _, GL_READ_BUFFER => _, GL_READ_FRAMEBUFFER => _, GL_READ_FRAMEBUFFER_BINDING => _, GL_RED => _, GL_RED_INTEGER => _, GL_RENDERBUFFER_SAMPLES => _, GL_RG => _, GL_RG16F => _, GL_RG16I => _, GL_RG16UI => _, GL_RG32F => _, GL_RG32I => _, GL_RG32UI => _, GL_RG8 => _, GL_RG8I => _, GL_RG8UI => _, GL_RG8_SNORM => _, GL_RGB10_A2 => _, GL_RGB10_A2UI => _, GL_RGB16F => _, GL_RGB16I => _, GL_RGB16UI => _, GL_RGB32F => _, GL_RGB32I => _, GL_RGB32UI => _, GL_RGB8 => _, GL_RGB8I => _, GL_RGB8UI => _, GL_RGB8_SNORM => _, GL_RGB9_E5 => _, GL_RGBA16F => _, GL_RGBA16I => _, GL_RGBA16UI => _, GL_RGBA32F => _, GL_RGBA32I => _, GL_RGBA32UI => _, GL_RGBA8 => _, GL_RGBA8I => _, GL_RGBA8UI => _, GL_RGBA8_SNORM => _, GL_RGBA_INTEGER => _, GL_RGB_INTEGER => _, GL_RG_INTEGER => _, GL_SAMPLER_2D_ARRAY => _, GL_SAMPLER_2D_ARRAY_SHADOW => _, GL_SAMPLER_2D_SHADOW => _, GL_SAMPLER_3D => _, GL_SAMPLER_BINDING => _, GL_SAMPLER_CUBE_SHADOW => _, GL_SEPARATE_ATTRIBS => _, GL_SIGNALED => _, GL_SIGNED_NORMALIZED => _, GL_SRGB => _, GL_SRGB8 => _, GL_SRGB8_ALPHA8 => _, GL_STATIC_COPY => _, GL_STATIC_READ => _, GL_STENCIL => _, GL_STREAM_COPY => _, GL_STREAM_READ => _, GL_SYNC_CONDITION => _, GL_SYNC_FENCE => _, GL_SYNC_FLAGS => _, GL_SYNC_FLUSH_COMMANDS_BIT => _, GL_SYNC_GPU_COMMANDS_COMPLETE => _, GL_SYNC_STATUS => _, GL_TEXTURE_2D_ARRAY => _, GL_TEXTURE_3D => _, GL_TEXTURE_BASE_LEVEL => _, GL_TEXTURE_BINDING_2D_ARRAY => _, GL_TEXTURE_BINDING_3D => _, GL_TEXTURE_COMPARE_FUNC => _, GL_TEXTURE_COMPARE_MODE => _, GL_TEXTURE_IMMUTABLE_FORMAT => _, GL_TEXTURE_IMMUTABLE_LEVELS => _, GL_TEXTURE_MAX_LEVEL => _, GL_TEXTURE_MAX_LOD => _, GL_TEXTURE_MIN_LOD => _, GL_TEXTURE_SWIZZLE_A => _, GL_TEXTURE_SWIZZLE_B => _, GL_TEXTURE_SWIZZLE_G => _, GL_TEXTURE_SWIZZLE_R => _, GL_TEXTURE_WRAP_R => _, GL_TIMEOUT_EXPIRED => _, GL_TIMEOUT_IGNORED => _, GL_TRANSFORM_FEEDBACK => _, GL_TRANSFORM_FEEDBACK_ACTIVE => _, GL_TRANSFORM_FEEDBACK_BINDING => _, GL_TRANSFORM_FEEDBACK_BUFFER => _, GL_TRANSFORM_FEEDBACK_BUFFER_BINDING => _, GL_TRANSFORM_FEEDBACK_BUFFER_MODE => _, GL_TRANSFORM_FEEDBACK_BUFFER_SIZE => _, GL_TRANSFORM_FEEDBACK_BUFFER_START => _, GL_TRANSFORM_FEEDBACK_PAUSED => _, GL_TRANSFORM_FEEDBACK_PRIMITIVES_WRITTEN => _, GL_TRANSFORM_FEEDBACK_VARYINGS => _, GL_TRANSFORM_FEEDBACK_VARYING_MAX_LENGTH => _, GL_UNIFORM_ARRAY_STRIDE => _, GL_UNIFORM_BLOCK_ACTIVE_UNIFORMS => _, GL_UNIFORM_BLOCK_ACTIVE_UNIFORM_INDICES => _, GL_UNIFORM_BLOCK_BINDING => _, GL_UNIFORM_BLOCK_DATA_SIZE => _, GL_UNIFORM_BLOCK_INDEX => _, GL_UNIFORM_BLOCK_NAME_LENGTH => _, GL_UNIFORM_BLOCK_REFERENCED_BY_FRAGMENT_SHADER => _, GL_UNIFORM_BLOCK_REFERENCED_BY_VERTEX_SHADER => _, GL_UNIFORM_BUFFER => _, GL_UNIFORM_BUFFER_BINDING => _, GL_UNIFORM_BUFFER_OFFSET_ALIGNMENT => _, GL_UNIFORM_BUFFER_SIZE => _, GL_UNIFORM_BUFFER_START => _, GL_UNIFORM_IS_ROW_MAJOR => _, GL_UNIFORM_MATRIX_STRIDE => _, GL_UNIFORM_NAME_LENGTH => _, GL_UNIFORM_OFFSET => _, GL_UNIFORM_SIZE => _, GL_UNIFORM_TYPE => _, GL_UNPACK_IMAGE_HEIGHT => _, GL_UNPACK_ROW_LENGTH => _, GL_UNPACK_SKIP_IMAGES => _, GL_UNPACK_SKIP_PIXELS => _, GL_UNPACK_SKIP_ROWS => _, GL_UNSIGNALED => _, GL_UNSIGNED_INT_10F_11F_11F_REV => _, GL_UNSIGNED_INT_24_8 => _, GL_UNSIGNED_INT_2_10_10_10_REV => _, GL_UNSIGNED_INT_5_9_9_9_REV => _, GL_UNSIGNED_INT_SAMPLER_2D => _, GL_UNSIGNED_INT_SAMPLER_2D_ARRAY => _, GL_UNSIGNED_INT_SAMPLER_3D => _, GL_UNSIGNED_INT_SAMPLER_CUBE => _, GL_UNSIGNED_INT_VEC2 => _, GL_UNSIGNED_INT_VEC3 => _, GL_UNSIGNED_INT_VEC4 => _, GL_UNSIGNED_NORMALIZED => _, GL_VERTEX_ARRAY_BINDING => _, GL_VERTEX_ATTRIB_ARRAY_DIVISOR => _, GL_VERTEX_ATTRIB_ARRAY_INTEGER => _, GL_WAIT_FAILED => _, *}
  final val GL_READ_BUFFER: scala.Int = 3074
  final val GL_UNPACK_ROW_LENGTH: scala.Int = 3314
  final val GL_UNPACK_SKIP_ROWS: scala.Int = 3315
  final val GL_UNPACK_SKIP_PIXELS: scala.Int = 3316
  final val GL_PACK_ROW_LENGTH: scala.Int = 3330
  final val GL_PACK_SKIP_ROWS: scala.Int = 3331
  final val GL_PACK_SKIP_PIXELS: scala.Int = 3332
  final val GL_COLOR: scala.Int = 6144
  final val GL_DEPTH: scala.Int = 6145
  final val GL_STENCIL: scala.Int = 6146
  final val GL_RED: scala.Int = 6403
  final val GL_RGB8: scala.Int = 32849
  final val GL_RGBA8: scala.Int = 32856
  final val GL_RGB10_A2: scala.Int = 32857
  final val GL_TEXTURE_BINDING_3D: scala.Int = 32874
  final val GL_UNPACK_SKIP_IMAGES: scala.Int = 32877
  final val GL_UNPACK_IMAGE_HEIGHT: scala.Int = 32878
  final val GL_TEXTURE_3D: scala.Int = 32879
  final val GL_TEXTURE_WRAP_R: scala.Int = 32882
  final val GL_MAX_3D_TEXTURE_SIZE: scala.Int = 32883
  final val GL_UNSIGNED_INT_2_10_10_10_REV: scala.Int = 33640
  final val GL_MAX_ELEMENTS_VERTICES: scala.Int = 33000
  final val GL_MAX_ELEMENTS_INDICES: scala.Int = 33001
  final val GL_TEXTURE_MIN_LOD: scala.Int = 33082
  final val GL_TEXTURE_MAX_LOD: scala.Int = 33083
  final val GL_TEXTURE_BASE_LEVEL: scala.Int = 33084
  final val GL_TEXTURE_MAX_LEVEL: scala.Int = 33085
  final val GL_MIN: scala.Int = 32775
  final val GL_MAX: scala.Int = 32776
  final val GL_DEPTH_COMPONENT24: scala.Int = 33190
  final val GL_MAX_TEXTURE_LOD_BIAS: scala.Int = 34045
  final val GL_TEXTURE_COMPARE_MODE: scala.Int = 34892
  final val GL_TEXTURE_COMPARE_FUNC: scala.Int = 34893
  final val GL_CURRENT_QUERY: scala.Int = 34917
  final val GL_QUERY_RESULT: scala.Int = 34918
  final val GL_QUERY_RESULT_AVAILABLE: scala.Int = 34919
  final val GL_BUFFER_MAPPED: scala.Int = 35004
  final val GL_BUFFER_MAP_POINTER: scala.Int = 35005
  final val GL_STREAM_READ: scala.Int = 35041
  final val GL_STREAM_COPY: scala.Int = 35042
  final val GL_STATIC_READ: scala.Int = 35045
  final val GL_STATIC_COPY: scala.Int = 35046
  final val GL_DYNAMIC_READ: scala.Int = 35049
  final val GL_DYNAMIC_COPY: scala.Int = 35050
  final val GL_MAX_DRAW_BUFFERS: scala.Int = 34852
  final val GL_DRAW_BUFFER0: scala.Int = 34853
  final val GL_DRAW_BUFFER1: scala.Int = 34854
  final val GL_DRAW_BUFFER2: scala.Int = 34855
  final val GL_DRAW_BUFFER3: scala.Int = 34856
  final val GL_DRAW_BUFFER4: scala.Int = 34857
  final val GL_DRAW_BUFFER5: scala.Int = 34858
  final val GL_DRAW_BUFFER6: scala.Int = 34859
  final val GL_DRAW_BUFFER7: scala.Int = 34860
  final val GL_DRAW_BUFFER8: scala.Int = 34861
  final val GL_DRAW_BUFFER9: scala.Int = 34862
  final val GL_DRAW_BUFFER10: scala.Int = 34863
  final val GL_DRAW_BUFFER11: scala.Int = 34864
  final val GL_DRAW_BUFFER12: scala.Int = 34865
  final val GL_DRAW_BUFFER13: scala.Int = 34866
  final val GL_DRAW_BUFFER14: scala.Int = 34867
  final val GL_DRAW_BUFFER15: scala.Int = 34868
  final val GL_MAX_FRAGMENT_UNIFORM_COMPONENTS: scala.Int = 35657
  final val GL_MAX_VERTEX_UNIFORM_COMPONENTS: scala.Int = 35658
  final val GL_SAMPLER_3D: scala.Int = 35679
  final val GL_SAMPLER_2D_SHADOW: scala.Int = 35682
  final val GL_FRAGMENT_SHADER_DERIVATIVE_HINT: scala.Int = 35723
  final val GL_PIXEL_PACK_BUFFER: scala.Int = 35051
  final val GL_PIXEL_UNPACK_BUFFER: scala.Int = 35052
  final val GL_PIXEL_PACK_BUFFER_BINDING: scala.Int = 35053
  final val GL_PIXEL_UNPACK_BUFFER_BINDING: scala.Int = 35055
  final val GL_FLOAT_MAT2x3: scala.Int = 35685
  final val GL_FLOAT_MAT2x4: scala.Int = 35686
  final val GL_FLOAT_MAT3x2: scala.Int = 35687
  final val GL_FLOAT_MAT3x4: scala.Int = 35688
  final val GL_FLOAT_MAT4x2: scala.Int = 35689
  final val GL_FLOAT_MAT4x3: scala.Int = 35690
  final val GL_SRGB: scala.Int = 35904
  final val GL_SRGB8: scala.Int = 35905
  final val GL_SRGB8_ALPHA8: scala.Int = 35907
  final val GL_COMPARE_REF_TO_TEXTURE: scala.Int = 34894
  final val GL_MAJOR_VERSION: scala.Int = 33307
  final val GL_MINOR_VERSION: scala.Int = 33308
  final val GL_NUM_EXTENSIONS: scala.Int = 33309
  final val GL_RGBA32F: scala.Int = 34836
  final val GL_RGB32F: scala.Int = 34837
  final val GL_RGBA16F: scala.Int = 34842
  final val GL_RGB16F: scala.Int = 34843
  final val GL_VERTEX_ATTRIB_ARRAY_INTEGER: scala.Int = 35069
  final val GL_MAX_ARRAY_TEXTURE_LAYERS: scala.Int = 35071
  final val GL_MIN_PROGRAM_TEXEL_OFFSET: scala.Int = 35076
  final val GL_MAX_PROGRAM_TEXEL_OFFSET: scala.Int = 35077
  final val GL_MAX_VARYING_COMPONENTS: scala.Int = 35659
  final val GL_TEXTURE_2D_ARRAY: scala.Int = 35866
  final val GL_TEXTURE_BINDING_2D_ARRAY: scala.Int = 35869
  final val GL_R11F_G11F_B10F: scala.Int = 35898
  final val GL_UNSIGNED_INT_10F_11F_11F_REV: scala.Int = 35899
  final val GL_RGB9_E5: scala.Int = 35901
  final val GL_UNSIGNED_INT_5_9_9_9_REV: scala.Int = 35902
  final val GL_TRANSFORM_FEEDBACK_VARYING_MAX_LENGTH: scala.Int = 35958
  final val GL_TRANSFORM_FEEDBACK_BUFFER_MODE: scala.Int = 35967
  final val GL_MAX_TRANSFORM_FEEDBACK_SEPARATE_COMPONENTS: scala.Int = 35968
  final val GL_TRANSFORM_FEEDBACK_VARYINGS: scala.Int = 35971
  final val GL_TRANSFORM_FEEDBACK_BUFFER_START: scala.Int = 35972
  final val GL_TRANSFORM_FEEDBACK_BUFFER_SIZE: scala.Int = 35973
  final val GL_TRANSFORM_FEEDBACK_PRIMITIVES_WRITTEN: scala.Int = 35976
  final val GL_RASTERIZER_DISCARD: scala.Int = 35977
  final val GL_MAX_TRANSFORM_FEEDBACK_INTERLEAVED_COMPONENTS: scala.Int = 35978
  final val GL_MAX_TRANSFORM_FEEDBACK_SEPARATE_ATTRIBS: scala.Int = 35979
  final val GL_INTERLEAVED_ATTRIBS: scala.Int = 35980
  final val GL_SEPARATE_ATTRIBS: scala.Int = 35981
  final val GL_TRANSFORM_FEEDBACK_BUFFER: scala.Int = 35982
  final val GL_TRANSFORM_FEEDBACK_BUFFER_BINDING: scala.Int = 35983
  final val GL_RGBA32UI: scala.Int = 36208
  final val GL_RGB32UI: scala.Int = 36209
  final val GL_RGBA16UI: scala.Int = 36214
  final val GL_RGB16UI: scala.Int = 36215
  final val GL_RGBA8UI: scala.Int = 36220
  final val GL_RGB8UI: scala.Int = 36221
  final val GL_RGBA32I: scala.Int = 36226
  final val GL_RGB32I: scala.Int = 36227
  final val GL_RGBA16I: scala.Int = 36232
  final val GL_RGB16I: scala.Int = 36233
  final val GL_RGBA8I: scala.Int = 36238
  final val GL_RGB8I: scala.Int = 36239
  final val GL_RED_INTEGER: scala.Int = 36244
  final val GL_RGB_INTEGER: scala.Int = 36248
  final val GL_RGBA_INTEGER: scala.Int = 36249
  final val GL_SAMPLER_2D_ARRAY: scala.Int = 36289
  final val GL_SAMPLER_2D_ARRAY_SHADOW: scala.Int = 36292
  final val GL_SAMPLER_CUBE_SHADOW: scala.Int = 36293
  final val GL_UNSIGNED_INT_VEC2: scala.Int = 36294
  final val GL_UNSIGNED_INT_VEC3: scala.Int = 36295
  final val GL_UNSIGNED_INT_VEC4: scala.Int = 36296
  final val GL_INT_SAMPLER_2D: scala.Int = 36298
  final val GL_INT_SAMPLER_3D: scala.Int = 36299
  final val GL_INT_SAMPLER_CUBE: scala.Int = 36300
  final val GL_INT_SAMPLER_2D_ARRAY: scala.Int = 36303
  final val GL_UNSIGNED_INT_SAMPLER_2D: scala.Int = 36306
  final val GL_UNSIGNED_INT_SAMPLER_3D: scala.Int = 36307
  final val GL_UNSIGNED_INT_SAMPLER_CUBE: scala.Int = 36308
  final val GL_UNSIGNED_INT_SAMPLER_2D_ARRAY: scala.Int = 36311
  final val GL_BUFFER_ACCESS_FLAGS: scala.Int = 37151
  final val GL_BUFFER_MAP_LENGTH: scala.Int = 37152
  final val GL_BUFFER_MAP_OFFSET: scala.Int = 37153
  final val GL_DEPTH_COMPONENT32F: scala.Int = 36012
  final val GL_DEPTH32F_STENCIL8: scala.Int = 36013
  final val GL_FLOAT_32_UNSIGNED_INT_24_8_REV: scala.Int = 36269
  final val GL_FRAMEBUFFER_ATTACHMENT_COLOR_ENCODING: scala.Int = 33296
  final val GL_FRAMEBUFFER_ATTACHMENT_COMPONENT_TYPE: scala.Int = 33297
  final val GL_FRAMEBUFFER_ATTACHMENT_RED_SIZE: scala.Int = 33298
  final val GL_FRAMEBUFFER_ATTACHMENT_GREEN_SIZE: scala.Int = 33299
  final val GL_FRAMEBUFFER_ATTACHMENT_BLUE_SIZE: scala.Int = 33300
  final val GL_FRAMEBUFFER_ATTACHMENT_ALPHA_SIZE: scala.Int = 33301
  final val GL_FRAMEBUFFER_ATTACHMENT_DEPTH_SIZE: scala.Int = 33302
  final val GL_FRAMEBUFFER_ATTACHMENT_STENCIL_SIZE: scala.Int = 33303
  final val GL_FRAMEBUFFER_DEFAULT: scala.Int = 33304
  final val GL_FRAMEBUFFER_UNDEFINED: scala.Int = 33305
  final val GL_DEPTH_STENCIL_ATTACHMENT: scala.Int = 33306
  final val GL_DEPTH_STENCIL: scala.Int = 34041
  final val GL_UNSIGNED_INT_24_8: scala.Int = 34042
  final val GL_DEPTH24_STENCIL8: scala.Int = 35056
  final val GL_UNSIGNED_NORMALIZED: scala.Int = 35863
  final val GL_DRAW_FRAMEBUFFER_BINDING: scala.Int = com.badlogic.gdx.graphics.GL20.GL_FRAMEBUFFER_BINDING
  final val GL_READ_FRAMEBUFFER: scala.Int = 36008
  final val GL_DRAW_FRAMEBUFFER: scala.Int = 36009
  final val GL_READ_FRAMEBUFFER_BINDING: scala.Int = 36010
  final val GL_RENDERBUFFER_SAMPLES: scala.Int = 36011
  final val GL_FRAMEBUFFER_ATTACHMENT_TEXTURE_LAYER: scala.Int = 36052
  final val GL_MAX_COLOR_ATTACHMENTS: scala.Int = 36063
  final val GL_COLOR_ATTACHMENT1: scala.Int = 36065
  final val GL_COLOR_ATTACHMENT2: scala.Int = 36066
  final val GL_COLOR_ATTACHMENT3: scala.Int = 36067
  final val GL_COLOR_ATTACHMENT4: scala.Int = 36068
  final val GL_COLOR_ATTACHMENT5: scala.Int = 36069
  final val GL_COLOR_ATTACHMENT6: scala.Int = 36070
  final val GL_COLOR_ATTACHMENT7: scala.Int = 36071
  final val GL_COLOR_ATTACHMENT8: scala.Int = 36072
  final val GL_COLOR_ATTACHMENT9: scala.Int = 36073
  final val GL_COLOR_ATTACHMENT10: scala.Int = 36074
  final val GL_COLOR_ATTACHMENT11: scala.Int = 36075
  final val GL_COLOR_ATTACHMENT12: scala.Int = 36076
  final val GL_COLOR_ATTACHMENT13: scala.Int = 36077
  final val GL_COLOR_ATTACHMENT14: scala.Int = 36078
  final val GL_COLOR_ATTACHMENT15: scala.Int = 36079
  final val GL_FRAMEBUFFER_INCOMPLETE_MULTISAMPLE: scala.Int = 36182
  final val GL_MAX_SAMPLES: scala.Int = 36183
  final val GL_HALF_FLOAT: scala.Int = 5131
  final val GL_MAP_READ_BIT: scala.Int = 1
  final val GL_MAP_WRITE_BIT: scala.Int = 2
  final val GL_MAP_INVALIDATE_RANGE_BIT: scala.Int = 4
  final val GL_MAP_INVALIDATE_BUFFER_BIT: scala.Int = 8
  final val GL_MAP_FLUSH_EXPLICIT_BIT: scala.Int = 16
  final val GL_MAP_UNSYNCHRONIZED_BIT: scala.Int = 32
  final val GL_RG: scala.Int = 33319
  final val GL_RG_INTEGER: scala.Int = 33320
  final val GL_R8: scala.Int = 33321
  final val GL_RG8: scala.Int = 33323
  final val GL_R16F: scala.Int = 33325
  final val GL_R32F: scala.Int = 33326
  final val GL_RG16F: scala.Int = 33327
  final val GL_RG32F: scala.Int = 33328
  final val GL_R8I: scala.Int = 33329
  final val GL_R8UI: scala.Int = 33330
  final val GL_R16I: scala.Int = 33331
  final val GL_R16UI: scala.Int = 33332
  final val GL_R32I: scala.Int = 33333
  final val GL_R32UI: scala.Int = 33334
  final val GL_RG8I: scala.Int = 33335
  final val GL_RG8UI: scala.Int = 33336
  final val GL_RG16I: scala.Int = 33337
  final val GL_RG16UI: scala.Int = 33338
  final val GL_RG32I: scala.Int = 33339
  final val GL_RG32UI: scala.Int = 33340
  final val GL_VERTEX_ARRAY_BINDING: scala.Int = 34229
  final val GL_R8_SNORM: scala.Int = 36756
  final val GL_RG8_SNORM: scala.Int = 36757
  final val GL_RGB8_SNORM: scala.Int = 36758
  final val GL_RGBA8_SNORM: scala.Int = 36759
  final val GL_SIGNED_NORMALIZED: scala.Int = 36764
  final val GL_PRIMITIVE_RESTART_FIXED_INDEX: scala.Int = 36201
  final val GL_COPY_READ_BUFFER: scala.Int = 36662
  final val GL_COPY_WRITE_BUFFER: scala.Int = 36663
  final val GL_COPY_READ_BUFFER_BINDING: scala.Int = GL30.GL_COPY_READ_BUFFER
  final val GL_COPY_WRITE_BUFFER_BINDING: scala.Int = GL30.GL_COPY_WRITE_BUFFER
  final val GL_UNIFORM_BUFFER: scala.Int = 35345
  final val GL_UNIFORM_BUFFER_BINDING: scala.Int = 35368
  final val GL_UNIFORM_BUFFER_START: scala.Int = 35369
  final val GL_UNIFORM_BUFFER_SIZE: scala.Int = 35370
  final val GL_MAX_VERTEX_UNIFORM_BLOCKS: scala.Int = 35371
  final val GL_MAX_FRAGMENT_UNIFORM_BLOCKS: scala.Int = 35373
  final val GL_MAX_COMBINED_UNIFORM_BLOCKS: scala.Int = 35374
  final val GL_MAX_UNIFORM_BUFFER_BINDINGS: scala.Int = 35375
  final val GL_MAX_UNIFORM_BLOCK_SIZE: scala.Int = 35376
  final val GL_MAX_COMBINED_VERTEX_UNIFORM_COMPONENTS: scala.Int = 35377
  final val GL_MAX_COMBINED_FRAGMENT_UNIFORM_COMPONENTS: scala.Int = 35379
  final val GL_UNIFORM_BUFFER_OFFSET_ALIGNMENT: scala.Int = 35380
  final val GL_ACTIVE_UNIFORM_BLOCK_MAX_NAME_LENGTH: scala.Int = 35381
  final val GL_ACTIVE_UNIFORM_BLOCKS: scala.Int = 35382
  final val GL_UNIFORM_TYPE: scala.Int = 35383
  final val GL_UNIFORM_SIZE: scala.Int = 35384
  final val GL_UNIFORM_NAME_LENGTH: scala.Int = 35385
  final val GL_UNIFORM_BLOCK_INDEX: scala.Int = 35386
  final val GL_UNIFORM_OFFSET: scala.Int = 35387
  final val GL_UNIFORM_ARRAY_STRIDE: scala.Int = 35388
  final val GL_UNIFORM_MATRIX_STRIDE: scala.Int = 35389
  final val GL_UNIFORM_IS_ROW_MAJOR: scala.Int = 35390
  final val GL_UNIFORM_BLOCK_BINDING: scala.Int = 35391
  final val GL_UNIFORM_BLOCK_DATA_SIZE: scala.Int = 35392
  final val GL_UNIFORM_BLOCK_NAME_LENGTH: scala.Int = 35393
  final val GL_UNIFORM_BLOCK_ACTIVE_UNIFORMS: scala.Int = 35394
  final val GL_UNIFORM_BLOCK_ACTIVE_UNIFORM_INDICES: scala.Int = 35395
  final val GL_UNIFORM_BLOCK_REFERENCED_BY_VERTEX_SHADER: scala.Int = 35396
  final val GL_UNIFORM_BLOCK_REFERENCED_BY_FRAGMENT_SHADER: scala.Int = 35398
  final val GL_INVALID_INDEX: scala.Int = -1
  final val GL_MAX_VERTEX_OUTPUT_COMPONENTS: scala.Int = 37154
  final val GL_MAX_FRAGMENT_INPUT_COMPONENTS: scala.Int = 37157
  final val GL_MAX_SERVER_WAIT_TIMEOUT: scala.Int = 37137
  final val GL_OBJECT_TYPE: scala.Int = 37138
  final val GL_SYNC_CONDITION: scala.Int = 37139
  final val GL_SYNC_STATUS: scala.Int = 37140
  final val GL_SYNC_FLAGS: scala.Int = 37141
  final val GL_SYNC_FENCE: scala.Int = 37142
  final val GL_SYNC_GPU_COMMANDS_COMPLETE: scala.Int = 37143
  final val GL_UNSIGNALED: scala.Int = 37144
  final val GL_SIGNALED: scala.Int = 37145
  final val GL_ALREADY_SIGNALED: scala.Int = 37146
  final val GL_TIMEOUT_EXPIRED: scala.Int = 37147
  final val GL_CONDITION_SATISFIED: scala.Int = 37148
  final val GL_WAIT_FAILED: scala.Int = 37149
  final val GL_SYNC_FLUSH_COMMANDS_BIT: scala.Int = 1
  final val GL_TIMEOUT_IGNORED: scala.Long = -1
  final val GL_VERTEX_ATTRIB_ARRAY_DIVISOR: scala.Int = 35070
  final val GL_ANY_SAMPLES_PASSED: scala.Int = 35887
  final val GL_ANY_SAMPLES_PASSED_CONSERVATIVE: scala.Int = 36202
  final val GL_SAMPLER_BINDING: scala.Int = 35097
  final val GL_RGB10_A2UI: scala.Int = 36975
  final val GL_TEXTURE_SWIZZLE_R: scala.Int = 36418
  final val GL_TEXTURE_SWIZZLE_G: scala.Int = 36419
  final val GL_TEXTURE_SWIZZLE_B: scala.Int = 36420
  final val GL_TEXTURE_SWIZZLE_A: scala.Int = 36421
  final val GL_GREEN: scala.Int = 6404
  final val GL_BLUE: scala.Int = 6405
  final val GL_INT_2_10_10_10_REV: scala.Int = 36255
  final val GL_TRANSFORM_FEEDBACK: scala.Int = 36386
  final val GL_TRANSFORM_FEEDBACK_PAUSED: scala.Int = 36387
  final val GL_TRANSFORM_FEEDBACK_ACTIVE: scala.Int = 36388
  final val GL_TRANSFORM_FEEDBACK_BINDING: scala.Int = 36389
  final val GL_PROGRAM_BINARY_RETRIEVABLE_HINT: scala.Int = 33367
  final val GL_PROGRAM_BINARY_LENGTH: scala.Int = 34625
  final val GL_NUM_PROGRAM_BINARY_FORMATS: scala.Int = 34814
  final val GL_PROGRAM_BINARY_FORMATS: scala.Int = 34815
  final val GL_COMPRESSED_R11_EAC: scala.Int = 37488
  final val GL_COMPRESSED_SIGNED_R11_EAC: scala.Int = 37489
  final val GL_COMPRESSED_RG11_EAC: scala.Int = 37490
  final val GL_COMPRESSED_SIGNED_RG11_EAC: scala.Int = 37491
  final val GL_COMPRESSED_RGB8_ETC2: scala.Int = 37492
  final val GL_COMPRESSED_SRGB8_ETC2: scala.Int = 37493
  final val GL_COMPRESSED_RGB8_PUNCHTHROUGH_ALPHA1_ETC2: scala.Int = 37494
  final val GL_COMPRESSED_SRGB8_PUNCHTHROUGH_ALPHA1_ETC2: scala.Int = 37495
  final val GL_COMPRESSED_RGBA8_ETC2_EAC: scala.Int = 37496
  final val GL_COMPRESSED_SRGB8_ALPHA8_ETC2_EAC: scala.Int = 37497
  final val GL_TEXTURE_IMMUTABLE_FORMAT: scala.Int = 37167
  final val GL_MAX_ELEMENT_INDEX: scala.Int = 36203
  final val GL_NUM_SAMPLE_COUNTS: scala.Int = 37760
  final val GL_TEXTURE_IMMUTABLE_LEVELS: scala.Int = 33503
}