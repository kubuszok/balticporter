package com.badlogic.gdx.math

class Ellipse extends java.io.Serializable with com.badlogic.gdx.math.Shape2D {
  var x: scala.Float = 0.0f
  var y: scala.Float = 0.0f
  var width: scala.Float = 0.0f
  var height: scala.Float = 0.0f
  def this(x: scala.Float, y: scala.Float, width: scala.Float, height: scala.Float) = {
    this()
    this.x = x
    this.y = y
    this.width = width
    this.height = height
  }
  def this(position: com.badlogic.gdx.math.Vector2, width: scala.Float, height: scala.Float) = {
    this()
    this.x = position.x
    this.y = position.y
    this.width = width
    this.height = height
  }
  def this(position: com.badlogic.gdx.math.Vector2, size: com.badlogic.gdx.math.Vector2) = {
    this()
    this.x = position.x
    this.y = position.y
    this.width = size.x
    this.height = size.y
  }
  def this(ellipse: Ellipse) = {
    this()
    this.x = ellipse.x
    this.y = ellipse.y
    this.width = ellipse.width
    this.height = ellipse.height
  }
  def this(circle: com.badlogic.gdx.math.Circle) = {
    this()
    this.x = circle.x
    this.y = circle.y
    this.width = circle.radius * 2.0f
    this.height = circle.radius * 2.0f
  }
  def contains(x$arg: scala.Float, y$arg: scala.Float): scala.Boolean = {
    var x: scala.Float = x$arg
    var y: scala.Float = y$arg
    x = x - this.x
    y = y - this.y
    return (((x * x) / (((this.width * 0.5f) * this.width) * 0.5f)) + ((y * y) / (((this.height * 0.5f) * this.height) * 0.5f))) <= 1.0f
  }
  def contains(point: com.badlogic.gdx.math.Vector2): scala.Boolean = {
    return this.contains(point.x, point.y)
  }
  def set(x: scala.Float, y: scala.Float, width: scala.Float, height: scala.Float): scala.Unit = {
    this.x = x
    this.y = y
    this.width = width
    this.height = height
  }
  def set(ellipse: Ellipse): scala.Unit = {
    this.x = ellipse.x
    this.y = ellipse.y
    this.width = ellipse.width
    this.height = ellipse.height
  }
  def set(circle: com.badlogic.gdx.math.Circle): scala.Unit = {
    this.x = circle.x
    this.y = circle.y
    this.width = circle.radius * 2.0f
    this.height = circle.radius * 2.0f
  }
  def set(position: com.badlogic.gdx.math.Vector2, size: com.badlogic.gdx.math.Vector2): scala.Unit = {
    this.x = position.x
    this.y = position.y
    this.width = size.x
    this.height = size.y
  }
  def setPosition(position: com.badlogic.gdx.math.Vector2): Ellipse = {
    this.x = position.x
    this.y = position.y
    return this
  }
  def setPosition(x: scala.Float, y: scala.Float): Ellipse = {
    this.x = x
    this.y = y
    return this
  }
  def setSize(width: scala.Float, height: scala.Float): Ellipse = {
    this.width = width
    this.height = height
    return this
  }
  def area(): scala.Float = {
    return (com.badlogic.gdx.math.MathUtils.PI * (this.width * this.height)) / 4
  }
  def circumference(): scala.Float = {
    val a: scala.Float = this.width / 2
    val b: scala.Float = this.height / 2
    if (((a * 3) > b) || ((b * 3) > a)) {
      return (com.badlogic.gdx.math.MathUtils.PI * ((3 * (a + b)) - java.lang.Math.sqrt(((3 * a) + b) * (a + (3 * b))))).asInstanceOf[scala.Float].asInstanceOf[scala.Float]
    } else {
      return (com.badlogic.gdx.math.MathUtils.PI2 * java.lang.Math.sqrt(((a * a) + (b * b)) / 2)).asInstanceOf[scala.Float].asInstanceOf[scala.Float]
    }
  }
  def toString(): java.lang.String = {
    return ((((((("[" + this.x) + ",") + this.y) + ",") + this.width) + ",") + this.height) + "]"
  }
  def equals(o: java.lang.Object): scala.Boolean = {
    if (o == this) {
      return true
    } else ()
    if ((o == null) || (o.getClass() != this.getClass())) {
      return false
    } else ()
    val e: Ellipse = o.asInstanceOf[Ellipse].asInstanceOf[Ellipse]
    return (((this.x == e.x) && (this.y == e.y)) && (this.width == e.width)) && (this.height == e.height)
  }
  def hashCode(): scala.Int = {
    val prime: scala.Int = 53
    var result: scala.Int = 1
    result = (prime * result) + com.badlogic.gdx.utils.NumberUtils.floatToRawIntBits(this.height)
    result = (prime * result) + com.badlogic.gdx.utils.NumberUtils.floatToRawIntBits(this.width)
    result = (prime * result) + com.badlogic.gdx.utils.NumberUtils.floatToRawIntBits(this.x)
    result = (prime * result) + com.badlogic.gdx.utils.NumberUtils.floatToRawIntBits(this.y)
    return result
  }
}
object Ellipse {
  private final val serialVersionUID: scala.Long = 7381533206532032099L
}