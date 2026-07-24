package com.badlogic.gdx

abstract class AbstractGraphics extends com.badlogic.gdx.Graphics {
  def getRawDeltaTime(): scala.Float = {
    return this.getDeltaTime()
  }
  def getDensity(): scala.Float = {
    val ppiX: scala.Float = this.getPpiX()
    return if ((ppiX > 0) && (ppiX <= java.lang.Float.MAX_VALUE)) ppiX / 160.0f else 1.0f
  }
  def getBackBufferScale(): scala.Float = {
    return this.getBackBufferWidth() / this.getWidth().asInstanceOf[scala.Float]
  }
}