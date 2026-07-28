package com.badlogic.gdx.graphics.g3d.decals

class DecalMaterial {
  var textureRegion: com.badlogic.gdx.graphics.g2d.TextureRegion = null.asInstanceOf[com.badlogic.gdx.graphics.g2d.TextureRegion]
  var srcBlendFactor: scala.Int = 0
  var dstBlendFactor: scala.Int = 0
  def set(): scala.Unit = {
    this.textureRegion.getTexture().bind(0)
    if (!this.isOpaque()) {
      com.badlogic.gdx.Gdx.gl.glBlendFunc(this.srcBlendFactor, this.dstBlendFactor)
    } else ()
  }
  def isOpaque(): scala.Boolean = {
    return this.srcBlendFactor == DecalMaterial.NO_BLEND
  }
  def getSrcBlendFactor(): scala.Int = {
    return this.srcBlendFactor
  }
  def getDstBlendFactor(): scala.Int = {
    return this.dstBlendFactor
  }
  @java.lang.Override
  override def equals(o: java.lang.Object): scala.Boolean = {
    if (o == null) {
      return false
    } else ()
    val material: DecalMaterial = o.asInstanceOf[DecalMaterial].asInstanceOf[DecalMaterial]
    return ((this.dstBlendFactor == material.dstBlendFactor) && (this.srcBlendFactor == material.srcBlendFactor)) && (this.textureRegion.getTexture() == material.textureRegion.getTexture())
  }
  @java.lang.Override
  override def hashCode(): scala.Int = {
    var result: scala.Int = if (this.textureRegion.getTexture() != null) this.textureRegion.getTexture().hashCode() else 0
    result = (31 * result) + this.srcBlendFactor
    result = (31 * result) + this.dstBlendFactor
    return result
  }
}
object DecalMaterial {
  final val NO_BLEND: scala.Int = -1
}