package com.badlogic.gdx.scenes.scene2d.actions

class MoveByAction extends com.badlogic.gdx.scenes.scene2d.actions.RelativeTemporalAction {
  private var amountX: scala.Float = 0.0f
  private var amountY: scala.Float = 0.0f
  override def updateRelative(percentDelta: scala.Float): scala.Unit = {
    target.moveBy(this.amountX * percentDelta, this.amountY * percentDelta)
  }
  def setAmount(x: scala.Float, y: scala.Float): scala.Unit = {
    this.amountX = x
    this.amountY = y
  }
  def getAmountX(): scala.Float = {
    return this.amountX
  }
  def setAmountX(x: scala.Float): scala.Unit = {
    this.amountX = x
  }
  def getAmountY(): scala.Float = {
    return this.amountY
  }
  def setAmountY(y: scala.Float): scala.Unit = {
    this.amountY = y
  }
}