package com.badlogic.gdx.graphics.glutils

trait VertexData extends com.badlogic.gdx.utils.Disposable {
  def getNumVertices(): scala.Int
  def getNumMaxVertices(): scala.Int
  def getAttributes(): com.badlogic.gdx.graphics.VertexAttributes
  def setVertices(vertices: scala.Array[scala.Float], offset: scala.Int, count: scala.Int): scala.Unit
  def updateVertices(targetOffset: scala.Int, vertices: scala.Array[scala.Float], sourceOffset: scala.Int, count: scala.Int): scala.Unit
  @java.lang.Deprecated
  def getBuffer(): java.nio.FloatBuffer
  def getBuffer(forWriting: scala.Boolean): java.nio.FloatBuffer
  def bind(shader: com.badlogic.gdx.graphics.glutils.ShaderProgram): scala.Unit
  def bind(shader: com.badlogic.gdx.graphics.glutils.ShaderProgram, locations: scala.Array[scala.Int]): scala.Unit
  def unbind(shader: com.badlogic.gdx.graphics.glutils.ShaderProgram): scala.Unit
  def unbind(shader: com.badlogic.gdx.graphics.glutils.ShaderProgram, locations: scala.Array[scala.Int]): scala.Unit
  def invalidate(): scala.Unit
  def dispose(): scala.Unit
}