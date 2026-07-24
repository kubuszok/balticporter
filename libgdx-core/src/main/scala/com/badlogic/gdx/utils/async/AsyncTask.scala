package com.badlogic.gdx.utils.async

trait AsyncTask[T] {
  def call(): T
}