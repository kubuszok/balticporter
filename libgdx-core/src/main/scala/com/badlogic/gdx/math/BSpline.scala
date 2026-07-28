package com.badlogic.gdx.math

class BSpline[T <: com.badlogic.gdx.math.Vector[T]] extends com.badlogic.gdx.math.Path[T] {
  var controlPoints: scala.Array[T] = null.asInstanceOf[scala.Array[T]]
  var knots: com.badlogic.gdx.utils.Array[T] = null.asInstanceOf[com.badlogic.gdx.utils.Array[T]]
  var degree: scala.Int = 0
  var continuous: scala.Boolean = false
  var spanCount: scala.Int = 0
  private var tmp: T = null.asInstanceOf[T]
  private var tmp2: T = null.asInstanceOf[T]
  private var tmp3: T = null.asInstanceOf[T]
  def this(controlPoints: scala.Array[T], degree: scala.Int, continuous: scala.Boolean) = {
    this()
    this.set(controlPoints, degree, continuous)
  }
  def set(controlPoints: scala.Array[T], degree: scala.Int, continuous: scala.Boolean): BSpline[?] = {
    if (this.tmp == null) {
      this.tmp = controlPoints(0).cpy().asInstanceOf[T]
    } else ()
    if (this.tmp2 == null) {
      this.tmp2 = controlPoints(0).cpy().asInstanceOf[T]
    } else ()
    if (this.tmp3 == null) {
      this.tmp3 = controlPoints(0).cpy().asInstanceOf[T]
    } else ()
    this.controlPoints = controlPoints
    this.degree = degree
    this.continuous = continuous
    this.spanCount = if (continuous) controlPoints.length else controlPoints.length - degree
    val knotCount: scala.Int = if (continuous) controlPoints.length else controlPoints.length - 1
    if (this.knots == null) {
      this.knots = new com.badlogic.gdx.utils.Array[T](knotCount)
    } else {
      this.knots.clear()
      this.knots.ensureCapacity(knotCount)
    };
    { var i: scala.Int = 0; while (i < knotCount) { {
      this.knots.add(BSpline.calculate(controlPoints(0).cpy(), if (continuous) i else i + (0.5f * degree).asInstanceOf[scala.Int], 0.0f, controlPoints, degree, continuous, this.tmp))
    }; i = i + 1 } }
    return this.asInstanceOf[BSpline[?]]
  }
  @java.lang.Override
  override def valueAt(out: T, t: scala.Float): T = {
    val n: scala.Int = this.spanCount
    var u: scala.Float = t * n
    val i: scala.Int = if (t >= 1.0f) n - 1 else u.asInstanceOf[scala.Int]
    u = u - i
    return this.valueAt(out, i, u)
  }
  def valueAt(out: T, span: scala.Int, u: scala.Float): T = {
    return BSpline.calculate(out, if (this.continuous) span else span + (this.degree * 0.5f).asInstanceOf[scala.Int], u, this.controlPoints, this.degree, this.continuous, this.tmp)
  }
  @java.lang.Override
  override def derivativeAt(out: T, t: scala.Float): T = {
    val n: scala.Int = this.spanCount
    var u: scala.Float = t * n
    val i: scala.Int = if (t >= 1.0f) n - 1 else u.asInstanceOf[scala.Int]
    u = u - i
    return this.derivativeAt(out, i, u)
  }
  def derivativeAt(out: T, span: scala.Int, u: scala.Float): T = {
    return BSpline.derivative(out, if (this.continuous) span else span + (this.degree * 0.5f).asInstanceOf[scala.Int], u, this.controlPoints, this.degree, this.continuous, this.tmp)
  }
  def nearest(in: T): scala.Int = {
    return this.nearest(in, 0, this.spanCount)
  }
  def nearest(in: T, start$arg: scala.Int, count: scala.Int): scala.Int = {
    var start: scala.Int = start$arg
    val knotCount: scala.Int = this.knots.size
    while (start < 0) {
      start = start + knotCount
    }
    var result: scala.Int = start % knotCount
    var dst: scala.Float = in.dst2(this.knots.get(result));
    { var i: scala.Int = 1; while (i < count) { {
      val idx: scala.Int = (start + i) % knotCount
      val d: scala.Float = in.dst2(this.knots.get(idx))
      if (d < dst) {
        dst = d
        result = idx
      } else ()
    }; i = i + 1 } }
    return result
  }
  @java.lang.Override
  override def approximate(v: T): scala.Float = {
    return this.approximate(v, this.nearest(v))
  }
  def approximate(in: T, start: scala.Int, count: scala.Int): scala.Float = {
    return this.approximate(in, this.nearest(in, start, count))
  }
  def approximate(in: T, near: scala.Int): scala.Float = {
    var n: scala.Int = near
    val nearest: T = this.knots.get(n)
    val previous: T = this.knots.get(if (n > 0) n - 1 else this.knots.size - 1)
    val next: T = this.knots.get((n + 1) % this.knots.size)
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
      n = if (n > 0) n - 1 else this.knots.size - 1
    }
    val L1Sqr: scala.Float = P1.dst2(P2) + 1.0E-10f
    val L2Sqr: scala.Float = P3.dst2(P2)
    val L3Sqr: scala.Float = P3.dst2(P1)
    val L1: scala.Float = java.lang.Math.sqrt(L1Sqr).asInstanceOf[scala.Float].asInstanceOf[scala.Float]
    val s: scala.Float = ((L2Sqr + L1Sqr) - L3Sqr) / (2 * L1)
    val u: scala.Float = (com.badlogic.gdx.math.MathUtils.clamp: (scala.Float, scala.Float, scala.Float) => scala.Float)((L1 - s) / L1, 0.0f, 1.0f)
    return (n + u) / this.spanCount
  }
  @java.lang.Override
  override def locate(v: T): scala.Float = {
    return this.approximate(v)
  }
  @java.lang.Override
  override def approxLength(samples: scala.Int): scala.Float = {
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
object BSpline {
  private final val d6: scala.Float = 1.0f / 6.0f
  def cubic[T <: com.badlogic.gdx.math.Vector[T]](out: T, t: scala.Float, points: scala.Array[T], continuous: scala.Boolean, tmp: T): T = {
    val n: scala.Int = if (continuous) points.length else points.length - 3
    var u: scala.Float = t * n
    val i: scala.Int = if (t >= 1.0f) n - 1 else u.asInstanceOf[scala.Int]
    u = u - i
    return BSpline.cubic(out, i, u, points, continuous, tmp)
  }
  def cubic_derivative[T <: com.badlogic.gdx.math.Vector[T]](out: T, t: scala.Float, points: scala.Array[T], continuous: scala.Boolean, tmp: T): T = {
    val n: scala.Int = if (continuous) points.length else points.length - 3
    var u: scala.Float = t * n
    val i: scala.Int = if (t >= 1.0f) n - 1 else u.asInstanceOf[scala.Int]
    u = u - i
    return BSpline.cubic(out, i, u, points, continuous, tmp)
  }
  def cubic[T <: com.badlogic.gdx.math.Vector[T]](out: T, i: scala.Int, u: scala.Float, points: scala.Array[T], continuous: scala.Boolean, tmp: T): T = {
    val n: scala.Int = points.length
    val dt: scala.Float = 1.0f - u
    val t2: scala.Float = u * u
    val t3: scala.Float = t2 * u
    out.set(points(i)).scl((((3.0f * t3) - (6.0f * t2)) + 4.0f) * BSpline.d6)
    if (continuous || (i > 0)) {
      out.add(tmp.set(points(((n + i) - 1) % n)).scl(((dt * dt) * dt) * BSpline.d6))
    } else ()
    if (continuous || (i < (n - 1))) {
      out.add(tmp.set(points((i + 1) % n)).scl((((((-3.0f) * t3) + (3.0f * t2)) + (3.0f * u)) + 1.0f) * BSpline.d6))
    } else ()
    if (continuous || (i < (n - 2))) {
      out.add(tmp.set(points((i + 2) % n)).scl(t3 * BSpline.d6))
    } else ()
    return out
  }
  def cubic_derivative[T <: com.badlogic.gdx.math.Vector[T]](out: T, i: scala.Int, u: scala.Float, points: scala.Array[T], continuous: scala.Boolean, tmp: T): T = {
    val n: scala.Int = points.length
    val dt: scala.Float = 1.0f - u
    val t2: scala.Float = u * u
    val t3: scala.Float = t2 * u
    out.set(points(i)).scl((1.5f * t2) - (2 * u))
    if (continuous || (i > 0)) {
      out.add(tmp.set(points(((n + i) - 1) % n)).scl(((-0.5f) * dt) * dt))
    } else ()
    if (continuous || (i < (n - 1))) {
      out.add(tmp.set(points((i + 1) % n)).scl((((-1.5f) * t2) + u) + 0.5f))
    } else ()
    if (continuous || (i < (n - 2))) {
      out.add(tmp.set(points((i + 2) % n)).scl(0.5f * t2))
    } else ()
    return out
  }
  def calculate[T <: com.badlogic.gdx.math.Vector[T]](out: T, t: scala.Float, points: scala.Array[T], degree: scala.Int, continuous: scala.Boolean, tmp: T): T = {
    val n: scala.Int = if (continuous) points.length else points.length - degree
    var u: scala.Float = t * n
    val i: scala.Int = if (t >= 1.0f) n - 1 else u.asInstanceOf[scala.Int]
    u = u - i
    return BSpline.calculate(out, i, u, points, degree, continuous, tmp)
  }
  def derivative[T <: com.badlogic.gdx.math.Vector[T]](out: T, t: scala.Float, points: scala.Array[T], degree: scala.Int, continuous: scala.Boolean, tmp: T): T = {
    val n: scala.Int = if (continuous) points.length else points.length - degree
    var u: scala.Float = t * n
    val i: scala.Int = if (t >= 1.0f) n - 1 else u.asInstanceOf[scala.Int]
    u = u - i
    return BSpline.derivative(out, i, u, points, degree, continuous, tmp)
  }
  def calculate[T <: com.badlogic.gdx.math.Vector[T]](out: T, i: scala.Int, u: scala.Float, points: scala.Array[T], degree: scala.Int, continuous: scala.Boolean, tmp: T): T = {
    degree match {
      case 3 => {
        return BSpline.cubic(out, i, u, points, continuous, tmp)
      }
    }
    throw new java.lang.IllegalArgumentException()
  }
  def derivative[T <: com.badlogic.gdx.math.Vector[T]](out: T, i: scala.Int, u: scala.Float, points: scala.Array[T], degree: scala.Int, continuous: scala.Boolean, tmp: T): T = {
    degree match {
      case 3 => {
        return BSpline.cubic_derivative(out, i, u, points, continuous, tmp)
      }
    }
    throw new java.lang.IllegalArgumentException()
  }
}