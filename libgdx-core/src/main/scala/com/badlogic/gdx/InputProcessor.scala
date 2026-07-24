package com.badlogic.gdx

trait InputProcessor {
  def keyDown(keycode: scala.Int): scala.Boolean
  def keyUp(keycode: scala.Int): scala.Boolean
  def keyTyped(character: scala.Char): scala.Boolean
  def touchDown(screenX: scala.Int, screenY: scala.Int, pointer: scala.Int, button: scala.Int): scala.Boolean
  def touchUp(screenX: scala.Int, screenY: scala.Int, pointer: scala.Int, button: scala.Int): scala.Boolean
  def touchCancelled(screenX: scala.Int, screenY: scala.Int, pointer: scala.Int, button: scala.Int): scala.Boolean
  def touchDragged(screenX: scala.Int, screenY: scala.Int, pointer: scala.Int): scala.Boolean
  def mouseMoved(screenX: scala.Int, screenY: scala.Int): scala.Boolean
  def scrolled(amountX: scala.Float, amountY: scala.Float): scala.Boolean
}