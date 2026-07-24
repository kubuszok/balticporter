package com.badlogic.gdx.graphics.g2d

class TextureRegion {
  var texture: com.badlogic.gdx.graphics.Texture = null.asInstanceOf[com.badlogic.gdx.graphics.Texture]
  var u: scala.Float = 0.0f
  var v: scala.Float = 0.0f
  var u2: scala.Float = 0.0f
  var v2: scala.Float = 0.0f
  var regionWidth: scala.Int = 0
  var regionHeight: scala.Int = 0
  def this(texture: com.badlogic.gdx.graphics.Texture, x: scala.Int, y: scala.Int, width: scala.Int, height: scala.Int) = {
    this()
    this.texture = texture
    this.setRegion(x, y, width, height)
  }
  def this(texture: com.badlogic.gdx.graphics.Texture, u: scala.Float, v: scala.Float, u2: scala.Float, v2: scala.Float) = {
    this()
    this.texture = texture
    this.setRegion(u, v, u2, v2)
  }
  def this(region: TextureRegion, x: scala.Int, y: scala.Int, width: scala.Int, height: scala.Int) = {
    this()
    this.setRegion(region, x, y, width, height)
  }
  def this(texture: com.badlogic.gdx.graphics.Texture, width: scala.Int, height: scala.Int) = {
    this()
    this.texture = texture
    this.setRegion(0, 0, width, height)
  }
  def this(texture: com.badlogic.gdx.graphics.Texture) = {
    this()
    if (texture == null) {
      throw new java.lang.IllegalArgumentException("texture cannot be null.")
    } else ()
    this.texture = texture
    this.setRegion(0, 0, texture.getWidth(), texture.getHeight())
  }
  def this(region: TextureRegion) = {
    this()
    this.setRegion(region)
  }
  def setRegion(texture: com.badlogic.gdx.graphics.Texture): scala.Unit = {
    this.texture = texture
    this.setRegion(0, 0, texture.getWidth(), texture.getHeight())
  }
  def setRegion(x: scala.Int, y: scala.Int, width: scala.Int, height: scala.Int): scala.Unit = {
    val invTexWidth: scala.Float = 1.0f / this.texture.getWidth()
    val invTexHeight: scala.Float = 1.0f / this.texture.getHeight()
    this.setRegion(x * invTexWidth, y * invTexHeight, (x + width) * invTexWidth, (y + height) * invTexHeight)
    this.regionWidth = java.lang.Math.abs(width)
    this.regionHeight = java.lang.Math.abs(height)
  }
  def setRegion(u$arg: scala.Float, v$arg: scala.Float, u2$arg: scala.Float, v2$arg: scala.Float): scala.Unit = {
    var u: scala.Float = u$arg
    var v: scala.Float = v$arg
    var u2: scala.Float = u2$arg
    var v2: scala.Float = v2$arg
    val texWidth: scala.Int = this.texture.getWidth()
    val texHeight: scala.Int = this.texture.getHeight()
    this.regionWidth = java.lang.Math.round(java.lang.Math.abs(u2 - u) * texWidth)
    this.regionHeight = java.lang.Math.round(java.lang.Math.abs(v2 - v) * texHeight)
    if ((this.regionWidth == 1) && (this.regionHeight == 1)) {
      val adjustX: scala.Float = 0.25f / texWidth
      u = u + adjustX
      u2 = u2 - adjustX
      val adjustY: scala.Float = 0.25f / texHeight
      v = v + adjustY
      v2 = v2 - adjustY
    } else ()
    this.u = u
    this.v = v
    this.u2 = u2
    this.v2 = v2
  }
  def setRegion(region: TextureRegion): scala.Unit = {
    this.texture = region.texture
    this.setRegion(region.u, region.v, region.u2, region.v2)
  }
  def setRegion(region: TextureRegion, x: scala.Int, y: scala.Int, width: scala.Int, height: scala.Int): scala.Unit = {
    this.texture = region.texture
    this.setRegion(region.getRegionX() + x, region.getRegionY() + y, width, height)
  }
  def getTexture(): com.badlogic.gdx.graphics.Texture = {
    return this.texture
  }
  def setTexture(texture: com.badlogic.gdx.graphics.Texture): scala.Unit = {
    this.texture = texture
  }
  def getU(): scala.Float = {
    return this.u
  }
  def setU(u: scala.Float): scala.Unit = {
    this.u = u
    this.regionWidth = java.lang.Math.round(java.lang.Math.abs(this.u2 - u) * this.texture.getWidth())
  }
  def getV(): scala.Float = {
    return this.v
  }
  def setV(v: scala.Float): scala.Unit = {
    this.v = v
    this.regionHeight = java.lang.Math.round(java.lang.Math.abs(this.v2 - v) * this.texture.getHeight())
  }
  def getU2(): scala.Float = {
    return this.u2
  }
  def setU2(u2: scala.Float): scala.Unit = {
    this.u2 = u2
    this.regionWidth = java.lang.Math.round(java.lang.Math.abs(u2 - this.u) * this.texture.getWidth())
  }
  def getV2(): scala.Float = {
    return this.v2
  }
  def setV2(v2: scala.Float): scala.Unit = {
    this.v2 = v2
    this.regionHeight = java.lang.Math.round(java.lang.Math.abs(v2 - this.v) * this.texture.getHeight())
  }
  def getRegionX(): scala.Int = {
    return java.lang.Math.round(this.u * this.texture.getWidth())
  }
  def setRegionX(x: scala.Int): scala.Unit = {
    this.setU(x / this.texture.getWidth().asInstanceOf[scala.Float])
  }
  def getRegionY(): scala.Int = {
    return java.lang.Math.round(this.v * this.texture.getHeight())
  }
  def setRegionY(y: scala.Int): scala.Unit = {
    this.setV(y / this.texture.getHeight().asInstanceOf[scala.Float])
  }
  def getRegionWidth(): scala.Int = {
    return this.regionWidth
  }
  def setRegionWidth(width: scala.Int): scala.Unit = {
    if (this.isFlipX()) {
      this.setU(this.u2 + (width / this.texture.getWidth().asInstanceOf[scala.Float]))
    } else {
      this.setU2(this.u + (width / this.texture.getWidth().asInstanceOf[scala.Float]))
    }
  }
  def getRegionHeight(): scala.Int = {
    return this.regionHeight
  }
  def setRegionHeight(height: scala.Int): scala.Unit = {
    if (this.isFlipY()) {
      this.setV(this.v2 + (height / this.texture.getHeight().asInstanceOf[scala.Float]))
    } else {
      this.setV2(this.v + (height / this.texture.getHeight().asInstanceOf[scala.Float]))
    }
  }
  def flip(x: scala.Boolean, y: scala.Boolean): scala.Unit = {
    if (x) {
      val temp: scala.Float = this.u
      this.u = this.u2
      this.u2 = temp
    } else ()
    if (y) {
      val temp: scala.Float = this.v
      this.v = this.v2
      this.v2 = temp
    } else ()
  }
  def isFlipX(): scala.Boolean = {
    return this.u > this.u2
  }
  def isFlipY(): scala.Boolean = {
    return this.v > this.v2
  }
  def scroll(xAmount: scala.Float, yAmount: scala.Float): scala.Unit = {
    if (xAmount != 0) {
      val width: scala.Float = (this.u2 - this.u) * this.texture.getWidth()
      this.u = (this.u + xAmount) % 1
      this.u2 = this.u + (width / this.texture.getWidth())
    } else ()
    if (yAmount != 0) {
      val height: scala.Float = (this.v2 - this.v) * this.texture.getHeight()
      this.v = (this.v + yAmount) % 1
      this.v2 = this.v + (height / this.texture.getHeight())
    } else ()
  }
  def split(tileWidth: scala.Int, tileHeight: scala.Int): scala.Array[scala.Array[TextureRegion]] = {
    var x: scala.Int = this.getRegionX()
    var y: scala.Int = this.getRegionY()
    val width: scala.Int = this.regionWidth
    val height: scala.Int = this.regionHeight
    val rows: scala.Int = height / tileHeight
    val cols: scala.Int = width / tileWidth
    val startX: scala.Int = x
    val tiles: scala.Array[scala.Array[TextureRegion]] = new scala.Array[scala.Array[TextureRegion]](rows, cols);
    { var row: scala.Int = 0; while (row < rows) { {
      x = startX;
      { var col: scala.Int = 0; while (col < cols) { {
        tiles(row)(col) = new TextureRegion(this.texture, x, y, tileWidth, tileHeight)
      }; col = col + 1; x = x + tileWidth } }
    }; row = row + 1; y = y + tileHeight } }
    return tiles
  }
}
object TextureRegion {
  def split(texture: com.badlogic.gdx.graphics.Texture, tileWidth: scala.Int, tileHeight: scala.Int): scala.Array[scala.Array[TextureRegion]] = {
    val region: TextureRegion = new TextureRegion(texture)
    return region.split(tileWidth, tileHeight)
  }
}