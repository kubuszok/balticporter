package com.badlogic.gdx.scenes.scene2d

sealed abstract class Touchable {
  def name(): java.lang.String = this.toString()
}
object Touchable {
  case object enabled extends Touchable
  case object disabled extends Touchable
  case object childrenOnly extends Touchable
  def values(): scala.Array[Touchable] = scala.Array(enabled, disabled, childrenOnly)
  def valueOf(name: java.lang.String): Touchable = name match {
    case "enabled" => enabled
    case "disabled" => disabled
    case "childrenOnly" => childrenOnly
    case _ => throw new java.lang.IllegalArgumentException(name)
  }
}