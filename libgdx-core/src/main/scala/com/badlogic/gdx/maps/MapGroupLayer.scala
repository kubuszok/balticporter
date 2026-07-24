package com.badlogic.gdx.maps

class MapGroupLayer extends com.badlogic.gdx.maps.MapLayer {
  private var layers: com.badlogic.gdx.maps.MapLayers = new com.badlogic.gdx.maps.MapLayers()
  def getLayers(): com.badlogic.gdx.maps.MapLayers = {
    return this.layers
  }
  def invalidateRenderOffset(): scala.Unit = {
    super.invalidateRenderOffset()
    { var i: scala.Int = 0; while (i < this.layers.size()) { {
      val child: com.badlogic.gdx.maps.MapLayer = this.layers.get(i)
      child.invalidateRenderOffset()
    }; i = i + 1 } }
  }
}