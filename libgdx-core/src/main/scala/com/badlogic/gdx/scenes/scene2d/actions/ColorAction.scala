package com.badlogic.gdx.scenes.scene2d.actions

class ColorAction extends com.badlogic.gdx.scenes.scene2d.actions.TemporalAction {
  private var startR: scala.Float = 0.0f
  private var startG: scala.Float = 0.0f
  private var startB: scala.Float = 0.0f
  private var startA: scala.Float = 0.0f
  private var color: com.badlogic.gdx.graphics.Color = null.asInstanceOf[com.badlogic.gdx.graphics.Color]
  private final val `end`: com.badlogic.gdx.graphics.Color = new com.badlogic.gdx.graphics.Color()
  def begin(): scala.Unit = {
    if (this.color == null) {
      this.color = target.getColor()
    } else ()
    this.startR = this.color.r
    this.startG = this.color.g
    this.startB = this.color.b
    this.startA = this.color.a
  }
  def update(percent: scala.Float): scala.Unit = {
    if (percent == 0) {
      this.color.set(this.startR, this.startG, this.startB, this.startA)
    } else {
      if (percent == 1) {
        this.color.set(this.`end`)
      } else {
        val r: scala.Float = this.startR + ((this.`end`.r - this.startR) * percent)
        val g: scala.Float = this.startG + ((this.`end`.g - this.startG) * percent)
        val b: scala.Float = this.startB + ((this.`end`.b - this.startB) * percent)
        val a: scala.Float = this.startA + ((this.`end`.a - this.startA) * percent)
        this.color.set(r, g, b, a)
      }
    }
  }
  def reset(): scala.Unit = {
    super.reset()
    this.color = null
  }
  def getColor(): com.badlogic.gdx.graphics.Color = {
    return this.color
  }
  def setColor(color: com.badlogic.gdx.graphics.Color): scala.Unit = {
    this.color = color
  }
  def getEndColor(): com.badlogic.gdx.graphics.Color = {
    return this.`end`
  }
  def setEndColor(color: com.badlogic.gdx.graphics.Color): scala.Unit = {
    this.`end`.set(color)
  }
}