package com.badlogic.gdx.graphics.glutils

class ShaderProgram extends com.badlogic.gdx.utils.Disposable {
  private var log: java.lang.String = ""
  var isCompiled$field: scala.Boolean = false
  private final val uniforms: com.badlogic.gdx.utils.ObjectIntMap[java.lang.String] = new com.badlogic.gdx.utils.ObjectIntMap[java.lang.String]()
  private final val uniformTypes: com.badlogic.gdx.utils.ObjectIntMap[java.lang.String] = new com.badlogic.gdx.utils.ObjectIntMap[java.lang.String]()
  private final val uniformSizes: com.badlogic.gdx.utils.ObjectIntMap[java.lang.String] = new com.badlogic.gdx.utils.ObjectIntMap[java.lang.String]()
  private var uniformNames: scala.Array[java.lang.String] = null.asInstanceOf[scala.Array[java.lang.String]]
  private final val attributes: com.badlogic.gdx.utils.ObjectIntMap[java.lang.String] = new com.badlogic.gdx.utils.ObjectIntMap[java.lang.String]()
  private final val attributeTypes: com.badlogic.gdx.utils.ObjectIntMap[java.lang.String] = new com.badlogic.gdx.utils.ObjectIntMap[java.lang.String]()
  private final val attributeSizes: com.badlogic.gdx.utils.ObjectIntMap[java.lang.String] = new com.badlogic.gdx.utils.ObjectIntMap[java.lang.String]()
  private var attributeNames: scala.Array[java.lang.String] = null.asInstanceOf[scala.Array[java.lang.String]]
  private var program: scala.Int = 0
  private var vertexShaderHandle: scala.Int = 0
  private var fragmentShaderHandle: scala.Int = 0
  private var matrix: java.nio.FloatBuffer = null.asInstanceOf[java.nio.FloatBuffer]
  private var vertexShaderSource: java.lang.String = null.asInstanceOf[java.lang.String]
  private var fragmentShaderSource: java.lang.String = null.asInstanceOf[java.lang.String]
  private var invalidated: scala.Boolean = false
  private var refCount: scala.Int = 0
  var params: java.nio.IntBuffer = com.badlogic.gdx.utils.BufferUtils.newIntBuffer(1)
  var `type`: java.nio.IntBuffer = com.badlogic.gdx.utils.BufferUtils.newIntBuffer(1)
  def this(vertexShader: java.lang.String, fragmentShader: java.lang.String) = {
    this()
    if (vertexShader == null) {
      throw new java.lang.IllegalArgumentException("vertex shader must not be null")
    } else ()
    if (fragmentShader == null) {
      throw new java.lang.IllegalArgumentException("fragment shader must not be null")
    } else ()
    if ((ShaderProgram.prependVertexCode != null) && (ShaderProgram.prependVertexCode.length() > 0)) {
      vertexShader = ShaderProgram.prependVertexCode + vertexShader
    } else ()
    if ((ShaderProgram.prependFragmentCode != null) && (ShaderProgram.prependFragmentCode.length() > 0)) {
      fragmentShader = ShaderProgram.prependFragmentCode + fragmentShader
    } else ()
    this.vertexShaderSource = vertexShader
    this.fragmentShaderSource = fragmentShader
    this.matrix = com.badlogic.gdx.utils.BufferUtils.newFloatBuffer(16)
    this.compileShaders(vertexShader, fragmentShader)
    if (this.isCompiled()) {
      this.fetchAttributes()
      this.fetchUniforms()
      this.addManagedShader(com.badlogic.gdx.Gdx.app, this)
    } else ()
  }
  def this(vertexShader: com.badlogic.gdx.files.FileHandle, fragmentShader: com.badlogic.gdx.files.FileHandle) = {
    this(vertexShader.readString(), fragmentShader.readString())
  }
  private def compileShaders(vertexShader: java.lang.String, fragmentShader: java.lang.String): scala.Unit = {
    this.vertexShaderHandle = this.loadShader(com.badlogic.gdx.graphics.GL20.GL_VERTEX_SHADER, vertexShader)
    this.fragmentShaderHandle = this.loadShader(com.badlogic.gdx.graphics.GL20.GL_FRAGMENT_SHADER, fragmentShader)
    if ((this.vertexShaderHandle == (-1)) || (this.fragmentShaderHandle == (-1))) {
      this.isCompiled$field = false
      return
    } else ()
    this.program = this.linkProgram(this.createProgram())
    if (this.program == (-1)) {
      this.isCompiled$field = false
      return
    } else ()
    this.isCompiled$field = true
  }
  private def loadShader(`type`: scala.Int, source: java.lang.String): scala.Int = {
    val gl: com.badlogic.gdx.graphics.GL20 = com.badlogic.gdx.Gdx.gl20
    val intbuf: java.nio.IntBuffer = com.badlogic.gdx.utils.BufferUtils.newIntBuffer(1)
    val shader: scala.Int = gl.glCreateShader(`type`)
    if (shader == 0) {
      return -1
    } else ()
    gl.glShaderSource(shader, source)
    gl.glCompileShader(shader)
    gl.glGetShaderiv(shader, com.badlogic.gdx.graphics.GL20.GL_COMPILE_STATUS, intbuf)
    val compiled: scala.Int = intbuf.get(0)
    if (compiled == 0) {
      val infoLog: java.lang.String = gl.glGetShaderInfoLog(shader)
      this.log = this.log + (if (`type` == com.badlogic.gdx.graphics.GL20.GL_VERTEX_SHADER) "Vertex shader\n" else "Fragment shader:\n")
      this.log = this.log + infoLog
      return -1
    } else ()
    return shader
  }
  def createProgram(): scala.Int = {
    val gl: com.badlogic.gdx.graphics.GL20 = com.badlogic.gdx.Gdx.gl20
    val program: scala.Int = gl.glCreateProgram()
    return if (program != 0) program else -1
  }
  private def linkProgram(program: scala.Int): scala.Int = {
    val gl: com.badlogic.gdx.graphics.GL20 = com.badlogic.gdx.Gdx.gl20
    if (program == (-1)) {
      return -1
    } else ()
    gl.glAttachShader(program, this.vertexShaderHandle)
    gl.glAttachShader(program, this.fragmentShaderHandle)
    gl.glLinkProgram(program)
    val tmp: java.nio.ByteBuffer = java.nio.ByteBuffer.allocateDirect(4)
    tmp.order(java.nio.ByteOrder.nativeOrder())
    val intbuf: java.nio.IntBuffer = tmp.asIntBuffer()
    gl.glGetProgramiv(program, com.badlogic.gdx.graphics.GL20.GL_LINK_STATUS, intbuf)
    val linked: scala.Int = intbuf.get(0)
    if (linked == 0) {
      this.log = com.badlogic.gdx.Gdx.gl20.glGetProgramInfoLog(program)
      return -1
    } else ()
    return program
  }
  def getLog(): java.lang.String = {
    if (this.isCompiled$field) {
      this.log = com.badlogic.gdx.Gdx.gl20.glGetProgramInfoLog(this.program)
      return this.log
    } else {
      return this.log
    }
  }
  def isCompiled(): scala.Boolean = {
    return this.isCompiled$field
  }
  private def fetchAttributeLocation(name: java.lang.String): scala.Int = {
    val gl: com.badlogic.gdx.graphics.GL20 = com.badlogic.gdx.Gdx.gl20
    var location: scala.Int = 0
    if ({
      location = this.attributes.get(name, -2)
      location
    } == (-2)) {
      location = gl.glGetAttribLocation(this.program, name)
      this.attributes.put(name, location)
    } else ()
    return location
  }
  private def fetchUniformLocation(name: java.lang.String): scala.Int = {
    return this.fetchUniformLocation(name, ShaderProgram.pedantic)
  }
  def fetchUniformLocation(name: java.lang.String, pedantic: scala.Boolean): scala.Int = {
    var location: scala.Int = 0
    if ({
      location = this.uniforms.get(name, -2)
      location
    } == (-2)) {
      location = com.badlogic.gdx.Gdx.gl20.glGetUniformLocation(this.program, name)
      if ((location == (-1)) && pedantic) {
        if (this.isCompiled$field) {
          throw new java.lang.IllegalArgumentException(("No uniform with name '" + name) + "' in shader")
        } else ()
        throw new java.lang.IllegalStateException("An attempted fetch uniform from uncompiled shader \n" + this.getLog())
      } else ()
      this.uniforms.put(name, location)
    } else ()
    return location
  }
  def setUniformi(name: java.lang.String, value: scala.Int): scala.Unit = {
    val gl: com.badlogic.gdx.graphics.GL20 = com.badlogic.gdx.Gdx.gl20
    this.checkManaged()
    val location: scala.Int = this.fetchUniformLocation(name)
    gl.glUniform1i(location, value)
  }
  def setUniformi(location: scala.Int, value: scala.Int): scala.Unit = {
    val gl: com.badlogic.gdx.graphics.GL20 = com.badlogic.gdx.Gdx.gl20
    this.checkManaged()
    gl.glUniform1i(location, value)
  }
  def setUniformi(name: java.lang.String, value1: scala.Int, value2: scala.Int): scala.Unit = {
    val gl: com.badlogic.gdx.graphics.GL20 = com.badlogic.gdx.Gdx.gl20
    this.checkManaged()
    val location: scala.Int = this.fetchUniformLocation(name)
    gl.glUniform2i(location, value1, value2)
  }
  def setUniformi(location: scala.Int, value1: scala.Int, value2: scala.Int): scala.Unit = {
    val gl: com.badlogic.gdx.graphics.GL20 = com.badlogic.gdx.Gdx.gl20
    this.checkManaged()
    gl.glUniform2i(location, value1, value2)
  }
  def setUniformi(name: java.lang.String, value1: scala.Int, value2: scala.Int, value3: scala.Int): scala.Unit = {
    val gl: com.badlogic.gdx.graphics.GL20 = com.badlogic.gdx.Gdx.gl20
    this.checkManaged()
    val location: scala.Int = this.fetchUniformLocation(name)
    gl.glUniform3i(location, value1, value2, value3)
  }
  def setUniformi(location: scala.Int, value1: scala.Int, value2: scala.Int, value3: scala.Int): scala.Unit = {
    val gl: com.badlogic.gdx.graphics.GL20 = com.badlogic.gdx.Gdx.gl20
    this.checkManaged()
    gl.glUniform3i(location, value1, value2, value3)
  }
  def setUniformi(name: java.lang.String, value1: scala.Int, value2: scala.Int, value3: scala.Int, value4: scala.Int): scala.Unit = {
    val gl: com.badlogic.gdx.graphics.GL20 = com.badlogic.gdx.Gdx.gl20
    this.checkManaged()
    val location: scala.Int = this.fetchUniformLocation(name)
    gl.glUniform4i(location, value1, value2, value3, value4)
  }
  def setUniformi(location: scala.Int, value1: scala.Int, value2: scala.Int, value3: scala.Int, value4: scala.Int): scala.Unit = {
    val gl: com.badlogic.gdx.graphics.GL20 = com.badlogic.gdx.Gdx.gl20
    this.checkManaged()
    gl.glUniform4i(location, value1, value2, value3, value4)
  }
  def setUniform1iv(name: java.lang.String, values: scala.Array[scala.Int], offset: scala.Int, length: scala.Int): scala.Unit = {
    val gl: com.badlogic.gdx.graphics.GL20 = com.badlogic.gdx.Gdx.gl20
    this.checkManaged()
    val location: scala.Int = this.fetchUniformLocation(name)
    gl.glUniform1iv(location, length, values, offset)
  }
  def setUniform1iv(location: scala.Int, values: scala.Array[scala.Int], offset: scala.Int, length: scala.Int): scala.Unit = {
    val gl: com.badlogic.gdx.graphics.GL20 = com.badlogic.gdx.Gdx.gl20
    this.checkManaged()
    gl.glUniform1iv(location, length, values, offset)
  }
  def setUniform2iv(name: java.lang.String, values: scala.Array[scala.Int], offset: scala.Int, length: scala.Int): scala.Unit = {
    val gl: com.badlogic.gdx.graphics.GL20 = com.badlogic.gdx.Gdx.gl20
    this.checkManaged()
    val location: scala.Int = this.fetchUniformLocation(name)
    gl.glUniform2iv(location, length / 2, values, offset)
  }
  def setUniform2iv(location: scala.Int, values: scala.Array[scala.Int], offset: scala.Int, length: scala.Int): scala.Unit = {
    val gl: com.badlogic.gdx.graphics.GL20 = com.badlogic.gdx.Gdx.gl20
    this.checkManaged()
    gl.glUniform2iv(location, length / 2, values, offset)
  }
  def setUniform3iv(name: java.lang.String, values: scala.Array[scala.Int], offset: scala.Int, length: scala.Int): scala.Unit = {
    val gl: com.badlogic.gdx.graphics.GL20 = com.badlogic.gdx.Gdx.gl20
    this.checkManaged()
    val location: scala.Int = this.fetchUniformLocation(name)
    gl.glUniform3iv(location, length / 3, values, offset)
  }
  def setUniform3iv(location: scala.Int, values: scala.Array[scala.Int], offset: scala.Int, length: scala.Int): scala.Unit = {
    val gl: com.badlogic.gdx.graphics.GL20 = com.badlogic.gdx.Gdx.gl20
    this.checkManaged()
    gl.glUniform3iv(location, length / 3, values, offset)
  }
  def setUniform4iv(name: java.lang.String, values: scala.Array[scala.Int], offset: scala.Int, length: scala.Int): scala.Unit = {
    val gl: com.badlogic.gdx.graphics.GL20 = com.badlogic.gdx.Gdx.gl20
    this.checkManaged()
    val location: scala.Int = this.fetchUniformLocation(name)
    gl.glUniform4iv(location, length / 4, values, offset)
  }
  def setUniform4iv(location: scala.Int, values: scala.Array[scala.Int], offset: scala.Int, length: scala.Int): scala.Unit = {
    val gl: com.badlogic.gdx.graphics.GL20 = com.badlogic.gdx.Gdx.gl20
    this.checkManaged()
    gl.glUniform4iv(location, length / 4, values, offset)
  }
  def setUniformf(name: java.lang.String, value: scala.Float): scala.Unit = {
    val gl: com.badlogic.gdx.graphics.GL20 = com.badlogic.gdx.Gdx.gl20
    this.checkManaged()
    val location: scala.Int = this.fetchUniformLocation(name)
    gl.glUniform1f(location, value)
  }
  def setUniformf(location: scala.Int, value: scala.Float): scala.Unit = {
    val gl: com.badlogic.gdx.graphics.GL20 = com.badlogic.gdx.Gdx.gl20
    this.checkManaged()
    gl.glUniform1f(location, value)
  }
  def setUniformf(name: java.lang.String, value1: scala.Float, value2: scala.Float): scala.Unit = {
    val gl: com.badlogic.gdx.graphics.GL20 = com.badlogic.gdx.Gdx.gl20
    this.checkManaged()
    val location: scala.Int = this.fetchUniformLocation(name)
    gl.glUniform2f(location, value1, value2)
  }
  def setUniformf(location: scala.Int, value1: scala.Float, value2: scala.Float): scala.Unit = {
    val gl: com.badlogic.gdx.graphics.GL20 = com.badlogic.gdx.Gdx.gl20
    this.checkManaged()
    gl.glUniform2f(location, value1, value2)
  }
  def setUniformf(name: java.lang.String, value1: scala.Float, value2: scala.Float, value3: scala.Float): scala.Unit = {
    val gl: com.badlogic.gdx.graphics.GL20 = com.badlogic.gdx.Gdx.gl20
    this.checkManaged()
    val location: scala.Int = this.fetchUniformLocation(name)
    gl.glUniform3f(location, value1, value2, value3)
  }
  def setUniformf(location: scala.Int, value1: scala.Float, value2: scala.Float, value3: scala.Float): scala.Unit = {
    val gl: com.badlogic.gdx.graphics.GL20 = com.badlogic.gdx.Gdx.gl20
    this.checkManaged()
    gl.glUniform3f(location, value1, value2, value3)
  }
  def setUniformf(name: java.lang.String, value1: scala.Float, value2: scala.Float, value3: scala.Float, value4: scala.Float): scala.Unit = {
    val gl: com.badlogic.gdx.graphics.GL20 = com.badlogic.gdx.Gdx.gl20
    this.checkManaged()
    val location: scala.Int = this.fetchUniformLocation(name)
    gl.glUniform4f(location, value1, value2, value3, value4)
  }
  def setUniformf(location: scala.Int, value1: scala.Float, value2: scala.Float, value3: scala.Float, value4: scala.Float): scala.Unit = {
    val gl: com.badlogic.gdx.graphics.GL20 = com.badlogic.gdx.Gdx.gl20
    this.checkManaged()
    gl.glUniform4f(location, value1, value2, value3, value4)
  }
  def setUniform1fv(name: java.lang.String, values: scala.Array[scala.Float], offset: scala.Int, length: scala.Int): scala.Unit = {
    val gl: com.badlogic.gdx.graphics.GL20 = com.badlogic.gdx.Gdx.gl20
    this.checkManaged()
    val location: scala.Int = this.fetchUniformLocation(name)
    gl.glUniform1fv(location, length, values, offset)
  }
  def setUniform1fv(location: scala.Int, values: scala.Array[scala.Float], offset: scala.Int, length: scala.Int): scala.Unit = {
    val gl: com.badlogic.gdx.graphics.GL20 = com.badlogic.gdx.Gdx.gl20
    this.checkManaged()
    gl.glUniform1fv(location, length, values, offset)
  }
  def setUniform2fv(name: java.lang.String, values: scala.Array[scala.Float], offset: scala.Int, length: scala.Int): scala.Unit = {
    val gl: com.badlogic.gdx.graphics.GL20 = com.badlogic.gdx.Gdx.gl20
    this.checkManaged()
    val location: scala.Int = this.fetchUniformLocation(name)
    gl.glUniform2fv(location, length / 2, values, offset)
  }
  def setUniform2fv(location: scala.Int, values: scala.Array[scala.Float], offset: scala.Int, length: scala.Int): scala.Unit = {
    val gl: com.badlogic.gdx.graphics.GL20 = com.badlogic.gdx.Gdx.gl20
    this.checkManaged()
    gl.glUniform2fv(location, length / 2, values, offset)
  }
  def setUniform3fv(name: java.lang.String, values: scala.Array[scala.Float], offset: scala.Int, length: scala.Int): scala.Unit = {
    val gl: com.badlogic.gdx.graphics.GL20 = com.badlogic.gdx.Gdx.gl20
    this.checkManaged()
    val location: scala.Int = this.fetchUniformLocation(name)
    gl.glUniform3fv(location, length / 3, values, offset)
  }
  def setUniform3fv(location: scala.Int, values: scala.Array[scala.Float], offset: scala.Int, length: scala.Int): scala.Unit = {
    val gl: com.badlogic.gdx.graphics.GL20 = com.badlogic.gdx.Gdx.gl20
    this.checkManaged()
    gl.glUniform3fv(location, length / 3, values, offset)
  }
  def setUniform4fv(name: java.lang.String, values: scala.Array[scala.Float], offset: scala.Int, length: scala.Int): scala.Unit = {
    val gl: com.badlogic.gdx.graphics.GL20 = com.badlogic.gdx.Gdx.gl20
    this.checkManaged()
    val location: scala.Int = this.fetchUniformLocation(name)
    gl.glUniform4fv(location, length / 4, values, offset)
  }
  def setUniform4fv(location: scala.Int, values: scala.Array[scala.Float], offset: scala.Int, length: scala.Int): scala.Unit = {
    val gl: com.badlogic.gdx.graphics.GL20 = com.badlogic.gdx.Gdx.gl20
    this.checkManaged()
    gl.glUniform4fv(location, length / 4, values, offset)
  }
  def setUniformMatrix(name: java.lang.String, matrix: com.badlogic.gdx.math.Matrix4): scala.Unit = {
    this.setUniformMatrix(name, matrix, false)
  }
  def setUniformMatrix(name: java.lang.String, matrix: com.badlogic.gdx.math.Matrix4, transpose: scala.Boolean): scala.Unit = {
    this.setUniformMatrix(this.fetchUniformLocation(name), matrix, transpose)
  }
  def setUniformMatrix(location: scala.Int, matrix: com.badlogic.gdx.math.Matrix4): scala.Unit = {
    this.setUniformMatrix(location, matrix, false)
  }
  def setUniformMatrix(location: scala.Int, matrix: com.badlogic.gdx.math.Matrix4, transpose: scala.Boolean): scala.Unit = {
    val gl: com.badlogic.gdx.graphics.GL20 = com.badlogic.gdx.Gdx.gl20
    this.checkManaged()
    gl.glUniformMatrix4fv(location, 1, transpose, matrix.`val`, 0)
  }
  def setUniformMatrix(name: java.lang.String, matrix: com.badlogic.gdx.math.Matrix3): scala.Unit = {
    this.setUniformMatrix(name, matrix, false)
  }
  def setUniformMatrix(name: java.lang.String, matrix: com.badlogic.gdx.math.Matrix3, transpose: scala.Boolean): scala.Unit = {
    this.setUniformMatrix(this.fetchUniformLocation(name), matrix, transpose)
  }
  def setUniformMatrix(location: scala.Int, matrix: com.badlogic.gdx.math.Matrix3): scala.Unit = {
    this.setUniformMatrix(location, matrix, false)
  }
  def setUniformMatrix(location: scala.Int, matrix: com.badlogic.gdx.math.Matrix3, transpose: scala.Boolean): scala.Unit = {
    val gl: com.badlogic.gdx.graphics.GL20 = com.badlogic.gdx.Gdx.gl20
    this.checkManaged()
    gl.glUniformMatrix3fv(location, 1, transpose, matrix.`val`, 0)
  }
  def setUniformMatrix3fv(name: java.lang.String, buffer: java.nio.FloatBuffer, count: scala.Int, transpose: scala.Boolean): scala.Unit = {
    val gl: com.badlogic.gdx.graphics.GL20 = com.badlogic.gdx.Gdx.gl20
    this.checkManaged()
    buffer.asInstanceOf[java.nio.Buffer].position(0)
    val location: scala.Int = this.fetchUniformLocation(name)
    gl.glUniformMatrix3fv(location, count, transpose, buffer)
  }
  def setUniformMatrix4fv(name: java.lang.String, buffer: java.nio.FloatBuffer, count: scala.Int, transpose: scala.Boolean): scala.Unit = {
    val gl: com.badlogic.gdx.graphics.GL20 = com.badlogic.gdx.Gdx.gl20
    this.checkManaged()
    buffer.asInstanceOf[java.nio.Buffer].position(0)
    val location: scala.Int = this.fetchUniformLocation(name)
    gl.glUniformMatrix4fv(location, count, transpose, buffer)
  }
  def setUniformMatrix4fv(location: scala.Int, values: scala.Array[scala.Float], offset: scala.Int, length: scala.Int): scala.Unit = {
    val gl: com.badlogic.gdx.graphics.GL20 = com.badlogic.gdx.Gdx.gl20
    this.checkManaged()
    gl.glUniformMatrix4fv(location, length / 16, false, values, offset)
  }
  def setUniformMatrix4fv(name: java.lang.String, values: scala.Array[scala.Float], offset: scala.Int, length: scala.Int): scala.Unit = {
    this.setUniformMatrix4fv(this.fetchUniformLocation(name), values, offset, length)
  }
  def setUniformf(name: java.lang.String, values: com.badlogic.gdx.math.Vector2): scala.Unit = {
    this.setUniformf(name, values.x, values.y)
  }
  def setUniformf(location: scala.Int, values: com.badlogic.gdx.math.Vector2): scala.Unit = {
    this.setUniformf(location, values.x, values.y)
  }
  def setUniformf(name: java.lang.String, values: com.badlogic.gdx.math.Vector3): scala.Unit = {
    this.setUniformf(name, values.x, values.y, values.z)
  }
  def setUniformf(location: scala.Int, values: com.badlogic.gdx.math.Vector3): scala.Unit = {
    this.setUniformf(location, values.x, values.y, values.z)
  }
  def setUniformf(name: java.lang.String, values: com.badlogic.gdx.math.Vector4): scala.Unit = {
    this.setUniformf(name, values.x, values.y, values.z, values.w)
  }
  def setUniformf(location: scala.Int, values: com.badlogic.gdx.math.Vector4): scala.Unit = {
    this.setUniformf(location, values.x, values.y, values.z, values.w)
  }
  def setUniformf(name: java.lang.String, values: com.badlogic.gdx.graphics.Color): scala.Unit = {
    this.setUniformf(name, values.r, values.g, values.b, values.a)
  }
  def setUniformf(location: scala.Int, values: com.badlogic.gdx.graphics.Color): scala.Unit = {
    this.setUniformf(location, values.r, values.g, values.b, values.a)
  }
  def setVertexAttribute(name: java.lang.String, size: scala.Int, `type`: scala.Int, normalize: scala.Boolean, stride: scala.Int, buffer: java.nio.Buffer): scala.Unit = {
    val gl: com.badlogic.gdx.graphics.GL20 = com.badlogic.gdx.Gdx.gl20
    this.checkManaged()
    val location: scala.Int = this.fetchAttributeLocation(name)
    if (location == (-1)) {
      return
    } else ()
    gl.glVertexAttribPointer(location, size, `type`, normalize, stride, buffer)
  }
  def setVertexAttribute(location: scala.Int, size: scala.Int, `type`: scala.Int, normalize: scala.Boolean, stride: scala.Int, buffer: java.nio.Buffer): scala.Unit = {
    val gl: com.badlogic.gdx.graphics.GL20 = com.badlogic.gdx.Gdx.gl20
    this.checkManaged()
    gl.glVertexAttribPointer(location, size, `type`, normalize, stride, buffer)
  }
  def setVertexAttribute(name: java.lang.String, size: scala.Int, `type`: scala.Int, normalize: scala.Boolean, stride: scala.Int, offset: scala.Int): scala.Unit = {
    val gl: com.badlogic.gdx.graphics.GL20 = com.badlogic.gdx.Gdx.gl20
    this.checkManaged()
    val location: scala.Int = this.fetchAttributeLocation(name)
    if (location == (-1)) {
      return
    } else ()
    gl.glVertexAttribPointer(location, size, `type`, normalize, stride, offset)
  }
  def setVertexAttribute(location: scala.Int, size: scala.Int, `type`: scala.Int, normalize: scala.Boolean, stride: scala.Int, offset: scala.Int): scala.Unit = {
    val gl: com.badlogic.gdx.graphics.GL20 = com.badlogic.gdx.Gdx.gl20
    this.checkManaged()
    gl.glVertexAttribPointer(location, size, `type`, normalize, stride, offset)
  }
  def begin(): scala.Unit = {
    this.bind()
  }
  def bind(): scala.Unit = {
    val gl: com.badlogic.gdx.graphics.GL20 = com.badlogic.gdx.Gdx.gl20
    this.checkManaged()
    gl.glUseProgram(this.program)
  }
  def `end`(): scala.Unit = {
    ()
  }
  def dispose(): scala.Unit = {
    val gl: com.badlogic.gdx.graphics.GL20 = com.badlogic.gdx.Gdx.gl20
    gl.glUseProgram(0)
    gl.glDeleteShader(this.vertexShaderHandle)
    gl.glDeleteShader(this.fragmentShaderHandle)
    gl.glDeleteProgram(this.program)
    if (ShaderProgram.shaders.get(com.badlogic.gdx.Gdx.app) != null) {
      ShaderProgram.shaders.get(com.badlogic.gdx.Gdx.app).removeValue(this, true)
    } else ()
  }
  def disableVertexAttribute(name: java.lang.String): scala.Unit = {
    val gl: com.badlogic.gdx.graphics.GL20 = com.badlogic.gdx.Gdx.gl20
    this.checkManaged()
    val location: scala.Int = this.fetchAttributeLocation(name)
    if (location == (-1)) {
      return
    } else ()
    gl.glDisableVertexAttribArray(location)
  }
  def disableVertexAttribute(location: scala.Int): scala.Unit = {
    val gl: com.badlogic.gdx.graphics.GL20 = com.badlogic.gdx.Gdx.gl20
    this.checkManaged()
    gl.glDisableVertexAttribArray(location)
  }
  def enableVertexAttribute(name: java.lang.String): scala.Unit = {
    val gl: com.badlogic.gdx.graphics.GL20 = com.badlogic.gdx.Gdx.gl20
    this.checkManaged()
    val location: scala.Int = this.fetchAttributeLocation(name)
    if (location == (-1)) {
      return
    } else ()
    gl.glEnableVertexAttribArray(location)
  }
  def enableVertexAttribute(location: scala.Int): scala.Unit = {
    val gl: com.badlogic.gdx.graphics.GL20 = com.badlogic.gdx.Gdx.gl20
    this.checkManaged()
    gl.glEnableVertexAttribArray(location)
  }
  private def checkManaged(): scala.Unit = {
    if (this.invalidated) {
      this.compileShaders(this.vertexShaderSource, this.fragmentShaderSource)
      this.invalidated = false
    } else ()
  }
  private def addManagedShader(app: com.badlogic.gdx.Application, shaderProgram: ShaderProgram): scala.Unit = {
    var managedResources: com.badlogic.gdx.utils.Array[ShaderProgram] = ShaderProgram.shaders.get(app)
    if (managedResources == null) {
      managedResources = new com.badlogic.gdx.utils.Array[ShaderProgram]()
    } else ()
    managedResources.add(shaderProgram)
    ShaderProgram.shaders.put(app, managedResources)
  }
  def setAttributef(name: java.lang.String, value1: scala.Float, value2: scala.Float, value3: scala.Float, value4: scala.Float): scala.Unit = {
    val gl: com.badlogic.gdx.graphics.GL20 = com.badlogic.gdx.Gdx.gl20
    val location: scala.Int = this.fetchAttributeLocation(name)
    gl.glVertexAttrib4f(location, value1, value2, value3, value4)
  }
  private def fetchUniforms(): scala.Unit = {
    this.params.asInstanceOf[java.nio.Buffer].clear()
    com.badlogic.gdx.Gdx.gl20.glGetProgramiv(this.program, com.badlogic.gdx.graphics.GL20.GL_ACTIVE_UNIFORMS, this.params)
    val numUniforms: scala.Int = this.params.get(0)
    this.uniformNames = new Array[java.lang.String](numUniforms);
    { var i: scala.Int = 0; while (i < numUniforms) { {
      this.params.asInstanceOf[java.nio.Buffer].clear()
      this.params.put(0, 1)
      this.`type`.asInstanceOf[java.nio.Buffer].clear()
      val name: java.lang.String = com.badlogic.gdx.Gdx.gl20.glGetActiveUniform(this.program, i, this.params, this.`type`)
      val location: scala.Int = com.badlogic.gdx.Gdx.gl20.glGetUniformLocation(this.program, name)
      this.uniforms.put(name, location)
      this.uniformTypes.put(name, this.`type`.get(0))
      this.uniformSizes.put(name, this.params.get(0))
      this.uniformNames(i) = name
    }; i = i + 1 } }
  }
  private def fetchAttributes(): scala.Unit = {
    this.params.asInstanceOf[java.nio.Buffer].clear()
    com.badlogic.gdx.Gdx.gl20.glGetProgramiv(this.program, com.badlogic.gdx.graphics.GL20.GL_ACTIVE_ATTRIBUTES, this.params)
    val numAttributes: scala.Int = this.params.get(0)
    this.attributeNames = new Array[java.lang.String](numAttributes);
    { var i: scala.Int = 0; while (i < numAttributes) { {
      this.params.asInstanceOf[java.nio.Buffer].clear()
      this.params.put(0, 1)
      this.`type`.asInstanceOf[java.nio.Buffer].clear()
      val name: java.lang.String = com.badlogic.gdx.Gdx.gl20.glGetActiveAttrib(this.program, i, this.params, this.`type`)
      val location: scala.Int = com.badlogic.gdx.Gdx.gl20.glGetAttribLocation(this.program, name)
      this.attributes.put(name, location)
      this.attributeTypes.put(name, this.`type`.get(0))
      this.attributeSizes.put(name, this.params.get(0))
      this.attributeNames(i) = name
    }; i = i + 1 } }
  }
  def hasAttribute(name: java.lang.String): scala.Boolean = {
    return this.attributes.containsKey(name)
  }
  def getAttributeType(name: java.lang.String): scala.Int = {
    return this.attributeTypes.get(name, 0)
  }
  def getAttributeLocation(name: java.lang.String): scala.Int = {
    return this.attributes.get(name, -1)
  }
  def getAttributeSize(name: java.lang.String): scala.Int = {
    return this.attributeSizes.get(name, 0)
  }
  def hasUniform(name: java.lang.String): scala.Boolean = {
    return this.uniforms.containsKey(name)
  }
  def getUniformType(name: java.lang.String): scala.Int = {
    return this.uniformTypes.get(name, 0)
  }
  def getUniformLocation(name: java.lang.String): scala.Int = {
    return this.uniforms.get(name, -1)
  }
  def getUniformSize(name: java.lang.String): scala.Int = {
    return this.uniformSizes.get(name, 0)
  }
  def getAttributes(): scala.Array[java.lang.String] = {
    return this.attributeNames
  }
  def getUniforms(): scala.Array[java.lang.String] = {
    return this.uniformNames
  }
  def getVertexShaderSource(): java.lang.String = {
    return this.vertexShaderSource
  }
  def getFragmentShaderSource(): java.lang.String = {
    return this.fragmentShaderSource
  }
  def getHandle(): scala.Int = {
    return this.program
  }
}
object ShaderProgram {
  final val POSITION_ATTRIBUTE: java.lang.String = "a_position"
  final val NORMAL_ATTRIBUTE: java.lang.String = "a_normal"
  final val COLOR_ATTRIBUTE: java.lang.String = "a_color"
  final val TEXCOORD_ATTRIBUTE: java.lang.String = "a_texCoord"
  final val TANGENT_ATTRIBUTE: java.lang.String = "a_tangent"
  final val BINORMAL_ATTRIBUTE: java.lang.String = "a_binormal"
  final val BONEWEIGHT_ATTRIBUTE: java.lang.String = "a_boneWeight"
  var pedantic: scala.Boolean = true
  var prependVertexCode: java.lang.String = ""
  var prependFragmentCode: java.lang.String = ""
  private final val shaders: com.badlogic.gdx.utils.ObjectMap[com.badlogic.gdx.Application, com.badlogic.gdx.utils.Array[ShaderProgram]] = new com.badlogic.gdx.utils.ObjectMap[com.badlogic.gdx.Application, com.badlogic.gdx.utils.Array[ShaderProgram]]()
  final val intbuf: java.nio.IntBuffer = com.badlogic.gdx.utils.BufferUtils.newIntBuffer(1)
  def invalidateAllShaderPrograms(app: com.badlogic.gdx.Application): scala.Unit = {
    if (com.badlogic.gdx.Gdx.gl20 == null) {
      return
    } else ()
    val shaderArray: com.badlogic.gdx.utils.Array[ShaderProgram] = ShaderProgram.shaders.get(app)
    if (shaderArray == null) {
      return
    } else ();
    { var i: scala.Int = 0; while (i < shaderArray.size) { {
      shaderArray.get(i).invalidated = true
      shaderArray.get(i).checkManaged()
    }; i = i + 1 } }
  }
  def clearAllShaderPrograms(app: com.badlogic.gdx.Application): scala.Unit = {
    ShaderProgram.shaders.remove(app)
  }
  def getManagedStatus(): java.lang.String = {
    val builder: java.lang.StringBuilder = new java.lang.StringBuilder()
    val i: scala.Int = 0
    builder.append("Managed shaders/app: { ")
    for (app <- ShaderProgram.shaders.keys()) {
      builder.append(ShaderProgram.shaders.get(app).size)
      builder.append(" ")
    }
    builder.append("}")
    return builder.toString()
  }
  def getNumManagedShaderPrograms(): scala.Int = {
    return ShaderProgram.shaders.get(com.badlogic.gdx.Gdx.app).size
  }
}