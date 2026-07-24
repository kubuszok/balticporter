package com.badlogic.gdx.math

trait Shape2D {
  def contains(point: com.badlogic.gdx.math.Vector2): scala.Boolean
  def contains(x: scala.Float, y: scala.Float): scala.Boolean
}