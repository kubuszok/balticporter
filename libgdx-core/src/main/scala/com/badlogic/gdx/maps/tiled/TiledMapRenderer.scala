package com.badlogic.gdx.maps.tiled

trait TiledMapRenderer extends com.badlogic.gdx.maps.MapRenderer {
  def renderObjects(layer: com.badlogic.gdx.maps.MapLayer): scala.Unit
  def renderObject(`object`: com.badlogic.gdx.maps.MapObject): scala.Unit
  def renderTileLayer(layer: com.badlogic.gdx.maps.tiled.TiledMapTileLayer): scala.Unit
  def renderImageLayer(layer: com.badlogic.gdx.maps.tiled.TiledMapImageLayer): scala.Unit
}