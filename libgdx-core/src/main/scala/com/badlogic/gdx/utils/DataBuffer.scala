package com.badlogic.gdx.utils

class DataBuffer extends com.badlogic.gdx.utils.DataOutput {
  private var outStream: com.badlogic.gdx.utils.StreamUtils.OptimizedByteArrayOutputStream = null.asInstanceOf[com.badlogic.gdx.utils.StreamUtils.OptimizedByteArrayOutputStream]
  def this(initialSize: scala.Int) = {
    this()
    this.outStream = out.asInstanceOf[com.badlogic.gdx.utils.StreamUtils.OptimizedByteArrayOutputStream]
  }
  def getBuffer(): scala.Array[scala.Byte] = {
    return this.outStream.getBuffer()
  }
  def toArray(): scala.Array[scala.Byte] = {
    return this.outStream.toByteArray()
  }
}