package com.badlogic.gdx.maps

class MapProperties {
  private var properties: com.badlogic.gdx.utils.ObjectMap[java.lang.String, java.lang.Object] = null.asInstanceOf[com.badlogic.gdx.utils.ObjectMap[java.lang.String, java.lang.Object]]
  this.properties = new com.badlogic.gdx.utils.ObjectMap[java.lang.String, java.lang.Object]()
  def containsKey(key: java.lang.String): scala.Boolean = {
    return this.properties.containsKey(key)
  }
  def get(key: java.lang.String): java.lang.Object = {
    return this.properties.get(key)
  }
  def get[T](key: java.lang.String, clazz: java.lang.Class[T]): T = {
    return this.get(key).asInstanceOf[T].asInstanceOf[T]
  }
  def get[T](key: java.lang.String, defaultValue: T, clazz: java.lang.Class[T]): T = {
    val `object`: java.lang.Object = this.get(key)
    return if (`object` == null) defaultValue else `object`.asInstanceOf[T]
  }
  def put(key: java.lang.String, value: java.lang.Object): scala.Unit = {
    this.properties.put(key, value)
  }
  def putAll(properties: MapProperties): scala.Unit = {
    this.properties.putAll(properties.properties)
  }
  def remove(key: java.lang.String): scala.Unit = {
    this.properties.remove(key)
  }
  def clear(): scala.Unit = {
    this.properties.clear()
  }
  def getKeys(): balticporter.runtime.JavaIterator[java.lang.String] = {
    return this.properties.keys()
  }
  def getValues(): balticporter.runtime.JavaIterator[java.lang.Object] = {
    return this.properties.values()
  }
  def toString(): java.lang.String = {
    return (("MapProperties{" + "properties=") + this.properties) + '}'
  }
  def equals(o: java.lang.Object): scala.Boolean = {
    if (!o.isInstanceOf[MapProperties]) {
      return false
    } else ()
    val that: MapProperties = o.asInstanceOf[MapProperties].asInstanceOf[MapProperties]
    return java.util.Objects.equals(this.properties, that.properties)
  }
  def hashCode(): scala.Int = {
    return this.properties.hashCode()
  }
}