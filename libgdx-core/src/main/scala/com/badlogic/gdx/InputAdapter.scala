package com.badlogic.gdx

class InputAdapter extends com.badlogic.gdx.InputProcessor {
  def keyDown(keycode: scala.Int): scala.Boolean = {
    return false
  }
  def keyUp(keycode: scala.Int): scala.Boolean = {
    return false
  }
  def keyTyped(character: scala.Char): scala.Boolean = {
    return false
  }
  def touchDown(screenX: scala.Int, screenY: scala.Int, pointer: scala.Int, button: scala.Int): scala.Boolean = {
    return false
  }
  def touchUp(screenX: scala.Int, screenY: scala.Int, pointer: scala.Int, button: scala.Int): scala.Boolean = {
    return false
  }
  def touchCancelled(screenX: scala.Int, screenY: scala.Int, pointer: scala.Int, button: scala.Int): scala.Boolean = {
    return false
  }
  def touchDragged(screenX: scala.Int, screenY: scala.Int, pointer: scala.Int): scala.Boolean = {
    return false
  }
  @java.lang.Override
  def mouseMoved(screenX: scala.Int, screenY: scala.Int): scala.Boolean = {
    return false
  }
  @java.lang.Override
  def scrolled(amountX: scala.Float, amountY: scala.Float): scala.Boolean = {
    return false
  }
}