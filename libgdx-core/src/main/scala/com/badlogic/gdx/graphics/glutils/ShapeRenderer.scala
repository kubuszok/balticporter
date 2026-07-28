package com.badlogic.gdx.graphics.glutils

class ShapeRenderer(maxVertices: scala.Int, defaultShader: com.badlogic.gdx.graphics.glutils.ShaderProgram) extends com.badlogic.gdx.utils.Disposable {
  private var renderer: com.badlogic.gdx.graphics.glutils.ImmediateModeRenderer = null.asInstanceOf[com.badlogic.gdx.graphics.glutils.ImmediateModeRenderer]
  private var matrixDirty: scala.Boolean = false
  private final val projectionMatrix: com.badlogic.gdx.math.Matrix4 = new com.badlogic.gdx.math.Matrix4()
  private final val transformMatrix: com.badlogic.gdx.math.Matrix4 = new com.badlogic.gdx.math.Matrix4()
  private final val combinedMatrix: com.badlogic.gdx.math.Matrix4 = new com.badlogic.gdx.math.Matrix4()
  private final val tmp: com.badlogic.gdx.math.Vector2 = new com.badlogic.gdx.math.Vector2()
  private final val color: com.badlogic.gdx.graphics.Color = new com.badlogic.gdx.graphics.Color(1, 1, 1, 1)
  private var shapeType: com.badlogic.gdx.graphics.glutils.ShapeRenderer.ShapeType = null.asInstanceOf[com.badlogic.gdx.graphics.glutils.ShapeRenderer.ShapeType]
  private var autoShapeType: scala.Boolean = false
  private var defaultRectLineWidth: scala.Float = 0.75f
  def this(maxVertices: scala.Int) = {
    this(maxVertices, null)
  }
  def this() = {
    this(5000)
  }
  if (defaultShader == null) {
    this.renderer = new com.badlogic.gdx.graphics.glutils.ImmediateModeRenderer20(maxVertices, false, true, 0)
  } else {
    this.renderer = new com.badlogic.gdx.graphics.glutils.ImmediateModeRenderer20(maxVertices, false, true, 0, defaultShader)
  }
  this.projectionMatrix.setToOrtho2D(0, 0, com.badlogic.gdx.Gdx.graphics.getWidth(), com.badlogic.gdx.Gdx.graphics.getHeight())
  this.matrixDirty = true
  def setColor(color: com.badlogic.gdx.graphics.Color): scala.Unit = {
    this.color.set(color)
  }
  def setColor(r: scala.Float, g: scala.Float, b: scala.Float, a: scala.Float): scala.Unit = {
    this.color.set(r, g, b, a)
  }
  def getColor(): com.badlogic.gdx.graphics.Color = {
    return this.color
  }
  def updateMatrices(): scala.Unit = {
    this.matrixDirty = true
  }
  def setProjectionMatrix(matrix: com.badlogic.gdx.math.Matrix4): scala.Unit = {
    this.projectionMatrix.set(matrix)
    this.matrixDirty = true
  }
  def getProjectionMatrix(): com.badlogic.gdx.math.Matrix4 = {
    return this.projectionMatrix
  }
  def setTransformMatrix(matrix: com.badlogic.gdx.math.Matrix4): scala.Unit = {
    this.transformMatrix.set(matrix)
    this.matrixDirty = true
  }
  def getTransformMatrix(): com.badlogic.gdx.math.Matrix4 = {
    return this.transformMatrix
  }
  def identity(): scala.Unit = {
    this.transformMatrix.idt()
    this.matrixDirty = true
  }
  def translate(x: scala.Float, y: scala.Float, z: scala.Float): scala.Unit = {
    this.transformMatrix.translate(x, y, z)
    this.matrixDirty = true
  }
  def rotate(axisX: scala.Float, axisY: scala.Float, axisZ: scala.Float, degrees: scala.Float): scala.Unit = {
    this.transformMatrix.rotate(axisX, axisY, axisZ, degrees)
    this.matrixDirty = true
  }
  def scale(scaleX: scala.Float, scaleY: scala.Float, scaleZ: scala.Float): scala.Unit = {
    this.transformMatrix.scale(scaleX, scaleY, scaleZ)
    this.matrixDirty = true
  }
  def setAutoShapeType(autoShapeType: scala.Boolean): scala.Unit = {
    this.autoShapeType = autoShapeType
  }
  def begin(): scala.Unit = {
    if (!this.autoShapeType) {
      throw new java.lang.IllegalStateException("autoShapeType must be true to use this method.")
    } else ()
    this.begin(com.badlogic.gdx.graphics.glutils.ShapeRenderer.ShapeType.Line)
  }
  def begin(`type`: com.badlogic.gdx.graphics.glutils.ShapeRenderer.ShapeType): scala.Unit = {
    if (this.shapeType != null) {
      throw new java.lang.IllegalStateException("Call end() before beginning a new shape batch.")
    } else ()
    this.shapeType = `type`
    if (this.matrixDirty) {
      this.combinedMatrix.set(this.projectionMatrix)
      com.badlogic.gdx.math.Matrix4.mul(this.combinedMatrix.`val`, this.transformMatrix.`val`)
      this.matrixDirty = false
    } else ()
    this.renderer.begin(this.combinedMatrix, this.shapeType.getGlType())
  }
  def set(`type`: com.badlogic.gdx.graphics.glutils.ShapeRenderer.ShapeType): scala.Unit = {
    if (this.shapeType == `type`) {
      return
    } else ()
    if (this.shapeType == null) {
      throw new java.lang.IllegalStateException("begin must be called first.")
    } else ()
    if (!this.autoShapeType) {
      throw new java.lang.IllegalStateException("autoShapeType must be enabled.")
    } else ()
    this.`end`()
    this.begin(`type`)
  }
  def point(x: scala.Float, y: scala.Float, z: scala.Float): scala.Unit = {
    if (this.shapeType == com.badlogic.gdx.graphics.glutils.ShapeRenderer.ShapeType.Line) {
      val size: scala.Float = this.defaultRectLineWidth * 0.5f
      this.line(x - size, y - size, z, x + size, y + size, z)
      return
    } else {
      if (this.shapeType == com.badlogic.gdx.graphics.glutils.ShapeRenderer.ShapeType.Filled) {
        val size: scala.Float = this.defaultRectLineWidth * 0.5f
        this.box(x - size, y - size, z - size, this.defaultRectLineWidth, this.defaultRectLineWidth, this.defaultRectLineWidth)
        return
      } else ()
    }
    this.check(com.badlogic.gdx.graphics.glutils.ShapeRenderer.ShapeType.Point, null, 1)
    this.renderer.color(this.color)
    this.renderer.vertex(x, y, z)
  }
  final def line(x: scala.Float, y: scala.Float, z: scala.Float, x2: scala.Float, y2: scala.Float, z2: scala.Float): scala.Unit = {
    this.line(x, y, z, x2, y2, z2, this.color, this.color)
  }
  final def line(v0: com.badlogic.gdx.math.Vector3, v1: com.badlogic.gdx.math.Vector3): scala.Unit = {
    this.line(v0.x, v0.y, v0.z, v1.x, v1.y, v1.z, this.color, this.color)
  }
  final def line(x: scala.Float, y: scala.Float, x2: scala.Float, y2: scala.Float): scala.Unit = {
    this.line(x, y, 0.0f, x2, y2, 0.0f, this.color, this.color)
  }
  final def line(v0: com.badlogic.gdx.math.Vector2, v1: com.badlogic.gdx.math.Vector2): scala.Unit = {
    this.line(v0.x, v0.y, 0.0f, v1.x, v1.y, 0.0f, this.color, this.color)
  }
  final def line(x: scala.Float, y: scala.Float, x2: scala.Float, y2: scala.Float, c1: com.badlogic.gdx.graphics.Color, c2: com.badlogic.gdx.graphics.Color): scala.Unit = {
    this.line(x, y, 0.0f, x2, y2, 0.0f, c1, c2)
  }
  def line(x: scala.Float, y: scala.Float, z: scala.Float, x2: scala.Float, y2: scala.Float, z2: scala.Float, c1: com.badlogic.gdx.graphics.Color, c2: com.badlogic.gdx.graphics.Color): scala.Unit = {
    if (this.shapeType == com.badlogic.gdx.graphics.glutils.ShapeRenderer.ShapeType.Filled) {
      this.rectLine(x, y, x2, y2, this.defaultRectLineWidth, c1, c2)
      return
    } else ()
    this.check(com.badlogic.gdx.graphics.glutils.ShapeRenderer.ShapeType.Line, null, 2)
    this.renderer.color(c1.r, c1.g, c1.b, c1.a)
    this.renderer.vertex(x, y, z)
    this.renderer.color(c2.r, c2.g, c2.b, c2.a)
    this.renderer.vertex(x2, y2, z2)
  }
  def curve(x1: scala.Float, y1: scala.Float, cx1: scala.Float, cy1: scala.Float, cx2: scala.Float, cy2: scala.Float, x2: scala.Float, y2: scala.Float, segments$arg: scala.Int): scala.Unit = {
    var segments: scala.Int = segments$arg
    this.check(com.badlogic.gdx.graphics.glutils.ShapeRenderer.ShapeType.Line, null, (segments * 2) + 2)
    val colorBits: scala.Float = this.color.toFloatBits()
    val subdiv_step: scala.Float = 1.0f / segments
    val subdiv_step2: scala.Float = subdiv_step * subdiv_step
    val subdiv_step3: scala.Float = (subdiv_step * subdiv_step) * subdiv_step
    val pre1: scala.Float = 3 * subdiv_step
    val pre2: scala.Float = 3 * subdiv_step2
    val pre4: scala.Float = 6 * subdiv_step2
    val pre5: scala.Float = 6 * subdiv_step3
    val tmp1x: scala.Float = (x1 - (cx1 * 2)) + cx2
    val tmp1y: scala.Float = (y1 - (cy1 * 2)) + cy2
    val tmp2x: scala.Float = (((cx1 - cx2) * 3) - x1) + x2
    val tmp2y: scala.Float = (((cy1 - cy2) * 3) - y1) + y2
    var fx: scala.Float = x1
    var fy: scala.Float = y1
    var dfx: scala.Float = (((cx1 - x1) * pre1) + (tmp1x * pre2)) + (tmp2x * subdiv_step3)
    var dfy: scala.Float = (((cy1 - y1) * pre1) + (tmp1y * pre2)) + (tmp2y * subdiv_step3)
    var ddfx: scala.Float = (tmp1x * pre4) + (tmp2x * pre5)
    var ddfy: scala.Float = (tmp1y * pre4) + (tmp2y * pre5)
    val dddfx: scala.Float = tmp2x * pre5
    val dddfy: scala.Float = tmp2y * pre5
    while ({ segments -= 1; segments } > 0) {
      this.renderer.color(colorBits)
      this.renderer.vertex(fx, fy, 0)
      fx = fx + dfx
      fy = fy + dfy
      dfx = dfx + ddfx
      dfy = dfy + ddfy
      ddfx = ddfx + dddfx
      ddfy = ddfy + dddfy
      this.renderer.color(colorBits)
      this.renderer.vertex(fx, fy, 0)
    }
    this.renderer.color(colorBits)
    this.renderer.vertex(fx, fy, 0)
    this.renderer.color(colorBits)
    this.renderer.vertex(x2, y2, 0)
  }
  def triangle(x1: scala.Float, y1: scala.Float, x2: scala.Float, y2: scala.Float, x3: scala.Float, y3: scala.Float): scala.Unit = {
    this.check(com.badlogic.gdx.graphics.glutils.ShapeRenderer.ShapeType.Line, com.badlogic.gdx.graphics.glutils.ShapeRenderer.ShapeType.Filled, 6)
    val colorBits: scala.Float = this.color.toFloatBits()
    if (this.shapeType == com.badlogic.gdx.graphics.glutils.ShapeRenderer.ShapeType.Line) {
      this.renderer.color(colorBits)
      this.renderer.vertex(x1, y1, 0)
      this.renderer.color(colorBits)
      this.renderer.vertex(x2, y2, 0)
      this.renderer.color(colorBits)
      this.renderer.vertex(x2, y2, 0)
      this.renderer.color(colorBits)
      this.renderer.vertex(x3, y3, 0)
      this.renderer.color(colorBits)
      this.renderer.vertex(x3, y3, 0)
      this.renderer.color(colorBits)
      this.renderer.vertex(x1, y1, 0)
    } else {
      this.renderer.color(colorBits)
      this.renderer.vertex(x1, y1, 0)
      this.renderer.color(colorBits)
      this.renderer.vertex(x2, y2, 0)
      this.renderer.color(colorBits)
      this.renderer.vertex(x3, y3, 0)
    }
  }
  def triangle(x1: scala.Float, y1: scala.Float, x2: scala.Float, y2: scala.Float, x3: scala.Float, y3: scala.Float, col1: com.badlogic.gdx.graphics.Color, col2: com.badlogic.gdx.graphics.Color, col3: com.badlogic.gdx.graphics.Color): scala.Unit = {
    this.check(com.badlogic.gdx.graphics.glutils.ShapeRenderer.ShapeType.Line, com.badlogic.gdx.graphics.glutils.ShapeRenderer.ShapeType.Filled, 6)
    if (this.shapeType == com.badlogic.gdx.graphics.glutils.ShapeRenderer.ShapeType.Line) {
      this.renderer.color(col1.r, col1.g, col1.b, col1.a)
      this.renderer.vertex(x1, y1, 0)
      this.renderer.color(col2.r, col2.g, col2.b, col2.a)
      this.renderer.vertex(x2, y2, 0)
      this.renderer.color(col2.r, col2.g, col2.b, col2.a)
      this.renderer.vertex(x2, y2, 0)
      this.renderer.color(col3.r, col3.g, col3.b, col3.a)
      this.renderer.vertex(x3, y3, 0)
      this.renderer.color(col3.r, col3.g, col3.b, col3.a)
      this.renderer.vertex(x3, y3, 0)
      this.renderer.color(col1.r, col1.g, col1.b, col1.a)
      this.renderer.vertex(x1, y1, 0)
    } else {
      this.renderer.color(col1.r, col1.g, col1.b, col1.a)
      this.renderer.vertex(x1, y1, 0)
      this.renderer.color(col2.r, col2.g, col2.b, col2.a)
      this.renderer.vertex(x2, y2, 0)
      this.renderer.color(col3.r, col3.g, col3.b, col3.a)
      this.renderer.vertex(x3, y3, 0)
    }
  }
  def rect(x: scala.Float, y: scala.Float, width: scala.Float, height: scala.Float): scala.Unit = {
    this.check(com.badlogic.gdx.graphics.glutils.ShapeRenderer.ShapeType.Line, com.badlogic.gdx.graphics.glutils.ShapeRenderer.ShapeType.Filled, 8)
    val colorBits: scala.Float = this.color.toFloatBits()
    if (this.shapeType == com.badlogic.gdx.graphics.glutils.ShapeRenderer.ShapeType.Line) {
      this.renderer.color(colorBits)
      this.renderer.vertex(x, y, 0)
      this.renderer.color(colorBits)
      this.renderer.vertex(x + width, y, 0)
      this.renderer.color(colorBits)
      this.renderer.vertex(x + width, y, 0)
      this.renderer.color(colorBits)
      this.renderer.vertex(x + width, y + height, 0)
      this.renderer.color(colorBits)
      this.renderer.vertex(x + width, y + height, 0)
      this.renderer.color(colorBits)
      this.renderer.vertex(x, y + height, 0)
      this.renderer.color(colorBits)
      this.renderer.vertex(x, y + height, 0)
      this.renderer.color(colorBits)
      this.renderer.vertex(x, y, 0)
    } else {
      this.renderer.color(colorBits)
      this.renderer.vertex(x, y, 0)
      this.renderer.color(colorBits)
      this.renderer.vertex(x + width, y, 0)
      this.renderer.color(colorBits)
      this.renderer.vertex(x + width, y + height, 0)
      this.renderer.color(colorBits)
      this.renderer.vertex(x + width, y + height, 0)
      this.renderer.color(colorBits)
      this.renderer.vertex(x, y + height, 0)
      this.renderer.color(colorBits)
      this.renderer.vertex(x, y, 0)
    }
  }
  def rect(x: scala.Float, y: scala.Float, width: scala.Float, height: scala.Float, col1: com.badlogic.gdx.graphics.Color, col2: com.badlogic.gdx.graphics.Color, col3: com.badlogic.gdx.graphics.Color, col4: com.badlogic.gdx.graphics.Color): scala.Unit = {
    this.check(com.badlogic.gdx.graphics.glutils.ShapeRenderer.ShapeType.Line, com.badlogic.gdx.graphics.glutils.ShapeRenderer.ShapeType.Filled, 8)
    if (this.shapeType == com.badlogic.gdx.graphics.glutils.ShapeRenderer.ShapeType.Line) {
      this.renderer.color(col1.r, col1.g, col1.b, col1.a)
      this.renderer.vertex(x, y, 0)
      this.renderer.color(col2.r, col2.g, col2.b, col2.a)
      this.renderer.vertex(x + width, y, 0)
      this.renderer.color(col2.r, col2.g, col2.b, col2.a)
      this.renderer.vertex(x + width, y, 0)
      this.renderer.color(col3.r, col3.g, col3.b, col3.a)
      this.renderer.vertex(x + width, y + height, 0)
      this.renderer.color(col3.r, col3.g, col3.b, col3.a)
      this.renderer.vertex(x + width, y + height, 0)
      this.renderer.color(col4.r, col4.g, col4.b, col4.a)
      this.renderer.vertex(x, y + height, 0)
      this.renderer.color(col4.r, col4.g, col4.b, col4.a)
      this.renderer.vertex(x, y + height, 0)
      this.renderer.color(col1.r, col1.g, col1.b, col1.a)
      this.renderer.vertex(x, y, 0)
    } else {
      this.renderer.color(col1.r, col1.g, col1.b, col1.a)
      this.renderer.vertex(x, y, 0)
      this.renderer.color(col2.r, col2.g, col2.b, col2.a)
      this.renderer.vertex(x + width, y, 0)
      this.renderer.color(col3.r, col3.g, col3.b, col3.a)
      this.renderer.vertex(x + width, y + height, 0)
      this.renderer.color(col3.r, col3.g, col3.b, col3.a)
      this.renderer.vertex(x + width, y + height, 0)
      this.renderer.color(col4.r, col4.g, col4.b, col4.a)
      this.renderer.vertex(x, y + height, 0)
      this.renderer.color(col1.r, col1.g, col1.b, col1.a)
      this.renderer.vertex(x, y, 0)
    }
  }
  def rect(x: scala.Float, y: scala.Float, originX: scala.Float, originY: scala.Float, width: scala.Float, height: scala.Float, scaleX: scala.Float, scaleY: scala.Float, degrees: scala.Float): scala.Unit = {
    this.rect(x, y, originX, originY, width, height, scaleX, scaleY, degrees, this.color, this.color, this.color, this.color)
  }
  def rect(x: scala.Float, y: scala.Float, originX: scala.Float, originY: scala.Float, width: scala.Float, height: scala.Float, scaleX: scala.Float, scaleY: scala.Float, degrees: scala.Float, col1: com.badlogic.gdx.graphics.Color, col2: com.badlogic.gdx.graphics.Color, col3: com.badlogic.gdx.graphics.Color, col4: com.badlogic.gdx.graphics.Color): scala.Unit = {
    this.check(com.badlogic.gdx.graphics.glutils.ShapeRenderer.ShapeType.Line, com.badlogic.gdx.graphics.glutils.ShapeRenderer.ShapeType.Filled, 8)
    val cos: scala.Float = com.badlogic.gdx.math.MathUtils.cosDeg(degrees)
    val sin: scala.Float = com.badlogic.gdx.math.MathUtils.sinDeg(degrees)
    var fx: scala.Float = -originX
    var fy: scala.Float = -originY
    var fx2: scala.Float = width - originX
    var fy2: scala.Float = height - originY
    if ((scaleX != 1) || (scaleY != 1)) {
      fx = fx * scaleX
      fy = fy * scaleY
      fx2 = fx2 * scaleX
      fy2 = fy2 * scaleY
    } else ()
    val worldOriginX: scala.Float = x + originX
    val worldOriginY: scala.Float = y + originY
    val x1: scala.Float = ((cos * fx) - (sin * fy)) + worldOriginX
    val y1: scala.Float = ((sin * fx) + (cos * fy)) + worldOriginY
    val x2: scala.Float = ((cos * fx2) - (sin * fy)) + worldOriginX
    val y2: scala.Float = ((sin * fx2) + (cos * fy)) + worldOriginY
    val x3: scala.Float = ((cos * fx2) - (sin * fy2)) + worldOriginX
    val y3: scala.Float = ((sin * fx2) + (cos * fy2)) + worldOriginY
    val x4: scala.Float = x1 + (x3 - x2)
    val y4: scala.Float = y3 - (y2 - y1)
    if (this.shapeType == com.badlogic.gdx.graphics.glutils.ShapeRenderer.ShapeType.Line) {
      this.renderer.color(col1.r, col1.g, col1.b, col1.a)
      this.renderer.vertex(x1, y1, 0)
      this.renderer.color(col2.r, col2.g, col2.b, col2.a)
      this.renderer.vertex(x2, y2, 0)
      this.renderer.color(col2.r, col2.g, col2.b, col2.a)
      this.renderer.vertex(x2, y2, 0)
      this.renderer.color(col3.r, col3.g, col3.b, col3.a)
      this.renderer.vertex(x3, y3, 0)
      this.renderer.color(col3.r, col3.g, col3.b, col3.a)
      this.renderer.vertex(x3, y3, 0)
      this.renderer.color(col4.r, col4.g, col4.b, col4.a)
      this.renderer.vertex(x4, y4, 0)
      this.renderer.color(col4.r, col4.g, col4.b, col4.a)
      this.renderer.vertex(x4, y4, 0)
      this.renderer.color(col1.r, col1.g, col1.b, col1.a)
      this.renderer.vertex(x1, y1, 0)
    } else {
      this.renderer.color(col1.r, col1.g, col1.b, col1.a)
      this.renderer.vertex(x1, y1, 0)
      this.renderer.color(col2.r, col2.g, col2.b, col2.a)
      this.renderer.vertex(x2, y2, 0)
      this.renderer.color(col3.r, col3.g, col3.b, col3.a)
      this.renderer.vertex(x3, y3, 0)
      this.renderer.color(col3.r, col3.g, col3.b, col3.a)
      this.renderer.vertex(x3, y3, 0)
      this.renderer.color(col4.r, col4.g, col4.b, col4.a)
      this.renderer.vertex(x4, y4, 0)
      this.renderer.color(col1.r, col1.g, col1.b, col1.a)
      this.renderer.vertex(x1, y1, 0)
    }
  }
  def rectLine(x1: scala.Float, y1: scala.Float, x2: scala.Float, y2: scala.Float, width$arg: scala.Float): scala.Unit = {
    var width: scala.Float = width$arg
    this.check(com.badlogic.gdx.graphics.glutils.ShapeRenderer.ShapeType.Line, com.badlogic.gdx.graphics.glutils.ShapeRenderer.ShapeType.Filled, 8)
    val colorBits: scala.Float = this.color.toFloatBits()
    val t: com.badlogic.gdx.math.Vector2 = this.tmp.set(y2 - y1, x1 - x2).nor()
    width = width * 0.5f
    val tx: scala.Float = t.x * width
    val ty: scala.Float = t.y * width
    if (this.shapeType == com.badlogic.gdx.graphics.glutils.ShapeRenderer.ShapeType.Line) {
      this.renderer.color(colorBits)
      this.renderer.vertex(x1 + tx, y1 + ty, 0)
      this.renderer.color(colorBits)
      this.renderer.vertex(x1 - tx, y1 - ty, 0)
      this.renderer.color(colorBits)
      this.renderer.vertex(x2 + tx, y2 + ty, 0)
      this.renderer.color(colorBits)
      this.renderer.vertex(x2 - tx, y2 - ty, 0)
      this.renderer.color(colorBits)
      this.renderer.vertex(x2 + tx, y2 + ty, 0)
      this.renderer.color(colorBits)
      this.renderer.vertex(x1 + tx, y1 + ty, 0)
      this.renderer.color(colorBits)
      this.renderer.vertex(x2 - tx, y2 - ty, 0)
      this.renderer.color(colorBits)
      this.renderer.vertex(x1 - tx, y1 - ty, 0)
    } else {
      this.renderer.color(colorBits)
      this.renderer.vertex(x1 + tx, y1 + ty, 0)
      this.renderer.color(colorBits)
      this.renderer.vertex(x1 - tx, y1 - ty, 0)
      this.renderer.color(colorBits)
      this.renderer.vertex(x2 + tx, y2 + ty, 0)
      this.renderer.color(colorBits)
      this.renderer.vertex(x2 - tx, y2 - ty, 0)
      this.renderer.color(colorBits)
      this.renderer.vertex(x2 + tx, y2 + ty, 0)
      this.renderer.color(colorBits)
      this.renderer.vertex(x1 - tx, y1 - ty, 0)
    }
  }
  def rectLine(x1: scala.Float, y1: scala.Float, x2: scala.Float, y2: scala.Float, width$arg: scala.Float, c1: com.badlogic.gdx.graphics.Color, c2: com.badlogic.gdx.graphics.Color): scala.Unit = {
    var width: scala.Float = width$arg
    this.check(com.badlogic.gdx.graphics.glutils.ShapeRenderer.ShapeType.Line, com.badlogic.gdx.graphics.glutils.ShapeRenderer.ShapeType.Filled, 8)
    val col1Bits: scala.Float = c1.toFloatBits()
    val col2Bits: scala.Float = c2.toFloatBits()
    val t: com.badlogic.gdx.math.Vector2 = this.tmp.set(y2 - y1, x1 - x2).nor()
    width = width * 0.5f
    val tx: scala.Float = t.x * width
    val ty: scala.Float = t.y * width
    if (this.shapeType == com.badlogic.gdx.graphics.glutils.ShapeRenderer.ShapeType.Line) {
      this.renderer.color(col1Bits)
      this.renderer.vertex(x1 + tx, y1 + ty, 0)
      this.renderer.color(col1Bits)
      this.renderer.vertex(x1 - tx, y1 - ty, 0)
      this.renderer.color(col2Bits)
      this.renderer.vertex(x2 + tx, y2 + ty, 0)
      this.renderer.color(col2Bits)
      this.renderer.vertex(x2 - tx, y2 - ty, 0)
      this.renderer.color(col2Bits)
      this.renderer.vertex(x2 + tx, y2 + ty, 0)
      this.renderer.color(col1Bits)
      this.renderer.vertex(x1 + tx, y1 + ty, 0)
      this.renderer.color(col2Bits)
      this.renderer.vertex(x2 - tx, y2 - ty, 0)
      this.renderer.color(col1Bits)
      this.renderer.vertex(x1 - tx, y1 - ty, 0)
    } else {
      this.renderer.color(col1Bits)
      this.renderer.vertex(x1 + tx, y1 + ty, 0)
      this.renderer.color(col1Bits)
      this.renderer.vertex(x1 - tx, y1 - ty, 0)
      this.renderer.color(col2Bits)
      this.renderer.vertex(x2 + tx, y2 + ty, 0)
      this.renderer.color(col2Bits)
      this.renderer.vertex(x2 - tx, y2 - ty, 0)
      this.renderer.color(col2Bits)
      this.renderer.vertex(x2 + tx, y2 + ty, 0)
      this.renderer.color(col1Bits)
      this.renderer.vertex(x1 - tx, y1 - ty, 0)
    }
  }
  def rectLine(p1: com.badlogic.gdx.math.Vector2, p2: com.badlogic.gdx.math.Vector2, width: scala.Float): scala.Unit = {
    this.rectLine(p1.x, p1.y, p2.x, p2.y, width)
  }
  def box(x: scala.Float, y: scala.Float, z: scala.Float, width: scala.Float, height: scala.Float, depth$arg: scala.Float): scala.Unit = {
    var depth: scala.Float = depth$arg
    depth = -depth
    val colorBits: scala.Float = this.color.toFloatBits()
    if (this.shapeType == com.badlogic.gdx.graphics.glutils.ShapeRenderer.ShapeType.Line) {
      this.check(com.badlogic.gdx.graphics.glutils.ShapeRenderer.ShapeType.Line, com.badlogic.gdx.graphics.glutils.ShapeRenderer.ShapeType.Filled, 24)
      this.renderer.color(colorBits)
      this.renderer.vertex(x, y, z)
      this.renderer.color(colorBits)
      this.renderer.vertex(x + width, y, z)
      this.renderer.color(colorBits)
      this.renderer.vertex(x + width, y, z)
      this.renderer.color(colorBits)
      this.renderer.vertex(x + width, y, z + depth)
      this.renderer.color(colorBits)
      this.renderer.vertex(x + width, y, z + depth)
      this.renderer.color(colorBits)
      this.renderer.vertex(x, y, z + depth)
      this.renderer.color(colorBits)
      this.renderer.vertex(x, y, z + depth)
      this.renderer.color(colorBits)
      this.renderer.vertex(x, y, z)
      this.renderer.color(colorBits)
      this.renderer.vertex(x, y, z)
      this.renderer.color(colorBits)
      this.renderer.vertex(x, y + height, z)
      this.renderer.color(colorBits)
      this.renderer.vertex(x, y + height, z)
      this.renderer.color(colorBits)
      this.renderer.vertex(x + width, y + height, z)
      this.renderer.color(colorBits)
      this.renderer.vertex(x + width, y + height, z)
      this.renderer.color(colorBits)
      this.renderer.vertex(x + width, y + height, z + depth)
      this.renderer.color(colorBits)
      this.renderer.vertex(x + width, y + height, z + depth)
      this.renderer.color(colorBits)
      this.renderer.vertex(x, y + height, z + depth)
      this.renderer.color(colorBits)
      this.renderer.vertex(x, y + height, z + depth)
      this.renderer.color(colorBits)
      this.renderer.vertex(x, y + height, z)
      this.renderer.color(colorBits)
      this.renderer.vertex(x + width, y, z)
      this.renderer.color(colorBits)
      this.renderer.vertex(x + width, y + height, z)
      this.renderer.color(colorBits)
      this.renderer.vertex(x + width, y, z + depth)
      this.renderer.color(colorBits)
      this.renderer.vertex(x + width, y + height, z + depth)
      this.renderer.color(colorBits)
      this.renderer.vertex(x, y, z + depth)
      this.renderer.color(colorBits)
      this.renderer.vertex(x, y + height, z + depth)
    } else {
      this.check(com.badlogic.gdx.graphics.glutils.ShapeRenderer.ShapeType.Line, com.badlogic.gdx.graphics.glutils.ShapeRenderer.ShapeType.Filled, 36)
      this.renderer.color(colorBits)
      this.renderer.vertex(x, y, z)
      this.renderer.color(colorBits)
      this.renderer.vertex(x + width, y, z)
      this.renderer.color(colorBits)
      this.renderer.vertex(x + width, y + height, z)
      this.renderer.color(colorBits)
      this.renderer.vertex(x, y, z)
      this.renderer.color(colorBits)
      this.renderer.vertex(x + width, y + height, z)
      this.renderer.color(colorBits)
      this.renderer.vertex(x, y + height, z)
      this.renderer.color(colorBits)
      this.renderer.vertex(x + width, y, z + depth)
      this.renderer.color(colorBits)
      this.renderer.vertex(x, y, z + depth)
      this.renderer.color(colorBits)
      this.renderer.vertex(x + width, y + height, z + depth)
      this.renderer.color(colorBits)
      this.renderer.vertex(x + width, y + height, z + depth)
      this.renderer.color(colorBits)
      this.renderer.vertex(x, y, z + depth)
      this.renderer.color(colorBits)
      this.renderer.vertex(x, y + height, z + depth)
      this.renderer.color(colorBits)
      this.renderer.vertex(x, y, z + depth)
      this.renderer.color(colorBits)
      this.renderer.vertex(x, y, z)
      this.renderer.color(colorBits)
      this.renderer.vertex(x, y + height, z)
      this.renderer.color(colorBits)
      this.renderer.vertex(x, y, z + depth)
      this.renderer.color(colorBits)
      this.renderer.vertex(x, y + height, z)
      this.renderer.color(colorBits)
      this.renderer.vertex(x, y + height, z + depth)
      this.renderer.color(colorBits)
      this.renderer.vertex(x + width, y, z)
      this.renderer.color(colorBits)
      this.renderer.vertex(x + width, y, z + depth)
      this.renderer.color(colorBits)
      this.renderer.vertex(x + width, y + height, z + depth)
      this.renderer.color(colorBits)
      this.renderer.vertex(x + width, y, z)
      this.renderer.color(colorBits)
      this.renderer.vertex(x + width, y + height, z + depth)
      this.renderer.color(colorBits)
      this.renderer.vertex(x + width, y + height, z)
      this.renderer.color(colorBits)
      this.renderer.vertex(x, y + height, z)
      this.renderer.color(colorBits)
      this.renderer.vertex(x + width, y + height, z)
      this.renderer.color(colorBits)
      this.renderer.vertex(x + width, y + height, z + depth)
      this.renderer.color(colorBits)
      this.renderer.vertex(x, y + height, z)
      this.renderer.color(colorBits)
      this.renderer.vertex(x + width, y + height, z + depth)
      this.renderer.color(colorBits)
      this.renderer.vertex(x, y + height, z + depth)
      this.renderer.color(colorBits)
      this.renderer.vertex(x, y, z + depth)
      this.renderer.color(colorBits)
      this.renderer.vertex(x + width, y, z + depth)
      this.renderer.color(colorBits)
      this.renderer.vertex(x + width, y, z)
      this.renderer.color(colorBits)
      this.renderer.vertex(x, y, z + depth)
      this.renderer.color(colorBits)
      this.renderer.vertex(x + width, y, z)
      this.renderer.color(colorBits)
      this.renderer.vertex(x, y, z)
    }
  }
  def x(x: scala.Float, y: scala.Float, size: scala.Float): scala.Unit = {
    this.line(x - size, y - size, x + size, y + size)
    this.line(x - size, y + size, x + size, y - size)
  }
  def x(p: com.badlogic.gdx.math.Vector2, size: scala.Float): scala.Unit = {
    this.x(p.x, p.y, size)
  }
  def arc(x: scala.Float, y: scala.Float, radius: scala.Float, start: scala.Float, degrees: scala.Float): scala.Unit = {
    this.arc(x, y, radius, start, degrees, java.lang.Math.max(1, ((6 * java.lang.Math.cbrt(radius).asInstanceOf[scala.Float]) * (degrees / 360.0f)).asInstanceOf[scala.Int].asInstanceOf[scala.Int]))
  }
  def arc(x: scala.Float, y: scala.Float, radius: scala.Float, start: scala.Float, degrees: scala.Float, segments: scala.Int): scala.Unit = {
    if (segments <= 0) {
      throw new java.lang.IllegalArgumentException("segments must be > 0.")
    } else ()
    val colorBits: scala.Float = this.color.toFloatBits()
    val theta: scala.Float = ((2 * com.badlogic.gdx.math.MathUtils.PI) * (degrees / 360.0f)) / segments
    val cos: scala.Float = com.badlogic.gdx.math.MathUtils.cos(theta)
    val sin: scala.Float = com.badlogic.gdx.math.MathUtils.sin(theta)
    var cx: scala.Float = radius * com.badlogic.gdx.math.MathUtils.cos(start * com.badlogic.gdx.math.MathUtils.degreesToRadians)
    var cy: scala.Float = radius * com.badlogic.gdx.math.MathUtils.sin(start * com.badlogic.gdx.math.MathUtils.degreesToRadians)
    if (this.shapeType == com.badlogic.gdx.graphics.glutils.ShapeRenderer.ShapeType.Line) {
      this.check(com.badlogic.gdx.graphics.glutils.ShapeRenderer.ShapeType.Line, com.badlogic.gdx.graphics.glutils.ShapeRenderer.ShapeType.Filled, (segments * 2) + 2)
      this.renderer.color(colorBits)
      this.renderer.vertex(x, y, 0)
      this.renderer.color(colorBits)
      this.renderer.vertex(x + cx, y + cy, 0);
      { var i: scala.Int = 0; while (i < segments) { {
        this.renderer.color(colorBits)
        this.renderer.vertex(x + cx, y + cy, 0)
        val temp: scala.Float = cx
        cx = (cos * cx) - (sin * cy)
        cy = (sin * temp) + (cos * cy)
        this.renderer.color(colorBits)
        this.renderer.vertex(x + cx, y + cy, 0)
      }; i = i + 1 } }
      this.renderer.color(colorBits)
      this.renderer.vertex(x + cx, y + cy, 0)
    } else {
      this.check(com.badlogic.gdx.graphics.glutils.ShapeRenderer.ShapeType.Line, com.badlogic.gdx.graphics.glutils.ShapeRenderer.ShapeType.Filled, (segments * 3) + 3);
      { var i: scala.Int = 0; while (i < segments) { {
        this.renderer.color(colorBits)
        this.renderer.vertex(x, y, 0)
        this.renderer.color(colorBits)
        this.renderer.vertex(x + cx, y + cy, 0)
        val temp: scala.Float = cx
        cx = (cos * cx) - (sin * cy)
        cy = (sin * temp) + (cos * cy)
        this.renderer.color(colorBits)
        this.renderer.vertex(x + cx, y + cy, 0)
      }; i = i + 1 } }
      this.renderer.color(colorBits)
      this.renderer.vertex(x, y, 0)
      this.renderer.color(colorBits)
      this.renderer.vertex(x + cx, y + cy, 0)
    }
    val temp: scala.Float = cx
    cx = 0
    cy = 0
    this.renderer.color(colorBits)
    this.renderer.vertex(x + cx, y + cy, 0)
  }
  def circle(x: scala.Float, y: scala.Float, radius: scala.Float): scala.Unit = {
    this.circle(x, y, radius, java.lang.Math.max(1, (6 * java.lang.Math.cbrt(radius).asInstanceOf[scala.Float]).asInstanceOf[scala.Int].asInstanceOf[scala.Int]))
  }
  def circle(x: scala.Float, y: scala.Float, radius: scala.Float, segments$arg: scala.Int): scala.Unit = {
    var segments: scala.Int = segments$arg
    if (segments <= 0) {
      throw new java.lang.IllegalArgumentException("segments must be > 0.")
    } else ()
    val colorBits: scala.Float = this.color.toFloatBits()
    val angle: scala.Float = (2 * com.badlogic.gdx.math.MathUtils.PI) / segments
    val cos: scala.Float = com.badlogic.gdx.math.MathUtils.cos(angle)
    val sin: scala.Float = com.badlogic.gdx.math.MathUtils.sin(angle)
    var cx: scala.Float = radius
    var cy: scala.Float = 0
    if (this.shapeType == com.badlogic.gdx.graphics.glutils.ShapeRenderer.ShapeType.Line) {
      this.check(com.badlogic.gdx.graphics.glutils.ShapeRenderer.ShapeType.Line, com.badlogic.gdx.graphics.glutils.ShapeRenderer.ShapeType.Filled, (segments * 2) + 2);
      { var i: scala.Int = 0; while (i < segments) { {
        this.renderer.color(colorBits)
        this.renderer.vertex(x + cx, y + cy, 0)
        val temp: scala.Float = cx
        cx = (cos * cx) - (sin * cy)
        cy = (sin * temp) + (cos * cy)
        this.renderer.color(colorBits)
        this.renderer.vertex(x + cx, y + cy, 0)
      }; i = i + 1 } }
      this.renderer.color(colorBits)
      this.renderer.vertex(x + cx, y + cy, 0)
    } else {
      this.check(com.badlogic.gdx.graphics.glutils.ShapeRenderer.ShapeType.Line, com.badlogic.gdx.graphics.glutils.ShapeRenderer.ShapeType.Filled, (segments * 3) + 3)
      segments = segments - 1;
      { var i: scala.Int = 0; while (i < segments) { {
        this.renderer.color(colorBits)
        this.renderer.vertex(x, y, 0)
        this.renderer.color(colorBits)
        this.renderer.vertex(x + cx, y + cy, 0)
        val temp: scala.Float = cx
        cx = (cos * cx) - (sin * cy)
        cy = (sin * temp) + (cos * cy)
        this.renderer.color(colorBits)
        this.renderer.vertex(x + cx, y + cy, 0)
      }; i = i + 1 } }
      this.renderer.color(colorBits)
      this.renderer.vertex(x, y, 0)
      this.renderer.color(colorBits)
      this.renderer.vertex(x + cx, y + cy, 0)
    }
    val temp: scala.Float = cx
    cx = radius
    cy = 0
    this.renderer.color(colorBits)
    this.renderer.vertex(x + cx, y + cy, 0)
  }
  def ellipse(x: scala.Float, y: scala.Float, width: scala.Float, height: scala.Float): scala.Unit = {
    (this.ellipse: (scala.Float, scala.Float, scala.Float, scala.Float, scala.Int) => scala.Unit)(x, y, width, height, java.lang.Math.max(1, (12 * java.lang.Math.cbrt(java.lang.Math.max(width * 0.5f, height * 0.5f)).asInstanceOf[scala.Float]).asInstanceOf[scala.Int].asInstanceOf[scala.Int]))
  }
  def ellipse(x: scala.Float, y: scala.Float, width: scala.Float, height: scala.Float, segments: scala.Int): scala.Unit = {
    if (segments <= 0) {
      throw new java.lang.IllegalArgumentException("segments must be > 0.")
    } else ()
    this.check(com.badlogic.gdx.graphics.glutils.ShapeRenderer.ShapeType.Line, com.badlogic.gdx.graphics.glutils.ShapeRenderer.ShapeType.Filled, segments * 3)
    val colorBits: scala.Float = this.color.toFloatBits()
    val angle: scala.Float = (2 * com.badlogic.gdx.math.MathUtils.PI) / segments
    val cx: scala.Float = x + (width / 2)
    val cy: scala.Float = y + (height / 2)
    if (this.shapeType == com.badlogic.gdx.graphics.glutils.ShapeRenderer.ShapeType.Line) {
      { var i: scala.Int = 0; while (i < segments) { {
        this.renderer.color(colorBits)
        this.renderer.vertex(cx + ((width * 0.5f) * com.badlogic.gdx.math.MathUtils.cos(i * angle)), cy + ((height * 0.5f) * com.badlogic.gdx.math.MathUtils.sin(i * angle)), 0)
        this.renderer.color(colorBits)
        this.renderer.vertex(cx + ((width * 0.5f) * com.badlogic.gdx.math.MathUtils.cos((i + 1) * angle)), cy + ((height * 0.5f) * com.badlogic.gdx.math.MathUtils.sin((i + 1) * angle)), 0)
      }; i = i + 1 } }
    } else {
      { var i: scala.Int = 0; while (i < segments) { {
        this.renderer.color(colorBits)
        this.renderer.vertex(cx + ((width * 0.5f) * com.badlogic.gdx.math.MathUtils.cos(i * angle)), cy + ((height * 0.5f) * com.badlogic.gdx.math.MathUtils.sin(i * angle)), 0)
        this.renderer.color(colorBits)
        this.renderer.vertex(cx, cy, 0)
        this.renderer.color(colorBits)
        this.renderer.vertex(cx + ((width * 0.5f) * com.badlogic.gdx.math.MathUtils.cos((i + 1) * angle)), cy + ((height * 0.5f) * com.badlogic.gdx.math.MathUtils.sin((i + 1) * angle)), 0)
      }; i = i + 1 } }
    }
  }
  def ellipse(x: scala.Float, y: scala.Float, width: scala.Float, height: scala.Float, rotation: scala.Float): scala.Unit = {
    this.ellipse(x, y, width, height, rotation, java.lang.Math.max(1, (12 * java.lang.Math.cbrt(java.lang.Math.max(width * 0.5f, height * 0.5f)).asInstanceOf[scala.Float]).asInstanceOf[scala.Int].asInstanceOf[scala.Int]))
  }
  def ellipse(x: scala.Float, y: scala.Float, width: scala.Float, height: scala.Float, rotation$arg: scala.Float, segments: scala.Int): scala.Unit = {
    var rotation: scala.Float = rotation$arg
    if (segments <= 0) {
      throw new java.lang.IllegalArgumentException("segments must be > 0.")
    } else ()
    this.check(com.badlogic.gdx.graphics.glutils.ShapeRenderer.ShapeType.Line, com.badlogic.gdx.graphics.glutils.ShapeRenderer.ShapeType.Filled, segments * 3)
    val colorBits: scala.Float = this.color.toFloatBits()
    val angle: scala.Float = (2 * com.badlogic.gdx.math.MathUtils.PI) / segments
    rotation = (com.badlogic.gdx.math.MathUtils.PI * rotation) / 180.0f
    val sin: scala.Float = com.badlogic.gdx.math.MathUtils.sin(rotation)
    val cos: scala.Float = com.badlogic.gdx.math.MathUtils.cos(rotation)
    val cx: scala.Float = x + (width / 2)
    val cy: scala.Float = y + (height / 2)
    var x1: scala.Float = width * 0.5f
    var y1: scala.Float = 0
    if (this.shapeType == com.badlogic.gdx.graphics.glutils.ShapeRenderer.ShapeType.Line) {
      { var i: scala.Int = 0; while (i < segments) { {
        this.renderer.color(colorBits)
        this.renderer.vertex((cx + (cos * x1)) - (sin * y1), (cy + (sin * x1)) + (cos * y1), 0)
        x1 = (width * 0.5f) * com.badlogic.gdx.math.MathUtils.cos((i + 1) * angle)
        y1 = (height * 0.5f) * com.badlogic.gdx.math.MathUtils.sin((i + 1) * angle)
        this.renderer.color(colorBits)
        this.renderer.vertex((cx + (cos * x1)) - (sin * y1), (cy + (sin * x1)) + (cos * y1), 0)
      }; i = i + 1 } }
    } else {
      { var i: scala.Int = 0; while (i < segments) { {
        this.renderer.color(colorBits)
        this.renderer.vertex((cx + (cos * x1)) - (sin * y1), (cy + (sin * x1)) + (cos * y1), 0)
        this.renderer.color(colorBits)
        this.renderer.vertex(cx, cy, 0)
        x1 = (width * 0.5f) * com.badlogic.gdx.math.MathUtils.cos((i + 1) * angle)
        y1 = (height * 0.5f) * com.badlogic.gdx.math.MathUtils.sin((i + 1) * angle)
        this.renderer.color(colorBits)
        this.renderer.vertex((cx + (cos * x1)) - (sin * y1), (cy + (sin * x1)) + (cos * y1), 0)
      }; i = i + 1 } }
    }
  }
  def cone(x: scala.Float, y: scala.Float, z: scala.Float, radius: scala.Float, height: scala.Float): scala.Unit = {
    this.cone(x, y, z, radius, height, java.lang.Math.max(1, (4 * java.lang.Math.sqrt(radius).asInstanceOf[scala.Float]).asInstanceOf[scala.Int].asInstanceOf[scala.Int]))
  }
  def cone(x: scala.Float, y: scala.Float, z: scala.Float, radius: scala.Float, height: scala.Float, segments$arg: scala.Int): scala.Unit = {
    var segments: scala.Int = segments$arg
    if (segments <= 0) {
      throw new java.lang.IllegalArgumentException("segments must be > 0.")
    } else ()
    this.check(com.badlogic.gdx.graphics.glutils.ShapeRenderer.ShapeType.Line, com.badlogic.gdx.graphics.glutils.ShapeRenderer.ShapeType.Filled, (segments * 4) + 2)
    val colorBits: scala.Float = this.color.toFloatBits()
    val angle: scala.Float = (2 * com.badlogic.gdx.math.MathUtils.PI) / segments
    val cos: scala.Float = com.badlogic.gdx.math.MathUtils.cos(angle)
    val sin: scala.Float = com.badlogic.gdx.math.MathUtils.sin(angle)
    var cx: scala.Float = radius
    var cy: scala.Float = 0
    if (this.shapeType == com.badlogic.gdx.graphics.glutils.ShapeRenderer.ShapeType.Line) {
      { var i: scala.Int = 0; while (i < segments) { {
        this.renderer.color(colorBits)
        this.renderer.vertex(x + cx, y + cy, z)
        this.renderer.color(colorBits)
        this.renderer.vertex(x, y, z + height)
        this.renderer.color(colorBits)
        this.renderer.vertex(x + cx, y + cy, z)
        val temp: scala.Float = cx
        cx = (cos * cx) - (sin * cy)
        cy = (sin * temp) + (cos * cy)
        this.renderer.color(colorBits)
        this.renderer.vertex(x + cx, y + cy, z)
      }; i = i + 1 } }
      this.renderer.color(colorBits)
      this.renderer.vertex(x + cx, y + cy, z)
    } else {
      segments = segments - 1;
      { var i: scala.Int = 0; while (i < segments) { {
        this.renderer.color(colorBits)
        this.renderer.vertex(x, y, z)
        this.renderer.color(colorBits)
        this.renderer.vertex(x + cx, y + cy, z)
        val temp: scala.Float = cx
        val temp2: scala.Float = cy
        cx = (cos * cx) - (sin * cy)
        cy = (sin * temp) + (cos * cy)
        this.renderer.color(colorBits)
        this.renderer.vertex(x + cx, y + cy, z)
        this.renderer.color(colorBits)
        this.renderer.vertex(x + temp, y + temp2, z)
        this.renderer.color(colorBits)
        this.renderer.vertex(x + cx, y + cy, z)
        this.renderer.color(colorBits)
        this.renderer.vertex(x, y, z + height)
      }; i = i + 1 } }
      this.renderer.color(colorBits)
      this.renderer.vertex(x, y, z)
      this.renderer.color(colorBits)
      this.renderer.vertex(x + cx, y + cy, z)
    }
    val temp: scala.Float = cx
    val temp2: scala.Float = cy
    cx = radius
    cy = 0
    this.renderer.color(colorBits)
    this.renderer.vertex(x + cx, y + cy, z)
    if (this.shapeType != com.badlogic.gdx.graphics.glutils.ShapeRenderer.ShapeType.Line) {
      this.renderer.color(colorBits)
      this.renderer.vertex(x + temp, y + temp2, z)
      this.renderer.color(colorBits)
      this.renderer.vertex(x + cx, y + cy, z)
      this.renderer.color(colorBits)
      this.renderer.vertex(x, y, z + height)
    } else ()
  }
  def polygon(vertices: scala.Array[scala.Float], offset: scala.Int, count: scala.Int): scala.Unit = {
    if (count < 6) {
      throw new java.lang.IllegalArgumentException("Polygons must contain at least 3 points.")
    } else ()
    if ((count % 2) != 0) {
      throw new java.lang.IllegalArgumentException("Polygons must have an even number of vertices.")
    } else ()
    this.check(com.badlogic.gdx.graphics.glutils.ShapeRenderer.ShapeType.Line, null, count)
    val colorBits: scala.Float = this.color.toFloatBits()
    val firstX: scala.Float = vertices(0)
    val firstY: scala.Float = vertices(1);
    { var i: scala.Int = offset; val n: scala.Int = offset + count; while (i < n) { {
      val x1: scala.Float = vertices(i)
      val y1: scala.Float = vertices(i + 1)
      var x2: scala.Float = 0.0f
      var y2: scala.Float = 0.0f
      if ((i + 2) >= count) {
        x2 = firstX
        y2 = firstY
      } else {
        x2 = vertices(i + 2)
        y2 = vertices(i + 3)
      }
      this.renderer.color(colorBits)
      this.renderer.vertex(x1, y1, 0)
      this.renderer.color(colorBits)
      this.renderer.vertex(x2, y2, 0)
    }; i = i + 2 } }
  }
  def polygon(vertices: scala.Array[scala.Float]): scala.Unit = {
    this.polygon(vertices, 0, vertices.length)
  }
  def polyline(vertices: scala.Array[scala.Float], offset: scala.Int, count: scala.Int): scala.Unit = {
    if (count < 4) {
      throw new java.lang.IllegalArgumentException("Polylines must contain at least 2 points.")
    } else ()
    if ((count % 2) != 0) {
      throw new java.lang.IllegalArgumentException("Polylines must have an even number of vertices.")
    } else ()
    this.check(com.badlogic.gdx.graphics.glutils.ShapeRenderer.ShapeType.Line, null, count)
    val colorBits: scala.Float = this.color.toFloatBits();
    { var i: scala.Int = offset; val n: scala.Int = (offset + count) - 2; while (i < n) { {
      val x1: scala.Float = vertices(i)
      val y1: scala.Float = vertices(i + 1)
      var x2: scala.Float = 0.0f
      var y2: scala.Float = 0.0f
      x2 = vertices(i + 2)
      y2 = vertices(i + 3)
      this.renderer.color(colorBits)
      this.renderer.vertex(x1, y1, 0)
      this.renderer.color(colorBits)
      this.renderer.vertex(x2, y2, 0)
    }; i = i + 2 } }
  }
  def polyline(vertices: scala.Array[scala.Float]): scala.Unit = {
    this.polyline(vertices, 0, vertices.length)
  }
  final def check(preferred: com.badlogic.gdx.graphics.glutils.ShapeRenderer.ShapeType, other: com.badlogic.gdx.graphics.glutils.ShapeRenderer.ShapeType, newVertices: scala.Int): scala.Unit = {
    if (this.shapeType == null) {
      throw new java.lang.IllegalStateException("begin must be called first.")
    } else ()
    if ((this.shapeType != preferred) && (this.shapeType != other)) {
      if (!this.autoShapeType) {
        if (other == null) {
          throw new java.lang.IllegalStateException(("Must call begin(ShapeType." + preferred) + ").")
        } else {
          throw new java.lang.IllegalStateException(((("Must call begin(ShapeType." + preferred) + ") or begin(ShapeType.") + other) + ").")
        }
      } else ()
      this.`end`()
      this.begin(preferred)
    } else {
      if (this.matrixDirty) {
        val `type`: com.badlogic.gdx.graphics.glutils.ShapeRenderer.ShapeType = this.shapeType
        this.`end`()
        this.begin(`type`)
      } else {
        if ((this.renderer.getMaxVertices() - this.renderer.getNumVertices()) < newVertices) {
          val `type`: com.badlogic.gdx.graphics.glutils.ShapeRenderer.ShapeType = this.shapeType
          this.`end`()
          this.begin(`type`)
        } else ()
      }
    }
  }
  def `end`(): scala.Unit = {
    this.renderer.`end`()
    this.shapeType = null
  }
  def flush(): scala.Unit = {
    val `type`: com.badlogic.gdx.graphics.glutils.ShapeRenderer.ShapeType = this.shapeType
    if (`type` == null) {
      return
    } else ()
    this.`end`()
    this.begin(`type`)
  }
  def getCurrentType(): com.badlogic.gdx.graphics.glutils.ShapeRenderer.ShapeType = {
    return this.shapeType
  }
  def getRenderer(): com.badlogic.gdx.graphics.glutils.ImmediateModeRenderer = {
    return this.renderer
  }
  def isDrawing(): scala.Boolean = {
    return this.shapeType != null
  }
  def dispose(): scala.Unit = {
    this.renderer.dispose()
  }
}
object ShapeRenderer {
  sealed abstract class ShapeType(var glType: scala.Int) {
    def getGlType(): scala.Int = {
      return this.glType
    }
    def name(): java.lang.String = this.toString()
  }
  object ShapeType {
    case object Point extends ShapeType(com.badlogic.gdx.graphics.GL20.GL_POINTS)
    case object Line extends ShapeType(com.badlogic.gdx.graphics.GL20.GL_LINES)
    case object Filled extends ShapeType(com.badlogic.gdx.graphics.GL20.GL_TRIANGLES)
    def values(): scala.Array[ShapeType] = scala.Array(Point, Line, Filled)
    def valueOf(name: java.lang.String): ShapeType = name match {
      case "Point" => Point
      case "Line" => Line
      case "Filled" => Filled
      case _ => throw new java.lang.IllegalArgumentException(name)
    }
  }
}