package com.badlogic.gdx.scenes.scene2d

sealed abstract class Touchable
object Touchable {
  case object enabled extends Touchable
  case object disabled extends Touchable
  case object childrenOnly extends Touchable
  def values(): Array[Touchable] = Array(enabled, disabled, childrenOnly)
}