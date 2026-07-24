package com.badlogic.gdx

abstract class ApplicationAdapter extends com.badlogic.gdx.ApplicationListener {
  def create(): scala.Unit = {
    ()
  }
  def resize(width: scala.Int, height: scala.Int): scala.Unit = {
    ()
  }
  def render(): scala.Unit = {
    ()
  }
  def pause(): scala.Unit = {
    ()
  }
  def resume(): scala.Unit = {
    ()
  }
  def dispose(): scala.Unit = {
    ()
  }
}