package com.badlogic.gdx.scenes.scene2d.ui

trait Styleable[T] {
  def getStyle(): T
  def setStyle(style: T): scala.Unit
}