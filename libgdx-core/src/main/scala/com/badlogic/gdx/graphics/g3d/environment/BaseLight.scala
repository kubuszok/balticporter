package com.badlogic.gdx.graphics.g3d.environment

abstract class BaseLight[T <: BaseLight[T]] {
  final val color: com.badlogic.gdx.graphics.Color = new com.badlogic.gdx.graphics.Color(0, 0, 0, 1)
  def setColor(r: scala.Float, g: scala.Float, b: scala.Float, a: scala.Float): T = {
    this.color.set(r, g, b, a)
    return this.asInstanceOf[T]
  }
  def setColor(color: com.badlogic.gdx.graphics.Color): T = {
    this.color.set(color)
    return this.asInstanceOf[T]
  }
}