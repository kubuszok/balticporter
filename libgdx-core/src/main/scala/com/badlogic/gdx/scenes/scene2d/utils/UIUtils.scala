package com.badlogic.gdx.scenes.scene2d.utils

object UIUtils {
  var isAndroid: scala.Boolean = com.badlogic.gdx.utils.SharedLibraryLoader.os == com.badlogic.gdx.utils.Os.Android
  var isMac: scala.Boolean = com.badlogic.gdx.utils.SharedLibraryLoader.os == com.badlogic.gdx.utils.Os.MacOsX
  var isWindows: scala.Boolean = com.badlogic.gdx.utils.SharedLibraryLoader.os == com.badlogic.gdx.utils.Os.Windows
  var isLinux: scala.Boolean = com.badlogic.gdx.utils.SharedLibraryLoader.os == com.badlogic.gdx.utils.Os.Linux
  var isIos: scala.Boolean = com.badlogic.gdx.utils.SharedLibraryLoader.os == com.badlogic.gdx.utils.Os.IOS
  def left(): scala.Boolean = {
    return com.badlogic.gdx.Gdx.input.isButtonPressed(com.badlogic.gdx.Input.Buttons.LEFT)
  }
  def left(button: scala.Int): scala.Boolean = {
    return button == com.badlogic.gdx.Input.Buttons.LEFT
  }
  def right(): scala.Boolean = {
    return com.badlogic.gdx.Gdx.input.isButtonPressed(com.badlogic.gdx.Input.Buttons.RIGHT)
  }
  def right(button: scala.Int): scala.Boolean = {
    return button == com.badlogic.gdx.Input.Buttons.RIGHT
  }
  def middle(): scala.Boolean = {
    return com.badlogic.gdx.Gdx.input.isButtonPressed(com.badlogic.gdx.Input.Buttons.MIDDLE)
  }
  def middle(button: scala.Int): scala.Boolean = {
    return button == com.badlogic.gdx.Input.Buttons.MIDDLE
  }
  def shift(): scala.Boolean = {
    return com.badlogic.gdx.Gdx.input.isKeyPressed(com.badlogic.gdx.Input.Keys.SHIFT_LEFT) || com.badlogic.gdx.Gdx.input.isKeyPressed(com.badlogic.gdx.Input.Keys.SHIFT_RIGHT)
  }
  def shift(keycode: scala.Int): scala.Boolean = {
    return (keycode == com.badlogic.gdx.Input.Keys.SHIFT_LEFT) || (keycode == com.badlogic.gdx.Input.Keys.SHIFT_RIGHT)
  }
  def ctrl(): scala.Boolean = {
    if (UIUtils.isMac) {
      return com.badlogic.gdx.Gdx.input.isKeyPressed(com.badlogic.gdx.Input.Keys.SYM)
    } else {
      return com.badlogic.gdx.Gdx.input.isKeyPressed(com.badlogic.gdx.Input.Keys.CONTROL_LEFT) || com.badlogic.gdx.Gdx.input.isKeyPressed(com.badlogic.gdx.Input.Keys.CONTROL_RIGHT)
    }
  }
  def ctrl(keycode: scala.Int): scala.Boolean = {
    if (UIUtils.isMac) {
      return keycode == com.badlogic.gdx.Input.Keys.SYM
    } else {
      return (keycode == com.badlogic.gdx.Input.Keys.CONTROL_LEFT) || (keycode == com.badlogic.gdx.Input.Keys.CONTROL_RIGHT)
    }
  }
  def alt(): scala.Boolean = {
    return com.badlogic.gdx.Gdx.input.isKeyPressed(com.badlogic.gdx.Input.Keys.ALT_LEFT) || com.badlogic.gdx.Gdx.input.isKeyPressed(com.badlogic.gdx.Input.Keys.ALT_RIGHT)
  }
  def alt(keycode: scala.Int): scala.Boolean = {
    return (keycode == com.badlogic.gdx.Input.Keys.ALT_LEFT) || (keycode == com.badlogic.gdx.Input.Keys.ALT_RIGHT)
  }
}