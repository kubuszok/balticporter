package com.badlogic.gdx

trait Screen extends com.badlogic.gdx.utils.Disposable {
  def show(): scala.Unit
  def render(delta: scala.Float): scala.Unit
  def resize(width: scala.Int, height: scala.Int): scala.Unit
  def pause(): scala.Unit
  def resume(): scala.Unit
  def hide(): scala.Unit
  override def dispose(): scala.Unit
}