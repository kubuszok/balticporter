package com.badlogic.gdx.math

class GridPoint3 extends java.io.Serializable {
  var x: scala.Int = 0
  var y: scala.Int = 0
  var z: scala.Int = 0
  def this(x: scala.Int, y: scala.Int, z: scala.Int) = {
    this()
    this.x = x
    this.y = y
    this.z = z
  }
  def this(point: GridPoint3) = {
    this()
    this.x = point.x
    this.y = point.y
    this.z = point.z
  }
  def set(point: GridPoint3): GridPoint3 = {
    this.x = point.x
    this.y = point.y
    this.z = point.z
    return this
  }
  def set(x: scala.Int, y: scala.Int, z: scala.Int): GridPoint3 = {
    this.x = x
    this.y = y
    this.z = z
    return this
  }
  def dst2(other: GridPoint3): scala.Float = {
    val xd: scala.Int = other.x - this.x
    val yd: scala.Int = other.y - this.y
    val zd: scala.Int = other.z - this.z
    return ((xd * xd) + (yd * yd)) + (zd * zd)
  }
  def dst2(x: scala.Int, y: scala.Int, z: scala.Int): scala.Float = {
    val xd: scala.Int = x - this.x
    val yd: scala.Int = y - this.y
    val zd: scala.Int = z - this.z
    return ((xd * xd) + (yd * yd)) + (zd * zd)
  }
  def dst(other: GridPoint3): scala.Float = {
    val xd: scala.Int = other.x - this.x
    val yd: scala.Int = other.y - this.y
    val zd: scala.Int = other.z - this.z
    return java.lang.Math.sqrt(((xd * xd) + (yd * yd)) + (zd * zd)).asInstanceOf[scala.Float].asInstanceOf[scala.Float]
  }
  def dst(x: scala.Int, y: scala.Int, z: scala.Int): scala.Float = {
    val xd: scala.Int = x - this.x
    val yd: scala.Int = y - this.y
    val zd: scala.Int = z - this.z
    return java.lang.Math.sqrt(((xd * xd) + (yd * yd)) + (zd * zd)).asInstanceOf[scala.Float].asInstanceOf[scala.Float]
  }
  def add(other: GridPoint3): GridPoint3 = {
    this.x = this.x + other.x
    this.y = this.y + other.y
    this.z = this.z + other.z
    return this
  }
  def add(x: scala.Int, y: scala.Int, z: scala.Int): GridPoint3 = {
    this.x = this.x + x
    this.y = this.y + y
    this.z = this.z + z
    return this
  }
  def sub(other: GridPoint3): GridPoint3 = {
    this.x = this.x - other.x
    this.y = this.y - other.y
    this.z = this.z - other.z
    return this
  }
  def sub(x: scala.Int, y: scala.Int, z: scala.Int): GridPoint3 = {
    this.x = this.x - x
    this.y = this.y - y
    this.z = this.z - z
    return this
  }
  def cpy(): GridPoint3 = {
    return new GridPoint3(this)
  }
  @java.lang.Override
  override def equals(o: java.lang.Object): scala.Boolean = {
    if (this == o) {
      return true
    } else ()
    if ((o == null) || (o.getClass() != this.getClass())) {
      return false
    } else ()
    val g: GridPoint3 = o.asInstanceOf[GridPoint3].asInstanceOf[GridPoint3]
    return ((this.x == g.x) && (this.y == g.y)) && (this.z == g.z)
  }
  @java.lang.Override
  override def hashCode(): scala.Int = {
    val prime: scala.Int = 17
    var result: scala.Int = 1
    result = (prime * result) + this.x
    result = (prime * result) + this.y
    result = (prime * result) + this.z
    return result
  }
  @java.lang.Override
  override def toString(): java.lang.String = {
    return ((((("(" + this.x) + ", ") + this.y) + ", ") + this.z) + ")"
  }
}
object GridPoint3 {
  private final val serialVersionUID: scala.Long = 5922187982746752830L
}