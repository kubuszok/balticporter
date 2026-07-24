package com.badlogic.gdx.scenes.scene2d.utils

trait TransformDrawable extends com.badlogic.gdx.scenes.scene2d.utils.Drawable {
  def draw(batch: com.badlogic.gdx.graphics.g2d.Batch, x: scala.Float, y: scala.Float, originX: scala.Float, originY: scala.Float, width: scala.Float, height: scala.Float, scaleX: scala.Float, scaleY: scala.Float, rotation: scala.Float): scala.Unit
}