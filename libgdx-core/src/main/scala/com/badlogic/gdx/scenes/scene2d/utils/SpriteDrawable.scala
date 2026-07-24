package com.badlogic.gdx.scenes.scene2d.utils

class SpriteDrawable extends com.badlogic.gdx.scenes.scene2d.utils.BaseDrawable with com.badlogic.gdx.scenes.scene2d.utils.TransformDrawable {
  private var sprite: com.badlogic.gdx.graphics.g2d.Sprite = null.asInstanceOf[com.badlogic.gdx.graphics.g2d.Sprite]
  def this(sprite: com.badlogic.gdx.graphics.g2d.Sprite) = {
    this()
    this.setSprite(sprite)
  }
  def this(drawable: SpriteDrawable) = {
    this()
    this.setSprite(drawable.sprite)
  }
  def draw(batch: com.badlogic.gdx.graphics.g2d.Batch, x: scala.Float, y: scala.Float, width: scala.Float, height: scala.Float): scala.Unit = {
    val spriteColor: com.badlogic.gdx.graphics.Color = this.sprite.getColor()
    val oldColor: scala.Float = this.sprite.getPackedColor()
    this.sprite.setColor(spriteColor.mul(batch.getColor()))
    this.sprite.setRotation(0)
    this.sprite.setScale(1, 1)
    this.sprite.setBounds(x, y, width, height)
    this.sprite.draw(batch)
    this.sprite.setPackedColor(oldColor)
  }
  def draw(batch: com.badlogic.gdx.graphics.g2d.Batch, x: scala.Float, y: scala.Float, originX: scala.Float, originY: scala.Float, width: scala.Float, height: scala.Float, scaleX: scala.Float, scaleY: scala.Float, rotation: scala.Float): scala.Unit = {
    val spriteColor: com.badlogic.gdx.graphics.Color = this.sprite.getColor()
    val oldColor: scala.Float = this.sprite.getPackedColor()
    this.sprite.setColor(spriteColor.mul(batch.getColor()))
    this.sprite.setOrigin(originX, originY)
    this.sprite.setRotation(rotation)
    this.sprite.setScale(scaleX, scaleY)
    this.sprite.setBounds(x, y, width, height)
    this.sprite.draw(batch)
    this.sprite.setPackedColor(oldColor)
  }
  def setSprite(sprite: com.badlogic.gdx.graphics.g2d.Sprite): scala.Unit = {
    this.sprite = sprite
    this.setMinWidth(sprite.getWidth())
    this.setMinHeight(sprite.getHeight())
  }
  def getSprite(): com.badlogic.gdx.graphics.g2d.Sprite = {
    return this.sprite
  }
  def tint(tint: com.badlogic.gdx.graphics.Color): SpriteDrawable = {
    var newSprite: com.badlogic.gdx.graphics.g2d.Sprite = null.asInstanceOf[com.badlogic.gdx.graphics.g2d.Sprite]
    if (this.sprite.isInstanceOf[com.badlogic.gdx.graphics.g2d.TextureAtlas.AtlasSprite]) {
      newSprite = new com.badlogic.gdx.graphics.g2d.TextureAtlas.AtlasSprite(this.sprite.asInstanceOf[com.badlogic.gdx.graphics.g2d.TextureAtlas.AtlasSprite])
    } else {
      newSprite = new com.badlogic.gdx.graphics.g2d.Sprite(this.sprite)
    }
    newSprite.setColor(tint)
    newSprite.setSize(this.getMinWidth(), this.getMinHeight())
    val drawable: SpriteDrawable = new SpriteDrawable(newSprite)
    drawable.setLeftWidth(this.getLeftWidth())
    drawable.setRightWidth(this.getRightWidth())
    drawable.setTopHeight(this.getTopHeight())
    drawable.setBottomHeight(this.getBottomHeight())
    return drawable
  }
}