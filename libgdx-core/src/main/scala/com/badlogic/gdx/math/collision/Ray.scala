package com.badlogic.gdx.math.collision

class Ray extends java.io.Serializable {
  final val origin: com.badlogic.gdx.math.Vector3 = new com.badlogic.gdx.math.Vector3()
  final val direction: com.badlogic.gdx.math.Vector3 = new com.badlogic.gdx.math.Vector3()
  def this(origin: com.badlogic.gdx.math.Vector3, direction: com.badlogic.gdx.math.Vector3) = {
    this()
    this.origin.set(origin)
    this.direction.set(direction).nor()
  }
  def cpy(): Ray = {
    return new Ray(this.origin, this.direction)
  }
  def getEndPoint(out: com.badlogic.gdx.math.Vector3, distance: scala.Float): com.badlogic.gdx.math.Vector3 = {
    return out.set(this.direction).scl(distance).add(this.origin)
  }
  def mul(matrix: com.badlogic.gdx.math.Matrix4): Ray = {
    Ray.tmp.set(this.origin).add(this.direction)
    Ray.tmp.mul(matrix)
    this.origin.mul(matrix)
    this.direction.set(Ray.tmp.sub(this.origin)).nor()
    return this
  }
  override def toString(): java.lang.String = {
    return ((("ray [" + this.origin) + ":") + this.direction) + "]"
  }
  def set(origin: com.badlogic.gdx.math.Vector3, direction: com.badlogic.gdx.math.Vector3): Ray = {
    this.origin.set(origin)
    this.direction.set(direction).nor()
    return this
  }
  def set(x: scala.Float, y: scala.Float, z: scala.Float, dx: scala.Float, dy: scala.Float, dz: scala.Float): Ray = {
    this.origin.set(x, y, z)
    this.direction.set(dx, dy, dz).nor()
    return this
  }
  def set(ray: Ray): Ray = {
    this.origin.set(ray.origin)
    this.direction.set(ray.direction).nor()
    return this
  }
  @java.lang.Override
  override def equals(o: java.lang.Object): scala.Boolean = {
    if (o == this) {
      return true
    } else ()
    if ((o == null) || (o.getClass() != this.getClass())) {
      return false
    } else ()
    val r: Ray = o.asInstanceOf[Ray].asInstanceOf[Ray]
    return this.direction.equals(r.direction) && this.origin.equals(r.origin)
  }
  @java.lang.Override
  override def hashCode(): scala.Int = {
    val prime: scala.Int = 73
    var result: scala.Int = 1
    result = (prime * result) + this.direction.hashCode()
    result = (prime * result) + this.origin.hashCode()
    return result
  }
}
object Ray {
  private final val serialVersionUID: scala.Long = -620692054835390878L
  var tmp: com.badlogic.gdx.math.Vector3 = new com.badlogic.gdx.math.Vector3()
}