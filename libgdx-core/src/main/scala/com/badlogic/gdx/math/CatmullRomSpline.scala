package com.badlogic.gdx.math

class CatmullRomSpline[T <: com.badlogic.gdx.math.Vector[T]] extends com.badlogic.gdx.math.Path[T] {
  var controlPoints: scala.Array[T] = null.asInstanceOf[scala.Array[T]]
  var continuous: scala.Boolean = false
  var spanCount: scala.Int = 0
  private var tmp: T = null.asInstanceOf[T]
  private var tmp2: T = null.asInstanceOf[T]
  private var tmp3: T = null.asInstanceOf[T]
  def this(controlPoints: scala.Array[T], continuous: scala.Boolean) = {
    this()
    this.set(controlPoints, continuous)
  }
  def set(controlPoints: scala.Array[T], continuous: scala.Boolean): CatmullRomSpline[?] = {
    if (this.tmp == null) {
      this.tmp = controlPoints(0).cpy()
    } else ()
    if (this.tmp2 == null) {
      this.tmp2 = controlPoints(0).cpy()
    } else ()
    if (this.tmp3 == null) {
      this.tmp3 = controlPoints(0).cpy()
    } else ()
    this.controlPoints = controlPoints
    this.continuous = continuous
    this.spanCount = if (continuous) controlPoints.length else controlPoints.length - 3
    return this
  }
  def valueAt(out: T, t: scala.Float): T = {
    val n: scala.Int = this.spanCount
    var u: scala.Float = t * n
    val i: scala.Int = if (t >= 1.0f) n - 1 else u.asInstanceOf[scala.Int]
    u = u - i
    return this.valueAt(out, i, u)
  }
  def valueAt(out: T, span: scala.Int, u: scala.Float): T = {
    return CatmullRomSpline.calculate(out, if (this.continuous) span else span + 1, u, this.controlPoints, this.continuous, this.tmp)
  }
  def derivativeAt(out: T, t: scala.Float): T = {
    val n: scala.Int = this.spanCount
    var u: scala.Float = t * n
    val i: scala.Int = if (t >= 1.0f) n - 1 else u.asInstanceOf[scala.Int]
    u = u - i
    return this.derivativeAt(out, i, u)
  }
  def derivativeAt(out: T, span: scala.Int, u: scala.Float): T = {
    return CatmullRomSpline.derivative(out, if (this.continuous) span else span + 1, u, this.controlPoints, this.continuous, this.tmp)
  }
  def nearest(in: T): scala.Int = {
    return this.nearest(in, 0, this.spanCount)
  }
  def nearest(in: T, start$arg: scala.Int, count: scala.Int): scala.Int = {
    var start: scala.Int = start$arg
    while (start < 0) {
      start = start + this.spanCount
    }
    var result: scala.Int = start % this.spanCount
    var dst: scala.Float = in.dst2(this.controlPoints(result));
    { var i: scala.Int = 1; while (i < count) { {
      val idx: scala.Int = (start + i) % this.spanCount
      val d: scala.Float = in.dst2(this.controlPoints(idx))
      if (d < dst) {
        dst = d
        result = idx
      } else ()
    }; i = i + 1 } }
    return result
  }
  def approximate(v: T): scala.Float = {
    return this.approximate(v, this.nearest(v))
  }
  def approximate(in: T, start: scala.Int, count: scala.Int): scala.Float = {
    return this.approximate(in, this.nearest(in, start, count))
  }
  def approximate(in: T, near: scala.Int): scala.Float = {
    var n: scala.Int = near
    val nearest: T = this.controlPoints(n)
    val previous: T = this.controlPoints(if (n > 0) n - 1 else this.spanCount - 1)
    val next: T = this.controlPoints((n + 1) % this.spanCount)
    val dstPrev2: scala.Float = in.dst2(previous)
    val dstNext2: scala.Float = in.dst2(next)
    var P1: T = null.asInstanceOf[T]
    var P2: T = null.asInstanceOf[T]
    var P3: T = null.asInstanceOf[T]
    if (dstNext2 < dstPrev2) {
      P1 = nearest
      P2 = next
      P3 = in
    } else {
      P1 = previous
      P2 = nearest
      P3 = in
      n = if (n > 0) n - 1 else this.spanCount - 1
    }
    val L1Sqr: scala.Float = P1.dst2(P2)
    val L2Sqr: scala.Float = P3.dst2(P2)
    val L3Sqr: scala.Float = P3.dst2(P1)
    val L1: scala.Float = java.lang.Math.sqrt(L1Sqr).asInstanceOf[scala.Float].asInstanceOf[scala.Float]
    val s: scala.Float = ((L2Sqr + L1Sqr) - L3Sqr) / (2.0f * L1)
    val u: scala.Float = com.badlogic.gdx.math.MathUtils.clamp((L1 - s) / L1, 0.0f, 1.0f)
    return (n + u) / this.spanCount
  }
  def locate(v: T): scala.Float = {
    return this.approximate(v)
  }
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
object CatmullRomSpline {
  def calculate[T <: com.badlogic.gdx.math.Vector[T]](out: T, t: scala.Float, points: scala.Array[T], continuous: scala.Boolean, tmp: T): T = {
    val n: scala.Int = if (continuous) points.length else points.length - 3
    var u: scala.Float = t * n
    val i: scala.Int = if (t >= 1.0f) n - 1 else u.asInstanceOf[scala.Int]
    u = u - i
    return CatmullRomSpline.calculate(out, i, u, points, continuous, tmp)
  }
  def calculate[T <: com.badlogic.gdx.math.Vector[T]](out: T, i: scala.Int, u: scala.Float, points: scala.Array[T], continuous: scala.Boolean, tmp: T): T = {
    val n: scala.Int = points.length
    val u2: scala.Float = u * u
    val u3: scala.Float = u2 * u
    out.set(points(i)).scl(((1.5f * u3) - (2.5f * u2)) + 1.0f)
    if (continuous || (i > 0)) {
      out.add(tmp.set(points(((n + i) - 1) % n)).scl((((-0.5f) * u3) + u2) - (0.5f * u)))
    } else ()
    if (continuous || (i < (n - 1))) {
      out.add(tmp.set(points((i + 1) % n)).scl((((-1.5f) * u3) + (2.0f * u2)) + (0.5f * u)))
    } else ()
    if (continuous || (i < (n - 2))) {
      out.add(tmp.set(points((i + 2) % n)).scl((0.5f * u3) - (0.5f * u2)))
    } else ()
    return out
  }
  def derivative[T <: com.badlogic.gdx.math.Vector[T]](out: T, t: scala.Float, points: scala.Array[T], continuous: scala.Boolean, tmp: T): T = {
    val n: scala.Int = if (continuous) points.length else points.length - 3
    var u: scala.Float = t * n
    val i: scala.Int = if (t >= 1.0f) n - 1 else u.asInstanceOf[scala.Int]
    u = u - i
    return CatmullRomSpline.derivative(out, i, u, points, continuous, tmp)
  }
  def derivative[T <: com.badlogic.gdx.math.Vector[T]](out: T, i: scala.Int, u: scala.Float, points: scala.Array[T], continuous: scala.Boolean, tmp: T): T = {
    val n: scala.Int = points.length
    val u2: scala.Float = u * u
    out.set(points(i)).scl(((-u) * 5) + (u2 * 4.5f))
    if (continuous || (i > 0)) {
      out.add(tmp.set(points(((n + i) - 1) % n)).scl(((-0.5f) + (u * 2)) - (u2 * 1.5f)))
    } else ()
    if (continuous || (i < (n - 1))) {
      out.add(tmp.set(points((i + 1) % n)).scl((0.5f + (u * 4)) - (u2 * 4.5f)))
    } else ()
    if (continuous || (i < (n - 2))) {
      out.add(tmp.set(points((i + 2) % n)).scl((-u) + (u2 * 1.5f)))
    } else ()
    return out
  }
}