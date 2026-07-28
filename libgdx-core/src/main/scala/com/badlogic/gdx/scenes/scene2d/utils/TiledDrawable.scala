package com.badlogic.gdx.scenes.scene2d.utils

class TiledDrawable extends com.badlogic.gdx.scenes.scene2d.utils.TextureRegionDrawable {
  private final val color: com.badlogic.gdx.graphics.Color = new com.badlogic.gdx.graphics.Color(1, 1, 1, 1)
  private var scale: scala.Float = 1
  private var align: scala.Int = com.badlogic.gdx.utils.Align.bottomLeft
  def this(region: com.badlogic.gdx.graphics.g2d.TextureRegion) = {
    this()
    this.setRegion(region)
  }
  def this(drawable: com.badlogic.gdx.scenes.scene2d.utils.TextureRegionDrawable) = {
    this()
    if (drawable.isInstanceOf[com.badlogic.gdx.scenes.scene2d.utils.BaseDrawable]) {
      this.name = drawable.asInstanceOf[com.badlogic.gdx.scenes.scene2d.utils.BaseDrawable].getName()
    } else ()
    this.leftWidth = drawable.getLeftWidth()
    this.rightWidth = drawable.getRightWidth()
    this.topHeight = drawable.getTopHeight()
    this.bottomHeight = drawable.getBottomHeight()
    this.minWidth = drawable.getMinWidth()
    this.minHeight = drawable.getMinHeight()
    this.setRegion(drawable.region)
  }
  override def draw(batch: com.badlogic.gdx.graphics.g2d.Batch, x: scala.Float, y: scala.Float, width: scala.Float, height: scala.Float): scala.Unit = {
    val oldColor: scala.Float = batch.getPackedColor()
    batch.setColor(batch.getColor().mul(this.color))
    TiledDrawable.draw(batch, this.getRegion(), x, y, width, height, this.scale, this.align)
    batch.setPackedColor(oldColor)
  }
  override def draw(batch: com.badlogic.gdx.graphics.g2d.Batch, x: scala.Float, y: scala.Float, originX: scala.Float, originY: scala.Float, width: scala.Float, height: scala.Float, scaleX: scala.Float, scaleY: scala.Float, rotation: scala.Float): scala.Unit = {
    throw new java.lang.UnsupportedOperationException()
  }
  def getColor(): com.badlogic.gdx.graphics.Color = {
    return this.color
  }
  def setScale(scale: scala.Float): scala.Unit = {
    this.scale = scale
  }
  def getScale(): scala.Float = {
    return this.scale
  }
  def getAlign(): scala.Int = {
    return this.align
  }
  def setAlign(align: scala.Int): scala.Unit = {
    this.align = align
  }
  override def tint(tint: com.badlogic.gdx.graphics.Color): com.badlogic.gdx.scenes.scene2d.utils.Drawable = {
    val drawable: TiledDrawable = new TiledDrawable(this)
    drawable.color.set(tint)
    drawable.setLeftWidth(this.getLeftWidth())
    drawable.setRightWidth(this.getRightWidth())
    drawable.setTopHeight(this.getTopHeight())
    drawable.setBottomHeight(this.getBottomHeight())
    return drawable
  }
}
object TiledDrawable {
  def draw(batch: com.badlogic.gdx.graphics.g2d.Batch, textureRegion: com.badlogic.gdx.graphics.g2d.TextureRegion, x: scala.Float, y: scala.Float, width: scala.Float, height: scala.Float, scale: scala.Float, align: scala.Int): scala.Unit = {
    val regionWidth: scala.Float = textureRegion.getRegionWidth() * scale
    val regionHeight: scala.Float = textureRegion.getRegionHeight() * scale
    val texture: com.badlogic.gdx.graphics.Texture = textureRegion.getTexture()
    val textureWidth: scala.Float = texture.getWidth() * scale
    val textureHeight: scala.Float = texture.getHeight() * scale
    val u: scala.Float = textureRegion.getU()
    val v: scala.Float = textureRegion.getV()
    val u2: scala.Float = textureRegion.getU2()
    val v2: scala.Float = textureRegion.getV2()
    var fullX: scala.Int = (width / regionWidth).asInstanceOf[scala.Int].asInstanceOf[scala.Int]
    var leftPartialWidth: scala.Float = 0.0f
    var rightPartialWidth: scala.Float = 0.0f
    if (com.badlogic.gdx.utils.Align.isLeft(align)) {
      leftPartialWidth = 0.0f
      rightPartialWidth = width - (regionWidth * fullX)
    } else {
      if (com.badlogic.gdx.utils.Align.isRight(align)) {
        leftPartialWidth = width - (regionWidth * fullX)
        rightPartialWidth = 0.0f
      } else {
        if (fullX != 0) {
          fullX = if ((fullX % 2) == 1) fullX else fullX - 1
          val leftRight: scala.Float = 0.5f * (width - (regionWidth * fullX))
          leftPartialWidth = leftRight
          rightPartialWidth = leftRight
        } else {
          leftPartialWidth = 0.0f
          rightPartialWidth = 0.0f
        }
      }
    }
    var fullY: scala.Int = (height / regionHeight).asInstanceOf[scala.Int].asInstanceOf[scala.Int]
    var topPartialHeight: scala.Float = 0.0f
    var bottomPartialHeight: scala.Float = 0.0f
    if (com.badlogic.gdx.utils.Align.isTop(align)) {
      topPartialHeight = 0.0f
      bottomPartialHeight = height - (regionHeight * fullY)
    } else {
      if (com.badlogic.gdx.utils.Align.isBottom(align)) {
        topPartialHeight = height - (regionHeight * fullY)
        bottomPartialHeight = 0.0f
      } else {
        if (fullY != 0) {
          fullY = if ((fullY % 2) == 1) fullY else fullY - 1
          val topBottom: scala.Float = 0.5f * (height - (regionHeight * fullY))
          topPartialHeight = topBottom
          bottomPartialHeight = topBottom
        } else {
          topPartialHeight = 0.0f
          bottomPartialHeight = 0.0f
        }
      }
    }
    var drawX: scala.Float = x
    var drawY: scala.Float = y
    if (leftPartialWidth > 0.0f) {
      val leftEdgeU: scala.Float = u2 - (leftPartialWidth / textureWidth)
      if (bottomPartialHeight > 0.0f) {
        val leftBottomV: scala.Float = v + (bottomPartialHeight / textureHeight)
        batch.draw(texture, drawX, drawY, leftPartialWidth, bottomPartialHeight, leftEdgeU, leftBottomV, u2, v)
        drawY = drawY + bottomPartialHeight
      } else ()
      if ((fullY == 0) && com.badlogic.gdx.utils.Align.isCenterVertical(align)) {
        val vOffset: scala.Float = (0.5f * (v2 - v)) * (1.0f - (height / regionHeight))
        val leftCenterV: scala.Float = v2 - vOffset
        val leftCenterV2: scala.Float = v + vOffset
        batch.draw(texture, drawX, drawY, leftPartialWidth, height, leftEdgeU, leftCenterV, u2, leftCenterV2)
        drawY = drawY + height
      } else {
        { var i: scala.Int = 0; while (i < fullY) { {
          batch.draw(texture, drawX, drawY, leftPartialWidth, regionHeight, leftEdgeU, v2, u2, v)
          drawY = drawY + regionHeight
        }; i = i + 1 } }
      }
      if (topPartialHeight > 0.0f) {
        val leftTopV: scala.Float = v2 - (topPartialHeight / textureHeight)
        batch.draw(texture, drawX, drawY, leftPartialWidth, topPartialHeight, leftEdgeU, v2, u2, leftTopV)
      } else ()
    } else ();
    {
      if (bottomPartialHeight > 0.0f) {
        drawX = x + leftPartialWidth
        drawY = y
        val centerBottomV: scala.Float = v + (bottomPartialHeight / textureHeight)
        if ((fullX == 0) && com.badlogic.gdx.utils.Align.isCenterHorizontal(align)) {
          val uOffset: scala.Float = (0.5f * (u2 - u)) * (1.0f - (width / regionWidth))
          val centerBottomU: scala.Float = u + uOffset
          val centerBottomU2: scala.Float = u2 - uOffset
          batch.draw(texture, drawX, drawY, width, bottomPartialHeight, centerBottomU, centerBottomV, centerBottomU2, v)
          drawX = drawX + width
        } else {
          { var i: scala.Int = 0; while (i < fullX) { {
            batch.draw(texture, drawX, drawY, regionWidth, bottomPartialHeight, u, centerBottomV, u2, v)
            drawX = drawX + regionWidth
          }; i = i + 1 } }
        }
      } else ();
      {
        drawX = x + leftPartialWidth
        val originalFullX: scala.Int = fullX
        val originalFullY: scala.Int = fullY
        var centerCenterDrawWidth: scala.Float = regionWidth
        var centerCenterDrawHeight: scala.Float = regionHeight
        var centerCenterU: scala.Float = u
        var centerCenterU2: scala.Float = u2
        var centerCenterV: scala.Float = v2
        var centerCenterV2: scala.Float = v
        if ((fullX == 0) && com.badlogic.gdx.utils.Align.isCenterHorizontal(align)) {
          fullX = 1
          centerCenterDrawWidth = width
          val uOffset: scala.Float = (0.5f * (u2 - u)) * (1.0f - (width / regionWidth))
          centerCenterU = u + uOffset
          centerCenterU2 = u2 - uOffset
        } else ()
        if ((fullY == 0) && com.badlogic.gdx.utils.Align.isCenterVertical(align)) {
          fullY = 1
          centerCenterDrawHeight = height
          val vOffset: scala.Float = (0.5f * (v2 - v)) * (1.0f - (height / regionHeight))
          centerCenterV = v2 - vOffset
          centerCenterV2 = v + vOffset
        } else ();
        { var i: scala.Int = 0; while (i < fullX) { {
          drawY = y + bottomPartialHeight;
          { var ii: scala.Int = 0; while (ii < fullY) { {
            batch.draw(texture, drawX, drawY, centerCenterDrawWidth, centerCenterDrawHeight, centerCenterU, centerCenterV, centerCenterU2, centerCenterV2)
            drawY = drawY + centerCenterDrawHeight
          }; ii = ii + 1 } }
          drawX = drawX + centerCenterDrawWidth
        }; i = i + 1 } }
        fullX = originalFullX
        fullY = originalFullY
      }
      if (topPartialHeight > 0.0f) {
        drawX = x + leftPartialWidth
        val centerTopV: scala.Float = v2 - (topPartialHeight / textureHeight)
        if ((fullX == 0) && com.badlogic.gdx.utils.Align.isCenterHorizontal(align)) {
          val uOffset: scala.Float = (0.5f * (u2 - u)) * (1.0f - (width / regionWidth))
          val centerTopU: scala.Float = u + uOffset
          val centerTopU2: scala.Float = u2 - uOffset
          batch.draw(texture, drawX, drawY, width, topPartialHeight, centerTopU, v2, centerTopU2, centerTopV)
          drawX = drawX + width
        } else {
          { var i: scala.Int = 0; while (i < fullX) { {
            batch.draw(texture, drawX, drawY, regionWidth, topPartialHeight, u, v2, u2, centerTopV)
            drawX = drawX + regionWidth
          }; i = i + 1 } }
        }
      } else ()
    }
    if (rightPartialWidth > 0.0f) {
      drawY = y
      val rightEdgeU2: scala.Float = u + (rightPartialWidth / textureWidth)
      if (bottomPartialHeight > 0.0f) {
        val rightBottomV: scala.Float = v + (bottomPartialHeight / textureHeight)
        batch.draw(texture, drawX, drawY, rightPartialWidth, bottomPartialHeight, u, rightBottomV, rightEdgeU2, v)
        drawY = drawY + bottomPartialHeight
      } else ()
      if ((fullY == 0) && com.badlogic.gdx.utils.Align.isCenterVertical(align)) {
        val vOffset: scala.Float = (0.5f * (v2 - v)) * (1.0f - (height / regionHeight))
        val rightCenterV: scala.Float = v2 - vOffset
        val rightCenterV2: scala.Float = v + vOffset
        batch.draw(texture, drawX, drawY, rightPartialWidth, height, u, rightCenterV, rightEdgeU2, rightCenterV2)
        drawY = drawY + height
      } else {
        { var i: scala.Int = 0; while (i < fullY) { {
          batch.draw(texture, drawX, drawY, rightPartialWidth, regionHeight, u, v2, rightEdgeU2, v)
          drawY = drawY + regionHeight
        }; i = i + 1 } }
      }
      if (topPartialHeight > 0.0f) {
        val rightTopV: scala.Float = v2 - (topPartialHeight / textureHeight)
        batch.draw(texture, drawX, drawY, rightPartialWidth, topPartialHeight, u, v2, rightEdgeU2, rightTopV)
      } else ()
    } else ()
  }
}