package com.badlogic.gdx.graphics.glutils

sealed abstract class HdpiMode
object HdpiMode {
  case object Logical extends HdpiMode
  case object Pixels extends HdpiMode
  def values(): Array[HdpiMode] = Array(Logical, Pixels)
}