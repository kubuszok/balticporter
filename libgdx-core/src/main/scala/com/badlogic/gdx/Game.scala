package com.badlogic.gdx

abstract class Game extends com.badlogic.gdx.ApplicationListener {
  var screen: com.badlogic.gdx.Screen = null.asInstanceOf[com.badlogic.gdx.Screen]
  @java.lang.Override
  def dispose(): scala.Unit = {
    if (this.screen != null) {
      this.screen.hide()
    } else ()
  }
  @java.lang.Override
  def pause(): scala.Unit = {
    if (this.screen != null) {
      this.screen.pause()
    } else ()
  }
  @java.lang.Override
  def resume(): scala.Unit = {
    if (this.screen != null) {
      this.screen.resume()
    } else ()
  }
  @java.lang.Override
  def render(): scala.Unit = {
    if (this.screen != null) {
      this.screen.render(com.badlogic.gdx.Gdx.graphics.getDeltaTime())
    } else ()
  }
  @java.lang.Override
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