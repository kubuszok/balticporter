package com.badlogic.gdx.utils

trait Clipboard {
  def hasContents(): scala.Boolean
  def getContents(): java.lang.String
  def setContents(content: java.lang.String): scala.Unit
}