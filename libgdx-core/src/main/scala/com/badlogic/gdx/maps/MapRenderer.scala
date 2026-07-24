package com.badlogic.gdx.maps

trait MapRenderer {
  def setView(camera: com.badlogic.gdx.graphics.OrthographicCamera): scala.Unit
  def setView(projectionMatrix: com.badlogic.gdx.math.Matrix4, viewboundsX: scala.Float, viewboundsY: scala.Float, viewboundsWidth: scala.Float, viewboundsHeight: scala.Float): scala.Unit
  def render(): scala.Unit
  def render(layers: scala.Array[scala.Int]): scala.Unit
}