package com.badlogic.gdx.graphics.g3d.decals

class SimpleOrthoGroupStrategy extends com.badlogic.gdx.graphics.g3d.decals.GroupStrategy {
  private var comparator: com.badlogic.gdx.graphics.g3d.decals.SimpleOrthoGroupStrategy#Comparator = new Comparator()
  @java.lang.Override
  override def decideGroup(decal: com.badlogic.gdx.graphics.g3d.decals.Decal): scala.Int = {
    return if (decal.getMaterial().isOpaque()) SimpleOrthoGroupStrategy.GROUP_OPAQUE else SimpleOrthoGroupStrategy.GROUP_BLEND
  }
  @java.lang.Override
  override def beforeGroup(group: scala.Int, contents: com.badlogic.gdx.utils.Array[com.badlogic.gdx.graphics.g3d.decals.Decal]): scala.Unit = {
    if (group == SimpleOrthoGroupStrategy.GROUP_BLEND) {
      com.badlogic.gdx.utils.Sort.instance().sort(contents, this.comparator)
      com.badlogic.gdx.Gdx.gl.glEnable(com.badlogic.gdx.graphics.GL20.GL_BLEND)
      com.badlogic.gdx.Gdx.gl.glDepthMask(false)
    } else {
      ()
    }
  }
  @java.lang.Override
  override def afterGroup(group: scala.Int): scala.Unit = {
    if (group == SimpleOrthoGroupStrategy.GROUP_BLEND) {
      com.badlogic.gdx.Gdx.gl.glDepthMask(true)
      com.badlogic.gdx.Gdx.gl.glDisable(com.badlogic.gdx.graphics.GL20.GL_BLEND)
    } else ()
  }
  @java.lang.Override
  override def beforeGroups(): scala.Unit = {
    com.badlogic.gdx.Gdx.gl.glEnable(com.badlogic.gdx.graphics.GL20.GL_TEXTURE_2D)
  }
  @java.lang.Override
  override def afterGroups(): scala.Unit = {
    com.badlogic.gdx.Gdx.gl.glDisable(com.badlogic.gdx.graphics.GL20.GL_TEXTURE_2D)
  }
  @java.lang.Override
  override def getGroupShader(group: scala.Int): com.badlogic.gdx.graphics.glutils.ShaderProgram = {
    return null
  }
  class Comparator extends java.util.Comparator[com.badlogic.gdx.graphics.g3d.decals.Decal] {
    @java.lang.Override
    override def compare(a: com.badlogic.gdx.graphics.g3d.decals.Decal, b: com.badlogic.gdx.graphics.g3d.decals.Decal): scala.Int = {
      if (a.getZ() == b.getZ()) {
        return 0
      } else ()
      return if ((a.getZ() - b.getZ()) < 0) -1 else 1
    }
  }
}
object SimpleOrthoGroupStrategy {
  private final val GROUP_OPAQUE: scala.Int = 0
  private final val GROUP_BLEND: scala.Int = 1
}