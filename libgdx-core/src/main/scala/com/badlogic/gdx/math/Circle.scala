package com.badlogic.gdx.math

class Circle extends java.io.Serializable with com.badlogic.gdx.math.Shape2D {
  var x: scala.Float = 0.0f
  var y: scala.Float = 0.0f
  var radius: scala.Float = 0.0f
  def this(x: scala.Float, y: scala.Float, radius: scala.Float) = {
    this()
    this.x = x
    this.y = y
    this.radius = radius
  }
  def this(position: com.badlogic.gdx.math.Vector2, radius: scala.Float) = {
    this()
    this.x = position.x
    this.y = position.y
    this.radius = radius
  }
  def this(center: com.badlogic.gdx.math.Vector2, edge: com.badlogic.gdx.math.Vector2) = {
    this()
    this.x = center.x
    this.y = center.y
    this.radius = com.badlogic.gdx.math.Vector2.len(center.x - edge.x, center.y - edge.y)
  }
  def this(circle: Circle) = {
    this()
    this.x = circle.x
    this.y = circle.y
    this.radius = circle.radius
  }
  def set(x: scala.Float, y: scala.Float, radius: scala.Float): scala.Unit = {
    this.x = x
    this.y = y
    this.radius = radius
  }
  def set(position: com.badlogic.gdx.math.Vector2, radius: scala.Float): scala.Unit = {
    this.x = position.x
    this.y = position.y
    this.radius = radius
  }
  def set(circle: Circle): scala.Unit = {
    this.x = circle.x
    this.y = circle.y
    this.radius = circle.radius
  }
  def set(center: com.badlogic.gdx.math.Vector2, edge: com.badlogic.gdx.math.Vector2): scala.Unit = {
    this.x = center.x
    this.y = center.y
    this.radius = com.badlogic.gdx.math.Vector2.len(center.x - edge.x, center.y - edge.y)
  }
  def setPosition(position: com.badlogic.gdx.math.Vector2): scala.Unit = {
    this.x = position.x
    this.y = position.y
  }
  def setPosition(x: scala.Float, y: scala.Float): scala.Unit = {
    this.x = x
    this.y = y
  }
  def setX(x: scala.Float): scala.Unit = {
    this.x = x
  }
  def setY(y: scala.Float): scala.Unit = {
    this.y = y
  }
  def setRadius(radius: scala.Float): scala.Unit = {
    this.radius = radius
  }
  def contains(x$arg: scala.Float, y$arg: scala.Float): scala.Boolean = {
    var x: scala.Float = x$arg
    var y: scala.Float = y$arg
    x = this.x - x
    y = this.y - y
    return ((x * x) + (y * y)) <= (this.radius * this.radius)
  }
  def contains(point: com.badlogic.gdx.math.Vector2): scala.Boolean = {
    val dx: scala.Float = this.x - point.x
    val dy: scala.Float = this.y - point.y
    return ((dx * dx) + (dy * dy)) <= (this.radius * this.radius)
  }
  def contains(c: Circle): scala.Boolean = {
    val radiusDiff: scala.Float = this.radius - c.radius
    if (radiusDiff < 0.0f) {
      return false
    } else ()
    val dx: scala.Float = this.x - c.x
    val dy: scala.Float = this.y - c.y
    val dst: scala.Float = (dx * dx) + (dy * dy)
    val radiusSum: scala.Float = this.radius + c.radius
    return (!((radiusDiff * radiusDiff) < dst)) && (dst < (radiusSum * radiusSum))
  }
  def overlaps(c: Circle): scala.Boolean = {
    val dx: scala.Float = this.x - c.x
    val dy: scala.Float = this.y - c.y
    val distance: scala.Float = (dx * dx) + (dy * dy)
    val radiusSum: scala.Float = this.radius + c.radius
    return distance < (radiusSum * radiusSum)
  }
  def toString(): java.lang.String = {
    return (((this.x + ",") + this.y) + ",") + this.radius
  }
  def circumference(): scala.Float = {
    return this.radius * com.badlogic.gdx.math.MathUtils.PI2
  }
  def area(): scala.Float = {
    return (this.radius * this.radius) * com.badlogic.gdx.math.MathUtils.PI
  }
  def equals(o: java.lang.Object): scala.Boolean = {
    if (o == this) {
      return true
    } else ()
    if ((o == null) || (o.getClass() != this.getClass())) {
      return false
    } else ()
    val c: Circle = o.asInstanceOf[Circle]
    return ((this.x == c.x) && (this.y == c.y)) && (this.radius == c.radius)
  }
  def hashCode(): scala.Int = {
    val prime: scala.Int = 41
    var result: scala.Int = 1
    result = (prime * result) + com.badlogic.gdx.utils.NumberUtils.floatToRawIntBits(this.radius)
    result = (prime * result) + com.badlogic.gdx.utils.NumberUtils.floatToRawIntBits(this.x)
    result = (prime * result) + com.badlogic.gdx.utils.NumberUtils.floatToRawIntBits(this.y)
    return result
  }
}