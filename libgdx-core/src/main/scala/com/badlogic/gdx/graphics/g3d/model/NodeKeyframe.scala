package com.badlogic.gdx.graphics.g3d.model

class NodeKeyframe[T <: java.lang.Object](t: scala.Float, v: T) {
  var keytime: scala.Float = 0.0f
  var value: T = null.asInstanceOf[T]
  this.keytime = t
  this.value = v
}