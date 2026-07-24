package com.badlogic.gdx.maps

class MapObjects extends scala.collection.Iterable[com.badlogic.gdx.maps.MapObject] {
  private var objects: com.badlogic.gdx.utils.Array[com.badlogic.gdx.maps.MapObject] = null.asInstanceOf[com.badlogic.gdx.utils.Array[com.badlogic.gdx.maps.MapObject]]
  def this() = {
    this()
    this.objects = new com.badlogic.gdx.utils.Array[com.badlogic.gdx.maps.MapObject]()
  }
  def get(index: scala.Int): com.badlogic.gdx.maps.MapObject = {
    return this.objects.get(index)
  }
  def get(name: java.lang.String): com.badlogic.gdx.maps.MapObject = {
    { var i: scala.Int = 0; val n: scala.Int = this.objects.size; while (i < n) { {
      val `object`: com.badlogic.gdx.maps.MapObject = this.objects.get(i)
      if (name.equals(`object`.getName())) {
        return `object`
      } else ()
    }; i = i + 1 } }
    return null
  }
  def getIndex(name: java.lang.String): scala.Int = {
    return this.getIndex(this.get(name))
  }
  def getIndex(`object`: com.badlogic.gdx.maps.MapObject): scala.Int = {
    return this.objects.indexOf(`object`, true)
  }
  def getCount(): scala.Int = {
    return this.objects.size
  }
  def add(`object`: com.badlogic.gdx.maps.MapObject): scala.Unit = {
    this.objects.add(`object`)
  }
  def remove(index: scala.Int): scala.Unit = {
    this.objects.removeIndex(index)
  }
  def remove(`object`: com.badlogic.gdx.maps.MapObject): scala.Unit = {
    this.objects.removeValue(`object`, true)
  }
  def getByType[T <: com.badlogic.gdx.maps.MapObject](`type`: java.lang.Class[T]): com.badlogic.gdx.utils.Array[T] = {
    return this.getByType(`type`, new com.badlogic.gdx.utils.Array[T]())
  }
  def getByType[T <: com.badlogic.gdx.maps.MapObject](`type`: java.lang.Class[T], fill: com.badlogic.gdx.utils.Array[T]): com.badlogic.gdx.utils.Array[T] = {
    fill.clear();
    { var i: scala.Int = 0; val n: scala.Int = this.objects.size; while (i < n) { {
      val `object`: com.badlogic.gdx.maps.MapObject = this.objects.get(i)
      if (com.badlogic.gdx.utils.reflect.ClassReflection.isInstance(`type`, `object`)) {
        fill.add(`object`.asInstanceOf[T])
      } else ()
    }; i = i + 1 } }
    return fill
  }
  def iterator(): scala.collection.Iterator[com.badlogic.gdx.maps.MapObject] = {
    return this.objects.iterator()
  }
}