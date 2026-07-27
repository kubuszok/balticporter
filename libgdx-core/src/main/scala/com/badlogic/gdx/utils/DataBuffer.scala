package com.badlogic.gdx.utils

class DataBuffer(initialSize: scala.Int) extends com.badlogic.gdx.utils.DataOutput(new com.badlogic.gdx.utils.StreamUtils.OptimizedByteArrayOutputStream(initialSize)) {
  private var outStream: com.badlogic.gdx.utils.StreamUtils.OptimizedByteArrayOutputStream = null.asInstanceOf[com.badlogic.gdx.utils.StreamUtils.OptimizedByteArrayOutputStream]
  def this() = {
    this(32)
  }
  this.outStream = out.asInstanceOf[com.badlogic.gdx.utils.StreamUtils.OptimizedByteArrayOutputStream]
  def getBuffer(): scala.Array[scala.Byte] = {
    return this.outStream.getBuffer()
  }
  def toArray(): scala.Array[scala.Byte] = {
    return this.outStream.toByteArray()
  }
}