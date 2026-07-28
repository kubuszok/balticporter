package com.badlogic.gdx

class InputAdapter extends com.badlogic.gdx.InputProcessor {
  override def keyDown(keycode: scala.Int): scala.Boolean = {
    return false
  }
  override def keyUp(keycode: scala.Int): scala.Boolean = {
    return false
  }
  override def keyTyped(character: scala.Char): scala.Boolean = {
    return false
  }
  override def touchDown(screenX: scala.Int, screenY: scala.Int, pointer: scala.Int, button: scala.Int): scala.Boolean = {
    return false
  }
  override def touchUp(screenX: scala.Int, screenY: scala.Int, pointer: scala.Int, button: scala.Int): scala.Boolean = {
    return false
  }
  override def touchCancelled(screenX: scala.Int, screenY: scala.Int, pointer: scala.Int, button: scala.Int): scala.Boolean = {
    return false
  }
  override def touchDragged(screenX: scala.Int, screenY: scala.Int, pointer: scala.Int): scala.Boolean = {
    return false
  }
  @java.lang.Override
  override def mouseMoved(screenX: scala.Int, screenY: scala.Int): scala.Boolean = {
    return false
  }
  @java.lang.Override
  override def scrolled(amountX: scala.Float, amountY: scala.Float): scala.Boolean = {
    return false
  }
}