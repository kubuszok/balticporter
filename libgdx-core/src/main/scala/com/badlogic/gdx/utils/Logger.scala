package com.badlogic.gdx.utils

class Logger {
  private var tag: java.lang.String = null.asInstanceOf[java.lang.String]
  private var level: scala.Int = 0
  def this(tag: java.lang.String, level: scala.Int) = {
    this()
    this.tag = tag
    this.level = level
  }
  def this(tag: java.lang.String) = {
    this(tag, Logger.ERROR)
  }
  def debug(message: java.lang.String): scala.Unit = {
    if (this.level >= Logger.DEBUG) {
      com.badlogic.gdx.Gdx.app.debug(this.tag, message)
    } else ()
  }
  def debug(message: java.lang.String, exception: java.lang.Exception): scala.Unit = {
    if (this.level >= Logger.DEBUG) {
      com.badlogic.gdx.Gdx.app.debug(this.tag, message, exception)
    } else ()
  }
  def info(message: java.lang.String): scala.Unit = {
    if (this.level >= Logger.INFO) {
      com.badlogic.gdx.Gdx.app.log(this.tag, message)
    } else ()
  }
  def info(message: java.lang.String, exception: java.lang.Exception): scala.Unit = {
    if (this.level >= Logger.INFO) {
      com.badlogic.gdx.Gdx.app.log(this.tag, message, exception)
    } else ()
  }
  def error(message: java.lang.String): scala.Unit = {
    if (this.level >= Logger.ERROR) {
      com.badlogic.gdx.Gdx.app.error(this.tag, message)
    } else ()
  }
  def error(message: java.lang.String, exception: java.lang.Throwable): scala.Unit = {
    if (this.level >= Logger.ERROR) {
      com.badlogic.gdx.Gdx.app.error(this.tag, message, exception)
    } else ()
  }
  def setLevel(level: scala.Int): scala.Unit = {
    this.level = level
  }
  def getLevel(): scala.Int = {
    return this.level
  }
}
object Logger {
  final val NONE: scala.Int = 0
  final val ERROR: scala.Int = 1
  final val INFO: scala.Int = 2
  final val DEBUG: scala.Int = 3
}