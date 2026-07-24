package com.badlogic.gdx.graphics.g3d.decals

trait GroupPlug {
  def beforeGroup(contents: com.badlogic.gdx.utils.Array[com.badlogic.gdx.graphics.g3d.decals.Decal]): scala.Unit
  def afterGroup(): scala.Unit
}