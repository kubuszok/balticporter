package com.badlogic.gdx.scenes.scene2d.utils

trait Drawable {
  def draw(batch: com.badlogic.gdx.graphics.g2d.Batch, x: scala.Float, y: scala.Float, width: scala.Float, height: scala.Float): scala.Unit
  def getLeftWidth(): scala.Float
  def setLeftWidth(leftWidth: scala.Float): scala.Unit
  def getRightWidth(): scala.Float
  def setRightWidth(rightWidth: scala.Float): scala.Unit
  def getTopHeight(): scala.Float
  def setTopHeight(topHeight: scala.Float): scala.Unit
  def getBottomHeight(): scala.Float
  def setBottomHeight(bottomHeight: scala.Float): scala.Unit
  def setPadding(topHeight: scala.Float, leftWidth: scala.Float, bottomHeight: scala.Float, rightWidth: scala.Float): scala.Unit = {
    this.setTopHeight(topHeight)
    this.setLeftWidth(leftWidth)
    this.setBottomHeight(bottomHeight)
    this.setRightWidth(rightWidth)
  }
  def setPadding(padding: scala.Float): scala.Unit = {
    this.setPadding(padding, padding, padding, padding)
  }
  def setPadding(from: Drawable): scala.Unit = {
    this.setPadding(from.getTopHeight(), from.getLeftWidth(), from.getBottomHeight(), from.getRightWidth())
  }
  def getMinWidth(): scala.Float
  def setMinWidth(minWidth: scala.Float): scala.Unit
  def getMinHeight(): scala.Float
  def setMinHeight(minHeight: scala.Float): scala.Unit
  def setMinSize(minWidth: scala.Float, minHeight: scala.Float): scala.Unit = {
    this.setMinWidth(minWidth)
    this.setMinHeight(minHeight)
  }
}