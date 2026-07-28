package com.badlogic.gdx.graphics.g3d

class Material extends com.badlogic.gdx.graphics.g3d.Attributes {
  var id: java.lang.String = null.asInstanceOf[java.lang.String]
  def this(id: java.lang.String) = {
    this()
    this.id = id
  }
  def this(attributes: scala.Array[com.badlogic.gdx.graphics.g3d.Attribute]) = {
    this()
    this.set(attributes)
  }
  def this(id: java.lang.String, attributes: scala.Array[com.badlogic.gdx.graphics.g3d.Attribute]) = {
    this(id)
    this.set(attributes)
  }
  def this(attributes: com.badlogic.gdx.utils.Array[com.badlogic.gdx.graphics.g3d.Attribute]) = {
    this()
    this.set(attributes)
  }
  def this(id: java.lang.String, attributes: com.badlogic.gdx.utils.Array[com.badlogic.gdx.graphics.g3d.Attribute]) = {
    this(id)
    this.set(attributes)
  }
  def this(id: java.lang.String, copyFrom: Material) = {
    this(id)
    for (attr <- copyFrom) {
      this.set(attr.copy())
    }
  }
  def this(copyFrom: Material) = {
    this(copyFrom.id, copyFrom)
  }
  this.id = "mtl" + { Material.counter += 1; Material.counter }
  def copy(): Material = {
    return new Material(this)
  }
  @java.lang.Override
  def hashCode(): scala.Int = {
    return super.hashCode() + (3 * this.id.hashCode())
  }
  @java.lang.Override
  def equals(other: java.lang.Object): scala.Boolean = {
    return other.isInstanceOf[Material] && ((other == this) || (other.asInstanceOf[Material].id.equals(this.id) && super.equals(other)))
  }
}
object Material {
  private var counter: scala.Int = 0
}