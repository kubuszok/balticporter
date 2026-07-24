package com.badlogic.gdx.graphics.g3d.decals

trait GroupStrategy {
  def getGroupShader(group: scala.Int): com.badlogic.gdx.graphics.glutils.ShaderProgram
  def decideGroup(decal: com.badlogic.gdx.graphics.g3d.decals.Decal): scala.Int
  def beforeGroup(group: scala.Int, contents: com.badlogic.gdx.utils.Array[com.badlogic.gdx.graphics.g3d.decals.Decal]): scala.Unit
  def afterGroup(group: scala.Int): scala.Unit
  def beforeGroups(): scala.Unit
  def afterGroups(): scala.Unit
}