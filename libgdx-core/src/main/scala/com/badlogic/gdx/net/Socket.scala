package com.badlogic.gdx.net

trait Socket extends com.badlogic.gdx.utils.Disposable {
  def isConnected(): scala.Boolean
  def getInputStream(): java.io.InputStream
  def getOutputStream(): java.io.OutputStream
  def getRemoteAddress(): java.lang.String
}