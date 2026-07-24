package com.badlogic.gdx.graphics

trait GL20 {
  def glActiveTexture(texture: scala.Int): scala.Unit
  def glBindTexture(target: scala.Int, texture: scala.Int): scala.Unit
  def glBlendFunc(sfactor: scala.Int, dfactor: scala.Int): scala.Unit
  def glClear(mask: scala.Int): scala.Unit
  def glClearColor(red: scala.Float, green: scala.Float, blue: scala.Float, alpha: scala.Float): scala.Unit
  def glClearDepthf(depth: scala.Float): scala.Unit
  def glClearStencil(s: scala.Int): scala.Unit
  def glColorMask(red: scala.Boolean, green: scala.Boolean, blue: scala.Boolean, alpha: scala.Boolean): scala.Unit
  def glCompressedTexImage2D(target: scala.Int, level: scala.Int, internalformat: scala.Int, width: scala.Int, height: scala.Int, border: scala.Int, imageSize: scala.Int, data: java.nio.Buffer): scala.Unit
  def glCompressedTexSubImage2D(target: scala.Int, level: scala.Int, xoffset: scala.Int, yoffset: scala.Int, width: scala.Int, height: scala.Int, format: scala.Int, imageSize: scala.Int, data: java.nio.Buffer): scala.Unit
  def glCopyTexImage2D(target: scala.Int, level: scala.Int, internalformat: scala.Int, x: scala.Int, y: scala.Int, width: scala.Int, height: scala.Int, border: scala.Int): scala.Unit
  def glCopyTexSubImage2D(target: scala.Int, level: scala.Int, xoffset: scala.Int, yoffset: scala.Int, x: scala.Int, y: scala.Int, width: scala.Int, height: scala.Int): scala.Unit
  def glCullFace(mode: scala.Int): scala.Unit
  def glDeleteTextures(n: scala.Int, textures: java.nio.IntBuffer): scala.Unit
  def glDeleteTexture(texture: scala.Int): scala.Unit
  def glDepthFunc(func: scala.Int): scala.Unit
  def glDepthMask(flag: scala.Boolean): scala.Unit
  def glDepthRangef(zNear: scala.Float, zFar: scala.Float): scala.Unit
  def glDisable(cap: scala.Int): scala.Unit
  def glDrawArrays(mode: scala.Int, first: scala.Int, count: scala.Int): scala.Unit
  def glDrawElements(mode: scala.Int, count: scala.Int, `type`: scala.Int, indices: java.nio.Buffer): scala.Unit
  def glEnable(cap: scala.Int): scala.Unit
  def glFinish(): scala.Unit
  def glFlush(): scala.Unit
  def glFrontFace(mode: scala.Int): scala.Unit
  def glGenTextures(n: scala.Int, textures: java.nio.IntBuffer): scala.Unit
  def glGenTexture(): scala.Int
  def glGetError(): scala.Int
  def glGetIntegerv(pname: scala.Int, params: java.nio.IntBuffer): scala.Unit
  def glGetString(name: scala.Int): java.lang.String
  def glHint(target: scala.Int, mode: scala.Int): scala.Unit
  def glLineWidth(width: scala.Float): scala.Unit
  def glPixelStorei(pname: scala.Int, param: scala.Int): scala.Unit
  def glPolygonOffset(factor: scala.Float, units: scala.Float): scala.Unit
  def glReadPixels(x: scala.Int, y: scala.Int, width: scala.Int, height: scala.Int, format: scala.Int, `type`: scala.Int, pixels: java.nio.Buffer): scala.Unit
  def glScissor(x: scala.Int, y: scala.Int, width: scala.Int, height: scala.Int): scala.Unit
  def glStencilFunc(func: scala.Int, ref: scala.Int, mask: scala.Int): scala.Unit
  def glStencilMask(mask: scala.Int): scala.Unit
  def glStencilOp(fail: scala.Int, zfail: scala.Int, zpass: scala.Int): scala.Unit
  def glTexImage2D(target: scala.Int, level: scala.Int, internalformat: scala.Int, width: scala.Int, height: scala.Int, border: scala.Int, format: scala.Int, `type`: scala.Int, pixels: java.nio.Buffer): scala.Unit
  def glTexParameterf(target: scala.Int, pname: scala.Int, param: scala.Float): scala.Unit
  def glTexSubImage2D(target: scala.Int, level: scala.Int, xoffset: scala.Int, yoffset: scala.Int, width: scala.Int, height: scala.Int, format: scala.Int, `type`: scala.Int, pixels: java.nio.Buffer): scala.Unit
  def glViewport(x: scala.Int, y: scala.Int, width: scala.Int, height: scala.Int): scala.Unit
  def glAttachShader(program: scala.Int, shader: scala.Int): scala.Unit
  def glBindAttribLocation(program: scala.Int, index: scala.Int, name: java.lang.String): scala.Unit
  def glBindBuffer(target: scala.Int, buffer: scala.Int): scala.Unit
  def glBindFramebuffer(target: scala.Int, framebuffer: scala.Int): scala.Unit
  def glBindRenderbuffer(target: scala.Int, renderbuffer: scala.Int): scala.Unit
  def glBlendColor(red: scala.Float, green: scala.Float, blue: scala.Float, alpha: scala.Float): scala.Unit
  def glBlendEquation(mode: scala.Int): scala.Unit
  def glBlendEquationSeparate(modeRGB: scala.Int, modeAlpha: scala.Int): scala.Unit
  def glBlendFuncSeparate(srcRGB: scala.Int, dstRGB: scala.Int, srcAlpha: scala.Int, dstAlpha: scala.Int): scala.Unit
  def glBufferData(target: scala.Int, size: scala.Int, data: java.nio.Buffer, usage: scala.Int): scala.Unit
  def glBufferSubData(target: scala.Int, offset: scala.Int, size: scala.Int, data: java.nio.Buffer): scala.Unit
  def glCheckFramebufferStatus(target: scala.Int): scala.Int
  def glCompileShader(shader: scala.Int): scala.Unit
  def glCreateProgram(): scala.Int
  def glCreateShader(`type`: scala.Int): scala.Int
  def glDeleteBuffer(buffer: scala.Int): scala.Unit
  def glDeleteBuffers(n: scala.Int, buffers: java.nio.IntBuffer): scala.Unit
  def glDeleteFramebuffer(framebuffer: scala.Int): scala.Unit
  def glDeleteFramebuffers(n: scala.Int, framebuffers: java.nio.IntBuffer): scala.Unit
  def glDeleteProgram(program: scala.Int): scala.Unit
  def glDeleteRenderbuffer(renderbuffer: scala.Int): scala.Unit
  def glDeleteRenderbuffers(n: scala.Int, renderbuffers: java.nio.IntBuffer): scala.Unit
  def glDeleteShader(shader: scala.Int): scala.Unit
  def glDetachShader(program: scala.Int, shader: scala.Int): scala.Unit
  def glDisableVertexAttribArray(index: scala.Int): scala.Unit
  def glDrawElements(mode: scala.Int, count: scala.Int, `type`: scala.Int, indices: scala.Int): scala.Unit
  def glEnableVertexAttribArray(index: scala.Int): scala.Unit
  def glFramebufferRenderbuffer(target: scala.Int, attachment: scala.Int, renderbuffertarget: scala.Int, renderbuffer: scala.Int): scala.Unit
  def glFramebufferTexture2D(target: scala.Int, attachment: scala.Int, textarget: scala.Int, texture: scala.Int, level: scala.Int): scala.Unit
  def glGenBuffer(): scala.Int
  def glGenBuffers(n: scala.Int, buffers: java.nio.IntBuffer): scala.Unit
  def glGenerateMipmap(target: scala.Int): scala.Unit
  def glGenFramebuffer(): scala.Int
  def glGenFramebuffers(n: scala.Int, framebuffers: java.nio.IntBuffer): scala.Unit
  def glGenRenderbuffer(): scala.Int
  def glGenRenderbuffers(n: scala.Int, renderbuffers: java.nio.IntBuffer): scala.Unit
  def glGetActiveAttrib(program: scala.Int, index: scala.Int, size: java.nio.IntBuffer, `type`: java.nio.IntBuffer): java.lang.String
  def glGetActiveUniform(program: scala.Int, index: scala.Int, size: java.nio.IntBuffer, `type`: java.nio.IntBuffer): java.lang.String
  def glGetAttachedShaders(program: scala.Int, maxcount: scala.Int, count: java.nio.Buffer, shaders: java.nio.IntBuffer): scala.Unit
  def glGetAttribLocation(program: scala.Int, name: java.lang.String): scala.Int
  def glGetBooleanv(pname: scala.Int, params: java.nio.Buffer): scala.Unit
  def glGetBufferParameteriv(target: scala.Int, pname: scala.Int, params: java.nio.IntBuffer): scala.Unit
  def glGetFloatv(pname: scala.Int, params: java.nio.FloatBuffer): scala.Unit
  def glGetFramebufferAttachmentParameteriv(target: scala.Int, attachment: scala.Int, pname: scala.Int, params: java.nio.IntBuffer): scala.Unit
  def glGetProgramiv(program: scala.Int, pname: scala.Int, params: java.nio.IntBuffer): scala.Unit
  def glGetProgramInfoLog(program: scala.Int): java.lang.String
  def glGetRenderbufferParameteriv(target: scala.Int, pname: scala.Int, params: java.nio.IntBuffer): scala.Unit
  def glGetShaderiv(shader: scala.Int, pname: scala.Int, params: java.nio.IntBuffer): scala.Unit
  def glGetShaderInfoLog(shader: scala.Int): java.lang.String
  def glGetShaderPrecisionFormat(shadertype: scala.Int, precisiontype: scala.Int, range: java.nio.IntBuffer, precision: java.nio.IntBuffer): scala.Unit
  def glGetTexParameterfv(target: scala.Int, pname: scala.Int, params: java.nio.FloatBuffer): scala.Unit
  def glGetTexParameteriv(target: scala.Int, pname: scala.Int, params: java.nio.IntBuffer): scala.Unit
  def glGetUniformfv(program: scala.Int, location: scala.Int, params: java.nio.FloatBuffer): scala.Unit
  def glGetUniformiv(program: scala.Int, location: scala.Int, params: java.nio.IntBuffer): scala.Unit
  def glGetUniformLocation(program: scala.Int, name: java.lang.String): scala.Int
  def glGetVertexAttribfv(index: scala.Int, pname: scala.Int, params: java.nio.FloatBuffer): scala.Unit
  def glGetVertexAttribiv(index: scala.Int, pname: scala.Int, params: java.nio.IntBuffer): scala.Unit
  def glGetVertexAttribPointerv(index: scala.Int, pname: scala.Int, pointer: java.nio.Buffer): scala.Unit
  def glIsBuffer(buffer: scala.Int): scala.Boolean
  def glIsEnabled(cap: scala.Int): scala.Boolean
  def glIsFramebuffer(framebuffer: scala.Int): scala.Boolean
  def glIsProgram(program: scala.Int): scala.Boolean
  def glIsRenderbuffer(renderbuffer: scala.Int): scala.Boolean
  def glIsShader(shader: scala.Int): scala.Boolean
  def glIsTexture(texture: scala.Int): scala.Boolean
  def glLinkProgram(program: scala.Int): scala.Unit
  def glReleaseShaderCompiler(): scala.Unit
  def glRenderbufferStorage(target: scala.Int, internalformat: scala.Int, width: scala.Int, height: scala.Int): scala.Unit
  def glSampleCoverage(value: scala.Float, invert: scala.Boolean): scala.Unit
  def glShaderBinary(n: scala.Int, shaders: java.nio.IntBuffer, binaryformat: scala.Int, binary: java.nio.Buffer, length: scala.Int): scala.Unit
  def glShaderSource(shader: scala.Int, string: java.lang.String): scala.Unit
  def glStencilFuncSeparate(face: scala.Int, func: scala.Int, ref: scala.Int, mask: scala.Int): scala.Unit
  def glStencilMaskSeparate(face: scala.Int, mask: scala.Int): scala.Unit
  def glStencilOpSeparate(face: scala.Int, fail: scala.Int, zfail: scala.Int, zpass: scala.Int): scala.Unit
  def glTexParameterfv(target: scala.Int, pname: scala.Int, params: java.nio.FloatBuffer): scala.Unit
  def glTexParameteri(target: scala.Int, pname: scala.Int, param: scala.Int): scala.Unit
  def glTexParameteriv(target: scala.Int, pname: scala.Int, params: java.nio.IntBuffer): scala.Unit
  def glUniform1f(location: scala.Int, x: scala.Float): scala.Unit
  def glUniform1fv(location: scala.Int, count: scala.Int, v: java.nio.FloatBuffer): scala.Unit
  def glUniform1fv(location: scala.Int, count: scala.Int, v: scala.Array[scala.Float], offset: scala.Int): scala.Unit
  def glUniform1i(location: scala.Int, x: scala.Int): scala.Unit
  def glUniform1iv(location: scala.Int, count: scala.Int, v: java.nio.IntBuffer): scala.Unit
  def glUniform1iv(location: scala.Int, count: scala.Int, v: scala.Array[scala.Int], offset: scala.Int): scala.Unit
  def glUniform2f(location: scala.Int, x: scala.Float, y: scala.Float): scala.Unit
  def glUniform2fv(location: scala.Int, count: scala.Int, v: java.nio.FloatBuffer): scala.Unit
  def glUniform2fv(location: scala.Int, count: scala.Int, v: scala.Array[scala.Float], offset: scala.Int): scala.Unit
  def glUniform2i(location: scala.Int, x: scala.Int, y: scala.Int): scala.Unit
  def glUniform2iv(location: scala.Int, count: scala.Int, v: java.nio.IntBuffer): scala.Unit
  def glUniform2iv(location: scala.Int, count: scala.Int, v: scala.Array[scala.Int], offset: scala.Int): scala.Unit
  def glUniform3f(location: scala.Int, x: scala.Float, y: scala.Float, z: scala.Float): scala.Unit
  def glUniform3fv(location: scala.Int, count: scala.Int, v: java.nio.FloatBuffer): scala.Unit
  def glUniform3fv(location: scala.Int, count: scala.Int, v: scala.Array[scala.Float], offset: scala.Int): scala.Unit
  def glUniform3i(location: scala.Int, x: scala.Int, y: scala.Int, z: scala.Int): scala.Unit
  def glUniform3iv(location: scala.Int, count: scala.Int, v: java.nio.IntBuffer): scala.Unit
  def glUniform3iv(location: scala.Int, count: scala.Int, v: scala.Array[scala.Int], offset: scala.Int): scala.Unit
  def glUniform4f(location: scala.Int, x: scala.Float, y: scala.Float, z: scala.Float, w: scala.Float): scala.Unit
  def glUniform4fv(location: scala.Int, count: scala.Int, v: java.nio.FloatBuffer): scala.Unit
  def glUniform4fv(location: scala.Int, count: scala.Int, v: scala.Array[scala.Float], offset: scala.Int): scala.Unit
  def glUniform4i(location: scala.Int, x: scala.Int, y: scala.Int, z: scala.Int, w: scala.Int): scala.Unit
  def glUniform4iv(location: scala.Int, count: scala.Int, v: java.nio.IntBuffer): scala.Unit
  def glUniform4iv(location: scala.Int, count: scala.Int, v: scala.Array[scala.Int], offset: scala.Int): scala.Unit
  def glUniformMatrix2fv(location: scala.Int, count: scala.Int, transpose: scala.Boolean, value: java.nio.FloatBuffer): scala.Unit
  def glUniformMatrix2fv(location: scala.Int, count: scala.Int, transpose: scala.Boolean, value: scala.Array[scala.Float], offset: scala.Int): scala.Unit
  def glUniformMatrix3fv(location: scala.Int, count: scala.Int, transpose: scala.Boolean, value: java.nio.FloatBuffer): scala.Unit
  def glUniformMatrix3fv(location: scala.Int, count: scala.Int, transpose: scala.Boolean, value: scala.Array[scala.Float], offset: scala.Int): scala.Unit
  def glUniformMatrix4fv(location: scala.Int, count: scala.Int, transpose: scala.Boolean, value: java.nio.FloatBuffer): scala.Unit
  def glUniformMatrix4fv(location: scala.Int, count: scala.Int, transpose: scala.Boolean, value: scala.Array[scala.Float], offset: scala.Int): scala.Unit
  def glUseProgram(program: scala.Int): scala.Unit
  def glValidateProgram(program: scala.Int): scala.Unit
  def glVertexAttrib1f(indx: scala.Int, x: scala.Float): scala.Unit
  def glVertexAttrib1fv(indx: scala.Int, values: java.nio.FloatBuffer): scala.Unit
  def glVertexAttrib2f(indx: scala.Int, x: scala.Float, y: scala.Float): scala.Unit
  def glVertexAttrib2fv(indx: scala.Int, values: java.nio.FloatBuffer): scala.Unit
  def glVertexAttrib3f(indx: scala.Int, x: scala.Float, y: scala.Float, z: scala.Float): scala.Unit
  def glVertexAttrib3fv(indx: scala.Int, values: java.nio.FloatBuffer): scala.Unit
  def glVertexAttrib4f(indx: scala.Int, x: scala.Float, y: scala.Float, z: scala.Float, w: scala.Float): scala.Unit
  def glVertexAttrib4fv(indx: scala.Int, values: java.nio.FloatBuffer): scala.Unit
  def glVertexAttribPointer(indx: scala.Int, size: scala.Int, `type`: scala.Int, normalized: scala.Boolean, stride: scala.Int, ptr: java.nio.Buffer): scala.Unit
  def glVertexAttribPointer(indx: scala.Int, size: scala.Int, `type`: scala.Int, normalized: scala.Boolean, stride: scala.Int, ptr: scala.Int): scala.Unit
}
object GL20 {
  final val GL_ES_VERSION_2_0: scala.Int = 1
  final val GL_DEPTH_BUFFER_BIT: scala.Int = 256
  final val GL_STENCIL_BUFFER_BIT: scala.Int = 1024
  final val GL_COLOR_BUFFER_BIT: scala.Int = 16384
  final val GL_FALSE: scala.Int = 0
  final val GL_TRUE: scala.Int = 1
  final val GL_POINTS: scala.Int = 0
  final val GL_LINES: scala.Int = 1
  final val GL_LINE_LOOP: scala.Int = 2
  final val GL_LINE_STRIP: scala.Int = 3
  final val GL_TRIANGLES: scala.Int = 4
  final val GL_TRIANGLE_STRIP: scala.Int = 5
  final val GL_TRIANGLE_FAN: scala.Int = 6
  final val GL_ZERO: scala.Int = 0
  final val GL_ONE: scala.Int = 1
  final val GL_SRC_COLOR: scala.Int = 768
  final val GL_ONE_MINUS_SRC_COLOR: scala.Int = 769
  final val GL_SRC_ALPHA: scala.Int = 770
  final val GL_ONE_MINUS_SRC_ALPHA: scala.Int = 771
  final val GL_DST_ALPHA: scala.Int = 772
  final val GL_ONE_MINUS_DST_ALPHA: scala.Int = 773
  final val GL_DST_COLOR: scala.Int = 774
  final val GL_ONE_MINUS_DST_COLOR: scala.Int = 775
  final val GL_SRC_ALPHA_SATURATE: scala.Int = 776
  final val GL_FUNC_ADD: scala.Int = 32774
  final val GL_BLEND_EQUATION: scala.Int = 32777
  final val GL_BLEND_EQUATION_RGB: scala.Int = 32777
  final val GL_BLEND_EQUATION_ALPHA: scala.Int = 34877
  final val GL_FUNC_SUBTRACT: scala.Int = 32778
  final val GL_FUNC_REVERSE_SUBTRACT: scala.Int = 32779
  final val GL_BLEND_DST_RGB: scala.Int = 32968
  final val GL_BLEND_SRC_RGB: scala.Int = 32969
  final val GL_BLEND_DST_ALPHA: scala.Int = 32970
  final val GL_BLEND_SRC_ALPHA: scala.Int = 32971
  final val GL_CONSTANT_COLOR: scala.Int = 32769
  final val GL_ONE_MINUS_CONSTANT_COLOR: scala.Int = 32770
  final val GL_CONSTANT_ALPHA: scala.Int = 32771
  final val GL_ONE_MINUS_CONSTANT_ALPHA: scala.Int = 32772
  final val GL_BLEND_COLOR: scala.Int = 32773
  final val GL_ARRAY_BUFFER: scala.Int = 34962
  final val GL_ELEMENT_ARRAY_BUFFER: scala.Int = 34963
  final val GL_ARRAY_BUFFER_BINDING: scala.Int = 34964
  final val GL_ELEMENT_ARRAY_BUFFER_BINDING: scala.Int = 34965
  final val GL_STREAM_DRAW: scala.Int = 35040
  final val GL_STATIC_DRAW: scala.Int = 35044
  final val GL_DYNAMIC_DRAW: scala.Int = 35048
  final val GL_BUFFER_SIZE: scala.Int = 34660
  final val GL_BUFFER_USAGE: scala.Int = 34661
  final val GL_CURRENT_VERTEX_ATTRIB: scala.Int = 34342
  final val GL_FRONT: scala.Int = 1028
  final val GL_BACK: scala.Int = 1029
  final val GL_FRONT_AND_BACK: scala.Int = 1032
  final val GL_TEXTURE_2D: scala.Int = 3553
  final val GL_CULL_FACE: scala.Int = 2884
  final val GL_BLEND: scala.Int = 3042
  final val GL_DITHER: scala.Int = 3024
  final val GL_STENCIL_TEST: scala.Int = 2960
  final val GL_DEPTH_TEST: scala.Int = 2929
  final val GL_SCISSOR_TEST: scala.Int = 3089
  final val GL_POLYGON_OFFSET_FILL: scala.Int = 32823
  final val GL_SAMPLE_ALPHA_TO_COVERAGE: scala.Int = 32926
  final val GL_SAMPLE_COVERAGE: scala.Int = 32928
  final val GL_NO_ERROR: scala.Int = 0
  final val GL_INVALID_ENUM: scala.Int = 1280
  final val GL_INVALID_VALUE: scala.Int = 1281
  final val GL_INVALID_OPERATION: scala.Int = 1282
  final val GL_OUT_OF_MEMORY: scala.Int = 1285
  final val GL_CW: scala.Int = 2304
  final val GL_CCW: scala.Int = 2305
  final val GL_LINE_WIDTH: scala.Int = 2849
  final val GL_ALIASED_POINT_SIZE_RANGE: scala.Int = 33901
  final val GL_ALIASED_LINE_WIDTH_RANGE: scala.Int = 33902
  final val GL_CULL_FACE_MODE: scala.Int = 2885
  final val GL_FRONT_FACE: scala.Int = 2886
  final val GL_DEPTH_RANGE: scala.Int = 2928
  final val GL_DEPTH_WRITEMASK: scala.Int = 2930
  final val GL_DEPTH_CLEAR_VALUE: scala.Int = 2931
  final val GL_DEPTH_FUNC: scala.Int = 2932
  final val GL_STENCIL_CLEAR_VALUE: scala.Int = 2961
  final val GL_STENCIL_FUNC: scala.Int = 2962
  final val GL_STENCIL_FAIL: scala.Int = 2964
  final val GL_STENCIL_PASS_DEPTH_FAIL: scala.Int = 2965
  final val GL_STENCIL_PASS_DEPTH_PASS: scala.Int = 2966
  final val GL_STENCIL_REF: scala.Int = 2967
  final val GL_STENCIL_VALUE_MASK: scala.Int = 2963
  final val GL_STENCIL_WRITEMASK: scala.Int = 2968
  final val GL_STENCIL_BACK_FUNC: scala.Int = 34816
  final val GL_STENCIL_BACK_FAIL: scala.Int = 34817
  final val GL_STENCIL_BACK_PASS_DEPTH_FAIL: scala.Int = 34818
  final val GL_STENCIL_BACK_PASS_DEPTH_PASS: scala.Int = 34819
  final val GL_STENCIL_BACK_REF: scala.Int = 36003
  final val GL_STENCIL_BACK_VALUE_MASK: scala.Int = 36004
  final val GL_STENCIL_BACK_WRITEMASK: scala.Int = 36005
  final val GL_VIEWPORT: scala.Int = 2978
  final val GL_SCISSOR_BOX: scala.Int = 3088
  final val GL_COLOR_CLEAR_VALUE: scala.Int = 3106
  final val GL_COLOR_WRITEMASK: scala.Int = 3107
  final val GL_UNPACK_ALIGNMENT: scala.Int = 3317
  final val GL_PACK_ALIGNMENT: scala.Int = 3333
  final val GL_MAX_TEXTURE_SIZE: scala.Int = 3379
  final val GL_MAX_TEXTURE_UNITS: scala.Int = 34018
  final val GL_MAX_VIEWPORT_DIMS: scala.Int = 3386
  final val GL_SUBPIXEL_BITS: scala.Int = 3408
  final val GL_RED_BITS: scala.Int = 3410
  final val GL_GREEN_BITS: scala.Int = 3411
  final val GL_BLUE_BITS: scala.Int = 3412
  final val GL_ALPHA_BITS: scala.Int = 3413
  final val GL_DEPTH_BITS: scala.Int = 3414
  final val GL_STENCIL_BITS: scala.Int = 3415
  final val GL_POLYGON_OFFSET_UNITS: scala.Int = 10752
  final val GL_POLYGON_OFFSET_FACTOR: scala.Int = 32824
  final val GL_TEXTURE_BINDING_2D: scala.Int = 32873
  final val GL_SAMPLE_BUFFERS: scala.Int = 32936
  final val GL_SAMPLES: scala.Int = 32937
  final val GL_SAMPLE_COVERAGE_VALUE: scala.Int = 32938
  final val GL_SAMPLE_COVERAGE_INVERT: scala.Int = 32939
  final val GL_NUM_COMPRESSED_TEXTURE_FORMATS: scala.Int = 34466
  final val GL_COMPRESSED_TEXTURE_FORMATS: scala.Int = 34467
  final val GL_DONT_CARE: scala.Int = 4352
  final val GL_FASTEST: scala.Int = 4353
  final val GL_NICEST: scala.Int = 4354
  final val GL_GENERATE_MIPMAP: scala.Int = 33169
  final val GL_GENERATE_MIPMAP_HINT: scala.Int = 33170
  final val GL_BYTE: scala.Int = 5120
  final val GL_UNSIGNED_BYTE: scala.Int = 5121
  final val GL_SHORT: scala.Int = 5122
  final val GL_UNSIGNED_SHORT: scala.Int = 5123
  final val GL_INT: scala.Int = 5124
  final val GL_UNSIGNED_INT: scala.Int = 5125
  final val GL_FLOAT: scala.Int = 5126
  final val GL_FIXED: scala.Int = 5132
  final val GL_DEPTH_COMPONENT: scala.Int = 6402
  final val GL_ALPHA: scala.Int = 6406
  final val GL_RGB: scala.Int = 6407
  final val GL_RGBA: scala.Int = 6408
  final val GL_LUMINANCE: scala.Int = 6409
  final val GL_LUMINANCE_ALPHA: scala.Int = 6410
  final val GL_UNSIGNED_SHORT_4_4_4_4: scala.Int = 32819
  final val GL_UNSIGNED_SHORT_5_5_5_1: scala.Int = 32820
  final val GL_UNSIGNED_SHORT_5_6_5: scala.Int = 33635
  final val GL_FRAGMENT_SHADER: scala.Int = 35632
  final val GL_VERTEX_SHADER: scala.Int = 35633
  final val GL_MAX_VERTEX_ATTRIBS: scala.Int = 34921
  final val GL_MAX_VERTEX_UNIFORM_VECTORS: scala.Int = 36347
  final val GL_MAX_VARYING_VECTORS: scala.Int = 36348
  final val GL_MAX_COMBINED_TEXTURE_IMAGE_UNITS: scala.Int = 35661
  final val GL_MAX_VERTEX_TEXTURE_IMAGE_UNITS: scala.Int = 35660
  final val GL_MAX_TEXTURE_IMAGE_UNITS: scala.Int = 34930
  final val GL_MAX_FRAGMENT_UNIFORM_VECTORS: scala.Int = 36349
  final val GL_SHADER_TYPE: scala.Int = 35663
  final val GL_DELETE_STATUS: scala.Int = 35712
  final val GL_LINK_STATUS: scala.Int = 35714
  final val GL_VALIDATE_STATUS: scala.Int = 35715
  final val GL_ATTACHED_SHADERS: scala.Int = 35717
  final val GL_ACTIVE_UNIFORMS: scala.Int = 35718
  final val GL_ACTIVE_UNIFORM_MAX_LENGTH: scala.Int = 35719
  final val GL_ACTIVE_ATTRIBUTES: scala.Int = 35721
  final val GL_ACTIVE_ATTRIBUTE_MAX_LENGTH: scala.Int = 35722
  final val GL_SHADING_LANGUAGE_VERSION: scala.Int = 35724
  final val GL_CURRENT_PROGRAM: scala.Int = 35725
  final val GL_NEVER: scala.Int = 512
  final val GL_LESS: scala.Int = 513
  final val GL_EQUAL: scala.Int = 514
  final val GL_LEQUAL: scala.Int = 515
  final val GL_GREATER: scala.Int = 516
  final val GL_NOTEQUAL: scala.Int = 517
  final val GL_GEQUAL: scala.Int = 518
  final val GL_ALWAYS: scala.Int = 519
  final val GL_KEEP: scala.Int = 7680
  final val GL_REPLACE: scala.Int = 7681
  final val GL_INCR: scala.Int = 7682
  final val GL_DECR: scala.Int = 7683
  final val GL_INVERT: scala.Int = 5386
  final val GL_INCR_WRAP: scala.Int = 34055
  final val GL_DECR_WRAP: scala.Int = 34056
  final val GL_VENDOR: scala.Int = 7936
  final val GL_RENDERER: scala.Int = 7937
  final val GL_VERSION: scala.Int = 7938
  final val GL_EXTENSIONS: scala.Int = 7939
  final val GL_NEAREST: scala.Int = 9728
  final val GL_LINEAR: scala.Int = 9729
  final val GL_NEAREST_MIPMAP_NEAREST: scala.Int = 9984
  final val GL_LINEAR_MIPMAP_NEAREST: scala.Int = 9985
  final val GL_NEAREST_MIPMAP_LINEAR: scala.Int = 9986
  final val GL_LINEAR_MIPMAP_LINEAR: scala.Int = 9987
  final val GL_TEXTURE_MAG_FILTER: scala.Int = 10240
  final val GL_TEXTURE_MIN_FILTER: scala.Int = 10241
  final val GL_TEXTURE_WRAP_S: scala.Int = 10242
  final val GL_TEXTURE_WRAP_T: scala.Int = 10243
  final val GL_TEXTURE: scala.Int = 5890
  final val GL_TEXTURE_CUBE_MAP: scala.Int = 34067
  final val GL_TEXTURE_BINDING_CUBE_MAP: scala.Int = 34068
  final val GL_TEXTURE_CUBE_MAP_POSITIVE_X: scala.Int = 34069
  final val GL_TEXTURE_CUBE_MAP_NEGATIVE_X: scala.Int = 34070
  final val GL_TEXTURE_CUBE_MAP_POSITIVE_Y: scala.Int = 34071
  final val GL_TEXTURE_CUBE_MAP_NEGATIVE_Y: scala.Int = 34072
  final val GL_TEXTURE_CUBE_MAP_POSITIVE_Z: scala.Int = 34073
  final val GL_TEXTURE_CUBE_MAP_NEGATIVE_Z: scala.Int = 34074
  final val GL_MAX_CUBE_MAP_TEXTURE_SIZE: scala.Int = 34076
  final val GL_TEXTURE0: scala.Int = 33984
  final val GL_TEXTURE1: scala.Int = 33985
  final val GL_TEXTURE2: scala.Int = 33986
  final val GL_TEXTURE3: scala.Int = 33987
  final val GL_TEXTURE4: scala.Int = 33988
  final val GL_TEXTURE5: scala.Int = 33989
  final val GL_TEXTURE6: scala.Int = 33990
  final val GL_TEXTURE7: scala.Int = 33991
  final val GL_TEXTURE8: scala.Int = 33992
  final val GL_TEXTURE9: scala.Int = 33993
  final val GL_TEXTURE10: scala.Int = 33994
  final val GL_TEXTURE11: scala.Int = 33995
  final val GL_TEXTURE12: scala.Int = 33996
  final val GL_TEXTURE13: scala.Int = 33997
  final val GL_TEXTURE14: scala.Int = 33998
  final val GL_TEXTURE15: scala.Int = 33999
  final val GL_TEXTURE16: scala.Int = 34000
  final val GL_TEXTURE17: scala.Int = 34001
  final val GL_TEXTURE18: scala.Int = 34002
  final val GL_TEXTURE19: scala.Int = 34003
  final val GL_TEXTURE20: scala.Int = 34004
  final val GL_TEXTURE21: scala.Int = 34005
  final val GL_TEXTURE22: scala.Int = 34006
  final val GL_TEXTURE23: scala.Int = 34007
  final val GL_TEXTURE24: scala.Int = 34008
  final val GL_TEXTURE25: scala.Int = 34009
  final val GL_TEXTURE26: scala.Int = 34010
  final val GL_TEXTURE27: scala.Int = 34011
  final val GL_TEXTURE28: scala.Int = 34012
  final val GL_TEXTURE29: scala.Int = 34013
  final val GL_TEXTURE30: scala.Int = 34014
  final val GL_TEXTURE31: scala.Int = 34015
  final val GL_ACTIVE_TEXTURE: scala.Int = 34016
  final val GL_REPEAT: scala.Int = 10497
  final val GL_CLAMP_TO_EDGE: scala.Int = 33071
  final val GL_MIRRORED_REPEAT: scala.Int = 33648
  final val GL_FLOAT_VEC2: scala.Int = 35664
  final val GL_FLOAT_VEC3: scala.Int = 35665
  final val GL_FLOAT_VEC4: scala.Int = 35666
  final val GL_INT_VEC2: scala.Int = 35667
  final val GL_INT_VEC3: scala.Int = 35668
  final val GL_INT_VEC4: scala.Int = 35669
  final val GL_BOOL: scala.Int = 35670
  final val GL_BOOL_VEC2: scala.Int = 35671
  final val GL_BOOL_VEC3: scala.Int = 35672
  final val GL_BOOL_VEC4: scala.Int = 35673
  final val GL_FLOAT_MAT2: scala.Int = 35674
  final val GL_FLOAT_MAT3: scala.Int = 35675
  final val GL_FLOAT_MAT4: scala.Int = 35676
  final val GL_SAMPLER_2D: scala.Int = 35678
  final val GL_SAMPLER_CUBE: scala.Int = 35680
  final val GL_VERTEX_ATTRIB_ARRAY_ENABLED: scala.Int = 34338
  final val GL_VERTEX_ATTRIB_ARRAY_SIZE: scala.Int = 34339
  final val GL_VERTEX_ATTRIB_ARRAY_STRIDE: scala.Int = 34340
  final val GL_VERTEX_ATTRIB_ARRAY_TYPE: scala.Int = 34341
  final val GL_VERTEX_ATTRIB_ARRAY_NORMALIZED: scala.Int = 34922
  final val GL_VERTEX_ATTRIB_ARRAY_POINTER: scala.Int = 34373
  final val GL_VERTEX_ATTRIB_ARRAY_BUFFER_BINDING: scala.Int = 34975
  final val GL_IMPLEMENTATION_COLOR_READ_TYPE: scala.Int = 35738
  final val GL_IMPLEMENTATION_COLOR_READ_FORMAT: scala.Int = 35739
  final val GL_COMPILE_STATUS: scala.Int = 35713
  final val GL_INFO_LOG_LENGTH: scala.Int = 35716
  final val GL_SHADER_SOURCE_LENGTH: scala.Int = 35720
  final val GL_SHADER_COMPILER: scala.Int = 36346
  final val GL_SHADER_BINARY_FORMATS: scala.Int = 36344
  final val GL_NUM_SHADER_BINARY_FORMATS: scala.Int = 36345
  final val GL_LOW_FLOAT: scala.Int = 36336
  final val GL_MEDIUM_FLOAT: scala.Int = 36337
  final val GL_HIGH_FLOAT: scala.Int = 36338
  final val GL_LOW_INT: scala.Int = 36339
  final val GL_MEDIUM_INT: scala.Int = 36340
  final val GL_HIGH_INT: scala.Int = 36341
  final val GL_FRAMEBUFFER: scala.Int = 36160
  final val GL_RENDERBUFFER: scala.Int = 36161
  final val GL_RGBA4: scala.Int = 32854
  final val GL_RGB5_A1: scala.Int = 32855
  final val GL_RGB565: scala.Int = 36194
  final val GL_DEPTH_COMPONENT16: scala.Int = 33189
  final val GL_STENCIL_INDEX: scala.Int = 6401
  final val GL_STENCIL_INDEX8: scala.Int = 36168
  final val GL_RENDERBUFFER_WIDTH: scala.Int = 36162
  final val GL_RENDERBUFFER_HEIGHT: scala.Int = 36163
  final val GL_RENDERBUFFER_INTERNAL_FORMAT: scala.Int = 36164
  final val GL_RENDERBUFFER_RED_SIZE: scala.Int = 36176
  final val GL_RENDERBUFFER_GREEN_SIZE: scala.Int = 36177
  final val GL_RENDERBUFFER_BLUE_SIZE: scala.Int = 36178
  final val GL_RENDERBUFFER_ALPHA_SIZE: scala.Int = 36179
  final val GL_RENDERBUFFER_DEPTH_SIZE: scala.Int = 36180
  final val GL_RENDERBUFFER_STENCIL_SIZE: scala.Int = 36181
  final val GL_FRAMEBUFFER_ATTACHMENT_OBJECT_TYPE: scala.Int = 36048
  final val GL_FRAMEBUFFER_ATTACHMENT_OBJECT_NAME: scala.Int = 36049
  final val GL_FRAMEBUFFER_ATTACHMENT_TEXTURE_LEVEL: scala.Int = 36050
  final val GL_FRAMEBUFFER_ATTACHMENT_TEXTURE_CUBE_MAP_FACE: scala.Int = 36051
  final val GL_COLOR_ATTACHMENT0: scala.Int = 36064
  final val GL_DEPTH_ATTACHMENT: scala.Int = 36096
  final val GL_STENCIL_ATTACHMENT: scala.Int = 36128
  final val GL_NONE: scala.Int = 0
  final val GL_FRAMEBUFFER_COMPLETE: scala.Int = 36053
  final val GL_FRAMEBUFFER_INCOMPLETE_ATTACHMENT: scala.Int = 36054
  final val GL_FRAMEBUFFER_INCOMPLETE_MISSING_ATTACHMENT: scala.Int = 36055
  final val GL_FRAMEBUFFER_INCOMPLETE_DIMENSIONS: scala.Int = 36057
  final val GL_FRAMEBUFFER_UNSUPPORTED: scala.Int = 36061
  final val GL_FRAMEBUFFER_BINDING: scala.Int = 36006
  final val GL_RENDERBUFFER_BINDING: scala.Int = 36007
  final val GL_MAX_RENDERBUFFER_SIZE: scala.Int = 34024
  final val GL_INVALID_FRAMEBUFFER_OPERATION: scala.Int = 1286
  final val GL_VERTEX_PROGRAM_POINT_SIZE: scala.Int = 34370
  final val GL_COVERAGE_BUFFER_BIT_NV: scala.Int = 32768
  final val GL_TEXTURE_MAX_ANISOTROPY_EXT: scala.Int = 34046
  final val GL_MAX_TEXTURE_MAX_ANISOTROPY_EXT: scala.Int = 34047
}