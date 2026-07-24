package com.badlogic.gdx.graphics.g3d.model

class NodeKeyframe[T] {
  var keytime: scala.Float = 0.0f
  var value: T = null.asInstanceOf[T]
  def this(t: scala.Float, v: T) = {
    this()
    this.keytime = t
    this.value = v
  }
}