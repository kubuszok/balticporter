package com.badlogic.gdx.graphics.glutils

trait ImmediateModeRenderer {
  def begin(projModelView: com.badlogic.gdx.math.Matrix4, primitiveType: scala.Int): scala.Unit
  def flush(): scala.Unit
  def color(color: com.badlogic.gdx.graphics.Color): scala.Unit
  def color(r: scala.Float, g: scala.Float, b: scala.Float, a: scala.Float): scala.Unit
  def color(colorBits: scala.Float): scala.Unit
  def texCoord(u: scala.Float, v: scala.Float): scala.Unit
  def normal(x: scala.Float, y: scala.Float, z: scala.Float): scala.Unit
  def vertex(x: scala.Float, y: scala.Float, z: scala.Float): scala.Unit
  def `end`(): scala.Unit
  def getNumVertices(): scala.Int
  def getMaxVertices(): scala.Int
  def dispose(): scala.Unit
}