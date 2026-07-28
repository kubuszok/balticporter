package com.badlogic.gdx.utils.async

trait AsyncTask[T <: java.lang.Object] {
  def call(): T
}