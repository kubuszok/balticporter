package com.badlogic.gdx.graphics.g3d.model.data

class ModelNodeKeyframe[T <: java.lang.Object] {
  var keytime: scala.Float = 0.0f
  var value: T = null.asInstanceOf[T]
}