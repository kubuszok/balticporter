package com.badlogic.gdx.graphics.glutils

class ImmediateModeRenderer20 extends com.badlogic.gdx.graphics.glutils.ImmediateModeRenderer {
  private var primitiveType: scala.Int = 0
  private var vertexIdx: scala.Int = 0
  private var numSetTexCoords: scala.Int = 0
  private var maxVertices: scala.Int = 0
  private var numVertices: scala.Int = 0
  private var mesh: com.badlogic.gdx.graphics.Mesh = null.asInstanceOf[com.badlogic.gdx.graphics.Mesh]
  private var shader: com.badlogic.gdx.graphics.glutils.ShaderProgram = null.asInstanceOf[com.badlogic.gdx.graphics.glutils.ShaderProgram]
  private var ownsShader: scala.Boolean = false
  private var numTexCoords: scala.Int = 0
  private var vertexSize: scala.Int = 0
  private var normalOffset: scala.Int = 0
  private var colorOffset: scala.Int = 0
  private var texCoordOffset: scala.Int = 0
  private final val projModelView: com.badlogic.gdx.math.Matrix4 = new com.badlogic.gdx.math.Matrix4()
  private var vertices: scala.Array[scala.Float] = null.asInstanceOf[scala.Array[scala.Float]]
  private var shaderUniformNames: scala.Array[java.lang.String] = null.asInstanceOf[scala.Array[java.lang.String]]
  def this(maxVertices: scala.Int, hasNormals: scala.Boolean, hasColors: scala.Boolean, numTexCoords: scala.Int, shader: com.badlogic.gdx.graphics.glutils.ShaderProgram) = {
    this()
    this.maxVertices = maxVertices
    this.numTexCoords = numTexCoords
    this.shader = shader
    val attribs: scala.Array[com.badlogic.gdx.graphics.VertexAttribute] = this.buildVertexAttributes(hasNormals, hasColors, numTexCoords)
    this.mesh = new com.badlogic.gdx.graphics.Mesh(false, maxVertices, 0, attribs)
    this.vertices = new scala.Array[scala.Float](maxVertices * (this.mesh.getVertexAttributes().vertexSize / 4))
    this.vertexSize = this.mesh.getVertexAttributes().vertexSize / 4
    this.normalOffset = if (this.mesh.getVertexAttribute(com.badlogic.gdx.graphics.VertexAttributes.Usage.Normal) != null) this.mesh.getVertexAttribute(com.badlogic.gdx.graphics.VertexAttributes.Usage.Normal).offset / 4 else 0
    this.colorOffset = if (this.mesh.getVertexAttribute(com.badlogic.gdx.graphics.VertexAttributes.Usage.ColorPacked) != null) this.mesh.getVertexAttribute(com.badlogic.gdx.graphics.VertexAttributes.Usage.ColorPacked).offset / 4 else 0
    this.texCoordOffset = if (this.mesh.getVertexAttribute(com.badlogic.gdx.graphics.VertexAttributes.Usage.TextureCoordinates) != null) this.mesh.getVertexAttribute(com.badlogic.gdx.graphics.VertexAttributes.Usage.TextureCoordinates).offset / 4 else 0
    this.shaderUniformNames = new scala.Array[java.lang.String](numTexCoords);
    { var i: scala.Int = 0; while (i < numTexCoords) { {
      this.shaderUniformNames(i) = "u_sampler" + i
    }; i = i + 1 } }
  }
  def this(hasNormals: scala.Boolean, hasColors: scala.Boolean, numTexCoords: scala.Int) = {
    this(5000, hasNormals, hasColors, numTexCoords, ImmediateModeRenderer20.createDefaultShader(hasNormals, hasColors, numTexCoords))
    this.ownsShader = true
  }
  def this(maxVertices: scala.Int, hasNormals: scala.Boolean, hasColors: scala.Boolean, numTexCoords: scala.Int) = {
    this(maxVertices, hasNormals, hasColors, numTexCoords, ImmediateModeRenderer20.createDefaultShader(hasNormals, hasColors, numTexCoords))
    this.ownsShader = true
  }
  private def buildVertexAttributes(hasNormals: scala.Boolean, hasColor: scala.Boolean, numTexCoords: scala.Int): scala.Array[com.badlogic.gdx.graphics.VertexAttribute] = {
    val attribs: com.badlogic.gdx.utils.Array[com.badlogic.gdx.graphics.VertexAttribute] = new com.badlogic.gdx.utils.Array[com.badlogic.gdx.graphics.VertexAttribute]()
    attribs.add(new com.badlogic.gdx.graphics.VertexAttribute(com.badlogic.gdx.graphics.VertexAttributes.Usage.Position, 3, com.badlogic.gdx.graphics.glutils.ShaderProgram.POSITION_ATTRIBUTE))
    if (hasNormals) {
      attribs.add(new com.badlogic.gdx.graphics.VertexAttribute(com.badlogic.gdx.graphics.VertexAttributes.Usage.Normal, 3, com.badlogic.gdx.graphics.glutils.ShaderProgram.NORMAL_ATTRIBUTE))
    } else ()
    if (hasColor) {
      attribs.add(new com.badlogic.gdx.graphics.VertexAttribute(com.badlogic.gdx.graphics.VertexAttributes.Usage.ColorPacked, 4, com.badlogic.gdx.graphics.glutils.ShaderProgram.COLOR_ATTRIBUTE))
    } else ();
    { var i: scala.Int = 0; while (i < numTexCoords) { {
      attribs.add(new com.badlogic.gdx.graphics.VertexAttribute(com.badlogic.gdx.graphics.VertexAttributes.Usage.TextureCoordinates, 2, com.badlogic.gdx.graphics.glutils.ShaderProgram.TEXCOORD_ATTRIBUTE + i))
    }; i = i + 1 } }
    val array: scala.Array[com.badlogic.gdx.graphics.VertexAttribute] = new scala.Array[com.badlogic.gdx.graphics.VertexAttribute](attribs.size);
    { var i: scala.Int = 0; while (i < attribs.size) { {
      array(i) = attribs.get(i)
    }; i = i + 1 } }
    return array
  }
  def setShader(shader: com.badlogic.gdx.graphics.glutils.ShaderProgram): scala.Unit = {
    if (this.ownsShader) {
      this.shader.dispose()
    } else ()
    this.shader = shader
    this.ownsShader = false
  }
  def getShader(): com.badlogic.gdx.graphics.glutils.ShaderProgram = {
    return this.shader
  }
  def begin(projModelView: com.badlogic.gdx.math.Matrix4, primitiveType: scala.Int): scala.Unit = {
    this.projModelView.set(projModelView)
    this.primitiveType = primitiveType
  }
  def color(color: com.badlogic.gdx.graphics.Color): scala.Unit = {
    this.vertices(this.vertexIdx + this.colorOffset) = color.toFloatBits()
  }
  def color(r: scala.Float, g: scala.Float, b: scala.Float, a: scala.Float): scala.Unit = {
    this.vertices(this.vertexIdx + this.colorOffset) = com.badlogic.gdx.graphics.Color.toFloatBits(r, g, b, a)
  }
  def color(colorBits: scala.Float): scala.Unit = {
    this.vertices(this.vertexIdx + this.colorOffset) = colorBits
  }
  def texCoord(u: scala.Float, v: scala.Float): scala.Unit = {
    val idx: scala.Int = this.vertexIdx + this.texCoordOffset
    this.vertices(idx + this.numSetTexCoords) = u
    this.vertices((idx + this.numSetTexCoords) + 1) = v
    this.numSetTexCoords = this.numSetTexCoords + 2
  }
  def normal(x: scala.Float, y: scala.Float, z: scala.Float): scala.Unit = {
    val idx: scala.Int = this.vertexIdx + this.normalOffset
    this.vertices(idx) = x
    this.vertices(idx + 1) = y
    this.vertices(idx + 2) = z
  }
  def vertex(x: scala.Float, y: scala.Float, z: scala.Float): scala.Unit = {
    val idx: scala.Int = this.vertexIdx
    this.vertices(idx) = x
    this.vertices(idx + 1) = y
    this.vertices(idx + 2) = z
    this.numSetTexCoords = 0
    this.vertexIdx = this.vertexIdx + this.vertexSize
    this.numVertices = this.numVertices + 1
  }
  def flush(): scala.Unit = {
    if (this.numVertices == 0) {
      return
    } else ()
    this.shader.bind()
    this.shader.setUniformMatrix("u_projModelView", this.projModelView);
    { var i: scala.Int = 0; while (i < this.numTexCoords) { {
      this.shader.setUniformi(this.shaderUniformNames(i), i)
    }; i = i + 1 } }
    this.mesh.setVertices(this.vertices, 0, this.vertexIdx)
    this.mesh.render(this.shader, this.primitiveType)
    this.numSetTexCoords = 0
    this.vertexIdx = 0
    this.numVertices = 0
  }
  def `end`(): scala.Unit = {
    this.flush()
  }
  def getNumVertices(): scala.Int = {
    return this.numVertices
  }
  def getMaxVertices(): scala.Int = {
    return this.maxVertices
  }
  def dispose(): scala.Unit = {
    if (this.ownsShader && (this.shader != null)) {
      this.shader.dispose()
    } else ()
    this.mesh.dispose()
  }
}
object ImmediateModeRenderer20 {
  private def createVertexShader(hasNormals: scala.Boolean, hasColors: scala.Boolean, numTexCoords: scala.Int): java.lang.String = {
    var shader: java.lang.String = ((("attribute vec4 " + com.badlogic.gdx.graphics.glutils.ShaderProgram.POSITION_ATTRIBUTE) + ";\n") + (if (hasNormals) ("attribute vec3 " + com.badlogic.gdx.graphics.glutils.ShaderProgram.NORMAL_ATTRIBUTE) + ";\n" else "")) + (if (hasColors) ("attribute vec4 " + com.badlogic.gdx.graphics.glutils.ShaderProgram.COLOR_ATTRIBUTE) + ";\n" else "");
    { var i: scala.Int = 0; while (i < numTexCoords) { {
      shader = shader + ((("attribute vec2 " + com.badlogic.gdx.graphics.glutils.ShaderProgram.TEXCOORD_ATTRIBUTE) + i) + ";\n")
    }; i = i + 1 } }
    shader = shader + ("uniform mat4 u_projModelView;\n" + (if (hasColors) "varying vec4 v_col;\n" else ""));
    { var i: scala.Int = 0; while (i < numTexCoords) { {
      shader = shader + (("varying vec2 v_tex" + i) + ";\n")
    }; i = i + 1 } }
    shader = shader + ((("void main() {\n" + "   gl_Position = u_projModelView * ") + com.badlogic.gdx.graphics.glutils.ShaderProgram.POSITION_ATTRIBUTE) + ";\n")
    if (hasColors) {
      shader = shader + ((("   v_col = " + com.badlogic.gdx.graphics.glutils.ShaderProgram.COLOR_ATTRIBUTE) + ";\n") + "   v_col.a *= 255.0 / 254.0;\n")
    } else ();
    { var i: scala.Int = 0; while (i < numTexCoords) { {
      shader = shader + ((((("   v_tex" + i) + " = ") + com.badlogic.gdx.graphics.glutils.ShaderProgram.TEXCOORD_ATTRIBUTE) + i) + ";\n")
    }; i = i + 1 } }
    shader = shader + ("   gl_PointSize = 1.0;\n" + "}\n")
    return shader
  }
  private def createFragmentShader(hasNormals: scala.Boolean, hasColors: scala.Boolean, numTexCoords: scala.Int): java.lang.String = {
    var shader: java.lang.String = ("#ifdef GL_ES\n" + "precision mediump float;\n") + "#endif\n"
    if (hasColors) {
      shader = shader + "varying vec4 v_col;\n"
    } else ();
    { var i: scala.Int = 0; while (i < numTexCoords) { {
      shader = shader + (("varying vec2 v_tex" + i) + ";\n")
      shader = shader + (("uniform sampler2D u_sampler" + i) + ";\n")
    }; i = i + 1 } }
    shader = shader + (("void main() {\n" + "   gl_FragColor = ") + (if (hasColors) "v_col" else "vec4(1, 1, 1, 1)"))
    if (numTexCoords > 0) {
      shader = shader + " * "
    } else ();
    { var i: scala.Int = 0; while (i < numTexCoords) { {
      if (i == (numTexCoords - 1)) {
        shader = shader + ((((" texture2D(u_sampler" + i) + ",  v_tex") + i) + ")")
      } else {
        shader = shader + ((((" texture2D(u_sampler" + i) + ",  v_tex") + i) + ") *")
      }
    }; i = i + 1 } }
    shader = shader + ";\n}"
    return shader
  }
  def createDefaultShader(hasNormals: scala.Boolean, hasColors: scala.Boolean, numTexCoords: scala.Int): com.badlogic.gdx.graphics.glutils.ShaderProgram = {
    val vertexShader: java.lang.String = ImmediateModeRenderer20.createVertexShader(hasNormals, hasColors, numTexCoords)
    val fragmentShader: java.lang.String = ImmediateModeRenderer20.createFragmentShader(hasNormals, hasColors, numTexCoords)
    val program: com.badlogic.gdx.graphics.glutils.ShaderProgram = new com.badlogic.gdx.graphics.glutils.ShaderProgram(vertexShader, fragmentShader)
    if (!program.isCompiled()) {
      throw new com.badlogic.gdx.utils.GdxRuntimeException("Error compiling shader: " + program.getLog())
    } else ()
    return program
  }
}