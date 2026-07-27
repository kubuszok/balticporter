package com.badlogic.gdx.maps

class MapLayers extends balticporter.runtime.JavaIterable[com.badlogic.gdx.maps.MapLayer] {
  private var layers: com.badlogic.gdx.utils.Array[com.badlogic.gdx.maps.MapLayer] = new com.badlogic.gdx.utils.Array[com.badlogic.gdx.maps.MapLayer]()
  def get(index: scala.Int): com.badlogic.gdx.maps.MapLayer = {
    return this.layers.get(index)
  }
  def get(name: java.lang.String): com.badlogic.gdx.maps.MapLayer = {
    { var i: scala.Int = 0; val n: scala.Int = this.layers.size; while (i < n) { {
      val layer: com.badlogic.gdx.maps.MapLayer = this.layers.get(i)
      if (name.equals(layer.getName())) {
        return layer
      } else ()
    }; i = i + 1 } }
    return null
  }
  def getIndex(name: java.lang.String): scala.Int = {
    return this.getIndex(this.get(name))
  }
  def getIndex(layer: com.badlogic.gdx.maps.MapLayer): scala.Int = {
    return this.layers.indexOf(layer, true)
  }
  def getCount(): scala.Int = {
    return this.layers.size
  }
  def add(layer: com.badlogic.gdx.maps.MapLayer): scala.Unit = {
    this.layers.add(layer)
  }
  def remove(index: scala.Int): scala.Unit = {
    this.layers.removeIndex(index)
  }
  def remove(layer: com.badlogic.gdx.maps.MapLayer): scala.Unit = {
    this.layers.removeValue(layer, true)
  }
  def size(): scala.Int = {
    return this.layers.size
  }
  def getByType[T <: com.badlogic.gdx.maps.MapLayer](`type`: java.lang.Class[T]): com.badlogic.gdx.utils.Array[T] = {
    return this.getByType(`type`, new com.badlogic.gdx.utils.Array[T]())
  }
  def getByType[T <: com.badlogic.gdx.maps.MapLayer](`type`: java.lang.Class[T], fill: com.badlogic.gdx.utils.Array[T]): com.badlogic.gdx.utils.Array[T] = {
    fill.clear();
    { var i: scala.Int = 0; val n: scala.Int = this.layers.size; while (i < n) { {
      val layer: com.badlogic.gdx.maps.MapLayer = this.layers.get(i)
      if (`type`.isInstance(layer)) {
        fill.add(layer.asInstanceOf[T])
      } else ()
    }; i = i + 1 } }
    return fill
  }
  def iterator(): balticporter.runtime.JavaIterator[com.badlogic.gdx.maps.MapLayer] = {
    return this.layers.iterator()
  }
}