package com.badlogic.gdx.graphics.g3d.utils

class TextureDescriptor[T <: com.badlogic.gdx.graphics.GLTexture] extends java.lang.Comparable[TextureDescriptor[T]] {
  var texture: T = null
  var minFilter: com.badlogic.gdx.graphics.Texture#TextureFilter = null.asInstanceOf[com.badlogic.gdx.graphics.Texture#TextureFilter]
  var magFilter: com.badlogic.gdx.graphics.Texture#TextureFilter = null.asInstanceOf[com.badlogic.gdx.graphics.Texture#TextureFilter]
  var uWrap: com.badlogic.gdx.graphics.Texture#TextureWrap = null.asInstanceOf[com.badlogic.gdx.graphics.Texture#TextureWrap]
  var vWrap: com.badlogic.gdx.graphics.Texture#TextureWrap = null.asInstanceOf[com.badlogic.gdx.graphics.Texture#TextureWrap]
  def this(texture: T, minFilter: com.badlogic.gdx.graphics.Texture#TextureFilter, magFilter: com.badlogic.gdx.graphics.Texture#TextureFilter, uWrap: com.badlogic.gdx.graphics.Texture#TextureWrap, vWrap: com.badlogic.gdx.graphics.Texture#TextureWrap) = {
    this()
    this.set(texture, minFilter, magFilter, uWrap, vWrap)
  }
  def this(texture: T) = {
    this(texture, null, null, null, null)
  }
  def set(texture: T, minFilter: com.badlogic.gdx.graphics.Texture#TextureFilter, magFilter: com.badlogic.gdx.graphics.Texture#TextureFilter, uWrap: com.badlogic.gdx.graphics.Texture#TextureWrap, vWrap: com.badlogic.gdx.graphics.Texture#TextureWrap): scala.Unit = {
    this.texture = texture
    this.minFilter = minFilter
    this.magFilter = magFilter
    this.uWrap = uWrap
    this.vWrap = vWrap
  }
  def set[V <: T](other: TextureDescriptor[V]): scala.Unit = {
    this.texture = other.texture
    this.minFilter = other.minFilter
    this.magFilter = other.magFilter
    this.uWrap = other.uWrap
    this.vWrap = other.vWrap
  }
  def equals(obj: java.lang.Object): scala.Boolean = {
    if (obj == null) {
      return false
    } else ()
    if (obj == this) {
      return true
    } else ()
    if (!obj.isInstanceOf[TextureDescriptor]) {
      return false
    } else ()
    val other: TextureDescriptor[?] = obj.asInstanceOf[TextureDescriptor[?]]
    return ((((other.texture == this.texture) && (other.minFilter == this.minFilter)) && (other.magFilter == this.magFilter)) && (other.uWrap == this.uWrap)) && (other.vWrap == this.vWrap)
  }
  def hashCode(): scala.Int = {
    var result: scala.Long = if (this.texture == null) 0 else this.texture.glTarget
    result = (811 * result) + (if (this.texture == null) 0 else this.texture.getTextureObjectHandle())
    result = (811 * result) + (if (this.minFilter == null) 0 else this.minFilter.getGLEnum())
    result = (811 * result) + (if (this.magFilter == null) 0 else this.magFilter.getGLEnum())
    result = (811 * result) + (if (this.uWrap == null) 0 else this.uWrap.getGLEnum())
    result = (811 * result) + (if (this.vWrap == null) 0 else this.vWrap.getGLEnum())
    return (result ^ (result >> 32)).asInstanceOf[scala.Int]
  }
  def compareTo(o: TextureDescriptor[T]): scala.Int = {
    if (o == this) {
      return 0
    } else ()
    val t1: scala.Int = if (this.texture == null) 0 else this.texture.glTarget
    val t2: scala.Int = if (o.texture == null) 0 else o.texture.glTarget
    if (t1 != t2) {
      return t1 - t2
    } else ()
    val h1: scala.Int = if (this.texture == null) 0 else this.texture.getTextureObjectHandle()
    val h2: scala.Int = if (o.texture == null) 0 else o.texture.getTextureObjectHandle()
    if (h1 != h2) {
      return h1 - h2
    } else ()
    if (this.minFilter != o.minFilter) {
      return (if (this.minFilter == null) 0 else this.minFilter.getGLEnum()) - (if (o.minFilter == null) 0 else o.minFilter.getGLEnum())
    } else ()
    if (this.magFilter != o.magFilter) {
      return (if (this.magFilter == null) 0 else this.magFilter.getGLEnum()) - (if (o.magFilter == null) 0 else o.magFilter.getGLEnum())
    } else ()
    if (this.uWrap != o.uWrap) {
      return (if (this.uWrap == null) 0 else this.uWrap.getGLEnum()) - (if (o.uWrap == null) 0 else o.uWrap.getGLEnum())
    } else ()
    if (this.vWrap != o.vWrap) {
      return (if (this.vWrap == null) 0 else this.vWrap.getGLEnum()) - (if (o.vWrap == null) 0 else o.vWrap.getGLEnum())
    } else ()
    return 0
  }
}