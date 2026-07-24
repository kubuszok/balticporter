package com.badlogic.gdx.graphics.glutils

trait InstanceData extends com.badlogic.gdx.utils.Disposable {
  def getNumInstances(): scala.Int
  def getNumMaxInstances(): scala.Int
  def getAttributes(): com.badlogic.gdx.graphics.VertexAttributes
  def setInstanceData(data: scala.Array[scala.Float], offset: scala.Int, count: scala.Int): scala.Unit
  def updateInstanceData(targetOffset: scala.Int, data: scala.Array[scala.Float], sourceOffset: scala.Int, count: scala.Int): scala.Unit
  def setInstanceData(data: java.nio.FloatBuffer, count: scala.Int): scala.Unit
  def updateInstanceData(targetOffset: scala.Int, data: java.nio.FloatBuffer, sourceOffset: scala.Int, count: scala.Int): scala.Unit
  def getBuffer(): java.nio.FloatBuffer
  def getBuffer(forWriting: scala.Boolean): java.nio.FloatBuffer
  def bind(shader: com.badlogic.gdx.graphics.glutils.ShaderProgram): scala.Unit
  def bind(shader: com.badlogic.gdx.graphics.glutils.ShaderProgram, locations: scala.Array[scala.Int]): scala.Unit
  def unbind(shader: com.badlogic.gdx.graphics.glutils.ShaderProgram): scala.Unit
  def unbind(shader: com.badlogic.gdx.graphics.glutils.ShaderProgram, locations: scala.Array[scala.Int]): scala.Unit
  def invalidate(): scala.Unit
  def dispose(): scala.Unit
}