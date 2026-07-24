package com.badlogic.gdx.utils.compression

trait ICodeProgress {
  def SetProgress(inSize: scala.Long, outSize: scala.Long): scala.Unit
}