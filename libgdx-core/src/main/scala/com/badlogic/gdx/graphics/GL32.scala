package com.badlogic.gdx.graphics

trait GL32 extends com.badlogic.gdx.graphics.GL31 {
  def glBlendBarrier(): scala.Unit
  def glCopyImageSubData(srcName: scala.Int, srcTarget: scala.Int, srcLevel: scala.Int, srcX: scala.Int, srcY: scala.Int, srcZ: scala.Int, dstName: scala.Int, dstTarget: scala.Int, dstLevel: scala.Int, dstX: scala.Int, dstY: scala.Int, dstZ: scala.Int, srcWidth: scala.Int, srcHeight: scala.Int, srcDepth: scala.Int): scala.Unit
  def glDebugMessageControl(source: scala.Int, `type`: scala.Int, severity: scala.Int, ids: java.nio.IntBuffer, enabled: scala.Boolean): scala.Unit
  def glDebugMessageInsert(source: scala.Int, `type`: scala.Int, id: scala.Int, severity: scala.Int, buf: java.lang.String): scala.Unit
  def glDebugMessageCallback(callback: com.badlogic.gdx.graphics.GL32.DebugProc): scala.Unit
  def glGetDebugMessageLog(count: scala.Int, sources: java.nio.IntBuffer, types: java.nio.IntBuffer, ids: java.nio.IntBuffer, severities: java.nio.IntBuffer, lengths: java.nio.IntBuffer, messageLog: java.nio.ByteBuffer): scala.Int
  def glPushDebugGroup(source: scala.Int, id: scala.Int, message: java.lang.String): scala.Unit
  def glPopDebugGroup(): scala.Unit
  def glObjectLabel(identifier: scala.Int, name: scala.Int, label: java.lang.String): scala.Unit
  def glGetObjectLabel(identifier: scala.Int, name: scala.Int): java.lang.String
  def glGetPointerv(pname: scala.Int): scala.Long
  def glEnablei(target: scala.Int, index: scala.Int): scala.Unit
  def glDisablei(target: scala.Int, index: scala.Int): scala.Unit
  def glBlendEquationi(buf: scala.Int, mode: scala.Int): scala.Unit
  def glBlendEquationSeparatei(buf: scala.Int, modeRGB: scala.Int, modeAlpha: scala.Int): scala.Unit
  def glBlendFunci(buf: scala.Int, src: scala.Int, dst: scala.Int): scala.Unit
  def glBlendFuncSeparatei(buf: scala.Int, srcRGB: scala.Int, dstRGB: scala.Int, srcAlpha: scala.Int, dstAlpha: scala.Int): scala.Unit
  def glColorMaski(index: scala.Int, r: scala.Boolean, g: scala.Boolean, b: scala.Boolean, a: scala.Boolean): scala.Unit
  def glIsEnabledi(target: scala.Int, index: scala.Int): scala.Boolean
  def glDrawElementsBaseVertex(mode: scala.Int, count: scala.Int, `type`: scala.Int, indices: java.nio.Buffer, basevertex: scala.Int): scala.Unit
  def glDrawRangeElementsBaseVertex(mode: scala.Int, start: scala.Int, `end`: scala.Int, count: scala.Int, `type`: scala.Int, indices: java.nio.Buffer, basevertex: scala.Int): scala.Unit
  def glDrawElementsInstancedBaseVertex(mode: scala.Int, count: scala.Int, `type`: scala.Int, indices: java.nio.Buffer, instanceCount: scala.Int, basevertex: scala.Int): scala.Unit
  def glDrawElementsInstancedBaseVertex(mode: scala.Int, count: scala.Int, `type`: scala.Int, indicesOffset: scala.Int, instanceCount: scala.Int, basevertex: scala.Int): scala.Unit
  def glFramebufferTexture(target: scala.Int, attachment: scala.Int, texture: scala.Int, level: scala.Int): scala.Unit
  def glGetGraphicsResetStatus(): scala.Int
  def glReadnPixels(x: scala.Int, y: scala.Int, width: scala.Int, height: scala.Int, format: scala.Int, `type`: scala.Int, bufSize: scala.Int, data: java.nio.Buffer): scala.Unit
  def glGetnUniformfv(program: scala.Int, location: scala.Int, params: java.nio.FloatBuffer): scala.Unit
  def glGetnUniformiv(program: scala.Int, location: scala.Int, params: java.nio.IntBuffer): scala.Unit
  def glGetnUniformuiv(program: scala.Int, location: scala.Int, params: java.nio.IntBuffer): scala.Unit
  def glMinSampleShading(value: scala.Float): scala.Unit
  def glPatchParameteri(pname: scala.Int, value: scala.Int): scala.Unit
  def glTexParameterIiv(target: scala.Int, pname: scala.Int, params: java.nio.IntBuffer): scala.Unit
  def glTexParameterIuiv(target: scala.Int, pname: scala.Int, params: java.nio.IntBuffer): scala.Unit
  def glGetTexParameterIiv(target: scala.Int, pname: scala.Int, params: java.nio.IntBuffer): scala.Unit
  def glGetTexParameterIuiv(target: scala.Int, pname: scala.Int, params: java.nio.IntBuffer): scala.Unit
  def glSamplerParameterIiv(sampler: scala.Int, pname: scala.Int, param: java.nio.IntBuffer): scala.Unit
  def glSamplerParameterIuiv(sampler: scala.Int, pname: scala.Int, param: java.nio.IntBuffer): scala.Unit
  def glGetSamplerParameterIiv(sampler: scala.Int, pname: scala.Int, params: java.nio.IntBuffer): scala.Unit
  def glGetSamplerParameterIuiv(sampler: scala.Int, pname: scala.Int, params: java.nio.IntBuffer): scala.Unit
  def glTexBuffer(target: scala.Int, internalformat: scala.Int, buffer: scala.Int): scala.Unit
  def glTexBufferRange(target: scala.Int, internalformat: scala.Int, buffer: scala.Int, offset: scala.Int, size: scala.Int): scala.Unit
  def glTexStorage3DMultisample(target: scala.Int, samples: scala.Int, internalformat: scala.Int, width: scala.Int, height: scala.Int, depth: scala.Int, fixedsamplelocations: scala.Boolean): scala.Unit
}
object GL32 {
  export com.badlogic.gdx.graphics.GL31.{GL_CONTEXT_FLAG_DEBUG_BIT => _, GL_CONTEXT_FLAG_ROBUST_ACCESS_BIT => _, GL_GEOMETRY_SHADER_BIT => _, GL_TESS_CONTROL_SHADER_BIT => _, GL_TESS_EVALUATION_SHADER_BIT => _, GL_QUADS => _, GL_LINES_ADJACENCY => _, GL_LINE_STRIP_ADJACENCY => _, GL_TRIANGLES_ADJACENCY => _, GL_TRIANGLE_STRIP_ADJACENCY => _, GL_PATCHES => _, GL_STACK_OVERFLOW => _, GL_STACK_UNDERFLOW => _, GL_CONTEXT_LOST => _, GL_TEXTURE_BORDER_COLOR => _, GL_VERTEX_ARRAY => _, GL_CLAMP_TO_BORDER => _, GL_CONTEXT_FLAGS => _, GL_PRIMITIVE_RESTART_FOR_PATCHES_SUPPORTED => _, GL_DEBUG_OUTPUT_SYNCHRONOUS => _, GL_DEBUG_NEXT_LOGGED_MESSAGE_LENGTH => _, GL_DEBUG_CALLBACK_FUNCTION => _, GL_DEBUG_CALLBACK_USER_PARAM => _, GL_DEBUG_SOURCE_API => _, GL_DEBUG_SOURCE_WINDOW_SYSTEM => _, GL_DEBUG_SOURCE_SHADER_COMPILER => _, GL_DEBUG_SOURCE_THIRD_PARTY => _, GL_DEBUG_SOURCE_APPLICATION => _, GL_DEBUG_SOURCE_OTHER => _, GL_DEBUG_TYPE_ERROR => _, GL_DEBUG_TYPE_DEPRECATED_BEHAVIOR => _, GL_DEBUG_TYPE_UNDEFINED_BEHAVIOR => _, GL_DEBUG_TYPE_PORTABILITY => _, GL_DEBUG_TYPE_PERFORMANCE => _, GL_DEBUG_TYPE_OTHER => _, GL_LOSE_CONTEXT_ON_RESET => _, GL_GUILTY_CONTEXT_RESET => _, GL_INNOCENT_CONTEXT_RESET => _, GL_UNKNOWN_CONTEXT_RESET => _, GL_RESET_NOTIFICATION_STRATEGY => _, GL_LAYER_PROVOKING_VERTEX => _, GL_UNDEFINED_VERTEX => _, GL_NO_RESET_NOTIFICATION => _, GL_DEBUG_TYPE_MARKER => _, GL_DEBUG_TYPE_PUSH_GROUP => _, GL_DEBUG_TYPE_POP_GROUP => _, GL_DEBUG_SEVERITY_NOTIFICATION => _, GL_MAX_DEBUG_GROUP_STACK_DEPTH => _, GL_DEBUG_GROUP_STACK_DEPTH => _, GL_BUFFER => _, GL_SHADER => _, GL_PROGRAM => _, GL_QUERY => _, GL_PROGRAM_PIPELINE => _, GL_SAMPLER => _, GL_MAX_LABEL_LENGTH => _, GL_MAX_TESS_CONTROL_INPUT_COMPONENTS => _, GL_MAX_TESS_EVALUATION_INPUT_COMPONENTS => _, GL_GEOMETRY_SHADER_INVOCATIONS => _, GL_GEOMETRY_VERTICES_OUT => _, GL_GEOMETRY_INPUT_TYPE => _, GL_GEOMETRY_OUTPUT_TYPE => _, GL_MAX_GEOMETRY_UNIFORM_BLOCKS => _, GL_MAX_COMBINED_GEOMETRY_UNIFORM_COMPONENTS => _, GL_MAX_GEOMETRY_TEXTURE_IMAGE_UNITS => _, GL_TEXTURE_BUFFER => _, GL_TEXTURE_BUFFER_BINDING => _, GL_MAX_TEXTURE_BUFFER_SIZE => _, GL_TEXTURE_BINDING_BUFFER => _, GL_TEXTURE_BUFFER_DATA_STORE_BINDING => _, GL_SAMPLE_SHADING => _, GL_MIN_SAMPLE_SHADING_VALUE => _, GL_PRIMITIVES_GENERATED => _, GL_FRAMEBUFFER_ATTACHMENT_LAYERED => _, GL_FRAMEBUFFER_INCOMPLETE_LAYER_TARGETS => _, GL_SAMPLER_BUFFER => _, GL_INT_SAMPLER_BUFFER => _, GL_UNSIGNED_INT_SAMPLER_BUFFER => _, GL_GEOMETRY_SHADER => _, GL_MAX_GEOMETRY_UNIFORM_COMPONENTS => _, GL_MAX_GEOMETRY_OUTPUT_VERTICES => _, GL_MAX_GEOMETRY_TOTAL_OUTPUT_COMPONENTS => _, GL_MAX_COMBINED_TESS_CONTROL_UNIFORM_COMPONENTS => _, GL_MAX_COMBINED_TESS_EVALUATION_UNIFORM_COMPONENTS => _, GL_FIRST_VERTEX_CONVENTION => _, GL_LAST_VERTEX_CONVENTION => _, GL_MAX_GEOMETRY_SHADER_INVOCATIONS => _, GL_MIN_FRAGMENT_INTERPOLATION_OFFSET => _, GL_MAX_FRAGMENT_INTERPOLATION_OFFSET => _, GL_FRAGMENT_INTERPOLATION_OFFSET_BITS => _, GL_PATCH_VERTICES => _, GL_TESS_CONTROL_OUTPUT_VERTICES => _, GL_TESS_GEN_MODE => _, GL_TESS_GEN_SPACING => _, GL_TESS_GEN_VERTEX_ORDER => _, GL_TESS_GEN_POINT_MODE => _, GL_ISOLINES => _, GL_FRACTIONAL_ODD => _, GL_FRACTIONAL_EVEN => _, GL_MAX_PATCH_VERTICES => _, GL_MAX_TESS_GEN_LEVEL => _, GL_MAX_TESS_CONTROL_UNIFORM_COMPONENTS => _, GL_MAX_TESS_EVALUATION_UNIFORM_COMPONENTS => _, GL_MAX_TESS_CONTROL_TEXTURE_IMAGE_UNITS => _, GL_MAX_TESS_EVALUATION_TEXTURE_IMAGE_UNITS => _, GL_MAX_TESS_CONTROL_OUTPUT_COMPONENTS => _, GL_MAX_TESS_PATCH_COMPONENTS => _, GL_MAX_TESS_CONTROL_TOTAL_OUTPUT_COMPONENTS => _, GL_MAX_TESS_EVALUATION_OUTPUT_COMPONENTS => _, GL_TESS_EVALUATION_SHADER => _, GL_TESS_CONTROL_SHADER => _, GL_MAX_TESS_CONTROL_UNIFORM_BLOCKS => _, GL_MAX_TESS_EVALUATION_UNIFORM_BLOCKS => _, GL_TEXTURE_CUBE_MAP_ARRAY => _, GL_TEXTURE_BINDING_CUBE_MAP_ARRAY => _, GL_SAMPLER_CUBE_MAP_ARRAY => _, GL_SAMPLER_CUBE_MAP_ARRAY_SHADOW => _, GL_INT_SAMPLER_CUBE_MAP_ARRAY => _, GL_UNSIGNED_INT_SAMPLER_CUBE_MAP_ARRAY => _, GL_IMAGE_BUFFER => _, GL_IMAGE_CUBE_MAP_ARRAY => _, GL_INT_IMAGE_BUFFER => _, GL_INT_IMAGE_CUBE_MAP_ARRAY => _, GL_UNSIGNED_INT_IMAGE_BUFFER => _, GL_UNSIGNED_INT_IMAGE_CUBE_MAP_ARRAY => _, GL_MAX_TESS_CONTROL_IMAGE_UNIFORMS => _, GL_MAX_TESS_EVALUATION_IMAGE_UNIFORMS => _, GL_MAX_GEOMETRY_IMAGE_UNIFORMS => _, GL_MAX_GEOMETRY_SHADER_STORAGE_BLOCKS => _, GL_MAX_TESS_CONTROL_SHADER_STORAGE_BLOCKS => _, GL_MAX_TESS_EVALUATION_SHADER_STORAGE_BLOCKS => _, GL_TEXTURE_2D_MULTISAMPLE_ARRAY => _, GL_TEXTURE_BINDING_2D_MULTISAMPLE_ARRAY => _, GL_SAMPLER_2D_MULTISAMPLE_ARRAY => _, GL_INT_SAMPLER_2D_MULTISAMPLE_ARRAY => _, GL_UNSIGNED_INT_SAMPLER_2D_MULTISAMPLE_ARRAY => _, GL_MAX_GEOMETRY_INPUT_COMPONENTS => _, GL_MAX_GEOMETRY_OUTPUT_COMPONENTS => _, GL_MAX_DEBUG_MESSAGE_LENGTH => _, GL_MAX_DEBUG_LOGGED_MESSAGES => _, GL_DEBUG_LOGGED_MESSAGES => _, GL_DEBUG_SEVERITY_HIGH => _, GL_DEBUG_SEVERITY_MEDIUM => _, GL_DEBUG_SEVERITY_LOW => _, GL_TEXTURE_BUFFER_OFFSET => _, GL_TEXTURE_BUFFER_SIZE => _, GL_TEXTURE_BUFFER_OFFSET_ALIGNMENT => _, GL_MULTIPLY => _, GL_SCREEN => _, GL_OVERLAY => _, GL_DARKEN => _, GL_LIGHTEN => _, GL_COLORDODGE => _, GL_COLORBURN => _, GL_HARDLIGHT => _, GL_SOFTLIGHT => _, GL_DIFFERENCE => _, GL_EXCLUSION => _, GL_HSL_HUE => _, GL_HSL_SATURATION => _, GL_HSL_COLOR => _, GL_HSL_LUMINOSITY => _, GL_PRIMITIVE_BOUNDING_BOX => _, GL_MAX_TESS_CONTROL_ATOMIC_COUNTER_BUFFERS => _, GL_MAX_TESS_EVALUATION_ATOMIC_COUNTER_BUFFERS => _, GL_MAX_GEOMETRY_ATOMIC_COUNTER_BUFFERS => _, GL_MAX_TESS_CONTROL_ATOMIC_COUNTERS => _, GL_MAX_TESS_EVALUATION_ATOMIC_COUNTERS => _, GL_MAX_GEOMETRY_ATOMIC_COUNTERS => _, GL_DEBUG_OUTPUT => _, GL_IS_PER_PATCH => _, GL_REFERENCED_BY_TESS_CONTROL_SHADER => _, GL_REFERENCED_BY_TESS_EVALUATION_SHADER => _, GL_REFERENCED_BY_GEOMETRY_SHADER => _, GL_FRAMEBUFFER_DEFAULT_LAYERS => _, GL_MAX_FRAMEBUFFER_LAYERS => _, GL_MULTISAMPLE_LINE_WIDTH_RANGE => _, GL_MULTISAMPLE_LINE_WIDTH_GRANULARITY => _, GL_COMPRESSED_RGBA_ASTC_4x4 => _, GL_COMPRESSED_RGBA_ASTC_5x4 => _, GL_COMPRESSED_RGBA_ASTC_5x5 => _, GL_COMPRESSED_RGBA_ASTC_6x5 => _, GL_COMPRESSED_RGBA_ASTC_6x6 => _, GL_COMPRESSED_RGBA_ASTC_8x5 => _, GL_COMPRESSED_RGBA_ASTC_8x6 => _, GL_COMPRESSED_RGBA_ASTC_8x8 => _, GL_COMPRESSED_RGBA_ASTC_10x5 => _, GL_COMPRESSED_RGBA_ASTC_10x6 => _, GL_COMPRESSED_RGBA_ASTC_10x8 => _, GL_COMPRESSED_RGBA_ASTC_10x10 => _, GL_COMPRESSED_RGBA_ASTC_12x10 => _, GL_COMPRESSED_RGBA_ASTC_12x12 => _, GL_COMPRESSED_SRGB8_ALPHA8_ASTC_4x4 => _, GL_COMPRESSED_SRGB8_ALPHA8_ASTC_5x4 => _, GL_COMPRESSED_SRGB8_ALPHA8_ASTC_5x5 => _, GL_COMPRESSED_SRGB8_ALPHA8_ASTC_6x5 => _, GL_COMPRESSED_SRGB8_ALPHA8_ASTC_6x6 => _, GL_COMPRESSED_SRGB8_ALPHA8_ASTC_8x5 => _, GL_COMPRESSED_SRGB8_ALPHA8_ASTC_8x6 => _, GL_COMPRESSED_SRGB8_ALPHA8_ASTC_8x8 => _, GL_COMPRESSED_SRGB8_ALPHA8_ASTC_10x5 => _, GL_COMPRESSED_SRGB8_ALPHA8_ASTC_10x6 => _, GL_COMPRESSED_SRGB8_ALPHA8_ASTC_10x8 => _, GL_COMPRESSED_SRGB8_ALPHA8_ASTC_10x10 => _, GL_COMPRESSED_SRGB8_ALPHA8_ASTC_12x10 => _, GL_COMPRESSED_SRGB8_ALPHA8_ASTC_12x12 => _, DebugProc => _, *}
  final val GL_CONTEXT_FLAG_DEBUG_BIT: scala.Int = 2
  final val GL_CONTEXT_FLAG_ROBUST_ACCESS_BIT: scala.Int = 4
  final val GL_GEOMETRY_SHADER_BIT: scala.Int = 4
  final val GL_TESS_CONTROL_SHADER_BIT: scala.Int = 8
  final val GL_TESS_EVALUATION_SHADER_BIT: scala.Int = 16
  final val GL_QUADS: scala.Int = 7
  final val GL_LINES_ADJACENCY: scala.Int = 10
  final val GL_LINE_STRIP_ADJACENCY: scala.Int = 11
  final val GL_TRIANGLES_ADJACENCY: scala.Int = 12
  final val GL_TRIANGLE_STRIP_ADJACENCY: scala.Int = 13
  final val GL_PATCHES: scala.Int = 14
  final val GL_STACK_OVERFLOW: scala.Int = 1283
  final val GL_STACK_UNDERFLOW: scala.Int = 1284
  final val GL_CONTEXT_LOST: scala.Int = 1287
  final val GL_TEXTURE_BORDER_COLOR: scala.Int = 4100
  final val GL_VERTEX_ARRAY: scala.Int = 32884
  final val GL_CLAMP_TO_BORDER: scala.Int = 33069
  final val GL_CONTEXT_FLAGS: scala.Int = 33310
  final val GL_PRIMITIVE_RESTART_FOR_PATCHES_SUPPORTED: scala.Int = 33313
  final val GL_DEBUG_OUTPUT_SYNCHRONOUS: scala.Int = 33346
  final val GL_DEBUG_NEXT_LOGGED_MESSAGE_LENGTH: scala.Int = 33347
  final val GL_DEBUG_CALLBACK_FUNCTION: scala.Int = 33348
  final val GL_DEBUG_CALLBACK_USER_PARAM: scala.Int = 33349
  final val GL_DEBUG_SOURCE_API: scala.Int = 33350
  final val GL_DEBUG_SOURCE_WINDOW_SYSTEM: scala.Int = 33351
  final val GL_DEBUG_SOURCE_SHADER_COMPILER: scala.Int = 33352
  final val GL_DEBUG_SOURCE_THIRD_PARTY: scala.Int = 33353
  final val GL_DEBUG_SOURCE_APPLICATION: scala.Int = 33354
  final val GL_DEBUG_SOURCE_OTHER: scala.Int = 33355
  final val GL_DEBUG_TYPE_ERROR: scala.Int = 33356
  final val GL_DEBUG_TYPE_DEPRECATED_BEHAVIOR: scala.Int = 33357
  final val GL_DEBUG_TYPE_UNDEFINED_BEHAVIOR: scala.Int = 33358
  final val GL_DEBUG_TYPE_PORTABILITY: scala.Int = 33359
  final val GL_DEBUG_TYPE_PERFORMANCE: scala.Int = 33360
  final val GL_DEBUG_TYPE_OTHER: scala.Int = 33361
  final val GL_LOSE_CONTEXT_ON_RESET: scala.Int = 33362
  final val GL_GUILTY_CONTEXT_RESET: scala.Int = 33363
  final val GL_INNOCENT_CONTEXT_RESET: scala.Int = 33364
  final val GL_UNKNOWN_CONTEXT_RESET: scala.Int = 33365
  final val GL_RESET_NOTIFICATION_STRATEGY: scala.Int = 33366
  final val GL_LAYER_PROVOKING_VERTEX: scala.Int = 33374
  final val GL_UNDEFINED_VERTEX: scala.Int = 33376
  final val GL_NO_RESET_NOTIFICATION: scala.Int = 33377
  final val GL_DEBUG_TYPE_MARKER: scala.Int = 33384
  final val GL_DEBUG_TYPE_PUSH_GROUP: scala.Int = 33385
  final val GL_DEBUG_TYPE_POP_GROUP: scala.Int = 33386
  final val GL_DEBUG_SEVERITY_NOTIFICATION: scala.Int = 33387
  final val GL_MAX_DEBUG_GROUP_STACK_DEPTH: scala.Int = 33388
  final val GL_DEBUG_GROUP_STACK_DEPTH: scala.Int = 33389
  final val GL_BUFFER: scala.Int = 33504
  final val GL_SHADER: scala.Int = 33505
  final val GL_PROGRAM: scala.Int = 33506
  final val GL_QUERY: scala.Int = 33507
  final val GL_PROGRAM_PIPELINE: scala.Int = 33508
  final val GL_SAMPLER: scala.Int = 33510
  final val GL_MAX_LABEL_LENGTH: scala.Int = 33512
  final val GL_MAX_TESS_CONTROL_INPUT_COMPONENTS: scala.Int = 34924
  final val GL_MAX_TESS_EVALUATION_INPUT_COMPONENTS: scala.Int = 34925
  final val GL_GEOMETRY_SHADER_INVOCATIONS: scala.Int = 34943
  final val GL_GEOMETRY_VERTICES_OUT: scala.Int = 35094
  final val GL_GEOMETRY_INPUT_TYPE: scala.Int = 35095
  final val GL_GEOMETRY_OUTPUT_TYPE: scala.Int = 35096
  final val GL_MAX_GEOMETRY_UNIFORM_BLOCKS: scala.Int = 35372
  final val GL_MAX_COMBINED_GEOMETRY_UNIFORM_COMPONENTS: scala.Int = 35378
  final val GL_MAX_GEOMETRY_TEXTURE_IMAGE_UNITS: scala.Int = 35881
  final val GL_TEXTURE_BUFFER: scala.Int = 35882
  final val GL_TEXTURE_BUFFER_BINDING: scala.Int = 35882
  final val GL_MAX_TEXTURE_BUFFER_SIZE: scala.Int = 35883
  final val GL_TEXTURE_BINDING_BUFFER: scala.Int = 35884
  final val GL_TEXTURE_BUFFER_DATA_STORE_BINDING: scala.Int = 35885
  final val GL_SAMPLE_SHADING: scala.Int = 35894
  final val GL_MIN_SAMPLE_SHADING_VALUE: scala.Int = 35895
  final val GL_PRIMITIVES_GENERATED: scala.Int = 35975
  final val GL_FRAMEBUFFER_ATTACHMENT_LAYERED: scala.Int = 36263
  final val GL_FRAMEBUFFER_INCOMPLETE_LAYER_TARGETS: scala.Int = 36264
  final val GL_SAMPLER_BUFFER: scala.Int = 36290
  final val GL_INT_SAMPLER_BUFFER: scala.Int = 36304
  final val GL_UNSIGNED_INT_SAMPLER_BUFFER: scala.Int = 36312
  final val GL_GEOMETRY_SHADER: scala.Int = 36313
  final val GL_MAX_GEOMETRY_UNIFORM_COMPONENTS: scala.Int = 36319
  final val GL_MAX_GEOMETRY_OUTPUT_VERTICES: scala.Int = 36320
  final val GL_MAX_GEOMETRY_TOTAL_OUTPUT_COMPONENTS: scala.Int = 36321
  final val GL_MAX_COMBINED_TESS_CONTROL_UNIFORM_COMPONENTS: scala.Int = 36382
  final val GL_MAX_COMBINED_TESS_EVALUATION_UNIFORM_COMPONENTS: scala.Int = 36383
  final val GL_FIRST_VERTEX_CONVENTION: scala.Int = 36429
  final val GL_LAST_VERTEX_CONVENTION: scala.Int = 36430
  final val GL_MAX_GEOMETRY_SHADER_INVOCATIONS: scala.Int = 36442
  final val GL_MIN_FRAGMENT_INTERPOLATION_OFFSET: scala.Int = 36443
  final val GL_MAX_FRAGMENT_INTERPOLATION_OFFSET: scala.Int = 36444
  final val GL_FRAGMENT_INTERPOLATION_OFFSET_BITS: scala.Int = 36445
  final val GL_PATCH_VERTICES: scala.Int = 36466
  final val GL_TESS_CONTROL_OUTPUT_VERTICES: scala.Int = 36469
  final val GL_TESS_GEN_MODE: scala.Int = 36470
  final val GL_TESS_GEN_SPACING: scala.Int = 36471
  final val GL_TESS_GEN_VERTEX_ORDER: scala.Int = 36472
  final val GL_TESS_GEN_POINT_MODE: scala.Int = 36473
  final val GL_ISOLINES: scala.Int = 36474
  final val GL_FRACTIONAL_ODD: scala.Int = 36475
  final val GL_FRACTIONAL_EVEN: scala.Int = 36476
  final val GL_MAX_PATCH_VERTICES: scala.Int = 36477
  final val GL_MAX_TESS_GEN_LEVEL: scala.Int = 36478
  final val GL_MAX_TESS_CONTROL_UNIFORM_COMPONENTS: scala.Int = 36479
  final val GL_MAX_TESS_EVALUATION_UNIFORM_COMPONENTS: scala.Int = 36480
  final val GL_MAX_TESS_CONTROL_TEXTURE_IMAGE_UNITS: scala.Int = 36481
  final val GL_MAX_TESS_EVALUATION_TEXTURE_IMAGE_UNITS: scala.Int = 36482
  final val GL_MAX_TESS_CONTROL_OUTPUT_COMPONENTS: scala.Int = 36483
  final val GL_MAX_TESS_PATCH_COMPONENTS: scala.Int = 36484
  final val GL_MAX_TESS_CONTROL_TOTAL_OUTPUT_COMPONENTS: scala.Int = 36485
  final val GL_MAX_TESS_EVALUATION_OUTPUT_COMPONENTS: scala.Int = 36486
  final val GL_TESS_EVALUATION_SHADER: scala.Int = 36487
  final val GL_TESS_CONTROL_SHADER: scala.Int = 36488
  final val GL_MAX_TESS_CONTROL_UNIFORM_BLOCKS: scala.Int = 36489
  final val GL_MAX_TESS_EVALUATION_UNIFORM_BLOCKS: scala.Int = 36490
  final val GL_TEXTURE_CUBE_MAP_ARRAY: scala.Int = 36873
  final val GL_TEXTURE_BINDING_CUBE_MAP_ARRAY: scala.Int = 36874
  final val GL_SAMPLER_CUBE_MAP_ARRAY: scala.Int = 36876
  final val GL_SAMPLER_CUBE_MAP_ARRAY_SHADOW: scala.Int = 36877
  final val GL_INT_SAMPLER_CUBE_MAP_ARRAY: scala.Int = 36878
  final val GL_UNSIGNED_INT_SAMPLER_CUBE_MAP_ARRAY: scala.Int = 36879
  final val GL_IMAGE_BUFFER: scala.Int = 36945
  final val GL_IMAGE_CUBE_MAP_ARRAY: scala.Int = 36948
  final val GL_INT_IMAGE_BUFFER: scala.Int = 36956
  final val GL_INT_IMAGE_CUBE_MAP_ARRAY: scala.Int = 36959
  final val GL_UNSIGNED_INT_IMAGE_BUFFER: scala.Int = 36967
  final val GL_UNSIGNED_INT_IMAGE_CUBE_MAP_ARRAY: scala.Int = 36970
  final val GL_MAX_TESS_CONTROL_IMAGE_UNIFORMS: scala.Int = 37067
  final val GL_MAX_TESS_EVALUATION_IMAGE_UNIFORMS: scala.Int = 37068
  final val GL_MAX_GEOMETRY_IMAGE_UNIFORMS: scala.Int = 37069
  final val GL_MAX_GEOMETRY_SHADER_STORAGE_BLOCKS: scala.Int = 37079
  final val GL_MAX_TESS_CONTROL_SHADER_STORAGE_BLOCKS: scala.Int = 37080
  final val GL_MAX_TESS_EVALUATION_SHADER_STORAGE_BLOCKS: scala.Int = 37081
  final val GL_TEXTURE_2D_MULTISAMPLE_ARRAY: scala.Int = 37122
  final val GL_TEXTURE_BINDING_2D_MULTISAMPLE_ARRAY: scala.Int = 37125
  final val GL_SAMPLER_2D_MULTISAMPLE_ARRAY: scala.Int = 37131
  final val GL_INT_SAMPLER_2D_MULTISAMPLE_ARRAY: scala.Int = 37132
  final val GL_UNSIGNED_INT_SAMPLER_2D_MULTISAMPLE_ARRAY: scala.Int = 37133
  final val GL_MAX_GEOMETRY_INPUT_COMPONENTS: scala.Int = 37155
  final val GL_MAX_GEOMETRY_OUTPUT_COMPONENTS: scala.Int = 37156
  final val GL_MAX_DEBUG_MESSAGE_LENGTH: scala.Int = 37187
  final val GL_MAX_DEBUG_LOGGED_MESSAGES: scala.Int = 37188
  final val GL_DEBUG_LOGGED_MESSAGES: scala.Int = 37189
  final val GL_DEBUG_SEVERITY_HIGH: scala.Int = 37190
  final val GL_DEBUG_SEVERITY_MEDIUM: scala.Int = 37191
  final val GL_DEBUG_SEVERITY_LOW: scala.Int = 37192
  final val GL_TEXTURE_BUFFER_OFFSET: scala.Int = 37277
  final val GL_TEXTURE_BUFFER_SIZE: scala.Int = 37278
  final val GL_TEXTURE_BUFFER_OFFSET_ALIGNMENT: scala.Int = 37279
  final val GL_MULTIPLY: scala.Int = 37524
  final val GL_SCREEN: scala.Int = 37525
  final val GL_OVERLAY: scala.Int = 37526
  final val GL_DARKEN: scala.Int = 37527
  final val GL_LIGHTEN: scala.Int = 37528
  final val GL_COLORDODGE: scala.Int = 37529
  final val GL_COLORBURN: scala.Int = 37530
  final val GL_HARDLIGHT: scala.Int = 37531
  final val GL_SOFTLIGHT: scala.Int = 37532
  final val GL_DIFFERENCE: scala.Int = 37534
  final val GL_EXCLUSION: scala.Int = 37536
  final val GL_HSL_HUE: scala.Int = 37549
  final val GL_HSL_SATURATION: scala.Int = 37550
  final val GL_HSL_COLOR: scala.Int = 37551
  final val GL_HSL_LUMINOSITY: scala.Int = 37552
  final val GL_PRIMITIVE_BOUNDING_BOX: scala.Int = 37566
  final val GL_MAX_TESS_CONTROL_ATOMIC_COUNTER_BUFFERS: scala.Int = 37581
  final val GL_MAX_TESS_EVALUATION_ATOMIC_COUNTER_BUFFERS: scala.Int = 37582
  final val GL_MAX_GEOMETRY_ATOMIC_COUNTER_BUFFERS: scala.Int = 37583
  final val GL_MAX_TESS_CONTROL_ATOMIC_COUNTERS: scala.Int = 37587
  final val GL_MAX_TESS_EVALUATION_ATOMIC_COUNTERS: scala.Int = 37588
  final val GL_MAX_GEOMETRY_ATOMIC_COUNTERS: scala.Int = 37589
  final val GL_DEBUG_OUTPUT: scala.Int = 37600
  final val GL_IS_PER_PATCH: scala.Int = 37607
  final val GL_REFERENCED_BY_TESS_CONTROL_SHADER: scala.Int = 37639
  final val GL_REFERENCED_BY_TESS_EVALUATION_SHADER: scala.Int = 37640
  final val GL_REFERENCED_BY_GEOMETRY_SHADER: scala.Int = 37641
  final val GL_FRAMEBUFFER_DEFAULT_LAYERS: scala.Int = 37650
  final val GL_MAX_FRAMEBUFFER_LAYERS: scala.Int = 37655
  final val GL_MULTISAMPLE_LINE_WIDTH_RANGE: scala.Int = 37761
  final val GL_MULTISAMPLE_LINE_WIDTH_GRANULARITY: scala.Int = 37762
  final val GL_COMPRESSED_RGBA_ASTC_4x4: scala.Int = 37808
  final val GL_COMPRESSED_RGBA_ASTC_5x4: scala.Int = 37809
  final val GL_COMPRESSED_RGBA_ASTC_5x5: scala.Int = 37810
  final val GL_COMPRESSED_RGBA_ASTC_6x5: scala.Int = 37811
  final val GL_COMPRESSED_RGBA_ASTC_6x6: scala.Int = 37812
  final val GL_COMPRESSED_RGBA_ASTC_8x5: scala.Int = 37813
  final val GL_COMPRESSED_RGBA_ASTC_8x6: scala.Int = 37814
  final val GL_COMPRESSED_RGBA_ASTC_8x8: scala.Int = 37815
  final val GL_COMPRESSED_RGBA_ASTC_10x5: scala.Int = 37816
  final val GL_COMPRESSED_RGBA_ASTC_10x6: scala.Int = 37817
  final val GL_COMPRESSED_RGBA_ASTC_10x8: scala.Int = 37818
  final val GL_COMPRESSED_RGBA_ASTC_10x10: scala.Int = 37819
  final val GL_COMPRESSED_RGBA_ASTC_12x10: scala.Int = 37820
  final val GL_COMPRESSED_RGBA_ASTC_12x12: scala.Int = 37821
  final val GL_COMPRESSED_SRGB8_ALPHA8_ASTC_4x4: scala.Int = 37840
  final val GL_COMPRESSED_SRGB8_ALPHA8_ASTC_5x4: scala.Int = 37841
  final val GL_COMPRESSED_SRGB8_ALPHA8_ASTC_5x5: scala.Int = 37842
  final val GL_COMPRESSED_SRGB8_ALPHA8_ASTC_6x5: scala.Int = 37843
  final val GL_COMPRESSED_SRGB8_ALPHA8_ASTC_6x6: scala.Int = 37844
  final val GL_COMPRESSED_SRGB8_ALPHA8_ASTC_8x5: scala.Int = 37845
  final val GL_COMPRESSED_SRGB8_ALPHA8_ASTC_8x6: scala.Int = 37846
  final val GL_COMPRESSED_SRGB8_ALPHA8_ASTC_8x8: scala.Int = 37847
  final val GL_COMPRESSED_SRGB8_ALPHA8_ASTC_10x5: scala.Int = 37848
  final val GL_COMPRESSED_SRGB8_ALPHA8_ASTC_10x6: scala.Int = 37849
  final val GL_COMPRESSED_SRGB8_ALPHA8_ASTC_10x8: scala.Int = 37850
  final val GL_COMPRESSED_SRGB8_ALPHA8_ASTC_10x10: scala.Int = 37851
  final val GL_COMPRESSED_SRGB8_ALPHA8_ASTC_12x10: scala.Int = 37852
  final val GL_COMPRESSED_SRGB8_ALPHA8_ASTC_12x12: scala.Int = 37853
  trait DebugProc {
    def onMessage(source: scala.Int, `type`: scala.Int, id: scala.Int, severity: scala.Int, message: java.lang.String): scala.Unit
  }
}