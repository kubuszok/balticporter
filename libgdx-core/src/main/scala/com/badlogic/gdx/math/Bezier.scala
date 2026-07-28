package com.badlogic.gdx.math

class Bezier[T <: com.badlogic.gdx.math.Vector[T]] extends com.badlogic.gdx.math.Path[T] {
  var points: com.badlogic.gdx.utils.Array[T] = new com.badlogic.gdx.utils.Array[T]()
  private var tmp: T = null.asInstanceOf[T]
  private var tmp2: T = null.asInstanceOf[T]
  private var tmp3: T = null.asInstanceOf[T]
  def this(points: scala.Array[T]) = {
    this()
    this.set(points)
  }
  def this(points: scala.Array[T], offset: scala.Int, length: scala.Int) = {
    this()
    this.set(points, offset, length)
  }
  def this(points: com.badlogic.gdx.utils.Array[T], offset: scala.Int, length: scala.Int) = {
    this()
    this.set(points, offset, length)
  }
  def set(points: scala.Array[T]): Bezier[T] = {
    return this.set(points, 0, points.length).asInstanceOf[Bezier[T]]
  }
  def set(points: scala.Array[T], offset: scala.Int, length: scala.Int): Bezier[T] = {
    if ((length < 2) || (length > 4)) {
      throw new com.badlogic.gdx.utils.GdxRuntimeException("Only first, second and third degree Bezier curves are supported.")
    } else ()
    if (this.tmp == null) {
      this.tmp = points(0).cpy().asInstanceOf[T]
    } else ()
    if (this.tmp2 == null) {
      this.tmp2 = points(0).cpy().asInstanceOf[T]
    } else ()
    if (this.tmp3 == null) {
      this.tmp3 = points(0).cpy().asInstanceOf[T]
    } else ()
    this.points.clear()
    this.points.addAll(points, offset, length)
    return this.asInstanceOf[Bezier[T]]
  }
  def set(points: com.badlogic.gdx.utils.Array[T], offset: scala.Int, length: scala.Int): Bezier[T] = {
    if ((length < 2) || (length > 4)) {
      throw new com.badlogic.gdx.utils.GdxRuntimeException("Only first, second and third degree Bezier curves are supported.")
    } else ()
    if (this.tmp == null) {
      this.tmp = points.get(0).cpy().asInstanceOf[T]
    } else ()
    if (this.tmp2 == null) {
      this.tmp2 = points.get(0).cpy().asInstanceOf[T]
    } else ()
    if (this.tmp3 == null) {
      this.tmp3 = points.get(0).cpy().asInstanceOf[T]
    } else ()
    this.points.clear()
    this.points.addAll(points.asInstanceOf[com.badlogic.gdx.utils.Array[? <: T]], offset, length)
    return this.asInstanceOf[Bezier[T]]
  }
  @java.lang.Override
  def valueAt(out: T, t: scala.Float): T = {
    val n: scala.Int = this.points.size
    if (n == 2) {
      Bezier.linear(out, t, this.points.get(0), this.points.get(1), this.tmp)
    } else {
      if (n == 3) {
        Bezier.quadratic(out, t, this.points.get(0), this.points.get(1), this.points.get(2), this.tmp)
      } else {
        if (n == 4) {
          Bezier.cubic(out, t, this.points.get(0), this.points.get(1), this.points.get(2), this.points.get(3), this.tmp)
        } else ()
      }
    }
    return out
  }
  @java.lang.Override
  def derivativeAt(out: T, t: scala.Float): T = {
    val n: scala.Int = this.points.size
    if (n == 2) {
      Bezier.linear_derivative(out, t, this.points.get(0), this.points.get(1), this.tmp)
    } else {
      if (n == 3) {
        Bezier.quadratic_derivative(out, t, this.points.get(0), this.points.get(1), this.points.get(2), this.tmp)
      } else {
        if (n == 4) {
          Bezier.cubic_derivative(out, t, this.points.get(0), this.points.get(1), this.points.get(2), this.points.get(3), this.tmp)
        } else ()
      }
    }
    return out
  }
  @java.lang.Override
  def approximate(v: T): scala.Float = {
    val p1: T = this.points.get(0)
    val p2: T = this.points.get(this.points.size - 1)
    val p3: T = v
    val l1Sqr: scala.Float = p1.dst2(p2)
    val l2Sqr: scala.Float = p3.dst2(p2)
    val l3Sqr: scala.Float = p3.dst2(p1)
    val l1: scala.Float = java.lang.Math.sqrt(l1Sqr).asInstanceOf[scala.Float].asInstanceOf[scala.Float]
    val s: scala.Float = ((l2Sqr + l1Sqr) - l3Sqr) / (2 * l1)
    return (com.badlogic.gdx.math.MathUtils.clamp: (scala.Float, scala.Float, scala.Float) => scala.Float)((l1 - s) / l1, 0.0f, 1.0f)
  }
  @java.lang.Override
  def locate(v: T): scala.Float = {
    return this.approximate(v)
  }
  @java.lang.Override
  def approxLength(samples: scala.Int): scala.Float = {
    var tempLength: scala.Float = 0;
    { var i: scala.Int = 0; while (i < samples) { {
      this.tmp2.set(this.tmp3)
      this.valueAt(this.tmp3, i / (samples.asInstanceOf[scala.Float] - 1))
      if (i > 0) {
        tempLength = tempLength + this.tmp2.dst(this.tmp3)
      } else ()
    }; i = i + 1 } }
    return tempLength
  }
}
object Bezier {
  def linear[T <: com.badlogic.gdx.math.Vector[T]](out: T, t: scala.Float, p0: T, p1: T, tmp: T): T = {
    return out.set(p0).scl(1.0f - t).add(tmp.set(p1).scl(t))
  }
  def linear_derivative[T <: com.badlogic.gdx.math.Vector[T]](out: T, t: scala.Float, p0: T, p1: T, tmp: T): T = {
    return out.set(p1).sub(p0)
  }
  def quadratic[T <: com.badlogic.gdx.math.Vector[T]](out: T, t: scala.Float, p0: T, p1: T, p2: T, tmp: T): T = {
    val dt: scala.Float = 1.0f - t
    return out.set(p0).scl(dt * dt).add(tmp.set(p1).scl((2 * dt) * t)).add(tmp.set(p2).scl(t * t))
  }
  def quadratic_derivative[T <: com.badlogic.gdx.math.Vector[T]](out: T, t: scala.Float, p0: T, p1: T, p2: T, tmp: T): T = {
    val dt: scala.Float = 1.0f - t
    return out.set(p1).sub(p0).scl(2).scl(1 - t).add(tmp.set(p2).sub(p1).scl(t).scl(2))
  }
  def cubic[T <: com.badlogic.gdx.math.Vector[T]](out: T, t: scala.Float, p0: T, p1: T, p2: T, p3: T, tmp: T): T = {
    val dt: scala.Float = 1.0f - t
    val dt2: scala.Float = dt * dt
    val t2: scala.Float = t * t
    return out.set(p0).scl(dt2 * dt).add(tmp.set(p1).scl((3 * dt2) * t)).add(tmp.set(p2).scl((3 * dt) * t2)).add(tmp.set(p3).scl(t2 * t))
  }
  def cubic_derivative[T <: com.badlogic.gdx.math.Vector[T]](out: T, t: scala.Float, p0: T, p1: T, p2: T, p3: T, tmp: T): T = {
    val dt: scala.Float = 1.0f - t
    val dt2: scala.Float = dt * dt
    val t2: scala.Float = t * t
    return out.set(p1).sub(p0).scl(dt2 * 3).add(tmp.set(p2).sub(p1).scl((dt * t) * 6)).add(tmp.set(p3).sub(p2).scl(t2 * 3))
  }
}