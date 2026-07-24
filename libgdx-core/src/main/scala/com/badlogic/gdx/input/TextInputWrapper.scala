package com.badlogic.gdx.input

trait TextInputWrapper {
  def getText(): java.lang.String
  def getSelectionStart(): scala.Int
  def getSelectionEnd(): scala.Int
  def writeResults(text: java.lang.String, selectionStart: scala.Int, selectionEnd: scala.Int): scala.Unit
}