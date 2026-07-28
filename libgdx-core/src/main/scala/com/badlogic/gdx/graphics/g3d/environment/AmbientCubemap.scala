package com.badlogic.gdx.graphics.g3d.environment

class AmbientCubemap {
  var data: scala.Array[scala.Float] = null.asInstanceOf[scala.Array[scala.Float]]
  def this(copyFrom: scala.Array[scala.Float]) = {
    this()
    if (copyFrom.length != AmbientCubemap.NUM_VALUES) {
      throw new com.badlogic.gdx.utils.GdxRuntimeException("Incorrect array size")
    } else ()
    this.data = new scala.Array[scala.Float](copyFrom.length)
    java.lang.System.arraycopy(copyFrom, 0, this.data, 0, this.data.length)
  }
  def this(copyFrom: AmbientCubemap) = {
    this(copyFrom.data)
  }
  this.data = new scala.Array[scala.Float](AmbientCubemap.NUM_VALUES)
  def set(values: scala.Array[scala.Float]): AmbientCubemap = {
    { var i: scala.Int = 0; while (i < this.data.length) { {
      this.data(i) = values(i)
    }; i = i + 1 } }
    return this
  }
  def set(other: AmbientCubemap): AmbientCubemap = {
    return this.set(other.data)
  }
  def set(color: com.badlogic.gdx.graphics.Color): AmbientCubemap = {
    return this.set(color.r, color.g, color.b)
  }
  def set(r: scala.Float, g: scala.Float, b: scala.Float): AmbientCubemap = {
    { var idx: scala.Int = 0; while (idx < AmbientCubemap.NUM_VALUES) { {
      this.data(idx) = r
      this.data(idx + 1) = g
      this.data(idx + 2) = b
      idx = idx + 3
    };  } }
    return this
  }
  def getColor(out: com.badlogic.gdx.graphics.Color, side$arg: scala.Int): com.badlogic.gdx.graphics.Color = {
    var side: scala.Int = side$arg
    side = side * 3
    return out.set(this.data(side), this.data(side + 1), this.data(side + 2), 1.0f)
  }
  def clear(): AmbientCubemap = {
    { var i: scala.Int = 0; while (i < this.data.length) { {
      this.data(i) = 0.0f
    }; i = i + 1 } }
    return this
  }
  def clamp(): AmbientCubemap = {
    { var i: scala.Int = 0; while (i < this.data.length) { {
      this.data(i) = AmbientCubemap.clamp(this.data(i))
    }; i = i + 1 } }
    return this
  }
  def add(r: scala.Float, g: scala.Float, b: scala.Float): AmbientCubemap = {
    { var idx: scala.Int = 0; while (idx < this.data.length) { {
      this.data({ idx += 1; idx }) = this.data({ idx += 1; idx }) + r
      this.data({ idx += 1; idx }) = this.data({ idx += 1; idx }) + g
      this.data({ idx += 1; idx }) = this.data({ idx += 1; idx }) + b
    };  } }
    return this
  }
  def add(color: com.badlogic.gdx.graphics.Color): AmbientCubemap = {
    return this.add(color.r, color.g, color.b)
  }
  def add(r: scala.Float, g: scala.Float, b: scala.Float, x: scala.Float, y: scala.Float, z: scala.Float): AmbientCubemap = {
    val x2: scala.Float = x * x
    val y2: scala.Float = y * y
    val z2: scala.Float = z * z
    var d: scala.Float = (x2 + y2) + z2
    if (d == 0.0f) {
      return this
    } else ()
    d = (1.0f / d) * (d + 1.0f)
    val rd: scala.Float = r * d
    val gd: scala.Float = g * d
    val bd: scala.Float = b * d
    var idx: scala.Int = if (x > 0) 0 else 3
    this.data(idx) = this.data(idx) + (x2 * rd)
    this.data(idx + 1) = this.data(idx + 1) + (x2 * gd)
    this.data(idx + 2) = this.data(idx + 2) + (x2 * bd)
    idx = if (y > 0) 6 else 9
    this.data(idx) = this.data(idx) + (y2 * rd)
    this.data(idx + 1) = this.data(idx + 1) + (y2 * gd)
    this.data(idx + 2) = this.data(idx + 2) + (y2 * bd)
    idx = if (z > 0) 12 else 15
    this.data(idx) = this.data(idx) + (z2 * rd)
    this.data(idx + 1) = this.data(idx + 1) + (z2 * gd)
    this.data(idx + 2) = this.data(idx + 2) + (z2 * bd)
    return this
  }
  def add(color: com.badlogic.gdx.graphics.Color, direction: com.badlogic.gdx.math.Vector3): AmbientCubemap = {
    return this.add(color.r, color.g, color.b, direction.x, direction.y, direction.z)
  }
  def add(r: scala.Float, g: scala.Float, b: scala.Float, direction: com.badlogic.gdx.math.Vector3): AmbientCubemap = {
    return this.add(r, g, b, direction.x, direction.y, direction.z)
  }
  def add(color: com.badlogic.gdx.graphics.Color, x: scala.Float, y: scala.Float, z: scala.Float): AmbientCubemap = {
    return this.add(color.r, color.g, color.b, x, y, z)
  }
  def add(color: com.badlogic.gdx.graphics.Color, point: com.badlogic.gdx.math.Vector3, target: com.badlogic.gdx.math.Vector3): AmbientCubemap = {
    return this.add(color.r, color.g, color.b, target.x - point.x, target.y - point.y, target.z - point.z)
  }
  def add(color: com.badlogic.gdx.graphics.Color, point: com.badlogic.gdx.math.Vector3, target: com.badlogic.gdx.math.Vector3, intensity: scala.Float): AmbientCubemap = {
    val t: scala.Float = intensity / (1.0f + target.dst(point))
    return this.add(color.r * t, color.g * t, color.b * t, target.x - point.x, target.y - point.y, target.z - point.z)
  }
  @java.lang.Override
  def toString(): java.lang.String = {
    var result: java.lang.String = "";
    { var i: scala.Int = 0; while (i < this.data.length) { {
      result = result + (((((java.lang.Float.toString(this.data(i)) + ", ") + java.lang.Float.toString(this.data(i + 1))) + ", ") + java.lang.Float.toString(this.data(i + 2))) + "\n")
    }; i = i + 3 } }
    return result
  }
}
object AmbientCubemap {
  private final val NUM_VALUES: scala.Int = 6 * 3
  private final def clamp(v: scala.Float): scala.Float = {
    return if (v < 0.0f) 0.0f else if (v > 1.0f) 1.0f else v
  }
}