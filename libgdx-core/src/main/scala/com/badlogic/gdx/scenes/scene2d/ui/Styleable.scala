package com.badlogic.gdx.scenes.scene2d.ui

trait Styleable[T <: java.lang.Object] {
  def getStyle(): T
  def setStyle(style: T): scala.Unit
}