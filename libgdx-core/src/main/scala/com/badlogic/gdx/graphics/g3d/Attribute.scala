package com.badlogic.gdx.graphics.g3d

abstract class Attribute extends java.lang.Comparable[Attribute] {
  var `type`: scala.Long = 0L
  private var typeBit: scala.Int = 0
  def this(`type`: scala.Long) = {
    this()
    this.`type` = `type`
    this.typeBit = java.lang.Long.numberOfTrailingZeros(`type`)
  }
  def copy(): Attribute
  def equals(other: Attribute): scala.Boolean = {
    return other.hashCode() == this.hashCode()
  }
  def equals(obj: java.lang.Object): scala.Boolean = {
    if (obj == null) {
      return false
    } else ()
    if (obj == this) {
      return true
    } else ()
    if (!obj.isInstanceOf[Attribute]) {
      return false
    } else ()
    val other: Attribute = obj.asInstanceOf[Attribute].asInstanceOf[Attribute]
    if (this.`type` != other.`type`) {
      return false
    } else ()
    return this.equals(other)
  }
  def toString(): java.lang.String = {
    return Attribute.getAttributeAlias(this.`type`)
  }
  def hashCode(): scala.Int = {
    return 7489 * this.typeBit
  }
}
object Attribute {
  private final val types: com.badlogic.gdx.utils.Array[java.lang.String] = new com.badlogic.gdx.utils.Array[java.lang.String]()
  private final val MAX_ATTRIBUTE_COUNT: scala.Int = 64
  final def getAttributeType(alias: java.lang.String): scala.Long = {
    { var i: scala.Int = 0; while (i < Attribute.types.size) { {
      if (Attribute.types.get(i).compareTo(alias) == 0) {
        return 1L << i
      } else ()
    }; i = i + 1 } }
    return 0
  }
  final def getAttributeAlias(`type`: scala.Long): java.lang.String = {
    var idx: scala.Int = -1
    while (((`type` != 0) && ({ idx += 1; idx } < 63)) && (((`type` >> idx) & 1) == 0)) {
      ()
    }
    return if ((idx >= 0) && (idx < Attribute.types.size)) Attribute.types.get(idx) else null
  }
  final def register(alias: java.lang.String): scala.Long = {
    val result: scala.Long = Attribute.getAttributeType(alias)
    if (result > 0) {
      return result
    } else ()
    if (Attribute.types.size >= Attribute.MAX_ATTRIBUTE_COUNT) {
      throw new com.badlogic.gdx.utils.GdxRuntimeException(("Cannot register " + alias) + ", maximum registered attribute count reached.")
    } else ()
    Attribute.types.add(alias)
    return 1L << (Attribute.types.size - 1)
  }
}