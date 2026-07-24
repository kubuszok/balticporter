package com.badlogic.gdx.math

abstract class Interpolation {
  def apply(a: scala.Float): scala.Float
  def apply(start: scala.Float, `end`: scala.Float, a: scala.Float): scala.Float = {
    return start + ((`end` - start) * this.apply(a))
  }
}
object Interpolation {
  final val linear: Interpolation = new Interpolation()
  final val smooth: Interpolation = new Interpolation()
  final val smooth2: Interpolation = new Interpolation()
  final val smoother: Interpolation = new Interpolation()
  final val fade: Interpolation = Interpolation.smoother
  final val pow2: com.badlogic.gdx.math.Interpolation.Pow = new com.badlogic.gdx.math.Interpolation.Pow(2)
  final val pow2In: com.badlogic.gdx.math.Interpolation.PowIn = new com.badlogic.gdx.math.Interpolation.PowIn(2)
  final val slowFast: com.badlogic.gdx.math.Interpolation.PowIn = Interpolation.pow2In
  final val pow2Out: com.badlogic.gdx.math.Interpolation.PowOut = new com.badlogic.gdx.math.Interpolation.PowOut(2)
  final val fastSlow: com.badlogic.gdx.math.Interpolation.PowOut = Interpolation.pow2Out
  final val pow2InInverse: Interpolation = new Interpolation()
  final val pow2OutInverse: Interpolation = new Interpolation()
  final val pow3: com.badlogic.gdx.math.Interpolation.Pow = new com.badlogic.gdx.math.Interpolation.Pow(3)
  final val pow3In: com.badlogic.gdx.math.Interpolation.PowIn = new com.badlogic.gdx.math.Interpolation.PowIn(3)
  final val pow3Out: com.badlogic.gdx.math.Interpolation.PowOut = new com.badlogic.gdx.math.Interpolation.PowOut(3)
  final val pow3InInverse: Interpolation = new Interpolation()
  final val pow3OutInverse: Interpolation = new Interpolation()
  final val pow4: com.badlogic.gdx.math.Interpolation.Pow = new com.badlogic.gdx.math.Interpolation.Pow(4)
  final val pow4In: com.badlogic.gdx.math.Interpolation.PowIn = new com.badlogic.gdx.math.Interpolation.PowIn(4)
  final val pow4Out: com.badlogic.gdx.math.Interpolation.PowOut = new com.badlogic.gdx.math.Interpolation.PowOut(4)
  final val pow5: com.badlogic.gdx.math.Interpolation.Pow = new com.badlogic.gdx.math.Interpolation.Pow(5)
  final val pow5In: com.badlogic.gdx.math.Interpolation.PowIn = new com.badlogic.gdx.math.Interpolation.PowIn(5)
  final val pow5Out: com.badlogic.gdx.math.Interpolation.PowOut = new com.badlogic.gdx.math.Interpolation.PowOut(5)
  final val sine: Interpolation = new Interpolation()
  final val sineIn: Interpolation = new Interpolation()
  final val sineOut: Interpolation = new Interpolation()
  final val exp10: com.badlogic.gdx.math.Interpolation.Exp = new com.badlogic.gdx.math.Interpolation.Exp(2, 10)
  final val exp10In: com.badlogic.gdx.math.Interpolation.ExpIn = new com.badlogic.gdx.math.Interpolation.ExpIn(2, 10)
  final val exp10Out: com.badlogic.gdx.math.Interpolation.ExpOut = new com.badlogic.gdx.math.Interpolation.ExpOut(2, 10)
  final val exp5: com.badlogic.gdx.math.Interpolation.Exp = new com.badlogic.gdx.math.Interpolation.Exp(2, 5)
  final val exp5In: com.badlogic.gdx.math.Interpolation.ExpIn = new com.badlogic.gdx.math.Interpolation.ExpIn(2, 5)
  final val exp5Out: com.badlogic.gdx.math.Interpolation.ExpOut = new com.badlogic.gdx.math.Interpolation.ExpOut(2, 5)
  final val circle: Interpolation = new Interpolation()
  final val circleIn: Interpolation = new Interpolation()
  final val circleOut: Interpolation = new Interpolation()
  final val elastic: com.badlogic.gdx.math.Interpolation.Elastic = new com.badlogic.gdx.math.Interpolation.Elastic(2, 10, 7, 1)
  final val elasticIn: com.badlogic.gdx.math.Interpolation.ElasticIn = new com.badlogic.gdx.math.Interpolation.ElasticIn(2, 10, 6, 1)
  final val elasticOut: com.badlogic.gdx.math.Interpolation.ElasticOut = new com.badlogic.gdx.math.Interpolation.ElasticOut(2, 10, 7, 1)
  final val swing: com.badlogic.gdx.math.Interpolation.Swing = new com.badlogic.gdx.math.Interpolation.Swing(1.5f)
  final val swingIn: com.badlogic.gdx.math.Interpolation.SwingIn = new com.badlogic.gdx.math.Interpolation.SwingIn(2.0f)
  final val swingOut: com.badlogic.gdx.math.Interpolation.SwingOut = new com.badlogic.gdx.math.Interpolation.SwingOut(2.0f)
  final val bounce: com.badlogic.gdx.math.Interpolation.Bounce = new com.badlogic.gdx.math.Interpolation.Bounce(4)
  final val bounceIn: com.badlogic.gdx.math.Interpolation.BounceIn = new com.badlogic.gdx.math.Interpolation.BounceIn(4)
  final val bounceOut: com.badlogic.gdx.math.Interpolation.BounceOut = new com.badlogic.gdx.math.Interpolation.BounceOut(4)
  class Pow extends Interpolation {
    var power: scala.Int = 0
    def this(power: scala.Int) = {
      this()
      this.power = power
    }
    def apply(a: scala.Float): scala.Float = {
      if (a <= 0.5f) {
        return java.lang.Math.pow(a * 2, this.power).asInstanceOf[scala.Float] / 2
      } else ()
      return (java.lang.Math.pow((a - 1) * 2, this.power).asInstanceOf[scala.Float] / (if ((this.power % 2) == 0) -2 else 2)) + 1
    }
  }
  object Pow {
    export Interpolation.*
  }
  class PowIn extends com.badlogic.gdx.math.Interpolation.Pow {
    def this(power: scala.Int) = {
      this()
    }
    def apply(a: scala.Float): scala.Float = {
      return java.lang.Math.pow(a, power).asInstanceOf[scala.Float].asInstanceOf[scala.Float]
    }
  }
  object PowIn {
    export com.badlogic.gdx.math.Interpolation.Pow.*
  }
  class PowOut extends com.badlogic.gdx.math.Interpolation.Pow {
    def this(power: scala.Int) = {
      this()
    }
    def apply(a: scala.Float): scala.Float = {
      return (java.lang.Math.pow(a - 1, power).asInstanceOf[scala.Float] * (if ((power % 2) == 0) -1 else 1)) + 1
    }
  }
  object PowOut {
    export com.badlogic.gdx.math.Interpolation.Pow.*
  }
  class Exp extends Interpolation {
    var value: scala.Float = 0.0f
    var power: scala.Float = 0.0f
    var min: scala.Float = 0.0f
    var scale: scala.Float = 0.0f
    def this(value: scala.Float, power: scala.Float) = {
      this()
      this.value = value
      this.power = power
      this.min = java.lang.Math.pow(value, -power).asInstanceOf[scala.Float].asInstanceOf[scala.Float]
      this.scale = 1 / (1 - this.min)
    }
    def apply(a: scala.Float): scala.Float = {
      if (a <= 0.5f) {
        return ((java.lang.Math.pow(this.value, this.power * ((a * 2) - 1)).asInstanceOf[scala.Float] - this.min) * this.scale) / 2
      } else ()
      return (2 - ((java.lang.Math.pow(this.value, (-this.power) * ((a * 2) - 1)).asInstanceOf[scala.Float] - this.min) * this.scale)) / 2
    }
  }
  object Exp {
    export Interpolation.*
  }
  class ExpIn extends com.badlogic.gdx.math.Interpolation.Exp {
    def this(value: scala.Float, power: scala.Float) = {
      this()
    }
    def apply(a: scala.Float): scala.Float = {
      return (java.lang.Math.pow(value, power * (a - 1)).asInstanceOf[scala.Float] - min) * scale
    }
  }
  object ExpIn {
    export com.badlogic.gdx.math.Interpolation.Exp.*
  }
  class ExpOut extends com.badlogic.gdx.math.Interpolation.Exp {
    def this(value: scala.Float, power: scala.Float) = {
      this()
    }
    def apply(a: scala.Float): scala.Float = {
      return 1 - ((java.lang.Math.pow(value, (-power) * a).asInstanceOf[scala.Float] - min) * scale)
    }
  }
  object ExpOut {
    export com.badlogic.gdx.math.Interpolation.Exp.*
  }
  class Elastic extends Interpolation {
    var value: scala.Float = 0.0f
    var power: scala.Float = 0.0f
    var scale: scala.Float = 0.0f
    var bounces: scala.Float = 0.0f
    def this(value: scala.Float, power: scala.Float, bounces: scala.Int, scale: scala.Float) = {
      this()
      this.value = value
      this.power = power
      this.scale = scale
      this.bounces = (bounces * com.badlogic.gdx.math.MathUtils.PI) * (if ((bounces % 2) == 0) 1 else -1)
    }
    def apply(a$arg: scala.Float): scala.Float = {
      var a: scala.Float = a$arg
      if (a <= 0.5f) {
        a = a * 2
        return ((java.lang.Math.pow(this.value, this.power * (a - 1)).asInstanceOf[scala.Float] * com.badlogic.gdx.math.MathUtils.sin(a * this.bounces)) * this.scale) / 2
      } else ()
      a = 1 - a
      a = a * 2
      return 1 - (((java.lang.Math.pow(this.value, this.power * (a - 1)).asInstanceOf[scala.Float] * com.badlogic.gdx.math.MathUtils.sin(a * this.bounces)) * this.scale) / 2)
    }
  }
  object Elastic {
    export Interpolation.*
  }
  class ElasticIn extends com.badlogic.gdx.math.Interpolation.Elastic {
    def this(value: scala.Float, power: scala.Float, bounces: scala.Int, scale: scala.Float) = {
      this()
    }
    def apply(a: scala.Float): scala.Float = {
      if (a >= 0.99) {
        return 1
      } else ()
      return (java.lang.Math.pow(value, power * (a - 1)).asInstanceOf[scala.Float] * com.badlogic.gdx.math.MathUtils.sin(a * bounces)) * scale
    }
  }
  object ElasticIn {
    export com.badlogic.gdx.math.Interpolation.Elastic.*
  }
  class ElasticOut extends com.badlogic.gdx.math.Interpolation.Elastic {
    def this(value: scala.Float, power: scala.Float, bounces: scala.Int, scale: scala.Float) = {
      this()
    }
    def apply(a$arg: scala.Float): scala.Float = {
      var a: scala.Float = a$arg
      if (a == 0) {
        return 0
      } else ()
      a = 1 - a
      return 1 - ((java.lang.Math.pow(value, power * (a - 1)).asInstanceOf[scala.Float] * com.badlogic.gdx.math.MathUtils.sin(a * bounces)) * scale)
    }
  }
  object ElasticOut {
    export com.badlogic.gdx.math.Interpolation.Elastic.*
  }
  class Bounce extends com.badlogic.gdx.math.Interpolation.BounceOut {
    def this(widths: scala.Array[scala.Float], heights: scala.Array[scala.Float]) = {
      this()
    }
    def this(bounces: scala.Int) = {
      this()
    }
    private def out(a: scala.Float): scala.Float = {
      val test: scala.Float = a + (widths(0) / 2)
      if (test < widths(0)) {
        return (test / (widths(0) / 2)) - 1
      } else ()
      return super.apply(a)
    }
    def apply(a: scala.Float): scala.Float = {
      if (a <= 0.5f) {
        return (1 - this.out(1 - (a * 2))) / 2
      } else ()
      return (this.out((a * 2) - 1) / 2) + 0.5f
    }
  }
  object Bounce {
    export com.badlogic.gdx.math.Interpolation.BounceOut.*
  }
  class BounceOut extends Interpolation {
    var widths: scala.Array[scala.Float] = null.asInstanceOf[scala.Array[scala.Float]]
    var heights: scala.Array[scala.Float] = null.asInstanceOf[scala.Array[scala.Float]]
    def this(widths: scala.Array[scala.Float], heights: scala.Array[scala.Float]) = {
      this()
      if (widths.length != heights.length) {
        throw new java.lang.IllegalArgumentException("Must be the same number of widths and heights.")
      } else ()
      this.widths = widths
      this.heights = heights
    }
    def this(bounces: scala.Int) = {
      this()
      if ((bounces < 2) || (bounces > 5)) {
        throw new java.lang.IllegalArgumentException("bounces cannot be < 2 or > 5: " + bounces)
      } else ()
      this.widths = new scala.Array[scala.Float](bounces)
      this.heights = new scala.Array[scala.Float](bounces)
      this.heights(0) = 1
      bounces match {
        case 2 => {
          this.widths(0) = 0.6f
          this.widths(1) = 0.4f
          this.heights(1) = 0.33f
        }
        case 3 => {
          this.widths(0) = 0.4f
          this.widths(1) = 0.4f
          this.widths(2) = 0.2f
          this.heights(1) = 0.33f
          this.heights(2) = 0.1f
        }
        case 4 => {
          this.widths(0) = 0.34f
          this.widths(1) = 0.34f
          this.widths(2) = 0.2f
          this.widths(3) = 0.15f
          this.heights(1) = 0.26f
          this.heights(2) = 0.11f
          this.heights(3) = 0.03f
        }
        case 5 => {
          this.widths(0) = 0.3f
          this.widths(1) = 0.3f
          this.widths(2) = 0.2f
          this.widths(3) = 0.1f
          this.widths(4) = 0.1f
          this.heights(1) = 0.45f
          this.heights(2) = 0.3f
          this.heights(3) = 0.15f
          this.heights(4) = 0.06f
        }
      }
      this.widths(0) = this.widths(0) * 2
    }
    def apply(a$arg: scala.Float): scala.Float = {
      var a: scala.Float = a$arg
      if (a == 1) {
        return 1
      } else ()
      a = a + (this.widths(0) / 2)
      var width: scala.Float = 0
      var height: scala.Float = 0;
      { var i: scala.Int = 0; val n: scala.Int = this.widths.length; while (i < n) { {
        width = this.widths(i)
        if (a <= width) {
          height = this.heights(i)
          /* break */ ()
        } else ()
        a = a - width
      }; i = i + 1 } }
      a = a / width
      val z: scala.Float = ((4 / width) * height) * a
      return 1 - ((z - (z * a)) * width)
    }
  }
  object BounceOut {
    export Interpolation.*
  }
  class BounceIn extends com.badlogic.gdx.math.Interpolation.BounceOut {
    def this(widths: scala.Array[scala.Float], heights: scala.Array[scala.Float]) = {
      this()
    }
    def this(bounces: scala.Int) = {
      this()
    }
    def apply(a: scala.Float): scala.Float = {
      return 1 - super.apply(1 - a)
    }
  }
  object BounceIn {
    export com.badlogic.gdx.math.Interpolation.BounceOut.*
  }
  class Swing extends Interpolation {
    private var scale: scala.Float = 0.0f
    def this(scale: scala.Float) = {
      this()
      this.scale = scale * 2
    }
    def apply(a$arg: scala.Float): scala.Float = {
      var a: scala.Float = a$arg
      if (a <= 0.5f) {
        a = a * 2
        return ((a * a) * (((this.scale + 1) * a) - this.scale)) / 2
      } else ()
      a = a - 1
      a = a * 2
      return (((a * a) * (((this.scale + 1) * a) + this.scale)) / 2) + 1
    }
  }
  object Swing {
    export Interpolation.*
  }
  class SwingOut extends Interpolation {
    private var scale: scala.Float = 0.0f
    def this(scale: scala.Float) = {
      this()
      this.scale = scale
    }
    def apply(a$arg: scala.Float): scala.Float = {
      var a: scala.Float = a$arg
      a = a - 1
      return ((a * a) * (((this.scale + 1) * a) + this.scale)) + 1
    }
  }
  object SwingOut {
    export Interpolation.*
  }
  class SwingIn extends Interpolation {
    private var scale: scala.Float = 0.0f
    def this(scale: scala.Float) = {
      this()
      this.scale = scale
    }
    def apply(a: scala.Float): scala.Float = {
      return (a * a) * (((this.scale + 1) * a) - this.scale)
    }
  }
  object SwingIn {
    export Interpolation.*
  }
}