package com.badlogic.gdx.math

class GridPoint2 extends java.io.Serializable {
  var x: scala.Int = 0
  var y: scala.Int = 0
  def this(x: scala.Int, y: scala.Int) = {
    this()
    this.x = x
    this.y = y
  }
  def this(point: GridPoint2) = {
    this()
    this.x = point.x
    this.y = point.y
  }
  def set(point: GridPoint2): GridPoint2 = {
    this.x = point.x
    this.y = point.y
    return this
  }
  def set(x: scala.Int, y: scala.Int): GridPoint2 = {
    this.x = x
    this.y = y
    return this
  }
  def dst2(other: GridPoint2): scala.Float = {
    val xd: scala.Int = other.x - this.x
    val yd: scala.Int = other.y - this.y
    return (xd * xd) + (yd * yd)
  }
  def dst2(x: scala.Int, y: scala.Int): scala.Float = {
    val xd: scala.Int = x - this.x
    val yd: scala.Int = y - this.y
    return (xd * xd) + (yd * yd)
  }
  def dst(other: GridPoint2): scala.Float = {
    val xd: scala.Int = other.x - this.x
    val yd: scala.Int = other.y - this.y
    return java.lang.Math.sqrt((xd * xd) + (yd * yd)).asInstanceOf[scala.Float]
  }
  def dst(x: scala.Int, y: scala.Int): scala.Float = {
    val xd: scala.Int = x - this.x
    val yd: scala.Int = y - this.y
    return java.lang.Math.sqrt((xd * xd) + (yd * yd)).asInstanceOf[scala.Float]
  }
  def add(other: GridPoint2): GridPoint2 = {
    this.x = this.x + other.x
    this.y = this.y + other.y
    return this
  }
  def add(x: scala.Int, y: scala.Int): GridPoint2 = {
    this.x = this.x + x
    this.y = this.y + y
    return this
  }
  def sub(other: GridPoint2): GridPoint2 = {
    this.x = this.x - other.x
    this.y = this.y - other.y
    return this
  }
  def sub(x: scala.Int, y: scala.Int): GridPoint2 = {
    this.x = this.x - x
    this.y = this.y - y
    return this
  }
  def cpy(): GridPoint2 = {
    return new GridPoint2(this)
  }
  def equals(o: java.lang.Object): scala.Boolean = {
    if (this == o) {
      return true
    } else ()
    if ((o == null) || (o.getClass() != this.getClass())) {
      return false
    } else ()
    val g: GridPoint2 = o.asInstanceOf[GridPoint2]
    return (this.x == g.x) && (this.y == g.y)
  }
  def hashCode(): scala.Int = {
    val prime: scala.Int = 53
    var result: scala.Int = 1
    result = (prime * result) + this.x
    result = (prime * result) + this.y
    return result
  }
  def toString(): java.lang.String = {
    return ((("(" + this.x) + ", ") + this.y) + ")"
  }
}
object GridPoint2 {
  private final val serialVersionUID: scala.Long = -4019969926331717380L
}