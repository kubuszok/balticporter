package com.badlogic.gdx

trait Application {
  def getApplicationListener(): com.badlogic.gdx.ApplicationListener
  def getGraphics(): com.badlogic.gdx.Graphics
  def getAudio(): com.badlogic.gdx.Audio
  def getInput(): com.badlogic.gdx.Input
  def getFiles(): com.badlogic.gdx.Files
  def getNet(): com.badlogic.gdx.Net
  def log(tag: java.lang.String, message: java.lang.String): scala.Unit
  def log(tag: java.lang.String, message: java.lang.String, exception: java.lang.Throwable): scala.Unit
  def error(tag: java.lang.String, message: java.lang.String): scala.Unit
  def error(tag: java.lang.String, message: java.lang.String, exception: java.lang.Throwable): scala.Unit
  def debug(tag: java.lang.String, message: java.lang.String): scala.Unit
  def debug(tag: java.lang.String, message: java.lang.String, exception: java.lang.Throwable): scala.Unit
  def setLogLevel(logLevel: scala.Int): scala.Unit
  def getLogLevel(): scala.Int
  def setApplicationLogger(applicationLogger: com.badlogic.gdx.ApplicationLogger): scala.Unit
  def getApplicationLogger(): com.badlogic.gdx.ApplicationLogger
  def getType(): com.badlogic.gdx.Application.ApplicationType
  def getVersion(): scala.Int
  def getJavaHeap(): scala.Long
  def getNativeHeap(): scala.Long
  def getPreferences(name: java.lang.String): com.badlogic.gdx.Preferences
  def getClipboard(): com.badlogic.gdx.utils.Clipboard
  def postRunnable(runnable: java.lang.Runnable): scala.Unit
  def exit(): scala.Unit
  def addLifecycleListener(listener: com.badlogic.gdx.LifecycleListener): scala.Unit
  def removeLifecycleListener(listener: com.badlogic.gdx.LifecycleListener): scala.Unit
}
object Application {
  final val LOG_NONE: scala.Int = 0
  final val LOG_DEBUG: scala.Int = 3
  final val LOG_INFO: scala.Int = 2
  final val LOG_ERROR: scala.Int = 1
  sealed abstract class ApplicationType
  object ApplicationType {
    case object Android extends ApplicationType
    case object Desktop extends ApplicationType
    case object HeadlessDesktop extends ApplicationType
    case object Applet extends ApplicationType
    case object WebGL extends ApplicationType
    case object iOS extends ApplicationType
    def values(): Array[ApplicationType] = Array(Android, Desktop, HeadlessDesktop, Applet, WebGL, iOS)
  }
}