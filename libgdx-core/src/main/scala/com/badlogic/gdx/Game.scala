package com.badlogic.gdx

abstract class Game extends com.badlogic.gdx.ApplicationListener {
  protected var screen: com.badlogic.gdx.Screen = null.asInstanceOf[com.badlogic.gdx.Screen]
  def dispose(): scala.Unit = {
    if (this.screen != null) {
      this.screen.hide()
    } else ()
  }
  def pause(): scala.Unit = {
    if (this.screen != null) {
      this.screen.pause()
    } else ()
  }
  def resume(): scala.Unit = {
    if (this.screen != null) {
      this.screen.resume()
    } else ()
  }
  def render(): scala.Unit = {
    if (this.screen != null) {
      this.screen.render(com.badlogic.gdx.Gdx.graphics.getDeltaTime())
    } else ()
  }
  def resize(width: scala.Int, height: scala.Int): scala.Unit = {
    if (this.screen != null) {
      this.screen.resize(width, height)
    } else ()
  }
  def setScreen(screen: com.badlogic.gdx.Screen): scala.Unit = {
    if (this.screen != null) {
      this.screen.hide()
    } else ()
    this.screen = screen
    if (this.screen != null) {
      this.screen.show()
      this.screen.resize(com.badlogic.gdx.Gdx.graphics.getWidth(), com.badlogic.gdx.Gdx.graphics.getHeight())
    } else ()
  }
  def getScreen(): com.badlogic.gdx.Screen = {
    return this.screen
  }
}