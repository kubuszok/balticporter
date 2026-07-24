package com.badlogic.gdx.graphics.g3d.environment

class SphericalHarmonics {
  var data: scala.Array[scala.Float] = null.asInstanceOf[scala.Array[scala.Float]]
  def this(copyFrom: scala.Array[scala.Float]) = {
    this()
    if (copyFrom.length != (9 * 3)) {
      throw new com.badlogic.gdx.utils.GdxRuntimeException("Incorrect array size")
    } else ()
    this.data = copyFrom.clone()
  }
  def this() = {
    this()
    this.data = new Array[scala.Float](9 * 3)
  }
  def set(values: scala.Array[scala.Float]): SphericalHarmonics = {
    { var i: scala.Int = 0; while (i < this.data.length) { {
      this.data(i) = values(i)
    }; i = i + 1 } }
    return this
  }
  def set(other: com.badlogic.gdx.graphics.g3d.environment.AmbientCubemap): SphericalHarmonics = {
    return this.set(other.data)
  }
  def set(color: com.badlogic.gdx.graphics.Color): SphericalHarmonics = {
    return this.set(color.r, color.g, color.b)
  }
  def set(r: scala.Float, g: scala.Float, b: scala.Float): SphericalHarmonics = {
    { var idx: scala.Int = 0; while (idx < this.data.length) { {
      this.data({ idx += 1; idx }) = r
      this.data({ idx += 1; idx }) = g
      this.data({ idx += 1; idx }) = b
    };  } }
    return this
  }
}
object SphericalHarmonics {
  private final val coeff: scala.Array[scala.Float] = Array[scala.Float](0.282095f, 0.488603f, 0.488603f, 0.488603f, 1.092548f, 1.092548f, 1.092548f, 0.315392f, 0.546274f)
  private final def clamp(v: scala.Float): scala.Float = {
    return if (v < 0.0f) 0.0f else if (v > 1.0f) 1.0f else v
  }
}