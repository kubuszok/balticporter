package com.badlogic.gdx.graphics.g3d.decals

abstract class PluggableGroupStrategy extends com.badlogic.gdx.graphics.g3d.decals.GroupStrategy {
  private var plugs: com.badlogic.gdx.utils.IntMap[com.badlogic.gdx.graphics.g3d.decals.GroupPlug] = new com.badlogic.gdx.utils.IntMap[com.badlogic.gdx.graphics.g3d.decals.GroupPlug]()
  @java.lang.Override
  def beforeGroup(group: scala.Int, contents: com.badlogic.gdx.utils.Array[com.badlogic.gdx.graphics.g3d.decals.Decal]): scala.Unit = {
    this.plugs.get(group).beforeGroup(contents)
  }
  @java.lang.Override
  def afterGroup(group: scala.Int): scala.Unit = {
    this.plugs.get(group).afterGroup()
  }
  def plugIn(plug: com.badlogic.gdx.graphics.g3d.decals.GroupPlug, group: scala.Int): scala.Unit = {
    this.plugs.put(group, plug)
  }
  def unPlug(group: scala.Int): com.badlogic.gdx.graphics.g3d.decals.GroupPlug = {
    return this.plugs.remove(group)
  }
}