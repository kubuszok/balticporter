package com.badlogic.gdx

trait ApplicationLogger {
  def log(tag: java.lang.String, message: java.lang.String): scala.Unit
  def log(tag: java.lang.String, message: java.lang.String, exception: java.lang.Throwable): scala.Unit
  def error(tag: java.lang.String, message: java.lang.String): scala.Unit
  def error(tag: java.lang.String, message: java.lang.String, exception: java.lang.Throwable): scala.Unit
  def debug(tag: java.lang.String, message: java.lang.String): scala.Unit
  def debug(tag: java.lang.String, message: java.lang.String, exception: java.lang.Throwable): scala.Unit
}