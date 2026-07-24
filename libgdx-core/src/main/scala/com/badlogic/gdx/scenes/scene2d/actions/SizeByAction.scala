package com.badlogic.gdx.scenes.scene2d.actions

class SizeByAction extends com.badlogic.gdx.scenes.scene2d.actions.RelativeTemporalAction {
  private var amountWidth: scala.Float = 0.0f
  private var amountHeight: scala.Float = 0.0f
  def updateRelative(percentDelta: scala.Float): scala.Unit = {
    target.sizeBy(this.amountWidth * percentDelta, this.amountHeight * percentDelta)
  }
  def setAmount(width: scala.Float, height: scala.Float): scala.Unit = {
    this.amountWidth = width
    this.amountHeight = height
  }
  def getAmountWidth(): scala.Float = {
    return this.amountWidth
  }
  def setAmountWidth(width: scala.Float): scala.Unit = {
    this.amountWidth = width
  }
  def getAmountHeight(): scala.Float = {
    return this.amountHeight
  }
  def setAmountHeight(height: scala.Float): scala.Unit = {
    this.amountHeight = height
  }
}