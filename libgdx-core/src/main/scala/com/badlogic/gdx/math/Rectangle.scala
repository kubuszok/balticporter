package com.badlogic.gdx.math

class Rectangle extends java.io.Serializable with com.badlogic.gdx.math.Shape2D {
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
  def this(rect: Rectangle) = {
    this()
    this.x = rect.x
    this.y = rect.y
    this.width = rect.width
    this.height = rect.height
  }
  def set(x: scala.Float, y: scala.Float, width: scala.Float, height: scala.Float): Rectangle = {
    this.x = x
    this.y = y
    this.width = width
    this.height = height
    return this
  }
  def getX(): scala.Float = {
    return this.x
  }
  def setX(x: scala.Float): Rectangle = {
    this.x = x
    return this
  }
  def getY(): scala.Float = {
    return this.y
  }
  def setY(y: scala.Float): Rectangle = {
    this.y = y
    return this
  }
  def getWidth(): scala.Float = {
    return this.width
  }
  def setWidth(width: scala.Float): Rectangle = {
    this.width = width
    return this
  }
  def getHeight(): scala.Float = {
    return this.height
  }
  def setHeight(height: scala.Float): Rectangle = {
    this.height = height
    return this
  }
  def getPosition(position: com.badlogic.gdx.math.Vector2): com.badlogic.gdx.math.Vector2 = {
    return position.set(this.x, this.y)
  }
  def setPosition(position: com.badlogic.gdx.math.Vector2): Rectangle = {
    this.x = position.x
    this.y = position.y
    return this
  }
  def setPosition(x: scala.Float, y: scala.Float): Rectangle = {
    this.x = x
    this.y = y
    return this
  }
  def setSize(width: scala.Float, height: scala.Float): Rectangle = {
    this.width = width
    this.height = height
    return this
  }
  def setSize(sizeXY: scala.Float): Rectangle = {
    this.width = sizeXY
    this.height = sizeXY
    return this
  }
  def getSize(size: com.badlogic.gdx.math.Vector2): com.badlogic.gdx.math.Vector2 = {
    return size.set(this.width, this.height)
  }
  def contains(x: scala.Float, y: scala.Float): scala.Boolean = {
    return (((this.x <= x) && ((this.x + this.width) >= x)) && (this.y <= y)) && ((this.y + this.height) >= y)
  }
  def contains(point: com.badlogic.gdx.math.Vector2): scala.Boolean = {
    return this.contains(point.x, point.y)
  }
  def contains(circle: com.badlogic.gdx.math.Circle): scala.Boolean = {
    return ((((circle.x - circle.radius) >= this.x) && ((circle.x + circle.radius) <= (this.x + this.width))) && ((circle.y - circle.radius) >= this.y)) && ((circle.y + circle.radius) <= (this.y + this.height))
  }
  def contains(rectangle: Rectangle): scala.Boolean = {
    val xmin: scala.Float = rectangle.x
    val xmax: scala.Float = xmin + rectangle.width
    val ymin: scala.Float = rectangle.y
    val ymax: scala.Float = ymin + rectangle.height
    return (((xmin > this.x) && (xmin < (this.x + this.width))) && ((xmax > this.x) && (xmax < (this.x + this.width)))) && (((ymin > this.y) && (ymin < (this.y + this.height))) && ((ymax > this.y) && (ymax < (this.y + this.height))))
  }
  def overlaps(r: Rectangle): scala.Boolean = {
    return (((this.x < (r.x + r.width)) && ((this.x + this.width) > r.x)) && (this.y < (r.y + r.height))) && ((this.y + this.height) > r.y)
  }
  def set(rect: Rectangle): Rectangle = {
    this.x = rect.x
    this.y = rect.y
    this.width = rect.width
    this.height = rect.height
    return this
  }
  def merge(rect: Rectangle): Rectangle = {
    val minX: scala.Float = java.lang.Math.min(this.x, rect.x)
    val maxX: scala.Float = java.lang.Math.max(this.x + this.width, rect.x + rect.width)
    this.x = minX
    this.width = maxX - minX
    val minY: scala.Float = java.lang.Math.min(this.y, rect.y)
    val maxY: scala.Float = java.lang.Math.max(this.y + this.height, rect.y + rect.height)
    this.y = minY
    this.height = maxY - minY
    return this
  }
  def merge(x: scala.Float, y: scala.Float): Rectangle = {
    val minX: scala.Float = java.lang.Math.min(this.x, x)
    val maxX: scala.Float = java.lang.Math.max(this.x + this.width, x)
    this.x = minX
    this.width = maxX - minX
    val minY: scala.Float = java.lang.Math.min(this.y, y)
    val maxY: scala.Float = java.lang.Math.max(this.y + this.height, y)
    this.y = minY
    this.height = maxY - minY
    return this
  }
  def merge(vec: com.badlogic.gdx.math.Vector2): Rectangle = {
    return this.merge(vec.x, vec.y)
  }
  def merge(vecs: scala.Array[com.badlogic.gdx.math.Vector2]): Rectangle = {
    var minX: scala.Float = this.x
    var maxX: scala.Float = this.x + this.width
    var minY: scala.Float = this.y
    var maxY: scala.Float = this.y + this.height;
    { var i: scala.Int = 0; while (i < vecs.length) { {
      val v: com.badlogic.gdx.math.Vector2 = vecs(i)
      minX = java.lang.Math.min(minX, v.x)
      maxX = java.lang.Math.max(maxX, v.x)
      minY = java.lang.Math.min(minY, v.y)
      maxY = java.lang.Math.max(maxY, v.y)
    }; i = i + 1 } }
    this.x = minX
    this.width = maxX - minX
    this.y = minY
    this.height = maxY - minY
    return this
  }
  def getAspectRatio(): scala.Float = {
    return if (this.height == 0) java.lang.Float.NaN else this.width / this.height
  }
  def getCenter(vector: com.badlogic.gdx.math.Vector2): com.badlogic.gdx.math.Vector2 = {
    vector.x = this.x + (this.width / 2)
    vector.y = this.y + (this.height / 2)
    return vector
  }
  def setCenter(x: scala.Float, y: scala.Float): Rectangle = {
    this.setPosition(x - (this.width / 2), y - (this.height / 2))
    return this
  }
  def setCenter(position: com.badlogic.gdx.math.Vector2): Rectangle = {
    this.setPosition(position.x - (this.width / 2), position.y - (this.height / 2))
    return this
  }
  def fitOutside(rect: Rectangle): Rectangle = {
    val ratio: scala.Float = this.getAspectRatio()
    if (ratio > rect.getAspectRatio()) {
      this.setSize(rect.height * ratio, rect.height)
    } else {
      this.setSize(rect.width, rect.width / ratio)
    }
    this.setPosition((rect.x + (rect.width / 2)) - (this.width / 2), (rect.y + (rect.height / 2)) - (this.height / 2))
    return this
  }
  def fitInside(rect: Rectangle): Rectangle = {
    val ratio: scala.Float = this.getAspectRatio()
    if (ratio < rect.getAspectRatio()) {
      this.setSize(rect.height * ratio, rect.height)
    } else {
      this.setSize(rect.width, rect.width / ratio)
    }
    this.setPosition((rect.x + (rect.width / 2)) - (this.width / 2), (rect.y + (rect.height / 2)) - (this.height / 2))
    return this
  }
  def toString(): java.lang.String = {
    return ((((((("[" + this.x) + ",") + this.y) + ",") + this.width) + ",") + this.height) + "]"
  }
  def fromString(v: java.lang.String): Rectangle = {
    val s0: scala.Int = v.indexOf(',', 1)
    val s1: scala.Int = v.indexOf(',', s0 + 1)
    val s2: scala.Int = v.indexOf(',', s1 + 1)
    if (((((s0 != (-1)) && (s1 != (-1))) && (s2 != (-1))) && (v.charAt(0) == '[')) && (v.charAt(v.length() - 1) == ']')) {
      try {
        val x: scala.Float = java.lang.Float.parseFloat(v.substring(1, s0))
        val y: scala.Float = java.lang.Float.parseFloat(v.substring(s0 + 1, s1))
        val width: scala.Float = java.lang.Float.parseFloat(v.substring(s1 + 1, s2))
        val height: scala.Float = java.lang.Float.parseFloat(v.substring(s2 + 1, v.length() - 1))
        return this.set(x, y, width, height)
      } catch {
        case ex: java.lang.NumberFormatException => {
          ()
        }
      }
    } else ()
    throw new com.badlogic.gdx.utils.GdxRuntimeException("Malformed Rectangle: " + v)
  }
  def area(): scala.Float = {
    return this.width * this.height
  }
  def perimeter(): scala.Float = {
    return 2 * (this.width + this.height)
  }
  def hashCode(): scala.Int = {
    val prime: scala.Int = 31
    var result: scala.Int = 1
    result = (prime * result) + com.badlogic.gdx.utils.NumberUtils.floatToRawIntBits(this.height)
    result = (prime * result) + com.badlogic.gdx.utils.NumberUtils.floatToRawIntBits(this.width)
    result = (prime * result) + com.badlogic.gdx.utils.NumberUtils.floatToRawIntBits(this.x)
    result = (prime * result) + com.badlogic.gdx.utils.NumberUtils.floatToRawIntBits(this.y)
    return result
  }
  def equals(obj: java.lang.Object): scala.Boolean = {
    if (this == obj) {
      return true
    } else ()
    if (obj == null) {
      return false
    } else ()
    if (this.getClass() != obj.getClass()) {
      return false
    } else ()
    val other: Rectangle = obj.asInstanceOf[Rectangle]
    if (com.badlogic.gdx.utils.NumberUtils.floatToRawIntBits(this.height) != com.badlogic.gdx.utils.NumberUtils.floatToRawIntBits(other.height)) {
      return false
    } else ()
    if (com.badlogic.gdx.utils.NumberUtils.floatToRawIntBits(this.width) != com.badlogic.gdx.utils.NumberUtils.floatToRawIntBits(other.width)) {
      return false
    } else ()
    if (com.badlogic.gdx.utils.NumberUtils.floatToRawIntBits(this.x) != com.badlogic.gdx.utils.NumberUtils.floatToRawIntBits(other.x)) {
      return false
    } else ()
    if (com.badlogic.gdx.utils.NumberUtils.floatToRawIntBits(this.y) != com.badlogic.gdx.utils.NumberUtils.floatToRawIntBits(other.y)) {
      return false
    } else ()
    return true
  }
}
object Rectangle {
  final val tmp: Rectangle = new Rectangle()
  final val tmp2: Rectangle = new Rectangle()
  private final val serialVersionUID: scala.Long = 5733252015138115702L
}