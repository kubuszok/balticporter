package com.badlogic.gdx.scenes.scene2d.actions

abstract class RelativeTemporalAction extends com.badlogic.gdx.scenes.scene2d.actions.TemporalAction {
  private var lastPercent: scala.Float = 0.0f
  protected def begin(): scala.Unit = {
    this.lastPercent = 0
  }
  protected def update(percent: scala.Float): scala.Unit = {
    this.updateRelative(percent - this.lastPercent)
    this.lastPercent = percent
  }
  protected def updateRelative(percentDelta: scala.Float): scala.Unit
}