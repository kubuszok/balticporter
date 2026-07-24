package com.badlogic.gdx.graphics.g2d

class NinePatch {
  private var texture: com.badlogic.gdx.graphics.Texture = null.asInstanceOf[com.badlogic.gdx.graphics.Texture]
  private var bottomLeft: scala.Int = 0
  private var bottomCenter: scala.Int = 0
  private var bottomRight: scala.Int = 0
  private var middleLeft: scala.Int = 0
  private var middleCenter: scala.Int = 0
  private var middleRight: scala.Int = 0
  private var topLeft: scala.Int = 0
  private var topCenter: scala.Int = 0
  private var topRight: scala.Int = 0
  private var leftWidth: scala.Float = 0.0f
  private var rightWidth: scala.Float = 0.0f
  private var middleWidth: scala.Float = 0.0f
  private var middleHeight: scala.Float = 0.0f
  private var topHeight: scala.Float = 0.0f
  private var bottomHeight: scala.Float = 0.0f
  private var vertices: scala.Array[scala.Float] = new scala.Array[scala.Float]((9 * 4) * 5)
  private var idx: scala.Int = 0
  private final val color: com.badlogic.gdx.graphics.Color = new com.badlogic.gdx.graphics.Color(com.badlogic.gdx.graphics.Color.WHITE)
  private var padLeft: scala.Float = -1
  private var padRight: scala.Float = -1
  private var padTop: scala.Float = -1
  private var padBottom: scala.Float = -1
  def this(region: com.badlogic.gdx.graphics.g2d.TextureRegion, left: scala.Int, right: scala.Int, top: scala.Int, bottom: scala.Int) = {
    this()
    if (region == null) {
      throw new java.lang.IllegalArgumentException("region cannot be null.")
    } else ()
    val sign: scala.Int = if (region.isFlipY()) -1 else 1
    val middleWidth: scala.Int = (region.getRegionWidth() - left) - right
    val middleHeight: scala.Int = (region.getRegionHeight() - top) - bottom
    val patches: scala.Array[com.badlogic.gdx.graphics.g2d.TextureRegion] = new scala.Array[com.badlogic.gdx.graphics.g2d.TextureRegion](9)
    if (top > 0) {
      if (left > 0) {
        patches(NinePatch.TOP_LEFT) = new com.badlogic.gdx.graphics.g2d.TextureRegion(region, 0, 0, left, sign * top)
      } else ()
      if (middleWidth > 0) {
        patches(NinePatch.TOP_CENTER) = new com.badlogic.gdx.graphics.g2d.TextureRegion(region, left, 0, middleWidth, sign * top)
      } else ()
      if (right > 0) {
        patches(NinePatch.TOP_RIGHT) = new com.badlogic.gdx.graphics.g2d.TextureRegion(region, left + middleWidth, 0, right, sign * top)
      } else ()
    } else ()
    if (middleHeight > 0) {
      if (left > 0) {
        patches(NinePatch.MIDDLE_LEFT) = new com.badlogic.gdx.graphics.g2d.TextureRegion(region, 0, sign * top, left, sign * middleHeight)
      } else ()
      if (middleWidth > 0) {
        patches(NinePatch.MIDDLE_CENTER) = new com.badlogic.gdx.graphics.g2d.TextureRegion(region, left, sign * top, middleWidth, sign * middleHeight)
      } else ()
      if (right > 0) {
        patches(NinePatch.MIDDLE_RIGHT) = new com.badlogic.gdx.graphics.g2d.TextureRegion(region, left + middleWidth, sign * top, right, sign * middleHeight)
      } else ()
    } else ()
    if (bottom > 0) {
      if (left > 0) {
        patches(NinePatch.BOTTOM_LEFT) = new com.badlogic.gdx.graphics.g2d.TextureRegion(region, 0, sign * (top + middleHeight), left, sign * bottom)
      } else ()
      if (middleWidth > 0) {
        patches(NinePatch.BOTTOM_CENTER) = new com.badlogic.gdx.graphics.g2d.TextureRegion(region, left, sign * (top + middleHeight), middleWidth, sign * bottom)
      } else ()
      if (right > 0) {
        patches(NinePatch.BOTTOM_RIGHT) = new com.badlogic.gdx.graphics.g2d.TextureRegion(region, left + middleWidth, sign * (top + middleHeight), right, sign * bottom)
      } else ()
    } else ()
    if ((left == 0) && (middleWidth == 0)) {
      patches(NinePatch.TOP_CENTER) = patches(NinePatch.TOP_RIGHT)
      patches(NinePatch.MIDDLE_CENTER) = patches(NinePatch.MIDDLE_RIGHT)
      patches(NinePatch.BOTTOM_CENTER) = patches(NinePatch.BOTTOM_RIGHT)
      patches(NinePatch.TOP_RIGHT) = null
      patches(NinePatch.MIDDLE_RIGHT) = null
      patches(NinePatch.BOTTOM_RIGHT) = null
    } else ()
    if ((top == 0) && (middleHeight == 0)) {
      patches(NinePatch.MIDDLE_LEFT) = patches(NinePatch.BOTTOM_LEFT)
      patches(NinePatch.MIDDLE_CENTER) = patches(NinePatch.BOTTOM_CENTER)
      patches(NinePatch.MIDDLE_RIGHT) = patches(NinePatch.BOTTOM_RIGHT)
      patches(NinePatch.BOTTOM_LEFT) = null
      patches(NinePatch.BOTTOM_CENTER) = null
      patches(NinePatch.BOTTOM_RIGHT) = null
    } else ()
    this.load(patches)
  }
  def this(ninePatch: NinePatch, color: com.badlogic.gdx.graphics.Color) = {
    this()
    this.texture = ninePatch.texture
    this.bottomLeft = ninePatch.bottomLeft
    this.bottomCenter = ninePatch.bottomCenter
    this.bottomRight = ninePatch.bottomRight
    this.middleLeft = ninePatch.middleLeft
    this.middleCenter = ninePatch.middleCenter
    this.middleRight = ninePatch.middleRight
    this.topLeft = ninePatch.topLeft
    this.topCenter = ninePatch.topCenter
    this.topRight = ninePatch.topRight
    this.leftWidth = ninePatch.leftWidth
    this.rightWidth = ninePatch.rightWidth
    this.middleWidth = ninePatch.middleWidth
    this.middleHeight = ninePatch.middleHeight
    this.topHeight = ninePatch.topHeight
    this.bottomHeight = ninePatch.bottomHeight
    this.padLeft = ninePatch.padLeft
    this.padTop = ninePatch.padTop
    this.padBottom = ninePatch.padBottom
    this.padRight = ninePatch.padRight
    this.vertices = new scala.Array[scala.Float](ninePatch.vertices.length)
    java.lang.System.arraycopy(ninePatch.vertices, 0, this.vertices, 0, ninePatch.vertices.length)
    this.idx = ninePatch.idx
    this.color.set(color)
  }
  def this(region: com.badlogic.gdx.graphics.g2d.TextureRegion) = {
    this()
    this.load(scala.Array[com.badlogic.gdx.graphics.g2d.TextureRegion](null, null, null, null, region, null, null, null, null))
  }
  def this(patches: scala.Array[com.badlogic.gdx.graphics.g2d.TextureRegion]) = {
    this()
    if ((patches == null) || (patches.length != 9)) {
      throw new java.lang.IllegalArgumentException("NinePatch needs nine TextureRegions")
    } else ()
    this.load(patches)
    if ((((patches(NinePatch.TOP_LEFT) != null) && (patches(NinePatch.TOP_LEFT).getRegionWidth() != this.leftWidth)) || ((patches(NinePatch.MIDDLE_LEFT) != null) && (patches(NinePatch.MIDDLE_LEFT).getRegionWidth() != this.leftWidth))) || ((patches(NinePatch.BOTTOM_LEFT) != null) && (patches(NinePatch.BOTTOM_LEFT).getRegionWidth() != this.leftWidth))) {
      throw new com.badlogic.gdx.utils.GdxRuntimeException("Left side patches must have the same width")
    } else ()
    if ((((patches(NinePatch.TOP_RIGHT) != null) && (patches(NinePatch.TOP_RIGHT).getRegionWidth() != this.rightWidth)) || ((patches(NinePatch.MIDDLE_RIGHT) != null) && (patches(NinePatch.MIDDLE_RIGHT).getRegionWidth() != this.rightWidth))) || ((patches(NinePatch.BOTTOM_RIGHT) != null) && (patches(NinePatch.BOTTOM_RIGHT).getRegionWidth() != this.rightWidth))) {
      throw new com.badlogic.gdx.utils.GdxRuntimeException("Right side patches must have the same width")
    } else ()
    if ((((patches(NinePatch.BOTTOM_LEFT) != null) && (patches(NinePatch.BOTTOM_LEFT).getRegionHeight() != this.bottomHeight)) || ((patches(NinePatch.BOTTOM_CENTER) != null) && (patches(NinePatch.BOTTOM_CENTER).getRegionHeight() != this.bottomHeight))) || ((patches(NinePatch.BOTTOM_RIGHT) != null) && (patches(NinePatch.BOTTOM_RIGHT).getRegionHeight() != this.bottomHeight))) {
      throw new com.badlogic.gdx.utils.GdxRuntimeException("Bottom side patches must have the same height")
    } else ()
    if ((((patches(NinePatch.TOP_LEFT) != null) && (patches(NinePatch.TOP_LEFT).getRegionHeight() != this.topHeight)) || ((patches(NinePatch.TOP_CENTER) != null) && (patches(NinePatch.TOP_CENTER).getRegionHeight() != this.topHeight))) || ((patches(NinePatch.TOP_RIGHT) != null) && (patches(NinePatch.TOP_RIGHT).getRegionHeight() != this.topHeight))) {
      throw new com.badlogic.gdx.utils.GdxRuntimeException("Top side patches must have the same height")
    } else ()
  }
  def this(texture: com.badlogic.gdx.graphics.Texture, left: scala.Int, right: scala.Int, top: scala.Int, bottom: scala.Int) = {
    this(new com.badlogic.gdx.graphics.g2d.TextureRegion(texture), left, right, top, bottom)
  }
  def this(texture: com.badlogic.gdx.graphics.Texture, color: com.badlogic.gdx.graphics.Color) = {
    this(texture)
    this.setColor(color)
  }
  def this(region: com.badlogic.gdx.graphics.g2d.TextureRegion, color: com.badlogic.gdx.graphics.Color) = {
    this(region)
    this.setColor(color)
  }
  def this(texture: com.badlogic.gdx.graphics.Texture) = {
    this(new com.badlogic.gdx.graphics.g2d.TextureRegion(texture))
  }
  def this(ninePatch: NinePatch) = {
    this(ninePatch, ninePatch.color)
  }
  private def load(patches: scala.Array[com.badlogic.gdx.graphics.g2d.TextureRegion]): scala.Unit = {
    if (patches(NinePatch.BOTTOM_LEFT) != null) {
      this.bottomLeft = this.add(patches(NinePatch.BOTTOM_LEFT), false, false)
      this.leftWidth = patches(NinePatch.BOTTOM_LEFT).getRegionWidth()
      this.bottomHeight = patches(NinePatch.BOTTOM_LEFT).getRegionHeight()
    } else {
      this.bottomLeft = -1
    }
    if (patches(NinePatch.BOTTOM_CENTER) != null) {
      this.bottomCenter = this.add(patches(NinePatch.BOTTOM_CENTER), (patches(NinePatch.BOTTOM_LEFT) != null) || (patches(NinePatch.BOTTOM_RIGHT) != null), false)
      this.middleWidth = java.lang.Math.max(this.middleWidth, patches(NinePatch.BOTTOM_CENTER).getRegionWidth())
      this.bottomHeight = java.lang.Math.max(this.bottomHeight, patches(NinePatch.BOTTOM_CENTER).getRegionHeight())
    } else {
      this.bottomCenter = -1
    }
    if (patches(NinePatch.BOTTOM_RIGHT) != null) {
      this.bottomRight = this.add(patches(NinePatch.BOTTOM_RIGHT), false, false)
      this.rightWidth = java.lang.Math.max(this.rightWidth, patches(NinePatch.BOTTOM_RIGHT).getRegionWidth())
      this.bottomHeight = java.lang.Math.max(this.bottomHeight, patches(NinePatch.BOTTOM_RIGHT).getRegionHeight())
    } else {
      this.bottomRight = -1
    }
    if (patches(NinePatch.MIDDLE_LEFT) != null) {
      this.middleLeft = this.add(patches(NinePatch.MIDDLE_LEFT), false, (patches(NinePatch.TOP_LEFT) != null) || (patches(NinePatch.BOTTOM_LEFT) != null))
      this.leftWidth = java.lang.Math.max(this.leftWidth, patches(NinePatch.MIDDLE_LEFT).getRegionWidth())
      this.middleHeight = java.lang.Math.max(this.middleHeight, patches(NinePatch.MIDDLE_LEFT).getRegionHeight())
    } else {
      this.middleLeft = -1
    }
    if (patches(NinePatch.MIDDLE_CENTER) != null) {
      this.middleCenter = this.add(patches(NinePatch.MIDDLE_CENTER), (patches(NinePatch.MIDDLE_LEFT) != null) || (patches(NinePatch.MIDDLE_RIGHT) != null), (patches(NinePatch.TOP_CENTER) != null) || (patches(NinePatch.BOTTOM_CENTER) != null))
      this.middleWidth = java.lang.Math.max(this.middleWidth, patches(NinePatch.MIDDLE_CENTER).getRegionWidth())
      this.middleHeight = java.lang.Math.max(this.middleHeight, patches(NinePatch.MIDDLE_CENTER).getRegionHeight())
    } else {
      this.middleCenter = -1
    }
    if (patches(NinePatch.MIDDLE_RIGHT) != null) {
      this.middleRight = this.add(patches(NinePatch.MIDDLE_RIGHT), false, (patches(NinePatch.TOP_RIGHT) != null) || (patches(NinePatch.BOTTOM_RIGHT) != null))
      this.rightWidth = java.lang.Math.max(this.rightWidth, patches(NinePatch.MIDDLE_RIGHT).getRegionWidth())
      this.middleHeight = java.lang.Math.max(this.middleHeight, patches(NinePatch.MIDDLE_RIGHT).getRegionHeight())
    } else {
      this.middleRight = -1
    }
    if (patches(NinePatch.TOP_LEFT) != null) {
      this.topLeft = this.add(patches(NinePatch.TOP_LEFT), false, false)
      this.leftWidth = java.lang.Math.max(this.leftWidth, patches(NinePatch.TOP_LEFT).getRegionWidth())
      this.topHeight = java.lang.Math.max(this.topHeight, patches(NinePatch.TOP_LEFT).getRegionHeight())
    } else {
      this.topLeft = -1
    }
    if (patches(NinePatch.TOP_CENTER) != null) {
      this.topCenter = this.add(patches(NinePatch.TOP_CENTER), (patches(NinePatch.TOP_LEFT) != null) || (patches(NinePatch.TOP_RIGHT) != null), false)
      this.middleWidth = java.lang.Math.max(this.middleWidth, patches(NinePatch.TOP_CENTER).getRegionWidth())
      this.topHeight = java.lang.Math.max(this.topHeight, patches(NinePatch.TOP_CENTER).getRegionHeight())
    } else {
      this.topCenter = -1
    }
    if (patches(NinePatch.TOP_RIGHT) != null) {
      this.topRight = this.add(patches(NinePatch.TOP_RIGHT), false, false)
      this.rightWidth = java.lang.Math.max(this.rightWidth, patches(NinePatch.TOP_RIGHT).getRegionWidth())
      this.topHeight = java.lang.Math.max(this.topHeight, patches(NinePatch.TOP_RIGHT).getRegionHeight())
    } else {
      this.topRight = -1
    }
    if (this.idx < this.vertices.length) {
      val newVertices: scala.Array[scala.Float] = new scala.Array[scala.Float](this.idx)
      java.lang.System.arraycopy(this.vertices, 0, newVertices, 0, this.idx)
      this.vertices = newVertices
    } else ()
  }
  private def add(region: com.badlogic.gdx.graphics.g2d.TextureRegion, isStretchW: scala.Boolean, isStretchH: scala.Boolean): scala.Int = {
    if (this.texture == null) {
      this.texture = region.getTexture()
    } else {
      if (this.texture != region.getTexture()) {
        throw new java.lang.IllegalArgumentException("All regions must be from the same texture.")
      } else ()
    }
    var u: scala.Float = region.u
    var v: scala.Float = region.v2
    var u2: scala.Float = region.u2
    var v2: scala.Float = region.v
    if ((this.texture.getMagFilter() == com.badlogic.gdx.graphics.Texture.TextureFilter.Linear) || (this.texture.getMinFilter() == com.badlogic.gdx.graphics.Texture.TextureFilter.Linear)) {
      if (isStretchW) {
        val halfTexelWidth: scala.Float = (0.5f * 1.0f) / this.texture.getWidth()
        u = u + halfTexelWidth
        u2 = u2 - halfTexelWidth
      } else ()
      if (isStretchH) {
        val halfTexelHeight: scala.Float = (0.5f * 1.0f) / this.texture.getHeight()
        v = v - halfTexelHeight
        v2 = v2 + halfTexelHeight
      } else ()
    } else ()
    val vertices: scala.Array[scala.Float] = this.vertices
    val i: scala.Int = this.idx
    vertices(i + 3) = u
    vertices(i + 4) = v
    vertices(i + 8) = u
    vertices(i + 9) = v2
    vertices(i + 13) = u2
    vertices(i + 14) = v2
    vertices(i + 18) = u2
    vertices(i + 19) = v
    this.idx = this.idx + 20
    return i
  }
  private def set(idx: scala.Int, x: scala.Float, y: scala.Float, width: scala.Float, height: scala.Float, color: scala.Float): scala.Unit = {
    val fx2: scala.Float = x + width
    val fy2: scala.Float = y + height
    val vertices: scala.Array[scala.Float] = this.vertices
    vertices(idx) = x
    vertices(idx + 1) = y
    vertices(idx + 2) = color
    vertices(idx + 5) = x
    vertices(idx + 6) = fy2
    vertices(idx + 7) = color
    vertices(idx + 10) = fx2
    vertices(idx + 11) = fy2
    vertices(idx + 12) = color
    vertices(idx + 15) = fx2
    vertices(idx + 16) = y
    vertices(idx + 17) = color
  }
  private def prepareVertices(batch: com.badlogic.gdx.graphics.g2d.Batch, x: scala.Float, y: scala.Float, width: scala.Float, height: scala.Float): scala.Unit = {
    val centerX: scala.Float = x + this.leftWidth
    val centerY: scala.Float = y + this.bottomHeight
    val centerWidth: scala.Float = (width - this.rightWidth) - this.leftWidth
    val centerHeight: scala.Float = (height - this.topHeight) - this.bottomHeight
    val rightX: scala.Float = (x + width) - this.rightWidth
    val topY: scala.Float = (y + height) - this.topHeight
    val c: scala.Float = NinePatch.tmpDrawColor.set(this.color).mul(batch.getColor()).toFloatBits()
    if (this.bottomLeft != (-1)) {
      this.set(this.bottomLeft, x, y, this.leftWidth, this.bottomHeight, c)
    } else ()
    if (this.bottomCenter != (-1)) {
      this.set(this.bottomCenter, centerX, y, centerWidth, this.bottomHeight, c)
    } else ()
    if (this.bottomRight != (-1)) {
      this.set(this.bottomRight, rightX, y, this.rightWidth, this.bottomHeight, c)
    } else ()
    if (this.middleLeft != (-1)) {
      this.set(this.middleLeft, x, centerY, this.leftWidth, centerHeight, c)
    } else ()
    if (this.middleCenter != (-1)) {
      this.set(this.middleCenter, centerX, centerY, centerWidth, centerHeight, c)
    } else ()
    if (this.middleRight != (-1)) {
      this.set(this.middleRight, rightX, centerY, this.rightWidth, centerHeight, c)
    } else ()
    if (this.topLeft != (-1)) {
      this.set(this.topLeft, x, topY, this.leftWidth, this.topHeight, c)
    } else ()
    if (this.topCenter != (-1)) {
      this.set(this.topCenter, centerX, topY, centerWidth, this.topHeight, c)
    } else ()
    if (this.topRight != (-1)) {
      this.set(this.topRight, rightX, topY, this.rightWidth, this.topHeight, c)
    } else ()
  }
  def draw(batch: com.badlogic.gdx.graphics.g2d.Batch, x: scala.Float, y: scala.Float, width: scala.Float, height: scala.Float): scala.Unit = {
    this.prepareVertices(batch, x, y, width, height)
    batch.draw(this.texture, this.vertices, 0, this.idx)
  }
  def draw(batch: com.badlogic.gdx.graphics.g2d.Batch, x: scala.Float, y: scala.Float, originX: scala.Float, originY: scala.Float, width: scala.Float, height: scala.Float, scaleX: scala.Float, scaleY: scala.Float, rotation: scala.Float): scala.Unit = {
    this.prepareVertices(batch, x, y, width, height)
    val worldOriginX: scala.Float = x + originX
    val worldOriginY: scala.Float = y + originY
    val n: scala.Int = this.idx
    val vertices: scala.Array[scala.Float] = this.vertices
    if (rotation != 0) {
      { var i: scala.Int = 0; while (i < n) { {
        val vx: scala.Float = (vertices(i) - worldOriginX) * scaleX
        val vy: scala.Float = (vertices(i + 1) - worldOriginY) * scaleY
        val cos: scala.Float = com.badlogic.gdx.math.MathUtils.cosDeg(rotation)
        val sin: scala.Float = com.badlogic.gdx.math.MathUtils.sinDeg(rotation)
        vertices(i) = ((cos * vx) - (sin * vy)) + worldOriginX
        vertices(i + 1) = ((sin * vx) + (cos * vy)) + worldOriginY
      }; i = i + 5 } }
    } else {
      if ((scaleX != 1) || (scaleY != 1)) {
        { var i: scala.Int = 0; while (i < n) { {
          vertices(i) = ((vertices(i) - worldOriginX) * scaleX) + worldOriginX
          vertices(i + 1) = ((vertices(i + 1) - worldOriginY) * scaleY) + worldOriginY
        }; i = i + 5 } }
      } else ()
    }
    batch.draw(this.texture, vertices, 0, n)
  }
  def setColor(color: com.badlogic.gdx.graphics.Color): scala.Unit = {
    this.color.set(color)
  }
  def getColor(): com.badlogic.gdx.graphics.Color = {
    return this.color
  }
  def getLeftWidth(): scala.Float = {
    return this.leftWidth
  }
  def setLeftWidth(leftWidth: scala.Float): scala.Unit = {
    this.leftWidth = leftWidth
  }
  def getRightWidth(): scala.Float = {
    return this.rightWidth
  }
  def setRightWidth(rightWidth: scala.Float): scala.Unit = {
    this.rightWidth = rightWidth
  }
  def getTopHeight(): scala.Float = {
    return this.topHeight
  }
  def setTopHeight(topHeight: scala.Float): scala.Unit = {
    this.topHeight = topHeight
  }
  def getBottomHeight(): scala.Float = {
    return this.bottomHeight
  }
  def setBottomHeight(bottomHeight: scala.Float): scala.Unit = {
    this.bottomHeight = bottomHeight
  }
  def getMiddleWidth(): scala.Float = {
    return this.middleWidth
  }
  def setMiddleWidth(middleWidth: scala.Float): scala.Unit = {
    this.middleWidth = middleWidth
  }
  def getMiddleHeight(): scala.Float = {
    return this.middleHeight
  }
  def setMiddleHeight(middleHeight: scala.Float): scala.Unit = {
    this.middleHeight = middleHeight
  }
  def getTotalWidth(): scala.Float = {
    return (this.leftWidth + this.middleWidth) + this.rightWidth
  }
  def getTotalHeight(): scala.Float = {
    return (this.topHeight + this.middleHeight) + this.bottomHeight
  }
  def setPadding(left: scala.Float, right: scala.Float, top: scala.Float, bottom: scala.Float): scala.Unit = {
    this.padLeft = left
    this.padRight = right
    this.padTop = top
    this.padBottom = bottom
  }
  def getPadLeft(): scala.Float = {
    if (this.padLeft == (-1)) {
      return this.getLeftWidth()
    } else ()
    return this.padLeft
  }
  def setPadLeft(left: scala.Float): scala.Unit = {
    this.padLeft = left
  }
  def getPadRight(): scala.Float = {
    if (this.padRight == (-1)) {
      return this.getRightWidth()
    } else ()
    return this.padRight
  }
  def setPadRight(right: scala.Float): scala.Unit = {
    this.padRight = right
  }
  def getPadTop(): scala.Float = {
    if (this.padTop == (-1)) {
      return this.getTopHeight()
    } else ()
    return this.padTop
  }
  def setPadTop(top: scala.Float): scala.Unit = {
    this.padTop = top
  }
  def getPadBottom(): scala.Float = {
    if (this.padBottom == (-1)) {
      return this.getBottomHeight()
    } else ()
    return this.padBottom
  }
  def setPadBottom(bottom: scala.Float): scala.Unit = {
    this.padBottom = bottom
  }
  def scale(scaleX: scala.Float, scaleY: scala.Float): scala.Unit = {
    this.leftWidth = this.leftWidth * scaleX
    this.rightWidth = this.rightWidth * scaleX
    this.topHeight = this.topHeight * scaleY
    this.bottomHeight = this.bottomHeight * scaleY
    this.middleWidth = this.middleWidth * scaleX
    this.middleHeight = this.middleHeight * scaleY
    if (this.padLeft != (-1)) {
      this.padLeft = this.padLeft * scaleX
    } else ()
    if (this.padRight != (-1)) {
      this.padRight = this.padRight * scaleX
    } else ()
    if (this.padTop != (-1)) {
      this.padTop = this.padTop * scaleY
    } else ()
    if (this.padBottom != (-1)) {
      this.padBottom = this.padBottom * scaleY
    } else ()
  }
  def getTexture(): com.badlogic.gdx.graphics.Texture = {
    return this.texture
  }
}
object NinePatch {
  final val TOP_LEFT: scala.Int = 0
  final val TOP_CENTER: scala.Int = 1
  final val TOP_RIGHT: scala.Int = 2
  final val MIDDLE_LEFT: scala.Int = 3
  final val MIDDLE_CENTER: scala.Int = 4
  final val MIDDLE_RIGHT: scala.Int = 5
  final val BOTTOM_LEFT: scala.Int = 6
  final val BOTTOM_CENTER: scala.Int = 7
  final val BOTTOM_RIGHT: scala.Int = 8
  private final val tmpDrawColor: com.badlogic.gdx.graphics.Color = new com.badlogic.gdx.graphics.Color()
}