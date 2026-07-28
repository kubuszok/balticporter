package com.badlogic.gdx.scenes.scene2d.actions

class RotateByAction extends com.badlogic.gdx.scenes.scene2d.actions.RelativeTemporalAction {
  private var amount: scala.Float = 0.0f
  override def updateRelative(percentDelta: scala.Float): scala.Unit = {
    target.rotateBy(this.amount * percentDelta)
  }
  def getAmount(): scala.Float = {
    return this.amount
  }
  def setAmount(rotationAmount: scala.Float): scala.Unit = {
    this.amount = rotationAmount
  }
}