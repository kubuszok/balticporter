package com.badlogic.gdx.graphics.g3d

class Attributes extends balticporter.runtime.JavaIterable[com.badlogic.gdx.graphics.g3d.Attribute] with java.util.Comparator[com.badlogic.gdx.graphics.g3d.Attribute] with java.lang.Comparable[Attributes] {
  var mask: scala.Long = 0L
  final val attributes: com.badlogic.gdx.utils.Array[com.badlogic.gdx.graphics.g3d.Attribute] = new com.badlogic.gdx.utils.Array[com.badlogic.gdx.graphics.g3d.Attribute]()
  var sorted: scala.Boolean = true
  final def sort(): scala.Unit = {
    if (!this.sorted) {
      this.attributes.sort(this)
      this.sorted = true
    } else ()
  }
  final def getMask(): scala.Long = {
    return this.mask
  }
  final def get(`type`: scala.Long): com.badlogic.gdx.graphics.g3d.Attribute = {
    if (this.has(`type`)) {
      { var i: scala.Int = 0; while (i < this.attributes.size) { {
        if (this.attributes.get(i).`type` == `type`) {
          return this.attributes.get(i)
        } else ()
      }; i = i + 1 } }
    } else ()
    return null
  }
  final def get[T <: com.badlogic.gdx.graphics.g3d.Attribute](clazz: java.lang.Class[T], `type`: scala.Long): T = {
    return this.get(`type`).asInstanceOf[T]
  }
  final def get(out: com.badlogic.gdx.utils.Array[com.badlogic.gdx.graphics.g3d.Attribute], `type`: scala.Long): com.badlogic.gdx.utils.Array[com.badlogic.gdx.graphics.g3d.Attribute] = {
    { var i: scala.Int = 0; while (i < this.attributes.size) { {
      if ((this.attributes.get(i).`type` & `type`) != 0) {
        out.add(this.attributes.get(i))
      } else ()
    }; i = i + 1 } }
    return out
  }
  def clear(): scala.Unit = {
    this.mask = 0
    this.attributes.clear()
  }
  def size(): scala.Int = {
    return this.attributes.size
  }
  private final def enable(mask: scala.Long): scala.Unit = {
    this.mask = this.mask | mask
  }
  private final def disable(mask: scala.Long): scala.Unit = {
    this.mask = this.mask & (~mask)
  }
  final def set(attribute: com.badlogic.gdx.graphics.g3d.Attribute): scala.Unit = {
    val idx: scala.Int = this.indexOf(attribute.`type`)
    if (idx < 0) {
      this.enable(attribute.`type`)
      this.attributes.add(attribute)
      this.sorted = false
    } else {
      this.attributes.set(idx, attribute)
    }
    this.sort()
  }
  final def set(attribute1: com.badlogic.gdx.graphics.g3d.Attribute, attribute2: com.badlogic.gdx.graphics.g3d.Attribute): scala.Unit = {
    this.set(attribute1)
    this.set(attribute2)
  }
  final def set(attribute1: com.badlogic.gdx.graphics.g3d.Attribute, attribute2: com.badlogic.gdx.graphics.g3d.Attribute, attribute3: com.badlogic.gdx.graphics.g3d.Attribute): scala.Unit = {
    this.set(attribute1)
    this.set(attribute2)
    this.set(attribute3)
  }
  final def set(attribute1: com.badlogic.gdx.graphics.g3d.Attribute, attribute2: com.badlogic.gdx.graphics.g3d.Attribute, attribute3: com.badlogic.gdx.graphics.g3d.Attribute, attribute4: com.badlogic.gdx.graphics.g3d.Attribute): scala.Unit = {
    this.set(attribute1)
    this.set(attribute2)
    this.set(attribute3)
    this.set(attribute4)
  }
  final def set(attributes: scala.Array[com.badlogic.gdx.graphics.g3d.Attribute]): scala.Unit = {
    for (attr <- attributes) {
      this.set(attr)
    }
  }
  final def set(attributes: balticporter.runtime.JavaIterable[com.badlogic.gdx.graphics.g3d.Attribute]): scala.Unit = {
    for (attr <- attributes) {
      this.set(attr)
    }
  }
  final def remove(mask: scala.Long): scala.Unit = {
    { var i: scala.Int = this.attributes.size - 1; while (i >= 0) { {
      val `type`: scala.Long = this.attributes.get(i).`type`
      if ((mask & `type`) == `type`) {
        this.attributes.removeIndex(i)
        this.disable(`type`)
        this.sorted = false
      } else ()
    }; i = i - 1 } }
    this.sort()
  }
  final def has(`type`: scala.Long): scala.Boolean = {
    return (`type` != 0) && ((this.mask & `type`) == `type`)
  }
  def indexOf(`type`: scala.Long): scala.Int = {
    if (this.has(`type`)) {
      { var i: scala.Int = 0; while (i < this.attributes.size) { {
        if (this.attributes.get(i).`type` == `type`) {
          return i
        } else ()
      }; i = i + 1 } }
    } else ()
    return -1
  }
  final def same(other: Attributes, compareValues: scala.Boolean): scala.Boolean = {
    if (other == this) {
      return true
    } else ()
    if ((other == null) || (this.mask != other.mask)) {
      return false
    } else ()
    if (!compareValues) {
      return true
    } else ()
    this.sort()
    other.sort();
    { var i: scala.Int = 0; while (i < this.attributes.size) { {
      if (!this.attributes.get(i).equals(other.attributes.get(i))) {
        return false
      } else ()
    }; i = i + 1 } }
    return true
  }
  final def same(other: Attributes): scala.Boolean = {
    return this.same(other, false)
  }
  @java.lang.Override
  override final def compare(arg0: com.badlogic.gdx.graphics.g3d.Attribute, arg1: com.badlogic.gdx.graphics.g3d.Attribute): scala.Int = {
    return (arg0.`type` - arg1.`type`).asInstanceOf[scala.Int].asInstanceOf[scala.Int]
  }
  @java.lang.Override
  override final def iterator(): balticporter.runtime.JavaIterator[com.badlogic.gdx.graphics.g3d.Attribute] = {
    return this.attributes.iterator()
  }
  def attributesHash(): scala.Int = {
    this.sort()
    val n: scala.Int = this.attributes.size
    var result: scala.Long = 71 + this.mask
    var m: scala.Int = 1;
    { var i: scala.Int = 0; while (i < n) { {
      result = result + ((this.mask * this.attributes.get(i).hashCode()) * {
        m = (m * 7) & 65535
        m
      })
    }; i = i + 1 } }
    return (result ^ (result >> 32)).asInstanceOf[scala.Int].asInstanceOf[scala.Int]
  }
  @java.lang.Override
  override def hashCode(): scala.Int = {
    return this.attributesHash()
  }
  @java.lang.Override
  override def equals(other: java.lang.Object): scala.Boolean = {
    if (!other.isInstanceOf[Attributes]) {
      return false
    } else ()
    if (other == this) {
      return true
    } else ()
    return this.same(other.asInstanceOf[Attributes].asInstanceOf[Attributes], true)
  }
  @java.lang.Override
  override def compareTo(other: Attributes): scala.Int = {
    if (other == this) {
      return 0
    } else ()
    if (this.mask != other.mask) {
      return if (this.mask < other.mask) -1 else 1
    } else ()
    this.sort()
    other.sort();
    { var i: scala.Int = 0; while (i < this.attributes.size) { {
      val c: scala.Int = this.attributes.get(i).compareTo(other.attributes.get(i))
      if (c != 0) {
        return if (c < 0) -1 else if (c > 0) 1 else 0
      } else ()
    }; i = i + 1 } }
    return 0
  }
}