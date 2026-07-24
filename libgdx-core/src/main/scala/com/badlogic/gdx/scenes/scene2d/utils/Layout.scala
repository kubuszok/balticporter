package com.badlogic.gdx.scenes.scene2d.utils

trait Layout {
  def layout(): scala.Unit
  def invalidate(): scala.Unit
  def invalidateHierarchy(): scala.Unit
  def validate(): scala.Unit
  def pack(): scala.Unit
  def setFillParent(fillParent: scala.Boolean): scala.Unit
  def setLayoutEnabled(enabled: scala.Boolean): scala.Unit
  def getMinWidth(): scala.Float
  def getMinHeight(): scala.Float
  def getPrefWidth(): scala.Float
  def getPrefHeight(): scala.Float
  def getMaxWidth(): scala.Float
  def getMaxHeight(): scala.Float
}