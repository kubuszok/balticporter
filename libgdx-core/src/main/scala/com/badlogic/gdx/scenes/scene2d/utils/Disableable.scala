package com.badlogic.gdx.scenes.scene2d.utils

trait Disableable {
  def setDisabled(isDisabled: scala.Boolean): scala.Unit
  def isDisabled(): scala.Boolean
}