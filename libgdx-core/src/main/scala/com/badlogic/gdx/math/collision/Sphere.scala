package com.badlogic.gdx.math.collision

class Sphere(center$p: com.badlogic.gdx.math.Vector3, radius$p: scala.Float) extends java.io.Serializable {
  var radius: scala.Float = 0.0f
  var center: com.badlogic.gdx.math.Vector3 = null.asInstanceOf[com.badlogic.gdx.math.Vector3]
  this.center = new com.badlogic.gdx.math.Vector3(center$p)
  this.radius = radius$p
  def overlaps(sphere: Sphere): scala.Boolean = {
    return this.center.dst2(sphere.center) < ((this.radius + sphere.radius) * (this.radius + sphere.radius))
  }
  def hashCode(): scala.Int = {
    val prime: scala.Int = 71
    var result: scala.Int = 1
    result = (prime * result) + this.center.hashCode()
    result = (prime * result) + com.badlogic.gdx.utils.NumberUtils.floatToRawIntBits(this.radius)
    return result
  }
  def equals(o: java.lang.Object): scala.Boolean = {
    if (this == o) {
      return true
    } else ()
    if ((o == null) || (o.getClass() != this.getClass())) {
      return false
    } else ()
    val s: Sphere = o.asInstanceOf[Sphere].asInstanceOf[Sphere]
    return (this.radius == s.radius) && this.center.equals(s.center)
  }
  def volume(): scala.Float = {
    return ((Sphere.PI_4_3 * this.radius) * this.radius) * this.radius
  }
  def surfaceArea(): scala.Float = {
    return ((4 * com.badlogic.gdx.math.MathUtils.PI) * this.radius) * this.radius
  }
}
object Sphere {
  private final val serialVersionUID: scala.Long = -6487336868908521596L
  private final val PI_4_3: scala.Float = (com.badlogic.gdx.math.MathUtils.PI * 4.0f) / 3.0f
}