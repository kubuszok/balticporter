package com.badlogic.gdx

abstract class AbstractGraphics extends com.badlogic.gdx.Graphics {
  @java.lang.Override
  override def getRawDeltaTime(): scala.Float = {
    return this.getDeltaTime()
  }
  @java.lang.Override
  override def getDensity(): scala.Float = {
    val ppiX: scala.Float = this.getPpiX()
    return if ((ppiX > 0) && (ppiX <= java.lang.Float.MAX_VALUE)) ppiX / 160.0f else 1.0f
  }
  @java.lang.Override
  override def getBackBufferScale(): scala.Float = {
    return this.getBackBufferWidth() / this.getWidth().asInstanceOf[scala.Float]
  }
}
object AbstractGraphics {
  export com.badlogic.gdx.Graphics.*
}