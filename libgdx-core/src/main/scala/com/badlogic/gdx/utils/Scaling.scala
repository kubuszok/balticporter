package com.badlogic.gdx.utils

abstract class Scaling {
  def apply(sourceWidth: scala.Float, sourceHeight: scala.Float, targetWidth: scala.Float, targetHeight: scala.Float): com.badlogic.gdx.math.Vector2
}
object Scaling {
  final val temp: com.badlogic.gdx.math.Vector2 = new com.badlogic.gdx.math.Vector2()
  final val fit: Scaling = new Scaling() {
    override def apply(sourceWidth: scala.Float, sourceHeight: scala.Float, targetWidth: scala.Float, targetHeight: scala.Float): com.badlogic.gdx.math.Vector2 = {
      val targetRatio: scala.Float = targetHeight / targetWidth
      val sourceRatio: scala.Float = sourceHeight / sourceWidth
      val scale: scala.Float = if (targetRatio > sourceRatio) targetWidth / sourceWidth else targetHeight / sourceHeight
      Scaling.temp.x = sourceWidth * scale
      Scaling.temp.y = sourceHeight * scale
      return Scaling.temp
    }
  }
  final val contain: Scaling = new Scaling() {
    override def apply(sourceWidth: scala.Float, sourceHeight: scala.Float, targetWidth: scala.Float, targetHeight: scala.Float): com.badlogic.gdx.math.Vector2 = {
      val targetRatio: scala.Float = targetHeight / targetWidth
      val sourceRatio: scala.Float = sourceHeight / sourceWidth
      var scale: scala.Float = if (targetRatio > sourceRatio) targetWidth / sourceWidth else targetHeight / sourceHeight
      if (scale > 1) {
        scale = 1
      } else ()
      Scaling.temp.x = sourceWidth * scale
      Scaling.temp.y = sourceHeight * scale
      return Scaling.temp
    }
  }
  final val fill: Scaling = new Scaling() {
    override def apply(sourceWidth: scala.Float, sourceHeight: scala.Float, targetWidth: scala.Float, targetHeight: scala.Float): com.badlogic.gdx.math.Vector2 = {
      val targetRatio: scala.Float = targetHeight / targetWidth
      val sourceRatio: scala.Float = sourceHeight / sourceWidth
      val scale: scala.Float = if (targetRatio < sourceRatio) targetWidth / sourceWidth else targetHeight / sourceHeight
      Scaling.temp.x = sourceWidth * scale
      Scaling.temp.y = sourceHeight * scale
      return Scaling.temp
    }
  }
  final val fillX: Scaling = new Scaling() {
    override def apply(sourceWidth: scala.Float, sourceHeight: scala.Float, targetWidth: scala.Float, targetHeight: scala.Float): com.badlogic.gdx.math.Vector2 = {
      val scale: scala.Float = targetWidth / sourceWidth
      Scaling.temp.x = sourceWidth * scale
      Scaling.temp.y = sourceHeight * scale
      return Scaling.temp
    }
  }
  final val fillY: Scaling = new Scaling() {
    override def apply(sourceWidth: scala.Float, sourceHeight: scala.Float, targetWidth: scala.Float, targetHeight: scala.Float): com.badlogic.gdx.math.Vector2 = {
      val scale: scala.Float = targetHeight / sourceHeight
      Scaling.temp.x = sourceWidth * scale
      Scaling.temp.y = sourceHeight * scale
      return Scaling.temp
    }
  }
  final val stretch: Scaling = new Scaling() {
    override def apply(sourceWidth: scala.Float, sourceHeight: scala.Float, targetWidth: scala.Float, targetHeight: scala.Float): com.badlogic.gdx.math.Vector2 = {
      Scaling.temp.x = targetWidth
      Scaling.temp.y = targetHeight
      return Scaling.temp
    }
  }
  final val stretchX: Scaling = new Scaling() {
    override def apply(sourceWidth: scala.Float, sourceHeight: scala.Float, targetWidth: scala.Float, targetHeight: scala.Float): com.badlogic.gdx.math.Vector2 = {
      Scaling.temp.x = targetWidth
      Scaling.temp.y = sourceHeight
      return Scaling.temp
    }
  }
  final val stretchY: Scaling = new Scaling() {
    override def apply(sourceWidth: scala.Float, sourceHeight: scala.Float, targetWidth: scala.Float, targetHeight: scala.Float): com.badlogic.gdx.math.Vector2 = {
      Scaling.temp.x = sourceWidth
      Scaling.temp.y = targetHeight
      return Scaling.temp
    }
  }
  final val none: Scaling = new Scaling() {
    override def apply(sourceWidth: scala.Float, sourceHeight: scala.Float, targetWidth: scala.Float, targetHeight: scala.Float): com.badlogic.gdx.math.Vector2 = {
      Scaling.temp.x = sourceWidth
      Scaling.temp.y = sourceHeight
      return Scaling.temp
    }
  }
}