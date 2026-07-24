package com.badlogic.gdx.scenes.scene2d.utils

class TextureRegionDrawable extends com.badlogic.gdx.scenes.scene2d.utils.BaseDrawable with com.badlogic.gdx.scenes.scene2d.utils.TransformDrawable {
  private var region: com.badlogic.gdx.graphics.g2d.TextureRegion = null.asInstanceOf[com.badlogic.gdx.graphics.g2d.TextureRegion]
  def this(texture: com.badlogic.gdx.graphics.Texture) = {
    this()
    this.setRegion(new com.badlogic.gdx.graphics.g2d.TextureRegion(texture))
  }
  def this(region: com.badlogic.gdx.graphics.g2d.TextureRegion) = {
    this()
    this.setRegion(region)
  }
  def this(drawable: TextureRegionDrawable) = {
    this()
    this.setRegion(drawable.region)
  }
  def draw(batch: com.badlogic.gdx.graphics.g2d.Batch, x: scala.Float, y: scala.Float, width: scala.Float, height: scala.Float): scala.Unit = {
    batch.draw(this.region, x, y, width, height)
  }
  def draw(batch: com.badlogic.gdx.graphics.g2d.Batch, x: scala.Float, y: scala.Float, originX: scala.Float, originY: scala.Float, width: scala.Float, height: scala.Float, scaleX: scala.Float, scaleY: scala.Float, rotation: scala.Float): scala.Unit = {
    batch.draw(this.region, x, y, originX, originY, width, height, scaleX, scaleY, rotation)
  }
  def setRegion(region: com.badlogic.gdx.graphics.g2d.TextureRegion): scala.Unit = {
    this.region = region
    if (region != null) {
      this.setMinWidth(region.getRegionWidth())
      this.setMinHeight(region.getRegionHeight())
    } else ()
  }
  def getRegion(): com.badlogic.gdx.graphics.g2d.TextureRegion = {
    return this.region
  }
  def tint(tint: com.badlogic.gdx.graphics.Color): com.badlogic.gdx.scenes.scene2d.utils.Drawable = {
    var sprite: com.badlogic.gdx.graphics.g2d.Sprite = null.asInstanceOf[com.badlogic.gdx.graphics.g2d.Sprite]
    if (this.region.isInstanceOf[com.badlogic.gdx.graphics.g2d.TextureAtlas#AtlasRegion]) {
      sprite = new com.badlogic.gdx.graphics.g2d.TextureAtlas#AtlasSprite(this.region.asInstanceOf[com.badlogic.gdx.graphics.g2d.TextureAtlas#AtlasRegion])
    } else {
      sprite = new com.badlogic.gdx.graphics.g2d.Sprite(this.region)
    }
    sprite.setColor(tint)
    sprite.setSize(this.getMinWidth(), this.getMinHeight())
    val drawable: com.badlogic.gdx.scenes.scene2d.utils.SpriteDrawable = new com.badlogic.gdx.scenes.scene2d.utils.SpriteDrawable(sprite)
    drawable.setLeftWidth(this.getLeftWidth())
    drawable.setRightWidth(this.getRightWidth())
    drawable.setTopHeight(this.getTopHeight())
    drawable.setBottomHeight(this.getBottomHeight())
    return drawable
  }
}