package com.badlogic.gdx

trait LifecycleListener {
  def pause(): scala.Unit
  def resume(): scala.Unit
  def dispose(): scala.Unit
}