package com.badlogic.gdx.graphics

trait GL31 extends com.badlogic.gdx.graphics.GL30 {
  def glDispatchCompute(num_groups_x: scala.Int, num_groups_y: scala.Int, num_groups_z: scala.Int): scala.Unit
  def glDispatchComputeIndirect(indirect: scala.Long): scala.Unit
  def glDrawArraysIndirect(mode: scala.Int, indirect: scala.Long): scala.Unit
  def glDrawElementsIndirect(mode: scala.Int, `type`: scala.Int, indirect: scala.Long): scala.Unit
  def glFramebufferParameteri(target: scala.Int, pname: scala.Int, param: scala.Int): scala.Unit
  def glGetFramebufferParameteriv(target: scala.Int, pname: scala.Int, params: java.nio.IntBuffer): scala.Unit
  def glGetProgramInterfaceiv(program: scala.Int, programInterface: scala.Int, pname: scala.Int, params: java.nio.IntBuffer): scala.Unit
  def glGetProgramResourceIndex(program: scala.Int, programInterface: scala.Int, name: java.lang.String): scala.Int
  def glGetProgramResourceName(program: scala.Int, programInterface: scala.Int, index: scala.Int): java.lang.String
  def glGetProgramResourceiv(program: scala.Int, programInterface: scala.Int, index: scala.Int, props: java.nio.IntBuffer, length: java.nio.IntBuffer, params: java.nio.IntBuffer): scala.Unit
  def glGetProgramResourceLocation(program: scala.Int, programInterface: scala.Int, name: java.lang.String): scala.Int
  def glUseProgramStages(pipeline: scala.Int, stages: scala.Int, program: scala.Int): scala.Unit
  def glActiveShaderProgram(pipeline: scala.Int, program: scala.Int): scala.Unit
  def glCreateShaderProgramv(`type`: scala.Int, strings: scala.Array[java.lang.String]): scala.Int
  def glBindProgramPipeline(pipeline: scala.Int): scala.Unit
  def glDeleteProgramPipelines(n: scala.Int, pipelines: java.nio.IntBuffer): scala.Unit
  def glGenProgramPipelines(n: scala.Int, pipelines: java.nio.IntBuffer): scala.Unit
  def glIsProgramPipeline(pipeline: scala.Int): scala.Boolean
  def glGetProgramPipelineiv(pipeline: scala.Int, pname: scala.Int, params: java.nio.IntBuffer): scala.Unit
  def glProgramUniform1i(program: scala.Int, location: scala.Int, v0: scala.Int): scala.Unit
  def glProgramUniform2i(program: scala.Int, location: scala.Int, v0: scala.Int, v1: scala.Int): scala.Unit
  def glProgramUniform3i(program: scala.Int, location: scala.Int, v0: scala.Int, v1: scala.Int, v2: scala.Int): scala.Unit
  def glProgramUniform4i(program: scala.Int, location: scala.Int, v0: scala.Int, v1: scala.Int, v2: scala.Int, v3: scala.Int): scala.Unit
  def glProgramUniform1ui(program: scala.Int, location: scala.Int, v0: scala.Int): scala.Unit
  def glProgramUniform2ui(program: scala.Int, location: scala.Int, v0: scala.Int, v1: scala.Int): scala.Unit
  def glProgramUniform3ui(program: scala.Int, location: scala.Int, v0: scala.Int, v1: scala.Int, v2: scala.Int): scala.Unit
  def glProgramUniform4ui(program: scala.Int, location: scala.Int, v0: scala.Int, v1: scala.Int, v2: scala.Int, v3: scala.Int): scala.Unit
  def glProgramUniform1f(program: scala.Int, location: scala.Int, v0: scala.Float): scala.Unit
  def glProgramUniform2f(program: scala.Int, location: scala.Int, v0: scala.Float, v1: scala.Float): scala.Unit
  def glProgramUniform3f(program: scala.Int, location: scala.Int, v0: scala.Float, v1: scala.Float, v2: scala.Float): scala.Unit
  def glProgramUniform4f(program: scala.Int, location: scala.Int, v0: scala.Float, v1: scala.Float, v2: scala.Float, v3: scala.Float): scala.Unit
  def glProgramUniform1iv(program: scala.Int, location: scala.Int, value: java.nio.IntBuffer): scala.Unit
  def glProgramUniform2iv(program: scala.Int, location: scala.Int, value: java.nio.IntBuffer): scala.Unit
  def glProgramUniform3iv(program: scala.Int, location: scala.Int, value: java.nio.IntBuffer): scala.Unit
  def glProgramUniform4iv(program: scala.Int, location: scala.Int, value: java.nio.IntBuffer): scala.Unit
  def glProgramUniform1uiv(program: scala.Int, location: scala.Int, value: java.nio.IntBuffer): scala.Unit
  def glProgramUniform2uiv(program: scala.Int, location: scala.Int, value: java.nio.IntBuffer): scala.Unit
  def glProgramUniform3uiv(program: scala.Int, location: scala.Int, value: java.nio.IntBuffer): scala.Unit
  def glProgramUniform4uiv(program: scala.Int, location: scala.Int, value: java.nio.IntBuffer): scala.Unit
  def glProgramUniform1fv(program: scala.Int, location: scala.Int, value: java.nio.FloatBuffer): scala.Unit
  def glProgramUniform2fv(program: scala.Int, location: scala.Int, value: java.nio.FloatBuffer): scala.Unit
  def glProgramUniform3fv(program: scala.Int, location: scala.Int, value: java.nio.FloatBuffer): scala.Unit
  def glProgramUniform4fv(program: scala.Int, location: scala.Int, value: java.nio.FloatBuffer): scala.Unit
  def glProgramUniformMatrix2fv(program: scala.Int, location: scala.Int, transpose: scala.Boolean, value: java.nio.FloatBuffer): scala.Unit
  def glProgramUniformMatrix3fv(program: scala.Int, location: scala.Int, transpose: scala.Boolean, value: java.nio.FloatBuffer): scala.Unit
  def glProgramUniformMatrix4fv(program: scala.Int, location: scala.Int, transpose: scala.Boolean, value: java.nio.FloatBuffer): scala.Unit
  def glProgramUniformMatrix2x3fv(program: scala.Int, location: scala.Int, transpose: scala.Boolean, value: java.nio.FloatBuffer): scala.Unit
  def glProgramUniformMatrix3x2fv(program: scala.Int, location: scala.Int, transpose: scala.Boolean, value: java.nio.FloatBuffer): scala.Unit
  def glProgramUniformMatrix2x4fv(program: scala.Int, location: scala.Int, transpose: scala.Boolean, value: java.nio.FloatBuffer): scala.Unit
  def glProgramUniformMatrix4x2fv(program: scala.Int, location: scala.Int, transpose: scala.Boolean, value: java.nio.FloatBuffer): scala.Unit
  def glProgramUniformMatrix3x4fv(program: scala.Int, location: scala.Int, transpose: scala.Boolean, value: java.nio.FloatBuffer): scala.Unit
  def glProgramUniformMatrix4x3fv(program: scala.Int, location: scala.Int, transpose: scala.Boolean, value: java.nio.FloatBuffer): scala.Unit
  def glValidateProgramPipeline(pipeline: scala.Int): scala.Unit
  def glGetProgramPipelineInfoLog(program: scala.Int): java.lang.String
  def glBindImageTexture(unit: scala.Int, texture: scala.Int, level: scala.Int, layered: scala.Boolean, layer: scala.Int, access: scala.Int, format: scala.Int): scala.Unit
  def glGetBooleani_v(target: scala.Int, index: scala.Int, data: java.nio.IntBuffer): scala.Unit
  def glMemoryBarrier(barriers: scala.Int): scala.Unit
  def glMemoryBarrierByRegion(barriers: scala.Int): scala.Unit
  def glTexStorage2DMultisample(target: scala.Int, samples: scala.Int, internalformat: scala.Int, width: scala.Int, height: scala.Int, fixedsamplelocations: scala.Boolean): scala.Unit
  def glGetMultisamplefv(pname: scala.Int, index: scala.Int, `val`: java.nio.FloatBuffer): scala.Unit
  def glSampleMaski(maskNumber: scala.Int, mask: scala.Int): scala.Unit
  def glGetTexLevelParameteriv(target: scala.Int, level: scala.Int, pname: scala.Int, params: java.nio.IntBuffer): scala.Unit
  def glGetTexLevelParameterfv(target: scala.Int, level: scala.Int, pname: scala.Int, params: java.nio.FloatBuffer): scala.Unit
  def glBindVertexBuffer(bindingindex: scala.Int, buffer: scala.Int, offset: scala.Long, stride: scala.Int): scala.Unit
  def glVertexAttribFormat(attribindex: scala.Int, size: scala.Int, `type`: scala.Int, normalized: scala.Boolean, relativeoffset: scala.Int): scala.Unit
  def glVertexAttribIFormat(attribindex: scala.Int, size: scala.Int, `type`: scala.Int, relativeoffset: scala.Int): scala.Unit
  def glVertexAttribBinding(attribindex: scala.Int, bindingindex: scala.Int): scala.Unit
  def glVertexBindingDivisor(bindingindex: scala.Int, divisor: scala.Int): scala.Unit
}
object GL31 {
  export com.badlogic.gdx.graphics.GL30.{GL_ACTIVE_ATOMIC_COUNTER_BUFFERS => _, GL_ACTIVE_PROGRAM => _, GL_ACTIVE_RESOURCES => _, GL_ACTIVE_VARIABLES => _, GL_ALL_BARRIER_BITS => _, GL_ALL_SHADER_BITS => _, GL_ARRAY_SIZE => _, GL_ARRAY_STRIDE => _, GL_ATOMIC_COUNTER_BARRIER_BIT => _, GL_ATOMIC_COUNTER_BUFFER => _, GL_ATOMIC_COUNTER_BUFFER_BINDING => _, GL_ATOMIC_COUNTER_BUFFER_INDEX => _, GL_ATOMIC_COUNTER_BUFFER_SIZE => _, GL_ATOMIC_COUNTER_BUFFER_START => _, GL_BLOCK_INDEX => _, GL_BUFFER_BINDING => _, GL_BUFFER_DATA_SIZE => _, GL_BUFFER_UPDATE_BARRIER_BIT => _, GL_BUFFER_VARIABLE => _, GL_COMMAND_BARRIER_BIT => _, GL_COMPUTE_SHADER => _, GL_COMPUTE_SHADER_BIT => _, GL_COMPUTE_WORK_GROUP_SIZE => _, GL_DEPTH_STENCIL_TEXTURE_MODE => _, GL_DISPATCH_INDIRECT_BUFFER => _, GL_DISPATCH_INDIRECT_BUFFER_BINDING => _, GL_DRAW_INDIRECT_BUFFER => _, GL_DRAW_INDIRECT_BUFFER_BINDING => _, GL_ELEMENT_ARRAY_BARRIER_BIT => _, GL_FRAGMENT_SHADER_BIT => _, GL_FRAMEBUFFER_BARRIER_BIT => _, GL_FRAMEBUFFER_DEFAULT_FIXED_SAMPLE_LOCATIONS => _, GL_FRAMEBUFFER_DEFAULT_HEIGHT => _, GL_FRAMEBUFFER_DEFAULT_SAMPLES => _, GL_FRAMEBUFFER_DEFAULT_WIDTH => _, GL_IMAGE_2D => _, GL_IMAGE_2D_ARRAY => _, GL_IMAGE_3D => _, GL_IMAGE_BINDING_ACCESS => _, GL_IMAGE_BINDING_FORMAT => _, GL_IMAGE_BINDING_LAYER => _, GL_IMAGE_BINDING_LAYERED => _, GL_IMAGE_BINDING_LEVEL => _, GL_IMAGE_BINDING_NAME => _, GL_IMAGE_CUBE => _, GL_IMAGE_FORMAT_COMPATIBILITY_BY_CLASS => _, GL_IMAGE_FORMAT_COMPATIBILITY_BY_SIZE => _, GL_IMAGE_FORMAT_COMPATIBILITY_TYPE => _, GL_INT_IMAGE_2D => _, GL_INT_IMAGE_2D_ARRAY => _, GL_INT_IMAGE_3D => _, GL_INT_IMAGE_CUBE => _, GL_INT_SAMPLER_2D_MULTISAMPLE => _, GL_IS_ROW_MAJOR => _, GL_LOCATION => _, GL_MATRIX_STRIDE => _, GL_MAX_ATOMIC_COUNTER_BUFFER_BINDINGS => _, GL_MAX_ATOMIC_COUNTER_BUFFER_SIZE => _, GL_MAX_COLOR_TEXTURE_SAMPLES => _, GL_MAX_COMBINED_ATOMIC_COUNTERS => _, GL_MAX_COMBINED_ATOMIC_COUNTER_BUFFERS => _, GL_MAX_COMBINED_COMPUTE_UNIFORM_COMPONENTS => _, GL_MAX_COMBINED_IMAGE_UNIFORMS => _, GL_MAX_COMBINED_SHADER_OUTPUT_RESOURCES => _, GL_MAX_COMBINED_SHADER_STORAGE_BLOCKS => _, GL_MAX_COMPUTE_ATOMIC_COUNTERS => _, GL_MAX_COMPUTE_ATOMIC_COUNTER_BUFFERS => _, GL_MAX_COMPUTE_IMAGE_UNIFORMS => _, GL_MAX_COMPUTE_SHADER_STORAGE_BLOCKS => _, GL_MAX_COMPUTE_SHARED_MEMORY_SIZE => _, GL_MAX_COMPUTE_TEXTURE_IMAGE_UNITS => _, GL_MAX_COMPUTE_UNIFORM_BLOCKS => _, GL_MAX_COMPUTE_UNIFORM_COMPONENTS => _, GL_MAX_COMPUTE_WORK_GROUP_COUNT => _, GL_MAX_COMPUTE_WORK_GROUP_INVOCATIONS => _, GL_MAX_COMPUTE_WORK_GROUP_SIZE => _, GL_MAX_DEPTH_TEXTURE_SAMPLES => _, GL_MAX_FRAGMENT_ATOMIC_COUNTERS => _, GL_MAX_FRAGMENT_ATOMIC_COUNTER_BUFFERS => _, GL_MAX_FRAGMENT_IMAGE_UNIFORMS => _, GL_MAX_FRAGMENT_SHADER_STORAGE_BLOCKS => _, GL_MAX_FRAMEBUFFER_HEIGHT => _, GL_MAX_FRAMEBUFFER_SAMPLES => _, GL_MAX_FRAMEBUFFER_WIDTH => _, GL_MAX_IMAGE_UNITS => _, GL_MAX_INTEGER_SAMPLES => _, GL_MAX_NAME_LENGTH => _, GL_MAX_NUM_ACTIVE_VARIABLES => _, GL_MAX_PROGRAM_TEXTURE_GATHER_OFFSET => _, GL_MAX_SAMPLE_MASK_WORDS => _, GL_MAX_SHADER_STORAGE_BLOCK_SIZE => _, GL_MAX_SHADER_STORAGE_BUFFER_BINDINGS => _, GL_MAX_UNIFORM_LOCATIONS => _, GL_MAX_VERTEX_ATOMIC_COUNTERS => _, GL_MAX_VERTEX_ATOMIC_COUNTER_BUFFERS => _, GL_MAX_VERTEX_ATTRIB_BINDINGS => _, GL_MAX_VERTEX_ATTRIB_RELATIVE_OFFSET => _, GL_MAX_VERTEX_ATTRIB_STRIDE => _, GL_MAX_VERTEX_IMAGE_UNIFORMS => _, GL_MAX_VERTEX_SHADER_STORAGE_BLOCKS => _, GL_MIN_PROGRAM_TEXTURE_GATHER_OFFSET => _, GL_NAME_LENGTH => _, GL_NUM_ACTIVE_VARIABLES => _, GL_OFFSET => _, GL_PIXEL_BUFFER_BARRIER_BIT => _, GL_PROGRAM_INPUT => _, GL_PROGRAM_OUTPUT => _, GL_PROGRAM_PIPELINE_BINDING => _, GL_PROGRAM_SEPARABLE => _, GL_READ_ONLY => _, GL_READ_WRITE => _, GL_REFERENCED_BY_COMPUTE_SHADER => _, GL_REFERENCED_BY_FRAGMENT_SHADER => _, GL_REFERENCED_BY_VERTEX_SHADER => _, GL_SAMPLER_2D_MULTISAMPLE => _, GL_SAMPLE_MASK => _, GL_SAMPLE_MASK_VALUE => _, GL_SAMPLE_POSITION => _, GL_SHADER_IMAGE_ACCESS_BARRIER_BIT => _, GL_SHADER_STORAGE_BARRIER_BIT => _, GL_SHADER_STORAGE_BLOCK => _, GL_SHADER_STORAGE_BUFFER => _, GL_SHADER_STORAGE_BUFFER_BINDING => _, GL_SHADER_STORAGE_BUFFER_OFFSET_ALIGNMENT => _, GL_SHADER_STORAGE_BUFFER_SIZE => _, GL_SHADER_STORAGE_BUFFER_START => _, GL_STENCIL_INDEX => _, GL_TEXTURE_2D_MULTISAMPLE => _, GL_TEXTURE_ALPHA_SIZE => _, GL_TEXTURE_ALPHA_TYPE => _, GL_TEXTURE_BINDING_2D_MULTISAMPLE => _, GL_TEXTURE_BLUE_SIZE => _, GL_TEXTURE_BLUE_TYPE => _, GL_TEXTURE_COMPRESSED => _, GL_TEXTURE_DEPTH => _, GL_TEXTURE_DEPTH_SIZE => _, GL_TEXTURE_DEPTH_TYPE => _, GL_TEXTURE_FETCH_BARRIER_BIT => _, GL_TEXTURE_FIXED_SAMPLE_LOCATIONS => _, GL_TEXTURE_GREEN_SIZE => _, GL_TEXTURE_GREEN_TYPE => _, GL_TEXTURE_HEIGHT => _, GL_TEXTURE_INTERNAL_FORMAT => _, GL_TEXTURE_RED_SIZE => _, GL_TEXTURE_RED_TYPE => _, GL_TEXTURE_SAMPLES => _, GL_TEXTURE_SHARED_SIZE => _, GL_TEXTURE_STENCIL_SIZE => _, GL_TEXTURE_UPDATE_BARRIER_BIT => _, GL_TEXTURE_WIDTH => _, GL_TOP_LEVEL_ARRAY_SIZE => _, GL_TOP_LEVEL_ARRAY_STRIDE => _, GL_TRANSFORM_FEEDBACK_BARRIER_BIT => _, GL_TRANSFORM_FEEDBACK_VARYING => _, GL_TYPE => _, GL_UNIFORM => _, GL_UNIFORM_BARRIER_BIT => _, GL_UNIFORM_BLOCK => _, GL_UNSIGNED_INT_ATOMIC_COUNTER => _, GL_UNSIGNED_INT_IMAGE_2D => _, GL_UNSIGNED_INT_IMAGE_2D_ARRAY => _, GL_UNSIGNED_INT_IMAGE_3D => _, GL_UNSIGNED_INT_IMAGE_CUBE => _, GL_UNSIGNED_INT_SAMPLER_2D_MULTISAMPLE => _, GL_VERTEX_ATTRIB_ARRAY_BARRIER_BIT => _, GL_VERTEX_ATTRIB_BINDING => _, GL_VERTEX_ATTRIB_RELATIVE_OFFSET => _, GL_VERTEX_BINDING_BUFFER => _, GL_VERTEX_BINDING_DIVISOR => _, GL_VERTEX_BINDING_OFFSET => _, GL_VERTEX_BINDING_STRIDE => _, GL_VERTEX_SHADER_BIT => _, GL_WRITE_ONLY => _, *}
  final val GL_VERTEX_SHADER_BIT: scala.Int = 1
  final val GL_FRAGMENT_SHADER_BIT: scala.Int = 2
  final val GL_COMPUTE_SHADER_BIT: scala.Int = 32
  final val GL_ALL_SHADER_BITS: scala.Int = -1
  final val GL_VERTEX_ATTRIB_ARRAY_BARRIER_BIT: scala.Int = 1
  final val GL_ELEMENT_ARRAY_BARRIER_BIT: scala.Int = 2
  final val GL_UNIFORM_BARRIER_BIT: scala.Int = 4
  final val GL_TEXTURE_FETCH_BARRIER_BIT: scala.Int = 8
  final val GL_SHADER_IMAGE_ACCESS_BARRIER_BIT: scala.Int = 32
  final val GL_COMMAND_BARRIER_BIT: scala.Int = 64
  final val GL_PIXEL_BUFFER_BARRIER_BIT: scala.Int = 128
  final val GL_TEXTURE_UPDATE_BARRIER_BIT: scala.Int = 256
  final val GL_BUFFER_UPDATE_BARRIER_BIT: scala.Int = 512
  final val GL_FRAMEBUFFER_BARRIER_BIT: scala.Int = 1024
  final val GL_TRANSFORM_FEEDBACK_BARRIER_BIT: scala.Int = 2048
  final val GL_ATOMIC_COUNTER_BARRIER_BIT: scala.Int = 4096
  final val GL_SHADER_STORAGE_BARRIER_BIT: scala.Int = 8192
  final val GL_ALL_BARRIER_BITS: scala.Int = -1
  final val GL_TEXTURE_WIDTH: scala.Int = 4096
  final val GL_TEXTURE_HEIGHT: scala.Int = 4097
  final val GL_TEXTURE_INTERNAL_FORMAT: scala.Int = 4099
  final val GL_STENCIL_INDEX: scala.Int = 6401
  final val GL_TEXTURE_RED_SIZE: scala.Int = 32860
  final val GL_TEXTURE_GREEN_SIZE: scala.Int = 32861
  final val GL_TEXTURE_BLUE_SIZE: scala.Int = 32862
  final val GL_TEXTURE_ALPHA_SIZE: scala.Int = 32863
  final val GL_TEXTURE_DEPTH: scala.Int = 32881
  final val GL_PROGRAM_SEPARABLE: scala.Int = 33368
  final val GL_ACTIVE_PROGRAM: scala.Int = 33369
  final val GL_PROGRAM_PIPELINE_BINDING: scala.Int = 33370
  final val GL_MAX_COMPUTE_SHARED_MEMORY_SIZE: scala.Int = 33378
  final val GL_MAX_COMPUTE_UNIFORM_COMPONENTS: scala.Int = 33379
  final val GL_MAX_COMPUTE_ATOMIC_COUNTER_BUFFERS: scala.Int = 33380
  final val GL_MAX_COMPUTE_ATOMIC_COUNTERS: scala.Int = 33381
  final val GL_MAX_COMBINED_COMPUTE_UNIFORM_COMPONENTS: scala.Int = 33382
  final val GL_COMPUTE_WORK_GROUP_SIZE: scala.Int = 33383
  final val GL_MAX_UNIFORM_LOCATIONS: scala.Int = 33390
  final val GL_VERTEX_ATTRIB_BINDING: scala.Int = 33492
  final val GL_VERTEX_ATTRIB_RELATIVE_OFFSET: scala.Int = 33493
  final val GL_VERTEX_BINDING_DIVISOR: scala.Int = 33494
  final val GL_VERTEX_BINDING_OFFSET: scala.Int = 33495
  final val GL_VERTEX_BINDING_STRIDE: scala.Int = 33496
  final val GL_MAX_VERTEX_ATTRIB_RELATIVE_OFFSET: scala.Int = 33497
  final val GL_MAX_VERTEX_ATTRIB_BINDINGS: scala.Int = 33498
  final val GL_MAX_VERTEX_ATTRIB_STRIDE: scala.Int = 33509
  final val GL_TEXTURE_COMPRESSED: scala.Int = 34465
  final val GL_TEXTURE_DEPTH_SIZE: scala.Int = 34890
  final val GL_READ_ONLY: scala.Int = 35000
  final val GL_WRITE_ONLY: scala.Int = 35001
  final val GL_READ_WRITE: scala.Int = 35002
  final val GL_TEXTURE_STENCIL_SIZE: scala.Int = 35057
  final val GL_TEXTURE_RED_TYPE: scala.Int = 35856
  final val GL_TEXTURE_GREEN_TYPE: scala.Int = 35857
  final val GL_TEXTURE_BLUE_TYPE: scala.Int = 35858
  final val GL_TEXTURE_ALPHA_TYPE: scala.Int = 35859
  final val GL_TEXTURE_DEPTH_TYPE: scala.Int = 35862
  final val GL_TEXTURE_SHARED_SIZE: scala.Int = 35903
  final val GL_SAMPLE_POSITION: scala.Int = 36432
  final val GL_SAMPLE_MASK: scala.Int = 36433
  final val GL_SAMPLE_MASK_VALUE: scala.Int = 36434
  final val GL_MAX_SAMPLE_MASK_WORDS: scala.Int = 36441
  final val GL_MIN_PROGRAM_TEXTURE_GATHER_OFFSET: scala.Int = 36446
  final val GL_MAX_PROGRAM_TEXTURE_GATHER_OFFSET: scala.Int = 36447
  final val GL_MAX_IMAGE_UNITS: scala.Int = 36664
  final val GL_MAX_COMBINED_SHADER_OUTPUT_RESOURCES: scala.Int = 36665
  final val GL_IMAGE_BINDING_NAME: scala.Int = 36666
  final val GL_IMAGE_BINDING_LEVEL: scala.Int = 36667
  final val GL_IMAGE_BINDING_LAYERED: scala.Int = 36668
  final val GL_IMAGE_BINDING_LAYER: scala.Int = 36669
  final val GL_IMAGE_BINDING_ACCESS: scala.Int = 36670
  final val GL_DRAW_INDIRECT_BUFFER: scala.Int = 36671
  final val GL_DRAW_INDIRECT_BUFFER_BINDING: scala.Int = 36675
  final val GL_VERTEX_BINDING_BUFFER: scala.Int = 36687
  final val GL_IMAGE_2D: scala.Int = 36941
  final val GL_IMAGE_3D: scala.Int = 36942
  final val GL_IMAGE_CUBE: scala.Int = 36944
  final val GL_IMAGE_2D_ARRAY: scala.Int = 36947
  final val GL_INT_IMAGE_2D: scala.Int = 36952
  final val GL_INT_IMAGE_3D: scala.Int = 36953
  final val GL_INT_IMAGE_CUBE: scala.Int = 36955
  final val GL_INT_IMAGE_2D_ARRAY: scala.Int = 36958
  final val GL_UNSIGNED_INT_IMAGE_2D: scala.Int = 36963
  final val GL_UNSIGNED_INT_IMAGE_3D: scala.Int = 36964
  final val GL_UNSIGNED_INT_IMAGE_CUBE: scala.Int = 36966
  final val GL_UNSIGNED_INT_IMAGE_2D_ARRAY: scala.Int = 36969
  final val GL_IMAGE_BINDING_FORMAT: scala.Int = 36974
  final val GL_IMAGE_FORMAT_COMPATIBILITY_TYPE: scala.Int = 37063
  final val GL_IMAGE_FORMAT_COMPATIBILITY_BY_SIZE: scala.Int = 37064
  final val GL_IMAGE_FORMAT_COMPATIBILITY_BY_CLASS: scala.Int = 37065
  final val GL_MAX_VERTEX_IMAGE_UNIFORMS: scala.Int = 37066
  final val GL_MAX_FRAGMENT_IMAGE_UNIFORMS: scala.Int = 37070
  final val GL_MAX_COMBINED_IMAGE_UNIFORMS: scala.Int = 37071
  final val GL_SHADER_STORAGE_BUFFER: scala.Int = 37074
  final val GL_SHADER_STORAGE_BUFFER_BINDING: scala.Int = 37075
  final val GL_SHADER_STORAGE_BUFFER_START: scala.Int = 37076
  final val GL_SHADER_STORAGE_BUFFER_SIZE: scala.Int = 37077
  final val GL_MAX_VERTEX_SHADER_STORAGE_BLOCKS: scala.Int = 37078
  final val GL_MAX_FRAGMENT_SHADER_STORAGE_BLOCKS: scala.Int = 37082
  final val GL_MAX_COMPUTE_SHADER_STORAGE_BLOCKS: scala.Int = 37083
  final val GL_MAX_COMBINED_SHADER_STORAGE_BLOCKS: scala.Int = 37084
  final val GL_MAX_SHADER_STORAGE_BUFFER_BINDINGS: scala.Int = 37085
  final val GL_MAX_SHADER_STORAGE_BLOCK_SIZE: scala.Int = 37086
  final val GL_SHADER_STORAGE_BUFFER_OFFSET_ALIGNMENT: scala.Int = 37087
  final val GL_DEPTH_STENCIL_TEXTURE_MODE: scala.Int = 37098
  final val GL_MAX_COMPUTE_WORK_GROUP_INVOCATIONS: scala.Int = 37099
  final val GL_DISPATCH_INDIRECT_BUFFER: scala.Int = 37102
  final val GL_DISPATCH_INDIRECT_BUFFER_BINDING: scala.Int = 37103
  final val GL_TEXTURE_2D_MULTISAMPLE: scala.Int = 37120
  final val GL_TEXTURE_BINDING_2D_MULTISAMPLE: scala.Int = 37124
  final val GL_TEXTURE_SAMPLES: scala.Int = 37126
  final val GL_TEXTURE_FIXED_SAMPLE_LOCATIONS: scala.Int = 37127
  final val GL_SAMPLER_2D_MULTISAMPLE: scala.Int = 37128
  final val GL_INT_SAMPLER_2D_MULTISAMPLE: scala.Int = 37129
  final val GL_UNSIGNED_INT_SAMPLER_2D_MULTISAMPLE: scala.Int = 37130
  final val GL_MAX_COLOR_TEXTURE_SAMPLES: scala.Int = 37134
  final val GL_MAX_DEPTH_TEXTURE_SAMPLES: scala.Int = 37135
  final val GL_MAX_INTEGER_SAMPLES: scala.Int = 37136
  final val GL_COMPUTE_SHADER: scala.Int = 37305
  final val GL_MAX_COMPUTE_UNIFORM_BLOCKS: scala.Int = 37307
  final val GL_MAX_COMPUTE_TEXTURE_IMAGE_UNITS: scala.Int = 37308
  final val GL_MAX_COMPUTE_IMAGE_UNIFORMS: scala.Int = 37309
  final val GL_MAX_COMPUTE_WORK_GROUP_COUNT: scala.Int = 37310
  final val GL_MAX_COMPUTE_WORK_GROUP_SIZE: scala.Int = 37311
  final val GL_ATOMIC_COUNTER_BUFFER: scala.Int = 37568
  final val GL_ATOMIC_COUNTER_BUFFER_BINDING: scala.Int = 37569
  final val GL_ATOMIC_COUNTER_BUFFER_START: scala.Int = 37570
  final val GL_ATOMIC_COUNTER_BUFFER_SIZE: scala.Int = 37571
  final val GL_MAX_VERTEX_ATOMIC_COUNTER_BUFFERS: scala.Int = 37580
  final val GL_MAX_FRAGMENT_ATOMIC_COUNTER_BUFFERS: scala.Int = 37584
  final val GL_MAX_COMBINED_ATOMIC_COUNTER_BUFFERS: scala.Int = 37585
  final val GL_MAX_VERTEX_ATOMIC_COUNTERS: scala.Int = 37586
  final val GL_MAX_FRAGMENT_ATOMIC_COUNTERS: scala.Int = 37590
  final val GL_MAX_COMBINED_ATOMIC_COUNTERS: scala.Int = 37591
  final val GL_MAX_ATOMIC_COUNTER_BUFFER_SIZE: scala.Int = 37592
  final val GL_ACTIVE_ATOMIC_COUNTER_BUFFERS: scala.Int = 37593
  final val GL_UNSIGNED_INT_ATOMIC_COUNTER: scala.Int = 37595
  final val GL_MAX_ATOMIC_COUNTER_BUFFER_BINDINGS: scala.Int = 37596
  final val GL_UNIFORM: scala.Int = 37601
  final val GL_UNIFORM_BLOCK: scala.Int = 37602
  final val GL_PROGRAM_INPUT: scala.Int = 37603
  final val GL_PROGRAM_OUTPUT: scala.Int = 37604
  final val GL_BUFFER_VARIABLE: scala.Int = 37605
  final val GL_SHADER_STORAGE_BLOCK: scala.Int = 37606
  final val GL_TRANSFORM_FEEDBACK_VARYING: scala.Int = 37620
  final val GL_ACTIVE_RESOURCES: scala.Int = 37621
  final val GL_MAX_NAME_LENGTH: scala.Int = 37622
  final val GL_MAX_NUM_ACTIVE_VARIABLES: scala.Int = 37623
  final val GL_NAME_LENGTH: scala.Int = 37625
  final val GL_TYPE: scala.Int = 37626
  final val GL_ARRAY_SIZE: scala.Int = 37627
  final val GL_OFFSET: scala.Int = 37628
  final val GL_BLOCK_INDEX: scala.Int = 37629
  final val GL_ARRAY_STRIDE: scala.Int = 37630
  final val GL_MATRIX_STRIDE: scala.Int = 37631
  final val GL_IS_ROW_MAJOR: scala.Int = 37632
  final val GL_ATOMIC_COUNTER_BUFFER_INDEX: scala.Int = 37633
  final val GL_BUFFER_BINDING: scala.Int = 37634
  final val GL_BUFFER_DATA_SIZE: scala.Int = 37635
  final val GL_NUM_ACTIVE_VARIABLES: scala.Int = 37636
  final val GL_ACTIVE_VARIABLES: scala.Int = 37637
  final val GL_REFERENCED_BY_VERTEX_SHADER: scala.Int = 37638
  final val GL_REFERENCED_BY_FRAGMENT_SHADER: scala.Int = 37642
  final val GL_REFERENCED_BY_COMPUTE_SHADER: scala.Int = 37643
  final val GL_TOP_LEVEL_ARRAY_SIZE: scala.Int = 37644
  final val GL_TOP_LEVEL_ARRAY_STRIDE: scala.Int = 37645
  final val GL_LOCATION: scala.Int = 37646
  final val GL_FRAMEBUFFER_DEFAULT_WIDTH: scala.Int = 37648
  final val GL_FRAMEBUFFER_DEFAULT_HEIGHT: scala.Int = 37649
  final val GL_FRAMEBUFFER_DEFAULT_SAMPLES: scala.Int = 37651
  final val GL_FRAMEBUFFER_DEFAULT_FIXED_SAMPLE_LOCATIONS: scala.Int = 37652
  final val GL_MAX_FRAMEBUFFER_WIDTH: scala.Int = 37653
  final val GL_MAX_FRAMEBUFFER_HEIGHT: scala.Int = 37654
  final val GL_MAX_FRAMEBUFFER_SAMPLES: scala.Int = 37656
}