package com.badlogic.gdx.graphics.glutils

sealed abstract class HdpiMode {
  def name(): java.lang.String = this.toString()
}
object HdpiMode {
  case object Logical extends HdpiMode
  case object Pixels extends HdpiMode
  def values(): scala.Array[HdpiMode] = scala.Array(Logical, Pixels)
  def valueOf(name: java.lang.String): HdpiMode = name match {
    case "Logical" => Logical
    case "Pixels" => Pixels
    case _ => throw new java.lang.IllegalArgumentException(name)
  }
}