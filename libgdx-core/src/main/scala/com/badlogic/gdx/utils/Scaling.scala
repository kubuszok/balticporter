package com.badlogic.gdx.utils

abstract class Scaling {
  def apply(sourceWidth: scala.Float, sourceHeight: scala.Float, targetWidth: scala.Float, targetHeight: scala.Float): com.badlogic.gdx.math.Vector2
}
object Scaling {
  final val temp: com.badlogic.gdx.math.Vector2 = new com.badlogic.gdx.math.Vector2()
  final val fit: Scaling = new Scaling()
  final val contain: Scaling = new Scaling()
  final val fill: Scaling = new Scaling()
  final val fillX: Scaling = new Scaling()
  final val fillY: Scaling = new Scaling()
  final val stretch: Scaling = new Scaling()
  final val stretchX: Scaling = new Scaling()
  final val stretchY: Scaling = new Scaling()
  final val none: Scaling = new Scaling()
}