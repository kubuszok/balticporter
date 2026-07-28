package com.badlogic.gdx.math

trait Path[T <: java.lang.Object] {
  def derivativeAt(out: T, t: scala.Float): T
  def valueAt(out: T, t: scala.Float): T
  def approximate(v: T): scala.Float
  def locate(v: T): scala.Float
  def approxLength(samples: scala.Int): scala.Float
}